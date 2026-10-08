package net.zamin.engine.block;

import net.zamin.api.BlockPosition;
import net.zamin.api.BlockType;
import net.zamin.api.Identifier;
import net.zamin.api.ItemStack;
import net.zamin.api.Position;
import net.zamin.engine.world.EngineWorld;
import net.zamin.engine.world.WorldChangeListener;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The fluid simulation: sources pour, streams spread, columns fall and the
 * two fluids annihilate on contact. Tick-thread confined like every world
 * system — the queue owns no locks because only the simulation thread runs.
 *
 * <p>The historical rules this slice models:</p>
 * <ul>
 *   <li><b>Down takes priority</b>: a fluid with an open cell below pours
 *       straight down (the falling column) and does not spread sideways
 *       until it lands — the classic pour shape.</li>
 *   <li><b>Level decay</b>: water loses one level per block (seven blocks of
 *       reach); lava loses two (three blocks of reach) on a six-times-slower
 *       cadence.</li>
 *   <li><b>Sustenance</b>: a flowing block survives only while the same
 *       fluid sits above it, or a horizontal neighbor is one level closer to
 *       a source. Starved streams dry out one level at a time, so a removed
 *       source drains the whole stream gracefully.</li>
 *   <li><b>Contact reactions</b>: water converts the lava it touches — a
 *       source hardens into obsidian, a stream into cobblestone; lava
 *       flowing into water freezes into stone. Both hiss.</li>
 *   <li><b>Wash-out</b>: a fluid flowing into a torch cell pops the torch
 *       as an item first.</li>
 * </ul>
 *
 * <p>Fluids schedule through the world change listener (every committed
 * change wakes the cell and its neighbors), so bucket pours, mined walls,
 * explosions and the fluid's own spread all arrive through one path. An
 * update that changes nothing schedules nothing — the simulation goes quiet
 * when the world reaches its steady state. Behavior ported in shape from
 * TogAr2/MinestomFluids (MIT) — see COMMUNITY_REFERENCES.md; the
 * implementation is the engine's own.</p>
 */
public final class FluidSystem implements WorldChangeListener {

    /** The world a fluid update reads and commits through (tick thread). */
    public interface World {
        BlockType getBlock(BlockPosition position);

        void setBlock(BlockPosition position, BlockType type);

        BlockType airType();

        long totalTicks();
    }

    /** Torch wash-out and fizz feedback. */
    public interface Sink {
        void popItem(Position at, ItemStack stack);

        void sound(Position at, String name, float volume, float pitch);
    }

    private record Scheduled(BlockPosition position, long dueTick) {
    }

    private static final BlockPosition[] SIDES = {
            new BlockPosition(1, 0, 0), new BlockPosition(-1, 0, 0),
            new BlockPosition(0, 0, 1), new BlockPosition(0, 0, -1)};

    private final World world;
    private final Sink sink;

    private final ArrayDeque<Scheduled> queue = new ArrayDeque<>();
    private final Set<Long> pending = new HashSet<>();

    public FluidSystem(World world, Sink sink) {
        this.world = Objects.requireNonNull(world, "world");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /**
     * A block changed: wake the cell and its neighbors. Runs on the
     * simulation thread through the world-listener path. Out-of-world cells
     * are skipped before offsetting (the record constructor validates y).
     */
    @Override
    public void onBlockChanged(EngineWorld changedWorld, BlockPosition position, BlockType newType) {
        long now = world.totalTicks();
        schedule(position, now);
        wake(position.offset(1, 0, 0), now);
        wake(position.offset(-1, 0, 0), now);
        if (position.y() < BlockPosition.MAX_Y) {
            wake(position.offset(0, 1, 0), now);
        }
        if (position.y() > BlockPosition.MIN_Y) {
            wake(position.offset(0, -1, 0), now);
        }
        wake(position.offset(0, 0, 1), now);
        wake(position.offset(0, 0, -1), now);
    }

    /** Schedules one update (one pending update per position; earliest wins). */
    private void schedule(BlockPosition position, long now) {
        if (position.y() < BlockPosition.MIN_Y || position.y() > BlockPosition.MAX_Y) {
            return;
        }
        if (!pending.add(key(position))) {
            return;
        }
        queue.add(new Scheduled(position, now + occupantCadence(position)));
    }

    /** Wakes a neighbor cell unless it is outside the world. */
    private void wake(BlockPosition position, long now) {
        if (position.y() < BlockPosition.MIN_Y || position.y() > BlockPosition.MAX_Y) {
            return;
        }
        schedule(position, now);
    }

    private long occupantCadence(BlockPosition position) {
        FluidBlocks.Kind kind = FluidBlocks.kindOf(world.getBlock(position).identifier());
        return kind == null ? FluidBlocks.WATER_CADENCE_TICKS : FluidBlocks.cadenceOf(kind);
    }

    /** Runs every fluid update due this tick. Tick-thread context. */
    public void tick() {
        long now = world.totalTicks();
        // Bounded drain: only the entries present at entry may be polled this
        // pass. A not-yet-due entry re-arms to the BACK of the queue and is
        // re-checked on a later tick — an unbounded while-loop would poll the
        // same future-due entry forever (the drain never sees an empty queue),
        // freezing the tick thread at 100% CPU (the sand-placement freeze).
        int entries = queue.size();
        for (int i = 0; i < entries; i++) {
            Scheduled entry = queue.poll();
            if (entry == null) {
                break; // drained everything reachable (defensive)
            }
            pending.remove(key(entry.position()));
            if (entry.dueTick() > now) {
                if (pending.add(key(entry.position()))) {
                    queue.add(entry); // re-arm for its own due tick (earliest wins)
                }
                continue;
            }
            runUpdate(entry.position());
        }
    }

    /** The rule set for one position. Tick-thread context. */
    private void runUpdate(BlockPosition position) {
        BlockType type = world.getBlock(position);
        Identifier identifier = type.identifier();
        FluidBlocks.Kind kind = FluidBlocks.kindOf(identifier);
        if (kind == null) {
            return; // the cell changed since scheduling, or holds a plain block
        }

        // Contact reactions come first: water beside lava hardens it instead
        // of pouring past it. Either fluid may trigger the conversion.
        if (reactWithNeighbors(position, kind)) {
            return;
        }

        if (FluidBlocks.isSource(identifier)) {
            spread(position, kind, 0);
            return;
        }

        int level = FluidBlocks.levelOf(identifier);

        // Sustain: the same fluid above, or a neighbor one level closer to
        // its source. Starved streams dry one step at a time.
        boolean fedFromAbove = position.y() < BlockPosition.MAX_Y
                && FluidBlocks.kindOf(world.getBlock(position.offset(0, 1, 0)).identifier()) == kind;
        int bestNeighbor = Integer.MAX_VALUE;
        for (BlockPosition side : SIDES) {
            Identifier neighborId = world.getBlock(offset(position, side)).identifier();
            if (FluidBlocks.kindOf(neighborId) != kind) {
                continue;
            }
            if (FluidBlocks.isSource(neighborId)) {
                bestNeighbor = 0;
                break;
            }
            int neighborLevel = FluidBlocks.levelOf(neighborId);
            if (neighborLevel >= 1 && neighborLevel < bestNeighbor) {
                bestNeighbor = neighborLevel;
            }
        }
        int decay = FluidBlocks.decayOf(kind);
        if (!fedFromAbove && bestNeighbor >= level) {
            if (level >= 8) {
                world.setBlock(position, world.airType()); // the stream above cut off
            } else if (level + decay > FluidBlocks.MAX_FLOW_LEVEL) {
                world.setBlock(position, world.airType());
            } else {
                world.setBlock(position, FluidBlocks.typeOf(kind, level + decay));
            }
            return;
        }

        int spreadLevel = level >= 8 ? 0 : level; // a landing column spreads fresh
        spread(position, kind, spreadLevel);
    }

    /**
     * Spreads one fluid step from a cell at {@code level} distance from its
     * source (0 at sources and landing columns). Down takes priority: an
     * open cell below collects the whole flow; sideways spreading happens
     * only against ground.
     */
    private void spread(BlockPosition position, FluidBlocks.Kind kind, int level) {
        if (position.y() > BlockPosition.MIN_Y) {
            BlockPosition below = position.offset(0, -1, 0);
            if (handleTarget(below, kind, 8)) {
                return; // poured down (or reacted) — the whole flow went there
            }
        }
        int decay = FluidBlocks.decayOf(kind);
        int next = level + decay;
        if (next > FluidBlocks.MAX_FLOW_LEVEL) {
            return; // the reach ends here
        }
        for (BlockPosition side : SIDES) {
            handleTarget(offset(position, side), kind, next);
        }
    }

    /**
     * One spread target: contact reaction first, wash-out then flow.
     * Returns whether the target consumed the flow (a reaction or a down
     * pour) so the caller stops spreading.
     */
    private boolean handleTarget(BlockPosition target, FluidBlocks.Kind kind, int level) {
        Identifier existing = world.getBlock(target).identifier();
        FluidBlocks.Kind otherKind = FluidBlocks.kindOf(existing);
        if (otherKind != null && otherKind != kind) {
            // The lava side converts; a water target simply blocks a lava flow.
            convertLavaOnContact(target);
            return true;
        }
        if (!existing.equals(world.airType())
                && !existing.equals(BuiltinBlocks.TORCH.identifier())) {
            return false; // solid ground or the same fluid already there
        }
        flowInto(target, kind, level);
        return true;
    }

    /**
     * The contact reaction: <b>the lava side always converts</b> — a lava
     * source touched by water hardens into obsidian, a flowing stream into
     * cobblestone, whichever fluid triggered the check (the historical
     * 1.8 outcomes; plain stone from fluids is not a 1.8 mechanic).
     * Returns whether a conversion committed.
     */
    private boolean convertLavaOnContact(BlockPosition lavaCell) {
        Identifier id = world.getBlock(lavaCell).identifier();
        if (!FluidBlocks.isLava(id)) {
            return false;
        }
        world.setBlock(lavaCell, FluidBlocks.isSource(id)
                ? BuiltinBlocks.OBSIDIAN : BuiltinBlocks.COBBLESTONE);
        fizz(lavaCell);
        return true;
    }

    /**
     * Scans the cell's below-and-side neighbors for lava and converts it.
     * Returns whether any reaction fired (the update stands down this tick;
     * the next wake continues).
     */
    private boolean reactWithNeighbors(BlockPosition position, FluidBlocks.Kind kind) {
        boolean reacted = false;
        if (position.y() > BlockPosition.MIN_Y) {
            BlockPosition below = position.offset(0, -1, 0);
            FluidBlocks.Kind belowKind = FluidBlocks.kindOf(world.getBlock(below).identifier());
            if (belowKind != null && belowKind != kind) {
                reacted |= convertLavaOnContact(below);
            }
        }
        for (BlockPosition side : SIDES) {
            BlockPosition target = offset(position, side);
            FluidBlocks.Kind otherKind = FluidBlocks.kindOf(world.getBlock(target).identifier());
            if (otherKind != null && otherKind != kind) {
                reacted |= convertLavaOnContact(target);
            }
        }
        return reacted;
    }

    /** @return {@code base} shifted by the side's components (no offset overload). */
    private static BlockPosition offset(BlockPosition base, BlockPosition side) {
        return new BlockPosition(base.x() + side.x(), base.y() + side.y(), base.z() + side.z());
    }

    /** Commits one flow step into a cell; torches pop first. */
    private void flowInto(BlockPosition target, FluidBlocks.Kind kind, int level) {
        if (world.getBlock(target).identifier().equals(BuiltinBlocks.TORCH.identifier())) {
            sink.popItem(new Position(target.x(), target.y(), target.z()),
                    ItemStack.of(net.zamin.engine.item.BuiltinItems.TORCH, 1));
        }
        world.setBlock(target, FluidBlocks.typeOf(kind, level));
    }

    private void fizz(BlockPosition at) {
        sink.sound(new Position(at.x(), at.y(), at.z()), "random.fizz", 0.5f, 1.0f);
    }

    /** Packs a position into one dedup key (the 1.8 coordinate space). */
    private static long key(BlockPosition position) {
        return ((long) (position.x() & 0x3FFFFFF) << 38)
                | ((long) (position.z() & 0x3FFFFFF) << 12)
                | (position.y() & 0xFFF);
    }
}
