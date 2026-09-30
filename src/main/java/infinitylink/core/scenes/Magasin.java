package infinitylink.core.scenes;

import infinitylink.core.scenes.Contrat.Cle;
import infinitylink.core.scenes.Contrat.Description;
import infinitylink.core.scenes.Contrat.Ressource;
import infinitylink.core.scenes.Lecteur.Refus;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Côté client du contrat « maillages » (§4, §5, §8) hors rendu : assemblage des descriptions (révisions, bornes),
 *  réception des ressources (dans l'ordre, 8 à la fois et 64 Mio en vol au plus, jamais préallouées), vérification
 *  SHA-256, validation et décodage sur un fil de travail, cache disque par serveur, BESOIN (ce qui manque vraiment,
 *  puis de nouveau après un rejet) et ÉTAT (un par seconde au plus). Le rendu reçoit tout par {@link Ecouteur}.
 *  Java pur ; entrées réseau sérialisées par le verrou de l'objet. */
public final class Magasin {
    /** Rappels, sur le fil de travail ou le fil réseau : le rendu les met en file et les consomme sur son fil. */
    public interface Ecouteur {
        /** Description devenue courante (marqueur « scene:<id> ») ; commeItem : faux pour une scène. */
        void scene(String marqueur, Description d, boolean commeItem);
        void retiree(String marqueur);
        void videe();
        void niveau(Cle k, Contrat.NiveauGpu n);
        void texture(Cle k, Contrat.Texture t);
    }

    private record Assemblage(int revision, int total, ByteArrayOutputStream octets, int[] suivant) {}
    private record Courante(int revision, Description d) {}
    private static final class Reception {
        final Ressource r; final ByteArrayOutputStream o = new ByteArrayOutputStream();
        Reception(Ressource r) { this.r = r; }
    }

    private final CacheDisque cache;
    private final Consumer<byte[]> envoi;
    private final Executor travail;
    private final Ecouteur ecouteur;
    private final boolean economie;
    private final Map<String, Assemblage> assemblages = new HashMap<>();
    private final Map<String, Courante> scenes = new LinkedHashMap<>();
    private final Map<Cle, Ressource> connues = new HashMap<>();
    private final Map<Cle, Integer> trianglesAnnonces = new HashMap<>();
    private final Map<Cle, Reception> receptions = new HashMap<>();
    private final Set<Cle> demandees = new HashSet<>(), livrees = new HashSet<>(), enChargement = new HashSet<>();
    private final Map<Cle, Integer> redemandes = new HashMap<>();
    private long enVol;
    private int rejets, recues, octetsRecus;
    private String dernierMotif = "";
    private boolean change = true;

    public Magasin(CacheDisque cache, Consumer<byte[]> envoi, Executor travail, Ecouteur ecouteur, boolean economie) {
        this.cache = cache; this.envoi = envoi; this.travail = travail; this.ecouteur = ecouteur; this.economie = economie;
    }

    private void rejeter(String motif) { rejets++; dernierMotif = motif.length() > 200 ? motif.substring(0, 200) : motif; change = true; }

    /** Un message de sage:link/meshes. */
    public synchronized void recevoir(byte[] brut) {
        Contrat.Message m;
        try { m = Contrat.message(brut); } catch (Refus r) { rejeter("message : " + r.getMessage()); return; }
        switch (m) {
            case Contrat.Scene s -> morceauScene(s);
            case Contrat.Ressourcemorceau r -> morceauRessource(r);
            case Contrat.Retirer r -> retirer(r.id());
            case Contrat.Vider v -> vider();
        }
    }

    private void morceauScene(Contrat.Scene s) {
        Courante c = scenes.get(s.id());
        Assemblage a = assemblages.get(s.id());
        if (s.index() == 0) {
            if (c != null && s.revision() <= c.revision()) return; // révision ≤ courante : ignorée
            if (a != null && s.revision() <= a.revision()) { assemblages.remove(s.id()); rejeter("scène " + s.id() + " : révision " + s.revision() + " hors séquence"); return; }
            if (a == null && assemblages.size() >= Contrat.MAX_ASSEMBLAGES) { rejeter("plus de 4 descriptions en cours"); return; }
            a = new Assemblage(s.revision(), s.total(), new ByteArrayOutputStream(), new int[]{0});
            assemblages.put(s.id(), a);
        } else if (a == null || a.revision() != s.revision() || a.total() != s.total() || a.suivant()[0] != s.index()) {
            assemblages.remove(s.id());
            rejeter("scène " + s.id() + " : morceau " + s.index() + " hors séquence");
            return;
        }
        if (a.octets().size() + s.donnees().length > Contrat.MAX_DESCRIPTION) { assemblages.remove(s.id()); rejeter("description de plus de 16 Mio"); return; }
        a.octets().writeBytes(s.donnees());
        a.suivant()[0]++;
        if (a.suivant()[0] < a.total()) return;
        assemblages.remove(s.id());
        Description d;
        try { d = Contrat.description(a.octets().toByteArray()); } catch (Refus r) { rejeter("description " + s.id() + " : " + r.getMessage()); return; }
        if (c == null && scenes.size() >= Contrat.MAX_SCENES) { rejeter("plus de 256 scènes"); return; }
        // une clé déjà connue d'une autre description, ou du cache, avec un autre genre ou une autre taille : refus
        for (Ressource r : d.ressources()) {
            Ressource k = connues.get(r.cle());
            if (k != null && (k.genre() != r.genre() || k.taille() != r.taille())) { rejeter("scène " + s.id() + " : clé " + r.cle().hex() + " contradictoire"); return; }
            long t = cache == null ? -1 : cache.taille(r.cle());
            if (t >= 0 && t != r.taille()) { rejeter("scène " + s.id() + " : clé " + r.cle().hex() + " de " + t + " octets au cache, " + r.taille() + " annoncés"); return; }
        }
        scenes.put(s.id(), new Courante(s.revision(), d));
        recalculerConnues();
        ecouteur.scene("scene:" + s.id(), d, false);
        change = true;
        besoin(s.id(), s.revision(), d, false);
    }

    private void recalculerConnues() {
        connues.clear();
        trianglesAnnonces.clear();
        for (Courante c : scenes.values()) {
            for (Ressource r : c.d().ressources()) connues.put(r.cle(), r);
            for (Contrat.Maillage m : c.d().maillages())
                for (Contrat.Niveau n : m.niveaux()) trianglesAnnonces.put(c.d().ressources()[n.ressource()].cle(), n.triangles());
        }
    }

    /** Index du niveau 0 des maillages de plus de 2 niveaux (non demandés en qualité « économie »). */
    private Set<Integer> niveauxZeroEconomie(Description d) {
        Set<Integer> s = new HashSet<>();
        if (!economie) return s;
        for (Contrat.Maillage m : d.maillages()) if (m.niveaux().length > 2) s.add(m.niveaux()[0].ressource());
        return s;
    }

    /** BESOIN : ce qui n'est ni livré, ni sur disque (chargé alors du disque), ni en réception, ni déjà demandé. */
    private void besoin(String id, int revision, Description d, boolean seulementRedemandes) {
        Set<Integer> exclus = niveauxZeroEconomie(d);
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < d.ressources().length; i++) {
            Cle k = d.ressources()[i].cle();
            if (exclus.contains(i)) continue;
            if (livrees.contains(k) || receptions.containsKey(k) || enChargement.contains(k)) continue;
            if (!seulementRedemandes && cache != null && cache.contient(k)) { chargerDuDisque(k); continue; }
            if (demandees.contains(k) && !seulementRedemandes) continue;
            if (seulementRedemandes && !redemandes.containsKey(k)) continue;
            idx.add(i);
            demandees.add(k);
        }
        if (seulementRedemandes && idx.isEmpty()) return;
        int[] t = idx.stream().mapToInt(Integer::intValue).toArray();
        envoi.accept(Contrat.besoin(id, revision, t));
    }

    private void morceauRessource(Contrat.Ressourcemorceau m) {
        // une réception commencée est menée à son terme même si plus aucune description ne cite la clé (§4)
        Reception x = receptions.get(m.cle());
        Ressource r = x != null ? x.r : connues.get(m.cle());
        if (x == null && (r == null || !demandees.contains(m.cle()))) { rejeter("ressource " + m.cle().hex() + " non demandée"); return; }
        if (x == null) {
            if (m.decalage() != 0) { rejeter("ressource " + m.cle().hex() + " : décalage " + m.decalage() + " attendu 0"); return; }
            if (receptions.size() >= Contrat.MAX_RECEPTIONS || enVol + r.taille() > Contrat.MAX_EN_VOL) { rejeter("trop de ressources en réception"); demandees.remove(m.cle()); redemander(m.cle()); return; } // §5 : BESOIN renvoyé après un rejet
            x = new Reception(r);
            receptions.put(m.cle(), x);
            enVol += r.taille();
        }
        if (m.decalage() != x.o.size() || (long) m.decalage() + m.donnees().length > r.taille()) {
            abandonner(m.cle(), x, "ressource " + m.cle().hex() + " : décalage " + m.decalage() + " inattendu");
            return;
        }
        x.o.writeBytes(m.donnees());
        octetsRecus += m.donnees().length;
        if (x.o.size() < r.taille()) return;
        receptions.remove(m.cle());
        enVol -= r.taille();
        demandees.remove(m.cle());
        byte[] o = x.o.toByteArray();
        Integer tri = trianglesAnnonces.get(m.cle());
        enChargement.add(m.cle()); // en vérification : ni redemandée ni rechargée d'ici là
        travail.execute(() -> verifier(r, o, tri == null ? -1 : tri, true));
    }

    private void abandonner(Cle k, Reception x, String motif) {
        receptions.remove(k);
        enVol -= x.r.taille();
        demandees.remove(k);
        rejeter(motif);
        redemander(k);
    }

    /** Une ressource rejetée est redemandée (2 fois au plus) sur la révision courante d'une scène qui la cite. */
    private void redemander(Cle k) {
        int n = redemandes.merge(k, 1, Integer::sum);
        if (n > 2) return;
        for (Map.Entry<String, Courante> e : scenes.entrySet())
            for (Ressource r : e.getValue().d().ressources())
                if (r.cle().equals(k)) { besoin(e.getKey(), e.getValue().revision(), e.getValue().d(), true); return; }
    }

    /** Fil de travail : clé, validation, décodage, puis cache disque et rendu. */
    private void verifier(Ressource r, byte[] o, int triangles, boolean duReseau) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(o);
            if (!Cle.de(h, 0).equals(r.cle())) throw new Refus("clé fausse");
            if (r.genre() == Contrat.GENRE_NIVEAU) {
                Contrat.NiveauGpu n = Contrat.niveau(o, triangles);
                for (Cle t : n.textures()) {
                    Ressource tr;
                    synchronized (this) { tr = connues.get(t); }
                    if (tr == null) throw new Refus("texture " + t.hex() + " citée par aucune description");
                    if (tr.genre() != Contrat.GENRE_TEXTURE) throw new Refus("texture citée de genre NIVEAU");
                }
                if (duReseau && cache != null) cache.ecrire(r.cle(), o);
                synchronized (this) { livrees.add(r.cle()); enChargement.remove(r.cle()); recues++; change = true; }
                ecouteur.niveau(r.cle(), n);
            } else {
                Contrat.Texture t = Contrat.texture(o);
                if (duReseau && cache != null) cache.ecrire(r.cle(), o);
                synchronized (this) { livrees.add(r.cle()); enChargement.remove(r.cle()); recues++; change = true; }
                ecouteur.texture(r.cle(), t);
            }
        } catch (Throwable t) {
            synchronized (this) {
                enChargement.remove(r.cle());
                rejeter("ressource " + r.cle().hex() + " : " + (t instanceof Refus ? t.getMessage() : t.toString()));
                if (!duReseau && cache != null) cache.oublier(r.cle());
                redemander(r.cle());
            }
        }
    }

    private void chargerDuDisque(Cle k) {
        Ressource r = connues.get(k);
        if (r == null || !enChargement.add(k)) return;
        Integer tri = trianglesAnnonces.get(k);
        travail.execute(() -> {
            byte[] o = cache.lire(k);
            if (o == null) {
                synchronized (this) {
                    enChargement.remove(k);
                    // plus sur disque : redemandée au serveur
                    for (Map.Entry<String, Courante> e : scenes.entrySet())
                        for (int i = 0; i < e.getValue().d().ressources().length; i++)
                            if (e.getValue().d().ressources()[i].cle().equals(k)) {
                                demandees.add(k);
                                envoi.accept(Contrat.besoin(e.getKey(), e.getValue().revision(), new int[]{i}));
                                return;
                            }
                }
                return;
            }
            verifier(r, o, tri == null ? -1 : tri, false);
        });
    }

    /** Le rendu n'a pas pu décoder une ressource livrée (PNG illisible au décodage complet) : rejet compté, oubliée
     *  du cache, redemandée (2 fois au plus). */
    public synchronized void rejetExterne(Cle k, String motif) {
        livrees.remove(k);
        rejeter("ressource " + k.hex() + " : " + motif);
        if (cache != null) cache.oublier(k);
        redemander(k);
    }

    /** Le rendu a libéré une ressource du GPU (plafond de mémoire, plus citée) : elle n'est plus livrée. */
    public synchronized void liberee(Cle k) { livrees.remove(k); }

    /** Le rendu veut une ressource qu'il n'a pas (libérée, ou niveau 0 exclu en économie) : disque, sinon serveur, si
     *  elle n'est ni livrée ni déjà en route. Le rendu limite lui-même la cadence de ses appels. */
    public synchronized void recharger(Cle k) {
        if (livrees.contains(k) || enChargement.contains(k) || receptions.containsKey(k) || demandees.contains(k)) return;
        if (cache != null && cache.contient(k)) { chargerDuDisque(k); return; }
        for (Map.Entry<String, Courante> e : scenes.entrySet())
            for (int i = 0; i < e.getValue().d().ressources().length; i++)
                if (e.getValue().d().ressources()[i].cle().equals(k)) {
                    demandees.add(k);
                    envoi.accept(Contrat.besoin(e.getKey(), e.getValue().revision(), new int[]{i}));
                    return;
                }
    }

    private void retirer(String id) {
        assemblages.remove(id);
        if (scenes.remove(id) != null) { recalculerConnues(); ecouteur.retiree("scene:" + id); change = true; }
    }

    private void vider() {
        assemblages.clear();
        scenes.clear();
        receptions.clear();
        demandees.clear();
        redemandes.clear();
        livrees.clear(); // le rendu libère tout à VIDER
        enVol = 0;
        recalculerConnues();
        ecouteur.videe();
        change = true;
    }

    /** Scènes courantes dont toutes les ressources voulues sont livrées. */
    public synchronized int pretes() {
        int n = 0;
        for (Courante c : scenes.values()) {
            Set<Integer> exclus = niveauxZeroEconomie(c.d());
            boolean ok = true;
            for (int i = 0; i < c.d().ressources().length && ok; i++) ok = exclus.contains(i) || livrees.contains(c.d().ressources()[i].cle());
            if (ok) n++;
        }
        return n;
    }

    /** ÉTAT à envoyer si quelque chose a changé (le client en envoie au plus un par seconde). */
    public synchronized byte[] etatSiChange() {
        if (!change) return null;
        change = false;
        return Contrat.etat(pretes(), cache == null ? livrees.size() : cache.nombre(), rejets, dernierMotif);
    }

    public synchronized String bilan() {
        return scenes.size() + " scène(s), " + pretes() + " prête(s), " + livrees.size() + " ressource(s) livrée(s), " + receptions.size()
            + " en réception, " + recues + " reçue(s), " + octetsRecus + " octets, " + rejets + " rejet(s)" + (dernierMotif.isEmpty() ? "" : " (" + dernierMotif + ")");
    }
}
