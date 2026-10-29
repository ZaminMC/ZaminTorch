package net.zaminmc.torch.server.block;

import net.zaminmc.torch.server.world.anvil.AnvilBlockIds;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vine family's state vocabulary + the climbable pair (Wave VN-A —
 * the F-VINE family pass's first slice, reference/1.8.8
 * block/VineBlock.java):
 *
 * <p>THE STATE FAMILY: the 1.8 wire form is legacy 106 with a metadata
 * nibble packing four persisted face bits — SOUTH=1, WEST=2, NORTH=4,
 * EAST=8 (getStateFromMetadata lines 330-333, the reference's own
 * write order); the fifth property (UP) is VIRTUAL
 * (resolveVirtualProperties computes it at render time, never
 * persisted, never on the wire). Sixteen states; the bare
 * {@code minecraft:vine} keeps the ALL-FACES form (metadata 15, the
 * historical wire row's own nibble — the bare-name convention, the
 * anvil round trip preserved), the all-clear form (metadata 0, a vine
 * placed on TOP of a block — getPlacementState's vertical arm returns
 * the faceless default) is {@code vine_none}.</p>
 *
 * <p>THE CLIMBABLE PAIR (LivingEntity.isClimbing lines 809-813):
 * {@code block == LADDER || block == VINE} — every vine state
 * climbable, and the consult DISTINCT from the ladder family's own
 * (the reference's canBeReplaced split: flowing water REPLACES a vine
 * (VineBlock line 74) but never a ladder — the fluid system's
 * liquid-blocking consult stays ladder-only).</p>
 */
class VineStateFamilyParityTest {

    /** The light boot registry (the blast census's own shape). */
    private static BlockRegistryBuilder.FrozenBlockRegistry registry() {
        return BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    @Test
    void theSixteenStatesRegisterWithTheirNibbles() {
        BlockRegistryBuilder.FrozenBlockRegistry registry = registry();
        for (int bits = 0; bits <= 15; bits++) {
            EngineBlockType state = VineBlocks.stateFor(bits);
            assertEquals(bits, state.legacyMetadata() & 0xF,
                    "the nibble of " + state.identifier().value());
            // The round trip: the state's face bits read back.
            assertEquals(bits, VineBlocks.faceBitsOf(state),
                    "the face bits of " + state.identifier().value());
            // The registration: every state resolvable in the boot registry.
            assertEquals(state, registry.require(state.identifier()));
            // The family consult covers it.
            assertTrue(VineBlocks.isVineFamily(state));
        }
        // The bare all-faces form keeps the plain name.
        assertEquals("vine", VineBlocks.stateFor(15).identifier().value());
        assertEquals(15, VineBlocks.faceBitsOf(BuiltinBlocks.VINE));
        // The all-clear form's own name; the single-face forms in the
        // reference's bit order.
        assertEquals("vine_none", VineBlocks.stateFor(0).identifier().value());
        assertEquals("vine_south", VineBlocks.stateFor(1).identifier().value());
        assertEquals("vine_west", VineBlocks.stateFor(2).identifier().value());
        assertEquals("vine_north", VineBlocks.stateFor(4).identifier().value());
        assertEquals("vine_east", VineBlocks.stateFor(8).identifier().value());
        assertEquals("vine_south_west_north",
                VineBlocks.stateFor(7).identifier().value());
    }

    @Test
    void theAnvilImportMapsEveryLegacyVineRow() {
        // Legacy 106's sixteen metadata rows (the pre-wave engine folded
        // them all to the bare form — the state bits dropped).
        for (int bits = 0; bits <= 15; bits++) {
            Optional<Identifier> resolved = AnvilBlockIds.resolve(106, bits);
            assertTrue(resolved.isPresent(), "the row for metadata " + bits);
            assertEquals(VineBlocks.stateFor(bits).identifier(), resolved.get(),
                    "the legacy 106/" + bits + " row");
        }
        // The historical round trip preserved: metadata 15 keeps the
        // plain name.
        assertEquals("vine", AnvilBlockIds.resolve(106, 15).orElseThrow().value());
    }

    @Test
    void theClimbablePairCoversTheVineFamilyAndStaysSplitFromTheLadder() {
        // The reference's isClimbing: the ladder family OR the vine
        // family — every state.
        assertTrue(WorldSolidity.isClimbable(Identifier.parse("minecraft:vine")));
        assertTrue(WorldSolidity.isClimbable(Identifier.parse("minecraft:vine_none")));
        assertTrue(WorldSolidity.isClimbable(Identifier.parse("minecraft:vine_south_west")));
        assertTrue(WorldSolidity.isClimbable(Identifier.parse("minecraft:ladder")));
        assertTrue(WorldSolidity.isClimbable(Identifier.parse("minecraft:ladder_west")));
        // Not climbable: the stone, the flora, the leaves.
        assertFalse(WorldSolidity.isClimbable(Identifier.parse("minecraft:stone")));
        assertFalse(WorldSolidity.isClimbable(Identifier.parse("minecraft:tall_grass")));
        assertFalse(WorldSolidity.isClimbable(Identifier.parse("minecraft:oak_leaves")));
        // The SPLIT: the vine is NOT a ladder (the fluid system's
        // liquid-blocking consult reads isLadder alone — flowing water
        // replaces a vine, never a ladder, VineBlock.canBeReplaced line
        // 74 over the ladder's own absent override).
        assertFalse(WorldSolidity.isLadder(Identifier.parse("minecraft:vine")));
        assertFalse(WorldSolidity.isLadder(Identifier.parse("minecraft:vine_south")));
        assertTrue(WorldSolidity.isLadder(Identifier.parse("minecraft:ladder")));
    }

    @Test
    void theFamilyTablesCoverTheStates() {
        // The sound family: GRASS on the whole family (the reference's
        // own register row, Block.java line 1212's setSounds(GRASS_SOUNDS)).
        assertEquals(net.zaminmc.torch.server.fx.BlockSoundMap.SoundFamily.GRASS,
                net.zaminmc.torch.server.fx.BlockSoundMap.familyOf(
                        VineBlocks.stateFor(3).identifier()).orElseThrow(),
                "the vine_south_west state's family");
        // The map color: the REPLACEABLE_PLANT material's FOLIAGE band.
        assertEquals(net.zaminmc.torch.server.map.MapColors.of(BuiltinBlocks.VINE),
                net.zaminmc.torch.server.map.MapColors.of(VineBlocks.stateFor(9)),
                "the vine_south_east state's color");
        // The blast resistance: the whole family 0.2 (the reference's
        // setStrength(0.2F)).
        assertEquals(0.2f, BlastResistanceTable.read(VineBlocks.stateFor(0).identifier()));
        assertEquals(0.2f, BlastResistanceTable.read(VineBlocks.stateFor(11).identifier()));
        assertEquals(0.2f, BlastResistanceTable.read(BuiltinBlocks.VINE.identifier()));
        // The support consult (canGrowOn: isCube && blocksMovement): the
        // stone grows a vine, the vine itself never (not a cube), nor
        // the flora.
        assertTrue(VineBlocks.isGrowableSupport(BuiltinBlocks.STONE));
        assertFalse(VineBlocks.isGrowableSupport(BuiltinBlocks.VINE));
        assertFalse(VineBlocks.isGrowableSupport(BuiltinBlocks.LADDER_NORTH));
    }
}
