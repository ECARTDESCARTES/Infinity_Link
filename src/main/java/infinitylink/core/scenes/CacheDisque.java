package infinitylink.core.scenes;

import infinitylink.core.scenes.Contrat.Cle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cache disque des ressources vérifiées (§8.6) : un dossier PAR SERVEUR, un fichier `<clé hex>.bin` par ressource,
 *  plafond en octets, éviction des moins récemment lues (date du fichier touchée à chaque lecture). Écriture atomique
 *  (fichier temporaire puis déplacement). Jamais partagé entre serveurs : un serveur ne remplit pas le cache d'un autre. */
public final class CacheDisque {
    private final Path dossier;
    private final long plafond;
    /** Clé → {taille, dernier accès ms}, en ordre d'accès : la tête est la moins récemment lue (éviction en O(1)). */
    private final LinkedHashMap<Cle, long[]> index = new LinkedHashMap<>(16, 0.75f, true);
    /** Nombre de fichiers au plus (un serveur hostile ne remplit pas le disque de fichiers minuscules). */
    static final int MAX_FICHIERS = 200_000;
    private long total;

    public CacheDisque(Path dossier, long plafond) {
        this.dossier = dossier;
        this.plafond = plafond;
        try {
            Files.createDirectories(dossier);
            List<Map.Entry<Cle, long[]>> lus = new ArrayList<>();
            try (var fichiers = Files.list(dossier)) {
                for (Path f : (Iterable<Path>) fichiers::iterator) {
                    String n = f.getFileName().toString();
                    if (n.endsWith(".tmp")) { Files.deleteIfExists(f); continue; }
                    if (!n.endsWith(".bin") || n.length() != 36) continue;
                    Cle k = depuisHex(n.substring(0, 32));
                    if (k == null) continue;
                    long t = Files.size(f);
                    lus.add(Map.entry(k, new long[]{t, Files.getLastModifiedTime(f).toMillis()}));
                    total += t;
                }
            }
            lus.sort(Comparator.comparingLong(e -> e.getValue()[1])); // du plus ancien au plus récent
            for (Map.Entry<Cle, long[]> e : lus) index.put(e.getKey(), e.getValue());
        } catch (IOException ignored) { }
    }

    static Cle depuisHex(String h) {
        try {
            byte[] o = new byte[16];
            for (int i = 0; i < 16; i++) o[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
            return Cle.de(o, 0);
        } catch (RuntimeException e) { return null; }
    }

    private Path fichier(Cle k) { return dossier.resolve(k.hex() + ".bin"); }

    public synchronized boolean contient(Cle k) { return index.containsKey(k); }
    /** Taille au cache, ou -1. */
    public synchronized long taille(Cle k) { long[] e = index.get(k); return e == null ? -1 : e[0]; }
    public synchronized int nombre() { return index.size(); }
    public synchronized long octets() { return total; }

    /** Octets d'une ressource, ou null (absente ou illisible : retirée de l'index). */
    public byte[] lire(Cle k) {
        synchronized (this) { if (index.get(k) == null) return null; } // get : remonte la clé en fin d'ordre d'accès
        Path f = fichier(k);
        try {
            byte[] o = Files.readAllBytes(f);
            long now = System.currentTimeMillis();
            try { Files.setLastModifiedTime(f, FileTime.fromMillis(now)); } catch (IOException ignored) { }
            synchronized (this) { long[] e = index.get(k); if (e != null) e[1] = now; }
            return o;
        } catch (IOException e) {
            oublier(k);
            return null;
        }
    }

    public synchronized void oublier(Cle k) {
        long[] e = index.remove(k);
        if (e != null) total -= e[0];
        try { Files.deleteIfExists(fichier(k)); } catch (IOException ignored) { }
    }

    /** Écrit une ressource vérifiée ; évince les plus anciennes au-delà du plafond (jamais celle qu'on écrit). */
    public void ecrire(Cle k, byte[] o) {
        if (o.length > plafond) return;
        Path f = fichier(k), t = dossier.resolve(k.hex() + ".tmp");
        try {
            Files.write(t, o);
            Files.move(t, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try { Files.deleteIfExists(t); } catch (IOException ignored) { }
            return;
        }
        List<Cle> aSupprimer = new ArrayList<>();
        synchronized (this) {
            long[] avant = index.put(k, new long[]{o.length, System.currentTimeMillis()});
            if (avant != null) total -= avant[0];
            total += o.length;
            if (total > plafond || index.size() > MAX_FICHIERS) {
                // éviction depuis la tête (moins récemment lues) jusqu'à 90 % du plafond : rare, et sans tri
                long cible = plafond - plafond / 10;
                for (Iterator<Map.Entry<Cle, long[]>> it = index.entrySet().iterator(); it.hasNext() && (total > cible || index.size() > MAX_FICHIERS - MAX_FICHIERS / 10); ) {
                    Map.Entry<Cle, long[]> e = it.next();
                    if (e.getKey().equals(k)) continue;
                    total -= e.getValue()[0];
                    it.remove();
                    aSupprimer.add(e.getKey());
                }
            }
        }
        for (Cle x : aSupprimer) try { Files.deleteIfExists(fichier(x)); } catch (IOException ignored) { }
    }
}
