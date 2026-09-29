package infinitylink.core.voice;

import infinitylink.core.Wire;

import java.util.ArrayList;
import java.util.List;

/** Capacité « voix » (1.1.2) : les trois messages Link du chat vocal, à l'octet de crates/sage_proto/src/link_voix.rs.
 *  voice (serveur -> client) : clé et session du relais UDP ; voice_peers : table complète des locuteurs ;
 *  voice_ctl (client -> serveur) : groupe, sourdine. */
public final class VoiceMsg {
    private VoiceMsg() {}

    public static final String CAP_VOIX = "voix";
    public static final String VOICE = "link/voice";
    public static final String VOICE_PEERS = "link/voice_peers";
    public static final String VOICE_CTL = "link/voice_ctl";
    public static final int VERSION = 1;
    public static final int CODEC_ADPCM16 = 1;
    public static final int MAX_PEERS = 1024;

    /** host vide : l'hôte de la connexion de jeu. */
    public record Config(int port, String host, byte[] key, byte[] session, float radius, float whisperRadius, int codec, String group) {
        public byte[] encode() {
            return new Wire.Out().varInt(VERSION).varInt(port).string(host, 255).bytes(key).bytes(session)
                    .f32(radius).f32(whisperRadius).varInt(codec).string(group, 64).toBytes();
        }
        public static Config decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            int v = in.varInt();
            if (v != VERSION) throw new Wire.DecodeException("voice version " + v);
            int port = in.varInt();
            if (port < 1 || port > 65535) throw new Wire.DecodeException("port voix " + port);
            String host = in.string(255);
            byte[] key = in.bytes(16), session = in.bytes(8);
            float r = in.f32(), w = in.f32();
            int codec = in.varInt();
            return new Config(port, host, key, session, r, w, codec, in.string(64));
        }
    }

    public record Peer(byte[] session, int eid, String name) {}

    public static byte[] encodePeers(List<Peer> peers) {
        Wire.Out o = new Wire.Out().varInt(peers.size());
        for (Peer p : peers) o.bytes(p.session()).varInt(p.eid()).string(p.name(), 64);
        return o.toBytes();
    }

    public static List<Peer> decodePeers(byte[] b) {
        Wire.In in = new Wire.In(b);
        int n = in.varInt();
        if (n < 0 || n > MAX_PEERS) throw new Wire.DecodeException("table voix de " + n);
        List<Peer> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            byte[] s = in.bytes(8);
            int eid = in.varInt();
            out.add(new Peer(s, eid, in.string(64)));
        }
        return out;
    }

    public static byte[] ctl(String action, String arg) { return new Wire.Out().string(action, 32).string(arg, 64).toBytes(); }
}
