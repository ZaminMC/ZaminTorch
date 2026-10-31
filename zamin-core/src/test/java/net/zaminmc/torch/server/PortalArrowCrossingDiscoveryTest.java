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
 * vanilla-conformance-audit.md, the twenty-fifth pass's deferred seat,
 * authored alongside the arm per the queue's plan): the flying arrow's
 * portal crossing.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): the arrow body runs the
 * entity-level portal arm — {@code ArrowEntity.tick} line 150 calls
 * {@code super.tick()} (Entity.tick → baseTick, the portal arm lines
 * 282-313). An arrow whose box intersects portal cells arms
 * {@code inPortal} through the gated collision
 * ({@code PortalBlock.onEntityCollision} lines 158-162 — a projectile
 * never rides, so the gate passes); the BASE threshold 0 (lines 354-355)
 * crosses on the FIRST armed tick; the cooldown arms at the base 300
 * (lines 1457-1459); {@code Entity.changeDimension} (1695-1730) walks
 * the arrow across — the 8:1 reposition, the forcer's placement, the
 * body recreated in the destination world with its kind, its thrower,
 * and its stick clock whole.</p>
 *
 * <p>THE ENGINE'S SHAPE: {@code ProjectileManager}'s tick walk carried
 * zero portal consults (the census — the twenty-fourth pass's
 * independent verification); an arrow flying into a portal face passes
 * through (or sticks in the frame) without ever crossing. This seat is
 * EXPECTED RED on develop until the projectile family's arm rides the
 * {@code DimensionCrossing} seam. Do not re-pin it to the engine's
 * current answer; the finding record owns the mismatch.</p>
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
     * VT-006 (the arrow family): an arrow flying INTO the portal face
     * crosses — the reference's entity-level portal arm over
     * ArrowEntity.tick's super chain — and its arrival lands inside the
     * destination frame's band through the same forcer walk the player,
     * the mobs, the items, the orbs, and the vehicles ride.
     *
     * <p>THE FLIGHT-PATH CONSTRUCTION (the seat the twenty-fifth pass
     * deferred): the arrow launches 0.4 blocks OUTSIDE the frame's face
     * (the X-plane portal's face is perpendicular to Z), aimed straight
     * in (yaw 180, speed 1.0 — the walk's per-tick step lands the body's
     * position cell INSIDE the portal column at the first flight tick,
     * not skipping past it the way a 3-block bow shot would); the next
     * tick's arm consult reads the portal cell and the crossing fires.
     * The post-crossing flight rides the arrival's rotated motion along
     * the destination frame's row and sticks in its obsidian columns —
     * the stuck position lands inside the frame's band.</p>
     */
    @Test
    void theArrowFlyingIntoThePortalFaceCrosses() throws Exception {
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
            // The catcher walls (the seat's own construction): the
            // crossed arrow's rotated flight rides the frame's open
            // axis — plain stone columns 2 blocks past either face,
            // inside the cleared band, so the stuck position lands
            // within the frame's neighborhood whichever way the
            // quarter-turn points (the crossing itself is the seat's
            // law; the wall keeps the STUCK sample observable).
            for (int dx = -3; dx <= 6; dx++) {
                for (int dy = -2; dy <= 5; dy++) {
                    nether.setBlock(new BlockPosition(netherX + dx, netherY + dy, netherZ + 2),
                            BuiltinBlocks.STONE);
                    nether.setBlock(new BlockPosition(netherX + dx, netherY + dy, netherZ - 2),
                            BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> nether.getBlock(new BlockPosition(netherX + 1, netherY, netherZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the pre-built nether frame stands");

        // The body: an arrow launched just outside the frame's face,
        // aimed straight in. The launch's own gaussian tilt is bounded
        // (sigma 0.0075 at speed 1.0 — the walk stays inside the
        // interior's margins by whole orders of magnitude).
        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(overX + 1.5, overY + 1.5, overZ + 1.4),
                180.0, 0.0, 1.0, 0)));
        await(() -> launched.get() != null, "the arrow launched at the portal face");
        ProjectileEntity arrow = launched.get();
        assertEquals(0, arrow.dimension(), "the body is in the overworld at the launch");

        // THE CROSSING: the reference's law — the arrow crosses on the
        // FIRST armed tick after its flight enters the cells (the base
        // threshold 0). THE ENGINE'S SHAPE: no portal walk exists for the
        // projectile family — the arrow flies through (or sticks in the
        // frame) without ever crossing.
        boolean crossed = EngineAwaits.awaitConditionWithin(server.world(),
                () -> arrow.dimension() == -1, 100, "the arrow's portal crossing");
        assertTrue(crossed,
                "VT-006 (the arrow family): the arrow flying into the portal face "
                        + "crosses on the first armed tick (the reference's entity-level "
                        + "portal arm over ArrowEntity.tick's super chain — Entity "
                        + "lines 354-355's base threshold 0)");

        // The arrival: the forcer's placement lands the body inside the
        // pre-built nether frame's band; the post-crossing flight rides
        // the rotated motion along the frame's row and sticks in its
        // obsidian columns (the 1-block step against the 1-block column
        // — the walk's midpoint+endpoint sampling catches the face).
        await(() -> arrow.inGround(), "the crossed arrow settles in the nether frame");
        BlockPosition arrival = new BlockPosition(
                (int) Math.floor(arrow.position().x()),
                (int) Math.floor(arrow.position().y()),
                (int) Math.floor(arrow.position().z()));
        assertTrue(arrival.x() >= netherX - 1 && arrival.x() <= netherX + 4
                        && arrival.y() >= netherY - 1 && arrival.y() <= netherY + 3
                        && arrival.z() >= netherZ - 2 && arrival.z() <= netherZ + 2,
                "VT-006 (the arrow family): the arrival lands inside the pre-built "
                        + "nether frame's band (" + arrival + ", position "
                        + arrow.position() + ")");
        assertTrue(server.projectiles().all().stream().anyMatch(
                        p -> p.entityId() == arrow.entityId() && p.dimension() == -1),
                "VT-006 (the arrow family): the crossed body carries the nether's stamp");
    }
}
