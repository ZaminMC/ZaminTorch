package net.zaminmc.torch.server.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

/**
 * The world seed store (T-SEED — the terrain family audit's first fix): the
 * reference's own seed law, persisted per world.
 *
 * <p><b>THE REFERENCE LAW</b> ({@code DedicatedServer} lines 170-183): a
 * fresh world rolls {@code new Random().nextLong()}; a non-empty
 * {@code level-seed} property overrides it — {@code Long.parseLong} kept
 * when non-zero, else the string's {@code hashCode()}. The chosen seed
 * persists in the level data (the reference's level.dat); every later boot
 * of the same world reuses it. The engine's persistence model (the
 * deterministic terrain rebuild + the delta overlay) needs exactly that
 * stability, so the store keeps the same contract: the record
 * ({@code world/data/seed.json}, the spawn.json pattern) is authoritative
 * once written.</p>
 *
 * <p><b>THE MIGRATION</b>: worlds created before this fix carried the
 * name-derived seed (the historical law {@code 0x5A4D494E5C00L ^
 * name.hashCode() * 0x9E3779B97F4L}) — their saved deltas, slime chunks,
 * and portal facts all ride that seed. A world with engine-touched state
 * (a storage manifest or a delta file) and no seed record adopts the
 * HISTORICAL name law and persists it, keeping its continuity. Only a
 * genuinely fresh world (no stored state of any kind) takes the
 * reference's random/level-seed derivation.</p>
 */
public final class WorldSeedStore {

    /** The persisted seed record ({@code world/data/seed.json}). */
    public static final String FILE_NAME = "seed.json";
    /** The delta overlay's file name (the migration freshness consult). */
    private static final String DELTA_FILE_NAME = "zamin-delta.bin";
    /** The storage manifest's file name (the migration freshness consult). */
    private static final String MANIFEST_FILE_NAME = StorageManifest.FILE_NAME;

    private WorldSeedStore() {
    }

    /**
     * The boot-time resolution: the stored record first, then the
     * migration law, then the reference's fresh-world derivation — the
     * chosen seed persisted before it answers.
     *
     * @param worldDataDir the world's data directory ({@code world/data})
     * @param worldName    the level name (the migration law's input)
     * @param levelSeed    the {@code level-seed} property ("" = absent)
     */
    public static long resolve(Path worldDataDir, String worldName, String levelSeed) {
        Path record = worldDataDir.resolve(FILE_NAME);
        Long stored = read(record);
        if (stored != null) {
            return stored;
        }
        long seed;
        if (isPreFixWorld(worldDataDir)) {
            // The migration: a name-seeded world keeps its continuity.
            seed = historicalNameSeed(worldName);
        } else {
            seed = derive(levelSeed);
        }
        write(record, seed);
        return seed;
    }

    /**
     * The reference's derivation ({@code DedicatedServer} lines 170-183,
     * 1:1): the random roll first; the property override second —
     * {@code parseLong} kept when non-zero (the reference's own "0 keeps
     * the random seed" quirk), else the string's {@code hashCode()}.
     */
    public static long derive(String levelSeed) {
        long seed = new Random().nextLong();
        if (levelSeed != null && !levelSeed.isEmpty()) {
            try {
                long parsed = Long.parseLong(levelSeed);
                if (parsed != 0L) {
                    seed = parsed;
                }
            } catch (NumberFormatException e) {
                seed = levelSeed.hashCode();
            }
        }
        return seed;
    }

    /**
     * The historical name law (the pre-fix engine's only seed): kept for
     * the migration path — a world whose state predates the store.
     */
    public static long historicalNameSeed(String worldName) {
        return 0x5A4D494E5C00L ^ (long) worldName.hashCode() * 0x9E3779B97F4L;
    }

    /**
     * The freshness consult: a world with ANY engine-touched state and no
     * seed record is a pre-fix world (the store must adopt its historical
     * seed, never re-roll it — the delta overlay's alignment is the seed).
     */
    private static boolean isPreFixWorld(Path worldDataDir) {
        return Files.exists(worldDataDir.resolve(MANIFEST_FILE_NAME))
                || Files.exists(worldDataDir.resolve(DELTA_FILE_NAME));
    }

    /** The stored record read ({@code null} when absent or unreadable). */
    private static Long read(Path record) {
        if (!Files.exists(record)) {
            return null;
        }
        try {
            String json = Files.readString(record, StandardCharsets.UTF_8);
            int i = json.indexOf("\"seed\"");
            if (i < 0) {
                return null;
            }
            int from = json.indexOf(':', i);
            if (from < 0) {
                return null;
            }
            int end = json.indexOf(',', from);
            if (end < 0) {
                end = json.indexOf('}', from);
            }
            if (end < 0) {
                return null;
            }
            return Long.parseLong(json.substring(from + 1, end).trim());
        } catch (Exception e) {
            return null; // an unreadable record re-derives (the boot survives)
        }
    }

    /** The record write (the spawn.json persistence pattern). */
    private static void write(Path record, long seed) {
        try {
            Files.createDirectories(record.getParent());
            Files.writeString(record,
                    String.format(Locale.ROOT, "{\"seed\": %d}%n", seed),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            // The boot continues: an unpersisted seed loses only the
            // cross-reboot stability (the same failure the spawn anchor
            // tolerates). The resolution logs nothing further — the boot's
            // own world-ready line carries the seed.
        }
    }
}
