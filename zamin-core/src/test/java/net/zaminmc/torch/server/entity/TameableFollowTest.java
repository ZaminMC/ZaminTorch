package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tameable family's owner-aware lane (Slice TF-1 — the reference's
 * FollowOwnerGoal, OcelotSitOnBlockGoal over GoToBlockGoal, the ocelot's
 * UntamedActiveTargetGoal(chicken), and PounceAtTargetGoal on both kinds):
 * the follow's start/continue bands and gates, the swim window with the
 * reference's own unconditional stop write, the teleport's outer-ring scan
 * over the failed path, the perch's cadence scan + walk + sit + stand-down,
 * the chest-viewer gate, the untamed chicken hunt, and the pounce's band,
 * leap, and airborne window.
 */
class TameableFollowTest {

    /** The fixed owner view the stub answers (position + the spectator flag). */
    private static final class OwnerView implements MobEntity.PlayerView {
        final Position position;
        final boolean spectator;

        OwnerView(Position position, boolean spectator) {
            this.position = position;
            this.spectator = spectator;
        }

        @Override public Position position() {
            return position;
        }

        @Override public float yaw() {
            return 0f;
        }

        @Override public float pitch() {
            return 0f;
        }

        @Override public String heldItem() {
            return null;
        }

        @Override public boolean spectator() {
            return spectator;
        }
    }

    /**
     * The stub world: a flat floor below y=8 (the pet's plane), an owner
     * view answered for the tamed pet's consult, the perch blocks the
     * sit-on-block scan reads, and the mob the hunt scans find.
     */
    private static class TameableWorld implements MobEntity.WorldQuery {
        OwnerView owner;
        MobEntity nearbyMob;
        MobType nearbyMobKind;
        /** The block identities by cell key "x,y,z" (null reads air). */
        final Map<String, String> blocks = new HashMap<>();
        /** The chest-viewer count by cell key. */
        final Map<String, Integer> viewers = new HashMap<>();

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public MobEntity.PlayerView ownerPlayerView(String ownerName, String ownerUuid) {
            return owner;
        }

        @Override
        public Identifier blockIdAt(int x, int y, int z) {
            // The engine's live query always answers an identifier — air
            // above the floor plane, stone inside it — never null.
            String id = blocks.get(x + "," + y + "," + z);
            if (id != null) {
                return Identifier.parse(id);
            }
            return Identifier.parse(y >= 8 ? "minecraft:air" : "minecraft:stone");
        }

        @Override
        public int containerViewersAt(int x, int y, int z) {
            return viewers.getOrDefault(x + "," + y + "," + z, 0);
        }

        @Override
        public MobEntity nearestMobOfKind(double x, double y, double z, double range,
                                          MobType kind) {
            return kind == nearbyMobKind ? nearbyMob : null;
        }
    }

    private static MobEntity tamedWolf(TameableWorld world, Position at) {
        MobEntity wolf = new MobEntity(500, MobType.WOLF, at, new Random(7L), world);
        wolf.tameWolf("Owner", "");
        wolf.setWolfSitting(false); // the tame's sitting start stands down
        return wolf;
    }

    private static MobEntity tamedCat(TameableWorld world, Position at) {
        MobEntity cat = new MobEntity(600, MobType.OCELOT, at, new Random(11L), world);
        cat.tameOcelot("Owner", "", 1);
        cat.setWolfSitting(false);
        return cat;
    }

    @Test
    void theOcelotsNavigationSwimsFromBirthAndTheWolfsDoesNot() {
        // OcelotEntity's ctor line 42 arms the cat's swim paths; the wolf's
        // navigation keeps the evaluator's own default off.
        TameableWorld world = new TameableWorld();
        MobEntity cat = new MobEntity(601, MobType.OCELOT, new Position(0.5, 8, 0.5),
                new Random(1L), world);
        assertTrue(cat.navigationCanSwim(), "the cat's ctor arms the swim paths");
        MobEntity wolf = new MobEntity(501, MobType.WOLF, new Position(0.5, 8, 0.5),
                new Random(1L), world);
        assertFalse(wolf.navigationCanSwim(), "the wolf's navigation starts dry");
    }

    @Test
    void theFollowStartsOnlyBeyondTheTenBlockBand() {
        TameableWorld world = new TameableWorld();
        world.owner = new OwnerView(new Position(12.5, 8, 0.5), false);
        MobEntity wolf = tamedWolf(world, new Position(0.5, 8, 0.5));
        assertTrue(wolf.goalFollowOwnerCanUse(false), "12 blocks out: the follow starts");

        TameableWorld near = new TameableWorld();
        near.owner = new OwnerView(new Position(5.5, 8, 0.5), false);
        MobEntity nearWolf = tamedWolf(near, new Position(0.5, 8, 0.5));
        assertFalse(nearWolf.goalFollowOwnerCanUse(false), "5 blocks out: no follow");
    }

    @Test
    void theSpectatorOwnerTheSittingBodyAndTheWildKindNeverFollow() {
        TameableWorld world = new TameableWorld();
        world.owner = new OwnerView(new Position(12.5, 8, 0.5), true);
        MobEntity wolf = tamedWolf(world, new Position(0.5, 8, 0.5));
        assertFalse(wolf.goalFollowOwnerCanUse(false), "a spectating owner is not followed");

        world.owner = new OwnerView(new Position(12.5, 8, 0.5), false);
        wolf.setWolfSitting(true);
        assertFalse(wolf.goalFollowOwnerCanUse(false), "a sitting body stays put");

        MobEntity wild = new MobEntity(502, MobType.WOLF, new Position(0.5, 8, 0.5),
                new Random(3L), world);
        assertFalse(wild.goalFollowOwnerCanUse(false), "the wild kind has no owner to follow");

        world.owner = null;
        assertFalse(wolf.goalFollowOwnerCanUse(false), "no owner in the world: no follow");
    }

    @Test
    void theSwimWindowCarriesTheReferencesOwnStopWrite() {
        TameableWorld world = new TameableWorld();
        world.owner = new OwnerView(new Position(12.5, 8, 0.5), false);
        MobEntity cat = tamedCat(world, new Position(0.5, 8, 0.5));
        assertTrue(cat.navigationCanSwim(), "the cat's swim paths start armed");
        assertTrue(cat.goalFollowOwnerCanUse(false), "the window opens");
        assertFalse(cat.navigationCanSwim(), "start(): the swim paths drop for the walk");

        // The stop (the owner leaves the world): the reference's own
        // unconditional setCanSwim(true) — never a restore of the parked
        // value (the 1.8.8 source's dead isInWater field).
        world.owner = null;
        assertFalse(cat.goalFollowOwnerCanUse(false));
        assertTrue(cat.navigationCanSwim(), "stop(): the swim paths return unconditionally");
    }

    @Test
    void theFailedPathTeleportsThroughTheOuterRing() {
        // The pillar world: the pet stands on a lone 8-tall column — every
        // neighbor's landing sits deeper than the pathfinder's safe fall,
        // so the walk's own findPath answers null (pathnode == start) and
        // the reference's moveTo(Entity) fails. The owner on the ground
        // plane 20+ blocks out clears the 12-block teleport gate.
        class PillarWorld extends TameableWorld {
            @Override
            public boolean isSolid(double x, double y, double z) {
                int fy = (int) Math.floor(y);
                if (fy < 0) {
                    return true; // the ground plane
                }
                return fy < 8 && (int) Math.floor(x) == 0 && (int) Math.floor(z) == 0;
            }
        }
        PillarWorld world = new PillarWorld();
        world.owner = new OwnerView(new Position(20.5, 0, 0.5), false);
        MobEntity wolf = tamedWolf(world, new Position(0.5, 8, 0.5));
        assertTrue(wolf.goalFollowOwnerCanUse(false), "the owner stands far beyond the band");

        wolf.goalFollowOwnerTick();
        // The ring's first passer: (floor(owner.x) - 2 + 0.5, floor(owner.y),
        // floor(owner.z) - 2 + 0.5) — the outer ring, never the center 3x3.
        assertEquals(18.5, wolf.position().x(), 1e-9, "the ring's first cell x");
        assertEquals(0.0, wolf.position().y(), 1e-9, "the owner's feet level");
        assertEquals(-1.5, wolf.position().z(), 1e-9, "the ring's first cell z");
    }

    @Test
    void theLeashedPetNeverTeleports() {
        class PillarWorld extends TameableWorld {
            @Override
            public boolean isSolid(double x, double y, double z) {
                int fy = (int) Math.floor(y);
                if (fy < 0) {
                    return true;
                }
                return fy < 8 && (int) Math.floor(x) == 0 && (int) Math.floor(z) == 0;
            }
        }
        PillarWorld world = new PillarWorld();
        world.owner = new OwnerView(new Position(20.5, 0, 0.5), false);
        MobEntity wolf = tamedWolf(world, new Position(0.5, 8, 0.5));
        wolf.applyLeashRows(true, java.util.Map.of());
        assertTrue(wolf.isLeashed());
        assertTrue(wolf.goalFollowOwnerCanUse(false));
        wolf.goalFollowOwnerTick();
        assertEquals(0.5, wolf.position().x(), 1e-9, "the leashed body stays on its pillar");
        assertEquals(8.0, wolf.position().y(), 1e-9);
    }

    @Test
    void thePerchScanFindsTheUnwatchedChestAndTheCatSitsAtTheArrival() {
        TameableWorld world = new TameableWorld();
        world.blocks.put("4,7,0", "minecraft:chest"); // three cells east, the pet's plane
        world.blocks.put("4,8,0", "minecraft:air");   // the perch's air above
        MobEntity cat = tamedCat(world, new Position(0.5, 8, 0.5));

        assertTrue(cat.goalSitOnBlockCanUse(), "the cadence's first consult scans at once");
        assertFalse(cat.wolfSitting(), "the walk keeps the body standing");

        // The walk arm: three blocks out, the approach clock counts up and
        // the body stands (the reference's own walking write).
        cat.goalSitOnBlockTick();
        assertFalse(cat.wolfSitting());

        // The arrival: the body at the perch's up-center sits once.
        MobEntity perched = tamedCat(world, new Position(4.5, 8, 0.5));
        perched.goalSitOnBlockCanUse();
        perched.goalSitOnBlockTick();
        assertTrue(perched.wolfSitting(), "the standing cell sits the body");
    }

    @Test
    void theWatchedChestTheBedHeadAndTheOccupiedCellAreNoPerch() {
        TameableWorld world = new TameableWorld();
        world.blocks.put("4,7,0", "minecraft:chest");
        world.blocks.put("4,8,0", "minecraft:air");
        world.viewers.put("4,7,0", 1); // the chest someone watches
        world.blocks.put("6,7,1", "minecraft:bed_head"); // the head half
        world.blocks.put("6,8,1", "minecraft:air");
        world.blocks.put("6,7,-1", "minecraft:bed"); // the foot half
        world.blocks.put("6,8,-1", "minecraft:air");
        world.blocks.put("2,7,3", "minecraft:furnace"); // cold furnace
        world.blocks.put("2,8,3", "minecraft:air");
        world.blocks.put("2,7,-3", "minecraft:furnace_lit"); // the lit one
        world.blocks.put("2,8,-3", "minecraft:air");
        MobEntity cat = tamedCat(world, new Position(0.5, 8, 0.5));

        // The first valid perch on the spiral is the lit furnace — the
        // watched chest, the bed head, and the cold furnace all refuse.
        assertTrue(cat.goalSitOnBlockCanUse());
        cat.goalSitOnBlockTick();
        assertTrue(cat.wolfSitting() || cat.mode() == MobEntity.Mode.WANDER,
                "the walk or the perch owns the body");
        // The foot-half bed and the lit furnace ARE perches (the scan found
        // one of them); a world holding only the refused cells never opens.
        TameableWorld refused = new TameableWorld();
        refused.blocks.put("4,7,0", "minecraft:chest");
        refused.blocks.put("4,8,0", "minecraft:air");
        refused.viewers.put("4,7,0", 1);
        refused.blocks.put("3,7,2", "minecraft:bed_head");
        refused.blocks.put("3,8,2", "minecraft:air");
        MobEntity other = tamedCat(refused, new Position(0.5, 8, 0.5));
        assertFalse(other.goalSitOnBlockCanUse(), "no valid perch: the goal never opens");
    }

    @Test
    void theBrokenPerchStandsTheSittingBodyDown() {
        TameableWorld world = new TameableWorld();
        world.blocks.put("1,7,0", "minecraft:chest");
        world.blocks.put("1,8,0", "minecraft:air");
        MobEntity cat = tamedCat(world, new Position(0.5, 8, 0.5));
        assertTrue(cat.goalSitOnBlockCanUse());

        // The perch breaks (the chest leaves the world): shouldContinue
        // fails and the reference's stop() stands the body down.
        world.blocks.remove("1,7,0");
        assertFalse(cat.goalSitOnBlockCanUse());
        assertFalse(cat.wolfSitting(), "stop(): setSitting(false)");
    }

    @Test
    void theUntamedCatHuntsTheChickenAndTheTamedOneDoesNot() {
        TameableWorld world = new TameableWorld();
        MobEntity chicken = new MobEntity(700, MobType.CHICKEN, new Position(5.5, 8, 0.5),
                new Random(2L), new TameableWorld());
        world.nearbyMob = chicken;
        world.nearbyMobKind = MobType.CHICKEN;

        MobEntity wild = new MobEntity(601, MobType.OCELOT, new Position(0.5, 8, 0.5),
                new Random(5L), world);
        MobEntity found = null;
        for (int i = 0; i < 200 && found == null; i++) {
            found = wild.tickMobTargeting(); // the 1-in-10 cadence scan
        }
        assertNotNull(found, "the 1-in-10 scan lands the chicken within 200 ticks");
        assertEquals(chicken, found);

        MobEntity tamed = tamedCat(world, new Position(0.5, 8, 0.5));
        assertNull(tamed.tickMobTargeting(), "the tamed cat never hunts");
    }

    @Test
    void thePounceLeapsFromTheBandAndHoldsTheFlight() {
        TameableWorld world = new TameableWorld();
        MobEntity wolf = tamedWolf(world, new Position(0.5, 8, 0.5));
        wolf.tick(); // the physics settles the body onto the floor
        assertTrue(wolf.onGround(), "grounded before the leap");

        // Out of band: 1.5 blocks (squared 2.25 < 4) never leaps.
        assertFalse(wolf.tickPounceAtTarget(new Position(2.0, 8, 0.5)),
                "inside the melee band: no leap");
        // Out of band: 5 blocks (squared 25 > 16) never leaps.
        assertFalse(wolf.tickPounceAtTarget(new Position(5.5, 8, 0.5)),
                "beyond the pounce band: no leap");

        // In band (3 blocks, squared 9): the 1-in-5 roll lands within 50
        // consults, and the leap writes the reference's own impulse —
        // the wolf's 0.4 vertical with the horizontal charge.
        boolean leapt = false;
        for (int i = 0; i < 50 && !leapt; i++) {
            leapt = wolf.tickPounceAtTarget(new Position(3.5, 8, 0.5));
        }
        assertTrue(leapt, "the 1-in-5 roll fires inside the band");
        assertEquals(0.4f, (float) wolf.velocityY(), 1e-6f,
                "the wolf's own vertical impulse (the reference's float)");
        assertFalse(wolf.onGround(), "the leap lifts the body off the ground");
        assertTrue(wolf.velocityX() > 0.0, "the horizontal charge rides the bearing");

        // The airborne window holds the movement until the landing.
        assertTrue(wolf.tickPounceAtTarget(new Position(3.5, 8, 0.5)),
                "the flight holds the walk");
        for (int i = 0; i < 40 && !wolf.onGround(); i++) {
            wolf.tick(); // the physics lands the body
        }
        assertTrue(wolf.onGround(), "landed");
        assertFalse(wolf.tickPounceAtTarget(new Position(3.5, 8, 0.5)),
                "the landing ends the window: the chase resumes");

        // The ocelot's own impulse is the reference's 0.3F.
        MobEntity cat = tamedCat(world, new Position(0.5, 8, 0.5));
        cat.tick();
        boolean catLeapt = false;
        for (int i = 0; i < 50 && !catLeapt; i++) {
            catLeapt = cat.tickPounceAtTarget(new Position(3.5, 8, 0.5));
        }
        assertTrue(catLeapt);
        assertEquals(0.3f, (float) cat.velocityY(), 1e-6f,
                "the cat's own vertical impulse (the reference's float)");
    }
}
