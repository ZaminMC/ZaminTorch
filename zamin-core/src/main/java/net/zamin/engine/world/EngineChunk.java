package net.zamin.engine.world;

import net.zamin.api.BlockType;
import net.zamin.api.ChunkPosition;

import java.util.Objects;

/**
 * A 16x256x16 chunk column: identity + block state.
 *
 * <p>Lifecycle (Slice #1): chunks are generated fully and then published, so a
 * visible chunk is always complete ({@code loaded} only). Unloading and persistence
 * states arrive with the world-storage slice.</p>
 */
public final class EngineChunk {

    public static final int SECTION_COUNT = 16;

    private final ChunkPosition position;
    private final ChunkSection[] sections;
    private final BlockType air;

    public EngineChunk(ChunkPosition position, BlockType air) {
        this.position = Objects.requireNonNull(position, "position");
        this.air = Objects.requireNonNull(air, "air");
        this.sections = new ChunkSection[SECTION_COUNT];
        // Empty sections exist implicitly as null; they materialize on first write.
    }

    public ChunkPosition position() {
        return position;
    }

    public BlockType getBlock(int localX, int y, int localZ) {
        int sectionY = y >> 4;
        ChunkSection section = sections[sectionY];
        return section == null ? air : section.get(localX, y & 0xF, localZ);
    }

    /** @return true if the stored type changed. */
    public boolean setBlock(int localX, int y, int localZ, BlockType type) {
        int sectionY = y >> 4;
        ChunkSection section = sections[sectionY];
        if (section == null) {
            if (type.equals(air)) {
                return false; // writing air into an implicitly-empty section changes nothing
            }
            section = new ChunkSection(air);
            sections[sectionY] = section;
        }
        return section.set(localX, y & 0xF, localZ, type);
    }

    /** @return the section for the given y range (0..15), or null when implicitly empty. */
    public ChunkSection section(int sectionY) {
        return sections[sectionY];
    }

    /** @return whether any section of this chunk contains non-air blocks. */
    public boolean isEmpty() {
        for (ChunkSection section : sections) {
            if (section != null && !section.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
