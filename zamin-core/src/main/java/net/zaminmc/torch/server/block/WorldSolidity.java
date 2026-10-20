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
 * everything else. The shape queries ({@link #isSolidAt} / {@link #supportY})
 * resolve through the {@link CollisionShapes} table (the collision-shape
 * slice): every partial block's own collision boxes — the slabs' halves, the
 * fences' 1.5-tall post with its connection arms, the wall's opening post,
 * the closed gate's panel, the thin covers — carve the full-cube rule into
 * the reference's exact geometry.</p>
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
                && !FireBlocks.isFire(id)
                && !id.equals(BuiltinBlocks.COBWEB.identifier())
                && !isNetherPortal(id)
                && !isEndPortal(id)
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
     * The end portal (Slice 54 — EndPortalBlock.addCollisions' empty
     * override): the 1/16 film never blocks a body exactly like the
     * nether portal (the contact arm rides the End dimension's slice).
     */
    public static boolean isEndPortal(Identifier id) {
        return net.zaminmc.torch.server.block.EndPortalBlocks.isEndPortal(id);
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
     * point at absolute {@code (x, y, z)} lies inside the collision shape
     * of the block type in whose cell the point sits — the
     * {@link CollisionShapes} table's own answer (a body stands on the
     * cake's half-height sliver, steps around the fence's post, walks
     * beneath a top slab).
     */
    public static boolean isSolidAt(BlockType type, double x, double y, double z,
            CollisionShapes.NeighborProbe probe) {
        return CollisionShapes.contains(type, x, y, z, probe);
    }

    /**
     * The shape-aware point test without a neighbour probe: the isolated
     * form (no fence, wall or pane connections, no door pairs, no chest
     * doubles) — the Y-only legacy view keeps the cell's centre column.
     */
    public static boolean isSolidAt(BlockType type, double y) {
        return CollisionShapes.contains(type, 0.5, y, 0.5, CollisionShapes.ISOLATED);
    }

    /**
     * The top surface of the collision shape in the point's cell (the
     * collision-shape slice): the box top the body rests on — full blocks
     * the cell's ceiling, slabs their half, the fences' post its 1.5, the
     * thin covers their own height. Non-solid cells answer
     * {@link Double#NEGATIVE_INFINITY} (no support).
     */
    public static double supportY(BlockType type, double x, double y, double z,
            CollisionShapes.NeighborProbe probe) {
        return CollisionShapes.top(type, x, y, z, probe);
    }

    /**
     * The shape's top surface without a neighbour probe: the isolated form
     * on the cell's centre column (the Y-only legacy view).
     */
    public static double supportY(BlockType type, double y) {
        return CollisionShapes.top(type, 0.5, y, 0.5, CollisionShapes.ISOLATED);
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
                || (id.namespace().equals("minecraft")
                        && (id.value().equals("cocoa") || id.value().startsWith("cocoa_")))
                || FlowerBlocks.isFlowerIdentifier(id)
                || DoublePlantBlocks.isDoublePlantIdentifier(id)
                || isCropStage(id);
    }

    /**
     * The replaceable read (the reference's {@code canBeReplaced}): air and
     * the walk-through flora answer true; a body may stand there.
     *
     * <p>The double-plant family's per-variant refinement (Wave L6 —
     * DoublePlantBlock.canBeReplaced 1:1): only the GRASS and FERN variants
     * (both halves) are replaceable; the four FLOWER variants are placement
     * targets like any normal block. The family stays walk-through as a
     * whole ({@link #isFlora} is the solidity read, unchanged) —
     * replaceability and walk-through are two different laws.</p>
     */
    public static boolean isReplaceable(Identifier id) {
        if (DoublePlantBlocks.isDoublePlantIdentifier(id)) {
            return DoublePlantBlocks.isReplaceableVariant(id);
        }
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

    /** The fire family (51): walk-through, damages bodies standing in it — every AGE state (FireBlocks' band). */
    public static boolean isFire(Identifier id) {
        return FireBlocks.isFire(id);
    }

    /** The sugar cane (83): walk-through reed, grows to three on wet soil. */
    public static boolean isSugarCane(Identifier id) {
        return id.equals(BuiltinBlocks.SUGAR_CANE.identifier());
    }

    /** The cactus (81): solid, breaks beside solids, damages bodies touching it. */
    public static boolean isCactus(Identifier id) {
        return id.equals(BuiltinBlocks.CACTUS.identifier());
    }

    /**
     * The fence families (85 + Slice 36's five woods 188-192 + the nether
     * brick 113): solid, every post and arm standing 1.5 blocks tall —
     * the spawn floor's own exclusion (a fence is never a full-cube
     * ground, however solid it reads).
     */
    public static boolean isFence(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        return value.equals("oak_fence")
                || value.equals("spruce_fence") || value.equals("birch_fence")
                || value.equals("jungle_fence") || value.equals("dark_oak_fence")
                || value.equals("acacia_fence")
                || value.equals("nether_brick_fence");
    }

    /** Either furnace half (61 unlit / 62 lit) — the container's two faces, the facing states included (Slice L3). */
    public static boolean isFurnaceBlock(Identifier id) {
        String value = id.value();
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        return value.equals("furnace") || value.equals("furnace_lit")
                || value.startsWith("furnace_") || value.startsWith("furnace_lit_");
    }
}
