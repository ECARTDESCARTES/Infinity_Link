package sage.link.mc;

import net.fabricmc.api.ClientModInitializer;

import java.util.function.Supplier;

/** Entrypoint « client » (fabric-loader, sans Fabric API). Publie l'oracle d'état du contrat §3. */
public final class SageLinkClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        try {
            Supplier<String> oracle = Bridge.STATE::json;
            sage.link.mc.lod.LodClient.loadOptions();
            sage.link.mc.armor.Armures3d.loadOptions(); // armures 3D (0.3.2)
            System.getProperties().put("sage.link", oracle);
            // Onglets créatifs : enregistrés (ou non) avant le gel par SageBoot ; ici on branche la réception.
            Bridge.STATE.tabsCapable = sage.link.mc.tabs.SageTabs.registered();
            Bridge.STATE.tabs.on = Bridge.STATE.tabsCapable;
            Bridge.STATE.tabsSink = sage.link.mc.tabs.SageTabs.SINK;
            Bridge.STATE.assetsSink = sage.link.mc.assets.AssetsLink.INSTANCE; // capacité « assets » (doc 30 §9.3)
            // Parade aux plantages du client 26.3 sous Windows (pile des Worker-Main qui ne peut plus grandir, MC-103) :
            // la JVM doit pré-engager toute la pile de chaque fil. On le vérifie et on le signale sans jamais bloquer.
            java.util.List<String> args = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
            Bridge.STATE.pretouch = args.contains("-XX:+AlwaysPreTouchStacks");
            if (!Bridge.STATE.pretouch) {
                System.err.println("[sage_link] ATTENTION : ajoutez aux arguments Java « -XX:+UnlockDiagnosticVMOptions -XX:+AlwaysPreTouchStacks »"
                    + " : sans eux, le client 26.3 peut planter au rechargement des ressources (jvm!_chkstk sur un fil Worker-Main).");
            }
        } catch (Throwable t) {
            Bridge.STATE.error("init", t);
        }
    }
}
