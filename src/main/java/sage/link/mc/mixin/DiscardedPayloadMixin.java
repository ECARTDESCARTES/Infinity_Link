package sage.link.mc.mixin;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sage.link.core.Msg;
import sage.link.mc.Bridge;
import sage.link.mc.SagePayload;

/** Tout id inconnu passe par CustomPacketPayload$1.findCodec -> FallbackProvider -> DiscardedPayload.codec(id, max)
 *  (Clientbound GAMEPLAY/CONFIG : max 1048576 ; Serverbound : 32767). Pour sage:* on rend notre codec d'octets bruts ;
 *  tout autre id garde le comportement vanilla. */
@Mixin(value = DiscardedPayload.class, remap = false)
public abstract class DiscardedPayloadMixin {
    @Inject(method = "codec(Lnet/minecraft/resources/Identifier;I)Lnet/minecraft/network/codec/StreamCodec;",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void sage$codec(Identifier id, int max, CallbackInfoReturnable<StreamCodec<?, ?>> cir) {
        try {
            if (id != null && Msg.NS.equals(id.getNamespace())) cir.setReturnValue(SagePayload.codec(id, max));
        } catch (Throwable t) {
            Bridge.STATE.error("codec", t);
        }
    }
}
