package infinitylink.mc;

import net.fabricmc.api.ClientModInitializer;

import java.util.function.Supplier;

/** Entrypoint « client » (fabric-loader, sans Fabric API). Publie l'oracle d'état du contrat §3. */
public final class InfinityLinkClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        try {
            Migration.run(); // réglages et cache de SAGE Link / Infinity_Link 1.x repris sous les noms InfinityLink
            Supplier<String> oracle = Bridge.STATE::json;
            infinitylink.mc.lod.LodClient.loadOptions();
            infinitylink.mc.armor.Armures3d.loadOptions(); // armures 3D (0.3.2)
            infinitylink.mc.voice.VoiceClient.loadOptions(); // chat vocal (1.1.2)
            Bridge.STATE.voiceSink = infinitylink.mc.voice.VoiceClient::onMessage;
            infinitylink.mc.scenes.Scenes3d.loadOptions(); // scènes 3D (1.1.4)
            Bridge.STATE.scenesSink = infinitylink.mc.scenes.Scenes3d::recevoir;
            System.getProperties().put("infinitylink", oracle);
            System.getProperties().put("sage.link", oracle); // outils de bout en bout écrits pour SAGE Link
            // Onglets créatifs : enregistrés (ou non) avant le gel par SageBoot ; ici on branche la réception.
            Bridge.STATE.tabsCapable = infinitylink.mc.tabs.SageTabs.registered();
            Bridge.STATE.tabs.on = Bridge.STATE.tabsCapable;
            Bridge.STATE.tabsSink = infinitylink.mc.tabs.SageTabs.SINK;
            Bridge.STATE.assetsSink = infinitylink.mc.assets.AssetsLink.INSTANCE; // capacité « assets » (doc 30 §9.3)
            // capacité « formes » (1.1.0) : seulement si le stock de réserve est enregistré (sinon aucun état étendu n'existe)
            Bridge.STATE.formesSink = infinitylink.mc.blocks.SageShapes.INSTANCE;
            Bridge.STATE.formesCapable = infinitylink.mc.blocks.SageStock.registered();
            // Parade aux plantages du client 26.3 sous Windows (pile des Worker-Main qui ne peut plus grandir, MC-103) :
            // la JVM doit pré-engager toute la pile de chaque fil. On le vérifie et on le signale sans jamais bloquer.
            java.util.List<String> args = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
            Bridge.STATE.pretouch = args.contains("-XX:+AlwaysPreTouchStacks");
            if (!Bridge.STATE.pretouch) {
                System.err.println("[infinitylink] ATTENTION : ajoutez aux arguments Java « -XX:+UnlockDiagnosticVMOptions -XX:+AlwaysPreTouchStacks »"
                    + " : sans eux, le client 26.3 peut planter au rechargement des ressources (jvm!_chkstk sur un fil Worker-Main).");
            }
        } catch (Throwable t) {
            Bridge.STATE.error("init", t);
        }
    }
}
