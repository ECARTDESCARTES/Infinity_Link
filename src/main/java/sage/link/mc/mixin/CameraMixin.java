package sage.link.mc.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sage.link.mc.lod.LodClient;

/** 26.3 : Camera.update calcule depthFar = max(4 x distance de rendu, nuages) (seule écriture du champ), puis s'en
 *  sert pour le frustum d'élimination et la projection (copiée dans CameraRenderState.depthFar, lue par
 *  GameRenderer pour Projection.setupPerspective). On le repousse juste après l'écriture, tant que le lod est actif. */
@Mixin(value = Camera.class, remap = false)
public abstract class CameraMixin {
    @Shadow(remap = false) private float depthFar;

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;depthFar:F", opcode = 181 /* PUTFIELD */,
                    shift = At.Shift.AFTER, remap = false),
            require = 1, remap = false)
    private void sage$lodFar(DeltaTracker delta, CallbackInfo ci) {
        try {
            this.depthFar = LodClient.depthFar(this.depthFar);
        } catch (Throwable ignored) { }
    }
}
