package infinitylink.core.scenes;

import infinitylink.core.scenes.Contrat.Cle;

/** Culling hiérarchique et choix du niveau par instance (contrat §8.3, §8.4), Java pur, sans allocation par instance.
 *  Repère : monde relatif à la caméra (la caméra à l'origine), en double. Matrices affines 3×4 en lignes
 *  (m00 m01 m02 tx · m10 m11 m12 ty · m20 m21 m22 tz). Tronc : les 4 plans latéraux de clip = projection × rotation de
 *  la vue (indépendants de la convention de profondeur, inversée en 26.3) ; derrière la caméra, rien ne passe. */
public final class Selection {
    private Selection() {}

    /** Réglages d'une image. pxParBloc1 = H / (2 tan(fov/2)) : pixels couverts par 1 bloc à 1 bloc de distance. */
    public static final class Parametres {
        public double pxParBloc1 = 1000, seuilPx = 2, distanceMax = 512, pxMin = 0.75;
        /** Instances testées pendant l'image (toutes ancres confondues), et plafond : au-delà, le parcours s'arrête. */
        public long tests, testsMax = Long.MAX_VALUE;
    }

    /** Ressources résidentes, et demande de celles qui manquent. */
    public interface Ressources {
        boolean prete(Cle k);
        void vouloir(Cle k);
    }

    /** Une instance à dessiner : matrice composée (monde relatif à la caméra), distance, boîte (relative à la caméra). */
    public interface Sortie {
        void dessiner(int instance, int cellule, int maillage, int niveau, double[] m, double distance, double[] boite);
    }

    /** Les 4 plans latéraux (a, b, c, d) de la matrice de clip, colonne majeure (JOML) : x' = Σ m[4c + r] … */
    public static double[] plans(float[] clip) {
        double[] p = new double[16];
        for (int k = 0; k < 4; k++) {
            int ligne = k / 2;               // 0 : x, 1 : y
            double signe = (k & 1) == 0 ? 1 : -1;
            for (int c = 0; c < 4; c++) p[4 * k + c] = clip[4 * c + 3] + signe * clip[4 * c + ligne];
        }
        return p;
    }

    /** Boîte (min x, y, z, max x, y, z) hors d'au moins un plan : false. */
    public static boolean visible(double[] plans, double[] b) {
        for (int k = 0; k < 16; k += 4) {
            double a = plans[k], bb = plans[k + 1], c = plans[k + 2], d = plans[k + 3];
            double x = a >= 0 ? b[3] : b[0], y = bb >= 0 ? b[4] : b[1], z = c >= 0 ? b[5] : b[2];
            if (a * x + bb * y + c * z + d < 0) return false;
        }
        return true;
    }

    /** Boîte de b[o .. o+6] (min puis max) transformée par l'affine e : centre et demi-étendue (|M| × h). */
    public static void boite(double[] e, float[] b, int o, double[] out) {
        double cx = (b[o] + b[o + 3]) * 0.5, cy = (b[o + 1] + b[o + 4]) * 0.5, cz = (b[o + 2] + b[o + 5]) * 0.5;
        double hx = (b[o + 3] - b[o]) * 0.5, hy = (b[o + 4] - b[o + 1]) * 0.5, hz = (b[o + 5] - b[o + 2]) * 0.5;
        for (int r = 0; r < 3; r++) {
            double c = e[4 * r] * cx + e[4 * r + 1] * cy + e[4 * r + 2] * cz + e[4 * r + 3];
            double h = Math.abs(e[4 * r]) * hx + Math.abs(e[4 * r + 1]) * hy + Math.abs(e[4 * r + 2]) * hz;
            out[r] = c - h;
            out[r + 3] = c + h;
        }
    }

    /** Distance de l'origine (la caméra) à la boîte ; 0 dedans. */
    public static double distance(double[] b) {
        double dx = b[0] > 0 ? b[0] : b[3] < 0 ? -b[3] : 0;
        double dy = b[1] > 0 ? b[1] : b[4] < 0 ? -b[4] : 0;
        double dz = b[2] > 0 ? b[2] : b[5] < 0 ? -b[5] : 0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Plus grand facteur d'échelle (norme de colonne maximale de la partie 3×3). */
    public static double echelle(double[] m) {
        double s = 0;
        for (int c = 0; c < 3; c++) s = Math.max(s, Math.sqrt(m[c] * m[c] + m[4 + c] * m[4 + c] + m[8 + c] * m[8 + c]));
        return s;
    }

    public static double determinant(double[] m) {
        return m[0] * (m[5] * m[10] - m[6] * m[9]) - m[1] * (m[4] * m[10] - m[6] * m[8]) + m[2] * (m[4] * m[9] - m[5] * m[8]);
    }

    /** out = a × b (affines 3×4 en lignes), b pris dans bf[o .. o+12] (f32 de la description). */
    public static void composer(double[] a, float[] bf, int o, double[] out) {
        for (int r = 0; r < 3; r++) {
            double a0 = a[4 * r], a1 = a[4 * r + 1], a2 = a[4 * r + 2];
            for (int c = 0; c < 4; c++) out[4 * r + c] = a0 * bf[o + c] + a1 * bf[o + 4 + c] + a2 * bf[o + 8 + c] + (c == 3 ? a[4 * r + 3] : 0);
        }
    }

    public static void composer(double[] a, double[] b, double[] out) {
        for (int r = 0; r < 3; r++) {
            double a0 = a[4 * r], a1 = a[4 * r + 1], a2 = a[4 * r + 2];
            for (int c = 0; c < 4; c++) out[4 * r + c] = a0 * b[c] + a1 * b[4 + c] + a2 * b[8 + c] + (c == 3 ? a[4 * r + 3] : 0);
        }
    }

    /** Parcourt la scène posée par l'affine e (repère de la scène → monde relatif à la caméra) : cellules, instances,
     *  niveau. prec : niveau voulu à l'image précédente + 1, par instance (hystérésis). Rend le nombre d'instances
     *  sorties. Le niveau voulu absent est demandé (vouloir) ; on dessine en attendant le plus proche disponible.
     *  image : numéro de l'image (disponibilité des niveaux relue une fois par maillage et par image). */
    public static int parcourir(Plan p, double[] e, double[] plans, Parametres pa, byte[] prec, Ressources r, Sortie s, long image) {
        double sE = echelle(e);
        double[] bc = new double[6], bi = new double[6], m = new double[12];
        int sorties = 0;
        Contrat.Cellule[] cs = p.d.cellules();
        for (int c = 0; c < cs.length; c++) {
            Contrat.Cellule ce = cs[c];
            if (ce.n() == 0) continue;
            boite(e, ce.bornes(), 0, bc);
            if (!visible(plans, bc) || distance(bc) > pa.distanceMax) continue;
            if (pa.tests >= pa.testsMax) return sorties;
            pa.tests += ce.n();
            for (int i = ce.premiere(); i < ce.premiere() + ce.n(); i++) {
                boite(e, p.boitesI, 6 * i, bi);
                if (ce.n() > 1 && !visible(plans, bi)) continue;
                double d = distance(bi);
                if (d > pa.distanceMax) continue;
                double px = pa.pxParBloc1 / Math.max(d, 0.05);
                if (d > 0) {
                    double dx = bi[3] - bi[0], dy = bi[4] - bi[1], dz = bi[5] - bi[2];
                    if (0.5 * Math.sqrt(dx * dx + dy * dy + dz * dz) * px < pa.pxMin) continue; // sous le pixel
                }
                int mi = p.maillageI[i];
                Cle[] cl = p.cles[mi];
                boolean[] perm = p.permis[mi];
                boolean[] dispo = p.dispo[mi];
                if (p.dispoImage[mi] != image) {
                    p.dispoImage[mi] = image;
                    for (int l = 0; l < cl.length; l++) dispo[l] = perm[l] && r.prete(cl[l]);
                }
                double k = sE * p.echelleI[i] * px;
                int avant = prec[i] - 1;
                int voulu = Choix.niveau(p.erreurs[mi], perm, k, pa.seuilPx, avant);
                if (voulu < 0) continue;
                prec[i] = (byte) (voulu + 1);
                if (!dispo[voulu]) r.vouloir(cl[voulu]);
                int choix = Choix.niveau(p.erreurs[mi], dispo, k, pa.seuilPx, avant);
                if (choix < 0) continue;
                composer(e, p.matI, 12 * i, m);
                s.dessiner(i, c, mi, choix, m, d, bi);
                sorties++;
            }
        }
        return sorties;
    }
}
