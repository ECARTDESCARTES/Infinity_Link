package infinitylink.mc.scenes;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import infinitylink.core.scenes.Contrat;
import infinitylink.core.scenes.Contrat.Cle;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/** Mémoire graphique des scènes (fil de rendu uniquement) : un tampon de sommets et un tampon d'index par ressource
 *  NIVEAU (octets du contrat copiés tels quels), une texture à mipmaps par ressource TEXTURE. Téléversement par
 *  tranches dans un budget d'octets par image (contrat §8.1) : un gros niveau s'étale sur plusieurs images et n'est
 *  dessinable qu'une fois complet. Plafond de VRAM (§8.5) : on libère d'abord les ressources les moins récemment
 *  dessinées (jamais celles des deux dernières images, jamais celles des modèles, qu'on ne peut pas redemander) ; le
 *  cache disque les garde et le rendu les redemande s'il les veut de nouveau. Aucune méthode n'est appelée pendant une
 *  passe de rendu, sauf les accesseurs de dessin. */
final class ScGpu {
    /** Niveau résident ou en téléversement. */
    static final class Niveau {
        final Cle cle;
        final float[] qo, qp;
        final Contrat.Materiau[] materiaux;
        final Contrat.Amas[] amas;
        final Cle[] textures;
        final int triangles;
        final boolean evincable;
        final long octets, arrivee;
        byte[] s, i;
        int faitS, faitI;
        GpuBuffer vb, ib;
        boolean pret;
        long vu;

        Niveau(Cle cle, Contrat.NiveauGpu n, boolean evincable, long image) {
            this.cle = cle;
            qo = n.qo(); qp = n.qp(); materiaux = n.materiaux(); amas = n.amas(); textures = n.textures();
            triangles = n.triangles();
            this.evincable = evincable;
            s = n.sommets(); i = n.index();
            octets = (long) s.length + i.length;
            arrivee = image;
            vu = image;
        }
    }

    /** Texture résidente ou en téléversement (mipmaps calculées sur le fil de travail, envoyées par bandes). */
    static final class Tex {
        final Cle cle;
        final boolean lisse, evincable;
        final int largeur, hauteur;
        final long octets, arrivee;
        NativeImage[] mips;
        int mip, ligne;
        GpuTexture t;
        GpuTextureView v;
        boolean pret;
        long vu;

        Tex(Cle cle, NativeImage[] mips, boolean lisse, boolean evincable, long image) {
            this.cle = cle;
            this.mips = mips;
            this.lisse = lisse;
            this.evincable = evincable;
            largeur = mips[0].getWidth();
            hauteur = mips[0].getHeight();
            long o = 0;
            for (NativeImage m : mips) o += 4L * m.getWidth() * m.getHeight();
            octets = o;
            arrivee = image;
            vu = image;
        }
    }

    private final Map<Cle, Niveau> niveaux = new HashMap<>();
    private final Map<Cle, Tex> textures = new HashMap<>();
    private final ArrayDeque<Object> file = new ArrayDeque<>();
    private final Consumer<Cle> liberee;
    private final LongConsumer libereDecode;
    long plafond;
    private long vram, televerse, evinces, manques, abandons, version;
    /** Objets GPU créés par image au plus (des milliers de petites ressources ne font pas un pic). */
    static final int MAX_CREATIONS = 64;
    private boolean manque;
    private GpuTexture blanche;
    private GpuTextureView blancheV;
    private final GpuBuffer[] anneau = new GpuBuffer[3];

    ScGpu(long plafond, Consumer<Cle> liberee, LongConsumer libereDecode) {
        this.plafond = plafond; this.liberee = liberee; this.libereDecode = libereDecode;
    }

    /** Change à chaque ressource devenue prête ou libérée (cache de Rendu.affichable). */
    long version() { return version; }
    long abandons() { return abandons; }

    static long octets(NativeImage[] mips) {
        long o = 0;
        if (mips != null) for (NativeImage m : mips) if (m != null) o += 4L * m.getWidth() * m.getHeight();
        return o;
    }

    /** Ferme les mipmaps d'une texture et rend leur mémoire au budget de décodage (une seule fois). */
    private void fermerMips(Tex t) {
        if (t.mips == null) return;
        fermer(t.mips);
        t.mips = null;
        libereDecode.accept(t.octets);
    }

    Niveau niveau(Cle k) { return niveaux.get(k); }
    Tex texture(Cle k) { return textures.get(k); }
    long vram() { return vram; }
    int nombreNiveaux() { return niveaux.size(); }
    int nombreTextures() { return textures.size(); }
    int enAttente() { return file.size(); }
    long evinces() { return evinces; }
    long manques() { return manques; }
    /** Lu et remis à zéro : une ressource n'a pas trouvé de place depuis. */
    boolean prendreManque() { boolean m = manque; manque = false; return m; }

    /** Un niveau arrive (fil de rendu) : remplace une copie en cours de même clé, mis en file de téléversement. */
    void ajouter(Cle k, Contrat.NiveauGpu n, boolean evincable, long image) {
        Niveau old = niveaux.get(k);
        if (old != null) { if (old.pret) return; liberer(old, false); }
        Niveau x = new Niveau(k, n, evincable, image);
        niveaux.put(k, x);
        file.add(x);
    }

    void ajouter(Cle k, NativeImage[] mips, boolean lisse, boolean evincable, long image) {
        Tex old = textures.get(k);
        if (old != null) { if (old.pret) { long o = octets(mips); fermer(mips); libereDecode.accept(o); return; } liberer(old, false); }
        Tex x = new Tex(k, mips, lisse, evincable, image);
        textures.put(k, x);
        file.add(x);
    }

    static void fermer(NativeImage[] mips) {
        if (mips != null) for (NativeImage m : mips) try { if (m != null) m.close(); } catch (Throwable ignored) { }
    }

    /** Téléversement dans le budget de l'image. Une tranche au moins par image (un niveau de 32 Mio ne bloque pas). */
    void televerser(long budget, long image) {
        CommandEncoder ce = null;
        long fait = 0;
        int creations = 0;
        candidats = null;
        while (!file.isEmpty() && fait < budget) {
            Object o = file.peek();
            if (ce == null) ce = RenderSystem.getDevice().createCommandEncoder();
            long reste = Math.max(budget - fait, 64 << 10);
            if (o instanceof Niveau n) {
                if (niveaux.get(n.cle) != n) { file.poll(); continue; }
                if (n.vb == null) {
                    if (creations++ >= MAX_CREATIONS) return;
                    if (!place(n.octets, image)) { sansPlace(n); return; }
                    n.vb = RenderSystem.getDevice().createBuffer(() -> "infinitylink scenes sommets", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, n.s.length);
                    n.ib = RenderSystem.getDevice().createBuffer(() -> "infinitylink scenes index", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, Math.max(4, n.i.length + (n.i.length & 2)));
                    vram += n.octets;
                }
                if (n.faitS < n.s.length) {
                    int t = (int) Math.min(reste, n.s.length - n.faitS);
                    ecrire(ce, n.vb, n.faitS, n.s, n.faitS, t);
                    n.faitS += t; fait += t;
                    continue;
                }
                if (n.faitI < n.i.length) {
                    // tranches de longueur paire (index u16) ; le tampon d'index est arrondi à 4 octets
                    int t = (int) Math.min(reste, n.i.length - n.faitI) & ~1;
                    if (t == 0) t = n.i.length - n.faitI;
                    ecrire(ce, n.ib, n.faitI, n.i, n.faitI, t);
                    n.faitI += t; fait += t;
                    continue;
                }
                n.s = null; n.i = null;
                n.pret = true;
                version++;
                televerse += n.octets;
                file.poll();
            } else if (o instanceof Tex x) {
                if (textures.get(x.cle) != x) { file.poll(); continue; }
                if (x.t == null) {
                    if (creations++ >= MAX_CREATIONS) return;
                    if (!place(x.octets, image)) { sansPlace(x); return; }
                    x.t = RenderSystem.getDevice().createTexture(() -> "infinitylink scenes texture", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                            GpuFormat.RGBA8_UNORM, x.largeur, x.hauteur, 1, x.mips.length);
                    vram += x.octets;
                }
                NativeImage m = x.mips[x.mip];
                int w = m.getWidth(), h = m.getHeight();
                int lignes = (int) Math.max(1, Math.min(h - x.ligne, reste / (4L * w)));
                ByteBuffer px = MemoryUtil.memByteBuffer(m.getPointer() + 4L * w * x.ligne, 4 * w * lignes);
                ce.writeToTexture(x.t, px, x.mip, 0, 0, x.ligne, w, lignes);
                x.ligne += lignes; fait += 4L * w * lignes;
                if (x.ligne < h) continue;
                x.ligne = 0;
                if (++x.mip < x.mips.length) continue;
                fermerMips(x);
                x.v = RenderSystem.getDevice().createTextureView(x.t);
                x.pret = true;
                version++;
                televerse += x.octets;
                file.poll();
            } else file.poll();
        }
    }

    /** Pas de place sous le plafond : une ressource redemandable est abandonnée (le rendu la redemandera s'il la veut
     *  encore, du disque) ; celle d'un modèle passe en fin de file. */
    private void sansPlace(Object o) {
        manque = true;
        manques++;
        file.poll();
        // redemandable : abandonnée, le rendu la redemandera (disque) s'il la veut encore ; celle d'un modèle ne peut
        // pas l'être : abandonnée aussi (item de repli), comptée, plutôt que retenue sans fin en mémoire
        if (o instanceof Niveau n) { if (!n.evincable) abandons++; liberer(n, n.evincable); }
        else if (o instanceof Tex t) { if (!t.evincable) abandons++; liberer(t, t.evincable); }
    }

    private static void ecrire(CommandEncoder ce, GpuBuffer b, long dst, byte[] src, int d, int n) {
        ByteBuffer bb = MemoryUtil.memAlloc(n);
        try {
            bb.put(src, d, n).flip();
            ce.writeToBuffer(b.slice(dst, n), bb);
        } finally {
            MemoryUtil.memFree(bb);
        }
    }

    /** Fait de la place sous le plafond : évince les ressources prêtes, évinçables, les moins récemment dessinées
     *  (pas dans les 2 dernières images). false : rien à évincer, la ressource attend. */
    private ArrayDeque<Object> candidats;

    private boolean place(long besoin, long image) {
        while (vram + besoin > plafond) {
            if (candidats == null) {
                // candidats triés une fois par image (du moins récemment dessiné au plus récent), pas un balayage par éviction
                List<Object> l = new ArrayList<>();
                for (Niveau n : niveaux.values()) if (n.pret && n.evincable && n.vu < image - 2) l.add(n);
                for (Tex t : textures.values()) if (t.pret && t.evincable && t.vu < image - 2) l.add(t);
                l.sort(java.util.Comparator.comparingLong(x -> x instanceof Niveau n ? n.vu : ((Tex) x).vu));
                candidats = new ArrayDeque<>(l);
            }
            Object pire = candidats.poll();
            if (pire == null) return vram == 0 && besoin > plafond; // une ressource plus grosse que le plafond passe seule
            if (pire instanceof Niveau n) { if (niveaux.get(n.cle) != n || !n.pret) continue; liberer(n, true); }
            else { Tex t = (Tex) pire; if (textures.get(t.cle) != t || !t.pret) continue; liberer(t, true); }
            evinces++;
        }
        return true;
    }

    void liberer(Niveau n, boolean prevenir) {
        if (niveaux.get(n.cle) == n) niveaux.remove(n.cle);
        if (n.vb != null) { vram -= n.octets; try { n.vb.close(); } catch (Throwable ignored) { } try { n.ib.close(); } catch (Throwable ignored) { } }
        if (n.pret) version++;
        n.vb = n.ib = null; n.s = n.i = null; n.pret = false;
        if (prevenir && n.evincable) liberee.accept(n.cle);
    }

    void liberer(Tex t, boolean prevenir) {
        if (textures.get(t.cle) == t) textures.remove(t.cle);
        if (t.t != null) { vram -= t.octets; try { if (t.v != null) t.v.close(); } catch (Throwable ignored) { } try { t.t.close(); } catch (Throwable ignored) { } }
        fermerMips(t);
        if (t.pret) version++;
        t.t = null; t.v = null; t.pret = false;
        if (prevenir && t.evincable) liberee.accept(t.cle);
    }

    /** Ressources qu'aucune scène courante ne cite plus depuis `grace` images : libérées. */
    void ramasser(java.util.Set<Cle> citees, long image, long grace) {
        List<Niveau> ln = new ArrayList<>();
        for (Niveau n : niveaux.values()) if (!citees.contains(n.cle) && image - Math.max(n.arrivee, n.vu) > grace) ln.add(n);
        for (Niveau n : ln) liberer(n, true);
        List<Tex> lt = new ArrayList<>();
        for (Tex t : textures.values()) if (!citees.contains(t.cle) && image - Math.max(t.arrivee, t.vu) > grace) lt.add(t);
        for (Tex t : lt) liberer(t, true);
    }

    /** Texture blanche déjà créée (pendant une passe : rien n'y est créé). */
    GpuTextureView blancheSiPrete() { return blancheV != null && !blancheV.isClosed() ? blancheV : null; }

    /** Texture blanche 1×1 (matériaux sans texture), créée hors de toute passe. */
    GpuTextureView blanche() {
        if (blancheV != null && !blancheV.isClosed()) return blancheV;
        blanche = RenderSystem.getDevice().createTexture(() -> "infinitylink scenes blanc", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
        ByteBuffer b = MemoryUtil.memAlloc(4);
        try {
            b.putInt(0, -1);
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(blanche, b, 0, 0, 0, 0, 1, 1);
        } finally {
            MemoryUtil.memFree(b);
        }
        blancheV = RenderSystem.getDevice().createTextureView(blanche);
        return blancheV;
    }

    /** Tampon d'instances de l'image (anneau de 3, pour ne pas réécrire un tampon encore lu par le GPU). */
    GpuBuffer instances(long image, ByteBuffer donnees) {
        int k = (int) (image % anneau.length);
        GpuBuffer b = anneau[k];
        long n = donnees.remaining();
        if (b == null || b.isClosed() || b.size() < n) {
            if (b != null) try { b.close(); } catch (Throwable ignored) { }
            long t = 64 << 10;
            while (t < n) t <<= 1;
            b = anneau[k] = RenderSystem.getDevice().createBuffer(() -> "infinitylink scenes instances", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, t);
        }
        if (n > 0) RenderSystem.getDevice().createCommandEncoder().writeToBuffer(b.slice(0, n), donnees);
        return b;
    }

    long televerse() { return televerse; }

    /** Tout libérer (déconnexion, coupure) : VRAM des scènes à 0. */
    void fermer() {
        for (Niveau n : new ArrayList<>(niveaux.values())) liberer(n, false);
        for (Tex t : new ArrayList<>(textures.values())) liberer(t, false);
        for (Iterator<Object> it = file.iterator(); it.hasNext(); ) { if (it.next() instanceof Tex t) fermerMips(t); }
        file.clear();
        for (int k = 0; k < anneau.length; k++) { if (anneau[k] != null) try { anneau[k].close(); } catch (Throwable ignored) { } anneau[k] = null; }
        try { if (blancheV != null) blancheV.close(); } catch (Throwable ignored) { }
        try { if (blanche != null) blanche.close(); } catch (Throwable ignored) { }
        blanche = null; blancheV = null;
        vram = 0;
    }
}
