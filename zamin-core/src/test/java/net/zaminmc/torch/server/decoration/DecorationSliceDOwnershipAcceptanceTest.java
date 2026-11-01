package net.zaminmc.torch.server.decoration;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE DECORATION FAMILY'S PER-DIMENSION STORY (FL4-GAP2 Slice D — the
 * transient families' row): the reference's hanging bodies live in their
 * own WorldServer — a nether frame is a NETHER body (the nether's
 * survival poll reads the nether's walls, the nether's comparator reads
 * the nether's frames), and the overworld's frame at the SAME
 * coordinates is a DIFFERENT body. The pre-Slice engine kept ONE shared
 * registry: every dimension's comparator read the overworld's frames
 * (the FL4-F10 comment's own "honest tail"), and the nether's frames
 * never existed at all (the one family answered every world).
 *
 * <p>THE SEATS: the nether comparator reading only the nether's frames
 * (the through-the-wall consult's routing — the named leak), and the
 * two-tile restart through a real reboot (the ZED v2 stamp's round
 * trip: each dimension's frames and paintings restore into their own
 * families).</p>
 */
class DecorationSliceDOwnershipAcceptanceTest {

    /** The restart seat's fixed world name (the store rides the world's own data dir). */
    private static final String WORLD_NAME = "slicedecorations";

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                WORLD_NAME, "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /**
     * THE NAMED LEAK'S SEAT: a nether comparator reads only the NETHER's
     * frames — the nether frame at the cell carries one rotation, the
     * overworld frame at the SAME coordinates another, and each
     * dimension's through-the-wall consult answers its own body's
     * signal (the pre-Slice shared registry answered the overworld's
     * frame for every world's comparator).
     */
    @Test
    void theNetherComparatorReadsOnlyTheNetherFrames() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int fy = 100;
        int cx = 64, cz = 64;
        platform(nether, cx, cz, fy);
        platform(server.world(), cx, cz, fy);

        // The wall block the frames hang on (both worlds, same
        // coordinates); the frames land in its east cell facing east.
        BlockPosition wall = new BlockPosition(cx, fy + 1, cz);
        BlockPosition frameCell = new BlockPosition(cx + 1, fy + 1, cz);
        server.ticker().submit(() -> {
            nether.setBlock(wall, BuiltinBlocks.STONE);
            server.world().setBlock(wall, BuiltinBlocks.STONE);
        });
        await(() -> BuiltinBlocks.STONE.equals(nether.getBlock(wall))
                && BuiltinBlocks.STONE.equals(server.world().getBlock(wall)),
                "both worlds' walls landed");

        // The nether's frame: a diamond at rotation 2 (signal 3). The
        // overworld's frame at the SAME cell: a diamond at rotation 5
        // (signal 6) — the display carries the signal (a bare frame
        // answers 0, getAnalogSignal's own law).
        AtomicReference<ItemFrame> netherFrame = new AtomicReference<>();
        AtomicReference<ItemFrame> overworldFrame = new AtomicReference<>();
        server.ticker().submit(() -> {
            netherFrame.set(server.decorationsFor(-1)
                    .restoreFrame(frameCell, 3,
                            ItemStack.of(BuiltinItems.DIAMOND, 1), 2));
            overworldFrame.set(server.decorations()
                    .restoreFrame(frameCell, 3,
                            ItemStack.of(BuiltinItems.DIAMOND, 1), 5));
        });
        await(() -> netherFrame.get() != null && overworldFrame.get() != null,
                "both dimensions' frames landed at the same coordinates");
        assertEquals(-1, netherFrame.get().dimension(), "the nether frame's own-world stamp");
        assertEquals(0, overworldFrame.get().dimension(), "the overworld frame's own-world stamp");

        // THE DISCRIMINATION: each dimension's through-the-wall consult
        // answers its OWN body (rotation + 1 = the analog signal).
        assertEquals(3, server.decorationsFor(-1).frameComparatorRead(frameCell, 3),
                "the nether comparator reads the NETHER frame's signal (3)");
        assertEquals(6, server.decorations().frameComparatorRead(frameCell, 3),
                "the overworld comparator reads the OVERWORLD frame's signal (6)");
        // The facing gate still holds per world: a west-facing comparator
        // reads nothing in either family.
        assertEquals(-1, server.decorationsFor(-1).frameComparatorRead(frameCell, 0),
                "a west-facing nether comparator never reads the east-facing frame");
        assertEquals(-1, server.decorations().frameComparatorRead(frameCell, 0),
                "a west-facing overworld comparator never reads the east-facing frame");
    }

    /**
     * THE RESTART SEAT: the two-tile reboot — a nether frame and an
     * overworld frame at the SAME coordinates carrying different
     * contents, a nether painting and an overworld painting at the same
     * anchor, a real shutdown + reboot, and each dimension's family
     * answering its own bodies (the ZED v2 stamp's round trip).
     */
    @Test
    void theTwoTileRestartThroughARealReboot() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int fy = 100;
        int cx = 64, cz = 64;
        platform(nether, cx, cz, fy);
        platform(server.world(), cx, cz, fy);

        BlockPosition frameWall = new BlockPosition(cx, fy + 1, cz);
        BlockPosition frameCell = new BlockPosition(cx + 1, fy + 1, cz);
        BlockPosition paintingWall = new BlockPosition(cx, fy + 1, cz + 2);
        BlockPosition paintingCell = new BlockPosition(cx + 1, fy + 1, cz + 2);
        server.ticker().submit(() -> {
            nether.setBlock(frameWall, BuiltinBlocks.STONE);
            server.world().setBlock(frameWall, BuiltinBlocks.STONE);
            nether.setBlock(paintingWall, BuiltinBlocks.STONE);
            server.world().setBlock(paintingWall, BuiltinBlocks.STONE);
        });
        await(() -> BuiltinBlocks.STONE.equals(nether.getBlock(frameWall))
                && BuiltinBlocks.STONE.equals(server.world().getBlock(paintingWall)),
                "both worlds' walls landed");

        // The live placements: each dimension's family takes its own
        // frame (with its own display through the interact arm) and its
        // own painting.
        AtomicReference<Boolean> netherPlaced = new AtomicReference<>();
        AtomicReference<Boolean> overworldPlaced = new AtomicReference<>();
        server.ticker().submit(() -> {
            netherPlaced.set(server.decorationsFor(-1).placeFrame(frameWall, 5, 3));
            overworldPlaced.set(server.decorations().placeFrame(frameWall, 5, 3));
        });
        await(() -> netherPlaced.get() != null && overworldPlaced.get() != null,
                "both frames' placements answered");
        assertTrue(netherPlaced.get(), "the nether frame landed");
        assertTrue(overworldPlaced.get(), "the overworld frame landed at the same coordinates");

        AtomicReference<ItemFrame> netherFrame = new AtomicReference<>();
        AtomicReference<ItemFrame> overworldFrame = new AtomicReference<>();
        server.ticker().submit(() -> {
            netherFrame.set(server.decorationsFor(-1).frameAt(frameCell));
            overworldFrame.set(server.decorations().frameAt(frameCell));
            // The displays through the interact arm: the nether's frame
            // holds a diamond, the overworld's a stick.
            server.decorationsFor(-1).interactFrame(netherFrame.get(),
                    ItemStack.of(BuiltinItems.DIAMOND, 1));
            server.decorations().interactFrame(overworldFrame.get(),
                    ItemStack.of(BuiltinItems.STICK, 1));
        });
        await(() -> netherFrame.get() != null && overworldFrame.get() != null,
                "both frames found in their own families");
        // The inserts land inside the SAME ticker task as the lookups —
        // await the DISPLAYS, never the references (the task's tail
        // races a reference-only await).
        await(() -> !netherFrame.get().display().isEmpty()
                        && !overworldFrame.get().display().isEmpty(),
                "both frames' displays landed");
        assertEquals(BuiltinItems.DIAMOND, netherFrame.get().display().type(),
                "the nether frame holds the diamond");
        assertEquals(BuiltinItems.STICK, overworldFrame.get().display().type(),
                "the overworld frame holds the stick");

        AtomicReference<Boolean> netherPainting = new AtomicReference<>();
        AtomicReference<Boolean> overworldPainting = new AtomicReference<>();
        server.ticker().submit(() -> {
            netherPainting.set(server.decorationsFor(-1).placePainting(paintingWall, 5, 3));
            overworldPainting.set(server.decorations().placePainting(paintingWall, 5, 3));
        });
        await(() -> netherPainting.get() != null && overworldPainting.get() != null,
                "both paintings' placements answered");
        assertTrue(netherPainting.get() && overworldPainting.get(),
                "both paintings landed at the same anchor");

        // The real reboot (the shutdown's saveAllNow + a fresh boot over
        // the SAME world — the store rides the world's own data dir).
        server.shutdown(null);
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                WORLD_NAME, "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();

        AtomicReference<ItemFrame> rebootNetherFrame = new AtomicReference<>();
        AtomicReference<ItemFrame> rebootOverworldFrame = new AtomicReference<>();
        AtomicReference<Painting> rebootNetherPainting = new AtomicReference<>();
        AtomicReference<Painting> rebootOverworldPainting = new AtomicReference<>();
        AtomicReference<int[]> rebootIds = new AtomicReference<>();
        server.ticker().submit(() -> {
            rebootNetherFrame.set(server.decorationsFor(-1).frameAt(frameCell));
            rebootOverworldFrame.set(server.decorations().frameAt(frameCell));
            rebootNetherPainting.set(server.decorationsFor(-1).paintingAt(paintingCell));
            rebootOverworldPainting.set(server.decorations().paintingAt(paintingCell));
            rebootIds.set(new int[]{
                    rebootNetherFrame.get() == null ? -1 : rebootNetherFrame.get().entityId(),
                    rebootOverworldFrame.get() == null ? -1 : rebootOverworldFrame.get().entityId()});
        });
        await(() -> rebootIds.get() != null, "the reboot census ran");
        assertNotNull(rebootNetherFrame.get(),
                "the nether frame restored into the NETHER family");
        assertNotNull(rebootOverworldFrame.get(),
                "the overworld frame restored into the overworld family");
        assertEquals(BuiltinItems.DIAMOND, rebootNetherFrame.get().display().type(),
                "the nether frame still holds the diamond (its own row's round trip)");
        assertEquals(BuiltinItems.STICK, rebootOverworldFrame.get().display().type(),
                "the overworld frame still holds the stick");
        assertEquals(-1, rebootNetherFrame.get().dimension(),
                "the restored nether frame's stamp");
        assertEquals(0, rebootOverworldFrame.get().dimension(),
                "the restored overworld frame's stamp");
        assertTrue(rebootIds.get()[0] != rebootIds.get()[1],
                "the shared allocator never re-issues one id across the worlds");
        assertNotNull(rebootNetherPainting.get(),
                "the nether painting restored into the NETHER family");
        assertNotNull(rebootOverworldPainting.get(),
                "the overworld painting restored into the overworld family");
    }

    // ------------------------------------------------------------------
    // The harness
    // ------------------------------------------------------------------

    /** A stone floor at y=100 with cleared air above, in the given world. */
    private void platform(EngineWorld world, int cx, int cz, int fy) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 4; dx++) {
                for (int dz = -3; dz <= 4; dz++) {
                    world.setBlock(new BlockPosition(cx + dx, fy, cz + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 5; y++) {
                        world.setBlock(new BlockPosition(cx + dx, y, cz + dz),
                                world.airType());
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(cx, fy, cz))),
                40, "the platform landed in " + world.dimension());
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
