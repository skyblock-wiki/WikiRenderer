package com.pigicial.wikirenderer.render.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.render.DefaultRenderable;
import com.pigicial.wikirenderer.render.export.ExportPathSpec;
import com.pigicial.wikirenderer.render.item.AnimationTimingsProvider;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.util.DrawType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.List;

public class ContainerScreenRenderable extends DefaultRenderable<ContainerScreenPropertyBundle> implements AnimationTimingsProvider {

    private final AbstractContainerScreen<?> containerScreen;

    private static final GuiRenderState state = new GuiRenderState();
    private static GuiRenderer guiRenderer;

    public ScreenSizeData previewScreenSizeData;
    private List<Integer> lastSeenAnimationTimings = null;

    public ContainerScreenRenderable(AbstractContainerScreen<?> containerScreen) {
        this.containerScreen = containerScreen;
    }

    @Override
    public boolean renderPreviewToEntireScreenWidth() {
        return false;
    }

    @Override
    public int optionallyOverrideExportWidth(int width) {
        if (previewScreenSizeData != null) {
            // shouldn't be null
            return previewScreenSizeData.getWidthAndHeightForHigherScale(getProperties().exportGuiScale.get())[0];
        }
        return super.optionallyOverrideExportWidth(width);
    }

    @Override
    public int optionallyOverrideExportHeight(int height) {
        if (previewScreenSizeData != null) {
            // shouldn't be null
            return previewScreenSizeData.getWidthAndHeightForHigherScale(getProperties().exportGuiScale.get())[1];
        }
        return super.optionallyOverrideExportHeight(height);
    }

    @Override
    public void onScreenHandle(RenderScreen screen, GuiGraphicsExtractor graphics, float tickDelta) {
        if (previewScreenSizeData != null) {
            int maxScale = determineScaleFromResolution(previewScreenSizeData.previewWidth(), previewScreenSizeData.previewHeight());
            if (getProperties().previewGuiScale.max() != maxScale) {
                getProperties().previewGuiScale.setMaxValue(maxScale);
            }
        }

        super.onScreenHandle(screen, graphics, tickDelta);
    }

    @Override
    public void emitVerticesThenDraw(RenderScreen renderScreen, Matrix4fStack modelViewStack, PoseStack poseStack, float tickDelta, long timeSinceCreationMs) {
        WikiRenderer.inContainerScreenDraw = true;
        WikiRenderer.animationTimingDataRequestedToFill = new ArrayList<>();
        state.reset();

        Minecraft client = Minecraft.getInstance();

        int framebufferWidth = WikiRenderer.mainTargetOverride.width;
        int framebufferHeight = WikiRenderer.mainTargetOverride.height;
        int guiScale = WikiRenderer.currentDrawType == DrawType.EXPORT
                ? getProperties().exportGuiScale.get()
                : getProperties().previewGuiScale.get();

        if (WikiRenderer.currentDrawType == DrawType.PREVIEW) {
            this.previewScreenSizeData = new ScreenSizeData(framebufferWidth, framebufferHeight, guiScale);
        }

        int width = (int) (framebufferWidth / (double) guiScale);
        int guiScaledWidth = (framebufferWidth / (double) guiScale > width) ? width + 1 : width;
        int height = (int) (framebufferHeight / (double) guiScale);
        int guiScaledHeight = (framebufferHeight / (double) guiScale > height) ? height + 1 : height;

        Window window = client.getWindow();
        int savedWidth = window.getWidth();
        int savedHeight = window.getHeight();
        int savedWindowScale = window.getGuiScale();
        WindowRenderState windowRenderState = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState;
        int savedRenderStateGuiScale = windowRenderState.guiScale;

        double currentGuiMouseX = client.mouseHandler.getScaledXPos(window);
        double currentGuiMouseY = client.mouseHandler.getScaledYPos(window);

        double viewportWidth = renderScreen.viewportEndX - renderScreen.viewportBeginX;
        double relativeMouseX = currentGuiMouseX - renderScreen.viewportBeginX;

        double percentageMouseX = viewportWidth > 0 ? (relativeMouseX / viewportWidth) : 0.0;
        double percentageMouseY = window.getGuiScaledHeight() > 0 ? (currentGuiMouseY / (double) window.getGuiScaledHeight()) : 0.0;

        int mouseX = (int) (percentageMouseX * guiScaledWidth);
        int mouseY = (int) (percentageMouseY * guiScaledHeight);

        if (guiRenderer == null) {
            guiRenderer = getGuiRenderer(client);
        }

        window.setWidth(framebufferWidth);
        window.setHeight(framebufferHeight);
        window.setGuiScale(guiScale);
        windowRenderState.guiScale = guiScale;

        GuiGraphicsExtractor guiGraphics = new GuiGraphicsExtractor(client, state, mouseX, mouseY);

        client.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);

        containerScreen.init(guiScaledWidth, guiScaledHeight);
        containerScreen.resize(guiScaledWidth, guiScaledHeight);

        containerScreen.extractRenderStateWithTooltipAndSubtitles(guiGraphics, mouseX, mouseY, tickDelta);
        guiRenderer.render();
        guiRenderer.endFrame();

        // Restore
        window.setWidth(savedWidth);
        window.setHeight(savedHeight);
        window.setGuiScale(savedWindowScale);
        windowRenderState.guiScale = savedRenderStateGuiScale;
        WikiRenderer.inContainerScreenDraw = false;

        lastSeenAnimationTimings = WikiRenderer.animationTimingDataRequestedToFill;
        WikiRenderer.animationTimingDataRequestedToFill = null;
    }

    private GuiRenderer getGuiRenderer(Minecraft client) {
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

    @Override
    public ContainerScreenPropertyBundle getProperties() {
        return ContainerScreenPropertyBundle.INSTANCE;
    }

    @Override
    public ExportPathSpec getExportPath() {
        return ExportPathSpec.of("screen", "screen");
    }

    public static int determineScaleFromResolution(int framebufferWidth, int framebufferHeight) {
        int maxScale = 0;
        int guiScale = 1;

        while (guiScale != maxScale && guiScale < framebufferWidth && guiScale < framebufferHeight && framebufferWidth / (guiScale + 1) >= 320 && framebufferHeight / (guiScale + 1) >= 240) {
            guiScale++;
        }

        return guiScale;
    }

    @Override
    public List<List<Integer>> getTicksToFullyAnimate() {
        return lastSeenAnimationTimings == null ? new ArrayList<>() : List.of(lastSeenAnimationTimings);
    }
}
