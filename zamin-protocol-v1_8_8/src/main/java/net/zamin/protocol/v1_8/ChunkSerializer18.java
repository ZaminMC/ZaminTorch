package net.zamin.protocol.v1_8;

import net.zamin.api.BlockType;
import net.zamin.engine.world.EngineChunk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * Produces the 1.8.8 chunk wire payload (without packet header) from engine
 * chunk state. The engine never sees this format; the wire format never leaks
 * back into the world model.
 *
 * <p>Section layout of protocol 47 (community-verified against the real
 * client's parser, prismarine-chunk pc/1.8, and the historical
 * {@code Chunk.func_150812_a} packing): each block is one <b>little-endian
 * u16</b> carrying {@code (id << 4) | metadata}, written y-then-z-then-x (x
 * fastest); 4096 blocks make an 8192-byte section array. The three arrays are
 * <b>grouped across the whole chunk</b>, not interleaved per section: first
 * every included section's block array, then every section's 2048-byte block
 * light nibbles, then every section's 2048-byte skylight nibbles, then the
 * 256-byte biome array for ground-up chunks. Light comes from the world's
 * light storage (the LightEngine): the nibble packing (low nibble = even
 * index, y<<8|z<<4|x) is identical, so the arrays copy verbatim. Sections the
 * bitmask omits are assumed fully-lit sky by the client (its null-storage
 * default) — matching the model's implicit defaults — so a section must ride
 * the wire when its light diverges from that default even if it holds no
 * blocks (a torch's light in an otherwise empty section).</p>
 */
final class ChunkSerializer18 {

    private static final int SECTION_BLOCKS = 4096;
    private static final int SECTION_BLOCK_BYTES = SECTION_BLOCKS * 2;
    private static final int SECTION_LIGHT_BYTES = SECTION_BLOCKS / 2;
    private static final int SECTION_TOTAL_BYTES =
            SECTION_BLOCK_BYTES + SECTION_LIGHT_BYTES * 2;

    private ChunkSerializer18() {
    }

    /**
     * @return the section bitmask: every section with blocks OR non-default
     *         light. The client reconstructs only masked sections; an unmasked
     *         section keeps its assumed defaults (blocks air, block light 0,
     *         skylight 15) — exactly the model's implicit defaults.
     */
    static int sectionBitmask(EngineChunk chunk) {
        int mask = 0;
        for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
            var section = chunk.section(sectionY);
            if ((section != null && !section.isEmpty()) || chunk.sectionNeedsLightWire(sectionY)) {
                mask |= (1 << sectionY);
            }
        }
        return mask;
    }

    static byte[] serialize(EngineChunk chunk, boolean includeBiomes) {
        int bitmask = sectionBitmask(chunk);
        int sections = Integer.bitCount(bitmask);
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                sections * SECTION_TOTAL_BYTES + (includeBiomes ? 256 : 0));
        try {
            // Pass 1: block ids packed (id << 4) | metadata, one LE u16 per
            // block, y -> z -> x, for every included section in y order.
            for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
                if ((bitmask & (1 << sectionY)) == 0) {
                    continue;
                }
                writeBlockArray(out, chunk, sectionY);
            }
            // Pass 2: block light nibbles for every included section, from the
            // world's light storage (verbatim: identical nibble packing).
            for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
                if ((bitmask & (1 << sectionY)) != 0) {
                    out.write(chunk.blockLightArray(sectionY));
                }
            }
            // Pass 3: skylight nibbles for every included section, same storage.
            for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
                if ((bitmask & (1 << sectionY)) != 0) {
                    out.write(chunk.skyLightArray(sectionY));
                }
            }
            if (includeBiomes) {
                byte[] biomes = new byte[256];
                java.util.Arrays.fill(biomes, (byte) Protocol18.BIOME_PLAINS);
                out.writeBytes(biomes);
            }
        } catch (IOException e) {
            throw new IllegalStateException("ByteArrayOutputStream cannot throw IOException", e);
        }
        return out.toByteArray();
    }

    private static void writeBlockArray(ByteArrayOutputStream out, EngineChunk chunk,
                                        int sectionY) throws IOException {
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockType type = chunk.getBlock(x, (sectionY << 4) | y, z);
                    int stateId = legacyStateId(type) << 4; // metadata 0 this slice
                    out.write(stateId & 0xFF);          // low byte first (LE)
                    out.write((stateId >> 8) & 0xFF);
                }
            }
        }
    }

    private static int legacyStateId(BlockType type) {
        Optional<Integer> legacy = LegacyBlockIds.legacyId(type.identifier());
        // An unmappable block type would silently render as something else; failing
        // loudly during development beats a corrupted-looking world (§147).
        return legacy.orElseThrow(() -> new IllegalStateException(
                "Block type has no legacy 1.8.8 representation: " + type.identifier()));
    }

    /** The wire's packed block-state value: {@code (legacy id << 4) | metadata}. */
    static int packedStateId(BlockType type) {
        return legacyStateId(type) << 4;
    }
}
