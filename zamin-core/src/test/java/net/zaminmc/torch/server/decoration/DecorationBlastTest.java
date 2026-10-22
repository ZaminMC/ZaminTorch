package net.zaminmc.torch.server.decoration;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.world.ExplosionEvent;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The explosion × decoration interplay (the reference's damageEntities walk
 * over the entity family — the README TODO's row 6): the frames and the
 * paintings in the blast box take the damage —
 * DecorationEntity.takeDamage lines 137-146, any damage beyond the
 * invulnerable gate removes the body — the INSTANT break off the wall (the
 * survival poll's 100-tick grace never applies), the frame item + the
 * loaded item dropping through the null-attacker arm. A decoration outside
 * the box never hears the blast.
 */
class DecorationBlastTest {

    @TempDir
    Path dataDir;

    @Test
    void theBlastBreaksTheDecorationsInTheBoxInstantly() throws Exception {
        EngineServer server = boot();
        try {
            // The pillar: the near wall (10,40,10) with the frame's cell
            // (11,40,10) to its east; the painting's own wall (10,43,10)
            // with its cell (11,43,10); the far wall (40,40,40) with a
            // frame — outside every blast band.
            // The pillar + the floors under the hanging cells (the drops
            // must land beside the bodies, not fall the flat world's 36
            // blocks to the ground).
            place(server, 10, 39, 10, BuiltinBlocks.STONE, 10, 40, 10, BuiltinBlocks.STONE);
            place(server, 11, 39, 10, BuiltinBlocks.STONE, 11, 39, 10, BuiltinBlocks.STONE);
            place(server, 10, 42, 10, BuiltinBlocks.STONE, 10, 43, 10, BuiltinBlocks.STONE);
            place(server, 11, 42, 10, BuiltinBlocks.STONE, 11, 42, 10, BuiltinBlocks.STONE);
            place(server, 40, 39, 40, BuiltinBlocks.STONE, 40, 40, 40, BuiltinBlocks.STONE);
            BlockPosition nearWall = new BlockPosition(10, 40, 10);
            BlockPosition frameCell = new BlockPosition(11, 40, 10);
            BlockPosition paintingWall = new BlockPosition(10, 43, 10);
            BlockPosition paintingCell = new BlockPosition(11, 43, 10);
            BlockPosition farWall = new BlockPosition(40, 40, 40);
            BlockPosition farCell = new BlockPosition(41, 40, 40);
            await(server, () -> server.world().getBlock(nearWall)
                    .identifier().value().equals("stone"), "the near wall placed");
            await(server, () -> server.world().getBlock(farWall)
                    .identifier().value().equals("stone"), "the far wall placed");

            // The frame hangs on the near wall facing east, loaded with a
            // diamond through the interact ladder's insert.
            AtomicReference<Boolean> framePlaced = new AtomicReference<>();
            server.ticker().submit(() -> framePlaced.set(
                    server.decorations().placeFrame(nearWall, 5, 3)));
            await(server, () -> framePlaced.get() != null, "the frame attempt answered");
            assertTrue(framePlaced.get(), "the frame hung");
            ItemFrame frame = server.decorations().frameAt(frameCell);
            assertNotNull(frame, "the frame is in the cell");
            AtomicReference<DecorationSystem.FrameInteract> insert =
                    new AtomicReference<>();
            server.ticker().submit(() -> insert.set(server.decorations().interactFrame(
                    frame, ItemStack.of(BuiltinItems.DIAMOND, 1))));
            await(server, () -> insert.get() != null, "the insert answered");
            assertEquals(DecorationSystem.FrameInteract.INSERTED, insert.get(),
                    "the diamond loaded the frame");
            assertFalse(frame.display().isEmpty(), "the frame carries the display");

            // The painting on its own wall.
            AtomicReference<Boolean> paintingPlaced = new AtomicReference<>();
            server.ticker().submit(() -> paintingPlaced.set(
                    server.decorations().placePainting(paintingWall, 5, 3)));
            await(server, () -> paintingPlaced.get() != null, "the painting attempt answered");
            assertTrue(paintingPlaced.get(), "the painting hung");
            Painting painting = server.decorations().paintingAt(paintingCell);
            assertNotNull(painting, "the painting is in the cell");

            // The far frame.
            AtomicReference<Boolean> farPlaced = new AtomicReference<>();
            server.ticker().submit(() -> farPlaced.set(
                    server.decorations().placeFrame(farWall, 5, 3)));
            await(server, () -> farPlaced.get() != null, "the far attempt answered");
            assertTrue(farPlaced.get(), "the far frame hung");
            int framesBefore = countFrames(server);
            assertEquals(2, framesBefore, "two frames + one painting");

            // The blast: a creeper with a 5-tick fuse beside the near
            // wall (inside the frame's band, inside the painting's).
            AtomicReference<ExplosionEvent> blast = new AtomicReference<>();
            var sub = ((net.zaminmc.torch.Server) server).events()
                    .on(ExplosionEvent.class, blast::set);
            var creeper = server.mobs().spawnAt(MobType.CREEPER,
                    new Position(12.5, 40, 10.5));
            assertNotNull(creeper, "the creeper spawned");
            server.ticker().submit(() -> {
                creeper.setCreeperIgnited(true); // no player needed (the flag forces the fuse)
                creeper.setCreeperFuseTime(5);
            });
            await(server, () -> blast.get() != null, "the blast detonated");
            sub.unsubscribe();

            // THE INSTANT SWEEP (the same tick as the blast — the poll's
            // 100-tick grace never applies): the near frame and the
            // painting are gone the moment the blast lands.
            await(server, () -> server.decorations().frameAt(frameCell) == null,
                    "the near frame swept instantly");
            await(server, () -> server.decorations().paintingAt(paintingCell) == null,
                    "the painting swept instantly");
            // The far frame never heard the blast.
            assertNotNull(server.decorations().frameAt(farCell),
                    "the far frame survives the out-of-band blast");
            assertEquals(1, countFrames(server), "only the far frame remains");

            // THE NULL-ATTACKER DROPS (the break arm's own walk): the frame
            // item + the loaded diamond at the frame's cell, the painting
            // item at the painting's cell.
            assertTrue(hasItemAt(server, frameCell, "item_frame"),
                    "the frame item dropped");
            assertTrue(hasItemAt(server, frameCell, "diamond"),
                    "the loaded display dropped");
            assertTrue(hasItemAt(server, paintingCell, "painting"),
                    "the painting item dropped");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /** @return whether an item entity of the kind settled within a couple
     * of blocks of the cell (the drops bounce off the blast scene before
     * coming to rest). */
    private static boolean hasItemAt(EngineServer server, BlockPosition cell, String kind) {
        AtomicReference<Boolean> found = new AtomicReference<>();
        server.ticker().submit(() -> {
            for (var item : server.itemEntities().all()) {
                String id = item.stack().type().identifier().value();
                Position p = item.position();
                if (id.equals(kind)
                        && Math.abs(p.x() - cell.x()) <= 2.0
                        && Math.abs(p.y() - cell.y()) <= 2.0
                        && Math.abs(p.z() - cell.z()) <= 2.0) {
                    found.set(true);
                    return;
                }
            }
            found.set(false);
        });
        long deadline = System.currentTimeMillis() + 5_000;
        try {
            while (System.currentTimeMillis() < deadline && found.get() == null) {
                Thread.sleep(5);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Boolean.TRUE.equals(found.get());
    }

    private static int countFrames(EngineServer server) throws InterruptedException {
        AtomicReference<Integer> count = new AtomicReference<>();
        server.ticker().submit(() -> count.set(server.decorations().allFrames().size()));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && count.get() == null) {
            Thread.sleep(5);
        }
        return count.get();
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
                "debt" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }
}
