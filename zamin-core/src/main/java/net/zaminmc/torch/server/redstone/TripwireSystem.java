package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.WorldChangeListener;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.util.Position;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The tripwire system (Slice 10c — the reference port of
 * TripwireBlock.java + TripwireHookBlock.java): the wire's occupancy scan
 * (the onEntityCollision equivalent over the engine's per-tick probe, the
 * 10-tick re-scan while occupied), the hook's updateState walk (the
 * 41-block chain scan, the attachment computation, the powered
 * aggregation over the chain, the ATTACHED propagation, the far hook's
 * state, the click sounds), the neighbor arms (the wire's suspended check,
 * the hook's mount check), the shears disarmed arm, and the wire's
 * onRemoved powered quirk (a removed powered wire counts as on for the
 * hook update — the classic trap signal).
 *
 * <p>The occupancy probe is wired by the engine (the players+mobs+items
 * box test over the wire's slim shape). The hook's weak signal (15 when
 * powered) and its strong FACING-only arm live in {@link RedstoneSystem}'s
 * dispatch; this system owns the state transitions.</p>
 */
public final class TripwireSystem implements WorldChangeListener {

    /** Vanilla's re-scan rate (TripwireBlock.getTickRate). */
    static final int TRIPWIRE_TICK_RATE = 10;
    /** The chain scan limit (the reference's 42-cell walk). */
    private static final int CHAIN_LIMIT = 42;

    private static final int[][] OFFSETS = {
            {0, -1, 0}, // 0 DOWN
            {0, 1, 0},  // 1 UP
            {0, 0, -1}, // 2 NORTH
            {0, 0, 1},  // 3 SOUTH
            {-1, 0, 0}, // 4 WEST
            {1, 0, 0},  // 5 EAST
    };

    private final EngineWorld world;
    private final RedstoneSystem redstone;
    private final FxManager fx;
    /** The occupancy probe: whether any body stands in the wire's box. */
    private Predicate<BlockPosition> occupancyProbe;

    private final Set<BlockPosition> wirePositions = new HashSet<>();
    /**
     * The re-entrancy guard (the reference's flag-3 write: the hook's own
     * chain writes must not re-enter the hook walk — the flattened-state
     * engine re-fires the commit listeners on every setBlock, and without
     * the guard the two hooks ping-pong their ATTACHED pairs forever).
     */
    private boolean updating;

    public TripwireSystem(EngineWorld world, RedstoneSystem redstone, FxManager fx) {
        this.world = world;
        this.redstone = redstone;
        this.fx = fx;
    }

    /** Wires the occupancy probe (the engine's entity box test). */
    public void setOccupancyProbe(Predicate<BlockPosition> probe) {
        this.occupancyProbe = probe;
    }

    // ------------------------------------------------------------------
    // The placement + removal tracking
    // ------------------------------------------------------------------

    @Override
    public void onBlockChanged(EngineWorld changed, BlockPosition position, BlockType now) {
        if (updating) {
            return; // the hook walk's own writes never re-enter the walk
        }
        if (TripwireBlocks.isTripwire(now)) {
            wirePositions.add(position);
            // TripwireBlock.onAdded lines 116-121: the suspended bit from
            // the floor test, then the hook update.
            boolean suspended = !WorldSolidity.isSolid(world.getBlock(position.offset(0, -1, 0)));
            BlockType state = TripwireBlocks.wireOf(
                    TripwireBlocks.wirePowered(now), suspended,
                    TripwireBlocks.wireAttached(now), TripwireBlocks.wireDisarmed(now));
            if (!state.equals(now)) {
                world.setBlock(position, state);
            }
            updateTripwireHooks(position, state);
        } else if (TripwireBlocks.isHook(now)) {
            // TripwireHookBlock.onPlaced: the initial attachment walk.
            updateHookState(position, now, false, false, -1, null);
        } else {
            wirePositions.remove(position);
        }
    }

    @Override
    public void onBlockRemoved(EngineWorld changed, BlockPosition position, BlockType oldType) {
        if (updating) {
            return;
        }
        if (TripwireBlocks.isTripwire(oldType)) {
            wirePositions.remove(position);
            // TripwireBlock.onRemoved lines 125-128: the hooks hear a
            // POWERED wire leave (the trap signal quirk — a removed
            // powered wire counts as on).
            updateTripwireHooks(position, TripwireBlocks.wireOfState(oldType,
                    true, TripwireBlocks.wireSuspended(oldType),
                    TripwireBlocks.wireAttached(oldType), TripwireBlocks.wireDisarmed(oldType)));
        } else if (TripwireBlocks.isHook(oldType)) {
            // TripwireHookBlock.onRemoved lines 243-257: an attached or
            // powered hook re-runs the update as removed.
            if (TripwireBlocks.hookAttached(oldType) || TripwireBlocks.hookPowered(oldType)) {
                updateHookState(position, oldType, true, false, -1, null);
            }
        }
    }

    // ------------------------------------------------------------------
    // The occupancy cycle (the plate-scan pattern)
    // ------------------------------------------------------------------

    /**
     * The per-tick scan: every unpowered wire probes its box (the
     * onEntityCollision equivalent); a hit powers the wire, updates the
     * hooks, and schedules the 10-tick re-scan; the scheduled re-check
     * releases when the box empties.
     */
    public void tick() {
        for (BlockPosition wire : List.copyOf(new java.util.ArrayList<>(wirePositions))) {
            BlockType type = world.getBlock(wire);
            if (!TripwireBlocks.isTripwire(type)) {
                continue;
            }
            if (!TripwireBlocks.wirePowered(type)) {
                updateOutputState(wire);
            }
        }
    }

    /**
     * The occupancy re-scan (the scheduled tick — TripwireBlock.tick lines
     * 172-180 + updateOutputState lines 182-212): the probe's answer
     * flips the POWERED pair, the hooks hear it, and an occupied wire
     * re-arms.
     */
    private void updateOutputState(BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        boolean powered = TripwireBlocks.wirePowered(type);
        boolean occupied = occupancyProbe != null && occupancyProbe.test(pos);
        if (occupied != powered) {
            BlockType state = TripwireBlocks.wireOfState(type, occupied,
                    TripwireBlocks.wireSuspended(type), TripwireBlocks.wireAttached(type),
                    TripwireBlocks.wireDisarmed(type));
            world.setBlock(pos, state);
            updateTripwireHooks(pos, state);
        }
        if (occupied) {
            redstone.scheduleTick(pos, RedstoneSystem.FAMILY_TRIPWIRE, TRIPWIRE_TICK_RATE, 0);
        }
    }

    /** The scheduled tripwire tick (the re-scan). */
    public void scheduledTripwireTick(BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        if (TripwireBlocks.isTripwire(type) && TripwireBlocks.wirePowered(type)) {
            updateOutputState(pos);
        }
    }

    // ------------------------------------------------------------------
    // The hook's chain walk (TripwireHookBlock.updateState)
    // ------------------------------------------------------------------

    /**
     * The wire's hook notification (updateTripwireHooks lines 140-158):
     * the SOUTH and WEST scans — the first hook facing back wins.
     */
    private void updateTripwireHooks(BlockPosition pos, BlockType state) {
        for (int direction : new int[]{3 /* SOUTH */, 4 /* WEST */}) {
            int[] o = OFFSETS[direction];
            for (int i = 1; i < CHAIN_LIMIT; i++) {
                BlockPosition at = pos.offset(o[0] * i, 0, o[2] * i);
                BlockType block = world.getBlock(at);
                if (TripwireBlocks.isHook(block)) {
                    if (TripwireBlocks.hookFacing(block) == opposite(direction)) {
                        updateHookState(at, block, false, true, i, state);
                    }
                    break;
                }
                if (!TripwireBlocks.isTripwire(block)) {
                    break;
                }
            }
        }
    }

    /**
     * The hook's state update (TripwireHookBlock.updateState lines
     * 124-180): the 41-cell walk toward FACING — the chain's suspended
     * flags must match the hook's, any powered non-disarmed wire powers
     * the circuit, the far hook (a hook facing back at distance > 1)
     * completes the attachment, the chain's ATTACHED bits propagate, and
     * the click sounds fire on the transitions.
     */
    void updateHookState(BlockPosition pos, BlockType state, boolean removed,
                         boolean updateNeighbors, int distanceToTripwire, BlockType tripwire) {
        updating = true;
        try {
            updateHookStateInWalk(pos, state, removed, updateNeighbors, distanceToTripwire, tripwire);
        } finally {
            updating = false;
        }
    }

    private void updateHookStateInWalk(BlockPosition pos, BlockType state, boolean removed,
                                        boolean updateNeighbors, int distanceToTripwire,
                                        BlockType tripwire) {
        int facing = TripwireBlocks.hookFacing(state);
        boolean wasAttached = TripwireBlocks.hookAttached(state);
        boolean wasPowered = TripwireBlocks.hookPowered(state);
        boolean suspended = !WorldSolidity.isSolid(world.getBlock(pos.offset(0, -1, 0)));
        boolean attached = !removed;
        boolean powered = false;
        int farHook = 0;
        BlockType[] chain = new BlockType[CHAIN_LIMIT];

        int[] o = OFFSETS[facing];
        for (int j = 1; j < CHAIN_LIMIT; j++) {
            BlockPosition at = pos.offset(o[0] * j, 0, o[2] * j);
            BlockType block = world.getBlock(at);
            if (TripwireBlocks.isHook(block)) {
                if (TripwireBlocks.hookFacing(block) == opposite(facing)) {
                    farHook = j;
                }
                break;
            }
            if (!TripwireBlocks.isTripwire(block) && j != distanceToTripwire) {
                chain[j] = null;
                attached = false;
            } else {
                if (j == distanceToTripwire && tripwire != null) {
                    block = tripwire;
                }
                boolean notDisarmed = !TripwireBlocks.wireDisarmed(block);
                boolean wireOn = TripwireBlocks.wirePowered(block);
                boolean wireSuspended = TripwireBlocks.wireSuspended(block);
                attached &= wireSuspended == suspended;
                powered |= notDisarmed && wireOn;
                chain[j] = block;
                if (j == distanceToTripwire) {
                    redstone.scheduleTick(pos, RedstoneSystem.FAMILY_TRIPWIRE, TRIPWIRE_TICK_RATE, 0);
                    attached &= notDisarmed;
                }
            }
        }

        attached &= farHook > 1;
        powered &= attached;
        if (farHook > 0) {
            // The far hook's state (lines 158-163): facing back at this
            // one, with its own two-position ring (the far hook's six +
            // its mount's six — the reference's updateNeighbors(world,
            // blockpos1, direction1)).
            BlockPosition farAt = pos.offset(o[0] * farHook, 0, o[2] * farHook);
            world.setBlock(farAt, TripwireBlocks.hookOf(opposite(facing), attached, powered));
            redstone.updateNeighbors(farAt);
            redstone.updateNeighbors(farAt.offset(o[0], 0, o[2])); // the far mount's ring
            playClickSound(farAt, attached, powered, wasAttached, wasPowered);
        }

        playClickSound(pos, attached, powered, wasAttached, wasPowered);
        if (!removed) {
            world.setBlock(pos, TripwireBlocks.hookOf(facing, attached, powered));
            if (updateNeighbors) {
                redstone.updateNeighbors(pos);
                redstone.updateNeighbors(pos.offset(-o[0], 0, -o[2]));
            }
        }

        if (wasAttached != attached) {
            // The chain's ATTACHED bits follow the hook (lines 165-173).
            for (int k = 1; k < farHook; k++) {
                BlockPosition at = pos.offset(o[0] * k, 0, o[2] * k);
                BlockType link = chain[k];
                if (link != null && TripwireBlocks.isTripwire(world.getBlock(at))) {
                    world.setBlock(at, TripwireBlocks.wireOfState(link,
                            TripwireBlocks.wirePowered(link), TripwireBlocks.wireSuspended(link),
                            attached, TripwireBlocks.wireDisarmed(link)));
                }
            }
        }
    }

    /** The click family (playClickSound lines 198-211). */
    private void playClickSound(BlockPosition pos, boolean attached, boolean powered,
                                boolean wasAttached, boolean wasPowered) {
        Position center = new Position(pos.x() + 0.5, pos.y() + 0.1, pos.z() + 0.5);
        if (powered && !wasPowered) {
            fx.sound(center, "random.click", 0.4f, 0.6f);
        } else if (!powered && wasPowered) {
            fx.sound(center, "random.click", 0.4f, 0.5f);
        } else if (attached && !wasAttached) {
            fx.sound(center, "random.click", 0.4f, 0.7f);
        } else if (!attached && wasAttached) {
            fx.sound(center, "random.bowhit", 0.4f, 1.2f);
        }
    }

    // ------------------------------------------------------------------
    // The neighbor arms
    // ------------------------------------------------------------------

    /**
     * The cross-system neighbor bus arm: a wire near a notification
     * re-checks its support (neighborChanged lines 91-98 — a suspended
     * flip breaks the wire with its drop); a hook re-checks its mount
     * (lines 108-119 — the mount gone breaks the hook with its drop).
     */
    public void neighborHeard(BlockPosition position) {
        for (int[] offset : OFFSETS) {
            checkSupport(position.offset(offset[0], offset[1], offset[2]));
        }
        checkSupport(position);
    }

    /** The support re-check for one position. */
    private void checkSupport(BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        if (TripwireBlocks.isTripwire(type)) {
            boolean suspended = !WorldSolidity.isSolid(world.getBlock(pos.offset(0, -1, 0)));
            if (suspended != TripwireBlocks.wireSuspended(type)) {
                // The suspended flip breaks the wire with the string drop
                // (dropItems + removeBlock).
                if (!TripwireBlocks.wireDisarmed(type)) {
                    fx.blockShatter(new Position(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5), type);
                }
                world.setBlock(pos, world.airType());
            }
        } else if (TripwireBlocks.isHook(type)) {
            int facing = TripwireBlocks.hookFacing(type);
            int[] o = OFFSETS[facing];
            if (!WorldSolidity.isSolid(world.getBlock(pos.offset(-o[0], 0, -o[2])))) {
                world.setBlock(pos, world.airType());
            }
        }
    }

    // ------------------------------------------------------------------
    // Placement helpers
    // ------------------------------------------------------------------

    /**
     * The wire's placement facing... the wire's support gate (canBePlaced
     * through the string item's block placement): any placement works,
     * the suspended bit lands with the commit listener's walk.
     */
    public static boolean hookPlacementSupported(EngineWorld world, BlockPosition target,
                                                 int clickedFace) {
        // TripwireHookBlock.canBePlaced: a horizontal face with a solid
        // block behind it.
        if (clickedFace < 2 || clickedFace > 5) {
            return false;
        }
        int[] o = OFFSETS[clickedFace];
        return WorldSolidity.isSolid(world.getBlock(
                target.offset(-o[0], -o[1], -o[2])));
    }

    /** The opposite direction id. */
    private static int opposite(int direction) {
        return switch (direction) {
            case 0 -> 1;
            case 1 -> 0;
            case 2 -> 3;
            case 3 -> 2;
            case 4 -> 5;
            default -> 4;
        };
    }
}
