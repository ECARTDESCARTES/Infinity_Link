package infinitylink.core.scenes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/** COL1 : volumes physiques de poses, indexés par chunk. Aucun remplissage des espaces du modèle. */
public final class ModelCollisions {
    public static final int MAGIC=0x434f4c31, MAX_BOXES=32768, MAX_TOTAL=1_000_000;
    public record Box(double x0,double y0,double z0,double x1,double y1,double z1) {
        public boolean intersects(Box b) { return x0<b.x1-1e-7&&x1>b.x0+1e-7&&y0<b.y1-1e-7&&y1>b.y0+1e-7&&z0<b.z1-1e-7&&z1>b.z0+1e-7; }
        Box move(double x,double y,double z) {return new Box(x0+x,y0+y,z0+z,x1+x,y1+y,z1+z);}
    }
    public record Pose(int id,double x,double y,double z,List<Box> local) {}
    private record Body(Pose pose,Box bounds,List<Box> boxes,Set<Long> cells) {}
    private final Map<Integer,Body> bodies=new HashMap<>();
    private final Map<Long,Set<Integer>> cells=new HashMap<>();
    private int count;
    public static Pose decode(byte[] bytes) {
        if(bytes==null||bytes.length<36)throw new IllegalArgumentException("COL1 tronqué");
        ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if(b.getInt()!=MAGIC)throw new IllegalArgumentException("COL1 version inconnue");
        int id=b.getInt();double x=b.getDouble(),y=b.getDouble(),z=b.getDouble();int n=b.getInt();
        if(id<=0||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>3e7||Math.abs(z)>3e7||n<0||n>MAX_BOXES||b.remaining()!=n*24)throw new IllegalArgumentException("COL1 bornes invalides");
        List<Box> list=new ArrayList<>(n);
        for(int k=0;k<n;k++) {float[] v=new float[6];for(int j=0;j<6;j++){v[j]=b.getFloat();if(!Float.isFinite(v[j])||Math.abs(v[j])>16384)throw new IllegalArgumentException("COL1 coordonnée invalide");}
            if(v[3]<=v[0]||v[4]<=v[1]||v[5]<=v[2])throw new IllegalArgumentException("COL1 boîte vide");list.add(new Box(v[0],v[1],v[2],v[3],v[4],v[5])); }
        return new Pose(id,x,y,z,List.copyOf(list));
    }
    private static int cell(double v){return (int)Math.floor(v/16);}
    private static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    public void put(Pose p) {
        if(p.local.isEmpty()){remove(p.id);return;}
        List<Box> boxes=p.local.stream().map(b->b.move(p.x,p.y,p.z)).toList();
        double[] v={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(Box b:boxes){v[0]=Math.min(v[0],b.x0);v[1]=Math.min(v[1],b.y0);v[2]=Math.min(v[2],b.z0);v[3]=Math.max(v[3],b.x1);v[4]=Math.max(v[4],b.y1);v[5]=Math.max(v[5],b.z1);}
        Box bound=new Box(v[0],v[1],v[2],v[3],v[4],v[5]);int x0=cell(v[0]),x1=cell(v[3]),z0=cell(v[2]),z1=cell(v[5]);
        if((long)(x1-x0+1)*(z1-z0+1)>16384||count+p.local.size()-(bodies.containsKey(p.id)?bodies.get(p.id).boxes.size():0)>MAX_TOTAL)throw new IllegalArgumentException("COL1 budget dépassé");
        Set<Long> keys=new HashSet<>();for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)keys.add(key(x,z));
        remove(p.id);for(long k:keys)cells.computeIfAbsent(k,c->new HashSet<>()).add(p.id);bodies.put(p.id,new Body(p,bound,boxes,keys));count+=boxes.size();
    }
    public void move(int id,double x,double y,double z){Body b=bodies.get(id);if(b!=null&&(b.pose.x!=x||b.pose.y!=y||b.pose.z!=z))put(new Pose(id,x,y,z,b.pose.local));}
    public void remove(int id){Body b=bodies.remove(id);if(b==null)return;count-=b.boxes.size();for(long k:b.cells){Set<Integer> ids=cells.get(k);ids.remove(id);if(ids.isEmpty())cells.remove(k);}}
    public List<Box> query(Box area){List<Box> out=new ArrayList<>();
        if((long)(cell(area.x1)-cell(area.x0)+1)*(cell(area.z1)-cell(area.z0)+1)>4096){for(Body b:bodies.values())if(b.bounds.intersects(area))for(Box box:b.boxes)if(box.intersects(area))out.add(box);return out;}
        Set<Integer> seen=new HashSet<>();for(int x=cell(area.x0);x<=cell(area.x1);x++)for(int z=cell(area.z0);z<=cell(area.z1);z++){Set<Integer> ids=cells.get(key(x,z));if(ids!=null)for(int id:ids)if(seen.add(id)){Body b=bodies.get(id);if(b.bounds.intersects(area))for(Box box:b.boxes)if(box.intersects(area))out.add(box);}}return out;}
    public Set<Integer> ids(){return Set.copyOf(bodies.keySet());}
    public int poses(){return bodies.size();}
    public int boxes(){return count;}
    public void clear(){bodies.clear();cells.clear();count=0;}
}
