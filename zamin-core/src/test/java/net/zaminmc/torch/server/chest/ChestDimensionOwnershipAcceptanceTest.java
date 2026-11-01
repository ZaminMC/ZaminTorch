package net.zaminmc.torch.server.chest;

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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave FL4-GAP2 Slice A — the chest family's dimension ownership (the
 * reference's per-world tile-entity law: every WorldServer owns its own
 * chests): a nether chest and an overworld chest at the SAME coordinates
 * are TWO tiles in TWO families, the window paths route through the
 * OPEN's own dimension (the session's stamp), the overworld hopper's pull
 * reads the OVERWORLD family, each family's contents-changed wake arms its
 * OWN world's comparators, and the ZCD v3 row carries the dimension
 * through a real restart (a v1/v2 file's rows are overworld rows).
 */
class ChestDimensionOwnershipAcceptanceTest {

    @TempDir
    Path dataDir;

    /** The restart seat's fixed world name (the store rides the world's own data dir). */
    private static final String WORLD_NAME = "gapchests";

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

    /**
     * THE TWO-TILE SEAT (the wave's named core): a nether chest and an
     * overworld chest at the SAME coordinates carry DIFFERENT contents
     * through a real restart — the ZCD v3 row's dimension stamp routes
     * each row to its own family (the pre-GAP2 single map collided the
     * pair into one entry; the v1/v2 file's rows stay overworld rows).
     */
    @Test
    void sameCoordinateChestsAreTwoTilesThroughARestart() throws Exception {
        var nether = server.worldFor(-1);
        int x = 70, y = 40, z = 70;
        overworldPlatform(x, y, z);
        netherPlatform(nether, x, y, z);
        BlockPosition at = new BlockPosition(x, y, z);

        server.ticker().submit(() -> {
            server.world().setBlock(at, BuiltinBlocks.CHEST);
            nether.setBlock(at, BuiltinBlocks.CHEST);
        });
        await(() -> BuiltinBlocks.CHEST.equals(server.world().getBlock(at))
                && BuiltinBlocks.CHEST.equals(nether.getBlock(at)),
                "both families' chests placed at the same coordinates");

        server.ticker().submit(() -> {
            server.chests().getOrCreate(at).setSlot(0, ItemStack.of(DIAMOND, 3));
            server.chestsFor(-1).getOrCreate(at).setSlot(0, ItemStack.of(EMERALD, 5));
        });
        await(() -> {
            var over = server.chests().peek(at);
            var below = server.chestsFor(-1).peek(at);
            return over != null && below != null
                    && over.snapshotSlots()[0].count() == 3
                    && below.snapshotSlots()[0].count() == 5;
        }, "the two families hold their own contents");

        server.shutdown(null);

        // The reboot over the SAME world: each family's chest answers its
        // own contents (the v3 stamp's round trip).
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                WORLD_NAME, "it", 20, 4, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
        var netherAgain = server.worldFor(-1);
        // (The seats read the TILES — the ZCD v3 row's round trip is the
        // wave's surface; the chest BLOCKS' own restart persistence rides
        // the world-save family's per-dimension rows, its own seats.)

        AtomicReference<int[]> counts = new AtomicReference<>();
        server.ticker().submit(() -> {
            var over = server.chests().peek(at);
            var below = server.chestsFor(-1).peek(at);
            counts.set(new int[]{
                    over == null ? -1 : over.snapshotSlots()[0].count(),
                    over == null ? -1
                            : (over.snapshotSlots()[0].isEmpty() ? -1
                                    : (over.snapshotSlots()[0].type().equals(DIAMOND) ? 1 : 0)),
                    below == null ? -1 : below.snapshotSlots()[0].count(),
                    below == null ? -1
                            : (below.snapshotSlots()[0].isEmpty() ? -1
                                    : (below.snapshotSlots()[0].type().equals(EMERALD) ? 1 : 0))});
        });
        await(() -> counts.get() != null, "the census ran");
        assertEquals(3, counts.get()[0],
                "the overworld chest kept its diamond stack");
        assertEquals(1, counts.get()[1],
                "the overworld chest's slot is the diamond (its own family's item)");
        assertEquals(5, counts.get()[2],
                "the nether chest kept its emerald stack");
        assertEquals(1, counts.get()[3],
                "the nether chest's slot is the emerald (its own family's item)");
    }

    /**
     * THE WINDOW-OPEN SEAT: a nether player's chest window reads the
     * NETHER family — the open stamps the session's dimension, the click
     * path routes through it, and the picked-up item is the nether chest's
     * own (the same-coordinate overworld chest holds a different stack).
     */
    @Test
    void theWindowOpenReadsTheActingDimensionsFamily() throws Exception {
        var nether = server.worldFor(-1);
        int x = 80, y = 40, z = 80;
        overworldPlatform(x, y, z);
        netherPlatform(nether, x, y, z);
        BlockPosition at = new BlockPosition(x, y, z);

        server.ticker().submit(() -> {
            server.world().setBlock(at, BuiltinBlocks.CHEST);
            nether.setBlock(at, BuiltinBlocks.CHEST);
            server.chests().getOrCreate(at).setSlot(0, ItemStack.of(DIAMOND, 3));
            server.chestsFor(-1).getOrCreate(at).setSlot(0, ItemStack.of(EMERALD, 5));
        });
        await(() -> BuiltinBlocks.CHEST.equals(nether.getBlock(at))
                && server.chestsFor(-1).peek(at) != null,
                "the nether chest holds the emerald");

        PlayerSession netherPlayer = join("NetherOpener", -1);
        netherPlayer.applyMovement(new Position(x + 0.5, y, z + 1.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the nether player settles");

        var opened = new java.util.concurrent.atomic.AtomicInteger();
        server.useItemOnBlock(netherPlayer, at, 1, Optional.empty(),
                id -> opened.incrementAndGet());
        await(() -> opened.get() > 0, "the nether chest window opened");
        assertEquals(-1, netherPlayer.openContainerDimension(),
                "the open stamped the window with the NETHER's dimension");
        assertEquals(at, netherPlayer.openContainerPosition(),
                "the window's position is the chest cell");

        // The pick-up click on slot 0: the cursor receives the NETHER
        // family's emerald, never the overworld's diamond.
        server.windowClick(netherPlayer, netherPlayer.openContainerWindowId(), 0, 0, 0,
                accepted -> { });
        EngineAwaits.awaitCondition(server.world(),
                () -> !netherPlayer.inventory().cursor().isEmpty(),
                10, "the click landed the cursor stack");
        ItemStack cursor = netherPlayer.inventory().cursor();
        assertTrue(cursor.type().equals(EMERALD),
                "the cursor holds the NETHER chest's emerald (its family's item)");
        assertEquals(5, cursor.count(),
                "the whole nether stack came up");
    }

    /**
     * THE HOPPER-PULL SEAT: the overworld hopper's pull reads the
     * OVERWORLD family — a same-coordinate nether chest's contents never
     * leak into the transfer (the hopper family itself stays overworld-
     * bound until Slice B; its chest CONSULT routes through the overworld
     * family alone).
     */
    @Test
    void theHopperPullReadsTheOverworldFamily() throws Exception {
        var nether = server.worldFor(-1);
        int x = 90, y = 40, z = 90;
        overworldPlatform(x, y, z);
        netherPlatform(nether, x, y, z);
        BlockPosition chestAt = new BlockPosition(x, y, z);
        BlockPosition hopperAt = new BlockPosition(x, y - 1, z);

        server.ticker().submit(() -> {
            server.world().setBlock(chestAt, BuiltinBlocks.CHEST);
            server.world().setBlock(hopperAt,
                    net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true));
            nether.setBlock(chestAt, BuiltinBlocks.CHEST);
            server.chests().getOrCreate(chestAt).setSlot(0, ItemStack.of(DIAMOND, 2));
            server.chestsFor(-1).getOrCreate(chestAt).setSlot(0, ItemStack.of(EMERALD, 7));
        });
        await(() -> net.zaminmc.torch.server.hopper.HopperBlocks.isHopper(
                        server.world().getBlock(hopperAt))
                        && server.chests().peek(chestAt) != null,
                "the overworld hopper sits under the chest pair");

        // The 8-tick pull cadence: the hopper's first slot receives the
        // OVERWORLD chest's diamond (the overworld family's own item).
        EngineAwaits.awaitCondition(server.world(), () -> {
            var hopper = server.hopper().peek(hopperAt);
            return hopper != null && !hopper.getItem(0).isEmpty();
        }, 60, "the hopper pulled a stack from the chest above");
        var hopper = server.hopper().peek(hopperAt);
        assertTrue(hopper.getItem(0).type().equals(DIAMOND),
                "the hopper pulled the OVERWORLD chest's diamond");
        // The vanilla cadence moves ONE item per transfer pulse — the
        // first pulse's unit is the discrimination, never the whole stack.
        assertEquals(1, hopper.getItem(0).count(),
                "the first transfer pulse moved its one item (the vanilla cadence)");
        // The nether chest keeps its own emerald — nothing of the nether
        // family entered the overworld's transfer.
        assertEquals(7, server.chestsFor(-1).peek(chestAt).snapshotSlots()[0].count(),
                "the nether family's chest is untouched by the overworld pull");
    }

    /**
     * THE COMPARATOR-WAKE SEAT: a nether chest's contents change arms the
     * NETHER's comparators (its family's wake routes through its OWN
     * world's system — the pre-GAP2 single family armed the overworld's
     * system alone, so the nether comparator never re-evaluated).
     */
    @Test
    void theComparatorWakeArmsTheActingDimensionsSystem() throws Exception {
        var nether = server.worldFor(-1);
        int x = 100, y = 40, z = 100;
        overworldPlatform(x, y, z);
        netherPlatform(nether, x, y, z);
        BlockPosition chestAt = new BlockPosition(x, y, z);
        BlockPosition comparatorAt = new BlockPosition(x + 1, y, z);
        BlockPosition wireAt = new BlockPosition(x + 2, y, z);

        server.ticker().submit(() -> {
            server.world().setBlock(chestAt, BuiltinBlocks.CHEST);
            nether.setBlock(chestAt, BuiltinBlocks.CHEST);
            // The comparator topology the family's own parity seat uses:
            // the comparator one east of the chest facing WEST reads the
            // chest, the wire two east carries the fullness signal.
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

        // The NETHER chest's fill: the nether comparator must read the
        // fullness and power its wire (the wake armed the NETHER system's
        // own re-evaluation).
        server.ticker().submit(() -> {
            server.chestsFor(-1).getOrCreate(chestAt).setSlot(0, ItemStack.of(EMERALD, 64));
        });
        await(() -> wirePower(nether, wireAt) >= 1,
                "the nether comparator reads the filled nether chest");
        // The overworld twin at the SAME coordinates: its chest is EMPTY,
        // its comparator stays cold (nothing of the nether's change armed
        // the overworld's system).
        assertEquals(0, wirePower(server.world(), wireAt),
                "the overworld comparator stays cold over its empty chest");
        // The overworld family's OWN tile (the placement listener's empty
        // state for the overworld chest block) never gained the nether's
        // contents — its slot stays empty where the nether's holds 64.
        var overworldTile = server.chests().peek(chestAt);
        assertTrue(overworldTile == null || overworldTile.snapshotSlots()[0].isEmpty(),
                "the overworld family's tile never gained the nether's emerald");
        assertEquals(64, server.chestsFor(-1).peek(chestAt).snapshotSlots()[0].count(),
                "the nether family's tile holds the fill");
    }

    private int wirePower(net.zaminmc.torch.server.world.EngineWorld world,
                          BlockPosition at) {
        net.zaminmc.torch.block.BlockType type = world.getBlock(at);
        return RedstoneBlocks.wirePower(type);
    }
}
