package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.entity.PlayerState;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.item.BuiltinItems;
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
    /** The far-despawn radius (Slice 11d — the reference's own 128). */
    public static final double DESPAWN_DISTANCE = 128.0;
    /** Night window (vanilla day time): zombies spawn in this half of the cycle. */
    public static final long NIGHT_START = 13_000;
    public static final long NIGHT_END = 23_000;
    /** 1.8 day length in ticks (the world's own cycle). */
    public static final long DAY_LENGTH = 24_000;

    /**
     * The equipment drop chance (MobEntity's own initEquipmentDropChances
     * line 83: the flat 0.085F every slot carries).
     */
    private static final float EQUIPMENT_DROP_CHANCE = 0.085f;

    /** The rare-drop roll's base (LivingEntity.die line 766: 0.025F). */
    private static final float RARE_DROP_BASE = 0.025f;

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

        /**
         * The ocelot's posture bits changed (the shared flags byte at
         * index 0: the tempt's sneak 0x02, the flee's sprint 0x10).
         */
        default void onMobPostureChanged(MobEntity mob) {
        }

        /**
         * The ghast's charging flag flipped (the synced slot 16 byte —
         * the client raises the mouth open from it).
         */
        default void onMobGhastChargingChanged(MobEntity mob) {
        }

        /**
         * The blaze's fire-active flag flipped (BlazeEntity's synced slot
         * 16 byte — the client raises the burning rods from it).
         */
        default void onMobBlazeFireChanged(MobEntity mob) {
        }

        /**
         * The blaze loosed one volley fireball at its target (the launch
         * rides the engine's projectile system; the 1009 shot event lands
         * with it — AttackGoal lines 230-244).
         */
        default void onMobBlazeFireball(MobEntity mob) {
        }

        /**
         * The silverfish's wake-up scan expired (WakeUpFriendsGoal.tick
         * lines 210-236): the engine walks the 10/5/10 box and breaks
         * every monster egg it finds (each break spawning its silverfish),
         * the reference's own 50% early stop.
         */
        default void onSilverfishWake(MobEntity mob) {
        }

        /**
         * The silverfish's infest roll fired (InfestStoneGoal.start lines
         * 171-187): the engine writes the egg for the adjacent host block
         * and removes the body (the reference's animateSpawn + remove).
         */
        default void onSilverfishInfest(MobEntity mob) {
        }

        /**
         * The enderman's whole wire state flipped (Slice 19d — the
         * carried block's short at the synced slot 16 and the screaming
         * byte at 18: the client draws the held block and swaps the
         * jaw from it).
         */
        default void onEndermanStateChanged(MobEntity mob) {
        }

        /**
         * The enderman lifted a block (PickUpBlockGoal.tick lines
         * 452-465): the intent's cell — the engine writes air and the
         * body takes the block.
         */
        default void onEndermanPickup(MobEntity mob) {
        }

        /**
         * The enderman set its carried block down (PlaceBlockGoal.tick
         * lines 483-496): the intent's cell — the engine writes the
         * carried block and the hands empty.
         */
        default void onEndermanPlace(MobEntity mob) {
        }

        /**
         * The stare scream fired (LookAtPlayerGoal.tick line 412 — the
         * creepy window's end): the mob.endermen.stare sound at the
         * body.
         */
        default void onEndermanStare(MobEntity mob) {
        }

        /**
         * The ghast loosed its charged fireball at the target (the launch
         * rides the engine's projectile system; the 1008 launch sound
         * lands with it).
         */
        default void onMobGhastFireball(MobEntity mob, Position target) {
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

        /**
         * A player opened a villager's trading window: the adapter sends Open
         * Window ("minecraft:villager") then the MC|TrList plugin message.
         */
        default void onVillagerTradeOpened(PlayerSession player, MobEntity villager, int windowId) {
        }

        /** A trade row was selected: the result slot syncs (the Set Slot). */
        default void onTradeSelected(PlayerSession player, int windowId, ItemStack result) {
        }

        /**
         * A loved body burst hearts (the event-18 love burst at feeding, and
         * the breed celebration — the wire renders hearts around the body).
         */
        default void onMobLoveBurst(MobEntity mob) {
        }

        /** A baby crossed into adulthood (the age metadata byte flips to 0). */
        default void onMobGrewUp(MobEntity mob) {
        }

        /**
         * A breed landed: the baby already exists in the manager (spawned
         * before the event), the parents carry their 6000-tick cooldown.
         */
        default void onMobBred(MobEntity parent, MobEntity mate, MobEntity baby) {
        }

        /**
         * The witch's drinking flag flipped (the index-21 metadata byte —
         * the client raises the drinking pose and the held potion).
         */
        default void onMobWitchDrinkingChanged(MobEntity mob, boolean drinking) {
        }

        /**
         * The witch's idle spell-swirl roll fired (the reference's
         * {@code doEntityEvent(this, (byte)15)} — the Entity Event packet
         * the client answers with the purple particles).
         */
        default void onMobWitchAura(MobEntity mob) {
        }

        /**
         * A mob's own sound with the reference's volume/pitch pair (the
         * slime's squish family: the jump and the landing rides roll
         * their own pitch — getSoundVolume 0.4*size, the pitch band
         * {@code (rand - rand) * 0.2 + 1}).
         */
        default void onMobSoundAt(MobEntity mob, String soundName,
                                  float volume, float pitch) {
        }

        /** The wolf's tamed/sitting/angry/collar state moved (the metadata update). */
        default void onWolfStateChanged(MobEntity mob) {
        }

        /**
         * The snow golem's volley (Slice 17d — SnowGolemEntity.
         * doRangedAttack lines 89-96): the 20-tick snowball at the hostile
         * target — the engine's projectile system owns the launch.
         */
        default void onMobRangedAttackMob(MobEntity shooter, MobEntity target) {
        }

        /** The world's doEntityEvent byte (7 hearts, 6 smoke, 18 love...). */
        default void onMobStatusEvent(MobEntity mob, int status) {
        }

        /**
         * The guardian's state moved (Slice 17a — the wire's guardian
         * block: the index-16 flag word's moving/elder bits and the
         * index-17 beam target id; the Set Entity Metadata republishes
         * the whole pair).
         */
        default void onGuardianStateChanged(MobEntity mob) {
        }

        /**
         * The guardian's laser completed (Slice 17a — the charged beam's
         * hit: the magic half pierces armor with no knockback, the melee
         * half walks the full armor/enchantment path — the pair the
         * reference's own {@code takeDamage} ladder lands).
         */
        default void onMobGuardianLaserHit(MobEntity mob, PlayerSession target,
                                           float magicDamage, float meleeDamage) {
        }

        /**
         * The elder's aura cycle fired (Slice 17a — mobAiTick's 1200-tick
         * cadence: the survival players within 50 blocks take the
         * Mining Fatigue III / 6000-tick curse, the weaker rows only).
         */
        default void onMobGuardianAura(MobEntity elder) {
        }
    }

    private final MobEntity.WorldQuery world;
    private final Random random;
    private final LootSink lootSink;
    private final List<MobEntity> mobs = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private int nextEntityId;
    /** Surface finder for population rolls: topmost solid y at (x, z), or -1. */
    private final java.util.function.BiFunction<Integer, Integer, Integer> surfaceY;
    /** The potion system (the witch's drink application); null until wired. */
    private volatile net.zaminmc.torch.server.potion.PotionSystem potionSystem;
    /** The owner-lane admission for goal registrations (§27.6); null until wired. */
    private volatile java.util.function.Consumer<Runnable> goalAdmission;

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

    /** Wires the potion system (the witch's drink lands its effects through it). */
    public void setPotionSystem(net.zaminmc.torch.server.potion.PotionSystem potionSystem) {
        this.potionSystem = potionSystem;
    }

    /**
     * Wires the owner-lane admission every spawned body's mind mutations
     * ride (§27.6's explicit command protocol — the world's {@code execute}).
     * Called once by the server after the world's admission exists.
     */
    public void setGoalAdmission(java.util.function.Consumer<Runnable> admission) {
        this.goalAdmission = admission;
    }

    /**
     * Wires the public event bus (§27.7): every spawn fires
     * {@link net.zaminmc.torch.event.entity.MobSpawnEvent} before the entity
     * joins the world; a cancelled spawn returns null and consumes no id.
     */
    public void setEventBus(net.zaminmc.torch.server.event.EngineEventBus eventBus) {
        this.eventBus = eventBus;
    }

    private volatile net.zaminmc.torch.server.event.EngineEventBus eventBus;

    /** @return an unmodifiable view of live mobs (diagnostics/tests). */
    public List<MobEntity> all() {
        return List.copyOf(mobs);
    }

    public int size() {
        return mobs.size();
    }

    /**
     * The shared body random (the stream every mob ticks on — the
     * reference's per-entity Random carries over as the engine's one
     * shared stream): exposed so the engine-side arms (the witch's
     * weakness roll) keep drawing from the same sequence.
     */
    public Random random() {
        return random;
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
     * The structure-spawn idempotence probe (Slice 12b): whether a villager
     * already stands within the radius of the position — the populate-time
     * drain skips records whose villager a previous server life already
     * materialized and the mob store restored (the chunks regenerate
     * deterministically; the villagers persist).
     */
    public boolean villagerNear(double x, double y, double z, double radius) {
        return mobOfKindNear(net.zaminmc.torch.server.entity.MobType.VILLAGER, x, y, z, radius);
    }

    /**
     * The kind-aware near probe (the populate drains' idempotence guard):
     * whether a mob of the kind stands within the radius of the position.
     * The animal seeding uses the pack-cohesion radius (8 — a group stays
     * within a few blocks; the guard holds the restart re-drain, the same
     * accepted class as the villagers' own 1.0 note).
     */
    public boolean mobOfKindNear(net.zaminmc.torch.server.entity.MobType kind,
                                 double x, double y, double z, double radius) {
        for (MobEntity mob : mobs) {
            if (mob.type() != kind || mob.dead()) {
                continue;
            }
            Position at = mob.position();
            double dx = at.x() - x;
            double dy = at.y() - y;
            double dz = at.z() - z;
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    /**
     * Spawns one mob of the kind at the exact position (validated by callers).
     * Tick-thread context.
     *
     * @return the spawned mob, or null when the public
     *         {@link net.zaminmc.torch.event.entity.MobSpawnEvent} cancelled
     *         the spawn (no id consumed, no listeners notified)
     */
    public MobEntity spawnAt(MobType type, Position position) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        var bus = eventBus;
        if (bus != null) {
            var event = new net.zaminmc.torch.event.entity.MobSpawnEvent(
                    type.identifier().toString(), position);
            bus.fire(event);
            if (event.cancelled()) {
                return null;
            }
        }
        MobEntity mob = new MobEntity(nextEntityId++, type, position, random, world);
        mob.setBreedFinder(this::scanForMate);
        mob.setRemovalHook(() -> remove(mob, "api")); // the public despawn path
        mob.setGoalAdmission(goalAdmission); // §27.6: mind mutations admit onto the owner lane
        if (type == MobType.SKELETON) {
            // SkeletonEntity.initialize's else arm (line 224 →
            // addRandomEquipment line 211): every archer spawns with the
            // bow in hand — the nether's wither conversion replaces it
            // with the stone sword at its own roll below.
            mob.setHeldEquipment(ItemStack.of(BuiltinItems.BOW, 1));
        }
        if (type == MobType.WITCH) {
            // The drink ladder's hasStatusEffect gates (mobTick lines
            // 107-121): the live store read through the potion system.
            mob.setWitchEffectQuery(effectId -> witchHasEffect(mob, effectId));
        }
        mobs.add(mob);
        for (Listener listener : listeners) {
            listener.onMobSpawned(mob);
        }
        return mob;
    }

    /**
     * The breed goal's mate scan ({@code World.getEntitiesOfType} + the
     * nearest-{@code canBreedWith} walk): the closest legal partner inside
     * the grown-8 box, distance measured in the vanilla 3D squared form.
     */
    private MobEntity scanForMate(MobEntity seeker) {
        MobEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        double r = MobEntity.BREED_MATE_SCAN_RADIUS;
        for (MobEntity other : mobs) {
            if (other == seeker || !seeker.canBreedWith(other)) {
                continue;
            }
            Position a = seeker.position();
            Position b = other.position();
            if (Math.abs(a.x() - b.x()) > r || Math.abs(a.y() - b.y()) > r
                    || Math.abs(a.z() - b.z()) > r) {
                continue; // outside the grown box (the vanilla shape gate)
            }
            double dx = a.x() - b.x();
            double dy = a.y() - b.y();
            double dz = a.z() - b.z();
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
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
            MobEntity mob = spawnAt(type, new Position(x + 0.5, y + 1.0, z + 0.5));
            if (mob != null) {
                spawned.add(mob); // a cancelled member simply never joins the pack
            }
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
     * The peaceful-difficulty rule: every hostile leaves the world at once
     * (the historical /difficulty peaceful sweep). Tick-thread context.
     */
    public void removeHostiles() {
        for (MobEntity mob : new java.util.ArrayList<>(mobs)) {
            if (mob.type().hostile) {
                remove(mob, "peaceful");
            }
        }
    }

    // (populateInitial removed — Slice 12h: the fresh world's animals arrive
    // through the vanilla worldgen seeding, NaturalSpawner.populateChunk's
    // engine port, not the synthetic spawn packs.)

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
     * + removal for finished deaths, despawn and the natural spawn cycle
     * (the NaturalSpawnSystem walks after this — the engine wires it).
     * Tick-thread context.
     */
    public void tick(Iterable<PlayerSession> players, long timeOfDay) {
        boolean night = isNight(timeOfDay);

        // The breeds defer to the loop's end: a baby spawned mid-iteration
        // would add to the iterated list (the ConcurrentModification trap).
        List<MobEntity[]> pendingBreeds = new ArrayList<>();
        // The slime splits defer the same way (the children join after the
        // iteration closes — the reference's remove() runs inside its own
        // removal walk, the engine's tick loop owns the equivalent).
        List<MobEntity> pendingSlimeSplits = new ArrayList<>();

        Iterator<MobEntity> iterator = mobs.iterator();
        while (iterator.hasNext()) {
            MobEntity mob = iterator.next();

            if (mob.deathAnimationFinished()) {
                iterator.remove();
                for (Listener listener : listeners) {
                    listener.onMobRemoved(mob, "died");
                }
                dropLoot(mob); // the loot lands after the body leaves the wire
                if (mob.type() == MobType.SLIME && mob.slimeSize() > 1) {
                    pendingSlimeSplits.add(mob); // the death split (12k)
                }
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
            // The wither skeleton never joins: it only exists in the nether,
            // where the reference's own hasSkyAccess gate reads false (the
            // engine's dimension-blind sweep's own wither arm, Slice 18d).
            if (mob.type().hostile && !mob.type().traits.neutralByDay() && !night) {
                if (mob.type().undead() && !mob.skeletonWither()) {
                    boolean wasBurning = mob.burning();
                    mob.ignite(MobEntity.FIRE_TICKS);
                    if (!wasBurning) {
                        for (Listener listener : listeners) {
                            listener.onMobBurningChanged(mob, true);
                        }
                    }
                }
            }

            // Despawn (Slice 11d — MobEntity.checkDespawn lines 417-438,
            // 1:1): the persistent never leave (the leashed family); the
            // monsters among the rest leave past 128 blocks at once and
            // past the 600-tick far window roll the 1/800 idle despawn
            // beyond 32 blocks; the ANIMALS never despawn at all
            // (AnimalEntity's own canDespawn() = false, lines 104-106 —
            // a penned cow stays penned). Nobody online: no player to
            // measure — the mob waits (the reference's own
            // getNearestPlayer-null guard, the window still counting).
            PlayerSession despawnNearest = null;
            double despawnSquared = Double.MAX_VALUE;
            for (PlayerSession player : players) {
                if (player.state() != net.zaminmc.torch.entity.PlayerState.PLAYING
                        || player.dead()) {
                    continue;
                }
                Position p = player.position();
                double dx = p.x() - mob.position().x();
                double dy = p.y() - mob.position().y();
                double dz = p.z() - mob.position().z();
                double squared = dx * dx + dy * dy + dz * dz;
                if (squared < despawnSquared) {
                    despawnSquared = squared;
                    despawnNearest = player;
                }
            }
            mob.tickDespawnWindow(mob.persistent(),
                    despawnNearest == null ? null : despawnSquared);
            if (despawnNearest != null && net.zaminmc.torch.server.spawn.NaturalSpawnSystem.shouldDespawn(
                    mob.persistent(), mob.canDespawn(), despawnSquared,
                    mob.farFromPlayerTicks(), random)) {
                if (mob.hasRider()) {
                    dismountMob(mob, false);
                }
                iterator.remove();
                for (Listener listener : listeners) {
                    listener.onMobRemoved(mob, "despawned");
                }
                continue;
            }

            // The breeding clocks (the vanilla love/age/breed-goal trio).
            tickBreedingFor(mob);

            boolean wasPriming = mob.fuseActive();
            boolean wasBurning = mob.burning();
            int wasHorseFlags = mob.horseFlagsRaw();
            boolean wasWitchDrinking = mob.witchDrinking();
            boolean wasBegging = mob.wolfBegging();
            int wasPosture = mob.postureFlags();
            boolean wasGhastCharging = mob.ghastCharging();
            boolean wasGuardianMoving = mob.guardianMoving();
            boolean wasGuardianBeam = mob.guardianBeamTargetId() != 0;
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
            if (wasBegging != mob.wolfBegging()) {
                // WolfBegGoal's start/stop pair rides the whole wolf-state
                // block (the begging byte 19 sits inside it).
                for (Listener listener : listeners) {
                    listener.onWolfStateChanged(mob);
                }
            }
            if (wasPosture != mob.postureFlags()) {
                // The ocelot's creep/flee postures ride the shared flags
                // byte at index 0 (the sneak and sprint bits).
                for (Listener listener : listeners) {
                    listener.onMobPostureChanged(mob);
                }
            }
            if (wasWitchDrinking != mob.witchDrinking()) {
                for (Listener listener : listeners) {
                    listener.onMobWitchDrinkingChanged(mob, mob.witchDrinking());
                }
            }
            if (mob.consumeWitchAura()) {
                for (Listener listener : listeners) {
                    listener.onMobWitchAura(mob);
                }
            }
            // The ghast's fireball cycle (Slice 16c — ShootFireballGoal's
            // charge model): the 1007 warning at charge 10, the launch at
            // 20, the charging flag's flip on the wire.
            if (mob.consumeGhastChargeSound()) {
                for (Listener listener : listeners) {
                    listener.onMobSoundAt(mob, "mob.ghast.charge", 10.0F, 0.95F);
                }
            }
            Position ghastFireball = mob.consumeGhastFireball();
            if (ghastFireball != null) {
                for (Listener listener : listeners) {
                    listener.onMobGhastFireball(mob, ghastFireball);
                }
            }
            if (wasGhastCharging != mob.ghastCharging()) {
                for (Listener listener : listeners) {
                    listener.onMobGhastChargingChanged(mob);
                }
            }
            // The blaze's volley cycle (Slice 18a — AttackGoal's step
            // walk): the fireball launch at steps 2-4 and the fire-active
            // flag's flip on the wire's index-16 byte.
            if (mob.consumeBlazeFireball()) {
                for (Listener listener : listeners) {
                    listener.onMobBlazeFireball(mob);
                }
            }
            if (mob.consumeBlazeFireDirty()) {
                for (Listener listener : listeners) {
                    listener.onMobBlazeFireChanged(mob);
                }
            }
            // The silverfish's egg-hosting verdicts (Slice 18c): the
            // wake-up scan's expiry and the infest roll's fire.
            if (mob.consumeSilverfishWake()) {
                for (Listener listener : listeners) {
                    listener.onSilverfishWake(mob);
                }
            }
            if (mob.consumeSilverfishInfest()) {
                for (Listener listener : listeners) {
                    listener.onSilverfishInfest(mob);
                }
            }
            // The enderman's carry + provocation (Slice 19d): the wire's
            // state flip (the carried short + the screaming byte), the
            // pickup/place intents (the world writes), and the stare
            // scream's sound.
            if (mob.consumeEndermanStateDirty()) {
                for (Listener listener : listeners) {
                    listener.onEndermanStateChanged(mob);
                }
            }
            if (mob.consumeEndermanPickup()) {
                for (Listener listener : listeners) {
                    listener.onEndermanPickup(mob);
                }
            }
            if (mob.consumeEndermanPlace()) {
                for (Listener listener : listeners) {
                    listener.onEndermanPlace(mob);
                }
            }
            if (mob.consumeEndermanStareScream()) {
                // LookAtPlayerGoal.tick line 412: the stare sound at the
                // body (the pigman's own anger-sound pattern).
                for (Listener listener : listeners) {
                    listener.onMobSoundAt(mob, "mob.endermen.stare", 1.0F, 1.0F);
                }
            }
            // The guardian's charge cycle (Slice 17a — the laser family):
            // the beam id's live resolution over the session registry (the
            // wire's slot 17 carries the target), the beam's Event 21 at
            // the start, the completed charge's magic + melee pair on the
            // nearest player, the elder's aura cadence, and the state flip
            // the wire republishes.
            if (mob.type() == MobType.GUARDIAN) {
                boolean wasBeam = wasGuardianBeam;
                int wasMoving = wasGuardianMoving ? 1 : 0;
                if (mob.guardianBeamActive()) {
                    PlayerSession beamTarget = nearestPlayingPlayer(players, mob);
                    mob.setGuardianBeamTargetId(
                            beamTarget != null ? beamTarget.engineEntityId() : 0);
                } else {
                    mob.setGuardianBeamTargetId(0);
                }
                if (mob.consumeGuardianBeamStart()) {
                    // doEntityEvent(this, (byte)21): the attack warning the
                    // client answers with the mob.guardian.attack sound.
                    for (Listener listener : listeners) {
                        listener.onMobStatusEvent(mob, 21);
                    }
                }
                if (mob.consumeGuardianLaserHit()) {
                    PlayerSession laserTarget = nearestPlayingPlayer(players, mob);
                    if (laserTarget != null && laserTarget.health() > 0
                            && !laserTarget.dead()) {
                        // The magic half (1.0 + the elder's 2.0 — the hard
                        // bump lands at the engine's difficulty read) and
                        // the melee half (the ATTACK_DAMAGE attribute's own).
                        float magic = mob.guardianElder()
                                ? 1.0F + 2.0F : 1.0F;
                        for (Listener listener : listeners) {
                            listener.onMobGuardianLaserHit(mob, laserTarget,
                                    magic, mob.attackDamage());
                        }
                    }
                }
                if (mob.consumeGuardianAura()) {
                    for (Listener listener : listeners) {
                        listener.onMobGuardianAura(mob);
                    }
                }
                if (wasMoving != (mob.guardianMoving() ? 1 : 0)
                        || wasBeam != (mob.guardianBeamTargetId() != 0)) {
                    for (Listener listener : listeners) {
                        listener.onGuardianStateChanged(mob);
                    }
                }
            }
            // The pigman's delayed angry sound (Slice 16d — mobAiTick's
            // angerSoundDelay arm: volume * 2, the 1.8-centered band).
            if (mob.consumePigmanAngrySound()) {
                float pitch = (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
                for (Listener listener : listeners) {
                    listener.onMobSoundAt(mob, "mob.zombiepig.zpigangry",
                            2.0F, pitch * 1.8F);
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
            // The witch's completed drink (WitchEntity.mobTick lines 98-115,
            // through PotionItem.finishUsing): the effect set lands on the
            // body — the instant arm applies, the timed arm installs (the
            // same math as the player's drink).
            int witchDrink = mob.consumeWitchDrink();
            if (witchDrink > 0) {
                net.zaminmc.torch.server.potion.PotionSystem potions = potionSystem;
                if (potions != null) {
                    potions.finishDrinking(mob, witchDrink);
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
            // The endermite's lifetime wall (Slice 17c — EndermiteEntity
            // mobTick lines 125-127): the non-persistent body removes
            // itself at 2400 ticks — no death animation, no loot (the
            // reference's own remove()).
            if (mob.consumeEndermiteExpire()) {
                if (mob.hasRider()) {
                    dismountMob(mob, false);
                }
                iterator.remove();
                for (Listener listener : listeners) {
                    listener.onMobRemoved(mob, "expired");
                }
                continue;
            }
            // The snow golem's volley (Slice 17d — ProjectileAttackGoal's
            // 20-tick cadence over the hostile kinds within 10 blocks): the
            // snowball launches at the golem's live target through the
            // engine's projectile system.
            if (mob.consumeSnowGolemShot()) {
                MobEntity shotTarget = mob.golemTarget();
                if (shotTarget != null && !shotTarget.dead()) {
                    for (Listener listener : listeners) {
                        listener.onMobRangedAttackMob(mob, shotTarget);
                    }
                }
            }
            if (mob.consumePendingMeleeAttack()) {
                // Slice 16b: the mob-vs-mob target family first — a live
                // mob target owns the swing (the reference's single
                // attackTarget read by the melee goal).
                MobEntity mobTarget = mob.mobAttackTarget();
                if (mobTarget != null && !mobTarget.dead()) {
                    hurtByMob(mob, mobTarget, mob.attackDamage());
                } else {
                    PlayerSession target = nearestPlayingPlayer(players, mob);
                    if (target != null && target.health() > 0 && !target.dead()) {
                        for (Listener listener : listeners) {
                            // The kind's own attack damage (the reference's ATTACK_DAMAGE
                            // attribute — the enderman's 7.0, the rest 2.0; the slime's
                            // own size — getDamageAmount, Slice 12k).
                            listener.onMobAttackedPlayer(mob, target, mob.attackDamage());
                        }
                        // The slime's contact squish (damageTargetEntity line
                        // 201: mob.attack at the reference's pitch band).
                        if (mob.consumeSlimeAttackSound()) {
                            float pitch = (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
                            for (Listener listener : listeners) {
                                listener.onMobSoundAt(mob, "mob.attack", 1.0F, pitch);
                            }
                        }
                    }
                }
            }
            // The slime's jump and landing squish (getSoundVolume 0.4 * size).
            MobEntity.SlimeSound jumpSound = mob.consumeSlimeSound();
            if (jumpSound != null) {
                for (Listener listener : listeners) {
                    listener.onMobSoundAt(mob, jumpSound.name(),
                            jumpSound.volume(), jumpSound.pitch() * 0.8F);
                }
            }
            MobEntity.SlimeSound landSound = mob.consumeSlimeLandSound();
            if (landSound != null) {
                for (Listener listener : listeners) {
                    listener.onMobSoundAt(mob, landSound.name(),
                            landSound.volume(), landSound.pitch() / 0.8F);
                }
            }
            // The breed polls (the vanilla goal's landing converts into the
            // real spawn after the loop — the manager owns the mob lifecycle).
            MobEntity mate = mob.consumePendingBreed();
            if (mate != null) {
                pendingBreeds.add(new MobEntity[] {mob, mate});
            }
            if (mob.consumePendingLoveBurst()) {
                for (Listener listener : listeners) {
                    listener.onMobLoveBurst(mob);
                }
            }
            if (mob.consumeGrewUp()) {
                for (Listener listener : listeners) {
                    listener.onMobGrewUp(mob);
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
                    listener.onMobSound(mob, mob.effectiveIdleSound());
                }
            }
        }

        // The deferred breeds land after the iteration (the loop is closed).
        for (MobEntity[] pair : pendingBreeds) {
            breed(pair[0], pair[1]);
        }
        // The deferred slime splits land after the iteration too.
        for (MobEntity parent : pendingSlimeSplits) {
            splitSlime(parent);
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
     * death animation starts here and loot lands when it finishes. The
     * public damage gate (§27.7) reads MELEE.
     *
     * @return whether the hit landed (the reference's {@code takeDamage}
     *         verdict — the fire-aspect extinguish and the knockback-
     *         enchantment extra both gate on it).
     */
    public boolean hurt(MobEntity mob, float amount, double attackerYaw) {
        return hurt(mob, amount, attackerYaw,
                net.zaminmc.torch.event.DamageCause.MELEE);
    }

    /**
     * Melee hit from another mob (Slice 16b — the mob-vs-mob target family's
     * damage land): the attacker's identity rides the walk. The wolf's own
     * takeDamage shaves a non-player non-arrow attacker to
     * {@code (amount + 1) / 2} (WolfEntity lines 262-268), and the victim's
     * RevengeGoal answers: the hostile kinds and the wolves target their
     * attacker, the wolf and the zombie calling their nearby kin.
     */
    public boolean hurtByMob(MobEntity attacker, MobEntity victim, float amount) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(victim, "victim");
        if (victim.type() == MobType.WOLF) {
            // WolfEntity.takeDamage: a mob's hit rounds up to half.
            amount = (amount + 1.0f) / 2.0f;
        }
        if (attacker.type() == MobType.IRON_GOLEM) {
            // IronGolemEntity.tryDamage line 202: the golem's own damage
            // draw — the heavy 7 + nextInt(15) roll per swing.
            amount = attacker.golemDamageRoll();
        }
        double kbYaw = Math.toDegrees(Math.atan2(
                -(victim.position().x() - attacker.position().x()),
                victim.position().z() - attacker.position().z()));
        // The endermite's exemption rides the inner walk (the cause alone
        // cannot tell the kin from a claw — EndermanEntity.takeDamage
        // lines 286-288).
        boolean landed = hurt(victim, amount, kbYaw,
                net.zaminmc.torch.event.DamageCause.MELEE,
                attacker.type() == MobType.ENDERMITE);
        if (landed) {
            if (attacker.type() == MobType.IRON_GOLEM) {
                // tryDamage line 204: the landed golem hit launches the
                // victim into the air (velocityY += 0.4F) — the famous
                // uppercut.
                victim.addVelocity(0.0, attacker.golemLaunchY(), 0.0);
                if (attacker.consumeGolemSwing()) {
                    // doEntityEvent(this, (byte)4) + the throw sound: the
                    // client's swing-up arm rides the wire.
                    for (Listener listener : listeners) {
                        listener.onMobStatusEvent(attacker, 4);
                    }
                }
                for (Listener listener : listeners) {
                    listener.onMobSoundAt(attacker, "mob.irongolem.throw", 1.0F, 1.0F);
                }
            }
            if (!victim.dead()) {
                applyRevenge(victim, attacker);
            }
        }
        return landed;
    }

    /**
     * RevengeGoal's arm (Slice 16b): the victim — any hostile kind or a
     * wolf — targets its attacker, and the call-others family (the wolf's
     * and the zombie's own {@code true} flag) passes the anger to every
     * nearby same-kind body with no live target.
     */
    private void applyRevenge(MobEntity victim, MobEntity attacker) {
        if (attacker.dead()) {
            return;
        }
        boolean revengeArmed = victim.type().hostile || victim.type() == MobType.WOLF;
        if (!revengeArmed) {
            return;
        }
        victim.setMobAttackTarget(attacker);
        boolean callOthers = victim.type() == MobType.WOLF || victim.type() == MobType.ZOMBIE;
        if (!callOthers) {
            return;
        }
        for (MobEntity kin : mobs) {
            if (kin == victim || kin.type() != victim.type() || kin.dead()) {
                continue;
            }
            if (kin.mobAttackTarget() != null) {
                continue; // the reference's own gate: only the targetless join
            }
            double dx = kin.position().x() - victim.position().x();
            double dy = kin.position().y() - victim.position().y();
            double dz = kin.position().z() - victim.position().z();
            if (dx * dx + dy * dy + dz * dz <= 32.0 * 32.0) {
                kin.setMobAttackTarget(attacker);
            }
        }
    }

    /**
     * The pigman group anger (Slice 16d — takeDamage's getAngryTo +
     * RevengeGoal's own setMobEntityTarget override): the hurt body and
     * every calm pigman within 32 blocks of it anger at the attacking
     * player together.
     */
    public void pigmenBecomeAngryAt(MobEntity victim, String attackerName) {
        if (victim.type() != MobType.ZOMBIE_PIGMAN || attackerName == null) {
            return;
        }
        victim.becomeAngryTo(attackerName);
        for (MobEntity pigman : mobs) {
            if (pigman == victim || pigman.type() != MobType.ZOMBIE_PIGMAN
                    || pigman.dead() || pigman.isAngry()) {
                continue; // the already-angry keep their own window
            }
            double dx = pigman.position().x() - victim.position().x();
            double dy = pigman.position().y() - victim.position().y();
            double dz = pigman.position().z() - victim.position().z();
            if (dx * dx + dy * dy + dz * dz <= 32.0 * 32.0) {
                pigman.becomeAngryTo(attackerName);
            }
        }
    }

    /**
     * The owner arms (Slice 16b — AttackWithOwnerGoal + OwnerHurtGoal):
     * every STANDING tamed wolf the player owns joins the fight on the
     * target — the mob their owner just attacked, or the mob that just
     * attacked their owner. A sitting wolf never joins (SitGoal's own
     * claim on the movement).
     */
    public void ownerWolvesJoinFight(String ownerName, MobEntity target) {
        if (ownerName == null || ownerName.isEmpty() || target == null || target.dead()) {
            return;
        }
        for (MobEntity wolf : mobs) {
            if (wolf.type() != MobType.WOLF || !wolf.wolfTamed() || wolf.dead()) {
                continue;
            }
            if (wolf.wolfSitting()) {
                continue; // the sitting pet stays seated
            }
            if (!ownerName.equals(wolf.ownerName())) {
                continue;
            }
            wolf.setMobAttackTarget(target);
        }
    }

    /**
     * The full semantic damage entry: validated damage, knockback, hurt
     * event; the death animation starts here and loot lands when it
     * finishes. The public MobDamageEvent (§27.7) gates the whole walk —
     * a cancelled hit lands nothing (no health, no knockback, no
     * listeners) and answers false, exactly a refused takeDamage.
     *
     * @param cause the damage-source classification the event carries
     * @return whether the hit landed (the reference's {@code takeDamage}
     *         verdict — the fire-aspect extinguish and the knockback-
     *         enchantment extra both gate on it).
     */
    public boolean hurt(MobEntity mob, float amount, double attackerYaw,
                        net.zaminmc.torch.event.DamageCause cause) {
        return hurt(mob, amount, attackerYaw, cause, false);
    }

    /**
     * The full walk with the endermite awareness (Slice 19d): the
     * pearl-spawned kin's melee never angers the tall body — the
     * reference's own exemption (takeDamage lines 286-288) — so the
     * mob-vs-mob melee site passes the attacker's identity through.
     */
    boolean hurt(MobEntity mob, float amount, double attackerYaw,
                 net.zaminmc.torch.event.DamageCause cause, boolean endermiteAttacker) {
        Objects.requireNonNull(mob, "mob");
        if (amount <= 0 || mob.dead()) {
            return false;
        }
        var bus = eventBus;
        if (bus != null) {
            var event = new net.zaminmc.torch.event.entity.MobDamageEvent(
                    mob, amount, cause);
            bus.fire(event);
            if (event.cancelled()) {
                return false;
            }
            amount = event.amount();
            if (amount <= 0) {
                return false;
            }
        }
        // The silverfish's swarm wake (Slice 18c —
        // SilverfishEntity.takeDamage lines 77-87): the entity-sourced or
        // magic hurt arms the 20-tick cooldown; the environmental family
        // never wakes the nest.
        if (mob.type() == MobType.SILVERFISH
                && (cause == net.zaminmc.torch.event.DamageCause.MELEE
                    || cause == net.zaminmc.torch.event.DamageCause.PROJECTILE
                    || cause == net.zaminmc.torch.event.DamageCause.EXPLOSION
                    || cause == net.zaminmc.torch.event.DamageCause.MAGIC)) {
            mob.armSilverfishWake();
        }
        // The enderman's provocation (Slice 19d — takeDamage lines
        // 281-309): every wound angers the body (the reference's own
        // no-attacker clause reaches even the environmental family); the
        // player's-hand marker lands at the engine's player-melee site
        // (the cause alone cannot tell a hand from a claw); the
        // projectile's unknown origin clears it (the dodge path's own
        // follow-up).
        if (mob.type() == MobType.ENDERMAN && !endermiteAttacker) {
            mob.setEndermanAngry(true);
            if (cause == net.zaminmc.torch.event.DamageCause.PROJECTILE) {
                mob.setEndermanAttackedByPlayer(false);
            }
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
        return true;
    }

    /**
     * The magic arm (Slice 11b — DamageSource.MAGIC carries no knockback):
     * the damage, the hurt/died fan-out, nothing else. The potion family's
     * periodic and instant damage ride it. The public MobDamageEvent (§27.7)
     * gates the walk with the MAGIC cause — a cancelled hit lands nothing.
     */
    public boolean hurtWithoutKnockback(MobEntity mob, float amount) {
        Objects.requireNonNull(mob, "mob");
        if (amount <= 0 || mob.dead()) {
            return false;
        }
        var bus = eventBus;
        if (bus != null) {
            var event = new net.zaminmc.torch.event.entity.MobDamageEvent(
                    mob, amount, net.zaminmc.torch.event.DamageCause.MAGIC);
            bus.fire(event);
            if (event.cancelled()) {
                return false;
            }
            amount = event.amount();
            if (amount <= 0) {
                return false;
            }
        }
        // The enderman's armor-bypass response (EndermanEntity.takeDamage
        // lines 311-316): the magic/environment family teleports the body
        // away 9 times in 10.
        if (mob.type() == MobType.ENDERMAN && random.nextInt(10) != 0) {
            mob.teleportRandomly();
        }
        mob.hurt(amount);
        for (Listener listener : listeners) {
            listener.onMobHurt(mob);
        }
        if (mob.health() <= 0) {
            for (Listener listener : listeners) {
                listener.onMobDied(mob);
            }
        }
        return true;
    }

    /** Rolls the kind's loot table into the world at the body. */
    private void dropLoot(MobEntity mob) {
        Position at = mob.position();
        // The Looting widening (the reference's dropFewItems shape, e.g.
        // CowEntity: `nextInt(3) + nextInt(1 + lootingMultiplier)` — every
        // common roll widens by nextInt(1 + looting); at level 0 the term
        // is nextInt(1) = 0, the exact base roll). The rare-drop arm
        // (0.025 + looting * 0.01, LivingEntity.die line 769) ledgered
        // with the rare-loot-table slice — the engine tables carry no
        // rare rows yet.
        int looting = mob.lootingLevel();
        if (mob.type() == MobType.SLIME && mob.slimeSize() > 1) {
            // getDropItem's null arm: only the smallest body carries the
            // slime balls (the split family drops nothing).
            return;
        }
        if (mob.type() == MobType.MAGMA_CUBE) {
            // MagmaCubeEntity.dropItems (lines 57-68): the size-1 gate,
            // then the reference's own negative-friendly count arm —
            // `nextInt(4) - 2` answers -2..1, so nothing drops most of
            // the time — widened by the looting bonus; each unit as its
            // own single-item stack.
            if (mob.slimeSize() > 1) {
                return;
            }
            int count = random.nextInt(4) - 2;
            if (looting > 0) {
                count += random.nextInt(looting + 1);
            }
            for (int unit = 0; unit < count; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.MAGMA_CREAM, 1));
            }
            return;
        }
        if (mob.type() == MobType.SPIDER || mob.type() == MobType.CAVE_SPIDER) {
            // SpiderEntity.dropItems (lines 104-109): the string walk
            // (the getDropItem roll widened by the looting) then the eye's
            // own conditional — a 1-in-3 base, or any looting luck. The
            // cave spider rides the family (no getDropItem override).
            int string = random.nextInt(3) + random.nextInt(1 + looting);
            for (int unit = 0; unit < string; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.STRING, 1));
            }
            if (random.nextInt(3) == 0 || random.nextInt(1 + looting) > 0) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.SPIDER_EYE, 1));
            }
            return;
        }
        if (mob.type() == MobType.BLAZE) {
            // BlazeEntity.dropItems (lines 145-153): nextInt(2 + looting)
            // single blaze rods — the full-looting bound like the pigman's
            // pair, each unit its own single-item stack.
            int rods = random.nextInt(2 + looting);
            for (int unit = 0; unit < rods; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.BLAZE_ROD, 1));
            }
            return;
        }
        if (mob.type() == MobType.SKELETON) {
            // SkeletonEntity.dropItems (lines 178-198): the wither variant
            // drops the coal roll (`nextInt(3 + looting) - 1` — the negative-
            // friendly count arm, so most wither bodies drop nothing), the
            // archer the arrows (`nextInt(3 + looting)`); BOTH carry the
            // bones (`nextInt(3 + looting)` again) — each unit its own
            // single-item stack, the full-looting bound like the pigman's.
            if (mob.skeletonWither()) {
                int coal = random.nextInt(3 + looting) - 1;
                for (int unit = 0; unit < coal; unit++) {
                    lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                            ItemStack.of(BuiltinItems.COAL, 1));
                }
            } else {
                int arrows = random.nextInt(3 + looting);
                for (int unit = 0; unit < arrows; unit++) {
                    lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                            ItemStack.of(BuiltinItems.ARROW, 1));
                }
            }
            int bones = random.nextInt(3 + looting);
            for (int unit = 0; unit < bones; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.BONE, 1));
            }
            // MobEntity.dropEquipment (lines 569-589): the held item drops
            // on a player kill at the 0.085 chance, widened by the looting
            // (`nextFloat() - looting * 0.01F < 0.085F` — the reference's
            // own shape over the one modeled slot).
            ItemStack held = mob.heldEquipment();
            if (held != null && mob.playerHitRecently()
                    && random.nextFloat() - looting * 0.01F < EQUIPMENT_DROP_CHANCE) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()), held);
            }
            // LivingEntity.die lines 766-768 + SkeletonEntity.dropRareItem
            // (lines 201-206): the wither variant's rare head — the
            // 0.025 + looting * 0.01 roll on the player-kill window.
            if (mob.skeletonWither() && mob.playerHitRecently()
                    && random.nextFloat() < RARE_DROP_BASE + looting * 0.01F) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        new ItemStack(BuiltinItems.SKULL, 1, 1, null));
            }
            return;
        }
        if (mob.type() == MobType.GHAST) {
            // GhastEntity.dropItems (lines 98-113): 0-1 ghast tears, then
            // 0-2 gunpowder, both widened by the looting bonus, each
            // unit as its own single-item stack.
            int tears = random.nextInt(2) + random.nextInt(1 + looting);
            for (int unit = 0; unit < tears; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.GHAST_TEAR, 1));
            }
            int powder = random.nextInt(3) + random.nextInt(1 + looting);
            for (int unit = 0; unit < powder; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.GUNPOWDER, 1));
            }
            return;
        }
        if (mob.type() == MobType.ZOMBIE_PIGMAN) {
            // ZombiePigmanEntity.dropItems (lines 130-139): 0-1 rotten
            // flesh, then 0-1 gold nugget — the counts widen by the FULL
            // looting (nextInt(2 + looting), the reference's own bound);
            // each unit as its own single-item stack.
            int flesh = random.nextInt(2 + looting);
            for (int unit = 0; unit < flesh; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.ROTTEN_FLESH, 1));
            }
            int nuggets = random.nextInt(2 + looting);
            for (int unit = 0; unit < nuggets; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.GOLD_NUGGET, 1));
            }
            return;
        }
        if (mob.type() == MobType.GUARDIAN) {
            // GuardianEntity.dropItems (lines 383-398): the prismarine
            // shards' own double roll — `nextInt(3) + nextInt(1+looting)`
            // as ONE stack of count i; then the fish-or-crystals pair (the
            // first `nextInt(3 + looting) > 1` drops the raw fish, else the
            // second rolls for the crystals); the elder's wet sponge rides
            // the player-kill arm (the `loot` flag — the engine's stand-in
            // is the looting level: only a player's kill carries one).
            int shards = random.nextInt(3) + random.nextInt(1 + looting);
            if (shards > 0) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        new ItemStack(BuiltinItems.PRISMARINE_SHARD, shards, 0, null));
            }
            if (random.nextInt(3 + looting) > 1) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.FISH, 1));
            } else if (random.nextInt(3 + looting) > 1) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.PRISMARINE_CRYSTALS, 1));
            }
            if (mob.guardianElder() && looting > 0) {
                // The wet sponge (Blocks.SPONGE with damage 1 — the engine's
                // stack damage field carries the variant).
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        new ItemStack(BuiltinItems.SPONGE, 1, 1, null));
            }
            return;
        }
        if (mob.type() == MobType.IRON_GOLEM) {
            // IronGolemEntity.dropItems (lines 236-244): the 0-2 poppies
            // (the flower's variant 0 rides the stack damage field), then
            // the 3-5 iron ingots — each unit as its own single-item stack
            // (the reference's per-unit dropItem loop).
            int poppies = random.nextInt(3);
            for (int unit = 0; unit < poppies; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.POPPY, 1));
            }
            int ingots = 3 + random.nextInt(3);
            for (int unit = 0; unit < ingots; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.IRON_INGOT, 1));
            }
            return;
        }
        if (mob.type() == MobType.WITCH) {
            // WitchEntity.dropItems (lines 178-192): 1-3 rows; per row the
            // count roll, then the LOOT member roll (the stick appears
            // twice — the double weight), then the looting bonus on top of
            // the count; each unit drops as its own single-item stack
            // (the reference's per-unit dropItem loop).
            int rows = random.nextInt(3) + 1;
            for (int row = 0; row < rows; row++) {
                int count = random.nextInt(3);
                var item = WITCH_LOOT[random.nextInt(WITCH_LOOT.length)];
                if (looting > 0) {
                    count += random.nextInt(looting + 1);
                }
                for (int unit = 0; unit < count; unit++) {
                    lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                            ItemStack.of(item, 1));
                }
            }
        } else {
            for (ItemRoll roll : mob.type().loot) {
            int count = roll.roll(random) + random.nextInt(1 + looting);
            if (count <= 0) {
                continue;
            }
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(roll.type(), count));
            }
        }
        // The worn gear and the donkey chest spill with the body
        // (HorseBaseEntity.dropInventoryAndChest on the death path).
        for (ItemStack stack : mob.spillMountGear()) {
            lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()), stack);
        }
    }

    /** WitchEntity's LOOT array (line 33): the stick carries the double weight. */
    private static final net.zaminmc.torch.item.ItemType[] WITCH_LOOT = {
            BuiltinItems.GLOWSTONE_DUST, BuiltinItems.SUGAR, BuiltinItems.REDSTONE,
            BuiltinItems.SPIDER_EYE, BuiltinItems.GLASS_BOTTLE, BuiltinItems.GUNPOWDER,
            BuiltinItems.STICK, BuiltinItems.STICK
    };

    /**
     * The slime's death split (SlimeEntity.remove lines 157-181): a body
     * over size 1 that died spawns 2-4 children at half the size, placed
     * on the reference's own {@code (k%2 - 0.5) * size / 4} pair grid
     * half a block up, each with its own yaw roll. The custom-name and
     * persistent carries ride their own missing features (no mob names;
     * the persistence walks below).
     */
    private void splitSlime(MobEntity parent) {
        if (!parent.type().slimeFamily() || parent.slimeSize() <= 1) {
            return;
        }
        int parentSize = parent.slimeSize();
        Position at = parent.position();
        int children = 2 + random.nextInt(3);
        for (int k = 0; k < children; k++) {
            float offsetX = (k % 2 - 0.5F) * parentSize / 4.0F;
            float offsetZ = (k / 2 - 0.5F) * parentSize / 4.0F;
            MobEntity child = spawnAt(parent.type(), new Position(
                    at.x() + offsetX, at.y() + 0.5, at.z() + offsetZ));
            if (child == null) {
                continue; // the spawn event refused the child
            }
            child.setSlimeSize(parentSize / 2);
            if (parent.persistent()) {
                child.setPersistent(true);
            }
            child.setBodyYaw(random.nextFloat() * 360.0F);
        }
    }

    /** The witch's live effect-store read (the drink ladder's gates). */
    private boolean witchHasEffect(MobEntity mob, int effectId) {
        net.zaminmc.torch.server.potion.PotionSystem potions = potionSystem;
        return potions != null && potions.hasEffect(mob, effectId);
    }

    /**
     * The love clock walk (the vanilla EntityAgeable age tick + the
     * AnimalEntity love drain, in the reference order): an age != 0 body
     * cannot hold love, the age walks one tick at a time, the love window
     * drains with its ambient heart clock.
     */
    private void tickBreedingFor(MobEntity mob) {
        if (mob.breedingAge() != 0) {
            mob.resetLove(); // AnimalEntity.mobTick: love dies off-age
        }
        mob.tickBreedingAge();
        mob.tickLove(); // the ambient hearts flag rides the consume poll below
        mob.consumePendingAmbientHearts(); // single-heart particles await the
        // heart id in the community particle data (the burst event covers the
        // observable feeding/breed moments for now — see the ledger)
    }

    /**
     * The vanilla AnimalBreedGoal.breed(): the baby spawns at the parent
     * (facing yaw 0), the parents take the 6000-tick cooldown, their love
     * clears, the child starts its -24000 childhood, and the doMobLoot XP
     * burst lands (1-7, the random.nextInt(7) + 1 roll).
     */
    private void breed(MobEntity parent, MobEntity mate) {
        if (parent.dead() || mate.dead()) {
            return;
        }
        MobEntity baby = spawnAt(childTypeOf(parent, mate), parent.position());
        baby.setHorseSubtype(childSubtypeOf(parent, mate));
        parent.setBreedingAge(MobEntity.ADULT_BREED_COOLDOWN_TICKS);
        mate.setBreedingAge(MobEntity.ADULT_BREED_COOLDOWN_TICKS);
        parent.resetLove();
        mate.resetLove();
        baby.setBreedingAge(MobEntity.CHILDHOOD_TICKS);
        for (Listener listener : listeners) {
            listener.onMobBred(parent, mate, baby);
            listener.onMobLoveBurst(parent); // the celebration hearts
        }
    }

    /**
     * The child's kind ({@code HorseBaseEntity.makeChild} + the AnimalEntity
     * default): same kind for everything, and the horse family resolves
     * horse+donkey into the mule (the cross only produces the sterile child).
     */
    private static MobType childTypeOf(MobEntity parent, MobEntity mate) {
        return parent.type(); // the engine keys the horse family on one MobType
    }

    /**
     * The child's horse subtype: same parents' subtype, horse+donkey makes
     * the mule (the makeChild type table: k = i when equal, 2 on the 0/1
     * cross — mules themselves never qualify through canBreedWith).
     */
    private static int childSubtypeOf(MobEntity parent, MobEntity mate) {
        if (parent.type() != MobType.HORSE) {
            return 0;
        }
        int a = parent.horseSubtype();
        int b = mate.horseSubtype();
        if (a == b) {
            return a;
        }
        return MobEntity.HORSE_SUBTYPE_MULE;
    }

    /**@return whether the night window is active at the given time of day. */
    public static boolean isNight(long timeOfDay) {
        return timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;
    }

    /**
     * The natural-spawn surface (Slice 11d): the count per category
     * (MONSTER = the hostile family, CREATURE = the passive family) and
     * the exact-position spawn with the group's baby flag (the
     * -24000 childhood the breeding model grows through).
     */
    public int countByCategory(net.zaminmc.torch.server.spawn.NaturalSpawnSystem.Category category) {
        int count = 0;
        for (MobEntity mob : mobs) {
            if (categoryOf(mob.type()) == category) {
                count++;
            }
        }
        return count;
    }

    /**
     * The kind's spawn category (the reference's MobCategory mapping):
     * the hostile kinds are MONSTER, the squid alone is WATER_CREATURE,
     * the bat alone is AMBIENT, everything else is CREATURE.
     */
    private static net.zaminmc.torch.server.spawn.NaturalSpawnSystem.Category categoryOf(MobType type) {
        if (type == MobType.SQUID) {
            return net.zaminmc.torch.server.spawn.NaturalSpawnSystem.Category.WATER_CREATURE;
        }
        if (type == MobType.BAT) {
            return net.zaminmc.torch.server.spawn.NaturalSpawnSystem.Category.AMBIENT;
        }
        return type.hostile
                ? net.zaminmc.torch.server.spawn.NaturalSpawnSystem.Category.MONSTER
                : net.zaminmc.torch.server.spawn.NaturalSpawnSystem.Category.CREATURE;
    }

    /** Spawns one mob at the exact position (the natural cycle's own). */
    public MobEntity spawnNatural(MobType type, Position position, boolean baby, float yaw) {
        MobEntity mob = spawnAt(type, position);
        if (mob != null) {
            mob.setBodyYaw(yaw);
            if (type == MobType.SLIME) {
                mob.initializeSlimeSize(); // SlimeEntity.initialize (12k)
            }
            if (baby) {
                mob.setBreedingAge(-24_000);
            }
        }
        return mob;
    }

    /**
     * The nether's own natural spawn (Slice 18d — SkeletonEntity.initialize
     * lines 215-226): the dimension's skeletons carry the wither roll —
     * {@code random.nextInt(5) > 0}, the 4-in-5 variant — replacing the
     * archer's bow with the stone sword (the melee goal swap rides the
     * wither flag in the mind router). Every other kind walks the shared
     * overworld shape (no dimension-specific initialize of their own).
     */
    public MobEntity spawnNaturalNether(MobType type, Position position, boolean baby,
                                        float yaw) {
        MobEntity mob = spawnNatural(type, position, baby, yaw);
        if (mob != null && type == MobType.SKELETON && random.nextInt(5) > 0) {
            mob.setSkeletonWither(true);
            mob.setHeldEquipment(ItemStack.of(BuiltinItems.STONE_SWORD, 1));
        }
        return mob;
    }

    /** The yaw-less legacy arm (the pre-worldgen callers' shape). */
    public MobEntity spawnNatural(MobType type, Position position, boolean baby) {
        MobEntity mob = spawnAt(type, position);
        if (mob != null && baby) {
            mob.setBreedingAge(-24_000);
        }
        return mob;
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
