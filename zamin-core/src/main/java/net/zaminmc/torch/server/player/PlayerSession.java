package net.zaminmc.torch.server.player;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.Player;

import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.entity.WindowKind;
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
    /**
     * The player's private ender chest (Slice 29a — the reference's
     * EnderChestInventory on the player, not the block): a 27-slot chest
     * grid owned by the session. Every ender chest block in the world opens
     * THIS grid; the contents travel with the player (ZPD v8), never with
     * any placed block.
     */
    private final net.zaminmc.torch.server.chest.ChestBlockEntity enderChest =
            new net.zaminmc.torch.server.chest.ChestBlockEntity();
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
    /**
     * The minecart travel window (VIII6 — PlayerEntity.minecartTravelStartPos
     * line 107): the block cell where the CURRENT cart ride began, null on
     * foot (line 280's reset) — the onARail displacement anchor. Tick-lane
     * owned like the ride ids' writers.
     */
    private net.zaminmc.torch.block.BlockPosition minecartTravelStart;
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
    /** The session's statistics (the stats slice — the tick-lane counters). */
    private final net.zaminmc.torch.server.stat.PlayerStats stats =
            new net.zaminmc.torch.server.stat.PlayerStats();
    /** The session's command-stat bindings (the reference's Entity field). */
    private final net.zaminmc.torch.server.command.EngineCommandResults commandResults =
            new net.zaminmc.torch.server.command.EngineCommandResults();

    /** The kinds of container windows a session can hold open. */
    public enum ContainerKind {
        NONE,
        /** The 3x3 crafting table (10-slot GUI, grid state lives in the session). */
        CRAFTING_TABLE,
        /** The furnace (3-slot GUI, slot state lives in the world at containerPosition). */
        FURNACE,
        /** The chest (27-slot GUI, slot state lives in the world at containerPosition). */
        CHEST,
        /**
         * The dispenser/dropper (Slice 11a: 9-slot GUI, slot state lives in
         * the world at containerPosition — the same ownership as the chest).
         */
        DISPENSER,
        /**
         * The brewing stand (Slice 11e: 4-slot GUI — three bottles + the
         * ingredient — with the brew timer riding property 0; slot state
         * lives in the world at containerPosition).
         */
        BREWING_STAND,
        /** The horse inventory (saddle + armor slots live on the mount at containerPosition's mount id). */
        HORSE,
        /**
         * The chest minecart's chest menu (Slice 21a — the reference's
         * InventoryMinecartEntity.interact opens the chest menu over the
         * cart): 27 GUI slots, the slot state lives on the vehicle at
         * containerMountId's cart id.
         */
        CHEST_MINECART,
        /**
         * The hopper minecart's hopper menu (Slice 66 — the reference's
         * HopperMinecartEntity.interact lines 54-60: openChestMenu over
         * the cart): 5 GUI slots, the slot state lives on the vehicle at
         * containerMountId's cart id.
         */
        HOPPER_MINECART,
        /**
         * The hopper (the reference's HopperMenu — the 5-slot GUI over the
         * block entity at containerPosition; the same ownership as the
         * chest).
         */
        HOPPER,
        /**
         * The ender chest (Slice 29a — the reference's EnderChestBlock.use:
         * the chest GUI over the PLAYER'S OWN 27-slot ender inventory; the
         * block at containerPosition is the door, not the storage).
         */
        ENDER_CHEST,
        /**
         * The beacon (Slice 29b — the reference's BeaconMenu: the 1-slot
         * payment GUI with the three properties riding the window, the
         * MC|Beacon confirm applying the choices).
         */
        BEACON,
        /** The villager trading window (offers ride the MC|TrList plugin message). */
        VILLAGER,
        /** The enchanting table (2 transient slots living in the session's menu, seed + costs included). */
        ENCHANTING_TABLE,
        /**
         * The anvil (the reference AnvilMenu: the 3-slot repair + rename GUI —
         * two transient inputs and the computed output projection living in
         * the session's menu; the repair cost rides window property 0, the
         * rename rides the MC|ItemName channel).
         */
        ANVIL
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
    private int peacefulTick; // the peaceful regen family's own clock (19e)
    private float fallDistance;
    private volatile boolean dead;
    // The End exit's own state (Slice 64 — ServerPlayerEntity.leavingTheEnd
    // line 128): true from the exit fountain's GameEvent-4 credits until the
    // client's respawn confirmation. While set the body is "removed from
    // the world" — no body simulation, no movement proposals, invisible to
    // dimension peers — but the connection and the session stay alive.
    private volatile boolean leavingTheEnd;
    // The /reply target: the name of the last player who /tell'd this one.
    // A name (not a uuid) because the reply may outlive a relogin window.
    private volatile String lastMessagedBy;
    // Eating: the server runs its own 32-tick timer, started by the use-item
    // gesture; the client's release (dig status 5) cancels when unfinished.
    private int eatingTicks = -1;
    // Drinking (Slice 11b): the potion's own 32-tick timer (the reference's
    // getUseDuration 32, the DRINK action) — a separate clock from the eat
    // because the potion answers no hunger gate.
    private int drinkingTicks = -1;

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

    /**
     * The spectator's camera target (the reference ServerPlayerEntity's own
     * {@code camera} field, lines 124/916-923): the uuid of the followed
     * player while spectating, {@code null} while the camera answers self.
     * Tick-thread written (the spectate walk and its exits), volatile for
     * the wire's tracking gates.
     */
    private volatile UUID spectatorCamera;

    /** The operator level (0 = player, 4 = console-equivalent). */
    private volatile int opLevel;

    /**
     * THE TELEPORT WINDOW (ServerPlayNetworkHandler's {@code teleported}
     * flag, inverted): a server-ordered teleport is IN FLIGHT until the
     * client's movement lands at the anchor (the reference's own
     * {@code d3 < 0.25} squared band) — uses refuse through the window
     * (the use handler's own first consult, handlePlayerUse line 475:
     * {@code if (this.teleported && ...)}). Tick-thread written, volatile
     * for the use-walk read.
     */
    private volatile boolean teleportPending;
    /** The in-flight teleport's anchor (the acknowledgment band's target). */
    private volatile Position teleportAnchor;

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

    /**
     * The open anvil menu's state (two inputs + the output projection, the
     * repair cost, the sent rename) — the historical per-open
     * {@code AnvilMenu}. Non-null only while an ANVIL window is open; the
     * close paths drop its two input slots (the reference {@code close}
     * walk; the output is a server-only projection, never returned).
     */
    private volatile net.zaminmc.torch.server.enchantment.AnvilMenu anvilMenu;

    /** @return the open anvil menu, or null when no anvil window is open. */
    public net.zaminmc.torch.server.enchantment.AnvilMenu anvilMenu() {
        return anvilMenu;
    }

    /** Installs (or clears with null) the open anvil menu. Tick-thread context. */
    public void setAnvilMenu(net.zaminmc.torch.server.enchantment.AnvilMenu menu) {
        this.anvilMenu = menu;
    }

    /**
     * The reference {@code PlayerEntity.addXp(int)} on the engine's
     * authoritative points model: {@code levels} (negative to pay) move
     * the level, the progress into the level is kept, a level below 0
     * zeroes the whole bar (level, progress, points). No enchanting-seed
     * reroll — the anvil's payment is {@code addXp}, never the enchanting
     * table's own {@code applyEnchantmentCosts}. Tick-thread context.
     */
    public void addXpLevels(int levels) {
        int current = experienceLevel();
        int next = current + levels;
        if (next < 0) {
            totalXp = 0;
            return;
        }
        long into = totalXp - net.zaminmc.torch.server.experience.ExperienceMath.totalXpForLevel(current);
        totalXp = net.zaminmc.torch.server.experience.ExperienceMath.totalXpForLevel(next) + into;
    }

    /**
     * The player-entity random (the reference {@code getRandom()}) — the
     * anvil's 0.12 damage roll reads it exactly like the reference's
     * {@code player.getRandom().nextFloat()}. Tick-thread context.
     */
    public java.util.Random playerRandom() {
        return playerRandom;
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

    // ---------------------------------------------------------------
    // The lava family (Slice 93/D2 — Entity.tick's isInLava arm over the
    // player body): the wound cadence clock and the fall store's own
    // halving seam.
    // ---------------------------------------------------------------

    /** The lava wound's cadence counter (the reference's i-frame window). */
    private int lavaTimer;

    /** @return the incremented lava wound cadence counter. */
    public int advanceLavaTimer() {
        return ++lavaTimer;
    }

    /** Resets the lava wound cadence counter (out of the lava, resistant). */
    public void resetLavaTimer() {
        lavaTimer = 0;
    }

    /**
     * The lava's fall-store halving (Entity.tick line 339's
     * {@code fallDistance *= 0.5F} — every tick the body stands in the
     * lava): the store halves instead of the water's full zero, so a
     * body that fell INTO the lava still spends the halved remainder on
     * the landing at the bottom.
     */
    public void halveFallDistance() {
        fallDistance *= 0.5f;
    }

    public PlayerSession(UUID uuid, String name, ClientLink link) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = Objects.requireNonNull(name, "name");
        this.link = Objects.requireNonNull(link, "link");
    }

    public UUID uuid() {
        return uuid;
    }

    /** The session's command-stat bindings (the /stats surface). */
    public net.zaminmc.torch.server.command.EngineCommandResults commandResults() {
        return commandResults;
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

    /**
     * The smoothed round-trip latency in milliseconds (the tab list's own
     * column). The keep-alive handler reports each round trip; the smoothing
     * is the reference's own (ServerPlayNetworkHandler lines 910-914):
     * {@code ping = (ping * 3 + roundTrip) / 4}. Volatile: the writes come
     * from one connection's event loop, the reads ride the tab-list walks.
     */
    private volatile int ping;

    /** @return the smoothed latency in ms (0 until the first keep-alive lands). */
    public int ping() {
        return ping;
    }

    /**
     * One keep-alive round trip observation (the connection's event loop is
     * the only writer): the 3/4 + 1/4 smoothing, integer arithmetic exactly
     * like the reference.
     */
    public void updatePing(int roundTripMs) {
        ping = (ping * 3 + roundTripMs) / 4;
    }

    /**
     * The scoreboard's health driver mark (ServerPlayerEntity.tick lines
     * 313-317): the last health the health-objective walk computed against
     * (0.0 initially, so the first tick always publishes — the reference's
     * own completeHealth field).
     */
    private float scoreboardHealthMark;

    /** @return the last health the scoreboard's health walk published (0 = never). */
    public float scoreboardHealthMark() {
        return scoreboardHealthMark;
    }

    /** The health walk's own write (the tick lane only). */
    public void setScoreboardHealthMark(float health) {
        this.scoreboardHealthMark = health;
    }

    public net.zaminmc.torch.GameMode gamemode() {
        return gamemode;
    }

    /** Tick-thread write only; the wire reads are volatile-safe. */
    public void setGamemode(net.zaminmc.torch.GameMode mode) {
        this.gamemode = java.util.Objects.requireNonNull(mode, "mode");
    }

    /**
     * @return the uuid of the player this spectator's camera follows, or
     * {@code null} while the camera answers self (the reference
     * ServerPlayerEntity.getCamera's self-null convention). Any thread.
     */
    public UUID spectatorCamera() {
        return spectatorCamera;
    }

    /**
     * The spectate walk's own write (the reference's setCamera): {@code null}
     * restores the camera to self. Tick-thread write only.
     */
    public void setSpectatorCamera(UUID target) {
        this.spectatorCamera = target;
    }

    public int opLevel() {
        return opLevel;
    }

    /**
     * Advances the breath clock: underwater it drains, out of water it
     * refills. The Respiration roll rides the drain exactly like the
     * reference's {@code updateBreathUnderwater} (reference/1.8.8
     * entity/living/LivingEntity lines 330-333): a level above zero rolls
     * {@code nextInt(level + 1) > 0} per submerged tick — success keeps this
     * tick's air, failure (the 1/(level+1) arm) takes the decrement.
     *
     * @return true when a drowning damage tick is due (2 per second
     * after the air ran out, the historical EntityPlayer rhythm).
     */
    /** The breath store (the Air row's own model — syncedData 1's engine shape). */
    public int airTicks() {
        return airTicks;
    }

    public boolean advanceBreath(boolean underwater, int respirationLevel,
                                 java.util.Random random) {
        if (!underwater) {
            airTicks = 300;
            drownTimer = 0;
            return false;
        }
        if (airTicks > 0) {
            if (respirationLevel > 0
                    && random.nextInt(respirationLevel + 1) > 0) {
                return false; // the roll kept this tick's air
            }
            airTicks--;
            return false;
        }
        return ++drownTimer % 20 == 0;
    }

    public void setOpLevel(int level) {
        this.opLevel = Math.max(0, level);
    }

    /**
     * Arms the teleport window (the engine's teleport order — the
     * reference's {@code teleport()} setting {@code teleported = false}):
     * uses refuse until the client's movement lands at the anchor.
     * Tick-thread context.
     */
    public void markTeleportPending(Position anchor) {
        this.teleportAnchor = java.util.Objects.requireNonNull(anchor, "anchor");
        this.teleportPending = true;
    }

    /**
     * The teleport-window consult (the use handler's first gate,
     * handlePlayerUse line 475): a pending teleport refuses uses.
     */
    public boolean teleportPending() {
        return teleportPending;
    }

    /**
     * The in-flight teleport's anchor (the acknowledgment band's target,
     * or null when no teleport is in flight).
     */
    public Position teleportAnchor() {
        return teleportAnchor;
    }

    /**
     * Acknowledges the in-flight teleport (the movement handler's own
     * band, lines 203-211: a position packet within {@code d3 < 0.25}
     * squared of the anchor re-arms the flag). Tick-thread context.
     */
    public void acknowledgeTeleportIfAtAnchor(Position position) {
        if (teleportPending && teleportAnchor != null
                && position.distanceSquared(teleportAnchor) < 0.25) {
            teleportPending = false;
        }
    }

    // The portal stand clock (the reference's Entity portal tick,
    // reference/1.8.8 entity/Entity.java lines 282-313 + the collision arm
    // onPortalCollision lines 1428-1436 and the player overrides
    // PlayerEntity lines 300-317).
    private int portalTime;
    private int portalCooldown;
    private boolean portalOccupiedLastTick;

    /**
     * Advances the portal clock one tick — the reference walk, literally:
     * the occupancy accumulates {@code portalTime++} and the POST-increment
     * check {@code old >= max} crosses at the max (the player override — 0
     * for the invulnerable creative body, 80 otherwise; the crossing pins
     * the time, arms the 10-tick player cooldown and reports the teleport
     * arm); out of a portal the stand time decays by 4 (clamped), and the
     * cooldown decrements unconditionally. The cooldown's collision arm
     * re-arms it on the ENTRY EDGE only — the reference's
     * {@code onPortalCollision} is movement-driven, and a STANDING body
     * sends no movement packets, so a body standing inside a portal never
     * refreshes the cooldown there (the vanilla round trip: arrive, cool
     * down 10 ticks, stand 4 seconds, return).
     *
     * <p>The crossing re-fires while the body stays pinned in the portal —
     * the reference's own shape, because its {@code changeDimension} moves
     * the body out of the portal on the same tick; the teleport arm lands
     * with the dimension slice (8b) and ends the re-fire naturally.</p>
     *
     * @return true on a threshold-crossing tick (the teleport arm)
     */
    public boolean advancePortalClock(boolean touchingPortal, boolean creative) {
        boolean crossed = false;
        if (portalCooldown > 0) {
            portalCooldown--;
        }
        if (touchingPortal) {
            if (portalCooldown > 0 && !portalOccupiedLastTick) {
                // The entry edge re-arms (the reference's onPortalCollision
                // early return, movement-driven).
                portalCooldown = PORTAL_COOLDOWN_TICKS;
            }
            int max = creative ? 0 : PORTAL_STAND_TICKS;
            int old = portalTime++;
            if (old >= max) {
                portalTime = max;
                portalCooldown = PORTAL_COOLDOWN_TICKS;
                crossed = true;
            }
        } else {
            portalTime = Math.max(0, portalTime - 4);
        }
        portalOccupiedLastTick = touchingPortal;
        return crossed;
    }

    /** The reference's {@code getPortalCooldown} player override (PlayerEntity line 315). */
    private static final int PORTAL_COOLDOWN_TICKS = 10;
    /** The reference's {@code getMaxNetherPortalTime} survival arm (PlayerEntity line 300: invulnerable 0, else 80). */
    private static final int PORTAL_STAND_TICKS = 80;

    /** @return the portal stand time (the reference's portalTime). */
    public int portalTime() {
        return portalTime;
    }

    /**
     * The body's dimension (the reference's Entity.dimension: 0 the
     * overworld, -1 the nether). The changeDimension walk owns the swap —
     * the wire's Respawn packet and the chunk streaming read it.
     */
    private int dimension;

    public int dimension() {
        return dimension;
    }

    /** The dimension swap (the changeDimension walk's first arm). */
    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    // The portal entry memory (the reference's Entity.onPortalCollision
    // lines 1428-1451: lastPortalPos + the BlockPattern-derived
    // lastPortalOffset / lastPortalFacing the placement math consumes).
    private BlockPosition lastPortalPosition;
    private double portalOffsetAlongWidth;
    private double portalOffsetUp;
    private int portalFacingHorizontalId;

    /**
     * The reference's {@code onPortalCollision} memory arm: a NEW portal
     * cell derives the body's offset inside its frame (the inverse-lerp of
     * the body's position across the frame's width and height — the raw
     * reference ratios, unclamped) and the entered facing; the SAME cell
     * re-collides without re-deriving.
     *
     * @param portalCell the body's portal cell
     * @param match      the frame's shape match at that cell
     * @param x          the body's x
     * @param y          the body's y
     * @param z          the body's z
     */
    public void notePortalEntry(BlockPosition portalCell,
                                net.zaminmc.torch.server.world.PortalFrameBuilder.PortalShapeMatch match,
                                double x, double y, double z) {
        if (portalCell.equals(lastPortalPosition)) {
            return; // the reference's !pos.equals(lastPortalPos) guard
        }
        lastPortalPosition = portalCell;
        boolean axisX = match.forwardAxis() == net.zaminmc.torch.server.world.PortalFrameBuilder.Axis.X;
        // d0: the topLeft's coordinate on the width axis; d1: the body's
        // coordinate on the same axis, shifted one on the negative-facing
        // forward (the reference's clockwiseY NEGATIVE arm).
        double origin = axisX ? match.topLeftFront().z() : match.topLeftFront().x();
        double bodyCoord = axisX ? z : x;
        if (!match.forwardPositive()) {
            bodyCoord -= 1.0;
        }
        // The reference's inverseLerp: (a - b) / (c - b), raw — no clamp.
        portalOffsetAlongWidth = Math.abs(
                (bodyCoord - origin) / ((origin - match.width()) - origin));
        portalOffsetUp = ((y - 1.0) - match.topLeftFront().y())
                / ((match.topLeftFront().y() - match.height()) - match.topLeftFront().y());
        portalFacingHorizontalId = match.forwardHorizontalId();
    }

    /** @return the memory snapshot the PortalForcer's placement math consumes. */
    public net.zaminmc.torch.server.world.PortalForcer.Memory portalMemory() {
        net.zaminmc.torch.server.world.PortalForcer.Memory memory =
                new net.zaminmc.torch.server.world.PortalForcer.Memory();
        memory.offsetAlongWidth = portalOffsetAlongWidth;
        memory.offsetUp = portalOffsetUp;
        memory.lastFacingHorizontalId = portalFacingHorizontalId;
        memory.known = lastPortalPosition != null;
        return memory;
    }

    /** The arrival's body reset: the position, the facing, the fall debt cleared. */
    public void applyDimensionArrival(int newDimension, Position arrival, float yaw, float pitch) {
        this.dimension = newDimension;
        applyMovement(arrival, new Rotation(yaw, pitch), false);
        consumeFallDistance();
        // The body left the entered frame's memory behind — the next entry
        // re-derives against the arrival frame (the reference's entity copy
        // carries the memory across, but its placement re-derives at the
        // entry edge either way).
        lastPortalPosition = null;
    }

    /** @return the remaining portal cooldown ticks. */
    public int portalCooldown() {
        return portalCooldown;
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
     * The player's private ender chest grid (Slice 29a — the reference's
     * EnderChestInventory: 27 slots riding the player, opened by every
     * ender chest block in the world). Same ownership discipline as the
     * inventory: tick-thread mutations only (the engine's click routing and
     * the ZPD restore).
     */
    public net.zaminmc.torch.server.chest.ChestBlockEntity enderChest() {
        return enderChest;
    }

    /**
     * Restores the persisted ender chest contents (ZPD v8): a full 27-slot
     * snapshot in engine slot order, empty slots as {@link
     * net.zaminmc.torch.item.ItemStack#EMPTY}. Join-path only.
     */
    public void restoreEnderChest(java.util.List<net.zaminmc.torch.item.ItemStack> slots) {
        for (int i = 0; i < net.zaminmc.torch.server.chest.ChestBlockEntity.SLOT_COUNT; i++) {
            enderChest.setSlot(i, i < slots.size() ? slots.get(i)
                    : net.zaminmc.torch.item.ItemStack.EMPTY);
        }
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

    /**
     * Marks the container window as open (engine-assigned id &gt; 0). The
     * shared §27.7 container-open observation fires here — the ONE seam
     * every container family's open flows through (the block containers
     * and the entity forms): the listener (the engine's event-bus wiring,
     * set at construction) receives the committed form, never a veto.
     */
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
        if (containerOpenListener != null) {
            containerOpenListener.accept(new ContainerOpenDescriptor(
                    windowId, kind, position, -1));
        }
    }

    /**
     * The shared container-open observation's own listener seam (the
     * engine's event-bus wiring — null in the bare session, the
     * standalone systems' own convention). Tick-thread context.
     */
    public void setContainerOpenListener(java.util.function.Consumer<ContainerOpenDescriptor> listener) {
        this.containerOpenListener = listener;
    }

    /** The committed open's own descriptor (the listener's payload). */
    public record ContainerOpenDescriptor(int windowId, ContainerKind kind,
                                          net.zaminmc.torch.block.BlockPosition position,
                                          int mountId) {
    }

    /** The shared open observation's listener (null = unobserved). */
    private java.util.function.Consumer<ContainerOpenDescriptor> containerOpenListener;

    /** Opens the horse inventory window (the mount's engine id rides along). */
    public void openHorseWindow(int windowId, int mountEngineId) {
        openContainerWindowWithMount(windowId, ContainerKind.HORSE, null, mountEngineId);
    }

    /** Opens the chest minecart's chest menu (the cart's engine id rides along). */
    public void openChestMinecartWindow(int windowId, int cartEngineId) {
        openContainerWindowWithMount(windowId, ContainerKind.CHEST_MINECART, null, cartEngineId);
    }

    /** Opens the hopper minecart's hopper menu (the cart's engine id rides along). */
    public void openHopperMinecartWindow(int windowId, int cartEngineId) {
        openContainerWindowWithMount(windowId, ContainerKind.HOPPER_MINECART, null, cartEngineId);
    }

    /** Opens the villager trading window (the trader's engine id rides along). */
    public void openVillagerWindow(int windowId, int villagerEngineId) {
        openContainerWindowWithMount(windowId, ContainerKind.VILLAGER, null, villagerEngineId);
    }

    /** The entity containers' own open (the mount id lands BEFORE the shared observation fires). */
    private void openContainerWindowWithMount(int windowId, ContainerKind kind,
                                              net.zaminmc.torch.block.BlockPosition position,
                                              int mountId) {
        this.containerWindowId = windowId;
        this.containerKind = kind;
        this.containerPosition = position;
        this.containerMountId = mountId;
        this.selectedTrade = -1;
        if (containerOpenListener != null) {
            containerOpenListener.accept(new ContainerOpenDescriptor(
                    windowId, kind, position, mountId));
        }
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
    /**
     * The session's statistics (the stats slice): the counter map over the
     * {@link net.zaminmc.torch.server.stat.EngineStats} vocabulary. The
     * increments ride the tick lane (the owner's own context); snapshots
     * for the wire answer and the persistence walk come off the same lane.
     */
    public net.zaminmc.torch.server.stat.PlayerStats stats() {
        return stats;
    }

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

    // --- §27 window family's public surface (the live open-window view) --------
    // The engine session IS the handle (§27.5): the view reads the session's
    // own window state through the same accessors the engine uses; the
    // fields publish best-effort for the adapter's cross-thread syncs (the
    // body-state contract).

    @Override
    public OpenWindow openWindow() {
        return liveOpenWindow;
    }

    private final OpenWindow liveOpenWindow = new OpenWindow() {
        @Override
        public WindowKind kind() {
            return publicKindOf(containerKind);
        }

        @Override
        public int windowId() {
            return containerWindowId;
        }

        @Override
        public java.util.Optional<net.zaminmc.torch.block.BlockPosition> position() {
            return java.util.Optional.ofNullable(containerPosition);
        }

        @Override
        public int mountId() {
            return containerMountId;
        }
    };

    /** The engine kind -> the public vocabulary (the §27 window family's read surface). */
    private static WindowKind publicKindOf(ContainerKind kind) {
        return switch (kind) {
            case NONE -> WindowKind.NONE;
            case CRAFTING_TABLE -> WindowKind.CRAFTING_TABLE;
            case FURNACE -> WindowKind.FURNACE;
            case CHEST -> WindowKind.CHEST;
            case DISPENSER -> WindowKind.DISPENSER;
            case BREWING_STAND -> WindowKind.BREWING_STAND;
            case HORSE -> WindowKind.HORSE;
            case CHEST_MINECART -> WindowKind.CHEST_MINECART;
            case HOPPER_MINECART -> WindowKind.HOPPER_MINECART;
            case HOPPER -> WindowKind.HOPPER;
            case ENDER_CHEST -> WindowKind.ENDER_CHEST;
            case BEACON -> WindowKind.BEACON;
            case VILLAGER -> WindowKind.VILLAGER;
            case ENCHANTING_TABLE -> WindowKind.ENCHANTING_TABLE;
            case ANVIL -> WindowKind.ANVIL;
        };
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

    /** The cart-ride window's anchor (VIII6 — null when the ride has not begun). */
    public net.zaminmc.torch.block.BlockPosition minecartTravelStart() {
        return minecartTravelStart;
    }

    /** Sets the cart-ride window's anchor (the first move of a cart ride). */
    public void setMinecartTravelStart(net.zaminmc.torch.block.BlockPosition start) {
        this.minecartTravelStart = start;
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

    /** The End exit's credits state (ServerPlayerEntity.leavingTheEnd). */
    public boolean leavingTheEnd() {
        return leavingTheEnd;
    }

    /** The exit fountain's arm: the body left the world for the credits. */
    public void markLeavingTheEnd() {
        this.leavingTheEnd = true;
    }

    /** The credits' respawn confirmation clears the flag (the alive respawn). */
    public void clearLeavingTheEnd() {
        this.leavingTheEnd = false;
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

    // ---- the public Player surface's additions (§27.5: the session IS the
    // handle — every call resolves to the live authoritative state) ------

    /** Restores health up to the maximum (the API's heal). */
    public void heal(float amount) {
        if (amount <= 0 || dead) {
            return;
        }
        health = Math.min(MAX_HEALTH, health + amount);
    }

    @Override
    public long totalExperience() {
        return totalXp();
    }

    @Override
    public net.zaminmc.torch.item.ItemStack heldItem() {
        return inventory.held();
    }

    @Override
    public int heldSlot() {
        return inventory.heldSlot();
    }

    @Override
    public int countOf(net.zaminmc.torch.item.ItemType type) {
        return inventory.countOf(type);
    }

    @Override
    public net.zaminmc.torch.item.ItemStack give(net.zaminmc.torch.item.ItemStack stack) {
        java.util.Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return stack;
        }
        net.zaminmc.torch.item.ItemStack leftover = inventory.pickUp(stack);
        var hook = inventorySyncHook;
        if (hook != null) {
            hook.accept(this); // the wire re-sync, the engine pickup path's own rule
        }
        return leftover;
    }

    // --- titles + chat (Slice 90 — Slice 87's wire over the §27 surface) ------

    @Override
    public void sendTitle(String titleText, String subtitleText,
                          int fadeInTicks, int stayTicks, int fadeOutTicks) {
        // The reference's own client expectation: Times first (0x45 arm 2),
        // then the title (arm 0), then the subtitle (arm 1) — blank texts
        // skip their own arm, all-zero counts skip the Times arm.
        if (fadeInTicks > 0 || stayTicks > 0 || fadeOutTicks > 0) {
            link.titleTimes(fadeInTicks, stayTicks, fadeOutTicks);
        }
        if (titleText != null && !titleText.isEmpty()) {
            link.titleTitle(plainComponentJson(titleText));
        }
        if (subtitleText != null && !subtitleText.isEmpty()) {
            link.titleSubtitle(plainComponentJson(subtitleText));
        }
    }

    @Override
    public void clearTitle() {
        link.titleClear();
    }

    @Override
    public void sendChatLine(String message) {
        link.sendChatComponent(plainComponentJson(message), 0);
    }

    @Override
    public void sendSystemLine(String message) {
        link.sendChatComponent(plainComponentJson(message), 1);
    }

    @Override
    public void sendChatComponent(String json) {
        link.sendChatComponent(json, 0);
    }

    @Override
    public void sendActionBar(String message) {
        link.sendChatComponent(plainComponentJson(message), 2);
    }

    /** A plain text as the serialized component ({"text": ...}). */
    private static String plainComponentJson(String text) {
        return net.zaminmc.torch.server.text.TextJson.toJson(
                new net.zaminmc.torch.server.text.LiteralText(text == null ? "" : text));
    }

    // --- the slot-level inventory view (the container/inventory wave) ------

    // PlayerSession.inventory() (the engine accessor) doubles as the public
    // §27.3 slot-level view through a covariant return: PlayerInventory
    // implements Player.Inventory — the engine object IS the handle (§27.5).

    /**
     * The engine's inventory wire-sync hook, installed at boot: the API's
     * give() and the slot-write arms must re-sync the client exactly like
     * the engine's own inventory paths. Null until wired (the tests' bare
     * sessions).
     */
    private volatile java.util.function.Consumer<PlayerSession> inventorySyncHook;

    /** Called once by the server at wiring time. */
    public void setInventorySyncHook(java.util.function.Consumer<PlayerSession> hook) {
        this.inventorySyncHook = hook;
        // The slot-write arms ride the same fan-out (setSlot/setArmor fire
        // it after every accepted write — the pickup path's own rule).
        this.inventory.setChangeCallback(hook == null ? null : () -> hook.accept(this));
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

    /**
     * Exhaustion accrual (the vanilla cap: 40.0) behind the reference's own
     * gate — the invulnerable bodies never accrue ({@code PlayerEntity
     * .addFatigue lines 1475-1481}: {@code if (!this.abilities.invulnerable)
     * hungerManager.addExhaustion(amount)}). Creative and spectator are the
     * engine's invulnerable modes (the abilities.invulnerable pair), so the
     * wrapper itself refuses them — the sprint/jump/attack/break/damage
     * sources stay ungated call sites exactly like the reference's own.
     */
    public void addExhaustion(float amount) {
        net.zaminmc.torch.GameMode mode = this.gamemode;
        if (mode == net.zaminmc.torch.GameMode.CREATIVE
                || mode == net.zaminmc.torch.GameMode.SPECTATOR) {
            return; // the invulnerable accrual refusal (addFatigue's own arm)
        }
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

    /** Advances the peaceful clock (Slice 19e — the 20/10 regen cadences). */
    public void advancePeacefulTick() {
        peacefulTick++;
    }

    /** The peaceful clock's tick count (the mod-20 heal / mod-10 food). */
    public int peacefulTick() {
        return peacefulTick;
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

    // --- drinking (Slice 11b — the potion's 32-tick use) -------------------

    /** @return whether the drink timer runs. */
    public boolean drinking() {
        return drinkingTicks >= 0;
    }

    /** Starts the drink timer (simulation context; validated by the engine). */
    public void beginDrinking() {
        this.drinkingTicks = 0;
    }

    /** @return the incremented drink tick count. */
    public int advanceDrinking() {
        return ++drinkingTicks;
    }

    public void cancelDrinking() {
        this.drinkingTicks = -1;
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

    // --- sleep ---------------------------------------------------------------

    /**
     * True while the body lies in a bed (the reference's {@code sleeping}):
     * the multiplayer sleep walk counts it, and the wire renders the sleep
     * pose (Use Bed 0x0A) while it holds.
     */
    private volatile boolean sleeping;

    /** The bed HEAD cell the body sleeps in (the reference's {@code sleepingPos}). */
    private volatile BlockPosition sleepingPos;

    /** True while the body lies in a bed. */
    public boolean sleeping() {
        return sleeping;
    }

    /** @return the bed head cell the body sleeps in (null when awake). */
    public BlockPosition sleepingPos() {
        return sleepingPos;
    }

    /**
     * The bed entry (the reference's trySleep tail: sleeping = true,
     * sleepingPos = pos, sleepingTime = 0 — the engine keeps no timer; the
     * walk's identity clause answers the all-sleepers question the moment
     * the last body lies down). Tick-thread context.
     */
    public void enterBed(BlockPosition bedHead) {
        this.sleeping = true;
        this.sleepingPos = bedHead;
    }

    /**
     * The bed departure (the reference's wakeUp: sleeping = false — the
     * sleepingPos drops with it, the spawn already set at the entry).
     * Tick-thread context.
     */
    public void leaveBed() {
        this.sleeping = false;
        this.sleepingPos = null;
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

    /** The test seam: freezes the per-tick charge advance so a load-slowed
     * suite cannot slip extra ticks into a pinned draw (the same shape as
     * setVelocityForTest). The engine's tick walk reads it. */
    private volatile boolean bowChargeFrozenForTest;

    public void freezeBowChargeForTest() {
        this.bowChargeFrozenForTest = true;
    }

    public void unfreezeBowChargeForTest() {
        this.bowChargeFrozenForTest = false;
    }

    /** @return whether the tick walk must skip the charge advance. */
    public boolean bowChargeFrozenForTest() {
        return bowChargeFrozenForTest;
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
