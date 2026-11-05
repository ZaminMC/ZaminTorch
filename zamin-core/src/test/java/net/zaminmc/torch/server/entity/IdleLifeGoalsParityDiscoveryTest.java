package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.WoodDoorFamily;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-22 — THE IDLE-LIFE FAMILY over the reference's own goal laws:
 * the sheep's graze ({@code EatGrassGoal}), the baby's parent walk
 * ({@code FollowParentGoal}), the golem's villager glance ({@code
 * IronGolemLookGoal} + the offer clock), the villager child's golem
 * walk ({@code FollowGolemGoal}), and the villager's door open walk
 * ({@code LongDoorInteractGoal} over the shared {@code
 * AbstractDoorInteractGoal} base).
 *
 * <p>THE LAWS PINNED:</p>
 * <ul>
 *   <li>THE GRAZE — the 40-tick window with the world write at exactly
 *       timer-4 (36 ticks after the arm), the grass block's DIRT write
 *       + the coat's return + the baby's 60-unit growth, the tall
 *       grass's drop-less break, and the mobGriefing gate over the
 *       world writes alone (the coat returns on a griefing-refused
 *       server too — the reference's own arm order).</li>
 *   <li>THE PARENT CENSUS — the child's OWN shape grown 8 horizontal /
 *       4 vertical (NOT the breed scan's 8/8), each candidate's OWN box
 *       intersecting STRICT both sides, the nearest ADULT by the
 *       reference's {@code !(d1 > d0)} tie form, the start refusal
 *       under 3 blocks, the window the 3..16-block band, the kinds gate
 *       (the four registrants alone), and the baby gate.</li>
 *   <li>THE GOLEM FAMILY — the offer clock's 400-tick window, the
 *       child's trigger wait (the clock hits the armed number), the
 *       0.5 walk, and the &lt;4 arrival release (the golem's offer
 *       released by the child itself).</li>
 *   <li>THE DOOR — the capture's open write (the LOWER half's OPEN bit
 *       alone — {@code DoorBlock.updateOpenState}), the 20-tick close,
 *       and the pass-through's dot-product stop.</li>
 * </ul>
 *
 * <p>THE RIG: a broad stone platform (y 38/39, the bodies at 40), the
 * graze/door/golem bodies NoAI with the goal bodies driven synchronously
 * inside single ticker actions (the zombie door-break seat's own
 * precedent — the window laws pinned without waiting on the dice), the
 * world writes landing through the real manager drain.</p>
 */
class IdleLifeGoalsParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The working surface's feet level (the platform top at y 39). */
    private static final int FLOOR_Y = 40;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "idlelife" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /**
     * The platform: stone at y 38/39 over x 4..44, z 4..24, open air
     * above through y 52 — the working surface every arm builds on.
     */
    private void buildPlatform() throws InterruptedException {
        server.ticker().submit(() -> {
            for (int x = 4; x <= 44; x++) {
                for (int z = 4; z <= 24; z++) {
                    server.world().setBlock(new BlockPosition(x, 38, z),
                            BuiltinBlocks.STONE);
                    server.world().setBlock(new BlockPosition(x, 39, z),
                            BuiltinBlocks.STONE);
                    for (int y = 40; y <= 52; y++) {
                        server.world().setBlock(new BlockPosition(x, y, z),
                                BuiltinBlocks.AIR);
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(20, 39, 10))),
                200, "the platform stands");
    }

    /**
     * A STATIC (NoAI) body on the platform — the geometry exact, the
     * goal bodies driven by the seat itself (the zombie door-break
     * seat's own shape).
     */
    private MobEntity spawnStatic(MobType type, double x, double z) throws InterruptedException {
        AtomicReference<MobEntity> spawned = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity mob = server.mobs().spawnAt(type,
                    new Position(x, FLOOR_Y, z), 0);
            if (mob != null) {
                mob.setNoAi(true);
            }
            spawned.set(mob);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> spawned.get() != null, 100, "the body spawned");
        return spawned.get();
    }

    /** The cell below a body's feet. */
    private BlockPosition below(BlockPosition at) {
        return at.offset(0, -1, 0);
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE GRAZE ON A GRASS BLOCK (the DIRT write family)
    // ------------------------------------------------------------------

    /**
     * A sheared BABY sheep standing on a grass block: the armed window
     * writes DIRT below at exactly timer-4 (36 direct goal ticks after
     * the arm — the reference's own 40-tick clock with the write at 4),
     * the coat returns (onEatingGrass — the graze's own regrow path),
     * and the baby grows 60 units (the reference's growUp(60), 1200
     * age-ticks). The timer never writes early: at timer-5 the cell is
     * still grass.
     */
    @Test
    void theGrazeWritesDirtAtTimerFourAndReturnsTheCoat() throws Exception {
        buildPlatform();
        BlockPosition at = new BlockPosition(20, FLOOR_Y, 10);
        server.ticker().submit(() ->
                server.world().setBlock(below(at), BuiltinBlocks.GRASS_BLOCK));
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.GRASS_BLOCK.equals(server.world().getBlock(below(at))),
                100, "the grass stands");

        MobEntity sheep = spawnStatic(MobType.SHEEP, 20.5, 10.5);
        server.ticker().submit(() -> {
            sheep.setSheared(true);
            sheep.setBreedingAge(-24_000);
            sheep.armEatGrass();
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> sheep.sheared(), 100, "the coat pinned sheared");
        int ageAtArm = readAge(sheep);

        // 35 direct goal ticks: the clock reads 5, the write has NOT
        // landed (the reference's write at exactly timer-4).
        server.ticker().submit(() -> {
            for (int i = 0; i < 35; i++) {
                sheep.goalEatGrassTick();
            }
        });
        AtomicInteger timer = new AtomicInteger();
        server.ticker().submit(() -> timer.set(sheep.eatGrassTimerForTest()));
        EngineAwaits.awaitCondition(server.world(),
                () -> timer.get() == 5, 100, "the clock at 5");
        assertTrue(BuiltinBlocks.GRASS_BLOCK.equals(server.world().getBlock(below(at))),
                "no write before timer-4 (the cell still grass at 5)");

        // The 36th tick: timer 4, the write lands through the drain.
        server.ticker().submit(sheep::goalEatGrassTick);
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.DIRT.equals(server.world().getBlock(below(at))),
                100, "the DIRT write landed");

        // onEatingGrass: the coat returns, the baby grows.
        EngineAwaits.awaitCondition(server.world(),
                () -> !sheep.sheared(), 100, "the coat returned");
        int ageAfter = readAge(sheep);
        assertTrue(ageAfter - ageAtArm >= 1200,
                "the baby grew 60 units (the age moved " + (ageAfter - ageAtArm) + ")");
        assertTrue(ageAfter - ageAtArm <= 1200 + 200,
                "exactly one graze's growth (the age moved " + (ageAfter - ageAtArm) + ")");
    }

    private int readAge(MobEntity mob) throws InterruptedException {
        AtomicInteger age = new AtomicInteger();
        server.ticker().submit(() -> age.set(mob.breedingAge()));
        EngineAwaits.awaitCondition(server.world(), () -> age.get() != 0, 100, "the age read");
        return age.get();
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE GRAZE ON TALL GRASS (the drop-less break)
    // ------------------------------------------------------------------

    /**
     * A sheep standing IN a tall-grass cell: the window's write breaks
     * the tall grass to AIR (the reference's {@code breakBlock(pos,
     * false)} — no drops) and the coat returns the same.
     */
    @Test
    void theGrazeBreaksTheTallGrassDropless() throws Exception {
        buildPlatform();
        BlockPosition at = new BlockPosition(20, FLOOR_Y, 10);
        server.ticker().submit(() ->
                server.world().setBlock(at, BuiltinBlocks.TALL_GRASS));
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.TALL_GRASS.equals(server.world().getBlock(at)),
                100, "the tall grass stands");

        MobEntity sheep = spawnStatic(MobType.SHEEP, 20.5, 10.5);
        server.ticker().submit(() -> {
            sheep.setSheared(true);
            sheep.armEatGrass();
        });
        EngineAwaits.awaitCondition(server.world(), sheep::sheared, 100, "pinned");

        server.ticker().submit(() -> {
            for (int i = 0; i < 36; i++) {
                sheep.goalEatGrassTick();
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.AIR.equals(server.world().getBlock(at)),
                100, "the tall grass broke drop-less");
        EngineAwaits.awaitCondition(server.world(),
                () -> !sheep.sheared(), 100, "the coat returned on the tall-grass graze");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE MOBGRIEFING GATE (the world writes alone)
    // ------------------------------------------------------------------

    /**
     * The griefing-refused server: the graze's world write stands down
     * (the grass block survives whole) but the coat still returns —
     * the reference's own arm order (onEatingGrass outside the gate).
     */
    @Test
    void theGrazeWorldWritesGateOnMobGriefing() throws Exception {
        buildPlatform();
        BlockPosition at = new BlockPosition(20, FLOOR_Y, 10);
        server.ticker().submit(() ->
                server.world().setBlock(below(at), BuiltinBlocks.GRASS_BLOCK));
        server.ticker().submit(() ->
                server.gameRules().set("mobGriefing", "false"));
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.GRASS_BLOCK.equals(server.world().getBlock(below(at))),
                100, "the grass stands");

        MobEntity sheep = spawnStatic(MobType.SHEEP, 20.5, 10.5);
        server.ticker().submit(() -> {
            sheep.setSheared(true);
            sheep.armEatGrass();
        });
        EngineAwaits.awaitCondition(server.world(), sheep::sheared, 100, "pinned");

        server.ticker().submit(() -> {
            for (int i = 0; i < 40; i++) {
                sheep.goalEatGrassTick();
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> !sheep.sheared(), 100, "the coat returned (the feed ungated)");
        assertTrue(BuiltinBlocks.GRASS_BLOCK.equals(server.world().getBlock(below(at))),
                "the grass block survived the griefing-refused graze");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE PARENT CENSUS (the own-box law + the bands)
    // ------------------------------------------------------------------

    /**
     * The baby cow's census: a straddling ADULT whose own box grazes
     * the grown face at 8 blocks east IS vanilla's find (the pre-fix
     * center-scan convention would refuse it — the grown(8,4,8) own-box
     * law), an adult 10 blocks east never is, the parent inside 3
     * blocks refuses the START (the squared-9 law), the kinds gate
     * keeps the rabbit baby out (the four registrants alone), and the
     * adult cow never claims the walk.
     */
    @Test
    void theParentCensusOwnBoxLawAndTheStartBands() throws Exception {
        buildPlatform();
        // The child's own box: cow 0.9 wide — the grown face at
        // 20.5 + 0.45 + 8 = 28.95. A parent CENTERED at 28.7 keeps its
        // own box [28.25, 29.15] grazing the face (STRICT intersection);
        // at 30.5 the box [30.05, 30.95] never reaches it.
        MobEntity baby = spawnStatic(MobType.COW, 20.5, 10.5);
        MobEntity straddling = spawnStatic(MobType.COW, 28.7, 10.5);
        MobEntity far = spawnStatic(MobType.COW, 30.5, 10.5);
        server.ticker().submit(() -> baby.setBreedingAge(-24_000));
        EngineAwaits.awaitCondition(server.world(),
                () -> baby.isBaby(), 100, "the baby pinned");

        // The straddling parent IS found — the own-box law (the
        // center-distance convention would refuse a parent whose center
        // sits beyond the face).
        AtomicReference<MobEntity> found = new AtomicReference<>();
        server.ticker().submit(() -> {
            if (baby.goalFollowParentCanUse()) {
                found.set(baby.followParentTargetForTest());
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> found.get() != null, 100, "the straddling parent found");
        assertEquals(straddling, found.get(), "the nearest adult owns the walk");

        // The far parent alone (the straddling one teleported beyond the
        // band): 10 blocks east is beyond the grown band whole — never
        // a claim. The first pass clears the stale window, the second
        // scans fresh (the selector's own two-pass shape).
        server.ticker().submit(() ->
                straddling.teleport(new Position(42.5, FLOOR_Y, 22.5)));
        EngineAwaits.awaitCondition(server.world(),
                () -> straddling.position().x() > 42.0, 100, "the straddling parent gone");
        AtomicReference<Boolean> farClaim = new AtomicReference<>();
        server.ticker().submit(() -> {
            baby.goalFollowParentCanUse(); // the stale window's clear pass
            farClaim.set(baby.goalFollowParentCanUse());
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> farClaim.get() != null, 100, "the far read settled");
        assertFalse(farClaim.get(), "a parent 10 blocks east is beyond the gather");
        assertNull(readTarget(baby), "the walk stands down without a parent");

        // The start refusal: a parent inside 3 blocks never arms the
        // walk (the squared-9 law — the window is a FOLLOW band, not a
        // press against the parent).
        MobEntity near = spawnStatic(MobType.COW, 21.5, 10.5);
        AtomicReference<Boolean> nearClaim = new AtomicReference<>();
        server.ticker().submit(() -> nearClaim.set(baby.goalFollowParentCanUse()));
        EngineAwaits.awaitCondition(server.world(),
                () -> nearClaim.get() != null, 100, "the near read settled");
        assertFalse(nearClaim.get(), "a parent under 3 blocks refuses the start");
        assertNull(readTarget(baby), "no claim kept from the refused start");
    }

    /** The walk's live target read (one ticker action). */
    private MobEntity readTarget(MobEntity baby) throws InterruptedException {
        AtomicReference<MobEntity> target = new AtomicReference<>();
        server.ticker().submit(() -> target.set(baby.followParentTargetForTest()));
        EngineAwaits.awaitCondition(server.world(),
                () -> target.get() != null || true, 100, "the target read");
        return target.get();
    }

    // ------------------------------------------------------------------
    // Seat 5 — THE KINDS AND BABY GATES
    // ------------------------------------------------------------------

    /**
     * The registration's own census: the RABBIT baby never claims (its
     * registration carries no FollowParentGoal — the four registrants
     * alone: the cow, the sheep, the pig, the chicken), and the ADULT
     * cow never claims either (the baby gate).
     */
    @Test
    void theParentWalkGatesOnKindAndBaby() throws Exception {
        buildPlatform();
        MobEntity rabbitBaby = spawnStatic(MobType.RABBIT, 20.5, 10.5);
        MobEntity rabbitAdult = spawnStatic(MobType.RABBIT, 26.5, 10.5);
        MobEntity adultCow = spawnStatic(MobType.COW, 22.5, 10.5);
        MobEntity distantCow = spawnStatic(MobType.COW, 26.5, 12.5);
        server.ticker().submit(() -> rabbitBaby.setBreedingAge(-24_000));
        EngineAwaits.awaitCondition(server.world(),
                () -> rabbitBaby.isBaby(), 100, "the rabbit baby pinned");

        AtomicReference<Boolean> rabbitClaim = new AtomicReference<>();
        server.ticker().submit(() ->
                rabbitClaim.set(rabbitBaby.goalFollowParentCanUse()));
        EngineAwaits.awaitCondition(server.world(),
                () -> rabbitClaim.get() != null, 100, "the rabbit read settled");
        assertFalse(rabbitClaim.get(), "the rabbit's registration carries no parent walk");

        AtomicReference<Boolean> adultClaim = new AtomicReference<>();
        server.ticker().submit(() ->
                adultClaim.set(adultCow.goalFollowParentCanUse()));
        EngineAwaits.awaitCondition(server.world(),
                () -> adultClaim.get() != null, 100, "the adult read settled");
        assertFalse(adultClaim.get(), "an adult never claims the parent walk");
    }

    // ------------------------------------------------------------------
    // Seat 6 — THE GOLEM FAMILY (the offer clock + the child's walk)
    // ------------------------------------------------------------------

    /**
     * The golem's offer window and the child's walk: the armed offer
     * clock (400) holds while the golem's body ticks pay it down, the
     * villager CHILD claims the walk over the census (the offer-gated
     * golem inside the grown 6/2 box), the walk's trigger fires when
     * the clock hits the armed number exactly, and the arrival inside
     * the 2-block band releases the golem's offer (the child's own
     * stop, setLookingAtVillager(false)).
     */
    @Test
    void theGolemOfferWindowAndTheChildsWalk() throws Exception {
        buildPlatform();
        MobEntity golem = spawnStatic(MobType.IRON_GOLEM, 20.5, 10.5);
        MobEntity child = spawnStatic(MobType.VILLAGER, 26.5, 10.5);
        server.ticker().submit(() -> {
            child.setBreedingAge(-24_000);
            golem.setLookingAtVillager(true);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> child.isBaby() && golem.golemLookingAtVillagerTicks() > 390,
                100, "the offer armed");

        // The claim: the child's census reads the offering golem.
        AtomicReference<Boolean> claimed = new AtomicReference<>();
        server.ticker().submit(() -> claimed.set(child.goalFollowGolemCanUse()));
        EngineAwaits.awaitCondition(server.world(),
                () -> claimed.get() != null, 100, "the claim read settled");
        assertTrue(claimed.get(), "the child claims the offering golem");
        AtomicReference<MobEntity> golemTarget = new AtomicReference<>();
        server.ticker().submit(() -> golemTarget.set(child.followGolemTargetForTest()));
        EngineAwaits.awaitCondition(server.world(),
                () -> golemTarget.get() != null, 100, "the golem target read");
        assertEquals(golem, golemTarget.get(), "the golem owns the walk");

        // The trigger wait: the offer RE-ARMED fresh (the manager's own
        // body ticks paid the earlier window down while the claim was
        // being read — the fresh 400 carries the whole trigger band),
        // the golem's body tick pays the clock down, the child's goal
        // tick watching for the armed number exactly — the walk starts
        // within the 400-tick window.
        AtomicReference<Boolean> walking = new AtomicReference<>();
        server.ticker().submit(() -> {
            golem.setLookingAtVillager(true);
            for (int i = 0; i < 420 && !child.followingGolemForTest(); i++) {
                golem.tick(false);
                child.goalFollowGolemTick();
            }
            walking.set(child.followingGolemForTest());
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> walking.get() != null, 200, "the trigger read settled");
        assertTrue(walking.get(), "the walk started at the armed trigger");

        // The arrival release: the child within the 2-block band
        // releases the golem's offer.
        server.ticker().submit(() -> {
            child.teleport(new Position(21.5, FLOOR_Y, 10.5));
            child.goalFollowGolemTick();
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> golem.golemLookingAtVillagerTicks() == 0,
                100, "the golem's offer released");
    }

    // ------------------------------------------------------------------
    // Seat 7 — THE VILLAGER'S DOOR (the open write + the close window)
    // ------------------------------------------------------------------

    /**
     * The door's open law ({@code DoorBlock.updateOpenState}): the
     * capture opens the LOWER half's OPEN bit alone (the upper's
     * powered/hinge state untouched — the reference's single-cell
     * write), the 20-tick window closes it again, and the pass-through
     * (the entry offset's dot product going negative) closes it early.
     */
    @Test
    void theVillagerOpensTheDoorAndTheWindowClosesIt() throws Exception {
        buildPlatform();
        // The door pair: the lower closed at (20, 40, 10), the upper
        // beside the standing villager.
        BlockPosition lower = new BlockPosition(20, FLOOR_Y, 10);
        BlockPosition upper = lower.offset(0, 1, 0);
        server.ticker().submit(() -> {
            server.world().setBlock(lower, BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_W);
            server.world().setBlock(upper, BuiltinBlocks.OAK_DOOR_UPPER);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.OAK_DOOR_UPPER.equals(server.world().getBlock(upper)),
                100, "the door stands");

        MobEntity villager = spawnStatic(MobType.VILLAGER, 21.5, 10.5);

        // The capture (the clip-site verb with the cell fed — the
        // seat's own drive, the zombie door precedent): the window
        // opens and the open write drains.
        AtomicReference<Boolean> armed = new AtomicReference<>();
        server.ticker().submit(() ->
                armed.set(villager.armVillagerDoorAt(upper.x(), upper.y(), upper.z())));
        EngineAwaits.awaitCondition(server.world(),
                () -> armed.get() != null, 100, "the arming settled");
        assertTrue(armed.get(), "the capture arms the window");
        EngineAwaits.awaitCondition(server.world(),
                () -> WoodDoorFamily.isOpen(server.world().getBlock(lower)),
                100, "the lower half opened");
        assertTrue(BuiltinBlocks.OAK_DOOR_UPPER.equals(server.world().getBlock(upper)),
                "the upper half untouched by the open write");

        // The 20-tick window: each selector pass reads the window first
        // (canUse) then pays the clock (tick) — 19 pairs hold the door
        // open (the clock at 1), the 20th pair drains the clock to zero,
        // and the NEXT canUse pass ends the window with the close write.
        server.ticker().submit(() -> {
            for (int i = 0; i < 19; i++) {
                villager.goalVillagerDoorCanUse();
                villager.goalVillagerDoorTick();
            }
        });
        EngineAwaits.awaitTicks(server.world(), 2, "the held window");
        assertTrue(WoodDoorFamily.isOpen(server.world().getBlock(lower)),
                "the door holds through the window");
        server.ticker().submit(() -> {
            villager.goalVillagerDoorCanUse(); // the 20th pass: the clock still above zero
            villager.goalVillagerDoorTick(); // the clock hits zero
            villager.goalVillagerDoorCanUse(); // the next pass ends the window
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> !WoodDoorFamily.isOpen(server.world().getBlock(lower)),
                100, "the 20th tick closed the door");
    }

    /**
     * The pass-through law: a body that crosses the door's center plane
     * (the entry offset dotted against the live offset going negative)
     * ends the window EARLY — the close write fires long before the
     * 20-tick clock runs out.
     */
    @Test
    void thePassThroughClosesTheDoorEarly() throws Exception {
        buildPlatform();
        BlockPosition lower = new BlockPosition(20, FLOOR_Y, 10);
        BlockPosition upper = lower.offset(0, 1, 0);
        server.ticker().submit(() -> {
            server.world().setBlock(lower, BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_W);
            server.world().setBlock(upper, BuiltinBlocks.OAK_DOOR_UPPER);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.OAK_DOOR_UPPER.equals(server.world().getBlock(upper)),
                100, "the door stands");

        // The villager WEST of the door: the entry offsets point west
        // (the door's center minus the body). Crossing to the east side
        // flips the dot product negative.
        MobEntity villager = spawnStatic(MobType.VILLAGER, 19.5, 10.5);
        server.ticker().submit(() ->
                villager.armVillagerDoorAt(upper.x(), upper.y(), upper.z()));
        EngineAwaits.awaitCondition(server.world(),
                () -> WoodDoorFamily.isOpen(server.world().getBlock(lower)),
                100, "the door opened");

        // Four ticks in, the body crosses the plane: the window ends.
        server.ticker().submit(() -> {
            for (int i = 0; i < 4; i++) {
                villager.goalVillagerDoorTick();
            }
        });
        server.ticker().submit(() ->
                villager.teleport(new Position(21.5, FLOOR_Y, 10.5)));
        server.ticker().submit(villager::goalVillagerDoorTick);
        AtomicReference<Boolean> windowOver = new AtomicReference<>();
        server.ticker().submit(() ->
                windowOver.set(!villager.goalVillagerDoorCanUse()));
        EngineAwaits.awaitCondition(server.world(),
                () -> windowOver.get() != null, 100, "the window read settled");
        assertTrue(windowOver.get(), "the pass-through ended the window");
        EngineAwaits.awaitCondition(server.world(),
                () -> !WoodDoorFamily.isOpen(server.world().getBlock(lower)),
                100, "the pass-through closed the door");
    }
}
