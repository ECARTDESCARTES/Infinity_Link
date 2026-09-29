package infinitylink.mc.blocks;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import infinitylink.core.BlocksSpec;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/** Refus en amont : aucune ouverture d'un monde contenant le stock, aucune réparation en air.
 * Le scanner ouvre les fichiers en lecture seule (RegionFile ouvrirait en écriture).
 * Le scan couvre régions/entités, level.dat, joueurs, structures et datapacks ZIP. */
public final class SoloGuard {
    private SoloGuard() {}
    public static BlockState check(BlockState state) {
        if(BlocksMode.forbidden(state)) { BlocksMode.guard("codec/structure/entite/plat"); throw new IllegalArgumentException("Etat du stock interdit hors profil Link"); }
        return state;
    }
    public static Codec<BlockState> codec(Codec<BlockState> original) { return original.xmap(SoloGuard::check,SoloGuard::check); }
    public static void checkTag(Tag tag) {
        if(tag instanceof StringTag s && BlocksSpec.stockName(s.value())) {
            BlocksMode.guard("NBT"); throw new IllegalArgumentException("Monde/structure contenant le stock refuse : "+s.value());
        }
        if(tag instanceof CompoundTag c) for(var entry:c.entrySet()) checkTag(entry.getValue());
        if(tag instanceof ListTag l) for(Tag child:l) checkTag(child);
    }
    private static void read(InputStream stream,boolean compressed) throws IOException {
        try(InputStream in=stream) {
            checkTag(compressed?NbtIo.readCompressed(in,NbtAccounter.create(64L<<20)):
                NbtIo.read(new DataInputStream(in),NbtAccounter.create(64L<<20)));
        }
    }
    public static void scanWorld(Path world) throws IOException {
        if(!Files.exists(world,LinkOption.NOFOLLOW_LINKS)) return;
        // Refuser les liens avant de lire un contenu extérieur au monde sélectionné.
        try(var paths=Files.walk(world)) {
            for(Path p:(Iterable<Path>)paths::iterator) {
                if(Files.isSymbolicLink(p)) throw new IOException("Verification blocs : lien symbolique non inspecte : "+p);
                if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)) continue;
                String name=p.getFileName().toString();
                try {
                    if(name.endsWith(".mca")) scanRegion(p);
                    else if(name.endsWith(".dat")||name.endsWith(".dat_old")||name.endsWith(".nbt")) read(Files.newInputStream(p),true);
                    else if(name.endsWith(".zip") && p.startsWith(world.resolve("datapacks"))) {
                        try(ZipFile zip=new ZipFile(p.toFile())) {
                            for(var it=zip.entries();it.hasMoreElements();) { var e=it.nextElement();
                                if(!e.isDirectory()&&e.getName().endsWith(".nbt")) read(zip.getInputStream(e),true);
                            }
                        }
                    }
                } catch(IllegalArgumentException e) { throw new IOException("Ouverture refusee avant sauvegarde : "+p+" : "+e.getMessage(),e); }
            }
        }
    }
    private static void scanRegion(Path path) throws IOException {
        try(RandomAccessFile file=new RandomAccessFile(path.toFile(),"r")) {
            if(file.length()==0) return;
            if(file.length()<8192) throw new IOException("Region tronquee : "+path);
            int[] offsets=new int[1024]; for(int i=0;i<1024;i++) offsets[i]=file.readInt();
            String[] coords=path.getFileName().toString().split("\\.");
            if(coords.length!=4) throw new IOException("Nom de region invalide");
            int rx=Integer.parseInt(coords[1]),rz=Integer.parseInt(coords[2]);
            for(int i=0;i<1024;i++) {
                int entry=offsets[i]; if(entry==0) continue;
                long start=(long)(entry>>>8)*4096; int sectors=entry&255;
                if(start<8192||sectors==0||start+5>file.length()) throw new IOException("Position de region invalide");
                file.seek(start); int length=file.readInt(), code=file.readUnsignedByte();
                if(length<1||length>sectors*4096-4||start+4+length>file.length()) throw new IOException("Longueur de region invalide");
                RegionFileVersion version=RegionFileVersion.fromId(code&127);
                if(version==null) throw new IOException("Compression de region inconnue");
                InputStream raw;
                if((code&128)!=0) {
                    Path external=path.resolveSibling("c."+(rx*32+(i&31))+"."+(rz*32+(i>>>5))+".mcc");
                    if(Files.isSymbolicLink(external)) throw new IOException("Region externe liee");
                    raw=Files.newInputStream(external);
                } else { byte[] bytes=new byte[length-1]; file.readFully(bytes); raw=new ByteArrayInputStream(bytes); }
                try(raw) { read(version.wrap(raw),false); }
            }
        }
    }
}
