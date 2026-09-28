package sage.link.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Capacité « assets » (SAGE-LINK-PROTOCOLE.md §11) : offre d'époques de packs, accusé de préchargement, deltas de la
 *  table de blocs, envoi découpé des fichiers de l'auteur (parts de 30 Kio, empreinte SHA-1 finale), verdict, aperçu de
 *  brouillon. Java pur, mêmes octets de référence que crates/sage_proto/src/link_assets.rs. Tout décodage est strict :
 *  bornes, ordre strict, aucun octet en trop ; une faute lève Wire.DecodeException. */
public final class AssetsMsg {
    private AssetsMsg() {}

    public static final String CAP_ASSETS = "assets";
    public static final String ASSETS_OFFER = "link/assets_offer";
    public static final String ASSETS_READY = "link/assets_ready";
    public static final String BLOCKS_DELTA = "link/blocks_delta";
    public static final String UPLOAD_BEGIN = "link/upload_begin";
    public static final String UPLOAD_PART = "link/upload_part";
    public static final String UPLOAD_END = "link/upload_end";
    public static final String UPLOAD_RESULT = "link/upload_result";
    public static final String DRAFT_PREVIEW = "link/draft_preview";

    public static final int SHA1_LEN = 20;
    public static final int MAX_EPOCHS = 64, MAX_URL = 512, MAX_SGB2_HEADER = 256, MAX_DELTA_CASES = 4096, MAX_CASE = 65_535;
    public static final int MAX_BLOCK_ID = 128, MAX_NAME = 128, MAX_REASONS = 16, MAX_REASON = 256;
    public static final int PART_SIZE = 30 * 1024;
    public static final int MAX_UPLOAD = 2 * 1024 * 1024;
    public static final int MAX_PARTS = (MAX_UPLOAD + PART_SIZE - 1) / PART_SIZE;

    /** Codes de assets_ready. */
    public static final int READY_OK = 0, READY_DOWNLOAD = 1, READY_HASH = 2, READY_DEFERRED = 3, READY_BAD_OFFER = 4;
    public static final int ACCEPTED = 0, REFUSED = 1;

    private static Wire.DecodeException bad(String m) { return new Wire.DecodeException(m); }

    private static int bounded(Wire.In in, int min, int max, String what) {
        int n = in.varInt();
        if (n < min || n > max) throw bad(what + " = " + n + " (bornes " + min + ".." + max + ")");
        return n;
    }

    private static String str(Wire.In in, int min, int max, String what) {
        int n = in.varInt();
        if (n < min || n > max || n > in.remaining()) throw bad(what + " de " + n + " o");
        return new String(in.bytes(n), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void end(Wire.In in, String what) {
        if (in.remaining() != 0) throw bad(what + " : " + in.remaining() + " octet(s) en trop");
    }

    public record Epoch(int n, byte[] sha1, int size, String url) {
        @Override public boolean equals(Object o) {
            return o instanceof Epoch e && e.n == n && Arrays.equals(e.sha1, sha1) && e.size == size && e.url.equals(url);
        }
        @Override public int hashCode() { return n * 31 + Arrays.hashCode(sha1); }
    }

    public static byte[] encodeOffer(List<Epoch> epochs) {
        Wire.Out o = new Wire.Out().varInt(epochs.size());
        for (Epoch e : epochs) o.varInt(e.n()).bytes(e.sha1()).varInt(e.size()).string(e.url(), MAX_URL);
        return o.toBytes();
    }

    public static List<Epoch> decodeOffer(byte[] b) {
        Wire.In in = new Wire.In(b);
        int n = bounded(in, 0, MAX_EPOCHS, "n_epoques");
        List<Epoch> v = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int num = bounded(in, 0, Integer.MAX_VALUE, "epoque");
            if (!v.isEmpty() && num <= v.get(v.size() - 1).n()) throw bad("epoque " + num + " hors ordre");
            byte[] sha = in.bytes(SHA1_LEN);
            int size = bounded(in, 1, Integer.MAX_VALUE, "taille du pack");
            v.add(new Epoch(num, sha, size, str(in, 1, MAX_URL, "url")));
        }
        end(in, "assets_offer");
        return v;
    }

    public record Ready(int code, List<Integer> epochs) {
        public Ready { epochs = List.copyOf(epochs); }
        public byte[] encode() {
            Wire.Out o = new Wire.Out().varInt(code).varInt(epochs.size());
            for (int e : epochs) o.varInt(e);
            return o.toBytes();
        }
        public static Ready decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            int code = bounded(in, 0, READY_BAD_OFFER, "code");
            int n = bounded(in, 0, MAX_EPOCHS, "n_epoques");
            List<Integer> v = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int e = bounded(in, 0, Integer.MAX_VALUE, "epoque");
                if (!v.isEmpty() && e <= v.get(v.size() - 1)) throw bad("epoque " + e + " hors ordre");
                v.add(e);
            }
            end(in, "assets_ready");
            return new Ready(code, v);
        }
    }

    /** Case du stock : id vide = case libérée. */
    public record Case(int slot, String id) {}

    public record BlocksDelta(int epoch, byte[] packSha1, byte[] header, List<Case> cases) {
        public BlocksDelta { cases = List.copyOf(cases); }
        public byte[] encode() {
            Wire.Out o = new Wire.Out().varInt(epoch).bytes(packSha1).varInt(header.length).bytes(header).varInt(cases.size());
            for (Case c : cases) o.varInt(c.slot()).string(c.id(), MAX_BLOCK_ID);
            return o.toBytes();
        }
        public static BlocksDelta decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            int epoch = bounded(in, 0, Integer.MAX_VALUE, "epoque");
            byte[] sha = in.bytes(SHA1_LEN);
            byte[] header = in.bytes(bounded(in, 1, MAX_SGB2_HEADER, "en-tete SGB2"));
            int n = bounded(in, 0, MAX_DELTA_CASES, "n_cases");
            List<Case> v = new ArrayList<>(Math.min(n, in.remaining() / 2));
            for (int i = 0; i < n; i++) {
                int slot = bounded(in, 0, MAX_CASE, "case");
                if (!v.isEmpty() && slot <= v.get(v.size() - 1).slot()) throw bad("case " + slot + " hors ordre");
                v.add(new Case(slot, str(in, 0, MAX_BLOCK_ID, "id de bloc")));
            }
            end(in, "blocks_delta");
            return new BlocksDelta(epoch, sha, header, v);
        }
    }

    public record Begin(int upload, String name, int size, int parts) {
        public byte[] encode() { return new Wire.Out().varInt(upload).string(name, MAX_NAME).varInt(size).varInt(parts).toBytes(); }
        public static Begin decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            Begin v = new Begin(bounded(in, 1, Integer.MAX_VALUE, "envoi"), str(in, 1, MAX_NAME, "nom"),
                bounded(in, 1, MAX_UPLOAD, "taille"), bounded(in, 1, MAX_PARTS, "parts"));
            end(in, "upload_begin");
            return v;
        }
    }

    public record Part(int upload, int index, byte[] data) {
        public byte[] encode() { return new Wire.Out().varInt(upload).varInt(index).bytes(data).toBytes(); }
        public static Part decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            int upload = bounded(in, 1, Integer.MAX_VALUE, "envoi");
            int index = bounded(in, 0, MAX_PARTS - 1, "index");
            int n = in.remaining();
            if (n < 1 || n > PART_SIZE) throw bad("part de " + n + " o");
            return new Part(upload, index, in.bytes(n));
        }
    }

    public record End(int upload, byte[] sha1) {
        public byte[] encode() { return new Wire.Out().varInt(upload).bytes(sha1).toBytes(); }
        public static End decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            End v = new End(bounded(in, 1, Integer.MAX_VALUE, "envoi"), in.bytes(SHA1_LEN));
            end(in, "upload_end");
            return v;
        }
    }

    public record Result(int upload, int verdict, List<String> reasons) {
        public Result { reasons = List.copyOf(reasons); }
        public byte[] encode() {
            Wire.Out o = new Wire.Out().varInt(upload).u8(verdict).varInt(reasons.size());
            for (String r : reasons) o.string(r, MAX_REASON);
            return o.toBytes();
        }
        public static Result decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            int upload = bounded(in, 1, Integer.MAX_VALUE, "envoi");
            int verdict = in.u8();
            if (verdict > REFUSED) throw bad("verdict " + verdict);
            int n = bounded(in, 0, MAX_REASONS, "n_motifs");
            List<String> v = new ArrayList<>(n);
            for (int i = 0; i < n; i++) v.add(str(in, 1, MAX_REASON, "motif"));
            end(in, "upload_result");
            return new Result(upload, verdict, v);
        }
    }

    /** sha1 == null : retirer le pack brouillon ; sinon l'appliquer. */
    public record Draft(byte[] sha1, int size, String url) {
        public static final Draft REMOVE = new Draft(null, 0, "");
        public byte[] encode() {
            if (sha1 == null) return new byte[] {0};
            return new Wire.Out().u8(1).bytes(sha1).varInt(size).string(url, MAX_URL).toBytes();
        }
        public static Draft decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            int a = in.u8();
            Draft d;
            if (a == 0) d = REMOVE;
            else if (a == 1) d = new Draft(in.bytes(SHA1_LEN), bounded(in, 1, Integer.MAX_VALUE, "taille"), str(in, 1, MAX_URL, "url"));
            else throw bad("action d'apercu " + a);
            end(in, "draft_preview");
            return d;
        }
    }

    public static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Découpe un fichier en corps upload_begin, upload_part × n, upload_end (dans l'ordre d'envoi). */
    public static List<byte[]> split(int upload, String name, byte[] data) {
        if (data.length < 1 || data.length > MAX_UPLOAD) throw new IllegalArgumentException("taille " + data.length);
        int parts = (data.length + PART_SIZE - 1) / PART_SIZE;
        List<byte[]> out = new ArrayList<>(parts + 2);
        out.add(new Begin(upload, name, data.length, parts).encode());
        for (int i = 0; i < parts; i++) {
            int from = i * PART_SIZE;
            out.add(new Part(upload, i, Arrays.copyOfRange(data, from, Math.min(data.length, from + PART_SIZE))).encode());
        }
        out.add(new End(upload, sha1(data)).encode());
        return out;
    }

    /** Observation (§3, section « assets ») : dernière offre, dernier delta, dernier verdict, aperçu. */
    public static final class Status {
        public volatile int offers, deltas, results, drafts, lastEpoch = -1, lastVerdict = -1;
        public volatile boolean draftOn;
        public void clear() { offers = deltas = results = drafts = 0; lastEpoch = -1; lastVerdict = -1; draftOn = false; }
        public void json(StringBuilder sb) {
            sb.append("{\"offers\":").append(offers).append(",\"deltas\":").append(deltas).append(",\"results\":").append(results)
              .append(",\"drafts\":").append(drafts).append(",\"epoch\":").append(lastEpoch).append(",\"verdict\":").append(lastVerdict)
              .append(",\"draft\":").append(draftOn).append('}');
        }
    }
}
