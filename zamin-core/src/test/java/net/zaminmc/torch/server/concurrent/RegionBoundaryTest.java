package net.zaminmc.torch.server.concurrent;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.piston.PistonBlocks;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 4 spatial-activation boundary tests (the master spec's §18
 * "cross-boundary mechanics" list over the regionized world — the first
 * live slice: the piston, the redstone wire, and the explosion): the
 * position-aware ownership assertion (a foreign context fails against the
 * position's own region, never a silent pass), the boundary scope (the
 * design §10.3's larger exclusive ownership — the seam-crossing operation
 * runs holding every region it touches, same-tick and vanilla-exact, the
 * one acquisition order preventing AB-BA), and the §7 intent protocol's
 * per-region drain (a region-targeted intent applies at the tick head in
 * the target's own boundary).
 *
 * <p>The seam fixture is {@code region-size-chunks=1}: every chunk its own
 * ownership domain, so a line of blocks at x=15/16 straddles two domains —
 * the cruelest boundary the §11 list asks for.</p>
 */
class RegionBoundaryTest {

    /** The reference direction id for EAST (the seam pushes along +X). */
    private static final int EAST = 5;

    private static FrozenBlockRegistry registry;

    @BeforeAll
    static void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The scope mechanics (the unit surface — the ambient domain exists
    // but is never entered, so ONLY the region credentials count)
    // ------------------------------------------------------------------

    @Test
    void theScopeCarriesTheRegionCredentialWithoutTheAmbientDomain() throws Exception {
        EngineWorld world = new EngineWorld("scoped", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        RegionPartition partition = new RegionPartition("scoped", 1);
        world.attachRegionPartition(partition);
        // An ambient domain exists but is NEVER entered: no single-lane
        // credential anywhere — only the region domains count.
        OwnershipDomain ambient = OwnershipDomain.create("simulation:scoped");
        world.attachDomain(ambient);

        // The seam: x=15 sits in region (0,0), x=16 in region (1,0).
        OwnershipDomain left = world.regionDomainFor(new BlockPosition(8, 4, 8));
        OwnershipDomain right = world.regionDomainFor(new BlockPosition(16, 4, 8));
        assertNotSame(left, right, "the seam splits two regions");

        // A writer holding nothing fails against the position's own region
        // (the diagnosable failure, never a silent pass).
        Throwable bare = runOnWriterThread(() ->
                world.setBlock(new BlockPosition(16, 4, 8), BuiltinBlocks.STONE));
        OwnershipViolationException refused =
                assertInstanceOf(OwnershipViolationException.class, bare);
        assertEquals("setBlock", refused.operation());
        assertTrue(refused.domain().startsWith("region:"),
                "the violation names the region domain: " + refused.domain());

        // Holding ONLY the left region: the left write passes, the
        // right-side write still fails (the spatial authority).
        Throwable crossed = runOnWriterThread(() -> {
            left.enter();
            try {
                world.setBlock(new BlockPosition(8, 4, 8), BuiltinBlocks.STONE);
                world.setBlock(new BlockPosition(16, 4, 8), BuiltinBlocks.STONE);
            } finally {
                left.exit();
            }
        });
        assertInstanceOf(OwnershipViolationException.class, crossed,
                "the right-region write failed through the left credential");

        // The boundary scope: the seam-crossing writes land under the
        // regions the box spans — the region credentials carry them; the
        // ambient domain was never entered.
        Throwable scoped = runOnWriterThread(() ->
                world.executeInRegions(new BlockPosition(8, 4, 8), new BlockPosition(17, 4, 8),
                        () -> {
                            world.setBlock(new BlockPosition(15, 4, 8), BuiltinBlocks.STONE);
                            world.setBlock(new BlockPosition(16, 4, 8), BuiltinBlocks.STONE);
                        }));
        assertTrue(scoped == null, "the scoped writes both landed: " + scoped);
        assertEquals("stone", world.getBlock(new BlockPosition(15, 4, 8))
                .identifier().value());
        assertEquals("stone", world.getBlock(new BlockPosition(16, 4, 8))
                .identifier().value());
        assertEquals(2, partition.regionCount(), "exactly the two touched regions exist");
    }

    /** Runs the body on a thread holding nothing; @return its failure or null. */
    private static Throwable runOnWriterThread(Runnable body) throws InterruptedException {
        AtomicReference<Throwable> caught = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread writer = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                caught.set(t);
            } finally {
                done.countDown();
            }
        }, "writer");
        writer.start();
        assertTrue(done.await(5, TimeUnit.SECONDS), "the writer ran");
        return caught.get();
    }

    @Test
    void aHeldRegionRefusesTheScopeAndKeepsItsExclusivity() throws Exception {
        EngineWorld world = new EngineWorld("exclusive", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        RegionPartition partition = new RegionPartition("exclusive", 1);
        world.attachRegionPartition(partition);
        OwnershipDomain right = world.regionDomainFor(new BlockPosition(16, 4, 8));

        // A foreign writer holds the right region for a while.
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean released = new AtomicBoolean(false);
        Thread holder = new Thread(() -> {
            right.enter();
            entered.countDown();
            while (!released.get()) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            right.exit();
        }, "region-holder");
        holder.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS), "the holder entered");

            // The scope spanning the seam cannot take the held region: the
            // enter fails, the body never runs, and the already-acquired
            // left region releases (the finally's reverse walk).
            AtomicBoolean ran = new AtomicBoolean(false);
            assertThrows(OwnershipViolationException.class,
                    () -> world.executeInRegions(new BlockPosition(8, 4, 8),
                            new BlockPosition(17, 4, 8), () -> ran.set(true)));
            assertFalse(ran.get(), "the scope body never ran");
        } finally {
            // After the release the same scope runs and the seam write lands.
            released.set(true);
            holder.join(5_000);
        }
        world.executeInRegions(new BlockPosition(8, 4, 8), new BlockPosition(17, 4, 8),
                () -> world.setBlock(new BlockPosition(16, 4, 8), BuiltinBlocks.STONE));
        assertEquals("stone", world.getBlock(new BlockPosition(16, 4, 8))
                .identifier().value());
    }

    @Test
    void theDrainSkipsHeldRegionsAndAppliesThemAfterRelease() throws Exception {
        EngineWorld world = new EngineWorld("drained", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        RegionPartition partition = new RegionPartition("drained", 1);
        world.attachRegionPartition(partition);
        OwnershipDomain left = world.regionDomainFor(new BlockPosition(8, 4, 8));
        OwnershipDomain right = world.regionDomainFor(new BlockPosition(16, 4, 8));
        CrossOwnerRouter router = new CrossOwnerRouter();
        AtomicInteger applied = new AtomicInteger();

        // An intent targeting the left region (drainable now) and one
        // targeting the right region (held by a foreign writer).
        router.submit(new CrossOwnerRouter.Intent(left, UUID.randomUUID(),
                "foreign", 0, "count-left", applied::incrementAndGet));
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean released = new AtomicBoolean(false);
        Thread holder = new Thread(() -> {
            right.enter();
            entered.countDown();
            while (!released.get()) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            right.exit();
        }, "region-holder");
        holder.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS), "the holder entered");
            router.submit(new CrossOwnerRouter.Intent(right, UUID.randomUUID(),
                    "foreign", 0, "count-right", applied::incrementAndGet));

            // The drain: the left intent applies, the held right region is
            // skipped — its intent stays queued (ordered, never dropped),
            // and the walk itself never throws.
            assertEquals(1, world.drainRegionIntents(router));
            assertEquals(1, applied.get());
            assertEquals(1, router.pendingFor(right), "the held region's intent stayed queued");
        } finally {
            released.set(true);
            holder.join(5_000);
        }

        // After the release: the next drain applies the waiting intent.
        assertEquals(1, world.drainRegionIntents(router));
        assertEquals(2, applied.get());
        assertEquals(0, router.pendingFor(right));
    }

    // ------------------------------------------------------------------
    // The live seam (region-size-chunks=1 — every chunk its own region)
    // ------------------------------------------------------------------

    @Test
    void theForeignWriteFailsAgainstThePositionRegionOnTheLiveEngine() throws Exception {
        EngineServer server = boot();
        try {
            // The partition took the config: x=15 and x=16 split regions.
            assertTrue(server.worldRegionPartition().regionId(0, 0)
                    != server.worldRegionPartition().regionId(1, 0),
                    "the one-chunk regions split at x=16");

            // A foreign context (this test thread) writing straight into
            // the world fails against the position's own region domain.
            AtomicReference<Throwable> caught = new AtomicReference<>();
            try {
                server.world().setBlock(new BlockPosition(16, 40, 10), BuiltinBlocks.STONE);
            } catch (Throwable t) {
                caught.set(t);
            }
            OwnershipViolationException violation = assertInstanceOf(
                    OwnershipViolationException.class, caught.get());
            assertEquals("setBlock", violation.operation());
            assertTrue(violation.domain().startsWith("region:"),
                    "the violation names the region: " + violation.domain());

            // The refused write never landed.
            assertEquals("air", typeAt(server, 16, 40, 10).identifier().value());
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void thePistonPushesAndPullsTheColumnAcrossTheRegionSeam() throws Exception {
        EngineServer server = boot();
        try {
            // The piston's base at x=15 (region 0,0); everything it pushes
            // lands in x=16+ (region 1,0) — the seam sits between the base
            // and the head's cell.
            int x = 15, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, true));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)),
                    "the sticky piston placed");
            // Power on: the extend event's boundary scope holds both
            // regions, the head lands across the seam, the pushed stone one
            // cell further — same-tick and vanilla-exact.
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston extended across the seam");
            await(() -> PistonBlocks.isPistonHead(typeAt(server, x + 1, y, z)),
                    "the head landed in the neighbor region");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pushed stone landed in the neighbor region");

            // Power off: the sticky pull drags the stone back across the
            // seam to the head's cell.
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> !PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston retracted");
            await(() -> typeAt(server, x + 1, y, z).identifier().value().equals("stone"),
                    "the sticky arm pulled the stone back across the seam");
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("air"),
                    "the stone left its pushed cell");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theRedstoneDecayWalksAcrossTheRegionSeam() throws Exception {
        EngineServer server = boot();
        try {
            // The torch-fed block at x=8 (region 0,0), the dust line east
            // through x=16+ (region 1,0): the same-tick cascade crosses the
            // seam and the decay ladder continues exactly — the wire in the
            // neighbor region neither restarts at 15 nor drops to 0.
            int y = 40, z = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(8, y - 2, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(8, y - 1, z),
                        RedstoneBlocks.torchStandingLit());
                server.world().setBlock(new BlockPosition(8, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(8, y + 1, z),
                        RedstoneBlocks.wireOfPower(0));
                for (int i = 1; i <= 18; i++) {
                    server.world().setBlock(new BlockPosition(8 + i, y, z), BuiltinBlocks.STONE);
                    server.world().setBlock(new BlockPosition(8 + i, y + 1, z),
                            RedstoneBlocks.wireOfPower(0));
                }
            });
            await(() -> wirePower(server, 8, y + 1, z) == 15,
                    "the first cell reads 15");
            // The seam cells: x=15 is the last cell of region (0,0),
            // x=16 the first of region (1,0).
            assertEquals(15 - 7, wirePower(server, 15, y + 1, z),
                    "the last cell before the seam");
            assertEquals(15 - 8, wirePower(server, 16, y + 1, z),
                    "the first cell past the seam continues the decay");
            assertEquals(15 - 15, wirePower(server, 23, y + 1, z));
            assertEquals(0, wirePower(server, 24, y + 1, z));
            assertEquals(0, wirePower(server, 26, y + 1, z));
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theExplosionConsumesBothSidesOfTheRegionSeam() throws Exception {
        EngineServer server = boot();
        try {
            // The TNT at x=15 (region 0,0) with stone neighbors on both
            // sides: x=14 stays in the TNT's region, x+1 (the lever's
            // mount carrier, adjacent to the TNT) sits in the neighbor
            // region. The blast's boundary scope holds both regions and
            // the destruction lands on both sides the same tick — the
            // §11 "explosions spanning multiple owners" row.
            int x = 15, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.TNT);
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x - 1, y, z), BuiltinBlocks.STONE);
                // The lever arm (the reference's own prime): its stone
                // first (the adjacent carrier), the lever on the side
                // facing the TNT.
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 2, y, z),
                        RedstoneBlocks.leverOf(1, false));
            });
            await(() -> typeAt(server, x, y, z).identifier().value().equals("tnt"),
                    "the TNT placed");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y, z)));
            await(() -> typeAt(server, x, y, z).identifier().value().equals("air"),
                    "the primed TNT left its cell");
            // The fuse ends: both sides of the seam consume.
            await(() -> typeAt(server, x - 1, y, z).identifier().value().equals("air"),
                    "the blast consumed the near-side stone");
            await(() -> typeAt(server, x + 1, y, z).identifier().value().equals("air"),
                    "the blast consumed the far-side stone across the seam");
            await(() -> typeAt(server, x, y - 1, z).identifier().value().equals("air"),
                    "the blast consumed the floor below");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theCrossOwnerIntentAppliesAtTheTargetRegionsBoundary() throws Exception {
        EngineServer server = boot();
        try {
            // A region-targeted intent (the §7 protocol's target-side
            // apply): the foreign submitter never touches the world — the
            // intent queues toward region (1,0), the tick-head boundary
            // walk enters that region's domain and applies the write there.
            BlockPosition target = new BlockPosition(16, 40, 10);
            OwnershipDomain region = server.world().regionDomainFor(target);
            UUID operationId = UUID.randomUUID();
            boolean admitted = server.crossOwnerRouter().submit(
                    new CrossOwnerRouter.Intent(region, operationId, "test:foreign", 0,
                            "setBlock", () -> server.world().setBlock(
                                    target, BuiltinBlocks.STONE)));
            assertTrue(admitted, "the intent admitted");

            // The tick-head drain applies it in the target's own context.
            await(() -> typeAt(server, 16, 40, 10).identifier().value().equals("stone"),
                    "the intent's write landed at the target region");
            assertTrue(server.crossOwnerRouter().appliedCount(region) >= 1,
                    "the target region's drain applied the intent");
            assertEquals(0, server.crossOwnerRouter().pendingFor(region),
                    "nothing stays queued");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    /** Reads the block type through the tick thread (the submit queue's happens-before edge). */
    private static BlockType typeAt(EngineServer server, int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get();
    }

    private static int wirePower(EngineServer server, int x, int y, int z) {
        BlockType type = typeAt(server, x, y, z);
        return RedstoneBlocks.isWire(type) ? RedstoneBlocks.wirePower(type) : -1;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The one-chunk-region engine: every chunk its own ownership domain. */
    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "rbt" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, 1);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
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
