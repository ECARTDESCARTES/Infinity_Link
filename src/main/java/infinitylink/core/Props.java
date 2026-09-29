package infinitylink.core;

/** Propriétés JVM du mod : `-Dinfinitylink.<nom>` ; l'ancien nom `-Dsage.link.<nom>` (SAGE Link) reste lu. */
public final class Props {
    private Props() {}

    public static String get(String nom) { return get(nom, null); }

    public static String get(String nom, String defaut) {
        String v = System.getProperty("infinitylink." + nom);
        if (v == null) v = System.getProperty(nom.startsWith("blocs.") ? "sage." + nom : "sage.link." + nom);
        return v == null ? defaut : v;
    }
}
