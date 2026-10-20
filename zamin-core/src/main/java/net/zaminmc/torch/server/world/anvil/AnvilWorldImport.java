package net.zaminmc.torch.server.world.anvil;

import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.nbt.BinaryNbtReader;
import net.zaminmc.torch.server.world.EngineWorld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The import session (the Anvil canvas' orchestrator): one explicit
 * operator walk over an original 1.8.8 world folder — {@code level.dat}
 * plus {@code region/*.mca} — into the live engine world. Read-only
 * against the SOURCE (the folder is never written); the TARGET is the
 * engine's own publication path: every chunk goes through
 * {@code EngineWorld.installGenerated}, the same route pregenerated
 * chunks take, so the light hook computes each chunk's initial light
 * exactly like a generated one and the deltas overlay stays untouched.
 *
 * <p><b>THE I/O DISCIPLINE (§31.6, the /save family's own pattern):</b>
 * in the live command's shape the file reads ride the blocking-I/O lane
 * and everything that touches world state (the NBT parse is pure, the
 * install is authoritative) runs on the owning lane inside the lane's
 * completion drain — never a simulation worker for file I/O, never the
 * I/O lane for a world write. The synchronous {@link #run} form (every
 * read on the CALLING thread, which is the owner in the tests) exists
 * for the test harness and shares the same parse/install walk.</p>
 *
 * <p><b>THE PUBLICATION RULE:</b> a chunk position the live world
 * already holds keeps the live state — {@code installGenerated}'s own
 * contract (the newer authoritative state wins; the import's detached
 * result is discarded, never installed over it). The skipped chunks are
 * COUNTED, never silent; a clean import wants a fresh world whose boot
 * pregen did not touch the imported area.</p>
 *
 * <p><b>THE NAMING (the reference's own):</b> region files are
 * {@code r.<regionX>.<regionZ>.mca} ({@code RegionIo.getRegionFile}'s
 * {@code "r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca"}), each
 * covering 32x32 chunks whose absolute position is
 * {@code regionX * 32 + localX} (the reference's chunk-locals mask).
 * Anything else in the region directory is counted and skipped by name.</p>
 */
public final class AnvilWorldImport {

    private static final Logger LOGGER = Logger.getLogger(AnvilWorldImport.class.getName());

    /** The reference's own region file name (RegionIo.getRegionFile). */
    static final Pattern REGION_FILE_NAME =
            Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private final EngineWorld world;
    private final AnvilChunkReader chunkReader;
    private final AnvilImportLedger ledger = new AnvilImportLedger();

    private AnvilWorldImport(EngineWorld world, AnvilChunkReader chunkReader) {
        this.world = world;
        this.chunkReader = chunkReader;
    }

    /** One session over the world (the chunk reader over the world's registry). */
    public static AnvilWorldImport over(EngineWorld world) {
        return new AnvilWorldImport(world, new AnvilChunkReader(world.blockRegistry()));
    }

    /** The session's isolation ledger (the operator-facing report's source). */
    public AnvilImportLedger ledger() {
        return ledger;
    }

    // ------------------------------------------------------------------ the synchronous walk

    /**
     * The whole import on the calling thread (the owning lane — every
     * world mutation asserts through the world's own ownership checks;
     * the file reads happen here too, the shape the tests drive).
     *
     * @return the ledger's summary (also available through {@link #ledger})
     */
    public String run(Path worldFolder) throws IOException {
        Path regionDir = worldFolder.resolve("region");
        if (!Files.isDirectory(regionDir)) {
            throw new IOException("The world folder has no region/ directory: " + worldFolder);
        }
        // level.dat first (the reference's own order: the world rows apply
        // before the chunks walk; a missing file is a loud refusal, never
        // a default world).
        Path levelDat = worldFolder.resolve("level.dat");
        if (Files.exists(levelDat)) {
            applyLevelData(AnvilLevelData.read(levelDat));
        } else {
            throw new IOException("The world folder has no level.dat: " + worldFolder);
        }
        for (Path regionFile : regionFiles(regionDir)) {
            importRegionFile(regionFile);
        }
        return ledger.summary();
    }

    /** The region directory's r.X.Z.mca files, sorted for determinism. */
    public static List<Path> regionFiles(Path regionDir) throws IOException {
        List<Path> out = new ArrayList<>();
        for (Path entry : Files.newDirectoryStream(regionDir)) {
            if (!Files.isRegularFile(entry)) {
                continue;
            }
            if (REGION_FILE_NAME.matcher(entry.getFileName().toString()).matches()) {
                out.add(entry);
            }
        }
        out.sort(Comparator.comparing(Path::toString));
        return out;
    }

    /** One region file's whole walk (the owning lane; the file already read). */
    public void importRegionFile(Path regionFile) throws IOException {
        importRegionBytes(regionFile, java.nio.file.Files.readAllBytes(regionFile));
    }

    /**
     * One region file's walk over already-read bytes (the live command's
     * I/O-lane shape: the bytes came off the blocking-I/O lane, the parse
     * and the installs run on the owning lane here).
     */
    public void importRegionBytes(Path regionFile, byte[] regionBytes) {
        AnvilRegionFile region;
        try {
            region = AnvilRegionFile.of(regionFile, regionBytes);
        } catch (IOException e) {
            ledger.regionFileIsolated(e.getMessage());
            LOGGER.warning(() -> "Anvil import: " + e.getMessage());
            return;
        }
        ledger.regionFileOpened();
        Matcher name = REGION_FILE_NAME.matcher(regionFile.getFileName().toString());
        if (!name.matches()) {
            ledger.regionFileIsolated("unrecognized region file name: " + regionFile);
            return;
        }
        int regionX = Integer.parseInt(name.group(1));
        int regionZ = Integer.parseInt(name.group(2));
        for (int localZ = 0; localZ < AnvilRegionFile.REGION_CHUNKS; localZ++) {
            for (int localX = 0; localX < AnvilRegionFile.REGION_CHUNKS; localX++) {
                importEntry(region, regionX, regionZ, localX, localZ);
            }
        }
    }

    /** One region entry (the owning lane). */
    private void importEntry(AnvilRegionFile region, int regionX, int regionZ, int localX, int localZ) {
        if (!region.isOccupied(localX, localZ)) {
            ledger.entryUnoccupied();
            return;
        }
        int chunkX = regionX * AnvilRegionFile.REGION_CHUNKS + localX;
        int chunkZ = regionZ * AnvilRegionFile.REGION_CHUNKS + localZ;
        AnvilRegionFile.ChunkRead read = region.readChunk(localX, localZ);
        if (read instanceof AnvilRegionFile.Isolated isolated) {
            if (isolated.reason().startsWith("unoccupied")) {
                ledger.entryUnoccupied();
            } else {
                ledger.isolated(isolated.reason(),
                        "chunk " + chunkX + "," + chunkZ + " in " + region.file().getFileName());
            }
            return;
        }
        byte[] nbtBytes = ((AnvilRegionFile.ChunkPayload) read).nbtBytes();
        Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> chunkNbt;
        try {
            chunkNbt = BinaryNbtReader.read(
                    new java.io.DataInputStream(new java.io.ByteArrayInputStream(nbtBytes)));
        } catch (IOException e) {
            ledger.isolated("corrupt chunk NBT: " + e.getMessage(),
                    "chunk " + chunkX + "," + chunkZ + " in " + region.file().getFileName());
            return;
        }
        AnvilChunkReader.ReadResult result =
                chunkReader.read(new ChunkPosition(chunkX, chunkZ), chunkNbt, ledger);
        if (result instanceof AnvilChunkReader.Rejected rejected) {
            // The reader already counted the rejection (its own gates).
            return;
        }
        net.zaminmc.torch.server.world.EngineChunk imported =
                ((AnvilChunkReader.ReadChunk) result).chunk();
        // The publication rule: a position the live world already holds
        // keeps its state (installGenerated's own contract); the import's
        // detached chunk is counted and discarded, never forced in.
        if (world.peek(imported.position()) != null) {
            ledger.chunkSkippedAlreadyLoaded();
            return;
        }
        world.installGenerated(imported.position(), imported);
        ledger.chunkInstalled();
    }

    // ------------------------------------------------------------------ the level rows

    /**
     * Applies the level rows the engine's configuration surface holds and
     * records the rest (the used-vs-ignored ledger): the spawn position
     * and the day clock are applied (the engine's own /setworldspawn and
     * /time surfaces); the seed and the total-tick counter are recorded
     * in the report (no runtime reseed surface); every other present row
     * is named in the ignored report.
     */
    public void applyLevelData(AnvilLevelData level) {
        world.setSpawnPosition(new net.zaminmc.torch.util.Position(
                level.spawnX() + 0.5, level.spawnY(), level.spawnZ() + 0.5));
        world.setTimeOfDay(level.dayTime());
        LOGGER.info(() -> "Anvil import: level.dat '" + level.levelName()
                + "' seed " + level.randomSeed() + " (recorded, not applied — no runtime reseed"
                + " surface), Time " + level.time() + " (recorded), DayTime " + level.dayTime()
                + " (applied); ignored rows: "
                + (level.ignoredReport().isEmpty() ? "(none)" : level.ignoredReport()));
    }
}
