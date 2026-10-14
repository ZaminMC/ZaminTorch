package net.zaminmc.torch.server.beacon;

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
import java.util.Optional;
import java.util.logging.Logger;

/**
 * File-backed beacon state ("ZBT" format, version 1), one file per world —
 * the chest store's own durability rules.
 *
 * <p>Layout: magic 'Z','B','T',1 - beacons(varint count; each: x(i32) y(i32)
 * z(i32) - levels(varint) - primary(varint) - secondary(varint) - payment
 * (present(u8) then item identifier(string) + count(varint) + damage(varint)
 * + displayName(varint-length UTF-8, 0 = none)).</p>
 *
 * <p>Writes are atomic (temp file then atomic move), corrupt files load as
 * absent and are preserved beside the storage path, and save failures are
 * loud. Save points are the engine's {@code saveAllNow}.</p>
 */
public final class BeaconDataStore {

    private static final Logger LOGGER = Logger.getLogger(BeaconDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'B';
    private static final int MAGIC_2 = 'T';
    private static final int FORMAT_VERSION = 1;

    private final Path file;

    public BeaconDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /** Saves the full beacon map (the engine routes this through the ticker). */
    public void save(Map<BlockPosition, BeaconBlockEntity> beacons) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                writeVarInt(out, beacons.size());
                for (Map.Entry<BlockPosition, BeaconBlockEntity> entry : beacons.entrySet()) {
                    BlockPosition position = entry.getKey();
                    BeaconBlockEntity beacon = entry.getValue();
                    out.writeInt(position.x());
                    out.writeInt(position.y());
                    out.writeInt(position.z());
                    writeVarInt(out, beacon.levels());
                    writeVarInt(out, beacon.primary());
                    writeVarInt(out, beacon.secondary());
                    ItemStack payment = beacon.payment();
                    if (payment.isEmpty()) {
                        out.writeByte(0);
                    } else {
                        out.writeByte(1);
                        writeString(out, payment.type().identifier().toString());
                        writeVarInt(out, payment.count());
                        writeVarInt(out, payment.damage());
                        writeOptionalString(out, payment.displayName());
                    }
                }
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Beacon save failed for " + file, e);
        }
    }

    /** Loads the beacon map (unresolvable payment items drop with a warning). */
    public Map<BlockPosition, BeaconBlockEntity> load() {
        if (!Files.exists(file)) {
            return Map.of();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1 || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZBT file");
            }
            int version = in.readByte();
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported ZBT version: " + version);
            }
            int count = readVarInt(in);
            Map<BlockPosition, BeaconBlockEntity> loaded = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                BeaconBlockEntity beacon = new BeaconBlockEntity();
                beacon.restoreLevels(readVarInt(in));
                beacon.restorePrimary(readVarInt(in));
                beacon.restoreSecondary(readVarInt(in));
                if (in.readUnsignedByte() == 1) {
                    Identifier identifier = Identifier.parse(readString(in));
                    int stackCount = readVarInt(in);
                    int damage = readVarInt(in);
                    String displayName = readOptionalString(in);
                    Optional<net.zaminmc.torch.item.ItemType> type =
                            BuiltinItems.lookup(identifier);
                    if (type.isPresent() && BeaconBlockEntity.isPaymentItem(type.get())) {
                        beacon.setPayment(ItemStack.of(type.get(), stackCount)
                                .withDamage(damage).withName(displayName));
                    } else {
                        LOGGER.warning("Saved beacon payment no longer registered, dropped: "
                                + identifier);
                    }
                }
                loaded.put(new BlockPosition(x, y, z), beacon);
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt");
            try {
                Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warning("Beacon data file was unreadable; preserved as " + quarantine
                        + " (" + corrupt.getMessage() + ")");
            } catch (IOException moveFailure) {
                LOGGER.warning("Beacon data file was unreadable and could not be preserved: "
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
                throw new IOException("VarInt too large in beacon data file");
            }
        }
    }
}
