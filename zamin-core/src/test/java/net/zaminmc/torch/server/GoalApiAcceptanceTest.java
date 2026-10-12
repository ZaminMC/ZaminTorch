package net.zaminmc.torch.server;

import net.zaminmc.torch.entity.LivingEntity;
import net.zaminmc.torch.entity.ai.Goal;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public goal-registration API (Slice 15a — §27.3's mob-AI family over
 * the live engine): {@link LivingEntity#goals()} — register, observe,
 * remove by identity, and the built-in remove/restore pair, with the
 * selector's priority and exclusivity laws visible through the surface.
 * Every mutation is admitted onto the owner lane (§27.6) and confirmed
 * through the {@code goals()} snapshot — the verbs themselves carry no
 * synchronous verdict by design.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GoalApiAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.CREATIVE);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** The snapshot holds a goal with the priority/exclusivity pair. */
    private BooleanSupplier holdsStroll(LivingEntity pig) {
        return () -> pig.goals().goals().stream().anyMatch(g -> g.priority() == 4 && g.exclusive());
    }

    /** An always-willing additive goal that counts its ticks (priority rides the constructor). */
    private static final class CountingGoal implements Goal {
        private final int priority;
        final AtomicInteger ticks = new AtomicInteger();
        final AtomicReference<String> thread = new AtomicReference<>();
        final CountDownLatch firstTick = new CountDownLatch(1);

        CountingGoal(int priority) {
            this.priority = priority;
        }

        @Override public int priority() { return priority; }
        @Override public boolean exclusive() { return false; }
        @Override public boolean canUse(LivingEntity mob, boolean night) { return true; }
        @Override public void tick(LivingEntity mob, boolean night) {
            ticks.incrementAndGet();
            thread.compareAndSet(null, Thread.currentThread().getName());
            firstTick.countDown();
        }
    }

    /** A gated exclusive goal: willing exactly while the flag stands. */
    private static final class GatedGoal implements Goal {
        final AtomicBoolean gate = new AtomicBoolean(false);

        @Override public int priority() { return 5; }
        @Override public boolean exclusive() { return true; }
        @Override public boolean canUse(LivingEntity mob, boolean night) { return gate.get(); }
        @Override public void tick(LivingEntity mob, boolean night) { }
    }

    private LivingEntity spawnPig() throws InterruptedException {
        join("Observer");
        Position at = new Position(8.5, 8.0, 8.5);
        var mob = server.mobs().spawnAt(net.zaminmc.torch.server.entity.MobType.PIG, at);
        assertNotNull(mob, "the pig spawned");
        return mob;
    }

    @Test
    void theRegisteredGoalTicksOnTheOwnerLane() throws Exception {
        server = boot();
        LivingEntity pig = spawnPig();

        // A priority-0 additive goal: the float's own band, ahead of every
        // exclusive goal — it contributes every mind tick the body lives.
        CountingGoal goal = new CountingGoal(0);
        pig.goals().register(goal);

        assertTrue(goal.firstTick.await(3, TimeUnit.SECONDS), "the goal ticked");
        await(() -> goal.ticks.get() >= 3, "the goal keeps ticking");
        assertEquals("zamin-tick", goal.thread.get(),
                "the goal body ran on the simulation owner's thread");

        // The snapshot: the plugin's own instance by identity, beside the
        // four built-in views (float, panic, breed, stroll).
        List<Goal> live = pig.goals().goals();
        assertTrue(live.contains(goal), "the registered instance is in the snapshot by identity");
        assertEquals(5, live.size(), "four built-ins plus the registered goal");
        server.shutdown(null);
    }

    @Test
    void theBuiltInStrollClaimsTheBodyUntilRemoved() throws Exception {
        server = boot();
        LivingEntity pig = spawnPig();

        // A priority-6 additive goal sits BELOW the stroll's exclusive
        // claim: while the built-in lives, the counter never fires.
        CountingGoal below = new CountingGoal(6);
        pig.goals().register(below);
        Thread.sleep(600); // ~12 ticks
        assertEquals(0, below.ticks.get(),
                "the exclusive stroll claim blocks every goal below it");

        // The remove/restore pair, by the stable id — confirmed through the
        // snapshot (the verbs carry no synchronous verdict).
        pig.goals().removeBuiltIn("stroll");
        await(() -> !holdsStroll(pig).getAsBoolean(), "the stroll left the mind");
        await(() -> below.ticks.get() >= 3, "the lower band runs once the claim is lifted");

        // The restore re-claims the body: the counter freezes again.
        pig.goals().restoreBuiltIn("stroll");
        await(holdsStroll(pig), "the stroll returned to the mind");
        int frozen = below.ticks.get();
        Thread.sleep(500);
        assertEquals(frozen, below.ticks.get(),
                "the restored stroll claim blocks the lower band again");
        server.shutdown(null);
    }

    @Test
    void theExclusiveCustomGoalClaimsTheLowerBand() throws Exception {
        server = boot();
        LivingEntity pig = spawnPig();

        // The stage: no stroll (removed by id), a gated exclusive at 5, a
        // counter at 6. Gate closed — the counter runs; gate open — the
        // exclusive claims the body and the counter freezes.
        pig.goals().removeBuiltIn("stroll");
        await(() -> !holdsStroll(pig).getAsBoolean(), "the stroll left the mind");
        GatedGoal claim = new GatedGoal();
        CountingGoal below = new CountingGoal(6);
        pig.goals().register(claim);
        pig.goals().register(below);
        await(() -> below.ticks.get() >= 3, "the counter runs with the gate closed");

        claim.gate.set(true);
        int frozen = below.ticks.get();
        Thread.sleep(500);
        assertEquals(frozen, below.ticks.get(),
                "the willing exclusive goal at 5 blocks the band below it");

        claim.gate.set(false);
        await(() -> below.ticks.get() > frozen, "the counter resumes with the gate closed again");

        // Removal by identity: the goal leaves the mind and stays gone.
        pig.goals().remove(below);
        await(() -> !pig.goals().goals().contains(below), "the goal left by identity");
        int afterRemove = below.ticks.get();
        Thread.sleep(400);
        assertEquals(afterRemove, below.ticks.get(), "the removed goal never ticks again");
        server.shutdown(null);
    }

    @Test
    void theSnapshotAddressesTheBuiltInsByView() throws Exception {
        server = boot();
        LivingEntity pig = spawnPig();

        // The built-ins dress as per-call views — a plugin holding one can
        // remove that built-in by identity through the same remove verb.
        List<Goal> live = pig.goals().goals();
        Goal strollView = live.stream()
                .filter(g -> g.priority() == 4 && g.exclusive())
                .findFirst()
                .orElseThrow(() -> new AssertionError("the stroll view is in the snapshot"));
        // The view is a probe too (§27.3's "querying intermediate decisions"):
        // for a peaceful pig with no hunt, the stroll wants the body.
        assertTrue(strollView.canUse(pig, false), "the stroll view probes willing");

        pig.goals().remove(strollView);
        await(() -> !holdsStroll(pig).getAsBoolean(), "the built-in left through its view");
        pig.goals().restoreBuiltIn("stroll");
        await(holdsStroll(pig), "the stable id still restores it");
        server.shutdown(null);
    }
}
