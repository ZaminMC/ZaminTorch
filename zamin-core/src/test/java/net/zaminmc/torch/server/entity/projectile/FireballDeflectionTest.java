package net.zaminmc.torch.server.entity.projectile;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.fx.FxManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fireball deflection (Slice 25b — ProjectileEntity.takeDamage lines
 * 248-273): the player's punch redirects the fireball family — the velocity
 * becomes the attacker's look vector, the acceleration look*0.1, the puncher
 * becomes the new shooter — and the fireball family's own 25-tick
 * shooter-immunity window (ProjectileEntity line 119) lets a deflected
 * fireball strike the ghast that loosed it.
 *
 * <p>The family census (F-EX5's resolution): of ProjectileEntity's THREE
 * direct subclasses exactly ONE inherits the re-aim — FireballEntity (the
 * ghast's large shot, no override). SmallFireballEntity (lines 62-64) and
 * WitherSkullEntity (lines 94-96) both override {@code takeDamage} with
 * {@code return false}: the blaze's charge and the wither's skull refuse
 * the punch AND the blast wound whole — only the SCATTER (Explosion lines
 * 135-137, outside the takeDamage verdict) ever moves them.</p>
 */
class FireballDeflectionTest {

    /** Records the wire events (the deflection's own). */
    private static final class Events implements ProjectileManager.Listener {
        int deflections;

        @Override
        public void onProjectileSpawned(ProjectileEntity projectile) {
        }

        @Override
        public void onProjectileMoved(ProjectileEntity projectile) {
        }

        @Override
        public void onProjectileLanded(ProjectileEntity projectile) {
        }

        @Override
        public void onProjectileRemoved(ProjectileEntity projectile, String reason) {
        }

        @Override
        public void onProjectileDeflected(ProjectileEntity projectile) {
            deflections++;
        }
    }

    /** The combat sink recording the landed hits. */
    static class RecordingCombat implements ProjectileManager.CombatSink {
        final List<String> events = new ArrayList<>();

        @Override
        public boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId) {
            events.add("mob");
            return true;
        }

        @Override
        public void playerHit(net.zaminmc.torch.server.player.PlayerSession player,
                              float damage, double kbYaw) {
            events.add("player");
        }

        @Override
        public void mobIgniteFromProjectile(MobEntity mob) {
        }

        @Override
        public void playerIgniteFromProjectile(
                net.zaminmc.torch.server.player.PlayerSession player) {
        }

        @Override
        public void chickenHatch(Position position, int dimension) {
        }
    }

    /** The rigged hit resolver: one fixed mob the walk always finds. */
    static final class RiggedHits implements ProjectileManager.HitResolver {
        MobEntity mob;

        @Override
        public MobEntity mobAt(Position point, int dimension) {
            return mob;
        }

        @Override
        public net.zaminmc.torch.server.player.PlayerSession playerAt(Position point, int dimension) {
            return null;
        }
    }

    private static MobEntity.WorldQuery solidWorld() {
        return new MobEntity.WorldQuery() {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return false; // open air: only the entity hits resolve
            }

            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    @Test
    void thePunchRedirectsTheFireball() {
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new RiggedHits(), new RecordingCombat(),
                new FxManager(), new Random(11), 5_000);
        Events events = new Events();
        manager.addListener(events);
        int ghastId = 777;

        ProjectileEntity fireball = manager.launchGhastFireball(ghastId,
                new Position(0.0, 10.0, 0.0), 1.0, 0.0, 0.0, 0);
        assertEquals(ghastId, fireball.throwerId(), "the ghast shot it");

        // The punch (takeDamage lines 248-273): the attacker's look vector
        // owns the new velocity, the acceleration follows at 0.1x, and the
        // attacker becomes the new shooter.
        assertTrue(manager.deflect(fireball, 4242, 0.0, 0.6, 0.8));
        assertEquals(4242, fireball.throwerId(), "the puncher is the new shooter");
        assertEquals(0.0, fireball.velocityX(), 1e-9);
        assertEquals(0.6, fireball.velocityY(), 1e-9);
        assertEquals(0.8, fireball.velocityZ(), 1e-9);
        assertEquals(0.0, fireball.accelerationX(), 1e-9, "velocity * 0.1");
        assertEquals(0.06, fireball.accelerationY(), 1e-9, "velocity * 0.1");
        assertEquals(0.08, fireball.accelerationZ(), 1e-9, "velocity * 0.1");
        assertEquals(1, events.deflections, "the wire re-publishes the velocity");
    }

    @Test
    void theThrownFamilyAndArrowsTakeNoDeflection() {
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new RiggedHits(), new RecordingCombat(),
                new FxManager(), new Random(12), 5_100);
        Events events = new Events();
        manager.addListener(events);

        ProjectileEntity arrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0, 10, 0), 0, 0, 3.0, 0);
        assertFalse(manager.deflect(arrow, 4242, 0.0, 0.0, 1.0),
                "the arrow carries no deflect surface");
        ProjectileEntity snowball = manager.launch(ProjectileEntity.Kind.SNOWBALL, -1,
                new Position(0, 10, 0), 0, 0, 1.5, 0);
        assertFalse(manager.deflect(snowball, 4242, 0.0, 0.0, 1.0),
                "the shard family carries no deflect surface");
        assertEquals(0, events.deflections, "no wire events fired");

        // The fire charge REFUSES the deflection whole
        // (SmallFireballEntity.takeDamage lines 62-64: `return false` —
        // the override the subclass-counting census missed): only the
        // GHAST's large fireball (FireballEntity, NO override) inherits
        // the re-aim arm. A punched blaze shot keeps its course.
        ProjectileEntity charge = manager.launchFireball(
                new Position(0, 10, 0), 0.0, 0.0, 1.0, 0);
        assertFalse(manager.deflect(charge, 4242, 1.0, 0.0, 0.0),
                "the fire charge answers false (the override's own verdict)");
        assertEquals(-1, charge.throwerId(), "no new shooter: the punch never landed");
        assertEquals(0, events.deflections, "no wire events fired");
    }

    /**
     * The blast wound's own census (F-EX5's resolution — the re-aim is the
     * LARGE fireball's alone): a mob-sourced blast beside a small fireball
     * or a wither skull never re-aims them — SmallFireballEntity's and
     * WitherSkullEntity's own {@code takeDamage → return false} overrides
     * refuse the wound whole (the scatter rides OUTSIDE the verdict, the
     * caller's addVelocity arm). Only the ghast's FireballEntity inherits
     * the re-aim.
     */
    @Test
    void theBlastWoundReAimsTheGhastFireballAlone() {
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new RiggedHits(), new RecordingCombat(),
                new FxManager(), new Random(18), 5_500);
        Events events = new Events();
        manager.addListener(events);

        // The LARGE control (the only kind that inherits the arm): the
        // creeper-sourced blast re-aims the ghast's shot — the velocity
        // BECOMES the attacker's look, the attacker the new shooter.
        ProjectileEntity ghastShot = manager.launchGhastFireball(777,
                new Position(0.0, 10.0, 0.0), 1.0, 0.0, 0.0, 0);
        manager.woundFromExplosion(ghastShot, 4242, 0.0, 0.6, 0.8);
        assertEquals(4242, ghastShot.throwerId(), "the blast's source is the new shooter");
        assertEquals(0.0, ghastShot.velocityX(), 1e-9);
        assertEquals(0.6, ghastShot.velocityY(), 1e-9);
        assertEquals(0.8, ghastShot.velocityZ(), 1e-9);
        assertEquals(0.06, ghastShot.accelerationY(), 1e-9, "look * 0.1");
        assertEquals(1, events.deflections, "the wire re-publishes the velocity");

        // The small fireball: the override answers false — no re-aim, no
        // new shooter, the velocity untouched by the wound (the scatter is
        // the caller's own arm, never deflectBy's).
        ProjectileEntity charge = manager.launchFireball(
                new Position(0.0, 12.0, 0.0), 0.0, 0.0, 1.0, 0);
        assertEquals(-1, charge.throwerId(), "the dispensed charge carries no shooter");
        manager.woundFromExplosion(charge, 4242, 0.0, 0.6, 0.8);
        assertEquals(-1, charge.throwerId(),
                "the override refuses the wound: no new shooter");
        assertEquals(0.0, charge.velocityX(), 1e-9, "the wound never writes");
        assertEquals(0.0, charge.velocityY(), 1e-9);
        assertEquals(0.0, charge.velocityZ(), 1e-9);
        assertEquals(1, events.deflections,
                "the refused wound fires no wire deflection");

        // The wither skull: the same refusal (WitherSkullEntity lines
        // 94-96) — the skull's flight is its wither's alone.
        ProjectileEntity skull = manager.launchWitherSkull(888,
                new Position(0.0, 14.0, 0.0), 10.0, 0.0, 0.0, false, 0);
        assertEquals(888, skull.throwerId(), "the wither shot it");
        manager.woundFromExplosion(skull, 4242, 0.0, 0.6, 0.8);
        assertEquals(888, skull.throwerId(),
                "the override refuses the wound: the wither keeps its skull");
        assertEquals(0.0, skull.velocityX(), 1e-9, "the wound never writes");
        assertEquals(0.0, skull.velocityY(), 1e-9);
        assertEquals(0.0, skull.velocityZ(), 1e-9);
    }

    @Test
    void theDeflectedFireballCanStrikeItsShooterPastTheWindow() {
        // The fireball family's own immunity window (ProjectileEntity line
        // 119: inAirTicks >= 25 — five times the thrown family's 5): the
        // shooter is safe for the first 25 air ticks, then its own fireball
        // (the deflected one) can strike it.
        RiggedHits hits = new RiggedHits();
        RecordingCombat combat = new RecordingCombat();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, combat, new FxManager(), new Random(13), 5_200);

        MobEntity ghast = new MobEntity(777, MobType.GHAST,
                new Position(0, 10, 0), new Random(14), solidWorld());
        hits.mob = ghast;
        ProjectileEntity fireball = manager.launchGhastFireball(777,
                new Position(0, 10, 0), 1.0, 0.0, 0.0, 0);

        // The first 25 ticks: the shooter is immune to its own fireball.
        for (int i = 0; i < ProjectileManager.FIREBALL_THROWER_IMMUNITY_TICKS; i++) {
            manager.tick(java.util.List.of());
        }
        assertTrue(combat.events.isEmpty(),
                "the shooter is immune inside its own window (25 ticks)");
        assertTrue(manager.byId(fireball.entityId()) != null, "still airborne");

        // The 26th tick: the fireball strikes its own shooter (the
        // deflected-fireball-kills-the-ghast window).
        manager.tick(java.util.List.of());
        assertEquals(List.of("mob"), combat.events,
                "past 25 air ticks the shooter is fair game");
    }

    @Test
    void theThrownFamilyKeepsItsFiveTickWindow() {
        // ThrownEntity line 165: the shard family's own window is 5 — a
        // snowball its thrower walks into at tick 6 lands the hit.
        RiggedHits hits = new RiggedHits();
        RecordingCombat combat = new RecordingCombat();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, combat, new FxManager(), new Random(15), 5_300);

        MobEntity thrower = new MobEntity(778, MobType.SNOW_GOLEM,
                new Position(0, 10, 0), new Random(16), solidWorld());
        hits.mob = thrower;
        manager.launch(ProjectileEntity.Kind.SNOWBALL, 778,
                new Position(0, 10, 0), 0, 0, 1.5, 0);
        for (int i = 0; i < 5; i++) {
            manager.tick(java.util.List.of());
        }
        assertTrue(combat.events.isEmpty(), "the 5-tick window holds");
        manager.tick(java.util.List.of());
        assertEquals(List.of("mob"), combat.events, "past 5 the shard can land");
    }

    @Test
    void theKnockbackPunchPushesTheDeflectedFireball() {
        // PlayerEntity.attack lines 983-987 (Slice TF-7c): the landed
        // punch with a Knockback level (the enchant or the sprint's +1)
        // ADDS the push to the look-vector velocity — the reference's
        // addVelocity arm AFTER takeDamage's set. Yaw 0 faces +z: the
        // push is (0, 0.1, i * 0.5).
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new RiggedHits(), new RecordingCombat(),
                new FxManager(), new Random(17), 5_400);
        Events events = new Events();
        manager.addListener(events);

        ProjectileEntity fireball = manager.launchGhastFireball(777,
                new Position(0.0, 10.0, 0.0), 1.0, 0.0, 0.0, 0);
        // The punch straight up (pitch -90): the look vector (0, 1, 0),
        // the level 2 push along +z on top.
        assertTrue(manager.deflect(fireball, 4242, 0.0, 1.0, 0.0, 2, 0.0));
        assertEquals(0.0, fireball.velocityX(), 1e-9, "the push keeps the look's x");
        assertEquals(1.0 + 0.1, fireball.velocityY(), 1e-9, "the flat 0.1 y term");
        assertEquals(2 * 0.5, fireball.velocityZ(), 1e-9, "cos(0) * 2 * 0.5");
        // The acceleration stays the deflection's own (look * 0.1 — the
        // reference's takeDamage write, untouched by the push).
        assertEquals(0.10, fireball.accelerationY(), 1e-9);
        assertEquals(0.0, fireball.accelerationZ(), 1e-9);
        assertEquals(1, events.deflections, "one wire publish for the whole punch");

        // The level-0 punch: the plain look-vector deflection alone.
        ProjectileEntity plain = manager.launchGhastFireball(777,
                new Position(0.0, 10.0, 0.0), 1.0, 0.0, 0.0, 0);
        assertTrue(manager.deflect(plain, 4242, 0.0, 0.6, 0.8, 0, 0.0));
        assertEquals(0.6, plain.velocityY(), 1e-9, "no push without the level");
        assertEquals(0.8, plain.velocityZ(), 1e-9);

        // The push's heading (yaw 90 faces +x... the reference's -sin(yaw)
        // pair): the level-1 punch at yaw 90 pushes -x with the flat y.
        ProjectileEntity turned = manager.launchGhastFireball(777,
                new Position(0.0, 10.0, 0.0), 1.0, 0.0, 0.0, 0);
        assertTrue(manager.deflect(turned, 4242, 0.0, 1.0, 0.0, 1,
                Math.toRadians(90.0)));
        assertEquals(-0.5, turned.velocityX(), 1e-9, "-sin(90) * 1 * 0.5");
        assertEquals(0.0, turned.velocityZ(), 1e-9, "cos(90) * 1 * 0.5");
    }
}
