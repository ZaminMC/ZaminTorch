package net.zaminmc.torch.server.profiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The tick profiler (Profiler 1:1): the section stack with the nano-time
 * table, the delayed activation ({@link #start} on the /debug request, the
 * next tick's head resetting the table), the 100ms warn on a slow section,
 * and the results walk — the children's percentages of parent and of
 * total, the "unspecified" remainder row, the *999/1000 decay every read
 * (the reference's own smoothing — a section's history fades between
 * reports), and the section's self row at index 0.
 */
public final class EngineProfiler {

    private static final Logger LOGGER = Logger.getLogger(EngineProfiler.class.getName());

    private final List<String> locations = new ArrayList<>();
    private final List<Long> times = new ArrayList<>();
    // Volatile (the reference's own field is plain — its profiler is
    // single-threaded): ML-024's wire-publish arm gates on collection from
    // the netty event loop (the join/movement publish lanes), a thread the
    // tick lane's start()/stop() writes carry no happens-before edge to;
    // the volatile read is the minimum that cross-thread gate needs (the
    // same discipline as EngineServer's profilerRequested flag).
    private volatile boolean profiling;
    private String currentLocation = "";
    // ConcurrentHashMap (not the reference's HashMap): the Phase-7
    // measurement face (sectionNanosSnapshot) reads this table from the
    // measuring thread while the tick thread merges — the weakly-
    // consistent iteration never throws, and every merge/put stays atomic;
    // for the single-threaded report walk the behavior is identical.
    private final Map<String, Long> timesByLocation = new ConcurrentHashMap<>();
    // The visit counts (NOT a reference arm — the reference's profiler
    // accumulates time only): one merge per pop, the same key as the
    // time merge, so every nanos row has its count row. The count is what
    // makes the /debug stop table's avg column meaningful (total/count)
    // and lets a harness difference per-visit costs between spans.
    private final Map<String, Long> countsByLocation = new ConcurrentHashMap<>();

    /** @return whether the table is collecting (the /debug stop gate). */
    public boolean isProfiling() {
        return profiling;
    }

    /** The delayed activation (MinecraftServer.enableProfiling + tick's head). */
    public void start() {
        this.profiling = true;
        reset();
    }

    /** Deactivates (the /debug stop, after the report is built). */
    public void stop() {
        this.profiling = false;
    }

    /** Profiler.reset 1:1 — clears the tables and the stack. */
    public void reset() {
        this.timesByLocation.clear();
        this.countsByLocation.clear();
        this.currentLocation = "";
        this.locations.clear();
    }

    /** Profiler.push 1:1 — descends into a section (nano-time stamp). */
    public void push(String location) {
        if (this.profiling) {
            if (this.currentLocation.length() > 0) {
                this.currentLocation = this.currentLocation + ".";
            }
            this.currentLocation = this.currentLocation + location;
            this.locations.add(this.currentLocation);
            this.times.add(System.nanoTime());
        }
    }

    /** Profiler.pop 1:1 — ascends, recording the section's elapsed time. */
    public void pop() {
        if (this.profiling) {
            long now = System.nanoTime();
            long opened = this.times.remove(this.times.size() - 1);
            this.locations.remove(this.locations.size() - 1);
            long elapsed = now - opened;
            this.timesByLocation.merge(this.currentLocation, elapsed, Long::sum);
            this.countsByLocation.merge(this.currentLocation, 1L, Long::sum);
            if (elapsed > 100_000_000L) {
                LOGGER.warning("Something's taking too long! '" + this.currentLocation
                        + "' took aprox " + elapsed / 1_000_000.0 + " ms");
            }
            this.currentLocation = !this.locations.isEmpty()
                    ? this.locations.get(this.locations.size() - 1)
                    : "";
        }
    }

    /** Profiler.swap 1:1 — endStartSection: pop then push. */
    public void swap(String location) {
        this.pop();
        this.push(location);
    }

    /**
     * The cross-thread span arm (ML-024 — the wire-publish walk's
     * section): records an already-measured elapsed span directly into the
     * section table WITHOUT the section stack. The walk this exists for
     * runs on three lanes — the netty event loop (the join sequence's and
     * the movement lane's chunk streams), the tick thread's pre-handler
     * job drain (the respawn/dimension-change re-streams, OUTSIDE the
     * root section's window), and the tick thread inside the
     * relightTransport section — and the first lane can interleave with
     * the tick thread's open sections, which the push/pop stack's
     * single-caller contract forbids: a nested section there would tear
     * the shared stack and produce garbage rows (worse than no data).
     * This arm merges into the same two ConcurrentHashMaps {@link #pop}
     * merges into — atomic merges, the same weakly-consistent read the
     * snapshot faces already document — so all three lanes accumulate
     * into ONE row. {@code location} carries its full dotted prefix (no
     * stack builds it here); the row appears in
     * {@link #sectionNanosSnapshot()} / {@link #sectionCountsSnapshot()}
     * and the /debug stop table like any pop-recorded row, as a
     * CROSS-CUT: where the walk runs inside another open section (the
     * relight re-sends), that section keeps its own wall time and this
     * row attributes the same span again — overlap, never a partition.
     * Gated on collection exactly like pop (a span that ends after stop
     * is dropped, pop's own semantics); no stack is held, so a throwing
     * body loses at most its one sample.
     *
     * @param location    the full dotted section name (e.g. "root.wirePublish")
     * @param elapsedNanos the span's elapsed nanoseconds
     */
    public void recordSectionSpan(String location, long elapsedNanos) {
        if (this.profiling) {
            this.timesByLocation.merge(location, elapsedNanos, Long::sum);
            this.countsByLocation.merge(location, 1L, Long::sum);
        }
    }

    /**
     * The engine's own measurement surface (NOT a reference arm — the
     * Phase-7 gate's evidence face, §22's "profiling demonstrates a
     * dense-owner bottleneck" bar): an immutable copy of the accumulated
     * section table in NANOSECONDS, keyed by the full dotted location —
     * the same table {@link #getResults} walks, but absolute and
     * UNDECAYED. The reference's *999/1000 decay belongs to the report
     * walk alone (it fires inside {@code getResults}, 1:1); this read
     * never mutates the table, so a measurement harness can sample before
     * and after a span and difference the two — the per-tick cost of any
     * section is the delta over the tick count, whatever the report
     * smoothing would have faded. Safe from any thread while the tick
     * thread is the only push/pop caller; an empty table answers an empty
     * map whether collection is on or off.
     *
     * @return the frozen section-name → accumulated-nanos table
     */
    public java.util.Map<String, Long> sectionNanosSnapshot() {
        return java.util.Map.copyOf(this.timesByLocation);
    }

    /**
     * The counts face of {@link #sectionNanosSnapshot} — the immutable
     * visit-count copy over the same keys, one count per recorded pop.
     * The same measurement-face contract: never mutated, safe from any
     * thread, an empty table answers an empty map.
     *
     * @return the frozen section-name → accumulated-visit-counts table
     */
    public java.util.Map<String, Long> sectionCountsSnapshot() {
        return java.util.Map.copyOf(this.countsByLocation);
    }

    /** Profiler.getCurrentLocation 1:1. */
    public String getCurrentLocation() {
        return this.locations.isEmpty() ? "[UNKNOWN]" : this.locations.get(this.locations.size() - 1);
    }

    /**
     * The /debug stop section table (ML-006 — B-7's ask: the console report
     * carried only the one-line summary, blocking per-subsystem attribution
     * from the console). Pure: takes frozen snapshots (so the builder never
     * mutates the table and never sees the report walk's decay), plus the
     * command's own span anchors. The header is the reference's own report
     * shape (the Time span / Tick span / ticks-per-second lines, verbatim
     * from DebugCommand.buildReport); the rows are the engine's own
     * attribution surface — every section with a time row, one line each:
     * name, visit count, average per visit, total, and the share of the
     * root total, sorted by total descending (the attribution order — the
     * top consumers first). Sections present in one table but not the
     * other are kept with the missing column as "-"; a zero count never
     * divides (avg prints "-").
     *
     * @param durationMillis the span's wall milliseconds (/debug's anchors)
     * @param durationTicks the span's tick count (/debug's anchors)
     * @param nanos a frozen sectionNanosSnapshot (undecayed — snapshot
     *              BEFORE any getResults call)
     * @param counts a matching sectionCountsSnapshot
     * @return the printed table, newline-terminated
     */
    public static String buildSectionTable(long durationMillis, long durationTicks,
            Map<String, Long> nanos, Map<String, Long> counts) {
        StringBuilder table = new StringBuilder();
        table.append("---- Section table (name, count, avg, total) ----\n");
        table.append("Time span: ").append(durationMillis).append(" ms\n");
        table.append("Tick span: ").append(durationTicks).append(" ticks\n");
        table.append("// This is approximately ")
                .append(String.format("%.2f", durationTicks / (durationMillis / 1000.0)))
                .append(" ticks per second. It should be ")
                .append(20)
                .append(" ticks per second\n\n");
        table.append(String.format("%-48s %8s %12s %12s %10s%n",
                "Section", "count", "avg/visit", "total", "% of root"));

        java.util.Set<String> names = new java.util.HashSet<>();
        names.addAll(nanos.keySet());
        names.addAll(counts.keySet());
        java.util.List<String> ordered = new java.util.ArrayList<>(names);
        java.util.Collections.sort(ordered, (a, b) -> Long.compare(
                nanos.getOrDefault(b, 0L), nanos.getOrDefault(a, 0L)));

        long rootTotal = nanos.getOrDefault("root", 0L);
        for (String name : ordered) {
            long totalNanos = nanos.getOrDefault(name, 0L);
            long count = counts.getOrDefault(name, 0L);
            String avg = count > 0
                    ? String.format("%.2f ms", totalNanos / (double) count / 1_000_000.0)
                    : "-";
            String total = String.format("%.2f ms", totalNanos / 1_000_000.0);
            String share = rootTotal > 0
                    ? String.format("%.2f%%", totalNanos * 100.0 / rootTotal)
                    : "-";
            table.append(String.format("%-48s %8d %12s %12s %10s%n",
                    name, count, avg, total, share));
        }
        return table.toString();
    }

    /**
     * Profiler.getResults 1:1 — the section's own row at index 0, the
     * direct children's percentages after (of the parent's total, of the
     * root's), the children's measured total padded up to the parent's own
     * time, the "unspecified" remainder row when the children under-count,
     * and the whole table's *999/1000 decay (the reference's own
     * smoothing — a section's share fades between reports).
     */
    public List<Result> getResults(String location) {
        if (!this.profiling) {
            return null;
        }
        String self = location;
        long rootTotal = this.timesByLocation.getOrDefault("root", 0L);
        long parentTotal = this.timesByLocation.getOrDefault(location, -1L);
        List<Result> list = new ArrayList<>();
        String prefix = location.length() > 0 ? location + "." : "";

        long childrenTotal = 0L;
        for (String key : this.timesByLocation.keySet()) {
            if (isDirectChild(key, prefix)) {
                childrenTotal += this.timesByLocation.get(key);
            }
        }

        float measuredTotal = (float) childrenTotal;
        if (childrenTotal < parentTotal) {
            childrenTotal = parentTotal;
        }
        if (rootTotal < childrenTotal) {
            rootTotal = childrenTotal;
        }

        for (String key : this.timesByLocation.keySet()) {
            if (isDirectChild(key, prefix)) {
                long time = this.timesByLocation.get(key);
                double ofParent = time * 100.0 / childrenTotal;
                double ofTotal = time * 100.0 / rootTotal;
                list.add(new Result(key.substring(prefix.length()), ofParent, ofTotal));
            }
        }

        for (String key : this.timesByLocation.keySet()) {
            this.timesByLocation.put(key, this.timesByLocation.get(key) * 999L / 1000L);
        }

        if ((float) childrenTotal > measuredTotal) {
            list.add(new Result("unspecified",
                    (childrenTotal - measuredTotal) * 100.0 / childrenTotal,
                    (childrenTotal - measuredTotal) * 100.0 / rootTotal));
        }

        list.sort(Result::compareTo);
        list.add(0, new Result(self, 100.0, childrenTotal * 100.0 / rootTotal));
        return list;
    }

    /** The reference's own direct-child gate: longer than the prefix, starts
     * with it, and no further dot below it. */
    private static boolean isDirectChild(String key, String prefix) {
        return key.length() > prefix.length()
                && key.startsWith(prefix)
                && key.indexOf(".", prefix.length() + 1) < 0;
    }

    /** Profiler.Result 1:1 — the report row, sorted by the parent share. */
    public static final class Result implements Comparable<Result> {
        public final String location;
        public final double percentageOfParent;
        public final double percentageOfTotal;

        public Result(String location, double percentageOfParent, double percentageOfTotal) {
            this.location = location;
            this.percentageOfParent = percentageOfParent;
            this.percentageOfTotal = percentageOfTotal;
        }

        @Override
        public int compareTo(Result other) {
            if (other.percentageOfParent < this.percentageOfParent) {
                return -1;
            }
            return other.percentageOfParent > this.percentageOfParent
                    ? 1
                    : other.location.compareTo(this.location);
        }

        /** The reference's own color hash (the GUI bars). */
        public int getColor() {
            return (this.location.hashCode() & 11184810) + 4473924;
        }
    }
}
