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
    static final int S2C_RESPAWN = 0x07;
    static final int S2C_PLAYER_POSITION_AND_LOOK = 0x08;
    static final int S2C_COLLECT_ITEM = 0x0D;
    static final int S2C_SPAWN_ENTITY = 0x0E;
    static final int S2C_SPAWN_MOB = 0x0F;
    static final int S2C_ANIMATION = 0x0B;
    static final int S2C_ENTITY_VELOCITY = 0x12;
    static final int S2C_DESTROY_ENTITIES = 0x13;
    static final int S2C_REL_ENTITY_MOVE = 0x15;
    static final int S2C_ENTITY_LOOK = 0x16;
    static final int S2C_REL_ENTITY_MOVE_LOOK = 0x17;
    static final int S2C_ENTITY_TELEPORT = 0x18;
    static final int S2C_ENTITY_HEAD_LOOK = 0x19;
    static final int S2C_ENTITY_STATUS = 0x1A;
    static final int S2C_ENTITY_METADATA = 0x1C;
    static final int S2C_NAMED_SOUND_EFFECT = 0x29;
    static final int S2C_WORLD_PARTICLES = 0x2B;
    static final int S2C_BLOCK_CHANGE = 0x23;
    static final int S2C_SET_SLOT = 0x2F;
    static final int S2C_WINDOW_ITEMS = 0x30;
    static final int S2C_WINDOW_PROPERTY = 0x31;
    static final int S2C_CONFIRM_TRANSACTION = 0x32;
    static final int S2C_OPEN_WINDOW = 0x2D;
    static final int S2C_CLOSE_WINDOW = 0x2E;
    static final int S2C_NAMED_SPAWN = 0x0C;
    static final int S2C_CHUNK_DATA = 0x21;
    static final int S2C_UNLOAD_CHUNK = 0x1D;
    static final int S2C_DISCONNECT = 0x40;
    static final int S2C_COMBAT_EVENT = 0x42;
    /** Combat event 2 = entity died (playerId varint, entityId i32, message). */
    static final int COMBAT_EVENT_ENTITY_DIED = 2;

    // Serverbound play packet ids (protocol 47) — cross-checked against the
    // community protocol.json (PrismarineJS/minecraft-data) mapping. The
    // "Player" ground packet lives at 0x03 ("flying"); 0x0F is the client's
    // Confirm Transaction response. Every id the vanilla 1.8.8 client can
    // emit is declared here so play-state traffic is never misrouted.
    static final int C2S_KEEP_ALIVE = 0x00;
    static final int C2S_CHAT_MESSAGE = 0x01;
    static final int C2S_USE_ENTITY = 0x02;
    static final int C2S_PLAYER = 0x03;
    static final int C2S_PLAYER_POSITION = 0x04;
    static final int C2S_PLAYER_LOOK = 0x05;
    static final int C2S_PLAYER_POSITION_AND_LOOK = 0x06;
    static final int C2S_PLAYER_DIGGING = 0x07;
    static final int C2S_PLAYER_BLOCK_PLACEMENT = 0x08;
    static final int C2S_HELD_ITEM_CHANGE = 0x09;
    static final int C2S_ARM_ANIMATION = 0x0A;
    static final int C2S_ENTITY_ACTION = 0x0B;
    static final int C2S_STEER_VEHICLE = 0x0C;
    static final int C2S_CLOSE_WINDOW = 0x0D;
    static final int C2S_WINDOW_CLICK = 0x0E;
    static final int C2S_CONFIRM_TRANSACTION = 0x0F;
    static final int C2S_SET_CREATIVE_SLOT = 0x10;
    static final int C2S_ENCHANT_ITEM = 0x11;
    static final int C2S_UPDATE_SIGN = 0x12;
    static final int C2S_PLAYER_ABILITIES = 0x13;
    static final int C2S_TAB_COMPLETE = 0x14;
    static final int C2S_CLIENT_SETTINGS = 0x15;
    static final int C2S_CLIENT_STATUS = 0x16;
    static final int C2S_PLUGIN_MESSAGE = 0x17;
    static final int C2S_SPECTATE = 0x18;
    static final int C2S_RESOURCE_PACK_STATUS = 0x19;

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
    /** Easy difficulty: hunger behaves, starvation cannot kill (floor 10). */
    static final int DIFFICULTY_EASY = 1;

    // Object types of the Spawn Entity packet (protocol 47, community
    // entities.json): 1 = Boat, 2 = Item ("Dropped item") — the item drop's
    // objectData = legacy item id | (damage << 16), announced like every
    // non-zero objectData with a velocity triple.
    static final int OBJECT_ITEM = 2;
    /** Falling block (community entities.json FallingSand): objectData = legacy id | (metadata << 12). */
    static final int OBJECT_FALLING_BLOCK = 70;
    // Ranged combat objects (community entities.json): the projectile's
    // objectData carries the thrower's entity id, announced with a velocity
    // triple like every non-zero objectData.
    static final int OBJECT_ARROW = 60;
    static final int OBJECT_SNOWBALL = 61;
    static final int OBJECT_EGG = 62;

    // Entity metadata (protocol 47, community-verified type map): value types
    // 0=byte 1=short 2=int 3=float 4=string 5=slot 6=position 7=rotation.
    // Living entities carry their flags byte at index 0 and health float at
    // index 6; item entities carry their stack slot at index 10.
    //
    // ⚠ Health at index 7 crashed real vanilla clients: 1.8's EntityLiving
    // DataWatcher registers health (float) at 6 and the potion color (int) at
    // 7, and the client's updateWatchedObjects throws on an index/type
    // mismatch — a float at 7 killed the game the moment the first mob
    // spawned near a player (~8s after join, the population maintainer's
    // first roll). The community metadata parser never cross-validates
    // index against type, which is why mineflayer validated this for weeks.
    static final int METADATA_TYPE_BYTE = 0;
    static final int METADATA_TYPE_FLOAT = 3;
    static final int METADATA_TYPE_SLOT = 5;
    static final int LIVING_FLAGS_METADATA_INDEX = 0;
    static final int LIVING_HEALTH_METADATA_INDEX = 6;
    static final int ITEM_STACK_METADATA_INDEX = 10;
    static final int METADATA_TERMINATOR = 0x7F;

    // The living-flags bit map (1.8's Entity flags byte, index 0):
    // 0x01 burning, 0x02 crouched, 0x08 riding, 0x10 sprinting, 0x20 eating.
    static final int LIVING_FLAG_SNEAKING = 0x02;
    static final int LIVING_FLAG_SPRINTING = 0x10;

    // World Particles (0x2B) numeric ids (community particles.json, 1.8).
    static final int PARTICLE_SNOWBALL_POOF = 31;
    static final int PARTICLE_ICON_CRACK = 36;   // data: [itemId, itemMeta]
    static final int PARTICLE_BLOCK_CRACK = 37;  // data: [blockStateId]

    // Entity Status (0x1A) codes the client animates from.
    static final int ENTITY_STATUS_HURT = 2;
    static final int ENTITY_STATUS_DEAD = 3;

    // Animation (0x0B) code 0 = arm swing.
    static final int ANIMATION_ARM_SWING = 0;

    // Use Entity (0x02) mouse actions.
    static final int USE_ENTITY_INTERACT = 0;
    static final int USE_ENTITY_ATTACK = 1;
    static final int USE_ENTITY_INTERACT_AT = 2;

    // Player inventory window (id 0) layout, historical order:
    // 0 craft result, 1-4 craft grid, 5-8 armor, 9-35 main, 36-44 hotbar.
    static final int INVENTORY_WINDOW_ID = 0;
    static final int INVENTORY_WINDOW_SLOTS = 45;
    static final int WIRE_SLOT_RESULT = 0;
    static final int WIRE_SLOT_CRAFT_FIRST = 1;
    static final int WIRE_SLOT_CRAFT_LAST = 4;
    static final int WIRE_SLOT_HOTBAR_BASE = 36;

    // Crafting-table container window layout (protocol 47, "minecraft:crafting_table"):
    // 0 craft result, 1-9 craft grid, 10-36 main, 37-45 hotbar (46 slots).
    static final String TABLE_WINDOW_TYPE = "minecraft:crafting_table";
    static final String TABLE_WINDOW_TITLE = "{\"text\":\"Crafting\"}";
    static final int TABLE_WINDOW_SLOTS = 46;
    static final int TABLE_WIRE_SLOT_GRID_FIRST = 1;
    static final int TABLE_WIRE_SLOT_GRID_LAST = 9;
    static final int TABLE_WIRE_SLOT_MAIN_FIRST = 10;
    static final int TABLE_WIRE_SLOT_MAIN_LAST = 36;
    static final int TABLE_WIRE_SLOT_HOTBAR_FIRST = 37;
    static final int TABLE_WIRE_SLOT_HOTBAR_LAST = 45;
    static final int TABLE_WIRE_SLOT_HOTBAR_BASE = 37;

    // Furnace container window layout (protocol 47, community-verified via
    // windows.json + mineflayer: 0 input ("smelted"), 1 fuel, 2 result,
    // 3-29 main, 30-38 hotbar — 39 slots).
    static final String FURNACE_WINDOW_TYPE = "minecraft:furnace";
    static final String FURNACE_WINDOW_TITLE = "{\"text\":\"Furnace\"}";
    static final int FURNACE_WINDOW_SLOTS = 39;
    static final int FURNACE_WIRE_SLOT_MAIN_FIRST = 3;
    static final int FURNACE_WIRE_SLOT_MAIN_LAST = 29;
    static final int FURNACE_WIRE_SLOT_HOTBAR_FIRST = 30;
    static final int FURNACE_WIRE_SLOT_HOTBAR_LAST = 38;
    static final int FURNACE_WIRE_SLOT_HOTBAR_BASE = 30;

    // Furnace window properties (community-verified order: fuel left,
    // fuel max, progress, progress max; values in ticks).
    static final int FURNACE_PROP_BURN_REMAINING = 0;
    static final int FURNACE_PROP_BURN_TOTAL = 1;
    static final int FURNACE_PROP_COOK = 2;
    static final int FURNACE_PROP_COOK_TOTAL = 3;
    static final int FURNACE_PROP_COUNT = 4;

    // Chest container window (protocol 47; community-verified via
    // prismarine-windows: container slots 0-26, player inventory range start 27
    // end 62 — main 27-53, hotbar 54-62 — 63 slots total).
    static final String CHEST_WINDOW_TYPE = "minecraft:chest";
    static final String CHEST_WINDOW_TITLE = "{\"text\":\"Chest\"}";
    static final int CHEST_WINDOW_SLOTS = 63;
    static final int CHEST_WIRE_SLOT_LAST = 26;
    static final int CHEST_WIRE_SLOT_MAIN_FIRST = 27;
    static final int CHEST_WIRE_SLOT_MAIN_LAST = 53;
    static final int CHEST_WIRE_SLOT_HOTBAR_FIRST = 54;
    static final int CHEST_WIRE_SLOT_HOTBAR_LAST = 62;
    static final int CHEST_WIRE_SLOT_HOTBAR_BASE = 54;
}
