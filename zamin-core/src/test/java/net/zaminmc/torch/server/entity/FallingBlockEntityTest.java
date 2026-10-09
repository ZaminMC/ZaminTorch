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
 * Falling-block behavior (§470): physics reproduces the vanilla model (the
 * box-bottom position convention, gravity before the move and the 0.98 drag
 * after it), the block→entity→block transition lands exactly, occupied
 * landings drop items, the vanilla lifetime rule (100+ ticks outside y
 * 1..256 / 600 ticks falling) drops the stack, the vanilla canFallThrough
 * set (air, water, lava, fire) drives the trigger, and the canPlace landing
 * set additionally accepts the replaceable flora.
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
        assertEquals(new Position(0.5, 6.0, 0.5), entity.position(),
                "the entity spawns at the vanilla box-bottom y (the model renders in its cell)");

        List<Position> steps = new ArrayList<>();
        for (int tick = 0; tick < 200 && manager.size() > 0; tick++) {
            manager.tick();
            if (manager.size() > 0) {
                steps.add(entity.position());
            }
        }
        assertTrue(steps.size() > 1, "the entity fell over several ticks");
        assertTrue(steps.get(0).y() < 6.0, "the first tick already accelerated downward");
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
    void belowWorldFallEndsInTheVanillaDrop() {
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(20, 20));
        // Air everywhere below: the entity falls past y=1 and the vanilla
        // lifetime rule (100 ticks below y=1) drops it as an item.
        List<net.zaminmc.torch.item.ItemStack> dropped = new ArrayList<>();
        FallingBlockEntityManager manager = new FallingBlockEntityManager(
                (x, y, z) -> false, world,
                (position, stack) -> dropped.add(stack),
                new Random(3), 5200);
        manager.startFall(new net.zaminmc.torch.block.BlockPosition(320, 250, 320), BuiltinBlocks.GRAVEL);
        int ticks = 0;
        while (manager.size() > 0 && ticks < 600) {
            manager.tick();
            ticks++;
        }
        assertEquals(0, manager.size(), "the entity ended by the lifetime rule");
        assertTrue(ticks < 600, "the fall terminated well before the 600-tick cap");
        assertEquals(1, dropped.size(), "the vanilla doEntityDrops rule drops the stack");
        assertEquals("minecraft:gravel", dropped.get(0).type().identifier().toString());
    }

    @Test
    void vanillaLifetimeRuleSixHundredTicks() {
        // A fall 590 ticks deep, high above y=1 (so the below-world rule
        // cannot fire): the 600-tick cap ends it on the 601st tick.
        FallingBlockEntity entity = new FallingBlockEntity(1, BuiltinBlocks.SAND,
                new Position(0.5, 250.0, 0.5), 590);
        FallingBlockEntity.Ground never = (x, y, z) -> false;
        for (int tick = 0; tick < 10; tick++) {
            assertEquals(FallingBlockEntity.Step.MOVED, entity.tick(never),
                    "still falling through tick " + (590 + tick + 1));
        }
        assertEquals(600, entity.fallingTicks());
        assertEquals(FallingBlockEntity.Step.VOID, entity.tick(never),
                "the vanilla 600-tick cap ends the fall");
    }

    @Test
    void vanillaCanFallThroughSet() {
        assertTrue(FallingBlockEntity.canFallThrough(world.airType()), "air falls through");
        assertTrue(FallingBlockEntity.canFallThrough(
                net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                        net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER)), "water falls through");
        assertTrue(FallingBlockEntity.canFallThrough(
                net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                        net.zaminmc.torch.server.block.FluidBlocks.Kind.LAVA)), "lava falls through");
        assertTrue(FallingBlockEntity.canFallThrough(BuiltinBlocks.FIRE), "fire falls through");
        assertTrue(!FallingBlockEntity.canFallThrough(BuiltinBlocks.STONE), "stone holds");
        assertTrue(!FallingBlockEntity.canFallThrough(BuiltinBlocks.SAND), "sand stacks");
    }

    @Test
    void physicsMatchesTheVanillaOrder() {
        FallingBlockEntity entity = new FallingBlockEntity(1, BuiltinBlocks.SAND,
                new Position(0.5, 10.0, 0.5));
        FallingBlockEntity.Step step = entity.tick((x, y, z) -> false);
        assertEquals(FallingBlockEntity.Step.MOVED, step);
        // The vanilla order (FallingBlockEntity.tick lines 85-89): gravity
        // (v = -0.04), the move (y = 10 - 0.04), THEN the 0.98 drag — the
        // drag shapes the next tick's displacement, not this one's.
        assertEquals(10.0 - 0.04, entity.position().y(), 1.0E-9, "the move uses the post-gravity velocity");
        assertEquals(-0.04 * 0.98, entity.velocityY(), 1.0E-9, "the drag applies after the move");
    }

    @Test
    void vanillaCanPlaceLandingSet() {
        // The replaceable materials: the canFallThrough set plus the
        // replaceable plants (Material.REPLACEABLE_PLANT).
        assertTrue(FallingBlockEntity.canBeReplacedOnLanding(world.airType()), "air accepts");
        assertTrue(FallingBlockEntity.canBeReplacedOnLanding(
                net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                        net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER)), "water accepts");
        assertTrue(FallingBlockEntity.canBeReplacedOnLanding(BuiltinBlocks.TALL_GRASS),
                "tall grass is REPLACEABLE_PLANT: the settling sand replaces it");
        assertTrue(FallingBlockEntity.canBeReplacedOnLanding(BuiltinBlocks.DANDELION), "flowers accept");
        assertTrue(!FallingBlockEntity.canBeReplacedOnLanding(BuiltinBlocks.STONE), "stone rejects");
        assertTrue(!FallingBlockEntity.canBeReplacedOnLanding(BuiltinBlocks.SAND), "sand stacks");
    }

    @Test
    void landingOverAirDropsInsteadOfFloating() {
        // The vanilla landing gate's second half: a landing whose below cell
        // can be fallen through cannot hold the block. Vanilla reaches this
        // when the box rests on a collision edge while the center column
        // below is air; here the stub ground pretends a support at the y=0
        // top while the world column is fully hollow — the stack must drop
        // as an item, not float.
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(30, 30));
        for (int y = 0; y <= 5; y++) {
            world.setBlock(new net.zaminmc.torch.block.BlockPosition(480, y, 480), world.airType());
        }

        List<net.zaminmc.torch.item.ItemStack> dropped = new ArrayList<>();
        FallingBlockEntityManager manager = new FallingBlockEntityManager(
                (x, y, z) -> Math.floor(y) == 0, world,
                (position, stack) -> dropped.add(stack), new Random(5), 5300);
        manager.startFall(new net.zaminmc.torch.block.BlockPosition(480, 9, 480), BuiltinBlocks.SAND);
        for (int tick = 0; tick < 200 && manager.size() > 0; tick++) {
            manager.tick();
        }
        assertEquals(1, dropped.size(), "the unsupported landing dropped the stack");
        assertEquals(world.airType(), world.getBlock(new net.zaminmc.torch.block.BlockPosition(480, 1, 480)),
                "nothing floated over the air gap");
    }
}
