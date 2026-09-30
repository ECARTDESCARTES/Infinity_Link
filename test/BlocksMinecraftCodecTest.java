import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import io.netty.buffer.Unpooled;
import infinitylink.mc.blocks.*;
import infinitylink.core.BlocksSpec;
import java.lang.reflect.Method;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.*;
import net.minecraft.core.particles.*;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import com.mojang.brigadier.StringReader;
import java.nio.file.*;
import java.util.*;

/** Vrais registres, vrai gel et vrais conteneurs. Sans mixin dans ce banc : injection de bootstrap reproduite explicitement. */
public final class BlocksMinecraftCodecTest {
    static int checks;
    static void check(boolean ok) { checks++;if(!ok)throw new AssertionError("controle "+checks); }
    static void reject(Runnable r) { try {r.run();}catch(RuntimeException e){checks++;return;}throw new AssertionError("acceptation indue"); }
    public static void main(String[] args) throws Exception {
        long bootStart=System.nanoTime();
        SharedConstants.tryDetectVersion();
        boolean woven=args.length>0 && args[0].equals("woven");
        if(woven) Bootstrap.bootStrap();
        else {
        var flag=Bootstrap.class.getDeclaredField("isBootstrapped"); flag.setAccessible(true); flag.setBoolean(null,true);
        // Bootstrap statique vanilla crée les contenus ; hook avant freeze, sans dégel/rollback.
        Class.forName("net.minecraft.world.level.block.Blocks");
        Method create=BuiltInRegistries.class.getDeclaredMethod("createContents");create.setAccessible(true);create.invoke(null);
        check(Block.BLOCK_STATE_REGISTRY.size()==BlocksSpec.BASE);
        SageStock.register();
        Method freeze=BuiltInRegistries.class.getDeclaredMethod("freeze");freeze.setAccessible(true);freeze.invoke(null);
        }
        // Régression réelle : la requête noCollision ne doit pas recevoir une forme seulement adjacente.
        var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)uf.get(null);
        var collisionWorld=(net.minecraft.client.multiplayer.ClientLevel)unsafe.allocateInstance(net.minecraft.client.multiplayer.ClientLevel.class);
        var index=infinitylink.mc.scenes.ModelCollisionClient.INDEX;
        index.put(new infinitylink.core.scenes.ModelCollisions.Pose(123,0,0,0,List.of(new infinitylink.core.scenes.ModelCollisions.Box(0,.75,0,1,1,1))));
        check(infinitylink.mc.scenes.ModelCollisionClient.append(collisionWorld,null,new net.minecraft.world.phys.AABB(1+1e-7,0,.1,1.6,1.8,.9),List.of()).isEmpty());
        check(infinitylink.mc.scenes.ModelCollisionClient.append(collisionWorld,null,new net.minecraft.world.phys.AABB(.8,0,.1,1.6,1.8,.9),List.of()).size()==1);
        index.clear();
        long bootMillis=(System.nanoTime()-bootStart)/1_000_000;
        System.gc(); long heap=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
        System.out.println("MESURE headless : boot_ms="+bootMillis+", heap_apres_gc="+heap+", stock_off="+"off".equals(infinitylink.core.Props.get("blocks")));
        // Le chargement force l'application des mixins même pour les chemins non exécutés par le bootstrap.
        for(String c:List.of("world.level.chunk.PalettedContainerFactory","network.protocol.game.ClientboundBlockUpdatePacket",
            "network.protocol.game.ClientboundAddTransientBlockPacket","network.protocol.game.ClientboundSectionBlocksUpdatePacket",
            "core.particles.BlockParticleOption","commands.arguments.blocks.BlockStateParser","world.level.levelgen.DebugLevelSource",
            "world.level.chunk.LevelChunk","world.level.chunk.ProtoChunk","world.level.storage.LevelStorageSource",
            "world.level.levelgen.flat.FlatLayerInfo","client.multiplayer.ClientConfigurationPacketListenerImpl",
            "client.renderer.entity.layers.HumanoidArmorLayer","client.renderer.entity.layers.CustomHeadLayer",
            "server.packs.resources.ReloadableResourceManager","client.Options","client.resources.language.ClientLanguage",
            "client.renderer.extract.LevelExtractor","client.renderer.entity.DisplayRenderer$ItemDisplayRenderer")) {
            Class<?> target=Class.forName("net.minecraft."+c,false,BlocksMinecraftCodecTest.class.getClassLoader());
            if(woven) check(Arrays.stream(target.getDeclaredMethods()).anyMatch(m->m.getName().contains("sage$")||m.getName().contains("infinitylink$")));
        }
        if("off".equals(infinitylink.core.Props.get("blocks"))) {
            check(Block.BLOCK_STATE_REGISTRY.size()==BlocksSpec.BASE);
            check(BlocksMode.forLevel()==Block.BLOCK_STATE_REGISTRY);
            check(BlocksEssai.verify(16,BlocksSpec.container(16))==4096);
            System.out.println("BlocksMinecraftCodecTest OFF : "+checks+" controles OK");return;
        }
        check(Block.BLOCK_STATE_REGISTRY.size()==BlocksSpec.TOTAL);
        check(infinitylink.mc.Bridge.STATE.blocks.empreinte.equals(BlocksSpec.SHA1));
        for(int i=0;i<BlocksSpec.STOCK;i++) {
            BlockState s=Block.BLOCK_STATE_REGISTRY.byId(BlocksSpec.BASE+i);
            check(s.getValue(SageBlock.S)==(i&15));
            check(s.getLightEmission()==(i<4000?(i&15):0));
        }
        var vanilla=new SageIdView(BlocksSpec.BASE); var extended=new SageIdView(BlocksSpec.TOTAL);
        check(vanilla.byId(-1)==null);check(vanilla.byId(35723)==null);
        check(vanilla.getId(extended.byId(35723))==-1);
        check(BlocksMode.forLevel().size()==35723);
        for(int bits:new int[]{16,18}) check(BlocksEssai.verify(bits,BlocksSpec.container(bits))==4096);
        byte[] wrong=BlocksSpec.container(18);wrong[0]=17;reject(()->BlocksEssai.verify(18,wrong));
        for(int size:new int[]{1,16,256,257}) {
            var strategy=Strategy.createForBlockStates(vanilla);
            var original=new PalettedContainer<BlockState>(Blocks.AIR.defaultBlockState(),strategy);
            for(int i=0;i<4096;i++) original.set(i&15,i>>>8,(i>>>4)&15,vanilla.byId(i%size));
            var buf=new FriendlyByteBuf(Unpooled.buffer());
            try {
                original.write(buf); var decoded=new PalettedContainer<BlockState>(Blocks.AIR.defaultBlockState(),strategy);decoded.read(buf);
                check(!buf.isReadable());
                for(int i=0;i<4096;i++) check(decoded.get(i&15,i>>>8,(i>>>4)&15)==vanilla.byId(i%size));
            }finally{buf.release();}
        }
        for(int id:new int[]{0,35722,35723,65536,131072,135722,135723}) for(var view:new SageIdView[]{vanilla,extended}) {
            var buf=new FriendlyByteBuf(Unpooled.buffer());
            try {buf.writeVarInt(id);var codec=ByteBufCodecs.idMapper(view);
                if(id>=view.size())reject(()->codec.decode(buf));else check(codec.decode(buf)==Block.BLOCK_STATE_REGISTRY.byId(id));
            }finally{buf.release();}
        }
        check(SoloGuard.check(Blocks.STONE.defaultBlockState())==Blocks.STONE.defaultBlockState());
        reject(()->SoloGuard.check(extended.byId(35723)));
        var tag=new net.minecraft.nbt.CompoundTag();tag.putString("Name","sage:a0000");reject(()->SoloGuard.checkTag(tag));
        if(woven) wovenChecks();
        System.out.println("BlocksMinecraftCodecTest : "+checks+" controles OK ; palettes 16/18 = 4096/4096 chacune");
    }
    static final class TestBuffer extends RegistryFriendlyByteBuf implements AutoCloseable {
        TestBuffer(){super(Unpooled.buffer(),RegistryAccess.EMPTY);} public void close(){release();}
    }
    static TestBuffer buffer() { return new TestBuffer(); }
    static void wovenChecks() throws Exception {
        var stock=Block.BLOCK_STATE_REGISTRY.byId(35723);
        // M1 : vraie factory et vrai registre de biomes minimal, sans modifier les registres intégrés.
        var biomes=new MappedRegistry<net.minecraft.world.level.biome.Biome>(net.minecraft.core.registries.Registries.BIOME,com.mojang.serialization.Lifecycle.stable());
        var biome=new net.minecraft.world.level.biome.Biome.BiomeBuilder().hasPrecipitation(false).temperature(0.5f).downfall(0.5f)
            .specialEffects(new net.minecraft.world.level.biome.BiomeSpecialEffects.Builder().waterColor(0).build())
            .generationSettings(net.minecraft.world.level.biome.BiomeGenerationSettings.EMPTY).mobSpawnSettings(net.minecraft.world.level.biome.MobSpawnSettings.EMPTY).build();
        Registry.register(biomes,net.minecraft.world.level.biome.Biomes.PLAINS,biome);biomes.freeze();
        var access=new RegistryAccess.ImmutableRegistryAccess(List.of(biomes));
        var factory=PalettedContainerFactory.create(access);
        check(factory.blockStatesStrategy().globalMap().size()==35723);
        check(factory.biomeStrategy().globalMap().size()==1);
        try(var b=buffer();var encoded=buffer()) {
            byte[] golden=BlocksSpec.container(16);b.writeBytes(golden);
            var actual=factory.createForBlockStates();actual.read(b);actual.write(encoded);
            byte[] roundtrip=new byte[encoded.readableBytes()];encoded.readBytes(roundtrip);
            check(!b.isReadable());check(Arrays.equals(golden,roundtrip));
        }
        var isolated=new PalettedContainer<BlockState>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(new SageIdView(BlocksSpec.TOTAL)));
        for(int i=0;i<4096;i++)isolated.set(i&15,i>>>8,(i>>>4)&15,Block.BLOCK_STATE_REGISTRY.byId(i%257));
        isolated.set(15,15,15,Block.BLOCK_STATE_REGISTRY.byId(135722));
        try(var b=buffer()) {
            isolated.write(b);check(b.getUnsignedByte(0)==18);
            var copy=new PalettedContainer<BlockState>(Blocks.AIR.defaultBlockState(),Strategy.createForBlockStates(new SageIdView(BlocksSpec.TOTAL)));copy.read(b);
            check(copy.get(15,15,15)==Block.BLOCK_STATE_REGISTRY.byId(135722));check(!b.isReadable());
        }
        var proto=new ProtoChunk(new net.minecraft.world.level.ChunkPos(0,0),UpgradeData.EMPTY,
            net.minecraft.world.level.LevelHeightAccessor.create(0,16),factory,null);
        proto.setBlockState(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),0);
        check(proto.setBlockState(BlockPos.ZERO,stock,0)==null);
        check(proto.getBlockState(BlockPos.ZERO)==Blocks.STONE.defaultBlockState());
        // M2-M7 : les codecs réels, créés parfois AVANT l'enregistrement, voient toujours la vue vanilla.
        for(int id:new int[]{0,35722,35723,65536,131072,135722,135723}) {
            try(var b=buffer()) { b.writeBlockPos(BlockPos.ZERO);b.writeVarInt(id);
                if(id<35723)check(ClientboundBlockUpdatePacket.STREAM_CODEC.decode(b).getBlockState()==Block.BLOCK_STATE_REGISTRY.byId(id));
                else reject(()->ClientboundBlockUpdatePacket.STREAM_CODEC.decode(b)); }
            try(var b=buffer()) { b.writeBlockPos(BlockPos.ZERO);b.writeVarInt(id);
                if(id<35723)check(ClientboundAddTransientBlockPacket.STREAM_CODEC.decode(b).getBlockState()==Block.BLOCK_STATE_REGISTRY.byId(id));
                else reject(()->ClientboundAddTransientBlockPacket.STREAM_CODEC.decode(b)); }
            try(var b=buffer()) { b.writeVarInt(id);
                var codec=BlockParticleOption.streamCodec(ParticleTypes.BLOCK);
                if(id<35723)check(codec.decode(b).getState()==Block.BLOCK_STATE_REGISTRY.byId(id));else reject(()->codec.decode(b)); }
            try(var b=buffer()) { b.writeVarInt(id);
                if(id<35723)check(EntityDataSerializers.BLOCK_STATE.codec().decode(b)==Block.BLOCK_STATE_REGISTRY.byId(id));else reject(()->EntityDataSerializers.BLOCK_STATE.codec().decode(b)); }
            try(var b=buffer()) { b.writeLong(0);b.writeVarInt(1);b.writeVarLong((long)id<<12);
                var packet=ClientboundSectionBlocksUpdatePacket.STREAM_CODEC.decode(b);
                final BlockState[] seen={null};packet.runUpdates((p,s)->seen[0]=s);
                check(seen[0]==(id<35723?Block.BLOCK_STATE_REGISTRY.byId(id):null)); }
            check(Block.stateById(id)==(id<35723?Block.BLOCK_STATE_REGISTRY.byId(id):Blocks.AIR.defaultBlockState()));
        }
        try(var b=buffer()) {reject(()->ClientboundAddTransientBlockPacket.STREAM_CODEC.encode(b,new ClientboundAddTransientBlockPacket(BlockPos.ZERO,stock)));}
        // M8 : curseur et type/message de l'erreur vanilla conservés.
        String[] errors=new String[2];int ix=0;
        for(String name:List.of("sage:a0000","sage:absent")) {
            StringReader reader=new StringReader(name);
            try {BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,reader,false);throw new AssertionError();}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException e) {check(reader.getCursor()==0);errors[ix++]=e.getRawMessage().getString().replace(name,"<bloc>");}
        }
        check(errors[0].equals(errors[1]));
        check(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,"minecraft:stone",false).blockState()==Blocks.STONE.defaultBlockState());
        // M10 : la grille de debug entière reste identique, pas seulement ses états retournés.
        Class<?> debug=Class.forName("net.minecraft.world.level.levelgen.DebugLevelSource");
        var states=debug.getDeclaredField("ALL_BLOCKS");states.setAccessible(true);
        List<?> all=(List<?>)states.get(null);check(all.size()==35723);
        for(int i=0;i<35723;i++)check(all.get(i)==Block.BLOCK_STATE_REGISTRY.byId(i));
        var width=debug.getDeclaredField("GRID_WIDTH");width.setAccessible(true);check(width.getInt(null)==190);
        reject(()->BlockState.CODEC.encodeStart(NbtOps.INSTANCE,stock));
        reject(()->BlockState.FULL_CODEC.encodeStart(NbtOps.INSTANCE,stock));
        var constantField=BlockState.class.getDeclaredField("CONSTANT_OR_DISPATCH_CODEC");constantField.setAccessible(true);
        @SuppressWarnings("unchecked") var constant=(com.mojang.serialization.Codec<com.mojang.datafixers.util.Either<Block,BlockState>>)constantField.get(null);
        reject(()->constant.encodeStart(NbtOps.INSTANCE,com.mojang.datafixers.util.Either.left(stock.getBlock())));
        var tag=new CompoundTag();tag.putString("Name","sage:a0000");
        reject(()->NbtUtils.readBlockState(BuiltInRegistries.BLOCK,tag));
        reject(()->new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(1,stock.getBlock()));
        // Préflight d'un monde importé avant acquisition du verrou ; aucun octet modifié.
        Path base=Path.of("build/l3/solo-fixtures").toAbsolutePath();Files.createDirectories(base);
        Path bad=Files.createTempDirectory(base,"import-");Path data=bad.resolve("level.dat");NbtIo.writeCompressed(tag,data);
        byte[] before=Files.readAllBytes(data);
        var storage=net.minecraft.world.level.storage.LevelStorageSource.createDefault(base);
        try {storage.validateAndCreateAccess(bad.getFileName().toString());throw new AssertionError("import accepte");}
        catch(java.io.IOException expected) {checks++;}
        check(Arrays.equals(before,Files.readAllBytes(data)));check(!Files.exists(bad.resolve("session.lock")));
        // Générations : un ancien disconnect ne réinitialise jamais le contexte nouveau.
        var c1=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND);
        var c2=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND);
        var old=BlocksMode.begin(c1);var current=BlocksMode.begin(c2);BlocksMode.disconnect(c1);
        check(BlocksMode.current(current));check(!BlocksMode.current(old));check(BlocksMode.currentSize()==35723);
        BlocksMode.disconnect(c2);check(!BlocksMode.current(current));check(factory.blockStatesStrategy().globalMap().size()==35723);
        probeChecks();
        regionChecks(base);
    }
    static final class ProbeConnection extends net.minecraft.network.Connection {
        int reply=-1; boolean memory; int port=25644; String address="127.0.0.1";
        ProbeConnection(){super(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND);}
        @Override public boolean isMemoryConnection(){return memory;}
        @Override public java.net.SocketAddress getRemoteAddress(){return new java.net.InetSocketAddress(address,port);}
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet){
            var p=(net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket)packet;
            reply=((infinitylink.mc.SagePayload)p.payload()).data()[0];
        }
    }
    static void probeChecks() throws Exception {
        Path config=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();Files.createDirectories(config);
        Path allow=config.resolve("infinitylink_blocs_essai.properties");
        Files.writeString(allow,"adresse=127.0.0.1:25644\n");
        System.setProperty("infinitylink.blocs.essai","1");
        var c=new ProbeConnection();
        check(BlocksEssai.allowed(c));c.port=25565;check(!BlocksEssai.allowed(c));c.port=25645;check(!BlocksEssai.allowed(c));
        c.port=25644;c.memory=true;check(!BlocksEssai.allowed(c));c.memory=false;c.address="192.0.2.1";check(!BlocksEssai.allowed(c));c.address="127.0.0.1";
        System.clearProperty("infinitylink.blocs.essai");check(!BlocksEssai.allowed(c));
        System.setProperty("sage.blocs.essai","1");check(BlocksEssai.allowed(c)); // ancien nom (SAGE Link) encore lu
        infinitylink.mc.Bridge.STATE.beginHello("26.3");
        // Le contrat B1 exige le hello, pas l'ordre relatif du manifest et de l'offre.
        check(infinitylink.mc.Bridge.STATE.phase()==infinitylink.core.LinkState.Phase.HELLO_SENT);
        byte[] offer=HexFormat.of().parseHex("018b9702a08d061249cf91f0c06591ffb6b2ab7d6d8e98a6d29291a3");
        BlocksMode.begin(c);BlocksEssai.receive(c,"link/blocks_probe",offer);check(c.reply==0);
        for(int bits:new int[]{16,18}) {
            byte[] data=BlocksSpec.container(bits);BlocksEssai.receive(c,"link/blocks_probe_data",new infinitylink.core.Wire.Out().u8(bits).varInt(data.length).bytes(data).toBytes());
        }
        var obs=infinitylink.mc.Bridge.STATE.blocks;check(obs.bits16==4096&&obs.bits18==4096&&obs.probe.equals("ok"));check(BlocksMode.currentSize()==35723);
        BlocksEssai.receive(c,"link/blocks_probe",offer);check(c.reply==1);
        BlocksMode.begin(c);BlocksEssai.receive(c,"link/blocks_probe",offer);
        byte[] data=BlocksSpec.container(18);BlocksEssai.receive(c,"link/blocks_probe_data",new infinitylink.core.Wire.Out().u8(18).varInt(data.length).bytes(data).toBytes());
        check(obs.probe.equals("refuse"));check(BlocksMode.currentSize()==35723);
        BlocksMode.begin(c);BlocksEssai.receive(c,"link/blocks_probe",offer);
        var deadline=BlocksEssai.class.getDeclaredField("deadline");deadline.setAccessible(true);deadline.setLong(null,System.nanoTime()-1);BlocksEssai.tick();
        check(obs.probe.equals("delai"));check(BlocksMode.currentSize()==35723);
        BlocksMode.begin(c);BlocksMode.finish(c);c.reply=-1;BlocksEssai.receive(c,"link/blocks_probe",offer);check(c.reply==-1);
        BlocksMode.disconnect(c);System.clearProperty("sage.blocs.essai");System.clearProperty("infinitylink.blocs.essai");Files.delete(allow);
    }
    static void regionChecks(Path base) throws Exception {
        for(int version:new int[]{1,2,3,4})for(boolean external:new boolean[]{false,true})for(boolean stock:new boolean[]{false,true}) {
            Path world=Files.createTempDirectory(base,"region-");Path folder=world.resolve("region");Files.createDirectories(folder);
            var tag=new CompoundTag();var list=new ListTag();var state=new CompoundTag();state.putString("Name",stock?"sage:a4999":"minecraft:stone");list.add(state);tag.put("palette",list);
            var bytes=new java.io.ByteArrayOutputStream();
            try(var out=new java.io.DataOutputStream(net.minecraft.world.level.chunk.storage.RegionFileVersion.fromId(version).wrap(bytes))) {NbtIo.write(tag,out);}
            byte[] payload=bytes.toByteArray();Path region=folder.resolve("r.0.0.mca");
            try(var out=new java.io.RandomAccessFile(region.toFile(),"rw")) {
                out.setLength(12288);out.writeInt((2<<8)|1);out.seek(8192);out.writeInt(external?1:payload.length+1);out.writeByte(version|(external?128:0));if(!external)out.write(payload);
            }
            if(external)Files.write(folder.resolve("c.0.0.mcc"),payload);
            byte[] before=Files.readAllBytes(region);
            try {SoloGuard.scanWorld(world);check(!stock);}catch(java.io.IOException e){check(stock&&e.getMessage().contains("Ouverture refusee"));}
            check(Arrays.equals(before,Files.readAllBytes(region)));
        }
    }
}
