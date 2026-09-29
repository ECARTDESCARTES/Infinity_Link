package infinitylink.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntPredicate;

/** État du lien (contrat §2/§3), thread-safe : réception sur le fil réseau, tick et HUD sur le fil client.
 *  Aucune méthode publique ne lève : toute anomalie devient un « rejet » compté. */
public final class LinkState {
    public enum Phase { OFF, HELLO_SENT, CONNECTED }

    /** Version annoncée au serveur (hello §4) : une seule source, `fabric.mod.json`, que build.sh recopie dans
     *  `infinitylink/core/version.txt` (partie avant « + »). Hors jar (tests java purs sans ce fichier) : « dev ». */
    public static final String VERSION = lireVersion();

    private static String lireVersion() {
        try (java.io.InputStream in = LinkState.class.getResourceAsStream("version.txt")) {
            if (in != null) {
                String v = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!v.isEmpty() && v.length() <= 32) return v;
            }
        } catch (Exception ignored) { }
        return "dev";
    }
    public static final List<String> OUR_CAPS = List.of("hud", "keys", "state");
    static final int MAX_HUD = 256;

    private volatile Phase phase = Phase.OFF;
    private volatile int proto = Msg.PROTO;
    private volatile String server = "";
    private volatile List<String> caps = List.of();
    private volatile List<Msg.KeyDef> keys = List.of();
    private final Map<String, Msg.HudEntry> hud = new LinkedHashMap<>(); // gardé par lui-même
    private final Map<String, Boolean> down = new ConcurrentHashMap<>();
    private volatile String lastKey = "";
    /** La JVM a-t-elle -XX:+AlwaysPreTouchStacks (parade aux plantages de pile du client 26.3, MC-103) ? */
    public volatile boolean pretouch = true;
    private final AtomicLong rx = new AtomicLong(), tx = new AtomicLong(), rejets = new AtomicLong();
    private final AtomicInteger logged = new AtomicInteger();
    // capacité « cubes » (§8) : fenêtre reçue, cubes connus, compteurs
    private volatile Msg.Offset window;
    private final Set<Msg.Pos> cubes = ConcurrentHashMap.newKeySet();
    private final AtomicLong cubesRx = new AtomicLong(), emptyRx = new AtomicLong(), unloadRx = new AtomicLong(), heightRx = new AtomicLong();
    // capacité « lod » (§9) : distance demandée (0 = pas de flux), magasin des tuiles
    private volatile int lodView;
    public final infinitylink.core.lod.LodStore lod = new infinitylink.core.lod.LodStore();
    // capacité « tabs » (ONGLETS-CREATIFS-SPEC.md §5) : annoncée seulement si les onglets sont enregistrés au boot
    public volatile boolean tabsCapable;
    public volatile TabsMsg.Sink tabsSink;
    public final TabsMsg.Status tabs = new TabsMsg.Status();
    public final BlocksSpec.Observation blocks = new BlocksSpec.Observation();
    // capacité « assets » (§11) : annoncée si assetsCapable (Bridge : vrai sauf -Dinfinitylink.assets=off)
    public volatile boolean assetsCapable;
    public final AssetsMsg.Status assets = new AssetsMsg.Status();
    /** Branchement mc de la capacité « assets » (préchargement, table SGB2, verdicts) ; null = observation seule. */
    public volatile infinitylink.core.assets.AssetsClient.Sink assetsSink;
    // capacité « formes » (1.1.0, lot Ph4g-link-formes) : annoncée si les blocs de réserve sont enregistrés
    public volatile boolean formesCapable;
    public volatile FormesSgb2.Sink formesSink;
    // capacité « voix » (1.1.2) : annoncée si voiceCapable (vrai sauf -Dinfinitylink.voix=off), messages remis à voiceSink
    public volatile boolean voiceCapable;
    public volatile java.util.function.BiConsumer<String, byte[]> voiceSink;
    public volatile int formesTables = 0, formesEtats = 0;

    public Phase phase() { return phase; }
    public long rx() { return rx.get(); }
    public long tx() { return tx.get(); }
    public long rejets() { return rejets.get(); }

    private void reset() {
        FormesSgb2.Sink fs = formesSink;
        if (fs != null) fs.clear();
        formesTables = 0; formesEtats = 0;
        phase = Phase.OFF;
        proto = Msg.PROTO;
        server = "";
        caps = List.of();
        keys = List.of();
        synchronized (hud) { hud.clear(); }
        down.clear();
        window = null;
        cubes.clear();
        cubesRx.set(0); emptyRx.set(0); unloadRx.set(0); heightRx.set(0);
        lodView = 0;
        lod.clear();
        lod.resetCounters();
        tabs.clear();
        assets.clear();
    }

    /** Capacités du hello : OUR_CAPS, plus « tabs » si les onglets sont enregistrés (tabsCapable). */
    public List<String> ourCaps() {
        if (!tabsCapable && !assetsCapable && !formesCapable && !voiceCapable) return OUR_CAPS;
        List<String> c = new ArrayList<>(OUR_CAPS);
        if (tabsCapable) c.add(TabsMsg.CAP_TABS);
        if (assetsCapable) c.add(AssetsMsg.CAP_ASSETS);
        if (formesCapable) c.add(FormesSgb2.CAP_FORMES);
        if (voiceCapable) c.add(infinitylink.core.voice.VoiceMsg.CAP_VOIX);
        return List.copyOf(c);
    }

    /** Connecté et capacité « assets » accordée par le manifeste (§11 ; absente = vanilla, §5). */
    public boolean assetsOn() { return phase == Phase.CONNECTED && caps.contains(AssetsMsg.CAP_ASSETS); }

    /** Connecté et capacité « voix » accordée par le manifeste. */
    public boolean voiceOn() { return phase == Phase.CONNECTED && caps.contains(infinitylink.core.voice.VoiceMsg.CAP_VOIX); }

    /** Une ligne lisible pour le chat (touche « état ») : phase, serveur, capacités, compteurs. */
    public String summary() {
        return "InfinityLink " + VERSION + " : " + phase.name().toLowerCase() + (server.isEmpty() ? "" : " sur " + server)
            + ", capacites " + caps + ", recus " + rx.get() + ", envoyes " + tx.get() + ", rejets " + rejets.get();
    }

    /** Connecté et capacité « tabs » accordée par le manifeste. */
    public boolean tabsOn() { return phase == Phase.CONNECTED && caps.contains(TabsMsg.CAP_TABS); }

    /** Entrée en configuration : repart de zéro, passe en HELLO_SENT, rend les octets du hello (§4). */
    public byte[] beginHello(String mcVersion) {
        reset();
        lastKey = "";
        phase = Phase.HELLO_SENT;
        return new Msg.Hello(Msg.PROTO, VERSION, mcVersion, ourCaps()).encode();
    }

    public void sent() { tx.incrementAndGet(); }

    public void disconnected() { reset(); }

    /** Un custom_payload sage:path reçu. data == null : message au-delà de la taille maximale. */
    public void onInbound(String path, byte[] data) {
        try {
            if (data == null) { reject("trop gros : sage:" + path); return; }
            boolean ok;
            switch (path) {
                case Msg.MANIFEST -> ok = applyManifest(Msg.Manifest.decode(data));
                case Msg.HUD -> ok = applyHud(Msg.Hud.decode(data));
                case Msg.OFFSET -> ok = applyOffset(Msg.Offset.decode(data));
                case Msg.CUBE -> ok = applyCubes(Msg.Cubes.decode(data));
                case Msg.UNLOAD_CUBE -> ok = applyUnload(Msg.Unload.decode(data));
                case Msg.HEIGHTMAP -> ok = applyHeight(Msg.HeightMap.decode(data));
                case Msg.LOD_TILE -> ok = applyLodTile(Msg.LodTile.decode(data));
                case Msg.LOD_FORGET -> ok = applyLodForget(Msg.LodForget.decode(data));
                case TabsMsg.TABS -> ok = applyTabs(data);
                case AssetsMsg.ASSETS_OFFER, AssetsMsg.BLOCKS_DELTA, AssetsMsg.UPLOAD_RESULT, AssetsMsg.DRAFT_PREVIEW -> ok = applyAssets(path, data);
                case FormesSgb2.FORMES -> ok = applyFormes(data);
                case infinitylink.core.voice.VoiceMsg.VOICE, infinitylink.core.voice.VoiceMsg.VOICE_PEERS -> ok = applyVoice(path, data);
                default -> { reject("message inconnu : sage:" + path); return; }
            }
            if (ok) rx.incrementAndGet();
        } catch (Throwable t) {
            reject("sage:" + path + " malforme : " + t);
        }
    }

    /** Corps de sage:link/cube_view (v > 0 demande le flux de cubes, 0 le coupe) ; compté comme envoyé. */
    public byte[] cubeView(int v) {
        byte[] b = new Msg.CubeView(v).encode();
        tx.incrementAndGet();
        return b;
    }

    /** Corps de sage:link/lod_view (d > 0 demande les tuiles, 0 coupe et oublie tout) ; compté comme envoyé. */
    public byte[] lodView(int d) {
        byte[] b = new Msg.LodView(d).encode();
        lodView = d;
        if (d == 0) lod.clear();
        tx.incrementAndGet();
        return b;
    }

    public int lodViewSent() { return lodView; }

    private boolean applyLodTile(Msg.LodTile t) {
        if (phase != Phase.CONNECTED || lodView == 0) { reject("lod_tile sans lod_view"); return false; }
        lod.put(t);
        return true;
    }

    private boolean applyLodForget(Msg.LodForget f) {
        if (phase != Phase.CONNECTED || lodView == 0) { reject("lod_forget sans lod_view"); return false; }
        lod.forget(f);
        return true;
    }

    /** Connecté et capacité « formes » accordée par le manifeste. */
    public boolean formesOn() { return phase == Phase.CONNECTED && caps.contains(FormesSgb2.CAP_FORMES); }

    private boolean applyVoice(String path, byte[] data) {
        if (!voiceOn()) { reject(path + " sans capacite voix"); return false; }
        java.util.function.BiConsumer<String, byte[]> s = voiceSink;
        if (s != null) s.accept(path, data);
        return true;
    }

    /** sage:link/formes : table SGB2 complète décodée ici (Java pur), appliquée aux blocs de réserve par formesSink. */
    private boolean applyFormes(byte[] data) {
        if (!formesOn()) { reject("formes sans capacite formes"); return false; }
        FormesSgb2.Table t = FormesSgb2.decode(data);
        formesTables++;
        formesEtats = t.etats().size();
        FormesSgb2.Sink s = formesSink;
        if (s != null) s.apply(t);
        return true;
    }

    /** Capacité « assets » (§11) : décodage strict et observation ; hors capacité, rejeté (le client reste vanilla). */
    private boolean applyAssets(String path, byte[] data) {
        if (!assetsOn()) { reject(path + " sans capacite assets"); return false; }
        switch (path) {
            case AssetsMsg.ASSETS_OFFER -> {
                List<AssetsMsg.Epoch> e = AssetsMsg.decodeOffer(data);
                assets.offers++;
                if (!e.isEmpty()) assets.lastEpoch = e.get(e.size() - 1).n();
                infinitylink.core.assets.AssetsClient.Sink s = assetsSink;
                if (s != null) s.offer(e);
            }
            case AssetsMsg.BLOCKS_DELTA -> {
                AssetsMsg.BlocksDelta d = AssetsMsg.BlocksDelta.decode(data);
                assets.deltas++;
                infinitylink.core.assets.AssetsClient.Sink s = assetsSink;
                if (s != null) s.delta(d);
            }
            case AssetsMsg.UPLOAD_RESULT -> {
                AssetsMsg.Result r = AssetsMsg.Result.decode(data);
                assets.lastVerdict = r.verdict();
                assets.results++;
                infinitylink.core.assets.AssetsClient.Sink s = assetsSink;
                if (s != null) s.result(r);
            }
            default -> { assets.draftOn = AssetsMsg.Draft.decode(data).sha1() != null; assets.drafts++; }
        }
        return true;
    }

    /** sage:link/tabs : décodé ici (Java pur), appliqué par la couche mc (tabsSink) sur le fil client. */
    private boolean applyTabs(byte[] data) {
        if (!tabsOn()) { reject("tabs sans capacite tabs"); return false; }
        TabsMsg.Catalog c;
        try {
            c = TabsMsg.decode(data);
        } catch (TabsMsg.Bad e) {
            tabs.code = e.code;
            reject("tabs code " + e.code + " : " + e.getMessage());
            TabsMsg.Sink s = tabsSink;
            if (s != null) s.failed(e.code);
            return false;
        }
        tabs.rx++;
        TabsMsg.Sink s = tabsSink;
        if (s != null) s.catalog(c);
        return true;
    }

    public Msg.Offset window() { return window; }
    public int cubeCount() { return cubes.size(); }
    public boolean hasCube(int x, int y, int z) { return cubes.contains(new Msg.Pos(x, y, z)); }

    private boolean applyOffset(Msg.Offset o) {
        if (phase != Phase.CONNECTED) { reject("offset hors connexion"); return false; }
        window = o;
        return true;
    }

    private boolean applyCubes(Msg.Cubes m) {
        if (phase != Phase.CONNECTED || window == null) { reject("cube sans offset"); return false; }
        for (Msg.CubeData c : m.cubes()) {
            cubes.add(c.pos());
            if (c.section().length == 0) emptyRx.incrementAndGet();
        }
        cubesRx.addAndGet(m.cubes().size());
        return true;
    }

    private boolean applyUnload(Msg.Unload m) {
        if (phase != Phase.CONNECTED || window == null) { reject("unload_cube sans offset"); return false; }
        for (Msg.Pos p : m.cubes()) cubes.remove(p);
        unloadRx.addAndGet(m.cubes().size());
        return true;
    }

    private boolean applyHeight(Msg.HeightMap m) {
        if (phase != Phase.CONNECTED || window == null) { reject("heightmap sans offset"); return false; }
        heightRx.incrementAndGet();
        return true;
    }

    private boolean applyManifest(Msg.Manifest m) {
        if (phase == Phase.OFF) { reject("manifest sans hello"); return false; }
        if (m.proto() != Msg.PROTO) { reject("manifest protocole " + m.proto()); return false; }
        List<String> c = new ArrayList<>();
        List<String> ours = ourCaps();
        for (String s : m.caps()) if (ours.contains(s) && !c.contains(s)) c.add(s); // capacité inconnue : ignorée
        List<Msg.KeyDef> k = new ArrayList<>();
        for (Msg.KeyDef d : m.keys()) if (!d.id().isEmpty()) k.add(d);
        proto = m.proto();
        server = m.server();
        caps = List.copyOf(c);
        keys = List.copyOf(k);
        down.clear();
        phase = Phase.CONNECTED;
        return true;
    }

    private boolean applyHud(Msg.Hud h) {
        if (phase != Phase.CONNECTED) { reject("hud hors connexion"); return false; }
        synchronized (hud) {
            for (Msg.HudEntry e : h.entries()) {
                if (e.text().isEmpty()) { hud.remove(e.key()); continue; }
                float f = e.fraction();
                if (Float.isNaN(f) || f == Float.NEGATIVE_INFINITY) f = -1f;
                else if (f == Float.POSITIVE_INFINITY) f = 1f;
                if (!hud.containsKey(e.key()) && hud.size() >= MAX_HUD) continue;
                hud.put(e.key(), new Msg.HudEntry(e.key(), e.text(), f, e.color()));
            }
        }
        return true;
    }

    public List<Msg.HudEntry> hudSnapshot() {
        synchronized (hud) { return hud.isEmpty() ? List.of() : List.copyOf(hud.values()); }
    }

    public boolean wantsKeys() { return phase == Phase.CONNECTED && !keys.isEmpty() && caps.contains("keys"); }

    /** Interroge les touches du manifeste (prédicat sur le code GLFW) et rend les transitions à envoyer. */
    public List<Msg.Key> pollKeys(IntPredicate glfwDown) {
        if (!wantsKeys()) return List.of();
        List<Msg.Key> out = null;
        for (Msg.KeyDef k : keys) {
            boolean d = glfwDown.test(k.glfw());
            Boolean prev = down.get(k.id());
            if (d != (prev != null && prev)) {
                down.put(k.id(), d);
                if (out == null) out = new ArrayList<>(2);
                out.add(new Msg.Key(k.id(), d));
            }
        }
        return out == null ? List.of() : out;
    }

    public void keySent(Msg.Key k) {
        lastKey = k.id() + (k.down() ? ":down" : ":up");
        tx.incrementAndGet();
    }

    public void reject(String why) {
        rejets.incrementAndGet();
        log("rejet : " + why);
    }

    /** Exception interne interceptée : comptée dans « rejets », jamais propagée au jeu. */
    public void error(String where, Throwable t) {
        rejets.incrementAndGet();
        log("erreur " + where + " : " + t);
    }

    private void log(String s) {
        if (logged.incrementAndGet() <= 20) System.err.println("[infinitylink] " + s);
    }

    /** Contrat §3 : state, ver, proto, server, caps, hud, keys, lastKey, rx, tx, rejets, pretouch. */
    public String json() {
        try {
            StringBuilder sb = new StringBuilder(256);
            sb.append("{\"state\":"); str(sb, phase.name());
            sb.append(",\"ver\":"); str(sb, VERSION);
            sb.append(",\"proto\":").append(proto);
            sb.append(",\"server\":"); str(sb, server);
            sb.append(",\"caps\":[");
            List<String> c = caps;
            for (int i = 0; i < c.size(); i++) { if (i > 0) sb.append(','); str(sb, c.get(i)); }
            sb.append("],\"hud\":{");
            List<Msg.HudEntry> h = hudSnapshot();
            for (int i = 0; i < h.size(); i++) {
                Msg.HudEntry e = h.get(i);
                if (i > 0) sb.append(',');
                str(sb, e.key());
                sb.append(":{\"text\":"); str(sb, e.text());
                sb.append(",\"f\":").append(Float.toString(e.fraction()));
                sb.append(",\"color\":\"").append(String.format("%08X", e.color())).append("\"}");
            }
            sb.append("},\"keys\":[");
            List<Msg.KeyDef> k = keys;
            for (int i = 0; i < k.size(); i++) {
                Msg.KeyDef d = k.get(i);
                if (i > 0) sb.append(',');
                sb.append("{\"id\":"); str(sb, d.id());
                sb.append(",\"label\":"); str(sb, d.label());
                sb.append(",\"glfw\":").append(d.glfw()).append('}');
            }
            sb.append("],\"lastKey\":"); str(sb, lastKey);
            sb.append(",\"rx\":").append(rx.get());
            sb.append(",\"tx\":").append(tx.get());
            sb.append(",\"rejets\":").append(rejets.get());
            if (assetsOn()) { sb.append(",\"assets\":"); assets.json(sb); }
            Msg.Offset w = window;
            if (w != null) {
                sb.append(",\"cubes\":{\"base\":").append(w.base()).append(",\"minY\":").append(w.minY())
                  .append(",\"sections\":").append(w.sections()).append(",\"shift\":").append(w.shift())
                  .append(",\"loaded\":").append(cubes.size()).append(",\"rx\":").append(cubesRx.get())
                  .append(",\"empty\":").append(emptyRx.get()).append(",\"unloaded\":").append(unloadRx.get())
                  .append(",\"heightmaps\":").append(heightRx.get()).append('}');
            }
            if (lodView > 0 || lod.size() > 0 || !lod.off.isEmpty()) {
                sb.append(",\"lod\":{\"view\":").append(lodView).append(",\"tiles\":").append(lod.size())
                  .append(",\"rx\":").append(lod.rx.get()).append(",\"forget\":").append(lod.forgets.get())
                  .append(",\"evicted\":").append(lod.evicted.get()).append(",\"meshed\":").append(lod.meshed.get())
                  .append(",\"gpu\":").append(lod.gpuTiles).append(",\"quads\":").append(lod.gpuQuads)
                  .append(",\"bytes\":").append(lod.gpuBytes)
                  .append(",\"reserved\":").append(lod.gpuReserved).append(",\"peak\":").append(lod.gpuPeak)
                  .append(",\"heaps\":").append(lod.gpuHeaps).append(",\"cap\":").append(lod.gpuCap)
                  .append(",\"waiting\":").append(lod.gpuWaiting).append(",\"capHits\":").append(lod.capHits)
                  .append(",\"capEvicted\":").append(lod.capEvicted).append(",\"viewCut\":").append(lod.viewCut).append(",\"drawn\":").append(lod.drawnTiles)
                  .append(",\"real\":").append(lod.realCount()).append(",\"pipeline\":").append(lod.pipelineReady)
                  .append(",\"off\":");
                str(sb, lod.off);
                sb.append('}');
            }
            TabsMsg.Status ts = tabs;
            if (ts.on || ts.mods > 0 || ts.code >= 0) {
                sb.append(",\"tabs\":{\"on\":").append(ts.on).append(",\"mods\":").append(ts.mods)
                  .append(",\"piles\":").append(ts.stacks).append(",\"rejets\":").append(ts.rejected)
                  .append(",\"page\":").append(ts.page).append(",\"pages\":").append(ts.pages)
                  .append(",\"code\":").append(ts.code).append(",\"rx\":").append(ts.rx)
                  .append(",\"ms\":").append(ts.micros / 1000).append(",\"erreurs\":").append(ts.errors).append('}');
            }
            sb.append(",\"blocks\":").append(blocks.json());
            sb.append(",\"pretouch\":").append(pretouch).append('}');
            return sb.toString();
        } catch (Throwable t) {
            return "{\"state\":\"OFF\",\"error\":\"json\"}";
        }
    }

    static void str(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        sb.append('"');
    }
}
