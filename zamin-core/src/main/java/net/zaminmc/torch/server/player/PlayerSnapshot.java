package net.zaminmc.torch.server.player;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;

import java.util.List;
import java.util.UUID;

/**
 * The persistable state of one player, as a pure value: everything needed to
 * put a returning player back where survival left them (position, look,
 * inventory, selected hotbar slot, body). Decoupled from the live
 * {@link PlayerSession} so snapshots can be taken without touching session
 * internals.
 *
 * @param uuid       the offline-mode identity (file key)
 * @param name       display name at save time (diagnostics; identity is the uuid)
 * @param position   the saved feet position
 * @param rotation   the saved look angles
 * @param heldSlot   the selected hotbar slot 0-8
 * @param slots      the non-empty inventory contents; each entry names its own
 *                   engine slot index (0-8 hotbar, 9-35 main)
 * @param health     the saved health (0-20)
 * @param food       the saved hunger (0-20)
 * @param saturation the saved saturation (>= 0)
 * @param gamemodeId the saved game mode legacy id (-1 = unspecified, ZPD v4+)
 */
public record PlayerSnapshot(UUID uuid, String name, Position position, Rotation rotation,
                             int heldSlot, List<SlotStack> slots,
                             float health, int food, float saturation,
                             int gamemodeId) {

    /** The pre-gamemode shape (ZPD v1-v3 readers and engine callers without a mode). */
    public PlayerSnapshot(UUID uuid, String name, Position position, Rotation rotation,
                          int heldSlot, List<SlotStack> slots,
                          float health, int food, float saturation) {
        this(uuid, name, position, rotation, heldSlot, slots, health, food, saturation, -1);
    }

    /** Historical defaults for snapshots saved before the body existed (ZPD v1). */
    public static final float LEGACY_HEALTH = PlayerSession.MAX_HEALTH;
    public static final int LEGACY_FOOD = PlayerSession.MAX_FOOD;
    public static final float LEGACY_SATURATION = PlayerSession.DEFAULT_SATURATION;

    /**
     * One non-empty saved slot: engine slot index and its stack value,
     * including the optional custom display name (ZPD v3; null in older files).
     */
    public record SlotStack(int slot, Identifier item, int count, int damage, String displayName) {
        public SlotStack {
            if (slot < 0 || slot > 39) {
                // 0-35 the inventory row; 36-39 the ZPD v5 armor namespace.
                throw new IllegalArgumentException("Slot out of range: " + slot);
            }
            if (count < 1) {
                throw new IllegalArgumentException("Count must be positive: " + count);
            }
            if (damage < 0) {
                throw new IllegalArgumentException("Damage must not be negative: " + damage);
            }
        }

        /** The pre-name shape (ZPD v1/v2 readers, engine callers without a name). */
        public SlotStack(int slot, Identifier item, int count, int damage) {
            this(slot, item, count, damage, null);
        }
    }
}
