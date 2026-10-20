package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * The scheduled block-tick queue — the 1:1 port of the reference's
 * ServerWorld machinery (reference/1.8.8 net/minecraft/server/world/
 * ServerWorld.java, the tickUpdates/scheduleUpdate family of the MCP
 * naming): the pending TileTicks surface every 1.8.8 world carries.
 *
 * <p><b>The three structures</b> (ServerWorld lines 86-87 + 110, 1:1):
 * the all-entries set (the dedupe index), the ordered tree (the drain
 * order — time, then priority, then insertion id, per
 * {@link ScheduledTickEntry#compareTo}), and the this-tick list (the
 * entries being fired this pass, so {@link #willTickThisTick} can
 * answer during the walk).</p>
 *
 * <p><b>The reference's own quirks, preserved:</b></p>
 * <ul>
 *   <li>Scheduling an AIR-family block (the engine's
 *       {@code minecraft:air}) leaves the entry at time 0 and — for
 *       {@link #scheduleTick} — priority 0, exactly the reference's
 *       {@code getMaterial() != Material.AIR} gate around
 *       setTime/setPriority (ServerWorld lines 412-416): an air row
 *       pops on the very next pass and drops at the dispatch gate.</li>
 *   <li>{@link #loadScheduledTick} (the anvil/import arm,
 *       ServerWorld lines 426-437) sets the priority ALWAYS and the
 *       time only for non-air — the same split.</li>
 *   <li>A second schedule while an entry is pending changes nothing
 *       (the {@code !contains} guard — the earliest entry wins; there
 *       is no merge).</li>
 *   <li>{@link #scheduleTick} refuses positions whose chunk is not
 *       loaded (the reference's {@code isAreaLoaded} at radius 0 — the
 *       i = 0 quirk); {@link #loadScheduledTick} loads regardless (the
 *       reference's import path has no loaded gate).</li>
 *   <li>An entry whose area went away between schedule and fire
 *       re-arms at delay 0 (ServerWorld line 505) — due immediately on
 *       the next pass once the chunk is back.</li>
 *   <li>The per-pass cap is 1000 entries (ServerWorld lines 467-469)
 *       and the pass stops at the first future-due entry (line 475,
 *       {@code time > world time} — the horizon).</li>
 *   <li>The dispatch gate (lines 492-496): the current block must be
 *       non-air and the SAME family the entry named — a replaced block
 *       drops its pending tick (no re-arm, no fire).</li>
 * </ul>
 *
 * <p><b>The dispatch seam:</b> the reference calls
 * {@code block.tick(world, pos, state, random)}. The engine dispatches
 * through {@link BlockTicker} registrations — a family with no
 * registered ticker observes the reference's default
 * {@code Block.tick} body (the no-op): the entry pops, nothing fires.
 * The family-by-family migration of the engine's existing delayed arms
 * onto this surface is demand-named (the ledger's follow-up rows), not
 * a silent rewrite of tested systems.</p>
 *
 * <p><b>Threading (§6):</b> every mutation runs on the world's
 * authoritative context — the system asserts it through
 * {@link EngineWorld#requireMutationContext(String)} like the
 * block-update system's own views. The queries copy the tick-confined
 * structures.</p>
 */
public final class ScheduledTickSystem {

    private static final Logger LOGGER = Logger.getLogger(ScheduledTickSystem.class.getName());

    /** The per-pass drain cap (ServerWorld lines 467-469: i > 1000 → 1000). */
    static final int PASS_CAP = 1000;

    /** The reference's doTicksImmediately (ServerWorld's worldgen arm) — never armed by the engine. */
    private boolean immediateTicks;

    /** The families that accept immediate ticks in immediate mode (the reference's acceptsImmediateTicks). */
    private final Set<Identifier> immediateTickFamilies = new HashSet<>();

    /** The per-family tick dispatch (the reference's Block.tick). */
    public interface BlockTicker {
        void tick(EngineWorld world, BlockPosition pos, BlockType state, Random random);
    }

    private final Map<Identifier, BlockTicker> tickers = new HashMap<>();

    private final EngineWorld world;
    private final Random random;

    /** The all-entries index (ServerWorld line 86). */
    private final Set<ScheduledTickEntry> entries = new HashSet<>();
    /** The drain order (ServerWorld line 87). */
    private final TreeSet<ScheduledTickEntry> ordered = new TreeSet<>();
    /** The entries being fired this pass (ServerWorld line 110). */
    private final List<ScheduledTickEntry> thisTick = new ArrayList<>();

    public ScheduledTickSystem(EngineWorld world, Random random) {
        this.world = Objects.requireNonNull(world, "world");
        this.random = Objects.requireNonNull(random, "random");
    }

    // ------------------------------------------------------------------
    // Registration (the dispatch seam — engine wiring, tick lane or boot)
    // ------------------------------------------------------------------

    /** Registers one family's tick arm. Idempotent per family (the last registration wins). */
    public void registerTicker(Identifier family, BlockTicker ticker) {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(ticker, "ticker");
        tickers.put(family, ticker);
    }

    /** The immediate-tick claim for one family (the reference's acceptsImmediateTicks registration). */
    public void registerImmediateTickFamily(Identifier family) {
        immediateTickFamilies.add(Objects.requireNonNull(family, "family"));
    }

    /** Arms or disarms the worldgen immediate mode (the reference's doTicksImmediately). The engine never arms it. */
    public void setImmediateTicks(boolean immediateTicks) {
        this.immediateTicks = immediateTicks;
    }

    // ------------------------------------------------------------------
    // The queries (copies; best-effort from any thread)
    // ------------------------------------------------------------------

    /** @return a copy of the pending entries in drain order (time, priority, insertion). */
    public List<ScheduledTickEntry> pending() {
        return new ArrayList<>(ordered);
    }

    /** @return the pending entry for the position and block family, or null. */
    public ScheduledTickEntry pendingAt(BlockPosition position, Identifier block) {
        for (ScheduledTickEntry entry : ordered) {
            if (entry.pos().equals(position) && entry.block().equals(block)) {
                return entry;
            }
        }
        return null;
    }

    /** The reference's willTickThisTick (ServerWorld lines 382-385): the this-tick list during a pass. */
    public boolean willTickThisTick(BlockPosition position, Identifier block) {
        return thisTick.contains(new ScheduledTickEntry(position, block));
    }

    // ------------------------------------------------------------------
    // The mutations (the world's authoritative context)
    // ------------------------------------------------------------------

    /** The reference's scheduleTick (ServerWorld lines 388-390): priority 0. */
    public void scheduleTick(BlockPosition position, Identifier block, int delay) {
        scheduleTick(position, block, delay, 0);
    }

    /**
     * The reference's scheduleTick (ServerWorld lines 392-423, 1:1):
     * the immediate-mode arm, then the loaded-area gate, then the
     * material gate around time/priority, then the no-merge add.
     */
    public void scheduleTick(BlockPosition position, Identifier block, int delay, int priority) {
        world.requireMutationContext("scheduleTick");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(block, "block");
        ScheduledTickEntry entry = new ScheduledTickEntry(position, block);
        boolean air = isAir(block);
        if (immediateTicks && !air) {
            if (immediateTickFamilies.contains(block)) {
                // ServerWorld lines 397-407: the family ticks NOW (the
                // area gate at radius 8, the same-block gate) and no
                // entry is queued. No engine family claims immediate
                // ticks and the engine never arms the mode — the arm is
                // ported whole for the worldgen surface's future.
                if (world.isChunkLoadedAt(position)) {
                    BlockType state = world.getBlock(position);
                    if (!state.identifier().equals(world.airType().identifier())
                            && state.identifier().equals(block)) {
                        dispatch(state, position);
                    }
                }
                return;
            }
            delay = 1;
        }

        // The reference's isAreaLoaded at radius 0 (line 412, the i = 0 quirk).
        if (world.isChunkLoadedAt(position)) {
            if (!air) {
                entry.setTime(delay + world.totalTicks());
                entry.setPriority(priority);
            }
            if (!entries.contains(entry)) {
                entries.add(entry);
                ordered.add(entry);
            }
        }
    }

    /**
     * The anvil/import arm (ServerWorld lines 426-437, 1:1): no
     * loaded-area gate, the priority ALWAYS, the time only for non-air.
     */
    public void loadScheduledTick(BlockPosition position, Identifier block, int delay, int priority) {
        world.requireMutationContext("loadScheduledTick");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(block, "block");
        ScheduledTickEntry entry = new ScheduledTickEntry(position, block);
        entry.setPriority(priority);
        if (!isAir(block)) {
            entry.setTime(delay + world.totalTicks());
        }
        if (!entries.contains(entry)) {
            entries.add(entry);
            ordered.add(entry);
        }
    }

    /** Cancels the pending entry for the position and block family (the anvil tile-import refusal arm). */
    public boolean cancelAt(BlockPosition position, Identifier block) {
        world.requireMutationContext("cancelScheduledTick");
        ScheduledTickEntry probe = new ScheduledTickEntry(position, block);
        if (!entries.contains(probe)) {
            return false;
        }
        ScheduledTickEntry found = null;
        for (ScheduledTickEntry entry : ordered) {
            if (entry.equals(probe)) {
                found = entry;
                break;
            }
        }
        if (found == null) {
            return false; // the structures disagree — the sync guard would refuse the pass
        }
        ordered.remove(found);
        entries.remove(found);
        return true;
    }

    // ------------------------------------------------------------------
    // The pass (the reference's doScheduledTicks, ServerWorld lines 457-512)
    // ------------------------------------------------------------------

    /** The fan's arm: {@link #doScheduledTicks(boolean)} with the horizon on. */
    public void tick() {
        doScheduledTicks(false);
    }

    /**
     * The reference's doScheduledTicks (ServerWorld lines 457-512, 1:1):
     * the sync guard, the cap, the cleaning phase (the horizon break at
     * {@code time > world time}), the ticking phase (the loaded gate,
     * the same-family gate, the not-loaded re-arm at delay 0), the
     * clear, and whether more remain.
     */
    public boolean doScheduledTicks(boolean flush) {
        world.requireMutationContext("doScheduledTicks");
        int size = ordered.size();
        if (size != entries.size()) {
            throw new IllegalStateException("TickNextTick list out of synch");
        }
        if (size > PASS_CAP) {
            size = PASS_CAP;
        }

        // cleaning (lines 473-482)
        for (int i = 0; i < size; i++) {
            ScheduledTickEntry first = ordered.first();
            if (!flush && first.time() > world.totalTicks()) {
                break;
            }
            ordered.remove(first);
            entries.remove(first);
            thisTick.add(first);
        }

        // ticking (lines 486-507)
        Iterator<ScheduledTickEntry> iterator = thisTick.iterator();
        while (iterator.hasNext()) {
            ScheduledTickEntry entry = iterator.next();
            iterator.remove();
            if (world.isChunkLoadedAt(entry.pos())) {
                BlockType state = world.getBlock(entry.pos());
                if (!state.identifier().equals(world.airType().identifier())
                        && state.identifier().equals(entry.block())) {
                    dispatch(state, entry.pos());
                }
                // else (line 494's material/areEqual gate): the block
                // changed families between schedule and fire — the entry
                // drops. No re-arm, exactly the reference.
            } else {
                // line 505: the area went away — re-arm due immediately.
                scheduleTick(entry.pos(), entry.block(), 0);
            }
        }

        thisTick.clear();
        return !ordered.isEmpty();
    }

    private void dispatch(BlockType state, BlockPosition pos) {
        BlockTicker ticker = tickers.get(state.identifier());
        if (ticker == null) {
            // The reference's default Block.tick body: the no-op. The
            // family's engine arm is demand-named (the ledger's rows).
            return;
        }
        ticker.tick(world, pos, state, random);
    }

    // ------------------------------------------------------------------
    // The box query (the reference's getScheduledTicks, ServerWorld
    // lines 515-554) — the chunk save/unload support
    // ------------------------------------------------------------------

    /**
     * The reference's per-chunk box (lines 515-522): the chunk grown by
     * 2 in x/z. The y band is ignored by the reference's own bounds
     * walk (line 539 checks x and z alone) — ported whole.
     */
    public List<ScheduledTickEntry> getScheduledTicks(ChunkPosition chunk, boolean remove) {
        int minX = (chunk.x() << 4) - 2;
        int maxX = minX + 16 + 2;
        int minZ = (chunk.z() << 4) - 2;
        int maxZ = minZ + 16 + 2;
        return getScheduledTicks(minX, minZ, maxX, maxZ, remove);
    }

    /**
     * The reference's box walk (lines 525-554, 1:1): both structures in
     * order (the ordered tree, then the this-tick list), the x/z
     * inclusive-exclusive bounds, the optional remove.
     */
    public List<ScheduledTickEntry> getScheduledTicks(int minX, int minZ, int maxX, int maxZ, boolean remove) {
        world.requireMutationContext("getScheduledTicks");
        List<ScheduledTickEntry> list = null;
        for (int pass = 0; pass < 2; pass++) {
            Iterator<ScheduledTickEntry> iterator;
            if (pass == 0) {
                iterator = ordered.iterator();
            } else {
                iterator = thisTick.iterator();
            }
            while (iterator.hasNext()) {
                ScheduledTickEntry entry = iterator.next();
                BlockPosition pos = entry.pos();
                if (pos.x() >= minX && pos.x() < maxX && pos.z() >= minZ && pos.z() < maxZ) {
                    if (remove) {
                        entries.remove(entry);
                        iterator.remove();
                    }
                    if (list == null) {
                        list = new ArrayList<>();
                    }
                    list.add(entry);
                }
            }
        }
        return list == null ? List.of() : list;
    }

    /** @return the pending count (the ledger's own row). */
    public int size() {
        return ordered.size();
    }

    private boolean isAir(Identifier block) {
        return block.equals(world.airType().identifier());
    }

    /** The wiring seam for the diagnostics surface — the families registered, for the report walk. */
    public Set<Identifier> registeredFamilies() {
        return Set.copyOf(tickers.keySet());
    }
}
