package sage.link.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sage.link.mc.armor.Armures3d;

/** 0.3.2, armures 3D : un casque 3D sans asset d'équipement est aussi dessiné par la couche de tête vanilla (voie
 *  « head » du pack, lisible sans le mod) ; quand SAGE Link le dessine en 3D, on retire ce doublon. */
@Mixin(value = CustomHeadLayer.class, remap = false)
public abstract class CustomHeadLayerMixin {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;FF)V",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void sage$casque3d(PoseStack ps, SubmitNodeCollector out, int lumiere, LivingEntityRenderState etat,
                               float yRot, float xRot, CallbackInfo ci) {
        if (etat instanceof HumanoidRenderState h && Armures3d.entree(h.headEquipment, EquipmentSlot.HEAD) != null) ci.cancel();
    }
}
