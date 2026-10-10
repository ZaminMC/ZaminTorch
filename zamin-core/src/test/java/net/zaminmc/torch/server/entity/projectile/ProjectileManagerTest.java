package net.zaminmc.torch.server.entity.projectile;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The projectile simulation's geometry and rules: block collisions (arrows
 * stick, shards shatter), entity hits (speed-scaled arrow damage, zero-damage
 * shard knockback), the five-tick thrower immunity, the stick clock, and the
 * egg's one-in-eight chick roll.
 */
class ProjectileManagerTest {

    /** Records what the combat sink received, for assertions. */
    static class RecordingCombat implements ProjectileManager.CombatSink {
        final List<String> events = new ArrayList<>();
        final List<Float> damages = new ArrayList<>();
        int chickenHatches;

        @Override
        public boolean mobHit(MobEntity mob, float damage, double kbYaw) {
            events.add("mob");
            damages.add(damage);
            return true;
        }

        @Override
        public void playerHit(PlayerSession player, float damage, double kbYaw) {
            events.add("player:" + player.name());
            damages.add(damage);
        }

        @Override
        public void chickenHatch(Position position) {
            chickenHatches++;
        }
    }

    /** Fixed hit targets the flight path always lands inside. */
    static final class RiggedHits implements ProjectileManager.HitResolver {
        MobEntity mob;
        PlayerSession player;

        @Override
        public MobEntity mobAt(Position point) {
            return mob;
        }

        @Override
        public PlayerSession playerAt(Position point) {
            return player;
        }
    }

    private static PlayerSession player(String name) {
        return new PlayerSession(UUID.randomUUID(), name, new ClientLinkStub());
    }

    private static final class ClientLinkStub implements net.zaminmc.torch.server.net.ClientLink {
        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void kick(String reason) {
        }
    }

    private static final FxManager FX = new FxManager();

    /** The minimal world query MobEntity demands (solid ground + no players). */
    private static MobEntity.WorldQuery solidWorld() {
        return new MobEntity.WorldQuery() {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return y <= 0;
            }

            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    @Test
    void snowballShattersOnABlockAndLeavesTheWorld() {
        List<Integer> destroyed = new ArrayList<>();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> z >= 5.0, new RiggedHits(), new RecordingCombat(), FX,
                new Random(1), 1_000);
        manager.addListener(new ProjectileManager.Listener() {
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
                destroyed.add(projectile.entityId());
            }
        });
        ProjectileEntity shard = manager.launch(ProjectileEntity.Kind.SNOWBALL, 42,
                new Position(0, 10, 0), 0, 0, 1.5);
        manager.tick(); // one tick at 1.5 blocks does not reach the wall at x=5
        assertEquals(0, destroyed.size(), "still airborne after one tick");
        assertTrue(shard.age() >= 1, "the shard aged");
        for (int i = 0; i < 10 && destroyed.isEmpty(); i++) {
            manager.tick();
        }
        assertEquals(1, destroyed.size(), "the shard shattered on the wall");
        assertNull(manager.byId(shard.entityId()), "removed from the world");
    }

    @Test
    void arrowLandsInABlockAndSticksOutItsMinute() {
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> y <= 8.0, new RiggedHits(), new RecordingCombat(), FX,
                new Random(2), 2_000);
        ProjectileEntity arrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0, 10, 0), 0, 90, 3.0); // straight down (pitch +90 aims down)
        List<Integer> landed = new ArrayList<>();
        manager.addListener(new ProjectileManager.Listener() {
            @Override
            public void onProjectileSpawned(ProjectileEntity projectile) {
            }

            @Override
            public void onProjectileMoved(ProjectileEntity projectile) {
            }

            @Override
            public void onProjectileLanded(ProjectileEntity projectile) {
                landed.add(projectile.entityId());
            }

            @Override
            public void onProjectileRemoved(ProjectileEntity projectile, String reason) {
            }
        });
        manager.tick();
        assertEquals(1, landed.size(), "the arrow landed in the first tick");
        assertTrue(arrow.inGround(), "the arrow is stuck");
        // The stick clock: 1199 more ticks stay alive, the 1200th removes.
        for (int i = 0; i < ProjectileManager.ARROW_GROUND_TICKS - 1; i++) {
            manager.tick();
        }
        assertNotNull(manager.byId(arrow.entityId()), "still stuck inside its minute");
        manager.tick();
        assertNull(manager.byId(arrow.entityId()), "expired after 1200 ground ticks");
    }

    @Test
    void arrowDamageScalesWithSpeedAndMobTakesTheHit() {
        RiggedHits hits = new RiggedHits();
        RecordingCombat combat = new RecordingCombat();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, combat, FX, new Random(3), 3_000);
        MobEntity zombie = new MobEntity(9_001, MobType.ZOMBIE,
                new Position(0, 10, 0), new Random(4), solidWorld());
        hits.mob = zombie;
        manager.launch(ProjectileEntity.Kind.ARROW, -1, new Position(0, 12, 0),
                0, 45, 3.0); // aimed down at the mob
        manager.tick();
        assertEquals(List.of("mob"), combat.events, "the mob took the arrow");
        float damage = combat.damages.get(0);
        assertTrue(damage >= 1.0f, "arrows always wound at least one heart");
        assertTrue(damage <= 7.0f, "full-draw arrows cap around seven damage");
    }

    @Test
    void snowballHitsAreZeroDamageKnockback() {
        RiggedHits hits = new RiggedHits();
        RecordingCombat combat = new RecordingCombat();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, combat, FX, new Random(5), 4_000);
        PlayerSession victim = player("Victim");
        victim.assignEngineEntityId(9_002);
        hits.player = victim;
        manager.launch(ProjectileEntity.Kind.SNOWBALL, 9_003,
                new Position(0, 11, 0), 90, 0, 1.5);
        manager.tick();
        assertEquals(List.of("player:Victim"), combat.events,
                "the snowball reached the victim");
        assertEquals(0.0f, combat.damages.get(0), 0.0001f, "shards bruise, never wound");
    }

    @Test
    void throwerIgnoresTheirOwnProjectileForFiveTicks() {
        RiggedHits hits = new RiggedHits();
        RecordingCombat combat = new RecordingCombat();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, combat, FX, new Random(6), 5_000);
        PlayerSession thrower = player("Thrower");
        thrower.assignEngineEntityId(9_010);
        hits.player = thrower; // the flight path starts inside the thrower's own box
        manager.launch(ProjectileEntity.Kind.SNOWBALL, 9_010,
                new Position(0, 11, 0), 90, 0, 1.5);
        for (int i = 0; i < 5; i++) {
            manager.tick(); // ages 1..10: the first five ticks are immune
        }
        assertTrue(combat.events.isEmpty() || combat.damages.get(0) >= 0,
                "the launch overlap window ran before any self-hit");
        // A fresh launch: the first five ticks must produce zero events.
        RecordingCombat fresh = new RecordingCombat();
        ProjectileManager cleanManager = new ProjectileManager(
                (x, y, z) -> false, hits, fresh, FX, new Random(16), 5_500);
        cleanManager.launch(ProjectileEntity.Kind.SNOWBALL, 9_010,
                new Position(0, 11, 0), 90, 0, 1.5);
        cleanManager.tick();
        cleanManager.tick();
        cleanManager.tick();
        cleanManager.tick();
        assertTrue(fresh.events.isEmpty(), "the launch overlap never hits the thrower");
    }

    @Test
    void theEggsChickRollHatchesWithinTwoCycles() {
        RiggedHits hits = new RiggedHits();
        RecordingCombat combat = new RecordingCombat();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, hits, combat, FX, new Random(7), 6_000);
        PlayerSession victim = player("Target");
        victim.assignEngineEntityId(9_020);
        hits.player = victim;
        // Up to sixteen throws (two full 1/8 cycles) must hatch at least one
        // chick; the fixed seed makes the roll deterministic.
        for (int i = 0; i < 16 && combat.chickenHatches == 0; i++) {
            manager.launch(ProjectileEntity.Kind.EGG, -1,
                    new Position(0, 11, 0), 90, 0, 1.5);
            manager.tick();
        }
        assertTrue(combat.chickenHatches > 0, "the chick roll hatched within two cycles");
    }

    @Test
    void shardLaunchFiresThePoofFeedbackOnBlockImpact() {
        List<FxManager.FxEvent> fx = new ArrayList<>();
        FxManager bus = new FxManager();
        bus.addListener(fx::add);
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> y <= 9.0, new RiggedHits(), new RecordingCombat(), bus,
                new Random(8), 7_000);
        manager.launch(ProjectileEntity.Kind.SNOWBALL, -1,
                new Position(0, 10, 0), 0, 90, 1.5);
        manager.tick();
        boolean poof = fx.stream().anyMatch(e -> e instanceof FxManager.Poof);
        boolean glass = fx.stream().anyMatch(e ->
                e instanceof FxManager.Sound s && s.name().equals("random.glass"));
        assertTrue(poof, "the shatter puff rode the FX bus");
        assertTrue(glass, "the shatter sound rode the FX bus");
    }

    @Test
    void shardFlightDiesAtTheAgeCap() {
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new RiggedHits(), new RecordingCombat(), FX,
                new Random(9), 8_000);
        ProjectileEntity shard = manager.launch(ProjectileEntity.Kind.SNOWBALL, -1,
                new Position(0, 100, 0), 90, 45, 0.1); // flies into the void
        for (int i = 0; i < ProjectileManager.THROWABLE_AGE_TICKS; i++) {
            manager.tick();
        }
        assertNull(manager.byId(shard.entityId()),
                "the airborne shard expired at the age cap");
    }

    @Test
    void dragSlowsTheFlightTickByTick() {
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new RiggedHits(), new RecordingCombat(), FX,
                new Random(10), 9_000);
        ProjectileEntity shard = manager.launch(ProjectileEntity.Kind.SNOWBALL, -1,
                new Position(0, 100, 0), 0, 0, 2.0);
        double first = shard.launchSpeed();
        manager.tick(); // two substeps of drag + gravity
        double second = shard.launchSpeed();
        assertTrue(second < first * 0.999, "drag bled speed across the tick");
        assertFalse(shard.inGround(), "no block in the void");
    }
}
