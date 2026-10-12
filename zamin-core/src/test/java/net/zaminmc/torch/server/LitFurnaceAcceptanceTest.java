package net.zaminmc.torch.server;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.furnace.FurnaceBlockEntity;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lit furnace visual: the burn drives the historical block swap — a
 * burning furnace reads as block 62 (lit, the 13-light source) and cools
 * back to 61 when the fuel runs out. The container state survives the swap.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LitFurnaceAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "lit", "it", 20, 4, 20,
                dataDir.toString());
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
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private ItemType item(String id) {
        return net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse(id)).orElseThrow();
    }

    private void setBlock(BlockPosition at, BlockType type) throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, type));
        await(() -> server.world().getBlock(at).equals(type), "seeded " + at);
    }

    @Test
    void aBurningFurnaceSwapsToTheLitBlockAndBack() throws Exception {
        server = boot();
        PlayerSession smelter = join("Smelter");
        BlockPosition at = new BlockPosition(0, 64, 0);
        setBlock(at, BuiltinBlocks.FURNACE);

        // Load the furnace through its own state: ore in, one stick of fuel
        // (the historical 100-tick burn — long enough to see the lit block,
        // short enough to watch it cool inside the test window).
        server.ticker().submit(() -> {
            FurnaceBlockEntity furnace = server.furnaceManager().getOrCreate(at);
            furnace.quickMoveIn(ItemStack.of(item("minecraft:iron_ore"), 1));
            furnace.quickMoveIn(ItemStack.of(item("minecraft:stick"), 1));
        });
        await(() -> server.furnaceManager().at(at) != null
                        && server.furnaceManager().at(at).burning(),
                "the furnace lit");

        // The block swaps to the lit variant while the burn holds.
        await(() -> server.world().getBlock(at).equals(BuiltinBlocks.FURNACE_LIT),
                "the block reads as the lit furnace");

        // The burn eventually ends: the block cools back to plain furnace.
        await(() -> !server.furnaceManager().at(at).burning(),
                "the fuel burned out");
        await(() -> server.world().getBlock(at).equals(BuiltinBlocks.FURNACE),
                "the block cooled back to the plain furnace");
        assertFalse(server.furnaceManager().at(at).burning());
        server.shutdown(null);
    }

    @Test
    void theLitBlockStillOpensTheFurnaceWindow() throws Exception {
        server = boot();
        PlayerSession smelter = join("Smelter");
        BlockPosition at = new BlockPosition(0, 64, 0);
        setBlock(at, BuiltinBlocks.FURNACE_LIT);

        // The lit variant is a furnace: the use dispatch opens its window.
        int[] windowId = {-1};
        server.useItemOnBlock(smelter, at, 1, java.util.Optional.empty(),
                id -> windowId[0] = id);
        await(() -> windowId[0] > 0, "the lit furnace opened its window");
        assertEquals(PlayerSession.ContainerKind.FURNACE, smelter.openContainerKind(),
                "the lit furnace's window is the furnace GUI");
        server.shutdown(null);
    }
}
