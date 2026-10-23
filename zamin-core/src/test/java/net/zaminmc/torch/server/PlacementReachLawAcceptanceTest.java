package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * THE PLACEMENT REACH LAW (Slice N7 — the secondary reach bands retired, a
 * REMOVAL not a re-banding): the reference's C08 is ONE handler with ONE
 * band — {@code squaredDistanceTo(x+0.5, y+0.5, z+0.5) < 64.0} from the
 * BODY's feet (handlePlayerUse lines 463-472) — and the
 * interactionManager.useBlock walk it guards carries NO reach consult of
 * its own (read in full: ServerPlayerInteractionManager.useBlock,
 * BlockItem.useOn, and every placement item family carry no distance
 * check). The engine's 38 inline 4.5-eye bands and the service's two
 * placement bands (6.0 creative / 4.5 survival from the 1.62 eye) were
 * the deviation: a placement at five to eight blocks — inside the
 * handler's 64.0, outside the 4.5 eye band — is REFUSED by the old
 * engine and ALLOWED by the reference. THE RETIREMENT: the bands are
 * gone; the walk head's 64.0 (N2) is the only band; the vanilla body
 * rule (the reference's own entity-collision check inside canPlace)
 * stays.
 */
class PlacementReachLawAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private PlayerSession player;
    /** The recording link: the resync pair's own observation surface. */
    private final List<String> resyncs = new ArrayList<>();

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "reach" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
            @Override public void blockResync(net.zaminmc.torch.block.BlockPosition position,
                    net.zaminmc.torch.block.BlockType type) {
                resyncs.add(position + "=" + type.identifier());
            }
        };
        var result = server.joinRequest(link, "Reach",
                UUID.nameUUIDFromBytes("Reach".getBytes()));
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

    private void place(BlockPosition at, BlockType type) throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, type));
        await(() -> server.world().getBlock(at).equals(type), "seeded " + at);
    }

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
     * THE WIDENING'S INSIDE EDGE (the survival/generic path): a stone
     * placed against the top face of a floor stone SEVEN blocks east —
     * the body d3 = 7^2 + 0.5^2 = 49.25 (inside the handler's 64.0), the
     * retired eye d3 = 7^2 + 1.12^2 = 50.25 (outside the old 4.5 band
     * that refused it). The placement OPENS (the reference's own shape)
     * and the stack pays one.
     */
    @Test
    void theSevenBlockSurvivalPlacementOpens() throws Exception {
        BlockPosition floorStone = new BlockPosition(7, 4, 0);
        place(floorStone, BuiltinBlocks.STONE);
        hold(ItemStack.of(BuiltinItems.STONE, 5));

        use(floorStone, 1); // the top face: the stone targets (7, 5, 0)
        await(() -> server.world().getBlock(new BlockPosition(7, 5, 0))
                .equals(BuiltinBlocks.STONE), "the seven-block stone placed");
        assertEquals(4, player.inventory().held().count(),
                "the placement spent one stone");
    }

    /**
     * THE INLINE BAND RETIREMENT (the family-arm path): a carpet placed
     * the same seven blocks east — the carpet family's own 4.5-eye band
     * (placeCarpetOnTick's retired arm) refused this use before; the
     * family dispatch now answers to the walk head's 64.0 alone. The red
     * carpet (damage 14) places and the stack pays one.
     */
    @Test
    void theSevenBlockCarpetFamilyPlacementOpens() throws Exception {
        BlockPosition floorStone = new BlockPosition(7, 4, 0);
        place(floorStone, BuiltinBlocks.STONE);
        BlockType redCarpet = server.blockRegistry()
                .lookup(Identifier.parse("minecraft:carpet_red")).orElseThrow();
        hold(ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems
                        .lookup(Identifier.parse("minecraft:carpet")).orElseThrow(), 4)
                .withDamage(14));

        use(floorStone, 1); // the top face: the carpet targets (7, 5, 0)
        await(() -> server.world().getBlock(new BlockPosition(7, 5, 0))
                .equals(redCarpet), "the seven-block red carpet placed");
        assertEquals(3, player.inventory().held().count(),
                "the placement spent one carpet");
    }

    /**
     * THE ONLY BAND (N2's own edge stays): a use NINE blocks east — the
     * body d3 = 9^2 + 0.5^2 = 81.25, beyond the handler's 64.0 — answers
     * NOTHING: no placement, no spend, and the anti-desync resync pair
     * still echoes the truth (the reference's flag=true band).
     */
    @Test
    void theBeyondEightBlockUseStillAnswersNothing() throws Exception {
        BlockPosition farStone = new BlockPosition(9, 4, 0);
        place(farStone, BuiltinBlocks.STONE);
        hold(ItemStack.of(BuiltinItems.STONE, 5));
        resyncs.clear();

        use(farStone, 1); // the top face: the target cell (9, 5, 0)
        EngineAwaits.awaitTicks(server.world(), 6, "engine progress (a 300 wall settle)");
        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(new BlockPosition(9, 5, 0)),
                "the beyond-band use never placed");
        assertEquals(5, player.inventory().held().count(),
                "the refused use spent nothing");
        assertEquals(2, resyncs.size(),
                "the resync pair still echoes the clicked and offset cells");
        assertTrue(resyncs.get(0).startsWith("BlockPosition[x=9, y=4, z=0]"),
                "the first echo answers the clicked cell: " + resyncs.get(0));
        assertTrue(resyncs.get(1).startsWith("BlockPosition[x=9, y=5, z=0]"),
                "the second echo answers the face-offset cell: " + resyncs.get(1));
    }

    /**
     * THE BODY RULE STAYS (the reference's own entity-collision check
     * inside canPlace): a solid stone against the top face of the block
     * UNDER the player — the target is the player's own feet cell —
     * refuses while the use is inside every band (the body d3 = 0.25).
     */
    @Test
    void thePlacementIntoThePlayerStillRefuses() throws Exception {
        BlockPosition underFeet = new BlockPosition(0, 4, 0);
        place(underFeet, BuiltinBlocks.STONE);
        hold(ItemStack.of(BuiltinItems.STONE, 5));

        use(underFeet, 1); // the top face: the target is the player's feet cell
        EngineAwaits.awaitTicks(server.world(), 6, "engine progress (a 300 wall settle)");
        assertEquals(BuiltinBlocks.AIR, server.world().getBlock(new BlockPosition(0, 5, 0)),
                "the solid stone never places inside the player");
        assertEquals(5, player.inventory().held().count(),
                "the refused placement spent nothing");
    }
}
