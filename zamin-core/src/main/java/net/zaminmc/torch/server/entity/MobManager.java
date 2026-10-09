package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.entity.PlayerState;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.item.ItemRoll;
import net.zaminmc.torch.server.player.PlayerSession;

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

        /** The body moved; the rider session rides along for the seat re-anchor (null when none). */
        void onMobMoved(MobEntity mob, PlayerSession rider);

        /** Hurt animation + sound (attacker null for environmental damage). */
        void onMobHurt(MobEntity mob);

        /** The death animation starts (status 3 + death sound; loot follows removal). */
        void onMobDied(MobEntity mob);

        void onMobRemoved(MobEntity mob, String reason);

        /** The living-flags burn bit changed (the fire metadata to observers). */
        default void onMobBurningChanged(MobEntity mob, boolean burning) {
        }

        /** A melee hunter landed a hit on a player (damage already validated). */
        void onMobAttackedPlayer(MobEntity mob, PlayerSession target, float damage);

        /** Ambient idle chatter. */
        void onMobSound(MobEntity mob, String soundName);

        /** A skeleton loosed an arrow at the aim point (the launch is the engine's). */
        void onMobRangedAttack(MobEntity mob, Position aimPoint);

        /** A creeper's prime state flipped (swelling on/off — the wire metadata). */
        void onMobFuseChanged(MobEntity mob, boolean priming);

        /** A shear took woolCount wool off a sheep. */
        void onMobSheared(MobEntity mob, int woolCount);

        /** A sheep's coat regrew (the wire metadata clears the sheared bit). */
        void onMobCoatRegrown(MobEntity mob);

        /** A primed creeper went off (the manager already removed it). */
        void onMobExploded(MobEntity mob);

        /** A player took a mount's seat (the wire Attach Entity + posture). */
        default void onMobMounted(MobEntity mob, int riderEngineId) {
        }

        /** A rider left (or was thrown off) the seat; the exit position rides along. */
        default void onMobDismounted(MobEntity mob, int riderEngineId,
                                     Position exit, boolean thrown) {
        }

        /** The horse's index-16 flag word changed (tame/saddle/armor/rear/eat). */
        default void onHorseFlagsChanged(MobEntity mob) {
        }

        /**
         * A player opened a mount's inventory: the adapter sends Open Window
         * ("EntityHorse" + the trailing mount id) then the slot contents.
         */
        default void onHorseInventoryOpened(PlayerSession player, MobEntity mob, int windowId) {
        }
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
     * The projectile hit query: the mob whose body box contains the point
     * (feet-anchored, type width/height), or null. Tick-thread context.
     */
    public MobEntity mobAt(Position point) {
        for (MobEntity mob : mobs) {
            if (mob.dead()) {
                continue;
            }
            Position p = mob.position();
            double halfWidth = mob.type().width / 2.0;
            double dx = Math.abs(point.x() - p.x());
            double dz = Math.abs(point.z() - p.z());
            double dy = point.y() - p.y();
            if (dx <= halfWidth && dz <= halfWidth && dy >= -0.1 && dy <= mob.type().height) {
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
        return spawnGroupAt(type, center, count, 0, 3);
    }

    /**
     * Spawns a group offset randomly from the center within
     * {@code [minOffset, maxOffset]} blocks. Population maintenance keeps
     * mobs AWAY from the player (the historical pack distance: 8-20 blocks
     * out) — spawning on top of the anchor put pigs on the building site and
     * blocked wire interactions there.
     */
    public List<MobEntity> spawnGroupAt(MobType type, Position center, int count,
                                        int minOffset, int maxOffset) {
        List<MobEntity> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // Ring spawn: a random bearing at a distance inside the band —
            // never closer than minOffset to the anchor.
            double bearing = random.nextDouble() * Math.PI * 2.0;
            int distance = minOffset + random.nextInt(maxOffset - minOffset + 1);
            int x = (int) Math.floor(center.x()) + (int) Math.round(Math.cos(bearing) * distance);
            int z = (int) Math.floor(center.z()) + (int) Math.round(Math.sin(bearing) * distance);
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
        MobType[] passives = {MobType.PIG, MobType.COW, MobType.CHICKEN,
                MobType.SHEEP, MobType.HORSE};
        for (int p = 0; p < packs; p++) {
            int dx = random.nextInt(61) - 30;
            int dz = random.nextInt(61) - 30;
            Position center = new Position(spawnCenter.x() + dx, spawnCenter.y(), spawnCenter.z() + dz);
            spawnGroup(passives[random.nextInt(passives.length)], center, 2 + random.nextInt(2));
        }
    }

    /**
     * Shears a sheep on a player's behalf: rolls 1-3 wool, strips the coat,
     * and reports through {@code onMobSheared} (the caller spawns the drops).
     * Returns the wool count (0 when the kind is bald or already shorn).
     * Tick-thread context.
     */
    public int shear(MobEntity mob) {
        Objects.requireNonNull(mob, "mob");
        if (!mob.shear()) {
            return 0;
        }
        int wool = 1 + random.nextInt(3);
        for (Listener listener : listeners) {
            listener.onMobSheared(mob, wool);
        }
        return wool;
    }

    // ------------------------------------------------ mounts (the horse/pig slice)

    /**
     * Seats a player on a mount: the vanilla temper flow for an untamed
     * horse (tame-and-seat, seat-and-buck, or a plain seat), a direct seat
     * for pigs and tamed horses. Returns whether the rider is in the seat.
     * Tick-thread context.
     */
    public boolean mountMob(MobEntity mob, int riderEngineId) {
        Objects.requireNonNull(mob, "mob");
        if (!mob.isMountable() || mob.hasRider() || mob.dead()) {
            return false;
        }
        MobEntity.MountAttempt attempt = mob.attemptMount();
        mob.setRider(riderEngineId);
        for (Listener listener : listeners) {
            listener.onMobMounted(mob, riderEngineId);
        }
        if (attempt == MobEntity.MountAttempt.TAMED) {
            for (Listener listener : listeners) {
                listener.onHorseFlagsChanged(mob); // the tame bit flipped
            }
        }
        if (attempt == MobEntity.MountAttempt.BUCKED) {
            for (Listener listener : listeners) {
                listener.onHorseFlagsChanged(mob); // the rear flag went up
            }
        }
        return true;
    }

    /**
     * Opens the seat: the rider steps out beside the mount (offset toward
     * the mount's left flank, snapped to the ground) — the vehicle rule.
     * {@code thrown} marks the buck throw (the exit carries a pop).
     * Tick-thread context.
     */
    public void dismountMob(MobEntity mob, boolean thrown) {
        Objects.requireNonNull(mob, "mob");
        if (!mob.hasRider()) {
            return;
        }
        int rider = mob.riderId();
        mob.clearRider();
        Position at = mob.position();
        Position exit = new Position(at.x() + 1.1, at.y() + 0.3, at.z());
        for (Listener listener : listeners) {
            listener.onMobDismounted(mob, rider, exit, thrown);
        }
    }

    /**
     * The rider's rein input lands on the mount. The manager owns the
     * control gate: an unsaddled horse and a pig without the carrot on a
     * stick answer nothing (the manager zeroes the forward). Tick-thread.
     */
    public void steerRidden(MobEntity mob, float sideways, float forward,
                            boolean jump, boolean riderControls) {
        mob.steer(sideways, riderControls ? forward : 0.0f, jump);
    }

    /** @return whether a rider currently holds the mount's seat. */
    public boolean mountHasRider(MobEntity mob) {
        return mob.hasRider();
    }

    /**
     * Advances all mobs one tick: mind + body, melee, arrows, fuses, loot
     * + removal for finished deaths, despawn and the population maintainer.
     * Tick-thread context.
     */
    public void tick(Iterable<PlayerSession> players, long timeOfDay) {
        // Population maintainer (before per-mob work so a fresh roll ticks next time).
        if (++maintainTimer >= MAINTAIN_PERIOD) {
            maintainTimer = 0;
            maintainPopulation(players, timeOfDay);
        }

        boolean night = isNight(timeOfDay);

        Iterator<MobEntity> iterator = mobs.iterator();
        while (iterator.hasNext()) {
            MobEntity mob = iterator.next();

            if (mob.deathAnimationFinished()) {
                iterator.remove();
                for (Listener listener : listeners) {
                    listener.onMobRemoved(mob, "died");
                }
                dropLoot(mob); // the loot lands after the body leaves the wire
                continue;
            }

            // A dying mount opens the seat first (no ghost riders on corpses).
            if (mob.dying() && mob.hasRider()) {
                dismountMob(mob, false);
            }
            if (mob.dying()) {
                mob.tick(night);
                continue;
            }

            // Daylight: the undead ignite (the historical sunlight burn —
            // re-armed every daylight tick until the fire consumes them); the
            // day-neutral spider just stops hunting; creepers are sun-proof.
            if (mob.type().hostile && !mob.type().traits.neutralByDay() && !night) {
                if (mob.type().undead()) {
                    boolean wasBurning = mob.burning();
                    mob.ignite(MobEntity.FIRE_TICKS);
                    if (!wasBurning) {
                        for (Listener listener : listeners) {
                            listener.onMobBurningChanged(mob, true);
                        }
                    }
                }
            }

            // Despawn: out of reach of every playing player (mobs idle when
            // nobody is online — the population waits for the next visitor).
            boolean anyPlaying = false;
            for (PlayerSession player : players) {
                if (player.state() == net.zaminmc.torch.entity.PlayerState.PLAYING) {
                    anyPlaying = true;
                    break;
                }
            }
            if (anyPlaying && outOfRangeOfAllPlayers(players, mob)) {
                if (mob.hasRider()) {
                    dismountMob(mob, false);
                }
                iterator.remove();
                for (Listener listener : listeners) {
                    listener.onMobRemoved(mob, "despawned");
                }
                continue;
            }

            boolean wasPriming = mob.fuseActive();
            boolean wasBurning = mob.burning();
            int wasHorseFlags = mob.horseFlagsRaw();
            boolean moved = mob.tick(night);
            if (wasPriming != mob.fuseActive()) {
                for (Listener listener : listeners) {
                    listener.onMobFuseChanged(mob, mob.fuseActive());
                }
            }
            if (wasBurning != mob.burning()) {
                for (Listener listener : listeners) {
                    listener.onMobBurningChanged(mob, mob.burning());
                }
            }
            if (wasHorseFlags != mob.horseFlagsRaw()) {
                for (Listener listener : listeners) {
                    listener.onHorseFlagsChanged(mob);
                }
            }
            if (mob.consumeBuckThrow()) {
                dismountMob(mob, true); // the rear-and-throw (the exit is a pop)
            }
            if (mob.consumeRegrown()) {
                for (Listener listener : listeners) {
                    listener.onMobCoatRegrown(mob);
                }
            }
            if (mob.consumePendingRangedShot()) {
                Position aim = mob.rangedTarget();
                mob.clearRangedTarget();
                for (Listener listener : listeners) {
                    listener.onMobRangedAttack(mob, aim);
                }
            }
            if (mob.consumePendingExplosion()) {
                // The blast replaces the body: no death animation, no loot.
                if (mob.hasRider()) {
                    dismountMob(mob, true); // the blast throws the rider
                }
                iterator.remove();
                for (Listener listener : listeners) {
                    listener.onMobRemoved(mob, "exploded");
                }
                for (Listener listener : listeners) {
                    listener.onMobExploded(mob);
                }
                continue;
            }
            if (mob.consumePendingMeleeAttack()) {
                PlayerSession target = nearestPlayingPlayer(players, mob);
                if (target != null && target.health() > 0 && !target.dead()) {
                    for (Listener listener : listeners) {
                        listener.onMobAttackedPlayer(mob, target, MobEntity.MELEE_ATTACK_DAMAGE);
                    }
                }
            }
            if (moved) {
                PlayerSession rider = mob.hasRider() ? sessionOfRider(players, mob.riderId()) : null;
                for (Listener listener : listeners) {
                    listener.onMobMoved(mob, rider);
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
     * The persistable view of one live mob (ZMD payload, version 1): kind,
     * position, facing and remaining health. Dying/dead mobs are not captured
     * — their loot lands on the tick that finishes the death animation.
     */
    public record MobSnapshot(String type, double x, double y, double z, float yaw, float health) {
    }

    /**
     * Restores the saved population. Saved kinds the current registry cannot
     * resolve are dropped with a warning — the registry moved on. Returns
     * whether any mob was restored (a fresh world boots its own packs).
     * Tick-thread context.
     */
    public boolean restoreAll(List<MobSnapshot> saved) {
        Objects.requireNonNull(saved, "saved");
        boolean any = false;
        for (MobSnapshot snapshot : saved) {
            MobType type;
            try {
                type = MobType.valueOf(snapshot.type());
            } catch (IllegalArgumentException unknownKind) {
                LOGGER.warning("Saved mob kind no longer registered, dropped: " + snapshot.type());
                continue;
            }
            MobEntity mob = spawnAt(type, new Position(snapshot.x(), snapshot.y(), snapshot.z()));
            mob.restore(snapshot.health(), snapshot.yaw());
            any = true;
        }
        return any;
    }

    /** @return the persistable snapshot of every living mob. Tick-thread context. */
    public List<MobSnapshot> snapshot() {
        List<MobSnapshot> saved = new ArrayList<>();
        for (MobEntity mob : mobs) {
            if (mob.dead()) {
                continue; // dying bodies and their loot resolve on the live tick
            }
            Position position = mob.position();
            saved.add(new MobSnapshot(mob.type().name(), position.x(), position.y(),
                    position.z(), mob.yaw(), mob.health()));
        }
        return saved;
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

    /**@return whether the night window is active at the given time of day. */
    public static boolean isNight(long timeOfDay) {
        return timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;
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
        boolean night = isNight(timeOfDay);
        PlayerSession anchor = randomPlayingPlayer(players);
        if (anchor == null) {
            return; // nobody watching: no spawning
        }
        if (passive < PASSIVE_CAP && random.nextInt(100) < 40) {
            int deficit = PASSIVE_CAP - passive;
            spawnGroupAt(randomPassiveType(), anchor.position(),
                    Math.min(deficit, 2 + random.nextInt(2)), 8, 20);
        }
        if (night && hostile < HOSTILE_CAP && random.nextInt(100) < 50) {
            // The night raid: zombies crowd, skeletons cover, spiders crawl.
            MobType kind = random.nextInt(100) < 50 ? MobType.ZOMBIE
                    : random.nextInt(100) < 60 ? MobType.SKELETON : MobType.SPIDER;
            spawnGroupAt(kind, anchor.position(),
                    Math.min(HOSTILE_CAP - hostile, 1 + random.nextInt(2)), 8, 20);
        }
    }

    private MobType randomPassiveType() {
        MobType[] passives = {MobType.PIG, MobType.COW, MobType.CHICKEN,
                MobType.SHEEP, MobType.HORSE};
        return passives[random.nextInt(passives.length)];
    }

    private boolean outOfRangeOfAllPlayers(Iterable<PlayerSession> players, MobEntity mob) {
        for (PlayerSession player : players) {
            if (player.state() != net.zaminmc.torch.entity.PlayerState.PLAYING) {
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
            if (player.state() != net.zaminmc.torch.entity.PlayerState.PLAYING || player.dead()) {
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
            if (player.state() == net.zaminmc.torch.entity.PlayerState.PLAYING && !player.dead()) {
                playing.add(player);
            }
        }
        if (playing.isEmpty()) {
            return null;
        }
        return playing.get(random.nextInt(playing.size()));
    }

    /** @return the playing session whose engine id matches the rider id, or null. */
    private PlayerSession sessionOfRider(Iterable<PlayerSession> players, int riderEngineId) {
        for (PlayerSession player : players) {
            if (player.engineEntityId() == riderEngineId
                    && player.state() == net.zaminmc.torch.entity.PlayerState.PLAYING) {
                return player;
            }
        }
        return null;
    }
}
