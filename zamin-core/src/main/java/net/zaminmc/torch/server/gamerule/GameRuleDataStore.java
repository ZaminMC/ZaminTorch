package net.zaminmc.torch.server.gamerule;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * File-backed game-rule state ("ZGR" format, version 1), one file per
 * server — Slice 24a, the reference's own {@code WorldData} save field
 * ({@code nbt.put("GameRules", ...)} — one table for the whole server,
 * the dimensions' derived data delegating to the overworld's).
 *
 * <p>Layout: magic 'Z','G','R',1 - u16 count - per entry
 * (UTF name, UTF value) in the table's sorted order. The same
 * durability rules as the other stores: writes are atomic (temp file
 * then atomic move), corrupt files load as absent and are preserved
 * beside the storage path instead of being destroyed, and save
 * failures are loud. Save points are the engine's {@code saveAllNow}
 * (shutdown, console save).</p>
 */
public final class GameRuleDataStore {

    private static final Logger LOGGER = Logger.getLogger(GameRuleDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'G';
    private static final int MAGIC_2 = 'R';
    private static final int FORMAT_VERSION = 1;

    private final Path file;

    public GameRuleDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the table's (name, value) pairs. Callers snapshot state on
     * the tick thread before calling (the engine routes this through
     * the ticker). The synchronous form composes the S5 pipeline's own
     * two halves — the pure serialization and the file publication — so
     * the sync save's bytes and the pipeline's bytes are identical by
     * construction (one serializer, one file half).
     */
    public void save(java.util.List<Map.Entry<String, String>> entries) {
        writeSerialized(serialize(entries));
    }

    /**
     * The pure serialization (the compute pool's own half, Slice S5):
     * the immutable snapshot IN (the entries list is the fresh list of
     * immutable {@code Map.entry} pairs the GameRules' own snapshot
     * builds), the ZGR bytes OUT — no world read, no I/O. The byte walk
     * is the historical save walk's own (version 1, byte-identical).
     */
    public byte[] serialize(java.util.List<Map.Entry<String, String>> entries) {
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(sink))) {
            out.writeByte(MAGIC_0);
            out.writeByte(MAGIC_1);
            out.writeByte(MAGIC_2);
            out.writeByte(FORMAT_VERSION);
            out.writeShort(entries.size());
            for (Map.Entry<String, String> entry : entries) {
                out.writeUTF(entry.getKey());
                out.writeUTF(entry.getValue());
            }
        } catch (IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud.
            throw new java.io.UncheckedIOException("Game-rule serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZGR bytes: the temp-file write + the
     * atomic move — the historical save walk's own file half with the
     * store's own failure contract 1:1 (the catch-all severe log, never
     * a throw). Safe from any thread that holds the publication right
     * (the save pipeline's per-storage lock orders the async
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
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOGGER.severe("Game-rule save failed: " + e.getMessage());
        }
    }

    /**
     * Loads the persisted pairs; a corrupt or missing file reads as null
     * (a fresh world keeps the reference's constructor defaults; a
     * corrupt file is preserved beside the storage path for manual
     * recovery, the established store behavior).
     */
    public Set<Map.Entry<String, String>> load() {
        if (!Files.exists(file)) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2 || in.readByte() != FORMAT_VERSION) {
                return corrupt("bad magic/version");
            }
            int count = in.readUnsignedShort();
            Set<Map.Entry<String, String>> entries = new LinkedHashSet<>(count * 2);
            for (int i = 0; i < count; i++) {
                String name = in.readUTF();
                String value = in.readUTF();
                entries.add(Map.entry(name, value));
            }
            return entries;
        } catch (IOException e) {
            return corrupt(e.toString());
        }
    }

    /** Preserves the corrupt file beside the storage path and reads as absent. */
    private Set<Map.Entry<String, String>> corrupt(String why) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // best-effort preservation
        }
        LOGGER.warning("Game-rule store corrupt (" + why + "): " + file
                + " — preserved as .corrupt, loading defaults");
        return null;
    }
}
