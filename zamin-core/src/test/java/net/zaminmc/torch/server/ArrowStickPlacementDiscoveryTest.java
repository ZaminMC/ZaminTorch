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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-014 SEAT — the stuck arrow's RESTING POSITION
 * (docs/vanilla-conformance-audit.md, the thirty-first pass's honest
 * residue after VT-013's rejection; this is the finding's PASS 2, the
 * behavioral construction).
 *
 * <p>THE REFERENCE LAW (ArrowEntity.java, the block-stick arm): when the
 * flight ray hits a face, the arrow rests 0.05 OUTSIDE that face, in the
 * cell it was flying through — the velocity becomes the vector to the
 * hit face ({@code hitresult.facePos - pos}, lines 296-298), the position
 * backs off 0.05 along the normalized flight line (lines 300-302),
 * {@code inGround = true} (line 304), and the tick's closing advance
 * (lines 327-329) then carries the position to
 * {@code facePos - 0.05 * unit(flight direction)} — the face's own cell
 * NEVER holds the resting arrow.</p>
 *
 * <p>THE ENGINE'S LAW (ProjectileManager.resolveCollisions, the solid
 * arm): the midpoint/endpoint solidity samples call
 * {@code projectile.landInGround()} WITHOUT touching the position — the
 * arrow rests at the post-step ENDPOINT, up to a full step INSIDE the
 * block, and for a midpoint stick the endpoint can be past a thin wall
 * entirely — in the AIR.</p>
 *
 * <p>SEAT ONE (the floor shot): a stone platform with a known top face;
 * a steeply-falling arrow must rest AT OR ABOVE the face, its cell the
 * AIR row — the thirty-first pass's observed engine sample was
 * {@code y=19.947} against a face at {@code y=20}, the platform's own
 * row. SEAT TWO (the thin wall): a one-block wall crossed by a single
 * step whose midpoint is inside the wall and whose endpoint is past it —
 * the reference's ray stops at the NEAR face and rests 0.05 in front of
 * it; the engine sticks at the endpoint, floating in the air a full
 * block past the wall.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArrowStickPlacementDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "stickplace" + Math.abs(new java.util.Random().nextInt(100000)),
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

    /**
     * SEAT ONE — the floor shot's resting geometry. A 5x5 stone platform
     * at cell row y=20 (the flat world's terrain ends at y=4, so the
     * platform stands alone in the air); the top face is y=21. An arrow
     * falls steeply onto it (pitch 80, speed 0.8 — the thirty-first
     * pass's own construction). The reference rests it at
     * 21 + 0.05 * sin(80°) ≈ 21.049 — ABOVE the face, in the AIR row
     * (cell 21). The engine rests it at the post-step endpoint INSIDE
     * the platform row.
     */
    @Test
    void theStuckArrowRestsOutsideTheHitFace() throws Exception {
        EngineWorld world = server.world();
        int platformY = 20;
        double faceY = platformY + 1.0; // the platform row's top face

        server.ticker().submit(() -> {
            for (int x = 38; x <= 42; x++) {
                for (int z = 38; z <= 42; z++) {
                    world.setBlock(new BlockPosition(x, platformY, z), BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(40, platformY, 40))),
                "the stone platform stands");

        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(40.5, 25.5, 40.5),
                180.0, 80.0, 0.8, 0)));
        await(() -> launched.get() != null, "the arrow launched above the platform");
        ProjectileEntity arrow = launched.get();
        await(arrow::inGround, "the arrow sticks in the platform");

        Position stuckAt = arrow.position();
        int stuckCellY = (int) Math.floor(stuckAt.y());

        // THE PIN (the reference's answer): the resting position is AT OR
        // ABOVE the hit face — 0.05 outside it along the flight line, the
        // arrow's cell the AIR row above the platform, never the
        // platform's own row.
        assertTrue(stuckAt.y() >= faceY,
                "VT-014 seat one: the stuck arrow must rest AT OR OUTSIDE the hit face "
                        + "(the reference's ArrowEntity lines 296-304 + the closing advance at "
                        + "327-329 rest it at facePos - 0.05*unit(flight), the cell it flew "
                        + "through). Observed engine rest: " + stuckAt + " — BELOW the face at "
                        + faceY + ", the post-step ENDPOINT inside the platform row (cell y="
                        + stuckCellY + " against the platform row y=" + platformY
                        + "). The engine's resolveCollisions calls landInGround() without "
                        + "touching the position.");
        assertTrue(stuckCellY == platformY + 1,
                "VT-014 seat one: the stuck arrow's cell must be the AIR row above the "
                        + "platform (the reference rests the body outside the face). Observed "
                        + "cell y=" + stuckCellY + " (rest " + stuckAt + ")");
        assertTrue(stuckAt.y() < faceY + 0.2,
                "VT-014 seat one (sanity bound): the reference's rest is 0.05 outside the "
                        + "face, not a float above it. Observed rest: " + stuckAt);
    }

    /**
     * SEAT TWO — the thin-wall air-stick. A wall one block thick at cell
     * z=40 (3 wide, 3 tall, cells y=19..21 — the band [19, 22)); the near
     * face is z=40, the far face z=41. The arrow flies straight at it
     * (yaw 0, pitch 0, speed 2.0) from z=39.5: the first step's midpoint
     * lands inside the wall (≈ z 40.49) and the endpoint past it
     * (≈ z 41.48). The engine's midpoint consult therefore fires
     * landInGround() at the ENDPOINT — the arrow hangs in the AIR a full
     * block past the wall. The reference's ray stops at the NEAR face
     * (facePos z=40) and rests the arrow at z ≈ 39.95, in the cell
     * BEFORE the wall.
     */
    @Test
    void theThinWallSticksAtTheNearFaceNotPastIt() throws Exception {
        EngineWorld world = server.world();
        int wallZ = 40; // the wall's single cell row; near face at z=40

        server.ticker().submit(() -> {
            for (int x = 39; x <= 41; x++) {
                for (int y = 19; y <= 21; y++) {
                    world.setBlock(new BlockPosition(x, y, wallZ), BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(40, 20, wallZ))),
                "the one-block wall stands");

        // The launch consults no cell before the first step (the origin
        // cell is air), and the step's geometry puts the midpoint INSIDE
        // the wall and the endpoint PAST it whatever the launch gaussian
        // does (the 3-sigma drift is ~0.045 against 0.49+ margins).
        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(40.5, 20.5, wallZ - 0.5),
                0.0, 0.0, 2.0, 0)));
        await(() -> launched.get() != null, "the arrow launched at the wall");
        ProjectileEntity arrow = launched.get();
        await(arrow::inGround, "the arrow sticks at the wall");

        Position stuckAt = arrow.position();
        int stuckCellZ = (int) Math.floor(stuckAt.z());

        // THE PIN (the reference's answer): the rest is BEFORE the near
        // face — the ray stops at the first face it crosses, and the
        // 0.05 backoff keeps the body in the approach cell.
        assertTrue(stuckAt.z() < (double) wallZ,
                "VT-014 seat two: an arrow that crosses a one-block wall mid-step must "
                        + "rest at the NEAR face (the reference's ray hits the first face at "
                        + "z=" + wallZ + " and rests 0.05 in front of it, ArrowEntity lines "
                        + "296-304). Observed engine rest: " + stuckAt + " — PAST the far "
                        + "face at z=" + (wallZ + 1) + ", hanging in the AIR (cell z="
                        + stuckCellZ + "): the midpoint solidity consult fired landInGround() "
                        + "while the position already stood at the post-step endpoint beyond "
                        + "the wall.");
        assertTrue(stuckCellZ == wallZ - 1,
                "VT-014 seat two: the stuck arrow's cell must be the approach cell before "
                        + "the wall. Observed cell z=" + stuckCellZ + " (rest " + stuckAt + ")");
    }
}
