package com.pigicial.wikirenderer.screen.owo.base;

import com.pigicial.wikirenderer.WikiRenderer;
import com.pigicial.wikirenderer.screen.owo.core.CursorStyle;
import com.pigicial.wikirenderer.screen.owo.core.OwoUIAdapter;
import com.pigicial.wikirenderer.screen.owo.core.ParentUIComponent;
import com.pigicial.wikirenderer.screen.owo.core.UIComponent;
import com.pigicial.wikirenderer.screen.owo.inject.GreedyInputUIComponent;
import com.pigicial.wikirenderer.screen.owo.util.UIErrorToast;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.function.BiFunction;

/**
 * A minimal implementation of a Screen which fully
 * supports all aspects of the UI system. Implementing this class
 * is trivial, as you only need to provide implementations for
 * {@link #createAdapter()} to initialize the UI system and {@link #build(ParentUIComponent)}
 * which is where you declare your component hierarchy.
 * <p>
 * Should you be locked into a different superclass on your screen already,
 * you can easily copy all code from this class into your screen - as you
 * can see supporting the entire feature-set of owo-ui only requires
 * very few changes to how a vanilla screen works
 *
 * @param <R> The type of root component this screen uses
 */
public abstract class BaseOwoScreen<R extends ParentUIComponent> extends Screen {

    /**
     * The UI adapter of this screen. This handles
     * all user input as well as setting up GL state for rendering
     * and managing component focus
     */
    protected OwoUIAdapter<R> uiAdapter = null;

    /**
     * Whether this screen has encountered an unrecoverable
     * error during its lifecycle and should thus close
     * itself on the next frame
     */
    protected boolean invalid = false;

    protected BaseOwoScreen(Component title) {
        super(title);
    }

    protected BaseOwoScreen() {
        this(Component.empty());
    }

    /**
     * Initialize the UI adapter for this screen. Usually
     * the body of this method will simply consist of a call
     * to {@link OwoUIAdapter#create(Screen, BiFunction)}
     *
     * @return The UI adapter for this screen to use
     */
    protected abstract @NotNull OwoUIAdapter<R> createAdapter();

    /**
     * Build the component hierarchy of this screen,
     * called after the adapter and root component have been
     * initialized by {@link #createAdapter()}
     *
     * @param rootComponent The root component created
     *                      in the previous initialization step
     */
    protected abstract void build(R rootComponent);

    @Override
    protected void init() {
        if (this.invalid) return;

        // Check whether this screen was already initialized
        if (this.uiAdapter != null) {
            // If it was, only resize the adapter instead of recreating it - this preserves UI state
            this.uiAdapter.moveAndResize(0, 0, this.width, this.height);
            // Re-add it as a child to circumvent vanilla clearing them
            this.addRenderableWidget(this.uiAdapter);
        } else {
            try {
                this.uiAdapter = this.createAdapter();
                this.build(this.uiAdapter.rootComponent);

                this.uiAdapter.inflateAndMount();
            } catch (Exception error) {
                WikiRenderer.LOGGER.warn("Could not initialize owo screen", error);
                UIErrorToast.report(error);
                this.invalid = true;
            }
        }

        ScreenEvents.afterExtract(this).register((screen, graphics) -> {
            this.drawComponentTooltip(graphics, graphics.mouseX(), graphics.mouseY(), graphics.partialTicks());
        });
    }

    /**
     * Draw the tooltip of this screen's component tree, invoked
     * by {@link ScreenEvents#afterExtract(Screen)} (Screen)} so that tooltips are
     * properly rendered above content
     */
    protected void drawComponentTooltip(GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float tickDelta) {
        if (this.uiAdapter != null) this.uiAdapter.drawTooltip(drawContext, mouseX, mouseY, tickDelta);
    }

    /**
     * A convenience shorthand for querying a component from the adapter's
     * root component via {@link ParentUIComponent#childById(Class, String)}
     */
    protected <C extends UIComponent> C component(Class<C> expectedClass, String id) {
        return this.uiAdapter.rootComponent.childById(expectedClass, id);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics) {

    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics) {
        if (!this.invalid) {
            super.extractRenderState(graphics);
        } else {
            this.onClose();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (this.uiAdapter == null) return false;

        if (!input.hasControlDown()
                && this.uiAdapter.rootComponent.focusHandler().focused() instanceof GreedyInputUIComponent inputComponent
                && inputComponent.onKeyPress(input)) {
            return true;
        }

        if (super.keyPressed(input)) {
            return true;
        }

        if (input.isEscape() && this.shouldCloseOnEsc()) {
            this.onClose();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.uiAdapter == null) return false;

        return this.uiAdapter.mouseDragged(click, deltaX, deltaY);
    }

    @Nullable
    @Override
    public GuiEventListener getFocused() {
        return this.uiAdapter;
    }

    @Override
    public void removed() {
        if (this.uiAdapter != null) {
            this.uiAdapter.cursorAdapter.applyStyle(CursorStyle.NONE);
        }
    }

    public void dispose() {
        if (this.uiAdapter != null) this.uiAdapter.dispose();
    }
}
