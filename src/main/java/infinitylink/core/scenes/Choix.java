package infinitylink.core.scenes;

/** Choix du niveau de détail et budget de triangles (contrat « maillages » §8.4, §8.5). Java pur. */
public final class Choix {
    private Choix() {}

    /** Hystérésis : un niveau plus grossier n'est pris que si son erreur projetée passe sous seuil / 1,2. */
    public static final double HYSTERESIS = 1.2;

    /** Pixels par bloc à la distance `d` : hauteur de l'écran / (2 d tan(fov / 2)) (fov vertical en radians). */
    public static double pixelsParBloc(double hauteurPx, double fovRad, double d) {
        return hauteurPx / (2 * Math.max(d, 1e-3) * Math.tan(fovRad / 2));
    }

    /** Niveau à dessiner : le plus grossier dont l'erreur projetée (erreur × k) est ≤ seuil (niveaux du plus fin au
     *  plus grossier, erreurs croissantes) ; hystérésis autour du niveau précédent ; un niveau absent est remplacé par
     *  le plus proche disponible, grossier d'abord. -1 : aucun disponible. */
    public static int niveau(float[] erreurs, boolean[] dispo, double k, double seuilPx, int precedent) {
        int n = erreurs.length, cible = 0, cibleStricte = 0;
        for (int i = 0; i < n; i++) {
            double px = erreurs[i] * k;
            if (px <= seuilPx) cible = i;
            if (px <= seuilPx / HYSTERESIS) cibleStricte = i;
        }
        int c = cible;
        if (precedent >= 0 && precedent < n && precedent < cible && precedent >= cibleStricte) c = precedent; // on garde le plus fin
        else if (precedent >= 0 && precedent < cible) c = cibleStricte > precedent ? cibleStricte : precedent;
        if (dispo[c]) return c;
        for (int i = c + 1; i < n; i++) if (dispo[i]) return i;
        for (int i = c - 1; i >= 0; i--) if (dispo[i]) return i;
        return -1;
    }

    /** Facteur du seuil d'erreur pour tenir un budget (triangles ou appels de dessin) : monte de 25 % par image au-delà
     *  du budget (64 × au plus), redescend de 10 % sous la moitié (1 au moins). */
    public static double facteur(double f, long depense, long budget) {
        if (depense > budget) return Math.min(f * 1.25, 64);
        if (depense < budget / 2) return Math.max(f / 1.1, 1);
        return f;
    }
}
