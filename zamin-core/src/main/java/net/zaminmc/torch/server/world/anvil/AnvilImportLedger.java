package net.zaminmc.torch.server.world.anvil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The import's isolation ledger (§31.6's loud bookkeeping): every outcome
 * of the walk is counted — the installed chunks, the unoccupied entries,
 * and every isolation branch by its exact reason, with the first few
 * example subjects kept for the operator's report. The ledger never
 * silently substitutes: a cell the engine cannot represent becomes air
 * WITH a counted reason; a section with corrupt arrays is skipped WITH a
 * counted reason; a chunk the format gates reject is refused WITH a
 * counted reason — and the summary the operator reads names every branch.
 */
public final class AnvilImportLedger {

    private final AtomicLong chunksInstalled = new AtomicLong();
    private final AtomicLong chunksSkippedLoaded = new AtomicLong();
    private final AtomicLong entriesUnoccupied = new AtomicLong();
    private final AtomicLong regionFilesOpened = new AtomicLong();
    private final AtomicLong regionFilesIsolated = new AtomicLong();
    private final AtomicLong entitiesImported = new AtomicLong();
    private final AtomicLong tilesImported = new AtomicLong();
    private final AtomicLong tileTicksImported = new AtomicLong();

    /** The per-reason isolation counts (the loud report's rows). */
    private final Map<String, AtomicLong> reasons = new TreeMap<>();
    /** The ignored-but-present format rows (the used-vs-ignored ledger). */
    private final Map<String, AtomicLong> ignored = new TreeMap<>();
    /** The unknown numeric states with their counts (the id isolation). */
    private final Map<String, AtomicLong> unknownStates = new TreeMap<>();
    /** Example subjects per reason, for the operator-facing summary. */
    private final Map<String, StringBuilder> examples = new LinkedHashMap<>();

    private static final int MAX_EXAMPLES_PER_REASON = 4;

    /** One chunk installed through the publication path. */
    public void chunkInstalled() {
        chunksInstalled.incrementAndGet();
    }

    /** One chunk the live world already had (the publication rule's skip). */
    public void chunkSkippedAlreadyLoaded() {
        chunksSkippedLoaded.incrementAndGet();
    }

    /** One unoccupied region entry (the format's own empty answer). */
    public void entryUnoccupied() {
        entriesUnoccupied.incrementAndGet();
    }

    /** One region file opened and walked. */
    public void regionFileOpened() {
        regionFilesOpened.incrementAndGet();
    }

    /** One region file refused whole (short header, unreadable). */
    public void regionFileIsolated(String reason) {
        regionFilesIsolated.incrementAndGet();
        count(reasons, reason);
    }

    /** One entity imported through the content seams (mob or item). */
    public void entityImported() {
        entitiesImported.incrementAndGet();
    }

    /** One tile entity applied through the per-family read walk. */
    public void tileImported() {
        tilesImported.incrementAndGet();
    }

    /** One scheduled block tick landed through the import arm (the TileTicks row). */
    public void tileTickImported() {
        tileTicksImported.incrementAndGet();
    }

    /** One isolation, by its exact reason. */
    public void isolated(String reason, String subject) {
        count(reasons, reason);
        StringBuilder seen = examples.computeIfAbsent(reason, key -> new StringBuilder());
        if (seen.length() < MAX_EXAMPLES_PER_REASON * 48) {
            if (seen.length() > 0) {
                seen.append("; ");
            }
            seen.append(subject);
        }
    }

    /** One ignored-but-present format row (the not-imported ledger). */
    public void ignored(String row, long count) {
        AtomicLong slot = ignored.computeIfAbsent(row, key -> new AtomicLong());
        slot.addAndGet(count);
    }

    /** One unknown numeric state (counted per exact state, air'd). */
    public void unknownState(int numericId, int metadata) {
        count(unknownStates, "id " + numericId + " meta " + metadata);
    }

    private static void count(Map<String, AtomicLong> into, String key) {
        into.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    /** @return how many chunks the import installed. */
    public long chunksInstalled() {
        return chunksInstalled.get();
    }

    /** @return how many chunks the live world already had (skipped). */
    public long chunksSkippedLoaded() {
        return chunksSkippedLoaded.get();
    }

    /** @return how many region entries were unoccupied. */
    public long entriesUnoccupied() {
        return entriesUnoccupied.get();
    }

    /** @return how many region files were opened. */
    public long regionFilesOpened() {
        return regionFilesOpened.get();
    }

    /** @return how many entities the content seams imported. */
    public long entitiesImported() {
        return entitiesImported.get();
    }

    /** @return how many tile entities the per-family walk applied. */
    public long tilesImported() {
        return tilesImported.get();
    }

    /** @return how many scheduled block ticks the import arm landed. */
    public long tileTicksImported() {
        return tileTicksImported.get();
    }

    /** @return the total of every isolation count across all reasons. */
    public long totalIsolated() {
        return reasons.values().stream().mapToLong(AtomicLong::get).sum();
    }

    /**
     * The operator-facing summary: the installed/skipped/unoccupied rows,
     * every isolation reason with its count and first examples, and the
     * ignored-format rows — the §31.6 report the import command answers
     * with (never a silent default).
     */
    public String summary() {
        StringBuilder out = new StringBuilder();
        out.append("installed ").append(chunksInstalled.get()).append(" chunks")
                .append(", skipped ").append(chunksSkippedLoaded.get())
                .append(" (already loaded), unoccupied ").append(entriesUnoccupied.get())
                .append(" entries, ").append(regionFilesOpened.get()).append(" region files")
                .append(", ").append(entitiesImported.get()).append(" entities")
                .append(", ").append(tilesImported.get()).append(" tile entities")
                .append(", ").append(tileTicksImported.get()).append(" scheduled block ticks");
        if (regionFilesIsolated.get() > 0) {
            out.append(", ").append(regionFilesIsolated.get())
                    .append(" region files isolated");
        }
        if (!reasons.isEmpty()) {
            out.append(" | isolations: ");
            boolean first = true;
            for (Map.Entry<String, AtomicLong> reason : reasons.entrySet()) {
                if (!first) {
                    out.append("; ");
                }
                first = false;
                out.append(reason.getKey()).append(" x").append(reason.getValue().get());
                StringBuilder seen = examples.get(reason.getKey());
                if (seen != null && seen.length() > 0) {
                    out.append(" [").append(seen).append("]");
                }
            }
        }
        if (!unknownStates.isEmpty()) {
            out.append(" | unknown block states (imported as air): ");
            boolean first = true;
            for (Map.Entry<String, AtomicLong> state : unknownStates.entrySet()) {
                if (!first) {
                    out.append(", ");
                }
                first = false;
                out.append(state.getKey()).append(" x").append(state.getValue().get());
            }
        }
        if (!ignored.isEmpty()) {
            out.append(" | present but not imported (this slice): ");
            boolean first = true;
            for (Map.Entry<String, AtomicLong> row : ignored.entrySet()) {
                if (!first) {
                    out.append(", ");
                }
                first = false;
                out.append(row.getKey()).append(" x").append(row.getValue().get());
            }
        }
        return out.toString();
    }
}
