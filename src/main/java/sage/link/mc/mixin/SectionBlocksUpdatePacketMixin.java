package sage.link.mc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import sage.link.mc.blocks.*;
import net.minecraft.core.IdMapper;
import net.minecraft.world.level.block.state.BlockState;
@Mixin(value = net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket.class, remap = false)
public abstract class SectionBlocksUpdatePacketMixin {

    @Redirect(method="<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at=@At(value="INVOKE",target="Lnet/minecraft/core/IdMapper;byId(I)Ljava/lang/Object;"), require=1)
    private Object sage$state(IdMapper<BlockState> registry, int id) { return BlocksMode.DYNAMIQUE.byId(id); }

}
