package infinitylink.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Capacité « tabs » (ONGLETS-CREATIFS-SPEC.md §5) : sage:link/tabs (serveur -> client, jeu) et
 *  sage:link/tabs_ready (client -> serveur, jeu). Java pur, aucune classe net.minecraft : les piles restent
 *  des octets (ItemStack encodage fiable 26.3) que la couche mc décode avec l'accès aux registres de la connexion. */
public final class TabsMsg {
    private TabsMsg() {}

    public static final String TABS = "link/tabs";
    public static final String TABS_READY = "link/tabs_ready";
    public static final String CAP_TABS = "tabs";
    public static final int PROTO_TABS = 1;
    public static final int MAX_TABS = 128;
    public static final int MAX_RAW = 8_388_608;
    public static final int MAX_PER_MOD = 8192;
    public static final int MAX_STACK = 65_535;
    public static final int MAX_ID = 64, MAX_NAME = 128, MAX_KEY = 128;

    /** Codes de tabs_ready (§5.2). */
    public static final int OK = 0, BAD_HEADER = 1, BAD_INFLATE = 2, BAD_BODY = 3, NOT_REGISTERED = 4;

    /** Trame refusée ; code = 1 en-tête, 2 décompression, 3 corps. Sans pile : c'est une donnée, pas un bug. */
    public static final class Bad extends IllegalArgumentException {
        public final int code;
        public Bad(int code, String m) { super(m, null); this.code = code; }
        @Override public synchronized Throwable fillInStackTrace() { return this; }
    }

    private static Bad bad(int code, String m) { return new Bad(code, m); }

    public record ModTab(String id, String name, String key, int icon, List<byte[]> stacks) {
        public ModTab { stacks = List.copyOf(stacks); }
    }

    public record Catalog(List<ModTab> mods) {
        public Catalog { mods = List.copyOf(mods); }
        public int stackCount() { int n = 0; for (ModTab m : mods) n += m.stacks().size(); return n; }
    }

    /** Décode une trame sage:link/tabs complète (en-tête, zlib borné à taille_brute, corps). Lève Bad(code). */
    public static Catalog decode(byte[] frame) {
        Wire.In in = new Wire.In(frame);
        int proto, codage, raw;
        try {
            proto = in.varInt();
            codage = in.u8();
            raw = in.varInt();
        } catch (Wire.DecodeException e) {
            throw bad(BAD_HEADER, "en-tete tronque");
        }
        if (proto != PROTO_TABS) throw bad(BAD_HEADER, "proto_onglets " + proto);
        if (codage != 0 && codage != 1) throw bad(BAD_HEADER, "codage " + codage);
        if (raw <= 0 || raw > MAX_RAW) throw bad(BAD_HEADER, "taille_brute " + Integer.toUnsignedString(raw));
        int off = in.position();
        byte[] body;
        if (codage == 0) {
            if (frame.length - off != raw) throw bad(BAD_HEADER, "corps brut de " + (frame.length - off) + " o, annonce " + raw);
            body = Arrays.copyOfRange(frame, off, frame.length);
        } else {
            body = inflate(frame, off, raw);
        }
        return decodeBody(body);
    }

    /** zlib (RFC 1950) -> exactement raw octets ; plus, moins, octets après la fin du flux : code 2.
     *  Allocation bornée à raw (≤ MAX_RAW) : une bombe zlib est coupée à la borne. */
    static byte[] inflate(byte[] frame, int off, int raw) {
        Inflater inf = new Inflater();
        try {
            inf.setInput(frame, off, frame.length - off);
            byte[] out = new byte[raw];
            int n = 0;
            while (n < raw) {
                int k = inf.inflate(out, n, raw - n);
                if (k == 0) {
                    if (inf.finished() || inf.needsInput() || inf.needsDictionary()) break;
                }
                n += k;
            }
            if (n != raw) throw bad(BAD_INFLATE, "zlib : " + n + " o decodes sur " + raw);
            if (!inf.finished()) {
                byte[] one = new byte[1];
                if (inf.inflate(one) > 0 || !inf.finished()) throw bad(BAD_INFLATE, "zlib : flux au-dela de taille_brute");
            }
            if (inf.getRemaining() != 0) throw bad(BAD_INFLATE, "zlib : " + inf.getRemaining() + " o apres la fin du flux");
            return out;
        } catch (DataFormatException e) {
            throw bad(BAD_INFLATE, "zlib invalide : " + e.getMessage());
        } finally {
            inf.end();
        }
    }

    static boolean validId(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-')) return false;
        }
        return true;
    }

    /** Corps décodé (§5.2) ; toute borne violée, trame tronquée ou octet en trop : code 3. */
    public static Catalog decodeBody(byte[] body) {
        try {
            Wire.In in = new Wire.In(body);
            int n = in.varInt();
            if (n < 0 || n > MAX_TABS) throw bad(BAD_BODY, "n_mods " + n);
            String[] id = new String[n], name = new String[n], key = new String[n];
            int[] icon = new int[n], count = new int[n];
            for (int i = 0; i < n; i++) {
                id[i] = in.string(MAX_ID);
                if (!validId(id[i])) throw bad(BAD_BODY, "id de mod invalide");
                name[i] = in.string(MAX_NAME);
                if (name[i].isEmpty()) throw bad(BAD_BODY, "nom vide");
                key[i] = in.string(MAX_KEY);
                icon[i] = in.varInt();
                count[i] = in.varInt();
                if (count[i] < 1 || count[i] > MAX_PER_MOD) throw bad(BAD_BODY, "n_contenus " + count[i]);
                if (icon[i] < 0 || icon[i] >= count[i]) throw bad(BAD_BODY, "icone " + icon[i] + " / " + count[i]);
            }
            List<ModTab> mods = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                List<byte[]> stacks = new ArrayList<>(Math.min(count[i], in.remaining() / 2 + 1));
                for (int j = 0; j < count[i]; j++) {
                    int len = in.varInt();
                    if (len < 1 || len > MAX_STACK) throw bad(BAD_BODY, "longueur de pile " + len);
                    stacks.add(in.bytes(len));
                }
                mods.add(new ModTab(id[i], name[i], key[i], icon[i], stacks));
            }
            if (in.remaining() != 0) throw bad(BAD_BODY, in.remaining() + " o en trop");
            return new Catalog(mods);
        } catch (Wire.DecodeException e) {
            throw bad(BAD_BODY, "corps : " + e.getMessage());
        }
    }

    public static byte[] encodeBody(Catalog c) {
        Wire.Out o = new Wire.Out().varInt(c.mods().size());
        for (ModTab m : c.mods()) {
            o.string(m.id(), MAX_ID).string(m.name(), MAX_NAME).string(m.key(), MAX_KEY).varInt(m.icon()).varInt(m.stacks().size());
        }
        for (ModTab m : c.mods()) for (byte[] s : m.stacks()) o.varInt(s.length).bytes(s);
        return o.toBytes();
    }

    /** Trame complète (tests et banc) ; codage 0 = brut, 1 = zlib niveau 6. */
    public static byte[] encode(Catalog c, int codage) {
        byte[] body = encodeBody(c);
        byte[] payload = body;
        if (codage == 1) {
            Deflater d = new Deflater(6);
            try {
                d.setInput(body);
                d.finish();
                byte[] buf = new byte[Math.max(64, body.length + body.length / 100 + 64)];
                int n = 0;
                while (!d.finished()) {
                    if (n == buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
                    n += d.deflate(buf, n, buf.length - n);
                }
                payload = Arrays.copyOf(buf, n);
            } finally {
                d.end();
            }
        } else if (codage != 0) {
            throw new IllegalArgumentException("codage " + codage);
        }
        return new Wire.Out().varInt(PROTO_TABS).u8(codage).varInt(body.length).bytes(payload).toBytes();
    }

    /** sage:link/tabs_ready : 4 VarInt (code, mods, piles, rejetées). Octets en trop tolérés (évolution). */
    public record Ready(int code, int mods, int stacks, int rejected) {
        public byte[] encode() { return new Wire.Out().varInt(code).varInt(mods).varInt(stacks).varInt(rejected).toBytes(); }
        public static Ready decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            return new Ready(in.varInt(), in.varInt(), in.varInt(), in.varInt());
        }
    }

    /** Reçoit, sur le fil réseau, le catalogue décodé ou le code d'échec ; la couche mc l'applique sur le fil client. */
    public interface Sink {
        void catalog(Catalog c);
        void failed(int code);
    }

    /** Oracle d'état (§4.6) : écrit par la couche mc, lu par le JSON infinitylink. */
    public static final class Status {
        /** Onglets enregistrés au boot (fait de démarrage, jamais remis à zéro). */
        public volatile boolean on;
        public volatile int mods, stacks, rejected, page, pages = 1, code = -1, errors;
        public volatile long rx, micros;

        void clear() {
            mods = 0; stacks = 0; rejected = 0; page = 0; pages = 1; code = -1; errors = 0; rx = 0; micros = 0;
        }
    }
}
