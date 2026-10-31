package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-004 DISCOVERY SEAT (docs/vanilla-conformance-audit.md,
 * the twenty-second pass): the portal stand clock's re-fire law.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8 entity/Entity.java): {@code
 * onPortalCollision} (lines 1426-1455) consults the cooldown FIRST — the
 * cooldown RE-ARMS on every collision tick while cooling, and {@code
 * inPortal} is armed ONLY when the cooldown is expired; the stand walk
 * ({@code baseTick} lines 284-313) accumulates {@code portalTime++} only
 * while {@code inPortal}. A body standing inside portal cells therefore
 * NEVER re-crosses: every collision tick re-arms the cooldown (the
 * player's 10, PlayerEntity line 315) and the clock decays. The return
 * requires leaving the cells and re-entering after the cooldown expires.</p>
 *
 * <p>THE ENGINE'S CORRECTED SHAPE (PlayerSession.advancePortalClock, the
 * VT-004 correction): the per-tick order is the reference's own — the
 * cooldown decrement, then the collision consult (the re-arm on EVERY
 * touching tick while cooling; the stand armed only when expired), then
 * the consumption (the crossing on the armed stand alone; the decay
 * otherwise). The pre-fix engine gated the crossing on {@code portalTime}
 * alone with an entry-edge-only re-arm, so an in-cells arrival re-fired
 * the crossing every tick — the every-tick dimension ping-pong the
 * twenty-second pass recorded.</p>
 *
 * <p>THE SEAT: the nether frame at the crossing's scaled destination is
 * PRE-BUILT so the forcer's arrival lands deterministically inside its
 * cells; the body crosses, NEVER moves again, and the dimension is sampled
 * every 2 ticks for a 120-tick window (past every cooldown window). The
 * reference law: the dimension NEVER leaves -1 while the body stands in
 * the cells. This seat was RED on develop until VT-004's correction
 * landed; it is the regression guard for the re-fire law now. Do not
 * re-pin it away from the reference's law.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalRefireDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "refire" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) throws InterruptedException {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var accepted = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        EngineAwaits.awaitCondition(server.world(), condition, 600, description);
    }

    /** The 4x5 X-plane frame (the DimensionWalk test's own build shape). */
    private static void buildPortal(EngineWorld world, int x, int y, int z) {
        for (int w = 0; w < 4; w++) {
            for (int h = -1; h < 4; h++) {
                boolean frameCell = w == 0 || w == 3 || h == -1 || h == 3;
                world.setBlock(new BlockPosition(x + w, y + h, z),
                        frameCell ? BuiltinBlocks.OBSIDIAN : BuiltinBlocks.NETHER_PORTAL);
            }
        }
    }

    /**
     * VT-004: a body standing inside the nether arrival frame's portal
     * cells keeps its dimension — the reference's cooldown consult re-arms
     * on every collision tick, so the standing body never re-fires the
     * crossing, whatever the stand clock carried across the dimension
     * change.
     */
    @Test
    void theBodyStandingInTheNetherArrivalCellsNeverReCrosses() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        PlayerSession walker = join("PortalStander");

        // The overworld portal (the crossing's source): a 4x5 X-plane
        // frame at (40, 20, 40) — the DimensionWalk shape.
        int overX = 40;
        int overZ = 40;
        int overY = 20;
        server.ticker().submit(() -> buildPortal(server.world(), overX, overY, overZ));
        await(() -> server.world().getBlock(new BlockPosition(overX + 1, overY, overZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the overworld portal frame built");

        // The PRE-BUILT nether frame AT THE SCALED DESTINATION (40/8 = 5):
        // the forcer's destination search finds THIS frame (the nearest
        // portal to the scaled position), so the arrival lands
        // deterministically inside its cells. The cleared air box around
        // it keeps the frame standing free of the nether's terrain.
        int netherX = 5;
        int netherZ = 5;
        int netherY = 20;
        for (int cx = (netherX - 4) >> 4; cx <= (netherX + 4) >> 4; cx++) {
            for (int cz = (netherZ - 4) >> 4; cz <= (netherZ + 4) >> 4; cz++) {
                ChunkPosition at = new ChunkPosition(cx, cz);
                if (!nether.isChunkLoaded(at)) {
                    server.requestChunkLoad(nether.dimension(), at, chunk -> { });
                }
            }
        }
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.isChunkLoaded(new ChunkPosition(netherX >> 4, netherZ >> 4)),
                200, "the nether frame chunk loaded");
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 6; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = -2; dy <= 5; dy++) {
                        nether.setBlock(new BlockPosition(netherX + dx, netherY + dy, netherZ + dz),
                                nether.airType());
                    }
                }
            }
            buildPortal(nether, netherX, netherY, netherZ);
        });
        await(() -> nether.getBlock(new BlockPosition(netherX + 1, netherY, netherZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the pre-built nether frame stands");

        // The crossing: stand the body in the overworld portal until the
        // 80-tick stand clock fires. The body never moves again.
        server.teleportPlayer(walker, new Position(overX + 1.5, overY + 0.1, overZ + 0.5));
        await(() -> walker.dimension() == -1, "the body crossed to the nether");

        // The seat's precondition: the arrival landed inside the pre-built
        // frame's portal cells (the forcer found it — the deterministic
        // arrival).
        BlockPosition feet = new BlockPosition(
                (int) Math.floor(walker.position().x()),
                (int) Math.floor(walker.position().y()),
                (int) Math.floor(walker.position().z()));
        assertTrue(BuiltinBlocks.NETHER_PORTAL.equals(nether.getBlock(feet))
                        || BuiltinBlocks.NETHER_PORTAL_Z.equals(nether.getBlock(feet)),
                "the seat's precondition: the arrival stands inside a portal cell ("
                        + feet + ", got " + nether.getBlock(feet).identifier()
                        + ", position " + walker.position() + ")");
        assertEquals(-1, walker.dimension(), "the body is in the nether at the arrival");

        // THE STAND: 120 ticks sampled at 2-tick granularity — past every
        // cooldown window (the engine's 10-tick constant, the reference's
        // player override 10, and margin). THE REFERENCE LAW: the
        // collision re-arms the cooldown every tick while the body stands
        // in the cells, so the crossing NEVER re-fires — the dimension
        // holds -1 for the whole window (the corrected engine answers the
        // same law: the cooldown pins at 10 and the clock decays to 0).
        StringBuilder timeline = new StringBuilder();
        for (int sample = 0; sample < 60; sample++) {
            EngineAwaits.awaitTicks(server.world(), 2, "the stand window advances");
            timeline.append("t").append((sample + 1) * 2)
                    .append(":dim=").append(walker.dimension())
                    .append(",clock=").append(walker.portalTime())
                    .append(",cool=").append(walker.portalCooldown())
                    .append("; ");
            assertEquals(-1, walker.dimension(),
                    "VT-004: the standing body never re-crosses (the reference's "
                            + "onPortalCollision cooldown consult) — timeline: " + timeline);
        }
        // The reference's own end state: the clock decayed to 0 while the
        // cooldown holds (the not-inPortal arm never runs its crossing).
        assertTrue(walker.portalCooldown() > 0 || walker.portalTime() == 0,
                "VT-004: the reference's end state — the cooldown holds or the clock "
                        + "decayed (clock " + walker.portalTime() + ", cooldown "
                        + walker.portalCooldown() + ")");
    }
}
