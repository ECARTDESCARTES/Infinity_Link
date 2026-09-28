package sage.link.mc.mixin;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sage.link.mc.blocks.SageBlock;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 0.3.3 : les 100 000 états de réserve des blocs étendus (sage:la00 … sage:a4999) n'ont de modèle que si le serveur les
 * attribue. ModelManager écrivait « Missing model for variant » pour chacun des autres, à chaque rechargement des
 * ressources : 100 000 lignes par rechargement, 1,7 million et 148 Mo de journal mesurés le 28/09/2026. Ces états sont
 * comptés et résumés en une ligne ; tout autre avertissement de modèle manquant reste écrit tel quel.
 */
@Mixin(value = ModelManager.class, remap = false)
public abstract class ModelManagerMixin {
    @Unique private static final AtomicInteger SAGE$RESERVE = new AtomicInteger();

    @Redirect(method = "lambda$createBlockStateToModelDispatch$0(Ljava/util/Map;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;Lnet/minecraft/world/level/block/state/BlockState;)V",
              at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V"),
              require = 1, remap = false)
    private static void sage$avertir(Logger journal, String message, Object etat) {
        if (etat instanceof BlockState st && st.getBlock() instanceof SageBlock) { SAGE$RESERVE.incrementAndGet(); return; }
        journal.warn(message, etat);
    }

    @Inject(method = "createBlockStateToModelDispatch(Ljava/util/Map;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;)Ljava/util/Map;",
            at = @At("RETURN"), require = 1, remap = false)
    private static void sage$bilan(Map<?, ?> modeles, BlockStateModel manquant, CallbackInfoReturnable<Map<?, ?>> cir) {
        int n = SAGE$RESERVE.getAndSet(0);
        if (n > 0) System.out.println("[sage_link] blocs : " + n + " etats de reserve sans modele (normal : le serveur attribue ceux qu'il utilise)");
    }
}
