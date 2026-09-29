package infinitylink.mc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import infinitylink.mc.blocks.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import java.util.Optional;
import java.util.stream.Stream;
import infinitylink.core.BlocksSpec;
@Mixin(value = net.minecraft.commands.arguments.blocks.BlockStateParser.class, remap = false)
public abstract class BlockStateParserMixin {

    @Redirect(method="readBlock",at=@At(value="INVOKE",target="Lnet/minecraft/core/HolderLookup;get(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;"),require=1)
    private Optional<Holder.Reference<Block>> sage$lookup(HolderLookup<Block> lookup, ResourceKey<Block> key) {
        return BlocksSpec.stockName(key.identifier().toString()) ? Optional.empty() : lookup.get(key);
    }
    @Redirect(method="suggestItem",at=@At(value="INVOKE",target="Lnet/minecraft/core/HolderLookup;listElementIds()Ljava/util/stream/Stream;"),require=1)
    private Stream<ResourceKey<Block>> sage$suggestions(HolderLookup<Block> lookup) {
        return lookup.listElementIds().filter(k->!BlocksSpec.stockName(k.identifier().toString()));
    }

}
