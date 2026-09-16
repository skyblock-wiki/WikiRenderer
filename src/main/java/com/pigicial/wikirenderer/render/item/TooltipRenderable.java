package com.pigicial.wikirenderer.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.render.DefaultRenderable;
import com.pigicial.wikirenderer.render.batch.DynamicBatchLabelProvider;
import com.pigicial.wikirenderer.render.export.ExportPathSpec;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.util.ItemNameUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fStack;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.util.Collection;
import java.util.List;

public class TooltipRenderable extends DefaultRenderable<TooltipPropertyBundle> implements DynamicBatchLabelProvider {

    private final ItemStack stack;

    public TooltipRenderable(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public void emitVerticesThenDraw(RenderScreen renderScreen, Matrix4fStack matrix4fStack, PoseStack matrices, float tickDelta, long timeSinceCreationMs) {
        Minecraft client = Minecraft.getInstance();

	    GuiRenderState state = new GuiRenderState();
        GuiRenderer renderer = this.getGuiRenderer(client, state);

        List<ClientTooltipComponent> list = this.getTooltip();
	    this.stack.getTooltipImage().ifPresent(datax -> list.add(list.isEmpty() ? 0 : 1, ClientTooltipComponent.create(datax)));

        MouseHandler mouse = client.mouseHandler;
        int xScale = (int) mouse.getScaledXPos(client.getWindow());
        int yScale = (int) mouse.getScaledYPos(client.getWindow());

        if (getProperties().hideBackground.get()) {
            WikiRenderer.skipTooltipBackgroundRender = true;
        }
        GuiGraphicsExtractor guiGraphics = new GuiGraphicsExtractor(client, state, xScale, yScale);
        guiGraphics.tooltip(client.font, list, 0, 0, this::positionTooltip, this.stack.get(DataComponents.TOOLTIP_STYLE), false);

        WikiRenderer.skipTooltipBackgroundRender = false;

		renderer.render();
		renderer.close();
    }

    private GuiRenderer getGuiRenderer(Minecraft client, GuiRenderState state) {
        AtlasManager atlasManager = client.getAtlasManager();

        List<PictureInPictureRenderer<?>> renderers = List.of(
                new GuiEntityRenderer(client.getEntityRenderDispatcher()),
                new GuiSkinRenderer(),
                new GuiBookModelRenderer(),
                new GuiBannerResultRenderer(atlasManager),
                new GuiProfilerChartRenderer()
        );

        return new GuiRenderer(state, client.gameRenderer.featureRenderDispatcher(), renderers);
    }

    private Vector2ic positionTooltip(int screenWidth, int screenHeight, int x, int y, int width, int height) {
        return new Vector2i(DefaultTooltipPositioner.INSTANCE.positionTooltip(screenWidth, screenHeight, x, y, width, height)).add(-12 - width / 2, 12 - height / 2);
    }

    private List<ClientTooltipComponent> getTooltip() {
        return Screen.getTooltipFromItem(Minecraft.getInstance(), this.stack)
                .stream()
                .map(Component::getVisualOrderText)
                .map(ClientTooltipComponent::create)
                .collect(Util.toMutableList());
    }

    public int getTooltipSize() {
        Minecraft minecraft = Minecraft.getInstance();

        List<ClientTooltipComponent> list = this.getTooltip();
        this.stack.getTooltipImage().ifPresent(data -> list.add(list.isEmpty() ? 0 : 1, ClientTooltipComponent.create(data)));

        int width = 0;
        int height = list.size() == 1 ? -2 : 0;

        for (ClientTooltipComponent component : list) {
            int componentWidth = component.getWidth(minecraft.font);
            if (componentWidth > width) width = componentWidth;
            height += component.getHeight(minecraft.font);
        }

        // use +18 instead of +12 so hypixel skyblock tooltips dont get cropped off
        // todo: figure out a better way to calculate the spacing on this (since maybe other server/mod tooltips need even more spacing)
        return Math.max(width + 18, height + 18);
    }

    @Override
    public TooltipPropertyBundle getProperties() {
        return TooltipPropertyBundle.INSTANCE;
    }

    @Override
    public ExportPathSpec getExportPath() {
        return ExportPathSpec.of("tooltip", BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
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
}
