package infinitylink.mc.mixin;

import net.minecraft.client.renderer.entity.state.ItemDisplayEntityRenderState;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scènes 3D et modèles (1.1.4, contrats « maillages » §7 et « modeles » §4). Fin de
 *  DisplayRenderer.ItemDisplayRenderer.extractRenderState : si l'item porte {"sage":"scene:<id>"} ou
 *  {"sage":"modele:<id>"} et que la scène est prête, elle est relevée (position, orientation, transformation, lumière)
 *  et l'item de repli est effacé : hasSubState devient faux, le jeu ne dessine ni l'item ni son ombre. */
@Mixin(targets = "net.minecraft.client.renderer.entity.DisplayRenderer$ItemDisplayRenderer", remap = false)
public abstract class ItemDisplayRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Display$ItemDisplay;Lnet/minecraft/client/renderer/entity/state/ItemDisplayEntityRenderState;F)V",
            at = @At("TAIL"), require = 1, remap = false)
    private void sage$scene(Display.ItemDisplay entity, ItemDisplayEntityRenderState state, float partialTick, CallbackInfo ci) {
        if (infinitylink.mc.scenes.Scenes3d.ancre(entity, state)) state.item.clear();
    }
}
