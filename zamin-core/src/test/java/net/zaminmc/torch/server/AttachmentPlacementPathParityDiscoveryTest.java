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
 * F-SP6's behavioral seats (pass 2) — the attachment family's
 * placement-path gate structure, seated per divergent row against the
 * reference's use-path law (reference/1.8.8: BlockItem.useOn lines
 * 28-68, World.canPlace lines 2274-2282, Block.canBePlaced lines
 * 656-666, LeverBlock lines 44-46/64-79, ButtonBlock lines 55-57/76-80,
 * TorchBlock lines 49-88, Direction.Plane lines 315-338).
 *
 * <p>THE LAW (the use path's TWO-STAGE contract): {@code BlockItem.useOn}
 * gates on {@code world.canPlace(block, pos, false, face, null, stack)}
 * BEFORE {@code getPlacementState} ever runs — and {@code canPlace}
 * consults the family's {@code canBePlaced(world, pos, face, item)} form.
 * The LEVER and the BUTTON override the SIDE form
 * ({@code canSurvive(world, pos, face.getOpposite())} — the CLICKED
 * face's mount law), so a failed clicked-face mount REFUSES THE USE
 * OUTRIGHT: no block, no sound, no stack decrement — and
 * {@code getPlacementState}'s own fallback layers (the lever's
 * horizontal walk + floor fallback, the button's doomed DOWN) are DEAD
 * CODE on the use path (arm 1 re-consults the law the pre-gate just
 * passed). The TORCHES override only the FIVE-DIRECTION walk
 * ({@code TorchBlock.canBePlaced} lines 59-67), so their walk-and-default
 * layers ARE live — the walk in {@code Plane.HORIZONTAL} = [NORTH, EAST,
 * SOUTH, WEST] FACING order (the mounts [pos+SOUTH, pos+WEST, pos+NORTH,
 * pos+EAST]) — and the plain wall torch carries REAL facing states
 * (legacy 50 metas 2-5).</p>
 *
 * <p>THE ENGINE'S SHAPE (the four divergences): {@code placeLeverOnTick}'s
 * floor fallback PLACES an up_z lever where vanilla refuses (L1);
 * {@code placeButtonOnTick} commits the doomed DOWN where vanilla refuses
 * (B1); {@code placeRedstoneTorchOnTick} walks its mounts
 * [S, N, E, W] where the reference walks [S, W, N, E] (RT-WALK); the
 * plain torch rides the GENERIC placement path — no consult, no facing
 * states — committing the single standing {@code minecraft:torch} whose
 * own-commit wake pops it when the floor refuses (T-WALL, T-REFUSE).</p>
 *
 * <p>THE CONSTRUCTION: every seat drives the real item-use path (the
 * item held, the click on the seeded block) then asserts the VANILLA
 * verdict. The seats are RED BY CONTRACT on unfixed develop; the two
 * controls (the valid wall lever, the standing torch) are GREEN on
 * develop — the fix's regression pins.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttachmentPlacementPathParityDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "attachpath", "it", 20, 2, 20,
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

    private ItemType item(String id) {
        return net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse(id)).orElseThrow();
    }

    private void hold(PlayerSession player, String id) {
        player.inventory().setSlot(0, ItemStack.of(item(id), 1));
        player.inventory().selectHotbarSlot(0);
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
        await(() -> server.world().getBlock(at).equals(type), "the cell seeded: " + at);
    }

    private void clear(BlockPosition at) throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, server.world().airType()));
        await(() -> server.world().getBlock(at).equals(server.world().airType()),
                "the cell cleared: " + at);
    }

    /** Clears the 3x3 column around the target (the target + its six neighbours). */
    private void clearStage(BlockPosition target) throws InterruptedException {
        clear(target);
        clear(target.offset(0, -1, 0));
        clear(target.offset(0, 1, 0));
        clear(target.offset(1, 0, 0));
        clear(target.offset(-1, 0, 0));
        clear(target.offset(0, 0, 1));
        clear(target.offset(0, 0, -1));
    }

    private void drive(PlayerSession player, BlockPosition clicked, int face)
            throws InterruptedException {
        server.useItemOnBlock(player, clicked, face, java.util.Optional.empty(),
                windowId -> { });
        Thread.sleep(600); // the settle window past, the verdict stands
    }

    private String idAt(BlockPosition at) {
        return server.world().getBlock(at).identifier().value();
    }

    // ------------------------------------------------------------------
    // Seat 1 (L1) — the lever's floor fallback places where vanilla refuses
    // ------------------------------------------------------------------

    /**
     * A lever clicked onto the EAST face of GLASS (the mount consult
     * fails — GLASS is translucent, {@code Block.isSolid()} =
     * isSolidBlocking &amp;&amp; isCube &amp;&amp; !isSignalSource refuses
     * it) with STONE under the target cell. Vanilla
     * ({@code World.canPlace} → the lever's SIDE-form
     * {@code canBePlaced} → {@code canSurvive(world, pos, WEST)} over the
     * glass) REFUSES the use outright: no lever, the item unspent. The
     * engine's floor fallback ({@code hasSolidTop(target.below)}) PLACES
     * an up_z lever that its own survival walk then keeps — RED here, the
     * parity pin riding the fix branch.
     */
    @Test
    void theLeverRefusesTheFailedMountDespiteTheFloorBelow() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("LeverBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition glass = anchorNear(builder);          // the clicked block
            BlockPosition target = glass.offset(1, 0, 0);       // east of it (face 5)
            clearStage(target);
            clear(glass);
            seed(glass, BuiltinBlocks.GLASS);
            seed(target.offset(0, -1, 0), BuiltinBlocks.STONE); // the hosting floor
            hold(builder, "minecraft:lever");
            drive(builder, glass, 5);
            assertTrue(server.world().getBlock(target).equals(server.world().airType()),
                    "no lever on the failed glass mount (World.canPlace's side consult "
                            + "refuses — the floor fallback is dead code on the use path)");
            assertEquals(1, builder.inventory().held().count(),
                    "the lever item unspent (the refused use never consumes)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 2 (B1, persistent) — the button's doomed DOWN never commits
    // ------------------------------------------------------------------

    /**
     * A stone button clicked onto the EAST face of GLASS with STONE above
     * the target (the ceiling). Vanilla's pre-gate consults the CLICKED
     * face's mount (the glass) — NOT the ceiling — and refuses: no
     * button, the item unspent. The engine commits the doomed DOWN
     * button, whose survival walk reads the CEILING above (isSolid — it
     * holds): a PERSISTENT ceiling button placed by clicking a wall —
     * RED here, the parity pin riding the fix branch.
     */
    @Test
    void theButtonNeverFallsToTheCeilingOnAFailedMount() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("ButtonBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition glass = anchorNear(builder);
            BlockPosition target = glass.offset(1, 0, 0);
            clearStage(target);
            clear(glass);
            seed(glass, BuiltinBlocks.GLASS);
            seed(target.offset(0, 1, 0), BuiltinBlocks.STONE);  // the hosting ceiling
            hold(builder, "minecraft:stone_button");
            drive(builder, glass, 5);
            assertTrue(server.world().getBlock(target).equals(server.world().airType()),
                    "no button on the failed glass mount (World.canPlace's side consult "
                            + "refuses — the doomed DOWN is dead code on the use path, "
                            + "and the ceiling was never the consulted mount)");
            assertEquals(1, builder.inventory().held().count(),
                    "the button item unspent (the refused use never consumes)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 3 (B1, the consume) — the failed button mount never consumes
    // ------------------------------------------------------------------

    /**
     * A stone button clicked onto the EAST face of GLASS with NOTHING
     * hosting anywhere (air below, above, and on every side but the
     * glass). Vanilla's pre-gate refuses: the item unspent, nothing
     * placed. The engine commits the doomed DOWN button and CONSUMES the
     * item (the button then sitting until a neighbour changes) — the
     * held-count row, RED here, the parity pin riding the fix branch.
     */
    @Test
    void theFailedButtonMountNeverConsumesTheItem() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("ButtonBuilder");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition glass = anchorNear(builder);
            BlockPosition target = glass.offset(1, 0, 0);
            clearStage(target);
            clear(glass);
            seed(glass, BuiltinBlocks.GLASS);
            hold(builder, "minecraft:stone_button");
            drive(builder, glass, 5);
            assertEquals(1, builder.inventory().held().count(),
                    "the button item unspent (vanilla's five-walk-equivalent side "
                            + "consult refuses the use before any commit or consume)");
            assertTrue(server.world().getBlock(target).equals(server.world().airType()),
                    "no button placed on the failed mount (the engine's doomed-DOWN "
                            + "commit is the divergence — vanilla places nothing)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 4 (RT-WALK) — the two-wall redstone torch picks the reference wall
    // ------------------------------------------------------------------

    /**
     * A redstone torch clicked onto the NORTH face of GLASS (arm 1 fails)
     * with hosting STONE walls both WEST and NORTH of the target. The
     * reference's walk ({@code Plane.HORIZONTAL} = [N, E, S, W] as FACING
     * — the mounts [S, W, N, E]) reaches the WEST mount second: the torch
     * hangs on the west wall pointing EAST ({@code redstone_torch_east},
     * legacy meta 2). The engine's walk ([S, N, E, W]) reaches the NORTH
     * mount second: {@code redstone_torch_south} — the OPPOSITE wall.
     * RED here, the parity pin riding the fix branch.
     */
    @Test
    void theTwoWallRedstoneTorchPicksTheReferenceWall() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("TorchWalker");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition glass = anchorNear(builder);
            BlockPosition target = glass.offset(0, 0, -1);      // north of it (face 2)
            clearStage(target);
            clear(glass);
            seed(glass, BuiltinBlocks.GLASS);                   // the failed clicked mount
            seed(target.offset(-1, 0, 0), BuiltinBlocks.STONE); // the WEST hosting wall
            seed(target.offset(0, 0, -1), BuiltinBlocks.STONE); // the NORTH hosting wall
            hold(builder, "minecraft:redstone_torch");
            drive(builder, glass, 2);
            assertEquals("redstone_torch_east", idAt(target),
                    "the two-wall walk hangs the torch on the WEST wall pointing east "
                            + "(Plane.HORIZONTAL = [N, E, S, W] as FACING — the west "
                            + "mount answers second; the engine's [S, N, E, W] order "
                            + "picks the north wall instead)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 5 (T-WALL) — the plain wall torch survives on the wall
    // ------------------------------------------------------------------

    /**
     * A torch clicked onto the EAST face of STONE (a VALID wall mount —
     * arm 1's {@code canAttach} answers) with AIR under the target.
     * Vanilla places the WALL torch (legacy 50 meta 2-5 — attached to the
     * stone, surviving with no floor anywhere). The engine has NO wall
     * states: the generic path commits the single standing
     * {@code minecraft:torch}, whose own-commit wake pops it (the floor
     * consult reads the air below) with the drop — the cell ends empty,
     * RED here, the parity pin riding the fix branch (the architectural
     * member: the wall-state vocabulary itself).
     */
    @Test
    void theWallTorchSurvivesOnTheWallWithoutAFloor() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("WallTorch");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition stone = anchorNear(builder);
            BlockPosition target = stone.offset(1, 0, 0);        // east of it (face 5)
            clearStage(target);
            clear(stone);
            seed(stone, BuiltinBlocks.STONE);                   // the valid wall mount
            hold(builder, "minecraft:torch");
            drive(builder, stone, 5);
            assertTrue(idAt(target).startsWith("torch"),
                    "the wall torch stands on the stone wall (TorchBlock.getPlacementState "
                            + "arm 1 — canAttach answers for the clicked face; the torch "
                            + "survives with NO floor anywhere. The engine's standing-form "
                            + "commit pops on the own-commit wake: "
                            + idAt(target));
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 6 (T-REFUSE) — the no-host torch click never consumes
    // ------------------------------------------------------------------

    /**
     * A torch clicked onto the EAST face of GLASS with NOTHING hosting
     * anywhere (no floor, no walls, no ceiling candidates — the glass is
     * the only neighbour and it never answers). Vanilla's five-direction
     * pre-gate refuses: the item unspent, nothing placed. The engine
     * commits the standing form, the own-commit wake pops it with the
     * drop, and the item was CONSUMED — the held-count row, RED here,
     * the parity pin riding the fix branch.
     */
    @Test
    void theNoHostTorchClickNeverConsumesTheItem() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("TorchRefuse");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition glass = anchorNear(builder);
            BlockPosition target = glass.offset(1, 0, 0);
            clearStage(target);
            clear(glass);
            seed(glass, BuiltinBlocks.GLASS);
            hold(builder, "minecraft:torch");
            drive(builder, glass, 5);
            assertEquals(1, builder.inventory().held().count(),
                    "the torch item unspent (TorchBlock.canBePlaced's five-direction "
                            + "walk finds no host — the use refuses before any commit "
                            + "or consume; the engine's commit-then-pop cycle consumes)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Control A — the VALID wall lever places (the regression pin)
    // ------------------------------------------------------------------

    /**
     * The control: a lever clicked onto the EAST face of STONE (the
     * clicked-face mount answers {@code isSolid()}) PLACES in both trees
     * — the lever pointing east, mounted on the stone, the item spent.
     * GREEN on the unfixed engine (the arm-1 path is correct); the fix's
     * regression pin.
     */
    @Test
    void theValidWallLeverPlacesOnTheStone() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("LeverControl");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition stone = anchorNear(builder);
            BlockPosition target = stone.offset(1, 0, 0);
            clearStage(target);
            clear(stone);
            seed(stone, BuiltinBlocks.STONE);
            hold(builder, "minecraft:lever");
            drive(builder, stone, 5);
            assertEquals("lever_east", idAt(target),
                    "the valid wall lever places pointing east (both trees agree — "
                            + "the arm-1 path)");
            assertEquals(0, builder.inventory().held().count(),
                    "the lever item spent (the valid placement consumes)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Control B — the standing torch places on the stone (the pin)
    // ------------------------------------------------------------------

    /**
     * The control: a torch clicked onto the TOP face of STONE (the
     * floor's {@code canSitOnTop} answers) PLACES the standing torch in
     * both trees — the item spent. GREEN on the unfixed engine; the
     * fix's regression pin (the generic path's torch placement with a
     * hosting floor).
     */
    @Test
    void theStandingTorchPlacesOnTheStone() throws Exception {
        server = boot();
        try {
            PlayerSession builder = join("TorchControl");
            await(() -> builder.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the builder reaches PLAYING");
            BlockPosition stone = anchorNear(builder);
            BlockPosition target = stone.offset(0, 1, 0);        // above it (face 1)
            clearStage(target);
            seed(stone, BuiltinBlocks.STONE);
            hold(builder, "minecraft:torch");
            drive(builder, stone, 1);
            assertEquals("torch", idAt(target),
                    "the standing torch places on the stone top (both trees agree)");
            assertEquals(0, builder.inventory().held().count(),
                    "the torch item spent (the valid placement consumes)");
        } finally {
            server.shutdown(() -> { });
        }
    }
}
