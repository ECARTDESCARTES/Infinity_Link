import sage.link.core.Msg;
import sage.link.core.lod.LodMesher;
import sage.link.core.lod.LodStore;
import sage.link.core.lod.LodWorker;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Java pur, sans écran : le mailleur lod (§9) sur des tuiles synthétiques (nombre de quads attendu, ombrage par face,
 *  raccord avec la tuile voisine, masque des chunks réels, eau), puis le magasin et le fil de travail. */
public final class LodMesherTest {
    static int ok, ko;

    static void check(String name, boolean cond, String detail) {
        if (cond) { ok++; System.out.println("OK   " + name); }
        else { ko++; System.out.println("FAIL " + name + " : " + detail); }
    }

    /** Tuile n×n au sol plat (top, depth, couleurs uniques), sans eau. */
    static Msg.LodTile flat(int level, int tx, int tz, int yBase, int n, int top, int depth) {
        int c = n * n;
        byte[] f = new byte[c];
        Arrays.fill(f, (byte) Msg.LOD_GROUND);
        int[] t = new int[c], d = new int[c], tr = new int[c], sr = new int[c];
        Arrays.fill(t, top); Arrays.fill(d, depth); Arrays.fill(tr, 0x5E9D34); Arrays.fill(sr, 0x866043);
        return new Msg.LodTile(level, tx, tz, yBase, n, f, t, d, tr, sr, new int[c], new int[c]);
    }

    static LodMesher.Mesh mesh(Msg.LodTile t, LodMesher.Neighbors nb, LodMesher.Mask m) {
        return LodMesher.mesh(LodStore.key(t.level(), t.tx(), t.tz()), 1, t, nb, m);
    }

    /** Couleur (r, g, b, a) du sommet v. */
    static int[] color(byte[] b, int v) {
        int o = v * LodMesher.VERTEX_BYTES + 4;
        return new int[]{b[o] & 0xFF, b[o + 1] & 0xFF, b[o + 2] & 0xFF, b[o + 3] & 0xFF};
    }

    /** Position (x, y, z) du sommet v en blocs relatifs au coin de la tuile : x, z en colonnes (u8) × (1 << level),
     *  y en i16 petit-boutiste (format compact 0.2.1). */
    static float[] pos(LodMesher.Mesh m, byte[] b, int v) {
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        int o = v * LodMesher.VERTEX_BYTES, c = 1 << m.level();
        return new float[]{(b[o] & 0xFF) * c, bb.getShort(o + 2), (b[o + 1] & 0xFF) * c};
    }

    public static void main(String[] args) {
        // 1. Plat 4×4 isolé : 1 dessus fusionné + 4 jupes fusionnées (une par bord).
        Msg.LodTile a = flat(0, 0, 0, 60, 4, 10, 5);
        LodMesher.Mesh m = mesh(a, LodMesher.NO_NEIGHBORS, LodMesher.NO_MASK);
        check("plat 4x4 : 5 quads opaques, 0 eau", m.opaqueQuads() == 5 && m.waterQuads() == 0, m.opaqueQuads() + "/" + m.waterQuads());
        check("plat 4x4 : 8 octets x 4 sommets x 5 quads", m.opaque().length == 5 * 4 * 8, "" + m.opaque().length);
        check("plat 4x4 : bornes y 5..10", m.minY() == 5 && m.maxY() == 10, m.minY() + ".." + m.maxY());
        int[] top = color(m.opaque(), 0);
        check("dessus ombre 1,0 = 5E9D34 opaque", top[0] == 0x5E && top[1] == 0x9D && top[2] == 0x34 && top[3] == 255, Arrays.toString(top));
        float[] p0 = pos(m, m.opaque(), 0), p2 = pos(m, m.opaque(), 2);
        check("dessus couvre 0..4 a y=10", p0[0] == 0 && p0[1] == 10 && p0[2] == 0 && p2[0] == 4 && p2[2] == 4, Arrays.toString(p0) + Arrays.toString(p2));
        // quads 1..2 : nord puis sud (0,8) ; 3..4 : ouest puis est (0,6)
        int[] north = color(m.opaque(), 4), east = color(m.opaque(), 16);
        check("flanc nord/sud ombre 0,8 (866043 -> 6B4D36)", north[0] == Math.round(0x86 * 0.8f) && north[1] == Math.round(0x60 * 0.8f)
                && north[2] == Math.round(0x43 * 0.8f), Arrays.toString(north));
        check("flanc est/ouest ombre 0,6 (866043 -> 50 3A 28)", east[0] == Math.round(0x86 * 0.6f) && east[1] == Math.round(0x60 * 0.6f)
                && east[2] == Math.round(0x43 * 0.6f), Arrays.toString(east));
        float[] n0 = pos(m, m.opaque(), 4), n1 = pos(m, m.opaque(), 5);
        check("jupe nord de y=5 a y=10 dans le plan z=0", n0[1] == 5 && n1[1] == 10 && n0[2] == 0 && n1[2] == 0, Arrays.toString(n0) + Arrays.toString(n1));

        // 2. Marche : colonne (1,1) à 12 : 5 dessus (glouton) + 4 jupes + 4 flancs de la marche.
        Msg.LodTile st = flat(0, 0, 0, 60, 4, 10, 5);
        st.top()[1 * 4 + 1] = 12;
        m = mesh(st, LodMesher.NO_NEIGHBORS, LodMesher.NO_MASK);
        check("marche : 13 quads (5 dessus + 4 jupes + 4 flancs)", m.opaqueQuads() == 13, "" + m.opaqueQuads());

        // 3. Niveau 2 (colonnes de 4 blocs), voisine est de même niveau 3 blocs plus bas : flanc est de 7 à 10, pas de jupe.
        Msg.LodTile w = flat(2, 0, 0, 60, 4, 10, 5), e = flat(2, 1, 0, 57, 4, 10, 5);
        LodMesher.Neighbors nb = (l, x, z) -> l == 2 && x == 1 && z == 0 ? e : null;
        m = mesh(w, nb, LodMesher.NO_MASK);
        check("voisine plus basse : 5 quads (dessus, 3 jupes, flanc est)", m.opaqueQuads() == 5, "" + m.opaqueQuads());
        float[] ev = pos(m, m.opaque(), 16), ev1 = pos(m, m.opaque(), 17);
        check("flanc est de y=7 a y=10 dans le plan x=16", ev[0] == 16 && ev[1] == 7 && ev1[1] == 10, Arrays.toString(ev) + Arrays.toString(ev1));
        Msg.LodTile same = flat(2, 1, 0, 60, 4, 10, 5);
        m = mesh(w, (l, x, z) -> l == 2 && x == 1 && z == 0 ? same : null, LodMesher.NO_MASK);
        check("voisine a la meme hauteur : pas de flanc est (4 quads)", m.opaqueQuads() == 4, "" + m.opaqueQuads());

        // 4. Masque : tuile 32×32 niveau 0, chunk réel (0,0) affiché : 256 colonnes masquées, 2 dessus + 6 flancs.
        Msg.LodTile big = flat(0, 0, 0, 60, 32, 10, 5);
        m = mesh(big, LodMesher.NO_NEIGHBORS, (cx, cz) -> cx == 0 && cz == 0);
        check("masque chunk (0,0) : 256 colonnes masquees", m.hidden() == 256, "" + m.hidden());
        check("masque chunk (0,0) : 8 quads (2 dessus + 4 jupes de bord + 2 jupes de raccord)", m.opaqueQuads() == 8, "" + m.opaqueQuads());
        m = mesh(big, LodMesher.NO_NEIGHBORS, (cx, cz) -> cx >= 0 && cx <= 1 && cz >= 0 && cz <= 1);
        check("masque total : rien a dessiner", m.empty() && m.hidden() == 1024, m.quads() + " " + m.hidden());
        m = mesh(big, LodMesher.NO_NEIGHBORS, LodMesher.NO_MASK);
        check("sans masque : 32x32 plat = 5 quads", m.opaqueQuads() == 5 && m.hidden() == 0, "" + m.opaqueQuads());

        // 5. Eau : 2×2 d'eau seule = 1 quad d'eau alpha 178 ; sol sous l'eau = sol + eau.
        int c = 4;
        byte[] fl = new byte[c];
        Arrays.fill(fl, (byte) Msg.LOD_WATER);
        int[] wt = new int[c], wr = new int[c];
        Arrays.fill(wt, 3); Arrays.fill(wr, 0x3F76E4);
        Msg.LodTile water = new Msg.LodTile(0, 0, 0, 60, 2, fl, new int[c], new int[c], new int[c], new int[c], wt, wr);
        m = mesh(water, LodMesher.NO_NEIGHBORS, LodMesher.NO_MASK);
        int[] wc = color(m.water(), 0);
        check("eau seule : 1 quad d'eau, alpha 178, 0 opaque", m.waterQuads() == 1 && m.opaqueQuads() == 0 && wc[3] == LodMesher.WATER_ALPHA
                && wc[2] == 0xE4, m.waterQuads() + " " + Arrays.toString(wc));
        Msg.LodTile ref = Msg.LodTile.decode(java.util.HexFormat.of().parseHex(
                "02FFFFFFFF0F033C020104035E9D3486604302023F76E4030101808080707070033F76E400"));
        m = mesh(ref, LodMesher.NO_NEIGHBORS, LodMesher.NO_MASK);
        // c0 (0,0) sol 4 ; c1 (1,0) eau seule 2 ; c2 (0,1) sol 1 + eau 3 ; c3 vide. Dessus : 2. Eau : c1 et c2 (wtop différents) : 2.
        // Flancs : nord 1 (jupe c0) ; sud 2 (c0 vers c2 de 1 à 4, jupe c2) ; ouest 2 et est 2 (jupes c0 et c2, hauteurs différentes).
        check("tuile de reference §9 : 9 quads opaques (2 dessus + 7 flancs), 2 eaux", m.waterQuads() == 2 && m.opaqueQuads() == 9,
                m.opaqueQuads() + "/" + m.waterQuads());

        // 6. Magasin + fil de travail : sale -> maillé ; une voisine arrivée re-salit la tuile ; forget -> retiré.
        LodStore s = new LodStore();
        LodWorker wk = new LodWorker(s, (where, t) -> { throw new AssertionError(where, t); });
        s.put(w);
        check("put : 1 tuile sale", s.hasDirty() && wk.runOnce(64) == 1 && !s.hasDirty(), "");
        LodMesher.Mesh first = wk.poll();
        check("maillage de la tuile seule : 5 quads (4 jupes + dessus)", first != null && first.opaqueQuads() == 5, first == null ? "null" : "" + first.opaqueQuads());
        s.put(e);
        check("voisine arrivee : 2 tuiles re-maillees", wk.runOnce(64) == 2, "");
        LodMesher.Mesh m1 = wk.poll(), m2 = wk.poll();
        LodMesher.Mesh mw = m1.key() == LodStore.key(2, 0, 0) ? m1 : m2;
        check("tuile ouest re-maillee avec le flanc est de raccord (5 quads, gen du magasin)", mw.opaqueQuads() == 5
                && mw.gen() == s.entry(LodStore.key(2, 0, 0)).gen(), "" + mw.opaqueQuads());
        int changed = s.setRealChunks(new long[]{LodStore.chunkKey(0, 0)});
        check("masque : 1 chunk change, la tuile qui le couvre est salie", changed == 1 && s.hasDirty() && s.isReal(0, 0) && !s.isReal(1, 0), "" + changed);
        wk.runOnce(64);
        while (wk.poll() != null) { }
        check("masque identique : aucun changement", s.setRealChunks(new long[]{LodStore.chunkKey(0, 0)}) == 0 && !s.hasDirty(), "");
        s.forget(new Msg.LodForget(2, 1, 0));
        Long rem = s.pollRemoved();
        check("forget : tuile retiree, voisine salie", rem != null && rem == LodStore.key(2, 1, 0) && s.size() == 1 && s.hasDirty(), "");
        check("eviction au-dela de 10 blocs de (1000, 1000)", s.evictBeyond(1000, 1000, 10) == 1 && s.size() == 0, "");
        long ep = s.epoch();
        s.put(w); s.forget(new Msg.LodForget(-1, 0, 0));
        check("forget(tout) : magasin vide, epoch avance", s.size() == 0 && s.epoch() == ep + 1, "");

        // 6 bis. Niveaux superposés : la tuile la plus fine l'emporte (colonnes grossières couvertes non dessinées).
        LodStore s3 = new LodStore();
        LodWorker w3 = new LodWorker(s3, (where, t) -> { throw new AssertionError(where, t); });
        Msg.LodTile coarse = flat(1, 0, 0, 60, 4, 10, 5); // colonnes de 2 blocs, tuile de 8 blocs en (0, 0)
        s3.put(coarse);
        w3.runOnce(64);
        LodMesher.Mesh c0 = w3.poll();
        check("grossiere seule : 5 quads, rien de cache", c0 != null && c0.opaqueQuads() == 5 && c0.hidden() == 0,
                c0 == null ? "null" : c0.opaqueQuads() + " cache " + c0.hidden());
        s3.put(flat(0, 0, 0, 60, 4, 10, 5)); // tuile fine de 4 blocs sur le coin (0..4, 0..4)
        check("couverture : (3,3) sous la fine, (5,5) non, niveau 0 jamais", s3.coveredFiner(1, 3, 3) && !s3.coveredFiner(1, 5, 5)
                && !s3.coveredFiner(0, 3, 3), "");
        int again = w3.runOnce(64);
        LodMesher.Mesh g1 = null;
        for (LodMesher.Mesh x; (x = w3.poll()) != null; ) if (x.level() == 1) g1 = x;
        // 12 colonnes visibles en L : 2 dessus ; 4 jupes extérieures ; 2 jupes le long de la zone couverte
        check("fine arrivee : la grossiere est re-maillee (4 colonnes cachees, 8 quads)", again == 2 && g1 != null
                && g1.hidden() == 4 && g1.opaqueQuads() == 8, again + " " + (g1 == null ? "null" : g1.hidden() + "/" + g1.opaqueQuads()));
        s3.forget(new Msg.LodForget(0, 0, 0));
        w3.runOnce(64);
        LodMesher.Mesh g2 = w3.poll();
        check("fine oubliee : la grossiere redevient entiere (5 quads)", g2 != null && g2.level() == 1 && g2.hidden() == 0
                && g2.opaqueQuads() == 5, g2 == null ? "null" : g2.hidden() + "/" + g2.opaqueQuads());
        s3.put(flat(1, 1, 0, 60, 4, 10, 5)); // grossière voisine à l'est
        w3.runOnce(64);
        while (w3.poll() != null) { }
        s3.put(flat(0, 2, 0, 60, 4, 10, 5)); // fine en (8..12, 0..4) : dans la voisine est, au bord de la première
        int touched = w3.runOnce(64);
        check("fine au bord : la grossiere qu'elle couvre ET la voisine dont le flanc la regarde sont re-maillees",
                touched == 3, "" + touched);
        while (w3.poll() != null) { }

        // 6 ter. Raccord atomique d'un oubli : le retrait nomme les tuiles qu'il salit et son horloge ; elles passent
        // en priorité, et leur maillage (commencé après) porte une horloge >= celle du retrait (le rendu attend ce
        // maillage avant de libérer l'ancienne tuile). Réveil du mailleur à l'arrivée et à l'oubli.
        int[] woken = {0};
        s3.setWaker(() -> woken[0]++);
        for (int i = 0; i < 40; i++) s3.put(flat(0, 10 + i, 10, 60, 4, 10, 5)); // 40 tuiles sales ailleurs
        check("arrivee : mailleur reveille", woken[0] == 40, "" + woken[0]);
        while (s3.pollRemovedEntry() != null) { } // retraits des essais précédents
        long before = s3.clock();
        s3.forget(new Msg.LodForget(0, 2, 0));
        LodStore.Removed rm = s3.pollRemovedEntry();
        long ck = LodStore.key(1, 1, 0), nk = LodStore.key(1, 0, 0);
        boolean namesCoarse = rm != null && Arrays.stream(rm.dirtied()).anyMatch(k -> k == ck) && Arrays.stream(rm.dirtied()).anyMatch(k -> k == nk);
        check("oubli : retrait avec tuiles salies (grossiere + voisine) et horloge", namesCoarse && rm.stamp() == before + 1
                && woken[0] == 41, rm == null ? "null" : Arrays.toString(rm.dirtied()) + " stamp " + rm.stamp());
        java.util.List<Long> prio = s3.drainDirty(2);
        check("oubli : les tuiles qui reprennent ses colonnes passent avant les 40 autres", prio.size() == 2
                && prio.contains(ck) && prio.contains(nk), prio.toString());
        s3.markDirty(ck);
        w3.runOnce(1);
        LodMesher.Mesh after = w3.poll();
        check("maillage commence apres l'oubli : horloge >= stamp", after != null && after.key() == ck && after.seq() >= rm.stamp(),
                after == null ? "null" : after.seq() + " < " + rm.stamp());
        s3.setWaker(null);

        // 7. Charge : 1 000 tuiles 32×32 bosselées maillées en temps raisonnable (fil de travail).
        LodStore big2 = new LodStore();
        java.util.Random r = new java.util.Random(7);
        for (int tx = 0; tx < 32; tx++)
            for (int tz = 0; tz < 32; tz++) {
                Msg.LodTile t = flat(1, tx, tz, 60, 32, 10, 8);
                for (int i = 0; i < 1024; i++) { t.top()[i] = 10 + r.nextInt(4); t.topRgb()[i] = 0x5E9D34 + r.nextInt(2); }
                big2.put(t);
            }
        LodWorker w2 = new LodWorker(big2, (where, t) -> { throw new AssertionError(where, t); });
        long t0 = System.nanoTime();
        int done = 0;
        long quads = 0;
        while (big2.hasDirty()) {
            done += w2.runOnce(256);
            LodMesher.Mesh x;
            while ((x = w2.poll()) != null) quads += x.quads();
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("     1024 tuiles 32x32 bosselees : %d maillages, %d quads, %.0f ms%n", done, quads, ms);
        check("1024 tuiles maillees en moins de 20 s", done == 1024 && ms < 20_000, done + " en " + ms + " ms");

        System.out.println("LodMesherTest : " + ok + " OK, " + ko + " FAIL");
        if (ko != 0) System.exit(1);
    }
}
