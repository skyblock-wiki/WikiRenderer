package com.pigicial.wikirenderer.render.item;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.mixin.access.ItemStackRenderStateAccessor;
import com.pigicial.wikirenderer.property.IntProperty;
import com.pigicial.wikirenderer.render.batch.DynamicBatchLabelProvider;
import com.pigicial.wikirenderer.render.export.ExportPathSpec;
import com.pigicial.wikirenderer.render.item.models.ItemModelsProcessor;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.textures.PlayerTextureUtils;
import com.pigicial.wikirenderer.textures.TextureData;
import com.pigicial.wikirenderer.textures.TextureDataProvider;
import com.pigicial.wikirenderer.util.AnimationTimingUtil;
import com.pigicial.wikirenderer.util.ItemNameUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;

import java.util.*;

public class ItemRenderable extends ItemBasedRenderable<ItemRenderablePropertyBundle> implements TextureDataProvider, DynamicBatchLabelProvider, AnimationTimingsProvider {

    private static final ItemStackRenderState RENDER_STATE = new ItemStackRenderState();

    public ItemStack stack;
    @Nullable
    private List<ItemModel> models = null;
    @Nullable
    private IntProperty currentModelIndex = null;

    @Nullable
    private Map<String, TextureData> textureData = null;
    @Nullable
    private DyedItemColor actualDyedItemColor = null;

    public ItemRenderable(ItemStack stack) {
        this.setItemStack(stack);
        this.customFileName = ItemNameUtil.getItemDisplayName(this.stack);
    }

    public void setItemStack(ItemStack itemStack) {
        this.stack = itemStack;
        this.models = ItemModelsProcessor.getModels(stack);
        if (models != null && models.size() > 1) {
            currentModelIndex = IntProperty.of(1, 1, models.size());
        }
    }

    public @Nullable IntProperty getCurrentModelIndex() {
        return currentModelIndex;
    }

    public @Nullable List<ItemModel> getModels() {
        return models;
    }

    @Override
    public String buildFileName(String preset) {
        String id = BuiltInRegistries.ITEM.getKey(this.stack.getItem()).getPath();
        String name = ItemNameUtil.getItemDisplayName(this.stack);
        return preset.replace("%id%", id).replace("%name%", name);
    }

    @Override
    public Collection<String> buildPresetExamples() {
        return List.of("label_example.item_id", "label_example.item_name");
    }

    @Override
    public ExportPathSpec getExportPath() {
        return ExportPathSpec.ofIdentified(
                BuiltInRegistries.ITEM.getKey(this.stack.getItem()),
                "item"
        );
    }

    @Override
    public void setupLighting() {
        this.setupLighting(RENDER_STATE);
    }

    @Override
    public void prepare() {
        ItemRenderablePropertyBundle properties = getProperties();
        if (properties.overrideEnchantmentGlints.get()) {
            WikiRenderer.overrideGlint = true;
        }

        this.actualDyedItemColor = stack.get(DataComponents.DYED_COLOR);
        if (properties.overrideDyeColors.get()) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(properties.dyeColorOverride));
        }

        ItemModelResolver itemModelResolver = Minecraft.getInstance().getItemModelResolver();

        Identifier modelId = stack.get(DataComponents.ITEM_MODEL);
        ModelManager modelManager = Minecraft.getInstance().getModelManager();
        if (properties.useModelOverrides.get() && modelId != null && models != null && !models.isEmpty()) {
            RENDER_STATE.setOversizedInGui(modelManager.getItemProperties(modelId).oversizedInGui());

            ItemModel itemModel = models.get(currentModelIndex == null ? 0 : Math.min(currentModelIndex.get(), models.size()) - 1);
            itemModel.update(RENDER_STATE, stack, itemModelResolver, ItemDisplayContext.GUI, Minecraft.getInstance().level, null, 0);
        } else {
            itemModelResolver.appendItemLayers(
                    RENDER_STATE,
                    this.stack,
                    ItemDisplayContext.GUI,
                    Minecraft.getInstance().level,
                    null,
                    0
            );
        }
    }

    @Override
    public void emitVerticesThenDraw(RenderScreen renderScreen, Matrix4fStack matrix4fStack, PoseStack matrices, float tickDelta, long timeSinceCreationMs) {
        SubmitNodeStorage nodeStorage = WikiRenderer.NODE_STORAGE;

        ((ItemStackRenderStateAccessor) RENDER_STATE).wikirenderer$setDisplayContext(ItemDisplayContext.GUI);
        RENDER_STATE.submit(matrices, nodeStorage, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
    }

    @Override
    public void cleanUp() {
        RENDER_STATE.clear();
        WikiRenderer.overrideGlint = false;

        stack.set(DataComponents.DYED_COLOR, this.actualDyedItemColor);
        this.actualDyedItemColor = null;
    }

    @Override
    public ItemRenderablePropertyBundle getProperties() {
        return ItemRenderablePropertyBundle.INSTANCE;
    }

    @Override
    public void cacheTextureData(Runnable rebuildCallback) {
        TextureData playerSkin = PlayerTextureUtils.getTextureDataFromPlayerHead(this.stack);
        this.textureData = playerSkin == null ? new HashMap<>() : Map.of("item", playerSkin);
    }

    public @NotNull Map<String, TextureData> getTextureData(Runnable rebuildCallback) {
        if (this.textureData == null) {
            this.cacheTextureData(rebuildCallback);
        }

        TextureData playerSkin = PlayerTextureUtils.getTextureDataFromPlayerHead(this.stack);
        return playerSkin == null ? new HashMap<>() : Map.of("item", playerSkin);
    }

    @Override
    public List<List<Integer>> getTicksToFullyAnimate() {
        List<Integer> animationTimings = new LinkedList<>();
        AnimationTimingUtil.scanTicksToFullyAnimateItem(this, animationTimings);
        return List.of(animationTimings);
    }

    @Override
    public Map<String, String> getPngTextMetadata() {
        TextureData headTextureData = PlayerTextureUtils.getTextureDataFromPlayerHead(this.stack);
        if (headTextureData == null) {
            return Map.of();
        }

        MinecraftProfileTexture skinTexture = headTextureData.payload().textures().get(MinecraftProfileTexture.Type.SKIN);
        if (skinTexture == null) {
            return Map.of();
        }

        String textureId = skinTexture.getHash();
        if (textureId == null || textureId.isBlank()) {
            return Map.of();
        }

        return Map.of("texture_id", textureId);
    }
}
