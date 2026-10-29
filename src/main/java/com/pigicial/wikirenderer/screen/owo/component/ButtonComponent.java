package com.pigicial.wikirenderer.screen.owo.component;

import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.mixin.screen.AbstractWidgetAccessor;
import com.pigicial.wikirenderer.mixin.screen.ButtonAccessor;
import com.pigicial.wikirenderer.screen.owo.core.CursorStyle;
import com.pigicial.wikirenderer.screen.owo.core.OwoUIGraphics;
import com.pigicial.wikirenderer.screen.owo.core.Sizing;
import com.pigicial.wikirenderer.screen.owo.util.NinePatchTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.GuiPipeline;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.pipeline.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

public class ButtonComponent extends Button {

    public static final Identifier ACTIVE_TEXTURE = Identifier.fromNamespaceAndPath(WikiRenderer.MOD_ID, "button/active");
    public static final Identifier HOVERED_TEXTURE = Identifier.fromNamespaceAndPath(WikiRenderer.MOD_ID, "button/hovered");
    public static final Identifier DISABLED_TEXTURE = Identifier.fromNamespaceAndPath(WikiRenderer.MOD_ID, "button/disabled");

    protected Renderer renderer = Renderer.VANILLA;
    protected boolean textShadow = true;

    public ButtonComponent(Component message, Consumer<ButtonComponent> onPress) {
        super(0, 0, 0, 0, message, button -> onPress.accept((ButtonComponent) button), Button.DEFAULT_NARRATION);
        this.sizing(Sizing.content());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics) {
        this.renderer.draw((OwoUIGraphics) graphics, this, graphics.partialTicks());

        var textRenderer = Minecraft.getInstance().font;
        int color = this.active ? 0xffffffff : 0xffa0a0a0;

        if (this.textShadow) {
            graphics.centeredText(textRenderer, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, color);
        } else {
            graphics.text(textRenderer, this.getMessage(), (int) (this.getX() + this.width / 2f - textRenderer.width(this.getMessage()) / 2f), (int) (this.getY() + (this.height - 8) / 2f), color, false);
        }

        var tooltip = ((AbstractWidgetAccessor) this).owo$getTooltip();
        if (this.isHovered && tooltip.get() != null) {
            graphics.setTooltipForNextFrame(textRenderer, tooltip.get().toCharSequence(Minecraft.getInstance()), DefaultTooltipPositioner.INSTANCE, graphics.mouseX(), graphics.mouseY(), false);
        }
    }

    public ButtonComponent onPress(Consumer<ButtonComponent> onPress) {
        ((ButtonAccessor) this).owo$setOnPress(button -> onPress.accept((ButtonComponent) button));
        return this;
    }

    public ButtonComponent renderer(Renderer renderer) {
        this.renderer = renderer;
        return this;
    }

    public Renderer renderer() {
        return this.renderer;
    }

    public ButtonComponent textShadow(boolean textShadow) {
        this.textShadow = textShadow;
        return this;
    }

    public boolean textShadow() {
        return this.textShadow;
    }

    public ButtonComponent active(boolean active) {
        this.active = active;
        return this;
    }

    public boolean active() {
        return this.active;
    }

    protected CursorStyle owo$preferredCursorStyle() {
        return CursorStyle.HAND;
    }

    @FunctionalInterface
    public interface Renderer {
        Renderer VANILLA = (matrices, button, delta) -> {
            var texture = button.active
                ? button.isHovered ? HOVERED_TEXTURE : ACTIVE_TEXTURE
                : DISABLED_TEXTURE;
            NinePatchTexture.draw(texture, matrices, button.getX(), button.getY(), button.width, button.height);
        };

        static Renderer flat(int color, int hoveredColor, int disabledColor) {
            return (context, button, delta) -> {
                if (button.active) {
                    if (button.isHovered) {
                        context.fill(button.getX(), button.getY(), button.getX() + button.width, button.getY() + button.height, hoveredColor);
                    } else {
                        context.fill(button.getX(), button.getY(), button.getX() + button.width, button.getY() + button.height, color);
                    }
                } else {
                    context.fill(button.getX(), button.getY(), button.getX() + button.width, button.getY() + button.height, disabledColor);
                }
            };
        }

        static Renderer texture(Identifier texture, int u, int v, int textureWidth, int textureHeight) {
            return (context, button, delta) -> {
                int renderV = v;
                if (!button.active) {
                    renderV += button.height * 2;
                } else if (button.isHovered()) {
                    renderV += button.height;
                }

                context.blit(GuiPipeline.DEFAULT, texture, button.getX(), button.getY(), u, renderV, button.width, button.height, textureWidth, textureHeight);
            };
        }

        void draw(OwoUIGraphics context, ButtonComponent button, float delta);
    }
}
