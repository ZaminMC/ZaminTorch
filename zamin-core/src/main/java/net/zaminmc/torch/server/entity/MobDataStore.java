package net.zaminmc.torch.server.entity;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * File-backed mob state ("ZMD" format), one file per world.
 *
 * <p>Layout: magic 'Z','M','D',2 — mobs(varint count; each: type(string) —
 * x(f64) y(f64) z(f64) — yaw(f32) health(f32) — the v2 state block:
 * flags(u8 bitmask) then, per bit, the kind's own fields — bit 0 the
 * zombie's villager byte + converting varint, bit 1 the villager's
 * profession byte + career/careerLevel/riches varints, bit 2 the rabbit's
 * coat byte, bit 3 the breeding-age int, bit 4 the skeleton's wither byte,
 * bit 5 the slime family's size byte). Version 1 files (the bare
 * six-field records) still load — every record answers the no-extras
 * shape. The payload is exactly {@link MobManager.MobSnapshot}.</p>
 *
 * <p>The same durability rules as the world, furnace, chest and player
 * stores: writes are atomic (temp file then atomic move), corrupt files load
 * as absent and are preserved beside the storage path instead of being
 * destroyed, and save failures are loud (§54/§286). Save points are the
 * engine's {@code saveAllNow} (shutdown, console save).</p>
 */
public final class MobDataStore {

    private static final Logger LOGGER = Logger.getLogger(MobDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'M';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 13;
    /** The v13 shape (VIII4 — the armor tail: the four armor rows + the DropChances rows). */
    private static final int FORMAT_VERSION_13 = 13;
    /** The v12 shape (Wave K — the SkullOwner row; the bare-FORMAT_VERSION
     *  gate the v13 bump's audit renamed, the v8 bump's own lesson). */
    private static final int FORMAT_VERSION_12 = 12;

    /** The v7 store's own version (Slice 94's Wave G tail — the load keeps reading it). */
    private static final int FORMAT_VERSION_7 = 7;
    /** The v8 store's own version (Slice 95's BlockEntityTag row — the load keeps reading it). */
    private static final int FORMAT_VERSION_8 = 8;
    /** The pre-color shape (Slice 96): the stack rows stop after the CanPlaceOn list. */
    private static final int FORMAT_VERSION_9 = 9;
    /** The pre-J1 shape (Wave I): the stack rows stop after the display.color row. */
    private static final int FORMAT_VERSION_10 = 10;
    /** The pre-K2 shape (Wave J): the stack rows stop after the attribute-modifiers row. */
    private static final int FORMAT_VERSION_11 = 11;

    /** The v6 store's own version (Slice 92's held-stack tail — the load keeps reading it). */
    private static final int FORMAT_VERSION_6 = 6;

    /** The v4 store's own version (Slice 78b's shape — the load keeps reading it). */
    private static final int FORMAT_VERSION_4 = 4;

    /** The v2 store's own version (Slice 25c's shape — the load keeps reading it). */
    private static final int FORMAT_VERSION_2 = 2;
    /** The Slice 75 shape (the name/NoAI/invulnerable/persistent rows, no equipment, no attributes). */
    private static final int FORMAT_VERSION_3 = 3;
    /** The v2 state block's bits (Slice 25c): one per kind's own family. */
    private static final int FLAG_ZOMBIE_VILLAGER = 0x01;
    private static final int FLAG_VILLAGER_TRADE = 0x02;
    private static final int FLAG_RABBIT_VARIANT = 0x04;
    private static final int FLAG_BREEDING_AGE = 0x08;
    private static final int FLAG_SKELETON_WITHER = 0x10;
    private static final int FLAG_SLIME_SIZE = 0x20;
    /** The kind's dye byte (Slice 30b): the sheep's fleece + 1 or the wolf's collar + 1 (0 = absent). */
    private static final int FLAG_DYE_COLOR = 0x80;
    /** The body's dimension (Slice 25d — Entity.dimension, 0 the overworld). */
    private static final int FLAG_DIMENSION = 0x40;

    private final Path file;

    public MobDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the full population snapshot. Callers snapshot state on the tick
     * thread before calling (the engine routes this through the ticker).
     * The synchronous form composes the S5 pipeline's own two halves —
     * the pure serialization and the file publication — so the sync
     * save's bytes and the pipeline's bytes are identical by
     * construction (one serializer, one file half).
     */
    public void save(List<MobManager.MobSnapshot> mobs) {
        writeSerialized(serialize(mobs));
    }

    /**
     * The pure serialization (the compute pool's own half, Slice S5): the
     * immutable snapshot IN (the records are the fresh values the
     * manager's own snapshot walk builds on the tick lane — the value
     * records over the immutable ItemStack model, the maps and arrays
     * the walk's own fresh copies), the ZMD bytes OUT — no
     * world read, no I/O. The byte walk is the historical save walk's
     * own (byte-identical).
     */
    public byte[] serialize(List<MobManager.MobSnapshot> mobs) {
        Objects.requireNonNull(mobs, "mobs");
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(sink))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                writeVarInt(out, mobs.size());
                for (MobManager.MobSnapshot mob : mobs) {
                    writeString(out, mob.type());
                    out.writeDouble(mob.x());
                    out.writeDouble(mob.y());
                    out.writeDouble(mob.z());
                    out.writeFloat(mob.yaw());
                    out.writeFloat(mob.health());
                    writeExtras(out, mob.extras());
                    writeNbtExtras(out, mob.nbt());
                    // The v5 command-stat tail (Slice 90): the bound pairs
                    // after the NBT rows.
                    writeCommandStatsPairs(out, mob.commandStats());
                    // The v6 tameable/horse tail (Slice 92): the owner
                    // pair, the tamed bit, the family's sitting/angry, the
                    // horse's bred markers.
                    writeTameExtras(out, mob.tame());
                    // The v13 armor tail (VIII4 — the reference's Equipment
                    // list's slots 1..4 + the DropChances rows): the
                    // record's own trailing block, always one presence byte
                    // so a pre-v13 reader never desyncs (and this reader
                    // never misparses a v12 file).
                    writeArmorTail(out, mob.nbt());
                }
        } catch (IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud (the store's
            // own contract).
            throw new IllegalStateException("Mob serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZMD bytes: the temp-file
     * write + the atomic move — the historical save walk's own file half
     * with the store's own failure contract 1:1 (the loud throw, never a
     * silent loss). Safe from any thread that holds the publication
     * right (the save pipeline's per-storage lock orders the async
     * publications; the synchronous save is uncontended).
     */
    public void writeSerialized(byte[] data) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.write(data);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Mob save failed for " + file, e);
        }
    }

    /**
     * Loads the population snapshot; an absent file means "fresh world".
     * Corrupt files are quarantined and treated as absent.
     */
    public List<MobManager.MobSnapshot> load() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1 || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZMD file");
            }
            int version = in.readByte();
            if (version != FORMAT_VERSION && version != FORMAT_VERSION_12
                    && version != FORMAT_VERSION_11
                    && version != FORMAT_VERSION_10
                    && version != FORMAT_VERSION_9
                    && version != FORMAT_VERSION_8
                    && version != FORMAT_VERSION_7
                    && version != FORMAT_VERSION_6
                    && version != FORMAT_VERSION_4
                    && version != FORMAT_VERSION_3 && version != FORMAT_VERSION_2
                    && version != 1) {
                throw new IOException("Unsupported ZMD version: " + version);
            }
            int count = readVarInt(in);
            if (count < 0 || count > 1_000_000) {
                throw new IOException("Implausible mob count: " + count);
            }
            List<MobManager.MobSnapshot> loaded = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String type = readString(in);
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                float health = in.readFloat();
                MobManager.MobStateExtras extras = version == 1
                        ? null : readExtras(in);
                MobManager.MobNbtExtras nbt = version >= FORMAT_VERSION_3
                        ? readNbtExtras(in, version) : null;
                // The v5 command-stat tail (Slice 90 — the reference's
                // Entity.writeNbt line 1128: every entity carries its
                // CommandResults in the save).
                java.util.Map<String, String> commandStats = version >= 5
                        ? readCommandStatsPairs(in) : null;
                // The v6 tameable/horse tail (Slice 92). The gate names its
                // own version: Slice 94's 6->7 bump left it riding the bare
                // FORMAT_VERSION constant (silently >= 7 — a v6 file's tame
                // rows would never be consumed and the stream would
                // misparse into the quarantine path); the v8 bump's audit
                // caught it.
                MobManager.MobTameExtras tame = version >= FORMAT_VERSION_6
                        ? readTameExtras(in) : null;
                // The v13 armor tail (VIII4): the four armor rows + the
                // DropChances rows, riding the record's own trailing block.
                if (version >= FORMAT_VERSION_13) {
                    nbt = readArmorTail(in, nbt);
                }
                loaded.add(new MobManager.MobSnapshot(type, x, y, z, yaw, health,
                        extras, nbt, commandStats, tame));
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            // Malformed values are corruption just as much as a broken header:
            // quarantine, then treat as absent (the population rebuilds).
            Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt");
            try {
                Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warning("Mob data file was unreadable; preserved as " + quarantine
                        + " (" + corrupt.getMessage() + ")");
            } catch (IOException moveFailure) {
                LOGGER.warning("Mob data file was unreadable and could not be preserved: "
                        + moveFailure);
            }
            return List.of();
        }
    }

    /** Writes the v2 state block (the flags byte + the present fields). */
    private static void writeExtras(DataOutputStream out, MobManager.MobStateExtras extras)
            throws IOException {
        if (extras == null) {
            out.writeByte(0); // the bare shape (a v1-equivalent record)
            return;
        }
        int flags = 0;
        if (extras.zombieVillager()) {
            flags |= FLAG_ZOMBIE_VILLAGER;
        }
        if (extras.villagerCareer() != 0 || extras.villagerCareerLevel() != 0
                || extras.villagerRiches() != 0 || extras.villagerProfession() != 0) {
            flags |= FLAG_VILLAGER_TRADE;
        }
        if (extras.rabbitVariant() != 0) {
            flags |= FLAG_RABBIT_VARIANT;
        }
        if (extras.breedingAge() != 0) {
            flags |= FLAG_BREEDING_AGE;
        }
        if (extras.skeletonWither()) {
            flags |= FLAG_SKELETON_WITHER;
        }
        if (extras.slimeSize() != 1) {
            flags |= FLAG_SLIME_SIZE;
        }
        if (extras.dimension() != 0) {
            flags |= FLAG_DIMENSION;
        }
        if (extras.dyeColor() != 0) {
            flags |= FLAG_DYE_COLOR;
        }
        out.writeByte(flags);
        if ((flags & FLAG_ZOMBIE_VILLAGER) != 0) {
            out.writeByte(1);
            writeVarInt(out, Math.max(0, extras.zombieConversionTicks()));
        }
        if ((flags & FLAG_VILLAGER_TRADE) != 0) {
            out.writeByte(extras.villagerProfession());
            writeVarInt(out, Math.max(0, extras.villagerCareer()));
            writeVarInt(out, Math.max(0, extras.villagerCareerLevel()));
            writeVarInt(out, Math.max(0, extras.villagerRiches()));
        }
        if ((flags & FLAG_RABBIT_VARIANT) != 0) {
            out.writeByte(extras.rabbitVariant());
        }
        if ((flags & FLAG_BREEDING_AGE) != 0) {
            out.writeInt(extras.breedingAge()); // the signed int (babies are negative)
        }
        if ((flags & FLAG_SKELETON_WITHER) != 0) {
            out.writeByte(1);
        }
        if ((flags & FLAG_SLIME_SIZE) != 0) {
            out.writeByte(extras.slimeSize());
        }
        if ((flags & FLAG_DIMENSION) != 0) {
            out.writeByte(extras.dimension());
        }
        if ((flags & FLAG_DYE_COLOR) != 0) {
            out.writeByte(extras.dyeColor());
        }
    }

    // ------------------------------------------------------------------
    // The v3 NBT block (Slice 75 — the /summon rows): one presence byte,
    // then the named rows. The v2 files carry no block (the load's own
    // version gate reads none).
    // ------------------------------------------------------------------

    private static final int NBT_FLAG_CUSTOM_NAME = 0x01;
    private static final int NBT_FLAG_CUSTOM_NAME_VISIBLE = 0x02;
    private static final int NBT_FLAG_NO_AI = 0x04;
    private static final int NBT_FLAG_INVULNERABLE = 0x08;
    private static final int NBT_FLAG_PERSISTENT = 0x10;
    /** Slice 78b's rows: the Equipment row's held stack + the Attributes overrides. */
    private static final int NBT_FLAG_EQUIPMENT = 0x20;
    private static final int NBT_FLAG_MOVEMENT_SPEED = 0x40;
    private static final int NBT_FLAG_ATTACK_DAMAGE = 0x80;

    /** Writes the command-stat pairs (the v5 tail): the pair count then the key/value strings. */

    private static void writeNullableString(DataOutputStream out, String value)
            throws IOException {
        if (value == null) {
            out.writeByte(0);
            return;
        }
        out.writeByte(1);
        writeString(out, value);
    }

    private static String readNullableString(DataInputStream in) throws IOException {
        if (in.readByte() == 0) {
            return null;
        }
        return readString(in);
    }

    /**
     * The attribute modifiers' SNBT row (Wave J — the v11 tail's own form):
     * the ItemNbt write shape wrapped in the one-row compound the parser
     * reads back ({@code {AttributeModifiers:[..]}}), the whole list as
     * ONE string row — the BlockEntityTag tail's own pattern (the store's
     * own local copy, the writeString family's own convention).
     */
    private static String attributeModifiersRow(
            java.util.List<net.zaminmc.torch.item.AttributeModifier> modifiers) {
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> tag =
                new java.util.LinkedHashMap<>();
        java.util.List<net.zaminmc.torch.server.nbt.Snbt.Tag> elements =
                new java.util.ArrayList<>(modifiers.size());
        for (net.zaminmc.torch.item.AttributeModifier modifier : modifiers) {
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> element =
                    new java.util.LinkedHashMap<>();
            net.zaminmc.torch.server.nbt.NbtOps.putString(element,
                    "AttributeName", modifier.attributeName());
            net.zaminmc.torch.server.nbt.NbtOps.putString(element, "Name", modifier.name());
            net.zaminmc.torch.server.nbt.NbtOps.putDouble(element, "Amount", modifier.amount());
            net.zaminmc.torch.server.nbt.NbtOps.putInt(element, "Operation", modifier.operation());
            net.zaminmc.torch.server.nbt.NbtOps.putLong(element, "UUIDMost",
                    modifier.uuid().getMostSignificantBits());
            net.zaminmc.torch.server.nbt.NbtOps.putLong(element, "UUIDLeast",
                    modifier.uuid().getLeastSignificantBits());
            elements.add(net.zaminmc.torch.server.nbt.Snbt.Tag.ofCompound(element));
        }
        net.zaminmc.torch.server.nbt.NbtOps.putList(tag, "AttributeModifiers", elements);
        return net.zaminmc.torch.server.nbt.NbtOps.writeParseable(tag);
    }

    /**
     * The SkullOwner SNBT row (Wave K — the v12 tail's own form): the
     * profile's compound wrapped in the one-row compound the parser
     * reads back (the ZID v7 row's own shape, the store's local copy).
     */
    private static String skullOwnerRow(net.zaminmc.torch.item.SkullProfile profile) {
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> tag =
                new java.util.LinkedHashMap<>();
        net.zaminmc.torch.server.nbt.TileNbt.writeProfile(tag, "SkullOwner", profile);
        return net.zaminmc.torch.server.nbt.NbtOps.writeParseable(tag);
    }

    /**
     * The v6 tameable/horse tail (Slice 92): a null marker byte, else the
     * present byte + the owner pair + the flags.
     */
    private static void writeTameExtras(DataOutputStream out,
            MobManager.MobTameExtras tame) throws IOException {
        if (tame == null) {
            out.writeByte(0);
            return;
        }
        out.writeByte(1);
        writeNullableString(out, tame.ownerName());
        writeNullableString(out, tame.ownerUuid());
        out.writeBoolean(tame.tamed());
        out.writeBoolean(tame.sitting());
        out.writeBoolean(tame.angry());
        out.writeBoolean(tame.horseBred());
        out.writeBoolean(tame.horseHasReproduced());
    }

    /** The tail's read (the mirror of {@link #writeTameExtras}). */
    private static MobManager.MobTameExtras readTameExtras(DataInputStream in)
            throws IOException {
        if (in.readByte() == 0) {
            return null;
        }
        String ownerName = readNullableString(in);
        String ownerUuid = readNullableString(in);
        return new MobManager.MobTameExtras(ownerName, ownerUuid,
                in.readBoolean(), in.readBoolean(), in.readBoolean(),
                in.readBoolean(), in.readBoolean());
    }
    private static void writeCommandStatsPairs(DataOutputStream out,
            java.util.Map<String, String> commandStats) throws IOException {
        int count = commandStats == null ? 0 : commandStats.size();
        writeVarInt(out, count);
        if (commandStats != null) {
            for (java.util.Map.Entry<String, String> pair : commandStats.entrySet()) {
                writeString(out, pair.getKey());
                writeString(out, pair.getValue());
            }
        }
    }

    /** Reads the command-stat pairs back (null when the count is zero). */
    private static java.util.Map<String, String> readCommandStatsPairs(DataInputStream in)
            throws IOException {
        int pairs = readVarInt(in);
        if (pairs < 0 || pairs > 64) {
            throw new IOException("Implausible command-stat pair count: " + pairs);
        }
        if (pairs == 0) {
            return null;
        }
        java.util.Map<String, String> commandStats = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs; i++) {
            commandStats.put(readString(in), readString(in));
        }
        return commandStats;
    }

    /** Writes the v3/v4 NBT block (a zero byte answers the null shape). */
    private static void writeNbtExtras(DataOutputStream out, MobManager.MobNbtExtras nbt)
            throws IOException {
        if (nbt == null) {
            out.writeByte(0);
            return;
        }
        int flags = 0;
        if (nbt.customName() != null) {
            flags |= NBT_FLAG_CUSTOM_NAME;
        }
        if (nbt.customNameVisible()) {
            flags |= NBT_FLAG_CUSTOM_NAME_VISIBLE;
        }
        if (nbt.noAi()) {
            flags |= NBT_FLAG_NO_AI;
        }
        if (nbt.invulnerable()) {
            flags |= NBT_FLAG_INVULNERABLE;
        }
        if (nbt.persistent()) {
            flags |= NBT_FLAG_PERSISTENT;
        }
        if (nbt.heldEquipment() != null && !nbt.heldEquipment().isEmpty()) {
            flags |= NBT_FLAG_EQUIPMENT;
        }
        if (!Double.isNaN(nbt.movementSpeed())) {
            flags |= NBT_FLAG_MOVEMENT_SPEED;
        }
        if (!Double.isNaN(nbt.attackDamage())) {
            flags |= NBT_FLAG_ATTACK_DAMAGE;
        }
        out.writeByte(flags);
        if ((flags & NBT_FLAG_CUSTOM_NAME) != 0) {
            writeString(out, nbt.customName());
        }
        if ((flags & NBT_FLAG_EQUIPMENT) != 0) {
            writeEquipmentRows(out, nbt.heldEquipment());
        }
        if ((flags & NBT_FLAG_MOVEMENT_SPEED) != 0) {
            out.writeDouble(nbt.movementSpeed());
        }
        if ((flags & NBT_FLAG_ATTACK_DAMAGE) != 0) {
            out.writeDouble(nbt.attackDamage());
        }
    }

    /**
     * The per-stack row family (the v4-v12 tails the held row grew, one
     * helper since VIII4 — the four armor slots ride the SAME bytes): the
     * identifier string, the count byte, the damage short, then the tails
     * — the display name + the enchant pairs (v6), the Unbreakable /
     * RepairCost / HideFlags / Lore rows (v7), the BlockEntityTag SNBT row
     * (v8), the canDestroy/canPlaceOn lists (v9), the displayColor (v10),
     * the AttributeModifiers row (v11), the SkullOwner row (v12).
     */
    private static void writeEquipmentRows(DataOutputStream out,
            net.zaminmc.torch.item.ItemStack stack) throws IOException {
        writeString(out, stack.type().identifier().toString());
        out.writeByte(stack.count());
        out.writeShort(stack.damage());
        // The v6 tail (Slice 92 — the ItemNbt tag arms' own model):
        // the display name + the enchant pairs (the nullable-string
        // pair + the count-prefixed short pairs).
        String displayName = stack.displayName();
        writeNullableString(out, displayName == null || displayName.isEmpty() ? null : displayName);
        var enchantments = stack.enchantments();
        if (enchantments == null || enchantments.isEmpty()) {
            writeVarInt(out, 0);
        } else {
            writeVarInt(out, enchantments.size());
            for (var pair : enchantments.entrySet()) {
                out.writeShort(pair.getKey());
                out.writeShort(pair.getValue());
            }
        }
        // The v7 tail (Slice 94 — the Wave G arms on the stack model):
        // the Unbreakable byte, the RepairCost varint, the HideFlags
        // varint, then the Lore list (the count + the strings).
        out.writeBoolean(stack.unbreakable());
        writeVarInt(out, stack.repairCost());
        writeVarInt(out, stack.hideFlags());
        var lore = stack.lore();
        if (lore == null || lore.isEmpty()) {
            writeVarInt(out, 0);
        } else {
            writeVarInt(out, lore.size());
            for (String line : lore) {
                writeString(out, line);
            }
        }
        // The v8 tail (Slice 95 — the BlockEntityTag raw compound, the
        // Wave H2 arm): the whole subtree as ONE SNBT string row
        // (NbtOps.write over the adapted boxes). Absent arm writes the
        // null marker.
        writeNullableString(out, stack.blockEntityTag() == null
                ? null
                : net.zaminmc.torch.server.nbt.NbtOps.writeParseable(
                        net.zaminmc.torch.server.nbt.NbtOps.fromBoxed(
                                stack.blockEntityTag())));
        // The v9 tail (Wave I — the adventure gates' lists): the
        // count + the strings, the Lore row's own shape.
        writeVarInt(out, stack.canDestroy() == null
                ? 0 : stack.canDestroy().size());
        if (stack.canDestroy() != null) {
            for (String key : stack.canDestroy()) {
                writeString(out, key);
            }
        }
        writeVarInt(out, stack.canPlaceOn() == null
                ? 0 : stack.canPlaceOn().size());
        if (stack.canPlaceOn() != null) {
            for (String key : stack.canPlaceOn()) {
                writeString(out, key);
            }
        }
        // The v10 tail (the leather tint): a nullable int row.
        out.writeBoolean(stack.displayColor() != null);
        if (stack.displayColor() != null) {
            out.writeInt(stack.displayColor());
        }
        // The v11 tail (Wave J — the attribute modifiers): the whole
        // list as ONE parseable-SNBT string row (the BlockEntityTag
        // tail's own pattern — the wrapped compound the parser reads
        // back). An absent arm writes the null marker.
        writeNullableString(out, stack.attributeModifiers() == null
                ? null
                : attributeModifiersRow(stack.attributeModifiers()));
        // The v12 tail (Wave K — the player head's SkullOwner): ONE
        // parseable-SNBT string row, the profile's compound form.
        writeNullableString(out, stack.skullOwner() == null
                ? null
                : skullOwnerRow(stack.skullOwner()));
    }

    /**
     * Reads the NBT block back (null for the bare zero-flags shape); the
     * v4 rows (the equipment stack + the attribute overrides) ride the
     * same flags byte's high bits — a v3 file's low flags load unchanged.
     */
    private static MobManager.MobNbtExtras readNbtExtras(DataInputStream in, int version)
            throws IOException {
        int flags = in.readUnsignedByte();
        if (flags == 0) {
            return null;
        }
        String customName = null;
        if ((flags & NBT_FLAG_CUSTOM_NAME) != 0) {
            customName = readString(in);
        }
        if (version < FORMAT_VERSION_4) {
            return new MobManager.MobNbtExtras(customName,
                    (flags & NBT_FLAG_CUSTOM_NAME_VISIBLE) != 0,
                    (flags & NBT_FLAG_NO_AI) != 0,
                    (flags & NBT_FLAG_INVULNERABLE) != 0,
                    (flags & NBT_FLAG_PERSISTENT) != 0);
        }
        net.zaminmc.torch.item.ItemStack held = null;
        if ((flags & NBT_FLAG_EQUIPMENT) != 0) {
            held = readEquipmentRows(in, version);
        }
        double movementSpeed = (flags & NBT_FLAG_MOVEMENT_SPEED) != 0
                ? in.readDouble() : Double.NaN;
        double attackDamage = (flags & NBT_FLAG_ATTACK_DAMAGE) != 0
                ? in.readDouble() : Double.NaN;
        return new MobManager.MobNbtExtras(customName,
                (flags & NBT_FLAG_CUSTOM_NAME_VISIBLE) != 0,
                (flags & NBT_FLAG_NO_AI) != 0,
                (flags & NBT_FLAG_INVULNERABLE) != 0,
                (flags & NBT_FLAG_PERSISTENT) != 0,
                held, movementSpeed, attackDamage);
    }

    /**
     * The read half of the per-stack row family (one helper since VIII4 —
     * the four armor slots ride the SAME bytes): the identifier/count/
     * damage triple, then the version-gated tails — each row names its OWN
     * version constant (the v8/v10/v11 bump audits' own lesson: a bare
     * {@code >= FORMAT_VERSION} gate skips the just-renamed version's rows
     * and misparses the file).
     */
    private static net.zaminmc.torch.item.ItemStack readEquipmentRows(
            DataInputStream in, int version) throws IOException {
        String identifier = readString(in);
        int count = in.readUnsignedByte();
        int damage = in.readUnsignedShort();
        net.zaminmc.torch.item.ItemStack stack =
                net.zaminmc.torch.server.item.BuiltinItems.lookup(
                                net.zaminmc.torch.util.Identifier.parse(identifier))
                        .map(type -> net.zaminmc.torch.item.ItemStack.of(type, count)
                                .withDamage(damage))
                        .orElse(null);
        if (stack == null || version < FORMAT_VERSION_6) {
            return stack;
        }
        // The v6 tail (Slice 92): the display name + the enchant pairs
        // land on the model.
        String displayName = readNullableString(in);
        if (displayName != null) {
            stack = stack.withName(displayName);
        }
        int enchCount = readVarInt(in);
        if (enchCount > 0) {
            java.util.Map<Integer, Integer> pairs = new java.util.LinkedHashMap<>();
            for (int i = 0; i < enchCount; i++) {
                int id = in.readUnsignedShort();
                int lvl = in.readUnsignedShort();
                if (lvl >= 1) {
                    pairs.put(id, lvl);
                }
            }
            stack = stack.withEnchantments(pairs);
        }
        // The v7 tail (Slice 94 — the Wave G rows): absent in a v6 file,
        // the model's defaults then.
        if (version < FORMAT_VERSION_7) {
            return stack;
        }
        if (in.readBoolean()) {
            stack = stack.withUnbreakable(true);
        }
        int repairCost = readVarInt(in);
        if (repairCost > 0) {
            stack = stack.withRepairCost(repairCost);
        }
        int hideFlags = readVarInt(in);
        if (hideFlags > 0) {
            stack = stack.withHideFlags(hideFlags);
        }
        int loreCount = readVarInt(in);
        if (loreCount > 0) {
            java.util.List<String> lines = new java.util.ArrayList<>(loreCount);
            for (int i = 0; i < loreCount; i++) {
                lines.add(readString(in));
            }
            stack = stack.withLore(lines);
        }
        // The v8 tail (Slice 95 — the BlockEntityTag raw compound, the
        // Wave H2 arm): the whole subtree as ONE SNBT string row. Absent
        // arm writes the null marker.
        String blockEntityTagRow = version >= FORMAT_VERSION_8
                ? readNullableString(in) : null;
        if (blockEntityTagRow != null) {
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> parsed =
                    net.zaminmc.torch.server.nbt.Snbt.parseCompound(blockEntityTagRow);
            if (!parsed.isEmpty()) {
                stack = stack.withBlockEntityTag(
                        net.zaminmc.torch.server.nbt.NbtOps.toBoxed(parsed));
            }
        }
        // The v9 tail (Wave I): the two lists — the gate names its own
        // version (the v10 bump's audit: an unconditional read inside the
        // v7 block would misparse v7/v8 files).
        int destroyCount = version >= FORMAT_VERSION_9 ? readVarInt(in) : 0;
        if (destroyCount > 0) {
            java.util.List<String> keys = new java.util.ArrayList<>(destroyCount);
            for (int k = 0; k < destroyCount; k++) {
                keys.add(readString(in));
            }
            stack = stack.withCanDestroy(keys);
        }
        int placeCount = version >= FORMAT_VERSION_9 ? readVarInt(in) : 0;
        if (placeCount > 0) {
            java.util.List<String> keys = new java.util.ArrayList<>(placeCount);
            for (int k = 0; k < placeCount; k++) {
                keys.add(readString(in));
            }
            stack = stack.withCanPlaceOn(keys);
        }
        // The v10 tail (the leather tint): absent in a v9 file — the row
        // names its OWN version (the v11 bump's audit: the old >=
        // FORMAT_VERSION gate would skip a v10 file's color row and
        // misparse the file).
        if (version >= FORMAT_VERSION_10 && in.readBoolean()) {
            stack = stack.withDisplayColor(in.readInt());
        }
        // The v11 tail (Wave J — the attribute modifiers): absent in a v10
        // file — the row names its OWN version (the v12 bump's audit).
        String attributeModifiersRow = version >= FORMAT_VERSION_11
                ? readNullableString(in) : null;
        if (attributeModifiersRow != null) {
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> parsed =
                    net.zaminmc.torch.server.nbt.Snbt.parseCompound(
                            attributeModifiersRow);
            if (net.zaminmc.torch.server.nbt.NbtOps.contains(parsed,
                    "AttributeModifiers",
                    net.zaminmc.torch.server.nbt.Snbt.Tag.Type.LIST)) {
                stack = stack.withAttributeModifiers(
                        net.zaminmc.torch.server.nbt.ItemNbt
                                .readAttributeModifiers(parsed));
            }
        }
        // The v12 tail (Wave K — the SkullOwner row): absent in a v11 file —
        // the gate names its OWN version constant (the v13 bump's audit: the
        // bare FORMAT_VERSION would skip a v12 file's row and misparse the
        // file).
        String skullOwnerRow = version >= FORMAT_VERSION_12
                ? readNullableString(in) : null;
        if (skullOwnerRow != null) {
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> parsed =
                    net.zaminmc.torch.server.nbt.Snbt.parseCompound(
                            skullOwnerRow);
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> owner =
                    net.zaminmc.torch.server.nbt.NbtOps.getCompound(parsed,
                            "SkullOwner");
            net.zaminmc.torch.item.SkullProfile profile =
                    net.zaminmc.torch.server.nbt.TileNbt.readProfile(owner);
            if (profile != null) {
                stack = stack.withSkullOwner(profile);
            }
        }
        return stack;
    }

    /**
     * The v13 armor tail's write (VIII4 — the reference's Equipment list's
     * slots 1..4 + the DropChances rows): one presence byte (0 = the record
     * carries no armor rows and every chance at the constructor's own
     * 0.085 default), else the four per-slot presence bytes (1 + the full
     * stack-row family per present slot — the SAME bytes the held row
     * rides) and the chances block (one presence byte, then five floats —
     * the reference's DropChances list shape, written only when a slot
     * left the default).
     */
    private static void writeArmorTail(DataOutputStream out, MobManager.MobNbtExtras nbt)
            throws IOException {
        if (nbt == null) {
            out.writeByte(0);
            return;
        }
        net.zaminmc.torch.item.ItemStack[] slots = {
                nbt.feetEquipment(), nbt.legsEquipment(),
                nbt.chestEquipment(), nbt.headEquipment()};
        boolean anyArmor = false;
        for (net.zaminmc.torch.item.ItemStack stack : slots) {
            if (stack != null && !stack.isEmpty()) {
                anyArmor = true;
                break;
            }
        }
        boolean chances = nbt.dropChances() != null;
        if (!anyArmor && !chances) {
            out.writeByte(0);
            return;
        }
        out.writeByte(1);
        for (net.zaminmc.torch.item.ItemStack stack : slots) {
            if (stack != null && !stack.isEmpty()) {
                out.writeByte(1);
                writeEquipmentRows(out, stack);
            } else {
                out.writeByte(0);
            }
        }
        if (chances) {
            out.writeByte(1);
            for (float chance : nbt.dropChances()) {
                out.writeFloat(chance);
            }
        } else {
            out.writeByte(0);
        }
    }

    /**
     * The v13 armor tail's read: the presence byte first (0 = no rows), else
     * the four per-slot stacks (the same stack-row family, the full v13 tail
     * set) and the chances block, folded back into the record. The tail is
     * SELF-SUFFICIENT: an armor-only body carries a bare NBT block (the
     * flags byte holds no armor bits) beside a present tail — the read
     * builds the record from the tail alone in that shape.
     */
    private static MobManager.MobNbtExtras readArmorTail(DataInputStream in,
            MobManager.MobNbtExtras nbt) throws IOException {
        if (in.readUnsignedByte() == 0) {
            return nbt;
        }
        net.zaminmc.torch.item.ItemStack[] slots = new net.zaminmc.torch.item.ItemStack[4];
        for (int i = 0; i < 4; i++) {
            slots[i] = in.readUnsignedByte() == 1
                    ? readEquipmentRows(in, FORMAT_VERSION) : null;
        }
        float[] chances = null;
        if (in.readUnsignedByte() == 1) {
            chances = new float[5];
            for (int i = 0; i < 5; i++) {
                chances[i] = in.readFloat();
            }
        }
        if (nbt == null) {
            // The armor-only body: the bare NBT block beside the present
            // tail — the record grows from the tail alone.
            return new MobManager.MobNbtExtras(null, false, false, false, false,
                    null, Double.NaN, Double.NaN,
                    slots[0], slots[1], slots[2], slots[3], chances);
        }
        return new MobManager.MobNbtExtras(nbt.customName(), nbt.customNameVisible(),
                nbt.noAi(), nbt.invulnerable(), nbt.persistent(),
                nbt.heldEquipment(), nbt.movementSpeed(), nbt.attackDamage(),
                slots[0], slots[1], slots[2], slots[3], chances);
    }

    /** Reads the v2 state block back (null for the bare zero-flags shape). */
    private static MobManager.MobStateExtras readExtras(DataInputStream in)
            throws IOException {
        int flags = in.readUnsignedByte();
        if (flags == 0) {
            return null; // the bare shape — the v1-equivalent record
        }
        boolean zombieVillager = false;
        int zombieConversion = 0;
        int profession = 0;
        int career = 0;
        int careerLevel = 0;
        int riches = 0;
        int rabbitVariant = 0;
        int breedingAge = 0;
        boolean wither = false;
        int slimeSize = 1;
        int dyeColor = 0;
        if ((flags & FLAG_ZOMBIE_VILLAGER) != 0) {
            zombieVillager = in.readUnsignedByte() == 1;
            zombieConversion = readVarInt(in);
        }
        if ((flags & FLAG_VILLAGER_TRADE) != 0) {
            profession = in.readUnsignedByte();
            career = readVarInt(in);
            careerLevel = readVarInt(in);
            riches = readVarInt(in);
        }
        if ((flags & FLAG_RABBIT_VARIANT) != 0) {
            rabbitVariant = in.readUnsignedByte();
        }
        if ((flags & FLAG_BREEDING_AGE) != 0) {
            breedingAge = in.readInt();
        }
        if ((flags & FLAG_SKELETON_WITHER) != 0) {
            wither = in.readUnsignedByte() == 1;
        }
        if ((flags & FLAG_SLIME_SIZE) != 0) {
            slimeSize = in.readUnsignedByte();
        }
        int dimension = 0;
        if ((flags & FLAG_DIMENSION) != 0) {
            // The byte carries the dimension (-1 lands as the u8 255).
            dimension = in.readByte();
        }
        if ((flags & FLAG_DYE_COLOR) != 0) {
            dyeColor = in.readUnsignedByte();
        }
        return new MobManager.MobStateExtras(zombieVillager, zombieConversion,
                profession, career, careerLevel, riches, rabbitVariant, breedingAge,
                wither, slimeSize, dimension, dyeColor);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > 256) {
            throw new IOException("Implausible string length: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static int readVarInt(DataInputStream in) throws IOException {
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
                throw new IOException("VarInt too large in mob data file");
            }
        }
    }
}
