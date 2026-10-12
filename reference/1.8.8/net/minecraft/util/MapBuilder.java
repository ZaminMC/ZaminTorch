package net.minecraft.util;

import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

public class MapBuilder {
    public static <K, V> Map<K, V> linkedHashMap(Iterable<K> keys, Iterable<V> values) {
        return map(keys, values, Maps.newLinkedHashMap());
    }

    public static <K, V> Map<K, V> map(Iterable<K> keys, Iterable<V> values, Map<K, V> map) {
        Iterator<V> iterator = values.iterator();

        for (K k : keys) {
            map.put(k, iterator.next());
        }

        if (iterator.hasNext()) {
            throw new NoSuchElementException();
        } else {
            return map;
        }
    }
}
