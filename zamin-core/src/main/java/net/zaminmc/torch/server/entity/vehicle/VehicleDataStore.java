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
     */
    private static final int FORMAT_VERSION = 4;
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
            if (version != FORMAT_VERSION && version != FORMAT_VERSION_4
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
                        cargo[slot] = readStack(in);
                    }
                } else if (kind.equals(VehicleEntity.Kind.HOPPER_MINECART.name())) {
                    cargo = new ItemStack[net.zaminmc.torch.server.hopper.HopperBlockEntity.SLOT_COUNT];
                    for (int slot = 0; slot < cargo.length; slot++) {
                        cargo[slot] = readStack(in);
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
                        fire, base));
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

    /** The ZCD item form (present byte + identifier + count + damage + name). */
    private static void writeStack(DataOutputStream out, ItemStack stack) throws IOException {
        if (stack == null || stack.isEmpty()) {
            out.writeByte(0);
        } else {
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
        }
    }

    /** The ZCD item form's read (unknown items drop with a warning). */
    private static ItemStack readStack(DataInputStream in) throws IOException {
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
            LOGGER.warning("Saved vehicle item no longer registered, dropped: " + identifier);
            return ItemStack.EMPTY;
        }
        return ItemStack.of(type.get(), count).withDamage(damage).withName(name);
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
