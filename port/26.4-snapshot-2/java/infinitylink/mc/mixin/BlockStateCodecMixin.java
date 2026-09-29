package infinitylink.mc.mixin;

import infinitylink.mc.blocks.BlocksMode;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/** 26.4-snapshot-2 : les paquets de blocs (block_update, add_transient_block, particules de bloc, données d'entité)
 *  partagent Block.BLOCK_STATE_REGISTRY_STREAM_CODEC, construit par ByteBufCodecs.idMapper(IntFunction, ToIntFunction).
 *  Ses deux fonctions sont remplacées par la vue dynamique des blocs de réserve, avec refus des identifiants inconnus comme en 26.3 (en 26.3 : cinq mixins sur
 *  idMapper(IdMap)). Les lambdas ne touchent BlocksMode qu'à l'usage, jamais pendant l'initialisation de Block. */
@Mixin(value = Block.class, remap = false)
public abstract class BlockStateCodecMixin {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/ByteBufCodecs;idMapper(Ljava/util/function/IntFunction;Ljava/util/function/ToIntFunction;)Lnet/minecraft/network/codec/StreamCodec;", ordinal = 0), index = 0, require = 1, remap = false)
    private static IntFunction<BlockState> infinitylink$byId(IntFunction<BlockState> original) { return i -> BlocksMode.DYNAMIQUE.byIdOrThrow(i); }

    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/ByteBufCodecs;idMapper(Ljava/util/function/IntFunction;Ljava/util/function/ToIntFunction;)Lnet/minecraft/network/codec/StreamCodec;", ordinal = 0), index = 1, require = 1, remap = false)
    private static ToIntFunction<BlockState> infinitylink$getId(ToIntFunction<BlockState> original) { return s -> BlocksMode.DYNAMIQUE.getIdOrThrow(s); }
}
