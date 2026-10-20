package net.zaminmc.torch.server.decoration;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decoration family on the live engine (Slice 28a/28b) — the reference
 * port of entity/decoration/{DecorationEntity, ItemFrameEntity,
 * PaintingEntity} + item/DecorationItem: the placement gates (the vertical
 * faces, the solid wall, the clear box, the overlap refusal), the frame's
 * interact ladder (the size-1 copy insert, the rotation walk), the frame's
 * attack ladder (the item eject against the break), the comparator's
 * through-the-wall rotation read, the painting's motive fit roll, the
 * survival poll (the broken wall), and the ZED persistence round trip.
 */
class DecorationSystemTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The motive registry (PaintingEntity.Motive lines 112-138)
    // ------------------------------------------------------------------

    @Test
    void theMotiveRegistryCarriesTheReferenceTable() {
        assertEquals(26, Painting.Motive.values().length);
        // The reference's own declaration order + the pixel sizes.
        assertEquals("Kebab", Painting.Motive.KEBAB.name);
        assertEquals(16, Painting.Motive.KEBAB.width);
        assertEquals(16, Painting.Motive.KEBAB.height);
        assertEquals(64, Painting.Motive.POINTER.width);
        assertEquals(64, Painting.Motive.POINTER.height);
        assertEquals(64, Painting.Motive.SKELETON.width);
        assertEquals(48, Painting.Motive.SKELETON.height);
        assertEquals(64, Painting.Motive.DONKEY_KONG.width);
        assertEquals(48, Painting.Motive.DONKEY_KONG.height);
        assertEquals(13, Painting.Motive.SKULL_AND_ROSES_LENGTH,
                "the reference's own length constant");
        // The registry-name lookup (readCustomNbt's walk).
        assertEquals(Painting.Motive.BURNING_SKULL, Painting.Motive.byName("BurningSkull"));
        assertNull(Painting.Motive.byName("NotArt"));
    }

    // ------------------------------------------------------------------
    // The frame's placement (DecorationItem.useOn lines 21-42)
    // ------------------------------------------------------------------

    @Test
    void theFramePlacementGatesWalk() throws Exception {
        EngineServer server = boot();
        try {
            // A stone pillar: the wall cell (10,40,10), the open cell to
            // its east (11,40,10).
            place(server, 10, 39, 10, BuiltinBlocks.STONE, 10, 40, 10, BuiltinBlocks.STONE);
            BlockPosition wall = new BlockPosition(10, 40, 10);
            BlockPosition eastCell = new BlockPosition(11, 40, 10);
            await(server, () -> server.world().getBlock(wall)
                    .identifier().value().equals("stone"), "the wall placed");

            // The vertical faces refuse outright (useOn's own gate).
            AtomicReference<Boolean> upFace = new AtomicReference<>();
            server.ticker().submit(() -> upFace.set(
                    server.decorations().placeFrame(wall, 1, 3)));
            await(server, () -> upFace.get() != null, "the UP face answered");
            assertFalse(upFace.get(), "the UP face never places");
            assertEquals(0, countFrames(server), "nothing landed");

            // The east face: the frame lands in the east cell facing east.
            AtomicReference<Boolean> east = new AtomicReference<>();
            server.ticker().submit(() -> east.set(
                    server.decorations().placeFrame(wall, 5, 3)));
            await(server, () -> east.get() != null, "the east face answered");
            assertTrue(east.get(), "the east face landed the frame");
            ItemFrame frame = server.decorations().frameAt(eastCell);
            assertNotNull(frame, "the frame hangs in the east cell");
            assertEquals(3, frame.facing(), "the frame faces east");
            assertEquals(server.decorations().firstId(), frame.entityId(),
                    "the id band's first body");

            // The same cell never takes a second frame (the overlap gate —
            // canSurvive's own getEntities walk).
            AtomicReference<Boolean> again = new AtomicReference<>();
            server.ticker().submit(() -> again.set(
                    server.decorations().placeFrame(wall, 5, 3)));
            await(server, () -> again.get() != null, "the second attempt answered");
            assertFalse(again.get(), "the overlap refused the second frame");

            // A frame against air never lands (the wall gate).
            AtomicReference<Boolean> airWall = new AtomicReference<>();
            server.ticker().submit(() -> airWall.set(
                    server.decorations().placeFrame(new BlockPosition(20, 40, 20), 5, 3)));
            await(server, () -> airWall.get() != null, "the air wall answered");
            assertFalse(airWall.get(), "a frame against air never lands");

            // A frame into a solid cell never lands (the box gate).
            place(server, 10, 40, 12, BuiltinBlocks.STONE, 11, 40, 12, BuiltinBlocks.STONE);
            await(server, () -> server.world().getBlock(new BlockPosition(11, 40, 12))
                    .identifier().value().equals("stone"), "the solid cell placed");
            AtomicReference<Boolean> solidCell = new AtomicReference<>();
            server.ticker().submit(() -> solidCell.set(
                    server.decorations().placeFrame(new BlockPosition(10, 40, 12), 5, 3)));
            await(server, () -> solidCell.get() != null, "the solid cell answered");
            assertFalse(solidCell.get(), "a frame into a solid cell never lands");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The restore arms (the Anvil import's exact-cell form)
    // ------------------------------------------------------------------

    @Test
    void theRestoreArmsLandTheExactCellsWithoutTheFitRoll() throws Exception {
        // The reference's readCustomNbt load walk: the saved anchor,
        // facing, motive, display and rotation land WHOLE — no placement
        // fit roll, no survival gate (a wall-less body stays until the
        // per-100-tick validation poll drops it, exactly the reference's
        // own load), and a duplicate anchor refuses loudly rather than
        // silently evicting the first body.
        EngineServer server = boot();
        try {
            // The frame: an exact cell against OPEN AIR (the survival
            // gate would refuse on the placement path — the restore arm
            // lands it anyway, the poll owns the later drop).
            BlockPosition frameCell = new BlockPosition(30, 40, 30);
            AtomicReference<ItemFrame> frame = new AtomicReference<>();
            server.ticker().submit(() -> frame.set(
                    server.decorations().restoreFrame(frameCell, 3,
                            ItemStack.of(BuiltinItems.DIAMOND, 1), 5)));
            await(server, () -> frame.get() != null, "the restore answered");
            assertNotNull(frame.get(), "the frame landed against open air (no survival gate)");
            assertEquals(3, frame.get().facing(), "the saved facing lands whole");
            assertEquals("diamond", frame.get().display().type().identifier().value(),
                    "the saved display stack lands (the size-1 copy rule)");
            assertEquals(1, frame.get().display().count(), "the frame never holds more than one");
            assertEquals(5, frame.get().rotation(), "the saved rotation lands whole");
            assertNotNull(server.decorations().frameAt(frameCell),
                    "the frame hangs in the exact saved cell");

            // The duplicate anchor refuses (the occupied-cell rule — the
            // engine keys frames by wall cell, so a second body would
            // silently evict the first; the restore arm refuses instead).
            AtomicReference<ItemFrame> duplicate = new AtomicReference<>(frame.get());
            server.ticker().submit(() -> duplicate.set(
                    server.decorations().restoreFrame(frameCell, 1, null, 0)));
            await(server, () -> duplicate.get() == null, "the duplicate answered");
            assertNull(duplicate.get(), "the occupied anchor refused the second frame");
            assertEquals(3, server.decorations().frameAt(frameCell).facing(),
                    "the first body keeps its cell");

            // The painting: the exact motive lands whole (the placement
            // path's fit roll never runs — the saved variant IS the
            // variant), against open air, with the same duplicate rule.
            BlockPosition paintingCell = new BlockPosition(35, 40, 35);
            AtomicReference<Painting> painting = new AtomicReference<>();
            server.ticker().submit(() -> painting.set(
                    server.decorations().restorePainting(paintingCell, 0,
                            Painting.Motive.KEBAB)));
            await(server, () -> painting.get() != null, "the painting restore answered");
            assertNotNull(painting.get(), "the painting landed against open air");
            assertEquals(Painting.Motive.KEBAB, painting.get().motive(),
                    "the saved motive lands whole — no fit roll");
            assertEquals(0, painting.get().facing(), "the saved facing");
            assertNotNull(server.decorations().paintingAt(paintingCell));
            AtomicReference<Painting> duplicatePainting = new AtomicReference<>(painting.get());
            server.ticker().submit(() -> duplicatePainting.set(
                    server.decorations().restorePainting(paintingCell, 2,
                            Painting.Motive.BUST)));
            await(server, () -> duplicatePainting.get() == null, "the duplicate painting answered");
            assertNull(duplicatePainting.get(), "the occupied anchor refused the second painting");

            // The wall-less bodies drop at the validation poll exactly
            // like the reference's own load (the per-100-tick sweep).
            await(server, () -> server.decorations().frameAt(frameCell) == null
                            && server.decorations().paintingAt(paintingCell) == null,
                    "the validation poll swept the wall-less bodies");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The frame's interact ladder (ItemFrameEntity.interact lines 180-194)
    // ------------------------------------------------------------------

    @Test
    void theFrameInteractInsertsAndRotates() throws Exception {
        EngineServer server = boot();
        try {
            place(server, 10, 39, 10, BuiltinBlocks.STONE, 10, 40, 10, BuiltinBlocks.STONE);
            BlockPosition wall = new BlockPosition(10, 40, 10);
            BlockPosition eastCell = new BlockPosition(11, 40, 10);
            await(server, () -> server.world().getBlock(wall)
                    .identifier().value().equals("stone"), "the wall placed");
            server.ticker().submit(() -> server.decorations().placeFrame(wall, 5, 3));
            await(server, () -> server.decorations().frameAt(eastCell) != null, "the frame hung");

            // The bare-hand click on a bare frame: consumed, nothing.
            ItemFrame frame = server.decorations().frameAt(eastCell);
            AtomicReference<DecorationSystem.FrameInteract> bare =
                    new AtomicReference<>();
            server.ticker().submit(() -> bare.set(
                    server.decorations().interactFrame(frame, ItemStack.EMPTY)));
            await(server, () -> bare.get() != null, "the bare click answered");
            assertEquals(DecorationSystem.FrameInteract.NOTHING, bare.get());

            // The insert: a copy at size 1 (setDisplayItem's own rule).
            ItemStack held = ItemStack.of(BuiltinItems.DIAMOND, 3);
            AtomicReference<DecorationSystem.FrameInteract> inserted =
                    new AtomicReference<>();
            server.ticker().submit(() -> inserted.set(
                    server.decorations().interactFrame(frame, held)));
            await(server, () -> inserted.get() != null, "the insert answered");
            assertEquals(DecorationSystem.FrameInteract.INSERTED, inserted.get());
            assertEquals(BuiltinItems.DIAMOND, frame.display().type());
            assertEquals(1, frame.display().count(), "the frame holds the count-1 copy");
            assertEquals(3, held.count(), "the held stack is the caller's own consume");

            // The rotation walk: each click turns one step, wrapping at 8.
            for (int expected = 1; expected <= 8; expected++) {
                AtomicReference<DecorationSystem.FrameInteract> turned =
                        new AtomicReference<>();
                final int want = expected;
                server.ticker().submit(() -> turned.set(
                        server.decorations().interactFrame(frame, ItemStack.EMPTY)));
                await(server, () -> turned.get() != null, "the turn " + want + " answered");
                assertEquals(DecorationSystem.FrameInteract.ROTATED, turned.get());
                assertEquals(expected % 8, frame.rotation(), "the rotation walk");
            }

            // The comparator read (getAnalogSignal): rotation + 1.
            assertEquals(8 % 8 + 1, frame.analogSignal());
            // The through-the-wall consult: the east cell, a comparator
            // reading east, answers the frame's signal; a wrong facing or
            // an absent frame answers nothing.
            assertEquals(frame.analogSignal(),
                    server.decorations().frameComparatorRead(eastCell, 3));
            assertEquals(-1, server.decorations().frameComparatorRead(eastCell, 0),
                    "a west-facing comparator never reads the east-facing frame");
            assertEquals(-1, server.decorations().frameComparatorRead(
                    new BlockPosition(30, 40, 30), 3), "an empty cell answers nothing");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The frame's attack ladder (takeDamage lines 40-55 + onAttack)
    // ------------------------------------------------------------------

    @Test
    void theFrameAttackLadderWalks() throws Exception {
        EngineServer server = boot();
        try {
            place(server, 10, 39, 10, BuiltinBlocks.STONE, 10, 40, 10, BuiltinBlocks.STONE);
            BlockPosition wall = new BlockPosition(10, 40, 10);
            BlockPosition eastCell = new BlockPosition(11, 40, 10);
            await(server, () -> server.world().getBlock(wall)
                    .identifier().value().equals("stone"), "the wall placed");
            server.ticker().submit(() -> server.decorations().placeFrame(wall, 5, 3));
            await(server, () -> server.decorations().frameAt(eastCell) != null, "the frame hung");
            ItemFrame frame = server.decorations().frameAt(eastCell);
            server.ticker().submit(() -> server.decorations().interactFrame(frame,
                    ItemStack.of(BuiltinItems.DIAMOND, 1)));
            await(server, () -> !frame.display().isEmpty(), "the diamond inserted");

            // The eject arm (takeDamage lines 45-51): a non-explosive hit
            // on a loaded frame drops the item and keeps the frame on the
            // wall. The drop rides the engine's item entities.
            int itemsBefore = countItemEntities(server);
            AtomicReference<Boolean> ejected = new AtomicReference<>();
            server.ticker().submit(() -> ejected.set(
                    server.decorations().attackFrame(frame, false, false)));
            await(server, () -> ejected.get() != null, "the eject answered");
            assertTrue(ejected.get());
            assertTrue(frame.display().isEmpty(), "the item left the frame");
            assertNotNull(server.decorations().frameAt(eastCell), "the frame stays on the wall");
            final int base = itemsBefore;
            await(server, () -> server.itemEntities().all().size() >= base + 1,
                    "the diamond dropped");

            // The break arm (the base takeDamage): a hit on the bare frame
            // takes it off the wall — the frame item drops too.
            AtomicReference<Boolean> broken = new AtomicReference<>();
            server.ticker().submit(() -> broken.set(
                    server.decorations().attackFrame(frame, false, false)));
            await(server, () -> broken.get() != null, "the break answered");
            assertTrue(broken.get());
            assertNull(server.decorations().frameAt(eastCell), "the frame left the wall");
            await(server, () -> server.itemEntities().all().size() >= base + 2,
                    "the frame item dropped");

            // The creative break: the body leaves without any drop.
            server.ticker().submit(() -> server.decorations().placeFrame(wall, 5, 3));
            await(server, () -> server.decorations().frameAt(eastCell) != null,
                    "the frame re-hung");
            ItemFrame creative = server.decorations().frameAt(eastCell);
            int beforeCreative = countItemEntities(server);
            final int creativeBase = beforeCreative;
            server.ticker().submit(() ->
                    server.decorations().attackFrame(creative, true, false));
            await(server, () -> server.decorations().frameAt(eastCell) == null,
                    "the creative break swept the frame");
            await(server, () -> server.itemEntities().all().size() == creativeBase,
                    "the creative hand drops nothing");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The painting's fit roll (PaintingEntity's ctor lines 21-38)
    // ------------------------------------------------------------------

    @Test
    void thePaintingFitRollCollectsAndRolls() throws Exception {
        EngineServer server = boot();
        try {
            // A two-tall, one-wide wall: the tall art (48/64 pixel heights)
            // cannot fit; the wide art cannot fit either (one wall cell
            // along the counter-clockwise walk).
            place(server, 10, 39, 10, BuiltinBlocks.STONE, 10, 40, 10, BuiltinBlocks.STONE);
            server.ticker().submit(() ->
                    server.world().setBlock(new BlockPosition(10, 41, 10), BuiltinBlocks.STONE));
            BlockPosition wall = new BlockPosition(10, 40, 10);
            BlockPosition eastCell = new BlockPosition(11, 40, 10);
            await(server, () -> server.world().getBlock(new BlockPosition(10, 41, 10))
                    .identifier().value().equals("stone"), "the wall placed");

            AtomicReference<Boolean> landed = new AtomicReference<>();
            server.ticker().submit(() -> landed.set(
                    server.decorations().placePainting(wall, 5, 3)));
            await(server, () -> landed.get() != null, "the fit roll answered");
            assertTrue(landed.get(), "the painting landed");
            Painting painting = server.decorations().paintingAt(eastCell);
            assertNotNull(painting);
            // The rolled motive must FIT the wall (the fit walk's own
            // collect: the 48/64-tall entries refuse — only three wall
            // cells exist but the width walk needs four cells for the
            // 64-wide; the height walk needs three+ for the 48-tall).
            assertTrue(painting.motive().height <= 32,
                    "the roll never picks art too tall for the wall: "
                            + painting.motive().name);
            assertTrue(painting.motive().width <= 16,
                    "the roll never picks art too wide for the wall: "
                            + painting.motive().name);
            assertEquals(3, painting.facing(), "the painting faces east");

            // The break: the body falls (the creative hand spares the
            // drop — the registry is the observable here).
            server.ticker().submit(() ->
                    server.decorations().attackPainting(painting, true));
            await(server, () -> server.decorations().paintingAt(eastCell) == null,
                    "the painting fell");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The survival poll (DecorationEntity.tick lines 80-91)
    // ------------------------------------------------------------------

    @Test
    void theSurvivalPollSweepsTheBrokenWall() throws Exception {
        EngineServer server = boot();
        try {
            place(server, 10, 39, 10, BuiltinBlocks.STONE, 10, 40, 10, BuiltinBlocks.STONE);
            BlockPosition wall = new BlockPosition(10, 40, 10);
            BlockPosition eastCell = new BlockPosition(11, 40, 10);
            await(server, () -> server.world().getBlock(wall)
                    .identifier().value().equals("stone"), "the wall placed");
            server.ticker().submit(() -> server.decorations().placeFrame(wall, 5, 3));
            await(server, () -> server.decorations().frameAt(eastCell) != null, "the frame hung");

            // The wall breaks: the poll must find it within its 100-tick
            // cadence on the engine's own clock (the reference's grace —
            // the body hangs that long, never longer by much).
            server.ticker().submit(() ->
                    server.world().setBlock(wall, server.world().airType()));
            await(server, () -> server.world().getBlock(wall)
                    .identifier().value().equals("air"), "the wall broke");
            // The grace: right after the break the frame still hangs (the
            // poll's boundary has not come yet — the placement was
            // moments ago).
            assertNotNull(server.decorations().frameAt(eastCell),
                    "the frame hangs the poll's grace period");
            // The sweep: the 100-tick boundary lands within ~5s at 20 TPS.
            await(server, () -> server.decorations().frameAt(eastCell) == null,
                    "the broken wall swept the frame");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The ZED persistence round trip
    // ------------------------------------------------------------------

    @Test
    void theZedRoundTripSurvivesTheStore() throws Exception {
        EngineServer server = boot();
        Path store = dataDir.resolve("zed-roundtrip").resolve("decorations.bin");
        try {
            place(server, 10, 39, 10, BuiltinBlocks.STONE, 10, 40, 10, BuiltinBlocks.STONE);
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(10, 41, 10), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(10, 43, 10), BuiltinBlocks.STONE);
            });
            BlockPosition wall = new BlockPosition(10, 40, 10);
            BlockPosition eastCell = new BlockPosition(11, 40, 10);
            BlockPosition paintingWall = new BlockPosition(10, 43, 10);
            BlockPosition paintingCell = new BlockPosition(11, 43, 10);
            await(server, () -> server.world().getBlock(paintingWall)
                    .identifier().value().equals("stone"), "the walls placed");

            // The frame hangs at the pillar's middle cell, the painting on
            // its own wall below — the two never overlap (canSurvive's own
            // decoration scan).
            server.ticker().submit(() -> {
                server.decorations().placeFrame(wall, 5, 3);
                server.decorations().placePainting(paintingWall, 5, 3);
            });
            await(server, () -> server.decorations().frameAt(eastCell) != null
                            && server.decorations().paintingAt(paintingCell) != null,
                    "both bodies hung");
            ItemFrame frame = server.decorations().frameAt(eastCell);
            server.ticker().submit(() -> {
                server.decorations().interactFrame(frame, ItemStack.of(BuiltinItems.DIAMOND, 1));
                server.decorations().interactFrame(frame, ItemStack.EMPTY);
                server.decorations().interactFrame(frame, ItemStack.EMPTY); // rotation 2
            });
            await(server, () -> frame.rotation() == 2, "the diamond rotated to 2");

            // The save.
            AtomicReference<DecorationSystem.Snapshot> snapshot = new AtomicReference<>();
            server.ticker().submit(() -> snapshot.set(server.decorations().snapshot()));
            await(server, () -> snapshot.get() != null, "the snapshot taken");
            new DecorationDataStore(store).save(snapshot.get().frames(),
                    snapshot.get().paintings());
            assertTrue(Files.exists(store), "the store wrote");
        } finally {
            server.shutdown(() -> { });
        }

        // The reload: the store's own round trip carries the frame's
        // stack + rotation + facing and the painting's motive.
        DecorationDataStore.DecorationSnapshot loaded =
                new DecorationDataStore(store).load();
        assertEquals(1, loaded.frames().size());
        assertEquals(1, loaded.paintings().size());
        DecorationSystem.FrameRecord frame = loaded.frames().get(0);
        assertEquals(new BlockPosition(11, 40, 10), frame.pos());
        assertEquals(3, frame.facing());
        assertEquals(BuiltinItems.DIAMOND, frame.item().type());
        assertEquals(1, frame.item().count());
        assertEquals(2, frame.rotation());
        DecorationSystem.PaintingRecord painting = loaded.paintings().get(0);
        assertEquals(new BlockPosition(11, 43, 10), painting.pos());
        assertNotNull(Painting.Motive.byName(painting.motive()), "the motive resolves");
    }

    // ------------------------------------------------------------------
    // Helpers (the jukebox test's own shape)
    // ------------------------------------------------------------------

    private static int countFrames(EngineServer server) throws InterruptedException {
        return countOn(server, () -> server.decorations().allFrames().size());
    }

    private static int countItemEntities(EngineServer server) {
        try {
            Integer count = countOn(server, () -> server.itemEntities().all().size());
            return count == null ? 0 : count;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }

    private static <T> T countOn(EngineServer server, java.util.function.Supplier<T> read)
            throws InterruptedException {
        AtomicReference<T> value = new AtomicReference<>();
        server.ticker().submit(() -> value.set(read.get()));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && value.get() == null) {
            Thread.sleep(5);
        }
        return value.get();
    }

    private static void place(EngineServer server, int fx, int fy, int fz,
                              net.zaminmc.torch.block.BlockType floor,
                              int wx, int wy, int wz,
                              net.zaminmc.torch.block.BlockType wall) {
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(fx, fy, fz), floor);
            server.world().setBlock(new BlockPosition(wx, wy, wz), wall);
        });
    }

    private static void await(EngineServer server, BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> ran = new AtomicReference<>();
            server.ticker().submit(() -> ran.set(condition.getAsBoolean()));
            long inner = System.currentTimeMillis() + 200;
            while (System.currentTimeMillis() < inner && ran.get() == null) {
                Thread.sleep(5);
            }
            if (Boolean.TRUE.equals(ran.get())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "dect" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }
}
