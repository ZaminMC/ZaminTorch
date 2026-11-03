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
 * F-SP3's discovery seats — the fire family's floor consult (the
 * reference's law, 1.8.8): the engine's {@code FireBlocks.hasSolidTop}
 * (lines 325-359) answers the translucent fallthrough — its common case
 * (line 358) falls through to {@code WorldSolidity.isSolid}, so GLASS,
 * ICE, TNT, every {@code _leaves}, the GLOWSTONE, the SEA_LANTERN and
 * the CACTUS all answer TRUE — where the reference's {@code
 * World.hasSolidTop} (World.java lines 1740-1752) answers FALSE for each
 * (the translucent materials never pass the first arm's {@code
 * material.isSolidBlocking()} conjunct).
 *
 * <p>The four consult sites that read the imprecise form (each the
 * reference's own fire law):</p>
 * <ul>
 * <li>{@code FireBlocks.neighborChanged} (lines 429-436 — the reference's
 *     FireBlock.neighborChanged lines 311-315): {@code !hasSolidTop(below)
 *     && !hasFlammableNeighbor(pos)} → the fire pops when its floor
 *     stops hosting and no fuel holds it.</li>
 * <li>{@code FireBlocks.canBePlaced} (lines 402-405 — the reference's
 *     lines 306-308): {@code hasSolidTop(below) || hasFlammableNeighbor}.</li>
 * <li>The tick burn-out (line 557 — the reference's line 168): {@code
 *     !hasSolidTop(below) || i > 3} with no fuel.</li>
 * <li>The block-change support gate (lines 688-691 — the reference's
 *     lines 318-325): the same pop at the flame's own commit.</li>
 * </ul>
 *
 * <p>THE CONTRACT: these seats are RED on the unfixed develop — a fire
 * standing on an isolated stone platform whose floor becomes GLASS,
 * ICE or GLOWSTONE (no flammable neighbour anywhere near — the
 * NON-flammable translucents alone; the flammable translucents, the
 * leaves and the TNT, KEEP a fire in vanilla as fuel and never seat
 * this law) SURVIVES in the engine where vanilla pops it at the same
 * neighbour update. The dirt-floor control keeps its flame (both
 * agree) — the attribution: the pop is the consult's law, never the
 * swap's side effect.</p>
 */
class FireFloorSupportParityDiscoveryTest {

    @TempDir
    Path dataDir;

    @Test
    void theFirePopsWhenItsFloorBecomesGlass() throws Exception {
        EngineServer server = boot();
        try {
            int x = 540, z = 540, y = 40;
            // Two age-0 flames placed on isolated STONE platforms (no
            // flammable neighbour anywhere near — the platforms float on
            // air at y=40); the floors then swap — one to GLASS (the
            // translucent fallthrough: hasSolidTop FALSE in vanilla, the
            // fire pops), one to DIRT (the opaque control: the fire
            // stays).
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        BuiltinBlocks.FIRE);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        BuiltinBlocks.FIRE);
            });
            await(() -> FireBlocks.isFire(typeAt(server, x, y, z).identifier()),
                    "the flame placed on its stone floor");
            await(() -> FireBlocks.isFire(typeAt(server, x, y, z + 5).identifier()),
                    "the control flame placed on its stone floor");

            // The floor swaps: GLASS at the divergence row, DIRT at the
            // control.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.DIRT);
            });
            // One tick past the swap (the block-update dispatch that
            // delivers the floor change to the flame's cell — before the
            // fire's own scheduled life ages it).
            sleep(600);

            // Vanilla (FireBlock.neighborChanged lines 311-315 over
            // World.hasSolidTop — glass is translucent): the glass-floor
            // flame POPS at the neighbour update.
            // The engine before the fix: FireBlocks.hasSolidTop(glass)
            // falls through to WorldSolidity.isSolid = true — the flame
            // SURVIVED on the glass where vanilla fells it.
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP3: the fire pops when its floor becomes glass "
                            + "(World.hasSolidTop — the translucent material "
                            + "never answers, and no flammable neighbour "
                            + "holds the flame)");
            // The attribution control: the opaque floor keeps the flame.
            assertTrue(FireBlocks.isFire(typeAt(server, x, y, z + 5).identifier()),
                    "F-SP3: the fire stays when its floor becomes dirt "
                            + "(the control)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theFirePopsWhenItsFloorBecomesIceOrGlowstone() throws Exception {
        EngineServer server = boot();
        try {
            int x = 560, z = 560, y = 40;
            // The same construction over ICE and GLOWSTONE — two
            // NON-FLAMMABLE translucent families (Material.ICE and the
            // glowstone's Material.GLASS material set: neither ever
            // hasSolidTop, neither in the fire-info table — no fuel
            // holds the flame). The FLAMMABLE translucents (the leaves
            // at flammability 30, the TNT at 15) KEEP a fire in vanilla
            // — the fuel consult's own arm — and never seat this law.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        BuiltinBlocks.FIRE);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        BuiltinBlocks.FIRE);
            });
            await(() -> FireBlocks.isFire(typeAt(server, x, y, z).identifier()),
                    "the ice-row flame placed on its stone floor");
            await(() -> FireBlocks.isFire(typeAt(server, x, y, z + 5).identifier()),
                    "the glowstone-row flame placed on its stone floor");

            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.ICE);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.GLOWSTONE);
            });
            sleep(600);

            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "F-SP3: the fire pops when its floor becomes ice "
                            + "(Material.ICE — translucent, never "
                            + "hasSolidTop, never flammable)");
            assertTrue(BuiltinBlocks.AIR.equals(typeAt(server, x, y, z + 5)),
                    "F-SP3: the fire pops when its floor becomes glowstone "
                            + "(the GLASS material set — translucent, never "
                            + "hasSolidTop, never flammable)");
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
