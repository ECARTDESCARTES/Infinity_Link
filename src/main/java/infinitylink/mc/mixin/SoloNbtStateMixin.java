package infinitylink.mc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import infinitylink.mc.blocks.*;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value = net.minecraft.nbt.NbtUtils.class, remap = false)
public abstract class SoloNbtStateMixin {

    @Inject(method="readBlockState",at=@At("HEAD"),require=1)
    private static void sage$nbt(HolderGetter<Block> lookup, CompoundTag tag, CallbackInfoReturnable<BlockState> ci) { SoloGuard.checkTag(tag); }

}
