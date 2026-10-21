package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The huge-mushroom pair (Slice 40 — MushroomBlock 1:1 over the
 * reference's registrations): the brown mushroom block 99 and the red
 * mushroom block 100 (both Material.WOOD, {@code setStrength(0.2F)}
 * WOOD_SOUNDS, the plant item their drops walk).
 *
 * <p>The state vocabulary is the reference's own VARIANT band
 * (getStateFromMetadata: the fourteen ids — 0 all_inside, 1-9 the
 * directional pores and corners, 10 stem, 14 all_outside, 15 all_stem).
 * The bare identifier carries the ALL_OUTSIDE form — the reference's own
 * defaultState and getPlacementState (the item's placed shape and the
 * creative pick's {@code getPickItem} twin).</p>
 */
public final class MushroomBlocks {

    private MushroomBlocks() {
    }

    /** The pair: name -> wire id (Block.java's own rows). */
    public static final Map<String, Integer> MUSHROOMS = Map.of(
            "brown_mushroom_block", 99,
            "red_mushroom_block", 100);

    /** One VARIANT band member: the metadata id with its key. */
    public record Variant(int id, String key) { }

    /** The VARIANT band: the reference's own fourteen ids. */
    public static final List<Variant> VARIANTS = List.of(
            new Variant(0, "all_inside"),
            new Variant(1, "north_west"),
            new Variant(2, "north"),
            new Variant(3, "north_east"),
            new Variant(4, "west"),
            new Variant(5, "center"),
            new Variant(6, "east"),
            new Variant(7, "south_west"),
            new Variant(8, "south"),
            new Variant(9, "south_east"),
            new Variant(10, "stem"),
            new Variant(14, "all_outside"),
            new Variant(15, "all_stem"));

    /** The plant item each family's drop walk yields (getDropItem's plant). */
    public static Identifier plantOf(String family) {
        return Identifier.parse(family.equals("brown_mushroom_block")
                ? "minecraft:brown_mushroom" : "minecraft:red_mushroom");
    }

    /** The family's item identifier. */
    public static Identifier blockItem(String family) {
        return Identifier.parse("minecraft:" + family);
    }

    private static String displayOf(String family) {
        return family.equals("brown_mushroom_block")
                ? "Brown Mushroom Block" : "Red Mushroom Block";
    }

    /** One state's identifier: the bare name carries all_outside (the
     *  defaultState), every other variant its own suffix. */
    private static Identifier stateId(String family, int variant) {
        if (variant == 14) {
            return Identifier.parse("minecraft:" + family);
        }
        for (Variant row : VARIANTS) {
            if (row.id() == variant) {
                return Identifier.parse("minecraft:" + family + "_" + row.key());
            }
        }
        return Identifier.parse("minecraft:" + family);
    }

    /** One state of a family (the metadata band's exact member). */
    public static EngineBlockType state(String family, int variant) {
        return new EngineBlockType(stateId(family, variant),
                displayOf(family), variant);
    }

    /** Every registration this family owns (the 26 states). */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (String family : MUSHROOMS.keySet()) {
            for (Variant row : VARIANTS) {
                blocks.add(state(family, row.id()));
            }
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads
    // ------------------------------------------------------------------

    /** @return the mushroom family of a type (item or state), or null. */
    public static String familyOf(net.zaminmc.torch.block.BlockType type) {
        if (type == null || !type.identifier().namespace().equals("minecraft")) {
            return null;
        }
        String value = type.identifier().value();
        for (String family : MUSHROOMS.keySet()) {
            if (value.equals(family) || value.startsWith(family + "_")) {
                return family;
            }
        }
        return null;
    }

    /** @return whether the type is a huge-mushroom family's state. */
    public static boolean isMushroomBlock(net.zaminmc.torch.block.BlockType type) {
        return familyOf(type) != null;
    }

    /** @return whether the identifier string is a family's item. */
    public static boolean isMushroomItem(String identifier) {
        if (identifier == null) {
            return false;
        }
        String value = identifier.startsWith("minecraft:")
                ? identifier.substring("minecraft:".length()) : identifier;
        return MUSHROOMS.containsKey(value);
    }

    /**
     * The hand drop count (getBaseDropCount line 27:
     * {@code max(0, nextInt(10) - 7)}) — 0 to 2 of the plant.
     */
    public static int baseDropCount(java.util.Random random) {
        return Math.max(0, random.nextInt(10) - 7);
    }

    // ------------------------------------------------------------------
    // The plant family (VIII7/VIII8 — MushroomPlantBlock 1:1): the small
    // mushrooms' survival law, the random-tick spread, and the bone-meal
    // giant form
    // ------------------------------------------------------------------

    /** The plant pair: plant name -> its giant family (the plant field's own mapping). */
    static final java.util.Map<String, String> PLANTS = java.util.Map.of(
            "brown_mushroom", "brown_mushroom_block",
            "red_mushroom", "red_mushroom_block");

    /** @return whether the type is one of the two small plants. */
    public static boolean isMushroomPlant(net.zaminmc.torch.block.BlockType type) {
        if (type == null || !type.identifier().namespace().equals("minecraft")) {
            return false;
        }
        return PLANTS.containsKey(type.identifier().value());
    }

    /** @return the giant family a plant grows into (MushroomPlantBlock.grow's own pair), null when not a plant. */
    public static String giantFamilyOf(net.zaminmc.torch.block.BlockType type) {
        if (type == null || !type.identifier().namespace().equals("minecraft")) {
            return null;
        }
        return PLANTS.get(type.identifier().value());
    }

    /**
     * The survival law (MushroomPlantBlock.canSurvive lines 71-80): y inside
     * the world band; the cell below MYCELIUM answers true whole (the light
     * gate bypassed — the mushroom island's own law, a mushroom on mycelium
     * surviving any daylight); the cell below PODZOL answers true the same
     * way (the engine ships no podzol — DirtBlock's variant family is the
     * biome backlog's own slice, the counted row named in the docs);
     * otherwise the light gate — the combined light at the plant's own cell
     * under 13 (the reference's {@code getActualLight}, the chunk's stored
     * combined light — the engine's block-light/sky-light max convention) —
     * AND the cell below opaque ({@code canBePlacedOn}, PlantBlock's
     * isOpaque consult — the full-cube filter read).
     */
    public static boolean canPlantSurviveAt(net.zaminmc.torch.server.world.EngineWorld world,
            net.zaminmc.torch.block.BlockPosition pos) {
        if (pos.y() < net.zaminmc.torch.block.BlockPosition.MIN_Y
                || pos.y() > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
            return false;
        }
        net.zaminmc.torch.block.BlockType below = world.getBlock(pos.offset(0, -1, 0));
        if (below.identifier().equals(BuiltinBlocks.MYCELIUM.identifier())) {
            return true;
        }
        int light = Math.max(world.blockLightAt(pos), world.skyLightAt(pos));
        return light < 13 && BlockLightTable.filter(below) >= 15;
    }

    /**
     * The random-tick spread (MushroomPlantBlock.tick lines 43-68, 1:1): the
     * 1-in-25 pick; the density gate — the 9x3x9 box around the plant counts
     * the SAME-family plants (brown counts brown alone), a fifth neighbour
     * refusing the tick whole; then the four-step walk — every step the
     * target rolls {@code ±1 x, nextInt(2) - nextInt(2) y, ±1 z} (the
     * triangular y form — two rolls subtracted), a step landing on air over
     * surviving ground MOVES the walk's anchor, and the walk's final target
     * plants its copy when it too is air over surviving ground.
     */
    public static void spreadTick(net.zaminmc.torch.server.world.EngineWorld world,
            net.zaminmc.torch.block.BlockPosition pos, net.zaminmc.torch.block.BlockType type,
            java.util.Random random) {
        if (random.nextInt(25) != 0) {
            return;
        }
        // The density gate (lines 46-57): five same-family neighbours in the
        // 9x3x9 box refuse the spread (i = 5; --i <= 0 returns).
        int budget = 5;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (world.getBlock(pos.offset(dx, dy, dz)).equals(type)) {
                        if (--budget <= 0) {
                            return;
                        }
                    }
                }
            }
        }
        // The four-step walk (lines 59-68): the anchor moves to every
        // surviving target, the final target planting its copy.
        net.zaminmc.torch.block.BlockPosition anchor = pos;
        net.zaminmc.torch.block.BlockPosition target = anchor.offset(
                random.nextInt(3) - 1,
                random.nextInt(2) - random.nextInt(2),
                random.nextInt(3) - 1);
        for (int step = 0; step < 4; step++) {
            if (world.getBlock(target).equals(world.airType())
                    && canPlantSurviveAt(world, target)) {
                anchor = target;
            }
            target = anchor.offset(
                    random.nextInt(3) - 1,
                    random.nextInt(2) - random.nextInt(2),
                    random.nextInt(3) - 1);
        }
        if (world.getBlock(target).equals(world.airType())
                && canPlantSurviveAt(world, target)) {
            world.setBlock(target, type);
        }
    }

    /**
     * The bone-meal grow (MushroomPlantBlock.grow lines 84-98): the plant is
     * removed first, the giant feature places at its cell, and a FAILED
     * placement restores the plant whole (flag 3 — the engine's own block
     * write) — the bone-meal stack has already paid either way (the
     * Fertilizable pair's own law: canGrow is always true, so fertilize's
     * {@code stack.size--} runs outside the grow).
     */
    public static boolean growHuge(net.zaminmc.torch.server.world.EngineWorld world,
            net.zaminmc.torch.block.BlockPosition pos, net.zaminmc.torch.block.BlockType type,
            java.util.Random random) {
        String family = giantFamilyOf(type);
        if (family == null) {
            return false;
        }
        world.setBlock(pos, world.airType());
        if (!placeHuge(world, pos, family, random)) {
            world.setBlock(pos, type); // the restore (grow line 97)
            return false;
        }
        return true;
    }

    /**
     * The giant form (HugeMushroomFeature.place lines 27-158, 1:1): the
     * height {@code nextInt(3) + 4}; the fit walk — every cell of the
     * shaft-and-cap band must be AIR or LEAVES (the bottom four rows a 1x1
     * column, the rows above the 7x7 box) and the base must be DIRT, GRASS
     * or MYCELIUM; then the cap band — the brown's own full 3-row cap (half
     * 3) against the red's flat 1-row cap over the 3-row skirt ({@code k2 =
     * y + i - 3}), the variant lattice ({@code j2 = 5} shifted -1/+1 along
     * the x edges and -3/+3 along the z edges), the skipped corners (the
     * brown every row, the red below the top row), the corner-adjacent
     * NORTH_WEST/NORTH_EAST/SOUTH_WEST/SOUTH_EAST overrides, the CENTER
     * interior's ALL_INSIDE flip below the top, and the opaque-refusing
     * write; then the STEM column ({@code height} tall, the same opaque
     * refusal).
     */
    static boolean placeHuge(net.zaminmc.torch.server.world.EngineWorld world,
            net.zaminmc.torch.block.BlockPosition pos, String family, java.util.Random random) {
        int height = random.nextInt(3) + 4;
        boolean fits = true;
        if (pos.y() >= 1 && pos.y() + height + 1 < 256) {
            // The fit walk (lines 34-56): the whole band must be air or
            // leaves — a single solid cell refuses the placement.
            for (int y = pos.y(); y <= pos.y() + 1 + height; y++) {
                int r = 3;
                if (y <= pos.y() + 3) {
                    r = 0;
                }
                for (int x = pos.x() - r; x <= pos.x() + r && fits; x++) {
                    for (int z = pos.z() - r; z <= pos.z() + r && fits; z++) {
                        if (y >= 0 && y < 256) {
                            if (!TreeFeatures.isAirOrLeaves(world,
                                    new net.zaminmc.torch.block.BlockPosition(x, y, z))) {
                                fits = false;
                            }
                        } else {
                            fits = false;
                        }
                    }
                }
            }
            if (!fits) {
                return false;
            }
            // The base gate (lines 58-61): dirt, grass or mycelium below.
            net.zaminmc.torch.block.BlockType base = world.getBlock(pos.offset(0, -1, 0));
            boolean onSoil = base.identifier().equals(BuiltinBlocks.DIRT.identifier())
                    || base.identifier().equals(BuiltinBlocks.GRASS_BLOCK.identifier())
                    || base.identifier().equals(BuiltinBlocks.MYCELIUM.identifier());
            if (!onSoil) {
                return false;
            }

            boolean brown = family.equals("brown_mushroom_block");
            int top = pos.y() + height;
            // The cap band (lines 63-148): the brown's cap starts at the top
            // (half 3, three rows); the red's starts three lower (its skirt).
            int capStart = brown ? top : pos.y() + height - 3;
            for (int y = capStart; y <= top; y++) {
                int half = 1;
                if (y < top) {
                    half++;
                }
                if (brown) {
                    half = 3;
                }

                int minX = pos.x() - half;
                int maxX = pos.x() + half;
                int minZ = pos.z() - half;
                int maxZ = pos.z() + half;

                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        int variant = 5; // CENTER
                        if (x == minX) {
                            variant--;
                        } else if (x == maxX) {
                            variant++;
                        }
                        if (z == minZ) {
                            variant -= 3;
                        } else if (z == maxZ) {
                            variant += 3;
                        }

                        if (brown || y < top) {
                            // The skipped corners (lines 92-94): the brown
                            // every row, the red below the top row.
                            if ((x == minX || x == maxX) && (z == minZ || z == maxZ)) {
                                continue;
                            }
                            // The corner-adjacent overrides (lines 96-127):
                            // the eight cells beside each corner carry their
                            // corner's own pore face.
                            if (x == pos.x() - (half - 1) && z == minZ) {
                                variant = 1; // NORTH_WEST
                            }
                            if (x == minX && z == pos.z() - (half - 1)) {
                                variant = 1; // NORTH_WEST
                            }
                            if (x == pos.x() + (half - 1) && z == minZ) {
                                variant = 3; // NORTH_EAST
                            }
                            if (x == maxX && z == pos.z() - (half - 1)) {
                                variant = 3; // NORTH_EAST
                            }
                            if (x == pos.x() - (half - 1) && z == maxZ) {
                                variant = 7; // SOUTH_WEST
                            }
                            if (x == minX && z == pos.z() + (half - 1)) {
                                variant = 7; // SOUTH_WEST
                            }
                            if (x == pos.x() + (half - 1) && z == maxZ) {
                                variant = 9; // SOUTH_EAST
                            }
                            if (x == maxX && z == pos.z() + (half - 1)) {
                                variant = 9; // SOUTH_EAST
                            }
                        }

                        // The interior flip (lines 129-131): a CENTER cell
                        // below the top row is the ALL_INSIDE form.
                        if (variant == 5 && y < top) {
                            variant = 0; // ALL_INSIDE
                        }

                        // The write gate (lines 133-141): the reference's own
                        // first arm is the always-false {@code y >= y + i - 1}
                        // at every height band (i >= 4), so the ALL_INSIDE
                        // interior never writes — kept literally 1:1.
                        if (pos.y() >= pos.y() + height - 1 || variant != 0) {
                            net.zaminmc.torch.block.BlockPosition at =
                                    new net.zaminmc.torch.block.BlockPosition(x, y, z);
                            if (!TreeFeatures.isOpaque(world, at)) {
                                world.setBlock(at, state(family, variant));
                            }
                        }
                    }
                }
            }

            // The stem column (lines 150-157): height tall, the opaque
            // refusal, the STEM variant.
            for (int i = 0; i < height; i++) {
                net.zaminmc.torch.block.BlockPosition at = pos.offset(0, i, 0);
                if (!TreeFeatures.isOpaque(world, at)) {
                    world.setBlock(at, state(family, 10)); // STEM
                }
            }

            return true;
        }
        return false;
    }
}
