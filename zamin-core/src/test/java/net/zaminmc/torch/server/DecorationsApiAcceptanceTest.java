package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.Server;
import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.Direction;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The decoration census's public API surface (§27.3 — the README TODO's
 * row 15, the §27.10 capability test): the hanging bodies' and the armor
 * stands' READS a plugin needs — the frames with their wall cells,
 * facings, displayed stacks, rotations and comparator reads; the
 * paintings with their art off the reference's own registry; the stands
 * with their feet positions, snapped yaws, five equipment slots, status
 * flags and six-part poses — exercised over the LIVE engine through the
 * api types alone (Server.worlds() → World.decorations(), no cast to
 * the engine's systems), the families placed through the established
 * engine paths (the placement ladders + the interact arms the package
 * tests drive), and the census resolving to live state (a placed body
 * appears; a removed body disappears on the next read; an empty world
 * answers empty).
 */
class DecorationsApiAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "dca" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(() -> { });
    }

    /**
     * The whole census read family through the public surface: an empty
     * world answers empty; a frame hung and loaded through the engine's
     * own ladders reads with every datum (the wall cell, the facing, the
     * displayed stack, the rotation, the comparator read — the eject arm
     * updating the rows live); a painting reads its art; a stand reads
     * its family rows (the position, the snapped yaw, the worn helmet,
     * the fresh flags, the rolled pose beside the reference's own
     * defaults); and every removal disappears on the next read.
     */
    @Test
    void theCensusReadsThroughThePublicSurface() throws Exception {
        // The plugin-facing route: Server.worlds() → the overworld World
        // → decorations() — never an engine accessor for the read.
        Server api = server;
        World world = api.worlds().iterator().next();
        net.zaminmc.torch.decoration.Decorations census = world.decorations();
        assertNotNull(census, "the api surface is wired");
        // THE EMPTY WORLD: no body hung, no stand placed.
        assertTrue(census.frames().isEmpty(), "no frames before anything hangs");
        assertTrue(census.paintings().isEmpty(), "no paintings before anything hangs");
        assertTrue(census.armorStands().isEmpty(), "no stands before anything places");

        // THE SCENE: the frame's wall (10,40,10) with its cell to the
        // east; the painting's own wall (10,43,10) with its cell; the
        // stand's floor (20,39,10) — its own column, clear of the
        // frame scene.
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(10, 40, 10), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(10, 43, 10), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(20, 39, 10), BuiltinBlocks.STONE);
        });
        await(() -> server.world().getBlock(new BlockPosition(10, 40, 10))
                .identifier().value().equals("stone"), "the scene's walls placed");

        // THE FRAME through the engine's own ladders: hung on the wall
        // facing east, loaded with a diamond through the interact
        // insert, then turned one step through the rotate arm.
        AtomicReference<Boolean> hung = new AtomicReference<>();
        server.ticker().submit(() -> hung.set(
                server.decorations().placeFrame(new BlockPosition(10, 40, 10), 5, 3)));
        await(() -> hung.get() != null, "the frame attempt answered");
        assertTrue(hung.get(), "the frame hung");
        var engineFrame = server.decorations().frameAt(new BlockPosition(11, 40, 10));
        assertNotNull(engineFrame, "the frame is in its cell");
        AtomicReference<net.zaminmc.torch.server.decoration.DecorationSystem.FrameInteract> insert =
                new AtomicReference<>();
        server.ticker().submit(() -> insert.set(server.decorations().interactFrame(
                engineFrame, ItemStack.of(BuiltinItems.DIAMOND, 1))));
        await(() -> insert.get() != null, "the insert answered");
        assertEquals(net.zaminmc.torch.server.decoration.DecorationSystem.FrameInteract.INSERTED,
                insert.get(), "the diamond loaded the frame");
        AtomicReference<net.zaminmc.torch.server.decoration.DecorationSystem.FrameInteract> turn =
                new AtomicReference<>();
        server.ticker().submit(() -> turn.set(server.decorations().interactFrame(
                engineFrame, ItemStack.EMPTY)));
        await(() -> turn.get() != null, "the rotate answered");
        assertEquals(net.zaminmc.torch.server.decoration.DecorationSystem.FrameInteract.ROTATED,
                turn.get(), "the loaded frame turned one step");

        // THE PAINTING through the placement's own fit roll.
        AtomicReference<Boolean> painted = new AtomicReference<>();
        server.ticker().submit(() -> painted.set(
                server.decorations().placePainting(new BlockPosition(10, 43, 10), 5, 3)));
        await(() -> painted.get() != null, "the painting attempt answered");
        assertTrue(painted.get(), "the painting hung");

        // THE STAND through the placement ladder: the UP face on the
        // floor block, the same seeded pose roll the package test pins.
        AtomicReference<Boolean> stood = new AtomicReference<>();
        server.ticker().submit(() -> stood.set(server.armorStands().place(
                new BlockPosition(20, 39, 10), 0, 1, 10.0f, new java.util.Random(1))));
        await(() -> stood.get() != null, "the stand attempt answered");
        assertTrue(stood.get(), "the stand landed");
        var engineStand = server.armorStands().byId(
                server.armorStands().allStands().get(0).entityId());
        assertNotNull(engineStand, "the stand is in the registry");
        // The helmet through the swap ladder (the armor item's own slot).
        AtomicReference<Boolean> helmed = new AtomicReference<>();
        server.ticker().submit(() -> helmed.set(server.armorStands().interactAt(
                engineStand, ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1), 1.0, false,
                stack -> { })));
        await(() -> helmed.get() != null, "the helmet swap answered");
        assertTrue(helmed.get(), "the helmet landed on the head slot");

        // THE CENSUS ROWS — every datum the engine tracks, read through
        // the api alone.
        var frames = census.frames();
        assertEquals(1, frames.size(), "one frame in the census");
        var frame = frames.get(0);
        assertTrue(frame.entityId() > 0, "the frame's engine entity id");
        assertEquals(new BlockPosition(11, 40, 10), frame.pos(),
                "the wall cell the frame hangs in");
        assertEquals(Direction.EAST, frame.facing(),
                "the facing points away from the wall (byIdHorizontal 3 = east)");
        assertEquals("diamond", frame.item().type().identifier().value(),
                "the displayed stack");
        assertEquals(1, frame.item().count(), "the size-1 copy rule");
        assertEquals(1, frame.rotation(), "the one-step turn");
        assertEquals(2, frame.analogSignal(), "the comparator read: rotation % 8 + 1");

        var paintings = census.paintings();
        assertEquals(1, paintings.size(), "one painting in the census");
        var painting = paintings.get(0);
        assertTrue(painting.entityId() > 0, "the painting's engine entity id");
        assertTrue(painting.entityId() != frame.entityId(), "the ids stay distinct");
        assertEquals(new BlockPosition(11, 43, 10), painting.pos(), "the painting's cell");
        assertEquals(Direction.EAST, painting.facing(), "the painting's facing");
        assertNotNull(painting.motive(), "the art entry (never null)");
        // Only the 1x1 arts fit the single wall block — the fit roll's
        // own set (the seven sixteen-by-sixteen entries).
        assertEquals(16, painting.motive().width(), "the single-block art's width");
        assertEquals(16, painting.motive().height(), "the single-block art's height");
        assertTrue(java.util.Set.of("Kebab", "Aztec", "Alban", "Aztec2", "Bomb",
                "Plant", "Wasteland").contains(painting.motive().name()),
                "the rolled art is one of the 1x1 entries: " + painting.motive().name());

        var stands = census.armorStands();
        assertEquals(1, stands.size(), "one stand in the census");
        var stand = stands.get(0);
        assertTrue(stand.entityId() > 0, "the stand's engine entity id");
        assertTrue(stand.entityId() != frame.entityId(), "the id bands stay distinct");
        assertEquals(20.5, stand.x(), 1e-9, "the feet x (the cell's center)");
        assertEquals(40.0, stand.y(), 1e-9, "the feet y (the floor's top)");
        assertEquals(10.5, stand.z(), 1e-9, "the feet z");
        assertEquals(-180.0f, stand.yaw(), 1e-6, "the 45-degree snap");
        assertEquals(5, stand.equipment().size(), "the five slots in the wire's order");
        assertEquals("diamond_helmet", stand.equipment().get(4).type().identifier().value(),
                "the helmet wears the head slot");
        for (int slot = 0; slot <= 3; slot++) {
            assertTrue(stand.equipment().get(slot).isEmpty(),
                    "the bare slot " + slot + " reads empty");
        }
        // The fresh stand's flags (the status byte's five bits — nothing
        // flips them through the live paths yet).
        assertFalse(stand.small(), "the small flag starts clear");
        assertFalse(stand.noGravity(), "the no-gravity flag starts clear");
        assertFalse(stand.showArms(), "the show-arms flag starts clear");
        assertFalse(stand.noBasePlate(), "the no-base-plate flag starts clear");
        assertFalse(stand.marker(), "the marker flag starts clear");
        // The pose: the roll touches the head + the body (the seeded
        // bands the package test pins); the arms and legs keep the
        // reference's own defaults.
        assertTrue(stand.head().pitch() >= 0.0f && stand.head().pitch() < 5.0f,
                "the head pose rolled inside the reference's band");
        assertTrue(stand.body().yaw() > -5.0f && stand.body().yaw() < 5.0f,
                "the body pose rolled inside the reference's band");
        assertEquals(-10.0f, stand.leftArm().pitch(), 1e-6, "the left arm's default pitch");
        assertEquals(-10.0f, stand.leftArm().roll(), 1e-6, "the left arm's default roll");
        assertEquals(-15.0f, stand.rightArm().pitch(), 1e-6, "the right arm's default pitch");
        assertEquals(10.0f, stand.rightArm().roll(), 1e-6, "the right arm's default roll");
        assertEquals(-1.0f, stand.leftLeg().pitch(), 1e-6, "the left leg's default pitch");
        assertEquals(1.0f, stand.rightLeg().pitch(), 1e-6, "the right leg's default pitch");

        // LIVE RESOLUTION — the eject arm first: the diamond leaves, the
        // frame stays, the comparator read drops to 0.
        AtomicReference<Boolean> ejected = new AtomicReference<>();
        server.ticker().submit(() -> ejected.set(
                server.decorations().attackFrame(engineFrame, false, false)));
        await(() -> ejected.get() != null, "the eject answered");
        assertTrue(ejected.get(), "the loaded frame ejected");
        var afterEject = census.frames().get(0);
        assertTrue(afterEject.item().isEmpty(), "the displayed stack reads empty");
        assertEquals(0, afterEject.analogSignal(), "the bare frame's comparator read");
        assertEquals(1, afterEject.rotation(), "the rotation keeps its step");

        // THE REMOVALS — every body disappears on the next read.
        server.ticker().submit(() -> server.decorations().attackFrame(engineFrame, false, false));
        await(() -> census.frames().isEmpty(), "the broken frame left the census");
        var enginePainting = server.decorations().paintingAt(new BlockPosition(11, 43, 10));
        assertNotNull(enginePainting, "the painting still hangs");
        server.ticker().submit(() -> server.decorations().attackPainting(enginePainting, false));
        await(() -> census.paintings().isEmpty(), "the fallen painting left the census");
        server.ticker().submit(() -> server.armorStands().attack(engineStand, true));
        await(() -> census.armorStands().isEmpty(), "the removed stand left the census");
    }

    /**
     * The §27.6 any-thread contract: the census snapshots stay tear-free
     * under concurrent tick-lane mutation — a foreign thread hammers the
     * three enumerations while the tick lane repeatedly breaks and
     * re-hangs a wall of frames (and the stands' walk ticks against it),
     * every answer a complete snapshot.
     */
    @Test
    void theCensusSnapshotReadsTearFreeFromAForeignThread() throws Exception {
        Server api = server;
        World world = api.worlds().iterator().next();
        net.zaminmc.torch.decoration.Decorations census = world.decorations();

        // THE WALL: eight stones along z at x=10, y=40 — one column's
        // own wall block per frame, each body hung on its east face
        // (the wall runs along z so no frame's cell is another wall's
        // spot).
        server.ticker().submit(() -> {
            for (int z = 10; z <= 17; z++) {
                server.world().setBlock(new BlockPosition(10, 40, z), BuiltinBlocks.STONE);
            }
        });
        await(() -> server.world().getBlock(new BlockPosition(10, 40, 17))
                .identifier().value().equals("stone"), "the wall placed");
        server.ticker().submit(() -> {
            for (int z = 10; z <= 17; z++) {
                server.decorations().placeFrame(new BlockPosition(10, 40, z), 5, 3);
            }
        });
        await(() -> census.frames().size() == 8, "the eight frames hung");

        // THE HAMMER: 400 census reads from a foreign thread against the
        // live tick lane.
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        Thread hammer = new Thread(() -> {
            try {
                for (int i = 0; i < 400; i++) {
                    int frames = census.frames().size();
                    int paintings = census.paintings().size();
                    int stands = census.armorStands().size();
                    if (frames < 0 || frames > 8 || paintings < 0 || stands < 0) {
                        throw new IllegalStateException("a torn census: frames=" + frames
                                + " paintings=" + paintings + " stands=" + stands);
                    }
                    for (var frame : census.frames()) {
                        if (frame.entityId() <= 0 || frame.pos() == null) {
                            throw new IllegalStateException("a torn row: " + frame);
                        }
                    }
                }
            } catch (RuntimeException e) {
                failure.set(e);
            } finally {
                done.countDown();
            }
        }, "decorations-census-hammer");
        hammer.start();

        // THE CHURN: the tick lane breaks the whole wall of frames and
        // re-hangs it, cycle after cycle, against the hammer's reads.
        for (int cycle = 0; cycle < 30; cycle++) {
            AtomicReference<Boolean> ran = new AtomicReference<>();
            server.ticker().submit(() -> {
                for (int z = 10; z <= 17; z++) {
                    var frame = server.decorations().frameAt(new BlockPosition(11, 40, z));
                    if (frame != null) {
                        server.decorations().removeFrame(frame);
                    }
                }
                for (int z = 10; z <= 17; z++) {
                    server.decorations().placeFrame(new BlockPosition(10, 40, z), 5, 3);
                }
                ran.set(true);
            });
            await(() -> Boolean.TRUE.equals(ran.get()), "the churn cycle " + cycle + " ran");
        }
        hammer.join(30_000);
        assertTrue(done.getCount() == 0, "the hammer finished");
        RuntimeException caught = failure.get();
        if (caught != null) {
            throw caught;
        }
        // The wall settles back to its eight frames.
        await(() -> census.frames().size() == 8, "the census settles at eight frames");
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
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
