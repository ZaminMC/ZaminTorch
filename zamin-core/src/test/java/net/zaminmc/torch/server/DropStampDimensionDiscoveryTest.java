package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity;
import net.zaminmc.torch.server.entity.vehicle.VehicleEntity;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-6 — the death-drop census's dimension stamps (the
 * dimension-ownership ledger's remaining row: the redstone world's
 * remaining consults — the wire's cross-dimension consults — plus the
 * same class the census sweep found): every break/death drop lands in
 * the ACTING world, never the dimensionless overload's overworld 0.
 *
 * <p>The reference's own law at every site: {@code
 * Block.dropItem(this.world, ...)} (the wire pop, the piston's to-break
 * walk, the jukebox disc, the beacon payment, the rail pop), {@code
 * InventoryUtils.dropItems(this.world, this, this)} (the container
 * spills), and {@code Entity.dropItem} over the dying body's own world
 * (the vehicle death drops). The pre-fix engine fed the dimensionless
 * {@code spawnDropAtBlock(position, stack, delay)} overload at every
 * one of these sites — a nether wire losing its floor popped its
 * redstone drop into the OVERWORLD at the same coordinates (an item
 * that falls through the nether's terrain, invisible to nether
 * players, picked up by an overworld bystander standing on the
 * overworld's cell). Both arms RED pre-fix.</p>
 *
 * <p>The second arm also pins the vehicle family's own drop FORMS
 * (Wave PE-6's companion laws): the plain drops at the body's EXACT
 * feet anchor ({@code Entity.dropItem(stack, 0.0F)} — never the
 * pre-fix +0.3 cell-inset band) and the contents riding
 * {@code InventoryUtils.dropItem}'s spill form (the [0.1, 0.9] band,
 * the 10-30 chunk splits, the NO pickup delay).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DropStampDimensionDiscoveryTest {

    private static final int PROBE_X = 100;
    private static final int PROBE_Y = 40;
    private static final int PROBE_Z = 100;

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "dropstamp" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private static void awaitServer(EngineServer server, BooleanSupplier condition,
                                    String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> ran = new AtomicReference<>();
            server.ticker().submit(() -> ran.set(condition.getAsBoolean()));
            long inner = System.currentTimeMillis() + 200;
            while (System.currentTimeMillis() < inner && ran.get() == null) {
                Thread.sleep(5);
            }
            if (Boolean.TRUE.equals(ran.get())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    /** Loads the nether probe chunks (the dimension's terrain gate). */
    private void loadNetherProbe() throws InterruptedException {
        EngineWorld nether = server.worldFor(-1);
        for (int cx = (PROBE_X - 8) >> 4; cx <= (PROBE_X + 8) >> 4; cx++) {
            for (int cz = (PROBE_Z - 8) >> 4; cz <= (PROBE_Z + 8) >> 4; cz++) {
                ChunkPosition at = new ChunkPosition(cx, cz);
                if (!nether.isChunkLoaded(at)) {
                    server.requestChunkLoad(nether.dimension(), at, chunk -> { });
                }
            }
        }
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.isChunkLoaded(new ChunkPosition(PROBE_X >> 4, PROBE_Z >> 4)),
                200, "the nether probe chunk loaded");
    }

    // ------------------------------------------------------------------
    // Arm 1 — THE NETHER WIRE POP (the architecture row's own find)
    // ------------------------------------------------------------------

    /**
     * A nether wire losing its floor pops its redstone drop into the
     * NETHER (RedstoneWireBlock lines 235-244 over {@code
     * Block.dropItem(this.world, ...)}): the drop carries the nether
     * stamp, and the overworld never sees a redstone item at the same
     * coordinates. Pre-fix RED: the dimensionless overload stamped the
     * overworld 0.
     */
    @Test
    void theNetherWirePopDropsIntoTheNether() throws Exception {
        loadNetherProbe();
        EngineWorld nether = server.worldFor(-1);

        // The rig: a stone floor in the nether with a wire on it.
        BlockPosition floor = new BlockPosition(PROBE_X, PROBE_Y, PROBE_Z);
        BlockPosition wire = floor.offset(0, 1, 0);
        server.ticker().submit(() -> {
            nether.setBlock(floor, BuiltinBlocks.STONE);
            nether.setBlock(wire, RedstoneBlocks.wireOfPower(0));
        });
        awaitServer(server, () -> RedstoneBlocks.isWire(nether.getBlock(wire)),
                "the nether wire placed");

        // The pop: the floor leaves, the wire's support gate fails
        // (neighborChanged over wireFloorHolds), the drop spawns.
        server.ticker().submit(() ->
                nether.setBlock(floor, BuiltinBlocks.AIR));
        awaitServer(server, () -> {
            for (ItemEntity item : server.itemEntities().all()) {
                if (item.stack().type().identifier().value().equals("redstone")) {
                    return true;
                }
            }
            return false;
        }, "the wire popped its redstone drop");

        ItemEntity popped = null;
        for (ItemEntity item : server.itemEntities().all()) {
            if (item.stack().type().identifier().value().equals("redstone")) {
                popped = item;
            }
        }
        assertNotNull(popped, "the popped redstone drop exists");
        assertEquals(-1, popped.dimension(),
                "the nether wire pop's drop carries the NETHER stamp "
                        + "(Block.dropItem(this.world, ...) — the pre-fix "
                        + "dimensionless overload stamped the overworld 0)");
        for (ItemEntity item : server.itemEntities().all()) {
            assertTrue(item.dimension() != 0 || !item.stack().type().identifier()
                            .value().equals("redstone"),
                    "no redstone drop ever spawns in the overworld");
        }
    }

    // ------------------------------------------------------------------
    // Arm 2 — THE NETHER CHEST CART'S DEATH (the stamp + the two forms)
    // ------------------------------------------------------------------

    /**
     * A nether chest minecart dying spills EVERYTHING into the NETHER
     * ({@code MinecartEntity.dropItems} + {@code
     * InventoryUtils.dropItems(this.world, this, this)}): the plain
     * cart and CHEST items at the body's EXACT feet anchor with the
     * 10-tick default pickup delay ({@code Entity.dropItem(stack,
     * 0.0F)} + {@code setDefaultPickUpDelay()}), and the coal cargo
     * riding the spill form — the 10-30 chunk splits, NO pickup delay,
     * the [0.1, 0.9] band — every drop stamped -1. Pre-fix RED: the
     * dimensionless overload stamped 0, the +0.3 cell-inset band
     * displaced the plain drops, and the cargo never split.
     */
    @Test
    void theNetherChestCartDeathSpillsIntoTheNether() throws Exception {
        loadNetherProbe();
        EngineWorld nether = server.worldFor(-1);

        // The rig: a stone platform in the nether with headroom.
        int cartX = PROBE_X + 20;
        BlockPosition stand = new BlockPosition(cartX, PROBE_Y, PROBE_Z);
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    nether.setBlock(new BlockPosition(cartX + dx, PROBE_Y, PROBE_Z + dz),
                            BuiltinBlocks.STONE);
                    for (int dy = 1; dy <= 4; dy++) {
                        nether.setBlock(new BlockPosition(cartX + dx, PROBE_Y + dy,
                                PROBE_Z + dz), BuiltinBlocks.AIR);
                    }
                }
            }
        });
        awaitServer(server, () -> BuiltinBlocks.STONE.equals(nether.getBlock(stand)),
                "the nether cart platform stands");

        // The chest cart with a 64-coal cargo, stamped for the nether.
        final ChestMinecartEntity[] cartHolder = new ChestMinecartEntity[1];
        server.ticker().submit(() -> {
            VehicleEntity spawned = server.vehicles()
                    .spawnChestMinecart(new Position(cartX + 0.5, PROBE_Y + 1.35,
                            PROBE_Z + 0.5), -1);
            cartHolder[0] = (ChestMinecartEntity) spawned;
        });
        awaitServer(server, () -> cartHolder[0] != null, "the chest cart spawned");
        assertEquals(-1, cartHolder[0].dimension(), "the cart carries the nether stamp");
        server.ticker().submit(() ->
                cartHolder[0].chest().setSlot(3, ItemStack.of(BuiltinItems.COAL, 64)));

        // The death: one 10.0 wound (the i-frame-free kill), the dead
        // arm draining on the following ticks with the drops.
        server.ticker().submit(() -> server.vehicles().hurt(cartHolder[0], 10.0f));
        awaitServer(server, () -> cartHolder[0].dead(), "the cart died");
        awaitServer(server, () -> {
            boolean cart = false;
            boolean chest = false;
            for (ItemEntity item : server.itemEntities().all()) {
                String id = item.stack().type().identifier().value();
                if (id.equals("minecart") && item.dimension() == -1) {
                    cart = true;
                }
                if (id.equals("chest") && item.dimension() == -1) {
                    chest = true;
                }
            }
            return cart && chest;
        }, "the cart and chest items dropped in the nether");

        // Every drop carries the nether stamp — the overworld never
        // sees one (the pre-fix dimensionless overload stamped 0).
        // THE CHUNK-SPLIT'S OWN TRANSIENCE (the reference's merge law,
        // ItemEntity.mergeWithNearbyItems lines 116-120: the chunks
        // spawn at the SAME band point and merge back within ticks —
        // vanilla's own splatter-then-recombine; the split band itself
        // pinned at the unit seat, ChestManagerTest's spill walk).
        int coalTotal = 0;
        ItemEntity cartItem = null;
        ItemEntity chestItem = null;
        for (ItemEntity item : server.itemEntities().all()) {
            assertEquals(-1, item.dimension(),
                    "every death drop carries the NETHER stamp");
            String id = item.stack().type().identifier().value();
            if (id.equals("coal")) {
                coalTotal += item.stack().count();
                assertTrue(item.pickupDelayTicks() <= 5,
                        "the spill form carries NO default pickup delay "
                                + "(InventoryUtils never calls setDefaultPickUpDelay; the "
                                + "merge's survivor inherits the older chunk's own)");
                // The spill band: the chunks spawn within [0.1, 0.9] of
                // the body's own anchor (the small gaussian drift the
                // only departure; the merge keeps the older chunk's
                // position).
                assertTrue(item.position().x() > cartX + 0.5 - 0.2
                                && item.position().x() < cartX + 0.5 + 1.1,
                        "the coal rides the [anchor+0.1, anchor+0.9] band");
            } else if (id.equals("minecart")) {
                cartItem = item;
            } else if (id.equals("chest")) {
                chestItem = item;
            }
        }
        assertEquals(64, coalTotal, "the coal cargo's total preserved whole");
        assertNotNull(cartItem, "the plain cart item dropped");
        assertNotNull(chestItem, "the chest block item dropped");
        // The two forms' own delays: the plain drops carry the 10-tick
        // default (Entity.dropItem's setDefaultPickUpDelay), the spill
        // none — the difference stable whatever the observation tick.
        assertTrue(cartItem.pickupDelayTicks() > 5,
                "the plain cart item carries the default pickup delay");
        assertTrue(chestItem.pickupDelayTicks() > 5,
                "the chest block item carries the default pickup delay");
        // The plain drops' EXACT feet anchor: the first-observed y sits
        // near the body's feet (PROBE_Y + 1 = 41.0), never the pre-fix
        // +0.3 cell-inset band's [41.9, 42.4].
        assertTrue(cartItem.position().y() < 41.7,
                "the cart item spawns at the body's exact feet anchor (observed y "
                        + cartItem.position().y() + ")");
    }
}
