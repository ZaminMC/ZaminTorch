package net.zaminmc.torch.server.nbt;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tile NBT projections (Slice 89 p4 — the reference's
 * BlockEntity.writeNbt/readNbt over the engine's tile families): the id
 * registry 1:1, the per-family rows (chest/furnace/hopper/brewing/beacon/
 * sign/spawner/control/jukebox/skull), and the read walk's slot landing.
 * Every mutation rides the tick lane (the same context the commands run
 * in — the ownership contract).
 */
class TileNbtTest {

    @TempDir
    Path dataDir;

    EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "tilenbt" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new EngineServer(config);
        server.start();
        await(() -> server.world() != null, "the world booted");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private static void await(java.util.function.BooleanSupplier condition, String what)
            throws InterruptedException {
        for (int i = 0; i < 200 && !condition.getAsBoolean(); i++) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "await failed: " + what);
    }

    /** Runs the body on the tick lane and waits it out. */
    private void tick(Runnable body) throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        server.ticker().submit(() -> {
            try {
                body.run();
            } finally {
                done.set(true);
            }
        });
        await(done::get, "the tick task ran");
    }

    private Map<String, Snbt.Tag> writeAt(BlockPosition pos) throws Exception {
        return writeAt(pos, null);
    }

    private Map<String, Snbt.Tag> writeAt(BlockPosition pos, Runnable prepare) throws Exception {
        Map<String, Snbt.Tag> rows = new LinkedHashMap<>();
        AtomicBoolean ok = new AtomicBoolean();
        tick(() -> {
            if (prepare != null) {
                prepare.run();
            }
            ok.set(server.tileNbt().writeTile(server.world(), 0, pos, rows));
        });
        assertTrue(ok.get(), "the family resolved");
        return rows;
    }

    private void readAt(BlockPosition pos, Map<String, Snbt.Tag> rows) throws Exception {
        readAt(pos, rows, null);
    }

    private void readAt(BlockPosition pos, Map<String, Snbt.Tag> rows, Runnable after)
            throws Exception {
        tick(() -> {
            server.tileNbt().readTile(server.world(), 0, pos, rows);
            if (after != null) {
                after.run();
            }
        });
    }

    private BlockPosition place(String blockName) throws Exception {
        BlockPosition pos = new BlockPosition(
                8 + (int) (Math.random() * 8), 40, 8 + (int) (Math.random() * 8));
        tick(() -> server.world().setBlock(pos,
                server.world().blockRegistry().byKey("minecraft:" + blockName).orElseThrow()));
        await(() -> server.world().getBlock(pos).identifier().value().equals(blockName),
                "the " + blockName + " landed");
        return pos;
    }

    private static ItemStack item(String name, int count) {
        return ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.lookup(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:" + name)).orElseThrow(),
                count);
    }

    @Test
    void theIdRegistryMatchesTheReferenceTable() {
        assertEquals("Furnace", TileNbt.idOf("furnace"));
        assertEquals("Furnace", TileNbt.idOf("furnace_lit"));
        assertEquals("Chest", TileNbt.idOf("trapped_chest"));
        assertEquals("EnderChest", TileNbt.idOf("ender_chest"));
        assertEquals("RecordPlayer", TileNbt.idOf("jukebox"));
        assertEquals("Trap", TileNbt.idOf("dispenser"));
        assertEquals("Dropper", TileNbt.idOf("dropper"));
        assertEquals("Sign", TileNbt.idOf("sign"));
        assertEquals("Sign", TileNbt.idOf("wall_sign_south"));
        assertEquals("MobSpawner", TileNbt.idOf("mob_spawner"));
        assertEquals("Music", TileNbt.idOf("note_block"));
        assertEquals("Cauldron", TileNbt.idOf("brewing_stand"));
        assertEquals("EnchantTable", TileNbt.idOf("enchanting_table"));
        assertEquals("Airportal", TileNbt.idOf("end_portal"));
        assertEquals("Control", TileNbt.idOf("command_block"));
        assertEquals("Beacon", TileNbt.idOf("beacon"));
        assertEquals("Skull", TileNbt.idOf("skull"));
        assertEquals("Skull", TileNbt.idOf("skull_north"));
        assertEquals("DLDetector", TileNbt.idOf("daylight_detector"));
        assertEquals("Hopper", TileNbt.idOf("hopper_down"));
        assertEquals("Comparator", TileNbt.idOf("comparator_north_compare"));
        assertEquals("Comparator", TileNbt.idOf("powered_comparator_north_compare"));
        assertEquals("FlowerPot", TileNbt.idOf("flower_pot"));
        assertEquals("Banner", TileNbt.idOf("standing_banner"));
        assertEquals(null, TileNbt.idOf("stone"));
        assertEquals(null, TileNbt.idOf("grass"));
    }

    @Test
    void theChestRowsRoundTrip() throws Exception {
        BlockPosition pos = place("chest");
        tick(() -> {
            var chest = server.chests().getOrCreate(pos);
            chest.setSlot(0, item("stone", 7));
            chest.setSlot(26, item("diamond_sword", 1).withDamage(40));
        });

        Map<String, Snbt.Tag> rows = writeAt(pos);
        assertEquals("Chest", NbtOps.getString(rows, "id"));
        assertEquals(pos.x(), NbtOps.getInt(rows, "x"));
        assertEquals(pos.y(), NbtOps.getInt(rows, "y"));
        assertEquals(pos.z(), NbtOps.getInt(rows, "z"));
        java.util.List<Snbt.Tag> items = NbtOps.getList(rows, "Items", Snbt.Tag.Type.COMPOUND);
        assertEquals(2, items.size(), "only the non-empty slots write");
        Map<String, Snbt.Tag> slotZero = NbtOps.listCompound(items, 0);
        assertEquals(0, NbtOps.getByte(slotZero, "Slot"));
        assertEquals("minecraft:stone", NbtOps.getString(slotZero, "id"));
        assertEquals(7, NbtOps.getByte(slotZero, "Count"));
        Map<String, Snbt.Tag> slot26 = NbtOps.listCompound(items, 1);
        assertEquals(26, NbtOps.getByte(slot26, "Slot"));
        assertEquals(40, NbtOps.getShort(slot26, "Damage"));

        // The read onto a fresh chest: the slots land.
        BlockPosition fresh = place("chest");
        readAt(fresh, rows);
        tick(() -> { /* the reads below consult live state */ });
        var loaded = server.chests().getOrCreate(fresh);
        assertEquals("stone", loaded.getItem(0).type().identifier().value());
        assertEquals(7, loaded.getItem(0).count());
        assertEquals(40, loaded.getItem(26).damage());
    }

    @Test
    void theFurnaceHopperBrewingRowsRoundTrip() throws Exception {
        BlockPosition furnacePos = place("furnace");
        Map<String, Snbt.Tag> furnaceRows = writeAt(furnacePos, () -> {
            var furnace = server.furnaces().getOrCreate(furnacePos);
            furnace.setSlot(0, item("cobblestone", 3));
            furnace.setSlot(1, item("coal", 2));
            furnace.restore(40, 160, 60);
        });
        assertEquals("Furnace", NbtOps.getString(furnaceRows, "id"));
        assertEquals(40, NbtOps.getShort(furnaceRows, "BurnTime"));
        assertEquals(60, NbtOps.getShort(furnaceRows, "CookTime"));
        assertEquals(2, NbtOps.getList(furnaceRows, "Items", Snbt.Tag.Type.COMPOUND).size());

        BlockPosition hopperPos = place("hopper_down");
        Map<String, Snbt.Tag> hopperRows = writeAt(hopperPos, () -> {
            var hopper = server.hopper().getOrCreate(hopperPos);
            hopper.setItem(2, item("iron_ingot", 5));
            hopper.setCooldown(6);
        });
        assertEquals("Hopper", NbtOps.getString(hopperRows, "id"));
        assertEquals(6, NbtOps.getInt(hopperRows, "TransferCooldown"));
        assertEquals(1, NbtOps.getList(hopperRows, "Items", Snbt.Tag.Type.COMPOUND).size());

        BlockPosition brewingPos = place("brewing_stand");
        Map<String, Snbt.Tag> brewingRows = writeAt(brewingPos, () -> {
            var brewing = server.brewingStands().getOrCreate(brewingPos);
            brewing.setItem(0, item("potion", 1));
            brewing.restoreState(brewing.snapshotSlots(), 250);
        });
        assertEquals("Cauldron", NbtOps.getString(brewingRows, "id"));
        assertEquals(250, NbtOps.getShort(brewingRows, "BrewTime"));

        // The reads onto fresh tiles. The furnace ticks its burn down
        // every engine tick — the capture runs in the SAME tick task as
        // the read.
        BlockPosition freshFurnace = place("furnace");
        int[] burn = new int[1];
        int[] cook = new int[1];
        String[] fuel = new String[1];
        readAt(freshFurnace, furnaceRows, () -> {
            var loaded = server.furnaces().getOrCreate(freshFurnace);
            burn[0] = loaded.burnTimeRemaining();
            cook[0] = loaded.cookTime();
            fuel[0] = loaded.fuel().type().identifier().value();
        });
        assertEquals(40, burn[0]);
        assertEquals(60, cook[0]);
        assertEquals("coal", fuel[0]);
        BlockPosition freshHopper = place("hopper_down");
        int[] cooldown = new int[1];
        String[] ingot = new String[1];
        readAt(freshHopper, hopperRows, () -> {
            var loaded = server.hopper().getOrCreate(freshHopper);
            cooldown[0] = loaded.cooldown();
            ingot[0] = loaded.getItem(2).type().identifier().value();
        });
        assertEquals(6, cooldown[0]);
        assertEquals("iron_ingot", ingot[0]);
        BlockPosition freshBrewing = place("brewing_stand");
        int[] brewTime = new int[1];
        readAt(freshBrewing, brewingRows, () ->
                brewTime[0] = server.brewingStands().getOrCreate(freshBrewing).timer());
        // The engine tick zeroes the timer on the first non-brewable walk
        // (the reference's own rule); the capture saw the restored value.
        assertEquals(250, brewTime[0]);
    }

    @Test
    void theSpawnerControlSignBeaconRowsRoundTrip() throws Exception {
        // The spawner: the entity id + the delay.
        BlockPosition spawnerPos = place("mob_spawner");
        tick(() -> {
            var spawner = server.spawners().getOrCreate(0, spawnerPos);
            spawner.setType(MobType.ZOMBIE);
            spawner.setDelay(23);
        });
        Map<String, Snbt.Tag> spawnerRows = writeAt(spawnerPos);
        assertEquals("MobSpawner", NbtOps.getString(spawnerRows, "id"));
        assertEquals("Zombie", NbtOps.getString(spawnerRows, "EntityId"));
        assertEquals(23, NbtOps.getShort(spawnerRows, "Delay"));
        BlockPosition freshSpawner = place("mob_spawner");
        readAt(freshSpawner, spawnerRows);
        var loadedSpawner = server.spawners().getOrCreate(0, freshSpawner);
        assertEquals(MobType.ZOMBIE, loadedSpawner.type());
        assertEquals(23, loadedSpawner.delay());

        // The command block: the executor rows.
        BlockPosition controlPos = place("command_block");
        tick(() -> {
            var control = server.commandBlocks().createAt(controlPos, true);
            control.setCommand("say hi");
            control.setSuccessCount(3);
        });
        Map<String, Snbt.Tag> controlRows = writeAt(controlPos);
        assertEquals("Control", NbtOps.getString(controlRows, "id"));
        assertEquals("say hi", NbtOps.getString(controlRows, "Command"));
        assertEquals(3, NbtOps.getInt(controlRows, "SuccessCount"));
        assertTrue(NbtOps.getBoolean(controlRows, "TrackOutput"));
        BlockPosition freshControl = place("command_block");
        readAt(freshControl, controlRows);
        var loadedControl = server.commandBlocks().at(freshControl);
        assertNotNull(loadedControl);
        assertEquals("say hi", loadedControl.command());
        assertEquals(3, loadedControl.successCount());

        // The sign: the text rows.
        BlockPosition signPos = place("sign");
        tick(() -> server.signs().set(signPos,
                new String[]{"Line one", "Line two", "Line three", "Line four"}));
        Map<String, Snbt.Tag> signRows = writeAt(signPos);
        assertEquals("Sign", NbtOps.getString(signRows, "id"));
        assertEquals("Line one", NbtOps.getString(signRows, "Text1"));
        assertEquals("Line four", NbtOps.getString(signRows, "Text4"));
        BlockPosition freshSign = place("sign");
        tick(() -> server.signs().set(freshSign, new String[]{"", "", "", ""}));
        readAt(freshSign, signRows);
        assertEquals("Line two", server.signs().peek(freshSign)[1]);

        // The beacon: the levels + the effect pair.
        BlockPosition beaconPos = place("beacon");
        tick(() -> {
            var beacon = server.beacons().getOrCreate(beaconPos);
            beacon.setLevels(2);
            beacon.setPrimary(1);
            beacon.setSecondary(3);
        });
        Map<String, Snbt.Tag> beaconRows = writeAt(beaconPos);
        assertEquals("Beacon", NbtOps.getString(beaconRows, "id"));
        assertEquals(1, NbtOps.getInt(beaconRows, "Primary"));
        assertEquals(3, NbtOps.getInt(beaconRows, "Secondary"));
        assertEquals(2, NbtOps.getInt(beaconRows, "Levels"));
        BlockPosition freshBeacon = place("beacon");
        readAt(freshBeacon, beaconRows);
        assertEquals(3, server.beacons().getOrCreate(freshBeacon).secondary());
    }

    @Test
    void theJukeboxAndSkullRowsRoundTrip() throws Exception {
        BlockPosition jukeboxPos = place("jukebox");
        tick(() -> server.jukeboxes().restoreRecord(jukeboxPos, item("record_cat", 1)));
        Map<String, Snbt.Tag> jukeboxRows = writeAt(jukeboxPos);
        assertEquals("RecordPlayer", NbtOps.getString(jukeboxRows, "id"));
        assertEquals("minecraft:record_cat",
                NbtOps.getString(NbtOps.getCompound(jukeboxRows, "RecordItem"), "id"));
        BlockPosition freshJukebox = place("jukebox");
        readAt(freshJukebox, jukeboxRows);
        assertEquals("minecraft:record_cat",
                server.jukeboxes().recordAt(freshJukebox).type().identifier().toString());

        BlockPosition skullPos = place("skull");
        tick(() -> server.skulls().place(skullPos, 2, 11));
        Map<String, Snbt.Tag> skullRows = writeAt(skullPos);
        assertEquals("Skull", NbtOps.getString(skullRows, "id"));
        assertEquals(2, NbtOps.getByte(skullRows, "SkullType"));
        assertEquals(11, NbtOps.getByte(skullRows, "Rotation"));
        BlockPosition freshSkull = place("skull");
        readAt(freshSkull, skullRows);
        assertEquals(11, server.skulls().tileAt(freshSkull).rotation());
        assertEquals(2, server.skulls().tileAt(freshSkull).type());
    }

    @Test
    void theNoTileFamilyRefuses() throws Exception {
        BlockPosition stone = place("stone");
        Map<String, Snbt.Tag> rows = new LinkedHashMap<>();
        AtomicBoolean resolved = new AtomicBoolean(true);
        tick(() -> resolved.set(server.tileNbt().writeTile(server.world(), 0, stone, rows)));
        assertTrue(!resolved.get(), "stone carries no tile");
    }
    /**
     * Slice 91 Wave C — the spawner's six cadence rows: the typed
     * projection writes them (MobSpawner.writeNbt lines 231-236), the read
     * lands them back, and the PAIRED gates carry the reference's own
     * zeroing quirk (readNbt lines 197-205: MinSpawnDelay present reads
     * ALL THREE rows - an absent partner lands as its short 0).
     */
    @Test
    void theSpawnerCadenceRowsRoundTrip() throws Exception {
        BlockPosition pos = place("mob_spawner");
        tick(() -> {
            var spawner = server.spawners().getOrCreate(0, pos);
            spawner.setType(MobType.ZOMBIE);
            spawner.setMinSpawnDelay(5);
            spawner.setMaxSpawnDelay(6);
            spawner.setSpawnCount(1);
            spawner.setMaxNearbyEntities(2);
            spawner.setRequiredPlayerRange(32);
            spawner.setSpawnRange(2);
        });
        Map<String, Snbt.Tag> rows = writeAt(pos);
        assertEquals("MobSpawner", NbtOps.getString(rows, "id"));
        assertEquals(5, NbtOps.getShort(rows, "MinSpawnDelay"));
        assertEquals(6, NbtOps.getShort(rows, "MaxSpawnDelay"));
        assertEquals(1, NbtOps.getShort(rows, "SpawnCount"));
        assertEquals(2, NbtOps.getShort(rows, "MaxNearbyEntities"));
        assertEquals(32, NbtOps.getShort(rows, "RequiredPlayerRange"));
        assertEquals(2, NbtOps.getShort(rows, "SpawnRange"));
        // The full round-trip onto a fresh spawner.
        BlockPosition fresh = place("mob_spawner");
        readAt(fresh, rows);
        var loaded = server.spawners().getOrCreate(0, fresh);
        assertEquals(5, loaded.minSpawnDelay());
        assertEquals(6, loaded.maxSpawnDelay());
        assertEquals(1, loaded.spawnCount());
        assertEquals(2, loaded.maxNearbyEntities());
        assertEquals(32, loaded.requiredPlayerRange());
        assertEquals(2, loaded.spawnRange());
        // The PAIRED-GATE QUIRK: a compound carrying MinSpawnDelay alone
        // (the other rows removed - the /blockdata partial-write shape)
        // reads ALL THREE - the absent partners land as their short 0,
        // while the OTHER pair (no MaxNearbyEntities row) keeps its
        // defaults.
        Map<String, Snbt.Tag> partial = new java.util.LinkedHashMap<>(rows);
        partial.remove("MaxSpawnDelay");
        partial.remove("SpawnCount");
        partial.remove("MaxNearbyEntities");
        partial.remove("RequiredPlayerRange");
        partial.remove("SpawnRange");
        BlockPosition quirkPos = place("mob_spawner");
        readAt(quirkPos, partial);
        var quirked = server.spawners().getOrCreate(0, quirkPos);
        assertEquals(5, quirked.minSpawnDelay(), "the present row lands");
        assertEquals(0, quirked.maxSpawnDelay(),
                "the absent partner reads its short 0 - the reference's own quirk");
        assertEquals(0, quirked.spawnCount(), "the third partner too");
        assertEquals(6, quirked.maxNearbyEntities(),
                "the OTHER pair keeps its default (no MaxNearbyEntities row)");
        assertEquals(16, quirked.requiredPlayerRange());
        assertEquals(4, quirked.spawnRange());
    }
    /**
     * Slice 91 Wave C p3 — the containers' CustomName/Lock rows: the write
     * gates (CustomName only when set, Lock ALWAYS — the reference's lock
     * object never null), the read gates (the string-typed contains checks),
     * and the round-trip onto a fresh chest.
     */
    @Test
    void theContainerNameLockRowsRoundTrip() throws Exception {
        BlockPosition pos = place("chest");
        tick(() -> {
            var chest = server.chests().getOrCreate(pos);
            chest.setCustomName("My Vault");
            chest.setLockKey("Secret");
        });
        Map<String, Snbt.Tag> rows = writeAt(pos);
        assertEquals("My Vault", NbtOps.getString(rows, "CustomName"));
        assertEquals("Secret", NbtOps.getString(rows, "Lock"));

        BlockPosition fresh = place("chest");
        readAt(fresh, rows);
        var loaded = server.chests().getOrCreate(fresh);
        assertEquals("My Vault", loaded.customName());
        assertEquals("Secret", loaded.lockKey());

        // An UNLOCKED chest still writes the Lock row (InventoryLock.NONE's
        // own empty key) and never a CustomName one.
        BlockPosition plain = place("chest");
        Map<String, Snbt.Tag> plainRows = writeAt(plain);
        assertFalse(plainRows.containsKey("CustomName"), "no custom name row when unset");
        assertEquals("", NbtOps.getString(plainRows, "Lock"), "the NONE lock writes its empty key");

        // The read gates: a Lock-only compound keeps the name unset, and an
        // absent Lock keeps the field's own value.
        BlockPosition lockOnly = place("chest");
        readAt(lockOnly, Snbt.parseCompound("{Lock:\"Open\"}"));
        var lockLoaded = server.chests().getOrCreate(lockOnly);
        assertEquals("Open", lockLoaded.lockKey());
        assertEquals(null, lockLoaded.customName(), "no CustomName row: the name stays unset");
    }
}
