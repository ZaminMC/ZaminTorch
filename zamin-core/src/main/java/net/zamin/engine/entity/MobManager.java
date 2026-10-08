package net.zamin.engine.entity;

import net.zamin.api.ItemStack;
import net.zamin.api.Position;
import net.zamin.engine.item.ItemRoll;
import net.zamin.engine.player.PlayerSession;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Owns all living mobs of one world. Simulation-thread confined, exactly like
 * the item-entity system: creation, ticking, combat resolution and removal all
 * run on the world's owner, so no locks are needed and ordering matches the
 * tick (§447/§448 spirit).
 *
 * <p>Population is a maintainer, not a per-chunk spawner yet: passive kinds
 * top up in small groups near players up to a cap (the historical pack spawn
 * feel), zombies only appear at night and vanish at dawn (the historical
 * burn-light simplification — no fire rendering this slice), and anything far
 * from every player despawns. Mobs are deliberately NOT persisted across
 * restarts this slice (temporary decision, §146 pattern): the population
 * rebuilds on boot from spawn-area rolls.</p>
 */
public final class MobManager {

    private static final Logger LOGGER = Logger.getLogger(MobManager.class.getName());

    /** Passive mobs kept alive around the players (slice-scale cap). */
    public static final int PASSIVE_CAP = 12;
    /** Hostile mobs kept alive at night (slice-scale cap). */
    public static final int HOSTILE_CAP = 6;
    /** Maintainer cadence: rolls for spawns every 5 seconds. */
    public static final int MAINTAIN_PERIOD = 100;
    /** Despawn distance from every playing player (historical rule). */
    public static final double DESPAWN_DISTANCE = 96.0;
    /** Night window (vanilla day time): zombies spawn in this half of the cycle. */
    public static final long NIGHT_START = 13_000;
    public static final long NIGHT_END = 23_000;
    /** 1.8 day length in ticks (the world's own cycle). */
    public static final long DAY_LENGTH = 24_000;

    /** Where mob loot goes (the engine's item-entity system). */
    public interface LootSink {
        void spawnLootDrop(Position position, ItemStack stack);
    }

    /** Events the protocol adapter translates into wire updates. */
    public interface Listener {
        void onMobSpawned(MobEntity mob);

        void onMobMoved(MobEntity mob);

        /** Hurt animation + sound (attacker null for environmental damage). */
        void onMobHurt(MobEntity mob);

        /** The death animation starts (status 3 + death sound; loot follows removal). */
        void onMobDied(MobEntity mob);

        void onMobRemoved(MobEntity mob, String reason);

        /** The zombie landed a melee hit on a player (damage already validated). */
        void onMobAttackedPlayer(MobEntity mob, PlayerSession target, float damage);

        /** Ambient idle chatter. */
        void onMobSound(MobEntity mob, String soundName);
    }

    private final MobEntity.WorldQuery world;
    private final Random random;
    private final LootSink lootSink;
    private final List<MobEntity> mobs = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private int nextEntityId;
    private int maintainTimer;
    /** Surface finder for population rolls: topmost solid y at (x, z), or -1. */
    private final java.util.function.BiFunction<Integer, Integer, Integer> surfaceY;

    public MobManager(MobEntity.WorldQuery world, Random random, LootSink lootSink,
                      int firstEntityId,
                      java.util.function.BiFunction<Integer, Integer, Integer> surfaceY) {
        this.world = Objects.requireNonNull(world, "world");
        this.random = Objects.requireNonNull(random, "random");
        this.lootSink = Objects.requireNonNull(lootSink, "lootSink");
        this.nextEntityId = firstEntityId;
        this.surfaceY = Objects.requireNonNull(surfaceY, "surfaceY");
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** @return an unmodifiable view of live mobs (diagnostics/tests). */
    public List<MobEntity> all() {
        return List.copyOf(mobs);
    }

    public int size() {
        return mobs.size();
    }

    /** @return the live mob with the engine id, or null (no dead ids reused). */
    public MobEntity byId(int entityId) {
        for (MobEntity mob : mobs) {
            if (mob.entityId() == entityId) {
                return mob;
            }
        }
        return null;
    }

    /**
     * Spawns one mob of the kind at the exact position (validated by callers).
     * Tick-thread context.
     */
    public MobEntity spawnAt(MobType type, Position position) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        MobEntity mob = new MobEntity(nextEntityId++, type, position, random, world);
        mobs.add(mob);
        for (Listener listener : listeners) {
            listener.onMobSpawned(mob);
        }
        return mob;
    }

    /**
     * Spawns {@code count} mobs of the kind near the given center on the
     * surface (the command path). Tick-thread context.
     */
    public List<MobEntity> spawnGroup(MobType type, Position center, int count) {
        List<MobEntity> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int offsetX = random.nextInt(7) - 3;
            int offsetZ = random.nextInt(7) - 3;
            int x = (int) Math.floor(center.x()) + offsetX;
            int z = (int) Math.floor(center.z()) + offsetZ;
            int y = surfaceY.apply(x, z);
            if (y < 0) {
                continue; // no surface in that column (unloaded chunk etc.)
            }
            spawned.add(spawnAt(type, new Position(x + 0.5, y + 1.0, z + 0.5)));
        }
        return spawned;
    }

    /** Removes a specific mob (despawn policy, chunk unload later). */
    public void remove(MobEntity mob, String reason) {
        if (mobs.remove(mob)) {
            for (Listener listener : listeners) {
                listener.onMobRemoved(mob, reason);
            }
        }
    }

    /**
     * Fills the spawn area with passive packs at boot (no persistence: the
     * population rebuilds, the §146 pattern). Tick-thread context.
     */
    public void populateInitial(Position spawnCenter) {
        int packs = PASSIVE_CAP / 3;
        MobType[] passives = {MobType.PIG, MobType.COW, MobType.CHICKEN};
        for (int p = 0; p < packs; p++) {
            int dx = random.nextInt(61) - 30;
            int dz = random.nextInt(61) - 30;
            Position center = new Position(spawnCenter.x() + dx, spawnCenter.y(), spawnCenter.z() + dz);
            spawnGroup(passives[random.nextInt(passives.length)], center, 2 + random.nextInt(2));
        }
    }

    /**
     * Advances all mobs one tick: mind + body, zombie melee on players,
     * loot + removal for finished deaths, despawn and the population
     * maintainer. Tick-thread context.
     */
    public void tick(Iterable<PlayerSession> players, long timeOfDay) {
        // Population maintainer (before per-mob work so a fresh roll ticks next time).
        if (++maintainTimer >= MAINTAIN_PERIOD) {
            maintainTimer = 0;
            maintainPopulation(players, timeOfDay);
        }

        boolean night = timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;

        Iterator<MobEntity> iterator = mobs.iterator();
        while (iterator.hasNext()) {
            MobEntity mob = iterator.next();

            if (mob.deathAnimationFinished()) {
                iterator.remove();
                dropLoot(mob);
                for (Listener listener : listeners) {
                    listener.onMobRemoved(mob, "died");
                }
                continue;
            }

            if (mob.dying()) {
                mob.tick();
                continue;
            }

            // Daytime kills hostile mobs (no fire visuals this slice: they vanish).
            if (mob.type().hostile && !night) {
                iterator.remove();
                for (Listener listener : listeners) {
                    listener.onMobRemoved(mob, "dawn");
                }
                continue;
            }

            // Despawn: out of reach of every playing player (mobs idle when
            // nobody is online — the population waits for the next visitor).
            boolean anyPlaying = false;
            for (PlayerSession player : players) {
                if (player.state() == net.zamin.api.PlayerState.PLAYING) {
                    anyPlaying = true;
                    break;
                }
            }
            if (anyPlaying && outOfRangeOfAllPlayers(players, mob)) {
                iterator.remove();
                for (Listener listener : listeners) {
                    listener.onMobRemoved(mob, "despawned");
                }
                continue;
            }

            boolean moved = mob.tick();
            if (mob.consumePendingAttack()) {
                PlayerSession target = nearestPlayingPlayer(players, mob);
                if (target != null && target.health() > 0 && !target.dead()) {
                    for (Listener listener : listeners) {
                        listener.onMobAttackedPlayer(mob, target, MobEntity.ZOMBIE_ATTACK_DAMAGE);
                    }
                }
            }
            if (moved) {
                for (Listener listener : listeners) {
                    listener.onMobMoved(mob);
                }
            }
            if (mob.idleSoundDue()) {
                for (Listener listener : listeners) {
                    listener.onMobSound(mob, mob.type().idleSound);
                }
            }
        }
    }

    /**
     * Melee hit from a player: validated damage, knockback, hurt event; the
     * death animation starts here and loot lands when it finishes.
     * Tick-thread context.
     */
    public void hurt(MobEntity mob, float amount, double attackerYaw) {
        Objects.requireNonNull(mob, "mob");
        if (amount <= 0 || mob.dead()) {
            return;
        }
        mob.hurt(amount);
        mob.knockbackFrom(attackerYaw);
        for (Listener listener : listeners) {
            listener.onMobHurt(mob);
        }
        if (mob.health() <= 0) {
            for (Listener listener : listeners) {
                listener.onMobDied(mob);
            }
        }
    }

    /** Rolls the kind's loot table into the world at the body. */
    private void dropLoot(MobEntity mob) {
        Position at = mob.position();
        for (ItemRoll roll : mob.type().loot) {
            int count = roll.roll(random);
            if (count <= 0) {
                continue;
            }
            lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                    ItemStack.of(roll.type(), count));
        }
    }

    private void maintainPopulation(Iterable<PlayerSession> players, long timeOfDay) {
        int passive = 0;
        int hostile = 0;
        for (MobEntity mob : mobs) {
            if (mob.type().hostile) {
                hostile++;
            } else {
                passive++;
            }
        }
        boolean night = timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;
        PlayerSession anchor = randomPlayingPlayer(players);
        if (anchor == null) {
            return; // nobody watching: no spawning
        }
        if (passive < PASSIVE_CAP && random.nextInt(100) < 40) {
            int deficit = PASSIVE_CAP - passive;
            spawnGroup(randomPassiveType(), anchor.position(),
                    Math.min(deficit, 2 + random.nextInt(2)));
        }
        if (night && hostile < HOSTILE_CAP && random.nextInt(100) < 50) {
            int deficit = HOSTILE_CAP - hostile;
            spawnGroup(MobType.ZOMBIE, anchor.position(),
                    Math.min(deficit, 1 + random.nextInt(2)));
        }
    }

    private MobType randomPassiveType() {
        MobType[] passives = {MobType.PIG, MobType.COW, MobType.CHICKEN};
        return passives[random.nextInt(passives.length)];
    }

    private boolean outOfRangeOfAllPlayers(Iterable<PlayerSession> players, MobEntity mob) {
        for (PlayerSession player : players) {
            if (player.state() != net.zamin.api.PlayerState.PLAYING) {
                continue;
            }
            Position playerPosition = player.position();
            double dx = playerPosition.x() - mob.position().x();
            double dy = playerPosition.y() - mob.position().y();
            double dz = playerPosition.z() - mob.position().z();
            if (dx * dx + dy * dy + dz * dz <= DESPAWN_DISTANCE * DESPAWN_DISTANCE) {
                return false;
            }
        }
        return true;
    }

    private PlayerSession nearestPlayingPlayer(Iterable<PlayerSession> players, MobEntity mob) {
        PlayerSession best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PlayerSession player : players) {
            if (player.state() != net.zamin.api.PlayerState.PLAYING || player.dead()) {
                continue;
            }
            double dx = player.position().x() - mob.position().x();
            double dy = player.position().y() - mob.position().y();
            double dz = player.position().z() - mob.position().z();
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private PlayerSession randomPlayingPlayer(Iterable<PlayerSession> players) {
        List<PlayerSession> playing = new ArrayList<>();
        for (PlayerSession player : players) {
            if (player.state() == net.zamin.api.PlayerState.PLAYING && !player.dead()) {
                playing.add(player);
            }
        }
        if (playing.isEmpty()) {
            return null;
        }
        return playing.get(random.nextInt(playing.size()));
    }
}
