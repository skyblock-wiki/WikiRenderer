package com.pigicial.wikirenderer.screen.components;

import com.pigicial.wikirenderer.screen.owo.component.DropdownComponent;
import com.pigicial.wikirenderer.screen.owo.core.*;
import com.pigicial.wikirenderer.util.Translate;
import net.minecraft.client.gui.GuiPipeline;
import net.minecraft.client.renderer.pipeline.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SearchableEntityListComponent extends DropdownComponent {
    private final List<EntityType<?>> hiddenEntityTypes;
    private final Supplier<String> searchFilter;
    private final Supplier<List<Entity>> visibleEntitiesSupplier;

    private final Set<EntityType<?>> shownOptions = new HashSet<>();
    private boolean needsReorganization = false;

    public SearchableEntityListComponent(List<EntityType<?>> hiddenEntityTypes, Supplier<String> searchFilter, Supplier<List<Entity>> visibleEntitiesSupplier) {
        super(Sizing.content());
        this.visibleEntitiesSupplier = visibleEntitiesSupplier;
        this.closeWhenNotHovered(false);
        this.padding(Insets.of(5));

        this.hiddenEntityTypes = hiddenEntityTypes;
        this.searchFilter = searchFilter;

        update();
    }

    @Override
    protected void parentUpdate(float delta, int mouseX, int mouseY) {
        this.update();
    }

    public void update() {
        String filter = searchFilter.get();

        List<LeftAlignedCheckbox> checkboxes = new ArrayList<>();
        boolean needsRefresh = needsReorganization;

        List<Holder.Reference<EntityType<?>>> entityTypes = BuiltInRegistries.ENTITY_TYPE.listElements()
                .sorted(Comparator.comparing(t -> !hiddenEntityTypes.contains(t.value())))
                .toList();

        for (Holder.Reference<EntityType<?>> typeHolder : entityTypes) {
            EntityType<?> type = typeHolder.value();
            Component description = type.getDescription();
            String descriptionString = description.getString();

            boolean allow = descriptionString.toLowerCase().contains(filter);
            if (filter.trim().equalsIgnoreCase("visible")) {
                allow = visibleEntitiesSupplier.get().stream().anyMatch(e -> e.getType() == type);
            }

            needsRefresh = needsRefresh || allow != shownOptions.contains(type);
            if (allow) {
                MutableComponent hideText = Translate.gui("hide", descriptionString);
                shownOptions.add(type);
                checkboxes.add(new LeftAlignedCheckbox(hideText, Sizing.fill(100), () -> hiddenEntityTypes.contains(type), pressed -> {
                    if (pressed) {
                        hiddenEntityTypes.add(type);
                    } else {
                        hiddenEntityTypes.remove(type);
                    }
                    needsReorganization = true;
                    update();
                }));
            } else {
                shownOptions.remove(type);
            }
        }

        if (needsRefresh) {
            this.entries.clearChildren();
            this.entries.children(checkboxes);
        }
    }

    public static class LeftAlignedCheckbox extends DropdownComponent.Button {

        private final Supplier<Boolean> stateSupplier;
        protected final int iconSpace = 13;

        public LeftAlignedCheckbox(Component text, Sizing horizontalSizing, Supplier<Boolean> stateSupplier, Consumer<Boolean> onClick) {
            super(null, text, _ -> {});

            this.stateSupplier = stateSupplier;
            this.onClick = dropdownComponent -> onClick.accept(!stateSupplier.get());

            this.horizontalSizing(horizontalSizing);
            this.horizontalTextAlignment(HorizontalAlignment.LEFT);
            this.margins(Insets.of(2, 2, 2, 2));
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            super.draw(graphics, mouseX, mouseY, partialTicks, delta);

            boolean state = stateSupplier.get();
            int u = state ? 16 : 0;
            int iconY = this.y + (this.height - 9) / 2;

            graphics.blit(GuiPipeline.TEXTURED, ICONS_TEXTURE,
                    this.x + 2, iconY,
                    u, 0,
                    9, 9,
                    32, 32
            );
        }

        @Override
        protected void drawText(LabelDrawFunction drawFunction) {
            this.x += iconSpace;
            this.width -= iconSpace;
            super.drawText(drawFunction);
            this.x -= iconSpace;
            this.width += iconSpace;
        }

        @Override
        public void inflate(Size space) {
            this.width -= iconSpace;
            super.inflate(space);
            this.width += iconSpace;
        }

        @Override
        protected int determineHorizontalContentSize(Sizing sizing) {
            return super.determineHorizontalContentSize(sizing) + iconSpace;
        }
    }

    public static class DynamicTextButton extends DropdownComponent.Button {

        private final Supplier<Component> textSupplier;

        public DynamicTextButton(Supplier<Component> textSupplier, Consumer<DropdownComponent> onClick) {
            super(null, textSupplier.get(), onClick);
            this.textSupplier = textSupplier;
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            this.text(textSupplier.get());
            this.applySizing();
            super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        }
    }
}
