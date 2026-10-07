package net.zamin.engine.entity;

import net.zamin.api.ItemStack;
import net.zamin.api.PlayerState;
import net.zamin.api.Position;
import net.zamin.engine.player.PlayerInventory;
import net.zamin.engine.player.PlayerSession;

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

    /**
     * Spawns a dropped item near the given block with the historical pop
     * velocity, returns the new entity. Caller supplies the stack; count and
     * validity are the caller's contract.
     */
    public ItemEntity spawnDropAtBlock(Position blockPosition, ItemStack stack,
                                       int pickupDelayTicks) {
        Objects.requireNonNull(blockPosition, "blockPosition");
        Objects.requireNonNull(stack, "stack");
        // Center of the block plus the historical small random pop offset.
        double x = blockPosition.x() + 0.5 + (random.nextDouble() * 0.2 - 0.1);
        double y = blockPosition.y() + 0.5;
        double z = blockPosition.z() + 0.5 + (random.nextDouble() * 0.2 - 0.1);
        ItemEntity entity = new ItemEntity(nextEntityId++, new Position(x, y, z),
                stack, pickupDelayTicks);
        entity.setVelocity(random.nextDouble() * 0.2 - 0.1, 0.2, random.nextDouble() * 0.2 - 0.1);
        entities.add(entity);
        for (Listener listener : listeners) {
            listener.onItemSpawned(entity);
        }
        return entity;
    }

    /** Spawns an entity at an exact position with exact velocity (player throws). */
    public ItemEntity spawnThrown(Position origin, ItemStack stack) {
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

    /** Advances all item entities one tick: physics, pickup checks, despawn. */
    public void tick(Iterable<PlayerSession> players) {
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

    /** Removes a specific entity (e.g. chunk unload policy later); publishes removal. */
    public void remove(ItemEntity entity, String reason) {
        if (entities.remove(entity)) {
            for (Listener listener : listeners) {
                listener.onItemRemoved(entity, reason);
            }
        }
    }
}
