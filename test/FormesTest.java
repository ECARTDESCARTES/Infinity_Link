import infinitylink.core.FormesSgb2;
import infinitylink.core.LinkState;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Java pur : capacité « formes » (Infinity_Link 1.1.0, lot Ph4g-link-formes) — décodage SGB2 complet (même format que
 *  crates/laconia_pack/src/blocs.rs::encoder), rotation (même règle que blocs_formes.rs::tourner_y), sans collision,
 *  rejets stricts, annonce dans le hello et rejet hors capacité. */
public final class FormesTest {
    static int ok, ko;
    static void check(String n, boolean c) { if (c) ok++; else { ko++; System.out.println("ECHEC " + n); } }

    static final ByteArrayOutputStream o = new ByteArrayOutputStream();
    static void vi(int v) { while ((v & ~0x7F) != 0) { o.write((v & 0x7F) | 0x80); v >>>= 7; } o.write(v); }
    static void s(String x) { byte[] b = x.getBytes(StandardCharsets.UTF_8); vi(b.length); o.writeBytes(b); }
    static void box(int... v) { for (int x : v) { o.write((x >> 8) & 0xFF); o.write(x & 0xFF); } }

    static byte[] table() {
        o.reset();
        o.writeBytes("SGB2".getBytes(StandardCharsets.US_ASCII)); vi(1); vi(35723); vi(100000); o.write(18);
        vi(3);
        s("escalier_droit_bas"); o.write(2); box(0, 0, 0, 4096, 2048, 4096); box(0, 2048, 0, 4096, 4096, 2048); o.write(255); o.write(0); o.write(0);
        s("croix"); o.write(1); box(0, 0, 0, 4096, 4096, 4096); o.write(0); o.write(0); o.write(2);
        s("muret_poteau"); o.write(1); box(1024, 0, 1024, 3072, 4096, 3072); o.write(1); box(1024, 0, 1024, 3072, 6144, 3072); o.write(0); o.write(0);
        vi(1); s("neutre_l0"); s("minecraft:block.stone"); o.write(0); vi(150); o.write(1); o.write(0); o.write(0); s(""); s(""); box(1000, 1000); o.write(0);
        vi(3);
        vi(35723 + 4000); vi(0); vi(0); o.write(1);
        vi(35723 + 4016); vi(0); vi(1); o.write(0);
        vi(35723 + 4032); vi(0); vi(2); o.write(0);
        return o.toByteArray();
    }

    public static void main(String[] a) {
        byte[] b = table();
        FormesSgb2.Table t = FormesSgb2.decode(b);
        check("formes", t.formes().size() == 3 && t.etats().size() == 3 && t.base() == 35723);
        double[][] esc = t.boites(t.etats().get(0), false);
        // marche nord tournée d'un quart de tour horaire : à l'est (x 0,5..1, z 0..1)
        check("rotation", Arrays.equals(esc[1], new double[]{0.5, 0.5, 0, 1, 1, 1}));
        check("collision identique", t.boites(t.etats().get(0), true).length == 2);
        check("sans collision", t.boites(t.etats().get(1), true).length == 0 && (t.drapeaux(t.etats().get(1)) & FormesSgb2.SANS_COLLISION) != 0);
        check("muret 1,5", t.boites(t.etats().get(2), true)[0][4] == 1.5 && t.boites(t.etats().get(2), false)[0][4] == 1.0);
        check("nom", t.nomForme(t.etats().get(2)).equals("muret_poteau"));
        boolean rejet = false;
        try { FormesSgb2.decode(Arrays.copyOf(b, b.length - 1)); } catch (RuntimeException e) { rejet = true; }
        check("tronque rejete", rejet);
        rejet = false;
        byte[] c = b.clone(); c[0] = 'X';
        try { FormesSgb2.decode(c); } catch (RuntimeException e) { rejet = true; }
        check("magic rejete", rejet);
        check("tourner 4", Arrays.equals(FormesSgb2.tourner(new short[]{0, 2048, 0, 4096, 4096, 2048}, 4), new short[]{0, 2048, 0, 4096, 4096, 2048}));
        LinkState st = new LinkState();
        check("hello sans formes", !st.ourCaps().contains("formes"));
        st.formesCapable = true;
        check("hello avec formes", st.ourCaps().contains("formes"));
        long rej = st.rejets();
        st.onInbound(FormesSgb2.FORMES, b);
        check("hors capacite rejete", st.rejets() == rej + 1 && st.formesTables == 0);
        System.out.println("FormesTest : " + ok + " ok, " + ko + " echec(s)");
        if (ko > 0) System.exit(1);
    }
}
