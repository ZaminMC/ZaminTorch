package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-EX13 — the blast's PLAYER wound walk reads the per-world entity list
 * (Explosion.damageEntities line 115: {@code this.world.getEntities(...)}
 * — the blast's OWN world's chunks alone; a body in another dimension is
 * structurally absent from the gather). Every other cross-dimension wound
 * walk in the engine already carries the filter — the mobs
 * ({@code mob.dimension() != dimension}), the items, the orbs, the
 * projectiles, the armor stands, the fireworks — and the
 * single-dimension families gate on their own dimension (the vehicles and
 * the decorations overworld-only, the crystals End-only).
 *
 * <p>THE PRE-FIX ENGINE'S PLAYER WALK ALONE iterated the GLOBAL session
 * list with no dimension gate: a blast at one dimension's coordinates
 * wounded a player standing at the SAME coordinates in ANOTHER dimension
 * — the wound, the knockback, and the packet's motion row all crossed
 * the world boundary.</p>
 *
 * <p>THE SEAT drives the REAL creeper detonation wire in the OVERWORLD
 * with two players at the SAME coordinates — one overworld, one NETHER:
 * the overworld body takes the blast, the nether body never feels it.</p>
 */
class BlastPlayerWoundDimensionGateTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "wounddim" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name, Position at, int dimension) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.setDimension(dimension);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    /**
     * THE CORE SEAT: the overworld creeper's blast wounds the OVERWORLD
     * body at the coordinates and never the NETHER body standing at the
     * SAME coordinates. The pre-fix walk wounded BOTH — the cross-dimension
     * wound, knockback, and motion row.
     */
    @Test
    void theBlastWoundsOnlyThePlayersInItsOwnDimension() throws Exception {
        // A floating dirt platform at y=100 (stone-free: nothing pops, and
        // the blast's crater is the dirt alone).
        BlockPosition platform = new BlockPosition(64, 100, 64);
        server.ticker().submit(() -> {
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    server.world().setBlock(new BlockPosition(64 + dx, 100, 64 + dz),
                            net.zaminmc.torch.server.block.BuiltinBlocks.DIRT);
                    for (int y = 101; y <= 105; y++) {
                        server.world().setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                server.world().airType());
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> net.zaminmc.torch.server.block.BuiltinBlocks.DIRT
                        .equals(server.world().getBlock(platform)),
                40, "the platform landed");

        // Two players at the SAME cell — one overworld, one nether.
        PlayerSession overworldBody = join("Grounded",
                new Position(65.5, 101.0, 64.5), 0);
        PlayerSession netherBody = join("Elsewhere",
                new Position(65.5, 101.0, 64.5), -1);
        EngineAwaits.awaitCondition(server.world(),
                () -> overworldBody.state() == net.zaminmc.torch.entity.PlayerState.PLAYING
                        && netherBody.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "both players reach PLAYING");

        // The creeper standing beside them, its swell armed.
        MobEntity creeper = server.mobs().spawnAt(MobType.CREEPER,
                new Position(64.5, 101.0, 64.5));
        assertNotNull(creeper, "the creeper spawned");
        server.ticker().submit(() -> {
            creeper.setCreeperIgnited(true);
            creeper.setCreeperFuseTime(5);
        });

        EngineAwaits.awaitCondition(server.world(),
                () -> server.world().getBlock(platform)
                        .equals(server.world().airType()),
                60, "the blast tore the platform under the pair");

        assertTrue(overworldBody.health() < 20.0f,
                "the overworld body took the blast (health "
                        + overworldBody.health() + ")");
        assertEquals(20.0f, netherBody.health(), 1e-6f,
                "the nether body at the SAME coordinates never felt the blast "
                        + "(health " + netherBody.health() + " — the pre-fix cross-dimension wound)");
    }
}
