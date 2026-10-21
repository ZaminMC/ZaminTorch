package net.zaminmc.torch.server.world.anvil;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.decoration.DecorationSystem;
import net.zaminmc.torch.server.decoration.ItemFrame;
import net.zaminmc.torch.server.decoration.Painting;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.nbt.BinaryNbtReader;
import net.zaminmc.torch.server.nbt.EntityNbt;
import net.zaminmc.torch.server.nbt.ItemNbt;
import net.zaminmc.torch.server.nbt.NbtOps;
import net.zaminmc.torch.server.nbt.Snbt;
import net.zaminmc.torch.server.nbt.TileNbt;
import net.zaminmc.torch.server.potion.PotionSystem;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Identifier;
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
 * <p><b>THE CONTENT SEAMS (the entities/tiles/tile-ticks follow-up —
 * the reference's own load order):</b> after a chunk installs, its
 * carried {@code TileEntities} rows apply through the engine's own
 * per-family read walk ({@code TileNbt.readTile} — the /blockdata
 * machinery, every family's 1:1 projection), then its {@code
 * TileTicks} rows land through the scheduled block-tick surface's own
 * import arm ({@code loadScheduledTick} — the reference's
 * createChunkFromNbt tail: the ticks load AFTER the tiles, BEFORE the
 * entities), then its {@code Entities} rows import through the /summon
 * machinery ({@code MobManager.spawnAt} + {@code EntityNbt.readNbt}
 * for the living mobs — Pos/Rotation/Motion/UUID/custom rows all land
 * — and {@code ItemEntityManager.spawnRestored} + the {@code Item}/
 * {@code Age}/{@code PickupDelay}/{@code Thrower}/{@code Owner} rows
 * for the dropped items, the {@code VehicleManager} hosts for the
 * eight vehicle ids, {@code ProjectileManager.spawnRestored} for the
 * nine projectile families (the stuck arrow's {@code inGround}/{@code
 * life} rows included), {@code DecorationSystem.restoreFrame}/
 * {@code restorePainting} for the item frames and paintings (the
 * anchor/facing/motive/display rows, the legacy {@code Direction}
 * offset quirk included), and {@code ExperienceOrbManager}
 * .spawnRestored for the XP orbs (the {@code Value}/{@code Age} rows).
 * What still has no engine surface isolates LOUDLY with its exact id
 * (§31.6's counted refusal, never a silent drop): the riding chains
 * (the non-session rider attach publication + the server-side rider
 * follow — the reference's updateRidden — have no surface yet), the
 * firework/eye-of-ender/leash-knot families, and every per-family row
 * whose engine model derives it differently (the ledger names each).
 * A skipped (already-loaded) chunk's content rows ride the skip whole,
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
     * The vehicle seams (the engine's own vehicle managers — the same
     * machinery the live item-use path and the restart restore spawn
     * through): the manager plus the three variant hosts (the hopper
     * cart's transfer host, the spawner cart's spawn host, the command
     * cart's run host).
     */
    public record VehicleSeams(
            net.zaminmc.torch.server.entity.vehicle.VehicleManager manager,
            java.util.function.Supplier<net.zaminmc.torch.server.entity.vehicle.HopperMinecartEntity.TransferHost> hopperHost,
            java.util.function.Supplier<net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity.SpawnHost> spawnerHost,
            java.util.function.Supplier<net.zaminmc.torch.server.entity.vehicle.CommandBlockMinecartEntity.CommandHost> commandHost) {
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
     * @param scheduledTicks the scheduled block-tick surface (the
     *                       TileTicks import arm)
     * @param vehicles the vehicle seams (null = the entities walk counts
     *                 vehicle rows as not-imported)
     * @param projectiles the projectile surface (null = the projectile
     *                   families count as not-imported)
     * @param decorations the hanging-decoration surface (null = the
     *                   painting/item-frame rows count as not-imported)
     * @param orbs the experience-orb surface (null = the XP-orb rows
     *             count as not-imported)
     */
    public record ContentSeams(TileNbt tiles, MobManager mobs, ItemEntityManager items,
                               PotionSystem potions,
                               java.util.function.Consumer<MobEntity> equipmentChanged,
                               net.zaminmc.torch.server.block.ScheduledTickSystem scheduledTicks,
                               VehicleSeams vehicles,
                               net.zaminmc.torch.server.entity.projectile.ProjectileManager projectiles,
                               net.zaminmc.torch.server.decoration.DecorationSystem decorations,
                               net.zaminmc.torch.server.experience.ExperienceOrbManager orbs,
                               net.zaminmc.torch.server.fireworks.FireworksSystem fireworks,
                               net.zaminmc.torch.server.endereye.EnderEyeSystem eyes,
                               net.zaminmc.torch.server.leash.LeadSystem leads,
                               GenericSeatSeam genericSeats) {
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
            if (!loaded.entities().isEmpty() || !loaded.tileEntities().isEmpty()
                    || !loaded.tileTicks().isEmpty()) {
                ledger.isolated("chunk already loaded — content rows not applied",
                        "chunk " + chunkX + "," + chunkZ + " carried "
                                + loaded.tileEntities().size() + " tile rows, "
                                + loaded.entities().size() + " entity rows, "
                                + loaded.tileTicks().size() + " tick rows");
            }
            ledger.chunkSkippedAlreadyLoaded();
            return;
        }
        world.installGenerated(imported.position(), imported);
        ledger.chunkInstalled();
        // The reference's own load order (createChunkFromNbt's tail): the
        // tiles apply over the installed blocks, the tile ticks load after
        // the tiles, then the entities spawn (loadEntities' own position).
        applyTileEntities(loaded.tileEntities());
        applyTileTicks(loaded.tileTicks());
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
     * One installed chunk's {@code TileTicks} rows — the reference's own
     * import walk (AnvilChunkStorage's TileTicks loop over
     * {@code world.loadScheduledTick}): the position ints are REQUIRED
     * (a corrupt row isolates loudly), the block resolves from the row's
     * own name — the {@code "i"} string form first (the reference's
     * {@code Block.byKey(contains("i", 8))}), the legacy integer form
     * through {@code AnvilBlockIds.resolve(id, 0)} (the reference's
     * {@code Block.byId(getInt("i"))} — the bare id, the family's base
     * state) — and the row lands through {@code loadScheduledTick} with
     * its {@code t} delay and {@code p} priority (the reference's own
     * time-from-now semantics). An unresolvable name isolates loudly
     * with the row's own id named; an AIR-family row lands at time zero
     * (the surface's own quirk, the reference's material gate).
     */
    private void applyTileTicks(List<Map<String, Snbt.Tag>> tileTicks) {
        if (content == null) {
            if (!tileTicks.isEmpty()) {
                ledger.ignored("TileTicks", tileTicks.size());
            }
            return;
        }
        net.zaminmc.torch.server.block.ScheduledTickSystem scheduledTicks =
                content.scheduledTicks();
        if (scheduledTicks == null) {
            if (!tileTicks.isEmpty()) {
                ledger.ignored("TileTicks", tileTicks.size());
            }
            return;
        }
        for (Map<String, Snbt.Tag> rows : tileTicks) {
            if (!NbtOps.containsNumeric(rows, "x") || !NbtOps.containsNumeric(rows, "y")
                    || !NbtOps.containsNumeric(rows, "z")) {
                ledger.isolated("tick row missing its x/y/z position",
                        "tick " + describeTickRow(rows));
                continue;
            }
            BlockPosition at = new BlockPosition(
                    NbtOps.getInt(rows, "x"), NbtOps.getInt(rows, "y"), NbtOps.getInt(rows, "z"));
            Identifier family = resolveTickBlock(rows);
            if (family == null) {
                continue; // resolveTickBlock already isolated the row loudly
            }
            scheduledTicks.loadScheduledTick(at, family,
                    NbtOps.getInt(rows, "t"), NbtOps.getInt(rows, "p"));
            ledger.tileTickImported();
        }
    }

    /** The row's own identity for the isolation notes (the name if present, the id otherwise). */
    private static String describeTickRow(Map<String, Snbt.Tag> rows) {
        if (NbtOps.contains(rows, "i", Snbt.Tag.Type.STRING)) {
            return NbtOps.getString(rows, "i");
        }
        return "id " + NbtOps.getInt(rows, "i");
    }

    /**
     * The reference's block resolution for one tick row (the string name
     * first, the legacy integer second) — null after an isolation.
     */
    private Identifier resolveTickBlock(Map<String, Snbt.Tag> rows) {
        if (NbtOps.contains(rows, "i", Snbt.Tag.Type.STRING)) {
            String name = NbtOps.getString(rows, "i");
            Identifier id = Identifier.parse(name);
            if (world.blockRegistry().lookup(id).isPresent()) {
                return id;
            }
            ledger.isolated("tick row's block name not registered (the engine carries no such family)",
                    "tick " + name);
            return null;
        }
        int legacyId = NbtOps.getInt(rows, "i");
        java.util.Optional<Identifier> resolved = AnvilBlockIds.resolve(legacyId, 0);
        if (resolved.isPresent()) {
            return resolved.get();
        }
        ledger.isolated("tick row's legacy block id not registered (the engine carries no such family)",
                "tick id " + legacyId);
        return null;
    }

    /**
     * One installed chunk's {@code Entities} rows (the reference's
     * Entities.create walk): the living mobs ride the /summon machinery
     * whole — {@code spawnAt} then {@code EntityNbt.readNbt}, whose own
     * Pos/Rotation/Motion/UUID/custom walks are the reference's own
     * Entity.readNbt — and the dropped items restore through the exact
     * position spawn with their Age/PickupDelay/Thrower/Owner rows. The
     * families with no engine surface (the fireworks rocket, the eye of
     * ender, the leash knot) isolate with their exact id — §31.6's counted
     * refusal; the riding chains walk the shared importRidingChain (the
     * mob and vehicle seatings land, the exotic shapes isolate loudly).
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
            Imported row = importRow(id, at, rows);
            if (row != null) {
                importRidingChain(row, rows);
            }
        }
    }

    /**
     * The generic-mount seating seam (the exotic shapes' import form): a
     * rider takes a non-vehicle/mob mount's seat through the engine's
     * own registry (the reference's startRiding at load) — the mount's
     * own seat height (the base Entity's getMountHeight: the family's
     * height * 0.75) riding the consult.
     */
    public interface GenericSeatSeam {
        /** @return whether the seat opened (false = the occupied seat). */
        boolean seat(int mountId,
                java.util.function.Supplier<Position> mountPosition,
                java.util.function.BooleanSupplier mountAlive,
                double mountHeight,
                int riderEngineId);
    }

    /**
     * One imported entity row's seatable handle: the mob and vehicle pair
     * for the seatable families; the generic handle (the position + alive
     * reads + the family's own seat height) for the families that carry
     * no vehicle/mob surface (the dropped items, the projectiles, the
     * fireworks rockets, the eyes, the XP orbs); null never returned for
     * those — the handle answers the riding chain's walk (the reference's
     * startRiding seats ANY family; the engine's follow arms now carry
     * every family — the rider side of the §31.6 counted refusal
     * retired).
     */
    private record Imported(MobEntity mob,
            net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle,
            GenericMountHandle generic) {
        int engineId() {
            return mob != null ? mob.entityId()
                    : vehicle != null ? vehicle.entityId() : generic.id();
        }
    }

    /** A landed generic family's own mount handle (the exotic seating). */
    private record GenericMountHandle(int id,
            java.util.function.Supplier<Position> position,
            java.util.function.BooleanSupplier alive,
            double mountHeight) {
    }

    /**
     * The reference's base Entity getMountHeight (Entity.java lines
     * 1367-1369: {@code this.height * 0.75}) for the generic families —
     * the mount's own seat band, from each family's own registered size:
     * the 0.25 bodies (the dropped item, the thrown family, the firework
     * rocket, the eye of ender) seat at +0.1875, the 0.5 pair (the arrow,
     * the XP orb) at +0.375, the ghast fireball's 1.0 at +0.75, and the
     * 0.3125 pair (the fire charge, the wither skull) at +0.234375.
     */
    private static final double MOUNT_HEIGHT_QUARTER = 0.25F * 0.75F;
    private static final double MOUNT_HEIGHT_HALF = 0.5F * 0.75F;
    private static final double MOUNT_HEIGHT_FULL = 1.0F * 0.75F;
    private static final double MOUNT_HEIGHT_FIRE_CHARGE = 0.3125F * 0.75F;

    /** The thrown family's own band (ThrownEntity's 0.25F size). */
    private static double thrownMountHeight() {
        return MOUNT_HEIGHT_QUARTER;
    }

    /** The projectile's own band from its kind (the reference's sizes). */
    private static double projectileMountHeight(
            net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind kind) {
        return switch (kind) {
            case ARROW -> MOUNT_HEIGHT_HALF;             // ArrowEntity's 0.5F
            case SMALL_FIREBALL, WITHER_SKULL -> MOUNT_HEIGHT_FIRE_CHARGE; // 0.3125F
            case LARGE_FIREBALL -> MOUNT_HEIGHT_FULL;    // the base ProjectileEntity's 1.0F
            default -> MOUNT_HEIGHT_QUARTER;             // ThrownEntity's 0.25F
        };
    }

    /**
     * One entity row's family dispatch (the reference's Entities.create
     * dispatch): the row lands through its family's own arm and answers
     * a seatable handle — {@code null} when the row did not land (the
     * arm's own isolation note fired inside), {@code Imported(null,
     * null)} when the family landed but carries no seat surface. The
     * living mobs ride the /summon machinery whole — {@code spawnAt} then
     * {@code EntityNbt.readNbt}, whose own Pos/Rotation/Motion/UUID/custom
     * walks are the reference's own Entity.readNbt — and the dropped
     * items restore through the exact-position spawn with their
     * Age/PickupDelay/Thrower/Owner rows. The families with no engine
     * surface isolate with their exact id — §31.6's counted refusal.
     */
    private Imported importRow(String id, Position at, Map<String, Snbt.Tag> rows) {
        // The dropped item (EntityItem): the stack's own compound + the
        // cadence rows, the restored spawn.
        if (id.equals("Item")) {
            return importItem(at, rows);
        }
        // The vehicles (the reference's Entities.create dispatch — the
        // eight wire ids; the engine's own managers spawn them, the same
        // hosts the live paths use).
        if (isVehicleId(id)) {
            net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle =
                    importVehicle(id, at, rows);
            return vehicle == null ? null : new Imported(null, vehicle, null);
        }
        // The projectiles (the reference's nine wire families — the
        // engine's own manager, the restored form; the generic-mount handle
        // for the exotic riding shapes).
        var projectileKind = projectileKindOf(id);
        if (projectileKind != null) {
            return new Imported(null, null,
                    importProjectile(id, projectileKind, at, rows));
        }
        // The hanging decorations (the painting + the item frame — the
        // engine's own decoration system, the exact-cell form).
        if (id.equals("Painting") || id.equals("ItemFrame")) {
            importDecoration(id, rows);
            return new Imported(null, null, null);
        }
        // The XP orb (the engine's own orb manager, the exact-value form;
        // the generic-mount handle for the exotic riding shapes).
        if (id.equals("XPOrb")) {
            return new Imported(null, null, importXpOrb(at, rows));
        }
        // The firework rocket (the reference's FireworksRocketEntity — the
        // engine's own fireworks system, the restored form; the
        // generic-mount handle for the exotic riding shapes).
        if (id.equals("FireworksRocketEntity")) {
            return new Imported(null, null, importFirework(at, rows));
        }
        // The eye of ender (the reference's EyeOfEnderSignal — the engine's
        // own eye system, the restored form; the generic-mount handle for
        // the exotic riding shapes).
        if (id.equals("EyeOfEnderSignal")) {
            return new Imported(null, null, importEye(at, rows));
        }
        // The leash knot (the reference's LeashKnot — the engine's own lead
        // system, the restored anchor).
        if (id.equals("LeashKnot")) {
            importKnot(rows);
            return new Imported(null, null, null);
        }
        // The living mobs (the /summon machinery whole).
        MobType type = MobType.byVanillaName(id);
        if (type == null) {
            type = MobType.byName(id);
        }
        if (type == null) {
            ledger.isolated("entity family not importable (no engine surface)",
                    "entity " + id + " at " + at.x() + "," + at.y() + "," + at.z());
            return null;
        }
        MobEntity spawned = content.mobs().spawnAt(type, at);
        if (spawned == null) {
            ledger.isolated("mob spawn withheld (the spawn gate)",
                    "entity " + id + " at " + at.x() + "," + at.y() + "," + at.z());
            return null;
        }
        EntityNbt.readNbt(spawned, rows, content.potions(),
                () -> content.equipmentChanged().accept(spawned));
        ledger.entityImported();
        return new Imported(spawned, null, null);
    }

    /** The dropped item row (EntityItem): landed, never seatable. */
    private Imported importItem(Position at, Map<String, Snbt.Tag> rows) {
        net.zaminmc.torch.item.ItemStack stack = ItemNbt.read(
                NbtOps.getCompound(rows, "Item"));
        if (stack == null) {
            ledger.isolated("item entity with an unresolvable Item compound",
                    "entity Item at " + at.x() + "," + at.y() + "," + at.z());
            return null;
        }
        var entity = content.items().spawnRestored(at, stack);
        if (entity == null) {
            ledger.isolated("item entity spawn withheld (the spawn gate)",
                    "entity Item at " + at.x() + "," + at.y() + "," + at.z());
            return null;
        }
        var itemSystem = content.items();
        GenericMountHandle generic = new GenericMountHandle(entity.entityId(),
                entity::position,
                () -> itemSystem.all().stream().anyMatch(
                        live -> live.entityId() == entity.entityId()),
                MOUNT_HEIGHT_QUARTER); // ItemEntity's 0.25F size
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
        return new Imported(null, null, generic);
    }

    /**
     * The riding chain walk (the reference's AnvilChunkStorage lines
     * 327-341, 1:1): the nested {@code Riding} compound IS the mount —
     * the outer entity rides it, the chain walks down (a mount may
     * itself carry a Riding row). Each mount lands through its family's
     * own arm with the mount's own Pos, then the head takes the mount's
     * seat: the vehicle seat through the manager host (the §27.7
     * convention — the attach publication flows through the listener)
     * and the mob seat through the manager's restored-rider arm (the
     * plain seat, no temper flow — the reference's startRiding at load,
     * never the live interact's mount-attempt law). The seatings the
     * engine cannot carry follow-wise isolate loudly with the missing
     * surface named — §31.6's counted refusal (landing a seated body
     * with no follow would be a silent behavior deviation, §0 rule 12).
     */
    private void importRidingChain(Imported head, Map<String, Snbt.Tag> rows) {
        while (NbtOps.contains(rows, "Riding", Snbt.Tag.Type.COMPOUND)) {
            Map<String, Snbt.Tag> mountRows = NbtOps.getCompound(rows, "Riding");
            String mountId = NbtOps.getString(mountRows, "id");
            List<Snbt.Tag> mountPos = NbtOps.getList(mountRows, "Pos", Snbt.Tag.Type.DOUBLE);
            if (mountId.isEmpty() || mountPos.size() != 3) {
                ledger.isolated("riding chain's mount row corrupt (no id or Pos)",
                        "entity rides " + mountId);
                return;
            }
            Position mountAt = new Position(
                    NbtOps.listDouble(mountPos, 0),
                    NbtOps.listDouble(mountPos, 1),
                    NbtOps.listDouble(mountPos, 2));
            // The mount always lands first (the reference's shape: the
            // nested compound is created and added whether or not the
            // seating can follow).
            Imported mount = importRow(mountId, mountAt, mountRows);
            if (mount == null) {
                // The mount's family carried no engine surface (the
                // importRow's own isolation note fired inside).
                return;
            }
            // The head's own seatable handle: every family but the
            // hanging decorations and the leash knots carries one (the
            // mob/vehicle/generic records — the reference's startRiding
            // seats any family; the engine's decorations carry no
            // position provider to follow with, the loud isolation the
            // §31.6 convention keeps).
            if (head.mob == null && head.vehicle == null && head.generic == null) {
                ledger.isolated("riding chain not imported (the rider family has no "
                                + "seat surface: the hanging decorations and the leash "
                                + "knots carry no position provider)",
                        "entity rides " + mountId);
                return;
            }
            if (mount.mob == null && mount.vehicle == null) {
                // THE GENERIC-MOUNT SEAT (the exotic shapes' mount side —
                // a rider on a non-vehicle/mob mount: a dropped item, a
                // projectile, a firework, an eye, an XP orb): the
                // reference's startRiding seats ANY family, the engine's
                // generic registry carries the follow whatever family the
                // rider is (the rider side of the §31.6 counted refusal
                // retired).
                if (mount.generic == null) {
                    ledger.isolated("riding chain not imported (the mount family has "
                                    + "no seat surface: the engine's seats carry vehicles, "
                                    + "mobs and the generic families alone)",
                            "entity rides " + mountId);
                    return;
                }
                GenericSeatSeam seats = content == null ? null : content.genericSeats();
                if (seats == null || !seats.seat(mount.generic.id(),
                        mount.generic.position(), mount.generic.alive(),
                        mount.generic.mountHeight(),
                        head.engineId())) {
                    ledger.isolated("riding chain's generic seat refused (occupied or dead)",
                            "entity rides " + mountId);
                    return;
                }
                head = mount;
                rows = mountRows;
                continue;
            }
            // The head takes the mount's seat — ANY head family (the
            // reference's startRiding at load: the mob, the vehicle, the
            // projectile, the dropped item, the firework rocket, the eye,
            // the XP orb all seat; the engine's follow arms carry every
            // family, the exotic riders' own ride height the base 0.0).
            if (mount.vehicle != null) {
                VehicleSeams seams = content == null ? null : content.vehicles();
                if (seams == null || !seams.manager().mount(mount.vehicle, head.engineId())) {
                    ledger.isolated("riding chain's vehicle seat refused (occupied or dead)",
                            "entity rides " + mountId);
                    return;
                }
            } else {
                content.mobs().seatImportedRider(mount.mob, head.engineId());
            }
            head = mount;
            rows = mountRows;
        }
    }

    // ------------------------------------------------------------------ the vehicle walk

    /** The reference's eight vehicle ids (Entities.register's own names). */
    private static boolean isVehicleId(String id) {
        return id.equals("MinecartRideable") || id.equals("MinecartChest")
                || id.equals("MinecartFurnace") || id.equals("MinecartTNT")
                || id.equals("MinecartHopper") || id.equals("MinecartSpawner")
                || id.equals("MinecartCommandBlock") || id.equals("Boat");
    }

    /**
     * One vehicle row (the reference's Entities.create dispatch + the base
     * Entity.readNbt walk over the engine's own vehicle surfaces): the
     * family spawns through the manager (the same hosts the live paths
     * use — a null return is the §27.7 gate's loud refusal), then the
     * shared rows land — Rotation (yaw/pitch), Motion (the reference's
     * own ±10 clamp), the raw signed Fire — and the per-family rows:
     * the chest cart's {@code Items} list (the reference's
     * InventoryMinecart.readCustomNbt walk — the Slot byte indexes the
     * 27-slot cargo), the spawner cart's {@code EntityId}/{@code Delay}
     * (the reference's MobSpawner.readNbt — the cadence/potential rows
     * have no per-cart surface, the engine's own cadence holds: one
     * loud note naming them), and the command cart's four rows (the
     * reference's CommandExecutor.readNbt). The base rows land through
     * restoreBaseRows (the vehicle slice's Entity.readNbt band: the UUID
     * pair/string forms, the custom name + visibility, Silent, the fall
     * accumulator, the breath clock, the ground state, the dimension, the
     * invulnerable flag, the portal cooldown). A {@code Riding}
     * row walks the shared chain (importRidingChain — the reference's
     * mount nesting).
     */
    private net.zaminmc.torch.server.entity.vehicle.VehicleEntity importVehicle(
            String id, Position at, Map<String, Snbt.Tag> rows) {
        VehicleSeams seams = content == null ? null : content.vehicles();
        if (seams == null) {
            // No vehicle seams wired: the rows count as not-imported.
            ledger.ignored("Entities (vehicle rows)", 1);
            return null;
        }
        var manager = seams.manager();
        net.zaminmc.torch.server.entity.vehicle.VehicleEntity spawned;
        switch (id) {
            case "Boat" -> spawned = manager.spawnBoat(at);
            case "MinecartRideable" -> spawned = manager.spawnMinecart(at);
            case "MinecartChest" -> spawned = manager.spawnChestMinecart(at);
            case "MinecartFurnace" -> spawned = manager.spawnFurnaceMinecart(at);
            case "MinecartTNT" -> spawned = manager.spawnTntMinecart(at);
            case "MinecartHopper" -> {
                if (seams.hopperHost() == null) {
                    ledger.isolated("hopper cart has no transfer host (the engine's hopper surface is absent)",
                            "entity " + id + " at " + at.x() + "," + at.y() + "," + at.z());
                    return null;
                }
                spawned = manager.spawnHopperMinecart(at, seams.hopperHost().get());
                continueVehicleImport(spawned, id, at, rows);
                if (spawned != null) {
                    ledger.entityImported();
                }
                return spawned;
            }
            case "MinecartSpawner" -> {
                if (seams.spawnerHost() == null) {
                    ledger.isolated("spawner cart has no spawn host (the engine's spawner surface is absent)",
                            "entity " + id + " at " + at.x() + "," + at.y() + "," + at.z());
                    return null;
                }
                spawned = manager.spawnSpawnerMinecart(at, seams.spawnerHost().get());
                if (spawned instanceof net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity cart) {
                    // The reference's MobSpawner.readNbt: EntityId (the
                    // vanilla name) + the raw Delay.
                    String entityName = NbtOps.getString(rows, "EntityId");
                    if (!entityName.isEmpty()) {
                        MobType type = MobType.byVanillaName(entityName);
                        if (type == null) {
                            type = MobType.byName(entityName);
                        }
                        if (type != null) {
                            cart.setType(type);
                        } else {
                            ledger.isolated("spawner cart's EntityId row not importable (no engine mob surface)",
                                    "entity " + id + " EntityId '" + entityName + "'");
                        }
                    }
                    if (NbtOps.containsNumeric(rows, "Delay")) {
                        cart.setDelay(NbtOps.getShort(rows, "Delay"));
                    }
                    // The cadence rows (the Wave VI per-cart surface —
                    // MobSpawnerBaseLogic.readNbt's own shorts): the
                    // imported cart carries its OWN band, attempts, cap,
                    // gate, and roll range — the engine's defaults hold
                    // only for the absent rows, exactly the reference.
                    if (NbtOps.containsNumeric(rows, "MinSpawnDelay")) {
                        cart.setMinSpawnDelay(NbtOps.getShort(rows, "MinSpawnDelay"));
                    }
                    if (NbtOps.containsNumeric(rows, "MaxSpawnDelay")) {
                        cart.setMaxSpawnDelay(NbtOps.getShort(rows, "MaxSpawnDelay"));
                    }
                    if (NbtOps.containsNumeric(rows, "SpawnCount")) {
                        cart.setSpawnCount(NbtOps.getShort(rows, "SpawnCount"));
                    }
                    if (NbtOps.containsNumeric(rows, "MaxNearbyEntities")) {
                        cart.setMaxNearbyEntities(NbtOps.getShort(rows, "MaxNearbyEntities"));
                    }
                    if (NbtOps.containsNumeric(rows, "RequiredPlayerRange")) {
                        cart.setRequiredPlayerRange(NbtOps.getShort(rows, "RequiredPlayerRange"));
                    }
                    if (NbtOps.containsNumeric(rows, "SpawnRange")) {
                        cart.setSpawnRange(NbtOps.getShort(rows, "SpawnRange"));
                    }
                    // The potential rows (SpawnData/SpawnPotentials): the
                    // single-type model's own ledgered refusal — the cart
                    // carries one EntityId, never the weighted list.
                    if (NbtOps.contains(rows, "SpawnData", Snbt.Tag.Type.COMPOUND)
                            || NbtOps.contains(rows, "SpawnPotentials", Snbt.Tag.Type.LIST)) {
                        ledger.isolated("spawner cart's SpawnData/SpawnPotentials rows have no cart surface "
                                        + "(the single-type model — the block family's ledgered row)",
                                "entity " + id + " at " + at.x() + "," + at.y() + "," + at.z());
                    }
                }
                continueVehicleImport(spawned, id, at, rows);
                if (spawned != null) {
                    ledger.entityImported();
                }
                return spawned;
            }
            case "MinecartCommandBlock" -> {
                if (seams.commandHost() == null) {
                    ledger.isolated("command cart has no run host (the engine's command surface is absent)",
                            "entity " + id + " at " + at.x() + "," + at.y() + "," + at.z());
                    return null;
                }
                spawned = manager.spawnCommandBlockMinecart(at, seams.commandHost().get());
                if (spawned instanceof net.zaminmc.torch.server.entity.vehicle.CommandBlockMinecartEntity cart) {
                    // The reference's CommandExecutor.readNbt (lines
                    // 53-74): the four rows.
                    String command = NbtOps.getString(rows, "Command");
                    if (!command.isEmpty()) {
                        cart.setCommand(command);
                    }
                    cart.setSuccessCount(NbtOps.getInt(rows, "SuccessCount"));
                    cart.setTrackOutput(NbtOps.getBoolean(rows, "TrackOutput"));
                    String lastOutput = NbtOps.getString(rows, "LastOutput");
                    if (!lastOutput.isEmpty()) {
                        cart.setLastOutput(lastOutput);
                    }
                }
                continueVehicleImport(spawned, id, at, rows);
                if (spawned != null) {
                    ledger.entityImported();
                }
                return spawned;
            }
            default -> {
                spawned = null;
            }
        }
        continueVehicleImport(spawned, id, at, rows);
        if (spawned != null) {
            ledger.entityImported();
        }
        return spawned;
    }

    /**
     * The shared rows over one spawned vehicle (the base Entity.readNbt
     * walk at the vehicle surface's granularity): Rotation, Motion with
     * the reference's ±10 clamp, the raw signed Fire, the chest cart's
     * Items, and the Riding chain's loud isolation.
     */
    private void continueVehicleImport(
            net.zaminmc.torch.server.entity.vehicle.VehicleEntity spawned,
            String id, Position at, Map<String, Snbt.Tag> rows) {
        if (spawned == null) {
            ledger.isolated("vehicle spawn withheld (the spawn gate)",
                    "entity " + id + " at " + at.x() + "," + at.y() + "," + at.z());
            return;
        }
        // The shared base rows (the reference's Entity.readNbt, the
        // surface's own granularity).
        List<Snbt.Tag> rotation = NbtOps.getList(rows, "Rotation", Snbt.Tag.Type.FLOAT);
        spawned.setRotation(new net.zaminmc.torch.util.Rotation(
                NbtOps.listFloat(rotation, 0), NbtOps.listFloat(rotation, 1)));
        if (NbtOps.contains(rows, "Motion", Snbt.Tag.Type.LIST)) {
            List<Snbt.Tag> motion = NbtOps.getList(rows, "Motion", Snbt.Tag.Type.DOUBLE);
            double vx = NbtOps.listDouble(motion, 0);
            double vy = NbtOps.listDouble(motion, 1);
            double vz = NbtOps.listDouble(motion, 2);
            // The reference's own clamp (Entity.readNbt lines 1156-1166):
            // a component beyond ±10 reads as 0.
            if (Math.abs(vx) > 10.0) {
                vx = 0.0;
            }
            if (Math.abs(vy) > 10.0) {
                vy = 0.0;
            }
            if (Math.abs(vz) > 10.0) {
                vz = 0.0;
            }
            spawned.setVelocity(vx, vy, vz);
        }
        // The burn clock's raw signed row (Entity.writeNbt's Fire short).
        if (NbtOps.containsNumeric(rows, "Fire")) {
            spawned.restoreFireTimer(NbtOps.getShort(rows, "Fire"));
        }
        // The base rows (the reference's Entity.readNbt lines 1155-1196,
        // 1:1 — the §31.6 counted refusal retired): the UUID (the
        // UUIDMost/Least long pair, else the string form), the custom
        // name (a non-empty row lands, the reference's own length gate)
        // + its visibility flag, the fall accumulator, the breath clock,
        // the ground state, the dimension id, the invulnerable flag, the
        // silence flag, the portal cooldown. Absent rows keep the model's
        // own defaults — the reference's absent-row shape.
        java.util.UUID uuid = null;
        if (NbtOps.contains(rows, "UUIDMost", Snbt.Tag.Type.LONG)
                && NbtOps.contains(rows, "UUIDLeast", Snbt.Tag.Type.LONG)) {
            uuid = new java.util.UUID(NbtOps.getLong(rows, "UUIDMost"),
                    NbtOps.getLong(rows, "UUIDLeast"));
        } else if (NbtOps.contains(rows, "UUID", Snbt.Tag.Type.STRING)) {
            try {
                uuid = java.util.UUID.fromString(NbtOps.getString(rows, "UUID"));
            } catch (IllegalArgumentException malformed) {
                ledger.isolated("vehicle UUID row malformed",
                        "entity " + id + " uuid " + NbtOps.getString(rows, "UUID"));
            }
        }
        String customName = null;
        if (NbtOps.contains(rows, "CustomName", Snbt.Tag.Type.STRING)
                && NbtOps.getString(rows, "CustomName").length() > 0) {
            customName = NbtOps.getString(rows, "CustomName");
        }
        spawned.restoreBaseRows(
                uuid,
                customName,
                NbtOps.getBoolean(rows, "CustomNameVisible"),
                NbtOps.getBoolean(rows, "Silent"),
                NbtOps.contains(rows, "FallDistance", Snbt.Tag.Type.FLOAT)
                        ? NbtOps.getFloat(rows, "FallDistance") : 0.0f,
                NbtOps.containsNumeric(rows, "Air") ? NbtOps.getShort(rows, "Air") : 300,
                NbtOps.getBoolean(rows, "OnGround"),
                NbtOps.getBoolean(rows, "Invulnerable"),
                NbtOps.containsNumeric(rows, "Dimension") ? NbtOps.getInt(rows, "Dimension") : 0,
                NbtOps.containsNumeric(rows, "PortalCooldown")
                        ? NbtOps.getInt(rows, "PortalCooldown") : 0);
        // The chest cart's cargo (the reference's
        // InventoryMinecart.readCustomNbt: the Items list, the Slot byte
        // indexing the 27 slots).
        if (spawned instanceof net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity cart
                && NbtOps.contains(rows, "Items", Snbt.Tag.Type.LIST)) {
            List<Snbt.Tag> items = NbtOps.getList(rows, "Items", Snbt.Tag.Type.COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                Map<String, Snbt.Tag> slotRows = NbtOps.listCompound(items, i);
                int slot = NbtOps.getByte(slotRows, "Slot") & 0xFF;
                if (slot < 0 || slot >= net.zaminmc.torch.server.chest.ChestBlockEntity.SLOT_COUNT) {
                    ledger.isolated("chest cart's slot row outside the 27-slot cargo",
                            "entity " + id + " slot " + slot);
                    continue;
                }
                net.zaminmc.torch.item.ItemStack stack = ItemNbt.read(slotRows);
                if (stack == null || stack.isEmpty()) {
                    continue; // the reference skips empty stacks the same way
                }
                cart.chest().setSlot(slot, stack);
            }
        }
        // (The Riding row's walk lives in the shared importRidingChain — the
        // reference's AnvilChunkStorage 327-341 loop, one place for every
        // family's chain, the vehicle walk's old own-note retired with it.)
    }

    // ------------------------------------------------------------------ the projectile walk

    /**
     * The reference's projectile wire ids (Entities.register's own
     * names) through the engine's Kind table. The families the engine
     * does not model (EyeOfEnderSignal, the fireworks rocket, the
     * fishing bobber, the leash knot) stay on the generic
     * not-importable isolation.
     */
    private static ProjectileEntity.Kind projectileKindOf(String id) {
        return switch (id) {
            case "Arrow" -> ProjectileEntity.Kind.ARROW;
            case "Snowball" -> ProjectileEntity.Kind.SNOWBALL;
            case "ThrownEgg" -> ProjectileEntity.Kind.EGG;
            case "ThrownEnderpearl" -> ProjectileEntity.Kind.ENDER_PEARL;
            case "ThrownPotion" -> ProjectileEntity.Kind.POTION;
            case "ThrownExpBottle" -> ProjectileEntity.Kind.EXPERIENCE_BOTTLE;
            case "Fireball" -> ProjectileEntity.Kind.LARGE_FIREBALL;
            case "SmallFireball" -> ProjectileEntity.Kind.SMALL_FIREBALL;
            case "WitherSkull" -> ProjectileEntity.Kind.WITHER_SKULL;
            default -> null;
        };
    }

    /**
     * One projectile row (the reference's base readCustomNbt + the
     * ArrowEntity family rows, at the engine surface's granularity):
     * the saved Motion lands whole (the modern Motion doubles, else the
     * reference's own legacy {@code direction} doubles — a row with
     * NEITHER isolates loudly, the reference's own remove() shape), the
     * stuck arrow's {@code inGround} + {@code life} land through the
     * restored stuck form, and the per-family rows the engine's point
     * model derives differently are named in the ledger (the arrow's
     * xTile/yTile/zTile/inTile/inData/shake/pickup/damage/Rotation; the
     * large fireball's ExplosionPower).
     */
    private GenericMountHandle importProjectile(String id, ProjectileEntity.Kind kind,
                                                  Position at, Map<String, Snbt.Tag> rows) {
        var manager = content == null ? null : content.projectiles();
        if (manager == null) {
            // No projectile surface wired: the rows count as not-imported.
            ledger.ignored("Entities (projectile rows)", 1);
            return null;
        }
        List<Snbt.Tag> motion = NbtOps.getList(rows, "Motion", Snbt.Tag.Type.DOUBLE);
        if (motion.isEmpty()) {
            // The reference's own writeNbt carries the velocity twice —
            // the base Motion doubles and the custom direction doubles
            // (readCustomNbt's own source); an older save carries the
            // custom form alone.
            motion = NbtOps.getList(rows, "direction", Snbt.Tag.Type.DOUBLE);
        }
        if (motion.isEmpty()) {
            ledger.isolated("projectile row without a Motion/direction row",
                    "entity " + id + " at " + at.x() + "," + at.y() + "," + at.z());
            return null;
        }
        if (motion.size() != 3) {
            ledger.isolated("projectile row with a corrupt Motion row",
                    "entity " + id + " at " + at.x() + "," + at.y() + "," + at.z());
            return null;
        }
        ProjectileEntity spawned = manager.spawnRestored(kind, at,
                NbtOps.listDouble(motion, 0),
                NbtOps.listDouble(motion, 1),
                NbtOps.listDouble(motion, 2));
        if (spawned == null) {
            ledger.isolated("projectile spawn withheld (the spawn gate)",
                    "entity " + id + " at " + at.x() + "," + at.y() + "," + at.z());
            return null;
        }
        // The stuck rows (the reference's readCustomNbt: inGround + the
        // ArrowEntity-only life clock — the stick clock resumes where
        // the save left it).
        boolean inGround = NbtOps.getByte(rows, "inGround") == 1;
        if (inGround) {
            int life = NbtOps.containsNumeric(rows, "life")
                    ? NbtOps.getShort(rows, "life") : 0;
            spawned.restoreGrounded(life);
        }
        // The pickup band (readCustomNbt lines 420-424, the Arrow family
        // alone): the modern numeric row lands whole (0/1/2), else the
        // legacy player boolean's 1/0 — the VII1 counted note's pickup
        // sub-row RETIRED (the grounded arrow's collect consult reads it).
        if (kind == ProjectileEntity.Kind.ARROW) {
            if (NbtOps.containsNumeric(rows, "pickup")) {
                spawned.setPickupStatus(NbtOps.getByte(rows, "pickup"));
            } else if (NbtOps.getBoolean(rows, "player")) {
                spawned.setPickupStatus(1);
            }
        }
        // The per-family rows the engine's point model derives
        // differently (the reference's own rows, the §31.6 envelope):
        // the arrow's stuck cell/block (derived from the position at
        // rest), the shake visual, the damage override
        // (the engine derives it from the speed, the Power bonus rides
        // the launch walk), and the explicit Rotation (the wire derives
        // the angles from the motion, the reference's own ctor walk);
        // the large fireball's ExplosionPower (the engine's fixed 1).
        // (The pickup row LANDED above — the sub-row retired.)
        boolean hasUnlandedFamilyRows =
                NbtOps.containsNumeric(rows, "xTile") || NbtOps.containsNumeric(rows, "yTile")
                || NbtOps.containsNumeric(rows, "zTile") || !NbtOps.getString(rows, "inTile").isEmpty()
                || NbtOps.containsNumeric(rows, "inData") || NbtOps.containsNumeric(rows, "shake")
                || NbtOps.containsNumeric(rows, "damage")
                || !NbtOps.getList(rows, "Rotation", Snbt.Tag.Type.FLOAT).isEmpty()
                || NbtOps.containsNumeric(rows, "ExplosionPower");
        if (hasUnlandedFamilyRows) {
            ledger.isolated("projectile rows without an engine surface "
                            + "(xTile/yTile/zTile, inTile/inData, shake, damage, "
                            + "Rotation, ExplosionPower — the point model derives the cell, "
                            + "the block and the damage from the motion)",
                    "entity " + id + " at " + at.x() + "," + at.y() + "," + at.z());
        }
        ledger.entityImported();
        return new GenericMountHandle(spawned.entityId(),
                spawned::position,
                () -> manager.all().stream().anyMatch(
                        live -> live.entityId() == spawned.entityId()),
                projectileMountHeight(kind));
    }

    // ------------------------------------------------------------------ the hanging-decoration walk

    /** One decoded hanging anchor (the reference's DecorationEntity.readCustomNbt). */
    private record DecorationAnchor(BlockPosition cell, int facing) { }

    /**
     * The reference's anchor decode (DecorationEntity.readCustomNbt
     * lines 177-190): the TileX/Y/Z ints are the anchor cell; the
     * facing reads the modern {@code Facing} byte, else the legacy
     * {@code Dir} byte — both byIdHorizontal (0-3, the engine's own
     * convention) — and the pre-1.4.2 {@code Direction} form carries
     * the WALL cell, the anchor offsetting into the air the decoration
     * faces. A row with no facing form at all isolates loudly (the
     * reference's byIdHorizontal(0) would silently face south — the
     * engine refuses to guess).
     */
    private DecorationAnchor decodeDecorationAnchor(
            String id, Position at, Map<String, Snbt.Tag> rows) {
        int x = NbtOps.getInt(rows, "TileX");
        int y = NbtOps.getInt(rows, "TileY");
        int z = NbtOps.getInt(rows, "TileZ");
        BlockPosition cell = new BlockPosition(x, y, z);
        if (NbtOps.containsNumeric(rows, "Direction")) {
            // The pre-1.4.2 form: the cell is the wall cell behind, the
            // anchor sits in the air cell the facing points to (the
            // reference's own pos.offset(direction)).
            int facing = Math.floorMod(NbtOps.getByte(rows, "Direction"), 4);
            int[] off = ItemFrame.FACING_OFFSETS[facing];
            return new DecorationAnchor(cell.offset(off[0], 0, off[1]), facing);
        }
        if (NbtOps.containsNumeric(rows, "Facing")) {
            return new DecorationAnchor(cell, Math.floorMod(NbtOps.getByte(rows, "Facing"), 4));
        }
        if (NbtOps.containsNumeric(rows, "Dir")) {
            // The oldest form: the same horizontal id, no offset.
            return new DecorationAnchor(cell, Math.floorMod(NbtOps.getByte(rows, "Dir"), 4));
        }
        ledger.isolated("decoration row without a facing form (Facing/Direction/Dir)",
                "entity " + id + " at " + at.x() + "," + at.y() + "," + at.z());
        return null;
    }

    /**
     * One hanging-decoration row (the painting + the item frame — the
     * reference's readCustomNbt over PaintingEntity/ItemFrameEntity):
     * the anchor decode, then the painting's {@code Motive} (an unknown
     * motive isolates loudly — the engine carries the reference's own
     * twenty-six) or the frame's {@code Item} + {@code ItemRotation}
     * (+ the legacy {@code Direction} form's rotation-doubling quirk,
     * ItemFrameEntity line 171-173). The exact-cell restore arms land
     * the bodies whole; the per-100-tick validation poll owns the
     * unsupported-wall drop exactly like the reference's own load. The
     * frame's {@code ItemDropChance} row has no per-frame surface (the
     * engine's fixed 1.0) — the ledger names it.
     */
    private void importDecoration(String id, Map<String, Snbt.Tag> rows) {
        var system = content == null ? null : content.decorations();
        if (system == null) {
            // No decoration surface wired: the rows count as not-imported.
            ledger.ignored("Entities (decoration rows)", 1);
            return;
        }
        // The Pos row is present on every entity save but the reference
        // IGNORES it for hanging bodies (shouldSetPositionOnLoad false —
        // the anchor rows derive the position); the decode walks the
        // anchor forms alone. A Pos-less row still reports the origin
        // for the isolation notes.
        List<Snbt.Tag> pos = NbtOps.getList(rows, "Pos", Snbt.Tag.Type.DOUBLE);
        Position reported = pos.size() == 3
                ? new Position(NbtOps.listDouble(pos, 0),
                        NbtOps.listDouble(pos, 1), NbtOps.listDouble(pos, 2))
                : new Position(0, 0, 0);
        DecorationAnchor anchor = decodeDecorationAnchor(id, reported, rows);
        if (anchor == null) {
            return;
        }
        if (id.equals("Painting")) {
            String motiveName = NbtOps.getString(rows, "Motive");
            if (motiveName.isEmpty()) {
                ledger.isolated("painting row without a Motive row",
                        "entity Painting at " + anchor.cell().x() + ","
                                + anchor.cell().y() + "," + anchor.cell().z());
                return;
            }
            Painting.Motive motive = Painting.Motive.byName(motiveName);
            if (motive == null) {
                ledger.isolated("painting motive not carried (the engine's own twenty-six)",
                        "entity Painting motive '" + motiveName + "'");
                return;
            }
            Painting landed = system.restorePainting(anchor.cell(), anchor.facing(), motive);
            if (landed == null) {
                ledger.isolated("painting anchor cell already carries a painting",
                        "entity Painting at " + anchor.cell().x() + ","
                                + anchor.cell().y() + "," + anchor.cell().z());
                return;
            }
            ledger.entityImported();
            return;
        }
        // The item frame: the displayed stack + the rotation (the
        // legacy Direction form doubles the rotation — the reference's
        // own old-format repair, ItemFrameEntity lines 171-173).
        int rotation = 0;
        if (NbtOps.containsNumeric(rows, "ItemRotation")) {
            rotation = NbtOps.getByte(rows, "ItemRotation");
        }
        boolean legacyDirectionForm = NbtOps.containsNumeric(rows, "Direction");
        net.zaminmc.torch.item.ItemStack display = null;
        if (NbtOps.contains(rows, "Item", Snbt.Tag.Type.COMPOUND)) {
            display = ItemNbt.read(NbtOps.getCompound(rows, "Item"));
        }
        if (display != null && legacyDirectionForm) {
            rotation = rotation * 2;
        }
        ItemFrame landed = system.restoreFrame(anchor.cell(), anchor.facing(), display, rotation);
        if (landed == null) {
            ledger.isolated("item frame anchor cell already carries a frame",
                    "entity ItemFrame at " + anchor.cell().x() + ","
                            + anchor.cell().y() + "," + anchor.cell().z());
            return;
        }
        if (NbtOps.containsNumeric(rows, "ItemDropChance")) {
            ledger.isolated("item frame's ItemDropChance row has no per-frame surface "
                            + "(the engine's fixed 1.0, the reference field's own initializer)",
                    "entity ItemFrame at " + anchor.cell().x() + ","
                            + anchor.cell().y() + "," + anchor.cell().z());
        }
        ledger.entityImported();
    }

    // ------------------------------------------------------------------ the XP-orb walk

    /**
     * One XP-orb row (the reference's ExperienceOrbEntity.readCustomNbt:
     * the Value short is the point total, the Age short resumes the
     * despawn clock; the Pos row is the spawn position): the exact-value
     * restore lands the saved amount whole — the burst arms' random
     * split never touches an imported orb. The {@code Health} row has
     * no engine surface (the orb model carries no wound clock) — the
     * ledger names it.
     */
    private GenericMountHandle importXpOrb(Position at, Map<String, Snbt.Tag> rows) {
        var manager = content == null ? null : content.orbs();
        if (manager == null) {
            // No orb surface wired: the rows count as not-imported.
            ledger.ignored("Entities (XP-orb rows)", 1);
            return null;
        }
        if (!NbtOps.containsNumeric(rows, "Value")) {
            ledger.isolated("XP orb row without a Value row",
                    "entity XPOrb at " + at.x() + "," + at.y() + "," + at.z());
            return null;
        }
        int value = NbtOps.getShort(rows, "Value");
        int age = NbtOps.containsNumeric(rows, "Age") ? NbtOps.getShort(rows, "Age") : 0;
        var landed = manager.spawnRestored(at, value, age);
        if (landed == null) {
            ledger.isolated("XP orb spawn withheld (a non-positive Value)",
                    "entity XPOrb at " + at.x() + "," + at.y() + "," + at.z()
                            + " Value " + value);
            return null;
        }
        if (NbtOps.containsNumeric(rows, "Health")) {
            ledger.isolated("XP orb's Health row has no engine surface "
                            + "(the orb model carries no wound clock)",
                    "entity XPOrb at " + at.x() + "," + at.y() + "," + at.z());
        }
        ledger.entityImported();
        return new GenericMountHandle(landed.entityId(),
                landed::position,
                () -> manager.all().stream().anyMatch(
                        live -> live.entityId() == landed.entityId()),
                MOUNT_HEIGHT_HALF); // ExperienceOrbEntity's 0.5F size
    }

    /**
     * One firework rocket row (the reference's FireworksEntity.readCustomNbt,
     * lines 151-163): the SAVED Life resumes the age clock (the loaded
     * rocket bursts after the REMAINING ticks, never a fresh roll), the
     * LifeTime row carries the whole fuse (0 default — the reference's
     * own getInt, a missing row bursts on the first tick), the
     * FireworksItem row becomes the payload (an absent row leaves the
     * empty stack — the reference's null synced slot 8), and the Motion
     * lands with the reference's own ±10 clamp. The Rotation rows carry
     * one counted note: the point model derives the angles from the
     * motion (the reference's own tick law — the loaded rotation never
     * survives the first flight step).
     */
    private GenericMountHandle importFirework(Position at, Map<String, Snbt.Tag> rows) {
        var system = content == null ? null : content.fireworks();
        if (system == null) {
            // No fireworks surface wired: the rows count as not-imported.
            ledger.ignored("Entities (firework rows)", 1);
            return null;
        }
        int life = NbtOps.getInt(rows, "Life");
        int lifeTime = NbtOps.getInt(rows, "LifeTime");
        net.zaminmc.torch.item.ItemStack payload =
                net.zaminmc.torch.item.ItemStack.EMPTY;
        if (NbtOps.contains(rows, "FireworksItem", Snbt.Tag.Type.COMPOUND)) {
            net.zaminmc.torch.item.ItemStack read = ItemNbt.read(
                    NbtOps.getCompound(rows, "FireworksItem"));
            if (read != null && !read.isEmpty()) {
                payload = read;
            }
        }
        double vx = 0.0;
        double vy = 0.0;
        double vz = 0.0;
        if (NbtOps.contains(rows, "Motion", Snbt.Tag.Type.LIST)) {
            List<Snbt.Tag> motion = NbtOps.getList(rows, "Motion", Snbt.Tag.Type.DOUBLE);
            vx = NbtOps.listDouble(motion, 0);
            vy = NbtOps.listDouble(motion, 1);
            vz = NbtOps.listDouble(motion, 2);
            // The reference's own clamp (Entity.readNbt lines 1156-1166):
            // a component beyond ±10 reads as 0.
            if (Math.abs(vx) > 10.0) {
                vx = 0.0;
            }
            if (Math.abs(vy) > 10.0) {
                vy = 0.0;
            }
            if (Math.abs(vz) > 10.0) {
                vz = 0.0;
            }
        }
        var rocket = system.launchRestored(at, vx, vy, vz, life, lifeTime, payload);
        ledger.entityImported();
        if (NbtOps.contains(rows, "Rotation", Snbt.Tag.Type.LIST)) {
            ledger.isolated("firework's Rotation row has no per-rocket surface "
                            + "(the point model derives the angles from the motion — "
                            + "the reference's own tick law)",
                    "entity FireworksRocketEntity at " + at.x() + "," + at.y() + "," + at.z());
        }
        return new GenericMountHandle(rocket.entityId(),
                rocket::position,
                () -> system.all().stream().anyMatch(
                        live -> live.entityId() == rocket.entityId()),
                MOUNT_HEIGHT_QUARTER); // FireworksEntity's 0.25F size
    }

    /**
     * One eye-of-ender row (the reference's readCustomNbt is EMPTY,
     * EnderEyeEntity lines 165-169: the eye carries no rows of its own;
     * the base Pos/Motion rows are the whole shape): the loaded Motion
     * lands with the ±10 clamp, the transient target stays the origin
     * (the reference's load quirk — a saved eye flies toward (0, 0, 0)
     * until its 80 ticks end), and the drops row is transient (a loaded
     * eye never drops — the 2003 event always ends it, the counted
     * note).
     */
    private GenericMountHandle importEye(Position at, Map<String, Snbt.Tag> rows) {
        var system = content == null ? null : content.eyes();
        if (system == null) {
            // No eye surface wired: the rows count as not-imported.
            ledger.ignored("Entities (eye rows)", 1);
            return null;
        }
        double vx = 0.0;
        double vy = 0.0;
        double vz = 0.0;
        if (NbtOps.contains(rows, "Motion", Snbt.Tag.Type.LIST)) {
            List<Snbt.Tag> motion = NbtOps.getList(rows, "Motion", Snbt.Tag.Type.DOUBLE);
            vx = NbtOps.listDouble(motion, 0);
            vy = NbtOps.listDouble(motion, 1);
            vz = NbtOps.listDouble(motion, 2);
            // The reference's own clamp (Entity.readNbt lines 1156-1166).
            if (Math.abs(vx) > 10.0) {
                vx = 0.0;
            }
            if (Math.abs(vy) > 10.0) {
                vy = 0.0;
            }
            if (Math.abs(vz) > 10.0) {
                vz = 0.0;
            }
        }
        var eye = system.launchRestored(at, vx, vy, vz);
        ledger.entityImported();
        ledger.isolated("eye's transient target and drops rows have no per-eye surface "
                        + "(the reference's load quirk: a saved eye flies toward the origin "
                        + "until its 80 ticks end, and never drops — the 2003 event "
                        + "always ends it)",
                "entity EyeOfEnderSignal at " + at.x() + "," + at.y() + "," + at.z());
        if (NbtOps.contains(rows, "Rotation", Snbt.Tag.Type.LIST)) {
            ledger.isolated("eye's Rotation row has no per-eye surface "
                            + "(the point model re-derives the angles from the motion "
                            + "on the first flight step)",
                    "entity EyeOfEnderSignal at " + at.x() + "," + at.y() + "," + at.z());
        }
        return new GenericMountHandle(eye.entityId(),
                eye::position,
                () -> system.all().stream().anyMatch(
                        live -> live.entityId() == eye.entityId()),
                MOUNT_HEIGHT_QUARTER); // EnderEyeEntity's 0.25F size
    }

    /**
     * One leash-knot row (the reference's LeadKnotEntity.readCustomNbt: the
     * Tile ints ARE the anchor — the knot hangs at the anchor's center
     * (+0.5), the survival poll owns the unsupported-anchor drop exactly
     * the decorations' load convention). The Direction row carries one
     * counted note: the knot's own facing is the down constant (the
     * reference's knot ignores it too).
     */
    private void importKnot(Map<String, Snbt.Tag> rows) {
        var leads = content == null ? null : content.leads();
        if (leads == null) {
            // No lead surface wired: the rows count as not-imported.
            ledger.ignored("Entities (knot rows)", 1);
            return;
        }
        if (!NbtOps.containsNumeric(rows, "TileX")
                || !NbtOps.containsNumeric(rows, "TileY")
                || !NbtOps.containsNumeric(rows, "TileZ")) {
            ledger.isolated("leash knot row without a Tile row",
                    "entity LeashKnot (no TileX/Y/Z)");
            return;
        }
        int x = NbtOps.getInt(rows, "TileX");
        int y = NbtOps.getInt(rows, "TileY");
        int z = NbtOps.getInt(rows, "TileZ");
        leads.restoreKnot(new net.zaminmc.torch.block.BlockPosition(x, y, z));
        ledger.entityImported();
        if (NbtOps.contains(rows, "Direction", Snbt.Tag.Type.BYTE)) {
            ledger.isolated("knot's Direction row has no per-knot surface "
                            + "(the knot's facing is the down constant — the reference's own)",
                    "entity LeashKnot at " + x + "," + y + "," + z);
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
