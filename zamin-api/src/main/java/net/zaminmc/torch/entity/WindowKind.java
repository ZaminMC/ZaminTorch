package net.zaminmc.torch.entity;

/**
 * The public vocabulary of open window kinds (the §27 window family's
 * read surface).
 *
 * <p>Mirrors the engine's own container tracking without exposing the
 * engine type: the kinds name what the player currently has open, so a
 * plugin can answer "does this player hold my chest's window open?"
 * without depending on internal classes.</p>
 */
public enum WindowKind {
    /** No container window open (the player's own inventory window is always present on the wire and is not an open-container state). */
    NONE,
    /** The 3x3 crafting table (the grid state lives in the session). */
    CRAFTING_TABLE,
    /** The furnace (the slot state lives in the world at the window's position). */
    FURNACE,
    /** The chest (the slot state lives in the world at the window's position). */
    CHEST,
    /** The dispenser/dropper (the slot state lives in the world at the window's position). */
    DISPENSER,
    /** The brewing stand (three bottles + the ingredient, the brew timer rides the property stream). */
    BREWING_STAND,
    /** The horse inventory (the saddle + armor slots live on the mount at the window's mount id). */
    HORSE,
    /** The chest minecart's chest menu (the slot state lives on the cart at the window's mount id). */
    CHEST_MINECART,
    /** The hopper minecart's hopper menu (the slot state lives on the cart at the window's mount id). */
    HOPPER_MINECART,
    /** The hopper (the slot state lives in the world at the window's position). */
    HOPPER,
    /** The ender chest (the GUI over the player's OWN private 27-slot grid; the block at the position is the door, not the storage). */
    ENDER_CHEST,
    /** The beacon (the payment slot + the three properties). */
    BEACON,
    /** The villager trading window (the offers ride the plugin channel). */
    VILLAGER,
    /** The enchanting table (two transient slots living in the session's menu). */
    ENCHANTING_TABLE,
    /** The anvil (the repair + rename GUI: two transient inputs and the computed output living in the session's menu). */
    ANVIL
}
