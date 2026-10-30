package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.WorldChangeListener;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * The fluid simulation, 1:1 from the reference's liquid family
 * (reference/1.8.8 net/minecraft/block/FlowingLiquidBlock.java +
 * LiquidSourceBlock.java + LiquidBlock.java — the Wave VI fluids
 * migration, retiring the TogAr2/MinestomFluids-adapted model whole).
 *
 * <p><b>The reference's two-form model</b>: the SOURCE block
 * ({@code LiquidSourceBlock} — the settled still form, inert until a
 * neighbor changes) and the FLOWING block ({@code FlowingLiquidBlock}
 * — the animated form whose tick carries the whole spread). The LEVEL
 * nibble rides both: 0 the true source, 1-7 the shallow flow, 8-15 the
 * falling column with the above-depth in the low three bits. The
 * engine's flattened state band ({@link FluidBlocks}) maps every member
 * back onto the wire's two-form pairs.</p>
 *
 * <p><b>The tick</b> (FlowingLiquidBlock.tick lines 27-115, 1:1): the
 * horizontal depth walk ({@code getLowestDepth} with the adjacent-source
 * count), the computed level {@code l + j} with the death at 8 or a dry
 * horizon, the falling inheritance from above (the plain pass-down and
 * the {@code + 8} conversion), the infinite-water source birth (two
 * adjacent sources over solid ground or a same-liquid source), the
 * lava's slow surface creep ({@code k *= 4} on the 3-of-4 decline), the
 * equilibrium's {@code convertToSource} (the settled still form), the
 * down-spread with the lava-into-water STONE arm, and the sideways
 * spread through {@code getSpread}'s direction preference — the
 * recursive {@code getDistanceToGap} walk (four blocks out) that pours
 * the stream toward the nearest cliff instead of a uniform ring. The
 * landing column spreads fresh ({@code k1 = 1}); a shallow cell decays
 * by {@code j} (water 1; lava 2 in a dimension that keeps water, 1 in
 * the nether — the demand-named {@code j} consult).</p>
 *
 * <p><b>The wake path</b> rides the world change listener (the engine's
 * neighbor bus): a committed change runs the cell's own onAdded arm
 * (a flowing form schedules its first tick — the bucket pour's start)
 * and the six neighbors' neighborChanged arms (the lava's
 * {@code checkSpreadCollisions} contact — the source's obsidian, the
 * shallow stream's cobblestone; the still form's
 * {@code convertToFlowing} — the settled pool wakes and re-ticks). A
 * flowing cell does NOT re-tick on neighbor changes — the reference's
 * own shape (FlowingLiquidBlock inherits LiquidBlock's
 * neighborChanged, the check alone; the flowing cell's re-arm is its
 * own tick's schedule).</p>
 *
 * <p><b>The flags-2 guard</b>: the reference's level-change and
 * conversion writes carry flag 2 (send to clients, NO neighbor
 * updates) while the spread and contact writes carry flag 3 (both).
 * The engine's bus fires every listener on every write, so the
 * no-update writes run under {@link #inNoWakeWrite} — the listener
 * arm stands down for its own quiet writes, exactly the reference's
 * flag semantics (the explicit {@code world.updateNeighbors} after a
 * level change rides {@link #wakeNeighbors}).</p>
 *
 * <p><b>The seam</b>: the fluid schedules ride the
 * {@link ScheduledTickSystem} when wired (the engine's boot always
 * wires it — the reference's own {@code world.scheduleTick} surface,
 * with the persistence and the anvil TileTicks import landing on the
 * same queue); a standalone-constructed system keeps its own queue
 * with the same delays and the same dispatch gate — the engine's own
 * unit-test harness rides that window (named, not a second
 * behavior).</p>
 */
public final class FluidSystem implements WorldChangeListener {

    /** The world a fluid update reads and commits through (tick thread). */
    public interface World {
        BlockType getBlock(BlockPosition position);

        void setBlock(BlockPosition position, BlockType type);

        BlockType airType();

        long totalTicks();

        /** The world's own name (the public event's world row). */
        String name();

        /**
         * The dimension's water-vaporizing consult (the reference's
         * {@code world.provider.doesWaterVaporize()} — true in the
         * nether): lava spreads at full reach (j = 1) where water
         * cannot exist, and a water bucket's pour fizzles away.
         */
        default boolean yeetsWater() {
            return false;
        }

        /**
         * The dimension's no-sky consult (the reference's
         * {@code world.provider.getHasNoSky()} — true in the nether):
         * lava's cadence drops to 10 ticks (the nether's own faster
         * lava sea).
         */
        default boolean hasNoSky() {
            return false;
        }
    }

    /** Torch wash-out and fizz feedback. */
    public interface Sink {
        /**
         * The wash-out drops (spreadTo's {@code dropItems} arm — the
         * reference's lines 119-125: a fluid spreading into a non-air
         * cell drops the displaced block's own items; lava fizzles it
         * away instead).
         */
        void wash(Position at, BlockType washed);

        void sound(Position at, String name, float volume, float pitch);
    }

    /** The unwired window's one pending tick (the seam's own shape). */
    private record Pending(BlockPosition position, Identifier family) {
    }

    private static final int[][] HORIZONTALS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final World world;
    private final Sink sink;
    private final Random random;

    /** The public §27.7 flow gate (the observe/cancel hook; null = unwired). */
    private volatile net.zaminmc.torch.server.event.EngineEventBus eventBus;

    /** The scheduled-tick seam (the reference's scheduleTick surface; null = the unwired window). */
    private volatile ScheduledTickSystem scheduledTicks;

    /** The flags-2 guard: the system's own quiet writes do not wake the bus. */
    private boolean inNoWakeWrite;

    /** The unwired window's queue (the same no-merge-update rule as the seam). */
    private final Map<Pending, Long> pending = new HashMap<>();
    private final ArrayDeque<Pending> order = new ArrayDeque<>();

    /** The adjacent-source count of the depth walk in flight (the reference's own field). */
    private int adjacentSources;

    public FluidSystem(World world, Sink sink) {
        this(world, sink, new Random());
    }

    public FluidSystem(World world, Sink sink, Random random) {
        this.world = Objects.requireNonNull(world, "world");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Wires the public §27.7 flow gate (the observe/cancel hook). */
    public void setEventBus(net.zaminmc.torch.server.event.EngineEventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    /** Wires the scheduled-tick seam (the reference's scheduleTick surface). */
    public void setScheduledTicks(ScheduledTickSystem scheduledTicks) {
        this.scheduledTicks = Objects.requireNonNull(scheduledTicks, "scheduledTicks");
    }

    // ------------------------------------------------------------------
    // The wake path (the neighbor bus — onAdded + neighborChanged, 1:1)
    // ------------------------------------------------------------------

    /**
     * A block was committed: the cell's own onAdded arm and the six
     * neighbors' neighborChanged arms, the reference's flag-3 fan. Runs
     * on the tick thread through the world-listener path.
     */
    @Override
    public void onBlockChanged(EngineWorld changedWorld, BlockPosition position, BlockType newType) {
        if (inNoWakeWrite) {
            return; // the flags-2 quiet write: no onAdded, no neighborChanged
        }
        onAdded(position, newType);
        wakeNeighbors(position);
    }

    /**
     * The cell's own onAdded arm (FlowingLiquidBlock lines 217-222 +
     * LiquidBlock lines 256-259): the contact check first — the lava's
     * {@code checkSpreadCollisions}; a flowing form then schedules its
     * first tick (the spread's start — the bucket pour's own shape); the
     * source form schedules nothing (the settled pool is inert until a
     * neighbor changes).
     */
    private void onAdded(BlockPosition position, BlockType placed) {
        Identifier id = placed.identifier();
        FluidBlocks.Kind kind = FluidBlocks.kindOf(id);
        if (kind == null) {
            return;
        }
        if (checkSpreadCollisions(position, kind, FluidBlocks.levelOf(id))) {
            return; // the placement itself converted (the water-touching lava pour)
        }
        if (FluidBlocks.formOf(id) == FluidBlocks.Form.FLOWING) {
            schedule(position, id, cadenceOf(kind));
        }
    }

    /**
     * The neighborChanged arms over the six neighbors (LiquidBlock lines
     * 261-264 + LiquidSourceBlock lines 19-24): the contact check first,
     * then the still form's {@code convertToFlowing} — the settled pool
     * wakes into the flowing form at its own level and schedules its
     * tick. A flowing cell does NOT re-tick here (the reference's own
     * inheritance shape — the flowing cell's re-arm is its own tick).
     */
    private void wakeNeighbors(BlockPosition position) {
        for (int[] offset : NEIGHBORS) {
            int x = position.x() + offset[0];
            int y = position.y() + offset[1];
            int z = position.z() + offset[2];
            if (y < BlockPosition.MIN_Y || y > BlockPosition.MAX_Y) {
                continue;
            }
            neighborChanged(new BlockPosition(x, y, z));
        }
    }

    /** One neighbor's arm. Tick-thread context. */
    private void neighborChanged(BlockPosition position) {
        BlockType type = world.getBlock(position);
        Identifier id = type.identifier();
        FluidBlocks.Kind kind = FluidBlocks.kindOf(id);
        if (kind == null) {
            return;
        }
        int level = FluidBlocks.levelOf(id);
        if (checkSpreadCollisions(position, kind, level)) {
            return; // the contact converted the cell (obsidian/cobblestone)
        }
        if (FluidBlocks.formOf(id) == FluidBlocks.Form.SOURCE) {
            // LiquidSourceBlock.neighborChanged → convertToFlowing: the
            // still form becomes the flowing form at its own level (a true
            // source carries 0 — its tick re-settles it to the source and
            // spreads) and schedules.
            Identifier flowingId = FluidBlocks.levelId(kind, FluidBlocks.Form.FLOWING, level);
            quietSet(position, FluidBlocks.typeOf(kind, FluidBlocks.Form.FLOWING, level));
            schedule(position, flowingId, cadenceOf(kind));
        }
    }

    // ------------------------------------------------------------------
    // The contact reactions (LiquidBlock.checkSpreadCollisions lines
    // 266-294, 1:1) — the LAVA cell converts when water touches it
    // ------------------------------------------------------------------

    /**
     * The lava's contact check: any non-down neighbor of water turns the
     * lava cell — a true source (LEVEL 0) to OBSIDIAN, a shallow stream
     * (LEVEL 1-4) to COBBLESTONE, both with the fizz; a deep stream or a
     * falling column stays (the reference's own bands). Water cells never
     * convert here — only the lava side reacts.
     *
     * @return whether the cell converted
     */
    private boolean checkSpreadCollisions(BlockPosition position, FluidBlocks.Kind kind, int level) {
        if (kind != FluidBlocks.Kind.LAVA) {
            return false;
        }
        for (int[] offset : NEIGHBORS) {
            if (offset[1] == -1) {
                continue; // the down neighbor never converts (the tick's own stone arm)
            }
            BlockType side = at(position, offset);
            if (FluidBlocks.kindOf(side.identifier()) == FluidBlocks.Kind.WATER) {
                BlockType converted = level == 0
                        ? BuiltinBlocks.OBSIDIAN
                        : (level <= 4 ? BuiltinBlocks.COBBLESTONE : null);
                if (converted != null) {
                    world.setBlock(position, converted); // flags 3 — the wake rides the bus
                    fizz(position);
                    return true;
                }
                return false; // the deep stream: the reference's own refusal
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // The tick dispatch (the seam's registered arm + the unwired window)
    // ------------------------------------------------------------------

    /**
     * The dispatch arm registered for every fluid family id on the
     * scheduled-tick surface: the source-form families carry the lava
     * fire-start (LiquidSourceBlock.tick — the reference's random-tick
     * body, reachable through a scheduled row the same way the
     * reference's {@code randomTick} default delegates to {@code tick});
     * the flowing-form families carry the whole flow tick
     * (FlowingLiquidBlock.tick).
     */
    public void dispatchTick(EngineWorld tickWorld, BlockPosition pos, BlockType state, Random rolls) {
        Identifier id = state.identifier();
        FluidBlocks.Kind kind = FluidBlocks.kindOf(id);
        if (kind == null) {
            return; // the cell changed families between schedule and fire
        }
        if (FluidBlocks.formOf(id) == FluidBlocks.Form.SOURCE) {
            sourceFormTick(pos, kind, rolls);
        } else {
            flowTick(pos, kind, FluidBlocks.levelOf(id), rolls);
        }
    }

    /**
     * LiquidSourceBlock.tick lines 33-62 (the lava source's fire-start —
     * the random-tick body over the doFireTick gate): the 0-2 upward walk
     * lighting an air cell above a flammable neighbor, the else arm's
     * three ground tries. Water sources carry no body (the no-op).
     */
    private void sourceFormTick(BlockPosition pos, FluidBlocks.Kind kind, Random rolls) {
        if (kind != FluidBlocks.Kind.LAVA) {
            return; // a water source's tick: the reference's empty body
        }
        lavaFireStart(pos, rolls);
    }

    /** The lava fire-start walk (shared with the random-tick arm). */
    private void lavaFireStart(BlockPosition pos, Random rolls) {
        if (!FireBlocks.fireTickEnabled()) {
            return; // the doFireTick gate wraps the whole body
        }
        int i = rolls.nextInt(3);
        if (i > 0) {
            BlockPosition cursor = pos;
            for (int j = 0; j < i; j++) {
                cursor = new BlockPosition(
                        cursor.x() + rolls.nextInt(3) - 1,
                        Math.min(BlockPosition.MAX_Y, cursor.y() + 1),
                        cursor.z() + rolls.nextInt(3) - 1);
                BlockType at = world.getBlock(cursor);
                if (at.identifier().equals(world.airType().identifier())) {
                    if (hasFlammableNeighbor(cursor)) {
                        world.setBlock(cursor, FireBlocks.stateOf(0));
                        return;
                    }
                } else if (WorldSolidity.isSolid(at)) {
                    return; // the walk's own stop
                }
            }
        } else {
            for (int k = 0; k < 3; k++) {
                BlockPosition ground = new BlockPosition(
                        pos.x() + rolls.nextInt(3) - 1, pos.y(), pos.z() + rolls.nextInt(3) - 1);
                BlockType at = world.getBlock(ground);
                if (FireBlocks.isFlammable(at.identifier())) {
                    BlockPosition above = new BlockPosition(ground.x(),
                            Math.min(BlockPosition.MAX_Y, ground.y() + 1), ground.z());
                    if (world.getBlock(above).identifier().equals(world.airType().identifier())) {
                        world.setBlock(above, FireBlocks.stateOf(0));
                    }
                }
            }
        }
    }

    /** The fire consult's own walk (LiquidSourceBlock.hasFlammableNeighbor 1:1, over the fluid adapter). */
    private boolean hasFlammableNeighbor(BlockPosition position) {
        for (int[] offset : NEIGHBORS) {
            if (FireBlocks.isFlammable(at(position, offset).identifier())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // The flow tick (FlowingLiquidBlock.tick lines 27-115, 1:1)
    // ------------------------------------------------------------------

    /** The whole flow tick. Tick-thread context. */
    private void flowTick(BlockPosition position, FluidBlocks.Kind kind, int level, Random rolls) {
        int i = level;
        int j = 1;
        if (kind == FluidBlocks.Kind.LAVA && !world.yeetsWater()) {
            j = 2; // the overworld's lava decay; the nether's own full reach
        }
        int k = cadenceOf(kind);

        if (i > 0) {
            // Lines 35-41: the horizontal depth walk.
            int l = -100;
            this.adjacentSources = 0;
            for (int[] offset : HORIZONTALS) {
                l = getLowestDepth(new BlockPosition(
                        position.x() + offset[0], position.y(), position.z() + offset[2]), l, kind);
            }

            // Lines 43-46: the computed level — the decay walk, the death at 8.
            int i1 = l + j;
            if (i1 >= 8 || l < 0) {
                i1 = -1;
            }

            // Lines 48-55: the falling inheritance from above (the sky reads
            // dry — the reference's own out-of-world air).
            int above = position.y() < BlockPosition.MAX_Y
                    ? liquidStateAt(position.offset(0, 1, 0), kind) : -1;
            if (above >= 0) {
                if (above >= 8) {
                    i1 = above; // above is falling — the column passes its level down
                } else {
                    i1 = above + 8; // above is flowing — this cell falls with its depth
                }
            }

            // Lines 57-64: the infinite-water birth — two adjacent sources
            // over solid ground (or a same-liquid source) make a source.
            if (this.adjacentSources >= 2 && kind == FluidBlocks.Kind.WATER) {
                BlockType below = world.getBlock(position.offset(0, -1, 0));
                if (WorldSolidity.isSolid(below)
                        || (FluidBlocks.kindOf(below.identifier()) == kind
                        && FluidBlocks.levelOf(below.identifier()) == 0)) {
                    i1 = 0;
                }
            }

            // Lines 66-68: the lava's slow surface creep — a declining level
            // advances only one tick in four.
            if (kind == FluidBlocks.Kind.LAVA && i < 8 && i1 < 8 && i1 > i
                    && rolls.nextInt(4) != 0) {
                k *= 4;
            }

            if (i1 == i) {
                // Line 70-71: the equilibrium — the settled still form (a
                // true source at level 0 through the next tick's own arm).
                convertToSource(position, kind, i);
            } else {
                i = i1;
                if (i < 0) {
                    world.setBlock(position, world.airType()); // removeBlock, flags 3
                } else {
                    // Lines 76-80: the level write (flags 2), the re-arm on
                    // the NEW family, the explicit neighbor wake.
                    Identifier newId = FluidBlocks.levelId(kind, FluidBlocks.Form.FLOWING, i);
                    quietSet(position, FluidBlocks.typeOf(kind, FluidBlocks.Form.FLOWING, i));
                    schedule(position, newId, k);
                    wakeNeighbors(position);
                }
            }
        } else {
            // Lines 83-85: the flowing form at level 0 — the bucket pour's
            // own cell — settles into the true source (the spread arms
            // below still run: the source's one spread pass).
            convertToSource(position, kind, 0);
        }

        // Lines 87-99: the down spread (the bedrock floor reads as the
        // reference's own out-of-world air — the void below y=0).
        BlockPosition down = position.y() > BlockPosition.MIN_Y
                ? position.offset(0, -1, 0) : null;
        BlockType belowState = down != null ? world.getBlock(down) : world.airType();
        if (canSpreadTo(down, belowState, kind)) {
            if (kind == FluidBlocks.Kind.LAVA
                    && FluidBlocks.kindOf(belowState.identifier()) == FluidBlocks.Kind.WATER) {
                // The lava pouring into water makes stone (lines 89-93).
                world.setBlock(down, BuiltinBlocks.STONE);
                fizz(down);
                return;
            }
            boolean flowed = i >= 8
                    ? spreadTo(position, down, belowState, i, kind)
                    : spreadTo(position, down, belowState, i + 8, kind);
            if (flowed) {
                return; // the whole flow went down
            }
            // The §27.7 gate cancelled the down-pour — the engine's own
            // blocked-ground law: the stream falls back to its sideways
            // spread (the reference's canSpreadTo-false path).
        }

        // Lines 100-113: the sideways spread — the direction preference
        // toward the nearest gap, the landing column's fresh level.
        if (i >= 0 && (i == 0 || isLiquidBlocking(down))) {
            Set<Offset> spread = getSpread(position, kind);
            int k1 = i + j;
            if (i >= 8) {
                k1 = 1; // the landing column spreads fresh
            }
            if (k1 >= 8) {
                return; // the reach ends
            }
            for (Offset offset : spread) {
                BlockPosition target = new BlockPosition(
                        position.x() + offset.x, position.y(), position.z() + offset.z);
                spreadTo(position, target, world.getBlock(target), k1, kind);
            }
        }
    }

    /**
     * The reference's spreadTo (lines 117-129): the canSpreadTo gate, the
     * displaced block's own drops (water washes them out; lava fizzles
     * them away), and the flowing write at the spread level (flags 3 —
     * the new cell's onAdded schedules its own tick through the bus).
     *
     * @return whether the flow committed (false = the §27.7 gate cancelled)
     */
    private boolean spreadTo(BlockPosition from, BlockPosition target, BlockType state,
                             int depth, FluidBlocks.Kind kind) {
        if (!canSpreadTo(target, state, kind)) {
            return false;
        }
        if (!fireFlowGate(from, target, kind, depth)) {
            return false; // the §27.7 gate cancelled this flow step — blocked ground
        }
        if (target == null) {
            return true; // the void: the reference's setBlockState no-op below y=0
        }
        if (!state.identifier().equals(world.airType().identifier())) {
            if (kind == FluidBlocks.Kind.LAVA) {
                fizz(target); // the lava burns the displaced block away
            } else {
                sink.wash(new Position(target.x(), target.y(), target.z()), state);
            }
        }
        world.setBlock(target, FluidBlocks.typeOf(kind, FluidBlocks.Form.FLOWING, depth));
        return true;
    }

    /**
     * The reference's getLowestDepth (lines 195-210): the neighbor's own
     * liquid state, the source count, the falling read as depth 0, and
     * the min walk.
     */
    private int getLowestDepth(BlockPosition position, int depth, FluidBlocks.Kind kind) {
        int i = liquidStateAt(position, kind);
        if (i < 0) {
            return depth;
        }
        if (i == 0) {
            this.adjacentSources++;
        }
        if (i >= 8) {
            i = 0;
        }
        return depth >= 0 && i >= depth ? depth : i;
    }

    /**
     * The reference's getLiquidState (LiquidBlock lines 64-66): the
     * same-material level at the position, or -1.
     */
    private int liquidStateAt(BlockPosition position, FluidBlocks.Kind kind) {
        BlockType type = world.getBlock(position);
        return FluidBlocks.kindOf(type.identifier()) == kind
                ? FluidBlocks.levelOf(type.identifier()) : -1;
    }

    /**
     * The reference's canSpreadTo (lines 212-215): never the same
     * material, never the other liquid (WATER may not enter LAVA; LAVA
     * may enter water — its own fizz arm), never a liquid-blocking cell.
     * A null position is the void below the world (the reference's own
     * out-of-world air read — spreadable, the write itself a no-op).
     */
    private boolean canSpreadTo(BlockPosition position, BlockType state, FluidBlocks.Kind kind) {
        FluidBlocks.Kind material = FluidBlocks.kindOf(state.identifier());
        if (material == kind) {
            return false;
        }
        if (material == FluidBlocks.Kind.LAVA) {
            return false; // the Material.LAVA refusal both kinds share
        }
        return !isLiquidBlocking(position);
    }

    /**
     * The reference's isLiquidBlocking (lines 185-193): the door family,
     * the standing sign, the ladder, the reeds, the portal material, and
     * every movement-blocking MATERIAL — {@code material.blocksMovement()},
     * the material model, NOT the collision model (a body walks through an
     * open door or a banner; the liquid still flows around it — the sign
     * family's twelfth-pass consult {@link MaterialSolidity#blocksMovement}
     * carries the reference's own law, the engine's one true material
     * read; the pre-fix arm read {@code WorldSolidity.isSolid}, the
     * entity-collision model, and washed out every open wood door, wall
     * sign, banner, open gate and pressure plate the reference protects).
     */
    private boolean isLiquidBlocking(BlockPosition position) {
        if (position == null) {
            return false; // the void below the world — the reference's air read
        }
        BlockType type = world.getBlock(position);
        Identifier id = type.identifier();
        return isDoor(id) || isStandingSign(id) || WorldSolidity.isLadder(id)
                || WorldSolidity.isSugarCane(id)
                || WorldSolidity.isNetherPortal(id) || WorldSolidity.isEndPortal(id)
                || MaterialSolidity.blocksMovement(id);
    }

    /**
     * The door family (the reference's {@code DoorBlock instanceof} — the
     * state never matters, the BLOCK does): all seven of the engine's
     * {@link WoodDoorFamily} families (the six woods + the iron pair),
     * every lower, upper, open and powered state, through the family's
     * own consult. The stale {@code wooden_door*} names matched no
     * registered id — only the iron prefix ever covered a real door, and
     * every OPEN wood state washed away.
     */
    private static boolean isDoor(Identifier id) {
        return WoodDoorFamily.isDoor(id);
    }

    /** The standing sign alone (the reference's STANDING_SIGN row — never the wall form). */
    private static boolean isStandingSign(Identifier id) {
        return id.namespace().equals("minecraft")
                && (id.value().equals("sign") || id.value().startsWith("sign_"));
    }

    /**
     * The reference's getSpread (lines 156-183): the four horizontal
     * directions scored by their distance to a gap (the recursive
     * four-deep walk below), the minimum alone carrying the flow.
     */
    private Set<Offset> getSpread(BlockPosition position, FluidBlocks.Kind kind) {
        int best = 1000;
        Set<Offset> spread = EnumSet.noneOf(Offset.class);
        for (Offset offset : Offset.ALL) {
            BlockPosition neighbor = new BlockPosition(
                    position.x() + offset.x, position.y(), position.z() + offset.z);
            BlockType state = world.getBlock(neighbor);
            if (!isLiquidBlocking(neighbor)
                    && (FluidBlocks.kindOf(state.identifier()) != kind
                    || FluidBlocks.levelOf(state.identifier()) > 0)) {
                int score;
                BlockPosition neighborDown = neighbor.y() > BlockPosition.MIN_Y
                        ? neighbor.offset(0, -1, 0) : null;
                if (isLiquidBlocking(neighborDown)) {
                    score = getDistanceToGap(neighbor, 1, offset.opposite(), kind);
                } else {
                    score = 0; // the neighbor's own cell drops — a gap right here
                }
                if (score < best) {
                    spread.clear();
                }
                if (score <= best) {
                    spread.add(offset);
                    best = score;
                }
            }
        }
        return spread;
    }

    /**
     * The reference's getDistanceToGap (lines 131-154): the recursive
     * walk (four blocks out) scoring how far the stream must run before
     * it can fall — the gap-seeking preference's own core. The QUIRK is
     * the reference's own: the passed state belongs to the HORIZONTAL
     * neighbor while the blocking consult reads the DOWN cell (line 139's
     * own argument pair — ported exactly).
     */
    private int getDistanceToGap(BlockPosition position, int distance, Offset fromDir,
                                 FluidBlocks.Kind kind) {
        int best = 1000;
        for (Offset offset : Offset.ALL) {
            if (offset == fromDir) {
                continue; // never walk back the way we came
            }
            BlockPosition neighbor = new BlockPosition(
                    position.x() + offset.x, position.y(), position.z() + offset.z);
            BlockType state = world.getBlock(neighbor);
            if (!isLiquidBlocking(neighbor)
                    && (FluidBlocks.kindOf(state.identifier()) != kind
                    || FluidBlocks.levelOf(state.identifier()) > 0)) {
                BlockPosition neighborDown = neighbor.y() > BlockPosition.MIN_Y
                        ? neighbor.offset(0, -1, 0) : null;
                if (!isLiquidBlocking(neighborDown)) {
                    return distance; // the gap — the stream can fall from here
                }
                if (distance < 4) {
                    int deeper = getDistanceToGap(neighbor, distance + 1, offset.opposite(), kind);
                    if (deeper < best) {
                        best = deeper;
                    }
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // The writes (the reference's flag pairs)
    // ------------------------------------------------------------------

    /**
     * The flags-2 quiet write: the level change and the conversions send
     * to clients but fire no neighbor updates (the guard stands the
     * listener arm down for the system's own write).
     */
    private void quietSet(BlockPosition position, BlockType type) {
        inNoWakeWrite = true;
        try {
            world.setBlock(position, type);
        } finally {
            inNoWakeWrite = false;
        }
    }

    /** The reference's convertToSource (lines 22-24): the still form keeps the level. */
    private void convertToSource(BlockPosition position, FluidBlocks.Kind kind, int level) {
        quietSet(position, FluidBlocks.typeOf(kind, FluidBlocks.Form.SOURCE, level));
    }

    // ------------------------------------------------------------------
    // The scheduling (the seam first, the unwired window otherwise)
    // ------------------------------------------------------------------

    /** The reference's scheduleTick (pos, this, delay) — the seam's own no-merge-update rule. */
    private void schedule(BlockPosition position, Identifier family, int delay) {
        ScheduledTickSystem seam = scheduledTicks;
        if (seam != null) {
            seam.scheduleTick(position, family, delay);
            return;
        }
        long due = world.totalTicks() + Math.max(0, delay);
        Pending key = new Pending(position, family);
        pending.put(key, due); // the existing row re-arms (scheduleTick's own update rule)
        if (!order.contains(key)) {
            order.add(key);
        }
    }

    /**
     * The unwired window's pass (the bounded drain — the same rule the
     * retired adapted model carried): only entries present at entry may
     * fire this pass, a future-due entry re-arms to the back. The wired
     * engine never runs this (the seam's own doScheduledTicks pass owns
     * the dispatch).
     */
    public void tick() {
        long now = world.totalTicks();
        int entries = order.size();
        for (int i = 0; i < entries; i++) {
            Pending entry = order.poll();
            if (entry == null) {
                break;
            }
            Long due = pending.get(entry);
            if (due == null) {
                continue;
            }
            if (due > now) {
                order.add(entry); // re-arm for its own due tick
                continue;
            }
            pending.remove(entry);
            BlockType state = world.getBlock(entry.position());
            // The dispatch gate (the seam's own shape): the cell must
            // still hold the scheduled family.
            if (state.identifier().equals(entry.family())) {
                dispatchTick(null, entry.position(), state, random);
            }
        }
    }

    // ------------------------------------------------------------------
    // The cadence + the §27.7 gate
    // ------------------------------------------------------------------

    /** The kind's cadence in THIS dimension (the reference's getTickRate). */
    private int cadenceOf(FluidBlocks.Kind kind) {
        return FluidBlocks.cadenceOf(kind, world.hasNoSky());
    }

    /**
     * The §27.7 pre-commit gate: fires the public FluidFlowEvent right
     * before the flow commits and answers whether the cell takes the
     * fluid. A cancelled flow behaves as blocked ground (the walk's own
     * rules continue around it). A null bus (the unwired window) admits
     * everything.
     */
    private boolean fireFlowGate(BlockPosition from, BlockPosition target,
                                 FluidBlocks.Kind kind, int level) {
        var bus = eventBus;
        if (bus == null) {
            return true;
        }
        var event = new net.zaminmc.torch.event.block.FluidFlowEvent(
                world.name(), from, target,
                (kind == FluidBlocks.Kind.WATER
                        ? FluidBlocks.WATER_SOURCE : FluidBlocks.LAVA_SOURCE).toString(),
                level);
        bus.fire(event);
        return !event.cancelled();
    }

    /**
     * The reference's fizz (LiquidBlock.fizz lines 296-305): the sound at
     * the cell's CENTER — {@code pos + 0.5} on every axis, the house
     * convention every block sound carries — at volume 0.5 with the
     * reference's own pitch band {@code 2.6F + (rand - rand) * 0.8F} over
     * the world's rolls (the 1.8-3.4 band; the pre-fix emission carried
     * the raw corner and a flat 1.0 pitch). The eight SMOKE_LARGE
     * particles are a NON-ROW per the F-DUST adjudication — the dedicated
     * server's addParticle is the empty stub, the client predicts its
     * own burst.
     */
    private void fizz(BlockPosition at) {
        float pitch = 2.6f + (random.nextFloat() - random.nextFloat()) * 0.8f;
        sink.sound(new Position(at.x() + 0.5, at.y() + 0.5, at.z() + 0.5),
                "random.fizz", 0.5f, pitch);
    }

    /** @return the block at the offset (the y-band guard inside). */
    private BlockType at(BlockPosition base, int[] offset) {
        int y = base.y() + offset[1];
        if (y < BlockPosition.MIN_Y || y > BlockPosition.MAX_Y) {
            return world.airType();
        }
        return world.getBlock(new BlockPosition(base.x() + offset[0], y, base.z() + offset[2]));
    }

    /** The horizontal directions (the reference's Direction.Plane.HORIZONTAL). */
    private enum Offset {
        EAST(1, 0), WEST(-1, 0), SOUTH(0, 1), NORTH(0, -1);

        static final Offset[] ALL = values();

        final int x;
        final int z;

        Offset(int x, int z) {
            this.x = x;
            this.z = z;
        }

        Offset opposite() {
            return switch (this) {
                case EAST -> WEST;
                case WEST -> EAST;
                case SOUTH -> NORTH;
                case NORTH -> SOUTH;
            };
        }
    }
}
