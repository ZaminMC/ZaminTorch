package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import net.zaminmc.torch.server.concurrent.OwnershipViolationException;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The EngineWorld mutation paths assert through the formal ownership domain
 * (the permanent architecture's Phase 1 wiring — the design's §15
 * enforcement over the historical thread check): out-of-context mutation
 * fails with the domain + operation context; in-context mutation behaves
 * exactly as before.
 */
class EngineWorldDomainTest {

    private static FrozenBlockRegistry registry;

    @BeforeAll
    static void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    @Test
    void worldMutationOutsideTheDomainFailsDiagnosably() throws Exception {
        EngineWorld world = new EngineWorld("domained", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        OwnershipDomain domain = OwnershipDomain.create("simulation:domained");
        world.attachDomain(domain);
        assertEquals(domain, world.domain());

        // In-context: the bound executor mutates exactly as before.
        domain.enter();
        try {
            world.getOrGenerate(new ChunkPosition(0, 0));
            world.setBlock(new BlockPosition(0, 4, 0),
                    net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
        } finally {
            domain.exit();
        }

        // Out-of-context: the diagnosable violation carries the domain and
        // the operation (and stays an IllegalStateException — the
        // historical wrong-thread contract).
        CountDownLatch attempted = new CountDownLatch(1);
        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread intruder = new Thread(() -> {
            try {
                world.setBlock(new BlockPosition(1, 4, 0),
                        net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
            } catch (Throwable t) {
                caught.set(t);
            } finally {
                attempted.countDown();
            }
        }, "intruder");
        intruder.start();
        assertTrue(attempted.await(5, TimeUnit.SECONDS), "the probe ran");
        OwnershipViolationException violation =
                assertInstanceOf(OwnershipViolationException.class, caught.get());
        assertEquals("setBlock", violation.operation());
        assertEquals("simulation:domained", violation.domain());
    }

    @Test
    void theLegacyThreadCheckStillGuardsUnattachedWorlds() throws Exception {
        EngineWorld world = new EngineWorld("legacy", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        assertNotNull(world, "the legacy fixture constructs");
        CountDownLatch attempted = new CountDownLatch(1);
        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread intruder = new Thread(() -> {
            try {
                world.setTimeOfDay(1000);
            } catch (Throwable t) {
                caught.set(t);
            } finally {
                attempted.countDown();
            }
        }, "intruder");
        intruder.start();
        assertTrue(attempted.await(5, TimeUnit.SECONDS), "the probe ran");
        // No domain attached: the historical IllegalStateException, not the
        // violation type.
        assertInstanceOf(IllegalStateException.class, caught.get());
        assertTrue(!(caught.get() instanceof OwnershipViolationException),
                "the legacy path stays on the plain guard");
    }
}
