package sage.link.mc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import sage.link.mc.blocks.*;
import net.minecraft.core.IdMap;
import net.minecraft.world.level.block.state.BlockState;
@Mixin(value = net.minecraft.network.protocol.game.ClientboundAddTransientBlockPacket.class, remap = false)
public abstract class AddTransientBlockPacketMixin {

    @ModifyArg(method="<init>(Lnet/minecraft/network/RegistryFriendlyByteBuf;)V", at=@At(value="INVOKE", target="Lnet/minecraft/network/codec/ByteBufCodecs;idMapper(Lnet/minecraft/core/IdMap;)Lnet/minecraft/network/codec/StreamCodec;", ordinal=0), index=0, require=1)
    private IdMap<BlockState> sage$view0(IdMap<BlockState> original) { return BlocksMode.DYNAMIQUE; }

    @ModifyArg(method="write(Lnet/minecraft/network/RegistryFriendlyByteBuf;)V", at=@At(value="INVOKE", target="Lnet/minecraft/network/codec/ByteBufCodecs;idMapper(Lnet/minecraft/core/IdMap;)Lnet/minecraft/network/codec/StreamCodec;", ordinal=0), index=0, require=1)
    private IdMap<BlockState> sage$view1(IdMap<BlockState> original) { return BlocksMode.DYNAMIQUE; }

}
