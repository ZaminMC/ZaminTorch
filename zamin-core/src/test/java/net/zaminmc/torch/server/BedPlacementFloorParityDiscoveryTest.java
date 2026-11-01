package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-BED's behavioral seats (pass 2) — the bed placement path's missing
 * BedItem.useOn gates, seated per divergent row against the reference's
 * law (reference/1.8.8 BedItem lines 23-52).
 *
 * <p>THE LAW (BedItem.useOn — the placement contract, two gates the
 * engine's placeBedOnTick carries NEITHER of):</p>
 * <ul>
 *   <li><b>The UP-face gate</b> (lines 24-26):
 *       {@code if (face != Direction.UP) return false;} — the bed places
 *       ONLY on an up-click; every side/down click fails the use outright
 *       (no block, no item spent).</li>
 *   <li><b>The both-halves floor gate</b> (line 42):
 *       {@code flag2 && flag3 && World.hasSolidTop(world, pos.down()) &&
 *       World.hasSolidTop(world, blockpos.down())} — the placement cell
 *       ({@code pos}, the cell above the clicked block for a normal
 *       up-click) AND the head cell ({@code blockpos = pos.offset(look)})
 *       must EACH stand on a solid top; any refusal fails the use
 *       outright. The five arms of {@code World.hasSolidTop} (World.java
 *       lines 1740-1752) turn on {@code material.isSolidBlocking()} — the
 *       translucents (GLASS, the glowstone among Material.GLASS) NEVER
 *       answer; AIR never answers; the bottom halves never answer.</li>
 * </ul>
 *
 * <p>THE ENGINE'S SHAPE: {@code EngineServer.placeBedOnTick} checks the
 * target cell's occupancy, the player box, the head cell's AIR-ness and
 * the held item — and places. NO floor consult anywhere (a bed places on
 * AIR and on GLASS), and NO face gate (a side click places the pair
 * sideways against the reference's outright refusal).</p>
 *
 * <p>THE CONSTRUCTION: every seat drives the real item-use path (the bed
 * item held, the click on the seeded bed), then asserts the VANILLA
 * verdict. The seats are RED BY CONTRACT on unfixed develop (the vanilla
 * answer asserted); the controls are GREEN on develop (the placement path
 * itself live — the fix's regression pins).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BedPlacementFloorParityDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "bedgate", "it", 20, 2, 20,
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

    private ItemType bedItem() {
        return net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:bed")).orElseThrow();
    }

    private void holdBed(PlayerSession player) {
        player.inventory().setSlot(0, ItemStack.of(bedItem(), 1));
        player.inventory().selectHotbarSlot(0);
    }

    /** The player looks SOUTH (yaw 0 → the head cell sits at +Z). */
    private void lookSouth(PlayerSession player) {
        player.applyMovement(player.position(),
                new net.zaminmc.torch.util.Rotation(0.0f, 0.0f), true);
    }

    /** A free anchor two blocks east of the player, one below the feet. */
    private BlockPosition anchorNear(PlayerSession player) {
        return new BlockPosition(
                (int) Math.floor(player.position().x()) + 2,
                (int) Math.floor(player.position().y()) - 1,
                (int) Math.floor(player.position().z()));
    }

    private void seed(BlockPosition at, net.zaminmc.torch.block.BlockType type)
            throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, type));
        await(() -> server.world().getBlock(at).equals(type), "the bed cell seeded");
    }

    /** Clears a cell to air (the deterministic placement surface). */
    private void clear(BlockPosition at) throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, server.world().airType()));
        await(() -> server.world().getBlock(at).equals(server.world().airType()),
                "the cell cleared");
    }

    private static boolean isBedHalfAt(EngineServer server, BlockPosition at) {
        return EngineServer.isBedHalf(server.world().getBlock(at));
    }

    // ------------------------------------------------------------------
    // The both-halves floor gate — BedItem.useOn line 42
    // ------------------------------------------------------------------

    /**
     * A bed clicked onto the top face of GLASS (the foot's cell stands on
     * glass; the head's on stone) refuses in vanilla (line 42's FIRST
     * hasSolidTop — GLASS is translucent, Material.java line 22) and
     * PLACES in the engine (placeBedOnTick carries NO floor consult). The
     * vanilla verdict: no bed, the item unspent.
     */
    @Test
    void theBedRefusesWhenTheFootCellSitsOnGlass() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("BedBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition footBed = anchorNear(builder);      // the foot's support
            BlockPosition headBed = footBed.offset(0, 0, 1);  // the head's support
            BlockPosition foot = footBed.offset(0, 1, 0);     // the foot cell
            BlockPosition head = foot.offset(0, 0, 1);        // the head cell (look S)
            seed(footBed, BuiltinBlocks.GLASS);
            seed(headBed, BuiltinBlocks.STONE);
            clear(foot);
            clear(head);
            holdBed(builder);
            lookSouth(builder);
            server.useItemOnBlock(builder, footBed, 1, java.util.Optional.empty(),
                    windowId -> { });
            Thread.sleep(600); // the settle window past, the vanilla verdict stands
            assertTrue(!isBedHalfAt(server, foot) && !isBedHalfAt(server, head),
                    "no bed on the glass foot (BedItem line 42 — hasSolidTop(pos.down()) refuses GLASS)");
            assertEquals(1, builder.inventory().held().count(),
                    "the bed item unspent (the refused use never consumes)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * A bed clicked onto the top face of STONE with GLASS under the head's
     * cell refuses in vanilla (line 42's SECOND hasSolidTop) and PLACES in
     * the engine. The head-half isolation row.
     */
    @Test
    void theBedRefusesWhenTheHeadCellSitsOnGlass() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("BedBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition footBed = anchorNear(builder);
            BlockPosition headBed = footBed.offset(0, 0, 1);
            BlockPosition foot = footBed.offset(0, 1, 0);
            BlockPosition head = foot.offset(0, 0, 1);
            seed(footBed, BuiltinBlocks.STONE);
            seed(headBed, BuiltinBlocks.GLASS);
            clear(foot);
            clear(head);
            holdBed(builder);
            lookSouth(builder);
            server.useItemOnBlock(builder, footBed, 1, java.util.Optional.empty(),
                    windowId -> { });
            Thread.sleep(600);
            assertTrue(!isBedHalfAt(server, foot) && !isBedHalfAt(server, head),
                    "no bed whose head sits on glass (BedItem line 42 — hasSolidTop(blockpos.down()) refuses GLASS)");
            assertEquals(1, builder.inventory().held().count(),
                    "the bed item unspent (the refused use never consumes)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * THE FLOATING ROW: a bed clicked onto the top face of a floating
     * GLASS block (air on every side) refuses in vanilla (BOTH line-42
     * consults false — glass under the foot, AIR under the head) and
     * PLACES in the engine — the pair standing on nothing. The census's
     * starkest row ("a bed places on AIR in the engine").
     */
    @Test
    void theBedRefusesWhenTheWholePairFloats() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("BedBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition footBed = anchorNear(builder);      // floating GLASS
            BlockPosition headBed = footBed.offset(0, 0, 1);  // AIR (nothing seeded)
            BlockPosition foot = footBed.offset(0, 1, 0);
            BlockPosition head = foot.offset(0, 0, 1);
            seed(footBed, BuiltinBlocks.GLASS);
            clear(foot);
            clear(head);
            holdBed(builder);
            lookSouth(builder);
            server.useItemOnBlock(builder, footBed, 1, java.util.Optional.empty(),
                    windowId -> { });
            Thread.sleep(600);
            assertTrue(!isBedHalfAt(server, foot) && !isBedHalfAt(server, head),
                    "no floating bed (BedItem line 42 — both hasSolidTop consults refuse: GLASS and AIR)");
            assertEquals(1, builder.inventory().held().count(),
                    "the bed item unspent (the refused use never consumes)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * A bed clicked onto the top face of a BOTTOM SLAB (stone under the
     * head) refuses in vanilla (hasSolidTop's slab arm requires
     * HALF==TOP — World.java lines 1740-1752 arm 3) and PLACES in the
     * engine (no half awareness — no consult at all). The half-class row.
     */
    @Test
    void theBedRefusesWhenTheFootCellSitsOnABottomSlab() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("BedBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition footBed = anchorNear(builder);
            BlockPosition headBed = footBed.offset(0, 0, 1);
            BlockPosition foot = footBed.offset(0, 1, 0);
            BlockPosition head = foot.offset(0, 0, 1);
            seed(footBed, server.blockRegistry()
                    .lookup(net.zaminmc.torch.util.Identifier.parse("minecraft:stone_slab"))
                    .orElseThrow());
            seed(headBed, BuiltinBlocks.STONE);
            clear(foot);
            clear(head);
            holdBed(builder);
            lookSouth(builder);
            server.useItemOnBlock(builder, footBed, 1, java.util.Optional.empty(),
                    windowId -> { });
            Thread.sleep(600);
            assertTrue(!isBedHalfAt(server, foot) && !isBedHalfAt(server, head),
                    "no bed on the bottom slab (hasSolidTop's slab arm requires HALF==TOP)");
            assertEquals(1, builder.inventory().held().count(),
                    "the bed item unspent (the refused use never consumes)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The UP-face gate — BedItem.useOn lines 24-26
    // ------------------------------------------------------------------

    /**
     * A bed clicked onto the SOUTH face of a block refuses OUTRIGHT in
     * vanilla (lines 24-26: {@code face != UP → return false} — the use
     * fails before any placement consult) and PLACES SIDEWAYS in the
     * engine (placeBedOnTick computes target = offsetByFace(clicked,
     * face) with NO face gate — the pair lands sideways of the clicked
     * block, floating). The face-gate row.
     */
    @Test
    void theBedRefusesASideFaceClickEntirely() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("BedBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition clicked = anchorNear(builder);      // a floating stone block
            BlockPosition side = clicked.offset(0, 0, 1);     // the SOUTH-side target
            BlockPosition head = side.offset(0, 0, 1);
            seed(clicked, BuiltinBlocks.STONE);
            clear(side);
            clear(head);
            holdBed(builder);
            lookSouth(builder);
            server.useItemOnBlock(builder, clicked, 3, java.util.Optional.empty(),
                    windowId -> { });
            Thread.sleep(600);
            assertTrue(!isBedHalfAt(server, side) && !isBedHalfAt(server, head),
                    "no sideways bed (BedItem lines 24-26 — a non-UP face fails the use outright)");
            assertEquals(1, builder.inventory().held().count(),
                    "the bed item unspent (the face-refused use never consumes)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The controls (the placement path live on develop — the fix's pins)
    // ------------------------------------------------------------------

    /**
     * CONTROL (the agreeing row): the same click onto the top face of
     * STONE with STONE under the head's cell places the pair on BOTH
     * trees — the foot at the target, the head at +Z (the look), the
     * item spent. The placement path's liveness and the fix's regression
     * pin: the agreeing rows must keep placing.
     */
    @Test
    void theBedPlacesOnDoubleStoneFloors() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("BedBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition footBed = anchorNear(builder);
            BlockPosition headBed = footBed.offset(0, 0, 1);
            BlockPosition foot = footBed.offset(0, 1, 0);
            BlockPosition head = foot.offset(0, 0, 1);
            seed(footBed, BuiltinBlocks.STONE);
            seed(headBed, BuiltinBlocks.STONE);
            clear(foot);
            clear(head);
            holdBed(builder);
            lookSouth(builder);
            server.useItemOnBlock(builder, footBed, 1, java.util.Optional.empty(),
                    windowId -> { });
            await(() -> isBedHalfAt(server, foot) && isBedHalfAt(server, head),
                    "the bed lands on the stone floors (both trees — the agreeing row)");
            assertEquals(0, builder.inventory().held().count(),
                    "the bed item spent (both trees)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * CONTROL (the engine's own existing gate, live on both trees): a
     * bed whose HEAD CELL is blocked (stone at the head cell) refuses on
     * BOTH trees — vanilla's {@code flag3} (isAir/replaceable) and the
     * engine's headroom check agree. The refusal semantics work when the
     * engine HAS the gate — pinning the divergence to the missing floor
     * and face gates alone.
     */
    @Test
    void theBedRefusesWhenTheHeadCellIsBlocked() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("BedBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition footBed = anchorNear(builder);
            BlockPosition headBed = footBed.offset(0, 0, 1);
            BlockPosition foot = footBed.offset(0, 1, 0);
            BlockPosition head = foot.offset(0, 0, 1);
            seed(footBed, BuiltinBlocks.STONE);
            seed(headBed, BuiltinBlocks.STONE);
            seed(head, BuiltinBlocks.STONE); // the head cell blocked
            clear(foot); // the foot cell free — the head cell STAYS blocked
            holdBed(builder);
            lookSouth(builder);
            server.useItemOnBlock(builder, footBed, 1, java.util.Optional.empty(),
                    windowId -> { });
            Thread.sleep(600);
            assertTrue(!isBedHalfAt(server, foot),
                    "no foot when the head cell is blocked (both trees — the headroom refusal)");
            assertEquals(1, builder.inventory().held().count(),
                    "the bed item unspent (the headroom refusal)");
        } finally {
            server.shutdown(() -> { });
        }
    }
}
