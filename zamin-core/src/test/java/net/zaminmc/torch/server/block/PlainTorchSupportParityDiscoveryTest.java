package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-SP1's fifth site — the PLAIN torch's support walk (the census the
 * pass-38b record missed): the reference's one {@code TorchBlock} class
 * carries the one law for BOTH torch families (the redstone torch
 * extends it), and its FLOOR arm is {@code canSitOnTop} (TorchBlock
 * lines 49-56):
 *
 * <pre>{@code
 * canSitOnTop = World.hasSolidTop(world, pos)            // the five arms
 *            || block instanceof FenceBlock              // the explicit list
 *            || block == Blocks.GLASS
 *            || block == Blocks.COBBLESTONE_WALL
 *            || block == Blocks.STAINED_GLASS
 * }</pre>
 *
 * <p>THE CONTRACT: these seats are RED on the unfixed develop — the
 * engine's plain-torch arm ({@code BlockUpdateSystem.runUpdate}'s TORCH
 * branch, the {@code identifier.equals(TORCH)} consult at lines 445-455)
 * answers ONE question: "is the cell below AIR?" — no material
 * awareness, no shape awareness. A torch standing on ICE, on a COBWEB,
 * on TALL GRASS or on a LEAF floor STAYS in the engine where vanilla
 * pops each of them at the same neighbor update (each floor outside
 * hasSolidTop's arms AND outside the explicit list). The stone and glass
 * floors are the controls — vanilla keeps the torch on both (the stone
 * through hasSolidTop's first arm, the glass through the explicit list),
 * and so does the engine.</p>
 */
class PlainTorchSupportParityDiscoveryTest {

    @TempDir
    Path dataDir;

    @Test
    void thePlainTorchPopsWhenItsFloorBecomesIceCobwebOrGrass() throws Exception {
        EngineServer server = boot();
        try {
            int x = 380, z = 380, y = 40;
            // Four torches standing LEGALLY on stone floors first (the
            // engine's own-commit walk consults canSitOnTop at the write);
            // each floor then swaps — ICE (translucent), COBWEB (never
            // blocks movement), TALL_GRASS (the replaceable flora) and
            // GLASS (the explicit list's own member — the control that
            // stays).
            server.ticker().submit(() -> {
                for (int dz = 0; dz <= 15; dz += 5) {
                    server.world().setBlock(new BlockPosition(x, y - 1, z + dz),
                            BuiltinBlocks.STONE);
                    server.world().setBlock(new BlockPosition(x, y - 2, z + dz),
                            BuiltinBlocks.DIRT);
                    server.world().setBlock(new BlockPosition(x, y, z + dz),
                            BuiltinBlocks.TORCH);
                }
            });
            await(() -> BuiltinBlocks.TORCH.equals(typeAt(server, x, y, z)),
                    "the torch placed on its stone floor");
            await(() -> BuiltinBlocks.TORCH.equals(typeAt(server, x, y, z + 5)),
                    "the torch placed on its stone floor");
            await(() -> BuiltinBlocks.TORCH.equals(typeAt(server, x, y, z + 10)),
                    "the torch placed on its stone floor");
            await(() -> BuiltinBlocks.TORCH.equals(typeAt(server, x, y, z + 15)),
                    "the torch placed on its stone floor");

            // The floor swaps: each write wakes the torch's own support
            // walk (the six-neighbor dispatch reaches the torch above the
            // swapped cell).
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.ICE);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.COBWEB);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 10),
                        BuiltinBlocks.TALL_GRASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 15),
                        BuiltinBlocks.GLASS);
            });
            sleep(1500);

            // Vanilla (canSitOnTop): the ice, the cobweb and the tall
            // grass are outside hasSolidTop's arms and outside the
            // explicit list — each torch POPS. The engine before the fix:
            // each floor is not AIR — each torch STOOD where vanilla fells
            // it.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP1: the plain torch pops when its floor becomes ice "
                            + "(canSitOnTop — the translucent floor never hosts)");
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z + 5)),
                    "F-SP1: the plain torch pops when its floor becomes cobweb "
                            + "(canSitOnTop — the web never blocks movement, "
                            + "never a solid top)");
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z + 10)),
                    "F-SP1: the plain torch pops when its floor becomes tall "
                            + "grass (canSitOnTop — the replaceable flora is no "
                            + "floor)");
            // The GLASS floor is canSitOnTop's EXPLICIT member — the torch
            // stands there in vanilla AND in the engine (the agreement row).
            assertTrue(BuiltinBlocks.TORCH.equals(typeAt(server, x, y, z + 15)),
                    "F-SP1: the plain torch stays when its floor becomes glass "
                            + "(canSitOnTop's explicit list — GLASS is a member)");
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
                "ptsp" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
