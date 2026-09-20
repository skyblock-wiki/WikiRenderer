package com.pigicial.wikirenderer.render.area;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.render.OrthographicSort;
import com.pigicial.wikirenderer.render.area.bounds.MeshBounds;
import com.pigicial.wikirenderer.render.area.side_view.WalkabilityFilter;
import com.pigicial.wikirenderer.util.compatibility.EntityCullingCheck;
import com.pigicial.wikirenderer.util.compatibility.ShaderCheck;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// todo: not a fan of how entities are handled in AreaRenderable and blocks are here, maybe they should be merged
public class WorldBlockMesh {

    private static SectionRenderDispatcher sectionRenderDispatcher;
    public static boolean overrideTerrainTransparencyRenderPipelines = false;
    public static GpuSampler terrainSampler = null;

    public final MeshWorldOverrides world;
    public final MeshBounds bounds;
    private AreaRenderable renderable;

    protected final SectionBufferBuilderPack resortBufferPack = new SectionBufferBuilderPack();
    public final Map<Long, MeshRenderSection> subMeshes = new ConcurrentHashMap<>();

    private MeshState state = MeshState.NEW;
    protected OrthographicSort orthographicTransparencySorting = null;
    private volatile CompletableFuture<Void> sortFuture = null;

    private boolean currentlyFullyBuilding = false; // initial build or rebuilds, not automatic ones from chunk updates
    private boolean currentlyUpdatingWalkabilityFilter = false;
    public boolean lastUpdateUsesWalkabilityFilter = false;

    private float fullBuildProgress = 0;
    protected volatile boolean buildingCancelled = false;
    private boolean isAutomaticUpdateScheduled = false;

    private float lastUsedRotation;
    private double lastUsedSlant;

    public WorldBlockMesh(
            ClientLevel world,
            MeshBounds bounds
    ) {
        this.bounds = bounds;
        this.world = new MeshWorldOverrides(world, bounds);
        this.lastUsedRotation = Float.MAX_VALUE;
        this.lastUsedSlant = Double.MAX_VALUE;

        Minecraft client = Minecraft.getInstance();
        if (sectionRenderDispatcher == null) {
            sectionRenderDispatcher = new SectionRenderDispatcher(Util.backgroundExecutor(), client.gameRenderer.renderBuffers(), null, s -> {});
        }
    }

    public void setRenderable(AreaRenderable renderable) {
        this.renderable = renderable;
    }

    public MeshState getMeshState() {
        return this.state;
    }

    public float getBuildProgress() {
        return this.fullBuildProgress;
    }

    public boolean canRebuild() {
        return !currentlyFullyBuilding;
    }

    public ChunkSectionsToRender getBlocksToRender(PoseStack matrices) {
        if (!this.getMeshState().canRender) {
            throw new IllegalStateException("World mesh not prepared!");
        }

        this.updateBuildingStatus();
        this.updateOutdatedMeshSections();

        if (terrainSampler == null) {
            Options options = Minecraft.getInstance().options;
            int maxAnisotropy = options.textureFiltering().get() == TextureFilteringMethod.ANISOTROPIC ? options.maxAnisotropyValue() : 1;
            terrainSampler = RenderSystem.getDevice().createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, maxAnisotropy, OptionalDouble.empty());
        }

        if (WikiRenderer.orthographicSorting != null) {
            this.orthographicTransparencySorting = WikiRenderer.orthographicSorting;
        }

        float currentRotation = AreaPropertyBundle.INSTANCE.getUsedRotation();
        double currentSlant = AreaPropertyBundle.INSTANCE.getUsedSlant();
        if ((this.lastUsedRotation != currentRotation || this.lastUsedSlant != currentSlant) && this.orthographicTransparencySorting != null) {
            boolean isProbablyLargeSpinningObjectShrunkenDown = renderable.getProperties().scale.get() <= 15 && renderable.getProperties().rotationSpeed.get() > 0;
            if (!isProbablyLargeSpinningObjectShrunkenDown) {
                // anything smaller than 15 you probably wont see transparency issues (i.e. rendering the skyblock hub)
                this.lastUsedRotation = currentRotation;
                this.lastUsedSlant = currentSlant;
                scheduleReSort();
            }
        }

        ChunkSectionsToRender sections;
        if (Minecraft.getInstance().levelRenderer.isChunkRenderingUsingMultiDrawIndirect()) {
            sections = this.prepareChunkRendersIndirect(matrices.last().pose(), !Minecraft.getInstance().gameRenderer.useImprovedTransparency());
        } else {
            sections = this.prepareChunkRenders(matrices.last().pose(), !Minecraft.getInstance().gameRenderer.useImprovedTransparency());
        }

        return sections;
    }

    public void drawItAll(@Nullable ChunkSectionsToRender blocksToRender) {
        RenderTarget mainTarget = WikiRenderer.mainTargetOverride == null ? Minecraft.getInstance().gameRenderer.mainRenderTarget() : WikiRenderer.mainTargetOverride;

        // based on LevelRenderer#addMainPass
        SubmitNodeStorage submitNodeStorage = WikiRenderer.NODE_STORAGE;
        FeatureRenderDispatcher featureRenderDispatcher = Minecraft.getInstance().gameRenderer.featureRenderDispatcher();
        try (FeatureRenderDispatcher.PreparedFrame frame = featureRenderDispatcher.prepareFrame(submitNodeStorage)) {
            try (RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(() -> "Mesh Main", mainTarget.getColorTextureView(), Optional.empty(), mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(renderPass);

                GpuTextureView blockAtlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
                if (blocksToRender != null) {
                    blocksToRender.renderGroup(ChunkSectionLayerGroup.OPAQUE, renderPass, terrainSampler, blockAtlas, false);
                }

                frame.executeSolid(renderPass);

                frame.executeTranslucent(renderPass); // this requires non-OIT, which is why there's WikiRenderer.NODE_STORAGE (defaults to non-oit)
                if (blocksToRender != null) {
                    blocksToRender.renderGroup(ChunkSectionLayerGroup.TRANSLUCENT, renderPass, terrainSampler, blockAtlas, false);
                }
                frame.executeTranslucentAfterTerrain(renderPass);
            }

            if (frame.hasAnySeeThrough()) {
                RenderPassDescriptor descriptor = RenderPassDescriptor.builder(() -> "Mesh see through features").withColorAttachment(mainTarget.getColorTextureView()).build();

                try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor)) {
                    RenderSystem.bindDefaultUniforms(renderPass);
                    frame.executeSeeThrough(renderPass);
                }
            }
        }

        sectionRenderDispatcher.lock();
        try {
            sectionRenderDispatcher.uploadTerrainBuffersToGpu();
        } finally {
            sectionRenderDispatcher.unlock();
        }
    }

    @Environment(EnvType.CLIENT)
    private record ChunkDrawGroup(
            GpuBufferSlice vertexBuffer, @Nullable GpuBufferSlice indexBuffer,
            @Nullable IndexType indexType, List<DynamicGpuData.IndexedDraw> draws) {
    }

    private int extractSectionDrawGroups(
            final boolean respectTranslucentOrder,
            final List<DynamicGpuData.ChunkSectionInfo> sectionInfos,
            final Map<ChunkSectionLayer, List<ChunkDrawGroup>> drawGroups
    ) {
        int largestIndexCount = 0;
        Int2ObjectOpenHashMap<ChunkDrawGroup> drawGroupCache = new Int2ObjectOpenHashMap<>();

        List<MeshRenderSection> sortedSections = new ArrayList<>(subMeshes.values());

        if (orthographicTransparencySorting instanceof OrthographicSort orthoSort) {
            sortedSections.sort(Comparator.comparingDouble(s -> {
                BlockPos from = s.getFrom();
                BlockPos to = s.getTo();
                float cx = (from.getX() + to.getX()) / 2f;
                float cy = (from.getY() + to.getY()) / 2f;
                float cz = (from.getZ() + to.getZ()) / 2f;
                return orthoSort.projectDepth(cx, cy, cz);
            }));
        }

        if (sectionRenderDispatcher != null) {
            sectionRenderDispatcher.lock();
            int lastTransparentGroupHash = 0;

            try {
                for (SectionRenderDispatcher.RenderSection section : sortedSections) {
                    SectionMesh sectionMesh = section.getSectionMesh();
                    int sectionInfoDataIndex = -1;

                    for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                        SectionMesh.SectionDraw draw = sectionMesh.getSectionDraw(layer);
                        SectionRenderDispatcher.RenderSectionBufferSlice slice = sectionRenderDispatcher.getRenderSectionSlice(sectionMesh, layer);
                        if (slice != null && draw != null && (!draw.hasCustomIndexBuffer() || slice.indexBuffer() != null)) {
                            if (sectionInfoDataIndex == -1) {
                                sectionInfoDataIndex = sectionInfos.size();
                                sectionInfos.add(new DynamicGpuData.ChunkSectionInfo(0, 0, 0, 1.0f));
                            }

                            int combinedHash = 173;
                            VertexFormat vertexFormat = layer.pipeline(false).getVertexFormatBinding(0);
                            GpuBuffer vertexBuffer = slice.vertexBuffer();
                            combinedHash = 31 * combinedHash + vertexBuffer.hashCode();
                            int firstIndex = 0;
                            GpuBuffer indexBuffer;
                            IndexType indexType;
                            if (!draw.hasCustomIndexBuffer()) {
                                if (draw.indexCount() > largestIndexCount) {
                                    largestIndexCount = draw.indexCount();
                                }

                                indexBuffer = null;
                                indexType = null;
                            } else {
                                indexBuffer = slice.indexBuffer();
                                indexType = draw.indexType();
                                combinedHash = 31 * combinedHash + indexBuffer.hashCode();
                                combinedHash = 31 * combinedHash + indexType.hashCode();
                                firstIndex = (int) (slice.indexBufferOffset() / indexType.bytes);
                            }

                            int baseVertex = (int) (slice.vertexBufferOffset() / vertexFormat.getVertexSize());
                            ChunkDrawGroup drawGroup = null;
                            if (layer.translucent() && respectTranslucentOrder) {
                                if (combinedHash == lastTransparentGroupHash) {
                                    drawGroup = drawGroups.get(layer).getLast();
                                }

                                lastTransparentGroupHash = combinedHash;
                            } else {
                                drawGroup = drawGroupCache.getOrDefault(combinedHash, null);
                            }

                            if (drawGroup == null) {
                                drawGroup = new ChunkDrawGroup(vertexBuffer.slice(), indexBuffer != null ? indexBuffer.slice() : null, indexType, new ArrayList<>());
                                drawGroupCache.put(combinedHash, drawGroup);
                                drawGroups.get(layer).add(drawGroup);
                            }

                            drawGroup.draws.add(new DynamicGpuData.IndexedDraw(draw.indexCount(), 1, firstIndex, baseVertex, sectionInfoDataIndex));
                        }
                    }
                }
            } finally {
                sectionRenderDispatcher.unlock();
            }
        }

        return largestIndexCount;
    }

    public ChunkSectionsToRender prepareChunkRenders(final Matrix4fc modelViewMatrix, final boolean respectTranslucentOrder) {
        Map<ChunkSectionLayer, List<ChunkDrawGroup>> drawGroups = Util.makeEnumMap(ChunkSectionLayer.class, _ -> new ReferenceArrayList<>());
        List<DynamicGpuData.ChunkSectionInfo> sectionInfos = new ArrayList<>();

        GpuTextureView blockAtlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        int textureAtlasWidth = blockAtlas.getWidth(0);
        int textureAtlasHeight = blockAtlas.getHeight(0);

        int largestIndexCount = this.extractSectionDrawGroups(respectTranslucentOrder, sectionInfos, drawGroups);
        Map<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> flattenDraws = Util.makeEnumMap(ChunkSectionLayer.class, _ -> new ReferenceArrayList<>());

        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            List<ChunkDrawGroup> sortedDrawGroups = drawGroups.get(layer);
            if (layer.translucent() && respectTranslucentOrder) {
                sortedDrawGroups = sortedDrawGroups.reversed();
            }

            for (ChunkDrawGroup drawGroup : sortedDrawGroups) {
                List<RenderPass.Draw<GpuBufferSlice[]>> dest = flattenDraws.get(layer);
                List<DynamicGpuData.IndexedDraw> sortedDraws = drawGroup.draws;
                if (layer.translucent() && respectTranslucentOrder) {
                    sortedDraws = sortedDraws.reversed();
                }

                for (DynamicGpuData.IndexedDraw draw : sortedDraws) {
                    int sectionInfoDataIndex = draw.baseInstance();
                    GpuBuffer indexBuffer = drawGroup.indexBuffer == null ? null : drawGroup.indexBuffer().buffer();
                    dest.add(
                            new RenderPass.Draw<>(
                                    0,
                                    drawGroup.vertexBuffer.buffer(),
                                    indexBuffer,
                                    drawGroup.indexType,
                                    draw.firstIndex(),
                                    draw.indexCount(),
                                    draw.baseVertex(),
                                    (sectionUbos, uploader) -> uploader.setUniform("ChunkSection", sectionUbos[sectionInfoDataIndex])
                            )
                    );
                }
            }
        }

        GpuBufferSlice terrainTransformUbo = RenderSystem.getDynamicUniforms().writeTerrainTransform(modelViewMatrix, textureAtlasWidth, textureAtlasHeight);
        GpuBufferSlice[] chunkSectionInfos = RenderSystem.getDynamicUniforms().writeChunkSections(sectionInfos.toArray(new DynamicGpuData.ChunkSectionInfo[0]));
        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        if (largestIndexCount != 0) {
            autoIndices.requestIndexCount(largestIndexCount);
        }

        return new ChunkSectionsToRender.DrawSeparate(terrainTransformUbo, flattenDraws, largestIndexCount, chunkSectionInfos);
    }

    public ChunkSectionsToRender prepareChunkRendersIndirect(final Matrix4fc modelViewMatrix, final boolean respectTranslucentOrder) {
        EnumMap<ChunkSectionLayer, List<ChunkDrawGroup>> drawGroups = new EnumMap<>(ChunkSectionLayer.class);

        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            drawGroups.put(layer, new ReferenceArrayList<>());
        }

        List<DynamicGpuData.ChunkSectionInfo> sectionInfos = new ArrayList<>();
        GpuTextureView blockAtlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        int textureAtlasWidth = blockAtlas.getWidth(0);
        int textureAtlasHeight = blockAtlas.getHeight(0);
        int largestIndexCount = this.extractSectionDrawGroups(respectTranslucentOrder, sectionInfos, drawGroups);
        EnumMap<ChunkSectionLayer, List<ChunkSectionsToRender.GpuMultiDrawIndexedIndirect>> indirectDraws = new EnumMap<>(ChunkSectionLayer.class);

        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            indirectDraws.put(layer, new ArrayList<>());
        }

        List<List<DynamicGpuData.IndexedDraw>> batchedDraws = new ArrayList<>();

        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            List<ChunkDrawGroup> sortedDrawGroups = drawGroups.get(layer);
            if (layer.translucent() && respectTranslucentOrder) {
                sortedDrawGroups = sortedDrawGroups.reversed();
            }

            for (ChunkDrawGroup chunkDrawGroup : sortedDrawGroups) {
                List<DynamicGpuData.IndexedDraw> sortedDraws = chunkDrawGroup.draws;
                if (layer.translucent() && respectTranslucentOrder) {
                    sortedDraws = sortedDraws.reversed();
                }

                batchedDraws.add(sortedDraws);
            }
        }

        GpuBufferSlice[] indirectBufferSlices = RenderSystem.getDynamicUniforms().writeChunkSectionCommands(batchedDraws);
        int index = 0;

        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            List<ChunkDrawGroup> sortedDrawGroups = drawGroups.get(layer);
            if (layer.translucent() && respectTranslucentOrder) {
                sortedDrawGroups = sortedDrawGroups.reversed();
            }

            for (ChunkDrawGroup chunkDrawGroup : sortedDrawGroups) {
                List<DynamicGpuData.IndexedDraw> draws = chunkDrawGroup.draws;
                GpuBufferSlice indirectBuffer = indirectBufferSlices[index++];
                indirectDraws.get(layer).add(new ChunkSectionsToRender.GpuMultiDrawIndexedIndirect(
                        chunkDrawGroup.vertexBuffer, chunkDrawGroup.indexBuffer, chunkDrawGroup.indexType, indirectBuffer, draws.size()
                ));
            }
        }

        GpuBufferSlice terrainTransformUbo = RenderSystem.getDynamicUniforms().writeTerrainTransform(modelViewMatrix, textureAtlasWidth, textureAtlasHeight);
        GpuBufferSlice chunkSectionInfos = RenderSystem.getDynamicUniforms().writeChunkSectionsInstanced(sectionInfos);
        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        if (largestIndexCount != 0) {
            autoIndices.requestIndexCount(largestIndexCount);
        }

        return new ChunkSectionsToRender.DrawIndirect(terrainTransformUbo, indirectDraws, largestIndexCount, chunkSectionInfos);
    }

    public void submitBlockEntities(PoseStack standardStack, SubmitNodeStorage nodeStorage, CameraRenderState cameraRenderState, float tickDelta) {
        BlockPos minCorner = bounds.getMinCorner();
        standardStack.pushPose();
        standardStack.translate(-minCorner.getX(), -minCorner.getY(), -minCorner.getZ());

        EntityCullingCheck.disableBlockEntityCullingIfPossible();
        BlockEntityRenderDispatcher blockEntityDispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        for (MeshRenderSection renderSection : this.subMeshes.values()) {
            renderSection.blockEntities.forEach((blockPos, entity) -> {
                SortedSet<BlockDestructionProgress> progresses = this.world.getDelegate().destructionProgress().get(blockPos.asLong());

                ModelFeatureRenderer.CrumblingOverlay breakProgress;
                if (progresses != null && !progresses.isEmpty()) { // this is nullable, idk why intellij thinks it's not
                    standardStack.pushPose();
                    standardStack.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());
                    breakProgress = new ModelFeatureRenderer.CrumblingOverlay(progresses.last().getProgress(), standardStack.last());
                    standardStack.popPose();
                } else {
                    breakProgress = null;
                }

                BlockEntityRenderState state = blockEntityDispatcher.tryExtractRenderState(entity, tickDelta, breakProgress, true);
                if (state == null) {
                    state = blockEntityDispatcher.tryExtractRenderState(entity, tickDelta, breakProgress, false);
                }

                if (state instanceof BeaconRenderState && AreaPropertyBundle.INSTANCE.hideBeaconBeams.get()) {
                    return;
                }

                standardStack.pushPose();
                standardStack.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());

                if (state != null) {
                    blockEntityDispatcher.submit(state, standardStack, nodeStorage, cameraRenderState);
                }

                standardStack.popPose();
            });
        }

        standardStack.popPose();
        EntityCullingCheck.reEnableBlockEntityCullingIfNecessary();
    }

    public synchronized void scheduleRebuild(boolean async) {
        if (currentlyFullyBuilding || currentlyUpdatingWalkabilityFilter) return;

        this.lastUpdateUsesWalkabilityFilter = AreaPropertyBundle.INSTANCE.useWalkabilityFilter.get();
        this.fullBuildProgress = 0;
        this.state = this.state != MeshState.NEW
                ? MeshState.REBUILDING
                : MeshState.BUILDING;

        this.subMeshes.values().forEach(section -> {
            section.reset();
            section.markBuildNotAttempted();
        });
        this.subMeshes.clear();

        this.orthographicTransparencySorting = WikiRenderer.orthographicSorting;

        this.currentlyFullyBuilding = true;
        if (ShaderCheck.isUsingShaders() || !async) {
            this.compileMesh();
        } else {
            CompletableFuture.runAsync(this::compileMesh);
        }
    }

    public synchronized void stopBuilding() {
        this.buildingCancelled = true;
        this.state = MeshState.CANCELLED;
    }

    public void setDirty(int sectionX, int sectionY, int sectionZ, boolean force) {
        long sectionIndex = MeshRenderSection.getSectionIndex(sectionX, sectionY, sectionZ);
        MeshRenderSection renderSection = this.subMeshes.get(sectionIndex);

        if (renderSection == null && force) {
            renderSection = createRenderSection(sectionX, sectionY, sectionZ);
            this.subMeshes.put(sectionIndex, renderSection);
        }

        if (renderSection != null) {
            renderSection.setDirty(force);
        }
    }

    private void updateBuildingStatus() {
        int amountBuilt = 0;
        for (MeshRenderSection sections : this.subMeshes.values()) {
            if (sections.hasBuildBeenAttempted()) amountBuilt++;
        }

        if (amountBuilt == this.subMeshes.size()) {
            currentlyFullyBuilding = false;
            if (buildingCancelled) {
                state = MeshState.CANCELLED;
                buildingCancelled = false;
                return;
            }

            if (state == MeshState.CANCELLED) {
                return;
            }

            buildingCancelled = false;
            state = MeshState.READY;
        }

        this.fullBuildProgress = (float) amountBuilt / this.subMeshes.size();
    }

    private void updateOutdatedMeshSections() {
        if (isAutomaticUpdateScheduled || currentlyFullyBuilding || currentlyUpdatingWalkabilityFilter) return;

        boolean allowAutoUpdate = !renderable.getProperties().freezeBlocks.get();
        boolean hasDirty = subMeshes.values().stream().anyMatch(section -> section.isDirty() && (allowAutoUpdate || section.isForceUpdate()));
        if (!hasDirty) return;

        isAutomaticUpdateScheduled = true;
        if (ShaderCheck.isUsingShaders()) {
            this.compileOutdatedMeshSections();
            isAutomaticUpdateScheduled = false;
        } else {
            CompletableFuture.runAsync(() -> {
                try {
                    this.compileOutdatedMeshSections();
                } finally {
                    isAutomaticUpdateScheduled = false;
                }
            });
        }
    }

    private void compileOutdatedMeshSections() {
        if (this.currentlyFullyBuilding) return;
        boolean allowAutoUpdate = !renderable.getProperties().freezeBlocks.get();

        for (MeshRenderSection section : this.subMeshes.values()) {
            if (section.isDirty() && (allowAutoUpdate || section.isForceUpdate())) {
                section.buildAndSubmit(this);
            }
        }
    }

    // Based on SectionCompiler#compile and then SectionRenderDispatcher.RenderSection.RebuildTask#doTask (which calls compile)
    private void compileMesh() {
        if (buildingCancelled) {
            currentlyFullyBuilding = false;
            return;
        }
        currentlyFullyBuilding = true;

        List<MeshRenderSection> unsortedSubMeshes = new ArrayList<>();

        BlockPos minCorner = this.bounds.getMinCorner();
        BlockPos maxCorner = this.bounds.getMaxCorner();

        int minSectionX = MeshRenderSection.getSection(minCorner.getX());
        int minSectionY = MeshRenderSection.getSection(minCorner.getY());
        int minSectionZ = MeshRenderSection.getSection(minCorner.getZ());
        int maxSectionX = MeshRenderSection.getSection(maxCorner.getX());
        int maxSectionY = MeshRenderSection.getSection(maxCorner.getY());
        int maxSectionZ = MeshRenderSection.getSection(maxCorner.getZ());

        for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX += 1) {
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY += 1) {
                for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ += 1) {
                    long sectionIndex = MeshRenderSection.getSectionIndex(sectionX, sectionY, sectionZ);
                    int finalSectionX = sectionX;
                    int finalSectionY = sectionY;
                    int finalSectionZ = sectionZ;

                    MeshRenderSection section = this.subMeshes.computeIfAbsent(sectionIndex, _ -> createRenderSection(finalSectionX, finalSectionY, finalSectionZ));
                    unsortedSubMeshes.add(section);
                }
            }
        }

        List<MeshRenderSection> subMeshes = unsortedSubMeshes.stream().sorted(Comparator.comparing(MeshRenderSection::getDistanceFromCenter)).toList();

        if (buildingCancelled) {
            currentlyFullyBuilding = false;
            return;
        }

        this.refreshWalkabilityFilter();

        BlockModelLighter.enableCaching();
        for (MeshRenderSection renderSection : subMeshes) {
            renderSection.buildAndSubmit(this);
        }
        BlockModelLighter.clearCache();
    }

    protected void refreshWalkabilityFilter() {
        this.currentlyUpdatingWalkabilityFilter = true;
        this.world.setWalkabilityFilter(null);
        WalkabilityFilter walkabilityFilter = null;
        AreaPropertyBundle properties = AreaPropertyBundle.INSTANCE;
        if (properties.perPixel90DegreeRendering.get()) {
            if (properties.useWalkabilityFilter.get()) {
                walkabilityFilter = new WalkabilityFilter(this, renderable);
                walkabilityFilter.cacheData();
            }
        }
        this.world.setWalkabilityFilter(walkabilityFilter);
        this.currentlyUpdatingWalkabilityFilter = false;
    }

    private MeshRenderSection createRenderSection(int sectionX, int sectionY, int sectionZ) {
        BlockPos minCorner = this.bounds.getMinCorner();
        BlockPos maxCorner = this.bounds.getMaxCorner();
        int middleX = maxCorner.getX() - (maxCorner.getX() - minCorner.getX()) / 2;
        int middleZ = maxCorner.getZ() - (maxCorner.getZ() - minCorner.getZ()) / 2;

        MeshRenderSection section = new MeshRenderSection(this.sectionRenderDispatcher, sectionX, sectionY, sectionZ);

        BlockPos from = section.getFrom();
        BlockPos to = section.getTo();

        int subMiddleX = to.getX() - (to.getX() - from.getX()) / 2;
        int subMiddleZ = to.getZ() - (to.getZ() - from.getZ()) / 2;
        double distance = Math.pow(middleX - subMiddleX, 2) + Math.pow(middleZ - subMiddleZ, 2);

        section.setDistanceFromCenter(distance);

        return section;
    }

    private synchronized void scheduleReSort() {
        if (this.sortFuture != null && !this.sortFuture.isDone()) return;

        this.orthographicTransparencySorting = WikiRenderer.orthographicSorting;
        if (ShaderCheck.isUsingShaders()) {
            this.sortFuture = CompletableFuture.completedFuture(null);
            this.reSortMeshSections();
            this.sortFuture = null;
        } else {
            this.sortFuture = CompletableFuture.runAsync(this::reSortMeshSections).whenComplete((_, _) -> this.sortFuture = null);
        }
    }

    // Based on SectionRenderDispatcher.RenderSection.ResortTransparencyTask#doTask
    private void reSortMeshSections() {
        for (MeshRenderSection section : subMeshes.values()) {
            section.reSortTransparencyAndSubmit(this);
        }
    }

    protected BufferBuilder getOrBeginLayer(Map<ChunkSectionLayer, BufferBuilder> startedLayers, SectionBufferBuilderPack buffers, ChunkSectionLayer layer) {
        return startedLayers.computeIfAbsent(layer, _ -> new BufferBuilder(buffers.buffer(layer), PrimitiveTopology.QUADS, layer.vertexFormat()));
    }

    public Optional<List<Integer>> getAnimationCompletionTimings() {
        List<Integer> timings = new ArrayList<>();
        for (MeshRenderSection section : subMeshes.values()) {
            timings.addAll(section.animationCompletionTimings);
        }
        return timings.isEmpty() ? Optional.empty() : Optional.of(timings);
    }

    public void dispose() {
        subMeshes.values().forEach(MeshRenderSection::reset);
        subMeshes.clear();
        resortBufferPack.close();
    }

    public enum MeshState {
        NEW(false, false),
        CANCELLED(true, true),
        BUILDING(true, true),
        REBUILDING(true, true),
        READY(false, true),
        CORRUPT(false, false);

        public final boolean isBuildStage;
        public final boolean canRender;

        MeshState(boolean buildStage, boolean canRender) {
            this.isBuildStage = buildStage;
            this.canRender = canRender;
        }
    }
}
