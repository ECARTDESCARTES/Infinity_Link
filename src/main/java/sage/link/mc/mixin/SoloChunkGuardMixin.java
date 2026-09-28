package sage.link.mc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import sage.link.mc.blocks.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value = {net.minecraft.world.level.chunk.LevelChunk.class, net.minecraft.world.level.chunk.ProtoChunk.class}, remap = false)
public abstract class SoloChunkGuardMixin {

    @Inject(method="setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;",at=@At("HEAD"),cancellable=true,require=1)
    private void sage$guard(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> ci) {
        if(BlocksMode.forbidden(state)) { BlocksMode.guard("chunk"); ci.setReturnValue(null); }
    }

}
