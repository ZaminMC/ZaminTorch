package net.zaminmc.torch.server.world;

import net.zaminmc.torch.util.Position;

/**
 * The terrain generator contract: one method fills a chunk, and the spawn
 * anchor comes from the generator (the flat world's surface, the normal
 * world's first dry column). Implementations are deterministic: the same
 * world name rebuilds identical terrain after every restart.
 */
public interface WorldGenerator {

    /** Fills {@code chunk} completely. The chunk is not observable before this returns. */
    void generate(EngineChunk chunk);

    /** The y the spawn anchor rests on (the top solid block of the spawn column). */
    int groundLevel();

    /**
     * The generator's deterministic seed (the /seed report). The default is
     * the flat fixture's zero; the full world derives a name-hash seed.
     */
    default long seed() {
        return 0L;
    }

    /** The spawn anchor. Default: the origin column, one above the surface. */
    default Position spawnPosition() {
        return new Position(0.5, groundLevel() + 1.0, 0.5);
    }
}
