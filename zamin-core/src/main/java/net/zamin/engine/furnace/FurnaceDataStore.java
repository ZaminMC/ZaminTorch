package net.zamin.engine.furnace;

import net.zamin.api.BlockPosition;
import net.zamin.api.Identifier;
import net.zamin.api.ItemStack;
import net.zamin.api.ItemType;
import net.zamin.engine.item.BuiltinItems;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * File-backed furnace state ("ZFD" format, version 2), one file per world.
 *
 * <p>Layout: magic 'Z','F','D',1 - furnaces(varint count; each: x(i32) y(i32)
 * z(i32) - burnRemaining(varint) burnTotal(varint) cookTime(varint) - slots(3
 * entries; each: present(u8) then item identifier(string) + count(varint) +
 * damage(varint) + displayName(varint-length UTF-8, 0 = none)). Version 1
 * files load unchanged (no custom names).</p>
 *
 * <p>The same durability rules as the world and player stores: writes are
 * atomic (temp file then atomic move), corrupt files load as absent and are
 * preserved beside the storage path instead of being destroyed, and save
 * failures are loud (§54/§286). Save points are the engine's
 * {@code saveAllNow} (shutdown, console save) — a crash can lose smelting
 * progress since the last save, the same trade the world delta store makes.</p>
 */
public final class FurnaceDataStore {

    private static final Logger LOGGER = Logger.getLogger(FurnaceDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'F';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 2;

    private final Path file;

    public FurnaceDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the full furnace map. Callers snapshot state on the tick thread
     * before calling (the engine routes this through the ticker).
     */
    public void save(Map<BlockPosition, FurnaceBlockEntity> furnaces) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                writeVarInt(out, furnaces.size());
                for (Map.Entry<BlockPosition, FurnaceBlockEntity> entry : furnaces.entrySet()) {
                    BlockPosition position = entry.getKey();
                    FurnaceBlockEntity furnace = entry.getValue();
                    out.writeInt(position.x());
                    out.writeInt(position.y());
                    out.writeInt(position.z());
                    writeVarInt(out, furnace.burnTimeRemaining());
                    writeVarInt(out, furnace.burnTimeTotal());
                    writeVarInt(out, furnace.cookTime());
                    for (ItemStack stack : furnace.snapshotSlots()) {
                        if (stack.isEmpty()) {
                            out.writeByte(0);
                        } else {
                            out.writeByte(1);
                            writeString(out, stack.type().identifier().toString());
                            writeVarInt(out, stack.count());
                            writeVarInt(out, stack.damage());
                            writeOptionalString(out, stack.displayName());
                        }
                    }
                }
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Furnace save failed for " + file, e);
        }
    }

    /**
     * Loads the furnace map. Saved items the current registry cannot resolve
     * are dropped with a warning — the registry moved on, everything else
     * survives.
     */
    public Map<BlockPosition, FurnaceBlockEntity> load() {
        if (!Files.exists(file)) {
            return Map.of();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1 || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZFD file");
            }
            int version = in.readByte();
            if (version < 1 || version > FORMAT_VERSION) {
                throw new IOException("Unsupported ZFD version: " + version);
            }
            int count = readVarInt(in);
            Map<BlockPosition, FurnaceBlockEntity> loaded = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                int burnRemaining = readVarInt(in);
                int burnTotal = readVarInt(in);
                int cook = readVarInt(in);
                FurnaceBlockEntity furnace = new FurnaceBlockEntity();
                for (int slot = 0; slot < FurnaceBlockEntity.SLOT_COUNT; slot++) {
                    if (in.readUnsignedByte() == 1) {
                        Identifier identifier = Identifier.parse(readString(in));
                        int stackCount = readVarInt(in);
                        int damage = readVarInt(in);
                        String displayName = version >= 2 ? readOptionalString(in) : null;
                        Optional<ItemType> type = BuiltinItems.lookup(identifier);
                        if (type.isPresent()) {
                            furnace.setSlot(slot,
                                    ItemStack.of(type.get(), stackCount)
                                            .withDamage(damage).withName(displayName));
                        } else {
                            LOGGER.warning("Saved furnace item no longer registered, dropped: "
                                    + identifier);
                        }
                    }
                }
                furnace.restore(burnRemaining, burnTotal, cook);
                loaded.put(new BlockPosition(x, y, z), furnace);
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            // Malformed values are corruption just as much as a broken header:
            // quarantine, then treat as absent.
            Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt");
            try {
                Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warning("Furnace data file was unreadable; preserved as " + quarantine
                        + " (" + corrupt.getMessage() + ")");
            } catch (IOException moveFailure) {
                LOGGER.warning("Furnace data file was unreadable and could not be preserved: "
                        + moveFailure);
            }
            return Map.of();
        }
    }

    /** Varint-length UTF-8; a 0 length is the "absent" marker (never null). */
    private static void writeOptionalString(DataOutputStream out, String value) throws IOException {
        if (value == null) {
            writeVarInt(out, 0);
            return;
        }
        writeString(out, value);
    }

    private static String readOptionalString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length == 0) {
            return null;
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
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
                throw new IOException("VarInt too large in furnace data file");
            }
        }
    }
}
