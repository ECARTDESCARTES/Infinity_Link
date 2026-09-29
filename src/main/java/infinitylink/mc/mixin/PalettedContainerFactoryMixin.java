package infinitylink.mc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import infinitylink.mc.blocks.*;
import net.minecraft.core.IdMap;
import net.minecraft.world.level.block.state.BlockState;
@Mixin(value = net.minecraft.world.level.chunk.PalettedContainerFactory.class, remap = false)
public abstract class PalettedContainerFactoryMixin {

    @ModifyArg(method="create", at=@At(value="INVOKE", target="Lnet/minecraft/world/level/chunk/Strategy;createForBlockStates(Lnet/minecraft/core/IdMap;)Lnet/minecraft/world/level/chunk/Strategy;"), index=0, require=1)
    private static IdMap<BlockState> sage$view(IdMap<BlockState> original) { return BlocksMode.forLevel(); }

}
