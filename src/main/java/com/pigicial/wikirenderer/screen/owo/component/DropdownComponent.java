package com.pigicial.wikirenderer.screen.owo.component;

import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.screen.WikiRendererUI;
import com.pigicial.wikirenderer.screen.owo.base.BaseUIComponent;
import com.pigicial.wikirenderer.screen.owo.container.FlowLayout;
import com.pigicial.wikirenderer.screen.owo.container.UIContainers;
import com.pigicial.wikirenderer.screen.owo.core.*;
import com.pigicial.wikirenderer.screen.owo.util.UISounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiPipeline;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.pipeline.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

public class DropdownComponent extends FlowLayout {

    protected static final Identifier ICONS_TEXTURE = Identifier.fromNamespaceAndPath(WikiRenderer.MOD_ID, "textures/gui/dropdown_icons.png");
    protected final FlowLayout entries;
    protected boolean closeWhenNotHovered = false;

    public DropdownComponent(Sizing horizontalSizing) {
        super(Sizing.content(), Sizing.content(), Algorithm.HORIZONTAL);

        this.entries = UIContainers.verticalFlow(horizontalSizing, Sizing.content());
        this.entries.padding(Insets.of(1));
        this.entries.allowOverflow(true);

        this.child(this.entries);
    }

    @Override
    public ParentUIComponent surface(Surface surface) {
        this.entries.surface(surface);

        return this;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        if (this.closeWhenNotHovered && !this.isInBoundingBox(mouseX, mouseY)) {
            this.queue(() -> {
                this.closeWhenNotHovered(false);
                this.parent.removeChild(this);
            });
        }
    }

    @Override
    public void layout(Size space) {
        super.layout(space);

        var entries = this.entries.children();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (!(entry instanceof ResizeableComponent sizeable)) continue;

            sizeable.setWidth(this.entries.width() - this.entries.padding().get().horizontal() - entry.margins().get().horizontal());
        }
    }

    public DropdownComponent divider() {
        this.entries.child(new Divider());
        return this;
    }

    public DropdownComponent text(Component text) {
        this.entries.child(WikiRendererUI.label(text).color(Color.ofFormatting(ChatFormatting.GRAY)).margins(Insets.of(2)));
        return this;
    }

    public DropdownComponent button(Component text, Consumer<DropdownComponent> onClick) {
        this.entries.child(new Button(this, text, onClick).margins(Insets.of(2)));
        return this;
    }

    public DropdownComponent checkbox(Component text, boolean state, Consumer<Boolean> onClick) {
        this.entries.child(new Checkbox(this, text, state, onClick).margins(Insets.of(2)));
        return this;
    }

    public DropdownComponent nested(Component text, Sizing horizontalSizing, Consumer<DropdownComponent> builder) {
        var nested = new DropdownComponent(horizontalSizing);
        builder.accept(nested);
        this.entries.child(new NestEntry(this, text, nested).margins(Insets.of(2)));
        return this;
    }

    @Override
    public FlowLayout removeChild(UIComponent child) {
        if (child == this.entries) {
            this.queue(() -> {
                this.closeWhenNotHovered(false);
                this.parent.removeChild(this);
            });
        }
        return super.removeChild(child);
    }

    public DropdownComponent closeWhenNotHovered(boolean closeWhenNotHovered) {
        this.closeWhenNotHovered = closeWhenNotHovered;
        return this;
    }

    public boolean closeWhenNotHovered() {
        return this.closeWhenNotHovered;
    }

    protected static void drawIconFromTexture(OwoUIGraphics context, ParentUIComponent dropdown, int y, int u, int v) {
        context.blit(GuiPipeline.TEXTURED, ICONS_TEXTURE,
                dropdown.x() + dropdown.width() - dropdown.padding().get().right() - 10, y,
                u, v,
                9, 9,
                32, 32
        );
    }

    protected interface ResizeableComponent {
        void setWidth(int width);
    }

    protected static class Divider extends BaseUIComponent implements ResizeableComponent {

        public Divider() {
            this.sizing(Sizing.fixed(1));
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            var margins = this.margins.get();
            graphics.fill(
                    this.x - margins.left(),
                    this.y - margins.top(),
                    this.x + this.width + margins.right(),
                    this.y + this.height + margins.bottom(),
                    0xFF121212
            );
        }

        @Override
        public void setWidth(int width) {
            this.width = width;
        }
    }

    protected static class NestEntry extends LabelComponent {

        private final DropdownComponent child;

        protected NestEntry(DropdownComponent parentDropdown, Component text, DropdownComponent child) {
            super(text);
            this.child = child;

            this.mouseEnter().subscribe(() -> {
                child.margins(Insets.top(this.y - parentDropdown.y));

                parentDropdown.queue(() -> {
                    parentDropdown.removeChild(child);
                    parentDropdown.child(child);
                });
            });
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            super.draw(graphics, mouseX, mouseY, partialTicks, delta);
            drawIconFromTexture(graphics, this.parent, this.y, 0, 16);

            this.child.closeWhenNotHovered(!PositionedRectangle.of(this.x, this.y, this.parent.width(), this.height).isInBoundingBox(mouseX, mouseY));
        }

        @Override
        protected int determineHorizontalContentSize(Sizing sizing) {
            return super.determineHorizontalContentSize(sizing) + 17;
        }
    }

    protected static class Button extends LabelComponent implements ResizeableComponent {

        protected final DropdownComponent parentDropdown;
        protected Consumer<DropdownComponent> onClick;

        protected Button(DropdownComponent parentDropdown, Component text, Consumer<DropdownComponent> onClick) {
            super(text);
            this.onClick = onClick;
            this.parentDropdown = parentDropdown;

            this.margins(Insets.vertical(1));
            this.cursorStyle(CursorStyle.HAND);
        }

        public void setWidth(int width) {
            this.width = width;
        }

        @Override
        public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
            super.onMouseDown(click, doubled);

            this.onClick.accept(this.parentDropdown);
            this.playInteractionSound();

            return true;
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            if (this.isInBoundingBox(mouseX, mouseY)) {
                var margins = this.margins.get();
                graphics.fill(
                        this.x - margins.left(),
                        this.y - margins.top(),
                        this.x + this.width + margins.right(),
                        this.y + this.height + margins.bottom(),
                        0x44FFFFFF
                );
            }

            super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        }

        protected void playInteractionSound() {
            UISounds.playButtonSound();
        }
    }

    protected static class Checkbox extends Button {

        protected boolean state;

        public Checkbox(DropdownComponent parentDropdown, Component text, boolean state, Consumer<Boolean> onClick) {
            super(parentDropdown, text, _ -> {
            });

            this.state = state;
            this.onClick = _ -> {
                this.state = !this.state;
                onClick.accept(this.state);
            };
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            super.draw(graphics, mouseX, mouseY, partialTicks, delta);
            drawIconFromTexture(graphics, this.parent, this.y, this.state ? 16 : 0, 0);
        }

        @Override
        protected int determineHorizontalContentSize(Sizing sizing) {
            return super.determineHorizontalContentSize(sizing) + 17;
        }

        @Override
        protected void playInteractionSound() {
            UISounds.playInteractionSound();
        }
    }
}
