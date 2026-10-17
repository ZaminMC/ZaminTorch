package net.zaminmc.torch.server.border;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world border's persistence (Slice 90 — the reference's level.dat
 * border rows as worldborder.json): the center pair, the stationary size,
 * the damage family, and the pending lerp's resume across the reboot.
 */
class WorldBorderPersistenceTest {

    @TempDir
    static Path dataDir;

    private static final String WORLD_NAME =
            "border" + Math.abs(UUID.randomUUID().hashCode() % 100000);

    private static net.zaminmc.torch.server.EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, WORLD_NAME,
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        net.zaminmc.torch.server.EngineServer server =
                new net.zaminmc.torch.server.EngineServer(config);
        server.start();
        return server;
    }

    private static PlayerSession join(net.zaminmc.torch.server.EngineServer server, String name)
            throws InterruptedException {
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(new SilentLink(), name,
                        UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = accepted.session();
        server.ticker().submit(session::markPlaying);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, "playing");
        return session;
    }

    private static final class SilentLink implements net.zaminmc.torch.server.net.ClientLink {
        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }
    }

    private static void await(java.util.function.BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void theBorderSurvivesTheRestart() throws Exception {
        net.zaminmc.torch.server.EngineServer first = boot();
        PlayerSession alice = join(first, "Alice");
        first.ticker().submit(() -> alice.setOpLevel(2));
        await(() -> alice.opLevel() >= 2, "opped");
        // The stationary family: center, size, the damage pair, the warnings.
        first.chatService().submitChat(alice, "/worldborder center 100.5 200.5");
        first.chatService().submitChat(alice, "/worldborder set 5000");
        first.chatService().submitChat(alice, "/worldborder damage buffer 3.0");
        first.chatService().submitChat(alice, "/worldborder damage amount 0.5");
        first.chatService().submitChat(alice, "/worldborder warning time 30");
        first.chatService().submitChat(alice, "/worldborder warning distance 10");
        await(() -> first.worldBorder().getLerpSize() == 5000.0, "the resize landed");
        first.chatService().submitChat(alice, "/save");
        await(() -> first.world().totalTicks() >= 2, "the save ran");
        first.shutdown(null);
        // --- the second life: the same data dir, the border back.
        net.zaminmc.torch.server.EngineServer second = boot();
        try {
            EngineWorldBorder border = second.worldBorder();
            assertEquals(100.5, border.getCenterX(), 1e-9, "the center X");
            assertEquals(200.5, border.getCenterZ(), 1e-9, "the center Z");
            assertEquals(5000.0, border.getLerpSize(), 1e-9, "the size");
            assertEquals(3.0, border.getSafeZone(), 1e-9, "the safe zone");
            assertEquals(0.5, border.getDamagePerBlock(), 1e-9, "the per-block damage");
            assertEquals(30, border.getWarningTime(), "the warning time");
            assertEquals(10, border.getWarningDistance(), "the warning distance");
        } finally {
            second.shutdown(null);
        }
    }

    @Test
    void thePendingLerpResumesAcrossTheRestart() throws Exception {
        net.zaminmc.torch.server.EngineServer first = boot();
        PlayerSession alice = join(first, "Alice");
        first.ticker().submit(() -> alice.setOpLevel(2));
        await(() -> alice.opLevel() >= 2, "opped");
        first.chatService().submitChat(alice, "/worldborder set 4000 5");
        await(() -> first.worldBorder().getSizeLerpTarget() == 4000.0, "the lerp armed");
        first.chatService().submitChat(alice, "/save");
        await(() -> first.world().totalTicks() >= 2, "the save ran");
        first.shutdown(null);
        // The reboot: the lerp resumes with the remaining time (the reference's
        // BorderSizeLerpTarget != BorderSize arm).
        net.zaminmc.torch.server.EngineServer second = boot();
        try {
            EngineWorldBorder border = second.worldBorder();
            assertEquals(4000.0, border.getSizeLerpTarget(), 1e-9, "the lerp target");
            assertTrue(border.getLerpTime() > 0, "the lerp still has time left");
            assertTrue(border.getLerpSize() > 4000.0, "the border has not finished (the shrink from 5000 is mid-flight)");
        } finally {
            second.shutdown(null);
        }
    }
}
