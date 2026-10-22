package net.zaminmc.torch.server;

import net.zaminmc.torch.server.border.EngineWorldBorder;
import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The world border (Slice 86 — WorldBorder 1:1): the geometry (the
 * center/size box with the max-size clamp), the wall-clock lerp walk toward
 * a target size (the snap at completion), the containment + distance reads,
 * the damage walk (a player whose box left the border takes
 * max(1, floor(-depth * rate)) per tick — IN_WALL, bypassing armor), the
 * setter broadcasts, and the /worldborder command's arms.
 */
class WorldBorderTest {

    private java.nio.file.Path dataDir;

    private static final class BorderLink implements ClientLink {
        final List<String> packets = new CopyOnWriteArrayList<>();

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }

        @Override
        public void worldBorderSize(double sizeLerpTarget) {
            packets.add("size:" + sizeLerpTarget);
        }

        @Override
        public void worldBorderLerp(double lerpSize, double sizeLerpTarget, long lerpTimeMs) {
            packets.add("lerp:" + lerpSize + "/" + sizeLerpTarget + "/" + lerpTimeMs);
        }

        @Override
        public void worldBorderCenter(double centerX, double centerZ) {
            packets.add("center:" + centerX + "/" + centerZ);
        }

        @Override
        public void worldBorderWarningTime(int seconds) {
            packets.add("warnTime:" + seconds);
        }

        @Override
        public void worldBorderWarningBlocks(int blocks) {
            packets.add("warnBlocks:" + blocks);
        }

        @Override
        public void worldBorderInit(double centerX, double centerZ, double lerpSize,
                                    double sizeLerpTarget, long lerpTimeMs, int maxSize,
                                    int warningBlocks, int warningTime) {
            packets.add("init:" + centerX + "/" + centerZ + "/" + lerpSize + "/"
                    + sizeLerpTarget + "/" + lerpTimeMs + "/" + maxSize + "/"
                    + warningBlocks + "/" + warningTime);
        }
    }

    private static final class RecordingConsole implements CommandSender {
        final List<String> replies = new CopyOnWriteArrayList<>();

        @Override
        public String name() {
            return "Console";
        }

        @Override
        public void sendMessage(String message) {
            replies.add(message);
        }

        @Override
        public int opLevel() {
            return 4;
        }

        @Override
        public PlayerSession player() {
            return null;
        }
    }

    private EngineServer server;

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "border", "it", 20, 2, 20,
                dataDir.toString());
        server = new EngineServer(config);
        server.start();
        return server;
    }

    @Test
    void theGeometryAndTheLerpWalk() throws Exception {
        net.zaminmc.torch.server.player.PlayerRegistry registry =
                new net.zaminmc.torch.server.player.PlayerRegistry();
        EngineWorldBorder border = new EngineWorldBorder(registry);

        // The defaults (WorldBorder's own field table).
        assertEquals(6.0E7, border.getLerpSize(), "the unconfigured border");
        assertEquals(0.2, border.getDamagePerBlock());
        assertEquals(5.0, border.getSafeZone());
        assertEquals(15, border.getWarningTime());
        assertEquals(5, border.getWarningDistance());
        assertEquals(29999984, border.maxSize());

        // A centered 10-block border: min -5, max +5.
        border.setSize(10.0);
        assertEquals(-5.0, border.getMinX(), 1e-9);
        assertEquals(5.0, border.getMaxX(), 1e-9);
        assertTrue(border.contains(0, 0));
        assertFalse(border.contains(6, 0));
        assertEquals(5.0, border.getDistanceFrom(0, 0), 1e-9, "the center is 5 from the edge");
        assertEquals(-1.0, border.getDistanceFrom(6, 0), 1e-9, "outside = negative");

        // The off-center move.
        border.setCenter(100.0, 0.0);
        assertEquals(95.0, border.getMinX(), 1e-9);
        assertEquals(105.0, border.getMaxX(), 1e-9);

        // The lerp: from 10 toward 20 over 100 ms — midway after ~60 ms.
        border.setSize(10.0);
        border.setSize(10.0, 20.0, 100L);
        assertTrue(border.isGrowing());
        EngineAwaits.awaitTicks(server.world(), 2, "engine progress (a 60 wall settle)");
        double midway = border.getLerpSize();
        assertTrue(midway > 10.0 && midway < 20.0, "the wall-clock walk advances: " + midway);
        try {
            await(() -> border.getLerpSize() == 20.0, "the lerp completes and snaps");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        assertFalse(border.isGrowing());
        assertEquals(20.0, border.getLerpSize());
    }

    @Test
    void theSetterBroadcastsRideTheWire() throws Exception {
        dataDir = Files.createTempDirectory("border-wire");
        boot();
        try {
            BorderLink link = new BorderLink();
            var accepted = server.joinRequest(link, "Warden",
                    UUID.nameUUIDFromBytes("OfflinePlayer:Warden".getBytes()));
            PlayerSession warden = ((EngineBridge.Accepted) accepted).session();
            server.joinTabList(warden);
            server.joinScoreboard(warden);
            server.joinWorldBorder(warden);
            server.joinCompleted(warden);
            await(() -> warden.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "playing");

            // The join INITIALIZE carries the full default state.
            assertTrue(link.packets.stream().anyMatch(p -> p.startsWith("init:0.0/0.0/6.0E7")
                            && p.endsWith("/29999984/5/15")),
                    "the join init: " + link.packets);

            // Every setter broadcasts its own verb.
            server.ticker().submit(() -> server.worldBorder().setSize(100.0));
            await(() -> link.packets.stream().anyMatch(p -> p.equals("size:100.0")),
                    "the SET_SIZE broadcast");
            server.ticker().submit(() -> server.worldBorder().setCenter(10.0, 20.0));
            await(() -> link.packets.stream().anyMatch(p -> p.equals("center:10.0/20.0")),
                    "the SET_CENTER broadcast");
            server.ticker().submit(() -> server.worldBorder().setWarningTime(30));
            await(() -> link.packets.stream().anyMatch(p -> p.equals("warnTime:30")),
                    "the SET_WARNING_TIME broadcast");
            server.ticker().submit(() -> server.worldBorder().setWarningDistance(10));
            await(() -> link.packets.stream().anyMatch(p -> p.equals("warnBlocks:10")),
                    "the SET_WARNING_BLOCKS broadcast");
            server.ticker().submit(() -> server.worldBorder().setSize(100.0, 200.0, 5000L));
            await(() -> link.packets.stream().anyMatch(p -> p.startsWith("lerp:100.0/200.0/")),
                    "the LERP_SIZE broadcast");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theDamageWalkChewsPlayersOutsideTheBorder() throws Exception {
        dataDir = Files.createTempDirectory("border-damage");
        boot();
        try {
            BorderLink link = new BorderLink();
            var accepted = server.joinRequest(link, "Runner",
                    UUID.nameUUIDFromBytes("OfflinePlayer:Runner".getBytes()));
            PlayerSession runner = ((EngineBridge.Accepted) accepted).session();
            server.joinCompleted(runner);
            await(() -> runner.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "playing");

            // A tight border around the world spawn: the runner stands AT the
            // spawn (inside), then walks out.
            double spawnX = runner.position().x();
            double spawnZ = runner.position().z();
            server.ticker().submit(() -> {
                var border = server.worldBorder();
                border.setCenter(spawnX, spawnZ);
                border.setSize(20.0); // +-10 around the spawn
                border.setSafeZone(0.0); // no buffer: damage starts at the edge
                border.setDamagePerBlock(1.0);
            });
            await(() -> server.worldBorder().getLerpSize() == 20.0, "the border is set");

            float inside = runner.health();
            EngineAwaits.awaitTicks(server.world(), 8, "several body ticks inside");
            assertEquals(inside, runner.health(), 1e-6, "inside the border: no damage");

            // Walk 15 blocks out (10-block half-width + 5 beyond).
            double targetX = spawnX + 15.0;
            server.ticker().submit(() -> runner.applyMovement(
                    new net.zaminmc.torch.util.Position(targetX, runner.position().y(), spawnZ),
                    runner.rotation(), true));
            await(() -> runner.position().x() >= targetX - 0.5, "the runner stands outside");
            float before = runner.health();
            await(() -> runner.health() < before, "the outside-of-border damage lands");
            // The depth walk: ~5 blocks past the edge at rate 1.0 -> ~5 per
            // tick (bounded below by 1). A few ticks must dent 20 health.
            assertTrue(runner.health() < 20.0f, "the per-tick chew compounds");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theCommandArms() throws Exception {
        dataDir = Files.createTempDirectory("border-cmd");
        boot();
        RecordingConsole console = new RecordingConsole();
        try {
            java.util.function.BiFunction<String, String, Boolean> command = (line, expect) -> {
                console.replies.clear();
                server.ticker().submit(() -> server.chatService().dispatchCommand(console, line));
                try {
                    await(() -> !console.replies.isEmpty(), "the command answered");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                return console.replies.get(0).contains(expect);
            };

            assertTrue(command.apply("/worldborder get", "60000000"), console.replies.get(0));
            assertTrue(command.apply("/worldborder set 100", "100.0"), console.replies.get(0));
            assertEquals(100.0, server.worldBorder().getLerpSize());
            assertTrue(command.apply("/worldborder add 50 10", "150.0"), console.replies.get(0));
            assertTrue(server.worldBorder().isGrowing(), "the lerped add");
            assertTrue(command.apply("/worldborder center 10 20", "10.0"), console.replies.get(0));
            assertEquals(10.0, server.worldBorder().getCenterX());
            assertTrue(command.apply("/worldborder damage buffer 10", "10.0"),
                    console.replies.get(0));
            assertEquals(10.0, server.worldBorder().getSafeZone());
            assertTrue(command.apply("/worldborder damage amount 0.5", "0.50"),
                    console.replies.get(0));
            assertEquals(0.5, server.worldBorder().getDamagePerBlock());
            assertTrue(command.apply("/worldborder warning time 30", "30"),
                    console.replies.get(0));
            assertEquals(30, server.worldBorder().getWarningTime());
            assertTrue(command.apply("/worldborder warning distance 16", "16"),
                    console.replies.get(0));
            assertEquals(16, server.worldBorder().getWarningDistance());
        } finally {
            server.shutdown(null);
        }
    }

    private static void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting for: " + what);
            }
            Thread.sleep(25);
        }
    }
}
