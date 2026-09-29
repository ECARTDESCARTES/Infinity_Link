package infinitylink.mc.mixin;

import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import infinitylink.mc.tabs.SageTabs;

import java.util.stream.Stream;

/** J5 (§4.5, R7) : l'arbre d'identifiants de la recherche créative voit l'id sage d'un contenu (altiscraft:atmbanqueblock)
 *  au lieu de son porteur (minecraft:paper). lambda$updateCreativeTooltips$3 = pile -> typeHolder().unwrapKey().map(..).stream()
 *  (javap 26.3, IdSearchTree) ; tourne sur Util.backgroundExecutor(). Toute autre pile : vanilla. */
@Mixin(value = SessionSearchTrees.class, remap = false)
public abstract class SessionSearchTreesMixin {
    @Inject(method = "lambda$updateCreativeTooltips$3(Lnet/minecraft/world/item/ItemStack;)Ljava/util/stream/Stream;",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void sage$searchId(ItemStack stack, CallbackInfoReturnable<Stream<Identifier>> cir) {
        Identifier id = SageTabs.searchId(stack);
        if (id != null) cir.setReturnValue(Stream.of(id));
    }
}
