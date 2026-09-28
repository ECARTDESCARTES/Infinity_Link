package sage.link.mc.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sage.link.mc.Bridge;
import sage.link.mc.tabs.SageTabs;
import sage.link.mc.tabs.TabPages;

import java.util.List;

/** §4.3 : pagination des onglets. tabs() filtré dans les 4 méthodes qui dessinent ou cliquent les onglets (F11) ;
 *  getTooltipFromContainerItem garde tous les onglets (nom du mod en bleu, F8). Boutons « < » « > » au-dessus des
 *  onglets et texte « 2 / 5 » ; aucun mot, donc aucune traduction. Sans catalogue : écran vanilla intact. */
@Mixin(value = CreativeModeInventoryScreen.class, remap = false)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class CreativeModeInventoryScreenMixin extends AbstractContainerScreen {
    @Shadow private static CreativeModeTab selectedTab;

    @Shadow
    private void selectTab(CreativeModeTab tab) {
        throw new AssertionError("mixin");
    }

    @Unique private Button sage$prev, sage$next;

    private CreativeModeInventoryScreenMixin() {
        super(null, null, null);
    }

    @Redirect(method = {
            "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z",
            "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/CreativeModeTabs;tabs()Ljava/util/List;"),
            require = 4, remap = false)
    private List<CreativeModeTab> sage$tabs() {
        List<CreativeModeTab> all = CreativeModeTabs.tabs();
        try {
            return TabPages.visible(all);
        } catch (Throwable t) {
            Bridge.STATE.error("onglets pages", t);
            return all;
        }
    }

    @Inject(method = "init()V", at = @At("TAIL"), require = 1, remap = false)
    private void sage$init(CallbackInfo ci) {
        try {
            sage$prev = null;
            sage$next = null;
            if (!SageTabs.registered()) return;
            sage$prev = Button.builder(Component.literal("<"), b -> sage$turn(-1)).bounds(this.leftPos, this.topPos - 50, 20, 20).build();
            sage$next = Button.builder(Component.literal(">"), b -> sage$turn(1)).bounds(this.leftPos + this.imageWidth - 20, this.topPos - 50, 20, 20).build();
            this.addRenderableWidget(sage$prev);
            this.addRenderableWidget(sage$next);
            sage$sync();
        } catch (Throwable t) {
            Bridge.STATE.error("onglets boutons", t);
        }
    }

    @Unique
    private void sage$sync() {
        int n = TabPages.pages(), p = TabPages.page();
        if (sage$prev != null) { sage$prev.visible = n > 1; sage$prev.active = p > 0; }
        if (sage$next != null) { sage$next.visible = n > 1; sage$next.active = p < n - 1; }
    }

    /** Changement de page par bouton : si l'onglet choisi est une catégorie absente de la nouvelle page, on choisit le premier de la page. */
    @Unique
    private void sage$turn(int d) {
        try {
            if (!(d < 0 ? TabPages.prev() : TabPages.next())) return;
            CreativeModeTab sel = selectedTab;
            if (sel == null || !TabPages.onPage(sel, TabPages.page())) {
                CreativeModeTab f = TabPages.first(TabPages.page());
                if (f != null) selectTab(f);
            }
            sage$sync();
        } catch (Throwable t) {
            Bridge.STATE.error("onglets page", t);
        }
    }

    /** Avant chaque image : la page suit l'onglet choisi (retour à l'onglet par défaut, réouverture) et les boutons l'état. */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), require = 1, remap = false)
    private void sage$beforeFrame(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
        try {
            CreativeModeTab sel = selectedTab;
            if (sel != null && SageTabs.count() > 0) {
                int p = TabPages.pageOf(sel);
                if (p >= 0 && p != TabPages.page()) TabPages.show(p);
            }
            sage$sync();
        } catch (Throwable t) {
            Bridge.STATE.error("onglets image", t);
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"), require = 1, remap = false)
    private void sage$pageText(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
        try {
            int n = TabPages.pages();
            if (n <= 1 || sage$prev == null) return;
            g.centeredText(this.font, (TabPages.page() + 1) + " / " + n, this.leftPos + this.imageWidth / 2, this.topPos - 44, 0xFFFFFFFF);
        } catch (Throwable t) {
            Bridge.STATE.error("onglets texte", t);
        }
    }
}
