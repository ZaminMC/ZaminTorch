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
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave CJ — the chicken's jockey family (the reference's own rows, the
 * architecture's census pick): the jockey mark's own laws — the 10-XP kill
 * (ChickenEntity.getXpDrop line 160: {@code this.hasJockey() ? 10 :
 * super.getXpDrop}), the riderless despawn (canDespawn line 164:
 * {@code this.hasJockey() && this.rider == null} — the chicken left
 * standing when its baby zombie died moves on like a monster), the jockey
 * seat (updateRiderPositon lines 168-177 — the reference's own typo kept:
 * the rider 0.1 forward of the body's yaw at HALF the height, the living
 * rider's bodyYaw following the mount's), and the spawn arm's gather
 * (ZombieEntity.initialize lines 413-415: the nearby NOT-RIDING chickens
 * within the zombie's box grown (5, 3, 5), the FIRST of the gather).
 */
class ChickenJockeyParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "cj" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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

    private int totalOrbXp() {
        int total = 0;
        for (var orb : server.world().experienceOrbs()) {
            total += orb.amount();
        }
        return total;
    }

    // ------------------------------------------------ the 10-XP kill

    @Test
    void theJockeyChickenPaysTenAndThePlainOneThePassiveBand() throws Exception {
        MobEntity jockey = server.mobs().spawnAt(MobType.CHICKEN,
                new Position(0.5, 8.0, 0.5));
        assertNotNull(jockey, "the chicken spawned");
        jockey.setChickenJockey(true);
        int before = totalOrbXp();
        assertTrue(server.mobs().hurt(jockey, 1000.0f, 0.0),
                "the killing wound landed");
        EngineAwaits.awaitTicks(server.world(), 26, "the death resolves");
        assertEquals(10, totalOrbXp() - before,
                "the jockey chicken's own ten (getXpDrop line 160)");
        // The plain chicken keeps the passive band (never ten).
        MobEntity plain = server.mobs().spawnAt(MobType.CHICKEN,
                new Position(4.5, 8.0, 0.5));
        before = totalOrbXp();
        assertTrue(server.mobs().hurt(plain, 1000.0f, 0.0));
        EngineAwaits.awaitTicks(server.world(), 26, "the plain death resolves");
        int paid = totalOrbXp() - before;
        assertTrue(paid >= 1 && paid <= 3,
                "the plain chicken keeps the passive 1-3 band (paid " + paid + ")");
    }

    // ------------------------------------------------ the riderless despawn

    @Test
    void theJockeyMarkAloneDespawnsAndTheRiddenMarkStays() throws Exception {
        MobEntity riderless = server.mobs().spawnAt(MobType.CHICKEN,
                new Position(0.5, 8.0, 0.5));
        riderless.setChickenJockey(true);
        assertTrue(riderless.canDespawn(),
                "the riderless jockey moves on like a monster (canDespawn line 164)");
        MobEntity plain = server.mobs().spawnAt(MobType.CHICKEN,
                new Position(4.5, 8.0, 0.5));
        assertFalse(plain.canDespawn(),
                "the plain chicken never leaves (the animal's own law)");
        MobEntity ridden = server.mobs().spawnAt(MobType.CHICKEN,
                new Position(8.5, 8.0, 0.5));
        MobEntity zombie = server.mobs().spawnAt(MobType.ZOMBIE,
                new Position(8.5, 8.0, 0.5));
        ridden.setChickenJockey(true);
        ridden.setRider(zombie.entityId());
        assertFalse(ridden.canDespawn(),
                "the ridden jockey stays — the seat holds the body");
    }

    // ------------------------------------------------ the jockey seat

    @Test
    void theRiderSitsForwardOfTheYawAtHalfTheHeightAndFollowsIt() throws Exception {
        MobEntity chicken = server.mobs().spawnAt(MobType.CHICKEN,
                new Position(0.5, 8.0, 0.5));
        MobEntity zombie = server.mobs().spawnAt(MobType.ZOMBIE,
                new Position(0.5, 8.0, 0.5));
        zombie.setBreedingAge(-24_000); // the baby zombie — the jockey's rider
        // The seat's own yaw (updateRiderPositon's sin/cos pair): 90
        // degrees puts the 0.1 forward offset wholly on X.
        chicken.setBodyYaw(90.0f);
        chicken.setRider(zombie.entityId());
        EngineAwaits.awaitTicks(server.world(), 3, "the seat anchors");
        double expectedY = chicken.position().y() + chicken.type().height * 0.5
                + net.zaminmc.torch.server.entity.vehicle.RiderSeatLaw
                .riderRideHeight(zombie.type(), zombie.isBaby());
        assertEquals(0.5 + 0.1, zombie.position().x(), 1e-6,
                "the rider sits 0.1 forward of the yaw (x + 0.1*sin)");
        assertEquals(expectedY, zombie.position().y(), 1e-6,
                "the rider sits at half the body's height plus its own ride height");
        assertEquals(0.5, zombie.position().z(), 1e-6,
                "the z offset rides the cos arm (z - 0.1*cos(90) = z)");
        assertEquals(90.0f, zombie.bodyYawLive(), 1e-6,
                "the living rider's bodyYaw follows the mount's");
    }

    // ------------------------------------------------ the spawn arm's gather

    @Test
    void theGatherTakesTheFirstNotRidingChickenInsideTheGrownBox() throws Exception {
        // The zombie at the origin cell; the chickens around: one OUT of
        // the grown (5, 3, 5) box (7+ blocks east), one INSIDE, one inside
        // but already seated on a vehicle — the FIRST eligible of the
        // manager's own order wins (the reference's list.get(0)).
        MobEntity zombie = server.mobs().spawnAt(MobType.ZOMBIE,
                new Position(0.5, 8.0, 0.5));
        MobEntity far = server.mobs().spawnAt(MobType.CHICKEN,
                new Position(8.5, 8.0, 0.5)); // beyond the grown box
        MobEntity near = server.mobs().spawnAt(MobType.CHICKEN,
                new Position(3.5, 8.0, 0.5)); // inside
        MobEntity seated = server.mobs().spawnAt(MobType.CHICKEN,
                new Position(2.5, 8.0, 0.5)); // inside, but riding
        // The seated marker: the vehicle seat's own stamp (the reference's
        // NOT_RIDING filter reads the vehicle field).
        seated.setSeatedVehicleId(77);
        assertSame(near, server.mobs().nearestRideableChicken(zombie),
                "the gather's first eligible chicken (the grown box, the not-riding filter)");
        // The seated one alone refused: clearing its seat brings it home
        // (the manager's order — the seated chicken spawned after the near
        // one, so the near one still wins; the far one never does).
        seated.setSeatedVehicleId(-1);
        assertSame(near, server.mobs().nearestRideableChicken(zombie));
    }
}
