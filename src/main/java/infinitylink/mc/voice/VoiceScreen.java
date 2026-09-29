package infinitylink.mc.voice;

import infinitylink.core.voice.VoiceMsg;
import infinitylink.mc.Bridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Écran du chat vocal (touche V, comme Simple Voice Chat) : mode, micro, sourdine, seuil d'activation avec niveau du
 *  micro en direct, volumes, groupe (rejoindre, quitter, parler au groupe), icônes. Réglages enregistrés à la
 *  fermeture. */
public final class VoiceScreen extends Screen {
    private EditBox group;
    private Button mode, mic, deaf, talkGroup, icons;

    public VoiceScreen() { super(Component.literal("InfinityLink : chat vocal")); }

    private static Component on(String label, boolean v) { return Component.literal(label + " : " + (v ? "oui" : "non")); }

    private void refresh() {
        mode.setMessage(Component.literal("Mode : " + (VoiceClient.pushToTalk ? "appuyer pour parler" : "activation vocale")));
        mic.setMessage(on("Micro coupe", VoiceClient.muted));
        deaf.setMessage(on("Sourdine", VoiceClient.deafened));
        talkGroup.setMessage(on("Parler au groupe", VoiceClient.groupTalk));
        icons.setMessage(on("Masquer les icones", VoiceClient.hideIcons));
    }

    @Override protected void init() {
        int x = width / 2 - 155, y = 40, w = 150, h = 20;
        mode = addRenderableWidget(Button.builder(Component.empty(), b -> { VoiceClient.pushToTalk = !VoiceClient.pushToTalk; refresh(); }).bounds(x, y, w, h).build());
        mic = addRenderableWidget(Button.builder(Component.empty(), b -> { VoiceClient.muted = !VoiceClient.muted; refresh(); }).bounds(x + 160, y, w, h).build());
        y += 24;
        deaf = addRenderableWidget(Button.builder(Component.empty(), b -> { VoiceClient.deafened = !VoiceClient.deafened; refresh(); }).bounds(x, y, w, h).build());
        icons = addRenderableWidget(Button.builder(Component.empty(), b -> { VoiceClient.hideIcons = !VoiceClient.hideIcons; refresh(); }).bounds(x + 160, y, w, h).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Seuil -5 dB"), b -> VoiceClient.threshold = VoiceClient.clamp(VoiceClient.threshold - 5, -80, 0)).bounds(x, y, 73, h).build());
        addRenderableWidget(Button.builder(Component.literal("Seuil +5 dB"), b -> VoiceClient.threshold = VoiceClient.clamp(VoiceClient.threshold + 5, -80, 0)).bounds(x + 77, y, 73, h).build());
        addRenderableWidget(Button.builder(Component.literal("Micro -10 %"), b -> VoiceClient.micVolume = VoiceClient.clamp(VoiceClient.micVolume - 10, 0, 300)).bounds(x + 160, y, 73, h).build());
        addRenderableWidget(Button.builder(Component.literal("Micro +10 %"), b -> VoiceClient.micVolume = VoiceClient.clamp(VoiceClient.micVolume + 10, 0, 300)).bounds(x + 237, y, 73, h).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Sortie -10 %"), b -> VoiceClient.outVolume = VoiceClient.clamp(VoiceClient.outVolume - 10, 0, 300)).bounds(x, y, 73, h).build());
        addRenderableWidget(Button.builder(Component.literal("Sortie +10 %"), b -> VoiceClient.outVolume = VoiceClient.clamp(VoiceClient.outVolume + 10, 0, 300)).bounds(x + 77, y, 73, h).build());
        talkGroup = addRenderableWidget(Button.builder(Component.empty(), b -> { VoiceClient.groupTalk = !VoiceClient.groupTalk; refresh(); }).bounds(x + 160, y, w, h).build());
        y += 46;
        group = new EditBox(font, x, y, w, h, Component.literal("Groupe"));
        group.setMaxLength(24);
        VoiceMsg.Config c = VoiceClient.config();
        group.setValue(c == null ? "" : c.group());
        addRenderableWidget(group);
        addRenderableWidget(Button.builder(Component.literal("Rejoindre"), b -> send("groupe", group.getValue().trim())).bounds(x + 160, y, 73, h).build());
        addRenderableWidget(Button.builder(Component.literal("Quitter"), b -> send("quitter", "")).bounds(x + 237, y, 73, h).build());
        y += 34;
        addRenderableWidget(Button.builder(Component.literal("Terminer"), b -> onClose()).bounds(width / 2 - 75, y, 150, h).build());
        refresh();
    }

    private void send(String action, String arg) {
        if (action.equals("groupe") && arg.isEmpty()) { VoiceClient.chat("nom de groupe vide"); return; }
        if (!VoiceClient.control(action, arg)) VoiceClient.chat("chat vocal absent sur ce serveur");
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        try {
            g.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
            VoiceMsg.Config c = VoiceClient.config();
            String etat = c == null ? "Serveur sans chat vocal (capacite voix absente)" : VoiceClient.status();
            g.centeredText(font, Component.literal(etat), width / 2, 26, 0xFFA0A0A0);
            int x = width / 2 - 155, y = 40 + 24 * 4;
            double lvl = VoiceClient.level();
            int bar = 310, fill = (int) Math.round(bar * Math.max(0, Math.min(1, (lvl + 80) / 80.0)));
            int th = (int) Math.round(bar * (VoiceClient.threshold + 80) / 80.0);
            g.fill(x, y + 2, x + bar, y + 6, 0xA0000000);
            g.fill(x, y + 2, x + fill, y + 6, lvl >= VoiceClient.threshold ? 0xFF55FF55 : 0xFF8888FF);
            g.fill(x + th, y, x + th + 1, y + 8, 0xFFFFFF55);
            g.text(font, "Seuil " + VoiceClient.threshold + " dB · micro " + VoiceClient.micVolume + " % · sortie " + VoiceClient.outVolume + " %"
                    + (c == null ? "" : " · rayon " + Math.round(c.radius()) + " blocs"), x, y + 10, 0xFFE0E0E0, true);
        } catch (Throwable t) {
            Bridge.STATE.error("ecran voix", t);
        }
    }

    @Override public void onClose() {
        VoiceClient.saveOptions();
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }
}
