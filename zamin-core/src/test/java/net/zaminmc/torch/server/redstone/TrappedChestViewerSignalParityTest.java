package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trapped chest's viewer signal reaches a wire (F-TC1, ChestBlock
 * lines 377-400 + World.getSignal lines 2358-2362): the reference's own
 * {@code Block.isSolid()} consult ({@code material.isSolidBlocking() &&
 * isCube() && !isSignalSource()}, Block.java lines 274-276) routes the
 * signal read — a solid block re-radiates the strong power entering it,
 * every other block answers its own emission. The trapped chest
 * (isCube FALSE, ChestBlock lines 46-48) answers its viewer count
 * (getSignal lines 382-395, clamped 0-15, weakly in EVERY direction)
 * and powers the block UNDER it (getDirectSignal lines 397-400, the
 * UP-ONLY strong arm); the redstone block (isSignalSource TRUE,
 * RedstoneBlock lines 16-18) answers its own 15 (getSignal lines
 * 21-23); the glass, the leaves and every other isCube-false or
 * translucent family NEVER re-radiates.
 *
 * <p>The engine's pre-fix defect: {@code isSignalSolid} read the
 * MOVEMENT consult ({@code WorldSolidity.isSolid} — what blocks a body),
 * routing the chest family and the glass into the re-radiation arm where
 * vanilla reads their own (zero) emission, and leaving the redstone
 * block's own 15 unreachable. The same movement consult stood in at the
 * wire's climb/descent bands (RedstoneWireBlock.doUpdatePower lines 129
 * + 133) and the comparator's through-solid arm (ComparatorBlock
 * .getInputSignal line 114).</p>
 */
class TrappedChestViewerSignalParityTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // Seat 1 — the weak arm: the wire beside an OPEN trapped chest
    // ------------------------------------------------------------------

    /**
     * ChestBlock.getSignal lines 382-395: one viewer answers 1 weakly in
     * every direction — the wire directly beside the trapped chest reads
     * 1 while the window is open and 0 after the close (the close wake:
     * ChestBlockEntity.onClose lines 322-324).
     */
    @Test
    void theWireBesideAnOpenTrappedChestReadsTheViewerCount() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            PlayerSession player = join(server, "Viewer",
                    new Position(x + 2.5, y, z + 0.5));
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        BuiltinBlocks.TRAPPED_CHEST);
                server.world().setBlock(new BlockPosition(x + 1, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x + 2, y - 1, z),
                        BuiltinBlocks.STONE);
            });
            await(() -> RedstoneBlocks.isWire(typeAt(server, x + 1, y, z)),
                    "the wire placed beside the trapped chest");
            await(() -> wirePower(server, x + 1, y, z) == 0,
                    "the closed chest keeps the wire dead");

            // The open: one viewer.
            AtomicInteger windowId = new AtomicInteger(-1);
            server.useItemOnBlock(player, new BlockPosition(x, y, z), 1,
                    java.util.Optional.empty(), windowId::set);
            await(() -> windowId.get() > 0, "the trapped chest window opened");
            await(() -> wirePower(server, x + 1, y, z) == 1,
                    "the wire beside the open chest reads the ONE viewer");

            // The close: the count departs (onClose lines 322-324 — the
            // wake pair rides the close itself).
            server.closeWindow(player, windowId.get());
            await(() -> wirePower(server, x + 1, y, z) == 0,
                    "the closed chest releases the wire to 0");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 2 — the strong arm: the block UNDER the open trapped chest
    // ------------------------------------------------------------------

    /**
     * ChestBlock.getDirectSignal lines 397-400: the strong arm is UP-ONLY
     * — the block under the trapped chest receives the viewer count and
     * re-radiates it to the wire beside it. The wire one level down (next
     * to the carrier stone) reads 1 on the open and 0 on the close — the
     * close's own wake must carry the pos.down() pair
     * (ChestBlockEntity.onClose line 324: {@code updateNeighbors(pos.down())}).
     */
    @Test
    void theBlockUnderAnOpenTrappedChestReRadiatesTheViewerCount() throws Exception {
        EngineServer server = boot();
        try {
            int x = 30, z = 30, y = 40;
            PlayerSession player = join(server, "Carrier",
                    new Position(x + 2.5, y, z + 0.5));
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        BuiltinBlocks.TRAPPED_CHEST);
                // The carrier stone under the chest + the wire's bed first
                // (the placement support consult reads the cell below at
                // the placement moment).
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 1, y - 2, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 1, y - 1, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x + 2, y - 1, z),
                        BuiltinBlocks.STONE);
            });
            await(() -> RedstoneBlocks.isWire(typeAt(server, x + 1, y - 1, z)),
                    "the wire placed beside the carrier stone");
            await(() -> wirePower(server, x + 1, y - 1, z) == 0,
                    "the closed chest keeps the carrier wire dead");

            AtomicInteger windowId = new AtomicInteger(-1);
            server.useItemOnBlock(player, new BlockPosition(x, y, z), 1,
                    java.util.Optional.empty(), windowId::set);
            await(() -> windowId.get() > 0, "the trapped chest window opened");
            await(() -> wirePower(server, x + 1, y - 1, z) == 1,
                    "the carrier stone re-radiates the ONE viewer to its wire");

            server.closeWindow(player, windowId.get());
            await(() -> wirePower(server, x + 1, y - 1, z) == 0,
                    "the close releases the carrier wire to 0");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 3 — the redstone block's own 15
    // ------------------------------------------------------------------

    /**
     * RedstoneBlock.getSignal lines 21-23: the block answers 15 weakly in
     * EVERY direction — the wire beside it reads 15 (the block is its own
     * source, isSignalSource lines 16-18; it never re-radiates, so the
     * emission is the only arm a wire ever reads).
     */
    @Test
    void theRedstoneBlockPowersTheWireBesideIt() throws Exception {
        EngineServer server = boot();
        try {
            int x = 50, z = 50, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        BuiltinBlocks.REDSTONE_BLOCK);
                server.world().setBlock(new BlockPosition(x + 1, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isWire(typeAt(server, x + 1, y, z)),
                    "the wire placed beside the redstone block");
            await(() -> wirePower(server, x + 1, y, z) == 15,
                    "the wire beside the redstone block reads the full 15");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 4 — the re-radiation negatives (glass + leaves) + the stone
    // ------------------------------------------------------------------

    /**
     * World.getSignal lines 2358-2362 over Block.isSolid(): the glass
     * (isCube FALSE, GlassBlock lines 25-27) and the leaves (the
     * TRANSLUCENT material, Material.java lines 13-30) NEVER re-radiate
     * the strong power entering them — the wire beside a repeater-fed
     * glass or leaves reads 0. The stone control re-radiates the
     * repeater's full 15 (the reference's own through-block law).
     */
    @Test
    void theStronglyPoweredGlassAndLeavesNeverReRadiate() throws Exception {
        EngineServer server = boot();
        try {
            int z = 70, y = 40;
            // The STONE control: lever -> repeater -> stone, wire beside
            // the stone (the LEVER input works on both trees — the seat
            // isolates the re-radiation consult alone).
            int sx = 10;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(sx, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(sx - 1, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(sx - 2, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(sx, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(sx - 1, y, z),
                        RedstoneBlocks.repeaterOf(RedstoneBlocks.FACING_WEST, 1, false));
                server.world().setBlock(new BlockPosition(sx - 2, y, z),
                        RedstoneBlocks.leverOf(5, false));
                server.world().setBlock(new BlockPosition(sx, y - 1, z - 1), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(sx, y, z - 1),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isWire(typeAt(server, sx, y, z - 1)),
                    "the control wire placed beside the stone");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(sx - 2, y, z)));
            await(() -> RedstoneBlocks.leverPowered(typeAt(server, sx - 2, y, z)),
                    "the control lever powered");
            await(() -> wirePower(server, sx, y, z - 1) == 15,
                    "the repeater-fed stone re-radiates the full 15");

            // The GLASS variant: lever -> repeater -> glass, wire beside
            // the glass.
            int gx = 30;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(gx, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(gx - 1, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(gx - 2, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(gx, y, z), BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(gx - 1, y, z),
                        RedstoneBlocks.repeaterOf(RedstoneBlocks.FACING_WEST, 1, false));
                server.world().setBlock(new BlockPosition(gx - 2, y, z),
                        RedstoneBlocks.leverOf(5, false));
                server.world().setBlock(new BlockPosition(gx, y - 1, z - 1), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(gx, y, z - 1),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isWire(typeAt(server, gx, y, z - 1)),
                    "the glass wire placed beside the glass");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(gx - 2, y, z)));
            await(() -> RedstoneBlocks.leverPowered(typeAt(server, gx - 2, y, z)),
                    "the glass lever powered");
            // The NEVER-WINDOW (the repeater's reaction lands well inside
            // it): if the glass re-radiated, the wire would read 15.
            holdNever(() -> wirePower(server, gx, y, z - 1) == 0,
                    "the repeater-fed glass never re-radiates: the wire reads 0", 3000);

            // The LEAVES variant: lever -> repeater -> leaves, wire
            // beside the leaves.
            int lx = 50;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(lx, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(lx - 1, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(lx - 2, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(lx, y, z), BuiltinBlocks.OAK_LEAVES);
                server.world().setBlock(new BlockPosition(lx - 1, y, z),
                        RedstoneBlocks.repeaterOf(RedstoneBlocks.FACING_WEST, 1, false));
                server.world().setBlock(new BlockPosition(lx - 2, y, z),
                        RedstoneBlocks.leverOf(5, false));
                server.world().setBlock(new BlockPosition(lx, y - 1, z - 1), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(lx, y, z - 1),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isWire(typeAt(server, lx, y, z - 1)),
                    "the leaves wire placed beside the leaves");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(lx - 2, y, z)));
            await(() -> RedstoneBlocks.leverPowered(typeAt(server, lx - 2, y, z)),
                    "the leaves lever powered");
            // The NEVER-WINDOW: if the leaves re-radiated, the wire would
            // read 15.
            holdNever(() -> wirePower(server, lx, y, z - 1) == 0,
                    "the repeater-fed leaves never re-radiate: the wire reads 0", 3000);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 5 — the comparator's through-solid arm
    // ------------------------------------------------------------------

    /**
     * ComparatorBlock.getInputSignal line 114: the through-solid read
     * consults the reference's own Block.isSolid() — a comparator never
     * reads a container THROUGH the glass. The stone control reads the
     * chest's fullness one cell further back.
     */
    @Test
    void theComparatorNeverReadsAContainerThroughGlass() throws Exception {
        EngineServer server = boot();
        try {
            int z = 90, y = 40;
            // The STONE control: chest -> stone -> comparator -> wire.
            int sx = 10;
            server.ticker().submit(() -> {
                for (int dx = 0; dx <= 3; dx++) {
                    server.world().setBlock(new BlockPosition(sx + dx, y - 1, z),
                            BuiltinBlocks.STONE);
                }
                server.world().setBlock(new BlockPosition(sx, y, z), BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(sx + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(sx + 2, y, z),
                        RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_WEST, false, false));
                server.world().setBlock(new BlockPosition(sx + 3, y, z),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isComparator(typeAt(server, sx + 2, y, z)),
                    "the control comparator placed");
            fill(server, sx, y, z, 1, 64);
            await(() -> wirePower(server, sx + 3, y, z) == 1,
                    "the stone carries the chest's fullness 1 to the comparator");

            // The GLASS variant: chest -> glass -> comparator -> wire.
            int gx = 40;
            server.ticker().submit(() -> {
                for (int dx = 0; dx <= 3; dx++) {
                    server.world().setBlock(new BlockPosition(gx + dx, y - 1, z),
                            BuiltinBlocks.STONE);
                }
                server.world().setBlock(new BlockPosition(gx, y, z), BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(gx + 1, y, z), BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(gx + 2, y, z),
                        RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_WEST, false, false));
                server.world().setBlock(new BlockPosition(gx + 3, y, z),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isComparator(typeAt(server, gx + 2, y, z)),
                    "the glass comparator placed");
            fill(server, gx, y, z, 1, 64);
            // The NEVER-WINDOW (the control's reaction lands well inside
            // it): if the comparator read through the glass, it would
            // power and the wire would read 1.
            holdNever(() -> wirePower(server, gx + 3, y, z) == 0
                            && !RedstoneBlocks.comparatorPowered(typeAt(server, gx + 2, y, z)),
                    "the glass never carries the chest's fullness: the wire stays 0", 3000);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 6 — the wire's descent band (doUpdatePower line 133)
    // ------------------------------------------------------------------

    /**
     * RedstoneWireBlock.doUpdatePower lines 129 + 133: the descent band
     * reads the wire BELOW a NON-solid neighbor — the glass is not
     * Block.isSolid, so the dust reads the powered wire under it (15 - 1
     * = 14). The stone control is solid: the descent band never fires and
     * the wire reads 0 from that side.
     */
    @Test
    void theWireReadsThePoweredWireBelowThroughGlass() throws Exception {
        EngineServer server = boot();
        try {
            int z = 110, y = 40;
            // The GLASS descent: the lower wire under the glass, the
            // reading wire at the glass's own level.
            int gx = 10;
            server.ticker().submit(() -> {
                // The lower wire's bed + the lever mount.
                server.world().setBlock(new BlockPosition(gx + 1, y - 2, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(gx + 2, y - 2, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(gx + 2, y - 1, z),
                        RedstoneBlocks.leverOf(5, false));
                // The lower wire (under the glass) + the glass above it.
                server.world().setBlock(new BlockPosition(gx + 1, y - 1, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(gx + 1, y, z), BuiltinBlocks.GLASS);
                // The reading wire's bed + the reading wire.
                server.world().setBlock(new BlockPosition(gx, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(gx, y, z),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isWire(typeAt(server, gx, y, z)),
                    "the reading wire placed at the glass's level");
            // The lever powers the lower wire to 15.
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(gx + 2, y - 1, z)));
            await(() -> wirePower(server, gx + 1, y - 1, z) == 15,
                    "the lever drove the lower wire to 15");
            await(() -> wirePower(server, gx, y, z) == 14,
                    "the reading wire reads the descent band: 15 - 1 = 14 through the glass");

            // The STONE control: the descent band never fires.
            int sx = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(sx + 1, y - 2, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(sx + 2, y - 2, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(sx + 2, y - 1, z),
                        RedstoneBlocks.leverOf(5, false));
                server.world().setBlock(new BlockPosition(sx + 1, y - 1, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(sx + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(sx, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(sx, y, z),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isWire(typeAt(server, sx, y, z)),
                    "the control wire placed at the stone's level");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(sx + 2, y - 1, z)));
            await(() -> wirePower(server, sx + 1, y - 1, z) == 15,
                    "the lever drove the control's lower wire to 15");
            await(() -> wirePower(server, sx, y, z) == 0,
                    "the solid stone blocks the descent band: the wire reads 0");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private static void fill(EngineServer server, int x, int y, int z, int slots, int count) {
        server.ticker().submit(() -> {
            var chest = server.chestManager().getOrCreate(new BlockPosition(x, y, z));
            for (int slot = 0; slot < slots; slot++) {
                chest.setSlot(slot, ItemStack.of(BuiltinItems.STONE, count));
            }
        });
    }

    private static PlayerSession join(EngineServer server, String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        server.ticker().submit(() -> session.applyMovement(
                at, net.zaminmc.torch.util.Rotation.ZERO, true));
        return session;
    }

    private static int wirePower(EngineServer server, int x, int y, int z) {
        BlockType type = typeAt(server, x, y, z);
        return RedstoneBlocks.isWire(type) ? RedstoneBlocks.wirePower(type) : -1;
    }

    private static BlockType typeAt(EngineServer server, int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "tcs" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
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
     * The NEVER-WINDOW: the condition must HOLD for the whole window —
     * the negative-assertion guard (a plain await on a "stays 0" seat
     * would pass on the initial state before the system ever reacts;
     * the window outlasts every reaction latency in the seat).
     */
    private void holdNever(BooleanSupplier condition, String description, long windowMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + windowMs;
        while (System.currentTimeMillis() < deadline) {
            if (!condition.getAsBoolean()) {
                throw new AssertionError("Condition broke during the window: " + description);
            }
            Thread.sleep(50);
        }
    }

    @SuppressWarnings("unused")
    private static void unused(BooleanSupplier supplier) {
        assertTrue(supplier.getAsBoolean());
    }
}
