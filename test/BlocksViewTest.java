import infinitylink.core.*;
import java.util.*;
public final class BlocksViewTest {
    static int checks;
    static void check(boolean b) { checks++; if(!b) throw new AssertionError("controle "+checks); }
    static void reject(Runnable r) { try {r.run();} catch(RuntimeException e) {checks++;return;} throw new AssertionError("acceptation indue"); }
    public static void main(String[] args) {
        int[] sizes={35723,65536,65537,131072,131073,135723}, bits={16,16,17,17,18,18};
        for(int i=0;i<sizes.length;i++) check(BlocksSpec.bits(sizes[i])==bits[i]);
        check(BlocksSpec.fingerprint().equals(BlocksSpec.SHA1));
        Set<String> names=new HashSet<>(); BlocksSpec.names((p,n)->{check(names.add(n));check(BlocksSpec.stockName("sage:"+n));}); check(names.size()==6250);
        for(String s:List.of("sage:lb200","sage:a5000","sage:v100","sage:la50","minecraft:air","sage:a00000","sage:a-001")) check(!BlocksSpec.stockName(s));
        byte[] offer=HexFormat.of().parseHex("018b9702a08d061249cf91f0c06591ffb6b2ab7d6d8e98a6d29291a3"); BlocksSpec.offer(offer);check(offer.length==28);
        for(int i=0;i<offer.length;i++) {byte[] bad=offer.clone();bad[i]^=1;reject(()->BlocksSpec.offer(bad));}
        for(int i=0;i<offer.length;i++) {byte[] bad=Arrays.copyOf(offer,i);reject(()->BlocksSpec.offer(bad));}
        reject(()->BlocksSpec.offer(Arrays.copyOf(offer,29)));
        String[] hashes={"d5aa20ba4a868a29e0846762086e7448f9812d6ccbb07c9ac5f147852a81cffd","b9b20743678d91bfd624bb72da219d573911068eabef7339913a6a9d35dc6ba4"};
        int ix=0;
        for(int b:new int[]{16,18}) {
            byte[] c=BlocksSpec.container(b); check(c.length==BlocksSpec.bytes(b));
            check(HexFormat.of().formatHex(BlocksSpec.digest("SHA-256").digest(c)).equals(hashes[ix++]));
            byte[] packet=new Wire.Out().u8(b).varInt(c.length).bytes(c).toBytes();
            check(Arrays.equals(c,BlocksSpec.data(packet,b)));
            reject(()->BlocksSpec.data(packet,b==16?18:16));
            reject(()->BlocksSpec.data(Arrays.copyOf(packet,packet.length-1),b));
            reject(()->BlocksSpec.data(Arrays.copyOf(packet,packet.length+1),b));
        }
        System.out.println("BlocksViewTest : "+checks+" controles OK");
    }
}
