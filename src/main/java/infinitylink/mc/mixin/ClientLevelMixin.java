package infinitylink.mc.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import infinitylink.mc.lod.LodClient;

/** 26.3 : ClientLevel.disconnect(Component) est la sortie volontaire (bouton « Déconnexion ») ; elle appelle
 *  Connection.disconnect. En tête, la connexion est encore ouverte : on y envoie lod_view 0 (contrat §9). */
@Mixin(value = ClientLevel.class, remap = false)
public abstract class ClientLevelMixin {
    @Inject(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), require = 1, remap = false)
    private void sage$lodQuit(Component reason, CallbackInfo ci) {
        LodClient.beforeQuit();
    }
}
