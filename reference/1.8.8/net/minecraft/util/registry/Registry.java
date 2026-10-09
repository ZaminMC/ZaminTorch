package net.minecraft.util.registry;

public interface Registry<K, V> extends Iterable<V> {
    V get(K key);

    void put(K key, V value);
}
