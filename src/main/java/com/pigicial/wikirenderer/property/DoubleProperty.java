package com.pigicial.wikirenderer.property;

public class DoubleProperty extends NumberProperty<Double> {

    private DoubleProperty(double defaultValue, double min, double max) {
        super(defaultValue, min, max);
    }

    public static DoubleProperty of(double defaultValue, double min, double max) {
        if (min >= max) {
            throw new IllegalArgumentException("'min' must be less than 'max'");
        }

        return new DoubleProperty(defaultValue, min, max);
    }

    @Override
    public boolean isDefault() {
        // i hate this
        return (double) this.value == defaultValue;
    }

    @Override
    protected Double getSpan(Double min, Double max) {
        return max - min;
    }

    @Override
    public void set(Double value) {
        if (allowRollover && (value > this.max || value < this.min)) {
            if (value > this.max) {
                value = (value - this.max) % this.max;
            } else {
                double range = this.max - this.min;
                value = this.max - ((this.min - value) % range);
            }
        }

        super.set(value);
    }

    @Override
    public void modify(double by) {
        if (allowRollover) {
            this.value += by;
            if (this.value > this.max) this.value -= this.span;
            if (this.value < this.min) this.value += this.span;
        } else {
            this.value = Math.clamp(this.value + by, this.min, this.max);
        }

        this.invokeListeners();
    }

    @Override
    public double progress() {
        return (this.value - this.min) / this.span;
    }

    @Override
    public void setFromProgress(double progress) {
        this.value = this.min + progress * this.span;
        this.invokeListeners();
    }
}
