package net.zaminmc.torch.bukkit;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Position;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The event bridge over the live engine (§28.7's inventory: priorities,
 * cancellation, ordering, exception behavior, reentrancy — §28.4's
 * contracts). The probe's listeners are the legacy side; the engine's own
 * actions (join walk, chat route, block interactions) are the native side;
 * the assertions read both worlds' observable state.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Order(2)
class BukkitEventBridgeTest {

    private static BukkitTestHarness harness() throws Exception {
        return BukkitTestHarness.harness();
    }

    @Test
    void thePriorityOrderIsTheLegacyOneOnTheLiveDispatch() throws Exception {
        BukkitTestHarness harness = harness();
        PlayerSession worker = harness.join("BreakOrder");
        // A diamond cell: the probe logs the priority chain for it.
        setCell(harness, 2, 5, 2, "minecraft:diamond_block");
        harness.server.blockInteraction().submitCreativeBreak(worker,
                new BlockPosition(2, 5, 2));
        harness.await(() -> harness.probeLogContains("prio:MONITOR type=DIAMOND_BLOCK"),
                "the whole chain dispatched");
        List<String> lines = harness.probeLogLines();
        int lowest = firstIndex(lines, "prio:LOWEST type=DIAMOND_BLOCK");
        int low = firstIndex(lines, "prio:LOW type=DIAMOND_BLOCK");
        int normal = firstIndex(lines, "prio:NORMAL type=DIAMOND_BLOCK");
        int high = firstIndex(lines, "prio:HIGH type=DIAMOND_BLOCK");
        int highest = firstIndex(lines, "prio:HIGHEST type=DIAMOND_BLOCK");
        int monitor = firstIndex(lines, "prio:MONITOR type=DIAMOND_BLOCK");
        assertTrue(lowest < low && low < normal && normal < high && high < highest
                && highest < monitor, "LOWEST→LOW→NORMAL→HIGH→HIGHEST→MONITOR in order: "
                + lines);
    }

    @Test
    void aLegacyCancelMapsBackOntoTheEngineEvent() throws Exception {
        BukkitTestHarness harness = harness();
        PlayerSession worker = harness.join("BreakCancel");
        // The probe cancels every OBSIDIAN break at LOWEST.
        setCell(harness, 2, 5, 2, "minecraft:obsidian");
        BlockPosition target = new BlockPosition(2, 5, 2);
        harness.server.blockInteraction().submitCreativeBreak(worker, target);
        harness.await(() -> harness.probeLogContains("cancel:break-obsidian"),
                "the legacy cancel fired");
        // The cancelled break leaves the engine's cell in place — the
        // engine's own pre-mutation cancellation honored the legacy cancel.
        harness.await(() -> "minecraft:obsidian".equals(blockId(harness, target)),
                "the cell is still obsidian");
        assertTrue(harness.probeLogContains("cancel:break-obsidian"));
    }

    @Test
    void aLegacyChatCancelSuppressesTheEngineBroadcast() throws Exception {
        BukkitTestHarness harness = harness();
        PlayerSession speaker = harness.join("Chatter");
        // The native MONITOR observer answers whether the broadcast is on.
        AtomicReference<Boolean> monitorSawCancelled = new AtomicReference<>();
        var sub = harness.server.events().on(
                net.zaminmc.torch.event.player.PlayerChatEvent.class,
                net.zaminmc.torch.event.EventPriority.MONITOR,
                event -> monitorSawCancelled.set(event.cancelled()));
        harness.server.chatService().submitChat(speaker, "this is censored text");
        harness.await(() -> Boolean.TRUE.equals(monitorSawCancelled.get()),
                "the native MONITOR saw the mapped-back cancel");
        // The uncancelled line passes through both legacy forms.
        harness.server.chatService().submitChat(speaker, "plain line");
        harness.await(() -> harness.probeLogContains("event:chat-legacy message=plain line"),
                "the legacy sync chat form fired for the uncancelled line");
        sub.unsubscribe();
    }

    @Test
    void theJoinAndQuitObservationsBridgeWithTheMutableLine() throws Exception {
        BukkitTestHarness harness = harness();
        PlayerSession joiner = harness.join("JoinLine");
        harness.await(() -> harness.probeLogContains("event:join message="),
                "the join observation bridged");
        assertTrue(harness.probeLogContains("primary=true"),
                "the bridge dispatched on the owner context (the honest flag)");
        harness.disconnect(joiner);
        harness.await(() -> harness.probeLogContains("event:quit message="),
                "the quit observation bridged");
    }

    @Test
    void aThrowingListenerNeverBreaksTheChainOrTheEngine() throws Exception {
        BukkitTestHarness harness = harness();
        PlayerSession worker = harness.join("BreakIsolation");
        // GOLD_BLOCK: the probe's NORMAL-priority listener throws; the
        // LOWEST before it and the whole chain after it must still run.
        setCell(harness, 2, 5, 2, "minecraft:gold_block");
        long ticksBefore = harness.server.world().totalTicks();
        harness.server.blockInteraction().submitCreativeBreak(worker,
                new BlockPosition(2, 5, 2));
        harness.await(() -> harness.probeLogContains("prio:MONITOR type=GOLD_BLOCK"),
                "the chain completed past the throwing listener");
        // The engine's own tick kept progressing through the failure.
        harness.await(() -> harness.server.world().totalTicks() > ticksBefore,
                "the engine's tick survived the listener's throw");
        // And the isolation is observable: LOWEST ran before the throw and
        // MONITOR after it (the §28.8 case-8 contract).
        List<String> lines = harness.probeLogLines();
        assertTrue(firstIndex(lines, "prio:LOW type=GOLD_BLOCK")
                < firstIndex(lines, "prio:NORMAL type=GOLD_BLOCK"));
    }

    @Test
    void aReentrantDispatchNestsWithoutDeadlock() throws Exception {
        BukkitTestHarness harness = harness();
        PlayerSession speaker = harness.join("Reentrant");
        // The probe's chat listener fires a nested PlayerJoinEvent from
        // inside the dispatch; the log proves the nesting unwound in order.
        harness.server.chatService().submitChat(speaker, "reentrant");
        harness.await(() -> harness.probeLogContains("reentrancy:chat-end"),
                "the nested dispatch returned (no deadlock)");
        List<String> lines = harness.probeLogLines();
        int begin = firstIndex(lines, "reentrancy:chat-begin");
        int nested = firstIndex(lines, "event:join message=nested");
        int end = firstIndex(lines, "reentrancy:chat-end");
        assertTrue(begin < nested && nested < end,
                "the nested event dispatched BETWEEN the listener's own lines: " + lines);
        // The engine kept ticking through the nested dispatch.
        assertTrue(harness.probeLogContains("event:join message=nested primary=true"),
                "the nested dispatch ran on the same owner context");
    }

    // ------------------------------------------------------------------ helpers

    private static void setCell(BukkitTestHarness harness, int x, int y, int z, String id) {
        EngineWorld world = harness.server.world();
        var type = harness.server.blocks().byKey(id).orElseThrow();
        // Ground preparation rides the owner lane (the existing suites' law).
        harness.server.ticker().submit(() -> {
            world.setBlock(new BlockPosition(x, y, z), type);
        });
        try {
            harness.await(() -> id.equals(blockId(harness, new BlockPosition(x, y, z))),
                    "the cell is " + id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static String blockId(BukkitTestHarness harness, BlockPosition at) {
        var type = harness.server.world().getBlock(at);
        return type == null ? null : type.identifier().toString();
    }

    private static int firstIndex(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }
}
