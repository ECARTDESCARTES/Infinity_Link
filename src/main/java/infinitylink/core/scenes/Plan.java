package infinitylink.core.scenes;

import infinitylink.core.scenes.Contrat.Cle;
import infinitylink.core.scenes.Contrat.Description;

import java.util.HashSet;
import java.util.Set;

/** Scène prête pour le rendu, calculée une fois hors du fil de rendu (Java pur) : par maillage, clés et erreurs de
 *  ses niveaux et niveaux permis (niveau 0 écarté en « économie » pour les maillages de plus de 2 niveaux, comme le
 *  BESOIN) ; par instance, boîte dans le repère de la scène (8 coins des bornes du maillage transformés), plus grand
 *  facteur d'échelle et matrice. */
public final class Plan {
    public final String marqueur;
    public final boolean commeItem;
    public final Description d;
    public final Cle[][] cles;
    public final float[][] erreurs;
    public final boolean[][] permis;
    public final int[][] triangles;
    public final float[] boitesI, echelleI, matI;
    public final int[] maillageI;
    public final Set<Cle> citees = new HashSet<>();
    /** Nombre de maillages cités par au moins une instance, et leurs index. */
    public final int maillagesCites;
    public final int[] maillagesUtiles;
    /** Brouillon du fil de rendu (Selection.parcourir) : niveaux disponibles par maillage, recalculés une fois par image. */
    final boolean[][] dispo;
    final long[] dispoImage;

    public Plan(String marqueur, Description d, boolean commeItem, boolean economie) {
        this.marqueur = marqueur;
        this.commeItem = commeItem;
        this.d = d;
        int nm = d.maillages().length;
        cles = new Cle[nm][];
        erreurs = new float[nm][];
        permis = new boolean[nm][];
        triangles = new int[nm][];
        dispo = new boolean[nm][];
        dispoImage = new long[nm];
        java.util.Arrays.fill(dispoImage, Long.MIN_VALUE);
        for (int m = 0; m < nm; m++) {
            Contrat.Niveau[] ns = d.maillages()[m].niveaux();
            cles[m] = new Cle[ns.length];
            erreurs[m] = new float[ns.length];
            permis[m] = new boolean[ns.length];
            triangles[m] = new int[ns.length];
            dispo[m] = new boolean[ns.length];
            for (int l = 0; l < ns.length; l++) {
                cles[m][l] = d.ressources()[ns[l].ressource()].cle();
                erreurs[m][l] = ns[l].erreur();
                triangles[m][l] = ns[l].triangles();
                permis[m][l] = !(economie && l == 0 && ns.length > 2);
            }
        }
        for (Contrat.Ressource r : d.ressources()) citees.add(r.cle());
        int ni = d.instances().length;
        boitesI = new float[6 * ni];
        echelleI = new float[ni];
        maillageI = new int[ni];
        matI = new float[12 * ni];
        boolean[] cite = new boolean[nm];
        double[] e = new double[12], b = new double[6];
        for (int i = 0; i < ni; i++) {
            Contrat.Instance in = d.instances()[i];
            maillageI[i] = in.maillage();
            cite[in.maillage()] = true;
            for (int k = 0; k < 12; k++) e[k] = in.m()[k];
            System.arraycopy(in.m(), 0, matI, 12 * i, 12);
            Selection.boite(e, d.maillages()[in.maillage()].bornes(), 0, b);
            for (int k = 0; k < 6; k++) boitesI[6 * i + k] = (float) b[k];
            // arrondi vers l'extérieur : la boîte contient toujours la géométrie
            for (int k = 0; k < 3; k++) {
                boitesI[6 * i + k] = Math.nextDown(boitesI[6 * i + k]);
                boitesI[6 * i + 3 + k] = Math.nextUp(boitesI[6 * i + 3 + k]);
            }
            echelleI[i] = (float) Selection.echelle(e);
        }
        int n = 0;
        for (boolean c : cite) if (c) n++;
        maillagesCites = n;
        maillagesUtiles = new int[n];
        for (int m = 0, k = 0; m < nm; m++) if (cite[m]) maillagesUtiles[k++] = m;
    }
}
