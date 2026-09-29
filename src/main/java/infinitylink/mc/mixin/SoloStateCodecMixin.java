package infinitylink.mc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import infinitylink.mc.blocks.*;
import com.mojang.serialization.Codec;
import net.minecraft.world.level.block.state.BlockState;
@Mixin(value = net.minecraft.world.level.chunk.PalettedContainerFactory.class, remap = false)
public abstract class SoloStateCodecMixin {

    @ModifyArg(method="create",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/PalettedContainer;codecRW(Lcom/mojang/serialization/Codec;Lnet/minecraft/world/level/chunk/Strategy;Ljava/lang/Object;)Lcom/mojang/serialization/Codec;"),index=0,require=1)
    private static Codec<BlockState> sage$codec(Codec<BlockState> codec) { return SoloGuard.codec(codec); }

}
