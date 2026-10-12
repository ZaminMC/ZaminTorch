package net.zaminmc.torch.server.chest;

import net.zaminmc.torch.item.ItemType;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;

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
 * File-backed chest state ("ZCD" format, version 2), one file per world.
 *
 * <p>Layout: magic 'Z','C','D',2 - chests(varint count; each: x(i32) y(i32)
 * z(i32) - slots(27 entries; each: present(u8) then item identifier(string) +
 * count(varint) + damage(varint) + displayName(varint-length UTF-8, 0 =
 * none))). Version 1 files load unchanged (no custom names).</p>
 *
 * <p>The same durability rules as the world, player and furnace stores: writes
 * are atomic (temp file then atomic move), corrupt files load as absent and
 * are preserved beside the storage path instead of being destroyed, and save
 * failures are loud (§54/§286). Save points are the engine's
 * {@code saveAllNow} (shutdown, console save) — a crash can lose chest
 * contents since the last save, the same trade the other stores make.</p>
 */
public final class ChestDataStore {

    private static final Logger LOGGER = Logger.getLogger(ChestDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'C';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 2;

    private final Path file;

    public ChestDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the full chest map. Callers snapshot state on the tick thread
     * before calling (the engine routes this through the ticker).
     */
    public void save(Map<BlockPosition, ChestBlockEntity> chests) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                writeVarInt(out, chests.size());
                for (Map.Entry<BlockPosition, ChestBlockEntity> entry : chests.entrySet()) {
                    BlockPosition position = entry.getKey();
                    ChestBlockEntity chest = entry.getValue();
                    out.writeInt(position.x());
                    out.writeInt(position.y());
                    out.writeInt(position.z());
                    for (ItemStack stack : chest.snapshotSlots()) {
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
            throw new IllegalStateException("Chest save failed for " + file, e);
        }
    }

    /**
     * Loads the chest map. Saved items the current registry cannot resolve
     * are dropped with a warning — the registry moved on, everything else
     * survives.
     */
    public Map<BlockPosition, ChestBlockEntity> load() {
        if (!Files.exists(file)) {
            return Map.of();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1 || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZCD file");
            }
            int version = in.readByte();
            if (version < 1 || version > FORMAT_VERSION) {
                throw new IOException("Unsupported ZCD version: " + version);
            }
            int count = readVarInt(in);
            Map<BlockPosition, ChestBlockEntity> loaded = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                ChestBlockEntity chest = new ChestBlockEntity();
                for (int slot = 0; slot < ChestBlockEntity.SLOT_COUNT; slot++) {
                    if (in.readUnsignedByte() == 1) {
                        Identifier identifier = Identifier.parse(readString(in));
                        int stackCount = readVarInt(in);
                        int damage = readVarInt(in);
                        String displayName = version >= 2 ? readOptionalString(in) : null;
                        Optional<net.zaminmc.torch.item.ItemType> type = BuiltinItems.lookup(identifier);
                        if (type.isPresent()) {
                            chest.setSlot(slot, ItemStack.of(type.get(), stackCount)
                                    .withDamage(damage).withName(displayName));
                        } else {
                            LOGGER.warning("Saved chest item no longer registered, dropped: "
                                    + identifier);
                        }
                    }
                }
                loaded.put(new BlockPosition(x, y, z), chest);
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            // Malformed values are corruption just as much as a broken header:
            // quarantine, then treat as absent.
            Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt");
            try {
                Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warning("Chest data file was unreadable; preserved as " + quarantine
                        + " (" + corrupt.getMessage() + ")");
            } catch (IOException moveFailure) {
                LOGGER.warning("Chest data file was unreadable and could not be preserved: "
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
                throw new IOException("VarInt too large in chest data file");
            }
        }
    }
}
