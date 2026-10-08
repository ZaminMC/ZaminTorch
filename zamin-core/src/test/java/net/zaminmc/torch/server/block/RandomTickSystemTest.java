package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.ChunkPosition;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Random ticks (§471 pattern): the grass rules of the historical 1.8
 * {@code BlockGrass.updateTick} — decay under an opaque block, spread into
 * nearby uncovered dirt — driven by seeded randomness so the assertions are
 * deterministic.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RandomTickSystemTest {

    private FrozenBlockRegistry registry;
    private EngineWorld world;

    @BeforeAll
    void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        world = new EngineWorld("test", registry, new FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        // The grass rules read the world's real light storage (§475): attach
        // the light engine like the server wiring does, BEFORE any chunk
        // generates, so covered cells are dark instead of implicitly lit.
        net.zaminmc.torch.server.world.light.LightEngine light =
                new net.zaminmc.torch.server.world.light.LightEngine(world);
        world.addChangeListener(light);
        world.addChunkLoadListener(light);
    }

    @Test
    void decayHappensOnARandomTickUnderAnOpaqueBlock() {
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(0, 0));
        BlockPosition grassAt = new BlockPosition(8, 4, 8);
        world.setBlock(new BlockPosition(8, 5, 8), BuiltinBlocks.STONE); // cover, no update event
        RandomTickSystem ticks = new RandomTickSystem(world, new Random(42));

        int guard = 0;
        while (world.getBlock(grassAt).equals(BuiltinBlocks.GRASS_BLOCK) && guard < 500_000) {
            ticks.tick(java.util.List.of()); // no players: the direct call still runs
            guard++;
        }
        assertEquals(BuiltinBlocks.DIRT, world.getBlock(grassAt),
                "the historical decay fires when the random tick samples the cell");
        assertTrue(guard < 500_000, "the decay terminated inside the guard");
    }

    @Test
    void coveredCellsNeverSpreadAndNeverDecayWithoutACover() {
        // Dirt buried under stone has no route to grass (no light modeled under
        // opaque blocks); grass in the open never decays on its own.
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(1, 1));
        BlockPosition buriedDirt = new BlockPosition(20, 2, 20);
        RandomTickSystem ticks = new RandomTickSystem(world, new Random(43));
        for (int i = 0; i < 20_000; i++) {
            ticks.tick(java.util.List.of());
        }
        assertEquals(BuiltinBlocks.DIRT, world.getBlock(buriedDirt),
                "covered dirt cannot become grass");
        assertEquals(BuiltinBlocks.GRASS_BLOCK, world.getBlock(new BlockPosition(24, 4, 24)),
                "open grass survives 20k random-tick passes");
    }

    @Test
    void spreadReachesExposedDirtNextToGrass() {
        // Turn a strip of surface grass into dirt, then let random ticks
        // regrow it: the historical reclaim.
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(2, 2));
        BlockPosition exposed = new BlockPosition(40, 4, 40);
        world.setBlock(exposed, BuiltinBlocks.DIRT);
        RandomTickSystem ticks = new RandomTickSystem(world, new Random(44));

        int guard = 0;
        while (world.getBlock(exposed).equals(BuiltinBlocks.DIRT) && guard < 500_000) {
            ticks.tick(java.util.List.of());
            guard++;
        }
        assertEquals(BuiltinBlocks.GRASS_BLOCK, world.getBlock(exposed),
                "grass spreads into the exposed dirt");
        assertTrue(guard < 500_000, "the spread terminated inside the guard");
    }
}
