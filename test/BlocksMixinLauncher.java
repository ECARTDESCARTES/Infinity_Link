import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;
/** Initialise Knot et ses mixins, puis lance le banc : jamais Minecraft.main ni fenêtre. */
public final class BlocksMixinLauncher {
    public static void main(String[] args) throws Exception {
        Knot knot=new Knot(EnvType.CLIENT);
        ClassLoader target=knot.init(new String[]{"--gameDir",args[0],"--version","26.3"});
        knot.addToClassPath(java.nio.file.Path.of(args[1]));
        Class.forName("BlocksMinecraftCodecTest",true,target).getMethod("main",String[].class)
            .invoke(null,(Object)new String[]{"woven"});
    }
}
