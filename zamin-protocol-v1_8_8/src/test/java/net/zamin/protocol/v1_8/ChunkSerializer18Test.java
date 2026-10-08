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

/**
 * The protocol 47 chunk payload layout, pinned to the community-verified
 * packing (prismarine-chunk pc/1.8 is the real client's parser): LE u16
 * {@code (id << 4) | metadata} per block, y->z->x order, and the three arrays
 * grouped across the chunk (all blocks, all block light, all sky light).
 */
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
    void serializedSectionCarriesPackedStateIdsInProtocolOrder() {
        EngineChunk chunk = flatChunk(2, -3);
        byte[] data = ChunkSerializer18.serialize(chunk, true);
        // One section: 8192 block bytes + 2048 block light + 2048 sky light,
        // then the biome array (256).
        assertEquals(12288 + 256, data.length);

        // Protocol order is y outer, then z, then x: block index =
        // y*256 + z*16 + x; each block is a little-endian u16
        // (id << 4) | metadata at offset index*2.
        // (x=0, z=0): y=0 bedrock(7), y=1 dirt(3), y=4 grass(2), y=5 air(0).
        assertEquals(7, readStateId(data, 0) >> 4, "bedrock id at y=0");
        assertEquals(3, readStateId(data, 256) >> 4, "dirt id at y=1");
        assertEquals(2, readStateId(data, 1024) >> 4, "grass id at y=4");
        assertEquals(0, readStateId(data, 1280) >> 4, "air above ground");
        assertEquals(0, readStateId(data, 256) & 0xF, "metadata zero this slice");

        // Grouped layout: block light region zero, skylight region fully lit,
        // biome array right after the skylight region.
        boolean blockLightZero = true;
        for (int i = 8192; i < 8192 + 2048; i++) {
            if (data[i] != 0) {
                blockLightZero = false;
                break;
            }
        }
        assertTrue(blockLightZero, "block light must be zero");
        boolean skylightLit = true;
        for (int i = 10240; i < 12288; i++) {
            if (data[i] != (byte) 0xFF) {
                skylightLit = false;
                break;
            }
        }
        assertTrue(skylightLit, "skylight must be fully lit (no lighting simulation yet)");
        assertEquals(1, data[12288] & 0xFF, "biome plains");
    }

    @Test
    void emptyChunkProducesOnlyBiomes() {
        EngineChunk chunk = new EngineChunk(new ChunkPosition(10, 10),
                registry.require(Identifier.parse("minecraft:air")));
        assertEquals(0, ChunkSerializer18.sectionBitmask(chunk));
        byte[] data = ChunkSerializer18.serialize(chunk, true);
        assertEquals(256, data.length);
    }

    private static int readStateId(byte[] data, int blockIndex) {
        int offset = blockIndex * 2;
        return ((data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8));
    }
}
