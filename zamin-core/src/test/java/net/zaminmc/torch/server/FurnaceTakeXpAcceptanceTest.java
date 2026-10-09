package net.zaminmc.torch.server;

import net.zaminmc.torch.ServerState;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.furnace.FurnaceBlockEntity;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The furnace-take XP (the historical SlotFurnaceOutput rule): taking a
 * smelted stack out of the output slot pays its recipe's experience to the
 * taker (gold's 1.0-per-item makes the award exact), and a fuel-less furnace
 * never pays anything.
 */
class FurnaceTakeXpAcceptanceTest {

    @TempDir
    Path dataDir;

    private static final BlockPosition FURNACE_POS = new BlockPosition(2, 5, 2);

    @Test
    void takingGoldOutOfTheFurnacePaysTheRecipeExperience() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "smelting", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        try {
            PlayerSession smelter = join(server, "Smelter");

            server.blockInteraction().submitPlace(smelter, new BlockPosition(2, 4, 2), 1,
                    BuiltinBlocks.FURNACE);
            await(() -> server.world().getBlock(FURNACE_POS).equals(BuiltinBlocks.FURNACE),
                    "furnace placed");

            AtomicInteger windowId = new AtomicInteger();
            server.useItemOnBlock(smelter, FURNACE_POS, -1, java.util.Optional.empty(),
                    windowId::set);
            await(() -> windowId.get() > 0, "furnace window opened");

            server.chatService().submitChat(smelter, "/give gold_ore 3");
            server.chatService().submitChat(smelter, "/give coal 2");
            await(() -> smelter.inventory().snapshot().stream().anyMatch(stack -> !stack.isEmpty()
                    && stack.type().equals(BuiltinItems.GOLD_ORE)), "gold ore given");

            // Load the furnace: ore (engine slot 0) and fuel shift-click in.
            server.windowClick(smelter, windowId.get(), 30, 0, 1, accepted -> { });
            await(() -> {
                var furnace = server.furnaces().peek(FURNACE_POS);
                return furnace != null && !furnace.input().isEmpty();
            }, "ore loaded");
            // The coal sits in the next inventory slot after the ore stack.
            server.windowClick(smelter, windowId.get(), 31, 0, 1, accepted -> { });
            await(() -> {
                var furnace = server.furnaces().peek(FURNACE_POS);
                return furnace != null && furnace.burnTimeRemaining() > 0;
            }, "coal loaded, furnace lit");

            // Three gold smelts (200 ticks each) — the window stays open.
            awaitMs(45_000, () -> {
                var furnace = server.furnaces().peek(FURNACE_POS);
                return furnace != null && !furnace.output().isEmpty()
                        && furnace.output().count() >= 3;
            }, "three gold ingots smelted");

            long xpBefore = smelter.totalXp();
            // Shift-click the output (wire slot 2, mode 1) into the inventory.
            server.windowClick(smelter, windowId.get(), 2, 0, 1, accepted -> { });
            await(() -> smelter.totalXp() > xpBefore, "take xp awarded");

            long paid = smelter.totalXp() - xpBefore;
            assertEquals(3, paid, "gold's 1.0-per-item pays exactly three points");
            assertTrue(smelter.inventory().snapshot().stream().anyMatch(stack -> !stack.isEmpty()
                    && stack.type().equals(BuiltinItems.GOLD_INGOT)), "the ingots came out");
        } finally {
            server.shutdown(null);
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        }
    }

    @Test
    void anEmptyOutputTakePaysNothing() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "smelting", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        try {
            PlayerSession smelter = join(server, "Smelter");
            server.blockInteraction().submitPlace(smelter, new BlockPosition(2, 4, 2), 1,
                    BuiltinBlocks.FURNACE);
            await(() -> server.world().getBlock(FURNACE_POS).equals(BuiltinBlocks.FURNACE),
                    "furnace placed");
            AtomicInteger windowId = new AtomicInteger();
            server.useItemOnBlock(smelter, FURNACE_POS, -1, java.util.Optional.empty(),
                    windowId::set);
            await(() -> windowId.get() > 0, "furnace window opened");

            long xpBefore = smelter.totalXp();
            // A click on the empty output slot (wire 2): nothing leaves, nothing pays.
            server.windowClick(smelter, windowId.get(), 2, 0, 0, accepted -> { });
            Thread.sleep(200);
            assertEquals(xpBefore, smelter.totalXp(), "an empty take never pays");
        } finally {
            server.shutdown(null);
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        }
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        };
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        awaitMs(10_000, condition, description);
    }

    private void awaitMs(long timeoutMs, BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("timed out waiting for " + description);
    }
}
