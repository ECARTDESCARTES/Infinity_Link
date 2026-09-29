package infinitylink.mc.lod;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import infinitylink.core.lod.LodMesher;
import infinitylink.core.lod.LodPool;
import infinitylink.core.lod.LodStore;
import infinitylink.core.lod.LodWorker;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Côté GPU du rendu lointain (fil de rendu uniquement). 0.2.1 : les sommets (8 octets, LodMesher) vivent dans une
 *  réserve de quelques tas GPU (LodPool, tas de HEAP_BYTES) au lieu d'un tampon par tuile et par matière ; une tuile
 *  occupe un seul morceau (opaque puis eau). Plafond de VRAM (option lod_vram_mb) : jamais dépassé ; en cas de manque,
 *  on libère d'abord les tuiles oubliées encore dessinées, puis l'ancien maillage de la même tuile, puis les tuiles
 *  résidentes plus lointaines (remaillées plus tard), et LodClient réduit la vue demandée au serveur. Les maillages
 *  arrivent dans une file d'attente dédoublonnée par tuile et sont envoyés au GPU du plus proche au plus lointain
 *  (budget par image). Dessin dans la passe principale juste après le terrain opaque (LevelRenderer.executeSolid) :
 *  même cible couleur et même profondeur. Un tampon d'indices de quads partagé (u16, 0,1,2,2,3,0), baseVertex = début
 *  du morceau dans son tas. Aucune méthode ne lève vers le jeu : LodClient attrape et coupe. */
final class LodRenderer {
    /** Quads par appel de dessin : 16 384 quads = 65 536 sommets, la limite des indices u16. */
    static final int QUADS_PER_DRAW = 16384;
    static final int UPLOAD_TILES_PER_FRAME = 48;
    static final long UPLOAD_BYTES_PER_FRAME = 6L << 20;
    /** Une tuile oubliée reste dessinée au plus ce nombre d'images en attendant les maillages de ses remplaçantes
     *  (raccord sans trou) ; libérée aussitôt si elle est hors de la distance de dessin ou si la réserve manque de place. */
    static final int DEFER_MAX_FRAMES = 120;
    /** Taille d'un tas de la réserve : 4 Mio = 131 072 quads. */
    static final long HEAP_BYTES = 4L << 20;

    /** Tuile résidente : un morceau de la réserve, quads opaques à mem.offset, quads d'eau à waterOffset. */
    record GpuTile(long key, long gen, long ox, int oy, long oz, int span, int cell, int minY, int maxY,
                   LodPool.Alloc mem, int opaqueQuads, long waterOffset, int waterQuads) {}

    private final LodStore store;
    private final LodWorker worker;
    private final LodPool pool;
    private final Map<Integer, GpuBuffer> heaps = new HashMap<>();
    private final Map<Long, GpuTile> tiles = new HashMap<>();
    /** Maillages prêts pas encore envoyés au GPU (un par tuile, le plus récent). */
    private final Map<Long, LodMesher.Mesh> waiting = new HashMap<>();
    /** Horloge (LodStore.clock) du dernier maillage envoyé par clé, vide compris. */
    private final Map<Long, Long> meshSeq = new HashMap<>();
    /** Tuiles oubliées encore dessinées : libérées quand les tuiles qu'elles ont salies ont un maillage qui les voit. */
    private record Deferred(LodStore.Removed r, long frame) {}
    private final List<Deferred> deferred = new ArrayList<>();
    private long frame;
    private long epochSeen = Long.MIN_VALUE;
    private GpuBuffer index;
    private CompletableFuture<CompiledRenderPipeline.Pending> opaqueF, waterF;
    private CompiledRenderPipeline opaqueP, waterP;
    private RenderPipeline opaqueDef, waterDef;
    // Caméra de l'image courante (capturée en tête de LevelRenderer.render).
    private Vec3 cam;
    private Frustum frustum;
    private double drawMax = Double.MAX_VALUE;
    private final Vector4f modulator = new Vector4f(1, 1, 1, 1);
    /** TextureMat par niveau : m00 = taille de colonne (1 << level), lue par le shader pour x et z. */
    private final Matrix4f[] cellMat = new Matrix4f[8];
    private long quads, capHits, capEvicted;
    private volatile boolean overCap;

    LodRenderer(LodStore store, LodWorker worker, long capBytes) {
        this.store = store;
        this.worker = worker;
        this.pool = new LodPool(new LodPool.Heaps() {
            @Override public void open(int id, long bytes) {
                heaps.put(id, RenderSystem.getDevice().createBuffer(() -> "infinitylink lod tas " + id,
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, bytes));
            }
            @Override public void close(int id) {
                GpuBuffer b = heaps.remove(id);
                if (b != null) b.close();
            }
        }, HEAP_BYTES, Math.max(capBytes, HEAP_BYTES));
        for (int l = 0; l < cellMat.length; l++) cellMat[l] = new Matrix4f().scaling(1 << l, 1, 1 << l);
    }

    boolean ready() { return opaqueP != null && waterP != null; }
    int size() { return tiles.size(); }
    /** true tant qu'une tuile, un maillage en attente ou un tas GPU est détenu. */
    boolean holdsGpu() { return !tiles.isEmpty() || !waiting.isEmpty() || pool.committed() > 0; }
    long capBytes() { return pool.cap(); }
    /** Lu et remis à zéro par LodClient (fil client = fil de rendu) : un maillage a manqué de place depuis. */
    boolean takeOverCap() { boolean o = overCap; overCap = false; return o; }

    /** Tête de LevelRenderer.render : hors de toute passe. Libérations, envois au GPU, compilation, caméra. */
    void frameBegin(CameraRenderState camera, LightmapRenderState light) {
        frame++;
        cam = camera != null ? camera.pos : null;
        frustum = camera != null ? camera.cullFrustum : null;
        if (store.epoch() != epochSeen) {
            freeTiles();
            worker.drop();
            epochSeen = store.epoch();
        }
        LodStore.Removed gone;
        while ((gone = store.pollRemovedEntry()) != null) deferred.add(new Deferred(gone, frame));
        compile();
        if (ready()) index(); // créé ici, hors de toute passe
        upload();
        releaseDeferred(false);
        if (tiles.isEmpty() && waiting.isEmpty() && pool.committed() > 0) pool.closeAll(); // plus rien : 0 octet
        skyModulator(light);
        publish();
    }

    private void publish() {
        store.gpuTiles = tiles.size();
        store.gpuQuads = quads;
        store.gpuBytes = pool.used();
        store.gpuReserved = pool.committed();
        store.gpuPeak = pool.peak();
        store.gpuHeaps = pool.heapCount();
        store.gpuCap = pool.cap();
        store.gpuWaiting = waiting.size();
        store.capHits = capHits;
        store.capEvicted = capEvicted;
        store.pipelineReady = ready();
    }

    private void compile() {
        if (ready()) return;
        GpuDevice device = RenderSystem.getDevice();
        if (opaqueF == null) {
            opaqueDef = LodShaders.opaque();
            waterDef = LodShaders.water();
            LodShaders.Source src = new LodShaders.Source();
            opaqueF = device.compilePipeline(opaqueDef, src, Util.backgroundExecutor());
            waterF = device.compilePipeline(waterDef, src, Util.backgroundExecutor());
        }
        if (opaqueP == null && opaqueF.isDone()) {
            opaqueP = opaqueF.join().finishCompile();
            if (opaqueP == null) throw new IllegalStateException("compilation du pipeline lod opaque refusee");
        }
        if (waterP == null && waterF.isDone()) {
            waterP = waterF.join().finishCompile();
            if (waterP == null) throw new IllegalStateException("compilation du pipeline lod eau refusee");
        }
    }

    private static boolean newer(LodMesher.Mesh a, LodMesher.Mesh b) {
        return a.gen() > b.gen() || (a.gen() == b.gen() && a.seq() >= b.seq());
    }

    private void upload() {
        if (LodShaders.FORMAT.getVertexSize() != LodMesher.VERTEX_BYTES)
            throw new IllegalStateException("format lod de " + LodShaders.FORMAT.getVertexSize() + " octets");
        LodMesher.Mesh m;
        while ((m = worker.poll()) != null) {
            LodMesher.Mesh w = waiting.get(m.key());
            if (w == null || newer(m, w)) waiting.put(m.key(), m);
        }
        if (waiting.isEmpty()) return;
        List<LodMesher.Mesh> order = new ArrayList<>(waiting.values());
        if (cam != null) order.sort(Comparator.comparingDouble(this::dist2));
        List<GpuTile> farFirst = null;
        int n = 0;
        long budget = 0;
        for (LodMesher.Mesh mm : order) {
            if (n >= UPLOAD_TILES_PER_FRAME || budget >= UPLOAD_BYTES_PER_FRAME) break;
            long k = mm.key();
            LodStore.Entry e = store.entry(k);
            // tuile oubliée (sa libération passe par pollRemoved), ou maillage d'une génération dépassée : un maillage
            // plus récent arrive ; on garde l'ancien morceau d'ici là.
            if (e == null || e.gen() > mm.gen()) { waiting.remove(k); continue; }
            if (mm.empty()) {
                GpuTile old = tiles.get(k);
                if (old != null) drop(old);
                meshSeq.merge(k, mm.seq(), Math::max);
                waiting.remove(k);
                continue;
            }
            long opBytes = LodPool.align(mm.opaque().length), need = opBytes + mm.water().length;
            LodPool.Alloc a = pool.alloc(need);
            if (a == null && !deferred.isEmpty()) { releaseDeferred(true); a = pool.alloc(need); }
            if (a == null) { GpuTile old = tiles.get(k); if (old != null) { drop(old); a = pool.alloc(need); } }
            if (a == null && cam != null) {
                if (farFirst == null) {
                    farFirst = new ArrayList<>(tiles.values());
                    farFirst.sort(Comparator.<GpuTile>comparingDouble(this::dist2).reversed());
                }
                double d = dist2(mm);
                for (Iterator<GpuTile> it = farFirst.iterator(); a == null && it.hasNext(); ) {
                    GpuTile far = it.next();
                    it.remove();
                    if (tiles.get(far.key()) != far) continue;
                    if (dist2(far) <= d) break; // tout le reste est plus proche que ce maillage
                    drop(far);
                    capEvicted++;
                    store.markDirty(far.key()); // remaillée, puis renvoyée au GPU quand la place revient
                    a = pool.alloc(need);
                }
            }
            if (a == null) { overCap = true; capHits++; break; } // la suite est plus lointaine : elle attend aussi
            GpuBuffer heap = heaps.get(a.heap());
            write(heap, a.offset(), mm.opaque());
            write(heap, a.offset() + opBytes, mm.water());
            GpuTile old = tiles.get(k);
            if (old != null) drop(old);
            GpuTile t = new GpuTile(k, mm.gen(), mm.originX(), mm.originY(), mm.originZ(), mm.span(), 1 << mm.level(),
                    mm.minY(), mm.maxY(), a, mm.opaqueQuads(), a.offset() + opBytes, mm.waterQuads());
            tiles.put(k, t);
            quads += mm.quads();
            meshSeq.merge(k, mm.seq(), Math::max);
            waiting.remove(k);
            n++;
            budget += need;
        }
    }

    private static void write(GpuBuffer buf, long offset, byte[] data) {
        if (data.length == 0) return;
        ByteBuffer bb = MemoryUtil.memAlloc(data.length);
        try {
            bb.put(data).flip();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buf.slice(offset, data.length), bb);
        } finally {
            MemoryUtil.memFree(bb);
        }
    }

    /** Retire une tuile résidente et rend son morceau à la réserve. */
    private void drop(GpuTile t) {
        if (tiles.get(t.key()) == t) tiles.remove(t.key());
        quads -= t.opaqueQuads() + t.waterQuads();
        pool.free(t.mem());
    }

    /** Raccord atomique : une tuile oubliée n'est libérée qu'à l'image où les maillages des tuiles qui reprennent ses
     *  colonnes sont envoyés (ou quand elles n'existent plus) ; borne DEFER_MAX_FRAMES ; aussitôt si elle est hors de la
     *  distance de dessin (invisible) ou si force (la réserve manque de place). */
    private void releaseDeferred(boolean force) {
        for (Iterator<Deferred> it = deferred.iterator(); it.hasNext(); ) {
            Deferred d = it.next();
            long k = d.r().key();
            GpuTile t = tiles.get(k);
            if (!force && t != null && frame - d.frame() < DEFER_MAX_FRAMES && !covered(d.r()) && !beyondDraw(t)) continue;
            it.remove();
            if (t == null) { if (store.entry(k) == null) meshSeq.remove(k); continue; }
            if (t.gen() > d.r().gen()) continue; // déjà remplacée par une génération plus récente
            drop(t);
            meshSeq.remove(k);
        }
    }

    /** true si chaque tuile salie par le retrait est partie, ou a un maillage envoyé qui voit le retrait. */
    private boolean covered(LodStore.Removed r) {
        for (long k : r.dirtied()) {
            if (store.entry(k) == null) continue;
            Long s = meshSeq.get(k);
            if (s == null || s < r.stamp()) return false;
        }
        return true;
    }

    private GpuBuffer index() {
        if (index != null && !index.isClosed()) return index;
        int count = QUADS_PER_DRAW * 6;
        ByteBuffer buf = MemoryUtil.memAlloc(count * 2);
        try {
            for (int q = 0; q < QUADS_PER_DRAW; q++) {
                int b = q * 4;
                buf.putShort((short) b).putShort((short) (b + 1)).putShort((short) (b + 2))
                   .putShort((short) (b + 2)).putShort((short) (b + 3)).putShort((short) b);
            }
            buf.flip();
            index = RenderSystem.getDevice().createBuffer(() -> "infinitylink lod index", GpuBuffer.USAGE_INDEX, buf);
        } finally {
            MemoryUtil.memFree(buf);
        }
        return index;
    }

    /** Couleur de la lightmap vanilla pour ciel 15 et bloc 0 (reprend lightmap.fsh de 26.3) : jour/nuit, effets. */
    private void skyModulator(LightmapRenderState ls) {
        if (ls == null || ls.skyLightColor == null || ls.ambientColor == null) { modulator.set(1, 1, 1, 1); return; }
        Vector3fc amb = ls.ambientColor, sky = ls.skyLightColor, nvc = ls.nightVisionColor;
        float nv = ls.nightVisionEffectIntensity;
        float r = Math.max(amb.x(), nvc != null ? nvc.x() * nv : 0) + sky.x() * ls.skyFactor;
        float g = Math.max(amb.y(), nvc != null ? nvc.y() * nv : 0) + sky.y() * ls.skyFactor;
        float b = Math.max(amb.z(), nvc != null ? nvc.z() * nv : 0) + sky.z() * ls.skyFactor;
        float boss = ls.bossOverlayWorldDarkening;
        r = r + (r * 0.7f - r) * boss; g = g + (g * 0.6f - g) * boss; b = b + (b * 0.6f - b) * boss;
        r = clamp(r - ls.darknessEffectScale); g = clamp(g - ls.darknessEffectScale); b = clamp(b - ls.darknessEffectScale);
        float mx = Math.max(r, Math.max(g, b));
        if (mx > 0) {
            float inv = 1f - mx, scaled = 1f - inv * inv * inv * inv, k = scaled / mx, br = ls.brightness;
            r = r + (r * k - r) * br; g = g + (g * k - g) * br; b = b + (b * k - b) * br;
        }
        modulator.set(r, g, b, 1f);
    }

    private static float clamp(float v) { return v < 0 ? 0 : Math.min(v, 1); }

    /** Distance horizontale (blocs) du bord de la tuile à la caméra. */
    private double edgeDistance(GpuTile t) {
        double cx = t.ox() + t.span() / 2.0 - cam.x, cz = t.oz() + t.span() / 2.0 - cam.z;
        return Math.max(0, Math.sqrt(cx * cx + cz * cz) - t.span() * 0.7072);
    }

    private boolean beyondDraw(GpuTile t) { return cam != null && edgeDistance(t) > drawMax; }

    /** Fin de LevelRenderer.executeSolid : même passe que le terrain opaque (profondeur déjà écrite). */
    void draw(RenderPass pass, double maxDistance) {
        drawMax = maxDistance;
        if (!ready() || tiles.isEmpty() || cam == null) { store.drawnTiles = 0; return; }
        List<GpuTile> vis = new ArrayList<>(tiles.size());
        for (GpuTile t : tiles.values()) {
            if (edgeDistance(t) > maxDistance) continue;
            if (frustum != null && !frustum.isVisible(new AABB(t.ox(), t.oy() + t.minY(), t.oz(),
                    t.ox() + t.span(), t.oy() + t.maxY() + 1, t.oz() + t.span()))) continue;
            vis.add(t);
        }
        store.drawnTiles = vis.size();
        if (vis.isEmpty()) return;
        // eau : de l'arrière vers l'avant ; le sol, lui, de l'avant vers l'arrière (rejet précoce en profondeur)
        vis.sort(Comparator.comparingDouble(this::dist2));
        Matrix4f view = RenderSystem.getModelViewMatrixCopy();
        DynamicGpuData.Transform[] tr = new DynamicGpuData.Transform[vis.size()];
        for (int i = 0; i < tr.length; i++) {
            GpuTile t = vis.get(i);
            Vector3f off = new Vector3f((float) (t.ox() - cam.x), (float) (t.oy() - cam.y), (float) (t.oz() - cam.z));
            tr[i] = new DynamicGpuData.Transform(view, new Vector4f(modulator), off, cellMat[Integer.numberOfTrailingZeros(t.cell()) & 7]);
        }
        GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms().writeTransforms(tr);
        GpuBuffer idx = index;
        if (idx == null || idx.isClosed()) return;
        pass.pushDebugGroup(() -> "infinitylink lod");
        try {
            pass.setPipeline(opaqueP);
            pass.setIndexBuffer(idx, IndexType.SHORT);
            int bound = -1;
            for (int i = 0; i < tr.length; i++) {
                GpuTile t = vis.get(i);
                if (t.opaqueQuads() == 0 || (bound = bind(pass, t, bound)) < 0) continue;
                pass.setUniform("DynamicTransforms", slices[i]);
                drawQuads(pass, t.mem().offset(), t.opaqueQuads());
            }
            pass.setPipeline(waterP);
            pass.setIndexBuffer(idx, IndexType.SHORT);
            bound = -1;
            for (int i = tr.length - 1; i >= 0; i--) {
                GpuTile t = vis.get(i);
                if (t.waterQuads() == 0 || (bound = bind(pass, t, bound)) < 0) continue;
                pass.setUniform("DynamicTransforms", slices[i]);
                drawQuads(pass, t.waterOffset(), t.waterQuads());
            }
        } finally {
            pass.popDebugGroup();
        }
    }

    /** Lie le tas de la tuile s'il n'est pas déjà lié ; rend son identifiant, ou -1 (tas fermé). */
    private int bind(RenderPass pass, GpuTile t, int bound) {
        int h = t.mem().heap();
        if (h == bound) return h;
        GpuBuffer b = heaps.get(h);
        if (b == null || b.isClosed()) return -1;
        pass.setVertexBuffer(0, b.slice());
        return h;
    }

    private double dist2(GpuTile t) {
        double cx = t.ox() + t.span() / 2.0 - cam.x, cz = t.oz() + t.span() / 2.0 - cam.z;
        return cx * cx + cz * cz;
    }

    private double dist2(LodMesher.Mesh m) {
        double cx = m.originX() + m.span() / 2.0 - cam.x, cz = m.originZ() + m.span() / 2.0 - cam.z;
        return cx * cx + cz * cz;
    }

    /** drawIndexed(indices, instances, premier indice, baseVertex, première instance) : baseVertex = début du morceau
     *  dans le tas (octets / 8), plus 4 sommets par quad déjà dessiné (26.3 : glDrawElementsInstancedBaseVertex). */
    private static void drawQuads(RenderPass pass, long byteOffset, int quads) {
        int base = (int) (byteOffset / LodMesher.VERTEX_BYTES);
        for (int q = 0; q < quads; q += QUADS_PER_DRAW) {
            int n = Math.min(QUADS_PER_DRAW, quads - q);
            pass.drawIndexed(n * 6, 1, 0, base + q * 4, 0);
        }
    }

    /** Libère les tuiles et tous les tas (fil de rendu) : VRAM du lointain à 0, hors tampon d'indices (192 Kio). */
    void freeTiles() {
        deferred.clear();
        meshSeq.clear();
        waiting.clear();
        tiles.clear();
        try { pool.closeAll(); } catch (Throwable ignored) { }
        for (GpuBuffer b : new ArrayList<>(heaps.values())) try { b.close(); } catch (Throwable ignored) { }
        heaps.clear();
        quads = 0;
        overCap = false;
        publish();
        store.drawnTiles = 0;
    }

    /** Coupure définitive : tampons, indices, pipelines. */
    void close() {
        freeTiles();
        try { if (index != null) index.close(); } catch (Throwable ignored) { }
        index = null;
        try { if (opaqueP != null) opaqueP.close(); } catch (Throwable ignored) { }
        try { if (waterP != null) waterP.close(); } catch (Throwable ignored) { }
        opaqueP = waterP = null;
        store.pipelineReady = false;
    }
}
