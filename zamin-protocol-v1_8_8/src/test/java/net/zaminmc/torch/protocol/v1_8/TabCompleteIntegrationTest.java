package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tab-Complete (0x14 → 0x3A) end-to-end: command names complete against the
 * dispatcher with the op gate applied, and argument tokens complete against
 * online player names (the historical server-side completion set).
 */
class TabCompleteIntegrationTest extends ProtocolTestBase {

    @Test
    void commandNamesCompleteWithTheOpGate() throws Exception {
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort())) {
            alice.sendHandshake(47, 2);
            alice.sendLoginStart("Alice");
            alice.readLoginSuccess();
            alice.readUntilPositionAndLook();

            // A non-operator asking for "he" sees only what they may run —
            // "/help" completes; the moderator-level "/heal" and the
            // op-gated "/gamemode" must not leak.
            alice.sendTabComplete("/he");
            List<String> completions = alice.readTabCompletions(5_000);
            assertTrue(completions.contains("/help"), completions.toString());
            assertFalse(completions.contains("/heal"), completions.toString());
            assertFalse(completions.contains("/gamemode"), completions.toString());
            assertFalse(completions.contains("/ban"), completions.toString());
        }
        awaitEmptyServer();
    }

    @Test
    void argumentTokensCompleteOnlinePlayerNames() throws Exception {
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 bob = new TestClient18("127.0.0.1", adapter.boundPort())) {
            alice.sendHandshake(47, 2);
            alice.sendLoginStart("Alice");
            alice.readLoginSuccess();
            alice.readUntilPositionAndLook();

            bob.sendHandshake(47, 2);
            bob.sendLoginStart("Bob");
            bob.readLoginSuccess();
            bob.readUntilPositionAndLook();
            awaitPlayers("Alice", "Bob");

            alice.sendTabComplete("/tp Bo");
            List<String> completions = alice.readTabCompletions(5_000);
            assertTrue(completions.contains("Bob"), completions.toString());
            assertFalse(completions.contains("Alice"), completions.toString());
        }
        awaitEmptyServer();
    }
}
