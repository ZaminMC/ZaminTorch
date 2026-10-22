package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blast SOURCE family (the reference's Entity overrides over
 * Explosion.damageEntities' consults, lines 87-93) + the cost form and the
 * destroy gate: the charged skull's 0.8 cap, the primed cart's rail
 * sparing, the per-crossing charge over the READ table, and the
 * unaffordable cell never joining the destroyed list.
 */
class BlastSourceTest {

    /** A cells-map world: reads and writes over a mutable grid. */
    private static final class GridWorld implements ExplosionService.World {
        final Map<Long, BlockType> cells = new HashMap<>();

        void put(int x, int y, int z, BlockType type) {
            cells.put(((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL), type);
        }

        BlockType at(int x, int y, int z) {
            BlockType t = cells.get(((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL));
            return t != null ? t : BuiltinBlocks.AIR;
        }

        @Override
        public BlockType getBlock(BlockPosition position) {
            return at(position.x(), position.y(), position.z());
        }

        @Override
        public boolean setBlock(BlockPosition position, BlockType type) {
            put(position.x(), position.y(), position.z(), type);
            return true;
        }
    }

    private static final BlockRegistryBuilder.FrozenBlockRegistry REGISTRY =
            BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();

    private static BlockType of(String id) {
        return REGISTRY.lookup(Identifier.parse("minecraft:" + id))
                .orElseThrow(() -> new AssertionError("missing " + id));
    }

    @Test
    void theChargedSkullCapsDestroyableBlocksAtPointEight() {
        // WitherSkullEntity lines 43-51: min(0.8, plain) over canDestroy —
        // stone 6 -> 0.8, obsidian 1200 -> 0.8; the unbreakable band
        // (bedrock, canDestroy false) reads its own enormous value.
        assertEquals(0.8f, BlastSources.CHARGED_SKULL.blastResistance(
                null, new BlockPosition(0, 0, 0), of("stone")), 1e-6f);
        assertEquals(0.8f, BlastSources.CHARGED_SKULL.blastResistance(
                null, new BlockPosition(0, 0, 0), of("obsidian")), 1e-6f);
        assertEquals(0.8f, BlastSources.CHARGED_SKULL.blastResistance(
                null, new BlockPosition(0, 0, 0), of("anvil")), 1e-6f);
        assertEquals(3600000.0f, BlastSources.CHARGED_SKULL.blastResistance(
                null, new BlockPosition(0, 0, 0), of("bedrock")), 1e-3f);
        // The reference's own datum: BarrierBlock's ctor carries 6000001.0F
        // (BarrierBlock.java line 12) — 3600000.6 after the x3/5 walk.
        assertEquals(3600000.6f, BlastSources.CHARGED_SKULL.blastResistance(
                null, new BlockPosition(0, 0, 0), of("barrier")), 1e-2f);
        // The cap's material consequence: a charged skull's power-1 ray
        // (strength 0.7-1.3) chews stone at (0.8+0.3)*0.3 = 0.33 per
        // crossing — deep craters where the plain skull (6 -> 1.89/crossing)
        // dies on the first cell.
        GridWorld wall = new GridWorld();
        for (int x = -3; x <= 3; x++) {
            for (int y = 60; y < 66; y++) {
                for (int z = -3; z <= 3; z++) {
                    wall.put(x, y, z, of("stone"));
                }
            }
        }
        ExplosionService charged = new ExplosionService(wall, new Random(3));
        var byCharged = charged.detonate(new BlockPosition(0, 63, 0), 1.0f, BlastSources.CHARGED_SKULL);
        ExplosionService plain = new ExplosionService(wall, new Random(3));
        var byPlain = plain.detonate(new BlockPosition(0, 63, 0), 1.0f);
        assertTrue(byCharged.size() > byPlain.size() * 3,
                "the charged skull's crater dwarfs the plain one: "
                        + byCharged.size() + " vs " + byPlain.size());
        assertTrue(byPlain.isEmpty() || byPlain.size() <= 2,
                "the plain skull barely dents stone: " + byPlain.size());
    }

    @Test
    void thePrimedCartSparesRails() {
        // TntMinecartEntity lines 142-152: a rail cell (the cell itself a
        // rail, or the cell above one) reads FREE but is never destroyed;
        // every other cell reads plain.
        GridWorld rails = new GridWorld();
        rails.put(0, 60, 0, of("stone"));   // the floor
        rails.put(0, 61, 0, of("rail"));    // the rail ON the floor
        rails.put(1, 61, 0, of("stone"));   // a plain cell beside it
        rails.put(2, 60, 0, of("golden_rail_ascending_east")); // a family state
        rails.put(3, 60, 0, of("stone"));   // a plain cell below nothing
        rails.put(4, 61, 0, of("golden_rail_ascending_east")); // the rail ABOVE the stone
        rails.put(4, 60, 0, of("stone"));   // the cell below the golden rail
        BlastSources.PRIMED_CART.blastResistance(rails, new BlockPosition(0, 61, 0), of("rail"));
        assertEquals(0.0f, BlastSources.PRIMED_CART.blastResistance(
                rails, new BlockPosition(0, 61, 0), of("rail")), 1e-9f, "the rail cell reads free");
        assertEquals(0.0f, BlastSources.PRIMED_CART.blastResistance(
                rails, new BlockPosition(4, 60, 0), of("stone")),
                1e-9f, "the cell below a rail reads free (isRail(world, pos.up()))");
        assertEquals(6.0f, BlastSources.PRIMED_CART.blastResistance(
                rails, new BlockPosition(3, 60, 0), of("stone")),
                1e-9f, "the cell below NO rail reads plain");
        assertEquals(6.0f, BlastSources.PRIMED_CART.blastResistance(
                rails, new BlockPosition(1, 61, 0), of("stone")), 1e-6f, "the dry cell reads plain");
        assertFalse(BlastSources.PRIMED_CART.canExplodeBlock(
                rails, new BlockPosition(0, 61, 0), of("rail")),
                "the primed cart never destroys the rail");
        assertTrue(BlastSources.PRIMED_CART.canExplodeBlock(
                rails, new BlockPosition(1, 61, 0), of("stone")),
                "the dry cell is destroyable");
        // The material consequence: a primed cart's blast clears the stone
        // through the rail line (the rails free to cross, the cart's own
        // floor destroyed around them) — the rails themselves standing.
        GridWorld line = new GridWorld();
        for (int x = -4; x <= 4; x++) {
            line.put(x, 60, 0, of("stone")); // the floor
            line.put(x, 61, 0, of(x == 0 ? "rail" : "air"));
        }
        ExplosionService cart = new ExplosionService(line, new Random(5));
        var destroyed = cart.detonate(new BlockPosition(0, 62, 0), 3.0f, BlastSources.PRIMED_CART);
        assertFalse(destroyed.stream().anyMatch(d -> d.type().identifier().value().contains("rail")),
                "no rail was destroyed");
        assertTrue(destroyed.stream().anyMatch(d -> d.type().identifier().value().equals("stone")),
                "the floor around the rails was destroyed");
    }

    @Test
    void theUnaffordableCellNeverJoinsTheDestroyedList() {
        // The destroy gate (Explosion line 93): the strength surviving AND
        // the source's veto — the pre-fix engine looted cells the ray could
        // not pay; the reference collects only what it can afford.
        GridWorld wall = new GridWorld();
        for (int x = -2; x <= 2; x++) {
            for (int y = 62; y <= 64; y++) {
                for (int z = -2; z <= 2; z++) {
                    wall.put(x, y, z, of("obsidian")); // 1200: no power-1 ray pays
                }
            }
        }
        ExplosionService blast = new ExplosionService(wall, new Random(7));
        var destroyed = blast.detonate(new BlockPosition(0, 63, 0), 1.0f);
        assertTrue(destroyed.isEmpty(),
                "the obsidian wall survives unaffordable: " + destroyed.size());
        // The dirt contrast: (0.5+0.3)*0.3 = 0.24 per crossing — a power-1
        // ray (0.7-1.3 strength) chews 2-5 cells.
        GridWorld dirt = new GridWorld();
        for (int x = -2; x <= 2; x++) {
            for (int y = 62; y <= 64; y++) {
                for (int z = -2; z <= 2; z++) {
                    dirt.put(x, y, z, of("dirt"));
                }
            }
        }
        ExplosionService soft = new ExplosionService(dirt, new Random(7));
        var chewed = soft.detonate(new BlockPosition(0, 63, 0), 1.0f);
        assertFalse(chewed.isEmpty(), "the dirt wall is chewed");
        // 21 cells under the engine's FULL 4095-ray fan (the reference's
        // shell-only fan carries ~2/3 of the rays — the service's own
        // historical shape, ledgered with its javadoc).
        assertEquals(21, chewed.size(), "the engine's power-1 dirt crater");
        // And the READ-table cost form: a stone crossing costs (6+0.3)*0.3
        // — the creeper's power-3 ray (2.1-3.9) dies inside two stone cells.
        GridWorld stone = new GridWorld();
        for (int x = -2; x <= 2; x++) {
            for (int y = 62; y <= 64; y++) {
                for (int z = -2; z <= 2; z++) {
                    stone.put(x, y, z, of("stone"));
                }
            }
        }
        ExplosionService creeper = new ExplosionService(stone, new Random(11));
        var crater = creeper.detonate(new BlockPosition(0, 63, 0), 3.0f);
        // 50 of the 75-cell region under the seed-11 rolls: each stone
        // crossing costs (6+0.3)*0.3 = 1.89 — a 2.1-3.9 strength ray dies
        // inside two cells. The retired model (hardness 1.5 -> 0.54 per
        // crossing) cleared the whole region; the READ table's own value
        // costs the creeper dear.
        assertEquals(50, crater.size(), "the READ table's stone crater (seed 11)");
    }
}
