package infinitylink.mc.blocks;
import net.minecraft.core.IdMap;
import net.minecraft.network.Connection;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import infinitylink.core.BlocksSpec;

/**
 * Vue des ids d'états de la connexion : base vanilla (35 723 états, 16 bits) tant que le serveur n'a pas envoyé la table
 * SGB2 des formes ; étendue (base + stock de réserve, 18 bits, comme {@code ext::bits_link} côté serveur) dès que
 * {@link SageShapes} l'a appliquée pour CETTE connexion (le serveur passe alors la session en profil Link et encode ses
 * chunks sur la largeur étendue). Une reconfiguration de la même connexion garde la vue étendue.
 */
public final class BlocksMode {
    private BlocksMode() {}
    public record Context(Connection connection,long generation,boolean configuration) {}
    private static volatile Context context=new Context(null,0,false);
    private static volatile Connection etenduPour;
    public static final SageIdView DYNAMIQUE=new SageIdView(0);
    public static synchronized Context begin(Connection connection) {
        if(context.connection!=connection) etenduPour=null;
        context=new Context(connection,context.generation+1,true); BlocksEssai.reset(); return context;
    }
    public static synchronized void disconnect(Connection connection) {
        if(context.connection!=connection) return;
        etenduPour=null;
        context=new Context(null,context.generation+1,false); BlocksEssai.reset();
    }
    public static synchronized void finish(Connection connection) {
        if(context.connection==connection) { context=new Context(connection,context.generation,false); BlocksEssai.finish(); }
    }
    public static Context context() { return context; }
    public static boolean current(Context token) { return context==token; }
    public static int currentSize() {
        Connection e=etenduPour;
        return e!=null&&e==context.connection?BlocksSpec.TOTAL:BlocksSpec.BASE;
    }
    /** Table SGB2 appliquée pour la connexion courante : vue étendue (stock enregistré exigé). */
    public static synchronized boolean activerEtendu() {
        if(context.connection==null||Block.BLOCK_STATE_REGISTRY.size()<BlocksSpec.TOTAL) return false;
        if(etenduPour!=context.connection) System.out.println("[infinitylink] blocs : vue etendue ("+BlocksSpec.TOTAL+" etats, 18 bits)");
        etenduPour=context.connection;
        return true;
    }
    public static IdMap<BlockState> forLevel() {
        int n=currentSize();
        infinitylink.mc.Bridge.STATE.blocks.vue=n;
        return Block.BLOCK_STATE_REGISTRY.size()==n?Block.BLOCK_STATE_REGISTRY:new SageIdView(n);
    }
    /** État de réserve refusé dans un chunk, un codec ou un NBT, sauf en vue étendue (profil Link accordé par le serveur). */
    public static boolean forbidden(BlockState state) { return state.getBlock() instanceof SageBlock && currentSize()==BlocksSpec.BASE; }
    public static void guard(String site) {
        long n=infinitylink.mc.Bridge.STATE.blocks.garde.incrementAndGet();
        if(n<=10||(n&(n-1))==0) System.err.println("[infinitylink] blocs : mutation/persistance refusee ("+site+"), garde="+n);
    }
}
