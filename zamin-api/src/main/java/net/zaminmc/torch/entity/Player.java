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
}
