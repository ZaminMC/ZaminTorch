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
 * THE VERIFIER'S VT-014 SEATS — the stick PLACEMENT's two observables
 * (docs/vanilla-conformance-audit.md, the thirty-first pass's honest
 * residue, pass 2 of 3): where a stuck arrow RESTS.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8, ArrowEntity.tick lines
 * 289-330 — the block-hit arm and the flight tail that runs after it
 * without returning): on the hit tick the ray from the PRE-STEP
 * position to the endpoint has already found the face
 * ({@code hitresult.facePos}); the arm replaces the velocity with
 * {@code facePos - position} (the vector toward the face, lines
 * 296-298), moves the position BACK 0.05 along it (lines 300-302),
 * sets {@code inGround = true}, and then the FLIGHT TAIL STILL RUNS
 * (line 328: {@code x += velocityX} with the replaced velocity) — the
 * composition lands the arrow at exactly {@code facePos - 0.05 * unit}
 * — 0.05 BLOCKS OUTSIDE THE HIT FACE, ON THE APPROACH SIDE, IN THE
 * CELL THE ARROW WAS FLYING THROUGH. The tracked cell
 * ({@code blockX/blockY/blockZ}, lines 290-292) is the HIT BLOCK's
 * cell; the arrow's body never enters it.</p>
 *
 * <p>THE ENGINE'S SHAPE (ProjectileManager.resolveCollisions' solid
 * arm, line ~1195): the midpoint/endpoint sample lands inside the hit
 * block's cell and {@code landInGround()} sets the flag WITHOUT
 * TOUCHING THE POSITION — the arrow rests at the post-step ENDPOINT,
 * up to a full step INSIDE the block (the thirty-first pass's observed
 * sample: y=19.947 against the floor's face at y=20, the cell the
 * obsidian floor row). The thin-wall construction exposes the same
 * root's second observable: a step whose midpoint lands inside a
 * ONE-BLOCK wall but whose endpoint is PAST the wall sticks the arrow
 * IN THE AIR BEYOND THE WALL — the reference's ray hits the near face
 * and rests 0.05 before it.</p>
 *
 * <p>These seats are EXPECTED RED on develop until VT-014's correction
 * lands (the face+0.05 rest). Do not re-pin them to the engine's
 * current answer; the finding record owns the mismatch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArrowStickPlacementDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "arrowstick" + Math.abs(new java.util.Random().nextInt(100000)),
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
     * A 5x5 stone platform with its TOP FACE at y=20 (the cells at
     * y=19): the arrow shot straight down from four and a half blocks
     * above hits the face at a known height. The launch jitter
     * (gaussian 0.0075 * speed per axis) drifts the x/z landing well
     * under half a block — the platform's 5x5 span absorbs it, and the
     * straight-down pitch keeps the approach near-vertical (the
     * reference's rest band [face, face+0.05] measured along y).
     */
    private static void buildPlatform(EngineWorld world, int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.setBlock(new BlockPosition(x + dx, 19, z + dz),
                        BuiltinBlocks.STONE);
            }
        }
    }

    /**
     * VT-014's primary seat: the stuck arrow rests ON THE APPROACH SIDE
     * of the hit face — the reference's face+0.05 (ArrowEntity 296-302
     * + 328-330: the backoff then the replaced-velocity advance compose
     * to exactly 0.05 outside the face; the tracked cell is the floor
     * block, the body's cell the AIR above it). The engine's endpoint
     * placement rests INSIDE the floor's cell — below the face.
     */
    @Test
    void theStuckArrowRestsOutsideTheHitFace() throws Exception {
        EngineWorld overworld = server.world();
        int cx = 42;
        int cz = 42;
        server.ticker().submit(() -> buildPlatform(overworld, cx, cz));
        await(() -> BuiltinBlocks.STONE.equals(overworld.getBlock(
                        new BlockPosition(cx, 19, cz))),
                "the stone platform stands with its top face at y=20");

        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(cx + 0.5, 24.5, cz + 0.5),
                180.0, 90.0, 0.8, 0)));
        await(() -> launched.get() != null, "the arrow launched straight down at the platform");
        ProjectileEntity arrow = launched.get();
        await(() -> arrow.inGround(), "the arrow sticks in the platform's top face (y=20)");

        Position stuckAt = arrow.position();
        BlockPosition stuckCell = new BlockPosition(
                (int) Math.floor(stuckAt.x()),
                (int) Math.floor(stuckAt.y()),
                (int) Math.floor(stuckAt.z()));
        // THE PIN: the rest is on the APPROACH SIDE of the face — y >= 20.0,
        // the cell the arrow flew through (the reference's exact rest is
        // face+0.05 along the approach; a near-vertical shot answers
        // within [20.0, 20.05]). The engine's endpoint placement answers
        // BELOW the face — inside the floor's cell (y=19).
        assertTrue(stuckAt.y() >= 20.0,
                "VT-014: the stuck arrow must rest OUTSIDE the hit face — the reference's "
                        + "ArrowEntity (lines 296-302 + 328-330) composes the 0.05 backoff "
                        + "with the replaced-velocity advance to rest exactly 0.05 before "
                        + "the face, in the cell the arrow flew through (" + cx + ", 20, "
                        + cz + "); the tracked cell alone (" + cx + ", 19, " + cz
                        + ") is the hit block. Observed rest " + stuckAt + " (cell "
                        + stuckCell + ") — the post-step ENDPOINT placement INSIDE the "
                        + "hit block's cell, the VT-014 divergence");
    }

    /**
     * VT-014's second observable — the THIN-WALL AIR-STICK: a
     * one-block-thick wall, a horizontal shot whose per-tick step is
     * ~1.96 blocks. The step from x=49.48 to x=51.44 has its midpoint
     * inside the wall's single cell (x=50) while its endpoint is PAST
     * the wall's far face (x=51): the engine's midpoint consult sticks
     * the arrow AT THE ENDPOINT — hanging in the AIR beyond the wall.
     * The reference's ray (pre-step 49.48 -> endpoint 51.44) hits the
     * NEAR face at x=50.0 and rests the arrow at 49.95 — ON THE
     * APPROACH SIDE, in the air cell before the wall.
     */
    @Test
    void theThinWallShotRestsAtTheNearFaceNotInTheAirBeyond() throws Exception {
        EngineWorld overworld = server.world();
        // The wall: ONE block thick in x (the column x=50), five tall
        // (y=20..24), five deep (z=45..49) — the jitter bands cannot
        // escape it.
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
        // Yaw 270 flies +x (dx = -sin(270) = 1); pitch 0 keeps it flat;
        // speed 2.0 gives the ~1.96-block step whose midpoint lands in
        // the wall's cell and whose endpoint clears the far face.
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(47.5, 22.5, 47.5),
                270.0, 0.0, 2.0, 0)));
        await(() -> launched.get() != null, "the arrow launched flat at the thin wall");
        ProjectileEntity arrow = launched.get();
        await(() -> arrow.inGround(), "the arrow sticks at the thin wall");

        Position stuckAt = arrow.position();
        BlockPosition stuckCell = new BlockPosition(
                (int) Math.floor(stuckAt.x()),
                (int) Math.floor(stuckAt.y()),
                (int) Math.floor(stuckAt.z()));
        // THE PIN: the rest is on the APPROACH SIDE of the near face —
        // x < 50.0 (the reference's exact rest: 49.95, the face minus
        // 0.05 along the +x approach). The engine's endpoint placement
        // answers PAST the far face — hanging in the air (x >= 51.0).
        assertTrue(stuckAt.x() < 50.0,
                "VT-014 (the thin-wall observable): the arrow shot at a one-block wall "
                        + "must rest AT THE NEAR FACE — the reference's ray hits x=50.0 "
                        + "and the 0.05 backoff composes with the replaced-velocity "
                        + "advance (ArrowEntity 296-302 + 328-330) to rest at x=49.95, "
                        + "in the approach-side air cell (49, .., 47). Observed rest "
                        + stuckAt + " (cell " + stuckCell + ") — the post-step ENDPOINT "
                        + "placement hanging in the AIR BEYOND the wall, the same "
                        + "VT-014 root's second observable");
    }
}
