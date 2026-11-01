package net.zaminmc.torch.server.container;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave FL4-GAP2 Slice B — the container remainder's dimension ownership
 * (the reference's per-world tile-entity law: every WorldServer owns its
 * own furnaces, hoppers, dispensers and brewing stands): a nether tile
 * and an overworld tile at the SAME coordinates are TWO tiles in TWO
 * families through a real restart (the ZFD v3 / ZHD v2 / ZDD v2 / ZBD v2
 * dimension stamps), the hopper's cross-family pulls consult the SAME
 * dimension's furnace, the item-entity suck gates on the hopper's own
 * world, each family's contents-changed wake arms its OWN world's
 * comparators, and the furnace window opens through the ACTING
 * dimension's family.
 */
class ContainerSliceBOwnershipAcceptanceTest {

    @TempDir
    Path dataDir;

    /** The restart seat's fixed world name (the stores ride the world's own data dir). */
    private static final String WORLD_NAME = "slicebcontainers";

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                WORLD_NAME, "it", 20, 4, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name, int dimension) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() {
                return true;
            }

            @Override public void kick(String reason) {
            }
        }, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        session.setDimension(dimension);
        EngineAwaits.awaitCondition(server.world(),
                () -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the player playing");
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** The nether platform at the same coordinates the overworld seats use. */
    private void netherPlatform(net.zaminmc.torch.server.world.EngineWorld nether,
                                int x, int y, int z) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    nether.setBlock(new BlockPosition(x + dx, y - 1, z + dz),
                            BuiltinBlocks.STONE);
                    for (int dy = 0; dy <= 3; dy++) {
                        nether.setBlock(new BlockPosition(x + dx, y + dy, z + dz),
                                nether.airType());
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(nether.getBlock(new BlockPosition(x, y - 1, z))),
                40, "the nether platform landed");
    }

    /** The overworld platform at the same coordinates the nether seats use. */
    private void overworldPlatform(int x, int y, int z) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    server.world().setBlock(new BlockPosition(x + dx, y - 1, z + dz),
                            BuiltinBlocks.STONE);
                    for (int dy = 0; dy <= 3; dy++) {
                        server.world().setBlock(new BlockPosition(x + dx, y + dy, z + dz),
                                server.world().airType());
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(
                        server.world().getBlock(new BlockPosition(x, y - 1, z))),
                40, "the overworld platform landed");
    }

    private static final net.zaminmc.torch.item.ItemType DIAMOND =
            net.zaminmc.torch.server.item.BuiltinItems.DIAMOND;
    private static final net.zaminmc.torch.item.ItemType EMERALD =
            net.zaminmc.torch.server.item.BuiltinItems.EMERALD;
    private static final net.zaminmc.torch.item.ItemType IRON_ORE =
            net.zaminmc.torch.server.item.BuiltinItems.IRON_ORE;
    private static final net.zaminmc.torch.item.ItemType GOLD_ORE =
            net.zaminmc.torch.server.item.BuiltinItems.GOLD_ORE;
    private static final net.zaminmc.torch.item.ItemType IRON_INGOT =
            net.zaminmc.torch.server.item.BuiltinItems.IRON_INGOT;
    private static final net.zaminmc.torch.item.ItemType GOLD_INGOT =
            net.zaminmc.torch.server.item.BuiltinItems.GOLD_INGOT;
    private static final net.zaminmc.torch.item.ItemType COAL =
            net.zaminmc.torch.server.item.BuiltinItems.COAL;
    private static final net.zaminmc.torch.item.ItemType POTION =
            net.zaminmc.torch.server.item.BuiltinItems.POTION;
    private static final net.zaminmc.torch.item.ItemType GLASS_BOTTLE =
            net.zaminmc.torch.server.item.BuiltinItems.GLASS_BOTTLE;

    /**
     * THE TWO-TILE RESTART SEAT (the slice's named core): a nether tile
     * and an overworld tile at the SAME coordinates carry DIFFERENT
     * contents through a real restart — the four stores' dimension stamps
     * route each row to its own family (the pre-Slice-B single maps
     * collided the pairs into one entry each; the older files' rows stay
     * overworld rows).
     */
    @Test
    void sameCoordinateTilesAreTwoTilesThroughARestart() throws Exception {
        var nether = server.worldFor(-1);
        int x = 110, y = 40, z = 110;
        overworldPlatform(x, y, z);
        netherPlatform(nether, x, y, z);
        BlockPosition furnaceAt = new BlockPosition(x - 1, y, z);
        BlockPosition hopperAt = new BlockPosition(x, y, z);
        BlockPosition dispenserAt = new BlockPosition(x + 1, y, z);
        BlockPosition brewingAt = new BlockPosition(x + 2, y, z);

        server.ticker().submit(() -> {
            // The furnace pair (the ZFD v3 stamp's own rows).
            server.world().setBlock(furnaceAt, BuiltinBlocks.FURNACE);
            nether.setBlock(furnaceAt, BuiltinBlocks.FURNACE);
            server.furnaceManager().getOrCreate(furnaceAt).setSlot(0, ItemStack.of(IRON_ORE, 3));
            server.furnacesFor(-1).getOrCreate(furnaceAt).setSlot(0, ItemStack.of(GOLD_ORE, 4));
            // The hopper pair (the ZHD v2 stamp) — facing down over stone,
            // inert until the restart reads them back.
            server.world().setBlock(hopperAt,
                    net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true));
            nether.setBlock(hopperAt,
                    net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true));
            server.hopper().getOrCreate(hopperAt).setItem(0, ItemStack.of(COAL, 2));
            server.hoppersFor(-1).getOrCreate(hopperAt).setItem(0, ItemStack.of(DIAMOND, 1));
            // The dispenser pair (the ZDD v2 stamp).
            server.world().setBlock(dispenserAt,
                    net.zaminmc.torch.server.dispenser.DispenserBlocks.dispenserOf(0, false));
            nether.setBlock(dispenserAt,
                    net.zaminmc.torch.server.dispenser.DispenserBlocks.dispenserOf(0, false));
            server.dispensers().getOrCreate(dispenserAt).setItem(3, ItemStack.of(IRON_INGOT, 5));
            server.dispensersFor(-1).getOrCreate(dispenserAt).setItem(3, ItemStack.of(EMERALD, 6));
            // The brewing pair (the ZBD v2 stamp) — the potion slots hold
            // their one-per-bottle units.
            server.world().setBlock(brewingAt,
                    net.zaminmc.torch.server.brewing.BrewingStandBlocks.of(false, false, false));
            nether.setBlock(brewingAt,
                    net.zaminmc.torch.server.brewing.BrewingStandBlocks.of(false, false, false));
            server.brewingStands().getOrCreate(brewingAt).setItem(1, ItemStack.of(POTION, 1));
            server.brewingStandsFor(-1).getOrCreate(brewingAt)
                    .setItem(2, ItemStack.of(GLASS_BOTTLE, 1));
        });
        await(() -> net.zaminmc.torch.server.hopper.HopperBlocks.isHopper(
                        server.world().getBlock(hopperAt))
                        && server.furnaceManager().peek(furnaceAt) != null
                        && server.brewingStandsFor(-1).peek(brewingAt) != null,
                "both dimensions' four families placed");

        server.shutdown(null);

        // The reboot over the SAME world: each family's tiles answer their
        // own contents (the four stamps' round trips).
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                WORLD_NAME, "it", 20, 4, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();

        AtomicReference<int[]> census = new AtomicReference<>();
        server.ticker().submit(() -> {
            var overFurnace = server.furnaceManager().peek(furnaceAt);
            var netherFurnace = server.furnacesFor(-1).peek(furnaceAt);
            var overHopper = server.hopper().peek(hopperAt);
            var netherHopper = server.hoppersFor(-1).peek(hopperAt);
            var overDispenser = server.dispensers().peek(dispenserAt);
            var netherDispenser = server.dispensersFor(-1).peek(dispenserAt);
            var overBrewing = server.brewingStands().peek(brewingAt);
            var netherBrewing = server.brewingStandsFor(-1).peek(brewingAt);
            census.set(new int[]{
                    overFurnace == null ? -1
                            : (overFurnace.input().isEmpty() ? -1
                                    : (overFurnace.input().type().equals(IRON_ORE) ? 1 : 0)),
                    netherFurnace == null ? -1
                            : (netherFurnace.input().isEmpty() ? -1
                                    : (netherFurnace.input().type().equals(GOLD_ORE) ? 1 : 0)),
                    overHopper == null ? -1 : overHopper.getItem(0).count(),
                    netherHopper == null ? -1
                            : (netherHopper.getItem(0).isEmpty() ? -1
                                    : (netherHopper.getItem(0).type().equals(DIAMOND) ? 1 : 0)),
                    overDispenser == null ? -1 : overDispenser.getItem(3).count(),
                    netherDispenser == null ? -1
                            : (netherDispenser.getItem(3).isEmpty() ? -1
                                    : (netherDispenser.getItem(3).type().equals(EMERALD) ? 1 : 0)),
                    overBrewing == null ? -1
                            : (overBrewing.getItem(1).isEmpty() ? -1
                                    : (overBrewing.getItem(1).type().equals(POTION) ? 1 : 0)),
                    netherBrewing == null ? -1
                            : (netherBrewing.getItem(2).isEmpty() ? -1
                                    : (netherBrewing.getItem(2).type().equals(GLASS_BOTTLE)
                                            ? 1 : 0))});
        });
        await(() -> census.get() != null, "the census ran");
        assertEquals(1, census.get()[0],
                "the overworld furnace kept its iron ore (its own family's row)");
        assertEquals(1, census.get()[1],
                "the nether furnace kept its gold ore (the v3 stamp's own row)");
        assertEquals(2, census.get()[2],
                "the overworld hopper kept its coal");
        assertEquals(1, census.get()[3],
                "the nether hopper kept its diamond (the ZHD v2 stamp's own row)");
        assertEquals(5, census.get()[4],
                "the overworld dispenser kept its iron ingots");
        assertEquals(1, census.get()[5],
                "the nether dispenser kept its emeralds (the ZDD v2 stamp's own row)");
        assertEquals(1, census.get()[6],
                "the overworld stand kept its potion");
        assertEquals(1, census.get()[7],
                "the nether stand kept its bottle (the ZBD v2 stamp's own row)");
    }

    /**
     * THE CROSS-FAMILY PULL SEAT (the slice's own named law): the hopper
     * below a furnace pulls the OUTPUT slot of the furnace of the SAME
     * dimension — the overworld hopper reads the overworld furnace's
     * iron, the nether hopper the nether furnace's gold, and neither
     * family's contents ever cross the world boundary.
     */
    @Test
    void theHopperPullReadsTheSameDimensionFurnace() throws Exception {
        var nether = server.worldFor(-1);
        int x = 120, y = 40, z = 120;
        overworldPlatform(x, y, z);
        netherPlatform(nether, x, y, z);
        BlockPosition furnaceAt = new BlockPosition(x, y + 1, z);
        BlockPosition hopperAt = new BlockPosition(x, y, z);

        server.ticker().submit(() -> {
            server.world().setBlock(furnaceAt, BuiltinBlocks.FURNACE);
            nether.setBlock(furnaceAt, BuiltinBlocks.FURNACE);
            // The hopper under the furnace: its pull reads the furnace's
            // DOWN face (the output slot first — the reference's sided
            // slots, FurnaceBlockEntity lines 326-333).
            server.world().setBlock(hopperAt,
                    net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true));
            nether.setBlock(hopperAt,
                    net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true));
            server.furnaceManager().getOrCreate(furnaceAt)
                    .setSlot(2, ItemStack.of(IRON_INGOT, 2));
            server.furnacesFor(-1).getOrCreate(furnaceAt)
                    .setSlot(2, ItemStack.of(GOLD_INGOT, 4));
        });
        await(() -> net.zaminmc.torch.server.hopper.HopperBlocks.isHopper(
                        server.world().getBlock(hopperAt))
                        && server.furnacesFor(-1).peek(furnaceAt) != null,
                "both dimensions' hoppers sit under their furnaces");

        // The 8-tick pull cadence: each hopper's first slot receives its
        // OWN dimension's furnace output (one item per pulse).
        EngineAwaits.awaitCondition(server.world(), () -> {
            var over = server.hopper().peek(hopperAt);
            var below = server.hoppersFor(-1).peek(hopperAt);
            return over != null && below != null
                    && !over.getItem(0).isEmpty() && !below.getItem(0).isEmpty();
        }, 60, "both hoppers pulled their furnaces' outputs");
        var overHopper = server.hopper().peek(hopperAt);
        var netherHopper = server.hoppersFor(-1).peek(hopperAt);
        assertTrue(overHopper.getItem(0).type().equals(IRON_INGOT),
                "the overworld hopper pulled the OVERWORLD furnace's iron");
        assertTrue(netherHopper.getItem(0).type().equals(GOLD_INGOT),
                "the nether hopper pulled the NETHER furnace's gold");
        // The vanilla cadence moves ONE item per transfer pulse.
        assertEquals(1, overHopper.getItem(0).count(),
                "the overworld pull moved its one item");
        assertEquals(1, netherHopper.getItem(0).count(),
                "the nether pull moved its one item");
        // Neither furnace lost the OTHER family's output.
        assertEquals(1, server.furnaceManager().peek(furnaceAt).output().count(),
                "the overworld furnace kept its remainder");
        assertEquals(3, server.furnacesFor(-1).peek(furnaceAt).output().count(),
                "the nether furnace kept its remainder");
    }

    /**
     * THE SUCK-GATE SEAT: the item-entity suck gathers the hopper's OWN
     * world's bodies alone (the reference's per-world getEntitiesOfType) —
     * a nether item at the same coordinates never enters the overworld
     * hopper, a same-world one does.
     */
    @Test
    void theSuckGateStaysInsideTheHoppersOwnWorld() throws Exception {
        int x = 130, y = 40, z = 130;
        overworldPlatform(x, y, z);
        BlockPosition hopperAt = new BlockPosition(x, y, z);

        server.ticker().submit(() -> {
            server.world().setBlock(hopperAt,
                    net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true));
        });
        await(() -> net.zaminmc.torch.server.hopper.HopperBlocks.isHopper(
                        server.world().getBlock(hopperAt)),
                "the overworld hopper placed with air above");

        // A nether item IN THE CELL ABOVE the hopper (the same coordinates
        // the suck scans): the pre-Slice-B scan had no dimension gate and
        // would have eaten it through the shared manager.
        server.ticker().submit(() -> server.itemEntities().spawnDropAtBlock(
                new Position(x, y + 1, z), ItemStack.of(EMERALD, 1), 0, -1));
        // Five full transfer pulses (the 8-tick cadence) with nothing
        // sucked — the gate holds.
        EngineAwaits.awaitTicks(server.world(), 40, "the nether item's observation window");
        var hopper = server.hopper().peek(hopperAt);
        assertTrue(hopper == null || hopper.getItem(0).isEmpty(),
                "the overworld hopper never sucked the NETHER's emerald");
        boolean netherItemAlive = server.itemEntities().all().stream()
                .anyMatch(item -> item.dimension() == -1
                        && !item.stack().isEmpty()
                        && item.stack().type().equals(EMERALD));
        assertTrue(netherItemAlive,
                "the nether item still lies in its own world (never collected)");

        // The same-world control: an overworld diamond at the same cell is
        // sucked within the cadence.
        server.ticker().submit(() -> server.itemEntities().spawnDropAtBlock(
                new Position(x, y + 1, z), ItemStack.of(DIAMOND, 1), 0, 0));
        EngineAwaits.awaitCondition(server.world(), () -> {
            var live = server.hopper().peek(hopperAt);
            return live != null && !live.getItem(0).isEmpty();
        }, 40, "the overworld hopper sucked its own world's diamond");
        assertTrue(server.hopper().peek(hopperAt).getItem(0).type().equals(DIAMOND),
                "the sucked stack is the overworld diamond");
    }

    /**
     * THE COMPARATOR-WAKE SEAT: a nether hopper's contents change arms the
     * NETHER's comparators (its family's wake routes through its OWN
     * world's system), where the same-coordinate overworld comparator
     * stays cold over its own empty hopper.
     */
    @Test
    void theComparatorWakeArmsTheActingDimensionsSystem() throws Exception {
        var nether = server.worldFor(-1);
        int x = 140, y = 40, z = 140;
        overworldPlatform(x, y, z);
        netherPlatform(nether, x, y, z);
        BlockPosition hopperAt = new BlockPosition(x, y, z);
        BlockPosition comparatorAt = new BlockPosition(x + 1, y, z);
        BlockPosition wireAt = new BlockPosition(x + 2, y, z);

        server.ticker().submit(() -> {
            server.world().setBlock(hopperAt,
                    net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true));
            nether.setBlock(hopperAt,
                    net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true));
            nether.setBlock(comparatorAt,
                    RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_WEST, false, false));
            nether.setBlock(wireAt, RedstoneBlocks.wireOfPower(0));
            server.world().setBlock(comparatorAt,
                    RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_WEST, false, false));
            server.world().setBlock(wireAt, RedstoneBlocks.wireOfPower(0));
        });
        await(() -> RedstoneBlocks.isComparator(nether.getBlock(comparatorAt))
                        && RedstoneBlocks.isComparator(server.world().getBlock(comparatorAt)),
                "both dimensions' comparators placed");

        // The NETHER hopper's fill: the nether comparator reads the
        // fullness and powers its wire.
        server.ticker().submit(() -> {
            server.hoppersFor(-1).getOrCreate(hopperAt).setItem(0, ItemStack.of(EMERALD, 64));
        });
        await(() -> wirePower(nether, wireAt) >= 1,
                "the nether comparator reads the filled nether hopper");
        assertEquals(0, wirePower(server.world(), wireAt),
                "the overworld comparator stays cold over its empty hopper");
    }

    /**
     * THE WINDOW-OPEN SEAT: a nether player's furnace window reads the
     * NETHER family — the open stamps the session's dimension and the
     * click path routes through it, so the cursor receives the nether
     * furnace's own ore (the same-coordinate overworld furnace holds a
     * different one).
     */
    @Test
    void theFurnaceWindowOpenReadsTheActingDimensionsFamily() throws Exception {
        var nether = server.worldFor(-1);
        int x = 150, y = 40, z = 150;
        overworldPlatform(x, y, z);
        netherPlatform(nether, x, y, z);
        BlockPosition at = new BlockPosition(x, y, z);

        server.ticker().submit(() -> {
            server.world().setBlock(at, BuiltinBlocks.FURNACE);
            nether.setBlock(at, BuiltinBlocks.FURNACE);
            server.furnaceManager().getOrCreate(at).setSlot(0, ItemStack.of(IRON_ORE, 2));
            server.furnacesFor(-1).getOrCreate(at).setSlot(0, ItemStack.of(GOLD_ORE, 3));
        });
        await(() -> BuiltinBlocks.FURNACE.equals(nether.getBlock(at))
                        && server.furnacesFor(-1).peek(at) != null,
                "the nether furnace holds its gold ore");

        PlayerSession netherPlayer = join("NetherSmelter", -1);
        netherPlayer.applyMovement(new Position(x + 0.5, y, z + 1.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the nether player settles");

        var opened = new AtomicInteger();
        server.useItemOnBlock(netherPlayer, at, 1, Optional.empty(),
                id -> opened.incrementAndGet());
        await(() -> opened.get() > 0, "the nether furnace window opened");
        assertEquals(-1, netherPlayer.openContainerDimension(),
                "the open stamped the window with the NETHER's dimension");
        assertEquals(PlayerSession.ContainerKind.FURNACE,
                netherPlayer.openContainerKind(),
                "the window's kind is the furnace");

        // The pick-up click on slot 0 (the input): the cursor receives the
        // NETHER family's gold ore, never the overworld's iron.
        server.windowClick(netherPlayer, netherPlayer.openContainerWindowId(), 0, 0, 0,
                accepted -> { });
        EngineAwaits.awaitCondition(server.world(),
                () -> !netherPlayer.inventory().cursor().isEmpty(),
                10, "the click landed the cursor stack");
        ItemStack cursor = netherPlayer.inventory().cursor();
        assertTrue(cursor.type().equals(GOLD_ORE),
                "the cursor holds the NETHER furnace's gold ore (its family's item)");
        assertEquals(3, cursor.count(),
                "the whole nether stack came up");
    }

    private int wirePower(net.zaminmc.torch.server.world.EngineWorld world,
                          BlockPosition at) {
        net.zaminmc.torch.block.BlockType type = world.getBlock(at);
        return RedstoneBlocks.wirePower(type);
    }
}
