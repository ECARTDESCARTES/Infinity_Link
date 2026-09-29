package infinitylink.core.lod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Réserve des sommets du rendu lointain (Java pur, testée par test/LodPoolTest.java) : quelques gros tas au lieu d'un
 *  tampon GPU par tuile et par matière (0.2.0 : environ 1 100 tampons à la vue 128). Allocation au meilleur ajustement,
 *  par tranches de ALIGN octets (un quad : 4 sommets de LodMesher.VERTEX_BYTES), trous voisins fusionnés à la
 *  libération, tas vide fermé aussitôt (le dernier ouvert est gardé tant que la réserve vit, closeAll le ferme).
 *  Plafond : la somme des tas ouverts, c'est-à-dire la VRAM réellement réservée, ne dépasse jamais cap. Les tas sont
 *  créés et fermés par l'appelant (Heaps : GpuBuffer côté jeu, rien dans les tests). Un seul fil (le fil de rendu). */
public final class LodPool {
    /** Granularité : un quad de sommets compacts (4 × 8 octets) ; tout décalage est donc un multiple de la taille d'un sommet. */
    public static final int ALIGN = 4 * LodMesher.VERTEX_BYTES;
    /** Taille d'un tas dédié (allocation plus grande qu'un tas) : arrondie à 64 Kio. */
    static final long DEDICATED_ROUND = 64 << 10;

    /** Création et fermeture des tas (identifiants attribués par la réserve). */
    public interface Heaps {
        void open(int id, long bytes);
        void close(int id);
    }

    /** Morceau alloué : tas, décalage et longueur alignés (bytes ≥ la taille demandée). */
    public record Alloc(int heap, long offset, long bytes) {}

    private static final class Heap {
        final int id;
        final long size;
        long used;
        /** Trous : décalage -> longueur, jamais deux trous contigus. */
        final TreeMap<Long, Long> free = new TreeMap<>();
        Heap(int id, long size) { this.id = id; this.size = size; free.put(0L, size); }
    }

    private final Heaps heaps;
    private final long heapBytes;
    private long cap;
    private final Map<Integer, Heap> open = new HashMap<>();
    private int nextId;
    private long committed, used, peak;

    public LodPool(Heaps heaps, long heapBytes, long cap) {
        if (heapBytes <= 0 || heapBytes % ALIGN != 0) throw new IllegalArgumentException("tas de " + heapBytes + " octets");
        this.heaps = heaps;
        this.heapBytes = heapBytes;
        this.cap = cap;
    }

    public static long align(long bytes) { return (bytes + ALIGN - 1) / ALIGN * ALIGN; }

    public long cap() { return cap; }
    public void setCap(long cap) { this.cap = cap; }
    /** VRAM réservée (somme des tas ouverts). */
    public long committed() { return committed; }
    /** Octets alloués (alignés) dans les tas. */
    public long used() { return used; }
    public long peak() { return peak; }
    public int heapCount() { return open.size(); }
    public long heapBytes() { return heapBytes; }

    /** Alloue bytes octets, ou null si aucun trou ne convient et qu'un nouveau tas dépasserait le plafond. */
    public Alloc alloc(long bytes) {
        if (bytes <= 0) throw new IllegalArgumentException("allocation de " + bytes + " octets");
        long need = align(bytes);
        Heap best = null;
        long bestOff = -1, bestLen = Long.MAX_VALUE;
        for (Heap h : open.values()) {
            if (h.size - h.used < need) continue;
            for (Map.Entry<Long, Long> e : h.free.entrySet()) {
                long len = e.getValue();
                if (len >= need && len < bestLen) { best = h; bestOff = e.getKey(); bestLen = len; }
            }
            if (bestLen == need) break;
        }
        if (best == null) {
            long size = need <= heapBytes ? heapBytes : (need + DEDICATED_ROUND - 1) / DEDICATED_ROUND * DEDICATED_ROUND;
            if (committed + size > cap) return null;
            int id = nextId++;
            heaps.open(id, size);
            best = new Heap(id, size);
            open.put(id, best);
            committed += size;
            peak = Math.max(peak, committed);
            bestOff = 0;
            bestLen = size;
        }
        best.free.remove(bestOff);
        if (bestLen > need) best.free.put(bestOff + need, bestLen - need);
        best.used += need;
        used += need;
        return new Alloc(best.id, bestOff, need);
    }

    /** Rend un morceau ; le tas vide est fermé sauf s'il est le dernier ouvert. Sans effet sur un tas déjà fermé. */
    public void free(Alloc a) {
        if (a == null) return;
        Heap h = open.get(a.heap());
        if (h == null) return;
        long off = a.offset(), len = a.bytes();
        Map.Entry<Long, Long> lo = h.free.floorEntry(off);
        if (lo != null && lo.getKey() + lo.getValue() > off) throw new IllegalStateException("double liberation " + a);
        Map.Entry<Long, Long> hi = h.free.ceilingEntry(off);
        if (hi != null && off + len > hi.getKey()) throw new IllegalStateException("liberation chevauchante " + a);
        if (lo != null && lo.getKey() + lo.getValue() == off) { off = lo.getKey(); len += lo.getValue(); h.free.remove(lo.getKey()); }
        if (hi != null && a.offset() + a.bytes() == hi.getKey()) { len += hi.getValue(); h.free.remove(hi.getKey()); }
        h.free.put(off, len);
        h.used -= a.bytes();
        used -= a.bytes();
        if (h.used == 0 && open.size() > 1) close(h);
    }

    /** Ferme tous les tas (déconnexion, changement de monde, coupure) : la VRAM de la réserve revient à 0. */
    public void closeAll() {
        List<Heap> all = new ArrayList<>(open.values());
        for (Heap h : all) close(h);
        used = 0;
    }

    private void close(Heap h) {
        open.remove(h.id);
        committed -= h.size;
        used -= h.used;
        heaps.close(h.id);
    }

    /** Somme des trous (octets réservés mais libres) : fragmentation. */
    public long holes() { return committed - used; }
}
