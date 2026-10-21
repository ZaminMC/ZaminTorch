package net.zaminmc.torch.bukkit.event;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.bukkit.world.TorchWorld;
import net.zaminmc.torch.server.player.PlayerSession;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * <p><b>The second group's cancellation table (this slice, §28.7):</b></p>
 * <ul>
 *   <li>PlayerDamage → EntityDamageEvent (the player arm) — cancellation
 *       SUPPORTED both ways; the mutable amount maps both ways too (the
 *       legacy {@code setDamage} drives the engine's own mutable
 *       {@code amount}).</li>
 *   <li>PlayerDeath → PlayerDeathEvent — the legacy form is NOT cancellable
 *       (the 1.8.8 shape kept); the death message is mutable and APPLIED
 *       (the engine-owned native arm), and the legacy form's own
 *       {@code setKeepInventory(true)} maps onto the native cancel arm
 *       (the engine's keepInventory gate — the XP also stays, the recorded
 *       bundle divergence, logged). The {@code getDrops()} list answers an
 *       UNMODIFIABLE EMPTY view (the adapter models no engine-inventory
 *       view — the native event fires pre-scatter by the engine's own
 *       design, so no honest drop list exists; a mutating plugin hits the
 *       loud refusal); {@code droppedExp} is readable-not-applied (the
 *       engine's scatter math is authoritative).</li>
 *   <li>FluidFlow → BlockFromToEvent — pre-commit cancellation SUPPORTED
 *       both ways (the engine's own per-cell flow gate).</li>
 *   <li>PistonMove → BlockPistonExtendEvent / BlockPistonRetractEvent —
 *       pre-movement cancellation SUPPORTED both ways (the whole movement
 *       cancels, the engine's own contract). The native event's richer
 *       {@code brokenCells} row has no 1.8.8 form (the §27.9 note: the
 *       native API keeps the capability, the legacy type carries no row).</li>
 *   <li>SignChange → SignChangeEvent — pre-commit cancellation SUPPORTED
 *       both ways; the four lines are mutable and APPLIED (the engine's
 *       own rewrite arm).</li>
 *   <li>FurnaceBurn → FurnaceBurnEvent — pre-commit cancellation SUPPORTED
 *       both ways; {@code setBurnTime} is readable-not-applied (the
 *       native event carries the fuel's own value read-only — the
 *       divergence logged when a plugin rewrites it).</li>
 *   <li>FurnaceSmelt → FurnaceSmeltEvent — pre-commit cancellation SUPPORTED
 *       both ways; {@code setResult} is readable-not-applied (the native
 *       event carries the engine's recipe answer — the divergence logged
 *       when a plugin rewrites it).</li>
 * </ul>
 *
 * <p><b>The third group's cancellation table (slice 3 — the entity
 * adapters):</b></p>
 * <ul>
 *   <li>MobSpawn → CreatureSpawnEvent — pre-spawn cancellation SUPPORTED
 *       both ways (the engine's own pre-construction gate). The legacy
 *       form's entity is the PENDING view (the engine fires before the
 *       body exists — no id consumed; the recorded §27.9 divergence
 *       from the reference's post-construction fire); the spawn reason
 *       answers {@code DEFAULT} (the native event carries no reason row
 *       — the recorded approximation, the slice-2
 *       EXPLOSION→ENTITY_EXPLOSION precedent).</li>
 *   <li>MobDamage → EntityDamageEvent (the mob arm) — cancellation
 *       SUPPORTED both ways; the mutable amount maps both ways (the
 *       same both-ways law as the player arm). The entity is the LIVE
 *       mob view (the native event fires over the live body).</li>
 *   <li>MobDeath → EntityDeathEvent — NOT cancellable (the 1.8.8 shape
 *       kept; the death is already authoritative); the drops list is
 *       the honest UNMODIFIABLE EMPTY shim (the native event fires
 *       pre-scatter — no honest drop list exists, the PlayerDeath
 *       precedent); {@code droppedExp} is readable-not-applied (the
 *       engine's scatter math is authoritative), logged on divergence.</li>
 *   <li>ItemSpawn → ItemSpawnEvent — pre-spawn cancellation SUPPORTED
 *       both ways; the entity is the pending Item view carrying the
 *       stack's legacy value view (a snapshot — mutations never reach
 *       the engine's stack).</li>
 *   <li>ProjectileSpawn → ProjectileLaunchEvent — pre-launch
 *       cancellation SUPPORTED both ways; the entity is the pending
 *       Projectile view with the thrower resolved against the
 *       adapter's own views (never an invented shooter).</li>
 *   <li>VehicleSpawn → VehicleCreateEvent — NOT cancellable (the
 *       1.8.8 shape kept — the reference's own VehicleCreateEvent
 *       carries no cancel row; the native VehicleSpawnEvent keeps the
 *       capability, §28.10's law: the compatibility limit stays in the
 *       adapter).</li>
 * </ul>
 *
 * <p><b>The unbridged native families (the honest gaps, §27.9 — every one
 * recorded with its concrete reason, none faked):</b> the entity-backed
 * families were bridged by the slice-3 lane (the six families through
 * the org.bukkit.entity views) and the container family by the
 * slice-4 lane below; the remaining honest gaps are the slice-4
 * record's own residuals — the open kinds with no modeled view, the
 * entity container forms (the horse, the carts — the entity-adapter
 * lane's family), and the hopper's SUCK arm (the item-entity source).
 * The chest family's own {@code ChestOpenEvent} stays native-only (the
 * shared {@code ContainerOpenEvent} is the bridge's one source — the
 * no-double-dispatch law). Each remaining gap stays unbridged until
 * its view lands; honest gaps over fake bridges, always.</p>
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
     * The one-time wiring: the engine-bus subscriptions. Idempotent;
     * called by the adapter's attach walk.
     */
    public void attach() {
        attachFirstGroup();
        attachSecondGroup();
        attachThirdGroup();
        attachInventoryGroup();
    }

    /** The first group: the five subscriptions the first slice carried. */
    private void attachFirstGroup() {
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

    // ------------------------------------------------------------------ the second group (this slice)

    /**
     * The second group's wiring: the seven constructible families — every
     * one verified against the pinned API's own constructor shapes (the
     * block/player/furnace views the adapter models; the entity-backed
     * families stay unbridged, the class javadoc's recorded gaps).
     */
    private void attachSecondGroup() {
        net.zaminmc.torch.event.EventBus bus = adapter.engine().events();
        // PlayerDamage — the pre-commit damage gate, cancellable both ways,
        // the mutable amount mapped both ways (§27.7's Cancellable).
        bus.on(net.zaminmc.torch.event.player.PlayerDamageEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    PlayerSession session = session(engineEvent.player());
                    if (session == null) {
                        return;
                    }
                    Player player = adapter.player(session);
                    EntityDamageEvent legacy = new EntityDamageEvent(
                            player, legacyDamageCause(engineEvent.cause()),
                            engineEvent.amount());
                    dispatch(legacy, "EntityDamage(player)");
                    engineEvent.setCancelled(legacy.isCancelled());
                    if ((float) legacy.getDamage() != engineEvent.amount()) {
                        // The legacy setDamage maps onto the engine's own
                        // mutable amount — the native event's own contract.
                        engineEvent.amount((float) legacy.getDamage());
                    }
                });
        // PlayerDeath — the §27.7 post-commit death observation: the
        // message mutable and applied; the legacy form's keepInventory arm
        // mapped onto the native cancel arm; the honest drops/exp shims.
        bus.on(net.zaminmc.torch.event.player.PlayerDeathEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    PlayerSession session = session(engineEvent.player());
                    if (session == null) {
                        return;
                    }
                    Player player = adapter.player(session);
                    org.bukkit.event.entity.PlayerDeathEvent legacy =
                            new org.bukkit.event.entity.PlayerDeathEvent(
                                    player, List.of(), 0, engineEvent.deathMessage());
                    dispatch(legacy, "PlayerDeath");
                    // The broadcast line maps back (the engine-owned arm).
                    engineEvent.deathMessage(legacy.getDeathMessage());
                    // The legacy keepInventory arm → the native cancel arm
                    // (the engine's keepInventory gate; the XP also stays —
                    // the recorded bundle divergence, logged when it bites).
                    if (legacy.getKeepInventory()) {
                        engineEvent.setCancelled(true);
                    } else if (legacy.getKeepLevel()) {
                        divergence("PlayerDeath.setKeepLevel"
                                + " (the engine's cancel arm keeps the whole" 
                                + " inventory+XP bundle — the recorded limitation)");
                    }
                    // The readable-but-not-applied rows, logged when they
                    // diverge (§27.9): the drops view is the unmodifiable
                    // empty shim (no engine-inventory view modeled — a
                    // mutating plugin already hit its loud refusal inside
                    // the dispatch); the engine's scatter math is
                    // authoritative for both the exp rows.
                    if (legacy.getDroppedExp() != 0) {
                        divergence("PlayerDeath.setDroppedExp");
                    }
                    if (legacy.getNewExp() != 0 || legacy.getNewLevel() != 0
                            || legacy.getNewTotalExp() != 0) {
                        divergence("PlayerDeath.setNew* (the respawn-screen rows)");
                    }
                });
        // FluidFlow — the per-cell pre-commit flow gate, cancellable both ways.
        bus.on(net.zaminmc.torch.event.block.FluidFlowEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    TorchWorld world = adapter.worldByName(engineEvent.worldName());
                    if (world == null) {
                        // A world the adapter does not view (a foreign host's)
                        // — the loud log, no dispatch, never a fake bridge.
                        adapter.logger().warning(() -> "FluidFlow: the world '"
                                + engineEvent.worldName()
                                + "' has no adapter view — the event was not bridged");
                        return;
                    }
                    Block from = adapter.blockAt(world, engineEvent.from().x(),
                            engineEvent.from().y(), engineEvent.from().z());
                    Block to = adapter.blockAt(world, engineEvent.to().x(),
                            engineEvent.to().y(), engineEvent.to().z());
                    BlockFromToEvent legacy = new BlockFromToEvent(from, to);
                    dispatch(legacy, "BlockFromTo(fluid)");
                    engineEvent.setCancelled(legacy.isCancelled());
                });
        // PistonMove — the pre-movement gate, extend/retract split by the
        // native event's own direction flag, cancellable both ways.
        bus.on(net.zaminmc.torch.event.block.PistonMoveEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    TorchWorld world = adapter.worldByName(engineEvent.worldName());
                    if (world == null) {
                        adapter.logger().warning(() -> "PistonMove: the world '"
                                + engineEvent.worldName()
                                + "' has no adapter view — the event was not bridged");
                        return;
                    }
                    Block piston = adapter.blockAt(world, engineEvent.position().x(),
                            engineEvent.position().y(), engineEvent.position().z());
                    BlockFace face = legacyFace(engineEvent.facing());
                    List<Block> moved = new ArrayList<>(engineEvent.movedCells().size());
                    for (net.zaminmc.torch.block.BlockPosition cell : engineEvent.movedCells()) {
                        moved.add(adapter.blockAt(world, cell.x(), cell.y(), cell.z()));
                    }
                    org.bukkit.event.block.BlockPistonEvent legacy;
                    if (engineEvent.extending()) {
                        legacy = new BlockPistonExtendEvent(piston, moved, face);
                        dispatch(legacy, "BlockPistonExtend");
                    } else {
                        legacy = new BlockPistonRetractEvent(piston, moved, face);
                        dispatch(legacy, "BlockPistonRetract");
                    }
                    engineEvent.setCancelled(legacy.isCancelled());
                });
        // SignChange — the pre-commit sign-editor gate: cancellable both
        // ways, the four lines mutable and applied (the engine's own arm).
        bus.on(net.zaminmc.torch.event.block.SignChangeEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    TorchWorld world = adapter.worldByName(engineEvent.worldName());
                    Player editor = adapter.playerByUuid(engineEvent.editorId());
                    if (world == null || editor == null) {
                        // A foreign world or an editor outside the adapter's
                        // player view — the loud log, no dispatch.
                        adapter.logger().warning(() -> "SignChange: the world '" 
                                + engineEvent.worldName() + "' or the editor '"
                                + engineEvent.editorName()
                                + "' has no adapter view — the event was not bridged");
                        return;
                    }
                    Block sign = adapter.blockAt(world, engineEvent.position().x(),
                            engineEvent.position().y(), engineEvent.position().z());
                    org.bukkit.event.block.SignChangeEvent legacy =
                            new org.bukkit.event.block.SignChangeEvent(
                                    sign, editor, engineEvent.lines().clone());
                    dispatch(legacy, "SignChange");
                    engineEvent.setCancelled(legacy.isCancelled());
                    if (!java.util.Arrays.equals(legacy.getLines(), engineEvent.lines())) {
                        // The legacy setLine rows map onto the native event's
                        // own rewrite arm (the walk applies the final array).
                        engineEvent.setLines(legacy.getLines());
                    }
                });
        // FurnaceBurn — the ignite gate, cancellable both ways.
        bus.on(net.zaminmc.torch.event.block.FurnaceBurnEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    TorchWorld world = adapter.worldByName(engineEvent.worldName());
                    if (world == null) {
                        adapter.logger().warning(() -> "FurnaceBurn: the world '"
                                + engineEvent.worldName()
                                + "' has no adapter view — the event was not bridged");
                        return;
                    }
                    ItemStack fuel = legacyItemView(engineEvent.fuel(), "FurnaceBurn.fuel");
                    if (fuel == null) {
                        return; // the unmapped item already logged loudly
                    }
                    Block furnace = adapter.blockAt(world, engineEvent.position().x(),
                            engineEvent.position().y(), engineEvent.position().z());
                    FurnaceBurnEvent legacy =
                            new FurnaceBurnEvent(furnace, fuel, engineEvent.burnTicks());
                    dispatch(legacy, "FurnaceBurn");
                    engineEvent.setCancelled(legacy.isCancelled());
                    // The readable-but-not-applied rows (§27.9): the native
                    // event carries the fuel's own burn value and ignite
                    // state read-only — the engine's own walk is authoritative.
                    if (legacy.getBurnTime() != engineEvent.burnTicks()) {
                        divergence("FurnaceBurn.setBurnTime");
                    }
                    if (!legacy.isBurning()) {
                        divergence("FurnaceBurn.setBurning(false)");
                    }
                });
        // FurnaceSmelt — the cook-completion gate, cancellable both ways.
        bus.on(net.zaminmc.torch.event.block.FurnaceSmeltEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    TorchWorld world = adapter.worldByName(engineEvent.worldName());
                    if (world == null) {
                        adapter.logger().warning(() -> "FurnaceSmelt: the world '"
                                + engineEvent.worldName()
                                + "' has no adapter view — the event was not bridged");
                        return;
                    }
                    ItemStack input = legacyItemView(engineEvent.input(), "FurnaceSmelt.input");
                    ItemStack result = legacyItemView(engineEvent.result(),
                            "FurnaceSmelt.result");
                    if (input == null || result == null) {
                        return; // the unmapped item already logged loudly
                    }
                    Block furnace = adapter.blockAt(world, engineEvent.position().x(),
                            engineEvent.position().y(), engineEvent.position().z());
                    FurnaceSmeltEvent legacy =
                            new FurnaceSmeltEvent(furnace, input, result);
                    dispatch(legacy, "FurnaceSmelt");
                    engineEvent.setCancelled(legacy.isCancelled());
                    // The readable-but-not-applied row (§27.9): the engine's
                    // recipe answer is authoritative — a rewritten result
                    // is logged, not applied.
                    if (!itemViewEquals(legacy.getResult(), result)) {
                        divergence("FurnaceSmelt.setResult");
                    }
                });
        adapter.logger().info("The event bridge's second group is wired: PlayerDamage, "
                + "PlayerDeath, FluidFlow, PistonMove (extend + retract), SignChange, "
                + "FurnaceBurn, FurnaceSmelt — cancellation: damage/flow/piston/sign/"
                + "furnace pre-commit SUPPORTED both ways, death not cancellable in the "
                + "legacy form (the 1.8.8 shape; the keepInventory arm mapped onto the "
                + "native cancel), each dispatch logged; the entity-backed families "
                + "(mob/item/projectile/vehicle spawn+damage+death) and the container "
                + "family stay unbridged — the recorded §27.9 gaps");
    }


    /**
     * The third group's wiring: the six entity-backed families the
     * slice-2 record demand-named — the org.bukkit.entity views
     * ({@link net.zaminmc.torch.bukkit.entity.TorchMob} live over the
     * damage/death bodies, the four pending views over the spawn
     * facts) carrying every legacy form. Every constructor shape was
     * verified against the pinned API artifact first (the slice-2
     * javap law): CreatureSpawnEvent(LivingEntity,SpawnReason),
     * ItemSpawnEvent(Item,Location), ProjectileLaunchEvent(Entity),
     * VehicleCreateEvent(Vehicle), EntityDeathEvent(LivingEntity,List,int).
     */
    private void attachThirdGroup() {
        net.zaminmc.torch.event.EventBus bus = adapter.engine().events();
        // MobSpawn — the pre-construction spawn gate: cancellable both
        // ways; the entity is the PENDING view (the class javadoc's
        // recorded §27.9 divergence); the reason answers DEFAULT (the
        // native event carries no reason row — the recorded
        // approximation).
        bus.on(net.zaminmc.torch.event.entity.MobSpawnEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    org.bukkit.entity.EntityType type =
                            net.zaminmc.torch.bukkit.entity.EntityTypeMap
                                    .ofMobIdentifier(engineEvent.mobType());
                    if (type == null) {
                        // An engine kind with no 1.8.8 identity cannot be
                        // named in the legacy event — the loud log, no
                        // dispatch (never a wrong entity).
                        return;
                    }
                    String bare = engineEvent.mobType().startsWith("minecraft:")
                            ? engineEvent.mobType().substring("minecraft:".length())
                            : engineEvent.mobType();
                    net.zaminmc.torch.server.entity.MobType mobType =
                            net.zaminmc.torch.server.entity.MobType.byName(bare);
                    if (mobType == null) {
                        return; // the EntityTypeMap already logged loudly
                    }
                    net.zaminmc.torch.bukkit.entity.TorchPendingMob view =
                            new net.zaminmc.torch.bukkit.entity.TorchPendingMob(
                                    adapter, mobType, engineEvent.position());
                    org.bukkit.event.entity.CreatureSpawnEvent legacy =
                            new org.bukkit.event.entity.CreatureSpawnEvent(
                                    view,
                                    org.bukkit.event.entity.CreatureSpawnEvent
                                            .SpawnReason.DEFAULT);
                    dispatch(legacy, "CreatureSpawn");
                    engineEvent.setCancelled(legacy.isCancelled());
                });
        // MobDamage — the pre-commit damage gate over the LIVE body:
        // cancellable both ways, the mutable amount mapped both ways
        // (the player arm's own law).
        bus.on(net.zaminmc.torch.event.entity.MobDamageEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    if (!(engineEvent.entity()
                            instanceof net.zaminmc.torch.server.entity.MobEntity mob)) {
                        // A foreign living body: not the adapter's to
                        // bridge — the loud log, no dispatch.
                        adapter.logger().warning(() -> "MobDamage: the body '"
                                + engineEvent.entity().typeIdentifier()
                                + "' is not an engine mob — the event was not bridged");
                        return;
                    }
                    net.zaminmc.torch.bukkit.entity.TorchMob view =
                            adapter.mobView(mob);
                    EntityDamageEvent legacy = new EntityDamageEvent(
                            view, legacyDamageCause(engineEvent.cause()),
                            engineEvent.amount());
                    dispatch(legacy, "EntityDamage(mob)");
                    engineEvent.setCancelled(legacy.isCancelled());
                    if ((float) legacy.getDamage() != engineEvent.amount()) {
                        // The legacy setDamage maps onto the engine's own
                        // mutable amount — the native event's contract.
                        engineEvent.amount((float) legacy.getDamage());
                    }
                    // The last-damage rows: the adapter-local presentation
                    // state the view documents (the engine carries no
                    // row — the native damage family is the observation
                    // surface).
                    view.lastDamageCause(legacy);
                });
        // MobDeath — the post-commit death observation over the LIVE
        // body: not cancellable (the 1.8.8 shape); the honest empty
        // drops shim; the exp rows readable-not-applied.
        bus.on(net.zaminmc.torch.event.entity.MobDeathEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    if (!(engineEvent.entity()
                            instanceof net.zaminmc.torch.server.entity.MobEntity mob)) {
                        adapter.logger().warning(() -> "MobDeath: the body '"
                                + engineEvent.entity().typeIdentifier()
                                + "' is not an engine mob — the event was not bridged");
                        return;
                    }
                    net.zaminmc.torch.bukkit.entity.TorchMob view =
                            adapter.mobView(mob);
                    org.bukkit.event.entity.EntityDeathEvent legacy =
                            new org.bukkit.event.entity.EntityDeathEvent(
                                    view, List.of(), 0);
                    dispatch(legacy, "EntityDeath(mob)");
                    // The readable-but-not-applied row (§27.9): the drops
                    // view is the unmodifiable empty shim (the native
                    // event fires pre-scatter — no honest drop list
                    // exists, the PlayerDeath precedent); the engine's
                    // scatter math is authoritative for the exp rows.
                    if (legacy.getDroppedExp() != 0) {
                        divergence("EntityDeath.setDroppedExp");
                    }
                });
        // ItemSpawn — the pre-spawn drop gate: cancellable both ways;
        // the entity is the pending Item view over the stack's legacy
        // value view.
        bus.on(net.zaminmc.torch.event.entity.ItemSpawnEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    ItemStack stack = legacyItemView(engineEvent.stack(),
                            "ItemSpawn.stack");
                    if (stack == null) {
                        return; // the unmapped item already logged loudly
                    }
                    net.zaminmc.torch.bukkit.entity.TorchPendingItem view =
                            new net.zaminmc.torch.bukkit.entity.TorchPendingItem(
                                    adapter, stack, engineEvent.position());
                    Location at = view.getLocation();
                    org.bukkit.event.entity.ItemSpawnEvent legacy =
                            new org.bukkit.event.entity.ItemSpawnEvent(view, at);
                    dispatch(legacy, "ItemSpawn");
                    engineEvent.setCancelled(legacy.isCancelled());
                });
        // ProjectileSpawn — the pre-launch gate: cancellable both
        // ways; the entity is the pending Projectile view with the
        // thrower resolved against the adapter's own views.
        bus.on(net.zaminmc.torch.event.entity.ProjectileSpawnEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    net.zaminmc.torch.bukkit.entity.TorchPendingProjectile view =
                            new net.zaminmc.torch.bukkit.entity.TorchPendingProjectile(
                                    adapter, engineEvent.kind(),
                                    engineEvent.throwerEntityId(),
                                    engineEvent.position());
                    org.bukkit.event.entity.ProjectileLaunchEvent legacy =
                            new org.bukkit.event.entity.ProjectileLaunchEvent(view);
                    dispatch(legacy, "ProjectileLaunch");
                    engineEvent.setCancelled(legacy.isCancelled());
                });
        // VehicleSpawn — the pre-spawn gate: the 1.8.8 form carries NO
        // cancel row (the reference's own shape — the native gate keeps
        // the capability, §28.10's law); the entity is the pending
        // Vehicle view.
        bus.on(net.zaminmc.torch.event.entity.VehicleSpawnEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    net.zaminmc.torch.bukkit.entity.TorchPendingVehicle view =
                            new net.zaminmc.torch.bukkit.entity.TorchPendingVehicle(
                                    adapter, engineEvent.kind(),
                                    engineEvent.position());
                    org.bukkit.event.vehicle.VehicleCreateEvent legacy =
                            new org.bukkit.event.vehicle.VehicleCreateEvent(view);
                    dispatch(legacy, "VehicleCreate");
                    // NOT cancellable in the 1.8.8 form — the native
                    // VehicleSpawnEvent's own cancel arm stays the
                    // engine-side verb (the class javadoc's table).
                });
        adapter.logger().info("The event bridge's third group is wired: MobSpawn, "
                + "MobDamage, MobDeath, ItemSpawn, ProjectileSpawn, VehicleSpawn "
                + "(the entity adapters' five families) — cancellation: spawn/damage/"
                + "launch pre-commit SUPPORTED both ways, death + vehicle-create not "
                + "cancellable in the legacy forms (the 1.8.8 shapes; the native gates "
                + "keep their own cancel arms), each dispatch logged; the container "
                + "family rides the inventory group below (the slice-4 lane)");
    }

    // ------------------------------------------------------------------ the inventory group (slice 4 — this lane's third group)

    /**
     * The inventory family's wiring (§28.7 — the slice-4 lane): the three
     * container families the slice-2 record named as the honest gaps —
     * the shared container-open observation → InventoryOpenEvent, the
     * dispense gate → BlockDispenseEvent, the hopper-transfer gate →
     * InventoryMoveItemEvent — every legacy object built over the
     * adapter's own inventory views (the org.bukkit.inventory family the
     * slice models), every constructor shape verified against the pinned
     * API artifact.
     *
     * <p><b>The inventory family's cancellation table (§28.7):</b></p>
     * <ul>
     *   <li>ContainerOpen → InventoryOpenEvent — the native event is
     *       POST-COMMIT by the engine's own design (§27.7: handlers
     *       observe, they do not veto — the window id is already assigned
     *       and the open walk continues after dispatch). The legacy
     *       cancellation therefore maps onto the engine's OWN close walk
     *       ({@code EngineServer.closeWindow} — the same walk the
     *       client-initiated close takes: carried state returns, the
     *       chest's lid closes for the other observers, the wire
     *       re-syncs), submitted to the tick lane and landing at the next
     *       tick boundary. The reference's pre-open packet suppression is
     *       a shape the post-commit event cannot express — the Open Window
     *       packet already left in the same open walk, so a cancelled
     *       open may leave a client-side ghost window until the player's
     *       own close (the reference server's own 1.8.8 behavior; the
     *       recorded §27.9 translation).</li>
     *   <li>Dispense → BlockDispenseEvent — pre-behavior cancellation
     *       SUPPORTED both ways (the engine's own gate: the stack stays
     *       in its slot untouched). {@code setItem} and {@code
     *       setVelocity} are readable-not-applied (the engine's behavior
     *       walk owns the launched stack — the reference computes its
     *       velocity INSIDE the behavior with the per-shot random family;
     *       the divergence is logged when a plugin rewrites either row).</li>
     *   <li>HopperTransfer → InventoryMoveItemEvent — pre-commit
     *       cancellation SUPPORTED both ways (the engine's own per-item
     *       gate: THIS item stays, the walk's own rules continue around
     *       it). {@code setItem} is readable-not-applied (the engine
     *       moves its own one-item stack — the reference's one-item-per-
     *       transfer law).</li>
     * </ul>
     *
     * <p><b>The honest gaps this slice still carries (§27.9 — every one
     * recorded with its concrete reason, none faked):</b> the open kinds
     * with no modeled view (the crafting table's session grid, the
     * enchanting table's and the anvil's menu state, the beacon's payment
     * slot, the villager's trade rows) log loudly and do NOT dispatch;
     * the ENTITY container forms (the horse, the chest minecart, the
     * hopper minecart) need org.bukkit.entity views — the entity-adapter
     * lane's own family (the parallel slice's territory); the hopper's
     * SUCK arm (the item entities in the collection box) needs an
     * Inventory view of an item ENTITY — the same lane's — so it logs
     * loudly and does not dispatch either. The chest family's own
     * ChestOpenEvent stays native-only: the SHARED ContainerOpenEvent is
     * this bridge's one source (the no-double-dispatch law).</p>
     */
    private void attachInventoryGroup() {
        net.zaminmc.torch.event.EventBus bus = adapter.engine().events();

        // ContainerOpen — the shared §27.7 post-commit observation over
        // every container family, translated into the legacy open event
        // through the adapter's own inventory views.
        bus.on(net.zaminmc.torch.event.player.ContainerOpenEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    PlayerSession session = sessionByUuid(engineEvent.openerId());
                    if (session == null) {
                        adapter.logger().warning(() -> "ContainerOpen: the opener '"
                                + engineEvent.openerName()
                                + "' has no adapter view — the event was not bridged");
                        return;
                    }
                    PlayerSession.ContainerKind kind = containerKindOf(engineEvent.kind());
                    if (kind == null) {
                        // An unknown family name — never faked.
                        adapter.logger().warning(() -> "ContainerOpen: the kind '"
                                + engineEvent.kind()
                                + "' is no engine container kind — the event was not bridged");
                        return;
                    }
                    org.bukkit.inventory.Inventory top =
                            net.zaminmc.torch.bukkit.inventory.TorchInventories
                                    .topOfOpenWindow(adapter, session, kind,
                                            engineEvent.position());
                    if (top == null) {
                        // The kinds this slice models no view of (the
                        // §27.9 gaps): the loud log, no dispatch, never a
                        // fake view.
                        adapter.logger().warning(() -> "ContainerOpen: the " + engineEvent.kind()
                                + " family has no adapter inventory view in this slice —"
                                + " the event was not bridged (the recorded §27.9 gap)");
                        return;
                    }
                    org.bukkit.event.inventory.InventoryOpenEvent legacy =
                            new org.bukkit.event.inventory.InventoryOpenEvent(
                                    net.zaminmc.torch.bukkit.inventory.TorchInventoryView
                                            .of(adapter, session, top));
                    dispatch(legacy, "InventoryOpen(" + engineEvent.kind() + ")");
                    if (legacy.isCancelled()) {
                        // The legacy cancellation mapped onto the engine's
                        // OWN close walk (the same surface the client close
                        // takes) — submitted to the tick lane, landing at
                        // the next boundary.
                        adapter.engine().closeWindow(session, engineEvent.windowId());
                    }
                });

        // Dispense — the §27.7 pre-behavior gate over the dispense family
        // (dispenser and dropper alike), cancellable both ways.
        bus.on(net.zaminmc.torch.event.block.DispenseEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    TorchWorld world = adapter.worldView(engineEvent.world());
                    if (world == null) {
                        adapter.logger().warning(() -> "Dispense: the world '"
                                + engineEvent.world().name()
                                + "' has no adapter view — the event was not bridged");
                        return;
                    }
                    ItemStack item = net.zaminmc.torch.bukkit.inventory.TorchItemStacks
                            .toLegacy(engineEvent.stack(), "Dispense.item", adapter.logger());
                    if (item == null) {
                        return; // the unmapped item already logged loudly
                    }
                    Block block = adapter.blockAt(world, engineEvent.origin().x(),
                            engineEvent.origin().y(), engineEvent.origin().z());
                    // The zero-vector record: the engine's native event
                    // carries no velocity row (the reference computes its
                    // launch INSIDE the behavior walk, per-shot random
                    // family) — the legacy constructor demands a Vector, so
                    // the bridge passes the zero vector and every
                    // setVelocity divergence is logged, never applied.
                    org.bukkit.util.Vector velocity = new org.bukkit.util.Vector(0, 0, 0);
                    org.bukkit.event.block.BlockDispenseEvent legacy =
                            new org.bukkit.event.block.BlockDispenseEvent(block, item, velocity);
                    dispatch(legacy, "BlockDispense");
                    engineEvent.setCancelled(legacy.isCancelled());
                    // The readable-but-not-applied rows (§27.9): the
                    // engine's reservoir stack is authoritative (setItem),
                    // and the behavior walk owns the launch (setVelocity).
                    if (!net.zaminmc.torch.bukkit.inventory.TorchItemStacks.viewEquals(
                            legacy.getItem(), item)) {
                        divergence("BlockDispense.setItem");
                    }
                    if (legacy.getVelocity().lengthSquared() != 0.0) {
                        divergence("BlockDispense.setVelocity"
                                + " (the engine's behavior walk owns the launch)");
                    }
                });

        // HopperTransfer — the §27.7 pre-commit gate over the hopper's
        // three arms, translated into the legacy move event: the PUSH arm
        // (the hopper is the source and the initiator), the PULL arm (the
        // container above is the source, the hopper the destination), and
        // the SUCK arm (the honest §27.9 gap — an item ENTITY source has
        // no Inventory view; the entity-adapter lane's family).
        bus.on(net.zaminmc.torch.event.block.HopperTransferEvent.class,
                net.zaminmc.torch.event.EventPriority.FIRST,
                engineEvent -> {
                    TorchWorld world = adapter.worldByName(engineEvent.worldName());
                    if (world == null) {
                        adapter.logger().warning(() -> "HopperTransfer: the world '"
                                + engineEvent.worldName()
                                + "' has no adapter view — the event was not bridged");
                        return;
                    }
                    if (engineEvent.direction()
                            == net.zaminmc.torch.event.block.HopperTransferEvent.Direction.SUCK) {
                        // The §27.9 honest gap: the suck's source is an
                        // item entity — the entity-adapter lane's view
                        // family. Loud, never faked.
                        adapter.logger().warning(() -> "HopperTransfer: the SUCK arm's"
                                + " item-entity source has no adapter Inventory view in"
                                + " this slice — the event was not bridged (the recorded"
                                + " §27.9 gap, the entity-adapter lane's family)");
                        return;
                    }
                    org.bukkit.inventory.Inventory source;
                    org.bukkit.inventory.Inventory destination;
                    boolean didSourceInitiate;
                    if (engineEvent.direction()
                            == net.zaminmc.torch.event.block.HopperTransferEvent.Direction.PUSH) {
                        // The hopper pushes into the container it faces:
                        // the hopper IS the source and the initiator.
                        source = net.zaminmc.torch.bukkit.inventory.TorchHopperInventory
                                .of(adapter, world.engineWorld(), engineEvent.hopper());
                        destination = net.zaminmc.torch.bukkit.inventory.TorchInventories
                                .containerAt(adapter, world.engineWorld(),
                                        engineEvent.counterpart());
                        didSourceInitiate = true;
                    } else {
                        // The pull from the container above: the container
                        // above is the source, the hopper the destination
                        // (the hopper remains the initiator — the legacy
                        // row carries didSourceInitiate=false).
                        source = net.zaminmc.torch.bukkit.inventory.TorchInventories
                                .containerAt(adapter, world.engineWorld(),
                                        engineEvent.counterpart());
                        destination = net.zaminmc.torch.bukkit.inventory.TorchHopperInventory
                                .of(adapter, world.engineWorld(), engineEvent.hopper());
                        didSourceInitiate = false;
                    }
                    if (source == null || destination == null) {
                        // A counterpart the adapter models no view of (the
                        // entity carts) or a hopper whose state vanished —
                        // the loud log, no dispatch, never a fake view.
                        adapter.logger().warning(() -> "HopperTransfer: the "
                                + engineEvent.direction() + " arm's ends have no adapter"
                                + " inventory views (source=" + (source == null ? "none" : "yes")
                                + ", destination=" + (destination == null ? "none" : "yes")
                                + ") — the event was not bridged");
                        return;
                    }
                    ItemStack item = net.zaminmc.torch.bukkit.inventory.TorchItemStacks
                            .toLegacy(engineEvent.item(), "HopperTransfer.item",
                                    adapter.logger());
                    if (item == null) {
                        return; // the unmapped item already logged loudly
                    }
                    // The 1.8.8 constructor's own parameter order, verified
                    // against the pinned artifact's bytecode: (source,
                    // ITEM, destination, didSourceInitiate).
                    org.bukkit.event.inventory.InventoryMoveItemEvent legacy =
                            new org.bukkit.event.inventory.InventoryMoveItemEvent(
                                    source, item, destination, didSourceInitiate);
                    dispatch(legacy, "InventoryMoveItem(" + engineEvent.direction() + ")");
                    engineEvent.setCancelled(legacy.isCancelled());
                    // The readable-but-not-applied row (§27.9): the engine
                    // moves its own one-item stack (the reference's
                    // one-item-per-transfer law) — a rewritten item is
                    // logged, not applied.
                    if (!net.zaminmc.torch.bukkit.inventory.TorchItemStacks.viewEquals(
                            legacy.getItem(), item)) {
                        divergence("InventoryMoveItem.setItem");
                    }
                });

        adapter.logger().info("The event bridge's inventory group is wired: ContainerOpen ->"
                + " InventoryOpen (cancellation mapped onto the engine's own close walk),"
                + " Dispense -> BlockDispense (cancellable both ways; the velocity row the"
                + " zero-vector record), HopperTransfer -> InventoryMoveItem (PUSH + PULL"
                + " cancellable both ways; the SUCK arm the recorded §27.9 gap) — the"
                + " entity container forms and the unmodeled open kinds stay unbridged,"
                + " each dispatch logged");
    }

    /** The opener lookup by uuid (the shared open event's own identity row). */
    private PlayerSession sessionByUuid(java.util.UUID uuid) {
        for (net.zaminmc.torch.entity.Player apiPlayer : adapter.engine().players()) {
            if (apiPlayer instanceof PlayerSession session
                    && session.uuid().equals(uuid)) {
                return session;
            }
        }
        return null;
    }

    /** The engine kind enum of the shared open event's kind name (null when unknown). */
    private static PlayerSession.ContainerKind containerKindOf(String kind) {
        try {
            return PlayerSession.ContainerKind.valueOf(kind);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
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

    // ------------------------------------------------------------------ the second group's translations

    /**
     * The engine damage classification → the 1.8.8 cause family. The
     * engine's single EXPLOSION row answers ENTITY_EXPLOSION (the §27.9
     * recorded approximation: the engine's own classification is one
     * family, the legacy form splits block/entity explosions and the
     * native event carries no split row). Every other engine cause has
     * its own 1.8.8 name.
     */
    private EntityDamageEvent.DamageCause legacyDamageCause(
            net.zaminmc.torch.event.DamageCause cause) {
        return switch (cause) {
            case MELEE -> EntityDamageEvent.DamageCause.ENTITY_ATTACK;
            case PROJECTILE -> EntityDamageEvent.DamageCause.PROJECTILE;
            case EXPLOSION -> EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
            case IN_FIRE -> EntityDamageEvent.DamageCause.FIRE;
            case ON_FIRE -> EntityDamageEvent.DamageCause.FIRE_TICK;
            case LAVA -> EntityDamageEvent.DamageCause.LAVA;
            case FALL -> EntityDamageEvent.DamageCause.FALL;
            case DROWN -> EntityDamageEvent.DamageCause.DROWNING;
            case CACTUS -> EntityDamageEvent.DamageCause.CONTACT;
            case STARVE -> EntityDamageEvent.DamageCause.STARVATION;
            case OUT_OF_WORLD -> EntityDamageEvent.DamageCause.VOID;
            case MAGIC -> EntityDamageEvent.DamageCause.MAGIC;
            case LIGHTNING -> EntityDamageEvent.DamageCause.LIGHTNING;
            case IN_WALL -> EntityDamageEvent.DamageCause.SUFFOCATION;
        };
    }

    /** The engine's six facings are the legacy six faces (identical names). */
    private BlockFace legacyFace(net.zaminmc.torch.block.Direction facing) {
        return switch (facing) {
            case DOWN -> BlockFace.DOWN;
            case UP -> BlockFace.UP;
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
            case EAST -> BlockFace.EAST;
        };
    }

    /**
     * One engine stack's legacy value view (the furnace family's own
     * translation): the identifier's bare path against the API's own
     * {@link Material#matchMaterial} resolver (the MaterialMap law — the
     * engine's lowercase paths ARE the legacy names), the count and the
     * damage/dual-role variant riding along. A fresh value snapshot (the
     * event's own copy — never a live view; the native events themselves
     * declare their stacks immutable reads). An unmapped identity logs
     * loudly and answers null — the caller's no-dispatch law, never a
     * wrong item.
     */
    private ItemStack legacyItemView(net.zaminmc.torch.item.ItemStack stack, String what) {
        if (stack == null || stack.isEmpty() || stack.type() == null) {
            return null;
        }
        String path = stack.type().identifier().value();
        Material material = Material.matchMaterial(path);
        if (material == null) {
            adapter.logger().warning(() -> what + ": the engine item minecraft:"
                    + path + " has no 1.8.8 Material — the event was not bridged");
            return null;
        }
        return new ItemStack(material, stack.count(), (short) stack.damage());
    }

    /** The value-equality read for the setResult divergence check. */
    private boolean itemViewEquals(ItemStack one, ItemStack two) {
        return one.getType() == two.getType()
                && one.getAmount() == two.getAmount()
                && one.getDurability() == two.getDurability();
    }
}
