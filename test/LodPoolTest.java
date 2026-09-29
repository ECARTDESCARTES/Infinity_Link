import infinitylink.core.Msg;
import infinitylink.core.lod.LodMesher;
import infinitylink.core.lod.LodPool;
import infinitylink.core.lod.LodStore;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/** Java pur, sans écran : la réserve de sommets du rendu lointain (LodPool, 0.2.1) et le calcul de VRAM avant/après
 *  à nombre de tuiles égal (sommets de 16 octets dans un tampon par tuile et par matière, contre 8 octets dans des tas
 *  de 4 Mio). Tuiles synthétiques : relief doux, palette de couleurs, eau sous le niveau 63. */
public final class LodPoolTest {
    static int ok, ko;
    static final long HEAP = 4L << 20;

    static void check(String name, boolean cond, String detail) {
        if (cond) { ok++; System.out.println("OK   " + name); }
        else { ko++; System.out.println("FAIL " + name + " : " + detail); }
    }

    /** Tas simulés : taille par identifiant (ouverts), compteurs d'ouvertures et de fermetures. */
    static final class FakeHeaps implements LodPool.Heaps {
        final Map<Integer, Long> open = new HashMap<>();
        int opened, closed;
        public void open(int id, long bytes) { if (open.put(id, bytes) != null) throw new IllegalStateException("tas " + id + " rouvert"); opened++; }
        public void close(int id) { if (open.remove(id) == null) throw new IllegalStateException("tas " + id + " inconnu"); closed++; }
        long total() { long t = 0; for (long v : open.values()) t += v; return t; }
    }

    public static void main(String[] args) {
        unit();
        stress();
        vram();
        System.out.println("LodPoolTest : " + ok + " OK, " + ko + " FAIL");
        System.exit(ko == 0 ? 0 : 1);
    }

    static void unit() {
        FakeHeaps fh = new FakeHeaps();
        LodPool p = new LodPool(fh, HEAP, 3 * HEAP);
        LodPool.Alloc a = p.alloc(100), b = p.alloc(1000), c = p.alloc(40);
        check("alignement sur un quad (32 octets)", a.bytes() == 128 && b.offset() == 128 && b.bytes() == 1024 && c.offset() == 1152,
                a + " " + b + " " + c);
        check("un seul tas ouvert, VRAM = 4 Mio", p.heapCount() == 1 && p.committed() == HEAP && fh.total() == HEAP, p.committed() + "");
        p.free(b);
        LodPool.Alloc d = p.alloc(900);
        check("meilleur ajustement : le trou libéré est repris", d.offset() == 128, d.toString());
        p.free(a); p.free(d); p.free(c);
        check("tout libéré : trous fusionnés, dernier tas gardé", p.used() == 0 && p.heapCount() == 1 && p.holes() == HEAP, p.used() + "/" + p.heapCount());
        LodPool.Alloc x = p.alloc(HEAP), y = p.alloc(HEAP), z = p.alloc(HEAP);
        check("plafond : trois tas pleins, le quatrième est refusé", x != null && y != null && z != null && p.alloc(32) == null
                && p.committed() == 3 * HEAP, p.committed() + "");
        p.free(y);
        check("tas vide fermé aussitôt (VRAM rendue)", p.heapCount() == 2 && p.committed() == 2 * HEAP && fh.total() == 2 * HEAP, p.heapCount() + "");
        p.setCap(5 * HEAP);
        LodPool.Alloc big = p.alloc(HEAP + 1);
        check("morceau plus grand qu'un tas : tas dédié arrondi à 64 Kio", big != null && fh.open.get(big.heap()) == HEAP + (64 << 10),
                big + " " + fh.open.get(big == null ? -1 : big.heap()));
        boolean threw = false;
        try { p.free(x); p.free(new LodPool.Alloc(z.heap(), 0, 64)); p.free(new LodPool.Alloc(z.heap(), 0, 64)); } catch (IllegalStateException e) { threw = true; }
        check("double libération détectée", threw, "aucune exception");
        p.closeAll();
        check("closeAll : 0 octet réservé, tous les tas fermés", p.committed() == 0 && p.heapCount() == 0 && fh.open.isEmpty()
                && fh.opened == fh.closed, p.committed() + " " + fh.open);
    }

    /** 200 000 opérations aléatoires : jamais de chevauchement, comptes justes, VRAM ≤ plafond. */
    static void stress() {
        FakeHeaps fh = new FakeHeaps();
        long cap = 24L << 20;
        LodPool p = new LodPool(fh, HEAP, cap);
        Random r = new Random(7);
        List<LodPool.Alloc> live = new ArrayList<>();
        long refused = 0, maxCommitted = 0;
        boolean fine = true;
        for (int i = 0; i < 200_000 && fine; i++) {
            if (!live.isEmpty() && (r.nextInt(100) < 45 || p.used() > cap * 9 / 10)) {
                p.free(live.remove(r.nextInt(live.size())));
            } else {
                long want = 32 + (long) (Math.abs(r.nextGaussian()) * 60_000);
                LodPool.Alloc a = p.alloc(want);
                if (a == null) refused++; else live.add(a);
            }
            maxCommitted = Math.max(maxCommitted, p.committed());
            if (p.committed() > cap || p.committed() != fh.total()) fine = false;
        }
        Map<Integer, TreeMap<Long, Long>> byHeap = new HashMap<>();
        long sum = 0;
        for (LodPool.Alloc a : live) {
            TreeMap<Long, Long> m = byHeap.computeIfAbsent(a.heap(), k -> new TreeMap<>());
            var lo = m.floorEntry(a.offset());
            var hi = m.ceilingEntry(a.offset());
            if ((lo != null && lo.getKey() + lo.getValue() > a.offset()) || (hi != null && a.offset() + a.bytes() > hi.getKey())
                    || a.offset() + a.bytes() > fh.open.getOrDefault(a.heap(), 0L)) fine = false;
            m.put(a.offset(), a.bytes());
            sum += a.bytes();
        }
        check("stress : aucun chevauchement, VRAM <= plafond, octets comptés justes", fine && sum == p.used(),
                "used " + p.used() + " somme " + sum);
        System.out.printf("stress : %d morceaux vivants, %.1f Mio utilisés dans %.1f Mio réservés (%d tas), pic %.1f Mio, %d refus au plafond de %d Mio%n",
                live.size(), p.used() / 1048576.0, p.committed() / 1048576.0, p.heapCount(), maxCommitted / 1048576.0, refused, cap >> 20);
    }

    /** Tuile synthétique n×n : relief doux, couleurs d'une petite palette, eau sous 63. */
    static Msg.LodTile tile(int level, int tx, int tz, int n, Random r) {
        int c = n * n, cell = 1 << level;
        byte[] f = new byte[c];
        int[] top = new int[c], depth = new int[c], trgb = new int[c], srgb = new int[c], wtop = new int[c], wrgb = new int[c];
        int[] pal = {0x5E9D34, 0x6A9F3C, 0x7FA84A, 0x8B8B8B, 0xC2B280, 0x4F7A28};
        long ox = (long) tx * n * cell, oz = (long) tz * n * cell;
        int[] h = new int[c];
        int min = Integer.MAX_VALUE;
        for (int z = 0; z < n; z++)
            for (int x = 0; x < n; x++) {
                double wx = ox + (x + 0.5) * cell, wz = oz + (z + 0.5) * cell;
                h[z * n + x] = (int) Math.round(66 + 14 * Math.sin(wx / 97.0) * Math.cos(wz / 131.0) + 6 * Math.sin((wx + wz) / 37.0));
                min = Math.min(min, Math.min(h[z * n + x], 63));
            }
        for (int i = 0; i < c; i++) {
            f[i] = (byte) Msg.LOD_GROUND;
            top[i] = h[i] - min;
            depth[i] = 1 + r.nextInt(4);
            trgb[i] = pal[(h[i] / 3 + i % 2) % pal.length];
            srgb[i] = 0x866043;
            if (h[i] < 63) { f[i] |= (byte) Msg.LOD_WATER; wtop[i] = 63 - min; wrgb[i] = 0x3F76E4; }
        }
        return new Msg.LodTile(level, tx, tz, min, n, f, top, depth, trgb, srgb, wtop, wrgb);
    }

    /** VRAM avant/après à nombre de tuiles égal : les 543 tuiles utiles de la vue 128 (L0-L3 au prorata de 268/197/202/165). */
    static void vram() {
        int[] perLevel = {175, 129, 132, 107}; // 543 = 832 servies − 289 masquées, au prorata des niveaux
        int n = 32;
        Random r = new Random(11);
        List<LodMesher.Mesh> meshes = new ArrayList<>();
        long quads = 0, badVertices = 0;
        int buffers = 0;
        for (int l = 0; l < perLevel.length; l++)
            for (int i = 0; i < perLevel[l]; i++) {
                int tx = i % 16 - 8, tz = i / 16 - 4;
                Msg.LodTile t = tile(l, tx, tz, n, r);
                LodMesher.Mesh m = LodMesher.mesh(LodStore.key(l, tx, tz), 1, t, LodMesher.NO_NEIGHBORS, LodMesher.NO_MASK);
                meshes.add(m);
                quads += m.quads();
                buffers += (m.opaqueQuads() > 0 ? 1 : 0) + (m.waterQuads() > 0 ? 1 : 0);
                badVertices += outOfTile(m);
            }
        check("sommets compacts dans la tuile (x, z <= span, y dans [minY, maxY])", badVertices == 0, badVertices + " hors tuile");
        long before = quads * 4 * 16, after = quads * 4 * LodMesher.VERTEX_BYTES;
        FakeHeaps fh = new FakeHeaps();
        LodPool p = new LodPool(fh, HEAP, 1L << 40);
        Map<Long, LodPool.Alloc> resident = new HashMap<>();
        for (LodMesher.Mesh m : meshes) resident.put(m.key(), p.alloc(LodPool.align(m.opaque().length) + m.water().length));
        long committed1 = p.committed();
        // Trois remaillages complets dans le désordre (masque, voisines, arrivée des tuiles) : fragmentation.
        for (int phase = 0; phase < 3; phase++) {
            List<LodMesher.Mesh> sh = new ArrayList<>(meshes);
            java.util.Collections.shuffle(sh, r);
            for (LodMesher.Mesh m : sh) {
                long need = (long) ((LodPool.align(m.opaque().length) + m.water().length) * (0.8 + 0.4 * r.nextDouble()));
                LodPool.Alloc nu = p.alloc(Math.max(32, need));
                p.free(resident.put(m.key(), nu));
            }
        }
        check("réserve : VRAM réservée <= données alignées + 1 tas", committed1 <= after + meshes.size() * 2L * LodPool.ALIGN + HEAP, committed1 + "");
        check("sommets : moitié exacte de l'ancien format à tuiles égales", after * 2 == before, before + " / " + after);
        System.out.printf("VRAM à tuiles égales (%d tuiles n=%d, %d quads) :%n", meshes.size(), n, quads);
        System.out.printf("  avant 0.2.0 : %.2f Mio de sommets (16 o) dans %d tampons GPU (un par tuile et par matière)%n", before / 1048576.0, buffers);
        System.out.printf("  après 0.2.1 : %.2f Mio de sommets (8 o), réservés %.2f Mio en %d tas de 4 Mio (trous %.2f Mio) ; "
                        + "après 3 remaillages : %.2f Mio réservés en %d tas, pic %.2f Mio%n",
                after / 1048576.0, committed1 / 1048576.0, (int) (committed1 / HEAP), (committed1 - after) / 1048576.0,
                p.committed() / 1048576.0, p.heapCount(), p.peak() / 1048576.0);
        System.out.printf("  rapport sommets après/avant : %.3f ; hauteurs bornées : %d%n", (double) after / before, LodMesher.CLAMPED_Y.get());
    }

    static long outOfTile(LodMesher.Mesh m) {
        long bad = 0;
        int c = 1 << m.level();
        for (byte[] b : new byte[][]{m.opaque(), m.water()}) {
            ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
            for (int o = 0; o + LodMesher.VERTEX_BYTES <= b.length; o += LodMesher.VERTEX_BYTES) {
                int x = (b[o] & 0xFF) * c, z = (b[o + 1] & 0xFF) * c, y = bb.getShort(o + 2);
                if (x > m.span() || z > m.span() || y < m.minY() || y > m.maxY()) bad++;
            }
        }
        return bad;
    }
}
