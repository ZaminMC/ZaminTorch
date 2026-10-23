package net.zaminmc.torch.server.dispenser;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.block.SkullBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dispenser row's ledgered behavior tails (the Slice 11a style — the
 * same live-engine acceptance as DispenserSystemTest): the spawn-egg, the
 * firework rocket, the boat, the minecart family, the armor equip, the
 * skull and the pumpkin arms, each against its own reference law —
 * Bootstrap lines 125-141 / 142-160 / 182-219 / 340-392 / 393-421,
 * MinecartItem lines 18-77, ArmorItem lines 32-63.
 *
 * <p>Every dispenser faces NORTH with its power mounted on the east side
 * (the stone + lever the Slice 11a tests established) so the facing cell
 * — the north neighbor — stays free for the arm's own world state.</p>
 */
class DispenserBehaviorTailsTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The spawn egg (Bootstrap lines 125-141)
    // ------------------------------------------------------------------

    @Test
    void theDispensedSpawnEggSpawnsItsBodyOneBlockOut() throws Exception {
        EngineServer server = boot();
        try {
            int x = 20, z = 60, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                server.world().setBlock(dispenser,
                        DispenserBlocks.dispenserOf(2, false)); // facing NORTH
                // The villager's own egg (the damage IS the entity id, 120).
                server.dispensers().getOrCreate(dispenser).setItem(0,
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.SPAWN_EGG, 2)
                                .withDamage(120));
                // A floor under the facing cell: the body lands and STAYS
                // where the arm put it (the flat world's ground sits far
                // below the fixture band).
                server.world().setBlock(new BlockPosition(x, y - 1, z - 1),
                        BuiltinBlocks.STONE);
                powerMount(server, x, y, z);
            });
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y, z)));
            // The body at (x + 0.5, y + 0.2, z - 0.5) — the reference's own
            // position law (lines 127-129: source.getX() + dx, the block's
            // own y + 0.2, source.getZ() + dz).
            await(() -> mobNear(server, MobType.VILLAGER, x + 0.5, y + 0.2, z - 0.5),
                    "the villager spawned one block out at the block's base");
            await(() -> slotCount(server, dispenser, 0) == 1,
                    "the egg stack paid one unit");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theUnknownEggIdStillSpendsItsUnit() throws Exception {
        // The dispense path's own quirk (lines 132-139): the split runs
        // after the null spawn — an unregistered id consumes the item
        // without spawning, unlike the use path's no-payment gate.
        EngineServer server = boot();
        try {
            int x = 30, z = 60, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                server.world().setBlock(dispenser, DispenserBlocks.dispenserOf(2, false));
                server.dispensers().getOrCreate(dispenser).setItem(0,
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.SPAWN_EGG, 3)
                                .withDamage(9999)); // no registered egg answers
                powerMount(server, x, y, z);
            });
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y, z)));
            await(() -> slotCount(server, dispenser, 0) == 2,
                    "the unknown egg still spent its unit");
            // The negative window: nothing ever spawns (the item spent
            // itself against the reference's null-spawn quirk).
            net.zaminmc.torch.testing.EngineAwaits.awaitTicks(server.world(), 8,
                    "the unknown-id negative window (8 ticks)");
            assertFalse(anyMob(server, MobType.VILLAGER), "no villager ever spawned");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The firework rocket (Bootstrap lines 142-160)
    // ------------------------------------------------------------------

    @Test
    void theDispensedFireworkRocketLiftsOff() throws Exception {
        EngineServer server = boot();
        try {
            int x = 40, z = 60, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                server.world().setBlock(dispenser, DispenserBlocks.dispenserOf(2, false));
                server.dispensers().getOrCreate(dispenser).setItem(0,
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.FIREWORKS, 2));
                powerMount(server, x, y, z);
            });
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y, z)));
            // The rocket lifts at the spawn egg's own position law (lines
            // 143-145) and the stack pays one.
            await(() -> rocketNear(server, x + 0.5, y + 0.2, z - 0.5),
                    "the rocket lifted one block out at the block's base");
            await(() -> slotCount(server, dispenser, 0) == 1,
                    "the rocket stack paid one unit");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The boat (Bootstrap lines 182-219)
    // ------------------------------------------------------------------

    @Test
    void theDispensedBoatLandsLiftedOnTheWaterAndTheDryCellRefuses() throws Exception {
        EngineServer server = boot();
        try {
            int x = 50, z = 60, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            // The refusal dispenser: ten north, facing the open air (no
            // water, no water below) — the fallback drop (lines 202-206).
            BlockPosition dry = new BlockPosition(x + 10, y, z);
            server.ticker().submit(() -> {
                server.world().setBlock(dispenser, DispenserBlocks.dispenserOf(2, false));
                server.dispensers().getOrCreate(dispenser).setItem(0,
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.BOAT, 1));
                // The facing cell is water (lines 196-200: d3 = 1.0),
                // grounded so the source settles and the hull floats.
                server.world().setBlock(new BlockPosition(x, y - 1, z - 1),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z - 1),
                        FluidBlocks.typeOf(FluidBlocks.Kind.WATER,
                                FluidBlocks.Form.SOURCE, 0));
                server.world().setBlock(dry, DispenserBlocks.dispenserOf(2, false));
                server.dispensers().getOrCreate(dry).setItem(0,
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.BOAT, 1));
                powerMount(server, x, y, z);
                powerMount(server, x + 10, y, z);
            });
            server.ticker().submit(() -> {
                server.redstone().useLever(new BlockPosition(x + 2, y, z));
                server.redstone().useLever(new BlockPosition(x + 12, y, z));
            });
            // The hull at (x + 0.5, y + 0.5 + 1.0, z - 0.625) — the 1.125-out
            // law (lines 187-189) with the water's full-block lift.
            await(() -> vehicleNear(server,
                            net.zaminmc.torch.server.entity.vehicle.VehicleEntity.Kind.BOAT,
                            x + 0.5, y + 1.5, z - 0.625),
                    "the boat spawned lifted over the water");
            await(() -> slotCount(server, dispenser, 0) == 0,
                    "the boat stack spent its unit");
            // The dry cell: no boat, the default item drop instead.
            await(() -> itemDropNear(server, x + 10, y, z - 1, "boat"),
                    "the dry cell fell through to the default drop");
            await(() -> slotCount(server, dry, 0) == 0,
                    "the refused boat spent its unit as the drop");
            assertFalse(vehicleNear(server,
                            net.zaminmc.torch.server.entity.vehicle.VehicleEntity.Kind.BOAT,
                            x + 10.5, y + 1.5, z - 0.625),
                    "no boat ever spawned at the dry cell");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The minecart family (MinecartItem lines 18-77)
    // ------------------------------------------------------------------

    @Test
    void theDispensedMinecartTakesItsRailAndTheRaillessCellRefuses() throws Exception {
        EngineServer server = boot();
        try {
            int x = 60, z = 60, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            BlockPosition railless = new BlockPosition(x + 10, y, z);
            server.ticker().submit(() -> {
                server.world().setBlock(dispenser, DispenserBlocks.dispenserOf(2, false));
                server.dispensers().getOrCreate(dispenser).setItem(0,
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.MINECART, 1));
                // A flat rail at the facing cell (lines 43-47: d3 = 0.1),
                // grounded on a floor so the cart rides where it lands.
                server.world().setBlock(new BlockPosition(x, y - 1, z - 1),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z - 1),
                        BuiltinBlocks.RAIL);
                server.world().setBlock(railless, DispenserBlocks.dispenserOf(2, false));
                server.dispensers().getOrCreate(railless).setItem(0,
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.MINECART, 1));
                powerMount(server, x, y, z);
                powerMount(server, x + 10, y, z);
            });
            server.ticker().submit(() -> {
                server.redstone().useLever(new BlockPosition(x + 2, y, z));
                server.redstone().useLever(new BlockPosition(x + 12, y, z));
            });
            // The cart at (x + 0.5, y + 0.1, z - 0.625) — the 1.125-out law
            // over the rail's 0.1 hold.
            await(() -> vehicleNear(server,
                            net.zaminmc.torch.server.entity.vehicle.VehicleEntity.Kind.MINECART,
                            x + 0.5, y + 0.1, z - 0.625),
                    "the minecart took its rail");
            await(() -> slotCount(server, dispenser, 0) == 0,
                    "the cart stack spent its unit");
            // The railless cell: the default drop (lines 51-53).
            await(() -> itemDropNear(server, x + 10, y, z - 1, "minecart"),
                    "the railless cell fell through to the default drop");
            await(() -> slotCount(server, railless, 0) == 0,
                    "the refused cart spent its unit as the drop");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The armor (ArmorItem lines 32-63)
    // ------------------------------------------------------------------

    @Test
    void theDispensedArmorEquipsTheFirstEligibleBody() throws Exception {
        EngineServer server = boot();
        try {
            int x = 70, z = 60, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            BlockPosition empty = new BlockPosition(x + 10, y, z);
            server.ticker().submit(() -> {
                server.world().setBlock(dispenser, DispenserBlocks.dispenserOf(2, false));
                server.dispensers().getOrCreate(dispenser).setItem(0,
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.IRON_HELMET, 1));
                // The floor under the facing cell: the body stands where
                // the test puts it (the dispense arrives four ticks later).
                server.world().setBlock(new BlockPosition(x, y - 1, z - 1),
                        BuiltinBlocks.STONE);
                // The villager stands in the facing cell (the villager's
                // own canPickupLoot always-true — the reference's filter,
                // EntityFilter lines 40-52). Pinned: the equip must catch
                // the body where it stands.
                net.zaminmc.torch.server.entity.MobEntity villager =
                        server.mobs().spawnAt(MobType.VILLAGER,
                                new net.zaminmc.torch.util.Position(x + 0.5, y, z - 0.5));
                villager.setNoAi(true);
                // The refusal dispenser: nobody at its cell.
                server.world().setBlock(empty, DispenserBlocks.dispenserOf(2, false));
                server.dispensers().getOrCreate(empty).setItem(0,
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.IRON_HELMET, 1));
                powerMount(server, x, y, z);
                powerMount(server, x + 10, y, z);
            });
            server.ticker().submit(() -> {
                server.redstone().useLever(new BlockPosition(x + 2, y, z));
                server.redstone().useLever(new BlockPosition(x + 12, y, z));
            });
            // setEquipment(4) + the always-drop marker (lines 49-59).
            await(() -> mobEquipment(server, MobType.VILLAGER, 4, "iron_helmet"),
                    "the villager wears the helmet in the head slot");
            assertTrue(mobDropChance(server, MobType.VILLAGER, 4) == 2.0f,
                    "the equipped slot carries the always-drop marker");
            await(() -> slotCount(server, dispenser, 0) == 0,
                    "the armor stack paid one unit");
            // Nobody eligible: the default item drop (line 62).
            await(() -> itemDropNear(server, x + 10, y, z - 1, "iron_helmet"),
                    "the empty cell fell through to the default drop");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The skull (Bootstrap lines 340-392)
    // ------------------------------------------------------------------

    @Test
    void theDispensedSkullLandsOnTheWitherBaseAndTheOtherTypesRefuse() throws Exception {
        EngineServer server = boot();
        try {
            int x = 80, z = 60, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            BlockPosition plain = new BlockPosition(x + 10, y, z);
            server.ticker().submit(() -> {
                server.world().setBlock(dispenser, DispenserBlocks.dispenserOf(2, false));
                server.dispensers().getOrCreate(dispenser).setItem(0,
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.SKULL, 2)
                                .withDamage(1)); // the wither head
                // The complete base under the facing cell (x, y, z-1): the
                // soul-sand row at y-1 and the air-sand-air row at y-2 —
                // canSpawn's own pattern (SkullBlock lines 159-165).
                for (int i = -1; i <= 1; i++) {
                    server.world().setBlock(new BlockPosition(x + i, y - 1, z - 1),
                            BuiltinBlocks.SOUL_SAND);
                }
                server.world().setBlock(new BlockPosition(x - 1, y - 2, z - 1),
                        server.world().airType());
                server.world().setBlock(new BlockPosition(x, y - 2, z - 1),
                        BuiltinBlocks.SOUL_SAND);
                server.world().setBlock(new BlockPosition(x + 1, y - 2, z - 1),
                        server.world().airType());
                // The skeleton head (damage 0): canSpawn's own metadata gate
                // (line 160) refuses it — the stack stays whole.
                server.world().setBlock(plain, DispenserBlocks.dispenserOf(2, false));
                server.dispensers().getOrCreate(plain).setItem(0,
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.SKULL, 3)
                                .withDamage(0));
                powerMount(server, x, y, z);
                powerMount(server, x + 10, y, z);
            });
            server.ticker().submit(() -> {
                server.redstone().useLever(new BlockPosition(x + 2, y, z));
                server.redstone().useLever(new BlockPosition(x + 12, y, z));
            });
            BlockPosition placed = new BlockPosition(x, y, z - 1);
            // The floor skull lands (line 352's FACING UP) — a base alone
            // never summons (trySpawn needs the full three-head pattern).
            await(() -> SkullBlocks.isSkull(blockAt(server, placed).identifier())
                            && SkullBlocks.facingOf(blockAt(server, placed)) == 1,
                    "the wither head landed as the floor skull");
            var tile = skullTile(server, placed);
            assertTrue(tile != null && tile.type() == 1,
                    "the tile carries the wither type");
            assertTrue(tile != null && tile.rotation() == 0,
                    "the head faces back at the north-facing dispenser");
            assertFalse(anyMob(server, MobType.WITHER), "the base alone never summons");
            await(() -> slotCount(server, dispenser, 0) == 1,
                    "the skull stack paid one unit");
            // The skeleton head refused: nothing placed, nothing spent.
            EngineAwaitsTicks(server, 8, "the refusal negative window (8 ticks)");
            assertEquals(3, slotCount(server, plain, 0),
                    "the refused skeleton head spent nothing");
            assertTrue(blockAt(server, new BlockPosition(x + 10, y, z - 1))
                            .equals(server.world().airType()),
                    "the refused skull never landed");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theDispensedWitherHeadCompletesTheSummon() throws Exception {
        EngineServer server = boot();
        try {
            int x = 90, z = 60, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                server.world().setBlock(dispenser, DispenserBlocks.dispenserOf(2, false));
                server.dispensers().getOrCreate(dispenser).setItem(0,
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.SKULL, 1)
                                .withDamage(1));
                // The base + the two flanking wither heads: the dispensed
                // third head completes the pattern (the head cell (x, y,
                // z-1) is the run's middle).
                for (int i = -1; i <= 1; i++) {
                    server.world().setBlock(new BlockPosition(x + i, y - 1, z - 1),
                            BuiltinBlocks.SOUL_SAND);
                }
                server.world().setBlock(new BlockPosition(x - 1, y - 2, z - 1),
                        server.world().airType());
                server.world().setBlock(new BlockPosition(x, y - 2, z - 1),
                        BuiltinBlocks.SOUL_SAND);
                server.world().setBlock(new BlockPosition(x + 1, y - 2, z - 1),
                        server.world().airType());
                placeWitherHead(server, x - 1, y, z - 1);
                placeWitherHead(server, x + 1, y, z - 1);
                powerMount(server, x, y, z);
            });
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y, z)));
            // The summon (line 375's trySpawn): the wither at the pattern's
            // top-center cell and the whole 3x3 build cleared.
            await(() -> anyMob(server, MobType.WITHER), "the wither summoned");
            EngineAwaitsTicks(server, 4, "the build-clear window (4 ticks)");
            assertTrue(blockAt(server, new BlockPosition(x, y, z - 1))
                            .equals(server.world().airType())
                    && blockAt(server, new BlockPosition(x, y - 1, z - 1))
                            .equals(server.world().airType()),
                    "the pattern's blocks cleared");
            await(() -> slotCount(server, dispenser, 0) == 0,
                    "the completing head spent its unit");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The pumpkin (Bootstrap lines 393-421)
    // ------------------------------------------------------------------

    @Test
    void theDispensedPumpkinBuildsTheSnowGolemAndTheBareCellRefuses() throws Exception {
        EngineServer server = boot();
        try {
            int x = 100, z = 60, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            BlockPosition bare = new BlockPosition(x + 10, y, z);
            server.ticker().submit(() -> {
                server.world().setBlock(dispenser, DispenserBlocks.dispenserOf(2, false));
                server.dispensers().getOrCreate(dispenser).setItem(0,
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.PUMPKIN, 2));
                // The snow column under the facing cell — the snow body
                // pattern (PumpkinBlock lines 137-146) — with a floor at
                // the column's base so the newborn golem stays at its
                // spawn cell.
                server.world().setBlock(new BlockPosition(x, y - 1, z - 1),
                        BuiltinBlocks.SNOW);
                server.world().setBlock(new BlockPosition(x, y - 2, z - 1),
                        BuiltinBlocks.SNOW);
                server.world().setBlock(new BlockPosition(x, y - 3, z - 1),
                        BuiltinBlocks.STONE);
                // The bare cell: no pattern (lines 401 + 407-408 refuse).
                server.world().setBlock(bare, DispenserBlocks.dispenserOf(2, false));
                server.dispensers().getOrCreate(bare).setItem(0,
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.PUMPKIN, 3));
                powerMount(server, x, y, z);
                powerMount(server, x + 10, y, z);
            });
            server.ticker().submit(() -> {
                server.redstone().useLever(new BlockPosition(x + 2, y, z));
                server.redstone().useLever(new BlockPosition(x + 12, y, z));
            });
            // The golem at the bottom snow cell (trySpawnGolem's own law)
            // and the column cleared to air.
            await(() -> mobNear(server, MobType.SNOW_GOLEM, x + 0.5, y - 1.95, z - 0.5),
                    "the snow golem built at the column's base");
            EngineAwaitsTicks(server, 4, "the column-clear window (4 ticks)");
            assertTrue(blockAt(server, new BlockPosition(x, y, z - 1))
                            .equals(server.world().airType()),
                    "the pumpkin cell cleared");
            await(() -> slotCount(server, dispenser, 0) == 1,
                    "the pumpkin stack paid one unit");
            // The bare cell: nothing placed, nothing spent.
            EngineAwaitsTicks(server, 6, "the refusal negative window (6 ticks)");
            assertEquals(3, slotCount(server, bare, 0),
                    "the refused pumpkin spent nothing");
            assertTrue(blockAt(server, new BlockPosition(x + 10, y, z - 1))
                            .equals(server.world().airType()),
                    "no pumpkin landed on the bare cell");
            assertFalse(mobNear(server, MobType.SNOW_GOLEM, x + 10.5, y - 1.95, z - 0.5),
                    "no golem ever built at the bare cell");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The helpers (the Slice 11a test rig's own shapes)
    // ------------------------------------------------------------------

    /** The stone + lever power mount east of the dispenser. */
    private static void powerMount(EngineServer server, int x, int y, int z) {
        server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
        server.world().setBlock(new BlockPosition(x + 2, y, z),
                RedstoneBlocks.leverOf(1, false));
    }

    private static void placeWitherHead(EngineServer server, int x, int y, int z) {
        server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.SKULL);
        server.skulls().place(new BlockPosition(x, y, z), 1, 0);
    }

    private static BlockType blockAt(EngineServer server, BlockPosition at) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(server.world().getBlock(at)));
        long deadline = System.currentTimeMillis() + 1_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get();
    }

    private static int slotCount(EngineServer server, BlockPosition dispenser, int slot) {
        AtomicReference<Integer> result = new AtomicReference<>();
        server.ticker().submit(() -> {
            var container = server.dispensers().peek(dispenser);
            result.set(container == null ? -1 : container.getItem(slot).count());
        });
        long deadline = System.currentTimeMillis() + 1_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get() == null ? -1 : result.get();
    }

    private static boolean anyMob(EngineServer server, MobType kind) {
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        java.util.concurrent.atomic.AtomicBoolean ran =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        server.ticker().submit(() -> {
            try {
                for (var mob : server.mobs().all()) {
                    if (mob.type() == kind) {
                        found.set(true);
                    }
                }
            } finally {
                ran.set(true);
            }
        });
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && !ran.get()) {
            sleep(5);
        }
        return found.get();
    }

    private static boolean mobNear(EngineServer server, MobType kind,
                                   double x, double y, double z) {
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        java.util.concurrent.atomic.AtomicBoolean ran =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        server.ticker().submit(() -> {
            try {
                for (var mob : server.mobs().all()) {
                    if (mob.type() != kind) {
                        continue;
                    }
                    var at = mob.position();
                    if (Math.abs(at.x() - x) <= 1.0 && Math.abs(at.y() - y) <= 1.0
                            && Math.abs(at.z() - z) <= 1.0) {
                        found.set(true);
                    }
                }
            } finally {
                ran.set(true);
            }
        });
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && !ran.get()) {
            sleep(5);
        }
        return found.get();
    }

    private static boolean mobEquipment(EngineServer server, MobType kind, int slot,
                                       String item) {
        AtomicReference<Boolean> equipped = new AtomicReference<>(false);
        java.util.concurrent.atomic.AtomicBoolean ran =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        server.ticker().submit(() -> {
            try {
                for (var mob : server.mobs().all()) {
                    if (mob.type() != kind) {
                        continue;
                    }
                    var held = mob.equipment(slot);
                    if (held != null && !held.isEmpty()
                            && held.type().identifier().value().equals(item)) {
                        equipped.set(true);
                    }
                }
            } finally {
                ran.set(true);
            }
        });
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && !ran.get()) {
            sleep(5);
        }
        return equipped.get();
    }

    private static float mobDropChance(EngineServer server, MobType kind, int slot) {
        AtomicReference<Float> chance = new AtomicReference<>(-1.0f);
        server.ticker().submit(() -> {
            for (var mob : server.mobs().all()) {
                if (mob.type() == kind) {
                    chance.set(mob.inventoryDropChance(slot));
                }
            }
        });
        long deadline = System.currentTimeMillis() + 1_000;
        while (System.currentTimeMillis() < deadline && chance.get() == -1.0f) {
            sleep(5);
        }
        return chance.get();
    }

    private static boolean vehicleNear(EngineServer server,
                                       net.zaminmc.torch.server.entity.vehicle.VehicleEntity.Kind kind,
                                       double x, double y, double z) {
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        java.util.concurrent.atomic.AtomicBoolean ran =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        server.ticker().submit(() -> {
            try {
                for (var vehicle : server.vehicles().all()) {
                    if (vehicle.kind() != kind) {
                        continue;
                    }
                    var at = vehicle.position();
                    if (Math.abs(at.x() - x) <= 1.0 && Math.abs(at.y() - y) <= 1.0
                            && Math.abs(at.z() - z) <= 1.0) {
                        found.set(true);
                    }
                }
            } finally {
                ran.set(true);
            }
        });
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && !ran.get()) {
            sleep(5);
        }
        return found.get();
    }

    private static boolean rocketNear(EngineServer server, double x, double y, double z) {
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        java.util.concurrent.atomic.AtomicBoolean ran =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        server.ticker().submit(() -> {
            try {
                for (var rocket : server.fireworks().all()) {
                    var at = rocket.position();
                    if (Math.abs(at.x() - x) <= 1.0 && Math.abs(at.y() - y) <= 1.0
                            && Math.abs(at.z() - z) <= 1.0) {
                        found.set(true);
                    }
                }
            } finally {
                ran.set(true);
            }
        });
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && !ran.get()) {
            sleep(5);
        }
        return found.get();
    }

    private static boolean itemDropNear(EngineServer server, int x, int y, int z,
                                        String item) {
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        java.util.concurrent.atomic.AtomicBoolean ran =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        server.ticker().submit(() -> {
            try {
                for (var entity : server.itemEntities().all()) {
                    var at = entity.position();
                    if (Math.abs(at.x() - (x + 0.5)) <= 2.0 && Math.abs(at.y() - y) <= 2.0
                            && Math.abs(at.z() - (z + 0.5)) <= 2.0
                            && entity.stack().type().identifier().value().equals(item)) {
                        found.set(true);
                    }
                }
            } finally {
                ran.set(true);
            }
        });
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && !ran.get()) {
            sleep(5);
        }
        return found.get();
    }

    private static net.zaminmc.torch.server.skull.SkullManager.SkullTile skullTile(
            EngineServer server, BlockPosition at) {
        AtomicReference<net.zaminmc.torch.server.skull.SkullManager.SkullTile> tile =
                new AtomicReference<>();
        server.ticker().submit(() -> tile.set(server.skulls().tileAt(at)));
        long deadline = System.currentTimeMillis() + 1_000;
        while (System.currentTimeMillis() < deadline && tile.get() == null) {
            sleep(10);
        }
        return tile.get();
    }

    private static void EngineAwaitsTicks(EngineServer server, int ticks, String why)
            throws InterruptedException {
        net.zaminmc.torch.testing.EngineAwaits.awaitTicks(server.world(), ticks, why);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "dtl" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
