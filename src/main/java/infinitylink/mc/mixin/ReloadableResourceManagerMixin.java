package infinitylink.mc.mixin;

import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import infinitylink.mc.armor.Armures3d;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** 0.3.2, armures 3D : chaque rechargement des packs (F3+T, pack serveur) invalide le manifeste armures_3d.json. */
@Mixin(value = ReloadableResourceManager.class, remap = false)
public abstract class ReloadableResourceManagerMixin {
    @Inject(method = "createReload(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)Lnet/minecraft/server/packs/resources/ReloadInstance;",
            at = @At("RETURN"), require = 1, remap = false)
    private void sage$rechargement(Executor a, Executor b, CompletableFuture<?> c, List<?> packs,
                                   CallbackInfoReturnable<ReloadInstance> cir) {
        Armures3d.rechargement();
    }
}
