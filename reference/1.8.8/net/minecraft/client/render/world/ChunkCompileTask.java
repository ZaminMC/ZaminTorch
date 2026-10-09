package net.minecraft.client.render.world;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class ChunkCompileTask {
    private final RenderChunk chunk;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<Runnable> cancelListeners = Lists.newArrayList();
    private final ChunkCompileTask.Type type;
    private ChunkBufferBuilders buffers;
    private CompiledChunk compiled;
    private ChunkCompileTask.Status status = ChunkCompileTask.Status.PENDING;
    private boolean cancelled;

    public ChunkCompileTask(RenderChunk chunk, ChunkCompileTask.Type type) {
        this.chunk = chunk;
        this.type = type;
    }

    public ChunkCompileTask.Status getStatus() {
        return this.status;
    }

    public RenderChunk getChunk() {
        return this.chunk;
    }

    public CompiledChunk getCompiledChunk() {
        return this.compiled;
    }

    public void setCompiledChunk(CompiledChunk compiled) {
        this.compiled = compiled;
    }

    public ChunkBufferBuilders getBuffers() {
        return this.buffers;
    }

    public void setBuffers(ChunkBufferBuilders buffers) {
        this.buffers = buffers;
    }

    public void setStatus(ChunkCompileTask.Status status) {
        this.lock.lock();

        try {
            this.status = status;
        } finally {
            this.lock.unlock();
        }
    }

    public void cancel() {
        this.lock.lock();

        try {
            if (this.type == ChunkCompileTask.Type.REBUILD_CHUNK && this.status != ChunkCompileTask.Status.DONE) {
                this.chunk.setDirty(true);
            }

            this.cancelled = true;
            this.status = ChunkCompileTask.Status.DONE;

            for (Runnable runnable : this.cancelListeners) {
                runnable.run();
            }
        } finally {
            this.lock.unlock();
        }
    }

    public void registerCancelListener(Runnable listener) {
        this.lock.lock();

        try {
            this.cancelListeners.add(listener);
            if (this.cancelled) {
                listener.run();
            }
        } finally {
            this.lock.unlock();
        }
    }

    public ReentrantLock getLock() {
        return this.lock;
    }

    public ChunkCompileTask.Type getType() {
        return this.type;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public enum Status {
        PENDING,
        COMPILING,
        UPLOADING,
        DONE;
    }

    public enum Type {
        REBUILD_CHUNK,
        RESORT_TRANSPARENCY;
    }
}
