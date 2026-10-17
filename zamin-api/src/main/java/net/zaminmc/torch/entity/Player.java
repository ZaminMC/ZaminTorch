package net.zaminmc.torch.entity;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.World;

import java.util.UUID;

/**
 * The authoritative gameplay identity of a connected player.
 *
 * <p>A player is not a connection: the connection belongs to the protocol layer,
 * this object belongs to the engine and outlives transient wire state.</p>
 *
 * <p>Threading contract (architecture §27.6): the read surface is
 * live-authoritative state — consistent reads and every mutation belong on
 * the player's simulation context (the documented owner path is
 * {@link World#execute}); mutations from any other context are ownership
 * violations. Off-owner observation belongs to snapshots/events.</p>
 */
public interface Player {

    UUID uuid();

    String name();

    PlayerState state();

    World world();

    Position position();

    Rotation rotation();

    boolean onGround();

    // --- body ------------------------------------------------------------------

    /** Current health (0..20). Simulation context for consistent reads. */
    float health();

    /** Current food level (0..20). */
    int food();

    /** Current saturation (0..food). */
    float saturation();

    /** Whether the player is dead. */
    boolean dead();

    /** Restores health up to the maximum. Simulation context. */
    void heal(float amount);

    /** Applies direct damage (the raw amount, no source walk). */
    void hurt(float amount);

    /** Adds exhaustion (the hunger engine's input; the vanilla scale). */
    void addExhaustion(float amount);

    // --- progression ------------------------------------------------------------

    /** The visible XP level. */
    int experienceLevel();

    /** Lifetime accumulated experience (the level cost ladder's input). */
    long totalExperience();

    /** Grants (or, when negative, removes) experience points. */
    void addExperience(long amount);

    // --- mode and fire -----------------------------------------------------------

    /** The current game mode. */
    GameMode gamemode();

    /** Sets the game mode (the authoritative switch, wire-synced). */
    void setGamemode(GameMode mode);

    /** Whether the player is on fire. */
    boolean burning();

    /** Ignites for the given ticks (the vanilla burn windows). */
    void ignite(int ticks);

    /** Extinguishes immediately. */
    void extinguish();

    // --- inventory ---------------------------------------------------------------

    /** The stack in the held hand (never null; empty when the hand is empty). */
    ItemStack heldItem();

    /** The held hotbar slot index (0..8). */
    int heldSlot();

    /** Total units of the item type across the inventory. */
    int countOf(ItemType type);

    /**
     * Gives a stack into the inventory (the vanilla pickup rules: stacks
     * merge first, empty slots fill next). Simulation context; the wire
     * re-syncs.
     *
     * @return the remainder that did not fit (empty when everything landed)
     */
    ItemStack give(ItemStack stack);

    // --- titles + chat (Slice 90 — Slice 87's title/chat wire over the §27 surface) ---

    /**
     * Shows a title pair for the given tick counts (the reference's own
     * wire order: Times, then the title, then the subtitle —
     * TitlesS2CPacket 0x45). Blank texts skip their own arm; zero counts
     * skip the Times arm. Safe from any thread (the link's own contract).
     */
    void sendTitle(String titleText, String subtitleText,
                   int fadeInTicks, int stayTicks, int fadeOutTicks);

    /**
     * Clears the showing title (the CLEAR arm — a blank pair with the
     * fade-in skipped). Safe from any thread.
     */
    void clearTitle();

    /**
     * A plain chat line at the client's chat box (Chat 0x02, position 0 —
     * the reference's sendMessage). Safe from any thread.
     */
    void sendChatLine(String message);

    /**
     * A system line (Chat 0x02, position 1 — no chat-log echo, the
     * reference's sendSystemMessage). Safe from any thread.
     */
    void sendSystemLine(String message);

    /**
     * A raw chat component (Chat 0x02 carrying the serialized component at
     * position 0 — hover/click events intact). Safe from any thread.
     */
    void sendChatComponent(String json);

    /**
     * An action-bar line (Chat 0x02, position 2 — the text above the
     * hotbar, no chat-log echo). Safe from any thread.
     */
    void sendActionBar(String message);

    // --- slot-level inventory (the container/inventory wave) ---------------

    /**
     * The live slot-level view over the player's inventory (§27.3's
     * player-inventory family): the 36-slot grid (0-8 the hotbar, 9-35
     * the main inventory) and the four armor slots (head, chest, legs,
     * feet). The engine's own inventory IS the handle (§27.5).
     *
     * <p>Reads are owner-context consistent; the write verbs run on the
     * world's authoritative simulation context (§27.6 — the documented
     * owner path is {@code World.execute}) and re-sync the wire exactly
     * like the engine's own inventory paths.</p>
     */
    Inventory inventory();

    /** The player's live inventory view (the 36 slots + the armor row). */
    interface Inventory {

        /** @return the storage slot count (the historical 36: hotbar + main). */
        int slotCount();

        /** @return one storage slot's stack copy (empty when out of range). */
        ItemStack slot(int index);

        /** @return a copy of the 36 storage slots (empty cells as empty stacks). */
        ItemStack[] slots();

        /**
         * Writes one storage slot (an empty stack clears it). Owner
         * context; the wire re-syncs.
         */
        void setSlot(int index, ItemStack stack);

        /** @return the held hotbar slot index (0..8). */
        int heldSlot();

        /**
         * Selects the held hotbar slot (0..8; cancels an in-progress eat,
         * the historical rule). Owner context; the wire re-syncs.
         */
        void selectHotbarSlot(int slot);

        /** @return the armor slot count (the historical 4). */
        int armorSlotCount();

        /** @return one armor slot's stack copy (0 head, 1 chest, 2 legs, 3 feet; empty when out of range). */
        ItemStack armorAt(int armorSlot);

        /** @return a copy of the four armor stacks in slot order (empty cells as empty stacks). */
        ItemStack[] armorStacks();

        /**
         * Writes one armor slot, enforcing the vanilla kind rule: only
         * armor of the matching slot may enter; an empty stack clears.
         * Owner context; the wire re-syncs.
         *
         * @return whether the write was accepted (a wrong-kind stack is refused)
         */
        boolean setArmor(int armorSlot, ItemStack stack);
    }
}
