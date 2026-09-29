package infinitylink.mc.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** CreativeModeTabs.CACHED_PARAMETERS (private static, non final) : le remettre à null force tryRebuildTabContents
 *  à reconstruire tous les onglets (au prochain containerTick de l'écran créatif, ou à son ouverture). */
@Mixin(value = CreativeModeTabs.class, remap = false)
public interface CreativeModeTabsAccessor {
    @Accessor(value = "CACHED_PARAMETERS", remap = false)
    static void sage$setCachedParameters(CreativeModeTab.ItemDisplayParameters p) {
        throw new AssertionError("mixin");
    }
}
