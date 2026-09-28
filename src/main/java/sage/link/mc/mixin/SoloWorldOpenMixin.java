package sage.link.mc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import sage.link.mc.blocks.*;
import java.nio.file.Path;
import java.io.IOException;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.storage.LevelStorageSource;
@Mixin(value = net.minecraft.world.level.storage.LevelStorageSource.class, remap = false)
public abstract class SoloWorldOpenMixin {

    @Shadow @Final private Path baseDir;
    @Inject(method={"createAccess","validateAndCreateAccess"},at=@At("HEAD"),require=1)
    private void sage$preflight(String name, CallbackInfoReturnable<LevelStorageSource.LevelStorageAccess> ci) throws IOException {
        SoloGuard.scanWorld(baseDir.resolve(name));
    }

}
