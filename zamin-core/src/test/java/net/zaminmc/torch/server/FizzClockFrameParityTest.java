package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The fizz site's clock + frame (the Wave LF review — the three
 * translation defects the parallel-lane collision surfaced, each read
 * against the reference before its fix):
 *
 * <p>THE POLL CLOCK (ItemEntity.tick line 76): the gate's second arm
 * is {@code this.ticks % 25 == 0} — the {@code World.updateEntity}
 * lifetime counter (World.java line 1355, incremented BEFORE every
 * {@code tick()}), an INDEPENDENT clock from the despawn {@code age}.
 * The reference seeds the age (the NBT {@code Age} tag on load, the
 * merge {@code Math.min} of the pair at line 165, the {@code -32768}
 * sentinel that skips the increment at lines 105-107) but NEVER the
 * counter — a reloaded item polls on the EXISTENCE grid (25 ticks
 * after its load), not the seeded-age grid. The pre-fix engine fused
 * the poll onto {@code age} — a reloaded item with {@code age % 25 ==
 * 0} polled on its FIRST tick back.</p>
 *
 * <p>THE CROSSING FLAG'S Y FRAME (ItemEntity.tick line 75): the
 * reference reads {@code (int)lastY != (int)y} where the 1.8.8 entity
 * y IS the box BOTTOM — the engine's center translates as
 * {@code position.y - HALF_HEIGHT}, the same convention the lava
 * consult three lines below already carries. The pre-fix flag read the
 * raw center: a center crossing without a feet crossing fired the gate
 * spuriously, and a feet crossing without a center crossing missed
 * it.</p>
 *
 * <p>THE SOUND FRAME (ItemEntity.tick line 81): the reference's
 * {@code playSound} plays at the entity — x/z the box center, y the
 * box BOTTOM. The pre-fix emission carried the center's y a
 * half-height high.</p>
 */
class FizzClockFrameParityTest {

    // ------------------------------------------------------------------
    // The cell-map world (the landing family's stub shape)
    // ------------------------------------------------------------------

    static long key(int x, int y, int z) {
        return ((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL);
    }

    static BlockType read(Map<Long, BlockType> cells, int x, int y, int z) {
        BlockType cell = cells.get(key(x, y, z));
        return cell != null ? cell : BuiltinBlocks.AIR;
    }

    /** The lava source block (the reference's Material.LAVA family). */
    static BlockType lava() {
        return FluidBlocks.sourceOf(FluidBlocks.Kind.LAVA);
    }

    /** A full stone slab at the given cell y (a solid floor everywhere). */
    private static void stone(Map<Long, BlockType> cells, int y) {
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                cells.put(key(x, y, z), BuiltinBlocks.STONE);
            }
        }
    }

    /** A one-deep lava lake at the given cell y (lava everywhere above the floor). */
    private static void lake(Map<Long, BlockType> cells, int y) {
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                cells.put(key(x, y, z), lava());
            }
        }
    }

    /**
     * The item's ground: the cell map + the swept surface (the landing
     * family's own MapGround shape) + the lava consult on the map's own
     * cells.
     */
    private static final class ItemMapGround implements ItemEntity.Ground {
        final Map<Long, BlockType> cells = new HashMap<>();

        @Override
        public boolean isSolid(double x, double y, double z) {
            return WorldSolidity.isSolid(read(cells,
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        }

        @Override
        public boolean lavaAtCell(double centerX, double feetY, double centerZ) {
            BlockType cell = read(cells, (int) Math.floor(centerX),
                    (int) Math.floor(feetY), (int) Math.floor(centerZ));
            return FluidBlocks.kindOf(cell.identifier()) == FluidBlocks.Kind.LAVA;
        }

        @Override
        public double slipperinessBelow(double centerX, double bottomY, double centerZ) {
            return 0.6; // stone under everything these seats build
        }

        private final net.zaminmc.torch.server.physics.EntityCollisionWorld surface =
                new net.zaminmc.torch.server.physics.EntityCollisionWorld() {
                    @Override
                    public BlockType blockAt(int x, int y, int z) {
                        return read(cells, x, y, z);
                    }

                    @Override
                    public boolean chunkLoaded(int x, int z) {
                        return true;
                    }
                };

        @Override
        public net.zaminmc.torch.server.physics.EntityCollisionWorld collisionWorld() {
            return surface;
        }
    }

    /** The recorded sound (the sink the seats drive). */
    private record Heard(Position at, String name, float volume, float pitch, int dimension) {
    }

    /** The recorder sink: collects every emission for the assertions. */
    private static final class RecordingSounds implements ItemEntity.Sounds {
        final List<Heard> heard = new ArrayList<>();

        @Override
        public void sound(Position at, String name, float volume, float pitch, int dimension) {
            heard.add(new Heard(at, name, volume, pitch, dimension));
        }
    }

    // ------------------------------------------------------------------
    // THE POLL CLOCK — the reloaded item polls on the existence grid
    // ------------------------------------------------------------------

    @Test
    void theSeededAgePollRidesTheExistenceClockNotTheAge() {
        // The persistence path's own shape: an item whose age was
        // restored (the NBT Age tag — AnvilWorldImport line 692, the
        // store round trip, ItemEntityManager line 795) RESTS under a
        // one-deep lava lake (its own cell the lava cell, position
        // fixed — no crossing, the reference's own at-rest shape). The
        // reference's poll rides the World.updateEntity lifetime
        // counter — restarted at 0 by the load — so the first resting
        // pop lands at existence tick 25 whatever the restored age.
        // The pre-fix engine polled on the age grid: age 4999 + tick ≡
        // 0 (mod 25) first at TICK 1 (age 5000) — the pop, the scatter
        // and the sound a full 24 ticks early.
        ItemMapGround ground = new ItemMapGround();
        stone(ground.cells, 60);
        lake(ground.cells, 61);
        RecordingSounds sounds = new RecordingSounds();
        ItemEntity item = new ItemEntity(7, new Position(0.5, 61.125, 0.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0); // bottom 61.0, the lava cell
        item.setAge(4999);
        item.setVelocity(0.0, 0.0, 0.0);
        Random random = new Random(11);
        int firstFizzTick = 0;
        for (int tick = 1; tick <= 24 && firstFizzTick == 0; tick++) {
            int before = sounds.heard.size();
            item.tick(ground, random, sounds);
            if (sounds.heard.size() > before) {
                firstFizzTick = tick;
            }
        }
        assertEquals(0, firstFizzTick,
                "a resting body crosses no boundary and the existence poll has not"
                        + " come — the seeded age NEVER opens the gate early");
        item.tick(ground, random, sounds); // the 25th tick
        assertEquals(1, sounds.heard.size(),
                "the ticks % 25 == 0 poll pops the resting body at existence tick 25");
        assertEquals(0.2 * 0.98 * -0.5, item.velocityY(), 1.0e-9,
                "the pop through the grounded drag (the resting micro-hop)");
    }

    // ------------------------------------------------------------------
    // THE CROSSING FLAG'S Y FRAME — the reference reads the feet
    // ------------------------------------------------------------------

    @Test
    void theCrossingFlagReadsTheFeetFrameNotTheCenter() {
        // ARM 1 (the spurious crossing): the CENTER crosses the integer
        // 10 (10.05 -> 9.95) but the FEET never do (9.925 -> 9.825,
        // cell 9 both sides) — the reference's flag reads the feet, so
        // the gate stays DARK (tick 1, the poll closed too). The
        // pre-fix engine read the center and popped.
        ItemMapGround centerCross = new ItemMapGround();
        lake(centerCross.cells, 9);
        RecordingSounds silent = new RecordingSounds();
        ItemEntity centerCrosser = new ItemEntity(8, new Position(0.5, 10.05, 0.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        centerCrosser.setVelocity(0.0, -0.06, 0.0); // with gravity: a 0.10 fall
        centerCrosser.tick(centerCross, new Random(12), silent);
        assertEquals(9.95, centerCrosser.position().y(), 1.0e-9,
                "the move itself ran");
        assertEquals(0, silent.heard.size(),
                "a center-only crossing never fired the reference's flag");

        // ARM 2 (the missed crossing): the FEET cross (10.005 -> 9.995,
        // cells 10 -> 9) but the CENTER never does (10.13 -> 10.12,
        // cell 10 both sides) — the reference's flag reads the feet and
        // the gate FIRES (the consult's post-move feet cell is the lava
        // cell 9). The pre-fix engine read the center and stayed dark.
        ItemMapGround feetCross = new ItemMapGround();
        lake(feetCross.cells, 9);
        RecordingSounds popping = new RecordingSounds();
        ItemEntity feetCrosser = new ItemEntity(9, new Position(0.5, 10.13, 0.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        feetCrosser.setVelocity(0.0, 0.03, 0.0); // with gravity: a 0.01 dip
        feetCrosser.tick(feetCross, new Random(12), popping);
        assertEquals(10.12, feetCrosser.position().y(), 1.0e-9,
                "the move itself ran");
        assertEquals(1, popping.heard.size(),
                "the feet's own crossing fired the reference's flag");
        assertEquals(0.2 * 0.98, feetCrosser.velocityY(), 1.0e-9,
                "the pop through the air drag band");
    }

    // ------------------------------------------------------------------
    // THE SOUND FRAME — the reference plays at the box bottom
    // ------------------------------------------------------------------

    @Test
    void theFizzSoundPlaysAtTheFeetFrame() {
        // The reference's playSound (ItemEntity.tick line 81) plays at
        // the entity: x/z the box center, y the box BOTTOM (the 1.8.8
        // position IS the feet — the same frame the lava consult
        // reads). A horizontal crossing pops; the emission's y is the
        // post-move feet, position.y - HALF_HEIGHT — the pre-fix
        // emission carried the center a half-height high.
        ItemMapGround ground = new ItemMapGround();
        lake(ground.cells, 9);
        RecordingSounds sounds = new RecordingSounds();
        ItemEntity item = new ItemEntity(10, new Position(-1.4, 10.075, 0.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        item.setVelocity(0.8, 0.0, 0.0);
        item.tick(ground, new Random(13), sounds);
        assertEquals(1, sounds.heard.size(), "the crossing popped");
        Position at = sounds.heard.get(0).at();
        assertEquals(item.position().x(), at.x(), 1.0e-9,
                "the sound's x is the body's post-move center x");
        assertEquals(item.position().y() - ItemEntity.HALF_HEIGHT, at.y(), 1.0e-9,
                "the sound's y is the box BOTTOM (the reference's own y)");
        assertEquals(item.position().z(), at.z(), 1.0e-9);
    }
}
