package net.zaminmc.torch.server;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.Direction;
import net.zaminmc.torch.dispenser.DispenseBehavior;
import net.zaminmc.torch.dispenser.DispenseContext;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public dispenser behavior registry (Slice 15b — §27.3's dispenser
 * family over the live engine): {@link Server#dispenserBehaviors()} —
 * register an item's custom dispense behavior, inspect the registration,
 * and the reset verb's way back to the built-in answer. The behaviors run
 * inside the dispense walk on the owner lane; the registration verbs
 * admit onto the owner lane and {@code behaviorOf} confirms.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DispenseBehaviorApiAcceptanceTest {

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

    /** Places a dispenser facing east with one stack and a powering lever. */
    private void stageDispenser(int x, int y, int z, net.zaminmc.torch.item.ItemType item,
                                int count, int slot) {
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(x, y, z),
                    net.zaminmc.torch.server.dispenser.DispenserBlocks.dispenserOf(5, false));
            server.dispensers().getOrCreate(new BlockPosition(x, y, z))
                    .setItem(slot, ItemStack.of(item, count));
            server.world().setBlock(new BlockPosition(x + 1, y, z),
                    net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(x + 2, y, z),
                    net.zaminmc.torch.server.redstone.RedstoneBlocks.leverOf(1, false));
        });
    }

    @Test
    void theCustomBehaviorReceivesTheLiveContext() throws Exception {
        server = boot();
        join("Observer");
        int x = 10, z = 10, y = 40;
        BlockPosition dispenser = new BlockPosition(x, y, z);
        var diamond = net.zaminmc.torch.server.item.BuiltinItems.DIAMOND;
        stageDispenser(x, y, z, diamond, 3, 0);

        // A plugin-style custom behavior, registered from a FOREIGN thread
        // (the register verb admits onto the owner lane): it observes the
        // live context and consumes the whole stack.
        CountDownLatch ran = new CountDownLatch(1);
        AtomicReference<DispenseContext> seen = new AtomicReference<>();
        AtomicReference<String> thread = new AtomicReference<>();
        server.dispenserBehaviors().register(diamond, context -> {
            seen.set(context);
            thread.compareAndSet(null, Thread.currentThread().getName());
            ran.countDown();
            return ItemStack.EMPTY; // consume the use
        });

        // The confirmation channel: behaviorOf sees the registration.
        await(() -> server.dispenserBehaviors().behaviorOf(diamond).isPresent(),
                "the registration landed");
        assertTrue(server.dispenserBehaviors().behaviorOf(diamond).isPresent(),
                "behaviorOf answers the registered custom");

        // Power ON: the dispense walks into the custom behavior.
        server.ticker().submit(() -> server.redstone().useLever(
                new BlockPosition(x + 2, y, z)));
        assertTrue(ran.await(3, TimeUnit.SECONDS), "the custom behavior ran");
        assertEquals("zamin-tick", thread.get(),
                "the behavior ran on the simulation owner's thread");

        DispenseContext context = seen.get();
        assertNotNull(context, "the context arrived");
        assertEquals(dispenser, context.origin(), "the origin is the dispenser");
        assertEquals(Direction.EAST, context.facing(), "the facing is the placement's");
        assertEquals(new BlockPosition(x + 1, y, z), context.facingCell(),
                "the facing cell is the east neighbor");
        assertEquals(diamond, context.stack().type(), "the stack is the picked item");
        assertEquals(3, context.stack().count(), "the stack carries the slot's count");
        assertEquals(server.world().name(), context.world().name(),
                "the world is the live one the behavior may write through");
        await(() -> server.dispensers().peek(dispenser) != null
                        && server.dispensers().peek(dispenser).getItem(0).isEmpty(),
                "the slot took the behavior's empty remainder");
        server.shutdown(null);
    }

    @Test
    void theResetReturnsTheBuiltInAnswer() throws Exception {
        server = boot();
        join("Handler");
        int x = 30, z = 30, y = 40;
        BlockPosition dispenser = new BlockPosition(x, y, z);
        var arrow = net.zaminmc.torch.server.item.BuiltinItems.ARROW;
        stageDispenser(x, y, z, arrow, 4, 1);

        // Override the built-in arrow behavior, confirm, then reset.
        server.dispenserBehaviors().register(arrow, context -> context.stack());
        await(() -> server.dispenserBehaviors().behaviorOf(arrow).isPresent(),
                "the override landed");
        server.dispenserBehaviors().reset(arrow);
        await(() -> server.dispenserBehaviors().behaviorOf(arrow).isEmpty(),
                "the reset landed");
        assertFalse(server.dispenserBehaviors().behaviorOf(arrow).isPresent(),
                "behaviorOf is empty for the built-in answer");

        // The built-in arrow arm is back: the dispense launches a projectile
        // (the projectile manager's view — a projectile is not a mob).
        server.ticker().submit(() -> server.redstone().useLever(
                new BlockPosition(x + 2, y, z)));
        await(() -> {
            AtomicReference<Boolean> found = new AtomicReference<>(false);
            server.ticker().submit(() -> {
                for (var projectile : server.projectiles().all()) {
                    if (projectile.kind() == net.zaminmc.torch.server.entity.projectile
                            .ProjectileEntity.Kind.ARROW) {
                        found.set(true);
                    }
                }
            });
            try {
                Thread.sleep(30); // the submitted check runs on the tick lane
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return found.get();
        }, "the built-in arrow behavior launched the projectile");
        assertEquals(3, server.dispensers().peek(dispenser).getItem(1).count(),
                "the slot lost one arrow to the built-in behavior");
        server.shutdown(null);
    }
}
