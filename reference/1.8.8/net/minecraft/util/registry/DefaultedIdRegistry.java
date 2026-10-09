package net.minecraft.util.registry;

import org.apache.commons.lang3.Validate;

public class DefaultedIdRegistry<K, V> extends IdRegistry<K, V> {
    private final K defaultKey;
    private V defaultValue;

    public DefaultedIdRegistry(K defaultKey) {
        this.defaultKey = defaultKey;
    }

    @Override
    public void register(int id, K key, V value) {
        if (this.defaultKey.equals(key)) {
            this.defaultValue = value;
        }

        super.register(id, key, value);
    }

    public void validate() {
        Validate.notNull(this.defaultKey);
    }

    @Override
    public V get(K key) {
        V v = super.get(key);
        return v == null ? this.defaultValue : v;
    }

    @Override
    public V get(int id) {
        V v = super.get(id);
        return v == null ? this.defaultValue : v;
    }
}
