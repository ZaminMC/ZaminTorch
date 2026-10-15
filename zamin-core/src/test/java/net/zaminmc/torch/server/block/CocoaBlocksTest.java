package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cocoa family (Slice 43 — CocoaBlock 1:1): the twelve facing/age
 * states with the reference's exact metadata band, the jungle-log support
 * rule with the pop's dye rolls, the growth walk, and the family tables.
 */
class CocoaBlocksTest {

    @Test
    void theTwelveStatesCarryTheReferenceBand() {
        for (int facing = 0; facing < 4; facing++) {
            for (int age = 0; age <= 2; age++) {
                EngineBlockType state = CocoaBlocks.state(facing, age);
                assertEquals(facing | (age << 2), state.legacyMetadata(),
                        state.identifier().value());
                assertEquals(facing, CocoaBlocks.facingOf(state));
                assertEquals(age, CocoaBlocks.ageOf(state));
                assertTrue(CocoaBlocks.isCocoa(state));
                // The behavior rows: the 0.2 plant, the brown-dye drop.
                BlockBehavior behavior = BlockBehaviorTable.of(state.identifier()).orElseThrow();
                assertEquals(0.2, behavior.hardness());
                assertEquals(Identifier.parse("minecraft:dye"), behavior.drops().get(0).item());
                // The light rows: the CUTOUT pod passes light.
                assertEquals(0, BlockLightTable.lookup(state.identifier())
                        .orElseThrow().filter());
                // The solidity: the walk-through flora.
                assertTrue(WorldSolidity.isReplaceable(state.identifier()));
                assertFalse(WorldSolidity.isSolid(state));
            }
        }
        // The bare form IS the south/age-0 state.
        assertEquals("minecraft:cocoa", CocoaBlocks.state(0, 0).identifier().toString());
        assertEquals(127, CocoaBlocks.WIRE_ID);
        // The placement state: the clicked face's opposite.
        assertEquals("minecraft:cocoa_east", CocoaBlocks.placementState(4).identifier().toString(),
                "clicking the west face: the pod faces east, back at the log");
        assertEquals("minecraft:cocoa", CocoaBlocks.placementState(2).identifier().toString(),
                "clicking the north face: the pod faces south");
    }

    @Test
    void theSupportRuleReadsTheJungleLogAndTheDropWalksTheAgeBand() {
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        var world = new net.zaminmc.torch.server.world.EngineWorld("cocoa-it", registry,
                new net.zaminmc.torch.server.world.FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        // A jungle log with a pod facing it.
        BlockPosition log = new BlockPosition(10, 5, 10);
        world.setBlock(log, registry.require(CocoaBlocks.JUNGLE_LOG));
        BlockPosition pod = log.offset(0, 0, 1); // south of the log
        world.setBlock(pod, CocoaBlocks.state(2, 2)); // facing north, back at the log
        assertTrue(CocoaBlocks.isSupported(world, pod, 2),
                "the north-facing pod reads the log north of it");
        // The wrong facing reads the wrong cell.
        assertFalse(CocoaBlocks.isSupported(world, pod, 0),
                "the south facing reads the cell south — no log there");
        // The support departs: the neighborChanged pop (the AGE band's dye
        // rolls are the DropService's walk — verified through the drops).
        var service = new net.zaminmc.torch.server.interaction.DropService(
                new java.util.Random(3L));
        var youngDrops = service.dropsFor(CocoaBlocks.state(1, 0), null, false, 0);
        assertEquals(1, youngDrops.size());
        assertEquals("minecraft:dye", youngDrops.get(0).type().identifier().toString());
        assertEquals(CocoaBlocks.BROWN_DYE_DAMAGE, youngDrops.get(0).damage());
        var grownDrops = service.dropsFor(CocoaBlocks.state(1, 2), null, false, 0);
        assertEquals(1, grownDrops.size(), "the age-2 pod's walk is one stack");
        assertEquals(3, grownDrops.get(0).count(), "the age-2 pod's three rolls");
        assertEquals(CocoaBlocks.BROWN_DYE_DAMAGE, grownDrops.get(0).damage());
        // The growth write: the age advance keeps the facing.
        assertEquals(CocoaBlocks.state(1, 1), CocoaBlocks.withAge(CocoaBlocks.state(1, 0), 1));
        assertEquals(CocoaBlocks.state(1, 2), CocoaBlocks.withAge(CocoaBlocks.state(1, 1), 2));
    }
}
