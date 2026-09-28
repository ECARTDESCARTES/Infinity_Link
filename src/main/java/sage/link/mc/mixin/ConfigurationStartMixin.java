package sage.link.mc.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import sage.link.mc.blocks.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl.class, remap = false)
public abstract class ConfigurationStartMixin {

    @Unique private Connection sage$owner;
    @Inject(method="<init>",at=@At("TAIL"),require=1)
    private void sage$begin(Minecraft mc, Connection connection, CommonListenerCookie cookie, CallbackInfo ci) {
        sage$owner=connection; BlocksMode.begin(connection);
    }
    @Inject(method="handleConfigurationFinished",at=@At("TAIL"),require=1)
    private void sage$finish(CallbackInfo ci) { BlocksMode.finish(sage$owner); }

}
