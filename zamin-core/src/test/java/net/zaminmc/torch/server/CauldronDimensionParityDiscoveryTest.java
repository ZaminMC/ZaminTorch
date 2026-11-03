package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.cauldron.CauldronBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave RW-1 — the cauldron's level write rides the CAULDRON'S OWN WORLD
 * (CauldronBlock.setLevel lines 162-165: {@code world.setBlockState(pos,
 * ..., 2)} + {@code world.updateNeighborComparators(pos, this)} — the
 * world the cauldron stands in; onEntityCollision lines 67-74 passes the
 * collision's own world through).
 *
 * <p>The pre-fix engine wrote the OVERWORLD's cell and armed the
 * OVERWORLD's comparators on every cauldron level change whatever the
 * acting dimension: a nether bucket fill left the nether cauldron empty
 * while writing {@code cauldron_level3} into the overworld at the same
 * coordinates, and a burning body's douse in the nether spent a level
 * that landed in the overworld.</p>
 */
class CauldronDimensionParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "cdp" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
    // Seat 1 — the nether bucket fill: the write AND the comparator wake
    // ------------------------------------------------------------------

    /**
     * CauldronBlock.setLevel lines 162-165 on the cauldron's own world:
     * a nether player's water bucket fills the NETHER cell to 3 (the
     * overworld cell at the same coordinates untouched — the pre-fix
     * corruption) and the NETHER's comparator beside the cauldron
     * re-reads the level through its own world's wake
     * (getAnalogSignal lines 188-195: the level IS the signal).
     */
    @Test
    void theNetherBucketFillWritesTheNetherCellAndArmsItsOwnComparators() throws Exception {
        int x = 20, y = 40, z = 20;
        EngineWorld nether = server.worldFor(-1);
        EngineWorld overworld = server.world();
        BlockPosition at = new BlockPosition(x, y, z);
        server.ticker().submit(() -> {
            clearBox(nether, x - 1, y - 1, z - 1, x + 4, y + 2, z + 1);
            // The cauldron's floor + the comparator row's beds.
            nether.setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
            nether.setBlock(new BlockPosition(x + 1, y - 1, z), BuiltinBlocks.STONE);
            nether.setBlock(new BlockPosition(x + 2, y - 1, z), BuiltinBlocks.STONE);
            nether.setBlock(new BlockPosition(x + 3, y - 1, z), BuiltinBlocks.STONE);
            nether.setBlock(at, CauldronBlocks.of(0));
            // The comparator at x+1 facing WEST (its input side reads the
            // cauldron), the wire chain at the output side.
            nether.setBlock(new BlockPosition(x + 1, y, z),
                    RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_WEST, false, false));
            nether.setBlock(new BlockPosition(x + 2, y, z), RedstoneBlocks.wireOfPower(0));
            nether.setBlock(new BlockPosition(x + 3, y, z), RedstoneBlocks.wireOfPower(0));
        });
        await(() -> CauldronBlocks.isCauldron(blockIn(nether, at)),
                "the empty nether cauldron stands");
        await(() -> RedstoneBlocks.isComparator(blockIn(nether,
                        new BlockPosition(x + 1, y, z))),
                "the nether comparator placed");

        // The farmer crosses to the nether beside the cauldron, holding
        // the water bucket.
        PlayerSession farmer = join("NetherFarmer");
        toNether(farmer, new Position(x + 2.5, y, z + 0.5));
        server.ticker().submit(() -> {
            farmer.inventory().setSlot(0, ItemStack.of(BuiltinItems.WATER_BUCKET, 1));
            farmer.inventory().selectHotbarSlot(0);
        });
        await(() -> !farmer.inventory().held().isEmpty(), "the bucket held");
        server.useItemOnBlock(farmer, at, 1, Optional.empty(), windowId -> { });

        // THE WRITE: the NETHER cell fills to 3; the OVERWORLD cell at
        // the same coordinates stays AIR (the pre-fix write corrupted
        // it with cauldron_level3).
        await(() -> CauldronBlocks.levelOf(blockIn(nether, at)) == 3,
                "the nether cauldron filled to 3");
        assertEquals(overworld.airType(), blockIn(overworld, at),
                "the overworld cell at the same coordinates is untouched "
                        + "(the reference's world parameter is the cauldron's own)");

        // THE WAKE: the nether comparator re-read the level — the wire at
        // the output reads the analog 3 (the level IS the signal, lines
        // 188-195) and the chain decays to 2.
        await(() -> wirePowerIn(nether, x + 2, y, z) == 3,
                "the nether wire at the comparator's output reads the level 3");
        await(() -> wirePowerIn(nether, x + 3, y, z) == 2,
                "the nether wire chain decays from the emission: 3 - 1 = 2");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the burning body's douse in the nether
    // ------------------------------------------------------------------

    /**
     * CauldronBlock.onEntityCollision lines 67-74 over the collision's
     * own world: a burning body dipping to the water surface in the
     * NETHER extinguishes and spends one level of the NETHER cauldron —
     * the overworld cell at the same coordinates untouched.
     */
    @Test
    void theNetherDouseSpendsTheNetherCauldronsLevel() throws Exception {
        int x = 40, y = 40, z = 20;
        EngineWorld nether = server.worldFor(-1);
        EngineWorld overworld = server.world();
        BlockPosition at = new BlockPosition(x, y, z);
        server.ticker().submit(() -> {
            clearBox(nether, x - 1, y - 1, z - 1, x + 2, y + 3, z + 1);
            nether.setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
            nether.setBlock(new BlockPosition(x, y - 1, z + 1), BuiltinBlocks.STONE);
            nether.setBlock(at, CauldronBlocks.of(2));
        });
        await(() -> CauldronBlocks.levelOf(blockIn(nether, at)) == 2,
                "the level-2 nether cauldron stands");

        // Ignite the body BESIDE the cauldron first (the CauldronAcceptance
        // seat's own note: joining straight into the cell races the
        // same-tick douse against the ignite — the burn window collapses
        // to a sub-tick sliver the poll can never see), then step in.
        PlayerSession walker = join("NetherWalker");
        toNether(walker, new Position(x + 0.5, y, z + 1.5));
        server.ticker().submit(() -> walker.ignite(200));
        await(() -> walker.burning(), "the walker burns");
        server.ticker().submit(() -> walker.applyMovement(
                new Position(x + 0.5, y + 0.3, z + 0.5), Rotation.ZERO, true));

        // The douse: the burn departs, the NETHER cauldron spends one
        // level (2 -> 1), the overworld cell stays AIR.
        await(() -> !walker.burning(), "the nether cauldron doused the burn");
        await(() -> CauldronBlocks.levelOf(blockIn(nether, at)) == 1,
                "the nether cauldron spent one level (2 -> 1)");
        assertEquals(overworld.airType(), blockIn(overworld, at),
                "the overworld cell at the same coordinates is untouched "
                        + "(the douse's setLevel rides the collision's own world)");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private PlayerSession join(String name) {
        var result = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        server.ticker().submit(session::markPlaying);
        try {
            await(() -> session.state() == PlayerState.PLAYING, name + " playing");
            return session;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted awaiting: " + name, e);
        }
    }

    /** Crosses the session to the nether at the given standing position. */
    private void toNether(PlayerSession session, Position at) {
        server.ticker().submit(() -> {
            session.setDimension(-1);
            session.applyMovement(at, Rotation.ZERO, true);
        });
        try {
            await(() -> session.dimension() == -1, "the session in the nether");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted awaiting the nether cross", e);
        }
    }

    private void clearBox(EngineWorld world, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    world.setBlock(new BlockPosition(bx, by, bz), world.airType());
                }
            }
        }
    }

    private net.zaminmc.torch.block.BlockType blockIn(EngineWorld world, BlockPosition at) {
        AtomicReference<net.zaminmc.torch.block.BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(world.getBlock(at)));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get();
    }

    private int wirePowerIn(EngineWorld world, int x, int y, int z) {
        var type = blockIn(world, new BlockPosition(x, y, z));
        return RedstoneBlocks.isWire(type) ? RedstoneBlocks.wirePower(type) : -1;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
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

    @SuppressWarnings("unused")
    private static void unused(BooleanSupplier supplier) {
        assertTrue(supplier.getAsBoolean());
    }
}
