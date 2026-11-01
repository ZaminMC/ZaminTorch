package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
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
    void theWetFarmlandNibbleIsTheMoistureSeven() {
        // The farmland state pass — FarmlandBlock.getMetadataFromState
        // lines 127-130: the MOISTURE level rides the wire nibble, and the
        // vanilla client renders the moist skin iff the received metadata
        // is exactly SEVEN (the blockstate's own moist variant). The
        // pre-audit table's 0-default wrote the wet form indistinguishable
        // from dry — every engine farmland rendered dry on the real client.
        EngineChunk chunk = flatChunk(0, 0);
        chunk.setBlock(0, 5, 0, registry.require(
                Identifier.parse("minecraft:farmland_wet")));
        chunk.setBlock(1, 5, 0, registry.require(
                Identifier.parse("minecraft:farmland")));
        byte[] data = ChunkSerializer18.serialize(chunk, true);
        int wet = readStateId(data, 5 * 256 + 0);
        assertEquals(60, wet >> 4, "farmland's legacy id");
        assertEquals(7, wet & 0xF,
                "the wet form's moisture nibble is SEVEN (the client's own"
                        + " moist-skin consult reads metadata == 7)");
        int dry = readStateId(data, 5 * 256 + 1);
        assertEquals(60, dry >> 4);
        assertEquals(0, dry & 0xF, "the dry form stays zero");
    }

    @Test
    void theVineFamilyStatesPackTheirFaceNibblesOnTheWire() {
        // The Nov 1 production crash's exact path: a jungle world's anvil
        // import carried vine_south (106|1) at the login bulk send and
        // vine_west_east (106|10) at every relight resend, and the
        // serializer's §147 loud fail threw on both (Wave VN-A had
        // registered the engine states without the wire rows). Every vine
        // state packs 106 with its OWN face bits on the nibble — the exact
        // byte a vanilla client decodes for the rendered faces.
        EngineChunk chunk = flatChunk(0, 0);
        chunk.setBlock(0, 5, 0, registry.require(
                Identifier.parse("minecraft:vine_south")));
        chunk.setBlock(1, 5, 0, registry.require(
                Identifier.parse("minecraft:vine_west_east")));
        chunk.setBlock(2, 5, 0, registry.require(
                Identifier.parse("minecraft:vine_none")));
        chunk.setBlock(3, 5, 0, registry.require(
                Identifier.parse("minecraft:vine")));
        byte[] data = ChunkSerializer18.serialize(chunk, true);
        int south = readStateId(data, 5 * 256 + 0);
        assertEquals(106, south >> 4, "the vine's legacy id");
        assertEquals(1, south & 0xF, "the south face bit (the login-bulk crasher)");
        int westEast = readStateId(data, 5 * 256 + 1);
        assertEquals(106, westEast >> 4);
        assertEquals(10, westEast & 0xF, "west 2 | east 8 (the relight-resend crasher)");
        int none = readStateId(data, 5 * 256 + 2);
        assertEquals(106, none >> 4);
        assertEquals(0, none & 0xF, "the all-clear form's faceless nibble");
        int bare = readStateId(data, 5 * 256 + 3);
        assertEquals(106, bare >> 4);
        assertEquals(15, bare & 0xF, "the bare all-faces form keeps its historical 15");
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
