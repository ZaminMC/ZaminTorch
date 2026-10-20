package net.zaminmc.torch.server.world.anvil;

import net.zaminmc.torch.server.nbt.BinaryNbtReader;
import net.zaminmc.torch.server.nbt.NbtOps;
import net.zaminmc.torch.server.nbt.Snbt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The level.dat reader (the Anvil canvas' level leg — the reference's
 * {@code AlphaWorldStorageSource.getData} walk over the gzip root, then
 * {@code WorldData}'s own row reads): the whole file is one gzip NBT
 * document whose root compound holds the {@code Data} compound with the
 * world's rows — {@code RandomSeed}, {@code SpawnX/Y/Z}, {@code Time},
 * {@code DayTime}, {@code LevelName}, the generator rows, and the rest of
 * the 1.8.8 surface.
 *
 * <p><b>The used-vs-ignored ledger (the task's own requirement):</b> the
 * import applies what the engine's existing configuration surface can
 * hold and RECORDS everything else — the reader's report row names every
 * row it found and which side of the ledger it fell on:</p>
 * <ul>
 *   <li><b>Used:</b> {@code SpawnX/Y/Z} — the engine's own spawn surface
 *       ({@code setSpawnPosition}, the /setworldspawn path); {@code
 *       DayTime} — the day clock ({@code setTimeOfDay}, the /time path).</li>
 *   <li><b>Recorded, not applied:</b> {@code RandomSeed} — the engine's
 *       generators fix their seed at construction (derived from the world
 *       name) and the runtime surface has no reseed; the import's report
 *       shows the seed so the operator can align a fresh boot with it.
 *       {@code Time} (the total-tick counter) — the engine's runtime time
 *       surface is the day clock; the counter's own setter exists only on
 *       the boot restore path, deliberately untouched by the import.</li>
 *   <li><b>Ignored (each presence counted in the report):</b> the
 *       generator rows ({@code generatorName/generatorOptions} — the
 *       engine's generator is configured at boot), {@code GameType},
 *       {@code version}, {@code LastPlayed}, {@code SizeOnDisk}, the
 *       weather rows, the difficulty/border rows, the {@code Player} tag,
 *       and {@code GameRules} — every one a surface the engine configures
 *       elsewhere; none is silently substituted.</li>
 * </ul>
 *
 * <p>A missing or unreadable level.dat is an IOException the caller
 * isolates with the path — §31.6: never a silent default world.</p>
 */
public final class AnvilLevelData {

    private final Path file;
    private final long randomSeed;
    private final int spawnX;
    private final int spawnY;
    private final int spawnZ;
    private final long time;
    private final long dayTime;
    private final String levelName;
    /** The ignored rows' presence report (the used-vs-ignored ledger). */
    private final String ignoredReport;

    private AnvilLevelData(Path file, long randomSeed, int spawnX, int spawnY, int spawnZ,
                           long time, long dayTime, String levelName, String ignoredReport) {
        this.file = file;
        this.randomSeed = randomSeed;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.time = time;
        this.dayTime = dayTime;
        this.levelName = levelName;
        this.ignoredReport = ignoredReport;
    }

    /**
     * Reads one level.dat (the reference's getData: the gzip root, the
     * Data compound, WorldData's row reads).
     *
     * @throws IOException the file is missing, unreadable, or not a
     *                     named-compound gzip document with a Data row
     */
    public static AnvilLevelData read(Path levelDat) throws IOException {
        try (InputStream in = Files.newInputStream(levelDat)) {
            return readBytes(levelDat, in.readAllBytes());
        }
    }

    /**
     * Reads the level rows from already-read bytes (the live command's
     * I/O-lane shape: the file was read on the blocking-I/O lane, the
     * parse runs on the owning lane).
     *
     * @throws IOException the bytes are not a gzip named-compound document
     *                     with a Data row
     */
    public static AnvilLevelData readBytes(Path levelDat, byte[] bytes) throws IOException {
        Map<String, Snbt.Tag> root;
        try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
            root = BinaryNbtReader.readGzip(in);
        }
        if (!NbtOps.contains(root, "Data", Snbt.Tag.Type.COMPOUND)) {
            throw new IOException("level.dat has no Data compound: " + levelDat);
        }
        Map<String, Snbt.Tag> data = NbtOps.getCompound(root, "Data");
        // WorldData's own reads (the rows the engine's surface uses).
        long randomSeed = NbtOps.getLong(data, "RandomSeed");
        int spawnX = NbtOps.getInt(data, "SpawnX");
        int spawnY = NbtOps.getInt(data, "SpawnY");
        int spawnZ = NbtOps.getInt(data, "SpawnZ");
        long time = NbtOps.getLong(data, "Time");
        long dayTime = NbtOps.containsNumeric(data, "DayTime")
                ? NbtOps.getLong(data, "DayTime") : time;
        String levelName = NbtOps.getString(data, "LevelName");
        return new AnvilLevelData(levelDat, randomSeed, spawnX, spawnY, spawnZ,
                time, dayTime, levelName, ignoredRows(data));
    }

    /** The ignored rows' presence report (each named once). */
    private static String ignoredRows(Map<String, Snbt.Tag> data) {
        StringBuilder out = new StringBuilder();
        for (String row : new String[] {
                "generatorName", "generatorVersion", "generatorOptions",
                "GameType", "version", "LastPlayed", "SizeOnDisk",
                "clearWeatherTime", "rainTime", "raining",
                "thunderTime", "thundering", "hardcore", "allowCommands",
                "initialized", "Difficulty", "DifficultyLocked",
                "BorderCenterX", "BorderCenterZ", "BorderSize",
                "BorderSafeZone", "BorderDamagePerBlock",
                "BorderWarningBlocks", "BorderWarningTime", "GameRules",
                "MapFeatures"}) {
            if (data.containsKey(row)) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(row);
            }
        }
        if (data.containsKey("Player")) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append("Player");
        }
        return out.toString();
    }

    /** The level.dat's own path (the reports' subject). */
    public Path file() {
        return file;
    }

    /** The world's RandomSeed (recorded; the engine's runtime surface has no reseed). */
    public long randomSeed() {
        return randomSeed;
    }

    public int spawnX() {
        return spawnX;
    }

    public int spawnY() {
        return spawnY;
    }

    public int spawnZ() {
        return spawnZ;
    }

    /** The total-tick counter (recorded; the day clock is the applied row). */
    public long time() {
        return time;
    }

    /** The day clock (the applied row — the engine's setTimeOfDay surface). */
    public long dayTime() {
        return dayTime;
    }

    public String levelName() {
        return levelName;
    }

    /** The ignored rows' names, comma-separated (empty when none were present). */
    public String ignoredReport() {
        return ignoredReport;
    }
}
