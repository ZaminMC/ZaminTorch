package net.zamin.protocol.v1_8;

import net.zamin.api.ChunkPosition;
import net.zamin.api.Identifier;
import net.zamin.engine.block.BlockRegistryBuilder;
import net.zamin.engine.block.BuiltinBlocks;
import net.zamin.engine.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zamin.engine.world.EngineChunk;
import net.zamin.engine.world.FlatWorldGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkSerializer18Test {

    private static FrozenBlockRegistry registry;
    private static FlatWorldGenerator generator;

    @BeforeAll
    static void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        generator = new FlatWorldGenerator(registry, 4);
    }

    private EngineChunk flatChunk(int x, int z) {
        EngineChunk chunk = new EngineChunk(new ChunkPosition(x, z),
                registry.require(Identifier.parse("minecraft:air")));
        generator.generate(chunk);
        return chunk;
    }

    @Test
    void flatChunkOnlyHasGroundSection() {
        EngineChunk chunk = flatChunk(0, 0);
        // Ground level 4 -> all blocks live in section 0; nothing above.
        assertEquals(0x0001, ChunkSerializer18.sectionBitmask(chunk));
    }

    @Test
    void serializedSectionCarriesLegacyIdsInProtocolOrder() {
        EngineChunk chunk = flatChunk(2, -3);
        byte[] data = ChunkSerializer18.serialize(chunk, true);
        // Section (8192) + biome array (256).
        assertEquals(8192 + 256, data.length);

        // Protocol order is y outer, then z, then x: index = y*256 + z*16 + x.
        // (x=0, z=0): y=0 bedrock, y=1 dirt, y=4 grass, y=5 air.
        assertEquals(7, data[0] & 0xFF, "bedrock at y=0");
        assertEquals(3, data[256] & 0xFF, "dirt at y=1");
        assertEquals(2, data[1024] & 0xFF, "grass at y=4");
        assertEquals(0, data[1280] & 0xFF, "air above ground");

        // Block light region is zero, skylight region fully lit.
        boolean skylightLit = true;
        for (int i = 4096 + 2048; i < 8192; i++) {
            if (data[i] != (byte) 0xFF) {
                skylightLit = false;
                break;
            }
        }
        assertTrue(skylightLit, "skylight must be fully lit (no lighting simulation yet)");
        assertEquals(1, data[8192] & 0xFF, "biome plains");
    }

    @Test
    void emptyChunkProducesOnlyBiomes() {
        EngineChunk chunk = new EngineChunk(new ChunkPosition(10, 10),
                registry.require(Identifier.parse("minecraft:air")));
        assertEquals(0, ChunkSerializer18.sectionBitmask(chunk));
        byte[] data = ChunkSerializer18.serialize(chunk, true);
        assertEquals(256, data.length);
    }
}
