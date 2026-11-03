package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.server.block.WallBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.hopper.HopperBlocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-SP5's discovery seats — the tripwire hook's WALL consults (the
 * reference's law, 1.8.8): the mount law reads the SIGNAL consult
 * {@code Block.isSolid()}, never the collision-solid question the
 * engine's {@code TripwireSystem} reads at its two wall sites:
 *
 * <ul>
 * <li>{@code TripwireHookBlock.canBePlaced(World, BlockPos, Direction)}
 *     lines 54-56 — the PLACEMENT gate (the item path
 *     {@code BlockItem.useOn} line 43 → {@code World.canPlace} line
 *     2274 → the face-specific form): {@code face.getAxis().
 *     isHorizontal() && world.getBlockState(pos.offset(face.getOpposite
 *     ())).getBlock().isSolid()} — a horizontal clicked face whose HOST
 *     answers the signal consult.</li>
 * <li>{@code TripwireHookBlock.neighborChanged} lines 85-95 — the
 *     SURVIVAL arm: the four-direction scan ({@code canBePlaced(world,
 *     pos)} lines 59-67) subsumed by the mount's own check ({@code
 *     !world.getBlockState(pos.offset(direction.getOpposite())).
 *     getBlock().isSolid()} → {@code dropItems + removeBlock}) — the
 *     hook pops iff its mount is not {@code Block.isSolid()}.</li>
 * <li>{@code Block.isSolid()} (Block.java lines 274-276) — the
 *     three-conjunct SIGNAL consult: {@code material.isSolidBlocking()
 *     && isCube() && !isSignalSource()} — the SAME law the lever/button
 *     family's wall arm reads ({@code ButtonBlock.canSurvive} lines
 *     70-73, the class F-SP1 named and corrected).</li>
 * </ul>
 *
 * <p>THE CONTRACT: these seats are RED on the unfixed develop — the
 * engine's two wall consults ({@code TripwireSystem.checkSupport}'s hook
 * arm at line 350, {@code TripwireSystem.hookPlacementSupported} at line
 * 373) answer the collision question {@code WorldSolidity.isSolid}, so
 * the hook HOLDS on every collision-solid-but-not-signal-solid mount
 * (the translucents, the non-cube solids, the redstone block) where
 * vanilla refuses the placement and pops the mount. The stone row and
 * the air row are the agreement controls.</p>
 */
class TripwireHookWallSupportParityDiscoveryTest {

    @TempDir
    Path dataDir;

    /** The row cursor — every row takes its own four-cell band. */
    private int cursor;

    // ------------------------------------------------------------------
    // Seat 1 — the SURVIVAL walk (neighborChanged's mount check)
    // ------------------------------------------------------------------

    /**
     * A legally-placed hook (a stone mount, FACING EAST away from it)
     * whose mount swaps to the row's block: vanilla re-reads
     * {@code Block.isSolid()} at the swap and pops the hook for every
     * non-signal-solid mount; the engine reads the collision question
     * and holds it.
     */
    @Test
    void theHookPopsWhenTheMountStopsAnsweringIsSolid() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            hookRow(server, rows, BuiltinBlocks.STONE, true,
                    "the stone mount holds the hook (isSolid — the control)");
            hookRow(server, rows, BuiltinBlocks.COBBLESTONE, true,
                    "the cobblestone mount holds the hook (isSolid — the control)");
            hookRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab holds the hook (isCube true, rock material — the control)");
            hookRow(server, rows, null, false,
                    "AIR never holds the hook (the material conjunct — the pop control, "
                            + "the engine agrees)");
            hookRow(server, rows, BuiltinBlocks.GLASS, false,
                    "glass is TRANSLUCENT — Block.isSolid refuses; the hook pops");
            hookRow(server, rows, stainedGlass(), false,
                    "stained glass is TRANSLUCENT — the hook pops");
            hookRow(server, rows, BuiltinBlocks.GLOWSTONE, false,
                    "glowstone is TRANSLUCENT (Material.GLASS at registration) — the hook pops");
            hookRow(server, rows, BuiltinBlocks.TNT, false,
                    "TNT is TRANSLUCENT — the hook pops");
            hookRow(server, rows, BuiltinBlocks.ICE, false,
                    "ice is TRANSLUCENT — the hook pops");
            hookRow(server, rows, BuiltinBlocks.OAK_LEAVES, false,
                    "the leaves are TRANSLUCENT — the hook pops");
            hookRow(server, rows, BuiltinBlocks.CACTUS, false,
                    "the cactus is TRANSLUCENT — the hook pops");
            hookRow(server, rows, BuiltinBlocks.REDSTONE_BLOCK, false,
                    "THE SIGNATURE ROW — the redstone block is a full opaque cube, "
                            + "material.isSolidBlocking && isCube both TRUE, but "
                            + "isSignalSource TRUE (RedstoneBlock line 16): the third "
                            + "conjunct kills it and the hook pops — the row that "
                            + "proves the consult is the signal law, not any "
                            + "solidity approximation");
            hookRow(server, rows, BuiltinBlocks.FENCE, false,
                    "the fence is a NON-CUBE — Block.isSolid refuses; the hook pops "
                            + "(the TORCH's floor law accepts fences; the hook's wall "
                            + "law never does)");
            hookRow(server, rows, BuiltinBlocks.STONE_SLAB, false,
                    "the single slab is a NON-CUBE — the hook pops");
            hookRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST, false,
                    "the stair is a NON-CUBE — the hook pops");
            hookRow(server, rows, BuiltinBlocks.GLASS_PANE, false,
                    "the glass pane is TRANSLUCENT and a NON-CUBE — the hook pops");
            hookRow(server, rows, BuiltinBlocks.IRON_BARS, false,
                    "the iron bars are a NON-CUBE — the hook pops");
            hookRow(server, rows, WallBlocks.state(0), false,
                    "the cobblestone wall is a NON-CUBE — the hook pops (the TORCH's "
                            + "floor law names walls; the hook's wall law never does)");
            hookRow(server, rows, BuiltinBlocks.CHEST, false,
                    "the chest is a NON-CUBE — the hook pops");
            hookRow(server, rows, HopperBlocks.hopperOf(0, true), false,
                    "the hopper is a NON-CUBE — the hook pops (the FLOOR consults "
                            + "accept hoppers through hasSolidTop; the hook's WALL "
                            + "consult refuses them)");
            assertVanilla("seat 1 — the hook's WALL survival consult (Block.isSolid)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 2 — the PLACEMENT gate (canBePlaced's face-specific form)
    // ------------------------------------------------------------------

    /**
     * The placement gate ({@code TripwireSystem.hookPlacementSupported}
     * — the engine's twin of {@code TripwireHookBlock.canBePlaced(World,
     * BlockPos, Direction)}): a horizontal clicked face whose host
     * answers {@code Block.isSolid()}. Vanilla refuses every
     * non-signal-solid host outright — the hook never lands there; the
     * engine's collision consult accepts the divergent band.
     */
    @Test
    void thePlacementGateRefusesEveryNonSignalSolidHost() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            gateRow(server, rows, BuiltinBlocks.STONE, true,
                    "the stone host takes the placement (isSolid — the control)");
            gateRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab takes the placement (isCube true — the control)");
            gateRow(server, rows, BuiltinBlocks.GLASS, false,
                    "glass is TRANSLUCENT — the placement is refused");
            gateRow(server, rows, BuiltinBlocks.GLOWSTONE, false,
                    "glowstone is TRANSLUCENT — the placement is refused");
            gateRow(server, rows, BuiltinBlocks.ICE, false,
                    "ice is TRANSLUCENT — the placement is refused");
            gateRow(server, rows, BuiltinBlocks.OAK_LEAVES, false,
                    "the leaves are TRANSLUCENT — the placement is refused");
            gateRow(server, rows, BuiltinBlocks.REDSTONE_BLOCK, false,
                    "THE SIGNATURE ROW — the redstone block's isSignalSource TRUE "
                            + "refuses the placement (the third conjunct's kill)");
            gateRow(server, rows, BuiltinBlocks.FENCE, false,
                    "the fence is a NON-CUBE — the placement is refused");
            gateRow(server, rows, BuiltinBlocks.STONE_SLAB, false,
                    "the single slab is a NON-CUBE — the placement is refused");
            gateRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST, false,
                    "the stair is a NON-CUBE — the placement is refused");
            gateRow(server, rows, BuiltinBlocks.CHEST, false,
                    "the chest is a NON-CUBE — the placement is refused");
            gateRow(server, rows, HopperBlocks.hopperOf(0, true), false,
                    "the hopper is a NON-CUBE — the placement is refused");
            assertVanilla("seat 2 — the hook's placement gate (canBePlaced's "
                    + "Block.isSolid host consult)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The row drivers
    // ------------------------------------------------------------------

    /** One survival-walk row: the mount swap, then the hook's fate. */
    private void hookRow(EngineServer server, List<RowOutcome> rows, BlockType mount,
            boolean vanillaHolds, String law) throws Exception {
        int x = 700 + (cursor % 40) * 4;
        int y = 40;
        int z = 700 + (cursor / 40) * 4;
        cursor++;
        BlockPosition mountCell = new BlockPosition(x - 1, y, z);
        BlockPosition hookCell = new BlockPosition(x, y, z);
        BlockType hook = TripwireBlocks.hookOf(5 /* EAST, away from the west mount */,
                false, false);

        // 1. the valid pair: the stone mount first, then the hook.
        server.ticker().submit(() -> {
            server.world().setBlock(mountCell, BuiltinBlocks.STONE);
            server.world().setBlock(hookCell, hook);
        });
        assertEquals(hook, typeAt(server, hookCell),
                "the hook landed on the stone mount (the row's precondition)");

        // 2. the mount swap — the survival walk consults the new mount
        //    (the walk runs synchronously inside the change dispatch).
        server.ticker().submit(() -> {
            if (mount == null) {
                server.world().setBlock(mountCell, server.world().airType());
            } else {
                server.world().setBlock(mountCell, mount);
            }
        });

        // 3. the settle window, then the read through the tick thread (a
        //    deferred pop must not pass silently).
        Thread.sleep(150);
        BlockType outcome = typeAt(server, hookCell);
        boolean engineHolds = hook.equals(outcome);
        rows.add(new RowOutcome(mount == null ? "air" : mount.identifier().value(),
                vanillaHolds, engineHolds, law));
    }

    /** One placement-gate row: the host block, then the gate's answer. */
    private void gateRow(EngineServer server, List<RowOutcome> rows, BlockType host,
            boolean vanillaTakes, String law) throws Exception {
        int x = 900 + (cursor % 40) * 4;
        int y = 40;
        int z = 900 + (cursor / 40) * 4;
        cursor++;
        BlockPosition hostCell = new BlockPosition(x - 1, y, z);
        BlockPosition target = new BlockPosition(x, y, z);
        server.ticker().submit(() -> server.world().setBlock(hostCell, host));
        await(() -> host.equals(typeAt(server, hostCell)),
                "the host block landed (the row's precondition)");

        // The gate read through the tick thread — the REAL consult the
        // placement path (EngineServer.placeTripwireHookOnTick) drives,
        // with the REAL world state: face 5 = EAST, the host at the
        // face-opposite cell exactly as the reference's
        // pos.offset(face.getOpposite()).
        AtomicReference<Boolean> answer = new AtomicReference<>();
        server.ticker().submit(() -> answer.set(
                TripwireSystem.hookPlacementSupported(server.world(), target, 5)));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && answer.get() == null) {
            sleep(10);
        }
        rows.add(new RowOutcome(host.identifier().value(), vanillaTakes,
                answer.get() != null && answer.get(), law));
    }

    // ------------------------------------------------------------------
    // The shared rig
    // ------------------------------------------------------------------

    private static void assertVanilla(String seat, List<RowOutcome> rows) {
        StringBuilder divergent = new StringBuilder();
        StringBuilder table = new StringBuilder();
        for (RowOutcome row : rows) {
            String line = String.format("%-24s vanilla=%-4s engine=%-4s — %s%n",
                    row.mount, row.vanillaHolds ? "hold" : "pop",
                    row.engineHolds ? "hold" : "pop", row.law);
            table.append(line);
            if (row.vanillaHolds != row.engineHolds) {
                divergent.append(line);
            }
        }
        assertTrue(divergent.length() == 0,
                seat + " — the divergent rows (the reference's law vs the engine's answer):"
                        + System.lineSeparator() + divergent
                        + "The complete row table:" + System.lineSeparator() + table);
    }

    private static final class RowOutcome {
        final String mount;
        final boolean vanillaHolds;
        final boolean engineHolds;
        final String law;

        RowOutcome(String mount, boolean vanillaHolds, boolean engineHolds, String law) {
            this.mount = mount;
            this.vanillaHolds = vanillaHolds;
            this.engineHolds = engineHolds;
            this.law = law;
        }
    }

    private static BlockType stainedGlass() {
        // the white member (color 0) of the flat stained-glass family —
        // the reference's single StainedGlassBlock instance (id 95)
        return new EngineBlockType(
                net.zaminmc.torch.server.block.DyeFamilyBlocks.stainedGlassBlock(0),
                "Stained Glass", 0);
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

    private static BlockType typeAt(EngineServer server, BlockPosition pos) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(server.world().getBlock(pos)));
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
                "scp" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
}
