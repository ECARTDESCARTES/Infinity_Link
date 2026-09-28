package sage.link.mc.blocks;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import sage.link.core.BlocksSpec;
import sage.link.core.FormesSgb2;

/** Formes des états de réserve (capacité « formes ») : table statique remplie par sage:link/formes, vide = cubes pleins. */
public final class SageShapes implements FormesSgb2.Sink {
    public static final SageShapes INSTANCE = new SageShapes();
    private SageShapes() {}

    /** Index = id réseau − BASE ; null = cube plein (comportement de la 1.0.0). */
    private static volatile VoxelShape[] contour = new VoxelShape[0], collision = new VoxelShape[0];
    private static volatile boolean[] grimpable = new boolean[0];
    public static volatile int formes, etats;

    static VoxelShape shape(double[][] b) {
        VoxelShape s = Shapes.empty();
        for (double[] x : b) s = Shapes.or(s, Shapes.box(x[0], x[1], x[2], x[3], x[4], x[5]));
        return s;
    }

    @Override public void apply(FormesSgb2.Table t) {
        VoxelShape[] c = new VoxelShape[BlocksSpec.STOCK], k = new VoxelShape[BlocksSpec.STOCK];
        boolean[] g = new boolean[BlocksSpec.STOCK];
        for (FormesSgb2.Etat e : t.etats()) {
            int i = e.id() - BlocksSpec.BASE;
            if (i < 0 || i >= BlocksSpec.STOCK) continue;
            c[i] = shape(t.boites(e, false));
            k[i] = shape(t.boites(e, true));
            g[i] = (t.drapeaux(e) & FormesSgb2.GRIMPABLE) != 0;
        }
        contour = c; collision = k; grimpable = g;
        formes = t.formes().size(); etats = t.etats().size();
        // le serveur passe la session en profil Link à réception de cette table : les chunks arrivent en 18 bits
        BlocksMode.activerEtendu();
    }

    @Override public void clear() { contour = new VoxelShape[0]; collision = new VoxelShape[0]; grimpable = new boolean[0]; formes = etats = 0; }

    private static VoxelShape at(VoxelShape[] t, int id) { int i = id - BlocksSpec.BASE; return i >= 0 && i < t.length ? t[i] : null; }
    public static VoxelShape contour(int id) { return at(contour, id); }
    public static VoxelShape collision(int id) { return at(collision, id); }
    public static boolean grimpable(int id) { boolean[] g = grimpable; int i = id - BlocksSpec.BASE; return i >= 0 && i < g.length && g[i]; }
}
