package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The doEntityDrops gate over the falling block's BOTH drop arms (the
 * 2026-11-08 void-census wave — reference/1.8.8 FallingBlockEntity.tick
 * lines 123 + 129): the occupied-landing drop AND the lifetime/void drop
 * pop the item ONLY when {@code this.dropping && world.getGameRules()
 * .getBoolean("doEntityDrops")} holds — the engine's falling bodies are
 * transient (never NBT-loaded), so the {@code dropping} flag keeps its
 * true default and the rule alone gates. A world with the rule off ends
 * both arms with the entity alone: no item, exactly the reference's
 * doEntityDrops=false shape.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FallingDropRuleParityDiscoveryTest {

    private static FrozenBlockRegistry registry;

    private EngineWorld world;

    @BeforeAll
    void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    private void scene() {
        world = new EngineWorld("fallrule", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        world.getOrGenerate(new ChunkPosition(0, 0));
    }

    /** The engine's ground query: solid when the block is not air. */
    private FallingBlockEntity.Ground ground() {
        return (x, y, z) -> {
            int by = (int) Math.floor(y);
            if (by < 0 || by > 255) {
                return false;
            }
            return !world.getBlock(new BlockPosition(
                    (int) Math.floor(x), by, (int) Math.floor(z))).equals(world.airType());
        };
    }

    @Test
    void theVoidLifetimeDropConsultsTheRule() {
        scene();
        // FallingBlockEntity.tick line 129: the lifetime arm (100+ ticks
        // with the cell outside y 1..256) pops the stack ONLY under the
        // rule — doEntityDrops off and the gravel simply vanishes with the
        // entity, no item at all.
        List<net.zaminmc.torch.item.ItemStack> dropped = new ArrayList<>();
        FallingBlockEntityManager manager = new FallingBlockEntityManager(
                (x, y, z) -> false, dimension -> world,
                (position, stack, dimension) -> dropped.add(stack),
                () -> false, // doEntityDrops = false (the reference's gate)
                new Random(3), 7200);
        manager.startFall(world, new BlockPosition(320, 250, 320), BuiltinBlocks.GRAVEL);
        int ticks = 0;
        while (manager.size() > 0 && ticks < 600) {
            manager.tick();
            ticks++;
        }
        assertEquals(0, manager.size(), "the entity ended by the lifetime rule");
        assertEquals(0, dropped.size(),
                "the rule-off lifetime arm leaves no item (reference line 129's gate)");
    }

    @Test
    void theOccupiedLandingDropConsultsTheRule() {
        scene();
        // FallingBlockEntity.tick line 123: the occupied-landing arm pops
        // the stack ONLY under the rule — the blocker holds its cell, the
        // sand ends, and NOTHING drops.
        world.setBlock(new BlockPosition(160, 5, 160), BuiltinBlocks.STONE);
        world.setBlock(new BlockPosition(160, 6, 160), BuiltinBlocks.STONE); // blocker
        world.setBlock(new BlockPosition(160, 7, 160), BuiltinBlocks.SAND);
        world.setBlock(new BlockPosition(160, 8, 160), world.airType());

        List<net.zaminmc.torch.item.ItemStack> dropped = new ArrayList<>();
        FallingBlockEntityManager manager = new FallingBlockEntityManager(ground(),
                dimension -> world,
                (position, stack, dimension) -> dropped.add(stack),
                () -> false, // doEntityDrops = false (the reference's gate)
                new Random(2), 7300);
        manager.startFall(world, new BlockPosition(160, 7, 160), BuiltinBlocks.SAND);
        for (int tick = 0; tick < 200 && manager.size() > 0; tick++) {
            manager.tick();
        }
        assertEquals(0, manager.size(), "the fall ended on the blocker");
        assertEquals(0, dropped.size(),
                "the rule-off occupied landing leaves no item (reference line 123's gate)");
        assertEquals(BuiltinBlocks.STONE,
                world.getBlock(new BlockPosition(160, 6, 160)),
                "the blocker was not overwritten");
    }

    @Test
    void theRuleOnKeepsBothDrops() {
        scene();
        // The control: the same two shapes under the rule's default TRUE
        // — both drops still pop (the sibling FallingBlockEntityTest seats
        // carry the same law; this control keeps the gate's both sides in
        // one class).
        List<net.zaminmc.torch.item.ItemStack> dropped = new ArrayList<>();
        FallingBlockEntityManager lifetime = new FallingBlockEntityManager(
                (x, y, z) -> false, dimension -> world,
                (position, stack, dimension) -> dropped.add(stack),
                () -> true,
                new Random(3), 7400);
        lifetime.startFall(world, new BlockPosition(320, 250, 320), BuiltinBlocks.GRAVEL);
        int ticks = 0;
        while (lifetime.size() > 0 && ticks < 600) {
            lifetime.tick();
            ticks++;
        }
        assertEquals(1, dropped.size(), "the rule-on lifetime arm drops the stack");

        world.setBlock(new BlockPosition(170, 5, 170), BuiltinBlocks.STONE);
        world.setBlock(new BlockPosition(170, 6, 170), BuiltinBlocks.STONE); // blocker
        world.setBlock(new BlockPosition(170, 7, 170), BuiltinBlocks.SAND);
        world.setBlock(new BlockPosition(170, 8, 170), world.airType());
        int before = dropped.size();
        FallingBlockEntityManager occupied = new FallingBlockEntityManager(ground(),
                dimension -> world,
                (position, stack, dimension) -> dropped.add(stack),
                () -> true,
                new Random(2), 7500);
        occupied.startFall(world, new BlockPosition(170, 7, 170), BuiltinBlocks.SAND);
        for (int tick = 0; tick < 200 && occupied.size() > 0; tick++) {
            occupied.tick();
        }
        assertEquals(before + 1, dropped.size(),
                "the rule-on occupied landing drops the stack");
    }
}
