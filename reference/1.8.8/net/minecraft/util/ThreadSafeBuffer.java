package net.minecraft.util;

import java.lang.reflect.Array;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ThreadSafeBuffer<T> {
    private final T[] buffer;
    private final Class<? extends T> type;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private int size;
    private int head;

    public ThreadSafeBuffer(Class<? extends T> type, int size) {
        this.type = type;
        this.buffer = (T[])((Object[])Array.newInstance(type, size));
    }

    public T add(T value) {
        this.lock.writeLock().lock();
        this.buffer[this.head] = value;
        this.head = (this.head + 1) % this.capacity();
        if (this.size < this.capacity()) {
            this.size++;
        }

        this.lock.writeLock().unlock();
        return value;
    }

    public int capacity() {
        this.lock.readLock().lock();
        int i = this.buffer.length;
        this.lock.readLock().unlock();
        return i;
    }

    public T[] array() {
        T[] at = (T[])((Object[])Array.newInstance(this.type, this.size));
        this.lock.readLock().lock();

        for (int i = 0; i < this.size; i++) {
            int j = (this.head - this.size + i) % this.capacity();
            if (j < 0) {
                j += this.capacity();
            }

            at[i] = this.buffer[j];
        }

        this.lock.readLock().unlock();
        return at;
    }
}
