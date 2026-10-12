package net.zaminmc.torch.server.world.light;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The light simulation (§475/§476): emission radiates and attenuates, glass
 * passes paying only the standard step, opaque cubes stop light, skylight's
 * direct columns hold 15 and shade when covered, removal re-darkens, light
 * crosses chunk borders, and every touched column lands in the relight queue.
 * Each test owns a fresh world (the flat fixture, grass top at y=4) so every
 * expectation is hand-checkable and independent of test order.
 */
class LightEngineTest {

    private EngineWorld world;
    private LightEngine light;

    @BeforeEach
    void setUp() {
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        world = new EngineWorld("test", registry, new FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        light = new LightEngine(world);
        // The engine's wiring: light observes every committed change and
        // computes the initial light of freshly generated chunks.
        world.addChangeListener(light);
        world.addChunkLoadListener(light);
        world.getOrGenerate(new ChunkPosition(0, 0));
    }

    private int blockLight(int x, int y, int z) {
        return world.blockLightAt(new BlockPosition(x, y, z));
    }

    private int skyLight(int x, int y, int z) {
        return world.skyLightAt(new BlockPosition(x, y, z));
    }

    @Test
    void freshChunkHasOpenSkyAndDarkGround() {
        assertEquals(15, skyLight(5, 10, 5), "open air holds direct skylight");
        assertEquals(15, skyLight(5, 5, 5), "the cell just above the grass is sky-lit");
        assertEquals(0, skyLight(5, 4, 5), "opaque cells store no skylight");
        assertEquals(0, skyLight(5, 3, 5), "the ground shades itself");
        assertEquals(0, blockLight(5, 6, 5), "no emitters, no block light");
    }

    @Test
    void torchRadiatesAndAttenuates() {
        world.setBlock(new BlockPosition(2, 5, 2), BuiltinBlocks.TORCH);
        assertEquals(14, blockLight(2, 5, 2), "the source cell holds its emission");
        assertEquals(13, blockLight(3, 5, 2), "one step pays one");
        assertEquals(11, blockLight(5, 5, 2), "three steps pay three");
        assertEquals(13, blockLight(2, 6, 2), "upward propagates too");
        assertEquals(0, blockLight(2, 4, 2), "the opaque ground stores nothing");
    }

    @Test
    void torchRemovalDarkensEverythingItFed() {
        world.setBlock(new BlockPosition(10, 6, 10), BuiltinBlocks.TORCH);
        assertEquals(12, blockLight(12, 6, 10));
        world.setBlock(new BlockPosition(10, 6, 10), world.airType());
        assertEquals(0, blockLight(10, 6, 10));
        assertEquals(0, blockLight(12, 6, 10), "the removal BFS strips downstream cells");
        assertEquals(0, blockLight(10, 8, 10));
    }

    @Test
    void torchRemovalKeepsLightFedBySurvivingSources() {
        // Two torches four blocks apart: removing one must not darken the
        // region the other still feeds (the re-add seeds).
        world.setBlock(new BlockPosition(20, 6, 20), BuiltinBlocks.TORCH);
        world.setBlock(new BlockPosition(24, 6, 20), BuiltinBlocks.TORCH);
        assertEquals(12, blockLight(22, 6, 20), "both feed the middle");
        world.setBlock(new BlockPosition(20, 6, 20), world.airType());
        assertEquals(12, blockLight(22, 6, 20), "the survivor re-fills from its side (14 - 2)");
        world.setBlock(new BlockPosition(24, 6, 20), world.airType());
        assertEquals(0, blockLight(22, 6, 20), "both gone: dark");
    }

    @Test
    void glassPassesLightPayingOnlyTheStandardStep() {
        world.setBlock(new BlockPosition(40, 6, 40), BuiltinBlocks.TORCH);
        int withoutGlass = blockLight(42, 6, 40);
        world.setBlock(new BlockPosition(41, 6, 40), BuiltinBlocks.GLASS);
        assertEquals(withoutGlass, blockLight(42, 6, 40),
                "filter-0 glass costs nothing beyond the standard step");
        world.setBlock(new BlockPosition(41, 6, 40), BuiltinBlocks.STONE);
        // One block does not seal a line: the light wraps around, paying two
        // extra steps for the detour (12 -> 10), the historical behavior.
        assertEquals(10, blockLight(42, 6, 40), "the detour around the block costs two");
        world.setBlock(new BlockPosition(41, 6, 40), world.airType());
        assertEquals(12, blockLight(42, 6, 40), "the straight path re-opens");
        world.setBlock(new BlockPosition(40, 6, 40), world.airType());
    }

    @Test
    void stoneRoofShadesAndOpeningRelights() {
        world.setBlock(new BlockPosition(60, 10, 60), BuiltinBlocks.STONE);
        // A 1x1 roof shades its own column; the columns beside it stay direct,
        // so the shaded cells sit one step below full brightness.
        assertEquals(14, skyLight(60, 9, 60), "the column below loses direct sky");
        assertEquals(14, skyLight(60, 8, 60), "still fed laterally by the open column beside it");
        assertEquals(15, skyLight(61, 9, 60), "the open column stays direct");
        world.setBlock(new BlockPosition(60, 10, 60), world.airType());
        assertEquals(15, skyLight(60, 9, 60), "the reopened column refills with direct sky");
        assertEquals(15, skyLight(60, 8, 60));
        assertEquals(0, skyLight(60, 4, 60), "the grass top still stops the column");
    }

    @Test
    void torchLightCrossesChunkBordersAndQueuesBothColumns() {
        world.getOrGenerate(new ChunkPosition(1, 0));
        world.setBlock(new BlockPosition(15, 6, 3), BuiltinBlocks.TORCH);
        assertEquals(13, blockLight(16, 6, 3), "light flows into the neighboring chunk");
        assertEquals(11, blockLight(18, 6, 3));

        List<ChunkPosition> relit = new ArrayList<>();
        light.flushRelight(relit::add);
        assertTrue(relit.contains(new ChunkPosition(0, 0)), "the torch's own column");
        assertTrue(relit.contains(new ChunkPosition(1, 0)), "the neighbor it lit");
        assertEquals(relit.size(), relit.stream().distinct().count(),
                "the queue deduplicates within the tick");
    }

    @Test
    void generatedChunksPullLightAcrossTheirBorders() {
        // A torch near chunk (2,0)'s ungenerated edge, then generate it: the
        // initial compute must pull the inflow in (no emitter inside that
        // chunk, so only border reconciliation can light the strip).
        world.setBlock(new BlockPosition(31, 6, 5), BuiltinBlocks.TORCH);
        world.getOrGenerate(new ChunkPosition(2, 0)); // spans x 32..47
        assertEquals(13, blockLight(32, 6, 5), "border inflow lights the fresh chunk");
        assertEquals(10, blockLight(35, 6, 5), "and attenuates inward from the border");
    }

    @Test
    void relightQueueSkipsTheUnpublishedChunkItself() {
        world.getOrGenerate(new ChunkPosition(5, 5));
        List<ChunkPosition> relit = new ArrayList<>();
        light.flushRelight(relit::add);
        assertTrue(relit.stream().noneMatch(p -> p.equals(new ChunkPosition(5, 5))),
                "the freshly generated column stays off the relight queue");
    }

    @Test
    void sectionsCarryingLightRideTheWireEvenWithoutBlocks() {
        // A torch high in the air lights empty sections: they must be flagged
        // for the wire despite holding no blocks. Light reaches 14 steps up
        // (y=33), so section 2 (y32..47) is touched as well; section 3 is not.
        world.setBlock(new BlockPosition(70, 20, 70), BuiltinBlocks.TORCH);
        var chunk = world.peek(new ChunkPosition(4, 4));
        assertTrue(chunk.sectionNeedsLightWire(1), "y16..31 carries the torch's light");
        assertTrue(chunk.sectionNeedsLightWire(2), "y32..33 still carries level 1..2");
        assertFalse(chunk.sectionNeedsLightWire(3), "y48.. stays at the implicit default");
        assertTrue(chunk.section(0) != null, "the ground section has blocks");
    }
}
