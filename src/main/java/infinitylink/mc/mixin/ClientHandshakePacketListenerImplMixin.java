package infinitylink.mc.mixin;

import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import infinitylink.mc.Bridge;

/** handleLoginFinished bascule la connexion en configuration puis envoie minecraft:brand et client_information :
 *  le hello part juste après, sans attendre le serveur (contrat §2). */
@Mixin(value = ClientHandshakePacketListenerImpl.class, remap = false)
public abstract class ClientHandshakePacketListenerImplMixin {
    @Shadow(remap = false) @Final private Connection connection;

    @Inject(method = "handleLoginFinished(Lnet/minecraft/network/protocol/login/ClientboundLoginFinishedPacket;)V",
            at = @At("TAIL"), require = 1, remap = false)
    private void sage$hello(ClientboundLoginFinishedPacket packet, CallbackInfo ci) {
        Bridge.sendHello(this.connection);
    }
}
