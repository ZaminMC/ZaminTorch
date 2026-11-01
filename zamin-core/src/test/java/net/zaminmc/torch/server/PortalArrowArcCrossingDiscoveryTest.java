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
 * THE VERIFIER'S INDEPENDENT FLIGHT-PATH SEAT — the arrow family's arc
 * construction (docs/vanilla-conformance-audit.md, the twenty-eighth
 * pass's owed seat): an arrow SHOT through the portal plane, crossing
 * the cells MID-FLIGHT, the arrival continuing the flight in the
 * nether.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): {@code ArrowEntity.tick} line
 * 150 calls {@code super.tick()} (Entity.tick → baseTick, the portal arm
 * lines 282-313) BEFORE the projectile's own walk — a FLYING arrow whose
 * box intersects portal cells arms {@code inPortal} through the gated
 * collision ({@code PortalBlock.onEntityCollision} lines 158-162); the
 * BASE threshold 0 (lines 354-355) crosses on the FIRST armed tick;
 * {@code Entity.changeDimension} (1695-1730) walks the arrow across —
 * the 8:1 reposition, the forcer's placement
 * ({@code PortalForcer.findNetherPortal} lines 149-152: the horizontal
 * motion rotated by the facing quarter-turn, the vertical carried whole
 * through the NBT copy), the body recreated in the destination world
 * with its kind, thrower, and motion whole — the flight CONTINUES in
 * the nether.</p>
 *
 * <p>THE INDEPENDENT CONSTRUCTION (beside PR #113's own face-shot seat):
 * a shallow arc from FOUR blocks out — pitch +10 down, speed 1.4 — the
 * flight crossing the plane on the third tick at the interior's mid
 * band, NOT the near face; the discriminator beyond the face shot is
 * the ARC itself (the crossing fires while the arrow is still descending
 * mid-flight, the vertical motion non-zero at the crossing — the NBT
 * carry the rotation never touches).</p>
 *
 * <p>THE ENGINE'S SHAPE: {@code ProjectileManager}'s tick walk carried
 * zero portal consults (the census). This seat is EXPECTED RED on
 * develop until the projectile family's arm rides the
 * {@code DimensionCrossing} seam. Do not re-pin it to the engine's
 * current answer; the finding record owns the mismatch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalArrowArcCrossingDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "arrowarc" + Math.abs(new java.util.Random().nextInt(100000)),
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
     * VT-006 (the arrow family): an arrow shot through the portal plane
     * in a shallow arc crosses MID-FLIGHT — the crossing firing while
     * the arrow still descends — and the arrival CONTINUES the flight in
     * the nether, sticking in the catchers around the destination frame.
     */
    @Test
    void theArrowsArcCrossesThePlaneMidFlight() throws Exception {
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
            for (int dx = -4; dx <= 7; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    for (int dy = -2; dy <= 5; dy++) {
                        nether.setBlock(new BlockPosition(netherX + dx, netherY + dy, netherZ + dz),
                                nether.airType());
                    }
                }
            }
            buildPortal(nether, netherX, netherY, netherZ);
            // The catcher set (this seat's own construction, wider than
            // the face-shot seat's): stone walls THREE past either open
            // face, a stone floor two under the frame, and a stone lid
            // two over it — the rotated arrival's continued flight ends
            // on one of them whichever way the quarter-turn points and
            // however long the descent runs, so the STUCK sample stays
            // observable inside the cleared band.
            for (int dx = -4; dx <= 7; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    nether.setBlock(new BlockPosition(netherX + dx, netherY - 3, netherZ + dz),
                            BuiltinBlocks.STONE);
                    nether.setBlock(new BlockPosition(netherX + dx, netherY + 4, netherZ + dz),
                            BuiltinBlocks.STONE);
                }
            }
            for (int dx = -4; dx <= 7; dx++) {
                for (int dy = -2; dy <= 3; dy++) {
                    nether.setBlock(new BlockPosition(netherX + dx, netherY + dy, netherZ - 3),
                            BuiltinBlocks.STONE);
                    nether.setBlock(new BlockPosition(netherX + dx, netherY + dy, netherZ + 3),
                            BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> nether.getBlock(new BlockPosition(netherX + 1, netherY, netherZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the pre-built nether frame stands");

        // The body: an arrow launched FOUR blocks out from the plane's
        // near face, pitch 10 down, speed 1.4 — the arc's third tick
        // lands the position cell inside the interior band (x 41.5,
        // y ~21.5, z ~40.4) while the arrow still descends at
        // ~0.2 blocks/tick: the crossing fires MID-FLIGHT with the
        // vertical motion the rotation never touches.
        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(overX + 1.5, overY + 2.2, overZ + 4.5),
                180.0, 10.0, 1.4, 0)));
        await(() -> launched.get() != null, "the arrow launched on its arc");
        ProjectileEntity arrow = launched.get();
        assertEquals(0, arrow.dimension(), "the body is in the overworld at the launch");

        // THE CROSSING: the reference's law — the crossing fires on the
        // first armed tick after the arc's flight enters the cells (the
        // base threshold 0), the arrow still descending mid-flight.
        // THE ENGINE'S SHAPE: no portal walk exists for the projectile
        // family — the arrow flies THROUGH the plane and lands in the
        // overworld terrain beyond it.
        boolean crossed = EngineAwaits.awaitConditionWithin(server.world(),
                () -> arrow.dimension() == -1, 100, "the arc's portal crossing");
        assertTrue(crossed,
                "VT-006 (the arrow family): the arrow shot through the plane in a "
                        + "shallow arc crosses MID-FLIGHT (the reference's entity-level "
                        + "portal arm over ArrowEntity.tick's super chain — Entity "
                        + "lines 354-355's base threshold 0; the crossing fires while "
                        + "the arrow still descends at ~0.2 blocks/tick)");

        // The arrival: the forcer's placement lands the body inside the
        // pre-built nether frame's band, and the flight CONTINUES — the
        // rotated horizontal motion carrying the arrow out of the frame
        // until it sticks on one of the catchers.
        BlockPosition arrival = new BlockPosition(
                (int) Math.floor(arrow.position().x()),
                (int) Math.floor(arrow.position().y()),
                (int) Math.floor(arrow.position().z()));
        assertTrue(arrival.x() >= netherX - 1 && arrival.x() <= netherX + 4
                        && arrival.y() >= netherY - 1 && arrival.y() <= netherY + 3
                        && arrival.z() >= netherZ - 3 && arrival.z() <= netherZ + 3,
                "VT-006 (the arrow family): the arc's arrival lands inside the "
                        + "pre-built nether frame's band (" + arrival + ", position "
                        + arrow.position() + ")");
        assertTrue(server.projectiles().all().stream().anyMatch(
                        p -> p.entityId() == arrow.entityId() && p.dimension() == -1),
                "VT-006 (the arrow family): the crossed body carries the nether's stamp");
        await(() -> arrow.inGround(),
                "the crossed arrow's flight continues in the nether until it sticks");
        assertTrue(arrow.inGround() && arrow.dimension() == -1,
                "VT-006 (the arrow family): the arrival's continued flight ends stuck "
                        + "in the nether (the reference's NBT carry — the recreated "
                        + "body keeps its motion and its kind)");
    }
}
