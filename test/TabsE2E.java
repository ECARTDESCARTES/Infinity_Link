import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import infinitylink.core.TabsMsg;
import infinitylink.mc.tabs.SageTabs;
import infinitylink.mc.tabs.TabPages;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/** Onglets sur les VRAIES classes 26.3 (Bootstrap sans écran, registres intégrés) : décodage de la pile de référence du §5.4
 *  par ItemStack.OPTIONAL_STREAM_CODEC, construction des onglets (SageTabs.build), vrai CreativeModeTab (F2, F14),
 *  pagination ; écrit le ré-encodage client (OPTIONAL_UNTRUSTED_STREAM_CODEC) pour item_canon du lot R. Argument : dossier test/ref. */
public final class TabsE2E {
    static int ok, ko;

    static byte[] hex(String s) { return HexFormat.of().parseHex(s.replace(" ", "")); }
    static String hex(byte[] b) { return HexFormat.ofDelimiter(" ").withUpperCase().formatHex(b); }

    static void check(String name, boolean cond, String detail) {
        if (cond) { ok++; System.out.println("OK   " + name); }
        else { ko++; System.out.println("FAIL " + name + " : " + detail); }
    }

    static byte[] enc(StreamCodec<RegistryFriendlyByteBuf, ItemStack> codec, ItemStack s, RegistryAccess ra) {
        ByteBuf raw = Unpooled.buffer();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(raw, ra);
        codec.encode(buf, s);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        raw.release();
        return out;
    }

    static ItemStack dec(StreamCodec<RegistryFriendlyByteBuf, ItemStack> codec, byte[] b, RegistryAccess ra) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(b), ra);
        return codec.decode(buf);
    }

    static byte[] cat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    static int indexOf(byte[] h, byte[] n) {
        outer:
        for (int i = 0; i + n.length <= h.length; i++) {
            for (int j = 0; j < n.length; j++) if (h[i + j] != n[j]) continue outer;
            return i;
        }
        return -1;
    }

    /** Banc post-v12 : décodage parallèle hors du fil de rendu (SageTabs.buildAsync sur SageTabs.POOL) contre séquentiel.
     *  Le fil de rendu ne fait plus que install() (échange de vue) ; ici on mesure le temps mur du décodage. Vue identique. */
    static void banc(String nom, TabsMsg.Catalog c, RegistryAccess ra) {
        long[] seq = new long[5], par = new long[5];
        SageTabs.View a = null, b = null;
        for (int r = 0; r < 5; r++) {
            long t = System.nanoTime(); b = SageTabs.buildAsync(c, ra, SageTabs.POOL).join(); par[r] = System.nanoTime() - t;
            t = System.nanoTime(); a = SageTabs.build(c, ra); seq[r] = System.nanoTime() - t;
        }
        boolean meme = a.count() == b.count() && a.stacks == b.stacks && a.rejected == b.rejected;
        for (int k = 0; meme && k < a.count(); k++) {
            SageTabs.Entry x = a.entry(a.tabOfRank(k)), y = b.entry(b.tabOfRank(k));
            meme = a.tabOfRank(k) == b.tabOfRank(k) && x.stacks().size() == y.stacks().size() && ItemStack.isSameItemSameComponents(x.icon(), y.icon());
            for (int j = 0; meme && j < x.stacks().size(); j++) meme = ItemStack.isSameItemSameComponents(x.stacks().get(j), y.stacks().get(j));
        }
        long[] s2 = seq.clone(), p2 = par.clone();
        Arrays.sort(s2); Arrays.sort(p2);
        String l = String.format(java.util.Locale.ROOT, "%s : %d piles, %d onglets ; parallele (%d coeurs) 1er %.1f ms / median %.1f ms ; sequentiel 1er %.1f ms / median %.1f ms",
                nom, a.stacks, a.count(), Runtime.getRuntime().availableProcessors(), par[0] / 1e6, p2[2] / 1e6, seq[0] / 1e6, s2[2] / 1e6);
        check("banc onglets " + l + " ; vue identique, parallele median < 100 ms", meme && p2[2] < 100_000_000L, l);
    }

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess.Frozen ra = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        // Sans monde, les composants par défaut des items ne sont pas liés (« Components not bound yet ») : le vrai client les lie
        // à la connexion (DataComponentInitializers). Le banc lie le seul porteur utile, minecraft:paper, à une carte minimale.
        ((net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item>) BuiltInRegistries.ITEM.wrapAsHolder(Items.PAPER))
                .bindComponents(net.minecraft.core.component.DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build());
        System.out.println("bootstrap 26.3 : " + (System.nanoTime() - t0) / 1_000_000 + " ms");

        // ---- pile de référence du §5.4 (octets de set_slot du serveur)
        byte[] pile = hex(TabsCodecRef.PILE);
        try {
            RegistryFriendlyByteBuf bb = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(pile), ra);
            ItemStack d = ItemStack.OPTIONAL_STREAM_CODEC.decode(bb);
            System.out.println("DIAG decode " + d + " reste " + bb.readableBytes() + " composants " + d.getComponents());
        } catch (Throwable t) {
            System.out.println("DIAG exception " + t);
            for (StackTraceElement e : t.getStackTrace()) System.out.println("DIAG   at " + e);
            for (Throwable c = t.getCause(); c != null; c = c.getCause()) System.out.println("DIAG cause " + c);
        }
        ItemStack s = SageTabs.decodeStack(pile, ra);
        check("pile(§5.4) decodee par OPTIONAL_STREAM_CODEC", s != null, "null");
        if (s == null) { System.out.println("TabsE2E : " + ok + "/" + (ok + ko)); System.exit(1); }
        check("porteur minecraft:paper (id 1145), quantite 1", s.getItem() == Items.PAPER && s.getCount() == 1
                && BuiltInRegistries.ITEM.getId(Items.PAPER) == 1145, s.getItem() + " x" + s.getCount());
        check("nom « ATM de banque »", s.getHoverName().getString().equals("ATM de banque"), s.getHoverName().getString());
        check("item_model sage:altiscraft/atmbanqueblock", "sage:altiscraft/atmbanqueblock".equals(String.valueOf(s.get(DataComponents.ITEM_MODEL))),
                String.valueOf(s.get(DataComponents.ITEM_MODEL)));
        ItemLore lore = s.get(DataComponents.LORE);
        check("lore = 1 ligne « AltisCraft · bloc »", lore != null && lore.lines().size() == 1 && lore.lines().get(0).getString().equals("AltisCraft · bloc"),
                String.valueOf(lore));
        check("custom_data.sage -> id de recherche altiscraft:atmbanqueblock (J5)",
                String.valueOf(SageTabs.searchId(s)).equals("altiscraft:atmbanqueblock"), String.valueOf(SageTabs.searchId(s)));
        check("pile vanilla : pas d'id de recherche sage", SageTabs.searchId(new ItemStack(Items.PAPER)) == null, "");

        // ---- ré-encodages (le client renvoie la pile par ServerboundSetCreativeModeSlot = OPTIONAL_UNTRUSTED_STREAM_CODEC)
        byte[] fiable = enc(ItemStack.OPTIONAL_STREAM_CODEC, s, ra);
        byte[] client = enc(ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC, s, ra);
        System.out.println("INFO ré-encodage fiable " + fiable.length + " o, " + (Arrays.equals(fiable, pile) ? "IDENTIQUE" : "DIFFERENT") + " des 179 o du serveur");
        System.out.println("INFO ré-encodage client (untrusted) " + client.length + " o ; \"type\" present : "
                + (indexOf(client, "type".getBytes(StandardCharsets.UTF_8)) >= 0));
        ItemStack back = dec(ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC, client, ra);
        check("ré-encodage client -> même pile (item + composants)", ItemStack.isSameItemSameComponents(s, back) && back.getCount() == 1, "");
        ItemStack back2 = dec(ItemStack.OPTIONAL_STREAM_CODEC, fiable, ra);
        check("ré-encodage fiable -> même pile", ItemStack.isSameItemSameComponents(s, back2), "");
        if (args.length > 0) {
            Path dir = Path.of(args[0]);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("onglets_pile_reencodee.hex"), hex(client) + "\n");
            Files.writeString(dir.resolve("onglets_pile_reencodee_fiable.hex"), hex(fiable) + "\n");
            System.out.println("INFO écrit " + dir.resolve("onglets_pile_reencodee.hex") + " (" + client.length + " o)");
        }

        // ---- rejets du décodeur de pile
        check("octet en trop -> rejet", SageTabs.decodeStack(cat(pile, new byte[]{0}), ra) == null, "");
        check("pile tronquee -> rejet", SageTabs.decodeStack(Arrays.copyOf(pile, 100), ra) == null, "");
        check("pile vide (00) -> rejet", SageTabs.decodeStack(new byte[]{0}, ra) == null, "");
        check("item inconnu -> rejet", SageTabs.decodeStack(hex("01 FF FF 03 00 00"), ra) == null, "");
        byte[] q3 = pile.clone();
        q3[0] = 3;
        ItemStack s3 = SageTabs.decodeStack(q3, ra);
        check("quantite 3 -> ramenee a 1", s3 != null && s3.getCount() == 1, String.valueOf(s3));

        // ---- construction des onglets : doublon, pile invalide, mod entièrement rejeté, icône, titre
        byte[] autre = pile.clone();
        autre[autre.length - 2] = 'x'; // autre id sage (…blocx) : autre pile
        TabsMsg.Catalog c = new TabsMsg.Catalog(List.of(
                new TabsMsg.ModTab("altiscraft", "AltisCraft", "", 1, List.of(pile, pile, hex("01 FF FF 03 00 00"), autre)),
                new TabsMsg.ModTab("vide", "Vide", "", 0, List.of(hex("00"))),
                new TabsMsg.ModTab("modc", "Mod C", "sage.onglet.modc", 0, List.of(autre))));
        SageTabs.View v = SageTabs.build(c, ra);
        check("build : 2 onglets non vides, 3 piles, 3 rejets", v.count() == 2 && v.stacks == 3 && v.rejected == 3,
                v.count() + " / " + v.stacks + " / " + v.rejected);
        SageTabs.Entry a = v.entry(0), b = v.entry(1), cc = v.entry(2);
        check("build : rangs 0 et 1, mod vide sans place", a != null && a.rank() == 0 && b == null && cc != null && cc.rank() == 1
                && v.tabOfRank(0) == 0 && v.tabOfRank(1) == 2, "");
        check("build : titre litteral + titre traduit avec repli", a.title().getString().equals("AltisCraft") && cc.title().getString().equals("Mod C"),
                a.title().getString() + " / " + cc.title().getString());
        check("build : icone = pile d'indice 1 du mod (doublon de 0 -> repli sur la premiere)", a.icon() == a.stacks().get(0), "");
        check("build : piles du mod dans l'ordre (ref, autre)", a.stacks().size() == 2
                && ItemStack.isSameItemSameComponents(a.stacks().get(0), s) && !ItemStack.isSameItemSameComponents(a.stacks().get(1), s), "");

        // ---- vrai CreativeModeTab : générateur Link, F14 (doublon attrapé), F2 (vide = caché)
        CreativeModeTab.ItemDisplayParameters params = new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, false, ra);
        int[] err = new int[1];
        List<ItemStack> avecDoublon = new ArrayList<>(a.stacks());
        avecDoublon.add(s.copy());
        CreativeModeTab tab = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 1000).title(Component.literal("AltisCraft"))
                .icon(() -> ItemStack.EMPTY).displayItems(SageTabs.generator((p, out) -> err[0] += SageTabs.fillInto(avecDoublon, out))).build();
        tab.buildContents(params);
        check("vrai onglet : 2 piles affichees, doublon attrape (1 echec), visible", tab.getDisplayItems().size() == 2 && err[0] == 1
                && tab.shouldDisplay() && tab.getSearchTabDisplayItems().size() == 2, tab.getDisplayItems().size() + " / " + err[0]);
        CreativeModeTab vide = CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 1000).title(Component.empty())
                .icon(() -> ItemStack.EMPTY).displayItems(SageTabs.generator((p, out) -> SageTabs.fillInto(List.of(), out))).build();
        vide.buildContents(params);
        check("vrai onglet vide : cache (shouldDisplay faux)", !vide.shouldDisplay(), "");

        // ---- pagination (§4.3) : 10 par page, 5 en haut puis 5 en bas
        check("rang 0 -> TOP 0, rang 4 -> TOP 4", TabPages.row(0) == CreativeModeTab.Row.TOP && TabPages.column(0) == 0
                && TabPages.row(4) == CreativeModeTab.Row.TOP && TabPages.column(4) == 4, "");
        check("rang 5 -> BOTTOM 0, rang 9 -> BOTTOM 4, rang 10 -> TOP 0", TabPages.row(5) == CreativeModeTab.Row.BOTTOM && TabPages.column(5) == 0
                && TabPages.row(9) == CreativeModeTab.Row.BOTTOM && TabPages.column(9) == 4 && TabPages.row(10) == CreativeModeTab.Row.TOP && TabPages.column(10) == 0, "");
        check("pages des rangs 0, 9, 10, 31 = 1, 1, 2, 4", TabPages.pageOfRank(0) == 1 && TabPages.pageOfRank(9) == 1
                && TabPages.pageOfRank(10) == 2 && TabPages.pageOfRank(31) == 4, "");
        check("sans catalogue : 1 page (vanilla intact)", TabPages.pages() == 1 && SageTabs.count() == 0, "" + TabPages.pages());
        List<TabsMsg.ModTab> trenteDeux = new ArrayList<>();
        for (int i = 0; i < 32; i++) trenteDeux.add(new TabsMsg.ModTab("m" + i, "M" + i, "", 0, List.of(pile)));
        long t1 = System.nanoTime();
        SageTabs.View v32 = SageTabs.build(new TabsMsg.Catalog(trenteDeux), ra);
        long us = (System.nanoTime() - t1) / 1000;
        java.lang.reflect.Field f = SageTabs.class.getDeclaredField("view");
        f.setAccessible(true);
        f.set(null, v32);
        check("32 mods -> 5 pages (vanilla + 4)", TabPages.pages() == 5 && SageTabs.count() == 32, "" + TabPages.pages());
        f.set(null, v);
        check("2 mods -> 2 pages", TabPages.pages() == 2, "" + TabPages.pages());

        // ---- mesure R8 : décodage + construction de 6 137 piles (taille réelle du catalogue)
        List<TabsMsg.ModTab> reel = new ArrayList<>();
        int total = 0;
        for (int i = 0; total < 6137; i++) {
            List<byte[]> st = new ArrayList<>();
            for (int j = 0; j < 192 && total < 6137; j++, total++) {
                byte[] p = pile.clone();
                p[p.length - 2] = (byte) ('a' + j % 26);
                p[p.length - 3] = (byte) ('a' + j / 26);
                st.add(p);
            }
            reel.add(new TabsMsg.ModTab("m" + i, "M" + i, "", 0, st));
        }
        byte[] trame = TabsMsg.encode(new TabsMsg.Catalog(reel), 1);
        t1 = System.nanoTime();
        TabsMsg.Catalog dr = TabsMsg.decode(trame);
        SageTabs.View vr = SageTabs.build(dr, ra);
        us = (System.nanoTime() - t1) / 1000;
        check("6137 piles : trame zlib " + trame.length + " o, " + vr.count() + " mods, decode + build " + us / 1000 + " ms",
                vr.stacks == 6137 && vr.rejected == 0 && trame.length < (1 << 20), vr.stacks + " / " + vr.rejected);
        banc("synthetique", dr, ra);

        // ---- INTÉGRATION : la VRAIE trame du serveur (ONGLETS_TRAME=test/ref/onglets_tabs_serveur.bin python tools/onglets_test.py),
        //      même fichier relu par onglets.rs (Rust) ; écrit onglets_piles_client.bin (ré-encodages untrusted) jugés par juger().
        Path trameSrv = args.length > 0 ? Path.of(args[0]).resolve("onglets_tabs_serveur.bin") : null;
        if (trameSrv != null && Files.exists(trameSrv)) {
            byte[] frS = Files.readAllBytes(trameSrv);
            net.minecraft.core.component.DataComponentMap minS = net.minecraft.core.component.DataComponentMap.builder()
                    .set(DataComponents.MAX_STACK_SIZE, 64).build();
            for (net.minecraft.world.item.Item itS : BuiltInRegistries.ITEM) {
                @SuppressWarnings("unchecked")
                net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item> hS =
                        (net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item>) BuiltInRegistries.ITEM.wrapAsHolder(itS);
                if (!hS.areComponentsBound()) hS.bindComponents(minS);
            }
            t1 = System.nanoTime();
            TabsMsg.Catalog cs = TabsMsg.decode(frS);
            SageTabs.View vs = SageTabs.build(cs, ra);
            us = (System.nanoTime() - t1) / 1000;
            int iS = 2, tailleS = 0;
            for (int sh = 0; ; sh += 7) { int bS = frS[iS++] & 0xFF; tailleS |= (bS & 0x7F) << sh; if (bS < 0x80) break; }
            java.util.zip.Inflater infS = new java.util.zip.Inflater();
            infS.setInput(frS, iS, frS.length - iS);
            byte[] corpsS = new byte[tailleS];
            int lusS = infS.inflate(corpsS);
            boolean finiS = infS.finished();
            infS.end();
            check("trame serveur " + frS.length + " o : en-tête 01 01, corps " + tailleS + " o = encodeBody(decode) à l'octet",
                    frS[0] == 1 && frS[1] == 1 && finiS && lusS == tailleS && Arrays.equals(TabsMsg.encodeBody(cs), corpsS), lusS + " / " + tailleS);
            check("trame serveur : " + cs.mods().size() + " mods, " + cs.stackCount() + " piles, " + vs.count() + " onglets, " + vs.rejected
                    + " rejet(s), decode + build " + us / 1000 + " ms",
                    vs.stacks == cs.stackCount() && vs.rejected == 0 && vs.count() == cs.mods().size(), vs.stacks + " / " + vs.rejected);
            banc("trame serveur", cs, ra);
            int nS = 0, identS = 0, retourS = 0;
            java.io.ByteArrayOutputStream outS = new java.io.ByteArrayOutputStream();
            String ecartS = null;
            for (TabsMsg.ModTab mS : cs.mods()) for (byte[] pS : mS.stacks()) {
                nS++;
                ItemStack dS = SageTabs.decodeStack(pS, ra);
                byte[] clS = dS == null ? new byte[]{0} : enc(ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC, dS, ra);
                if (dS != null && Arrays.equals(enc(ItemStack.OPTIONAL_STREAM_CODEC, dS, ra), pS)) identS++;
                else if (ecartS == null) ecartS = mS.id() + " pile " + nS;
                try {
                    ItemStack qS = dec(ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC, clS, ra);
                    if (dS != null && ItemStack.isSameItemSameComponents(dS, qS)) retourS++;
                } catch (RuntimeException e) { if (ecartS == null) ecartS = mS.id() + " pile " + nS + " : " + e; }
                for (int wS = clS.length; ; wS >>>= 7) { if ((wS & ~0x7F) == 0) { outS.write(wS); break; } outS.write((wS & 0x7F) | 0x80); }
                outS.write(clS, 0, clS.length);
            }
            Files.write(Path.of(args[0]).resolve("onglets_piles_client.bin"), outS.toByteArray());
            check("trame serveur : " + identS + "/" + nS + " piles ré-encodées (fiable) identiques à l'octet", identS == nS, String.valueOf(ecartS));
            check("trame serveur : " + retourS + "/" + nS + " piles client (untrusted) relues identiques", retourS == nS, String.valueOf(ecartS));
            System.out.println("INFO écrit onglets_piles_client.bin (" + outS.size() + " o, " + nS + " piles)");
        } else System.out.println("INFO trame serveur absente (" + trameSrv + ") : intégration sautée");

        System.out.println("TabsE2E : " + ok + "/" + (ok + ko));
        System.exit(ko > 0 ? 1 : 0);
    }
}

/** Référence partagée avec TabsCodecTest (§5.4). */
final class TabsCodecRef {
    static final String PILE = "01 F9 08 04 00 06 0A 08 00 05 63 6F 6C 6F 72 00 05 77 68 69 74 65 08 00 04 74 65 78 74 00 0D 41 54 4D 20 64 65 20 62 61 6E 71 75 65 01 00 06 69 74 61 6C 69 63 00 00 0A 1E 73 61 67 65 3A 61 6C 74 69 73 63 72 61 66 74 2F 61 74 6D 62 61 6E 71 75 65 62 6C 6F 63 6B 0B 01 0A 08 00 05 63 6F 6C 6F 72 00 04 67 72 61 79 08 00 04 74 65 78 74 00 12 41 6C 74 69 73 43 72 61 66 74 20 C2 B7 20 62 6C 6F 63 01 00 06 69 74 61 6C 69 63 00 00 00 0A 08 00 04 73 61 67 65 00 19 61 6C 74 69 73 63 72 61 66 74 3A 61 74 6D 62 61 6E 71 75 65 62 6C 6F 63 6B 00";
}
