package infinitylink.mc.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import infinitylink.mc.Bridge;

/** 26.3 : le HUD vit dans net.minecraft.client.gui.Hud (appelé par Gui.extractRenderState) et dessine via
 *  GuiGraphicsExtractor.fill/text. On ajoute nos lignes à la fin, masquées avec le HUD (F1). */
@Mixin(value = Hud.class, remap = false)
public abstract class HudMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL"), require = 1, remap = false)
    private void sage$hud(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        Bridge.drawHud(graphics, ((Hud) (Object) this).isHidden());
    }
}
