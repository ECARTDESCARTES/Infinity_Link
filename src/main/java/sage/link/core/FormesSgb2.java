package sage.link.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Capacité « formes » (Infinity_Link 1.1.0, lot Ph4g-link-formes) : décodage Java pur de la table SGB2 complète
 * reçue sur sage:link/formes (DOCS/BLOCS-SPEC.md §3.5, §5.1 ; encodeur crates/laconia_pack/src/blocs.rs) et
 * résolution par état : boîtes de contour et de collision déjà tournées (champ rot, bits 0-1 : quarts de tour horaires
 * autour de Y vus d'en haut, nord → est → sud → ouest), en blocs (1/4096 → double), drapeaux 1 ciel, 2 sans collision,
 * 4 grimpable. Les états absents de la table restent des cubes pleins.
 */
public final class FormesSgb2 {
    private FormesSgb2() {}

    public static final String CAP_FORMES = "formes";
    public static final String FORMES = "link/formes";
    public static final int UNITE = 4096, MAX_FORMES = 4096, MAX_MATERIAUX = 8192, MAX_BOITES = 32, COLLISION_IDENTIQUE = 255;
    public static final int CIEL = 1, SANS_COLLISION = 2, GRIMPABLE = 4;

    /** Boîtes en 1/4096 : {x0, y0, z0, x1, y1, z1} par boîte. */
    public record Forme(String nom, short[][] boites, short[][] collision, int opacite, int drapeaux) {}
    public record Etat(int id, int materiau, int forme, int rot) {}
    public record Table(int version, int base, int stock, List<Forme> formes, int materiaux, List<Etat> etats) {
        /** Boîtes d'un état tournées, en blocs ; collision = false : contour. */
        public double[][] boites(Etat e, boolean collision) {
            Forme f = formes.get(e.forme());
            short[][] b = collision ? f.collision() : f.boites();
            double[][] out = new double[b.length][];
            for (int i = 0; i < b.length; i++) {
                short[] r = tourner(b[i], e.rot() & 3);
                out[i] = new double[6];
                for (int k = 0; k < 6; k++) out[i][k] = r[k] / (double) UNITE;
            }
            return out;
        }
        public int drapeaux(Etat e) { return formes.get(e.forme()).drapeaux(); }
        public String nomForme(Etat e) { return formes.get(e.forme()).nom(); }
    }

    /** Quart de tour horaire, q fois : (x, z) → (1 − z, x) (même règle que blocs_formes.rs::tourner_y). */
    public static short[] tourner(short[] b, int q) {
        short[] r = b.clone();
        for (int i = 0; i < (q & 3); i++) {
            short x0 = r[0], z0 = r[2], x1 = r[3], z1 = r[5];
            r = new short[]{(short) (UNITE - z1), r[1], x0, (short) (UNITE - z0), r[4], x1};
        }
        return r;
    }

    private static final class In {
        final byte[] b; int i;
        In(byte[] b) { this.b = b; }
        int u8() { if (i >= b.length) throw new Wire.DecodeException("SGB2 tronque"); return b[i++] & 0xFF; }
        int u16() { return (u8() << 8) | u8(); }
        short i16() { return (short) u16(); }
        int varint() {
            int v = 0;
            for (int s = 0; s < 35; s += 7) { int x = u8(); v |= (x & 0x7F) << s; if ((x & 0x80) == 0) return v; }
            throw new Wire.DecodeException("varint trop long");
        }
        int borne(int max, String quoi) { int n = varint(); if (n < 0 || n > max) throw new Wire.DecodeException(quoi + " hors borne : " + n); return n; }
        String str(int max) { int n = borne(max * 4, "chaine"); if (i + n > b.length) throw new Wire.DecodeException("SGB2 tronque"); String s = new String(b, i, n, StandardCharsets.UTF_8); i += n; return s; }
        short[][] boites(int n) {
            if (n > MAX_BOITES) throw new Wire.DecodeException("boites : " + n);
            short[][] r = new short[n][6];
            for (short[] x : r) { for (int k = 0; k < 6; k++) x[k] = i16(); if (x[0] > x[3] || x[1] > x[4] || x[2] > x[5]) throw new Wire.DecodeException("bornes inversees"); }
            return r;
        }
    }

    public static Table decode(byte[] data) {
        In in = new In(data);
        if (data.length < 4 || data[0] != 'S' || data[1] != 'G' || data[2] != 'B' || data[3] != '2') throw new Wire.DecodeException("magic SGB2");
        in.i = 4;
        int version = in.varint(), base = in.varint(), stock = in.varint();
        in.u8(); // bits
        int nf = in.borne(MAX_FORMES, "formes");
        List<Forme> formes = new ArrayList<>(nf);
        for (int f = 0; f < nf; f++) {
            String nom = in.str(48);
            short[][] b = in.boites(in.u8());
            int nc = in.u8();
            short[][] c = nc == COLLISION_IDENTIQUE ? b : in.boites(nc);
            int op = in.u8(), dr = in.u8();
            if ((dr & SANS_COLLISION) != 0) c = new short[0][];
            formes.add(new Forme(nom, b, c, op, dr));
        }
        int nm = in.borne(MAX_MATERIAUX, "materiaux");
        for (int m = 0; m < nm; m++) {
            in.str(64); in.str(64);
            int teinte = in.u8();
            if (teinte == 4) { in.u8(); in.u8(); in.u8(); }
            in.varint(); in.u8(); in.u8(); in.u8();
            in.str(128); in.str(128);
            in.u16(); in.u16(); in.u8();
        }
        int ne = in.borne(stock, "etats");
        List<Etat> etats = new ArrayList<>(ne);
        int prec = -1;
        for (int e = 0; e < ne; e++) {
            int id = in.varint(), mat = in.varint(), forme = in.varint(), rot = in.u8();
            if (id < base || id >= base + stock || id <= prec) throw new Wire.DecodeException("etat " + id + " hors stock ou desordre");
            if (mat >= nm || forme >= nf || (rot & ~0x0F) != 0) throw new Wire.DecodeException("etat " + id + " : references invalides");
            prec = id;
            etats.add(new Etat(id, mat, forme, rot));
        }
        if (in.i != data.length) throw new Wire.DecodeException("octets en trop apres SGB2");
        return new Table(version, base, stock, List.copyOf(formes), nm, List.copyOf(etats));
    }

    /** Couche mc : applique (ou oublie) la table sur les blocs de réserve. */
    public interface Sink { void apply(Table t); void clear(); }
}
