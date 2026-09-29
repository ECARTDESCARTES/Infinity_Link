import infinitylink.core.KeyMap;
import infinitylink.core.LinkState;
import infinitylink.core.Msg;
import infinitylink.core.Wire;

import java.util.HexFormat;
import java.util.List;

/** Java pur : les 4 messages contre les octets de référence du contrat §4, puis le flux d'état et le JSON §3. */
public final class CodecTest {
    static int ok, ko;

    static byte[] hex(String s) { return HexFormat.of().parseHex(s.replace(" ", "")); }
    static String hex(byte[] b) { return HexFormat.ofDelimiter(" ").withUpperCase().formatHex(b); }

    static void check(String name, boolean cond, String detail) {
        if (cond) { ok++; System.out.println("OK   " + name); }
        else { ko++; System.out.println("FAIL " + name + " : " + detail); }
    }

    static void bytes(String name, byte[] got, String ref) {
        byte[] want = hex(ref);
        check(name, java.util.Arrays.equals(got, want), "attendu " + hex(want) + " obtenu " + hex(got));
    }

    static final String HELLO = "01 05 30 2E 31 2E 30 04 32 36 2E 33 03 03 68 75 64 04 6B 65 79 73 05 73 74 61 74 65";
    static final String MANIFEST = "01 06 53 41 47 45 20 5A 02 03 68 75 64 04 6B 65 79 73 01 09 72 65 63 68 61 72 67 65 72 09 52 65 63 68 61 72 67 65 72 52";
    static final String HUD = "01 04 73 6F 69 66 0A 53 6F 69 66 20 31 35 2F 32 30 3F 40 00 00 FF 33 55 FF";
    static final String KEY = "09 72 65 63 68 61 72 67 65 72 01";
    /** hello réellement envoyé par le mod 0.2.0 (même format §4, version "0.2.0"). */
    /** Hello §4 attendu pour une version donnée (proto 1, mc 26.3, capacités hud/keys/state). */
    static String helloRef(String v) {
        StringBuilder b = new StringBuilder(String.format("01 %02X", v.length()));
        for (char c : v.toCharArray()) b.append(String.format(" %02X", (int) c));
        return b + " 04 32 36 2E 33 03 03 68 75 64 04 6B 65 79 73 05 73 74 61 74 65";
    }
    static final String HELLO_020 = "01 05 30 2E 32 2E 30 04 32 36 2E 33 03 03 68 75 64 04 6B 65 79 73 05 73 74 61 74 65";

    static final String CUBE_VIEW_REF = "08";
    static final String OFFSET_REF = "FC FF FF FF 0F C0 FF FF FF 0F 18";
    static final String CUBE_REF = "02 00 05 00 FF FF FF FF 0F FC FF FF FF 0F 02 01 00 10 00 00 00 00 01 00 00";
    static final String UNLOAD_REF = "02 01 02 03 FF FF FF FF 0F 00 00";
    static final String HEIGHTMAP_REF = "03 FE FF FF FF 0F 02 00 40 FF A0 80 80 80 08";

    // ---- §9 capacité « lod »
    static final String LOD_VIEW_REF = "40";
    static final String LOD_TILE_REF = "02 FF FF FF FF 0F 03 3C 02 01 04 03 5E 9D 34 86 60 43 02 02 3F 76 E4 03 01 01 80 80 80 70 70 70 03 3F 76 E4 00";
    static final String LOD_FORGET_REF = "02 FF FF FF FF 0F 03";
    static final String LOD_FORGET_ALL_REF = "FF FF FF FF 0F 00 00";

    static Msg.LodTile refTile() {
        return new Msg.LodTile(2, -1, 3, 60, 2,
                new byte[]{1, 2, 3, 0},
                new int[]{4, 0, 1, 0}, new int[]{3, 0, 1, 0},
                new int[]{0x5E9D34, 0, 0x808080, 0}, new int[]{0x866043, 0, 0x707070, 0},
                new int[]{0, 2, 3, 0}, new int[]{0, 0x3F76E4, 0x3F76E4, 0});
    }

    static void lod() {
        bytes("lod_view encode = §9", new Msg.LodView(64).encode(), LOD_VIEW_REF);
        check("lod_view decode(§9)", Msg.LodView.decode(hex(LOD_VIEW_REF)).equals(new Msg.LodView(64)), "");
        bytes("lod_view(128) = 80 01", new Msg.LodView(128).encode(), "80 01");
        bytes("lod_tile encode = §9", refTile().encode(), LOD_TILE_REF);
        Msg.LodTile t = Msg.LodTile.decode(hex(LOD_TILE_REF));
        bytes("lod_tile decode(§9) re-encode", t.encode(), LOD_TILE_REF);
        check("lod_tile decode(§9) champs", t.level() == 2 && t.tx() == -1 && t.tz() == 3 && t.yBase() == 60 && t.n() == 2
                && t.ground(0) && !t.water(0) && t.top()[0] == 4 && t.depth()[0] == 3 && t.topRgb()[0] == 0x5E9D34 && t.sideRgb()[0] == 0x866043
                && !t.ground(1) && t.water(1) && t.wtop()[1] == 2 && t.waterRgb()[1] == 0x3F76E4
                && t.ground(2) && t.water(2) && t.top()[2] == 1 && t.wtop()[2] == 3 && t.flags()[3] == 0
                && t.cell() == 4 && t.span() == 8 && t.originX() == -8 && t.originZ() == 24, "" + t);
        bytes("lod_forget encode = §9", new Msg.LodForget(2, -1, 3).encode(), LOD_FORGET_REF);
        check("lod_forget decode(§9)", Msg.LodForget.decode(hex(LOD_FORGET_REF)).equals(new Msg.LodForget(2, -1, 3)), "");
        bytes("lod_forget(tout) encode = §9", new Msg.LodForget(-1, 0, 0).encode(), LOD_FORGET_ALL_REF);
        check("lod_forget(tout) decode(§9)", Msg.LodForget.decode(hex(LOD_FORGET_ALL_REF)).all(), "");
        int bad = 0;
        byte[] ref = hex(LOD_TILE_REF);
        for (int n = 0; n < ref.length; n++) {
            try { Msg.LodTile.decode(java.util.Arrays.copyOf(ref, n)); } catch (Wire.DecodeException e) { bad++; }
        }
        check("lod_tile tronque : toujours rejete", bad == ref.length, bad + "/" + ref.length);
        byte[] badFlag = ref.clone(); badFlag[9] = 4;
        byte[] badDepth = ref.clone(); badDepth[11] = 0;
        byte[] badLevel = ref.clone(); badLevel[0] = 8;
        byte[] badN = ref.clone(); badN[8] = 0;
        int rej = 0;
        for (byte[] b : new byte[][]{badFlag, badDepth, badLevel, badN, hex("81 04"), hex("08 00 00")}) {
            try { if (b.length == 2) Msg.LodView.decode(b); else if (b.length == 3) Msg.LodForget.decode(b); else Msg.LodTile.decode(b); }
            catch (Wire.DecodeException e) { rej++; }
        }
        check("lod : drapeau 4, depth 0, niveau 8, n 0, vue 513, forget niveau 8 rejetes", rej == 6, rej + "/6");

        // Flux : tuile sans lod_view rejetée ; lod_view 64 ; tuile ; forget ; forget(tout) ; JSON ; coupure.
        LinkState s = new LinkState();
        s.beginHello("26.3"); s.sent();
        s.onInbound(Msg.MANIFEST, hex(MANIFEST));
        check("sans lod : pas de section lod dans le JSON", !s.json().contains("\"lod\""), s.json());
        s.onInbound(Msg.LOD_TILE, hex(LOD_TILE_REF));
        check("lod_tile sans lod_view rejete", s.rejets() == 1 && s.lod.size() == 0, s.json());
        bytes("lodView(64) = §9", s.lodView(64), LOD_VIEW_REF);
        s.onInbound(Msg.LOD_TILE, hex(LOD_TILE_REF));
        s.onInbound(Msg.LOD_TILE, new Msg.LodTile(2, 0, 3, 60, 2, new byte[4], new int[4], new int[4], new int[4], new int[4], new int[4], new int[4]).encode());
        check("2 tuiles connues", s.lod.size() == 2 && s.lod.tile(2, -1, 3) != null && s.lod.tile(2, 0, 3) != null, s.json());
        s.onInbound(Msg.LOD_FORGET, hex(LOD_FORGET_REF));
        check("lod_forget retire la tuile", s.lod.size() == 1 && s.lod.tile(2, -1, 3) == null && s.lod.pollRemoved() != null, s.json());
        String j = s.json();
        check("JSON lod", j.contains("\"lod\":{\"view\":64,\"tiles\":1,\"rx\":2,\"forget\":1,"), j);
        s.onInbound(Msg.LOD_FORGET, hex(LOD_FORGET_ALL_REF));
        check("lod_forget(tout) vide le magasin", s.lod.size() == 0, s.json());
        s.onInbound(Msg.LOD_TILE, hex(LOD_TILE_REF));
        bytes("lodView(0) = 00", s.lodView(0), "00");
        check("lod_view 0 : tout oublie", s.lod.size() == 0 && s.lodViewSent() == 0, s.json());
        s.lodView(64);
        s.onInbound(Msg.LOD_TILE, hex(LOD_TILE_REF));
        s.disconnected();
        check("deconnexion : lod oublie", s.lod.size() == 0 && s.lodViewSent() == 0 && !s.json().contains("\"lod\""), s.json());
        // clés du magasin : aller-retour signé
        long k = infinitylink.core.lod.LodStore.key(7, -123456, 98765);
        check("cle lod aller-retour", infinitylink.core.lod.LodStore.keyLevel(k) == 7 && infinitylink.core.lod.LodStore.keyX(k) == -123456
                && infinitylink.core.lod.LodStore.keyZ(k) == 98765, Long.toHexString(k));
    }

    static void cubes() {
        Msg.CubeView cv = new Msg.CubeView(8);
        bytes("cube_view encode = §8", cv.encode(), CUBE_VIEW_REF);
        check("cube_view decode(§8)", Msg.CubeView.decode(hex(CUBE_VIEW_REF)).equals(cv), "");
        Msg.Offset off = new Msg.Offset(-4, -64, 24);
        bytes("offset encode = §8", off.encode(), OFFSET_REF);
        check("offset decode(§8), shift 0", Msg.Offset.decode(hex(OFFSET_REF)).equals(off) && off.shift() == 0, "");
        Msg.Cubes cubes = new Msg.Cubes(List.of(
                new Msg.CubeData(new Msg.Pos(0, 5, 0), new byte[0], null, null),
                new Msg.CubeData(new Msg.Pos(-1, -4, 2), hex("10 00 00 00 00 01 00 00"), null, null)));
        bytes("cube encode = §8", cubes.encode(), CUBE_REF);
        bytes("cube decode(§8) re-encode", Msg.Cubes.decode(hex(CUBE_REF)).encode(), CUBE_REF);
        Msg.Unload un = new Msg.Unload(List.of(new Msg.Pos(1, 2, 3), new Msg.Pos(-1, 0, 0)));
        bytes("unload_cube encode = §8", un.encode(), UNLOAD_REF);
        check("unload_cube decode(§8)", Msg.Unload.decode(hex(UNLOAD_REF)).equals(un), "");
        Msg.HeightMap hm = new Msg.HeightMap(3, -2, List.of(new Msg.Height(0, 64), new Msg.Height(255, Msg.NO_HEIGHT)));
        bytes("heightmap encode = §8", hm.encode(), HEIGHTMAP_REF);
        check("heightmap decode(§8)", Msg.HeightMap.decode(hex(HEIGHTMAP_REF)).equals(hm), "");
        // section indirecte 4 bits (256 longs) + lumières : aller-retour
        byte[] sec = new byte[4 + 1 + 1 + 2 + 256 * 8 + 2];
        sec[1] = 0x10; sec[4] = 4; sec[5] = 2; sec[6] = 1; sec[7] = 9; // non-air 4096 ; 4 bits, palette [1, 9]
        byte[] lb = new byte[2048]; java.util.Arrays.fill(lb, (byte) 0x12);
        Msg.Cubes big = new Msg.Cubes(List.of(new Msg.CubeData(new Msg.Pos(7, -100000, 3), sec, lb, null),
                new Msg.CubeData(new Msg.Pos(7, 100000, 3), new byte[0], null, new byte[2048])));
        byte[] enc = big.encode();
        check("cube aller-retour (palette 4 bits, lumieres)", java.util.Arrays.equals(Msg.Cubes.decode(enc).encode(), enc), "");
        int bad = 0;
        byte[] ref = hex(CUBE_REF);
        for (int n = 0; n < ref.length; n++) {
            try { Msg.Cubes.decode(java.util.Arrays.copyOf(ref, n)); } catch (Wire.DecodeException e) { bad++; }
        }
        check("cube tronque : toujours rejete", bad == ref.length, bad + "/" + ref.length);

        // flux : cube sans offset rejeté ; offset, cubes, unload, heightmap ; JSON « cubes »
        LinkState s = new LinkState();
        s.beginHello("26.3"); s.sent();
        s.onInbound(Msg.MANIFEST, hex(MANIFEST));
        String before = s.json();
        s.onInbound(Msg.CUBE, hex(CUBE_REF));
        check("cube sans offset rejete", s.rejets() == 1 && s.cubeCount() == 0, s.json());
        bytes("cubeView = §8", s.cubeView(8), CUBE_VIEW_REF);
        s.onInbound(Msg.OFFSET, hex(OFFSET_REF));
        s.onInbound(Msg.CUBE, hex(CUBE_REF));
        s.onInbound(Msg.HEIGHTMAP, hex(HEIGHTMAP_REF));
        check("2 cubes connus", s.cubeCount() == 2 && s.hasCube(-1, -4, 2) && s.window().equals(off), s.json());
        s.onInbound(Msg.UNLOAD_CUBE, hex("01 00 05 00"));
        check("unload retire le cube", s.cubeCount() == 1 && !s.hasCube(0, 5, 0), s.json());
        String j = s.json();
        check("JSON cubes", j.contains("\"cubes\":{\"base\":-4,\"minY\":-64,\"sections\":24,\"shift\":0,\"loaded\":1,\"rx\":2,\"empty\":1,\"unloaded\":1,\"heightmaps\":1}")
                && !before.contains("cubes"), j);
        s.disconnected();
        check("deconnexion : cubes oublies", s.cubeCount() == 0 && s.window() == null && !s.json().contains("cubes"), s.json());
    }

    public static void main(String[] args) {
        Msg.Hello hello = new Msg.Hello(1, "0.1.0", "26.3", List.of("hud", "keys", "state"));
        Msg.Manifest man = new Msg.Manifest(1, "SAGE Z", List.of("hud", "keys"), List.of(new Msg.KeyDef("recharger", "Recharger", 82)));
        Msg.Hud hud = new Msg.Hud(List.of(new Msg.HudEntry("soif", "Soif 15/20", 0.75f, 0xFF3355FF)));
        Msg.Key key = new Msg.Key("recharger", true);

        bytes("hello encode = §4", hello.encode(), HELLO);
        check("hello decode(§4)", Msg.Hello.decode(hex(HELLO)).equals(hello), "" + Msg.Hello.decode(hex(HELLO)));
        bytes("manifest encode = §4", man.encode(), MANIFEST);
        check("manifest decode(§4)", Msg.Manifest.decode(hex(MANIFEST)).equals(man), "" + Msg.Manifest.decode(hex(MANIFEST)));
        bytes("hud encode = §4", hud.encode(), HUD);
        check("hud decode(§4)", Msg.Hud.decode(hex(HUD)).equals(hud), "" + Msg.Hud.decode(hex(HUD)));
        bytes("key encode = §4", key.encode(), KEY);
        check("key decode(§4)", Msg.Key.decode(hex(KEY)).equals(key), "" + Msg.Key.decode(hex(KEY)));

        check("GLFW->SDL R 82->21", KeyMap.glfwToSdl(82) == 21, "" + KeyMap.glfwToSdl(82));
        check("GLFW->SDL A,0,F1,espace,echap", KeyMap.glfwToSdl(65) == 4 && KeyMap.glfwToSdl(48) == 39
                && KeyMap.glfwToSdl(290) == 58 && KeyMap.glfwToSdl(32) == 44 && KeyMap.glfwToSdl(256) == 41, "table");
        check("GLFW inconnu -> -1", KeyMap.glfwToSdl(-5) == -1 && KeyMap.glfwToSdl(9999) == -1 && KeyMap.glfwToSdl(1) == -1, "borne");

        // Flux : OFF -> HELLO_SENT -> CONNECTED, HUD, touches, JSON §3.
        LinkState s = new LinkState();
        check("etat initial OFF", s.phase() == LinkState.Phase.OFF, "" + s.phase());
        s.onInbound(Msg.MANIFEST, hex(MANIFEST));
        check("manifest sans hello rejete", s.phase() == LinkState.Phase.OFF && s.rejets() == 1, s.json());
        s = new LinkState();
        check("reference hello 0.2.0 = format §4", helloRef("0.2.0").equals(HELLO_020), helloRef("0.2.0"));
        check("version annoncee lue de version.txt (build.sh)", !LinkState.VERSION.equals("0.2.0") && !LinkState.VERSION.isEmpty(), LinkState.VERSION);
        bytes("beginHello = §4 (version " + LinkState.VERSION + ")", s.beginHello("26.3"), helloRef(LinkState.VERSION));
        s.sent();
        check("HELLO_SENT", s.phase() == LinkState.Phase.HELLO_SENT, "" + s.phase());
        s.onInbound(Msg.MANIFEST, hex(MANIFEST));
        check("CONNECTED", s.phase() == LinkState.Phase.CONNECTED, s.json());
        s.onInbound(Msg.HUD, hex(HUD));
        List<Msg.Key> t = s.pollKeys(g -> g == 82);
        check("transition down", t.equals(List.of(key)), "" + t);
        if (!t.isEmpty()) { bytes("key envoyee = §4", t.get(0).encode(), KEY); s.keySent(t.get(0)); }
        check("pas de repetition", s.pollKeys(g -> g == 82).isEmpty(), "repetition");
        String want = "{\"state\":\"CONNECTED\",\"ver\":\"" + LinkState.VERSION + "\",\"proto\":1,\"server\":\"SAGE Z\",\"caps\":[\"hud\",\"keys\"],"
                + "\"hud\":{\"soif\":{\"text\":\"Soif 15/20\",\"f\":0.75,\"color\":\"FF3355FF\"}},"
                + "\"keys\":[{\"id\":\"recharger\",\"label\":\"Recharger\",\"glfw\":82}],\"lastKey\":\"recharger:down\",\"rx\":2,\"tx\":2,\"rejets\":0,\"blocks\":{\"stock\":0,\"base\":35723,\"empreinte\":\"\",\"vue\":35723,\"garde\":0,\"probe\":{\"bits16\":0,\"bits18\":0,\"etat\":\"off\"}},\"pretouch\":true}";
        String got = s.json();
        check("JSON §3", got.equals(want), "\n  attendu " + want + "\n  obtenu  " + got);
        System.out.println("     " + got);
        List<Msg.Key> up = s.pollKeys(g -> false);
        if (!up.isEmpty()) s.keySent(up.get(0));
        check("transition up", up.equals(List.of(new Msg.Key("recharger", false))) && s.json().contains("\"lastKey\":\"recharger:up\""), "" + up);

        // Robustesse : rien ne lève, tout est compté.
        long r0 = s.rejets();
        s.onInbound("link/zzz", new byte[]{1});
        s.onInbound(Msg.HUD, null);
        s.onInbound(Msg.HUD, new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0});
        s.onInbound(Msg.HUD, hex("05 01 61"));
        s.onInbound(Msg.MANIFEST, hex("02 00 00 00"));
        check("5 rejets (inconnu, trop gros, VarInt, tronque, proto 2)", s.rejets() - r0 == 5, "" + (s.rejets() - r0));
        s.onInbound(Msg.HUD, new Msg.Hud(List.of(new Msg.HudEntry("soif", "", 0f, 0))).encode());
        check("texte vide retire la cle", s.hudSnapshot().isEmpty() && s.json().contains("\"hud\":{}"), s.json());
        s.disconnected();
        check("deconnexion -> OFF", s.phase() == LinkState.Phase.OFF && s.pollKeys(g -> true).isEmpty(), s.json());

        cubes();
        lod();

        System.out.println("CodecTest : " + ok + " OK, " + ko + " FAIL");
        if (ko != 0) System.exit(1);
    }
}
