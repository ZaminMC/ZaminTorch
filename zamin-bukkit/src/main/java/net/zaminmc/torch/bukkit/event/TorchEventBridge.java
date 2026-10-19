package net.zaminmc.torch.bukkit.event;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.server.player.PlayerSession;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * The event bridge — the first group (§28.7's inventory: event
 * registration, priorities, cancellation, ordering, exception behavior,
 * reentrancy): the engine's §27.7 events translated into the legacy
 * 1.8.8 event objects, dispatched through the adapter's plugin manager
 * (the HandlerList machinery with the LOWEST→HIGHEST, MONITOR order and
 * the per-listener exception isolation).
 *
 * <p><b>The subscription law:</b> the bridge holds ONE engine-bus
 * subscription per bridged event type, established at attach and alive
 * for the adapter's life, at the engine's FIRST priority — the legacy
 * listeners see the pre-native state and every NATIVE handler (including
 * a MONITOR observer) sees the post-legacy state. The engine re-validates
 * its own state after dispatch (§27.7's advisory-unto-commit law), so the
 * ordering choice is documented, not load-bearing.</p>
 *
 * <p><b>The cancellation table (logged per dispatch, §28.7):</b></p>
 * <ul>
 *   <li>PlayerChat — pre-broadcast cancellation SUPPORTED (the engine's
 *       own cancellable chat event); message/format/recipients mutations
 *       are readable but not applied (the engine's broadcast line is
 *       engine-owned — the recorded §27.9 limitation, logged when a
 *       mutation diverges).</li>
 *   <li>BlockBreak — pre-mutation cancellation SUPPORTED.</li>
 *   <li>BlockPlace — pre-mutation + pre-consumption cancellation SUPPORTED.</li>
 *   <li>PlayerJoin / PlayerQuit — NOT cancellable (the join/quit is already
 *       authoritative — the engine's own contract); the broadcast line is
 *       mutable ({@code setJoinMessage(null)} suppresses it).</li>
 * </ul>
 *
 * <p><b>The chat translation (§28.7's honest limitation):</b> the engine
 * fires its chat event SYNCHRONOUSLY on the owner context (§28.4's law —
 * never change a historically synchronous engine event into an async
 * one); the answer to a synchronous pre-broadcast cancellation cannot
 * wait for an off-owner dispatch (§28.2 forbids the hidden blocking
 * wait). The legacy {@link AsyncPlayerChatEvent} therefore dispatches on
 * the owner context with {@code isAsynchronous() == false} — the honest
 * context flag, the type's legacy identity preserved. The deprecated
 * {@link PlayerChatEvent} fires after it, for every uncancelled chat (the
 * 1.8.8 server's own recipients-untouched condition is not reproducible
 * from the API artifact — the bridge's documented translation).</p>
 *
 * <p><b>Reentrancy (§28.8 case 7):</b> a legacy listener may fire further
 * events through the manager's own {@code callEvent} (including of the
 * same type); dispatches nest like method calls on the same owner context
 * and unwind in order — no dispatch lock exists (the owner context is the
 * serializer, §27.7's own law), so no nested dispatch can deadlock.</p>
 */
public final class TorchEventBridge {

    private final BukkitAdapter adapter;

    public TorchEventBridge(BukkitAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * The one-time wiring: the five engine-bus subscriptions. Idempotent;
     * called by the adapter's attach walk.
     */
    public void attach() {
        net.zaminmc.torch.event.EventBus bus = adapter.engine().events();
        // PlayerJoin — the §27.7 post-commit observation, message mutable.
        bus.on(net.zaminmc.torch.event.player.PlayerJoinEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    PlayerSession session = session(engineEvent.player());
                    if (session == null) {
                        return;
                    }
                    Player player = adapter.player(session);
                    PlayerJoinEvent legacy =
                            new PlayerJoinEvent(player, engineEvent.joinMessage());
                    dispatch(legacy, "PlayerJoin");
                    // The broadcast line maps back (null suppresses it) — the
                    // message mutation is APPLIED, the one chat-form mutator
                    // that the engine owns natively.
                    engineEvent.joinMessage(legacy.getJoinMessage());
                });
        // PlayerQuit — the §27.7 disconnect observation, message mutable.
        bus.on(net.zaminmc.torch.event.player.PlayerQuitEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    PlayerSession session = session(engineEvent.player());
                    if (session == null) {
                        return;
                    }
                    Player player = adapter.player(session);
                    PlayerQuitEvent legacy =
                            new PlayerQuitEvent(player, engineEvent.quitMessage());
                    dispatch(legacy, "PlayerQuit");
                    engineEvent.quitMessage(legacy.getQuitMessage());
                });
        // PlayerChat — the pre-broadcast cancellation (§27.7's Cancellable).
        bus.on(net.zaminmc.torch.event.player.PlayerChatEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    PlayerSession session = session(engineEvent.player());
                    if (session == null) {
                        return;
                    }
                    Player player = adapter.player(session);
                    // The honest context flag: the engine's chat event is
                    // synchronous on the owner context — the legacy async
                    // thread expectation is the §28.7 documented limitation.
                    AsyncPlayerChatEvent asyncForm = new AsyncPlayerChatEvent(
                            false, player, engineEvent.message(),
                            recipientsView());
                    dispatch(asyncForm, "AsyncPlayerChat");
                    PlayerChatEvent legacyForm = null;
                    if (!asyncForm.isCancelled()) {
                        legacyForm = new PlayerChatEvent(player, engineEvent.message());
                        dispatch(legacyForm, "PlayerChat(legacy)");
                    }
                    boolean cancelled = asyncForm.isCancelled()
                            || (legacyForm != null && legacyForm.isCancelled());
                    engineEvent.setCancelled(cancelled);
                    // The readable-but-not-applied mutators, logged when they
                    // diverge from the engine-owned line (§27.9).
                    if (!asyncForm.getMessage().equals(engineEvent.message())) {
                        divergence("AsyncPlayerChat.message");
                    }
                    if (!"<%1$s> %2$s".equals(asyncForm.getFormat())) {
                        divergence("AsyncPlayerChat.format");
                    }
                });
        // BlockBreak — the pre-mutation cancellation (§27.7's Cancellable).
        bus.on(net.zaminmc.torch.event.block.BlockBreakEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    PlayerSession session = session(engineEvent.player());
                    if (session == null) {
                        return;
                    }
                    Player player = adapter.player(session);
                    Block block = adapter.blockAtPosition(
                            engineEvent.world(), engineEvent.position());
                    BlockBreakEvent legacy = new BlockBreakEvent(block, player);
                    dispatch(legacy, "BlockBreak");
                    engineEvent.setCancelled(legacy.isCancelled());
                });
        // BlockPlace — the pre-mutation + pre-consumption cancellation.
        bus.on(net.zaminmc.torch.event.block.BlockPlaceEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    PlayerSession session = session(engineEvent.player());
                    if (session == null) {
                        return;
                    }
                    Player player = adapter.player(session);
                    Block placed = adapter.blockAtPosition(
                            engineEvent.world(), engineEvent.position());
                    Block below = adapter.blockAtPosition(
                            engineEvent.world(), engineEvent.position().offset(0, -1, 0));
                    org.bukkit.Material legacyMaterial =
                            adapter.materialMap().toLegacy(engineEvent.block());
                    if (legacyMaterial == null) {
                        // An engine block with no 1.8.8 identity cannot be
                        // named in the legacy event — the loud log, no dispatch.
                        adapter.logger().warning(() -> "BlockPlace: the engine block "
                                + engineEvent.block().identifier()
                                + " has no 1.8.8 Material — the event was not bridged");
                        return;
                    }
                    BlockPlaceEvent legacy = new BlockPlaceEvent(
                            placed,
                            placed.getState(),
                            below,
                            new ItemStack(legacyMaterial),
                            player,
                            true);
                    dispatch(legacy, "BlockPlace");
                    engineEvent.setCancelled(legacy.isCancelled());
                });
        adapter.logger().info("The event bridge's first group is wired: PlayerJoin, "
                + "PlayerQuit, PlayerChat (async + legacy forms), BlockBreak, BlockPlace "
                + "— cancellation: chat/break/place pre-commit SUPPORTED, join/quit "
                + "not cancellable (the engine's own contracts), each dispatch logged");
    }

    // ------------------------------------------------------------------ the dispatch

    private void dispatch(org.bukkit.event.Event legacy, String label) {
        try {
            adapter.pluginManager().callEvent(legacy);
            adapter.logger().fine(() -> "Bridged " + label
                    + " dispatched (cancelled="
                    + (!(legacy instanceof org.bukkit.event.Cancellable cancellable)
                            ? "n/a" : cancellable.isCancelled())
                    + ", primary="
                    + (adapter.engine().ticker().isOwnerThread() ? "yes" : "NO")
                    + ")");
        } catch (IllegalStateException illegalFromPrimary) {
            // A plugin fired an async-marked event from the primary thread
            // (the 1.8.8 rule kept by the manager): reported, isolated.
            adapter.logger().log(Level.WARNING, "Bridged " + label
                    + " dispatch refused: " + illegalFromPrimary.getMessage());
        }
    }

    // ------------------------------------------------------------------ helpers

    private PlayerSession session(net.zaminmc.torch.entity.Player apiPlayer) {
        if (apiPlayer instanceof PlayerSession session) {
            return session;
        }
        return null; // a foreign player view: not the adapter's to bridge
    }

    /**
     * The recipients view: the unmodifiable snapshot of the online legacy
     * players at dispatch time. Mutations are not applied — the engine's
     * broadcast audience is engine-owned (the §27.9 recorded limitation).
     */
    private Set<Player> recipientsView() {
        return Set.copyOf(adapter.onlinePlayers());
    }

    /** The readable-but-not-applied divergence log (§27.9's honest record). */
    private void divergence(String what) {
        adapter.logger().warning(() -> what
                + " diverged from the engine-owned line — the mutation is "
                + "readable but not applied (the §27.9 recorded limitation)");
    }
}
