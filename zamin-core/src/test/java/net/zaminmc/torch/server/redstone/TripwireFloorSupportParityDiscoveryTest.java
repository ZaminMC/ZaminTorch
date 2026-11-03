package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-SP4's discovery seats — the tripwire family's floor law (the
 * reference's law, 1.8.8): the SUSPENDED flag and the pop arm both over
 * {@code World.hasSolidTop(pos.down())}, never the collision-solid
 * question the engine's {@code TripwireSystem} consults read
 * ({@code WorldSolidity.isSolid} at lines 88, 229 and 338):
 *
 * <ul>
 * <li>{@code TripwireBlock.onAdded} (lines 110-114): {@code SUSPENDED :=
 *     !World.hasSolidTop(pos.down())} at the commit — a wire placed on a
 *     GLASS floor (translucent, never hasSolidTop) reads SUSPENDED=TRUE
 *     in vanilla, FALSE in the engine (glass is collision-solid).</li>
 * <li>{@code TripwireBlock.neighborChanged} (lines 87-94): the pop when
 *     the floor's hasSolidTop verdict FLIPS since the commit ({@code flag
 *     != flag1} → dropItems + removeBlock) — a stone-floor wire whose
 *     floor becomes GLASS pops in vanilla, stays in the engine.</li>
 * <li>{@code TripwireHookBlock.updateState} (line 101+, the engine's
 *     twin at line 229): the hook walk's own suspended computation reads
 *     the same law.</li>
 * </ul>
 *
 * <p>THE CONTRACT: these seats are RED on the unfixed develop — the
 * engine's three floor-law consults answer the collision question, so a
 * wire on glass is never suspended and a floor flip to glass never pops
 * the wire. The dirt-floor control keeps its wire (both agree).</p>
 */
class TripwireFloorSupportParityDiscoveryTest {

    @TempDir
    Path dataDir;

    @Test
    void theTripwirePopsWhenItsFloorBecomesGlass() throws Exception {
        EngineServer server = boot();
        try {
            int x = 580, z = 580, y = 40;
            // A plain unsuspended wire placed legally on a STONE floor
            // (the engine's commit arm reading suspended=false there —
            // both agree on the opaque floor); the floor then swaps —
            // one to GLASS (hasSolidTop FALSE: the flag flips, the wire
            // pops at neighborChanged lines 87-94), one to DIRT (the
            // opaque control: the flag never flips, the wire stays).
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        TripwireBlocks.wireOf(false, false, false, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        TripwireBlocks.wireOf(false, false, false, false));
            });
            await(() -> TripwireBlocks.isTripwire(typeAt(server, x, y, z)),
                    "the wire placed on its stone floor");
            await(() -> TripwireBlocks.isTripwire(typeAt(server, x, y, z + 5)),
                    "the control wire placed on its stone floor");

            // The floor swaps: GLASS at the divergence row, DIRT at the
            // control.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.DIRT);
            });
            sleep(800);

            // Vanilla (the flag flip at neighborChanged 87-94): the
            // glass-floor wire POPS.
            // The engine before the fix: !WorldSolidity.isSolid(glass) =
            // false == wireSuspended(false) — no flip, the wire STAYED.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP4: the tripwire pops when its floor becomes glass "
                            + "(the hasSolidTop flip — the suspended flag "
                            + "turning true breaks the wire)");
            // The attribution control: the opaque floor keeps the wire.
            assertTrue(TripwireBlocks.isTripwire(typeAt(server, x, y, z + 5)),
                    "F-SP4: the tripwire stays when its floor becomes dirt "
                            + "(the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theTripwireCommitsSuspendedOnTheGlassFloor() throws Exception {
        EngineServer server = boot();
        try {
            int x = 600, z = 600, y = 40;
            // The commit arm (onAdded lines 110-114, the engine's twin at
            // line 88): a wire placed directly on a GLASS floor reads
            // SUSPENDED=TRUE in vanilla (hasSolidTop FALSE — the wire
            // hangs from its hooks). The engine before the fix computes
            // !WorldSolidity.isSolid(glass) = FALSE — the unsuspended
            // form lands.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.DIRT);
            });
            await(() -> BuiltinBlocks.GLASS.equals(typeAt(server, x, y - 1, z)),
                    "the glass floor placed");
            await(() -> BuiltinBlocks.DIRT.equals(typeAt(server, x, y - 1, z + 5)),
                    "the dirt floor placed");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        TripwireBlocks.wireOf(false, false, false, false));
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        TripwireBlocks.wireOf(false, false, false, false));
            });
            sleep(800);

            // Vanilla: the glass-floor wire carries the SUSPENDED bit.
            assertTrue(TripwireBlocks.wireSuspended(typeAt(server, x, y, z)),
                    "F-SP4: the tripwire commits SUSPENDED on the glass "
                            + "floor (onAdded's !hasSolidTop — the wire "
                            + "hanging, never standing on the translucent "
                            + "floor)");
            // The attribution control: the opaque floor's wire stays
            // unsuspended (both agree).
            assertTrue(!TripwireBlocks.wireSuspended(typeAt(server, x, y, z + 5)),
                    "F-SP4: the tripwire commits unsuspended on the dirt "
                            + "floor (the control)");
        } finally {
            server.shutdown(() -> { });
        }
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
