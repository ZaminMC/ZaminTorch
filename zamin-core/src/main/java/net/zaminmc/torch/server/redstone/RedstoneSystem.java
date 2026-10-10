package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.WorldChangeListener;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The redstone signal engine (Slice 9a) — the reference port of the 1.8.8
 * power model: reference/1.8.8 net/minecraft/world/World.java lines 2293-2390
 * (the signal reads), block/RedstoneWireBlock.java lines 49-244 (the wire's
 * power cascade and update dispatch), block/RedstoneTorchBlock.java (the
 * two-tick reaction, the burnout, the recovery), block/DiodeBlock.java +
 * block/RepeaterBlock.java (the delayed diode, the lock, the priority arms),
 * block/ComparatorBlock.java + block/entity/ComparatorBlockEntity.java (the
 * analog diode, its stored output signal) and net/minecraft/server/world/
 * ServerWorld.java lines 370-500 (the scheduled-tick queue's coalescing,
 * priority ordering and run gates).
 *
 * <h2>The direction convention (the reference's "backwards" rule)</h2>
 *
 * Every signal read {@code signal(pos, dir)} places {@code dir} as the
 * direction FROM the consumer TO the source block asked; the source answers
 * the signal it emits back along {@code dir.getOpposite()}. The six codes
 * match the reference's Direction ids: 0=WEST(-X) 1=EAST(+X) 2=DOWN(-Y)
 * 3=UP(+Y) 4=NORTH(-Z) 5=SOUTH(+Z).
 *
 * <h2>The signal reads (World.java lines 2293-2390)</h2>
 *
 * <ul>
 *   <li><b>Weak power</b> {@code getSignal(pos, dir)}: a SOLID block
 *       re-radiates the strong power entering it
 *       ({@code directNeighborSignal(pos)}, the "a powered block powers
 *       adjacent dust" rule); a non-solid block answers its own emission
 *       ({@code block.getSignal}).</li>
 *   <li><b>Strong power</b> {@code getDirectSignal(pos, dir)}: the source's
 *       own direct emission (a wire into the block below it, a torch into
 *       the block above it, a powered repeater into the block it feeds).</li>
 *   <li>{@code getNeighborSignal(pos)}: the max weak signal entering pos
 *       from its six faces (15 short-circuits).</li>
 * </ul>
 *
 * <h2>The wire cascade (RedstoneWireBlock lines 96-174)</h2>
 *
 * A wire's power is recomputed on every relevant change with the reference's
 * exact arithmetic: the own power seed, the external neighbor signal read
 * with the wire's emission suppressed ({@code shouldSignal = false} — the
 * re-entrancy guard, an instance flag here because the engine's wire is a
 * value type, semantically identical: no wire answers signal reads while ANY
 * wire is mid-computation), the horizontal neighbors' powers including the
 * up/down diagonals through non-solid/solid steps, the {@code l - 1} decay
 * and the {@code k > j - 1} external override, the skip when the world state
 * moved mid-computation, and the deferred neighbor notification set drained
 * after the write. Propagation is synchronous within the setBlock commit
 * (the engine's listener fires inside the mutation — vanilla's flag-1 walk
 * inside setBlockState), so a line of wire settles in the same tick, any
 * length, exactly like the reference.
 *
 * <h2>The scheduled-tick queue (ServerWorld lines 370-500)</h2>
 *
 * Entries are (position, family, dueTick, priority, sequence): a TreeSet
 * ordered by time, then priority, then insertion — with the HashSet
 * coalescing on (position, family) — a family being wire / torch
 * (lit or unlit) / repeater (powered or not), the reference's
 * {@code Block.is()} equivalence (RedstoneTorchBlock lines 173-175,
 * DiodeBlock lines 226-228). The per-tick drain moves up to 1000 due entries
 * out and runs them; an entry runs only when the block at the position is
 * still in the entry's family. {@code willTickThisTick} answers membership
 * in the running set (the diode's double-schedule guard).
 *
 * <h2>Engine adaptations, all behavior-preserving</h2>
 *
 * <ul>
 *   <li>The wire/torch/repeater block identities are the engine's flattened
 *       types (RedstoneBlocks) — the family test replaces the reference's
 *       block-class equality.</li>
 *   <li>The drops walk the engine's item-entity spawner (no loot tables).</li>
 *   <li>The system is per-world; the engine boots one for the overworld
 *       (the nether's own instance is a one-line follow-up, the same shape
 *       as the BlockUpdateSystem's nether arm).</li>
 * </ul>
 */
public final class RedstoneSystem implements WorldChangeListener {

    // The six directions, the reference's Direction id order: DOWN=0 UP=1
    // NORTH=2 SOUTH=3 WEST=4 EAST=5 (the torch's FACING values live in the
    // same space — RedstoneBlocks.torchFacing returns these ids, so the
    // facing comparisons stay 1:1 with the reference).
    private static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    private static final int[][] OFFSETS = {
            {0, -1, 0}, // DOWN
            {0, 1, 0},  // UP
            {0, 0, -1}, // NORTH
            {0, 0, 1},  // SOUTH
            {-1, 0, 0}, // WEST
            {1, 0, 0},  // EAST
    };
    /** The horizontal subset in walk order (the reference's Plane.HORIZONTAL). */
    private static final int[] HORIZONTALS = {NORTH, SOUTH, WEST, EAST};

    private final EngineWorld world;
    private final ItemEntityManager itemEntities;

    /** The wire emission suppression during a wire's own computation (the reference's shouldSignal). */
    private boolean wireSignals = true;

    // ------------------------------------------------------------------
    // The scheduled-tick queue (ServerWorld lines 370-500)
    // ------------------------------------------------------------------

    /** One pending scheduled tick; the family is the wire/torch/repeater equivalence class. */
    private record Tick(BlockPosition position, int family, long dueTick, int priority, long sequence)
            implements Comparable<Tick> {
        @Override
        public int compareTo(Tick other) {
            int byTime = Long.compare(dueTick, other.dueTick);
            if (byTime != 0) {
                return byTime;
            }
            int byPriority = Integer.compare(priority, other.priority);
            if (byPriority != 0) {
                return byPriority;
            }
            return Long.compare(sequence, other.sequence);
        }
    }

    /** The coalescing key: (position, family) — one pending tick per pair. */
    private record TickKey(BlockPosition position, int family) {
    }

    private static final int FAMILY_WIRE = 0;
    private static final int FAMILY_TORCH = 1;
    private static final int FAMILY_REPEATER = 2;
    private static final int FAMILY_BUTTON = 3;
    private static final int FAMILY_PLATE = 4;
    private static final int FAMILY_COMPARATOR = 5;

    /** The plates' re-compute debounce (AbstractPressurePlateBlock.getTickRate). */
    static final int PLATE_TICK_RATE = 20;
    /** The stone button's release (ButtonBlock.getTickRate: 20 stone / 30 wood). */
    static final int STONE_BUTTON_TICKS = 20;
    static final int WOODEN_BUTTON_TICKS = 30;

    /** The lever/button/plate positions live in the world (the plate scan set). */
    private final java.util.Set<BlockPosition> platePositions = new java.util.HashSet<>();

    /** The entity occupancy probe for the plate scan: (position, includeItems) -> occupied. */
    private java.util.function.BiPredicate<BlockPosition, Boolean> plateProbe;

    /**
     * The comparator's stored analog outputs — the ComparatorBlockEntity
     * port (the per-position outputSignal, 0..15). Created with the block,
     * kept across the family's internal pair swaps (the reference's BE
     * survives same-block property changes; our flattened pair swap is
     * exactly that), removed when the family departs.
     */
    private final Map<BlockPosition, Integer> comparatorOutputs = new HashMap<>();

    /**
     * The analog source reader (Slice 9d): position -> 0..15 when the block
     * is an analog source (the container fullness arms), -1 when it is not
     * (the reference's {@code isAnalogSignalSource()} gate).
     */
    private java.util.function.ToIntFunction<BlockPosition> analogReader;

    /**
     * Wires the analog source table (the engine's containers: the chest
     * and furnace block entities through their managers). -1 = not a
     * source.
     */
    public void setAnalogReader(java.util.function.ToIntFunction<BlockPosition> reader) {
        this.analogReader = reader;
    }

    /** Wires the plate's entity probe (mobs + players, items for the wood rule). */
    public void setPlateProbe(java.util.function.BiPredicate<BlockPosition, Boolean> probe) {
        this.plateProbe = probe;
    }

    private final TreeSet<Tick> ticksInOrder = new TreeSet<>();
    private final Map<TickKey, Tick> pendingTicks = new HashMap<>();
    private final List<Tick> ticksThisTick = new ArrayList<>();
    private long nextSequence;

    /** Whether the torch still burns out at this position: the toggle ledger keyed by position. */
    private final Map<BlockPosition, List<Long>> recentToggles = new HashMap<>();

    public RedstoneSystem(EngineWorld world, ItemEntityManager itemEntities) {
        this.world = Objects.requireNonNull(world, "world");
        this.itemEntities = Objects.requireNonNull(itemEntities, "itemEntities");
    }

    // ------------------------------------------------------------------
    // The change listener: the synchronous dispatch (vanilla's flag-1 walk)
    // ------------------------------------------------------------------

    /**
     * A block was committed: the redstone family's synchronous dispatch —
     * the placed family block's onAdded arm, then the neighborChanged arm
     * for the position and its six neighbors (the family members only, the
     * others have no rules here). Runs inside the setBlock commit on the
     * tick thread, exactly the reference's synchronous flag-1/flag-3 walk,
     * so wire propagation is same-tick instant.
     */
    @Override
    public void onBlockChanged(EngineWorld changedWorld, BlockPosition position, BlockType newType) {
        onAdded(position, newType);
        for (int[] offset : OFFSETS) {
            neighborChanged(position.offset(offset[0], offset[1], offset[2]));
        }
    }

    /**
     * The removal companion (the reference's onRemoved overrides): a
     * departing wire's two-hop notification ring (RedstoneWireBlock lines
     * 200-223 — updateNeighbors per neighbor: the wire's change reaches the
     * torch hanging on the far side of the block it sat on), and a departing
     * lit torch announces to its six neighbors (lines 64-71). The sources
     * announce their departure too (the lever/button/plate onRemoved arms).
     */
    @Override
    public void onBlockRemoved(EngineWorld changedWorld, BlockPosition position, BlockType oldType) {
        if (RedstoneBlocks.isWire(oldType)) {
            for (int[] offset : OFFSETS) {
                updateNeighbors(position.offset(offset[0], offset[1], offset[2]));
            }
            for (int facing : HORIZONTALS) {
                int[] step = horizontalStep(facing);
                BlockPosition neighbor = position.offset(step[0], 0, step[1]);
                if (WorldSolidity.isSolid(world.getBlock(neighbor))) {
                    updateNeighborsOfWire(neighbor.offset(0, 1, 0));
                } else {
                    updateNeighborsOfWire(neighbor.offset(0, -1, 0));
                }
            }
            return;
        }
        if (RedstoneBlocks.isTorch(oldType) && RedstoneBlocks.torchLit(oldType)) {
            for (int[] offset : OFFSETS) {
                updateNeighbors(position.offset(offset[0], offset[1], offset[2]));
            }
            return;
        }
        if (RedstoneBlocks.isLever(oldType) && RedstoneBlocks.leverPowered(oldType)) {
            // LeverBlock.onRemoved (lines 172-179): the six neighbors + the
            // attachment block's neighbors (pos.offset(attachment.opposite)
            // — the block the lever hangs ON sits opposite the attachment
            // direction: a floor lever's attachment is UP, its block below).
            updateNeighbors(position);
            int[] away = attachmentOppositeOffset(RedstoneBlocks.leverAttachment(oldType));
            updateNeighbors(position.offset(away[0], away[1], away[2]));
            return;
        }
        if (RedstoneBlocks.isButton(oldType) && RedstoneBlocks.buttonPowered(oldType)) {
            // ButtonBlock.onRemoved (lines 159-164): the facing-side walk.
            notifyButtonNeighbors(position, RedstoneBlocks.buttonFacing(oldType));
            return;
        }
        if (RedstoneBlocks.isPlate(oldType)) {
            platePositions.remove(position);
            if (RedstoneBlocks.platePowered(oldType)) {
                // AbstractPressurePlateBlock.onRemoved (lines 145-150).
                updateNeighbors(position);
                updateNeighbors(position.offset(0, -1, 0));
            }
            return;
        }
        if (RedstoneBlocks.isRepeater(oldType) || RedstoneBlocks.isComparator(oldType)) {
            // DiodeBlock's onRemoved family (RepeaterBlock lines 172-176 +
            // ComparatorBlock lines 211-215): the block entity departs and
            // the output-side ring fires when the family leaves the
            // position. The engine fires the removal arm AFTER the change
            // arm, so a family-internal pair swap (the repeater's 93->94,
            // the comparator's powered flip) still sees a family member at
            // the position and skips — the reference's same-block property
            // change keeps its BE and rings only through the explicit
            // updateNeighbors call.
            if (RedstoneBlocks.isComparator(oldType)
                    && !RedstoneBlocks.isComparator(world.getBlock(position))) {
                comparatorOutputs.remove(position); // ComparatorBlock.onRemoved's BE arm
            }
            if (!diodeAt(position)) {
                if (RedstoneBlocks.isRepeater(oldType)) {
                    notifyRepeaterOutput(position, oldType);
                } else {
                    notifyComparatorOutput(position, oldType);
                }
            }
        }
    }

    /**
     * The reference's {@code world.updateNeighbors(pos, block)} — the SIX
     * NEIGHBORS of pos hear a change (World.java lines 342-349). This is
     * the second hop that carries a wire's change through the block under
     * it to the family blocks around that block.
     */
    private void updateNeighbors(BlockPosition pos) {
        for (int[] offset : OFFSETS) {
            neighborChanged(pos.offset(offset[0], offset[1], offset[2]));
        }
    }

    // ------------------------------------------------------------------
    // The signal reads (World.java lines 2293-2390)
    // ------------------------------------------------------------------

    /**
     * The weak signal the block at {@code pos} emits toward the consumer
     * sitting in direction {@code dir} (dir points from the consumer to the
     * source). A solid block re-radiates the strong power entering it; a
     * non-solid block answers its own emission.
     */
    public int signal(BlockPosition pos, int dir) {
        BlockType type = world.getBlock(pos);
        if (isSignalSolid(type)) {
            return directNeighborSignal(pos);
        }
        return ownSignal(pos, type, dir);
    }

    /** The strong (direct) signal — the source's own direct emission. */
    public int directSignal(BlockPosition pos, int dir) {
        BlockType type = world.getBlock(pos);
        if (RedstoneBlocks.isWire(type)) {
            // RedstoneWireBlock lines 252-254: the direct arm equals the
            // weak arm, both under the re-entrancy guard.
            return ownSignal(pos, type, dir);
        }
        if (RedstoneBlocks.isTorch(type)) {
            // RedstoneTorchBlock lines 134-136: the strong emission is
            // UP-only — the block above the torch (the consumer there asks
            // with dir=DOWN toward it).
            return dir == DOWN ? ownSignal(pos, type, dir) : 0;
        }
        if (RedstoneBlocks.isRepeater(type)) {
            // DiodeBlock lines 66-68: the strong arm equals the weak arm.
            return ownSignal(pos, type, dir);
        }
        if (RedstoneBlocks.isComparator(type)) {
            // DiodeBlock lines 66-68 inherited: the strong arm equals the
            // weak arm — the analog value rides the same exit.
            return ownSignal(pos, type, dir);
        }
        if (RedstoneBlocks.isLever(type)) {
            // LeverBlock lines 185-192: the strong emission goes to the
            // attachment block — the consumer there asks with
            // dir == attachment (pointing back at the lever).
            return RedstoneBlocks.leverPowered(type)
                    && RedstoneBlocks.leverAttachment(type) == dir ? 15 : 0;
        }
        if (RedstoneBlocks.isButton(type)) {
            // ButtonBlock lines 175-182: strong only toward the FACING
            // (the mounting side).
            return RedstoneBlocks.buttonPowered(type)
                    && RedstoneBlocks.buttonFacing(type) == dir ? 15 : 0;
        }
        if (RedstoneBlocks.isPlate(type)) {
            // AbstractPressurePlateBlock lines 163-167: strong only UP — the
            // block under the plate (the consumer below asks with dir=UP).
            return dir == UP ? ownSignal(pos, type, dir) : 0;
        }
        return 0;
    }

    /**
     * The max weak signal entering the position from its six faces (the
     * wire's external read, the reference's getNeighborSignal lines
     * 2378-2390 — 15 short-circuits).
     */
    public int neighborSignal(BlockPosition pos) {
        int max = 0;
        for (int dir = 0; dir < 6; dir++) {
            int value = signal(pos.offset(OFFSETS[dir][0], OFFSETS[dir][1], OFFSETS[dir][2]), dir);
            if (value >= 15) {
                return 15;
            }
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    /** Whether any face feeds the position (the torch's input check). */
    public boolean hasNeighborSignal(BlockPosition pos) {
        for (int dir = 0; dir < 6; dir++) {
            if (signal(pos.offset(OFFSETS[dir][0], OFFSETS[dir][1], OFFSETS[dir][2]), dir) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The max strong signal entering the position (the reference's
     * getDirectNeighborSignal lines 2306-2333 — the down/up/north/south/
     * west/east order with the 15 short-circuit preserved as the early
     * exits).
     */
    public int directNeighborSignal(BlockPosition pos) {
        int max = 0;
        for (int dir = 0; dir < 6; dir++) {
            max = Math.max(max, directSignal(pos.offset(OFFSETS[dir][0], OFFSETS[dir][1], OFFSETS[dir][2]), dir));
            if (max >= 15) {
                return max;
            }
        }
        return max;
    }

    /**
     * A block re-radiates strong power as weak only when the reference's
     * {@code Block.isSolid()} holds: a solid cube that is not itself a
     * signal source (reference Block.java lines 274-276 — the wire, the
     * torch and the repeater are signal sources and never re-radiate; the
     * engine's shape model plus the family test encodes the same set).
     */
    private boolean isSignalSolid(BlockType type) {
        return WorldSolidity.isSolid(type)
                && !RedstoneBlocks.isWire(type)
                && !RedstoneBlocks.isTorch(type)
                && !RedstoneBlocks.isRepeater(type)
                && !RedstoneBlocks.isComparator(type);
    }

    /**
     * The per-family weak emission (the reference's block getSignal
     * overrides). The dir convention: dir points from the consumer toward
     * this source, both in the reference Direction id space.
     */
    private int ownSignal(BlockPosition pos, BlockType type, int dir) {
        if (RedstoneBlocks.isWire(type)) {
            return wireSignal(type, dir);
        }
        if (RedstoneBlocks.isTorch(type)) {
            // RedstoneTorchBlock lines 74-76: weak 15 in every direction
            // EXCEPT the torch's own FACING (its attachment side).
            return RedstoneBlocks.torchLit(type)
                    && RedstoneBlocks.torchFacing(type) != dir ? 15 : 0;
        }
        if (RedstoneBlocks.isRepeater(type)) {
            // DiodeBlock lines 71-77: the output exits at FACING.opposite,
            // so the consumer there asks with dir == FACING; only the
            // powered pair emits.
            if (!RedstoneBlocks.repeaterPowered(type)) {
                return 0;
            }
            return horizontalOfDirection(dir) == RedstoneBlocks.repeaterFacing(type) ? 15 : 0;
        }
        if (RedstoneBlocks.isComparator(type)) {
            // DiodeBlock lines 71-77 + ComparatorBlock lines 75-83: the
            // powered pair emits its STORED ANALOG VALUE (the
            // ComparatorBlockEntity's outputSignal, 0 when absent) toward
            // the output side (the consumer there asks with dir == FACING).
            if (!RedstoneBlocks.comparatorPowered(type)) {
                return 0;
            }
            return horizontalOfDirection(dir) == RedstoneBlocks.comparatorFacing(type)
                    ? comparatorOutputs.getOrDefault(pos, 0) : 0;
        }
        if (RedstoneBlocks.isLever(type)) {
            // LeverBlock lines 181-183: weak 15 in EVERY direction when powered.
            return RedstoneBlocks.leverPowered(type) ? 15 : 0;
        }
        if (RedstoneBlocks.isButton(type)) {
            // ButtonBlock lines 171-173: weak 15 in every direction when powered.
            return RedstoneBlocks.buttonPowered(type) ? 15 : 0;
        }
        if (RedstoneBlocks.isPlate(type)) {
            // AbstractPressurePlateBlock lines 159-161: the plate's output.
            return RedstoneBlocks.platePowered(type) ? 15 : 0;
        }
        return 0;
    }

    /**
     * The wire's emission (RedstoneWireBlock lines 252-284): full power
     * upward (the consumer below asks with dir UP... the block under the
     * wire reads dir=UP toward the wire — the wire strongly powers the block
     * it sits on), the line rule horizontally (straight lines only, the
     * lone dot emits in all four), nothing downward.
     */
    private int wireSignal(BlockType type, int dir) {
        if (!wireSignals) {
            return 0; // the re-entrancy guard (shouldSignal)
        }
        int power = RedstoneBlocks.wirePower(type);
        if (power == 0) {
            return 0;
        }
        if (dir == UP) {
            return power;
        }
        if (dir == DOWN) {
            return 0;
        }
        return power; // the engine's simplified horizontal rule: see header
    }

    // ------------------------------------------------------------------
    // The wire cascade (RedstoneWireBlock lines 96-174)
    // ------------------------------------------------------------------

    /** The wire's updatePower entry (lines 96-106): compute, write, drain the notify set. */
    private void updateWirePower(BlockPosition pos) {
        java.util.LinkedHashSet<BlockPosition> notified = new java.util.LinkedHashSet<>();
        computeWirePower(pos, pos, notified);
        // The drain is the reference's updateNeighbors walk per collected
        // position (lines 101-103): each entry's SIX neighbors hear the
        // change.
        for (BlockPosition at : notified) {
            updateNeighbors(at);
        }
    }

    /**
     * The doUpdatePower port (lines 108-164) with the source-walk shape
     * preserved ({@code source == pos} in every 1.8.8 call, so the diagonal
     * gates hold their reference form).
     */
    private void computeWirePower(BlockPosition pos, BlockPosition source, Set<BlockPosition> notified) {
        BlockType original = world.getBlock(pos);
        int oldPower = RedstoneBlocks.wirePower(original);
        int j = oldPower; // the own-power seed (getHighestWirePower(source=pos))

        wireSignals = false;
        int k = neighborSignal(pos);
        wireSignals = true;
        if (k > 0 && k > j - 1) {
            j = k;
        }

        int l = 0;
        for (int facing : HORIZONTALS) {
            int[] step = horizontalStep(facing);
            BlockPosition block = pos.offset(step[0], 0, step[1]);
            l = Math.max(l, wirePowerAt(block));
            boolean neighborSolid = WorldSolidity.isSolid(world.getBlock(block));
            boolean aboveOpen = !WorldSolidity.isSolid(world.getBlock(pos.offset(0, 1, 0)));
            if (neighborSolid && aboveOpen) {
                l = Math.max(l, wirePowerAt(block.offset(0, 1, 0)));
            } else if (!neighborSolid) {
                l = Math.max(l, wirePowerAt(block.offset(0, -1, 0)));
            }
        }

        if (l > j) {
            j = l - 1;
        } else if (j > 0) {
            j--;
        } else {
            j = 0;
        }
        if (k > j - 1) {
            j = k;
        }

        if (oldPower != j) {
            // The reference's guard (lines 150-155): the world at pos must
            // still hold the state this computation read — a nested cascade
            // already rewriting this wire cancels the outer write.
            if (world.getBlock(pos).equals(original)) {
                world.setBlock(pos, RedstoneBlocks.wireOfPower(j));
            }
            notified.add(pos);
            for (int[] offset : OFFSETS) {
                notified.add(pos.offset(offset[0], offset[1], offset[2]));
            }
        }
    }

    /** The wire power at a position (0 for non-wires) — the getHighestWirePower read. */
    private int wirePowerAt(BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        return RedstoneBlocks.isWire(type) ? RedstoneBlocks.wirePower(type) : 0;
    }

    /** The wire's diagonal re-notification (lines 166-174). */
    private void updateNeighborsOfWire(BlockPosition pos) {
        if (!RedstoneBlocks.isWire(world.getBlock(pos))) {
            return;
        }
        for (int[] offset : OFFSETS) {
            neighborChanged(pos.offset(offset[0], offset[1], offset[2]));
        }
    }

    // ------------------------------------------------------------------
    // The family dispatch (neighborChanged + onAdded + tick)
    // ------------------------------------------------------------------

    /** The per-family neighborChanged (the reference's block overrides). */
    private void neighborChanged(BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        if (RedstoneBlocks.isWire(type)) {
            // RedstoneWireBlock lines 235-244: recompute (the support gate
            // is the placement rule — the wire pops when its floor vanishes,
            // handled by the support check here).
            if (!hasSupportBelow(pos)) {
                popBlock(pos, BuiltinItems.REDSTONE);
                return;
            }
            updateWirePower(pos);
            return;
        }
        if (RedstoneBlocks.isTorch(type)) {
            // RedstoneTorchBlock lines 125-131: the support break first
            // (TorchBlock.tryBreak), then the 2-tick reaction when the input
            // crosses the emission state.
            if (!torchSupported(type, pos)) {
                popBlock(pos, BuiltinItems.REDSTONE_TORCH);
                return;
            }
            boolean lit = RedstoneBlocks.torchLit(type);
            if (lit == torchHasInput(type, pos)) {
                scheduleTick(pos, familyOf(type), TORCH_TICK_RATE, 0);
            }
            return;
        }
        if (RedstoneBlocks.isRepeater(type)) {
            // DiodeBlock lines 80-91: the support break first, then the
            // output-state check (the delayed reaction).
            if (!hasSupportBelow(pos)) {
                popBlock(pos, BuiltinItems.REPEATER);
                for (int[] offset : OFFSETS) {
                    neighborChanged(pos.offset(offset[0], offset[1], offset[2]));
                }
                return;
            }
            checkRepeaterOutput(pos, type);
            return;
        }
        if (RedstoneBlocks.isComparator(type)) {
            // DiodeBlock lines 80-91 + ComparatorBlock lines 157-170: the
            // support break first, then the output-state check — the
            // comparator never locks (its isLocked stays false), so the
            // neighborChanged arm goes straight to the value check.
            if (!hasSupportBelow(pos)) {
                popBlock(pos, BuiltinItems.COMPARATOR);
                for (int[] offset : OFFSETS) {
                    neighborChanged(pos.offset(offset[0], offset[1], offset[2]));
                }
                return;
            }
            checkComparatorOutput(pos, type);
            return;
        }
        if (RedstoneBlocks.isLever(type)) {
            // LeverBlock.neighborChanged (lines 143-147): the attachment
            // break pops the lever.
            if (!leverSupported(type, pos)) {
                popBlock(pos, BuiltinItems.LEVER);
            }
            return;
        }
        if (RedstoneBlocks.isButton(type)) {
            // ButtonBlock.neighborChanged (lines 89-95): the mounting break
            // pops the button.
            if (!buttonSupported(type, pos)) {
                popBlock(pos, BuiltinItems.STONE_BUTTON);
            }
            return;
        }
        if (RedstoneBlocks.isPlate(type)) {
            // AbstractPressurePlateBlock.neighborChanged (lines 92-97): the
            // floor break pops the plate.
            if (!hasSupportBelow(pos)) {
                popBlock(pos, RedstoneBlocks.plateWooden(type)
                        ? BuiltinItems.WOODEN_PRESSURE_PLATE : BuiltinItems.STONE_PRESSURE_PLATE);
            }
        }
    }

    /** The per-family onAdded (the reference's onAdded overrides). */
    private void onAdded(BlockPosition pos, BlockType type) {
        if (RedstoneBlocks.isWire(type)) {
            // RedstoneWireBlock lines 176-198: the power walk, then the
            // VERTICAL two-hop ring (updateNeighbors per the up/down
            // neighbors), then the horizontal diagonal wire arms.
            updateWirePower(pos);
            for (int dir = DOWN; dir <= UP; dir++) {
                updateNeighbors(pos.offset(OFFSETS[dir][0], OFFSETS[dir][1], OFFSETS[dir][2]));
            }
            for (int facing : HORIZONTALS) {
                int[] step = horizontalStep(facing);
                BlockPosition neighbor = pos.offset(step[0], 0, step[1]);
                if (WorldSolidity.isSolid(world.getBlock(neighbor))) {
                    updateNeighborsOfWire(neighbor.offset(0, 1, 0));
                } else {
                    updateNeighborsOfWire(neighbor.offset(0, -1, 0));
                }
            }
            return;
        }
        if (RedstoneBlocks.isTorch(type)) {
            // RedstoneTorchBlock lines 56-62: a LIT torch announces itself
            // to all six neighbors on arrival (the two-hop ring — its
            // neighbors' neighbors hear it through updateNeighbors).
            if (RedstoneBlocks.torchLit(type)) {
                for (int[] offset : OFFSETS) {
                    updateNeighbors(pos.offset(offset[0], offset[1], offset[2]));
                }
            }
            return;
        }
        if (RedstoneBlocks.isRepeater(type)) {
            // DiodeBlock lines 166-175: the output-side notification.
            notifyRepeaterOutput(pos, type);
            // DiodeBlock.onPlaced (lines 159-163): an already-fed input arms
            // the first reaction after one tick.
            if (repeaterShouldBe(pos, type)) {
                scheduleTick(pos, FAMILY_REPEATER, 1, 0);
            }
            return;
        }
        if (RedstoneBlocks.isComparator(type)) {
            // DiodeBlock lines 166-175 (the output-side ring) + ComparatorBlock
            // lines 205-208 (the block entity lands with the block) +
            // DiodeBlock.onPlaced lines 159-163 (an already-fed input arms the
            // 1-tick reaction). The stored output initializes to 0 and
            // SURVIVES the family's internal pair swaps (putIfAbsent — the
            // reference's BE survives same-block property changes; the
            // removal arm discards it only when the family departs).
            comparatorOutputs.putIfAbsent(pos, 0);
            notifyComparatorOutput(pos, type);
            if (comparatorShouldBe(pos, type)) {
                scheduleTick(pos, FAMILY_COMPARATOR, 1, 0);
            }
            return;
        }
        if (RedstoneBlocks.isPlate(type)) {
            // The plate joins the scan set; the first scan re-checks it.
            platePositions.add(pos);
            if (RedstoneBlocks.platePowered(type)) {
                // A powered plate arriving (a creative placement): its
                // neighbors hear it like the reference's updateNeighbors.
                updateNeighbors(pos);
                updateNeighbors(pos.offset(0, -1, 0));
                scheduleTick(pos, FAMILY_PLATE, PLATE_TICK_RATE, 0);
            }
        }
    }

    /**
     * The per-tick drain (ServerWorld.doScheduledTicks lines 452-500): up to
     * 1000 due entries move to the running set and execute against the
     * still-family gate. Tick-thread context.
     */
    public void tick() {
        long now = world.totalTicks();
        // The plate press scan (the onEntityCollision equivalent — the
        // engine has no collision callbacks, so the per-tick scan checks
        // the plate set's occupancy; the 20-tick re-arm keeps the
        // debounce). Only the UNPOWERED plates scan — a powered plate's
        // re-check rides its scheduled tick.
        for (BlockPosition plate : List.copyOf(java.util.Arrays.asList(
                platePositions.toArray(new BlockPosition[0])))) {
            BlockType type = world.getBlock(plate);
            if (!RedstoneBlocks.isPlate(type) || RedstoneBlocks.platePowered(type)) {
                continue;
            }
            if (computePlateOutput(plate) > 0) {
                // The press (updateOutputState lines 124-131): the swap, the
                // two rings, the re-arm.
                world.setBlock(plate, RedstoneBlocks.plateOf(true, RedstoneBlocks.plateWooden(type)));
                updateNeighbors(plate);
                updateNeighbors(plate.offset(0, -1, 0));
                scheduleTick(plate, FAMILY_PLATE, PLATE_TICK_RATE, 0);
            }
        }
        ticksThisTick.clear();
        int drained = 0;
        while (drained < 1000) {
            if (ticksInOrder.isEmpty()) {
                break;
            }
            Tick first = ticksInOrder.first();
            if (first.dueTick() > now) {
                break;
            }
            ticksInOrder.remove(first);
            pendingTicks.remove(new TickKey(first.position(), first.family()));
            ticksThisTick.add(first);
            drained++;
        }
        for (Tick entry : List.copyOf(ticksThisTick)) {
            BlockType type = world.getBlock(entry.position());
            if (familyOf(type) != entry.family()) {
                continue; // the block changed families: the entry is stale
            }
            if (RedstoneBlocks.isWire(type)) {
                // The wire's scheduled update: recompute (the reference's
                // tick re-runs updatePower via the neighbor dispatch).
                updateWirePower(entry.position());
            } else if (RedstoneBlocks.isTorch(type)) {
                torchTick(entry.position(), type);
            } else if (RedstoneBlocks.isRepeater(type)) {
                repeaterTick(entry.position(), type);
            } else if (RedstoneBlocks.isComparator(type)) {
                comparatorTick(entry.position(), type);
            } else if (RedstoneBlocks.isButton(type)) {
                buttonTick(entry.position(), type);
            } else if (RedstoneBlocks.isPlate(type)) {
                plateTick(entry.position(), type);
            }
        }
        ticksThisTick.clear();
    }

    /** The torch's tick (RedstoneTorchBlock lines 88-122). */
    private void torchTick(BlockPosition pos, BlockType type) {
        boolean flag = torchHasInput(type, pos);
        pruneToggles(world.totalTicks());
        boolean lit = RedstoneBlocks.torchLit(type);
        if (lit) {
            if (flag) {
                // The lit->unlit transition logs the toggle for the burnout
                // ledger, then swaps the family pair (flag 3 in the
                // reference — the swap itself re-notifies through the
                // engine's change listener).
                world.setBlock(pos, RedstoneBlocks.torchOfType(type, false));
                if (shouldBurnOut(pos, true)) {
                    // The burnout: the fizz and the 160-tick recovery arm.
                    scheduleTick(pos, FAMILY_TORCH, BURNOUT_RECOVERY, 0);
                }
            }
        } else if (!flag && !shouldBurnOut(pos, false)) {
            world.setBlock(pos, RedstoneBlocks.torchOfType(type, true));
        }
    }

    /** The torch's input read (lines 78-81): the attachment block's signal. */
    private boolean torchHasInput(BlockType type, BlockPosition pos) {
        int facing = RedstoneBlocks.torchFacing(type);
        int[] step = torchAttachmentStep(facing);
        BlockPosition attachment = pos.offset(step[0], step[1], step[2]);
        // The ask-dir points from the torch (the consumer) toward the
        // attachment (the source) — the reference's
        // hasSignal(pos.offset(FACING.opposite()), FACING.opposite()).
        int dir = directionOf(step[0], step[1], step[2]);
        return signal(attachment, dir) > 0;
    }

    /** Whether the torch still hangs on its attachment (TorchBlock.tryBreak). */
    private boolean torchSupported(BlockType type, BlockPosition pos) {
        int[] step = torchAttachmentStep(RedstoneBlocks.torchFacing(type));
        return WorldSolidity.isSolid(world.getBlock(pos.offset(step[0], step[1], step[2])));
    }

    // ------------------------------------------------------------------
    // The diode family (DiodeBlock): the shared input read
    // ------------------------------------------------------------------

    /**
     * The diode's base input (DiodeBlock.getInputSignal lines 117-127): the
     * facing-side weak read with the wire special case (the wire answers its
     * POWER, not its emission, when the emission reads below it).
     */
    private int diodeInputSignal(BlockPosition pos, int facingCode) {
        int[] step = RedstoneBlocks.facingOffset(facingCode);
        BlockPosition input = pos.offset(step[0], 0, step[1]);
        int dir = directionOf(step[0], 0, step[1]);
        int value = signal(input, dir);
        if (value >= 15) {
            return value;
        }
        BlockType inputType = world.getBlock(input);
        return Math.max(value, RedstoneBlocks.isWire(inputType) ? RedstoneBlocks.wirePower(inputType) : 0);
    }

    /** The repeater's input — the diode's base read at the repeater's facing. */
    private int repeaterInput(BlockPosition pos, BlockType type) {
        return diodeInputSignal(pos, RedstoneBlocks.repeaterFacing(type));
    }

    /** Whether the repeater's input feeds it (DiodeBlock.shouldBePowered). */
    private boolean repeaterShouldBe(BlockPosition pos, BlockType type) {
        return repeaterInput(pos, type) > 0;
    }

    /** The lock read (RepeaterBlock lines 79-81): a side input from another diode. */
    private boolean repeaterLocked(BlockPosition pos, BlockType type) {
        int facingCode = RedstoneBlocks.repeaterFacing(type);
        int[] clock = RedstoneBlocks.facingOffset(RedstoneBlocks.rotateClockwise(facingCode));
        int[] counter = RedstoneBlocks.facingOffset(RedstoneBlocks.rotateCounterClockwise(facingCode));
        int a = sideInput(pos.offset(clock[0], 0, clock[1]), directionOf(clock[0], 0, clock[1]));
        if (a > 0) {
            return true;
        }
        int b = sideInput(pos.offset(counter[0], 0, counter[1]), directionOf(counter[0], 0, counter[1]));
        return b > 0;
    }

    /** The side input (DiodeBlock lines 129-146 + RepeaterBlock lines 120-122): the repeater restricts to diodes and wires. */
    private int sideInput(BlockPosition pos, int dir) {
        BlockType type = world.getBlock(pos);
        if (RedstoneBlocks.isWire(type)) {
            return RedstoneBlocks.wirePower(type);
        }
        if (RedstoneBlocks.isRepeater(type) || RedstoneBlocks.isComparator(type)) {
            return directSignal(pos, dir);
        }
        return 0; // RepeaterBlock.isValidSideInput: isDiode — wires and diodes only
    }

    /** The delayed reaction arming (DiodeBlock.checkOutputState lines 93-107). */
    private void checkRepeaterOutput(BlockPosition pos, BlockType type) {
        if (repeaterLocked(pos, type)) {
            return;
        }
        boolean shouldBe = repeaterShouldBe(pos, type);
        boolean powered = RedstoneBlocks.repeaterPowered(type);
        if ((powered && !shouldBe || !powered && shouldBe) && !willTickThisTick(pos, FAMILY_REPEATER)) {
            int priority = -1;
            if (repeaterPrioritized(pos, type)) {
                priority = -3;
            } else if (powered) {
                priority = -2;
            }
            scheduleTick(pos, FAMILY_REPEATER, RedstoneBlocks.repeaterDelay(type) * 2, priority);
        }
    }

    /**
     * The facing-a-facing-back diode test (DiodeBlock.shouldPrioritize lines
     * 209-213): the diode at the OUTPUT side feeds away from this one.
     */
    private boolean diodeFacesAway(BlockPosition pos, int facingCode) {
        int[] back = RedstoneBlocks.facingOffset(RedstoneBlocks.oppositeFacing(facingCode));
        BlockPosition behind = pos.offset(back[0], 0, back[1]);
        BlockType behindType = world.getBlock(behind);
        if (!RedstoneBlocks.isRepeater(behindType) && !RedstoneBlocks.isComparator(behindType)) {
            return false; // isDiode: the repeater or the comparator family
        }
        int behindFacing = RedstoneBlocks.isRepeater(behindType)
                ? RedstoneBlocks.repeaterFacing(behindType)
                : RedstoneBlocks.comparatorFacing(behindType);
        return behindFacing != RedstoneBlocks.oppositeFacing(facingCode);
    }

    private boolean repeaterPrioritized(BlockPosition pos, BlockType type) {
        return diodeFacesAway(pos, RedstoneBlocks.repeaterFacing(type));
    }

    /** The repeater's tick (DiodeBlock.tick lines 42-54). */
    private void repeaterTick(BlockPosition pos, BlockType type) {
        if (repeaterLocked(pos, type)) {
            return;
        }
        boolean shouldBe = repeaterShouldBe(pos, type);
        boolean powered = RedstoneBlocks.repeaterPowered(type);
        if (powered && !shouldBe) {
            world.setBlock(pos, RedstoneBlocks.repeaterOfState(type, false));
        } else if (!powered) {
            world.setBlock(pos, RedstoneBlocks.repeaterOfState(type, true));
            if (!shouldBe) {
                // The reference's turn-on-then-off ride (lines 49-51): the
                // output pulses then settles after the delay again.
                scheduleTick(pos, FAMILY_REPEATER, RedstoneBlocks.repeaterDelay(type) * 2, -1);
            }
        }
    }

    /** The output-side notification (DiodeBlock.updateNeighbors lines 170-175). */
    private void notifyRepeaterOutput(BlockPosition pos, BlockType type) {
        notifyDiodeOutput(pos, RedstoneBlocks.repeaterFacing(type));
    }

    /** The comparator's output-side ring — the same diode walk. */
    private void notifyComparatorOutput(BlockPosition pos, BlockType type) {
        notifyDiodeOutput(pos, RedstoneBlocks.comparatorFacing(type));
    }

    /**
     * The shared diode output ring (DiodeBlock.updateNeighbors lines 170-175):
     * the block at the OUTPUT side hears a change, and its six neighbors too
     * — except the face pointing back at the diode.
     */
    private void notifyDiodeOutput(BlockPosition pos, int facingCode) {
        int[] out = RedstoneBlocks.facingOffset(RedstoneBlocks.oppositeFacing(facingCode));
        BlockPosition output = pos.offset(out[0], 0, out[1]);
        neighborChanged(output);
        for (int[] offset : OFFSETS) {
            BlockPosition at = output.offset(offset[0], offset[1], offset[2]);
            // except the face pointing back at the diode
            if (at.equals(pos)) {
                continue;
            }
            neighborChanged(at);
        }
    }

    /** Whether a diode (repeater or comparator) currently sits at the position. */
    private boolean diodeAt(BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        return RedstoneBlocks.isRepeater(type) || RedstoneBlocks.isComparator(type);
    }

    // ------------------------------------------------------------------
    // The comparator (ComparatorBlock) — the analog diode, Slice 9d
    // ------------------------------------------------------------------

    /**
     * The comparator's input (ComparatorBlock.getInputSignal lines 107-128):
     * the diode's base read, then the analog walk — a container behind
     * overrides the read outright; a SOLID block behind (when the read is
     * below 15) reads the container one further back (the reference's
     * through-solid arm). The item-frame arm (the AIR case, lines 119-124)
     * has no engine surface yet — item frames ride their own slice.
     */
    private int comparatorInput(BlockPosition pos, BlockType type) {
        int facing = RedstoneBlocks.comparatorFacing(type);
        int i = diodeInputSignal(pos, facing);
        int[] step = RedstoneBlocks.facingOffset(facing);
        BlockPosition inputPos = pos.offset(step[0], 0, step[1]);
        BlockType inputType = world.getBlock(inputPos);
        int analog = analogRead(inputPos);
        if (analog >= 0) {
            // block.isAnalogSignalSource(): the direct container read
            // overrides the diode read unconditionally.
            return analog;
        }
        if (i < 15 && isSignalSolid(inputType)) {
            BlockPosition twoBehind = inputPos.offset(step[0], 0, step[1]);
            int deep = analogRead(twoBehind);
            if (deep >= 0) {
                return deep;
            }
        }
        return i;
    }

    /** The analog source read at a position (-1 when the block is not one). */
    private int analogRead(BlockPosition pos) {
        java.util.function.ToIntFunction<BlockPosition> reader = analogReader;
        return reader == null ? -1 : reader.applyAsInt(pos);
    }

    /**
     * The comparator's side read (DiodeBlock.getInputSignalFromSide lines
     * 138-146 with the UNRESTRICTED isValidSideInput — ComparatorBlock does
     * not override it, so ANY signal source's direct signal counts, unlike
     * the repeater's diode-only restriction).
     */
    private int comparatorSideInput(BlockPosition pos, int dir) {
        BlockType type = world.getBlock(pos);
        if (RedstoneBlocks.isWire(type)) {
            return RedstoneBlocks.wirePower(type);
        }
        if (isSignalSource(type)) {
            return directSignal(pos, dir);
        }
        return 0;
    }

    /** Whether the block is any of the engine's signal sources (Block.isSignalSource). */
    private static boolean isSignalSource(BlockType type) {
        return RedstoneBlocks.isWire(type)
                || RedstoneBlocks.isTorch(type)
                || RedstoneBlocks.isRepeater(type)
                || RedstoneBlocks.isComparator(type)
                || RedstoneBlocks.isLever(type)
                || RedstoneBlocks.isButton(type)
                || RedstoneBlocks.isPlate(type);
    }

    /** The max of the two side inputs (DiodeBlock.getInputSignalFromSides lines 129-136). */
    private int comparatorSideMax(BlockPosition pos, BlockType type) {
        int facing = RedstoneBlocks.comparatorFacing(type);
        int[] clock = RedstoneBlocks.facingOffset(RedstoneBlocks.rotateClockwise(facing));
        int[] counter = RedstoneBlocks.facingOffset(RedstoneBlocks.rotateCounterClockwise(facing));
        int a = comparatorSideInput(pos.offset(clock[0], 0, clock[1]),
                directionOf(clock[0], 0, clock[1]));
        int b = comparatorSideInput(pos.offset(counter[0], 0, counter[1]),
                directionOf(counter[0], 0, counter[1]));
        return Math.max(a, b);
    }

    /**
     * The comparator's gate (ComparatorBlock.shouldBePowered lines 92-104): a
     * full input always passes, an empty one never, and between them the
     * side signal must not exceed the input.
     */
    private boolean comparatorShouldBe(BlockPosition pos, BlockType type) {
        int input = comparatorInput(pos, type);
        if (input >= 15) {
            return true;
        }
        if (input == 0) {
            return false;
        }
        int sides = comparatorSideMax(pos, type);
        return sides == 0 || input >= sides;
    }

    /**
     * The comparator's arithmetic (ComparatorBlock.calculateOutputSignal
     * lines 85-89): SUBTRACT takes the side signal off the input, COMPARE
     * passes the input through.
     */
    private int comparatorCalculate(BlockPosition pos, BlockType type) {
        return RedstoneBlocks.comparatorSubtract(type)
                ? Math.max(comparatorInput(pos, type) - comparatorSideMax(pos, type), 0)
                : comparatorInput(pos, type);
    }

    /**
     * The neighborChanged reaction (ComparatorBlock.checkOutputState lines
     * 157-170): when the analog value or the powered state drifts, the
     * 2-tick reaction arms (priority -1 when a diode at the output side
     * faces away, else 0 — the reference's own priority pair).
     */
    private void checkComparatorOutput(BlockPosition pos, BlockType type) {
        if (willTickThisTick(pos, FAMILY_COMPARATOR)) {
            return;
        }
        int computed = comparatorCalculate(pos, type);
        int stored = comparatorOutputs.getOrDefault(pos, 0);
        if (computed != stored
                || RedstoneBlocks.comparatorPowered(type) != comparatorShouldBe(pos, type)) {
            scheduleTick(pos, FAMILY_COMPARATOR, COMPARATOR_DELAY,
                    diodeFacesAway(pos, RedstoneBlocks.comparatorFacing(type)) ? -1 : 0);
        }
    }

    /**
     * The comparator's scheduled tick (ComparatorBlock.tick lines 196-202 +
     * updateOutputState lines 172-193). The reference's 150-block
     * normalization arm is a no-op in the flattened model: our powered pair
     * IS the normalized 149-with-POWERED-true state (the live reference
     * comparator never holds the 150 block id past its own tick), and the
     * stored value survives the internal swap, so the arm would rewrite the
     * state it already holds.
     */
    private void comparatorTick(BlockPosition pos, BlockType type) {
        updateComparatorOutput(pos, type);
    }

    /**
     * The output-state commit (ComparatorBlock.updateOutputState lines
     * 172-193): store the computed analog value, then — when the value
     * changed or the mode is COMPARE — reconcile the POWERED pair and ring
     * the output side. In SUBTRACT mode an unchanged value rings nothing
     * (the reference's quiet-subtract quirk: {@code j != i || COMPARE}).
     */
    private void updateComparatorOutput(BlockPosition pos, BlockType type) {
        int computed = comparatorCalculate(pos, type);
        int stored = comparatorOutputs.getOrDefault(pos, 0);
        comparatorOutputs.put(pos, computed);
        if (stored != computed || !RedstoneBlocks.comparatorSubtract(type)) {
            boolean shouldBe = comparatorShouldBe(pos, type);
            boolean powered = RedstoneBlocks.comparatorPowered(type);
            if (powered && !shouldBe) {
                world.setBlock(pos, RedstoneBlocks.comparatorOf(
                        RedstoneBlocks.comparatorFacing(type), RedstoneBlocks.comparatorSubtract(type), false));
            } else if (!powered && shouldBe) {
                world.setBlock(pos, RedstoneBlocks.comparatorOf(
                        RedstoneBlocks.comparatorFacing(type), RedstoneBlocks.comparatorSubtract(type), true));
            }
            notifyComparatorOutput(pos, type);
        }
    }

    /**
     * The comparator's use (ComparatorBlock.use lines 142-154): the mode
     * cycle COMPARE <-> SUBTRACT, then the IMMEDIATE output re-evaluation
     * (no scheduled delay — the reference calls updateOutputState inline).
     * Returns whether the use consumed the click. Tick-thread context.
     */
    public boolean useComparator(BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        if (!RedstoneBlocks.isComparator(type)) {
            return false;
        }
        boolean subtract = !RedstoneBlocks.comparatorSubtract(type);
        BlockType next = RedstoneBlocks.comparatorOf(
                RedstoneBlocks.comparatorFacing(type), subtract, RedstoneBlocks.comparatorPowered(type));
        world.setBlock(pos, next);
        updateComparatorOutput(pos, next);
        return true;
    }

    /**
     * The container-content wake (World.updateNeighborComparators lines
     * 2701-2716, the BlockEntity.markDirty fan-out): every comparator
     * reading this position — directly beside it, or through one solid
     * block — re-runs its neighborChanged (arming the 2-tick reaction).
     * Called by the container systems when their contents mutate.
     */
    public void wakeComparators(BlockPosition changed) {
        for (int facing : HORIZONTALS) {
            int[] step = horizontalStep(facing);
            BlockPosition neighbor = changed.offset(step[0], 0, step[1]);
            BlockType neighborType = world.getBlock(neighbor);
            if (RedstoneBlocks.isComparator(neighborType)) {
                neighborChanged(neighbor);
            } else if (WorldSolidity.isSolid(neighborType)) {
                BlockPosition twoOut = neighbor.offset(step[0], 0, step[1]);
                if (RedstoneBlocks.isComparator(world.getBlock(twoOut))) {
                    neighborChanged(twoOut);
                }
            }
        }
    }

    /**
     * The container fullness read (InventoryMenu.getAnalogSignal lines
     * 541-558): the per-slot fraction over min(inventoryMax, itemMax),
     * averaged over the WHOLE inventory, {@code floor(f * 14) + 1} when any
     * slot holds anything. The engine's empty stack is the reference's
     * null slot.
     */
    public static int containerFullness(net.zaminmc.torch.item.ItemStack[] slots, int inventoryMaxStack) {
        int nonEmpty = 0;
        float fill = 0.0f;
        for (net.zaminmc.torch.item.ItemStack stack : slots) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            fill += (float) stack.count()
                    / Math.min(inventoryMaxStack, stack.type().maxStackSize());
            nonEmpty++;
        }
        fill /= slots.length;
        return (int) Math.floor(fill * 14.0f) + (nonEmpty > 0 ? 1 : 0);
    }

    // ------------------------------------------------------------------
    // The lever (LeverBlock) + the buttons (ButtonBlock) + the plates
    // ------------------------------------------------------------------

    /**
     * The lever's use (LeverBlock.use lines 165-170): the POWERED pair
     * swaps, the click (0.6 powered / 0.5 released), then the two neighbor
     * rings — the lever's own six + the attachment block's six
     * (pos.offset(attachment.opposite)). Returns whether the use consumed
     * the click. Tick-thread context.
     */
    public boolean useLever(BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        if (!RedstoneBlocks.isLever(type)) {
            return false;
        }
        boolean powered = !RedstoneBlocks.leverPowered(type);
        world.setBlock(pos, RedstoneBlocks.leverOf(RedstoneBlocks.leverFacing(type), powered));
        updateNeighbors(pos);
        int[] away = attachmentOppositeOffset(RedstoneBlocks.leverAttachment(type));
        updateNeighbors(pos.offset(away[0], away[1], away[2]));
        return true;
    }

    /**
     * The button's use (ButtonBlock.use lines 137-150): a powered button
     * ignores the press; otherwise the press powers it, notifies the
     * mounting side, and schedules the release (20 stone / 30 wood).
     * Returns whether the use consumed the click. Tick-thread context.
     */
    public boolean useButton(BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        if (!RedstoneBlocks.isButton(type)) {
            return false;
        }
        if (RedstoneBlocks.buttonPowered(type)) {
            return true; // the reference's early return
        }
        world.setBlock(pos, RedstoneBlocks.buttonOf(
                RedstoneBlocks.buttonFacing(type), true, RedstoneBlocks.buttonWooden(type)));
        notifyButtonNeighbors(pos, RedstoneBlocks.buttonFacing(type));
        scheduleTick(pos, FAMILY_BUTTON,
                RedstoneBlocks.buttonWooden(type) ? WOODEN_BUTTON_TICKS : STONE_BUTTON_TICKS, 0);
        return true;
    }

    /** The button's scheduled release (ButtonBlock.tick lines 184-196). */
    private void buttonTick(BlockPosition pos, BlockType type) {
        if (!RedstoneBlocks.buttonPowered(type)) {
            return;
        }
        world.setBlock(pos, RedstoneBlocks.buttonOf(
                RedstoneBlocks.buttonFacing(type), false, RedstoneBlocks.buttonWooden(type)));
        notifyButtonNeighbors(pos, RedstoneBlocks.buttonFacing(type));
    }

    /**
     * The button's neighbor walk (ButtonBlock.use's updateNeighbors):
     * the MOUNT's six neighbors hear the press — the mount sits at the
     * facing's opposite (the button's FACING points away from its wall).
     */
    private void notifyButtonNeighbors(BlockPosition pos, int facing) {
        int[] away = OFFSETS[facing];
        updateNeighbors(pos.offset(-away[0], -away[1], -away[2]));
    }

    /**
     * The plate's scheduled re-compute (AbstractPressurePlateBlock.tick
     * lines 108-116): the occupancy re-scan — still occupied re-arms, empty
     * releases.
     */
    private void plateTick(BlockPosition pos, BlockType type) {
        if (computePlateOutput(pos) > 0) {
            scheduleTick(pos, FAMILY_PLATE, PLATE_TICK_RATE, 0); // re-arm
        } else if (RedstoneBlocks.platePowered(type)) {
            world.setBlock(pos, RedstoneBlocks.plateOf(false, RedstoneBlocks.plateWooden(type)));
            updateNeighbors(pos);
            updateNeighbors(pos.offset(0, -1, 0));
        }
    }

    /**
     * The plate's occupancy scan (PressurePlateBlock.calculateOutputSignal
     * lines 40-62: the 0.125-inset box, 0.25 tall — MOBS for stone,
     * EVERYTHING for wood; spectator players never trigger).
     */
    private int computePlateOutput(BlockPosition pos) {
        java.util.function.BiPredicate<BlockPosition, Boolean> probe = plateProbe;
        if (probe == null) {
            return 0;
        }
        return probe.test(pos, RedstoneBlocks.plateWooden(world.getBlock(pos))) ? 15 : 0;
    }

    /** Whether the lever still hangs on its attachment (canSurvive's test). */
    private boolean leverSupported(BlockType type, BlockPosition pos) {
        int[] away = attachmentOppositeOffset(RedstoneBlocks.leverAttachment(type));
        return WorldSolidity.isSolid(world.getBlock(pos.offset(away[0], away[1], away[2])));
    }

    /** Whether the button still hangs on its mount (canSurvive: the block at FACING.opposite). */
    private boolean buttonSupported(BlockType type, BlockPosition pos) {
        int[] away = OFFSETS[RedstoneBlocks.buttonFacing(type)];
        return WorldSolidity.isSolid(world.getBlock(pos.offset(-away[0], -away[1], -away[2])));
    }

    /** The offset of a direction's opposite (the reference's getOpposite). */
    private static int[] attachmentOppositeOffset(int direction) {
        return new int[]{-OFFSETS[direction][0], -OFFSETS[direction][1], -OFFSETS[direction][2]};
    }

    // ------------------------------------------------------------------
    // The scheduled-tick queue internals
    // ------------------------------------------------------------------

    /** Vanilla's torch reaction latency (RedstoneTorchBlock.getTickRate). */
    static final int TORCH_TICK_RATE = 2;
    /** The comparator's reaction latency (ComparatorBlock.getDelay). */
    static final int COMPARATOR_DELAY = 2;
    /** The burnout recovery (RedstoneTorchBlock line 116: scheduleTick 160). */
    static final int BURNOUT_RECOVERY = 160;
    /** The burnout window and threshold (lines 20-42 + 92-94). */
    static final long BURNOUT_WINDOW = 60;
    static final int BURNOUT_THRESHOLD = 8;

    /** Schedules a family tick at a delay (the ServerWorld.scheduleTick coalescing). */
    void scheduleTick(BlockPosition pos, int family, int delay, int priority) {
        TickKey key = new TickKey(pos, family);
        if (pendingTicks.containsKey(key)) {
            return; // the (pos, family) coalescing — the first schedule wins
        }
        Tick entry = new Tick(pos, family, world.totalTicks() + Math.max(1, delay), priority, nextSequence++);
        pendingTicks.put(key, entry);
        ticksInOrder.add(entry);
    }

    /** Whether the family's tick runs this tick (the willTickThisTick guard). */
    boolean willTickThisTick(BlockPosition pos, int family) {
        for (Tick entry : ticksThisTick) {
            if (entry.family() == family && entry.position().equals(pos)) {
                return true;
            }
        }
        return pendingTicks.containsKey(new TickKey(pos, family));
    }

    // ------------------------------------------------------------------
    // The burnout ledger (RedstoneTorchBlock lines 17-42, 88-95)
    // ------------------------------------------------------------------

    private void pruneToggles(long now) {
        for (List<Long> times : recentToggles.values()) {
            times.removeIf(time -> now - time > BURNOUT_WINDOW);
        }
    }

    private boolean shouldBurnOut(BlockPosition pos, boolean logToggle) {
        List<Long> times = recentToggles.computeIfAbsent(pos, key -> new ArrayList<>());
        if (logToggle) {
            times.add(world.totalTicks());
        }
        return times.size() >= BURNOUT_THRESHOLD;
    }

    // ------------------------------------------------------------------
    // Support and drops
    // ------------------------------------------------------------------

    /** The wire's floor rule (RedstoneWireBlock.canBePlaced lines 92-94). */
    private boolean hasSupportBelow(BlockPosition pos) {
        return WorldSolidity.isSolid(world.getBlock(pos.offset(0, -1, 0)));
    }

    /** The removal + drop (the reference's dropItems + removeBlock). */
    private void popBlock(BlockPosition pos, net.zaminmc.torch.item.ItemType drop) {
        world.setBlock(pos, world.airType());
        itemEntities.spawnDropAtBlock(
                new Position(pos.x(), pos.y(), pos.z()),
                ItemStack.of(drop, 1),
                ItemEntity.PICKUP_DELAY_DROP_TICKS);
    }

    // ------------------------------------------------------------------
    // Geometry helpers
    // ------------------------------------------------------------------

    /** The horizontal facing code of a direction id (or -1 for vertical). */
    private static int horizontalOfDirection(int dir) {
        return switch (dir) {
            case NORTH -> RedstoneBlocks.FACING_NORTH;
            case SOUTH -> RedstoneBlocks.FACING_SOUTH;
            case WEST -> RedstoneBlocks.FACING_WEST;
            case EAST -> RedstoneBlocks.FACING_EAST;
            default -> -1;
        };
    }

    /** The [dx, dz] step of a horizontal facing code. */
    private static int[] horizontalStep(int facing) {
        return RedstoneBlocks.facingOffset(facing);
    }

    /**
     * The torch's attachment offset (FACING.opposite — the torch points AWAY
     * from its wall, so the block it hangs on sits opposite its FACING; a
     * standing torch's FACING is UP and its floor sits below).
     */
    private static int[] torchAttachmentStep(int facing) {
        return switch (facing) {
            case UP -> new int[]{0, -1, 0};    // standing: the floor below
            case NORTH -> new int[]{0, 0, 1};  // points north: hangs on the south wall
            case SOUTH -> new int[]{0, 0, -1}; // points south: hangs on the north wall
            case WEST -> new int[]{1, 0, 0};   // points west: hangs on the east wall
            case EAST -> new int[]{-1, 0, 0};  // points east: hangs on the west wall
            default -> new int[]{0, -1, 0};
        };
    }

    /** The direction id of a unit offset (the inverse of OFFSETS). */
    private static int directionOf(int dx, int dy, int dz) {
        for (int i = 0; i < 6; i++) {
            if (OFFSETS[i][0] == dx && OFFSETS[i][1] == dy && OFFSETS[i][2] == dz) {
                return i;
            }
        }
        return -1;
    }

    private static int familyOf(BlockType type) {
        if (RedstoneBlocks.isWire(type)) {
            return FAMILY_WIRE;
        }
        if (RedstoneBlocks.isTorch(type)) {
            return FAMILY_TORCH;
        }
        if (RedstoneBlocks.isRepeater(type)) {
            return FAMILY_REPEATER;
        }
        if (RedstoneBlocks.isComparator(type)) {
            return FAMILY_COMPARATOR;
        }
        if (RedstoneBlocks.isButton(type)) {
            return FAMILY_BUTTON;
        }
        if (RedstoneBlocks.isPlate(type)) {
            return FAMILY_PLATE;
        }
        return -1;
    }
}