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

    /** 1.1.4 : une touche enregistrée « key.keyboard.-1 » (InfinityLink 1.1.2) devient « sans touche » (UNKNOWN). */
    @Inject(method = "load()V", at = @At("TAIL"), require = 1, remap = false)
    private void infinitylink$touchesInvalides(CallbackInfo ci) {
        try {
            boolean change = false;
            for (KeyMapping k : keyMappings) {
                if (k.saveString().equals("key.keyboard.-1")) { k.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN); change = true; }
            }
            if (change) KeyMapping.resetMapping();
        } catch (Throwable t) {
            infinitylink.mc.Bridge.STATE.error("touches (reprise des touches -1)", t);
        }
    }
}
