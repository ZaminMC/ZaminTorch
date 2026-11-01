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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-013 DISCOVERY SEAT — the stuck arrow's portal
 * crossing (docs/vanilla-conformance-audit.md): an arrow ALREADY STUCK
 * in the portal frame's floor crosses when the portal lights up around
 * it — the box-scan collision law the engine's position-cell consult
 * cannot reproduce.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8), the full chain:
 * <ol>
 * <li>THE STICK PLACEMENT: {@code ArrowEntity.tick}'s block-hit arm
 * (lines 288-309) sets the velocity to the segment to the hit face
 * (lines 296-298), backs the position off 0.05 ALONG the approach
 * (lines 300-302), then advances by the full segment (lines 328-330) —
 * the final position is 0.05 OUTSIDE the hit face, in the cell the
 * arrow was flying through. An arrow sticking in the portal floor's top
 * face rests with its position INSIDE the portal cell above the
 * floor.</li>
 * <li>THE COLLISION: {@code Entity.tick}'s movement walk ends in
 * {@code checkBlockCollisions()} (line 627 → lines 667-688) — a full
 * BOX scan: every cell the body's shape overlaps receives
 * {@code onEntityCollision}; {@code PortalBlock.onEntityCollision}
 * (lines 158-162) arms {@code inPortal} for the stuck arrow (a
 * projectile never rides — the gate passes) and derives the entry
 * memory.</li>
 * <li>THE ARM: {@code ArrowEntity.tick} line 150 calls
 * {@code super.tick()} BEFORE the inGround branch — baseTick's portal
 * arm (lines 282-313) reads {@code inPortal} for STUCK arrows too, the
 * BASE threshold 0 (lines 354-355) crossing on the first armed tick.
 * A portal that LIGHTS UP around an already-stuck arrow crosses it on
 * the very next tick.</li>
 * </ol></p>
 *
 * <p>THE ENGINE'S SHAPE (the divergence, TWO stacked rows): the stick
 * placement is the post-step ENDPOINT — inside the solid cell, up to a
 * full step deep ({@code ProjectileManager.resolveCollisions}'s solid
 * arm calls {@code landInGround()} without touching the position), not
 * vanilla's face+0.05; and the crossing consult (PR #113's arm, the
 * same position-cell form the mob/item/orb/vehicle families ride) reads
 * the position cell ALONE, not the box overlap. The composition: a
 * stuck arrow's position cell is the OBSIDIAN floor cell — the portal
 * consult answers null FOREVER, and the stuck arrow never crosses, on
 * develop (no arm) AND on the #113 tree (the arm's consult misses).
 * This seat is RED on both; VT-013 owns the law. Do not re-pin it to
 * the engine's current answer.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalStuckArrowCrossingDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "stuckarrow" + Math.abs(new java.util.Random().nextInt(100000)),
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
     * The 4x5 X-plane frame with the interior LEFT AIR (the pre-lighting
     * shape): the arrow must stick in the floor BEFORE any portal cell
     * exists — the mid-fall crossing can never fire.
     */
    private static void buildEmptyFrame(EngineWorld world, int x, int y, int z) {
        for (int w = 0; w < 4; w++) {
            for (int h = -1; h < 4; h++) {
                boolean frameCell = w == 0 || w == 3 || h == -1 || h == 3;
                world.setBlock(new BlockPosition(x + w, y + h, z),
                        frameCell ? BuiltinBlocks.OBSIDIAN : world.airType());
            }
        }
    }

    /** Lights the interior cells — the flint-and-steel ignition's result. */
    private static void lightPortal(EngineWorld world, int x, int y, int z) {
        for (int w = 1; w <= 2; w++) {
            for (int h = 0; h <= 2; h++) {
                world.setBlock(new BlockPosition(x + w, y + h, z),
                        BuiltinBlocks.NETHER_PORTAL);
            }
        }
    }

    /**
     * VT-013: an arrow stuck in the portal frame's floor crosses when the
     * portal lights up around it — vanilla's box-scan law. The engine's
     * stick placement (the endpoint inside the floor cell) plus the
     * position-cell consult leave the stuck arrow stranded forever.
     */
    @Test
    void theStuckArrowCrossesWhenThePortalLightsAroundIt() throws Exception {
        EngineWorld overworld = server.world();
        EngineWorld nether = server.worldFor(-1);

        int overX = 40;
        int overZ = 40;
        int overY = 20;
        // The EMPTY frame first — the interior air, so the arrow's fall
        // through the frame's column never touches a portal cell.
        server.ticker().submit(() -> buildEmptyFrame(overworld, overX, overY, overZ));
        await(() -> BuiltinBlocks.OBSIDIAN.equals(overworld.getBlock(
                        new BlockPosition(overX + 1, overY - 1, overZ)))
                        && overworld.getBlock(new BlockPosition(overX + 1, overY, overZ))
                                .equals(overworld.airType()),
                "the empty obsidian frame stands with an air interior");

        // The PRE-BUILT nether frame AT THE SCALED DESTINATION (40/8 = 5)
        // — ready for the crossing the reference's law fires.
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
            for (int w = 0; w < 4; w++) {
                for (int h = -1; h < 4; h++) {
                    boolean frameCell = w == 0 || w == 3 || h == -1 || h == 3;
                    nether.setBlock(new BlockPosition(netherX + w, netherY + h, netherZ),
                            frameCell ? BuiltinBlocks.OBSIDIAN : BuiltinBlocks.NETHER_PORTAL);
                }
            }
        });
        await(() -> nether.getBlock(new BlockPosition(netherX + 1, netherY, netherZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the pre-built nether frame stands");

        // The body: an arrow shot steeply down INTO the frame's interior
        // from above and one block out — pitch 80 down, speed 0.8. The
        // fall clears the frame's top edge (the top row lives at the
        // plane's z column alone; the approach drifts into it a full
        // block late), descends through the AIR interior, and sticks in
        // the bottom obsidian row's top face (y = overY).
        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(overX + 1.5, overY + 4.5, overZ + 1.5),
                180.0, 80.0, 0.8, 0)));
        await(() -> launched.get() != null, "the arrow launched at the empty frame");
        ProjectileEntity arrow = launched.get();
        await(() -> arrow.inGround(), "the arrow sticks in the frame's floor");

        // VT-013's PLACEMENT EVIDENCE (observed, not asserted as law):
        // the engine's stuck position is the post-step endpoint — BELOW
        // the floor's face, its cell the OBSIDIAN floor row; the
        // reference's placement is the face+0.05 — INSIDE the interior
        // cell that is about to become a portal cell. The crossing
        // assert's failure message carries both readings.
        Position stuckAt = arrow.position();
        BlockPosition stuckCell = new BlockPosition(
                (int) Math.floor(stuckAt.x()),
                (int) Math.floor(stuckAt.y()),
                (int) Math.floor(stuckAt.z()));

        // LIGHT THE PORTAL around the stuck arrow — the flint-and-steel
        // ignition's result: the interior cells the arrow rests against
        // become portal cells.
        server.ticker().submit(() -> lightPortal(overworld, overX, overY, overZ));
        await(() -> BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(
                        new BlockPosition(overX + 1, overY, overZ))),
                "the portal lights around the stuck arrow");

        // THE CROSSING: the reference's law — the box scan arms inPortal
        // on the very next tick (the stuck arrow's shape overlaps the
        // new portal cell), and the threshold-0 arm crosses it. THE
        // ENGINE'S SHAPE: the position-cell consult reads the OBSIDIAN
        // floor cell (the stick placement's endpoint) — null forever —
        // and the stuck arrow never leaves the overworld, on develop and
        // on the #113 tree alike (VT-013's record, the handoff's named
        // stuck-arrow discriminator).
        boolean crossed = EngineAwaits.awaitConditionWithin(server.world(),
                () -> arrow.dimension() == -1, 100, "the stuck arrow's portal crossing");
        assertTrue(crossed,
                "VT-013: the arrow stuck in the portal frame's floor crosses when the "
                        + "portal lights around it (the reference's box-scan collision — "
                        + "checkBlockCollisions lines 667-688 — arms inPortal for the "
                        + "STUCK arrow whose shape overlaps the portal cell, and baseTick's "
                        + "threshold-0 arm crosses it on the next tick). The engine's "
                        + "answer: the stuck position " + stuckAt + " (cell " + stuckCell
                        + ", the obsidian floor row — the post-step-endpoint placement) "
                        + "reads null at the position-cell consult forever; the reference "
                        + "rests the arrow at the face+0.05 — inside the interior cell "
                        + "(" + (overX + 1) + ", " + overY + ", " + overZ + ") — whose "
                        + "overlap arms the crossing");
    }
}
