package net.minecraft.util;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TypeInstanceMultiMap<T> extends AbstractSet<T> {
    private static final Set<Class<?>> ALL_TYPES = Sets.newHashSet();
    private final Map<Class<?>, List<T>> delegate = Maps.newHashMap();
    private final Set<Class<?>> types = Sets.newIdentityHashSet();
    private final Class<T> baseType;
    private final List<T> instances = Lists.newArrayList();

    public TypeInstanceMultiMap(Class<T> baseType) {
        this.baseType = baseType;
        this.types.add(baseType);
        this.delegate.put(baseType, this.instances);

        for (Class<?> oclass : ALL_TYPES) {
            this.addType(oclass);
        }
    }

    protected void addType(Class<?> type) {
        ALL_TYPES.add(type);

        for (T t : this.instances) {
            if (type.isAssignableFrom(t.getClass())) {
                this.add(t, type);
            }
        }

        this.types.add(type);
    }

    protected Class<?> findOrThrow(Class<?> type) {
        if (this.baseType.isAssignableFrom(type)) {
            if (!this.types.contains(type)) {
                this.addType(type);
            }

            return type;
        } else {
            throw new IllegalArgumentException("Don't know how to search for " + type);
        }
    }

    @Override
    public boolean add(T e) {
        for (Class<?> oclass : this.types) {
            if (oclass.isAssignableFrom(e.getClass())) {
                this.add(e, oclass);
            }
        }

        return true;
    }

    private void add(T e, Class<?> type) {
        List<T> list = this.delegate.get(type);
        if (list == null) {
            this.delegate.put(type, Lists.newArrayList(e));
        } else {
            list.add(e);
        }
    }

    @Override
    public boolean remove(Object o) {
        T t = (T)o;
        boolean flag = false;

        for (Class<?> oclass : this.types) {
            if (oclass.isAssignableFrom(t.getClass())) {
                List<T> list = this.delegate.get(oclass);
                if (list != null && list.remove(t)) {
                    flag = true;
                }
            }
        }

        return flag;
    }

    @Override
    public boolean contains(Object o) {
        return Iterators.contains(this.find(o.getClass()).iterator(), o);
    }

    public <S> Iterable<S> find(Class<S> type) {
        return new Iterable<S>() {
            @Override
            public Iterator<S> iterator() {
                List<T> list = TypeInstanceMultiMap.this.delegate.get(TypeInstanceMultiMap.this.findOrThrow(type));
                if (list == null) {
                    return Iterators.emptyIterator();
                }

                Iterator<T> iterator = list.iterator();
                return Iterators.filter(iterator, type);
            }
        };
    }

    @Override
    public Iterator<T> iterator() {
        return this.instances.isEmpty() ? Iterators.emptyIterator() : Iterators.unmodifiableIterator(this.instances.iterator());
    }

    @Override
    public int size() {
        return this.instances.size();
    }
}
