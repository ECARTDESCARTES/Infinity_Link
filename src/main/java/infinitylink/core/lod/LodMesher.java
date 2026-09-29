package infinitylink.core.lod;

import infinitylink.core.Msg;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Mailleur lod façon Distant Horizons, Java pur (testé sans écran par test/LodMesherTest.java).
 *  Par colonne visible : quad du dessus ; flancs là où la voisine est plus basse (dans la tuile, ou dans la tuile
 *  voisine de même niveau), sinon jupe de hauteur depth (voisine absente, sans sol, ou masquée par le vrai terrain) ;
 *  eau semi-transparente à part. Couleurs de sommet ombrées par face (dessus 1,0 ; nord/sud 0,8 ; est/ouest 0,6).
 *  Fusion gloutonne des quads coplanaires de même couleur (dessus et eau en 2D, flancs en 1D).
 *  Sommets compacts (0.2.1) : 8 octets = x, z en u8 (indice de COLONNE, 0..n <= 64, relatif au coin de la tuile),
 *  y en i16 petit-boutiste (blocs, relatif à yBase, borné à ±32767 : seules des jupes plus profondes que 32 767 blocs
 *  seraient raccourcies, compteur CLAMPED_Y), puis r, g, b, a en u8. Le shader multiplie x et z par la taille de
 *  colonne (1 << level, passée par TextureMat) : positions identiques au bloc près à l'ancien format float (16 octets).
 *  Format LodShaders.FORMAT (Position RGBA8_UINT + Color RGBA8_UNORM) ; 4 sommets par quad, indices 0,1,2,2,3,0. */
public final class LodMesher {
    private LodMesher() {}

    public static final float SHADE_TOP = 1.0f, SHADE_NS = 0.8f, SHADE_EW = 0.6f;
    /** Les colonnes sont en 2,5D (aucun surplomb) : aucune face du dessous n'est émise ; ombre prévue si un jour. */
    public static final float SHADE_BOTTOM = 0.5f;
    public static final int WATER_ALPHA = 178; // ≈ 0,7
    public static final int VERTEX_BYTES = 8;
    /** Hauteurs de sommet bornées à l'intervalle i16 (jamais vu : les hauteurs du monde tiennent sur 12 bits). */
    public static final java.util.concurrent.atomic.AtomicLong CLAMPED_Y = new java.util.concurrent.atomic.AtomicLong();

    /** Voisine de même niveau (null si absente). */
    public interface Neighbors { Msg.LodTile at(int level, int tx, int tz); }
    /** true si le vrai chunk (cx, cz) est affiché : ses colonnes lod ne sont pas dessinées. */
    public interface Mask { boolean real(int cx, int cz); }

    /** true si une tuile d'un niveau plus fin que level couvre le point (bx, bz) : le plus fin l'emporte. */
    public interface Cover { boolean finer(int level, long bx, long bz); }

    public static final Mask NO_MASK = (cx, cz) -> false;
    public static final Cover NO_COVER = (l, x, z) -> false;
    public static final Neighbors NO_NEIGHBORS = (l, x, z) -> null;

    /** Résultat : tampons de sommets opaque et eau, bornes verticales relatives à yBase, compteurs. */
    public record Mesh(long key, long gen, int level, long originX, int originY, long originZ, int span,
                       byte[] opaque, int opaqueQuads, byte[] water, int waterQuads, int minY, int maxY, int hidden, long seq) {
        public int quads() { return opaqueQuads + waterQuads; }
        public boolean empty() { return quads() == 0; }
        /** Même maillage, commencé à l'horloge seq du magasin (LodStore.clock) : il voit tout retrait de stamp <= seq. */
        public Mesh withSeq(long s) {
            return new Mesh(key, gen, level, originX, originY, originZ, span, opaque, opaqueQuads, water, waterQuads, minY, maxY, hidden, s);
        }
    }

    /** Tampon de quads extensible. */
    static final class Quads {
        ByteBuffer b = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
        int quads, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        void vertex(int xc, int y, int zc, int rgb, float shade, int alpha) {
            if (b.remaining() < VERTEX_BYTES) {
                ByteBuffer nb = ByteBuffer.allocate(b.capacity() * 2).order(ByteOrder.LITTLE_ENDIAN);
                b.flip();
                nb.put(b);
                b = nb;
            }
            if (y < Short.MIN_VALUE || y > Short.MAX_VALUE) { CLAMPED_Y.incrementAndGet(); y = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, y)); }
            b.put((byte) xc).put((byte) zc).putShort((short) y);
            b.put((byte) Math.round(((rgb >>> 16) & 0xFF) * shade));
            b.put((byte) Math.round(((rgb >>> 8) & 0xFF) * shade));
            b.put((byte) Math.round((rgb & 0xFF) * shade));
            b.put((byte) alpha);
        }

        void y(int y) { if (y < minY) minY = y; if (y > maxY) maxY = y; }

        /** Quad horizontal à la hauteur y, de (x0, z0) à (x1, z1). */
        void flat(int x0, int z0, int x1, int z1, int y, int rgb, float shade, int alpha) {
            vertex(x0, y, z0, rgb, shade, alpha);
            vertex(x0, y, z1, rgb, shade, alpha);
            vertex(x1, y, z1, rgb, shade, alpha);
            vertex(x1, y, z0, rgb, shade, alpha);
            quads++;
            y(y);
        }

        /** Quad vertical dans le plan z = z (de x0 à x1) ou x = x (de z0 à z1), de lo à hi. */
        void wallZ(int z, int x0, int x1, int lo, int hi, int rgb, float shade) {
            vertex(x0, lo, z, rgb, shade, 255);
            vertex(x0, hi, z, rgb, shade, 255);
            vertex(x1, hi, z, rgb, shade, 255);
            vertex(x1, lo, z, rgb, shade, 255);
            quads++;
            y(lo); y(hi);
        }
        void wallX(int x, int z0, int z1, int lo, int hi, int rgb, float shade) {
            vertex(x, lo, z0, rgb, shade, 255);
            vertex(x, hi, z0, rgb, shade, 255);
            vertex(x, hi, z1, rgb, shade, 255);
            vertex(x, lo, z1, rgb, shade, 255);
            quads++;
            y(lo); y(hi);
        }

        byte[] bytes() { return Arrays.copyOf(b.array(), b.position()); }
    }

    public static Mesh mesh(long key, long gen, Msg.LodTile t, Neighbors nb, Mask mask) {
        return mesh(key, gen, t, nb, mask, NO_COVER);
    }

    /** Colonnes cachées : chunk réel affiché (mask) ou zone couverte par une tuile plus fine (cover). */
    public static Mesh mesh(long key, long gen, Msg.LodTile t, Neighbors nb, Mask realMask, Cover cover) {
        final int lvl = t.level();
        final Hide mask = lvl == 0 || cover == NO_COVER
                ? (x, z) -> realMask.real((int) Math.floorDiv(x, 16L), (int) Math.floorDiv(z, 16L))
                : (x, z) -> realMask.real((int) Math.floorDiv(x, 16L), (int) Math.floorDiv(z, 16L)) || cover.finer(lvl, x, z);
        final int n = t.n(), s = t.cell(), c = n * n;
        final long ox = t.originX(), oz = t.originZ();
        boolean[] hidden = new boolean[c];
        boolean[] ground = new boolean[c];
        int hiddenCount = 0;
        for (int z = 0; z < n; z++)
            for (int x = 0; x < n; x++) {
                int i = z * n + x;
                hidden[i] = columnHidden(ox + (long) x * s, oz + (long) z * s, s, mask);
                if (hidden[i]) hiddenCount++;
                ground[i] = t.ground(i) && !hidden[i];
            }

        Quads op = new Quads(), wa = new Quads();

        // Dessus (fusion 2D sur hauteur + couleur).
        long[] topKey = new long[c];
        boolean[] topOk = new boolean[c];
        for (int i = 0; i < c; i++) {
            topOk[i] = ground[i];
            topKey[i] = ((long) t.top()[i] << 24) | (t.topRgb()[i] & 0xFFFFFF);
        }
        greedy(n, topOk, topKey, (x0, z0, w, h, k) ->
                op.flat(x0, z0, x0 + w, z0 + h, (int) (k >> 24), (int) (k & 0xFFFFFF), SHADE_TOP, 255));

        // Eau (fusion 2D), seulement au-dessus du sol.
        long[] wKey = new long[c];
        boolean[] wOk = new boolean[c];
        for (int i = 0; i < c; i++) {
            wOk[i] = t.water(i) && !hidden[i] && !(t.ground(i) && t.top()[i] >= t.wtop()[i]);
            wKey[i] = ((long) t.wtop()[i] << 24) | (t.waterRgb()[i] & 0xFFFFFF);
        }
        greedy(n, wOk, wKey, (x0, z0, w, h, k) ->
                wa.flat(x0, z0, x0 + w, z0 + h, (int) (k >> 24), (int) (k & 0xFFFFFF), SHADE_TOP, WATER_ALPHA));

        // Flancs : 4 directions ; lo = hauteur de la voisine (relative à notre yBase) ou jupe h - depth.
        int[] lo = new int[n];
        boolean[] has = new boolean[n];
        // nord (-z) et sud (+z) : lignes le long de x
        for (int dir = 0; dir < 2; dir++) {
            int dz = dir == 0 ? -1 : 1;
            for (int z = 0; z < n; z++) {
                for (int x = 0; x < n; x++) side(t, nb, mask, ground, x, z, 0, dz, lo, has, x);
                final int zz = z;
                int plane = dir == 0 ? z : z + 1; // en colonnes
                runs(n, has, lo, x -> t.top()[zz * n + x], x -> t.sideRgb()[zz * n + x],
                        (a, b, l, h, rgb) -> op.wallZ(plane, a, b, l, h, rgb, SHADE_NS));
            }
        }
        // ouest (-x) et est (+x) : colonnes le long de z
        for (int dir = 0; dir < 2; dir++) {
            int dx = dir == 0 ? -1 : 1;
            for (int x = 0; x < n; x++) {
                for (int z = 0; z < n; z++) side(t, nb, mask, ground, x, z, dx, 0, lo, has, z);
                final int xx = x;
                int plane = dir == 0 ? x : x + 1;
                runs(n, has, lo, z -> t.top()[z * n + xx], z -> t.sideRgb()[z * n + xx],
                        (a, b, l, h, rgb) -> op.wallX(plane, a, b, l, h, rgb, SHADE_EW));
            }
        }

        int minY = Math.min(op.minY, wa.minY), maxY = Math.max(op.maxY, wa.maxY);
        if (minY == Integer.MAX_VALUE) { minY = 0; maxY = 0; }
        return new Mesh(key, gen, t.level(), ox, t.yBase(), oz, t.span(), op.bytes(), op.quads, wa.bytes(), wa.quads, minY, maxY, hiddenCount, 0L);
    }

    /** Masque évalué au centre d'une colonne (blocs). */
    interface Hide { boolean at(long bx, long bz); }

    /** La colonne est masquée si son centre est dans un chunk affiché en vrai ou sous une tuile plus fine. */
    static boolean columnHidden(long bx, long bz, int s, Hide mask) {
        return mask.at(bx + s / 2, bz + s / 2);
    }

    /** Flanc de la colonne (x, z) vers (dx, dz) : has[slot] et lo[slot] (bas du flanc, relatif à t.yBase). */
    static void side(Msg.LodTile t, Neighbors nb, Hide mask, boolean[] ground, int x, int z, int dx, int dz,
                     int[] lo, boolean[] has, int slot) {
        int n = t.n(), i = z * n + x;
        has[slot] = false;
        if (!ground[i]) return;
        int h = t.top()[i];
        Integer hn = null;
        int nx = x + dx, nz = z + dz;
        if (nx >= 0 && nx < n && nz >= 0 && nz < n) {
            int j = nz * n + nx;
            if (ground[j]) hn = t.top()[j];
        } else {
            Msg.LodTile o = nb.at(t.level(), t.tx() + (nx < 0 ? -1 : nx >= n ? 1 : 0), t.tz() + (nz < 0 ? -1 : nz >= n ? 1 : 0));
            if (o != null && o.n() == n) {
                int ox = Math.floorMod(nx, n), oz = Math.floorMod(nz, n), j = oz * n + ox;
                if (o.ground(j) && !columnHidden(o.originX() + (long) ox * t.cell(), o.originZ() + (long) oz * t.cell(), t.cell(), mask))
                    hn = o.yBase() + o.top()[j] - t.yBase();
            }
        }
        int l = hn != null ? hn : h - t.depth()[i];
        if (l >= h) return;
        has[slot] = true;
        lo[slot] = l;
    }

    interface IntFn { int at(int i); }
    interface RunSink { void run(int a, int b, int lo, int hi, int rgb); }
    interface RectSink { void rect(int x0, int z0, int w, int h, long key); }

    /** Fusion 1D des flancs consécutifs de mêmes (lo, hi, couleur). */
    static void runs(int n, boolean[] has, int[] lo, IntFn hi, IntFn rgb, RunSink sink) {
        int a = 0;
        while (a < n) {
            if (!has[a]) { a++; continue; }
            int l = lo[a], h = hi.at(a), c = rgb.at(a), b = a + 1;
            while (b < n && has[b] && lo[b] == l && hi.at(b) == h && rgb.at(b) == c) b++;
            sink.run(a, b, l, h, c);
            a = b;
        }
    }

    /** Fusion gloutonne 2D : rectangles maximaux en x puis en z de cellules ok de même clé. */
    static void greedy(int n, boolean[] ok, long[] key, RectSink sink) {
        boolean[] used = new boolean[n * n];
        for (int z = 0; z < n; z++)
            for (int x = 0; x < n; x++) {
                int i = z * n + x;
                if (!ok[i] || used[i]) continue;
                long k = key[i];
                int w = 1;
                while (x + w < n && ok[i + w] && !used[i + w] && key[i + w] == k) w++;
                int h = 1;
                grow:
                while (z + h < n) {
                    int row = (z + h) * n + x;
                    for (int d = 0; d < w; d++) if (!ok[row + d] || used[row + d] || key[row + d] != k) break grow;
                    h++;
                }
                for (int dz = 0; dz < h; dz++) Arrays.fill(used, (z + dz) * n + x, (z + dz) * n + x + w, true);
                sink.rect(x, z, w, h, k);
            }
    }
}
