package infinitylink.core;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Primitives du contrat SAGE Link §1 : VarInt (LEB128 32 bits), String (VarInt longueur en octets UTF-8 + octets),
 *  f32 / i32 big-endian, bool (1 octet 0/1). Java pur, aucune classe net.minecraft. */
public final class Wire {
    private Wire() {}

    /** Message malformé (tronqué, compte absurde, chaîne trop longue). Sans pile : c'est une donnée, pas un bug. */
    public static final class DecodeException extends RuntimeException {
        public DecodeException(String m) { super(m, null, false, false); }
    }

    public static final class Out {
        private byte[] buf = new byte[64];
        private int len;

        private void ensure(int n) {
            if (len + n > buf.length) buf = Arrays.copyOf(buf, Math.max(buf.length * 2, len + n));
        }
        public Out u8(int b) { ensure(1); buf[len++] = (byte) b; return this; }
        public Out varInt(int v) {
            while ((v & ~0x7F) != 0) { u8((v & 0x7F) | 0x80); v >>>= 7; }
            return u8(v);
        }
        public Out bytes(byte[] b) { ensure(b.length); System.arraycopy(b, 0, buf, len, b.length); len += b.length; return this; }
        public Out string(String s) { return string(s, Msg.MAX_STR); }
        public Out string(String s, int maxBytes) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            if (b.length > maxBytes) throw new IllegalArgumentException("chaine > " + maxBytes + " octets");
            varInt(b.length);
            return bytes(b);
        }
        public Out i32(int v) { return u8(v >>> 24).u8(v >>> 16).u8(v >>> 8).u8(v); }
        public Out f32(float f) { return i32(Float.floatToIntBits(f)); }
        public Out bool(boolean b) { return u8(b ? 1 : 0); }
        public byte[] toBytes() { return Arrays.copyOf(buf, len); }
    }

    public static final class In {
        private final byte[] b;
        private int pos;

        public In(byte[] b) { this.b = b; }
        public int remaining() { return b.length - pos; }
        public int u8() {
            if (pos >= b.length) throw new DecodeException("fin de message");
            return b[pos++] & 0xFF;
        }
        public int varInt() {
            int v = 0;
            for (int i = 0; i < 5; i++) {
                int x = u8();
                v |= (x & 0x7F) << (7 * i);
                if ((x & 0x80) == 0) return v;
            }
            throw new DecodeException("VarInt > 5 octets");
        }
        public String string(int maxBytes) {
            int n = varInt();
            if (n < 0 || n > remaining() || n > maxBytes) throw new DecodeException("chaine invalide (" + n + ")");
            String s = new String(b, pos, n, StandardCharsets.UTF_8);
            pos += n;
            return s;
        }
        public int i32() { return (u8() << 24) | (u8() << 16) | (u8() << 8) | u8(); }
        public float f32() { return Float.intBitsToFloat(i32()); }
        public boolean bool() {
            int x = u8();
            if (x > 1) throw new DecodeException("bool invalide " + x);
            return x == 1;
        }
        /** Position courante (octets lus). */
        public int position() { return pos; }
        /** Lit n octets bruts. */
        public byte[] bytes(int n) {
            if (n < 0 || n > remaining()) throw new DecodeException("fin de message (" + n + " o)");
            byte[] r = Arrays.copyOfRange(b, pos, pos + n);
            pos += n;
            return r;
        }
        /** Saute n octets. */
        public void skip(int n) {
            if (n < 0 || n > remaining()) throw new DecodeException("fin de message (" + n + " o)");
            pos += n;
        }
        /** Copie des octets [from, position). */
        public byte[] since(int from) { return Arrays.copyOfRange(b, from, pos); }
        /** Compte d'éléments borné par les octets restants (chaque élément en occupe au moins minEntryBytes). */
        public int count(int minEntryBytes) {
            int n = varInt();
            if (n < 0 || (long) n * minEntryBytes > remaining()) throw new DecodeException("compte invalide " + n);
            return n;
        }
    }
}
