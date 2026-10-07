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
        return payload;
    }

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
        body.writeByte(1);            // count
        body.writeShort(0);           // metadata
        body.writeShort(0);           // nbt: none
        sendPacket(bodyToBytes(body));
    }

    void sendChat(String text) throws IOException {
        ByteBuf body = Unpooled.buffer(16 + text.length());
        ByteBufOps.writeVarInt(body, Protocol18.C2S_CHAT_MESSAGE);
        ByteBufOps.writeString(body, text);
        sendPacket(bodyToBytes(body));
    }

    /** Reads a Chat packet, returning the raw JSON text. */
    String readChatLine(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_CHAT, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        return ByteBufOps.readString(buffer, 4096);
    }

    /** Reads a Block Change packet, returning {x, y, z, legacyId}. */
    int[] readBlockChange(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_BLOCK_CHANGE, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int[] pos = ByteBufOps.readPackedBlockPosition(buffer);
        int legacy = ByteBufOps.readVarInt(buffer);
        return new int[]{pos[0], pos[1], pos[2], legacy};
    }

    // ---- survival slice helpers -------------------------------------------------

    void sendHeldItemChange(int slot) throws IOException {
        ByteBuf body = Unpooled.buffer(6);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_HELD_ITEM_CHANGE);
        body.writeShort(slot);
        sendPacket(bodyToBytes(body));
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
            int nbt = buffer.readShort();
            if (nbt > 0) {
                buffer.readBytes(new byte[nbt]); // skip NBT payload (tests never send any)
            }
            if (slot == -1) {
                slot = i;
                itemId = id;
                itemCount = stackCount;
                itemDamage = damage;
            }
        }
        return new int[]{windowId, count, slot, itemId, itemCount, itemDamage};
    }

    /** Reads an Entity Metadata packet carrying an item slot, returning {entityId, itemId, count}. */
    int[] readItemMetadata(long timeoutMs) throws IOException {
        byte[] payload = readPacketOfType(Protocol18.S2C_ENTITY_METADATA, timeoutMs);
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        int entityId = ByteBufOps.readVarInt(buffer);
        int itemId = -1, count = -1;
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
                    buffer.readShort(); // damage
                    int nbt = buffer.readShort();
                    if (nbt > 0) {
                        buffer.readBytes(new byte[nbt]);
                    }
                    itemId = id;
                }
            } else {
                throw new IOException("Unexpected metadata type " + type);
            }
        }
        return new int[]{entityId, itemId, count};
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
