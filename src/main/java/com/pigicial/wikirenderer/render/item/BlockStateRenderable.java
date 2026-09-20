package com.pigicial.wikirenderer.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.mixin.access.BlockEntityAccessor;
import com.pigicial.wikirenderer.mixin.access.BlockEntityRenderDispatcherAccessor;
import com.pigicial.wikirenderer.mixin.access.LevelRendererAccessor;
import com.pigicial.wikirenderer.property.GlobalProperties;
import com.pigicial.wikirenderer.render.CameraUtil;
import com.pigicial.wikirenderer.render.TickingRenderable;
import com.pigicial.wikirenderer.render.batch.DynamicBatchLabelProvider;
import com.pigicial.wikirenderer.render.export.ExportPathSpec;
import com.pigicial.wikirenderer.render.particle.ParticleDisplayCondition;
import com.pigicial.wikirenderer.render.particle.ParticleRendererAndLooper;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.textures.PlayerTextureUtils;
import com.pigicial.wikirenderer.textures.TextureData;
import com.pigicial.wikirenderer.textures.TextureDataProvider;
import com.pigicial.wikirenderer.util.AnimationTimingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;

import java.util.*;

public class BlockStateRenderable
        extends ItemBasedRenderable<BlockStatePropertyBundle>
        implements TickingRenderable<BlockStatePropertyBundle>, DynamicBatchLabelProvider, TextureDataProvider, AnimationTimingsProvider {

    private final BlockDisplayContext displayContext = BlockDisplayContext.create();
    private final Minecraft client = Minecraft.getInstance();

    private final BlockState state;
    private final @Nullable BlockEntity blockEntity;
    private LinkedHashMap<String, TextureData> textureData = null;

    public BlockStateRenderable(BlockState state, @Nullable BlockEntity blockEntity) {
        this.state = state;
        this.blockEntity = blockEntity;
    }

    @Nullable
    public static BlockStateRenderable of(Block block) {
        return of(block.defaultBlockState(), null);
    }

    @Nullable
    public static BlockStateRenderable of(BlockState state, @Nullable CompoundTag nbt) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return null;

        BlockEntity blockEntity = null;

        if (state.getBlock() instanceof EntityBlock provider) {
            blockEntity = provider.newBlockEntity(client.player.blockPosition(), state);
            prepareBlockEntity(state, blockEntity, nbt);
        }

        return new BlockStateRenderable(state, blockEntity);
    }

    @Override
    public ParticleDisplayCondition getParticleDisplayCondition() {
        return ParticleDisplayCondition.DURING_TICK;
    }

    @Nullable
    public static BlockStateRenderable copyOf(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        CompoundTag data = blockEntity != null ? blockEntity.saveWithoutMetadata(world.registryAccess()) : null;

        return of(state, data);
    }

    private static void prepareBlockEntity(BlockState state, BlockEntity blockEntity, @Nullable CompoundTag nbt) {
        if (blockEntity == null) return;
        if (Minecraft.getInstance().level == null) return;

        ((BlockEntityAccessor) blockEntity).wikirenderer$setBlockState(state);
        blockEntity.setLevel(Minecraft.getInstance().level);

        if (nbt == null) return;

        CompoundTag nbtCopy = nbt.copy();

        nbtCopy.putInt("x", 0);
        nbtCopy.putInt("y", 0);
        nbtCopy.putInt("z", 0);
        try (ProblemReporter.ScopedCollector logging = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), WikiRenderer.LOGGER)) {
            blockEntity.loadWithComponents(TagValueInput.create(logging, blockEntity.getLevel().registryAccess(), nbtCopy));
        }
    }

    @Override
    public void emitVerticesThenDraw(RenderScreen renderScreen, Matrix4fStack matrix4fStack, PoseStack matrices, float tickDelta, long timeSinceCreationMs) {
        matrices.pushPose();
        matrices.translate(-0.5, -0.5, -0.5);

        SubmitNodeStorage submitNodeCollector = ((LevelRendererAccessor) this.client.levelRenderer).wikirenderer$getSubmitNodeStorage();

        // renders the main stuff
        if (this.state.getRenderShape() != RenderShape.INVISIBLE) {
            BlockModelRenderState blockModelRenderState = new BlockModelRenderState();
            BlockModelResolver blockModelResolver = ((BlockEntityRenderDispatcherAccessor) Minecraft.getInstance().getBlockEntityRenderDispatcher()).wikirenderer$getBlockModelResolver();
            blockModelResolver.update(blockModelRenderState, state, displayContext);
            blockModelRenderState.submit(matrices, submitNodeCollector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            // todo: figure out liquid rendering (waterlogged / fluid states)
        }

        // renders the extra stuff, like the book on the enchantment table, middle bell within the bell block, etc
        BlockEntityRenderState renderState = this.blockEntity == null ? null : this.client.getBlockEntityRenderDispatcher().tryExtractRenderState(blockEntity, tickDelta, null, true);
        if (renderState != null) {
            renderState.lightCoords = LightCoordsUtil.FULL_BRIGHT;
            this.client.getBlockEntityRenderDispatcher().submit(renderState, matrices, submitNodeCollector, CameraUtil.createRenderState(this));
        }

        super.drawSubmittedRenderFeatures();

        assert this.client.player != null;
        double xOffset = this.client.player.getX() % 1d;
        double zOffset = this.client.player.getZ() % 1d;

        if (xOffset < 0) xOffset += 1;
        if (zOffset < 0) zOffset += 1;

        matrices.translate(xOffset, 1.65 + this.client.player.getY() % 1d, zOffset);
        ParticleRendererAndLooper.submitAndDrawParticles(this, matrices.last().pose(), tickDelta);

        matrices.popPose();
    }

    @Override
    public void tick() {
        if (client.level == null || client.player == null) return;
        if (GlobalProperties.get().tickParticles.get()) {
            if (this.blockEntity != null && this.state.getTicker(client.level, this.blockEntity.getType()) != null) {
                BlockEntityTicker<BlockEntity> ticker = this.state.getTicker(client.level, (BlockEntityType<BlockEntity>) this.blockEntity.getType());
                if (ticker == null) return;

                ticker.tick(client.level, client.player.blockPosition(), this.state, this.blockEntity);
            }

            if (client.level.getRandom().nextDouble() < 0.150) {
                this.state.getBlock().animateTick(this.state, client.level, client.player.blockPosition(), client.level.getRandom());
            }
        }

    }

    @Override
    public BlockStatePropertyBundle getProperties() {
        return BlockStatePropertyBundle.INSTANCE;
    }

    @Override
    public ExportPathSpec getExportPath() {
        return ExportPathSpec.ofIdentified(
                BuiltInRegistries.BLOCK.getKey(this.state.getBlock()),
                "block"
        );
    }

    @Override
    public String buildFileName(String preset) {
        String id = BuiltInRegistries.BLOCK.getKey(this.state.getBlock()).getPath();
        Item item = this.state.getBlock().asItem();
        String name = item.getName(item.getDefaultInstance()).getString();
        return preset.replace("%id%", id).replace("%name%", name);
    }

    @Override
    public Collection<String> buildPresetExamples() {
        return List.of("label_example.block_id", "label_example.block_name");
    }

    @Override
    public void cacheTextureData(Runnable rebuildCallback) {
        if (this.textureData == null && this.blockEntity instanceof SkullBlockEntity skullBlockEntity) {
            this.textureData = new LinkedHashMap<>();
            ResolvableProfile profile = skullBlockEntity.getOwnerProfile();
            if (profile != null) {
                TextureData texture = PlayerTextureUtils.getTextureDataFromGameProfile(profile.partialProfile());
                if (texture != null) {
                    textureData.put("block", texture);
                }
            }
        }
    }

    @Override
    public @NotNull Map<String, TextureData> getTextureData(Runnable rebuildCallback) {
        Map<String, TextureData> textureData = new LinkedHashMap<>();
        if (this.blockEntity instanceof SkullBlockEntity skullBlockEntity) {
            ResolvableProfile profile = skullBlockEntity.getOwnerProfile();
            if (profile != null) {
                TextureData texture = PlayerTextureUtils.getTextureDataFromGameProfile(profile.partialProfile());
                if (texture != null) {
                    textureData.put("block", texture);
                }
            }
        }
        return textureData;
    }

    @Override
    public List<List<Integer>> getTicksToFullyAnimate() {
        List<Integer> animationTimings = new LinkedList<>();
        AnimationTimingUtil.scanTicksToFullyAnimateBlock(this.state, animationTimings, null);
        return List.of(animationTimings);
    }
}
