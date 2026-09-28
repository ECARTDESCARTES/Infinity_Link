package sage.link.mc.tabs;

/** Interface « canard » posée sur CreativeModeTab par CreativeModeTabMixin : indice de l'onglet Link (-1 = autre onglet). */
public interface SageTabHandle {
    int sage$index();

    void sage$setIndex(int k);
}
