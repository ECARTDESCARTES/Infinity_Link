package infinitylink.mc;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import infinitylink.core.KeyMap;
import infinitylink.core.LinkState;
import infinitylink.core.Msg;

import java.util.List;

/** Colle entre les mixins 26.3 et le noyau java pur. Chaque entrée attrape tout (Throwable) : rien ne remonte au jeu. */
public final class Bridge {
    private Bridge() {}

    public static final LinkState STATE = new LinkState();
    public static final String MC_VERSION = "26.3";
    private static volatile boolean hudOff, keysOff, inputOff;

    /** Fin de ClientHandshakePacketListenerImpl.handleLoginFinished : après minecraft:brand et client_information. */
    public static void sendHello(Connection connection) {
        try {
            STATE.tabsCapable = infinitylink.mc.tabs.SageTabs.registered(); // « tabs » seulement si les onglets existent
            STATE.assetsCapable = !"off".equals(infinitylink.core.Props.get("assets")); // « assets » (§11), coupable
            STATE.voiceCapable = !"off".equals(infinitylink.core.Props.get("voix")); // « voix » (1.1.2), coupable
            STATE.maillagesCapable = infinitylink.mc.scenes.Scenes3d.maillagesOption(); // « maillages » (1.1.4)
            STATE.modelesCapable = infinitylink.mc.scenes.Scenes3d.modelesOption(); // « modeles » (1.1.3, rendus en 1.1.4)
            infinitylink.mc.scenes.Scenes3d.connexion(connection);
            byte[] b = STATE.beginHello(MC_VERSION);
            connection.send(new ServerboundCustomPayloadPacket(SagePayload.out(Msg.HELLO, b)));
            STATE.sent();
        } catch (Throwable t) {
            STATE.error("hello", t);
        }
    }

    /** Tête de ClientCommonPacketListenerImpl.handleCustomPayload (configuration ET jeu). true = consommé. */
    public static boolean onClientbound(Connection connection, CustomPacketPayload payload) {
        if (!(payload instanceof SagePayload s)) return false;
        try {
            if (infinitylink.mc.scenes.ModelCollisionClient.receive(s.id().getPath(), s.data())) return true;
            if (infinitylink.mc.blocks.BlocksEssai.receive(connection, s.id().getPath(), s.data())) return true;
            STATE.onInbound(s.id().getPath(), s.data());
        } catch (Throwable t) {
            STATE.error("reception", t);
        }
        return true;
    }

    public static void onDisconnect() {
        try { STATE.disconnected(); } catch (Throwable t) { STATE.error("deconnexion", t); }
        try { infinitylink.mc.lod.LodClient.onDisconnect(); } catch (Throwable t) { STATE.error("deconnexion lod", t); }
        try { infinitylink.mc.tabs.SageTabs.clear(); } catch (Throwable t) { STATE.error("deconnexion onglets", t); }
        try { infinitylink.mc.assets.AssetsLink.INSTANCE.table.clear(); } catch (Throwable t) { STATE.error("deconnexion assets", t); }
        try { infinitylink.mc.voice.VoiceClient.onDisconnect(); } catch (Throwable t) { STATE.error("deconnexion voix", t); }
        infinitylink.mc.scenes.ModelCollisionClient.clear();
        try { infinitylink.mc.scenes.Scenes3d.onDisconnect(); } catch (Throwable t) { STATE.error("deconnexion scenes", t); }
    }

    /** Tête de Minecraft.tick() : interroge les touches du manifeste, aucun écran ouvert, envoie les transitions. */
    public static void tick(Minecraft mc) {
        infinitylink.mc.blocks.BlocksEssai.tick();
        infinitylink.mc.lod.LodClient.tick(mc); // attrape tout lui-même
        infinitylink.mc.scenes.ModelCollisionClient.tick(mc);
        infinitylink.mc.scenes.Scenes3d.tick(mc); // idem
        if (!inputOff) {
            try { Keys.tick(mc); infinitylink.mc.voice.VoiceClient.tick(mc); }
            catch (Throwable t) { inputOff = true; STATE.error("touches du mod et voix (desactivees)", t); }
        }
        if (keysOff || !STATE.wantsKeys()) return;
        try {
            ClientPacketListener conn = mc.getConnection();
            if (conn == null || mc.player == null || mc.gui.screen() != null) return;
            List<Msg.Key> changes = STATE.pollKeys(glfw -> {
                int sc = KeyMap.glfwToSdl(glfw);
                return sc >= 0 && InputConstants.isKeyDown(sc);
            });
            for (Msg.Key k : changes) {
                conn.send(new ServerboundCustomPayloadPacket(SagePayload.out(Msg.KEY, k.encode())));
                STATE.keySent(k);
            }
        } catch (Throwable t) {
            keysOff = true;
            STATE.error("touches (desactivees)", t);
        }
    }

    /** Fin de Hud.extractRenderState : barres + texte, coin haut gauche, style vanilla discret. */
    public static void drawHud(GuiGraphicsExtractor g, boolean hidden) {
        if (hudOff || hidden) return;
        try {
            List<Msg.HudEntry> list = STATE.hudSnapshot();
            Minecraft mc = Minecraft.getInstance();
            if (!STATE.pretouch && !mc.debugEntries.isOverlayVisible()) {
                g.text(mc.font, "InfinityLink : ajoutez -XX:+UnlockDiagnosticVMOptions -XX:+AlwaysPreTouchStacks (risque de plantage)", 4, g.guiHeight() - 60, 0xFFFF5555, true);
            }
            if (mc.debugEntries.isOverlayVisible()) return; // F3 occupe le coin
            drawVoice(g, mc);
            if (list.isEmpty() || Keys.hudHidden) return;
            Font font = mc.font;
            int x = 4, y = 4, n = 0;
            for (Msg.HudEntry e : list) {
                if (n++ >= 16 || y > g.guiHeight() - 12) break;
                g.text(font, e.text(), x, y, 0xFFE0E0E0, true);
                y += font.lineHeight;
                if (e.fraction() >= 0f) {
                    int w = 82;
                    int fw = Math.round((w - 2) * Math.min(1f, e.fraction()));
                    int c = e.color();
                    if ((c >>> 24) == 0) c |= 0xFF000000;
                    g.fill(x, y, x + w, y + 4, 0xA0000000);
                    if (fw > 0) g.fill(x + 1, y + 1, x + 1 + fw, y + 3, c);
                    y += 5;
                }
                y += 2;
            }
        } catch (Throwable t) {
            hudOff = true;
            STATE.error("hud (desactive)", t);
        }
    }

    /** Chat vocal (1.1.2) : état du micro et joueurs qui parlent, coin bas gauche au-dessus du chat ; masqué par la
     *  touche « icônes ». */
    private static void drawVoice(GuiGraphicsExtractor g, Minecraft mc) {
        if (infinitylink.mc.voice.VoiceClient.hideIcons || !infinitylink.mc.voice.VoiceClient.running()) return;
        Font font = mc.font;
        int y = g.guiHeight() - 90;
        boolean talking = infinitylink.mc.voice.VoiceClient.talking();
        g.text(font, infinitylink.mc.voice.VoiceClient.micLine(), 4, y, talking ? 0xFF55FF55 : 0xFFB0B0B0, true);
        for (String n : infinitylink.mc.voice.VoiceClient.speakers()) {
            y -= font.lineHeight + 1;
            if (y < 20) break;
            g.text(font, "> " + n, 4, y, 0xFFFFFF55, true);
        }
    }
}
