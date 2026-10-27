package net.zaminmc.torch.server.entity.projectile;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.fireworks.FireworksEntity;
import net.zaminmc.torch.server.fireworks.FireworksSystem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spawnable pair (Slice 65): the bottle o' enchanting
 * (ExperienceBottleEntity 1:1 — the 0.07 gravity, the lobbed launch, the
 * 2002 splash + the roundSize orb burst on ANY impact with no damage
 * ever) and the firework rocket (FireworksEntity 1:1 — the near-zero
 * launch drift, the 1.15/0.04 flight, the 10..22-tick fuse, the burst
 * event 17 at the fuse's end).
 */
class FireworksAndBottleTest {

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

    /** The bottle's recording sink: the burst points. */
    private static final class BottleSink implements ProjectileManager.XpBottleSink {
        final List<Position> bursts = new ArrayList<>();
        final List<Integer> throwers = new ArrayList<>();

        @Override
        public void burst(double x, double y, double z, int throwerId) {
            bursts.add(new Position(x, y, z));
            throwers.add(throwerId);
        }
    }

    /** The rigged hits: one fixed mob the walk always finds. */
    private static final class RiggedHits implements ProjectileManager.HitResolver {
        MobEntity mob;

        @Override public MobEntity mobAt(Position point, int dimension) {
            return mob;
        }

        @Override public net.zaminmc.torch.server.player.PlayerSession playerAt(Position point, int dimension) {
            return null;
        }
    }

    /** The no-op combat sink (the bottle must never reach it). */
    private static final class NoCombat implements ProjectileManager.CombatSink {
        @Override
        public boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId) {
            throw new AssertionError("the bottle never wounds");
        }

        @Override
        public void playerHit(net.zaminmc.torch.server.player.PlayerSession player,
                              float damage, double kbYaw) {
            throw new AssertionError("the bottle never wounds");
        }

        @Override
        public void chickenHatch(Position position) {
        }
    }

    private static MobEntity.WorldQuery openWorld() {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return false;
            }

            @Override public Position nearestPlayer(double x, double y, double z,
                                                    double range) {
                return null;
            }
        };
    }

    @Test
    void theBottleCarriesItsOwnGravity() {
        assertEquals(0.07, ProjectileManager.gravityOf(ProjectileEntity.Kind.EXPERIENCE_BOTTLE),
                "ExperienceBottleEntity.getGravity line 24");
    }

    @Test
    void theBottleBurstsOnABlockWithoutAnyWound() {
        // The solid wall at x >= 5: the bottle flies east (0.7 power) until
        // the block arm catches it — the burst + the removal, and the
        // combat sink never runs (the reference's onCollision calls no
        // takeDamage: no damage, no knockback).
        BottleSink sink = new BottleSink();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> x >= 5.0, new RiggedHits(), new NoCombat(),
                new net.zaminmc.torch.server.fx.FxManager(), new Random(64L), 7_000);
        Events events = new Events();
        manager.addListener(events);
        manager.setXpBottleSink(sink);
        ProjectileEntity bottle = manager.launchVector(
                ProjectileEntity.Kind.EXPERIENCE_BOTTLE, 4242,
                new Position(0.5, 10.0, 0.5), 1.0, 0.0, 0.0, 0.7, 1.0, 0);
        assertTrue(bottle != null, "the unit harness has no admission gate");
        for (int i = 0; i < 30 && sink.bursts.isEmpty(); i++) {
            manager.tick(java.util.List.of());
        }
        assertEquals(1, sink.bursts.size(), "the burst landed at the wall");
        assertTrue(sink.bursts.get(0).x() >= 4.0, "the burst sits at the impact");
        assertEquals(4242, sink.throwers.get(0), "the burst carries the thrower's id");
        assertTrue(events.removals.contains("hit"), "the bottle died at the block");
        assertEquals(0, manager.all().size(), "the registry emptied");
    }

    @Test
    void theBottleBurstsOnABodyAndTheBodySurvives() {
        // A mob stands in the flight path: the burst lands, no damage, no
        // knockback — the struck body never even notices (the reference's
        // onCollision never calls takeDamage).
        BottleSink sink = new BottleSink();
        RiggedHits hits = new RiggedHits();
        MobEntity bystander = new MobEntity(9600, MobType.COW,
                new Position(0.5, 4.0, 0.5), new Random(65L), openWorld());
        hits.mob = bystander;
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, new NoCombat(),
                new net.zaminmc.torch.server.fx.FxManager(), new Random(66L), 7_100);
        Events events = new Events();
        manager.addListener(events);
        manager.setXpBottleSink(sink);
        manager.launchVector(ProjectileEntity.Kind.EXPERIENCE_BOTTLE, 4242,
                new Position(0.5, 10.0, 0.5), 0.0, -1.0, 0.0, 0.7, 1.0, 0);
        for (int i = 0; i < 30 && sink.bursts.isEmpty(); i++) {
            manager.tick(java.util.List.of());
        }
        assertEquals(1, sink.bursts.size(), "the burst landed");
        assertTrue(events.removals.contains("hit"), "the bottle died at the body");
        assertEquals(bystander.maxHealth(), bystander.health(), 1e-6f,
                "the struck body never lost a heart");
    }

    // ------------------------------------------------------------------ fireworks

    /** The recording fireworks listener. */
    private static final class FireworksEvents implements FireworksSystem.Listener {
        int launched;
        int velocitySyncs;
        int bursts;
        int removed;

        @Override public void onFireworksLaunched(FireworksEntity rocket) {
            launched++;
        }

        @Override public void onFireworksVelocity(FireworksEntity rocket) {
            velocitySyncs++;
        }

        @Override public void onFireworksBurst(FireworksEntity rocket) {
            bursts++;
        }

        @Override public void onFireworksRemoved(FireworksEntity rocket) {
            removed++;
        }
    }

    /** The recording surface: the age-0 launch sound. */
    private static final class LaunchSounds implements FireworksSystem.Surface {
        final List<Position> heard = new ArrayList<>();

        @Override
        public void launchSound(Position at) {
            heard.add(at);
        }
    }

    @Test
    void theRocketFliesTheFuseAndBursts() {
        LaunchSounds sounds = new LaunchSounds();
        FireworksSystem system = new FireworksSystem(sounds, new Random(67L), 8_000);
        FireworksEvents events = new FireworksEvents();
        system.addListener(events);
        net.zaminmc.torch.item.ItemStack payload =
                net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.FIREWORKS, 1);
        FireworksEntity rocket = system.launch(new Position(10.0, 64.0, 10.0), payload);
        assertEquals(1, events.launched, "the spawn went out");
        assertTrue(rocket.maxAge() >= 10 && rocket.maxAge() <= 22,
                "the fuse's plain band 10 + rand(6) + rand(7): " + rocket.maxAge());
        double startY = rocket.position().y();
        // The flight: every tick the horizontal grows 1.15x, the vertical
        // gains 0.04, the position moves by the product.
        double expectedVy = FireworksEntity.LAUNCH_LIFT;
        double expectedY = 64.0;
        int ticks = 0;
        while (!rocket.expired() && ticks < 100) {
            system.tick();
            ticks++;
            expectedVy += FireworksEntity.VERTICAL_GAIN;
            expectedY += expectedVy;
        }
        assertTrue(rocket.expired(), "the fuse burned out");
        assertEquals(expectedY, rocket.position().y(), 1e-9,
                "the 0.04-gain flight law (the lift's own start)");
        assertEquals(1, sounds.heard.size(), "the launch sound played once at age 0");
        assertEquals(1, events.bursts, "the burst event 17 fired");
        assertEquals(1, events.removed, "the body left the world");
        assertEquals(0, system.size(), "the registry emptied");
        assertTrue(events.velocitySyncs >= 1,
                "the tracker's 10-tick velocity cadence ran at least once");
    }

    @Test
    void theRocketLaunchesAtTheClickedFacesPoint() {
        // FireworksItem.useOn: the rocket's birth is the clicked face's own
        // point (pos + the cursor fractions), not the block center.
        FireworksSystem system = new FireworksSystem(new LaunchSounds(),
                new Random(68L), 8_100);
        net.zaminmc.torch.item.ItemStack payload =
                net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.FIREWORKS, 1);
        FireworksEntity rocket = system.launch(new Position(100.75, 65.0, -20.25), payload);
        assertEquals(100.75, rocket.position().x(), 1e-9);
        assertEquals(65.0, rocket.position().y(), 1e-9);
        assertEquals(-20.25, rocket.position().z(), 1e-9);
        // The launch velocity: the near-zero drift (gaussian 0.001 sigma,
        // |v| < 0.01 with overwhelming odds) and the 0.05 lift.
        assertEquals(FireworksEntity.LAUNCH_LIFT, rocket.velocityY(), 1e-9);
        assertTrue(Math.abs(rocket.velocityX()) < 0.01, "the horizontal drift is near zero");
        assertTrue(Math.abs(rocket.velocityZ()) < 0.01, "the horizontal drift is near zero");
        assertFalse(rocket.expired(), "freshly launched");
    }

    /**
     * THE FLIGHT BYTE NAMES THE FUSE BAND (the ctor lines 33-46, 1:1):
     * {@code i = 1 + Flight} then {@code 10 * i + rand(6) + rand(7)} — a
     * bare rocket rolls the plain 10..21 band, a Flight byte shifts the
     * band by tens, and a NON-BYTE Flight row reads 0 (the reference's
     * getByte cast failure — the plain band again).
     */
    @Test
    void theFlightByteNamesTheFuseBand() {
        // The bare rocket: the plain band 10 + rand(6) + rand(7) = 10..21.
        for (int seed = 0; seed < 40; seed++) {
            FireworksSystem system = new FireworksSystem(new LaunchSounds(),
                    new Random(seed * 31L + 7L), 9_000 + seed);
            net.zaminmc.torch.item.ItemStack bare =
                    net.zaminmc.torch.item.ItemStack.of(
                            net.zaminmc.torch.server.item.BuiltinItems.FIREWORKS, 1);
            FireworksEntity rocket = system.launch(new Position(0, 64, 0), bare);
            assertTrue(rocket.maxAge() >= 10 && rocket.maxAge() <= 21,
                    "the bare band 10..21: " + rocket.maxAge());
        }
        // Flight 2: the band 10*3 + rand(6) + rand(7) = 30..41.
        for (int seed = 0; seed < 40; seed++) {
            FireworksSystem system = new FireworksSystem(new LaunchSounds(),
                    new Random(seed * 37L + 11L), 9_500 + seed);
            net.zaminmc.torch.item.ItemStack payload =
                    net.zaminmc.torch.item.ItemStack.of(
                            net.zaminmc.torch.server.item.BuiltinItems.FIREWORKS, 1)
                            .withFireworks(java.util.Map.of("Flight", (byte) 2));
            FireworksEntity rocket = system.launch(new Position(0, 64, 0), payload);
            assertTrue(rocket.maxAge() >= 30 && rocket.maxAge() <= 41,
                    "the Flight 2 band 30..41: " + rocket.maxAge());
        }
        // A NON-BYTE Flight row (the reference's getByte reads 0 for the
        // cast failure): the plain band again.
        for (int seed = 0; seed < 40; seed++) {
            FireworksSystem system = new FireworksSystem(new LaunchSounds(),
                    new Random(seed * 41L + 13L), 9_900 + seed);
            net.zaminmc.torch.item.ItemStack payload =
                    net.zaminmc.torch.item.ItemStack.of(
                            net.zaminmc.torch.server.item.BuiltinItems.FIREWORKS, 1)
                            .withFireworks(java.util.Map.of("Flight", 2));
            FireworksEntity rocket = system.launch(new Position(0, 64, 0), payload);
            assertTrue(rocket.maxAge() >= 10 && rocket.maxAge() <= 21,
                    "the non-byte Flight row reads 0 — the plain band: " + rocket.maxAge());
        }
        // An empty Fireworks compound: the getCompound finds it but the
        // Flight row is absent — getByte answers 0, the plain band.
        for (int seed = 0; seed < 20; seed++) {
            FireworksSystem system = new FireworksSystem(new LaunchSounds(),
                    new Random(seed * 43L + 17L), 9_950 + seed);
            net.zaminmc.torch.item.ItemStack payload =
                    net.zaminmc.torch.item.ItemStack.of(
                            net.zaminmc.torch.server.item.BuiltinItems.FIREWORKS, 1)
                            .withFireworks(java.util.Map.of("Explosions", java.util.List.of()));
            FireworksEntity rocket = system.launch(new Position(0, 64, 0), payload);
            assertTrue(rocket.maxAge() >= 10 && rocket.maxAge() <= 21,
                    "the absent Flight row reads 0 — the plain band: " + rocket.maxAge());
        }
    }
}
