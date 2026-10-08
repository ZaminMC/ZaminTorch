package net.zamin.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * A minimal scripted 1.8.8 test client speaking real wire frames over a real
 * socket. It exists to prove the server's observable protocol behavior without
 * launching a real Minecraft client (real-client harness is a later slice).
 *
 * <p>Deliberately raw: DataInputStream + manual varint keeps the test honest
 * about what the wire actually carries.</p>
 */
final class TestClient18 implements AutoCloseable {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    TestClient18(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.socket.setTcpNoDelay(true);
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    // ---- framing ------------------------------------------------------------

    private void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 28) {
                throw new IOException("VarInt too large");
            }
        }
    }

    private void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Sends one framed packet from the given payload. */
    void sendPacket(byte[] payload) throws IOException {
        writeVarInt(out, payload.length);
        out.write(payload);
        out.flush();
    }

    /** Reads one framed packet, returns its payload (packet id is the first varint). */
    byte[] readPacket() throws IOException {
        int length = readVarInt(in);
        byte[] payload = new byte[length];
        in.readFully(payload);
        if (WIRE_TRACE) {
            ByteBuf peek = Unpooled.wrappedBuffer(payload);
            System.out.println("[trace-client] frame len=" + length + " id=0x"
                    + Integer.toHexString(ByteBufOps.readVarInt(peek)) + " at " + System.currentTimeMillis());
        }
        return payload;
    }

    static final boolean WIRE_TRACE = Boolean.getBoolean("wireTrace")
            || System.getenv("WIRE_TRACE") != null;

    int readPacketId(byte[] payload) {
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        return ByteBufOps.readVarInt(buffer);
    }

    // ---- state transitions ---------------------------------------------------

    void sendHandshake(int protocolVersion, int nextState) throws IOException {
        ByteBuf body = Unpooled.buffer(64);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_HANDSHAKE);
        ByteBufOps.writeVarInt(body, protocolVersion);
        ByteBufOps.writeString(body, "127.0.0.1");
        body.writeShort(port());
        ByteBufOps.writeVarInt(body, nextState);
        sendPacket(bodyToBytes(body));
    }

    private int port() {
        return socket.getPort();
    }

    void sendStatusRequest() throws IOException {
        ByteBuf body = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_STATUS_REQUEST);
        sendPacket(bodyToBytes(body));
    }

    void sendStatusPing(long payload) throws IOException {
        ByteBuf body = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_STATUS_PING);
        body.writeLong(payload);
        sendPacket(bodyToBytes(body));
    }

    void sendLoginStart(String name) throws IOException {
        ByteBuf body = Unpooled.buffer(32);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_LOGIN_START);
        ByteBufOps.writeString(body, name);
        sendPacket(bodyToBytes(body));
    }

    void sendPosition(double x, double y, double z, boolean onGround) throws IOException {
        ByteBuf body = Unpooled.buffer(40);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_PLAYER_POSITION);
        body.writeDouble(x);
        body.writeDouble(y);
        body.writeDouble(z);
        body.writeBoolean(onGround);
        sendPacket(bodyToBytes(body));
    }

    void sendKeepAliveResponse(int id) throws IOException {
        ByteBuf body = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_KEEP_ALIVE);
        ByteBufOps.writeVarInt(body, id);
        sendPacket(bodyToBytes(body));
    }

    private static byte[] bodyToBytes(ByteBuf body) {
        byte[] bytes = new byte[body.readableBytes()];
        body.readBytes(bytes);
        body.release();
        return bytes;
    }

    String readStatusResponse() throws IOException {
        byte[] payload = readPacket();
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        int id = ByteBufOps.readVarInt(buffer);
        if (id != Protocol18.S2C_STATUS_RESPONSE) {
            throw new IOException("Expected status response, got " + id);
        }
        return ByteBufOps.readString(buffer, 4096);
    }

    long readPong() throws IOException {
        byte[] payload = readPacket();
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        int id = ByteBufOps.readVarInt(buffer);
        if (id != Protocol18.S2C_STATUS_PONG) {
            throw new IOException("Expected pong, got " + id);
        }
        return buffer.readLong();
    }

    String readLoginDisconnect() throws IOException {
        byte[] payload = readPacket();
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        int id = ByteBufOps.readVarInt(buffer);
        if (id != Protocol18.S2C_LOGIN_DISCONNECT) {
            throw new IOException("Expected login disconnect, got " + id);
        }
        return ByteBufOps.readString(buffer, 1024);
    }

    /** Reads packets until login success; returns the player name it contains. */
    String readLoginSuccess() throws IOException {
        byte[] payload = readPacket();
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        int id = ByteBufOps.readVarInt(buffer);
        if (id != Protocol18.S2C_LOGIN_SUCCESS) {
            throw new IOException("Expected login success, got " + id);
        }
        String uuid = ByteBufOps.readString(buffer, 64);
        String name = ByteBufOps.readString(buffer, 64);
        return uuid + "/" + name;
    }

    /** Skips world-sync packets until the initial position packet arrives. */
    void readUntilPositionAndLook() throws IOException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            byte[] payload = readPacket();
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            int id = ByteBufOps.readVarInt(buffer);
            if (id == Protocol18.S2C_PLAYER_POSITION_AND_LOOK) {
                return;
            }
        }
        throw new IOException("Timed out waiting for position packet");
    }

    /** Raw frame access for diagnostics: one framed packet body (id included). */
    byte[] readPacketForDiagnostics() throws IOException {
        return readPacket();
    }

    /** Reads packets until one with the expected id arrives (deadline-bounded). */
    byte[] readPacketOfType(int expectedId, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            byte[] payload = readPacket();
            if (readPacketId(payload) == expectedId) {
                return payload;
            }
        }
        throw new IOException("Timed out waiting for packet id " + expectedId);
    }

    /** Reads packets until one with any of the expected ids arrives. */
    byte[] readPacketOfTypes(int[] expectedIds, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            byte[] payload = readPacket();
            int id = readPacketId(payload);
            for (int expectedId : expectedIds) {
                if (id == expectedId) {
                    return payload;
                }
            }
        }
        throw new IOException("Timed out waiting for packet ids "
                + java.util.Arrays.toString(expectedIds));
    }

    void sendDigging(int status, int x, int y, int z, int face) throws IOException {
        ByteBuf body = Unpooled.buffer(24);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_PLAYER_DIGGING);
        body.writeByte(status);
        ByteBufOps.writePackedBlockPosition(body, x, y, z);
        body.writeByte(face);
        sendPacket(bodyToBytes(body));
    }

    void sendBlockPlacement(int x, int y, int z, int face, int heldItemId) throws IOException {
        ByteBuf body = Unpooled.buffer(24);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_PLAYER_BLOCK_PLACEMENT);
        ByteBufOps.writePackedBlockPosition(body, x, y, z);
        body.writeByte(face);
        body.writeShort(heldItemId);  // 1.8 slot: id
        if (heldItemId >= 0) {
            body.writeByte(1);        // count
            body.writeShort(0);       // damage
            body.writeByte(0);        // NBT marker: 0 = none (TAG_End)
        }
        sendPacket(bodyToBytes(body));
    }

    void sendChat(String text) throws IOException {
        ByteBuf body = Unpooled.buffer(16 + text.length());
        ByteBufOps.writeVarInt(body, Protocol18.C2S_CHAT_MESSAGE);
        ByteBufOps.writeString(body, text);
        sendPacket(bodyToBytes(body));
    }

    /** Use Entity (0x02): mouse 0 interact / 1 attack / 2 interact-at (+3 f32). */
    void sendUseEntity(int target, int mouse) throws IOException {
        ByteBuf body = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_USE_ENTITY);
        ByteBufOps.writeVarInt(body, target);
        ByteBufOps.writeVarInt(body, mouse);
        if (mouse == Protocol18.USE_ENTITY_INTERACT_AT) {
            body.writeFloat(0.5f);
            body.writeFloat(1.0f);
            body.writeFloat(0.5f);
        }
        sendPacket(bodyToBytes(body));
    }

    /** Arm Animation (0x0A): the swing gesture, empty payload. */
    void sendArmAnimation() throws IOException {
        ByteBuf body = Unpooled.buffer(4);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_ARM_ANIMATION);
        sendPacket(bodyToBytes(body));
    }

    /** Reads a Spawn Mob packet, returning {entityId, type, x32, y32, z32, health}. */
    int[] readSpawnMob(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_SPAWN_MOB, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int entityId = ByteBufOps.readVarInt(buffer);
        int type = buffer.readByte() & 0xFF;
        int x = buffer.readInt();
        int y = buffer.readInt();
        int z = buffer.readInt();
        buffer.readByte(); // yaw
        buffer.readByte(); // pitch
        buffer.readByte(); // head pitch
        buffer.readShort(); // velocity x
        buffer.readShort(); // velocity y
        buffer.readShort(); // velocity z
        int health = -1;
        while (true) {
            int header = buffer.readUnsignedByte();
            if (header == Protocol18.METADATA_TERMINATOR) {
                break;
            }
            int mtype = header >> 5;
            int index = header & 0x1F;
            switch (mtype) {
                case Protocol18.METADATA_TYPE_BYTE -> buffer.readByte();
                case Protocol18.METADATA_TYPE_FLOAT -> {
                    float value = buffer.readFloat();
                    if (index == Protocol18.LIVING_HEALTH_METADATA_INDEX) {
                        health = (int) value;
                    }
                }
                case Protocol18.METADATA_TYPE_SLOT -> {
                    int id = buffer.readShort();
                    if (id != -1) {
                        buffer.readByte();
                        buffer.readShort();
                        SlotNbt.skip(buffer); // structural parse of the optional NBT
                    }
                }
                default -> throw new IOException("Unexpected mob metadata type " + mtype);
            }
        }
        return new int[]{entityId, type, x, y, z, health};
    }

    /**
     * Reads a Spawn Mob packet and returns its RAW DataWatcher entries as
     * {@code {index, type, valueFixed}} rows (floats x100, others as-is).
     * The regression net for the vanilla client's DataWatcher cross-check:
     * 1.8's client throws when an entry's type does not match the type its
     * entity class registered for that index, so a wrong index/type pair
     * crashes real clients while community parsers sail through.
     */
    int[][] readSpawnMobDataWatcher(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_SPAWN_MOB, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        ByteBufOps.readVarInt(buffer); // entity id
        buffer.readByte(); // type
        buffer.readInt();
        buffer.readInt();
        buffer.readInt();
        buffer.readByte();
        buffer.readByte();
        buffer.readByte();
        buffer.readShort();
        buffer.readShort();
        buffer.readShort();
        java.util.List<int[]> rows = new java.util.ArrayList<>();
        while (true) {
            int header = buffer.readUnsignedByte();
            if (header == Protocol18.METADATA_TERMINATOR) {
                break;
            }
            int type = header >> 5;
            int index = header & 0x1F;
            switch (type) {
                case Protocol18.METADATA_TYPE_BYTE ->
                        rows.add(new int[]{index, type, buffer.readByte()});
                case Protocol18.METADATA_TYPE_FLOAT ->
                        rows.add(new int[]{index, type, Math.round(buffer.readFloat() * 100)});
                case Protocol18.METADATA_TYPE_SLOT -> {
                    int id = buffer.readShort();
                    if (id != -1) {
                        buffer.readByte();
                        buffer.readShort();
                        SlotNbt.skip(buffer);
                    }
                    rows.add(new int[]{index, type, id});
                }
                default -> throw new IOException("Unexpected metadata type " + type);
            }
        }
        return rows.toArray(new int[0][]);
    }

    /** Reads a Spawn Mob packet of the given legacy type, skipping other spawns. */
    int[] readSpawnMobOfType(int legacyType, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int[] spawn = readSpawnMob(Math.max(1, deadline - System.currentTimeMillis()));
            if (spawn[1] == legacyType) {
                return spawn;
            }
        }
        throw new IOException("Timed out waiting for a mob of type " + legacyType);
    }

    /** Reads an Entity Status packet, returning {entityId, status}. */
    int[] readEntityStatus(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_ENTITY_STATUS, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int entityId = buffer.readInt();
        int status = buffer.readByte();
        return new int[]{entityId, status};
    }

    /** Reads a Rel Move Look packet, returning {entityId, dx, dy, dz, onGround}. */
    int[] readRelMoveLook(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_REL_ENTITY_MOVE_LOOK, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int entityId = ByteBufOps.readVarInt(buffer);
        int dx = buffer.readByte();
        int dy = buffer.readByte();
        int dz = buffer.readByte();
        buffer.readByte(); // yaw
        buffer.readByte(); // pitch
        boolean onGround = buffer.readBoolean();
        return new int[]{entityId, dx, dy, dz, onGround ? 1 : 0};
    }

    /** Reads a Destroy Entities packet, returning the destroyed entity ids. */
    /**
     * Reads the next Block Change (kind 0: {0,x,y,z,state}) or Destroy
     * Entities (kind 1: {1,id...}) — engine-driven landings interleave the
     * two, so one drain loop must consume both without skipping either.
     */
    int[] readBlockChangeOrDestroy(long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            byte[] payload = readPacketOfTypes(
                    new int[]{Protocol18.S2C_BLOCK_CHANGE, Protocol18.S2C_DESTROY_ENTITIES},
                    Math.max(1, deadline - System.currentTimeMillis()));
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            int packetId = ByteBufOps.readVarInt(buffer);
            if (packetId == Protocol18.S2C_BLOCK_CHANGE) {
                int[] pos = ByteBufOps.readPackedBlockPosition(buffer);
                int stateId = ByteBufOps.readVarInt(buffer);
                return new int[]{0, pos[0], pos[1], pos[2], stateId >> 4};
            }
            int count = ByteBufOps.readVarInt(buffer);
            int[] out = new int[1 + count];
            out[0] = 1;
            for (int i = 0; i < count; i++) {
                out[1 + i] = ByteBufOps.readVarInt(buffer);
            }
            return out;
        }
        throw new IOException("Timed out waiting for a block change or destroy");
    }

    int[] readDestroyEntities(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_DESTROY_ENTITIES, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int count = ByteBufOps.readVarInt(buffer);
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            ids[i] = ByteBufOps.readVarInt(buffer);
        }
        return ids;
    }

    /** Reads an Animation packet, returning {entityId, animation}. */
    int[] readAnimation(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_ANIMATION, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int entityId = ByteBufOps.readVarInt(buffer);
        int animation = buffer.readByte() & 0xFF;
        return new int[]{entityId, animation};
    }

    /** Reads a Named Sound Effect packet, returning {name, x8, y8, z8}. */
    String[] readNamedSound(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_NAMED_SOUND_EFFECT, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        String name = ByteBufOps.readString(buffer, 256);
        int x = buffer.readInt();
        int y = buffer.readInt();
        int z = buffer.readInt();
        return new String[]{name, String.valueOf(x), String.valueOf(y), String.valueOf(z)};
    }

    /** Sends the Entity Action posture gesture (0 start-sneak, 2 start-sprint...). */
    void sendEntityAction(int action) throws IOException {
        ByteBuf body = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_ENTITY_ACTION);
        ByteBufOps.writeVarInt(body, 0); // entity id (the engine ignores it this slice)
        ByteBufOps.writeVarInt(body, action);
        ByteBufOps.writeVarInt(body, 0); // jump boost
        sendPacket(bodyToBytes(body));
    }

    /**
     * Reads a World Particles packet, returning
     * {id, xF, yF, zF, count, data0, data1} — the position as fixed ×32 ints
     * for assertion stability, the data payload padded with zeros.
     */
    int[] readWorldParticles(int particleId, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            byte[] payload = readPacketOfType(Protocol18.S2C_WORLD_PARTICLES,
                    Math.max(1, deadline - System.currentTimeMillis()));
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            ByteBufOps.readVarInt(buffer); // packet id
            int id = buffer.readInt();
            buffer.readBoolean(); // longDistance
            float x = buffer.readFloat();
            float y = buffer.readFloat();
            float z = buffer.readFloat();
            buffer.readFloat(); // offsetX
            buffer.readFloat(); // offsetY
            buffer.readFloat(); // offsetZ
            buffer.readFloat(); // particleData
            int count = buffer.readInt();
            int d0 = 0, d1 = 0;
            if (id == Protocol18.PARTICLE_ICON_CRACK) {
                d0 = ByteBufOps.readVarInt(buffer);
                d1 = ByteBufOps.readVarInt(buffer);
            } else if (id == Protocol18.PARTICLE_BLOCK_CRACK) {
                d0 = ByteBufOps.readVarInt(buffer);
            }
            if (particleId < 0 || id == particleId) {
                return new int[]{id, (int) (x * 32), (int) (y * 32), (int) (z * 32),
                        count, d0, d1};
            }
        }
        throw new IOException("Timed out waiting for particle " + particleId);
    }

    /** Reads a Time Update packet, returning {age, timeOfDay}. */
    long[] readTimeUpdate(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_TIME_UPDATE, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        long age = buffer.readLong();
        long timeOfDay = buffer.readLong();
        return new long[]{age, timeOfDay};
    }

    /** Reads a Chat packet, returning the raw JSON text. */
    String readChatLine(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_CHAT, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        return ByteBufOps.readString(buffer, 4096);
    }

    /** Reads a Block Change packet, returning {x, y, z, legacyId}. The wire
     * carries the packed state (id << 4) | metadata; unpacked here. */
    /**
     * Reads Block Change packets until one targets the given position, then
     * asserts its state. Engine-driven rule changes (grass decay, falling
     * conversions) share the wire with player-driven commits, so callers must
     * name the position they wait for instead of counting packets.
     */
    int[] readBlockChangeAt(int x, int y, int z, long timeoutMs) throws IOException {
        return readBlockChangeAt(x, y, z, -1, timeoutMs);
    }

    /** Position + expected legacy state; state -1 accepts any. */
    int[] readBlockChangeAt(int x, int y, int z, int expectedLegacy, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int[] change = readBlockChange(Math.max(1, deadline - System.currentTimeMillis()));
            if (change[0] == x && change[1] == y && change[2] == z
                    && (expectedLegacy < 0 || change[3] == expectedLegacy)) {
                return change;
            }
        }
        throw new IOException("Timed out waiting for a block change at ("
                + x + "," + y + "," + z + ") state " + expectedLegacy);
    }

    int[] readBlockChange(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_BLOCK_CHANGE, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int[] pos = ByteBufOps.readPackedBlockPosition(buffer);
        int stateId = ByteBufOps.readVarInt(buffer);
        int legacy = stateId >> 4;
        if (WIRE_TRACE) {
            System.out.println("[trace-client] block change parsed x=" + pos[0] + " y=" + pos[1]
                    + " z=" + pos[2] + " legacy=" + legacy + " raw=" + java.util.Arrays.toString(payload));
        }
        return new int[]{pos[0], pos[1], pos[2], legacy};
    }

    // ---- survival slice helpers -------------------------------------------------

    void sendHeldItemChange(int slot) throws IOException {
        ByteBuf body = Unpooled.buffer(6);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_HELD_ITEM_CHANGE);
        body.writeShort(slot);
        sendPacket(bodyToBytes(body));
    }

    /** Sends a Click Window packet (community-verified protocol 47 layout). */
    void sendWindowClick(int wireSlot, int button, int mode, int actionNumber) throws IOException {
        sendWindowClick(Protocol18.INVENTORY_WINDOW_ID, wireSlot, button, mode, actionNumber);
    }

    /** Sends a Click Window packet for an explicit window id (containers). */
    void sendWindowClick(int windowId, int wireSlot, int button, int mode,
                         int actionNumber) throws IOException {
        ByteBuf body = Unpooled.buffer(24);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_WINDOW_CLICK);
        body.writeByte(windowId);
        body.writeShort(wireSlot);
        body.writeByte(button);
        body.writeShort(actionNumber);
        body.writeByte(mode);
        body.writeShort(-1); // claimed item: empty (tests never predict)
        sendPacket(bodyToBytes(body));
    }

    /** Reads a Confirm Transaction packet, returning {windowId, actionNumber, accepted}. */
    int[] readConfirmTransaction(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_CONFIRM_TRANSACTION, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int windowId = buffer.readByte();
        int action = buffer.readShort();
        boolean accepted = buffer.readBoolean();
        return new int[]{windowId, action, accepted ? 1 : 0};
    }

    /** Reads the cursor Set Slot (window -1, slot -1), returning {slot, itemId, count}. */
    int[] readCursorSlot(long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            byte[] payload = readPacket();
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            int id = ByteBufOps.readVarInt(buffer);
            if (id != Protocol18.S2C_SET_SLOT) {
                continue;
            }
            int windowId = buffer.readByte();
            int slot = buffer.readShort();
            if (windowId != -1 || slot != -1) {
                continue; // some other slot update (not expected in this suite)
            }
            int itemId = buffer.readShort();
            int count = itemId == -1 ? -1 : buffer.readUnsignedByte();
            return new int[]{slot, itemId, count};
        }
        throw new IOException("Timed out waiting for cursor slot");
    }

    /** Reads a Spawn Entity packet for an item, returning {entityId, type, x32, y32, z32, data, vx, vy, vz}. */
    int[] readSpawnItem(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_SPAWN_ENTITY, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int entityId = ByteBufOps.readVarInt(buffer);
        int type = buffer.readByte();
        int x = buffer.readInt();
        int y = buffer.readInt();
        int z = buffer.readInt();
        buffer.readByte(); // pitch
        buffer.readByte(); // yaw
        int data = buffer.readInt();
        int vx = 0, vy = 0, vz = 0;
        if (data != 0) {
            vx = buffer.readShort();
            vy = buffer.readShort();
            vz = buffer.readShort();
        }
        return new int[]{entityId, type, x, y, z, data, vx, vy, vz};
    }

    /** Reads a Spawn Entity packet carrying the given object data (legacy item id). */
    int[] readSpawnItemOfType(int data, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int[] spawn = readSpawnItem(Math.max(1, deadline - System.currentTimeMillis()));
            if (spawn[5] == data) {
                return spawn;
            }
        }
        throw new IOException("Timed out waiting for an item spawn with data " + data);
    }

    /**
     * Reads a Spawn Entity (0x0E) of the given object type (e.g. 70 = falling
     * block), returning {entityId, type, x, y, z, objectData, vx, vy, vz}.
     */
    int[] readSpawnObjectOfType(int objectType, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            byte[] payload = readPacketOfType(Protocol18.S2C_SPAWN_ENTITY,
                    Math.max(1, deadline - System.currentTimeMillis()));
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            ByteBufOps.readVarInt(buffer); // packet id
            int entityId = ByteBufOps.readVarInt(buffer);
            int type = buffer.readByte();
            int x = buffer.readInt();
            int y = buffer.readInt();
            int z = buffer.readInt();
            buffer.readByte(); // pitch
            buffer.readByte(); // yaw
            int data = buffer.readInt();
            int vx = 0, vy = 0, vz = 0;
            if (data != 0) {
                vx = buffer.readShort();
                vy = buffer.readShort();
                vz = buffer.readShort();
            }
            if (type == objectType) {
                return new int[]{entityId, type, x, y, z, data, vx, vy, vz};
            }
        }
        throw new IOException("Timed out waiting for a spawn of object type " + objectType);
    }

    /** Reads a Collect Item packet, returning {collectedEntityId, collectorEntityId}. */
    int[] readCollectItem(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_COLLECT_ITEM, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int collected = ByteBufOps.readVarInt(buffer);
        int collector = ByteBufOps.readVarInt(buffer);
        return new int[]{collected, collector};
    }

    /**
     * Reads a Window Items packet. Returns {windowId, slotCount,
     * firstNonEmptyWireSlot, firstNonEmptyItemId, firstNonEmptyCount,
     * firstNonEmptyDamage} (-1 when everything is empty).
     */
    int[] readWindowItems(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_WINDOW_ITEMS, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int windowId = buffer.readByte();
        int count = buffer.readShort();
        int slot = -1, itemId = -1, itemCount = -1, itemDamage = -1;
        for (int i = 0; i < count; i++) {
            int id = buffer.readShort();
            if (id == -1) {
                continue;
            }
            byte stackCount = buffer.readByte();
            int damage = buffer.readShort();
            SlotNbt.skip(buffer); // structural parse of the optional NBT
            if (slot == -1) {
                slot = i;
                itemId = id;
                itemCount = stackCount;
                itemDamage = damage;
            }
        }
        return new int[]{windowId, count, slot, itemId, itemCount, itemDamage};
    }

    /** Reads an Entity Metadata packet carrying an item slot, returning {entityId, itemId, count, damage}. */
    int[] readItemMetadata(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_ENTITY_METADATA, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int entityId = ByteBufOps.readVarInt(buffer);
        int itemId = -1, count = -1, damage = -1;
        while (true) {
            int header = buffer.readUnsignedByte();
            if (header == 0x7F) {
                break; // terminator
            }
            int type = header >> 5;
            if (type == Protocol18.METADATA_TYPE_SLOT) {
                int id = buffer.readShort();
                if (id != -1) {
                    count = buffer.readByte();
                    damage = buffer.readShort();
                    SlotNbt.skip(buffer); // structural parse of the optional NBT
                    itemId = id;
                }
            } else {
                throw new IOException("Unexpected metadata type " + type);
            }
        }
        return new int[]{entityId, itemId, count, damage};
    }

    /** Sends Close Window (0x0D) for the player inventory window. */
    void sendCloseWindow() throws IOException {
        sendCloseWindow(Protocol18.INVENTORY_WINDOW_ID);
    }

    /** Sends Close Window (0x0D) for an explicit window id (containers). */
    void sendCloseWindow(int windowId) throws IOException {
        ByteBuf body = Unpooled.buffer(4);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_CLOSE_WINDOW);
        body.writeByte(windowId);
        sendPacket(bodyToBytes(body));
    }

    /**
     * Reads an Open Window packet, returning {windowId, slotCount} and the
     * window type string (title skipped).
     */
    Object[] readOpenWindow(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_OPEN_WINDOW, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int windowId = buffer.readUnsignedByte();
        String type = ByteBufOps.readString(buffer, 64);
        ByteBufOps.readString(buffer, 256); // title
        int slotCount = buffer.readUnsignedByte();
        return new Object[]{windowId, type, slotCount};
    }

    /**
     * Reads a Window Items packet into a full per-wire-slot table for window 0:
     * {@code table[wireSlot] = {itemId, count, damage}}, itemId -1 when empty.
     */
    int[][] readWindowSlotTable(long timeoutMs) throws IOException {
        return readWindowSlotTable(timeoutMs, Protocol18.INVENTORY_WINDOW_ID);
    }

    /** Reads a Window Items packet for an explicit window id into a slot table. */
    int[][] readWindowSlotTable(long timeoutMs, int expectedWindowId) throws IOException {
        return readWindowSlotTable(timeoutMs, expectedWindowId, false);
    }

    /**
     * Reads a Window Items packet for an explicit window id. With
     * {@code drainStale} (a live container closing while its per-tick sync
     * packets are still in flight), Window Items for other window ids are
     * skipped instead of failing the read.
     */
    int[][] readWindowSlotTable(long timeoutMs, int expectedWindowId, boolean drainStale)
            throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        do {
            byte[] payload = readPacketOfType(Protocol18.S2C_WINDOW_ITEMS,
                    Math.max(1, deadline - System.currentTimeMillis()));
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            ByteBufOps.readVarInt(buffer); // packet id
            int windowId = buffer.readByte();
            if (windowId != expectedWindowId) {
                if (drainStale) {
                    readSlotTablePayload(buffer); // skip and keep draining
                    continue;
                }
                throw new IOException("Unexpected window id " + windowId);
            }
            return readSlotTablePayload(buffer);
        } while (System.currentTimeMillis() < deadline);
        throw new IOException("Timed out waiting for window " + expectedWindowId);
    }

    /** Consumes a slot table payload (count + encoded stacks) and returns it. */
    private static int[][] readSlotTablePayload(ByteBuf buffer) {
        int count = buffer.readShort();
        int[][] table = new int[count][];
        for (int i = 0; i < count; i++) {
            int id = buffer.readShort();
            if (id == -1) {
                table[i] = new int[]{-1, 0, 0};
                continue;
            }
            int stackCount = buffer.readUnsignedByte();
            int damage = buffer.readShort();
            SlotNbt.skip(buffer); // structural parse of the optional NBT
            table[i] = new int[]{id, stackCount, damage};
        }
        return table;
    }

    /**
     * Reads a Window Items packet and returns each slot's display name (null
     * when unnamed) — the display.Name assertions of the item-NBT slice.
     * {@code names[wireSlot]} aligns with {@code table[wireSlot]}.
     */
    String[] readWindowSlotNames(long timeoutMs) throws IOException {
        return readWindowSlotNames(timeoutMs, Protocol18.INVENTORY_WINDOW_ID);
    }

    String[] readWindowSlotNames(long timeoutMs, int expectedWindowId) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        do {
            byte[] payload = readPacketOfType(Protocol18.S2C_WINDOW_ITEMS,
                    Math.max(1, deadline - System.currentTimeMillis()));
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            ByteBufOps.readVarInt(buffer); // packet id
            int windowId = buffer.readByte();
            if (windowId != expectedWindowId) {
                readSlotTablePayload(buffer); // skip and keep draining
                continue;
            }
            int count = buffer.readShort();
            String[] names = new String[count];
            for (int i = 0; i < count; i++) {
                int id = buffer.readShort();
                if (id == -1) {
                    continue;
                }
                buffer.readUnsignedByte();  // count
                buffer.readShort();         // damage
                names[i] = SlotNbt.readDisplayName(buffer);
            }
            return names;
        } while (System.currentTimeMillis() < deadline);
        throw new IOException("Timed out waiting for window " + expectedWindowId);
    }

    /**
     * Reads one Window Property packet (0x31), returning
     * {windowId, property, value}.
     */
    int[] readWindowProperty(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_WINDOW_PROPERTY, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int windowId = buffer.readUnsignedByte();
        int property = buffer.readShort();
        int value = buffer.readShort();
        return new int[]{windowId, property, value};
    }

    /**
     * Reads Window Property packets until one matching the expected property
     * arrives. Intermediate properties are discarded (they all arrive on the
     * smelting path).
     */
    int[] readWindowProperty(long timeoutMs, int expectedProperty) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int[] property = readWindowProperty(Math.max(1, deadline - System.currentTimeMillis()));
            if (property[1] == expectedProperty) {
                return property;
            }
        }
        throw new IOException("Timed out waiting for window property " + expectedProperty);
    }

    /** Reads an Update Health packet (0x06), returning {health, food, saturation}. */
    double[] readUpdateHealth(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_UPDATE_HEALTH, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        float health = buffer.readFloat();
        int food = ByteBufOps.readVarInt(buffer);
        float saturation = buffer.readFloat();
        return new double[]{health, food, saturation};
    }

    /** Reads a Combat Event packet (0x42) type 2, returning {eventType, playerId}. */
    int[] readCombatEvent(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_COMBAT_EVENT, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int event = ByteBufOps.readVarInt(buffer);
        if (event != Protocol18.COMBAT_EVENT_ENTITY_DIED) {
            throw new IOException("Unexpected combat event " + event);
        }
        int playerId = ByteBufOps.readVarInt(buffer);
        return new int[]{event, playerId};
    }

    /** Reads a Respawn packet (0x07), returning {dimension, difficulty, gamemode}. */
    int[] readRespawn(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_RESPAWN, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int dimension = buffer.readInt();
        int difficulty = buffer.readUnsignedByte();
        int gamemode = buffer.readUnsignedByte();
        return new int[]{dimension, difficulty, gamemode};
    }

    /**
     * Reads Named Entity Spawn (0x0C): {entityId} — the tracked remote
     * player's observer-local id, the target space of PvP Use Entity.
     */
    int[] readNamedSpawn(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_NAMED_SPAWN, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int entityId = ByteBufOps.readVarInt(buffer);
        ByteBufOps.readString(buffer, 64); // uuid
        buffer.readInt();
        buffer.readInt();
        buffer.readInt();
        buffer.readByte();
        buffer.readByte();
        buffer.readShort();
        buffer.readByte(); // metadata terminator
        return new int[]{entityId};
    }

    /** Reads Set Entity Velocity (0x12): {entityId, vx, vy, vz} in 1/8000 units. */
    int[] readEntityVelocity(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_ENTITY_VELOCITY, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int entityId = ByteBufOps.readVarInt(buffer);
        return new int[]{entityId, buffer.readShort(), buffer.readShort(), buffer.readShort()};
    }

    /** Sends Client Status (0x16) — action 0 performs the respawn. */
    void sendClientStatus(int action) throws IOException {
        ByteBuf body = Unpooled.buffer(4);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_CLIENT_STATUS);
        ByteBufOps.writeVarInt(body, action);
        sendPacket(bodyToBytes(body));
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
