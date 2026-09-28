package sage.link.core;

import java.util.ArrayList;
import java.util.List;

/** Les 4 messages v1 du contrat SAGE-LINK-PROTOCOLE.md §2, à l'octet (§4). Octets = corps du custom_payload
 *  après l'Identifier, sans préfixe de longueur. Octets en trop à la fin : tolérés (évolution). */
public final class Msg {
    private Msg() {}

    public static final String NS = "sage";
    public static final String HELLO = "link/hello";
    public static final String MANIFEST = "link/manifest";
    public static final String HUD = "link/hud";
    public static final String KEY = "link/key";
    public static final int PROTO = 1;
    /** §1 « 32 Kio vers le serveur », borné à 32767 = limite vanilla de ServerboundCustomPayloadPacket. */
    public static final int MAX_TO_SERVER = 32767;
    /** §1 « 1 Mio vers le client » (= limite vanilla 1048576 de ClientboundCustomPayloadPacket). */
    public static final int MAX_TO_CLIENT = 1 << 20;
    static final int MAX_STR = 32767;

    /** serveur <- client, configuration. */
    public record Hello(int proto, String modVersion, String mcVersion, List<String> caps) {
        public Hello { caps = List.copyOf(caps); }
        public byte[] encode() {
            Wire.Out o = new Wire.Out().varInt(proto).string(modVersion, 64).string(mcVersion, 32).varInt(caps.size());
            for (String c : caps) o.string(c, 64);
            return o.toBytes();
        }
        public static Hello decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            int proto = in.varInt();
            String mod = in.string(64);
            String mc = in.string(32);
            int n = in.count(1);
            List<String> caps = new ArrayList<>(n);
            for (int i = 0; i < n; i++) caps.add(in.string(64));
            return new Hello(proto, mod, mc, caps);
        }
    }

    public record KeyDef(String id, String label, int glfw) {}

    /** serveur -> client, configuration. */
    public record Manifest(int proto, String server, List<String> caps, List<KeyDef> keys) {
        public Manifest { caps = List.copyOf(caps); keys = List.copyOf(keys); }
        public byte[] encode() {
            Wire.Out o = new Wire.Out().varInt(proto).string(server).varInt(caps.size());
            for (String c : caps) o.string(c);
            o.varInt(keys.size());
            for (KeyDef k : keys) o.string(k.id()).string(k.label()).varInt(k.glfw());
            return o.toBytes();
        }
        public static Manifest decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            int proto = in.varInt();
            String server = in.string(MAX_STR);
            int n = in.count(1);
            List<String> caps = new ArrayList<>(n);
            for (int i = 0; i < n; i++) caps.add(in.string(MAX_STR));
            int k = in.count(3);
            List<KeyDef> keys = new ArrayList<>(k);
            for (int i = 0; i < k; i++) {
                String id = in.string(MAX_STR);
                String label = in.string(MAX_STR);
                keys.add(new KeyDef(id, label, in.varInt()));
            }
            return new Manifest(proto, server, caps, keys);
        }
    }

    public record HudEntry(String key, String text, float fraction, int color) {}

    /** serveur -> client, jeu. Remplacement par clé ; texte vide = retrait. */
    public record Hud(List<HudEntry> entries) {
        public Hud { entries = List.copyOf(entries); }
        public byte[] encode() {
            Wire.Out o = new Wire.Out().varInt(entries.size());
            for (HudEntry e : entries) o.string(e.key()).string(e.text()).f32(e.fraction()).i32(e.color());
            return o.toBytes();
        }
        public static Hud decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            int n = in.count(10);
            List<HudEntry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String key = in.string(MAX_STR);
                String text = in.string(MAX_STR);
                float f = in.f32();
                list.add(new HudEntry(key, text, f, in.i32()));
            }
            return new Hud(list);
        }
    }

    /** serveur <- client, jeu. Transition d'une touche du manifeste. */
    public record Key(String id, boolean down) {
        public byte[] encode() { return new Wire.Out().string(id).bool(down).toBytes(); }
        public static Key decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            String id = in.string(MAX_STR);
            return new Key(id, in.bool());
        }
    }

    // ---- Capacité « cubes » (contrat §8, CUBIC-L7) : coordonnées de cube absolues, section = format réseau 26.3.
    public static final String CAP_CUBES = "cubes";
    public static final String CUBE_VIEW = "link/cube_view";
    public static final String OFFSET = "link/offset";
    public static final String CUBE = "link/cube";
    public static final String UNLOAD_CUBE = "link/unload_cube";
    public static final String HEIGHTMAP = "link/heightmap";
    public static final int MAX_VIEW = 64, MAX_BATCH = 1024, MAX_UNLOAD = 16384;
    public static final int FLAG_EMPTY = 1, FLAG_BLOCK_LIGHT = 2, FLAG_SKY_LIGHT = 4;
    /** Coords.NO_HEIGHT de CubicChunks : aucun bloc opaque connu dans la colonne. */
    public static final int NO_HEIGHT = Integer.MIN_VALUE + 32;

    /** serveur <- client, jeu : distance de vue verticale en cubes ; 0 coupe le flux. */
    public record CubeView(int v) {
        public byte[] encode() { return new Wire.Out().varInt(v).toBytes(); }
        public static CubeView decode(byte[] b) {
            int v = new Wire.In(b).varInt();
            if (v < 0 || v > MAX_VIEW) throw new Wire.DecodeException("distance verticale " + v);
            return new CubeView(v);
        }
    }

    /** serveur -> client, jeu : fenêtre verticale. Y absolu = Y local + shift(). */
    public record Offset(int base, int minY, int sections) {
        public long shift() { return (long) base * 16 - minY; }
        public byte[] encode() { return new Wire.Out().varInt(base).varInt(minY).varInt(sections).toBytes(); }
        public static Offset decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            Offset o = new Offset(in.varInt(), in.varInt(), in.varInt());
            if (o.minY % 16 != 0 || o.minY < -2032 || o.sections < 1 || o.sections > 254 || o.minY + o.sections * 16 > 2032)
                throw new Wire.DecodeException("fenetre " + o);
            return o;
        }
    }

    public record Pos(int x, int y, int z) {}

    /** Un cube de sage:link/cube. section vide = cube vide ; lumière null = absente (bloc 0, ciel 15). */
    public record CubeData(Pos pos, byte[] section, byte[] blockLight, byte[] skyLight) {
        public int flags() {
            return (section.length == 0 ? FLAG_EMPTY : 0) | (blockLight != null ? FLAG_BLOCK_LIGHT : 0) | (skyLight != null ? FLAG_SKY_LIGHT : 0);
        }
    }

    /** serveur -> client, jeu : lot de cubes en colonnes (positions, drapeaux, sections, lumière de bloc, lumière du ciel). */
    public record Cubes(List<CubeData> cubes) {
        public Cubes { cubes = List.copyOf(cubes); }
        public byte[] encode() {
            Wire.Out o = new Wire.Out().varInt(cubes.size());
            for (CubeData c : cubes) o.varInt(c.pos().x()).varInt(c.pos().y()).varInt(c.pos().z());
            for (CubeData c : cubes) o.u8(c.flags());
            for (CubeData c : cubes) o.bytes(c.section());
            for (CubeData c : cubes) if (c.blockLight() != null) o.bytes(c.blockLight());
            for (CubeData c : cubes) if (c.skyLight() != null) o.bytes(c.skyLight());
            return o.toBytes();
        }
        public static Cubes decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            int n = in.varInt();
            if (n < 1 || n > MAX_BATCH || n * 4L > in.remaining()) throw new Wire.DecodeException("lot de " + n + " cubes");
            Pos[] pos = new Pos[n];
            for (int i = 0; i < n; i++) pos[i] = new Pos(in.varInt(), in.varInt(), in.varInt());
            int[] fl = new int[n];
            for (int i = 0; i < n; i++) {
                fl[i] = in.u8();
                if ((fl[i] & ~(FLAG_EMPTY | FLAG_BLOCK_LIGHT | FLAG_SKY_LIGHT)) != 0) throw new Wire.DecodeException("drapeaux " + fl[i]);
            }
            byte[][] sec = new byte[n][], bl = new byte[n][], sl = new byte[n][];
            for (int i = 0; i < n; i++) {
                if ((fl[i] & FLAG_EMPTY) != 0) { sec[i] = new byte[0]; continue; }
                int start = in.position();
                skipSection(in);
                sec[i] = in.since(start);
            }
            for (int i = 0; i < n; i++) if ((fl[i] & FLAG_BLOCK_LIGHT) != 0) bl[i] = in.bytes(2048);
            for (int i = 0; i < n; i++) if ((fl[i] & FLAG_SKY_LIGHT) != 0) sl[i] = in.bytes(2048);
            List<CubeData> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(new CubeData(pos[i], sec[i], bl[i], sl[i]));
            return new Cubes(list);
        }
    }

    /** Section réseau 26.3 : i16 non-air, i16 fluides, conteneur des états (indirect <= 8 bits), des biomes (<= 3 bits). */
    public static void skipSection(Wire.In in) {
        in.skip(4);
        skipContainer(in, 4096, 8);
        skipContainer(in, 64, 3);
    }

    static void skipContainer(Wire.In in, int entries, int maxIndirect) {
        int bits = in.u8();
        if (bits == 0) { in.varInt(); return; }
        if (bits > 32) throw new Wire.DecodeException(bits + " bits par entree");
        if (bits <= maxIndirect) {
            int n = in.varInt();
            if (n < 1 || n > 256) throw new Wire.DecodeException("palette de " + n);
            for (int i = 0; i < n; i++) in.varInt();
        }
        int per = 64 / bits;
        in.skip(((entries + per - 1) / per) * 8);
    }

    /** serveur -> client, jeu : cubes à oublier. */
    public record Unload(List<Pos> cubes) {
        public Unload { cubes = List.copyOf(cubes); }
        public byte[] encode() {
            Wire.Out o = new Wire.Out().varInt(cubes.size());
            for (Pos p : cubes) o.varInt(p.x()).varInt(p.y()).varInt(p.z());
            return o.toBytes();
        }
        public static Unload decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            int n = in.varInt();
            if (n < 0 || n > MAX_UNLOAD || n * 3L > in.remaining()) throw new Wire.DecodeException(n + " cubes a oublier");
            List<Pos> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(new Pos(in.varInt(), in.varInt(), in.varInt()));
            return new Unload(l);
        }
    }

    public record Height(int index, int y) {}

    /** serveur -> client, jeu : Y absolu du plus haut bloc opaque, index z<<4|x. */
    public record HeightMap(int cx, int cz, List<Height> entries) {
        public HeightMap { entries = List.copyOf(entries); }
        public byte[] encode() {
            Wire.Out o = new Wire.Out().varInt(cx).varInt(cz).varInt(entries.size());
            for (Height h : entries) o.u8(h.index()).varInt(h.y());
            return o.toBytes();
        }
        public static HeightMap decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            int cx = in.varInt(), cz = in.varInt(), n = in.varInt();
            if (n < 0 || n > 256) throw new Wire.DecodeException(n + " hauteurs");
            List<Height> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(new Height(in.u8(), in.varInt()));
            return new HeightMap(cx, cz, l);
        }
    }

    // ---- Capacité « lod » (contrat §9, CUBIC-L6) : rendu lointain natif façon Distant Horizons.
    public static final String LOD_VIEW = "link/lod_view";
    public static final String LOD_TILE = "link/lod_tile";
    public static final String LOD_FORGET = "link/lod_forget";
    public static final int LOD_MAX_VIEW = 512, LOD_MAX_LEVEL = 7, LOD_MAX_N = 64;
    public static final int LOD_GROUND = 1, LOD_WATER = 2;
    /** Bornes de sécurité (pas dans le contrat) : hauteurs relatives et flancs absurdes rejetés. */
    static final int LOD_MAX_REL = 1 << 16;

    /** serveur <- client, jeu : distance lod en chunks (0..512) ; 0 coupe le flux. */
    public record LodView(int d) {
        public byte[] encode() { return new Wire.Out().varInt(d).toBytes(); }
        public static LodView decode(byte[] b) {
            int d = new Wire.In(b).varInt();
            if (d < 0 || d > LOD_MAX_VIEW) throw new Wire.DecodeException("distance lod " + d);
            return new LodView(d);
        }
    }

    /** serveur -> client, jeu : tuile n×n de colonnes (index z·n + x). Couleurs 0xRRGGBB (albédo, sans ombrage).
     *  Colonne sans sol : top, depth, topRgb, sideRgb à 0 ; sans eau : wtop, waterRgb à 0. */
    public record LodTile(int level, int tx, int tz, int yBase, int n, byte[] flags, int[] top, int[] depth,
                          int[] topRgb, int[] sideRgb, int[] wtop, int[] waterRgb) {
        /** Taille d'une colonne en blocs (2^level). */
        public int cell() { return 1 << level; }
        /** Côté de la tuile en blocs. */
        public int span() { return n << level; }
        public long originX() { return (long) tx * span(); }
        public long originZ() { return (long) tz * span(); }
        public boolean ground(int i) { return (flags[i] & LOD_GROUND) != 0; }
        public boolean water(int i) { return (flags[i] & LOD_WATER) != 0; }

        public byte[] encode() {
            Wire.Out o = new Wire.Out().varInt(level).varInt(tx).varInt(tz).varInt(yBase).u8(n);
            for (int i = 0; i < n * n; i++) {
                int f = flags[i] & 0xFF;
                o.u8(f);
                if ((f & LOD_GROUND) != 0) { o.varInt(top[i]).varInt(depth[i]); rgb(o, topRgb[i]); rgb(o, sideRgb[i]); }
                if ((f & LOD_WATER) != 0) { o.varInt(wtop[i]); rgb(o, waterRgb[i]); }
            }
            return o.toBytes();
        }

        public static LodTile decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            int level = in.varInt();
            if (level < 0 || level > LOD_MAX_LEVEL) throw new Wire.DecodeException("niveau lod " + level);
            int tx = in.varInt(), tz = in.varInt(), yBase = in.varInt();
            int n = in.u8();
            if (n < 1 || n > LOD_MAX_N) throw new Wire.DecodeException("tuile de " + n + " colonnes");
            int c = n * n;
            if (c > in.remaining()) throw new Wire.DecodeException("tuile tronquee");
            byte[] fl = new byte[c];
            int[] top = new int[c], depth = new int[c], trgb = new int[c], srgb = new int[c], wtop = new int[c], wrgb = new int[c];
            for (int i = 0; i < c; i++) {
                int f = in.u8();
                if ((f & ~(LOD_GROUND | LOD_WATER)) != 0) throw new Wire.DecodeException("drapeaux lod " + f);
                fl[i] = (byte) f;
                if ((f & LOD_GROUND) != 0) {
                    top[i] = rel(in.varInt(), "top");
                    depth[i] = in.varInt();
                    if (depth[i] < 1 || depth[i] > LOD_MAX_REL) throw new Wire.DecodeException("depth " + depth[i]);
                    trgb[i] = rgb(in);
                    srgb[i] = rgb(in);
                }
                if ((f & LOD_WATER) != 0) {
                    wtop[i] = rel(in.varInt(), "wtop");
                    wrgb[i] = rgb(in);
                }
            }
            return new LodTile(level, tx, tz, yBase, n, fl, top, depth, trgb, srgb, wtop, wrgb);
        }

        static int rel(int v, String what) {
            if (v < -LOD_MAX_REL || v > LOD_MAX_REL) throw new Wire.DecodeException(what + " " + v);
            return v;
        }
        static void rgb(Wire.Out o, int c) { o.u8(c >>> 16).u8(c >>> 8).u8(c); }
        static int rgb(Wire.In in) { return (in.u8() << 16) | (in.u8() << 8) | in.u8(); }
    }

    /** serveur -> client, jeu : tuile à oublier ; level = -1 : tout oublier. */
    public record LodForget(int level, int tx, int tz) {
        public boolean all() { return level == -1; }
        public byte[] encode() { return new Wire.Out().varInt(level).varInt(tx).varInt(tz).toBytes(); }
        public static LodForget decode(byte[] b) {
            Wire.In in = new Wire.In(b);
            LodForget f = new LodForget(in.varInt(), in.varInt(), in.varInt());
            if (f.level < -1 || f.level > LOD_MAX_LEVEL) throw new Wire.DecodeException("niveau lod " + f.level);
            return f;
        }
    }
}
