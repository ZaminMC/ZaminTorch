package net.zaminmc.torch.decoration;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.Direction;
import net.zaminmc.torch.item.ItemStack;

import java.util.List;

/**
 * The decoration-census query surface (§27.3's world-state family — the
 * README TODO's row 15, the Wave IX exposure): the reference 1.8.8's own
 * hanging bodies and armor stands — the item frames with their displayed
 * stacks, rotations and comparator reads, the paintings with their art,
 * and the stands' family-specific rows (the five equipment slots, the
 * status flags, the six-part pose) — reached through
 * {@link World#decorations()}.
 *
 * <p>The engine behind it is {@code server/decoration/} + {@code
 * server/armorstand/} (the compat ledger's Slice 28a/28b/28c rows:
 * {@code DecorationSystem} over the reference's entity/decoration family
 * — the placement gates, the shared survival poll, the interact and
 * attack ladders, the through-the-wall comparator consult — and {@code
 * ArmorStandSystem} over ArmorStandEntity's placement, swap ladder,
 * melee break and fire burn-down). This surface exposes the READS a
 * plugin needs to answer "what hangs on the walls, and what do the
 * stands carry?" — the census's own mutation arms (the placement, the
 * interact, the attack) stay engine-owned, driven by the live use and
 * damage paths.</p>
 *
 * <p><b>Threading (§27.6):</b> every answer is an immutable
 * point-in-time snapshot — each enumeration copies its family's rows at
 * query time under the owning system's monitor (every public mutation
 * entry point of the two systems holds the same monitor, the
 * VillageSystem shape), so the result is freely consumable from any
 * thread and never a torn mid-tick state. A snapshot taken off the
 * simulation context is best-effort: it reflects the census at some
 * instant between mutations. Each family's rows copy under their own
 * system's monitor — the three enumerations are independent snapshots
 * of independent registries, never one cross-family atomic. The record
 * types carry no engine references — every row is a copied value.</p>
 */
public interface Decorations {

    /** The empty answer (no bodies hung, no stands placed — a world
     * without the family, or the view before the boot wires it). */
    Decorations EMPTY = new Decorations() {
        @Override public List<ItemFrame> frames() {
            return List.of();
        }

        @Override public List<Painting> paintings() {
            return List.of();
        }

        @Override public List<ArmorStand> armorStands() {
            return List.of();
        }
    };

    /**
     * @return a point-in-time copy of every live item frame (the
     *         registry's own wall-cell order), each with its wall cell,
     *         its facing, its displayed stack, its eight-step rotation,
     *         and its comparator read.
     */
    List<ItemFrame> frames();

    /**
     * @return a point-in-time copy of every live painting (the
     *         registry's own wall-cell order), each with its wall cell,
     *         its facing, and its art (the reference's own twenty-six
     *         entry registry).
     */
    List<Painting> paintings();

    /**
     * @return a point-in-time copy of every live armor stand (the
     *         registry's own id order), each with its feet position and
     *         yaw, its five equipment slots, its status flags, and its
     *         six-part pose.
     */
    List<ArmorStand> armorStands();

    /**
     * One item frame's point-in-time census row (the reference's
     * {@code ItemFrameEntity} reads): the wall cell the body hangs in,
     * the horizontal facing it points away from the wall with, the
     * displayed stack (a size-1 copy — setDisplayItem's own rule;
     * empty when bare), the eight-step rotation (the right-click walk's
     * own byte wrap), and the comparator read the through-the-wall
     * consult answers ({@code getAnalogSignal}: 0 when bare, otherwise
     * {@code rotation % 8 + 1}).
     *
     * @param entityId     the frame's engine entity id (the Spawn
     *                     Object 71 wire id)
     * @param pos          the wall cell the frame hangs in
     * @param facing       the facing the frame points away from its
     *                     wall (the reference's byIdHorizontal order:
     *                     south, west, north, east)
     * @param item         the displayed stack (empty when bare)
     * @param rotation     the rotation step 0-7
     * @param analogSignal the comparator read (0 bare, else
     *                     {@code rotation % 8 + 1})
     */
    record ItemFrame(int entityId, BlockPosition pos, Direction facing,
                     ItemStack item, int rotation, int analogSignal) {
    }

    /**
     * One painting's point-in-time census row (the reference's
     * {@code PaintingEntity} reads): the wall cell, the facing, and the
     * motive — one of the reference's own art entries with its pixel
     * size.
     *
     * @param entityId the painting's engine entity id (the Spawn
     *                 Painting wire id)
     * @param pos      the wall cell the painting hangs in
     * @param facing   the facing the painting points away from its wall
     * @param motive   the art entry (never null — the read walk's own
     *                 Kebab fallback)
     */
    record Painting(int entityId, BlockPosition pos, Direction facing,
                    Motive motive) {
    }

    /**
     * One art entry (the reference's {@code PaintingEntity.Motive}
     * registry — the exact twenty-six, their pixel sizes and atlas
     * offsets).
     *
     * @param name   the registry name (the wire's Spawn Painting title)
     * @param width  the art's pixel width (16 pixels per block)
     * @param height the art's pixel height
     * @param u      the atlas u offset (the renderer's own concern,
     *               carried for parity)
     * @param v      the atlas v offset
     */
    record Motive(String name, int width, int height, int u, int v) {
    }

    /**
     * One armor stand's point-in-time census row (the reference's
     * {@code ArmorStandEntity} family-specific reads — the generic
     * {@code entity.LivingEntity} surface covers the stand's body;
     * these are the rows the family's own model carries): the feet
     * position and the placement's snapped yaw, the five equipment
     * slots in the wire's own order, the status byte's five flags, and
     * the six-part pose.
     *
     * @param entityId    the stand's engine entity id (the Spawn
     *                    Object 78 wire id)
     * @param x           the feet position's x
     * @param y           the feet position's y
     * @param z           the feet position's z
     * @param yaw         the body yaw (the placement's 45-degree snap)
     * @param equipment   the five slots in the wire's Entity Equipment
     *                    order (0 the hand, 1 the boots, 2 the
     *                    leggings, 3 the chestplate, 4 the helmet —
     *                    empty when bare; the copied list is immutable)
     * @param small       the small flag (the synced byte's bit 0)
     * @param noGravity   the no-gravity flag (bit 1 — a flagged stand
     *                    never walks)
     * @param showArms    the show-arms flag (bit 2 — the hand slot's
     *                    interact gate)
     * @param noBasePlate the no-base-plate flag (bit 3)
     * @param marker      the marker flag (bit 4 — the hitbox-less
     *                    quirk: no interact, no damage)
     * @param head        the head pose
     * @param body        the body pose
     * @param leftArm     the left arm pose (the reference's default
     *                    {@code (-10, 0, -10)})
     * @param rightArm    the right arm pose (the default
     *                    {@code (-15, 0, 10)})
     * @param leftLeg     the left leg pose (the default
     *                    {@code (-1, 0, -1)})
     * @param rightLeg    the right leg pose (the default
     *                    {@code (1, 0, 1)})
     */
    record ArmorStand(int entityId, double x, double y, double z, float yaw,
                      List<ItemStack> equipment,
                      boolean small, boolean noGravity, boolean showArms,
                      boolean noBasePlate, boolean marker,
                      Pose head, Pose body,
                      Pose leftArm, Pose rightArm, Pose leftLeg, Pose rightLeg) {
    }

    /**
     * One pose part (the wire's synced Rotation triple): pitch, yaw and
     * roll in degrees.
     */
    record Pose(float pitch, float yaw, float roll) {
    }
}
