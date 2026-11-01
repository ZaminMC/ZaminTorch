package net.zaminmc.torch.server.tile;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave FL4-GAP2 Slice C — the tile families' dimension ownership (the
 * reference's per-world tile-entity law: every WorldServer owns its own
 * signs, jukeboxes, note blocks, banners, beacons, skulls and command
 * blocks): a nether tile and an overworld tile at the SAME coordinates
 * are TWO tiles in TWO families through a real restart (the ZSD v3 /
 * ZJD v2 / ZND v2 / ZBD v2 / ZBT v2 / ZCB v3 dimension stamps), a
 * nether player's sign edit writes the NETHER family alone, the note
 * use arm tunes the user's OWN world's tile, and the beacon window open
 * reads the ACTING dimension's family.
 */
class TileSliceCOwnershipAcceptanceTest {

    @TempDir
    Path dataDir;

    /** The restart seat's fixed world name (the stores ride the world's own data dir). */
    private static final String WORLD_NAME = "slicectiles";

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

    private static final net.zaminmc.torch.item.ItemType RECORD_FAR =
            net.zaminmc.torch.server.item.BuiltinItems.RECORD_FAR;
    private static final net.zaminmc.torch.item.ItemType RECORD_CAT =
            net.zaminmc.torch.server.item.BuiltinItems.RECORD_CAT;

    /**
     * THE TWO-TILE RESTART SEAT (the slice's named core): a nether tile
     * and an overworld tile at the SAME coordinates carry DIFFERENT state
     * through a real restart — the six stores' dimension stamps route each
     * row to its own family (the pre-Slice-C single maps collided the
     * pairs into one entry each; the older files' rows stay overworld
     * rows).
     */
    @Test
    void sameCoordinateTilesAreTwoTilesThroughARestart() throws Exception {
        var nether = server.worldFor(-1);
        int x = 210, y = 40, z = 210;
        overworldPlatform(x, y, z);
        netherPlatform(nether, x, y, z);
        BlockPosition signAt = new BlockPosition(x - 2, y, z);
        BlockPosition jukeboxAt = new BlockPosition(x - 1, y, z);
        BlockPosition noteAt = new BlockPosition(x, y, z);
        BlockPosition bannerAt = new BlockPosition(x + 1, y, z);
        BlockPosition beaconAt = new BlockPosition(x + 2, y, z);
        BlockPosition commandAt = new BlockPosition(x, y + 1, z);

        server.ticker().submit(() -> {
            // The sign pair (the ZSD v3 stamp's own rows).
            server.world().setBlock(signAt, BuiltinBlocks.SIGN_SOUTH);
            nether.setBlock(signAt, BuiltinBlocks.SIGN_SOUTH);
            server.signs().set(signAt, new String[]{"Overworld", "", "", ""});
            server.signsFor(-1).set(signAt, new String[]{"Nether", "", "", ""});
            // The jukebox pair (the ZJD v2 stamp).
            server.world().setBlock(jukeboxAt, BuiltinBlocks.JUKEBOX);
            nether.setBlock(jukeboxAt, BuiltinBlocks.JUKEBOX);
            server.jukeboxes().insertFromTick(jukeboxAt, ItemStack.of(RECORD_FAR, 1));
            server.jukeboxesFor(-1).insertFromTick(jukeboxAt, ItemStack.of(RECORD_CAT, 1));
            // The note pair (the ZND v2 stamp) — two tunes each.
            server.world().setBlock(noteAt, BuiltinBlocks.NOTE_BLOCK);
            nether.setBlock(noteAt, BuiltinBlocks.NOTE_BLOCK);
            server.noteBlocks().useFromTick(noteAt);
            server.noteBlocks().useFromTick(noteAt);
            server.noteBlocksFor(-1).useFromTick(noteAt);
            // The banner pair (the ZBD v2 stamp) — a base tile each.
            server.world().setBlock(bannerAt,
                    net.zaminmc.torch.server.block.BannerBlocks.standingFor(8));
            nether.setBlock(bannerAt,
                    net.zaminmc.torch.server.block.BannerBlocks.standingFor(8));
            server.banners().place(bannerAt,
                    new net.zaminmc.torch.server.banner.BannerManager.BannerTile(3,
                            java.util.List.of()));
            server.bannersFor(-1).place(bannerAt,
                    new net.zaminmc.torch.server.banner.BannerManager.BannerTile(14,
                            java.util.List.of()));
            // The beacon pair (the ZBT v2 stamp).
            server.world().setBlock(beaconAt,
                    net.zaminmc.torch.server.beacon.BeaconSystem.BEACON);
            nether.setBlock(beaconAt,
                    net.zaminmc.torch.server.beacon.BeaconSystem.BEACON);
            server.beacons().getOrCreate(beaconAt).setLevels(1);
            server.beacons().getOrCreate(beaconAt).setPrimary(1);
            server.beaconsFor(-1).getOrCreate(beaconAt).setLevels(2);
            server.beaconsFor(-1).getOrCreate(beaconAt).setPrimary(3);
            // The command-block pair (the ZCB v3 stamp) — the block-change
            // listener creates each world's own tile at the placement.
            server.world().setBlock(commandAt, BuiltinBlocks.COMMAND_BLOCK);
            nether.setBlock(commandAt, BuiltinBlocks.COMMAND_BLOCK);
        });
        await(() -> net.zaminmc.torch.server.beacon.BeaconSystem.isBeacon(
                        nether.getBlock(beaconAt))
                        && server.commandBlocks().at(commandAt) != null
                        && server.commandBlocksFor(-1).at(commandAt) != null,
                "both dimensions' tiles placed");
        server.ticker().submit(() -> {
            server.commandBlocks().edit(commandAt, "say overworld", true);
            server.commandBlocksFor(-1).edit(commandAt, "say nether", true);
        });
        await(() -> "say nether".equals(server.commandBlocksFor(-1).at(commandAt).command()),
                "both command rows edited");

        server.shutdown(null);

        // The reboot over the SAME world: each family's tiles answer their
        // own state (the six stamps' round trips).
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                WORLD_NAME, "it", 20, 4, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();

        AtomicReference<String[]> census = new AtomicReference<>();
        server.ticker().submit(() -> {
            String[] rows = new String[12];
            rows[0] = server.signs().peek(signAt) == null ? null : server.signs().peek(signAt)[0];
            rows[1] = server.signsFor(-1).peek(signAt) == null
                    ? null : server.signsFor(-1).peek(signAt)[0];
            rows[2] = server.jukeboxes().recordAt(jukeboxAt) == null
                    || server.jukeboxes().recordAt(jukeboxAt).isEmpty()
                    ? null : server.jukeboxes().recordAt(jukeboxAt).type().identifier().value();
            rows[3] = server.jukeboxesFor(-1).recordAt(jukeboxAt) == null
                    || server.jukeboxesFor(-1).recordAt(jukeboxAt).isEmpty()
                    ? null : server.jukeboxesFor(-1).recordAt(jukeboxAt).type().identifier().value();
            rows[4] = String.valueOf(server.noteBlocks().tileAt(noteAt) == null
                    ? -1 : server.noteBlocks().tileAt(noteAt).note());
            rows[5] = String.valueOf(server.noteBlocksFor(-1).tileAt(noteAt) == null
                    ? -1 : server.noteBlocksFor(-1).tileAt(noteAt).note());
            rows[6] = String.valueOf(server.banners().tileAt(bannerAt) == null
                    ? -1 : server.banners().tileAt(bannerAt).base());
            rows[7] = String.valueOf(server.bannersFor(-1).tileAt(bannerAt) == null
                    ? -1 : server.bannersFor(-1).tileAt(bannerAt).base());
            rows[8] = String.valueOf(server.beacons().peek(beaconAt) == null
                    ? -1 : server.beacons().peek(beaconAt).levels());
            rows[9] = String.valueOf(server.beaconsFor(-1).peek(beaconAt) == null
                    ? -1 : server.beaconsFor(-1).peek(beaconAt).levels());
            rows[10] = server.commandBlocks().at(commandAt) == null
                    ? null : server.commandBlocks().at(commandAt).command();
            rows[11] = server.commandBlocksFor(-1).at(commandAt) == null
                    ? null : server.commandBlocksFor(-1).at(commandAt).command();
            census.set(rows);
        });
        await(() -> census.get() != null, "the census ran");
        String[] rows = census.get();
        assertEquals("Overworld", rows[0], "the overworld sign kept its text (its own row)");
        assertEquals("Nether", rows[1], "the nether sign kept its text (the v3 stamp's own row)");
        assertEquals("record_far", rows[2], "the overworld jukebox kept its disc");
        assertEquals("record_cat", rows[3], "the nether jukebox kept its disc (the v2 stamp's own row)");
        assertEquals("2", rows[4], "the overworld note stayed tuned to 2");
        assertEquals("1", rows[5], "the nether note stayed tuned to 1 (the v2 stamp's own row)");
        assertEquals("3", rows[6], "the overworld banner kept its base");
        assertEquals("14", rows[7], "the nether banner kept its base (the v2 stamp's own row)");
        assertEquals("1", rows[8], "the overworld beacon kept its level");
        assertEquals("2", rows[9], "the nether beacon kept its level (the v2 stamp's own row)");
        assertEquals("say overworld", rows[10], "the overworld command block kept its command");
        assertEquals("say nether", rows[11], "the nether command block kept its command (the v3 stamp's own row)");
    }

    /**
     * THE SIGN-EDIT SEAT: a nether player's edit writes the NETHER family
     * alone — the same-coordinate overworld sign keeps its own text (the
     * reference's player.world at the Update Sign walk).
     */
    @Test
    void theNetherSignEditWritesTheNetherFamilyAlone() throws Exception {
        var nether = server.worldFor(-1);
        int x = 220, y = 40, z = 220;
        overworldPlatform(x, y, z);
        netherPlatform(nether, x, y, z);
        BlockPosition at = new BlockPosition(x, y, z);

        server.ticker().submit(() -> {
            server.world().setBlock(at, BuiltinBlocks.SIGN_SOUTH);
            nether.setBlock(at, BuiltinBlocks.SIGN_SOUTH);
            server.signs().set(at, new String[]{"Keep", "me", "", ""});
        });
        await(() -> BuiltinBlocks.SIGN_SOUTH.equals(nether.getBlock(at))
                        && server.signs().peek(at) != null,
                "both dimensions' signs placed");

        PlayerSession netherPlayer = join("NetherWriter", -1);
        netherPlayer.applyMovement(new Position(x + 0.5, y, z + 1.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the nether player settles");

        server.editSign(netherPlayer, at, new String[]{"Nether", "rules", "", ""});
        await(() -> server.signsFor(-1).peek(at) != null
                        && "Nether".equals(server.signsFor(-1).peek(at)[0]),
                "the nether sign's text landed");
        assertArrayEquals(new String[]{"Nether", "rules", "", ""},
                server.signsFor(-1).peek(at),
                "the nether family carries the edit");
        assertArrayEquals(new String[]{"Keep", "me", "", ""},
                server.signs().peek(at),
                "the overworld twin at the SAME coordinates is untouched");
    }

    /**
     * THE NOTE-USE SEAT: a nether player's use tunes the NETHER family's
     * tile — the same-coordinate overworld note stays at its own pitch
     * (the reference's player.world at the use).
     */
    @Test
    void theNoteUseTunesTheUsersOwnWorld() throws Exception {
        var nether = server.worldFor(-1);
        int x = 230, y = 40, z = 230;
        overworldPlatform(x, y, z);
        netherPlatform(nether, x, y, z);
        BlockPosition at = new BlockPosition(x, y, z);

        server.ticker().submit(() -> {
            server.world().setBlock(at, BuiltinBlocks.NOTE_BLOCK);
            nether.setBlock(at, BuiltinBlocks.NOTE_BLOCK);
        });
        await(() -> BuiltinBlocks.NOTE_BLOCK.equals(nether.getBlock(at)),
                "both dimensions' note blocks placed");

        PlayerSession netherPlayer = join("NetherTuner", -1);
        netherPlayer.applyMovement(new Position(x + 0.5, y, z + 1.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the nether player settles");

        server.useItemOnBlock(netherPlayer, at, 1, java.util.Optional.empty(), id -> { });
        await(() -> server.noteBlocksFor(-1).tileAt(at) != null
                        && server.noteBlocksFor(-1).tileAt(at).note() == 1,
                "the nether note tuned once");
        assertEquals(1, server.noteBlocksFor(-1).tileAt(at).note(),
                "the nether family's tile carries the tune");
        assertTrue(server.noteBlocks().tileAt(at) == null
                        || server.noteBlocks().tileAt(at).note() == 0,
                "the overworld twin at the SAME coordinates stays at pitch 0");
    }

    /**
     * THE BEACON-OPEN SEAT: a nether player's beacon window reads the
     * NETHER family — the open stamps the session's dimension (the
     * furnace/chest open law), so the window's level reads the nether
     * beacon's own pyramid, never the overworld twin's.
     */
    @Test
    void theBeaconWindowOpenReadsTheActingDimensionsFamily() throws Exception {
        var nether = server.worldFor(-1);
        int x = 240, y = 40, z = 240;
        overworldPlatform(x, y, z);
        netherPlatform(nether, x, y, z);
        BlockPosition at = new BlockPosition(x, y, z);

        server.ticker().submit(() -> {
            server.world().setBlock(at, net.zaminmc.torch.server.beacon.BeaconSystem.BEACON);
            nether.setBlock(at, net.zaminmc.torch.server.beacon.BeaconSystem.BEACON);
            server.beacons().getOrCreate(at).setLevels(1);
            server.beaconsFor(-1).getOrCreate(at).setLevels(4);
        });
        await(() -> net.zaminmc.torch.server.beacon.BeaconSystem.isBeacon(nether.getBlock(at))
                        && server.beaconsFor(-1).peek(at) != null,
                "both dimensions' beacons placed");

        PlayerSession netherPlayer = join("NetherBeaconer", -1);
        netherPlayer.applyMovement(new Position(x + 0.5, y, z + 1.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the nether player settles");

        var opened = new java.util.concurrent.atomic.AtomicInteger();
        server.useItemOnBlock(netherPlayer, at, 1, java.util.Optional.empty(),
                id -> opened.incrementAndGet());
        await(() -> opened.get() > 0, "the nether beacon window opened");
        assertEquals(-1, netherPlayer.openContainerDimension(),
                "the open stamped the window with the NETHER's dimension");
        assertEquals(PlayerSession.ContainerKind.BEACON,
                netherPlayer.openContainerKind(),
                "the window's kind is the beacon");
        // The window's beacon is the NETHER family's own body: its levels
        // read 4, the overworld twin's 1.
        var beacon = server.beaconsFor(netherPlayer.openContainerDimension())
                .peek(netherPlayer.openContainerPosition());
        assertTrue(beacon != null && beacon.levels() == 4,
                "the open window reads the NETHER family's beacon (levels 4)");
        assertEquals(1, server.beacons().peek(at).levels(),
                "the overworld twin keeps its own level 1");
    }
}
