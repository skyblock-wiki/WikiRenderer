package com.pigicial.wikirenderer.render.area;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.pigicial.wikirenderer.components.AutoResizingLabelComponent;
import com.pigicial.wikirenderer.components.ConditionalButton;
import com.pigicial.wikirenderer.components.SearchableEntityListComponent;
import com.pigicial.wikirenderer.mixin.access.LivingEntityRendererAccessor;
import com.pigicial.wikirenderer.property.*;
import com.pigicial.wikirenderer.property.config.WikiRendererConfigs;
import com.pigicial.wikirenderer.render.Renderable;
import com.pigicial.wikirenderer.render.area.bounds.ExpandableMeshBounds;
import com.pigicial.wikirenderer.render.area.side_view.ExpansionSide;
import com.pigicial.wikirenderer.render.area.side_view.MeshSideRotation;
import com.pigicial.wikirenderer.render.area.side_view.MeshSideSlant;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.screen.WikiRendererUI;
import com.pigicial.wikirenderer.util.ClipboardUtil;
import com.pigicial.wikirenderer.util.Translate;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AreaPropertyBundle extends DefaultCroppablePropertyBundle implements SerializablePropertyBundle {

    public static final AreaPropertyBundle INSTANCE = WikiRendererConfigs.loadOrDefault(new AreaPropertyBundle());

    public final Property<Boolean> perPixel90DegreeRendering = Property.of(false);
    public MeshSideRotation sideViewRotation = MeshSideRotation.NORTH;
    public MeshSideSlant sideViewSlant = MeshSideSlant.ABOVE;

    public final Property<Boolean> exportSideViewMinimapData = Property.of(true);
    public final Property<Boolean> halfPixelOffsetFor4x4 = Property.of(true); // helps fix certain things like fence lines not rendering
    private int pixelsPerBlockResolution = 16;
    private transient int faceRenderingActualResolution = this.getDefaultExportResolution();

    public final Property<Boolean> emulateDaylight = Property.of(true);
    public final Property<Boolean> useFullBrightGamma = Property.of(false);
    public final Property<Boolean> useNightVision = Property.of(false);
    public final Property<Boolean> lightUpSurroundingBlocks = Property.of(true);

    public final Property<Boolean> useWalkabilityFilter = Property.of(false);
    public final IntProperty dontSearchForHigherFloorsThreshold = IntProperty.of(255, 0, 255);
    public final IntProperty walkableBlocksThreshold = IntProperty.of(2, 1, 20);
    public final Property<Boolean> requireCeilingForCaveMode = Property.of(false);
    public final Property<Boolean> includeWallsForCaveMode = Property.of(false);
    public final Property<Boolean> includeCornersWhenIncludingWalls = Property.of(false);
    public final Property<Boolean> showMeshExpansionControls = Property.of(false);

    public final Property<Boolean> freezeBlocks = Property.of(false);
    public final Property<Boolean> hideMesh = Property.of(false);
    public final Property<Boolean> hideFluids = Property.of(false);
    public final Property<Boolean> hideBeaconBeams = Property.of(false);

    public final Property<Boolean> hideEntities = Property.of(false);
    public final Property<Boolean> hideLivingEntities = Property.of(false);
    public final Property<Boolean> showHiddenEntitiesList = Property.of(false);
    public transient String entityTypeSearch = "Visible";
    public final transient List<EntityType<?>> hiddenEntityTypes = new ArrayList<>();

    public final IntProperty entityBoundsIntersectionRequirement = IntProperty.of(20, 0, 100);

    public AreaEntityRefreshMode entityRefreshMode = AreaEntityRefreshMode.REFRESHING_WITHIN_BOUNDS;

    public final Property<Boolean> hideText = Property.of(false);
    public final Property<Boolean> hideNametags = Property.of(false);

    public final Property<Boolean> freezePlayerArms = Property.of(false);
    public final Property<Boolean> overrideEntityRotations = Property.of(false);
    public final IntProperty entityYawOverride = IntProperty.of(0, -180, 180).withRollover();
    public final IntProperty entityPitchOverride = IntProperty.of(0, -180, 180).withRollover();
    public final IntProperty entityRotationOverride = IntProperty.of(0, -180, 180).withRollover();
    public final Property<Boolean> useSteveSkinForEntities = Property.of(false);
    public final Property<Boolean> hideHeldItemsForEntities = Property.of(false);
    public final Property<Boolean> hideArmorForEntities = Property.of(false);
    public final Property<Boolean> hideEnchantmentsForEntities = Property.of(false);
    public final Property<Boolean> toggleInvisibilityForEntities = Property.of(false); // idk what this is for but its a requested option
    public final Property<Boolean> forceSmallArmsForEntities = Property.of(false);

    @Override
    public String getConfigFileName() {
        return "area_render_settings";
    }

    public boolean areMinimapSettingsExportable() {
        return sideViewRotation == MeshSideRotation.NORTH && sideViewSlant == MeshSideSlant.ABOVE;
    }

    @Override
    protected boolean allowRotatingWithMouseByDefault() {
        return true;
    }

    @Override
    public void setExportResolution(Renderable<?> renderable, int exportResolution) {
        if (perPixel90DegreeRendering.get()) {
            this.faceRenderingActualResolution = exportResolution;
        } else {
            super.setExportResolution(renderable, exportResolution);
        }
    }

    @Override
    public int getExportResolution(Renderable<?> renderable) {
        if (this.perPixel90DegreeRendering.get()) {
            return this.faceRenderingActualResolution;
        } else {
            return super.getExportResolution(renderable);
        }
    }

    public int getPixelsPerBlockResolution() {
        return pixelsPerBlockResolution;
    }

    public void setPixelsPerBlockResolution(int pixelsPerBlockResolution) {
        this.pixelsPerBlockResolution = pixelsPerBlockResolution;
    }

    @Override
    public float getUsedRotation() {
        return this.perPixel90DegreeRendering.get() ? this.sideViewRotation.getRotationDegrees() : super.getUsedRotation();
    }

    @Override
    public double getUsedSlant() {
        return this.perPixel90DegreeRendering.get() ? this.sideViewSlant.getRotationDegrees() : super.getUsedSlant();
    }

    @Override
    public void modifyRotation(int amount) {
        if (this.perPixel90DegreeRendering.get()) return;
        super.modifyRotation(amount);
    }

    @Override
    public void modifySlant(double amount) {
        if (this.perPixel90DegreeRendering.get()) return;
        super.modifySlant(amount);
    }

    @Override
    public void buildMainGUIControls(Renderable<?> r, RenderScreen screen, FlowLayout container) {
        AreaRenderable renderable = (AreaRenderable) r;
        WikiRendererUI.text(container, "transform_options", false);

        WikiRendererUI.booleanControl(container, this.perPixel90DegreeRendering, "per_pixel_90_degree_rendering");
        this.perPixel90DegreeRendering.futureListen(screen, (_, value) -> {
            if (value) {
                this.sideViewRotation = MeshSideRotation.NORTH;
                this.sideViewSlant = MeshSideSlant.ABOVE;
            }
            screen.guiRebuildScheduled = true;
        });

        if (!this.perPixel90DegreeRendering.get()) {
            try (WikiRendererUI.RowBuilder builder = WikiRendererUI.autoNewLineRow(container)) {
                ButtonComponent dimetricButton = WikiRendererUI.button(Translate.gui("dimetric_recommended"), _ -> {
                    this.rotation.setToDefault();
                    this.slant.set(30D);
                });
                dimetricButton.margins(dimetricButton.margins().get().add(0, 0, 0, 5));
                builder.row.child(dimetricButton);

                builder.row.child(WikiRendererUI.button(Translate.gui("isometric"), _ -> {
                    this.rotation.setToDefault();
                    this.slant.set(35.264);
                }));
            }
            WikiRendererUI.intControl(screen, container, scale, "scale");
            WikiRendererUI.intControl(screen, container, rotation, "rotation");
            WikiRendererUI.doubleControl(screen, container, slant, "slant");
            WikiRendererUI.intControl(screen, container, rotationSpeed, "rotation_speed");
            WikiRendererUI.conditionalBooleanControl(container, GlobalProperties.get().syncRotationToAnimation, "sync_rotation_to_animation", () -> !rotationSpeed.isDefault());
            WikiRendererUI.booleanControl(container, allowRotatingWithMouse, "allow_rotating_with_mouse");
            container.child(this.buildResetButton());
        } else {
            try (WikiRendererUI.RowBuilder builder = WikiRendererUI.autoNewLineRow(container)) {
                builder.row.child(WikiRendererUI.button(Translate.gui("cycle_rotation"), _ -> {
                    this.sideViewRotation = this.sideViewRotation.nextRotation();
                    screen.guiRebuildScheduled = true;
                }));

                builder.row.child(WikiRendererUI.button(Translate.gui("cycle_slant"), _ -> {
                    this.sideViewSlant = this.sideViewSlant.nextSlant();
                    screen.guiRebuildScheduled = true;
                }));
            }
            container.child(WikiRendererUI.button(Translate.gui("reset_rotation_and_slant"), _ -> {
                this.sideViewRotation = MeshSideRotation.NORTH;
                this.sideViewSlant = MeshSideSlant.ABOVE;
                screen.guiRebuildScheduled = true;
            }));

            WikiRendererUI.booleanControl(container, this.useWalkabilityFilter, "walkability_filter");
            this.useWalkabilityFilter.addRebuildListener(screen);
            if (this.useWalkabilityFilter.get()) {
                WikiRendererUI.intControl(screen, container, this.walkableBlocksThreshold, "walkable_blocks_threshold");
                WikiRendererUI.intControl(screen, container, this.dontSearchForHigherFloorsThreshold, "dont_search_for_higher_floors_threshold");
                WikiRendererUI.intControl(screen, container, renderable.minFloorYLevelForOverhead, "min_floor_y_level");
                WikiRendererUI.intControl(screen, container, renderable.maxFloorYLevelForOverhead, "max_floor_y_level");
                WikiRendererUI.booleanControl(container, this.includeWallsForCaveMode, "show_walls");
                this.includeWallsForCaveMode.addRebuildListener(screen);
                if (this.includeWallsForCaveMode.get()) {
                    WikiRendererUI.booleanControl(container, this.includeCornersWhenIncludingWalls, "show_corner_walls");
                }
                WikiRendererUI.booleanControl(container, this.requireCeilingForCaveMode, "require_ceiling");
            }

            if (renderable.mesh.bounds instanceof ExpandableMeshBounds expandableMeshBounds && sideViewSlant == MeshSideSlant.ABOVE) {
                WikiRendererUI.booleanControl(container, this.showMeshExpansionControls, "show_expansion_buttons");
                this.showMeshExpansionControls.addRebuildListener(screen);

                if (showMeshExpansionControls.get()) {
                    for (ExpansionSide expansionSide : ExpansionSide.values()) {
                        try (WikiRendererUI.RowBuilder rowBuilder = WikiRendererUI.rowBuilder(container)) {
                            rowBuilder.row.child(new ConditionalButton(Translate.gui("minus_five"), _ -> {
                                if (renderable.mesh.canRebuild()) {
                                    expandableMeshBounds.move(renderable.mesh, expansionSide, sideViewRotation, -5);
                                    renderable.mesh.refreshWalkabilityFilter();
                                }
                            }, renderable.mesh::canRebuild));
                            rowBuilder.row.child(new ConditionalButton(Translate.gui("minus_one"), _ -> {
                                if (renderable.mesh.canRebuild()) {
                                    expandableMeshBounds.move(renderable.mesh, expansionSide, sideViewRotation, -1);
                                    renderable.mesh.refreshWalkabilityFilter();
                                }
                            }, renderable.mesh::canRebuild));
                            rowBuilder.row.child(new ConditionalButton(Translate.gui("plus_one"), _ -> {
                                if (renderable.mesh.canRebuild()) {
                                    expandableMeshBounds.move(renderable.mesh, expansionSide, sideViewRotation, 1);
                                    renderable.mesh.refreshWalkabilityFilter();
                                }
                            }, renderable.mesh::canRebuild));
                            rowBuilder.row.child(new ConditionalButton(Translate.gui("plus_five"), _ -> {
                                if (renderable.mesh.canRebuild()) {
                                    expandableMeshBounds.move(renderable.mesh, expansionSide, sideViewRotation, 5);
                                    renderable.mesh.refreshWalkabilityFilter();
                                }
                            }, renderable.mesh::canRebuild));


                            rowBuilder.row.child(UIComponents.label(Translate.gui(expansionSide.name().toLowerCase())).margins(Insets.of(0, 0, 10, 10)));
                        }
                    }
                }
            }
        }

        WorldBlockMesh mesh = renderable.mesh;

        try (WikiRendererUI.RowBuilder builder = WikiRendererUI.autoNewLineRow(container)) {
            ButtonComponent buildMeshButton = WikiRendererUI.button(Translate.gui("rebuild_mesh"), _ -> mesh.scheduleRebuild(true));
            builder.row.child(buildMeshButton);

            ButtonComponent stopBuildingButton = WikiRendererUI.button(Translate.gui("stop_building"), _ -> mesh.stopBuilding());
            stopBuildingButton.margins(stopBuildingButton.margins().get().withLeft(5));
            stopBuildingButton.active = false;

            builder.row.child(stopBuildingButton);

            WikiRendererUI.dynamicText(builder.row, () -> {
                MutableComponent meshStatusText;
                if (hideMesh.get()) {
                    meshStatusText = Translate.gui("mesh_hidden").withStyle(ChatFormatting.GRAY);
                } else if (!mesh.getMeshState().isBuildStage) {
                    meshStatusText = Translate.gui("mesh_ready").withStyle(ChatFormatting.GREEN);
                } else {
                    meshStatusText = Translate.gui(
                            switch (mesh.getMeshState()) {
                                case BUILDING -> "mesh_building";
                                case CANCELLED -> "mesh_cancelled";
                                case CORRUPT -> "mesh_corrupt";
                                default -> "mesh_rebuilding";
                            },
                            (int) (mesh.getBuildProgress() * 100)
                    ).withStyle(ChatFormatting.RED);
                }

                buildMeshButton.active = mesh.canRebuild();
                stopBuildingButton.active = mesh.getMeshState() == WorldBlockMesh.MeshState.BUILDING || mesh.getMeshState() == WorldBlockMesh.MeshState.REBUILDING;

                return meshStatusText;
            }).margins(Insets.of(8, 0, 10, 0));
        }

        container.child(WikiRendererUI.button(Translate.gui("copy_render_command"), _ -> {
            screen.notify(Translate.gui("copied_coordinates_command_to_clipboard"));
            ClipboardUtil.setClipboard(mesh.bounds.generateAreaCommand());
        }));

        WikiRendererUI.text(container, "block_visibility", true);
        container.child(this.buildResetBlockAndEntityOverridesButton(screen, renderable));
        WikiRendererUI.booleanControl(container, this.hideMesh, "hide_blocks");
        this.hideMesh.addRebuildListener(screen);
        if (!this.hideMesh.get()) {
            WikiRendererUI.booleanControl(container, this.hideFluids, "hide_fluids");
            this.hideFluids.futureListen(screen, (_, _) -> mesh.scheduleRebuild(true));
            WikiRendererUI.booleanControl(container, this.hideBeaconBeams, "hide_beacon_beams");
            WikiRendererUI.booleanControl(container, this.freezeBlocks, "freeze_blocks");
        }

        WikiRendererUI.text(container, "entity_visibility_overrides", 10);
        WikiRendererUI.booleanControl(container, this.hideEntities, "hide_entities");
        this.hideEntities.addRebuildListener(screen);
        if (!this.hideEntities.get()) {
            WikiRendererUI.booleanControl(container, this.hideLivingEntities, "hide_living_entities");

            WikiRendererUI.booleanControl(container, this.showHiddenEntitiesList, "show_hidden_entities_list", hiddenEntityTypes.size());
            this.showHiddenEntitiesList.addRebuildListener(screen);
            if (showHiddenEntitiesList.get()) {
                TextBoxComponent editField = WikiRendererUI.labelledTextField(container, entityTypeSearch, "search", Sizing.expand(90));
                editField.onChanged().subscribe(text -> entityTypeSearch = text);

                WikiRendererUI.text(container, "visible_keyword", 3);
                WikiRendererUI.dynamicText(container, () -> Translate.gui("hidden_entities_amount", hiddenEntityTypes.size()));

                container.child(new SearchableEntityListComponent(hiddenEntityTypes, () -> entityTypeSearch, () -> renderable.entities));
            }

            WikiRendererUI.text(container, "selected_entity_refresh_mode", false).margins(Insets.of(10, 0, 5, 0));
            DropdownComponent refreshModeOptionsComponent = UIComponents.dropdown(Sizing.content());
            refreshModeOptionsComponent.closeWhenNotHovered(false);
            refreshModeOptionsComponent.padding(Insets.of(5));
            refreshModeOptionsComponent.surface(Surface.blur(10, 20));

            for (AreaEntityRefreshMode refreshMode : AreaEntityRefreshMode.values()) {
                MutableComponent text = Translate.gui("entity_refresh_mode_" + refreshMode.name().toLowerCase());
                if (entityRefreshMode == refreshMode) {
                    text = Translate.gui("selected", text.withStyle(switch (refreshMode) {
                        case REFRESHING_WITHIN_BOUNDS -> ChatFormatting.GREEN;
                        case NOT_REFRESHING -> ChatFormatting.YELLOW;
                        case NOT_REFRESHING_AND_FROZEN -> ChatFormatting.RED;
                    }));
                    text.withStyle(ChatFormatting.WHITE);
                } else {
                    text.withStyle(ChatFormatting.GRAY);
                }

                refreshModeOptionsComponent.button(text, _ -> {
                    entityRefreshMode = refreshMode;
                    screen.guiRebuildScheduled = true;
                });
            }
            container.child(refreshModeOptionsComponent);

            if (entityRefreshMode == AreaEntityRefreshMode.REFRESHING_WITHIN_BOUNDS) {
                WikiRendererUI.intPercentageControl(screen, container, this.entityBoundsIntersectionRequirement, "entity_collision_threshold_requirement");
            }

            WikiRendererUI.booleanControl(container, this.hideText, "hide_text");
            this.hideText.addRebuildListener(screen);
            if (!this.hideText.get()) {
                WikiRendererUI.booleanControl(container, this.hideNametags, "hide_nametags");
            }

            WikiRendererUI.text(container, "entity_overrides", 10);
            WikiRendererUI.booleanControl(container, this.overrideEntityRotations, "override_rotations");
            WikiRendererUI.intControl(screen, container, entityYawOverride, "entity_data.yaw");
            WikiRendererUI.intControl(screen, container, entityPitchOverride, "entity_data.pitch");
            WikiRendererUI.intControl(screen, container, entityRotationOverride, "entity_data.rotation");
            WikiRendererUI.booleanControl(container, toggleInvisibilityForEntities, "entity_data.invisible");

            WikiRendererUI.conditionalBooleanControl(container, this.freezePlayerArms, "freeze_player_arms", () -> renderable.hasEntityType(Avatar.class)
                                                                                                                   && entityRefreshMode != AreaEntityRefreshMode.NOT_REFRESHING_AND_FROZEN);
            WikiRendererUI.conditionalBooleanControl(container, useSteveSkinForEntities, "entity_data.steve", () -> renderable.hasEntityType(Avatar.class));
            WikiRendererUI.conditionalBooleanControl(container, forceSmallArmsForEntities, "entity_data.small_arms", () -> renderable.hasEntityType(Avatar.class));
            WikiRendererUI.conditionalBooleanControl(container, hideHeldItemsForEntities, "entity_data.hide_held_items",
                    () -> renderable.hasLivingEntityProperty(living -> !living.getMainHandItem().isEmpty() || !living.getOffhandItem().isEmpty()));
            WikiRendererUI.conditionalBooleanControl(container, hideArmorForEntities, "entity_data.hide_armor",
                    () -> renderable.hasLivingEntityProperty(e -> {
                        EntityRenderer<? super LivingEntity, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
                        if (renderer instanceof LivingEntityRenderer<?, ?, ?> livingEntityRenderer) {
                            return ((LivingEntityRendererAccessor) livingEntityRenderer).wikirenderer$getLayers().stream().anyMatch(l -> l instanceof HumanoidArmorLayer<?, ?, ?>);
                        }

                        return false;
                    }));
            WikiRendererUI.conditionalBooleanControl(container, hideEnchantmentsForEntities, "entity_data.hide_enchantments",
                    () -> renderable.hasLivingEntityProperty(living -> Arrays.stream(EquipmentSlot.values()).anyMatch(slot -> living.getItemBySlot(slot).hasFoil())));

            LabelComponent label = new AutoResizingLabelComponent(Translate.gui("advanced_entity_data_activation"));
            label.color(Color.ofFormatting(ChatFormatting.GRAY));
            label.margins(Insets.of(2).withTop(6));
            container.child(label);

            container.child(renderable.advancedPropertiesComponent);
        }
    }

    private UIComponent buildResetBlockAndEntityOverridesButton(RenderScreen screen, AreaRenderable renderable) {
        return WikiRendererUI.button(Translate.gui("reset_block_and_entity_overrides"), _ -> {
            this.hideMesh.setToDefault();
            this.hideFluids.setToDefault();
            this.hideBeaconBeams.setToDefault();
            this.hideEntities.setToDefault();
            this.hideLivingEntities.setToDefault();
            this.showHiddenEntitiesList.setToDefault();
            this.entityTypeSearch = "Visible";
            this.hiddenEntityTypes.clear();
            this.entityRefreshMode = AreaEntityRefreshMode.REFRESHING_WITHIN_BOUNDS;
            this.entityBoundsIntersectionRequirement.setToDefault();
            this.hideText.setToDefault();
            this.hideNametags.setToDefault();
            this.overrideEntityRotations.setToDefault();
            this.entityYawOverride.setToDefault();
            this.entityPitchOverride.setToDefault();
            this.entityRotationOverride.setToDefault();
            this.toggleInvisibilityForEntities.setToDefault();
            this.freezePlayerArms.setToDefault();
            this.useSteveSkinForEntities.setToDefault();
            this.forceSmallArmsForEntities.setToDefault();
            this.hideHeldItemsForEntities.setToDefault();
            this.hideArmorForEntities.setToDefault();
            this.hideEnchantmentsForEntities.setToDefault();
            AreaRenderable.ENTITY_SPECIFIC_OVERRIDES_BY_ID.clear();
            renderable.selectedEntityId = null;
            renderable.renderStateOverrides = null;
            this.emulateDaylight.setToDefault();
            this.useFullBrightGamma.setToDefault();
            this.useNightVision.setToDefault();
            screen.guiRebuildScheduled = true;
        });
    }

    @Override
    public void buildRenderOptionGUIControls(Renderable<?> r, RenderScreen screen, FlowLayout container) {
        AreaRenderable renderable = (AreaRenderable) r;

        WikiRendererUI.booleanControl(container, this.emulateDaylight, "render_as_daytime");
        WikiRendererUI.booleanControl(container, this.useFullBrightGamma, "full_bright");
        WikiRendererUI.booleanControl(container, this.useNightVision, "night_vision");
        this.useFullBrightGamma.addRebuildListener(screen);
        if (!this.useFullBrightGamma.get()) {
            WikiRendererUI.booleanControl(container, this.lightUpSurroundingBlocks, "light_up_surrounding_blocks");
            this.lightUpSurroundingBlocks.futureListen(screen, (_, _) -> renderable.mesh.scheduleRebuild(true));
        }

        WikiRendererUI.booleanControl(container, GlobalProperties.get().tickParticles, "particles");
        this.buildLoopParticlesOption(container);
    }

    @Override
    public void buildExportResolutionGUIControls(Renderable<?> renderable, RenderScreen screen, FlowLayout container) {
        if (!this.perPixel90DegreeRendering.get()) {
            super.buildExportResolutionGUIControls(renderable, screen, container);
        } else {
            AreaRenderable areaRenderable = (AreaRenderable) renderable;

            BlockPos cornerOne = areaRenderable.mesh.bounds.getMinCorner();
            BlockPos cornerTwo = areaRenderable.mesh.bounds.getMaxCorner();

            int totalBlocksX = cornerTwo.getX() - cornerOne.getX() + 1;
            int totalBlocksY = cornerTwo.getY() - cornerOne.getY() + 1;
            int totalBlocksZ = cornerTwo.getZ() - cornerOne.getZ() + 1;
            int highest = Math.max(totalBlocksY, Math.max(totalBlocksX, totalBlocksZ));

            TextBoxComponent resolutionField = WikiRendererUI.labelledTextField(container, String.valueOf(this.getPixelsPerBlockResolution()), "block_resolution", Sizing.fixed(50));
            resolutionField.setFilter(s -> s.matches("\\d{0,5}"));
            resolutionField.onChanged().subscribe(s -> {
                if (s.isBlank()) return;
                int pixelsPerBlock = Integer.parseInt(s);

                double bufferSize = highest * pixelsPerBlock;

                if ((pixelsPerBlock < 1 || pixelsPerBlock > 256 || bufferSize >= RenderSystem.getDevice().getMaxTextureSize()) && !GlobalProperties.get().unsafe.get()) {
                    screen.exportButton.active = false;
                } else {
                    if ((this.getPixelsPerBlockResolution() != 4 && pixelsPerBlock == 4) || (pixelsPerBlock != 4 && this.getPixelsPerBlockResolution() == 4)) {
                        screen.guiRebuildScheduled = true;
                    }
                    this.setPixelsPerBlockResolution(pixelsPerBlock);
                    screen.exportButton.active = true;
                }
            });

            boolean allowMinimapExporting = this.areMinimapSettingsExportable();
            if (allowMinimapExporting) {
                WikiRendererUI.booleanControl(container, this.exportSideViewMinimapData, "export_minimap_data");
            } else {
                WikiRendererUI.text(container, "minimap_disabled_notice_1", false);
                WikiRendererUI.text(container, "minimap_disabled_notice_2", false);
            }

            if (this.getPixelsPerBlockResolution() == 4) {
                WikiRendererUI.booleanControl(container, this.halfPixelOffsetFor4x4, "half_pixel_offset_for_4x4");
            }
        }
    }

    @Override
    public boolean allowForRescaling() {
        return !perPixel90DegreeRendering.get();
    }

    @Override
    public void applyToViewMatrix(Renderable<?> r, Matrix4fStack modelViewStack) {
        AreaRenderable renderable = (AreaRenderable) r;
        AreaPropertyBundle properties = renderable.getProperties();

        if (properties.perPixel90DegreeRendering.get()) {
            WorldBlockMesh mesh = renderable.mesh;
            BlockPos cornerOne = mesh.bounds.getMinCorner();
            BlockPos cornerTwo = mesh.bounds.getMaxCorner();

            Direction.Axis[] visibleAxes = switch (properties.sideViewSlant) {
                case BELOW, ABOVE -> new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z};
                case SIDE -> switch (properties.sideViewRotation) {
                    case NORTH, SOUTH -> new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Y};
                    case EAST, WEST -> new Direction.Axis[]{Direction.Axis.Z, Direction.Axis.Y};
                };
            };

            int totalBlocksA = cornerTwo.get(visibleAxes[0]) - cornerOne.get(visibleAxes[0]) + 1;
            int totalBlocksB = cornerTwo.get(visibleAxes[1]) - cornerOne.get(visibleAxes[1]) + 1;

            int highest = Math.max(totalBlocksA, totalBlocksB);

            // force pixel count per blocks without blurriness
            double pixelsPerBlock = this.getPixelsPerBlockResolution();
            double bufferSize = highest * pixelsPerBlock;
            this.setExportResolution(renderable, (int) bufferSize);
            double orthoWidth = 2.0; // bcause ortho is -1 to 1

            float pixelPerfectScale = (float) (pixelsPerBlock / (bufferSize / orthoWidth));

            modelViewStack.scale(pixelPerfectScale, pixelPerfectScale, pixelPerfectScale);
            modelViewStack.rotate(Axis.XP.rotationDegrees(this.sideViewSlant.getRotationDegrees()));
            modelViewStack.rotate(Axis.YP.rotationDegrees(this.sideViewRotation.getRotationDegrees()));

            if (pixelsPerBlock == 4 && this.halfPixelOffsetFor4x4.get()) {
                float halfPixelWorld = 0.5f / (float) pixelsPerBlock;
                modelViewStack.translate(halfPixelWorld, 0, halfPixelWorld);
            }
        } else {
            float scale = this.scale.get() / 1000f;
            modelViewStack.scale(scale, scale, scale);

            // offsets arent needed for side rendering because they're already perfectly aligned
            modelViewStack.translate(this.xOffset.get() / 2600f, this.yOffset.get() / -2600f, 0);

            modelViewStack.rotate(Axis.XP.rotationDegrees(this.slant.get().floatValue()));
            modelViewStack.rotate(Axis.YP.rotationDegrees(this.rotation.get() + this.updateAndGetSpinningRotationOffset()));
        }
    }
}
