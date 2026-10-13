package net.zaminmc.torch.server.concurrent;

import net.zaminmc.torch.concurrent.AsyncCompute;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The public async-compute view (§27.3's compute family): the engine's
 * own {@link ComputeSubsystem} dressed as the {@link AsyncCompute}
 * interface — every submission binds to the server's simulation owner
 * domain (the authoritative context the ticker's own drain validates
 * and publishes on), the diagnostics read the subsystem's own counters.
 */
public final class AsyncComputeView implements AsyncCompute {

    private final ComputeSubsystem compute;
    private final OwnershipDomain simulationDomain;

    public AsyncComputeView(ComputeSubsystem compute, OwnershipDomain simulationDomain) {
        this.compute = compute;
        this.simulationDomain = simulationDomain;
    }

    @Override
    public <I, R> Computation<R> submit(I input, Function<I, R> compute,
                                        BiConsumer<R, Long> onFresh, Runnable onStale) {
        ComputeSubsystem.ComputeHandle<R> handle = this.compute.submit(
                simulationDomain, input, compute, onFresh, onStale);
        return new ComputationView<>(handle);
    }

    @Override
    public long submittedCount() {
        return compute.submittedCount();
    }

    @Override
    public long rejectedCount() {
        return compute.rejectedCount();
    }

    @Override
    public long failedCount() {
        return compute.failedCount();
    }

    @Override
    public int queuedCount() {
        return compute.queuedCount();
    }

    /** The lifecycle handle over the engine's own. */
    private static final class ComputationView<R> implements Computation<R> {
        private final ComputeSubsystem.ComputeHandle<R> handle;

        ComputationView(ComputeSubsystem.ComputeHandle<R> handle) {
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
