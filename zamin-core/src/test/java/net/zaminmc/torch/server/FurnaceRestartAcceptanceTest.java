package net.zaminmc.torch.server;

import net.zaminmc.torch.ServerState;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Furnace restart proof: a furnace's contents live in the world (ZFD v1), so
 * ore loaded into a furnace survives the shutdown and is still sitting in the
 * block after a fresh server process boots on the same data.
 */
class FurnaceRestartAcceptanceTest {

    @TempDir
    Path dataDir;

    private static final BlockPosition FURNACE_POS = new BlockPosition(2, 5, 2);

    @Test
    void furnaceContentsSurviveShutdownAndRestart() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "smelting", "it", 20, 2, 20,
                dataDir.toString());

        // --- first life: place a furnace, open it, load ore, close, stop ------
        EngineServer first = new EngineServer(config);
        first.start();
        PlayerSession smelter = join(first, "Smelter");

        first.blockInteraction().submitPlace(smelter, new BlockPosition(2, 4, 2), 1,
                BuiltinBlocks.FURNACE);
        await(() -> first.world().getBlock(FURNACE_POS).equals(BuiltinBlocks.FURNACE),
                "furnace placed");

        AtomicInteger windowId = new AtomicInteger();
        first.useItemOnBlock(smelter, FURNACE_POS, -1, java.util.Optional.empty(), windowId::set);
        await(() -> windowId.get() > 0, "furnace window opened");

        first.chatService().submitChat(smelter, "/give iron_ore 3");
        await(() -> smelter.inventory().snapshot().stream()
                .anyMatch(stack -> !stack.isEmpty()
                        && stack.type().equals(BuiltinItems.IRON_ORE)), "ore given");

        // Shift-click the ore (engine hotbar slot 0 -> furnace wire 30) inside.
        AtomicInteger oreMoved = new AtomicInteger();
        first.windowClick(smelter, windowId.get(), 30, 0, 1, accepted -> oreMoved.set(accepted ? 1 : 0));
        await(() -> oreMoved.get() == 1, "ore shift-click accepted");
        await(() -> {
            FurnaceBlockEntity furnace = first.furnaces().peek(FURNACE_POS);
            return furnace != null && !furnace.input().isEmpty();
        }, "ore inside the furnace");

        // No fuel: the furnace stays inert, so the state is stable to observe.
        FurnaceBlockEntity furnace = first.furnaces().peek(FURNACE_POS);
        assertEquals(3, furnace.input().count());

        first.closeWindow(smelter, windowId.get());
        first.shutdown(null);
        awaitState(first);

        // --- second life: fresh process state, same data ----------------------
        EngineServer second = new EngineServer(config);
        second.start();
        await(() -> second.world().getBlock(FURNACE_POS).equals(BuiltinBlocks.FURNACE),
                "furnace block survived");
        FurnaceBlockEntity restored = second.furnaces().peek(FURNACE_POS);
        assertNotNull(restored, "the furnace's state must be restored");
        ItemStack input = restored.input();
        assertNotNull(input);
        assertFalse(input.isEmpty(), "the ore must still be inside");
        assertEquals(3, input.count());
        assertEquals(BuiltinItems.IRON_ORE, input.type());
        assertEquals(0, restored.burnTimeRemaining(), "an unfueled furnace stayed inert");
        second.shutdown(null);
        awaitState(second);
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
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private void awaitState(EngineServer server) throws InterruptedException {
        await(() -> server.state() == net.zaminmc.torch.ServerState.STOPPED, "server stopped");
    }
}
