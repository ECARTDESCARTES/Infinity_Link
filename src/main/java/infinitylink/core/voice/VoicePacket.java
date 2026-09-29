package infinitylink.core.voice;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Trame UDP du relais vocal (DOCS/VOIX-PROTOCOLE.md §2) : version(1) · session(8) · séquence(4) en clair (= AAD),
 *  puis AES-128-GCM sur horodatage(4) · drapeaux(1) · charge utile, étiquette de 16 octets. Nonce = session · séquence.
 *  Le relais rechiffre chaque trame sous la clé du destinataire en gardant la session de l'émetteur : c'est elle qui
 *  identifie le locuteur. */
public final class VoicePacket {
    public static final int VERSION = 1, HEADER = 13, TAG = 16, MIN = HEADER + 5 + TAG, MAX = 1200;
    public static final int VOICE = 1, WHISPER = 2, GROUP = 4, END = 8;

    public record Frame(byte[] session, int sequence, int timestamp, int flags, byte[] payload) {}

    private final SecretKeySpec key;

    public VoicePacket(byte[] key16) {
        if (key16.length != 16) throw new IllegalArgumentException("clé voix de " + key16.length + " octets");
        key = new SecretKeySpec(key16, "AES");
    }

    private static byte[] nonce(byte[] session, int seq) {
        return ByteBuffer.allocate(12).put(session, 0, 8).putInt(seq).array();
    }

    /** Datagramme complet prêt à envoyer. */
    public byte[] seal(byte[] session, int seq, int timestamp, int flags, byte[] payload) throws Exception {
        byte[] aad = ByteBuffer.allocate(HEADER).put((byte) VERSION).put(session, 0, 8).putInt(seq).array();
        byte[] inner = ByteBuffer.allocate(5 + payload.length).putInt(timestamp).put((byte) flags).put(payload).array();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce(session, seq)));
        c.updateAAD(aad);
        byte[] body = c.doFinal(inner);
        byte[] out = Arrays.copyOf(aad, HEADER + body.length);
        System.arraycopy(body, 0, out, HEADER, body.length);
        return out;
    }

    /** null : taille, version ou étiquette invalide. */
    public Frame open(byte[] d, int len) {
        if (len < MIN || len > MAX || d[0] != VERSION) return null;
        try {
            byte[] session = Arrays.copyOfRange(d, 1, 9);
            int seq = ByteBuffer.wrap(d, 9, 4).getInt();
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce(session, seq)));
            c.updateAAD(d, 0, HEADER);
            byte[] inner = c.doFinal(d, HEADER, len - HEADER);
            ByteBuffer b = ByteBuffer.wrap(inner);
            int ts = b.getInt();
            int flags = b.get() & 0xFF;
            return new Frame(session, seq, ts, flags, Arrays.copyOfRange(inner, 5, inner.length));
        } catch (Exception e) {
            return null;
        }
    }
}
