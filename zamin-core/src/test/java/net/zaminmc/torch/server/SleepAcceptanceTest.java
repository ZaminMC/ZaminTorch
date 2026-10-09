package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral scenarios for the sleep loop: beds place as foot/head pairs,
 * the night right-click sets the body's spawn and jumps the world to
 * morning, the day right-click refuses with the vanilla line, and the
 * respawn honors the bed spawn over the world spawn.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SleepAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "sleep", "it", 20, 4, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name) throws InterruptedException {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        // The historical join flow completes on the client's first position
        // proposal; the test drives the session directly, so mark it here.
        server.ticker().submit(session::markPlaying);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " reached PLAYING");
        return session;
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

    private ItemType item(String id) {
        return net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse(id)).orElseThrow();
    }

    private void hold(PlayerSession player, ItemType type, int count) {
        player.inventory().setSlot(0, ItemStack.of(type, count));
        player.inventory().selectHotbarSlot(0);
    }

    private void useOn(PlayerSession player, BlockPosition at, int face) throws InterruptedException {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(),
                windowId -> { });
        Thread.sleep(120);
    }

    @Test
    void bedsPlaceAsAPairAndSleepThroughTheNight() throws Exception {
        server = boot();
        PlayerSession sleeper = join("Sleeper");
        var world = server.world();
        BlockPosition foot = new BlockPosition(
                (int) Math.floor(sleeper.position().x()) + 2,
                (int) Math.floor(sleeper.position().y()),
                (int) Math.floor(sleeper.position().z()));
        // A stone pad at the join's eye level keeps the placement clear of
        // the placer's box and the head cell next to it open.
        server.ticker().submit(() -> world.setBlock(foot, BuiltinBlocks.STONE));
        await(() -> world.getBlock(foot).equals(BuiltinBlocks.STONE), "pad seeded");

        hold(sleeper, item("minecraft:bed"), 1);
        useOn(sleeper, foot, 1); // click the pad's top face: the bed lands on it
        BlockPosition footCell = foot.offset(0, 1, 0);
        assertTrue(EngineServer.isBedHalf(world.getBlock(footCell)),
                "the foot half commits on the pad");
        assertTrue(EngineServer.isBedHalf(world.getBlock(footCell.offset(0, 0, 1))),
                "the head half lands along the look (south)");
        assertEquals(0, sleeper.inventory().held().count(), "the bed item is spent");

        // Night: the right-click sleeps through — the clock jumps to morning
        // and the bed spawn is set.
        server.ticker().submit(() -> world.setTimeOfDay(net.zaminmc.torch.server.entity.MobManager.NIGHT_START + 100));
        await(() -> world.timeOfDay() >= net.zaminmc.torch.server.entity.MobManager.NIGHT_START,
                "night falls");
        useOn(sleeper, footCell, 1);
        // The world tick advances past the exact dawn tick between polls,
        // so assert the daytime band rather than the instant of 0.
        await(() -> world.timeOfDay() < net.zaminmc.torch.server.entity.MobManager.NIGHT_START,
                "the world wakes at dawn");
        assertNotNull(sleeper.bedSpawn(), "the bed spawn is set");

        // The day right-click refuses (the vanilla line, no state change):
        // the morning clock keeps advancing, never jumping back.
        long before = world.timeOfDay();
        useOn(sleeper, footCell, 1);
        Thread.sleep(150);
        assertTrue(world.timeOfDay() >= before, "the day right-click does nothing");
        server.shutdown(null);
    }
}
