package net.zaminmc.torch.server;

import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.experience.ExperienceOrbEntity;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item and orb bodies' landing physics + the friction family (the
 * fifteenth pass's §D/§F tables, each row adjudicated against the
 * reference before its fix — Wave IO-F):
 *
 * <p>THE ADJUDICATION first (the pass's own claims, three of them
 * WRONG): ItemEntity.makesSteps() (line 44) and
 * ExperienceOrbEntity.makesSteps() (line 32) both return FALSE — no
 * onSteppedOn dispatch ever fires for either body (Entity.move line 591
 * gates the whole steps band on it), so the step damping and the ore
 * lighting rows are NOT OWED (a resting item or orb never lights the
 * redstone ore in vanilla 1.8.8), and F-ITEMSTEP is a non-row (every
 * non-mob body — items, orbs, minecarts, boats, arrows, crystals, the
 * bat — overrides makesSteps to false; only living bodies and walking
 * players make steps). What IS owed is the tick's own velocity walk:</p>
 *
 * <p>THE ITEM (ItemEntity.tick lines 60-114): gravity BEFORE the move,
 * unconditional ({@code velocityY -= 0.04} line 72, never fused with the
 * drag); the vertical clip's {@code beforeCollision} consult (Entity.move
 * line 599 over Block line 882 — the slime override flips a falling body
 * at full speed, keeps a rising one, the default zeroes); the POST-move
 * drag (lines 89-100: the horizontal band reads the SUPPORT cell's
 * slipperiness * 0.98 — {@code floor(x), floor(minY) - 1, floor(z)} —
 * the ice family's 0.98 and the pad's 0.8 against the common case's 0.6;
 * the vertical * 0.98); and the grounded flip
 * {@code velocityY *= -0.5} (line 102 — the resting micro-hop: over the
 * pad it halves the beforeCollision bounce, over the common blocks it
 * acts on the zero the default consult left).</p>
 *
 * <p>THE ORB (ExperienceOrbEntity.tick lines 61-128): the same shape with
 * the orb's own constants — gravity 0.03 (line 70, NOT the item family's
 * 0.04) and the grounded flip -0.9 (line 121 — the orb keeps most of its
 * bounce over the pad, where the item's -0.5 halves it away).</p>
 *
 * <p>THE FRICTION FAMILY (F-FRICTION — the loudest row): the mobs read
 * the support cell's own slipperiness through the reference's
 * moveRelative law (LivingEntity lines 1136-1161, on the ground
 * {@code slipperiness * 0.91} — the common case 0.546, the ice family
 * 0.892, the pad 0.728 — and the AIR band the reference's own 0.91 base,
 * line 1137's f4 = 0.91F; the pre-fix engine carried the flat 0.6/0.98,
 * the item family's bands, never the mob's). The ridden body's twin
 * (PlayerControlGoal lines 79-84) and the ghast's on-ground band
 * (FlyingMobEntity lines 36-49) ride the same consult.</p>
 */
class ItemOrbLandingPhysicsParityTest {

    // ------------------------------------------------------------------
    // The shared cell-map floor (the SlimePad family's stub shape)
    // ------------------------------------------------------------------

    /** A flat floor of one material at cell y=63 — the top at 64.0. */
    private static void floor(Map<Long, BlockType> cells, String material) {
        BlockType type = switch (material) {
            case "slime_block" -> BuiltinBlocks.SLIME_BLOCK;
            case "ice" -> BuiltinBlocks.ICE;
            case "packed_ice" -> BuiltinBlocks.PACKED_ICE;
            default -> BuiltinBlocks.STONE;
        };
        for (int x = -100; x <= 100; x++) {
            for (int z = -100; z <= 100; z++) {
                cells.put(key(x, 63, z), type);
            }
        }
    }

    static long key(int x, int y, int z) {
        return ((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL);
    }

    static BlockType read(Map<Long, BlockType> cells, int x, int y, int z) {
        BlockType cell = cells.get(key(x, y, z));
        return cell != null ? cell : BuiltinBlocks.AIR;
    }

    /** The reference's slipperiness table (Block.java line 117's default). */
    static double slipperinessOf(BlockType cell) {
        String value = cell.identifier().value();
        return switch (value) {
            case "ice", "packed_ice" -> 0.98;
            case "slime_block" -> 0.8;
            default -> 0.6;
        };
    }

    // ------------------------------------------------------------------
    // The item seats (the solver path — the production shape)
    // ------------------------------------------------------------------

    /** The item's ground: the cell map + the swept surface (the PadStub shape). */
    private static class MapGround implements ItemEntity.Ground {
        final Map<Long, BlockType> cells = new HashMap<>();

        @Override
        public boolean isSolid(double x, double y, double z) {
            return WorldSolidity.isSolid(read(cells,
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        }

        // No @Override: the consults are plain stub methods pre-wave and
        // the interface's implementing methods post-wave (the stash-run
        // discipline compiles this file against the pre-fix tree).
        public Identifier blockIdAt(double centerX, double bottomY, double centerZ) {
            // The reference's consult cell (Entity.move line 572):
            // floor(x), floor(bottom - 0.2), floor(z).
            int consultY = (int) Math.floor(bottomY - 0.2);
            return read(cells, (int) Math.floor(centerX), consultY,
                    (int) Math.floor(centerZ)).identifier();
        }

        public double slipperinessBelow(double centerX, double bottomY, double centerZ) {
            // The reference's support cell (ItemEntity lines 89-96):
            // floor(x), floor(bottom) - 1, floor(z).
            int supportY = (int) Math.floor(bottomY) - 1;
            return slipperinessOf(read(cells, (int) Math.floor(centerX), supportY,
                    (int) Math.floor(centerZ)));
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

    @Test
    void theItemGravityArmsBeforeTheMove() {
        // The reference's ItemEntity.tick line 72: gravity lands BEFORE
        // the move, never fused with the drag — the first tick from rest
        // moves the body a FULL 0.04 (the pre-fix engine's fused
        // (v - 0.04) * 0.98 moved it 0.0392), and the tick's end carries
        // the POST-move drag alone.
        MapGround ground = new MapGround(); // no floor: the air band
        ItemEntity item = new ItemEntity(1, new Position(5.5, 70.175, 5.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        item.setVelocity(0.0, 0.0, 0.0);
        item.tick(ground);
        // Moved the full pre-move gravity; the drag folded 0.98 after.
        assertEquals(70.135, item.position().y(), 1.0E-9, "tick 1: the full -0.04 move");
        assertEquals(-0.0392, item.velocityY(), 1.0E-9, "tick 1 end: the post-move drag");
        item.tick(ground);
        // v1 = -0.0392; gravity -> -0.0792 (the move); drag -> -0.077616.
        assertEquals(70.0558, item.position().y(), 1.0E-9, "tick 2: the -0.0792 move");
        assertEquals(-0.077616, item.velocityY(), 1.0E-9, "tick 2 end");
    }

    @Test
    void theItemLandingOverStonePaysTheDefaultZero() {
        // The control: the default beforeCollision (Block line 882) zeroes
        // the vertical at the clip — a landed item carries velocityY 0.0
        // at the tick's end (the -0.5 flip then acts on the zero).
        MapGround ground = new MapGround();
        floor(ground.cells, "stone");
        ItemEntity item = new ItemEntity(1, new Position(5.5, 64.175, 5.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        item.setVelocity(0.0, -0.5, 0.0);
        item.tick(ground); // the landing tick
        assertEquals(64.125, item.position().y(), 1.0E-9, "the bottom snapped onto the floor top");
        assertTrue(item.onGround(), "the landing turned the ground on");
        assertEquals(0.0, item.velocityY(), 1.0E-9, "the default consult's zero");
        item.tick(ground);
        assertEquals(0.0, item.velocityY(), 1.0E-9, "the resting vertical stays zeroed");
        assertEquals(64.125, item.position().y(), 1.0E-9, "the rest position holds");
    }

    @Test
    void theItemOverThePadKeepsTheBounceTrain() {
        // THE BOUNCE + THE MICRO-HOP (the wave's own row): the vertical
        // clip consults the -0.2 feet cell — the pad's override flips a
        // falling body at full speed (SlimeBlock lines 33-39), the
        // post-move drag folds 0.98, and the grounded flip halves it
        // away (-0.5, line 102) — the resting oscillation converging on
        // the reference's own fixed point. The pre-fix engine zeroed the
        // vertical at the landing: every value below would read 0.0.
        MapGround ground = new MapGround();
        floor(ground.cells, "slime_block");
        ItemEntity item = new ItemEntity(1, new Position(5.5, 64.175, 5.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        item.setVelocity(0.0, -0.5, 0.0);
        item.tick(ground); // the landing tick: the clip flips +0.54, the
        // drag folds 0.98 (0.5292), the flip halves (-0.2646).
        assertEquals(-0.2646, item.velocityY(), 1.0E-9, "the landing tick's end");
        assertEquals(64.125, item.position().y(), 1.0E-9, "the pad's top");
        item.tick(ground);
        assertEquals(-0.149254, item.velocityY(), 1.0E-9, "tick 2 end");
        item.tick(ground);
        assertEquals(-0.09273446, item.velocityY(), 1.0E-9, "tick 3 end");
        item.tick(ground);
        assertEquals(-0.0650398854, item.velocityY(), 1.0E-9, "tick 4 end");
        // The oscillation never leaves the surface (the clip holds the
        // box at the pad's top every tick).
        assertEquals(64.125, item.position().y(), 1.0E-9, "the rest position holds");
        assertTrue(item.onGround(), "the pad keeps the ground on");
    }

    @Test
    void theItemDragReadsTheSupportSlipperiness() {
        // F-FRICTION, the item's half: the horizontal band reads the
        // SUPPORT cell's own slipperiness * 0.98 (ItemEntity lines
        // 89-96) — the ice family 0.9604, the pad 0.784, the common case
        // 0.588. The pre-fix engine carried the flat 0.6 for every floor
        // (0.24 from 0.4): ice and stone were identical.
        double initial = 0.4;
        double ice = dragOnce("ice", initial);
        double stone = dragOnce("stone", initial);
        double pad = dragOnce("slime_block", initial);
        assertEquals(0.38416, ice, 1.0E-9, "the ice band: 0.4 * 0.98 * 0.98");
        assertEquals(0.2352, stone, 1.0E-9, "the common case: 0.4 * 0.6 * 0.98");
        assertEquals(0.3136, pad, 1.0E-9, "the pad: 0.4 * 0.8 * 0.98");
        // The air band is the flat 0.98 (the control — unchanged by the
        // wave): an airborne item keeps 0.392 from 0.4.
        MapGround air = new MapGround();
        ItemEntity falling = new ItemEntity(1, new Position(5.5, 70.175, 5.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        falling.setVelocity(initial, 0.0, 0.0);
        falling.tick(air);
        assertEquals(0.392, falling.velocityX(), 1.0E-9, "the air band: 0.4 * 0.98");
    }

    private double dragOnce(String material, double initialVelocity) {
        MapGround ground = new MapGround();
        floor(ground.cells, material);
        ItemEntity item = new ItemEntity(1, new Position(5.5, 64.125, 5.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0);
        item.setVelocity(initialVelocity, 0.0, 0.0);
        item.tick(ground);
        return item.velocityX();
    }

    // ------------------------------------------------------------------
    // The orb seats (the point-test path — the orb's own model)
    // ------------------------------------------------------------------

    private static class OrbMapGround implements ExperienceOrbEntity.Ground {
        final Map<Long, BlockType> cells = new HashMap<>();

        @Override
        public boolean isSolid(double x, double y, double z) {
            return WorldSolidity.isSolid(read(cells,
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        }

        // No @Override: the consults are plain stub methods pre-wave and
        // the interface's implementing methods post-wave (the stash-run
        // discipline compiles this file against the pre-fix tree).
        public Identifier blockIdAt(double centerX, double bottomY, double centerZ) {
            int consultY = (int) Math.floor(bottomY - 0.2);
            return read(cells, (int) Math.floor(centerX), consultY,
                    (int) Math.floor(centerZ)).identifier();
        }

        public double slipperinessBelow(double centerX, double bottomY, double centerZ) {
            int supportY = (int) Math.floor(bottomY) - 1;
            return slipperinessOf(read(cells, (int) Math.floor(centerX), supportY,
                    (int) Math.floor(centerZ)));
        }
    }

    @Test
    void theOrbGravityIsItsOwnConstant() {
        // ExperienceOrbEntity line 70: the orb's gravity is 0.03, NOT the
        // item family's 0.04 — the pre-fix engine carried the item's
        // fused (v - 0.04) * 0.04-band here (0.0392 on the first tick).
        OrbMapGround ground = new OrbMapGround(); // the air band
        ExperienceOrbEntity orb = new ExperienceOrbEntity(1,
                new Position(5.5, 70.175, 5.5), 3, 0);
        orb.setVelocity(0.0, 0.0, 0.0);
        orb.tick(ground);
        assertEquals(70.145, orb.position().y(), 1.0E-9, "tick 1: the full -0.03 move");
        assertEquals(-0.0294, orb.velocityY(), 1.0E-9, "tick 1 end: the post-move drag");
        orb.tick(ground);
        // v1 = -0.0294; gravity -> -0.0594 (the move); drag -> -0.058212.
        assertEquals(70.0856, orb.position().y(), 1.0E-9, "tick 2: the -0.0594 move");
        assertEquals(-0.058212, orb.velocityY(), 1.0E-9, "tick 2 end");
    }

    @Test
    void theOrbOverThePadKeepsMostOfItsBounce() {
        // The orb's flip is -0.9 (line 121) — over the pad the
        // beforeCollision bounce survives the post-move fold at 88% per
        // tick (the item's -0.5 halves it away instead): the orb's
        // resting oscillation decays at the reference's own slow rate.
        OrbMapGround ground = new OrbMapGround();
        floor(ground.cells, "slime_block");
        ExperienceOrbEntity orb = new ExperienceOrbEntity(1,
                new Position(5.5, 64.175, 5.5), 3, 0);
        orb.setVelocity(0.0, -0.5, 0.0);
        orb.tick(ground); // the landing tick: the clip flips +0.53, the
        // drag folds 0.98 (0.5194), the flip keeps -0.46746.
        assertEquals(-0.46746, orb.velocityY(), 1.0E-9, "the landing tick's end");
        assertEquals(64.125, orb.position().y(), 1.0E-9, "the pad's top");
        orb.tick(ground);
        assertEquals(-0.43875972, orb.velocityY(), 1.0E-9, "tick 2 end");
        orb.tick(ground);
        assertEquals(-0.41344607304, orb.velocityY(), 1.0E-9, "tick 3 end");
        assertEquals(64.125, orb.position().y(), 1.0E-9, "the rest position holds");
        assertTrue(orb.onGround(), "the pad keeps the ground on");
    }

    @Test
    void theOrbOverStonePaysTheDefaultZero() {
        // The control: the default consult zeroes the landed orb's
        // vertical (the -0.9 flip then acts on the zero).
        OrbMapGround ground = new OrbMapGround();
        floor(ground.cells, "stone");
        ExperienceOrbEntity orb = new ExperienceOrbEntity(1,
                new Position(5.5, 64.175, 5.5), 3, 0);
        orb.setVelocity(0.0, -0.5, 0.0);
        orb.tick(ground);
        assertEquals(0.0, orb.velocityY(), 1.0E-9, "the default consult's zero");
        assertTrue(orb.onGround(), "the landing turned the ground on");
    }

    // ------------------------------------------------------------------
    // The mob seats (the walker's friction consult)
    // ------------------------------------------------------------------

    /** The mob's world: the cell map + the swept surface (the PadStub shape). */
    private static class FloorWorld implements MobEntity.WorldQuery {
        final Map<Long, BlockType> cells = new HashMap<>();

        @Override
        public boolean isSolid(double x, double y, double z) {
            return WorldSolidity.isSolid(read(cells,
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        public Identifier blockIdAt(int x, int y, int z) {
            return read(cells, x, y, z).identifier();
        }

        public double slipperinessBelow(double x, double feetY, double z) {
            int supportY = (int) Math.floor(feetY) - 1;
            return slipperinessOf(read(cells, (int) Math.floor(x), supportY,
                    (int) Math.floor(z)));
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return false;
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

    @Test
    void theWalkerDragsAtTheSupportSlipperiness() {
        // F-FRICTION, the mob's half — the ice slide, the loudest row:
        // the walker's grounded band reads the support cell's own
        // slipperiness * 0.91 (the moveRelative law, LivingEntity lines
        // 1136-1161): the ice family 0.892, the common case 0.546. The
        // pre-fix engine carried the flat 0.6 for every floor — ice and
        // stone were identical (0.3 from 0.5), and the air band 0.98
        // (0.49) where the reference's own base is 0.91 (0.455).
        double initial = 0.5;
        double onIce = mobDragOnce("ice", initial);
        double onStone = mobDragOnce("stone", initial);
        assertEquals(0.4459, onIce, 1.0E-9, "the ice slide: 0.5 * 0.98 * 0.91");
        assertEquals(0.273, onStone, 1.0E-9, "the common case: 0.5 * 0.6 * 0.91");
        // The air band: a falling cow drags at the reference's 0.91 base
        // (the pre-fix engine's 0.98 was the item family's band).
        FloorWorld air = new FloorWorld();
        MobEntity falling = new MobEntity(7, MobType.COW,
                new Position(5.5, 70.0, 5.5), new Random(11), air);
        falling.setVelocity(initial, 0.0, 0.0);
        falling.tick(false);
        assertTrue(!falling.onGround(), "the control body stays airborne");
        assertEquals(0.455, falling.velocityX(), 1.0E-9, "the air band: 0.5 * 0.91");
    }

    private double mobDragOnce(String material, double initialVelocity) {
        FloorWorld world = new FloorWorld();
        floor(world.cells, material);
        MobEntity cow = new MobEntity(7, MobType.COW,
                new Position(5.5, 70.0, 5.5), new Random(11), world);
        // Let the cow land first (the drag consult reads the grounded
        // state the previous tick's move left).
        for (int i = 0; i < 40 && !cow.onGround(); i++) {
            cow.tick(false);
        }
        assertTrue(cow.onGround(), "the cow landed");
        cow.setVelocity(initialVelocity, 0.0, 0.0);
        cow.tick(false);
        return cow.velocityX();
    }

    @Test
    void theIceSlideOutrunsTheStoneSlide() {
        // The integrated seat (the row's own name): a cow knocked across
        // ice slides roughly twice as far as across stone — the flat 0.6
        // the pre-fix engine carried for both made the two identical.
        double iceTravel = mobSlideTravel("ice", 0.5, 10);
        double stoneTravel = mobSlideTravel("stone", 0.5, 10);
        assertTrue(iceTravel > stoneTravel * 1.5,
                "the ice slide outruns the stone slide: " + iceTravel + " vs " + stoneTravel);
    }

    private double mobSlideTravel(String material, double initialVelocity, int ticks) {
        FloorWorld world = new FloorWorld();
        floor(world.cells, material);
        MobEntity cow = new MobEntity(7, MobType.COW,
                new Position(5.5, 70.0, 5.5), new Random(11), world);
        for (int i = 0; i < 40 && !cow.onGround(); i++) {
            cow.tick(false);
        }
        assertTrue(cow.onGround(), "the cow landed");
        double startX = cow.position().x();
        cow.setVelocity(initialVelocity, 0.0, 0.0);
        for (int i = 0; i < ticks; i++) {
            cow.tick(false);
        }
        return cow.position().x() - startX;
    }
}
