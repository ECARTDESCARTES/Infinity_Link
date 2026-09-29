package infinitylink.mc.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import infinitylink.mc.SageBoot;

/** BuiltInRegistries.bootStrap() = createContents, freeze (invocation), validate : on s'insère juste avant freeze(),
 *  seul moment où un mod sans Fabric API peut encore ajouter une entrée aux registres intégrés
 *  (onInitializeClient arrive après le gel). Partagé avec BLOCS : SageBoot.avantGel(). */
@Mixin(value = BuiltInRegistries.class, remap = false)
public abstract class BuiltInRegistriesMixin {
    @Inject(method = "bootStrap()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/registries/BuiltInRegistries;freeze()V"),
            require = 1, remap = false)
    private static void sage$avantGel(CallbackInfo ci) {
        SageBoot.avantGel();
    }
}
