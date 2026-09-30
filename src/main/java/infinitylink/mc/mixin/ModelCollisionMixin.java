package infinitylink.mc.mixin;

import infinitylink.mc.scenes.ModelCollisionClient;
import net.minecraft.world.level.CommonLevelAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Inclut les formes dans les déplacements, marches, plafonds et vérifications noCollision natifs. */
@Mixin(value=CommonLevelAccessor.class,remap=false)
public interface ModelCollisionMixin {
    @Inject(method="getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",at=@At("RETURN"),cancellable=true,require=1,remap=false)
    private void infinitylink$modelPhysics(Entity entity,AABB box,CallbackInfoReturnable<List<VoxelShape>> ci){
        ci.setReturnValue(ModelCollisionClient.append(this,entity,box,ci.getReturnValue()));
    }
}
