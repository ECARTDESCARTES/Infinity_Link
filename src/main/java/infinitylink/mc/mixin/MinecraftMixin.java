package infinitylink.mc.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import infinitylink.mc.Bridge;

@Mixin(value = Minecraft.class, remap = false)
public abstract class MinecraftMixin {
    @Inject(method = "tick()V", at = @At("HEAD"), require = 1, remap = false)
    private void sage$tick(CallbackInfo ci) {
        Bridge.tick((Minecraft) (Object) this);
    }
}
