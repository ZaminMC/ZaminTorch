package net.zamin.protocol.v1_8;

/**
 * Wire constants of Minecraft protocol 47 (client 1.8.x). These IDs are
 * translation data of the 1.8.8 adapter only; the engine never references them.
 */
final class Protocol18 {

    private Protocol18() {
    }

    static final int PROTOCOL_VERSION = 47;
    static final String VERSION_NAME = "1.8.8";

    // Clientbound play packet ids (protocol 47)
    static final int S2C_KEEP_ALIVE = 0x00;
    static final int S2C_JOIN_GAME = 0x01;
    static final int S2C_CHAT = 0x02;
    static final int S2C_TIME_UPDATE = 0x03;
    static final int S2C_SPAWN_POSITION = 0x05;
    static final int S2C_UPDATE_HEALTH = 0x06;
    static final int S2C_PLAYER_POSITION_AND_LOOK = 0x08;
    static final int S2C_COLLECT_ITEM = 0x0D;
    static final int S2C_SPAWN_ENTITY = 0x0E;
    static final int S2C_ENTITY_VELOCITY = 0x12;
    static final int S2C_DESTROY_ENTITIES = 0x13;
    static final int S2C_ENTITY_TELEPORT = 0x18;
    static final int S2C_ENTITY_METADATA = 0x1C;
    static final int S2C_BLOCK_CHANGE = 0x23;
    static final int S2C_SET_SLOT = 0x2F;
    static final int S2C_WINDOW_ITEMS = 0x30;
    static final int S2C_NAMED_SPAWN = 0x0C;
    static final int S2C_CHUNK_DATA = 0x21;
    static final int S2C_UNLOAD_CHUNK = 0x1D;
    static final int S2C_DISCONNECT = 0x40;

    // Serverbound play packet ids (protocol 47)
    static final int C2S_KEEP_ALIVE = 0x00;
    static final int C2S_CHAT_MESSAGE = 0x01;
    static final int C2S_PLAYER_POSITION = 0x04;
    static final int C2S_PLAYER_LOOK = 0x05;
    static final int C2S_PLAYER_POSITION_AND_LOOK = 0x06;
    static final int C2S_PLAYER_DIGGING = 0x07;
    static final int C2S_PLAYER_BLOCK_PLACEMENT = 0x08;
    static final int C2S_HELD_ITEM_CHANGE = 0x09;
    static final int C2S_CLIENT_SETTINGS = 0x15;
    static final int C2S_CLIENT_STATUS = 0x16;
    static final int C2S_PLAYER = 0x0F;

    // State-transition ids
    static final int C2S_HANDSHAKE = 0x00;
    static final int C2S_LOGIN_START = 0x00;
    static final int S2C_LOGIN_DISCONNECT = 0x00;
    static final int S2C_LOGIN_SUCCESS = 0x02;
    static final int S2C_STATUS_RESPONSE = 0x00;
    static final int C2S_STATUS_REQUEST = 0x00;
    static final int C2S_STATUS_PING = 0x01;
    static final int S2C_STATUS_PONG = 0x01;

    static final int NEXT_STATE_STATUS = 1;
    static final int NEXT_STATE_LOGIN = 2;

    static final int GAMEMODE_CREATIVE = 1;
    static final String LEVEL_TYPE_FLAT = "flat";
    static final int BIOME_PLAINS = 1;

    // Object types of the Spawn Entity packet (protocol 47)
    static final int OBJECT_ITEM = 1;

    // Entity metadata (protocol 47): type 5 = item slot, index 10 = item entity stack
    static final int METADATA_TYPE_SLOT = 5;
    static final int ITEM_STACK_METADATA_INDEX = 10;
    static final int METADATA_TERMINATOR = 0x7F;

    // Player inventory window (id 0) layout, historical order:
    // 0 craft result, 1-4 craft grid, 5-8 armor, 9-35 main, 36-44 hotbar.
    static final int INVENTORY_WINDOW_ID = 0;
    static final int INVENTORY_WINDOW_SLOTS = 45;
    static final int WIRE_SLOT_HOTBAR_BASE = 36;
}
