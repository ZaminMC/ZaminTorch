package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.Server;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.event.world.ExplosionEvent;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * Slice 91 Wave C p2 — the creeper's own rows over the live engine
 * (CreeperEntity lines 26-28 + 206-213): the ExplosionRadius row steers the
 * blast's base, the charged flag (powered) doubles it, the Fuse row steers
 * the swell's duration, and the ignited flag's swell runs with nobody in
 * sight (tick lines 105-107 — the flag forces the fuse direction 1 every
 * tick, target or no target).
 */
class CreeperRowsAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "crw" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description)
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

    private PlayerSession join(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    @Test
    void theRadiusRowAndTheChargedFlagSteerTheBlast() throws Exception {
        PlayerSession walker = join("Walker", new Position(0.5, 5, 0.5));
        await(() -> walker.state() == PlayerState.PLAYING, "session reaches PLAYING");
        AtomicReference<ExplosionEvent> blast = new AtomicReference<>();
        var sub = ((Server) server).events().on(ExplosionEvent.class, blast::set);
        // A creeper one block out (inside the prime band) with
        // ExplosionRadius 2, the charged flag, and a fast 5-tick fuse: the
        // reference's explode() walks radius * (charged ? 2.0F : 1.0F).
        var creeper = server.mobs().spawnAt(MobType.CREEPER, new Position(1.5, 5, 0.5));
        assertNotNull(creeper, "the creeper spawned");
        server.ticker().submit(() -> {
            creeper.setCreeperExplosionRadius(2);
            creeper.setCreeperCharged(true);
            creeper.setCreeperFuseTime(5);
        });
        await(() -> blast.get() != null, "the charged blast detonated");
        assertEquals(4.0f, blast.get().power(),
                "the ExplosionRadius row * the charged doubling (2 * 2.0F)");
        sub.unsubscribe();
    }

    @Test
    void theIgnitedFlagSwellRunsWithoutATarget() throws Exception {
        PlayerSession walker = join("Walker", new Position(0.5, 5, 0.5));
        await(() -> walker.state() == PlayerState.PLAYING, "session reaches PLAYING");
        AtomicReference<ExplosionEvent> blast = new AtomicReference<>();
        var sub = ((Server) server).events().on(ExplosionEvent.class, blast::set);
        // A creeper 40 blocks out — beyond the 16-block awareness band, no
        // target, no goal-primed swell — with the ignited flag and a
        // 20-tick fuse: the swell still climbs (the flag forces the fuse
        // direction 1 every tick, CreeperEntity.tick lines 105-107).
        var creeper = server.mobs().spawnAt(MobType.CREEPER, new Position(40.5, 5, 0.5));
        assertNotNull(creeper, "the far creeper spawned");
        server.ticker().submit(() -> {
            creeper.setCreeperIgnited(true);
            creeper.setCreeperFuseTime(20);
        });
        await(() -> blast.get() != null, "the ignited no-target swell detonated");
        assertEquals(3.0f, blast.get().power(),
                "the default radius row, uncharged (3 * 1.0F)");
        sub.unsubscribe();
    }

    @Test
    void theGroundStillTearsUnderTheRows() throws Exception {
        // The rows change the LAW, not just the event: a radius-1 blast on
        // the flat ground eats a strictly smaller sphere than the default.
        PlayerSession walker = join("Walker", new Position(0.5, 5, 0.5));
        await(() -> walker.state() == PlayerState.PLAYING, "session reaches PLAYING");
        AtomicReference<ExplosionEvent> blast = new AtomicReference<>();
        var sub = ((Server) server).events().on(ExplosionEvent.class, blast::set);
        var creeper = server.mobs().spawnAt(MobType.CREEPER, new Position(1.5, 5, 0.5));
        assertNotNull(creeper, "the creeper spawned");
        server.ticker().submit(() -> {
            creeper.setCreeperExplosionRadius(1);
            creeper.setCreeperFuseTime(5);
        });
        await(() -> blast.get() != null, "the small blast detonated");
        assertEquals(1.0f, blast.get().power(), "the radius row steers the base");
        // The default 3-power blast tears the ground three blocks out; the
        // radius-1 blast leaves the two-block-out column standing.
        EngineAwaits.awaitTicks(server.world(), 6, "engine progress (a 300 wall settle)");
        BlockPosition twoOut = new BlockPosition(3, 4, 0);
        java.util.concurrent.atomic.AtomicBoolean stood = new java.util.concurrent.atomic.AtomicBoolean();
        server.ticker().submit(() -> stood.set(
                !server.world().getBlock(twoOut)
                        .equals(net.zaminmc.torch.server.block.BuiltinBlocks.AIR)));
        EngineAwaits.awaitTicks(server.world(), 6, "engine progress (a 300 wall settle)");
        assertEquals(true, stood.get(),
                "the 1-power blast's sphere never reached the two-block-out column");
        sub.unsubscribe();
    }
}
