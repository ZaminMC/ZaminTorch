package net.zaminmc.torch.server.decoration;

import java.util.List;

/**
 * The public decoration-census view (§27.3's family — the README TODO's
 * row 15, the Wave IX exposure): the {@link DecorationSystem}'s frames
 * and paintings plus the armor-stand system's family rows dressed as the
 * {@code net.zaminmc.torch.decoration.Decorations} query surface. Every
 * answer COPIES the census at query time (§27.6): each enumeration
 * builds under its owning system's monitor — the same monitor every
 * public mutation entry point of that system holds — so the snapshot is
 * tear-free against the tick lane from any calling thread, best-effort
 * consistent (an off-lane query sees some instant between mutations,
 * never a half-written walk). The three enumerations are independent
 * snapshots of independent registries (the two systems carry their own
 * monitors), never one cross-family atomic. The census's mutation arms
 * stay engine-owned.
 *
 * <p>(The engine type names are fully qualified below: the implemented
 * interface's own nested {@code ItemFrame}/{@code Painting}/{@code
 * ArmorStand} records would otherwise shadow the same-package engine
 * classes inside this class body — Java's inherited-member scope rule,
 * the same trap VillageViews documents.)</p>
 */
public final class DecorationViews implements net.zaminmc.torch.decoration.Decorations {

    private final DecorationSystem decorations;
    private final net.zaminmc.torch.server.armorstand.ArmorStandSystem armorStands;

    public DecorationViews(DecorationSystem decorations,
                           net.zaminmc.torch.server.armorstand.ArmorStandSystem armorStands) {
        this.decorations = java.util.Objects.requireNonNull(decorations, "decorations");
        this.armorStands = java.util.Objects.requireNonNull(armorStands, "armorStands");
    }

    @Override
    public List<net.zaminmc.torch.decoration.Decorations.ItemFrame> frames() {
        // §27.6: the copy builds under the system's monitor (every
        // public mutation entry point holds the same monitor) with each
        // row taken as an immutable value — tear-free against the tick
        // lane, any calling thread.
        synchronized (decorations) {
            return decorations.allFrames().stream().map(DecorationViews::frame).toList();
        }
    }

    @Override
    public List<net.zaminmc.torch.decoration.Decorations.Painting> paintings() {
        synchronized (decorations) {
            return decorations.allPaintings().stream().map(DecorationViews::painting).toList();
        }
    }

    @Override
    public List<net.zaminmc.torch.decoration.Decorations.ArmorStand> armorStands() {
        // The stands' own monitor (the ArmorStandSystem's mutation arms
        // hold it) — the same per-system snapshot law as the frames.
        synchronized (armorStands) {
            return armorStands.allStands().stream().map(DecorationViews::stand).toList();
        }
    }

    /** One frame's immutable census row (the engine's own reads, whole). */
    private static net.zaminmc.torch.decoration.Decorations.ItemFrame frame(
            net.zaminmc.torch.server.decoration.ItemFrame row) {
        return new net.zaminmc.torch.decoration.Decorations.ItemFrame(
                row.entityId(), row.pos(), facing(row.facing()),
                row.display(), row.rotation(), row.analogSignal());
    }

    /** One painting's immutable census row (the motive off the reference's own registry). */
    private static net.zaminmc.torch.decoration.Decorations.Painting painting(
            net.zaminmc.torch.server.decoration.Painting row) {
        return new net.zaminmc.torch.decoration.Decorations.Painting(
                row.entityId(), row.pos(), facing(row.facing()), motive(row.motive()));
    }

    /** One art entry (the reference's twenty-six registry, carried whole). */
    private static net.zaminmc.torch.decoration.Decorations.Motive motive(
            net.zaminmc.torch.server.decoration.Painting.Motive row) {
        return new net.zaminmc.torch.decoration.Decorations.Motive(
                row.name, row.width, row.height, row.u, row.v);
    }

    /** One stand's immutable census row (the family-specific reads, whole). */
    private static net.zaminmc.torch.decoration.Decorations.ArmorStand stand(
            net.zaminmc.torch.server.armorstand.ArmorStand row) {
        return new net.zaminmc.torch.decoration.Decorations.ArmorStand(
                row.entityId(), row.x(), row.y(), row.z(), row.yaw(),
                List.of(row.equipment()),
                row.small(), row.noGravity(), row.showArms(),
                row.noBasePlate(), row.marker(),
                pose(row.head()), pose(row.body()),
                pose(row.leftArm()), pose(row.rightArm()),
                pose(row.leftLeg()), pose(row.rightLeg()));
    }

    /** One pose part (the wire's Rotation triple, copied). */
    private static net.zaminmc.torch.decoration.Decorations.Pose pose(
            net.zaminmc.torch.server.armorstand.ArmorStand.Pose row) {
        return new net.zaminmc.torch.decoration.Decorations.Pose(
                row.pitch(), row.yaw(), row.roll());
    }

    /**
     * The engine's byIdHorizontal code (0 south +Z, 1 west -X, 2 north
     * -Z, 3 east +X — the reference Direction table's own order, the
     * engine's {@code ItemFrame.FACING_OFFSETS}) dressed as the api
     * facing.
     */
    private static net.zaminmc.torch.block.Direction facing(int code) {
        return switch (code & 3) {
            case 0 -> net.zaminmc.torch.block.Direction.SOUTH;
            case 1 -> net.zaminmc.torch.block.Direction.WEST;
            case 2 -> net.zaminmc.torch.block.Direction.NORTH;
            default -> net.zaminmc.torch.block.Direction.EAST;
        };
    }
}
