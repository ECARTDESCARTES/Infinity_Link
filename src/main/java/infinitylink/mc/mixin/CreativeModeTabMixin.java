package infinitylink.mc.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import infinitylink.mc.tabs.SageTabHandle;
import infinitylink.mc.tabs.SageTabs;
import infinitylink.mc.tabs.TabPages;

/** §4.2 : nom, icône et position dynamiques des onglets Link. Actif seulement pour nos onglets ET s'ils ont une place
 *  dans le catalogue reçu ; sinon (autres onglets, boot, validate()) le comportement vanilla est intact. */
@Mixin(value = CreativeModeTab.class, remap = false)
public abstract class CreativeModeTabMixin implements SageTabHandle {
    /** indice + 1 (0 = onglet qui n'est pas à Link) : aucun initialiseur de champ à fusionner dans le constructeur. */
    @Unique private int sage$slot;

    @Override public int sage$index() { return sage$slot - 1; }

    @Override public void sage$setIndex(int k) { sage$slot = k + 1; }

    @Inject(method = "getDisplayName()Lnet/minecraft/network/chat/Component;", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void sage$name(CallbackInfoReturnable<Component> cir) {
        if (sage$slot == 0) return;
        Component c = SageTabs.title(sage$slot - 1);
        if (c != null) cir.setReturnValue(c);
    }

    @Inject(method = "getIconItem()Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void sage$icon(CallbackInfoReturnable<ItemStack> cir) {
        if (sage$slot == 0) return;
        ItemStack s = SageTabs.icon(sage$slot - 1);
        if (s != null) cir.setReturnValue(s);
    }

    @Inject(method = "row()Lnet/minecraft/world/item/CreativeModeTab$Row;", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void sage$row(CallbackInfoReturnable<CreativeModeTab.Row> cir) {
        if (sage$slot == 0) return;
        int r = SageTabs.rank(sage$slot - 1);
        if (r >= 0) cir.setReturnValue(TabPages.row(r));
    }

    @Inject(method = "column()I", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void sage$column(CallbackInfoReturnable<Integer> cir) {
        if (sage$slot == 0) return;
        int r = SageTabs.rank(sage$slot - 1);
        if (r >= 0) cir.setReturnValue(TabPages.column(r));
    }
}
