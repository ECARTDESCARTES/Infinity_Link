package infinitylink.mc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import infinitylink.mc.blocks.*;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = net.minecraft.world.level.levelgen.flat.FlatLayerInfo.class, remap = false)
public abstract class SoloFlatLayerMixin {

    @Inject(method="<init>(ILnet/minecraft/world/level/block/Block;)V",at=@At("TAIL"),require=1)
    private void sage$layer(int height,Block block,CallbackInfo ci) { SoloGuard.check(block.defaultBlockState()); }

}
