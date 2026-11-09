package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice #3 behavioral scenarios: chat delivery, command feedback, and the
 * multiplayer visibility proof (§322: two players see each other).
 */
class ChatAndMultiplayerTest extends ProtocolTestBase {

    @Test
    void chatFromOnePlayerReachesTheOther() throws Exception {
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

            alice.sendChat("Hello from Alice!");

            String bobLine = bob.readChatLine(5_000);
            assertTrue(bobLine.contains("<Alice>"), bobLine);
            assertTrue(bobLine.contains("Hello from Alice!"), bobLine);
        }
        awaitEmptyServer();
    }

    /**
     * Wave C1 — THE CONSOLE LOG SEAT (the reference's own law,
     * PlayerManager.sendMessage lines 758-762: {@code this.server
     * .sendMessage(message)} logs player chat to the server console
     * BEFORE the broadcast rides): the reported "player messages never
     * show up in console" gap. The seat captures the engine server
     * logger's records while a scripted client chats.
     */
    @Test
    void playerChatLandsInTheServerConsoleLog() throws Exception {
        java.util.List<String> captured = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.logging.Handler capture = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                captured.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Logger engineLogger =
                java.util.logging.Logger.getLogger("net.zaminmc.torch.server.EngineServer");
        engineLogger.addHandler(capture);
        try {
            try (TestClient18 eve = new TestClient18("127.0.0.1", adapter.boundPort())) {
                eve.sendHandshake(47, 2);
                eve.sendLoginStart("Eve");
                eve.readLoginSuccess();
                eve.readUntilPositionAndLook();
                awaitPlayers("Eve");

                eve.sendChat("console should see this");
                // The chat line itself arrives (the broadcast's own receipt).
                String line = eve.readChatLine(5_000);
                assertTrue(line.contains("console should see this"), line);
            }
            awaitEmptyServer();
            // The console log carries the reference's own form: the
            // message logged BEFORE the broadcast, "<Name> content".
            assertTrue(captured.stream()
                            .anyMatch(m -> m.equals("<Eve> console should see this")),
                    () -> "console log missing the chat line; captured=" + captured);
        } finally {
            engineLogger.removeHandler(capture);
        }
    }

    @Test
    void commandsReplyOnlyToTheSender() throws Exception {
        try (TestClient18 carol = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 dave = new TestClient18("127.0.0.1", adapter.boundPort())) {
            carol.sendHandshake(47, 2);
            carol.sendLoginStart("Carol");
            carol.readLoginSuccess();
            carol.readUntilPositionAndLook();
            dave.sendHandshake(47, 2);
            dave.sendLoginStart("Dave");
            dave.readLoginSuccess();
            dave.readUntilPositionAndLook();
            awaitPlayers("Carol", "Dave");

            carol.sendChat("/ping");
            String reply = carol.readChatLineSkippingBroadcasts(5_000);
            assertTrue(reply.contains("pong"), reply);

            // The command feedback went only to Carol: the next line Dave receives
            // is the public chat sent afterwards (never the /ping feedback).
            carol.sendChat("public after command");
            String daveLine = dave.readChatLineSkippingBroadcasts(5_000);
            assertTrue(daveLine.contains("public after command"), daveLine);
        }
        awaitEmptyServer();
    }

    @Test
    void twoPlayersSpawnEachOtherAndSeeMovement() throws Exception {
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

            // Alice received a Named Spawn for Bob (mutual join exchange).
            byte[] spawnPacket = alice.readPacketOfType(Protocol18.S2C_NAMED_SPAWN, 5_000);
            assertEquals(Protocol18.S2C_NAMED_SPAWN, alice.readPacketId(spawnPacket));

            // Bob moves; Alice receives an Entity Teleport.
            bob.walkTo(4.5, 5.0, -6.5, true);
            byte[] teleportPacket = alice.readPacketOfType(Protocol18.S2C_ENTITY_TELEPORT, 5_000);
            assertEquals(Protocol18.S2C_ENTITY_TELEPORT, alice.readPacketId(teleportPacket));

            // Bob disconnects; Alice receives Destroy Entities.
            bob.close();
            byte[] destroyPacket = alice.readPacketOfType(Protocol18.S2C_DESTROY_ENTITIES, 5_000);
            assertEquals(Protocol18.S2C_DESTROY_ENTITIES, alice.readPacketId(destroyPacket));
        }
        awaitEmptyServer();
    }

    @Test
    void chatInputIsSanitized() throws Exception {
        try (TestClient18 mallory = new TestClient18("127.0.0.1", adapter.boundPort())) {
            mallory.sendHandshake(47, 2);
            mallory.sendLoginStart("Mallory");
            mallory.readLoginSuccess();
            mallory.readUntilPositionAndLook();
            awaitPlayers("Mallory");

            // Control characters must not survive sanitization; text still delivers.
            mallory.sendChat("hi \u0007\u001B[31m colored");
            String line = mallory.readChatLine(5_000);
            assertTrue(line.contains("hi "), line);
            assertTrue(!line.contains("\u0007"), line);
            assertTrue(!line.contains("\u001B"), line);
        }
        awaitEmptyServer();
    }
}
