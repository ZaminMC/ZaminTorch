package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Identifier;

import java.util.Objects;

/**
 * One pending scheduled block tick — the 1:1 port of the reference's
 * {@code ScheduledTick} (reference/1.8.8 net/minecraft/server/world/
 * ScheduledTick.java, the NextTickListEntry of the MCP naming): a
 * position, the block family that asked for the wake-up, the due time
 * (total world ticks), a priority, and a global insertion id.
 *
 * <p>Equality is {@code (position, block)} — the reference's
 * {@code pos.equals && Block.areEqual} (the block is compared at
 * family identity, not state): two entries may coexist at one position
 * for two different blocks, and a re-schedule while an entry is pending
 * merges into the existing one (the reference's no-merge rule — the
 * {@code TreeSet} keeps the first). The hash follows the position alone,
 * exactly as the reference's {@code pos.hashCode()}.</p>
 *
 * <p>The ordering ({@link #compareTo}) is the reference's own triple:
 * time first, then priority, then the insertion id — the tie-break that
 * makes the drain order deterministic for entries scheduled in the same
 * tick at the same priority.</p>
 */
public final class ScheduledTickEntry implements Comparable<ScheduledTickEntry> {

    /** The reference's global insertion counter (one JVM, every world). */
    private static long idCounter;

    private final BlockPosition pos;
    private final Identifier block;
    private long time;
    private int priority;
    private final long id;

    public ScheduledTickEntry(BlockPosition pos, Identifier block) {
        this.pos = Objects.requireNonNull(pos, "pos");
        this.block = Objects.requireNonNull(block, "block");
        this.id = idCounter++;
    }

    public BlockPosition pos() {
        return pos;
    }

    public Identifier block() {
        return block;
    }

    public long time() {
        return time;
    }

    public int priority() {
        return priority;
    }

    long id() {
        return id;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ScheduledTickEntry)) {
            return false;
        }
        ScheduledTickEntry other = (ScheduledTickEntry) object;
        return pos.equals(other.pos) && block.equals(other.block);
    }

    @Override
    public int hashCode() {
        return pos.hashCode();
    }

    public ScheduledTickEntry setTime(long time) {
        this.time = time;
        return this;
    }

    public ScheduledTickEntry setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    @Override
    public int compareTo(ScheduledTickEntry other) {
        if (time < other.time) {
            return -1;
        } else if (time > other.time) {
            return 1;
        } else if (priority != other.priority) {
            return priority - other.priority;
        } else if (id < other.id) {
            return -1;
        } else {
            return id > other.id ? 1 : 0;
        }
    }

    @Override
    public String toString() {
        return block + ": " + pos + ", " + time + ", " + priority + ", " + id;
    }
}
