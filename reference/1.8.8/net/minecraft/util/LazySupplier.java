package net.minecraft.util;

public abstract class LazySupplier<T> {
    private T value;
    private boolean loaded = false;

    public T get() {
        if (!this.loaded) {
            this.loaded = true;
            this.value = this.load();
        }

        return this.value;
    }

    protected abstract T load();
}
