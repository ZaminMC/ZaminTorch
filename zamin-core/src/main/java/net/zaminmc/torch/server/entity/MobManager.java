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

    /** The rare-drop roll's base (LivingEntity.die line 766: 0.025F). */
    private static final float RARE_DROP_BASE = 0.025f;

    /** Where mob loot goes (the engine's item-entity system). */
    public interface LootSink {
        /**
         * The loot drop in the body's own dimension (Wave TF-9 — the
         * reference's Entity.world: the dying body's world owns its drops).
         */
        void spawnLootDrop(Position position, ItemStack stack, int dimension);

        /**
         * A drop that never despawns (Slice 59 — the wither's nether star,
         * ItemEntity.setNeverDespawn): the default bridges to the ordinary
         * drop so simple sinks keep working.
         */
        default void spawnPersistentLootDrop(Position position, ItemStack stack, int dimension) {
            spawnLootDrop(position, stack, dimension);
        }
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
         * The mob's equipment changed after the spawn (Slice 78b — the
         * /summon Equipment row + the restore's held stack: the Entity
         * Equipment re-send to every observer).
         */
        default void onMobEquipmentChanged(MobEntity mob) {
        }

        /**
         * The mob's attribute overrides changed after the spawn (ML-011 —
         * the /summon + /entitydata Attributes rows over the Slice 78b
         * override setters): the Entity Properties (0x20) re-send to every
         * observer — the reference's own change walk (setBase → markDirty →
         * the tracked set → TrackedEntity.sendDataAndAttributes lines
         * 260-274). The wire's isTrackable law holds on this wake: only
         * the movementSpeed row fires it (attackDamage never enters the
         * tracked set — EntityAttributes line 21 has no trackable flag).
         */
        default void onMobAttributesChanged(MobEntity mob) {
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
         * The wither's blue window burned out (Slice 59 — mobAiTick lines
         * 214-219): the 7.0F birth explosion at the body's eye and the
         * GLOBAL 1013 spawn sound (every connected player, every
         * dimension — doGlobalEvent's own broadcast).
         */
        default void onWitherBirth(MobEntity mob) {
        }

        /**
         * The wither loosed one skull from a head (Slice 59 —
         * shootSkullAt lines 383-400): {@code head} the head index (1-3),
         * the target cell, and the charged flag — the launch rides the
         * engine's projectile system with the head's own mount point.
         */
        default void onWitherSkull(MobEntity mob, int head, double targetX,
                                   double targetY, double targetZ, boolean charged) {
        }

        /**
         * The wither's sweep cooldown expired (Slice 59 — mobAiTick lines
         * 285-312): the engine walks the 3x3x4 around the feet under the
         * mobGriefing rule, breaking every destructible non-air block and
         * dropping its loot (breakBlock(pos, true)).
         */
        default void onWitherSweep(MobEntity mob) {
        }

        /**
         * The wither's head-target Ints or the invuln countdown changed
         * (Slice 59 — the synced slots 17-20): the wire republishes the
         * four Ints.
         */
        default void onWitherMetadataChanged(MobEntity mob) {
        }

        /**
         * The wither's sweep broke at least one block (Slice 59 — line
         * 309's doEvent(null, 1012, ...)): the chunk break sound.
         */
        default void onWitherBlockBreakSound(MobEntity mob) {
        }

        /**
         * The wither loosed a skull (Slice 59 — line 384's doEvent(null,
         * 1014, ...)): the launch sound at the body.
         */
        default void onWitherShootSound(MobEntity mob) {
        }

        /**
         * The bat left its roost (BatEntity.mobAiTick lines 99-113 — both
         * arms' doEvent(null, 1015, blockPos, 0)): the takeoff event at
         * the body's cell.
         */
        default void onBatTakeoff(MobEntity mob) {
        }

        /**
         * A world event that reaches EVERY connected player (the
         * reference's doGlobalEvent — PlayerManager.sendPacket, no
         * distance and no dimension gate).
         */
        default void onWorldGlobalEventAt(Position position, int eventId) {
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
         * The dragon's contact arms (Slice 63 — mobTick lines 264-268):
         * the wing hulls' fling ({@code grown(4, 2, 4).moved(0, -2, 0)} —
         * the {@code d/d4 * 4} radial push with the 0.2 lift) and the
         * head hull's grown-1 damage (the flat 10.0) against every living
         * body of the dragon's own dimension.
         */
        default void onDragonContact(MobEntity mob) {
        }

        /**
         * The dragon's block sweep (destroyBlocks lines 417-456): the
         * head and body hulls over the End world — everything but the
         * immune five (barrier, obsidian, end stone, bedrock, command
         * block) breaks under mobGriefing; an immune block met writes
         * the restrictMovement verdict back through {@code
         * dragonSetRestrictMovement}.
         */
        default void onDragonSweep(MobEntity mob) {
        }

        /**
         * The linked crystal popped (tickEndCrystalInteraction lines
         * 326-331): the head's 10.0 explosive wound already landed on the
         * body through the part walk — the event carries the wire's hurt
         * fan.
         */
        default void onDragonCrystalWound(MobEntity mob) {
        }

        /** The death walk's first tick (doGlobalEvent 1018). */
        default void onDragonDeathSound(MobEntity mob) {
        }

        /**
         * The death walk's XP batch (tickPostDeath lines 512-520/530-537):
         * the orb burst of {@code amount} at the body — the 1000s between
         * 151..195 and the 2000 at 200.
         */
        default void onDragonXpBatch(MobEntity mob, int amount) {
        }

        /**
         * The death walk's end (createPortal + remove, lines 529-542):
         * the bedrock fountain with the exit portal and the egg, then the
         * body leaves.
         */
        default void onDragonPortal(MobEntity mob) {
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
         * A body trampled its farmland landing (FarmlandBlock.onFallenOn
         * lines 57-71 — the fall path's DIRT write): the intent's cell —
         * the engine writes the dirt and the neighbor bus carries the
         * crop's own pop.
         */
        default void onMobTrample(MobEntity mob) {
        }

        /**
         * A body stepped on the plain redstone ore (RedstoneOreBlock
         * .onSteppedOn lines 39-43 over interact lines 52-57 — the step
         * path's glow write): the intent's cell — the engine writes the
         * LIT form (the host re-check keeps the swap honest; the
         * particles are the client's own randomDisplayTick band, the
         * dedicated server's addParticle a no-op stub).
         */
        default void onMobStepOre(MobEntity mob) {
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

        /**
         * One ambient heart particle is due over a body in love (the
         * AnimalEntity.mobTick clock — every 10th love tick the reference
         * calls {@code world.addParticle(HEART, ...)} directly with the
         * server-chosen position and the gaussian velocity; the wire
         * renders one heart per event, unlike the event-18 burst).
         */
        default void onMobAmbientHearts(MobEntity mob, Position at,
                                        double vx, double vy, double vz) {
        }

        /**
         * A mob's worn piece shattered (the daylight wear's exhaustion —
         * the burn arm's helmet breaking at its durability max, the
         * reference's {@code LivingEntity.onBrokenItem} particle half:
         * the ITEM_CRACK crumbs at the body over the broken stack).
         */
        default void onMobItemShattered(MobEntity mob, ItemStack broken) {
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
         * The breed's cause-carrying form (VIII6 — AnimalBreedGoal.breed
         * lines 84-89: the love-causing player's engine id, captured before
         * the reset clears the rows; -1 when nature alone paired them).
         * The default hands off to the plain form (source compatibility
         * for the observers that never consult the cause).
         */
        default void onMobBred(MobEntity parent, MobEntity mate, MobEntity baby,
                               int causePlayerId) {
            onMobBred(parent, mate, baby);
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
         * The sheep's fleece color moved (Slice 30b — the dye arm's write
         * to the index-16 nibble; the observers re-send the kind byte with
         * the color | sheared bits).
         */
        default void onSheepFleeceChanged(MobEntity mob) {
        }

        /**
         * The snow golem's volley (Slice 17d — SnowGolemEntity.
         * doRangedAttack lines 89-96): the 20-tick snowball at the hostile
         * target — the engine's projectile system owns the launch.
         */
        default void onMobRangedAttackMob(MobEntity shooter, MobEntity target) {
        }

        /**
         * One water-splash particle of the wolf's shake burst (Slice 30d —
         * WolfEntity.tick lines 212-221: the server-computed point inside
         * the footprint at {@code minY + 0.8} riding the body's velocity,
         * one World Particles unit in the count-0 shape).
         */
        default void onWolfShakeSplash(MobEntity mob, Position at,
                                       double vx, double vy, double vz) {
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

        /**
         * The rabbit's garden eat (Slice 21d — RaidFarmGoal.tick lines
         * 566-579): the age-7 carrots vanish (the air write + the
         * neighbor updates), the block-dust burst pops, and the hunger
         * clock refills to 100 (onEatFromGarden).
         */
        default void onRabbitFarmEat(MobEntity rabbit) {
        }

        /**
         * A world event fired at a position (Slice 25a — the reference's
         * {@code world.doEvent(null, id, pos, 0)}: the zombie villager's
         * infect 1016 and un-infect 1017 events, the sounds the client
         * plays from the Effect packet itself; the zombie door family's
         * 1010 knock beside them). The event's DIMENSION rides beside the
         * position (the reference's PlayerManager.sendPacket dimension
         * gate — the README TODO's row 10 radius law).
         */
        default void onWorldEventAt(Position position, int eventId, int dimension) {
        }

        /**
         * The zombie villager's wire state moved (Slice 25a — the synced
         * slot-13 villager byte and the slot-14 converting byte: the Set
         * Entity Metadata republishes the pair when the cure arms).
         */
        default void onZombieStateChanged(MobEntity mob) {
        }

        /**
         * The zombie's door-break stage moved (VIII9 —
         * BreakDoorGoal.tick's {@code updateBlockMiningProgress} over the
         * MOB's own wire id: the Block Break Animation (0x28) carrying the
         * 0-9 crack stage, -1 the clear the goal's stop()/completion
         * publishes).
         */
        default void onZombieDoorBreakProgress(MobEntity mob, int x, int y, int z,
                                               int stage) {
        }

        /**
         * The zombie's door break completed (VIII9 — BreakDoorGoal.tick
         * lines 53-57, the 240th tick on HARD): the door cell's removal +
         * the 1012 door-break event + the 2001 block-dust burst land at
         * the bridge.
         */
        default void onZombieDoorBroken(MobEntity mob, int x, int y, int z) {
        }
    }

    /**
     * VT-006 — the entity-level crossing service (the reference's
     * Entity.changeDimension walk, lines 1695-1730, over PlayerManager
     * .changeDimension's reposition, lines 449-502): the direction law
     * (the nether pair -1 <-> 0), the 8:1 scale, the border band clamp,
     * the destination neighborhood load, and the PortalForcer's
     * find-or-build placement. The engine's server owns the worlds and
     * the forcers; the crossing families (the mobs first) ride the seam
     * one by one.
     */
    public interface DimensionCrossing {

        /** The crossing's outcome: the destination dimension + the forcer's arrival. */
        record Crossing(int toDimension,
                        net.zaminmc.torch.server.world.PortalForcer.Arrival arrival) {
        }

        /**
         * Walks the body's crossing coordinates to the destination: the
         * direction, the scale, the loads, and the forcer's placement.
         *
         * @param fromDimension the body's current dimension
         * @param velocityX     the body's horizontal motion (the forcer's
         *                      quarter-turn rotation consumes it)
         * @param velocityZ     the body's horizontal motion
         * @param memory        the body's portal entry memory (the frame
         *                      offsets the placement math consumes)
         */
        Crossing cross(int fromDimension, double x, double y, double z, float yaw,
                       double velocityX, double velocityZ,
                       net.zaminmc.torch.server.world.PortalForcer.Memory memory);
    }

    private final MobEntity.WorldQuery world;
    private final Random random;
    private final LootSink lootSink;
    /**
     * The live bodies. Copy-on-write because {@link #all()} is the whole
     * test suite's off-thread polling surface (every await polls it while
     * this thread spawns, despawns, and clears): {@code List.copyOf} over
     * a plain ArrayList can read the size and the array on opposite sides
     * of a concurrent removal and hand {@code List.of} the nulled tail —
     * the NPE run 591's attribution harness caught (a 100-body clear
     * racing a 25 ms poll). The copy-on-write array makes every snapshot
     * ({@code all()}, the iterators, {@code toArray}) a consistent view
     * by construction, and mutation here stays tick-thread-only.
     */
    private final List<MobEntity> mobs = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private int nextEntityId;
    /** Surface finder for population rolls: topmost solid y at (x, z), or -1. */
    private final java.util.function.BiFunction<Integer, Integer, Integer> surfaceY;
    /** The potion system (the witch's drink application); null until wired. */
    private volatile net.zaminmc.torch.server.potion.PotionSystem potionSystem;
    /** The item-entity world for the loot-pickup scan (Slice 92 — the engine wires). */
    private volatile ItemEntityManager itemEntities;
    /** The mobGriefing rule read (the pickup walk's own gate). */
    private volatile java.util.function.BooleanSupplier mobGriefingRead;
    /** The last tick's time-of-day (the local difficulty's own time term). */
    private volatile long lastTimeOfDay;
    /** The owner-lane admission for goal registrations (§27.6); null until wired. */
    private volatile java.util.function.Consumer<Runnable> goalAdmission;
    /** The difficulty read (0 peaceful .. 3 hard); null until wired. */
    private volatile java.util.function.IntSupplier difficultyRead;
    /**
     * The local-difficulty consult (VIII14 — the reference's
     * World.getLocalDifficulty over the engine's worlds): position + the
     * spawn dimension -> the {@link net.zaminmc.torch.server.world
     * .LocalDifficulty} read. Null until wired (the bare test boots ride
     * the historical zeroed stand-in).
     */
    private volatile java.util.function.BiFunction<Position, Integer,
            net.zaminmc.torch.server.world.LocalDifficulty> localDifficultyRead;
    /**
     * The dimension the current spawn walk lands in (VIII14 — the
     * consult's own context: the natural cycle's overworld default, the
     * nether cycle's arm, the reinforcement callee's caller dimension).
     * Tick-lane only (the spawn walks own it).
     */
    private int spawnDimension;

    /**
     * The village system (VIII10 — the villager's mating gate + scan
     * cadence, the golem's defense consult, the reputation hooks); null
     * until the engine wires it.
     */
    private volatile net.zaminmc.torch.server.village.VillageSystem villageSystem;

    /**
     * VT-006 — the entity-level crossing service; null until the engine
     * wires it (the unwired boots keep the no-crossing shape — the
     * seam's own hold).
     */
    private volatile DimensionCrossing dimensionCrossing;

    /** Wires the crossing service (the engine's own boot). */
    public void setDimensionCrossing(DimensionCrossing dimensionCrossing) {
        this.dimensionCrossing = java.util.Objects.requireNonNull(
                dimensionCrossing, "dimensionCrossing");
    }

    /** Wires the village surface (the engine's own boot). */
    public void setVillageSystem(net.zaminmc.torch.server.village.VillageSystem system) {
        this.villageSystem = java.util.Objects.requireNonNull(system, "system");
    }
    /**
     * The crafting consult for the sheep's offspring color (Slice 30b —
     * SheepEntity.getColorForOffspring's own
     * {@code CraftingManager.getResult} over the two-dye grid); null until
     * wired, in which case the mix falls to the parent coin alone.
     */
    private volatile net.zaminmc.torch.server.crafting.CraftingService craftingConsult;

    /**
     * The staged zombie-villager infections (Slice 25a): the killing blow
     * lands deep inside the melee walk, so the replacement defers to the
     * tick loop's close — the same deferred shape the breeds and the
     * splits carry. Tick-lane only (hurtByMob runs inside the loop).
     */
    private final List<MobEntity> stagedInfections = new ArrayList<>();

    public MobManager(MobEntity.WorldQuery world, Random random, LootSink lootSink,
                      int firstEntityId,
                      java.util.function.BiFunction<Integer, Integer, Integer> surfaceY) {
        this.world = Objects.requireNonNull(world, "world");
        this.random = Objects.requireNonNull(random, "random");
        this.lootSink = Objects.requireNonNull(lootSink, "lootSink");
        this.nextEntityId = firstEntityId;
        this.surfaceY = Objects.requireNonNull(surfaceY, "surfaceY");
    }

    /**
     * Wires the difficulty read (Slice 19e — the reference's MonsterEntity
     * .tick model: a hostile body on a peaceful world leaves the very
     * next tick, whatever path brought it there).
     */
    public void setDifficultyRead(java.util.function.IntSupplier difficultyRead) {
        this.difficultyRead = java.util.Objects.requireNonNull(difficultyRead, "difficultyRead");
    }

    /**
     * Wires the local-difficulty consult (VIII14 — the engine's boot: the
     * spawn dice's multiplier reads the real clock/inhabited/moon terms
     * at the spawn position).
     */
    public void setLocalDifficultyRead(
            java.util.function.BiFunction<Position, Integer,
                    net.zaminmc.torch.server.world.LocalDifficulty> localDifficultyRead) {
        this.localDifficultyRead = java.util.Objects.requireNonNull(
                localDifficultyRead, "localDifficultyRead");
    }

    /**
     * The thorns retaliation consult (the README TODO's row 8 — the
     * reference's Entity.damageEntity base method over a MOB victim:
     * MonsterEntity.attack / IronGolemEntity.tryDamage / SlimeEntity /
     * WolfEntity's melee walks fire it on every landed hit): the VICTIM's
     * worn thorns bite the attacking mob back. Wired from the engine
     * (EngineServer owns the walk — the same shape as the player-victim
     * sites). Null until the engine wires it; the unwired boot keeps the
     * historical no-retaliation behavior (the same fallback law as the
     * local-difficulty seam).
     */
    public interface ThornsRetaliation {
        /** The reference's {@code damageEntity(source, entity)} pair. */
        void biteBack(MobEntity victim, MobEntity attacker);
    }

    private volatile ThornsRetaliation thornsRetaliation;

    /** Wires the thorns retaliation consult (the engine's own boot). */
    public void setThornsRetaliation(ThornsRetaliation thornsRetaliation) {
        this.thornsRetaliation = java.util.Objects.requireNonNull(
                thornsRetaliation, "thornsRetaliation");
    }

    /**
     * The {@code doMobLoot} read (Slice 24a — LivingEntity.die's gate:
     * the loot rolls and the XP release both skip when the rule is
     * off). Null keeps the historical always-drop behavior.
     */
    private volatile java.util.function.BooleanSupplier mobLootRead;

    /** Wires the {@code doMobLoot} read (the game-rule table's loot gate). */
    public void setMobLootRead(java.util.function.BooleanSupplier mobLootRead) {
        this.mobLootRead = java.util.Objects.requireNonNull(mobLootRead, "mobLootRead");
    }

    /**
     * The player-stat award seam (VIII6 — the body-side award sites whose
     * walks live inside a body's own tick, the reference's
     * {@code playerentity.incrementStat} calls): given the earning player's
     * engine id and the stat key, the engine resolves the session and
     * increments. Injected into every spawned body (the spawn walk); null
     * keeps the sites no-ops.
     */
    private volatile java.util.function.BiConsumer<Integer, String> playerStatAward;

    /** Wires the player-stat award seam (the engine's own resolution walk). */
    public void setPlayerStatAward(java.util.function.BiConsumer<Integer, String> award) {
        this.playerStatAward = java.util.Objects.requireNonNull(award, "award");
    }

    /**
     * The rider's fall payment (F-RIDER-SPARE — the mount's landing
     * delegate): the engine resolves the seated player's session by id
     * and pays the same (distance, multiplier) pair the mount's own
     * wound reads. Injected into every spawned body (the spawn walk);
     * null keeps the site a no-op.
     */
    private volatile MobEntity.RiderFall riderFall;

    /** Wires the rider-fall seam (the engine's own resolution walk). */
    public void setRiderFall(MobEntity.RiderFall fall) {
        this.riderFall = java.util.Objects.requireNonNull(fall, "fall");
    }

    /** @return whether mob loot may drop (the doMobLoot rule's view). */
    boolean mobLootEnabled() {
        java.util.function.BooleanSupplier read = mobLootRead;
        return read == null || read.getAsBoolean();
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * The equipment-change publication (Slice 78b — the /summon Equipment
     * row and the restore's held stack land AFTER the spawn packet went
     * out): every listener re-sends the Entity Equipment. Tick-thread
     * context.
     */
    public void equipmentChanged(MobEntity mob) {
        Objects.requireNonNull(mob, "mob");
        for (Listener listener : listeners) {
            listener.onMobEquipmentChanged(mob);
        }
    }

    /**
     * The attributes-change publication (ML-011 — the /summon and
     * /entitydata Attributes rows land after the spawn packet went out,
     * the same convention as the equipment wake): every listener
     * re-sends the Entity Properties (0x20). Tick-thread context.
     */
    public void attributesChanged(MobEntity mob) {
        Objects.requireNonNull(mob, "mob");
        for (Listener listener : listeners) {
            listener.onMobAttributesChanged(mob);
        }
    }

    /** Wires the potion system (the witch's drink lands its effects through it). */
    /** Wires the item-entity world for the loot-pickup scan (Slice 92). */
    public void setItemEntitiesForPickup(ItemEntityManager items) {
        this.itemEntities = items;
    }

    /** Wires the mobGriefing rule read (the pickup walk's gate). */
    public void setMobGriefingRead(java.util.function.BooleanSupplier read) {
        this.mobGriefingRead = read;
    }

    public void setPotionSystem(net.zaminmc.torch.server.potion.PotionSystem potionSystem) {
        this.potionSystem = potionSystem;
    }

    /**
     * Wires the crafting consult the sheep's offspring color rides
     * (SheepEntity.getColorForOffspring lines 262-276 — the reference's
     * own CraftingManager consult over the two-dye 2x1 grid).
     */
    public void setCraftingConsult(net.zaminmc.torch.server.crafting.CraftingService consult) {
        this.craftingConsult = consult;
    }

    /**
     * Wires the owner-lane admission every spawned body's mind mutations
     * ride (§27.6's explicit command protocol — the world's {@code execute}).
     * Called once by the server after the world's admission exists.
     */
    public void setGoalAdmission(java.util.function.Consumer<Runnable> admission) {
        this.goalAdmission = admission;
    }

    /** The leash system's detach hook (the public unleash path). */
    private java.util.function.BiConsumer<MobEntity, Boolean> leashDetacher;

    /**
     * Wires the leash system's detach walk behind the public
     * {@code LivingEntity.unleash} (§27.3's leash family). Called once by
     * the server when the LeadSystem attaches — the setter retro-wires
     * every already-spawned body too (the boot order restores mobs before
     * the leash system exists), and {@code spawnAt} wires fresh spawns.
     */
    public void setLeashDetacher(java.util.function.BiConsumer<MobEntity, Boolean> detacher) {
        this.leashDetacher = detacher;
        for (MobEntity mob : mobs) {
            mob.setLeashDetacher(drop -> detacher.accept(mob, drop));
        }
    }

    /**
     * The public equipment write path (§27.3's VIII4 exposure — the
     * {@code LivingEntity.setEquipmentAt} handle's wired route): the
     * §27.6 owner-context assert, the one authoritative
     * {@link MobEntity#setEquipment}, then the {@link #equipmentChanged}
     * re-publish. The three-argument seam follows the SkullExplosionSink
     * precedent (no standard shape carries a body + a slot + a stack).
     */
    @FunctionalInterface
    public interface EquipmentWritePath {
        /**
         * @param stack the engine's own bare form — {@code null} clears
         *              the slot (the mapping the api adapter makes from
         *              the canonical empty stack)
         */
        void equip(MobEntity mob, int slot, ItemStack stack);
    }

    /** The equipment write path (null until the server wires it). */
    private EquipmentWritePath equipmentWritePath;

    /**
     * Wires the public equipment write path behind the §27.6 owner-context
     * assert. Called once by the server — the setter retro-wires every
     * already-spawned body too (the boot order restores mobs before the
     * write path is wired), and {@code spawnAt} wires fresh spawns.
     */
    public void setEquipmentWritePath(EquipmentWritePath path) {
        this.equipmentWritePath = path;
        for (MobEntity mob : mobs) {
            mob.setEquipmentWritePath((slot, stack) -> path.equip(mob, slot, stack));
        }
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

    /**
     * @return an unmodifiable snapshot of the live mobs — consistent by
     * construction even while the tick thread mutates (diagnostics/tests
     * poll this off-thread; the copy-on-write list guarantees the view).
     */
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
     * The dimension-aware box consult (Wave TF-9 — the reference's
     * same-world entity scan): only a body in the named dimension answers.
     * The projectile hit resolver reads it — a nether arrow never wounds
     * the overworld mob at the same coordinates.
     */
    public MobEntity mobAt(Position point, int dimension) {
        for (MobEntity mob : mobs) {
            if (mob.dead() || mob.dimension() != dimension) {
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
     * The sleep-refusal monster probe (PlayerEntity.trySleep lines 1101-1109,
     * the NOT_SAFE arm's exact rule and radius): whether any hostile body's
     * own box intersects the reference's bed box — x/z ±8, y ±5 around the
     * bed cell (the {@code getEntitiesOfType(MonsterEntity.class, box)}
     * consult; the neutral-by-day kinds count too — the reference's class
     * hierarchy decides, never the temper). Tick-thread context.
     */
    public boolean monstersNearBed(net.zaminmc.torch.block.BlockPosition bed) {
        for (MobEntity mob : mobs) {
            if (mob.dead() || !mob.type().hostile) {
                continue;
            }
            Position at = mob.position();
            double halfWidth = mob.type().width / 2.0;
            // The box-intersection the reference's entity query answers:
            // the mob's feet-anchored box (width x height) against the bed
            // box's own spans.
            boolean overlaps = at.x() + halfWidth >= bed.x() - 8.0
                    && at.x() - halfWidth <= bed.x() + 8.0
                    && at.z() + halfWidth >= bed.z() - 8.0
                    && at.z() - halfWidth <= bed.z() + 8.0
                    && at.y() + mob.type().height >= bed.y() - 5.0
                    && at.y() <= bed.y() + 5.0;
            if (overlaps) {
                return true;
            }
        }
        return false;
    }

    /**
     * Spawns one mob of the kind at the exact position (validated by callers).
     * Tick-thread context.
     *
     * <p>VT-012 — the dimensionless arm INHERITS the armed spawn context
     * ({@code spawnDimension}, the VIII14 field): the nether natural cycle
     * and the zombie reinforcement both arm the field around their calls
     * (their dice consult the acting dimension), and their bodies are born
     * in that same armed dimension — the reference's construction law
     * (Entity line 166) stamps at birth, so the armed context is the birth
     * dimension. Unarmed callers keep the field's 0 (today's behavior).</p>
     *
     * @return the spawned mob, or null when the public
     *         {@link net.zaminmc.torch.event.entity.MobSpawnEvent} cancelled
     *         the spawn (no id consumed, no listeners notified)
     */
    public MobEntity spawnAt(MobType type, Position position) {
        return spawnAt(type, position, spawnDimension);
    }

    /**
     * Spawns one mob of the kind at the exact position IN THE GIVEN
     * DIMENSION (VT-012 — the reference's construction law: Entity's
     * constructor line 166 stamps {@code this.dimension =
     * world.dimension.getId()} at BIRTH, before any spawn event can
     * fire; the engine's spawn fan — the wire's own sendMobSpawn gate
     * reads the stamp at the event — therefore stamps the body BEFORE
     * the listener fan, not after the call returns). The dimension also
     * rides the VIII14 dice context (the zombie door-break / equipment /
     * leader rolls consult the ACTING dimension's local difficulty).
     * Tick-thread context.
     *
     * @return the spawned mob, or null when the public
     *         {@link net.zaminmc.torch.event.entity.MobSpawnEvent} cancelled
     *         the spawn (no id consumed, no listeners notified)
     */
    public MobEntity spawnAt(MobType type, Position position, int dimension) {
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
        // VIII14 + VT-012 — the spawn's dimension context: the dice below
        // consult the ACTING dimension's local difficulty (the nether egg's
        // zombie rolls at the nether's own reads, the spawnNaturalNether
        // precedent's own field law).
        int priorDimension = spawnDimension;
        spawnDimension = dimension;
        MobEntity mob;
        try {
            mob = spawnAtCore(type, position, dimension);
        } finally {
            spawnDimension = priorDimension;
        }
        return mob;
    }

    /** The construction + fan (the dimension already armed). */
    private MobEntity spawnAtCore(MobType type, Position position, int dimension) {
        MobEntity mob = new MobEntity(nextEntityId, type, position, random, world);
        if (type == MobType.ENDER_DRAGON) {
            // The wire's part-id convention (ClientPlayNetworkHandler lines
            // 701-708): the client re-aligns its locally-built parts to
            // the spawn packet's dragonId — the ids dragonId+1..+7 must be
            // RESERVED so the next seven spawns never collide with them.
            nextEntityId += 8;
        } else {
            nextEntityId++;
        }
        // VT-012 — the birth stamp (Entity's constructor line 166: the
        // body is born in its acting world; the stamp lands BEFORE the
        // spawn fan so the wire's own dimension gate reads the born
        // dimension at the event, never the Java-default 0).
        mob.setDimension(dimension);
        mob.setBreedFinder(this::scanForMate);
        mob.setRemovalHook(() -> remove(mob, "api")); // the public despawn path
        // The public equipment write path (the VIII4 exposure): fresh
        // spawns carry the wired route (assert + write + re-publish) —
        // the leash detacher's own spawn-wiring form.
        EquipmentWritePath equipPath = equipmentWritePath;
        if (equipPath != null) {
            mob.setEquipmentWritePath((slot, stack) -> equipPath.equip(mob, slot, stack));
        }
        if (playerStatAward != null) {
            // VIII6: the body-side award sites' seam (the pig's rider fall —
            // the engine resolves the rider session by id at the award).
            mob.setPlayerStatAward(playerStatAward);
        }
        var fall = riderFall;
        if (fall != null) {
            // F-RIDER-SPARE: the mount's landing delegate (the seated
            // player pays the fall with the mount's own multiplier).
            mob.setRiderFall(fall);
        }
        var detacher = leashDetacher;
        if (detacher != null) {
            mob.setLeashDetacher(drop -> detacher.accept(mob, drop)); // the public unleash path
        }
        mob.setGoalAdmission(goalAdmission); // §27.6: mind mutations admit onto the owner lane
        // Slice 92 — the body's own effect consults (the breath clock's
        // WATER_BREATHING shield, the fall walk's JUMP_BOOST term): the
        // live store read through the potion system, the amplifier + 1
        // term shape the reference's own walks read.
        mob.setEffectAmplifierQuery(effectId -> effectAmplifierPlusOne(mob, effectId));
        if (type == MobType.SKELETON) {
            // SkeletonEntity.initialize's else arm (line 224 →
            // addRandomEquipment line 211): every archer spawns with the
            // bow in hand — the nether's wither conversion replaces it
            // with the stone sword at its own roll below.
            mob.setHeldEquipment(ItemStack.of(BuiltinItems.BOW, 1));
        }
        if (type == MobType.SKELETON || type == MobType.ZOMBIE
                || type == MobType.ZOMBIE_PIGMAN) {
            // The initialize rolls (SkeletonEntity line 228, ZombieEntity
            // line 399 — the pigman inherits the zombie's own walk):
            // setCanPickupLoot(nextFloat() < 0.55F * the local difficulty
            // multiplier).
            mob.setCanPickUpLoot(random.nextFloat() < 0.55F * localDifficultyMultiplier(position));
        }
        if (type == MobType.ZOMBIE) {
            // VIII9 — initialize line 430: the door-break arming roll
            // ({@code nextFloat() < f * 0.1F}, f the local difficulty
            // multiplier), BEFORE the equipment dice exactly as the
            // reference orders the initialize walk.
            rollZombieCanBreakDoors(mob, random, localDifficultyMultiplier(position));
        }
        if (type == MobType.SKELETON || type == MobType.ZOMBIE) {
            // The armor family's spawn dice (VIII4 — MobEntity
            // .addRandomEquipment lines 593-623 through the family's own
            // overrides: the skeleton's super + bow, the zombie's super +
            // the iron tool arm; the pigman's override replaces the whole
            // walk with the golden sword alone, so it never rolls here).
            // The nether's wither arm REPLACES the walk (initialize's
            // if/else — the variant strip rides the nether spawn below).
            rollSpawnEquipment(mob, random, localDifficultyMultiplier(position),
                    difficultyId());
        }
        if (type == MobType.ZOMBIE) {
            // VIII9 — initialize lines 447-451: the leader roll, AFTER the
            // equipment dice (the reference's own order). The leader gains
            // the reinforcement bonus, the 40..100 health band, and the
            // forced door-break.
            rollZombieLeader(mob, random, localDifficultyMultiplier(position));
        }
        if (type == MobType.VILLAGER) {
            // VillagerEntity's own constructor arm (line 322): the
            // villager ALWAYS picks up (the food-in-hand walk).
            mob.setCanPickUpLoot(true);
            // VIII10 — the mating gate consult (the manager's own village
            // walk over checkVillageValidity's gates).
            mob.setVillagerMatingGate(() -> villagerMatingGate(mob));
        }
        if (type == MobType.WITCH) {
            // The drink ladder's hasStatusEffect gates (mobTick lines
            // 107-121): the live store read through the potion system.
            mob.setWitchEffectQuery(effectId -> witchHasEffect(mob, effectId));
        }
        if (type == MobType.RABBIT) {
            // RabbitEntity.initialize (lines 336-353): the natural coat —
            // {@code nextInt(6)}, the six natural skins; the killer's 99
            // only ever lands through setVariant (the NBT /summon arm).
            mob.setRabbitVariant(random.nextInt(6));
        }
        if (type == MobType.SHEEP) {
            // SheepEntity.initialize line 256 → pickColor (lines 225-238):
            // the natural fleece roll — 5% black, then gray, then silver,
            // then brown (18/100 all told), else the 1-in-500 pink over
            // the white default.
            mob.setSheepFleeceColor(pickSheepColor(random));
        }
        if (type == MobType.HORSE) {
            // HorseBaseEntity.initialize lines 1278-1284: the MAX_HEALTH
            // roll (line 1278 over getChildHealthBonus line 1386 — 15 +
            // nextInt(8) + nextInt(9)) and the MOVEMENT_SPEED roll (line
            // 1281 over getMovementSpeedBonus line 1390 — the donkey/mule
            // flat of line 1283 lands at walkSpeed()'s live-subtype read).
            rollHorseSpawnAttributes(mob, random);
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
     * The egg's own spawn ({@code SpawnEggItem.spawnEntity} lines 131-153):
     * the reference's setPositionAndAngles — the wrapped random facing over
     * the shared random — then the ambient sound the reference's
     * playAmbientSound plays at the spawn (the silent kinds stay silent).
     * The per-kind initialize arms (the skeleton's bow, the witch's effect
     * query) already ride {@link #spawnAt}. Tick-thread context.
     *
     * @return the spawned mob, or null when the spawn event cancelled it
     */
    public MobEntity spawnFromEgg(MobType type, Position position) {
        return spawnFromEgg(type, position, 0);
    }

    /**
     * The egg's spawn in the acting dimension (VT-012 —
     * SpawnEggItem.spawnEntity's own {@code world} parameter: the body
     * is born in the world the egg was used in; the stamp lands before
     * the spawn fan through the dimension-aware {@link #spawnAt}).
     */
    public MobEntity spawnFromEgg(MobType type, Position position, int dimension) {
        MobEntity spawned = spawnAt(type, position, dimension);
        if (spawned != null) {
            // MathHelper.wrapDegrees(world.random.nextFloat() * 360.0F):
            // the random facing [0, 360) wrapped to [-180, 180).
            float facing = wrapDegrees(random.nextFloat() * 360.0f);
            spawned.setBodyYaw(facing);
            // playAmbientSound: getAmbientSound() == null never plays — the
            // engine's empty-string idle is the same silence. The play pair
            // rides the volume/pitch consults (the VIII14 cadence slice):
            // getSoundVolume then getSoundPitch, the reference's own order.
            String ambient = spawned.effectiveIdleSound();
            if (!ambient.isEmpty()) {
                for (Listener listener : listeners) {
                    listener.onMobSoundAt(spawned, ambient,
                            spawned.ambientSoundVolume(), spawned.ambientSoundPitch());
                }
            }
        }
        return spawned;
    }

    /** {@link MathHelper#wrapDegrees}' own band: the value to [-180, 180). */
    private static float wrapDegrees(float value) {
        float f = value % 360.0f;
        if (f >= 180.0f) {
            f -= 360.0f;
        }
        if (f < -180.0f) {
            f += 360.0f;
        }
        return f;
    }

    /**
     * Spawns {@code count} mobs of the kind near the given center on the
     * surface (the command path). Tick-thread context.
     */
    public List<MobEntity> spawnGroup(MobType type, Position center, int count) {
        return spawnGroupAt(type, center, count, 0, 3, 0);
    }

    /**
     * The group spawn in the acting dimension (VT-012 — the command
     * source's {@code getCommandSourceWorld()}: every member is born in
     * the sender's world; the stamp lands before each member's own spawn
     * fan through the dimension-aware {@link #spawnAt}).
     */
    public List<MobEntity> spawnGroup(MobType type, Position center, int count,
            int dimension) {
        return spawnGroupAt(type, center, count, 0, 3, dimension);
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
        return spawnGroupAt(type, center, count, minOffset, maxOffset, 0);
    }

    /**
     * The group spawn's dimension-aware arm (VT-012 — every member born
     * in the acting dimension through the dimension-aware
     * {@link #spawnAt(MobType, Position, int)}).
     */
    public List<MobEntity> spawnGroupAt(MobType type, Position center, int count,
                                        int minOffset, int maxOffset, int dimension) {
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
            MobEntity mob = spawnAt(type, new Position(x + 0.5, y + 1.0, z + 0.5),
                    dimension);
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
        // The copy-on-write iterator snapshots, so the removals below never
        // disturb the walk (the defensive ArrayList copy was the old guard).
        for (MobEntity mob : mobs) {
            if (mob.type().hostile) {
                remove(mob, "peaceful");
            }
        }
    }

    // (populateInitial removed — Slice 12h: the fresh world's animals arrive
    // through the vanilla worldgen seeding, NaturalSpawner.populateChunk's
    // engine port, not the synthetic spawn packs.)

    /**
     * The natural fleece roll (SheepEntity.pickColor lines 225-238, 1:1):
     * 5/100 black, the next 5/100 gray, the next 5/100 silver, the next
     * 3/100 brown, else the 1-in-500 pink over the white default.
     */
    static int pickSheepColor(java.util.Random roll) {
        int i = roll.nextInt(100);
        if (i < 5) {
            return 15; // DyeColor.BLACK.getId()
        } else if (i < 10) {
            return 7; // GRAY
        } else if (i < 15) {
            return 8; // SILVER
        } else if (i < 18) {
            return 12; // BROWN
        }
        return roll.nextInt(500) == 0 ? 6 /* PINK */ : 0 /* WHITE */;
    }

    /**
     * The horse's spawn-time attribute rolls (HorseBaseEntity.initialize
     * lines 1278-1284, in the reference's own order): MAX_HEALTH first
     * (line 1278 over getChildHealthBonus line 1386 — {@code 15 +
     * nextInt(8) + nextInt(9)}), then MOVEMENT_SPEED (line 1281 over
     * getMovementSpeedBonus line 1390 — {@code (0.45 + 3x nextDouble()*0.3)
     * * 0.25}). The reference rolls the speed only on the {@code i == 0}
     * branch; the engine rolls it for every body and lets the consult's
     * live-subtype read take the donkey/mule flat (initialize line 1283) —
     * the rows that overwrite the subtype after the spawn stay coherent
     * either way. Package-private static so the tests drive the dice with
     * their own seeded random.
     */
    static void rollHorseSpawnAttributes(MobEntity mob, java.util.Random random) {
        mob.applyHorseSpawnHealth(15 + random.nextInt(8) + random.nextInt(9));
        mob.rollHorseSpawnSpeed(random);
    }

    /**
     * Shears a sheep on a player's behalf (SheepEntity.interactMob lines
     * 146-167, 1:1): the baby gate (a lamb never shears), 1-3 wool of the
     * body's own color dropping around the coat, the strip, and the report
     * through {@code onMobSheared}. Returns the wool count (0 when the kind
     * is bald, already shorn, or a baby). Tick-thread context.
     */
    public int shear(MobEntity mob) {
        Objects.requireNonNull(mob, "mob");
        if (mob.isBaby() || !mob.shear()) {
            // SheepEntity.interactMob line 149's own gate order: the whole
            // arm — the strip included — never runs for a lamb.
            return 0;
        }
        int wool = 1 + random.nextInt(3);
        // The shear drops (interactMob lines 150-159): every unit its own
        // single-wool stack of the body's color — the wool item's damage
        // IS the DyeColor id (BlockColored's damage domain) — spawned at
        // the coat with the spread the item-entity system owns.
        Position at = mob.position();
        for (int unit = 0; unit < wool; unit++) {
            lootSink.spawnLootDrop(new Position(at.x(), at.y() + mob.type().height, at.z()),
                    new ItemStack(BuiltinItems.WOOL, 1, mob.sheepFleeceColor()), mob.dimension());
        }
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
    public boolean mountMob(MobEntity mob, int riderEngineId,
            String ownerName, String ownerUuid) {
        Objects.requireNonNull(mob, "mob");
        if (!mob.isMountable() || mob.hasRider() || mob.dead()) {
            return false;
        }
        MobEntity.MountAttempt attempt = mob.attemptMount(ownerName, ownerUuid);
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
     * Seats a restored rider (the anvil load path — the reference's
     * startRiding at AnvilChunkStorage line 335: the plain seat, NO
     * temper flow, no buck — the load never consults the live interact's
     * mount-attempt law). The listener still fires (the attach
     * publication flows through the manager host, the §27.7-style
     * convention the import rides). Tick-thread context.
     */
    public void seatImportedRider(MobEntity mob, int riderEngineId) {
        Objects.requireNonNull(mob, "mob");
        if (mob.hasRider() || mob.dead()) {
            return;
        }
        mob.setRider(riderEngineId);
        for (Listener listener : listeners) {
            listener.onMobMounted(mob, riderEngineId);
        }
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
     * VT-006 — the entity-level portal arm (the reference's Entity.baseTick
     * portal arm, lines 282-313, fed by PortalBlock.onEntityCollision's
     * gated collision): the collision consult (the riding gate — VT-005's
     * law: a seated body never arms), the entry memory's derive (a NEW
     * cell re-derives the frame offsets), and the base-entity clock (the
     * threshold 0, the 300 cooldown). Runs on the flipped shared query —
     * the body's own world.
     *
     * @return true when the crossing fired (the caller skips the body's
     *         own walk — the reference's removed-body shape: the
     *         recreated body never runs its mind on the arrival tick)
     */
    private boolean tickPortalArm(MobEntity mob) {
        Position at = mob.position();
        int feetX = (int) Math.floor(at.x());
        int feetY = (int) Math.floor(at.y());
        int feetZ = (int) Math.floor(at.z());
        // The gated collision consult (PortalBlock.onEntityCollision
        // lines 158-162: vehicle == null && rider == null — the engine's
        // mobs never ride, so the rider consult alone carries the pair).
        var match = mob.hasRider() ? null : world.portalFrameAt(feetX, feetY, feetZ);
        if (match != null) {
            // The entry memory's derive (onPortalCollision lines 1432-1451:
            // the same-cell re-collision re-derives nothing).
            mob.notePortalEntry(new net.zaminmc.torch.block.BlockPosition(
                    feetX, feetY, feetZ), match, at.x(), at.y(), at.z());
        }
        if (mob.advanceEntityPortalClock(match != null)) {
            crossMobDimension(mob);
            return true;
        }
        return false;
    }

    /**
     * VT-006 — the crossing walk (Entity.changeDimension lines
     * 1695-1730): the old world's destroy fan FIRST (the TF-12 wire
     * reads the stamp at send time — the reference's old-world tracker's
     * EntityDestroy), then the service's reposition + the forcer's
     * placement, then the stamp swap and the new world's spawn fan (the
     * destination tracker's Spawn Mob — the full metadata packet). The
     * body keeps its engine id and its whole state (the reference's
     * copyNbtFrom — the recreated body carries everything the NBT
     * holds); the persistence rides the stamp (the snapshot's own
     * dimension row).
     */
    private void crossMobDimension(MobEntity mob) {
        DimensionCrossing crossing = dimensionCrossing;
        int from = mob.dimension();
        if (crossing == null || from == 1) {
            // The unwired boot (the bare test harnesses) and the
            // End-origin arm (the reference's else branch reads the
            // destination's forced-spawn consult, which the engine does
            // not carry yet — the audit's named tail): the crossing
            // holds, the cooldown keeps the body from re-firing.
            return;
        }
        Position at = mob.position();
        for (Listener listener : listeners) {
            listener.onMobRemoved(mob, "portal");
        }
        DimensionCrossing.Crossing result = crossing.cross(from,
                at.x(), at.y(), at.z(), mob.yaw(), mob.velocityX(), mob.velocityZ(),
                mob.portalMemory());
        mob.applyDimensionArrival(result.toDimension(),
                new Position(result.arrival().x(), result.arrival().y(), result.arrival().z()),
                result.arrival().yaw(),
                result.arrival().velocityX(), result.arrival().velocityZ());
        for (Listener listener : listeners) {
            listener.onMobSpawned(mob);
        }
        LOGGER.info(() -> "Mob " + mob.type() + " " + mob.entityId()
                + " crossed to dimension " + result.toDimension());
    }

    /**
     * Advances all mobs one tick: mind + body, melee, arrows, fuses, loot
     * + removal for finished deaths, despawn and the natural spawn cycle
     * (the NaturalSpawnSystem walks after this — the engine wires it).
     * Tick-thread context.
     */
    public void tick(Iterable<PlayerSession> players, long timeOfDay) {
        boolean night = isNight(timeOfDay);
        this.lastTimeOfDay = timeOfDay; // the local difficulty's own time term

        // The peaceful sweep (Slice 19e — MonsterEntity.tick lines 33-38):
        // a hostile body on a peaceful world leaves the very next tick —
        // the arrival path never matters (the spawner, the egg, the
        // command, the conversion).
        java.util.function.IntSupplier difficultyAt = difficultyRead;
        if (difficultyAt != null && difficultyAt.getAsInt() == 0) {
            removeHostiles();
        }

        // The breeds defer to the loop's end (the reference's own order:
        // children join the world after the walk closes — and the
        // copy-on-write iterator below would not visit a mid-walk add
        // anyway, the deferral keeps both laws the same shape).
        List<MobEntity[]> pendingBreeds = new ArrayList<>();
        // The slime splits defer the same way (the children join after the
        // iteration closes — the reference's remove() runs inside its own
        // removal walk, the engine's tick loop owns the equivalent).
        List<MobEntity> pendingSlimeSplits = new ArrayList<>();
        // The zombie-villager infections and conversions defer the same
        // way (Slice 25a — onKill's replacement and convertToVillager's
        // spawn/remove pair both land after the loop closes; the infections
        // stage through the field the melee walk writes).
        List<MobEntity> pendingConversions = new ArrayList<>();

        Iterator<MobEntity> iterator = mobs.iterator();
        while (iterator.hasNext()) {
            MobEntity mob = iterator.next();

            if (mob.deathAnimationFinished()) {
                remove(mob, "died"); // mid-walk removal is copy-on-write safe
                dropLoot(mob); // the loot lands after the body leaves the wire
                dropEquipment(mob); // the equipment walk rides beside the loot
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
                world.setActiveDimension(mob.dimension()); // the body's own world (25d)
                mob.tick(night);
                continue;
            }

            // VT-006 — the entity-level portal arm (Entity.baseTick lines
            // 282-313, the BASE law): the reference runs the portal walk at
            // the START of every ticked body's tick, before the despawn
            // window and the mind (baseTick precedes mobAiTick — a body
            // standing in the cells crosses rather than despawning on the
            // same tick). The shared query flips to the body's own world
            // for the collision read (25d).
            world.setActiveDimension(mob.dimension());
            if (tickPortalArm(mob)) {
                continue; // the crossing consumed this body's walk
            }

            // The undead's daylight burn moved INTO the family's own tick
            // clock (MobEntity.tickDaylightBurn — the reference's mobTick
            // arms 1:1: the isSunny/brightness/roll/sky gates, the helmet
            // wear, the baby gate, the pigman and the wither variant
            // riding their parents' walks under fire immunity). The old
            // dimension-blind night-window sweep is retired — the walk
            // consults the body's own dimension's sun through the flipped
            // shared query below, exactly the reference's per-world read.

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
                        || player.dead()
                        // Slice 25d: the despawn window measures the body's
                        // own dimension's players only.
                        || player.dimension() != mob.dimension()) {
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
                remove(mob, "despawned"); // mid-walk removal is copy-on-write safe
                continue;
            }

            // The breeding clocks (the vanilla love/age/breed-goal trio).
            tickBreedingFor(mob);

            // VIII10 — mobAiTick's village scan cadence (VillagerEntity
            // lines 353-368): the 70 + nextInt(50) window marks the
            // villager's cell for the door census (the reference's own
            // markForUpdate call) and pays the converted-zombie's +5
            // reputation over the villager's own village once (lines
            // 363-366 — the cured villager's first scan). The scan also
            // resolves the village CACHE (lines 356-362: the nearest
            // village within 32 writes its center/radius onto the body —
            // the village-return walk's own seat; no village resets it).
            if (mob.type() == MobType.VILLAGER && villageSystem != null
                    && mob.tickVillagerScanCooldown(random)) {
                Position at = mob.position();
                int scanX = (int) Math.floor(at.x());
                int scanY = (int) Math.floor(at.y());
                int scanZ = (int) Math.floor(at.z());
                villageSystem.markForUpdate(scanX, scanY, scanZ);
                var village = villageSystem.nearestVillage(scanX, scanY, scanZ, 32);
                if (village == null) {
                    mob.resetVillageCache();
                } else {
                    mob.setVillageCache(village.centerX(), village.centerY(),
                            village.centerZ(), village.radius());
                }
                if (mob.consumeVillagerConvertedZombie()) {
                    if (village != null) {
                        village.updateAllReputations(5);
                    }
                }
            }
            // VIII10 — the golem's village defense (IronGolemEntity
            // .mobAiTick lines 66-79 + TrackIronGolemTargetGoal): the
            // target consult runs BEFORE the mind's own walk so the
            // defense target owns the hunt.
            if (mob.type() == MobType.IRON_GOLEM) {
                tickGolemVillageDefense(mob);
            }

            boolean wasPriming = mob.fuseActive();
            boolean wasBurning = mob.burning();
            int wasHorseFlags = mob.horseFlagsRaw();
            boolean wasWitchDrinking = mob.witchDrinking();
            boolean wasBegging = mob.wolfBegging();
            int wasWolfState = mob.wolfFlagsRaw();
            int wasPosture = mob.postureFlags();
            boolean wasGhastCharging = mob.ghastCharging();
            boolean wasGuardianMoving = mob.guardianMoving();
            boolean wasGuardianBeam = mob.guardianBeamTargetId() != 0;
            // Slice 25d: the shared query flips to the body's own world
            // before every walk (the reference's Entity.world — the nether
            // bodies' physics, scans, and pathing read the nether's blocks).
            world.setActiveDimension(mob.dimension());
            boolean moved = mob.tick(night);
            // The loot-pickup scan (Slice 92 — MobEntity.mobTick lines
            // 339-347, riding AFTER the mob's own tick like the
            // reference's ai-section order): a pickup-armed, non-pinned,
            // living body scans the item entities in its grown box.
            tickLootPickup(mob);
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
            // The daylight wear's exhaustion (LivingEntity.onBrokenItem
            // lines 728-735 over the burn arm's helmet shatter): the
            // reference's own pair — the "random.break" sound at 0.8F
            // with its staged pitch jitter, and the ITEM_CRACK crumbs at
            // the body (the adapter's iconcrack burst).
            MobEntity.EquipmentBreak broke = mob.consumeEquipmentBreak();
            if (broke != null) {
                for (Listener listener : listeners) {
                    listener.onMobSoundAt(mob, "random.break", 0.8f, broke.pitch());
                    listener.onMobItemShattered(mob, broke.stack());
                }
            }
            if (wasBegging != mob.wolfBegging() || wasWolfState != mob.wolfFlagsRaw()) {
                // The wolf-state block's writers (the begging byte 19 and
                // the state byte 16 — the goal-driven flips land here too:
                // the perch's sit/stand pair, the anger's rise and its
                // mobTick clear).
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
            // The bat's own takeoff event (BatEntity.mobAiTick lines 99-113,
            // both roost-exit arms): the 1015 world event at the body.
            if (mob.consumeBatTakeoff()) {
                for (Listener listener : listeners) {
                    listener.onBatTakeoff(mob);
                }
            }
            // The wither's own band (Slice 59 — WitherEntity.mobAiTick's
            // event family): the blue window's birth, the loosed skulls,
            // the sweep walk, the head Ints' dirty resend, and the two
            // sounds. The sweep fires BEFORE the break-sound consume so
            // the walk's own mark (a block fell) lands in the same tick.
            if (mob.type() == MobType.WITHER) {
                if (mob.consumeWitherBirth()) {
                    for (Listener listener : listeners) {
                        listener.onWitherBirth(mob);
                    }
                }
                int witherSkullHead = mob.consumeWitherSkullHead();
                if (witherSkullHead >= 0) {
                    for (Listener listener : listeners) {
                        listener.onWitherSkull(mob, witherSkullHead,
                                mob.pendingWitherSkullX(), mob.pendingWitherSkullY(),
                                mob.pendingWitherSkullZ(), mob.pendingWitherSkullCharged());
                    }
                }
                if (mob.consumeWitherSweep()) {
                    for (Listener listener : listeners) {
                        listener.onWitherSweep(mob);
                    }
                }
                if (mob.consumeWitherMetadataDirty()) {
                    for (Listener listener : listeners) {
                        listener.onWitherMetadataChanged(mob);
                    }
                }
                if (mob.consumeWitherShootSound()) {
                    for (Listener listener : listeners) {
                        listener.onWitherShootSound(mob);
                    }
                }
                if (mob.consumeWitherBlockBreakSound()) {
                    for (Listener listener : listeners) {
                        listener.onWitherBlockBreakSound(mob);
                    }
                }
            }
            // The ender dragon's own band (Slice 63 — the contact arms +
            // the death walk's events): the wing fling + the head damage
            // (the engine walks the dimension's bodies against the part
            // hulls), the block sweep (the destroyBlocks walk over the
            // End world), the crystal-pop wound, the 1018, the XP batches,
            // and the portal-and-remove.
            if (mob.type() == MobType.ENDER_DRAGON) {
                if (mob.consumeDragonFlungPlayers()) {
                    for (Listener listener : listeners) {
                        listener.onDragonContact(mob);
                    }
                }
                if (mob.consumeDragonSweptBlocks()) {
                    for (Listener listener : listeners) {
                        listener.onDragonSweep(mob);
                    }
                }
                if (mob.consumeDragonCrystalWound()) {
                    for (Listener listener : listeners) {
                        listener.onDragonCrystalWound(mob);
                    }
                }
                if (mob.consumeDragonDeathSound()) {
                    for (Listener listener : listeners) {
                        listener.onDragonDeathSound(mob);
                    }
                }
                int dragonXp = mob.consumeDragonXpBatch();
                if (dragonXp > 0) {
                    for (Listener listener : listeners) {
                        listener.onDragonXpBatch(mob, dragonXp);
                    }
                }
                if (mob.consumeDragonPortal()) {
                    for (Listener listener : listeners) {
                        listener.onDragonPortal(mob);
                    }
                }
            }
            // The wolf's water-shake cycle (Slice 30d — WolfEntity.tick's
            // closing block + mobTick's start gate): the event-8 broadcast,
            // the cycle-start sound at the reference's own volume/pitch
            // pair, and the per-tick splash burst past the 0.4 bend.
            if (mob.type() == MobType.WOLF) {
                mob.tickWolfShake();
                if (mob.consumePendingWolfShakeEvent()) {
                    for (Listener listener : listeners) {
                        listener.onMobStatusEvent(mob, 8); // doEntityEvent(8)
                    }
                }
                if (mob.consumePendingWolfShakeSound()) {
                    for (Listener listener : listeners) {
                        // getSoundVolume 0.4F, the pitch band
                        // (rand - rand) * 0.2 + 1 (line 200).
                        listener.onMobSoundAt(mob, "mob.wolf.shake", 0.4f,
                                (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f);
                    }
                }
                int splashes = mob.consumePendingWolfShakeSplashes();
                if (splashes > 0) {
                    // tick lines 212-221: each unit its own particle at a
                    // random point in the footprint, minY + 0.8 up, riding
                    // the body's own velocity.
                    double halfWidth = mob.type().width * 0.5;
                    for (int unit = 0; unit < splashes; unit++) {
                        double px = mob.position().x()
                                + (random.nextFloat() * 2.0f - 1.0f) * halfWidth;
                        double pz = mob.position().z()
                                + (random.nextFloat() * 2.0f - 1.0f) * halfWidth;
                        double py = mob.position().y() + 0.8;
                        for (Listener listener : listeners) {
                            listener.onWolfShakeSplash(mob, new Position(px, py, pz),
                                    mob.velocityX(), mob.velocityY(), mob.velocityZ());
                        }
                    }
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
            if (mob.consumeTrample()) {
                for (Listener listener : listeners) {
                    listener.onMobTrample(mob);
                }
            }
            if (mob.consumeOreStep()) {
                for (Listener listener : listeners) {
                    listener.onMobStepOre(mob);
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
                // VT-7c — the squid arm's beam id: the reference's
                // setBeamTag carries the ATTACK TARGET's own network id
                // (any LivingEntity — the squid branch included), never a
                // fresh player scan.
                MobEntity squidHunt = mob.guardianHuntSquid();
                if (squidHunt != null && !squidHunt.dead()) {
                    if (mob.guardianBeamActive()) {
                        mob.setGuardianBeamTargetId(squidHunt.entityId());
                    } else {
                        mob.setGuardianBeamTargetId(0);
                    }
                } else if (mob.guardianBeamActive()) {
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
                    if (squidHunt != null && !squidHunt.dead()) {
                        // VT-7c — the squid arm's hit (the reference's
                        // tick lines 508-522 land the same pair on ANY
                        // LivingEntity target): the magic half pierces with
                        // no knockback, then the melee half walks the mob
                        // ladder (the ATTACK_DAMAGE attribute's own).
                        float magic = mob.guardianElder() ? 1.0F + 2.0F : 1.0F;
                        java.util.function.IntSupplier squidDifficultyAt = difficultyRead;
                        if (squidDifficultyAt != null && squidDifficultyAt.getAsInt() == 3) {
                            magic += 2.0F; // the goal's own hard-difficulty arm
                        }
                        hurt(squidHunt, magic, Double.NaN,
                                net.zaminmc.torch.event.DamageCause.MAGIC,
                                false, false, mob, null, true);
                        hurtByMob(mob, squidHunt, mob.attackDamage());
                    } else {
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
            // The villager's level-up countdown (Slice 21g —
            // VillagerEntity.mobAiTick lines 377-405): with no customer
            // standing the countdown burns; at zero the armed level-up
            // re-greens the spent-out offers and appends the career's
            // next level. The customer gate (the reference's
            // {@code !hasCustomer()} read): an open trade window on this
            // body holds the walk.
            if (mob.type() == MobType.VILLAGER) {
                boolean customerStanding = false;
                for (PlayerSession player : players) {
                    if (player.state() == net.zaminmc.torch.entity.PlayerState.PLAYING
                            && player.openContainerKind() == PlayerSession.ContainerKind.VILLAGER
                            && player.containerMountId() == mob.entityId()) {
                        customerStanding = true;
                        break;
                    }
                }
                if (!customerStanding && mob.tickVillagerLevelUp()) {
                    // VIII10 — the level-up's own arms (VillagerEntity
                    // lines 382-385): the happy burst (doEntityEvent 14)
                    // and the village's +1 reputation for the last
                    // successful trade's customer (the ledgered note the
                    // village surface retires).
                    for (Listener listener : listeners) {
                        listener.onMobStatusEvent(mob, 14);
                    }
                    net.zaminmc.torch.server.village.VillageSystem villages = villageSystem;
                    String customer = mob.villagerLastTradeCustomer();
                    if (villages != null && !customer.isEmpty()) {
                        Position at = mob.position();
                        var village = villages.nearestVillage(
                                (int) Math.floor(at.x()), (int) Math.floor(at.y()),
                                (int) Math.floor(at.z()), 32);
                        if (village != null) {
                            village.updateReputation(customer, 1);
                        }
                    }
                }
            }
            // The pigman's attacker re-acquisition (Slice 21e —
            // ZombiePigmanEntity.mobAiTick lines 79-83): while the anger
            // window burns and the named attacker is live, the revenge
            // state re-pins to them — the kill-credit window
            // (playerHitTimer, the reference's recentlyHit stamp) never
            // lapses across the chase, so the drops and the XP credit
            // the attacker even minutes in.
            if (mob.type() == MobType.ZOMBIE_PIGMAN && mob.isAngry()) {
                String angerTarget = mob.angerTargetName();
                if (!angerTarget.isEmpty()) {
                    for (PlayerSession player : players) {
                        if (angerTarget.equals(player.name())) {
                            mob.markPlayerHit(); // the re-armed window
                            break;
                        }
                    }
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
                remove(mob, "exploded"); // mid-walk removal is copy-on-write safe
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
                remove(mob, "expired"); // mid-walk removal is copy-on-write safe
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
            MobEntity.StagedSound jumpSound = mob.consumeSlimeSound();
            if (jumpSound != null) {
                for (Listener listener : listeners) {
                    listener.onMobSoundAt(mob, jumpSound.name(),
                            jumpSound.volume(), jumpSound.pitch() * 0.8F);
                }
            }
            // The rabbit's hop animation (RabbitEntity.mobTick lines
            // 202-206: the doEntityEvent(1) at the jump-tick clock's
            // zero) — the client's own hop bounce answers it.
            if (mob.consumeRabbitJumpStatus()) {
                for (Listener listener : listeners) {
                    listener.onMobStatusEvent(mob, 1);
                }
            }
            // The rabbit's garden eat (RaidFarmGoal.tick lines 566-579): the
            // world write + the block-dust burst land through the intent.
            if (mob.consumeRabbitFarmEat()) {
                for (Listener listener : listeners) {
                    listener.onRabbitFarmEat(mob);
                }
            }
            MobEntity.StagedSound landSound = mob.consumeSlimeLandSound();
            if (landSound != null) {
                for (Listener listener : listeners) {
                    listener.onMobSoundAt(mob, landSound.name(),
                            landSound.volume(), landSound.pitch() / 0.8F);
                }
            }
            // The step-sound walk's pair (Entity.move lines 594-624): the
            // swim splash first, the footstep second — the reference's own
            // call order inside the crossing (a wading body over a solid
            // riverbed carries both on one crossing).
            MobEntity.StagedSound swimSound = mob.consumeSwimSound();
            if (swimSound != null) {
                for (Listener listener : listeners) {
                    listener.onMobSoundAt(mob, swimSound.name(),
                            swimSound.volume(), swimSound.pitch());
                }
            }
            MobEntity.StagedSound stepSound = mob.consumeStepSound();
            if (stepSound != null) {
                for (Listener listener : listeners) {
                    listener.onMobSoundAt(mob, stepSound.name(),
                            stepSound.volume(), stepSound.pitch());
                }
            }
            // The zombie villager's cure countdown completed its walk
            // (ZombieEntity.tick lines 236-243): the conversion lands after
            // the loop — the spawn/remove pair cannot run mid-iteration.
            if (mob.consumeZombieConversionComplete()) {
                pendingConversions.add(mob);
            }
            // The zombie's door-break publications (VIII9 — the goal's
            // per-tick walk): the crack stage on change (the -1 clear at
            // the goal's stop), the 1-in-20 knock (the doEvent 1010), and
            // the 240-on-HARD completion (the door cell's removal + the
            // 1012/2001 pair at the bridge).
            int doorStage = mob.consumeZombieDoorStage();
            if (doorStage != -2) {
                for (Listener listener : listeners) {
                    listener.onZombieDoorBreakProgress(mob,
                            mob.zombieBreakDoorX(), mob.zombieBreakDoorY(),
                            mob.zombieBreakDoorZ(), doorStage);
                }
            }
            if (mob.consumeZombieDoorKnock()) {
                for (Listener listener : listeners) {
                    listener.onWorldEventAt(new Position(
                            mob.zombieBreakDoorX() + 0.5, mob.zombieBreakDoorY() + 0.5,
                            mob.zombieBreakDoorZ() + 0.5), 1010, mob.dimension());
                }
            }
            if (mob.consumeZombieDoorBroken()) {
                for (Listener listener : listeners) {
                    listener.onZombieDoorBroken(mob,
                            mob.zombieBreakDoorX(), mob.zombieBreakDoorY(),
                            mob.zombieBreakDoorZ());
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
            MobEntity.AmbientSound ambient = mob.ambientSoundDue();
            if (ambient != null) {
                for (Listener listener : listeners) {
                    listener.onMobSoundAt(mob, ambient.name(),
                            ambient.volume(), ambient.pitch());
                }
            }
        }

        // The deferred breeds land after the iteration (the loop is closed).
        // The love re-verification (VIII6's diag catch — the reference's
        // GoalSelector order: A's breed resets BOTH loves before B's own
        // goal walk runs, so a same-tick double request lands ONE child;
        // the deferred flush re-reads the love gate for the same shape).
        // VIII10 — the villager's own gate is the WILLING pair
        // (VillagerMatingGoal.breed line 74's getMating read): the food
        // model's flag stands where the love clock does.
        for (MobEntity[] pair : pendingBreeds) {
            if (pair[0].type() == MobType.VILLAGER
                    ? pair[0].villagerWilling() && pair[1].villagerWilling()
                    : pair[0].isInLove() && pair[1].isInLove()) {
                breed(pair[0], pair[1]);
            }
        }
        // The deferred slime splits land after the iteration too.
        for (MobEntity parent : pendingSlimeSplits) {
            splitSlime(parent);
        }
        // The deferred infections land after the iteration too (Slice 25a —
        // ZombieEntity.onKill's removeEntity walk replaces the victim with
        // the villager-skinned zombie).
        for (MobEntity victim : stagedInfections) {
            infectVillager(victim);
        }
        stagedInfections.clear();
        // The deferred conversions (Slice 25a — convertToVillager's walk).
        for (MobEntity zombie : pendingConversions) {
            convertZombieToVillager(zombie);
        }
        pendingConversions.clear();
    }

    /**
     * The persistable view of one live mob (ZMD payload, version 2): kind,
     * position, facing, remaining health, and the kind's own state block
     * (null when the kind carries none — the v1 shape). Dying/dead mobs
     * are not captured — their loot lands on the tick that finishes the
     * death animation.
     */
    public record MobSnapshot(String type, double x, double y, double z, float yaw,
                              float health, MobStateExtras extras, MobNbtExtras nbt,
                              java.util.Map<String, String> commandStats,
                              MobTameExtras tame) {
        /** The v1-compatible shape (no extras — every v1 record loads this way). */
        public MobSnapshot(String type, double x, double y, double z, float yaw, float health) {
            this(type, x, y, z, yaw, health, null, null, null, null);
        }

        /** The pre-v3 shape (no NBT rows — every v2 record loads this way). */
        public MobSnapshot(String type, double x, double y, double z, float yaw,
                float health, MobStateExtras extras) {
            this(type, x, y, z, yaw, health, extras, null, null, null);
        }

        /** The pre-v5 shape (no command-stat pairs — every v3/v4 record loads this way). */
        public MobSnapshot(String type, double x, double y, double z, float yaw,
                float health, MobStateExtras extras, MobNbtExtras nbt) {
            this(type, x, y, z, yaw, health, extras, nbt, null, null);
        }

        /** The pre-v6 shape (no tame block — every v5 record loads this way). */
        public MobSnapshot(String type, double x, double y, double z, float yaw,
                float health, MobStateExtras extras, MobNbtExtras nbt,
                java.util.Map<String, String> commandStats) {
            this(type, x, y, z, yaw, health, extras, nbt, commandStats, null);
        }
    }

    /**
     * The tameable/horse state block (Slice 92 — the reference's own world
     * save rows: TameableEntity's OwnerUUID + Sitting, WolfEntity's Angry,
     * HorseBaseEntity's Tame/Bred/HasReproduced): null when the body
     * carries none of it (the plain kinds).
     */
    public record MobTameExtras(
            String ownerName, String ownerUuid,
            boolean tamed, boolean sitting, boolean angry,
            boolean horseBred, boolean horseHasReproduced) {
    }

    /**
     * The /summon NBT rows the engine's mob model carries (Slice 75 — the
     * reference's Entity NBT fields): the custom name pair, the NoAI pin,
     * the invulnerability flag, and the raw persistence mark (the name's
     * own despawn immunity derives live in persistent()). A null record —
     * the pre-v3 shape — is a body with none of the rows.
     *
     * <p>Slice 78b grows the family: the Equipment row's held stack (the
     * reference's slot 0) and the Attributes rows' overrides
     * (generic.movementSpeed + generic.attackDamage; NaN = no override).</p>
     *
     * <p>VIII4 completes the Equipment family's shape 1:1: the four armor
     * rows (slots 1..4 — feet, legs, chest, head, the reference's
     * five-entry Equipment list) and the DropChances rows (the five
     * floats; null = every slot at the constructor's own 0.085 default —
     * the reference saves the list whole, the engine's store carries only
     * the non-default tails, the same presence convention every other row
     * rides).</p>
     */
    public record MobNbtExtras(
            String customName, boolean customNameVisible,
            boolean noAi, boolean invulnerable, boolean persistent,
            ItemStack heldEquipment, double movementSpeed, double attackDamage,
            ItemStack feetEquipment, ItemStack legsEquipment, ItemStack chestEquipment,
            ItemStack headEquipment, float[] dropChances) {

        /** The Slice 75 shape (no equipment, no attribute rows). */
        public MobNbtExtras(String customName, boolean customNameVisible,
                boolean noAi, boolean invulnerable, boolean persistent) {
            this(customName, customNameVisible, noAi, invulnerable, persistent,
                    null, Double.NaN, Double.NaN,
                    null, null, null, null, null);
        }

        /** The Slice 78b shape (the held stack + the attribute rows alone). */
        public MobNbtExtras(String customName, boolean customNameVisible,
                boolean noAi, boolean invulnerable, boolean persistent,
                ItemStack heldEquipment, double movementSpeed, double attackDamage) {
            this(customName, customNameVisible, noAi, invulnerable, persistent,
                    heldEquipment, movementSpeed, attackDamage,
                    null, null, null, null, null);
        }
    }

    /**
     * The per-kind state the v2 store carries (Slice 25c — the reference's
     * own NBT fields): the zombie's IsVillager/ConversionTime pair, the
     * villager's Profession/Career/CareerLevel/Riches quartet, the rabbit's
     * coat byte, the shared Age int (babies survive restarts), the
     * skeleton's wither variant, and the slime family's size.
     */
    public record MobStateExtras(
            boolean zombieVillager, int zombieConversionTicks,
            int villagerProfession, int villagerCareer, int villagerCareerLevel,
            int villagerRiches,
            int rabbitVariant, int breedingAge,
            boolean skeletonWither, int slimeSize, int dimension,
            int dyeColor) {

        /** The overworld shape (the v1-equivalent record, dimension 0). */
        public MobStateExtras(
                boolean zombieVillager, int zombieConversionTicks,
                int villagerProfession, int villagerCareer, int villagerCareerLevel,
                int villagerRiches,
                int rabbitVariant, int breedingAge,
                boolean skeletonWither, int slimeSize) {
            this(zombieVillager, zombieConversionTicks, villagerProfession, villagerCareer,
                    villagerCareerLevel, villagerRiches, rabbitVariant, breedingAge,
                    skeletonWither, slimeSize, 0, 0);
        }

        /** The pre-dye shape (Slice 30b added the kind's dye byte). */
        public MobStateExtras(
                boolean zombieVillager, int zombieConversionTicks,
                int villagerProfession, int villagerCareer, int villagerCareerLevel,
                int villagerRiches,
                int rabbitVariant, int breedingAge,
                boolean skeletonWither, int slimeSize, int dimension) {
            this(zombieVillager, zombieConversionTicks, villagerProfession, villagerCareer,
                    villagerCareerLevel, villagerRiches, rabbitVariant, breedingAge,
                    skeletonWither, slimeSize, dimension, 0);
        }
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
            MobStateExtras extras = snapshot.extras();
            if (extras != null) {
                restoreStateExtras(mob, extras);
            }
            MobNbtExtras nbt = snapshot.nbt();
            if (nbt != null) {
                // The /summon NBT rows' restore (Slice 75 — the reference's
                // Entity.readNbt arms): every row lands on any kind.
                mob.setCustomName(nbt.customName());
                mob.setCustomNameVisible(nbt.customNameVisible());
                mob.setNoAi(nbt.noAi());
                mob.setInvulnerable(nbt.invulnerable());
                mob.setPersistent(nbt.persistent());
                // Slice 78b's rows: the Equipment row's held stack (the
                // wire re-publishes through the equipment arm — the spawn
                // packet went out before the rows landed) + the Attributes
                // rows' overrides.
                if (nbt.heldEquipment() != null) {
                    mob.setHeldEquipment(nbt.heldEquipment());
                    equipmentChanged(mob);
                }
                // VIII4's rows: the four armor slots (the reference's
                // Equipment list [1..4]) + the DropChances rows. The wire
                // re-publish rides the same equipment arm.
                boolean armorRestored = false;
                for (int slot = 1; slot <= 4; slot++) {
                    ItemStack piece = switch (slot) {
                        case 1 -> nbt.feetEquipment();
                        case 2 -> nbt.legsEquipment();
                        case 3 -> nbt.chestEquipment();
                        default -> nbt.headEquipment();
                    };
                    if (piece != null) {
                        mob.setEquipment(slot, piece);
                        armorRestored = true;
                    }
                }
                if (armorRestored) {
                    equipmentChanged(mob);
                }
                if (nbt.dropChances() != null) {
                    mob.setInventoryDropChances(nbt.dropChances());
                }
                boolean attributesRestored = false;
                if (!Double.isNaN(nbt.movementSpeed())) {
                    mob.setMovementSpeedOverride(nbt.movementSpeed());
                    // MOVEMENT_SPEED is the trackable one (EntityAttributes
                    // line 18) — the wake fires the 0x20 re-publish; the
                    // attackDamage row below never enters the tracked set
                    // (the reference's own isTrackable law).
                    attributesRestored = true;
                }
                if (!Double.isNaN(nbt.attackDamage())) {
                    mob.setAttackDamageOverride((float) nbt.attackDamage());
                }
                if (attributesRestored) {
                    attributesChanged(mob); // the boot-time fan: no connections yet
                }
            }
            // The v5 command-stat bindings (Slice 90 — the reference's
            // Entity.readNbt line 1195: every entity reads its CommandResults
            // back).
            restoreCommandStats(mob, snapshot.commandStats());
            // The v6 tameable/horse block (Slice 92 — the reference's own
            // readCustomNbt arms: the owner pair, the tamed bit, the
            // family's sitting/angry, the horse's bred markers).
            restoreTameExtras(mob, snapshot.tame());
            any = true;
        }
        return any;
    }

    /**
     * The state block's restore (Slice 25c): each field lands only on the
     * kind that reads it — the reference's own readCustomNbt dispatch.
     */
    private static void restoreStateExtras(MobEntity mob, MobStateExtras extras) {
        if (mob.type() == MobType.ZOMBIE) {
            // ZombieEntity.readCustomNbt lines 334-346: IsVillager + the
            // ConversionTime gate ({@code > -1} arms the countdown).
            if (extras.zombieVillager()) {
                mob.setZombieVillager(true);
            }
            if (extras.zombieConversionTicks() > 0) {
                mob.setZombieConversionTime(extras.zombieConversionTicks());
            }
        }
        if (mob.type() == MobType.VILLAGER) {
            // VillagerEntity.readCustomNbt lines 445-448.
            mob.setVillagerProfession(extras.villagerProfession());
            mob.restoreVillagerTradeState(extras.villagerCareer(),
                    extras.villagerCareerLevel(), extras.villagerRiches());
        }
        if (mob.type() == MobType.RABBIT) {
            mob.setRabbitVariant(extras.rabbitVariant());
        }
        if (mob.type() == MobType.SHEEP && extras.dyeColor() != 0) {
            // SheepEntity.readCustomNbt line 180: Color = the DyeColor id.
            // The store shifts by one (0 = absent): the byte lands as
            // fleece + 1 so the black (0-valid) rows never alias absence.
            mob.setSheepFleeceColor(extras.dyeColor() - 1);
        }
        if (mob.type() == MobType.WOLF && extras.dyeColor() != 0) {
            // WolfEntity.readCustomNbt line 133: CollarColor = the dye
            // metadata domain (the index-20 byte's own shape), stored
            // shifted by one the same way.
            mob.setWolfCollarColor(extras.dyeColor() - 1);
        }
        if (extras.breedingAge() != 0) {
            mob.setBreedingAge(extras.breedingAge());
        }
        if (mob.type() == MobType.SKELETON && extras.skeletonWither()) {
            mob.setSkeletonWither(true);
            mob.setHeldEquipment(net.zaminmc.torch.item.ItemStack.of(
                    net.zaminmc.torch.server.item.BuiltinItems.STONE_SWORD, 1));
        }
        if ((mob.type() == MobType.SLIME || mob.type() == MobType.MAGMA_CUBE)
                && extras.slimeSize() > 0) {
            mob.setSlimeSize(extras.slimeSize());
        }
        if (extras.dimension() != 0) {
            // Slice 25d: the body restores into its own dimension (a nether
            // pigman never wakes in the overworld).
            mob.setDimension(extras.dimension());
        }
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
                    position.z(), mob.yaw(), mob.health(), stateExtrasOf(mob),
                    nbtExtrasOf(mob), commandStatsOf(mob), tameExtrasOf(mob)));
        }
        return saved;
    }

    /**
     * The mob's command-stat bindings' snapshot (Slice 90 — the reference's
     * Entity.writeNbt line 1128: every entity carries its CommandResults in
     * the save): the flattened <Name>Name/<Name>Objective pairs, null when
     * nothing is bound.
     */
    static java.util.Map<String, String> commandStatsOf(MobEntity mob) {
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> envelope =
                new java.util.LinkedHashMap<>();
        mob.commandResults().writeNbt(envelope);
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> stats =
                net.zaminmc.torch.server.nbt.NbtOps.getCompound(envelope, "CommandStats");
        if (stats.isEmpty()) {
            return null;
        }
        java.util.Map<String, String> pairs = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, net.zaminmc.torch.server.nbt.Snbt.Tag> pair : stats.entrySet()) {
            pairs.put(pair.getKey(), net.zaminmc.torch.server.nbt.NbtOps.getString(stats, pair.getKey()));
        }
        return pairs;
    }

    /**
     * The bindings' restore (the same readNbt walk /stats set rides):
     * the pairs back into the CommandStats compound.
     */
    static void restoreCommandStats(MobEntity mob, java.util.Map<String, String> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return;
        }
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> envelope =
                new java.util.LinkedHashMap<>();
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> stats =
                new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, String> pair : pairs.entrySet()) {
            net.zaminmc.torch.server.nbt.NbtOps.putString(stats, pair.getKey(), pair.getValue());
        }
        net.zaminmc.torch.server.nbt.NbtOps.putCompound(envelope, "CommandStats", stats);
        mob.commandResults().readNbt(envelope);
    }

    /** The tameable/horse block's restore (the mirror of {@link #tameExtrasOf}). */
    static void restoreTameExtras(MobEntity mob, MobTameExtras tame) {
        if (tame == null) {
            return;
        }
        if (tame.ownerName() != null) {
            mob.setCustomOwnerName(tame.ownerName());
        }
        if (tame.ownerUuid() != null) {
            mob.setOwnerUuid(tame.ownerUuid());
        }
        boolean tameable = mob.type() == MobType.WOLF || mob.type() == MobType.OCELOT;
        if (tameable) {
            if (tame.tamed()) {
                mob.setTamedFlag(true);
            }
            mob.setWolfSitting(tame.sitting());
            mob.setWolfAngry(tame.angry());
        } else if (mob.type() == MobType.HORSE) {
            mob.setTamedFlag(tame.tamed());
            mob.setHorseBred(tame.horseBred());
            mob.setHorseHasReproduced(tame.horseHasReproduced());
        }
    }

    /**
     * The tameable/horse block's snapshot (Slice 92): the owner pair, the
     * tamed bit, the tameable family's sitting/angry pair, the horse's
     * bred markers — null when the body carries none of it.
     */
    static MobTameExtras tameExtrasOf(MobEntity mob) {
        boolean tameable = mob.type() == MobType.WOLF || mob.type() == MobType.OCELOT;
        boolean horse = mob.type() == MobType.HORSE;
        if (!tameable && !horse) {
            return null;
        }
        String ownerName = mob.ownerName().isEmpty() ? null : mob.ownerName();
        String ownerUuid = mob.ownerUuid().isEmpty() ? null : mob.ownerUuid();
        boolean tamed = tameable ? mob.wolfTamed() : mob.tamed();
        if (ownerName == null && ownerUuid == null && !tamed
                && !(tameable && (mob.wolfSitting() || mob.wolfAngry()))
                && !(horse && (mob.horseBred() || mob.horseHasReproduced()))) {
            return null;
        }
        return new MobTameExtras(ownerName, ownerUuid, tamed,
                tameable && mob.wolfSitting(),
                tameable && mob.wolfAngry(),
                horse && mob.horseBred(),
                horse && mob.horseHasReproduced());
    }

    /** The /summon NBT rows' snapshot (Slice 75): null when none are set. */
    private static MobNbtExtras nbtExtrasOf(MobEntity mob) {
        // VIII4: the armor rows ride the family — any equipped slot or any
        // non-default chance keeps the record alive.
        boolean anyArmor = false;
        for (int slot = 1; slot <= 4; slot++) {
            ItemStack stack = mob.equipment(slot);
            if (stack != null && !stack.isEmpty()) {
                anyArmor = true;
                break;
            }
        }
        float[] chances = mob.inventoryDropChances();
        boolean anyChance = false;
        for (float chance : chances) {
            if (chance != 0.085F) {
                anyChance = true;
                break;
            }
        }
        if (mob.customName() == null && !mob.customNameVisible() && !mob.noAi()
                && !mob.invulnerable() && !mob.rawPersistent()
                && mob.heldEquipment() == null && !mob.hasMovementSpeedOverride()
                && !mob.hasAttackDamageOverride() && !anyArmor && !anyChance) {
            return null;
        }
        return new MobNbtExtras(mob.customName(), mob.customNameVisible(),
                mob.noAi(), mob.invulnerable(), mob.rawPersistent(),
                mob.heldEquipment(),
                mob.hasMovementSpeedOverride() ? mob.walkSpeed() : Double.NaN,
                mob.hasAttackDamageOverride() ? mob.attackDamage() : Double.NaN,
                mob.equipment(1), mob.equipment(2), mob.equipment(3), mob.equipment(4),
                anyChance ? chances : null);
    }

    /**
     * The per-kind state block (Slice 25c): only the fields the kind reads
     * land in the record — a plain cow carries nothing but its age, the
     * kinds without their own state carry the age alone.
     */
    private static MobStateExtras stateExtrasOf(MobEntity mob) {
        boolean zombieVillager = mob.type() == MobType.ZOMBIE && mob.zombieVillager();
        int zombieConversion = mob.type() == MobType.ZOMBIE && mob.zombieConverting()
                ? Math.max(0, mob.zombieConversionTicks()) : 0;
        int profession = 0;
        int career = 0;
        int careerLevel = 0;
        int riches = 0;
        if (mob.type() == MobType.VILLAGER) {
            profession = mob.profession();
            career = mob.villagerCareer();
            careerLevel = mob.villagerCareerLevel();
            riches = mob.villagerRiches();
        }
        int rabbitVariant = mob.type() == MobType.RABBIT ? mob.rabbitVariant() : 0;
        boolean wither = mob.type() == MobType.SKELETON && mob.skeletonWither();
        int slimeSize = mob.type() == MobType.SLIME || mob.type() == MobType.MAGMA_CUBE
                ? mob.slimeSize() : 1;
        // The kind's dye byte (Slice 30b), stored shifted by one so 0 stays
        // the honest absent marker for both domains: the sheep's fleece id
        // (SheepEntity's always-written Color NBT) or the wolf's collar
        // metadata (WolfEntity's always-written CollarColor NBT).
        int dyeColor = 0;
        if (mob.type() == MobType.SHEEP) {
            dyeColor = mob.sheepFleeceColor() + 1;
        } else if (mob.type() == MobType.WOLF) {
            dyeColor = mob.wolfCollarColor() + 1;
        }
        return new MobStateExtras(zombieVillager, zombieConversion, profession, career,
                careerLevel, riches, rabbitVariant, mob.breedingAge(), wither, slimeSize,
                mob.dimension(), dyeColor);
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
     * The blast's own wound (Explosion.damageEntities lines 106-137 over
     * takeDamage): the damage lands through the ordinary hurt walk with NO
     * melee knockback — the caller applies the reference's own
     * {@code velocity += (d5, d7, d9) * d11} impulse separately (the
     * blast's direction-and-density scale, not the attack's yaw hop).
     */
    public boolean hurtFromExplosion(MobEntity mob, float amount) {
        return hurtFromExplosion(mob, amount, null);
    }

    /**
     * The blast's own wound with its SOURCE (Explosion.damageEntities
     * lines 106-137 over takeDamage): the damage lands through the
     * ordinary hurt walk with NO melee knockback and the blast's source
     * riding the attacker seat (the reference's DamageSource carries the
     * exploding body — the zombie reinforcement ladder, the enderman
     * anger, and the charged-creeper head-drop attribution all read it).
     * When the wound kills and the source is a CHARGED creeper, the
     * victim carries the attribution to its loot walk (the die arms'
     * own law).
     */
    public boolean hurtFromExplosion(MobEntity mob, float amount, MobEntity blastSource) {
        boolean landed = hurt(mob, amount, Double.NaN,
                net.zaminmc.torch.event.DamageCause.EXPLOSION,
                false, false, blastSource, null, true);
        if (landed && blastSource != null && blastSource.type() == MobType.CREEPER
                && blastSource.creeperCharged() && mob.health() <= 0) {
            mob.killedByChargedCreeper(blastSource);
        }
        return landed;
    }

    /**
     * Melee hit from another mob (Slice 16b — the mob-vs-mob target family's
     * damage land): the attacker's identity rides the walk. The victim's
     * RevengeGoal answers inside the walk (the reference's takeDamage sets
     * the attacker at its own site, LivingEntity lines 671-676, and the
     * goal reads it — the engine's walk carries the same seat): the hostile
     * kinds, the wolves, and the iron golem target their attacker, the wolf
     * and the zombie calling their nearby kin.
     */
    public boolean hurtByMob(MobEntity attacker, MobEntity victim, float amount) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(victim, "victim");
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
        // lines 286-288); the attacker rides it too (Slice 59 — the
        // wither's UNDEAD-melee gate reads the source's body).
        boolean landed = hurt(victim, amount, kbYaw,
                net.zaminmc.torch.event.DamageCause.MELEE,
                attacker.type() == MobType.ENDERMITE,
                false, attacker);
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
            // The thorns wildcard's mob-victim arm (the README TODO's row 8
            // — the reference's Entity.damageEntity(source, entity) at the
            // mob melee walks: IronGolemEntity.tryDamage line 159,
            // MonsterEntity.attack line 101, SlimeEntity line 202, WolfEntity
            // line 277 — the victim's worn thorns bite the attacking mob
            // back on every landed hit, the same walk the player victims
            // already carry). The seam consults the engine-side walk; an
            // unwired boot keeps the historical no-retaliation behavior.
            ThornsRetaliation thorns = thornsRetaliation;
            if (thorns != null) {
                thorns.biteBack(victim, attacker);
            }
            if (victim.dead()
                    && attacker.type() == MobType.ZOMBIE
                    && victim.type() == MobType.VILLAGER) {
                // ZombieEntity.onKill (lines 352-378, fired from die's own
                // attacker credit): a zombie's killing blow on a villager
                // infects — the difficulty gate and the NORMAL coin ride
                // the staging walk, the replacement lands after the loop.
                stageZombieInfection(victim);
            }
        }
        return landed;
    }

    /**
     * RevengeGoal's arm (Slice 16b + the golem's own, IronGolemEntity
     * targetSelector 2 — line 55): the victim — any hostile kind, a wolf,
     * the killer bunny, or an iron golem — targets its attacker, and the
     * call-others family (the wolf's and the zombie's own {@code true}
     * flag) passes the anger to every nearby same-kind body with no live
     * target. The gates are the reference's own: a same-team attacker
     * never angers its kin ({@code TrackTargetGoal.isTarget} lines
     * 102-106), and the call-others only reach kin NOT on the attacker's
     * team ({@code RevengeGoal.start} line 39's {@code !isInSameTeam}).
     */
    private void applyRevenge(MobEntity victim, MobEntity attacker) {
        if (attacker.dead()) {
            return;
        }
        if (victim.sameTeamAs(attacker)) {
            return; // isTarget's same-team refusal
        }
        boolean revengeArmed = victim.type().hostile || victim.type() == MobType.WOLF
                // The killer bunny's own RevengeGoal (setVariant line 324:
                // targetSelector 1, hurt → the attacker).
                || victim.rabbitKiller()
                // The golem's RevengeGoal(this, false) — targetSelector 2,
                // no call-others.
                || victim.type() == MobType.IRON_GOLEM;
        if (!revengeArmed) {
            return;
        }
        victim.setMobAttackTarget(attacker);
        boolean callOthers = victim.type() == MobType.WOLF || victim.type() == MobType.ZOMBIE;
        if (!callOthers) {
            return;
        }
        String attackerTeam = attacker.teamName();
        for (MobEntity kin : mobs) {
            if (kin == victim || kin.type() != victim.type() || kin.dead()) {
                continue;
            }
            if (kin.mobAttackTarget() != null) {
                continue; // the reference's own gate: only the targetless join
            }
            if (attackerTeam != null && attackerTeam.equals(kin.teamName())) {
                continue; // RevengeGoal.start line 39: kin on the attacker's team never join
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
     * ZombieEntity.onKill's difficulty gate (lines 352-357): NORMAL and
     * HARD carry the infection arm — on NORMAL the reference's own early
     * coin ({@code nextBoolean()}) refuses half the kills, on HARD every
     * kill lands, and EASY and below never infect. The staging defers the
     * replacement to the tick loop's close.
     */
    private void stageZombieInfection(MobEntity victim) {
        java.util.function.IntSupplier difficultyAt = difficultyRead;
        int difficulty = difficultyAt == null ? 2 : difficultyAt.getAsInt();
        if (difficulty != 2 && difficulty != 3) {
            return; // EASY and PEACEFUL never carry the arm
        }
        if (difficulty != 3 && random.nextBoolean()) {
            return; // NORMAL: the reference's own early coin
        }
        stagedInfections.add(victim);
    }

    /**
     * ZombieEntity.onKill's replacement walk (lines 358-377): the villager
     * body leaves immediately ({@code removeEntity} — no death animation
     * window, the destroy packet lands straight away) and a villager-skinned
     * zombie takes its place at the same position and rotation — a baby for
     * a baby ({@code setBaby(true)}) — with the 1016 infect event at the
     * body ({@code doEvent(null, 1016, pos, 0)}). The {@code MobSpawnEvent}
     * gate rides {@link #spawnAt} as every spawn does; a refused infection
     * still removes the victim (the kill already landed).
     */
    private void infectVillager(MobEntity victim) {
        if (!mobs.contains(victim)) {
            return; // the body already left (a second kill landed first)
        }
        Position at = victim.position();
        float yaw = victim.bodyYawLive();
        boolean baby = victim.isBaby();
        remove(victim, "infected");
        MobEntity zombie = spawnAt(MobType.ZOMBIE, at, victim.dimension());
        if (zombie != null) {
            // The birth dimension rides the spawn (ZombieEntity.onKill
            // line 360's {@code new ZombieEntity(this.world)} — the
            // attacking zombie's own world; the staged kill that arms
            // this walk is a same-dimension melee, so the victim's row is
            // the zombie's row).
            zombie.setZombieVillager(true);
            zombie.setBodyYaw(yaw);
            if (baby) {
                zombie.setBreedingAge(-24_000);
            }
        }
        for (Listener listener : listeners) {
            listener.onWorldEventAt(at, 1016, zombie != null ? zombie.dimension() : 0);
        }
    }

    /**
     * convertToVillager (lines 519-537): the countdown burned out — a fresh
     * villager takes the zombie's place (its own {@code initialize} rolls
     * the profession, {@code nextInt(5)}), a baby zombie converts into a
     * baby villager ({@code setBreedingAge(-24000)}), the zombie leaves
     * ({@code removeEntity}), the newcomer carries the 200-tick NAUSEA
     * ({@code addStatusEffect(StatusEffect.NAUSEA.id, 200, 0)}), and the
     * 1017 un-infect event sounds at the body.
     */
    private void convertZombieToVillager(MobEntity zombie) {
        if (!mobs.contains(zombie)) {
            return; // the body already left (a killing blow won the race)
        }
        Position at = zombie.position();
        float yaw = zombie.bodyYawLive();
        boolean baby = zombie.isBaby();
        remove(zombie, "converted");
        MobEntity villager = spawnAt(MobType.VILLAGER, at, zombie.dimension());
        if (villager != null) {
            // The birth dimension rides the spawn
            // (ZombieEntity.convertToVillager line 520's
            // {@code new VillagerEntity(this.world)}).
            villager.setBodyYaw(yaw);
            if (baby) {
                villager.setBreedingAge(-24_000);
            }
            // VIII10 — setConvertedZombie (VillagerEntity line 523): the
            // marker arms the +5 reputation walk at the villager's next
            // village-scan cadence (mobAiTick lines 363-366).
            villager.setVillagerConvertedZombie();
            net.zaminmc.torch.server.potion.PotionSystem potions = potionSystem;
            if (potions != null) {
                potions.add(villager, new net.zaminmc.torch.server.potion.StatusEffectInstance(
                        net.zaminmc.torch.server.potion.StatusEffects.NAUSEA, 200, 0));
            }
        }
        for (Listener listener : listeners) {
            listener.onWorldEventAt(at, 1017, villager != null ? villager.dimension() : 0);
        }
    }

    /**
     * The cure's walk (interactMob lines 457-480 + setConversionTime's own
     * side effects, lines 482-488): the golden apple on the weakened
     * villager-skinned zombie arms the conversion —
     * {@code nextInt(2401) + 3600} ticks — clears the Weakness, installs
     * the Strength for the whole window (the reference's own
     * {@code Math.min(difficultyId - 1, 0)} amplifier — the observed 0 on
     * every difficulty that can carry the arm), and fires the entity
     * event 16 (the mob.zombie.remedy sound the client plays at the
     * body). The apple is eaten by the caller (the interact walk's own
     * {@code eatFromHand}).
     */
    public boolean cureZombieVillager(MobEntity mob) {
        Objects.requireNonNull(mob, "mob");
        if (mob.type() != MobType.ZOMBIE || !mob.zombieVillager() || mob.dead()) {
            return false;
        }
        mob.setZombieConversionTime(random.nextInt(2401) + 3600);
        net.zaminmc.torch.server.potion.PotionSystem potions = potionSystem;
        if (potions != null) {
            potions.remove(mob, net.zaminmc.torch.server.potion.StatusEffects.WEAKNESS);
            java.util.function.IntSupplier difficultyAt = difficultyRead;
            int difficulty = difficultyAt == null ? 2 : difficultyAt.getAsInt();
            potions.add(mob, new net.zaminmc.torch.server.potion.StatusEffectInstance(
                    net.zaminmc.torch.server.potion.StatusEffects.STRENGTH,
                    mob.zombieConversionTicks(), Math.min(difficulty - 1, 0)));
        }
        for (Listener listener : listeners) {
            listener.onZombieStateChanged(mob);
            listener.onMobStatusEvent(mob, 16);
        }
        return true;
    }

    /**
     * The owner arms (Slice 16b — AttackWithOwnerGoal + OwnerHurtGoal):
     * every STANDING tamed wolf the player owns joins the fight on the
     * target — the mob their owner just attacked, or the mob that just
     * attacked their owner. A sitting wolf never joins (SitGoal's own
     * claim on the movement).
     */
    public void ownerWolvesJoinFight(String ownerName, String ownerUuid,
            MobEntity target) {
        if (target == null || target.dead()) {
            return;
        }
        boolean byName = ownerName != null && !ownerName.isEmpty();
        boolean byUuid = ownerUuid != null && !ownerUuid.isEmpty();
        if (!byName && !byUuid) {
            return;
        }
        for (MobEntity wolf : mobs) {
            if (wolf.type() != MobType.WOLF || !wolf.wolfTamed() || wolf.dead()) {
                continue;
            }
            if (wolf.wolfSitting()) {
                continue; // the sitting pet stays seated
            }
            boolean owned = byUuid && ownerUuid.equals(wolf.ownerUuid());
            if (!owned && byName) {
                owned = ownerName.equals(wolf.ownerName());
            }
            if (!owned) {
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
        return hurt(mob, amount, attackerYaw, cause, endermiteAttacker,
                false, null);
    }

    /**
     * The full walk with the projectile identity pair (Slice 59): the
     * wither's own gates read whether the source was an arrow (the
     * half-health shield is arrow-only) and the attacker's body (a
     * wither's skull never wounds another wither; the UNDEAD melee
     * family never wounds the boss).
     */
    public boolean hurt(MobEntity mob, float amount, double attackerYaw,
                 net.zaminmc.torch.event.DamageCause cause, boolean endermiteAttacker,
                 boolean arrowSource, MobEntity attacker) {
        return hurt(mob, amount, attackerYaw, cause, endermiteAttacker,
                arrowSource, attacker, null);
    }

    /**
     * The full walk with the attacking PLAYER's identity (VIII9 — the
     * reference's {@code DamageSource.getAttacker()} read the zombie's
     * reinforcement ladder carries: the roll's target resolution answers
     * the body's own attack target first, then the wound's living
     * attacker — a player's melee or arrow reaches the callee's
     * setAttackTarget through this seat). The player paths (the melee
     * site, the arrow site) are the only callers that carry the session.
     */
    public boolean hurt(MobEntity mob, float amount, double attackerYaw,
                 net.zaminmc.torch.event.DamageCause cause, boolean endermiteAttacker,
                 boolean arrowSource, MobEntity attacker,
                 PlayerSession attackingPlayer) {
        return hurt(mob, amount, attackerYaw, cause, endermiteAttacker,
                arrowSource, attacker, attackingPlayer, false);
    }

    /**
     * The full walk with the knockback gate: the blast family's own entry
     * (Explosion.damageEntities lines 106-137) — a wound through this seat
     * lands NO melee-shaped knockback; the caller adds the reference's own
     * {@code velocity += dir * d11} impulse over the blast's direction
     * and density scale (the yaw-shaped half-velocity hop is the attack
     * family's, never the blast's).
     */
    public boolean hurt(MobEntity mob, float amount, double attackerYaw,
                 net.zaminmc.torch.event.DamageCause cause, boolean endermiteAttacker,
                 boolean arrowSource, MobEntity attacker,
                 PlayerSession attackingPlayer, boolean skipKnockback) {
        Objects.requireNonNull(mob, "mob");
        if (amount <= 0 || mob.dead()) {
            return false;
        }
        // The wolf's own takeDamage shave (WolfEntity lines 264-268): a
        // non-player non-arrow living attacker — a mob's claw, a mob-shot
        // arrow, a mob-sourced explosion — rounds the amount up to half.
        // The walk's own seat (the reference's takeDamage entry): every
        // attacker-carrying path shaves, the 16b melee arm and the explosion
        // and projectile identity arms alike.
        if (attacker != null && mob.type() == MobType.WOLF) {
            amount = (amount + 1.0f) / 2.0f;
        }
        // The Invulnerable pin (Slice 75 — Entity.isInvulnerable's own
        // gate over every damage entry): the body refuses every wound.
        // The reference's creative-melee and void exemptions ride the
        // engine's melee-path attacker context (ledgered — the hurt walk
        // does not carry the attacker's gamemode).
        if (mob.invulnerable()) {
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
        // The wither's own gates (Slice 59 — WitherEntity.takeDamage
        // lines 408-444, the reference's order): the drown and the
        // wither's own skull never land (line 413), the blue window
        // blocks everything but the void (line 417), the half-health
        // body shields arrows alone (lines 421-426 — the source must be
        // an ArrowEntity; fireballs and snowballs still land), and the
        // UNDEAD melee family never wounds the boss (lines 428-434 —
        // players are exempt by the reference's own instanceof gate).
        // A wound that passes them all arms the 20-tick sweep clock and
        // speeds the blue barrage (lines 436-442) BEFORE the damage.
        if (mob.type() == MobType.WITHER) {
            if (cause == net.zaminmc.torch.event.DamageCause.DROWN) {
                return false;
            }
            if (attacker != null && attacker.type() == MobType.WITHER) {
                return false; // the skull of another wither never lands
            }
            if (mob.witherInvulnerable()
                    && cause != net.zaminmc.torch.event.DamageCause.OUT_OF_WORLD) {
                return false; // the blue window blocks all but the void
            }
            if (arrowSource
                    && mob.health() <= mob.type().maxHealth / 2.0f) {
                return false; // the half-health arrow shield
            }
            if (attacker != null && attacker.type().damageCategory() == 1) {
                return false; // the UNDEAD melee family (smite's own set)
            }
            mob.armWitherSweep();
        }
        mob.hurt(amount);
        if (!skipKnockback) {
            mob.knockbackFrom(attackerYaw);
        }
        for (Listener listener : listeners) {
            listener.onMobHurt(mob);
        }
        if (mob.health() <= 0) {
            if (mob.type() == MobType.ENDER_DRAGON) {
                // The dragon's death walk (Slice 64 — tickPostDeath lines
                // 501-543): the 200-tick rise-and-spin with the XP batches
                // and the portal — the standard death (loot, remove) never
                // runs; the body stays until the walk's own end.
                mob.beginDragonDeath();
                return true;
            }
            // VIII10 — the villager's death reputation (VillagerEntity.die
            // lines 532-550) + the golem's own −5 arm (IronGolemEntity.die
            // lines 239-245): the village's table moves at the death itself.
            if (mob.type() == MobType.VILLAGER) {
                villagerDeathHook(mob, attacker, attackingPlayer);
            } else if (mob.type() == MobType.IRON_GOLEM) {
                golemDeathHook(mob, attackingPlayer);
            }
            for (Listener listener : listeners) {
                listener.onMobDied(mob);
            }
        }
        // VIII9 — ZombieEntity.takeDamage lines 191-233: the landed wound's
        // own reinforcement ladder (the reference's whole roll sits inside
        // the {@code if (super.takeDamage(...))} verdict — even a killing
        // blow calls). The walk answers true below, the roll rides here.
        if (mob.type() == MobType.ZOMBIE) {
            callZombieReinforcements(mob, attacker, attackingPlayer);
        }
        // VIII10 — the villager's wound hook (VillagerEntity.setAttacker
        // lines 512-529): the attacker census + the player reputation arm.
        if (mob.type() == MobType.VILLAGER) {
            villagerWoundHook(mob, attacker, attackingPlayer);
        }
        // The revenge seat (LivingEntity.takeDamage lines 671-676 —
        // setAttacker at the wound's own site; the targetSelector reads it
        // the next goal walk): a living attacker angers the victim through
        // the walk's own arm, whatever path carried the identity — the 16b
        // melee, the mob-shot arrow, the mob-sourced explosion. The
        // reference's player arm rides the forced-hunt seat for the one
        // kind whose own mind never hunts players: a VILLAGE golem revenges
        // on its player attacker (IronGolemEntity targetSelector 2 — the
        // RevengeGoal routes through canAttack line 137, so the
        // player-built body's own gate refuses players).
        if (!mob.dead()) {
            if (attacker != null) {
                applyRevenge(mob, attacker);
            } else if (attackingPlayer != null
                    && attackingPlayer.state() == PlayerState.PLAYING
                    && mob.type() == MobType.IRON_GOLEM
                    && !mob.golemPlayerCreated()
                    && !sameTeamAsPlayer(mob, attackingPlayer)) {
                mob.setForcedHuntPlayer(attackingPlayer.engineEntityId());
            }
        }
        return true;
    }

    /**
     * The player's same-team consult (TrackTargetGoal.isTarget lines
     * 102-106 over the player's NAME key — PlayerEntity.getScoreboardTeam
     * line 1634): a teamed body never avenges on its own kin.
     */
    private boolean sameTeamAsPlayer(MobEntity mob, PlayerSession player) {
        String own = mob.teamName();
        return own != null && own.equals(world.teamNameOf(player.name()));
    }

    /**
     * The zombie's reinforcement caller (VIII9 — ZombieEntity.takeDamage
     * lines 199-227, 1:1 with the named gates): a landed wound on a HARD
     * world rolls the body's own spawnReinforcements attribute — the
     * target is the attack target first, then the wound's living
     * attacker (a mob's claw or a player's hand) — and the 50-try flank
     * walk ({@code nextInt(7, 40) * nextInt(-1, 1)} per axis, the
     * reference's own shape) spawns the callee at the first cell that
     * passes the whole gate family: the solid top below, the dark band
     * ({@code getRawBrightness < 10} — the engine's sky-light consult,
     * the named adaptation: the mob WorldQuery carries no block-light
     * read), no player within 7, the spawn cell + headroom clear, no
     * fluid. The callee takes the target (the mob arm through the mob
     * target, the player arm through the forced hunt — the callee chases
     * wherever the target stands), its own spawn dice ride the spawnAt
     * walk (the local-difficulty multiplier the engine's dice read — the
     * callee lands as a plain adult zombie, never the baby/villager
     * rolls: the standing shape, named in the PR), and BOTH sides pay
     * the 0.05 charge (the reference's caller/callee modifier pair).
     *
     * <p>The two gates the 1.8.8 reference does not carry — the task's
     * own named additions, both adapted and documented: the same-family
     * census cap (more than ten ZOMBIE bodies within 40 blocks of the
     * caller refuses the call — the pack stays bounded) and the
     * baby-caller refusal (a child zombie never calls).</p>
     */
    private void callZombieReinforcements(MobEntity caller,
                                          MobEntity mobAttacker,
                                          PlayerSession playerAttacker) {
        // The reference's target resolution (lines 194-197): the body's
        // own attack target first, then the wound's living attacker.
        MobEntity targetMob = caller.mobAttackTarget();
        if (targetMob == null) {
            targetMob = mobAttacker;
        }
        if (targetMob == null && playerAttacker == null) {
            return; // no living target: the ladder never runs
        }
        if (difficultyId() != 3) {
            return; // HARD alone (line 200)
        }
        if (caller.isBaby()) {
            return; // the named baby-caller refusal
        }
        // The named census cap: the same-family nearby walk.
        int census = 0;
        for (MobEntity kin : mobs) {
            if (kin == caller || kin.dead() || kin.type() != MobType.ZOMBIE
                    || kin.dimension() != caller.dimension()) {
                continue;
            }
            double dx = kin.position().x() - caller.position().x();
            double dy = kin.position().y() - caller.position().y();
            double dz = kin.position().z() - caller.position().z();
            if (dx * dx + dy * dy + dz * dz <= 40.0 * 40.0) {
                census++;
            }
        }
        if (census > 10) {
            return; // the <=10 pack cap
        }
        if (random.nextFloat() >= caller.zombieReinforcementChance()) {
            return; // the attribute roll (line 201)
        }
        int i = (int) Math.floor(caller.position().x());
        int j = (int) Math.floor(caller.position().y());
        int k = (int) Math.floor(caller.position().z());
        for (int l = 0; l < 50; l++) {
            // MathHelper.nextInt(random, 7, 40) * MathHelper.nextInt(random, -1, 1)
            // — the reference's own flank shape (lines 208-210): 7..40
            // blocks out, the axis sign -1..1.
            int i1 = i + (7 + random.nextInt(34)) * (-1 + random.nextInt(3));
            int j1 = j + (7 + random.nextInt(34)) * (-1 + random.nextInt(3));
            int k1 = k + (7 + random.nextInt(34)) * (-1 + random.nextInt(3));
            // hasSolidTop (line 211): the block below the cell.
            if (!world.isSolid(i1 + 0.5, j1 - 1 + 0.5, k1 + 0.5)) {
                continue;
            }
            // getRawBrightness < 10 — the dark band (the named adaptation).
            if (world.skyLight(i1, j1, k1) >= 10) {
                continue;
            }
            // isPlayerWithinRange(i1, j1, k1, 7.0) (line 213).
            if (world.nearestPlayer(i1 + 0.5, j1, k1 + 0.5, 7.0) != null) {
                continue;
            }
            // isUnobstructed + getCollisions-empty + containsLiquid (lines
            // 214-216) — the engine's two-cell + fluid gates (the named
            // adaptation: the box consult folds to the spawn cells).
            if (world.isSolid(i1 + 0.5, j1 + 0.5, k1 + 0.5)
                    || world.isSolid(i1 + 0.5, j1 + 1 + 0.5, k1 + 0.5)
                    || world.inFluid(i1 + 0.5, j1 + 0.5, k1 + 0.5)) {
                continue;
            }
            // VIII14 — the callee spawns in the CALLER's world (the
            // reference's own spawnEntity over this.world): the dice read
            // the caller's dimension clock + the flank chunk's inhabited
            // row, and the body itself carries the caller's dimension
            // (a nether zombie's reinforcement is a nether zombie).
            int priorDimension = spawnDimension;
            spawnDimension = caller.dimension();
            MobEntity callee;
            try {
                callee = spawnAt(MobType.ZOMBIE, new Position(i1 + 0.5, j1, k1 + 0.5));
            } finally {
                spawnDimension = priorDimension;
            }
            if (callee == null) {
                continue; // the public spawn gate refused the body
            }
            callee.setDimension(caller.dimension());
            // setAttackTarget (line 218): the callee chases the caller's
            // own fight — the mob arm or the forced player hunt.
            if (targetMob != null && !targetMob.dead()) {
                callee.setMobAttackTarget(targetMob);
            } else if (playerAttacker != null) {
                callee.setForcedHuntPlayer(playerAttacker.engineEntityId());
            }
            // The charges (lines 220-222): both sides' chances fall 0.05.
            caller.addZombieReinforcementModifier(-0.05F);
            callee.addZombieReinforcementModifier(-0.05F);
            break;
        }
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

    /**
     * The equipment drop (Slice 92 — MobEntity.dropEquipment lines
     * 569-591, VIII4's five-slot completion): every slot [0]..[4] walks
     * the same law — the stack drops when the player-kill window is open
     * (or the slot carries the always-drop marker — the 2.0 a pickup
     * writes) and the chance roll clears, widened by the killer's looting
     * ({@code nextFloat() - looting * 0.01F < chance}); a damageable tool
     * on a rolled slot (never the 2.0 marker's own) rides the reference's
     * own durability shave ({@code max(maxDamage - 25, 1)} bound, the
     * {@code maxDamage - nextInt(nextInt(j) + 1)} cut, clamped 1..j). The
     * slots land in the reference's own order — held, feet, legs, chest,
     * head.
     */
    private void dropEquipment(MobEntity mob) {
        if (!mobLootEnabled()) {
            return;
        }
        int looting = mob.lootingLevel();
        for (int slot = 0; slot <= 4; slot++) {
            ItemStack stack = mob.equipment(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            float chance = mob.inventoryDropChance(slot);
            boolean always = chance > 1.0F;
            if (!(mob.playerHitRecently() || always)
                    || !(random.nextFloat() - looting * 0.01F < chance)) {
                continue;
            }
            ItemStack drop = stack;
            if (!always && stack.type().maxDurability() > 0 && !stack.unbreakable()) {
                // isDamageable: the durability shave — the dropped tool never
                // lands pristine (the reference's own cut). The shave rides
                // withDamage alone: the pre-G1 5-arg reconstruction stripped
                // every tag arm the stack carried (the Wave G strip-bug
                // pattern, caught in Wave J's audit — a named lore-carrying
                // locked sword now drops WITH its whole tag).
                int max = stack.type().maxDurability();
                int bound = Math.max(max - 25, 1);
                int k = max - random.nextInt(random.nextInt(bound) + 1);
                if (k > bound) {
                    k = bound;
                }
                if (k < 1) {
                    k = 1;
                }
                drop = stack.withDamage(k);
            }
            Position at = mob.position();
            lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()), drop, mob.dimension());
        }
    }

    // ------------------------------------- the village arms (VIII10)

    /**
     * The golem's village-defense consult (VIII10 — IronGolemEntity
     * .mobAiTick lines 66-79's own village read over
     * TrackIronGolemTargetGoal.canStart lines 19-39): the nearest village
     * within 32, its attacker census first (the creeper excluded — the
     * goal's own gate), the nearest live attacker becoming the golem's
     * own target — the mob arm through the mob target, the player arm
     * through the forced hunt — and the 1-in-20 bad-reputation player
     * fallback when no attacker stands. The reference caches the village
     * at the 70-120 cadence and the goal reads it every tick; the engine
     * resolves the nearest village live (the same village the cadence
     * would cache — the named adaptation, observably identical).
     */
    private void tickGolemVillageDefense(MobEntity golem) {
        net.zaminmc.torch.server.village.VillageSystem villages = villageSystem;
        if (villages == null) {
            return;
        }
        // The player arms' own gate (IronGolemEntity.canAttack line 137:
        // {@code !isPlayerCreated() || !PlayerEntity.isAssignableFrom(type)}
        // — a player-built golem never attacks players, the defense goal's
        // canTarget consult included).
        boolean mayHuntPlayers = !golem.golemPlayerCreated();
        Position at = golem.position();
        int x = (int) Math.floor(at.x());
        int y = (int) Math.floor(at.y());
        int z = (int) Math.floor(at.z());
        var village = villages.nearestVillage(x, y, z, 32);
        if (village == null) {
            return; // resetVillageRadius: the stroll stays the engine's own
        }
        int attackerId = villages.defenseAttackerId(village, x, y, z);
        if (attackerId >= 0) {
            MobEntity attackerMob = byId(attackerId);
            if (attackerMob != null) {
                golem.setMobAttackTarget(attackerMob);
            } else if (mayHuntPlayers) {
                golem.setForcedHuntPlayer(attackerId); // a player attacker
            }
            return;
        }
        // The 1-in-20 fallback (lines 30-33): the village's enemy player.
        if (mayHuntPlayers && random.nextInt(20) == 0) {
            String name = villages.defensePlayerName(village, x, y, z);
            if (name != null) {
                int playerId = villages.engineIdOfPlayer(name);
                if (playerId >= 0) {
                    golem.setForcedHuntPlayer(playerId);
                }
            }
        }
    }

    /**
     * The mating gate consult (VIII10 — VillagerMatingGoal
     * .checkVillageValidity lines 83-90 over canStart's village read,
     * lines 32-35): the nearest village within range 0 (the village must
     * CONTAIN the villager), the canMate window, and the population under
     * the 0.35-per-door band.
     */
    private boolean villagerMatingGate(MobEntity villager) {
        net.zaminmc.torch.server.village.VillageSystem villages = villageSystem;
        if (villages == null) {
            return false;
        }
        Position at = villager.position();
        var village = villages.nearestVillage(
                (int) Math.floor(at.x()), (int) Math.floor(at.y()),
                (int) Math.floor(at.z()), 0);
        if (village == null) {
            return false;
        }
        if (!village.canMate()) {
            return false;
        }
        int allowed = (int) (village.doorCount() * 0.35);
        return village.populationSize() < allowed;
    }

    /**
     * The villager's wound hook (VIII10 — VillagerEntity.setAttacker
     * lines 512-529): the village registers ANY living attacker in its
     * census (the golem's defense consult walks it), and a PLAYER
     * attacker costs reputation — a baby's wound −3, an adult's −1 —
     * with the angry-particles event 13.
     */
    private void villagerWoundHook(MobEntity villager, MobEntity mobAttacker,
                                   PlayerSession playerAttacker) {
        net.zaminmc.torch.server.village.VillageSystem villages = villageSystem;
        if (villages == null) {
            return;
        }
        Position at = villager.position();
        var village = villages.nearestVillage(
                (int) Math.floor(at.x()), (int) Math.floor(at.y()),
                (int) Math.floor(at.z()), 32);
        if (village == null) {
            return;
        }
        if (mobAttacker != null && !mobAttacker.dead()) {
            village.addOrUpdateAttacker(mobAttacker.entityId(), false,
                    mobAttacker.type().name());
        } else if (playerAttacker != null) {
            village.addOrUpdateAttacker(playerAttacker.engineEntityId(), true, null);
            village.updateReputation(playerAttacker.name(), villager.isBaby() ? -3 : -1);
            for (Listener listener : listeners) {
                listener.onMobStatusEvent(villager, 13); // the angry particles
            }
        }
    }

    /**
     * The villager's death hook (VIII10 — VillagerEntity.die lines
     * 532-550): a player's kill costs −2 reputation; a monster's kill
     * closes the village's mating window; a stranger's death with a
     * player watching within 16 blocks closes it too.
     */
    private void villagerDeathHook(MobEntity villager, MobEntity mobAttacker,
                                   PlayerSession playerAttacker) {
        net.zaminmc.torch.server.village.VillageSystem villages = villageSystem;
        if (villages == null) {
            return;
        }
        Position at = villager.position();
        var village = villages.nearestVillage(
                (int) Math.floor(at.x()), (int) Math.floor(at.y()),
                (int) Math.floor(at.z()), 32);
        if (village == null) {
            return;
        }
        if (playerAttacker != null) {
            village.updateReputation(playerAttacker.name(), -2);
        } else if (mobAttacker != null && mobAttacker.type().hostile) {
            village.stopMating();
        } else if (mobAttacker == null) {
            // die lines 541-546: a player watching within 16 blocks
            if (world.nearestPlayer(at.x(), at.y(), at.z(), 16.0) != null) {
                village.stopMating();
            }
        }
    }

    /**
     * The golem's death hook (VIII10 — IronGolemEntity.die lines
     * 239-245): a player killing a VILLAGE golem costs −5 reputation
     * (the pattern-built golem never turns on its builder's village).
     */
    private void golemDeathHook(MobEntity golem, PlayerSession playerAttacker) {
        if (playerAttacker == null || golem.golemPlayerCreated()) {
            return;
        }
        net.zaminmc.torch.server.village.VillageSystem villages = villageSystem;
        if (villages == null) {
            return;
        }
        Position at = golem.position();
        var village = villages.nearestVillage(
                (int) Math.floor(at.x()), (int) Math.floor(at.y()),
                (int) Math.floor(at.z()), 32);
        if (village != null) {
            village.updateReputation(playerAttacker.name(), -5);
        }
    }

    /** Rolls the kind's loot table into the world at the body. */
    private void dropLoot(MobEntity mob) {
        // doMobLoot (Slice 24a — LivingEntity.die lines 82-92: the whole
        // dropFewItems walk skips when the rule is off; the body still
        // dies, the animation still plays, nothing lands).
        if (!mobLootEnabled()) {
            return;
        }
        Position at = mob.position();
        // The charged-creeper head drop (ZombieEntity.die lines 589-598,
        // SkeletonEntity.die lines 156-172, CreeperEntity.die lines
        // 139-152): a victim killed by a CHARGED creeper's blast drops
        // its own head — once per creeper (shouldDropMobHead's own
        // counter), the zombie's head (damage 2, the pigman never — the
        // engine's own type split carries the gate), the skeleton's
        // (damage 1 wither variant, 0 plain), the creeper's own (damage
        // 4 — a creeper killed by ANOTHER charged creeper; the blasting
        // body itself never wounds itself, the pre-blast removal).
        MobEntity chargedCreeper = mob.chargedCreeperKiller();
        if (chargedCreeper != null && chargedCreeper.shouldDropMobHead()) {
            int headDamage;
            switch (mob.type()) {
                case ZOMBIE -> headDamage = 2;
                case SKELETON -> headDamage = mob.skeletonWither() ? 1 : 0;
                case CREEPER -> headDamage = 4;
                default -> headDamage = -1;
            }
            if (headDamage >= 0) {
                chargedCreeper.addMobHeadDrop();
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        net.zaminmc.torch.item.ItemStack.of(
                                net.zaminmc.torch.server.item.BuiltinItems.SKULL, 1)
                                .withDamage(headDamage), mob.dimension());
            }
        }
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
                        ItemStack.of(BuiltinItems.MAGMA_CREAM, 1), mob.dimension());
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
                        ItemStack.of(BuiltinItems.STRING, 1), mob.dimension());
            }
            if (random.nextInt(3) == 0 || random.nextInt(1 + looting) > 0) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.SPIDER_EYE, 1), mob.dimension());
            }
            return;
        }
        if (mob.type() == MobType.WITHER) {
            // WitherEntity.dropItems (lines 448-459): the one nether star
            // (the item's only source) under the never-despawn flag — the
            // star stays until collected. The 50-XP band and the
            // kill-wither achievement walk (the grown 50/100/50 player
            // scan) ride the death fan-out's own sites.
            lootSink.spawnPersistentLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                    ItemStack.of(BuiltinItems.NETHER_STAR, 1), mob.dimension());
            return;
        }
        if (mob.type() == MobType.BLAZE) {
            // BlazeEntity.dropItems (lines 145-153): nextInt(2 + looting)
            // single blaze rods — the full-looting bound like the pigman's
            // pair, each unit its own single-item stack.
            int rods = random.nextInt(2 + looting);
            for (int unit = 0; unit < rods; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.BLAZE_ROD, 1), mob.dimension());
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
                            ItemStack.of(BuiltinItems.COAL, 1), mob.dimension());
                }
            } else {
                int arrows = random.nextInt(3 + looting);
                for (int unit = 0; unit < arrows; unit++) {
                    lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                            ItemStack.of(BuiltinItems.ARROW, 1), mob.dimension());
                }
            }
            int bones = random.nextInt(3 + looting);
            for (int unit = 0; unit < bones; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.BONE, 1), mob.dimension());
            }
            // The held-item drop rides the general dropEquipment walk
            // (Slice 92 — the inline 0.085 arm retired into the model's
            // own chance quintet).
            // LivingEntity.die lines 766-768 + SkeletonEntity.dropRareItem
            // (lines 201-206): the wither variant's rare head — the
            // 0.025 + looting * 0.01 roll on the player-kill window.
            if (mob.skeletonWither() && mob.playerHitRecently()
                    && random.nextFloat() < RARE_DROP_BASE + looting * 0.01F) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        new ItemStack(BuiltinItems.SKULL, 1, 1, null), mob.dimension());
            }
            return;
        }
        if (mob.type() == MobType.RABBIT) {
            // RabbitEntity.dropItems (lines 280-296): the hide walk
            // {@code nextInt(2) + nextInt(1 + looting)}, then the meat
            // {@code nextInt(2)} with the fire's cooked swap — each unit
            // its own single-item stack. The rare foot (dropRareItem line
            // 276) rides the player-kill window's 0.025 + looting * 0.01
            // roll (LivingEntity.die lines 766-768).
            int hides = random.nextInt(2) + random.nextInt(1 + looting);
            for (int unit = 0; unit < hides; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.RABBIT_HIDE, 1), mob.dimension());
            }
            int meat = random.nextInt(2);
            for (int unit = 0; unit < meat; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(mob.burning()
                                ? BuiltinItems.COOKED_RABBIT : BuiltinItems.RABBIT, 1), mob.dimension());
            }
            if (mob.playerHitRecently()
                    && random.nextFloat() < RARE_DROP_BASE + looting * 0.01F) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.RABBIT_FOOT, 1), mob.dimension());
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
                        ItemStack.of(BuiltinItems.GHAST_TEAR, 1), mob.dimension());
            }
            int powder = random.nextInt(3) + random.nextInt(1 + looting);
            for (int unit = 0; unit < powder; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.GUNPOWDER, 1), mob.dimension());
            }
            return;
        }
        if (mob.type() == MobType.SHEEP) {
            // SheepEntity.dropItems (lines 97-111), 1:1: the unsheared
            // body carries one wool of its own color (the stack damage IS
            // the DyeColor id), then the mutton walk —
            // {@code nextInt(2) + 1 + nextInt(1 + looting)} units, the
            // fire's cooked swap, each unit its own single-item stack.
            if (!mob.sheared()) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        new ItemStack(BuiltinItems.WOOL, 1, mob.sheepFleeceColor()), mob.dimension());
            }
            int mutton = random.nextInt(2) + 1 + random.nextInt(1 + looting);
            for (int unit = 0; unit < mutton; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(mob.burning()
                                ? BuiltinItems.COOKED_MUTTON : BuiltinItems.MUTTON, 1), mob.dimension());
            }
            return;
        }
        if (mob.type() == MobType.ZOMBIE_PIGMAN) {
            // ZombiePigmanEntity.dropItems (lines 169-186): 0-1 rotten
            // flesh, then 0-1 gold nugget — the counts widen by the FULL
            // looting (nextInt(2 + looting), the reference's own bound);
            // each unit as its own single-item stack. dropRareItem (line
            // 189): the single gold ingot on the player-kill window's
            // 0.025 + looting * 0.01 roll.
            int flesh = random.nextInt(2 + looting);
            for (int unit = 0; unit < flesh; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.ROTTEN_FLESH, 1), mob.dimension());
            }
            int nuggets = random.nextInt(2 + looting);
            for (int unit = 0; unit < nuggets; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.GOLD_NUGGET, 1), mob.dimension());
            }
            if (mob.playerHitRecently()
                    && random.nextFloat() < RARE_DROP_BASE + looting * 0.01F) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.GOLD_INGOT, 1), mob.dimension());
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
                        new ItemStack(BuiltinItems.PRISMARINE_SHARD, shards, 0, null), mob.dimension());
            }
            if (random.nextInt(3 + looting) > 1) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.FISH, 1), mob.dimension());
            } else if (random.nextInt(3 + looting) > 1) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.PRISMARINE_CRYSTALS, 1), mob.dimension());
            }
            if (mob.guardianElder() && looting > 0) {
                // The wet sponge (Blocks.SPONGE with damage 1 — the engine's
                // stack damage field carries the variant).
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        new ItemStack(BuiltinItems.SPONGE, 1, 1, null), mob.dimension());
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
                        ItemStack.of(BuiltinItems.POPPY, 1), mob.dimension());
            }
            int ingots = 3 + random.nextInt(3);
            for (int unit = 0; unit < ingots; unit++) {
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(BuiltinItems.IRON_INGOT, 1), mob.dimension());
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
                            ItemStack.of(item, 1), mob.dimension());
                }
            }
        } else {
            for (ItemRoll roll : mob.type().loot) {
            int count = roll.roll(random) + random.nextInt(1 + looting);
            if (count <= 0) {
                continue;
            }
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()),
                        ItemStack.of(roll.type(), count), mob.dimension());
            }
        }
        // The worn gear and the donkey chest spill with the body
        // (HorseBaseEntity.dropInventoryAndChest on the death path).
        for (ItemStack stack : mob.spillMountGear()) {
            lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()), stack, mob.dimension());
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
                    at.x() + offsetX, at.y() + 0.5, at.z() + offsetZ),
                    parent.dimension());
            if (child == null) {
                continue; // the spawn event refused the child
            }
            // The split children live where the parent lived
            // (SlimeEntity.getInstance line 138's
            // {@code new SlimeEntity(this.world)}) — the dimension rides
            // the spawn.
            child.setSlimeSize(parentSize / 2);
            if (parent.persistent()) {
                child.setPersistent(true);
            }
            child.setBodyYaw(random.nextFloat() * 360.0F);
        }
    }

    /**
     * The regional difficulty's own multiplier (VIII14 — the
     * local-difficulty completion): the wired consult's
     * {@link net.zaminmc.torch.server.world.LocalDifficulty#getMultiplier()}
     * at the SPAWN POSITION and the spawn dimension (the reference's
     * World.getLocalDifficulty — the raw day clock's time term, the
     * chunk's inhabited row, the moon term capped by the time term, the
     * easy shave, the (regional - 2) / 2 clamp into 0..1). Unwired (the
     * bare test boots' own shape), the historical stand-in answers: the
     * same law with the inhabited + moon terms zeroed and the time term
     * over the WRAPPED clock — the fresh-chunk, new-moon read the
     * pre-VIII14 engine always carried.
     */
    private float localDifficultyMultiplier(Position position) {
        java.util.function.BiFunction<Position, Integer,
                net.zaminmc.torch.server.world.LocalDifficulty> consult = localDifficultyRead;
        if (consult != null) {
            return consult.apply(position, spawnDimension).getMultiplier();
        }
        java.util.function.IntSupplier difficultyAt = difficultyRead;
        int difficulty = difficultyAt != null ? difficultyAt.getAsInt() : 1;
        if (difficulty <= 0) {
            return 0.0F;
        }
        float timeTerm = Math.max(0.0F, Math.min(1.0F,
                (lastTimeOfDay - 72000.0F) / 1440000.0F)) * 0.25F;
        float bonus = 0.0F; // the inhabited + moon terms: zeroed (the unwired fallback)
        if (difficulty == 1) {
            bonus *= 0.5F;
        }
        float regional = difficulty * (0.75F + timeTerm + bonus);
        if (regional < 2.0F) {
            return 0.0F;
        }
        return Math.min((regional - 2.0F) / 2.0F, 1.0F);
    }

    /**
     * The global difficulty id (the reference's {@code world.getDifficulty()}
     * reads — 0 peaceful, 1 easy, 2 normal, 3 HARD). The dice's own HARD
     * bands consult this, never the local multiplier.
     */
    private int difficultyId() {
        java.util.function.IntSupplier difficultyAt = difficultyRead;
        return difficultyAt != null ? difficultyAt.getAsInt() : 1;
    }

    /**
     * The zombie's door-break arming roll (VIII9 — initialize line 430
     * 1:1): {@code setCanBreakDoors(nextFloat() < f * 0.1F)}, f the local
     * difficulty multiplier. Package-private static so the seeded dice
     * tests drive it with their own random.
     */
    static void rollZombieCanBreakDoors(MobEntity mob, Random random,
                                        float localMultiplier) {
        mob.setZombieCanBreakDoors(random.nextFloat() < localMultiplier * 0.1F);
    }

    /**
     * The zombie's leader roll (VIII9 — initialize lines 447-451 1:1): a
     * {@code nextFloat() < f * 0.05F} roll arms the LEADER — the
     * reinforcement attribute's own bonus ({@code nextDouble() * 0.25 +
     * 0.5}, the additive op-0 band), MAX_HEALTH's own op-2 multiply
     * ({@code nextDouble() * 3.0 + 1.0} over the plain 20 — the 40..100
     * ceiling the famous leader zombies carry), and the forced
     * door-break. Package-private static for the seeded dice tests.
     */
    static void rollZombieLeader(MobEntity mob, Random random,
                                 float localMultiplier) {
        if (random.nextFloat() < localMultiplier * 0.05F) {
            mob.addZombieReinforcementModifier(random.nextDouble() * 0.25 + 0.5);
            mob.applyZombieLeaderHealth(random.nextDouble() * 3.0 + 1.0);
            mob.setZombieCanBreakDoors(true);
        }
    }

    /**
     * The armor family's spawn dice (VIII4 — MobEntity.addRandomEquipment
     * lines 593-623, 1:1): the 0.15-times-multiplier gate, then the
     * equipment level roll — {@code nextInt(2)} widened by three 0.095
     * chances (the 0..4 band) — then the head-to-feet walk ({@code j = 3
     * .. 0}, the reference's own direction) where every slot past the
     * first takes the stop roll first ({@code nextFloat() < f}, the HARD
     * world's own 0.1 against the 0.25 everywhere else) and an EMPTY slot
     * equips the level's piece of its own column. The zombie's held arm
     * (ZombieEntity.addRandomEquipment lines 306-316) rides the tail: the
     * 1%-or-5%-by-HARD iron tool roll, the sword one third of the time.
     * Package-private static so the equivalence tests drive the dice with
     * their own seeded random.
     */
    static void rollSpawnEquipment(MobEntity mob, Random random,
                                   float localMultiplier, int globalDifficultyId) {
        if (random.nextFloat() < 0.15F * localMultiplier) {
            int level = random.nextInt(2);
            float stopChance = globalDifficultyId == 3 ? 0.1F : 0.25F;
            if (random.nextFloat() < 0.095F) {
                level++;
            }
            if (random.nextFloat() < 0.095F) {
                level++;
            }
            if (random.nextFloat() < 0.095F) {
                level++;
            }
            for (int j = 3; j >= 0; j--) {
                if (j < 3 && random.nextFloat() < stopChance) {
                    break;
                }
                if (mob.equipment(j + 1) == null) {
                    var piece = equipmentForSlot(j + 1, level);
                    if (piece != null) {
                        mob.setEquipment(j + 1, ItemStack.of(piece, 1));
                    }
                }
            }
        }
        if (mob.type() == MobType.ZOMBIE
                && random.nextFloat() < (globalDifficultyId == 3 ? 0.05F : 0.01F)) {
            mob.setHeldEquipment(ItemStack.of(random.nextInt(3) == 0
                    ? BuiltinItems.IRON_SWORD : BuiltinItems.IRON_SHOVEL, 1));
        }
    }

    /**
     * The level ladder (MobEntity.getEquipmentForSlot lines 646-698): the
     * material column per slot — leather, gold, chain, iron, diamond (the
     * reference's own order) — null past the band.
     */
    private static net.zaminmc.torch.item.ItemType equipmentForSlot(int slot, int level) {
        return switch (slot) {
            case 4 -> switch (level) {
                case 0 -> BuiltinItems.LEATHER_HELMET;
                case 1 -> BuiltinItems.GOLDEN_HELMET;
                case 2 -> BuiltinItems.CHAINMAIL_HELMET;
                case 3 -> BuiltinItems.IRON_HELMET;
                case 4 -> BuiltinItems.DIAMOND_HELMET;
                default -> null;
            };
            case 3 -> switch (level) {
                case 0 -> BuiltinItems.LEATHER_CHESTPLATE;
                case 1 -> BuiltinItems.GOLDEN_CHESTPLATE;
                case 2 -> BuiltinItems.CHAINMAIL_CHESTPLATE;
                case 3 -> BuiltinItems.IRON_CHESTPLATE;
                case 4 -> BuiltinItems.DIAMOND_CHESTPLATE;
                default -> null;
            };
            case 2 -> switch (level) {
                case 0 -> BuiltinItems.LEATHER_LEGGINGS;
                case 1 -> BuiltinItems.GOLDEN_LEGGINGS;
                case 2 -> BuiltinItems.CHAINMAIL_LEGGINGS;
                case 3 -> BuiltinItems.IRON_LEGGINGS;
                case 4 -> BuiltinItems.DIAMOND_LEGGINGS;
                default -> null;
            };
            case 1 -> switch (level) {
                case 0 -> BuiltinItems.LEATHER_BOOTS;
                case 1 -> BuiltinItems.GOLDEN_BOOTS;
                case 2 -> BuiltinItems.CHAINMAIL_BOOTS;
                case 3 -> BuiltinItems.IRON_BOOTS;
                case 4 -> BuiltinItems.DIAMOND_BOOTS;
                default -> null;
            };
            default -> null;
        };
    }

    /** The witch's live effect-store read (the drink ladder's gates). */
    private boolean witchHasEffect(MobEntity mob, int effectId) {
        net.zaminmc.torch.server.potion.PotionSystem potions = potionSystem;
        return potions != null && potions.hasEffect(mob, effectId);
    }

    /**
     * The body's live amplifier read (Slice 92 — the reference's own term
     * shape): {@code amplifier + 1} when the store carries the effect, 0
     * when absent (or before the potion system wires).
     */
    private int effectAmplifierPlusOne(MobEntity mob, int effectId) {
        net.zaminmc.torch.server.potion.PotionSystem potions = potionSystem;
        net.zaminmc.torch.server.potion.StatusEffectInstance effect =
                potions != null ? potions.get(mob, effectId) : null;
        return effect != null && effect.duration() > 0
                ? effect.amplifier() + 1 : 0;
    }

    // ------------------------------------------------ the loot pickup (Slice 92)

    /**
     * The loot-pickup scan (MobEntity.mobTick lines 339-347): a
     * pickup-armed body — the mobGriefing rule on, not dead, not NoAI
     * (the scan rides the ai section the reference's isLocallyControlled
     * gate owns — a pinned body never picks up) — walks the item
     * entities whose boxes meet its own grown by 1.0 horizontal, 0.0
     * vertical.
     */
    private void tickLootPickup(MobEntity mob) {
        ItemEntityManager items = itemEntities;
        if (items == null || !mob.canPickUpLoot() || mob.dead() || mob.noAi()) {
            return;
        }
        java.util.function.BooleanSupplier griefing = mobGriefingRead;
        if (griefing != null && !griefing.getAsBoolean()) {
            return;
        }
        double mx = mob.position().x();
        double mz = mob.position().z();
        double halfWidth = mob.type().width / 2 + 1.0; // grown(1.0, 0.0, 1.0)
        double minY = mob.position().y();
        double maxY = mob.position().y() + mob.type().height;
        for (ItemEntity item : items.all().toArray(new ItemEntity[0])) {
            if (!item.pickupAllowed()) { // !hasPickUpDelay
                continue;
            }
            Position at = item.position();
            if (at.x() < mx - halfWidth || at.x() > mx + halfWidth
                    || at.z() < mz - halfWidth || at.z() > mz + halfWidth
                    || at.y() + ItemEntity.HALF_HEIGHT < minY
                    || at.y() - ItemEntity.HALF_HEIGHT > maxY) {
                continue;
            }
            updateInventory(mob, item, items);
        }
    }

    /**
     * The equip walk (MobEntity.updateInventory lines 349-403, the
     * engine's held-slot shape — VIII4 landed the four armor slots' MODEL,
     * and this pickup walk still refuses them): the better-item
     * comparison — a sword over a non-sword, the sword family by attack
     * damage (ties by the stack damage), the bow pair by the nbt alone —
     * then the old hand's 0.1-widened chance drop, the equip, the
     * always-drop marker (2.0), the persistence pin, and the collect. The
     * ARMOR compare (updateInventory lines 373-385 — an ArmorItem over a
     * non-armor piece, the protection tie, the metadata tie) is the
     * family's remaining demand-named arm: a mob never picks armor up
     * mid-life here (the slots fill through the spawn dice, the /summon
     * rows, and the anvil import).
     */
    private void updateInventory(MobEntity mob, ItemEntity item, ItemEntityManager items) {
        ItemStack stack = item.stack();
        if (stack == null || equipmentSlotOf(stack) > 0) {
            return; // the armor pickup arm: demand-named (see the javadoc)
        }
        // VIII10 — the villager's own updateInventory override
        // (VillagerEntity lines 821-832): the food family lands in the
        // 8-slot food stock (the willing model's own inventory), never
        // the hand — the whole stack lands or the remainder rides the
        // item back (SimpleInventory.addItem's own shape). The villager
        // never despawns (its own canDespawn false), so the pickup walk's
        // persistence pin rides the held arm alone.
        if (mob.type() == MobType.VILLAGER) {
            ItemStack remainder = mob.villagerAcceptFood(stack);
            if (remainder == null) {
                items.collectByMob(item, mob);
            } else if (remainder != stack) {
                item.setStack(remainder);
            }
            return;
        }
        ItemStack held = mob.heldEquipment();
        boolean better;
        if (held == null) {
            better = true;
        } else {
            var newSpec = net.zaminmc.torch.server.item.Tools
                    .specOf(stack.type()).orElse(null);
            var oldSpec = net.zaminmc.torch.server.item.Tools
                    .specOf(held.type()).orElse(null);
            boolean newSword = newSpec != null && newSpec.toolClass()
                    == net.zaminmc.torch.server.item.ToolClass.SWORD;
            boolean oldSword = oldSpec != null && oldSpec.toolClass()
                    == net.zaminmc.torch.server.item.ToolClass.SWORD;
            boolean newBow = stack.type().identifier().value().equals("bow");
            boolean oldBow = held.type().identifier().value().equals("bow");
            if (newSword && !oldSword) {
                better = true;
            } else if (newSword && oldSword) {
                // Wave J's fold consult (the reference's own compare reads
                // the sword tier's field; the engine upgrades both sides
                // to the held stack's folded damage — a tagged sword
                // out-ranks an untagged one, the tagless fall-back keeps
                // the tier's own ordering).
                float newDamage = net.zaminmc.torch.server.item.Tools
                        .heldDamageOf(stack);
                float oldDamage = net.zaminmc.torch.server.item.Tools
                        .heldDamageOf(held);
                if (newDamage != oldDamage) {
                    better = newDamage > oldDamage;
                } else {
                    better = stack.damage() > held.damage()
                            || hasTagRows(stack) && !hasTagRows(held);
                }
            } else if (newBow && oldBow) {
                better = hasTagRows(stack) && !hasTagRows(held);
            } else {
                better = false;
            }
        }
        if (better) { // canHoldItem: the reference's own default true
            if (held != null && random.nextFloat() - 0.1F
                    < mob.inventoryDropChance(0)) {
                Position at = mob.position();
                lootSink.spawnLootDrop(new Position(at.x(), at.y() + 0.5, at.z()), held, mob.dimension());
            }
            // The GIVE_DIAMOND arm (updateInventory lines 393-399) keys on
            // the item's thrower — the engine's ItemEntity carries no
            // thrower field (the envelope, ledgered).
            mob.setHeldEquipment(stack);
            mob.setInventoryDropChance(0, 2.0F);
            mob.setPersistent(true);
            items.collectByMob(item, mob);
            equipmentChanged(mob); // the Entity Equipment publish
        }
    }

    /**
     * The engine's stack "nbt" read (the bow pair's own tie-breaker —
     * the reference's {@code hasNbt()}}): a custom name or an
     * enchantment pair rides the stack's own rows.
     */
    private static boolean hasTagRows(ItemStack stack) {
        return (stack.displayName() != null && !stack.displayName().isEmpty())
                || (stack.enchantments() != null && !stack.enchantments().isEmpty());
    }

    /**
     * The equipment-slot mapping (MobEntity.getEquipmentSlot lines
     * 625-647): the pumpkin and the skull to the head (4), the armor
     * family by its own slot, everything else to the hand (0).
     */
    private static int equipmentSlotOf(ItemStack stack) {
        String id = stack.type().identifier().value();
        if (id.equals("pumpkin") || id.equals("skull")) {
            return 4;
        }
        var armor = net.zaminmc.torch.server.item.Armor.specOf(stack.type());
        if (armor.isPresent()) {
            return switch (armor.get().slot()) {
                case HEAD -> 4;
                case CHEST -> 3;
                case LEGS -> 2;
                case FEET -> 1;
            };
        }
        return 0;
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
        if (mob.consumePendingAmbientHearts()) {
            // The AnimalEntity.mobTick heart clock, 1:1 (lines 42-61): one
            // HEART particle every 10th love tick at a random point inside
            // the body's footprint, drifting on three fresh gaussians. The
            // reference's own shapes: x + rand*width*2 - width,
            // y + 0.5 + rand*height, and the 0.02-sigma velocity cloud.
            double w = mob.type().width;
            double h = mob.type().height;
            Position base = mob.position();
            Position at = new Position(
                    base.x() + random.nextFloat() * w * 2.0 - w,
                    base.y() + 0.5 + random.nextFloat() * h,
                    base.z() + random.nextFloat() * w * 2.0 - w);
            double vx = random.nextGaussian() * 0.02;
            double vy = random.nextGaussian() * 0.02;
            double vz = random.nextGaussian() * 0.02;
            for (Listener listener : listeners) {
                listener.onMobAmbientHearts(mob, at, vx, vy, vz);
            }
        }
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
        // The CAUSE player (VIII6 — AnimalBreedGoal.breed lines 84-89's own
        // read: the parent's love player first, the mate's as the fallback,
        // captured BEFORE the reset clears the rows).
        int causePlayerId = parent.lovePlayerId();
        if (causePlayerId <= 0) {
            causePlayerId = mate.lovePlayerId();
        }
        // The calf lives where the parents live (CowEntity.createChild
        // line 110's {@code new CowEntity(this.world)}) — the dimension
        // rides the spawn.
        MobEntity baby = spawnAt(childTypeOf(parent, mate), parent.position(),
                parent.dimension());
        baby.setHorseSubtype(childSubtypeOf(parent, mate));
        if (baby.type() == MobType.RABBIT) {
            // RabbitEntity.makeChild (lines 302-309): the coat comes from
            // either parent — the reference's own {@code nextBoolean()} coin.
            baby.setRabbitVariant(random.nextBoolean()
                    ? parent.rabbitVariant() : mate.rabbitVariant());
        }
        if (baby.type() == MobType.SHEEP) {
            // SheepEntity.makeChild line 243: the coat's own mixing ladder.
            baby.setSheepFleeceColor(offspringSheepColor(parent, mate));
        }
        parent.setBreedingAge(MobEntity.ADULT_BREED_COOLDOWN_TICKS);
        mate.setBreedingAge(MobEntity.ADULT_BREED_COOLDOWN_TICKS);
        parent.resetLove();
        mate.resetLove();
        baby.setBreedingAge(MobEntity.CHILDHOOD_TICKS);
        if (parent.type() == MobType.VILLAGER) {
            // VIII10 — VillagerMatingGoal.breed lines 96-97: the villagers'
            // own willing flag clears at the breed (the food model's
            // cooldown is the breeding-age pair the engine walk lands).
            parent.setVillagerWilling(false);
            mate.setVillagerWilling(false);
        }
        for (Listener listener : listeners) {
            listener.onMobBred(parent, mate, baby, causePlayerId);
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
     * The sheep coat's mixing ladder (SheepEntity.getColorForOffspring
     * lines 262-276, 1:1): both parents' colors move into the dye domain
     * (the id's own metadata {@code 15 - id}), the two-dye 2x1 grid goes
     * through the crafting consult — a matching dye-mix recipe (the
     * reference's own registerShapeless pairs) hands back the mixed
     * color's metadata — and an unmatched pair falls to the parent coin.
     * The answer returns as the fleece's DyeColor id. Package-private for
     * the behavioral tests (the same package's MobManagerTest).
     */
    int offspringSheepColor(MobEntity parent, MobEntity mate) {
        int i = 15 - (parent.sheepFleeceColor() & 15);
        int j = 15 - (mate.sheepFleeceColor() & 15);
        int k;
        net.zaminmc.torch.server.crafting.CraftingService consult = craftingConsult;
        java.util.Optional<net.zaminmc.torch.server.crafting.CraftingRecipe> matched =
                java.util.Optional.empty();
        if (consult != null) {
            // The reference's 2x1 CraftingInventory rides the engine's 2x2
            // matcher: the two dyes fill the top row, the bottom row empty
            // (the shapeless multiset only counts filled cells).
            ItemStack[] grid = {
                    new ItemStack(BuiltinItems.INK_SAC, 1, i),
                    new ItemStack(BuiltinItems.INK_SAC, 1, j),
                    ItemStack.EMPTY, ItemStack.EMPTY,
            };
            matched = consult.match(grid, 2);
        }
        if (matched.isPresent()
                && matched.get().result().item()
                        .equals(net.zaminmc.torch.util.Identifier.parse("minecraft:dye"))) {
            k = matched.get().result().damage();
        } else {
            k = random.nextBoolean() ? i : j;
        }
        return 15 - k; // DyeColor.byMetadata(k).getId()
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
            if (type == MobType.ZOMBIE && random.nextFloat() < 0.05F) {
                // ZombieEntity.initialize's Data roll (line 401: the 5%
                // villager skin — {@code world.random.nextFloat() < 0.05F}):
                // the natural zombie villager the cure's whole family keys
                // on (Slice 25a).
                mob.setZombieVillager(true);
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
        // VIII14 — the spawn-dimension context: the dice inside spawnAt
        // consult the local difficulty at the NETHER's own reads (the
        // callee clock, the nether chunk's inhabited row, the shared
        // moon).
        int priorDimension = spawnDimension;
        spawnDimension = -1;
        MobEntity mob;
        try {
            mob = spawnNatural(type, position, baby, yaw);
        } finally {
            spawnDimension = priorDimension;
        }
        if (mob != null) {
            mob.setDimension(-1); // the body lives in the nether (Slice 25d)
            if (type == MobType.SKELETON && random.nextInt(5) > 0) {
                mob.setSkeletonWither(true);
                mob.setHeldEquipment(ItemStack.of(BuiltinItems.STONE_SWORD, 1));
                // The wither arm REPLACES the dice (initialize's if/else —
                // the else branch alone calls addRandomEquipment): the
                // spawn-site roll above never happened for this body, so
                // the four armor slots strip back to empty 1:1.
                for (int slot = 1; slot <= 4; slot++) {
                    mob.setEquipment(slot, null);
                }
            }
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
            if (player.state() != net.zaminmc.torch.entity.PlayerState.PLAYING || player.dead()
                    // Slice 25d: the melee target reads the body's own
                    // dimension's players (the same coords exist in both).
                    || player.dimension() != mob.dimension()) {
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
