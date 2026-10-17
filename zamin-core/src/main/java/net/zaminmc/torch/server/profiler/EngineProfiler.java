package net.zaminmc.torch.server.profiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private boolean profiling;
    private String currentLocation = "";
    private final Map<String, Long> timesByLocation = new HashMap<>();

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

    /** Profiler.reset 1:1 — clears the table and the stack. */
    public void reset() {
        this.timesByLocation.clear();
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

    /** Profiler.getCurrentLocation 1:1. */
    public String getCurrentLocation() {
        return this.locations.isEmpty() ? "[UNKNOWN]" : this.locations.get(this.locations.size() - 1);
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
