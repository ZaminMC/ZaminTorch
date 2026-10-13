package net.zaminmc.torch.server.weather;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * File-backed weather state ("ZWD" format, version 1), one file per world —
 * Slice 21b, the reference's own {@code WorldData} save fields.
 *
 * <p>Layout: magic 'Z','W','D',1 - clearWeatherTime(i32) - raining(u8) -
 * rainTime(i32) - thundering(u8) - thunderTime(i32). The gradients are NOT
 * persisted ({@code WorldData} carries only the five flags/clocks — the
 * gradients walk from 0 on load exactly like the reference's fresh
 * {@code World.rain} fields). The same durability rules as the other
 * stores: writes are atomic (temp file then atomic move), corrupt files
 * load as absent and are preserved beside the storage path instead of being
 * destroyed, and save failures are loud. Save points are the engine's
 * {@code saveAllNow} (shutdown, console save).</p>
 */
public final class WeatherDataStore {

    private static final Logger LOGGER = Logger.getLogger(WeatherDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'W';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 1;

    /** The persisted weather state (WorldData's own five fields). */
    public record WeatherState(int clearWeatherTime, boolean raining, int rainTime,
                               boolean thundering, int thunderTime) {
    }

    private final Path file;

    public WeatherDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the five fields. Callers snapshot state on the tick thread
     * before calling (the engine routes this through the ticker).
     */
    public void save(WeatherState state) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                out.writeInt(state.clearWeatherTime());
                out.writeBoolean(state.raining());
                out.writeInt(state.rainTime());
                out.writeBoolean(state.thundering());
                out.writeInt(state.thunderTime());
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOGGER.severe("Weather save failed: " + e.getMessage());
        }
    }

    /**
     * Loads the five fields; a corrupt or missing file reads as null (a
     * fresh world keeps the WorldData defaults — clear, the first tick's
     * reroll band draws the clocks; a corrupt file is preserved beside the
     * storage path for manual recovery, the established store behavior).
     */
    public WeatherState load() {
        if (!Files.exists(file)) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2 || in.readByte() != FORMAT_VERSION) {
                throw new IOException("Not a ZWD v1 file");
            }
            return new WeatherState(in.readInt(), in.readBoolean(), in.readInt(),
                    in.readBoolean(), in.readInt());
        } catch (Exception e) {
            preserveCorrupt(e);
            return null;
        }
    }

    private void preserveCorrupt(Exception cause) {
        LOGGER.severe("Weather store corrupt, loading defaults: " + cause.getMessage());
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // the copy is best-effort; the load already failed fresh
        }
    }
}
