package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Entity Properties (0x20) wire form pinned against the reference
 * packet class (ML-011): {@code EntityAttributesS2CPacket} — the #31
 * clientbound play registration (NetworkProtocol line 154, between
 * PlayerXp 0x1F and WorldChunk 0x21). The conformance row
 * ({@code S2C_ENTITY_PROPERTIES} 0x20 in Protocol18ConformanceTest)
 * pins the ID; THIS suite pins the BODY — every field's order and type
 * decoded exactly as the reference's own read()
 * (EntityAttributesS2CPacket lines 35-52): VarInt entity id, Int
 * property count, per property: String name, Double base, VarInt
 * modifier count, per modifier: UUID (two big-endian longs), Double
 * amount, Byte operation — and the track-time send itself
 * (TrackedEntity.updatePlayer lines 322-328: the observer's first 0x20
 * rides the tracking exchange, carrying the trackable pair every living
 * body registers — maxHealth + movementSpeed; attackDamage never
 * rides, the reference's own isTrackable law, EntityAttributes lines
 * 12-21).
 *
 * <p>Wire waits: the fresh test world seeds its animals and keeps them
 * walking (the movement syncs keep the wire busy — the quiet window
 * never truly quiets), so every drain is BUDGET-bounded (a fixed count
 * of engine-emitted packets — the tick-scaled bound) with the
 * keep-alive exchange answered inline (the scripted client's own
 * exchange, the same law as the attribute suite's), never a wall-clock
 * sleep.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntityPropertiesWireConformanceTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 2_000; // the scripted clients answer only when the test drives it
    }

    /** One decoded property row (the reference packet's Entry). */
    private record Property(String name, double base, List<UUID> modifierIds) {
    }

    /**
     * The reference's read() decode, 1:1 (EntityAttributesS2CPacket lines
     * 35-52) — the field types are the assertion: the VarInt entity head,
     * the four-byte Int count, the length-prefixed String name, the
     * eight-byte Double base, the VarInt modifier count, and the
     * 16-byte UUID + Double + Byte modifier rows.
     */
    private static List<Property> decodeProperties(byte[] frame) {
        io.netty.buffer.ByteBuf body = io.netty.buffer.Unpooled.wrappedBuffer(frame);
        ByteBufOps.readVarInt(body); // the packet id
        ByteBufOps.readVarInt(body); // the entity id (asserted by the caller's filter)
        int count = body.readInt(); // an INT, not a varint — the reference's own form
        List<Property> properties = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = ByteBufOps.readString(body, 64);
            double base = body.readDouble();
            int modifierCount = ByteBufOps.readVarInt(body);
            List<UUID> modifierIds = new ArrayList<>(modifierCount);
            for (int j = 0; j < modifierCount; j++) {
                long most = body.readLong();
                long least = body.readLong();
                body.readDouble(); // the amount
                body.readByte(); // the operation
                modifierIds.add(new UUID(most, least));
            }
            properties.add(new Property(name, base, modifierIds));
        }
        assertEquals(0, body.readableBytes(),
                "the frame ends exactly at the last field — the reference's own layout,"
                + " nothing more");
        return properties;
    }

    /**
     * The next 0x20 frame for one entity id, budget-bounded: non-matching
     * packets are skipped (the server's keep-alives answered inline — the
     * scripted client's own exchange), the quiet window or the packet
     * budget closes the read.
     */
    private static byte[] readPropertiesFrame(TestClient18 client, int entityId, int budget)
            throws IOException {
        client.setReadTimeout(600);
        for (int i = 0; i < budget; i++) {
            byte[] frame;
            try {
                frame = client.readPacket();
            } catch (SocketTimeoutException quietWire) {
                return null;
            }
            if (answerIfKeepAlive(client, frame)) {
                i--; // the keep-alive exchange is not budget consumption
                continue;
            }
            if (client.readPacketId(frame) != Protocol18.S2C_ENTITY_PROPERTIES) {
                continue;
            }
            io.netty.buffer.ByteBuf body = io.netty.buffer.Unpooled.wrappedBuffer(frame);
            ByteBufOps.readVarInt(body); // the packet id
            if (ByteBufOps.readVarInt(body) != entityId) {
                continue; // another body's publication
            }
            return frame;
        }
        return null;
    }

    /** The scripted client's keep-alive exchange: answers the server's ping inline. */
    private static boolean answerIfKeepAlive(TestClient18 client, byte[] frame) throws IOException {
        if (client.readPacketId(frame) != Protocol18.S2C_KEEP_ALIVE) {
            return false;
        }
        io.netty.buffer.ByteBuf body = io.netty.buffer.Unpooled.wrappedBuffer(frame);
        ByteBufOps.readVarInt(body); // the packet id
        int id = (int) ByteBufOps.readVarInt(body); // the counter's own varint
        client.sendKeepAliveResponse(id);
        return true;
    }

    @Test
    void theTrackTimePairRidesTheTrackingExchange() throws Exception {
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 bob = new TestClient18("127.0.0.1", adapter.boundPort())) {
            alice.sendHandshake(47, 2);
            alice.sendLoginStart("Alpha");
            alice.readLoginSuccess();
            alice.readUntilPositionAndLook();
            awaitPlaying("Alpha");

            bob.sendHandshake(47, 2);
            bob.sendLoginStart("Bravo");
            bob.readLoginSuccess();
            bob.readUntilPositionAndLook();
            awaitPlaying("Bravo");

            // The mutual tracking exchange (playerEnteredPlay): each
            // client spawns the other, and the track-time 0x20 rides the
            // same exchange — the reference's updatePlayer arm.
            int aliceSeesBravo = alice.readNamedSpawn(5_000)[0];
            int bravoSeesAlice = bob.readNamedSpawn(5_000)[0];

            byte[] bravoFrame = readPropertiesFrame(bob, bravoSeesAlice, 500);
            byte[] aliceFrame = readPropertiesFrame(alice, aliceSeesBravo, 500);
            assertNotNull(bravoFrame, "Alpha's track-time 0x20 reached Bravo");
            assertNotNull(aliceFrame, "Bravo's track-time 0x20 reached Alpha");

            // The trackable pair, both observers, the reference's decode:
            // maxHealth (the survival body's own 20) then movementSpeed
            // (PlayerEntity line 141's registered base 0.1) — no modifiers
            // on a bare body, and attackDamage never rides (isTrackable).
            for (byte[] frame : List.of(aliceFrame, bravoFrame)) {
                List<Property> properties = decodeProperties(frame);
                assertEquals(2, properties.size(),
                        "the trackable pair: maxHealth + movementSpeed, attackDamage never rides");
                assertEquals(Protocol18.ATTRIBUTE_MAX_HEALTH, properties.get(0).name());
                assertEquals(20.0, properties.get(0).base(), 0.000001,
                        "the living body's registered maxHealth base");
                assertTrue(properties.get(0).modifierIds().isEmpty(),
                        "a bare body carries no maxHealth rows");
                assertEquals(Protocol18.ATTRIBUTE_MOVEMENT_SPEED, properties.get(1).name());
                assertEquals(0.1, properties.get(1).base(), 0.000001,
                        "the player's own registered movementSpeed base (PlayerEntity line 141)");
                assertTrue(properties.get(1).modifierIds().isEmpty(),
                        "a bare body carries no movementSpeed rows");
            }
        }
    }

    @Test
    void theEquippedModifierRowRidesTheTrackTimePair() throws Exception {
        try (TestClient18 carol = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 dave = new TestClient18("127.0.0.1", adapter.boundPort())) {
            carol.sendHandshake(47, 2);
            carol.sendLoginStart("Charlie");
            carol.readLoginSuccess();
            carol.readUntilPositionAndLook();
            var carolSession = awaitPlaying("Charlie");

            // The Tools item carrying AttributeModifiers (Wave J's own
            // dataTag form): a movementSpeed-named op-0 row — the
            // trackable name the 0x20 rides.
            carol.sendChat("/give diamond_sword 1 0 {AttributeModifiers:"
                    + "[{AttributeName:\"generic.movementSpeed\","
                    + "Name:\"Speed modifier\",Amount:0.5d,Operation:0,"
                    + "UUIDMost:3L,UUIDLeast:4L}]}");
            awaitCondition(() -> {
                var held = carolSession.inventory().held();
                return held != null && !held.isEmpty()
                        && held.attributeModifiers() != null
                        && !held.attributeModifiers().isEmpty();
            }, "the granted sword lands in the held slot with its modifier row");

            // The late joiner tracks the equipped body: the track-time 0x20
            // carries the held stack's trackable rows (the reference's
            // equipment scan over getTrackable).
            dave.sendHandshake(47, 2);
            dave.sendLoginStart("Delta");
            dave.readLoginSuccess();
            dave.readUntilPositionAndLook();
            awaitPlaying("Delta");
            int daveSeesCarol = dave.readNamedSpawn(5_000)[0];

            byte[] frame = readPropertiesFrame(dave, daveSeesCarol, 500);
            assertNotNull(frame, "the equipped body's track-time 0x20 reached the late joiner");
            List<Property> properties = decodeProperties(frame);
            assertEquals(2, properties.size());
            assertEquals(Protocol18.ATTRIBUTE_MAX_HEALTH, properties.get(0).name());
            assertEquals(20.0, properties.get(0).base(), 0.000001);
            assertEquals(Protocol18.ATTRIBUTE_MOVEMENT_SPEED, properties.get(1).name());
            assertEquals(0.1, properties.get(1).base(), 0.000001,
                    "the base the client folds the rows over");

            // The modifier row's own wire form (the reference's write
            // lines 64-68): the UUID as two big-endian longs, the amount
            // as a Double, the operation as a Byte — the client derives
            // 0.1 + 0.5 = 0.6.
            List<UUID> rows = properties.get(1).modifierIds();
            assertEquals(1, rows.size(), "the held stack's one trackable row");
            assertEquals(new UUID(3L, 4L), rows.get(0),
                    "the UUID rides as two longs — most-significant first");
        }
    }

    private net.zaminmc.torch.server.player.PlayerSession awaitPlaying(String name)
            throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name)
                .map(session -> session.state()
                        == net.zaminmc.torch.entity.PlayerState.PLAYING)
                .orElse(false), "player PLAYING: " + name);
        return server.playerRegistry().byName(name).orElseThrow();
    }
}
