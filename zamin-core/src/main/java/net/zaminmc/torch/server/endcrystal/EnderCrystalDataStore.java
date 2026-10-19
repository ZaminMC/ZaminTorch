package net.zaminmc.torch.server.endcrystal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * File-backed ender-crystal state ("ZEC" format, version 1) — the armor
 * stand store's own durability rules.
 *
 * <p>Layout: magic 'Z','E','C',1 - destroyed cells (varint count; each:
 * x i32, y i32, z i32). The destroyed-set law (Slice 62): the End's
 * crystals rebuild deterministically from the generator's decorate on
 * every boot (the reference's own empty writeCustomNbt saves nothing to
 * keep), so the ONLY state that must survive a restart is which pillars
 * already lost theirs — a popped crystal never comes back.</p>
 *
 * <p>Writes are atomic (temp file then atomic move), corrupt files load
 * as absent and are preserved beside the storage path instead of being
 * destroyed, and save failures are loud. Save points are the engine's
 * {@code saveAllNow} (shutdown, console save).</p>
 */
public final class EnderCrystalDataStore {

    private static final Logger LOGGER = Logger.getLogger(EnderCrystalDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'E';
    private static final int MAGIC_2 = 'C';
    private static final int FORMAT_VERSION = 1;

    private final Path file;

    public EnderCrystalDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the destroyed birth cells. Callers snapshot state on the tick
     * thread before calling (the engine routes this through the ticker).
     * The synchronous form composes the S5 pipeline's own two halves —
     * the pure serialization and the file publication — so the sync
     * save's bytes and the pipeline's bytes are identical by
     * construction (one serializer, one file half).
     */
    public void save(Set<Long> cells) {
        writeSerialized(serialize(cells));
    }

    /**
     * The pure serialization (the compute pool's own half, Slice S5):
     * the immutable snapshot IN (the destroyed-cells set is the fresh
     * {@code Long} set the system's own snapshot walk builds), the ZEC
     * bytes OUT — no world read, no I/O. The byte walk is the historical
     * save walk's own (version 1, byte-identical).
     */
    public byte[] serialize(Set<Long> cells) {
        Objects.requireNonNull(cells, "cells");
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(sink))) {
            out.writeByte(MAGIC_0);
            out.writeByte(MAGIC_1);
            out.writeByte(MAGIC_2);
            out.writeByte(FORMAT_VERSION);
            writeVarInt(out, cells.size());
            for (long cell : cells) {
                out.writeLong(cell);
            }
        } catch (IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud.
            throw new java.io.UncheckedIOException("Ender-crystal serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZEC bytes: the temp-file write + the
     * atomic move (the non-atomic fallback preserved) — the historical
     * save walk's own file half with the store's own failure contract
     * 1:1 (the severe log, never a throw). Safe from any thread that
     * holds the publication right (the save pipeline's per-storage lock
     * orders the async publications; the synchronous save is
     * uncontended).
     */
    public void writeSerialized(byte[] data) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.write(data);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.severe(() -> "Failed to save ender crystals to " + file + ": " + e);
        }
    }

    /**
     * Loads the destroyed cells; an absent or corrupt file answers an empty
     * set (a corrupt file is preserved beside the storage path).
     */
    public Set<Long> load() {
        if (!Files.exists(file)) {
            return new HashSet<>();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2 || in.readByte() != FORMAT_VERSION) {
                LOGGER.warning(() -> "Corrupt ender-crystal store " + file
                        + " — loading none");
                return new HashSet<>();
            }
            int count = readVarInt(in);
            Set<Long> cells = new HashSet<>(Math.max(0, count));
            for (int i = 0; i < count; i++) {
                cells.add(in.readLong());
            }
            return cells;
        } catch (IOException e) {
            LOGGER.warning(() -> "Unreadable ender-crystal store " + file
                    + " (" + e + ") — loading none");
            return new HashSet<>();
        }
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
        int position = 0;
        while (true) {
            byte current = in.readByte();
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) {
                return value;
            }
            position += 7;
            if (position >= 32) {
                throw new IOException("VarInt too big");
            }
        }
    }
}
