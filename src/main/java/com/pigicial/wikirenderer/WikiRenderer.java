package com.pigicial.wikirenderer;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.pigicial.wikirenderer.command.WikiRendererCommand;
import com.pigicial.wikirenderer.screen.components.AreaSelectionComponent;
import com.pigicial.wikirenderer.screen.components.IOStateComponent;
import com.pigicial.wikirenderer.render.OrthographicSort;
import com.pigicial.wikirenderer.render.area.AreaSelectionHelper;
import com.pigicial.wikirenderer.render.area.MeshWorldOverrides;
import com.pigicial.wikirenderer.render.export.CustomRenderPipelines;
import com.pigicial.wikirenderer.render.export.FileIO;
import com.pigicial.wikirenderer.render.export.animation.AnimationHandler;
import com.pigicial.wikirenderer.render.particle.ParticleDisplayCondition;
import com.pigicial.wikirenderer.render.skyblock.frame_based.SkyBlockTimingDataCacher;
import com.pigicial.wikirenderer.screen.owo.container.FlowLayout;
import com.pigicial.wikirenderer.screen.owo.container.UIContainers;
import com.pigicial.wikirenderer.screen.owo.core.Positioning;
import com.pigicial.wikirenderer.screen.owo.core.Sizing;
import com.pigicial.wikirenderer.screen.owo.hud.Hud;
import com.pigicial.wikirenderer.screen.owo.renderstate.OwoItemElementRenderState;
import com.pigicial.wikirenderer.screen.owo.util.NinePatchTexture;
import com.pigicial.wikirenderer.util.DrawType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Environment(EnvType.CLIENT)
public class WikiRenderer implements ClientModInitializer {

	public static final String MOD_ID = "wikirenderer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String VERSION = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata().getVersion().getFriendlyString();

    // forces OIT to off (makes things simpler)
    public static final SubmitNodeStorage NODE_STORAGE = new SubmitNodeStorage();

    public static AnimationHandler currentAnimationHandler = null;
    public static ParticleDisplayCondition particleDisplayCondition = ParticleDisplayCondition.SHOW_ALL;
    public static DrawType currentDrawType = null;

    public static List<Integer> animationTimingDataRequestedToFill = null;
    public static MeshWorldOverrides currentWorldOverrides;
    public static boolean inAreaRenderDraw = false;
    public static boolean inEntityDraw = false;
    public static boolean inSpriteEntityDraw = false;
    public static boolean inContainerScreenDraw = false;
    public static boolean inGuiItemAtlasDraw = false;
    public static boolean inGuiPictureInPictureDraw = false;
    public static boolean inRenderableDraw = false;
    public static boolean inRenderableTick = false;
    public static boolean inBatchRender = false;
    public static boolean skipWorldRender = false;
    public static boolean skipTooltipBackgroundRender = false;
    public static boolean overrideGlint = false;
    public static boolean inBoundsCalculation = false;

    public static RenderTarget mainTargetOverride = null;
	public static ProjectionType prevProjectionType = null;
	public static GpuBufferSlice prevProjectionMatrix = null;
	public static GpuBufferSlice renderableDrawProjectionBuffer = null;
    public static OrthographicSort orthographicSorting = null;
    public static Lightmap alternateLightmap = null;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(WikiRendererCommand::register);

        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                Identifier.fromNamespaceAndPath(WikiRenderer.MOD_ID, "nine_patch_metadata"),
                new NinePatchTexture.MetadataLoader()
        );

        CustomRenderPipelines.register();
        PictureInPictureRendererRegistry.register(_ -> new OwoItemElementRenderState.Renderer());

        WikiRendererKeybinds.registerKeyBinds();
        SkyBlockTimingDataCacher.getInstance().startTickEvent();

        String ioStateId = "io-state";
        String areaSelectionHintId = "area-selection-hint";

        Identifier hudId = Identifier.fromNamespaceAndPath(MOD_ID, "hud");
        Hud.add(hudId, () -> UIContainers.verticalFlow(Sizing.content(), Sizing.content()).positioning(Positioning.absolute(20, 20)));

        HudElementRegistry.addLast(hudId, (_, _) -> {
            Minecraft client = Minecraft.getInstance();
            FlowLayout isometricHud = (FlowLayout) Hud.getComponent(hudId);
            if (isometricHud == null) return;

            IOStateComponent ioState = isometricHud.childById(IOStateComponent.class, ioStateId);
            if ((ioState == null) == (FileIO.taskCount() > 0 && client.gui.screen() == null)) {
                if (FileIO.taskCount() > 0 && client.gui.screen() == null) {
                    isometricHud.child(new IOStateComponent().positioning(Positioning.absolute(20, 20)).id(ioStateId));
                } else {
                    isometricHud.removeChild(ioState);
                }
            }

            AreaSelectionComponent selectionHint = isometricHud.childById(AreaSelectionComponent.class, areaSelectionHintId);
            if ((selectionHint == null) == AreaSelectionHelper.shouldDrawOverlay()) {
                if (AreaSelectionHelper.shouldDrawOverlay()) {
                    isometricHud.child(new AreaSelectionComponent().id(areaSelectionHintId));
                } else {
                    isometricHud.removeChild(selectionHint);
                }
            }
        });
    }

	public static void beginRenderableDraw(ProjectionMatrixBuffer matrixStore, Matrix4f projectionMatrix, DrawType drawType) {
		prevProjectionType = RenderSystem.getProjectionType();
		prevProjectionMatrix = RenderSystem.getProjectionMatrixBuffer();
		renderableDrawProjectionBuffer = matrixStore.getBuffer(projectionMatrix);
		RenderSystem.setProjectionMatrix(renderableDrawProjectionBuffer, ProjectionType.ORTHOGRAPHIC);
		inRenderableDraw = true;
        currentDrawType = drawType;
    }

    public static void setSortingMethod(Matrix4f projectionMatrix, Matrix4fStack modelViewStack) {
        orthographicSorting = new OrthographicSort(projectionMatrix, modelViewStack);
    }

	public static void endRenderableDraw() {
		RenderSystem.setProjectionMatrix(prevProjectionMatrix, prevProjectionType);
		prevProjectionType = null;
		prevProjectionMatrix = null;
		renderableDrawProjectionBuffer = null;
		inRenderableDraw = false;
        orthographicSorting = null;
        currentDrawType = null;
    }
}
