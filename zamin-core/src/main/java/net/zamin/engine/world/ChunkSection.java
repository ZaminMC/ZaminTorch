package net.zamin.engine.world;

import net.zamin.api.BlockType;

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
 */
public final class ChunkSection {

    public static final int SIZE = 16;
    public static final int VOLUME = SIZE * SIZE * SIZE;

    private final BlockType air;
    private final List<BlockType> palette;
    private final short[] indices = new short[VOLUME];
    private int nonAirCount;

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
        return true;
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
