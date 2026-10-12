package net.minecraft.util;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

public class CrudeIncrementalIntIdentityHashMap<T> implements IdObjectIterable<T> {
    private final IdentityHashMap<T, Integer> ids = new IdentityHashMap<>(512);
    private final List<T> values = Lists.newArrayList();

    public void put(T value, int id) {
        this.ids.put(value, id);

        while (this.values.size() <= id) {
            this.values.add(null);
        }

        this.values.set(id, value);
    }

    public int getId(T value) {
        Integer integer = this.ids.get(value);
        return integer == null ? -1 : integer;
    }

    public final T get(int id) {
        return id >= 0 && id < this.values.size() ? this.values.get(id) : null;
    }

    @Override
    public Iterator<T> iterator() {
        return Iterators.filter(this.values.iterator(), Predicates.notNull());
    }
}
