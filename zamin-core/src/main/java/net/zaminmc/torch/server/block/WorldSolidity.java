package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

import java.util.Objects;

/**
 * The one solidity rule of the engine, shared by every physics query — mob
 * bodies, item stacks, falling blocks and projectiles all ask the same
 * question and get the same answer.
 *
 * <p>Non-solid: air, torches (the thin wall model) and fluids — the
 * historical Material replaces-able set: entities fall through water and
 * lava, items sink through the pool, arrows do not stick in a stream. Solid:
 * everything else, with the collision-shape slice's refinement: the shape
 * queries ({@link #isSolidAt} / {@link #supportY}) carve the partial blocks
 * out of the full-cube rule — bottom slabs and stairs fill their cell's
 * lower half, top slabs the upper, and the oak fence stands 1.5 blocks tall
 * (the historical fence collision).</p>
 */
public final class WorldSolidity {

    private WorldSolidity() {
    }

    /** @return whether the block type blocks entity movement. Null = air. */
    public static boolean isSolid(BlockType type) {
        if (type == null) {
            return false;
        }
        Identifier id = type.identifier();
        return !id.equals(BuiltinBlocks.AIR.identifier())
                && !id.equals(BuiltinBlocks.TORCH.identifier())
                && !id.equals(BuiltinBlocks.FIRE.identifier())
                && !isSign(id)
                && !isLadder(id)
                && !isOpenDoorHalf(id)
                && !isFlora(id)
                && !isRail(id)
                && !FluidBlocks.isFluid(id);
    }

    /** The rails (block 66): walk-through track, the minecart reads the axis. */
    public static boolean isRail(Identifier id) {
        return id.namespace().equals("minecraft")
                && (id.value().equals("rail") || id.value().startsWith("rail_"));
    }

    /**
     * The shape-aware point test (the collision-shape slice): whether the
     * point at absolute {@code y} lies inside the collision shape of the
     * block type in whose cell the point sits. Full cubes answer like
     * {@link #isSolid}; the partial blocks answer by their half — a point in
     * the upper half of a bottom slab's cell is open (a body can stand "in"
     * the slab's cell, at its surface), a point in the lower half of a top
     * slab's cell is open (an arrow flies beneath it). Stairs count as their
     * bottom half; the fence fills its whole cell (its 1.5 height shows only
     * in {@link #supportY}).
     */
    public static boolean isSolidAt(BlockType type, double y) {
        if (!isSolid(type)) {
            return false;
        }
        return switch (shapeOf(type.identifier())) {
            case FULL -> true;
            case BOTTOM_HALF -> (y - Math.floor(y)) < 0.5;
            case TOP_HALF -> (y - Math.floor(y)) >= 0.5;
        };
    }

    /**
     * The top surface of the collision shape in the cell containing absolute
     * {@code y}: full blocks answer the cell's ceiling (floor + 1), slabs and
     * stairs their half, the fence its historical 1.5 — the number a body's
     * feet rest on when the point just below them is inside the shape.
     * Non-solid cells answer {@link Double#NEGATIVE_INFINITY} (no support).
     */
    public static double supportY(BlockType type, double y) {
        if (!isSolid(type)) {
            return Double.NEGATIVE_INFINITY;
        }
        return switch (shapeOf(type.identifier())) {
            case FULL -> Math.floor(y) + (isFence(type.identifier()) ? 1.5 : 1.0);
            case BOTTOM_HALF -> Math.floor(y) + 0.5;
            case TOP_HALF -> Math.floor(y) + 1.0;
        };
    }

    /** The collision shapes of the registry's partial blocks. */
    private enum Shape {
        FULL, BOTTOM_HALF, TOP_HALF
    }

    /** @return the shape a block identifier occupies of its cell. */
    private static Shape shapeOf(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return Shape.FULL;
        }
        String value = id.value();
        if (value.endsWith("_slab_top")) {
            return Shape.TOP_HALF;
        }
        if (value.endsWith("_slab") || value.endsWith("_stairs_east")
                || value.endsWith("_stairs_west") || value.endsWith("_stairs_south")
                || value.endsWith("_stairs_north")) {
            return Shape.BOTTOM_HALF;
        }
        return Shape.FULL;
    }

    /**
     * The walk-through flora (the historical replaceable Material set): tall
     * grass, the dead bush, the two flowers, the wheat crop and the sugar
     * cane never block a body.
     */
    private static boolean isFlora(Identifier id) {
        return id.equals(BuiltinBlocks.TALL_GRASS.identifier())
                || id.equals(BuiltinBlocks.DEAD_BUSH.identifier())
                || id.equals(BuiltinBlocks.DANDELION.identifier())
                || id.equals(BuiltinBlocks.POPPY.identifier())
                || id.equals(BuiltinBlocks.SUGAR_CANE.identifier())
                || isWheatCrop(id);
    }

    /** The ladders (block 65): climbable, never block a body. */
    public static boolean isLadder(Identifier id) {
        return id.namespace().equals("minecraft")
                && (id.value().equals("ladder") || id.value().startsWith("ladder_"));
    }

    /** The open door halves: the swing opens the passage. */
    public static boolean isOpenDoorHalf(Identifier id) {
        return id.namespace().equals("minecraft")
                && (id.value().startsWith("oak_door_open")
                    || id.value().equals("oak_door_upper_open"));
    }

    /** The signs (standing 63, wall 68): the thin models, walk-through. */
    public static boolean isSign(Identifier id) {
        return id.namespace().equals("minecraft")
                && (id.value().equals("sign") || id.value().startsWith("sign_")
                    || id.value().equals("wall_sign") || id.value().startsWith("wall_sign_"));
    }

    /** The wheat crop stages (block 59, ages 0..7): walk-through plants. */
    public static boolean isWheatCrop(Identifier id) {
        return id.namespace().equals("minecraft")
                && id.value().startsWith("wheat_stage");
    }

    /** The fire block (51): walk-through, damages bodies standing in it. */
    public static boolean isFire(Identifier id) {
        return id.equals(BuiltinBlocks.FIRE.identifier());
    }

    /** The sugar cane (83): walk-through reed, grows to three on wet soil. */
    public static boolean isSugarCane(Identifier id) {
        return id.equals(BuiltinBlocks.SUGAR_CANE.identifier());
    }

    /** The cactus (81): solid, breaks beside solids, damages bodies touching it. */
    public static boolean isCactus(Identifier id) {
        return id.equals(BuiltinBlocks.CACTUS.identifier());
    }

    /** The oak fence (85): solid, its collision stands 1.5 blocks tall. */
    public static boolean isFence(Identifier id) {
        return id.equals(BuiltinBlocks.FENCE.identifier());
    }

    /** Either furnace half (61 unlit / 62 lit) — the container's two faces. */
    public static boolean isFurnaceBlock(Identifier id) {
        return id.equals(BuiltinBlocks.FURNACE.identifier())
                || id.equals(BuiltinBlocks.FURNACE_LIT.identifier());
    }
}
