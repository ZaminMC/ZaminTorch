package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Identifier;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gravity fuse's migration onto the scheduled block-tick surface
 * (reference/1.8.8 FallingBlock.java lines 22-33 + DragonEggBlock.java):
 * the seam path — the production shape — with the fuse riding the
 * ScheduledTickSystem's own entries, the dispatch gate dropping a
 * replaced block's entry, and the block-update system's gravity arm
 * scheduling through the seam when wired.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GravityFallsTest {

    private static FrozenBlockRegistry registry;

    private EngineWorld world;
    private ScheduledTickSystem system;
    private FallingBlockEntityManager falling;
    private BlockUpdateSystem updates;

    @BeforeAll
    void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    private void scene() {
        world = new EngineWorld("gravity-scene", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        world.getOrGenerate(new ChunkPosition(0, 0));
        falling = new FallingBlockEntityManager((x, y, z) -> false, world,
                (position, stack) -> { }, new Random(2), 2000);
        ItemEntityManager items = new ItemEntityManager((x, y, z) -> false, new Random(1), 1000);
        updates = new BlockUpdateSystem(world, items, falling);
        system = new ScheduledTickSystem(world, new Random(7));
        GravityFalls.registerTicker(system, world, falling);
        updates.setScheduledTicks(system);
    }

    /** Mirrors the engine's wiring: commit → listener → the neighbor rules (the gravity arm) → the fuse. */
    private void wake(BlockPosition at, net.zaminmc.torch.block.BlockType type) {
        updates.onBlockChanged(world, at, type);
        updates.tick();
    }

    private void advance(int ticks) {
        for (int i = 0; i < ticks; i++) {
            world.tickTime(true);
            updates.tick();
            system.tick();
        }
    }

    @Test
    void theSandFuseRidesTheScheduledSurface() {
        scene();
        // Sand two cells above the flat ground: the wake (a neighbor
        // change at the sand cell) schedules through the seam.
        BlockPosition sandAt = new BlockPosition(1, 7, 1);
        world.setBlock(sandAt, BuiltinBlocks.SAND);
        wake(sandAt, BuiltinBlocks.SAND);
        assertNotNull(system.pendingAt(sandAt, Identifier.parse("minecraft:sand")),
                "the fuse rides the scheduled surface under the sand family id");
        long due = system.pendingAt(sandAt, Identifier.parse("minecraft:sand")).time();
        assertEquals(2, due - world.totalTicks(), "the two-tick fuse (getTickRate 2)");

        advance(1);
        assertTrue(world.getBlock(sandAt).identifier()
                        .equals(Identifier.parse("minecraft:sand")),
                "the block still hovers one tick in (the reference's hover)");
        advance(1);
        assertTrue(world.getBlock(sandAt).identifier().equals(world.airType().identifier()),
                "the block left the world on the fuse");
        assertEquals(1, falling.size(), "the falling entity took over");
        assertEquals(Identifier.parse("minecraft:sand"), falling.all().get(0).blockType().identifier());
        assertNull(system.pendingAt(sandAt, Identifier.parse("minecraft:sand")),
                "no re-arm — a fall is a one-shot");
    }

    @Test
    void theDragonEggCarriesItsOwnFiveTickBand() {
        scene();
        BlockPosition eggAt = new BlockPosition(2, 7, 2);
        world.setBlock(eggAt, DragonEggBlocks.dragonEgg());
        wake(eggAt, DragonEggBlocks.dragonEgg());
        assertNotNull(system.pendingAt(eggAt, DragonEggBlocks.DRAGON_EGG));
        long due = system.pendingAt(eggAt, DragonEggBlocks.DRAGON_EGG).time();
        assertEquals(5, due - world.totalTicks(), "the egg's own getTickRate 5");
        advance(4);
        assertFalse(falling.size() > 0, "the egg hovers longer than the sand family");
        advance(1);
        assertEquals(1, falling.size(), "the egg's entity took over at five");
    }

    @Test
    void theAnvilBandJoinsTheWake() {
        scene();
        BlockPosition anvilAt = new BlockPosition(3, 7, 3);
        EngineBlockType anvil = AnvilBlocks.state(0, 0);
        world.setBlock(anvilAt, anvil);
        wake(anvilAt, anvil);
        assertNotNull(system.pendingAt(anvilAt, anvil.identifier()),
                "every anvil state registers under its own id");
        advance(2);
        assertEquals(1, falling.size(), "the anvil's entity took over on the same two-tick fuse");
    }

    @Test
    void theDispatchGateDropsAReplacedBlock() {
        scene();
        BlockPosition sandAt = new BlockPosition(4, 7, 4);
        world.setBlock(sandAt, BuiltinBlocks.SAND);
        wake(sandAt, BuiltinBlocks.SAND);
        // The cell changes families before the fuse fires.
        world.setBlock(sandAt, BuiltinBlocks.STONE);
        advance(3);
        assertEquals(0, falling.size(), "a stone never falls — the entry dropped at the gate");
        assertNull(system.pendingAt(sandAt, Identifier.parse("minecraft:sand")),
                "no re-arm for the replaced family");
    }

    @Test
    void thePublicFallViewReadsTheSeam() {
        scene();
        BlockPosition sandAt = new BlockPosition(5, 7, 5);
        world.setBlock(sandAt, BuiltinBlocks.SAND);
        wake(sandAt, BuiltinBlocks.SAND);
        assertEquals(1, updates.pendingFallChecksView().size(),
                "the §27.3 fall view answers the seam's entries");
        assertEquals(sandAt, updates.pendingFallChecksView().get(0).position());
        assertTrue(updates.cancelFallCheck(sandAt), "the cancel removes the seam's fuse");
        assertEquals(0, updates.pendingFallChecksView().size());
        advance(3);
        assertEquals(0, falling.size(), "a cancelled fuse never fires");
    }

    @Test
    void aSupportedBlockNeverSchedulesAFallEntity() {
        scene();
        // Sand resting on the flat ground (y=4 is the surface): the fuse
        // fires, tryFall finds support, nothing converts.
        BlockPosition resting = new BlockPosition(6, 5, 6);
        world.setBlock(resting, BuiltinBlocks.SAND);
        wake(resting, BuiltinBlocks.SAND);
        advance(3);
        assertTrue(world.getBlock(resting).identifier()
                        .equals(Identifier.parse("minecraft:sand")),
                "the supported sand stays a block");
        assertEquals(0, falling.size(), "no entity for a supported block");
    }
}
