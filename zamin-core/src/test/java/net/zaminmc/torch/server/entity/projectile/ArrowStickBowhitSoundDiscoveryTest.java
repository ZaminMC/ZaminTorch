package net.zaminmc.torch.server.entity.projectile;

import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-018 PASS-2 SEAT — the stick's {@code random.bowhit}
 * sound (docs/vanilla-conformance-audit.md, the thirty-fifth pass's
 * discovery): the reference plays the bowhit at the block stick.
 *
 * <p>THE REFERENCE LAW (ArrowEntity.tick, re-walked for this seat): the
 * stick arm plays the sound at the rest — line 303:
 * {@code this.playSound("random.bowhit", 1.0F, 1.2F / (this.random
 * .nextFloat() * 0.2F + 0.9F))} — the volume 1.0, the pitch a random
 * draw over the band [1.2/1.1, 1.2/0.9] ≈ [1.0909, 1.3333]. The
 * entity-hit arm plays the same name at line 276 (the same pitch law,
 * a fresh draw). A stuck arrow is AUDIBLE in vanilla.</p>
 *
 * <p>THE ENGINE'S ROW (ProjectileManager's stick path): the solid arm
 * calls {@code landInGround} and fans {@code onProjectileLanded} —
 * which plays NOTHING (the adapter's landed arm sends no sound; the
 * manager's own FX uses are the shatter family alone — {@code random
 * .glass} at line 1449, the poof). The name {@code random.bowhit}
 * exists in the engine at the TRIPWIRE alone (TripwireSystem line
 * 313).</p>
 *
 * <p>THE SEAT: an arrow dropped into a stub-solid cell sticks; the
 * captured FX stream must carry the {@code random.bowhit} Sound at the
 * rest. RED on develop (no event fires at the stick — the captured
 * stream is empty of the name).</p>
 */
class ArrowStickBowhitSoundDiscoveryTest {

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

    /** No entity targets: the flight meets the block alone. */
    private static final class NoHits implements ProjectileManager.HitResolver {
        @Override
        public MobEntity mobAt(Position point, int dimension) {
            return null;
        }

        @Override
        public PlayerSession playerAt(Position point, int dimension) {
            return null;
        }
    }

    @Test
    void theStickingArrowPlaysTheBowhitSound() {
        FxManager fx = new FxManager();
        RecordingFx recorder = new RecordingFx();
        fx.addListener(recorder);
        // The solid stub: solid inside the cell (10, 40, 10) alone — the
        // arrow drops from above and sticks in it.
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> Math.floor(x) == 10 && Math.floor(y) == 40
                        && Math.floor(z) == 10,
                new NoHits(), new ProjectileManagerTest.RecordingCombat(),
                fx, new Random(7), 5_000);

        ProjectileEntity arrow = manager.launchVector(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(10.5, 44.0, 10.5), 0.0, -1.0, 0.0, 1.0, 0.0, 0);
        assertTrue(arrow != null, "the arrow entered the world");
        for (int i = 0; i < 12; i++) {
            manager.tick(List.of());
        }
        assertTrue(arrow.inGround(), "the arrow stuck in the solid cell");

        // THE PIN — the reference's stick arm is AUDIBLE (line 303):
        // the random.bowhit Sound at the rest, volume 1.0. The engine
        // plays nothing at the stick today.
        assertTrue(recorder.sounds.stream()
                        .anyMatch(sound -> sound.name().equals("random.bowhit")),
                "VT-018: the sticking arrow must play random.bowhit (the reference's "
                        + "stick arm, ArrowEntity line 303: playSound(\"random.bowhit\", "
                        + "1.0F, 1.2F / (nextFloat() * 0.2F + 0.9F)) — the volume 1.0, "
                        + "the pitch a random draw over [~1.09, ~1.33]). Observed: the "
                        + "captured FX stream carries NO random.bowhit at the stick — "
                        + "the engine's landed arm plays nothing (the name exists in "
                        + "the engine at the tripwire alone, TripwireSystem line 313); "
                        + "the captured sounds: " + recorder.sounds);
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
        // THE CONTROL — the stick is otherwise silent in the engine: no
        // OTHER sound fires at the stick either (the captured stream may
        // carry nothing at all on develop).
        assertFalse(recorder.sounds.stream()
                        .anyMatch(sound -> !sound.name().equals("random.bowhit")),
                "THE CONTROL: the stick plays the bowhit ALONE (the reference's stick "
                        + "arm carries no other sound); observed: " + recorder.sounds);
    }
}
