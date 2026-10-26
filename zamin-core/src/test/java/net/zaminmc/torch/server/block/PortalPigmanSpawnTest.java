package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The overworld portal's pigman (Slice TF-7e — PortalBlock.tick lines
 * 34-53, the reference's own random-tick arm): the doMobSpawning rule
 * and the {@code nextInt(2000) < difficulty} roll gate the walk, the
 * ground search walks down from the portal to the first solid top
 * (World.hasSolidTop — the frame's own bottom obsidian row), and the
 * pigman lands at the ground's up-face with the portal cooldown —
 * the arm that makes the pigman's INHERITED daylight burn reachable in
 * the overworld (ZombiePigmanEntity extends ZombieEntity; the burn walk
 * is the zombie's own).
 *
 * <p>The frame matters: the reference's PortalBlock.neighborChanged
 * kills a portal cell whose obsidian frame is invalid, so the probe
 * portal stands inside a hand-built 4x5 X-plane frame (the
 * PortalForcerTest geometry) — the placement the reference's own
 * PortalBuilder.build would leave behind.</p>
 */
class PortalPigmanSpawnTest {

    @TempDir
    Path dataDir;

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "portal", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private static void await(EngineServer server, BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> ran = new AtomicReference<>();
            server.ticker().submit(() -> ran.set(condition.getAsBoolean()));
            long inner = System.currentTimeMillis() + 200;
            while (System.currentTimeMillis() < inner && ran.get() == null) {
                Thread.sleep(5);
            }
            if (Boolean.TRUE.equals(ran.get())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    /**
     * Builds the 4x5 X-plane frame at x 29..32, y 39..43, z 30 (the
     * interior portal cells x 30..31 x y 40..42) and captures the
     * placement tick — the survival await then proves the frame passed
     * the neighbor validation the scheduled update carries.
     */
    private static AtomicLong buildFrame(EngineServer server) {
        AtomicLong placedTick = new AtomicLong();
        server.ticker().submit(() -> {
            for (int w = 0; w < 4; w++) {
                for (int h = -1; h < 4; h++) {
                    boolean frame = w == 0 || w == 3 || h == -1 || h == 3;
                    server.world().setBlock(new BlockPosition(29 + w, 40 + h, 30),
                            frame ? BuiltinBlocks.OBSIDIAN : BuiltinBlocks.NETHER_PORTAL);
                }
            }
            placedTick.set(server.world().totalTicks());
        });
        return placedTick;
    }

    @Test
    void theOverworldPortalSpawnsItsPigman() throws Exception {
        EngineServer server = boot();
        try {
            // A portal standing in a valid frame, high above the flat
            // world's floor (the frame's own bottom row owns the ground
            // the search finds).
            AtomicLong placedTick = buildFrame(server);
            BlockPosition portal = new BlockPosition(30, 41, 30);
            await(server, () -> server.world().totalTicks() >= placedTick.get() + 10
                    && server.world().getBlock(portal).identifier().value().equals("nether_portal"),
                    "the framed portal placed and surviving");

            // The deterministic probe (the growth family's own pattern —
            // the sampler's 3-of-4096 odds make end-to-end flaky): drive
            // the portal cell's own random tick until the difficulty roll
            // lands (difficulty 1: the 1-in-2000 band). The spawn site is
            // captured at the roll — the body walks away after (lesson:
            // the live cell must be read at the event, never after).
            AtomicReference<MobEntity> pigman = new AtomicReference<>();
            AtomicReference<Position> site = new AtomicReference<>();
            server.ticker().submit(() -> {
                for (int i = 0; i < 60_000 && pigman.get() == null; i++) {
                    server.randomTicks().randomTick(portal);
                    for (MobEntity mob : server.mobs().all()) {
                        if (mob.type() == MobType.ZOMBIE_PIGMAN && pigman.get() == null) {
                            pigman.set(mob);
                            site.set(mob.position());
                        }
                    }
                }
            });
            await(server, () -> pigman.get() != null, "the portal spawned its pigman");
            MobEntity body = pigman.get();
            assertNotNull(body, "the pigman landed");
            Position landed = site.get();

            // The spawn site: the ground below the portal — the frame's
            // bottom obsidian row at y 39 — the up-face (x + 0.5,
            // ground + 1.1, z + 0.5 — the reference's own coordinates).
            assertTrue(landed.x() > 29.0 && landed.x() < 32.0,
                    "the pigman's x sits at the portal's column (was "
                            + landed.x() + ")");
            assertTrue(landed.z() > 29.0 && landed.z() < 32.0,
                    "the pigman's z sits at the portal's column (was "
                            + landed.z() + ")");
            assertTrue(landed.y() > 39.0 && landed.y() <= 41.1,
                    "the pigman stands on the ground under the portal (was "
                            + landed.y() + ")");

            // The portal cooldown (Entity.getPortalCooldown's 300): the
            // fresh body never re-enters the portal it was born at.
            assertTrue(body.portalCooldown() > 0,
                    "the portal-born body carries its re-entry cooldown (was "
                            + body.portalCooldown() + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void thePeacefulWorldNeverSpawnsThePigman() throws Exception {
        EngineServer server = boot();
        try {
            AtomicLong placedTick = buildFrame(server);
            BlockPosition portal = new BlockPosition(30, 41, 30);
            await(server, () -> server.world().totalTicks() >= placedTick.get() + 10
                    && server.world().getBlock(portal).identifier().value().equals("nether_portal"),
                    "the framed portal placed and surviving");
            // The peaceful difficulty: the roll's band is empty
            // (nextInt(2000) >= 0 always). The command walk sets it.
            server.chatService().dispatchCommand(new net.zaminmc.torch.server.chat.CommandSender() {
                @Override public String name() {
                    return "Server";
                }

                @Override public void sendMessage(String message) {
                }

                @Override public int opLevel() {
                    return 4;
                }

                @Override public net.zaminmc.torch.server.player.PlayerSession player() {
                    return null;
                }
            }, "/difficulty peaceful");
            await(server, () -> server.difficulty() == 0, "the peaceful difficulty landed");

            AtomicReference<Integer> count = new AtomicReference<>();
            server.ticker().submit(() -> {
                for (int i = 0; i < 20_000; i++) {
                    server.randomTicks().randomTick(portal);
                }
                count.set((int) server.mobs().all().stream()
                        .filter(m -> m.type() == MobType.ZOMBIE_PIGMAN).count());
            });
            await(server, () -> count.get() != null, "the peaceful probe finished");
            assertTrue(count.get() == 0,
                    "the peaceful world never spawns the portal pigman (saw " + count.get() + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }
}
