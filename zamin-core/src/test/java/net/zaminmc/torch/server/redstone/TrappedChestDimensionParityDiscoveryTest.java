package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave RW-1 — the trapped chest's viewer-count wakes ride the CHEST'S OWN
 * WORLD (ChestBlockEntity.onOpen lines 312-318 / onClose lines 320-330:
 * {@code this.world.updateNeighbors(pos, ...)} + {@code .down()} — the
 * world the chest stands in, never a shared field).
 *
 * <p>The pre-fix engine woke the OVERWORLD's redstone on every trapped
 * chest's viewer change: a nether trapped chest's open never re-evaluated
 * the nether wire beside it (the wire stayed 0 with the window open), and
 * the close-path gate read the OVERWORLD's cell at the same coordinates
 * (a stale window over a broken chest never notifying — the nether close
 * wake withheld outright when the overworld cell was not a chest).</p>
 */
class TrappedChestDimensionParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "tcd" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    // ------------------------------------------------------------------
    // Seat 1 — the nether open/close cycle: the NETHER wire hears both
    // ------------------------------------------------------------------

    /**
     * ChestBlockEntity.onOpen lines 312-318 + onClose lines 320-330 over
     * {@code this.world}: the nether trapped chest's viewer count powers
     * the NETHER wire beside it (1 on the open) and releases it (0 on the
     * close) — both wakes on the chest's own world's redstone. Pre-fix
     * both arms fail: the open's wake armed the overworld's wires (the
     * nether wire never re-evaluating), and the close's gate consulted
     * the overworld cell (the wake withheld outright).
     */
    @Test
    void theNetherTrappedChestPowersItsOwnWorldsWireOnOpenAndClose() throws Exception {
        PlayerSession opener = join("NetherOpener");
        int x = 10, y = 40, z = 10;
        EngineWorld nether = server.worldFor(-1);
        server.ticker().submit(() -> {
            clearBox(nether, x - 1, y - 1, z - 1, x + 3, y + 2, z + 1);
            // The chest's floor, the wire's bed, then the family itself.
            nether.setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
            nether.setBlock(new BlockPosition(x + 1, y - 1, z), BuiltinBlocks.STONE);
            nether.setBlock(new BlockPosition(x + 2, y - 1, z), BuiltinBlocks.STONE);
            nether.setBlock(new BlockPosition(x, y, z), BuiltinBlocks.TRAPPED_CHEST);
            nether.setBlock(new BlockPosition(x + 1, y, z), RedstoneBlocks.wireOfPower(0));
        });
        await(() -> RedstoneBlocks.isWire(typeIn(nether, x + 1, y, z)),
                "the nether wire placed beside the trapped chest");
        await(() -> wirePowerIn(nether, x + 1, y, z) == 0,
                "the closed nether chest keeps the wire dead");
        // The opener crosses to the nether beside the chest (the use arm
        // reads the PLAYER'S OWN world — the reference's player.world).
        toNether(opener, new Position(x + 2.5, y, z + 0.5));

        // The open: one viewer — the NETHER wire re-evaluates.
        AtomicInteger windowId = new AtomicInteger(-1);
        server.useItemOnBlock(opener, new BlockPosition(x, y, z), 1,
                java.util.Optional.empty(), windowId::set);
        await(() -> windowId.get() > 0, "the nether trapped chest window opened");
        await(() -> wirePowerIn(nether, x + 1, y, z) == 1,
                "the nether wire beside the open chest reads the ONE viewer");

        // The close: the count departs on the chest's OWN world.
        server.closeWindow(opener, windowId.get());
        await(() -> wirePowerIn(nether, x + 1, y, z) == 0,
                "the nether close releases the wire to 0");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the swap-close: the second open releases the first chest
    // ------------------------------------------------------------------

    /**
     * The swap-close path (closeOpenContainerOnTick's chest arm): opening
     * a second trapped chest closes the first — the first chest's viewer
     * departure wakes the FIRST CHEST'S OWN world's wires. Pre-fix the
     * swap-close's wake consulted the overworld: the nether wire held its
     * 1 forever after the swap.
     */
    @Test
    void theNetherSwapCloseReleasesTheFirstChestsWire() throws Exception {
        PlayerSession opener = join("NetherSwapper");
        int x = 30, y = 40, z = 10;
        EngineWorld nether = server.worldFor(-1);
        server.ticker().submit(() -> {
            clearBox(nether, x - 1, y - 1, z - 1, x + 6, y + 2, z + 1);
            nether.setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
            nether.setBlock(new BlockPosition(x + 1, y - 1, z), BuiltinBlocks.STONE);
            nether.setBlock(new BlockPosition(x + 2, y - 1, z), BuiltinBlocks.STONE);
            nether.setBlock(new BlockPosition(x, y, z), BuiltinBlocks.TRAPPED_CHEST);
            nether.setBlock(new BlockPosition(x + 1, y, z), RedstoneBlocks.wireOfPower(0));
            nether.setBlock(new BlockPosition(x + 4, y - 1, z), BuiltinBlocks.STONE);
            nether.setBlock(new BlockPosition(x + 5, y - 1, z), BuiltinBlocks.STONE);
            nether.setBlock(new BlockPosition(x + 4, y, z), BuiltinBlocks.TRAPPED_CHEST);
        });
        await(() -> RedstoneBlocks.isWire(typeIn(nether, x + 1, y, z)),
                "the nether wire placed beside the first chest");
        await(() -> wirePowerIn(nether, x + 1, y, z) == 0,
                "the closed first chest keeps the wire dead");
        // The opener crosses to the nether between the two chests.
        toNether(opener, new Position(x + 2.5, y, z + 0.5));

        // The first open arms the wire.
        AtomicInteger first = new AtomicInteger(-1);
        server.useItemOnBlock(opener, new BlockPosition(x, y, z), 1,
                java.util.Optional.empty(), first::set);
        await(() -> first.get() > 0, "the first nether chest window opened");
        await(() -> wirePowerIn(nether, x + 1, y, z) == 1,
                "the first chest's wire reads the ONE viewer");

        // The swap: the second open closes the first — the departure's
        // wake rides the NETHER's redstone.
        AtomicInteger second = new AtomicInteger(-1);
        server.useItemOnBlock(opener, new BlockPosition(x + 4, y, z), 1,
                java.util.Optional.empty(), second::set);
        await(() -> second.get() > 0, "the second nether chest window opened");
        await(() -> wirePowerIn(nether, x + 1, y, z) == 0,
                "the swap-close releases the first chest's wire to 0");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the overworld isolation control (the never-window)
    // ------------------------------------------------------------------

    /**
     * The reverse control (the regression guard against over-correction):
     * an OVERWORLD trapped chest's open/close cycle never moves the
     * NETHER wire at the same coordinates — each dimension's wakes stay
     * on their own world (the reference's per-WorldServer neighbor walk).
     */
    @Test
    void theOverworldChestCycleNeverMovesTheNetherWire() throws Exception {
        PlayerSession overworldOpener = join("OverOpener");
        int x = 50, y = 40, z = 10;
        EngineWorld nether = server.worldFor(-1);
        EngineWorld overworld = server.world();
        server.ticker().submit(() -> {
            clearBox(nether, x - 1, y - 1, z - 1, x + 3, y + 2, z + 1);
            nether.setBlock(new BlockPosition(x + 1, y - 1, z), BuiltinBlocks.STONE);
            nether.setBlock(new BlockPosition(x + 1, y, z), RedstoneBlocks.wireOfPower(0));
            // The mirrored overworld chest (the same coordinates, the
            // overworld's own cells).
            overworld.setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
            overworld.setBlock(new BlockPosition(x + 1, y - 1, z), BuiltinBlocks.STONE);
            overworld.setBlock(new BlockPosition(x, y, z), BuiltinBlocks.TRAPPED_CHEST);
        });
        await(() -> RedstoneBlocks.isWire(typeIn(nether, x + 1, y, z)),
                "the nether wire placed at the mirrored coordinates");
        await(() -> wirePowerIn(nether, x + 1, y, z) == 0,
                "the nether wire starts dead");
        // The overworld opener stands beside the overworld chest.
        server.ticker().submit(() -> overworldOpener.applyMovement(
                new Position(x + 2.5, y, z + 0.5), Rotation.ZERO, true));

        // The overworld open/close cycle: the nether wire NEVER moves.
        AtomicInteger windowId = new AtomicInteger(-1);
        server.useItemOnBlock(overworldOpener, new BlockPosition(x, y, z), 1,
                java.util.Optional.empty(), windowId::set);
        await(() -> windowId.get() > 0, "the overworld trapped chest window opened");
        server.closeWindow(overworldOpener, windowId.get());
        // The settle window (the never-window): the overworld cycle's
        // wakes land well inside it.
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            assertTrue(wirePowerIn(nether, x + 1, y, z) == 0,
                    "the overworld chest's cycle never powers the nether wire "
                            + "at the same coordinates (the per-world wake law)");
            Thread.sleep(50);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private PlayerSession join(String name) {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.ticker().submit(session::markPlaying);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    /** Crosses the session to the nether at the given standing position. */
    private void toNether(PlayerSession session, Position at) {
        server.ticker().submit(() -> {
            session.setDimension(-1);
            session.applyMovement(at, Rotation.ZERO, true);
        });
        await(() -> session.dimension() == -1, "the session in the nether");
    }

    private void clearBox(EngineWorld world, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    world.setBlock(new BlockPosition(x, y, z), world.airType());
                }
            }
        }
    }

    private void await(BooleanSupplier condition, String what) {
        try {
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                if (condition.getAsBoolean()) {
                    return;
                }
                Thread.sleep(50);
            }
            throw new AssertionError("Condition not met in time: " + what);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted awaiting: " + what, e);
        }
    }

    private BlockType typeIn(EngineWorld world, int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                world.getBlock(new BlockPosition(x, y, z))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get();
    }

    private int wirePowerIn(EngineWorld world, int x, int y, int z) {
        BlockType type = typeIn(world, x, y, z);
        return RedstoneBlocks.isWire(type) ? RedstoneBlocks.wirePower(type) : -1;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
