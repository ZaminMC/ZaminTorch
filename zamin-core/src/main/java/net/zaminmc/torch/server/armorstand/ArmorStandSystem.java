package net.zaminmc.torch.server.armorstand;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.Armor;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The armor stand system (Slice 28c — the reference port of
 * entity/living/ArmorStandEntity + item/ArmorStandItem): the registry,
 * the placement (ArmorStandItem.useOn's cell gates + the 45-degree yaw
 * snap + the pose roll), the equipment swap ladder (interactAt lines
 * 273-342 + swapItem lines 344-365: the armor item's own slot map, the
 * empty hand's height bands, the creative copy, the one-unit carve),
 * the melee break ladder (takeDamage lines 368-433: the first hit's
 * five-tick stamp, the double-hit break with the stand item + the
 * equipment drops, the creative instant remove), the fire burn-down
 * (the ignite + the 4.0 on-fire toll to the 0.5 floor), and the ZAD
 * persistence.
 *
 * <p>The stands stay where they land (the engine's static-entity model
 * — the reference's gravity walk is ledgered); the equipment, the pose
 * and the flags ride the wire through the listener family.</p>
 */
public final class ArmorStandSystem {

    /** The double-hit window (takeDamage line 423: > 5 ticks apart). */
    static final long DOUBLE_HIT_WINDOW = 5L;

    /** Events the protocol adapter translates into the wire form. */
    public interface Listener {
        /** A stand spawned (the Spawn Object 78 + the metadata + the equipment). */
        default void onStandSpawned(ArmorStand stand) { }

        /** A stand removed (the Destroy Entities). */
        default void onStandRemoved(ArmorStand stand) { }

        /** A stand's equipment slot changed (the Entity Equipment packet). */
        default void onStandEquipmentChanged(ArmorStand stand, int slot) { }
    }

    /** The engine surface the system reads (the world + the drops). */
    public interface Surface {
        /** Whether a block can be replaced (the tall grass/flora family). */
        boolean replaceableAt(BlockPosition pos);

        /** Whether the cell holds air. */
        boolean airAt(BlockPosition pos);

        /** An item drop at a position. */
        void dropItem(Position at, ItemStack stack);

        /** The engine's entity-occupancy probe (the placement's box gate). */
        boolean entityOccupies(double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ);

        /** The engine's tick clock (the double-hit window's stamp). */
        long now();
    }

    private final Surface surface;
    private final List<Listener> listeners = new ArrayList<>();
    private final Map<Integer, ArmorStand> stands = new java.util.LinkedHashMap<>();
    private final int firstId;
    private int nextId;

    public ArmorStandSystem(Surface surface, int firstId) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.firstId = firstId;
        this.nextId = firstId;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /** The id band the stands live in. */
    public int firstId() {
        return firstId;
    }

    /** Every live stand (the join view's walk). */
    public List<ArmorStand> allStands() {
        return new ArrayList<>(stands.values());
    }

    /** The stand with the engine entity id, or null. */
    public ArmorStand byId(int entityId) {
        return stands.get(entityId);
    }

    // ------------------------------------------------------------------
    // The placement (ArmorStandItem.useOn lines 22-68)
    // ------------------------------------------------------------------

    /**
     * The armor stand item's placement: the DOWN face refuses, a
     * replaceable clicked cell stands there (the face offset otherwise),
     * the cell AND the cell above must be free, no entity may occupy the
     * 1x2x1 box, the yaw snaps to 45-degree steps off the player's look,
     * and the pose rolls (the head's 0-5 pitch + the ±10 yaw, the body's
     * ±5 yaw).
     *
     * @return whether a stand landed (the stack consumes one unit).
     */
    public boolean place(BlockPosition clicked, int face, float playerYaw, Random random) {
        if (face == 0) {
            return false; // DOWN: the reference's own refusal
        }
        BlockPosition cell = surface.replaceableAt(clicked) ? clicked : offsetFace(clicked, face);
        BlockPosition above = cell.offset(0, 1, 0);
        // The two-cell gate (useOn lines 33-38): both the cell and the
        // headroom must be air or replaceable.
        boolean blocked = !(surface.airAt(cell) || surface.replaceableAt(cell));
        blocked |= !(surface.airAt(above) || surface.replaceableAt(above));
        if (blocked) {
            return false;
        }
        // The entity box gate (useOn lines 40-46): nothing may stand in
        // the 1x2x1 box the body claims.
        if (surface.entityOccupies(cell.x(), cell.y(), cell.z(),
                cell.x() + 1.0, cell.y() + 2.0, cell.z() + 1.0)) {
            return false;
        }
        // The yaw snap (useOn line 52): floor((wrapDegrees(yaw - 180) +
        // 22.5) / 45) * 45 — the 45-degree compass walk.
        float wrapped = playerYaw - 180.0f;
        while (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        while (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        float yaw = (float) (Math.floor((wrapped + 22.5f) / 45.0f) * 45.0f);
        ArmorStand stand = new ArmorStand(nextId, cell.x() + 0.5, cell.y(), cell.z() + 0.5, yaw);
        pickRotation(stand, random);
        nextId++;
        stands.put(stand.entityId(), stand);
        for (Listener listener : listeners) {
            listener.onStandSpawned(stand);
        }
        return true;
    }

    /** The pose roll (ArmorStandItem.pickRotation lines 70-80). */
    private static void pickRotation(ArmorStand stand, Random random) {
        float headPitch = random.nextFloat() * 5.0f;
        float headYaw = random.nextFloat() * 20.0f - 10.0f;
        stand.setHead(new ArmorStand.Pose(stand.head().pitch() + headPitch,
                stand.head().yaw() + headYaw, stand.head().roll()));
        float bodyYaw = random.nextFloat() * 10.0f - 5.0f;
        stand.setBody(new ArmorStand.Pose(stand.body().pitch(),
                stand.body().yaw() + bodyYaw, stand.body().roll()));
    }

    private static BlockPosition offsetFace(BlockPosition clicked, int face) {
        return switch (face) {
            case 1 -> clicked.offset(0, 1, 0);
            case 2 -> clicked.offset(0, 0, -1);
            case 3 -> clicked.offset(0, 0, 1);
            case 4 -> clicked.offset(-1, 0, 0);
            case 5 -> clicked.offset(1, 0, 0);
            default -> clicked;
        };
    }

    // ------------------------------------------------------------------
    // The equipment swap ladder (interactAt lines 273-342 + swapItem)
    // ------------------------------------------------------------------

    /**
     * The right-click swap (interactAt over swapItem lines 344-365): the
     * armor item in hand names its own slot, a skull or a pumpkin claims
     * the head, an empty hand takes the worn piece the click's height
     * names. The swap rules: the creative hand places a size-1 copy on a
     * bare slot without paying, a single held unit swaps fully, a bigger
     * stack carves one unit onto a bare slot, and an item onto an
     * occupied slot (or a held item with the arms hidden) never lands.
     *
     * @return whether the interact landed (the marker refusal answers
     * false — the click falls through).
     */
    public boolean interactAt(ArmorStand stand, ItemStack held, double offsetY,
                              boolean creative, Consumer<ItemStack> handSetter) {
        Objects.requireNonNull(stand, "stand");
        if ((stand.status() & ArmorStand.FLAG_MARKER) != 0) {
            return false; // the marker's hitbox-less refusal
        }
        // The held armor's own slot (interactAt lines 280-293): the
        // reference's ArmorItem.slot is the engine's Armor.Slot ordinal.
        int heldSlot = 0;
        if (!held.isEmpty()) {
            var spec = Armor.specOf(held.type());
            if (spec.isPresent()) {
                heldSlot = switch (spec.get().slot()) {
                    case FEET -> 1;
                    case LEGS -> 2;
                    case CHEST -> 3;
                    case HEAD -> 4;
                };
            }
            // The skull and the pumpkin claim the head slot (lines
            // 295-297).
            String heldId = held.type().identifier().value();
            if (heldId.equals("skull") || heldId.equals("pumpkin")) {
                heldSlot = 4;
            }
        }
        // The empty hand's height bands (lines 306-314): the click's
        // offset names the worn piece to take (the small stand's offsets
        // halve).
        int takeSlot = 0;
        boolean small = (stand.status() & ArmorStand.FLAG_SMALL) != 0;
        double height = small ? offsetY * 2.0 : offsetY;
        if (height >= 0.1 && height < 0.1 + (small ? 0.8 : 0.45)
                && !stand.equipment(1).isEmpty()) {
            takeSlot = 1;
        } else if (height >= 0.9 + (small ? 0.3 : 0.0)
                && height < 0.9 + (small ? 1.0 : 0.7)
                && !stand.equipment(3).isEmpty()) {
            takeSlot = 3;
        } else if (height >= 0.4 && height < 0.4 + (small ? 1.0 : 0.8)
                && !stand.equipment(2).isEmpty()) {
            takeSlot = 2;
        } else if (height >= 1.6 && !stand.equipment(4).isEmpty()) {
            takeSlot = 4;
        }
        // The held-item gates (lines 328-336): an item onto the HAND slot
        // needs the arms shown; an item onto an occupied slot never lands
        // (swapItem's own size <= 1 gate) — the fall-through takes the
        // worn piece instead.
        int slot;
        if (!held.isEmpty()) {
            if (heldSlot == 0 && (stand.status() & ArmorStand.FLAG_SHOW_ARMS) == 0) {
                return true; // consumed: the arms stay hidden
            }
            slot = heldSlot;
            if (!stand.equipment(slot).isEmpty() && held.count() > 1) {
                return true; // swapItem's refusal: the stack too big
            }
        } else {
            slot = takeSlot;
            if (stand.equipment(slot).isEmpty()) {
                return true; // nothing worn, nothing held: consumed
            }
        }
        // The swap (swapItem lines 344-365): the creative hand places the
        // size-1 copy on a bare slot without paying (the hand keeps its
        // stack — the reference's own arm); a single unit swaps fully; a
        // bigger stack carves one unit onto a bare slot.
        ItemStack current = stand.equipment(slot);
        if (creative && current.isEmpty() && !held.isEmpty()) {
            stand.setEquipment(slot, ItemStack.of(held.type(), 1)
                    .withDamage(held.damage()).withName(held.displayName()));
            // the hand keeps its stack (the reference's creative arm
            // never touches the inventory)
        } else if (held.isEmpty() || held.count() <= 1) {
            stand.setEquipment(slot, held);
            handSetter.accept(current);
        } else {
            stand.setEquipment(slot, ItemStack.of(held.type(), 1)
                    .withDamage(held.damage()).withName(held.displayName()));
            handSetter.accept(held.withCount(held.count() - 1));
        }
        for (Listener listener : listeners) {
            listener.onStandEquipmentChanged(stand, slot);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // The melee break ladder (takeDamage lines 368-433)
    // ------------------------------------------------------------------

    /**
     * The player's melee hit (takeDamage's player arm, lines 412-429):
     * the creative hand removes the stand outright (no drops); the first
     * hit inside the five-tick window's gap only stamps the clock (the
     * wobble); a hit inside the window breaks the stand — the stand item
     * plus every worn piece drops.
     *
     * @return true when the hit was consumed.
     */
    public boolean attack(ArmorStand stand, boolean creative) {
        Objects.requireNonNull(stand, "stand");
        if (creative) {
            remove(stand);
            return true;
        }
        long now = surface.now();
        if (now - stand.lastDamageTime() > DOUBLE_HIT_WINDOW) {
            stand.setLastDamageTime(now); // the wobble: the first hit stamps
            return true;
        }
        // The break (onBrokenByPlayer lines 475-478): the stand item at
        // the feet, then the equipment at the head cell (onBroken's own
        // up() offset).
        remove(stand);
        surface.dropItem(new Position(stand.x(), stand.y(), stand.z()),
                ItemStack.of(BuiltinItems.ARMOR_STAND, 1));
        dropEquipment(stand);
        return true;
    }

    /** The equipment drop (onBroken lines 480-490): every piece at the head cell. */
    private void dropEquipment(ArmorStand stand) {
        Position at = new Position(stand.x(), stand.y() + 1.0, stand.z());
        for (ItemStack piece : stand.equipment()) {
            if (!piece.isEmpty()) {
                surface.dropItem(at, piece);
            }
        }
    }

    /** The explosion arm (takeDamage lines 382-386): the equipment drops, the stand item does NOT. */
    public void breakByExplosion(ArmorStand stand) {
        remove(stand);
        dropEquipment(stand);
    }

    /** The removal (the registry drop + the wire destroy). */
    public void remove(ArmorStand stand) {
        stands.remove(stand.entityId());
        for (Listener listener : listeners) {
            listener.onStandRemoved(stand);
        }
    }

    // ------------------------------------------------------------------
    // The fire burn-down (takeDamage lines 388-400 + takeFireDamage)
    // ------------------------------------------------------------------

    /**
     * One tick of every burning stand's clock: the ignite (the FIRE
     * contact) burns for 100 ticks, each burning tick costs the 4.0
     * on-fire toll, and the 0.5 floor breaks the stand (the equipment
     * drops — the stand item does not, the burn's own onBroken arm).
     */
    public void tick() {
        List<ArmorStand> burned = null;
        for (ArmorStand stand : stands.values()) {
            if (stand.fireTicks() <= 0) {
                continue;
            }
            stand.setFireTicks(stand.fireTicks() - 1);
            stand.setHealth(stand.health() - ArmorStand.ON_FIRE_TOLL);
            if (stand.health() <= ArmorStand.FIRE_BREAK_FLOOR) {
                if (burned == null) {
                    burned = new ArrayList<>();
                }
                burned.add(stand);
            }
        }
        if (burned != null) {
            for (ArmorStand stand : burned) {
                remove(stand);
                dropEquipment(stand);
            }
        }
    }

    /** The ignite (the standing-in-fire contact, takeDamage lines 388-395). */
    public void ignite(ArmorStand stand) {
        if (stand.fireTicks() <= 0) {
            stand.setFireTicks(ArmorStand.IGNITE_TICKS);
        } else {
            stand.setHealth(stand.health() - ArmorStand.FIRE_CONTACT_TOLL);
            if (stand.health() <= ArmorStand.FIRE_BREAK_FLOOR) {
                remove(stand);
                dropEquipment(stand);
            }
        }
    }

    // ------------------------------------------------------------------
    // The persistence (the ZAD snapshot form)
    // ------------------------------------------------------------------

    /** One stand's persistable view (writeCustomNbt's own fields). */
    public record StandRecord(double x, double y, double z, float yaw,
                              List<ItemStack> equipment, byte status,
                              ArmorStand.Pose head, ArmorStand.Pose body,
                              ArmorStand.Pose leftArm, ArmorStand.Pose rightArm,
                              ArmorStand.Pose leftLeg, ArmorStand.Pose rightLeg) { }

    /** The full snapshot (saved at saveAllNow). */
    public List<StandRecord> snapshot() {
        List<StandRecord> records = new ArrayList<>();
        for (ArmorStand stand : stands.values()) {
            List<ItemStack> equipment = new ArrayList<>();
            for (ItemStack piece : stand.equipment()) {
                equipment.add(piece);
            }
            records.add(new StandRecord(stand.x(), stand.y(), stand.z(), stand.yaw(),
                    equipment, stand.status(), stand.head(), stand.body(),
                    stand.leftArm(), stand.rightArm(), stand.leftLeg(), stand.rightLeg()));
        }
        return records;
    }

    /** The restore: the saved stands land back through the spawn listener. */
    public void restore(List<StandRecord> records) {
        for (StandRecord record : records) {
            ArmorStand stand = new ArmorStand(nextId++, record.x(), record.y(), record.z(),
                    record.yaw());
            for (int slot = 0; slot < 5 && slot < record.equipment().size(); slot++) {
                stand.setEquipment(slot, record.equipment().get(slot));
            }
            stand.setStatus(record.status());
            stand.setHead(record.head() == null ? ArmorStand.DEFAULT_HEAD : record.head());
            stand.setBody(record.body() == null ? ArmorStand.DEFAULT_BODY : record.body());
            stand.setLeftArm(record.leftArm() == null ? ArmorStand.DEFAULT_LEFT_ARM : record.leftArm());
            stand.setRightArm(record.rightArm() == null ? ArmorStand.DEFAULT_RIGHT_ARM : record.rightArm());
            stand.setLeftLeg(record.leftLeg() == null ? ArmorStand.DEFAULT_LEFT_LEG : record.leftLeg());
            stand.setRightLeg(record.rightLeg() == null ? ArmorStand.DEFAULT_RIGHT_LEG : record.rightLeg());
            stands.put(stand.entityId(), stand);
            for (Listener listener : listeners) {
                listener.onStandSpawned(stand);
            }
        }
    }
}
