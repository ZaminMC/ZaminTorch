package net.zaminmc.torch.server.concurrent;

import net.zaminmc.torch.concurrent.BlockingIo;

import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

/**
 * The public blocking-I/O view (§28.1's fourth category): the engine's own
 * {@link BlockingIoLane} dressed as the {@link BlockingIo} interface —
 * every submission binds to the server's simulation owner domain (the
 * authoritative context the ticker's own drain validates and publishes
 * on), the diagnostics read the lane's own counters.
 */
public final class BlockingIoView implements BlockingIo {

    private final BlockingIoLane lane;
    private final OwnershipDomain simulationDomain;

    public BlockingIoView(BlockingIoLane lane, OwnershipDomain simulationDomain) {
        this.lane = lane;
        this.simulationDomain = simulationDomain;
    }

    @Override
    public <R> Task<R> submit(String operation, Callable<R> work,
                              BiConsumer<R, Long> onFresh, Runnable onStale) {
        BlockingIoLane.IoHandle<R> handle = lane.submit(
                simulationDomain, operation, work, onFresh, onStale);
        return new TaskView<>(handle);
    }

    @Override
    public long submittedCount() {
        return lane.submittedCount();
    }

    @Override
    public long rejectedCount() {
        return lane.rejectedCount();
    }

    @Override
    public long failedCount() {
        return lane.failedCount();
    }

    @Override
    public int queuedCount() {
        return lane.queuedDepth();
    }

    /** The lifecycle handle over the engine's own. */
    private static final class TaskView<R> implements Task<R> {
        private final BlockingIoLane.IoHandle<R> handle;

        TaskView(BlockingIoLane.IoHandle<R> handle) {
            this.handle = handle;
        }

        @Override public long generation() {
            return handle.generation();
        }
        @Override public boolean isDone() {
            return handle.isDone();
        }
        @Override public boolean isCancelled() {
            return handle.isCancelled();
        }
        @Override public boolean isFailed() {
            return handle.isFailed();
        }
        @Override public Throwable failure() {
            return handle.failure();
        }
        @Override public boolean tryCancel() {
            return handle.tryCancel();
        }
    }
}
