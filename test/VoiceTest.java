import infinitylink.core.voice.Adpcm;
import infinitylink.core.voice.Jitter;
import infinitylink.core.voice.Spatial;
import infinitylink.core.voice.VoiceMsg;
import infinitylink.core.voice.VoicePacket;

import java.util.HexFormat;
import java.util.List;

/** Java pur : chat vocal 1.1.2 contre les octets de référence du serveur (DOCS/VOIX-PROTOCOLE.md §4,
 *  crates/sage_proto/src/link_voix.rs), codec ADPCM, tampon de gigue et spatialisation. */
public final class VoiceTest {
    static int ok, ko;

    static void check(String name, boolean cond, String detail) {
        if (cond) { ok++; System.out.println("OK   " + name); }
        else { ko++; System.out.println("FAIL " + name + " : " + detail); }
    }

    static String hex(byte[] b) { return HexFormat.of().formatHex(b); }
    static byte[] unhex(String s) { return HexFormat.of().parseHex(s.replace(" ", "")); }

    public static void main(String[] a) throws Exception {
        // §4 : clé 00..0f, session 0102030405060708, séquence 42, horodatage 1000, drapeau voix, charge deadbeef
        byte[] key = unhex("000102030405060708090a0b0c0d0e0f"), session = unhex("0102030405060708");
        VoicePacket vp = new VoicePacket(key);
        byte[] p = vp.seal(session, 42, 1000, VoicePacket.VOICE, unhex("deadbeef"));
        check("paquet de reference 38 octets", hex(p).equals("0101020304050607080000002ac10949b0ecbdd4e774787fd5a4edbb3f7906aca341d46f3b9f"), hex(p));
        VoicePacket.Frame f = vp.open(p, p.length);
        check("ouverture du paquet de reference", f != null && f.sequence() == 42 && f.timestamp() == 1000 && f.flags() == 1
                && hex(f.payload()).equals("deadbeef") && hex(f.session()).equals("0102030405060708"), String.valueOf(f));
        p[20] ^= 1;
        check("paquet falsifie refuse", vp.open(p, p.length) == null, "accepte");
        check("paquet trop court refuse", vp.open(new byte[33], 33) == null, "accepte");

        // message voice : mêmes octets que link_voix::tests::octets_de_reference_voice
        VoiceMsg.Config c = new VoiceMsg.Config(25565, "", key, session, 48f, 8f, VoiceMsg.CODEC_ADPCM16, "equipe");
        String ref = "01ddc70100000102030405060708090a0b0c0d0e0f010203040506070842400000410000000106657175697065";
        check("voice a l'octet du serveur", hex(c.encode()).equals(ref), hex(c.encode()));
        VoiceMsg.Config d = VoiceMsg.Config.decode(unhex(ref));
        check("voice decode", d.port() == 25565 && d.host().isEmpty() && d.radius() == 48f && d.whisperRadius() == 8f
                && d.group().equals("equipe") && hex(d.key()).equals(hex(key)), d.toString());
        List<VoiceMsg.Peer> peers = List.of(new VoiceMsg.Peer(session, 42, "Joueur"));
        List<VoiceMsg.Peer> back = VoiceMsg.decodePeers(VoiceMsg.encodePeers(peers));
        check("voice_peers aller-retour", back.size() == 1 && back.get(0).eid() == 42 && back.get(0).name().equals("Joueur"), back.toString());
        check("voice_ctl octets", hex(VoiceMsg.ctl("groupe", "a")).equals("0667726f75706501" + "61"), hex(VoiceMsg.ctl("groupe", "a")));
        boolean refused = false;
        try { VoiceMsg.decodePeers(unhex("ffffffff0f")); } catch (RuntimeException e) { refused = true; }
        check("table hostile refusee", refused, "acceptee");

        // ADPCM : sinusoïde de 440 Hz, rapport signal/bruit > 20 dB, taille de trame fixe
        Adpcm.State st = new Adpcm.State();
        double sig = 0, err = 0;
        for (int t = 0; t < 20; t++) {
            short[] in = new short[Adpcm.FRAME];
            for (int i = 0; i < in.length; i++) in[i] = (short) (12000 * Math.sin(2 * Math.PI * 440 * (t * Adpcm.FRAME + i) / Adpcm.RATE));
            byte[] enc = Adpcm.encode(in, st);
            short[] out = Adpcm.decode(enc);
            if (enc.length != Adpcm.BYTES || out == null) { check("trame adpcm", false, "taille " + enc.length); break; }
            if (t > 0) for (int i = 0; i < in.length; i++) { sig += (double) in[i] * in[i]; err += Math.pow(in[i] - out[i], 2); }
        }
        double snr = 10 * Math.log10(sig / Math.max(1, err));
        check("adpcm SNR > 20 dB (" + Math.round(snr) + " dB)", snr > 20, "SNR " + snr);
        check("adpcm trame etrangere refusee", Adpcm.decode(new byte[10]) == null, "acceptee");
        check("dbfs silence", Adpcm.dbfs(new short[Adpcm.FRAME]) == -127, "");

        // gigue : désordre remis en ordre, perte masquée une fois, fin de parole
        Jitter j = new Jitter();
        short[][] fr = new short[6][];
        for (int i = 0; i < 6; i++) { fr[i] = new short[Adpcm.FRAME]; fr[i][0] = (short) (1000 + i); }
        j.put(11, fr[1], 0); j.put(10, fr[0], 0); j.put(13, fr[3], 0); j.put(12, fr[2], 0); j.put(15, fr[5], 0);
        short[] o1 = j.poll(), o2 = j.poll(), o3 = j.poll(), o4 = j.poll(), o5 = j.poll(), o6 = j.poll();
        check("gigue remise en ordre", o1 == fr[0] && o2 == fr[1] && o3 == fr[2] && o4 == fr[3], "");
        check("gigue perte masquee", o5 != null && o5[0] == fr[3][0] / 2 && o6 == fr[5], "");
        check("gigue vide = silence", j.poll() == null, "");
        j.put(3, fr[0], 0);
        check("gigue nouvelle prise de parole (prebuffer)", j.poll() == null, "");

        // spatialisation : source à droite de l'auditeur regardant +Z (yaw 0) -> x négatif
        float[] g = Spatial.gains(0, 64, 0, 0f, -5, 64, 0, 48f);
        check("source a droite plus forte a droite", g[1] > g[0], g[0] + " " + g[1]);
        float[] loin = Spatial.gains(0, 64, 0, 0f, 100, 64, 0, 48f);
        check("hors rayon = muet", loin[0] == 0 && loin[1] == 0, loin[0] + " " + loin[1]);

        System.out.println("VoiceTest : " + ok + " OK, " + ko + " echec(s)");
        if (ko > 0) System.exit(1);
    }
}
