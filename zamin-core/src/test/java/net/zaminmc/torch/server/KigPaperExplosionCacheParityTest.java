package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE KO-B — THE EXPLOSION DENSITY CACHE (the port of KigPaper/Paper
 * 0050 "Optimize explosions"): the victim-box density ray fan
 * ({@code World.getBlockDensity}, ~45 rays per victim, each a block
 * walk) is memoized per (world, blast center, victim box) for ONE TICK
 * — the stacked-blast reuse the proven lineup arms for the classic TNT
 * pile. The engine ports the patch's own gating law: DEFAULT OFF (the
 * reuse across same-tick blasts reads the world as it stood before the
 * earlier blast's crater — Paper's accepted approximation; the engine's
 * default stays vanilla-exact), armed with
 * {@code -Dtorch.optimize-explosions=true}.
 *
 * <p>THE SEATS: (1) the pure-memoization proof — a single wrong-world
 * bed blast (the deterministic detonator: power 5.0 at the bed's own
 * cell, no random anywhere in the wound walk) answers the IDENTICAL
 * wound with the cache armed and with it off — two identically-built
 * sites, two identically-placed players, one flag flip between the
 * detonations; (2) the armed-path liveness — the same-position crystal
 * chain (the first blast's gather wounds the second crystal, the second
 * detonates at the SAME coordinates: the stacked shape the cache exists
 * for) still wounds with the flag on, the second detonation's density
 * consults reading the first's cache entries.</p>
 */
class KigPaperExplosionCacheParityTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private EngineWorld nether;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "koexbl" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        nether = server.worldFor(-1);
        server.ticker().submit(() -> server.gameRules().set("doMobSpawning", "false"));
        EngineAwaits.awaitCondition(server.world(),
                () -> !server.gameRules().getBoolean("doMobSpawning"),
                40, "the natural spawn cycle is off");
    }

    @AfterEach
    void stop() {
        System.clearProperty("torch.optimize-explosions");
        server.shutdown(null);
    }

    /**
     * SEAT 1 — the pure-memoization proof: within ONE detonation the
     * cache is a pure memo (every family walk consults its own bodies'
     * distinct boxes), so the armed flag must answer the IDENTICAL wound
     * the vanilla-exact walk does. Site A's bed (the flag OFF) wounds
     * its player; site B's bed — the SAME local geometry, built 56
     * blocks away, its player at the same offset — detonates with the
     * flag ARMED: the health deltas must match exactly.
     */
    @Test
    void theArmedDensityCacheAnswersTheSameWound() throws Exception {
        // Two identically-built sites, 56 blocks apart (each blast's
        // reach is power*2 + 2 = 12 — the first crater never touches the
        // second site's rays).
        layNetherSite(64, 64);
        layNetherSite(120, 64);
        PlayerSession plainVictim = join("PlainVictim",
                new Position(71.5, 101.0, 64.5), -1);
        PlayerSession cachedVictim = join("CachedVictim",
                new Position(127.5, 101.0, 64.5), -1);
        EngineAwaits.awaitCondition(server.world(),
                () -> plainVictim.state() == net.zaminmc.torch.entity.PlayerState.PLAYING
                        && cachedVictim.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "both players reach PLAYING");

        // Blast A — the flag OFF (the default): the vanilla-exact walk.
        server.useItemOnBlock(plainVictim, new BlockPosition(64, 101, 64), 1,
                java.util.Optional.empty(), windowId -> { });
        EngineAwaits.awaitCondition(server.world(),
                () -> plainVictim.health() < 20.0f,
                40, "the plain blast wounded its player");
        float plainDelta = 20.0f - plainVictim.health();
        assertTrue(plainDelta > 0.0f, "the plain blast's wound is real");

        // Blast B — the flag ARMED: the same wound through the cache.
        System.setProperty("torch.optimize-explosions", "true");
        server.useItemOnBlock(cachedVictim, new BlockPosition(120, 101, 64), 1,
                java.util.Optional.empty(), windowId -> { });
        EngineAwaits.awaitCondition(server.world(),
                () -> cachedVictim.health() < 20.0f,
                40, "the cached blast wounded its player");
        float cachedDelta = 20.0f - cachedVictim.health();

        assertEquals(plainDelta, cachedDelta, 1e-4f,
                "the armed cache answered the identical wound (plain "
                        + plainDelta + ", cached " + cachedDelta + ")");
    }

    /**
     * SEAT 2 — the armed-path liveness at the stacked shape: two
     * crystals at the SAME coordinates, the first's death blast chaining
     * the second (the same-position double detonation the cache exists
     * for — the second blast's density consults read the first's cache
     * entries). With the flag ARMED the chain still runs whole: both
     * blasts fire and the player standing at the site takes the wound.
     */
    @Test
    void theArmedCacheSurvivesTheSamePositionCrystalChain() throws Exception {
        System.setProperty("torch.optimize-explosions", "true");
        // The End platform (the crystals ride the END world's blast
        // routing): a dirt floor at y=44 under the site, the air cleared.
        EngineWorld end = server.worldFor(1);
        server.ticker().submit(() -> {
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    end.setBlock(new BlockPosition(13 + dx, 44, 10 + dz),
                            BuiltinBlocks.DIRT);
                    for (int y = 45; y <= 51; y++) {
                        end.setBlock(new BlockPosition(13 + dx, y, 10 + dz),
                                end.airType());
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.DIRT.equals(end.getBlock(new BlockPosition(13, 44, 10))),
                40, "the End platform landed");
        AtomicReference<Boolean> a = new AtomicReference<>();
        AtomicReference<Boolean> b = new AtomicReference<>();
        server.ticker().submit(() -> {
            a.set(server.enderCrystals().spawn(10.5, 45.0, 10.5, 0.0f));
            b.set(server.enderCrystals().spawn(10.5, 45.0, 10.5, 0.0f));
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> Boolean.TRUE.equals(a.get()) && Boolean.TRUE.equals(b.get()),
                40, "the same-position crystal pair spawned");
        // Nine blocks east: inside the 6.0-power injury radius (12), far
        // enough the wound lands without the kill.
        PlayerSession victim = join("ChainVictim",
                new Position(19.5, 45.0, 10.5), 1);
        EngineAwaits.awaitCondition(server.world(),
                () -> victim.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the player reaches PLAYING");

        java.util.List<EngineServer.ExplosionEvent> events =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        server.addExplosionListener(events::add);
        server.ticker().submit(() -> server.enderCrystals()
                .damageAt(10.5, 45.0, 10.5));
        EngineAwaits.awaitCondition(server.world(),
                () -> events.size() >= 2, 60, "the same-position chain detonated both");
        EngineAwaits.awaitCondition(server.world(),
                () -> victim.health() < 20.0f,
                40, "the armed chain wounded the player");
        assertNotNull(events.get(0), "the chain's first blast fired");
    }

    // ------------------------------------------------------------------ harness

    /**
     * Lays one nether site: a 15x15 dirt platform at y=100, the air
     * cleared above (101-107), and the bed pair at the site's origin —
     * the same shape the wrong-world bed harness lays, at an arbitrary
     * center.
     */
    private void layNetherSite(int originX, int originZ) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    nether.setBlock(new BlockPosition(originX + dx, 100, originZ + dz),
                            BuiltinBlocks.DIRT);
                    for (int y = 101; y <= 107; y++) {
                        nether.setBlock(new BlockPosition(originX + dx, y, originZ + dz),
                                nether.airType());
                    }
                }
            }
            nether.setBlock(new BlockPosition(originX, 101, originZ),
                    BuiltinBlocks.BED_FOOT_SOUTH);
            nether.setBlock(new BlockPosition(originX, 101, originZ + 1),
                    BuiltinBlocks.BED_HEAD_SOUTH);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> EngineServer.isBedHalf(nether.getBlock(
                        new BlockPosition(originX, 101, originZ))),
                        40, "the site's bed landed at " + originX + "/" + originZ);
    }

    private PlayerSession join(String name, Position at, int dimension) {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() {
                return true;
            }

            @Override public void kick(String reason) {
            }
        }, name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        session.setDimension(dimension);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }
}
