package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.interaction.BlockInteractionService;
import net.zaminmc.torch.server.interaction.MiningRules;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vanilla 1.8.8 mining port ({@code MiningRules}: PlayerEntity.
 * getMiningSpeed + Block.getMiningSpeed; the flow: ServerPlayerInteraction-
 * Manager startMiningBlock/finishMiningBlock/tick): the tool-tier,
 * environment-divisor and Efficiency math must match the vanilla numbers, a
 * finish at the vanilla 0.7 accumulated-progress threshold mines immediately,
 * an early finish keeps accumulating and self-completes at 1.0 without a
 * resync, and instant-progress blocks mine on the START itself.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VanillaMiningTimingTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
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

    private void seedBlock(PlayerSession player, BlockPosition at,
                           net.zaminmc.torch.block.BlockType type) throws InterruptedException {
        server.blockInteraction().submitPlace(player, at.offset(0, -1, 0), 1, type);
        await(() -> server.world().getBlock(at).equals(type), "seeded at " + at);
    }

    // ------------------------------------------------------------------ the math

    @Test
    void vanillaBreakTicksMatrix() {
        // Stone (hardness 1.5) by hand: 1.5 * 100 / 1 = 150 ticks (7.5s).
        assertEquals(150, MiningRules.breakTicks(1.5, 1.0, false));
        // Stone with a wooden pickaxe (harvestable): 1.5 * 30 / 2 = 22.5 -> 23.
        assertEquals(23, MiningRules.breakTicks(1.5, 2.0, true));
        // Dirt (0.5) by hand: 0.5 * 30 / 1 = 15.
        assertEquals(15, MiningRules.breakTicks(0.5, 1.0, true));
        // Dirt with an iron shovel: 0.5 * 30 / 6 = 2.5 -> 3.
        assertEquals(3, MiningRules.breakTicks(0.5, 6.0, true));
        // Iron ore with a stone pickaxe: 3 * 30 / 4 = 22.5 -> 23.
        assertEquals(23, MiningRules.breakTicks(3.0, 4.0, true));
        // Iron ore with a wooden pickaxe (cannot harvest): 3 * 100 / 2 = 150.
        assertEquals(150, MiningRules.breakTicks(3.0, 2.0, false));
        // Bedrock: unbreakable regardless.
        assertEquals(Integer.MAX_VALUE, MiningRules.breakTicks(-1.0, 8.0, true));
        // A hardness-0 block (torch, flora): instant.
        assertEquals(1, MiningRules.breakTicks(0.0, 1.0, true));
    }

    @Test
    void vanillaEnvironmentDivisors() {
        // Stone + wooden pickaxe submerged in water: 22.5 * 5 = 112.5 -> 113.
        double speed = MiningRules.miningSpeed(2.0, 0, true, false, true);
        assertEquals(113, MiningRules.breakTicks(1.5, speed, true));
        // The same dig airborne: another x5.
        double airborne = MiningRules.miningSpeed(2.0, 0, false, false, false);
        assertEquals(113, MiningRules.breakTicks(1.5, airborne, true));
        // Both at once: x25 (562.5 -> 563).
        double both = MiningRules.miningSpeed(2.0, 0, true, false, false);
        assertEquals(563, MiningRules.breakTicks(1.5, both, true));
        // Aqua Affinity cancels the water divisor (the dig is back to 23).
        double aqua = MiningRules.miningSpeed(2.0, 0, true, true, true);
        assertEquals(23, MiningRules.breakTicks(1.5, aqua, true));
    }

    @Test
    void vanillaEfficiencyBonusGatesOnEffectiveTools() {
        // Efficiency III on a wooden pickaxe mining stone: speed 2 + (9 + 1) = 12
        // -> 1.5 * 30 / 12 = 3.75 -> 4 ticks.
        double speed = MiningRules.miningSpeed(2.0, 3, false, false, true);
        assertEquals(12.0, speed, 1e-9);
        assertEquals(4, MiningRules.breakTicks(1.5, speed, true));
        // Efficiency V on diamond: 8 + 26 = 34 -> stone 1.5*30/34 = 1.32 -> 2.
        assertEquals(34.0, MiningRules.miningSpeed(8.0, 5, false, false, true), 1e-9);
        // The vanilla gate: an effective-tool-only bonus. A bare fist at 1.0
        // gains nothing from Efficiency (f > 1.0F fails) -> still 150 ticks.
        assertEquals(1.0, MiningRules.miningSpeed(1.0, 3, false, false, true), 1e-9);
        assertEquals(150, MiningRules.breakTicks(1.5, 1.0, false));
        // And a mismatched tool (speed 1.0) stays slow even with the enchant:
        // stone by hand is not harvestable, so the /100 branch runs.
        assertEquals(150, MiningRules.breakTicks(1.5,
                MiningRules.miningSpeed(1.0, 5, false, false, true), false));
    }

    // -------------------------------------------------- the vanilla dig flow

    @Test
    void finishAtVanillaThresholdMinesImmediatelyAndBroadcastsStages() throws Exception {
        server = boot();
        PlayerSession player = join("ThresholdDigger");
        assertTrue(player.inventory()
                .pickUp(ItemStack.of(BuiltinItems.WOODEN_PICKAXE))
                .isEmpty(), "test grants the tool into the held hotbar slot");

        BlockPosition stone = new BlockPosition(2, 5, 2);
        seedBlock(player, stone, BuiltinBlocks.STONE);

        List<Integer> stages = new ArrayList<>();
        server.blockInteraction().setMiningProgressListener((digger, target, stage) -> {
            if (target.equals(stone)) {
                stages.add(stage);
            }
        });

        // Stone with a wooden pickaxe: 23 ticks to progress 1.0. The vanilla
        // finish accepts at f >= 0.7 = tick 15.1; the finish at ~500ms (10-11
        // ticks, f ~= 0.5) must park the dig — the vanilla wasMining path.
        server.blockInteraction().submitMiningStart(player, stone);
        Thread.sleep(500);
        server.blockInteraction().submitMiningFinished(player, stone);
        assertTrue(server.world().getBlock(stone).equals(BuiltinBlocks.STONE),
                "a half-progress finish is below the vanilla 0.7 threshold");

        // The parked dig self-completes at tick 23 (~1.15s from start) — the
        // vanilla wasMining accumulation, no further packets needed.
        await(() -> server.world().getBlock(stone).equals(BuiltinBlocks.AIR),
                "the parked early finish self-completes at progress 1.0");
        assertEquals(1, player.inventory().held().damage(),
                "the self-completed break wears the tool like any other");
        assertTrue(!server.itemEntities().all().isEmpty()
                        && server.itemEntities().all().get(0).stack().type().identifier()
                                .toString().equals("minecraft:cobblestone"),
                "the self-completed break yields the drops");

        // The stage ladder: the START's initial stage, then the climb while
        // the accumulation ran (no repeats), ending at the vanilla full-
        // progress frame 10. The self-complete path relies on the Block
        // Change, not a removal byte — no 255 ever flies.
        int removals = 0;
        int previous = -1;
        for (int stage : stages) {
            if (stage == BlockInteractionService.REMOVAL_STAGE) {
                removals++;
                continue;
            }
            assertTrue(stage > previous && stage <= 10,
                    "stages climb without repeats, saw " + stage + " after " + previous);
            previous = stage;
        }
        assertTrue(previous >= 0, "at least one cracking stage was broadcast");
        assertEquals(10, previous, "the ladder ends at the vanilla full-progress frame");
        assertEquals(0, removals, "the vanilla self-complete sends no removal byte");

        server.shutdown(null);
    }

    @Test
    void finishBelowThresholdNeverBreaksBeforeTheVanillaClock() throws Exception {
        server = boot();
        PlayerSession player = join("EarlyFinisher");

        BlockPosition dirt = new BlockPosition(2, 5, 2);
        seedBlock(player, dirt, BuiltinBlocks.DIRT);

        // Dirt by hand: 15 ticks to 1.0. A finish at 300ms (6 ticks, f = 0.47)
        // parks the dig; the vanilla self-completion then lands at tick 15
        // (~750ms) — not at the client's claimed 300ms and not never.
        server.blockInteraction().submitMiningStart(player, dirt);
        Thread.sleep(300);
        server.blockInteraction().submitMiningFinished(player, dirt);
        assertTrue(server.world().getBlock(dirt).equals(BuiltinBlocks.DIRT),
                "a 0.47-progress finish does not mine");
        await(() -> server.world().getBlock(dirt).equals(BuiltinBlocks.AIR),
                "the parked dig self-completes on the vanilla clock");
        server.shutdown(null);
    }

    @Test
    void airborneDigTakesFiveTimesLonger() throws Exception {
        server = boot();
        PlayerSession player = join("JumpingMiner");

        BlockPosition dirt = new BlockPosition(2, 5, 2);
        seedBlock(player, dirt, BuiltinBlocks.DIRT);

        // Feet leave the ground: the vanilla /5 divisor applies to the whole
        // dig. Dirt by hand 15 ticks becomes 75; the finish at 1.5s (30 ticks,
        // f = 0.4) is below the threshold, and the parked self-completion
        // needs the full 75 ticks (3.75s) — the vanilla airborne slow-down.
        player.applyMovement(player.position(), new Rotation(0f, 0f), false);

        server.blockInteraction().submitMiningStart(player, dirt);
        Thread.sleep(1_500);
        server.blockInteraction().submitMiningFinished(player, dirt);
        assertTrue(server.world().getBlock(dirt).equals(BuiltinBlocks.DIRT),
                "an airborne dig cannot finish in a grounded dig's time");
        await(() -> server.world().getBlock(dirt).equals(BuiltinBlocks.AIR),
                "the airborne dig still self-completes on the slowed clock");
        server.shutdown(null);
    }

    @Test
    void instantBreakMinesOnTheStartPacket() throws Exception {
        server = boot();
        PlayerSession player = join("TorchDigger");

        BlockPosition torch = new BlockPosition(2, 5, 2);
        seedBlock(player, torch, BuiltinBlocks.TORCH);

        // Hardness 0: the vanilla startMiningBlock f >= 1.0 rule mines the
        // block on the START packet itself, no accumulator tick needed.
        server.blockInteraction().submitMiningStart(player, torch);
        await(() -> server.world().getBlock(torch).equals(BuiltinBlocks.AIR),
                "a hardness-0 block mines on the START");
        server.shutdown(null);
    }
}
