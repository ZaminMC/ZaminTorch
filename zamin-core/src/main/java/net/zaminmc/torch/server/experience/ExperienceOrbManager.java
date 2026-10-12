package net.zaminmc.torch.server.experience;

import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.player.PlayerSession;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Owns all experience orbs of one world, simulation-thread confined like the
 * item-entity manager it mirrors: creation, ticking, pickup and removal run
 * on the world's owner.
 *
 * <p>Payout (the historical orb-toucher rule): the first playing player whose
 * body comes within the pickup box absorbs the whole bundle — the client
 * hears the collect chime through the adapter, the points ride the player's
 * total through {@code Set Experience}.</p>
 */
public final class ExperienceOrbManager {

    /** The orb pickup box (the item rule: player box grown 1.0 h / 0.5 v). */
    static final double PICKUP_GROWTH_HORIZONTAL = 1.0;
    static final double PICKUP_GROWTH_VERTICAL = 0.5;

    private final ExperienceOrbEntity.Ground ground;
    private final Random random;
    private final List<ExperienceOrbEntity> orbs = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private int nextEntityId;

    /** Events the protocol adapter translates into wire updates. */
    public interface Listener {
        void onOrbSpawned(ExperienceOrbEntity orb);

        void onOrbMoved(ExperienceOrbEntity orb);

        void onOrbCollected(ExperienceOrbEntity orb, PlayerSession collector);

        void onOrbRemoved(ExperienceOrbEntity orb, String reason);
    }

    public ExperienceOrbManager(ExperienceOrbEntity.Ground ground, Random random, int firstEntityId) {
        this.ground = Objects.requireNonNull(ground, "ground");
        this.random = Objects.requireNonNull(random, "random");
        this.nextEntityId = firstEntityId;
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Splits {@code total} points into orbs at the position (the historical
     * kill reward splits 1..3 bundles so the scatter animates) and returns
     * the spawned entities. A non-positive total spawns nothing.
     */
    public List<ExperienceOrbEntity> spawnBurst(Position position, int total, int maxOrbs) {
        List<ExperienceOrbEntity> spawned = new ArrayList<>();
        if (total <= 0 || maxOrbs <= 0) {
            return spawned;
        }
        int remaining = total;
        int orbsToSpawn = Math.min(maxOrbs, Math.max(1, total / 3));
        for (int i = 0; i < orbsToSpawn && remaining > 0; i++) {
            int share = (i == orbsToSpawn - 1) ? remaining : remaining / (orbsToSpawn - i);
            if (share <= 0) {
                continue;
            }
            remaining -= share;
            ExperienceOrbEntity orb = new ExperienceOrbEntity(nextEntityId++,
                    new Position(position.x(), position.y() + 0.2, position.z()), share,
                    ExperienceOrbEntity.PICKUP_DELAY_TICKS);
            orb.setVelocity((random.nextDouble() - 0.5) * 0.2, 0.2,
                    (random.nextDouble() - 0.5) * 0.2);
            orbs.add(orb);
            spawned.add(orb);
            for (Listener listener : listeners) {
                listener.onOrbSpawned(orb);
            }
        }
        return spawned;
    }

    /** Spawns one orb of exactly {@code amount} points at the position. */
    public ExperienceOrbEntity spawnOrb(Position position, int amount) {
        return spawnBurst(position, amount, 1).get(0);
    }

    /** @return an unmodifiable view of live orbs (diagnostics/tests). */
    public List<ExperienceOrbEntity> all() {
        return List.copyOf(orbs);
    }

    public int size() {
        return orbs.size();
    }

    /** Advances all orbs one tick: physics, pickup, despawn, void. */
    public void tick(Iterable<PlayerSession> players) {
        Iterator<ExperienceOrbEntity> iterator = orbs.iterator();
        while (iterator.hasNext()) {
            ExperienceOrbEntity orb = iterator.next();

            if (orb.expired()) {
                iterator.remove();
                publishRemoved(orb, "despawned");
                continue;
            }
            if (orb.inVoid()) {
                iterator.remove();
                publishRemoved(orb, "fell out of world");
                continue;
            }

            if (orb.tick(ground)) {
                for (Listener listener : listeners) {
                    listener.onOrbMoved(orb);
                }
            }

            if (!orb.pickupAllowed()) {
                continue;
            }
            for (PlayerSession player : players) {
                if (player.state() != PlayerState.PLAYING || player.dead()) {
                    continue;
                }
                if (!withinPickupRange(player, orb)) {
                    continue;
                }
                player.addExperience(orb.amount());
                iterator.remove();
                for (Listener listener : listeners) {
                    listener.onOrbCollected(orb, player);
                }
                break; // one collector per tick per orb
            }
        }
    }

    private boolean withinPickupRange(PlayerSession player, ExperienceOrbEntity orb) {
        Position playerPosition = player.position();
        Position orbPosition = orb.position();
        double dx = Math.abs(orbPosition.x() - playerPosition.x());
        double dz = Math.abs(orbPosition.z() - playerPosition.z());
        double maxHorizontal = 0.3 + ExperienceOrbEntity.HALF_WIDTH + PICKUP_GROWTH_HORIZONTAL;
        if (dx > maxHorizontal || dz > maxHorizontal) {
            return false;
        }
        double orbBottom = orbPosition.y() - ExperienceOrbEntity.HALF_HEIGHT;
        double orbTop = orbPosition.y() + ExperienceOrbEntity.HALF_HEIGHT;
        double rangeBottom = playerPosition.y() - PICKUP_GROWTH_VERTICAL;
        double rangeTop = playerPosition.y() + 1.8 + PICKUP_GROWTH_VERTICAL;
        return orbTop > rangeBottom && orbBottom < rangeTop;
    }

    private void publishRemoved(ExperienceOrbEntity orb, String reason) {
        for (Listener listener : listeners) {
            listener.onOrbRemoved(orb, reason);
        }
    }

    /** Removes a specific orb (cleanup paths); publishes removal. */
    public void remove(ExperienceOrbEntity orb, String reason) {
        if (orbs.remove(orb)) {
            publishRemoved(orb, reason);
        }
    }
}
