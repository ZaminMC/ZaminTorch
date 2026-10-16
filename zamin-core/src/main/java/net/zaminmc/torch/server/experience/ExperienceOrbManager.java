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

    /**
     * The reference's own burst walk (Slice 65 — the bottle o' enchanting's
     * onCollision lines 35-41, ExperienceOrbEntity.roundSize): the total
     * splits through the roundSize ladder — each orb takes the biggest
     * tier the remainder still fits, until nothing is left. The historical
     * tier table: 2477, 1237, 617, 307, 149, 73, 37, 17, 7, 3, 1.
     */
    public List<ExperienceOrbEntity> spawnSplitBurst(Position position, int total) {
        List<ExperienceOrbEntity> spawned = new ArrayList<>();
        int remaining = total;
        while (remaining > 0) {
            int share = roundSize(remaining);
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

    /**
     * The reference's orb tier ladder (ExperienceOrbEntity.roundSize lines
     * 210-227): the biggest tier a value still fits. The tiers are the
     * historical XP-orb sizes — the client renders each tier with its own
     * texture, and every burst in vanilla (kills, ores, the bottle, the
     * dragon's batches) walks this ladder.
     */
    public static int roundSize(int size) {
        if (size >= 2477) {
            return 2477;
        } else if (size >= 1237) {
            return 1237;
        } else if (size >= 617) {
            return 617;
        } else if (size >= 307) {
            return 307;
        } else if (size >= 149) {
            return 149;
        } else if (size >= 73) {
            return 73;
        } else if (size >= 37) {
            return 37;
        } else if (size >= 17) {
            return 17;
        } else if (size >= 7) {
            return 7;
        }
        return size >= 3 ? 3 : 1;
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
