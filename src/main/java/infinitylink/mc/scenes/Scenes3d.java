package infinitylink.mc.scenes;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Transformation;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import infinitylink.core.LinkState;
import infinitylink.core.Wire;
import infinitylink.core.scenes.CacheDisque;
import infinitylink.core.scenes.Contrat;
import infinitylink.core.scenes.Contrat.Cle;
import infinitylink.core.scenes.Magasin;
import infinitylink.core.scenes.ModelesV1;
import infinitylink.core.scenes.Plan;
import infinitylink.core.scenes.Selection;
import infinitylink.mc.Bridge;
import infinitylink.mc.SagePayload;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.LightLayer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** InfinityLink 1.1.4 : capacités « maillages » (DOCS/INFINITYLINK-MAILLAGES-PROTOCOLE.md, scènes 3D haute définition
 *  du plugin modeles_3d) et « modeles » (DOCS/INFINITYLINK-MODELES-PROTOCOLE.md, modèles BlockBench du plugin
 *  modeles_bb), un seul moteur de rendu pour les deux.
 *
 *  Fils : réception (un fil, dans l'ordre : assemblage, BESOIN), travail (SHA-256, validation, décodage, PNG,
 *  mipmaps, cache disque), rendu (téléversement dans un budget par image, culling hiérarchique, niveau par instance,
 *  dessin instancié juste après le terrain opaque, dans la même passe). Rien de lourd sur le fil de rendu.
 *
 *  Placement : une scène est dessinée à la place de l'item d'un item_display marqué {"sage":"scene:<id>"} (ou
 *  "modele:<id>") : position interpolée, orientation (billboard), transformation de l'entité ; un modèle, comme
 *  l'item qu'il remplace, est en plus tourné de 180° autour de y (DisplayRenderer.ItemDisplayRenderer). Scène inconnue
 *  ou pas encore prête : l'item de repli reste dessiné par le jeu.
 *
 *  Toute exception coupe les scènes pour la session : journal une fois, VRAM libérée, items de repli, le jeu continue. */
public final class Scenes3d {
    private Scenes3d() {}

    // ---------------------------------------------------------------------------------------------------- options
    static final String CONFIG = "infinitylink.properties";
    static volatile boolean optionMaillages = true, optionModeles = true;
    static volatile boolean economie;
    static volatile double seuilPx = 2;
    static volatile long budgetTriangles = 2_000_000, budgetAppels = 4000, vramOctets = 512L << 20, cacheOctets = 2048L << 20, envoiOctets = 4L << 20;
    static volatile int coteMax = 4096;

    public static boolean maillagesOption() { return optionMaillages && !disabled; }
    public static boolean modelesOption() { return optionModeles && !disabled; }

    /** Réglages (config/infinitylink.properties, clés ajoutées au fichier si absentes). qualite = auto (économie si
     *  la JVM a moins de 3 Gio ou 4 cœurs au plus), normale ou economie ; 0 = valeur de la qualité (contrat §8.5). */
    public static void loadOptions() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve(CONFIG);
            Properties pr = new Properties();
            if (Files.isRegularFile(p)) try (InputStream in = Files.newInputStream(p)) { pr.load(in); }
            if (pr.getProperty("maillages") == null) {
                Files.createDirectories(p.getParent());
                Files.writeString(p, "\n# InfinityLink 1.1.4 : scenes 3D (capacites maillages et modeles)\n"
                        + "# maillages, modeles : true/false ; maillages_qualite : auto, normale ou economie\n"
                        + "# 0 = valeur de la qualite : seuil_px 2/6, triangles 2000000/500000, appels 4000/1500, vram_mb 512/128,\n"
                        + "# texture_max 4096/1024, cache_mb 2048/512 (par serveur), envoi_kio 4096/1024 (televersement par image)\n"
                        + "maillages=true\nmodeles=true\nmaillages_qualite=auto\nmaillages_seuil_px=0\nmaillages_triangles=0\nmaillages_appels=0\n"
                        + "maillages_vram_mb=0\nmaillages_texture_max=0\nmaillages_cache_mb=0\nmaillages_envoi_kio=0\n",
                        StandardCharsets.ISO_8859_1, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            optionMaillages = !"false".equalsIgnoreCase(pr.getProperty("maillages", "true").trim()) && !"off".equals(infinitylink.core.Props.get("maillages"));
            optionModeles = !"false".equalsIgnoreCase(pr.getProperty("modeles", "true").trim()) && !"off".equals(infinitylink.core.Props.get("modeles"));
            String q = pr.getProperty("maillages_qualite", "auto").trim().toLowerCase(Locale.ROOT);
            economie = q.startsWith("eco") || (q.equals("auto") && (Runtime.getRuntime().maxMemory() < (3L << 30) || Runtime.getRuntime().availableProcessors() <= 4));
            seuilPx = reel(pr, "maillages_seuil_px", economie ? 6 : 2, 0.5, 64);
            budgetTriangles = (long) reel(pr, "maillages_triangles", economie ? 500_000 : 2_000_000, 10_000, 1e9);
            budgetAppels = (long) reel(pr, "maillages_appels", economie ? 1500 : 4000, 50, 1e6);
            vramOctets = (long) reel(pr, "maillages_vram_mb", economie ? 128 : 512, 16, 65536) << 20;
            coteMax = (int) reel(pr, "maillages_texture_max", economie ? 1024 : 4096, 16, 4096);
            cacheOctets = (long) reel(pr, "maillages_cache_mb", economie ? 512 : 2048, 16, 1 << 20) << 20;
            envoiOctets = (long) reel(pr, "maillages_envoi_kio", economie ? 1024 : 4096, 64, 1 << 20) << 10;
            System.err.println(String.format(Locale.ROOT, "[infinitylink] scenes 3d : maillages %s, modeles %s, qualite %s (seuil %.1f px, %d triangles, %d appels,"
                    + " VRAM %d Mio, textures %d px, cache %d Mio, envoi %d Kio/image)", optionMaillages ? "oui" : "non", optionModeles ? "oui" : "non",
                    economie ? "economie" : "normale", seuilPx, budgetTriangles, budgetAppels, vramOctets >> 20, coteMax, cacheOctets >> 20, envoiOctets >> 10));
        } catch (Throwable t) {
            Bridge.STATE.error("options des scenes (valeurs par defaut)", t);
        }
    }

    private static double reel(Properties pr, String cle, double defaut, double min, double max) {
        try {
            double v = Double.parseDouble(pr.getProperty(cle, "0").trim());
            return v <= 0 ? defaut : Math.max(min, Math.min(max, v));
        } catch (RuntimeException e) { return defaut; }
    }

    // ---------------------------------------------------------------------------------------------------- session
    /** Événements du réseau et du travail vers le rendu. */
    private record Retrait(String marqueur) {}
    private record Vidage(String prefixe) {}
    private record ArriveeNiveau(Cle k, Contrat.NiveauGpu n, boolean evincable) {}
    private record ArriveeTexture(Cle k, NativeImage[] mips, boolean lisse, boolean evincable) {}

    /** Octets de messages en attente sur le fil de réception au plus : au-delà, message refusé et compté (un serveur
     *  qui inonde ne remplit pas le tas ; le serveur plafonne son débit à 2 Mio/s par joueur, contrat §9). */
    static final long MAX_EN_FILE = 128L << 20;

    static final class Session {
        final Connection connexion;
        final String serveur;
        final ExecutorService reception, travail;
        final ConcurrentLinkedQueue<Object> file = new ConcurrentLinkedQueue<>();
        /** Créé par la première tâche du fil de réception (l'indexation du cache disque ne se fait pas sur le fil réseau) ;
         *  lu seulement sur ce fil (et par ses propres tâches de travail). */
        Magasin magasin;
        final ModelesV1 modeles;
        final AtomicInteger modelesCharges = new AtomicInteger();
        final java.util.concurrent.ConcurrentMap<String, String> modelNames = new java.util.concurrent.ConcurrentHashMap<>();
        final AtomicLong modelRevision = new AtomicLong();
        /** Mémoire native des textures décodées pas encore téléversées ni fermées, plafonnée : au-delà, le décodage
         *  attend (differes) qu'une texture soit téléversée ou libérée. */
        final AtomicLong decodees = new AtomicLong();
        final ConcurrentLinkedQueue<Runnable> differes = new ConcurrentLinkedQueue<>();
        final long plafondDecode;
        final AtomicLong enFile = new AtomicLong();
        volatile boolean fermee;
        volatile String bilan = "";
        long dernierEtat;
        String dernierModeles = "";

        Session(Connection c) {
            connexion = c;
            serveur = serveur();
            reception = Executors.newSingleThreadExecutor(fils("infinitylink-scenes-reception", Thread.NORM_PRIORITY));
            travail = Executors.newFixedThreadPool(Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 2)), fils("infinitylink-scenes-travail", Thread.MIN_PRIORITY));
            plafondDecode = Math.max(64L << 20, Math.min(256L << 20, vramOctets / 2));
            modeles = new ModelesV1(new Relais(this, false));
            reception.execute(() -> {
                CacheDisque cache = null;
                try {
                    cache = new CacheDisque(FabricLoader.getInstance().getGameDir().resolve("infinitylink").resolve("maillages").resolve(serveur), cacheOctets);
                } catch (Throwable t) { Bridge.STATE.error("cache disque des scenes (desactive)", t); }
                magasin = new Magasin(cache, b -> envoyer(Contrat.CANAL_RETOUR, b), travail, new Relais(this, true), economie);
            });
        }

        /** Des textures décodées ont été téléversées ou fermées : leur mémoire est rendue, des décodages reprennent. */
        void libererDecode(long octets) {
            decodees.addAndGet(-octets);
            for (int i = 0; i < 4; i++) {
                Runnable r = differes.poll();
                if (r == null) break;
                try { travail.execute(r); } catch (RejectedExecutionException e) { break; }
            }
        }

        void fermerMips(NativeImage[] mips) {
            long o = ScGpu.octets(mips);
            ScGpu.fermer(mips);
            libererDecode(o);
        }

        /** Vide la file vers le rendu (aucun rendu ne la lira) : les NativeImage en attente sont fermées. */
        void vider() {
            for (Object o; (o = file.poll()) != null; ) if (o instanceof ArriveeTexture a) fermerMips(a.mips());
        }

        void envoyer(String canal, byte[] b) {
            Connection c = connexion;
            if (c != null && !fermee && c.isConnected()) c.send(new ServerboundCustomPayloadPacket(SagePayload.out(canal, b)));
        }

        void fermer() {
            fermee = true;
            reception.shutdownNow();
            travail.shutdownNow();
            differes.clear();
            vider();
        }

        private static String serveur() {
            String ip = null;
            try { ServerData d = Minecraft.getInstance().getCurrentServer(); if (d != null) ip = d.ip; } catch (Throwable ignored) { }
            if (ip == null || ip.isBlank()) return "local";
            StringBuilder s = new StringBuilder();
            for (char ch : ip.toLowerCase(Locale.ROOT).toCharArray()) s.append(ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9' || ch == '.' || ch == '-' ? ch : '_');
            return s.length() > 80 ? s.substring(0, 80) : s.toString();
        }
    }

    private static ThreadFactory fils(String nom, int priorite) {
        AtomicInteger n = new AtomicInteger();
        return r -> { Thread t = new Thread(r, nom + "-" + n.incrementAndGet()); t.setDaemon(true); t.setPriority(priorite); return t; };
    }

    /** Relais des rappels du magasin et des modèles vers le rendu (plan calculé ici, PNG décodé sur le fil de travail). */
    private static final class Relais implements Magasin.Ecouteur {
        final Session s;
        final boolean maillages;
        Relais(Session s, boolean maillages) { this.s = s; this.maillages = maillages; }

        @Override public void scene(String marqueur, Contrat.Description d, boolean commeItem) {
            s.file.add(new Plan(marqueur, d, commeItem, economie));
            if (!maillages && marqueur.startsWith("modele:")) {
                s.modelNames.put(marqueur.substring(7), d.nom());
                s.modelRevision.incrementAndGet();
            }
        }
        @Override public void retiree(String marqueur) {
            s.file.add(new Retrait(marqueur));
            if (!maillages && marqueur.startsWith("modele:")) {
                s.modelNames.remove(marqueur.substring(7));
                s.modelRevision.incrementAndGet();
            }
        }
        @Override public void videe() {
            s.file.add(new Vidage(maillages ? "scene:" : "modele:"));
            if (!maillages) { s.modelNames.clear(); s.modelRevision.incrementAndGet(); }
        }
        @Override public void niveau(Cle k, Contrat.NiveauGpu n) { s.file.add(new ArriveeNiveau(k, n, maillages)); }
        @Override public void texture(Cle k, Contrat.Texture t) {
            if (disabled || s.fermee) return;
            Runnable decodage = new Runnable() {
                @Override public void run() {
                    if (disabled || s.fermee) return;
                    long est = estimation(t.largeur(), t.hauteur(), coteMax);
                    long d = s.decodees.get();
                    if (d > 0 && d + est > s.plafondDecode) { s.differes.add(this); return; } // repris par libererDecode
                    s.decodees.addAndGet(est);
                    NativeImage[] mips = null;
                    try {
                        mips = mipmaps(t.png(), coteMax);
                        s.decodees.addAndGet(ScGpu.octets(mips) - est);
                        if (disabled || s.fermee) { s.fermerMips(mips); return; }
                        s.file.add(new ArriveeTexture(k, mips, maillages, maillages));
                    } catch (Throwable e) {
                        if (mips != null) s.fermerMips(mips); else s.libererDecode(est);
                        System.err.println("[infinitylink] scenes : texture " + k.hex() + " illisible : " + e);
                        if (maillages) {
                            try { s.reception.execute(() -> { Magasin mg = s.magasin; if (mg != null) mg.rejetExterne(k, "PNG illisible (" + e.getClass().getSimpleName() + ")"); }); }
                            catch (RejectedExecutionException ignored) { }
                        }
                    }
                }
            };
            try { s.travail.execute(decodage); } catch (RejectedExecutionException ignored) { }
        }
    }

    /** Mémoire native des mipmaps d'une texture w × h réduite sous coteMax (4/3 de l'image de base). */
    static long estimation(int w, int h, int coteMax) {
        while (w > coteMax || h > coteMax) { w = Math.max(1, w / 2); h = Math.max(1, h / 2); }
        return 4L * w * h * 4 / 3 + 64;
    }

    /** PNG décodé (RGBA), réduit par moitiés sous coteMax, puis chaîne de mipmaps jusqu'à 1×1 (fil de travail). */
    static NativeImage[] mipmaps(byte[] png, int coteMax) throws java.io.IOException {
        NativeImage img = NativeImage.read(png);
        try {
            while (img.getWidth() > coteMax || img.getHeight() > coteMax) {
                NativeImage r = new NativeImage(Math.max(1, img.getWidth() / 2), Math.max(1, img.getHeight() / 2), false);
                img.resizeSubRectTo(0, 0, img.getWidth(), img.getHeight(), r);
                img.close();
                img = r;
            }
            List<NativeImage> l = new ArrayList<>();
            l.add(img);
            try {
                NativeImage c = img;
                while ((c.getWidth() > 1 || c.getHeight() > 1) && l.size() < 13) {
                    NativeImage r = new NativeImage(Math.max(1, c.getWidth() / 2), Math.max(1, c.getHeight() / 2), false);
                    l.add(r);
                    c.resizeSubRectTo(0, 0, c.getWidth(), c.getHeight(), r);
                    c = r;
                }
            } catch (Throwable t) {
                for (NativeImage m : l) if (m != img) m.close();
                throw t;
            }
            return l.toArray(new NativeImage[0]);
        } catch (Throwable t) {
            img.close();
            throw t;
        }
    }

    private static volatile Connection connexion;
    private static volatile Session session;
    private static volatile boolean disabled, fermerDemande;

    /** Fin de handleLoginFinished (Bridge.sendHello) : la connexion de la session à venir. */
    public static void connexion(Connection c) { connexion = c; }

    private static synchronized Session session() {
        Session s = session;
        if (s == null || s.fermee) s = session = new Session(connexion);
        return s;
    }

    /** sage:link/meshes et sage:link/models (fil réseau) : remis dans l'ordre au fil de réception. */
    public static void recevoir(String path, byte[] data) {
        if (disabled) return;
        Session s = session();
        long n = data.length;
        if (s.enFile.addAndGet(n) > MAX_EN_FILE) {
            s.enFile.addAndGet(-n);
            Bridge.STATE.reject("scenes : file de reception pleine, sage:" + path + " refuse");
            return;
        }
        try {
            s.reception.execute(() -> {
                try {
                    if (Contrat.CANAL.equals(path)) { Magasin mg = s.magasin; if (mg != null) mg.recevoir(data); }
                    else s.modeles.recevoir(data);
                } catch (Throwable t) {
                    Bridge.STATE.error("scenes : reception " + path, t);
                } finally {
                    s.enFile.addAndGet(-n);
                }
            });
        } catch (RejectedExecutionException ignored) { s.enFile.addAndGet(-n); } // session fermée entre-temps
    }

    public static void onDisconnect() {
        Session s;
        synchronized (Scenes3d.class) { s = session; session = null; }
        if (s != null) s.fermer();
        infinitylink.mc.tabs.ModelTabs.clear();
    }

    // ---------------------------------------------------------------------------------------------------- tick
    /** Tête de Minecraft.tick (fil client) : ÉTAT (un par seconde au plus), models_ready, libération hors monde. */
    public static void tick(Minecraft mc) {
        try {
            // hors de toute passe : c'est ici que se ferment les ressources d'un rendu coupé (fail)
            if (fermerDemande) { fermerDemande = false; fermerRendu(); }
            Rendu r = rendu;
            if (r != null && session != r.s) fermerRendu(); // session finie (un monde nul entre deux dimensions ne compte pas)
            Session s = session;
            if (disabled && s != null) s.vider(); // coupé : personne ne lit la file
            if (disabled && Bridge.STATE.phase() != LinkState.Phase.CONNECTED) { disabled = false; System.err.println("[infinitylink] scenes 3d : etat d'echec leve (nouvelle session)"); }
            if (s == null || s.fermee || disabled) return;
            long now = System.currentTimeMillis();
            if (now - s.dernierEtat < 1000) return;
            s.dernierEtat = now;
            infinitylink.mc.tabs.ModelTabs.refresh(s, s.modelRevision.get(), s.modelNames,
                    Bridge.STATE.modelesOn() && modelesOption() && infinitylink.mc.tabs.SageTabs.registered());
            // ÉTAT et models_ready sur le fil de réception : le fil client ne prend jamais les verrous du magasin ni
            // des modèles (tenus pendant les décodages)
            boolean maillages = Bridge.STATE.maillagesOn(), modeles = Bridge.STATE.modelesOn();
            int n = s.modelesCharges.get();
            try {
                s.reception.execute(() -> {
                    Magasin mg = s.magasin;
                    if (mg != null) {
                        if (maillages) { byte[] e = mg.etatSiChange(); if (e != null) s.envoyer(Contrat.CANAL_RETOUR, e); }
                        s.bilan = mg.bilan();
                    }
                    if (modeles) {
                        int rj = s.modeles.rejets();
                        String cle = n + "/" + rj;
                        if (!cle.equals(s.dernierModeles) && (n > 0 || rj > 0)) {
                            s.dernierModeles = cle;
                            s.envoyer("link/models_ready", new Wire.Out().varInt(n).varInt(rj).string(court(s.modeles.dernierMotif()), 256).toBytes());
                        }
                    }
                });
            } catch (RejectedExecutionException ignored) { }
        } catch (Throwable t) {
            fail("tick", t);
        }
    }

    private static String court(String m) {
        byte[] b = m.getBytes(StandardCharsets.UTF_8);
        if (b.length <= 256) return m;
        int k = 256;
        while (k > 0 && (b[k] & 0xC0) == 0x80) k--;
        return new String(b, 0, k, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------------------------------------------- ancres
    /** Une entité à dessiner comme scène, relevée à l'extraction de l'image. */
    record Ancre(Plan plan, int id, double x, double y, double z, Matrix3f rotation, Matrix4f transformation, int lumiere) {}

    private static List<Ancre> collecte = new ArrayList<>(), courantes = new ArrayList<>();
    /** Ancres par image au plus : au-delà, l'item de repli reste (le coût d'une image ne dépend pas du nombre d'entités). */
    static final int MAX_ANCRES = 512;
    private static final Map<ItemStack, String> MARQUEURS = new WeakHashMap<>();

    /** Tête de LevelExtractor.extract : nouvelle image. */
    public static void debutExtraction() {
        collecte = new ArrayList<>();
    }

    private static String marqueur(ItemStack st) {
        if (st == null || st.isEmpty()) return null;
        String m = MARQUEURS.get(st);
        if (m == null) {
            CustomData cd = st.get(DataComponents.CUSTOM_DATA);
            m = cd == null ? "" : cd.copyTag().getString("sage").orElse("");
            if (!(m.startsWith("scene:") || m.startsWith("modele:"))) m = "";
            MARQUEURS.put(st, m);
        }
        return m.isEmpty() ? null : m;
    }

    /** Fin de ItemDisplayRenderer.extractRenderState (fil de rendu) : true = scène prête, l'item de repli est effacé. */
    public static boolean ancre(Display.ItemDisplay e, ItemDisplayEntityRenderState s) {
        Rendu r = rendu;
        if (disabled || r == null || s.renderState == null) return false;
        try {
            Display.ItemDisplay.ItemRenderState irs = e.itemRenderState();
            String m = marqueur(irs == null ? null : irs.itemStack());
            if (m == null || collecte.size() >= MAX_ANCRES) return false;
            r.marques++;
            Plan p = r.plans.get(m);
            if (p == null) { r.inconnues++; return false; }
            if (!r.affichable(p)) { r.pasPretes++; return false; }
            Display.RenderState rs = s.renderState;
            Quaternionf q = switch (rs.billboardConstraints()) {
                case FIXED -> new Quaternionf().rotationYXZ(-0.017453292f * s.entityYRot, 0.017453292f * s.entityXRot, 0f);
                case HORIZONTAL -> new Quaternionf().rotationYXZ(-0.017453292f * s.entityYRot, 0.017453292f * -s.cameraXRot, 0f);
                case VERTICAL -> new Quaternionf().rotationYXZ(-0.017453292f * (s.cameraYRot - 180f), 0.017453292f * s.entityXRot, 0f);
                case CENTER -> new Quaternionf().rotationYXZ(-0.017453292f * (s.cameraYRot - 180f), 0.017453292f * -s.cameraXRot, 0f);
            };
            Transformation t = rs.transformation().get(s.interpolationProgress);
            Matrix4fc tm = t.getMatrix();
            collecte.add(new Ancre(p, e.getId(), s.x, s.y, s.z, new Matrix3f().set(q), new Matrix4f(tm), s.lightCoords));
            return true;
        } catch (Throwable t) {
            fail("ancre", t);
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------------- rendu
    private static volatile Rendu rendu;

    /** Tête de LevelRenderer.render (fil de rendu, hors de toute passe) : événements, téléversement, sélection. */
    public static void frameBegin(CameraRenderState camera) {
        if (disabled) return;
        try {
            Session s = session;
            Rendu r = rendu;
            if (r != null && r.s != s) { fermerRendu(); r = null; }
            if (s == null || s.fermee) return;
            if (r == null) r = rendu = new Rendu(s);
            courantes = collecte;
            r.image(camera, courantes);
        } catch (Throwable t) {
            fail("preparation", t);
        }
    }

    /** Fin de LevelRenderer.executeSolid : même passe que le terrain opaque. */
    public static void draw(RenderPass pass) {
        Rendu r = rendu;
        if (disabled || r == null) return;
        try {
            r.dessiner(pass);
        } catch (Throwable t) {
            fail("dessin", t);
        }
    }

    private static void fermerRendu() {
        Rendu r = rendu;
        rendu = null;
        collecte = new ArrayList<>();
        courantes = collecte;
        if (r != null) try { r.fermer(); } catch (Throwable ignored) { }
    }

    static void fail(String ou, Throwable t) {
        if (disabled) return;
        disabled = true;
        try {
            Bridge.STATE.error("scenes 3d coupees (" + ou + ")", t);
            System.err.println("[infinitylink] scenes 3d coupees (" + ou + ") : items de repli jusqu'a la prochaine session");
            t.printStackTrace();
        } catch (Throwable ignored) { }
        fermerDemande = true; // fermé au prochain tick, jamais pendant une passe de rendu encore ouverte
    }

    /** État GPU et plans de la session (fil de rendu). */
    static final class Rendu {
        final Session s;
        final ScGpu gpu;
        final Map<String, Plan> plans = new HashMap<>();
        private final Map<Plan, Map<Integer, EtatAncre>> etats = new IdentityHashMap<>();
        private final Map<Plan, long[]> affichage = new IdentityHashMap<>(); // {version GPU, affichable, arrivée ms, image}
        private final Map<Cle, Long> demandes = new HashMap<>();
        /** Nombre de plans courants qui citent chaque clé (tenu plan par plan, jamais recalculé en entier). */
        private final Map<Cle, Integer> refs = new HashMap<>();
        private int modeles;
        private final double[] mq = new double[12];
        private int proches;
        private long image;
        private double facteur = 1;
        // pipelines : 0 opaque, 1 opaque deux faces, 2 découpe, 3 découpe deux faces, 4 translucide, 5 translucide deux faces
        private final RenderPipeline[] defs = new RenderPipeline[6];
        @SuppressWarnings("unchecked")
        private final CompletableFuture<CompiledRenderPipeline.Pending>[] enCours = new CompletableFuture[6];
        private final CompiledRenderPipeline[] pipes = new CompiledRenderPipeline[6];
        // image courante
        private final List<Groupe> groupes = new ArrayList<>();
        private final Map<ScGpu.Niveau, Groupe[]> parNiveau = new IdentityHashMap<>();
        private GpuBuffer instances;
        private ByteBuffer tampon = MemoryUtil.memAlloc(64 << 10).order(ByteOrder.LITTLE_ENDIAN);
        private long triangles, appels, dessines, ecartes, lastLog;
        // diagnostic (journal) : item_display marqués vus à l'extraction, dont scène inconnue ou pas prête, ancres de la dernière image
        long marques, inconnues, pasPretes, ancresImage;
        // mesure (journal toutes les 30 s) : temps du fil de rendu passé dans la préparation et le dessin des scènes
        private long nsPrep, nsDessin, images, maxPrep, maxDessin;
        private String dernierJournal = "";

        Rendu(Session s) {
            this.s = s;
            gpu = new ScGpu(vramOctets, k -> {
                try { s.reception.execute(() -> { Magasin mg = s.magasin; if (mg != null) mg.liberee(k); }); } catch (RejectedExecutionException ignored) { }
            }, s::libererDecode);
        }

        private static final class EtatAncre {
            byte[] prec;
            int[] lum;
            long[] lumImage;
            long vu;
        }

        /** Instances d'un même niveau (et même sens de faces) dessinées par un seul appel par amas. */
        private static final class Groupe {
            final ScGpu.Niveau n;
            final boolean inverse;
            int[] donnees = new int[13 * 8];
            int nombre;
            int[] amasVisibles; // null : tous
            int[] tr; // transformation par matériau (dessin)
            double distance = Double.MAX_VALUE;
            long decalage, triangles, appels;
            Groupe(ScGpu.Niveau n, boolean inverse) { this.n = n; this.inverse = inverse; }
            void calculer() {
                int na = amasVisibles == null ? n.amas.length : amasVisibles.length;
                long t = 0;
                for (int j = 0; j < na; j++) t += n.amas[amasVisibles == null ? j : amasVisibles[j]].nIndices() / 3;
                triangles = t * nombre;
                appels = na;
            }
            void ajouter(double[] m, int lumiere, double d) {
                if (13 * (nombre + 1) > donnees.length) donnees = Arrays.copyOf(donnees, donnees.length * 2);
                int o = 13 * nombre++;
                for (int k = 0; k < 12; k++) donnees[o + k] = Float.floatToRawIntBits((float) m[k]);
                // UV2 : (bloc × 16, ciel × 16), comme LightCoordsUtil
                donnees[o + 12] = (lumiere & 0xFFFF) | (lumiere >>> 16) << 16;
                distance = Math.min(distance, d);
            }
        }

        boolean affichable(Plan p) {
            long[] a = affichage.get(p);
            if (a == null) return false;
            long v = gpu.version();
            if (a[0] == v && (a[1] != 0 || image - a[3] < 60)) return a[1] != 0; // recalcul : ressources changées, ou 1 s
            a[0] = v;
            a[3] = image;
            int prets = 0;
            for (int mi : p.maillagesUtiles)
                for (int l = 0; l < p.cles[mi].length; l++) if (p.permis[mi][l] && prete(p.cles[mi][l])) { prets++; break; }
            a[1] = prets > 0 && (prets == p.maillagesCites || System.currentTimeMillis() - a[2] > 5000) ? 1 : 0;
            return a[1] != 0;
        }

        /** Niveau résident et ses textures prêtes (ou absentes depuis 10 s : dessiné en blanc). */
        boolean prete(Cle k) {
            ScGpu.Niveau n = gpu.niveau(k);
            if (n == null || !n.pret) return false;
            boolean ok = true;
            for (Cle t : n.textures) {
                ScGpu.Tex x = gpu.texture(t);
                if (x == null || !x.pret) {
                    if (x == null && n.evincable) vouloir(null, t); // évincée ou abandonnée faute de place : redemandée
                    if (image - n.arrivee < 600) ok = false;
                }
            }
            return ok;
        }

        private void vouloir(Plan p, Cle k) {
            if (p != null && p.commeItem) return; // un modèle n'est pas redemandable (il reste résident)
            Long d = demandes.get(k);
            if (d != null && image - d < 120) return;
            demandes.put(k, image);
            try { s.reception.execute(() -> { Magasin mg = s.magasin; if (mg != null) mg.recharger(k); }); } catch (RejectedExecutionException ignored) { }
        }

        private void evenements() {
            Object o;
            int n = 0;
            while (n++ < 4096 && (o = s.file.poll()) != null) {
                switch (o) {
                    case Plan p -> {
                        Plan avant = plans.put(p.marqueur, p);
                        if (avant != null) oublier(avant);
                        for (Cle k : p.citees) refs.merge(k, 1, Integer::sum);
                        if (p.commeItem) modeles++;
                        affichage.put(p, new long[]{Long.MIN_VALUE, 0, System.currentTimeMillis(), 0});
                    }
                    case Retrait r -> { Plan p = plans.remove(r.marqueur()); if (p != null) oublier(p); }
                    case Vidage v -> {
                        List<Plan> partis = new ArrayList<>();
                        plans.entrySet().removeIf(e -> e.getKey().startsWith(v.prefixe()) && partis.add(e.getValue()));
                        for (Plan p : partis) oublier(p);
                    }
                    case ArriveeNiveau a -> gpu.ajouter(a.k(), a.n(), a.evincable(), image);
                    case ArriveeTexture a -> gpu.ajouter(a.k(), a.mips(), a.lisse(), a.evincable(), image);
                    default -> { }
                }
            }
            s.modelesCharges.set(modeles);
            if (image % 120 == 0) {
                gpu.ramasser(refs.keySet(), image, 600); // plus citées depuis 10 s : libérées
                demandes.values().removeIf(d -> image - d > 600);
                for (Map<Integer, EtatAncre> m : etats.values()) m.values().removeIf(e -> image - e.vu > 600);
            }
        }

        private void oublier(Plan p) {
            etats.remove(p);
            affichage.remove(p);
            if (p.commeItem) modeles--;
            for (Cle k : p.citees) refs.computeIfPresent(k, (x, n) -> n > 1 ? n - 1 : null);
        }

        private void compiler() {
            boolean pret = true;
            for (int i = 0; i < 6; i++) {
                if (pipes[i] != null) continue;
                if (enCours[i] == null) {
                    defs[i] = ScShaders.pipeline(i / 2, (i & 1) == 0);
                    com.mojang.renderpearl.api.device.GpuDevice device = RenderSystem.getDevice();
                    enCours[i] = device.compilePipeline(defs[i], new ScShaders.Source(), Util.backgroundExecutor());
                }
                if (enCours[i].isDone()) {
                    pipes[i] = enCours[i].join().finishCompile();
                    if (pipes[i] == null) throw new IllegalStateException("compilation du pipeline " + defs[i].getLocation() + " refusee");
                } else pret = false;
            }
            if (!pret) return;
        }

        private boolean pret() { for (CompiledRenderPipeline p : pipes) if (p == null) return false; return true; }

        /** Préparation de l'image : événements, téléversement, puis sélection et tampon d'instances. */
        void image(CameraRenderState cam, List<Ancre> ancres) {
            long t0 = System.nanoTime();
            try { preparer(cam, ancres); } finally {
                long d = System.nanoTime() - t0;
                nsPrep += d; maxPrep = Math.max(maxPrep, d); images++;
            }
            journal();
        }

        private void preparer(CameraRenderState cam, List<Ancre> ancres) {
            image++;
            ancresImage = ancres.size();
            evenements();
            if (plans.isEmpty() && gpu.enAttente() == 0) { groupes.clear(); return; }
            compiler();
            gpu.televerser(envoiOctets, image);
            groupes.clear();
            parNiveau.clear();
            triangles = appels = dessines = 0;
            if (!pret() || cam == null || cam.pos == null || ancres.isEmpty()) return;
            gpu.blanche(); // créée ici, hors de toute passe
            Minecraft mc = Minecraft.getInstance();
            Matrix4f clip = new Matrix4f(cam.projectionMatrix).mul(cam.viewRotationMatrix);
            float[] cf = new float[16];
            clip.get(cf);
            double[] plansTronc = Selection.plans(cf);
            Selection.Parametres pa = new Selection.Parametres();
            pa.pxParBloc1 = mc.getWindow().getHeight() * 0.5 * Math.abs(cam.projectionMatrix.m11());
            pa.seuilPx = seuilPx * facteur;
            // au-delà du budget, le seuil monte, et les instances de moins de pxMin pixels sont écartées
            pa.pxMin = 0.75 * facteur;
            pa.testsMax = economie ? 250_000 : 1_000_000;
            pa.distanceMax = Math.max(64, mc.options.getEffectiveRenderDistance() * 16) + 32;
            double cx = cam.pos.x, cy = cam.pos.y, cz = cam.pos.z;
            double[] e = new double[12];
            List<Ancre> triees = new ArrayList<>(ancres);
            triees.sort(Comparator.comparingDouble(a -> (a.x() - cx) * (a.x() - cx) + (a.y() - cy) * (a.y() - cy) + (a.z() - cz) * (a.z() - cz)));
            proches = 0;
            for (Ancre a : triees) {
                Plan p = a.plan();
                if (plans.get(p.marqueur) != p) continue;
                entite(a, cx, cy, cz, e);
                EtatAncre st = etats.computeIfAbsent(p, x -> new HashMap<>()).computeIfAbsent(a.id(), x -> new EtatAncre());
                st.vu = image;
                if (st.prec == null || st.prec.length != p.d.instances().length) st.prec = new byte[p.d.instances().length];
                boolean parCellule = !p.commeItem && p.d.cellules().length > 1;
                if (parCellule && (st.lum == null || st.lum.length != p.d.cellules().length)) {
                    st.lum = new int[p.d.cellules().length];
                    st.lumImage = new long[st.lum.length];
                    Arrays.fill(st.lumImage, Long.MIN_VALUE / 2);
                }
                Selection.parcourir(p, e, plansTronc, pa, st.prec, new Selection.Ressources() {
                    @Override public boolean prete(Cle k) { return Rendu.this.prete(k); }
                    @Override public void vouloir(Cle k) { Rendu.this.vouloir(p, k); }
                }, (i, c, mi, l, m, d, boite) -> {
                    ScGpu.Niveau n = gpu.niveau(p.cles[mi][l]);
                    n.vu = image;
                    for (Cle t : n.textures) { ScGpu.Tex x = gpu.texture(t); if (x != null) x.vu = image; }
                    int lum = parCellule ? lumiere(mc, st, p, c, e, cx, cy, cz) : a.lumiere();
                    boolean inverse = Selection.determinant(m) < 0;
                    // déquantification repliée dans la matrice, en double (contrat §6.2) : M' = M · T(q_origine) ; le
                    // shader ne fait que q × q_pas, petit : pas de tremblement loin de l'origine du maillage
                    System.arraycopy(m, 0, mq, 0, 12);
                    for (int r = 0; r < 3; r++) mq[4 * r + 3] += m[4 * r] * n.qo[0] + m[4 * r + 1] * n.qo[1] + m[4 * r + 2] * n.qo[2];
                    // instance proche et découpée en plusieurs amas : amas écartés un par un (contrat §8.3), 64 par image au plus
                    double dx = boite[3] - boite[0], dy = boite[4] - boite[1], dz = boite[5] - boite[2];
                    if (n.amas.length > 1 && proches < 64 && d < 0.5 * Math.sqrt(dx * dx + dy * dy + dz * dz)) {
                        proches++;
                        int[] vis = amasVisibles(n, m, plansTronc);
                        if (vis.length == 0) return;
                        Groupe g = new Groupe(n, inverse);
                        g.amasVisibles = vis;
                        g.ajouter(mq, lum, d);
                        groupes.add(g);
                        return;
                    }
                    Groupe[] gs = parNiveau.computeIfAbsent(n, x -> new Groupe[2]);
                    int k = inverse ? 1 : 0;
                    if (gs[k] == null) { gs[k] = new Groupe(n, inverse); groupes.add(gs[k]); }
                    gs[k].ajouter(mq, lum, d);
                }, image);
            }
            // plafond dur par image (le facteur ne suffit pas : le niveau le plus grossier reste dessiné) : au-delà de
            // 2 × le budget de triangles ou d'appels, ou du plafond d'instances, les groupes les plus lointains sont écartés
            long maxInstances = economie ? 32_768 : 131_072, tri = 0, app = 0, inst = 0;
            for (Groupe g : groupes) { g.calculer(); tri += g.triangles; app += g.appels; inst += g.nombre; }
            if (tri > 2 * budgetTriangles || app > 2 * budgetAppels || inst > maxInstances) {
                groupes.sort(Comparator.comparingDouble(g -> g.distance));
                long t2 = 0, a2 = 0, i2 = 0;
                int garde = 0;
                for (Groupe g : groupes) {
                    if (t2 + g.triangles > 2 * budgetTriangles || a2 + g.appels > 2 * budgetAppels || i2 + g.nombre > maxInstances) break;
                    t2 += g.triangles; a2 += g.appels; i2 += g.nombre; garde++;
                }
                ecartes += groupes.size() - garde;
                groupes.subList(garde, groupes.size()).clear();
            }
            // tampon d'instances de l'image, groupe par groupe
            int total = 0;
            for (Groupe g : groupes) total += g.nombre;
            long octets = (long) total * ScShaders.INSTANCE;
            if (tampon.capacity() < octets) {
                long t = tampon.capacity();
                while (t < octets) t <<= 1;
                tampon = MemoryUtil.memRealloc(tampon, (int) t).order(ByteOrder.LITTLE_ENDIAN);
            }
            tampon.clear();
            for (Groupe g : groupes) {
                g.decalage = tampon.position();
                for (int i = 0; i < 13 * g.nombre; i++) tampon.putInt(g.donnees[i]);
                triangles += g.triangles;
                appels += g.appels;
                dessines += g.nombre;
            }
            tampon.flip();
            instances = gpu.instances(image, tampon);
            // budgets (contrat §8.5) : le seuil monte jusqu'à les tenir, redescend sous la moitié
            double r = Math.max((double) triangles / budgetTriangles, (double) appels / budgetAppels);
            if (r > 1) facteur = Math.min(facteur * 1.25, 64);
            else if (r < 0.5) facteur = Math.max(facteur / 1.1, 1);
            if (gpu.prendreManque()) facteur = Math.min(facteur * 1.1, 64);
        }

        /** Lumière du monde au centre d'une cellule, en cache, rafraîchie toutes les 20 images au plus. */
        private int lumiere(Minecraft mc, EtatAncre st, Plan p, int c, double[] e, double cx, double cy, double cz) {
            boolean connue = st.lumImage[c] > Long.MIN_VALUE / 2;
            if (mc.level == null || (connue && (image + c) % 20 != 0)) return st.lum[c];
            st.lumImage[c] = image;
            float[] b = p.d.cellules()[c].bornes();
            double x = (b[0] + b[3]) * 0.5, y = (b[1] + b[4]) * 0.5, z = (b[2] + b[5]) * 0.5;
            BlockPos pos = BlockPos.containing(e[0] * x + e[1] * y + e[2] * z + e[3] + cx, e[4] * x + e[5] * y + e[6] * z + e[7] + cy, e[8] * x + e[9] * y + e[10] * z + e[11] + cz);
            int bl = mc.level.getBrightness(LightLayer.BLOCK, pos), sk = mc.level.getBrightness(LightLayer.SKY, pos);
            return st.lum[c] = bl << 4 | sk << 20;
        }

        private static int[] amasVisibles(ScGpu.Niveau n, double[] m, double[] plansTronc) {
            int[] v = new int[n.amas.length];
            int k = 0;
            float[] b = new float[6];
            double[] w = new double[6];
            for (int j = 0; j < n.amas.length; j++) {
                int[] q = n.amas[j].bornesQ();
                for (int a = 0; a < 3; a++) { b[a] = n.qo[a] + q[a] * n.qp[a]; b[a + 3] = n.qo[a] + q[a + 3] * n.qp[a]; }
                Selection.boite(m, b, 0, w);
                if (Selection.visible(plansTronc, w)) v[k++] = j;
            }
            return Arrays.copyOf(v, k);
        }

        /** Repère de la scène → monde relatif à la caméra : translation × orientation × transformation de l'entité
         *  (× rotation de 180° autour de y pour un modèle, comme l'item). */
        private static void entite(Ancre a, double cx, double cy, double cz, double[] e) {
            Matrix3f r = a.rotation();
            Matrix4f t = a.transformation();
            double[] rr = {r.m00(), r.m10(), r.m20(), 0, r.m01(), r.m11(), r.m21(), 0, r.m02(), r.m12(), r.m22(), 0};
            double[] tt = {t.m00(), t.m10(), t.m20(), t.m30(), t.m01(), t.m11(), t.m21(), t.m31(), t.m02(), t.m12(), t.m22(), t.m32()};
            Selection.composer(rr, tt, e);
            if (a.plan().commeItem) { for (int k = 0; k < 12; k += 4) { e[k] = -e[k]; e[k + 2] = -e[k + 2]; } }
            e[3] += a.x() - cx;
            e[7] += a.y() - cy;
            e[11] += a.z() - cz;
        }

        /** Fin d'executeSolid : opaques et découpes d'abord (par pipeline puis par niveau), translucides ensuite, du
         *  plus lointain au plus proche. */
        void dessiner(RenderPass pass) {
            long t0 = System.nanoTime();
            try { dessinerPasse(pass); } finally {
                long d = System.nanoTime() - t0;
                nsDessin += d; maxDessin = Math.max(maxDessin, d);
            }
        }

        private void dessinerPasse(RenderPass pass) {
            if (groupes.isEmpty() || !pret() || instances == null || instances.isClosed()) return;
            Minecraft mc = Minecraft.getInstance();
            GpuTextureView lm = mc.gameRenderer.levelLightmap();
            GpuSampler sLm = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuSampler sLisse = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR, true);
            GpuSampler sNet = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST, true);
            GpuTextureView blanche = gpu.blancheSiPrete();
            if (blanche == null) return;
            Matrix4f vue = RenderSystem.getModelViewMatrixCopy();
            // appels : (pipeline, groupe, amas, transformation)
            record Appel(int pipe, Groupe g, Contrat.Amas a, int tr) {}
            List<Appel> liste = new ArrayList<>();
            List<DynamicGpuData.Transform> trs = new ArrayList<>();
            for (Groupe g : groupes) {
                if (!g.n.pret) continue;
                int[] vis = g.amasVisibles;
                int na = vis == null ? g.n.amas.length : vis.length;
                for (int j = 0; j < na; j++) {
                    Contrat.Amas a = g.n.amas[vis == null ? j : vis[j]];
                    Contrat.Materiau mat = g.n.materiaux[a.materiau()];
                    int genre = mat.translucide() ? 2 : mat.decoupe() ? 1 : 0;
                    int pipe = 2 * genre + (mat.deuxFaces() || g.inverse ? 1 : 0);
                    if (g.tr == null) { g.tr = new int[g.n.materiaux.length]; Arrays.fill(g.tr, -1); }
                    int tr = g.tr[a.materiau()];
                    if (tr < 0) {
                        tr = g.tr[a.materiau()] = trs.size();
                        int c = mat.rgba();
                        Vector4f couleur = new Vector4f((c >>> 24) / 255f, (c >>> 16 & 0xFF) / 255f, (c >>> 8 & 0xFF) / 255f, (c & 0xFF) / 255f);
                        Matrix4f tex = new Matrix4f(
                                g.n.qp[0], g.n.qp[1], g.n.qp[2], mat.seuil(),
                                0, 0, 0, g.inverse ? 1 : 0,
                                0, 0, 0, 0,
                                0, 0, 0, 1);
                        trs.add(new DynamicGpuData.Transform(vue, couleur, new Vector3f(), tex));
                    }
                    liste.add(new Appel(pipe, g, a, tr));
                }
            }
            if (liste.isEmpty()) return;
            liste.sort(Comparator.<Appel>comparingInt(x -> x.pipe() >= 4 ? 1 : 0)
                    .thenComparingDouble(x -> x.pipe() >= 4 ? -x.g().distance : 0)
                    .thenComparingInt(Appel::pipe)
                    .thenComparingInt(x -> System.identityHashCode(x.g().n)));
            GpuBufferSlice[] slices = RenderSystem.getDynamicUniforms().writeTransforms(trs.toArray(new DynamicGpuData.Transform[0]));
            pass.pushDebugGroup(() -> "infinitylink scenes");
            try {
                int pipe = -1;
                Groupe lie = null;
                ScGpu.Niveau niv = null;
                GpuTextureView texLiee = null;
                for (Appel x : liste) {
                    if (x.pipe() != pipe) {
                        pipe = x.pipe();
                        pass.setPipeline(pipes[pipe]);
                        pass.setUniform("Lighting", RenderSystem.getShaderLights());
                        pass.setUniform("Sampler2", lm, sLm);
                        lie = null; niv = null; texLiee = null;
                    }
                    Groupe g = x.g();
                    if (g.n != niv) {
                        niv = g.n;
                        pass.setVertexBuffer(0, niv.vb.slice());
                        pass.setIndexBuffer(niv.ib, IndexType.SHORT);
                    }
                    if (g != lie) {
                        lie = g;
                        pass.setVertexBuffer(1, instances.slice(g.decalage, (long) g.nombre * ScShaders.INSTANCE));
                    }
                    Contrat.Materiau mat = niv.materiaux[x.a().materiau()];
                    GpuTextureView tv = blanche;
                    boolean lisse = true;
                    if (mat.texture() > 0) {
                        ScGpu.Tex t = gpu.texture(niv.textures[mat.texture() - 1]);
                        if (t != null && t.pret) { tv = t.v; lisse = t.lisse; }
                    }
                    if (tv != texLiee) { texLiee = tv; pass.setUniform("Sampler0", tv, lisse ? sLisse : sNet); }
                    pass.setUniform("DynamicTransforms", slices[x.tr()]);
                    pass.drawIndexed(x.a().nIndices(), g.nombre, x.a().premierIndice(), x.a().baseSommet(), 0);
                }
            } finally {
                pass.popDebugGroup();
            }
        }

        /** Toutes les 30 s (si changé) : état des scènes dans latest.log. */
        private void journal() {
            long now = System.currentTimeMillis();
            if (now - lastLog < 30_000) return;
            lastLog = now;
            String l = String.format(Locale.ROOT, "[infinitylink] scenes 3d : %d plan(s), %d niveau(x) et %d texture(s) resident(e)s, VRAM %.1f/%d Mio,"
                            + " attente %d, evinces %d, manques %d, abandons %d, decodees %.1f Mio ; image : %d instance(s), %d triangles, %d appels,"
                            + " facteur %.2f, groupes ecartes %d ; ancres %d (marques vues %d, inconnues %d, pas pretes %d) ;"
                            + " fil de rendu : preparation %.3f ms (max %.2f), dessin %.3f ms (max %.2f) par image, %d fps ; %s",
                    plans.size(), gpu.nombreNiveaux(), gpu.nombreTextures(), gpu.vram() / 1048576.0, gpu.plafond >> 20, gpu.enAttente(), gpu.evinces(),
                    gpu.manques(), gpu.abandons(), s.decodees.get() / 1048576.0, dessines, triangles, appels, facteur, ecartes,
                    ancresImage, marques, inconnues, pasPretes,
                    images == 0 ? 0 : nsPrep / 1e6 / images, maxPrep / 1e6,
                    images == 0 ? 0 : nsDessin / 1e6 / images, maxDessin / 1e6, Minecraft.getInstance().getFps(), s.bilan);
            nsPrep = nsDessin = images = maxPrep = maxDessin = ecartes = marques = inconnues = pasPretes = 0;
            if (!l.equals(dernierJournal)) { dernierJournal = l; System.err.println(l); }
        }

        void fermer() {
            gpu.fermer();
            s.vider();
            for (CompiledRenderPipeline p : pipes) try { if (p != null) p.close(); } catch (Throwable ignored) { }
            Arrays.fill(pipes, null);
            try { MemoryUtil.memFree(tampon); } catch (Throwable ignored) { }
            tampon = MemoryUtil.memAlloc(16).order(ByteOrder.LITTLE_ENDIAN);
            groupes.clear();
            plans.clear();
        }
    }
}
