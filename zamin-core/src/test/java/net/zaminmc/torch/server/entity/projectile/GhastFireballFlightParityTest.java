package net.zaminmc.torch.server.entity.projectile;

import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-EX9 — the ghast fireball's flight constants (the fireball family rides
 * the BASE projectile walk): {@code FireballEntity extends ProjectileEntity}
 * DIRECTLY — not {@code ThrownEntity} — so its flight is the reference's own
 * {@code velocity += acceleration; velocity *= getDrag()} with NO gravity
 * term ({@code ProjectileEntity.tick} lines 186-191; the gravity walk lives
 * in {@code ThrownEntity.tick} line 240 alone, behind the throwable family's
 * five subclasses) and the base walk's own drag ({@code getDrag} line 203:
 * 0.95 — the ghast shot shares it with the small fireball and the skull).
 *
 * <p>THE PRE-FIX ENGINE answered both constants from the THROWABLE rows:
 * {@code gravityOf(LARGE_FIREBALL) = 0.03} and {@code dragOf(LARGE_FIREBALL)
 * = 0.99} — the exclusion lists covered SMALL_FIREBALL and WITHER_SKULL
 * alone. THE OBSERVABLE PAIR: the launched fireball's terminal speed ran to
 * {@code a*drag/(1-drag) = 0.1*0.99/0.01 = 9.9} blocks a tick where vanilla
 * caps at {@code 0.1*0.95/0.05 = 1.9} (a five-times overshoot); and the
 * RESTORED fireball (the anvil import's zero-acceleration load shape — the
 * reference's writeNbt never saves the acceleration fields) fell under the
 * gravity arm the launch path's acceleration happened to mask, drooping a
 * restored straight shot ~19 blocks over 40 ticks where vanilla flies it
 * level.</p>
 */
class GhastFireballFlightParityTest {

    /** The void world: nothing is solid, nothing is hit. */
    private static final class NoHits implements ProjectileManager.HitResolver {
        @Override public MobEntity mobAt(Position point, int dimension) { return null; }
        @Override public PlayerSession playerAt(Position point, int dimension) { return null; }
    }

    private static final class NoCombat implements ProjectileManager.CombatSink {
        @Override public boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId) {
            return true;
        }
        @Override public void playerHit(PlayerSession player, float damage, double kbYaw) { }
        @Override public void chickenHatch(Position position, int dimension) { }
    }

    private static ProjectileManager manager(long seed) {
        return new ProjectileManager((x, y, z) -> false, new NoHits(),
                new NoCombat(), new net.zaminmc.torch.server.fx.FxManager(),
                new Random(seed), 7_000);
    }

    private static double speed(ProjectileEntity body) {
        return Math.sqrt(body.velocityX() * body.velocityX()
                + body.velocityY() * body.velocityY()
                + body.velocityZ() * body.velocityZ());
    }

    /**
     * THE DRAG SEAT — the launched fireball's terminal walk: the
     * acceleration magnitude is exactly 0.1 (the reference's normalized
     * ctor), so the speed converges on {@code 0.1*drag/(1-drag)} — 1.9 at
     * the base walk's 0.95 drag. The pre-fix 0.99 row carried it toward 9.9:
     * at tick 60 the pre-fix speed stands near 4.5 where vanilla's is under
     * 1.9 (monotone approach — the bound holds at every tick past the
     * transient).
     */
    @Test
    void theLaunchedFireballConvergesOnTheBaseWalksTerminalSpeed() {
        ProjectileManager manager = manager(31L);
        ProjectileEntity fireball = manager.launchGhastFireball(777,
                new Position(0.0, 100.0, 0.0), 1.0, 0.0, 0.0, 0);
        assertNotNull(fireball, "the ghast shot left the muzzle");

        for (int tick = 0; tick < 60; tick++) {
            manager.tick(java.util.List.of());
        }
        double speed = speed(fireball);
        assertTrue(speed > 1.0,
                "the fireball accelerated away from rest (got " + speed + ")");
        assertTrue(speed < 2.0,
                "the base walk's own terminal 1.9 caps the speed (got " + speed
                        + " — the throwable drag's 9.9 overshoot)");
    }

    /**
     * THE GRAVITY SEAT — the restored fireball's level flight: the anvil
     * import lands the saved velocity with ZERO acceleration (the
     * reference's load shape — writeNbt never writes the acceleration
     * fields), and the base walk applies no gravity to it: a straight
     * restored shot flies LEVEL, bleeding only the 0.95 drag. The pre-fix
     * gravity row (0.03, the arm the launch path's acceleration masks)
     * drooped it ~19 blocks over 40 ticks.
     */
    @Test
    void theRestoredFireballFliesLevelWithNoGravity() {
        ProjectileManager manager = manager(32L);
        ProjectileEntity fireball = manager.spawnRestored(
                ProjectileEntity.Kind.LARGE_FIREBALL,
                new Position(0.0, 100.0, 0.0), 1.0, 0.0, 0.0);

        for (int tick = 0; tick < 40; tick++) {
            manager.tick(java.util.List.of());
        }
        assertTrue(fireball.position().y() > 99.5,
                "the restored straight shot flies level (got y="
                        + fireball.position().y() + " — the throwable gravity's droop)");
        assertTrue(fireball.position().x() > 10.0,
                "the restored shot carried its saved velocity downrange");
    }
}
