package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import net.zaminmc.torch.server.concurrent.OwnershipViolationException;
import net.zaminmc.torch.server.world.DeltaWorldStorage;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nether dimension's engine wiring (the 8b-ii slice — the second
 * EngineWorld): the ticker walks both worlds' clocks in one tick, the
 * nether's mutations assert through its own ownership domain
 * ("simulation:nether"), and its delta persistence rides vanilla's DIM-1
 * directory shape — a fresh world applies the saved deltas back.
 */
class NetherDimensionTest {

    private static FrozenBlockRegistry registry;

    @BeforeAll
    static void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    private static EngineWorld world(String name) {
        return new EngineWorld(name, registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
    }

    @Test
    void tickerWalksBothWorldsClocks() {
        EngineTicker ticker = new EngineTicker(20);
        EngineWorld overworld = world("walked");
        EngineWorld nether = world("walked_nether");
        ticker.attachWorld(overworld);
        ticker.attachNetherWorld(nether);
        assertEquals(nether, ticker.netherWorld(), "the nether attach is readable");

        ticker.tickOnce();
        assertEquals(1, overworld.totalTicks(), "the overworld clock advanced");
        assertEquals(1, nether.totalTicks(), "the nether clock advanced with the tick");
        assertEquals(overworld.timeOfDay(), nether.timeOfDay(),
                "both clocks ride the same day length from midnight");
        ticker.tickOnce();
        assertEquals(2, overworld.totalTicks());
        assertEquals(2, nether.totalTicks());
    }

    @Test
    void singleDimensionTickerKeepsWorkingWithoutTheNether() {
        // The historical contract: a ticker without a nether world never
        // touches one (null keeps the single-dimension behavior).
        EngineTicker ticker = new EngineTicker(20);
        EngineWorld overworld = world("solo");
        ticker.attachWorld(overworld);
        ticker.tickOnce();
        assertEquals(1, overworld.totalTicks());
        assertEquals(null, ticker.netherWorld());
    }

    @Test
    void doubleNetherAttachRefuses() {
        EngineTicker ticker = new EngineTicker(20);
        ticker.attachWorld(world("once"));
        ticker.attachNetherWorld(world("nether"));
        assertThrows(IllegalStateException.class, () -> ticker.attachNetherWorld(world("again")),
                "the second attach is a wiring bug, not a re-attach");
    }

    @Test
    void netherMutationsAssertThroughTheNetherDomain() {
        EngineWorld nether = world("domained_nether");
        OwnershipDomain netherDomain = OwnershipDomain.create("simulation:nether");
        nether.attachDomain(netherDomain);

        // In-context: the bound executor mutates exactly as before (the boot
        // binds both domains to the loop's thread for the whole run).
        netherDomain.enter();
        try {
            nether.getOrGenerate(new ChunkPosition(0, 0));
            nether.setBlock(new BlockPosition(0, 4, 0), BuiltinBlocks.STONE);
            assertEquals(BuiltinBlocks.STONE, nether.getBlock(new BlockPosition(0, 4, 0)));
        } finally {
            netherDomain.exit();
        }

        // Out-of-context: another thread's mutation fails diagnosably —
        // the violation names the nether domain and the operation.
        AtomicReference<Throwable> caught = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread stranger = new Thread(() -> {
            try {
                nether.setBlock(new BlockPosition(1, 4, 1), BuiltinBlocks.STONE);
            } catch (Throwable t) {
                caught.set(t);
            } finally {
                done.countDown();
            }
        });
        stranger.start();
        try {
            assertTrue(done.await(5, TimeUnit.SECONDS), "the stranger finished");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted waiting for the stranger", e);
        }
        OwnershipViolationException violation =
                assertInstanceOf(OwnershipViolationException.class, caught.get());
        assertEquals("simulation:nether", violation.domain(), "the violation names the nether domain");
        assertEquals("setBlock", violation.operation());
    }

    @Test
    void netherDeltasPersistUnderTheDim1Directory(@TempDir Path dataDir) {
        // The boot's exact shape: DIM-1/zamin-delta.bin under the world data
        // directory. A built world saves; a fresh world applies the load back.
        Path dim1 = dataDir.resolve("DIM-1").resolve("zamin-delta.bin");
        assertFalse(java.nio.file.Files.exists(dim1), "nothing persisted before the first save");

        EngineWorld built = world("persisted_nether");
        OwnershipDomain domain = OwnershipDomain.create("simulation:nether");
        built.attachDomain(domain);
        DeltaWorldStorage storage = new DeltaWorldStorage(dim1, id -> registry.require(id));
        domain.enter();
        try {
            built.getOrGenerate(new ChunkPosition(2, -3));
            built.setBlock(new BlockPosition(32, 40, -48), BuiltinBlocks.STONE);
            built.setBlock(new BlockPosition(33, 40, -48), BuiltinBlocks.DIRT);
            // The saveAllNow walk rides the owner too (the boot's submit path).
            storage.save(built.snapshotDeltas());
        } finally {
            domain.exit();
        }
        assertTrue(java.nio.file.Files.exists(dim1), "the DIM-1 file landed");

        EngineWorld revived = world("revived_nether");
        domain.enter();
        try {
            storage.load().ifPresent(revived::applyDeltas);
            // The boot's order: deltas apply before any chunk generates —
            // the persisted edits override the freshly generated terrain
            // when the chunk arrives (§407 restart proof).
            assertEquals(2, revived.deltaCount(), "both nether edits rest in the delta map");
            revived.getOrGenerate(new ChunkPosition(2, -3));
        } finally {
            domain.exit();
        }
        assertEquals(BuiltinBlocks.STONE, revived.getBlock(new BlockPosition(32, 40, -48)),
                "the first nether edit survives the restart");
        assertEquals(BuiltinBlocks.DIRT, revived.getBlock(new BlockPosition(33, 40, -48)),
                "the second nether edit survives the restart");
    }

    private static <T extends Throwable> T assertInstanceOf(Class<T> type, Throwable actual) {
        assertNotNull(actual, "a throwable was expected");
        assertTrue(type.isInstance(actual),
                "expected " + type.getSimpleName() + " but got " + actual);
        return type.cast(actual);
    }
}
