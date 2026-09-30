import infinitylink.core.scenes.CacheDisque;
import infinitylink.core.scenes.Choix;
import infinitylink.core.scenes.Contrat;
import infinitylink.core.scenes.Lecteur;
import infinitylink.core.scenes.Magasin;
import infinitylink.core.scenes.ModelesV1;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/** Java pur : capacités « maillages » et « modeles » (InfinityLink 1.1.4) — octets de référence du contrat
 *  (DOCS/INFINITYLINK-MAILLAGES-PROTOCOLE.md §10, INFINITYLINK-MODELES-PROTOCOLE.md §5), refus, magasin (révisions,
 *  morceaux, BESOIN, redemande, cache disque), choix du niveau. -Dscenes.vidage=<dossier> : décode aussi un vidage réel
 *  du client de test (scene_*.bin, ressources/*.bin) avec le décodeur du mod. */
public final class ScenesTest {
    static int ok, ko;
    static void check(String n, boolean c) { if (c) ok++; else { ko++; System.out.println("ECHEC " + n); } }
    static byte[] hex(String h) { return HexFormat.of().parseHex(h); }

    static final String NIVEAU_REF = "01000000000000000000000000378000803780008033d6bf95000100ffffffff3f000000000100000000000000ffffffff00000301000000000000000000007f000000000000000000ffffffffffff00000000000000007f000000000000000000ffffffff0000ffff0000000000007f000000000000000000ffffffff000001000200";
    static final String CLE_REF = "cf8c84102f3244e72bc7b0bdea54c08f";
    static final String SCENE_REF = "01037265660100019f010103526566000000000042800000000000003f800000428200000000000001cf8c84102f3244e72bc7b0bdea54c08f018301010000000000000000000000003f8000003f800000000000000100000000000101003f800000000000000000000000000000000000003f800000000000004280000000000000000000003f80000000000000010000000042800000000000003f80000042820000000000000001";
    static final String MODELE_REF = "0103726566035265660103bf0000003f800000bf0000003f0000003f8000003f00000000010000000000003f80000000000000bf0000003f800000bf0000000000000000000000bf0000003f8000003f000000000000003f8000003f0000003f8000003f0000003f8000003f8000003f0000003f800000bf0000003f80000000000000";

    static boolean refuse(Runnable r) { try { r.run(); return false; } catch (Lecteur.Refus e) { return true; } }

    /** Écouteur qui garde tout. */
    static final class Journal implements Magasin.Ecouteur {
        final List<String> scenes = new ArrayList<>();
        final List<Contrat.NiveauGpu> niveaux = new ArrayList<>();
        final List<Contrat.Cle> cles = new ArrayList<>();
        int textures, videes, retirees;
        Contrat.Description derniere;
        boolean commeItem;
        public void scene(String m, Contrat.Description d, boolean ci) { scenes.add(m); derniere = d; commeItem = ci; }
        public void retiree(String m) { retirees++; }
        public void videe() { videes++; }
        public void niveau(Contrat.Cle k, Contrat.NiveauGpu n) { niveaux.add(n); cles.add(k); }
        public void texture(Contrat.Cle k, Contrat.Texture t) { textures++; }
    }

    /** Message SCÈNE de `desc` en `n` morceaux. */
    static List<byte[]> morceauxScene(String id, int rev, byte[] desc, int n) {
        List<byte[]> l = new ArrayList<>();
        int t = (desc.length + n - 1) / n;
        for (int i = 0; i < n; i++) {
            byte[] p = Arrays.copyOfRange(desc, Math.min(i * t, desc.length), Math.min((i + 1) * t, desc.length));
            l.add(new infinitylink.core.Wire.Out().u8(1).string(id).varInt(rev).varInt(i).varInt(n).varInt(p.length).bytes(p).toBytes());
        }
        return l;
    }

    static byte[] morceauRessource(byte[] cle, int dec, byte[] p) {
        return new infinitylink.core.Wire.Out().u8(2).bytes(cle).varInt(dec).varInt(p.length).bytes(p).toBytes();
    }

    public static void main(String[] a) throws Exception {
        // §10 : NIVEAU d'un triangle
        byte[] niv = hex(NIVEAU_REF);
        Contrat.NiveauGpu n = Contrat.niveau(niv, 1);
        check("niveau : 1 amas, 3 sommets, 1 triangle", n.amas().length == 1 && n.amas()[0].nSommets() == 3 && n.triangles() == 1);
        check("niveau : q_pas", Math.abs(n.qp()[0] - 1 / 65535f) < 1e-9 && n.qp()[2] == 1e-7f);
        check("niveau : index 0 1 2", Arrays.equals(n.index(), new byte[]{0, 0, 1, 0, 2, 0}));
        check("niveau : sommets 24 octets", n.sommets().length == 72 && (n.sommets()[10] & 0xFF) == 127 && (n.sommets()[24] & 0xFF) == 0xFF);
        check("niveau : matériau blanc", n.materiaux().length == 1 && n.materiaux()[0].rgba() == 0xFFFFFFFF && n.materiaux()[0].seuil() == 0.5f);
        check("niveau : triangles annoncés faux", refuse(() -> Contrat.niveau(niv, 2)));
        byte[] tropLong = Arrays.copyOf(niv, niv.length + 1);
        check("niveau : octet en trop", refuse(() -> Contrat.niveau(tropLong, 1)));
        byte[] indexFaux = niv.clone();
        indexFaux[indexFaux.length - 2] = 3; // index 3 ≥ 3 sommets
        check("niveau : index hors de l'amas", refuse(() -> Contrat.niveau(indexFaux, 1)));
        check("clé = SHA-256[0..16]", Contrat.Cle.de(java.security.MessageDigest.getInstance("SHA-256").digest(niv), 0).hex().equals(CLE_REF));
        // §10 : SCÈNE
        Contrat.Message m = Contrat.message(hex(SCENE_REF));
        check("scène : message", m instanceof Contrat.Scene s && s.id().equals("ref") && s.revision() == 1 && s.total() == 1 && s.donnees().length == 159);
        Contrat.Description d = Contrat.description(((Contrat.Scene) m).donnees());
        check("description", d.nom().equals("Ref") && !d.provisoire() && d.ressources().length == 1 && d.ressources()[0].taille() == 131
                && d.ressources()[0].cle().hex().equals(CLE_REF) && d.maillages().length == 1 && d.instances()[0].m()[7] == 64 && d.cellules().length == 1);
        // autres octets du §10
        check("BESOIN", Arrays.equals(Contrat.besoin("ref", 1, new int[]{0, 1}), hex("0103726566010200" + "01")));
        check("RETIRER", Contrat.message(hex("03026162")) instanceof Contrat.Retirer r && r.id().equals("ab"));
        check("VIDER", Contrat.message(hex("04")) instanceof Contrat.Vider);
        // refus du §3 et du §6.1
        check("varint 5e octet > 0x07", refuse(() -> new Lecteur(hex("8080808008")).varint()));
        check("id interdit", refuse(() -> Contrat.message(hex("03024142"))));
        byte[] desc = ((Contrat.Scene) m).donnees();
        byte[] trou = desc.clone();
        trou[trou.length - 2] = 1; // cellule qui commence à l'instance 1 : trou
        check("cellules : trou", refuse(() -> Contrat.description(trou)));
        byte[] provisoireInconnu = desc.clone();
        provisoireInconnu[5] = 2; // drapeau réservé
        check("drapeaux réservés", refuse(() -> Contrat.description(provisoireInconnu)));
        check("TEXTURE : IHDR différent", refuse(() -> Contrat.texture(new infinitylink.core.Wire.Out().varInt(1).varInt(2).varInt(1)
                .bytes(hex("89504e470d0a1a0a0000000d494844520000000100000001080600000000")).toBytes())));

        // magasin : description en 2 morceaux, BESOIN, ressource en 2 morceaux, vérification, décodage
        List<byte[]> envois = new ArrayList<>();
        Journal j = new Journal();
        Path dossier = Files.createTempDirectory("infinitylink-scenes");
        CacheDisque cache = new CacheDisque(dossier, 1 << 20);
        Magasin mg = new Magasin(cache, envois::add, Runnable::run, j, false);
        for (byte[] x : morceauxScene("ref", 3, desc, 2)) mg.recevoir(x);
        check("magasin : scène courante", j.scenes.equals(List.of("scene:ref")) && !j.commeItem);
        check("magasin : BESOIN de la ressource", envois.size() == 1 && Arrays.equals(envois.get(0), Contrat.besoin("ref", 3, new int[]{0})));
        byte[] cle = hex(CLE_REF);
        mg.recevoir(morceauRessource(cle, 0, Arrays.copyOf(niv, 100)));
        mg.recevoir(morceauRessource(cle, 100, Arrays.copyOfRange(niv, 100, niv.length)));
        check("magasin : niveau livré", j.niveaux.size() == 1 && j.niveaux.get(0).triangles() == 1 && cache.contient(j.cles.get(0)));
        check("magasin : scène prête", mg.pretes() == 1 && mg.etatSiChange() != null);
        for (byte[] x : morceauxScene("ref", 3, desc, 1)) mg.recevoir(x);
        check("magasin : même révision ignorée", j.scenes.size() == 1 && envois.size() == 1);
        for (byte[] x : morceauxScene("ref", 2, desc, 1)) mg.recevoir(x);
        check("magasin : révision plus ancienne ignorée", j.scenes.size() == 1);
        mg.recevoir(hex("04"));
        check("magasin : VIDER", j.videes == 1 && mg.pretes() == 0);
        // décalage inattendu : rejet, puis redemande sur la révision courante
        envois.clear();
        Journal j2 = new Journal();
        Magasin sansCache = new Magasin(null, envois::add, Runnable::run, j2, false);
        for (byte[] x : morceauxScene("ref", 1, desc, 1)) sansCache.recevoir(x);
        sansCache.recevoir(morceauRessource(cle, 0, Arrays.copyOf(niv, 50)));
        sansCache.recevoir(morceauRessource(cle, 60, Arrays.copyOfRange(niv, 60, niv.length)));
        check("magasin : décalage faux, redemandé", envois.size() == 2 && Arrays.equals(envois.get(1), Contrat.besoin("ref", 1, new int[]{0})) && j2.niveaux.isEmpty());
        sansCache.recevoir(morceauRessource(cle, 0, niv));
        check("magasin : ressource reçue après redemande", j2.niveaux.size() == 1);
        // clé fausse : rejetée
        Journal j3 = new Journal();
        envois.clear();
        Magasin mf = new Magasin(null, envois::add, Runnable::run, j3, false);
        for (byte[] x : morceauxScene("ref", 1, desc, 1)) mf.recevoir(x);
        byte[] faux = niv.clone(); faux[5] ^= 1;
        mf.recevoir(morceauRessource(cle, 0, faux));
        check("magasin : clé fausse rejetée et redemandée", j3.niveaux.isEmpty() && envois.size() == 2 && mf.bilan().contains("clé fausse"));
        // session suivante : la ressource est sur disque, rien n'est demandé au serveur
        envois.clear();
        Journal j4 = new Journal();
        Magasin mg2 = new Magasin(new CacheDisque(dossier, 1 << 20), envois::add, Runnable::run, j4, false);
        for (byte[] x : morceauxScene("ref", 7, desc, 1)) mg2.recevoir(x);
        check("cache disque : BESOIN vide", envois.size() == 1 && Arrays.equals(envois.get(0), Contrat.besoin("ref", 7, new int[0])));
        check("cache disque : niveau relu", j4.niveaux.size() == 1 && mg2.pretes() == 1);

        // revue 1.1.4 : une réception commencée est menée à son terme après RETIRER (§4), sans perdre sa place
        envois.clear();
        Journal jr = new Journal();
        Magasin mr = new Magasin(null, envois::add, Runnable::run, jr, false);
        for (byte[] x : morceauxScene("ref", 1, desc, 1)) mr.recevoir(x);
        mr.recevoir(morceauRessource(cle, 0, Arrays.copyOf(niv, 64)));
        mr.recevoir(hex("0303726566")); // RETIRER « ref »
        mr.recevoir(morceauRessource(cle, 64, Arrays.copyOfRange(niv, 64, niv.length)));
        check("réception menée à terme après RETIRER", jr.retirees == 1 && jr.niveaux.size() == 1 && mr.bilan().contains("0 en réception") && mr.bilan().contains("0 rejet"));
        // rejet venu du rendu (PNG illisible) : compté, redemandé
        for (byte[] x : morceauxScene("ref", 2, desc, 1)) mr.recevoir(x);
        int avantRejet = envois.size();
        mr.rejetExterne(Contrat.Cle.de(cle, 0), "PNG illisible");
        check("rejet externe compté et redemandé", mr.bilan().contains("1 rejet") && envois.size() == avantRejet + 1);
        // cache disque : LRU en ordre d'accès, éviction jusqu'à 90 % du plafond
        Path dl = Files.createTempDirectory("infinitylink-lru");
        CacheDisque lru = new CacheDisque(dl, 1000);
        Contrat.Cle k1 = Contrat.Cle.de(new byte[16], 0), k2 = Contrat.Cle.de(hex("01000000000000000000000000000000"), 0),
                k3 = Contrat.Cle.de(hex("02000000000000000000000000000000"), 0), k4 = Contrat.Cle.de(hex("03000000000000000000000000000000"), 0);
        lru.ecrire(k1, new byte[400]); lru.ecrire(k2, new byte[400]);
        lru.lire(k1); // k1 lue : k2 devient la moins récente
        lru.ecrire(k3, new byte[400]);
        check("cache : la moins récemment lue part", lru.contient(k1) && !lru.contient(k2) && lru.contient(k3) && lru.octets() == 800 && lru.taille(k3) == 400);
        lru.ecrire(k4, new byte[400]);
        check("cache : éviction sous 90 %", lru.octets() <= 900 && lru.contient(k4) && !Files.exists(dl.resolve(k2.hex() + ".bin")));

        // modeles (contrat v1 §5) : une face up d'un cube java_block → maillage d'un quad (2 triangles), comme un item
        Journal jm = new Journal();
        ModelesV1 mv = new ModelesV1(jm);
        mv.recevoir(hex(MODELE_REF));
        check("modeles : scène modele:ref", jm.scenes.equals(List.of("modele:ref")) && jm.commeItem && mv.rejets() == 0);
        Contrat.NiveauGpu q = jm.niveaux.get(0);
        check("modeles : 4 sommets, 2 triangles", q.amas().length == 1 && q.amas()[0].nSommets() == 4 && q.triangles() == 2);
        check("modeles : découpe alpha, une face", q.materiaux()[0].decoupe() && !q.materiaux()[0].deuxFaces() && q.materiaux()[0].texture() == 0);
        // morceaux v1 : même modèle en 3 MORCEAU
        byte[] corps = Arrays.copyOfRange(hex(MODELE_REF), 1, hex(MODELE_REF).length);
        Journal jm2 = new Journal();
        ModelesV1 mv2 = new ModelesV1(jm2);
        int t = (corps.length + 2) / 3;
        for (int i = 0; i < 3; i++) {
            byte[] p = Arrays.copyOfRange(corps, i * t, Math.min((i + 1) * t, corps.length));
            mv2.recevoir(new infinitylink.core.Wire.Out().u8(4).string("ref").varInt(i).varInt(3).varInt(p.length).bytes(p).toBytes());
        }
        check("modeles : morceaux", jm2.niveaux.size() == 1 && Arrays.equals(jm2.niveaux.get(0).sommets(), q.sommets()));
        // bornes (revue 1.1.4) : 4 assemblages de MORCEAU au plus, 256 modèles au plus
        Journal jm3 = new Journal();
        ModelesV1 mv3 = new ModelesV1(jm3);
        for (int i = 0; i < 5; i++)
            mv3.recevoir(new infinitylink.core.Wire.Out().u8(4).string("m" + i).varInt(0).varInt(2).varInt(8).bytes(new byte[8]).toBytes());
        check("modeles : 5e assemblage refusé", mv3.rejets() == 1 && mv3.dernierMotif().contains("4 modèles"));
        byte[] ref = hex(MODELE_REF);
        int idLen = 3; // « ref »
        Journal jm4 = new Journal();
        ModelesV1 mv4 = new ModelesV1(jm4);
        for (int i = 0; i < 257; i++) {
            String id = String.format("r%02x", i & 0xFF) + (i >= 256 ? "z" : "");
            byte[] corps2 = new infinitylink.core.Wire.Out().u8(1).string(id).bytes(Arrays.copyOfRange(ref, 2 + idLen, ref.length)).toBytes();
            mv4.recevoir(corps2);
        }
        check("modeles : 256 au plus", jm4.scenes.size() == 256 && mv4.rejets() == 1 && mv4.dernierMotif().contains("256 modèles"));
        mv4.recevoir(hex("0203723030")); // RETIRER « r00 » : une place se libère
        mv4.recevoir(new infinitylink.core.Wire.Out().u8(1).string("nouveau").bytes(Arrays.copyOfRange(ref, 2 + idLen, ref.length)).toBytes());
        check("modeles : place rendue par RETIRER", jm4.scenes.size() == 257 && jm4.retirees == 1);

        // choix du niveau
        float[] e = {0, 0.1f, 0.5f, 2f};
        boolean[] tous = {true, true, true, true};
        check("choix : près → niveau 0", Choix.niveau(e, tous, 1000, 2, -1) == 0);
        check("choix : loin → le plus grossier", Choix.niveau(e, tous, 0.5, 2, -1) == 3);
        check("choix : hystérésis garde le plus fin", Choix.niveau(e, tous, 3.5, 2, 1) == 1 && Choix.niveau(e, tous, 3.5, 2, -1) == 2);
        check("choix : absent → plus grossier disponible", Choix.niveau(e, new boolean[]{true, false, false, true}, 10, 2, -1) == 3);
        check("choix : rien", Choix.niveau(e, new boolean[4], 10, 2, -1) == -1);
        check("budget", Choix.facteur(1, 3_000_000, 2_000_000) == 1.25 && Choix.facteur(2, 100, 2_000_000) < 2);

        // tronc : perspective 90°, carré, vue vers -z (rotation identité), matrice de clip colonne majeure
        float fo = 1f;          // 1 / tan(45°)
        float[] clip = {fo, 0, 0, 0, 0, fo, 0, 0, 0, 0, -1.0001f, -1, 0, 0, -0.1f, 0};
        double[] pl = infinitylink.core.scenes.Selection.plans(clip);
        check("tronc : devant", infinitylink.core.scenes.Selection.visible(pl, new double[]{-1, -1, -11, 1, 1, -9}));
        check("tronc : derrière", !infinitylink.core.scenes.Selection.visible(pl, new double[]{-1, -1, 9, 1, 1, 11}));
        check("tronc : à gauche hors champ", !infinitylink.core.scenes.Selection.visible(pl, new double[]{-40, -1, -11, -30, 1, -9}));
        check("tronc : à cheval", infinitylink.core.scenes.Selection.visible(pl, new double[]{-40, -1, -11, 0, 1, -9}));
        // boîte transformée : rotation de 90° autour de y puis translation
        double[] rot = {0, 0, 1, 5, 0, 1, 0, 0, -1, 0, 0, 0};
        double[] bt = new double[6];
        infinitylink.core.scenes.Selection.boite(rot, new float[]{0, 0, 0, 2, 1, 1}, 0, bt);
        check("boîte tournée", Arrays.equals(bt, new double[]{5, 0, -2, 6, 1, 0}));
        check("distance à la boîte", Math.abs(infinitylink.core.scenes.Selection.distance(new double[]{3, -1, -1, 4, 1, 1}) - 3) < 1e-12
                && infinitylink.core.scenes.Selection.distance(new double[]{-1, -1, -1, 1, 1, 1}) == 0);
        // parcours de la scène de référence (un triangle, instance à y = 64) posée à 10 blocs devant la caméra
        infinitylink.core.scenes.Plan plan = new infinitylink.core.scenes.Plan("scene:ref", d, false, false);
        check("plan : boîte de l'instance", plan.boitesI[1] <= 64 && plan.boitesI[4] >= 65 && plan.echelleI[0] == 1 && plan.maillagesCites == 1);
        List<Contrat.Cle> voulues = new ArrayList<>();
        int[] sortis = {0, -1};
        infinitylink.core.scenes.Selection.Ressources toutes = new infinitylink.core.scenes.Selection.Ressources() {
            public boolean prete(Contrat.Cle k) { return true; }
            public void vouloir(Contrat.Cle k) { voulues.add(k); }
        };
        infinitylink.core.scenes.Selection.Sortie sortie = (i, c, mi, l, mm, dist, b) -> { sortis[0]++; sortis[1] = l; };
        infinitylink.core.scenes.Selection.Parametres pa = new infinitylink.core.scenes.Selection.Parametres();
        byte[] prec = new byte[1];
        double[] devant = {1, 0, 0, -0.5, 0, 1, 0, -64.5, 0, 0, 1, -10};
        infinitylink.core.scenes.Selection.parcourir(plan, devant, pl, pa, prec, toutes, sortie, 1);
        check("parcours : instance devant dessinée au niveau 0", sortis[0] == 1 && sortis[1] == 0 && voulues.isEmpty() && prec[0] == 1);
        double[] derriere = {1, 0, 0, -0.5, 0, 1, 0, -64.5, 0, 0, 1, 10};
        infinitylink.core.scenes.Selection.parcourir(plan, derriere, pl, pa, prec, toutes, sortie, 2);
        check("parcours : cellule derrière écartée", sortis[0] == 1);
        infinitylink.core.scenes.Selection.Ressources aucune = new infinitylink.core.scenes.Selection.Ressources() {
            public boolean prete(Contrat.Cle k) { return false; }
            public void vouloir(Contrat.Cle k) { voulues.add(k); }
        };
        infinitylink.core.scenes.Selection.parcourir(plan, devant, pl, pa, prec, aucune, sortie, 3);
        check("parcours : niveau absent demandé, rien dessiné", sortis[0] == 1 && voulues.size() == 1 && voulues.get(0).hex().equals(CLE_REF));
        pa.distanceMax = 5;
        infinitylink.core.scenes.Selection.parcourir(plan, devant, pl, pa, prec, toutes, sortie, 4);
        check("parcours : au-delà de la distance", sortis[0] == 1);
        check("économie : niveau 0 écarté au-delà de 2 niveaux seulement", new infinitylink.core.scenes.Plan("scene:ref", d, false, true).permis[0][0]);

        // vidage réel du client de test (serveur modeles_3d)
        String v = System.getProperty("scenes.vidage");
        if (v != null) {
            Path dv = Path.of(v);
            int nd = 0, nr = 0;
            long tri = 0;
            try (var ls = Files.list(dv)) {
                for (Path f : (Iterable<Path>) ls::iterator) {
                    if (!f.getFileName().toString().startsWith("scene_")) continue;
                    Contrat.Description dd = Contrat.description(Files.readAllBytes(f));
                    nd++;
                    for (Contrat.Maillage mm : dd.maillages())
                        for (Contrat.Niveau nn : mm.niveaux()) {
                            Contrat.Ressource r = dd.ressources()[nn.ressource()];
                            Path fr = dv.resolve("ressources").resolve(r.cle().hex() + ".bin");
                            if (!Files.exists(fr)) continue;
                            Contrat.NiveauGpu g = Contrat.niveau(Files.readAllBytes(fr), nn.triangles());
                            nr++;
                            tri += g.triangles();
                        }
                    for (Contrat.Ressource r : dd.ressources())
                        if (r.genre() == Contrat.GENRE_TEXTURE) { Contrat.texture(Files.readAllBytes(dv.resolve("ressources").resolve(r.cle().hex() + ".bin"))); nr++; }
                }
            }
            System.out.println("vidage : " + nd + " description(s), " + nr + " ressource(s) décodée(s), " + tri + " triangles");
            check("vidage décodé", nd > 0 && nr > 0);
        }
        System.out.println("ScenesTest : " + ok + " contrôles OK" + (ko > 0 ? ", " + ko + " ÉCHECS" : ""));
        if (ko > 0) System.exit(1);
    }
}
