package sage.link.mc;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import sage.link.core.KeyMap;
import sage.link.core.LinkState;
import sage.link.core.Msg;

import java.util.List;

/** Colle entre les mixins 26.3 et le noyau java pur. Chaque entrée attrape tout (Throwable) : rien ne remonte au jeu. */
public final class Bridge {
    private Bridge() {}

    public static final LinkState STATE = new LinkState();
    public static final String MC_VERSION = "26.3";
    private static volatile boolean hudOff, keysOff;

    /** Fin de ClientHandshakePacketListenerImpl.handleLoginFinished : après minecraft:brand et client_information. */
    public static void sendHello(Connection connection) {
        try {
            STATE.tabsCapable = sage.link.mc.tabs.SageTabs.registered(); // « tabs » seulement si les onglets existent
            STATE.assetsCapable = !"off".equals(System.getProperty("sage.link.assets")); // « assets » (§11), coupable
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
            if (sage.link.mc.blocks.BlocksEssai.receive(connection, s.id().getPath(), s.data())) return true;
            STATE.onInbound(s.id().getPath(), s.data());
        } catch (Throwable t) {
            STATE.error("reception", t);
        }
        return true;
    }

    public static void onDisconnect() {
        try { STATE.disconnected(); } catch (Throwable t) { STATE.error("deconnexion", t); }
        try { sage.link.mc.lod.LodClient.onDisconnect(); } catch (Throwable t) { STATE.error("deconnexion lod", t); }
        try { sage.link.mc.tabs.SageTabs.clear(); } catch (Throwable t) { STATE.error("deconnexion onglets", t); }
        try { sage.link.mc.assets.AssetsLink.INSTANCE.table.clear(); } catch (Throwable t) { STATE.error("deconnexion assets", t); }
    }

    /** Tête de Minecraft.tick() : interroge les touches du manifeste, aucun écran ouvert, envoie les transitions. */
    public static void tick(Minecraft mc) {
        sage.link.mc.blocks.BlocksEssai.tick();
        sage.link.mc.lod.LodClient.tick(mc); // attrape tout lui-même
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
                g.text(mc.font, "SAGE Link : ajoutez -XX:+UnlockDiagnosticVMOptions -XX:+AlwaysPreTouchStacks (risque de plantage)", 4, g.guiHeight() - 60, 0xFFFF5555, true);
            }
            if (list.isEmpty()) return;
            if (mc.debugEntries.isOverlayVisible()) return; // F3 occupe le coin
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
}
