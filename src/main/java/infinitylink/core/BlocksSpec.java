package infinitylink.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.BiConsumer;

/** Contrat B1 indépendant du jeu, partagé par le stock et ses bancs. */
public final class BlocksSpec {
    private BlocksSpec() {}
    public static final int BASE = 35723, STOCK = 100000, TOTAL = BASE + STOCK;
    public static final String SHA1 = "49cf91f0c06591ffb6b2ab7d6d8e98a6d29291a3";
    public record Part(String prefix, int count, int digits, int offset) {}
    public static final java.util.List<Part> PARTS = java.util.List.of(
        new Part("la",50,2,0), new Part("lb",200,3,800), new Part("v",100,2,4000),
        new Part("b",900,3,5600), new Part("a",5000,4,20000));
    public static int bits(int size) { if (size < 1) throw new IllegalArgumentException(); return 32-Integer.numberOfLeadingZeros(size-1); }
    public static void names(BiConsumer<Part,String> sink) {
        for (Part p : PARTS) for (int i=0;i<p.count;i++)
            sink.accept(p, p.prefix + String.format(Locale.ROOT,"%0"+p.digits+"d",i));
    }
    public static boolean stockName(String name) {
        if (!name.startsWith("sage:")) return false;
        String path=name.substring(5);
        for (Part p:PARTS) if (path.startsWith(p.prefix) && path.length()==p.prefix.length()+p.digits) {
            String digits=path.substring(p.prefix.length());
            if (digits.chars().allMatch(c->c>='0'&&c<='9') && Integer.parseInt(digits)<p.count) return true;
        }
        return false;
    }
    public static MessageDigest digest(String algorithm) {
        try { return MessageDigest.getInstance(algorithm); } catch (Exception e) { throw new AssertionError(e); }
    }
    public static void hashState(MessageDigest d,String key,int s) { d.update((key+"[s="+s+"]\n").getBytes(StandardCharsets.UTF_8)); }
    public static String fingerprint() {
        MessageDigest d=digest("SHA-1"); names((p,n)->{for(int s=0;s<16;s++) hashState(d,"sage:"+n,s);});
        return HexFormat.of().formatHex(d.digest());
    }
    public static int value(int bits,int i) { return bits==16 || (i&1)==0 ? i*7%BASE : BASE+(i*24+7)%STOCK; }
    public static int bytes(int bits) { if(bits!=16&&bits!=18) throw new IllegalArgumentException(); return 1+8*((4096+64/bits-1)/(64/bits)); }
    public static byte[] container(int bits) {
        Wire.Out out=new Wire.Out().u8(bits); int per=64/bits;
        for(int i=0;i<4096;i+=per) { long word=0; for(int k=0;k<per&&i+k<4096;k++) word|=(long)value(bits,i+k)<<(k*bits);
            for(int shift=56;shift>=0;shift-=8) out.u8((int)(word>>>shift)); }
        return out.toBytes();
    }
    public static void offer(byte[] data) {
        if(data==null) throw new IllegalArgumentException("offre absente");
        Wire.In in=new Wire.In(data);
        if(in.varInt()!=1||in.varInt()!=BASE||in.varInt()!=STOCK||in.u8()!=18||
            !HexFormat.of().formatHex(in.bytes(20)).equals(SHA1)||in.remaining()!=0) throw new IllegalArgumentException("offre incompatible");
    }
    public static byte[] data(byte[] data,int expected) {
        if(data==null) throw new IllegalArgumentException("donnees absentes");
        Wire.In in=new Wire.In(data);
        int bits=in.u8(), n=in.varInt();
        if(bits!=expected||n!=bytes(bits)) throw new IllegalArgumentException("ordre/taille du probe");
        byte[] result=in.bytes(n);
        if(in.remaining()!=0||(result[0]&255)!=bits) throw new IllegalArgumentException("conteneur invalide");
        return result;
    }
    public static final class Observation {
        public volatile int stock, base=BASE, vue=BASE, bits16, bits18;
        public volatile String empreinte="", probe="off";
        public final java.util.concurrent.atomic.AtomicLong garde=new java.util.concurrent.atomic.AtomicLong();
        public String json() { return "{\"stock\":"+stock+",\"base\":"+base+",\"empreinte\":\""+empreinte+
            "\",\"vue\":"+vue+",\"garde\":"+garde.get()+",\"probe\":{\"bits16\":"+bits16+",\"bits18\":"+bits18+",\"etat\":\""+probe+"\"}}"; }
    }
}
