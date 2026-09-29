package infinitylink.mc.tabs;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import infinitylink.mc.Bridge;

import java.util.ArrayList;
import java.util.List;

/** Pagination « comme en moddé » (§4.3, Forge 1.7.10 / Fabric API) : page 0 = onglets vanilla ; pages 1..P = nos onglets
 *  non vides dans l'ordre du catalogue, 10 par page (5 en haut, 5 en bas) ; les onglets alignés à droite (barres,
 *  recherche, opérateur, inventaire) restent sur toutes les pages. La page est gardée d'une ouverture à l'autre. */
public final class TabPages {
    private TabPages() {}

    public static final int PER_PAGE = 10, PER_ROW = 5;
    private static volatile int page;

    public static int pages() {
        int n = SageTabs.count();
        return n == 0 ? 1 : 1 + (n + PER_PAGE - 1) / PER_PAGE;
    }

    public static int page() { return page; }

    /** Position visuelle d'un onglet de rang r sur sa page. */
    public static CreativeModeTab.Row row(int rank) { return (rank % PER_PAGE) < PER_ROW ? CreativeModeTab.Row.TOP : CreativeModeTab.Row.BOTTOM; }
    public static int column(int rank) { return (rank % PER_PAGE) % PER_ROW; }
    public static int pageOfRank(int rank) { return 1 + rank / PER_PAGE; }

    /** -1 = sur toutes les pages (aligné à droite) ; 0 = vanilla (ou autre mod) ; p ≥ 1 = page de l'onglet Link ; -2 = jamais. */
    public static int pageOf(CreativeModeTab t) {
        if (t.isAlignedRight()) return -1;
        int k = ((SageTabHandle) (Object) t).sage$index();
        if (k < 0) return 0;
        int r = SageTabs.rank(k);
        return r < 0 ? -2 : pageOfRank(r);
    }

    public static boolean onPage(CreativeModeTab t, int p) {
        int q = pageOf(t);
        return q == -1 || q == p;
    }

    /** Filtre de CreativeModeTabs.tabs() dans mouseClicked, mouseReleased, extractRenderState et extractBackground. */
    public static List<CreativeModeTab> visible(List<CreativeModeTab> all) {
        if (SageTabs.count() == 0) return all; // sans catalogue : écran vanilla intact
        int p = page;
        List<CreativeModeTab> out = new ArrayList<>(all.size());
        for (CreativeModeTab t : all) if (onPage(t, p)) out.add(t);
        return out;
    }

    /** Premier onglet catégorie de la page p (sélection après un changement de page). */
    public static CreativeModeTab first(int p) {
        return p <= 0 ? CreativeModeTabs.getDefaultTab() : SageTabs.tabAtRank((p - 1) * PER_PAGE);
    }

    public static boolean next() { return show(page + 1); }
    public static boolean prev() { return show(page - 1); }

    public static boolean show(int p) {
        if (p < 0 || p >= pages() || p == page) return false;
        page = p;
        publish();
        return true;
    }

    /** Le nombre de pages a changé (catalogue appliqué ou vidé) : page ramenée dans les bornes. */
    public static void changed() {
        int n = pages();
        if (page > n - 1) page = n - 1;
        if (page < 0) page = 0;
        publish();
    }

    static void publish() {
        Bridge.STATE.tabs.page = page;
        Bridge.STATE.tabs.pages = pages();
    }
}
