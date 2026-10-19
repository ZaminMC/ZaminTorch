package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.TrapdoorBlocks;
import net.zaminmc.torch.server.block.WoodDoorFamily;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE TWO-STAGE USE LAW (ServerPlayerInteractionManager.useBlock lines
 * 259-296, 1:1): the block's use arm runs FIRST and consumes the click
 * (the held item's useOn never runs); the SNEAKING USE-BYPASS skips the
 * whole block stage when the player sneaks with an item in hand (the
 * chest stays shut, the door stands still, the item places); an empty
 * hand keeps the block stage and skips the item stage.
 *
 * <p>THE ORDERING REPAIRS the two-stage split lands: the engine's old
 * single chain ran the bottle/fireworks/flint item arms BEFORE the late
 * block arms (the repeater, the lever, the door, the gate, the trapdoor)
 * — a bottle's early-return ate clicks the reference answers with the
 * BLOCK's use (a door swung for a bottle in vanilla, stood still in the
 * engine). The block stage now precedes every item arm.</p>
 */
class TwoStageUseLawAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private PlayerSession player;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "use" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, "Clicker",
                UUID.nameUUIDFromBytes("Clicker".getBytes()));
        player = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(player);
        player.applyMovement(new Position(0.5, 5.0, 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true);
        await(() -> player.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "the player joined");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** Places a block on the tick lane and waits for it. */
    private void place(BlockPosition at, net.zaminmc.torch.block.BlockType type)
            throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, type));
        await(() -> server.world().getBlock(at).equals(type), "the block landed at " + at);
    }

    /** Gives the player a held stack on the tick lane. */
    private void hold(ItemStack stack) throws InterruptedException {
        server.ticker().submit(() -> {
            player.inventory().setSlot(0, stack);
            player.inventory().selectHotbarSlot(0);
        });
        await(() -> stack.isEmpty()
                        ? player.inventory().held().isEmpty()
                        : !player.inventory().held().isEmpty(),
                "the hand holds " + (stack.isEmpty() ? "nothing" : stack.type().identifier()));
    }

    private void use(BlockPosition at, int face) {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(),
                windowId -> { });
    }

    /**
     * Seeds an oak door pair the way the engine's own placement commits it
     * (the pair-walk shape): the lower and the upper in ONE tick-thread
     * submit — the redstone neighbor arm runs after the pair is whole.
     */
    private void seedDoorPair(BlockPosition lower, BlockPosition upper)
            throws InterruptedException {
        server.ticker().submit(() -> {
            server.world().setBlock(lower, BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_W);
            server.world().setBlock(upper, BuiltinBlocks.OAK_DOOR_UPPER);
        });
        await(() -> server.world().getBlock(lower)
                .equals(BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_W)
                && server.world().getBlock(upper)
                .equals(BuiltinBlocks.OAK_DOOR_UPPER), "the door pair landed");
    }

    /**
     * THE BYPASS ITSELF: a sneaking player with a stone in hand clicks a
     * chest — the chest stays shut (no window) and the stone PLACES
     * against the chest's face; unsneaked, the chest opens and the stone
     * never places (the block's use consumed the click).
     */
    @Test
    void theSneakingUseBypassHoldsTheChestShut() throws Exception {
        BlockPosition chestAt = new BlockPosition(2, 5, 0);
        place(chestAt, BuiltinBlocks.CHEST);
        BlockPosition beside = new BlockPosition(1, 5, 0);
        hold(ItemStack.of(BuiltinItems.STONE, 5));

        // Sneaking: the bypass skips the chest's use — the item places.
        server.entityAction(player, 0); // start sneaking
        Thread.sleep(200);
        use(chestAt, 4); // the west face: the stone targets (1, 5, 0)
        await(() -> server.world().getBlock(beside)
                .equals(BuiltinBlocks.STONE), "the sneaked stone placed");
        assertEquals(PlayerSession.ContainerKind.NONE, player.openContainerKind(),
                "the sneaked click never opened the chest");

        // Unsneaked: the chest's use consumes the click — no placement.
        server.entityAction(player, 1); // stop sneaking
        server.ticker().submit(() -> server.world().setBlock(beside,
                BuiltinBlocks.AIR));
        await(() -> server.world().getBlock(beside).equals(BuiltinBlocks.AIR),
                "the placed stone cleared");
        use(chestAt, 4);
        await(() -> player.openContainerKind() == PlayerSession.ContainerKind.CHEST,
                "the chest opened for the unsneaked click");
        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(beside),
                "the consumed click never placed the stone");
    }

    /**
     * THE EMPTY-HAND EXCEPTION (the reference's own
     * {@code getDisplayItemInHand() == null} clause): a sneaking player
     * with an EMPTY hand still reaches the block's use — the chest opens.
     */
    @Test
    void theEmptyHandSneakStillOpensTheChest() throws Exception {
        BlockPosition chestAt = new BlockPosition(2, 5, 0);
        place(chestAt, BuiltinBlocks.CHEST);
        hold(ItemStack.EMPTY);
        server.entityAction(player, 0); // start sneaking
        Thread.sleep(200);
        use(chestAt, 4);
        await(() -> player.openContainerKind() == PlayerSession.ContainerKind.CHEST,
                "the empty-handed sneak still opens the chest");
    }

    /**
     * THE CONSUMED BLOCK USE (the reference's {@code block.use -> true}):
     * a stone in hand, unsneaked, against a crafting table — the table
     * opens and the stone NEVER places (the item stage never runs).
     */
    @Test
    void theBlocksUseConsumesTheClickBeforeTheItem() throws Exception {
        BlockPosition tableAt = new BlockPosition(2, 5, 0);
        place(tableAt, BuiltinBlocks.CRAFTING_TABLE);
        hold(ItemStack.of(BuiltinItems.STONE, 5));
        use(tableAt, 4);
        await(() -> player.openContainerKind()
                == PlayerSession.ContainerKind.CRAFTING_TABLE,
                "the crafting table opened");
        BlockPosition beside = new BlockPosition(1, 5, 0);
        await(() -> server.world().getBlock(beside).equals(BuiltinBlocks.AIR),
                "the consumed click never placed");
    }

    /**
     * THE ORDERING REPAIR (the two-stage split's own class): the engine's
     * old chain ran the bottle and fireworks item arms BEFORE the door's
     * block arm — a bottle's early-return ate the click and the door
     * stood still. The reference answers with the DOOR: the block's use
     * runs first for both the bottle and the fireworks (and by
     * construction the launch/fill arms never ran), and the sneaking
     * bypass stands the door still for a held stone while the stone
     * places beside it.
     */
    @Test
    void theDoorSwingsForTheBottleAndTheFireworks() throws Exception {
        BlockPosition lower = new BlockPosition(2, 5, 0);
        BlockPosition upper = new BlockPosition(2, 6, 0);
        // The pair commits in ONE tick submit (the engine's own pair-walk
        // shape — a lone lower pops in the redstone neighbor arm before
        // the upper lands, exactly like the double-plant pair lesson).
        seedDoorPair(lower, upper);

        // The bottle in hand, unsneaked: the door swings (the reference's
        // own order — the old engine did nothing: the bottle ate the click).
        hold(ItemStack.of(BuiltinItems.GLASS_BOTTLE, 1));
        use(lower, 4);
        await(() -> WoodDoorFamily.isOpen(server.world().getBlock(lower)),
                "the door swung for the bottle");

        // Close it again, then the fireworks: the door swings, the rocket
        // arm never ran (the block's use consumed the click).
        seedDoorPair(lower, upper);
        hold(ItemStack.of(BuiltinItems.FIREWORKS, 3));
        use(lower, 4);
        await(() -> WoodDoorFamily.isOpen(server.world().getBlock(lower)),
                "the door swung for the fireworks (the launch never ran)");

        // Sneaking with a stone: the door stands still, the stone places.
        seedDoorPair(lower, upper);
        hold(ItemStack.of(BuiltinItems.STONE, 5));
        server.entityAction(player, 0); // start sneaking
        Thread.sleep(200);
        use(lower, 4);
        BlockPosition beside = new BlockPosition(1, 5, 0);
        await(() -> server.world().getBlock(beside).equals(BuiltinBlocks.STONE),
                "the sneaked stone placed beside the door");
        assertTrue(!WoodDoorFamily.isOpen(server.world().getBlock(lower)),
                "the sneaked click never swung the door");
    }

    /**
     * THE REPEATER'S CYCLE for a held bottle — the same ordering class
     * over the redstone family: the delay cycles (1 -> 2) because the
     * block's use precedes the bottle's arm.
     */
    @Test
    void theRepeaterCyclesForTheBottle() throws Exception {
        BlockPosition repeaterAt = new BlockPosition(2, 5, 0);
        place(repeaterAt, RedstoneBlocks.repeaterOf(0, 1, false));
        hold(ItemStack.of(BuiltinItems.GLASS_BOTTLE, 1));
        use(repeaterAt, 4);
        await(() -> RedstoneBlocks.repeaterDelay(server.world().getBlock(repeaterAt)) == 2,
                "the repeater's delay cycled to 2 for the bottle");
    }

    /**
     * THE ORE'S SNEAKING GATE (RedstoneOreBlock.use -> super.use — the
     * block's use does NOT consume): unsneaked with a stone, the click
     * lights the ore AND the stone places (the fall-through); sneaked,
     * the bypass skips the block stage — the ore stays dark while the
     * stone still places.
     */
    @Test
    void theOreLightsOnlyForTheUnsneaked() throws Exception {
        BlockPosition oreAt = new BlockPosition(2, 5, 0);
        place(oreAt, BuiltinBlocks.REDSTONE_ORE);
        hold(ItemStack.of(BuiltinItems.STONE, 5));

        // Unsneaked: the ore lights AND the stone places (super.use false).
        use(oreAt, 4);
        await(() -> server.world().getBlock(oreAt)
                .equals(BuiltinBlocks.LIT_REDSTONE_ORE), "the ore lit");
        BlockPosition beside = new BlockPosition(1, 5, 0);
        await(() -> server.world().getBlock(beside).equals(BuiltinBlocks.STONE),
                "the fall-through placed the stone");

        // Sneaked: the bypass skips the block stage — the ore stays dark.
        BlockPosition oreTwo = new BlockPosition(2, 5, 2);
        place(oreTwo, BuiltinBlocks.REDSTONE_ORE);
        server.ticker().submit(() -> server.world().setBlock(beside,
                BuiltinBlocks.AIR));
        server.entityAction(player, 0); // start sneaking
        Thread.sleep(200);
        use(oreTwo, 1); // the UP face: the stone targets the cell above
        await(() -> server.world().getBlock(new BlockPosition(2, 6, 2))
                .equals(BuiltinBlocks.STONE), "the sneaked stone placed above");
        assertTrue(server.world().getBlock(oreTwo).equals(BuiltinBlocks.REDSTONE_ORE),
                "the sneaked click never lit the ore");
    }

    /**
     * THE NOTE BLOCK'S TUNE: the sneaked click never tunes (the stat
     * stays zero — the bypass skipped the block's use); the unsneaked
     * click tunes (the stat increments) even with a stone in hand.
     */
    @Test
    void theNoteBlockTunesOnlyForTheUnsneaked() throws Exception {
        BlockPosition noteAt = new BlockPosition(2, 5, 0);
        place(noteAt, BuiltinBlocks.NOTE_BLOCK);
        hold(ItemStack.of(BuiltinItems.STONE, 5));

        server.entityAction(player, 0); // start sneaking
        Thread.sleep(200);
        use(noteAt, 4);
        Thread.sleep(300);
        assertEquals(0, player.stats().get(
                        net.zaminmc.torch.server.stat.EngineStats.NOTE_BLOCKS_TUNED),
                "the sneaked click never tuned the note block");

        server.entityAction(player, 1); // stop sneaking
        Thread.sleep(200);
        use(noteAt, 4);
        await(() -> player.stats().get(
                net.zaminmc.torch.server.stat.EngineStats.NOTE_BLOCKS_TUNED) == 1,
                "the unsneaked click tuned the note block");
    }

    /**
     * THE HANDLER'S 8-BLOCK BODY GATE (handlePlayerUse lines 463-472:
     * {@code squaredDistanceTo(x + 0.5, y + 0.5, z + 0.5) < 64.0} from
     * the BODY's feet — never the eyes): a chest clicked from beyond
     * eight blocks stays shut; the same chest clicked from inside opens.
     */
    @Test
    void theBeyondEightBlocksUseRefuses() throws Exception {
        BlockPosition chestAt = new BlockPosition(2, 5, 0);
        place(chestAt, BuiltinBlocks.CHEST);
        hold(ItemStack.EMPTY);

        // Beyond eight blocks: the use answers nothing.
        server.ticker().submit(() -> player.applyMovement(
                new Position(0.5, 5.0, 20.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        await(() -> player.position().z() > 19.0, "the player walked away");
        use(chestAt, 4);
        Thread.sleep(300);
        assertEquals(PlayerSession.ContainerKind.NONE, player.openContainerKind(),
                "the too-far use never opened the chest");

        // Back inside eight blocks: the chest opens.
        server.ticker().submit(() -> player.applyMovement(
                new Position(0.5, 5.0, 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
        await(() -> player.position().z() < 2.0, "the player walked back");
        use(chestAt, 4);
        await(() -> player.openContainerKind() == PlayerSession.ContainerKind.CHEST,
                "the in-range use opened the chest");
    }

    /**
     * THE TRAPDOOR'S SWING for a held bottle (the late-block class's
     * third family): unsneaked the wooden trapdoor flips OPEN; sneaked
     * with a stone it stands closed while the stone places.
     */
    @Test
    void theTrapdoorSwingsForTheBottle() throws Exception {
        BlockPosition trapdoorAt = new BlockPosition(2, 5, 0);
        // The mount wall behind the plain north-facing form (the wall
        // cell sits at z+1 — the trapdoor's own survival gate).
        BlockPosition wallAt = new BlockPosition(2, 5, 1);
        place(wallAt, BuiltinBlocks.STONE);
        place(trapdoorAt, TrapdoorBlocks.state("trapdoor", 0, false, false));
        hold(ItemStack.of(BuiltinItems.GLASS_BOTTLE, 1));
        use(trapdoorAt, 4);
        await(() -> TrapdoorBlocks.isOpen(server.world().getBlock(trapdoorAt)),
                "the trapdoor flipped open for the bottle");

        // Sneaked with a stone: the trapdoor stands closed, the stone places.
        place(trapdoorAt, TrapdoorBlocks.state("trapdoor", 0, false, false));
        hold(ItemStack.of(BuiltinItems.STONE, 5));
        server.entityAction(player, 0); // start sneaking
        Thread.sleep(200);
        use(trapdoorAt, 4);
        BlockPosition beside = new BlockPosition(1, 5, 0);
        await(() -> server.world().getBlock(beside).equals(BuiltinBlocks.STONE),
                "the sneaked stone placed beside the trapdoor");
        assertTrue(!TrapdoorBlocks.isOpen(server.world().getBlock(trapdoorAt)),
                "the sneaked click never flipped the trapdoor");
    }
}
