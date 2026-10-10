package net.zaminmc.torch.server.player;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.Player;

import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.World;
import net.zaminmc.torch.server.net.ClientLink;

import java.util.Objects;
import java.util.UUID;

/**
 * The engine-side gameplay state of one connected player.
 *
 * <p>Ownership: mutated only by the simulation context (through the engine's
 * command queue). The {@link ClientLink} is the protocol adapter's handle back to
 * the wire; the engine only uses it to push engine-decided lifecycle actions
 * (kick). Gameplay never sees transport objects.</p>
 */
public final class PlayerSession implements net.zaminmc.torch.entity.Player {

    private final UUID uuid;
    private final String name;
    private final ClientLink link;
    private final PlayerInventory inventory = new PlayerInventory();
    private final CraftingGrid crafting = new CraftingGrid();          // window 0: 2x2
    private final CraftingGrid tableCrafting = new CraftingGrid(3, 3); // container: 3x3
    private volatile World world;
    private volatile PlayerState state = PlayerState.CONNECTING;

    // Position state is written by the simulation context only; volatile for cross-thread reads.
    private volatile Position position = Position.ZERO; // replaced at spawn
    private volatile Rotation rotation = Rotation.ZERO;
    /** The vehicle this body rides (engine-global id, -1 = on foot). */
    private volatile int ridingVehicleId = -1;
    /** The mob mount this body rides (engine id, -1 = on foot; the horse/pig slice). */
    private volatile int ridingMobId = -1;
    private volatile boolean onGround = true;
    /** The open container window id (crafting table, furnace), or -1 when none. Tick-thread written, volatile-read by the adapter's sync. */
    private volatile int containerWindowId = -1;
    /** Which container the open window is (routes wire layout + click semantics). */
    private volatile ContainerKind containerKind = ContainerKind.NONE;
    /** The block the open container belongs to (furnace state lookup); null otherwise. */
    private volatile net.zaminmc.torch.block.BlockPosition containerPosition;
    /** The mount whose inventory is open (the HORSE container's engine id); -1 otherwise. */
    private volatile int containerMountId = -1;
    /** The selected trade row of the open villager window (the MC|TrSel index); -1 none. */
    private volatile int selectedTrade = -1;
    /** The anti-cheat violation ledger (reach/nuker/fastplace counters + rings). */
    private final Violations violations = new Violations();

    /** The kinds of container windows a session can hold open. */
    public enum ContainerKind {
        NONE,
        /** The 3x3 crafting table (10-slot GUI, grid state lives in the session). */
        CRAFTING_TABLE,
        /** The furnace (3-slot GUI, slot state lives in the world at containerPosition). */
        FURNACE,
        /** The chest (27-slot GUI, slot state lives in the world at containerPosition). */
        CHEST,
        /** The horse inventory (saddle + armor slots live on the mount at containerPosition's mount id). */
        HORSE,
        /** The villager trading window (offers ride the MC|TrList plugin message). */
        VILLAGER,
        /** The enchanting table (2 transient slots living in the session's menu, seed + costs included). */
        ENCHANTING_TABLE
    }

    // --- survival body state (§436 family) ---------------------------------
    // Mutated by the simulation context (the engine's body tick and damage
    // entry points); volatile for the adapter's cross-thread health syncs.

    /** Historical defaults: full hearts, full hunger, 5 saturation. */
    public static final float MAX_HEALTH = 20.0f;
    public static final int MAX_FOOD = 20;
    public static final float DEFAULT_SATURATION = 5.0f;
    /** The exhaustion point that costs one saturation or food unit (historical 4.0). */
    public static final float EXHAUSTION_COST = 4.0f;
    /** Regen/starvation cadence: one point every 80 ticks (4 s). */
    public static final int BODY_TIMER_PERIOD = 80;
    /** Easy difficulty's starvation floor (the death-by-hunger guard). */
    public static final float STARVATION_FLOOR = 10.0f;
    /** Falls further than this hurt: damage = ceil(distance - SAFE_FALL). */
    public static final float SAFE_FALL_DISTANCE = 3.0f;

    private volatile float health = MAX_HEALTH;
    private volatile int food = MAX_FOOD;
    private volatile float saturation = DEFAULT_SATURATION;
    private float exhaustion;
    private int bodyTimer; // shared regen/starve cadence counter
    private float fallDistance;
    private volatile boolean dead;
    // The /reply target: the name of the last player who /tell'd this one.
    // A name (not a uuid) because the reply may outlive a relogin window.
    private volatile String lastMessagedBy;
    // Eating: the server runs its own 32-tick timer, started by the use-item
    // gesture; the client's release (dig status 5) cancels when unfinished.
    private int eatingTicks = -1;

    // Posture flags from the Entity Action (0x0B) stream. The client animates
    // locally; the engine tracks them for the observers' metadata flags, the
    // sprint exhaustion cost and future movement validation multipliers.
    private volatile boolean sneaking;
    private volatile boolean sprinting;

    // The server-known motion (the vanilla EntityPlayer motionX/Y/Z): only
    // explicit server impulses write it — knockback, explosions — the
    // client's own movement never does. The vanilla applyKnockback halves
    // THIS value before adding the impulse, so chained hits decay exactly
    // as historical, and a fresh hit on a resting body rides the plain 0.4.
    private volatile double motionX;
    private volatile double motionY;
    private volatile double motionZ;

    // The body-delta scratch (the movement-exhaustion ledger): the previous
    // body position and ground state, captured once per tick by the body
    // tick so the per-meter rates charge from the real displacement.
    private double bodyPrevX;
    private double bodyPrevY;
    private double bodyPrevZ;
    private boolean bodyPrevOnGround;
    private boolean bodyPrevValid;

    // --- movement guard state (the anti-cheat baseline) ---------------------

    /** Grace-window ticks left: teleports, knockback and join bursts ride it. */
    private volatile int graceTicks;
    /** Flight rights (creative/spectator, or an explicit /fly grant). */
    private volatile boolean allowedToFly;
    /** The client's reported flying state (Player Abilities 0x13 bit 1). */
    private volatile boolean flying;
    /** Sustained airborne non-descent ticks (the hover tell). */
    private volatile int hoverTicks;

    // Bow charge: begins at the use gesture while a bow is held, ends at the
    // release gesture; the arrow's launch speed scales with the held duration.
    private int bowChargeTicks = -1;

    /**
     * The engine-global entity id (its own id band, disjoint from items, mobs,
     * falling blocks and projectiles). Assigned once at registration; -1 before.
     * Combat systems that speak engine-global ids (projectile throwers, future
     * combat events) use it; the per-observer wire ids remain the adapter's.
     */
    private volatile int engineEntityId = -1;

    /**
     * The per-player game mode (the historical per-EntityPlayer theGamemode).
     * Survives restarts through the personal store; new players inherit the
     * server default. Tick-thread written, volatile for wire reads.
     */
    private volatile net.zaminmc.torch.GameMode gamemode = net.zaminmc.torch.GameMode.SURVIVAL;
    /** The operator level (0 = player, 4 = console-equivalent). */
    private volatile int opLevel;

    /** The underwater breath clock: 300 ticks of air (the historical 15 s). */
    private int airTicks = 300;
    private int drownTimer;

    /** The body's fire clock (ticks of burning left; 0 = not on fire). */
    private volatile int fireTicks;

    /**
     * The body's accumulated experience points (the authoritative state;
     * level and bar progress derive from it through ExperienceMath).
     * Persisted in ZPD v6.
     */
    private volatile long totalXp;

    // --- experience (the historical XP bar state) ----------------------------

    /** @return the accumulated XP points (authoritative; level derives from it). */
    public long totalXp() {
        return totalXp;
    }

    /** Adds XP points (orb pickup, future furnace takes). Tick-thread context. */
    public void addExperience(long amount) {
        if (amount > 0) {
            totalXp += amount;
        }
    }

    /** Overwrites the XP total (restore, death reset). Tick-thread context. */
    public void setTotalXp(long value) {
        this.totalXp = Math.max(0, value);
    }

    /** @return the level the XP total currently sits at. */
    public int experienceLevel() {
        return net.zaminmc.torch.server.experience.ExperienceMath.levelForTotalXp(totalXp);
    }

    // --- the enchanting seed + the level-cost payment (the reference
    // PlayerEntity.enchantmentTableSeed / applyEnchantmentCosts) -------------

    /**
     * The enchanting table's per-player RNG seed. Vanilla loads it from the
     * persisted {@code XpSeed} and falls back to {@code random.nextInt()}; the
     * engine lazily rolls it on first read (0 stays a legal seed — the
     * reference never special-cases 0 after load).
     */
    private volatile int enchantingSeed;

    /** The seed's lazy-roll flag (the reference rolls at load; the engine rolls at first use). */
    private volatile boolean enchantingSeedRolled;

    /** The player-entity random the reference rerolls the seed with. */
    private final java.util.Random playerRandom = new java.util.Random();

    /** @return the current enchanting seed (rolling it on first read). Tick-thread context. */
    public int enchantingSeed() {
        if (!enchantingSeedRolled) {
            enchantingSeed = playerRandom.nextInt();
            enchantingSeedRolled = true;
        }
        return enchantingSeed;
    }

    /**
     * The reference {@code applyEnchantmentCosts}: the level pays {@code cost},
     * the progress into the level is kept, a level below 0 zeroes the whole
     * bar (level, progress, points), and the enchanting seed rerolls — the
     * exact walk on the engine's authoritative points model (the progress
     * rides the same point tail the bar float derives from).
     * Tick-thread context.
     */
    public void applyEnchantmentCosts(int cost) {
        int level = experienceLevel();
        int next = level - cost;
        if (next < 0) {
            totalXp = 0;
        } else {
            long into = totalXp - net.zaminmc.torch.server.experience.ExperienceMath.totalXpForLevel(level);
            totalXp = net.zaminmc.torch.server.experience.ExperienceMath.totalXpForLevel(next) + into;
        }
        enchantingSeed = playerRandom.nextInt();
        enchantingSeedRolled = true;
    }

    /**
     * The open enchanting menu's state (two slots, seed, costs, clues) — the
     * historical per-open {@code EnchantingTableMenu}. Non-null only while an
     * ENCHANTING_TABLE window is open; the close paths drop its slots.
     */
    private volatile net.zaminmc.torch.server.enchantment.EnchantingMenu enchantingMenu;

    /** @return the open enchanting menu, or null when no table window is open. */
    public net.zaminmc.torch.server.enchantment.EnchantingMenu enchantingMenu() {
        return enchantingMenu;
    }

    /** Installs (or clears with null) the open enchanting menu. Tick-thread context. */
    public void setEnchantingMenu(net.zaminmc.torch.server.enchantment.EnchantingMenu menu) {
        this.enchantingMenu = menu;
    }

    /** @return the name of the last player who /tell'd this one (the /reply target). */
    public String lastMessagedBy() {
        return lastMessagedBy;
    }

    /** Records the /reply target (the last private-message sender). */
    public void noteMessagedBy(String name) {
        this.lastMessagedBy = name;
    }

    // --- fire (the burning slice) ---------------------------------------------

    /** @return ticks of burning left (0 = not on fire). */
    public int fireTicks() {
        return fireTicks;
    }

    /** @return true while the body is on fire (the living-flags bit 0x01). */
    public boolean burning() {
        return fireTicks > 0;
    }

    /** Sets the fire clock to at least {@code ticks} (the ignite rule). */
    public void ignite(int ticks) {
        if (ticks > fireTicks) {
            fireTicks = ticks;
        }
    }

    /** Per-tick decay of the fire clock. Tick-thread context. */
    public void tickFire() {
        if (fireTicks > 0) {
            fireTicks--;
        }
    }

    /** Clears the fire clock (water contact, respawn, mode switches). */
    public void extinguish() {
        fireTicks = 0;
        fireDamageTimer = 0;
    }

    /** The in-fire / burning damage cadence counter (10 vs 20 ticks). */
    private int fireDamageTimer;

    /** @return the incremented fire damage cadence counter. */
    public int advanceFireDamageTimer() {
        return ++fireDamageTimer;
    }

    public void resetFireDamageTimer() {
        fireDamageTimer = 0;
    }

    /** The cactus contact cadence counter (its own clock, fire's is separate). */
    private int cactusTimer;

    /** @return the incremented cactus contact cadence counter. */
    public int advanceCactusTimer() {
        return ++cactusTimer;
    }

    public PlayerSession(UUID uuid, String name, ClientLink link) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = Objects.requireNonNull(name, "name");
        this.link = Objects.requireNonNull(link, "link");
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    /** The engine-global entity id, or -1 before registration assigns it. */
    public int engineEntityId() {
        return engineEntityId;
    }

    /** Assigns the engine-global entity id (EngineServer registration only, once). */
    public void assignEngineEntityId(int id) {
        if (this.engineEntityId != -1 && this.engineEntityId != id) {
            throw new IllegalStateException("Player " + name + " already owns entity id "
                    + this.engineEntityId);
        }
        this.engineEntityId = id;
    }

    public ClientLink link() {
        return link;
    }

    public net.zaminmc.torch.GameMode gamemode() {
        return gamemode;
    }

    /** Tick-thread write only; the wire reads are volatile-safe. */
    public void setGamemode(net.zaminmc.torch.GameMode mode) {
        this.gamemode = java.util.Objects.requireNonNull(mode, "mode");
    }

    public int opLevel() {
        return opLevel;
    }

    /**
     * Advances the breath clock: underwater it drains, out of water it
     * refills. @return true when a drowning damage tick is due (2 per second
     * after the air ran out, the historical EntityPlayer rhythm).
     */
    public boolean advanceBreath(boolean underwater) {
        if (!underwater) {
            airTicks = 300;
            drownTimer = 0;
            return false;
        }
        if (airTicks > 0) {
            airTicks--;
            return false;
        }
        return ++drownTimer % 20 == 0;
    }

    public void setOpLevel(int level) {
        this.opLevel = Math.max(0, level);
    }

    /**
     * The authoritative inventory. Mutations run on the simulation context only
     * (via engine-submitted tasks); reads for wire sync must treat the snapshot
     * accordingly.
     */
    public PlayerInventory inventory() {
        return inventory;
    }

    /**
     * The player's 2x2 crafting grid (window state beside the inventory).
     * Same ownership discipline as the inventory: tick-thread mutations only.
     */
    public CraftingGrid crafting() {
        return crafting;
    }

    /**
     * The crafting table's 3x3 grid, used while a table container window is
     * open. Same ownership discipline as the inventory: tick-thread mutations
     * only; contents are transient window state, never persisted.
     */
    public CraftingGrid tableCrafting() {
        return tableCrafting;
    }

    /** @return the open container window id, or -1 when no container is open. */
    public int openContainerWindowId() {
        return containerWindowId;
    }

    /** @return which container the open window is (never null). */
    public ContainerKind openContainerKind() {
        return containerKind;
    }

    /** @return the block the open container belongs to, or null for the crafting table. */
    public net.zaminmc.torch.block.BlockPosition openContainerPosition() {
        return containerPosition;
    }

    /** Marks the container window as open (engine-assigned id &gt; 0). */
    public void openContainerWindow(int windowId, ContainerKind kind,
                                    net.zaminmc.torch.block.BlockPosition position) {
        if (windowId <= 0) {
            throw new IllegalArgumentException("Container window ids start at 1: " + windowId);
        }
        Objects.requireNonNull(kind, "kind");
        if (kind == ContainerKind.NONE) {
            throw new IllegalArgumentException("NONE cannot open a window");
        }
        this.containerWindowId = windowId;
        this.containerKind = kind;
        this.containerPosition = position;
        this.containerMountId = -1;
        this.selectedTrade = -1;
    }

    /** Opens the horse inventory window (the mount's engine id rides along). */
    public void openHorseWindow(int windowId, int mountEngineId) {
        openContainerWindow(windowId, ContainerKind.HORSE, null);
        this.containerMountId = mountEngineId;
    }

    /** Opens the villager trading window (the trader's engine id rides along). */
    public void openVillagerWindow(int windowId, int villagerEngineId) {
        openContainerWindow(windowId, ContainerKind.VILLAGER, null);
        this.containerMountId = villagerEngineId;
    }

    /** @return the mount id of the open HORSE window, or -1. */
    public int containerMountId() {
        return containerMountId;
    }

    /** @return the selected trade row of the open villager window, or -1. */
    public int selectedTrade() {
        return selectedTrade;
    }

    /** Tick-thread only: the MC|TrSel index lands here (the offer to execute). */
    public void setSelectedTrade(int index) {
        this.selectedTrade = index;
    }

    /** @return the anti-cheat violation ledger (tick-thread confined). */
    public Violations violations() {
        return violations;
    }

    /** Marks the container window as closed (no container open). */
    public void closeContainerWindow() {
        this.containerWindowId = -1;
        this.containerKind = ContainerKind.NONE;
        this.containerPosition = null;
        this.containerMountId = -1;
        this.selectedTrade = -1;
    }

    public PlayerState state() {
        return state;
    }

    public World world() {
        return world;
    }

    public Position position() {
        return position;
    }

    public Rotation rotation() {
        return rotation;
    }

    /** The vehicle this body rides (engine-global id), or -1 on foot. */
    public int ridingVehicleId() {
        return ridingVehicleId;
    }

    /** Tick-thread only: the mount takes the body, the dismount frees it. */
    public void setRidingVehicleId(int vehicleId) {
        this.ridingVehicleId = vehicleId;
    }

    /** The mob mount this body rides (engine id), or -1 on foot. */
    public int ridingMobId() {
        return ridingMobId;
    }

    /** Tick-thread only: the mount takes the body, the dismount frees it. */
    public void setRidingMobId(int mobId) {
        this.ridingMobId = mobId;
    }

    /** @return whether the body sits in any seat (vehicle or mob mount). */
    public boolean ridingAny() {
        return ridingVehicleId >= 0 || ridingMobId >= 0;
    }

    public boolean onGround() {
        return onGround;
    }

    // --- body read/write (simulation context) -------------------------------

    public float health() {
        return health;
    }

    public int food() {
        return food;
    }

    public float saturation() {
        return saturation;
    }

    public boolean dead() {
        return dead;
    }

    public boolean eating() {
        return eatingTicks >= 0;
    }

    public int eatingTicks() {
        return eatingTicks;
    }

    /** Applies a damage amount (already computed by the caller); clamps at 0. */
    public void hurt(float amount) {
        if (amount <= 0 || dead) {
            return;
        }
        health = Math.max(0.0f, health - amount);
    }

    // --- combat invulnerability frames (the historical 10-tick hurt window) ---

    /** Ticks left before the next hit may land; the historical half-second. */
    public static final int HURT_INVULNERABILITY_TICKS = 10;

    private int hurtInvulnerabilityTicks;
    private float lastHurtDamage;

    /** Enters the hurt window remembering the damage the frame must out-damage. */
    public void beginHurtInvulnerability(float damage) {
        this.hurtInvulnerabilityTicks = HURT_INVULNERABILITY_TICKS;
        this.lastHurtDamage = damage;
    }

    /** True while a hit weaker than the frame's damage cannot re-hurt. */
    public boolean hurtInvulnerable() {
        return hurtInvulnerabilityTicks > 0;
    }

    /** The damage the active hurt frame absorbed; a stronger hit out-damages it. */
    public float lastHurtDamage() {
        return lastHurtDamage;
    }

    /** Per-tick decay of the hurt window. Tick-thread context. */
    public void tickHurtInvulnerability() {
        if (hurtInvulnerabilityTicks > 0) {
            hurtInvulnerabilityTicks--;
        }
    }

    public void resetHurtInvulnerability() {
        hurtInvulnerabilityTicks = 0;
        lastHurtDamage = 0.0f;
    }

    /** Direct body write for engine commands (eat, regen, respawn, restore). */
    public void setBody(float newHealth, int newFood, float newSaturation) {
        this.health = Math.max(0.0f, Math.min(MAX_HEALTH, newHealth));
        this.food = Math.max(0, Math.min(MAX_FOOD, newFood));
        this.saturation = Math.max(0.0f, newSaturation);
    }

    public void markDead() {
        this.dead = true;
        this.eatingTicks = -1;
        this.bowChargeTicks = -1;
    }

    /** Full body reset (respawn, fresh join). */
    public void resetBody() {
        this.health = MAX_HEALTH;
        this.food = MAX_FOOD;
        this.saturation = DEFAULT_SATURATION;
        this.exhaustion = 0;
        this.bodyTimer = 0;
        this.fallDistance = 0;
        this.dead = false;
        this.eatingTicks = -1;
        this.bowChargeTicks = -1;
        this.sneaking = false;
        this.sprinting = false;
        this.motionX = 0;
        this.motionY = 0;
        this.motionZ = 0;
        this.bodyPrevValid = false;
        extinguish();
        resetFireDamageTimer();
        resetHurtInvulnerability();
        clearHoverTicks();
    }

    /** Exhaustion accrual (the vanilla cap: 40.0). */
    public void addExhaustion(float amount) {
        exhaustion = Math.min(exhaustion + amount, EXHAUSTION_CAP);
    }

    /** The vanilla exhaustion ceiling ({@code Math.min(this.exhaustion + e, 40.0F)}). */
    public static final float EXHAUSTION_CAP = 40.0f;

    /** @return the pending exhaustion (tick consumes it through the food rules). */
    public float exhaustion() {
        return exhaustion;
    }

    public void setExhaustion(float value) {
        this.exhaustion = Math.max(0.0f, value);
    }

    // --- the server-known motion (the knockback residual) -------------------

    /** @return the server-known X motion (only server impulses write it). */
    public double motionX() {
        return motionX;
    }

    /** @return the server-known Y motion. */
    public double motionY() {
        return motionY;
    }

    /** @return the server-known Z motion. */
    public double motionZ() {
        return motionZ;
    }

    /** Replaces the server-known motion (the vanilla motion-set sites). */
    public void setMotion(double x, double y, double z) {
        this.motionX = x;
        this.motionY = y;
        this.motionZ = z;
    }

    // --- the movement-exhaustion ledger (the body-delta scratch) ------------

    /**
     * Captures this tick's body snapshot for the next tick's delta; the
     * first call only seeds (no displacement exists yet).
     */
    public void captureBodyDelta(double x, double y, double z, boolean onGround) {
        this.bodyPrevX = x;
        this.bodyPrevY = y;
        this.bodyPrevZ = z;
        this.bodyPrevOnGround = onGround;
        this.bodyPrevValid = true;
    }

    public double bodyPrevX() {
        return bodyPrevX;
    }

    public double bodyPrevY() {
        return bodyPrevY;
    }

    public double bodyPrevZ() {
        return bodyPrevZ;
    }

    public boolean bodyPrevOnGround() {
        return bodyPrevOnGround;
    }

    public boolean bodyPrevValid() {
        return bodyPrevValid;
    }

    public void advanceBodyTimer() {
        bodyTimer++;
    }

    public void resetBodyTimer() {
        bodyTimer = 0;
    }

    public int bodyTimer() {
        return bodyTimer;
    }

    /** Starts the eat timer (simulation context; validated by the engine). */
    public void beginEating() {
        this.eatingTicks = 0;
    }

    /** @return the incremented eat tick count (the engine compares to the duration). */
    public int advanceEating() {
        return ++eatingTicks;
    }

    public void cancelEating() {
        this.eatingTicks = -1;
    }

    // --- posture (sneak/sprint) ----------------------------------------------

    /** True while the player holds the sneak action (clients animate locally). */
    public boolean sneaking() {
        return sneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    /** True while the player holds the sprint action. */
    public boolean sprinting() {
        return sprinting;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }

    // --- movement guard accessors --------------------------------------------

    /** @return grace ticks left (a teleport or knockback is still in flight). */
    public int graceTicks() {
        return graceTicks;
    }

    /** Opens the grace window (engine-side teleports, knockback, joins). */
    public void setGraceTicks(int ticks) {
        this.graceTicks = Math.max(0, ticks);
    }

    /** Per-tick decay of the grace window. Tick-thread context. */
    public void tickGrace() {
        if (graceTicks > 0) {
            graceTicks--;
        }
    }

    /** @return whether flight is permitted (mode-granted or /fly). */
    public boolean allowedToFly() {
        return allowedToFly;
    }

    /** Sets flight rights (mode change, /fly). Tick-thread context. */
    public void setAllowedToFly(boolean allowedToFly) {
        this.allowedToFly = allowedToFly;
    }

    /** @return the client's reported flying state (the abilities packet bit). */
    public boolean flying() {
        return flying;
    }

    /** Records the client's flying flag (the owning channel loop orders it). */
    public void setFlying(boolean flying) {
        this.flying = flying;
    }

    /** Counts one more airborne non-descent tick (the hover tell). */
    public void noteHoverTick() {
        hoverTicks++;
    }

    /** @return the sustained airborne non-descent tick count. */
    public int hoverTicks() {
        return hoverTicks;
    }

    /** Clears the hover accumulator (landed, fell, or accepted flight). */
    public void clearHoverTicks() {
        hoverTicks = 0;
    }

    // --- bow charge -----------------------------------------------------------

    /** True between the use gesture and the release while a bow is drawn. */
    public boolean bowCharging() {
        return bowChargeTicks >= 0;
    }

    public int bowChargeTicks() {
        return bowChargeTicks;
    }

    /** Starts the bow draw (simulation context; validated by the engine). */
    public void beginBowCharge() {
        this.bowChargeTicks = 0;
    }

    /** @return the incremented draw tick count (the engine scales the launch). */
    public int advanceBowCharge() {
        return ++bowChargeTicks;
    }

    public void cancelBowCharge() {
        this.bowChargeTicks = -1;
    }

    /**
     * Fall tracking from movement proposals (the owning channel loop provides
     * ordering). Falling accumulates distance; upward motion does not add;
     * landing resets. The tick thread reads and clears the accumulated
     * distance when it applies landing damage.
     */
    public void noteFall(double previousY, double newY, boolean landed) {
        if (landed) {
            if (newY < previousY) {
                fallDistance += (float) (previousY - newY);
            }
        } else if (newY < previousY) {
            fallDistance += (float) (previousY - newY);
        }
    }

    /** @return the accumulated fall distance without consuming it (tick-thread landing). */
    public float fallDistance() {
        return fallDistance;
    }

    /** @return the accumulated fall distance, clearing it (tick-thread landing). */
    public float consumeFallDistance() {
        float distance = fallDistance;
        fallDistance = 0;
        return distance;
    }

    /** Clears the accumulated fall distance without consuming it (teleports). */
    public void resetFallDistance() {
        fallDistance = 0;
    }

    // The bed spawn (the sleeping slice). In-memory for now: the persisted
    // form rides a ZPD v6 bump with the spawn-persistence slice.
    private volatile Position bedSpawn;

    /** @return the bed spawn this body slept at, or null for the world spawn. */
    public Position bedSpawn() {
        return bedSpawn;
    }

    /** Sets the bed spawn (the sleeping flow). Tick-thread context. */
    public void setBedSpawn(Position at) {
        this.bedSpawn = at;
    }

    // --- state transitions (engine-owned; only EngineServer may call these) ---

    public void authenticate() {
        transition(PlayerState.CONNECTING, PlayerState.AUTHENTICATING);
    }

    public void beginJoin(World world, Position spawn) {
        transition(PlayerState.AUTHENTICATING, PlayerState.JOINING);
        this.world = Objects.requireNonNull(world, "world");
        this.position = Objects.requireNonNull(spawn, "spawn");
    }

    public void markPlaying() {
        transition(PlayerState.JOINING, PlayerState.PLAYING);
    }

    public void markDisconnecting() {
        if (state == PlayerState.PLAYING || state == PlayerState.JOINING) {
            transition(state, PlayerState.DISCONNECTING);
        }
    }

    public void markDisconnected() {
        if (state == PlayerState.DISCONNECTING || state == PlayerState.PLAYING) {
            transition(state, PlayerState.DISCONNECTED);
        } else {
            // Force-terminal state for abnormal paths (rejected during login).
            state = PlayerState.DISCONNECTED;
        }
    }

    /**
     * Applies a validated movement proposal. Called by the engine when a proposal
     * passes validation; the owning channel loop provides ordering. Fall
     * distance accumulates from airborne descent (the tick thread consumes it
     * on landing).
     */
    public void applyMovement(Position position, Rotation rotation, boolean onGround) {
        if (!position.isFinite() || !rotation.isFinite()) {
            throw new IllegalArgumentException("Movement proposal contains non-finite values");
        }
        if (!this.onGround) {
            noteFall(this.position.y(), position.y(), onGround);
        }
        this.position = position;
        this.rotation = rotation;
        this.onGround = onGround;
    }

    private void transition(PlayerState from, PlayerState to) {
        if (state != from) {
            throw new IllegalStateException(
                    "Player " + name + " cannot transition " + from + " -> " + to + " from " + state);
        }
        state = to;
    }
}
