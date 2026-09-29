package infinitylink.core.voice;

/** Codec 1 du chat vocal : IMA-ADPCM mono, 16 kHz, trames de 20 ms (320 échantillons). Trame codée : prédicteur
 *  (int16 grand-boutiste), index de pas (1 octet), 0 (1 octet), puis 160 octets de codes 4 bits (poids faible d'abord).
 *  4 bits par échantillon : 64 kbit/s, sans bibliothèque native. */
public final class Adpcm {
    private Adpcm() {}

    public static final int RATE = 16000, FRAME = 320, BYTES = 4 + FRAME / 2;

    private static final int[] STEP = {7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449,
        494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024,
        3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818,
        18500, 20350, 22385, 24623, 27086, 29794, 32767};
    private static final int[] INDEX = {-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8};

    /** État d'un sens (encodeur ou décodeur) : prédicteur et index courants, transmis dans chaque trame. */
    public static final class State { int pred, index; }

    public static byte[] encode(short[] pcm, State st) {
        byte[] out = new byte[BYTES];
        out[0] = (byte) (st.pred >> 8); out[1] = (byte) st.pred; out[2] = (byte) st.index;
        for (int i = 0; i < FRAME; i++) {
            int step = STEP[st.index], diff = pcm[i] - st.pred, code = 0;
            if (diff < 0) { code = 8; diff = -diff; }
            int delta = step >> 3;
            if (diff >= step) { code |= 4; diff -= step; delta += step; }
            step >>= 1;
            if (diff >= step) { code |= 2; diff -= step; delta += step; }
            step >>= 1;
            if (diff >= step) { code |= 1; delta += step; }
            st.pred = clamp((code & 8) != 0 ? st.pred - delta : st.pred + delta);
            st.index = Math.max(0, Math.min(88, st.index + INDEX[code]));
            if ((i & 1) == 0) out[4 + i / 2] = (byte) code; else out[4 + i / 2] |= (byte) (code << 4);
        }
        return out;
    }

    /** null si la trame n'a pas la taille du codec. L'état porté par la trame remplace celui du décodeur (resynchro). */
    public static short[] decode(byte[] in) {
        if (in == null || in.length != BYTES) return null;
        int pred = (short) (((in[0] & 0xFF) << 8) | (in[1] & 0xFF));
        int index = Math.max(0, Math.min(88, in[2] & 0xFF));
        short[] pcm = new short[FRAME];
        for (int i = 0; i < FRAME; i++) {
            int b = in[4 + i / 2] & 0xFF, code = (i & 1) == 0 ? b & 15 : b >> 4, step = STEP[index];
            int delta = step >> 3;
            if ((code & 4) != 0) delta += step;
            if ((code & 2) != 0) delta += step >> 1;
            if ((code & 1) != 0) delta += step >> 2;
            pred = clamp((code & 8) != 0 ? pred - delta : pred + delta);
            index = Math.max(0, Math.min(88, index + INDEX[code]));
            pcm[i] = (short) pred;
        }
        return pcm;
    }

    private static int clamp(int v) { return Math.max(-32768, Math.min(32767, v)); }

    /** Niveau RMS d'une trame en dBFS (−127 pour le silence absolu) : seuil de l'activation vocale. */
    public static double dbfs(short[] pcm) {
        double s = 0;
        for (short v : pcm) s += (double) v * v;
        double rms = Math.sqrt(s / pcm.length) / 32768.0;
        return rms <= 0 ? -127 : Math.max(-127, 20 * Math.log10(rms));
    }
}
