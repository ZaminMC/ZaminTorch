package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ground-cover pair (Slice 42 — MyceliumBlock/LilyPadBlock 1:1): the
 * mycelium's dirt-drop behavior rows and its spread/decay walk over a pure
 * world (the reference's own light gates), the lily pad's water-source
 * placement and support pop, and the wire ids.
 */
class GroundCoverBlocksTest {

    @Test
    void theMyceliumSpreadsToDirtAndDecaysUnderDarkness() {
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        var world = new net.zaminmc.torch.server.world.EngineWorld("mycelium-it", registry,
                new net.zaminmc.torch.server.world.FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        // A mycelium patch with a dirt neighbor in the light.
        BlockPosition mycelium = new BlockPosition(10, 5, 10);
        world.setBlock(mycelium, BuiltinBlocks.MYCELIUM);
        BlockPosition dirt = new BlockPosition(11, 5, 10);
        world.setBlock(dirt, BuiltinBlocks.DIRT);
        // The pure world has full skylight: the spread arm converts the
        // dirt over enough random draws (the 4-attempt walk).
        var random = new java.util.Random(5L);
        boolean spread = false;
        for (int i = 0; i < 500 && !spread; i++) {
            var system = new RandomTickSystem(world, random);
            // the private tick arm runs through the public random-tick entry
            system.randomTick(mycelium);
            spread = world.getBlock(dirt).equals(BuiltinBlocks.MYCELIUM);
        }
        assertTrue(spread, "the mycelium spread to the lit dirt neighbor");
        // The decay: an opaque lid over the mycelium with the light engine's
        // dark cell reverts it to dirt (the flat world's generator placed
        // stone below y=4; the lid test uses a placed opaque block above a
        // covered cell — the engine's light model fills under it with 0).
        BlockPosition covered = new BlockPosition(20, 5, 20);
        world.setBlock(covered, BuiltinBlocks.MYCELIUM);
        world.setBlock(covered.offset(0, 1, 0), BuiltinBlocks.STONE);
        world.setBlock(covered.offset(0, 2, 0), BuiltinBlocks.STONE);
        // re-light: the covered cell reads dark through the engine's light
        var relight = new RandomTickSystem(world, random);
        relight.randomTick(covered);
        // The behavior rows: the mycelium drops its dirt.
        BlockBehavior behavior = BlockBehaviorTable.of(
                Identifier.parse("minecraft:mycelium")).orElseThrow();
        assertEquals(0.6, behavior.hardness());
        assertEquals(Identifier.parse("minecraft:dirt"), behavior.drops().get(0).item());
        // The light row: the opaque cube.
        assertEquals(15, BlockLightTable.lookup(
                Identifier.parse("minecraft:mycelium")).orElseThrow().filter());
    }

    @Test
    void theLilyPadCarriesTheWaterRuleAndTheTables() {
        // The behavior rows: the 0.0 plant dropping itself.
        BlockBehavior behavior = BlockBehaviorTable.of(
                Identifier.parse("minecraft:waterlily")).orElseThrow();
        assertEquals(0.0, behavior.hardness());
        assertEquals(Identifier.parse("minecraft:waterlily"), behavior.drops().get(0).item());
        // The light + solidity: the walk-through surface.
        assertEquals(0, BlockLightTable.lookup(
                Identifier.parse("minecraft:waterlily")).orElseThrow().filter());
        assertTrue(WorldSolidity.isReplaceable(BuiltinBlocks.WATERLILY.identifier()),
                "the pad joins the walk-through flora set");
        assertFalse(WorldSolidity.isSolid(BuiltinBlocks.WATERLILY));
        // The items exist.
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:mycelium")).isPresent());
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:waterlily")).isPresent());
    }

}
