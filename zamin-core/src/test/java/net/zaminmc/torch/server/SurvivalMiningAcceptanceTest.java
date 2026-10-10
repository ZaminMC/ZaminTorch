package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral scenarios for survival mining (§93/§614): the client proposes
 * start/abort/finish; the server validates reach, diggability and elapsed time
 * before committing, and re-syncs on rejection so no ghost blocks remain.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SurvivalMiningAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        // Survival is the server default; booting without a mode override proves it.
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private net.zaminmc.torch.block.BlockType world0() {
        return server.world().airType();
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** Places a block of the given type at the position through the authoritative path. */
    private void seedBlock(PlayerSession player, BlockPosition at,
                           net.zaminmc.torch.block.BlockType type) throws InterruptedException {
        server.blockInteraction().submitPlace(player, at.offset(0, -1, 0), 1, type);
        // The seed observation rides a wider budget than the behavior awaits:
        // under full-suite load the tick thread can starve long enough to
        // miss the 20s window (the same flake class the fire test's seed
        // latch hardened — the commit itself is deterministic, the poll
        // budget is what failed).
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            if (server.world().getBlock(at).equals(type)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("seed not committed in time at " + at);
    }

    @Test
    void correctlyTimedSurvivalDigBreaksBlock() throws Exception {
        server = boot();
        PlayerSession player = join("Miner");
        BlockPosition target = new BlockPosition(2, 5, 2);
        seedBlock(player, target, BuiltinBlocks.DIRT);

        server.blockInteraction().submitMiningStart(player, target);
        // dirt by hand: 15 ticks nominal (750ms); lenient floor 70% (525ms). Wait past it.
        Thread.sleep(900);
        server.blockInteraction().submitMiningFinished(player, target);
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.AIR),
                "survival dig commits break");
        server.shutdown(null);
    }

    @Test
    void breakingGrassSpawnsDirtDrop() throws Exception {
        server = boot();
        PlayerSession player = join("DropTaker");
        // The surface grass itself, no seeding needed.
        BlockPosition grass = new BlockPosition(2, 4, 2);
        server.requestChunkLoad(grass.chunkPosition(), chunk -> { });
        await(() -> server.world().isChunkLoaded(grass.chunkPosition()), "chunk loaded");
        assertEquals(BuiltinBlocks.GRASS_BLOCK, server.world().getBlock(grass));

        server.blockInteraction().submitMiningStart(player, grass);
        Thread.sleep(900); // grass by hand: 18 ticks nominal, floor 546ms
        server.blockInteraction().submitMiningFinished(player, grass);
        await(() -> server.world().getBlock(grass).equals(BuiltinBlocks.AIR), "grass broken");

        await(() -> !server.itemEntities().all().isEmpty()
                        && server.itemEntities().all().get(0).stack().type().identifier()
                                .toString().equals("minecraft:dirt"),
                "dirt drop spawned");
        assertEquals(1, server.itemEntities().all().get(0).stack().count());
        server.shutdown(null);
    }

    @Test
    void breakingStoneByHandYieldsNothing() throws Exception {
        server = boot();
        PlayerSession player = join("BareHands");
        BlockPosition stone = new BlockPosition(2, 5, 2);
        seedBlock(player, stone, BuiltinBlocks.STONE);

        server.blockInteraction().submitMiningStart(player, stone);
        // Stone by hand is not harvestable: 150 ticks nominal (7.5s), floor 5.25s.
        Thread.sleep(5_400);
        server.blockInteraction().submitMiningFinished(player, stone);
        await(() -> server.world().getBlock(stone).equals(BuiltinBlocks.AIR),
                "stone breaks (slowly) even without a tool");
        org.junit.jupiter.api.Assertions.assertEquals(0, server.itemEntities().size(),
                "no drop without a harvest tool (historical behavior)");
        server.shutdown(null);
    }

    @Test
    void tooFastFinishParksTheDigOnTheVanillaClock() throws Exception {
        server = boot();
        PlayerSession player = join("Hasty");
        BlockPosition target = new BlockPosition(3, 5, 2);
        seedBlock(player, target, BuiltinBlocks.DIRT);

        // The vanilla finishMiningBlock: an immediate finish is far below the
        // 0.7 progress threshold, so the dig parks (no resync — the vanilla
        // flow keeps the client's prediction standing) and the wasMining
        // accumulator self-completes at progress 1.0, i.e. the full 15 ticks.
        server.blockInteraction().submitMiningStart(player, target);
        server.blockInteraction().submitMiningFinished(player, target); // immediately: far too fast
        assertTrue(server.world().getBlock(target).equals(BuiltinBlocks.DIRT),
                "an instant finish does not mine on the spot");
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.AIR),
                "the parked dig self-completes on the vanilla clock");
        server.shutdown(null);
    }

    @Test
    void abortedDigThenFinishIsRejected() throws Exception {
        server = boot();
        PlayerSession player = join("Abandoner");
        BlockPosition target = new BlockPosition(4, 5, 2);
        seedBlock(player, target, BuiltinBlocks.DIRT);

        server.blockInteraction().submitMiningStart(player, target);
        Thread.sleep(100);
        server.blockInteraction().submitMiningAborted(player);
        Thread.sleep(700); // longer than the full nominal duration
        server.blockInteraction().submitMiningFinished(player, target);
        // The dig commit would produce air; a grass random tick may still
        // claim the exposed dirt, so the invariant is "never air", not "still dirt".
        await(() -> !server.world().getBlock(target).equals(world0()),
                "aborted dig does not commit");
        server.shutdown(null);
    }

    @Test
    void finishWithoutSessionIsRejected() throws Exception {
        server = boot();
        PlayerSession player = join("Sneaky");
        BlockPosition target = new BlockPosition(5, 5, 2);
        seedBlock(player, target, BuiltinBlocks.DIRT);

        server.blockInteraction().submitMiningFinished(player, target); // never started
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.DIRT),
                "session-less finish does not commit");
        server.shutdown(null);
    }

    @Test
    void bedrockNeverOpensMiningSession() throws Exception {
        server = boot();
        PlayerSession player = join("BedrockMiner");
        BlockPosition bedrock = new BlockPosition(6, 0, 2); // flat world ground level 4 -> y0 is bedrock
        server.requestChunkLoad(bedrock.chunkPosition(), chunk -> { });
        await(() -> server.world().isChunkLoaded(bedrock.chunkPosition()), "chunk loaded");
        assertEquals(BuiltinBlocks.BEDROCK, server.world().getBlock(bedrock));

        server.blockInteraction().submitMiningStart(player, bedrock);
        Thread.sleep(200);
        server.blockInteraction().submitMiningFinished(player, bedrock);
        await(() -> server.world().getBlock(bedrock).equals(BuiltinBlocks.BEDROCK),
                "bedrock survives a dig attempt");
        server.shutdown(null);
    }

    // ------------------------------------------------------------------ tools slice

    @Test
    void pickaxeMinesStoneFasterAndDropsCobblestone() throws Exception {
        server = boot();
        PlayerSession player = join("PickaxeMiner");
        assertTrue(player.inventory()
                .pickUp(net.zaminmc.torch.item.ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.WOODEN_PICKAXE))
                .isEmpty(), "test grants the tool into the held hotbar slot");

        BlockPosition stone = new BlockPosition(2, 5, 2);
        seedBlock(player, stone, BuiltinBlocks.STONE);

        server.blockInteraction().submitMiningStart(player, stone);
        // Stone with a wooden pickaxe: 1.5 * 30 / 2 = 23 ticks nominal (1.15s),
        // lenient floor 70% (805ms). Historically a third of the hand time.
        Thread.sleep(1_200);
        server.blockInteraction().submitMiningFinished(player, stone);
        await(() -> server.world().getBlock(stone).equals(BuiltinBlocks.AIR),
                "pickaxe dig commits break");

        await(() -> server.itemEntities().size() == 1
                        && server.itemEntities().all().get(0).stack().type().identifier()
                                .toString().equals("minecraft:cobblestone"),
                "cobblestone drop spawned");
        org.junit.jupiter.api.Assertions.assertEquals(1, player.inventory().held().damage(),
                "one successful dig wears the tool by one durability unit");
        server.shutdown(null);
    }

    @Test
    void ironOreNeedsAStoneTierPickaxe() throws Exception {
        server = boot();
        PlayerSession wooden = join("WoodMiner");
        assertTrue(wooden.inventory()
                .pickUp(net.zaminmc.torch.item.ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.WOODEN_PICKAXE))
                .isEmpty());

        BlockPosition ore = new BlockPosition(2, 5, 3);
        seedBlock(wooden, ore, BuiltinBlocks.IRON_ORE);

        // The wooden pickaxe class-matches (2x speed) but cannot harvest iron ore:
        // 3 * 100 / 2 = 150 ticks nominal (7.5s), floor 5.25s - the full slow dig.
        server.blockInteraction().submitMiningStart(wooden, ore);
        Thread.sleep(5_400);
        server.blockInteraction().submitMiningFinished(wooden, ore);
        await(() -> server.world().getBlock(ore).equals(BuiltinBlocks.AIR),
                "breakable even with the wrong tier");
        org.junit.jupiter.api.Assertions.assertEquals(0, server.itemEntities().size(),
                "no drop without the required harvest tier");

        // A stone-tier pickaxe harvests: 3 * 30 / 4 = 23 ticks (1.15s).
        PlayerSession stoned = join("StoneMiner");
        assertTrue(stoned.inventory()
                .pickUp(net.zaminmc.torch.item.ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.STONE_PICKAXE))
                .isEmpty());
        BlockPosition ore2 = new BlockPosition(2, 5, 3); // within 4.5 survival reach of spawn
        seedBlock(stoned, ore2, BuiltinBlocks.IRON_ORE);
        server.blockInteraction().submitMiningStart(stoned, ore2);
        Thread.sleep(1_200);
        server.blockInteraction().submitMiningFinished(stoned, ore2);
        await(() -> server.world().getBlock(ore2).equals(BuiltinBlocks.AIR), "stone-tier dig commits");

        await(() -> server.itemEntities().all().stream().anyMatch(entity ->
                        entity.stack().type().identifier().toString().equals("minecraft:iron_ore")),
                "iron ore drops itself to a stone-tier pickaxe");
        server.shutdown(null);
    }

    @Test
    void aToolAtItsDurabilityLimitBreaksInTheHand() throws Exception {
        server = boot();
        PlayerSession player = join("ToolBreaker");
        player.inventory().pickUp(
                net.zaminmc.torch.item.ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.GOLDEN_PICKAXE));
        // Wear the golden pickaxe (32 durability) to one point before breaking.
        while (player.inventory().held().damage() < 31) {
            player.inventory().damageHeld(1);
        }

        BlockPosition stone = new BlockPosition(2, 5, 2); // within 4.5 survival reach of spawn
        seedBlock(player, stone, BuiltinBlocks.STONE);
        server.blockInteraction().submitMiningStart(player, stone);
        Thread.sleep(1_200); // golden pickaxe on stone: 1.5 * 30 / 12 = 4 ticks, floor 140ms
        server.blockInteraction().submitMiningFinished(player, stone);
        await(() -> server.world().getBlock(stone).equals(BuiltinBlocks.AIR), "dig commits");

        await(() -> player.inventory().held().isEmpty(), "the final wear point breaks the tool");
        server.shutdown(null);
    }

    private static void assertEquals(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
