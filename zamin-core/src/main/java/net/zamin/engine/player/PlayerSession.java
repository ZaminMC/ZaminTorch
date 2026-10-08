package net.zamin.engine.player;

import net.zamin.api.PlayerState;
import net.zamin.api.Position;
import net.zamin.api.Rotation;
import net.zamin.api.World;
import net.zamin.engine.net.ClientLink;

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
public final class PlayerSession implements net.zamin.api.Player {

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
    private volatile boolean onGround = true;
    /** The open container window id (crafting table, furnace), or -1 when none. Tick-thread written, volatile-read by the adapter's sync. */
    private volatile int containerWindowId = -1;
    /** Which container the open window is (routes wire layout + click semantics). */
    private volatile ContainerKind containerKind = ContainerKind.NONE;
    /** The block the open container belongs to (furnace state lookup); null otherwise. */
    private volatile net.zamin.api.BlockPosition containerPosition;

    /** The kinds of container windows a session can hold open. */
    public enum ContainerKind {
        NONE,
        /** The 3x3 crafting table (10-slot GUI, grid state lives in the session). */
        CRAFTING_TABLE,
        /** The furnace (3-slot GUI, slot state lives in the world at containerPosition). */
        FURNACE,
        /** The chest (27-slot GUI, slot state lives in the world at containerPosition). */
        CHEST
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
    // Eating: the server runs its own 32-tick timer, started by the use-item
    // gesture; the client's release (dig status 5) cancels when unfinished.
    private int eatingTicks = -1;

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

    public ClientLink link() {
        return link;
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
    public net.zamin.api.BlockPosition openContainerPosition() {
        return containerPosition;
    }

    /** Marks the container window as open (engine-assigned id &gt; 0). */
    public void openContainerWindow(int windowId, ContainerKind kind,
                                    net.zamin.api.BlockPosition position) {
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
    }

    /** Marks the container window as closed (no container open). */
    public void closeContainerWindow() {
        this.containerWindowId = -1;
        this.containerKind = ContainerKind.NONE;
        this.containerPosition = null;
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
        resetHurtInvulnerability();
    }

    /** Exhaustion accrual (regen hearts; more sources arrive with combat). */
    public void addExhaustion(float amount) {
        exhaustion += amount;
    }

    /** @return the pending exhaustion (tick consumes it through the food rules). */
    public float exhaustion() {
        return exhaustion;
    }

    public void setExhaustion(float value) {
        this.exhaustion = Math.max(0.0f, value);
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

    /** @return the accumulated fall distance, clearing it (tick-thread landing). */
    public float consumeFallDistance() {
        float distance = fallDistance;
        fallDistance = 0;
        return distance;
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
