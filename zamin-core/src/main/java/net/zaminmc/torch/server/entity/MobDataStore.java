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
    private static final int FORMAT_VERSION = 3;

    /** The v2 store's own version (Slice 25c's shape — the load keeps reading it). */
    private static final int FORMAT_VERSION_2 = 2;
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
     */
    public void save(List<MobManager.MobSnapshot> mobs) {
        Objects.requireNonNull(mobs, "mobs");
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
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
                }
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
            if (version != FORMAT_VERSION && version != FORMAT_VERSION_2 && version != 1) {
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
                MobManager.MobNbtExtras nbt = version >= FORMAT_VERSION
                        ? readNbtExtras(in) : null;
                loaded.add(new MobManager.MobSnapshot(type, x, y, z, yaw, health, extras, nbt));
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

    /** Writes the v3 NBT block (a zero byte answers the null shape). */
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
        out.writeByte(flags);
        if ((flags & NBT_FLAG_CUSTOM_NAME) != 0) {
            writeString(out, nbt.customName());
        }
    }

    /** Reads the v3 NBT block back (null for the zero-byte shape). */
    private static MobManager.MobNbtExtras readNbtExtras(DataInputStream in)
            throws IOException {
        int flags = in.readUnsignedByte();
        if (flags == 0) {
            return null;
        }
        String customName = null;
        if ((flags & NBT_FLAG_CUSTOM_NAME) != 0) {
            customName = readString(in);
        }
        return new MobManager.MobNbtExtras(customName,
                (flags & NBT_FLAG_CUSTOM_NAME_VISIBLE) != 0,
                (flags & NBT_FLAG_NO_AI) != 0,
                (flags & NBT_FLAG_INVULNERABLE) != 0,
                (flags & NBT_FLAG_PERSISTENT) != 0);
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
