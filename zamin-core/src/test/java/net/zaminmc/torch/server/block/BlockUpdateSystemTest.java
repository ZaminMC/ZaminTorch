package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;

import net.zaminmc.torch.block.BlockPosition;
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
 * The scheduled block-update queue (§466): neighbor notifications schedule,
 * one pending update per position coalesces, and the rule set converts
 * gravity blocks, pops unsupported torches and decays covered grass — each on
 * the tick after (or the same tick as) the commit that requested it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockUpdateSystemTest {

    private FrozenBlockRegistry registry;
    private EngineWorld world;

    @BeforeAll
    void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        world = new EngineWorld("test", registry, new FlatWorldGenerator(registry, 4),
                Thread.currentThread());
    }

    /** Mirrors the engine's wiring: commit → listener → tick → rules. */
    private void commit(BlockUpdateSystem system, BlockPosition position,
                        net.zaminmc.torch.block.BlockType type) {
        world.setBlock(position, type);
        system.onBlockChanged(world, position, type);
        system.tick();
    }

    @Test
    void sandWithoutSupportConvertsIntoAFallingEntitySameTick() {
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(0, 0));
        ItemEntityManager items = new ItemEntityManager((x, y, z) -> false, new Random(1), 1000);
        FallingBlockEntityManager falling = new FallingBlockEntityManager((x, y, z) -> false, world,
                (position, stack) -> { }, new Random(2), 2000);
        BlockUpdateSystem system = new BlockUpdateSystem(world, items, falling);

        BlockPosition sandAt = new BlockPosition(1, 8, 1);
        world.setBlock(sandAt, BuiltinBlocks.SAND);
        // Support below vanishes (the dig commit): the sand's cell is a
        // neighbor of the change, so its update schedules and runs this tick.
        commit(system, new BlockPosition(1, 7, 1), world.airType());

        assertEquals(world.airType(), world.getBlock(sandAt), "the block left the world");
        assertEquals(1, falling.size(), "the §470 entity took over");
        assertEquals(BuiltinBlocks.SAND, falling.all().get(0).blockType());
        assertEquals(0, items.size(), "no item entity for a gravity transition");
    }

    @Test
    void repeatedNeighborRequestsCoalesceIntoOneUpdate() {
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(5, 5));
        ItemEntityManager items = new ItemEntityManager((x, y, z) -> false, new Random(3), 1100);
        FallingBlockEntityManager falling = new FallingBlockEntityManager((x, y, z) -> false, world,
                (position, stack) -> { }, new Random(4), 2100);
        BlockUpdateSystem system = new BlockUpdateSystem(world, items, falling);

        BlockPosition sandAt = new BlockPosition(80, 8, 80);
        world.setBlock(sandAt, BuiltinBlocks.SAND);
        // Four neighbors of the sand "break" in one tick: every request
        // schedules the sand, but exactly one conversion may happen.
        system.onBlockChanged(world, new BlockPosition(81, 8, 80), world.airType());
        system.onBlockChanged(world, new BlockPosition(79, 8, 80), world.airType());
        system.onBlockChanged(world, new BlockPosition(80, 8, 81), world.airType());
        system.onBlockChanged(world, new BlockPosition(80, 9, 80), world.airType());
        system.tick();

        assertEquals(1, falling.size(), "one update, one entity — no duplication");
        assertEquals(world.airType(), world.getBlock(sandAt));
    }

    @Test
    void unsupportedTorchPopsAsAnItem() {
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(9, 9));
        ItemEntityManager items = new ItemEntityManager((x, y, z) -> false, new Random(5), 1200);
        FallingBlockEntityManager falling = new FallingBlockEntityManager((x, y, z) -> false, world,
                (position, stack) -> { }, new Random(6), 2200);
        BlockUpdateSystem system = new BlockUpdateSystem(world, items, falling);

        BlockPosition torchAt = new BlockPosition(144, 7, 144);
        world.setBlock(torchAt, BuiltinBlocks.TORCH);
        commit(system, new BlockPosition(144, 6, 144), world.airType()); // support gone

        assertEquals(world.airType(), world.getBlock(torchAt), "the torch popped");
        assertEquals(1, items.size(), "the torch became an item entity");
        assertEquals("minecraft:torch",
                items.all().get(0).stack().type().identifier().toString());
        assertEquals(0, falling.size(), "torches do not fall");
    }

    @Test
    void grassUnderAnOpaqueBlockDecaysToDirt() {
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(12, 12));
        ItemEntityManager items = new ItemEntityManager((x, y, z) -> false, new Random(7), 1300);
        FallingBlockEntityManager falling = new FallingBlockEntityManager((x, y, z) -> false, world,
                (position, stack) -> { }, new Random(8), 2300);
        BlockUpdateSystem system = new BlockUpdateSystem(world, items, falling);

        BlockPosition grassAt = new BlockPosition(192, 4, 192); // the generator's surface grass
        BlockPosition coverAt = new BlockPosition(192, 5, 192);
        commit(system, coverAt, BuiltinBlocks.STONE); // a cover lands on the grass

        assertEquals(BuiltinBlocks.DIRT, world.getBlock(grassAt), "the historical decay");
    }

    @Test
    void grassUnderGlassOrATorchSurvives() {
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(13, 13));
        ItemEntityManager items = new ItemEntityManager((x, y, z) -> false, new Random(9), 1400);
        FallingBlockEntityManager falling = new FallingBlockEntityManager((x, y, z) -> false, world,
                (position, stack) -> { }, new Random(10), 2400);
        BlockUpdateSystem system = new BlockUpdateSystem(world, items, falling);

        BlockPosition grassAt = new BlockPosition(208, 4, 208);
        commit(system, new BlockPosition(208, 5, 208), BuiltinBlocks.GLASS);
        assertEquals(BuiltinBlocks.GRASS_BLOCK, world.getBlock(grassAt),
                "glass is not opaque: the grass keeps living");
        commit(system, new BlockPosition(208, 5, 208), world.airType());
        commit(system, new BlockPosition(208, 5, 208), BuiltinBlocks.TORCH);
        assertEquals(BuiltinBlocks.GRASS_BLOCK, world.getBlock(grassAt),
                "a torch does not kill the grass");
    }
}
