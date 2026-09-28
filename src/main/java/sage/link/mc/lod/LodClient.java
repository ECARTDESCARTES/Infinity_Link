package sage.link.mc.lod;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import sage.link.core.LinkState;
import sage.link.core.Msg;
import sage.link.core.lod.LodStore;
import sage.link.core.lod.LodWorker;
import sage.link.mc.Bridge;
import sage.link.mc.SagePayload;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Properties;

/** Capacité « lod » côté client (contrat §9) : option du mod, envoi de lod_view une fois en jeu, masque des chunks
 *  réels, éviction, rendu (LodRenderer), plan lointain et brouillard repoussés (mixins Camera et FogRenderer).
 *  Toute exception coupe le lod : journalisée une fois, lod_view 0 renvoyé, tampons libérés, le jeu continue. */
public final class LodClient {
    private LodClient() {}

    static final LinkState STATE = Bridge.STATE;
    static final String CONFIG = "sage_link.properties";
    /** Option : lod=true|false, lod_view=0..512 chunks (défaut 128 = 2 048 blocs ; le serveur sert au plus 256). */
    static volatile boolean optionOn = true;
    static volatile int optionView = 128;
    /** Option : lod_vram_mb = plafond de VRAM des sommets du lointain en Mio (16..4096, défaut 96 ; 0.2.1). */
    static final int DEFAULT_VRAM_MB = 96;
    static volatile int optionVramMb = DEFAULT_VRAM_MB;
    /** Vue demandée au serveur : optionView, réduite par paliers de 16 chunks quand le plafond de VRAM est atteint
     *  (jamais sous MIN_VIEW_CUT), rétablie au changement de monde ou de session. 0 = pas encore demandée. */
    private static volatile int viewEff;
    static final int MIN_VIEW_CUT = 32;
    private static int lastCut = Integer.MIN_VALUE / 2;
    private static String lastLog = "";

    private static volatile boolean disabled;
    private static volatile boolean sent;
    private static volatile int farBlocks;
    private static ClientLevel levelSeen;
    private static int ticks;
    /** Chunk de la caméra au dernier calcul du masque (le cercle des vrais chunks de 26.3 est centré sur elle). */
    private static long camChunk = Long.MIN_VALUE;
    private static LodWorker worker;
    private static LodRenderer renderer;

    static LodStore store() { return STATE.lod; }

    /** Distance servie en blocs pour la vue demandée (le serveur plafonne à 256 chunks). */
    static int servedBlocks() { return Math.min(viewEff > 0 ? viewEff : optionView, 256) * 16; }

    // ---------------------------------------------------------------- option
    public static void loadOptions() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve(CONFIG);
            Properties pr = new Properties();
            if (Files.isRegularFile(p)) {
                try (InputStream in = Files.newInputStream(p)) { pr.load(in); }
            } else {
                pr.setProperty("lod", "true");
                pr.setProperty("lod_view", "128");
                pr.setProperty("lod_vram_mb", String.valueOf(DEFAULT_VRAM_MB));
                Files.createDirectories(p.getParent());
                try (OutputStream out = Files.newOutputStream(p)) {
                    out.write(("# SAGE Link : rendu lointain natif (capacite lod, contrat SAGE-LINK-PROTOCOLE.md section 9)\n"
                            + "# lod : true/false ; lod_view : distance en chunks (0..512, servie <= 256 ; 128 = 2048 blocs)\n"
                            + "# lod_vram_mb : plafond de VRAM des sommets du rendu lointain en Mio (16..4096)\n")
                            .getBytes(StandardCharsets.ISO_8859_1));
                    pr.store(out, null);
                }
            }
            optionOn = !"false".equalsIgnoreCase(pr.getProperty("lod", "true").trim());
            int v = Integer.parseInt(pr.getProperty("lod_view", "128").trim());
            optionView = Math.max(0, Math.min(Msg.LOD_MAX_VIEW, v));
            if (optionView == 0) optionOn = false;
            String vm = pr.getProperty("lod_vram_mb");
            if (vm == null && Files.isRegularFile(p))
                Files.writeString(p, "\n# lod_vram_mb : plafond de VRAM des sommets du rendu lointain en Mio (16..4096, SAGE Link 0.2.1)\nlod_vram_mb="
                        + DEFAULT_VRAM_MB + "\n", StandardCharsets.ISO_8859_1, StandardOpenOption.APPEND);
            optionVramMb = Math.max(16, Math.min(4096, vm == null ? DEFAULT_VRAM_MB : Integer.parseInt(vm.trim())));
            System.err.println("[sage_link] lod " + (optionOn ? "actif, vue " + optionView + " chunks, plafond VRAM " + optionVramMb + " Mio" : "coupe par l'option") + " (" + p + ")");
        } catch (Throwable t) {
            STATE.error("option lod (valeurs par defaut)", t);
        }
    }

    // ---------------------------------------------------------------- fil client (Minecraft.tick)
    public static void tick(Minecraft mc) {
        if (disabled) {
            // coupure limitée à la session de jeu : hors connexion, l'état d'échec est levé (nouveau mailleur et nouveau
            // rendu à la prochaine session)
            if (mc.level == null || STATE.phase() != LinkState.Phase.CONNECTED) resetAfterFailure();
            return;
        }
        try {
            if (mc.level == null || STATE.phase() != LinkState.Phase.CONNECTED) {
                if (mc.level == null && renderer != null && renderer.holdsGpu()) renderer.freeTiles();
                if (STATE.phase() != LinkState.Phase.CONNECTED) { sent = false; levelSeen = null; farBlocks = 0; viewEff = 0; }
                return;
            }
            if (!optionOn) return;
            ClientPacketListener conn = mc.getConnection();
            if (conn == null || mc.player == null) return;
            if (levelSeen != mc.level) {
                // nouveau monde (dimension, réapparition) : on repart de zéro des deux côtés
                if (sent) send(conn, 0);
                levelSeen = mc.level;
                sent = false;
            }
            if (!sent) {
                ensureWorker();
                viewEff = optionView;
                store().viewCut = 0;
                send(conn, viewEff);
                sent = true;
            }
            // masque recalculé dès que la caméra change de chunk (le vrai terrain change alors de cercle), sinon tous
            // les 5 ticks (chunks arrivés, sections compilées)
            Vec3 cp = mc.gameRenderer.mainCamera().position();
            long cc = LodStore.chunkKey((int) Math.floor(cp.x / 16.0), (int) Math.floor(cp.z / 16.0));
            if (++ticks % 5 == 0 || cc != camChunk) updateMask(mc, cp);
            if (ticks % 40 == 0) store().evictBeyond(mc.player.getX(), mc.player.getZ(), servedBlocks() + 1024);
            // plafond de VRAM atteint (un maillage n'a pas trouvé de place) : vue réduite de 16 chunks, le serveur oublie
            // au-delà ; le lointain local hors de la nouvelle distance de dessin est évincé tout de suite
            if (ticks - lastCut >= 100 && renderer != null && Math.min(viewEff, 256) > MIN_VIEW_CUT && renderer.takeOverCap()) {
                viewEff = Math.max(MIN_VIEW_CUT, Math.min(viewEff, 256) - 16);
                lastCut = ticks;
                send(conn, viewEff);
                store().viewCut = viewEff;
                store().evictBeyond(mc.player.getX(), mc.player.getZ(), servedBlocks() + 256);
                System.err.println("[sage_link] lod : plafond de VRAM de " + optionVramMb + " Mio atteint, vue ramenee a " + viewEff + " chunks");
            }
            if (ticks % 600 == 0) logVram();
            farBlocks = store().size() > 0 && renderer != null && renderer.ready() ? servedBlocks() : 0;
        } catch (Throwable t) {
            fail("tick", t);
        }
    }

    private static void send(ClientPacketListener conn, int d) {
        conn.send(new ServerboundCustomPayloadPacket(SagePayload.out(Msg.LOD_VIEW, STATE.lodView(d))));
    }

    private static synchronized void ensureWorker() {
        if (worker == null) {
            worker = new LodWorker(store(), LodClient::fail);
            renderer = new LodRenderer(store(), worker, (long) optionVramMb << 20);
            LodWorker w = worker;
            store().setWaker(w::wake);
        }
        worker.start();
    }

    /** Chunks dont le vrai terrain est affiché : chargés, dans la distance de rendu vanilla autour de la CAMÉRA
     *  (26.3, bytecode : SectionOcclusionGraph centre son cercle sur la section de CameraRenderState.pos et teste
     *  ChunkTrackingView.isInViewDistance(ViewArea.getViewDistance, ...)) ET dont la section la plus haute non vide est
     *  compilée et visible (LevelRenderer.isSectionCompiledAndVisible, fondu d'apparition compris) : le lod ne s'efface
     *  qu'une fois le vrai terrain réellement dessiné. */
    static void updateMask(Minecraft mc, Vec3 cam) {
        var va = mc.levelRenderer.viewArea();
        int rd = va != null ? va.getViewDistance() : mc.options.getEffectiveRenderDistance();
        int pcx = (int) Math.floor(cam.x / 16.0), pcz = (int) Math.floor(cam.z / 16.0);
        camChunk = LodStore.chunkKey(pcx, pcz);
        long fade = Util.toMillis(mc.options.chunkSectionFadeInTime().get());
        long[] buf = new long[(2 * rd + 3) * (2 * rd + 3)];
        int n = 0;
        var chunks = mc.level.getChunkSource();
        for (int dx = -rd - 1; dx <= rd + 1; dx++)
            for (int dz = -rd - 1; dz <= rd + 1; dz++) {
                long ax = Math.max(0, Math.abs(dx) - 1), az = Math.max(0, Math.abs(dz) - 1);
                if (ax * ax + az * az >= (long) rd * rd) continue;
                LevelChunk ch = chunks.getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
                if (ch == null) continue;
                int top = ch.getHighestFilledSectionIndex();
                if (top >= 0) {
                    BlockPos p = new BlockPos((pcx + dx) * 16 + 8, ch.getSectionYFromSectionIndex(top) * 16 + 8, (pcz + dz) * 16 + 8);
                    if (!mc.levelRenderer.isSectionCompiledAndVisible(p, fade)) continue;
                }
                buf[n++] = LodStore.chunkKey(pcx + dx, pcz + dz);
            }
        if (store().setRealChunks(java.util.Arrays.copyOf(buf, n)) > 0 && worker != null) worker.wake();
    }

    /** Toutes les 30 s (si changé) : VRAM du lointain dans latest.log, pour mesurer sans outil externe. */
    private static void logVram() {
        LodStore s = store();
        if (s.gpuTiles == 0 && s.gpuReserved == 0) return;
        String line = String.format(Locale.ROOT, "[sage_link] lod vram : %d tuiles, %d quads, sommets %.1f Mio, reserve %.1f Mio en %d tas"
                        + " (pic %.1f), plafond %d Mio, vue %d/%d, attente %d, manques %d, evinces %d",
                s.gpuTiles, s.gpuQuads, s.gpuBytes / 1048576.0, s.gpuReserved / 1048576.0, s.gpuHeaps, s.gpuPeak / 1048576.0,
                optionVramMb, viewEff, optionView, s.gpuWaiting, s.capHits, s.capEvicted);
        if (!line.equals(lastLog)) { lastLog = line; System.err.println(line); }
    }

    // ---------------------------------------------------------------- rendu (fil de rendu)
    /** Tête de LevelRenderer.render. */
    public static void frameBegin(CameraRenderState camera) {
        if (disabled || renderer == null) return;
        try {
            LightmapRenderState light = Minecraft.getInstance().gameRenderer.gameRenderState().lightmapRenderState;
            renderer.frameBegin(camera, light);
            if (worker != null && store().hasDirty()) worker.wake();
        } catch (Throwable t) {
            fail("preparation du rendu", t);
        }
    }

    /** Fin de LevelRenderer.executeSolid. */
    public static void drawSolid(RenderPass pass) {
        if (disabled || renderer == null || !sent) return;
        try {
            renderer.draw(pass, servedBlocks() + 256);
        } catch (Throwable t) {
            fail("dessin", t);
        }
    }

    /** Camera.update, juste après le calcul de depthFar : plan lointain repoussé tant que le lod est actif. */
    public static float depthFar(float vanilla) {
        int far = farBlocks;
        return far > 0 && !disabled ? Math.max(vanilla, far * 1.25f + 256f) : vanilla;
    }

    /** Sortie de FogRenderer.setupFog : fin du brouillard de distance repoussée à la distance lod ; brouillard
     *  atmosphérique (ciel ouvert, fin > 128 blocs : ni eau, ni lave, ni cécité, ni boss) étiré d'autant. */
    public static void adjustFog(FogData d) {
        int far = farBlocks;
        if (far <= 0 || disabled || d == null) return;
        try {
            if (d.renderDistanceEnd < far) {
                d.renderDistanceEnd = far;
                d.renderDistanceStart = far - Math.max(4f, Math.min(64f, far / 10f));
            }
            if (d.environmentalEnd > 128f && d.environmentalEnd < far) {
                float k = far / Math.max(1024f, d.environmentalEnd);
                if (k > 1f) {
                    d.environmentalStart *= k;
                    d.environmentalEnd *= k;
                }
            }
        } catch (Throwable t) {
            fail("brouillard", t);
        }
    }

    // ---------------------------------------------------------------- coupure
    /** Tête de ClientLevel.disconnect (sortie volontaire) : la connexion est encore ouverte, on envoie lod_view 0. */
    public static void beforeQuit() {
        try {
            ClientPacketListener conn = Minecraft.getInstance().getConnection();
            if (sent && conn != null && STATE.phase() == LinkState.Phase.CONNECTED) send(conn, 0);
            sent = false;
            farBlocks = 0;
        } catch (Throwable t) {
            STATE.error("lod_view 0 a la sortie", t);
        }
    }

    public static void onDisconnect() {
        // STATE.disconnected() a déjà vidé le magasin (le serveur oublie de son côté à la déconnexion) ;
        // les tampons GPU sont libérés au tick suivant (monde nul) ou à l'image suivante (epoch).
        sent = false;
        levelSeen = null;
        farBlocks = 0;
        viewEff = 0;
        LodWorker w = worker;
        if (w != null) w.drop();
    }

    /** Coupe le lod pour la session de jeu : journal une fois, lod_view 0, tampons libérés (fil de rendu). */
    static void fail(String where, Throwable t) {
        if (disabled) return;
        disabled = true;
        farBlocks = 0;
        try {
            store().off = where + " : " + t;
            STATE.error("lod coupe (" + where + ")", t);
            System.err.println("[sage_link] rendu lointain coupe (" + where + ") :");
            t.printStackTrace();
        } catch (Throwable ignored) { }
        try {
            Minecraft mc = Minecraft.getInstance();
            ClientPacketListener conn = mc.getConnection();
            if (conn != null && STATE.phase() == LinkState.Phase.CONNECTED && sent) send(conn, 0);
            else store().clear();
        } catch (Throwable ignored) { store().clear(); }
        try { if (worker != null) worker.stop(); } catch (Throwable ignored) { }
        final LodRenderer r = renderer;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.isSameThread()) closeRenderer(r);
            else mc.execute(() -> closeRenderer(r));
        } catch (Throwable ignored) { }
    }

    private static void closeRenderer(LodRenderer r) {
        try { if (r != null) r.close(); } catch (Throwable ignored) { }
    }

    /** Fil client, hors connexion après une coupure : l'échec ne vaut que pour la session où il s'est produit. Le rendu
     *  coupé a été (ou sera, file du fil client) fermé par fail ; un nouveau mailleur et un nouveau rendu seront créés
     *  par ensureWorker à la prochaine activation. */
    private static synchronized void resetAfterFailure() {
        worker = null;
        renderer = null;
        sent = false;
        levelSeen = null;
        farBlocks = 0;
        camChunk = Long.MIN_VALUE;
        store().setWaker(null);
        store().off = "";
        disabled = false;
        System.err.println("[sage_link] rendu lointain : etat d'echec leve (nouvelle session)");
    }

    public static boolean disabled() { return disabled; }
}
