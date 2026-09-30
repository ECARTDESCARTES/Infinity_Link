import infinitylink.core.scenes.ModelCollisions;
import java.nio.ByteBuffer;
import java.util.List;

public final class ModelCollisionsTest {
    static int n; static void check(boolean b){if(!b)throw new AssertionError("collision test "+(n+1));n++;}
    static ModelCollisions.Box b(double x0,double y0,double z0,double x1,double y1,double z1){return new ModelCollisions.Box(x0,y0,z0,x1,y1,z1);}
    static void rejects(byte[] data){try{ModelCollisions.decode(data);throw new AssertionError("accepted malformed");}catch(IllegalArgumentException expected){n++;}}
    public static void main(String[] args){
        var i=new ModelCollisions();i.put(new ModelCollisions.Pose(1,-16,66,0,List.of(b(-2,0,0,-1,3,1),b(1,0,0,2,3,1))));
        check(i.poses()==1&&i.boxes()==2);check(i.query(b(-3e7,-1000,-3e7,3e7,1000,3e7)).size()==2);check(i.query(b(-16.3,66,-1,-15.7,68,2)).isEmpty());check(i.query(b(-19,66,0,-17,68,1)).size()==1);
        i.move(1,0,0,0);check(i.query(b(-19,66,0,-17,68,1)).isEmpty());check(i.query(b(-3,0,0,-1,2,1)).size()==1);
        ByteBuffer buf=ByteBuffer.allocate(60);buf.putInt(ModelCollisions.MAGIC).putInt(3).putDouble(0).putDouble(66).putDouble(0).putInt(1);for(float v:new float[]{0,0,0,1,0.5f,1})buf.putFloat(v);
        var p=ModelCollisions.decode(buf.array());check(p.local().size()==1&&p.local().get(0).y1()==0.5);i.put(p);check(i.poses()==2);
        i.put(new ModelCollisions.Pose(3,0,66,0,List.of()));check(i.poses()==1&&i.boxes()==2);i.remove(1);check(i.poses()==0&&i.boxes()==0);
        rejects(new byte[0]);var bad=buf.array().clone();bad[0]=0;rejects(bad);bad=buf.array().clone();ByteBuffer.wrap(bad).putInt(32,32769);rejects(bad);bad=buf.array().clone();ByteBuffer.wrap(bad).putFloat(36,Float.NaN);rejects(bad);
        i.put(new ModelCollisions.Pose(9,0,0,0,List.of(b(0,0,0,1,1,1))));i.clear();check(i.ids().isEmpty());
        System.out.println("ModelCollisionsTest : "+n+"/"+n);
    }
}
