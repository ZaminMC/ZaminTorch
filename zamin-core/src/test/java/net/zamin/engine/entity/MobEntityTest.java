package net.zamin.engine.entity;

import net.zamin.api.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The living body on a deterministic world: gravity with ground snap, the
 * small goal set (wander, panic, chase), the hurt/death timers and the zombie
 * melee decision. All values the wire and the loot flow depend on.
 */
class MobEntityTest {

    /** Solid everywhere below y=4 (like the flat world's surface at level 4). */
    private static MobEntity.WorldQuery worldAt(Position nearestPlayer) {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                if (nearestPlayer == null) {
                    return null;
                }
                double dx = nearestPlayer.x() - x;
                double dy = nearestPlayer.y() - y;
                double dz = nearestPlayer.z() - z;
                return dx * dx + dy * dy + dz * dz <= range * range ? nearestPlayer : null;
            }
        };
    }

    private static MobEntity spawn(MobType type, Position at, long seed, MobEntity.WorldQuery query) {
        return new MobEntity(1, type, at, new Random(seed), query);
    }

    @Test
    void gravitySettlesTheBodyOnSolidGround() {
        MobEntity pig = spawn(MobType.PIG, new Position(0.5, 8.0, 0.5), 7, worldAt(null));
        for (int i = 0; i < 200; i++) {
            pig.tick();
        }
        assertEquals(4.0, pig.position().y(), 0.0001, "feet rest on the surface block");
        assertTrue(pig.onGround(), "grounded after settling");
        assertEquals(4.0, pig.position().y(), 0.0001, "no further sink after settling (rest stable)");
    }

    @Test
    void passiveMobsWanderAfterAnIdleRoll() {
        MobEntity pig = spawn(MobType.PIG, new Position(0.5, 4.0, 0.5), 42, worldAt(null));
        int ticks = 0;
        while (pig.mode() != MobEntity.Mode.WANDER && ticks < 400) {
            pig.tick();
            ticks++;
        }
        assertEquals(MobEntity.Mode.WANDER, pig.mode(),
                "the idle roll turned into a wander within the window");
        Position wandered = pig.position();
        int moved = 0;
        while (pig.mode() == MobEntity.Mode.WANDER && moved < 400) {
            pig.tick();
            moved++;
            if (pig.position().x() != wandered.x() || pig.position().z() != wandered.z()) {
                return; // the body actually moved while wandering
            }
        }
        throw new AssertionError("wandering never moved the mob");
    }

    @Test
    void hurtTriggersPanicAndItExpires() {
        MobEntity cow = spawn(MobType.COW, new Position(0.5, 4.0, 0.5), 3, worldAt(null));
        cow.hurt(3.0f);
        assertEquals(7.0f, cow.health(), 0.0001, "damage applied");
        assertTrue(cow.hurtFlashing(), "the hurt flash is up");
        assertEquals(MobEntity.Mode.PANIC, cow.mode(), "passive mobs panic");
        for (int i = 0; i < MobEntity.PANIC_TICKS + 2; i++) {
            cow.tick();
        }
        assertFalse(cow.hurtFlashing(), "the flash window ended");
        assertTrue(cow.mode() != MobEntity.Mode.PANIC, "panic expired back to normal goals");
    }

    @Test
    void deathRunsTheHistoricalTwentyTickAnimation() {
        MobEntity chicken = spawn(MobType.CHICKEN, new Position(0.5, 4.0, 0.5), 5, worldAt(null));
        chicken.hurt(999.0f);
        assertEquals(0.0f, chicken.health(), "lethal damage floors health");
        assertTrue(chicken.dying(), "the death animation runs");
        for (int i = 0; i < MobEntity.DEATH_TICKS - 1; i++) {
            chicken.tick();
        }
        assertFalse(chicken.deathAnimationFinished(), "the animation holds 20 ticks");
        chicken.tick();
        assertTrue(chicken.deathAnimationFinished(), "the 20-tick death finished (loot is due)");
    }

    @Test
    void knockbackPushesAwayFromTheAttackerAndLifts() {
        MobEntity pig = spawn(MobType.PIG, new Position(0.5, 4.0, 0.5), 11, worldAt(null));
        // Attacker to the pig's -Z: the push yaw points back at +Z (yaw 0).
        pig.knockbackFrom(0.0);
        assertTrue(pig.velocityY() > 0, "the historical upward lift");
        // yaw 0 faces +Z: -sin(0) x speed = 0, cos(0) x speed = +Z velocity.
        assertTrue(pig.velocityZ() > 0, "pushed along the attacker-to-mob direction");
    }

    @Test
    void zombieChasesTheNearestPlayerAndFacesIt() {
        Position player = new Position(8.0, 4.0, 0.0);
        MobEntity zombie = spawn(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5), 9, worldAt(player));
        zombie.tick();
        assertEquals(MobEntity.Mode.CHASE, zombie.mode(), "aggro range engaged the chase");
        float expectedYaw = (float) Math.toDegrees(Math.atan2(-(8.0 - 0.5), 0.0 - 0.5));
        assertEquals(expectedYaw, zombie.yaw(), 0.5, "the body faces the target");
    }

    @Test
    void zombieMeleeLandsOnCooldownWithinReach() {
        Position player = new Position(1.0, 4.0, 0.5); // 0.5 blocks away: inside reach
        MobEntity zombie = spawn(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5), 13, worldAt(player));
        zombie.tick();
        assertTrue(zombie.consumePendingAttack(), "first swing lands immediately in range");
        assertFalse(zombie.consumePendingAttack(), "the swing was consumed");
        for (int i = 0; i < MobEntity.ZOMBIE_ATTACK_COOLDOWN - 1; i++) {
            zombie.tick();
            assertFalse(zombie.consumePendingAttack(), "cooldown suppresses swings");
        }
        zombie.tick();
        assertTrue(zombie.consumePendingAttack(), "the cooldown elapsed and the swing re-arms");
    }

    @Test
    void distantPlayersAreIgnoredByTheZombie() {
        Position far = new Position(100.0, 4.0, 100.0); // beyond the 16-block aggro
        MobEntity zombie = spawn(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5), 17, worldAt(far));
        zombie.tick();
        assertTrue(zombie.mode() != MobEntity.Mode.CHASE, "out of aggro: no chase");
        assertFalse(zombie.consumePendingAttack(), "no melee out of aggro");
    }

    @Test
    void idleChatterReArmsWithinItsWindow() {
        MobEntity cow = spawn(MobType.COW, new Position(0.5, 4.0, 0.5), 23, worldAt(null));
        int sounds = 0;
        for (int i = 0; i < 1_200; i++) {
            if (cow.idleSoundDue()) {
                sounds++;
            }
            cow.tick();
        }
        // Window is 8-24 s; 60 s must produce at least two and at most eight.
        assertTrue(sounds >= 2 && sounds <= 8, "ambient chatter cadence, got " + sounds);
    }
}
