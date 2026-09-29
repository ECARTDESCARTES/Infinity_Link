package infinitylink.mc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import infinitylink.mc.blocks.*;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.world.level.block.Block;
import java.util.Spliterator;
import java.util.stream.StreamSupport;
@Mixin(value = net.minecraft.world.level.levelgen.DebugLevelSource.class, remap = false)
public abstract class DebugLevelSourceMixin {

    @Redirect(method="<clinit>",at=@At(value="INVOKE",target="Lnet/minecraft/core/DefaultedRegistry;spliterator()Ljava/util/Spliterator;"),require=1)
    private static Spliterator<Block> sage$vanilla(DefaultedRegistry<Block> registry) {
        return StreamSupport.stream(registry.spliterator(),false).filter(b->!(b instanceof SageBlock)).spliterator();
    }

}
