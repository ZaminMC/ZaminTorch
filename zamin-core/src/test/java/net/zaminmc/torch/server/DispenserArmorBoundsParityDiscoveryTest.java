package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.armorstand.ArmorStand;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.dispenser.DispenserBlocks;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
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
 * Wave PE-7 — the dispenser's armor arm over the Phase E boundary arms:
 * the reference's own law (ArmorItem lines 26-53 — the facing cell's
 * FULL cube {@code Box(i, j, k, i+1, j+1, k+1)}, every body's OWN box
 * against it STRICT both sides, the LivingEntity census under
 * NOT_SPECTATOR + CanPickUpItemsFilter — EntityFilter line 52's explicit
 * {@code instanceof ArmorStandEntity || instanceof PlayerEntity} arm:
 * THE ARMOR STAND QUALIFIES).
 *
 * <p>The pre-fix consult grew the cell around each body's CENTER with
 * inclusive edges and a HARDCODED hull (the mob's 0.6x1.8 stand-in):
 * the stand was absent from the census whole (a dispenser never dressed
 * a stand), and a wide mob's edge-stand (its own 1.4 hull over the cell
 * while its center sits outside the 0.6 stand-in's reach) was never
 * equipped. Both RED pre-fix.</p>
 */
class DispenserArmorBoundsParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "disparmor" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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

    /**
     * The rig: a dispenser facing EAST (the target cell x+1) on a stone
     * floor, holding one diamond helmet at slot 0, its lever mount
     * beside (the stone above the floor carrying the lever — the
     * vanilla-valid F-SP1 mount).
     */
    private void placeDispenserRig(int x, int y, int z) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -1; dx <= 2; dx++) {
                server.world().setBlock(new BlockPosition(x + dx, y - 1, z),
                        BuiltinBlocks.STONE);
            }
            // facing 5 = EAST (the dispenser's own facing table)
            server.world().setBlock(new BlockPosition(x, y, z),
                    DispenserBlocks.dispenserOf(5, false));
            server.dispensers().getOrCreate(new BlockPosition(x, y, z))
                    .setItem(0, ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1));
            server.world().setBlock(new BlockPosition(x, y + 1, z), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(x, y + 2, z),
                    RedstoneBlocks.leverOf(5, false));
        });
        await(() -> DispenserBlocks.isFamily(
                        server.world().getBlock(new BlockPosition(x, y, z))),
                "the dispenser placed");
    }

    private void flipLever(int x, int y, int z) {
        server.ticker().submit(() -> server.redstone().useLever(
                new BlockPosition(x, y + 2, z)));
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE ARMOR STAND WEARS THE PIECE (the LivingEntity census)
    // ------------------------------------------------------------------

    /**
     * The reference's CanPickUpItemsFilter line 52 qualifies the armor
     * stand explicitly: a dispenser facing a cell with a stand dresses
     * it — the stand's own head row (setEquipment(i1 - 0), the stand no
     * PlayerEntity, l = 0) carrying the helmet, the stack consumed.
     * Pre-fix RED: the stand was absent from the engine's census whole.
     */
    @Test
    void theStandWearsTheDispensedHelmet() throws Exception {
        int x = 10, z = 10, y = 40;
        placeDispenserRig(x, y, z);
        server.ticker().submit(() -> server.armorStands().place(
                new BlockPosition(x + 1, y - 1, z), 0, 1, 0.0f,
                new java.util.Random(1)));
        AtomicReference<ArmorStand> stand = new AtomicReference<>();
        await(() -> {
            for (ArmorStand placed : server.armorStands().allStands()) {
                if ((int) Math.floor(placed.x()) == x + 1
                        && (int) Math.floor(placed.z()) == z) {
                    stand.set(placed);
                    return true;
                }
            }
            return false;
        }, "the stand landed in the target cell");

        flipLever(x, y, z);
        await(() -> !stand.get().equipment(4).isEmpty(),
                "the stand wears the dispensed helmet (the reference's own "
                        + "ArmorStandEntity qualify arm in CanPickUpItemsFilter)");
        assertTrue(stand.get().equipment(4).type().identifier().value()
                        .equals("diamond_helmet"),
                "the head row carries the diamond helmet");
        await(() -> server.dispensers().getOrCreate(new BlockPosition(x, y, z))
                        .snapshotSlots()[0].isEmpty(),
                "the dispensed stack consumed whole");
    }

    // ------------------------------------------------------------------
    // Seat 2 — THE WIDE MOB'S EDGE-STAND (the type's own box)
    // ------------------------------------------------------------------

    /**
     * The mob walk reads the TYPE'S own box: a spider (1.4 wide, 0.9
     * high) standing with its center 0.65 WEST of the target cell — its
     * own hull [center-0.7, center+0.7] still over the cell's west face
     * by 0.05 — wears the piece. Pre-fix RED: the hardcoded 0.6
     * half-width stand-in's reach [center-0.6, center+0.6] never touched
     * the cell.
     */
    @Test
    void theSpidersEdgeStandWearsThePiece() throws Exception {
        int x = 30, z = 10, y = 40;
        placeDispenserRig(x, y, z);
        AtomicReference<MobEntity> spider = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity spawned = server.mobs().spawnAt(MobType.SPIDER,
                    new Position(x + 0.35, y, z + 0.5), 0);
            if (spawned != null) {
                spawned.setNoAi(true); // the seat discipline: no wandering
                spawned.setCanPickUpLoot(true); // the CanPickUpItemsFilter's mob arm
                spider.set(spawned);
            }
        });
        await(() -> spider.get() != null, "the spider spawned at the cell's west face");

        flipLever(x, y, z);
        await(() -> spider.get().equipment(4) != null,
                "the spider wears the dispensed helmet");
        assertTrue(spider.get().equipment(4).type().identifier().value()
                        .equals("diamond_helmet"),
                "the head row carries the diamond helmet (the type's own "
                        + "1.4 hull over the cell while the 0.6 stand-in never reached)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE EXACT-TOUCH STRADDLE at the cell's west face
    // ------------------------------------------------------------------

    /**
     * The boundary pinned to a nanometer each side: a spider whose
     * hull's east face sits a hair (1e-9) PAST the cell's west face
     * never wears the piece (the default item drop instead — exactly
     * touching never intersects); the same spider a hair INSIDE wears
     * it. The pre-fix inclusive-edge consult equipped BOTH.
     */
    @Test
    void theExactTouchStraddleAtTheCellsWestFace() throws Exception {
        int x = 50, z = 10, y = 40;
        placeDispenserRig(x, y, z);
        double boundary = x + 1; // the target cell's west face
        AtomicReference<MobEntity> spider = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity spawned = server.mobs().spawnAt(MobType.SPIDER,
                    new Position(boundary - 0.7 - 1e-9, y, z + 0.5), 0);
            if (spawned != null) {
                spawned.setNoAi(true);
                spawned.setCanPickUpLoot(true);
                spider.set(spawned);
            }
        });
        await(() -> spider.get() != null, "the never-side spider spawned");

        flipLever(x, y, z);
        // The never-window: the helmet never lands on the spider — the
        // default drop instead (an item in the world beside).
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            assertFalse(spider.get().equipment(4) != null
                            && !spider.get().equipment(4).isEmpty(),
                    "the spider a hair past the cell's face never wears the "
                            + "piece (exactly touching never intersects)");
            Thread.sleep(100);
        }
        assertTrue(spider.get().equipment(4) == null
                        || spider.get().equipment(4).isEmpty(),
                "the never-side spider's head row stays empty");

        // The fires-side: the same spider a hair inside the cell.
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(x, y, z),
                    DispenserBlocks.dispenserOf(5, false));
            server.dispensers().getOrCreate(new BlockPosition(x, y, z))
                    .setItem(0, ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1));
        });
        AtomicReference<MobEntity> inside = new AtomicReference<>();
        server.ticker().submit(() -> {
            MobEntity spawned = server.mobs().spawnAt(MobType.SPIDER,
                    new Position(boundary - 0.7 + 1e-9, y, z + 0.5), 0);
            if (spawned != null) {
                spawned.setNoAi(true);
                spawned.setCanPickUpLoot(true);
                inside.set(spawned);
            }
        });
        await(() -> inside.get() != null, "the fires-side spider spawned");
        flipLever(x, y, z);
        await(() -> inside.get().equipment(4) != null
                        && !inside.get().equipment(4).isEmpty(),
                "the spider a hair inside the cell's face wears the piece");
    }
}
