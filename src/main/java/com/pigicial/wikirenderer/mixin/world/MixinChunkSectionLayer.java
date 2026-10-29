package com.pigicial.wikirenderer.mixin.world;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.pigicial.wikirenderer.render.area.WorldBlockMesh;
import com.pigicial.wikirenderer.render.export.CustomRenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.pipeline.RenderPipelines;
import net.minecraft.client.renderer.pipeline.variant.TerrainRenderPipelineVariant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSectionLayer.class)
public class MixinChunkSectionLayer {

    @Shadow
    @Final
    private TerrainRenderPipelineVariant variant;

    @Inject(method = "pipeline", at = @At("HEAD"), cancellable = true)
    private void onGetPipeline(boolean multiDraw, boolean improvedFog, boolean wireframe, OitStage oitStage, CallbackInfoReturnable<RenderPipeline> cir) {
        ChunkSectionLayer layer = (ChunkSectionLayer) (Object) this;

        if (WorldBlockMesh.overrideTerrainTransparencyRenderPipelines) {
            if (layer == ChunkSectionLayer.CUTOUT || layer == ChunkSectionLayer.SOLID) {
                cir.setReturnValue(CustomRenderPipelines.terrain(new RenderPipelines.TerrainParameters(variant, improvedFog, multiDraw, wireframe, oitStage)));
                // cir.setReturnValue(multiDraw ? CustomRenderPipelines.CORE_CUTOUT_TERRAIN_MULTIDRAW_NO_TRANSPARENCY : CustomRenderPipelines.CORE_CUTOUT_TERRAIN_NO_TRANSPARENCY);
            } else if (layer == ChunkSectionLayer.SOLID) {
                // used to fix leaves that have transparency turned off
                // in that case, despite being cutout-based-blocks, they can have transparent pixels
                // cir.setReturnValue(multiDraw ? CustomRenderPipelines.CORE_SOLID_TERRAIN_MULTIDRAW_NO_TRANSPARENCY : CustomRenderPipelines.CORE_SOLID_TERRAIN_NO_TRANSPARENCY);
            }
        }
    }

}