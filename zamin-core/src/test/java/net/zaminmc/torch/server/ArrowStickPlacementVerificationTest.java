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
 * THE VERIFIER'S VT-014 SECOND-CONSTRUCTION SEATS — the stick
 * PLACEMENT's EXACT GEOMETRY (docs/vanilla-conformance-audit.md, the
 * thirty-fourth pass): the independent lane's confirmation of the
 * thirty-third pass's discovery seats, through a DIFFERENT
 * construction mechanism and a FINER pin.
 *
 * <p>THE INDEPENDENCE (this lane's own value beside the
 * thirty-third's ArrowStickPlacementDiscoveryTest): every body here
 * lands through {@code spawnRestored} — the anvil import's own
 * restored form, an exact position with an EXACT velocity, no launch
 * gaussian — so the flights are FULLY deterministic and the seats pin
 * the reference's rest to the EXACT face±0.05 (0.001 tolerance, the
 * float-cast slack) rather than the approach side of the face alone.
 * The two lanes' constructions agree on the law; the observed engine
 * rests agree with the thirty-third's records to their construction's
 * precision (their y=19.841/0.8-speed shot; this lane's
 * y=19.4079402396/-1.0-velocity descent — the rest is the endpoint,
 * its depth the step's leftover, both far inside the face).</p>
 *
 * <p>THE REFERENCE LAW (ArrowEntity.tick lines 288-330, re-walked for
 * this seat): on a block hit the ray's face position is the anchor —
 * {@code velocity = (float)(facePos - pos)} (the remaining step, lines
 * 296-298), {@code pos -= velocity / f5 * 0.05} (the 0.05 backoff along
 * the approach, lines 300-302), then the flight branch's tail ADVANCES
 * the position by the same velocity (lines 328-330). The composition
 * lands the arrow EXACTLY 0.05 OUTSIDE the crossed face — in the cell
 * the arrow was flying through — for an axis-aligned approach,
 * independent of the remaining step's length:
 * {@code (pos - 0.05*unit) + (face - pos) = face - 0.05*unit}.</p>
 *
 * <p>THE ENGINE'S ROW (ProjectileManager.tick + resolveCollisions, the
 * pass-1 walk): the position is integrated to the step's ENDPOINT
 * BEFORE any sampling (line {@code Position after = projectile.position()}
 * — the midpoint is a sampling point, not a landing), and the solid
 * arm's {@code landInGround()} (ProjectileEntity) sets the flag and
 * zeroes the velocity WITHOUT touching the position — the arrow rests
 * wherever the step ended, up to a full step INSIDE the block, or past
 * a one-block wall entirely (the thin-wall air-stick: the midpoint
 * sample lands inside the wall, the endpoint lands in the open air
 * beyond it, and the arrow hangs there for its whole 1200-tick
 * stick clock).</p>
 *
 * <p>SEAT CONSTRUCTIONS (both fully deterministic): (A) a vertical
 * descent onto a flat stone face at a known height — the reference
 * rests at {@code face + 0.05}; (B) a horizontal 3.0-per-tick flight
 * into a one-block-thick wall — the reference rests against the NEAR
 * face at {@code nearFace - 0.05}, the engine's endpoint sticks past
 * the FAR face in open air.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArrowStickPlacementVerificationTest {

    /** The reference's backoff: the rest sits 0.05 outside the crossed face. */
    private static final double FACE_BACKOFF = 0.05;
    /** Float-cast slack for the exact face+0.05 pin (the reference casts
     *  the remaining step to float; the composition error is ~1e-7). */
    private static final double EXACT_TOLERANCE = 0.001;

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
        EngineAwaits.awaitCondition(server.world(), condition, 120, description);
    }

    /** A 3x3 stone platform whose TOP FACE sits at {@code topFaceY}. */
    private static void buildPlatform(EngineWorld world, int cx, int cy, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlock(new BlockPosition(cx + dx, cy, cz + dz), BuiltinBlocks.STONE);
            }
        }
    }

    /**
     * SEAT A — the plain stuck position: an arrow falling straight down
     * onto a stone face at y=20 rests at y=20.05 in the reference
     * (0.05 OUTSIDE the face, in the air cell above the stone). The
     * engine's endpoint placement rests INSIDE the stone row — this
     * pass's predicted sample: y ~= 19.408, the cell the arrow's step
     * ended in (the pass-1 record's observed y=19.947 was a different
     * scene's face; the law is the same — the rest is the endpoint,
     * not the face).
     */
    @Test
    void theStuckArrowRestsJustOutsideTheHitFace() throws Exception {
        EngineWorld world = server.world();
        int cx = 40;
        int cz = 40;
        int stoneY = 19;      // the stone row's cell
        double faceY = 20.0;  // the face the arrow crosses

        server.ticker().submit(() -> buildPlatform(world, cx, stoneY, cz));
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(cx, stoneY, cz))),
                "the stone platform stands with its top face at y=" + faceY);

        // The exact body: straight down from five blocks above the face,
        // velocity exactly -1.0 — no launch gaussian (spawnRestored's
        // law), so the descent is purely vertical and deterministic.
        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> launched.set(server.projectiles().spawnRestored(
                ProjectileEntity.Kind.ARROW,
                new Position(cx + 0.5, faceY + 5.0, cz + 0.5),
                0.0, -1.0, 0.0)));
        await(() -> launched.get() != null, "the arrow spawned above the platform");
        ProjectileEntity arrow = launched.get();
        await(() -> arrow.inGround(), "the arrow sticks in the platform");

        Position stuckAt = arrow.position();
        int stuckCellY = (int) Math.floor(stuckAt.y());

        // THE PIN — the side of the face: the reference rests OUTSIDE
        // the block it hit, in the cell the arrow was flying through
        // (the air cell above the stone row).
        assertTrue(stuckAt.y() >= faceY,
                "VT-014: the stuck arrow must rest OUTSIDE the block it hit — at or "
                        + "above the crossed face (the reference's backoff law, "
                        + "ArrowEntity lines 296-302: the position is backed off 0.05 "
                        + "along the approach then advanced by the remaining step — "
                        + "face - 0.05*unit, the cell the arrow flew through). Observed: "
                        + "the arrow rests at y=" + stuckAt.y() + " (cell y=" + stuckCellY
                        + "), which is " + (faceY - stuckAt.y()) + " BELOW the face at "
                        + "y=" + faceY + " — inside the stone row. The engine's "
                        + "resolveCollisions sticks the arrow at the post-step ENDPOINT "
                        + "(landInGround never touches the position), up to a full step "
                        + "inside the block.");
        // THE PIN — the exact geometry: face + 0.05 for the vertical
        // descent (the composition is exact for an axis-aligned approach).
        assertEquals(faceY + FACE_BACKOFF, stuckAt.y(), EXACT_TOLERANCE,
                "VT-014: the stuck arrow's rest is the crossed face + the 0.05 "
                        + "backoff (the reference's exact composition: (pos - 0.05*unit) "
                        + "+ (face - pos) = face - 0.05*unit — for the vertical descent, "
                        + "y = " + (faceY + FACE_BACKOFF) + "). Observed: y=" + stuckAt.y());
    }

    /**
     * SEAT B — the thin-wall air-stick: a 3.0-per-tick horizontal arrow
     * meets a one-block-thick wall. The reference's ray hits the NEAR
     * face and rests against it (nearFace - 0.05, on the shooter's
     * side). The engine's midpoint sample lands inside the wall while
     * the step's ENDPOINT lands past the FAR face in open air — the
     * arrow sticks IN THE AIR, a full block past the wall, and hangs
     * there for its whole 1200-tick stick clock.
     */
    @Test
    void theThinWallRestsTheArrowAgainstTheNearFaceNotPastIt() throws Exception {
        EngineWorld world = server.world();
        int wallX = 60;        // the wall's single-thickness column
        int wallZ = 60;        // the flight's z lane (cell)
        double nearFaceX = 60.0;
        double farFaceX = 61.0;

        // The 3-tall, 3-wide, ONE-block-thick wall at x=60.
        server.ticker().submit(() -> {
            for (int y = 19; y <= 21; y++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.setBlock(new BlockPosition(wallX, y, wallZ + dz), BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(wallX, 20, wallZ)))
                        && BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(wallX, 21, wallZ))),
                "the one-block-thick wall stands");

        // The exact body: horizontal, exactly 3.0 per tick (a full-draw
        // arrow's step), one block short of the wall — the first step's
        // midpoint (x ~= 60.485) lands inside the wall while the
        // endpoint (x ~= 61.97) lands past the far face in open air.
        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> launched.set(server.projectiles().spawnRestored(
                ProjectileEntity.Kind.ARROW,
                new Position(nearFaceX - 1.0, 20.5, wallZ + 0.5),
                3.0, 0.0, 0.0)));
        await(() -> launched.get() != null, "the arrow spawned one block short of the wall");
        ProjectileEntity arrow = launched.get();
        await(() -> arrow.inGround(), "the arrow sticks at the wall");

        Position stuckAt = arrow.position();
        int stuckCellX = (int) Math.floor(stuckAt.x());

        // THE PIN — the side of the wall: the reference rests against
        // the NEAR face, on the SHOOTER's side — never past the wall.
        assertTrue(stuckAt.x() <= nearFaceX,
                "VT-014 (the thin-wall air-stick): the stuck arrow must rest against "
                        + "the wall's NEAR face — on the shooter's side (the reference's "
                        + "ray hits the first crossed face; the backoff lands the rest "
                        + "0.05 OUTSIDE it). Observed: the arrow rests at x=" + stuckAt.x()
                        + " (cell x=" + stuckCellX + "), which is "
                        + (stuckAt.x() - farFaceX) + " past the FAR face at x=" + farFaceX
                        + " — in the OPEN AIR on the wall's far side. The engine's "
                        + "midpoint sample landed inside the wall while the step's "
                        + "endpoint sailed past it, and landInGround() froze the arrow "
                        + "at that in-air endpoint.");
        // THE PIN — the exact geometry: nearFace - 0.05 for the
        // horizontal approach.
        assertEquals(nearFaceX - FACE_BACKOFF, stuckAt.x(), EXACT_TOLERANCE,
                "VT-014: the thin-wall stuck arrow's rest is the near face - the 0.05 "
                        + "backoff (the reference's exact composition for the horizontal "
                        + "approach: x = " + (nearFaceX - FACE_BACKOFF) + "). Observed: "
                        + "x=" + stuckAt.x());
    }
}
