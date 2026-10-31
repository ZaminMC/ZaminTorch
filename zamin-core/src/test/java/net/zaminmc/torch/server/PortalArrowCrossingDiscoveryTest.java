package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-006 FAMILY SEAT — the ARROW family (docs/
 * vanilla-conformance-audit.md, the twenty-fifth pass's deferred seat):
 * the flying arrow's portal crossing, authored in the fix run exactly as
 * the record planned ("the flight-path construction needs the crossing
 * tick's determinism care").
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): the arrow body runs the
 * entity-level portal arm — {@code ArrowEntity.tick} line 150 calls
 * {@code super.tick()} (Entity.tick → baseTick, the portal arm lines
 * 282-313; ArrowEntity extends Entity DIRECTLY, line 28 — the chain is
 * one hop). A flying arrow whose box intersects portal cells arms
 * {@code inPortal} through the same gated collision
 * ({@code PortalBlock.onEntityCollision} lines 158-162 — a projectile
 * never rides, so the gate passes); the BASE threshold 0 (lines 354-355)
 * crosses on the FIRST armed tick; the cooldown arms at the base 300
 * (lines 1457-1459); {@code Entity.changeDimension} (1695-1730) walks the
 * body across mid-flight — the 8:1 reposition, the forcer's placement
 * (the velocity quarter-turned with it), the body recreated in the
 * destination world.</p>
 *
 * <p>THE DETERMINISM: the launch rides {@code launchVector} with
 * variation 0.0 — the gaussian spread multiplies by the variation, so
 * the trajectory is EXACT. The flight plan: a slow arrow (force 0.3)
 * launched flat two blocks from the frame's plane; the seventh tick's
 * portal arm consults the interior cell (41, 21, 40) — the crossing
 * fires mid-flight, the position and the velocity both carried across.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalArrowCrossingDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "arrowcross" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
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
     * VT-006 (the arrow family): a flying arrow whose cell is a portal
     * cell crosses on the first armed tick — ArrowEntity.tick's own
     * super.tick() chain — and lands inside the destination frame's
     * interior band with its flight carried across.
     */
    @Test
    void theFlyingArrowCrossesMidFlight() throws Exception {
        EngineWorld overworld = server.world();
        EngineWorld nether = server.worldFor(-1);

        int overX = 40;
        int overZ = 40;
        int overY = 20;
        server.ticker().submit(() -> buildPortal(overworld, overX, overY, overZ));
        await(() -> overworld.getBlock(new BlockPosition(overX + 1, overY, overZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the overworld portal frame built");

        // The PRE-BUILT nether frame AT THE SCALED DESTINATION (40/8 = 5)
        // so the forcer walk lands the body deterministically inside its
        // cells.
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

        // The body: a slow arrow launched flat two blocks from the frame's
        // plane, the trajectory EXACT (variation 0.0 — the gaussian spread
        // multiplies by it). The seventh tick's portal arm consults the
        // interior cell (41, 21, 40) and the crossing fires mid-flight.
        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> launched.set(server.projectiles().launchVector(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(overX + 1.5, overY + 2.5, overZ - 1.5),
                0.0, 0.0, 1.0,
                0.3, 0.0, 0)));
        await(() -> launched.get() != null, "the arrow launched toward the frame");
        ProjectileEntity arrow = launched.get();
        assertEquals(0, arrow.dimension(), "the body flies in the overworld at the launch");

        // The seat's precondition: the arrow reaches the frame's interior
        // cells in flight (the portal arm's consult window).
        await(() -> {
            BlockPosition cell = new BlockPosition(
                    (int) Math.floor(arrow.position().x()),
                    (int) Math.floor(arrow.position().y()),
                    (int) Math.floor(arrow.position().z()));
            return BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(cell))
                    || BuiltinBlocks.NETHER_PORTAL_Z.equals(overworld.getBlock(cell))
                    || arrow.dimension() == -1;
        }, "the arrow's flight enters the portal cells");

        // THE CROSSING: the reference's law — the arrow crosses on the
        // FIRST armed tick (the base threshold 0), mid-flight.
        boolean crossed = EngineAwaits.awaitConditionWithin(server.world(),
                () -> arrow.dimension() == -1, 100, "the arrow's portal crossing");
        assertTrue(crossed,
                "VT-006 (the arrow family): the flying arrow whose cell is a portal "
                        + "cell crosses on the first armed tick (the reference's "
                        + "entity-level portal arm over ArrowEntity.tick's super "
                        + "chain — Entity lines 354-355's base threshold 0)");

        // The arrival: the forcer's placement lands the body inside the
        // pre-built nether frame's neighborhood — the band absorbs the
        // arrow's own continued flight (the poll reads a tick or two past
        // the crossing; the slow launch keeps the drift under a block).
        BlockPosition arrival = new BlockPosition(
                (int) Math.floor(arrow.position().x()),
                (int) Math.floor(arrow.position().y()),
                (int) Math.floor(arrow.position().z()));
        assertTrue(arrival.x() >= netherX - 1 && arrival.x() <= netherX + 4
                        && arrival.y() >= netherY - 1 && arrival.y() <= netherY + 4
                        && arrival.z() >= netherZ - 2 && arrival.z() <= netherZ + 2,
                "VT-006 (the arrow family): the arrival lands inside the pre-built "
                        + "nether frame's neighborhood (" + arrival + ", position "
                        + arrow.position() + ")");
        assertTrue(server.projectiles().all().stream().anyMatch(
                        p -> p.entityId() == arrow.entityId() && p.dimension() == -1),
                "VT-006 (the arrow family): the crossed body carries the nether's stamp");
    }
}
