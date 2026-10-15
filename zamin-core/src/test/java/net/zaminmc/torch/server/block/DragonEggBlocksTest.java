package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dragon egg family (Slice 55 — DragonEggBlock 1:1): the rows and the
 * click teleport walk.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DragonEggBlocksTest {

    private static BlockRegistryBuilder.FrozenBlockRegistry registry;
    private static net.zaminmc.torch.server.world.EngineWorld world;

    @BeforeAll
    void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        world = new net.zaminmc.torch.server.world.EngineWorld("test", registry,
                new net.zaminmc.torch.server.world.FlatWorldGenerator(registry, 4),
                Thread.currentThread());
    }

    private DragonEggBlocks.EggWorld eggWorld() {
        return new DragonEggBlocks.EggWorld() {
            @Override
            public net.zaminmc.torch.block.BlockType getBlock(BlockPosition at) {
                return world.getBlock(at);
            }

            @Override
            public void setBlock(BlockPosition at, net.zaminmc.torch.block.BlockType type) {
                world.setBlock(at, type);
            }

            @Override
            public net.zaminmc.torch.block.BlockType air() {
                return world.airType();
            }
        };
    }

    @Test
    void theEggCarriesTheReferenceRows() {
        BlockBehavior behavior = BlockBehaviorTable.of(DragonEggBlocks.DRAGON_EGG).orElseThrow();
        assertEquals(3.0, behavior.hardness());
        assertTrue(behavior.diggable());
        assertFalse(behavior.requiresTool());
        assertTrue(behavior.drops().isEmpty(), "the egg never yields a stack");
        // The light rows: the setLight(0.125F) emission 1, the CUTOUT film
        // passes whole.
        assertEquals(1, BlockLightTable.lookup(DragonEggBlocks.DRAGON_EGG).orElseThrow().emission());
        assertEquals(0, BlockLightTable.lookup(DragonEggBlocks.DRAGON_EGG).orElseThrow().filter());
        // The inset box: 1/16 in on X/Z, full height.
        assertTrue(net.zaminmc.torch.server.block.CollisionShapes.contains(
                registry.require(DragonEggBlocks.DRAGON_EGG), 0.5, 0.5, 0.5,
                CollisionShapes.ISOLATED));
        assertTrue(!net.zaminmc.torch.server.block.CollisionShapes.contains(
                registry.require(DragonEggBlocks.DRAGON_EGG), 0.03, 0.5, 0.5,
                CollisionShapes.ISOLATED), "the egg is 1/16 in");
        assertEquals(1.0, net.zaminmc.torch.server.block.CollisionShapes.top(
                registry.require(DragonEggBlocks.DRAGON_EGG), 0.5, 0.5, 0.5,
                CollisionShapes.ISOLATED), 1.0E-9);
        // No item form.
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:dragon_egg")).isEmpty());
    }

    @Test
    void theTeleportWalkMovesTheEggToAnAirCell() {
        world.getOrGenerate(new ChunkPosition(20, 20));
        BlockPosition at = new BlockPosition(320, 8, 320);
        world.setBlock(at, registry.require(DragonEggBlocks.DRAGON_EGG));
        // A deterministic world: every cell within the offset band is air
        // (the flat generator's surface is far below), so the FIRST
        // attempt with an in-band air target moves the egg.
        boolean moved = DragonEggBlocks.tryTeleport(eggWorld(), new Random(7), at);
        assertTrue(moved, "the walk found an air cell");
        assertEquals(world.airType(), world.getBlock(at), "the source cell emptied");
        // Somewhere in the ±16/±8/±16 band the egg now sits.
        boolean found = false;
        for (int dx = -16; dx <= 16 && !found; dx++) {
            for (int dy = -8; dy <= 8 && !found; dy++) {
                for (int dz = -16; dz <= 16 && !found; dz++) {
                    found = world.getBlock(at.offset(dx, dy, dz)).identifier()
                            .equals(DragonEggBlocks.DRAGON_EGG);
                }
            }
        }
        assertTrue(found, "the egg moved within the reference's own band");
    }

    @Test
    void theWalkNeverTeleportsANonEgg() {
        world.getOrGenerate(new ChunkPosition(0, 0));
        BlockPosition stone = new BlockPosition(0, 8, 0);
        world.setBlock(stone, BuiltinBlocks.STONE);
        assertFalse(DragonEggBlocks.tryTeleport(eggWorld(), new Random(3), stone),
                "the walk only rides the egg");
        assertEquals(BuiltinBlocks.STONE, world.getBlock(stone));
    }
}
