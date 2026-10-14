package net.zaminmc.torch.protocol.v1_8;

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
    static final int S2C_ENTITY_EQUIPMENT = 0x04;
    static final int S2C_ENTITY_METADATA = 0x1C;
    /** Entity Effect (0x1D): varint entity, byte effect, byte amplifier,
     * varint duration (capped 32767), byte flags (1 = show particles —
     * the reference's own EntityStatusEffectS2CPacket ctor). */
    static final int S2C_ENTITY_EFFECT = 0x1D;
    /** Remove Entity Effect (0x1E): varint entity, byte effect. */
    static final int S2C_REMOVE_ENTITY_EFFECT = 0x1E;
    static final int S2C_NAMED_SOUND_EFFECT = 0x29;
    static final int S2C_EXPLOSION = 0x27;
    /** World Particles is 0x2A — 0x2B is Game State Change (an off-by-one
     * the mineflayer parser tolerated; the real 1.8.8 client would not). */
    static final int S2C_WORLD_PARTICLES = 0x2A;
    static final int S2C_BLOCK_CHANGE = 0x23;
    static final int S2C_CHANGE_GAME_STATE = 0x2B;
    /** Change Game State reasons: 1 end rain, 2 begin rain, 3 = change gamemode. */
    static final int GAME_STATE_CHANGE_END_RAINING = 1;
    static final int GAME_STATE_CHANGE_BEGIN_RAINING = 2;
    static final int GAME_STATE_CHANGE_GAMEMODE = 3;
    /** Spawn Global Entity: #42 in the registration order (right between
     * GameEvent 0x2B and OpenInventoryMenu 0x2D) — the lightning bolt's
     * own spawn. The type byte's single member: the thunderbolt. */
    static final int S2C_SPAWN_GLOBAL_ENTITY = 0x2C;
    static final int GLOBAL_ENTITY_THUNDERBOLT = 1;
    static final int S2C_PLAYER_ABILITIES = 0x39;
    static final int S2C_SET_SLOT = 0x2F;
    static final int S2C_WINDOW_ITEMS = 0x30;
    static final int S2C_WINDOW_PROPERTY = 0x31;
    static final int S2C_CONFIRM_TRANSACTION = 0x32;
    static final int S2C_OPEN_WINDOW = 0x2D;
    /** Plugin Message (0x3F): the MC|TrList trade offers ride this (no dedicated Trade List packet on 47). */
    static final int S2C_PLUGIN_MESSAGE = 0x3F;
    static final int S2C_CLOSE_WINDOW = 0x2E;
    /** Tab-Complete reply: VarInt count, then that many strings (protocol 47). */
    static final int S2C_TAB_COMPLETE = 0x3A;
    /** Update Sign: packed position long, then the four UTF lines (protocol 47). */
    static final int S2C_UPDATE_SIGN = 0x33;
    static final int S2C_NAMED_SPAWN = 0x0C;
    /** Attach Entity (0x1B, protocol 47): i32 rider, i32 vehicle, u8 leash/riding. */
    static final int S2C_ATTACH_ENTITY = 0x1B;
    static final int S2C_CHUNK_DATA = 0x21;
    /** Spawn Experience Orb (0x11, protocol 47): varint id, i32 xyz (1/32), i16 count. */
    static final int S2C_SPAWN_XP_ORB = 0x11;
    /** Set Experience (0x1F, protocol 47): f32 progress, short level, short total. */
    static final int S2C_SET_EXPERIENCE = 0x1F;
    /** Held Item Change (0x09, protocol 47): the i8 slot the client now holds. */
    static final int S2C_HELD_ITEM_CHANGE = 0x09;
    /** Block Action (0x24, protocol 47): i64 pos, u8 action, u8 param, u32 block. */
    static final int S2C_BLOCK_ACTION = 0x24;
    /**
     * Update Block Entity (0x35 — the PLAY registration order's own slot,
     * the reference NetworkProtocol lines 122-195: the fifty-fourth
     * clientbound play packet): position + the tile type byte + the NBT
     * compound. The spawner's spinning body rides it (Slice 18e).
     */
    static final int S2C_UPDATE_BLOCK_ENTITY = 0x35;
    /** The tile type byte: 1 = the mob spawner (BlockEntity ids, 1.8.8). */
    static final byte BLOCK_ENTITY_TYPE_MOB_SPAWNER = 1;
    /** Update Block Entity's beacon action (createUpdatePacket: 3). */
    static final byte BLOCK_ENTITY_TYPE_BEACON = 3;
    /** The spawner's legacy block id (the block event's type word). */
    static final int SPAWNER_LEGACY_BLOCK_ID = 52;
    /** The fired cycle's flame burst (MobSpawner.tick line 108: doEvent 2004). */
    static final int WORLD_EVENT_SPAWNER_FLAMES = 2004;
    /**
     * Block Break Animation (0x25, protocol 47 — the NetworkProtocol's own
     * registration order: #37 in the clientbound play list): varint id,
     * i64 pos, u8 stage. (0x28 was never the break animation — that slot
     * is the world Effect packet.)
     */
    static final int S2C_BLOCK_MINING_PROGRESS = 0x25;
    /**
     * Effect / world event (0x28, protocol 47 — #40 in the clientbound
     * play list): i32 effect id, i64 pos, i32 data, bool global. The 2002
     * potion splash rides it with the potion's damage as the data.
     */
    static final int S2C_WORLD_EVENT = 0x28;
    /** The world event ids (ServerWorld.doEvent's own): the potion burst. */
    static final int WORLD_EVENT_POTION_SPLASH = 2002;
    /** The witch's idle spell swirl (Entity Event byte 15 — the client's own doEvent arm). */
    static final int ENTITY_EVENT_WITCH_AURA = 15;
    /** The chest lid Block Action: action 1, param 1 = open / 0 = close. */
    static final int BLOCK_ACTION_CHEST_LID = 1;
    static final int CHEST_LEGACY_BLOCK_ID = 54;
    /** The note block's legacy wire id (the reference's register(25, "noteblock")). */
    static final int NOTE_BLOCK_LEGACY_BLOCK_ID = 25;
    // Protocol 47 has NO unload-chunk packet: the 1.8 client prunes
    // out-of-range chunks itself (the vanilla server never said goodbye).
    // 0x1D is Entity Effect — writing a 1.9-style unload there killed real
    // clients with "Packet 0/29 (ib), 3 bytes extra" (29 decimal = 0x1D).
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
    // Biome ids (the chunk packet's biome array values, community blocks.json).
    static final int BIOME_PLAINS = 1;
    static final int BIOME_DESERT = 2;
    static final int BIOME_FOREST = 4;
    /** Easy difficulty: hunger behaves, starvation cannot kill (floor 10). */
    static final int DIFFICULTY_EASY = 1;

    // Object types of the Spawn Entity packet (protocol 47, community
    // entities.json): 1 = Boat, 2 = Item ("Dropped item") — the item drop's
    // objectData = legacy item id | (damage << 16), announced like every
    // non-zero objectData with a velocity triple.
    static final int OBJECT_ITEM = 2;
    /** Falling block (community entities.json FallingSand): objectData = legacy id | (metadata << 12). */
    static final int OBJECT_FALLING_BLOCK = 70;
    /** The primed TNT's object type (1.8.8 EntityList: EntityTNTPrimed = 20, object id 50). */
    static final int OBJECT_PRIMED_TNT = 50;
    // Ranged combat objects (TrackedEntity.createAddEntityPacket's own
    // wire map, 1.8.8): the objectData field is the SHOOTER's id for the
    // arrow family (announced with the velocity triple — the client reads
    // the shorts only when objectData > 0) and the carried payload for the
    // potion (the damage value) and the falling block (the serialized id).
    // The snowball and the egg carry no data: vanilla sends them bare
    // (no velocity shorts on the wire at all).
    static final int OBJECT_ARROW = 60;
    static final int OBJECT_SNOWBALL = 61;
    static final int OBJECT_EGG = 62;
    /** The dispensed fire charge (Slice 11a — community entities.json). */
    static final int OBJECT_SMALL_FIREBALL = 63;
    static final int OBJECT_LARGE_FIREBALL = 64;
    /**
     * Thrown potion (TrackedEntity's own type 73 — the client builds it
     * with the objectData as the potion's damage value, so the bottle
     * renders with the right color; 68 was the guardian's MOB id, never a
     * spawn-object type).
     */
    static final int OBJECT_POTION = 73;
    /** Thrown ender pearl (Spawn Object type 65 — the client's own). */
    static final int OBJECT_ENDER_PEARL = 65;
    /** Leash knot (Spawn Object type 77 — TrackedEntity's own). */
    static final int OBJECT_LEASH_KNOT = 77;
    /** Item frame (Spawn Object type 71 — TrackedEntity lines 493-502). */
    static final int OBJECT_ITEM_FRAME = 71;
    /** Armor stand (Spawn Object type 78 — TrackedEntity line 491). */
    static final int OBJECT_ARMOR_STAND = 78;
    /** Spawn Painting (0x10 — AddPaintingS2CPacket's own packet). */
    static final int S2C_SPAWN_PAINTING = 0x10;
    /** The vehicles (community entities.json object types). */
    static final int OBJECT_BOAT = 1;
    static final int OBJECT_MINECART = 10;

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
    /** The int metadata type (the 1.8 DataWatcher order: 0 byte, 2 int, 3 float). */
    static final int METADATA_TYPE_INT = 2;
    static final int METADATA_TYPE_FLOAT = 3;
    static final int METADATA_TYPE_SLOT = 5;
    /** The string metadata type (the horse's owner name — the 1.8 quirk). */
    static final int METADATA_TYPE_STRING = 4;
    /** The position metadata type (the 1.8 DataWatcher order: 6 position). */
    static final int METADATA_TYPE_POSITION = 6;
    /** The rotation metadata type (three floats — the armor stand's pose). */
    static final int METADATA_TYPE_ROTATION = 7;
    static final int LIVING_FLAGS_METADATA_INDEX = 0;
    static final int LIVING_HEALTH_METADATA_INDEX = 6;
    /** The kind-status byte index (1.8 DataWatcher 16): creeper swell, sheep coat. */
    static final int KIND_STATUS_METADATA_INDEX = 16;
    /** The sheep coat bit inside the kind-status byte (0x10 = sheared). */
    static final byte SHEEP_STATUS_SHEARED = (byte) 0x10;
    /** The creeper swell state values (the historical EntityCreeper field). */
    static final byte CREEPER_FUSE_IDLE = (byte) -1;
    static final byte CREEPER_FUSE_SWELLING = (byte) 1;
    static final int ITEM_STACK_METADATA_INDEX = 10;
    static final int METADATA_TERMINATOR = 0x7F;

    // The horse metadata block (protocol 47; ProtocolSupport/Glowstone
    // cross-verified): index 16 Int flags (0x02 tamed, 0x04 saddled,
    // 0x20 eating, 0x40 rearing, 0x80 mouth), 19 Byte subtype (0 horse,
    // 1 donkey...), 20 Int variant (color | marking << 8), 21 String owner,
    // 22 Int armor (0 none .. 3 diamond). The pig's saddle is a Byte at 16.
    static final int HORSE_FLAGS_METADATA_INDEX = 16;
    static final int HORSE_SUBTYPE_METADATA_INDEX = 19;
    static final int HORSE_VARIANT_METADATA_INDEX = 20;
    static final int HORSE_OWNER_METADATA_INDEX = 21;
    static final int HORSE_ARMOR_METADATA_INDEX = 22;
    static final int PIG_SADDLE_METADATA_INDEX = 16;
    /** The villager profession (the index-16 Int: 0 farmer .. 4 butcher). */
    static final int VILLAGER_PROFESSION_METADATA_INDEX = 16;
    /** The witch's drinking flag (WitchEntity's own synced-data slot 21). */
    static final int WITCH_DRINKING_METADATA_INDEX = 21;
    /**
     * The skeleton's type byte (SkeletonEntity.registerSyncedData line 72:
     * the synced slot 13 — 0 the archer, 1 the wither variant).
     */
    static final int SKELETON_TYPE_METADATA_INDEX = 13;
    /**
     * The short metadata type (the 1.8 DataWatcher order: 1 short — the
     * enderman's carried block rides it).
     */
    static final int METADATA_TYPE_SHORT = 1;
    /**
     * The enderman's screaming byte (EndermanEntity.registerSyncedData line
     * 82: the synced slot 18) — the client swaps the jaw texture from it.
     */
    static final int ENDERMAN_SCREAMING_METADATA_INDEX = 18;
    /**
     * The rabbit's coat byte (RabbitEntity.registerSyncedData line 116:
     * the synced slot 18) — 0-5 the natural skins, 99 the killer bunny
     * (the client swaps the body texture and the red eyes from it).
     */
    static final int RABBIT_TYPE_METADATA_INDEX = 18;
    // The wolf's block (WolfEntity.registerSyncedData): the index-16 state
    // byte (TameableEntity's sitting 0x1 | tamed 0x4 bits + the wolf's own
    // angry 0x2), the 17 owner string, the 18 health float (the client's
    // tail-wag read), the 19 begging byte, the 20 collar dye byte.
    static final int WOLF_STATE_METADATA_INDEX = 16;
    static final int WOLF_OWNER_METADATA_INDEX = 17;
    static final int WOLF_HEALTH_METADATA_INDEX = 18;
    static final int WOLF_BEGGING_METADATA_INDEX = 19;
    static final int WOLF_COLLAR_METADATA_INDEX = 20;
    // The guardian's block (GuardianEntity.registerSyncedData): the
    // index-16 flag Int (bit 0x02 the swim's moving, bit 0x04 the elder)
    // and the 17 beam-target Int (the laser's live victim id — 0 = none;
    // the client draws the beam from it).
    static final int GUARDIAN_BEAM_METADATA_INDEX = 17;
    /** The swim steering bit (GuardianEntity's setDataFlagValue(2)). */
    static final int GUARDIAN_FLAG_MOVING = 0x02;
    /** The elder bit (GuardianEntity's setDataFlagValue(4)). */
    static final int GUARDIAN_FLAG_ELDER = 0x04;

    // The living-flags bit map (1.8's Entity flags byte, index 0):
    // 0x01 burning, 0x02 crouched, 0x08 riding, 0x10 sprinting, 0x20 eating.
    static final int LIVING_FLAG_BURNING = 0x01;
    static final int LIVING_FLAG_SNEAKING = 0x02;
    static final int LIVING_FLAG_RIDING = 0x08;
    static final int LIVING_FLAG_SPRINTING = 0x10;

    // World Particles (0x2B) numeric ids (community particles.json, 1.8).
    static final int PARTICLE_SNOWBALL_POOF = 31;
    static final int PARTICLE_ICON_CRACK = 36;   // data: [itemId, itemMeta]
    static final int PARTICLE_BLOCK_CRACK = 37;  // data: [blockStateId]
    // The display family (EnumParticleTypes' own ids — ParticleType.java):
    // the server-side addParticle consults. count 0 = one particle at the
    // exact position with the offsets as its velocity, the shape
    // ServerWorld.addParticle sends.
    static final int PARTICLE_WATER_SPLASH = 5;
    static final int PARTICLE_SMOKE_NORMAL = 11;
    static final int PARTICLE_EXPLOSION_LARGE = 1;
    static final int PARTICLE_VILLAGER_ANGRY = 20;
    static final int PARTICLE_VILLAGER_HAPPY = 21;
    static final int PARTICLE_PORTAL = 24;
    static final int PARTICLE_HEART = 34;

    // Entity Status (0x1A) codes the client animates from: the hurt flash
    // (2), the death fall (3), and 18 the love heart burst
    // (AnimalEntity.lovePlayer's doEntityEvent(18); the client renders the
    // seven hearts around the body).
    static final int ENTITY_STATUS_HURT = 2;
    static final int ENTITY_STATUS_DEAD = 3;
    static final int ENTITY_STATUS_LOVE = 18;
    /** TameableEntity.addTamingParticles' verdict pair: 7 hearts, 6 smoke. */
    static final int ENTITY_STATUS_TAME_HEARTS = 7;
    static final int ENTITY_STATUS_TAME_SMOKE = 6;
    /** The passive age byte (index 12: -1 baby, 0 adult — PassiveEntity). */
    static final int PASSIVE_AGE_METADATA_INDEX = 12;
    // The zombie's own block (ZombieEntity.registerSyncedData lines
    // 91-96): the index-12 baby byte (1 baby — the zombie's own flag, not
    // the passive family's -1), the 13 villager-skin byte, the 14
    // converting byte (the cure's countdown).
    static final int ZOMBIE_BABY_METADATA_INDEX = 12;
    static final int ZOMBIE_VILLAGER_METADATA_INDEX = 13;
    static final int ZOMBIE_CONVERTING_METADATA_INDEX = 14;
    /**
     * Entity status 16 on a zombie (ZombieEntity.doEvent line 492): the
     * client plays mob.zombie.remedy at the body — the cure's own sound.
     */
    static final int ENTITY_STATUS_ZOMBIE_REMEDY = 16;
    /**
     * World events 1016/1017 (ZombieEntity.onKill line 376 +
     * convertToVillager line 537): the client plays mob.zombie.infect /
     * mob.zombie.unfect at volume 2.0 from the Effect packet itself
     * (WorldRenderer lines 1957-1961).
     */
    static final int WORLD_EVENT_ZOMBIE_INFECT = 1016;
    static final int WORLD_EVENT_ZOMBIE_UNFECT = 1017;

    /**
     * World event 1005 (MusicDiscItem.useOn line 39 + JukeboxBlock
     * line 58): the Effect packet's record arm — the data carries the
     * disc's item id to play (the client's own "records." walk,
     * WorldRenderer lines 1923-1929) or 0 to stop the record at the
     * position.
     */
    static final int WORLD_EVENT_PLAY_RECORD = 1005;

    // Animation (0x0B) code 0 = arm swing.
    static final int ANIMATION_ARM_SWING = 0;
    /**
     * Animation code 4: the critical-hit particle burst — the reference's
     * ServerPlayerEntity.addCritParticles broadcasts EntityAnimationS2CPacket
     * (target, 4) for a landed falling crit (PlayerEntity.attack lines
     * 1002-1004).
     */
    static final int ANIMATION_CRIT = 4;
    /**
     * Animation code 5: the magic-crit burst on an enchanted hit — the
     * reference's addEnchantedCritParticles (lines 1006-1008, fired whenever
     * the damage-family enchantment added any damage, falling or not).
     */
    static final int ANIMATION_MAGIC_CRIT = 5;

    // Use Entity (0x02) mouse actions.
    static final int USE_ENTITY_INTERACT = 0;
    static final int USE_ENTITY_ATTACK = 1;
    static final int USE_ENTITY_INTERACT_AT = 2;

    // Entity Equipment (0x04) slot order, the 1.8 wire order: held, feet, legs, chest, head.
    static final int EQUIPMENT_SLOT_HELD = 0;
    static final int EQUIPMENT_SLOT_FEET = 1;
    static final int EQUIPMENT_SLOT_LEGS = 2;
    static final int EQUIPMENT_SLOT_CHEST = 3;
    static final int EQUIPMENT_SLOT_HEAD = 4;

    // Player inventory window (id 0) layout, historical order:
    // 0 craft result, 1-4 craft grid, 5-8 armor, 9-35 main, 36-44 hotbar.
    static final int INVENTORY_WINDOW_ID = 0;
    static final int INVENTORY_WINDOW_SLOTS = 45;
    static final int WIRE_SLOT_RESULT = 0;
    static final int WIRE_SLOT_CRAFT_FIRST = 1;
    static final int WIRE_SLOT_CRAFT_LAST = 4;
    /** The armor row (head, chest, legs, feet) in the player window's layout. */
    static final int WIRE_SLOT_ARMOR_FIRST = 5;
    static final int WIRE_SLOT_ARMOR_LAST = 8;
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
    /** The chest minecart's own title (the reference's "container.minecart"). */
    static final String MINECART_CHEST_WINDOW_TITLE = "{\"text\":\"Minecart\"}";
    /** The ender chest's own title (the reference's "container.enderchest"). */
    static final String ENDER_CHEST_WINDOW_TITLE = "{\"text\":\"Ender Chest\"}";
    /** The ender chest's legacy block id (the lid's Block Action carrier). */
    static final int ENDER_CHEST_LEGACY_BLOCK_ID = 130;
    /** The beacon's own window (the reference's getMenuType "minecraft:beacon"). */
    static final String BEACON_WINDOW_TYPE = "minecraft:beacon";
    static final String BEACON_WINDOW_TITLE = "{\"text\":\"Beacon\"}";
    /** The beacon window's layout: 1 payment slot + 27 main + 9 hotbar. */
    static final int BEACON_WINDOW_SLOTS = 37;
    static final String PLUGIN_CHANNEL_BEACON = "MC|Beacon";
    static final int CHEST_WINDOW_SLOTS = 63;
    static final int CHEST_WIRE_SLOT_LAST = 26;
    static final int CHEST_WIRE_SLOT_MAIN_FIRST = 27;
    static final int CHEST_WIRE_SLOT_MAIN_LAST = 53;
    static final int CHEST_WIRE_SLOT_HOTBAR_FIRST = 54;
    static final int CHEST_WIRE_SLOT_HOTBAR_LAST = 62;
    static final int CHEST_WIRE_SLOT_HOTBAR_BASE = 54;
    // The dispenser/dropper window (Slice 11a — DispenserMenu lines 15-34:
    // the same 3x9 layout the chest uses, nine container slots instead of
    // twenty-seven).
    static final String DISPENSER_WINDOW_TYPE = "minecraft:dispenser";
    static final String DROPPER_WINDOW_TYPE = "minecraft:dropper";
    static final String DISPENSER_WINDOW_TITLE = "{\"text\":\"Dispenser\"}";
    static final String DROPPER_WINDOW_TITLE = "{\"text\":\"Dropper\"}";

    /** The hopper window (the reference's HopperMenu): 5 + 27 + 9 = 41 slots. */
    static final String HOPPER_WINDOW_TYPE = "minecraft:hopper";
    static final String HOPPER_WINDOW_TITLE = "{\"text\":\"Hopper\"}";
    static final int HOPPER_WINDOW_SLOTS = 41;
    static final int HOPPER_WIRE_SLOT_LAST = 4;
    static final int HOPPER_WIRE_SLOT_MAIN_FIRST = 5;
    static final int HOPPER_WIRE_SLOT_MAIN_LAST = 31;
    static final int HOPPER_WIRE_SLOT_HOTBAR_FIRST = 32;
    static final int HOPPER_WIRE_SLOT_HOTBAR_LAST = 40;
    static final int DISPENSER_WINDOW_SLOTS = 45;
    static final int DISPENSER_WIRE_SLOT_LAST = 8;
    static final int DISPENSER_WIRE_SLOT_MAIN_FIRST = 9;
    static final int DISPENSER_WIRE_SLOT_MAIN_LAST = 35;
    static final int DISPENSER_WIRE_SLOT_HOTBAR_FIRST = 36;
    static final int DISPENSER_WIRE_SLOT_HOTBAR_LAST = 44;

    // The brewing stand window (Slice 11e — BrewingStandMenu lines 16-32:
    // wire 0-2 the potion bottles, 3 the ingredient, then the player's
    // 27+9; the brew timer rides the window's property 0).
    static final String BREWING_WINDOW_TYPE = "minecraft:brewing_stand";
    static final String BREWING_WINDOW_TITLE = "{\"text\":\"Brewing\"}";
    static final int BREWING_WINDOW_SLOTS = 40;
    static final int BREWING_WIRE_SLOT_LAST = 3;
    static final int BREWING_WIRE_SLOT_MAIN_FIRST = 4;
    static final int BREWING_WIRE_SLOT_MAIN_LAST = 30;
    static final int BREWING_WIRE_SLOT_HOTBAR_FIRST = 31;
    static final int BREWING_WIRE_SLOT_HOTBAR_LAST = 39;

    // The horse inventory window (protocol 47; the quirky legacy string the
    // 1.8 client switches on, plus the trailing mount entity id — the only
    // Open Window that carries one). Slot 0 saddle, slot 1 armor, player
    // tail 2-37 (main 2-28, hotbar 29-37), 38 slots total — the vanilla
    // SPacketOpenWindow carries the full menu size (inventorySlots.size()).
    // A chested donkey/mule inserts the 15-slot 3x5 grid on 2-16 (HorseMenu)
    // and grows the window to 53: main 17-43, hotbar 44-52.
    static final String HORSE_WINDOW_TYPE = "EntityHorse";
    static final String HORSE_WINDOW_TITLE = "{\"text\":\"Horse\"}";
    static final int HORSE_WINDOW_GUI_SLOTS = 2;
    static final int HORSE_WINDOW_TOTAL_SLOTS = 38;
    static final int HORSE_CHEST_WINDOW_TOTAL_SLOTS = 53;
    static final int HORSE_WIRE_SLOT_SADDLE = 0;
    static final int HORSE_WIRE_SLOT_ARMOR = 1;
    static final int HORSE_WIRE_SLOT_PLAYER_FIRST = 2;
    static final int HORSE_WIRE_SLOT_PLAYER_LAST = 37;
    static final int HORSE_CHEST_WIRE_SLOT_FIRST = 2;
    static final int HORSE_CHEST_WIRE_SLOT_LAST = 16;
    static final int HORSE_CHEST_WIRE_PLAYER_FIRST = 17;
    static final int HORSE_CHEST_WIRE_PLAYER_MAIN_LAST = 43;
    static final int HORSE_CHEST_WIRE_PLAYER_HOTBAR_FIRST = 44;
    static final int HORSE_CHEST_WIRE_PLAYER_LAST = 52;

    // The villager trading window (protocol 47; the offers ride the plugin
    // message MC|TrList — there is no Trade List packet on 47).
    static final String VILLAGER_WINDOW_TYPE = "minecraft:villager";
    static final String VILLAGER_WINDOW_TITLE = "{\"text\":\"Villager\"}";
    static final int VILLAGER_WINDOW_GUI_SLOTS = 3;
    static final String PLUGIN_CHANNEL_TRADE_LIST = "MC|TrList";
    static final String PLUGIN_CHANNEL_TRADE_SELECT = "MC|TrSel";
    static final String PLUGIN_CHANNEL_CHANNEL_LABEL = "MC|BEdit"; // (unused label guard)

    // Enchanting-table container window (protocol 47, "minecraft:enchanting_table"):
    // 0 the item (max 1), 1 the lapis, 2-28 main, 29-37 hotbar — 38 slots.
    // Open Window carries size 0 like the crafting table: the client builds
    // the two-slot layout itself from the type string (a nonzero size pushes
    // the client onto the generic-chest path).
    static final String ENCHANTING_WINDOW_TYPE = "minecraft:enchanting_table";
    static final String ENCHANTING_WINDOW_TITLE = "{\"text\":\"Enchant\"}";
    static final int ENCHANTING_WINDOW_SLOTS = 38;
    static final int ENCHANTING_WIRE_SLOT_ITEM = 0;
    static final int ENCHANTING_WIRE_SLOT_LAPIS = 1;
    static final int ENCHANTING_WIRE_SLOT_MAIN_FIRST = 2;
    static final int ENCHANTING_WIRE_SLOT_MAIN_LAST = 28;
    static final int ENCHANTING_WIRE_SLOT_HOTBAR_FIRST = 29;
    static final int ENCHANTING_WIRE_SLOT_HOTBAR_LAST = 37;
    static final int ENCHANTING_WIRE_SLOT_HOTBAR_BASE = 29;

    // Enchanting window properties (the reference addListener/updateListeners
    // order): 0-2 the XP costs, 3 the seed (already masked &-16 by the menu),
    // 4-6 the enchantment clue values (id | level << 8, -1 when none).
    static final int ENCHANTING_PROP_COST_FIRST = 0;
    static final int ENCHANTING_PROP_SEED = 3;
    static final int ENCHANTING_PROP_CLUE_FIRST = 4;
    static final int ENCHANTING_PROP_COUNT = 7;
}
