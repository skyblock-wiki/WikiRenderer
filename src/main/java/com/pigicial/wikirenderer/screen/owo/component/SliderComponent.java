package com.pigicial.wikirenderer.screen.owo.component;

import com.pigicial.wikirenderer.screen.owo.core.Sizing;
import com.pigicial.wikirenderer.screen.owo.event.EventSource;
import com.pigicial.wikirenderer.screen.owo.event.EventStream;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Function;

public class SliderComponent extends AbstractSliderButton {

    protected final EventStream<OnChanged> changedEvents = OnChanged.newStream();
    protected final EventStream<OnSlideEnd> slideEndEvents = OnSlideEnd.newStream();

    protected Function<String, Component> messageProvider = value -> Component.empty();
    protected double scrollStep = .05;

    protected SliderComponent(Sizing horizontalSizing) {
        super(0, 0, 0, 0, Component.empty(), 0);

        this.sizing(horizontalSizing, Sizing.fixed(20));
    }

    public SliderComponent value(double value) {
        value = Math.clamp(value, 0, 1);

        if (this.value != value) {
            this.value = value;

            this.updateMessage();
            this.applyValue();
        }

        return this;
    }

    public double value() {
        return this.value;
    }

    public SliderComponent message(Function<String, Component> messageProvider) {
        this.messageProvider = messageProvider;
        this.updateMessage();
        return this;
    }

    public SliderComponent scrollStep(double scrollStep) {
        this.scrollStep = scrollStep;
        return this;
    }

    public double scrollStep() {
        return this.scrollStep;
    }

    public SliderComponent active(boolean active) {
        this.active = active;
        return this;
    }

    public boolean active() {
        return this.active;
    }

    public EventSource<OnChanged> onChanged() {
        return this.changedEvents.source();
    }

    public EventSource<OnSlideEnd> slideEnd() {
        return this.slideEndEvents.source();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(this.messageProvider.apply(String.valueOf(this.value)));
    }

    @Override
    protected void applyValue() {
        this.changedEvents.sink().onChanged(this.value);
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (!this.active) return super.onMouseScroll(mouseX, mouseY, amount);

        this.value(this.value + this.scrollStep * amount);

        super.onMouseScroll(mouseX, mouseY, amount);
        return true;
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        this.slideEndEvents.sink().onSlideEnd();
        return super.onMouseUp(click);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (!this.active) return false;
        return super.keyPressed(input);
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo input) {
        return this.active && super.isValidClickButton(input);
    }

    /**
     * @deprecated Use {@link #message(Function)} instead,
     * as the message set by this method will be overwritten
     * the next time this slider is moved
     */
    @Override
    @Deprecated
    public final void setMessage(Component message) {
        super.setMessage(message);
    }

    public interface OnChanged {
        void onChanged(double value);

        static EventStream<OnChanged> newStream() {
            return new EventStream<>(subscribers -> value -> {
                for (var subscriber : subscribers) {
                    subscriber.onChanged(value);
                }
            });
        }
    }

    public interface OnSlideEnd {
        void onSlideEnd();

        static EventStream<OnSlideEnd> newStream() {
            return new EventStream<>(subscribers -> () -> {
                for (var subscriber : subscribers) {
                    subscriber.onSlideEnd();
                }
            });
        }
    }
}
