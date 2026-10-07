package net.zamin.engine.player;

import net.zamin.api.Identifier;
import net.zamin.api.Position;
import net.zamin.api.Rotation;

import java.util.List;
import java.util.UUID;

/**
 * The persistable state of one player, as a pure value: everything needed to
 * put a returning player back where survival left them (position, look,
 * inventory, selected hotbar slot). Decoupled from the live {@link PlayerSession}
 * so snapshots can be taken without touching session internals.
 *
 * @param uuid     the offline-mode identity (file key)
 * @param name     display name at save time (diagnostics; identity is the uuid)
 * @param position the saved feet position
 * @param rotation the saved look angles
 * @param heldSlot the selected hotbar slot 0-8
 * @param slots    the non-empty inventory contents; each entry names its own
 *                 engine slot index (0-8 hotbar, 9-35 main)
 */
public record PlayerSnapshot(UUID uuid, String name, Position position, Rotation rotation,
                             int heldSlot, List<SlotStack> slots) {

    /** One non-empty saved slot: engine slot index and its stack value. */
    public record SlotStack(int slot, Identifier item, int count, int damage) {
        public SlotStack {
            if (slot < 0 || slot > 35) {
                throw new IllegalArgumentException("Slot out of range: " + slot);
            }
            if (count < 1) {
                throw new IllegalArgumentException("Count must be positive: " + count);
            }
            if (damage < 0) {
                throw new IllegalArgumentException("Damage must not be negative: " + damage);
            }
        }
    }
}
