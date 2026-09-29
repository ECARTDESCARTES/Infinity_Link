package infinitylink.core.assets;

import infinitylink.core.AssetsMsg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Côté client de la capacité « assets » (doc 30 §5.3, §9.3), en Java pur : chemins du dossier de dépôt, préchargement
 *  des époques (téléchargement, contrôle de taille et de SHA-1, cache), commandes « /lk », table SGB2 tenue à jour par
 *  blocks_delta. La couche mc (infinitylink.mc.assets) ne fait que brancher le réseau, le fil client et l'écran. */
public final class AssetsClient {
    private AssetsClient() {}

    /** Plafond d'un pack d'époque accepté au préchargement (au-delà : READY_BAD_OFFER). */
    public static final int MAX_PACK = 256 * 1024 * 1024;
    public static final int MAX_FILE_NAME = 64;

    /** Réception côté mc : appelé depuis LinkState (fil réseau ou client) après un décodage strict réussi. */
    public interface Sink {
        void offer(List<AssetsMsg.Epoch> epochs);
        void delta(AssetsMsg.BlocksDelta delta);
        void result(AssetsMsg.Result result);
    }

    // ---------------------------------------------------------------- chemins du dépôt

    /** Fichier {@code name} du dossier de dépôt, jamais ailleurs : un seul composant de nom, sans « .. », sans séparateur,
     *  sans lecteur ni caractère de contrôle, borné à 64 caractères. Le chemin normalisé doit rester sous {@code depot}. */
    public static Path depotFile(Path depot, String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_FILE_NAME) throw new IllegalArgumentException("nom vide ou trop long");
        if (name.equals(".") || name.equals("..") || name.contains("..")) throw new IllegalArgumentException("« .. » interdit");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|')
                throw new IllegalArgumentException("caractere interdit dans « " + name + " »");
        }
        if (name.startsWith(".") || name.endsWith(".") || name.endsWith(" ") || name.startsWith(" "))
            throw new IllegalArgumentException("nom commencant ou finissant par un point ou une espace");
        String base = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        if (base.toUpperCase(Locale.ROOT).matches("CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9]")) throw new IllegalArgumentException("nom reserve");
        Path root = depot.toAbsolutePath().normalize();
        Path p = root.resolve(name).normalize();
        if (!p.getParent().equals(root)) throw new IllegalArgumentException("hors du dossier de depot");
        return p;
    }

    /** Lit un fichier du dépôt (lien symbolique refusé : on ne sort jamais du dossier), borné à MAX_UPLOAD. */
    public static byte[] readDepot(Path depot, String name) throws IOException {
        Path p = depotFile(depot, name);
        if (Files.isSymbolicLink(p)) throw new IOException("lien symbolique refuse : " + name);
        if (!Files.isRegularFile(p)) throw new IOException("fichier absent du depot : " + name);
        Path real = p.toRealPath(), realRoot = depot.toRealPath();
        if (!realRoot.equals(real.getParent())) throw new IOException("hors du dossier de depot : " + name);
        long n = Files.size(p);
        if (n < 1 || n > AssetsMsg.MAX_UPLOAD) throw new IOException("taille " + n + " o hors bornes (1.." + AssetsMsg.MAX_UPLOAD + ")");
        return Files.readAllBytes(p);
    }

    private static final AtomicInteger UPLOADS = new AtomicInteger();

    /** Envoi numéroté (1, 2, ...) : corps upload_begin, upload_part × n, upload_end dans l'ordre d'envoi. */
    public static Upload prepareUpload(Path depot, String name) throws IOException {
        byte[] data = readDepot(depot, name);
        int id = UPLOADS.incrementAndGet();
        return new Upload(id, name, data.length, AssetsMsg.split(id, name, data));
    }

    public record Upload(int id, String name, int size, List<byte[]> bodies) {
        public int parts() { return bodies.size() - 2; }
    }

    // ---------------------------------------------------------------- préchargement

    /** Téléchargement d'une URL, borné à {@code max} octets (la couche mc passe un HttpClient ; les tests, une table). */
    public interface Fetcher { byte[] fetch(String url, int max) throws IOException; }

    public static String hex(byte[] b) { return HexFormat.of().formatHex(b); }

    /** Précharge chaque époque dans {@code cache}/&lt;sha1&gt; (déjà présente et intacte : pas de téléchargement), vérifie
     *  taille et SHA-1. Code : READY_OK (tout est là), READY_BAD_OFFER (URL non http(s) ou pack trop gros),
     *  READY_HASH (au moins une empreinte fausse), READY_DOWNLOAD (au moins un échec réseau) ; la liste ne porte que les
     *  époques effectivement prêtes, en ordre croissant. */
    public static AssetsMsg.Ready preload(List<AssetsMsg.Epoch> epochs, Fetcher fetcher, Path cache) {
        List<Integer> ready = new ArrayList<>();
        int code = AssetsMsg.READY_OK;
        for (AssetsMsg.Epoch e : epochs) {
            String url = e.url().toLowerCase(Locale.ROOT);
            if (!(url.startsWith("http://") || url.startsWith("https://")) || e.size() > MAX_PACK) {
                code = worst(code, AssetsMsg.READY_BAD_OFFER);
                continue;
            }
            Path file = cache.resolve(hex(e.sha1()));
            try {
                if (Files.isRegularFile(file) && Files.size(file) == e.size() && Arrays.equals(AssetsMsg.sha1(Files.readAllBytes(file)), e.sha1())) {
                    ready.add(e.n());
                    continue;
                }
            } catch (IOException ignored) { /* cache illisible : on retélécharge */ }
            byte[] data;
            try {
                data = fetcher.fetch(e.url(), e.size());
            } catch (IOException | RuntimeException x) {
                code = worst(code, AssetsMsg.READY_DOWNLOAD);
                continue;
            }
            if (data == null || data.length != e.size() || !Arrays.equals(AssetsMsg.sha1(data), e.sha1())) {
                code = worst(code, AssetsMsg.READY_HASH);
                continue;
            }
            try {
                Files.createDirectories(cache);
                Path tmp = cache.resolve(hex(e.sha1()) + ".part");
                Files.write(tmp, data);
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                ready.add(e.n());
            } catch (IOException x) {
                code = worst(code, AssetsMsg.READY_DOWNLOAD);
            }
        }
        return new AssetsMsg.Ready(code, ready);
    }

    /** Priorité des codes : offre invalide > empreinte > réseau > ok. */
    private static int worst(int a, int b) {
        int[] rank = {0, 2, 3, 1, 4}; // index = code : OK, DOWNLOAD, HASH, DEFERRED, BAD_OFFER
        return rank[b] > rank[a] ? b : a;
    }

    // ---------------------------------------------------------------- table SGB2 (blocks_delta)

    /** Table des cases du stock côté client, tenue par blocks_delta : dernière époque, en-tête SGB2, SHA-1 du pack. */
    public static final class BlocksTable {
        private final Map<Integer, String> slots = new LinkedHashMap<>();
        private int epoch = -1;
        private byte[] header = new byte[0], packSha1 = new byte[0];

        /** false (et rien n'est changé) si l'époque recule : un delta périmé ne doit pas écraser un plus récent. */
        public synchronized boolean apply(AssetsMsg.BlocksDelta d) {
            if (d.epoch() < epoch) return false;
            epoch = d.epoch();
            header = d.header().clone();
            packSha1 = d.packSha1().clone();
            for (AssetsMsg.Case c : d.cases()) {
                if (c.id().isEmpty()) slots.remove(c.slot()); else slots.put(c.slot(), c.id());
            }
            return true;
        }
        public synchronized String id(int slot) { return slots.get(slot); }
        public synchronized int size() { return slots.size(); }
        public synchronized int epoch() { return epoch; }
        public synchronized byte[] header() { return header.clone(); }
        public synchronized String packSha1() { return hex(packSha1); }
        public synchronized void clear() { slots.clear(); epoch = -1; header = new byte[0]; packSha1 = new byte[0]; }
    }

    // ---------------------------------------------------------------- commandes « /lk »

    /** verb : "envoyer", "editeur", "depot" ou "aide" ; arg : reste de la ligne (peut être vide). */
    public record Command(String verb, String arg) {}

    /** Commande sans la barre oblique ({@code sendCommand}). null si ce n'est pas « lk ... » (elle part au serveur). */
    public static Command parse(String command) {
        if (command == null) return null;
        String c = command.strip();
        if (!(c.equals("lk") || c.startsWith("lk "))) return null;
        String rest = c.substring(2).strip();
        int sp = rest.indexOf(' ');
        String verb = (sp < 0 ? rest : rest.substring(0, sp)).toLowerCase(Locale.ROOT);
        String arg = sp < 0 ? "" : rest.substring(sp + 1).strip();
        return switch (verb) {
            case "envoyer", "editeur", "depot" -> new Command(verb, arg);
            default -> new Command("aide", "");
        };
    }

    public static final String HELP = "/lk envoyer <fichier> (dossier .minecraft/laconia/depot) · /lk editeur [nom.png] · /lk depot";

    public static String describe(AssetsMsg.Result r) {
        String v = r.verdict() == AssetsMsg.ACCEPTED ? "accepte" : "refuse";
        return "envoi " + r.upload() + " " + v + (r.reasons().isEmpty() ? "" : " : " + String.join(" ; ", r.reasons()));
    }
}
