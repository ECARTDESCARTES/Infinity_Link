import infinitylink.core.armor.Armures3dSpec;
import infinitylink.core.armor.Armures3dSpec.Entree;

import java.util.List;
import java.util.Map;

/** Java pur : armures 3D (0.3.2) : manifeste (c), marqueur (b), repère (e)(f) sur la pile de référence test/cube
 *  (partie head, cube 2x2x2 centré sur le pivot : élément de (7, 7, 7) à (9, 9, 9) en unités du modèle). */
public final class Armures3dTest {
    static int ok, ko;

    static void check(String name, boolean cond, String detail) {
        if (cond) { ok++; System.out.println("OK   " + name); }
        else { ko++; System.out.println("FAIL " + name + " : " + detail); }
    }

    static boolean near(double[] a, double x, double y, double z) {
        return Math.abs(a[0] - x) < 1e-9 && Math.abs(a[1] - y) < 1e-9 && Math.abs(a[2] - z) < 1e-9;
    }

    static String s(double[] a) { return "(" + a[0] + ", " + a[1] + ", " + a[2] + ")"; }

    public static void main(String[] args) {
        String json = "{\n \"test/cube\": {\"emplacement\": \"head\", \"parties\": [\"head\"]},\n"
                + " \"modern_warfare_content_pack/swathelmet\": {\"emplacement\": \"head\", \"parties\": [\"head\"]},\n"
                + " \"drawxv2/gilet\": {\"emplacement\": \"chest\", \"parties\": [\"left_arm\", \"body\", \"right_arm\"]},\n"
                + " \"Mauvais/Casse\": {\"emplacement\": \"head\", \"parties\": [\"head\"]},\n"
                + " \"x/sans_emplacement\": {\"parties\": [\"head\"]},\n"
                + " \"x/partie_inconnue\": {\"emplacement\": \"feet\", \"parties\": [\"tail\"]},\n"
                + " \"../evasion\": {\"emplacement\": \"legs\", \"parties\": [\"left_leg\"]},\n"
                + " \"x/\\u00e9chappe\": {\"emplacement\": \"legs\", \"parties\": [\"left_leg\"]}\n}";
        int[] rj = {0};
        Map<String, Entree> m = Armures3dSpec.manifeste(json, rj);
        check("manifeste : 3 entrees valides", m.size() == 3, m.keySet().toString());
        check("manifeste : 5 rejets", rj[0] == 5, "" + rj[0]);
        Entree cube = m.get("test/cube");
        check("manifeste : test/cube head", cube != null && cube.emplacement().equals("head") && cube.parties().equals(List.of("head")), "" + cube);
        Entree gilet = m.get("drawxv2/gilet");
        check("manifeste : parties en ordre canonique", gilet != null && gilet.parties().equals(List.of("body", "right_arm", "left_arm")), "" + gilet);
        check("modele d'item (d)", cube != null && cube.modele("head").equals("armure/test/cube/head"), "");
        boolean syntaxe = false;
        try { Armures3dSpec.manifeste("{\"a/b\": ", null); } catch (IllegalArgumentException e) { syntaxe = true; }
        check("manifeste : JSON tronque refuse", syntaxe, "");
        Map<String, Entree> haut = Armures3dSpec.manifeste("{\"test/cube\": {\"emplacement\": \"head\", \"parties\": [\"head\", \"body\"]}}", null);
        check("fusion : le pack le plus haut l'emporte", Armures3dSpec.fusion(List.of(m, haut)).get("test/cube").parties().size() == 2, "");

        check("marqueur valide", "test/cube".equals(Armures3dSpec.marqueur(Map.of("sage_armure", "test/cube"))), "");
        check("marqueur majuscules refuse", Armures3dSpec.marqueur(Map.of("sage_armure", "Test/Cube")) == null, "");
        check("marqueur absent", Armures3dSpec.marqueur(Map.of("autre", "a/b")) == null, "");
        check("marqueur non chaine", Armures3dSpec.marqueur(Map.of("sage_armure", 3)) == null, "");
        check("emplacement HEAD -> head", "head".equals(Armures3dSpec.emplacement("HEAD")), "");
        check("emplacement MAINHAND -> null", Armures3dSpec.emplacement("MAINHAND") == null, "");

        // (e) : pixel d'origine (0, 0, 0) de la partie = pivot = (8, 8, 8) du modèle
        check("repere (e) : pivot -> (8,8,8)", near(Armures3dSpec.versModele(0, 0, 0), 8, 8, 8), "");
        check("repere (e) : (1,2,3) -> (9,6,5)", near(Armures3dSpec.versModele(1, 2, 3), 9, 6, 5), "");
        // (f) : centre du cube test/cube sur le pivot de la tête
        double[] centre = {8, 8, 8};
        check("pose (f) : centre du cube sur le pivot (espace partie)", near(Armures3dSpec.toPart(centre), 0, 0, 0), s(Armures3dSpec.toPart(centre)));
        // pivot de la tête du modèle humanoïde vanilla (0, 0, 0) ; tête tournée : le centre reste sur le pivot
        double[] c1 = Armures3dSpec.toEntity(0, 0, 0, 0.4, -0.9, 0.2, centre);
        check("pose (f) : centre sur le pivot, tete tournee", near(c1, 0, 0, 0), s(c1));
        double[] c2 = Armures3dSpec.toEntity(0, -2.5, 0, 0.3, 0, 0, centre);
        check("pose (f) : centre sur un pivot deplace", near(c2, 0, -2.5 / 16, 0), s(c2));
        // aller-retour (e)(f) : un pixel d'origine revient à sa place dans l'espace de la partie (en blocs)
        double[] r = Armures3dSpec.toPart(Armures3dSpec.versModele(1.5, -3, 2));
        check("aller-retour (e)(f)", near(r, 1.5 / 16, -3 / 16.0, 2 / 16.0), s(r));
        double[] coin = Armures3dSpec.toPart(new double[] {9, 9, 9});
        check("pose (f) : coin (9,9,9) -> (1,-1,-1)/16", near(coin, 1 / 16.0, -1 / 16.0, -1 / 16.0), s(coin));
        // rotation de 90° autour de Y : +x vers -z (Quaternionf.rotationZYX, main droite)
        double[] rot = Armures3dSpec.toEntity(0, 0, 0, 0, Math.PI / 2, 0, new double[] {24, 8, 8});
        check("pose : yRot 90 deg envoie +x sur -z", near(rot, 0, 0, -1), s(rot));

        System.out.println("Armures3dTest : " + ok + " OK, " + ko + " FAIL");
        if (ko != 0) System.exit(1);
    }
}
