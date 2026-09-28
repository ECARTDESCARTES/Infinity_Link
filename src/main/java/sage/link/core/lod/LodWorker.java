package sage.link.core.lod;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;

/** Fil de travail du mailleur (démon, un seul) : vide les tuiles sales du magasin, les maille, dépose les maillages
 *  dans une file que le fil de rendu consomme avec un budget par image. Aucune exception ne sort : la première est
 *  remise à onError (qui coupe le lod), les suivantes sont ignorées. */
public final class LodWorker implements Runnable {
    private final LodStore store;
    private final ConcurrentLinkedQueue<LodMesher.Mesh> out = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();
    private final BiConsumer<String, Throwable> onError;
    private volatile Thread thread;
    private volatile boolean stopped;
    /** Borne des maillages prêts non encore envoyés au GPU (mémoire). */
    static final int MAX_PENDING = 2048;

    public LodWorker(LodStore store, BiConsumer<String, Throwable> onError) {
        this.store = store;
        this.onError = onError;
    }

    public synchronized void start() {
        if (thread != null || stopped) return;
        Thread t = new Thread(this, "sage-lod-mailleur");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        thread = t;
        t.start();
    }

    /** Réveille le fil (nouvelles tuiles sales). */
    public void wake() {
        Thread t = thread;
        if (t != null) LockSupport.unpark(t);
    }

    public void stop() {
        stopped = true;
        wake();
    }

    public LodMesher.Mesh poll() {
        LodMesher.Mesh m = out.poll();
        if (m != null) pending.decrementAndGet();
        return m;
    }

    public int pending() { return pending.get(); }

    /** Maille une passe (appelable sans fil, pour les tests). Rend le nombre de tuiles maillées. */
    public int runOnce(int max) {
        List<Long> keys = store.drainDirty(max);
        int done = 0;
        for (long k : keys) {
            // horloge relevée AVANT de lire le magasin : ce maillage voit tout retrait d'horloge <= seq
            long seq = store.clock();
            LodStore.Entry e = store.entry(k);
            if (e == null) continue;
            LodMesher.Mesh m = LodMesher.mesh(k, e.gen(), e.tile(), store::tile, store::isReal, store::coveredFiner).withSeq(seq);
            out.add(m);
            pending.incrementAndGet();
            store.meshed.incrementAndGet();
            done++;
        }
        return done;
    }

    @Override
    public void run() {
        boolean reported = false;
        while (!stopped) {
            try {
                if (pending.get() >= MAX_PENDING || !store.hasDirty()) {
                    LockSupport.parkNanos(20_000_000L);
                    continue;
                }
                runOnce(32);
            } catch (Throwable t) {
                if (!reported) {
                    reported = true;
                    try { onError.accept("mailleur", t); } catch (Throwable ignored) { }
                }
                LockSupport.parkNanos(200_000_000L);
            }
        }
    }

    /** Vide la file des maillages prêts (coupure, changement de monde). */
    public void drop() {
        while (poll() != null) { }
    }

}
