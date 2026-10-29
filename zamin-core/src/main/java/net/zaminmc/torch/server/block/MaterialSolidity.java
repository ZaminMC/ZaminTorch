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
