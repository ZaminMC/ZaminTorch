package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Identifier;

import java.util.Objects;

/**
 * Deterministic flat-world generator: the Slice #1 world fixture.
 *
 * <p>Determinism matters: tests, spawn calculation and network serialization all
 * rely on the same known world. Real terrain generation is a later slice with its
 * own dependency analysis.</p>
 */
public final class FlatWorldGenerator {

    private final BlockRegistry registry;
    private final int groundLevel;

    public FlatWorldGenerator(BlockRegistry registry, int groundLevel) {
        this.registry = Objects.requireNonNull(registry, "registry");
        if (groundLevel < 1 || groundLevel > 250) {
            throw new IllegalArgumentException("groundLevel out of sensible range: " + groundLevel);
        }
        this.groundLevel = groundLevel;
    }

    public int groundLevel() {
        return groundLevel;
    }

    /** Fills {@code chunk} completely. The chunk is not observable before this returns. */
    public void generate(EngineChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        Identifier airId = Identifier.parse("minecraft:air");
        BlockType air = registry.require(airId);
        BlockType bedrock = registry.require(Identifier.parse("minecraft:bedrock"));
        BlockType dirt = registry.require(Identifier.parse("minecraft:dirt"));
        BlockType grass = registry.require(Identifier.parse("minecraft:grass_block"));

        ChunkPosition position = chunk.position();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                chunk.setBlock(lx, 0, lz, bedrock);
                for (int y = 1; y < groundLevel; y++) {
                    chunk.setBlock(lx, y, lz, dirt);
                }
                chunk.setBlock(lx, groundLevel, lz, grass);
                // Everything above stays air (implicit empty sections), which the
                // palette model represents without materializing data.
                assert chunk.getBlock(lx, groundLevel + 1, lz).equals(air);
            }
        }
    }
}
