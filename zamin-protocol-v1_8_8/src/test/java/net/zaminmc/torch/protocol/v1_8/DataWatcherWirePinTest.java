package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ML-021 — the 0x0C Named Entity Spawn diagnostic, phase one (the botless
 * pin): the engine's DataWatcher encoding pinned against the reference
 * SyncedData wire form for every watched type the engine emits, over LIVE
 * frames on the real wire. The parser below is INDEPENDENT of the engine's
 * writers — ported from the reference's own shapes
 * (net.minecraft.entity.data.SyncedData.writeEntry / read):
 * the entry byte is {@code (type << 5 | index & 31) & 0xFF}; the values
 * 0 byte, 1 short, 2 int, 3 float, 4 string, 5 item (short id, byte
 * count, short data, then NBT — empty is short -1 alone), 6 position
 * (three ints), 7 rotation (three floats: pitch, yaw, roll — the
 * reference's own order); the list ends on 127/0x7F. Every value read is
 * STRICT — a value that overruns the frame throws (the mineflayer
 * PartialReadError analog this row exists to localize), and the parse
 * must land EXACTLY on the frame boundary. The observed cycle-3 failure
 * was a protodef PartialReadError inside entityMetadataItem with four
 * mutual players; if every live frame here parses clean to its boundary,
 * the engine's side is exonerated at the form level and the row's
 * remainder is the four-bot raw capture.
 *
 * <p>The engine's used type family (verified by writer survey): BYTE
 * (flags/age/status), SHORT (the enderman's carried block), INT (the
 * villager's profession), FLOAT (health), STRING (the custom-name pair),
 * SLOT (the item entities' stacks), ROTATION (the armor stand's poses).
 * The POSITION type (6) has no writer in the engine — recorded here, not
 * silently claimed.</p>
 */
class DataWatcherWirePinTest extends ProtocolTestBase {

    /** The keep-alive interval disabled — the pin's scripted client never
     * answers keep-alives; the frames under test are not keep-alives. */
    @Override
    protected long keepAliveIntervalMs() {
        return 10_000_000;
    }

    // ---- the independent reference-form parser -------------------------------

    /** One parsed metadata entry: the wire's (index, type, value). */
    private record Entry(int index, int type, Object value) {
    }

    /** The strict-parse failure (the mineflayer PartialReadError analog). */
    private static final class PartialRead extends RuntimeException {
        PartialRead(String what) {
            super(what);
        }
    }

    /** A cursor over one raw frame; every read is bounds-checked. */
    private static final class Cursor {
        private final byte[] frame;
        private int at;

        Cursor(byte[] frame, int at) {
            this.frame = frame;
            this.at = at;
        }

        int remaining() {
            return frame.length - at;
        }

        int unsigned() {
            if (remaining() < 1) {
                throw new PartialRead("a byte ran past the frame");
            }
            return frame[at++] & 0xFF;
        }

        int uByte() {
            return unsigned();
        }

        short signedShort() {
            if (remaining() < 2) {
                throw new PartialRead("a short ran past the frame");
            }
            int v = (frame[at] & 0xFF) << 8 | (frame[at + 1] & 0xFF);
            at += 2;
            return (short) v;
        }

        int signedInt() {
            if (remaining() < 4) {
                throw new PartialRead("an int ran past the frame");
            }
            long v = (frame[at] & 0xFFL) << 24 | (frame[at + 1] & 0xFFL) << 16
                    | (frame[at + 2] & 0xFFL) << 8 | (frame[at + 3] & 0xFFL);
            at += 4;
            return (int) v;
        }

        float float32() {
            return Float.intBitsToFloat(signedInt());
        }

        int varint() {
            int value = 0;
            int shift = 0;
            while (true) {
                int b = unsigned();
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
                shift += 7;
                if (shift > 28) {
                    throw new PartialRead("a varint ran past the frame");
                }
            }
        }

        String string() {
            int len = varint();
            if (len < 0 || len > remaining()) {
                throw new PartialRead("a string ran past the frame (len " + len
                        + " vs " + remaining() + " left)");
            }
            String s = new String(frame, at, len, java.nio.charset.StandardCharsets.UTF_8);
            at += len;
            return s;
        }
    }

    /** The reference item-slot form's parsed value. */
    private record Slot(short id, int count, short data) {
        @Override
        public String toString() {
            return id + "x" + count + "@" + data;
        }
    }

    /** The reference SyncedData entry loop: entries until 127, then the
     * boundary MUST sit exactly on the frame's end. */
    private static List<Entry> parseTail(byte[] frame, int tailOffset) {
        Cursor c = new Cursor(frame, tailOffset);
        List<Entry> entries = new ArrayList<>();
        int first = c.unsigned();
        int at = first;
        while (at != 127) {
            int type = (at & 0xE0) >> 5;
            int index = at & 0x1F;
            switch (type) {
                case 0 -> entries.add(new Entry(index, 0, (byte) c.unsigned()));
                case 1 -> entries.add(new Entry(index, 1, c.signedShort()));
                case 2 -> entries.add(new Entry(index, 2, c.signedInt()));
                case 3 -> entries.add(new Entry(index, 3, c.float32()));
                case 4 -> entries.add(new Entry(index, 4, c.string()));
                case 5 -> {
                    short id = c.signedShort();
                    if (id >= 0) {
                        int count = c.unsigned();     // the reference writeItem: byte count
                        short data = c.signedShort(); // then short metadata
                        // then the NBT: TAG_End (0x00) when empty — the pin
                        // only needs the empty form (the trigger's stack
                        // carries no payload).
                        int tag = c.unsigned();
                        if (tag != 0) {
                            throw new PartialRead("unexpected NBT tag " + tag
                                    + " — the pin's slot form is the empty one");
                        }
                        entries.add(new Entry(index, 5, new Slot(id, count, data)));
                    } else {
                        entries.add(new Entry(index, 5, new Slot((short) -1, 0, (short) 0)));
                    }
                }
                case 6 -> entries.add(new Entry(index, 6,
                        new int[]{c.signedInt(), c.signedInt(), c.signedInt()}));
                case 7 -> entries.add(new Entry(index, 7,
                        new float[]{c.float32(), c.float32(), c.float32()}));
                default -> throw new PartialRead("unknown metadata type " + type);
            }
            at = c.unsigned();
        }
        if (c.remaining() != 0) {
            throw new PartialRead("the terminator left " + c.remaining()
                    + " unread bytes — the frame desynced");
        }
        return entries;
    }

    /** The frame's entity-id varint (the id filter — the natural cycle and
     * the worldgen bodies send their own frames; the pin reads only ITS
     * body's). */
    private static int frameEntityId(byte[] frame) {
        Cursor c = new Cursor(frame, 0);
        c.varint(); // packet id
        return c.varint();
    }

    /** The 0x0C tail offset: varint id, 16 raw uuid bytes (the reference's
     * writeUuid — two longs; ML-021's fix: this parser once codified the
     * string form, exactly the defect the capture localized), 12 bytes of
     * fixed-point position, 2 angle bytes, 2 bytes held item. */
    private static int namedSpawnTailOffset(byte[] frame) {
        Cursor c = new Cursor(frame, 0);
        c.varint();          // packet id
        c.varint();          // entity id
        skipUuid16(c);       // the reference readUuid: two longs
        c.signedInt();
        c.signedInt();
        c.signedInt();       // fixed-point x/y/z
        c.unsigned();        // yaw
        c.unsigned();        // pitch
        c.signedShort();     // held item
        return c.at;
    }

    /** The 16 raw uuid bytes (the reference's readUuid). */
    private static void skipUuid16(Cursor c) {
        c.signedInt();
        c.signedInt();
        c.signedInt();
        c.signedInt();
    }

    /** The 0x0F tail offset: varint id, byte kind, 12 bytes position, 3
     * angle bytes, 6 bytes velocity. */
    private static int spawnMobTailOffset(byte[] frame) {
        Cursor c = new Cursor(frame, 0);
        c.varint();      // packet id
        c.varint();      // entity id
        c.unsigned();    // kind
        c.signedInt();
        c.signedInt();
        c.signedInt();   // x/y/z
        c.unsigned();
        c.unsigned();
        c.unsigned();    // yaw/pitch/head
        c.signedShort();
        c.signedShort();
        c.signedShort(); // velocity
        return c.at;
    }

    /** The 0x1C tail offset: varint packet id, varint entity id. */
    private static int entityMetadataTailOffset(byte[] frame) {
        Cursor c = new Cursor(frame, 0);
        c.varint();
        c.varint();
        return c.at;
    }

    // ---- the frame capture ---------------------------------------------------

    /** Scans the wire until a frame of the wanted type passes the filter;
     * every other frame is discarded (the capture loop is itself a
     * strict consumer — a malformed frame anywhere in the stream fails
     * the read and the test). */
    private byte[] awaitFrame(TestClient18 client, int packetId,
                               java.util.function.Predicate<byte[]> filter,
                               String what) throws Exception {
        client.setReadTimeout(200);
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            byte[] frame;
            try {
                frame = client.readPacket();
            } catch (Exception timeout) {
                continue;
            }
            if (client.readPacketId(frame) == packetId && filter.test(frame)) {
                return frame;
            }
        }
        throw new AssertionError("frame not observed: " + what);
    }

    private static void assertType(Entry e, int type, int index) {
        assertEquals(type, e.type(), "entry type at index " + e.index());
        assertEquals(index, e.index(), "entry index");
    }

    // ---- the pins -------------------------------------------------------------

    /** ML-021's own packet: the 0x0C Named Entity Spawn's metadata tail on
     * mutual join — the reference sends the player's SyncedData entries +
     * 127; the engine sends the empty set + 127 (legal). The parse must
     * land exactly on the boundary — the observed cycle-3 failure was a
     * PartialReadError here with four mutual players, root-caused this
     * cycle: the uuid rode a length-prefixed string (21 extra bytes) where
     * the reference's writeUuid is 16 raw bytes — every strict client
     * entered the metadata walk 21 bytes early. Fixed in trackPlayer; this
     * parse reads the reference form and the boundary assert is the
     * regression guard. */
    @Test
    void theNamedSpawnCarriesTheReferenceEmptyTail() throws Exception {
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

            byte[] bobSeesAlice = awaitFrame(bob, Protocol18.S2C_NAMED_SPAWN,
                    f -> true, "Bob's 0x0C for Alice");
            List<Entry> tail = parseTail(bobSeesAlice, namedSpawnTailOffset(bobSeesAlice));
            assertTrue(tail.isEmpty(), "the engine's 0x0C carries the empty set: " + tail);
            byte[] aliceSeesBob = awaitFrame(alice, Protocol18.S2C_NAMED_SPAWN,
                    f -> true, "Alice's 0x0C for Bob");
            assertTrue(parseTail(aliceSeesBob, namedSpawnTailOffset(aliceSeesBob)).isEmpty(),
                    "the mutual 0x0C carries the empty set both ways");
        }
        awaitEmptyServer();
    }

    /** The mob spawn's metadata family, one live frame per watched type:
     * the named zombie (BYTE flags, FLOAT health, STRING name, BYTE
     * name-visible), the pig (the age BYTE), the villager (the INT
     * profession), the enderman (the SHORT carried block + the BYTE
     * scream). */
    @Test
    void theMobMetadataCarriesEveryWatchedType() throws Exception {
        try (TestClient18 observer = new TestClient18("127.0.0.1", adapter.boundPort())) {
            observer.sendHandshake(47, 2);
            observer.sendLoginStart("Observer");
            observer.readLoginSuccess();
            observer.readUntilPositionAndLook();
            awaitPlayers("Observer");
            var anchor = server.playerRegistry().byName("Observer").orElseThrow().position();
            double ax = anchor.x();
            double az = anchor.z();

            // The named zombie: spawned UNNAMED (the name pair lands after,
            // through the update packet — the reference's collectDirty flow).
            AtomicInteger zombieId = new AtomicInteger();
            server.ticker().submit(() -> {
                var zombie = server.mobs().spawnAt(
                        net.zaminmc.torch.server.entity.MobType.ZOMBIE,
                        new net.zaminmc.torch.util.Position(ax + 2, anchor.y(), az + 2));
                zombieId.set(zombie.entityId());
            });
            awaitCondition(() -> zombieId.get() != 0, "the zombie landed");
            byte[] zombieFrame = awaitFrame(observer, Protocol18.S2C_SPAWN_MOB,
                    f -> frameEntityId(f) == zombieId.get(), "the zombie's 0x0F");
            List<Entry> zombieTail = parseTail(zombieFrame, spawnMobTailOffset(zombieFrame));
            // The spawn frame carries the base family (the name pair rides
            // the follow-up update — the reference's collectDirty flow).
            assertType(zombieTail.get(0), 0, 0);
            assertType(zombieTail.get(1), 3, 6);
            // The zombie's own block (ZombieEntity.registerSyncedData):
            // baby 12, villager-skin 13, converting 14 — a plain adult.
            assertTrue(zombieTail.stream().anyMatch(e -> e.type() == 0 && e.index() == 12),
                    "the zombie's baby byte present: " + zombieTail);
            assertTrue(zombieTail.stream().anyMatch(e -> e.type() == 0 && e.index() == 13),
                    "the villager-skin byte present: " + zombieTail);
            assertTrue(zombieTail.stream().anyMatch(e -> e.type() == 0 && e.index() == 14),
                    "the converting byte present: " + zombieTail);

            // The STRING type's live form: the 0x0C's uuid string is parsed
            // STRICTLY on every named-spawn frame (the same writeString form
            // the metadata context uses); the metadata-context STRING's live
            // trigger (the custom-name/owner-string flips) has no update
            // publication in the engine — the family-owned "CustomName rows"
            // ledgered tail, recorded here, never silently claimed. The
            // wolf/horse writers carry the identical writeString bytes
            // (writeWolfMetadata / writeHorseMetadata, surveyed).

            // The pig: the age BYTE at 12 beside the flags and health.
            AtomicInteger pigId = new AtomicInteger();
            server.ticker().submit(() -> {
                var pig = server.mobs().spawnAt(
                        net.zaminmc.torch.server.entity.MobType.PIG,
                        new net.zaminmc.torch.util.Position(ax - 2, anchor.y(), az - 2));
                pigId.set(pig.entityId());
            });
            awaitCondition(() -> pigId.get() != 0, "the pig landed");
            byte[] pigFrame = awaitFrame(observer, Protocol18.S2C_SPAWN_MOB,
                    f -> frameEntityId(f) == pigId.get(), "the pig's 0x0F");
            List<Entry> pigTail = parseTail(pigFrame, spawnMobTailOffset(pigFrame));
            assertTrue(pigTail.size() >= 3, "flags+health+age: " + pigTail);
            assertTrue(pigTail.stream().anyMatch(e -> e.type() == 0 && e.index() == 12),
                    "the passive age byte present");

            // The villager: the INT profession at 16.
            AtomicInteger villagerId = new AtomicInteger();
            server.ticker().submit(() -> {
                var villager = server.mobs().spawnAt(
                        net.zaminmc.torch.server.entity.MobType.VILLAGER,
                        new net.zaminmc.torch.util.Position(ax + 3, anchor.y(), az - 3));
                villagerId.set(villager.entityId());
            });
            awaitCondition(() -> villagerId.get() != 0, "the villager landed");
            byte[] villagerFrame = awaitFrame(observer, Protocol18.S2C_SPAWN_MOB,
                    f -> frameEntityId(f) == villagerId.get(), "the villager's 0x0F");
            List<Entry> villagerTail = parseTail(villagerFrame, spawnMobTailOffset(villagerFrame));
            assertTrue(villagerTail.stream().anyMatch(e -> e.type() == 2 && e.index() == 16),
                    "the profession INT present: " + villagerTail);

            // The enderman: the SHORT carried block at 16 + the BYTE scream at 18.
            AtomicInteger endermanId = new AtomicInteger();
            server.ticker().submit(() -> {
                var enderman = server.mobs().spawnAt(
                        net.zaminmc.torch.server.entity.MobType.ENDERMAN,
                        new net.zaminmc.torch.util.Position(ax - 3, anchor.y(), az + 3));
                endermanId.set(enderman.entityId());
            });
            awaitCondition(() -> endermanId.get() != 0, "the enderman landed");
            byte[] endermanFrame = awaitFrame(observer, Protocol18.S2C_SPAWN_MOB,
                    f -> frameEntityId(f) == endermanId.get(), "the enderman's 0x0F");
            List<Entry> endermanTail = parseTail(endermanFrame, spawnMobTailOffset(endermanFrame));
            assertTrue(endermanTail.stream().anyMatch(e -> e.type() == 1 && e.index() == 16),
                    "the carried-block SHORT present: " + endermanTail);
            assertTrue(endermanTail.stream().anyMatch(e -> e.type() == 0 && e.index() == 18),
                    "the scream BYTE present: " + endermanTail);

            // The cleanup: the pin leaves no bodies behind.
            server.ticker().submit(() -> server.mobs().removeHostiles());
            awaitCondition(() -> server.mobs().all().stream()
                    .noneMatch(m -> m.type().hostile), "the pin's bodies cleared");
        }
        awaitEmptyServer();
    }

    /** The SLOT type (5) over the item entities' stack metadata: the
     * reference writeItem form — short id, byte count, short data, then
     * the empty NBT's single TAG_End. */
    @Test
    void theSlotMetadataRidesTheReferenceItemForm() throws Exception {
        try (TestClient18 observer = new TestClient18("127.0.0.1", adapter.boundPort())) {
            observer.sendHandshake(47, 2);
            observer.sendLoginStart("Observer");
            observer.readLoginSuccess();
            observer.readUntilPositionAndLook();
            awaitPlayers("Observer");
            var anchor = server.playerRegistry().byName("Observer").orElseThrow().position();

            AtomicInteger itemId = new AtomicInteger();
            server.ticker().submit(() -> {
                var dropped = server.itemEntities().spawnDropAtBlock(
                        new net.zaminmc.torch.util.Position(anchor.x() + 1, anchor.y() + 1, anchor.z()),
                        new net.zaminmc.torch.item.ItemStack(
                                net.zaminmc.torch.server.item.BuiltinItems.STICK, 3), 0);
                itemId.set(dropped.entityId());
            });
            awaitCondition(() -> itemId.get() != 0, "the drop landed");
            byte[] frame = awaitFrame(observer, Protocol18.S2C_ENTITY_METADATA,
                    f -> frameEntityId(f) == itemId.get(), "the dropped stack's 0x1C");
            List<Entry> tail = parseTail(frame, entityMetadataTailOffset(frame));
            assertEquals(1, tail.size(), "the item entity's slot entry: " + tail);
            assertType(tail.get(0), 5, 10);
            Slot slot = (Slot) tail.get(0).value();
            assertEquals(280, slot.id(), "STICK's legacy id on the wire");
            assertEquals(3, slot.count(), "three units on the wire");
            assertEquals(0, slot.data(), "the plain variant's data field");
        }
        awaitEmptyServer();
    }

    /** The ROTATION type (7) over the armor stand's pose block: three
     * floats per entry — pitch, yaw, roll, the reference's own order. */
    @Test
    void theArmorStandPoseRidesTheReferenceRotationForm() throws Exception {
        try (TestClient18 observer = new TestClient18("127.0.0.1", adapter.boundPort())) {
            observer.sendHandshake(47, 2);
            observer.sendLoginStart("Observer");
            observer.readLoginSuccess();
            observer.readUntilPositionAndLook();
            awaitPlayers("Observer");
            var anchor = server.playerRegistry().byName("Observer").orElseThrow().position();
            var clicked = anchor.toBlockPosition();
            boolean placed = server.armorStands().place(
                    new net.zaminmc.torch.block.BlockPosition(
                            clicked.x() + 1, clicked.y(), clicked.z()),
                    1, 0f, new Random(1234));
            assertTrue(placed, "the stand placed");
            byte[] frame = awaitFrame(observer, Protocol18.S2C_ENTITY_METADATA,
                    f -> true, "the stand's pose metadata");
            List<Entry> tail = parseTail(frame, entityMetadataTailOffset(frame));
            assertTrue(tail.size() >= 6, "the six pose entries: " + tail);
            for (Entry e : tail) {
                if (e.type() == 7) {
                    float[] rot = (float[]) e.value();
                    assertEquals(3, rot.length, "three floats per pose");
                }
            }
            assertTrue(tail.stream().filter(e -> e.type() == 7).count() >= 6,
                    "the rotation family covers the whole pose block: " + tail);
            // The cleanup.
            server.armorStands().allStands().forEach(stand ->
                    server.armorStands().attack(stand, true));
        }
        awaitEmptyServer();
    }
}
