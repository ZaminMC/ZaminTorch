package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spectator camera's engine law (ML-018 — the reference's
 * ServerPlayerEntity camera walk, lines 916-923 + tick's camera arm lines
 * 273-285): the spectate walk attaches the camera to a live same-dimension
 * player and anchors the body at the target, the per-tick follow keeps it
 * there, every exit (sneak, the target's departure, the game-mode change)
 * restores the camera to self, and the spectator's own movement never meets
 * the anti-cheat pushback while every other mode keeps the envelope intact.
 */
class SpectatorCameraAcceptanceTest {

    @TempDir
    Path dataDir;

    @Test
    void theSpectateWalkArmsTheCameraAndAnchorsTheBodyAtTheTarget() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession alice = join(server, "Alice");
            PlayerSession bob = spectator(server, "Bob");
            PlayerSession target =
                    server.playerRegistry().byName("Alice").orElseThrow();
            alice.applyMovement(new Position(40.0, 5.0, 12.5),
                    new Rotation(10.0f, 0.0f), true);

            server.spectateCamera(bob, target.uuid());
            await(() -> target.uuid().equals(bob.spectatorCamera()),
                    "the camera attached to the target");
            // The reference's setCamera teleport arm: the body anchors at
            // the target the moment the camera attaches.
            assertEquals(40.0, bob.position().x(), 0.0001, "the body anchored at the target");
            assertEquals(5.0, bob.position().y(), 0.0001, "the body anchored at the target");
            assertEquals(12.5, bob.position().z(), 0.0001, "the body anchored at the target");
            assertEquals(10.0f, bob.rotation().yaw(), 0.01f, "the angles follow too");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void thePerTickFollowAnchorsTheSpectatorsBodyAtTheTargetEveryTick() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession alice = join(server, "Alice");
            PlayerSession bob = spectator(server, "Bob");
            RecordingLink cameraWire = lastLink;
            server.spectateCamera(bob, alice.uuid());
            await(() -> alice.uuid().equals(bob.spectatorCamera()), "the camera attached");
            await(() -> alice.uuid().equals(cameraWire.lastCameraTarget()),
                    "the attach reached the camera verb");

            // The attached client sends no movement of its own
            // (LocalClientPlayerEntity's isCamera gate), so the walk must
            // anchor the body after the target's own authoritative steps.
            alice.applyMovement(new Position(3.0, 5.0, 3.0),
                    new Rotation(0.0f, 0.0f), true);
            await(() -> bob.position().x() == 3.0 && bob.position().z() == 3.0,
                    "the follow anchored the body at the target's first step");
            alice.applyMovement(new Position(3.4, 5.0, 3.0),
                    new Rotation(0.0f, 0.0f), true);
            await(() -> bob.position().x() == 3.4,
                    "the follow keeps the body at the target's next step");
            // The world-stream arm: the per-tick follow reaches the wire.
            await(() -> cameraWire.followCalls() > 0,
                    "the follow's world-stream anchor reached the link");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theSneakExitRestoresTheCameraAndTheBodyStaysWhereTheFollowLeftIt() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession alice = join(server, "Alice");
            PlayerSession bob = spectator(server, "Bob");
            RecordingLink cameraWire = lastLink;
            server.spectateCamera(bob, alice.uuid());
            await(() -> alice.uuid().equals(bob.spectatorCamera()), "the camera attached");
            await(() -> alice.uuid().equals(cameraWire.lastCameraTarget()),
                    "the attach reached the camera verb");

            // The client signals the exit the only way it can while its
            // camera rides another body (the reference's isSneaking consult
            // in the per-tick walk): the sneak action.
            server.entityAction(bob, 0);
            await(() -> bob.spectatorCamera() == null, "the sneak exit restored the camera");
            // The restore's own wire (the reference's camera-then-teleport
            // order, setCamera(this) -> the null verb).
            await(() -> cameraWire.lastCameraTarget() == null,
                    "the restore reached the camera verb");
            // The body stays where the follow left it (the reference's own
            // teleport target is the followed position, not the origin).
            assertEquals(alice.position().x(), bob.position().x(), 0.0001);
            assertEquals(alice.position().y(), bob.position().y(), 0.0001);
            assertEquals(alice.position().z(), bob.position().z(), 0.0001);
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theGameModeChangeAwayFromSpectatorExitsTheCamera() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession alice = join(server, "Alice");
            PlayerSession bob = spectator(server, "Bob");
            server.spectateCamera(bob, alice.uuid());
            await(() -> alice.uuid().equals(bob.spectatorCamera()), "the camera attached");

            // The reference's setGameMode else-arm (lines 823-826): leaving
            // spectator restores the camera to self.
            server.setGamemode(bob, GameMode.SURVIVAL);
            await(() -> bob.spectatorCamera() == null,
                    "the game-mode change restored the camera");
            assertTrue(bob.gamemode() == GameMode.SURVIVAL, "the mode change landed");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theTargetsDepartureExitsTheCamera() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession alice = join(server, "Alice");
            PlayerSession bob = spectator(server, "Bob");
            server.spectateCamera(bob, alice.uuid());
            await(() -> alice.uuid().equals(bob.spectatorCamera()), "the camera attached");

            server.clientDisconnected(alice, "test leave");
            // The walk's own liveness consult: a gone target restores the
            // camera within the tick lane's own cadence (the reference's
            // dead-camera arm, isAlive == false).
            await(() -> bob.spectatorCamera() == null,
                    "the target's departure restored the camera");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theSpectatorsMovementNeverMeetsTheGuardWhileEveryOtherModeKeepsIt() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession alice = join(server, "Alice");
            PlayerSession bob = spectator(server, "Bob");

            // The spectator's free camera (the reference's noClip arm): a
            // flight-speed burst beyond the physics envelope applies.
            Position burst = new Position(24.0, 9.0, -14.0);
            server.movementProposal(bob, burst, new Rotation(0.0f, 0.0f), false);
            assertEquals(24.0, bob.position().x(), 0.0001,
                    "the spectator's burst applied (no-clip)");
            assertEquals(9.0, bob.position().y(), 0.0001,
                    "the spectator's burst applied (no-clip)");

            // The same burst for a survival body is still the guard's own
            // refuse (the envelope never weakens for any other mode): the
            // refusal leaves the state exactly where it was.
            Position before = alice.position();
            Position survivalBurst = new Position(before.x() + 30.0, before.y() + 4.0,
                    before.z() + 30.0);
            server.movementProposal(alice, survivalBurst,
                    new Rotation(0.0f, 0.0f), true);
            assertEquals(before, alice.position(),
                    "the survival burst is still refused, the state untouched");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theSpectateWalkRefusesNonSpectatorsAndAbsentTargets() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession alice = join(server, "Alice");
            PlayerSession bob = spectator(server, "Bob");

            // The reference's isSpectator refusal: a survival player's
            // spectate packet is discarded.
            PlayerSession carol = join(server, "Carol");
            server.spectateCamera(carol, alice.uuid());
            Thread.sleep(150); // two engine ticks: the walk ran and refused
            assertNull(carol.spectatorCamera(), "the non-spectator's packet was refused");

            // The reference's entity==null arm: an absent target attaches
            // nothing (no crash, no state).
            server.spectateCamera(bob, UUID.randomUUID());
            Thread.sleep(150);
            assertNull(bob.spectatorCamera(), "the absent target attached nothing");

            // The self-target arm is the exit: attaching to self while
            // attached restores the camera.
            server.spectateCamera(bob, alice.uuid());
            await(() -> alice.uuid().equals(bob.spectatorCamera()), "the camera attached");
            server.spectateCamera(bob, bob.uuid());
            await(() -> bob.spectatorCamera() == null,
                    "the self-target exited the camera");
        } finally {
            server.shutdown(null);
        }
    }

    // ------------------------------------------------------------------ harness

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "spectate", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    /** The recording link handed to the last joined session (per-test state). */
    private RecordingLink lastLink;

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        // The adapter calls this after its join sequence; the scripted fake link
        // skips straight to play state so the body simulation applies.
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    /** Joins the named player and flips them to spectator (the mode's own tick write). */
    private PlayerSession spectator(EngineServer server, String name) throws InterruptedException {
        PlayerSession session = join(server, name);
        server.setGamemode(session, GameMode.SPECTATOR);
        await(() -> session.gamemode() == GameMode.SPECTATOR, name + " spectator");
        return session;
    }

    private ClientLink link() {
        RecordingLink link = new RecordingLink();
        lastLink = link;
        return link;
    }

    /** The fake link with the camera arms' own observation. */
    private static final class RecordingLink implements ClientLink {
        private volatile UUID lastCameraTarget;
        private volatile int followCalls;

        UUID lastCameraTarget() {
            return lastCameraTarget;
        }

        int followCalls() {
            return followCalls;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }

        @Override
        public void spectateCamera(UUID target) {
            lastCameraTarget = target;
        }

        @Override
        public void spectateCameraFollow(Position targetPosition) {
            followCalls++;
        }
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
