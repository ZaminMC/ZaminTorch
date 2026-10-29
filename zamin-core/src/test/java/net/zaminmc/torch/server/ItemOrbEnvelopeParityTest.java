package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.experience.ExperienceOrbEntity;
import net.zaminmc.torch.server.experience.ExperienceOrbManager;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item/orb envelope family (the sixteenth pass — the §22 queue's
 * eldest named remainder, each row restored against the reference):
 *
 * <p>THE ITEM'S LAVA FIZZ (ItemEntity.tick lines 75-87): the gate — the
 * block-crossing flag ({@code (int)lastX != (int)x || ... || (int)lastZ
 * != (int)z}, the Java truncation verbatim) OR the entity's own
 * {@code ticks % 25 == 0} poll (the {@code World.updateEntity} line-1355
 * lifetime counter, an INDEPENDENT clock from the despawn {@code age});
 * the consult — the body's OWN cell ({@code new BlockPos(this)} =
 * floor(x), floor(bottom), floor(z)) against {@code Material.LAVA}
 * (the engine's {@code FluidBlocks.Kind.LAVA} — the same material set);
 * the response — the 0.2 vertical pop, the ±0.2 horizontal scatter, and
 * {@code random.fizz} at 0.4F / 2.0F..2.4F; the placement — between the
 * move and the drag (the popped velocity rides the same tick's
 * bands).</p>
 *
 * <p>THE ORB'S LAVA FIZZ (ExperienceOrbEntity.tick lines 71-76): the
 * SAME consult and response, but UNCONDITIONAL — every tick, no
 * crossing flag, no poll — placed after the pre-move gravity and before
 * the move.</p>
 *
 * <p>THE IGNITER ATTRACTION (ExperienceOrbEntity.tick lines 79-104 over
 * World.getNearestPlayer 2399-2413 + EntityFilter.NOT_SPECTATOR 27-31 +
 * PlayerEntity.getEyeHeight 1648): the retarget cadence
 * {@code lastTargetUpdateTick < renderTicks - 20 + id % 100} (the
 * renderTicks clock independent of the despawn age), the STICKY target
 * (kept unless null or beyond 64 squared distance — a closer player
 * never steals the lock), the spectator clear every tick, and the pull
 * itself: the eye-aimed offset over 8, {@code d5 = 1 - d4} squared, the
 * velocity add {@code d_i / d4 * d5 * 0.1} — the quadratic pull inside
 * the 8-block sphere.</p>
 */
class ItemOrbEnvelopeParityTest {

    // ------------------------------------------------------------------
    // The shared cell-map world (the landing family's stub shape)
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
     * family's own MapGround shape) + the own-cell consult (the NEW seam
     * — a plain method per the stash discipline: pre-fix an unused
     * helper, post-fix the fizz's consult).
     */
    private static final class ItemMapGround implements ItemEntity.Ground {
        final Map<Long, BlockType> cells = new HashMap<>();

        @Override
        public boolean isSolid(double x, double y, double z) {
            return WorldSolidity.isSolid(read(cells,
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        }

        @Override
        public net.zaminmc.torch.util.Identifier blockIdAt(double centerX, double bottomY,
                                                            double centerZ) {
            // The IO-F clip consult cell: floor(x), floor(bottom - 0.2), floor(z).
            return read(cells, (int) Math.floor(centerX),
                    (int) Math.floor(bottomY - 0.2),
                    (int) Math.floor(centerZ)).identifier();
        }

        // No @Override — the OWN-cell seam (the reference's new BlockPos(this)):
        // floor(x), floor(bottom), floor(z). The lava fizz reads it.
        public net.zaminmc.torch.util.Identifier blockIdAtCell(double centerX, double bottomY,
                                                               double centerZ) {
            return read(cells, (int) Math.floor(centerX), (int) Math.floor(bottomY),
                    (int) Math.floor(centerZ)).identifier();
        }

        @Override
        public double slipperinessBelow(double centerX, double bottomY, double centerZ) {
            // The support cell (ItemEntity lines 89-96): floor(bottom) - 1.
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

    /**
     * The orb's ground: the same cell map (the orb's own Ground family)
     * + the own-cell consult (the same plain-method stash discipline).
     */
    private static final class OrbMapGround implements ExperienceOrbEntity.Ground {
        final Map<Long, BlockType> cells = new HashMap<>();

        @Override
        public boolean isSolid(double x, double y, double z) {
            return WorldSolidity.isSolid(read(cells,
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        }

        // No @Override — the same stash discipline.
        public net.zaminmc.torch.util.Identifier blockIdAt(double centerX, double bottomY,
                                                            double centerZ) {
            return read(cells, (int) Math.floor(centerX),
                    (int) Math.floor(bottomY - 0.2),
                    (int) Math.floor(centerZ)).identifier();
        }

        // No @Override — the OWN-cell seam (the reference's new BlockPos(this)).
        public net.zaminmc.torch.util.Identifier blockIdAtCell(double centerX, double bottomY,
                                                               double centerZ) {
            return read(cells, (int) Math.floor(centerX), (int) Math.floor(bottomY),
                    (int) Math.floor(centerZ)).identifier();
        }
    }

    // ------------------------------------------------------------------
    // The fizz recorders (the Listener arms the managers publish)
    // ------------------------------------------------------------------

    /** Records the item manager's events; the fizz arm is the seat's probe. */
    private static final class ItemRecorder implements ItemEntityManager.Listener {
        int fizzCount;
        float lastPitch = Float.NaN;

        @Override
        public void onItemSpawned(ItemEntity entity) {
        }

        @Override
        public void onItemMoved(ItemEntity entity) {
        }

        @Override
        public void onItemCollected(ItemEntity entity, PlayerSession collector,
                                     int collectedCount) {
        }

        @Override
        public void onItemStackChanged(ItemEntity entity) {
        }

        @Override
        public void onItemRemoved(ItemEntity entity, String reason) {
        }

        // No @Override — the fizz arm (post-fix: the interface's default).
        public void onItemFizzed(ItemEntity entity, float pitch) {
            fizzCount++;
            lastPitch = pitch;
        }
    }

    /** Records the orb manager's events; the fizz arm is the seat's probe. */
    private static final class OrbRecorder implements ExperienceOrbManager.Listener {
        int fizzCount;
        float lastPitch = Float.NaN;

        @Override
        public void onOrbSpawned(ExperienceOrbEntity orb) {
        }

        @Override
        public void onOrbMoved(ExperienceOrbEntity orb) {
        }

        @Override
        public void onOrbCollected(ExperienceOrbEntity orb, PlayerSession collector) {
        }

        @Override
        public void onOrbRemoved(ExperienceOrbEntity orb, String reason) {
        }

        // No @Override — the fizz arm (post-fix: the interface's default).
        public void onOrbFizzed(ExperienceOrbEntity orb, float pitch) {
            fizzCount++;
            lastPitch = pitch;
        }
    }

    // ------------------------------------------------------------------
    // The player harness (the orb manager test's own shape)
    // ------------------------------------------------------------------

    private static PlayerSession player(String name) {
        ClientLink link = new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        };
        PlayerSession session = new PlayerSession(
                UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(stubWorld(), Position.ZERO);
        session.markPlaying();
        return session;
    }

    private static net.zaminmc.torch.World stubWorld() {
        return new net.zaminmc.torch.World() {
            @Override
            public String name() {
                return "stub";
            }

            @Override
            public long timeOfDay() {
                return 0;
            }

            @Override
            public long totalTicks() {
                return 0;
            }

            @Override
            public Position spawnPosition() {
                return Position.ZERO;
            }

            @Override
            public net.zaminmc.torch.block.BlockType getBlock(
                    net.zaminmc.torch.block.BlockPosition p) {
                return null;
            }

            @Override
            public boolean setBlock(
                    net.zaminmc.torch.block.BlockPosition p,
                    net.zaminmc.torch.block.BlockType t) {
                return false;
            }

            @Override
            public boolean isChunkLoaded(net.zaminmc.torch.block.ChunkPosition p) {
                return true;
            }

            @Override
            public java.util.Collection<net.zaminmc.torch.entity.LivingEntity> entities() {
                return java.util.List.of();
            }

            @Override
            public void execute(Runnable work) {
                work.run();
            }

            @Override
            public void setTimeOfDay(long timeOfDay) {
            }

            @Override
            public int blockLightAt(net.zaminmc.torch.block.BlockPosition p) {
                return 0;
            }

            @Override
            public int skyLightAt(net.zaminmc.torch.block.BlockPosition p) {
                return 15;
            }

            @Override
            public String biomeAt(net.zaminmc.torch.block.BlockPosition p) {
                return "plains";
            }
        };
    }

    /** Places the player's body (the pickup harness's own applyMovement). */
    private static void place(PlayerSession session, double x, double y, double z) {
        session.applyMovement(new Position(x, y, z),
                net.zaminmc.torch.util.Rotation.ZERO, true);
    }

    // ------------------------------------------------------------------
    // The pull's closed form (ExperienceOrbEntity.tick lines 92-103):
    // the velocity add = the unit direction toward the player's EYE
    // times (1 - D/8)^2 * 0.1, D the eye-aimed distance.
    // ------------------------------------------------------------------

    static double[] expectedPull(Position orbCenter, Position playerFeet,
                                boolean sneaking, boolean sleeping) {
        double eye = 1.62;
        if (sleeping) {
            eye = 0.2;
        }
        if (sneaking) {
            eye -= 0.08;
        }
        double ox = orbCenter.x();
        double oy = orbCenter.y() - ExperienceOrbEntity.HALF_HEIGHT; // the box bottom
        double oz = orbCenter.z();
        double d1 = (playerFeet.x() - ox) / 8.0;
        double d2 = (playerFeet.y() + eye - oy) / 8.0;
        double d3 = (playerFeet.z() - oz) / 8.0;
        double d4 = Math.sqrt(d1 * d1 + d2 * d2 + d3 * d3);
        double d5 = 1.0 - d4;
        if (d5 > 0.0) {
            d5 *= d5;
            return new double[]{d1 / d4 * d5 * 0.1, d2 / d4 * d5 * 0.1, d3 / d4 * d5 * 0.1};
        }
        return new double[]{0.0, 0.0, 0.0};
    }

    // ------------------------------------------------------------------
    // The item seats
    // ------------------------------------------------------------------

    @Test
    void theItemsCrossingGatePopsItWhenItEntersTheLavaCell() {
        // ItemEntity.tick lines 75-87: an AIRBORNE body crossing a block
        // boundary INTO a lava cell pops on the CROSSING TICK itself — the
        // 0.2 vertical pop, the ±0.2 scatter, the fizz sound at 2.0..2.4 —
        // and the popped velocity rides the same tick's airborne drag.
        ItemMapGround ground = new ItemMapGround();
        stone(ground.cells, 63);
        lake(ground.cells, 64); // a one-deep lava lake; air above
        ItemRecorder recorder = new ItemRecorder();
        ItemEntityManager manager = new ItemEntityManager(ground, new java.util.Random(42), 1);
        manager.addListener(recorder);
        ItemEntity item = manager.spawnRestored(new Position(0.5, 65.625, 0.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0); // bottom 65.5, own cell 65 (air)
        item.setVelocity(0.0, 0.0, 0.0);
        for (int i = 0; i < 4; i++) {
            manager.tick(List.of());
        }
        assertEquals(0, recorder.fizzCount,
                "the airborne descent through AIR cells never pops (no crossing, no poll yet)");
        assertEquals(65.5, item.position().y() - ItemEntity.HALF_HEIGHT, 0.5,
                "the fall is under way above the lake");
        // Tick 5: the bottom crosses 65.0 into the lava cell (the fall
        // train: 0.04, 0.0792, 0.1176, 0.1533, 0.19 — the crossing move).
        manager.tick(List.of());
        assertEquals(1, recorder.fizzCount,
                "the crossing tick pops (the block-boundary gate)");
        assertEquals(0.196, item.velocityY(), 1.0E-9,
                "the 0.2 pop through the same tick's airborne 0.98 drag");
        assertTrue(Math.abs(item.velocityX()) < 0.2,
                "the horizontal scatter stays inside the ±0.2 band");
        assertTrue(Math.abs(item.velocityZ()) < 0.2, "the second scatter axis too");
        assertTrue(recorder.lastPitch >= 2.0F && recorder.lastPitch < 2.4F,
                "random.fizz pitch 2.0F + nextFloat() * 0.4F");
        assertFalse(item.onGround(), "the pop leaves the body airborne inside the lava cell");
    }

    @Test
    void theItemsTwentyFiveTickPollPopsTheRestingBody() {
        // The poll arm: a body RESTING under a one-deep lava lake (its own
        // cell the lava cell, position fixed — no crossing) stays silent
        // through tick 24 and pops exactly at tick 25, the entity's own
        // lifetime counter (the World.updateEntity increment), NOT the
        // despawn age. The grounded drag folds the pop to
        // 0.2 * 0.98 * -0.5 = 0.098 (the onGround flip line 102).
        ItemMapGround ground = new ItemMapGround();
        stone(ground.cells, 63);
        lake(ground.cells, 64);
        ItemRecorder recorder = new ItemRecorder();
        ItemEntityManager manager = new ItemEntityManager(ground, new java.util.Random(42), 1);
        manager.addListener(recorder);
        ItemEntity item = manager.spawnRestored(new Position(0.5, 64.125, 0.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0); // bottom 64.0, the lava cell
        item.setVelocity(0.0, 0.0, 0.0);
        for (int i = 0; i < 24; i++) {
            manager.tick(List.of());
        }
        assertEquals(0, recorder.fizzCount,
                "a resting body crosses no boundary and the poll has not come");
        assertEquals(64.125, item.position().y(), 1.0E-9, "the rest holds through the wait");
        manager.tick(List.of()); // the 25th tick
        assertEquals(1, recorder.fizzCount, "the ticks % 25 == 0 poll pops the resting body");
        assertEquals(-0.098, item.velocityY(), 1.0E-9,
                "the pop through the grounded drag: 0.2 * 0.98 * -0.5 (the flip writes it DOWN — the resting micro-hop)");
        assertTrue(Math.abs(item.velocityX()) <= 0.2 * 0.588 + 1.0E-9,
                "the scatter through the stone's 0.588 ground band");
    }

    @Test
    void theStoneRestNeverFizzes() {
        // The material consult's negative: the gate's poll ticks arrive
        // (25, 50) but the body's own cell is AIR — no pop, no sound, the
        // rest undisturbed.
        ItemMapGround ground = new ItemMapGround();
        stone(ground.cells, 63);
        stone(ground.cells, 64);
        ItemRecorder recorder = new ItemRecorder();
        ItemEntityManager manager = new ItemEntityManager(ground, new java.util.Random(42), 1);
        manager.addListener(recorder);
        ItemEntity item = manager.spawnRestored(new Position(0.5, 65.125, 0.5),
                ItemStack.of(BuiltinItems.DIAMOND, 1), 0); // bottom 65.0, own cell AIR
        item.setVelocity(0.0, 0.0, 0.0);
        for (int i = 0; i < 60; i++) {
            manager.tick(List.of());
        }
        assertEquals(0, recorder.fizzCount, "the polls arrive and the material consult fails");
        assertEquals(0.0, item.velocityY(), 1.0E-9, "the rest stays zeroed");
        assertEquals(65.125, item.position().y(), 1.0E-9, "the rest holds");
    }

    // ------------------------------------------------------------------
    // The orb seats
    // ------------------------------------------------------------------

    @Test
    void theOrbsFizzIsUnconditionalOnTheFirstTick() {
        // ExperienceOrbEntity.tick lines 71-76: NO gate — the consult runs
        // EVERY tick, so a body in lava pops on the FIRST tick (the item
        // waits for a crossing or its poll; the orb never does).
        OrbMapGround ground = new OrbMapGround();
        stone(ground.cells, 63);
        lake(ground.cells, 64);
        OrbRecorder recorder = new OrbRecorder();
        ExperienceOrbManager manager = new ExperienceOrbManager(ground,
                new java.util.Random(42), 1);
        manager.addListener(recorder);
        ExperienceOrbEntity orb = manager.spawnRestored(new Position(0.5, 64.35, 0.5), 7, 0);
        manager.tick(List.of()); // ONE tick, no players
        assertEquals(1, recorder.fizzCount,
                "the unconditional consult pops on the very first tick");
        assertEquals(0.196, orb.velocityY(), 1.0E-9,
                "the 0.2 pop through the airborne 0.98 drag");
        assertTrue(Math.abs(orb.velocityX()) < 0.2, "the horizontal scatter band");
        assertTrue(Math.abs(orb.velocityZ()) < 0.2, "the second scatter axis");
        assertTrue(recorder.lastPitch >= 2.0F && recorder.lastPitch < 2.4F,
                "random.fizz pitch 2.0F + nextFloat() * 0.4F");
    }

    @Test
    void thePullDrawsTheOrbTowardThePlayersEye() {
        // The igniter pull's closed form (lines 92-103): the velocity add
        // is the unit direction toward the player's EYE (feet + 1.62,
        // -0.08 sneaking, 0.2 sleeping) times (1 - D/8)^2 * 0.1 — then the
        // move consumes it and the airborne drag folds 0.98. The orb id
        // 121 (id % 100 = 21) acquires on the FIRST tick (the stagger's
        // own math: 0 < renderTicks - 20 + 21 is true at renderTicks 0).
        OrbMapGround ground = new OrbMapGround();
        stone(ground.cells, 63); // no lava: the pull is the only velocity writer
        ExperienceOrbManager manager = new ExperienceOrbManager(ground,
                new java.util.Random(42), 121);
        ExperienceOrbEntity orb = manager.spawnRestored(new Position(0.5, 66.625, 0.5), 7, 0);
        PlayerSession walker = player("Walker");
        place(walker, 4.5, 64.0, 0.5);
        double[] pull = expectedPull(orb.position(), walker.position(), false, false);
        manager.tick(List.of(walker));
        assertEquals(pull[0] * 0.98, orb.velocityX(), 1.0E-9,
                "the eye-aimed pull through the airborne drag (x)");
        assertEquals((-0.03 + pull[1]) * 0.98, orb.velocityY(), 1.0E-9,
                "gravity and the pull through the airborne drag (y)");
        assertEquals(pull[2] * 0.98, orb.velocityZ(), 1.0E-9, "the pull's z axis");
    }

    @Test
    void theRetargetStaggerDelaysTheLowIdOrbs() {
        // The cadence law (line 80): lastTargetUpdateTick < renderTicks -
        // 20 + id % 100 — the id stagger's own arithmetic: id 320 (r = 20)
        // misses its FIRST retarget window at tick 1 (0 < 0 is false) and
        // acquires at tick 2 (0 < 1); ids 321/322 (r = 21/22) acquire at
        // tick 1. No pull happens before the acquire.
        OrbMapGround ground = new OrbMapGround();
        stone(ground.cells, 63);
        ExperienceOrbManager manager = new ExperienceOrbManager(ground,
                new java.util.Random(42), 320);
        ExperienceOrbEntity low = manager.spawnRestored(new Position(0.5, 66.625, 0.5), 3, 0);
        ExperienceOrbEntity mid = manager.spawnRestored(new Position(0.5, 66.625, 2.5), 3, 0);
        ExperienceOrbEntity high = manager.spawnRestored(new Position(0.5, 66.625, 4.5), 3, 0);
        assertEquals(320, low.entityId());
        assertEquals(321, mid.entityId());
        assertEquals(322, high.entityId());
        PlayerSession walker = player("StaggerWalker");
        place(walker, 4.5, 64.0, 0.5);
        List<PlayerSession> players = List.of(walker);
        // Tick 1: 321/322 acquire (r > 20), 320 does not (r == 20: the
        // gate at renderTicks 0 is 0 < 0 — false).
        double[] midPull = expectedPull(mid.position(), walker.position(), false, false);
        double[] highPull = expectedPull(high.position(), walker.position(), false, false);
        manager.tick(players);
        assertEquals(0.0, low.velocityX(), 1.0E-12,
                "the r=20 orb pulls nothing on tick 1 (no acquire yet)");
        assertEquals(midPull[0] * 0.98, mid.velocityX(), 1.0E-9, "the r=21 orb acquires at tick 1");
        assertEquals(highPull[0] * 0.98, high.velocityX(), 1.0E-9, "the r=22 orb too");
        // Tick 2: 320's window opens (renderTicks 1: 0 < 1 - 20 + 20).
        double[] lowPull = expectedPull(low.position(), walker.position(), false, false);
        manager.tick(players);
        assertEquals(lowPull[0] * 0.98, low.velocityX(), 1.0E-9,
                "the r=20 orb acquires at tick 2");
    }

    @Test
    void theSpectatorStopsPullingImmediately() {
        // The spectator clear (lines 88-90 + the EntityFilter 27-31): the
        // moment the igniter turns spectator the pull stops — the next
        // tick's velocity is the pure drag decay, and it STAYS stopped
        // (the re-acquire's nearest consult filters spectators too).
        OrbMapGround ground = new OrbMapGround();
        stone(ground.cells, 63);
        ExperienceOrbManager manager = new ExperienceOrbManager(ground,
                new java.util.Random(42), 121);
        ExperienceOrbEntity orb = manager.spawnRestored(new Position(0.5, 66.625, 0.5), 7, 0);
        PlayerSession watcher = player("Watcher");
        place(watcher, 4.5, 64.0, 0.5);
        List<PlayerSession> players = List.of(watcher);
        for (int i = 0; i < 3; i++) {
            manager.tick(players);
        }
        assertTrue(orb.velocityX() > 0.0, "the pull is active toward the walker");
        double vxBefore = orb.velocityX();
        watcher.setGamemode(GameMode.SPECTATOR);
        manager.tick(players);
        assertEquals(vxBefore * 0.98, orb.velocityX(), 1.0E-12,
                "the spectator tick carries the drag alone (no pull add)");
        manager.tick(players);
        assertEquals(vxBefore * 0.98 * 0.98, orb.velocityX(), 1.0E-12,
                "and it stays stopped (the nearest consult filters spectators)");
    }

    @Test
    void theIgniterIsStickyAndReacquiresOnlyWhenOutOfRange() {
        // The sticky-target law (lines 80-86): on a retarget tick the
        // CURRENT igniter is kept unless null or beyond 64 squared
        // distance — a CLOSER player never steals the lock; only the
        // out-of-range exit re-acquires (the nearest then wins). The orb
        // id 399 (r = 99) retargets EVERY tick (0 < renderTicks + 79),
        // so the sticky branch itself is exercised on tick 2 and the
        // re-acquire on tick 3.
        OrbMapGround ground = new OrbMapGround();
        stone(ground.cells, 63);
        ExperienceOrbManager manager = new ExperienceOrbManager(ground,
                new java.util.Random(42), 399);
        ExperienceOrbEntity orb = manager.spawnRestored(new Position(0.5, 67.0, 0.5), 7, 0);
        assertEquals(399, orb.entityId());
        PlayerSession near = player("Near"); // acquired first
        PlayerSession far = player("FarCloser"); // enters CLOSER later
        place(near, 2.5, 64.0, 0.5);
        List<PlayerSession> both = new java.util.ArrayList<>(List.of(near));
        // Tick 1: acquire NEAR (the only player).
        double[] nearPull1 = expectedPull(orb.position(), near.position(), false, false);
        manager.tick(both);
        assertEquals(nearPull1[0] * 0.98, orb.velocityX(), 1.0E-9, "NEAR acquired at tick 1");
        // Between ticks: FAR enters, CLOSER than NEAR, on the other side.
        place(far, -1.0, 64.0, 0.5);
        both = new java.util.ArrayList<>(List.of(near, far));
        // Tick 2: the retarget runs (r = 99) — NEAR is within 64 squared:
        // KEPT (the closer FAR does not steal).
        double[] nearPull2 = expectedPull(orb.position(), near.position(), false, false);
        manager.tick(both);
        double expectedKept = (nearPull1[0] * 0.98 + nearPull2[0]) * 0.98;
        assertEquals(expectedKept, orb.velocityX(), 1.0E-9,
                "the sticky igniter keeps NEAR (FAR closer does not steal)");
        assertTrue(orb.velocityX() > 0.0, "still accelerating toward NEAR (+x)");
        // Between ticks: NEAR leaves the 8-block range.
        place(near, 30.5, 64.0, 0.5);
        // Tick 3: the retarget — NEAR beyond 64 squared: re-acquire FAR.
        // Tick 4: FAR's pull has overtaken the carried momentum.
        manager.tick(both);
        manager.tick(both);
        assertTrue(orb.velocityX() < 0.0,
                "NEAR out of range: the re-acquire pulls toward FAR (-x)");
    }
}
