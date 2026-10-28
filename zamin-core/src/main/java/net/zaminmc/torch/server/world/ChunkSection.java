package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A 16x16x16 section of blocks stored through a per-section palette.
 *
 * <p>Storage choice (Slice #1): palette of block types + one short per block.
 * It is simple, correct and measurable. Compact bit-packed variants are a
 * measured optimization for later, not a starting requirement.</p>
 *
 * <p>Threading: mutations must happen on the owning simulation context; array
 * reads from network threads are acceptable only for fully published chunks
 * that no longer mutate (true for generated chunks in Slice #1).</p>
 *
 * <p><b>The wire revision + the opaque wire-memo slot</b> (the serialized-
 * column memoization): every state-changing {@link #set} bumps a volatile
 * {@code revision} AFTER the index write (the ordering is the safety
 * linchpin — a reader that observes revision N is guaranteed to observe
 * every index write that preceded the bump to N, so a pack stored against
 * revision N can never be staler than state-at-N). The {@code wireMemo}
 * slot holds the WIRE LAYER's opaque cached packed-block array (the 1.8
 * {@code (id << 4) | metadata} LE u16 walk is the wire's dominant per-column
 * cost; an unchanged section re-serves its packed bytes instead of
 * re-walking 4096 blocks). The engine never reads the slot — the protocol
 * module owns the stored type and the read/store protocol
 * ({@code ChunkSerializer18.packedSection}: read the revision FIRST, then
 * the memo, compare; the volatile pair can serve at most a committed-newer
 * state, never a stale one — the same relaxed cross-lane contract the
 * arrays themselves already ride). The {@code revision++} is a volatile
 * read-modify-write, safe under this class's single-writer mutation
 * discipline exactly as the palette/indices writes already are.</p>
 */
public final class ChunkSection {

    public static final int SIZE = 16;
    public static final int VOLUME = SIZE * SIZE * SIZE;

    private final BlockType air;
    private final List<BlockType> palette;
    private final short[] indices = new short[VOLUME];
    private int nonAirCount;

    /**
     * The content revision: bumped by every state-changing {@link #set},
     * AFTER the index write. Volatile so the wire lanes' serialize walks
     * (netty event loop + tick lane) observe it; single-writer discipline
     * for the increment itself, exactly as the array writes.
     */
    private volatile long revision;

    /**
     * The wire layer's opaque memo slot (the cached packed-block array and
     * its revision — see the class javadoc). Volatile for safe publication:
     * the protocol module stores a fully-built immutable record, and every
     * reader that observes the reference observes the record's contents.
     * The engine never reads or writes this beyond the accessors.
     */
    private volatile Object wireMemo;

    public ChunkSection(BlockType air) {
        this.air = Objects.requireNonNull(air, "air");
        // Palette entry 0 is always air so an untouched section is all-air for free.
        this.palette = new ArrayList<>();
        this.palette.add(air);
    }

    public BlockType get(int localX, int localY, int localZ) {
        return palette.get(indices[indexOf(localX, localY, localZ)]);
    }

    /** @return true if the stored type changed. */
    public boolean set(int localX, int localY, int localZ, BlockType type) {
        Objects.requireNonNull(type, "type");
        int index = indexOf(localX, localY, localZ);
        short current = indices[index];
        BlockType currentType = palette.get(current);
        if (currentType.equals(type)) {
            return false;
        }
        short paletteIndex = paletteIndexOf(type);
        indices[index] = paletteIndex;
        if (currentType.equals(air) && !type.equals(air)) {
            nonAirCount++;
        } else if (!currentType.equals(air) && type.equals(air)) {
            nonAirCount--;
        }
        // AFTER the index write (the class javadoc's ordering law): a
        // revision-N reader is guaranteed the index writes up to N, so a
        // pack stored against N is never staler than state-at-N.
        revision++;
        return true;
    }

    /**
     * @return the content revision (bumped by every state-changing
     *         {@code set}; a no-op {@code set} leaves it unchanged).
     */
    public long revision() {
        return revision;
    }

    /** @return the wire layer's opaque memo slot (null until first stored). */
    public Object wireMemo() {
        return wireMemo;
    }

    /**
     * Stores the wire layer's opaque memo. The stored object must be
     * effectively immutable and fully built before the call (the volatile
     * write publishes it). The engine imposes no type — the protocol
     * module owns the convention.
     */
    public void storeWireMemo(Object memo) {
        this.wireMemo = memo;
    }

    /** @return whether the section contains any non-air block. */
    public boolean isEmpty() {
        return nonAirCount == 0;
    }

    /** Number of distinct types in this section's palette (diagnostics + serialization sizing). */
    public int paletteSize() {
        return palette.size();
    }

    private short paletteIndexOf(BlockType type) {
        for (short i = 0; i < palette.size(); i++) {
            if (palette.get(i).equals(type)) {
                return i;
            }
        }
        palette.add(type);
        return (short) (palette.size() - 1);
    }

    private static int indexOf(int localX, int localY, int localZ) {
        // Bounds checks are deliberate: local coordinates must be 0..15, fail loudly otherwise.
        if ((localX | localY | localZ) < 0 || localX >= SIZE || localY >= SIZE || localZ >= SIZE) {
            throw new IllegalArgumentException(
                    "Local coordinates out of section bounds: " + localX + "," + localY + "," + localZ);
        }
        return (localY << 8) | (localZ << 4) | localX;
    }
}
