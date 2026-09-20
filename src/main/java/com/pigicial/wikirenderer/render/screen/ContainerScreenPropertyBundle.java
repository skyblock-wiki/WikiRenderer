package com.pigicial.wikirenderer.render.screen;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.property.*;
import com.pigicial.wikirenderer.property.config.WikiRendererConfigs;
import com.pigicial.wikirenderer.render.Renderable;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.screen.WikiRendererUI;
import com.pigicial.wikirenderer.screen.components.AutoResizingLabelComponent;
import com.pigicial.wikirenderer.screen.components.DynamicComponent;
import com.pigicial.wikirenderer.screen.owo.component.LabelComponent;
import com.pigicial.wikirenderer.screen.owo.component.TextBoxComponent;
import com.pigicial.wikirenderer.screen.owo.container.FlowLayout;
import com.pigicial.wikirenderer.screen.owo.core.Color;
import com.pigicial.wikirenderer.screen.owo.core.Insets;
import com.pigicial.wikirenderer.screen.owo.core.Sizing;
import com.pigicial.wikirenderer.util.DrawType;
import com.pigicial.wikirenderer.util.Translate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4fStack;

public class ContainerScreenPropertyBundle extends DefaultCroppablePropertyBundle implements SerializablePropertyBundle {

    public static final ContainerScreenPropertyBundle INSTANCE = WikiRendererConfigs.loadOrDefault(new ContainerScreenPropertyBundle());

    public final IntProperty exportGuiScale = IntProperty.of(3, 1, 20);
    public final IntProperty previewGuiScale = IntProperty.of(1, 1, 5); // arbitrary max, gets re-calculated later
    public final Property<Boolean> hideItems = Property.of(false);
    public final Property<Boolean> hideText = Property.of(false);

    @Override
    public boolean allowForRescaling() {
        return false;
    }

    @Override
    public String getConfigFileName() {
        return "container_screen_render_settings";
    }

    @Override
    public void buildMainGUIControls(Renderable<?> renderable, RenderScreen screen, FlowLayout container) {
        WikiRendererUI.text(container, "screen_render_options", 10);

        Window window = Minecraft.getInstance().getWindow();
        int width = window.getWidth();
        int height = window.getHeight();

        previewGuiScale.setMaxValue(ContainerScreenRenderable.determineScaleFromResolution(width, height));
        WikiRendererUI.intControl(screen, container, previewGuiScale, "preview_gui_scale");
        WikiRendererUI.booleanControl(container, hideItems, "hide_items");
        WikiRendererUI.booleanControl(container, hideText, "hide_text");
    }

    @Override
    public void buildExportResolutionGUIControls(Renderable<?> r, RenderScreen screen, FlowLayout container) {
        ContainerScreenRenderable renderable = (ContainerScreenRenderable) r;

        TextBoxComponent resolutionField = WikiRendererUI.labelledTextField(container, String.valueOf(this.exportGuiScale.get()), "gui_scale_resolution", Sizing.fixed(28));
        resolutionField.setFilter(s -> s.matches("\\d{0,3}"));
        resolutionField.onChanged().subscribe(s -> {
            if (s.isBlank()) return;
            int scale = Integer.parseInt(s);

            int[] resolutionWidthHeight = renderable.previewScreenSizeData.getWidthAndHeightForHigherScale(scale);
            int resolution = Math.max(resolutionWidthHeight[0], resolutionWidthHeight[1]);

            if ((scale < 1 || resolution >= RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize()) && !GlobalProperties.get().unsafe.get()) {
                screen.exportButton.active = false;
            } else {
                this.exportGuiScale.set(scale);
                screen.exportButton.active = true;
            }
        });

        LabelComponent label = new AutoResizingLabelComponent(Translate.gui("unicode_font_mismatched_scale_notice"));
        label.color(Color.ofFormatting(ChatFormatting.GRAY));
        label.margins(Insets.of(2).withTop(6));
        container.child(new DynamicComponent(label, () -> Minecraft.getInstance().isEnforceUnicode() && this.exportGuiScale.get() % 2 != 0));
    }

    @Override
    public void applyToViewMatrix(Renderable<?> renderable, Matrix4fStack modelViewStack) {
        int framebufferWidth = WikiRenderer.mainTargetOverride.width;
        int framebufferHeight = WikiRenderer.mainTargetOverride.height;
        int guiScale = WikiRenderer.currentDrawType == DrawType.PREVIEW
                ? this.previewGuiScale.get()
                : this.exportGuiScale.get();

        int width = (int) (framebufferWidth / (double) guiScale);
        int screenWidth = framebufferWidth / (double) guiScale > width ? width + 1 : width;
        int height = (int) (framebufferHeight / (double) guiScale);
        int screenHeight = framebufferHeight / (double) guiScale > height ? height + 1 : height;

        double aspectRatio  = screenWidth / (double) screenHeight;
        modelViewStack.scale((float) ((2.0d * aspectRatio) / screenWidth), (float) (2.0d / screenHeight), 1.0f);

        modelViewStack.translate(-screenWidth / 2.0f, -screenHeight / 2.0f, 0.0f);
    }
}
