import sage.link.core.Msg;
import sage.link.core.Wire;
import sage.link.core.lod.LodMesher;
import sage.link.core.lod.LodStore;
import sage.link.core.lod.LodWorker;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Intégration de bout en bout, Java pur, sans écran : les VRAIES tuiles envoyées par le serveur Rust (capture de
 *  tools/lod_link_dump.py sur une instance de test) passent par le décodeur, le magasin et le mailleur du mod, dans
 *  l'ordre reçu. Vérifie : décodage strict de chaque message, aucun oubli d'une tuile inconnue, disque servi couvert
 *  après chaque phase (sans trou), aucun doublon de même niveau, niveaux superposés résolus (le plus fin l'emporte),
 *  maillage sans exception ni sommet hors de sa tuile, et donne le coût GPU et le temps de maillage.
 *  Usage : java LodReplayTest <capture.bin> */
public final class LodReplayTest {
    static int ok, ko;

    static void check(String name, boolean cond, String detail) {
        if (cond) { ok++; System.out.println("OK   " + name + (detail.isEmpty() ? "" : " — " + detail)); }
        else { ko++; System.out.println("FAIL " + name + " : " + detail); }
    }

    record Rec(char kind, byte[] body) {}

    public static void main(String[] args) throws Exception {
        List<Rec> recs = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(args[0])))) {
            while (true) {
                int k;
                try { k = in.readUnsignedByte(); } catch (EOFException e) { break; }
                byte[] b = new byte[in.readInt()];
                in.readFully(b);
                recs.add(new Rec((char) k, b));
            }
        }
        LodStore store = new LodStore();
        LodWorker worker = new LodWorker(store, (w, t) -> { throw new RuntimeException(w, t); });
        int tiles = 0, forgets = 0, badTile = 0, badForget = 0, unknownForget = 0, resent = 0, phases = 0;
        long quads = 0, gpuBytes = 0, meshNs = 0, meshed = 0, outside = 0;
        int[] perLevel = new int[8];
        Map<Long, byte[]> lastBody = new HashMap<>();
        int identical = 0;
        int criard = 0, cols = 0;
        for (Rec r : recs) {
            switch (r.kind()) {
                case 'T' -> {
                    Msg.LodTile t;
                    try { t = Msg.LodTile.decode(r.body()); } catch (Wire.DecodeException e) { badTile++; continue; }
                    tiles++;
                    perLevel[t.level()]++;
                    long k = LodStore.key(t.level(), t.tx(), t.tz());
                    boolean known = store.entry(k) != null;
                    if (known) resent++;
                    if (java.util.Arrays.equals(lastBody.put(k, r.body()), r.body()) && known) identical++;
                    for (int i = 0; i < t.n() * t.n(); i++)
                        if (t.ground(i)) { cols++; if ((t.topRgb()[i] & 0xFFFFFF) == 0x7FB238) criard++; }
                    store.put(t);
                }
                case 'F' -> {
                    Msg.LodForget f;
                    try { f = Msg.LodForget.decode(r.body()); } catch (Wire.DecodeException e) { badForget++; continue; }
                    forgets++;
                    if (!f.all() && store.tile(f.level(), f.tx(), f.tz()) == null) unknownForget++;
                    store.forget(f);
                }
                case 'P' -> {
                    phases++;
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(r.body());
                    double px = bb.getDouble(), pz = bb.getDouble();
                    int served = bb.getInt();
                    // maillage de tout ce qui est sale (comme le fil de travail), chronométré
                    long t0 = System.nanoTime();
                    int n;
                    while ((n = worker.runOnce(256)) > 0) meshed += n;
                    meshNs += System.nanoTime() - t0;
                    LodMesher.Mesh m;
                    Map<Long, LodMesher.Mesh> last = new HashMap<>();
                    while ((m = worker.poll()) != null) last.put(m.key(), m);
                    long q = 0, bytes = 0;
                    for (LodMesher.Mesh mm : last.values()) {
                        q += mm.quads();
                        bytes += mm.opaque().length + mm.water().length;
                        outside += outOfTile(mm);
                    }
                    quads += q; gpuBytes += bytes;
                    // couverture : points du disque servi → au moins une tuile ; aucun point sous 2 tuiles d'un même niveau
                    int holes = 0, dup = 0, layered = 0, samples = 1500;
                    for (int i = 0; i < samples; i++) {
                        double ang = i * 2.39996, rad = served * Math.sqrt((i + 0.5) / samples) * 0.97;
                        long bx = (long) Math.floor(px + rad * Math.cos(ang)), bz = (long) Math.floor(pz + rad * Math.sin(ang));
                        int found = 0, finest = -1;
                        for (int l = 0; l <= Msg.LOD_MAX_LEVEL; l++) {
                            int span = 32 << l; // n = 32 servi
                            if (store.tile(l, (int) Math.floorDiv(bx, span), (int) Math.floorDiv(bz, span)) != null) {
                                found++;
                                if (finest < 0) finest = l;
                            }
                        }
                        if (found == 0) holes++;
                        if (found > 1) layered++;
                        // une tuile plus grossière présente au même point doit y être cachée (le plus fin l'emporte)
                        for (int l = finest + 1; finest >= 0 && l <= Msg.LOD_MAX_LEVEL; l++)
                            if (store.tile(l, (int) Math.floorDiv(bx, 32 << l), (int) Math.floorDiv(bz, 32 << l)) != null
                                    && !store.coveredFiner(l, bx, bz)) dup++;
                    }
                    check("phase " + phases + " : disque de " + served + " blocs couvert, sans trou", holes == 0,
                            holes + "/" + samples + " points sans tuile ; " + store.size() + " tuiles en magasin, joueur "
                                    + Math.round(px) + " " + Math.round(pz));
                    check("phase " + phases + " : niveaux superposés résolus (le plus fin l'emporte)", dup == 0,
                            dup + " points en double ; " + layered + "/" + samples + " points sous plusieurs niveaux");
                    check("phase " + phases + " : maillages non vides", !last.isEmpty() && q > 0,
                            last.size() + " maillages, " + q + " quads, " + bytes / 1024 + " Kio de sommets");
                }
                default -> throw new IllegalStateException("enregistrement inconnu " + r.kind());
            }
        }
        check("capture lue", !recs.isEmpty() && phases == 3, recs.size() + " enregistrements, " + phases + " phases");
        check("chaque lod_tile du serveur se décode (décodeur strict du mod)", badTile == 0 && tiles > 0, tiles + " tuiles, " + badTile + " illisibles");
        check("chaque lod_forget du serveur se décode", badForget == 0, forgets + " oublis, " + badForget + " illisibles");
        check("aucun lod_forget d'une tuile que le client n'a pas", unknownForget == 0, unknownForget + " oublis inconnus");
        StringBuilder lv = new StringBuilder();
        for (int l = 0; l < 8; l++) if (perLevel[l] > 0) lv.append(" L").append(l).append('=').append(perLevel[l]);
        check("paliers reçus", perLevel[0] > 0 && perLevel[1] > 0, lv.toString().trim() + ", " + resent + " renvois de tuiles déjà connues");
        check("aucune tuile renvoyée à l'identique (un renvoi = une surface modifiée)", identical == 0,
                identical + " renvois identiques sur " + resent + " renvois");
        check("maillage sans sommet hors de sa tuile", outside == 0, outside + " sommets hors tuile");
        check("pas de vert criard 7FB238", criard == 0, criard + "/" + cols + " colonnes");
        System.out.printf("maillage : %d tuiles maillées en %.0f ms (%.2f ms/tuile) ; somme des 3 phases : %d quads, %d Kio de sommets%n",
                meshed, meshNs / 1e6, meshed > 0 ? meshNs / 1e6 / meshed : 0.0, quads, gpuBytes / 1024);
        System.out.println("LodReplayTest : " + ok + " OK, " + ko + " FAIL");
        System.exit(ko == 0 ? 0 : 1);
    }

    /** Sommets en dehors du cube [0, span] × [minY, maxY] × [0, span] de la tuile (relatifs à son coin). */
    static long outOfTile(LodMesher.Mesh m) {
        long bad = 0;
        for (byte[] b : new byte[][]{m.opaque(), m.water()}) {
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int o = 0; o + LodMesher.VERTEX_BYTES <= b.length; o += LodMesher.VERTEX_BYTES) {
                int c = 1 << m.level(); float x = (b[o] & 0xFF) * c, y = bb.getShort(o + 2), z = (b[o + 1] & 0xFF) * c;
                if (x < 0 || z < 0 || x > m.span() || z > m.span() || y < m.minY() || y > m.maxY() || Float.isNaN(y)) bad++;
            }
        }
        return bad;
    }
}
