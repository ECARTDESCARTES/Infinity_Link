package infinitylink.mc;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Premier lancement d'InfinityLink après SAGE Link / Infinity_Link 1.x : réglages et cache repris sous les nouveaux noms.
 *  Rien n'est supprimé ; un fichier déjà présent sous le nouveau nom n'est jamais écrasé. */
final class Migration {
    private Migration() {}

    static void run() {
        FabricLoader fl = FabricLoader.getInstance();
        copier(fl.getConfigDir().resolve("sage_link.properties"), fl.getConfigDir().resolve("infinitylink.properties"));
        copier(fl.getConfigDir().resolve("sage_link_blocs_essai.properties"), fl.getConfigDir().resolve("infinitylink_blocs_essai.properties"));
        deplacer(fl.getGameDir().resolve("sage_link").resolve("cache"), fl.getGameDir().resolve("infinitylink").resolve("cache"));
    }

    private static void copier(Path ancien, Path nouveau) {
        try {
            if (Files.isRegularFile(ancien) && !Files.exists(nouveau)) {
                Files.copy(ancien, nouveau);
                System.out.println("[infinitylink] migration : " + ancien.getFileName() + " -> " + nouveau.getFileName());
            }
        } catch (Exception e) { System.err.println("[infinitylink] migration impossible (" + ancien + ") : " + e); }
    }

    private static void deplacer(Path ancien, Path nouveau) {
        try {
            if (Files.isDirectory(ancien) && !Files.exists(nouveau)) {
                Files.createDirectories(nouveau.getParent());
                Files.move(ancien, nouveau);
                System.out.println("[infinitylink] migration : cache " + ancien + " -> " + nouveau);
            }
        } catch (Exception e) { System.err.println("[infinitylink] migration du cache impossible (" + ancien + ") : " + e); }
    }
}
