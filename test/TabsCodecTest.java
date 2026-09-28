import sage.link.core.LinkState;
import sage.link.core.Msg;
import sage.link.core.TabsMsg;
import sage.link.core.Wire;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.Deflater;

/** Java pur : capacité « tabs » (ONGLETS-CREATIFS-SPEC.md §5) contre les octets de référence du §5.4, zlib,
 *  rejets du §9 (lot R et lot J identiques), puis le flux d'état LinkState et le JSON « tabs » (§4.6). */
public final class TabsCodecTest {
    static int ok, ko;

    static byte[] hex(String s) { return HexFormat.of().parseHex(s.replace(" ", "")); }
    static String hex(byte[] b) { return HexFormat.ofDelimiter(" ").withUpperCase().formatHex(b); }

    static void check(String name, boolean cond, String detail) {
        if (cond) { ok++; System.out.println("OK   " + name); }
        else { ko++; System.out.println("FAIL " + name + " : " + detail); }
    }

    static void bytes(String name, byte[] got, byte[] want) {
        check(name, Arrays.equals(got, want), "attendu " + hex(want) + "\n  obtenu  " + hex(got));
    }

    /** §5.4 : pile de référence (179 o) = Stack::vanilla("minecraft:paper",1).with_name(..).with_model(..).with_lore(..).with_sage_id(..). */
    static final String PILE = "01 F9 08 04 00 06 0A 08 00 05 63 6F 6C 6F 72 00 05 77 68 69 74 65 08 00 04 74 65 78 74 00 0D 41 54 4D 20 64 65 20 62 61 6E 71 75 65 01 00 06 69 74 61 6C 69 63 00 00 0A 1E 73 61 67 65 3A 61 6C 74 69 73 63 72 61 66 74 2F 61 74 6D 62 61 6E 71 75 65 62 6C 6F 63 6B 0B 01 0A 08 00 05 63 6F 6C 6F 72 00 04 67 72 61 79 08 00 04 74 65 78 74 00 12 41 6C 74 69 73 43 72 61 66 74 20 C2 B7 20 62 6C 6F 63 01 00 06 69 74 61 6C 69 63 00 00 00 0A 08 00 04 73 61 67 65 00 19 61 6C 74 69 73 63 72 61 66 74 3A 61 74 6D 62 61 6E 71 75 65 62 6C 6F 63 6B 00";
    static final String CORPS_ENTETE = "01 0A 61 6C 74 69 73 63 72 61 66 74 0A 41 6C 74 69 73 43 72 61 66 74 00 00 01 B3 01";
    static final String TABS_ENTETE = "01 00 CF 01";
    static final String TABS_VIDE = "01 00 01 00";

    static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.writeBytes(p);
        return o.toByteArray();
    }

    static byte[] frame(int proto, int codage, int raw, byte[] payload) {
        return new Wire.Out().varInt(proto).u8(codage).varInt(raw).bytes(payload).toBytes();
    }

    static byte[] zlib(byte[] b) {
        Deflater d = new Deflater(6);
        d.setInput(b);
        d.finish();
        byte[] buf = new byte[b.length + 1024];
        int n = 0;
        while (!d.finished()) {
            if (n == buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
            n += d.deflate(buf, n, buf.length - n);
        }
        d.end();
        return Arrays.copyOf(buf, n);
    }

    /** Le décodage doit lever Bad avec ce code (ou un code de l'ensemble), jamais autre chose. */
    static void rejet(String name, byte[] trame, int... codes) {
        try {
            TabsMsg.Catalog c = TabsMsg.decode(trame);
            check(name, false, "accepte (" + c.mods().size() + " mods)");
        } catch (TabsMsg.Bad e) {
            boolean in = false;
            for (int c : codes) in |= c == e.code;
            check(name, in, "code " + e.code + " (" + e.getMessage() + ")");
        } catch (Throwable t) {
            check(name, false, "exception " + t);
        }
    }

    static byte[] corpsBrut(String entete) { return cat(hex(entete), hex(PILE)); }

    public static void main(String[] args) {
        byte[] pile = hex(PILE);
        byte[] corps = corpsBrut(CORPS_ENTETE);
        byte[] tabs = cat(hex(TABS_ENTETE), corps);
        check("pile de reference = 179 o", pile.length == 179, "" + pile.length);
        check("tabs de reference = 211 o, corps 207", tabs.length == 211 && corps.length == 207, tabs.length + " / " + corps.length);

        // ---- §5.4 à l'octet
        TabsMsg.Catalog c = TabsMsg.decode(tabs);
        check("decode tabs(§5.4) : 1 mod", c.mods().size() == 1, "" + c.mods().size());
        TabsMsg.ModTab m = c.mods().get(0);
        check("decode tabs(§5.4) : champs", m.id().equals("altiscraft") && m.name().equals("AltisCraft") && m.key().isEmpty()
                && m.icon() == 0 && m.stacks().size() == 1, m.toString());
        bytes("decode tabs(§5.4) : pile = 179 o de reference", m.stacks().get(0), pile);
        TabsMsg.Catalog ref = new TabsMsg.Catalog(List.of(new TabsMsg.ModTab("altiscraft", "AltisCraft", "", 0, List.of(pile))));
        bytes("encode(codage 0) = tabs(§5.4) 211 o", TabsMsg.encode(ref, 0), tabs);
        bytes("encodeBody = corps(§5.4)", TabsMsg.encodeBody(ref), corps);
        check("tabs vide -> 0 mod", TabsMsg.decode(hex(TABS_VIDE)).mods().isEmpty(), "");
        bytes("encode(vide) = 01 00 01 00", TabsMsg.encode(new TabsMsg.Catalog(List.of()), 0), hex(TABS_VIDE));

        bytes("tabs_ready(0,1,1,0)", new TabsMsg.Ready(0, 1, 1, 0).encode(), hex("00 01 01 00"));
        bytes("tabs_ready(2,0,0,0)", new TabsMsg.Ready(2, 0, 0, 0).encode(), hex("02 00 00 00"));
        bytes("tabs_ready(0,38,6137,3)", new TabsMsg.Ready(0, 38, 6137, 3).encode(), hex("00 26 F9 2F 03"));
        check("tabs_ready decode", TabsMsg.Ready.decode(hex("00 26 F9 2F 03")).equals(new TabsMsg.Ready(0, 38, 6137, 3)), "");

        // ---- codage 1 (zlib) : le corps décodé est égal, octet pour octet, au corps brut
        List<TabsMsg.ModTab> mods = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            List<byte[]> st = new ArrayList<>();
            for (int j = 0; j < 50; j++) { byte[] p = pile.clone(); p[p.length - 2] = (byte) ('a' + j % 26); st.add(p); }
            mods.add(new TabsMsg.ModTab("mod" + i, "Mod " + i, i % 2 == 0 ? "" : "sage.mod." + i, i % 50, st));
        }
        TabsMsg.Catalog gros = new TabsMsg.Catalog(mods.subList(0, 40));
        byte[] brut = TabsMsg.encodeBody(gros);
        byte[] z = TabsMsg.encode(gros, 1);
        TabsMsg.Catalog dz = TabsMsg.decode(z);
        bytes("codage 1 : corps decode = corps brut", TabsMsg.encodeBody(dz), brut);
        check("codage 1 : compresse (" + z.length + " o pour " + brut.length + ")", z.length < brut.length / 5, "");
        check("codage 1 : 40 mods, 2000 piles", dz.mods().size() == 40 && dz.stackCount() == 2000, "" + dz.stackCount());
        bytes("codage 1 d'une trame zlib tierce (Deflater 9)", TabsMsg.encodeBody(TabsMsg.decode(frame(1, 1, corps.length, zlib(corps)))), corps);

        // ---- rejets (§9, identiques au lot R)
        rejet("trame vide", new byte[0], 1);
        rejet("en-tete tronque", hex("01 00"), 1);
        rejet("proto 2", hex("02 00 01 00"), 1);
        rejet("codage 2", hex("01 02 01 00"), 1);
        rejet("taille 0", hex("01 00 00"), 1);
        rejet("taille > MAX_BRUT", frame(1, 1, TabsMsg.MAX_RAW + 1, zlib(corps)), 1);
        rejet("taille negative (VarInt 5 o)", hex("01 00 FF FF FF FF 0F 00"), 1);
        rejet("codage 0 : octet en trop apres le corps", cat(tabs, hex("00")), 1);
        rejet("codage 0 : corps plus court que taille", Arrays.copyOf(tabs, 210), 1);
        byte[] enTrop = cat(corps, hex("00"));
        rejet("corps : octet en trop", frame(1, 0, enTrop.length, enTrop), 3);
        byte[] b129 = hex("81 01");
        rejet("n_mods 129", frame(1, 0, b129.length, b129), 3);
        byte[] icone = corpsBrut("01 0A 61 6C 74 69 73 63 72 61 66 74 0A 41 6C 74 69 73 43 72 61 66 74 00 01 01 B3 01");
        rejet("icone >= n_contenus", frame(1, 0, icone.length, icone), 3);
        byte[] zero = hex("01 0A 61 6C 74 69 73 63 72 61 66 74 0A 41 6C 74 69 73 43 72 61 66 74 00 00 00");
        rejet("n_contenus 0", frame(1, 0, zero.length, zero), 3);
        byte[] trop = hex("01 0A 61 6C 74 69 73 63 72 61 66 74 0A 41 6C 74 69 73 43 72 61 66 74 00 00 81 40");
        rejet("n_contenus 8193", frame(1, 0, trop.length, trop), 3);
        byte[] len0 = hex("01 0A 61 6C 74 69 73 63 72 61 66 74 0A 41 6C 74 69 73 43 72 61 66 74 00 00 01 00");
        rejet("longueur de pile 0", frame(1, 0, len0.length, len0), 3);
        byte[] lenMax = cat(hex("01 0A 61 6C 74 69 73 63 72 61 66 74 0A 41 6C 74 69 73 43 72 61 66 74 00 00 01 80 80 04"), new byte[65_536]);
        rejet("longueur de pile 65536", frame(1, 0, lenMax.length, lenMax), 3);
        byte[] maj = corpsBrut("01 0A 41 6C 74 69 73 63 72 61 66 74 0A 41 6C 74 69 73 43 72 61 66 74 00 00 01 B3 01");
        rejet("id de mod hors [a-z0-9_.-]", frame(1, 0, maj.length, maj), 3);
        byte[] sansNom = corpsBrut("01 0A 61 6C 74 69 73 63 72 61 66 74 00 00 00 01 B3 01");
        rejet("nom vide", frame(1, 0, sansNom.length, sansNom), 3);
        int bad = 0;
        for (int n = 0; n < tabs.length; n++) {
            try { TabsMsg.decode(Arrays.copyOf(tabs, n)); bad++; }
            catch (TabsMsg.Bad e) { if (e.code != 1 && e.code != 3) bad++; }
            catch (Throwable t) { bad++; }
        }
        check("trame tronquee a toute longueur (0..210) : code 1", bad == 0, bad + " cas");
        bad = 0;
        for (int n = 0; n < corps.length; n++) {
            try { TabsMsg.decodeBody(Arrays.copyOf(corps, n)); bad++; }
            catch (TabsMsg.Bad e) { if (e.code != 3) bad++; }
            catch (Throwable t) { bad++; }
        }
        check("corps tronque a toute longueur (0..206) : code 3", bad == 0, bad + " cas");

        // ---- zlib hostile : code 2, allocation bornée à taille_brute
        byte[] bombe = zlib(new byte[4 << 20]);
        long t0 = System.nanoTime();
        rejet("bombe zlib (4 Mio annonces 1000 o)", frame(1, 1, 1000, bombe), 2);
        check("bombe zlib coupee vite", (System.nanoTime() - t0) < 2_000_000_000L, "");
        byte[] zc = zlib(corps);
        rejet("zlib tronque", frame(1, 1, corps.length, Arrays.copyOf(zc, zc.length - 5)), 2);
        rejet("zlib plus court que taille", frame(1, 1, corps.length + 10, zc), 2);
        rejet("zlib plus long que taille", frame(1, 1, corps.length - 10, zc), 2);
        rejet("zlib puis octets en trop", frame(1, 1, corps.length, cat(zc, hex("00"))), 2);
        rejet("zlib invalide", frame(1, 1, 100, hex("12 34 56 78 9A")), 2);
        rejet("codage 1, flux vide", frame(1, 1, 100, new byte[0]), 2);

        // ---- flux LinkState : capacité, aiguillage, JSON
        LinkState s = new LinkState();
        List<Object> recu = new ArrayList<>();
        s.tabsSink = new TabsMsg.Sink() {
            @Override public void catalog(TabsMsg.Catalog cc) { recu.add(cc); }
            @Override public void failed(int code) { recu.add(code); }
        };
        check("hello sans onglets : caps inchangees", Msg.Hello.decode(s.beginHello("26.3")).caps().equals(List.of("hud", "keys", "state")), "");
        check("JSON sans onglets : pas de section tabs", !s.json().contains("\"tabs\""), s.json());
        s.tabsCapable = true;
        s.tabs.on = true;
        check("hello avec onglets : caps + tabs", Msg.Hello.decode(s.beginHello("26.3")).caps().equals(List.of("hud", "keys", "state", "tabs")), "");
        s.onInbound(Msg.MANIFEST, new Msg.Manifest(1, "Serveur", List.of("hud", "keys", "state"), List.of()).encode());
        s.onInbound(TabsMsg.TABS, tabs);
        check("tabs sans capacite accordee : rejete, rien d'applique", s.rejets() == 1 && recu.isEmpty() && !s.tabsOn(), s.json());
        s.beginHello("26.3");
        s.onInbound(Msg.MANIFEST, new Msg.Manifest(1, "Serveur", List.of("hud", "tabs", "inconnue"), List.of()).encode());
        check("manifest avec tabs : tabsOn", s.tabsOn() && s.json().contains("\"caps\":[\"hud\",\"tabs\"]"), s.json());
        long rx = s.rx();
        s.onInbound(TabsMsg.TABS, tabs);
        check("tabs recu -> catalogue au puits", recu.size() == 1 && recu.get(0) instanceof TabsMsg.Catalog cc && cc.mods().size() == 1
                && s.rx() == rx + 1 && s.tabs.rx == 1, "" + recu);
        s.onInbound(TabsMsg.TABS, hex("02 00 01 00"));
        check("tabs malforme -> failed(1), rejet compte", recu.size() == 2 && Integer.valueOf(1).equals(recu.get(1)) && s.tabs.code == 1 && s.rejets() == 2, "" + recu);
        s.tabs.mods = 32; s.tabs.stacks = 6137; s.tabs.rejected = 0; s.tabs.page = 1; s.tabs.pages = 5; s.tabs.code = 0;
        String j = s.json();
        check("JSON tabs (§4.6)", j.contains("\"tabs\":{\"on\":true,\"mods\":32,\"piles\":6137,\"rejets\":0,\"page\":1,\"pages\":5,\"code\":0,\"rx\":1,"), j);
        s.disconnected();
        check("deconnexion : catalogue oublie, on garde", s.tabs.mods == 0 && s.tabs.code == -1 && s.tabs.on && !s.tabsOn()
                && s.json().contains("\"tabs\":{\"on\":true,\"mods\":0"), s.json());
        s.onInbound(TabsMsg.TABS, tabs);
        check("tabs hors connexion : rejete", recu.size() == 2, "" + recu);

        System.out.println("TabsCodecTest : " + ok + "/" + (ok + ko));
        if (ko > 0) System.exit(1);
    }
}
