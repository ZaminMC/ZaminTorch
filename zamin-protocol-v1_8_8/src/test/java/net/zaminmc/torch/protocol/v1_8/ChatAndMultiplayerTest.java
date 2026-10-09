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
            String reply = carol.readChatLine(5_000);
            assertTrue(reply.contains("pong"), reply);

            // The command feedback went only to Carol: the next line Dave receives
            // is the public chat sent afterwards (never the /ping feedback).
            carol.sendChat("public after command");
            String daveLine = dave.readChatLine(5_000);
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
