package sage.link.mc.blocks;
import net.minecraft.core.IdMap;
import net.minecraft.network.Connection;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import sage.link.core.BlocksSpec;

/** B1 ne possède aucune API d'activation étendue. L4 ajoutera le commit validé. */
public final class BlocksMode {
    private BlocksMode() {}
    public record Context(Connection connection,long generation,boolean configuration) {}
    private static volatile Context context=new Context(null,0,false);
    public static final SageIdView DYNAMIQUE=new SageIdView(0);
    public static synchronized Context begin(Connection connection) {
        context=new Context(connection,context.generation+1,true); BlocksEssai.reset(); return context;
    }
    public static synchronized void disconnect(Connection connection) {
        if(context.connection!=connection) return;
        context=new Context(null,context.generation+1,false); BlocksEssai.reset();
    }
    public static synchronized void finish(Connection connection) {
        if(context.connection==connection) { context=new Context(connection,context.generation,false); BlocksEssai.finish(); }
    }
    public static Context context() { return context; }
    public static boolean current(Context token) { return context==token; }
    public static int currentSize() { return BlocksSpec.BASE; }
    public static IdMap<BlockState> forLevel() {
        sage.link.mc.Bridge.STATE.blocks.vue=BlocksSpec.BASE;
        return Block.BLOCK_STATE_REGISTRY.size()==BlocksSpec.BASE?Block.BLOCK_STATE_REGISTRY:new SageIdView(BlocksSpec.BASE);
    }
    public static boolean forbidden(BlockState state) { return state.getBlock() instanceof SageBlock; }
    public static void guard(String site) {
        long n=sage.link.mc.Bridge.STATE.blocks.garde.incrementAndGet();
        if(n<=10||(n&(n-1))==0) System.err.println("[sage_link] blocs : mutation/persistance refusee ("+site+"), garde="+n);
    }
}
