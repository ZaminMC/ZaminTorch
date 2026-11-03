package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.RailBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity;
import net.zaminmc.torch.server.entity.vehicle.CommandBlockMinecartEntity;
import net.zaminmc.torch.server.entity.vehicle.HopperMinecartEntity;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-8 — the detector rail's comparator read (PE-7's ledgered
 * find): the reference's {@code DetectorRailBlock.getAnalogSignal}
 * lines 133-147 — a POWERED rail reads its carts over the SAME
 * 0.2-inset box the probe walks ({@code getBoundsForMinecarts} lines
 * 155-158, each cart's own hull STRICT): the COMMAND-BLOCK cart's tier
 * first (the last run's success count verbatim), then the INVENTORY
 * carts ({@code EntityFilter.INVENTORY} lines 22-26 — the chest
 * minecart AND the hopper minecart, the first one's
 * {@code InventoryMenu.getAnalogSignal} fullness).
 *
 * <p>The pre-fix engine's analog reader never recognized the family —
 * a comparator beside a powered detector rail read nothing whole (the
 * rail's own strong power alone, never the cargo). All three arms RED
 * pre-fix.</p>
 */
class DetectorRailComparatorParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "detrailcmp" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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

    private int wirePower(int x, int y, int z) {
        AtomicReference<Integer> power = new AtomicReference<>();
        server.ticker().submit(() -> {
            net.zaminmc.torch.block.BlockType type =
                    server.world().getBlock(new BlockPosition(x, y, z));
            power.set(RedstoneBlocks.isWire(type) ? RedstoneBlocks.wirePower(type) : -1);
        });
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && power.get() == null) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return power.get() == null ? -1 : power.get();
    }

    /**
     * The rig: a stone bed carrying a detector rail at (x, y, z), the
     * comparator at (x+1, y, z) facing WEST (its input side toward the
     * rail), and the reading wire at (x+2, y, z).
     */
    private void placeRig(int x, int y, int z) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = 0; dx <= 2; dx++) {
                server.world().setBlock(new BlockPosition(x + dx, y - 1, z),
                        BuiltinBlocks.STONE);
            }
            server.world().setBlock(new BlockPosition(x, y, z),
                    RailBlocks.defaultState("detector_rail"));
            server.world().setBlock(new BlockPosition(x + 1, y, z),
                    RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_WEST, false, false));
            server.world().setBlock(new BlockPosition(x + 2, y, z),
                    RedstoneBlocks.wireOfPower(0));
        });
        await(() -> RedstoneBlocks.isComparator(server.world().getBlock(
                        new BlockPosition(x + 1, y, z)))
                        && wirePower(x + 2, y, z) == 0,
                "the rig placed (the empty rail keeps the comparator silent)");
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE CHEST CART'S FULLNESS reads through the comparator
    // ------------------------------------------------------------------

    /**
     * A chest minecart with thirteen full stacks on the powered rail:
     * the comparator reads the cart's {@code InventoryMenu.getAnalog
     * *Signal} fullness — floor(14 * 13/27) + 1 = 7 — exactly the chest
     * family's own arithmetic over the ROLLING container. Pre-fix RED:
     * the reader never recognized the family (the wire stayed 0).
     */
    @Test
    void theChestCartsFullnessReadsThroughTheComparator() throws Exception {
        int x = 10, z = 10, y = 40;
        placeRig(x, y, z);
        AtomicReference<ChestMinecartEntity> cart = new AtomicReference<>();
        server.ticker().submit(() -> {
            ChestMinecartEntity spawned = (ChestMinecartEntity)
                    server.vehicles().spawnChestMinecart(
                            new Position(x + 0.5, y + 0.35, z + 0.5), 0);
            for (int slot = 0; slot < 13; slot++) {
                spawned.chest().setSlot(slot, ItemStack.of(BuiltinItems.DIRT, 64));
            }
            cart.set(spawned);
        });
        await(() -> cart.get() != null, "the chest cart spawned with its cargo");
        await(() -> wirePower(x + 2, y, z) == 7,
                "the comparator reads the chest cart's fullness 7 "
                        + "(thirteen full stacks of the 27-slot rolling chest — "
                        + "the reference's own InventoryMenu arithmetic)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE COMMAND CART'S SUCCESS COUNT (the precedence tier)
    // ------------------------------------------------------------------

    /**
     * A command-block minecart on the powered rail: the comparator
     * reads the cart's last run's SUCCESS COUNT verbatim (lines
     * 136-140 — the CommandBlockMinecart tier precedes the INVENTORY
     * walk; the command cart is no Inventory, so without the tier the
     * read would fall to 0). Pre-fix RED: the wire stayed 0.
     */
    @Test
    void theCommandCartsSuccessCountReadsThroughTheComparator() throws Exception {
        int x = 30, z = 10, y = 40;
        placeRig(x, y, z);
        AtomicReference<CommandBlockMinecartEntity> cart = new AtomicReference<>();
        server.ticker().submit(() -> {
            CommandBlockMinecartEntity spawned = (CommandBlockMinecartEntity)
                    server.vehicles().spawnCommandBlockMinecart(
                            new Position(x + 0.5, y + 0.35, z + 0.5),
                            server::runCommandCart, 0);
            spawned.setSuccessCount(9); // the last run's count (the read's own source)
            cart.set(spawned);
        });
        await(() -> cart.get() != null, "the command cart spawned");
        await(() -> wirePower(x + 2, y, z) == 9,
                "the comparator reads the command cart's success count 9 "
                        + "(the CommandBlockMinecart tier — the count verbatim, "
                        + "never a fullness, never the 0 of a non-Inventory fall-through)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE HOPPER CART'S FULLNESS (the INVENTORY census's
    // other member)
    // ------------------------------------------------------------------

    /**
     * A hopper minecart with two full stacks on the powered rail: the
     * comparator reads the cart's five-slot fullness — floor(14 * 2/5)
     * + 1 = 6 — the hopper family's own arithmetic over the ROLLING
     * container ({@code EntityFilter.INVENTORY}'s other member).
     * Pre-fix RED: the wire stayed 0.
     */
    @Test
    void theHopperCartsFullnessReadsThroughTheComparator() throws Exception {
        int x = 50, z = 10, y = 40;
        placeRig(x, y, z);
        AtomicReference<HopperMinecartEntity> cart = new AtomicReference<>();
        server.ticker().submit(() -> {
            HopperMinecartEntity spawned = (HopperMinecartEntity)
                    server.vehicles().spawnHopperMinecart(
                            new Position(x + 0.5, y + 0.35, z + 0.5),
                            server.hoppersFor(0)::hopperCartTransfer, 0);
            spawned.hopper().setItem(0, ItemStack.of(BuiltinItems.DIRT, 64));
            spawned.hopper().setItem(1, ItemStack.of(BuiltinItems.DIRT, 64));
            cart.set(spawned);
        });
        await(() -> cart.get() != null, "the hopper cart spawned with its cargo");
        await(() -> wirePower(x + 2, y, z) == 6,
                "the comparator reads the hopper cart's fullness 6 "
                        + "(two full stacks of the 5-slot rolling hopper — the "
                        + "INVENTORY census's other member)");
        // The cargo stays put: no container below the rail, nothing drains.
        assertTrue(cart.get().hopper().snapshotSlots()[0].count() == 64,
                "the cargo rode the read whole (nothing drains through a rail)");
    }
}
