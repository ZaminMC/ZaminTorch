package net.zaminmc.torch.server.interaction;

import net.zaminmc.torch.util.Position;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineTicker;
import net.zaminmc.torch.server.block.BlockBehavior;
import net.zaminmc.torch.server.block.BlockBehaviorTable;
import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.event.EngineEventBus;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.event.block.BlockBreakEvent;
import net.zaminmc.torch.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * The single semantic entry point for player-driven block changes (§208/§215/§216).
 *
 * <p>Flow: intent (from a protocol adapter) -&gt; tick-thread task -&gt; validate -&gt;
 * commit -&gt; publish. All validation and mutation runs on the world's owning
 * thread, so no torn reads are possible and ordering matches the simulation.
 * Interaction latency is therefore at most one tick, matching tick-based
 * historical behavior (§214).</p>
 *
 * <p>Modes (§415 behavior first): creative interaction is instant and
 * inventory-free. Survival mining is the vanilla 1.8.8 ServerPlayerInteraction-
 * Manager flow, ported from {@code reference/1.8.8/net/minecraft/server/
 * ServerPlayerInteractionManager.java}: the client proposes start/abort/finish;
 * the server accumulates per-tick progress ({@code getMiningSpeed * (elapsed +
 * 1)}), broadcasts the crack stages, accepts a finish once the accumulated
 * progress reaches the vanilla 0.7 threshold, and lets an early finish keep
 * accumulating to 1.0 — the vanilla self-completion instead of a resync (the
 * honest-but-laggy client's prediction stands, exactly as vanilla).
 * Instant-progress blocks (torch, flora) break on the START packet like the
 * vanilla {@code startMiningBlock}.</p>
 */
public final class BlockInteractionService {

    private static final Logger LOGGER = Logger.getLogger(BlockInteractionService.class.getName());

    /**
     * The vanilla finish threshold ({@code ServerPlayerInteractionManager.
     * finishMiningBlock}: {@code f >= 0.7F} on the accumulated progress, not
     * wall time — network jitter and tick quantization included, the dig may
     * legitimately complete at 70% of the nominal duration).
     */
    static final double VANILLA_FINISH_ACCEPT = 0.7;

    // The anti-cheat budgets (the NCP shapes, server-lag simplified to
    // server-side clocks): the rolling break window and the fastplace band.
    static final long BREAK_WINDOW_NANOS = 1_000_000_000L; // 20 ticks
    static final int SURVIVAL_BREAK_BUDGET = 25;  // finishes per window (NCP 45/s with headroom)
    static final int CREATIVE_BREAK_BUDGET = 45;  // the historical creative pace
    static final int NUKER_KICK_VIOLATIONS = 10;
    static final long PLACE_WINDOW_NANOS = 500_000_000L; // 10 ticks
    static final int PLACE_BUDGET = 6;            // places per 10 ticks (NCP FastPlace)
    static final int FASTPLACE_KICK_VIOLATIONS = 10;

    private final EngineWorld world;
    /**
     * The dimension resolver (Wave VT-002 — the reference's own per-packet
     * consult: {@code ServerPlayNetworkHandler.handlePlayerHandAction} opens
     * with {@code ServerWorld serverworld = this.server.getWorld(this.player
     * .dimension)} and every dig/use/place consult below rides it; the
     * manager itself is constructed per world, {@code new
     * ServerPlayerInteractionManager(this.server.getWorld(player.dimension))}
     * — PlayerManager lines 349-372). The engine's single service resolves
     * the digger's own world per walk instead; the {@link #world} field
     * stays the overworld (the dimension-0 resolution) for the pre-tick
     * gates that consult registry-level state alone.
     */
    private final java.util.function.IntFunction<EngineWorld> worldFor;
    private final EngineTicker ticker;
    private final java.util.function.BiConsumer<EngineWorld, BlockChange> publisher;
    /** The service's gameplay random (the wear walks' roll source). Tick-thread only. */
    private final java.util.Random gameplayRandom = new java.util.Random();
    /** Per-player game mode resolver: the session's own mode decides the rule. */
    private final java.util.function.Function<PlayerSession, GameMode> gameMode;
    private final DropService dropService;
    private final ItemEntityManager itemEntities;
    private final java.util.function.BiFunction<net.zaminmc.torch.item.ItemType, Integer,
            java.util.Optional<BlockType>> blockItemResolver;
    private final java.util.function.Consumer<PlayerSession> inventorySync;
    /** Post-break hook (furnace spill, future block entities). Tick-thread context.
     *  Wave VT-002: the world the break ran in rides along (the reference's
     *  onRemoved consults the broken block's own world). */
    private volatile BlockBrokenListener blockBrokenListener;

    /** The post-break hook's own signature (the world, the cell, the broken type). */
    public interface BlockBrokenListener {
        /** One committed break, either mode. Tick-thread context. */
        void onBlockBroken(EngineWorld world, BlockPosition position, BlockType brokenType);
    }
    /** The survival-break XP hook (mining awards; creative breaks never fire it). */
    private volatile SurvivalXpHook survivalXpListener;
    /**
     * The survival-break afterMinedByPlayer hook (the tile-reading drops —
     * the reference's arm runs in tryMineBlock's non-creative branch alone;
     * creative breaks never fire it).
     */
    private volatile SurvivalMinedHook survivalMinedListener;
    /** The world-mutation feedback hook (break/place FX); null until registered. */
    private volatile java.util.function.Consumer<Commit> commitFeedbackListener;
    /** The dig-progress broadcast hook (the 0x28 fan-out); null until registered. */
    private volatile MiningProgressListener miningProgressListener;
    /** The dig-start hook (Slice 27a — the reference's block.startMining arm,
     *  the note block's play); null until registered. Tick-thread context.
     *  Wave VT-002: the world the dig runs in rides along (the reference's
     *  startMining consults {@code this.world}, the player's own). */
    private volatile java.util.function.BiConsumer<EngineWorld, BlockPosition> miningStartListener;
    /** The public event bus (§27.7 observe/cancel); null until wired. */
    private volatile EngineEventBus eventBus;
    /**
     * The {@code doTileDrops} read (Slice 24a — Block.java's dropBlock
     * gate: the break still commits, nothing lands on the ground). Null
     * keeps the historical always-drop behavior.
     */
    private volatile java.util.function.BooleanSupplier tileDropsRead;

    /**
     * The status-effect amplifier read the dig consults (the mining-speed
     * parity audit — {@code PlayerEntity.hasStatusEffect} + {@code
     * getEffectInstance(...).getAmplifier()}); the server wires it to the
     * potion system, the no-hook default answering the no-effect amplifier.
     */
    private volatile java.util.function.BiFunction<PlayerSession, Integer, Integer> effectAmplifierRead;

    /** Wires the dig's status-effect read ({@code amplifierOf} shape). */
    public void setEffectAmplifierRead(
            java.util.function.BiFunction<PlayerSession, Integer, Integer> read) {
        this.effectAmplifierRead = read;
    }

    /**
     * The placement-support gate (Slice 44 — PlantBlock.canBePlaced over
     * canBePlacedOn: the flora consult refusing a placement whose soil is
     * wrong BEFORE any placement is attempted, the reference's own
     * no-op). Null keeps the historical place-anywhere behavior for
     * non-gated kinds. Wave VT-002: the world the placement runs in rides
     * along (the reference's canBePlaced consults the placing player's
     * own world's soil).
     */
    private volatile PlacementSupportGate placementSupportGate;

    /** The support gate's own signature (the world, the target, the held block). */
    public interface PlacementSupportGate {
        /** True = the placement may proceed. Tick-thread context. */
        boolean test(EngineWorld world, BlockPosition target, BlockType held);
    }

    /**
     * The placement's BlockEntityTag consult (Wave H2 — BlockItem.useOn
     * lines 46-51 -> setTileNbt lines 70-100): the held stack's raw
     * compound merged into the freshly-placed tile's rows, right after
     * the commit. Null keeps the placement bare (the pre-H2 behavior).
     */
    private volatile PlacementTagConsult placementTagConsult;

    /**
     * The dig-start consult (Slice N6 — handlePlayerDig lines 437-445,
     * 1:1): {@code !this.server.isSpawnProtected(...) && serverworld
     * .getWorldBorder().contains(blockpos)} — the consult whose refusal
     * answers the BlockUpdate echo instead of opening the dig. True =
     * the dig MAY open; null keeps the ungated historical path (the
     * engine's internal API path — the reference's consults ride the
     * network handler, the engine's seam is the service's own walk).
     */
    private volatile java.util.function.BiPredicate<PlayerSession, BlockPosition> digStartGate;

    /**
     * The fire-extinguish FX hook (Slice N6 — World.extinguishFire line
     * 1642's {@code doEvent(player, 1004, pos, 0)}): the fizz broadcast
     * at the removed fire cell. Null keeps the removal silent.
     */
    private volatile java.util.function.Consumer<BlockPosition> fireExtinguishFx;

    /**
     * The consult's own signature (the engine-side seam: the world, the
     * dimension id for the tile family's key, the position, the held
     * stack, and the placer's op level — the reference's isOp gate).
     */
    public interface PlacementTagConsult {
        boolean apply(EngineWorld world, int dimension, BlockPosition position,
                      net.zaminmc.torch.item.ItemStack held, int opLevel);
    }

    /** Wires the {@code doTileDrops} read (the game-rule table's block-drop gate). */
    public void setTileDropsRead(java.util.function.BooleanSupplier tileDropsRead) {
        this.tileDropsRead = java.util.Objects.requireNonNull(tileDropsRead, "tileDropsRead");
    }

    /** Wires the placement-support gate (the flora's canBePlaced consult). */
    public void setPlacementSupportGate(PlacementSupportGate gate) {
        this.placementSupportGate = java.util.Objects.requireNonNull(gate, "gate");
    }

    /** Wires the placement's BlockEntityTag consult (the tile NBT seam). */
    public void setPlacementTagConsult(PlacementTagConsult consult) {
        this.placementTagConsult = java.util.Objects.requireNonNull(consult, "consult");
    }

    /**
     * Wires the dig-start consult (the spawn-protection + border pair,
     * the reference's own {@code !isSpawnProtected && contains} band).
     * True = the dig may open; false = the refusal answers the echo.
     */
    public void setDigStartGate(
            java.util.function.BiPredicate<PlayerSession, BlockPosition> gate) {
        this.digStartGate = java.util.Objects.requireNonNull(gate, "gate");
    }

    /**
     * Wires the fire-extinguish FX (the reference's doEvent 1004 — the
     * fizz at the removed fire cell).
     */
    public void setFireExtinguishFx(java.util.function.Consumer<BlockPosition> fx) {
        this.fireExtinguishFx = java.util.Objects.requireNonNull(fx, "fx");
    }

    /**
     * A mode-aware break report (Slice 45 — the double plants'
     * beforeMinedByPlayer: the UPPER's break needs the player's mode and
     * held item — creative removes the lower silently, survival walks the
     * lower's drops with the shears arm). Null until wired.
     */
    public interface BreakReport {
        /**
         * One committed break, either mode. {@code previous} is the broken
         * type (the cell is already air). Tick-thread context.
         */
        void onBreak(PlayerSession player, BlockPosition position,
                BlockType previous, boolean creative);
    }

    private volatile BreakReport breakReport;

    /** Wires the mode-aware break report (the double-plant pair arm). */
    public void setBreakReport(BreakReport report) {
        this.breakReport = java.util.Objects.requireNonNull(report, "report");
    }

    /**
     * A player-aware dig-start report (Slice 46 — CakeBlock.startMining
     * lines 62-66: the left click's eat needs the acting player). Null
     * until wired; fires right after the position listener, same context.
     * Wave VT-002: the world the dig runs in rides along (the cake/egg/note
     * arms consult the digger's own world, not the service's overworld).
     */
    public interface MiningStartReport {
        /** One dig start, with the digger's own world. Tick-thread context. */
        void onStartReport(EngineWorld world, PlayerSession player, BlockPosition at);
    }

    private volatile MiningStartReport miningStartReport;

    /** Wires the player-aware dig-start report (the cake's eat arm). */
    public void setMiningStartReport(MiningStartReport report) {
        this.miningStartReport = java.util.Objects.requireNonNull(report, "report");
    }

    /** Wires the dig-start hook (the reference's startMining arm — the note
     *  block's play on the survival dig's start). */
    public void setMiningStartListener(
            java.util.function.BiConsumer<EngineWorld, BlockPosition> listener) {
        this.miningStartListener = java.util.Objects.requireNonNull(listener, "listener");
    }

    /**
     * The digger's own world (the reference's {@code this.server.getWorld(
     * this.player.dimension)} — every dig/use/place walk resolves the
     * player's dimension once at its head, the same {@code worldFor}
     * consult the FL4 arms use). Tick-thread context.
     */
    private EngineWorld worldOf(PlayerSession player) {
        return worldFor.apply(player.dimension());
    }

    /** Active survival mining sessions, keyed by player. Tick-thread confined. */
    private final Map<UUID, MiningSession> miningSessions = new HashMap<>();

    /** A committed block change, published to observers (adapter sync). */
    public record BlockChange(BlockPosition position, BlockType newType) {
    }

    /**
     * One survival dig state, the vanilla two-slot model tick-thread confined:
     * {@code target} is the live dig (the vanilla {@code isMiningBlock}, fed
     * by START and finished by the finish packet), {@code prevTarget} is an
     * early-finished dig still accumulating to 1.0 (the vanilla {@code
     * wasMiningBlock}, which self-completes server-side). Both can coexist per
     * player, exactly as vanilla. Progress is derived from the world tick
     * clock ({@code getMiningSpeed * (elapsed + 1)}), never wall time.
     */
    private static final class MiningSession {
        final PlayerSession digger;
        BlockPosition target;
        long startTick;
        int lastStage = -1;
        BlockPosition prevTarget;
        long prevStartTick;
        int prevLastStage = -1;

        MiningSession(PlayerSession digger) {
            this.digger = digger;
        }
    }

    public BlockInteractionService(EngineWorld world, java.util.function.IntFunction<EngineWorld> worldFor,
                                   EngineTicker ticker,
                                   java.util.function.BiConsumer<EngineWorld, BlockChange> publisher, java.util.function.Function<PlayerSession, GameMode> gameMode,
                                   DropService dropService, ItemEntityManager itemEntities,
                                   java.util.function.BiFunction<net.zaminmc.torch.item.ItemType, Integer,
                                           java.util.Optional<BlockType>> blockItemResolver,
                                   java.util.function.Consumer<PlayerSession> inventorySync) {
        this.world = Objects.requireNonNull(world, "world");
        this.worldFor = Objects.requireNonNull(worldFor, "worldFor");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.gameMode = Objects.requireNonNull(gameMode, "gameMode");
        this.dropService = Objects.requireNonNull(dropService, "dropService");
        this.itemEntities = Objects.requireNonNull(itemEntities, "itemEntities");
        this.blockItemResolver = Objects.requireNonNull(blockItemResolver, "blockItemResolver");
        this.inventorySync = Objects.requireNonNull(inventorySync, "inventorySync");
    }

    /**
     * Requests an instant (creative) break. Safe from any thread. The
     * compat overload without the face (the engine's internal API path
     * — Slice N6's documented adaptation: the reference's every dig
     * carries the packet's face; the internal path's face-less break
     * simply never arms the extinguish).
     */
    public void submitCreativeBreak(PlayerSession player, BlockPosition position) {
        submitCreativeBreak(player, position, -1);
    }

    /**
     * Requests an instant (creative) break with the dig packet's face.
     * Safe from any thread. THE CREATIVE EXTINGUISH-FIRST LAW
     * (ServerPlayerInteractionManager.startMiningBlock lines 105-110,
     * 1:1): {@code if (!extinguishFire(null, pos, face)) tryMineBlock}
     * — a fire block at the face-offset cell consumes the click itself
     * (the fire dies, the block behind it SURVIVES).
     */
    public void submitCreativeBreak(PlayerSession player, BlockPosition position, int face) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(position, "position");
        ticker.submit(() -> creativeBreakOnTick(player, position, face));
    }

    /**
     * Reports the start of a survival dig. Safe from any thread. The
     * compat overload without the face (the internal API path — no
     * extinguish arm arms).
     */
    public void submitMiningStart(PlayerSession player, BlockPosition target) {
        submitMiningStart(player, target, -1);
    }

    /**
     * Reports the start of a survival dig with the dig packet's face.
     * Safe from any thread. THE EXTINGUISH ARM
     * (ServerPlayerInteractionManager.startMiningBlock line 128, 1:1):
     * every survival dig start runs {@code world.extinguishFire(null,
     * pos, face)} — a fire block at the face-offset cell dies (the dig
     * still opens: the reference discards the return on the survival
     * path, lines 128+).
     */
    public void submitMiningStart(PlayerSession player, BlockPosition target, int face) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        ticker.submit(() -> miningStartOnTick(player, target, face));
    }

    /**
     * Reports an aborted survival dig (client moved away or released).
     * Safe from any thread. The compat overload without the packet's
     * position (the internal API path — no reach band, no tail echo:
     * the reference's abort gates and echo both read the packet's own
     * blockpos, lines 424-452).
     */
    public void submitMiningAborted(PlayerSession player) {
        submitMiningAborted(player, null);
    }

    /**
     * Reports an aborted survival dig with the packet's position. Safe
     * from any thread. THE ABORT'S OWN GATES (handlePlayerDig lines
     * 424-433 + 449-452, 1:1): an abort beyond the 6-block dig band
     * answers NOTHING (the session stays live, exactly the reference's
     * {@code return}), and the abort's tail answers the authoritative
     * state of the packet's cell when it still holds its block.
     */
    public void submitMiningAborted(PlayerSession player, BlockPosition target) {
        Objects.requireNonNull(player, "player");
        ticker.submit(() -> stopMiningOnTick(player, target));
    }

    /**
     * Reports the completion of a survival dig. Safe from any thread.
     */
    public void submitMiningFinished(PlayerSession player, BlockPosition target) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        ticker.submit(() -> miningFinishOnTick(player, target));
    }

    /**
     * Requests placement from the player's authoritative inventory (§430): the
     * held stack must be a registered block item; one unit is consumed
     * atomically with the commit (§431/§432). Safe from any thread.
     */
    public void submitSurvivalPlace(PlayerSession player, BlockPosition clicked, int face) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(clicked, "clicked");
        ticker.submit(() -> survivalPlaceOnTick(player, clicked, face));
    }

    /**
     * Requests placement against the clicked block and face, using the item the
     * client claims to hold (validated against the registry). Creative path.
     * Safe from any thread.
     */
    public void submitPlace(PlayerSession player, BlockPosition clicked, int face, BlockType held) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(clicked, "clicked");
        Objects.requireNonNull(held, "held");
        if (held.equals(world.airType())) {
            return; // placing "nothing" is a no-op, not an error
        }
        ticker.submit(() -> placeOnTick(player, clicked, face, held));
    }

    // ---- tick-thread execution ---------------------------------------------

    private void creativeBreakOnTick(PlayerSession player, BlockPosition position, int face) {
        // The digger's own world (Wave VT-002 — the reference's per-packet
        // {@code this.server.getWorld(this.player.dimension)} consult).
        EngineWorld world = worldOf(player);
        BlockType current = world.getBlock(position);
        if (current.equals(world.airType())) {
            return; // nothing to break: no-op, no error
        }
        // THE DIG REACH BAND (handlePlayerDig lines 424-433, 1:1 — the
        // creative break rides the same case band): from the feet raised
        // by the reference's own + 1.5 to the target cell's centre,
        // squared at most 36.0 — a break beyond six blocks answers
        // NOTHING (the reference's {@code d3 > 36.0} gate guards the
        // interactionManager call for every mode alike).
        if (!InteractionRules.withinDigReach(player.position(), position)) {
            LOGGER.fine(() -> "Rejected creative break (beyond the dig band) by " + player.name());
            return;
        }
        // THE CREATIVE EXTINGUISH-FIRST LAW (startMiningBlock lines
        // 105-110, 1:1): {@code if (!this.world.extinguishFire(null, pos,
        // face)) this.tryMineBlock(pos)} — a fire block at the
        // face-offset cell consumes the click itself: the fire dies with
        // its fizz, the block behind it SURVIVES (no break event, no
        // drops, nothing spends).
        if (extinguishFireAt(world, face >= 0 ? position : null, face)) {
            return; // the extinguish consumed the click
        }
        // The adventure mine gate (Wave I — a restricted placer's held
        // stack must carry the block in its CanDestroy list; creative
        // passes through mayModifyWorld untouched).
        if (!canMineBlock(player, heldOf(player), current)) {
            LOGGER.fine(() -> "Rejected break (adventure CanDestroy) by " + player.name());
            resync(world, position);
            return;
        }
        // The nuker guard runs creative too (the higher creative budget).
        long nowNanos = System.nanoTime();
        if (!player.violations().recordBreak(nowNanos, BREAK_WINDOW_NANOS, CREATIVE_BREAK_BUDGET)
                || player.violations().isMultiBreak(nowNanos, position)) {
            player.violations().addNukerViolation();
            resync(world, position);
            LOGGER.warning(() -> "Nuker violation (creative) for " + player.name()
                    + " (vl " + player.violations().nukerViolations() + ")");
            if (player.violations().nukerViolations() >= NUKER_KICK_VIOLATIONS) {
                player.link().kick("Nuker");
            }
            return;
        }
        player.violations().noteBreakTarget(nowNanos, position);
        BlockType previous = world.getBlock(position);
        if (!fireBreak(world, player, position, previous)) {
            resync(world, position); // drop the client's prediction: the break was cancelled
            return;
        }
        commit(world, position, world.airType());
        // The break hook fires in creative too: door halves die as a unit and
        // container contents spill (the historical creative-break behavior).
        BlockBrokenListener listener = blockBrokenListener;
        if (listener != null) {
            listener.onBlockBroken(world, position, previous);
        }
        BreakReport report = breakReport;
        if (report != null) {
            report.onBreak(player, position, previous, true);
        }
    }

    /**
     * The vanilla {@code startMiningBlock}: opens (or re-targets) the live
     * dig. Instant-progress blocks break on the START itself; everything else
     * starts accumulating and broadcasts its initial stage.
     */
    private void miningStartOnTick(PlayerSession player, BlockPosition target, int face) {
        // The digger's own world (Wave VT-002 — the reference's per-packet
        // {@code this.server.getWorld(this.player.dimension)} consult).
        EngineWorld world = worldOf(player);
        // THE DIG REACH BAND (handlePlayerDig lines 424-433, 1:1): the
        // dig's own six-block band from the feet raised by the
        // reference's + 1.5 — a START beyond it answers NOTHING (no dig
        // opens, no echo). THE REAL BUG THIS REPAIRS: the old band was
        // the 4.5-block survival reach from the 1.62 standing eye — a
        // dig at five blocks that vanilla opens, the engine refused.
        if (!InteractionRules.withinDigReach(player.position(), target)) {
            LOGGER.fine(() -> "Rejected mining start (beyond the dig band) by " + player.name());
            return;
        }
        // THE START CONSULTS (handlePlayerDig lines 437-445, 1:1): the
        // reference's {@code !this.server.isSpawnProtected(serverworld,
        // blockpos, this.player) && serverworld.getWorldBorder()
        // .contains(blockpos)} — a refused consult answers the BlockUpdate
        // echo (the client's predicted dig-start ghost reverts) and NO
        // dig opens; the passed consult opens the dig exactly like the
        // reference's interactionManager.startMiningBlock call.
        java.util.function.BiPredicate<PlayerSession, BlockPosition> startGate = digStartGate;
        if (startGate != null && !startGate.test(player, target)) {
            player.link().blockResync(target, world.getBlock(target));
            LOGGER.fine(() -> "Refused mining start (spawn-protection/border) by "
                    + player.name() + " — the refusal echo answers");
            return;
        }
        BlockBehavior behavior = BlockBehaviorTable.of(world.getBlock(target).identifier()).orElse(null);
        if (behavior == null || !behavior.diggable() || behavior.hardness() < 0) {
            return; // air or unbreakable: no dig (bedrock never opens one)
        }
        // The adventure mine gate (Wave I — ServerPlayerInteractionManager
        // 112-133: a restricted placer's held stack must carry the block
        // in its CanDestroy list; a refusal never opens the dig session).
        if (!canMineBlock(player, heldOf(player), world.getBlock(target))) {
            LOGGER.fine(() -> "Rejected mining start (adventure CanDestroy) by " + player.name());
            return;
        }
        // THE EXTINGUISH ARM (startMiningBlock line 128, 1:1): every
        // survival dig start runs {@code world.extinguishFire(null, pos,
        // face)} — a fire block at the face-offset cell dies with its
        // fizz; the return is discarded, the dig still opens. THE ORDER
        // 1:1: the reference's mode gates run FIRST (the spectator and
        // CanDestroy refusals above) — a refused dig never extinguishes.
        extinguishFireAt(world, face >= 0 ? target : null, face);
        // The reference's startMining arm (ServerPlayerInteractionManager
        // line 133: block.startMining before the speed consult) — the note
        // block plays here; creative never reaches this path.
        java.util.function.BiConsumer<EngineWorld, BlockPosition> startListener = miningStartListener;
        if (startListener != null) {
            startListener.accept(world, target);
        }
        MiningStartReport startReport = miningStartReport;
        if (startReport != null) {
            startReport.onStartReport(world, player, target);
        }
        MiningSession session = miningSessions.computeIfAbsent(player.uuid(),
                id -> new MiningSession(player));
        // The vanilla flow: a new START overwrites the live dig without an
        // animation removal (the old block's overlay dies with its block
        // change); a pending wasMining accumulation keeps running.
        session.target = target;
        session.startTick = world.totalTicks();
        double perTick = perTickProgress(world, player, target, behavior);
        if (perTick >= 1.0) {
            // The vanilla instant rule: f >= 1.0 at start mines immediately.
            session.target = null;
            if (survivalBreakAllowed(world, player, target)) {
                tryMineBlock(world, player, target, behavior);
            }
            return;
        }
        session.lastStage = (int) (perTick * 10.0);
        publishProgress(player, target, session.lastStage);
    }

    /**
     * The vanilla {@code finishMiningBlock} wrapped in the dig handler's
     * own band and tail (handlePlayerDig lines 424-456, 1:1): the FINISH
     * beyond the six-block dig band answers NOTHING (the block never
     * breaks — the reference's {@code d3 > 36.0} gate refuses before the
     * interactionManager call), and after every other outcome the walk's
     * tail answers the authoritative state of the packet's cell when it
     * still holds its block — THE TAIL ECHO (lines 449-452): the accepted
     * finish (the cell now air) echoes nothing; the early-parked finish,
     * the refused finish, and the cancelled break all answer the truth
     * (the client's predicted ghost reverts — the never-desync guarantee
     * that replaces the walk's old internal resyncs: the reference's
     * finish walk carries no echoes of its own, the handler's single
     * tail echo covers them all).
     */
    private void miningFinishOnTick(PlayerSession player, BlockPosition target) {
        // The digger's own world (Wave VT-002 — the reference's per-packet
        // {@code this.server.getWorld(this.player.dimension)} consult).
        EngineWorld world = worldOf(player);
        // THE DIG REACH BAND: a finish beyond six blocks answers NOTHING.
        if (!InteractionRules.withinDigReach(player.position(), target)) {
            LOGGER.fine(() -> "Rejected mining finish (beyond the dig band) by "
                    + player.name());
            return;
        }
        try {
            miningFinishWalk(world, player, target);
        } finally {
            // THE TAIL ECHO (handlePlayerDig lines 449-452, 1:1): after
            // the finish — accepted, parked, refused, or cancelled — the
            // cell still holding its block answers the clicking player's
            // link with the truth (the reference's own BlockUpdate:
            // {@code getBlockState(blockpos).getBlock().getMaterial()
            // != Material.AIR -> sendPacket}).
            if (!world.getBlock(target).equals(world.airType())) {
                player.link().blockResync(target, world.getBlock(target));
            }
        }
    }

    /** The finish walk itself (the reference's finishMiningBlock shape). */
    private void miningFinishWalk(EngineWorld world, PlayerSession player, BlockPosition target) {
        BlockType current = world.getBlock(target);
        if (current.equals(world.airType())) {
            return; // already gone (race or double finish): no-op
        }
        MiningSession session = miningSessions.get(player.uuid());
        if (session == null || session.target == null || !session.target.equals(target)) {
            LOGGER.fine(() -> "Rejected mining finish (no session/wrong target) by "
                    + player.name());
            return;
        }
        // The adventure mine gate's second arm (Wave I — the reference's
        // tryHarvestBlock 195-211: the same consult at the harvest).
        if (!canMineBlock(player, heldOf(player), current)) {
            LOGGER.fine(() -> "Rejected mining finish (adventure CanDestroy) by " + player.name());
            return;
        }
        BlockBehavior behavior = BlockBehaviorTable.of(current.identifier()).orElse(null);
        if (behavior == null || !behavior.diggable()) {
            return;
        }
        long elapsedTicks = world.totalTicks() - session.startTick;
        double progress = perTickProgress(world, player, target, behavior) * (elapsedTicks + 1);
        if (progress >= VANILLA_FINISH_ACCEPT) {
            // The vanilla accepted finish: the animation dies, the block mines.
            session.target = null;
            publishProgress(player, target, REMOVAL_STAGE);
            if (survivalBreakAllowed(world, player, target)) {
                tryMineBlock(world, player, target, behavior);
            }
        } else {
            // The vanilla early finish: the dig keeps accumulating and
            // self-completes later — the walk itself stays silent (the
            // TAIL ECHO outside answers the still-standing block, exactly
            // the reference's handler-level BlockUpdate the old code's
            // "prediction stays" comment missed).
            session.prevTarget = session.target;
            session.prevStartTick = session.startTick;
            session.prevLastStage = session.lastStage;
            session.target = null;
        }
    }

    /**
     * The vanilla {@code stopMiningBlock} wrapped in the dig handler's
     * own band and tail (handlePlayerDig lines 424-456, 1:1): the ABORT
     * beyond the six-block dig band answers NOTHING — the session stays
     * live, exactly the reference's {@code return} (an ignored abort
     * never stops the dig); the walk's tail answers the authoritative
     * state of the packet's cell when it still holds its block — the
     * same tail echo the finish carries (the reference's shared
     * else-band tail, lines 449-452). The compat path (a null target,
     * the internal API's face-less abort) runs the walk ungated with no
     * tail, the pre-N6 behavior.
     */
    private void stopMiningOnTick(PlayerSession player, BlockPosition packetTarget) {
        // The digger's own world (Wave VT-002 — the reference's per-packet
        // {@code this.server.getWorld(this.player.dimension)} consult).
        EngineWorld world = worldOf(player);
        if (packetTarget != null) {
            // THE DIG REACH BAND: an abort beyond six blocks is IGNORED.
            if (!InteractionRules.withinDigReach(player.position(), packetTarget)) {
                LOGGER.fine(() -> "Ignored mining abort (beyond the dig band) by "
                        + player.name() + " — the session stays live");
                return;
            }
        }
        try {
            stopMiningWalk(player);
        } finally {
            if (packetTarget != null
                    && !world.getBlock(packetTarget).equals(world.airType())) {
                // THE TAIL ECHO: the cleared dig's cell still holding its
                // block answers the truth (the client's abort ghost reverts).
                player.link().blockResync(packetTarget, world.getBlock(packetTarget));
            }
        }
    }

    /** The abort walk itself (the reference's stopMiningBlock shape). */
    private void stopMiningWalk(PlayerSession player) {
        MiningSession session = miningSessions.get(player.uuid());
        if (session == null) {
            return;
        }
        if (session.target != null) {
            publishProgress(player, session.target, REMOVAL_STAGE);
            session.target = null;
        }
        // A pending wasMining accumulation keeps running (vanilla leaves it).
        if (session.prevTarget == null) {
            miningSessions.remove(player.uuid());
        }
    }

    /**
     * THE EXTINGUISH ARM (World.extinguishFire lines 1639-1647, 1:1): the
     * dig's own fire law — {@code pos.offset(face)}, a FIRE block there
     * dies: {@code doEvent(player, 1004, pos, 0)} (the fizz broadcast)
     * then {@code removeBlock(pos)}. A null base (the internal API's
     * face-less path — Slice N6's documented adaptation) or an invalid
     * face never arms; the off-world offset is guarded (the reference's
     * raw write at the out-of-band cell, an out-of-world read answering
     * air the client drops — the engine's bounds merely skip it). The
     * reference's {@code player} exclusion argument is null from the dig
     * start (the reference itself passes null), so the fizz reaches
     * everyone in earshot.
     */
    private boolean extinguishFireAt(EngineWorld world, BlockPosition base, int face) {
        if (base == null) {
            return false; // the face-less internal path: no arm
        }
        BlockPosition offset = InteractionRules.offsetByFace(base, face);
        if (offset == null || offset.y() < BlockPosition.MIN_Y || offset.y() > BlockPosition.MAX_Y) {
            return false; // the invalid face / the off-world neighbour
        }
        if (!world.getBlock(offset).identifier().value().equals("fire")) {
            return false; // not fire: nothing to extinguish
        }
        java.util.function.Consumer<BlockPosition> fx = fireExtinguishFx;
        if (fx != null) {
            fx.accept(offset); // the doEvent 1004: the fizz at the fire cell
        }
        commit(world, offset, world.airType());
        return true;
    }

    /**
     * The vanilla interaction-manager tick: the {@code wasMining} slot
     * self-completes at progress 1.0 (the early-finished dig breaks even if
     * the client never re-sends), the live slot only broadcasts stages. The
     * per-tick progress recomputes with the digger's live posture, so diving
     * or jumping while mining slows the dig exactly as it historically did.
     */
    public void tickMining() {
        for (MiningSession session : miningSessions.values().toArray(new MiningSession[0])) {
            // The digger's own world (Wave VT-002 — the reference's manager
            // holds the player's world; resolved per session so a digger's
            // dimension change re-routes its live dig's tick with it).
            EngineWorld world = worldFor.apply(session.digger.dimension());
            if (session.prevTarget != null) {
                BlockBehavior behavior = BlockBehaviorTable.of(
                        world.getBlock(session.prevTarget).identifier()).orElse(null);
                if (behavior == null || !behavior.diggable()
                        || world.getBlock(session.prevTarget).equals(world.airType())) {
                    session.prevTarget = null; // the target died some other way
                } else {
                    long elapsedTicks = world.totalTicks() - session.prevStartTick;
                    double progress = perTickProgress(world, session.digger, session.prevTarget, behavior)
                            * (elapsedTicks + 1);
                    int stage = (int) (progress * 10.0);
                    if (stage != session.prevLastStage) {
                        session.prevLastStage = stage;
                        publishProgress(session.digger, session.prevTarget, stage);
                    }
                    if (progress >= 1.0) {
                        BlockPosition completed = session.prevTarget;
                        session.prevTarget = null;
                        if (survivalBreakAllowed(world, session.digger, completed)) {
                            tryMineBlock(world, session.digger, completed, behavior);
                        }
                    }
                }
            }
            if (session.target != null) {
                BlockType current = world.getBlock(session.target);
                BlockBehavior behavior = BlockBehaviorTable.of(current.identifier()).orElse(null);
                if (behavior == null || !behavior.diggable() || current.equals(world.airType())) {
                    publishProgress(session.digger, session.target, REMOVAL_STAGE);
                    session.target = null;
                } else {
                    long elapsedTicks = world.totalTicks() - session.startTick;
                    double progress = perTickProgress(world, session.digger, session.target, behavior)
                            * (elapsedTicks + 1);
                    int stage = (int) (progress * 10.0);
                    if (stage != session.lastStage) {
                        session.lastStage = stage;
                        publishProgress(session.digger, session.target, stage);
                    }
                    // No self-completion in the live slot: the vanilla isMining
                    // branch only animates; the finish packet decides.
                }
            }
            if (session.target == null && session.prevTarget == null) {
                miningSessions.remove(session.digger.uuid());
            }
        }
    }

    /**
     * The engine's anti-cheat gate (kept from the NCP shapes: the rolling
     * 20-tick break budget and the same-tick different-target MultiBreak)
     * wrapping the vanilla tryMineBlock paths. The vanilla flow itself has no
     * budget — the engine's volumes run wider than one client's.
     */
    private boolean survivalBreakAllowed(EngineWorld world, PlayerSession player, BlockPosition target) {
        long nowNanos = System.nanoTime();
        boolean overBudget = !player.violations().recordBreak(nowNanos, BREAK_WINDOW_NANOS, SURVIVAL_BREAK_BUDGET);
        boolean multiBreak = player.violations().isMultiBreak(nowNanos, target);
        if (overBudget || multiBreak) {
            player.violations().addNukerViolation();
            resync(world, target);
            LOGGER.warning(() -> "Nuker violation for " + player.name() + " (vl "
                    + player.violations().nukerViolations() + ") overBudget=" + overBudget
                    + " multiBreak=" + multiBreak + " target=" + target);
            if (player.violations().nukerViolations() >= NUKER_KICK_VIOLATIONS) {
                player.link().kick("Nuker");
            }
            return false;
        }
        player.violations().noteBreakTarget(nowNanos, target);
        return true;
    }

    /**
     * The vanilla {@code tryMineBlock}: the break commits, the drops roll, the
     * tool wears, the XP awards and the block-broken hooks (container spill)
     * run — the same chain every accepted dig path takes.
     */
    private void tryMineBlock(EngineWorld world, PlayerSession player, BlockPosition target, BlockBehavior behavior) {
        BlockType current = world.getBlock(target);
        if (current.equals(world.airType())) {
            return; // gone between the decision and the mine: no-op
        }
        if (!fireBreak(world, player, target, current)) {
            resync(world, target); // the cancelled break: the digging client re-sees the block
                publishProgress(player, target, REMOVAL_STAGE);
            return;
        }
        net.zaminmc.torch.item.ItemStack heldStack = player.inventory().held();
        ItemType held = heldStack.isEmpty() ? null : heldStack.type();
        commit(world, target, world.airType());
        // The vanilla afterMinedByPlayer consult (ServerPlayerInteraction
        // Manager.tryMineBlock lines 211-225): the creative arm NEVER
        // reaches the walk (no stat, no fatigue, no drops), and the
        // survival arm's line-223 gate is flag1 && flag — the block gone
        // AND the held tool HARVESTS it (PlayerInventory.canMineBlock
        // lines 458-465: the material's isToolNotRequired or the tool's
        // own gate). A bare-handed stone break pays NEITHER the
        // mined-block row nor the 0.025 in the reference — the stats
        // family audit's B1 restoration (the harvest gate the pre-audit
        // site lacked).
        GameMode breakMode = gameMode.apply(player);
        boolean harvested = BlockBehaviorTable.canHarvest(behavior, held);
        // The banner family's own afterMinedByPlayer override (BannerBlock
        // lines 109-120) never calls super — no 0.025 fatigue, no
        // mined-block stat (the registration's own disableStats); the
        // survival drop rides the afterMinedByPlayer hook below instead
        // of the generic walk.
        boolean bannerBreak = net.zaminmc.torch.server.block.BannerBlocks.isBanner(
                current.identifier());
        if ((breakMode == GameMode.SURVIVAL || breakMode == GameMode.ADVENTURE)
                && harvested && !bannerBreak) {
            player.addExhaustion(0.025f);
            // The mined-block stat (afterMinedByPlayer line 774's own
            // incrementStat — creative never counts, the reference's
            // caller gate).
            player.stats().increment(
                    net.zaminmc.torch.server.stat.EngineStats.mineBlockOf(
                            current.identifier()), 1);
        }
        // The loot reads ride the held stack once (the silk-arm parity
        // audit: the XP gate consults the same flag the drop walk does).
        boolean silkTouch = !heldStack.isEmpty()
                && net.zaminmc.torch.server.enchantment.EnchantmentHelper
                        .hasSilkTouch(heldStack);
        int fortune = heldStack.isEmpty() ? 0
                : net.zaminmc.torch.server.enchantment.EnchantmentHelper
                        .fortuneLevel(heldStack);
        publishDrops(world, target, current, held, silkTouch, fortune);
        wearHeldTool(player, behavior, current);
        // The infested egg's burst (InfestedBlock.dropItems lines 79-88):
        // the survival break spawns the silverfish at the block's own
        // position — the doTileDrops gamerule gates the reference's own
        // arm (the engine's default-on stand-in).
        if (net.zaminmc.torch.server.block.InfestedBlocks.isInfested(current.identifier())) {
            InfestedBurstHook burst = infestedBurstHook;
            if (burst != null) {
                burst.onEggBroken(target);
            }
        }
        SurvivalXpHook xp = survivalXpListener;
        if (xp != null && !silkTouch) {
            // The silk-arm parity audit — Block.afterMinedByPlayer lines
            // 772-781: the silk arm REPLACES the drop walk wholesale
            // (dropItems — the OreBlock XP's own carrier — never runs),
            // so a silk harvest pays NO mining XP; the lit redstone
            // band rides the award table's own row.
            xp.onSurvivalBreak(player, current, target); // the award sees the broken block
        }
        BlockBrokenListener listener = blockBrokenListener;
        // The afterMinedByPlayer hook (the survival arm's own tail — the
        // reference's else-branch at tryMineBlock line 224; creative never
        // reaches it, exactly like the XP awards). It fires BEFORE the
        // block-broken listener: the tile-reading drops consult the tile
        // before the mode-agnostic cleanup arms remove it.
        SurvivalMinedHook mined = survivalMinedListener;
        if (mined != null) {
            mined.onSurvivalMined(player, current, target);
        }
        if (listener != null) {
            listener.onBlockBroken(world, target, current); // container spill runs after the block is gone
        }
        BreakReport report = breakReport;
        if (report != null) {
            report.onBreak(player, target, current, false);
        }
    }

    /**
     * The per-dig per-tick progress delta ({@code Block.getMiningSpeed}):
     * the tool tier, the environment divisors, the /30 or /100 harvest split.
     * The enchantment reads ride the reference walk — the held stack's
     * Efficiency level and the helmet's Aqua Affinity gate the water divisor.
     * The mining-speed parity audit added the status-effect multipliers
     * (PlayerEntity.getMiningSpeed lines 663-695): Haste and mining fatigue
     * read through the effect-amplifier hook the server wires to the potion
     * system, between the Efficiency add and the environment divisors exactly
     * as the reference orders them.
     */
    private double perTickProgress(EngineWorld world, PlayerSession player, BlockPosition target, BlockBehavior behavior) {
        net.zaminmc.torch.item.ItemStack heldStack = player.inventory().held();
        ItemType held = heldStack.isEmpty() ? null : heldStack.type();
        boolean canHarvest = BlockBehaviorTable.canHarvest(behavior, held);
        int efficiency = net.zaminmc.torch.server.enchantment.EnchantmentHelper
                .efficiencyLevel(heldStack);
        boolean aquaAffinity = net.zaminmc.torch.server.enchantment.EnchantmentHelper
                .highestLevel(net.zaminmc.torch.server.enchantment.Enchantments.AQUA_AFFINITY.id,
                        player.inventory().armorStacks()) > 0;
        int haste = effectAmplifier(player,
                net.zaminmc.torch.server.potion.StatusEffects.HASTE);
        int fatigue = effectAmplifier(player,
                net.zaminmc.torch.server.potion.StatusEffects.MINING_FATIGUE);
        return MiningRules.perTickProgress(behavior.hardness(),
                MiningRules.miningSpeed(BlockBehaviorTable.speedMultiplier(behavior,
                                world.getBlock(target), held),
                        efficiency, isSubmerged(world, player), aquaAffinity, player.onGround(),
                        haste, fatigue),
                canHarvest);
    }

    /** The status-effect read the dig consults ({@code hasStatusEffect} + amplifier). */
    private int effectAmplifier(PlayerSession player, int effectId) {
        java.util.function.BiFunction<PlayerSession, Integer, Integer> read = effectAmplifierRead;
        return read == null ? MiningRules.NO_EFFECT : read.apply(player, effectId);
    }

    /** The vanilla removal stage: the client clears the animation above stage 10. */
    public static final int REMOVAL_STAGE = 255;

    /**
     * The vanilla {@code isSubmergedIn(Material.WATER)} for the dig: the block
     * at the eye point (1.62 standing / 1.54 sneaking, the same eye constants
     * the reach check uses) is water.
     */
    private boolean isSubmerged(EngineWorld world, PlayerSession player) {
        Position eye = player.position();
        double eyeY = eye.y() + (player.sneaking()
                ? net.zaminmc.torch.server.player.AntiCheat.EYE_HEIGHT_SNEAKING
                : net.zaminmc.torch.server.player.AntiCheat.EYE_HEIGHT_STANDING);
        BlockPosition head = new BlockPosition((int) Math.floor(eye.x()),
                (int) Math.floor(eyeY), (int) Math.floor(eye.z()));
        return net.zaminmc.torch.server.block.FluidBlocks.isWater(world.getBlock(head).identifier());
    }

    /**
     * The dig-progress fan-out hook: invoked on the tick thread whenever a
     * session's animation stage moves (or the dig ends). Receives the digger
     * (excluded from the broadcast — the client predicts its own cracking),
     * the target and the stage 0-9, or {@link #REMOVAL_STAGE}.
     */
    public interface MiningProgressListener {
        void onMiningProgress(PlayerSession digger, BlockPosition target, int stage);
    }

    /** Registers the 0x28 fan-out hook. Tick-thread context. */
    public void setMiningProgressListener(MiningProgressListener listener) {
        this.miningProgressListener = listener;
    }

    private void publishProgress(PlayerSession digger, BlockPosition target, int stage) {
        MiningProgressListener listener = miningProgressListener;
        if (listener != null) {
            listener.onMiningProgress(digger, target, stage);
        }
    }

    /**
     * Historical durability wear — the tool-wear parity audit (the
     * reference's own {@code mineBlock} rows, read before the fix): a
     * successful dig of a block with hardness wears the SWORD two units
     * (SwordItem line 55: {@code takeDamageAndBreak(2)}) and the ToolItem
     * family ONE (ToolItem line 42, the hardness gate
     * {@code getMiningTime != 0.0} riding ahead); the SHEARS wear only on
     * the reference's own band — the web, the tallgrass pair, the vine,
     * the tripwire, the wool family and the leaves material (ShearsItem
     * lines 20-29) — with NO hardness gate (the reference's shears arm
     * wears the instant-break tallgrass and tripwire too; a shears dig
     * of stone never wears, the pre-fix walk paid one unit for EVERY
     * block). The HOE never wears (HoeItem extends Item — no mineBlock
     * arm). Creative players never wear tools; a tool at its limit
     * breaks and leaves the hand (the boundary itself rides the
     * inventory's own takeDamage walk).
     */
    private void wearHeldTool(PlayerSession player, BlockBehavior behavior, BlockType broken) {
        GameMode mode = gameMode.apply(player);
        if (mode == GameMode.CREATIVE) {
            return;
        }
        net.zaminmc.torch.item.ItemStack heldStack = player.inventory().held();
        boolean shears = !heldStack.isEmpty()
                && net.zaminmc.torch.server.item.Tools.specOf(heldStack.type())
                        .map(spec -> spec.toolClass() == net.zaminmc.torch.server.item.ToolClass.SHEARS)
                        .orElse(false);
        // The used-item row of the dig (the stats family audit —
        // ItemStack.mineBlock lines 252-257's flag, consulted inside
        // tryMineBlock's NON-creative arm at lines 216-221): the ToolItem
        // family and the sword pay for EVERY broken block — the flag sits
        // outside the hardness gate, so even an instant dig counts while
        // only the WEAR is hardness-gated — the shears pay their band
        // alone, and the hoe with the bare hand never pays.
        if (!heldStack.isEmpty()) {
            boolean usePays = shears
                    ? BlockBehaviorTable.inShearsWearBand(broken)
                    : net.zaminmc.torch.server.item.Tools.mineUsePaysOf(heldStack.type());
            if (usePays) {
                player.stats().increment(
                        net.zaminmc.torch.server.stat.EngineStats.useItemOf(
                                heldStack.type().identifier()), 1);
            }
        }
        int wear;
        if (shears) {
            // The shears' own arm (ShearsItem lines 20-29): the band alone
            // decides — no hardness gate (the instant-break band members
            // still wear one unit), everything else never wears.
            wear = BlockBehaviorTable.inShearsWearBand(broken) ? 1 : 0;
        } else {
            // The SwordItem/ToolItem gate (line 53 / line 41:
            // getMiningTime != 0.0 — the instant dig never wears).
            if (behavior.hardness() <= 0) {
                return;
            }
            wear = heldStack.isEmpty() ? 0
                    : net.zaminmc.torch.server.item.Tools.blockWearOf(heldStack.type());
        }
        if (wear <= 0) {
            return;
        }
        if (player.inventory().damageHeld(wear, gameplayRandom, player.stats())) {
            // Worn or broken: the client's held slot is now stale, re-sync it.
            inventorySync.accept(player);
        }
    }

    /**
     * Registers a post-break hook invoked on the tick thread after a break
     * commits (the world the break ran in, the broken block's position and
     * its pre-break type). Used by the engine to spill container contents
     * (furnace slots) into the world and to invalidate bed spawns.
     */
    public void setBlockBrokenListener(BlockBrokenListener listener) {
        this.blockBrokenListener = listener;
    }

    /**
     * The infested-egg burst (Slice 18c — InfestedBlock.dropItems lines
     * 79-88): a SURVIVAL break of an egg spawns the silverfish at the
     * block's own position (the creative break skips the reference's
     * afterMinedByPlayer arm entirely, so no burst). Tick-thread context.
     */
    public interface InfestedBurstHook {
        void onEggBroken(BlockPosition position);
    }

    /** The egg burst sink (the engine's mob-spawn wiring). */
    private volatile InfestedBurstHook infestedBurstHook;

    /** Registers the egg-burst hook (the engine's silverfish wiring). */
    public void setInfestedBurstHook(InfestedBurstHook hook) {
        this.infestedBurstHook = hook;
    }

    /**
     * The survival-break XP hook: invoked on the tick thread after a survival
     * mining finish commits, with the broken block's type and position.
     * Creative breaks never fire it (the historical no-XP rule).
     */
    public interface SurvivalXpHook {
        void onSurvivalBreak(PlayerSession player, BlockType broken, BlockPosition position);
    }

    /** The survival-break afterMinedByPlayer hook (the tile-reading drops). */
    public interface SurvivalMinedHook {
        void onSurvivalMined(PlayerSession player, BlockType broken, BlockPosition position);
    }

    /**
     * Registers the survival-break XP hook (the engine's mining award wiring).
     * Tick-thread context.
     */
    public void setSurvivalXpListener(SurvivalXpHook listener) {
        this.survivalXpListener = listener;
    }

    /**
     * Registers the survival-break afterMinedByPlayer hook (the tile-reading
     * drops — the banner's tagged item). Tick-thread context.
     */
    public void setSurvivalMinedListener(SurvivalMinedHook listener) {
        this.survivalMinedListener = listener;
    }

    /**
     * Wires the public event bus (§27.7). Registered before the first tick;
     * handlers fire on the tick thread inside the pre-commit gates.
     */
    public void setEventBus(EngineEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * The public break gate: fires {@link BlockBreakEvent} before the world
     * mutation and reports whether the engine may proceed. Runs on the tick
     * thread (the firing owner).
     */
    private boolean fireBreak(EngineWorld world, PlayerSession player, BlockPosition position, BlockType block) {
        EngineEventBus bus = eventBus;
        if (bus == null) {
            return true;
        }
        return !bus.fire(new BlockBreakEvent(player, world, position, block)).cancelled();
    }

    // ------------------------------------------------------------------
    // The adventure gates (Wave I — the reference's mayBuild consults)
    // ------------------------------------------------------------------

    /**
     * The derived mayBuild (the reference's PlayerAbilities.canModifyWorld —
     * false in adventure and spectator, exactly the way PlayerNbt's
     * abilities compound derives it). The gates below consult it before
     * the held stack's CanPlaceOn/CanDestroy lists.
     */
    private static boolean mayModifyWorld(PlayerSession player) {
        GameMode mode = player.gamemode();
        return mode != GameMode.ADVENTURE && mode != GameMode.SPECTATOR;
    }

    /**
     * The place gate's consult (PlayerEntity.canUseItemOn 1:1): the
     * CanPlaceOn list must name the CLICKED block — pos.offset(face's
     * opposite) in the reference is the block the face belongs to; the
     * engine's walk already holds it as {@code clicked}. The
     * canUseOnBlockInAdventureMode seam is constant-false across the
     * whole reference set — no item overrides it.
     */
    private static boolean canUseItemOn(PlayerSession player, ItemStack held, BlockType clicked) {
        if (mayModifyWorld(player)) {
            return true;
        }
        if (held == null || held.isEmpty()) {
            return false;
        }
        return held.canPlaceOn() != null
                && held.canPlaceOn().contains(clicked.identifier().toString());
    }

    /**
     * The dispatch-level placement consult (Wave I's audit repair): every
     * block-item placement arm in {@code EngineServer.useItemOnBlockOnTick}
     * rides this one gate — the held stack's CanPlaceOn list must name the
     * CLICKED block (the authoritative inventory's held slot stands in for
     * the creative claim, the engine's trust stance), exactly the reference's
     * {@code PlayerEntity.canUseItemOn} over {@code ItemBlock.useOn} line 39.
     * Safe from the tick thread only (reads the live inventory and world).
     */
    public boolean canUseItemOnForPlacement(PlayerSession player, BlockPosition clicked) {
        return canUseItemOn(player, heldOf(player), worldOf(player).getBlock(clicked));
    }

    /**
     * The mine gate's consult (ServerPlayerInteractionManager 112-133 and
     * 195-211): the CanDestroy list must name the mined block.
     */
    private static boolean canMineBlock(PlayerSession player, ItemStack held, BlockType mined) {
        if (mayModifyWorld(player)) {
            return true;
        }
        // The reference's own order (ServerPlayerInteractionManager lines
        // 118-121): the SPECTATOR refusal comes BEFORE the CanDestroy
        // consult — a spectator never mines, whatever the held stack's
        // list claims (Slice N6: the wire's dig case band is shared by
        // every mode, the walk's own arms refuse inside).
        if (player.gamemode() == GameMode.SPECTATOR) {
            return false;
        }
        if (held == null || held.isEmpty()) {
            return false;
        }
        return held.canDestroy() != null
                && held.canDestroy().contains(mined.identifier().toString());
    }

    /** The held stack (the authoritative inventory's own slot, any mode). */
    private static ItemStack heldOf(PlayerSession player) {
        ItemStack held = player.inventory().held();
        return held == null ? ItemStack.EMPTY : held;
    }


    /**
     * The public place gate: fires {@link BlockPlaceEvent} before the world
     * mutation and the survival consumption, and reports whether the engine
     * may proceed. Runs on the tick thread (the firing owner).
     */
    private boolean firePlace(EngineWorld world, PlayerSession player, BlockPosition position, BlockType block) {
        EngineEventBus bus = eventBus;
        if (bus == null) {
            return true;
        }
        return !bus.fire(new BlockPlaceEvent(player, world, position, block)).cancelled();
    }

    /**
     * The placement's occupied gate (World.canBlockBePlaced over
     * Block.canBeReplaced, Wave L6): the target cell accepts the placement
     * when it is air OR replaceable flora — the tall grass and the large
     * fern (the double-plant family's GRASS/FERN variants, both halves)
     * yield their place silently; the four double-plant FLOWERS refuse
     * like any normal block. A replaced lower's upper pops through the
     * update system's own support arm, and the replaced flora drops
     * nothing — exactly the reference's walk.
     */
    private boolean canOccupy(EngineWorld world, BlockPosition target) {
        BlockType existing = world.getBlock(target);
        return existing.equals(world.airType())
                || WorldSolidity.isReplaceable(existing.identifier());
    }

    private void placeOnTick(PlayerSession player, BlockPosition clicked, int face, BlockType held) {
        // The placer's own world (Wave VT-002 — the reference's use arm
        // receives {@code player.world}).
        EngineWorld world = worldOf(player);
        BlockPosition target = InteractionRules.offsetByFace(clicked, face);
        if (target == null) {
            return; // invalid face value
        }
        if (world.getBlock(clicked).equals(world.airType())) {
            return; // must click an existing block face (§215)
        }
        if (!canOccupy(world, target)) {
            return; // air or replaceable flora (Block.canBeReplaced, Wave L6)
        }
        // The vanilla body rule: a block WITH a collision box never places
        // inside the player; collision-less blocks (torches, flora, rails)
        // place freely inside any entity.
        if (WorldSolidity.isSolid(held)
                && InteractionRules.intersectsPlayer(player.position(), target)) {
            LOGGER.fine(() -> "Rejected placement (inside player) by " + player.name());
            return;
        }
        // The canBePlaced consult (Slice 44 — PlantBlock's own gate): the
        // flora's soil check refuses the placement before any attempt.
        PlacementSupportGate supportGate = placementSupportGate;
        if (supportGate != null && !supportGate.test(world, target, held)) {
            LOGGER.fine(() -> "Rejected placement (unsupported soil) by " + player.name());
            resync(world, target);
            return;
        }
        // The fastplace guard runs the creative path too (the same budget —
        // the creative 1.8 client cannot legitimately outpace it either).
        if (!player.violations().recordPlace(System.nanoTime(), PLACE_WINDOW_NANOS, PLACE_BUDGET)) {
            player.violations().addFastPlaceViolation();
            resync(world, target);
            LOGGER.warning(() -> "Fastplace violation (creative) for " + player.name()
                    + " (vl " + player.violations().fastPlaceViolations() + ")");
            if (player.violations().fastPlaceViolations() >= FASTPLACE_KICK_VIOLATIONS) {
                player.link().kick("Fastplace");
            }
            return;
        }
        if (!firePlace(world, player, target, held)) {
            resync(world, target); // drop the client's prediction: the place was cancelled
            return;
        }
        commit(world, target, held);
        // BlockItem.useOn lines 46-51: the placement's setTileNbt consult —
        // the claimed block's tile already exists (the TilePlacementListener
        // created the family's model during the commit), and the reference's
        // consult input is the HELD STACK: the authoritative inventory's own
        // held slot stands in for the creative client's claim (the engine's
        // claimed-block model is its own trust stance).
        PlacementTagConsult consult = placementTagConsult;
        if (consult != null) {
            net.zaminmc.torch.item.ItemStack heldStack = player.inventory().held();
            if (!heldStack.isEmpty()) {
                consult.apply(world, player.dimension(), target, heldStack, player.opLevel());
            }
        }
    }

    private void survivalPlaceOnTick(PlayerSession player, BlockPosition clicked, int face) {
        // The placer's own world (Wave VT-002 — the reference's use arm
        // receives {@code player.world}).
        EngineWorld world = worldOf(player);
        net.zaminmc.torch.item.ItemStack heldStack = player.inventory().held();
        if (heldStack.isEmpty()) {
            return; // nothing held: no-op
        }
        BlockType heldBlock = blockItemResolver.apply(heldStack.type(), heldStack.damage()).orElse(null);
        if (heldBlock == null) {
            LOGGER.fine(() -> "Rejected survival placement of non-block item by " + player.name());
            return;
        }
        BlockPosition target = InteractionRules.offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !canOccupy(world, target)
                || (WorldSolidity.isSolid(heldBlock)
                        && InteractionRules.intersectsPlayer(player.position(), target))) {
            if (target == null) {
                LOGGER.fine(() -> "Rejected survival placement (bad face) by " + player.name());
            } else if (world.getBlock(clicked).equals(world.airType())) {
                LOGGER.fine(() -> "Rejected survival placement (clicked air) by " + player.name());
            } else if (!canOccupy(world, target)) {
                LOGGER.fine(() -> "Rejected survival placement (target not placeable: "
                        + world.getBlock(target).identifier() + " at " + target + ") by " + player.name());
            } else {
                LOGGER.fine(() -> "Rejected survival placement (inside player) by " + player.name());
            }
            if (target != null) {
                // Real clients predict the placement locally; an unanswered
                // reject leaves the ghost block until the next chunk sync.
                // The authoritative cell re-sync drops the prediction.
                resync(world, target);
            }
            return;
        }
        // The canBePlaced consult (Slice 44 — PlantBlock's own gate): the
        // flora's soil check refuses the placement before any attempt.
        PlacementSupportGate supportGate = placementSupportGate;
        if (supportGate != null && !supportGate.test(world, target, heldBlock)) {
            LOGGER.fine(() -> "Rejected survival placement (unsupported soil) by " + player.name());
            resync(world, target);
            return;
        }
        // The adventure place gate (Wave I — BlockItem.useOn line 39 ->
        // PlayerEntity.canUseItemOn 1:1): a restricted placer's held stack
        // must carry the CLICKED block in its CanPlaceOn list; a refusal
        // drops the prediction (the reference returns false before
        // canPlace, nothing places, nothing consumes).
        if (!canUseItemOn(player, heldStack, world.getBlock(clicked))) {
            LOGGER.fine(() -> "Rejected placement (adventure CanPlaceOn) by " + player.name());
            resync(world, target);
            return;
        }
        // The fastplace guard (the NCP shape): the rolling 10-tick budget;
        // over budget the placement refuses and the cell re-syncs (the
        // client's prediction drops).
        if (!player.violations().recordPlace(System.nanoTime(), PLACE_WINDOW_NANOS, PLACE_BUDGET)) {
            player.violations().addFastPlaceViolation();
            resync(world, target);
            LOGGER.warning(() -> "Fastplace violation for " + player.name() + " (vl "
                    + player.violations().fastPlaceViolations() + ")");
            if (player.violations().fastPlaceViolations() >= FASTPLACE_KICK_VIOLATIONS) {
                player.link().kick("Fastplace");
            }
            return;
        }
        if (!firePlace(world, player, target, heldBlock)) {
            resync(world, target); // the cancelled place: the client's prediction drops
            return;
        }
        commit(world, target, heldBlock);
        // BlockItem.useOn lines 46-51: the placement's setTileNbt consult —
        // the freshly-placed tile's rows merged with the held stack's
        // BlockEntityTag, BEFORE the consume (the reference's own order:
        // setBlockState, then setTileNbt, then the stack's size--).
        PlacementTagConsult consult = placementTagConsult;
        if (consult != null) {
            consult.apply(world, player.dimension(), target, heldStack, player.opLevel());
        }
        player.inventory().consumeHeld(1); // atomic with the commit above (§431)
        // The used-item stat (ItemStack.useOn's own row over the placed
        // block item — the reference counts the use on the placement).
        player.stats().increment(
                net.zaminmc.torch.server.stat.EngineStats.useItemOf(
                        heldStack.type().identifier()), 1);
        inventorySync.accept(player);
    }

    /**
     * The placement path of the engine's use-on-block dispatch, run on the
     * caller's (simulation) context: creative places the client-claimed block,
     * survival consumes from the authoritative inventory. The engine calls this
     * from its own tick task after the container check (crafting table) has
     * ruled out a block GUI. Creative uses with no mappable held block are a
     * no-op, matching the direct creative path.
     */
    public void placeFromUseOnTick(PlayerSession player, BlockPosition clicked, int face,
                                   java.util.Optional<BlockType> creativeHeld) {
        if (player.gamemode() == GameMode.SPECTATOR) {
            return; // spectators never use items (the engine's own guard)
        }
        if (creativeHeld.isPresent()) {
            placeOnTick(player, clicked, face, creativeHeld.get());
        } else if (gameMode.apply(player) == GameMode.SURVIVAL
                || gameMode.apply(player) == GameMode.ADVENTURE) {
            // Adventure rides the survival path (Wave I — the reference's
            // adventure players place through the same useOn walk, the
            // canUseItemOn gate inside carrying the CanPlaceOn consult;
            // a passing placement consumes the stack like any other).
            survivalPlaceOnTick(player, clicked, face);
        }
        // creative with an unmapped/empty held item: a no-op use
    }

    /**
     * The single semantic commit: the world's setBlock dispatches the change
     * event itself (§208 — the world is the source of truth; player- and
     * engine-driven changes reach neighbor updates and client syncs through
     * the same listener path). The feedback hook (break/place sounds and
     * particles) fires after the mutation, before the caller continues.
     */
    private void commit(EngineWorld world, BlockPosition position, BlockType type) {
        BlockType previous = world.getBlock(position);
        world.setBlock(position, type);
        java.util.function.Consumer<Commit> feedback = commitFeedbackListener;
        if (feedback != null && !previous.equals(type)) {
            feedback.accept(new Commit(position, previous, type));
        }
    }

    /** One world mutation with its before/after block states. */
    public record Commit(BlockPosition position, BlockType previous, BlockType now) {
    }

    /**
     * Registers the commit feedback hook (the engine's FX bus wiring). The
     * consumer runs on the tick thread, directly after the world mutation.
     */
    public void setCommitFeedbackListener(java.util.function.Consumer<Commit> listener) {
        this.commitFeedbackListener = listener;
    }

    /**
     * Drop calculation (§434): committed break -&gt; drops -&gt; item entities.
     * Wave VT-002/TF-9: the drops stamp the break's own dimension (the
     * four-arg form — the reference's spawnDrops runs in the broken
     * block's world).
     */
    private void publishDrops(EngineWorld world, BlockPosition broken, BlockType brokenType, ItemType heldTool,
                              boolean silkTouch, int fortune) {
        // doTileDrops (Slice 24a — Block.java: the world's dropBlock runs
        // only under the rule; the break itself already committed).
        java.util.function.BooleanSupplier dropsRead = tileDropsRead;
        if (dropsRead != null && !dropsRead.getAsBoolean()) {
            return;
        }
        List<ItemStack> drops = dropService.dropsFor(brokenType, heldTool, silkTouch, fortune);
        if (drops.isEmpty()) {
            return;
        }
        // The manager positions drops around the block's center from its min corner.
        var blockOrigin = new net.zaminmc.torch.util.Position(broken.x(), broken.y(), broken.z());
        for (ItemStack drop : drops) {
            itemEntities.spawnDropAtBlock(blockOrigin, drop, ItemEntity.PICKUP_DELAY_DROP_TICKS,
                    world.dimension());
        }
    }

    /**
     * Re-publishes the authoritative current state so clients drop
     * predictions — in the world the walk ran in (Wave VT-002: the
     * reference's BlockUpdate consults the player's own world).
     */
    private void resync(EngineWorld world, BlockPosition position) {
        publisher.accept(world, new BlockChange(position, world.getBlock(position)));
    }
}
