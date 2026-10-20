package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.player.PlayerInventory;
import net.zaminmc.torch.server.player.PlayerSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Owns all item entities of one world. Simulation-thread confined: creation,
 * ticking, pickup and removal all run on the world's owner, so no locks are
 * needed and ordering matches the tick (§447/§448 spirit).
 *
 * <p>Pickup (§433) is a validated semantic operation: delay respected, proximity
 * required, capacity handled through the inventory's partial-pickup contract —
 * the remainder stays in the world. Removal (§450) coordinates state, timer and
 * publication in one place.</p>
 */
public final class ItemEntityManager {

    private static final Logger LOGGER = Logger.getLogger(ItemEntityManager.class.getName());

    /** Historical pickup proximity: player box grown by 1.0 horizontal, 0.5 vertical. */
    static final double PICKUP_GROWTH_HORIZONTAL = 1.0;
    static final double PICKUP_GROWTH_VERTICAL = 0.5;
    /**
     * Historical item-merge search: the entity's bounding box expanded by 0.5
     * on every side (1.8 {@code getEntityBoundingBox().expand(0.5)}). Two item
     * entities whose boxes come within that expansion combine.
     */
    static final double MERGE_BOX_EXPANSION = 0.5;

    private final ItemEntity.Ground ground;
    private final Random random;
    private final List<ItemEntity> entities = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private int nextEntityId;

    /** Events the protocol adapter translates into wire updates. */
    public interface Listener {
        void onItemSpawned(ItemEntity entity);

        void onItemMoved(ItemEntity entity);

        void onItemCollected(ItemEntity entity, PlayerSession collector, int collectedCount);

        /**
         * A MOB's pickup (Slice 92 — MobEntity.updateInventory's
         * sendPickup + item.remove()): the Collect packet's collector is
         * the mob's own engine id (the mobs ride the engine id space on
         * the wire, unlike the observer-local player ids).
         */
        default void onItemCollectedByMob(ItemEntity entity,
                net.zaminmc.torch.server.entity.MobEntity collector) {
        }

        /**
         * The pickup's own effects (Slice 93/D1 — ItemEntity.onPlayerCollision
         * lines 269-300's tail, the part that rides a SUCCESSFUL add — the
         * partial pickup included, the reference's addItem-returned-true
         * semantics): the achievement family (GET_LOG for the log/log2
         * pair, KILL_COW for the leather, GET_DIAMOND, GET_BLAZE_ROD, and
         * GIVE_DIAMOND — the diamond picked up with a thrower stamps THAT
         * player) and the random.pop sound at the collector's position
         * (0.2F, the {@code ((r - r) * 0.7F + 1.0F) * 2.0F} pitch). The
         * engine's cross-system walk (the stats, the thrower lookup, the
         * fx) lives in the server's dispatch; the manager stays decoupled.
         */
        default void onItemPickupEffects(ItemEntity entity, PlayerSession collector,
                int collectedCount) {
        }

        void onItemStackChanged(ItemEntity entity);

        void onItemRemoved(ItemEntity entity, String reason);
    }

    public ItemEntityManager(ItemEntity.Ground ground, Random random, int firstEntityId) {
        this.ground = Objects.requireNonNull(ground, "ground");
        this.random = Objects.requireNonNull(random, "random");
        this.nextEntityId = firstEntityId;
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** The public §27.7 item-spawn gate (null until the server wires the bus). */
    private net.zaminmc.torch.server.event.EngineEventBus eventBus;

    /**
     * Wires the public item-spawn gate: every drop (the block-break
     * loot, the mob deaths, the player throws, the container spills,
     * the dispenser's plain egress) fires the cancellable pre-commit
     * event before the entity joins the world. Called once by the
     * server at boot.
     */
    public void setEventBus(net.zaminmc.torch.server.event.EngineEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * The spawn gate: fires the §27.7 event, answers whether the drop
     * may enter the world. Runs on the spawn's own (tick) context.
     */
    private boolean admitSpawn(ItemStack stack, Position position) {
        if (eventBus == null) {
            return true;
        }
        net.zaminmc.torch.event.entity.ItemSpawnEvent gate =
                new net.zaminmc.torch.event.entity.ItemSpawnEvent(stack, position);
        eventBus.fire(gate);
        return !gate.cancelled();
    }

    /**
     * The restored form (the Anvil import's exact-position spawn): no
     * random pop offset, no velocity — the saved Pos and Motion rows
     * apply after through the entity's own setters, exactly like the
     * reference's {@code Entities.create} path for an EntityItem.
     *
     * @return the new entity, or null when the §27.7 gate cancelled the
     *         spawn (the stack never enters the world; no id is consumed)
     */
    public ItemEntity spawnRestored(Position at, ItemStack stack) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(stack, "stack");
        if (!admitSpawn(stack, at)) {
            return null;
        }
        ItemEntity entity = new ItemEntity(nextEntityId++, at, stack, 0);
        entities.add(entity);
        for (Listener listener : listeners) {
            listener.onItemSpawned(entity);
        }
        return entity;
    }

    /**
     * Spawns a dropped item near the given block with the historical pop
     * velocity, returns the new entity. Caller supplies the stack; count and
     * validity are the caller's contract.
     *
     * @return the new entity, or null when the §27.7 gate cancelled the
     *         drop (the stack never enters the world; no id is consumed)
     */
    public ItemEntity spawnDropAtBlock(Position blockPosition, ItemStack stack,
                                       int pickupDelayTicks) {
        Objects.requireNonNull(blockPosition, "blockPosition");
        Objects.requireNonNull(stack, "stack");
        // Center of the block plus the historical small random pop offset.
        double x = blockPosition.x() + 0.5 + (random.nextDouble() * 0.2 - 0.1);
        double y = blockPosition.y() + 0.5;
        double z = blockPosition.z() + 0.5 + (random.nextDouble() * 0.2 - 0.1);
        Position at = new Position(x, y, z);
        if (!admitSpawn(stack, at)) {
            return null; // the §27.7 gate withheld the drop
        }
        ItemEntity entity = new ItemEntity(nextEntityId++, at,
                stack, pickupDelayTicks);
        entity.setVelocity(random.nextDouble() * 0.2 - 0.1, 0.2, random.nextDouble() * 0.2 - 0.1);
        entities.add(entity);
        for (Listener listener : listeners) {
            listener.onItemSpawned(entity);
        }
        return entity;
    }

    /**
     * Spawns an entity at an exact position with exact velocity (player throws).
     *
     * @return the new entity, or null when the §27.7 gate cancelled the
     *         drop (no id is consumed)
     */
    public ItemEntity spawnThrown(Position origin, ItemStack stack) {
        if (!admitSpawn(stack, origin)) {
            return null; // the §27.7 gate withheld the throw
        }
        ItemEntity entity = new ItemEntity(nextEntityId++, origin, stack,
                ItemEntity.PICKUP_DELAY_PLAYER_THROW_TICKS);
        entities.add(entity);
        for (Listener listener : listeners) {
            listener.onItemSpawned(entity);
        }
        return entity;
    }

    /** @return an unmodifiable view of live entities (diagnostics/tests). */
    public List<ItemEntity> all() {
        return List.copyOf(entities);
    }

    /** @return the live entity with the engine id, or null (the seated-rider consult). */
    public ItemEntity byId(int entityId) {
        for (ItemEntity entity : entities) {
            if (entity.entityId() == entityId) {
                return entity;
            }
        }
        return null;
    }

    public int size() {
        return entities.size();
    }

    /** Advances all item entities one tick: merging, physics, pickup checks, despawn. */
    public void tick(Iterable<PlayerSession> players) {
        mergeNearbyItems();

        Iterator<ItemEntity> iterator = entities.iterator();
        while (iterator.hasNext()) {
            ItemEntity entity = iterator.next();

            if (entity.expired()) {
                iterator.remove();
                for (Listener listener : listeners) {
                    listener.onItemRemoved(entity, "despawned");
                }
                continue;
            }

            if (entity.inVoid()) {
                // The vanilla void: fell past the kill plane, gone silently
                // (no drop-back — the stack is out of the world).
                iterator.remove();
                for (Listener listener : listeners) {
                    listener.onItemRemoved(entity, "fell out of world");
                }
                continue;
            }

            if (entity.tick(ground)) {
                for (Listener listener : listeners) {
                    listener.onItemMoved(entity);
                }
            }

            if (!entity.pickupAllowed()) {
                continue;
            }
            for (PlayerSession player : players) {
                if (player.state() != PlayerState.PLAYING) {
                    continue; // only players in play state collect items
                }
                // The owner-window gate (Slice 93/D1 — onPlayerCollision
                // lines 266-267): an owned body answers only to its owner
                // until the last 200 ticks before despawn; the engine's
                // drops carry no owner (the null arm passes everyone).
                if (!entity.pickupAllowedFor(player.name())) {
                    continue;
                }
                if (!withinPickupRange(player, entity)) {
                    continue;
                }
                PlayerInventory inventory = player.inventory();
                ItemStack wanted = entity.stack();
                ItemStack remainder = inventory.pickUp(wanted);
                int collected = wanted.count() - remainder.count();
                if (collected <= 0) {
                    continue; // inventory could not take any of it
                }
                // The pickup's own effects (the reference's tail — the
                // achievements and the pop sound ride EVERY successful
                // add, the partial pickup included).
                for (Listener listener : listeners) {
                    listener.onItemPickupEffects(entity, player, collected);
                }
                if (remainder.isEmpty()) {
                    iterator.remove();
                    for (Listener listener : listeners) {
                        listener.onItemCollected(entity, player, collected);
                    }
                } else {
                    entity.setStack(remainder);
                    for (Listener listener : listeners) {
                        listener.onItemStackChanged(entity);
                    }
                }
                break; // one collector per tick per entity
            }
        }
    }

    private boolean withinPickupRange(PlayerSession player, ItemEntity entity) {
        Position playerPosition = player.position();
        Position itemPosition = entity.position();
        double dx = Math.abs(itemPosition.x() - playerPosition.x());
        double dz = Math.abs(itemPosition.z() - playerPosition.z());
        double maxHorizontal = 0.3 + ItemEntity.HALF_WIDTH + PICKUP_GROWTH_HORIZONTAL;
        if (dx > maxHorizontal || dz > maxHorizontal) {
            return false;
        }
        double itemBottom = itemPosition.y() - ItemEntity.HALF_HEIGHT;
        double itemTop = itemPosition.y() + ItemEntity.HALF_HEIGHT;
        double rangeBottom = playerPosition.y() - PICKUP_GROWTH_VERTICAL;
        double rangeTop = playerPosition.y() + 1.8 + PICKUP_GROWTH_VERTICAL;
        return itemTop > rangeBottom && itemBottom < rangeTop;
    }

    /**
     * The historical item combination: every tick, entities whose boxes lie
     * within {@link #MERGE_BOX_EXPANSION} of each other and carry mergeable
     * stacks (same item, same damage) combine — the younger entity (higher id,
     * deterministic) empties into the older one, capped at the stack limit; a
     * partial remainder keeps the younger entity alive with the rest. Pickup
     * delay does not block combining (freshly dropped items visibly merge,
     * the historical behavior).
     *
     * <p><b>The walk's spatial bucketing (§22 Phase 7 — the demand-named
     * heavy-owner optimization).</b> The candidate scan was a plain O(n²)
     * double loop over the whole population; at 1546 settled items one owner
     * spent 16.4ms/tick inside this walk (the §22 Phase-7 record: superlinear
     * per-item cost, 92.6% of the tick fan). The walk now builds, once per
     * tick, a spatial bucket index over the live population (a
     * {@link #MERGE_CELL_SIZE}-edge cell grid — coarse enough that any pair
     * inside the per-axis merge window sits in cells at most one apart, so a
     * 3×3×3 neighbor sweep is complete) and consults only the 27 nearby
     * cells for each keeper. THE LAW IS UNCHANGED — the buckets are a
     * candidate-index optimization, never a reordering: the outer loop still
     * walks the live list in order, the inner loop still visits its
     * candidates in ASCENDING list order (the collected candidates are
     * sorted back into list order), every check and every merge arm runs
     * verbatim — so the absorbed set, the final stacks, the partial
     * remainders and the wire-visible listener publication sequence are
     * identical to the old walk by construction (pinned per-seed by
     * ItemMergeBucketingEquivalenceTest against a verbatim copy of the old
     * law). A dense neighborhood (more than a quarter of the population in
     * one keeper's 27 cells — the mixed-type pile) falls back to the old
     * plain full scan for that keeper: sorting a near-full candidate set
     * would cost more than the scan it replaces, and the scan visits the
     * same candidates in the same order anyway.</p>
     *
     * <p>Bucket granularity: the §22 Phase-7 row's own first shape was
     * per-chunk buckets (16×16, no cross-owner coordination inside the
     * window); this walk takes the finer cell grid the window itself names.
     * The merge window is 0.75 blocks per axis — three orders below a
     * chunk's edge — and the measured dense scene (settled items on a
     * 2.0-block grid) leaves a per-chunk 3×3 sweep holding hundreds of
     * candidates where the 1.0-block cell grid holds one or two; the finer
     * grid needs no cross-owner coordination either (any pair inside the
     * window sits inside one 2×2×2 block corner, one owner's region when
     * the partition lands). Snapshot-friendly: the buckets are rebuilt from
     * the live list every walk and never outlive it.</p>
     *
     * <p>Package-private so the equivalence test can drive the walk in
     * isolation. The walk assumes listeners do not re-enter the manager —
     * the registered dispatches are pure wire publications, the same
     * no-reentrancy the old index walk already required of a mid-walk list
     * mutation.</p>
     */
    void mergeNearbyItems() {
        int population = entities.size();
        if (population == 0) {
            return;
        }
        buildMergeBuckets(population);
        // The dense-neighborhood bail: a keeper whose 27 cells hold more
        // than a quarter of the population takes the plain full scan —
        // sorting a near-full candidate set costs more than the scan, and
        // the scan is the same law in the same visiting order.
        int bailLimit = population / 4;
        List<ItemEntity> absorbed = null;
        for (int i = 0; i < population; i++) {
            ItemEntity keeper = entities.get(i);
            if (keeper.expired() || (absorbed != null && absorbed.contains(keeper))) {
                continue;
            }
            int count = collectMergeCandidates(i, bailLimit);
            if (count < 0) {
                // The plain full scan (the old walk's inner loop, verbatim).
                for (int j = 0; j < population; j++) {
                    absorbed = mergeInto(i, keeper, j, absorbed);
                }
                continue;
            }
            // The bucketed scan: only the provably-relevant candidates, in
            // the SAME ascending list order the plain scan would visit them.
            Arrays.sort(mergeCandidates, 0, count);
            for (int c = 0; c < count; c++) {
                absorbed = mergeInto(i, keeper, mergeCandidates[c], absorbed);
            }
        }
        if (absorbed != null) {
            entities.removeAll(absorbed);
        }
    }

    /**
     * One candidate visit — the old walk's inner-loop body VERBATIM (the
     * checks in the old order, the merge arms, the publication sequence).
     * Returns the absorbed list, created on the first full absorption.
     */
    private List<ItemEntity> mergeInto(int keeperIndex, ItemEntity keeper, int otherIndex,
                                       List<ItemEntity> absorbed) {
        if (otherIndex == keeperIndex) {
            return absorbed;
        }
        ItemEntity other = entities.get(otherIndex);
        if (other.expired() || (absorbed != null && absorbed.contains(other))) {
            return absorbed;
        }
        if (other.entityId() < keeper.entityId() || !withinMergeRange(keeper, other)
                || !stacksMergeable(keeper.stack(), other.stack())) {
            return absorbed;
        }
        // "other" (younger) empties into "keeper" (older), capped.
        int capacity = keeper.stack().type().maxStackSize() - keeper.stack().count();
        int moved = Math.min(capacity, other.stack().count());
        if (moved <= 0) {
            return absorbed; // keeper's stack is full: nothing combines
        }
        keeper.setStack(keeper.stack().withCount(keeper.stack().count() + moved));
        if (absorbed == null) {
            absorbed = new ArrayList<>();
        }
        if (moved == other.stack().count()) {
            absorbed.add(other);
            publishStackChanged(keeper);
            publishRemoved(other, "merged");
        } else {
            other.setStack(other.stack().withCount(other.stack().count() - moved));
            publishStackChanged(keeper);
            publishStackChanged(other);
        }
        return absorbed;
    }

    // ------------------------------------------------------------------
    // The merge walk's spatial buckets (§22 Phase 7): a cell grid over the
    // live population, built once per walk. Tick-lane confined — the arrays
    // are reused across walks and touched only inside the walk.
    // ------------------------------------------------------------------

    /**
     * The bucket cell edge: the per-axis merge window (the box expansion
     * plus the item box) rounded up. A cell at least as wide as the window
     * makes the 3×3×3 neighbor sweep complete — two positions within the
     * window can never sit in cells two apart on an axis. Derived, not
     * hardcoded: the window the constants define is the window the sweep
     * covers.
     */
    static final double MERGE_CELL_SIZE = Math.ceil(
            MERGE_BOX_EXPANSION + 2 * Math.max(ItemEntity.HALF_WIDTH, ItemEntity.HALF_HEIGHT));

    /**
     * The packed cell key's per-axis limits: 26 bits per horizontal axis
     * (±33,554,431 cells — the 1.8.8 world border at ±29,999,984 blocks sits
     * deep inside) and 11 vertical bits (±1023 cells — the void kill plane
     * at -64 and the build height at 256 both inside). A positive 63-bit
     * long: horizontal field bits 37-62, vertical 26-36, z 0-25.
     */
    private static final int MERGE_CELL_LIMIT_HORIZONTAL = 1 << 25;
    private static final int MERGE_CELL_LIMIT_VERTICAL = 1 << 10;

    /** The bucket table: the packed cell key per slot (valid iff the slot head is set). */
    private long[] mergeSlotKeys;
    /** The head entity index of each slot's cell chain; -1 marks the slot empty. */
    private int[] mergeSlotHeads;
    /** The chain links: per entity, the next entity index of the same cell, -1 at the end. */
    private int[] mergeChainNext;
    /** The table's mask (its length minus one — a power of two). */
    private int mergeSlotMask;
    /** The per-keeper candidate buffer (indices collected from the 27 nearby cells). */
    private int[] mergeCandidates;

    /**
     * Builds the walk's bucket index over the CURRENT population: an
     * open-addressed table (capacity at least twice the population, so the
     * probe chains always terminate) keyed by the packed cell, each occupied
     * slot heading a singly-linked chain of its cell's entity indices. A
     * chain holds its indices in descending insertion order — irrelevant:
     * the per-keeper collection sorts the candidates back into list order
     * (the buckets are an index, never a reordering).
     */
    private void buildMergeBuckets(int population) {
        if (mergeSlotHeads == null || mergeSlotHeads.length < population * 2) {
            int capacity = 16;
            while (capacity < population * 2) {
                capacity <<= 1;
            }
            mergeSlotKeys = new long[capacity];
            mergeSlotHeads = new int[capacity];
        }
        mergeSlotMask = mergeSlotHeads.length - 1;
        if (mergeChainNext == null || mergeChainNext.length < population) {
            mergeChainNext = new int[population];
        }
        if (mergeCandidates == null || mergeCandidates.length < population) {
            mergeCandidates = new int[population];
        }
        Arrays.fill(mergeSlotHeads, -1);
        for (int i = 0; i < population; i++) {
            long key = mergeCellKey(entities.get(i).position());
            int slot = claimMergeSlot(key);
            mergeChainNext[i] = mergeSlotHeads[slot];
            mergeSlotHeads[slot] = i;
        }
    }

    /**
     * Collects the keeper's merge candidates: every entity but the keeper
     * itself whose cell sits in the keeper's 3×3×3 cell neighborhood — the
     * complete candidate set for the per-axis window at the cell edge (an
     * in-window pair's cells are at most one apart per axis, so the sweep
     * cannot miss one; the cell key's border clamp is monotone and
     * 1-Lipschitz, so the property survives the packing limits). Border
     * folding can enumerate a border cell more than once — a duplicated
     * candidate is a provable no-op (after any merge the keeper's capacity
     * is zero or the other is absorbed), and the bail below bounds the
     * buffer long before it could overflow.
     *
     * @return the candidate count, or -1 when the neighborhood holds more
     *         than {@code bailLimit} candidates — the caller takes the plain
     *         full scan instead (the same law in the same visiting order)
     */
    private int collectMergeCandidates(int keeper, int bailLimit) {
        ItemEntity entity = entities.get(keeper);
        Position position = entity.position();
        int cellX = mergeCellOf(position.x(), MERGE_CELL_LIMIT_HORIZONTAL);
        int cellY = mergeCellOf(position.y(), MERGE_CELL_LIMIT_VERTICAL);
        int cellZ = mergeCellOf(position.z(), MERGE_CELL_LIMIT_HORIZONTAL);
        int[] buffer = mergeCandidates;
        int size = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int slot = findMergeSlot(mergeCellKey(cellX + dx, cellY + dy, cellZ + dz));
                    if (slot < 0) {
                        continue;
                    }
                    for (int j = mergeSlotHeads[slot]; j != -1; j = mergeChainNext[j]) {
                        if (j == keeper) {
                            continue;
                        }
                        buffer[size++] = j;
                        if (size > bailLimit) {
                            return -1;
                        }
                    }
                }
            }
        }
        return size;
    }

    /** Inserts or finds the key's slot (linear probing; the table is never full). */
    private int claimMergeSlot(long key) {
        int slot = mergeSlotOf(key);
        while (mergeSlotHeads[slot] >= 0 && mergeSlotKeys[slot] != key) {
            slot = (slot + 1) & mergeSlotMask;
        }
        mergeSlotKeys[slot] = key;
        return slot;
    }

    /** @return the key's slot, or -1 when the cell holds no entity this walk. */
    private int findMergeSlot(long key) {
        int slot = mergeSlotOf(key);
        while (mergeSlotHeads[slot] >= 0) {
            if (mergeSlotKeys[slot] == key) {
                return slot;
            }
            slot = (slot + 1) & mergeSlotMask;
        }
        return -1;
    }

    /** The golden-ratio spread over the packed key's high bits. */
    private int mergeSlotOf(long key) {
        return (int) ((key * 0x9E3779B97F4A7C15L) >>> 32) & mergeSlotMask;
    }

    /** The packed 63-bit key of one position's (clamped) cell. */
    private static long mergeCellKey(Position position) {
        return mergeCellKey(
                mergeCellOf(position.x(), MERGE_CELL_LIMIT_HORIZONTAL),
                mergeCellOf(position.y(), MERGE_CELL_LIMIT_VERTICAL),
                mergeCellOf(position.z(), MERGE_CELL_LIMIT_HORIZONTAL));
    }

    /** The packed 63-bit key of three (pre- or post-sweep) cell indices. */
    private static long mergeCellKey(int cellX, int cellY, int cellZ) {
        long x = mergeCellOf(cellX, MERGE_CELL_LIMIT_HORIZONTAL) + MERGE_CELL_LIMIT_HORIZONTAL;
        long y = mergeCellOf(cellY, MERGE_CELL_LIMIT_VERTICAL) + MERGE_CELL_LIMIT_VERTICAL;
        long z = mergeCellOf(cellZ, MERGE_CELL_LIMIT_HORIZONTAL) + MERGE_CELL_LIMIT_HORIZONTAL;
        return (x << 37) | (y << 26) | z;
    }

    /** The clamped cell index of one coordinate (the clamp keeps the ±1 sweep complete at the packing borders). */
    private static int mergeCellOf(double coordinate, int limit) {
        int cell = (int) Math.floor(coordinate / MERGE_CELL_SIZE);
        return Math.max(-limit, Math.min(limit - 1, cell));
    }

    /** The clamp itself: fold out-of-range cells onto the border cell (monotone, 1-Lipschitz). */
    private static int mergeCellOf(int cell, int limit) {
        return Math.max(-limit, Math.min(limit - 1, cell));
    }

    private static boolean withinMergeRange(ItemEntity a, ItemEntity b) {
        double dx = Math.abs(a.position().x() - b.position().x());
        double dy = Math.abs(a.position().y() - b.position().y());
        double dz = Math.abs(a.position().z() - b.position().z());
        return dx <= MERGE_BOX_EXPANSION + 2 * ItemEntity.HALF_WIDTH
                && dy <= MERGE_BOX_EXPANSION + 2 * ItemEntity.HALF_HEIGHT
                && dz <= MERGE_BOX_EXPANSION + 2 * ItemEntity.HALF_WIDTH;
    }

    /** Same item and same damage: the merge rule the window clicks play too. */
    private static boolean stacksMergeable(net.zaminmc.torch.item.ItemStack a, net.zaminmc.torch.item.ItemStack b) {
        return net.zaminmc.torch.item.ItemStack.mergeable(a, b);
    }

    private void publishStackChanged(ItemEntity entity) {
        for (Listener listener : listeners) {
            listener.onItemStackChanged(entity);
        }
    }

    private void publishRemoved(ItemEntity entity, String reason) {
        for (Listener listener : listeners) {
            listener.onItemRemoved(entity, reason);
        }
    }

    /** Removes a specific entity (e.g. chunk unload policy later); publishes removal. */
    /**
     * A mob's pickup (Slice 92 — the reference's sendPickup(item, 1) +
     * item.remove() pair): removes the entity and fires the
     * mob-collector arm (the wire's Collect + Destroy pair — the same
     * pair the player pickup sends). Tick-thread context.
     */
    public boolean collectByMob(ItemEntity entity,
            net.zaminmc.torch.server.entity.MobEntity collector) {
        if (entities.remove(entity)) {
            for (Listener listener : listeners) {
                listener.onItemCollectedByMob(entity, collector);
            }
            return true;
        }
        return false;
    }

    public void remove(ItemEntity entity, String reason) {
        if (entities.remove(entity)) {
            for (Listener listener : listeners) {
                listener.onItemRemoved(entity, reason);
            }
        }
    }

    // ------------------------------------------------------------------
    // The persistence surface (Slice 93/E1 — the reference's own
    // writeCustomNbt/readCustomNbt rows over the engine's Z-family store:
    // the item bodies survive the restart).
    // ------------------------------------------------------------------

    /** One persisted body: the whole state the store round-trips. */
    public record ItemRecord(
            Position position,
            double velocityX, double velocityY, double velocityZ,
            ItemStack stack,
            int age,
            int pickupDelay,
            boolean neverDespawn,
            String thrower,
            String owner) {
    }

    /** The live population's records (the tick-lane save snapshot). */
    public List<ItemRecord> snapshot() {
        List<ItemRecord> records = new ArrayList<>(entities.size());
        for (ItemEntity entity : entities) {
            records.add(new ItemRecord(
                    entity.position(),
                    entity.velocityX(), entity.velocityY(), entity.velocityZ(),
                    entity.stack(),
                    entity.age(),
                    entity.pickupDelayTicks(),
                    entity.neverDespawn(),
                    entity.thrower(),
                    entity.owner()));
        }
        return records;
    }

    /**
     * The boot restore: the persisted bodies rejoin the world with their
     * whole state — the position and motion, the stack with its tag arms,
     * the age clock and the pickup delay, the never-despawn pin, the
     * thrower/owner strings. The restore bypasses the §27.7 spawn gate
     * (the persisted bodies are already-committed state, exactly like the
     * mob store's restoreAll) and publishes each body's spawn so the
     * observers see them. Tick-lane context.
     */
    public void restoreAll(List<ItemRecord> records) {
        for (ItemRecord record : records) {
            if (record.stack().isEmpty()) {
                continue; // a resolvable-stack miss never joins the world
            }
            ItemEntity entity = new ItemEntity(nextEntityId++,
                    record.position(), record.stack(), record.pickupDelay());
            entity.setVelocity(record.velocityX(), record.velocityY(),
                    record.velocityZ());
            entity.setAge(record.age());
            if (record.neverDespawn()) {
                entity.setNeverDespawn();
            }
            entity.setThrower(record.thrower());
            entity.setOwner(record.owner());
            entities.add(entity);
            for (Listener listener : listeners) {
                listener.onItemSpawned(entity);
            }
        }
    }
}
