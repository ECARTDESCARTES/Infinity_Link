package infinitylink.mc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import infinitylink.mc.blocks.*;
import net.minecraft.core.IdMap;
import net.minecraft.world.level.block.state.BlockState;
@Mixin(value = net.minecraft.core.particles.BlockParticleOption.class, remap = false)
public abstract class BlockParticleOptionMixin {

    @ModifyArg(method="streamCodec", at=@At(value="INVOKE", target="Lnet/minecraft/network/codec/ByteBufCodecs;idMapper(Lnet/minecraft/core/IdMap;)Lnet/minecraft/network/codec/StreamCodec;", ordinal=0), index=0, require=1)
    private static IdMap<BlockState> sage$view0(IdMap<BlockState> original) { return BlocksMode.DYNAMIQUE; }

}
