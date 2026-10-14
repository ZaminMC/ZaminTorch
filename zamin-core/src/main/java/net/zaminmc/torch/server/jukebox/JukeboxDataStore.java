package net.zaminmc.torch.server.jukebox;

import net.zaminmc.torch.block.BlockPosition;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * File-backed jukebox state ("ZJD" format, version 1), one file per world —
 * the note store's own durability rules.
 *
 * <p>Layout: magic 'Z','J','D',1 - records(varint count; each: x(i32) y(i32)
 * z(i32) then the disc in the ZCD item form (present(u8) then item
 * identifier(string) + count(varint) + damage(varint) + displayName,
 * 0 = none) — the reference's writeNbt "RecordItem" compound).</p>
 *
 * <p>Writes are atomic (temp file then atomic move), corrupt files load as
 * absent and are preserved beside the storage path instead of being
 * destroyed, and save failures are loud. Save points are the engine's
 * {@code saveAllNow} (shutdown, console save).</p>
 */
public final class JukeboxDataStore {

    private static final Logger LOGGER = Logger.getLogger(JukeboxDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'J';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 1;

    private final Path file;

    public JukeboxDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the record map. Callers snapshot state on the tick thread before
     * calling (the engine routes this through the ticker).
     */
    public void save(Map<BlockPosition, ItemStack> records) {
        Objects.requireNonNull(records, "records");
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                writeVarInt(out, records.size());
                for (Map.Entry<BlockPosition, ItemStack> entry : records.entrySet()) {
                    BlockPosition position = entry.getKey();
                    out.writeInt(position.x());
                    out.writeInt(position.y());
                    out.writeInt(position.z());
                    writeStack(out, entry.getValue());
                }
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.severe("Jukebox state save failed for " + file + ": " + e);
        }
    }

    /**
     * Loads the record map; an absent or corrupt file loads empty (corrupt
     * files are preserved, not destroyed). A stored disc the registry cannot
     * resolve drops with a warning — everything else survives.
     */
    public Map<BlockPosition, ItemStack> load() {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        Map<BlockPosition, ItemStack> loaded = new LinkedHashMap<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                LOGGER.warning("Jukebox state " + file + " is not ZJD; loading empty");
                return new LinkedHashMap<>();
            }
            int version = in.readByte() & 0xFF;
            if (version != FORMAT_VERSION) {
                LOGGER.warning("Jukebox state " + file + " version " + version
                        + " unsupported; loading empty");
                return new LinkedHashMap<>();
            }
            int count = readVarInt(in);
            if (count < 0 || count > 100_000) {
                LOGGER.warning("Jukebox state " + file + " implausible count; loading empty");
                return new LinkedHashMap<>();
            }
            for (int i = 0; i < count; i++) {
                BlockPosition position = new BlockPosition(in.readInt(), in.readInt(),
                        in.readInt());
                loaded.put(position, readStack(in));
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            LOGGER.warning("Jukebox state " + file + " corrupt; loading empty (" + corrupt + ")");
            return new LinkedHashMap<>();
        }
    }

    /** The ZCD item form (present byte + identifier + count + damage + name). */
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
            LOGGER.warning("Saved jukebox disc no longer registered, dropped: " + identifier);
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
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
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
                throw new IOException("VarInt too long");
            }
        }
    }
}
