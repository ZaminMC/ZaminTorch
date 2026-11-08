package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave JC2 — THE JOCKEY CHASE HAND-OFF (ZombieEntity.mobTick lines
 * 184-187: {@code if (this.isRiding() && this.getAttackTarget() != null
 * && this.vehicle instanceof ChickenEntity) ((MobEntity)this.vehicle)
 * .getNavigation().moveAlong(this.getNavigation().getPath(), 1.5)} — the
 * famous jockey chase, the architecture's own named open row from the CJ
 * session): the mounted baby zombie with a live target hands ITS OWN
 * navigation route to the CHICKEN's navigation at the hand-off's own 1.5,
 * every tick — the LAST WRITER of the mount's navigation.
 *
 * <p>THE LAWS PINNED:</p>
 * <ul>
 *   <li>THE HAND-OFF + THE MOUNT-SIDE DRIVE — the chicken walks the
 *       ZOMBIE'S route toward the target (the steer flag armed through
 *       the window, the pair closing on the player): the rider-side
 *       mount stamp (the reference's {@code this.vehicle} row — the
 *       engine's per-tick both-sides stamp), the cross-body moveAlong,
 *       and the body walk's handed-route band (the reference's
 *       unconditional navigation tick + the movement control's
 *       {@code setSpeed(nav.speed x attribute)} law) all ride this seat.
 *       The pre-fix engine: the jockey chicken's mind PARKED under the
 *       ridden-body gate (the pre-JC shape — every ridden body parked),
 *       the pair never moving on any target.</li>
 *   <li>THE SPEED + THE SHARED ROUTE — the chicken's closing slope is
 *       its own walk base times the hand-off's literal 1.5 (0.035 x 1.5
 *       = 0.0525 blocks per tick — never the stroll band's 1.0, never
 *       the panic's 1.4), and the mount's navigation holds the RIDER'S
 *       OWN route object (the reference's shared-Path shape: both
 *       navigations advance the one walking index).</li>
 *   <li>THE BITE — the zombie strikes FROM THE SEAT: the pair closes
 *       through the drive and the zombie's melee reach reads its seat
 *       position (the anchor 0.1 forward of the chicken's yaw at half
 *       its height) — the jockey's own kill, the player's health
 *       dropping while the zombie rides.</li>
 *   <li>THE TAIL — when the hunt ends (the target beyond the follow
 *       range), the handed route STANDS (no own write took the
 *       navigation) and the drive walks it to its end — the reference's
 *       movement control walking the standing path until isDone, the
 *       mount finishing the chase the rider abandoned.</li>
 * </ul>
 *
 * <p>THE RIG: the flat world's open plain (the feet at y 8), a stone
 * ROOF corridor above the pair's whole route (the daylight burn never
 * arms under it — the zombie hunts the whole window), the baby zombie
 * (the jockey's own rider) seated on a jockey-marked chicken, the
 * survival player twelve blocks east on the route's line.</p>
 */
class ChickenJockeyChaseHandoffDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The pair's standing cell (the route runs east along z = 10). */
    private static final double PAIR_X = 20.5;
    private static final double ROUTE_Z = 10.5;
    /** The player's cell — twelve blocks east, on the route's line. */
    private static final double PLAYER_X = 32.5;
    /** The roof corridor's span (the burn shade over the whole route). */
    private static final int ROOF_Y = 12;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "jc2" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /**
     * The roof corridor: stone at y 12 over x 18..34, z 9..12 — the
     * burn consult's sky read blocked above every cell the pair walks
     * (the zombie hunts the whole window whatever the clock), the
     * horizontal eye ray clear below it.
     */
    private void buildRoof() throws InterruptedException {
        server.ticker().submit(() -> {
            for (int x = 18; x <= 34; x++) {
                for (int z = 9; z <= 12; z++) {
                    server.world().setBlock(
                            new net.zaminmc.torch.block.BlockPosition(x, ROOF_Y, z),
                            net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> net.zaminmc.torch.server.block.BuiltinBlocks.STONE.equals(
                        server.world().getBlock(
                                new net.zaminmc.torch.block.BlockPosition(
                                        (int) PAIR_X, ROOF_Y, (int) ROUTE_Z))),
                200, "the roof stands");
    }

    private PlayerSession joinPlayer(String name, double x, double z) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        EngineAwaits.awaitCondition(server.world(),
                () -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                200, name + " playing");
        server.teleportPlayer(session, new Position(x, 5.0, z));
        EngineAwaits.awaitCondition(server.world(),
                () -> session.position().y() == 5.0, 200, name + " standing");
        return session;
    }

    /** The jockey pair: the baby zombie seated on the jockey-marked chicken. */
    private MobEntity[] spawnPair() throws InterruptedException {
        AtomicReference<MobEntity> chickenRef = new AtomicReference<>();
        AtomicReference<MobEntity> zombieRef = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity chicken = server.mobs().spawnAt(MobType.CHICKEN,
                    new Position(PAIR_X, 8.0, ROUTE_Z));
            MobEntity zombie = server.mobs().spawnAt(MobType.ZOMBIE,
                    new Position(PAIR_X, 8.0, ROUTE_Z));
            if (chicken != null && zombie != null) {
                zombie.setBreedingAge(-24_000); // the baby — the jockey's rider
                chicken.setChickenJockey(true);
                chicken.setRider(zombie.entityId());
                chickenRef.set(chicken);
                zombieRef.set(zombie);
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> chickenRef.get() != null && zombieRef.get() != null,
                200, "the pair spawned");
        // The stamp + the seat anchor land on the next ticks.
        EngineAwaits.awaitTicks(server.world(), 3, "the seat anchors");
        MobEntity chicken = chickenRef.get();
        MobEntity zombie = zombieRef.get();
        assertNotNull(chicken, "the chicken spawned");
        assertNotNull(zombie, "the zombie spawned");
        // THE BOTH-SIDES STAMP (the reference's rider/vehicle pair): the
        // rider carries the mount's id + kind, the mount the mob-rider flag.
        assertTrue(zombie.ridingAMob(),
                "the rider-side stamp (the reference's vehicle row)");
        assertEquals(chicken.entityId(), zombie.mountedMobId(),
                "the stamp names the mount");
        assertSame(MobType.CHICKEN, zombie.mountedMobType(),
                "the stamp carries the mount's kind");
        assertTrue(chicken.mobRiderSeat(),
                "the mount-side stamp (the mob holds the seat)");
        return new MobEntity[]{chicken, zombie};
    }

    // ------------------------------------------------ the hand-off + the drive

    /**
     * THE CHASE: the mounted zombie steers the chicken along its own
     * route toward the player — the steer flag armed through the window
     * (the cross-body moveAlong every rider tick) and the pair closing
     * (the mount-side drive walking the handed route). The pre-fix
     * engine's parked jockey chicken never moved on any target.
     */
    @Test
    void theMountedZombieSteersTheChickenAlongItsOwnRoute() throws Exception {
        buildRoof();
        MobEntity[] pair = spawnPair();
        MobEntity chicken = pair[0];
        MobEntity zombie = pair[1];
        joinPlayer("Fodder", PLAYER_X, ROUTE_Z);
        EngineAwaits.awaitCondition(server.world(),
                () -> chicken.navigationHanded(),
                200, "the steer arms (the hand-off's first write)");
        EngineAwaits.awaitCondition(server.world(),
                () -> PLAYER_X - chicken.position().x() <= 3.0,
                500, "the pair closes on the player along the handed route");
        // The rider rode the whole chase: the zombie's own body never
        // left the seat (the anchor owns the position, the freeze holds
        // the drift) and its mind chased from the seat.
        assertTrue(zombie.ridingAMob(), "the rider still seated at the close");
        assertTrue(zombie.mode() == MobEntity.Mode.CHASE
                        || PLAYER_X - zombie.position().x() <= 4.0,
                "the rider's mind chased from the seat");
    }

    // ------------------------------------------- the speed + the shared route

    /**
     * THE SPEED LAW + THE SHARED ROUTE: the chicken's closing slope over
     * a straight mid-chase window is its walk base times the hand-off's
     * literal 1.5 (0.035 x 1.5 = 0.0525 blocks per tick — the band
     * [0.045, 0.0625] excludes the stroll band's flat 0.035 and the
     * drive-less engine's 0), and the mount's navigation holds the
     * RIDER'S OWN route object — the reference's shared-Path shape (both
     * navigations advance the one walking index).
     */
    @Test
    void theChickenWalksTheHandedRouteAtTheHandoffsOwnSpeed() throws Exception {
        buildRoof();
        MobEntity[] pair = spawnPair();
        MobEntity chicken = pair[0];
        MobEntity zombie = pair[1];
        joinPlayer("Mark", PLAYER_X, ROUTE_Z);
        EngineAwaits.awaitCondition(server.world(),
                () -> chicken.navigationHanded()
                        && PLAYER_X - chicken.position().x() <= 10.5,
                300, "the chase runs (the route's first leg walked)");
        // The shared-route witness (sampled inside one ticker action, the
        // mid-chase adoption): the chicken's navigation holds the ZOMBIE'S
        // route object — the same instance the rider walks.
        AtomicReference<Boolean> sharedRoute = new AtomicReference<>();
        server.ticker().submit(() -> sharedRoute.set(
                chicken.navigationPath() != null
                        && chicken.navigationPath() == zombie.navigationPath()));
        EngineAwaits.awaitCondition(server.world(),
                () -> sharedRoute.get() != null, 100, "the route sampled");
        assertTrue(sharedRoute.get(),
                "the mount's navigation holds the rider's own route object"
                        + " (the reference's shared-Path shape)");
        // The speed window: forty straight ticks mid-chase.
        AtomicReference<Double> x0 = new AtomicReference<>();
        server.ticker().submit(() -> x0.set(chicken.position().x()));
        EngineAwaits.awaitCondition(server.world(),
                () -> x0.get() != null, 100, "x0 sampled");
        EngineAwaits.awaitTicks(server.world(), 40, "the speed window");
        AtomicReference<Double> x1 = new AtomicReference<>();
        server.ticker().submit(() -> x1.set(chicken.position().x()));
        EngineAwaits.awaitCondition(server.world(),
                () -> x1.get() != null, 100, "x1 sampled");
        double slope = (x1.get() - x0.get()) / 40.0;
        assertTrue(slope >= 0.045 && slope <= 0.0625,
                "the handed route's walk at the hand-off's 1.5 over the base"
                        + " (0.035 x 1.5 = 0.0525/tick; measured " + slope + ")");
    }

    // -------------------------------------------------------- the jockey's bite

    /**
     * THE BITE: the jockey strikes from the seat — the drive carries the
     * pair into the zombie's melee reach (the reach reads the seat
     * position, the anchor 0.1 forward of the chicken's yaw at half its
     * height) and the player's health drops while the zombie rides. The
     * pre-fix pair never closed, the bite never armed.
     */
    @Test
    void theJockeyStrikesFromTheSeat() throws Exception {
        buildRoof();
        MobEntity[] pair = spawnPair();
        MobEntity zombie = pair[1];
        PlayerSession victim = joinPlayer("Bitten", PLAYER_X, ROUTE_Z);
        EngineAwaits.awaitCondition(server.world(),
                () -> victim.health() < PlayerSession.MAX_HEALTH,
                600, "the jockey's bite landed from the seat");
        assertTrue(zombie.ridingAMob(),
                "the zombie was still seated when it struck");
    }

    // -------------------------------------------------------------- the tail

    /**
     * THE TAIL: when the hunt ends (the player teleported beyond the
     * follow range mid-chase), the handed route STANDS — no own lane
     * took the navigation — and the drive walks it to its end (the
     * reference's movement control walking the standing path until
     * isDone): the chicken finishes the chase the rider abandoned,
     * bounded by the route's own remainder.
     */
    @Test
    void theChickenFinishesTheHandedRouteWhenTheHuntEnds() throws Exception {
        buildRoof();
        MobEntity[] pair = spawnPair();
        MobEntity chicken = pair[0];
        PlayerSession runner = joinPlayer("Runner", PLAYER_X, ROUTE_Z);
        EngineAwaits.awaitCondition(server.world(),
                () -> chicken.navigationHanded()
                        && PLAYER_X - chicken.position().x() <= 8.0,
                300, "the chase runs before the departure");
        // The departure: the player beyond the zombie's follow range (35)
        // but INSIDE the despawn band (128) — the pair lives through the
        // tail (a riderless jockey chicken beyond 128 despawns whole, the
        // CJ law; the rider's seat keeps this pair's mark quiet).
        server.teleportPlayer(runner, new Position(100.5, 5.0, ROUTE_Z));
        EngineAwaits.awaitCondition(server.world(),
                () -> runner.position().x() > 90.0, 200, "the runner departed");
        AtomicReference<Double> departureX = new AtomicReference<>();
        server.ticker().submit(() -> departureX.set(chicken.position().x()));
        EngineAwaits.awaitCondition(server.world(),
                () -> departureX.get() != null, 100, "the departure sampled");
        // The tail: the handed route walks to its end (never a stroll's
        // own route — the steer flag stands through the walk, no own
        // write took the navigation).
        EngineAwaits.awaitCondition(server.world(),
                () -> chicken.navigationHanded()
                        && (chicken.navigationPath() == null
                                || chicken.navigationPath().isDone()),
                400, "the handed route finished (the vanilla tail)");
        assertTrue(Math.abs(chicken.position().x() - departureX.get()) <= 10.0,
                "the tail bounded by the route's own remainder (walked "
                        + (chicken.position().x() - departureX.get()) + " blocks)");
    }
}
