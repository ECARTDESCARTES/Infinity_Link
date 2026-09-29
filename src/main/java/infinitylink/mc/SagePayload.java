package infinitylink.mc;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import infinitylink.core.Msg;

/** Payload brut sage:* : l'Identifier puis le reste de la trame, sans préfixe de longueur (contrat §1).
 *  data == null : message reçu au-delà de la taille maximale (octets sautés, compté en rejet). */
public final class SagePayload implements CustomPacketPayload {
    private final CustomPacketPayload.Type<SagePayload> type;
    private final byte[] data;

    public SagePayload(Identifier id, byte[] data) {
        this.type = new CustomPacketPayload.Type<>(id);
        this.data = data;
    }

    public static SagePayload out(String path, byte[] data) {
        return new SagePayload(Identifier.fromNamespaceAndPath(Msg.NS, path), data);
    }

    @Override
    public CustomPacketPayload.Type<SagePayload> type() { return type; }

    public Identifier id() { return type.id(); }

    public byte[] data() { return data; }

    /** Remplace le codec de DiscardedPayload pour les ids sage:* (clientbound 1048576, serverbound 32767).
     *  Consomme toujours toute la trame : le décodeur vanilla refuse les octets restants. */
    public static StreamCodec<FriendlyByteBuf, CustomPacketPayload> codec(Identifier id, int max) {
        return new StreamCodec<FriendlyByteBuf, CustomPacketPayload>() {
            @Override
            public CustomPacketPayload decode(FriendlyByteBuf buf) {
                int n = buf.readableBytes();
                if (n > max) {
                    buf.skipBytes(n);
                    return new SagePayload(id, null);
                }
                byte[] b = new byte[n];
                buf.readBytes(b);
                return new SagePayload(id, b);
            }

            @Override
            public void encode(FriendlyByteBuf buf, CustomPacketPayload p) {
                if (p instanceof SagePayload s && s.data != null) buf.writeBytes(s.data);
            }
        };
    }
}
