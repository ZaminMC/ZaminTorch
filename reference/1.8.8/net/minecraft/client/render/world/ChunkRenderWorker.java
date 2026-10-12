package net.minecraft.client.render.world;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.Entity;
import net.minecraft.util.crash.CrashReport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkRenderWorker implements Runnable {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ChunkRenderDispatcher dispatcher;
    private final ChunkBufferBuilders fixedBuffers;

    public ChunkRenderWorker(ChunkRenderDispatcher dispatcher) {
        this(dispatcher, null);
    }

    public ChunkRenderWorker(ChunkRenderDispatcher dispatcher, ChunkBufferBuilders fixedBuffers) {
        this.dispatcher = dispatcher;
        this.fixedBuffers = fixedBuffers;
    }

    @Override
    public void run() {
        while (true) {
            try {
                this.runTask(this.dispatcher.takeTask());
            } catch (InterruptedException interruptedexception) {
                LOGGER.debug("Stopping due to interrupt");
                return;
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.of(throwable, "Batching chunks");
                Minecraft.getInstance().integratedServerCrashed(Minecraft.getInstance().populateCrashReport(crashreport));
                return;
            }
        }
    }

    protected void runTask(ChunkCompileTask task) throws InterruptedException {
        task.getLock().lock();

        try {
            if (task.getStatus() != ChunkCompileTask.Status.PENDING) {
                if (!task.isCancelled()) {
                    LOGGER.warn("Chunk render task was " + task.getStatus() + " when I expected it to be pending; ignoring task");
                }

                return;
            }

            task.setStatus(ChunkCompileTask.Status.COMPILING);
        } finally {
            task.getLock().unlock();
        }

        Entity entity = Minecraft.getInstance().getCamera();
        if (entity == null) {
            task.cancel();
        } else {
            task.setBuffers(this.takeBuffers());
            float f = (float)entity.x;
            float f1 = (float)entity.y + entity.getEyeHeight();
            float f2 = (float)entity.z;
            ChunkCompileTask.Type chunkcompiletask$type = task.getType();
            if (chunkcompiletask$type == ChunkCompileTask.Type.REBUILD_CHUNK) {
                task.getChunk().compile(f, f1, f2, task);
            } else if (chunkcompiletask$type == ChunkCompileTask.Type.RESORT_TRANSPARENCY) {
                task.getChunk().rebuildTranslucent(f, f1, f2, task);
            }

            task.getLock().lock();

            try {
                if (task.getStatus() != ChunkCompileTask.Status.COMPILING) {
                    if (!task.isCancelled()) {
                        LOGGER.warn("Chunk render task was " + task.getStatus() + " when I expected it to be compiling; aborting task");
                    }

                    this.releaseBuffer(task);
                    return;
                }

                task.setStatus(ChunkCompileTask.Status.UPLOADING);
            } finally {
                task.getLock().unlock();
            }

            final CompiledChunk compiledChunk = task.getCompiledChunk();
            ArrayList list = Lists.newArrayList();
            if (chunkcompiletask$type == ChunkCompileTask.Type.REBUILD_CHUNK) {
                for (BlockLayer blocklayer : BlockLayer.values()) {
                    if (compiledChunk.hasLayer(blocklayer)) {
                        list.add(this.dispatcher.upload(blocklayer, task.getBuffers().get(blocklayer), task.getChunk(), compiledChunk));
                    }
                }
            } else if (chunkcompiletask$type == ChunkCompileTask.Type.RESORT_TRANSPARENCY) {
                list.add(this.dispatcher.upload(BlockLayer.TRANSLUCENT, task.getBuffers().get(BlockLayer.TRANSLUCENT), task.getChunk(), compiledChunk));
            }

            final ListenableFuture<List<Object>> listenablefuture = Futures.allAsList(list);
            task.registerCancelListener(new Runnable() {
                @Override
                public void run() {
                    listenablefuture.cancel(false);
                }
            });
            Futures.addCallback(
                listenablefuture,
                new FutureCallback<List<Object>>() {
                    public void onSuccess(List<Object> list) {
                        ChunkRenderWorker.this.releaseBuffer(task);
                        task.getLock().lock();

                        label44: {
                            try {
                                if (task.getStatus() == ChunkCompileTask.Status.UPLOADING) {
                                    task.setStatus(ChunkCompileTask.Status.DONE);
                                    break label44;
                                }

                                if (!task.isCancelled()) {
                                    ChunkRenderWorker.LOGGER
                                        .warn("Chunk render task was " + task.getStatus() + " when I expected it to be uploading; aborting task");
                                }
                            } finally {
                                task.getLock().unlock();
                            }

                            return;
                        }

                        task.getChunk().setCompiledChunk(compiledChunk);
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        ChunkRenderWorker.this.releaseBuffer(task);
                        if (!(throwable instanceof CancellationException) && !(throwable instanceof InterruptedException)) {
                            Minecraft.getInstance().integratedServerCrashed(CrashReport.of(throwable, "Rendering chunk"));
                        }
                    }
                }
            );
        }
    }

    private ChunkBufferBuilders takeBuffers() throws InterruptedException {
        return this.fixedBuffers != null ? this.fixedBuffers : this.dispatcher.takeBuffer();
    }

    private void releaseBuffer(ChunkCompileTask task) {
        if (this.fixedBuffers == null) {
            this.dispatcher.releaseBuffer(task.getBuffers());
        }
    }
}
