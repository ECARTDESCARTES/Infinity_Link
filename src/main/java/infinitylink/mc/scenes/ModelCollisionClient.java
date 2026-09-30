package infinitylink.mc.scenes;

import infinitylink.core.scenes.ModelCollisions;
import infinitylink.mc.Bridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.List;

/** Les volumes reçus sont ajoutés à la physique vanilla, sur le fil du client uniquement. */
public final class ModelCollisionClient {
    public static final ModelCollisions INDEX=new ModelCollisions();
    private ModelCollisionClient(){}
    public static boolean receive(String path,byte[] data){
        if(!"link/model_collisions".equals(path))return false;
        var connection=Minecraft.getInstance().getConnection();
        var pose=ModelCollisions.decode(data);
        Minecraft.getInstance().execute(()->{
            if(connection==null||Minecraft.getInstance().getConnection()!=connection)return;
            if(!Bridge.STATE.modelesOn()||!Bridge.STATE.collisionsOn())return;
            try { INDEX.put(pose);sync(Minecraft.getInstance(),pose.id()); }
            catch (RuntimeException e) { Bridge.STATE.error("volumes physiques invalides", e); connection.getConnection().disconnect(net.minecraft.network.chat.Component.literal("InfinityLink : volumes physiques invalides")); }
        });return true;
    }
    private static void sync(Minecraft mc,int id){if(mc.level==null)return;Entity e=mc.level.getEntity(id);if(e!=null)INDEX.move(id,e.getX(),e.getY(),e.getZ());}
    public static void tick(Minecraft mc){if(mc.level==null)return;for(int id:INDEX.ids())sync(mc,id);}
    public static List<VoxelShape> append(Object world,Entity entity,AABB box,List<VoxelShape> original){
        if(!(world instanceof ClientLevel)||INDEX.poses()==0||entity!=null&&entity.noPhysics)return original;
        // Respecter la requête native : inclure une forme adjacente transforme noCollision en faux chevauchement.
        var nearby=INDEX.query(new ModelCollisions.Box(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ));
        if(nearby.isEmpty())return original;List<VoxelShape> list=new ArrayList<>(original.size()+nearby.size());list.addAll(original);
        for(var b:nearby)list.add(Shapes.box(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1()));return list;
    }
    public static void remove(int id){INDEX.remove(id);}
    public static void clear(){Minecraft.getInstance().execute(INDEX::clear);}
}
