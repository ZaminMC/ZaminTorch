package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.village.Village;
import net.zaminmc.torch.server.village.VillageDoor;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-24 — THE VILLAGE NIGHT WALK + THE VILLAGER'S STARE over the
 * reference's own goal laws: {@code WanderThroughVillageAtNightGoal}
 * (the zombie's band-6 raid + the iron golem's band-3 stroll) and
 * {@code GoToEntityGoal} over {@code LookAtEntityGoal} (the villager's
 * band-9 close-player stare).
 *
 * <p>THE LAWS PINNED:</p>
 * <ul>
 *   <li>THE NIGHT WALK — the friendly gate (the golem refuses the sunny
 *       sky, the zombie raids whatever the clock reads), the findDoor
 *       walk over the door MEMORY (a door the body reached is refused
 *       until the 15-row cap rolls it out), the (width+4)² continue
 *       band, and the stop's memory write.</li>
 *   <li>THE STARE — the chance-1.0 claim over the nearest player
 *       within 3, the 40+nextInt(40) window, the head hold, the
 *       out-of-reach stop, and the villager's own registration.</li>
 * </ul>
 */
class VillageNightStareParityDiscoveryTest {

    /** The scripted player: the view the mind reads, mutable per phase. */
    private static final class ScriptedPlayer implements MobEntity.PlayerView {
        Position position = new Position(2.5, 8.0, 0.5);
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

    /** A flat-world stub with the scripted player and the scripted village. */
    private static final class ScriptedWorld implements MobEntity.WorldQuery {
        final ScriptedPlayer player = new ScriptedPlayer();
        boolean sunny = true;
        Village village;

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
        public boolean isSunny() {
            return sunny;
        }

        @Override
        public Village nearestVillageAt(int x, int y, int z, int range) {
            return village;
        }
    }

    /**
     * THE NIGHT WALK — a zombie inside a scripted village claims the
     * walk to the nearest door NOT in its memory (the path consult the
     * flat stub answers); the golem's sunny gate refuses the claim
     * until the sun sets; and the stop's memory write — the body
     * teleported within the squared-16 band of the door — refuses that
     * door on the next claim (the OTHER door answers).
     */
    @Test
    void theNightWalkClaimsTheNearestUnrememberedDoor() {
        ScriptedWorld world = new ScriptedWorld();
        Village village = new Village();
        // Two doors near the body: the nearer at (6, 8, 0), the farther
        // at (10, 8, 0).
        village.addDoor(new VillageDoor(6, 8, 0, 2, 0, 0));
        village.addDoor(new VillageDoor(10, 8, 0, 2, 0, 0));
        world.village = village;

        MobEntity zombie = new MobEntity(800, MobType.ZOMBIE, new Position(0.5, 8.0, 0.5),
                new Random(201L), world);
        assertTrue(zombie.goalNightVillageCanUse(),
                "the zombie claims the night walk (the friendly gate false — any clock)");
        VillageDoor door = zombie.nightWalkDoorForTest();
        assertNotNull(door, "the walk holds its door");
        assertEquals(6, door.x(), "the NEAREST door answers the findDoor walk");

        // The (width + 4)² continue band: the body teleported within the
        // band stops the walk, the door enters the memory (the stop's
        // squared-16 write — the body within 4 blocks of the cell).
        zombie.teleport(new Position(6.5, 8.0, 0.5));
        assertFalse(zombie.goalNightVillageCanUse(),
                "the body inside the (width+4)² band stops the walk");
        assertEquals(1, zombie.nightWalkMemorySizeForTest(),
                "the reached door entered the memory");

        // The next claim: the remembered door is refused — the OTHER
        // door answers.
        assertTrue(zombie.goalNightVillageCanUse(),
                "the walk re-claims over the other door");
        assertEquals(10, zombie.nightWalkDoorForTest().x(),
                "the remembered door never answers twice");

        // The golem's friendly gate: the sunny sky refuses the claim.
        MobEntity golem = new MobEntity(801, MobType.IRON_GOLEM, new Position(0.5, 8.0, 0.5),
                new Random(202L), world);
        assertFalse(golem.goalNightVillageCanUse(),
                "the golem refuses the walk under the sunny sky (the friendly gate)");
        world.sunny = false;
        assertTrue(golem.goalNightVillageCanUse(),
                "the golem claims the walk once the sun sets");
    }

    /**
     * THE VILLAGER'S STARE — a player two blocks from an idle villager
     * claims the body at once (the chance-1.0 registration — no dice
     * gate), the window rolls 40+nextInt(40), the head holds the
     * player's bearing, and the player walking out of the 3-block
     * reach stops the window. The kind gate: a cow beside the same
     * player never claims.
     */
    @Test
    void theVillagerStaresAtTheClosePlayer() {
        ScriptedWorld world = new ScriptedWorld();
        world.player.position = new Position(2.5, 8.0, 0.5); // 2 blocks east
        MobEntity villager = new MobEntity(810, MobType.VILLAGER, new Position(0.5, 8.0, 0.5),
                new Random(210L), world);

        assertTrue(villager.goalLookAtCloseCanUse(),
                "the chance-1.0 roll always claims a player within 3");
        int window = villager.lookAtCloseTicksLeftForTest();
        assertTrue(window >= 40 && window <= 79,
                "the window is 40 + nextInt(40) (read " + window + ")");

        // The head hold: the east bearing.
        villager.goalLookAtCloseTick();
        assertEquals(-90.0f, villager.headYaw(), 0.001f, "the head holds the player east");
        assertEquals(window - 1, villager.lookAtCloseTicksLeftForTest(), "the clock pays its tick");

        // The out-of-reach stop: the player teleports 10 blocks out.
        world.player.position = new Position(12.5, 8.0, 0.5);
        assertFalse(villager.goalLookAtCloseCanUse(),
                "the window stops when the player leaves the 3-block reach");
        assertEquals(0, villager.lookAtCloseTicksLeftForTest());

        // The kinds gate: the stare is the villager's own registration.
        MobEntity cow = new MobEntity(811, MobType.COW, new Position(0.5, 8.0, 0.5),
                new Random(211L), world);
        world.player.position = new Position(2.5, 8.0, 0.5);
        assertFalse(cow.goalLookAtCloseCanUse(), "the cow never registers the stare");
    }
}
