package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.LivingEntity;
import net.zaminmc.torch.entity.ai.DoorBreak;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The door-break read's public API surface (§27.3's mob-AI family — the
 * VIII9 exposure, the §27.10 capability test): the built-in BREAK_DOOR
 * goal's arming (the {@code CanBreakDoors} row) and its live progress
 * (the door target + the 240-tick clock), exercised through
 * {@link LivingEntity#doorBreak()} over the LIVE engine — every READ
 * through the api-typed handle alone (no cast to engine internals on
 * any read; the body IS the handle, §27.5).
 *
 * <p>The fixtures are VIII9's own (ZombieAttributesTest's shapes, reused
 * as the task directs): the {@code DoorWorld} harness (the flat world
 * with the door cell at (0, 5, 0), the difficulty/mobGriefing pair) and
 * the arming through the established engine paths — the spawn dice
 * ({@code MobManager.rollZombieCanBreakDoors}, initialize line 430's own
 * verb) and the NBT row's landing verb ({@code setCanBreakDoors} — the
 * same verb {@code EntityNbt}'s CanBreakDoors row read lands through).
 * The walk itself runs through the REAL mind: the manager's own tick
 * walk drives the selector, and the completion lands through the
 * manager's listener bridge — the same bridge the server's world write
 * rides (the engine behavior unchanged, ZombieAttributesTest's own
 * pins).</p>
 */
class DoorBreakApiAcceptanceTest {

    /** The flat world with a door cell, the difficulty/mobGriefing pair, and the dark band (VIII9's own fixture). */
    private static class DoorWorld implements MobEntity.WorldQuery {
        boolean hard = true;
        boolean griefing = true;
        boolean dark = true;
        /** The door half's identifier at the cell (0, 5, 0), null when none. */
        Identifier upperAt;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return y < 4.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public boolean difficultyHard() {
            return hard;
        }

        @Override
        public boolean mobGriefing() {
            return griefing;
        }

        @Override
        public int skyLight(int x, int y, int z) {
            return dark ? 0 : 15;
        }

        @Override
        public Identifier blockIdAt(int x, int y, int z) {
            if (upperAt != null && x == 0 && y == 5 && z == 0) {
                return upperAt;
            }
            return null;
        }
    }

    /** The manager-bridge recorder: the door-break family the drain fires (the engine's own events). */
    private static final class Events implements MobManager.Listener {
        final List<int[]> stages = new ArrayList<>();
        int brokenCount;
        int[] brokenAt;

        @Override public void onMobSpawned(MobEntity mob) { }
        @Override public void onMobMoved(MobEntity mob, net.zaminmc.torch.server.player.PlayerSession rider) { }
        @Override public void onMobHurt(MobEntity mob) { }
        @Override public void onMobDied(MobEntity mob) { }
        @Override public void onMobRemoved(MobEntity mob, String reason) { }
        @Override public void onMobAttackedPlayer(MobEntity mob,
                net.zaminmc.torch.server.player.PlayerSession target, float damage) { }
        @Override public void onMobSound(MobEntity mob, String soundName) { }
        @Override public void onMobRangedAttack(MobEntity mob, Position aimPoint) { }
        @Override public void onMobFuseChanged(MobEntity mob, boolean priming) { }
        @Override public void onMobSheared(MobEntity mob, int woolCount) { }
        @Override public void onMobCoatRegrown(MobEntity mob) { }
        @Override public void onMobExploded(MobEntity mob) { }

        @Override public void onZombieDoorBreakProgress(MobEntity mob, int x, int y, int z, int stage) {
            stages.add(new int[] {x, y, z, stage});
        }

        @Override public void onZombieDoorBroken(MobEntity mob, int x, int y, int z) {
            brokenCount++;
            brokenAt = new int[] {x, y, z};
        }
    }

    private record Rig(MobManager manager, Events events, DoorWorld world) {
    }

    private static final MobManager.LootSink NO_LOOT = (position, stack) -> {
    };

    private Rig rig(long seed) {
        DoorWorld world = new DoorWorld();
        Events events = new Events();
        MobManager manager = new MobManager(world, new Random(seed), NO_LOOT,
                3_100_000, (x, z) -> 4);
        manager.setDifficultyRead(() -> world.hard ? 3 : 2);
        manager.addListener(events);
        return new Rig(manager, events, world);
    }

    /** The scene's door cell (the flat ground level 4: the door stands at y=5). */
    private static final BlockPosition DOOR = new BlockPosition(0, 5, 0);

    /** The fixture's zombie, standing inside the door's lower cell (the engine reference — the fixture's own). */
    private static MobEntity zombieAt(Rig rig) {
        MobEntity mob = rig.manager().spawnAt(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        assertNotNull(mob, "the zombie spawned");
        return mob;
    }

    // ------------------------------------------------------------- the arming read

    /**
     * The arming read answers the {@code CanBreakDoors} row: the spawn
     * dice arm it (the seeded roll, the VIII9 dice test's own verb — a
     * first float under the 0.1 band at the HARD-fresh multiplier 1.0),
     * the NBT row's landing verb flips it (the same verb the anvil
     * import's row read lands through), the EASY multiplier never arms
     * (the unarmed state answered on the present view), and a non-zombie
     * body answers the empty optional — the goal never arms on another
     * kind.
     */
    @Test
    void theArmingReadAnswersTheCanBreakDoorsRow() {
        Rig rig = rig(51L);
        MobEntity zombie = zombieAt(rig);
        LivingEntity handle = zombie; // the api-typed handle — every read below goes through it

        // THE VIEW: a zombie answers it (armed or not — the unarmed state
        // is itself the read, §27.3's complete exposure).
        var view = handle.doorBreak();
        assertTrue(view.isPresent(), "a zombie answers the door-break view");
        DoorBreak read = view.get();

        // The spawn dice (initialize line 430's own verb, the multiplier
        // 1.0 the HARD-fresh cap): the seeded first float 0.0245 < 0.1
        // arms — the read answers the row.
        MobManager.rollZombieCanBreakDoors(zombie, new Random(31_337L), 1.0F);
        assertTrue(read.canBreakDoors(), "the spawn-dice arm answers through the read");

        // The EASY law (the multiplier 0.0 — nextFloat() < 0 never arms):
        // the SAME body answers the unarmed state, the view still present.
        MobManager.rollZombieCanBreakDoors(zombie, new Random(31_337L), 0.0F);
        assertFalse(read.canBreakDoors(), "the EASY multiplier never arms (the unarmed state)");

        // The NBT row's landing verb (EntityNbt's CanBreakDoors row read
        // lands through setCanBreakDoors — the anvil import's own path):
        // the row flips the read.
        zombie.setZombieCanBreakDoors(true);
        assertTrue(read.canBreakDoors(), "the NBT row's verb arms the read");

        // THE KIND GATE: a pig carries no door-break goal at all.
        LivingEntity pig = rig.manager().spawnAt(MobType.PIG, new Position(8.5, 4.0, 8.5));
        assertNotNull(pig, "the pig spawned");
        assertTrue(pig.doorBreak().isEmpty(), "a non-zombie body answers the empty optional");
    }

    // ------------------------------------------------------------- the live progress read

    /**
     * The progress read over the LIVE walk: an armed zombie claims the
     * closed wooden door (the VIII9 fixture's clip-site capture — the
     * fallback cell above a body standing inside a door cell), and the
     * manager's own tick walk drives the real mind (the selector's
     * band-1 BREAK_DOOR claim) — the read answering the door target and
     * the clock advancing tick over tick, the engine's completion
     * landing through the manager's bridge (the same bridge the
     * server's world write rides), and the walk's own publications (the
     * 0-10 stage band) firing at the door cell — the engine behavior
     * unchanged.
     */
    @Test
    void theProgressReadAdvancesOverTheLiveWalkAndTheDoorBreaks() {
        Rig rig = rig(52L);
        rig.world().upperAt = Identifier.parse("minecraft:oak_door_upper");
        MobEntity zombie = zombieAt(rig);
        LivingEntity handle = zombie; // the api-typed handle — every read below goes through it
        MobManager.rollZombieCanBreakDoors(zombie, new Random(31_337L), 1.0F);

        // The clip-site capture (the VIII9 fixture's own arming verb —
        // the reference's canStart fallback: the cell above the body).
        assertTrue(zombie.armZombieDoorBreak(), "the closed wooden upper arms the walk");

        // THE READ before the walk: the walk owns the door, the clock at
        // zero, the target the upper half's cell.
        DoorBreak read = handle.doorBreak().orElseThrow();
        assertTrue(read.breaking(), "the walk owns the door");
        assertEquals(DOOR, read.doorPosition(), "the door target is the upper half's cell");
        assertEquals(0, read.progress(), "the clock starts at zero");

        // THE LIVE WALK: 240 manager ticks (night — the undead never
        // burn; no players — no hunt, no despawn). The selector's own
        // band-1 claim drives the clock; the read resolves between ticks.
        // The 240th tick's own read answers the POST-DRAIN state (the
        // completion's reset lands inside the same manager tick) — the
        // walk's in-flight law is asserted over the ticks before it.
        int lastProgress = 0;
        for (int tick = 1; tick <= 240; tick++) {
            rig.manager().tick(List.of(), 14_000L);
            if (tick == 240) {
                break; // the completion tick: asserted below, post-drain
            }
            int progress = read.progress();
            assertTrue(read.breaking(), "the walk holds the body (tick " + tick + ")");
            assertEquals(DOOR, read.doorPosition(), "the door target holds the cell");
            assertTrue(progress >= lastProgress, "the clock never runs backward");
            if (tick == 1 || tick == 24 || tick == 120 || tick == 239) {
                // The sampled whole-clock reads: one tick, one count (the
                // reference's breakProgress++ — the walk's own law).
                assertEquals(tick, progress, "the clock counts the walk's own ticks");
            }
            lastProgress = progress;
        }

        // THE COMPLETION (the 240th tick on HARD): the engine's bridge
        // fired — the door cell's break through the manager's listener
        // (the same bridge the server's world write + the 1012/2001 pair
        // ride), and the stage publications walked the whole 0-10 band
        // at the door cell (eleven, one per 24 ticks — the reference's
        // progress/240*10 on change).
        assertEquals(1, rig.events().brokenCount, "the door break landed through the bridge");
        assertEquals(DOOR.x(), rig.events().brokenAt[0], "the break's cell x");
        assertEquals(DOOR.y(), rig.events().brokenAt[1], "the break's cell y");
        assertEquals(DOOR.z(), rig.events().brokenAt[2], "the break's cell z");
        assertEquals(11, rig.events().stages.size(), "the eleven stage publications");
        for (int i = 0; i < rig.events().stages.size(); i++) {
            int[] row = rig.events().stages.get(i);
            assertEquals(DOOR.x(), row[0], "the stage's cell x");
            assertEquals(DOOR.y(), row[1], "the stage's cell y");
            assertEquals(DOOR.z(), row[2], "the stage's cell z");
            assertEquals(i, row[3], "the stages walk 0..10 one at a time");
        }

        // THE READ after the walk: the capture cleared (the completion's
        // own tick), the clock reset (the drain's reset), the ARMING
        // FLAG STILL ARMED — the zombie remains a door-breaker (the
        // reference's own shape: the flag persists across walks).
        assertFalse(read.breaking(), "the completion releases the door");
        assertEquals(0, read.progress(), "the drain resets the clock");
        assertTrue(read.canBreakDoors(), "the arming flag survives the completion");
    }

    // ------------------------------------------------------------- the unarmed control

    /**
     * The unarmed control: the EASY-rolled zombie standing in the same
     * door cell never claims it — the capture verb refuses the flag
     * gate (the engine's own refusal), and over the manager's live walk
     * the read answers the unarmed state every tick — no door target
     * owned, no clock, no bridge events.
     */
    @Test
    void theUnarmedZombieNeverClaimsADoor() {
        Rig rig = rig(53L);
        rig.world().upperAt = Identifier.parse("minecraft:oak_door_upper");
        MobEntity zombie = zombieAt(rig);
        LivingEntity handle = zombie; // the api-typed handle — every read below goes through it
        MobManager.rollZombieCanBreakDoors(zombie, new Random(31_337L), 0.0F); // the EASY law

        assertFalse(zombie.armZombieDoorBreak(), "the capture verb refuses the unarmed flag");

        DoorBreak read = handle.doorBreak().orElseThrow();
        assertFalse(read.canBreakDoors(), "the unarmed state answers");
        for (int tick = 0; tick < 30; tick++) {
            rig.manager().tick(List.of(), 14_000L);
            assertFalse(read.breaking(), "the unarmed body never owns the door (tick " + tick + ")");
            assertEquals(0, read.progress(), "the clock never starts");
        }
        assertEquals(0, rig.events().stages.size(), "no stage ever published");
        assertEquals(0, rig.events().brokenCount, "no break ever landed");
    }
}
