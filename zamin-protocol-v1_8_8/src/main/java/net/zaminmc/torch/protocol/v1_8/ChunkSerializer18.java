package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.world.ChunkSection;
import net.zaminmc.torch.server.world.EngineChunk;

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

    /**
     * The packed form of an implicitly-empty (null) section: every cell
     * air — legacy id 0, metadata 0 — so the whole 8192-byte array is zero.
     * A shared immutable constant (pinned against the per-block walk by the
     * memo test): callers only ever copy from it.
     */
    private static final byte[] ALL_AIR_SECTION = new byte[SECTION_BLOCK_BYTES];

    /**
     * The per-section memo record stored in {@link ChunkSection}'s opaque
     * slot: the packed block array against the revision it was walked at.
     * Effectively immutable after store (the array is never mutated once
     * published — the serializer only ever copies from it).
     */
    private record PackedSectionMemo(long revision, byte[] packed) {
    }

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
        return serialize(chunk, includeBiomes, sectionBitmask(chunk));
    }

    /**
     * The partial-resend mask law (the reference's own effective-mask walk,
     * WorldChunkS2CPacket.writeChunkData lines 68-74: a requested section
     * rides only when the chunk actually holds it — {@code worldchunksection
     * != null} — the written mask is the intersected one, never the request).
     * The block-change flush's full-buffer arm passes the per-chunk dirty
     * section mask the way the reference's ChunkHolder does
     * (ChunkMap.java line 372: {@code new WorldChunkS2CPacket(chunk, false,
     * this.dirtySections)}).
     *
     * @param requested the sections the caller believes changed (bit per y>>4)
     * @return the requested sections the chunk can actually fill
     */
    static int effectiveSectionMask(EngineChunk chunk, int requested) {
        int mask = 0;
        for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
            if ((requested & (1 << sectionY)) != 0 && chunk.section(sectionY) != null) {
                mask |= (1 << sectionY);
            }
        }
        return mask;
    }

    /**
     * The serialize over a caller-chosen section mask (the partial-resend
     * arm): the same three-grouped arrays as the full form, sized by the
     * mask's own population — the client computes the same size from the
     * written mask bit-for-bit (the reference's findBufferSize, lines 55-61).
     * The mask must be an EFFECTIVE one (see {@link #effectiveSectionMask})
     * or the full-form {@link #sectionBitmask} — the writer and the client
     * both trust it to name only fillable sections.
     */
    static byte[] serialize(EngineChunk chunk, boolean includeBiomes, int sectionMask) {
        int bitmask = sectionMask;
        int sections = Integer.bitCount(bitmask);
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                sections * SECTION_TOTAL_BYTES + (includeBiomes ? 256 : 0));
        try {
            // Pass 1: block ids packed (id << 4) | metadata, one LE u16 per
            // block, y -> z -> x, for every included section in y order.
            // The per-section packed array rides the memo (an unchanged
            // section re-serves its bytes; the walk runs on change alone).
            for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
                if ((bitmask & (1 << sectionY)) == 0) {
                    continue;
                }
                out.write(packedSection(chunk, sectionY));
            }
            // Pass 2: block light nibbles for every included section, from the
            // world's light storage (verbatim: identical nibble packing).
            // Light is NEVER memoized — every serialize reads the live
            // arrays, exactly the pre-memo behavior (light changes do not
            // touch the block memo, and vice versa).
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
                // The generation-derived 16x16 row (x fastest — the wire's
                // exact layout). A chunk generation never wrote carries the
                // plains default in the array itself. Never memoized — the
                // live row every serialize, exactly the pre-memo behavior.
                out.write(chunk.biomeArray());
            }
        } catch (IOException e) {
            throw new IllegalStateException("ByteArrayOutputStream cannot throw IOException", e);
        }
        return out.toByteArray();
    }

    /**
     * The serialized-column memoization's serving arm: this section's packed
     * block array — the memo's when the section's content revision is
     * unchanged since the last walk, a fresh walk (stored) when it is not.
     *
     * <p><b>The read/store protocol</b> (the safety linchpin — the volatile
     * ordering is what makes a stale serve impossible): the REVISION is read
     * FIRST, then the memo slot; a store records the revision read BEFORE
     * its walk. Under the volatile total order this admits exactly one
     * imprecision — serving a pack that includes a mutation the reader's
     * revision read could not yet see (a committed, real state — the same
     * tearing window the unmemoized per-block walk always rode on the
     * cross-lane serialize paths) — and NEVER a pack staler than the
     * revision it was served against. The bump inside {@code ChunkSection
     * .set} lands AFTER the index write, so a revision-N packer observes
     * every index write up to N: the stored pair (N, bytes) always carries
     * bytes at least as fresh as state-at-N.</p>
     *
     * <p>The returned array — memo or fresh — is owned by the serializer:
     * callers copy from it, never into it. A null (implicitly empty)
     * section serves the shared all-air constant (air is legacy id 0 with
     * metadata 0 — the whole array zero, pinned by test against the walk).</p>
     *
     * <p>Invalidation surface: {@code ChunkSection.set} alone — the single
     * method every block mutation in the engine flows through. Light and
     * biome writes never invalidate (they never ride this memo).</p>
     */
    private static byte[] packedSection(EngineChunk chunk, int sectionY) {
        ChunkSection section = chunk.section(sectionY);
        if (section == null) {
            return ALL_AIR_SECTION;
        }
        long revision = section.revision();
        Object slot = section.wireMemo();
        if (slot instanceof PackedSectionMemo memo && memo.revision() == revision) {
            return memo.packed();
        }
        byte[] packed = packBlocks(section);
        section.storeWireMemo(new PackedSectionMemo(revision, packed));
        return packed;
    }

    /**
     * The per-block pack walk — verbatim the pre-memo law (same cell order,
     * same {@code (id << 4) | metadata} LE u16 packing), into a standalone
     * array instead of the shared stream. The memo test carries a verbatim
     * copy of the original stream walk to pin this against drift.
     */
    private static byte[] packBlocks(ChunkSection section) {
        byte[] packed = new byte[SECTION_BLOCK_BYTES];
        int offset = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockType type = section.get(x, y, z);
                    // (id << 4) | metadata — fluids carry their flow level in
                    // the nibble (0 source, 1..7 flowing, 8 falling), the
                    // historical 1.8 encoding; plain blocks ride metadata 0.
                    int stateId = legacyStateId(type) << 4
                            | LegacyBlockIds.metadataOf(type.identifier());
                    packed[offset++] = (byte) (stateId & 0xFF);   // low byte first (LE)
                    packed[offset++] = (byte) ((stateId >> 8) & 0xFF);
                }
            }
        }
        return packed;
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
        return legacyStateId(type) << 4 | LegacyBlockIds.metadataOf(type.identifier());
    }
}
