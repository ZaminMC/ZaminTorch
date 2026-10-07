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
        FURNACE
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
     * passes validation; the owning channel loop provides ordering.
     */
    public void applyMovement(Position position, Rotation rotation, boolean onGround) {
        if (!position.isFinite() || !rotation.isFinite()) {
            throw new IllegalArgumentException("Movement proposal contains non-finite values");
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
