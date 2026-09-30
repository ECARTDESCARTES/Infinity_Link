package infinitylink.core.scenes;

import java.nio.charset.StandardCharsets;

/** Lecture stricte des types du contrat « maillages » (DOCS/INFINITYLINK-MAILLAGES-PROTOCOLE.md §3) : varint LEB128
 *  non signé de 5 octets au plus et de valeur ≤ 2^31 − 1, chaînes bornées vérifiées AVANT d'allouer, f32 finis,
 *  en-têtes gros-boutistes. Java pur ; toute violation lève {@link Refus} (une donnée, pas un bogue : sans pile). */
public final class Lecteur {
    /** Donnée refusée par le contrat : l'objet qui la porte est rejeté et compté, jamais de plantage. */
    public static final class Refus extends RuntimeException {
        public Refus(String m) { super(m, null, false, false); }
    }

    private final byte[] b;
    private int p;
    private final int fin;

    public Lecteur(byte[] b) { this(b, 0, b.length); }
    public Lecteur(byte[] b, int debut, int fin) { this.b = b; this.p = debut; this.fin = fin; }

    public int position() { return p; }
    public int reste() { return fin - p; }
    public boolean fini() { return p == fin; }
    public void finir(String quoi) { if (p != fin) throw new Refus(quoi + " : " + (fin - p) + " octets en trop"); }

    private void besoin(int n) { if (n < 0 || fin - p < n) throw new Refus("tronqué : " + n + " octets demandés, " + (fin - p) + " restants"); }

    public int u8() { besoin(1); return b[p++] & 0xFF; }
    public int i8() { besoin(1); return b[p++]; }

    public int u16() { besoin(2); int v = (b[p] & 0xFF) << 8 | (b[p + 1] & 0xFF); p += 2; return v; }

    public int i32() { besoin(4); int v = (b[p] & 0xFF) << 24 | (b[p + 1] & 0xFF) << 16 | (b[p + 2] & 0xFF) << 8 | (b[p + 3] & 0xFF); p += 4; return v; }

    /** f32 gros-boutiste, fini (ni NaN ni infini), sinon refus. */
    public float f32(String quoi) {
        float f = Float.intBitsToFloat(i32());
        if (!Float.isFinite(f)) throw new Refus(quoi + " : flottant non fini");
        return f;
    }

    /** varint LEB128 non signé : 5 octets au plus, 5e octet ≤ 0x07 (valeur ≤ 2^31 − 1) ; zéros de tête tolérés. */
    public int varint() {
        int v = 0;
        for (int k = 0; k < 5; k++) {
            int x = u8();
            if (k == 4 && x > 0x07) throw new Refus("varint hors de 2^31 − 1");
            v |= (x & 0x7F) << (7 * k);
            if ((x & 0x80) == 0) return v;
        }
        throw new Refus("varint de plus de 5 octets");
    }

    /** varint borné : refus si < min ou > max. */
    public int varint(String quoi, int min, int max) {
        int v = varint();
        if (v < min || v > max) throw new Refus(quoi + " = " + v + " hors de [" + min + ", " + max + "]");
        return v;
    }

    /** Chaîne UTF-8 de `max` octets au plus (longueur vérifiée avant d'allouer). */
    public String string(String quoi, int max) {
        int n = varint();
        if (n > max) throw new Refus(quoi + " de " + n + " octets (plus de " + max + ")");
        besoin(n);
        String s = new String(b, p, n, StandardCharsets.UTF_8);
        p += n;
        return s;
    }

    public byte[] octets(int n) {
        besoin(n);
        byte[] o = new byte[n];
        System.arraycopy(b, p, o, 0, n);
        p += n;
        return o;
    }

    /** Saute `n` octets (bloc déjà validé ailleurs), rend leur début. */
    public int sauter(int n) { besoin(n); int d = p; p += n; return d; }

    public byte[] source() { return b; }
}
