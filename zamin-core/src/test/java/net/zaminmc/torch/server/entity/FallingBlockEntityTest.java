package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.item.ItemStack;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Falling-block behavior (§470): physics reproduces the shared model, the
 * block→entity→block transition lands exactly, occupied landings drop items,
 * and void falls end silently.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FallingBlockEntityTest {

    private FrozenBlockRegistry registry;
    private EngineWorld world;

    @BeforeAll
    void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        world = new EngineWorld("test", registry, new FlatWorldGenerator(registry, 4),
                Thread.currentThread());
    }

    /** The engine's ground query: solid when the block is not air. */
    private FallingBlockEntity.Ground ground() {
        return (x, y, z) -> {
            int by = (int) Math.floor(y);
            if (by < 0 || by > 255) {
                return false;
            }
            return !world.getBlock(new net.zaminmc.torch.block.BlockPosition(
                    (int) Math.floor(x), by, (int) Math.floor(z))).equals(world.airType());
        };
    }

    @Test
    void fallsUnderGravityAndLandsBackAsABlock() {
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(0, 0));
        // Sand at (0,6,0) with air beneath: surface (grass) top at y=5.
        world.setBlock(new net.zaminmc.torch.block.BlockPosition(0, 6, 0), BuiltinBlocks.SAND);
        world.setBlock(new net.zaminmc.torch.block.BlockPosition(0, 5, 0), world.airType());

        FallingBlockEntityManager manager = new FallingBlockEntityManager(ground(), world,
                (position, stack) -> { throw new AssertionError("no drop on a free landing"); },
                new Random(1), 5000);
        FallingBlockEntity entity = manager.startFall(
                new net.zaminmc.torch.block.BlockPosition(0, 6, 0), BuiltinBlocks.SAND);
        assertEquals(new Position(0.5, 6.5, 0.5), entity.position(), "the entity centers in the cell");

        List<Position> steps = new ArrayList<>();
        for (int tick = 0; tick < 200 && manager.size() > 0; tick++) {
            manager.tick();
            if (manager.size() > 0) {
                steps.add(entity.position());
            }
        }
        assertTrue(steps.size() > 1, "the entity fell over several ticks");
        assertTrue(steps.get(0).y() < 6.5, "the first tick already accelerated downward");
        assertTrue(entity.onGround(), "the landed entity rests on the ground");

        // The block re-materialized exactly where the sand sat before the fall.
        assertEquals(BuiltinBlocks.SAND,
                world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 5, 0)),
                "the §470 transition ends back in block state");
        assertEquals(0, manager.size(), "the entity is gone after landing");
    }

    @Test
    void occupiedLandingDropsTheStackAsAnItem() {
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(10, 10));
        // A stone floor one above the surface: the sand lands ON the stone,
        // but that cell is occupied by a torch-like blocker we place ourselves.
        world.setBlock(new net.zaminmc.torch.block.BlockPosition(160, 5, 160), BuiltinBlocks.STONE);
        world.setBlock(new net.zaminmc.torch.block.BlockPosition(160, 6, 160), BuiltinBlocks.STONE); // blocker
        world.setBlock(new net.zaminmc.torch.block.BlockPosition(160, 7, 160), BuiltinBlocks.SAND);
        world.setBlock(new net.zaminmc.torch.block.BlockPosition(160, 8, 160), world.airType());

        List<net.zaminmc.torch.item.ItemStack> dropped = new ArrayList<>();
        List<net.zaminmc.torch.block.BlockPosition> dropAt = new ArrayList<>();
        FallingBlockEntityManager manager = new FallingBlockEntityManager(ground(), world,
                (position, stack) -> {
                    dropped.add(stack);
                    dropAt.add(position);
                },
                new Random(2), 5100);
        manager.startFall(new net.zaminmc.torch.block.BlockPosition(160, 7, 160), BuiltinBlocks.SAND);
        for (int tick = 0; tick < 200 && manager.size() > 0; tick++) {
            manager.tick();
        }
        assertEquals(1, dropped.size(), "the occupied landing dropped the stack");
        assertEquals("minecraft:sand", dropped.get(0).type().identifier().toString());
        assertEquals(new net.zaminmc.torch.block.BlockPosition(160, 7, 160), dropAt.get(0),
                "the drop lands in the blocked cell itself");
        assertEquals(BuiltinBlocks.STONE,
                world.getBlock(new net.zaminmc.torch.block.BlockPosition(160, 6, 160)),
                "the blocker was not overwritten");
    }

    @Test
    void voidFallEndsSilentlyWithoutADrop() {
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(20, 20));
        // Air everywhere below: the entity falls out of the world.
        FallingBlockEntityManager manager = new FallingBlockEntityManager(
                (x, y, z) -> false, world,
                (position, stack) -> { throw new AssertionError("void removal drops nothing"); },
                new Random(3), 5200);
        manager.startFall(new net.zaminmc.torch.block.BlockPosition(320, 250, 320), BuiltinBlocks.GRAVEL);
        int ticks = 0;
        while (manager.size() > 0 && ticks < 600) {
            manager.tick();
            ticks++;
        }
        assertEquals(0, manager.size(), "the entity left the world at the bottom");
        assertTrue(ticks < 600, "the fall terminated");
    }

    @Test
    void physicsMatchesTheSharedModel() {
        FallingBlockEntity entity = new FallingBlockEntity(1, BuiltinBlocks.SAND,
                new Position(0.5, 10.0, 0.5));
        FallingBlockEntity.Step step = entity.tick((x, y, z) -> false);
        assertEquals(FallingBlockEntity.Step.MOVED, step);
        // v1 = (0 - 0.04) * 0.98 = -0.0392; y = 10 - 0.0392.
        assertEquals(-0.0392, entity.velocityY(), 1.0E-9, "the shared gravity/drag model");
        assertEquals(10.0 - 0.0392, entity.position().y(), 1.0E-9);
    }
}
