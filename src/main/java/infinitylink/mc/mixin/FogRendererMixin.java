package infinitylink.mc.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import infinitylink.mc.lod.LodClient;

/** 26.3 : FogRenderer.setupFog rend le FogData de l'image (renderDistanceStart/End = distance de rendu en blocs,
 *  environmentalStart/End = attributs d'environnement), écrit ensuite dans l'UBO « Fog » par updateBuffer.
 *  On repousse la fin à la distance lod tant que le lod est actif. */
@Mixin(value = FogRenderer.class, remap = false)
public abstract class FogRendererMixin {
    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;",
            at = @At("RETURN"), require = 1, remap = false)
    private void sage$lodFog(Camera camera, int renderDistance, DeltaTracker delta, float darken, ClientLevel level,
                             CallbackInfoReturnable<FogData> cir) {
        LodClient.adjustFog(cir.getReturnValue());
    }
}
