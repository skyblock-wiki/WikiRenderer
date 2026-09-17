package com.pigicial.wikirenderer.render.entity;

import com.mojang.math.Axis;
import com.pigicial.wikirenderer.components.AutoResizingLabelComponent;
import com.pigicial.wikirenderer.components.SearchableEntityListComponent;
import com.pigicial.wikirenderer.mixin.access.LivingEntityRendererAccessor;
import com.pigicial.wikirenderer.property.*;
import com.pigicial.wikirenderer.property.config.WikiRendererConfigs;
import com.pigicial.wikirenderer.render.Renderable;
import com.pigicial.wikirenderer.render.export.ImageRescaleMode;
import com.pigicial.wikirenderer.screen.RenderScreen;
import com.pigicial.wikirenderer.screen.WikiRendererUI;
import com.pigicial.wikirenderer.util.ClipboardUtil;
import com.pigicial.wikirenderer.util.Translate;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fStack;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EntityPropertyBundle extends DefaultCroppablePropertyBundle implements SerializablePropertyBundle {

    public static final EntityPropertyBundle INSTANCE = WikiRendererConfigs.loadOrDefault(new EntityPropertyBundle());

    public final Property<Boolean> spriteRendering = Property.of(false);
    public final Property<Boolean> spriteCropping = Property.of(true);
    public final Property<ImageRescaleMode> spriteRescaleMode = Property.of(ImageRescaleMode.DISABLED);
    public final IntProperty spriteRotation = IntProperty.of(0, 0, 360).withRollover();
    public final IntProperty spriteSlant = IntProperty.of(0, -90, 90);
    public final IntProperty spriteScale = IntProperty.of(200, 0, 1000);
    public int spriteExportResolution = 288;

    public final Property<Boolean> useLiveEntity = Property.of(false);
    public final Property<Boolean> tickEntityAnimations = Property.of(false);

    // for live entities only
    public final Property<Boolean> showSurroundingEntities = Property.of(false);
    public final DoubleProperty surroundingEntitiesRadius = DoubleProperty.of(0, 0, 30);
    public final Property<Boolean> autoRefreshVisibleSurroundingEntities = Property.of(true);
    public final Property<Boolean> showHiddenSurroundingEntitiesList = Property.of(false);
    public transient String entityTypeSearch = "Visible";
    public final transient List<EntityType<?>> hiddenSurroundingEntityTypes = new ArrayList<>();
    public final DoubleProperty surroundingParticlesRadius = DoubleProperty.of(0, 0, 30);

    public final Property<Boolean> hideNametags = Property.of(true);
    public final Property<Boolean> overrideHeadRotations = Property.of(true);
    public final IntProperty yaw = IntProperty.of(0, -180, 180).withRollover();
    public final IntProperty pitch = IntProperty.of(0, -90, 90);
    public final Property<Boolean> overrideBodyRotations = Property.of(true);
    public final IntProperty entityRotation = IntProperty.of(0, -180, 180).withRollover();

    // handle separately for more customization
    public final Property<Boolean> overrideEnderDragonFlapAnimation = Property.of(true);

    public final Property<Boolean> hideRedDamageGlow = Property.of(true);
    public final Property<Boolean> useSteveSkin = Property.of(false);
    public final Property<Boolean> hideHeldItems = Property.of(false);
    public final Property<Boolean> hideArmor = Property.of(false);
    public final Property<Boolean> hideEnchantments = Property.of(false);
    public final Property<Boolean> invisible = Property.of(false); // idk what this is for but its a requested option
    public final Property<Boolean> forceSmallArms = Property.of(false);

    @Override
    public String getConfigFileName() {
        return "entity_render_settings";
    }

    @Override
    protected int getDefaultExportResolution() {
        return 512;
    }

    @Override
    protected int getDefaultRotation() {
        return 315;
    }

    @Override
    public Property<Boolean> getCropProperty() {
        return this.spriteRendering.get() ? this.spriteCropping : super.getCropProperty();
    }

    @Override
    public Property<ImageRescaleMode> getRescaleMode() {
        return this.spriteRendering.get() ? this.spriteRescaleMode : super.getRescaleMode();
    }

    @Override
    public int getExportResolution(Renderable<?> renderable) {
        return this.spriteRendering.get() ? this.spriteExportResolution : super.getExportResolution(renderable);
    }

    @Override
    public void setExportResolution(Renderable<?> renderable, int exportResolution) {
        if (this.spriteRendering.get()) {
            this.spriteExportResolution = exportResolution;
        } else {
            super.setExportResolution(renderable, exportResolution);
        }
    }

    @Override
    public float getUsedRotation() {
        return this.spriteRendering.get() ? this.spriteRotation.get() : super.getUsedRotation();
    }

    @Override
    public double getUsedSlant() {
        return this.spriteRendering.get() ? spriteSlant.get() : super.getUsedSlant();
    }

    @Override
    public double getUsedScale() {
        return this.spriteRendering.get() ? spriteScale.get() : super.getUsedScale();
    }

    @Override
    public void modifyScale(double amount) {
        if (this.spriteRendering.get()) {
            this.spriteScale.modify(amount);
        } else {
            super.modifyScale(amount);
        }
    }

    @Override
    public void modifyRotation(int amount) {
        if (this.spriteRendering.get()) {
            if (this.allowRotatingWithMouse.get()) {
                this.spriteRotation.modify(amount);
            }
        } else {
            super.modifyRotation(amount);
        }
    }

    @Override
    public void modifySlant(double amount) {
        if (this.spriteRendering.get()) {
            if (this.allowRotatingWithMouse.get()) {
                this.spriteSlant.modify(amount);
            }
        } else {
            super.modifySlant(amount);
        }
    }

    @Override
    public void buildMainGUIControls(Renderable<?> r, RenderScreen screen, FlowLayout container) {
        EntityRenderable renderable = (EntityRenderable) r;

        WikiRendererUI.text(container, "transform_options", false);
        WikiRendererUI.booleanControl(container, this.spriteRendering, "sprite_rendering");

        this.spriteRendering.futureListen(screen, (_, _) -> {
            screen.guiRebuildScheduled = true;
            renderable.cachedCenterOffset = null;
            renderable.cachedScaleMultiplier = null;
            this.spriteRotation.set(0);
            this.spriteSlant.set(0);
        });

        if (!this.spriteRendering.get()) {
            WikiRendererUI.intControl(screen, container, this.scale, "scale");
            WikiRendererUI.intControl(screen, container, this.rotation, "rotation");
            WikiRendererUI.doubleControl(screen, container, this.slant, "slant");
            WikiRendererUI.intControl(screen, container, this.rotationSpeed, "rotation_speed");
            WikiRendererUI.conditionalBooleanControl(container, GlobalProperties.get().syncRotationToAnimation, "sync_rotation_to_animation", () -> !rotationSpeed.isDefault());
        } else {
            WikiRendererUI.intControl(screen, container, this.spriteScale, "scale");
            WikiRendererUI.intControl(screen, container, this.spriteRotation, "rotation");
            WikiRendererUI.intControl(screen, container, this.spriteSlant, "slant");
        }
        WikiRendererUI.booleanControl(container, this.allowRotatingWithMouse, "allow_rotating_with_mouse");
        if (!this.spriteRendering.get()) {
            try (WikiRendererUI.RowBuilder builder = WikiRendererUI.autoNewLineRow(container)) {
                builder.row.margins(Insets.none());
                builder.row.child(WikiRendererUI.button(Translate.gui("dimetric_recommended"), _ -> {
                    this.rotation.setToDefault();
                    this.slant.set(30D);
                }));
                builder.row.child(WikiRendererUI.button(Translate.gui("isometric"), _ -> {
                    this.rotation.setToDefault();
                    this.slant.set(35.264);
                }));
            }
        }

        container.child(this.buildResetButton(() -> {
            this.spriteRotation.setToDefault();
            this.spriteSlant.setToDefault();
            this.spriteScale.setToDefault();
            renderable.cachedCenterOffset = null;
            renderable.cachedScaleMultiplier = null;
        })).margins(Insets.of(5, 0, 0, 0));

        WikiRendererUI.text(container, "entity_render_options", true);
        container.child(this.buildResetEntityOverridesButton(screen, renderable));

        if (renderable.liveNonTickableEntity != null) {
            WikiRendererUI.booleanControl(container, this.useLiveEntity, "entity_data.use_live_entities");
            this.useLiveEntity.futureListen(screen, (_, value) -> {
                renderable.requireTextureReCache = true;
                if (renderable.textureCancelMarker != null) {
                    renderable.textureCancelMarker.set(true);
                }
                screen.guiRebuildScheduled = true;
                if (value) {
                    tickEntityAnimations.set(true);
                }
                renderable.cachedCenterOffset = null;
                renderable.cachedScaleMultiplier = null;
            });
        }
        WikiRendererUI.booleanControl(container, this.tickEntityAnimations, "entity_animations");

        if (renderable.liveNonTickableEntity != null && useLiveEntity.get()) {
            WikiRendererUI.booleanControl(container, showSurroundingEntities, "show_surrounding_entities");
            showSurroundingEntities.addRebuildListener(screen);

            if (showSurroundingEntities.get()) {
                WikiRendererUI.doubleControl(screen, container, surroundingEntitiesRadius, "surrounding_entity_radius");
                WikiRendererUI.booleanControl(container, this.autoRefreshVisibleSurroundingEntities, "auto_refresh_visible_entities");

                WikiRendererUI.booleanControl(container, this.showHiddenSurroundingEntitiesList, "show_hidden_surrounding_entities_list");
                this.showHiddenSurroundingEntitiesList.addRebuildListener(screen);
                if (showHiddenSurroundingEntitiesList.get()) {
                    TextBoxComponent editField = WikiRendererUI.labelledTextField(container, entityTypeSearch, "search", Sizing.expand(90));
                    editField.onChanged().subscribe(text -> entityTypeSearch = text);

                    WikiRendererUI.text(container, "visible_keyword", 3);
                    WikiRendererUI.dynamicText(container, () -> Translate.gui("hidden_entities_amount", hiddenSurroundingEntityTypes.size()));

                    container.child(new SearchableEntityListComponent(hiddenSurroundingEntityTypes, () -> entityTypeSearch, () -> {
                        List<Entity> shownEntities = new ArrayList<>();
                        renderable.forBaseAndSurroundingEntities(renderable.getUsedEntity(), (entity, isSurrounding) -> {
                            if (isSurrounding) {
                                shownEntities.add(entity);
                            }
                        });
                        return shownEntities;
                    }));
                }
            }
        }

        WikiRendererUI.text(container, "entity_data", 10);
        if (renderable.liveNonTickableEntity != null) {
            container.child(WikiRendererUI.button(Translate.gui("copy_entity_coordinates"), _ -> {
                Vec3 coords = renderable.getUsedEntity().position();

                DecimalFormat df = new DecimalFormat("0.#######");
                String text = df.format(coords.x) + " " + df.format(coords.y) + " " + df.format(coords.z);

                ClipboardUtil.setClipboard(text);
                screen.notify(Translate.gui("copied_entity_coordinates_to_clipboard"));
            }));
        }

        renderable.isNametagOnlyRenderedData = EntityRenderBoundsUtil.isNametagOnlyRenderedData(renderable.getUsedEntity());
        if (!renderable.isNametagOnlyRenderedData && !spriteRendering.get()) {
            WikiRendererUI.booleanControl(container, this.hideNametags, "hide_nametags");
            this.hideNametags.futureListen(screen, (_, _) -> {
                renderable.cachedCenterOffset = null;
                renderable.cachedScaleMultiplier = null;
            });
        }

        // rotation stuff
        WikiRendererUI.conditionalBooleanControl(container, this.overrideHeadRotations, "override_head_rotations", () -> renderable.hasEntityType(LivingEntity.class));
        WikiRendererUI.conditionalIntControl(screen, container, this.yaw, "entity_data.yaw", () -> renderable.hasEntityType(LivingEntity.class));
        WikiRendererUI.conditionalIntControl(screen, container, this.pitch, "entity_data.pitch", () -> renderable.hasEntityType(LivingEntity.class));
        WikiRendererUI.conditionalBooleanControl(container, this.overrideBodyRotations, "override_body_rotations", () -> renderable.hasEntityType(LivingEntity.class));
        WikiRendererUI.conditionalIntControl(screen, container, this.entityRotation, "entity_data.rotation", () -> renderable.hasEntityType(LivingEntity.class));

        // dragons
        WikiRendererUI.conditionalBooleanControl(container, this.overrideEnderDragonFlapAnimation, "override_dragon_flap_animation", () -> renderable.hasEntityType(EnderDragon.class));

        WikiRendererUI.conditionalBooleanControl(container, this.hideRedDamageGlow, "entity_data.hide_red_damage_glow", () -> renderable.hasEntityType(LivingEntity.class) || renderable.hasEntityType(EnderDragon.class));

        // players
        WikiRendererUI.conditionalBooleanControl(container, this.useSteveSkin, "entity_data.steve", () -> renderable.hasEntityType(Avatar.class));
        if (!spriteRendering.get()) {
            WikiRendererUI.conditionalBooleanControl(container, this.forceSmallArms, "entity_data.small_arms", () -> renderable.hasEntityType(Avatar.class));
        }

        WikiRendererUI.conditionalBooleanControl(container, this.hideHeldItems, "entity_data.hide_held_items", () -> shouldShowHideHeldItemsOption(renderable));
        WikiRendererUI.conditionalBooleanControl(container, this.hideArmor, "entity_data.hide_armor", () -> shouldShowHideArmorOption(renderable));
        WikiRendererUI.conditionalBooleanControl(container, this.hideEnchantments, "entity_data.hide_enchantments", () -> shouldShowHideEnchantmentsOption(renderable));
        WikiRendererUI.conditionalBooleanControl(container, this.invisible, "entity_data.invisible", () -> renderable.hasEntityType(LivingEntity.class));

        LabelComponent label = new AutoResizingLabelComponent(Translate.gui("advanced_entity_data_activation"));
        label.color(Color.ofFormatting(ChatFormatting.GRAY));
        label.margins(Insets.of(2).withTop(6));
        container.child(label);

        container.child(renderable.advancedPropertiesComponent);
    }

    private boolean shouldShowHideHeldItemsOption(EntityRenderable renderable) {
        return renderable.hasLivingEntityProperty(living -> !living.getMainHandItem().isEmpty() || !living.getOffhandItem().isEmpty());
    }

    private boolean shouldShowHideArmorOption(EntityRenderable renderable) {
        return renderable.hasLivingEntityProperty(e -> {
            EntityRenderer<? super LivingEntity, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
            if (renderer instanceof LivingEntityRenderer<?, ?, ?> livingEntityRenderer) {
                return ((LivingEntityRendererAccessor) livingEntityRenderer).wikirenderer$getLayers().stream().anyMatch(l -> l instanceof HumanoidArmorLayer<?, ?, ?>);
            }

            return false;
        });
    }

    private boolean shouldShowHideEnchantmentsOption(EntityRenderable renderable) {
        return renderable.hasLivingEntityProperty(living -> Arrays.stream(EquipmentSlot.values()).anyMatch(slot -> living.getItemBySlot(slot).hasFoil()));
    }

    private UIComponent buildResetEntityOverridesButton(RenderScreen screen, EntityRenderable renderable) {
        return WikiRendererUI.button(Translate.gui("reset_entity_overrides"), _ -> {
            this.showSurroundingEntities.setToDefault();
            this.surroundingEntitiesRadius.setToDefault();
            this.showHiddenSurroundingEntitiesList.setToDefault();
            this.entityTypeSearch = "Visible";
            this.hiddenSurroundingEntityTypes.clear();
            this.autoRefreshVisibleSurroundingEntities.setToDefault();
            this.surroundingParticlesRadius.setToDefault();
            this.useLiveEntity.setToDefault();
            this.tickEntityAnimations.setToDefault();
            this.hideNametags.setToDefault();
            this.overrideHeadRotations.setToDefault();
            this.hideNametags.setToDefault();
            this.overrideHeadRotations.setToDefault();
            this.yaw.setToDefault();
            this.pitch.setToDefault();
            this.overrideBodyRotations.setToDefault();
            this.entityRotation.setToDefault();
            this.overrideEnderDragonFlapAnimation.setToDefault();
            this.hideRedDamageGlow.setToDefault();
            this.useSteveSkin.setToDefault();
            this.hideHeldItems.setToDefault();
            this.hideArmor.setToDefault();
            this.hideEnchantments.setToDefault();
            this.invisible.setToDefault();
            this.forceSmallArms.setToDefault();
            EntityRenderable.ENTITY_SPECIFIC_OVERRIDES_BY_ID.clear();
            renderable.selectedEntityId = null;
            renderable.renderStateOverrides = null;
            screen.guiRebuildScheduled = true;
        });
    }

    @Override
    public void buildRenderOptionGUIControls(Renderable<?> r, RenderScreen screen, FlowLayout container) {
        EntityRenderable renderable = (EntityRenderable) r;
        super.buildRenderOptionGUIControls(renderable, screen, container);

        if (renderable.liveNonTickableEntity != null) {
            GlobalProperties globalProperties = GlobalProperties.get();

            WikiRendererUI.booleanControl(container, globalProperties.tickParticles, "show_surrounding_particles");
            globalProperties.tickParticles.addRebuildListener(screen);
            if (globalProperties.tickParticles.get()) {
                WikiRendererUI.doubleControl(screen, container, surroundingParticlesRadius, "surrounding_particles_radius");
                if (!useLiveEntity.get()) {
                    WikiRendererUI.text(container, Translate.gui("show_surrounding_particles_non_live_entities_warning").withStyle(ChatFormatting.RED), 5);
                }

                this.buildLoopParticlesOption(container);
            }
        }
    }

    @Override
    public void applyToViewMatrix(Renderable<?> renderable, Matrix4fStack modelViewStack) {
        float scale = (this.spriteRendering.get() ? this.spriteScale.get() : this.scale.get()) / 100f;
        modelViewStack.scale(scale, scale, scale);
        modelViewStack.translate(this.xOffset.get() / 26000f, this.yOffset.get() / -26000f, 0);

        if (this.spriteRendering.get()) {
            modelViewStack.rotate(Axis.XP.rotationDegrees(this.spriteSlant.get()));
            modelViewStack.rotate(Axis.YP.rotationDegrees(this.spriteRotation.get()));
        } else {
            modelViewStack.rotate(Axis.XP.rotationDegrees(this.slant.get().floatValue()));
            modelViewStack.rotate(Axis.YP.rotationDegrees(this.rotation.get() + this.updateAndGetSpinningRotationOffset()));
        }
    }
}
