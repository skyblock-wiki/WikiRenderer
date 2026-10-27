package com.pigicial.wikirenderer.render.area;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Based on ChunkSectionsToRender, but drawn by us so that clients hooking ChunkSectionsToRender#renderGroup for world terrain
// (Lunar Client swaps in a Sodium-only pipeline there, which fails to compile against vanilla core/terrain) don't touch the area mesh
@Environment(EnvType.CLIENT)
public abstract class AreaTerrainDraws {
    private final GpuBufferSlice terrainTransformUbo;
    private final int maxIndicesRequired;

    private AreaTerrainDraws(GpuBufferSlice terrainTransformUbo, int maxIndicesRequired) {
        this.terrainTransformUbo = terrainTransformUbo;
        this.maxIndicesRequired = maxIndicesRequired;
    }

    protected abstract void render(ChunkSectionLayer layer, RenderPass renderPass, @Nullable GpuBuffer defaultIndexBuffer, @Nullable IndexType defaultIndexType);

    // Based on ChunkSectionsToRender#renderGroup and #renderLayers
    public void renderGroup(ChunkSectionLayerGroup group, RenderPass renderPass, GpuSampler sampler, GpuTextureView atlas) {
        GpuTextureView lightmap = Minecraft.getInstance().gameRenderer.lightmap();
        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer defaultIndexBuffer = this.maxIndicesRequired == 0 ? null : autoIndices.getBuffer();
        IndexType defaultIndexType = this.maxIndicesRequired == 0 ? null : autoIndices.type();
        renderPass.setUniform("TerrainUniform", this.terrainTransformUbo);
        renderPass.setUniform("Sampler0", atlas, sampler);
        renderPass.setUniform("Sampler2", lightmap, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

        for (ChunkSectionLayer layer : group.layers()) {
            renderPass.pushDebugGroup(() -> "Area terrain layer: " + layer.label());
            try {
                this.render(layer, renderPass, defaultIndexBuffer, defaultIndexType);
            } finally {
                renderPass.popDebugGroup();
            }
        }
    }

    // other mods may also swap ChunkSectionLayer#pipeline, so fall back to vanilla's pipeline if theirs can't be compiled
    private static CompiledRenderPipeline compiledPipeline(ChunkSectionLayer layer, boolean multiDraw) {
        CompiledRenderPipeline compiled = RenderSystem.getCompiledPipelineNullable(layer.pipeline(multiDraw));
        if (compiled != null) {
            return compiled;
        }

        RenderPipeline vanilla = switch (layer) {
            case SOLID -> multiDraw ? RenderPipelines.SOLID_TERRAIN_MULTIDRAW : RenderPipelines.SOLID_TERRAIN;
            case CUTOUT -> multiDraw ? RenderPipelines.CUTOUT_TERRAIN_MULTIDRAW : RenderPipelines.CUTOUT_TERRAIN;
            case TRANSLUCENT -> multiDraw ? RenderPipelines.TRANSLUCENT_TERRAIN_MULTIDRAW : RenderPipelines.TRANSLUCENT_TERRAIN;
        };
        return RenderSystem.getCompiledPipeline(vanilla);
    }

    // Based on ChunkSectionsToRender.DrawIndirect
    @Environment(EnvType.CLIENT)
    public static final class DrawIndirect extends AreaTerrainDraws {
        private final EnumMap<ChunkSectionLayer, List<ChunkSectionsToRender.GpuMultiDrawIndexedIndirect>> drawGroupsPerLayer;
        private final GpuBufferSlice chunkSectionInfos;

        public DrawIndirect(GpuBufferSlice terrainTransformUbo, EnumMap<ChunkSectionLayer, List<ChunkSectionsToRender.GpuMultiDrawIndexedIndirect>> drawGroupsPerLayer, int maxIndicesRequired, GpuBufferSlice chunkSectionInfos) {
            super(terrainTransformUbo, maxIndicesRequired);
            this.drawGroupsPerLayer = drawGroupsPerLayer;
            this.chunkSectionInfos = chunkSectionInfos;
        }

        @Override
        protected void render(ChunkSectionLayer layer, RenderPass renderPass, @Nullable GpuBuffer defaultIndexBuffer, @Nullable IndexType defaultIndexType) {
            renderPass.setPipeline(compiledPipeline(layer, true));
            int maxDrawIndirectDrawCount = RenderSystem.getDevice().getDeviceInfo().limits().maxDrawIndirectDrawCount();
            List<ChunkSectionsToRender.GpuMultiDrawIndexedIndirect> drawGroups = this.drawGroupsPerLayer.get(layer);
            if (!drawGroups.isEmpty()) {
                renderPass.setVertexBuffer(1, this.chunkSectionInfos);
            }

            for (ChunkSectionsToRender.GpuMultiDrawIndexedIndirect indirectDraw : drawGroups) {
                if (indirectDraw.drawCount() > 0) {
                    renderPass.setVertexBuffer(0, indirectDraw.vertexBuffer());
                    IndexType indexType = indirectDraw.indexType() == null ? defaultIndexType : indirectDraw.indexType();
                    renderPass.setIndexBuffer(indirectDraw.indexBuffer() == null ? defaultIndexBuffer : indirectDraw.indexBuffer().buffer(), indexType);
                    GpuBuffer buffer = indirectDraw.indirectCommandBuffer().buffer();
                    long startOffset = indirectDraw.indirectCommandBuffer().offset();
                    int remainingDrawCount = indirectDraw.drawCount();

                    while (remainingDrawCount > 0) {
                        int passDrawCount = Integer.min(remainingDrawCount, maxDrawIndirectDrawCount);
                        long length = passDrawCount * 20L;
                        renderPass.drawIndexedIndirect(buffer.slice(startOffset, length), passDrawCount);
                        remainingDrawCount -= passDrawCount;
                        startOffset += length;
                    }
                }
            }
        }
    }

    // Based on ChunkSectionsToRender.DrawSeparate
    @Environment(EnvType.CLIENT)
    public static final class DrawSeparate extends AreaTerrainDraws {
        private final Map<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> drawsPerLayer;
        private final GpuBufferSlice[] chunkSectionInfos;

        public DrawSeparate(GpuBufferSlice terrainTransformUbo, Map<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> drawsPerLayer, int maxIndicesRequired, GpuBufferSlice[] chunkSectionInfos) {
            super(terrainTransformUbo, maxIndicesRequired);
            this.drawsPerLayer = drawsPerLayer;
            this.chunkSectionInfos = chunkSectionInfos;
        }

        @Override
        protected void render(ChunkSectionLayer layer, RenderPass renderPass, @Nullable GpuBuffer defaultIndexBuffer, @Nullable IndexType defaultIndexType) {
            renderPass.setPipeline(compiledPipeline(layer, false));
            List<RenderPass.Draw<GpuBufferSlice[]>> draws = this.drawsPerLayer.get(layer);
            if (!draws.isEmpty()) {
                renderPass.drawMultipleIndexed(draws, defaultIndexBuffer, defaultIndexType, List.of("ChunkSection"), this.chunkSectionInfos);
            }
        }
    }
}
