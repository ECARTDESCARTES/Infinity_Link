package infinitylink.core.scenes;

import infinitylink.core.Wire;
import infinitylink.core.scenes.Lecteur.Refus;

import java.util.HashMap;
import java.util.Map;

/** Contrat « maillages » (DOCS/INFINITYLINK-MAILLAGES-PROTOCOLE.md, version 1) : description de scène (§6.1),
 *  ressources NIVEAU (§6.2) et TEXTURE (§6.3), messages serveur → client (§4) et client → serveur (§5), limites (§11).
 *  Java pur. Chaque décodeur valide tout AVANT l'usage et refuse (Refus) au premier écart. */
public final class Contrat {
    private Contrat() {}

    public static final String CAP = "maillages";
    public static final String CANAL = "link/meshes";
    public static final String CANAL_RETOUR = "link/meshes_have";
    public static final int OP_SCENE = 1, OP_RESSOURCE = 2, OP_RETIRER = 3, OP_VIDER = 4;
    public static final int OP_BESOIN = 1, OP_ETAT = 2;
    public static final int GENRE_NIVEAU = 1, GENRE_TEXTURE = 2;
    // §11
    public static final int MAX_ID = 64, MAX_NOM = 128, MAX_MORCEAUX = 256, MAX_MORCEAU = 64 * 1024, MAX_DESCRIPTION = 16 << 20;
    public static final int MAX_ASSEMBLAGES = 4, MAX_SCENES = 256, MAX_RESSOURCES = 4096, MAX_RECEPTIONS = 8;
    public static final long MAX_EN_VOL = 64L << 20, MAX_OCTETS_SCENE = 1L << 30;
    public static final int MAX_MAILLAGES = 4096, MAX_INSTANCES = 65_536, MAX_CELLULES = 4096, MAX_NIVEAUX = 8;
    public static final int MAX_NIVEAU = 32 << 20, MAX_TEXTURE = 16 << 20, MAX_AMAS = 4096, MAX_MATERIAUX = 256, MAX_TEXTURES = 16;
    public static final int MAX_SOMMETS = 65_536, MAX_TRIANGLES = 65_536, MAX_COTE = 4096;
    public static final float MAX_COORD = 100_000f;
    public static final int SOMMET = 24;

    /** Clé d'une ressource : 16 octets (SHA-256 tronqué), comparée par valeur. */
    public record Cle(long a, long b) {
        public static Cle de(byte[] o, int d) {
            long x = 0, y = 0;
            for (int i = 0; i < 8; i++) { x = x << 8 | (o[d + i] & 0xFF); y = y << 8 | (o[d + 8 + i] & 0xFF); }
            return new Cle(x, y);
        }
        public byte[] octets() {
            byte[] o = new byte[16];
            for (int i = 0; i < 8; i++) { o[i] = (byte) (a >>> (56 - 8 * i)); o[8 + i] = (byte) (b >>> (56 - 8 * i)); }
            return o;
        }
        public String hex() { return String.format("%016x%016x", a, b); }
    }

    static Cle cle(Lecteur l) { int d = l.sauter(16); return Cle.de(l.source(), d); }

    static float coord(Lecteur l, String quoi) {
        float v = l.f32(quoi);
        if (Math.abs(v) > MAX_COORD) throw new Refus(quoi + " hors de ±100 000");
        return v;
    }

    /** min x, y, z puis max x, y, z, finis, dans ±100 000, min ≤ max. */
    static float[] bornes(Lecteur l, String quoi) {
        float[] b = new float[6];
        for (int i = 0; i < 6; i++) b[i] = coord(l, quoi);
        for (int i = 0; i < 3; i++) if (b[i] > b[i + 3]) throw new Refus(quoi + " : min > max");
        return b;
    }

    // ------------------------------------------------------------------------------------------ description (§6.1)
    public record Ressource(Cle cle, int genre, int taille) {}
    public record Niveau(int ressource, float erreur, int triangles) {}
    public record Maillage(float[] bornes, Niveau[] niveaux) {}
    /** Instance : maillage, matrice en lignes (m00 m01 m02 tx · m10 m11 m12 ty · m20 m21 m22 tz), déterminant. */
    public record Instance(int maillage, float[] m, float det) {}
    public record Cellule(float[] bornes, int premiere, int n) {}
    public record Description(String nom, boolean provisoire, float[] bornes, Ressource[] ressources, Maillage[] maillages,
                              Instance[] instances, Cellule[] cellules) {}

    public static Description description(byte[] o) {
        Lecteur l = new Lecteur(o);
        l.varint("version", 1, 1);
        String nom = l.string("nom", MAX_NOM);
        int drapeaux = l.u8();
        if ((drapeaux & ~1) != 0) throw new Refus("drapeaux réservés non nuls");
        float[] bornes = bornes(l, "bornes de la scène");
        int nr = l.varint("n_ressources", 0, MAX_RESSOURCES);
        Ressource[] res = new Ressource[nr];
        Map<Cle, Ressource> vues = new HashMap<>();
        long total = 0;
        for (int i = 0; i < nr; i++) {
            Cle k = cle(l);
            int genre = l.u8();
            if (genre != GENRE_NIVEAU && genre != GENRE_TEXTURE) throw new Refus("ressource " + i + " : genre " + genre);
            int taille = l.varint("taille", 1, genre == GENRE_NIVEAU ? MAX_NIVEAU : MAX_TEXTURE);
            Ressource r = new Ressource(k, genre, taille);
            Ressource d = vues.putIfAbsent(k, r);
            if (d != null && (d.genre != genre || d.taille != taille)) throw new Refus("clé " + k.hex() + " annoncée deux fois autrement");
            total += taille;
            res[i] = r;
        }
        if (total > MAX_OCTETS_SCENE) throw new Refus("ressources de plus de 1 Gio");
        int nm = l.varint("n_maillages", 0, MAX_MAILLAGES);
        Maillage[] ms = new Maillage[nm];
        for (int i = 0; i < nm; i++) {
            float[] b = bornes(l, "bornes du maillage " + i);
            int nn = l.varint("n_niveaux", 1, MAX_NIVEAUX);
            Niveau[] ns = new Niveau[nn];
            float avant = -1;
            for (int k = 0; k < nn; k++) {
                int r = l.varint("ressource du niveau", 0, nr - 1);
                if (res[r].genre != GENRE_NIVEAU) throw new Refus("niveau " + k + " du maillage " + i + " : ressource non NIVEAU");
                float e = l.f32("erreur");
                if (e < 0 || e <= avant) throw new Refus("maillage " + i + " : erreurs non strictement croissantes");
                avant = e;
                int t = l.varint("triangles", 1, Integer.MAX_VALUE);
                ns[k] = new Niveau(r, e, t);
            }
            ms[i] = new Maillage(b, ns);
        }
        int ni = l.varint("n_instances", 0, MAX_INSTANCES);
        Instance[] is = new Instance[ni];
        for (int i = 0; i < ni; i++) {
            int m = l.varint("maillage de l'instance", 0, nm - 1);
            float[] x = new float[12];
            for (int k = 0; k < 12; k++) x[k] = l.f32("matrice");
            for (int k = 3; k < 12; k += 4) if (Math.abs(x[k]) > MAX_COORD) throw new Refus("translation hors de ±100 000");
            float det = x[0] * (x[5] * x[10] - x[6] * x[9]) - x[1] * (x[4] * x[10] - x[6] * x[8]) + x[2] * (x[4] * x[9] - x[5] * x[8]);
            if (!(Math.abs(det) >= 1e-9f)) throw new Refus("instance " + i + " : déterminant nul");
            is[i] = new Instance(m, x, det);
        }
        int nc = l.varint("n_cellules", 0, MAX_CELLULES);
        Cellule[] cs = new Cellule[nc];
        int suivante = 0;
        for (int i = 0; i < nc; i++) {
            float[] b = bornes(l, "bornes de la cellule " + i);
            int p = l.varint("première", 0, MAX_INSTANCES), n = l.varint("n", 0, MAX_INSTANCES);
            if (p != suivante || (long) p + n > ni) throw new Refus("cellules : trou ou recouvrement à la cellule " + i);
            suivante = p + n;
            cs[i] = new Cellule(b, p, n);
        }
        if (suivante != ni) throw new Refus("cellules : " + (ni - suivante) + " instances hors cellule");
        l.finir("description");
        return new Description(nom, (drapeaux & 1) != 0, bornes, res, ms, is, cs);
    }

    // ------------------------------------------------------------------------------------------ NIVEAU (§6.2)
    public record Materiau(int drapeaux, int rgba, float seuil, int texture) {
        public boolean deuxFaces() { return (drapeaux & 1) != 0; }
        public boolean decoupe() { return (drapeaux & 2) != 0; }
        public boolean translucide() { return (drapeaux & 4) != 0; }
    }
    /** Amas : matériau, bornes dans la grille q (u16 ×6), premier indice et nombre d'indices dans `index`, premier
     *  sommet dans `sommets` (en sommets de 24 octets), nombre de sommets. */
    public record Amas(int materiau, int[] bornesQ, int premierIndice, int nIndices, int baseSommet, int nSommets) {}

    /** Ressource NIVEAU décodée et validée, prête pour le GPU : sommets de 24 octets petit-boutistes et index u16
     *  petit-boutistes recopiés d'un seul tenant (les index restent locaux à leur amas : baseSommet au dessin). */
    public record NiveauGpu(float[] qo, float[] qp, Cle[] textures, Materiau[] materiaux, Amas[] amas, byte[] sommets,
                            byte[] index, int triangles) {
        public long octets() { return (long) sommets.length + index.length; }
    }

    public static NiveauGpu niveau(byte[] o, int trianglesAnnonces) {
        Lecteur l = new Lecteur(o);
        l.varint("version", 1, 1);
        float[] qo = new float[3], qp = new float[3];
        for (int i = 0; i < 3; i++) qo[i] = coord(l, "q_origine");
        for (int i = 0; i < 3; i++) { qp[i] = l.f32("q_pas"); if (!(qp[i] > 0)) throw new Refus("q_pas ≤ 0"); }
        int nt = l.varint("n_textures", 0, MAX_TEXTURES);
        Cle[] tex = new Cle[nt];
        for (int i = 0; i < nt; i++) tex[i] = cle(l);
        int nm = l.varint("n_matériaux", 1, MAX_MATERIAUX);
        Materiau[] mats = new Materiau[nm];
        for (int i = 0; i < nm; i++) {
            int d = l.u8();
            if ((d & ~7) != 0) throw new Refus("matériau " + i + " : drapeaux réservés non nuls");
            int rgba = l.i32();
            float s = l.f32("seuil_alpha");
            if (s < 0 || s > 1) throw new Refus("seuil_alpha hors de [0, 1]");
            int t = l.varint("texture", 0, nt);
            mats[i] = new Materiau(d, rgba, s, t);
        }
        int na = l.varint("n_amas", 1, MAX_AMAS);
        // deux passes : validation et tailles, puis copie d'un seul tenant
        int[][] pos = new int[na][];
        long totalS = 0, totalI = 0;
        int triangles = 0;
        Amas[] amas = new Amas[na];
        for (int i = 0; i < na; i++) {
            int m = l.varint("matériau de l'amas", 0, nm - 1);
            int[] bq = new int[6];
            for (int k = 0; k < 6; k++) bq[k] = l.u16();
            int ns = l.varint("n_sommets", 1, MAX_SOMMETS), ntr = l.varint("n_triangles", 1, MAX_TRIANGLES);
            if ((long) SOMMET * ns + 6L * ntr > l.reste()) throw new Refus("amas " + i + " tronqué");
            int ds = l.sauter(SOMMET * ns), di = l.sauter(6 * ntr);
            for (int k = 0; k < 3 * ntr; k++) {
                int x = (o[di + 2 * k] & 0xFF) | (o[di + 2 * k + 1] & 0xFF) << 8;
                if (x >= ns) throw new Refus("amas " + i + " : index " + x + " ≥ " + ns);
            }
            pos[i] = new int[]{ds, di};
            amas[i] = new Amas(m, bq, (int) totalI / 2, 3 * ntr, (int) (totalS / SOMMET), ns);
            totalS += (long) SOMMET * ns;
            totalI += 6L * ntr;
            triangles += ntr;
        }
        l.finir("NIVEAU");
        if (trianglesAnnonces >= 0 && triangles != trianglesAnnonces) throw new Refus("NIVEAU de " + triangles + " triangles, " + trianglesAnnonces + " annoncés");
        byte[] s = new byte[(int) totalS], ix = new byte[(int) totalI];
        for (int i = 0; i < na; i++) {
            System.arraycopy(o, pos[i][0], s, amas[i].baseSommet * SOMMET, amas[i].nSommets * SOMMET);
            System.arraycopy(o, pos[i][1], ix, amas[i].premierIndice * 2, amas[i].nIndices * 2);
        }
        return new NiveauGpu(qo, qp, tex, mats, amas, s, ix, triangles);
    }

    // ------------------------------------------------------------------------------------------ TEXTURE (§6.3)
    public record Texture(int largeur, int hauteur, byte[] png) {}

    public static Texture texture(byte[] o) {
        Lecteur l = new Lecteur(o);
        l.varint("version", 1, 1);
        int w = l.varint("largeur", 1, MAX_COTE), h = l.varint("hauteur", 1, MAX_COTE);
        byte[] png = l.octets(l.reste());
        // IHDR vérifié avant de décoder : signature, bloc IHDR, dimensions annoncées
        if (png.length < 24 || (png[0] & 0xFF) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G'
            || png[12] != 'I' || png[13] != 'H' || png[14] != 'D' || png[15] != 'R') throw new Refus("TEXTURE : pas un PNG");
        Lecteur ih = new Lecteur(png, 16, 24);
        int pw = ih.i32(), ph = ih.i32();
        if (pw != w || ph != h) throw new Refus("TEXTURE : IHDR " + pw + "×" + ph + " ≠ " + w + "×" + h);
        return new Texture(w, h, png);
    }

    // ------------------------------------------------------------------------------------------ messages
    public sealed interface Message permits Scene, Ressourcemorceau, Retirer, Vider {}
    public record Scene(String id, int revision, int index, int total, byte[] donnees) implements Message {}
    public record Ressourcemorceau(Cle cle, int decalage, byte[] donnees) implements Message {}
    public record Retirer(String id) implements Message {}
    public record Vider() implements Message {}

    static String identifiant(Lecteur l) {
        String id = l.string("id", MAX_ID);
        if (id.isEmpty()) throw new Refus("id vide");
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_')) throw new Refus("id « " + id + " » : caractère interdit");
        }
        return id;
    }

    public static Message message(byte[] o) {
        Lecteur l = new Lecteur(o);
        int op = l.u8();
        Message m = switch (op) {
            case OP_SCENE -> {
                String id = identifiant(l);
                int rev = l.varint(), idx = l.varint(), tot = l.varint("total", 1, MAX_MORCEAUX);
                if (idx >= tot) throw new Refus("morceau " + idx + " sur " + tot);
                int n = l.varint("n", 0, MAX_MORCEAU);
                yield new Scene(id, rev, idx, tot, l.octets(n));
            }
            case OP_RESSOURCE -> {
                Cle k = cle(l);
                int dec = l.varint();
                int n = l.varint("n", 1, MAX_MORCEAU);
                yield new Ressourcemorceau(k, dec, l.octets(n));
            }
            case OP_RETIRER -> new Retirer(identifiant(l));
            case OP_VIDER -> new Vider();
            default -> throw new Refus("opération " + op);
        };
        l.finir("message");
        return m;
    }

    public static byte[] besoin(String id, int revision, int[] index) {
        Wire.Out w = new Wire.Out().u8(OP_BESOIN).string(id, MAX_ID).varInt(revision).varInt(index.length);
        for (int i : index) w.varInt(i);
        return w.toBytes();
    }

    public static byte[] etat(int pretes, int enCache, int rejets, String motif) {
        byte[] m = motif.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String court = motif;
        if (m.length > 256) { int k = 256; while (k > 0 && (m[k] & 0xC0) == 0x80) k--; court = new String(m, 0, k, java.nio.charset.StandardCharsets.UTF_8); }
        return new Wire.Out().u8(OP_ETAT).varInt(pretes).varInt(enCache).varInt(rejets).string(court, 256).toBytes();
    }
}
