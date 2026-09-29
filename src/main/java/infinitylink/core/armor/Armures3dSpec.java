package infinitylink.core.armor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Armures 3D (SAGE Link 0.3.2), Java pur : contrat d'interface commun aux lots Ph4b-armures-3d (pack) et
 * Ph4b-link-armures-3d (mod).
 * <ul>
 * <li>(a) identifiant « &lt;pack&gt;/&lt;nom&gt; » en minuscules ;</li>
 * <li>(b) marqueur : custom_data {"sage_armure":"&lt;pack&gt;/&lt;nom&gt;"} sur la pile portée ;</li>
 * <li>(c) manifeste assets/sage/armures_3d.json : {"&lt;id&gt;": {"emplacement": "head|chest|legs|feet",
 *     "parties": ["head","body","right_arm","left_arm","right_leg","left_leg"]}} ;</li>
 * <li>(d) modèle d'item de la partie p : sage:armure/&lt;pack&gt;/&lt;nom&gt;/&lt;p&gt; ;</li>
 * <li>(e)(f) repère : modèle en unités de 16 par bloc, pivot de la partie en (8, 8, 8) ; après translateAndRotate
 *     de la partie : scale(1, -1, -1), translate(-0.5, -0.5, -0.5), rendu en contexte NONE.</li>
 * </ul>
 * Note (f) : en 26.3, ItemTransform.apply (appelé par ItemStackRenderState$LayerRenderState.submit, même pour
 * NO_TRANSFORM, donc en contexte NONE) applique déjà translate(-0.5, -0.5, -0.5) (vérifié par javap) : le mod ne
 * pose donc que scale(1, -1, -1) ; la translation du contrat est celle du rendu d'item vanilla (voir {@link #toPart}).
 */
public final class Armures3dSpec {
    private Armures3dSpec() {}

    /** Parties dans l'ordre canonique du contrat (c). */
    public static final List<String> PARTIES = List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");
    public static final List<String> EMPLACEMENTS = List.of("head", "chest", "legs", "feet");
    /** Bornes de coût : manifeste d'au plus 1 Mio et 8192 entrées. */
    public static final int MAX_OCTETS = 1 << 20, MAX_ENTREES = 8192;

    /** Une armure 3D du manifeste. */
    public record Entree(String id, String emplacement, List<String> parties) {
        /** Modèle d'item d'une partie, contrat (d) : « armure/&lt;pack&gt;/&lt;nom&gt;/&lt;partie&gt; » (espace sage). */
        public String modele(String partie) { return "armure/" + id + "/" + partie; }
    }

    /** Identifiant valide (a) ou null : deux segments ou plus [a-z0-9_.-], séparés par « / », sans « .. ». */
    public static String id(String brut) {
        if (brut == null) return null;
        String s = brut.trim();
        if (s.isEmpty() || s.length() > 200 || !s.equals(s.toLowerCase(Locale.ROOT))) return null;
        String[] seg = s.split("/", -1);
        if (seg.length < 2) return null;
        for (String g : seg) {
            if (g.isEmpty() || g.equals(".") || g.equals("..")) return null;
            for (int i = 0; i < g.length(); i++) {
                char c = g.charAt(i);
                if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.')) return null;
            }
        }
        return s;
    }

    /** Marqueur (b) lu depuis la valeur de la clé sage_armure (null si absente ou invalide). */
    public static String marqueur(Map<String, ?> customData) {
        if (customData == null) return null;
        Object v = customData.get("sage_armure");
        return v instanceof String s ? id(s) : null;
    }

    /** Emplacement du contrat pour un nom d'EquipmentSlot vanilla (HEAD, CHEST, LEGS, FEET), sinon null. */
    public static String emplacement(String slotName) {
        if (slotName == null) return null;
        String e = slotName.toLowerCase(Locale.ROOT);
        return EMPLACEMENTS.contains(e) ? e : null;
    }

    /**
     * Lit un manifeste (c). Les entrées invalides sont ignorées une à une (compteur {@code rejets[0]} si fourni) ;
     * une erreur de syntaxe JSON lève IllegalArgumentException.
     */
    public static Map<String, Entree> manifeste(String json, int[] rejets) {
        if (json.length() > MAX_OCTETS) throw new IllegalArgumentException("manifeste trop gros");
        Object racine = new Json(json).document();
        if (!(racine instanceof Map<?, ?> m)) throw new IllegalArgumentException("manifeste : objet attendu");
        Map<String, Entree> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            Entree en = entree((String) e.getKey(), e.getValue());
            if (en == null || out.size() >= MAX_ENTREES) { if (rejets != null) rejets[0]++; continue; }
            out.put(en.id(), en);
        }
        return out;
    }

    /** Fusionne plusieurs manifestes (du pack le plus bas au plus haut : le plus haut l'emporte). */
    public static Map<String, Entree> fusion(List<Map<String, Entree>> bas_vers_haut) {
        Map<String, Entree> out = new LinkedHashMap<>();
        for (Map<String, Entree> m : bas_vers_haut) out.putAll(m);
        return out;
    }

    private static Entree entree(String cle, Object v) {
        String id = id(cle);
        if (id == null || !(v instanceof Map<?, ?> o)) return null;
        Object emp = o.get("emplacement");
        if (!(emp instanceof String es) || !EMPLACEMENTS.contains(es)) return null;
        Object ps = o.get("parties");
        if (!(ps instanceof List<?> l) || l.isEmpty()) return null;
        List<String> parties = new ArrayList<>();
        for (String p : PARTIES) if (l.contains(p)) parties.add(p);
        for (Object x : l) if (!(x instanceof String s) || !PARTIES.contains(s)) return null;
        return new Entree(id, es, Collections.unmodifiableList(parties));
    }

    // ------------------------------------------------------------------ repère (e)(f)

    /** Conversion (e) : pixel d'origine de l'espace de la partie (y vers le bas) vers unités du modèle JSON. */
    public static double[] versModele(double x, double y, double z) { return new double[] {8 + x, 8 - y, 8 - z}; }

    /**
     * Pose (f) : point du modèle JSON (unités 16 = 1 bloc) vers l'espace de la partie (en blocs, convention ModelPart,
     * y vers le bas) : scale(1, -1, -1) posé par le mod, puis translate(-0.5, -0.5, -0.5) (ItemTransform.apply
     * vanilla en contexte NONE), puis sommet du modèle / 16.
     */
    public static double[] toPart(double[] m) {
        double x = m[0] / 16 - 0.5, y = m[1] / 16 - 0.5, z = m[2] / 16 - 0.5;
        return new double[] {x, -y, -z};
    }

    /**
     * Point du modèle JSON vers l'espace du modèle d'entité, pour une partie de pivot (px, py, pz) en pixels et de
     * rotations (xRot, yRot, zRot) en radians : même composition que ModelPart.translateAndRotate
     * (translate(pivot / 16) puis rotation ZYX, échelle 1), puis {@link #toPart}.
     */
    public static double[] toEntity(double px, double py, double pz, double xRot, double yRot, double zRot, double[] m) {
        double[] p = toPart(m);
        // Quaternionf.rotationZYX(z, y, x) : R = Rz * Ry * Rx
        double[] r = rot(0, xRot, rot(1, yRot, rot(2, zRot, null)));
        double x = r[0] * p[0] + r[1] * p[1] + r[2] * p[2];
        double y = r[3] * p[0] + r[4] * p[1] + r[5] * p[2];
        double z = r[6] * p[0] + r[7] * p[1] + r[8] * p[2];
        return new double[] {px / 16 + x, py / 16 + y, pz / 16 + z};
    }

    /** m * R_axe(a) (matrices 3x3 en ligne) ; m == null : identité. */
    private static double[] rot(int axe, double a, double[] m) {
        double c = Math.cos(a), s = Math.sin(a);
        double[] r = switch (axe) {
            case 0 -> new double[] {1, 0, 0, 0, c, -s, 0, s, c};
            case 1 -> new double[] {c, 0, s, 0, 1, 0, -s, 0, c};
            default -> new double[] {c, -s, 0, s, c, 0, 0, 0, 1};
        };
        if (m == null) return r;
        double[] o = new double[9];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
            o[i * 3 + j] = m[i * 3] * r[j] + m[i * 3 + 1] * r[3 + j] + m[i * 3 + 2] * r[6 + j];
        return o;
    }

    // ------------------------------------------------------------------ JSON minimal (objets, listes, chaînes, nombres, littéraux)

    static final class Json {
        private final String s; private int i;
        Json(String s) { this.s = s; }
        Object document() { Object v = valeur(0); blancs(); if (i != s.length()) throw err("fin attendue"); return v; }
        private IllegalArgumentException err(String m) { return new IllegalArgumentException("JSON " + m + " a " + i); }
        private void blancs() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        private Object valeur(int prof) {
            if (prof > 32) throw err("trop profond");
            blancs();
            if (i >= s.length()) throw err("valeur attendue");
            char c = s.charAt(i);
            if (c == '{') {
                i++; Map<String, Object> m = new LinkedHashMap<>(); blancs();
                if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    blancs(); if (i >= s.length() || s.charAt(i) != '"') throw err("cle attendue");
                    String k = chaine(); blancs();
                    if (i >= s.length() || s.charAt(i++) != ':') throw err("':' attendu");
                    m.put(k, valeur(prof + 1)); blancs();
                    if (i >= s.length()) throw err("objet non ferme");
                    char d = s.charAt(i++);
                    if (d == '}') return m;
                    if (d != ',') throw err("',' attendu");
                }
            }
            if (c == '[') {
                i++; List<Object> l = new ArrayList<>(); blancs();
                if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(valeur(prof + 1)); blancs();
                    if (i >= s.length()) throw err("liste non fermee");
                    char d = s.charAt(i++);
                    if (d == ']') return l;
                    if (d != ',') throw err("',' attendu");
                }
            }
            if (c == '"') return chaine();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int d = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (d == i) throw err("valeur inconnue");
            try { return Double.parseDouble(s.substring(d, i)); } catch (NumberFormatException e) { throw err("nombre"); }
        }
        private String chaine() {
            i++; StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw err("chaine non fermee");
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                if (i >= s.length()) throw err("echappement");
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n'); case 't' -> b.append('\t'); case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b'); case 'f' -> b.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw err("\\u");
                        try { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); } catch (NumberFormatException x) { throw err("\\u"); }
                        i += 4;
                    }
                    default -> b.append(e);
                }
            }
        }
    }
}
