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
     * the ticker).
     */
    public void save(java.util.List<Map.Entry<String, String>> entries) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                out.writeShort(entries.size());
                for (Map.Entry<String, String> entry : entries) {
                    out.writeUTF(entry.getKey());
                    out.writeUTF(entry.getValue());
                }
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
