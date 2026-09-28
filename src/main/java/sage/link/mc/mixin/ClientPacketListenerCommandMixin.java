package sage.link.mc.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sage.link.mc.assets.AssetsLink;

/** Commandes clientes « /lk ... » (capacité assets) : interceptées avant l'envoi au serveur ; toute autre commande passe. */
@Mixin(value = ClientPacketListener.class, remap = false)
public abstract class ClientPacketListenerCommandMixin {
    @Inject(method = "sendCommand(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void sage$command(String command, CallbackInfo ci) {
        try {
            if (AssetsLink.onCommand(command)) ci.cancel();
        } catch (Throwable t) {
            sage.link.mc.Bridge.STATE.error("commande", t);
        }
    }
}
