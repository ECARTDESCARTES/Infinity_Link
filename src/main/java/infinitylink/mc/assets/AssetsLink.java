package infinitylink.mc.assets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import infinitylink.core.AssetsMsg;
import infinitylink.core.assets.AssetsClient;
import infinitylink.mc.Bridge;
import infinitylink.mc.SagePayload;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/** Couche mc de la capacité « assets » : préchargement en arrière-plan puis assets_ready, table SGB2 par blocks_delta,
 *  verdicts affichés dans le chat, commandes « /lk » (envoi depuis .minecraft/laconia/depot, éditeur 16×16). */
public final class AssetsLink implements AssetsClient.Sink {
    public static final AssetsLink INSTANCE = new AssetsLink();
    public final AssetsClient.BlocksTable table = new AssetsClient.BlocksTable();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "infinitylink-assets");
        t.setDaemon(true);
        return t;
    });
    private volatile HttpClient http;

    private AssetsLink() {}

    public static Path gameDir() { return Minecraft.getInstance().gameDirectory.toPath(); }
    public static Path depot() { return gameDir().resolve("laconia").resolve("depot"); }
    public static Path cache() { return gameDir().resolve("infinitylink").resolve("cache"); }

    static void chat(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try { mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[InfinityLink] " + text)); }
            catch (Throwable t) { Bridge.STATE.error("chat assets", t); }
        });
    }

    /** Envoie un corps sage:path sur le fil client ; false si pas de connexion de jeu. */
    static boolean send(String path, byte[] body) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return false;
        conn.send(new ServerboundCustomPayloadPacket(SagePayload.out(path, body)));
        Bridge.STATE.sent();
        return true;
    }

    // ---------------------------------------------------------------- Sink

    @Override public void offer(List<AssetsMsg.Epoch> epochs) {
        worker.execute(() -> {
            try {
                AssetsMsg.Ready r = AssetsClient.preload(epochs, this::fetch, cache());
                System.out.println("[infinitylink] assets_offer : " + epochs.size() + " epoque(s), pretes " + r.epochs() + ", code " + r.code());
                Minecraft.getInstance().execute(() -> {
                    try { send(AssetsMsg.ASSETS_READY, r.encode()); } catch (Throwable t) { Bridge.STATE.error("assets_ready", t); }
                });
            } catch (Throwable t) {
                Bridge.STATE.error("prechargement", t);
            }
        });
    }

    @Override public void delta(AssetsMsg.BlocksDelta d) {
        boolean applied = table.apply(d);
        // Consigné : la table SGB2 est tenue ici ; son application au stock de blocs du client (SageStock) n'est pas
        // faite dans cette tranche (voir README, « Assets »).
        System.out.println("[infinitylink] blocks_delta epoque " + d.epoch() + " : " + d.cases().size() + " case(s), "
            + (applied ? "appliquee" : "ignoree (epoque perimee)") + ", table " + table.size() + " case(s), pack " + table.packSha1());
    }

    @Override public void result(AssetsMsg.Result r) {
        chat(AssetsClient.describe(r));
    }

    private byte[] fetch(String url, int max) throws IOException {
        HttpClient c = http;
        if (c == null) http = c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build();
        try {
            HttpResponse<InputStream> resp = c.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = resp.body()) {
                if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode());
                byte[] b = in.readNBytes(max + 1);
                if (b.length > max) throw new IOException("pack plus grand qu'annonce");
                return b;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    // ---------------------------------------------------------------- commandes

    /** Tête de ClientPacketListener.sendCommand : true = commande « lk » traitée ici (ne part pas au serveur). */
    public static boolean onCommand(String command) {
        AssetsClient.Command c = AssetsClient.parse(command);
        if (c == null) return false;
        try {
            switch (c.verb()) {
                case "envoyer" -> upload(c.arg());
                case "editeur" -> {
                    String name = c.arg().isEmpty() ? "dessin.png" : c.arg();
                    AssetsClient.depotFile(depot(), name); // valide le nom avant d'ouvrir
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.gui.setScreen(new EditorScreen(name)));
                }
                case "depot" -> {
                    Files.createDirectories(depot());
                    try (Stream<Path> s = Files.list(depot())) {
                        List<String> names = s.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).sorted().toList();
                        chat("depot (" + names.size() + ") : " + (names.isEmpty() ? "vide" : String.join(", ", names)));
                    }
                }
                default -> chat(AssetsClient.HELP);
            }
        } catch (IllegalArgumentException | IOException e) {
            chat("refuse : " + e.getMessage());
        } catch (Throwable t) {
            Bridge.STATE.error("commande lk", t);
        }
        return true;
    }

    /** Lit le fichier du dépôt, le découpe (≤ 30 Kio par part) et l'envoie ; le verdict arrive par upload_result. */
    public static void upload(String name) throws IOException {
        if (name.isEmpty()) { chat(AssetsClient.HELP); return; }
        if (!Bridge.STATE.assetsOn()) { chat("capacite assets absente sur ce serveur : envoi impossible"); return; }
        Files.createDirectories(depot());
        AssetsClient.Upload u = AssetsClient.prepareUpload(depot(), name);
        int i = 0;
        for (byte[] b : u.bodies()) {
            String path = i == 0 ? AssetsMsg.UPLOAD_BEGIN : i == u.bodies().size() - 1 ? AssetsMsg.UPLOAD_END : AssetsMsg.UPLOAD_PART;
            if (!send(path, b)) { chat("pas de connexion de jeu"); return; }
            i++;
        }
        chat("envoi " + u.id() + " : " + name + " (" + u.size() + " o, " + u.parts() + " part(s)) ; verdict attendu");
    }
}
