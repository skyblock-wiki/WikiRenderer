package com.pigicial.wikirenderer.render.area;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.context.CommandContext;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.mixin.access.GameRendererAccessor;
import com.pigicial.wikirenderer.mixin.access.ItemStackRenderStateAccessor;
import com.pigicial.wikirenderer.property.GlobalProperties;
import com.pigicial.wikirenderer.property.IntProperty;
import com.pigicial.wikirenderer.render.CameraUtil;
import com.pigicial.wikirenderer.render.DefaultRenderable;
import com.pigicial.wikirenderer.render.area.bounds.ChunkScannedMeshBounds;
import com.pigicial.wikirenderer.render.area.bounds.MeshBounds;
import com.pigicial.wikirenderer.render.area.bounds.SingleCuboidMeshBounds;
import com.pigicial.wikirenderer.render.area.bounds.chunk.ChunkScanResult;
import com.pigicial.wikirenderer.render.area.bounds.chunk.HorizontalMiniChunk;
import com.pigicial.wikirenderer.render.area.bounds.chunk.MiniChunkScanner;
import com.pigicial.wikirenderer.render.entity.EntityCloner;
import com.pigicial.wikirenderer.render.entity.EntityRenderBoundsUtil;
import com.pigicial.wikirenderer.render.entity.EntityVertexBounds;
import com.pigicial.wikirenderer.render.entity.options.EntityTypeSpecificOverrides;
import com.pigicial.wikirenderer.render.export.ExportPathSpec;
import com.pigicial.wikirenderer.render.export.RenderableDispatcher;
import com.pigicial.wikirenderer.render.export.animation.AnimationHandler;
import com.pigicial.wikirenderer.render.item.AnimationTimingsProvider;
import com.pigicial.wikirenderer.render.particle.ParticleDisplayCondition;
import com.pigicial.wikirenderer.render.particle.ParticleRendererAndLooper;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.screen.components.EntityTypeSpecificPropertiesComponent;
import com.pigicial.wikirenderer.util.*;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AreaRenderable extends DefaultRenderable<AreaPropertyBundle> implements AnimationTimingsProvider {

    public static final Map<Integer, EntityTypeSpecificOverrides<?>> ENTITY_SPECIFIC_OVERRIDES_BY_ID = new HashMap<>();
    public static final Map<Integer, Integer> FAKE_TO_REAL_ENTITY_ID_MAP = new HashMap<>();

    private final Minecraft client = Minecraft.getInstance();

    public final IntProperty minFloorYLevelForOverhead;
    public final IntProperty maxFloorYLevelForOverhead;
    public final WorldBlockMesh mesh;

    protected List<Entity> entities = new ArrayList<>();
    private boolean entitiesFrozen = false;
    private boolean entitiesLoaded = false;

    private final Map<Integer, DrawEntityDataCache> drawnVertexBoundCache = new HashMap<>();
    protected final EntityTypeSpecificPropertiesComponent advancedPropertiesComponent;
    public @Nullable Integer selectedEntityId;
    public @Nullable EntityTypeSpecificOverrides<?> renderStateOverrides = null;

    private List<Integer> lastSeenEntityAnimationTimings = null;

    public AreaRenderable(WorldBlockMesh mesh) {
        this.mesh = mesh;

        AABB dimensions = mesh.bounds.buildBoundingBox();
        this.minFloorYLevelForOverhead = IntProperty.of((int) dimensions.minY, (int) dimensions.minY, (int) dimensions.maxY);
        this.maxFloorYLevelForOverhead = IntProperty.of((int) dimensions.maxY, (int) dimensions.minY, (int) dimensions.maxY);
        this.mesh.setRenderable(this);

        this.advancedPropertiesComponent = new EntityTypeSpecificPropertiesComponent(() -> selectedEntityId, () -> renderStateOverrides);
    }

    public static AreaRenderable of(BlockPos start, BlockPos end) {
        MeshBounds bounds = new SingleCuboidMeshBounds(start, end);
        WorldBlockMesh mesh = new WorldBlockMesh(Minecraft.getInstance().level, bounds);
        return new AreaRenderable(mesh);
    }

    @Nullable
    public static AreaRenderable of(CommandContext<FabricClientCommandSource> commandContext, BlockPos origin, int chunkSize, int scanLimit) {
        ClientLevel level = Minecraft.getInstance().level;
        assert level != null;

        ChunkScanResult scanResult = MiniChunkScanner.getConnectedChunks(level, origin, chunkSize, scanLimit);
        if (scanResult == null) {
            Translate.commandError(commandContext, "no_valid_chunks");
            return null;
        }

        Set<HorizontalMiniChunk> chunks = scanResult.chunks();
        if (chunks.isEmpty()) {
            Translate.commandError(commandContext, "no_valid_chunks");
            return null;
        }

        MeshBounds bounds = new ChunkScannedMeshBounds(scanResult);
        WorldBlockMesh mesh = new WorldBlockMesh(Minecraft.getInstance().level, bounds);
        return new AreaRenderable(mesh);
    }

    @Override
    public boolean usesWorldLightMap() {
        return true;
    }

    @Override
    public void emitVerticesThenDraw(RenderScreen renderScreen, Matrix4fStack modelViewStack, PoseStack standardStack, float tickDelta, long timeSinceCreationMs) {
        if (!mesh.getMeshState().canRender) {
            if (mesh.getMeshState() == WorldBlockMesh.MeshState.CORRUPT) return;

            mesh.scheduleRebuild(true);
            return;
        }

        WikiRenderer.inAreaRenderDraw = true;
        AreaPropertyBundle properties = getProperties();

        GlobalSettingsUniform globalSettings = ((GameRendererAccessor) Minecraft.getInstance().gameRenderer).wikirenderer$getGlobalSettingsUniform();
        ClientLevel level = Minecraft.getInstance().level;
        long shaderAnimationTicks = 0; // i.e. end portals
        if (GlobalProperties.get().tickTextureAnimations.get() && level != null) {
            shaderAnimationTicks = level.getGameTime();
            if (GlobalProperties.get().syncTextureAnimationsToAnimation.get()) {
                AnimationHandler animationHandler = WikiRenderer.currentAnimationHandler;
                if (animationHandler != null && !animationHandler.isFinished()) {
                    int totalFrameCount = animationHandler.getAnimationFrames();
                    int framesRenderedSoFar = totalFrameCount - animationHandler.getRemainingFrames();
                    int frameRate = GlobalProperties.get().exportFramerate.get();
                    double secondsIntoAnimation = (double) framesRenderedSoFar / (double) frameRate;
                    shaderAnimationTicks = (int) Math.floor(secondsIntoAnimation * 20);
                } else {
                    shaderAnimationTicks = 0;
                }
            }
        }

        globalSettings.update(
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight(),
                1.0,
                shaderAnimationTicks,
                client.getDeltaTracker().getGameTimeDeltaPartialTick(false), 0,
                new Vec3(0, 0, 0), // Passing a new/empty camera sets pos to 0,0,0
                false
        );

        AABB boundingBox = this.mesh.bounds.buildBoundingBox();
        double xSize = boundingBox.getXsize();
        double ySize = boundingBox.getYsize();
        double zSize = boundingBox.getZsize();

        SubmitNodeStorage nodeStorage = WikiRenderer.NODE_STORAGE;
        CameraRenderState cameraRenderState = CameraUtil.createRenderState(this);

        standardStack.setIdentity();
        standardStack.translate(-xSize / 2f, -ySize / 2f, -zSize / 2f);

        BlockPos minCorner = mesh.bounds.getMinCorner();
        if (client.player != null) {
            Camera camera = CameraUtil.getCamera();
            Vec3 cameraPosition = camera != null ? camera.position() : client.player.getEyePosition();
            Vec3 particleOffset = Vec3.atLowerCornerOf(minCorner).subtract(cameraPosition);

            PoseStack particleStack = new PoseStack();
            particleStack.mulPose(standardStack.last().pose());

            particleStack.translate(-particleOffset.x, -particleOffset.y, -particleOffset.z);

            // this calls a new render pass / feature frame, do it early
            ParticleRendererAndLooper.submitAndDrawParticles(this, particleStack.last().pose(), tickDelta);
        }

        if (!properties.hideMesh.get()) {
            this.mesh.drawBlockEntities(standardStack, nodeStorage, cameraRenderState, tickDelta);
        }

        // do this here because the bounds calculation calls a render pass / feature frame, it has to be done before the render pass below is created
        this.refreshEntities();
        if (!properties.hideEntities.get()) {
            this.drawEntities(cameraRenderState, tickDelta, standardStack, nodeStorage);
        }

        if (!properties.hideMesh.get()) {
            PoseStack meshStack = new PoseStack();
            meshStack.mulPose(modelViewStack);
            meshStack.translate(-xSize / 2f, -ySize / 2f, -zSize / 2f);
            meshStack.translate(-minCorner.getX(), -minCorner.getY(), -minCorner.getZ());

            this.mesh.drawBlocks(meshStack);
        } else {
            this.drawSubmittedRenderFeatures();
        }

        WikiRenderer.inAreaRenderDraw = false;
    }

    private void refreshEntities() {
        if (this.getProperties().entityRefreshMode == AreaEntityRefreshMode.NOT_REFRESHING_AND_FROZEN && entitiesLoaded) {
            if (!this.entitiesFrozen) {
                this.entitiesFrozen = true;

                this.entities = this.entities
                        .stream()
                        .map(originalEntity -> {
                            Entity clonedEntity = EntityCloner.copy(originalEntity);
                            if (clonedEntity == null) return null;
                            FAKE_TO_REAL_ENTITY_ID_MAP.put(clonedEntity.getId(), originalEntity.getId());
                            return clonedEntity;
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
            return;
        }

        // not frozen selected by here
        if (getProperties().entityRefreshMode == AreaEntityRefreshMode.REFRESHING_WITHIN_BOUNDS || this.entitiesFrozen || !entitiesLoaded) {
            entitiesLoaded = true;

            ClientLevel level = Minecraft.getInstance().level;
            assert level != null;
            BlockPos start = mesh.bounds.getMinCorner();
            BlockPos end = mesh.bounds.getMaxCorner();
            AABB areaBoundingBox = AABB.encapsulatingFullBlocks(start, end);

            this.entities = level.getEntities((Entity) null, AABB.encapsulatingFullBlocks(start.offset(-8, -8, -8), end.offset(8, 8, 8)), entity -> {
                if (entity instanceof EnderDragonPart) return false; // crash fix

                EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(entity, 0);
                this.updateEntityState(entity, state);

                EntityVertexBounds entityVertexBounds = EntityRenderBoundsUtil.getPositionOffsetBasedBounds(entity, state, CameraUtil.createRenderState(this));
                AABB entityBounds = entityVertexBounds == null ? null : entityVertexBounds.getBounds();

                if (entityBounds != null && entityBounds.intersects(areaBoundingBox)) {
                    AABB intersection = entityBounds.intersect(areaBoundingBox);
                    double entityVolume = entityBounds.getXsize() * entityBounds.getYsize() * entityBounds.getZsize();
                    if (entityVolume == 0) return true; // fixes what seems to be single-vertex entities (like some item displays) not appearing from NaN math

                    double intersectionVolume = intersection.getXsize() * intersection.getYsize() * intersection.getZsize();
                    double intersectionPercentage = (intersectionVolume / entityVolume) * 100D;
                    return intersectionPercentage >= getProperties().entityBoundsIntersectionRequirement.get();
                }

                return false;
            });
        }

        this.entities.removeIf(Entity::isRemoved);
        this.entitiesFrozen = false;
    }

    private void drawEntities(CameraRenderState cameraRenderState, float delta, PoseStack standardStack, SubmitNodeStorage nodeStorage) {
        float tickDelta = entitiesFrozen ? 0 : delta;
        AreaPropertyBundle properties = this.getProperties();
        EntityRenderDispatcher entityDispatcher = client.getEntityRenderDispatcher();

        drawnVertexBoundCache.clear();

        WikiRenderer.currentWorldOverrides = mesh.world;

        List<Integer> animationTimingsToFill = new ArrayList<>();

        this.entities.forEach(entity -> {
            if (properties.hiddenEntityTypes.contains(entity.getType())) return;
            if (entity instanceof LivingEntity && properties.hideLivingEntities.get()) return;

            BlockPos meshStartPos = mesh.bounds.getMinCorner();
            Vec3 entityPosition = entity.getPosition(tickDelta);
            Vec3 offsetFromMesh = entityPosition.subtract(meshStartPos.getX(), meshStartPos.getY(), meshStartPos.getZ());

            int entityId = entity.getId();
            entityId = FAKE_TO_REAL_ENTITY_ID_MAP.getOrDefault(entityId, entityId);
            EntityTypeSpecificOverrides<?> renderStateOverrides = ENTITY_SPECIFIC_OVERRIDES_BY_ID.get(entityId);
            if (renderStateOverrides != null && renderStateOverrides.isInvisible()) return;

            EntityRenderState state = entityDispatcher.extractEntity(entity, entity.isRemoved() ? 0 : tickDelta);
            this.updateEntityState(entity, state);

            PoseStack clonedPose = new PoseStack();
            clonedPose.mulPose(standardStack.last().pose());
            drawnVertexBoundCache.put(entityId, new DrawEntityDataCache(state, offsetFromMesh, clonedPose, false));

            WikiRenderer.animationTimingDataRequestedToFill = animationTimingsToFill;
            entityDispatcher.submit(state, cameraRenderState, offsetFromMesh.x, offsetFromMesh.y, offsetFromMesh.z, standardStack, nodeStorage);
            WikiRenderer.animationTimingDataRequestedToFill = null;

        });

        this.lastSeenEntityAnimationTimings = animationTimingsToFill;
        WikiRenderer.animationTimingDataRequestedToFill = null;
        WikiRenderer.currentWorldOverrides = null;
    }

    private void updateEntityState(Entity entity, EntityRenderState state) {
        int entityId = entity.getId();
        EntityTypeSpecificOverrides<?> renderStateOverrides =  ENTITY_SPECIFIC_OVERRIDES_BY_ID.get(FAKE_TO_REAL_ENTITY_ID_MAP.getOrDefault(entityId, entityId));

        if (state instanceof DisplayEntityRenderState displayEntityRenderState) {
            displayEntityRenderState.cameraYRot = 180 + getProperties().getUsedRotation();
            displayEntityRenderState.cameraXRot = (float) getProperties().getUsedSlant();
        }

        AreaPropertyBundle properties = this.getProperties();
        if (properties.useFullBrightGamma.get() || properties.emulateDaylight.get()) {
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        } else {
            state.lightCoords = client.getEntityRenderDispatcher().getPackedLightCoords(entity, 0);
        }
        state.outlineColor = 0; // remove glow (doesn't render properly)

        if (this.entitiesFrozen && (state instanceof AvatarRenderState avatarRenderState)) {
            // fix weird cape behavior with frozen models - there might be a better way to do this but ehh this is fine for now
            avatarRenderState.capeFlap = 0;
            avatarRenderState.capeLean = 0;
            avatarRenderState.capeLean2 = 0;
            avatarRenderState.ageInTicks = 1; // 1 allows for an armor offset to fix z-fighting
        }

        if (properties.hideText.get()) {
            state.nameTag = null;
            state.nameTagAttachment = null;
        }

        if (properties.overrideEntityRotations.get()) {
            if (state instanceof LivingEntityRenderState livingEntityRenderState) {
                livingEntityRenderState.bodyRot = (properties.entityRotationOverride.get());
                livingEntityRenderState.xRot = properties.entityPitchOverride.get();
                livingEntityRenderState.yRot = properties.entityYawOverride.get();
            }
            if (state instanceof ArmorStandRenderState armorStandRenderState) {
                armorStandRenderState.headPose = new Rotations(properties.entityPitchOverride.get(), properties.entityYawOverride.get(), 0);
            }
        }

        // todo: de-dupe
        if (state instanceof AvatarRenderState avatarRenderState) {
            if (properties.useSteveSkinForEntities.get()) {
                avatarRenderState.skin = DefaultPlayerSkin.getDefaultSkin();
            }
            if (properties.forceSmallArmsForEntities.get()) {
                avatarRenderState.skin = avatarRenderState.skin.with(PlayerSkin.Patch.create(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(PlayerModelType.SLIM))
                );
            }

            if (properties.freezePlayerArms.get()) {
                avatarRenderState.ageInTicks = 1; // 1 allows for an armor offset to fix z-fighting
            }
        }

        if (state instanceof ArmedEntityRenderState armedEntityRenderState) {
            if (properties.hideHeldItemsForEntities.get()) {
                armedEntityRenderState.leftHandItemStack = ItemStack.EMPTY;
                armedEntityRenderState.rightHandItemStack = ItemStack.EMPTY;
                armedEntityRenderState.leftHandItemState.clear();
                armedEntityRenderState.rightHandItemState.clear();
                armedEntityRenderState.leftArmPose = HumanoidModel.ArmPose.EMPTY;
                armedEntityRenderState.rightArmPose = HumanoidModel.ArmPose.EMPTY;
            } else if (properties.hideEnchantmentsForEntities.get()) {
                for (ItemStackRenderState.LayerRenderState layer : ((ItemStackRenderStateAccessor) armedEntityRenderState.leftHandItemState).wikirenderer$getLayers()) {
                    layer.setFoilType(ItemStackRenderState.FoilType.NONE);
                }
                for (ItemStackRenderState.LayerRenderState layer : ((ItemStackRenderStateAccessor) armedEntityRenderState.rightHandItemState).wikirenderer$getLayers()) {
                    layer.setFoilType(ItemStackRenderState.FoilType.NONE);
                }
            }
        }

        if (state instanceof HumanoidRenderState humanoidRenderState) {
            if (properties.hideArmorForEntities.get()) {
                humanoidRenderState.headItem.clear();
                humanoidRenderState.wornHeadType = null;
                humanoidRenderState.headEquipment = ItemStack.EMPTY;
                humanoidRenderState.chestEquipment = ItemStack.EMPTY;
                humanoidRenderState.legsEquipment = ItemStack.EMPTY;
                humanoidRenderState.feetEquipment = ItemStack.EMPTY;
            } else if (properties.hideEnchantmentsForEntities.get()) {
                humanoidRenderState.headEquipment.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
                humanoidRenderState.chestEquipment.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
                humanoidRenderState.legsEquipment.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
                humanoidRenderState.feetEquipment.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            }
        }

        if (properties.toggleInvisibilityForEntities.get()) {
            state.isInvisible = true;
            if (state instanceof LivingEntityRenderState livingEntityRenderState) {
                livingEntityRenderState.isInvisibleToPlayer = true;
            }
        }

        if (!state.shadowPieces.isEmpty()) {
            if (properties.hideMesh.get()) {
                state.shadowPieces.clear();
            } else {
                // increase shadow height by a tiny amount to fix z-fighting, +0.001 is enough
                List<EntityRenderState.ShadowPiece> newPieces = state.shadowPieces
                        .stream()
                        .map(piece -> new EntityRenderState.ShadowPiece(piece.relativeX(), piece.relativeY() + 0.001f, piece.relativeZ(), piece.shapeBelow(), piece.alpha()))
                        .toList();
                state.shadowPieces.clear();
                state.shadowPieces.addAll(newPieces);
            }
        }

        if (renderStateOverrides != null) {
            renderStateOverrides.applyOverridesIfPossible(state);
        }
    }

    @Override
    public AreaPropertyBundle getProperties() {
        return AreaPropertyBundle.INSTANCE;
    }

    @Override
    public ParticleDisplayCondition getParticleDisplayCondition() {
        AABB dimensions = this.mesh.bounds.buildBoundingBox();
        return ParticleDisplayCondition.inArea(dimensions);
    }

    @Override
    public ExportPathSpec getExportPath() {
        return ExportPathSpec.of("area_renders", "area_render");
    }

    @Override
    public void dispose() {
        super.dispose();
        mesh.dispose();
    }

    @Override
    public List<List<Integer>> getTicksToFullyAnimate() {
        List<List<Integer>> animationTimings = new ArrayList<>();
        if (!mesh.getMeshState().isBuildStage && mesh.getAnimationCompletionTimings().isPresent()) {
            animationTimings.add(mesh.getAnimationCompletionTimings().get());
        }

        if (lastSeenEntityAnimationTimings != null && !lastSeenEntityAnimationTimings.isEmpty()) {
            animationTimings.add(lastSeenEntityAnimationTimings);
        }

        return animationTimings;
    }

    @Override
    public void onScreenHandle(RenderScreen screen, GuiGraphicsExtractor graphics, float tickDelta) {
        int scale = Minecraft.getInstance().getWindow().getGuiScale();

        if (this.selectedEntityId != null) {
            DrawProjectionDataCache projectionData = RenderableDispatcher.PROJECTION_CACHE.get(DrawType.PREVIEW);
            DrawEntityDataCache entityDrawData = drawnVertexBoundCache.get(selectedEntityId);
            if (entityDrawData == null) return;

            CornerData cornerData = EntityRenderBoundsUtil.getDrawnBounds(CameraUtil.createRenderState(this), entityDrawData, projectionData);
            if (cornerData == null) return;

            int minX = cornerData.minX() / scale;
            int minY = cornerData.minY() / scale;
            int maxX = cornerData.maxX() / scale;
            int maxY = cornerData.maxY() / scale;
            graphics.outline(minX, minY, maxX - minX, maxY - minY, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean onScreenViewportClick(MouseButtonEvent click, boolean doubled) {
        int scale = Minecraft.getInstance().getWindow().getGuiScale();
        double x = click.x() * scale;
        double y = click.y() * scale;

        this.selectedEntityId = null;
        if (click.hasShiftDown()) {
            return false; // deselection option
        }

        Integer closestEntityId = null;
        double lastDistance = Double.MAX_VALUE;

        DrawProjectionDataCache projectionData = RenderableDispatcher.PROJECTION_CACHE.get(DrawType.PREVIEW);
        for (Map.Entry<Integer, DrawEntityDataCache> drawnEntities : drawnVertexBoundCache.entrySet()) {
            Integer entityId = drawnEntities.getKey();
            DrawEntityDataCache entityDrawData = drawnEntities.getValue();

            CornerData bounds = EntityRenderBoundsUtil.getDrawnBounds(CameraUtil.createRenderState(this), entityDrawData, projectionData);
            if (bounds != null && bounds.contains((int) x, (int) y)) {
                int distanceToCenter = bounds.getDistanceToCenterSquared((int) x, (int) y);
                if (distanceToCenter < lastDistance) {
                    lastDistance = distanceToCenter;
                    closestEntityId = entityId;
                }
            }
        }

        if (closestEntityId != null) {
            this.selectedEntityId = closestEntityId;
            this.renderStateOverrides = ENTITY_SPECIFIC_OVERRIDES_BY_ID.computeIfAbsent(closestEntityId, e -> EntityTypeSpecificOverrides.getOverrides(drawnVertexBoundCache.get(e).renderState()));
            return true;
        }

        return false;
    }

    protected boolean hasEntityType(Class<? extends Entity> entityTypeClass) {
        AreaPropertyBundle properties = getProperties();

        for (Entity entity : this.entities) {
            if (properties.hiddenEntityTypes.contains(entity.getType())) continue;
            if (entity instanceof LivingEntity && properties.hideLivingEntities.get()) continue;
            if (entityTypeClass.isAssignableFrom(entity.getClass())) {
                return true;
            }
        }

        return false;
    }

    protected boolean hasLivingEntityProperty(Predicate<LivingEntity> predicate) {
        AreaPropertyBundle properties = getProperties();

        for (Entity entity : this.entities) {
            if (properties.hiddenEntityTypes.contains(entity.getType())) continue;
            if (entity instanceof LivingEntity && properties.hideLivingEntities.get()) continue;
            if (entity instanceof LivingEntity livingEntity && predicate.test(livingEntity)) {
                return true;
            }
        }

        return false;
    }
}
