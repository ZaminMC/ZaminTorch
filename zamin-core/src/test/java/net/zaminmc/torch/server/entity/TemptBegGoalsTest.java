package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mind's awareness goals (Slice 16a — the reference's TemptGoal,
 * FleeEntityGoal and WolfBegGoal): the ocelot's fish-follow with the
 * scared-by-movement check, the untamed cat's player-flee with the close
 * sprint, and the wolf's begging gaze over its own timer window.
 */
class TemptBegGoalsTest {

    /** The scripted player: the view the mind reads, mutable per phase. */
    private static final class ScriptedPlayer implements MobEntity.PlayerView {
        Position position = new Position(4.5, 8.0, 0.5);
        float yaw;
        float pitch;
        String held;

        @Override public Position position() {
            return position;
        }

        @Override public float yaw() {
            return yaw;
        }

        @Override public float pitch() {
            return pitch;
        }

        @Override public String heldItem() {
            return held;
        }
    }

    /** A flat-world stub with the scripted player inside it. */
    private static final class ScriptedWorld implements MobEntity.WorldQuery {
        final ScriptedPlayer player = new ScriptedPlayer();

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            double dx = player.position.x() - x;
            double dy = player.position.y() - y;
            double dz = player.position.z() - z;
            if (dx * dx + dy * dy + dz * dz > range * range) {
                return null;
            }
            return player.position;
        }

        @Override
        public MobEntity.PlayerView nearestPlayerView(double x, double y, double z, double range) {
            double dx = player.position.x() - x;
            double dy = player.position.y() - y;
            double dz = player.position.z() - z;
            if (dx * dx + dy * dy + dz * dz > range * range) {
                return null;
            }
            return player;
        }

        @Override
        public java.util.List<MobEntity.PlayerView> playersInBox(
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) {
            // The player's 0.6x1.8 box against the gather box, STRICT.
            Position p = player.position;
            if (p.x() + 0.3 > minX && p.x() - 0.3 < maxX
                    && p.y() + 1.8 > minY && p.y() < maxY
                    && p.z() + 0.3 > minZ && p.z() - 0.3 < maxZ) {
                return java.util.List.of(player);
            }
            return java.util.List.of();
        }
    }

    private static MobEntity ocelotIn(ScriptedWorld world, long seed) {
        return new MobEntity(700, MobType.OCELOT, new Position(0.5, 8.0, 0.5),
                new Random(seed), world);
    }

    private static MobEntity wolfIn(ScriptedWorld world, long seed) {
        return new MobEntity(701, MobType.WOLF, new Position(0.5, 8.0, 0.5),
                new Random(seed), world);
    }

    @Test
    void theTemptFollowsTheFishHolderAndCreeps() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity cat = ocelotIn(world, 30L);
        world.player.held = "minecraft:fish";
        world.player.position = new Position(4.5, 8.0, 0.5); // 4 blocks out

        assertTrue(cat.goalTemptCanUse(), "the fresh start finds the fish-holder");
        assertTrue(cat.temptActive());
        cat.goalTemptTick();
        assertTrue(cat.postureFlags() == 0x02, "the creep walk sneaks (mobAiTick's 0.6 rule)");
        assertFalse(cat.postureSprinting());

        // The tame's gate: the interact arm only opens while tempted.
        assertTrue(cat.temptActive());

        // The approach closes to the 2.5-block hold band: the feet stop.
        world.player.position = new Position(2.0, 8.0, 0.5);
        cat.goalTemptTick();
        assertTrue(cat.postureFlags() == 0, "the hold band stands normal");
    }

    @Test
    void theScaredCheckKillsTheTemptInsideSixBlocks() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity cat = ocelotIn(world, 31L);
        world.player.held = "minecraft:fish";
        world.player.position = new Position(4.0, 8.0, 0.5);

        assertTrue(cat.goalTemptCanUse());
        cat.goalTemptTick();

        // Within 6 blocks: a move beyond 0.01 scares the cat off.
        world.player.position = new Position(4.0, 8.0, 1.0); // half a block aside
        assertFalse(cat.goalTemptCanUse(), "the movement inside the scared window kills the goal");
        assertFalse(cat.temptActive());

        // The stop's penalty: 100 failed tries before the fish works again
        // (canStart's delay gate decrements on every call).
        world.player.position = new Position(4.0, 8.0, 0.5);
        for (int i = 0; i < 100; i++) {
            assertFalse(cat.goalTemptCanUse(), "the delay gate holds (tick " + i + ")");
        }
        assertTrue(cat.goalTemptCanUse(), "the 101st try starts fresh (100 decrements consumed)");
        assertTrue(cat.temptActive());

        // The rotation arm: a big turn inside the window kills it too.
        cat.goalTemptTick();
        world.player.yaw = 40.0f;
        assertFalse(cat.goalTemptCanUse(), "the 5-degree turn scares the cat");
        assertFalse(cat.temptActive());
    }

    @Test
    void theTemptOnlyTakesTheRawFishFromTheNearestPlayer() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity cat = ocelotIn(world, 32L);

        world.player.held = "minecraft:beef";
        assertFalse(cat.goalTemptCanUse(), "the beef never tempts a cat");

        world.player.held = null; // the empty hand
        assertFalse(cat.goalTemptCanUse());

        world.player.held = "minecraft:fish";
        world.player.position = new Position(30.5, 8.0, 0.5); // beyond the 10-block range
        assertFalse(cat.goalTemptCanUse(), "the fish beyond 10 blocks is out of mind");
    }

    @Test
    void theWildCatFleesPlayersAndSprintsClose() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity cat = ocelotIn(world, 33L);
        world.player.position = new Position(8.0, 8.0, 0.5); // 7.5 blocks out

        // PE-15: the canUse runs FleeEntityGoal.canStart whole — the
        // grown-box census, the visibility ray, the away-half-plane pick,
        // the farther-check, and the path pair; true means the claim armed.
        assertTrue(cat.goalFleePlayerCanUse(), "the visible player in the grown box scares the wild cat");
        cat.goalTickFlee();
        assertFalse(cat.postureSprinting(), "the far band walks at 0.8, no sprint");
        assertEquals(MobEntity.Mode.PANIC, cat.mode());

        // The heading walks the AWAY PATH: the picked cell sits in the west
        // half-plane the filter keeps (every candidate that moves back
        // toward the east player is rejected) — the first leg walks west.
        assertTrue(cat.goalHasPathHeading(), "the flee arms a navigation path");
        assertTrue(Math.sin(Math.toRadians(cat.goalPathYaw())) >= -1e-6,
                "the away half-plane's first leg never walks east (yaw "
                        + cat.goalPathYaw() + ")");

        // The close band: within 7 blocks the cat sprints (1.33 → 0x10). A
        // spent short path re-arms through canUse (the selector's own shape).
        world.player.position = new Position(3.0, 8.0, 0.5);
        if (cat.goalFleePlayerCanUse()) {
            cat.goalTickFlee();
        }
        assertTrue(cat.postureSprinting(), "the 7-block close band sets the sprint bit");
        assertTrue(cat.postureFlags() == 0x10);

        // The tamed cat never flees (onTamedChanged's own removal).
        cat.tameOcelot("Owner", "", 1);
        assertFalse(cat.goalFleePlayerCanUse(), "the tamed cat stands its ground");
    }

    @Test
    void theWolfBegsAtTheBoneHolderOverItsTimer() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity dog = wolfIn(world, 34L);
        world.player.held = "minecraft:bone";
        world.player.position = new Position(5.5, 8.0, 0.5); // 5 blocks out

        assertTrue(dog.goalBegCanUse(), "the bone-holder draws the beg");
        assertTrue(dog.wolfBegging(), "start() raises the index-19 flag");
        dog.goalBegTick();
        assertTrue(dog.wolfBegging());

        // The timer burns down: 40 + nextInt(40) ticks of begging.
        int burned = 0;
        while (dog.wolfBegging() && burned < 200) {
            dog.goalBegTick();
            burned++;
        }
        assertTrue(burned >= 40 && burned < 80, "the window is 40-79 ticks (burned " + burned + ")");
        assertFalse(dog.wolfBegging(), "the timer out clears the flag");

        // A fresh start re-arms (the canUse re-roll).
        assertTrue(dog.goalBegCanUse());
        assertTrue(dog.wolfBegging());

        // The player walks out of the 8-block reach: the stop.
        world.player.position = new Position(20.5, 8.0, 0.5);
        assertFalse(dog.goalBegCanUse());
        assertFalse(dog.wolfBegging(), "out of reach the head comes up");

        // The beef begs any wolf (the breeding family); the bone begs only
        // the untamed one.
        dog.tameWolf("Owner", "");
        world.player.held = "minecraft:beef";
        world.player.position = new Position(5.5, 8.0, 0.5);
        assertTrue(dog.goalBegCanUse(), "the meat family begs the tamed wolf too");
        dog.goalBegTick();
        dog.tameWolf("Owner", ""); // idempotent flag state
        world.player.held = "minecraft:bone";
        dog.setWolfBegging(false);
        assertFalse(dog.goalBegCanUse(), "the bone no longer begs the tamed wolf");
    }

    @Test
    void theGoalsRideTheStandardSelector() {
        // The selector's own band order: the float and the sun-shade flag
        // (TF-7f, additive — the reference's own swim-before-avoid order)
        // before the zombie's door break (VIII9) at band 1 after the
        // panic, then beg (additive) before tempt before the escape and
        // flee, then the tameable family's follow + perch (TF-1) before
        // the rabbit's garden raid (Slice 21d) below the stroll's
        // exclusive claim.
        GoalSelector selector = MobGoals.standard();
        var snapshot = selector.snapshot();
        // The VT-7k village return joins at the stroll's band, before it
        // (the reference's own order: the village walk 5 over the plain
        // wander 9); the TF-1 pair joins before both (the reference's own
        // order: the follow 5 over the perch 6 over the wander 7/10).
        // Wave PE-22's idle-life family joins beside them: the golem's
        // glance (additive, LOOK-only beside the beg), the villager's
        // door open FIRST in the walk band (the reference's 4 under the
        // village walk's 5), the baby's parent walk, the child's golem
        // walk LATE (the reference's 7 over the wander's 9), and the
        // graze LAST before the stroll (the sheep ladder's 5-over-6).
        // Wave PE-23's standing tails join: the shelter walk FIRST in
        // band 2 (the reference's own villager ladder — StayIndoors
        // band 2 over the mating 6), the door guard ADDITIVE in band 3
        // (the reference's zero-controls registration), the universal
        // look-at beside the glance (LOOK-only, additive), the farmer's
        // farm walk (the reference's band 6) before the golem walk,
        // the child's caravan (the reference's band 8) between the
        // golem walk and the graze, and the idle look-around AFTER the
        // stroll (every registrant's own ladder — the look 7/8/11
        // after the wander 6/7/9/10).
        assertEquals(26, snapshot.size(),
                "float, avoid_sunlight, panic, break_door, flee_zombie, stay_indoors,"
                        + " breed, beg, golem_look, look_at, restrict_door, tempt,"
                        + " escape_sunlight, flee_player, open_door, follow_owner,"
                        + " sit_on_block, follow_parent, rabbit_raid_farm, village_return,"
                        + " villager_farm, follow_golem, form_caravan, eat_grass,"
                        + " stroll, look_around");
        assertEquals("avoid_sunlight", snapshot.get(1).goalId());
        assertEquals(0, snapshot.get(1).priority());
        assertFalse(snapshot.get(1).exclusive(), "the sun-shade flag is additive");
        assertEquals("break_door", snapshot.get(3).goalId());
        assertEquals(1, snapshot.get(3).priority());
        assertTrue(snapshot.get(3).exclusive());
        assertEquals("flee_zombie", snapshot.get(4).goalId());
        assertEquals(1, snapshot.get(4).priority());
        assertTrue(snapshot.get(4).exclusive());
        assertEquals("stay_indoors", snapshot.get(5).goalId());
        assertEquals(2, snapshot.get(5).priority());
        assertTrue(snapshot.get(5).exclusive(), "the shelter walk holds the MOVE bit");
        assertEquals("beg", snapshot.get(7).goalId());
        assertFalse(snapshot.get(7).exclusive(), "the beg is LOOK-only, additive");
        assertEquals("golem_look", snapshot.get(8).goalId());
        assertFalse(snapshot.get(8).exclusive(), "the glance is LOOK-only, additive");
        assertEquals("look_at", snapshot.get(9).goalId());
        assertFalse(snapshot.get(9).exclusive(), "the universal look-at is LOOK-only, additive");
        assertEquals("restrict_door", snapshot.get(10).goalId());
        assertFalse(snapshot.get(10).exclusive(),
                "the door guard sets no controls — it runs beside every walk");
        assertEquals("tempt", snapshot.get(11).goalId());
        assertTrue(snapshot.get(11).exclusive());
        assertEquals("escape_sunlight", snapshot.get(12).goalId());
        assertTrue(snapshot.get(12).exclusive());
        assertEquals("flee_player", snapshot.get(13).goalId());
        assertTrue(snapshot.get(13).exclusive());
        assertEquals("open_door", snapshot.get(14).goalId());
        assertTrue(snapshot.get(14).exclusive());
        assertEquals("follow_owner", snapshot.get(15).goalId());
        assertTrue(snapshot.get(15).exclusive());
        assertEquals("sit_on_block", snapshot.get(16).goalId());
        assertTrue(snapshot.get(16).exclusive());
        assertEquals("follow_parent", snapshot.get(17).goalId());
        assertTrue(snapshot.get(17).exclusive());
        assertEquals("rabbit_raid_farm", snapshot.get(18).goalId());
        assertTrue(snapshot.get(18).exclusive());
        assertEquals("village_return", snapshot.get(19).goalId());
        assertTrue(snapshot.get(19).exclusive());
        assertEquals("villager_farm", snapshot.get(20).goalId());
        assertTrue(snapshot.get(20).exclusive());
        assertEquals("follow_golem", snapshot.get(21).goalId());
        assertTrue(snapshot.get(21).exclusive());
        assertEquals("form_caravan", snapshot.get(22).goalId());
        assertTrue(snapshot.get(22).exclusive());
        assertEquals("eat_grass", snapshot.get(23).goalId());
        assertTrue(snapshot.get(23).exclusive());
        assertEquals("stroll", snapshot.get(24).goalId());
        assertEquals("look_around", snapshot.get(25).goalId());
        assertTrue(snapshot.get(25).exclusive(),
                "the look-around holds the MOVE bit too — a live window keeps the idle body standing");
        assertEquals(4, snapshot.get(23).priority());
    }

    @Test
    void theOcelotsTameGateReadsTheTemptState() {
        // The engine's interact arm (interactOcelotOnTick) reads
        // temptActive() — a cat not tempted takes no fish. The unit seam
        // is the flag itself: fresh cats are never tempted.
        ScriptedWorld world = new ScriptedWorld();
        MobEntity cat = ocelotIn(world, 35L);
        assertFalse(cat.temptActive());

        world.player.held = "minecraft:fish";
        world.player.position = new Position(5.5, 8.0, 0.5);
        assertTrue(cat.goalTemptCanUse());
        assertTrue(cat.temptActive(), "only now does the fish arm open");

        // The other kinds never tempt (the kind gate).
        MobEntity dog = wolfIn(world, 36L);
        world.player.held = "minecraft:fish";
        assertFalse(dog.goalTemptCanUse(), "the wolf ignores the fish-follow");
        assertFalse(dog.goalFleePlayerCanUse(), "the wolf never flees players");
        MobEntity pig = new MobEntity(702, MobType.PIG, new Position(0.5, 8.0, 0.5),
                new Random(37L), world);
        assertFalse(pig.goalTemptCanUse());
        assertFalse(pig.goalBegCanUse());
    }
}
