package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE PARITY PIN for VT-014 — the stuck arrow's PLACEMENT (the
 * correction's own seat, GREEN on the fix; the discovery seats'
 * constructions, RED on the pre-fix tree by the recorded evidence:
 * the platform rest observed at y=19.841 INSIDE the hit cell, the
 * thin-wall rest at x=51.480 hanging past the wall's far face).
 *
 * <p>THE REFERENCE LAW (reference/1.8.8, ArrowEntity.tick lines
 * 289-330): the block-hit arm replaces the velocity with the vector
 * from the pre-step position to the hit face (296-298), moves the
 * position BACK 0.05 along it (300-302), and the flight tail STILL
 * advances by the replaced velocity (328-330) — the composition
 * rests the arrow EXACTLY 0.05 OUTSIDE the hit face, on the approach
 * side, in the cell the arrow flew through. The tracked cell
 * ({@code blockX/blockY/blockZ}, 290-292) is the hit block alone;
 * the body never enters it.</p>
 *
 * <p>THE CORRECTION: {@code ProjectileManager.resolveCollisions}'
 * ARROW solid arm computes the segment-to-sample's entry face against
 * the hit cell's collision boxes (partial shapes answer their OWN
 * faces) and lands the rest at face − 0.05·unit through
 * {@code ProjectileEntity.landInGround(Position)}.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArrowStickPlacementParityTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "arrowparity" + Math.abs(new java.util.Random().nextInt(100000)),
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

    private ProjectileEntity launchDown(int cx, int cz) throws InterruptedException {
        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(cx + 0.5, 24.5, cz + 0.5),
                180.0, 90.0, 0.8, 0)));
        await(() -> launched.get() != null, "the arrow launched straight down");
        ProjectileEntity arrow = launched.get();
        await(() -> arrow.inGround(), "the arrow sticks in the platform's top face");
        return arrow;
    }

    /**
     * THE FULL-CUBE SEAT: a 5x5 stone platform, top face y=20 — the rest
     * must sit within [face, face+0.05] measured along the approach (a
     * near-vertical shot answers within [20.0, 20.05]), its cell the AIR
     * cell the arrow flew through, and the stick must HOLD at that rest
     * (no drift, no re-flight).
     */
    @Test
    void theStuckArrowRestsOutsideTheFullCubeHitFace() throws Exception {
        EngineWorld overworld = server.world();
        int cx = 42;
        int cz = 42;
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    overworld.setBlock(new BlockPosition(cx + dx, 19, cz + dz),
                            BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> BuiltinBlocks.STONE.equals(overworld.getBlock(
                        new BlockPosition(cx, 19, cz))),
                "the stone platform stands with its top face at y=20");

        ProjectileEntity arrow = launchDown(cx, cz);
        Position rest = arrow.position();
        assertTrue(rest.y() >= 20.0 && rest.y() <= 20.05,
                "the rest sits within [face, face+0.05] along the approach (the "
                        + "reference's composed law — ArrowEntity 296-302 + 328-330); "
                        + "observed " + rest);
        assertEquals(20, (int) Math.floor(rest.y()),
                "the rest's cell is the AIR cell the arrow flew through (the tracked "
                        + "cell alone — the platform's top row — is the hit block)");
        Position held = rest;
        EngineAwaits.awaitTicks(server.world(), 30, "the stick holds");
        assertTrue(arrow.inGround() && arrow.position().y() == held.y()
                        && arrow.position().x() == held.x() && arrow.position().z() == held.z(),
                "the stick holds at the rest through the following ticks (no drift, "
                        + "no re-flight); observed " + arrow.position() + " against "
                        + held);
    }

    /**
     * THE THIN-WALL SEAT: a one-block-thick wall at x=50, a flat
     * speed-2.0 shot whose ~1.96-block second step's midpoint lands
     * inside the wall's single cell while the endpoint clears the far
     * face — the rest must sit ON THE APPROACH SIDE of the near face
     * (the reference's exact rest: 49.95), never hanging in the air
     * beyond the wall (the pre-fix engine's observed 51.480).
     */
    @Test
    void theThinWallShotRestsAtTheNearFace() throws Exception {
        EngineWorld overworld = server.world();
        server.ticker().submit(() -> {
            for (int y = 20; y <= 24; y++) {
                for (int z = 45; z <= 49; z++) {
                    overworld.setBlock(new BlockPosition(50, y, z),
                            BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> BuiltinBlocks.STONE.equals(overworld.getBlock(
                        new BlockPosition(50, 22, 47))),
                "the one-block wall stands at x=50");

        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(47.5, 22.5, 47.5),
                270.0, 0.0, 2.0, 0)));
        await(() -> launched.get() != null, "the arrow launched flat at the thin wall");
        ProjectileEntity arrow = launched.get();
        await(() -> arrow.inGround(), "the arrow sticks at the thin wall");

        Position rest = arrow.position();
        assertTrue(rest.x() > 49.9 && rest.x() < 50.0,
                "the rest sits on the APPROACH side of the near face, within 0.1 of "
                        + "it (the reference's exact rest: the face minus 0.05 along "
                        + "the approach = 49.95); observed " + rest);
        assertEquals(49, (int) Math.floor(rest.x()),
                "the rest's cell is the approach-side air cell before the wall");
    }

    /**
     * THE PARTIAL-SHAPE SEAT: a bottom-slab platform (the collision
     * box's top at y=19.5, the CELL's boundary at y=20) — the rest must
     * sit at the SHAPE's face (within [19.5, 19.55]), never at the
     * cell boundary (+0.05 would answer 20.05, half a block above the
     * slab's surface). This seat discriminates the box-view computation
     * from the unit-cell stand-in.
     */
    @Test
    void theStuckArrowRestsAtTheSlabShapeFaceNotTheCellBoundary() throws Exception {
        EngineWorld overworld = server.world();
        int cx = 42;
        int cz = 42;
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    overworld.setBlock(new BlockPosition(cx + dx, 19, cz + dz),
                            BuiltinBlocks.STONE_SLAB);
                }
            }
        });
        await(() -> BuiltinBlocks.STONE_SLAB.equals(overworld.getBlock(
                        new BlockPosition(cx, 19, cz))),
                "the bottom-slab platform stands with its shape top at y=19.5");

        ProjectileEntity arrow = launchDown(cx, cz);
        Position rest = arrow.position();
        assertTrue(rest.y() >= 19.5 && rest.y() <= 19.55,
                "the rest sits within [shape face, shape face+0.05] — the SLAB's "
                        + "half-height surface at 19.5, not the cell boundary at 20 "
                        + "(the box-view computation, the reference's own rayTrace "
                        + "against the collision shape); observed " + rest);
        assertTrue(arrow.inGround(), "the arrow is stuck at the slab");
    }
}
