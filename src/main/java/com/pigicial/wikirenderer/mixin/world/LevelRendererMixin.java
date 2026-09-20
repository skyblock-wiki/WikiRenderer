package com.pigicial.wikirenderer.mixin.world;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.pigicial.wikirenderer.render.area.AreaSelectionHelper;
import com.pigicial.wikirenderer.render.area.WorldBlockMesh;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(
            method = "lambda$addMainPass$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;executeOutline(Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V"
            )
    )
    public void drawAreaSelection(GpuBufferSlice terrainFog, boolean useImprovedTransparency, ChunkSectionsToRender chunkSectionsToRender, FeatureRenderDispatcher.PreparedFrame featureFrame, boolean hasAlwaysOnTopGizmos, boolean consistentDepthRequired, CallbackInfo ci) {
        AreaSelectionHelper.renderSelectionBox();
    }

    @Inject(method = "close", at = @At(value = "HEAD"))
    public void resetTerrainSampler1(CallbackInfo ci) {
        if (WorldBlockMesh.terrainSampler != null) {
            WorldBlockMesh.terrainSampler.close();
            WorldBlockMesh.terrainSampler = null;
        }
    }

    @Inject(
            method = "lambda$addMainPass$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/textures/GpuSampler;close()V"
            )
    )
    public void resetTerrainSampler2(CallbackInfo ci) {
        if (WorldBlockMesh.terrainSampler != null) {
            WorldBlockMesh.terrainSampler.close();
            WorldBlockMesh.terrainSampler = null;
        }
    }
}
