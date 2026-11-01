package net.zaminmc.torch.server.entity.projectile;

import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-018 FOLLOW-UP SEAT — the ENTITY-HIT arm of the
 * {@code random.bowhit} sound (docs/vanilla-conformance-audit.md, the
 * thirty-seventh pass's named follow-up; the site the pass-39 walk
 * discovered): the reference plays the bowhit at a successful entity hit.
 *
 * <p>THE REFERENCE LAW (ArrowEntity.tick, re-walked for this seat): the
 * entity-hit arm plays the sound at line 276 — INSIDE the
 * {@code takeDamage(...) == true} branch (a successful hit alone),
 * BEFORE the {@code remove()} — {@code this.playSound("random.bowhit",
 * 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F))}: the volume
 * 1.0, the pitch a fresh draw over the band [1.2/1.1, 1.2/0.9] ≈
 * [1.0909, 1.3333], at the arrow's own position, in the arrow's OWN
 * world. The FAILED-damage branch (line 280's else) plays NOTHING — the
 * arrow bounces (velocity *= -0.1, the yaw flip) in silence. The bowhit
 * is the ARROW family's own law: the reference's other projectile
 * families (SnowballEntity, EggEntity, the fireballs) play no such
 * name on their hits.</p>
 *
 * <p>THE ENGINE'S ROW (ProjectileManager's hit paths): {@code hitMob}
 * walks the ignite, the damage and the Punch impulse through the combat
 * sink — and plays NOTHING; the caller then removes the arrow. The same
 * silence in {@code hitPlayer} and {@code hitVehicle}. An arrow that
 * strikes a mob is SILENT in the engine, AUDIBLE in vanilla — the
 * second arm of VT-018 (the block stick, line 303, is PR #124's carried
 * arm).</p>
 *
 * <p>THE SEAT: an arrow dropped onto a zombie standing in the flight
 * path; the captured FX stream must carry the {@code random.bowhit}
 * Sound at the hit. RED on develop (the hit lands, the arrow leaves,
 * and the captured stream carries no event at all). The two guards:
 * the FAILED-damage construction (the sink's landed=false — the
 * reference's silent bounce branch) and the SNOWBALL construction (the
 * kind gate — the name is the arrow's alone).</p>
 */
class ArrowEntityHitBowhitSoundDiscoveryTest {

    /** Captures every Sound FX event for the assertions. */
    private static final class RecordingFx implements FxManager.Listener {
        final List<FxManager.Sound> sounds = new ArrayList<>();

        @Override
        public void onFx(FxManager.FxEvent event) {
            if (event instanceof FxManager.Sound sound) {
                sounds.add(sound);
            }
        }
    }

    /**
     * The zombie in the flight path: the hit resolver answers the body
     * while the flight's sample walks its box — the two-block column
     * under the drop, the reference's box scan at the engine's seat
     * granularity.
     */
    private static final class BoxedMobHits implements ProjectileManager.HitResolver {
        final MobEntity zombie;

        BoxedMobHits(MobEntity zombie) {
            this.zombie = zombie;
        }

        @Override
        public MobEntity mobAt(Position point, int dimension) {
            boolean inBox = point.x() >= 10.0 && point.x() < 11.0
                    && point.y() >= 40.0 && point.y() < 42.0
                    && point.z() >= 10.0 && point.z() < 11.0;
            return inBox ? zombie : null;
        }

        @Override
        public PlayerSession playerAt(Position point, int dimension) {
            return null;
        }
    }

    /** The combat sink's LANDED verdict: the hit took (takeDamage true). */
    private static final class LandingCombat implements ProjectileManager.CombatSink {
        int mobHits;

        @Override
        public boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId) {
            mobHits++;
            return true;
        }

        @Override
        public void playerHit(PlayerSession player, float damage, double kbYaw) {
        }

        @Override
        public void chickenHatch(Position position, int dimension) {
        }
    }

    /** The combat sink's FAILED verdict: the damage never took. */
    private static final class FailedCombat implements ProjectileManager.CombatSink {
        int mobHits;

        @Override
        public boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId) {
            mobHits++;
            return false;
        }

        @Override
        public void playerHit(PlayerSession player, float damage, double kbYaw) {
        }

        @Override
        public void chickenHatch(Position position, int dimension) {
        }
    }

    /** The minimal world query MobEntity demands (no ground, no players). */
    private static MobEntity.WorldQuery emptyWorld() {
        return new MobEntity.WorldQuery() {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return false;
            }

            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    private static MobEntity zombie() {
        return new MobEntity(9, MobType.ZOMBIE, new Position(10.5, 40.0, 10.5),
                new Random(3), emptyWorld());
    }

    @Test
    void theEntityHitPlaysTheBowhitSound() {
        FxManager fx = new FxManager();
        RecordingFx recorder = new RecordingFx();
        fx.addListener(recorder);
        LandingCombat combat = new LandingCombat();
        MobEntity zombie = zombie();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new BoxedMobHits(zombie), combat,
                fx, new Random(7), 5_000);

        ProjectileEntity arrow = manager.launchVector(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(10.5, 44.0, 10.5), 0.0, -1.0, 0.0, 1.0, 0.0, 0);
        assertTrue(arrow != null, "the arrow entered the world");
        for (int i = 0; i < 12 && manager.byId(arrow.entityId()) != null; i++) {
            manager.tick(List.of());
        }

        // THE CONTROLS — the hit landed through the combat sink and the
        // arrow left the world on it (the reference's remove at 278).
        assertEquals(1, combat.mobHits, "the arrow struck the zombie");
        assertNull(manager.byId(arrow.entityId()),
                "the arrow left the world on the entity hit");

        // THE PIN — the reference's entity-hit arm is AUDIBLE (line 276):
        // the random.bowhit Sound at the hit, volume 1.0, a fresh pitch
        // draw. The engine plays nothing at the entity hit today.
        assertTrue(recorder.sounds.stream()
                        .anyMatch(sound -> sound.name().equals("random.bowhit")),
                "VT-018's entity-hit arm: a landed arrow strike must play random.bowhit "
                        + "(the reference's entity-hit arm, ArrowEntity line 276: "
                        + "playSound(\"random.bowhit\", 1.0F, 1.2F / (nextFloat() * 0.2F "
                        + "+ 0.9F)) — inside the takeDamage-true branch, before the "
                        + "remove). Observed: the captured FX stream carries NO "
                        + "random.bowhit at the entity hit — the engine's hitMob walk "
                        + "plays nothing (the stick arm is PR #124's carried site; this "
                        + "is the finding's second arm); the captured sounds: "
                        + recorder.sounds);
        // THE EXACT LAW — the volume 1.0 and the pitch inside the
        // reference's draw band (1.2 / (u*0.2 + 0.9) over u ∈ [0, 1):
        // the band [1.2/1.1, 1.2/0.9] ≈ [1.0909, 1.3333], the ends
        // widened a hair for the float cast).
        FxManager.Sound bowhit = recorder.sounds.stream()
                .filter(sound -> sound.name().equals("random.bowhit"))
                .findFirst().orElseThrow();
        assertTrue(Math.abs(bowhit.volume() - 1.0f) < 1.0e-4f,
                "the bowhit's volume is the reference's 1.0F: " + bowhit.volume());
        assertTrue(bowhit.pitch() >= 1.09f && bowhit.pitch() <= 1.34f,
                "the bowhit's pitch is the reference's draw band "
                        + "[~1.09, ~1.33]: " + bowhit.pitch());
        assertEquals(0, bowhit.dimension(),
                "the bowhit rides the arrow's own dimension");
        // THE CONTROL — the hit is otherwise silent in the engine: no
        // OTHER sound fires at the entity hit.
        assertFalse(recorder.sounds.stream()
                        .anyMatch(sound -> !sound.name().equals("random.bowhit")),
                "THE CONTROL: the entity hit plays the bowhit ALONE (the reference's "
                        + "entity-hit arm carries no other sound); observed: "
                        + recorder.sounds);
    }

    @Test
    void theFailedDamageEntityHitPlaysNoBowhit() {
        FxManager fx = new FxManager();
        RecordingFx recorder = new RecordingFx();
        fx.addListener(recorder);
        FailedCombat combat = new FailedCombat();
        MobEntity zombie = zombie();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new BoxedMobHits(zombie), combat,
                fx, new Random(7), 5_000);

        ProjectileEntity arrow = manager.launchVector(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(10.5, 44.0, 10.5), 0.0, -1.0, 0.0, 1.0, 0.0, 0);
        assertTrue(arrow != null, "the arrow entered the world");
        for (int i = 0; i < 12 && manager.byId(arrow.entityId()) != null; i++) {
            manager.tick(List.of());
        }

        // The hit was CONSULTED but the damage never took (the sink's
        // landed=false — the reference's takeDamage-false branch). THE
        // GATE GUARD: that branch is SILENT in the reference (line 280's
        // else: the bounce, no playSound) — the fix must gate the bowhit
        // on the landed verdict, never play it unconditionally.
        assertEquals(1, combat.mobHits, "the strike was consulted");
        assertFalse(recorder.sounds.stream()
                        .anyMatch(sound -> sound.name().equals("random.bowhit")),
                "THE GATE GUARD: a FAILED-damage entity hit plays no bowhit (the "
                        + "reference's takeDamage-false branch is the silent bounce — "
                        + "ArrowEntity lines 280-287); observed: " + recorder.sounds);
        // THE NAMED RESIDUE (documented, not asserted): the reference's
        // failed branch BOUNCES the arrow (velocity *= -0.1, the yaw
        // flip, inAirTicks = 0 — the arrow lives on); the engine's
        // caller removes it unconditionally. That bounce law is the
        // entity-hit family's own adjacent tail, ledgered beside this
        // finding — never fixed by the bowhit PR.
    }

    @Test
    void aShardEntityHitPlaysNoBowhit() {
        FxManager fx = new FxManager();
        RecordingFx recorder = new RecordingFx();
        fx.addListener(recorder);
        LandingCombat combat = new LandingCombat();
        MobEntity zombie = zombie();
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new BoxedMobHits(zombie), combat,
                fx, new Random(7), 5_000);

        // A SNOWBALL through the same box: the hit lands (the sink's
        // verdict true) but the name is the ARROW family's alone —
        // SnowballEntity's onHit plays no bowhit. THE KIND GUARD: the
        // engine's hitMob is shared across every projectile kind, so
        // the fix must gate on the ARROW kind, never the shared arm.
        ProjectileEntity shard = manager.launchVector(
                ProjectileEntity.Kind.SNOWBALL, -1,
                new Position(10.5, 44.0, 10.5), 0.0, -1.0, 0.0, 1.0, 0.0, 0);
        assertTrue(shard != null, "the snowball entered the world");
        for (int i = 0; i < 12 && manager.byId(shard.entityId()) != null; i++) {
            manager.tick(List.of());
        }

        assertEquals(1, combat.mobHits, "the snowball struck the zombie");
        assertFalse(recorder.sounds.stream()
                        .anyMatch(sound -> sound.name().equals("random.bowhit")),
                "THE KIND GUARD: the bowhit is the arrow family's own sound (the "
                        + "reference's line 276 rides ArrowEntity alone — the shard "
                        + "families play no such name); observed: " + recorder.sounds);
    }
}
