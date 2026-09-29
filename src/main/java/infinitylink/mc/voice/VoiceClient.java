package infinitylink.mc.voice;

import infinitylink.core.voice.Adpcm;
import infinitylink.core.voice.Jitter;
import infinitylink.core.voice.Spatial;
import infinitylink.core.voice.VoiceMsg;
import infinitylink.core.voice.VoicePacket;
import infinitylink.mc.Bridge;
import infinitylink.mc.SagePayload;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.Entity;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/** Chat vocal de proximité d'InfinityLink 1.1.2, façon Simple Voice Chat : micro (Java Sound, 16 kHz mono), codec
 *  IMA-ADPCM, trames AES-GCM vers le relais UDP du serveur (capacité « voix »), lecture stéréo spatialisée, parler en
 *  appuyant (push-to-talk) ou activation vocale, chuchotement, groupe, micro coupé, sourdine. Trois fils : réseau,
 *  micro, haut-parleurs. Toute erreur coupe la voix pour la session, le jeu continue. */
public final class VoiceClient {
    private VoiceClient() {}

    public static final String CONFIG = "infinitylink_voix.properties";
    private static final HexFormat HEX = HexFormat.of();

    // ------------------------------------------------------------------ réglages (persistés)
    public static volatile boolean enabled = true, pushToTalk = true, muted, deafened, hideIcons, groupTalk;
    public static volatile int threshold = -45, micVolume = 100, outVolume = 100;

    // ------------------------------------------------------------------ entrées (fil client)
    public static volatile boolean pttDown, whisperDown;

    // ------------------------------------------------------------------ état de session
    private static volatile VoiceMsg.Config config;
    private static volatile Map<String, VoiceMsg.Peer> peers = Map.of();
    private static final Map<String, Jitter> jitters = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> groupFrames = new ConcurrentHashMap<>();
    /** Position des locuteurs (par session), relue à chaque tick du client ; auditeur en fin de tableau. */
    private static volatile Map<String, double[]> positions = Map.of();
    private static volatile double lx, ly, lz;
    private static volatile float lyaw;
    private static volatile boolean running, talking;
    private static volatile double level = -127;
    private static volatile String status = "inactif";
    private static DatagramSocket socket;
    private static SocketAddress relay;
    private static VoicePacket crypto;
    private static Thread net, mic, spk;
    private static int seq;

    public static VoiceMsg.Config config() { return config; }
    public static boolean running() { return running; }
    public static boolean talking() { return talking; }
    public static double level() { return level; }
    public static String status() { return status; }

    // ------------------------------------------------------------------ réglages
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve(CONFIG); }

    public static void loadOptions() {
        try {
            Properties p = new Properties();
            if (Files.isRegularFile(file())) try (InputStream in = Files.newInputStream(file())) { p.load(in); }
            enabled = !"false".equals(p.getProperty("voix", "true"));
            pushToTalk = !"activation".equals(p.getProperty("mode", "appuyer"));
            muted = "true".equals(p.getProperty("micro_coupe", "false"));
            deafened = "true".equals(p.getProperty("sourdine", "false"));
            hideIcons = "true".equals(p.getProperty("masquer_icones", "false"));
            threshold = clamp(Integer.parseInt(p.getProperty("seuil_db", "-45").trim()), -80, 0);
            micVolume = clamp(Integer.parseInt(p.getProperty("volume_micro", "100").trim()), 0, 300);
            outVolume = clamp(Integer.parseInt(p.getProperty("volume_sortie", "100").trim()), 0, 300);
        } catch (Throwable t) {
            Bridge.STATE.error("options voix (valeurs par defaut)", t);
        }
    }

    public static void saveOptions() {
        try {
            Properties p = new Properties();
            p.setProperty("voix", String.valueOf(enabled));
            p.setProperty("mode", pushToTalk ? "appuyer" : "activation");
            p.setProperty("micro_coupe", String.valueOf(muted));
            p.setProperty("sourdine", String.valueOf(deafened));
            p.setProperty("masquer_icones", String.valueOf(hideIcons));
            p.setProperty("seuil_db", String.valueOf(threshold));
            p.setProperty("volume_micro", String.valueOf(micVolume));
            p.setProperty("volume_sortie", String.valueOf(outVolume));
            Files.createDirectories(file().getParent());
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "InfinityLink : chat vocal (mode appuyer|activation, seuil en dBFS, volumes en %)");
            }
        } catch (Throwable t) {
            Bridge.STATE.error("options voix (enregistrement)", t);
        }
    }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // ------------------------------------------------------------------ messages Link
    /** sage:link/voice et sage:link/voice_peers (fil réseau du jeu). */
    public static void onMessage(String path, byte[] data) {
        switch (path) {
            case VoiceMsg.VOICE -> {
                VoiceMsg.Config c = VoiceMsg.Config.decode(data);
                if (c.codec() != VoiceMsg.CODEC_ADPCM16) { status = "codec " + c.codec() + " non gere"; return; }
                VoiceMsg.Config old = config;
                config = c;
                if (old == null || !HEX.formatHex(old.session()).equals(HEX.formatHex(c.session()))) {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(VoiceClient::start);
                }
                if (c.group().isEmpty()) groupTalk = false;
            }
            case VoiceMsg.VOICE_PEERS -> {
                Map<String, VoiceMsg.Peer> m = new HashMap<>();
                for (VoiceMsg.Peer p : VoiceMsg.decodePeers(data)) m.put(HEX.formatHex(p.session()), p);
                peers = Map.copyOf(m);
                jitters.keySet().retainAll(m.keySet());
            }
            default -> { }
        }
    }

    /** Commande de l'écran vocal vers le serveur (groupe, sourdine d'un joueur). */
    public static boolean control(String action, String arg) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null || !Bridge.STATE.voiceOn()) return false;
        conn.send(new ServerboundCustomPayloadPacket(SagePayload.out(VoiceMsg.VOICE_CTL, VoiceMsg.ctl(action, arg))));
        Bridge.STATE.sent();
        return true;
    }

    // ------------------------------------------------------------------ cycle de vie
    static synchronized void start() {
        stop();
        VoiceMsg.Config c = config;
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        if (c == null || conn == null) return;
        try {
            InetAddress host;
            if (!c.host().isEmpty()) host = InetAddress.getByName(c.host());
            else if (conn.getConnection().getRemoteAddress() instanceof InetSocketAddress a) host = a.getAddress();
            else { status = "adresse du serveur inconnue"; return; }
            relay = new InetSocketAddress(host, c.port());
            crypto = new VoicePacket(c.key());
            socket = new DatagramSocket();
            socket.setSoTimeout(250);
            seq = 1;
            running = true;
            status = "connecte a " + host.getHostAddress() + ":" + c.port();
            net = daemon("infinitylink-voix-reseau", VoiceClient::netLoop);
            mic = daemon("infinitylink-voix-micro", VoiceClient::micLoop);
            spk = daemon("infinitylink-voix-sortie", VoiceClient::speakerLoop);
            System.err.println("[infinitylink] voix : relais " + relay + ", rayon " + c.radius() + " blocs");
        } catch (Throwable t) {
            fail("demarrage", t);
        }
    }

    public static synchronized void stop() {
        running = false;
        talking = false;
        if (socket != null) socket.close();
        socket = null;
        for (Thread t : new Thread[]{net, mic, spk}) if (t != null) t.interrupt();
        net = mic = spk = null;
        jitters.clear();
    }

    /** Déconnexion du jeu : tout est oublié. */
    public static void onDisconnect() {
        stop();
        config = null;
        peers = Map.of();
        groupTalk = false;
        status = "inactif";
    }

    private static Thread daemon(String name, Runnable r) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void fail(String where, Throwable t) {
        if (!running && socket == null) return;
        status = "coupe (" + where + ") : " + t.getMessage();
        Bridge.STATE.error("voix " + where + " (coupee)", t);
        chat("chat vocal coupe (" + where + ") : " + t.getMessage());
        Minecraft.getInstance().execute(VoiceClient::stop);
    }

    public static void chat(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try { mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[InfinityLink] " + text)); }
            catch (Throwable t) { Bridge.STATE.error("chat voix", t); }
        });
    }

    // ------------------------------------------------------------------ fil client : positions
    /** Tête de Minecraft.tick : position de l'auditeur et des locuteurs (entités par identifiant). */
    public static void tick(Minecraft mc) {
        if (!running || mc.player == null || mc.level == null) return;
        lx = mc.player.getX(); ly = mc.player.getEyeY(); lz = mc.player.getZ(); lyaw = mc.player.getYRot();
        Map<String, double[]> m = new HashMap<>();
        for (Map.Entry<String, VoiceMsg.Peer> e : peers.entrySet()) {
            Entity en = mc.level.getEntity(e.getValue().eid());
            if (en != null) m.put(e.getKey(), new double[]{en.getX(), en.getEyeY(), en.getZ()});
        }
        positions = m;
    }

    // ------------------------------------------------------------------ fil réseau
    private static void netLoop() {
        byte[] buf = new byte[VoicePacket.MAX];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        while (running) {
            try {
                socket.receive(dp);
                if (!relay.equals(dp.getSocketAddress())) continue;
                VoicePacket.Frame f = crypto.open(buf, dp.getLength());
                if (f == null || deafened || !enabled) continue;
                String who = HEX.formatHex(f.session());
                if (!peers.containsKey(who)) continue;
                Jitter j = jitters.computeIfAbsent(who, k -> new Jitter());
                if ((f.flags() & VoicePacket.VOICE) != 0) {
                    short[] pcm = Adpcm.decode(f.payload());
                    if (pcm != null) { j.put(f.sequence(), pcm, System.currentTimeMillis()); groupFrames.put(who, (f.flags() & VoicePacket.GROUP) != 0); }
                }
                if ((f.flags() & VoicePacket.END) != 0) j.lastRx = 0;
            } catch (SocketTimeoutException e) {
                // attente
            } catch (Throwable t) {
                if (running) fail("reseau", t);
                return;
            }
        }
    }

    private static void send(int flags, byte[] payload) throws Exception {
        DatagramSocket s = socket;
        if (s == null) return;
        byte[] session = config.session();
        byte[] d = crypto.seal(session, seq, seq, flags, payload);
        seq++;
        s.send(new DatagramPacket(d, d.length, relay));
    }

    // ------------------------------------------------------------------ fil micro
    private static void micLoop() {
        AudioFormat fmt = new AudioFormat(Adpcm.RATE, 16, 1, true, false);
        TargetDataLine line = null;
        Adpcm.State enc = new Adpcm.State();
        byte[] raw = new byte[Adpcm.FRAME * 2];
        short[] pcm = new short[Adpcm.FRAME];
        long lastKeepAlive = 0, lastVoice = 0;
        boolean wasTalking = false, micFailed = false;
        try {
            send(0, new byte[0]); // le relais apprend notre adresse UDP
            lastKeepAlive = System.currentTimeMillis();
            while (running) {
                boolean wantMic = enabled && !muted && !deafened;
                if (wantMic && line == null && !micFailed) {
                    try {
                        line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, fmt));
                        line.open(fmt, raw.length * 4);
                        line.start();
                    } catch (Throwable t) {
                        micFailed = true;
                        line = null;
                        status = "micro indisponible : " + t.getMessage();
                        chat("micro indisponible (" + t.getMessage() + ") : ecoute seule");
                    }
                }
                if (!wantMic && line != null) { line.close(); line = null; }
                long now = System.currentTimeMillis();
                if (line == null) {
                    level = -127;
                    if (wasTalking) { send(VoicePacket.END, new byte[0]); wasTalking = false; talking = false; }
                    if (now - lastKeepAlive >= 1000) { send(0, new byte[0]); lastKeepAlive = now; }
                    Thread.sleep(20);
                    continue;
                }
                int n = 0;
                while (n < raw.length && running) n += line.read(raw, n, raw.length - n);
                float gain = micVolume / 100f;
                for (int i = 0; i < pcm.length; i++) {
                    int v = (short) ((raw[2 * i] & 0xFF) | (raw[2 * i + 1] << 8));
                    pcm[i] = (short) Math.max(-32768, Math.min(32767, Math.round(v * gain)));
                }
                level = Adpcm.dbfs(pcm);
                boolean key = pttDown || whisperDown;
                if (!pushToTalk && level >= threshold) lastVoice = now;
                boolean speak = pushToTalk ? key : (key || now - lastVoice < 400);
                talking = speak;
                if (speak) {
                    VoiceMsg.Config c = config;
                    int flags = VoicePacket.VOICE;
                    if (whisperDown) flags |= VoicePacket.WHISPER;
                    if (groupTalk && c != null && !c.group().isEmpty()) flags |= VoicePacket.GROUP;
                    send(flags, Adpcm.encode(pcm, enc));
                    lastKeepAlive = now;
                    wasTalking = true;
                } else {
                    if (wasTalking) { send(VoicePacket.END, new byte[0]); wasTalking = false; enc = new Adpcm.State(); lastKeepAlive = now; }
                    if (now - lastKeepAlive >= 1000) { send(0, new byte[0]); lastKeepAlive = now; }
                }
            }
        } catch (InterruptedException e) {
            // arrêt
        } catch (Throwable t) {
            if (running) fail("micro", t);
        } finally {
            if (line != null) line.close();
            talking = false;
        }
    }

    // ------------------------------------------------------------------ fil haut-parleurs
    private static void speakerLoop() {
        AudioFormat fmt = new AudioFormat(Adpcm.RATE, 16, 2, true, false);
        SourceDataLine line = null;
        byte[] out = new byte[Adpcm.FRAME * 4];
        float[] mixL = new float[Adpcm.FRAME], mixR = new float[Adpcm.FRAME];
        try {
            line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));
            line.open(fmt, out.length * 4);
            line.start();
            while (running) {
                java.util.Arrays.fill(mixL, 0); java.util.Arrays.fill(mixR, 0);
                VoiceMsg.Config c = config;
                float radius = c == null ? 48 : c.radius(), vol = outVolume / 100f;
                Map<String, double[]> pos = positions;
                for (Map.Entry<String, Jitter> e : jitters.entrySet()) {
                    short[] f = e.getValue().poll();
                    if (f == null) continue;
                    float gl, gr;
                    double[] p = pos.get(e.getKey());
                    if (Boolean.TRUE.equals(groupFrames.get(e.getKey())) || p == null) { gl = gr = 0.8f; }
                    else { float[] g = Spatial.gains(lx, ly, lz, lyaw, p[0], p[1], p[2], radius); gl = g[0]; gr = g[1]; }
                    for (int i = 0; i < f.length; i++) { mixL[i] += f[i] * gl; mixR[i] += f[i] * gr; }
                }
                for (int i = 0; i < Adpcm.FRAME; i++) {
                    int l = Math.round(Math.max(-32768, Math.min(32767, mixL[i] * vol)));
                    int r = Math.round(Math.max(-32768, Math.min(32767, mixR[i] * vol)));
                    out[4 * i] = (byte) l; out[4 * i + 1] = (byte) (l >> 8);
                    out[4 * i + 2] = (byte) r; out[4 * i + 3] = (byte) (r >> 8);
                }
                line.write(out, 0, out.length); // bloquant : cadence de 20 ms
            }
        } catch (Throwable t) {
            if (running) fail("sortie audio", t);
        } finally {
            if (line != null) line.close();
        }
    }

    // ------------------------------------------------------------------ HUD
    /** Noms des joueurs qui parlent (trame de voix reçue il y a moins de 300 ms). */
    public static List<String> speakers() {
        List<String> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Jitter> e : jitters.entrySet()) {
            if (now - e.getValue().lastRx > 300) continue;
            VoiceMsg.Peer p = peers.get(e.getKey());
            if (p != null) out.add(p.name() + (Boolean.TRUE.equals(groupFrames.get(e.getKey())) ? " (groupe)" : ""));
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    /** Ligne d'état du micro pour le HUD. */
    public static String micLine() {
        if (!enabled) return "Voix : desactivee";
        if (deafened) return "Voix : sourdine (rien n'est entendu ni envoye)";
        if (muted) return "Voix : micro coupe";
        VoiceMsg.Config c = config;
        String dest = groupTalk && c != null && !c.group().isEmpty() ? "groupe " + c.group() : whisperDown ? "chuchotement" : "proximite";
        if (talking) return "Voix : parle (" + dest + ")";
        return "Voix : " + (pushToTalk ? "appuyer pour parler" : "activation vocale") + " (" + dest + ")";
    }
}
