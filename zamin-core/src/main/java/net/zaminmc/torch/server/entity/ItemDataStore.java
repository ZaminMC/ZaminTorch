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
 * File-backed item-entity state ("ZID" format), one file per world.
 *
 * <p>Layout: magic 'Z','I','D',1 — items(varint count; each: x(f64) y(f64)
 * z(f64) — vx(f64) vy(f64) vz(f64) — the stack rows: the item id (UTF),
 * the count (varint), the damage (varint), the tag arms (the nullable
 * display-name string + the count-prefixed {id, lvl} pairs — the same
 * shape the ZMD v6 held-stack tail carries) — the age (varint), the
 * pickup delay (varint), the flags byte (bit 0 never-despawn, bit 1 the
 * thrower's presence, bit 2 the owner's presence) and the two nullable
 * strings when present). The payload is exactly
 * {@link ItemEntityManager.ItemRecord} — the reference's own
 * writeCustomNbt row set (ItemEntity lines 220-259: Health excepted —
 * the engine's item body carries no health model — Age, PickupDelay,
 * Thrower, Owner, Item) over the engine's Z-family binary shape.</p>
 *
 * <p>The same durability rules as every other store: writes are atomic
 * (temp file then atomic move), corrupt files load as absent and are
 * preserved beside the storage path, and save failures are loud. Save
 * points are the engine's {@code saveAllNow} (shutdown, console save).</p>
 */
public final class ItemDataStore {

    private static final Logger LOGGER = Logger.getLogger(ItemDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'I';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 1;

    private static final int FLAG_NEVER_DESPAWN = 0x01;
    private static final int FLAG_THROWER = 0x02;
    private static final int FLAG_OWNER = 0x04;

    private final Path file;

    public ItemDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the full item population snapshot. Callers snapshot state on
     * the tick lane; the write itself is the store's own (atomic).
     */
    public void save(List<ItemEntityManager.ItemRecord> items) {
        Objects.requireNonNull(items, "items");
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                writeVarInt(out, items.size());
                for (ItemEntityManager.ItemRecord item : items) {
                    out.writeDouble(item.position().x());
                    out.writeDouble(item.position().y());
                    out.writeDouble(item.position().z());
                    out.writeDouble(item.velocityX());
                    out.writeDouble(item.velocityY());
                    out.writeDouble(item.velocityZ());
                    // The stack rows (the ZMD v6 held-stack tail's own
                    // shape): the id, the count, the damage, the tag arms.
                    writeString(out, item.stack().type().identifier().toString());
                    writeVarInt(out, item.stack().count());
                    writeVarInt(out, item.stack().damage());
                    String name = item.stack().displayName();
                    writeNullableString(out,
                            name == null || name.isEmpty() ? null : name);
                    var enchantments = item.stack().enchantments();
                    if (enchantments == null || enchantments.isEmpty()) {
                        writeVarInt(out, 0);
                    } else {
                        writeVarInt(out, enchantments.size());
                        for (var pair : enchantments.entrySet()) {
                            out.writeShort(pair.getKey());
                            out.writeShort(pair.getValue());
                        }
                    }
                    writeVarInt(out, item.age());
                    writeVarInt(out, item.pickupDelay());
                    int flags = 0;
                    if (item.neverDespawn()) {
                        flags |= FLAG_NEVER_DESPAWN;
                    }
                    if (item.thrower() != null) {
                        flags |= FLAG_THROWER;
                    }
                    if (item.owner() != null) {
                        flags |= FLAG_OWNER;
                    }
                    out.writeByte(flags);
                    if (item.thrower() != null) {
                        writeNullableString(out, item.thrower());
                    }
                    if (item.owner() != null) {
                        writeNullableString(out, item.owner());
                    }
                }
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Item save failed for " + file, e);
        }
    }

    /**
     * Loads the item population snapshot; an absent file means "fresh
     * world". Corrupt files are quarantined and treated as absent.
     */
    public List<ItemEntityManager.ItemRecord> load() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZID file");
            }
            int version = in.readByte();
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported ZID version: " + version);
            }
            int count = readVarInt(in);
            if (count < 0 || count > 1_000_000) {
                throw new IOException("Implausible item count: " + count);
            }
            List<ItemEntityManager.ItemRecord> loaded = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                double vx = in.readDouble();
                double vy = in.readDouble();
                double vz = in.readDouble();
                String identifier = readString(in);
                int stackCount = readVarInt(in);
                int damage = readVarInt(in);
                String name = readNullableString(in);
                int enchantCount = readVarInt(in);
                java.util.Map<Integer, Integer> pairs = new java.util.LinkedHashMap<>();
                for (int e = 0; e < enchantCount; e++) {
                    int id = in.readUnsignedShort();
                    int lvl = in.readUnsignedShort();
                    if (lvl >= 1) {
                        pairs.put(id, lvl);
                    }
                }
                // An unresolvable item id (a newer save on an older build)
                // still consumes its rows: the body simply never joins the
                // world (the restore skips the empty stack).
                net.zaminmc.torch.item.ItemStack stack =
                        net.zaminmc.torch.server.item.BuiltinItems.lookup(
                                        net.zaminmc.torch.util.Identifier.parse(identifier))
                                .map(type -> net.zaminmc.torch.item.ItemStack.of(
                                        type, Math.max(1, stackCount)))
                                .orElse(null);
                if (stack != null) {
                    stack = stack.withDamage(damage);
                    if (name != null) {
                        stack = stack.withName(name);
                    }
                    if (!pairs.isEmpty()) {
                        stack = stack.withEnchantments(pairs);
                    }
                }
                int age = readVarInt(in);
                int pickupDelay = readVarInt(in);
                int flags = in.readUnsignedByte();
                String thrower = (flags & FLAG_THROWER) != 0
                        ? readNullableString(in) : null;
                String owner = (flags & FLAG_OWNER) != 0
                        ? readNullableString(in) : null;
                if (stack == null) {
                    continue;
                }
                loaded.add(new ItemEntityManager.ItemRecord(
                        new net.zaminmc.torch.util.Position(x, y, z),
                        vx, vy, vz, stack, age, pickupDelay,
                        (flags & FLAG_NEVER_DESPAWN) != 0, thrower, owner));
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            // Malformed values are corruption just as much as a broken
            // header: quarantine, then treat as absent.
            Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt");
            try {
                Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warning("Item data file was unreadable; preserved as " + quarantine
                        + " (" + corrupt.getMessage() + ")");
            } catch (IOException moveFailure) {
                LOGGER.warning("Item data file was unreadable and could not be preserved: "
                        + moveFailure);
            }
            return List.of();
        }
    }

    private static void writeNullableString(DataOutputStream out, String value)
            throws IOException {
        if (value == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            writeString(out, value);
        }
    }

    private static String readNullableString(DataInputStream in) throws IOException {
        return in.readBoolean() ? readString(in) : null;
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
                throw new IOException("VarInt too large in item data file");
            }
        }
    }
}
