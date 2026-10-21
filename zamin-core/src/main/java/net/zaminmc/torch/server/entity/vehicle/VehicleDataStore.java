package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;

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
import java.util.Optional;
import java.util.logging.Logger;

/**
 * File-backed vehicle state ("ZVD" format, version 1), one file per world.
 *
 * <p>Layout: magic 'Z','V','D',1 — vehicles(varint count; each: kind(string)
 * — x(f64) y(f64) z(f64) — yaw(f32) — health(f32) — the CHEST_MINECART's
 * cargo: 27 slots in the ZCD item form (present(u8) then item
 * identifier(string) + count(varint) + damage(varint) + displayName,
 * 0 = none); the other kinds carry no cargo block). The payload is exactly
 * {@link VehicleManager.VehicleSnapshot}.</p>
 *
 * <p>The same durability rules as the world, chest, furnace and mob stores:
 * writes are atomic (temp file then atomic move), corrupt files load as
 * absent and are preserved beside the storage path instead of being
 * destroyed, and save failures are loud (§54/§286). Save points are the
 * engine's {@code saveAllNow} (shutdown, console save) — the whole
 * vehicle family's pre-25g gap (the carts carried no store at all) closes
 * here: the chest minecart's cargo now survives restarts.</p>
 */
public final class VehicleDataStore {

    private static final Logger LOGGER = Logger.getLogger(VehicleDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'V';
    private static final int MAGIC_2 = 'D';
    /**
     * Version 3 (Slice 78): the burn clock's own row — the reference's
     * {@code Fire} short (Entity.java lines 1115/1176), the raw signed
     * timer every kind carries (negative = the grace band). Version 2
     * and 1 files load with the fresh-body default (-1).
     *
     * <p>Version 6 (VIII11 — the chest-cart envelope's stack-row tail): a
     * present cargo stack now carries the WHOLE ItemStack NBT family the
     * ZMD/ZID stack rows ride (the display name and the enchant pairs,
     * the Wave G scalars and the Lore list, the BlockEntityTag SNBT row,
     * the adventure gates, the leather tint, the AttributeModifiers and
     * SkullOwner SNBT rows, the Fireworks compound, and the book family's
     * pages/author/title/resolved/generation rows) — an enchanted sword,
     * a firework rocket with its fuse row, or a SIGNED BOOK riding a
     * chest or hopper cart survives the restart whole. Version 1-5
     * files load with the bare id/count/damage/name form (the cargo the
     * old walk wrote; the pre-v6 store carried no other arms).</p>
     */
    private static final int FORMAT_VERSION = 6;
    private static final int FORMAT_VERSION_6 = 6;
    private static final int FORMAT_VERSION_5 = 5;
    private static final int FORMAT_VERSION_4 = 4;
    private static final int FORMAT_VERSION_3 = 3;
    private static final int FORMAT_VERSION_2 = 2;
    private static final int FORMAT_VERSION_1 = 1;

    private final Path file;

    public VehicleDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the vehicle population snapshot. Callers snapshot state on the
     * tick thread before calling (the engine routes this through the
     * ticker). The synchronous form composes the S5 pipeline's own two
     * halves — the pure serialization and the file publication — so the
     * sync save's bytes and the pipeline's bytes are identical by
     * construction (one serializer, one file half).
     */
    public void save(List<VehicleManager.VehicleSnapshot> vehicles) {
        writeSerialized(serialize(vehicles));
    }

    /**
     * The pure serialization (the compute pool's own half, Slice S5): the
     * immutable snapshot IN (the records are the fresh values the
     * manager's own snapshot walk builds on the tick lane — the value
     * records over the immutable ItemStack model, the maps and arrays
     * the walk's own fresh copies), the ZVD bytes OUT — no
     * world read, no I/O. The byte walk is the historical save walk's
     * own (byte-identical).
     */
    public byte[] serialize(List<VehicleManager.VehicleSnapshot> vehicles) {
        Objects.requireNonNull(vehicles, "vehicles");
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(sink))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                writeVarInt(out, vehicles.size());
                for (VehicleManager.VehicleSnapshot vehicle : vehicles) {
                    writeString(out, vehicle.kind());
                    out.writeDouble(vehicle.x());
                    out.writeDouble(vehicle.y());
                    out.writeDouble(vehicle.z());
                    out.writeFloat(vehicle.yaw());
                    out.writeFloat(vehicle.health());
                    if (vehicle.cargo() != null) {
                        for (ItemStack stack : vehicle.cargo()) {
                            writeStack(out, stack);
                        }
                    }
                    if (vehicle.kind().equals(VehicleEntity.Kind.HOPPER_MINECART.name())) {
                        // The variant extras (the reference's own NBT rows:
                        // writeCustomNbt's TransferCooldown + the enable
                        // flag's own bit).
                        out.writeByte(vehicle.hopperEnabled() ? 1 : 0);
                        out.writeInt(vehicle.hopperCooldown());
                    } else if (vehicle.kind().equals(
                            VehicleEntity.Kind.SPAWNER_MINECART.name())) {
                        // The spawner's own rows (MobSpawner.writeNbt's
                        // EntityId/Delay — a null type writes the empty
                        // string, the reference's absent-row shape).
                        writeString(out, vehicle.spawnerType() == null
                                ? "" : vehicle.spawnerType());
                        writeVarInt(out, vehicle.spawnerDelay());
                        // v5: the cadence rows (the Wave VI per-cart surface
                        // — writeNbt's own six: the band pair, the attempts,
                        // the cap, the gate, the roll range; a null row set
                        // writes the reference's own defaults).
                        VehicleManager.CadenceRows cadence = vehicle.cadence();
                        if (cadence == null) {
                            cadence = new VehicleManager.CadenceRows(
                                    SpawnerMinecartEntity.MIN_SPAWN_DELAY,
                                    SpawnerMinecartEntity.MAX_SPAWN_DELAY,
                                    SpawnerMinecartEntity.SPAWN_COUNT,
                                    SpawnerMinecartEntity.MAX_NEARBY_ENTITIES,
                                    SpawnerMinecartEntity.REQUIRED_PLAYER_RANGE,
                                    SpawnerMinecartEntity.SPAWN_RANGE);
                        }
                        writeVarInt(out, cadence.minSpawnDelay());
                        writeVarInt(out, cadence.maxSpawnDelay());
                        writeVarInt(out, cadence.spawnCount());
                        writeVarInt(out, cadence.maxNearbyEntities());
                        writeVarInt(out, cadence.requiredPlayerRange());
                        writeVarInt(out, cadence.spawnRange());
                    } else if (vehicle.kind().equals(
                            VehicleEntity.Kind.COMMAND_BLOCK_MINECART.name())) {
                        // The command executor's own rows
                        // (CommandExecutor.writeNbt lines 37-51): Command,
                        // SuccessCount, TrackOutput, LastOutput.
                        writeString(out, vehicle.command() == null
                                ? "" : vehicle.command());
                        writeVarInt(out, vehicle.commandSuccessCount());
                        out.writeByte(vehicle.commandTrackOutput() ? 1 : 0);
                        writeString(out, vehicle.commandLastOutput() == null
                                ? "" : vehicle.commandLastOutput());
                    }
                    // The burn clock's own row (Slice 78 — Entity.java line
                    // 1115's {@code Fire} short, every kind: the raw signed
                    // timer — a burning cart keeps its burn, a grace-band
                    // cart keeps its grace, across the restart).
                    out.writeShort(vehicle.fire());
                    // v4: the base rows (the reference's Entity.readNbt base
                    // band — the import's landing round-trips through the
                    // restart now).
                    VehicleManager.BaseRows base = vehicle.base();
                    out.writeLong(base.uuidMost());
                    out.writeLong(base.uuidLeast());
                    out.writeByte(base.hasUuid() ? 1 : 0);
                    writeString(out, base.customName() == null ? "" : base.customName());
                    out.writeByte(base.customNameVisible() ? 1 : 0);
                    out.writeByte(base.silent() ? 1 : 0);
                    out.writeFloat(base.fallDistance());
                    out.writeShort(base.breath());
                    out.writeByte(base.onGround() ? 1 : 0);
                    out.writeByte(base.invulnerable() ? 1 : 0);
                    out.writeInt(base.dimension());
                    writeVarInt(out, base.portalCooldown());
                }
        } catch (IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud (the store's
            // own contract).
            throw new IllegalStateException("Vehicle serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZVD bytes: the temp-file
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
            throw new IllegalStateException("Vehicle save failed for " + file, e);
        }
    }

    /**
     * Loads the vehicle population snapshot; an absent file means "fresh
     * world". Corrupt files are quarantined and treated as absent. Saved
     * kinds and items the current registries cannot resolve are dropped
     * with a warning — everything else survives.
     */
    public List<VehicleManager.VehicleSnapshot> load() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZVD file");
            }
            int version = in.readByte();
            if (version != FORMAT_VERSION && version != FORMAT_VERSION_6
                    && version != FORMAT_VERSION_5
                    && version != FORMAT_VERSION_4
                    && version != FORMAT_VERSION_3 && version != FORMAT_VERSION_2
                    && version != FORMAT_VERSION_1) {
                throw new IOException("Unsupported ZVD version: " + version);
            }
            int count = readVarInt(in);
            if (count < 0 || count > 100_000) {
                throw new IOException("Implausible vehicle count: " + count);
            }
            List<VehicleManager.VehicleSnapshot> loaded = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String kind = readString(in);
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                float health = in.readFloat();
                ItemStack[] cargo = null;
                if (kind.equals(VehicleEntity.Kind.CHEST_MINECART.name())) {
                    cargo = new ItemStack[net.zaminmc.torch.server.chest.ChestBlockEntity.SLOT_COUNT];
                    for (int slot = 0; slot < cargo.length; slot++) {
                        cargo[slot] = readStack(in, version);
                    }
                } else if (kind.equals(VehicleEntity.Kind.HOPPER_MINECART.name())) {
                    cargo = new ItemStack[net.zaminmc.torch.server.hopper.HopperBlockEntity.SLOT_COUNT];
                    for (int slot = 0; slot < cargo.length; slot++) {
                        cargo[slot] = readStack(in, version);
                    }
                }
                boolean hopperEnabled = true;
                int hopperCooldown = -1;
                String spawnerType = null;
                int spawnerDelay = -1;
                String command = null;
                int commandSuccessCount = 0;
                boolean commandTrackOutput = true;
                String commandLastOutput = null;
                // The fresh-body default (the reference's absent Fire row
                // reads back as the -fireResistance grace).
                int fire = -VehicleEntity.FIRE_SAFE_TICKS;
                VehicleManager.CadenceRows cadence = null;
                if (version >= FORMAT_VERSION_2) {
                    if (kind.equals(VehicleEntity.Kind.HOPPER_MINECART.name())) {
                        hopperEnabled = in.readUnsignedByte() != 0;
                        hopperCooldown = in.readInt();
                    } else if (kind.equals(
                            VehicleEntity.Kind.SPAWNER_MINECART.name())) {
                        spawnerType = readString(in);
                        if (spawnerType.isEmpty()) {
                            spawnerType = null;
                        }
                        spawnerDelay = readVarInt(in);
                        // v5: the cadence rows (the Wave VI per-cart surface
                        // — the saved band, attempts, cap, gate, and roll
                        // range; older files load the reference's defaults).
                        if (version >= FORMAT_VERSION_5) {
                            cadence = new VehicleManager.CadenceRows(
                                    readVarInt(in), readVarInt(in), readVarInt(in),
                                    readVarInt(in), readVarInt(in), readVarInt(in));
                        }
                    } else if (kind.equals(
                            VehicleEntity.Kind.COMMAND_BLOCK_MINECART.name())) {
                        command = readString(in);
                        commandSuccessCount = readVarInt(in);
                        commandTrackOutput = in.readUnsignedByte() != 0;
                        commandLastOutput = readString(in);
                        if (commandLastOutput.isEmpty()) {
                            commandLastOutput = null;
                        }
                    }
                }
                if (version >= FORMAT_VERSION) {
                    // Slice 78's own row: the raw signed burn clock.
                    fire = in.readShort();
                }
                VehicleManager.BaseRows base = VehicleManager.BaseRows.DEFAULT;
                if (version >= FORMAT_VERSION_4) {
                    // The base rows (the reference's Entity.readNbt base
                    // band): the UUID pair + its presence flag, the custom
                    // name (the empty string reads as none), the visibility
                    // and silent flags, the fall accumulator, the breath
                    // clock, the ground state, the invulnerable flag, the
                    // dimension id, the portal cooldown.
                    long uuidMost = in.readLong();
                    long uuidLeast = in.readLong();
                    boolean hasUuid = in.readUnsignedByte() == 1;
                    String customName = readString(in);
                    boolean customNameVisible = in.readUnsignedByte() == 1;
                    boolean silent = in.readUnsignedByte() == 1;
                    float fallDistance = in.readFloat();
                    int breath = in.readShort();
                    boolean onGround = in.readUnsignedByte() == 1;
                    boolean invulnerable = in.readUnsignedByte() == 1;
                    int dimension = in.readInt();
                    int portalCooldown = readVarInt(in);
                    base = new VehicleManager.BaseRows(uuidMost, uuidLeast, hasUuid,
                            customName.isEmpty() ? null : customName, customNameVisible,
                            silent, fallDistance, breath, onGround, invulnerable,
                            dimension, portalCooldown);
                }
                loaded.add(new VehicleManager.VehicleSnapshot(
                        kind, x, y, z, yaw, health, cargo, hopperEnabled, hopperCooldown,
                        spawnerType, spawnerDelay,
                        command, commandSuccessCount, commandTrackOutput, commandLastOutput,
                        fire, base, cadence));
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            // Malformed values are corruption just as much as a broken
            // header: quarantine, then treat as absent.
            Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt");
            try {
                Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warning("Vehicle data file was unreadable; preserved as " + quarantine
                        + " (" + corrupt.getMessage() + ")");
            } catch (IOException moveFailure) {
                LOGGER.warning("Vehicle data file was unreadable and could not be preserved: "
                        + moveFailure);
            }
            return List.of();
        }
    }

    /**
     * The ZCD item form (present byte + identifier + count + damage + name)
     * grown by the v6 tail (VIII11 — the whole ItemStack NBT family in the
     * ZMD/ZID stack-row shape, so a cart's cargo round-trips every arm the
     * model carries).
     */
    private static void writeStack(DataOutputStream out, ItemStack stack) throws IOException {
        if (stack == null || stack.isEmpty()) {
            out.writeByte(0);
            return;
        }
        out.writeByte(1);
        writeString(out, stack.type().identifier().toString());
        writeVarInt(out, stack.count());
        writeVarInt(out, stack.damage());
        String name = stack.displayName();
        if (name == null) {
            writeVarInt(out, 0);
        } else {
            byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
            writeVarInt(out, bytes.length);
            out.write(bytes);
        }
        writeStackRows(out, stack);
    }

    /**
     * The v6 stack-row tail (VIII11 — the ZMD writeEquipmentRows family's
     * own shape): the enchant pairs, the Wave G scalars + Lore, the
     * BlockEntityTag SNBT row, the adventure gates, the leather tint, the
     * AttributeModifiers and SkullOwner SNBT rows, the Fireworks compound,
     * and the book family's five rows. Each row rides its model default
     * when the stack carries nothing (the absent-means-default store
     * convention).
     */
    private static void writeStackRows(DataOutputStream out, ItemStack stack) throws IOException {
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
        writeNullableString(out, stack.blockEntityTag() == null
                ? null
                : net.zaminmc.torch.server.nbt.NbtOps.writeParseable(
                        net.zaminmc.torch.server.nbt.NbtOps.fromBoxed(
                                stack.blockEntityTag())));
        writeVarInt(out, stack.canDestroy() == null ? 0 : stack.canDestroy().size());
        if (stack.canDestroy() != null) {
            for (String key : stack.canDestroy()) {
                writeString(out, key);
            }
        }
        writeVarInt(out, stack.canPlaceOn() == null ? 0 : stack.canPlaceOn().size());
        if (stack.canPlaceOn() != null) {
            for (String key : stack.canPlaceOn()) {
                writeString(out, key);
            }
        }
        out.writeBoolean(stack.displayColor() != null);
        if (stack.displayColor() != null) {
            out.writeInt(stack.displayColor());
        }
        writeNullableString(out, stack.attributeModifiers() == null
                ? null
                : attributeModifiersRow(stack.attributeModifiers()));
        writeNullableString(out, stack.skullOwner() == null
                ? null
                : skullOwnerRow(stack.skullOwner()));
        writeNullableString(out, stack.fireworks() == null
                ? null
                : net.zaminmc.torch.server.nbt.NbtOps.writeParseable(
                        net.zaminmc.torch.server.nbt.NbtOps.fromBoxed(
                                stack.fireworks())));
        // The book family's rows (VIII12): the count + the strings, then
        // the nullable author/title, the resolved byte, the generation
        // varint.
        var pages = stack.bookPages();
        if (pages == null || pages.isEmpty()) {
            writeVarInt(out, 0);
        } else {
            writeVarInt(out, pages.size());
            for (String page : pages) {
                writeString(out, page);
            }
        }
        writeNullableString(out, stack.bookAuthor());
        writeNullableString(out, stack.bookTitle());
        out.writeBoolean(stack.bookResolved());
        writeVarInt(out, stack.bookGeneration());
    }

    /** The ZCD item form's read (unknown items drop with a warning; the v6
     * stack-row tail rides the file's own version). */
    private static ItemStack readStack(DataInputStream in, int version) throws IOException {
        if (in.readUnsignedByte() != 1) {
            return ItemStack.EMPTY;
        }
        Identifier identifier = Identifier.parse(readString(in));
        int count = readVarInt(in);
        int damage = readVarInt(in);
        int nameLength = readVarInt(in);
        String name = null;
        if (nameLength > 0) {
            if (nameLength > 256) {
                throw new IOException("Implausible name length: " + nameLength);
            }
            byte[] bytes = new byte[nameLength];
            in.readFully(bytes);
            name = new String(bytes, StandardCharsets.UTF_8);
        }
        Optional<net.zaminmc.torch.item.ItemType> type = BuiltinItems.lookup(identifier);
        if (type.isEmpty()) {
            if (version >= FORMAT_VERSION_6) {
                skipStackRows(in); // the dropped stack's tail still rides the file
            }
            LOGGER.warning("Saved vehicle item no longer registered, dropped: " + identifier);
            return ItemStack.EMPTY;
        }
        ItemStack stack = ItemStack.of(type.get(), count).withDamage(damage).withName(name);
        if (version >= FORMAT_VERSION_6) {
            stack = readStackRows(in, stack);
        }
        return stack;
    }

    /**
     * The v6 stack-row tail's read (VIII11 — the writeStackRows mirror,
     * row for row). A corrupt SNBT row is the file's own corruption: the
     * load's IOException quarantine owns it.
     */
    private static ItemStack readStackRows(DataInputStream in, ItemStack stack)
            throws IOException {
        int enchCount = readVarInt(in);
        if (enchCount < 0 || enchCount > 256) {
            throw new IOException("Implausible enchantment count: " + enchCount);
        }
        if (enchCount > 0) {
            java.util.Map<Integer, Integer> pairs = new java.util.LinkedHashMap<>();
            for (int i = 0; i < enchCount; i++) {
                pairs.put(in.readUnsignedShort(), in.readUnsignedShort());
            }
            stack = stack.withEnchantments(pairs);
        }
        boolean unbreakable = in.readBoolean();
        if (unbreakable) {
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
        if (loreCount < 0 || loreCount > 256) {
            throw new IOException("Implausible lore count: " + loreCount);
        }
        if (loreCount > 0) {
            java.util.List<String> lines = new java.util.ArrayList<>(loreCount);
            for (int i = 0; i < loreCount; i++) {
                lines.add(readString(in));
            }
            stack = stack.withLore(lines);
        }
        String blockEntityTagRow = readNullableString(in);
        if (blockEntityTagRow != null) {
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> parsed =
                    net.zaminmc.torch.server.nbt.Snbt.parseCompound(blockEntityTagRow);
            if (!parsed.isEmpty()) {
                stack = stack.withBlockEntityTag(
                        net.zaminmc.torch.server.nbt.NbtOps.toBoxed(parsed));
            }
        }
        int canDestroyCount = readVarInt(in);
        if (canDestroyCount < 0 || canDestroyCount > 256) {
            throw new IOException("Implausible CanDestroy count: " + canDestroyCount);
        }
        if (canDestroyCount > 0) {
            java.util.List<String> keys = new java.util.ArrayList<>(canDestroyCount);
            for (int i = 0; i < canDestroyCount; i++) {
                keys.add(readString(in));
            }
            stack = stack.withCanDestroy(keys);
        }
        int canPlaceOnCount = readVarInt(in);
        if (canPlaceOnCount < 0 || canPlaceOnCount > 256) {
            throw new IOException("Implausible CanPlaceOn count: " + canPlaceOnCount);
        }
        if (canPlaceOnCount > 0) {
            java.util.List<String> keys = new java.util.ArrayList<>(canPlaceOnCount);
            for (int i = 0; i < canPlaceOnCount; i++) {
                keys.add(readString(in));
            }
            stack = stack.withCanPlaceOn(keys);
        }
        if (in.readBoolean()) {
            stack = stack.withDisplayColor(in.readInt());
        }
        String attributeModifiersRow = readNullableString(in);
        if (attributeModifiersRow != null) {
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> parsed =
                    net.zaminmc.torch.server.nbt.Snbt.parseCompound(attributeModifiersRow);
            if (net.zaminmc.torch.server.nbt.NbtOps.contains(parsed, "AttributeModifiers",
                    net.zaminmc.torch.server.nbt.Snbt.Tag.Type.LIST)) {
                stack = stack.withAttributeModifiers(
                        net.zaminmc.torch.server.nbt.ItemNbt.readAttributeModifiers(parsed));
            }
        }
        String skullOwnerRow = readNullableString(in);
        if (skullOwnerRow != null) {
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> parsed =
                    net.zaminmc.torch.server.nbt.Snbt.parseCompound(skullOwnerRow);
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> owner =
                    net.zaminmc.torch.server.nbt.NbtOps.getCompound(parsed, "SkullOwner");
            net.zaminmc.torch.item.SkullProfile profile =
                    net.zaminmc.torch.server.nbt.TileNbt.readProfile(owner);
            if (profile != null) {
                stack = stack.withSkullOwner(profile);
            }
        }
        String fireworksRow = readNullableString(in);
        if (fireworksRow != null) {
            java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> parsed =
                    net.zaminmc.torch.server.nbt.Snbt.parseCompound(fireworksRow);
            if (!parsed.isEmpty()) {
                stack = stack.withFireworks(
                        net.zaminmc.torch.server.nbt.NbtOps.toBoxed(parsed));
            }
        }
        // The book family's rows (VIII12).
        int pageCount = readVarInt(in);
        if (pageCount < 0 || pageCount > net.zaminmc.torch.item.ItemStack.MAX_BOOK_PAGES) {
            throw new IOException("Implausible book page count: " + pageCount);
        }
        if (pageCount > 0) {
            java.util.List<String> pages = new java.util.ArrayList<>(pageCount);
            for (int i = 0; i < pageCount; i++) {
                pages.add(readString(in));
            }
            stack = stack.withBookPages(pages);
        }
        String author = readNullableString(in);
        if (author != null) {
            stack = stack.withBookAuthor(author);
        }
        String title = readNullableString(in);
        if (title != null) {
            stack = stack.withBookTitle(title);
        }
        boolean resolved = in.readBoolean();
        if (resolved) {
            stack = stack.withBookResolved(true);
        }
        int generation = readVarInt(in);
        if (generation > 0) {
            stack = stack.withBookGeneration(generation);
        }
        return stack;
    }

    /** Skips the v6 tail's rows (the unregistered item's dropped stack). */
    private static void skipStackRows(DataInputStream in) throws IOException {
        // The mirror of readStackRows with every payload discarded.
        int enchCount = readVarInt(in);
        if (enchCount < 0 || enchCount > 256) {
            throw new IOException("Implausible enchantment count: " + enchCount);
        }
        for (int i = 0; i < enchCount; i++) {
            in.skipBytes(4);
        }
        in.readBoolean(); // unbreakable
        readVarInt(in);   // repairCost
        readVarInt(in);   // hideFlags
        int loreCount = readVarInt(in);
        if (loreCount < 0 || loreCount > 256) {
            throw new IOException("Implausible lore count: " + loreCount);
        }
        for (int i = 0; i < loreCount; i++) {
            readString(in);
        }
        readNullableString(in); // blockEntityTag
        int canDestroyCount = readVarInt(in);
        if (canDestroyCount < 0 || canDestroyCount > 256) {
            throw new IOException("Implausible CanDestroy count: " + canDestroyCount);
        }
        for (int i = 0; i < canDestroyCount; i++) {
            readString(in);
        }
        int canPlaceOnCount = readVarInt(in);
        if (canPlaceOnCount < 0 || canPlaceOnCount > 256) {
            throw new IOException("Implausible CanPlaceOn count: " + canPlaceOnCount);
        }
        for (int i = 0; i < canPlaceOnCount; i++) {
            readString(in);
        }
        if (in.readBoolean()) {
            in.skipBytes(4); // displayColor
        }
        readNullableString(in); // attributeModifiers
        readNullableString(in); // skullOwner
        readNullableString(in); // fireworks
        int pageCount = readVarInt(in);
        if (pageCount < 0 || pageCount > net.zaminmc.torch.item.ItemStack.MAX_BOOK_PAGES) {
            throw new IOException("Implausible book page count: " + pageCount);
        }
        for (int i = 0; i < pageCount; i++) {
            readString(in);
        }
        readNullableString(in); // author
        readNullableString(in); // title
        in.readBoolean();       // resolved
        readVarInt(in);         // generation
    }

    /**
     * The AttributeModifiers SNBT row (the ZMD v11 tail's own form — the
     * store's local copy, the writeString family's own convention): the
     * compound wrapping the list the parser reads back.
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
     * The SkullOwner SNBT row (the ZMD v12 tail's own form — the store's
     * local copy): the profile's compound wrapped in the one-row compound
     * the parser reads back.
     */
    private static String skullOwnerRow(net.zaminmc.torch.item.SkullProfile profile) {
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> tag =
                new java.util.LinkedHashMap<>();
        net.zaminmc.torch.server.nbt.TileNbt.writeProfile(tag, "SkullOwner", profile);
        return net.zaminmc.torch.server.nbt.NbtOps.writeParseable(tag);
    }

    private static void writeNullableString(DataOutputStream out, String value) throws IOException {
        if (value == null || value.isEmpty()) {
            writeVarInt(out, 0);
        } else {
            writeString(out, value);
        }
    }

    private static String readNullableString(DataInputStream in) throws IOException {
        String value = readString(in);
        return value.isEmpty() ? null : value;
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
                throw new IOException("VarInt too large in vehicle data file");
            }
        }
    }
}
