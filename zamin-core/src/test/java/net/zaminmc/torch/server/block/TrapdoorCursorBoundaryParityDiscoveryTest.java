package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-044's behavioral seat — the trapdoor's cursorY HALF boundary reads
 * {@code >= 0.5f} where the reference reads {@code dy > 0.5F} (the strict
 * form, TrapdoorBlock.getPlacementState lines 156-160 — the arm running
 * for every horizontal click). At the EXACT mid-face click — the wire's
 * {@code readByte() / 16.0f} carrying byte 8, exactly 0.5 — the engine
 * places the TOP half where vanilla places the BOTTOM half.
 *
 * <p>THE CONTROLS: byte 7 (7/16 = 0.4375 — both trees place the BOTTOM
 * half) and byte 9 (9/16 = 0.5625 — both trees place the TOP half) frame
 * the boundary; the seat drives the REAL item-use path (the trapdoor item
 * held by a survival player, the side click on a stone mount) through the
 * cursor-fraction overload, exactly the form the protocol's C08 handler
 * feeds ({@code cursorY = packet.readByte() / 16.0f},
 * V18Connection lines 1591-1592).</p>
 *
 * <p>THE SIBLING CONTRAST (the attribution): the slab and stairs sites
 * read the STRICT form already — {@code placeSlabOnTick} (line ~16983)
 * and {@code placeStairsOnTick} (line ~17024) both read
 * {@code cursorY > 0.5f} with the reference's own citation ("the
 * exact-middle click lays the bottom exactly like the reference's own
 * dy > 0.5f gate", SlabBlock lines 76-79 / StairsBlock lines 492-497
 * reading the same strict {@code dy > 0.5}). The trapdoor's
 * {@code >=} at line ~16162 is the SOLE divergent site of the three
 * cursor-splitting families — the one-character class. RED BY CONTRACT
 * on unfixed develop at the byte-8 row alone.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrapdoorCursorBoundaryParityDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "trapcursor", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private static ItemType trapdoorItem() {
        return net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:trapdoor")).orElseThrow();
    }

    private void holdTrapdoor(PlayerSession player) {
        player.inventory().setSlot(0, ItemStack.of(trapdoorItem(), 3));
        player.inventory().selectHotbarSlot(0);
    }

    /** A free floor cell two blocks east of the player, one below the feet. */
    private BlockPosition anchorNear(PlayerSession player) {
        return new BlockPosition(
                (int) Math.floor(player.position().x()) + 2,
                (int) Math.floor(player.position().y()) - 1,
                (int) Math.floor(player.position().z()));
    }

    private void seed(BlockPosition at, BlockType type)
            throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, type));
        await(() -> server.world().getBlock(at).equals(type), "the cell seeded");
    }

    private void clear(BlockPosition at) throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, server.world().airType()));
        await(() -> server.world().getBlock(at).equals(server.world().airType()),
                "the cell cleared");
    }

    private BlockType typeAt(BlockPosition at) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(server.world().getBlock(at)));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return result.get();
    }

    /**
     * THE BOUNDARY SEAT: one boot, three side clicks on three stone mounts
     * — the cursor at 7/16 (the below-band control), 8/16 (the exact
     * boundary) and 9/16 (the above-band control). Vanilla's law
     * (dy > 0.5): BOTTOM, BOTTOM, TOP. The engine's law (cursorY >= 0.5):
     * BOTTOM, TOP, TOP — the middle row RED, the two controls GREEN on
     * unfixed develop.
     */
    @Test
    void theExactMidFaceClickLaysTheBottomHalfVanillasAnswer() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("TrapdoorCursor");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition base = anchorNear(builder);
            // Three mounts two blocks apart (every target well inside the
            // use reach's 64.0 squared band): the click on each pillar's
            // NORTH face (face 2), the target the cell north of the
            // pillar at the same height (air, cleared).
            BlockPosition mountBelow = base;
            BlockPosition mountMid = base.offset(0, 0, 2);
            BlockPosition mountAbove = base.offset(0, 0, 4);
            BlockPosition targetBelow = mountBelow.offset(0, 0, -1);
            BlockPosition targetMid = mountMid.offset(0, 0, -1);
            BlockPosition targetAbove = mountAbove.offset(0, 0, -1);
            seed(mountBelow, net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
            seed(mountMid, net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
            seed(mountAbove, net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
            clear(targetBelow);
            clear(targetMid);
            clear(targetAbove);
            holdTrapdoor(builder);
            // The cursor byte arrives as the fraction byte/16 — exactly the
            // protocol's form (V18Connection: readByte() / 16.0f).
            server.useItemOnBlock(builder, mountBelow, 2, java.util.Optional.empty(),
                    0.0f, 7 / 16.0f, 0.0f, windowId -> { });
            server.useItemOnBlock(builder, mountMid, 2, java.util.Optional.empty(),
                    0.0f, 8 / 16.0f, 0.0f, windowId -> { });
            server.useItemOnBlock(builder, mountAbove, 2, java.util.Optional.empty(),
                    0.0f, 9 / 16.0f, 0.0f, windowId -> { });
            Thread.sleep(600); // the settle window past, the verdicts stand

            // The below-band control: 7/16 < 0.5 — BOTH trees place BOTTOM.
            assertTrue(TrapdoorBlocks.isTrapdoor(typeAt(targetBelow)),
                    "the below-band control: the trapdoor places at 7/16");
            assertFalse(TrapdoorBlocks.isTop(typeAt(targetBelow)),
                    "VT-044: the 7/16 click lays the BOTTOM half (both trees "
                            + "agree — the below-band control). Observed: "
                            + typeAt(targetBelow).identifier());

            // THE BOUNDARY ROW: 8/16 = exactly 0.5 — the reference's strict
            // dy > 0.5F answers FALSE (the BOTTOM half); the engine's
            // >= answers TRUE (the TOP half). RED on unfixed develop.
            assertTrue(TrapdoorBlocks.isTrapdoor(typeAt(targetMid)),
                    "the boundary row: the trapdoor places at 8/16");
            assertFalse(TrapdoorBlocks.isTop(typeAt(targetMid)),
                    "VT-044: the EXACT mid-face click (byte 8, exactly 0.5) "
                            + "lays the BOTTOM half — the reference's strict "
                            + "dy > 0.5F (TrapdoorBlock.getPlacementState lines "
                            + "156-160). Observed: "
                            + typeAt(targetMid).identifier() + " — the engine's "
                            + ">= boundary placing the TOP half.");

            // The above-band control: 9/16 > 0.5 — BOTH trees place TOP.
            assertTrue(TrapdoorBlocks.isTrapdoor(typeAt(targetAbove)),
                    "the above-band control: the trapdoor places at 9/16");
            assertTrue(TrapdoorBlocks.isTop(typeAt(targetAbove)),
                    "VT-044: the 9/16 click lays the TOP half (both trees "
                            + "agree — the above-band control). Observed: "
                            + typeAt(targetAbove).identifier());

            // The item consumption: three placements consumed three items
            // in BOTH trees (the gate passed on every row — the click face
            // horizontal, the stone mount a legal canBePlacedOn member).
            assertTrue(builder.inventory().held().isEmpty()
                            || builder.inventory().held().count() == 0,
                    "the three placements consumed the held stack whole (the "
                            + "agreement consumption — the gate never refused)");
        } finally {
            server.shutdown(() -> { });
        }
    }
}
