package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.entity.FallingBlockEntityManager;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * VT-017's discovery seats — the grass-decay shade law (the reference's
 * GrassBlock.tick lines 49-52, 1.8.8):
 *
 * <pre>world.getRawBrightness(pos.up()) &lt; 4
 *     &amp;&amp; world.getBlockState(pos.up()).getBlock().getOpacity() &gt; 2</pre>
 *
 * The OPACITY consult is the shade law's own membership: every
 * non-solid-render block answers opacity 0 (Block's constructor line 216
 * {@code this.opacity = this.isSolidRender() ? 255 : 0}) — the bed
 * (BedBlock lines 99-102 {@code isSolidRender() -> false}), the flowers,
 * the snow layer (SnowLayerBlock's {@code setOpacity(0)}), the chests,
 * the doors — the grass SURVIVES under them all wherever the light holds.
 * Only true solid-render cubes (255) and the explicit small-opacity rows
 * above 2 (water and ice, opacity 3) shade the grass to death.
 *
 * <p>THE CONTRACT: these seats are RED on the unfixed develop — the
 * engine's decay consults never read the opacity law. The UPDATE-WALK arm
 * (BlockUpdateSystem) answers a 3-block allowlist (air/torch/glass pass,
 * everything else "opaque") with no light gate, so a bed, a flower or a
 * snow layer landing on grass kills it on the placement's own drain; the
 * RANDOM-TICK arm (RandomTickSystem) reads the light alone (missing the
 * opacity conjunct), so a covered-in-darkness transparent block kills too.
 * The engine's own mycelium arm (Slice 42, the same file) carries the
 * reference's exact two-conjunct law — the grass family predates that
 * correction and never received it.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrassDecayShadeParityDiscoveryTest {

    private static FrozenBlockRegistry registry;
    private EngineWorld world;

    /** The flat generator's surface height (the grass top). */
    private static final int SURFACE = 4;

    @BeforeAll
    void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    /** A fresh scene with the real wiring: the light engine attached first. */
    private void scene(String name) {
        world = new EngineWorld(name, registry,
                new FlatWorldGenerator(registry, SURFACE), Thread.currentThread());
        net.zaminmc.torch.server.world.light.LightEngine light =
                new net.zaminmc.torch.server.world.light.LightEngine(world);
        world.addChangeListener(light);
        world.addChunkLoadListener(light);
    }

    /** Mirrors the engine's wiring: commit -> listener -> tick -> rules. */
    private void commit(BlockUpdateSystem system, BlockPosition position,
                        net.zaminmc.torch.block.BlockType type) {
        world.setBlock(position, type);
        system.onBlockChanged(world, position, type);
        system.tick();
    }

    private BlockUpdateSystem updateSystem() {
        ItemEntityManager items = new ItemEntityManager((x, y, z) -> false, new Random(7), 1300);
        FallingBlockEntityManager falling = new FallingBlockEntityManager(
                (x, y, z) -> false, dimension -> world,
                (position, stack, dimension) -> { }, new Random(8), 2300);
        return new BlockUpdateSystem(world, items, falling);
    }

    // ------------------------------------------------------------------
    // The update-walk arm: the cover lands through the real commit path
    // ------------------------------------------------------------------

    @Test
    void theBedCoveredGrassSurvivesTheShadeLaw() {
        scene("grass-bed-shade");
        world.getOrGenerate(new ChunkPosition(1, 1));
        BlockUpdateSystem system = updateSystem();
        BlockPosition grassAt = new BlockPosition(16, SURFACE, 16);

        // The bed's material answers opacity 0 (BedBlock lines 99-102):
        // vanilla never shades the grass under a bed, in any light.
        commit(system, new BlockPosition(16, SURFACE + 1, 16),
                BuiltinBlocks.BED_FOOT_SOUTH);

        assertEquals(BuiltinBlocks.GRASS_BLOCK, world.getBlock(grassAt),
                "VT-017: the bed's opacity 0 never shades the grass "
                + "(GrassBlock.tick lines 49-52)");
    }

    @Test
    void theFlowerCoveredGrassSurvivesTheShadeLaw() {
        scene("grass-flower-shade");
        world.getOrGenerate(new ChunkPosition(2, 2));
        BlockUpdateSystem system = updateSystem();
        BlockPosition grassAt = new BlockPosition(32, SURFACE, 32);

        // The flower family: non-solid-render, opacity 0 — the grass
        // under a flower keeps living in vanilla.
        commit(system, new BlockPosition(32, SURFACE + 1, 32),
                BuiltinBlocks.DANDELION);

        assertEquals(BuiltinBlocks.GRASS_BLOCK, world.getBlock(grassAt),
                "VT-017: the flower's opacity 0 never shades the grass");
    }

    @Test
    void theSnowLayerCoveredGrassSurvivesTheShadeLaw() {
        scene("grass-snow-shade");
        world.getOrGenerate(new ChunkPosition(3, 3));
        BlockUpdateSystem system = updateSystem();
        BlockPosition grassAt = new BlockPosition(48, SURFACE, 48);

        // The snow layer's own registration: setOpacity(0) — the vanilla
        // winter never kills the grass under the snowpack.
        commit(system, new BlockPosition(48, SURFACE + 1, 48),
                BuiltinBlocks.SNOW_LAYER);

        assertEquals(BuiltinBlocks.GRASS_BLOCK, world.getBlock(grassAt),
                "VT-017: the snow layer's opacity 0 never shades the grass");
    }

    @Test
    void theStoneControlDecaysOnTheSameWalk() {
        scene("grass-stone-control");
        world.getOrGenerate(new ChunkPosition(4, 4));
        BlockUpdateSystem system = updateSystem();
        BlockPosition grassAt = new BlockPosition(64, SURFACE, 64);

        // The positive control: stone is a full opaque cube (opacity 255)
        // and the cell under it is dark — the decay fires on both sides
        // of the law. The correction must keep this arm whole.
        commit(system, new BlockPosition(64, SURFACE + 1, 64),
                BuiltinBlocks.STONE);

        assertEquals(BuiltinBlocks.DIRT, world.getBlock(grassAt),
                "the stone's opacity 255 and the darkness under it decay "
                + "the grass (the law's true member)");
    }

    // ------------------------------------------------------------------
    // The random-tick arm: the sealed box isolates the light-alone gate
    // ------------------------------------------------------------------

    @Test
    void theRandomTickArmKeepsTheBedShadeInTheDark() {
        scene("grass-bed-dark");
        world.getOrGenerate(new ChunkPosition(5, 5));
        // No BlockUpdateSystem: the scene is staged silently so ONLY the
        // random-tick arm can fire. The bed cell is sealed — a stone lid
        // above and stone walls on the four sides — so its light is 0.
        BlockPosition grassAt = new BlockPosition(80, SURFACE, 80);
        BlockPosition bedAt = new BlockPosition(80, SURFACE + 1, 80);
        world.setBlock(bedAt, BuiltinBlocks.BED_FOOT_SOUTH);
        world.setBlock(new BlockPosition(80, SURFACE + 2, 80), BuiltinBlocks.STONE);
        for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            world.setBlock(new BlockPosition(80 + side[0], SURFACE + 1, 80 + side[1]),
                    BuiltinBlocks.STONE);
        }
        int lightAtBed = Math.max(world.blockLightAt(bedAt), world.skyLightAt(bedAt));
        RandomTickSystem ticks = new RandomTickSystem(world, new Random(42));

        ticks.randomTick(grassAt);

        assertEquals(BuiltinBlocks.GRASS_BLOCK, world.getBlock(grassAt),
                "VT-017: the bed's opacity 0 keeps the grass alive even at "
                + "light " + lightAtBed + " (the reference's second conjunct "
                + "getOpacity() > 2 fails for the bed)");
    }

    @Test
    void theRandomTickArmDecaysTheStoneCoverInTheSameDark() {
        scene("grass-stone-dark");
        world.getOrGenerate(new ChunkPosition(6, 6));
        // The same sealed box with a STONE directly on the grass: the
        // light is 0 AND the opacity is 255 — both conjuncts pass and the
        // decay fires in vanilla and in the engine alike.
        BlockPosition grassAt = new BlockPosition(96, SURFACE, 96);
        BlockPosition stoneAt = new BlockPosition(96, SURFACE + 1, 96);
        world.setBlock(stoneAt, BuiltinBlocks.STONE);
        world.setBlock(new BlockPosition(96, SURFACE + 2, 96), BuiltinBlocks.STONE);
        for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            world.setBlock(new BlockPosition(96 + side[0], SURFACE + 1, 96 + side[1]),
                    BuiltinBlocks.STONE);
        }
        RandomTickSystem ticks = new RandomTickSystem(world, new Random(43));

        ticks.randomTick(grassAt);

        assertEquals(BuiltinBlocks.DIRT, world.getBlock(grassAt),
                "the stone's opacity 255 with the darkness decays the grass "
                + "on the random tick (the law's true member, in the dark)");
    }
}
