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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wither's skull (Slice 59 — WitherSkullEntity 1:1): the launch (the
 * shooter ctor's 0.4-sigma tilt into the 0.1 acceleration, the charged
 * flag riding the body), the flight (the charged blue's own 0.73 drag
 * against the family's 0.95, no gravity), and the hit law (the flat 8.0,
 * the kill's 5.0 heal to the shooting wither, the living victim's WITHER
 * II curse, and the 1.0F detonation where it lands — no fire).
 */
class WitherSkullTest {

    private static final class Events implements ProjectileManager.Listener {
        int spawned;
        final List<String> removals = new ArrayList<>();

        @Override public void onProjectileSpawned(ProjectileEntity projectile) {
            spawned++;
        }

        @Override public void onProjectileMoved(ProjectileEntity projectile) {
        }

        @Override public void onProjectileLanded(ProjectileEntity projectile) {
        }

        @Override public void onProjectileRemoved(ProjectileEntity projectile, String reason) {
            removals.add(reason);
        }

        @Override public void onProjectileDeflected(ProjectileEntity projectile) {
        }
    }

    /** The recording combat sink: the skull's own hit family. */
    private static final class SkullCombat implements ProjectileManager.CombatSink {
        final List<String> events = new ArrayList<>();
        float lastDamage = -1.0f;
        boolean mobDies;
        int healShooter = -1;
        MobEntity cursedMob;
        net.zaminmc.torch.server.player.PlayerSession cursedPlayer;

        @Override
        public boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId) {
            events.add("legacy-mob");
            return true;
        }

        @Override
        public boolean mobHit(ProjectileEntity projectile, MobEntity mob,
                              float damage, double kbYaw) {
            events.add("mob");
            lastDamage = damage;
            mob.hurt(damage); // the real sink's damage application
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
        public void chickenHatch(Position position) {
        }

        @Override
        public void skullKillHeal(int shooterId) {
            healShooter = shooterId;
        }

        @Override
        public void skullWitherCurse(MobEntity victim) {
            cursedMob = victim;
        }

        @Override
        public void skullWitherCurse(net.zaminmc.torch.server.player.PlayerSession victim) {
            cursedPlayer = victim;
        }
    }

    /** The rigged hits: one fixed mob the walk always finds. */
    private static final class RiggedHits implements ProjectileManager.HitResolver {
        MobEntity mob;

        @Override public MobEntity mobAt(Position point) {
            return mob;
        }

        @Override public net.zaminmc.torch.server.player.PlayerSession playerAt(Position point) {
            return null;
        }
    }

    /** The recording explosion sink. */
    private static final class Booms implements ProjectileManager.ExplosionSink {
        final List<Position> at = new ArrayList<>();
        final List<Float> power = new ArrayList<>();

        @Override
        public void explode(Position position, float power) {
            at.add(position);
            this.power.add(power);
        }
    }

    private static MobEntity.WorldQuery openWorld() {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return false;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    @Test
    void theLaunchRidesTheShooterCtorAndTheChargedFlag() {
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new RiggedHits(), new SkullCombat(),
                new FxManager(), new Random(61L), 6_000);
        Events events = new Events();
        manager.addListener(events);

        ProjectileEntity skull = manager.launchWitherSkull(777,
                new Position(0.0, 10.0, 0.0), 10.0, 0.0, 0.0, false);
        assertEquals(777, skull.throwerId(), "the wither shot it");
        assertFalse(skull.charged(), "the plain skull");
        assertEquals(1, events.spawned, "the wire saw the spawn");
        // The ctor law: the normalized*0.1 acceleration (the tilt is the
        // gaussian's own — the direction is exact east, the y/z noise
        // only bends the magnitude slightly off 0.1).
        double magnitude = Math.sqrt(skull.accelerationX() * skull.accelerationX()
                + skull.accelerationY() * skull.accelerationY()
                + skull.accelerationZ() * skull.accelerationZ());
        assertEquals(0.1, magnitude, 1e-6, "the 0.1 acceleration");
        assertTrue(skull.accelerationX() > 0.05, "the aim is east");

        ProjectileEntity blue = manager.launchWitherSkull(777,
                new Position(0.0, 10.0, 0.0), 0.0, 10.0, 0.0, true);
        assertTrue(blue.charged(), "the blue skull");
        // The skull never deflects (WitherSkullEntity.takeDamage's false).
        assertFalse(manager.deflect(blue, 4242, 0.0, 1.0, 0.0),
                "the skull carries no deflect surface");
    }

    @Test
    void theHitLandsEightTheCurseAndTheDetonation() {
        SkullCombat combat = new SkullCombat();
        Booms booms = new Booms();
        RiggedHits hits = new RiggedHits();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, combat,
                new FxManager(), new Random(62L), 6_100);
        Events events = new Events();
        manager.addListener(events);
        manager.setExplosionSink(booms);
        // The cow (10 hearts) survives the 8.0: a living victim takes the
        // curse, not the heal.
        MobEntity victim = new MobEntity(9507, MobType.COW,
                new Position(0.5, 4.0, 0.5), new Random(63L), openWorld());
        hits.mob = victim;

        manager.launchWitherSkull(777, new Position(0.0, 10.0, 0.0), 1.0, 0.0, 0.0, false);
        manager.tick();
        assertTrue(combat.events.contains("mob"), "the skull struck the body");
        assertEquals(8.0f, combat.lastDamage, 1e-6f, "the flat 8.0 hit");
        assertFalse(victim.dead(), "the cow lived");
        assertEquals(-1, combat.healShooter, "no kill, no heal");
        assertEquals(victim, combat.cursedMob, "the living victim took the curse");
        assertEquals(1, booms.at.size(), "the detonation fired");
        assertEquals(1.0f, booms.power.get(0), 1e-6f, "the power-1 blast");
        assertTrue(events.removals.contains("hit"), "the skull left the world");
    }

    @Test
    void theKillHealsTheShootingWither() {
        SkullCombat combat = new SkullCombat();
        RiggedHits hits = new RiggedHits();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, combat,
                new FxManager(), new Random(64L), 6_200);
        // A fragile victim: the 8.0 kills, the heal rides the shooter.
        MobEntity victim = new MobEntity(9508, MobType.CHICKEN,
                new Position(0.5, 4.0, 0.5), new Random(65L), openWorld());
        hits.mob = victim;
        manager.launchWitherSkull(778, new Position(0.0, 10.0, 0.0), 1.0, 0.0, 0.0, false);
        manager.tick();
        assertTrue(victim.dead(), "the chicken died to the 8.0");
        assertEquals(778, combat.healShooter, "the kill's 5.0 heal credit");
        // The curse only lands on a LIVING victim (the reference's
        // instanceof walk after the damage).
        assertNull(combat.cursedMob, "the dead body takes no curse");
    }

    @Test
    void theChargedBlueRidesItsOwnDrag() {
        // WitherSkullEntity.getDrag (line 28-30): the charged blue
        // drag-steps at 0.73 against the family's 0.95 — the manager's
        // per-kind step answers through the package's own law; the skull
        // joins the fireball family's gravity-free accelerating flight
        // (the launch's 0.4-sigma tilt bends each shot — the reference's
        // own spread, a straight-line assertion would be wrong).
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new RiggedHits(), new SkullCombat(),
                new FxManager(), new Random(66L), 6_300);
        ProjectileEntity plain = manager.launchWitherSkull(777,
                new Position(0.0, 10.0, 0.0), 1.0, 0.0, 0.0, false);
        ProjectileEntity blue = manager.launchWitherSkull(777,
                new Position(0.0, 10.0, 0.0), 1.0, 0.0, 0.0, true);
        assertEquals(ProjectileManager.FIREBALL_DRAG, ProjectileManager.dragOf(plain),
                1e-9, "the plain skull's 0.95");
        assertEquals(ProjectileManager.CHARGED_SKULL_DRAG, ProjectileManager.dragOf(blue),
                1e-9, "the charged blue's 0.73");
        assertEquals(0.0, ProjectileManager.gravityOf(ProjectileEntity.Kind.WITHER_SKULL),
                1e-9, "the skull flies gravity-free");
        // Five airborne ticks: both accelerate (the fireball family's
        // flight) and neither leaves the horizontal band the tilt allows
        // (no gravity term ever joins — the pure tilt is the only bend).
        double plainTilt = Math.abs(plain.accelerationY());
        for (int tick = 0; tick < 5; tick++) {
            manager.tick();
        }
        assertTrue(blue.velocityX() > 0.0, "the blue still accelerates east");
        assertTrue(plain.velocityX() > 0.0, "the plain still accelerates east");
        // No gravity: the y velocity converges on the tilt's own terminal
        // band a*drag/(1-drag) = a*19, never beyond it (a gravity term
        // would run away at 0.03 per tick on top of the tilt).
        assertTrue(Math.abs(plain.velocityY()) <= plainTilt * 19.0 + 1e-9,
                "the y term stays in the tilt's terminal band");
    }
}
