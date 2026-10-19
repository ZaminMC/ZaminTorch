package net.zaminmc.torch.server.world;

import net.zaminmc.torch.server.concurrent.BlockingIoLane;
import net.zaminmc.torch.server.concurrent.ComputeSubsystem;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The async world-save pipeline (the Phase-5 adoption's FIRST workload —
 * the persistence-preparation slice S1): the world-delta save's two
 * halves split along the §14 compute contract. THE SERIALIZATION (the
 * pure {@link WorldDeltaSerializer} over the immutable snapshot) leaves
 * the tick lane — one job on the compute pool per save. THE PUBLICATION
 * (the temp write + the atomic move) rides the blocking-I/O lane — the
 * storage's own file walk, never re-reading the world.
 *
 * <p>THE SAVE SEQUENCE: every submission takes a monotonically
 * increasing sequence number. The publication gate (§15's
 * "persistence must not publish an older snapshot over newer state"):
 * inside the per-storage publication right, a snapshot whose sequence
 * is not the newest seen DROPS — a newer snapshot already owns the
 * file; the dropped save's completion still reports (its durability
 * demand is satisfied by the newer content on disk). The bytes from a
 * dropped save are discarded, never written.</p>
 *
 * <p>THE OWNER BOUNDARY: the snapshot is captured ON the tick lane (the
 * engine's own {@code snapshotDeltas} walk — the authoritative read);
 * the serialization is pure (no world read); the publication touches
 * only the filesystem. The completion lands through the caller's
 * callback — the pipeline itself never mutates engine state, so the
 * callback may run on the I/O lane's worker (the caller's latch or
 * counter, nothing else).</p>
 *
 * <p>THE OBSERVABILITY: {@code submitted()}, {@code serialized()},
 * {@code landed()}, {@code staleDropped()}, and
 * {@code serializeNanos()} — the lane's own engagement evidence and
 * the before/after measurement hook (the §22 evidence gate's
 * measurement row).</p>
 */
public final class WorldSavePipeline {

    private static final Logger LOGGER = Logger.getLogger(WorldSavePipeline.class.getName());

    private final ComputeSubsystem compute;
    private final BlockingIoLane io;
    private final OwnershipDomain domain;

    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong serialized = new AtomicLong();
    private final AtomicLong landed = new AtomicLong();
    private final AtomicLong staleDropped = new AtomicLong();
    private final AtomicLong serializeNanos = new AtomicLong();

    public WorldSavePipeline(ComputeSubsystem compute, BlockingIoLane io, OwnershipDomain domain) {
        this.compute = java.util.Objects.requireNonNull(compute, "compute");
        this.io = java.util.Objects.requireNonNull(io, "io");
        this.domain = java.util.Objects.requireNonNull(domain, "domain");
    }

    /**
     * One async save: the serialization on the compute pool, the
     * publication on the I/O lane, the sequence gate at the file. The
     * returned latch releases when THIS save's durability demand is
     * satisfied — its own bytes landed, or a newer save's bytes landed
     * instead (the supersession), or the pipeline failed loudly (the
     * failure surfaces on the caller's side as the documented false).
     */
    public CountDownLatch submitSave(DeltaWorldStorage storage, WorldDeltaSnapshot snapshot) {
        java.util.Objects.requireNonNull(storage, "storage");
        java.util.Objects.requireNonNull(snapshot, "snapshot");
        long seq = submitted.incrementAndGet();
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean(false);

        try {
            compute.submit(domain, snapshot,
                    input -> {
                        long start = System.nanoTime();
                        byte[] bytes = WorldDeltaSerializer.serialize(input);
                        serializeNanos.addAndGet(System.nanoTime() - start);
                        return bytes;
                    },
                    (bytes, generation) -> {
                        // The serialization is done — the publication rides
                        // the blocking-I/O lane (never a simulation worker).
                        serialized.incrementAndGet();
                        try {
                            io.submit(domain, "world-delta-save-" + seq,
                                    () -> {
                                        boolean landedNow;
                                        try {
                                            landedNow = publish(storage, seq, bytes);
                                        } catch (RuntimeException publicationFailure) {
                                            // The publication failed loudly (the
                                            // lane logs it): the save never landed,
                                            // and the caller's durability walk must
                                            // never hang on the failure.
                                            finish(done, finished);
                                            throw publicationFailure;
                                        }
                                        return landedNow;
                                    },
                                    (landedNow, ioGeneration) -> {
                                        if (landedNow) {
                                            landed.incrementAndGet();
                                        } else {
                                            staleDropped.incrementAndGet();
                                            LOGGER.fine(() -> "Save #" + seq
                                                    + " superseded by a newer snapshot; the newer bytes own the file");
                                        }
                                        finish(done, finished);
                                    },
                                    () -> {
                                        // The generation moved between the
                                        // serialize and the drain: the save's
                                        // content is stale by definition —
                                        // drop, never publish (§6.3).
                                        staleDropped.incrementAndGet();
                                        finish(done, finished);
                                    });
                        } catch (IllegalStateException laneRefused) {
                            // The lane is shut down or its admission is full:
                            // the save never published — loud, and the latch
                            // releases so the caller's durability walk never
                            // hangs (the caller logs the failure).
                            LOGGER.log(Level.WARNING, "Save #" + seq + " refused by the I/O lane",
                                    laneRefused);
                            finish(done, finished);
                        }
                    },
                    () -> {
                        // The compute generation moved before the drain: the
                        // snapshot is stale — drop, never publish (§6.3).
                        staleDropped.incrementAndGet();
                        finish(done, finished);
                    });
        } catch (IllegalStateException computeRefused) {
            LOGGER.log(Level.WARNING, "Save #" + seq + " refused by the compute subsystem",
                    computeRefused);
            finish(done, finished);
        }
        return done;
    }

    /**
     * The publication gate: inside the per-storage right, only a
     * sequence newer than everything already landed may write. The
     * per-storage lock keeps two publications from interleaving inside
     * the same file (the temp name is per-storage). Package-private:
     * the gate's own unit tests exercise it directly.
     */
    boolean publish(DeltaWorldStorage storage, long seq, byte[] bytes) {
        synchronized (storage) {
            if (seq <= landedSeqOf(storage)) {
                return false; // the stale save: a newer snapshot owns the file
            }
            storage.writeSerialized(bytes);
            rememberLandedSeq(storage, seq);
            return true;
        }
    }

    /** Waits for the save's durability demand, bounded — the caller's walk. */
    public boolean awaitSave(CountDownLatch latch, long timeoutMillis) throws InterruptedException {
        return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * One generic async save (Slice S2 — the container trio's own path):
     * the same two halves as the typed path, but the caller owns the
     * serialization and the publication — the pure serializer (the
     * snapshot IN, the bytes OUT) rides the compute pool, the publisher
     * (the store's own file half) rides the I/O lane under the named
     * sequence gate. The snapshot must be immutable (the caller captures
     * it on the owning tick lane); the serializer must not read the world
     * or the filesystem; the publisher touches only the filesystem.
     */
    public <S> CountDownLatch submitSave(String storageName, S snapshot,
                                         java.util.function.Function<S, byte[]> serializer,
                                         java.util.function.Consumer<byte[]> publisher) {
        java.util.Objects.requireNonNull(storageName, "storageName");
        java.util.Objects.requireNonNull(snapshot, "snapshot");
        java.util.Objects.requireNonNull(serializer, "serializer");
        java.util.Objects.requireNonNull(publisher, "publisher");
        long seq = submitted.incrementAndGet();
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean(false);

        try {
            compute.submit(domain, snapshot,
                    input -> {
                        long start = System.nanoTime();
                        byte[] bytes = serializer.apply(input);
                        serializeNanos.addAndGet(System.nanoTime() - start);
                        return bytes;
                    },
                    (bytes, generation) -> {
                        serialized.incrementAndGet();
                        try {
                            io.submit(domain, "store-save-" + storageName + "-" + seq,
                                    () -> {
                                        boolean landedNow;
                                        try {
                                            landedNow = publishNamed(storageName, seq, bytes, publisher);
                                        } catch (RuntimeException publicationFailure) {
                                            // The publication failed loudly (the
                                            // lane logs it): the save never landed,
                                            // and the caller's durability walk must
                                            // never hang on the failure.
                                            finish(done, finished);
                                            throw publicationFailure;
                                        }
                                        return landedNow;
                                    },
                                    (landedNow, ioGeneration) -> {
                                        if (landedNow) {
                                            landed.incrementAndGet();
                                        } else {
                                            staleDropped.incrementAndGet();
                                            LOGGER.fine(() -> "Save #" + seq + " (" + storageName
                                                    + ") superseded by a newer snapshot; the newer"
                                                    + " bytes own the file");
                                        }
                                        finish(done, finished);
                                    },
                                    () -> {
                                        staleDropped.incrementAndGet();
                                        finish(done, finished);
                                    });
                        } catch (IllegalStateException laneRefused) {
                            LOGGER.log(Level.WARNING, "Save #" + seq + " (" + storageName
                                    + ") refused by the I/O lane", laneRefused);
                            finish(done, finished);
                        }
                    },
                    () -> {
                        staleDropped.incrementAndGet();
                        finish(done, finished);
                    });
        } catch (IllegalStateException computeRefused) {
            LOGGER.log(Level.WARNING, "Save #" + seq + " (" + storageName
                    + ") refused by the compute subsystem", computeRefused);
            finish(done, finished);
        }
        return done;
    }

    /**
     * The named publication gate: the same monotonic-sequence rule as the
     * typed path, keyed by the storage's own name — inside the per-storage
     * right, only a sequence newer than everything already landed may
     * write. Package-private: the gate's own unit tests exercise it
     * directly.
     */
    boolean publishNamed(String storageName, long seq, byte[] bytes,
                         java.util.function.Consumer<byte[]> publisher) {
        synchronized (namedLocks.computeIfAbsent(storageName, key -> new Object())) {
            if (seq <= namedLandedSeqOf(storageName)) {
                return false; // the stale save: a newer snapshot owns the file
            }
            publisher.accept(bytes);
            rememberNamedLandedSeq(storageName, seq);
            return true;
        }
    }

    // The named-path per-storage landed-sequence memory (the generic gate's
    // own state), keyed by the caller's storage name.
    private final ConcurrentHashMap<String, Object> namedLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> namedLandedSeqs = new ConcurrentHashMap<>();

    private long namedLandedSeqOf(String storageName) {
        AtomicLong seq = namedLandedSeqs.get(storageName);
        return seq == null ? -1 : seq.get();
    }

    private void rememberNamedLandedSeq(String storageName, long seq) {
        namedLandedSeqs.computeIfAbsent(storageName, key -> new AtomicLong(-1)).set(seq);
    }

    private static void finish(CountDownLatch done, AtomicBoolean finished) {
        if (finished.compareAndSet(false, true)) {
            done.countDown();
        }
    }

    // The per-storage landed-sequence memory (the gate's own state), keyed
    // by identity — the pipeline's callers pass the engine's three delta
    // stores, a stable small set.
    private final java.util.IdentityHashMap<DeltaWorldStorage, AtomicLong> landedSeqs =
            new java.util.IdentityHashMap<>();

    private long landedSeqOf(DeltaWorldStorage storage) {
        synchronized (landedSeqs) {
            AtomicLong seq = landedSeqs.get(storage);
            return seq == null ? -1 : seq.get();
        }
    }

    private void rememberLandedSeq(DeltaWorldStorage storage, long seq) {
        synchronized (landedSeqs) {
            landedSeqs.computeIfAbsent(storage, key -> new AtomicLong(-1)).set(seq);
        }
    }

    // ---- observability ----

    public long submitted() { return submitted.get(); }
    public long serialized() { return serialized.get(); }
    public long landed() { return landed.get(); }
    public long staleDropped() { return staleDropped.get(); }
    /** The cumulative serialization nanoseconds (the off-tick measurement). */
    public long serializeNanos() { return serializeNanos.get(); }
}
