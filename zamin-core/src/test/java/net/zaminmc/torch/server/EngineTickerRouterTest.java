package net.zaminmc.torch.server;

import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.concurrent.CrossOwnerRouter;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cross-owner router's live tick integration (the permanent
 * architecture's Phase 4 wiring — the design's §7 target-side apply at the
 * owner boundary): intents submitted from outside the owner land in the
 * tick's drain and apply inside the owner's context at the tick edge,
 * after the scheduler walk and before the tick handler; a duplicate
 * operation id drops harmlessly; the telemetry records the walk.
 */
class EngineTickerRouterTest {

    private static FrozenBlockRegistry registry;

    @BeforeAll
    static void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    @Test
    void submittedIntentsApplyAtTheTickEdgeInsideTheOwnerContext() throws Exception {
        EngineTicker ticker = new EngineTicker(20);
        EngineWorld world = new EngineWorld("routed", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        ticker.attachWorld(world);
        OwnershipDomain domain = OwnershipDomain.create("simulation:routed");
        CrossOwnerRouter router = new CrossOwnerRouter();
        ticker.attachCrossOwnerRouter(router, domain);

        AtomicBoolean applied = new AtomicBoolean(false);
        AtomicReference<String> applyingThread = new AtomicReference<>();
        // Submitted from THIS thread — which is not the owner (the owner
        // binding never entered here); the drain applies it at the tick.
        router.submit(new CrossOwnerRouter.Intent(domain, UUID.randomUUID(),
                "elsewhere", 0L, "test-operation", () -> {
                    applied.set(true);
                    applyingThread.set(Thread.currentThread().getName());
                }));

        // The tick walks inside the owner's binding (the engine's boot shape).
        domain.enter();
        try {
            ticker.tickOnce();
        } finally {
            domain.exit();
        }
        assertTrue(applied.get(), "the intent applied at the tick edge");
        assertEquals(Thread.currentThread().getName(), applyingThread.get(),
                "the apply ran inside the owner's context");
        assertEquals(1, router.appliedCount(domain));
        assertEquals(0, router.pendingFor(domain), "the drain emptied the target queue");
    }

    @Test
    void intentsApplyInAdmissionOrderAfterTheSchedulerWalk() {
        EngineTicker ticker = new EngineTicker(20);
        EngineWorld world = new EngineWorld("ordered", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        ticker.attachWorld(world);
        OwnershipDomain domain = OwnershipDomain.create("simulation:ordered");
        CrossOwnerRouter router = new CrossOwnerRouter();
        ticker.attachCrossOwnerRouter(router, domain);

        List<String> order = new ArrayList<>();
        // The scheduler walk runs before the router drain in the same tick.
        net.zaminmc.torch.server.concurrent.SimulationScheduler scheduler =
                new net.zaminmc.torch.server.concurrent.SimulationScheduler();
        ticker.attachScheduler(scheduler, domain);
        scheduler.submit(domain, () -> order.add("scheduled-first"));
        router.submit(new CrossOwnerRouter.Intent(domain, UUID.randomUUID(),
                "elsewhere", 0L, "intent-a", () -> order.add("intent-a")));
        router.submit(new CrossOwnerRouter.Intent(domain, UUID.randomUUID(),
                "elsewhere", 0L, "intent-b", () -> order.add("intent-b")));

        domain.enter();
        try {
            ticker.tickOnce();
        } finally {
            domain.exit();
        }
        assertEquals(List.of("scheduled-first", "intent-a", "intent-b"), order,
                "the tick's sequence: the scheduler walk, then the intents in admission order");
    }

    @Test
    void aDuplicateOperationIdDropsHarmlesslyAtTheTick() {
        EngineTicker ticker = new EngineTicker(20);
        EngineWorld world = new EngineWorld("deduped", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        ticker.attachWorld(world);
        OwnershipDomain domain = OwnershipDomain.create("simulation:deduped");
        CrossOwnerRouter router = new CrossOwnerRouter();
        ticker.attachCrossOwnerRouter(router, domain);

        int[] runs = {0};
        UUID operationId = UUID.randomUUID();
        boolean firstAdmitted = router.submit(new CrossOwnerRouter.Intent(domain, operationId,
                "elsewhere", 0L, "once-only", () -> runs[0]++));
        boolean duplicateAdmitted = router.submit(new CrossOwnerRouter.Intent(domain, operationId,
                "elsewhere", 0L, "once-only", () -> runs[0] = 99));

        assertTrue(firstAdmitted, "the first admission wins");
        assertEquals(false, duplicateAdmitted, "the duplicate is refused as already-handled");
        assertEquals(1, router.pendingFor(domain), "the duplicate never queued");
        assertEquals(1, router.duplicatesDropped(), "the exactly-once telemetry counted it");

        domain.enter();
        try {
            ticker.tickOnce();
        } finally {
            domain.exit();
        }
        assertEquals(1, runs[0], "only the admitted action ran (the duplicate's marker never fired)");
    }
}
