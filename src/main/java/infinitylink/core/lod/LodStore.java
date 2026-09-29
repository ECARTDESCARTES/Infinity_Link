package infinitylink.core.lod;

import infinitylink.core.Msg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/** Magasin des tuiles lod (contrat §9), Java pur et thread-safe : écriture sur le fil réseau, maillage sur le fil
 *  de travail, masque des chunks réels et éviction sur le fil client. Chaque changement qui touche la géométrie
 *  d'une tuile (elle-même, une voisine de même niveau, le masque) la marque « sale » pour le mailleur. */
public final class LodStore {
    /** Une tuile et sa génération (croît à chaque remplacement : un maillage d'une génération passée est jeté). */
    public record Entry(Msg.LodTile tile, long gen) {}

    private final Map<Long, Entry> tiles = new ConcurrentHashMap<>();
    private final Set<Long> dirty = ConcurrentHashMap.newKeySet();
    /** Sous-ensemble prioritaire de dirty : tuiles qui doivent reprendre au plus vite des colonnes laissées par un oubli
     *  ou par un changement du masque (sinon un trou se voit). Le mailleur les prend en premier. */
    private final Set<Long> urgent = ConcurrentHashMap.newKeySet();
    /** Tuile retirée (clé, génération retirée, tuiles salies par le retrait, horloge du retrait) : le rendu ne libère
     *  que des tampons de cette génération ou plus anciens (une tuile oubliée puis renvoyée aussitôt garde son nouveau
     *  maillage), et seulement une fois envoyés les maillages des tuiles salies commencés après stamp (raccord
     *  atomique : jamais d'image où ni l'ancienne ni sa remplaçante ne dessine). */
    public record Removed(long key, long gen, long[] dirtied, long stamp) {}
    private final ConcurrentLinkedQueue<Removed> removed = new ConcurrentLinkedQueue<>();
    private final AtomicLong gen = new AtomicLong();
    /** Horloge des retraits : un maillage commencé à clock() ≥ stamp voit le retrait. */
    private final AtomicLong clock = new AtomicLong();
    private volatile long epoch;
    /** Réveil du mailleur (arrivée, oubli, masque) : pas d'attente de sa sieste. */
    private volatile Runnable waker;
    /** Par niveau, les valeurs de n déjà reçues (bit n-1) : sert à trouver la tuile d'un niveau qui couvre un point. */
    private final AtomicLongArray nSeen = new AtomicLongArray(Msg.LOD_MAX_LEVEL + 1);
    /** Chunks (clé chunkKey) dont le vrai terrain est affiché par le client : leurs colonnes ne sont pas dessinées. */
    private volatile long[] real = new long[0];

    public final AtomicLong rx = new AtomicLong(), forgets = new AtomicLong(), evicted = new AtomicLong(), meshed = new AtomicLong();
    // Compteurs publiés par le rendu (fil client), lus par l'oracle JSON.
    public volatile int gpuTiles, drawnTiles;
    public volatile long gpuQuads, gpuBytes;
    // 0.2.1 : réserve de sommets (LodPool) : VRAM réservée, pic, plafond, manques de place, tuiles évincées pour la
    // place, tas ouverts, maillages en attente de place, vue réduite par le plafond (0 = jamais).
    public volatile long gpuReserved, gpuPeak, gpuCap, capHits, capEvicted;
    public volatile int gpuHeaps, gpuWaiting, viewCut;
    public volatile boolean pipelineReady;
    /** Raison de la coupure du lod ("" = actif ou jamais coupé). */
    public volatile String off = "";

    // ---- clés
    public static long key(int level, int tx, int tz) {
        return ((long) (level & 0x7) << 58) | ((long) (tx & 0x1FFFFFFF) << 29) | (tz & 0x1FFFFFFFL);
    }
    public static int keyLevel(long k) { return (int) (k >>> 58) & 0x7; }
    public static int keyX(long k) { return ((int) (k >>> 29) << 3) >> 3; }
    public static int keyZ(long k) { return ((int) k << 3) >> 3; }
    public static long chunkKey(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }

    // ---- lecture
    public int size() { return tiles.size(); }
    public Entry entry(long key) { return tiles.get(key); }
    public Msg.LodTile tile(int level, int tx, int tz) {
        Entry e = tiles.get(key(level, tx, tz));
        return e == null ? null : e.tile();
    }
    /** Incrémenté par clear() : le rendu libère alors tout ce qu'il détient. */
    public long epoch() { return epoch; }
    public List<Long> keys() { return new ArrayList<>(tiles.keySet()); }

    public long clock() { return clock.get(); }
    public void setWaker(Runnable r) { waker = r; }
    private void wake() {
        Runnable r = waker;
        if (r != null) r.run();
    }

    // ---- écriture (fil réseau)
    public void put(Msg.LodTile t) {
        long k = key(t.level(), t.tx(), t.tz());
        long bit = 1L << (t.n() - 1);
        nSeen.accumulateAndGet(t.level(), bit, (a, b) -> a | b);
        tiles.put(k, new Entry(t, gen.incrementAndGet()));
        rx.incrementAndGet();
        dirty.add(k);
        markNeighbors(t.level(), t.tx(), t.tz(), null);
        markCoarser(t, null);
        wake();
    }

    public void forget(Msg.LodForget f) {
        forgets.incrementAndGet();
        if (f.all()) { clear(); return; }
        remove(key(f.level(), f.tx(), f.tz()));
        wake();
    }

    private void remove(long k) {
        Entry e = tiles.remove(k);
        if (e != null) {
            dirty.remove(k);
            urgent.remove(k);
            List<Long> touched = new ArrayList<>();
            markNeighbors(keyLevel(k), keyX(k), keyZ(k), touched);
            markCoarser(e.tile(), touched);
            long[] d = new long[touched.size()];
            for (int i = 0; i < d.length; i++) d[i] = touched.get(i);
            // horloge relevée APRÈS le retrait et les marques : un maillage commencé ensuite voit la tuile partie
            removed.add(new Removed(k, e.gen(), d, clock.incrementAndGet()));
        }
    }

    /** Marque sale ; touched != null : prioritaire et relevée (tuiles qui reprennent les colonnes d'un oubli). */
    private void mark(long k, List<Long> touched) {
        if (!tiles.containsKey(k)) return;
        if (touched != null) { urgent.add(k); touched.add(k); }
        dirty.add(k);
    }

    /** Les niveaux se recouvrent (anneaux du serveur, tuiles restées d'un ancien palier quand le joueur bouge) :
     *  le plus fin l'emporte. Une tuile fine arrivée ou oubliée salit donc les tuiles plus grossières qu'elle touche. */
    private void markCoarser(Msg.LodTile t, List<Long> touched) {
        // un bloc de marge : la tuile grossière voisine dont le flanc de bord regarde la zone couverte change aussi
        long x0 = t.originX() - 1, z0 = t.originZ() - 1, x1 = t.originX() + t.span(), z1 = t.originZ() + t.span();
        for (int l = t.level() + 1; l <= Msg.LOD_MAX_LEVEL; l++) {
            long m = nSeen.get(l);
            while (m != 0) {
                int n = Long.numberOfTrailingZeros(m) + 1;
                m &= m - 1;
                long span = (long) n << l;
                long ax = Math.floorDiv(x0, span), bx = Math.floorDiv(x1, span);
                long az = Math.floorDiv(z0, span), bz = Math.floorDiv(z1, span);
                if ((bx - ax + 1) * (bz - az + 1) > 64) continue; // impossible pour une tuile plus fine ; borne de sûreté
                for (long tx = ax; tx <= bx; tx++)
                    for (long tz = az; tz <= bz; tz++) mark(key(l, (int) tx, (int) tz), touched);
            }
        }
    }

    /** true si une tuile d'un niveau plus fin que level couvre le point (bx, bz) (blocs) : la colonne grossière qui
     *  contient ce point n'est pas dessinée (pas de double géométrie ni de scintillement entre niveaux). */
    public boolean coveredFiner(int level, long bx, long bz) {
        for (int l = 0; l < level && l <= Msg.LOD_MAX_LEVEL; l++) {
            long m = nSeen.get(l);
            while (m != 0) {
                int n = Long.numberOfTrailingZeros(m) + 1;
                m &= m - 1;
                long span = (long) n << l;
                Entry e = tiles.get(key(l, (int) Math.floorDiv(bx, span), (int) Math.floorDiv(bz, span)));
                if (e != null && e.tile().n() == n) return true;
            }
        }
        return false;
    }

    private void markNeighbors(int level, int tx, int tz, List<Long> touched) {
        long[] ns = {key(level, tx - 1, tz), key(level, tx + 1, tz), key(level, tx, tz - 1), key(level, tx, tz + 1)};
        for (long n : ns) mark(n, touched);
    }

    /** Oublie tout (déconnexion, lod_forget(-1), changement de monde, coupure). */
    public void clear() {
        tiles.clear();
        dirty.clear();
        urgent.clear();
        removed.clear();
        for (int l = 0; l <= Msg.LOD_MAX_LEVEL; l++) nSeen.set(l, 0);
        epoch++;
    }

    // ---- fil de travail
    /** Retire jusqu'à max clés sales, les prioritaires (oubli, masque) d'abord. */
    public List<Long> drainDirty(int max) {
        List<Long> out = new ArrayList<>(Math.min(max, 64));
        for (Iterator<Long> it = urgent.iterator(); it.hasNext() && out.size() < max; ) {
            Long k = it.next();
            it.remove();
            if (dirty.remove(k)) out.add(k);
        }
        Iterator<Long> it = dirty.iterator();
        while (it.hasNext() && out.size() < max) {
            Long k = it.next();
            it.remove();
            urgent.remove(k);
            out.add(k);
        }
        return out;
    }
    public boolean hasDirty() { return !dirty.isEmpty(); }
    public void markDirty(long k) { if (tiles.containsKey(k)) dirty.add(k); }

    /** Fil client : tuile retirée dont les tampons GPU sont à libérer (null s'il n'y en a plus). */
    public Long pollRemoved() { Removed r = removed.poll(); return r == null ? null : r.key(); }
    public Removed pollRemovedEntry() { return removed.poll(); }

    // ---- masque des chunks réels (fil client)
    public boolean isReal(int cx, int cz) { return Arrays.binarySearch(real, chunkKey(cx, cz)) >= 0; }
    public int realCount() { return real.length; }

    /** Publie le nouvel ensemble (trié par la méthode) et marque sales les tuiles dont un chunk a changé d'état
     *  (colonne masquée ou flanc de raccord). Rend le nombre de chunks changés. */
    public int setRealChunks(long[] chunks) {
        long[] now = chunks.clone();
        Arrays.sort(now);
        long[] old = real;
        real = now;
        List<Long> changed = new ArrayList<>();
        int i = 0, j = 0;
        while (i < old.length || j < now.length) {
            if (j >= now.length || (i < old.length && old[i] < now[j])) changed.add(old[i++]);
            else if (i >= old.length || now[j] < old[i]) changed.add(now[j++]);
            else { i++; j++; }
        }
        if (changed.isEmpty()) return 0;
        long minX = Long.MAX_VALUE, maxX = Long.MIN_VALUE, minZ = Long.MAX_VALUE, maxZ = Long.MIN_VALUE;
        for (long c : changed) {
            long x = (long) (int) (c >> 32) * 16, z = (long) (int) c * 16;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x + 16);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z + 16);
        }
        for (Map.Entry<Long, Entry> e : tiles.entrySet()) {
            Msg.LodTile t = e.getValue().tile();
            long pad = t.cell();
            long x0 = t.originX() - pad, x1 = t.originX() + t.span() + pad;
            long z0 = t.originZ() - pad, z1 = t.originZ() + t.span() + pad;
            if (x1 <= minX || x0 >= maxX || z1 <= minZ || z0 >= maxZ) continue;
            for (long c : changed) {
                long cx = (long) (int) (c >> 32) * 16, cz = (long) (int) c * 16;
                // prioritaire : une colonne à montrer (vrai chunk sorti de la vue) ou à cacher (vrai chunk prêt)
                if (cx + 16 > x0 && cx < x1 && cz + 16 > z0 && cz < z1) { urgent.add(e.getKey()); dirty.add(e.getKey()); break; }
            }
        }
        wake();
        return changed.size();
    }

    /** Oublie les tuiles dont le centre est à plus de radius blocs (horizontalement) de (px, pz). */
    public int evictBeyond(double px, double pz, double radius) {
        int n = 0;
        double r2 = radius * radius;
        for (Map.Entry<Long, Entry> e : tiles.entrySet()) {
            Msg.LodTile t = e.getValue().tile();
            double cx = t.originX() + t.span() / 2.0 - px, cz = t.originZ() + t.span() / 2.0 - pz;
            if (cx * cx + cz * cz > r2) { remove(e.getKey()); n++; }
        }
        if (n > 0) evicted.addAndGet(n);
        return n;
    }

    public void resetCounters() {
        rx.set(0); forgets.set(0); evicted.set(0); meshed.set(0);
        gpuTiles = 0; drawnTiles = 0; gpuQuads = 0; gpuBytes = 0;
        gpuReserved = 0; gpuPeak = 0; gpuCap = 0; capHits = 0; capEvicted = 0; gpuHeaps = 0; gpuWaiting = 0; viewCut = 0;
    }
}
