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
                && !id.equals(BuiltinBlocks.COBWEB.identifier())
                && !isNetherPortal(id)
                && !isSign(id)
                && !isLadder(id)
                && !isOpenDoorHalf(id)
                && !isOpenFenceGate(id)
                && !isFlora(id)
                && !WoodVariantBlocks.isSapling(id) // the six saplings: walk-through, never replaceable
                && !isCarpet(id)
                && !isRail(id)
                && !isRedstoneComponent(id)
                && !isMovingPiston(id)
                && !isTripwireFamily(id)
                && !FluidBlocks.isFluid(id);
    }

    /**
     * The carpet family (Slice 31 — CarpetBlock's 1/16-tall shape): the
     * cover never blocks a body (the reference's isCube false), the same
     * envelope as the snow layer's thin ground cover.
     */
    public static boolean isCarpet(Identifier id) {
        return net.zaminmc.torch.server.block.DyeFamilyBlocks.isCarpet(id);
    }

    /**
     * The tripwire family (Slice 10c): the wire (a 5/32-tall line) and the
     * hook (a thin wall mount) never block a body (the reference's
     * isCube-false, null collision shape) — and like every signal source,
     * the hook stays out of the strong re-radiation set.
     */
    public static boolean isTripwireFamily(Identifier id) {
        return id.namespace().equals("minecraft")
                && (id.value().equals("tripwire")
                || id.value().startsWith("tripwire_"));
    }

    /**
     * The in-flight piston carrier (legacy 36, Slice 9e): never blocks a
     * body — the reference's collision is the progress-scaled partial box
     * (MovingBlock.getCollisionShape), and the entity displacement rides
     * the entity-collision slice; the empty cell keeps bodies from
     * clipping into the landing.
     */
    public static boolean isMovingPiston(Identifier id) {
        return id.namespace().equals("minecraft")
                && id.value().startsWith("moving_piston_");
    }

    /**
     * The redstone family (Slice 9a): the wire (a 1/16-tall line), the
     * torches (the thin wall model, like the plain torch) and the repeaters
     * (a 1/8-tall diode) never block a body — and the reference's
     * {@code Block.isSolid()} (material.isSolidBlocking() && isCube() &&
     * !isSignalSource()) keeps every signal source out of the re-radiation
     * set as well: a wire, a torch or a repeater never re-emits strong
     * power as weak.
     */
    public static boolean isRedstoneComponent(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        return value.startsWith("redstone_wire")
                || value.startsWith("redstone_torch")
                || value.startsWith("unlit_redstone_torch")
                || value.startsWith("repeater_")
                || value.startsWith("powered_repeater_")
                || value.startsWith("comparator_")
                || value.startsWith("powered_comparator_")
                || value.startsWith("lever_")
                || value.startsWith("stone_button_")
                || value.startsWith("wooden_button_")
                || value.startsWith("stone_pressure_plate")
                || value.startsWith("wooden_pressure_plate");
    }

    /**
     * The nether portal (90): walk-through in both axis planes (the
     * reference's {@code getCollisionShape} returning null — the bodies
     * walk into it and the portal clock reads the occupancy).
     */
    public static boolean isNetherPortal(Identifier id) {
        return id.equals(BuiltinBlocks.NETHER_PORTAL.identifier())
                || id.equals(BuiltinBlocks.NETHER_PORTAL_Z.identifier());
    }

    /** The rails (block 66 + the Slice 38 trio 27/28/157): walk-through
     *  track, the minecart reads the axis. */
    public static boolean isRail(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        if (value.equals("rail") || value.startsWith("rail_")) {
            return true;
        }
        // the trio families' twelve states each
        return value.equals("golden_rail") || value.startsWith("golden_rail_")
                || value.equals("detector_rail") || value.startsWith("detector_rail_")
                || value.equals("activator_rail") || value.startsWith("activator_rail_");
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
                || id.equals(BuiltinBlocks.BROWN_MUSHROOM.identifier())
                || id.equals(BuiltinBlocks.RED_MUSHROOM.identifier())
                || id.equals(BuiltinBlocks.SUGAR_CANE.identifier())
                || id.equals(BuiltinBlocks.SNOW_LAYER.identifier()) // the thin ground cover
                || id.equals(BuiltinBlocks.WATERLILY.identifier()) // the 1/64 lily surface
                || isCropStage(id);
    }

    /**
     * The replaceable read (the armor stand's placement gate — the
     * reference's {@code canBeReplaced}): air and the walk-through flora
     * answer true; a body may stand there.
     */
    public static boolean isReplaceable(Identifier id) {
        return isFlora(id);
    }

    /** The ladders (block 65): climbable, never block a body. */
    public static boolean isLadder(Identifier id) {
        return id.namespace().equals("minecraft")
                && (id.value().equals("ladder") || id.value().startsWith("ladder_"));
    }

    /**
     * The open door halves: the swing opens the passage (the reference's
     * canWalkThrough reads the combined metadata's open bit — the lower's
     * open band and every upper whose identifier ends in the open form:
     * the manual pair {@code _upper_open}/{@code _upper_right_open} and
     * the signal-held pair {@code _upper_powered_open}/...
     * {@code _upper_right_powered_open}). The closed halves stay solid
     * (the flat-solid model's thin-panel stand-in).
     */
    public static boolean isOpenDoorHalf(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        if (value.contains("_trapdoor")) {
            return false; // the trapdoor family is its own slice
        }
        return value.contains("_door_open")
                || (value.contains("_door_upper") && value.endsWith("_open"));
    }

    /**
     * The open fence gates (Slice 37 — FenceGateBlock.getCollisionShape
     * lines 52-61: the open gate returns null — canWalkThrough reads the
     * OPEN bit): the open band ({@code _open} / {@code _powered_open})
     * never blocks a body; the closed states stay solid (the flat-solid
     * stand-in for the 1.5-tall panel — the reference's own Box).
     */
    public static boolean isOpenFenceGate(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        int gate = value.indexOf("_fence_gate");
        if (gate < 0) {
            return false;
        }
        return value.substring(gate + "_fence_gate".length()).contains("_open");
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

    /**
     * The village's crop families (the farm plots): carrots (141) and
     * potatoes (142) with the wheat's own age 0..7 band — walk-through
     * plants like the wheat (the reference's BlockCrops material).
     */
    public static boolean isCropStage(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        return value.startsWith("wheat_stage")
                || value.startsWith("carrots_stage")
                || value.startsWith("potatoes_stage")
                || value.startsWith("pumpkin_stem_stage")
                || value.startsWith("melon_stem_stage");
    }

    /** @return the age digit (0..7) of a crop identifier, -1 when none. */
    public static int cropStageOf(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return -1;
        }
        String value = id.value();
        if (value.startsWith("wheat_stage")) {
            return value.charAt("wheat_stage".length()) - '0';
        }
        if (value.startsWith("carrots_stage")) {
            return value.charAt("carrots_stage".length()) - '0';
        }
        if (value.startsWith("potatoes_stage")) {
            return value.charAt("potatoes_stage".length()) - '0';
        }
        // The stem families (Slice 41 — StemBlock's AGE band 0..7)
        if (value.startsWith("pumpkin_stem_stage")) {
            return value.charAt("pumpkin_stem_stage".length()) - '0';
        }
        if (value.startsWith("melon_stem_stage")) {
            return value.charAt("melon_stem_stage".length()) - '0';
        }
        return -1;
    }

    /** @return the crop identifier prefix ("wheat"/"carrots"/"potatoes"), null when none. */
    public static String cropFamilyOf(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return null;
        }
        String value = id.value();
        if (value.startsWith("wheat_stage")) {
            return "wheat";
        }
        if (value.startsWith("carrots_stage")) {
            return "carrots";
        }
        if (value.startsWith("potatoes_stage")) {
            return "potatoes";
        }
        if (value.startsWith("pumpkin_stem_stage")) {
            return "pumpkin_stem";
        }
        if (value.startsWith("melon_stem_stage")) {
            return "melon_stem";
        }
        return null;
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
