package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

/**
 * The reference's {@code Material.isSolid()} — the MATERIAL model, not the
 * collision model ({@link WorldSolidity} answers a different question: what
 * blocks a body).
 *
 * <p>The reference's law (Material.java lines 59-62 + the subclasses): the
 * base {@code Material} is SOLID; {@code AirMaterial} (AIR, FIRE),
 * {@code LiquidMaterial} (WATER, LAVA), {@code PlantMaterial} (PLANT,
 * REPLACEABLE_PLANT, DECORATION, CARPET, SNOW_LAYER) and
 * {@code PortalMaterial} (PORTAL) override {@code isSolid()} to false. The
 * notable consequences against the collision model: the cobweb's material is
 * the BASE class (Material.COBWEB, Material.java lines 36-41 — solid, only
 * {@code blocksMovement()} overridden), the signs are WOOD (SignBlock line
 * 25 — solid, a sign stacks on a sign), the doors are WOOD/IRON, the
 * fence gates WOOD, the pressure plates WOOD/ROCK/GOLD/IRON (the ctor's own
 * parameter), the moving piston PISTON — all material-solid even where
 * their collision shapes let a body pass.</p>
 *
 * <p>Consulted by the sign family's laws (the twelfth verifier pass): the
 * placement's clicked-block gate (SignItem.useOn lines 25-27), the standing
 * sign's floor consult and the wall sign's behind-consult (the
 * neighborChanged arms, StandingSignBlock lines 22-29 + WallSignBlock lines
 * 43-51).</p>
 */
public final class MaterialSolidity {

    private MaterialSolidity() {
    }

    /**
     * @return the reference's {@code getMaterial().isSolid()} for the
     *         registered kind — false for the AIR/FIRE/LIQUID/PORTAL and the
     *         PlantMaterial families (the flora, the saplings, the stems,
     *         the vines, the carpet, the snow layer, the DECORATION family:
     *         the torch, the rails, the redstone wire and torches, the
     *         repeaters, the comparators, the levers, the buttons, the
     *         tripwire, the ladders); true for everything else — the cobweb,
     *         the signs, the doors, the gates, the pressure plates, the
     *         moving piston included.
     */
    public static boolean isSolid(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return true; // an unregistered kind defaults solid, the base class
        }
        String value = id.value();
        if (value.equals("air") || value.equals("cave_air") || value.equals("void_air")) {
            return false; // Material.AIR (AirMaterial)
        }
        if (FireBlocks.isFire(id)) {
            return false; // Material.FIRE (AirMaterial)
        }
        if (FluidBlocks.isFluid(id)) {
            return false; // Material.WATER / LAVA (LiquidMaterial)
        }
        if (WorldSolidity.isNetherPortal(id) || WorldSolidity.isEndPortal(id)) {
            return false; // Material.PORTAL (PortalMaterial)
        }
        // The DECORATION family (PlantMaterial) — the torch, the rails, the
        // wire, the diodes, the levers, the buttons, the tripwire, the
        // ladders. The PRESSURE PLATES are carved out: their material is
        // the ctor's own parameter (WOOD/ROCK/GOLD/IRON — the base class,
        // solid), unlike every other redstone member's DECORATION.
        if (WorldSolidity.isLadder(id) || WorldSolidity.isRail(id)
                || WorldSolidity.isTripwireFamily(id)
                || id.equals(BuiltinBlocks.TORCH.identifier())
                || isDecorationRedstone(value)) {
            return false;
        }
        // The PLANT / REPLACEABLE_PLANT family (PlantMaterial): the flora
        // (the engine's own walk-through set: tall grass, the fern, the
        // dead bush, the flowers, the mushrooms, the sugar cane, the snow
        // layer, the lily, the cocoa, the double plants, the crop stages),
        // the six saplings, the wall vines (REPLACEABLE_PLANT — VineBlock's
        // own line), and the pumpkin/melon stems (PLANT — BlockStem's own).
        if (WorldSolidity.isFlora(id) || WoodVariantBlocks.isSapling(id)
                || value.equals("vine") || value.startsWith("vine_")
                || value.equals("pumpkin_stem") || value.startsWith("pumpkin_stem_")
                || value.equals("melon_stem") || value.startsWith("melon_stem_")) {
            return false;
        }
        // The carpet family (Material.CARPET — PlantMaterial).
        if (DyeFamilyBlocks.isCarpet(id)) {
            return false;
        }
        return true;
    }

    /**
     * The reference's {@code Material.blocksMovement()} (Material.java
     * lines 66-70 + the subclasses): the base {@code Material} blocks
     * bodies; {@code AirMaterial} (AIR, FIRE), {@code LiquidMaterial}
     * (WATER, LAVA), {@code PlantMaterial} (PLANT, REPLACEABLE_PLANT,
     * DECORATION, CARPET, SNOW_LAYER) and {@code PortalMaterial} (PORTAL)
     * override it to false — exactly the material-unsolid set — and the
     * COBWEB's own anonymous subclass (Material.java lines 36-41) keeps
     * {@code isSolid()} true while overriding this one alone: the
     * material-solid, movement-passable pair.
     *
     * <p>Consulted by the vine family (Wave VN-B — canGrowOn line 147's
     * second conjunct): a vine grows on every cube whose material still
     * moves bodies, the TNT and the cobweb's opposite laws.</p>
     */
    public static boolean blocksMovement(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return true; // an unregistered kind defaults solid, the base class
        }
        if (id.value().equals("cobweb")) {
            return false; // the anonymous material's own override, alone in its class
        }
        return isSolid(id); // every material-solid kind blocks bodies (the base class)
    }

    /**
     * The reference's translucent materials — {@code Material
     * .setTranslucent()}'s own rows (Material.java lines 13-30): LEAVES
     * (every wood's leaves), GLASS (the glass, the sixteen stained, the
     * glowstone, the sea lantern, the panes — GlowstoneBlock and
     * SeaLanternBlock's own registrations carry Material.GLASS, Block.java
     * lines 1191 + 1357), TNT, ICE, SNOW_LAYER and CACTUS. Packed ice is
     * NOT translucent (its own row, line 27) — the near-pair the ice
     * family's face-gain law turns on.
     *
     * <p>Consulted by the vine family (Wave VN-B — the face-gain consult
     * line 288's {@code material.isSolidBlocking()} = {@code !translucent
     * && blocksMovement()}): a vine GROWS on the leaves, the glowstone,
     * the sea lantern, the TNT and the ice (canGrowOn reads blocksMovement
     * alone) but never WRAPS a face bit toward any of them — only the
     * opaque cubes take the bit.</p>
     */
    public static boolean isTranslucentMaterial(Identifier id) {
        if (!id.namespace().equals("minecraft")) {
            return false; // the base class, never translucent
        }
        String value = id.value();
        // Material.LEAVES — every wood's leaves.
        if (value.endsWith("_leaves")) {
            return true;
        }
        // Material.GLASS — the glass family + the glowstone + the sea
        // lantern + the panes (the pane rows are redundant for the vine's
        // cube-consult, the material table's own completeness).
        if (value.equals("glass") || value.equals("glowstone")
                || value.equals("sea_lantern") || value.equals("glass_pane")
                || value.startsWith("stained_glass")) {
            return true;
        }
        // Material.TNT / ICE / SNOW_LAYER / CACTUS.
        return value.equals("tnt") || value.equals("ice")
                || value.equals("snow_layer") || value.equals("cactus");
    }

    /**
     * The DECORATION-material redstone members (the engine's redstone set
     * minus the pressure plates): the wire, the redstone torches, the
     * repeaters, the comparators, the levers, the buttons.
     */
    private static boolean isDecorationRedstone(String value) {
        return value.startsWith("redstone_wire")
                || value.startsWith("redstone_torch")
                || value.startsWith("unlit_redstone_torch")
                || value.startsWith("repeater_")
                || value.startsWith("powered_repeater_")
                || value.startsWith("comparator_")
                || value.startsWith("powered_comparator_")
                || value.startsWith("lever_")
                || value.startsWith("stone_button_")
                || value.startsWith("wooden_button_");
    }
}
