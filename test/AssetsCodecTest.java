import infinitylink.core.AssetsMsg;
import infinitylink.core.LinkState;
import infinitylink.core.Msg;
import infinitylink.core.Wire;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

/** Java pur : capacité « assets » (SAGE-LINK-PROTOCOLE.md §11) contre les octets de référence (identiques au test Rust
 *  crates/sage_proto/src/link_assets.rs), rejets stricts, découpe en parts de 30 Kio, puis annonce dans le hello et
 *  règle §5 (capacité absente : messages rejetés, client vanilla). */
public final class AssetsCodecTest {
    static int ok, ko;

    static byte[] hex(String s) { return HexFormat.of().parseHex(s.replace(" ", "")); }
    static String hex(byte[] b) { return HexFormat.ofDelimiter(" ").withUpperCase().formatHex(b); }

    static void check(String name, boolean cond, String detail) {
        if (cond) { ok++; System.out.println("OK   " + name); }
        else { ko++; System.out.println("FAIL " + name + " : " + detail); }
    }

    static void bytes(String name, byte[] got, byte[] want) {
        check(name, Arrays.equals(got, want), "attendu " + hex(want) + "\n  obtenu  " + hex(got));
    }

    static boolean refuses(Function<byte[], Object> dec, byte[] b) {
        try { dec.apply(b); return false; } catch (Wire.DecodeException e) { return true; }
    }

    static final String SHA_ABC = "A9 99 3E 36 47 06 81 6A BA 3E 25 71 78 50 C2 6C 9C D0 D8 9D";
    static final String SHA_VIDE = "DA 39 A3 EE 5E 6B 4B 0D 32 55 BF EF 95 60 18 90 AF D8 07 09";
    static final String OFFER = "01 03 " + SHA_ABC + " 80 80 40 10 68 74 74 70 3A 2F 2F 73 2F 70 2F 33 2E 7A 69 70";
    static final String DELTA = "03 " + SHA_VIDE + " 06 53 47 42 32 00 01 02 07 0C 6A 6F 75 65 75 72 3A 6C 61 6D 70 65 09 00";
    static final String BEGIN = "01 09 6C 61 6D 70 65 2E 70 6E 67 C0 B8 02 02";
    static final String PART = "01 01 DE AD BE EF";
    static final String END = "01 " + SHA_ABC;
    static final String RESULT = "01 01 01 0E 69 6D 61 67 65 20 3E 20 35 31 32 20 70 78";
    static final String DRAFT = "01 " + SHA_ABC + " 80 10 10 68 74 74 70 3A 2F 2F 73 2F 64 2F 31 2E 7A 69 70";

    public static void main(String[] args) {
        byte[] abc = AssetsMsg.sha1("abc".getBytes()), vide = AssetsMsg.sha1(new byte[0]);
        bytes("sha1(abc)", abc, hex(SHA_ABC));
        bytes("sha1()", vide, hex(SHA_VIDE));

        List<AssetsMsg.Epoch> offre = List.of(new AssetsMsg.Epoch(3, abc, 1 << 20, "http://s/p/3.zip"));
        bytes("assets_offer = §11", AssetsMsg.encodeOffer(offre), hex(OFFER));
        check("assets_offer decode", AssetsMsg.decodeOffer(hex(OFFER)).equals(offre), "");
        bytes("assets_offer vide", AssetsMsg.encodeOffer(List.of()), hex("00"));

        bytes("assets_ready (0,[3])", new AssetsMsg.Ready(0, List.of(3)).encode(), hex("00 01 03"));
        bytes("assets_ready (2,[])", new AssetsMsg.Ready(2, List.of()).encode(), hex("02 00"));
        check("assets_ready decode", AssetsMsg.Ready.decode(hex("00 01 03")).equals(new AssetsMsg.Ready(0, List.of(3))), "");

        AssetsMsg.BlocksDelta d = new AssetsMsg.BlocksDelta(3, vide, hex("53 47 42 32 00 01"),
            List.of(new AssetsMsg.Case(7, "joueur:lampe"), new AssetsMsg.Case(9, "")));
        bytes("blocks_delta = §11", d.encode(), hex(DELTA));
        AssetsMsg.BlocksDelta d2 = AssetsMsg.BlocksDelta.decode(hex(DELTA));
        check("blocks_delta decode", d2.epoch() == 3 && Arrays.equals(d2.packSha1(), vide) && d2.cases().equals(d.cases()), "");

        AssetsMsg.Begin b = new AssetsMsg.Begin(1, "lampe.png", 40_000, 2);
        bytes("upload_begin = §11", b.encode(), hex(BEGIN));
        check("upload_begin decode", AssetsMsg.Begin.decode(hex(BEGIN)).equals(b), "");
        bytes("upload_part = §11", new AssetsMsg.Part(1, 1, hex("DE AD BE EF")).encode(), hex(PART));
        AssetsMsg.Part p = AssetsMsg.Part.decode(hex(PART));
        check("upload_part decode", p.upload() == 1 && p.index() == 1 && Arrays.equals(p.data(), hex("DE AD BE EF")), "");
        bytes("upload_end = §11", new AssetsMsg.End(1, abc).encode(), hex(END));
        check("upload_end decode", Arrays.equals(AssetsMsg.End.decode(hex(END)).sha1(), abc), "");
        AssetsMsg.Result r = new AssetsMsg.Result(1, AssetsMsg.REFUSED, List.of("image > 512 px"));
        bytes("upload_result = §11", r.encode(), hex(RESULT));
        check("upload_result decode", AssetsMsg.Result.decode(hex(RESULT)).equals(r), "");
        bytes("upload_result accepte", new AssetsMsg.Result(1, 0, List.of()).encode(), hex("01 00 00"));
        bytes("draft_preview appliquer = §11", new AssetsMsg.Draft(abc, 2048, "http://s/d/1.zip").encode(), hex(DRAFT));
        bytes("draft_preview retirer", AssetsMsg.Draft.REMOVE.encode(), hex("00"));
        check("draft_preview decode", AssetsMsg.Draft.decode(hex(DRAFT)).size() == 2048 && AssetsMsg.Draft.decode(hex("00")).sha1() == null, "");

        // Rejets : toute troncature et tout octet en trop
        Object[][] refs = {
            {OFFER, (Function<byte[], Object>) AssetsMsg::decodeOffer}, {"00 01 03", (Function<byte[], Object>) AssetsMsg.Ready::decode},
            {DELTA, (Function<byte[], Object>) AssetsMsg.BlocksDelta::decode}, {BEGIN, (Function<byte[], Object>) AssetsMsg.Begin::decode},
            {END, (Function<byte[], Object>) AssetsMsg.End::decode}, {RESULT, (Function<byte[], Object>) AssetsMsg.Result::decode},
            {DRAFT, (Function<byte[], Object>) AssetsMsg.Draft::decode}};
        for (Object[] ref : refs) {
            @SuppressWarnings("unchecked") Function<byte[], Object> dec = (Function<byte[], Object>) ref[1];
            byte[] full = hex((String) ref[0]);
            boolean all = true;
            for (int n = 0; n < full.length; n++) all &= refuses(dec, Arrays.copyOf(full, n));
            check("troncatures refusees : " + ((String) ref[0]).substring(0, 5), all, "");
            check("octet en trop refuse : " + ((String) ref[0]).substring(0, 5), refuses(dec, Arrays.copyOf(full, full.length + 1)), "");
        }
        check("epoques non croissantes", refuses(AssetsMsg::decodeOffer, AssetsMsg.encodeOffer(List.of(
            new AssetsMsg.Epoch(3, abc, 1, "u"), new AssetsMsg.Epoch(3, abc, 1, "u")))), "");
        check("assets_ready code inconnu", refuses(AssetsMsg.Ready::decode, hex("05 00")), "");
        check("upload_part vide", refuses(AssetsMsg.Part::decode, hex("01 00")), "");
        check("upload_part > 30 Kio", refuses(AssetsMsg.Part::decode, new AssetsMsg.Part(1, 0, new byte[AssetsMsg.PART_SIZE + 1]).encode()), "");
        check("upload_begin > 2 Mio", refuses(AssetsMsg.Begin::decode, new AssetsMsg.Begin(1, "a.png", AssetsMsg.MAX_UPLOAD + 1, 1).encode()), "");
        check("upload_result verdict 2", refuses(AssetsMsg.Result::decode, hex("01 02 00")), "");
        check("draft_preview action 2", refuses(AssetsMsg.Draft::decode, hex("02")), "");

        // Découpe : OGG de 256 Kio = 9 parts, la dernière de 256 Kio - 8 x 30 Kio ; chaque trame ≤ 32 Kio
        byte[] ogg = new byte[256 * 1024];
        for (int i = 0; i < ogg.length; i++) ogg[i] = (byte) ((i * 2654435761L) >>> 24);
        List<byte[]> frames = AssetsMsg.split(7, "sons/pluie.ogg", ogg);
        AssetsMsg.Begin fb = AssetsMsg.Begin.decode(frames.get(0));
        check("decoupe : 9 parts", fb.parts() == 9 && frames.size() == 11 && fb.size() == ogg.length, "" + fb);
        java.io.ByteArrayOutputStream re = new java.io.ByteArrayOutputStream();
        boolean ordre = true, borne = true;
        for (int i = 1; i <= 9; i++) {
            borne &= frames.get(i).length <= 32 * 1024;
            AssetsMsg.Part q = AssetsMsg.Part.decode(frames.get(i));
            ordre &= q.index() == i - 1 && q.upload() == 7;
            re.writeBytes(q.data());
        }
        check("decoupe : ordre et bornes", ordre && borne, "");
        check("decoupe : reassemblage et empreinte", Arrays.equals(re.toByteArray(), ogg)
            && Arrays.equals(AssetsMsg.End.decode(frames.get(10)).sha1(), AssetsMsg.sha1(ogg)), "");
        check("MAX_PARTS = 69 (comme Rust)", AssetsMsg.MAX_PARTS == 69, "" + AssetsMsg.MAX_PARTS);

        // Annonce dans le hello et règle §5
        LinkState s = new LinkState();
        check("hello sans assets : caps inchangees", Msg.Hello.decode(s.beginHello("26.3")).caps().equals(List.of("hud", "keys", "state")), "");
        s.assetsCapable = true;
        check("hello avec assets", Msg.Hello.decode(s.beginHello("26.3")).caps().equals(List.of("hud", "keys", "state", "assets")), "");
        s.tabsCapable = true;
        check("hello tabs + assets", Msg.Hello.decode(s.beginHello("26.3")).caps().equals(List.of("hud", "keys", "state", "tabs", "assets")), "");
        s.onInbound(Msg.MANIFEST, new Msg.Manifest(1, "Serveur", List.of("hud", "keys"), List.of()).encode());
        long rej = s.rejets();
        s.onInbound(AssetsMsg.ASSETS_OFFER, hex(OFFER));
        check("capacite non accordee : offre rejetee, vanilla", !s.assetsOn() && s.rejets() == rej + 1 && s.assets.offers == 0
            && !s.json().contains("\"assets\""), s.json());
        s.beginHello("26.3");
        s.onInbound(Msg.MANIFEST, new Msg.Manifest(1, "Serveur", List.of("hud", "assets"), List.of()).encode());
        long rx = s.rx();
        s.onInbound(AssetsMsg.ASSETS_OFFER, hex(OFFER));
        s.onInbound(AssetsMsg.BLOCKS_DELTA, hex(DELTA));
        s.onInbound(AssetsMsg.UPLOAD_RESULT, hex(RESULT));
        s.onInbound(AssetsMsg.DRAFT_PREVIEW, hex(DRAFT));
        check("capacite accordee : 4 messages acceptes", s.assetsOn() && s.rx() == rx + 4 && s.assets.offers == 1 && s.assets.lastEpoch == 3
            && s.assets.deltas == 1 && s.assets.lastVerdict == 1 && s.assets.draftOn, s.json());
        String j = s.json();
        check("JSON assets", j.contains("\"assets\":{\"offers\":1,\"deltas\":1,\"results\":1,\"drafts\":1,\"epoch\":3,\"verdict\":1,\"draft\":true}"), j);
        rej = s.rejets();
        s.onInbound(AssetsMsg.ASSETS_OFFER, hex("01"));
        check("offre malformee : rejet comptee, aucun plantage", s.rejets() == rej + 1 && s.assets.offers == 1, s.json());
        s.disconnected();
        check("deconnexion : observation remise a zero", s.assets.offers == 0 && !s.assetsOn(), s.json());

        System.out.println("AssetsCodecTest : " + ok + "/" + (ok + ko));
        if (ko > 0) System.exit(1);
    }
}
