package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.CocoaBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.block.WoodDoorFamily;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.sign.SignManager;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * THE PLACEMENT HANDLER'S OWN GATES (ServerPlayNetworkHandler.handlePlayerUse
 * lines 460-515, 1:1 — Slice N7): the reference's C08 is ONE handler with
 * ONE band — the 64.0 body consult chain (Slice N2, the walk's head) — and
 * every placement rides it ALONE: BlockItem.useOn carries NO distance gate
 * of its own (verified against reference/1.8.8/net/minecraft/item/BlockItem
 * .java: canBeReplaced, canUseItemOn, canPlace — never a reach), and neither
 * do the item-specific arms (EnderEyeItem, LilyPadItem, DyeItem's cocoa,
 * the hangings, the rails, the vehicles).
 *
 * <p>THE REAL BUG THIS SLICE REPAIRS: the engine carried ~38 secondary
 * 4.5-eye bands across the use walk's placement families plus the service's
 * two placement-walk bands (6.0 creative / 4.5 survival) — a placement at
 * five-to-eight blocks (inside the handler's 64.0, outside the 4.5 eye
 * band) was REFUSED by the engine and ALLOWED by vanilla, the same bug
 * class N6 repaired for the dig. THE FIX IS A REMOVAL, not a re-banding:
 * the secondary bands retire arm by arm; the handler's 64.0 stays the only
 * law. THE BODY RULE stays (the reference's World.canPlace entity consult):
 * a block WITH a collision box never places inside any body.</p>
 *
 * <p>THE GEOMETRY: the player stands at {@code (2.5, 5, 2.5)} (INSIDE_EDGE),
 * clicks the wall at {@code (8, 5, 2)} — feet-to-clicked-centre d²
 * 36.25 &lt; 64 (inside the handler's band), eye-to-target-centre d²
 * 26.25 &gt; 20.25 (outside the retired 4.5 eye band): every placement
 * below was refused before the slice and places now.</p>
 */
class PlacementHandlerGatesAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private PlayerSession player;

    /** The inside-edge stand: 6 blocks out on X — beyond the old eye band, well inside the 64.0 body band. */
    private static final Position INSIDE_EDGE = new Position(2.5, 5.0, 2.5);
    /** The clicked wall cell: 6 blocks east of the stand. */
    private static final BlockPosition WALL = new BlockPosition(8, 5, 2);
    /** The face-4 (west) offset cell: where the placed block lands. */
    private static final BlockPosition TARGET = new BlockPosition(7, 5, 2);
    /** The far stand: ~28 blocks from the wall — beyond the handler's 64.0, the law that STAYS. */
    private static final Position FAR_AWAY = new Position(0.5, 5.0, 30.5);

    @AfterEach
    void stop() {
        if (server != null) {
            server.shutdown(null);
        }
    }

    private void boot(GameMode mode) throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "place" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                mode, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    private PlayerSession join(String name) {
        List<String> sink = new ArrayList<>();
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
            @Override public void blockResync(net.zaminmc.torch.block.BlockPosition position,
                    net.zaminmc.torch.block.BlockType type) {
                sink.add(position + "=" + type.identifier());
            }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** Stands the player at the given position and lets the movement land. */
    private void standAt(Position at) throws InterruptedException {
        server.ticker().submit(() ->
                player.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true));
        await(() -> Math.abs(player.position().x() - at.x()) < 0.01
                        && Math.abs(player.position().z() - at.z()) < 0.01,
                "the player stood at " + at);
        EngineAwaits.awaitTicks(server.world(), 4, "the movement settles before the use submissions");
    }

    /** Seeds a block on the tick lane (the test's own authoritative commit). */
    private void seed(BlockPosition at, net.zaminmc.torch.block.BlockType type)
            throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, type));
        await(() -> server.world().getBlock(at).equals(type), "the seed landed at " + at);
    }

    /** Clears cells back to air between the arms of one test. */
    private void clear(BlockPosition... cells) throws InterruptedException {
        server.ticker().submit(() -> {
            for (BlockPosition c : cells) {
                server.world().setBlock(c, server.world().airType());
            }
        });
        for (BlockPosition c : cells) {
            BlockPosition cell = c;
            await(() -> server.world().getBlock(cell).equals(server.world().airType()),
                    "the cell cleared at " + cell);
        }
    }

    /** Gives the player a held stack on the tick lane. */
    private void hold(ItemStack stack) throws InterruptedException {
        server.ticker().submit(() -> {
            player.inventory().setSlot(0, stack);
            player.inventory().selectHotbarSlot(0);
        });
        await(() -> !player.inventory().held().isEmpty()
                        && player.inventory().held().type().equals(stack.type()),
                "the hand holds " + stack.type().identifier());
    }

    /** The survival use drive (empty claimed block — the authoritative inventory places). */
    private void use(BlockPosition at, int face) {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(), windowId -> { });
    }

    /** The creative use drive (the client-claimed block places). */
    private void useClaiming(BlockPosition at, int face, net.zaminmc.torch.block.BlockType claimed) {
        server.useItemOnBlock(player, at, face, java.util.Optional.of(claimed), windowId -> { });
    }

    /**
     * THE BAND WIDENING'S OWN EVIDENCE (survival): a stone placement at
     * six blocks out — the old per-arm 4.5-eye band and the service's
     * 4.5 survival band both refused it; the reference's 64.0 body band
     * places it, and the stack pays one unit.
     */
    @Test
    void theInsideEdgeSurvivalPlacementPlacesWhatTheOldEyeBandRefused() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("Placer");
        standAt(INSIDE_EDGE);
        seed(WALL, BuiltinBlocks.STONE);
        hold(ItemStack.of(BuiltinItems.STONE, 5));

        use(WALL, 4);
        await(() -> server.world().getBlock(TARGET).equals(BuiltinBlocks.STONE),
                "the stone landed at six blocks out");
        await(() -> player.inventory().held().count() == 4,
                "the placement paid one stone");
    }

    /** The creative twin: the client-claimed stone rides the same 64.0 alone —
     *  the authoritative hand carries the stack (the reference's own law: the
     *  item stage reads the inventory's selected slot, which the creative
     *  client's Set Creative Slot populates). */
    @Test
    void theInsideEdgeCreativePlacementPlacesWhatTheOldEyeBandRefused() throws Exception {
        boot(GameMode.CREATIVE);
        player = join("Creator");
        standAt(INSIDE_EDGE);
        seed(WALL, BuiltinBlocks.STONE);
        hold(ItemStack.of(BuiltinItems.STONE, 64));

        useClaiming(WALL, 4, BuiltinBlocks.STONE);
        await(() -> server.world().getBlock(TARGET).equals(BuiltinBlocks.STONE),
                "the claimed stone landed at six blocks out");
        assertEquals(64, player.inventory().held().count(),
                "the creative placement paid nothing");
    }

    /**
     * THE SERVICE'S OWN WALKS (the two retired bands): submitSurvivalPlace's
     * old 4.5 band and submitPlace's old 6.0 band both refused the six-block
     * placement; both walks place now — the handler's band is their only law.
     */
    @Test
    void theServiceWalksPlaceAtTheInsideEdge() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("Walker");
        standAt(INSIDE_EDGE);
        seed(WALL, BuiltinBlocks.STONE);

        // The survival walk (the old withinSurvivalReach 4.5 band retired).
        hold(ItemStack.of(BuiltinItems.STONE, 5));
        server.blockInteraction().submitSurvivalPlace(player, WALL, 4);
        await(() -> server.world().getBlock(TARGET).equals(BuiltinBlocks.STONE),
                "the survival walk placed at six blocks out");

        // The creative-claim walk (the old withinReach 6.0 band retired).
        clear(TARGET);
        server.blockInteraction().submitPlace(player, WALL, 4, BuiltinBlocks.COBBLESTONE);
        await(() -> server.world().getBlock(TARGET).equals(BuiltinBlocks.COBBLESTONE),
                "the claimed walk placed at six blocks out");
    }

    /**
     * THE RETIRED OR-PAIR ARMS (the sign's, the rail's and the door's old
     * {@code distance > 4.5 || intersectsPlayer} guards — the reach clause
     * retired, the body rule stays): all three families place at six
     * blocks out.
     */
    @Test
    void theRetiredPairBandArmsPlaceAtTheInsideEdge() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("Families");
        standAt(INSIDE_EDGE);
        seed(WALL, BuiltinBlocks.STONE);

        // The sign (the 4.5 clause retired from its OR-pair guard).
        clear(TARGET);
        hold(ItemStack.of(BuiltinItems.SIGN, 3));
        use(WALL, 4);
        await(() -> SignManager.isSignType(server.world().getBlock(TARGET)),
                "the wall sign hung at six blocks out");

        // The rail (the rails family's OR-pair guard).
        clear(TARGET);
        hold(ItemStack.of(BuiltinItems.RAIL, 3));
        use(WALL, 4);
        await(() -> WorldSolidity.isRail(server.world().getBlock(TARGET).identifier()),
                "the rail laid at six blocks out");

        // The door (the two-half commit's OR-pair guard — the pair lands
        // whole): DoorItem.useOn places on the UP face only, so the click
        // rides face 1 and the pair lands on top of the wall — the retired
        // band measured the same offset cell (eye-to-target d² 36.01 > 20.25).
        clear(TARGET, TARGET.offset(0, 1, 0));
        hold(ItemStack.of(BuiltinItems.OAK_DOOR, 3));
        use(WALL, 1);
        BlockPosition doorLower = WALL.offset(0, 1, 0);
        await(() -> WoodDoorFamily.isDoor(server.world().getBlock(doorLower))
                        && !WoodDoorFamily.isUpper(server.world().getBlock(doorLower))
                        && WoodDoorFamily.isDoor(server.world().getBlock(doorLower.offset(0, 1, 0)))
                        && WoodDoorFamily.isUpper(server.world().getBlock(doorLower.offset(0, 1, 0))),
                "the door pair landed whole at six blocks out");
    }

    /**
     * THE RETIRED STANDALONE ARMS (the lever's, the button's and the
     * minecart's whole {@code distance > 4.5} guards): the mountings and
     * the vehicle all answer at six blocks out.
     */
    @Test
    void theRetiredStandaloneBandArmsPlaceAtTheInsideEdge() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("Standalone");
        standAt(INSIDE_EDGE);
        seed(WALL, BuiltinBlocks.STONE);

        // The lever (the standalone guard retired whole; face 4 = west).
        clear(TARGET);
        hold(ItemStack.of(BuiltinItems.LEVER, 3));
        use(WALL, 4);
        await(() -> server.world().getBlock(TARGET).equals(RedstoneBlocks.leverOf(2, false)),
                "the lever mounted at six blocks out");

        // The stone button (the same standalone class: the wall-mounted
        // form keyed by the clicked face — face 4 = west).
        clear(TARGET);
        hold(ItemStack.of(BuiltinItems.STONE_BUTTON, 3));
        use(WALL, 4);
        await(() -> server.world().getBlock(TARGET).equals(
                        RedstoneBlocks.buttonOf(4, false, false)),
                "the button mounted at six blocks out");

        // The minecart (the vehicle arm's standalone guard): the rail seeded
        // at the target's cell IS the clicked cell — the cart spawns on it.
        clear(TARGET);
        seed(TARGET, BuiltinBlocks.RAIL_EW);
        hold(ItemStack.of(BuiltinItems.MINECART, 1));
        use(TARGET, 1);
        await(() -> !server.vehicles().all().isEmpty(),
                "the minecart spawned on the rail at six blocks out");
        assertEquals(net.zaminmc.torch.entity.VehicleKind.MINECART,
                server.vehicles().all().get(0).kind().apiKind,
                "the spawned body carries the minecart kind");
    }

    /**
     * THE SLAB DOUBLE-MERGE ARM (the retired clicked-cell band — the
     * old guard measured the CLICKED slab from the eye and refused the
     * merge at six blocks): the same-family click doubles the clicked
     * slab, exactly like the reference's ItemSlab walk.
     */
    @Test
    void theSlabDoubleMergeAtTheInsideEdge() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("Merger");
        standAt(INSIDE_EDGE);
        seed(WALL, BuiltinBlocks.STONE_SLAB);
        hold(ItemStack.of(BuiltinItems.STONE_SLAB, 3));

        use(WALL, 4);
        await(() -> server.world().getBlock(WALL).equals(BuiltinBlocks.DOUBLE_STONE_SLAB),
                "the clicked slab doubled at six blocks out");
    }

    /**
     * THE RETIRED CONJUNCT ARMS (the three positive {@code && distance
     * <= 4.5} gates): the ender eye's frame insert, the lily pad's
     * surface landing and the cocoa pod's planting all answer at six
     * blocks out.
     */
    @Test
    void theConjunctArmsPlaceAtTheInsideEdge() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("Conjuncts");
        standAt(INSIDE_EDGE);

        // The ender eye (EnderEyeItem.useOn — no distance in the reference):
        // the empty frame takes the eye at six blocks out.
        seed(WALL, BuiltinBlocks.END_PORTAL_FRAME_E);
        hold(ItemStack.of(BuiltinItems.ENDER_EYE, 2));
        use(WALL, 4);
        await(() -> server.world().getBlock(WALL)
                        .equals(BuiltinBlocks.END_PORTAL_FRAME_EYE_E),
                "the frame took the eye at six blocks out");
        await(() -> player.inventory().held().count() == 1,
                "the eye's insert paid one unit");

        // The lily pad (LilyPadItem — no distance in the reference): the
        // pad lands on the water surface cell at six blocks out.
        server.ticker().submit(() -> server.world().setBlock(WALL,
                FluidBlocks.sourceOf(FluidBlocks.Kind.WATER)));
        await(() -> FluidBlocks.kindOf(server.world().getBlock(WALL).identifier())
                        == FluidBlocks.Kind.WATER, "the water seeded");
        hold(ItemStack.of(BuiltinItems.WATERLILY, 2));
        use(WALL, 1);
        await(() -> server.world().getBlock(WALL.offset(0, 1, 0))
                        .equals(BuiltinBlocks.WATERLILY),
                "the lily pad landed at six blocks out");

        // The cocoa pod (DyeItem.useOn's brown arm — no distance in the
        // reference): the age-0 pod plants on the jungle log's side.
        server.ticker().submit(() -> server.world().setBlock(WALL,
                server.world().blockRegistry().require(CocoaBlocks.JUNGLE_LOG)));
        await(() -> server.world().getBlock(WALL).identifier()
                        .equals(CocoaBlocks.JUNGLE_LOG), "the jungle log seeded");
        clear(TARGET);
        hold(ItemStack.of(BuiltinItems.INK_SAC, 2).withDamage(
                CocoaBlocks.BROWN_DYE_DAMAGE));
        use(WALL, 4);
        await(() -> server.world().getBlock(TARGET)
                        .equals(CocoaBlocks.placementState(4)),
                "the cocoa pod planted at six blocks out");
    }

    /**
     * THE LAW THAT STAYS: the handler's own 64.0 body band (Slice N2) —
     * a placement from ~28 blocks out answers NOTHING (the head consult
     * refuses before any arm runs); the removal never touched the head.
     */
    @Test
    void theBeyondEightBlocksPlacementStillRefuses() throws Exception {
        boot(GameMode.SURVIVAL);
        player = join("FarAway");
        seed(WALL, BuiltinBlocks.STONE);
        standAt(FAR_AWAY);
        hold(ItemStack.of(BuiltinItems.STONE, 5));

        use(WALL, 4);
        EngineAwaits.awaitTicks(server.world(), 8, "engine progress (a 400 wall settle)");
        assertTrue(server.world().getBlock(TARGET).equals(server.world().airType()),
                "the beyond-eight-blocks placement still refuses");
        assertEquals(5, player.inventory().held().count(),
                "the refused placement paid nothing");
    }
}
