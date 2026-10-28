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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The serialized-column memoization's law: the wire's dominant per-column
 * cost is the 4096-block legacy-id walk, and an UNCHANGED section re-serves
 * its packed bytes from {@link net.zaminmc.torch.server.world.ChunkSection}'s
 * opaque memo slot instead of re-walking. The invalidation surface is
 * {@code ChunkSection.set} alone — every block mutation in the engine flows
 * through it (verified: one caller engine-wide); light and biome writes
 * never ride the memo and never invalidate it.
 *
 * <p>Every byte-level assertion diffs the memoized serializer against a
 * VERBATIM COPY of the pre-memo law (the original per-block stream walk,
 * copied before the memo landed) — the drift-guard convention, so a future
 * edit to the pack walk cannot silently diverge from the law this replaced.</p>
 */
class SectionPackMemoTest {

    private static FrozenBlockRegistry registry;
    private static FlatWorldGenerator generator;

    @BeforeAll
    static void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        generator = new FlatWorldGenerator(registry, 4);
    }

    // ------------------------------------------------------------------
    // The verbatim pre-memo law (the drift guard)
    // ------------------------------------------------------------------

    /**
     * A byte-for-byte copy of the ORIGINAL serialize (the pre-memo
     * ChunkSerializer18: per-block {@code chunk.getBlock} walk, two stream
     * writes per block). Never edit: the memoized production serializer must
     * stay byte-identical to this law.
     */
    private static byte[] verbatimPreMemoSerialize(EngineChunk chunk, boolean includeBiomes,
                                                   int sectionMask) {
        int bitmask = sectionMask;
        int sections = Integer.bitCount(bitmask);
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                sections * (8192 + 2048 * 2) + (includeBiomes ? 256 : 0));
        try {
            for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
                if ((bitmask & (1 << sectionY)) == 0) {
                    continue;
                }
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            var type = chunk.getBlock(x, (sectionY << 4) | y, z);
                            int stateId = ChunkSerializer18.packedStateId(type);
                            out.write(stateId & 0xFF);
                            out.write((stateId >> 8) & 0xFF);
                        }
                    }
                }
            }
            for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
                if ((bitmask & (1 << sectionY)) != 0) {
                    out.write(chunk.blockLightArray(sectionY));
                }
            }
            for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
                if ((bitmask & (1 << sectionY)) != 0) {
                    out.write(chunk.skyLightArray(sectionY));
                }
            }
            if (includeBiomes) {
                out.write(chunk.biomeArray());
            }
        } catch (IOException e) {
            throw new IllegalStateException("ByteArrayOutputStream cannot throw IOException", e);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private EngineChunk flatChunk(int x, int z) {
        EngineChunk chunk = new EngineChunk(new ChunkPosition(x, z),
                registry.require(Identifier.parse("minecraft:air")));
        generator.generate(chunk);
        return chunk;
    }

    /**
     * The variety fixture: the flat ground (section 0), scattered blocks in
     * sections 1 and 2 (stone, glass, and the metadata carriers — tall grass
     * rides 1, fern rides 2), a light-only section far above (block light in
     * section 6 with NO blocks — the null-section serving path), a biome
     * override, and non-default light inside a populated section.
     */
    private EngineChunk varietyChunk() {
        EngineChunk chunk = flatChunk(3, -7);
        var stone = registry.require(Identifier.parse("minecraft:stone"));
        var glass = registry.require(Identifier.parse("minecraft:glass"));
        var tallGrass = registry.require(Identifier.parse("minecraft:tall_grass"));
        var fern = registry.require(Identifier.parse("minecraft:fern"));
        // Section 1 (y 16-31): a scatter.
        for (int i = 0; i < 40; i++) {
            chunk.setBlock((i * 7) & 0xF, 16 + ((i * 5) & 0xF), (i * 3) & 0xF,
                    (i & 1) == 0 ? stone : glass);
        }
        chunk.setBlock(4, 20, 4, tallGrass);  // metadata 1
        chunk.setBlock(5, 21, 5, fern);       // metadata 2
        // Section 2 (y 32-47): sparse.
        chunk.setBlock(0, 32, 0, stone);
        chunk.setBlock(15, 47, 15, glass);
        // Light-only section 6 (y 96-111): NO blocks, one torch-light cell —
        // the mask bit from sectionNeedsLightWire, the block arm all-air.
        chunk.setBlockLight(8, 100, 8, 14);
        // Non-default light inside populated section 1.
        chunk.setBlockLight(1, 17, 1, 7);
        // One biome override (desert is wire id 2).
        chunk.setBiome(3, 5, (byte) 2);
        return chunk;
    }

    // ------------------------------------------------------------------
    // Byte identity against the verbatim pre-memo law
    // ------------------------------------------------------------------

    @Test
    void memoizedSerializeIsByteIdenticalToThePreMemoLaw() {
        EngineChunk chunk = varietyChunk();
        int mask = ChunkSerializer18.sectionBitmask(chunk);
        // Cold (first serialize — every populated section walks and stores),
        // warm (re-serialize — every unchanged section serves the memo), and
        // post-mutation (one section re-walks, the rest still hit).
        byte[] cold = ChunkSerializer18.serialize(chunk, true);
        assertArrayEquals(verbatimPreMemoSerialize(chunk, true, mask), cold, "cold walk");

        byte[] warm = ChunkSerializer18.serialize(chunk, true);
        assertArrayEquals(cold, warm, "warm re-serialize");
        assertArrayEquals(verbatimPreMemoSerialize(chunk, true, mask), warm, "warm vs the law");

        chunk.setBlock(2, 18, 2, registry.require(Identifier.parse("minecraft:glass")));
        byte[] afterMutation = ChunkSerializer18.serialize(chunk, true);
        assertArrayEquals(verbatimPreMemoSerialize(chunk, true,
                ChunkSerializer18.sectionBitmask(chunk)), afterMutation, "post-mutation");
    }

    @Test
    void partialMaskAndGroundUpFalseFormsStayByteIdentical() {
        EngineChunk chunk = varietyChunk();
        // The partial-resend arm: a dirty mask over a subset of the sections.
        int dirtyMask = (1 << 0) | (1 << 1);
        byte[] partial = ChunkSerializer18.serialize(chunk, false, dirtyMask);
        assertArrayEquals(verbatimPreMemoSerialize(chunk, false, dirtyMask), partial);

        // The ground-up-false full form (the relight re-send shape).
        byte[] relight = ChunkSerializer18.serialize(chunk, false);
        assertArrayEquals(verbatimPreMemoSerialize(chunk, false,
                ChunkSerializer18.sectionBitmask(chunk)), relight);

        // Warming the other forms must not disturb the full-form bytes either.
        byte[] warmFull = ChunkSerializer18.serialize(chunk, true);
        assertArrayEquals(verbatimPreMemoSerialize(chunk, true,
                ChunkSerializer18.sectionBitmask(chunk)), warmFull);
    }

    @Test
    void emptyChunkStaysBiomesAlone() {
        EngineChunk chunk = new EngineChunk(new ChunkPosition(9, 9),
                registry.require(Identifier.parse("minecraft:air")));
        assertEquals(0, ChunkSerializer18.sectionBitmask(chunk));
        byte[] data = ChunkSerializer18.serialize(chunk, true);
        assertEquals(256, data.length);
        byte[] again = ChunkSerializer18.serialize(chunk, true);
        assertArrayEquals(data, again);
    }

    // ------------------------------------------------------------------
    // The memo's own law: hits, invalidation, and the surfaces it ignores
    // ------------------------------------------------------------------

    @Test
    void unchangedSectionsReServeTheStoredMemo() {
        EngineChunk chunk = varietyChunk();
        ChunkSerializer18.serialize(chunk, true);
        Object slotAfterFirst = chunk.section(1).wireMemo();
        // Non-null: the populated section stored its pack.
        org.junit.jupiter.api.Assertions.assertNotNull(slotAfterFirst);
        ChunkSerializer18.serialize(chunk, true);
        ChunkSerializer18.serialize(chunk, false);
        // The SAME record object across re-serializes of either ground-up
        // form: the section's content revision never changed, so the pack
        // was re-served, never re-walked (never replaced).
        assertSame(slotAfterFirst, chunk.section(1).wireMemo());
    }

    @Test
    void blockMutationInvalidatesExactlyTheMutatedSection() {
        EngineChunk chunk = varietyChunk();
        ChunkSerializer18.serialize(chunk, true);
        Object section1Slot = chunk.section(1).wireMemo();
        Object section0Slot = chunk.section(0).wireMemo();
        Object section2Slot = chunk.section(2).wireMemo();
        org.junit.jupiter.api.Assertions.assertNotNull(section2Slot);
        // A real change in section 1 alone — read the cell first and write
        // a DIFFERENT type, so the change is guaranteed (a scatter position
        // may already hold either scatter member).
        var stone = registry.require(Identifier.parse("minecraft:stone"));
        var glass = registry.require(Identifier.parse("minecraft:glass"));
        var before = chunk.getBlock(9, 19, 9);
        var replacement = before.identifier().equals(stone.identifier()) ? glass : stone;
        long revisionBefore = chunk.section(1).revision();
        org.junit.jupiter.api.Assertions.assertTrue(chunk.setBlock(9, 19, 9, replacement));
        // The mutation moves the section's content revision — the stored
        // pack no longer matches it, so the NEXT serialize must re-walk
        // this section (and only this one) and replace its slot.
        org.junit.jupiter.api.Assertions.assertTrue(chunk.section(1).revision() > revisionBefore);
        byte[] column = ChunkSerializer18.serialize(chunk, true);
        assertNotSame(section1Slot, chunk.section(1).wireMemo(),
                "the mutated section's pack was rebuilt");
        assertSame(section0Slot, chunk.section(0).wireMemo(),
                "the untouched section's pack survived");
        assertSame(section2Slot, chunk.section(2).wireMemo(),
                "the far section's pack survived");
        // And the rebuilt column is byte-identical to the law.
        assertArrayEquals(verbatimPreMemoSerialize(chunk, true,
                ChunkSerializer18.sectionBitmask(chunk)), column);
    }

    @Test
    void noOpSetNeverInvalidates() {
        EngineChunk chunk = flatChunk(0, 0);
        var grass = chunk.getBlock(4, 4, 4);
        long before = chunk.section(0).revision();
        // Writing the SAME type back: set answers false, the revision must
        // not move, and the memo must stay valid (the wire bytes cannot
        // have changed — this is the no-op-set law).
        org.junit.jupiter.api.Assertions.assertFalse(chunk.setBlock(4, 4, 4, grass));
        assertEquals(before, chunk.section(0).revision());
        ChunkSerializer18.serialize(chunk, true);
        Object slot = chunk.section(0).wireMemo();
        chunk.setBlock(4, 4, 4, grass);
        ChunkSerializer18.serialize(chunk, true);
        assertSame(slot, chunk.section(0).wireMemo());
    }

    @Test
    void lightWritesNeverTouchTheBlockMemo() {
        EngineChunk chunk = varietyChunk();
        ChunkSerializer18.serialize(chunk, true);
        Object section1Slot = chunk.section(1).wireMemo();
        byte[] before = ChunkSerializer18.serialize(chunk, true);
        // A light write inside a populated section: the light GROUP in the
        // payload changes, the block pack does not.
        chunk.setBlockLight(2, 19, 2, 11);
        byte[] after = ChunkSerializer18.serialize(chunk, true);
        assertSame(section1Slot, chunk.section(1).wireMemo(),
                "a light write never invalidates the block pack");
        assertNotEquals_bytes(before, after, "the light group did move");
        assertArrayEquals(verbatimPreMemoSerialize(chunk, true,
                ChunkSerializer18.sectionBitmask(chunk)), after);
    }

    @Test
    void biomeWritesNeverTouchTheBlockMemo() {
        EngineChunk chunk = flatChunk(1, 1);
        ChunkSerializer18.serialize(chunk, true);
        Object slot = chunk.section(0).wireMemo();
        chunk.setBiome(0, 0, (byte) 2);
        byte[] after = ChunkSerializer18.serialize(chunk, true);
        assertSame(slot, chunk.section(0).wireMemo());
        // The biome byte moved (the row's tail), the block bytes did not.
        int blockBytes = 8192 + 2048 * 2;
        assertEquals(2, after[blockBytes] & 0xFF, "the biome row reflects the write");
        assertArrayEquals(verbatimPreMemoSerialize(chunk, true,
                ChunkSerializer18.sectionBitmask(chunk)), after);
    }

    @Test
    void nullSectionServesTheAllAirConstant() {
        // The light-only section (blocks null, light non-default) rides the
        // wire with an all-air block array — the shared constant path. The
        // verbatim law walks chunk.getBlock (air for a null section), so the
        // identity assertion pins air -> legacy 0, metadata 0 -> zero bytes.
        EngineChunk chunk = varietyChunk();
        int mask = ChunkSerializer18.sectionBitmask(chunk);
        // Section 6 is in the mask (light) but has no ChunkSection object.
        org.junit.jupiter.api.Assertions.assertTrue((mask & (1 << 6)) != 0,
                "fixture: section 6 rides for light");
        assertNull(chunk.section(6), "fixture: section 6 holds no blocks");
        byte[] data = ChunkSerializer18.serialize(chunk, true);
        // Its block group region: find section 6's offset in the grouped
        // layout (blocks of sections before it, in mask order) and pin zeros.
        int sectionsBefore = Integer.bitCount(mask & ((1 << 6) - 1));
        int offset = sectionsBefore * 8192;
        for (int i = 0; i < 8192; i++) {
            assertEquals(0, data[offset + i], "all-air bytes at " + i);
        }
        assertArrayEquals(verbatimPreMemoSerialize(chunk, true, mask), data);
    }

    @Test
    void sectionRevisionCountsRealChangesOnly() {
        EngineChunk chunk = flatChunk(2, 2);
        long r0 = chunk.section(0).revision();
        var glass = registry.require(Identifier.parse("minecraft:glass"));
        var stone = registry.require(Identifier.parse("minecraft:stone"));
        chunk.setBlock(1, 1, 1, glass);           // air -> glass: change
        chunk.setBlock(2, 2, 2, glass);           // air -> glass: change
        chunk.setBlock(1, 1, 1, glass);           // same: no change
        chunk.setBlock(2, 2, 2, stone);           // glass -> stone: change
        assertEquals(r0 + 3, chunk.section(0).revision());
    }

    /** Asserts two arrays are NOT equal (the named-assert readability shim). */
    private static void assertNotEquals_bytes(byte[] a, byte[] b, String message) {
        org.junit.jupiter.api.Assertions.assertFalse(java.util.Arrays.equals(a, b), message);
    }
}
