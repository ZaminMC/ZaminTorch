package net.zaminmc.torch.server.brewing;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * File-backed brewing stand state ("ZBD" format, version 1), one file per
 * world (Slice 11f — the ledgered slot + timer persistence of the 11e
 * slice, the reference's own readNbt/writeNbt Items + BrewTime pair).
 *
 * <p>Layout: magic 'Z','B','D',1 - stands(varint count; each: x(i32)
 * y(i32) z(i32) - brewTime(varint) - slots(4 entries; each: present(u8)
 * then item identifier(string) + count(varint) + damage(varint) +
 * displayName(varint-length UTF-8, 0 = none))).</p>
 *
 * <p>The reference does not persist the armed ingredient identity — a
 * restored mid-brew cycle cancels on the first post-load tick (the
 * null-ingredient quirk the block entity's restore models), so the
 * timer rides along exactly like the reference's own BrewTime.</p>
 *
 * <p>The same durability rules as the other stores: atomic writes,
 * corrupt files quarantine-load as absent, failures are loud (§54/§286).
 * Save points are the engine's {@code saveAllNow}.</p>
 */
public final class BrewingStandDataStore {

    private static final Logger LOGGER = Logger.getLogger(BrewingStandDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'B';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 1;

    private final Path file;

    public BrewingStandDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the full stand map. Callers snapshot state on the tick thread
     * before calling (the engine routes this through the ticker).
     */
    public void save(Map<BlockPosition, BrewingStandBlockEntity> stands) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                writeVarInt(out, stands.size());
                for (Map.Entry<BlockPosition, BrewingStandBlockEntity> entry : stands.entrySet()) {
                    BlockPosition position = entry.getKey();
                    BrewingStandBlockEntity stand = entry.getValue();
                    out.writeInt(position.x());
                    out.writeInt(position.y());
                    out.writeInt(position.z());
                    writeVarInt(out, stand.timer());
                    for (ItemStack stack : stand.snapshotSlots()) {
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
            throw new IllegalStateException("Brewing stand save failed for " + file, e);
        }
    }

    /**
     * Loads the stand map. Saved items the current registry cannot resolve
     * are dropped with a warning — the registry moved on, everything else
     * survives.
     */
    public Map<BlockPosition, BrewingStandBlockEntity> load() {
        if (!Files.exists(file)) {
            return Map.of();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1 || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZBD file");
            }
            int version = in.readByte();
            if (version < 1 || version > FORMAT_VERSION) {
                throw new IOException("Unsupported ZBD version: " + version);
            }
            int count = readVarInt(in);
            Map<BlockPosition, BrewingStandBlockEntity> loaded = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                int timer = readVarInt(in);
                BrewingStandBlockEntity stand = new BrewingStandBlockEntity();
                ItemStack[] slots = new ItemStack[BrewingStandBlockEntity.SLOT_COUNT];
                Arrays.fill(slots, ItemStack.EMPTY);
                for (int slot = 0; slot < BrewingStandBlockEntity.SLOT_COUNT; slot++) {
                    if (in.readUnsignedByte() == 1) {
                        Identifier identifier = Identifier.parse(readString(in));
                        int stackCount = readVarInt(in);
                        int damage = readVarInt(in);
                        String displayName = readOptionalString(in);
                        Optional<ItemType> type = BuiltinItems.lookup(identifier);
                        if (type.isPresent()) {
                            slots[slot] = ItemStack.of(type.get(), stackCount)
                                    .withDamage(damage).withName(displayName);
                        } else {
                            LOGGER.warning("Saved brewing stand item no longer registered, dropped: "
                                    + identifier);
                        }
                    }
                }
                stand.restoreState(slots, timer);
                loaded.put(new BlockPosition(x, y, z), stand);
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            // Malformed values are corruption just as much as a broken
            // header: quarantine, then treat as absent.
            Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt");
            try {
                Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warning("Brewing stand data file was unreadable; preserved as " + quarantine
                        + " (" + corrupt.getMessage() + ")");
            } catch (IOException moveFailure) {
                LOGGER.warning("Brewing stand data file was unreadable and could not be preserved: "
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
        return new String(bytes, StandardCharsets.UTF_8);
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
                throw new IOException("VarInt too large in brewing stand data file");
            }
        }
    }
}
