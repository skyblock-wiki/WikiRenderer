package com.pigicial.wikirenderer.render.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.screen.components.EntityTypeSpecificPropertiesComponent;
import com.pigicial.wikirenderer.mixin.access.ClientMannequinAccessor;
import com.pigicial.wikirenderer.mixin.access.ItemStackRenderStateAccessor;
import com.pigicial.wikirenderer.mixin.access.LevelRendererAccessor;
import com.pigicial.wikirenderer.mixin.access.MannequinAccessor;
import com.pigicial.wikirenderer.render.CameraUtil;
import com.pigicial.wikirenderer.render.DefaultRenderable;
import com.pigicial.wikirenderer.render.batch.DynamicBatchLabelProvider;
import com.pigicial.wikirenderer.render.entity.options.EntityTypeSpecificOverrides;
import com.pigicial.wikirenderer.render.entity.player.RenderablePlayerEntity;
import com.pigicial.wikirenderer.render.export.ExportPathSpec;
import com.pigicial.wikirenderer.render.export.RenderableDispatcher;
import com.pigicial.wikirenderer.render.item.AnimationTimingsProvider;
import com.pigicial.wikirenderer.render.particle.ParticleDisplayCondition;
import com.pigicial.wikirenderer.render.particle.ParticleRendererAndLooper;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.textures.PlayerTextureUtils;
import com.pigicial.wikirenderer.textures.TextureData;
import com.pigicial.wikirenderer.textures.TextureDataProvider;
import com.pigicial.wikirenderer.util.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class EntityRenderable extends DefaultRenderable<EntityPropertyBundle> implements TextureDataProvider, DynamicBatchLabelProvider, AnimationTimingsProvider {

    public static final Map<Integer, EntityTypeSpecificOverrides<?>> ENTITY_SPECIFIC_OVERRIDES_BY_ID = new HashMap<>();
    public static final Map<Integer, Integer> FAKE_TO_REAL_ENTITY_ID_MAP = new HashMap<>();

    private final Minecraft client = Minecraft.getInstance();

    private final Map<Integer, DrawEntityDataCache> drawnVertexBoundCache = new HashMap<>();
    @Nullable
    protected final Entity liveNonTickableEntity;
    protected final Entity clonedTickableEntity;

    public List<Entity> nearbyEntitiesToShow = new ArrayList<>();
    private boolean nearbyEntitiesLoaded = false;

    private final Map<String, TextureData> textureData = new LinkedHashMap<>();
    protected boolean requireTextureReCache = true;
    protected AtomicBoolean textureCancelMarker = null;
    protected Vec3 cachedCenterOffset = null;
    protected Float cachedScaleMultiplier = null;
    protected boolean isNametagOnlyRenderedData = false;

    protected final EntityTypeSpecificPropertiesComponent advancedPropertiesComponent;
    public @Nullable Integer selectedEntityId;
    public @Nullable EntityTypeSpecificOverrides<?> renderStateOverrides = null;

    private List<Integer> lastSeenAnimationTimings = null;

    public EntityRenderable(@Nullable Entity liveNonTickableEntity, Entity clonedTickableEntity) {
        this.liveNonTickableEntity = liveNonTickableEntity;
        this.clonedTickableEntity = clonedTickableEntity;
        this.advancedPropertiesComponent = new EntityTypeSpecificPropertiesComponent(() -> selectedEntityId, () -> renderStateOverrides);

        if (liveNonTickableEntity != null) {
            FAKE_TO_REAL_ENTITY_ID_MAP.put(clonedTickableEntity.getId(), liveNonTickableEntity.getId());
        }
    }

    @Nullable
    public static EntityRenderable fromEntityType(EntityType<?> type, @Nullable CompoundTag nbt) {
        return fromEntityType(type, nbt, EntityNBTValidityFilter.NO_FILTER);
    }

    @Nullable
    public static EntityRenderable fromEntityType(EntityType<?> type, @Nullable CompoundTag nbt, @Nullable EntityNBTValidityFilter nbtFilterRequirement) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return null;

        if (nbt == null) nbt = new CompoundTag();
        if (nbtFilterRequirement == null) nbtFilterRequirement = EntityNBTValidityFilter.NO_FILTER;

        nbt.putString("id", EntityType.getKey(type).toString());

        Entity entity = EntityType.loadEntityRecursive(nbt, minecraft.level, new EntitySpawnRequest(EntitySpawnReason.LOAD, true), EntityProcessor.NOP);
        if (entity == null) return null;

        CompoundTag savedNbt = EntityCloner.saveNBT(entity);
        nbt.remove("id");
        if (!nbtFilterRequirement.passesFilter(nbt, savedNbt)) return null;

        entity.absSnapTo(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
        entity.setId(Integer.MAX_VALUE);
        return new EntityRenderable(null, entity);
    }

    public static EntityRenderable fromEntity(Entity source) {
        return new EntityRenderable(source, EntityCloner.copyEntityAndPassengers(source));
    }

    public static void applyToEntityAndPassengers(Entity entity, Consumer<Entity> action) {
        action.accept(entity);
        if (entity.getPassengers().isEmpty()) {
            return;
        }

        for (Entity e : entity.getPassengers()) {
            applyToEntityAndPassengers(e, action);
        }
    }

    public boolean isUsingLiveEntity() {
        return liveNonTickableEntity != null && this.getProperties().useLiveEntity.get();
    }

    public Entity getUsedEntity() {
        if (this.isUsingLiveEntity()) {
            return this.liveNonTickableEntity;
        } else {
            return this.clonedTickableEntity;
        }
    }

    private void refreshSurroundingVisibleEntities(long timeSinceCreationMs) {
        EntityPropertyBundle properties = getProperties();
        boolean isUsingLiveEntity = isUsingLiveEntity();
        if (!isUsingLiveEntity || !properties.showSurroundingEntities.get() || properties.surroundingEntitiesRadius.get() == 0) {
            this.nearbyEntitiesToShow.clear();
            return;
        }

        // not frozen selected by here
        if (properties.autoRefreshVisibleSurroundingEntities.get() || !nearbyEntitiesLoaded) {
            nearbyEntitiesLoaded = true;

            ClientLevel level = Minecraft.getInstance().level;
            assert level != null;

            Entity usedEntity = getUsedEntity();
            EntityRenderState mainEntityRenderState = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(usedEntity, 0);
            this.updateRenderState(usedEntity, mainEntityRenderState, properties, timeSinceCreationMs, isUsingLiveEntity());
            EntityVertexBounds mainEntityVertexBounds = EntityRenderBoundsUtil.getPositionOffsetBasedBounds(usedEntity, mainEntityRenderState, CameraUtil.createRenderState(this));

            AABB mainEntityBounds = mainEntityVertexBounds != null ? mainEntityVertexBounds.getBounds() : usedEntity.getBoundingBox();
            double distance = properties.surroundingEntitiesRadius.get();

            AABB area = new AABB(
                    mainEntityBounds.minX - distance, mainEntityBounds.minY - distance, mainEntityBounds.minZ - distance,
                    mainEntityBounds.maxX + distance, mainEntityBounds.maxY + distance, mainEntityBounds.maxZ + distance
            );

            this.nearbyEntitiesToShow = level.getEntities((Entity) null, area, entity -> {
                if (entity instanceof EnderDragonPart) return false; // crash fix
                if (entity == clonedTickableEntity || entity == liveNonTickableEntity) return false;

                EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(entity, 0);
                this.updateRenderState(entity, state, properties, timeSinceCreationMs, true);

                EntityVertexBounds entityVertexBounds = EntityRenderBoundsUtil.getPositionOffsetBasedBounds(entity, state, CameraUtil.createRenderState(this));
                AABB entityBounds = entityVertexBounds == null ? null : entityVertexBounds.getBounds();

                return entityBounds != null && entityBounds.intersects(area);
            });
        }

        this.nearbyEntitiesToShow.removeIf(Entity::isRemoved);
    }

    public void forBaseAndSurroundingEntities(Entity baseEntity, BiConsumer<Entity, Boolean> predicate) {
        applyToEntityAndPassengers(baseEntity, e -> predicate.accept(e, false));
        for (Entity nearbyEntity : nearbyEntitiesToShow) {
            predicate.accept(nearbyEntity, true);
        }
    }

    @Override
    public void emitVerticesThenDraw(RenderScreen renderScreen, Matrix4fStack matrix4fStack, PoseStack matrices, float delta, long timeSinceCreationMs) {
        matrices.pushPose();

        boolean usingLiveEntity = isUsingLiveEntity();
        Entity usedEntity = this.getUsedEntity();
        float tickDelta = usingLiveEntity ? delta : 0;

        List<Integer> animationTimingsToFill = new ArrayList<>();

        this.drawnVertexBoundCache.clear();
        this.refreshSurroundingVisibleEntities(timeSinceCreationMs);
        this.forBaseAndSurroundingEntities(usedEntity, (entity, isSurrounding) -> {
            EntityPropertyBundle properties = this.getProperties();
            if (isSurrounding && properties.hiddenSurroundingEntityTypes.contains(entity.getType())) return;

            Vec3 entityPosition = entity.position();
            Vec3 offset = entityPosition.subtract(usedEntity.position());

            if (entity instanceof ClientMannequin mannequin) {
                this.updateMannequinSkin(mannequin);
            }

            WikiRenderer.inEntityDraw = true;
            WikiRenderer.inSpriteEntityDraw = properties.spriteRendering.get();

            EntityRenderDispatcher renderDispatcher = client.getEntityRenderDispatcher();
            SubmitNodeStorage nodeStorage = ((LevelRendererAccessor) client.levelRenderer).wikirenderer$getSubmitNodeStorage();

            EntityRenderState state = renderDispatcher.extractEntity(entity, properties.tickEntityAnimations.get() && !entity.isRemoved() ? tickDelta : 0);

            int entityId = entity.getId();
            entityId = FAKE_TO_REAL_ENTITY_ID_MAP.getOrDefault(entityId, entityId);

            EntityTypeSpecificOverrides<?> renderStateOverrides = ENTITY_SPECIFIC_OVERRIDES_BY_ID.get(entityId);
            if (renderStateOverrides != null && renderStateOverrides.isInvisible()) return;

            this.updateRenderState(entity, state, properties, timeSinceCreationMs, usingLiveEntity);

            List<Runnable> partVisibilityCallbacks = new ArrayList<>();
            if (properties.spriteRendering.get()) {
                EntityRenderer<?, ?> renderer = renderDispatcher.getRenderer(state);
                if (renderer instanceof LivingEntityRenderer<?, ?, ?> livingEntityRenderer) {
                    EntitySpriteModelVisibilityUtil.hideNonHeadParts(livingEntityRenderer, partVisibilityCallbacks);
                }
            }


            if (cachedCenterOffset == null || cachedScaleMultiplier == null) {
                EntityVertexBounds vertexBounds = EntityRenderBoundsUtil.getBounds(state, CameraUtil.createRenderState(this), 0, 0, 0);
                AABB regularBounds = entity.getBoundingBox();
                if (vertexBounds == null) {
                    cachedCenterOffset = new Vec3(0, 0, 0);
                    cachedScaleMultiplier = 1F;
                } else {
                    AABB renderedBounds = vertexBounds.getBounds();
                    double xDifference = renderedBounds.getCenter().x - (regularBounds.getCenter().x - entityPosition.x);
                    double zDifference = renderedBounds.getCenter().z - (regularBounds.getCenter().z - entityPosition.z);
                    cachedCenterOffset = new Vec3(-xDifference, -renderedBounds.minY - renderedBounds.getYsize() / 2, -zDifference);
                    // centers to the screen

                    cachedScaleMultiplier = (float) (1f / Math.max(renderedBounds.getXsize(), Math.max(renderedBounds.getYsize(), renderedBounds.getZsize())));
                }
            }

            matrices.pushPose();
            matrices.scale(cachedScaleMultiplier, cachedScaleMultiplier, cachedScaleMultiplier);
            matrices.translate(cachedCenterOffset); // this fits it into the default frame

            PoseStack clonedPose = new PoseStack();
            clonedPose.mulPose(matrices.last().pose());
            drawnVertexBoundCache.put(entityId, new DrawEntityDataCache(state, offset, clonedPose, properties.spriteRendering.get()));

            WikiRenderer.animationTimingDataRequestedToFill = animationTimingsToFill;

            renderDispatcher.submit(state, CameraUtil.createRenderState(this), offset.x(), offset.y(), offset.z(), matrices, nodeStorage);
            this.drawSubmittedRenderFeatures();

            matrices.popPose();
            WikiRenderer.inSpriteEntityDraw = false;
            WikiRenderer.inEntityDraw = false;
            WikiRenderer.animationTimingDataRequestedToFill = null;
            partVisibilityCallbacks.forEach(Runnable::run);
        });

        this.lastSeenAnimationTimings = animationTimingsToFill;
        WikiRenderer.animationTimingDataRequestedToFill = null;

        if (this.client.player != null && liveNonTickableEntity != null) {
            matrices.pushPose();

            Camera camera = CameraUtil.getCamera();
            Vec3 cameraPosition = camera != null ? camera.position() : client.player.getEyePosition();
            Vec3 entityPosition = CameraUtil.getEntityPositionForParticles(usedEntity, tickDelta);

            Vec3 cameraDifference = entityPosition.subtract(cameraPosition);
            if (cachedScaleMultiplier != null) {
                matrices.scale(cachedScaleMultiplier, cachedScaleMultiplier, cachedScaleMultiplier);
                matrices.translate(cachedCenterOffset); // this fits it into the default frame
            }
            matrices.translate(-cameraDifference.x, -cameraDifference.y, -cameraDifference.z);

            ParticleRendererAndLooper.submitAndDrawParticles(this, matrices.last().pose(), delta);
            matrices.popPose();
        }

        matrices.popPose();
    }

    private void updateRenderState(Entity entity, EntityRenderState state, EntityPropertyBundle properties, long timeSinceCreationMs, boolean usingLiveEntity) {
        int entityId = entity.getId();
        EntityTypeSpecificOverrides<?> renderStateOverrides = ENTITY_SPECIFIC_OVERRIDES_BY_ID.get(FAKE_TO_REAL_ENTITY_ID_MAP.getOrDefault(entityId, entityId));

        if (state instanceof DisplayEntityRenderState displayEntityRenderState) {
            displayEntityRenderState.cameraYRot = 180 + getProperties().getUsedRotation();
            displayEntityRenderState.cameraXRot = (float) getProperties().getUsedSlant();
        }

        if (!(entity instanceof Leashable leashable && leashable.getLeashHolder() != null && nearbyEntitiesToShow.contains(leashable.getLeashHolder()))) {
            state.leashStates = null;
        }

        if (properties.spriteRendering.get()) {
            state.displayFireAnimation = false;
        }

        state.outlineColor = 0; // remove glow (doesn't render properly)
        state.shadowPieces.clear(); // remove shadows
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;

        if ((getProperties().hideNametags.get() || getProperties().spriteRendering.get()) && !isNametagOnlyRenderedData) {
            state.nameTag = null;
            state.nameTagAttachment = null;
        }

        if (properties.tickEntityAnimations.get()) {
            if (!usingLiveEntity) {
                state.ageInTicks = timeSinceCreationMs / 50f;
            } // otherwise just use the live entity state for more accuracy of what's being seen in the actual game
        } else {
            state.ageInTicks = 1;
        }

        if (state instanceof LivingEntityRenderState livingState) {
            if (properties.overrideHeadRotations.get()) {
                livingState.yRot = properties.yaw.get();
                livingState.xRot = properties.pitch.get();

                if (state instanceof ArmorStandRenderState armorStandRenderState) {
                    armorStandRenderState.headPose = new Rotations(properties.pitch.get(), properties.yaw.get(), 0);
                }
            }

            if (properties.hideRedDamageGlow.get()) {
                livingState.hasRedOverlay = false;
            }

            if (properties.overrideBodyRotations.get()) {
                livingState.bodyRot = properties.entityRotation.get();
            }
        }

        if (state instanceof EnderDragonRenderState dragonRenderState) {
            if (properties.overrideBodyRotations.get()) {
                for (int i = 0; i < 64; i++) {
                    dragonRenderState.flightHistory.record(0, properties.entityRotation.get() + 180);
                }
            }

            if (properties.hideRedDamageGlow.get()) {
                dragonRenderState.hasRedOverlay = false;
            }

            if (properties.tickEntityAnimations.get() && properties.overrideEnderDragonFlapAnimation.get()) {
                dragonRenderState.flapTime = timeSinceCreationMs / 2000f;
            }
        }


        // fix weird cape behavior with frozen models - there might be a better way to do this but ehh this is fine for now
        if (state instanceof AvatarRenderState avatarRenderState) {
            if (!usingLiveEntity) {
                avatarRenderState.capeFlap = 0;
                avatarRenderState.capeLean = 0;
                avatarRenderState.capeLean2 = 0;
            }

            if (properties.useSteveSkin.get()) {
                avatarRenderState.skin = DefaultPlayerSkin.getDefaultSkin();
            }
            if (properties.forceSmallArms.get()) {
                avatarRenderState.skin = avatarRenderState.skin.with(PlayerSkin.Patch.create(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(PlayerModelType.SLIM))
                );
            }
        }
        if (state instanceof ArmedEntityRenderState armedEntityRenderState) {
            if (properties.hideHeldItems.get()) {
                armedEntityRenderState.leftHandItemStack = ItemStack.EMPTY;
                armedEntityRenderState.rightHandItemStack = ItemStack.EMPTY;
                armedEntityRenderState.leftHandItemState.clear();
                armedEntityRenderState.rightHandItemState.clear();
                armedEntityRenderState.leftArmPose = HumanoidModel.ArmPose.EMPTY;
                armedEntityRenderState.rightArmPose = HumanoidModel.ArmPose.EMPTY;
            } else if (properties.hideEnchantments.get()) {
                for (ItemStackRenderState.LayerRenderState layer : ((ItemStackRenderStateAccessor) armedEntityRenderState.leftHandItemState).wikirenderer$getLayers()) {
                    layer.setFoilType(ItemStackRenderState.FoilType.NONE);
                }
                for (ItemStackRenderState.LayerRenderState layer : ((ItemStackRenderStateAccessor) armedEntityRenderState.rightHandItemState).wikirenderer$getLayers()) {
                    layer.setFoilType(ItemStackRenderState.FoilType.NONE);
                }
            }
        }

        if (state instanceof HumanoidRenderState humanoidRenderState) {
            if (properties.hideArmor.get() || properties.spriteRendering.get()) {
                if (properties.hideArmor.get()) {
                    humanoidRenderState.headItem.clear();
                    humanoidRenderState.wornHeadType = null;
                    humanoidRenderState.headEquipment = ItemStack.EMPTY;
                }
                humanoidRenderState.chestEquipment = ItemStack.EMPTY;
                humanoidRenderState.legsEquipment = ItemStack.EMPTY;
                humanoidRenderState.feetEquipment = ItemStack.EMPTY;
            } else if (properties.hideEnchantments.get()) {
                humanoidRenderState.headEquipment.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
                humanoidRenderState.chestEquipment.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
                humanoidRenderState.legsEquipment.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
                humanoidRenderState.feetEquipment.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            }
        }

        if (properties.invisible.get()) {
            state.isInvisible = true;
            if (state instanceof LivingEntityRenderState livingEntityRenderState) {
                livingEntityRenderState.isInvisibleToPlayer = true;
            }
        }

        if (renderStateOverrides != null) {
            renderStateOverrides.applyOverridesIfPossible(state);
        }
    }

    @Override
    public EntityPropertyBundle getProperties() {
        return EntityPropertyBundle.INSTANCE;
    }

    @Override
    public ParticleDisplayCondition getParticleDisplayCondition() {
        return ParticleDisplayCondition.inArea(() -> {
            Entity usedEntity = getUsedEntity();
            EntityRenderState mainEntityRenderState = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(usedEntity, 0);

            this.updateRenderState(usedEntity, mainEntityRenderState, getProperties(), 0, isUsingLiveEntity());
            EntityVertexBounds mainEntityVertexBounds = EntityRenderBoundsUtil.getPositionOffsetBasedBounds(usedEntity, mainEntityRenderState, CameraUtil.createRenderState(this));

            AABB mainEntityBounds = mainEntityVertexBounds != null ? mainEntityVertexBounds.getBounds() : usedEntity.getBoundingBox();
            double distance = getProperties().surroundingParticlesRadius.get();

            return new AABB(
                    mainEntityBounds.minX - distance, mainEntityBounds.minY - distance, mainEntityBounds.minZ - distance,
                    mainEntityBounds.maxX + distance, mainEntityBounds.maxY + distance, mainEntityBounds.maxZ + distance
            );
        });
    }

    @Override
    public ExportPathSpec getExportPath() {
        return ExportPathSpec.ofIdentified(
                BuiltInRegistries.ENTITY_TYPE.getKey(this.getUsedEntity().getType()),
                "entity"
        );
    }

    @Override
    public void setupLighting() {
        if (this.getProperties().spriteRendering.get()) {
            float rotation = (float) Math.toRadians(getProperties().getUsedRotation());

            // forces the face to be bright
            Vector3f faceLight = new Vector3f(0, 0, -1).rotateY((float) (Math.PI - rotation)).normalize();
            this.setupLighting(faceLight, faceLight);
        } else {
            super.setupLighting();
        }
    }

    protected boolean hasEntityType(Class<? extends Entity> entityTypeClass) {
        MutableBoolean found = new MutableBoolean(false);

        forBaseAndSurroundingEntities(getUsedEntity(), (entity, _) -> {
            if (entityTypeClass.isAssignableFrom(entity.getClass())) {
                found.setTrue();
            }
        });

        return found.get();
    }

    protected boolean hasLivingEntityProperty(Predicate<LivingEntity> predicate) {
        MutableBoolean found = new MutableBoolean(false);

        forBaseAndSurroundingEntities(getUsedEntity(), (entity, _) -> {
            if (entity instanceof LivingEntity livingEntity && predicate.test(livingEntity)) {
                found.setTrue();
            }
        });

        return found.get();
    }

    // alternative to the tick method, which has a bunch of other stuff done i dont want
    private void updateMannequinSkin(ClientMannequin clientMannequin) {
        ClientMannequinAccessor mannequin = (ClientMannequinAccessor) clientMannequin;

        CompletableFuture<Optional<PlayerSkin>> skinLookup = mannequin.getSkinLookup();
        if (skinLookup != null && skinLookup.isDone()) {
            try {
                skinLookup.get().ifPresent(mannequin::setSkin);
                mannequin.setSkinLookup(null);
            } catch (Exception ignored) {

            }
        }
    }

    @Override
    public void cacheTextureData(Runnable rebuildCallback) {
        if (!this.requireTextureReCache) return;
        this.requireTextureReCache = false;
        this.textureData.clear();

        if (this.textureCancelMarker != null) {
            this.textureCancelMarker.set(true);
        }

        AtomicBoolean cancelMarker = new AtomicBoolean(false);
        this.textureCancelMarker = cancelMarker;

        // i hate how there are like 5 billion ways skins are handled but whatever, there's probably a better way to do this but that's a later project
        applyToEntityAndPassengers(getUsedEntity(), usedEntity -> {
            switch (usedEntity) {
                case RenderablePlayerEntity player -> player.getSkinGrabber().whenComplete((data, throwable) -> {
                    if (throwable != null || cancelMarker.get() || data == null) return;
                    textureData.put("player", data);
                    rebuildCallback.run();
                });
                case AbstractClientPlayer player -> {
                    ClientPacketListener connection = Minecraft.getInstance().getConnection();
                    if (connection == null) return;

                    PlayerInfo playerInfo = connection.getPlayerInfo(player.getUUID());
                    GameProfile profile = playerInfo != null ? playerInfo.getProfile() : player.getGameProfile();

                    TextureData playerTexture = PlayerTextureUtils.getTextureDataFromGameProfile(profile);
                    if (playerTexture != null) {
                        textureData.put("player", playerTexture);
                    }
                }
                case Player player -> {
                    TextureData playerTexture = PlayerTextureUtils.getTextureDataFromGameProfile(player.getGameProfile());
                    if (playerTexture != null) {
                        textureData.put("player", playerTexture);
                    }
                }
                case ClientMannequin mannequin -> {
                    CompletableFuture<Optional<PlayerSkin>> skinLookup = ((ClientMannequinAccessor) mannequin).getSkinLookup();
                    if (skinLookup != null) {
                        skinLookup.whenComplete((skin, throwable) -> {
                            if (throwable != null || skin.isEmpty() || cancelMarker.get()) return;

                            ResolvableProfile profile = ((MannequinAccessor) mannequin).wikirenderer$getProfile();
                            PlayerSkinRenderCache.RenderInfo renderInfo = Minecraft.getInstance().playerSkinRenderCache().getOrDefault(profile);
                            TextureData playerTexture = PlayerTextureUtils.getTextureDataFromGameProfile(renderInfo.gameProfile());
                            if (playerTexture != null) {
                                textureData.put("player", playerTexture);
                                rebuildCallback.run();
                            }
                        });
                    }
                }
                case Mannequin mannequin -> {
                    ResolvableProfile profile = ((MannequinAccessor) mannequin).wikirenderer$getProfile();
                    PlayerSkinRenderCache.RenderInfo renderInfo = Minecraft.getInstance().playerSkinRenderCache().getOrDefault(profile);
                    TextureData playerTexture = PlayerTextureUtils.getTextureDataFromGameProfile(renderInfo.gameProfile());
                    if (playerTexture != null) {
                        textureData.put("player", playerTexture);
                    }
                }
                case Display.ItemDisplay itemDisplay -> {
                    Display.ItemDisplay.ItemRenderState itemRenderState = itemDisplay.itemRenderState();
                    if (itemRenderState != null) {
                        ItemStack item = itemRenderState.itemStack();
                        TextureData itemTextureData = PlayerTextureUtils.getTextureDataFromPlayerHead(item);
                        if (itemTextureData != null) {
                            textureData.put("item", itemTextureData);
                        }
                    }
                }
                case ItemEntity itemEntity -> {
                    ItemStack item = itemEntity.getItem();
                    TextureData itemTextureData = PlayerTextureUtils.getTextureDataFromPlayerHead(item);
                    if (itemTextureData != null) {
                        textureData.put("item", itemTextureData);
                    }
                }
                default -> {
                }
            }

            if (usedEntity instanceof LivingEntity livingEntity) {
                for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                    ItemStack item = livingEntity.getItemBySlot(equipmentSlot);
                    TextureData itemTextureData = PlayerTextureUtils.getTextureDataFromPlayerHead(item);
                    if (itemTextureData != null) {
                        textureData.put(equipmentSlot.getName(), itemTextureData);
                    }
                }
            }
        });
    }

    @Override
    public @NotNull Map<String, TextureData> getTextureData(Runnable rebuildCallback) {
        if (this.requireTextureReCache) {
            this.cacheTextureData(rebuildCallback);
        }
        return this.textureData;
    }

    @Override
    public List<List<Integer>> getTicksToFullyAnimate() {
        return lastSeenAnimationTimings == null ? new ArrayList<>() : List.of(lastSeenAnimationTimings);
    }

    @Override
    public String buildFileName(String preset) {
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(this.getUsedEntity().getType()).getPath();
        Component displayName = this.getUsedEntity().getDisplayName();
        String name = displayName.getString();
        return preset.replace("%entity_type%", type).replace("%name%", name);
    }

    @Override
    public Collection<String> buildPresetExamples() {
        return List.of("label_example.entity_type", "label_example.entity_name");
    }

    @Override
    public void onScreenHandle(RenderScreen screen, GuiGraphicsExtractor graphics, float tickDelta) {
        super.onScreenHandle(screen, graphics, tickDelta);
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
            int entityId = drawnEntities.getKey();
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
}
