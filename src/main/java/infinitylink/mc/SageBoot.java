package infinitylink.mc;

/** Point d'entrée unique AVANT le gel des registres intégrés (BuiltInRegistriesMixin, juste avant freeze()).
 *  Partagé par les onglets créatifs (ONGLETS-CREATIFS-SPEC.md §4.1) et les vrais blocs (BLOCS-SPEC.md §3.2) :
 *  Onglets facultatifs ; un stock partiel arrête explicitement le bootstrap, sans rollback. */
public final class SageBoot {
    private SageBoot() {}

    private static boolean done;

    public static void avantGel() {
        if (done) return;
        done = true;
        try {
            infinitylink.mc.tabs.SageTabs.register();
        } catch (Throwable t) {
            System.err.println("[infinitylink] onglets : enregistrement abandonne : " + t);
        }
        try { infinitylink.mc.blocks.SageStock.register(); }
        catch (Throwable t) {
            throw new IllegalStateException("Stock incomplet : redemarrer avec -Dinfinitylink.blocks=off (aucun rollback de registre)",t);
        }
    }
}
