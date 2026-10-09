package net.minecraft.util;

import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public class LowercaseMap<V> implements Map<String, V> {
    private final Map<String, V> delegate = Maps.newLinkedHashMap();

    @Override
    public int size() {
        return this.delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return this.delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return this.delegate.containsKey(key.toString().toLowerCase());
    }

    @Override
    public boolean containsValue(Object value) {
        return this.delegate.containsKey(value);
    }

    @Override
    public V get(Object key) {
        return this.delegate.get(key.toString().toLowerCase());
    }

    public V put(String string, V object) {
        return this.delegate.put(string.toLowerCase(), object);
    }

    @Override
    public V remove(Object key) {
        return this.delegate.remove(key.toString().toLowerCase());
    }

    @Override
    public void putAll(Map<? extends String, ? extends V> m) {
        for (Entry<? extends String, ? extends V> entry : m.entrySet()) {
            this.put(entry.getKey(), (V)entry.getValue());
        }
    }

    @Override
    public void clear() {
        this.delegate.clear();
    }

    @Override
    public Set<String> keySet() {
        return this.delegate.keySet();
    }

    @Override
    public Collection<V> values() {
        return this.delegate.values();
    }

    @Override
    public Set<Entry<String, V>> entrySet() {
        return this.delegate.entrySet();
    }
}
