package com.pigicial.wikirenderer.render.area;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.pigicial.wikirenderer.mixin.access.RenderSectionInvoker;
import com.pigicial.wikirenderer.util.AnimationTimingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MeshRenderSection extends SectionRenderDispatcher.RenderSection {
    public static final int RENDER_SECTION_SIZE = 32; // vanilla is 16, but it seems 32 is perfectly doable without hitting vertex limits
    private final int sectionX;
    private final int sectionY;
    private final int sectionZ;
    private double distanceFromCenter;

    protected Map<BlockPos, BlockEntity> blockEntities = new ConcurrentHashMap<>();
    protected List<Integer> animationCompletionTimings = new LinkedList<>();
    protected volatile boolean isDirty = false;
    protected volatile boolean isBuilding = false;
    protected volatile boolean forceUpdate = false;
    protected volatile boolean buildAttempted = false;

    public MeshRenderSection(SectionRenderDispatcher dispatcher, int sectionX, int sectionY, int sectionZ) {
        dispatcher.super((int) getSectionIndex(sectionX, sectionY, sectionZ), 0);
        this.sectionX = sectionX;
        this.sectionY = sectionY;
        this.sectionZ = sectionZ;
    }

    public void setDistanceFromCenter(double distanceFromCenter) {
        this.distanceFromCenter = distanceFromCenter;
    }

    public double getDistanceFromCenter() {
        return distanceFromCenter;
    }

    public BlockPos getFrom() {
        return new BlockPos(sectionToBlockCoordinate(sectionX), sectionToBlockCoordinate(sectionY), sectionToBlockCoordinate(sectionZ));
    }

    @Override
    public @NonNull BlockPos getRenderOrigin() {
        return this.getFrom();
    }

    public BlockPos getTo() {
        return getFrom().offset(RENDER_SECTION_SIZE - 1, RENDER_SECTION_SIZE - 1, RENDER_SECTION_SIZE - 1);
    }

    public void buildAndSubmit(WorldBlockMesh mesh) {
        if (isBuilding) return;
        isBuilding = true;

        Minecraft client = Minecraft.getInstance();
        boolean cutoutLeaves = client.options.cutoutLeaves().get();
        BlockStateModelSet blockModelSet = client.getModelManager().getBlockStateModelSet();
        FluidRenderer fluidRenderer = new FluidRenderer(client.getModelManager().getFluidStateModelSet());
        ModelBlockRenderer blockRenderer = new ModelBlockRenderer(client.options.ambientOcclusion().get(), true, client.getBlockColors());

        List<Integer> animationCompletionTimings = new LinkedList<>();
        Map<BlockPos, BlockEntity> blockEntities = new ConcurrentHashMap<>();

        PoseStack poseStack = new PoseStack();
        HashMap<ChunkSectionLayer, BufferBuilder> startedLayers = new HashMap<>();
        SectionBufferBuilderPack builders = new SectionBufferBuilderPack();

        BlockQuadOutput quadOutput = (x, y, z, quad, instance) -> {
            BufferBuilder builder = mesh.getOrBeginLayer(startedLayers, builders, quad.materialInfo().layer());
            builder.putBlockBakedQuad(x, y, z, quad, instance);
        };
        BlockQuadOutput opaqueQuadOutput = (x, y, z, quad, instance) -> {
            BufferBuilder builder = mesh.getOrBeginLayer(startedLayers, builders, ChunkSectionLayer.SOLID);
            builder.putBlockBakedQuad(x, y, z, quad, instance);
        };

        BlockModelLighter.enableCaching();
        List<Iterable<BlockPos>> blockPositionsForBuilding = mesh.bounds.buildBlockPositionsForSubMesh(getFrom(), getTo());
        for (Iterable<BlockPos> positions : blockPositionsForBuilding) {
            if (mesh.buildingCancelled) {
                builders.discardAll();
                setNotDirty();
                return;
            }

            for (BlockPos pos : positions) {

                BlockState blockState = mesh.world.getBlockState(pos);
                if (blockState.isAir()) continue;
                if (blockState.is(Blocks.LIGHT)) continue; // axiom fix

                if (mesh.world.getBlockEntity(pos) != null) {
                    blockEntities.put(new BlockPos(pos.getX(), pos.getY(), pos.getZ()), mesh.world.getBlockEntity(pos));
                }

                FluidState fluidState = mesh.world.getFluidState(pos);
                if (!fluidState.isEmpty()) {

                    poseStack.pushPose();
                    poseStack.translate(
                            sectionRelative(pos.getX()) - (pos.getX() & 15),
                            sectionRelative(pos.getY()) - (pos.getY() & 15),
                            sectionRelative(pos.getZ()) - (pos.getZ() & 15)
                    );
                    //FluidVertexConsumer is used because insert pos into tesselarate will break what it thinks the water looks like (height/adjacent blocks and whatnot)
                    FluidRenderer.Output fluidOutput = l -> new FluidVertexConsumer(mesh.getOrBeginLayer(startedLayers, builders, l), poseStack.last().pose(), poseStack.last().normal());
                    fluidRenderer.tesselate(mesh.world, pos, fluidOutput, blockState, fluidState);
                    poseStack.popPose();
                }

                if (blockState.getRenderShape() == RenderShape.MODEL) {
                    BlockStateModel model = blockModelSet.get(blockState);
                    long randomSeed = blockState.getSeed(pos);
                    AnimationTimingUtil.scanTicksToFullyAnimateBlock(model, animationCompletionTimings, randomSeed);

                    BlockQuadOutput output = ModelBlockRenderer.forceOpaque(cutoutLeaves, blockState) ? opaqueQuadOutput : quadOutput;
                    blockRenderer.tesselateBlock(output, sectionRelative(pos.getX()), sectionRelative(pos.getY()), sectionRelative(pos.getZ()), mesh.world, pos, blockState, model, randomSeed);
                }
            }
        }

        if (mesh.buildingCancelled) {
            builders.discardAll();
            setNotDirty();
            return;
        }

        // based on SectionRenderDispatcher.RenderSection.RebuildTask#doTask (which calls compile)
        SectionCompiler.Results results = new SectionCompiler.Results();
        startedLayers.forEach((layer, bufferBuilder) -> {
            MeshData builtMesh = bufferBuilder.build();
            if (builtMesh != null) {
                if (layer == ChunkSectionLayer.TRANSLUCENT && mesh.orthographicTransparencySorting != null) {
                    results.transparencyState = builtMesh.sortQuads(builders.buffer(layer), mesh.orthographicTransparencySorting);
                }
                results.renderedLayers.put(layer, builtMesh);
            }
        });

        if (mesh.buildingCancelled) {
            results.release();
            setNotDirty();
            return;
        }

        client.execute((() -> {
            if (mesh.buildingCancelled) {
                results.release();
                setNotDirty();
                return;
            }

            if (results.renderedLayers.isEmpty()) {
                reset();
                setNotDirty();
                this.blockEntities = blockEntities;
                return;
            }

            CompiledSectionMesh compiledSectionMesh = new CompiledSectionMesh(null, results, 0);

            for (Map.Entry<ChunkSectionLayer, MeshData> entry : results.renderedLayers.entrySet()) {
                MeshData meshData = entry.getValue();
                boolean success = false;
                while (!success) {
                    success = ((RenderSectionInvoker) this).wikirenderer$addSectionBuffersToUberBuffer(entry.getKey(), compiledSectionMesh, meshData.vertexBuffer(), meshData.indexBuffer());
                    if (!success && !RenderSystem.isOnRenderThread()) {
                        Thread.onSpinWait();
                    }
                }

                meshData.close();
            }

            this.animationCompletionTimings = animationCompletionTimings;
            this.blockEntities = blockEntities;
            setNotDirty();
        }));

        BlockModelLighter.clearCache();
    }

    public void setNotDirty() {
        isDirty = false;
        isBuilding = false;
        forceUpdate = false;
        buildAttempted = true;
    }

    public void setDirty(boolean force) {
        isDirty = true;
        forceUpdate = force;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public boolean isForceUpdate() {
        return forceUpdate;
    }

    @Override
    public void reset() {
        super.reset();
        if (blockEntities != null) {
            blockEntities.clear();
        }
        if (animationCompletionTimings != null) {
            animationCompletionTimings.clear();
        }
    }

    public void markBuildNotAttempted() {
        buildAttempted = false;
    }

    public boolean hasBuildBeenAttempted() {
        return buildAttempted;
    }

    public void reSortTransparencyAndSubmit(WorldBlockMesh mesh) {
        if (!(this.getSectionMesh() instanceof CompiledSectionMesh compiledSectionMesh)) {
            return;
        }

        MeshData.SortState state = compiledSectionMesh.getTransparencyState();
        if (state != null && !compiledSectionMesh.isEmpty(ChunkSectionLayer.TRANSLUCENT)) {

            ByteBufferBuilder.Result indexBuffer = state.buildSortedIndexBuffer(mesh.resortBufferPack.buffer(ChunkSectionLayer.TRANSLUCENT), mesh.orthographicTransparencySorting);
            if (indexBuffer == null) {
                return;
            }

            boolean success = false;
            while (!success) {
                success = ((RenderSectionInvoker) this).wikirenderer$addSectionBuffersToUberBuffer(ChunkSectionLayer.TRANSLUCENT, compiledSectionMesh, null, indexBuffer.byteBuffer());
                if (!success && !RenderSystem.isOnRenderThread()) {
                    Thread.onSpinWait();
                }
            }

            indexBuffer.close();
        }
    }

    public static long getSectionIndex(int sectionX, int sectionY, int sectionZ) {
        return SectionPos.asLong(sectionX, sectionY, sectionZ);
    }

    public static int getSection(int block) {
        return block >> 5;
    }

    public static int sectionToBlockCoordinate(int sectionCoordinate) {
        return sectionCoordinate << 5;
    }

    public static int sectionRelative(int blockCoord) {
        return blockCoord & 31;
    }
}
