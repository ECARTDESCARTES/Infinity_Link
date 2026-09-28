package sage.link.mc.tabs;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.item.component.CustomData;
import sage.link.core.TabsMsg;
import sage.link.mc.Bridge;
import sage.link.mc.SagePayload;
import sage.link.mc.mixin.CreativeModeTabsAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Onglets créatifs des mods (ONGLETS-CREATIFS-SPEC.md §4) : 128 onglets neutres enregistrés avant le gel,
 *  vides donc cachés (shouldDisplay), remplis par le catalogue sage:link/tabs reçu en jeu. Rien ne remonte au jeu. */
public final class SageTabs {
    private SageTabs() {}

    public static final int MAX = TabsMsg.MAX_TABS;
    private static final CreativeModeTab[] TABS = new CreativeModeTab[MAX];
    private static volatile boolean registered;

    /** Onglet rempli : titre, icône, piles décodées (quantité 1, sans doublon), rang parmi les onglets non vides. */
    public record Entry(String name, String key, Component title, ItemStack icon, List<ItemStack> stacks, int rank) {}

    /** Catalogue appliqué, remplacé d'un bloc (fil client). byRank[r] = indice de l'onglet de rang r. */
    public static final class View {
        static final View EMPTY = new View(new Entry[MAX], new int[0], 0, 0);
        final Entry[] byTab;
        final int[] byRank;
        public final int stacks, rejected;

        View(Entry[] byTab, int[] byRank, int stacks, int rejected) {
            this.byTab = byTab;
            this.byRank = byRank;
            this.stacks = stacks;
            this.rejected = rejected;
        }

        public int count() { return byRank.length; }
        public Entry entry(int k) { return k >= 0 && k < byTab.length ? byTab[k] : null; }
        public int tabOfRank(int r) { return r >= 0 && r < byRank.length ? byRank[r] : -1; }
    }

    private static volatile View view = View.EMPTY;

    /** Génération du catalogue : un décodage terminé après un catalogue plus récent ou une déconnexion est jeté. */
    private static final java.util.concurrent.atomic.AtomicLong GEN = new java.util.concurrent.atomic.AtomicLong();
    /** Décodage hors du fil de rendu (254 ms mesurés sur le fil client en v12 pour 6 137 piles) : fils démons, un onglet
     *  par tâche. Vanilla décode déjà des ItemStack hors du fil principal (fils netty) : registres gelés, codecs sans état. */
    public static final java.util.concurrent.ExecutorService POOL = pool();

    private static java.util.concurrent.ExecutorService pool() {
        int n = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        java.util.concurrent.atomic.AtomicInteger k = new java.util.concurrent.atomic.AtomicInteger();
        return java.util.concurrent.Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "sage_link-onglets-" + k.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    /** Branché sur LinkState.tabsSink : appelé sur le fil réseau ; décodé sur POOL, installé sur le fil client. */
    public static final TabsMsg.Sink SINK = new TabsMsg.Sink() {
        @Override public void catalog(TabsMsg.Catalog c) { long g = GEN.incrementAndGet(); Minecraft.getInstance().execute(() -> apply(c, g)); }
        @Override public void failed(int code) { Minecraft.getInstance().execute(() -> sendReady(new TabsMsg.Ready(code, 0, 0, 0))); }
    };

    public static boolean registered() { return registered; }

    private static void log(String s) { System.err.println("[sage_link] onglets : " + s); }

    /** §4.1, AVANT freeze() (SageBoot) : sage_link:onglet_000..127, positions TOP/BOTTOM × colonnes 1000..1063 (validate()). */
    public static void register() {
        if ("off".equalsIgnoreCase(System.getProperty("sage.link.onglets", ""))) { log("coupes (-Dsage.link.onglets=off)"); return; }
        try {
            if (FabricLoader.getInstance().isModLoaded("fabric-item-group-api-v1")) {
                log("Fabric API (fabric-item-group-api-v1) presente : onglets Link desactives (R6)");
                return;
            }
        } catch (Throwable ignored) {
            // pas de loader (banc) : on continue
        }
        for (int k = 0; k < MAX; k++) {
            final int idx = k;
            ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                    Identifier.fromNamespaceAndPath("sage_link", String.format("onglet_%03d", k)));
            CreativeModeTab tab = CreativeModeTab.builder(k % 2 == 0 ? CreativeModeTab.Row.TOP : CreativeModeTab.Row.BOTTOM, 1000 + k / 2)
                    .title(Component.empty())
                    .icon(() -> ItemStack.EMPTY)
                    .displayItems(generator((p, out) -> fill(idx, p, out)))
                    .build();
            Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, tab);
            ((SageTabHandle) (Object) tab).sage$setIndex(k);
            TABS[k] = tab;
        }
        registered = true;
        log(MAX + " onglets enregistres (colonnes 1000..1063)");
    }

    // ---- lecture (mixins, fil client)

    public static Component title(int k) { Entry e = view.entry(k); return e == null ? null : e.title(); }
    public static ItemStack icon(int k) { Entry e = view.entry(k); return e == null ? null : e.icon(); }
    public static int rank(int k) { Entry e = view.entry(k); return e == null ? -1 : e.rank(); }
    public static int count() { return view.count(); }
    public static View view() { return view; }

    public static CreativeModeTab tabAtRank(int r) {
        int k = view.tabOfRank(r);
        return k < 0 ? null : TABS[k];
    }

    // CreativeModeTab.Output est une interface « protected » : javac refuse de la nommer (même en paramètre implicite
    // de lambda) hors de son paquet, mais son fichier de classe est ACC_PUBLIC (javap -v). On la manipule donc sans la
    // nommer : générateur par Proxy de DisplayItemsGenerator (public), appel de Output.accept(ItemStack) par MethodHandle.

    /** Corps d'un générateur ; out = le CreativeModeTab.Output de la reconstruction vanilla. */
    public interface Filler {
        void fill(CreativeModeTab.ItemDisplayParameters p, Object out);
    }

    private static final java.lang.invoke.MethodHandle ACCEPT = acceptHandle();

    private static java.lang.invoke.MethodHandle acceptHandle() {
        try {
            for (java.lang.reflect.Method m : CreativeModeTab.DisplayItemsGenerator.class.getMethods()) {
                if (!m.getName().equals("accept") || m.getParameterCount() != 2) continue;
                java.lang.reflect.Method acc = m.getParameterTypes()[1].getMethod("accept", ItemStack.class);
                acc.setAccessible(true);
                return java.lang.invoke.MethodHandles.lookup().unreflect(acc)
                        .asType(java.lang.invoke.MethodType.methodType(void.class, Object.class, ItemStack.class));
            }
        } catch (Throwable t) {
            System.err.println("[sage_link] onglets : Output.accept introuvable : " + t);
        }
        return null;
    }

    public static CreativeModeTab.DisplayItemsGenerator generator(Filler f) {
        Class<?> gi = CreativeModeTab.DisplayItemsGenerator.class;
        return (CreativeModeTab.DisplayItemsGenerator) java.lang.reflect.Proxy.newProxyInstance(gi.getClassLoader(), new Class<?>[]{gi},
                (proxy, m, a) -> switch (m.getName()) {
                    case "accept" -> {
                        if (a != null && a.length == 2) f.fill((CreativeModeTab.ItemDisplayParameters) a[0], a[1]);
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> a != null && a.length == 1 && proxy == a[0];
                    case "toString" -> "sage_link:onglet";
                    default -> null;
                });
    }

    /** DisplayItemsGenerator de l'onglet k (reconstruction vanilla, fil client). */
    static void fill(int k, CreativeModeTab.ItemDisplayParameters p, Object out) {
        Entry e = view.entry(k);
        if (e == null) return;
        int err = fillInto(e.stacks(), out);
        if (err > 0) {
            Bridge.STATE.tabs.errors += err;
            Bridge.STATE.error("onglet " + k, new IllegalStateException(err + " piles refusees par ItemDisplayBuilder"));
        }
    }

    /** F14 : quantité 1 et try/catch par pile (ItemDisplayBuilder lève sur un doublon ou une quantité ≠ 1). Rend le nombre d'échecs. */
    public static int fillInto(List<ItemStack> stacks, Object out) {
        java.lang.invoke.MethodHandle h = ACCEPT;
        if (h == null || out == null) return stacks.size();
        int err = 0;
        for (ItemStack s : stacks) {
            try {
                h.invokeExact(out, s.copyWithCount(1));
            } catch (Throwable ex) {
                err++;
            }
        }
        return err;
    }

    /** F16 : octets exacts de set_slot (ItemStack.OPTIONAL_STREAM_CODEC). null si exception, pile vide ou octets restants. */
    public static ItemStack decodeStack(byte[] b, RegistryAccess ra) {
        ByteBuf raw = Unpooled.wrappedBuffer(b);
        try {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(raw, ra);
            ItemStack s = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            if (s == null || s.isEmpty() || buf.readableBytes() != 0) return null;
            return s.getCount() == 1 ? s : s.copyWithCount(1);
        } catch (RuntimeException e) {
            return null;
        } finally {
            raw.release();
        }
    }

    /** Un onglet décodé : piles (quantité 1, sans doublon), icône, rejets. Indépendant des autres onglets. */
    record Decoded(List<ItemStack> list, ItemStack icon, int rejected) {}

    /** Décodage d'un onglet : piles, dédoublonnage (type + composants), icône. Sans état partagé (appelable en parallèle). */
    static Decoded decodeMod(TabsMsg.ModTab m, RegistryAccess ra) {
        Set<ItemStack> seen = ItemStackLinkedSet.createTypeAndComponentsSet();
        List<ItemStack> list = new ArrayList<>(m.stacks().size());
        ItemStack icon = null;
        int rejected = 0;
        for (int j = 0; j < m.stacks().size(); j++) {
            ItemStack s = decodeStack(m.stacks().get(j), ra);
            if (s == null || !seen.add(s)) { rejected++; continue; }
            list.add(s);
            if (j == m.icon()) icon = s;
        }
        return new Decoded(list, icon, rejected);
    }

    /** Onglets décodés -> vue (ordre du catalogue) : titres, rangs des onglets non vides. */
    static View assemble(List<TabsMsg.ModTab> mods, Decoded[] d) {
        Entry[] by = new Entry[MAX];
        int[] ranks = new int[MAX];
        int n = 0, stacks = 0, rejected = 0;
        for (int i = 0; i < d.length; i++) {
            TabsMsg.ModTab m = mods.get(i);
            rejected += d[i].rejected();
            List<ItemStack> list = d[i].list();
            if (list.isEmpty()) continue;
            Component title = m.key().isEmpty() ? Component.literal(m.name()) : Component.translatableWithFallback(m.key(), m.name());
            by[i] = new Entry(m.name(), m.key(), title, d[i].icon() != null ? d[i].icon() : list.get(0), List.copyOf(list), n);
            ranks[n++] = i;
            stacks += list.size();
        }
        return new View(by, java.util.Arrays.copyOf(ranks, n), stacks, rejected);
    }

    /** Catalogue -> vue, sur le fil appelant (bancs). */
    public static View build(TabsMsg.Catalog c, RegistryAccess ra) {
        List<TabsMsg.ModTab> mods = c.mods();
        Decoded[] d = new Decoded[Math.min(mods.size(), MAX)];
        for (int i = 0; i < d.length; i++) d[i] = decodeMod(mods.get(i), ra);
        return assemble(mods, d);
    }

    /** Catalogue -> vue, un onglet par tâche sur `ex` ; aucune attente bloquante dans `ex` (pas d'interblocage). */
    public static java.util.concurrent.CompletableFuture<View> buildAsync(TabsMsg.Catalog c, RegistryAccess ra, java.util.concurrent.Executor ex) {
        List<TabsMsg.ModTab> mods = c.mods();
        int n = Math.min(mods.size(), MAX);
        @SuppressWarnings("unchecked")
        java.util.concurrent.CompletableFuture<Decoded>[] f = new java.util.concurrent.CompletableFuture[n];
        for (int i = 0; i < n; i++) { TabsMsg.ModTab m = mods.get(i); f[i] = java.util.concurrent.CompletableFuture.supplyAsync(() -> decodeMod(m, ra), ex); }
        return java.util.concurrent.CompletableFuture.allOf(f).thenApply(x -> {
            Decoded[] d = new Decoded[n];
            for (int i = 0; i < n; i++) d[i] = f[i].join();
            return assemble(mods, d);
        });
    }

    /** Fil client : lance le décodage sur POOL (le fil de rendu ne décode plus rien). */
    static void apply(TabsMsg.Catalog c, long g) {
        TabsMsg.Status st = Bridge.STATE.tabs;
        try {
            if (!registered) { st.code = TabsMsg.NOT_REGISTERED; sendReady(new TabsMsg.Ready(TabsMsg.NOT_REGISTERED, 0, 0, 0)); return; }
            ClientPacketListener conn = Minecraft.getInstance().getConnection();
            if (conn == null || g != GEN.get()) return;
            long t0 = System.nanoTime();
            buildAsync(c, conn.registryAccess(), POOL).whenComplete((v, t) -> Minecraft.getInstance().execute(() -> install(v, t, g, t0)));
        } catch (Throwable t) {
            Bridge.STATE.error("onglets", t);
            st.code = TabsMsg.BAD_BODY;
            sendReady(new TabsMsg.Ready(TabsMsg.BAD_BODY, 0, 0, 0));
        }
    }

    /** Fil client : remplace la vue d'un bloc, invalide le cache vanilla (F4), répond tabs_ready. Vue périmée : jetée. */
    static void install(View v, Throwable err, long g, long t0) {
        TabsMsg.Status st = Bridge.STATE.tabs;
        try {
            if (g != GEN.get()) return; // catalogue plus récent ou déconnexion pendant le décodage
            if (err != null || v == null) throw err != null ? err : new IllegalStateException("vue absente");
            long m0 = System.nanoTime();
            view = v;
            invalidate();
            TabPages.changed();
            st.mods = v.count();
            st.stacks = v.stacks;
            st.rejected = v.rejected;
            st.code = TabsMsg.OK;
            st.micros = (m0 - t0) / 1000;
            log(v.count() + " mods, " + v.stacks + " piles, " + v.rejected + " rejets, " + (st.micros / 1000) + " ms (hors fil de rendu ; fil client "
                    + String.format(java.util.Locale.ROOT, "%.2f", (System.nanoTime() - m0) / 1e6) + " ms)");
            sendReady(new TabsMsg.Ready(TabsMsg.OK, v.count(), v.stacks, v.rejected));
        } catch (Throwable t) {
            Bridge.STATE.error("onglets", t);
            st.code = TabsMsg.BAD_BODY;
            sendReady(new TabsMsg.Ready(TabsMsg.BAD_BODY, 0, 0, 0));
        }
    }

    /** Déconnexion : onglets vidés (donc cachés), cache vanilla invalidé. */
    public static void clear() {
        GEN.incrementAndGet(); // un décodage en cours ne s'installera pas après la déconnexion
        view = View.EMPTY;
        invalidate();
        TabPages.changed();
    }

    static void invalidate() {
        if (!registered) return;
        try {
            CreativeModeTabsAccessor.sage$setCachedParameters(null);
        } catch (Throwable t) {
            Bridge.STATE.error("onglets invalidation", t);
        }
    }

    static void sendReady(TabsMsg.Ready r) {
        try {
            ClientPacketListener conn = Minecraft.getInstance().getConnection();
            if (conn == null) return;
            conn.send(new ServerboundCustomPayloadPacket(SagePayload.out(TabsMsg.TABS_READY, r.encode())));
            Bridge.STATE.sent();
        } catch (Throwable t) {
            Bridge.STATE.error("tabs_ready", t);
        }
    }

    /** J5 : identifiant de recherche d'un contenu = son id sage (custom_data.sage), sinon null (vanilla). */
    public static Identifier searchId(ItemStack stack) {
        try {
            CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
            if (cd == null || cd.isEmpty()) return null;
            String s = cd.copyTag().getStringOr("sage", "");
            return s.isEmpty() ? null : Identifier.tryParse(s);
        } catch (Throwable t) {
            return null;
        }
    }
}
