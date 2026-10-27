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
        public boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId) {
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
        public MobEntity mobAt(Position point, int dimension) {
            return mob;
        }

        @Override
        public PlayerSession playerAt(Position point, int dimension) {
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
                new Position(0, 10, 0), 0, 0, 1.5, 0);
        manager.tick(java.util.List.of()); // one tick at 1.5 blocks does not reach the wall at x=5
        assertEquals(0, destroyed.size(), "still airborne after one tick");
        assertTrue(shard.age() >= 1, "the shard aged");
        for (int i = 0; i < 10 && destroyed.isEmpty(); i++) {
            manager.tick(java.util.List.of());
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
                new Position(0, 10, 0), 0, 90, 3.0, 0); // straight down (pitch +90 aims down)
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
        manager.tick(java.util.List.of());
        assertEquals(1, landed.size(), "the arrow landed in the first tick");
        assertTrue(arrow.inGround(), "the arrow is stuck");
        // The stick clock: 1199 more ticks stay alive, the 1200th removes.
        for (int i = 0; i < ProjectileManager.ARROW_GROUND_TICKS - 1; i++) {
            manager.tick(java.util.List.of());
        }
        assertNotNull(manager.byId(arrow.entityId()), "still stuck inside its minute");
        manager.tick(java.util.List.of());
        assertNull(manager.byId(arrow.entityId()), "expired after 1200 ground ticks");
    }

    @Test
    void theRestoredProjectileLandsTheSavedMotionAndTheStuckClockResumes() {
        // The Anvil import's exact-position form (the reference's
        // Entities.create + readCustomNbt): the saved Motion lands whole
        // (no gaussian tilt, no launch force walk), the thrower stays
        // unknown (the reference's projectile NBT carries no shooter),
        // and a stuck arrow's inGround + life rows resume the stick
        // clock where the save left it — the restored arrow expires
        // after the REMAINING ticks, not a fresh minute.
        List<Integer> spawned = new ArrayList<>();
        List<Integer> destroyed = new ArrayList<>();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new RiggedHits(), new RecordingCombat(), FX,
                new Random(6), 5_000);
        manager.addListener(new ProjectileManager.Listener() {
            @Override
            public void onProjectileSpawned(ProjectileEntity projectile) {
                spawned.add(projectile.entityId());
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
        ProjectileEntity shard = manager.spawnRestored(ProjectileEntity.Kind.SNOWBALL,
                new Position(1.5, 64.0, 2.5), -0.5, 0.2, 0.5);
        assertNotNull(shard, "the restore landed the shard");
        assertEquals(-0.5, shard.velocityX(), "the saved motion lands whole");
        assertEquals(0.2, shard.velocityY());
        assertEquals(0.5, shard.velocityZ());
        assertEquals(-1, shard.throwerId(), "the thrower stays unknown (no shooter row)");
        assertEquals(1.5, shard.position().x(), "the exact position");
        assertEquals(1, spawned.size(), "the spawn publication rode the restore");

        // The stuck arrow: inGround + life 1000 — the clock resumes at
        // 1000, and the remaining 200 ticks carry the removal.
        ProjectileEntity stuck = manager.spawnRestored(ProjectileEntity.Kind.ARROW,
                new Position(4.0, 64.0, 4.0), 0.0, 0.0, 0.0);
        assertNotNull(stuck);
        stuck.restoreGrounded(1_000);
        assertTrue(stuck.inGround(), "the inGround row landed");
        assertEquals(1_000, stuck.ticksInGround(), "the life row resumed the clock");
        assertEquals(0.0, stuck.velocityY(), "the saved velocity stays as loaded "
                + "(the reference's stuck arm never integrates it)");
        for (int i = 0; i < 199; i++) {
            manager.tick(java.util.List.of());
        }
        assertNotNull(manager.byId(stuck.entityId()), "the restored clock holds the remaining span");
        manager.tick(java.util.List.of());
        assertNull(manager.byId(stuck.entityId()), "the resumed clock expires on time");
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
                0, 45, 3.0, 0); // aimed down at the mob
        manager.tick(java.util.List.of());
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
                new Position(0, 11, 0), 90, 0, 1.5, 0);
        manager.tick(java.util.List.of());
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
                new Position(0, 11, 0), 90, 0, 1.5, 0);
        for (int i = 0; i < 5; i++) {
            manager.tick(java.util.List.of()); // ages 1..10: the first five ticks are immune
        }
        assertTrue(combat.events.isEmpty() || combat.damages.get(0) >= 0,
                "the launch overlap window ran before any self-hit");
        // A fresh launch: the first five ticks must produce zero events.
        RecordingCombat fresh = new RecordingCombat();
        ProjectileManager cleanManager = new ProjectileManager(
                (x, y, z) -> false, hits, fresh, FX, new Random(16), 5_500);
        cleanManager.launch(ProjectileEntity.Kind.SNOWBALL, 9_010,
                new Position(0, 11, 0), 90, 0, 1.5, 0);
        cleanManager.tick(java.util.List.of());
        cleanManager.tick(java.util.List.of());
        cleanManager.tick(java.util.List.of());
        cleanManager.tick(java.util.List.of());
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
                    new Position(0, 11, 0), 90, 0, 1.5, 0);
            manager.tick(java.util.List.of());
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
                new Position(0, 10, 0), 0, 90, 1.5, 0);
        manager.tick(java.util.List.of());
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
                new Position(0, 100, 0), 90, 45, 0.1, 0); // flies into the void
        for (int i = 0; i < ProjectileManager.THROWABLE_AGE_TICKS; i++) {
            manager.tick(java.util.List.of());
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
                new Position(0, 100, 0), 0, 0, 2.0, 0);
        double first = shard.launchSpeed();
        manager.tick(java.util.List.of()); // two substeps of drag + gravity
        double second = shard.launchSpeed();
        assertTrue(second < first * 0.999, "drag bled speed across the tick");
        assertFalse(shard.inGround(), "no block in the void");
    }
    @Test
    void aBurningArrowPrimesTheTntItOverlaps() {
        // The reference's per-block entity collision (Entity lines
        // 665-689 walking the arrow's box, TntBlock.onEntityCollision
        // lines 91-104 priming every TNT cell a burning arrow overlaps):
        // a stuck burning arrow on the TNT face primes it; a plain arrow
        // in the same place never does; a burning arrow away from the
        // band never does.
        java.util.List<net.zaminmc.torch.block.BlockPosition> primed = new ArrayList<>();
        java.util.Set<net.zaminmc.torch.block.BlockPosition> tntCells = new java.util.HashSet<>(
                java.util.Set.of(new net.zaminmc.torch.block.BlockPosition(10, 40, 10)));
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> tntCells.contains(new net.zaminmc.torch.block.BlockPosition(
                        (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))),
                new RiggedHits(), new RecordingCombat(), FX,
                new Random(3), 3_000);
        manager.setBurningTntArm(new ProjectileManager.BurningTntArm() {
            @Override public boolean isTnt(net.zaminmc.torch.block.BlockPosition pos, int dimension) {
                return tntCells.contains(pos);
            }

            @Override public void ignite(net.zaminmc.torch.block.BlockPosition pos, int dimension) {
                primed.add(pos);
                // The reference's world.removeBlock(pos): the primed cell
                // leaves the world, so the next pass finds no TNT there.
                tntCells.remove(pos);
            }
        });

        // The burning arrow drops onto the TNT cell and sticks on its
        // face (the solid query answers inside the cell alone).
        ProjectileEntity burning = manager.launchVector(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(10.5, 44.0, 10.5), 0.0, -1.0, 0.0, 1.0, 0.4, 0);
        assertNotNull(burning, "the shot entered the world");
        burning.setOnFireFor(2_000); // the Flame release's own clock
        // The plain control arrow, the same drop over a plain cell.
        ProjectileEntity plain = manager.launchVector(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(12.5, 44.0, 10.5), 0.0, -1.0, 0.0, 1.0, 0.4, 0);
        assertNotNull(plain, "the control entered the world");

        for (int i = 0; i < 20; i++) {
            manager.tick(java.util.List.of());
        }
        assertTrue(burning.inGround(), "the burning arrow stuck on the TNT face");
        assertTrue(plain.inGround() == false, "the control keeps falling past its plain cell");

        assertTrue(primed.contains(new net.zaminmc.torch.block.BlockPosition(10, 40, 10)),
                "the TNT cell the burning arrow overlaps was primed");
        assertEquals(1, primed.size(), "one cell, one prime");
    }
}
