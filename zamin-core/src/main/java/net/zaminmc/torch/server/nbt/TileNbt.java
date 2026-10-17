package net.zaminmc.torch.server.nbt;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.beacon.BeaconSystem;
import net.zaminmc.torch.server.brewing.BrewingStandSystem;
import net.zaminmc.torch.server.chest.ChestManager;
import net.zaminmc.torch.server.commandblock.CommandBlockManager;
import net.zaminmc.torch.server.dispenser.DispenserSystem;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.furnace.FurnaceManager;
import net.zaminmc.torch.server.hopper.HopperSystem;
import net.zaminmc.torch.server.jukebox.JukeboxBlockSystem;
import net.zaminmc.torch.server.noteblock.NoteBlockSystem;
import net.zaminmc.torch.server.sign.SignManager;
import net.zaminmc.torch.server.skull.SkullManager;
import net.zaminmc.torch.server.spawner.SpawnerManager;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The tile NBT projections (Slice 89 p4 — the reference's
 * {@code BlockEntity.writeNbt}/{@code readNbt} over the engine's tile
 * families): the id registry 1:1 (the reference's TYPE_TO_ID table), the
 * block-identity resolution, and the per-family rows.
 *
 * <p><b>The base rows</b> (the reference's BlockEntity.writeNbt): the id
 * STRING plus the x/y/z INTs — every family writes them, every family
 * reads them back.</p>
 *
 * <p><b>The per-family rows</b> (the reference's own writeNbt bodies):
 * the chest's Items (27), the furnace's BurnTime/CookTime/CookTimeTotal +
 * Items (3), the hopper's Items (5) + TransferCooldown, the
 * dispenser/dropper's Items (9), the brewing stand's BrewTime + Items
 * (4), the beacon's Levels/Primary/Secondary, the sign's Text1..Text4,
 * the spawner's EntityId + Delay, the command block's
 * Command/SuccessCount/TrackOutput/LastOutput + the CommandStats ride,
 * the jukebox's RecordItem, the skull's SkullType + Rotation.</p>
 *
 * <p><b>The engine's standing envelopes</b> (the id resolves — the
 * reference's own registration — the state rows land in no model):
 * EnderChest (the contents live on the player), the containers'
 * CustomName/Lock rows (no lock model), the spawner's cadence rows
 * LANDED into the model (Slice 91 Wave C — the six rows steer each
 * spawner's own walk: the paired read gates carry the reference's own
 * zeroing quirk, and the per-spawner delay band, spawn count, nearby
 * cap, player range, and spawn range ride the MobSpawnerBlockEntity),
 * the
 * comparator's OutputSignal (no stored-output model), the daylight
 * detector (the inverted block state is the whole model), the
 * enchanting table and the end portal (no state rows in the reference
 * either), the piston's moving-block state (the engine's in-flight
 * carrier is its own model), the flower pot (the block state is the
 * whole model), the banner (no model), the skull's Owner profile (the
 * engine's skull carries type + rotation alone). Ledgered.</p>
 */
public final class TileNbt {

    /**
     * The seams (the engine's tile managers — one record so the command
     * layer constructs once).
     */
    public record Seams(
            ChestManager chests,
            FurnaceManager furnaces,
            HopperSystem hoppers,
            DispenserSystem dispensers,
            BrewingStandSystem brewing,
            BeaconSystem beacons,
            SignManager signs,
            SpawnerManager spawners,
            CommandBlockManager commandBlocks,
            NoteBlockSystem noteBlocks,
            SkullManager skulls,
            JukeboxBlockSystem jukeboxes) {
    }

    private final Seams seams;

    public TileNbt(Seams seams) {
        this.seams = seams;
    }

    // ------------------------------------------------------------------
    // The id registry (the reference's TYPE_TO_ID, resolved by the block
    // identity at the position)
    // ------------------------------------------------------------------

    /**
     * The reference's tile id for the block name, or null when the block
     * family carries no tile (the reference's hasBlockEntity gate).
     */
    public static String idOf(String blockName) {
        // The variant families first (the engine's prefixed names).
        if (blockName.equals("sign") || blockName.startsWith("wall_sign")) {
            return "Sign";
        }
        if (blockName.equals("skull") || blockName.startsWith("skull_")) {
            return "Skull";
        }
        if (blockName.startsWith("comparator")
                || blockName.startsWith("powered_comparator")) {
            return "Comparator"; // the unpowered and powered pairs alike
        }
        if (blockName.startsWith("hopper")) {
            return "Hopper";
        }
        if (blockName.startsWith("brewing_stand")) {
            return "Cauldron"; // the plain stand and the has-bottle variants
        }
        if (blockName.equals("banner") || blockName.equals("standing_banner")
                || blockName.equals("wall_banner")) {
            return "Banner";
        }
        return switch (blockName) {
            case "furnace", "furnace_lit" -> "Furnace";
            case "chest", "trapped_chest" -> "Chest";
            case "ender_chest" -> "EnderChest";
            case "jukebox", "jukebox_record" -> "RecordPlayer";
            case "dispenser" -> "Trap";
            case "dropper" -> "Dropper";
            case "mob_spawner" -> "MobSpawner";
            case "note_block" -> "Music";
            case "piston", "moving_piston", "sticky_piston", "moving_sticky_piston" -> "Piston";
            case "enchanting_table" -> "EnchantTable";
            case "end_portal" -> "Airportal";
            case "command_block", "chain_command_block", "repeating_command_block" -> "Control";
            case "beacon" -> "Beacon";
            case "daylight_detector" -> "DLDetector";
            case "flower_pot" -> "FlowerPot";
            default -> null;
        };
    }

    /**
     * Writes the tile's rows at the position (the family resolved by the
     * block identity — the engine's lazy getOrCreate stands in for the
     * reference's placement-created tile).
     *
     * @param dimension the world's own id (the spawner's own dimension
     *                  filter — the engine's one global map keyed by
     *                  position needs it)
     * @return false when the block family carries no tile at all (the
     *         reference's hasBlockEntity gate), true otherwise.
     */
    public boolean writeTile(EngineWorld world, int dimension, BlockPosition pos,
            Map<String, Snbt.Tag> rows) {
        String blockName = world.getBlock(pos).identifier().value();
        String id = idOf(blockName);
        if (id == null) {
            return false;
        }
        NbtOps.putString(rows, "id", id);
        NbtOps.putInt(rows, "x", pos.x());
        NbtOps.putInt(rows, "y", pos.y());
        NbtOps.putInt(rows, "z", pos.z());
        switch (id) {
            case "Chest" -> {
                var chest = seams.chests().getOrCreate(pos);
                writeItems(rows, chest.snapshotSlots());
                writeNameLockRows(rows, chest.customName(), chest.lockKey());
            }
            case "Furnace" -> {
                var furnace = seams.furnaces().getOrCreate(pos);
                NbtOps.putShort(rows, "BurnTime", (short) furnace.burnTimeRemaining());
                NbtOps.putShort(rows, "CookTime", (short) furnace.cookTime());
                NbtOps.putShort(rows, "CookTimeTotal", (short) furnace.burnTimeTotal());
                writeItems(rows, new ItemStack[]{
                        furnace.input(), furnace.fuel(), furnace.output()});
                writeNameLockRows(rows, furnace.customName(), furnace.lockKey());
            }
            case "Hopper" -> {
                var hopper = seams.hoppers().getOrCreate(pos);
                ItemStack[] slots = new ItemStack[5];
                for (int i = 0; i < 5; i++) {
                    slots[i] = hopper.getItem(i);
                }
                writeItems(rows, slots);
                NbtOps.putInt(rows, "TransferCooldown", hopper.cooldown());
                writeNameLockRows(rows, hopper.customName(), hopper.lockKey());
            }
            case "Trap", "Dropper" -> {
                var dispenser = seams.dispensers().getOrCreate(pos);
                ItemStack[] slots = new ItemStack[9];
                for (int i = 0; i < 9; i++) {
                    slots[i] = dispenser.getItem(i);
                }
                writeItems(rows, slots);
                writeNameLockRows(rows, dispenser.customName(), dispenser.lockKey());
            }
            case "Cauldron" -> {
                var brewing = seams.brewing().getOrCreate(pos);
                ItemStack[] slots = new ItemStack[4];
                for (int i = 0; i < 4; i++) {
                    slots[i] = brewing.getItem(i);
                }
                writeItems(rows, slots);
                NbtOps.putShort(rows, "BrewTime", (short) brewing.timer());
                writeNameLockRows(rows, brewing.customName(), brewing.lockKey());
            }
            case "Beacon" -> {
                var beacon = seams.beacons().getOrCreate(pos);
                NbtOps.putInt(rows, "Primary", beacon.primary());
                NbtOps.putInt(rows, "Secondary", beacon.secondary());
                NbtOps.putInt(rows, "Levels", beacon.levels());
            }
            case "Sign" -> {
                String[] lines = seams.signs().peek(pos);
                if (lines != null) {
                    for (int i = 0; i < 4; i++) {
                        NbtOps.putString(rows, "Text" + (i + 1), lines[i] == null ? "" : lines[i]);
                    }
                }
            }
            case "MobSpawner" -> {
                var spawner = seams.spawners().getOrCreate(dimension, pos);
                // writeNbt lines 228-237: the whole projection rides the
                // non-empty type gate (an untyped spawner writes NOTHING).
                if (spawner.type() != null) {
                    NbtOps.putString(rows, "EntityId", spawner.type().vanillaName());
                    NbtOps.putShort(rows, "Delay", (short) spawner.delay());
                    NbtOps.putShort(rows, "MinSpawnDelay", (short) spawner.minSpawnDelay());
                    NbtOps.putShort(rows, "MaxSpawnDelay", (short) spawner.maxSpawnDelay());
                    NbtOps.putShort(rows, "SpawnCount", (short) spawner.spawnCount());
                    NbtOps.putShort(rows, "MaxNearbyEntities", (short) spawner.maxNearbyEntities());
                    NbtOps.putShort(rows, "RequiredPlayerRange",
                            (short) spawner.requiredPlayerRange());
                    NbtOps.putShort(rows, "SpawnRange", (short) spawner.spawnRange());
                }
            }
            case "Control" -> {
                var state = seams.commandBlocks().at(pos);
                if (state != null) {
                    NbtOps.putString(rows, "Command", state.command());
                    NbtOps.putInt(rows, "SuccessCount", state.successCount());
                    NbtOps.putBoolean(rows, "TrackOutput", state.trackOutput());
                    if (state.lastOutput() != null) {
                        NbtOps.putString(rows, "LastOutput", state.lastOutput());
                    }
                    state.commandResults().writeNbt(rows);
                }
            }
            case "RecordPlayer" -> {
                ItemStack record = seams.jukeboxes().recordAt(pos);
                if (record != null && !record.isEmpty()) {
                    Map<String, Snbt.Tag> recordRows = new java.util.LinkedHashMap<>();
                    ItemNbt.write(record, recordRows);
                    NbtOps.putCompound(rows, "RecordItem", recordRows);
                }
            }
            case "Skull" -> {
                var skull = seams.skulls().tileAt(pos);
                if (skull != null) {
                    NbtOps.putByte(rows, "SkullType", (byte) skull.type());
                    NbtOps.putByte(rows, "Rotation", (byte) skull.rotation());
                }
            }
            // Music (the note lives in the block state), EnderChest,
            // EnchantTable, Airportal, DLDetector, Comparator, Piston,
            // FlowerPot, Banner: the engine's standing envelopes — the
            // id/x/y/z rows alone.
            default -> { }
        }
        return true;
    }

    /**
     * Reads the tile's rows back at the position (the family resolved by
     * the block identity; the lazy getOrCreate stands in for the
     * reference's placement-created tile).
     */
    public void readTile(EngineWorld world, int dimension, BlockPosition pos,
            Map<String, Snbt.Tag> rows) {
        String blockName = world.getBlock(pos).identifier().value();
        String id = idOf(blockName);
        if (id == null) {
            return;
        }
        switch (id) {
            case "Chest" -> {
                var chest = seams.chests().getOrCreate(pos);
                readItems(rows, (slot, stack) ->
                        chest.setSlot(slot, stack == null ? ItemStack.EMPTY : stack), 27);
                readNameLockRows(rows, chest::setCustomName, chest::setLockKey);
            }
            case "Furnace" -> {
                var furnace = seams.furnaces().getOrCreate(pos);
                readItems(rows, (slot, stack) ->
                        furnace.setSlot(slot, stack == null ? ItemStack.EMPTY : stack), 3);
                furnace.restore(
                        NbtOps.getShort(rows, "BurnTime"),
                        furnace.burnTimeTotal(),
                        NbtOps.getShort(rows, "CookTime"));
                readNameLockRows(rows, furnace::setCustomName, furnace::setLockKey);
            }
            case "Hopper" -> {
                var hopper = seams.hoppers().getOrCreate(pos);
                readItems(rows, (slot, stack) ->
                        hopper.setItem(slot, stack), 5);
                hopper.setCooldown(NbtOps.getInt(rows, "TransferCooldown"));
                readNameLockRows(rows, hopper::setCustomName, hopper::setLockKey);
            }
            case "Trap", "Dropper" -> {
                var dispenser = seams.dispensers().getOrCreate(pos);
                readItems(rows, (slot, stack) ->
                        dispenser.setItem(slot, stack), 9);
                readNameLockRows(rows, dispenser::setCustomName, dispenser::setLockKey);
            }
            case "Cauldron" -> {
                var brewing = seams.brewing().getOrCreate(pos);
                readItems(rows, (slot, stack) ->
                        brewing.setItem(slot, stack), 4);
                brewing.restoreState(brewing.snapshotSlots(),
                        NbtOps.getShort(rows, "BrewTime"));
                readNameLockRows(rows, brewing::setCustomName, brewing::setLockKey);
            }
            case "Beacon" -> {
                var beacon = seams.beacons().getOrCreate(pos);
                beacon.setPrimary(NbtOps.getInt(rows, "Primary"));
                beacon.setSecondary(NbtOps.getInt(rows, "Secondary"));
                beacon.setLevels(NbtOps.getInt(rows, "Levels"));
            }
            case "Sign" -> {
                String[] lines = new String[4];
                for (int i = 0; i < 4; i++) {
                    lines[i] = NbtOps.getString(rows, "Text" + (i + 1));
                }
                if (seams.signs().peek(pos) != null) {
                    seams.signs().set(pos, lines);
                }
            }
            case "MobSpawner" -> {
                var spawner = seams.spawners().getOrCreate(dimension, pos);
                String entityName = NbtOps.getString(rows, "EntityId");
                if (!entityName.isEmpty()) {
                    MobType type = MobType.byVanillaName(entityName);
                    if (type == null) {
                        type = MobType.byName(entityName);
                    }
                    if (type != null) {
                        spawner.setType(type);
                    }
                }
                spawner.setDelay(NbtOps.getShort(rows, "Delay"));
                // readNbt lines 197-213 — the PAIRED gates (the reference's
                // own shape): MinSpawnDelay present reads ALL THREE rows
                // (an absent partner reads its short 0 — the reference's
                // own zeroing quirk, kept 1:1), MaxNearbyEntities present
                // reads the range pair, SpawnRange reads alone.
                if (NbtOps.containsNumeric(rows, "MinSpawnDelay")) {
                    spawner.setMinSpawnDelay(NbtOps.getShort(rows, "MinSpawnDelay"));
                    spawner.setMaxSpawnDelay(NbtOps.getShort(rows, "MaxSpawnDelay"));
                    spawner.setSpawnCount(NbtOps.getShort(rows, "SpawnCount"));
                }
                if (NbtOps.containsNumeric(rows, "MaxNearbyEntities")) {
                    spawner.setMaxNearbyEntities(NbtOps.getShort(rows, "MaxNearbyEntities"));
                    spawner.setRequiredPlayerRange(NbtOps.getShort(rows, "RequiredPlayerRange"));
                }
                if (NbtOps.containsNumeric(rows, "SpawnRange")) {
                    spawner.setSpawnRange(NbtOps.getShort(rows, "SpawnRange"));
                }
            }
            case "Control" -> {
                var state = seams.commandBlocks().at(pos);
                if (state == null) {
                    state = seams.commandBlocks().createAt(pos, true);
                }
                state.setCommand(NbtOps.getString(rows, "Command"));
                state.setSuccessCount(NbtOps.getInt(rows, "SuccessCount"));
                state.setTrackOutput(NbtOps.getBoolean(rows, "TrackOutput"));
                if (NbtOps.contains(rows, "LastOutput", Snbt.Tag.Type.STRING)) {
                    state.recordOutput(NbtOps.getString(rows, "LastOutput"));
                }
                state.commandResults().readNbt(rows);
            }
            case "RecordPlayer" -> {
                ItemStack record = ItemNbt.read(NbtOps.getCompound(rows, "RecordItem"));
                if (record != null) {
                    seams.jukeboxes().restoreRecord(pos, record);
                }
            }
            case "Skull" -> {
                seams.skulls().place(pos,
                        NbtOps.getByte(rows, "SkullType"),
                        NbtOps.getByte(rows, "Rotation"));
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    // The Items rows (the reference's shared container form)
    // ------------------------------------------------------------------

    /** The reference's Items list: one row per non-empty slot. */
    private static void writeItems(Map<String, Snbt.Tag> rows, ItemStack[] slots) {
        List<Snbt.Tag> items = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && !slots[i].isEmpty()) {
                items.add(Snbt.Tag.ofCompound(ItemNbt.slotRows(i, slots[i])));
            }
        }
        NbtOps.putList(rows, "Items", items);
    }

    /** The reference's read walk: each row's Slot byte lands its stack. */
    private interface SlotWriter {
        void set(int slot, ItemStack stack);
    }

    private static void readItems(Map<String, Snbt.Tag> rows, SlotWriter writer, int slotCount) {
        List<Snbt.Tag> items = NbtOps.getList(rows, "Items", Snbt.Tag.Type.COMPOUND);
        for (Snbt.Tag row : items) {
            Map<String, Snbt.Tag> compound = row != null ? row.asCompound() : null;
            if (compound == null) {
                continue;
            }
            int slot = NbtOps.getByte(compound, "Slot");
            if (slot < 0 || slot >= slotCount) {
                continue; // the reference's size-limited walk skips the rest
            }
            writer.set(slot, ItemNbt.read(compound));
        }
    }

    /**
     * The containers' shared rows (Slice 91 Wave C p3 — the reference's
     * per-family Nameable write: CustomName only when set, lines 141-143's
     * own gate; and the InventoryBlockEntity base's Lock write, which
     * ALWAYS lands — the lock object is never null, so even the NONE lock
     * writes its empty key row, InventoryLock.writeNbt's own shape).
     */
    private static void writeNameLockRows(Map<String, Snbt.Tag> rows, String customName,
            String lockKey) {
        if (customName != null && !customName.isEmpty()) {
            NbtOps.putString(rows, "CustomName", customName);
        }
        NbtOps.putString(rows, "Lock", lockKey == null ? "" : lockKey);
    }

    /**
     * The containers' shared read (the per-family contains("CustomName", 8)
     * gate + the base's InventoryLock.fromNbt — the string-typed Lock row;
     * an absent row keeps the field's own value, a present one lands even
     * when empty).
     */
    private static void readNameLockRows(Map<String, Snbt.Tag> rows,
            java.util.function.Consumer<String> nameSetter,
            java.util.function.Consumer<String> lockSetter) {
        if (NbtOps.contains(rows, "CustomName", Snbt.Tag.Type.STRING)) {
            nameSetter.accept(NbtOps.getString(rows, "CustomName"));
        }
        if (NbtOps.contains(rows, "Lock", Snbt.Tag.Type.STRING)) {
            lockSetter.accept(NbtOps.getString(rows, "Lock"));
        }
    }

    // ------------------------------------------------------------------
    // The placement consult (Wave H2 — BlockItem.setTileNbt, lines 70-100)
    // ------------------------------------------------------------------

    /**
     * The reference's requireOpForPlacingWithNbt families: the three
     * overriding bodies (SignBlockEntity 122, CommandBlockBlockEntity 82,
     * MobSpawnerBlockEntity 69 — each {@code return true}); the base BE
     * answers false. The engine's own id registry names them.
     */
    private static final java.util.Set<String> REQUIRE_OP_FAMILIES =
            java.util.Set.of("Sign", "Control", "MobSpawner");

    /**
     * The placement's BlockEntityTag consult — BlockItem.setTileNbt 1:1
     * (lines 70-100): the freshly-placed tile's written rows merged with
     * the stack's tag rows, x/y/z forced back, the no-change refusal, and
     * the re-read into the tile. Called by the interaction service right
     * after its commit (the TilePlacementListener has already created the
     * tile family's model during the world mutation).
     *
     * <p><b>The 1.8.8 quirk, ported verbatim:</b> the pristine copy is
     * taken BEFORE the write (lines 83-84), so the no-change refusal can
     * never fire — x/y/z always land in the merged compound, the
     * {@code !equals} is always true, and the read + markDirty always
     * runs. The refusal is dead code in 1.8.8 (fixed in 1.9+ by copying
     * after the write); the port keeps the dead branch and this note.
     *
     * <p>The op gate (line 79): the three requireOp families refuse the
     * merge for a non-op placer — the reference's {@code isOp} is the ops
     * list membership; the engine's op level 2 stands in (the convention
     * every vanilla command uses). A refusal only skips the NBT apply:
     * the placement itself already committed, exactly like the reference
     * (setTileNbt runs after setBlockState succeeded).</p>
     *
     * @param dimension the world's own id (the spawner family's key)
     * @return true when the rows were merged and re-read into the tile
     */
    public boolean applyPlacementTag(EngineWorld world, int dimension, BlockPosition pos,
            net.zaminmc.torch.item.ItemStack stack, int opLevel) {
        if (stack == null || stack.isEmpty()
                || stack.blockEntityTag() == null || stack.blockEntityTag().isEmpty()) {
            return false; // hasNbt() && contains("BlockEntityTag", 10): no
        }
        String blockName = world.getBlock(pos).identifier().value();
        String id = idOf(blockName);
        if (id == null) {
            return false; // the tile at the position: no tile family, no merge
        }
        if (REQUIRE_OP_FAMILIES.contains(id) && opLevel < 2) {
            return false; // requireOpForPlacingWithNbt && !isOp
        }
        Map<String, Snbt.Tag> rows = new java.util.LinkedHashMap<>();
        // THE 1.8.8 QUIRK: the copy precedes the write (lines 83-84) — the
        // refusal below is unreachable; kept verbatim with the note above.
        Map<String, Snbt.Tag> pristine = NbtOps.copy(rows);
        writeTile(world, dimension, pos, rows);
        NbtOps.merge(rows, NbtOps.fromBoxed(stack.blockEntityTag()));
        NbtOps.putInt(rows, "x", pos.x());
        NbtOps.putInt(rows, "y", pos.y());
        NbtOps.putInt(rows, "z", pos.z());
        if (!NbtOps.equal(rows, pristine)) {
            readTile(world, dimension, pos, rows);
            // markDirty: the engine's tiles ARE the live model — the read's
            // mutations land straight into the managers; the Z-family
            // stores snapshot them at saveAllNow (the reference's dirty
            // flag exists to reach exactly this guarantee).
            return true;
        }
        return false;
    }
}
