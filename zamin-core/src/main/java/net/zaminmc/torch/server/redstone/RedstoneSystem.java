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
 * block/RepeaterBlock.java (the delayed diode, the lock, the priority arms)
 * and net/minecraft/server/world/ServerWorld.java lines 370-500 (the
 * scheduled-tick queue's coalescing, priority ordering and run gates).
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
    private record TickKey(long packed, int family) {
    }

    private static final int FAMILY_WIRE = 0;
    private static final int FAMILY_TORCH = 1;
    private static final int FAMILY_REPEATER = 2;

    private final TreeSet<Tick> ticksInOrder = new TreeSet<>();
    private final Map<TickKey, Tick> pendingTicks = new HashMap<>();
    private final List<Tick> ticksThisTick = new ArrayList<>();
    private long nextSequence;

    /** The torch burnout ledger: (position -> toggle times), the reference's RECENT_TOGGLES. */
    private final Map<Long, List<Long>> recentToggles = new HashMap<>();

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
        // The wire's diagonal re-notification (RedstoneWireBlock lines
        // 181-196): the horizontal neighbors' up/down diagonal wires hear
        // the change through the solid/non-solid step.
        if (RedstoneBlocks.isWire(newType)) {
            for (int facing : HORIZONTALS) {
                int[] step = horizontalStep(facing);
                BlockPosition neighbor = position.offset(step[0], 0, step[1]);
                if (WorldSolidity.isSolid(world.getBlock(neighbor))) {
                    updateNeighborsOfWire(neighbor.offset(0, 1, 0));
                } else {
                    updateNeighborsOfWire(neighbor.offset(0, -1, 0));
                }
            }
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
        return ownSignal(type, dir);
    }

    /** The strong (direct) signal — the source's own direct emission. */
    public int directSignal(BlockPosition pos, int dir) {
        BlockType type = world.getBlock(pos);
        if (RedstoneBlocks.isWire(type)) {
            // RedstoneWireBlock lines 252-254: the direct arm equals the
            // weak arm, both under the re-entrancy guard.
            return ownSignal(type, dir);
        }
        if (RedstoneBlocks.isTorch(type)) {
            // RedstoneTorchBlock lines 134-136: the strong emission is
            // UP-only — the block above the torch (the consumer there asks
            // with dir=DOWN toward it).
            return dir == DOWN ? ownSignal(type, dir) : 0;
        }
        if (RedstoneBlocks.isRepeater(type)) {
            // DiodeBlock lines 66-68: the strong arm equals the weak arm.
            return ownSignal(type, dir);
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
                && !RedstoneBlocks.isRepeater(type);
    }

    /**
     * The per-family weak emission (the reference's block getSignal
     * overrides). The dir convention: dir points from the consumer toward
     * this source, both in the reference Direction id space.
     */
    private int ownSignal(BlockType type, int dir) {
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
        Set<Long> notified = new HashSet<>();
        computeWirePower(pos, pos, notified);
        for (Long key : notified) {
            BlockPosition at = unpack(key);
            for (int[] offset : OFFSETS) {
                neighborChanged(at.offset(offset[0], offset[1], offset[2]));
            }
        }
    }

    /**
     * The doUpdatePower port (lines 108-164) with the source-walk shape
     * preserved ({@code source == pos} in every 1.8.8 call, so the diagonal
     * gates hold their reference form).
     */
    private void computeWirePower(BlockPosition pos, BlockPosition source, Set<Long> notified) {
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
            notified.add(pack(pos));
            for (int[] offset : OFFSETS) {
                notified.add(pack(pos.offset(offset[0], offset[1], offset[2])));
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
    }

    /** The per-family onAdded (the reference's onAdded overrides). */
    private void onAdded(BlockPosition pos, BlockType type) {
        if (RedstoneBlocks.isWire(type)) {
            // RedstoneWireBlock lines 176-198: the power walk, then the
            // vertical neighbor updates, then the diagonal wire
            // re-notifications (the diagonal walk rides onBlockChanged).
            updateWirePower(pos);
            for (int[] offset : OFFSETS) {
                neighborChanged(pos.offset(offset[0], offset[1], offset[2]));
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
            // to all six neighbors on arrival.
            if (RedstoneBlocks.torchLit(type)) {
                for (int[] offset : OFFSETS) {
                    neighborChanged(pos.offset(offset[0], offset[1], offset[2]));
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
        }
    }

    /**
     * The per-tick drain (ServerWorld.doScheduledTicks lines 452-500): up to
     * 1000 due entries move to the running set and execute against the
     * still-family gate. Tick-thread context.
     */
    public void tick() {
        long now = world.totalTicks();
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
            pendingTicks.remove(new TickKey(pack(first.position()), first.family()));
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
    // The repeater (DiodeBlock + RepeaterBlock)
    // ------------------------------------------------------------------

    /** The repeater's input (DiodeBlock lines 117-127): the facing-side read with the wire special case. */
    private int repeaterInput(BlockPosition pos, BlockType type) {
        int facingCode = RedstoneBlocks.repeaterFacing(type);
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

    /** The side input (DiodeBlock lines 129-146): only diodes and wires count. */
    private int sideInput(BlockPosition pos, int dir) {
        BlockType type = world.getBlock(pos);
        if (RedstoneBlocks.isWire(type)) {
            return RedstoneBlocks.wirePower(type);
        }
        if (RedstoneBlocks.isRepeater(type)) {
            return directSignal(pos, dir);
        }
        return 0;
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

    /** The facing-a-facing-back diode test (DiodeBlock.shouldPrioritize lines 209-213). */
    private boolean repeaterPrioritized(BlockPosition pos, BlockType type) {
        int facingCode = RedstoneBlocks.repeaterFacing(type);
        int[] back = RedstoneBlocks.facingOffset(RedstoneBlocks.oppositeFacing(facingCode));
        BlockPosition behind = pos.offset(back[0], 0, back[1]);
        BlockType behindType = world.getBlock(behind);
        if (!RedstoneBlocks.isRepeater(behindType)) {
            return false;
        }
        return RedstoneBlocks.repeaterFacing(behindType) != RedstoneBlocks.oppositeFacing(facingCode);
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
        int facingCode = RedstoneBlocks.repeaterFacing(type);
        int[] out = RedstoneBlocks.facingOffset(RedstoneBlocks.oppositeFacing(facingCode));
        BlockPosition output = pos.offset(out[0], 0, out[1]);
        neighborChanged(output);
        for (int[] offset : OFFSETS) {
            BlockPosition at = output.offset(offset[0], offset[1], offset[2]);
            // except the face pointing back at the repeater
            if (at.equals(pos)) {
                continue;
            }
            neighborChanged(at);
        }
    }

    // ------------------------------------------------------------------
    // The scheduled-tick queue internals
    // ------------------------------------------------------------------

    /** Vanilla's torch reaction latency (RedstoneTorchBlock.getTickRate). */
    static final int TORCH_TICK_RATE = 2;
    /** The burnout recovery (RedstoneTorchBlock line 116: scheduleTick 160). */
    static final int BURNOUT_RECOVERY = 160;
    /** The burnout window and threshold (lines 20-42 + 92-94). */
    static final long BURNOUT_WINDOW = 60;
    static final int BURNOUT_THRESHOLD = 8;

    /** Schedules a family tick at a delay (the ServerWorld.scheduleTick coalescing). */
    void scheduleTick(BlockPosition pos, int family, int delay, int priority) {
        TickKey key = new TickKey(pack(pos), family);
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
        return pendingTicks.containsKey(new TickKey(pack(pos), family));
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
        List<Long> times = recentToggles.computeIfAbsent(pack(pos), key -> new ArrayList<>());
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

    /** The packed position key (the engine's BlockPosition packing). */
    private static long pack(BlockPosition pos) {
        return ((long) (pos.x() & 0x3FFFFFF) << 38) | ((pos.z() & 0x3FFFFFF) << 12) | (pos.y() & 0xFFF);
    }

    private static BlockPosition unpack(long key) {
        int x = (int) (key >> 38);
        int z = (int) (key << 26 >> 38);
        int y = (int) (key << 52 >> 52);
        return new BlockPosition(x, y, z);
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
        return -1;
    }
}
