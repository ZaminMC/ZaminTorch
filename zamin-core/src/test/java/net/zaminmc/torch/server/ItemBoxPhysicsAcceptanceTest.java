package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.hopper.HopperBlockEntity;
import net.zaminmc.torch.server.hopper.HopperBlocks;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The item box-footprint physics (the vanilla-shape slice): the item's
 * historical 0.25-wide EntityItem box lands as a BOX, not a center
 * point — the bottom face's four corners + center against the collision
 * shapes. The proof case is the hopper's own funnel (the gap the §27.7
 * suck gate's test found): a dropped item scatters as it falls, its
 * center drifts past the cell edge, and the pre-fix POINT test missed
 * the funnel entirely (the item fell through to the ground below);
 * vanilla's box catches the funnel's rim and rests the item IN the
 * collection band — the suck's own home.
 */
class ItemBoxPhysicsAcceptanceTest {

    /** The funnel's top surface (the hopper's 5/8 base) in cell-local terms. */
    private static final double FUNNEL_TOP = 0.625;

    @TempDir
    Path dataDir;

    @Test
    void theDroppedItemRestsInTheHoppersFunnel() throws Exception {
        EngineServer server = boot();
        try {
            // A POWERED hopper: the lever ON above it holds the disabled
            // pair (vanilla's own updateEnabled — an unpowered "disabled"
            // hopper re-enables on the next neighbor change), so no suck
            // disturbs the rest. The enabled twin's collection is the
            // sibling test's own proof.
            BlockPosition hopperAt = new BlockPosition(0, 64, 0);
            BlockPosition leverAt = new BlockPosition(0, 65, 0);
            server.ticker().submit(() -> {
                server.world().setBlock(hopperAt, HopperBlocks.hopperOf(0 /* DOWN */, true));
                server.world().setBlock(leverAt,
                        net.zaminmc.torch.server.redstone.RedstoneBlocks.leverOf(5, false));
            });
            await(() -> server.world().getBlock(hopperAt).identifier().value().startsWith("hopper")
                            && server.world().getBlock(leverAt).identifier().value().startsWith("lever"),
                    "the hopper and its lever placed");
            server.ticker().submit(() -> server.redstone().useLever(leverAt));
            await(() -> server.world().getBlock(hopperAt).identifier().value().endsWith("_disabled"),
                    "the lever's signal disabled the hopper");

            // Drop the item EXACTLY over the funnel's center, zero
            // scatter (the exact-position spawn): a deterministic fall —
            // the historical drop scatter is the pickup path's own test.
            server.ticker().submit(() -> server.itemEntities().spawnThrown(
                    new Position(hopperAt.x() + 0.5, hopperAt.y() + 1.3, hopperAt.z() + 0.5),
                    net.zaminmc.torch.item.ItemStack.of(BuiltinItems.EMERALD, 1)));

            // The rest: the item settles on the funnel (the box footprint
            // touching the 5/8 base or a leg), inside the collection band
            // (|y - (hopper.y + 1)| <= 0.5), and STAYS there.
            AtomicReference<Double> restY = new AtomicReference<>();
            await(() -> {
                for (var item : server.itemEntities().all()) {
                    if (item.stack().type().identifier().equals(BuiltinItems.EMERALD.identifier())) {
                        double y = item.position().y();
                        if (item.onGround() && y < hopperAt.y() + 1.5) {
                            restY.set(y);
                            return true;
                        }
                    }
                }
                return false;
            }, "the item came to rest on the funnel");

            double y = restY.get();
            assertTrue(y >= hopperAt.y() + FUNNEL_TOP
                            && y <= hopperAt.y() + 1.125,
                    "the rest sits on the funnel's surface: " + y);
            assertTrue(Math.abs(y - (hopperAt.y() + 1.0)) <= 0.5,
                    "the rest is inside the collection band (the suck's home)");

            // And it STAYS: a second later the same item is still there
            // (the pre-fix point test fell through the funnel to the
            // ground; the box holds the rim).
            EngineAwaits.awaitTicks(server.world(), 20, "engine progress (a 1000 wall settle)");
            boolean stillThere = false;
            for (var item : server.itemEntities().all()) {
                if (item.stack().type().identifier().equals(BuiltinItems.EMERALD.identifier())
                        && Math.abs(item.position().y() - y) < 0.01) {
                    stillThere = true;
                }
            }
            assertTrue(stillThere, "the resting item stayed on the funnel");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theRestingItemIsSuckedIntoTheHopper() throws Exception {
        EngineServer server = boot();
        try {
            BlockPosition hopperAt = new BlockPosition(0, 64, 0);
            server.ticker().submit(() -> server.world().setBlock(hopperAt,
                    HopperBlocks.hopperOf(0 /* DOWN */, true)));
            await(() -> server.hopper().peek(hopperAt) != null, "the hopper placed");

            server.ticker().submit(() -> server.itemEntities().spawnThrown(
                    new Position(hopperAt.x() + 0.5, hopperAt.y() + 1.3, hopperAt.z() + 0.5),
                    net.zaminmc.torch.item.ItemStack.of(BuiltinItems.DIAMOND, 2)));

            // The uncancelled suck lands while the item rests in the band:
            // the hopper collects the whole stack.
            await(() -> {
                HopperBlockEntity hopper = server.hopper().peek(hopperAt);
                if (hopper == null) {
                    return false;
                }
                int total = 0;
                for (int slot = 0; slot < HopperBlockEntity.SLOT_COUNT; slot++) {
                    total += hopper.getItem(slot).count();
                }
                return total >= 2;
            }, "the resting stack was sucked into the hopper");

            int total = 0;
            HopperBlockEntity hopper = server.hopper().peek(hopperAt);
            for (int slot = 0; slot < HopperBlockEntity.SLOT_COUNT; slot++) {
                total += hopper.getItem(slot).count();
            }
            assertEquals(2, total, "the whole stack landed in the hopper");
        } finally {
            server.shutdown(() -> { });
        }
    }


    @Test
    void theThrownItemStopsAgainstTheWall() throws Exception {
        EngineServer server = boot();
        try {
            // A three-tall stone wall at x=5, the item thrown east at it
            // from two and a half blocks out.
            server.ticker().submit(() -> {
                // The wall spans z 2..10: a diagonal throw (vx 0.4, vz
                // 0.3) cannot out-run the wall's south end — the box
                // reaches the face while its z-band still overlaps, the
                // X axis clips, the slide runs ALONG the face. (The
                // historical 1-cell pillar was never touched by either
                // model's trajectory: the throw clears its south corner
                // first, and the old pass rode the leading-face
                // stand-in's freeze-on-open-ground artifact — the item
                // stopped mid-slide at (4.737, 4.178) on empty ground,
                // which the x assertion happened to accept.)
                for (int y = 5; y <= 7; y++) {
                    for (int z = 2; z <= 10; z++) {
                        server.world().setBlock(new BlockPosition(5, y, z),
                                net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
                    }
                }
            });
            await(() -> server.world().getBlock(new BlockPosition(5, 5, 2))
                            .equals(net.zaminmc.torch.server.block.BuiltinBlocks.STONE),
                    "the wall placed");

            AtomicReference<net.zaminmc.torch.server.entity.ItemEntity> thrown =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                var item = server.itemEntities().spawnThrown(
                        new Position(2.5, 6.0, 2.5),
                        net.zaminmc.torch.item.ItemStack.of(BuiltinItems.EMERALD, 1));
                if (item != null) {
                    item.setVelocity(0.4, 0.0, 0.0);
                    thrown.set(item);
                }
            });
            await(() -> thrown.get() != null, "the item thrown");

            // The wall's face at x=5: the box's leading edge stops there —
            // the CENTER never passes 5 - 0.125 (the pre-clip engine let
            // the drift pass straight through the wall).
            AtomicReference<Double> finalX = new AtomicReference<>();
            await(() -> {
                var item = thrown.get();
                if (item.onGround() && item.position().x() <= 5.0 - 0.125 + 0.001) {
                    finalX.set(item.position().x());
                    return true;
                }
                return false;
            }, "the item came to rest against the wall's face");
            assertTrue(finalX.get() >= 4.5,
                    "the slide reached the wall (x=" + finalX.get() + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theBlockedAxisSlidesAlongTheWall() throws Exception {
        EngineServer server = boot();
        try {
            server.ticker().submit(() -> {
                // The wall spans z 2..10: a diagonal throw (vx 0.4, vz
                // 0.3) cannot out-run the wall's south end — the box
                // reaches the face while its z-band still overlaps, the
                // X axis clips, the slide runs ALONG the face. (The
                // historical 1-cell pillar was never touched by either
                // model's trajectory: the throw clears its south corner
                // first, and the old pass rode the leading-face
                // stand-in's freeze-on-open-ground artifact — the item
                // stopped mid-slide at (4.737, 4.178) on empty ground,
                // which the x assertion happened to accept.)
                for (int y = 5; y <= 7; y++) {
                    for (int z = 2; z <= 10; z++) {
                        server.world().setBlock(new BlockPosition(5, y, z),
                                net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
                    }
                }
            });
            await(() -> server.world().getBlock(new BlockPosition(5, 5, 2))
                            .equals(net.zaminmc.torch.server.block.BuiltinBlocks.STONE),
                    "the wall placed");

            // The diagonal throw: east into the wall, south free — the X
            // axis clips, the Z axis keeps sliding.
            AtomicReference<net.zaminmc.torch.server.entity.ItemEntity> thrown =
                    new AtomicReference<>();
            server.ticker().submit(() -> {
                var item = server.itemEntities().spawnThrown(
                        new Position(2.5, 6.0, 2.5),
                        net.zaminmc.torch.item.ItemStack.of(BuiltinItems.DIAMOND, 1));
                if (item != null) {
                    item.setVelocity(0.4, 0.0, 0.3);
                    thrown.set(item);
                }
            });
            await(() -> thrown.get() != null, "the item thrown");

            await(() -> {
                var item = thrown.get();
                return item.onGround();
            }, "the item came to rest");

            var item = thrown.get();
            assertTrue(item.position().x() <= 5.0 - 0.125 + 0.001,
                    "the X axis clipped at the wall: " + item.position().x());
            assertTrue(item.position().x() >= 4.5,
                    "the slide reached the wall: " + item.position().x());
            assertTrue(item.position().z() > 3.0,
                    "the Z axis slid along the wall: " + item.position().z());
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "ibp" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private static void await(BooleanSupplier condition, String description)
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
