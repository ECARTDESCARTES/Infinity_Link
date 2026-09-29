package infinitylink.mc;

import com.mojang.blaze3d.platform.InputConstants;
import infinitylink.mc.voice.VoiceClient;
import infinitylink.mc.voice.VoiceScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/** Touches d'InfinityLink 1.1.2, réglables dans Options > Commandes (enregistrées dans options.txt comme les touches
 *  vanilla, OptionsMixin) : chat vocal façon Simple Voice Chat et raccourcis du mod. Codes = scancodes SDL3 de 26.3
 *  (InputConstants.KEY_*), -1 = sans touche par défaut. Libellés FR/EN fournis par ClientLanguageMixin. */
public final class Keys {
    private Keys() {}

    public static final KeyMapping.Category VOIX = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("infinitylink", "voix"));
    public static final KeyMapping.Category GENERAL = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("infinitylink", "general"));

    private static KeyMapping k(String id, int code, KeyMapping.Category c) {
        return new KeyMapping("key.infinitylink." + id, InputConstants.Type.KEYBOARD, code, c);
    }

    public static final KeyMapping PARLER = k("parler", InputConstants.KEY_CAPSLOCK, VOIX);
    public static final KeyMapping CHUCHOTER = k("chuchoter", -1, VOIX);
    public static final KeyMapping MICRO = k("micro", InputConstants.KEY_M, VOIX);
    public static final KeyMapping SOURDINE = k("sourdine", InputConstants.KEY_N, VOIX);
    public static final KeyMapping MENU = k("menu_voix", InputConstants.KEY_V, VOIX);
    public static final KeyMapping GROUPE = k("groupe", InputConstants.KEY_G, VOIX);
    public static final KeyMapping MODE = k("mode_voix", -1, VOIX);
    public static final KeyMapping ICONES = k("icones", InputConstants.KEY_H, VOIX);
    public static final KeyMapping LOINTAIN = k("lointain", -1, GENERAL);
    public static final KeyMapping EDITEUR = k("editeur", -1, GENERAL);
    public static final KeyMapping HUD = k("hud", -1, GENERAL);
    public static final KeyMapping ETAT = k("etat", -1, GENERAL);

    public static final List<KeyMapping> ALL = List.of(PARLER, CHUCHOTER, MICRO, SOURDINE, MENU, GROUPE, MODE, ICONES, LOINTAIN, EDITEUR, HUD, ETAT);

    /** Masque les lignes du HUD serveur d'InfinityLink (touche « hud »). */
    public static volatile boolean hudHidden;

    /** Tête de Minecraft.tick : touches maintenues et appuis. */
    public static void tick(Minecraft mc) {
        VoiceClient.pttDown = mc.gui.screen() == null && PARLER.isDown();
        VoiceClient.whisperDown = mc.gui.screen() == null && CHUCHOTER.isDown();
        while (MICRO.consumeClick()) { VoiceClient.muted = !VoiceClient.muted; VoiceClient.saveOptions(); tell(VoiceClient.muted ? "micro coupe" : "micro actif"); }
        while (SOURDINE.consumeClick()) { VoiceClient.deafened = !VoiceClient.deafened; VoiceClient.saveOptions(); tell(VoiceClient.deafened ? "sourdine : plus rien n'est entendu ni envoye" : "sourdine levee"); }
        while (MODE.consumeClick()) { VoiceClient.pushToTalk = !VoiceClient.pushToTalk; VoiceClient.saveOptions(); tell(VoiceClient.pushToTalk ? "mode : appuyer pour parler" : "mode : activation vocale"); }
        while (ICONES.consumeClick()) { VoiceClient.hideIcons = !VoiceClient.hideIcons; VoiceClient.saveOptions(); }
        while (GROUPE.consumeClick()) {
            var c = VoiceClient.config();
            if (c == null || c.group().isEmpty()) { tell("aucun groupe vocal : le rejoindre dans le menu vocal (" + MENU.getTranslatedKeyMessage().getString() + ")"); VoiceClient.groupTalk = false; }
            else { VoiceClient.groupTalk = !VoiceClient.groupTalk; tell(VoiceClient.groupTalk ? "parole au groupe " + c.group() : "parole de proximite"); }
        }
        while (MENU.consumeClick()) mc.gui.setScreen(new VoiceScreen());
        while (LOINTAIN.consumeClick()) tell(infinitylink.mc.lod.LodClient.toggle() ? "rendu lointain actif" : "rendu lointain coupe");
        while (EDITEUR.consumeClick()) infinitylink.mc.assets.AssetsLink.onCommand("lk editeur");
        while (HUD.consumeClick()) { hudHidden = !hudHidden; tell(hudHidden ? "HUD InfinityLink masque" : "HUD InfinityLink affiche"); }
        while (ETAT.consumeClick()) tell(Bridge.STATE.summary() + " · " + VoiceClient.micLine() + " · " + VoiceClient.status());
    }

    private static void tell(String s) { VoiceClient.chat(s); }

    // ------------------------------------------------------------------ libellés (ClientLanguageMixin)
    private static final Map<String, String[]> TEXT = Map.ofEntries(
        Map.entry("key.category.infinitylink.voix", new String[]{"InfinityLink — chat vocal", "InfinityLink — voice chat"}),
        Map.entry("key.category.infinitylink.general", new String[]{"InfinityLink", "InfinityLink"}),
        Map.entry("key.infinitylink.parler", new String[]{"Parler (appuyer)", "Push to talk"}),
        Map.entry("key.infinitylink.chuchoter", new String[]{"Chuchoter (appuyer)", "Whisper"}),
        Map.entry("key.infinitylink.micro", new String[]{"Couper le micro", "Mute microphone"}),
        Map.entry("key.infinitylink.sourdine", new String[]{"Sourdine (désactiver le chat vocal)", "Disable voice chat"}),
        Map.entry("key.infinitylink.menu_voix", new String[]{"Menu du chat vocal", "Voice chat menu"}),
        Map.entry("key.infinitylink.groupe", new String[]{"Parler au groupe / en proximité", "Toggle group talk"}),
        Map.entry("key.infinitylink.mode_voix", new String[]{"Mode : appuyer / activation vocale", "Toggle push to talk / voice activation"}),
        Map.entry("key.infinitylink.icones", new String[]{"Masquer les icônes vocales", "Hide voice icons"}),
        Map.entry("key.infinitylink.lointain", new String[]{"Rendu lointain (activer / couper)", "Toggle distant rendering"}),
        Map.entry("key.infinitylink.editeur", new String[]{"Éditeur de textures 16×16", "16×16 texture editor"}),
        Map.entry("key.infinitylink.hud", new String[]{"Masquer le HUD InfinityLink", "Hide InfinityLink HUD"}),
        Map.entry("key.infinitylink.etat", new String[]{"État d'InfinityLink (dans le chat)", "InfinityLink status (chat)"}));

    /** Traduction d'une clé du mod, ou null. Français si la langue du jeu est fr_*, anglais sinon. */
    public static String translate(String key) {
        String[] t = TEXT.get(key);
        if (t == null) return null;
        try {
            Minecraft mc = Minecraft.getInstance();
            String lang = mc == null || mc.options == null ? "fr_fr" : mc.options.languageCode;
            return lang != null && lang.startsWith("fr") ? t[0] : t[1];
        } catch (Throwable e) {
            return t[0];
        }
    }
}
