package infinitylink.mc.blocks;
import net.minecraft.core.IdMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** Zéro = vue dynamique des codecs ; positif = instantané immuable d'un niveau/banc. */
public final class SageIdView implements IdMap<BlockState> {
    private final int fixed;
    public SageIdView(int fixed) { if(fixed<0||fixed>infinitylink.core.BlocksSpec.TOTAL) throw new IllegalArgumentException(); this.fixed=fixed; }
    public int size() { return fixed==0?BlocksMode.currentSize():fixed; }
    public BlockState byId(int id) { return id>=0&&id<size()?Block.BLOCK_STATE_REGISTRY.byId(id):null; }
    public int getId(BlockState state) { int id=Block.BLOCK_STATE_REGISTRY.getId(state); return id>=0&&id<size()?id:-1; }
    public Iterator<BlockState> iterator() {
        final int end=size();
        return new Iterator<>() { int id; public boolean hasNext(){return id<end;} public BlockState next(){if(!hasNext())throw new NoSuchElementException();return Block.BLOCK_STATE_REGISTRY.byId(id++);} };
    }
}
