package net.minecraft.client.render.world;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.vertex.VertexBufferUploader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

public class ChunkRenderDispatcher {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ThreadFactory CHUNK_BATCHER_FACTORY = new ThreadFactoryBuilder().setNameFormat("Chunk Batcher %d").setDaemon(true).build();
    private final List<ChunkRenderWorker> workers = Lists.newArrayList();
    private final BlockingQueue<ChunkCompileTask> pendingTasks = Queues.newArrayBlockingQueue(100);
    private final BlockingQueue<ChunkBufferBuilders> availableBuffers = Queues.newArrayBlockingQueue(5);
    private final BufferUploader bufferUploader = new BufferUploader();
    private final VertexBufferUploader vertexBufferUploader = new VertexBufferUploader();
    private final Queue<ListenableFutureTask<?>> pendingUploads = Queues.newArrayDeque();
    private final ChunkRenderWorker localWorker;

    public ChunkRenderDispatcher() {
        for (int i = 0; i < 2; i++) {
            ChunkRenderWorker chunkrenderworker = new ChunkRenderWorker(this);
            Thread thread = CHUNK_BATCHER_FACTORY.newThread(chunkrenderworker);
            thread.start();
            this.workers.add(chunkrenderworker);
        }

        for (int j = 0; j < 5; j++) {
            this.availableBuffers.add(new ChunkBufferBuilders());
        }

        this.localWorker = new ChunkRenderWorker(this, new ChunkBufferBuilders());
    }

    public String getChunkDebugInfo() {
        return String.format("pC: %03d, pU: %1d, aB: %1d", this.pendingTasks.size(), this.pendingUploads.size(), this.availableBuffers.size());
    }

    public boolean runTasksUntil(long time) {
        boolean flag = false;

        long i;
        do {
            boolean flag1 = false;
            synchronized (this.pendingUploads) {
                if (!this.pendingUploads.isEmpty()) {
                    this.pendingUploads.poll().run();
                    flag1 = true;
                    flag = true;
                }
            }

            if (time == 0L || !flag1) {
                break;
            }

            i = time - System.nanoTime();
        } while (i >= 0L);

        return flag;
    }

    public boolean rebuildAsync(RenderChunk chunk) {
        chunk.getTaskLock().lock();

        try {
            final ChunkCompileTask chunkcompiletask = chunk.startCompile();
            chunkcompiletask.registerCancelListener(new Runnable() {
                @Override
                public void run() {
                    ChunkRenderDispatcher.this.pendingTasks.remove(chunkcompiletask);
                }
            });
            boolean flag = this.pendingTasks.offer(chunkcompiletask);
            if (!flag) {
                chunkcompiletask.cancel();
            }

            return flag;
        } finally {
            chunk.getTaskLock().unlock();
        }
    }

    public boolean rebuildSync(RenderChunk chunk) {
        chunk.getTaskLock().lock();

        try {
            ChunkCompileTask chunkcompiletask = chunk.startCompile();

            try {
                this.localWorker.runTask(chunkcompiletask);
            } catch (InterruptedException interruptedexception) {
            }

            return true;
        } finally {
            chunk.getTaskLock().unlock();
        }
    }

    public void runTasks() {
        this.clear();

        while (this.runTasksUntil(0L)) {
        }

        List<ChunkBufferBuilders> list = Lists.newArrayList();

        while (list.size() != 5) {
            try {
                list.add(this.takeBuffer());
            } catch (InterruptedException interruptedexception) {
            }
        }

        this.availableBuffers.addAll(list);
    }

    public void releaseBuffer(ChunkBufferBuilders buffer) {
        this.availableBuffers.add(buffer);
    }

    public ChunkBufferBuilders takeBuffer() throws InterruptedException {
        return this.availableBuffers.take();
    }

    public ChunkCompileTask takeTask() throws InterruptedException {
        return this.pendingTasks.take();
    }

    public boolean resortTransparency(RenderChunk chunk) {
        chunk.getTaskLock().lock();

        try {
            final ChunkCompileTask chunkcompiletask = chunk.startResortTransparency();
            if (chunkcompiletask != null) {
                chunkcompiletask.registerCancelListener(new Runnable() {
                    @Override
                    public void run() {
                        ChunkRenderDispatcher.this.pendingTasks.remove(chunkcompiletask);
                    }
                });
                return this.pendingTasks.offer(chunkcompiletask);
            } else {
                return true;
            }
        } finally {
            chunk.getTaskLock().unlock();
        }
    }

    public ListenableFuture<Object> upload(BlockLayer layer, BufferBuilder bufferBuilder, RenderChunk chunk, CompiledChunk compiled) {
        if (Minecraft.getInstance().isOnSameThread()) {
            if (GLX.useVbo()) {
                this.uploadBuffer(bufferBuilder, chunk.getBuffer(layer.ordinal()));
            } else {
                this.uploadGlList(bufferBuilder, ((GlListRenderChunk)chunk).getGlList(layer, compiled), chunk);
            }

            bufferBuilder.offset(0.0, 0.0, 0.0);
            return Futures.immediateFuture(null);
        } else {
            ListenableFutureTask<Object> listenablefuturetask = ListenableFutureTask.create(new Runnable() {
                @Override
                public void run() {
                    ChunkRenderDispatcher.this.upload(layer, bufferBuilder, chunk, compiled);
                }
            }, null);
            synchronized (this.pendingUploads) {
                this.pendingUploads.add(listenablefuturetask);
                return listenablefuturetask;
            }
        }
    }

    private void uploadGlList(BufferBuilder bufferBuilder, int list, RenderChunk chunk) {
        GL11.glNewList(list, 4864);
        GlStateManager.pushMatrix();
        chunk.glMultMatrix();
        this.bufferUploader.end(bufferBuilder);
        GlStateManager.popMatrix();
        GL11.glEndList();
    }

    private void uploadBuffer(BufferBuilder bufferBuilder, VertexBuffer buffer) {
        this.vertexBufferUploader.setBuffer(buffer);
        this.vertexBufferUploader.end(bufferBuilder);
    }

    public void clear() {
        while (!this.pendingTasks.isEmpty()) {
            ChunkCompileTask chunkcompiletask = this.pendingTasks.poll();
            if (chunkcompiletask != null) {
                chunkcompiletask.cancel();
            }
        }
    }
}
