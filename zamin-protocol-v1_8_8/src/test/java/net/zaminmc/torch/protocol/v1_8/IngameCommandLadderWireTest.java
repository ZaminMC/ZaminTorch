package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE C2 — THE IN-GAME COMMAND LADDER (the owner's own report read whole:
 * "player is unable to use commands from in-game, it is as if it doesn't
 * exist, but can use commands from console"). The full ladder over the
 * real socket, exactly the owner's flow: a fresh player's level-gated
 * command consult, the console's {@code /op} granting mid-session, and the
 * SAME in-game command running after — plus the reference's own rejoin
 * law (ops.json: the op survives a disconnect/reconnect, the level
 * restored at the login walk, ServerPlayerEntity's
 * {@code this.getGameProfile()}-keyed isOp consult).
 */
class IngameCommandLadderWireTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    /**
     * THE OWNER'S LADDER: (1) the fresh player's /gamemode is refused with
     * the reference's permission line (CommandRegistry.run lines 66-70 —
     * its own arm since the C1 split, never the notFound masquerade); (2)
     * the console's /op grants mid-session (opCommand: target.setOpLevel(4)
     * + the "You are now op!" system line over the wire); (3) the SAME
     * in-game /gamemode runs whole (the mode change + the feedback).
     */
    @Test
    void theOpLadderCarriesInGameCommandsWhole() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Ladder");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayers("Ladder");

            // (1) The fresh player's level-gated consult: the permission
            // refusal, its own text since the C1 split.
            client.sendChat("/gamemode creative");
            String refusal = client.readChatLineSkippingBroadcasts(5_000);
            assertTrue(refusal.contains("you do not have permission"), refusal);

            // (2) The console's /op, mid-session.
            server.consoleCommand("op Ladder");
            String opNote = client.readChatLineSkippingBroadcasts(5_000);
            assertTrue(opNote.contains("You are now op!"), opNote);

            // (3) The SAME in-game command runs whole.
            client.sendChat("/gamemode creative");
            String feedback = client.readChatLineSkippingBroadcasts(5_000);
            assertTrue(feedback.contains("Ladder"), feedback);
            var session = server.playerRegistry().byName("Ladder").orElseThrow();
            assertEquals(net.zaminmc.torch.GameMode.CREATIVE, session.gamemode(),
                    "the in-game /gamemode changed the mode");
        }
        awaitEmptyServer();
    }

    /**
     * THE REJOIN LAW (ops.json's own persistence): an opped player who
     * disconnects and reconnects carries the level back (the login walk's
     * opStore consult) — the level-gated command runs on the second join
     * without any console grant.
     */
    @Test
    void theOpSurvivesTheRejoin() throws Exception {
        try (TestClient18 first = new TestClient18("127.0.0.1", adapter.boundPort())) {
            first.sendHandshake(47, 2);
            first.sendLoginStart("Rejoiner");
            first.readLoginSuccess();
            first.readUntilPositionAndLook();
            awaitPlayers("Rejoiner");
            server.consoleCommand("op Rejoiner");
            String opNote = first.readChatLineSkippingBroadcasts(5_000);
            assertTrue(opNote.contains("You are now op!"), opNote);
        }
        awaitEmptyServer();

        try (TestClient18 second = new TestClient18("127.0.0.1", adapter.boundPort())) {
            second.sendHandshake(47, 2);
            second.sendLoginStart("Rejoiner");
            second.readLoginSuccess();
            second.readUntilPositionAndLook();
            awaitPlayers("Rejoiner");

            // The level-gated command runs whole on the rejoin — no grant.
            second.sendChat("/gamemode creative");
            String feedback = second.readChatLineSkippingBroadcasts(5_000);
            assertTrue(feedback.contains("Rejoiner"), feedback);
            var session = server.playerRegistry().byName("Rejoiner").orElseThrow();
            assertEquals(net.zaminmc.torch.GameMode.CREATIVE, session.gamemode(),
                    "the rejoiner's /gamemode changed the mode");
        }
        awaitEmptyServer();
    }
}
