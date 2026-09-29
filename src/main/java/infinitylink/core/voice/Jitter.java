package infinitylink.core.voice;

import java.util.TreeMap;

/** Tampon de gigue d'un locuteur : trames rangées par séquence, lecture après PREBUFFER trames, trame perdue remplacée
 *  par la précédente atténuée (une fois), retard borné (au-delà de MAX, les plus vieilles sont jetées). */
public final class Jitter {
    public static final int PREBUFFER = 3, MAX = 25;

    private final TreeMap<Integer, short[]> frames = new TreeMap<>();
    private int next;
    private boolean playing;
    private short[] last;
    private int concealed;
    public volatile long lastRx;

    public synchronized void put(int seq, short[] pcm, long now) {
        lastRx = now;
        if (playing && seq - next < 0) return; // trop tard
        frames.put(seq, pcm);
        while (frames.size() > MAX) frames.pollFirstEntry();
        if (playing && seq - next > MAX) next = frames.firstKey(); // saut (reprise après coupure)
    }

    /** Trame suivante à jouer, ou null (silence). */
    public synchronized short[] poll() {
        if (!playing) {
            if (frames.size() < PREBUFFER) return null;
            playing = true;
            next = frames.firstKey();
        }
        short[] f = frames.remove(next);
        next++;
        if (f != null) { last = f; concealed = 0; return f; }
        if (frames.isEmpty()) { playing = false; last = null; return null; }
        if (last != null && concealed++ == 0) {
            short[] c = new short[last.length];
            for (int i = 0; i < c.length; i++) c[i] = (short) (last[i] / 2);
            return c;
        }
        return null;
    }

    public synchronized void clear() { frames.clear(); playing = false; last = null; }
}
