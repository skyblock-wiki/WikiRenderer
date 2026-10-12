package com.pigicial.wikirenderer.property;

public class IntProperty extends NumberProperty<Integer> {

    private IntProperty(int defaultValue, int min, int max) {
        super(defaultValue, min, max);
    }

    @Override
    protected Integer getSpan(Integer min, Integer max) {
        return max - min;
    }

    public static IntProperty of(int defaultValue, int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("'min' must be less than 'max'");
        }

        return new IntProperty(defaultValue, min, max);
    }

    public void setMaxValue(int max) {
        if (this.max != max) {
            this.max = max;
            this.span = max - min;
            if (this.value > max) {
                this.value = max;
            }

            this.invokeListeners();
        }
    }

    public IntProperty withRollover() {
        this.allowRollover = true;
        return this;
    }

    @Override
    public boolean isDefault() {
        return (int) value == defaultValue;
    }

    @Override
    public void set(Integer value) {
        if (allowRollover && (value > this.max || value < this.min)) {
            if (value > this.max) {
                value = (value - this.max) % this.max;
            } else {
                int range = this.max - this.min;
                value = this.max - ((this.min - value) % range);
            }
        }

        value = Math.min(max, Math.max(value, min));
        super.set(value);
    }

    @Override
    public synchronized void modify(double byDouble) {
        int by = (int) Math.round(byDouble);
        if (this.allowRollover) {
            this.value += by;
            if (this.value > this.max) this.value -= this.span;
            if (this.value < this.min) this.value += this.span;
        } else {
            this.value = Math.clamp(this.value + by, this.min, this.max);
        }

        value = Math.min(max, Math.max(value, min));
        this.invokeListeners();
    }

    @Override
    public double progress() {
        return span == 0 ? 1 : (this.value - this.min) / (double) this.span;
    }

    @Override
    public synchronized void setFromProgress(double progress) {
        this.value = (int) Math.round(this.min + progress * this.span);
        this.value = Math.min(max, Math.max(value, min));
        this.invokeListeners();
    }
}
