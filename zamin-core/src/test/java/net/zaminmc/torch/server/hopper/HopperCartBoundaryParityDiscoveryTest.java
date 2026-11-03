package net.zaminmc.torch.server.hopper;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.vehicle.MinecartEntity;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-1 — the Phase E boundary arms over the hopper cart's consults
 * (the strict-form rider's own class: every gather is the reference's
 * BOX law — Box.intersects lines 164-171, {@code !(a <= b)} on all six
 * faces, EXACTLY TOUCHING is never intersecting — over each body's OWN
 * box: the cart's 0.98x0.7 hull, the item's 0.25 box).
 *
 * <p>The pre-fix engine held three divergences: (1) the block hopper's
 * entity fallback (getInventoryAt lines 466-472) was a CENTER-in-cell
 * band — a cart whose 0.98 hull overlaps the query cell by a hair while
 * its center sits outside was NEVER a container; (2) the fallback
 * consulted the chest cart alone — the HOPPER cart (MinecartHopper
 * implements Hopper + Inventory) was invisible to the block hopper's
 * pull; (3) the cart's own suck arms were inclusive center-bands
 * (an exact touch at the grown hull's edge collected where the
 * reference's strict box law refuses).</p>
 */
class HopperCartBoundaryParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "hcb" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE DISCRIMINATOR (the pre-fix miss): the graze — the
    // cart's hull overlaps the query cell while its center sits outside
    // ------------------------------------------------------------------

    /**
     * getInventoryAt lines 466-472 gathers the Inventory entities
     * through the QUERY BOX [x±0.5, y±0.5, z±0.5] around the consult
     * point, never a center test: a chest cart resting on the stone
     * beside the hopper — its hull [x+0.81, x+1.79] overlapping the
     * cell above the hopper by 0.19 while its center x+1.3 sits 0.3
     * outside the cell — IS the container, and the block hopper drains
     * its cargo one item per 8-tick cadence. The pre-fix center-in-cell
     * band (|center - cellCenter| <= 0.5) never saw it: RED pre-fix.
     */
    @Test
    void theGrazeOverlapCartIsAContainerWhileItsCenterSitsOutside() throws Exception {
        int x = 10, z = 10, y = 40;
        // The hopper facing down (the cart arm above is the walk under
        // test) + the stone shelf BESIDE it: the cart rests on the
        // stone's top (y+1), its hull reaching back over the hopper.
        server.ticker().submit(() -> {
            EngineWorld world = server.world();
            world.setBlock(new BlockPosition(x, y, z), HopperBlocks.hopperOf(0, true));
            world.setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
            // The cart's center at x+1.3, resting on the shelf: the hull
            // [x+0.81, x+1.79] grazes the query cell [x, x+1] by 0.19.
            var cart = (net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity)
                    server.vehicles().spawnChestMinecart(
                            new Position(x + 1.3, y + 1.35, z + 0.5), 0);
            cart.chest().setSlot(0, ItemStack.of(BuiltinItems.DIAMOND, 5));
        });
        await(() -> hopperFirstSlotCount(x, y, z) == 1,
                "the hopper pulled the first diamond from the grazing cart");
        await(() -> hopperFirstSlotCount(x, y, z) == 5,
                "the whole cargo drained off the grazing cart");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the strictness guard: a hair OUTSIDE the query cell's
    // face is never a container (the straddle's negative arm)
    // ------------------------------------------------------------------

    /**
     * Box.intersects lines 164-171 refuses the exact touch on every
     * face: a cart whose hull's west edge sits a HAIR (1e-9) past the
     * query cell's east face is never an intersection — the block
     * hopper never pulls from it, five full cadences and beyond.
     */
    @Test
    void theCartAPastTheQueryCellsFaceIsNeverAContainer() throws Exception {
        int x = 30, z = 10, y = 40;
        // The hull's west edge at (x+1) + 1e-9: the straddle's outside
        // arm — a boundary-tight negative that no double rounding can
        // flip (the hair dwarfs the ulp by six orders).
        double halfHull = MinecartEntity.CART_WIDTH / 2.0;
        double centerX = (x + 1.0) + halfHull + 1e-9;
        server.ticker().submit(() -> {
            EngineWorld world = server.world();
            world.setBlock(new BlockPosition(x, y, z), HopperBlocks.hopperOf(0, true));
            world.setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
            var cart = (net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity)
                    server.vehicles().spawnChestMinecart(
                            new Position(centerX, y + 1.35, z + 0.5), 0);
            cart.chest().setSlot(0, ItemStack.of(BuiltinItems.DIAMOND, 5));
        });
        // The never-window: five full transfer cadences (8 ticks each).
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            assertEquals(0, hopperFirstSlotCount(x, y, z),
                    "the cart a hair past the query cell's face is never a "
                            + "container — the strict box law refuses the touch");
            Thread.sleep(50);
        }
    }

    // ------------------------------------------------------------------
    // Seat 3 — the straddle's positive control: the same cart a hair
    // INSIDE the face is the container
    // ------------------------------------------------------------------

    /**
     * The ARM D twin of seat 2: the same cart nudged a hair (1e-9)
     * INSIDE the query cell's east face — the hull overlaps the cell by
     * the hair — IS the container, the cargo drains whole. The pair
     * pins the boundary to a nanometer on each side.
     */
    @Test
    void theCartAHairInsideTheQueryCellsFaceIsTheContainer() throws Exception {
        int x = 50, z = 10, y = 40;
        double halfHull = MinecartEntity.CART_WIDTH / 2.0;
        double centerX = (x + 1.0) + halfHull - 1e-9;
        server.ticker().submit(() -> {
            EngineWorld world = server.world();
            world.setBlock(new BlockPosition(x, y, z), HopperBlocks.hopperOf(0, true));
            world.setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
            var cart = (net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity)
                    server.vehicles().spawnChestMinecart(
                            new Position(centerX, y + 1.35, z + 0.5), 0);
            cart.chest().setSlot(0, ItemStack.of(BuiltinItems.DIAMOND, 5));
        });
        await(() -> hopperFirstSlotCount(x, y, z) == 5,
                "the control: the same cart a hair inside the face drains whole");
    }

    // ------------------------------------------------------------------
    // Seat 4 — THE DISCRIMINATOR (the pre-fix miss): the HOPPER cart is
    // itself the container above the block hopper
    // ------------------------------------------------------------------

    /**
     * MinecartHopperEntity implements Hopper + Inventory: a HOPPER cart
     * standing on the block hopper IS the container the block hopper
     * drains (getInventoryAt's Inventory interface consult, line 468 —
     * the family is the interface, never the chest cart's concrete
     * class). The pre-fix fallback consulted the chest cart alone: RED
     * pre-fix.
     */
    @Test
    void theHopperCartItselfIsTheContainerAboveTheBlockHopper() throws Exception {
        int x = 70, z = 10, y = 40;
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(x, y, z),
                    HopperBlocks.hopperOf(0, true));
            // The hopper cart standing on the hopper (the existing
            // chest-cart seat's own spawn shape), its own five slots
            // carrying the cargo — the host routes the cart's own
            // transfer walk through the engine's real per-dimension
            // hopper family.
            var cart = (net.zaminmc.torch.server.entity.vehicle.HopperMinecartEntity)
                    server.vehicles().spawnHopperMinecart(
                            new Position(x + 0.5, y + 1.0, z + 0.5),
                            server.hoppersFor(0)::hopperCartTransfer, 0);
            cart.hopper().setItem(0, ItemStack.of(BuiltinItems.DIAMOND, 3));
        });
        await(() -> hopperFirstSlotCount(x, y, z) == 3,
                "the block hopper drained the HOPPER cart's own cargo (the "
                        + "Inventory family, never the chest cart's class alone)");
    }

    // ------------------------------------------------------------------
    // Seat 5 — the cart's own suck arm: the exact touch at the grown
    // hull's edge (the strictness guard) + the hair-inside control
    // ------------------------------------------------------------------

    /**
     * transferItems lines 124-128: the fallback gathers over
     * {@code this.getShape().grown(0.25, 0.0, 0.25)} — the cart's own
     * 0.98x0.7 box grown a quarter horizontally — the item's own 0.25
     * box intersecting it STRICTLY. An item resting on the floor beside
     * the cart, its box's east edge EXACTLY at the grown band's east
     * edge (the construct: the item center at {@code bandMax + 0.125},
     * where bandMax is the engine's own {@code (x + 0.5) + 0.74} — the
     * ±2^-3 round-trip within one binade is exact in doubles, so the
     * touch lands on the same bit the engine compares) is NEVER sucked.
     * The control — the same item a hair inside — is sucked whole by
     * the every-tick arm.
     */
    @Test
    void theOwnShapeArmRefusesTheExactTouchAtItsGrownEdge() throws Exception {
        int x = 90, z = 10, y = 40;
        // The cart standing on the stone floor; a second stone so the
        // boundary item's own box rests fully supported.
        server.ticker().submit(() -> {
            EngineWorld world = server.world();
            world.setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
            world.setBlock(new BlockPosition(x + 1, y - 1, z), BuiltinBlocks.STONE);
            // The cart at the cell's center, standing on the floor: box
            // [x+0.01, x+0.99] x [y, y+0.7] x [z+0.01, z+0.99], the
            // grown band [x-0.24, x+1.24] x [y, y+0.7] x [z-0.24, z+1.24].
            server.vehicles().spawnHopperMinecart(
                    new Position(x + 0.5, y + 0.35, z + 0.5),
                    server.hoppersFor(0)::hopperCartTransfer, 0);
        });
        // THE CONTROL FIRST (a hair inside — sucked away before the
        // boundary item spawns, so the merge system never pairs them):
        // the item center at bandMax + 0.125 - 1e-9, its box's east
        // edge a hair INSIDE the band.
        double bandMax = (x + 0.5) + 0.74; // the engine's own expression
        double controlX = bandMax + 0.125 - 1e-9;
        server.itemEntities().spawnRestored(
                new Position(controlX, y + 0.125, z + 0.5),
                ItemStack.of(BuiltinItems.DIAMOND, 2), 0);
        await(() -> itemCountAt(controlX, y + 0.125, z + 0.5) == 0,
                "the control: the item a hair inside the grown edge is sucked whole");

        // THE EXACT TOUCH: the item center at bandMax + 0.125 — its
        // box's WEST edge at exactly bandMax (the item outside to the
        // east) — never sucked, three seconds of every-tick attempts.
        double touchX = bandMax + 0.125;
        server.itemEntities().spawnRestored(
                new Position(touchX, y + 0.125, z + 0.5),
                ItemStack.of(BuiltinItems.DIAMOND, 2), 0);
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            assertTrue(itemCountAt(touchX, y + 0.125, z + 0.5) == 2,
                    "the item's box edge EXACTLY at the grown band's edge is "
                            + "never an intersection — the every-tick arm never sucks it");
            Thread.sleep(50);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private int hopperFirstSlotCount(int x, int y, int z) {
        AtomicReference<Integer> count = new AtomicReference<>();
        server.ticker().submit(() -> {
            var hopper = server.hoppersFor(0).peek(new BlockPosition(x, y, z));
            count.set(hopper == null ? -1
                    : hopper.snapshotSlots()[0].count());
        });
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && count.get() == null) {
            sleep(10);
        }
        return count.get() == null ? -1 : count.get();
    }

    /** The stack count held by the item entity at the given coordinates. */
    private int itemCountAt(double x, double y, double z) {
        AtomicReference<Integer> count = new AtomicReference<>();
        server.ticker().submit(() -> {
            int found = 0;
            for (var item : server.itemEntities().all()) {
                if (Math.abs(item.position().x() - x) < 0.01
                        && Math.abs(item.position().y() - y) < 0.01
                        && Math.abs(item.position().z() - z) < 0.01) {
                    found = item.stack().count();
                    break;
                }
            }
            count.set(found);
        });
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && count.get() == null) {
            sleep(10);
        }
        return count.get() == null ? 0 : count.get();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
