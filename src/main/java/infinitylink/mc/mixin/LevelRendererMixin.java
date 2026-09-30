package infinitylink.mc.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import infinitylink.mc.lod.LodClient;

/** Rendu lointain (§9). 26.3 : LevelRenderer.render prépare l'image (hors de toute passe : envois au GPU) ;
 *  la passe principale (lambda$addMainPass$0) ouvre UNE RenderPass sur la cible principale (couleur + profondeur),
 *  appelle executeSolid (terrain opaque puis éléments solides) puis la transparence. On dessine à la fin
 *  d'executeSolid, dans cette même passe : le vrai terrain a déjà écrit la profondeur et cache le lointain. */
@Mixin(value = LevelRenderer.class, remap = false)
public abstract class LevelRendererMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
            at = @At("HEAD"), require = 1, remap = false)
    private void sage$lodFrame(GraphicsResourceAllocator allocator, boolean b1, CameraRenderState camera, GpuBufferSlice fog,
                               Vector4f fogColor, boolean b2, boolean b3, CallbackInfo ci) {
        LodClient.frameBegin(camera);
        infinitylink.mc.scenes.Scenes3d.frameBegin(camera); // scènes 3D (1.1.4), attrape tout
    }

    @Inject(method = "executeSolid(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
            at = @At("TAIL"), require = 1, remap = false)
    private void sage$lodDraw(ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass, CallbackInfo ci) {
        LodClient.drawSolid(pass);
        infinitylink.mc.scenes.Scenes3d.draw(pass);
    }
}
