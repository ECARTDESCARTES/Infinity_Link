package sage.link.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sage.link.core.armor.Armures3dSpec;
import sage.link.mc.armor.Armures3d;

/** 0.3.2, armures 3D : HumanoidArmorLayer.submit appelle renderArmorPiece pour chaque emplacement (avant le test
 *  d'equippable) ; pour une pile marquée sage_armure et connue du manifeste, on dessine les parties 3D sur le modèle
 *  parent (déjà posé par LivingEntityRenderer.submit via setupAnim) et on annule le rendu vanilla de l'équipement. */
@Mixin(value = HumanoidArmorLayer.class, remap = false)
public abstract class HumanoidArmorLayerMixin {
    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void sage$armure3d(PoseStack ps, SubmitNodeCollector out, ItemStack pile, EquipmentSlot slot, int lumiere,
                               HumanoidRenderState etat, CallbackInfo ci) {
        Armures3dSpec.Entree e = Armures3d.entree(pile, slot);
        if (e == null) return;
        Object parent = ((RenderLayer<?, ?>) (Object) this).getParentModel();
        if (parent instanceof HumanoidModel<?> m && Armures3d.dessiner(m, e, ps, out, lumiere, etat)) ci.cancel();
    }
}
