package net.minecraft.util.registry;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.util.CrudeIncrementalIntIdentityHashMap;
import net.minecraft.util.IdObjectIterable;

public class IdRegistry<K, V> extends MappedRegistry<K, V> implements IdObjectIterable<V> {
    protected final CrudeIncrementalIntIdentityHashMap<V> ids = new CrudeIncrementalIntIdentityHashMap<>();
    protected final Map<V, K> keys = ((BiMap)this.entries).inverse();

    public void register(int id, K key, V value) {
        this.ids.put(value, id);
        this.put(key, value);
    }

    @Override
    protected Map<K, V> createMap() {
        return HashBiMap.create();
    }

    @Override
    public V get(K key) {
        return super.get(key);
    }

    public K getKey(V value) {
        return this.keys.get(value);
    }

    @Override
    public boolean containsKey(K key) {
        return super.containsKey(key);
    }

    public int getId(V value) {
        return this.ids.getId(value);
    }

    public V get(int id) {
        return this.ids.get(id);
    }

    @Override
    public Iterator<V> iterator() {
        return this.ids.iterator();
    }
}
