package infinitylink.mc.blocks;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import infinitylink.core.BlocksSpec;
import java.util.HexFormat;

public final class SageStock {
    private SageStock() {}
    private static volatile boolean complete;
    public static boolean registered() { return complete; }
    public static void register() {
        if (complete || "off".equals(infinitylink.core.Props.get("blocks"))) return;
        if(Block.BLOCK_STATE_REGISTRY.size()!=BlocksSpec.BASE) throw new IllegalStateException("Registre incompatible avant stock : "+Block.BLOCK_STATE_REGISTRY.size());
        long start=System.nanoTime();
        if(!BlocksSpec.fingerprint().equals(BlocksSpec.SHA1)) throw new IllegalStateException("Partition invalide");
        BlocksSpec.names((part,name)->{
            var key=ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("sage",name));
            var props=BlockBehaviour.Properties.of().setId(key);
            if(!part.prefix().equals("la")&&!part.prefix().equals("a")) props.noOcclusion();
            if(part.prefix().equals("la")||part.prefix().equals("lb")) props.lightLevel(s->s.getValue(SageBlock.S));
            if(part.prefix().equals("v")) props.offsetType(BlockBehaviour.OffsetType.XZ).replaceable();
            SageBlock block=new SageBlock(props);
            Registry.register(BuiltInRegistries.BLOCK,key,block);
            for(var state:block.getStateDefinition().getPossibleStates()) { Block.BLOCK_STATE_REGISTRY.add(state); state.initCache(); }
        });
        var digest=BlocksSpec.digest("SHA-1");
        for(int i=BlocksSpec.BASE;i<Block.BLOCK_STATE_REGISTRY.size();i++) {
            var state=Block.BLOCK_STATE_REGISTRY.byId(i);
            BlocksSpec.hashState(digest,BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),state.getValue(SageBlock.S));
        }
        String hash=HexFormat.of().formatHex(digest.digest());
        if(Block.BLOCK_STATE_REGISTRY.size()!=BlocksSpec.TOTAL||!hash.equals(BlocksSpec.SHA1)) throw new IllegalStateException("Stock partiel ou ordre incompatible : "+hash);
        complete=true;
        infinitylink.mc.Bridge.STATE.formesCapable=true; // capacité « formes » (1.1.0) : états de réserve présents
        var obs=infinitylink.mc.Bridge.STATE.blocks;
        obs.stock=BlocksSpec.STOCK; obs.empreinte=hash;
        System.out.println("[infinitylink] blocs : 6250 blocs, 100000 etats, base=35723, empreinte="+hash+", ms="+(System.nanoTime()-start)/1_000_000);
    }
}
