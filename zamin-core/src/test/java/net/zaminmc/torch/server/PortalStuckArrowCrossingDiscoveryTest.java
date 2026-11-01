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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-013 CORRECTED SEAT — the stuck arrow and the
 * portal that lights around it (docs/vanilla-conformance-audit.md,
 * the thirty-first pass's REJECTION record): the reference's answer
 * is NO CROSSING, and this seat now PINS that law.
 *
 * <p>THE CORRECTION (the pass-3 challenge, recorded): this seat's
 * first form asserted the opposite vanilla answer — that a portal
 * lighting around an already-stuck arrow crosses it "on the very next
 * tick" through the box-scan collision. That reading was WRONG: the
 * box scan ({@code checkBlockCollisions}, Entity lines 667-688) is
 * called from {@code ArrowEntity.tick} line 381 — INSIDE THE FLIGHT
 * BRANCH ALONE. The {@code inGround} branch (lines 172-186) runs only
 * the stick clock and the tracked-block check; a STUCK arrow never
 * re-arms {@code inPortal}. (The mobs, the items, and the other
 * move()-based families DO scan every tick — their tick calls
 * {@code Entity.move()}, whose tail is the same scan. The arrow's
 * custom tick advances its position directly and scans only while
 * flying.) The reference's own stuck-arrow law is the TRACKED-BLOCK
 * UN-STICK at lines 173-186 — the engine's missing mechanic recorded
 * as VT-015 — and the stick placement's face+0.05 rests the flying
 * arrow's body inside the portal cell on the STICK TICK (the scan's
 * last flight-branch run), arming the crossing that fires on the very
 * next tick: an arrow shot into an ALREADY-LIT portal's floor crosses
 * ONE TICK AFTER STICKING. The lights-LATER construction arms nothing
 * — no crossing, in the reference or in the engine.</p>
 *
 * <p>THIS SEAT now pins the honest law: the lights-later stuck arrow
 * stays in the overworld — GREEN on develop AND on the #113 tree (the
 * engine's no-crossing matches the reference here, for different
 * reasons: no arm at all on develop; the arm's position-cell consult
 * reading the obsidian floor cell on #113). The seat also OBSERVES
 * the stick placement's geometry (the engine's endpoint-inside
 * placement vs the reference's face+0.05 — VT-014's record) in the
 * stuck-position sample.</p>
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
     * exists — no flight-branch scan can arm anything.
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
     * VT-013's corrected pin: a portal that lights up around an
     * ALREADY-STUCK arrow does not cross it — the reference's arrow
     * scans portal cells only while FLYING (ArrowEntity.tick line 381,
     * the flight branch), and the stuck branch's tracked cell (the
     * obsidian floor) never changes. The engine's answer matches: no
     * arm consults a stuck body's cells on develop, and the #113 arm's
     * position-cell consult reads the obsidian floor cell forever.
     */
    @Test
    void theStuckArrowStaysWhenThePortalLightsAroundIt() throws Exception {
        EngineWorld overworld = server.world();

        int overX = 40;
        int overZ = 40;
        int overY = 20;
        // The EMPTY frame first — the interior air, so the arrow's fall
        // through the frame's column never arms anything.
        server.ticker().submit(() -> buildEmptyFrame(overworld, overX, overY, overZ));
        await(() -> BuiltinBlocks.OBSIDIAN.equals(overworld.getBlock(
                        new BlockPosition(overX + 1, overY - 1, overZ)))
                        && overworld.getBlock(new BlockPosition(overX + 1, overY, overZ))
                                .equals(overworld.airType()),
                "the empty obsidian frame stands with an air interior");

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

        // VT-014's OBSERVED EVIDENCE (the stick placement's geometry,
        // recorded for the finding — no assert pins it): the engine's
        // stuck position is the post-step ENDPOINT — BELOW the floor's
        // face, its cell the OBSIDIAN floor row; the reference rests the
        // arrow at the face+0.05 — INSIDE the interior cell that the
        // lighting is about to turn into a portal cell. The sample rides
        // the failure messages of the VT-014/VT-015 seats to come.
        Position stuckAt = arrow.position();
        BlockPosition stuckCell = new BlockPosition(
                (int) Math.floor(stuckAt.x()),
                (int) Math.floor(stuckAt.y()),
                (int) Math.floor(stuckAt.z()));

        // LIGHT THE PORTAL around the stuck arrow — the flint-and-steel
        // ignition's result: the interior cells above the floor become
        // portal cells. The tracked cell (the floor) is untouched.
        server.ticker().submit(() -> lightPortal(overworld, overX, overY, overZ));
        await(() -> BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(
                        new BlockPosition(overX + 1, overY, overZ))),
                "the portal lights around the stuck arrow");

        // THE PIN: a hundred ticks of the lit portal around the stuck
        // arrow — the reference's answer is NO CROSSING (the flight
        // branch's scan alone arms the portal; the stuck branch runs
        // the stick clock and the tracked-block check only, and the
        // tracked floor cell never changed). The engine's answer
        // matches — on develop (no arm) and on the #113 tree (the
        // position-cell consult reads the floor cell forever) alike.
        EngineAwaits.awaitTicks(server.world(), 100,
                "the lit portal stands around the stuck arrow");
        assertFalse(arrow.dimension() == -1,
                "VT-013's corrected pin: the ALREADY-STUCK arrow does not cross when "
                        + "the portal lights around it (the reference's box scan rides "
                        + "ArrowEntity.tick's FLIGHT branch alone — line 381; the inGround "
                        + "branch at lines 172-186 runs the stick clock and the "
                        + "tracked-block check only, and the tracked floor cell never "
                        + "changed). The engine's answer matches for its own reasons — "
                        + "observed stuck position " + stuckAt + " (cell " + stuckCell
                        + ", the obsidian floor row — the endpoint placement VT-014 "
                        + "records; the reference would rest at the face+0.05, inside "
                        + "the interior cell (" + (overX + 1) + ", " + overY + ", "
                        + overZ + "))");
        assertTrue(arrow.inGround(),
                "the stuck arrow stays stuck through the lighting (the reference's "
                        + "tracked cell — the floor — is untouched by the ignition)");
    }
}
