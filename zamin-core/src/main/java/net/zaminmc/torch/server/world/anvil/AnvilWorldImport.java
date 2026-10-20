package net.zaminmc.torch.server.world.anvil;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.nbt.BinaryNbtReader;
import net.zaminmc.torch.server.nbt.EntityNbt;
import net.zaminmc.torch.server.nbt.ItemNbt;
import net.zaminmc.torch.server.nbt.NbtOps;
import net.zaminmc.torch.server.nbt.Snbt;
import net.zaminmc.torch.server.nbt.TileNbt;
import net.zaminmc.torch.server.potion.PotionSystem;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Position;

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
 * <p><b>THE CONTENT SEAMS (the entities/tiles follow-up — the reference's
 * own load order):</b> after a chunk installs, its carried
 * {@code TileEntities} rows apply through the engine's own per-family
 * read walk ({@code TileNbt.readTile} — the /blockdata machinery, every
 * family's 1:1 projection), then its {@code Entities} rows import
 * through the /summon machinery ({@code MobManager.spawnAt} +
 * {@code EntityNbt.readNbt} for the living mobs — Pos/Rotation/Motion/
 * UUID/custom rows all land — and {@code ItemEntityManager.spawnRestored}
 * + the {@code Item}/{@code Age}/{@code PickupDelay}/{@code Thrower}/
 * {@code Owner} rows for the dropped items). Everything else the
 * reference can save — vehicles, projectiles, paintings, item frames,
 * XP orbs, riding chains — has no engine surface yet and isolates LOUDLY
 * with its exact id (§31.6's counted refusal, never a silent drop). A
 * skipped (already-loaded) chunk's content rows ride the skip whole,
 * with a counted note naming what did not apply.</p>
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
    /** The content seams (null = the blocks-only session — the content rows count as not-imported). */
    private final ContentSeams content;

    private AnvilWorldImport(EngineWorld world, AnvilChunkReader chunkReader, ContentSeams content) {
        this.world = world;
        this.chunkReader = chunkReader;
        this.content = content;
    }

    /**
     * The content seams: the engine's own tile + entity surfaces. The
     * seams are the SAME machinery the live commands use — readTile is
     * the /blockdata walk, spawnAt + EntityNbt.readNbt is the /summon
     * walk — so an imported chest/mob/spawner is indistinguishable from
     * an engine-made one (the wire, the persistence, the ticking all
     * ride the existing tested paths).
     *
     * @param tiles the per-family tile read walk (the /blockdata machinery)
     * @param mobs the living-mob spawn (the /summon machinery's first half)
     * @param items the exact-position item spawn (the restored form)
     * @param potions the entity NBT walk's effect consult
     * @param equipmentChanged the per-mob equipment fan-out (the spawn
     *                         walk's second half)
     */
    public record ContentSeams(TileNbt tiles, MobManager mobs, ItemEntityManager items,
                               PotionSystem potions,
                               java.util.function.Consumer<MobEntity> equipmentChanged) {
    }

    /** One session over the world (the chunk reader over the world's registry). */
    public static AnvilWorldImport over(EngineWorld world) {
        return new AnvilWorldImport(world, new AnvilChunkReader(world.blockRegistry()), null);
    }

    /** One session over the world WITH the content seams (the live command's form). */
    public static AnvilWorldImport over(EngineWorld world, ContentSeams content) {
        return new AnvilWorldImport(world, new AnvilChunkReader(world.blockRegistry()), content);
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
        AnvilChunkReader.ReadChunk loaded = (AnvilChunkReader.ReadChunk) result;
        net.zaminmc.torch.server.world.EngineChunk imported = loaded.chunk();
        // The publication rule: a position the live world already holds
        // keeps its state (installGenerated's own contract); the import's
        // detached chunk is counted and discarded, never forced in. The
        // content rows ride the skip WHOLE (the reference never loads a
        // chunk it already has — entities and tiles included), with a
        // counted note naming what did not apply.
        if (world.peek(imported.position()) != null) {
            if (!loaded.entities().isEmpty() || !loaded.tileEntities().isEmpty()) {
                ledger.isolated("chunk already loaded — content rows not applied",
                        "chunk " + chunkX + "," + chunkZ + " carried "
                                + loaded.tileEntities().size() + " tile rows, "
                                + loaded.entities().size() + " entity rows");
            }
            ledger.chunkSkippedAlreadyLoaded();
            return;
        }
        world.installGenerated(imported.position(), imported);
        ledger.chunkInstalled();
        // The reference's own load order (createChunkFromNbt's tail): the
        // tiles apply over the installed blocks, then the entities spawn.
        applyTileEntities(loaded.tileEntities());
        applyEntities(loaded.entities());
    }

    // ------------------------------------------------------------------ the content walks

    /**
     * One installed chunk's {@code TileEntities} rows: the position ints
     * are REQUIRED (a corrupt row isolates loudly), the block at the
     * position resolves the family (the engine's own lazy getOrCreate
     * stance — the /blockdata walk's own resolution), and the rows apply
     * through {@code TileNbt.readTile} — every family's 1:1 projection:
     * chest/furnace/hopper/dispenser contents, the brewing timer, the
     * beacon's levels, the sign's lines, the spawner's entity + cadence,
     * the command block's rows, the jukebox's record, the skull's owner.
     */
    private void applyTileEntities(List<Map<String, Snbt.Tag>> tileEntities) {
        if (content == null) {
            if (!tileEntities.isEmpty()) {
                ledger.ignored("TileEntities", tileEntities.size());
            }
            return;
        }
        for (Map<String, Snbt.Tag> rows : tileEntities) {
            if (!NbtOps.containsNumeric(rows, "x") || !NbtOps.containsNumeric(rows, "y")
                    || !NbtOps.containsNumeric(rows, "z")) {
                ledger.isolated("tile row missing its x/y/z position",
                        "tile " + NbtOps.getString(rows, "id"));
                continue;
            }
            BlockPosition at = new BlockPosition(
                    NbtOps.getInt(rows, "x"), NbtOps.getInt(rows, "y"), NbtOps.getInt(rows, "z"));
            String rowId = NbtOps.getString(rows, "id");
            String family = TileNbt.idOf(world.getBlock(at).identifier().value());
            if (family == null) {
                ledger.isolated("tile row over a block with no tile family",
                        "tile " + (rowId.isEmpty() ? "(no id row)" : rowId) + " at "
                                + at.x() + "," + at.y() + "," + at.z());
                continue;
            }
            if (!rowId.isEmpty() && !rowId.equals(family)) {
                // The engine resolves by the BLOCK identity (its own
                // model); the disagreement is a counted anomaly, the rows
                // still apply through the block's family.
                ledger.isolated("tile id row '" + rowId + "' disagrees with the block's family '"
                        + family + "' (applied through the block's family)",
                        "tile at " + at.x() + "," + at.y() + "," + at.z());
            }
            content.tiles().readTile(world, 0, at, rows);
            ledger.tileImported();
        }
    }

    /**
     * One installed chunk's {@code Entities} rows (the reference's
     * Entities.create walk): the living mobs ride the /summon machinery
     * whole — {@code spawnAt} then {@code EntityNbt.readNbt}, whose own
     * Pos/Rotation/Motion/UUID/custom walks are the reference's own
     * Entity.readNbt — and the dropped items restore through the exact
     * position spawn with their Age/PickupDelay/Thrower/Owner rows. The
     * families with no engine surface (vehicles, projectiles, paintings,
     * item frames, XP orbs, riding chains) isolate with their exact id —
     * §31.6's counted refusal.
     */
    private void applyEntities(List<Map<String, Snbt.Tag>> entities) {
        if (content == null) {
            if (!entities.isEmpty()) {
                ledger.ignored("Entities", entities.size());
            }
            return;
        }
        for (Map<String, Snbt.Tag> rows : entities) {
            String id = NbtOps.getString(rows, "id");
            if (id.isEmpty()) {
                ledger.isolated("entity row without an id", "entity row in the chunk's list");
                continue;
            }
            List<Snbt.Tag> pos = NbtOps.getList(rows, "Pos", Snbt.Tag.Type.DOUBLE);
            if (pos.size() != 3) {
                ledger.isolated("entity row with a corrupt Pos row",
                        "entity " + id + " (Pos size " + pos.size() + ")");
                continue;
            }
            Position at = new Position(
                    NbtOps.listDouble(pos, 0), NbtOps.listDouble(pos, 1), NbtOps.listDouble(pos, 2));
            // The dropped item (EntityItem): the stack's own compound +
            // the cadence rows, the restored spawn.
            if (id.equals("Item")) {
                net.zaminmc.torch.item.ItemStack stack = ItemNbt.read(
                        NbtOps.getCompound(rows, "Item"));
                if (stack == null) {
                    ledger.isolated("item entity with an unresolvable Item compound",
                            "entity Item at " + at.x() + "," + at.y() + "," + at.z());
                    continue;
                }
                var entity = content.items().spawnRestored(at, stack);
                if (entity == null) {
                    ledger.isolated("item entity spawn withheld (the spawn gate)",
                            "entity Item at " + at.x() + "," + at.y() + "," + at.z());
                    continue;
                }
                if (NbtOps.containsNumeric(rows, "Age")) {
                    entity.setAge(NbtOps.getShort(rows, "Age"));
                }
                if (NbtOps.containsNumeric(rows, "PickupDelay")) {
                    entity.setPickupDelayTicks(NbtOps.getShort(rows, "PickupDelay"));
                }
                String thrower = NbtOps.getString(rows, "Thrower");
                if (!thrower.isEmpty()) {
                    entity.setThrower(thrower);
                }
                String owner = NbtOps.getString(rows, "Owner");
                if (!owner.isEmpty()) {
                    entity.setOwner(owner);
                }
                ledger.entityImported();
                continue;
            }
            // The living mobs (the /summon machinery whole).
            MobType type = MobType.byVanillaName(id);
            if (type == null) {
                type = MobType.byName(id);
            }
            if (type == null) {
                ledger.isolated("entity family not importable (no engine surface)",
                        "entity " + id + " at " + at.x() + "," + at.y() + "," + at.z());
                continue;
            }
            MobEntity spawned = content.mobs().spawnAt(type, at);
            if (spawned == null) {
                ledger.isolated("mob spawn withheld (the spawn gate)",
                        "entity " + id + " at " + at.x() + "," + at.y() + "," + at.z());
                continue;
            }
            EntityNbt.readNbt(spawned, rows, content.potions(),
                    () -> content.equipmentChanged().accept(spawned));
            ledger.entityImported();
            if (NbtOps.contains(rows, "Riding", Snbt.Tag.Type.COMPOUND)) {
                // The reference walks the Riding chain creating mounts and
                // seating riders; the engine has no riding surface — the
                // rider imports, the chain isolates loudly.
                ledger.isolated("riding chain not imported (no riding surface)",
                        "entity " + id + " rides "
                                + NbtOps.getString(NbtOps.getCompound(rows, "Riding"), "id"));
            }
        }
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
