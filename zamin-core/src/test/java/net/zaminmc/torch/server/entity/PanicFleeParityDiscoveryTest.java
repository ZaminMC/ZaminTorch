package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-15 — the panic/flee TARGET SELECTION over the Phase E boundary
 * arms, the reference's two goal families:
 *
 * <p><b>EscapeDangerGoal</b> (the hurt panic, lines 14-49): the CENSUS —
 * only the chicken (1.4), the sheep and the pig (1.25), the horse family
 * (1.2), the cow and the mooshroom (2.0) register the goal; a hurt wolf,
 * villager, rabbit, squid or bat walks on. The GATE —
 * {@code getAttacker() == null && !isOnFire()} answers false: the plain
 * environmental hurt (the fall, the cactus) never panics, the
 * attacker-carrying wound arms a 100-tick window (setAttacker's own
 * clear, LivingEntity lines 265-269), the burning body panics alone. The
 * TARGET — TargetFinder's {@code getTarget(mob, 5, 4)}: ten candidates
 * in the ±5/±4/±5 band, the highest favor wins, the feet walk the picked
 * cell's PATH (never a random bearing).</p>
 *
 * <p><b>FleeEntityGoal</b> (lines 34-116): the grown-box census
 * ({@code getShape().grown(range, 3.0, range)}), the FIRST body in the
 * gather's own order (list.get(0), never the nearest), the visibility
 * ray, the away-half-plane pick ({@code getTargetAwayFromEntity(mob, 16,
 * 7, threat)} — every candidate that moves back toward the threat is
 * rejected), the farther-check, the findPath + isOnTarget pair, and the
 * 49-squared speed band. The five registration sites: the ocelot from
 * players (16.0F, 0.8/1.33), the rabbit from wolves (16.0F, 1.33/1.33),
 * the villager from zombies (8.0F, 0.6/0.6 — the engine's missing
 * family), the skeleton from wolves (6.0F, 1.0/1.2), the creeper from
 * ocelots (6.0F, 1.0/1.2 — the famous cat-scares-creeper, yielding to
 * the ignite's own band-2 claim).</p>
 *
 * <p>The pre-fix engine: every non-hostile hurt panicked at a flat 1.8×
 * over a RANDOM bearing (no census, no gate, no target, wrong speed),
 * and every flee site steered DIRECTLY AWAY from the NEAREST threat in
 * range (no box law, no first-in-list, no half-plane, no path) — with
 * the villager and creeper families absent whole. Seven arms RED
 * pre-fix; the far controls green by design.</p>
 */
class PanicFleeParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The platform's surface level (the bodies stand at y = 40). */
    private static final int FLOOR_Y = 39;

    /** The working area's center column. */
    private static final int CENTER_X = 20;
    private static final int CENTER_Z = 20;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "panicflee" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        EngineAwaits.awaitCondition(server.world(),
                () -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                200, name + " playing");
        return session;
    }

    /**
     * The open platform: a 45x45 stone floor at y = 38/39 with air above
     * through y = 48 — every flee path and every panic walk runs un-
     * walled, the visibility rays stay clear.
     */
    private void buildPlatform() throws InterruptedException {
        server.ticker().submit(() -> {
            for (int x = 2; x <= 44; x++) {
                for (int z = 2; z <= 44; z++) {
                    server.world().setBlock(new BlockPosition(x, 38, z),
                            BuiltinBlocks.STONE);
                    server.world().setBlock(new BlockPosition(x, 39, z),
                            BuiltinBlocks.STONE);
                    for (int y = 40; y <= 48; y++) {
                        server.world().setBlock(new BlockPosition(x, y, z),
                                BuiltinBlocks.AIR);
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(CENTER_X, FLOOR_Y, CENTER_Z))),
                200, "the platform stands");
    }

    private MobEntity spawn(MobType type, Position at) throws InterruptedException {
        AtomicReference<MobEntity> spawned = new AtomicReference<>();
        server.ticker().submit(() -> spawned.set(
                server.mobs().spawnAt(type, at, 0)));
        EngineAwaits.awaitCondition(server.world(),
                () -> spawned.get() != null, 100, "the " + type + " spawned");
        return spawned.get();
    }

    /** A static body (no mind): the geometry the flee seats read stays
     * pinned — the threat never wanders out of the grown box mid-flight. */
    private MobEntity spawnStatic(MobType type, Position at) throws InterruptedException {
        AtomicReference<MobEntity> spawned = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity mob = server.mobs().spawnAt(type, at, 0);
            if (mob != null) {
                mob.setNoAi(true);
            }
            spawned.set(mob);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> spawned.get() != null, 100, "the static " + type + " spawned");
        return spawned.get();
    }

    /** The player's landed melee on the mob (the attacker-carrying wound). */
    private void melee(PlayerSession attacker, MobEntity victim) throws InterruptedException {
        Position at = victim.position();
        server.teleportPlayer(attacker, new Position(at.x(), at.y(), at.z() + 0.8));
        EngineAwaits.awaitCondition(server.world(),
                () -> attacker.position().z() == at.z() + 0.8, 100, "the stand landed");
        float before = victim.health();
        server.attackEntity(attacker, victim.entityId());
        EngineAwaits.awaitCondition(server.world(),
                () -> victim.health() < before, 100, "the swing landed");
    }

    /** Ticks the world forward through the harness's own clock. */
    private void tick(int ticks) throws InterruptedException {
        EngineAwaits.awaitTicks(server.world(), ticks, "the window");
    }

    /** Whether the mode stayed out of PANIC the whole window. */
    private static boolean neverPanicked(MobEntity mob) {
        return mob.mode() != MobEntity.Mode.PANIC;
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE PANIC CENSUS: a wounded wolf never panics, a wounded
    // cow does
    // ------------------------------------------------------------------

    /**
     * The reference's registration table: the wolf registers NO
     * EscapeDangerGoal (WolfEntity's own goal set — the hurt body turns
     * to fight through the revenge arm), the cow registers it at 2.0
     * (CowEntity line 28). Pre-fix RED: every non-hostile hurt panicked,
     * the wolf included.
     */
    @Test
    void theWoundedWolfNeverPanicsAndTheCowDoes() throws Exception {
        buildPlatform();
        PlayerSession attacker = join("Hunter");

        MobEntity wolf = spawn(MobType.WOLF,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));
        MobEntity cow = spawn(MobType.COW,
                new Position(CENTER_X + 8.5, 40.0, CENTER_Z + 8.5));

        melee(attacker, wolf);
        tick(60);
        assertTrue(neverPanicked(wolf),
                "the wounded wolf never panics (no EscapeDangerGoal registered)");

        melee(attacker, cow);
        try {
            EngineAwaits.awaitCondition(server.world(),
                    () -> cow.mode() == MobEntity.Mode.PANIC, 100,
                    "the wounded cow panics (the census's own member)");
        } catch (AssertionError missed) {
            // Runs #686's + the full-lane load flake's first-occurrence
            // read (the same convention the furnace and hunger seats
            // carry): the engine COMPLETED its hundred ticks (the
            // tick-deadline verdict, never the stall's or the patience
            // cap's) and the cow still never claimed the panic — the
            // next landing dumps the body's own live state so the miss
            // speaks: the wound's recency window, the mode the mind
            // carried, and the goal layer's own claim.
            throw new AssertionError(missed.getMessage()
                    + " [state: health=" + cow.health()
                    + " mode=" + cow.mode()
                    + " walkMult=" + cow.goalWalkMultiplier()
                    + " attackerAt=" + attacker.position()
                    + " wolfMode=" + wolf.mode() + "]", missed);
        }
    }

    // ------------------------------------------------------------------
    // Seat 1b — THE CLAIM'S OWN START WALK (the poisoned-ride repair)
    // ------------------------------------------------------------------

    /**
     * EscapeDangerGoal.start() line 42 (the 1:1 law the CI forensics
     * surfaced): the claim's moveTo runs UNCONDITIONALLY — the goal's own
     * route REPLACES whatever the last goal walked. The pre-fix engine
     * gated the moveTo on the navigation's isDone, so a panic claim
     * adopted over a LIVE stroll route rode the STROLL's path at the
     * panic's 2.0 speed with the mode write never landing — the poisoned
     * ride (walkMult 2.0 + mode WANDER, the wounded-cow seat's own
     * first-occurrence dump). The seat wounds the cow MID-STROLL (a live
     * route under the body) and pins the claim's own start: the mode
     * flips with the claim's first tick, never the adopted stroll's.
     */
    @Test
    void theClaimOverALiveStrollRouteRunsItsOwnStartWalk() throws Exception {
        buildPlatform();
        PlayerSession attacker = join("Hunter");
        MobEntity cow = spawn(MobType.COW,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));

        // The player NEAR (the stroll's own gate: a body 100+ ticks
        // without a player within 32 blocks never strolls — the despawn
        // clock's own pre-despawn band).
        server.teleportPlayer(attacker,
                new Position(CENTER_X + 3.5, 40.0, CENTER_Z + 0.5));
        EngineAwaits.awaitCondition(server.world(),
                () -> attacker.position().x() == CENTER_X + 3.5, 100,
                "the attacker stands near the cow");

        // The stroll claim LIVE: a WANDER body walking a route — the
        // navigation holds the STROLL's path when the wound lands (the
        // stroll route's own life at the 1.0 band is hundreds of ticks —
        // the wound lands deep inside it).
        EngineAwaits.awaitCondition(server.world(),
                () -> cow.mode() == MobEntity.Mode.WANDER
                        && cow.goalHasPathHeading(),
                600, "the cow claims a stroll with a live route");
        melee(attacker, cow);

        // THE CLAIM: whatever the navigation held, start() owes its OWN
        // route — the mode write lands with the claim's first tick (the
        // stroll's 1.0 never reads as the panic's 2.0, so the 2.0 write
        // names the panic claim alone).
        EngineAwaits.awaitCondition(server.world(),
                () -> cow.goalWalkMultiplier() == 2.0, 100,
                "the panic claims (the per-kind 2.0 speed writes)");
        assertTrue(cow.mode() == MobEntity.Mode.PANIC,
                "the claim writes the PANIC mode at its own start (start() "
                        + "line 42 — the unconditional moveTo, never the "
                        + "adopted stroll route; mode read " + cow.mode() + ")");
        assertTrue(cow.panicTargetForTest() != null,
                "the claim carries its own picked cell");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE GATE: the environmental hurt never panics
    // ------------------------------------------------------------------

    /**
     * EscapeDangerGoal.canStart's gate (lines 20-24): a hurt with no
     * attacker and no fire never starts — vanilla's panicked cow is a
     * WOUNDED cow, never a fallen one. Pre-fix RED: any hurt armed the
     * panic whole.
     */
    @Test
    void theEnvironmentalHurtNeverPanics() throws Exception {
        buildPlatform();
        MobEntity cow = spawn(MobType.COW,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));

        server.ticker().submit(() -> cow.hurt(3.0f)); // the bare walk: no attacker
        EngineAwaits.awaitCondition(server.world(),
                () -> cow.health() < 10.0f, 100, "the fall wound landed");
        tick(80);
        assertTrue(neverPanicked(cow),
                "the environmental hurt (no attacker, no fire) never panics");
        assertTrue(cow.goalHasPathHeading() == false || cow.mode() != MobEntity.Mode.PANIC,
                "no panic claim ever armed a walk");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE FIRE ARM: a burning body panics with no wound at all
    // ------------------------------------------------------------------

    /**
     * canStart's second gate ({@code !isOnFire()}): a burning cow panics
     * between burn ticks — the fire arm alone. Green in both engines for
     * the panic itself (the pre-fix burn damage armed it too), pinned as
     * the law's own control.
     */
    @Test
    void theBurningBodyPanicsWithoutAWound() throws Exception {
        buildPlatform();
        MobEntity cow = spawn(MobType.COW,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));

        server.ticker().submit(() -> cow.setFireTicks(200));
        EngineAwaits.awaitCondition(server.world(),
                () -> cow.mode() == MobEntity.Mode.PANIC, 200,
                "the burning body panics (the fire arm, no wound needed)");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE TARGET: the panic walks a PATH to a TargetFinder cell
    // inside the ±5/±4 band
    // ------------------------------------------------------------------

    /**
     * EscapeDangerGoal's start (lines 36-38): {@code getTarget(mob, 5,
     * 4)} — ten candidates in the ±5/±4/±5 band around the hurt cell,
     * the highest favor wins — then {@code navigation.moveTo} walks the
     * picked cell's path. Pre-fix RED twice over: the panic never armed a
     * navigation path (the random-bearing walk), and no target cell
     * existed to read.
     */
    @Test
    void thePanicWalksAPathToABandCell() throws Exception {
        buildPlatform();
        PlayerSession attacker = join("Hunter");
        MobEntity cow = spawn(MobType.COW,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));

        melee(attacker, cow);
        try {
            EngineAwaits.awaitCondition(server.world(),
                    () -> cow.mode() == MobEntity.Mode.PANIC, 100, "the panic claimed");
        } catch (AssertionError missed) {
            // The first-occurrence convention (the wounded-wolf seat's own
            // shape): the engine COMPLETED its hundred ticks and the cow
            // never claimed the panic — the dump names the live arm: the
            // wound's truth, the mode, the walk multiplier the last goal
            // tick wrote, the panic's own picked cell (a null says every
            // moveTo yielded — the no-route arm), and the path heading.
            throw new AssertionError(missed.getMessage()
                    + " [state: health=" + cow.health()
                    + " mode=" + cow.mode()
                    + " walkMult=" + cow.goalWalkMultiplier()
                    + " panicTarget=" + cow.panicTargetForTest()
                    + " pathHeading=" + cow.goalHasPathHeading()
                    + " attackerAt=" + attacker.position() + "]", missed);
        }
        assertTrue(cow.goalHasPathHeading(),
                "the panic walks a navigation path (never a random bearing)");

        // The picked cell's band: ±5 horizontal, ±4 vertical around the
        // wound's own cell. The claim stood at the await's settle (the
        // mode write rides the claim's own first tick), so the pick is
        // live; a spent-claim gap re-arms through one canUse pass.
        AtomicReference<Position> target = new AtomicReference<>();
        server.ticker().submit(() -> {
            if (cow.panicTargetForTest() == null) {
                cow.goalPanicCanUse(); // the re-arm's pick (the selector's shape)
            }
            target.set(cow.panicTargetForTest());
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> target.get() != null, 100, "the pick stands");
        Position picked = target.get();
        // The band law: the candidates are floor(mob) ± the range — the
        // mob's own CELL is the anchor, not the .5 position (the pick's
        // cells reach [cell − 5, cell + 5]).
        assertTrue(Math.abs(picked.x() - CENTER_X) <= 5.0,
                "the picked cell inside the ±5 horizontal band of the wound cell");
        assertTrue(Math.abs(picked.y() - 40.0) <= 4.0,
                "the picked cell inside the ±4 vertical band");
        assertTrue(Math.abs(picked.z() - CENTER_Z) <= 5.0,
                "the picked cell inside the ±5 horizontal band of the wound cell");
    }

    // ------------------------------------------------------------------
    // Seat 5 — THE SPEED CENSUS: the cow's 2.0 against the sheep's 1.25
    // ------------------------------------------------------------------

    /**
     * The ctor registrations' own numbers (CowEntity line 28 vs
     * SheepEntity line 56): the panicked cow walks 1.6x the panicked
     * sheep's speed (2.0/1.25 over the same movement base). Pre-fix RED:
     * the flat 1.8 band made both speeds equal.
     */
    @Test
    void thePanickedCowOutrunsThePanickedSheep() throws Exception {
        buildPlatform();
        PlayerSession attacker = join("Hunter");
        MobEntity cow = spawn(MobType.COW,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));
        MobEntity sheep = spawn(MobType.SHEEP,
                new Position(CENTER_X - 10.5, 40.0, CENTER_Z - 10.5));

        // The registration table itself (the ctor numbers, line-exact).
        assertEquals(1.4, MobEntity.escapeDangerSpeed(MobType.CHICKEN), 1e-9);
        assertEquals(1.25, MobEntity.escapeDangerSpeed(MobType.SHEEP), 1e-9);
        assertEquals(1.25, MobEntity.escapeDangerSpeed(MobType.PIG), 1e-9);
        assertEquals(1.2, MobEntity.escapeDangerSpeed(MobType.HORSE), 1e-9);
        assertEquals(2.0, MobEntity.escapeDangerSpeed(MobType.COW), 1e-9);
        assertEquals(2.0, MobEntity.escapeDangerSpeed(MobType.MOOSHROOM), 1e-9);
        assertEquals(0.0, MobEntity.escapeDangerSpeed(MobType.WOLF), 1e-9);
        assertEquals(0.0, MobEntity.escapeDangerSpeed(MobType.VILLAGER), 1e-9);
        assertEquals(0.0, MobEntity.escapeDangerSpeed(MobType.RABBIT), 1e-9);
        assertEquals(0.0, MobEntity.escapeDangerSpeed(MobType.SQUID), 1e-9);

        melee(attacker, cow);
        EngineAwaits.awaitCondition(server.world(),
                () -> cow.mode() == MobEntity.Mode.PANIC, 100,
                "the cow's panic claimed");
        melee(attacker, sheep);
        EngineAwaits.awaitCondition(server.world(),
                () -> sheep.mode() == MobEntity.Mode.PANIC, 100,
                "the sheep's panic claimed");
        tick(12); // both wounds' knockback impulses die (0.546^n decay)
        // The walk RATE, not the net displacement (the panic's plain pick
        // re-targets a random cell every leg — the body turns around, the
        // net walk is a random walk): the fastest 5-tick straight window
        // over 50 ticks carries the leg's own speed.
        double[] cowX = new double[51];
        double[] cowZ = new double[51];
        double[] sheepX = new double[51];
        double[] sheepZ = new double[51];
        for (int i = 0; i <= 50; i++) {
            cowX[i] = cow.position().x();
            cowZ[i] = cow.position().z();
            sheepX[i] = sheep.position().x();
            sheepZ[i] = sheep.position().z();
            tick(1);
        }
        // The MEDIAN 5-tick window (the walk's own steady rate): the scan
        // skips the first 12 samples — the melee's knockback launch puts
        // the victim AIRBORNE (the landing drift outpaces the walk) — and
        // the median (never the max) keeps the read immune to the await's
        // occasional two-tick sample slips. The cow's steady leg is 0.035
        // x 2.0 x 5 = 0.35, the sheep's 0.035 x 1.25 x 5 = 0.219.
        java.util.List<Double> cowWindows = new java.util.ArrayList<>();
        java.util.List<Double> sheepWindows = new java.util.ArrayList<>();
        for (int i = 17; i <= 50; i++) {
            cowWindows.add(Math.hypot(cowX[i] - cowX[i - 5], cowZ[i] - cowZ[i - 5]));
            sheepWindows.add(Math.hypot(sheepX[i] - sheepX[i - 5],
                    sheepZ[i] - sheepZ[i - 5]));
        }
        java.util.Collections.sort(cowWindows);
        java.util.Collections.sort(sheepWindows);
        double cowMedian = cowWindows.get(cowWindows.size() / 2);
        double sheepMedian = sheepWindows.get(sheepWindows.size() / 2);
        assertTrue(cowMedian >= 0.333,
                "the cow's steady leg walks the 2.0 band (median window "
                        + cowMedian + ", the clean 0.35)");
        assertTrue(sheepMedian <= 0.25,
                "the sheep's steady leg walks the 1.25 band (median window "
                        + sheepMedian + ", the clean 0.219)");
        assertTrue(cowMedian > sheepMedian * 1.4,
                "the cow's 2.0 outruns the sheep's 1.25 (medians "
                        + cowMedian + " vs " + sheepMedian + ")");
    }

    // ------------------------------------------------------------------
    // Seat 6 — THE BOX LAW: the grazing wolf scares the rabbit
    // ------------------------------------------------------------------

    /**
     * FleeEntityGoal.canStart's census (lines 63-71): the gather box is
     * the rabbit's OWN shape grown 16 — [x ± 0.3 + 16] — and a wolf
     * whose OWN box grazes the face (its center 16.2 out, its box
     * [16.2 ± 0.3] overlapping the face at 16.3 by 0.1) is INSIDE the
     * gather. Pre-fix RED: the center-range consult (a plain 16.0
     * distance) refused 16.2 whole.
     */
    @Test
    void theGrazingWolfScaresTheRabbit() throws Exception {
        buildPlatform();
        MobEntity rabbit = spawn(MobType.RABBIT,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));
        spawnStatic(MobType.WOLF,
                new Position(CENTER_X + 16.7, 40.0, CENTER_Z + 0.5)); // 16.2 east

        EngineAwaits.awaitCondition(server.world(),
                () -> rabbit.mode() == MobEntity.Mode.PANIC, 100,
                "the grazing wolf (its own box inside the grown box) scares the rabbit");
    }

    /**
     * The far control: a wolf 17.2 out — its own box entirely beyond the
     * grown box's face — never scares. Green by design in both engines.
     */
    @Test
    void theFarWolfNeverScaresTheRabbit() throws Exception {
        buildPlatform();
        MobEntity rabbit = spawn(MobType.RABBIT,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));
        spawnStatic(MobType.WOLF,
                new Position(CENTER_X + 17.7, 40.0, CENTER_Z + 0.5)); // 17.2 east

        tick(80);
        assertTrue(neverPanicked(rabbit),
                "the far wolf (its own box beyond the grown face) never scares");
    }

    // ------------------------------------------------------------------
    // Seat 7 — THE FIRST-IN-LIST + THE AWAY HALF-PLANE: the rabbit flees
    // the FIRST wolf, even into the closer one
    // ------------------------------------------------------------------

    /**
     * list.get(0)'s own law (line 71) + the half-plane filter
     * (TargetFinder line 49): the FIRST wolf in the gather's order (the
     * engine's spawn order — spawned first, 10 blocks EAST) owns the
     * away-pick, whose half-plane rejects every candidate that moves
     * back toward IT — the rabbit runs WEST, straight into the closer
     * wolf standing 6 blocks west. Vanilla's own quirk. Pre-fix RED: the
     * NEAREST wolf (the west one) owned the steer, and the rabbit ran
     * EAST.
     */
    @Test
    void theRabbitFleesTheFirstWolfEvenIntoTheCloserOne() throws Exception {
        buildPlatform();
        MobEntity rabbit = spawn(MobType.RABBIT,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));
        // The FIRST spawn (the gather's own order): 10 blocks EAST, static.
        spawnStatic(MobType.WOLF, new Position(CENTER_X + 10.5, 40.0, CENTER_Z + 0.5));
        // The second spawn: 6 blocks WEST (closer, never the first), static.
        spawnStatic(MobType.WOLF, new Position(CENTER_X - 5.5, 40.0, CENTER_Z + 0.5));

        EngineAwaits.awaitCondition(server.world(),
                () -> rabbit.mode() == MobEntity.Mode.PANIC, 100, "the flee claimed");
        // The away half-plane against the PURELY-EAST first wolf: every
        // candidate that moves back toward IT is rejected — the flee's
        // first leg never walks east (yaw in [0, 180], the west half).
        AtomicReference<Float> firstLegYaw = new AtomicReference<>();
        server.ticker().submit(() -> firstLegYaw.set(rabbit.goalPathYaw()));
        EngineAwaits.awaitCondition(server.world(),
                () -> firstLegYaw.get() != null, 100, "the heading read");
        float yaw = firstLegYaw.get();
        assertTrue(Math.sin(Math.toRadians(yaw)) >= -1e-6,
                "the away half-plane's first leg never walks east (yaw "
                        + yaw + ", sin " + Math.sin(Math.toRadians(yaw)) + ")");
        tick(60);
        assertTrue(rabbit.position().x() <= CENTER_X + 1.0,
                "the rabbit NEVER runs east toward the FIRST wolf (it may run "
                        + "west into the closer one — vanilla's own quirk; stands at x="
                        + rabbit.position().x() + ")");
    }

    // ------------------------------------------------------------------
    // Seat 8 — THE VILLAGER'S ZOMBIE FLEE: the missing family, landed
    // ------------------------------------------------------------------

    /**
     * VillagerEntity line 309: {@code FleeEntityGoal<>(this,
     * ZombieEntity.class, 8.0F, 0.6, 0.6)} at goal band 1 — above every
     * other walk the villager carries. Pre-fix RED: the family was
     * absent whole — a zombie 4 blocks away never moved the villager at
     * all.
     */
    @Test
    void theVillagerFleesTheZombie() throws Exception {
        buildPlatform();
        MobEntity villager = spawn(MobType.VILLAGER,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));
        spawnStatic(MobType.ZOMBIE, new Position(CENTER_X + 4.5, 40.0, CENTER_Z + 0.5));

        EngineAwaits.awaitCondition(server.world(),
                () -> villager.mode() == MobEntity.Mode.PANIC, 100,
                "the zombie within 8 blocks sends the villager running");
        tick(60);
        assertTrue(villager.position().x() <= CENTER_X + 1.0,
                "the villager never runs east toward the zombie (the away "
                        + "half-plane; stands at x=" + villager.position().x() + ")");
    }

    /**
     * The far control: a zombie 12 blocks out (beyond the 8.0F scare
     * range's grown box) never scares the villager. Green by design.
     */
    @Test
    void theFarZombieNeverScaresTheVillager() throws Exception {
        buildPlatform();
        MobEntity villager = spawn(MobType.VILLAGER,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));
        spawnStatic(MobType.ZOMBIE, new Position(CENTER_X + 12.5, 40.0, CENTER_Z + 0.5));

        tick(80);
        assertTrue(neverPanicked(villager),
                "the far zombie (beyond the 8-block grown box) never scares");
    }

    // ------------------------------------------------------------------
    // Seat 9 — THE CREEPER'S CAT FLEE + THE IGNITE'S OWN CLAIM
    // ------------------------------------------------------------------

    /**
     * CreeperEntity line 35: {@code FleeEntityGoal<>(this,
     * OcelotEntity.class, 6.0F, 1.0, 1.2)} at goal band 3 — the famous
     * cat-scares-creeper. Pre-fix RED: the family was absent whole.
     */
    @Test
    void theCreeperFleesTheOcelot() throws Exception {
        buildPlatform();
        MobEntity creeper = spawn(MobType.CREEPER,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));
        spawnStatic(MobType.OCELOT, new Position(CENTER_X + 4.5, 40.0, CENTER_Z + 0.5));

        EngineAwaits.awaitCondition(server.world(),
                () -> creeper.mode() == MobEntity.Mode.PANIC, 100,
                "the ocelot within 6 blocks sends the creeper running");
        tick(60);
        assertTrue(creeper.position().x() <= CENTER_X + 1.0,
                "the creeper never runs east toward the cat (the away half-plane; "
                        + "stands at x=" + creeper.position().x() + ")");
    }

    /**
     * The ignite's own claim (CreeperIgniteGoal band 2 over the flee's
     * band 3 — the reference's mutex order): once the swell owns the
     * movement, the cat's presence never steals it — the flee's canUse
     * yields at the live fuse. The reverse order is the famous mechanic
     * itself: a creeper ALREADY fleeing a cat never stops to fuse (the
     * running flee's shouldContinue holds, the ignite never starts) —
     * that arm rides the flee seat above. Green by design for the fuse
     * itself; the seat pins the band order.
     */
    @Test
    void theIgniteClaimHoldsTheFuseOverTheCat() throws Exception {
        buildPlatform();
        PlayerSession victim = join("Victim");
        MobEntity creeper = spawn(MobType.CREEPER,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5));

        // The player INSIDE the prime band (1.8 horizontal) FIRST — no
        // cat in the box yet, the flee never armed: the swell claims.
        server.teleportPlayer(victim, new Position(CENTER_X + 1.5, 40.0, CENTER_Z + 0.5));
        EngineAwaits.awaitCondition(server.world(),
                () -> creeper.mode() == MobEntity.Mode.FUSE, 200,
                "the swell claims the body first (the ignite's band-2 order)");

        // The cat arrives — the live fuse outranks the flee's fresh claim.
        spawnStatic(MobType.OCELOT, new Position(CENTER_X - 4.5, 40.0, CENTER_Z + 0.5));
        tick(40);
        assertTrue(creeper.mode() == MobEntity.Mode.FUSE,
                "the live swell holds the movement over the cat (the band order)");
    }
}
