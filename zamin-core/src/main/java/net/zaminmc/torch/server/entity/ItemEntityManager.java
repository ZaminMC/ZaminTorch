package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.player.PlayerInventory;
import net.zaminmc.torch.server.player.PlayerSession;

import java.util.ArrayList;
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
     * the historical behavior). Bounded O(n²) per tick: fine at slice scale,
     * revisit with the entity-system slice when worlds carry many items.
     */
    private void mergeNearbyItems() {
        List<ItemEntity> absorbed = null;
        for (int i = 0; i < entities.size(); i++) {
            ItemEntity keeper = entities.get(i);
            if (keeper.expired() || (absorbed != null && absorbed.contains(keeper))) {
                continue;
            }
            for (int j = 0; j < entities.size(); j++) {
                if (j == i) {
                    continue;
                }
                ItemEntity other = entities.get(j);
                if (other.expired() || (absorbed != null && absorbed.contains(other))) {
                    continue;
                }
                if (other.entityId() < keeper.entityId() || !withinMergeRange(keeper, other)
                        || !stacksMergeable(keeper.stack(), other.stack())) {
                    continue;
                }
                // "other" (younger) empties into "keeper" (older), capped.
                int capacity = keeper.stack().type().maxStackSize() - keeper.stack().count();
                int moved = Math.min(capacity, other.stack().count());
                if (moved <= 0) {
                    continue; // keeper's stack is full: nothing combines
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
            }
        }
        if (absorbed != null) {
            entities.removeAll(absorbed);
        }
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
}
