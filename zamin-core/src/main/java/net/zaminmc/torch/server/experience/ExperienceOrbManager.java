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
public final class ExperienceOrbManager implements ExperienceOrbEntity.Players {

    /** The orb pickup box (the item rule: player box grown 1.0 h / 0.5 v). */
    static final double PICKUP_GROWTH_HORIZONTAL = 1.0;
    static final double PICKUP_GROWTH_VERTICAL = 0.5;

    private final ExperienceOrbEntity.Ground ground;
    private final Random random;
    /**
     * The fizz band's sound sink (Wave LF) — null keeps the entities'
     * fizz-free pre-wave walk.
     */
    private final ExperienceOrbEntity.Sounds sounds;
    private final List<ExperienceOrbEntity> orbs = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    /**
     * The attraction's consult source (Wave LF): the tick walk's own
     * players, held for the per-orb {@link ExperienceOrbEntity.Players}
     * reads — null outside the walk (the consults are tick-thread only).
     */
    private Iterable<PlayerSession> currentPlayers;
    private int nextEntityId;

    /** Events the protocol adapter translates into wire updates. */
    public interface Listener {
        void onOrbSpawned(ExperienceOrbEntity orb);

        void onOrbMoved(ExperienceOrbEntity orb);

        void onOrbCollected(ExperienceOrbEntity orb, PlayerSession collector);

        void onOrbRemoved(ExperienceOrbEntity orb, String reason);
    }

    public ExperienceOrbManager(ExperienceOrbEntity.Ground ground, Random random, int firstEntityId) {
        this(ground, random, firstEntityId, null);
    }

    /**
     * The full form (Wave LF — the lava envelope + the attraction):
     * the {@code sounds} sink wires the fizz band over the FX broadcast;
     * null keeps the pre-wave silent walk (the seats' three-arg form).
     */
    public ExperienceOrbManager(ExperienceOrbEntity.Ground ground, Random random, int firstEntityId,
                                ExperienceOrbEntity.Sounds sounds) {
        this.ground = Objects.requireNonNull(ground, "ground");
        this.random = Objects.requireNonNull(random, "random");
        this.nextEntityId = firstEntityId;
        this.sounds = sounds;
    }

    /**
     * The attraction's nearest consult (Wave LF — the reference's
     * World.getNearestPlayer over EntityFilter.NOT_SPECTATOR): the
     * closest strictly-inside-range player of the dimension, the
     * player's FEET position to the point, the first of equals kept
     * (the reference's own {@code d1 < d0} preference).
     */
    @Override
    public ExperienceOrbEntity.Players.Target nearest(double x, double y, double z,
                                                       int dimension, double range) {
        ExperienceOrbEntity.Players.Target best = null;
        double bestSquared = -1.0;
        for (PlayerSession player : currentPlayers) {
            if (player.state() != PlayerState.PLAYING || player.dimension() != dimension
                    || player.gamemode() == net.zaminmc.torch.GameMode.SPECTATOR) {
                continue;
            }
            Position at = player.position();
            double dx = at.x() - x;
            double dy = at.y() - y;
            double dz = at.z() - z;
            double squared = dx * dx + dy * dy + dz * dz;
            if (squared < range * range && (bestSquared == -1.0 || squared < bestSquared)) {
                bestSquared = squared;
                best = targetOf(player);
            }
        }
        return best;
    }

    /** The attraction's held-igniter consult: the live same-dimension player. */
    @Override
    public ExperienceOrbEntity.Players.Target byId(int entityId, int dimension) {
        for (PlayerSession player : currentPlayers) {
            if (player.state() == PlayerState.PLAYING && player.dimension() == dimension
                    && player.engineEntityId() == entityId) {
                return targetOf(player);
            }
        }
        return null;
    }

    /**
     * The player view the pull reads — the feet position, the EYE aim
     * (PlayerEntity.getEyeHeight lines 1648-1655: 1.62 standing, 1.54
     * sneaking, 0.2 lying), the spectator flag.
     */
    private static ExperienceOrbEntity.Players.Target targetOf(PlayerSession player) {
        Position at = player.position();
        double eye = player.sleeping() ? 0.2 : 1.62 - (player.sneaking() ? 0.08 : 0.0);
        return new ExperienceOrbEntity.Players.Target(
                player.engineEntityId(), at.x(), at.y(), at.y() + eye, at.z(),
                player.gamemode() == net.zaminmc.torch.GameMode.SPECTATOR);
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Splits {@code total} points into orbs at the position (the historical
     * kill reward splits 1..3 bundles so the scatter animates) and returns
     * the spawned entities. A non-positive total spawns nothing. The
     * three-arg form keeps the overworld 0 (the overworld-bound systems'
     * honest default — Wave TF-10's stamp convention).
     */
    public List<ExperienceOrbEntity> spawnBurst(Position position, int total, int maxOrbs) {
        return spawnBurst(position, total, maxOrbs, 0);
    }

    /**
     * The dimension-stamped burst (Wave TF-10 — the reference's
     * Entity.world): the sites that know their world stamp it; the dying
     * mob's own dimension, the player's session, the projectile's carry.
     */
    public List<ExperienceOrbEntity> spawnBurst(Position position, int total, int maxOrbs,
                                                int dimension) {
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
            orb.setDimension(dimension);
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

    /** The dimension-stamped single orb (Wave TF-10). */
    public ExperienceOrbEntity spawnOrb(Position position, int amount, int dimension) {
        return spawnBurst(position, amount, 1, dimension).get(0);
    }

    /**
     * The restored form (the Anvil import's exact-amount landing — the
     * reference's {@code Entities.create} + {@code readCustomNbt} walk
     * over ExperienceOrbEntity): the saved position, the saved point
     * value and the saved age land whole. No random scatter velocity
     * (the saved Motion rows apply after through the entity's own
     * setter, the ItemEntityManager.spawnRestored pattern), no pickup
     * delay (the reference's own load leaves the field at its zero
     * initializer — readCustomNbt reads Health/Age/Value alone). The
     * {@code Health} row has no engine surface (the engine's orb model
     * carries no wound clock) — the import's ledger names it.
     *
     * @return the landed orb, or null when the amount is non-positive
     *         (nothing spawns — the burst arms' own rule).
     */
    public ExperienceOrbEntity spawnRestored(Position at, int amount, int age) {
        return spawnRestored(at, amount, age, 0);
    }

    /** The dimension-stamped restore (Wave TF-10 — the anvil import keeps
     * the overworld 0; the row exists for the store-carrying future). */
    public ExperienceOrbEntity spawnRestored(Position at, int amount, int age, int dimension) {
        Objects.requireNonNull(at, "at");
        if (amount <= 0) {
            return null;
        }
        ExperienceOrbEntity orb = new ExperienceOrbEntity(nextEntityId++, at, amount, 0);
        orb.setAge(age);
        orb.setDimension(dimension);
        orbs.add(orb);
        for (Listener listener : listeners) {
            listener.onOrbSpawned(orb);
        }
        return orb;
    }

    /**
     * The reference's own burst walk (Slice 65 — the bottle o' enchanting's
     * onCollision lines 35-41, ExperienceOrbEntity.roundSize): the total
     * splits through the roundSize ladder — each orb takes the biggest
     * tier the remainder still fits, until nothing is left. The historical
     * tier table: 2477, 1237, 617, 307, 149, 73, 37, 17, 7, 3, 1.
     */
    public List<ExperienceOrbEntity> spawnSplitBurst(Position position, int total) {
        return spawnSplitBurst(position, total, 0);
    }

    /**
     * The dimension-stamped split burst (Wave TF-10 — the bottle o'
     * enchanting's orbs land in the projectile's own world).
     */
    public List<ExperienceOrbEntity> spawnSplitBurst(Position position, int total, int dimension) {
        List<ExperienceOrbEntity> spawned = new ArrayList<>();
        int remaining = total;
        while (remaining > 0) {
            int share = roundSize(remaining);
            remaining -= share;
            ExperienceOrbEntity orb = new ExperienceOrbEntity(nextEntityId++,
                    new Position(position.x(), position.y() + 0.2, position.z()), share,
                    ExperienceOrbEntity.PICKUP_DELAY_TICKS);
            orb.setDimension(dimension);
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

    /** The manager's own ground consult (the VIII13 piston-push adapter reads it). */
    public ExperienceOrbEntity.Ground ground() {
        return ground;
    }

    /**
     * The piston push (the VIII13 additive families — the reference's
     * MovingBlockEntity.moveEntities lines 80-115): the immediate,
     * per-axis clamped shift through the orb's own item-model law, then
     * the move publication through the orb's own listener face — the
     * observers see the pushed orb the same tick, never waiting for
     * the next physics pass to notice the displacement. Tick-thread
     * context (the push surface's walk runs inside the moving blocks'
     * own tick).
     */
    public void pushOrb(ExperienceOrbEntity orb, double dx, double dy, double dz) {
        // Wave TF-10 — the push reads the orb's OWN world: the ground flips
        // to the body's dimension before the clamped shift (the TF-9 item
        // push's own shape).
        ground.setActiveDimension(orb.dimension());
        orb.pushBy(ground, dx, dy, dz);
        for (Listener listener : listeners) {
            listener.onOrbMoved(orb);
        }
    }

    /** @return the live orb with the engine id, or null (the seated-rider consult). */
    public ExperienceOrbEntity byId(int entityId) {
        for (ExperienceOrbEntity orb : orbs) {
            if (orb.entityId() == entityId) {
                return orb;
            }
        }
        return null;
    }

    public int size() {
        return orbs.size();
    }

    /** Advances all orbs one tick: physics, pickup, despawn, void. */
    public void tick(Iterable<PlayerSession> players) {
        currentPlayers = players;
        try {
            tickOrbs(players);
        } finally {
            currentPlayers = null;
        }
    }

    private void tickOrbs(Iterable<PlayerSession> players) {
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

            // Wave TF-10 — the reference's Entity.world: the shared ground
            // flips to the body's own dimension before its physics walk (the
            // TF-9 item family's own per-body flip, generalized).
            ground.setActiveDimension(orb.dimension());
            if (orb.tick(ground, random, sounds, this)) {
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
                // Wave TF-10 — the reference's same-world collision walk: a
                // player in another dimension never collects an orb at the
                // same coordinates.
                if (player.dimension() != orb.dimension()) {
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

    /**
     * The blast's orb wound (the reference's ExperienceOrbEntity.takeDamage
     * lines 141-151 over the explosion's own source): the health takes the
     * int-truncated quadratic damage and the body DIES at zero — removed
     * whole, no payout, the reference's own {@code health <= 0 → remove()}
     * shape.
     *
     * @return whether the body died (the caller's own ledger — the scatter
     *         lands regardless, exactly the reference's order).
     */
    public boolean hurtFromExplosion(ExperienceOrbEntity orb, float damage) {
        Objects.requireNonNull(orb, "orb");
        orb.takeExplosionDamage(damage);
        if (orb.health() <= 0) {
            remove(orb, "explosion");
            return true;
        }
        return false;
    }
}
