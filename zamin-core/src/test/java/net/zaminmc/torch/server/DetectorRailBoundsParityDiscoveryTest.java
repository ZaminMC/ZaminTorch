package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.RailBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-7 — the detector rail's cart probe over the Phase E boundary
 * arms: the reference's own law (DetectorRailBlock.getBoundsForMinecarts
 * lines 155-158 — the 0.2-INSET box {@code [cell+0.2, cell+0.8] x [y,
 * y+0.8] x [cell+0.2, cell+0.8]}), each cart's OWN 0.98x0.7 hull against
 * it STRICT both sides, and the census MinecartEntity.class — EVERY cart
 * kind (the furnace, TNT, hopper and spawner carts included, the boat
 * excluded).
 *
 * <p>The pre-fix probe tested the cart's CENTER inside the FULL cell
 * with inclusive edges and walked only the plain and chest kinds: an
 * edge-grazing cart (its hull over the inset while its center sits
 * outside the cell) never powered the rail, and the hopper cart never
 * powered it at all. Both RED pre-fix.</p>
 */
class DetectorRailBoundsParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "detrail" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private boolean railPowered(int x, int y, int z) {
        AtomicReference<Boolean> powered = new AtomicReference<>();
        server.ticker().submit(() -> powered.set(RailBlocks.isPowered(
                server.world().getBlock(new BlockPosition(x, y, z)))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && powered.get() == null) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return Boolean.TRUE.equals(powered.get());
    }

    /** The rig: a stone rail bed with a detector rail and a wide stone shelf west. */
    private void placeRailRig(int x, int y, int z) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                server.world().setBlock(new BlockPosition(x + dx, y - 1, z),
                        BuiltinBlocks.STONE);
            }
            server.world().setBlock(new BlockPosition(x, y, z),
                    RailBlocks.defaultState("detector_rail"));
        });
        await(() -> server.world().getBlock(new BlockPosition(x, y, z))
                .identifier().value().startsWith("detector_rail"),
                "the detector rail placed");
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE KIND CENSUS: the hopper cart powers the rail
    // ------------------------------------------------------------------

    /**
     * The reference's census is {@code getMinecarts(world, pos,
     * MinecartEntity.class)} — the HOPPER cart is a MinecartEntity: a
     * hopper minecart standing on the rail powers it exactly like the
     * plain cart. Pre-fix RED: the probe walked only the plain and
     * chest kinds — the hopper cart never powered the rail.
     */
    @Test
    void theHopperCartPowersTheDetectorRail() throws Exception {
        int x = 10, z = 10, y = 40;
        placeRailRig(x, y, z);
        server.ticker().submit(() -> server.vehicles().spawnHopperMinecart(
                new Position(x + 0.5, y + 0.35, z + 0.5),
                server.hoppersFor(0)::hopperCartTransfer, 0));
        await(() -> railPowered(x, y, z),
                "the hopper cart powers the detector rail (the "
                        + "MinecartEntity class census)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE EDGE-GRAZE: the hull over the inset, the center
    // outside the cell
    // ------------------------------------------------------------------

    /**
     * A plain cart standing with its center 0.25 WEST of the rail's
     * cell — its own 0.98 hull [cell-0.74, cell+0.24] still over the
     * inset's west face [cell+0.2, cell+0.8] by 0.04 — powers the rail
     * (the own-box law). Pre-fix RED: the center test read the FULL
     * cell and refused the center outside it.
     */
    @Test
    void theEdgeGrazingCartPowersTheDetectorRail() throws Exception {
        int x = 20, z = 10, y = 40;
        placeRailRig(x, y, z);
        server.ticker().submit(() -> server.vehicles().spawnMinecart(
                new Position(x - 0.25, y + 0.35, z + 0.5), 0));
        await(() -> railPowered(x, y, z),
                "the edge-grazing cart powers the rail (its own hull over "
                        + "the inset while its center sits outside the cell)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE EXACT-TOUCH STRADDLE at the inset's west face
    // ------------------------------------------------------------------

    /**
     * The boundary pinned to a nanometer each side: a cart whose hull's
     * east face sits a hair (1e-9) PAST the inset's west face
     * ({@code cell+0.2}) never powers the rail over a long never-window;
     * the same cart a hair INSIDE powers it. The pre-fix center test
     * refused both (the center sits far west of the cell either way).
     */
    @Test
    void theExactTouchStraddleAtTheInsetsWestFace() throws Exception {
        int x = 30, z = 10, y = 40;
        placeRailRig(x, y, z);
        // The never-side: the hull's east face a hair past cell+0.2.
        double boundary = x + 0.2;
        server.ticker().submit(() -> server.vehicles().spawnMinecart(
                new Position(boundary - 0.49 - 1e-9, y + 0.35, z + 0.5), 0));
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            assertFalse(railPowered(x, y, z),
                    "the cart a hair past the inset's west face never powers "
                            + "the rail (exactly touching never intersects)");
            Thread.sleep(100);
        }
        // The fires-side: the same cart a hair inside the inset.
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(x, y, z),
                    RailBlocks.defaultState("detector_rail"));
        });
        await(() -> server.world().getBlock(new BlockPosition(x, y, z))
                .identifier().value().startsWith("detector_rail"),
                "the unpowered rail re-placed");
        server.ticker().submit(() -> server.vehicles().spawnMinecart(
                new Position(boundary - 0.49 + 1e-9, y + 0.35, z + 0.5), 0));
        await(() -> railPowered(x, y, z),
                "the cart a hair inside the inset's west face powers the rail");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE BOAT CONTROL: the class census's exclusion
    // ------------------------------------------------------------------

    /**
     * A boat is no MinecartEntity: a boat standing on the rail never
     * powers it (the class census's own exclusion — both the reference
     * and the engine refuse).
     */
    @Test
    void theBoatNeverPowersTheDetectorRail() throws Exception {
        int x = 40, z = 10, y = 40;
        placeRailRig(x, y, z);
        server.ticker().submit(() -> server.vehicles().spawnBoat(
                new Position(x + 0.5, y + 0.25, z + 0.5), 0));
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline) {
            assertFalse(railPowered(x, y, z),
                    "the boat never powers the detector rail");
            Thread.sleep(100);
        }
    }
}
