package infinitylink.core.scenes;

import infinitylink.core.scenes.Contrat.Amas;
import infinitylink.core.scenes.Contrat.Cle;
import infinitylink.core.scenes.Contrat.Materiau;
import infinitylink.core.scenes.Lecteur.Refus;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Capacité « modeles » (DOCS/INFINITYLINK-MODELES-PROTOCOLE.md, modèles BlockBench aplatis en quads) : assemblage des
 *  MORCEAU, décodage du corps, puis conversion au format de rendu des maillages (un maillage, un niveau, une instance)
 *  pour un seul moteur de rendu. Positions quantifiées sur les bornes du modèle, deux triangles par quad, un matériau
 *  par (texture, deux faces), découpe alpha à 0,1 (contrat §4). Marqueur « modele:<id> », rendu comme un item. */
public final class ModelesV1 {
    public static final String CAP = "modeles";
    public static final String CANAL = "link/models";
    static final int OP_DEFINIR = 1, OP_RETIRER = 2, OP_VIDER = 3, OP_MORCEAU = 4;
    static final int MAX_CORPS = 8 << 20, MAX_QUADS = 65_536, MAX_TEXTURES = 16, MAX_PNG = 4 << 20;
    /** Au plus 4 assemblages de MORCEAU et 16 Mio en cours ; 256 modèles et 256 Mio de corps connus (comme les scènes). */
    static final int MAX_ASSEMBLAGES = 4, MAX_EN_COURS = 16 << 20, MAX_MODELES = 256;
    static final long MAX_OCTETS_MODELES = 256L << 20;

    private record Assemblage(int total, ByteArrayOutputStream o, int[] suivant) {}

    private final Magasin.Ecouteur ecouteur;
    private final Map<String, Assemblage> morceaux = new HashMap<>();
    private final Map<String, Integer> tailles = new HashMap<>();
    private long octetsModeles;
    private int rejets;
    private String dernier = "";

    public ModelesV1(Magasin.Ecouteur ecouteur) { this.ecouteur = ecouteur; }

    public synchronized int rejets() { return rejets; }
    public synchronized String dernierMotif() { return dernier; }

    public synchronized void recevoir(byte[] brut) {
        try {
            Lecteur l = new Lecteur(brut);
            switch (l.u8()) {
                case OP_DEFINIR -> definir(l.octets(l.reste()));
                case OP_RETIRER -> {
                    String id = Contrat.identifiant(l);
                    l.finir("RETIRER");
                    morceaux.remove(id);
                    Integer t = tailles.remove(id);
                    if (t != null) octetsModeles -= t;
                    ecouteur.retiree("modele:" + id);
                }
                case OP_VIDER -> { l.finir("VIDER"); morceaux.clear(); tailles.clear(); octetsModeles = 0; ecouteur.videe(); }
                case OP_MORCEAU -> {
                    String id = Contrat.identifiant(l);
                    int i = l.varint(), total = l.varint("total", 1, 64);
                    byte[] part = l.octets(l.varint("n", 0, 1 << 20));
                    l.finir("MORCEAU");
                    Assemblage a = morceaux.get(id);
                    if (i == 0) {
                        morceaux.remove(id);
                        if (morceaux.size() >= MAX_ASSEMBLAGES) throw new Refus("plus de 4 modèles en cours d'assemblage");
                        a = new Assemblage(total, new ByteArrayOutputStream(), new int[]{0});
                        morceaux.put(id, a);
                    }
                    else if (a == null || a.total() != total || a.suivant()[0] != i) { morceaux.remove(id); throw new Refus("morceau " + i + " hors séquence"); }
                    if (a.o().size() + part.length > MAX_CORPS) { morceaux.remove(id); throw new Refus("corps de plus de 8 Mio"); }
                    long enCours = part.length;
                    for (Assemblage b : morceaux.values()) enCours += b.o().size();
                    if (enCours > MAX_EN_COURS) { morceaux.remove(id); throw new Refus("plus de 16 Mio de modèles en cours d'assemblage"); }
                    a.o().writeBytes(part);
                    if (++a.suivant()[0] == total) { morceaux.remove(id); definir(a.o().toByteArray()); }
                }
                default -> throw new Refus("opération inconnue");
            }
        } catch (Refus r) {
            rejets++;
            dernier = r.getMessage();
        }
    }

    static Cle empreinte(byte[] o, int d, int n) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(o, d, n);
            return Cle.de(md.digest(), 0);
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /** Corps d'un modèle (§3) : décodé, converti, livré au rendu (textures, niveau, puis scène). */
    void definir(byte[] corps) {
        Lecteur l = new Lecteur(corps);
        String id = Contrat.identifiant(l);
        String nom = l.string("nom", 32767);
        l.varint("version", 1, 1);
        l.varint(); // révision : le contenu fait la clé
        float[] b = new float[6];
        for (int i = 0; i < 6; i++) b[i] = l.f32("bornes");
        int nt = l.varint("n_textures", 0, MAX_TEXTURES);
        Cle[] tex = new Cle[nt];
        Contrat.Texture[] textures = new Contrat.Texture[nt];
        for (int i = 0; i < nt; i++) {
            int w = l.varint("largeur", 1, 4096), h = l.varint("hauteur", 1, 4096), n = l.varint("n", 24, MAX_PNG);
            int d = l.sauter(n);
            byte[] png = java.util.Arrays.copyOfRange(corps, d, d + n);
            // même vérification d'en-tête que TEXTURE (§6.3 des maillages) : IHDR = largeur × hauteur annoncées
            byte[] enTete = new Contrat.Texture(w, h, png).png();
            textures[i] = Contrat.texture(new infinitylink.core.Wire.Out().varInt(1).varInt(w).varInt(h).bytes(enTete).toBytes());
            tex[i] = empreinte(corps, d, n);
        }
        int nq = l.varint("n_quads", 0, MAX_QUADS);
        if (nq == 0) throw new Refus("modèle sans face");
        // grille de quantification sur les bornes annoncées (pas minimal 1e-7 pour un axe plat)
        float[] qo = {b[0], b[1], b[2]}, qp = new float[3];
        for (int k = 0; k < 3; k++) qp[k] = Math.max((b[k + 3] - b[k]) / 65535f, 1e-7f);
        // matériaux : (texture, deux faces)
        Map<Integer, Integer> matIndex = new HashMap<>();
        List<Materiau> mats = new ArrayList<>();
        int[] matDuQuad = new int[nq];
        float[][] q = new float[nq][];
        for (int i = 0; i < nq; i++) {
            int t = l.varint("texture", 0, nt);
            int dr = l.u8();
            float[] v = new float[3 + 20];
            for (int k = 0; k < 23; k++) v[k] = l.f32("quad");
            int cleMat = t << 1 | (dr & 1);
            Integer m = matIndex.get(cleMat);
            if (m == null) {
                m = mats.size();
                matIndex.put(cleMat, m);
                mats.add(new Materiau((dr & 1) | 2, 0xFFFFFFFF, 0.1f, t));
            }
            matDuQuad[i] = m;
            q[i] = v;
        }
        l.finir("corps du modèle");
        Integer avant = tailles.get(id);
        if (avant == null && tailles.size() >= MAX_MODELES) throw new Refus("plus de 256 modèles");
        if (octetsModeles - (avant == null ? 0 : avant) + corps.length > MAX_OCTETS_MODELES) throw new Refus("plus de 256 Mio de modèles");
        // amas : par matériau, 16 384 quads (65 536 sommets) au plus
        ByteArrayOutputStream so = new ByteArrayOutputStream(), io = new ByteArrayOutputStream();
        List<Amas> amas = new ArrayList<>();
        int sommets = 0, indices = 0;
        for (int m = 0; m < mats.size(); m++) {
            int dansAmas = 0, base = sommets, premier = indices;
            int[] bq = {65535, 65535, 65535, 0, 0, 0};
            for (int i = 0; i < nq; i++) {
                if (matDuQuad[i] != m) continue;
                if (dansAmas == 16_384) {
                    amas.add(new Amas(m, bq, premier, indices - premier, base, sommets - base));
                    dansAmas = 0; base = sommets; premier = indices; bq = new int[]{65535, 65535, 65535, 0, 0, 0};
                }
                float[] v = q[i];
                byte nx = (byte) Math.round(Math.max(-1, Math.min(1, v[0])) * 127), ny = (byte) Math.round(Math.max(-1, Math.min(1, v[1])) * 127),
                     nz = (byte) Math.round(Math.max(-1, Math.min(1, v[2])) * 127);
                for (int s = 0; s < 4; s++) {
                    int o = 3 + 5 * s;
                    int[] qq = new int[3];
                    for (int k = 0; k < 3; k++) {
                        qq[k] = Math.max(0, Math.min(65535, Math.round((v[o + k] - qo[k]) / qp[k])));
                        bq[k] = Math.min(bq[k], qq[k]); bq[k + 3] = Math.max(bq[k + 3], qq[k]);
                    }
                    for (int k = 0; k < 3; k++) { so.write(qq[k] & 0xFF); so.write(qq[k] >>> 8); }
                    so.write(0); so.write(0);
                    so.write(nx); so.write(ny); so.write(nz); so.write(0);
                    for (int k = 3; k < 5; k++) { int f = Float.floatToIntBits(v[o + k]); so.write(f); so.write(f >>> 8); so.write(f >>> 16); so.write(f >>> 24); }
                    so.write(0xFF); so.write(0xFF); so.write(0xFF); so.write(0xFF);
                }
                int s0 = sommets - base;
                for (int x : new int[]{0, 1, 2, 0, 2, 3}) { int y = s0 + x; io.write(y & 0xFF); io.write(y >>> 8); }
                sommets += 4;
                indices += 6;
                dansAmas++;
            }
            if (dansAmas > 0) amas.add(new Amas(m, bq, premier, indices - premier, base, sommets - base));
        }
        Contrat.NiveauGpu n = new Contrat.NiveauGpu(qo, qp, tex, mats.toArray(new Materiau[0]), amas.toArray(new Amas[0]),
                so.toByteArray(), io.toByteArray(), 2 * nq);
        Cle cleNiveau = empreinte(corps, 0, corps.length);
        for (int i = 0; i < nt; i++) ecouteur.texture(tex[i], textures[i]);
        ecouteur.niveau(cleNiveau, n);
        List<Contrat.Ressource> res = new ArrayList<>();
        res.add(new Contrat.Ressource(cleNiveau, Contrat.GENRE_NIVEAU, corps.length));
        for (int i = 0; i < nt; i++) res.add(new Contrat.Ressource(tex[i], Contrat.GENRE_TEXTURE, textures[i].png().length));
        Contrat.Description d = new Contrat.Description(nom, false, b, res.toArray(new Contrat.Ressource[0]),
                new Contrat.Maillage[]{new Contrat.Maillage(b, new Contrat.Niveau[]{new Contrat.Niveau(0, 0f, 2 * nq)})},
                new Contrat.Instance[]{new Contrat.Instance(0, new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0}, 1f)},
                new Contrat.Cellule[]{new Contrat.Cellule(b, 0, 1)});
        tailles.put(id, corps.length);
        octetsModeles += corps.length - (avant == null ? 0 : avant);
        ecouteur.scene("modele:" + id, d, true);
    }
}
