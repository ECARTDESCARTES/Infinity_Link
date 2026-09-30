package infinitylink.mc.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 1.1.2 : ajoute les touches d'InfinityLink (Keys.ALL) à Options.keyMappings avant la lecture d'options.txt, comme le
 *  fait Fabric API : elles apparaissent dans Options > Commandes et sont enregistrées avec les touches vanilla. */
@Mixin(value = Options.class, remap = false)
public abstract class OptionsMixin {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;

    @Inject(method = "load()V", at = @At("HEAD"), require = 1, remap = false)
    private void infinitylink$keys(CallbackInfo ci) {
        try {
            List<KeyMapping> all = new ArrayList<>(Arrays.asList(keyMappings));
            for (KeyMapping k : infinitylink.mc.Keys.ALL) if (!all.contains(k)) all.add(k);
            keyMappings = all.toArray(new KeyMapping[0]);
        } catch (Throwable t) {
            infinitylink.mc.Bridge.STATE.error("touches (enregistrement)", t);
        }
    }

    /** Réparation 1.1.2 : un options.txt écrit par la 1.1.2 d'origine porte « key.keyboard.-1 » pour les touches sans
     *  valeur ; KeyMapping.setAll appellerait alors InputConstants.isKeyDown(-1) (IndexOutOfBoundsException). Toute
     *  touche du mod à une valeur négative repasse à InputConstants.UNKNOWN après la lecture. */
    @Inject(method = "load()V", at = @At("TAIL"), require = 1, remap = false)
    private void infinitylink$repairKeys(CallbackInfo ci) {
        try {
            boolean changed = false;
            for (KeyMapping k : infinitylink.mc.Keys.ALL) {
                if (k.saveString().endsWith(".-1")) { k.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN); changed = true; }
            }
            if (changed) KeyMapping.resetMapping();
        } catch (Throwable t) {
            infinitylink.mc.Bridge.STATE.error("touches (reparation)", t);
        }
    }
}
