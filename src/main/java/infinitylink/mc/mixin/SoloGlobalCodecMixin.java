package infinitylink.mc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import infinitylink.mc.blocks.*;
import com.mojang.serialization.Codec;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = net.minecraft.world.level.block.state.BlockState.class, remap = false)
public abstract class SoloGlobalCodecMixin {

    @Shadow @Final @Mutable public static Codec<BlockState> CODEC;
    @Shadow @Final @Mutable public static Codec<BlockState> FULL_CODEC;
    @Shadow @Final @Mutable private static Codec<com.mojang.datafixers.util.Either<net.minecraft.world.level.block.Block,BlockState>> CONSTANT_OR_DISPATCH_CODEC;
    @Inject(method="<clinit>",at=@At("TAIL"),require=1)
    private static void sage$guard(CallbackInfo ci) {
        CODEC=SoloGuard.codec(CODEC); FULL_CODEC=SoloGuard.codec(FULL_CODEC);
        CONSTANT_OR_DISPATCH_CODEC=CONSTANT_OR_DISPATCH_CODEC.xmap(SoloGlobalCodecMixin::sage$constant,SoloGlobalCodecMixin::sage$constant);
    }
    @Unique private static com.mojang.datafixers.util.Either<net.minecraft.world.level.block.Block,BlockState> sage$constant(
            com.mojang.datafixers.util.Either<net.minecraft.world.level.block.Block,BlockState> value) {
        SoloGuard.check(value.map(net.minecraft.world.level.block.Block::defaultBlockState,s->s)); return value;
    }

}
