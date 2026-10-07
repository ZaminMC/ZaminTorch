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
 * <p>Section layout of protocol 47: 4096 block ids (y,z,x order, byte per
 * block), 2048 bytes block light nibbles, 2048 bytes skylight nibbles.
 * Skylight is sent fully lit: the flat fixture has no lighting simulation yet,
 * and a dark world would be indistinguishable from a broken one.</p>
 */
final class ChunkSerializer18 {

    private ChunkSerializer18() {
    }

    static int sectionBitmask(EngineChunk chunk) {
        int mask = 0;
        for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
            var section = chunk.section(sectionY);
            if (section != null && !section.isEmpty()) {
                mask |= (1 << sectionY);
            }
        }
        return mask;
    }

    static byte[] serialize(EngineChunk chunk, boolean includeBiomes) {
        int bitmask = sectionBitmask(chunk);
        ByteArrayOutputStream out = new ByteArrayOutputStream(estimateSize(bitmask, includeBiomes));
        for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
            if ((bitmask & (1 << sectionY)) == 0) {
                continue;
            }
            writeSection(out, chunk, sectionY);
        }
        if (includeBiomes) {
            byte[] biomes = new byte[256];
            java.util.Arrays.fill(biomes, (byte) Protocol18.BIOME_PLAINS);
            out.writeBytes(biomes);
        }
        return out.toByteArray();
    }

    private static void writeSection(ByteArrayOutputStream out, EngineChunk chunk, int sectionY) {
        try {
            // Block ids: y outer, then z, then x — the exact order protocol 47 expects.
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockType type = chunk.getBlock(x, (sectionY << 4) | y, z);
                        out.write(legacyByte(type));
                    }
                }
            }
            out.write(new byte[2048]);                 // block light: none (surface world)
            byte[] sky = new byte[2048];
            java.util.Arrays.fill(sky, (byte) 0xFF);   // skylight: fully lit
            out.write(sky);
        } catch (IOException e) {
            throw new IllegalStateException("ByteArrayOutputStream cannot throw IOException", e);
        }
    }

    private static int legacyByte(BlockType type) {
        Optional<Integer> legacy = LegacyBlockIds.legacyId(type.identifier());
        // An unmappable block type would silently render as something else; failing
        // loudly during development beats a corrupted-looking world (§147).
        return legacy.orElseThrow(() -> new IllegalStateException(
                "Block type has no legacy 1.8.8 representation: " + type.identifier()))
                & 0xFF;
    }

    private static int estimateSize(int bitmask, boolean includeBiomes) {
        int sections = Integer.bitCount(bitmask);
        return sections * 8192 + (includeBiomes ? 256 : 0);
    }
}
