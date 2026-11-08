package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave FT — THE FARM TEMPT FAMILY + THE CTOR SWIM CENSUS (TemptGoal over
 * the four animals' own registrations, the non-scared form):
 *
 * <p>THE ITEMS (the ctor rows): the CHICKEN's WHEAT_SEEDS at 1.0
 * (ChickenEntity line 48), the COW's WHEAT at 1.25 (CowEntity line 30 —
 * the MOOSHROOM inheriting the super ctor's registration whole), the
 * SHEEP's WHEAT at 1.1 (SheepEntity line 58), and the PIG's carrot PAIR
 * at 1.2 (PigEntity lines 38-39 — the plain carrot AND the
 * carrot-on-a-stick, two registrations at one band). The non-scared form
 * (the ctor's {@code false} parameter): shouldContinue is the canStart
 * re-run alone — the holder still the nearest player within 10 blocks
 * still holding the kind's item, no scared window, no postures.</p>
 *
 * <p>THE SWIM LAWS the same family carries: start() PARKS the navigation's
 * swim paths ({@code setCanSwim(false)} — the tempted body walks AROUND
 * the water, TemptGoal lines 72-75) and stop() restores the PARKED value
 * (line 80 — unlike the follow goal's unconditional true); and the CTOR
 * SWIM CENSUS (the reference's own construction law): the cow, the
 * mooshroom, the sheep, the pig, the rabbit, the wolf, the horse, the
 * snow golem, the villager and the iron golem all arm
 * {@code setCanSwim(true)} at construction — THE CHICKEN ALONE keeps the
 * default off (its ctor arms nothing).</p>
 */
class FarmTemptParityDiscoveryTest {

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
            Position p = player.position;
            if (p.x() + 0.3 > minX && p.x() - 0.3 < maxX
                    && p.y() + 1.8 > minY && p.y() < maxY
                    && p.z() + 0.3 > minZ && p.z() - 0.3 < maxZ) {
                return java.util.List.of(player);
            }
            return java.util.List.of();
        }
    }

    private static MobEntity mobIn(ScriptedWorld world, MobType kind, long seed) {
        return new MobEntity(800, kind, new Position(0.5, 8.0, 0.5),
                new Random(seed), world);
    }

    // ------------------------------------------------------------------
    // Seat 1 — the per-kind item gates the claim (the registration rows)
    // ------------------------------------------------------------------

    /**
     * THE ITEM GATES: each kind's own food arms the claim from the
     * nearest player within 10 blocks — and ONLY that food: the chicken
     * refuses the wheat, the cow and the sheep refuse the seeds, the pig
     * refuses the wheat. The pig's PAIR carries both rows: the plain
     * carrot AND the carrot-on-a-stick (the rider's held control item —
     * two registrations at one band, lines 38-39).
     */
    @Test
    void thePerKindItemsGateTheClaim() {
        // The chicken's seeds.
        ScriptedWorld world = new ScriptedWorld();
        MobEntity chicken = mobIn(world, MobType.CHICKEN, 40L);
        world.player.held = "minecraft:wheat_seeds";
        world.player.position = new Position(4.5, 8.0, 0.5); // 4 blocks out
        assertTrue(chicken.goalTemptCanUse(), "the seeds arm the chicken");
        assertTrue(chicken.temptActive());

        world.player.held = "minecraft:wheat";
        assertFalse(chicken.goalTemptCanUse(),
                "the wheat never tempts a chicken (the seeds alone)");
        assertFalse(chicken.temptActive());

        // The cow's and the sheep's wheat (the mooshroom inheriting the
        // cow's registration whole).
        for (MobType kind : new MobType[] {MobType.COW, MobType.MOOSHROOM,
                MobType.SHEEP}) {
            ScriptedWorld grass = new ScriptedWorld();
            MobEntity grazer = mobIn(grass, kind, 41L);
            grass.player.held = "minecraft:wheat";
            grass.player.position = new Position(4.5, 8.0, 0.5);
            assertTrue(grazer.goalTemptCanUse(),
                    kind + " follows the wheat holder");
            grass.player.held = "minecraft:wheat_seeds";
            assertFalse(grazer.goalTemptCanUse(),
                    kind + " never follows the seeds holder");
        }

        // The pig's carrot pair.
        ScriptedWorld pigWorld = new ScriptedWorld();
        MobEntity pig = mobIn(pigWorld, MobType.PIG, 42L);
        pigWorld.player.position = new Position(4.5, 8.0, 0.5);
        pigWorld.player.held = "minecraft:carrot";
        assertTrue(pig.goalTemptCanUse(), "the plain carrot arms the pig");
        // The PAIR's other member keeps the claim LIVE through the item
        // change (the carrot-on-a-stick is the pig's own row too — two
        // registrations at one band, lines 38-39).
        pigWorld.player.held = "minecraft:carrot_on_a_stick";
        assertTrue(pig.goalTemptCanUse(),
                "the carrot-on-a-stick holds the pig's claim (the PAIR)");
        pigWorld.player.held = "minecraft:wheat"; // the stop: no pig food
        assertFalse(pig.goalTemptCanUse(), "the wheat never tempts a pig");
        assertFalse(pig.temptActive(), "the stop landed with the item change");
        // The stop's 100-tick delay: the fresh carrot waits it out.
        for (int i = 0; i < 100; i++) {
            assertFalse(pig.goalTemptCanUse(),
                    "the delay gates the next claim (tick " + i + ")");
        }
        pigWorld.player.held = "minecraft:carrot";
        assertTrue(pig.goalTemptCanUse(),
                "the next claim lands after the delay window");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the walk bands (the registrations' own speed parameters)
    // ------------------------------------------------------------------

    /**
     * THE SPEED BANDS: the tick writes the registration's own parameter
     * as the walk multiplier — the chicken's 1.0, the cow's (and the
     * mooshroom's) 1.25, the sheep's 1.1, the pig's 1.2 (the raw goal
     * parameter over the movement attribute, the parent-follow family's
     * own convention).
     */
    @Test
    void theWalkBandsRideTheRegistrationsOwnSpeeds() {
        ScriptedWorld world = new ScriptedWorld();
        world.player.position = new Position(6.5, 8.0, 0.5); // beyond the hold
        world.player.held = "minecraft:wheat_seeds";
        MobEntity chicken = mobIn(world, MobType.CHICKEN, 43L);
        assertTrue(chicken.goalTemptCanUse());
        chicken.goalTemptTick();
        assertEquals(1.0, chicken.goalWalkMultiplier(), 1e-9,
                "the chicken's 1.0 (ChickenEntity line 48)");

        world.player.held = "minecraft:wheat";
        for (MobType kind : new MobType[] {MobType.COW, MobType.MOOSHROOM}) {
            MobEntity grazer = mobIn(world, kind, 44L);
            assertTrue(grazer.goalTemptCanUse());
            grazer.goalTemptTick();
            assertEquals(1.25, grazer.goalWalkMultiplier(), 1e-9,
                    kind + "'s 1.25 (CowEntity line 30)");
        }
        MobEntity sheep = mobIn(world, MobType.SHEEP, 45L);
        assertTrue(sheep.goalTemptCanUse());
        sheep.goalTemptTick();
        assertEquals(1.1, sheep.goalWalkMultiplier(), 1e-9,
                "the sheep's 1.1 (SheepEntity line 58)");

        world.player.held = "minecraft:carrot";
        MobEntity pig = mobIn(world, MobType.PIG, 46L);
        assertTrue(pig.goalTemptCanUse());
        pig.goalTemptTick();
        assertEquals(1.2, pig.goalWalkMultiplier(), 1e-9,
                "the pig's 1.2 (PigEntity lines 38-39)");

        // NO POSTURE (the creep is the ocelot's own mobAiTick law alone —
        // a tempted farm animal stands normal).
        assertEquals(0, pig.postureFlags(),
                "the farm kinds' tempt carries no creep posture");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the hold band (2.5 blocks: the feet stop)
    // ------------------------------------------------------------------

    /**
     * THE HOLD (TemptGoal.tick lines 98-100: {@code squaredDistanceTo <
     * 6.25} — the navigation stops): within 2.5 blocks the animal stands
     * at the holder's feet, the walk multiplier zeroed.
     */
    @Test
    void theHoldBandStopsTheFeetAtTwoAndAHalfBlocks() {
        ScriptedWorld world = new ScriptedWorld();
        world.player.held = "minecraft:wheat";
        MobEntity cow = mobIn(world, MobType.COW, 47L);
        world.player.position = new Position(2.0, 8.0, 0.5); // 1.5 blocks out
        assertTrue(cow.goalTemptCanUse());
        cow.goalTemptTick();
        assertEquals(0.0, cow.goalWalkMultiplier(), 1e-9,
                "the hold band zeroes the walk");
    }

    // ------------------------------------------------------------------
    // Seat 4 — the swim parks at the claim and returns at the stop
    // ------------------------------------------------------------------

    /**
     * THE SWIM LAWS (TemptGoal lines 72-75 + 80): the claim parks the
     * navigation's swim paths (the tempted body walks AROUND the water)
     * and the stop restores the PARKED value — the farm kinds' ctor-armed
     * true, the chicken's own false (its ctor arms nothing, so the park
     * and the restore are both false — the tempted chicken never gains
     * swim paths from the claim).
     */
    @Test
    void theClaimParksTheSwimPathsAndTheStopRestores() {
        // The farm kind: ctor true, claim false, stop true.
        ScriptedWorld world = new ScriptedWorld();
        world.player.held = "minecraft:wheat";
        world.player.position = new Position(4.5, 8.0, 0.5);
        MobEntity cow = mobIn(world, MobType.COW, 48L);
        assertTrue(cow.navigationCanSwim(), "the ctor armed the swim paths");
        assertTrue(cow.goalTemptCanUse());
        assertFalse(cow.navigationCanSwim(),
                "the claim parked the swim paths (start(), lines 72-75)");
        world.player.held = "minecraft:carrot"; // the wrong item: the stop
        assertFalse(cow.goalTemptCanUse());
        assertTrue(cow.navigationCanSwim(),
                "the stop restored the parked value (line 80)");

        // The chicken: ctor false, claim false, stop false.
        ScriptedWorld chickenWorld = new ScriptedWorld();
        chickenWorld.player.held = "minecraft:wheat_seeds";
        chickenWorld.player.position = new Position(4.5, 8.0, 0.5);
        MobEntity chicken = mobIn(chickenWorld, MobType.CHICKEN, 49L);
        assertFalse(chicken.navigationCanSwim(),
                "the chicken's ctor arms nothing (the census's one refusal)");
        assertTrue(chicken.goalTemptCanUse());
        assertFalse(chicken.navigationCanSwim(),
                "the park of false stands (the claim never arms swim paths)");
        chickenWorld.player.held = "minecraft:wheat";
        assertFalse(chicken.goalTemptCanUse());
        assertFalse(chicken.navigationCanSwim(),
                "the stop restored the chicken's own false");
    }

    // ------------------------------------------------------------------
    // Seat 5 — the ctor swim census (the construction law, ten kinds)
    // ------------------------------------------------------------------

    /**
     * THE CTOR SWIM CENSUS (the reference's own construction law —
     * CowEntity line 26, SheepEntity line 54, PigEntity line 33,
     * RabbitEntity line 55, WolfEntity line 56, HorseBaseEntity line 105,
     * SnowGolemEntity's ctor, VillagerEntity line 307, IronGolemEntity
     * line 41, OcelotEntity line 42): every body of the census arms
     * {@code setCanSwim(true)} at construction — the CHICKEN alone keeps
     * the default off, and the ZOMBIE with it (its ctor arms nothing).
     */
    @Test
    void theCtorSwimCensusArmsTenKinds() {
        ScriptedWorld world = new ScriptedWorld();
        MobType[] armed = {MobType.COW, MobType.MOOSHROOM, MobType.SHEEP,
                MobType.PIG, MobType.RABBIT, MobType.WOLF, MobType.HORSE,
                MobType.SNOW_GOLEM, MobType.VILLAGER, MobType.IRON_GOLEM,
                MobType.OCELOT};
        for (MobType kind : armed) {
            MobEntity body = mobIn(world, kind, 50L);
            assertTrue(body.navigationCanSwim(),
                    kind + "'s ctor arms the swim paths (the census)");
        }
        MobEntity chicken = mobIn(world, MobType.CHICKEN, 51L);
        assertFalse(chicken.navigationCanSwim(),
                "the chicken's ctor arms nothing");
        MobEntity zombie = mobIn(world, MobType.ZOMBIE, 52L);
        assertFalse(zombie.navigationCanSwim(),
                "the zombie's ctor arms nothing (the census's control)");
    }
}
