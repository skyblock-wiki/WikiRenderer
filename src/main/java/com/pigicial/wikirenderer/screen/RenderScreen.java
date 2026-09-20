package com.pigicial.wikirenderer.screen;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.property.*;
import com.pigicial.wikirenderer.property.config.WikiRendererConfigs;
import com.pigicial.wikirenderer.render.DefaultRenderable;
import com.pigicial.wikirenderer.render.Renderable;
import com.pigicial.wikirenderer.render.TickingRenderable;
import com.pigicial.wikirenderer.render.area.AreaRenderable;
import com.pigicial.wikirenderer.render.area.side_view.MinimapCalibratorData;
import com.pigicial.wikirenderer.render.batch.BatchPropertyBundle;
import com.pigicial.wikirenderer.render.export.ExportPathSpec;
import com.pigicial.wikirenderer.render.export.FileIO;
import com.pigicial.wikirenderer.render.export.HeadTextureTextExporter;
import com.pigicial.wikirenderer.render.export.RenderableDispatcher;
import com.pigicial.wikirenderer.render.export.animation.AnimationFormat;
import com.pigicial.wikirenderer.render.export.animation.AnimationHandler;
import com.pigicial.wikirenderer.render.export.animation.MemoryGuard;
import com.pigicial.wikirenderer.render.export.animation.ffmpeg.FFmpegAnimationHandler;
import com.pigicial.wikirenderer.render.export.animation.ffmpeg.FFmpegAnimationHandlingMode;
import com.pigicial.wikirenderer.render.export.animation.ffmpeg.FFmpegDispatcher;
import com.pigicial.wikirenderer.render.export.animation.ffmpeg.live.LiveRenderFFmpegFFmpegAnimationHandler;
import com.pigicial.wikirenderer.render.export.animation.gifski.GifskiDispatcher;
import com.pigicial.wikirenderer.render.export.animation.gifski.MemoryBasedGifskiAnimationHandler;
import com.pigicial.wikirenderer.render.item.AnimationTimingsProvider;
import com.pigicial.wikirenderer.render.particle.ParticleDisplayCondition;
import com.pigicial.wikirenderer.render.skyblock.frame_based.DyedArmorFrameBasedRenderable;
import com.pigicial.wikirenderer.render.skyblock.frame_based.FrameBasedRenderable;
import com.pigicial.wikirenderer.render.skyblock.frame_based.ItemFrameBasedRenderable;
import com.pigicial.wikirenderer.screen.components.IOStateComponent;
import com.pigicial.wikirenderer.screen.components.NonResettingScrollContainer;
import com.pigicial.wikirenderer.screen.components.NotificationComponent;
import com.pigicial.wikirenderer.screen.owo.base.BaseOwoScreen;
import com.pigicial.wikirenderer.screen.owo.component.ButtonComponent;
import com.pigicial.wikirenderer.screen.owo.component.TextBoxComponent;
import com.pigicial.wikirenderer.screen.owo.container.FlowLayout;
import com.pigicial.wikirenderer.screen.owo.container.ScrollContainer;
import com.pigicial.wikirenderer.screen.owo.container.UIContainers;
import com.pigicial.wikirenderer.screen.owo.core.*;
import com.pigicial.wikirenderer.screen.owo.util.FocusHandler;
import com.pigicial.wikirenderer.textures.TextureDataProvider;
import com.pigicial.wikirenderer.util.Translate;
import com.pigicial.wikirenderer.util.compatibility.ShaderCheck;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix4fStack;
import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class RenderScreen extends BaseOwoScreen<FlowLayout> {

    private static final Int2ObjectMap<Consumer<DefaultPropertyBundle>> KEYBOARD_CONTROLS = new Int2ObjectOpenHashMap<>();

    static {
        KEYBOARD_CONTROLS.put(InputConstants.KEY_W, properties -> properties.yOffset.modify(-1000));
        KEYBOARD_CONTROLS.put(InputConstants.KEY_S, properties -> properties.yOffset.modify(1000));
        KEYBOARD_CONTROLS.put(InputConstants.KEY_D, properties -> properties.xOffset.modify(1000));
        KEYBOARD_CONTROLS.put(InputConstants.KEY_A, properties -> properties.xOffset.modify(-1000));

        KEYBOARD_CONTROLS.put(InputConstants.KEY_UP, properties -> properties.modifySlant(-5D));
        KEYBOARD_CONTROLS.put(InputConstants.KEY_DOWN, properties -> properties.modifySlant(5D));
        KEYBOARD_CONTROLS.put(InputConstants.KEY_LEFT, properties -> properties.modifyRotation(-10));
        KEYBOARD_CONTROLS.put(InputConstants.KEY_RIGHT, properties -> properties.modifyRotation(10));

        KEYBOARD_CONTROLS.put(InputConstants.KEY_RBRACKET, properties -> properties.scale.modify(10));
        KEYBOARD_CONTROLS.put(InputConstants.KEY_SLASH, properties -> properties.scale.modify(-10));
    }

    public final MemoryGuard memoryGuard = new MemoryGuard(0.75f);
    public final long creationTimeMs = System.currentTimeMillis();

    private final FlowLayout notificationArea = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
    private final IOStateComponent ioStateComponent = new IOStateComponent();

    private final FlowLayout leftAnchor = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
    private final FlowLayout rightAnchor = UIContainers.verticalFlow(Sizing.content(), Sizing.content());

    private final FlowLayout leftColumn = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(-4);
    private final FlowLayout rightColumn = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(-4);

    private final Set<Property<?>> propertyListeners = new HashSet<>();

    public Renderable<?> renderable;

    private boolean drawOnlyBackground = false;
    public boolean captureScheduled = false;
    public boolean capturing = false;
    public boolean guiRebuildScheduled = false;

    public int viewportBeginX;
    public int viewportEndX;
    public boolean hasBothColumns = false;

    public boolean openingFile = false;
    @Nullable public ButtonComponent exportButton = null;
    @Nullable public Button exportAnimationButton = null;
    @Nullable public AnimationHandler currentAnimationExportData = null;
    @Nullable public Button refreshCustomFFmpegPathButton;

    @Nullable private TextBoxComponent animationFramesField;
    @Nullable private TextBoxComponent animationFramerateField;

    public TextBoxComponent fileNameField = null;
    private double[] scrollOffsetData = null;
    public int mouseX;
    public int mouseY;
    private boolean clickedOnceBefore; // for some reason in 26.3 dragging is occurring even when you haven't clicked yet, this is kind of a fix for that

    private AbstractContainerScreen<?> previouslyOpenedContainerScreen = null;

    public RenderScreen(Renderable<?> renderable) {
        this.renderable = renderable;
        this.memoryGuard.update();
    }

    public void setPreviouslyOpenedContainerScreen(AbstractContainerScreen<?> previouslyOpenedContainerScreen) {
        this.previouslyOpenedContainerScreen = previouslyOpenedContainerScreen;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::horizontalFlow);
    }

    @Override
    protected void init() {
        this.viewportBeginX = (int) ((this.width - this.height) * 0.5);
        this.viewportEndX = (int) (this.width - (this.width - this.height) * 0.5) + 1;

        if (!guiRebuildScheduled) {
            this.saveScrollOffsetDataIfPossible();
        }
        propertyListeners.clear();

        this.leftAnchor.clearChildren();
        this.rightAnchor.clearChildren();

        if (this.viewportBeginX < 175) {
            this.viewportEndX -= this.viewportBeginX;
            this.viewportBeginX = 0;
            this.hasBothColumns = false;

            this.leftAnchor.horizontalSizing(Sizing.fixed(0)).verticalSizing(Sizing.fixed(this.height));
            this.rightAnchor.positioning(Positioning.absolute(viewportEndX + 5, 0)).horizontalSizing(Sizing.fixed(this.width - this.viewportEndX - 5)).verticalSizing(Sizing.fixed(this.height));

            this.rightAnchor.child(new NonResettingScrollContainer(ScrollContainer.ScrollDirection.VERTICAL, Sizing.fill(100), Sizing.fill(100), (FlowLayout) UIContainers.verticalFlow(Sizing.content(), Sizing.content(10))
                    .child(leftColumn)
                    .child(rightColumn)
                    .horizontalAlignment(HorizontalAlignment.CENTER))

            );
        } else {
            this.hasBothColumns = true;

            this.leftAnchor.horizontalSizing(Sizing.fixed(viewportBeginX)).verticalSizing(Sizing.fixed(this.height));
            this.rightAnchor.positioning(Positioning.absolute(viewportEndX, 0)).horizontalSizing(Sizing.fixed(viewportBeginX)).verticalSizing(Sizing.fixed(this.height));

            this.leftAnchor.child(new NonResettingScrollContainer(ScrollContainer.ScrollDirection.VERTICAL, Sizing.fill(100), Sizing.fill(100), UIContainers.verticalFlow(Sizing.content(), Sizing.content(10)).child(this.leftColumn)));
            this.rightAnchor.child(new NonResettingScrollContainer(ScrollContainer.ScrollDirection.VERTICAL, Sizing.fill(100), Sizing.fill(100), UIContainers.verticalFlow(Sizing.content(), Sizing.content(10)).child(this.rightColumn)));
        }

        this.notificationArea.positioning(Positioning.absolute(this.viewportBeginX + 5, 5)).sizing(Sizing.fixed(this.height - 10));

        super.init();
        this.applyScrollData();
    }

    private void saveScrollOffsetDataIfPossible() {
        boolean hasScrollData = false;
        double leftScrollStep = 0;
        if (!this.leftAnchor.children().isEmpty()) {
            NonResettingScrollContainer container = (NonResettingScrollContainer) this.leftAnchor.children().getFirst();
            if (!container.children().isEmpty()) {
                hasScrollData = true;
                leftScrollStep = ((NonResettingScrollContainer) this.leftAnchor.children().getFirst()).getScrollOffset();
            }
        }

        double rightScrollStep = 0;
        if (!this.rightAnchor.children().isEmpty()) {
            NonResettingScrollContainer container = (NonResettingScrollContainer) this.rightAnchor.children().getFirst();
            if (!container.children().isEmpty()) {
                hasScrollData = true;
                rightScrollStep = ((NonResettingScrollContainer) this.rightAnchor.children().getFirst()).getScrollOffset();
            }
        }

        if (hasScrollData) {
            this.scrollOffsetData = new double[]{leftScrollStep, rightScrollStep};
        }
    }

    public void applyScrollData() {
        if (this.scrollOffsetData != null) {
            double leftScrollStep = this.scrollOffsetData[0];
            double rightScrollStep = this.scrollOffsetData[1];
            if (!this.leftAnchor.children().isEmpty() && leftScrollStep != 0) {
                ((NonResettingScrollContainer) this.leftAnchor.children().getFirst()).setScrollPosition(leftScrollStep);
            }
            if (!this.rightAnchor.children().isEmpty() && rightScrollStep != 0) {
                ((NonResettingScrollContainer) this.rightAnchor.children().getFirst()).setScrollPosition(rightScrollStep);
            }
            this.scrollOffsetData = null;
        }
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        WikiRenderer.particleDisplayCondition = this.renderable.getParticleDisplayCondition();

        this.leftColumn.margins(Insets.top(12));
        this.rightColumn.margins(Insets.top(12));

        for (Property<?> propertyListener : propertyListeners) {
            propertyListener.removeListeners(this);
        }

        rootComponent.child(this.leftAnchor.padding(Insets.left(10)).positioning(Positioning.absolute(0, 0)));
        rootComponent.child(this.rightAnchor.padding(Insets.left(10)));

        rootComponent.child(this.notificationArea.child(this.ioStateComponent.positioning(Positioning.relative(0, 100)))
                .horizontalAlignment(HorizontalAlignment.RIGHT)
                .verticalAlignment(VerticalAlignment.BOTTOM)
                .padding(Insets.of(5))
        );

        this.renderable.getProperties().buildMainGUIControls(this.renderable, this, this.leftColumn);

        if (renderable instanceof TextureDataProvider textureProvider) {
            textureProvider.buildTextureGrabSection(this, leftColumn);
        }

        WikiRendererUI.text(rightColumn, "render_options", false);
        this.buildDefaultRenderOptionsGUIControls();
        this.renderable.getProperties().buildRenderOptionGUIControls(this.renderable, this, this.rightColumn);

        WikiRendererUI.text(rightColumn, "export_options", true);
        this.renderable.getProperties().buildExportOptionGUIControls(this.renderable, this, this.rightColumn);
        this.renderable.getProperties().buildExportResolutionGUIControls(this.renderable, this, this.rightColumn);
        this.renderable.getProperties().buildFileNameGUIControls(this.renderable, this, this.rightColumn);

        WikiRendererUI.text(rightColumn, "animation_options", true);
        this.buildAnimationSection();
    }

    private void buildDefaultRenderOptionsGUIControls() {
        GlobalProperties globalProperties = GlobalProperties.get();

        TextBoxComponent colorField = WikiRendererUI.labelledTextField(rightColumn, "#000000", "background_color", Sizing.fixed(50));
        colorField.setFilter(s -> s.matches("^#([A-Fa-f\\d]{0,6})$"));
        colorField.setValue(String.format("#%06x", globalProperties.backgroundColor & 0xFFFFFF));
        colorField.moveCursorToStart(false);
        colorField.onChanged().subscribe(s -> {
            String text = s.startsWith("#") ? s.substring(1) : s;
            if (text.length() < 6) {
                return;
            }

            globalProperties.backgroundColor = Integer.parseInt(s.substring(1), 16) | 0xFF000000;
        });

        WikiRendererUI.booleanControl(rightColumn, globalProperties.showBackgroundColorInExports, "show_background_color_in_exports");
        WikiRendererUI.booleanControl(rightColumn, globalProperties.tickTextureAnimations, "texture_animations");
        WikiRendererUI.conditionalBooleanControl(rightColumn, globalProperties.syncTextureAnimationsToAnimation, "sync_texture_animations", globalProperties.tickTextureAnimations::get);
    }

    private void buildFFmpegCustomPathSection() {
        GlobalProperties globalProperties = GlobalProperties.get();
        WikiRendererUI.booleanControl(rightColumn, globalProperties.useCustomFFmpegPath, "use_custom_ffmpeg_path");
        globalProperties.useCustomFFmpegPath.addRebuildListener(this);
        globalProperties.useCustomFFmpegPath.futureListen(this, (_, value) -> {
            if (value && globalProperties.customFFmpegPath.isBlank()) return; // turning on for first time, don't check
            this.detectFFmpeg(true);
        });

        if (globalProperties.useCustomFFmpegPath.get()) {
            TextBoxComponent editBox = WikiRendererUI.labelledTextField(rightColumn, globalProperties.customFFmpegPath, "custom_ffmpeg_path", Sizing.expand(80));
            editBox.onChanged().subscribe(path -> globalProperties.customFFmpegPath = path);

            try (WikiRendererUI.RowBuilder builder = WikiRendererUI.autoNewLineRow(rightColumn)) {
                this.refreshCustomFFmpegPathButton = WikiRendererUI.button(Translate.gui("check_ffmpeg_path"), _ -> this.detectFFmpeg(true));
                builder.row.child(refreshCustomFFmpegPathButton);

                WikiRendererUI.dynamicText(builder.row, () -> {
                    MutableComponent meshStatusText;
                    meshStatusText = Translate.gui(switch (FFmpegDispatcher.customPathState) {
                        case NOT_CHECKED -> "ffmpeg_custom_path_not_checked";
                        case CHECKING -> "ffmpeg_custom_path_checking";
                        case FOUND -> "ffmpeg_custom_path_found";
                        case NOT_FOUND -> "ffmpeg_custom_path_not_found";
                    }).withStyle(FFmpegDispatcher.customPathState.getColor());

                    return meshStatusText;
                }).margins(Insets.of(6, 0, 8, 0));
            }
        }
    }

    private void detectFFmpeg(boolean bypass) {
        if (bypass) {
            if (currentAnimationExportData != null) return;
            FFmpegDispatcher.tryCustomPathAgain = true;
        }
        FFmpegDispatcher.detectFFmpeg().whenComplete((_, throwable) -> {
            this.guiRebuildScheduled = true;
            if (throwable != null) {
                this.minecraft.execute(() -> this.notify(
                        Translate.gui("ffmpeg_check_error").withStyle(ChatFormatting.RED),
                        Component.literal(String.valueOf(throwable.getMessage())).withStyle(ChatFormatting.GRAY)
                ));
            }
        });
    }

    private boolean buildGifskiLoadingOrFailedSection() {
        if (!GifskiDispatcher.wasGifskiCopiedToTempPath()) {
            WikiRendererUI.text(rightColumn, "copying_gifski", false);
            GifskiDispatcher.createGifskiTemporaryPath().whenComplete((_, throwable) -> {
                this.guiRebuildScheduled = true;
                if (throwable != null) {
                    this.minecraft.execute(() -> this.notify(
                            Translate.gui("no_gifski").withStyle(ChatFormatting.RED),
                            Component.literal(String.valueOf(throwable.getMessage())).withStyle(ChatFormatting.GRAY)
                    ));
                }
            });
            return false;
        }

        if (!GifskiDispatcher.isGifskiAvailable()) {
            WikiRendererUI.text(rightColumn, "no_gifski", true);
            return false;
        }

        return true;
    }

    private boolean buildFFmpegLoadingOrRequirementsSections() {
        if (!FFmpegDispatcher.wasFFmpegDetected()) {
            WikiRendererUI.text(rightColumn, "detecting_ffmpeg", 5);
            this.buildFFmpegCustomPathSection();
            this.detectFFmpeg(false);
            return false;
        }

        if (!FFmpegDispatcher.ffmpegAvailable()) {
            WikiRendererUI.text(rightColumn, "no_ffmpeg_1", 5);
            WikiRendererUI.text(rightColumn, "no_ffmpeg_2", false);
            WikiRendererUI.text(rightColumn, "no_ffmpeg_3", false)
                    .cursorStyle(CursorStyle.HAND)
                    .mouseDown().subscribe((_, _) -> {
                        this.minecraft.setScreenAndShow(new ConfirmLinkScreen(confirmed -> {
                            if (confirmed) {
                                Blaze3D.openUri(URI.create("https://ffmpeg.org/download.html"));
                            }

                            this.minecraft.setScreenAndShow(this);
                        }, URI.create("https://ffmpeg.org/download.html"), true));
                        return true;
                    });
            this.buildFFmpegCustomPathSection();
            return false;
        }

        return true;
    }

    private void buildAnimationSection() {
        if (ShaderCheck.isUsingShaders()) {
            // they don't work due to LevelRendererMixin skipping the world render from skipWorldRender
            WikiRendererUI.text(rightColumn, "shader_animations_unsupported", 5);
            return;
        }

        GlobalProperties globalProperties = GlobalProperties.get();
        try (WikiRendererUI.RowBuilder builder = WikiRendererUI.autoNewLineRow(rightColumn)) {
            this.exportAnimationButton = WikiRendererUI.button(Translate.gui("export_animation"), _ -> this.queueAnimationExport());

            if (WikiRenderer.currentAnimationHandler != null) {
                exportAnimationButton.active = false;
            }
            if (renderable instanceof ItemFrameBasedRenderable frameBasedRenderable) {
                if (frameBasedRenderable.getTimingData() == null) {
                    exportAnimationButton.active = false;
                }
            }

            builder.row.child(this.exportAnimationButton.margins(Insets.right(5)));
            builder.row.child(WikiRendererUI.button(Translate.gui("format." + globalProperties.animationFormat.extension), button -> {
                globalProperties.animationFormat = globalProperties.animationFormat.next();
                button.setMessage(Translate.gui("format." + globalProperties.animationFormat.extension));
                guiRebuildScheduled = true;
            }).horizontalSizing(Sizing.fixed(35)));
        }

        WikiRendererUI.dynamicText(rightColumn, () -> {
            if (this.currentAnimationExportData == null) {
                return Component.empty();
            } else if (this.currentAnimationExportData.getRemainingFrames() > 0) {
                return Translate.gui("export_remaining_frames", this.currentAnimationExportData.getRemainingFrames());
            } else if (this.currentAnimationExportData.getCurrentFrame() != null) {
                String frame = this.currentAnimationExportData.getCurrentFrame();
                int totalFrames = currentAnimationExportData.getAnimationFrames();
                if (this.currentAnimationExportData instanceof FFmpegAnimationHandler) {
                    String exportFps = currentAnimationExportData.getCurrentFFmpegFps();
                    return Translate.gui("ffmpeg_data", frame, totalFrames, exportFps);
                } else {
                    return Translate.gui("gifski_data", frame, totalFrames);
                }
            } else {
                if (this.currentAnimationExportData instanceof LiveRenderFFmpegFFmpegAnimationHandler) {
                    return Translate.gui("setting_up_second_ffmpeg_pass");
                } else if (this.currentAnimationExportData instanceof FFmpegAnimationHandler) {
                    return Translate.gui("setting_up_ffmpeg");
                } else {
                    return Translate.gui("setting_up_gifski");
                }
            }
        });

        if (globalProperties.animationFormat == AnimationFormat.GIF) {
            if (!buildGifskiLoadingOrFailedSection()) {
                return;
            }
            WikiRendererUI.intPercentageControl(this, rightColumn, globalProperties.gifskiQuality, "gifski_quality");
            WikiRendererUI.booleanControl(rightColumn, globalProperties.saveIndividualFrames, "save_individual_frames");
        } else {
            if (!buildFFmpegLoadingOrRequirementsSections()) {
                return;
            }
        }

        WikiRendererUI.booleanControl(rightColumn, globalProperties.setAnimationFpsCap, "render_with_game_timings");
        if (renderable instanceof FrameBasedRenderable<?, ?, ?>) {
            WikiRendererUI.text(rightColumn, "frame_options_override_notice", 5);
            if (renderable instanceof DyedArmorFrameBasedRenderable && globalProperties.animationFormat == AnimationFormat.GIF) {
                WikiRendererUI.text(rightColumn, "armor_data_gif_force_crop_scale_notice", 5);
            }
        } else {
            if (renderable.getProperties() instanceof CroppablePropertyBundle croppablePropertyBundle) {
                Property<Boolean> animatedCropProperty = croppablePropertyBundle.getFFmpegCropProperty();
                WikiRendererUI.booleanControl(rightColumn, animatedCropProperty, "crop");
            }

            this.animationFramesField = WikiRendererUI.labelledTextField(rightColumn,
                    String.valueOf(globalProperties.exportFrames.get()), "animation_frames", Sizing.fixed(30));
            this.animationFramesField.setFilter(s -> WikiRenderer.currentAnimationHandler == null && s.matches("\\d*"));
            this.animationFramesField.onChanged().subscribe(s -> {
                if (!s.isBlank()) {
                    globalProperties.exportFrames.set(Integer.parseInt(s));
                }
                updateAnimationExportButtonFromFields();
            });

            this.animationFramerateField = WikiRendererUI.labelledTextField(rightColumn,
                    String.valueOf(globalProperties.exportFramerate.get()), "animation_framerate", Sizing.fixed(30));
            this.animationFramerateField.setFilter(s -> WikiRenderer.currentAnimationHandler == null && s.matches("\\d*"));
            this.animationFramerateField.onChanged().subscribe(s -> {
                if (!s.isBlank()) {
                    globalProperties.exportFramerate.set(Integer.parseInt(s));
                }
                updateAnimationExportButtonFromFields();
            });

            WikiRendererUI.booleanControl(rightColumn, globalProperties.syncEnchantmentGlintsToExport, "sync_enchantment_glints");
            WikiRendererUI.booleanControl(rightColumn, globalProperties.speedUpEnchantmentGlints, "speed_up_enchantment_glints");
            globalProperties.speedUpEnchantmentGlints.futureListen(this, (_, _) -> guiRebuildScheduled = true);

            if (globalProperties.speedUpEnchantmentGlints.get()) {
                rightColumn.child(WikiRendererUI.button(Translate.gui("enchantment_glint_preset"), _ -> {
                    int seconds = 120000 / 8000;
                    int framerate = 20;
                    globalProperties.exportFramerate.set(framerate);
                    globalProperties.exportFrames.set(seconds * framerate);
                    this.guiRebuildScheduled = true;
                }).margins(Insets.vertical(5)));
            }

            if (renderable instanceof AnimationTimingsProvider timingsProvider) {
                timingsProvider.buildTimingsSection(rightColumn);
            }
        }

        if (globalProperties.animationFormat != AnimationFormat.GIF) {
            WikiRendererUI.conditionalBooleanControl(rightColumn, globalProperties.saveIndividualFrames, "save_individual_frames", () -> globalProperties.animationHandlingMode.savesFramesToFiles());
            WikiRendererUI.dynamicText(rightColumn, () -> switch (globalProperties.animationHandlingMode) {
                case DISK_INSTANT_SAVE -> Translate.gui("animation_mode_selected_instant_file_save");
                case MEMORY_CACHE -> Translate.gui("animation_mode_selected_save_in_memory");
                case LIVE_FFMPEG -> Translate.gui("animation_mode_selected_live_ffmpeg");
            }).margins(Insets.of(10, 0, 5, 0));

            rightColumn.child(WikiRendererUI.dropdown(Sizing.content())
                    .button(Translate.gui("animation_mode_name_live_ffmpeg"), _ -> globalProperties.animationHandlingMode = FFmpegAnimationHandlingMode.LIVE_FFMPEG)
                    .text(Translate.gui("animation_mode_description_live_ffmpeg_1"))
                    .text(Translate.gui("animation_mode_description_live_ffmpeg_2"))
                    .button(Translate.gui("animation_mode_name_instant_file_save"), _ -> globalProperties.animationHandlingMode = FFmpegAnimationHandlingMode.DISK_INSTANT_SAVE)
                    .text(Translate.gui("animation_mode_description_instant_file_save_1"))
                    .text(Translate.gui("animation_mode_description_instant_file_save_2"))
                    .button(Translate.gui("animation_mode_name_save_in_memory"), _ -> globalProperties.animationHandlingMode = FFmpegAnimationHandlingMode.MEMORY_CACHE)
                    .text(Translate.gui("animation_mode_description_save_in_memory_1"))
                    .text(Translate.gui("animation_mode_description_save_in_memory_2"))

                    .closeWhenNotHovered(false)
                    .padding(Insets.of(5))
            );

            this.buildFFmpegCustomPathSection();
        }
    }

    private void updateAnimationExportButtonFromFields() {
        if (this.exportAnimationButton != null) {
            boolean valid = isFieldValid(animationFramesField, GlobalProperties.get().exportFrames) && isFieldValid(animationFramerateField, GlobalProperties.get().exportFramerate);
            this.exportAnimationButton.active = valid && WikiRenderer.currentAnimationHandler == null;
        }
    }

    private boolean isFieldValid(TextBoxComponent text, IntProperty property) {
        if (text == null) {
            return false;
        }

        String value = text.getValue();
        if (value.isBlank()) {
            return false;
        }

        try {
            int number = Integer.parseInt(value);
            return number != 0 && number >= property.min() && number <= property.max();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void queueAnimationExport() {
        renderable.onAnimationStart();

        GlobalProperties globalProperties = GlobalProperties.get();
        int framesStoreInMemory = globalProperties.animationHandlingMode.isStoredInMemory() || globalProperties.animationFormat == AnimationFormat.GIF ? globalProperties.exportFrames.get() : 1;
        if (this.memoryGuard.canFitInRam(memoryGuard.estimateMemoryMBUsage(renderable, framesStoreInMemory)) || this.minecraft.hasControlDown()) {
            this.currentAnimationExportData = globalProperties.animationFormat == AnimationFormat.GIF
                    ? new MemoryBasedGifskiAnimationHandler(this, renderable, GlobalProperties.get().exportFrames.get())
                    : globalProperties.animationHandlingMode.createAnimationHandler(this, renderable);

            WikiRenderer.currentAnimationHandler = this.currentAnimationExportData;

            if (globalProperties.setAnimationFpsCap.get()) {
                this.minecraft.getFramerateLimitTracker().setFramerateLimit(globalProperties.exportFramerate.get());
            }
            WikiRenderer.skipWorldRender = true;

            if (this.exportAnimationButton != null) {
                this.exportAnimationButton.active = false;
                this.exportAnimationButton.setMessage(Translate.gui("exporting"));
            }
            if (this.refreshCustomFFmpegPathButton != null) {
                this.refreshCustomFFmpegPathButton.active = false;
                this.refreshCustomFFmpegPathButton.setMessage(Translate.gui("exporting"));
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        if (this.guiRebuildScheduled) {
            this.saveScrollOffsetDataIfPossible();
            this.uiAdapter = null;
            this.rightColumn.clearChildren();
            this.leftColumn.clearChildren();
            this.rebuildWidgets();

            this.guiRebuildScheduled = false;
        }
        // smoother, idk why but the provided tickDelta is bad
        tickDelta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);


        Window window = minecraft.getWindow();
        Consumer<Matrix4fStack> positionTransformer = this.hasBothColumns ? null : matrixStack -> matrixStack.translate(1 - window.getWidth() / (float) window.getHeight(), 0, 0);
        if (!renderable.renderPreviewToEntireScreenWidth()) positionTransformer = null;

        RenderTarget renderedOutput = RenderableDispatcher.drawPreview(this, this.renderable, tickDelta, this.getTimeSinceCreationMs(), positionTransformer);

        GlobalProperties globalProperties = GlobalProperties.get();
        if (this.drawOnlyBackground) {
            graphics.fill(0, 0, this.width, this.height, globalProperties.backgroundColor | 255 << 24);
        } else {
            this.extractTransparentBackground(graphics);
        }

        int placementX = renderable.renderPreviewToEntireScreenWidth() ? 0 : viewportBeginX;
        int placementXEnd = renderable.renderPreviewToEntireScreenWidth() ? window.getGuiScaledWidth() : window.getGuiScaledWidth() - (width - (viewportEndX));

        graphics.guiRenderState.addBlitToCurrentLayer(new BlitRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureSetup.singleTexture(Objects.requireNonNull(renderedOutput.getColorTextureView()), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)),
                new Matrix3x2f(graphics.pose()),
                placementX,
                0,
                placementXEnd,
                window.getGuiScaledHeight(),
                0,
                1,
                1,
                0,
                -1,
                null
        ));

        // basically just for batch rendering
        this.renderable.onScreenHandle(this, graphics, tickDelta);

        if (!this.drawOnlyBackground && this.uiAdapter != null) {
            drawFramingHint(graphics);
            drawGuiBackground(graphics);

            if (this.exportAnimationButton != null) {
                int framesStoreInMemory = globalProperties.animationHandlingMode.isStoredInMemory() || globalProperties.animationFormat == AnimationFormat.GIF ? globalProperties.exportFrames.get() : 1;
                int memoryMB = memoryGuard.estimateMemoryMBUsage(renderable, framesStoreInMemory);
                List<ClientTooltipComponent> tooltip = this.memoryGuard.getStatusTooltip(memoryMB)
                        .stream()
                        .map(text -> ClientTooltipComponent.create(text.getVisualOrderText()))
                        .toList();
                this.exportAnimationButton.tooltip(tooltip);
            }

            super.extractRenderState(graphics, mouseX, mouseY, tickDelta);

            if (FileIO.taskCount() > 0) {
                if (!this.ioStateComponent.hasParent()) {
                    this.notificationArea.child(this.ioStateComponent);
                }
            } else if (this.ioStateComponent.hasParent()) {
                this.notificationArea.removeChild(this.ioStateComponent);
            }
        }

        if (this.captureScheduled) {
            this.exportImage(true, tickDelta);
            this.captureScheduled = false;
        }

        this.renderOrExportAnimationIfNecessary(tickDelta);
    }

    public void exportImage(boolean popupText, float tickDelta) {
        capturing = true;
        ExportPathSpec defaultExportPath = this.renderable.getExportPath();
        String customFileName = renderable.getCustomFileName();
        ExportPathSpec exportPath = customFileName == null || customFileName.isBlank() ? defaultExportPath : defaultExportPath.differentFileName(customFileName);

        String areaRunCommandIfPossible;
        AtomicReference<MinimapCalibratorData> minimapCalibratorData = new AtomicReference<>();
        Consumer<MinimapCalibratorData> dataConsumer = null;
        if (renderable instanceof AreaRenderable areaRenderable
            && areaRenderable.getProperties().perPixel90DegreeRendering.get()
            && areaRenderable.getProperties().exportSideViewMinimapData.get()
            && areaRenderable.getProperties().areMinimapSettingsExportable()) {

            dataConsumer = minimapCalibratorData::set;
            areaRunCommandIfPossible = areaRenderable.mesh.bounds.generateAreaCommand();
        } else {
            areaRunCommandIfPossible = null;
        }

        RenderableDispatcher.drawIntoImage(this, this.renderable, tickDelta, this.getTimeSinceCreationMs(), renderable.getExportResolution(), renderable.shouldCrop(), dataConsumer)
            .thenCompose(img -> FileIO.saveImage(img, exportPath, this.renderable.getPngTextMetadata()).whenComplete((_, _) -> img.close()))
                .whenComplete((imageFile, throwable) -> {
                    capturing = false;
                    if (throwable != null) {
                        WikiRenderer.LOGGER.error("Failed to render image", throwable);
                        this.minecraft.execute(() -> this.notify(
                                Translate.gui("export_failed").withStyle(ChatFormatting.RED),
                                Component.literal(String.valueOf(throwable.getMessage())).withStyle(ChatFormatting.GRAY)
                        ));
                        return;
                    }

                    if (popupText) {
                        this.minecraft.execute(() -> this.notify(
                                () -> Blaze3D.openPath(imageFile.toPath()),
                                Translate.gui("exported_as"),
                                Component.literal(ExportPathSpec.exportRoot().relativize(imageFile.toPath()).toString())
                        ));
                    }

                    if (minimapCalibratorData.get() != null) {
                        String fileText = minimapCalibratorData.get().toFileText(imageFile.getName(), areaRunCommandIfPossible);
                        ExportPathSpec minimapExportPath = customFileName == null || customFileName.isBlank()
                                ? defaultExportPath.differentFileName("area_render_minimap_data")
                                : defaultExportPath.differentFileName(customFileName + "_area_render_minimap_data");

                        FileIO.saveTextAndNotify(fileText, minimapExportPath, this, "exported_minimap_data_as");
                    }

                    HeadTextureTextExporter.exportIfEnabled(renderable, exportPath, this);
                });
    }

    private void renderOrExportAnimationIfNecessary(float tickDelta) {
        if (this.currentAnimationExportData != null) {
            if (!GlobalProperties.get().setAnimationFpsCap.get()) {
                // overrides tabbing out lowering the fps cap
                FramerateLimitTracker framerateLimitTracker = Minecraft.getInstance().getFramerateLimitTracker();
                framerateLimitTracker.setFramerateLimit(Minecraft.getInstance().options.framerateLimit().get());
                framerateLimitTracker.onInputReceived();
            }
            this.currentAnimationExportData.renderAndSaveFrame(tickDelta);
        }
    }

    @Override
    public void tick() {
        if (this.minecraft.level == null) return;

        if (this.minecraft.level.getGameTime() % 40 == 0) {
            this.memoryGuard.update();
        }

        if (this.renderable instanceof TickingRenderable<?> ticking) {
            WikiRenderer.inRenderableTick = true;
            ticking.tick();
            WikiRenderer.inRenderableTick = false;
        }
    }

    public void notify(@NotNull Runnable onClick, Component... messages) {
        this.notificationArea.child(0, new NotificationComponent(this, onClick, messages));
    }

    public void notify(Component... messages) {
        this.notificationArea.child(0, new NotificationComponent(this, null, messages));
    }

    private boolean isInViewport(double mouseX) {
        return mouseX > viewportBeginX && mouseX < viewportEndX;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (!clickedOnceBefore) return false;
        if (!(this.renderable.getProperties() instanceof DefaultPropertyBundle properties))
            return super.mouseDragged(click, offsetX, offsetY);

        if (this.isInViewport(click.x())) {
            int button = click.button();
            if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
                double xScaling = (100d / properties.scale.get()) * (this.minecraft.getWindow().getScreenWidth() / (float) this.minecraft.getWindow().getGuiScaledWidth());
                double yScaling = (100d / properties.scale.get()) * (this.minecraft.getWindow().getScreenHeight() / (float) this.minecraft.getWindow().getGuiScaledHeight());

                properties.xOffset.modify((int) (50 * offsetX * xScaling));
                properties.yOffset.modify((int) (50 * offsetY * yScaling));
                return true;
            } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                properties.modifyRotation((int) (offsetX * 2));
                return true;
            } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                properties.modifySlant(offsetY * 2);
                return true;
            }
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent click, boolean doubled) {
        this.clickedOnceBefore = true;
        if (!(this.renderable.getProperties() instanceof DefaultPropertyBundle properties))
            return super.mouseClicked(click, doubled);

        boolean clickHandled = super.mouseClicked(click, doubled);
        if (this.isInViewport(click.x())) {
            if (this.openingFile) {
                this.openingFile = false;
                return true;
            }

            if (renderable.onScreenViewportClick(click, doubled)) {
                return true;
            }

            if (click.hasControlDown()) {
                int button = click.button();
                if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
                    properties.xOffset.setToDefault();
                    properties.yOffset.setToDefault();
                } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                    properties.rotation.setToDefault();
                } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                    properties.slant.setToDefault();
                }
                return true;
            }
        }

        this.openingFile = false;
        return clickHandled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.renderable.getProperties() instanceof DefaultPropertyBundle properties) {
            if (this.isInViewport(mouseX)) {
                properties.modifyScale((int) (verticalAmount * Math.max(1, properties.getUsedScale() * 0.075)));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (super.keyPressed(input)) return true;

        int keyCode = input.key();

        if (keyCode == InputConstants.KEY_F12) {
            this.captureScheduled = true;
        } else if (keyCode == InputConstants.KEY_F10) {
            this.drawOnlyBackground = !this.drawOnlyBackground;
        } else if (KEYBOARD_CONTROLS.containsKey(keyCode) && this.renderable instanceof DefaultRenderable) {
            FocusHandler focusHandler = this.uiAdapter.rootComponent.focusHandler();
            if (focusHandler != null && focusHandler.focused() instanceof EditBox) {
                return true;
            }

            KEYBOARD_CONTROLS.get(keyCode).accept((DefaultPropertyBundle) this.renderable.getProperties());
        }

        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        if (this.previouslyOpenedContainerScreen != null) {
            this.previouslyOpenedContainerScreen.onClose();
            this.previouslyOpenedContainerScreen = null;
        }
    }

    @Override
    public void removed() {
        WikiRenderer.particleDisplayCondition = ParticleDisplayCondition.SHOW_ALL;
        this.minecraft.getFramerateLimitTracker().setFramerateLimit(this.minecraft.options.framerateLimit().get());

        PropertyBundle properties = renderable.getProperties();
        if (renderable instanceof BatchPropertyBundle batchPropertyBundle) {
            properties = batchPropertyBundle.getActualProperties();
        }
        if (properties instanceof SerializablePropertyBundle serializableProperties) {
            WikiRendererConfigs.save(serializableProperties);
        }
        WikiRendererConfigs.save(GlobalProperties.get());

        if (ScreenSchedulerAndSaver.getScheduledScreen() == null) {
            ScreenSchedulerAndSaver.setSavedScreen(this);
        } else {
            // discrd is called for any saved screens in schedule, but ignore that if this is the saved screen and is being reopened
            this.renderable.dispose();
        }

        if (this.currentAnimationExportData != null) {
            this.currentAnimationExportData.close();
            this.currentAnimationExportData = null;
            WikiRenderer.currentAnimationHandler = null;
        }
        if (this.exportAnimationButton != null) {
            this.exportAnimationButton.active = true;
            this.exportAnimationButton.setMessage(Translate.gui("export_animation"));
        }
        if (this.refreshCustomFFmpegPathButton != null) {
            this.refreshCustomFFmpegPathButton.active = true;
            this.refreshCustomFFmpegPathButton.setMessage(Translate.gui("check_ffmpeg_path"));
        }

        this.uiAdapter = null;
        this.rightColumn.clearChildren();
        this.leftColumn.clearChildren();
        this.guiRebuildScheduled = true;

        for (Property<?> propertyListener : propertyListeners) {
            propertyListener.removeListeners(this);
        }
        propertyListeners.clear();
    }

    private void drawFramingHint(GuiGraphicsExtractor context) {
        context.fill(viewportBeginX, 0, viewportEndX, 0, 0x90000000);
        context.fill(viewportBeginX, height, viewportEndX, height, 0x90000000);
        context.fill(viewportBeginX, 0, viewportBeginX, height, 0x90000000);
        context.fill(viewportEndX, 0, viewportEndX, height, 0x90000000);
    }

    private void drawGuiBackground(GuiGraphicsExtractor context) {
        context.fill(0, 0, viewportBeginX, height, 0x90000000);
        context.fill(viewportEndX, 0, width, height, 0x90000000);
    }

    public void registerPropertyListener(Property<?> property) {
        this.propertyListeners.add(property);
    }

    public long getTimeSinceCreationMs() {
        return System.currentTimeMillis() - creationTimeMs;
    }
}
