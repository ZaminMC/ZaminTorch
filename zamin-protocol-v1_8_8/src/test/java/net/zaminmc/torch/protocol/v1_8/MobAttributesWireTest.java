package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mob arm of the attribute wire (ML-011): the engine's Wave J
 * attribute state over the real wire. A Tools item carrying
 * {@code tag.AttributeModifiers} (a movementSpeed-named row) equipped
 * through the {@code /summon} Equipment row re-derives the tracked pair
 * for every observer — the reference's own walk (the equipment scan of
 * LivingEntity.tick lines 1289-1304 swaps the rows on the attribute
 * instances, the tracked set carries them out through
 * TrackedEntity.sendDataAndAttributes lines 260-274). The LATER
 * attribute mutation (the {@code /entitydata} Attributes row — the
 * reference's setBase) fires the change-time re-send with the new base.
 * And the wire's own isTrackable law pinned negative: an attackDamage
 * row NEVER fires the 0x20 (EntityAttributes line 21 carries no
 * trackable flag — the row never enters the tracked set).
 *
 * <p>Wire waits: the fresh test world seeds its animals and keeps them
 * walking (the movement syncs keep the wire busy — the quiet window
 * never truly quiets), so every drain is BUDGET-bounded (a fixed count
 * of engine-emitted packets — the tick-scaled bound) with the
 * keep-alive exchange answered inline (the scripted client's own
 * exchange), never a wall-clock sleep; every engine state is an
 * awaitCondition.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MobAttributesWireTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 2_000; // the scripted client answers only when the test drives it
    }

    /** One decoded property row (the reference packet's Entry). */
    private record Property(String name, double base, List<double[]> modifiers) {
    }

    /** The reference's read() decode, 1:1 (EntityAttributesS2CPacket lines 35-52). */
    private static List<Property> decodeProperties(byte[] frame) {
        io.netty.buffer.ByteBuf body = io.netty.buffer.Unpooled.wrappedBuffer(frame);
        ByteBufOps.readVarInt(body); // the packet id
        ByteBufOps.readVarInt(body); // the entity id (asserted by the caller's filter)
        int count = body.readInt();
        List<Property> properties = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = ByteBufOps.readString(body, 64);
            double base = body.readDouble();
            int modifierCount = ByteBufOps.readVarInt(body);
            List<double[]> modifiers = new ArrayList<>(modifierCount);
            for (int j = 0; j < modifierCount; j++) {
                long most = body.readLong();
                long least = body.readLong();
                double amount = body.readDouble();
                int operation = body.readByte();
                modifiers.add(new double[] {most, least, amount, operation});
            }
            properties.add(new Property(name, base, modifiers));
        }
        assertEquals(0, body.readableBytes(),
                "the frame ends exactly at the reference's own last field");
        return properties;
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

    /**
     * The bounded drain of one body's 0x20 frames over the LIVE test
     * world: the fresh world's own animal seeding and movement syncs
     * keep the wire busy, so the drain reads a fixed PACKET BUDGET (the
     * tick-scaled bound) or until the quiet window, answering the
     * server's keep-alives inline — frames for other bodies and other
     * packets are skipped.
     */
    private static List<byte[]> drainPropertiesFrames(TestClient18 client, int entityId,
            int budget) throws IOException {
        List<byte[]> frames = new ArrayList<>();
        client.setReadTimeout(600);
        for (int i = 0; i < budget; i++) {
            byte[] frame;
            try {
                frame = client.readPacket();
            } catch (SocketTimeoutException quietWire) {
                return frames; // the budget's own quiet window
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
            frames.add(frame);
        }
        return frames;
    }

    private TestClient18 joinWatcher(String name) throws IOException {
        TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort());
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        client.readUntilPositionAndLook();
        return client;
    }

    private net.zaminmc.torch.server.entity.MobEntity awaitMobNamed(String name)
            throws InterruptedException {
        awaitCondition(() -> server.mobs().all().stream()
                        .anyMatch(mob -> name.equals(mob.customName()) && !mob.dead()),
                "the named mob present: " + name);
        return server.mobs().all().stream()
                .filter(mob -> name.equals(mob.customName()))
                .findFirst().orElseThrow();
    }

    @Test
    void theEquippedModifierItemReDerivesTheTrackedPair() throws Exception {
        try (TestClient18 watcher = joinWatcher("Equipper")) {
            awaitCondition(() -> server.playerRegistry().byName("Equipper")
                            .map(session -> session.state()
                                    == net.zaminmc.torch.entity.PlayerState.PLAYING)
                            .orElse(false), "the watcher PLAYING");
            server.playerRegistry().byName("Equipper").ifPresent(p -> p.setOpLevel(2));

            // The Tools item carrying AttributeModifiers, equipped through
            // the Equipment row's [0] slot (the held item — the reference's
            // own list index IS the slot; the item compound's modifier rows
            // live under tag, the reference's own ItemStack NBT shape).
            watcher.sendChat("/summon Zombie ~ ~ ~ {CustomName:\"Swift\","
                    + "Equipment:[{id:\"minecraft:diamond_sword\",Count:1b,"
                    + "tag:{AttributeModifiers:[{AttributeName:\"generic.movementSpeed\","
                    + "Name:\"Speed modifier\",Amount:0.5d,Operation:0,"
                    + "UUIDMost:3L,UUIDLeast:4L}]}}]}");
            var swift = awaitMobNamed("Swift");

            // Both 0x20s collect: the spawn batch's pair (the dice-shaped
            // body, no rows) and the Equipment row's re-publish (the held
            // stack's rows now on the instances). The engine side first:
            // the wake's frames are written before the mob state settles.
            List<byte[]> frames = drainPropertiesFrames(watcher, swift.entityId(), 500);
            assertTrue(frames.size() >= 2,
                    "the spawn pair AND the equipment re-publish both arrived ("
                    + frames.size() + " frames)");

            // The LAST frame carries the re-derived pair: the engine's own
            // maxHealth + walk consult, with the held stack's trackable row.
            List<Property> properties = decodeProperties(frames.get(frames.size() - 1));
            assertEquals(2, properties.size(), "the trackable pair");
            assertEquals(Protocol18.ATTRIBUTE_MAX_HEALTH, properties.get(0).name());
            assertEquals(swift.maxHealth(), properties.get(0).base(), 0.000001,
                    "the wire equals the engine's own health model");
            assertEquals(Protocol18.ATTRIBUTE_MOVEMENT_SPEED, properties.get(1).name());
            assertEquals(swift.walkSpeed(), properties.get(1).base(), 0.000001,
                    "the wire equals the engine's own walk consult");
            List<double[]> rows = properties.get(1).modifiers();
            assertEquals(1, rows.size(), "the equipped stack's one trackable row");
            assertEquals(3.0, rows.get(0)[0], 0.0, "the row's UUID most-significant long");
            assertEquals(4.0, rows.get(0)[1], 0.0, "the row's UUID least-significant long");
            assertEquals(0.5, rows.get(0)[2], 0.000001, "the row's Amount as a Double");
            assertEquals(0.0, rows.get(0)[3], 0.0, "the row's Operation as a Byte");
            // The derived value the tracking client folds: base + 0.5 op-0.
            assertEquals(swift.walkSpeed() + 0.5, properties.get(1).base() + rows.get(0)[2],
                    0.000001, "the client derives the equipped body's movement speed");
        }
    }

    @Test
    void theLaterAttributeMutationFiresTheChangeTimeSend() throws Exception {
        try (TestClient18 watcher = joinWatcher("Mutator")) {
            awaitCondition(() -> server.playerRegistry().byName("Mutator")
                            .map(session -> session.state()
                                    == net.zaminmc.torch.entity.PlayerState.PLAYING)
                            .orElse(false), "the watcher PLAYING");
            server.playerRegistry().byName("Mutator").ifPresent(p -> p.setOpLevel(2));

            // (The SKELETON kind: each test owns its own mob kind — the @e
            // selector stays unambiguous over the accumulated PER_CLASS
            // world.)
            watcher.sendChat("/summon Skeleton ~ ~ ~ {CustomName:\"Mutant\"}");
            var mutant = awaitMobNamed("Mutant");

            // The quiet baseline: the spawn batch's pair, nothing after.
            List<byte[]> baseline = drainPropertiesFrames(watcher, mutant.entityId(), 400);
            assertEquals(1, baseline.size(),
                    "the spawn pair alone (a tagless summon fires no wake)");

            // The LATER mutation — the /entitydata Attributes row (the
            // reference's setBase): the movementSpeed override replaces the
            // attribute's base and the change-time send fires.
            watcher.sendChat("/entitydata @e[type=Skeleton,c=1] "
                    + "{Attributes:[{Name:\"generic.movementSpeed\",Base:0.3d}]}");
            awaitCondition(() -> mutant.hasMovementSpeedOverride()
                            && Math.abs(mutant.walkSpeed() - 0.3) < 0.000001,
                    "the movementSpeed override landed (the setBase row)");

            List<byte[]> after = drainPropertiesFrames(watcher, mutant.entityId(), 400);
            assertTrue(after.size() >= 1,
                    "the change-time send fired (" + after.size() + " frames)");

            // The LAST frame carries the new base — the tracking client now
            // derives 0.3 (no rows on a tagless summon).
            List<Property> properties = decodeProperties(after.get(after.size() - 1));
            assertEquals(Protocol18.ATTRIBUTE_MOVEMENT_SPEED, properties.get(1).name());
            assertEquals(0.3, properties.get(1).base(), 0.000001,
                    "the setBase row's value is the re-published base");
            assertTrue(properties.get(1).modifiers().isEmpty(),
                    "no rows ride a tagless body");
        }
    }

    @Test
    void theAttackDamageRowNeverFiresTheWire() throws Exception {
        try (TestClient18 watcher = joinWatcher("Bruter")) {
            awaitCondition(() -> server.playerRegistry().byName("Bruter")
                            .map(session -> session.state()
                                    == net.zaminmc.torch.entity.PlayerState.PLAYING)
                            .orElse(false), "the watcher PLAYING");
            server.playerRegistry().byName("Bruter").ifPresent(p -> p.setOpLevel(2));

            watcher.sendChat("/summon Creeper ~ ~ ~ {CustomName:\"Brute\"}");
            var brute = awaitMobNamed("Brute");
            drainPropertiesFrames(watcher, brute.entityId(), 400); // the spawn pair alone

            // An attackDamage-only row: the override lands on the engine's
            // consult (the melee arm reads it), but the tracked set never
            // carries attackDamage (isTrackable — EntityAttributes line 21)
            // — no 0x20 rides.
            watcher.sendChat("/entitydata @e[type=Creeper,c=1] "
                    + "{Attributes:[{Name:\"generic.attackDamage\",Base:7.5d}]}");
            awaitCondition(() -> brute.hasAttackDamageOverride()
                            && Math.abs(brute.attackDamage() - 7.5f) < 0.000001,
                    "the attackDamage override landed on the engine's consult");

            List<byte[]> after = drainPropertiesFrames(watcher, brute.entityId(), 400);
            assertTrue(after.isEmpty(),
                    "the attackDamage row never fires the 0x20 (isTrackable's own law)");
        }
    }
}
