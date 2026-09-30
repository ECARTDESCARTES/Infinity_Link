package infinitylink.mc.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scènes 3D (1.1.4). 26.3 : GameRenderer extrait l'image (LevelExtractor.extract : entités visibles, dont les
 *  item_display) AVANT LevelRenderer.render. En tête de l'extraction, les ancres de l'image précédente sont oubliées. */
@Mixin(value = LevelExtractor.class, remap = false)
public abstract class LevelExtractorMixin {
    @Inject(method = "extract(Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;F)V", at = @At("HEAD"), require = 1, remap = false)
    private void sage$scenesExtraction(DeltaTracker delta, Camera camera, float partialTick, CallbackInfo ci) {
        infinitylink.mc.scenes.Scenes3d.debutExtraction();
    }
}
