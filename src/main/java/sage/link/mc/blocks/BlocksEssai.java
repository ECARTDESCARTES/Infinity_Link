package sage.link.mc.blocks;

import io.netty.buffer.Unpooled;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import sage.link.core.BlocksSpec;
import sage.link.mc.Bridge;
import sage.link.mc.SagePayload;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Properties;

/** Probe B1 : aucune mutation du contexte réseau, de niveau ou de monde. */
public final class BlocksEssai {
    private BlocksEssai() {}
    private static BlocksMode.Context token;
    private static long deadline;
    private static int expected;
    private static boolean offered;
    public static synchronized void reset() {
        token=null; expected=0; deadline=0; offered=false;
        var obs=Bridge.STATE.blocks; obs.bits16=obs.bits18=0; obs.probe="off";
    }
    public static synchronized void finish() { if(expected!=0) fail("incomplet"); }
    private static void fail(String why) { expected=0; token=null; deadline=0; Bridge.STATE.blocks.probe=why; }
    public static synchronized void tick() { if(expected!=0 && System.nanoTime()-deadline>=0) fail("delai"); }
    public static boolean allowed(Connection connection) {
        if(!"1".equals(System.getProperty("sage.blocs.essai"))||!SageStock.registered()||connection.isMemoryConnection()) return false;
        if(!(connection.getRemoteAddress() instanceof InetSocketAddress peer)||peer.getAddress()==null||!peer.getAddress().isLoopbackAddress()||peer.getPort()==25565) return false;
        try {
            Properties p=new Properties();
            try(var in=Files.newInputStream(FabricLoader.getInstance().getConfigDir().resolve("sage_link_blocs_essai.properties"))) { p.load(in); }
            return (peer.getAddress().getHostAddress()+":"+peer.getPort()).equals(p.getProperty("adresse"));
        } catch(Exception e) { return false; }
    }
    public static synchronized boolean receive(Connection connection,String path,byte[] data) {
        if(!path.equals("link/blocks_probe")&&!path.equals("link/blocks_probe_data")&&!path.equals("link/blocks_probe_ready")) return false;
        tick();
        var ctx=BlocksMode.context();
        if(ctx.connection()!=connection||!ctx.configuration()) { Bridge.STATE.reject("probe hors configuration"); return true; }
        try {
            if(path.equals("link/blocks_probe")) {
                if(offered || Bridge.STATE.phase()==sage.link.core.LinkState.Phase.OFF || !allowed(connection)) throw new IllegalArgumentException("probe non autorise");
                BlocksSpec.offer(data);
                offered=true;
                token=ctx; expected=16; deadline=System.nanoTime()+10_000_000_000L;
                Bridge.STATE.blocks.bits16=Bridge.STATE.blocks.bits18=0; Bridge.STATE.blocks.probe="attente";
                ready(connection,0);
            } else if(path.equals("link/blocks_probe_data")) {
                if(token!=ctx||expected==0) throw new IllegalArgumentException("probe sans offre courante");
                byte[] container=BlocksSpec.data(data,expected);
                int checked=verify(expected,container);
                if(expected==16) { Bridge.STATE.blocks.bits16=checked; expected=18; }
                else { Bridge.STATE.blocks.bits18=checked; expected=0; token=null; Bridge.STATE.blocks.probe="ok"; }
            } else throw new IllegalArgumentException("sens probe_ready invalide");
        } catch(Exception e) {
            fail("refuse"); Bridge.STATE.reject("probe : "+e.getMessage());
            if(path.equals("link/blocks_probe")) ready(connection,1);
        }
        return true;
    }
    private static void ready(Connection connection,int code) {
        connection.send(new ServerboundCustomPayloadPacket(SagePayload.out("link/blocks_probe_ready",new byte[]{(byte)code}))); Bridge.STATE.sent();
    }
    public static int verify(int bits,byte[] bytes) {
        if(bytes.length!=BlocksSpec.bytes(bits)||(bytes[0]&255)!=bits) throw new IllegalArgumentException("bits/taille");
        var strategy=Strategy.createForBlockStates(new SageIdView(bits==16?BlocksSpec.BASE:BlocksSpec.TOTAL));
        var container=new PalettedContainer<BlockState>(Blocks.AIR.defaultBlockState(),strategy);
        var buffer=new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            container.read(buffer);
            if(buffer.isReadable()) throw new IllegalArgumentException("octets restants");
            for(int i=0;i<4096;i++) if(Block.BLOCK_STATE_REGISTRY.getId(container.get(i&15,i>>>8,(i>>>4)&15))!=BlocksSpec.value(bits,i))
                throw new IllegalArgumentException("etat different a "+i);
            return 4096;
        } finally { buffer.release(); }
    }
}
