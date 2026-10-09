package net.minecraft.util.registry;

public class DefaultedRegistry<K, V> extends MappedRegistry<K, V> {
    private final V defaultValue;

    public DefaultedRegistry(V defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public V get(K key) {
        V v = super.get(key);
        return v == null ? this.defaultValue : v;
    }
}
