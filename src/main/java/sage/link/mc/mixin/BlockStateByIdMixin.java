package sage.link.mc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import sage.link.mc.blocks.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value = net.minecraft.world.level.block.Block.class, remap = false)
public abstract class BlockStateByIdMixin {

    @Inject(method="stateById",at=@At("HEAD"),cancellable=true,require=1)
    private static void sage$bound(int id, CallbackInfoReturnable<BlockState> ci) {
        if(id>=BlocksMode.DYNAMIQUE.size()) ci.setReturnValue(Blocks.AIR.defaultBlockState());
    }

}
