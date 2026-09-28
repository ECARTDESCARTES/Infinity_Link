package sage.link.mc.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sage.link.mc.Bridge;

/** Base commune de ClientConfigurationPacketListenerImpl et ClientPacketListener (aucun des deux ne redéfinit
 *  handleCustomPayload(ClientboundCustomPayloadPacket) ; la config appelle super.onDisconnect en tête). */
@Mixin(value = ClientCommonPacketListenerImpl.class, remap = false)
public abstract class ClientCommonPacketListenerImplMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    protected net.minecraft.network.Connection connection;
    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void sage$payload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (Bridge.onClientbound(connection, packet.payload())) ci.cancel();
    }

    @Inject(method = "onDisconnect(Lnet/minecraft/network/DisconnectionDetails;)V",
            at = @At("HEAD"), require = 1, remap = false)
    private void sage$disconnect(DisconnectionDetails details, CallbackInfo ci) {
        sage.link.mc.blocks.BlocksMode.disconnect(connection);
        Bridge.onDisconnect();
    }
}
