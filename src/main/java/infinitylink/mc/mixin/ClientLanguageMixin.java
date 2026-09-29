package infinitylink.mc.mixin;

import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.1.2 : libellés FR/EN des touches et catégories d'InfinityLink. Sans Fabric API, les fichiers de langue d'un mod
 *  ne sont pas chargés ; les clés key.infinitylink.* et key.category.infinitylink.* sont donc résolues ici. */
@Mixin(value = ClientLanguage.class, remap = false)
public abstract class ClientLanguageMixin {
    @Inject(method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void infinitylink$text(String key, String fallback, CallbackInfoReturnable<String> cir) {
        String t = infinitylink.mc.Keys.translate(key);
        if (t != null) cir.setReturnValue(t);
    }

    @Inject(method = "has(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void infinitylink$has(String key, CallbackInfoReturnable<Boolean> cir) {
        if (infinitylink.mc.Keys.translate(key) != null) cir.setReturnValue(true);
    }
}
