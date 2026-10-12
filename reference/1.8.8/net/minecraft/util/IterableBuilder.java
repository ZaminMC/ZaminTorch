package net.minecraft.util;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.UnmodifiableIterator;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class IterableBuilder {
    public static <T> Iterable<T[]> iterableIterableToArrayIterable(Class<T> type, Iterable<? extends Iterable<? extends T>> iterableIterable) {
        return new IterableBuilder.ArrayIterable<>(type, iterableToArray(Iterable.class, iterableIterable));
    }

    public static <T> Iterable<List<T>> iterableIterableToListIterable(Iterable<? extends Iterable<? extends T>> iterableIterable) {
        return arrayIterableToListIterable(iterableIterableToArrayIterable(Object.class, iterableIterable));
    }

    private static <T> Iterable<List<T>> arrayIterableToListIterable(Iterable<Object[]> arrayIterable) {
        return Iterables.transform(arrayIterable, new IterableBuilder.ArrayToListFunction<>());
    }

    private static <T> T[] iterableToArray(Class<? super T> type, Iterable<? extends T> iterable) {
        List<T> list = Lists.newArrayList();

        for (T t : iterable) {
            list.add(t);
        }

        return (T[])list.toArray(newArray(type, list.size()));
    }

    private static <T> T[] newArray(Class<? super T> type, int size) {
        return (T[])((Object[])Array.newInstance(type, size));
    }

    static class ArrayIterable<T> implements Iterable<T[]> {
        private final Class<T> type;
        private final Iterable<? extends T>[] iterables;

        private ArrayIterable(Class<T> type, Iterable<? extends T>[] iterables) {
            this.type = type;
            this.iterables = iterables;
        }

        @Override
        public Iterator<T[]> iterator() {
            return this.iterables.length <= 0
                ? Collections.singletonList((T)IterableBuilder.newArray(this.type, 0)).iterator()
                : new IterableBuilder.ArrayIterable.ArrayIterator<>(this.type, this.iterables);
        }

        static class ArrayIterator<T> extends UnmodifiableIterator<T[]> {
            private int nextIterator = -2;
            private final Iterable<? extends T>[] iterables;
            private final Iterator<? extends T>[] iterators;
            private final T[] nextArray;

            private ArrayIterator(Class<T> type, Iterable<? extends T>[] iterables) {
                this.iterables = iterables;
                this.iterators = IterableBuilder.newArray(Iterator.class, this.iterables.length);

                for (int i = 0; i < this.iterables.length; i++) {
                    this.iterators[i] = iterables[i].iterator();
                }

                this.nextArray = IterableBuilder.newArray(type, this.iterators.length);
            }

            private void cleanUp() {
                this.nextIterator = -1;
                Arrays.fill(this.iterators, null);
                Arrays.fill(this.nextArray, null);
            }

            @Override
            public boolean hasNext() {
                if (this.nextIterator == -2) {
                    this.nextIterator = 0;

                    for (Iterator<? extends T> iterator1 : this.iterators) {
                        if (!iterator1.hasNext()) {
                            this.cleanUp();
                            break;
                        }
                    }

                    return true;
                } else {
                    if (this.nextIterator >= this.iterators.length) {
                        for (this.nextIterator = this.iterators.length - 1; this.nextIterator >= 0; this.nextIterator--) {
                            Iterator<? extends T> iterator = this.iterators[this.nextIterator];
                            if (iterator.hasNext()) {
                                break;
                            }

                            if (this.nextIterator == 0) {
                                this.cleanUp();
                                break;
                            }

                            iterator = this.iterables[this.nextIterator].iterator();
                            this.iterators[this.nextIterator] = iterator;
                            if (!iterator.hasNext()) {
                                this.cleanUp();
                                break;
                            }
                        }
                    }

                    return this.nextIterator >= 0;
                }
            }

            public T[] next() {
                if (!this.hasNext()) {
                    throw new NoSuchElementException();
                }

                while (this.nextIterator < this.iterators.length) {
                    this.nextArray[this.nextIterator] = (T)this.iterators[this.nextIterator].next();
                    this.nextIterator++;
                }

                return (T[])((Object[])this.nextArray.clone());
            }
        }
    }

    static class ArrayToListFunction<T> implements Function<Object[], List<T>> {
        private ArrayToListFunction() {
        }

        public List<T> apply(Object[] array) {
            return Arrays.asList((T[])array);
        }
    }
}
