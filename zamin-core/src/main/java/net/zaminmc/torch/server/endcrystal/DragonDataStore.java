package net.zaminmc.torch.server.endcrystal;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * The ender-dragon fight's one-bit persistence ("ZDR" format, version 1):
 * whether the dragon was already defeated. The End's chunks regenerate
 * deterministically on every boot, so the chunk-(0,0) decorate would
 * re-spawn the dragon each restart — vanilla's own entity ledger keeps the
 * kill; this store carries the same verdict. (A RESPAWN of the dragon —
 * the four end-crystal fountain ritual — is its own future slice; the
 * store grows its arm then.)
 *
 * <p>Writes are atomic (temp file then atomic move), corrupt files load as
 * the fresh {@code false} and are preserved beside the storage path. The
 * save point is the dragon-death event's own write (the flag transitions
 * exactly once and the event persists it immediately — the deterministic
 * End regenerate would re-spawn the dragon every boot otherwise; the store
 * is NOT part of the save walk: no state can change between event writes).</p>
 */
public final class DragonDataStore {

    private static final Logger LOGGER = Logger.getLogger(DragonDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'D';
    private static final int MAGIC_2 = 'R';
    private static final int FORMAT_VERSION = 1;

    private final Path file;

    public DragonDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /** Saves the defeated flag (the engine routes this on the tick lane). */
    public void save(boolean defeated) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(
                    Files.newOutputStream(temp))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                out.writeBoolean(defeated);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.severe(() -> "Failed to save the dragon state to " + file + ": " + e);
        }
    }

    /** Loads the defeated flag (false when absent or corrupt). */
    public boolean load() {
        if (!Files.exists(file)) {
            return false;
        }
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2 || in.readByte() != FORMAT_VERSION) {
                LOGGER.warning(() -> "Corrupt dragon store " + file + " — loading false");
                return false;
            }
            return in.readBoolean();
        } catch (IOException e) {
            LOGGER.warning(() -> "Unreadable dragon store " + file
                    + " (" + e + ") — loading false");
            return false;
        }
    }
}
