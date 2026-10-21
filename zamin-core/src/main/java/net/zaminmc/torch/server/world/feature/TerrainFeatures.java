package net.zaminmc.torch.server.world.feature;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.world.structure.StructureWorld;
import net.zaminmc.torch.util.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The terrain features (the VIII14 decorators slice — the reference's
 * terrain placement classes, 1:1): the lake (water + lava with the frozen
 * top and the stone ring), the dungeon (the 5x5-7x7 mossy room with its
 * spawner and loot chests), the desert well, the ice spike, the liquid
 * fall (the populate walk's own lake stage), the liquid pocket (the
 * nether's), and the bonus chest.
 *
 * <p><b>The counted notes</b> (the named adaptations the slice's PR
 * records): the flowing-liquid forms ride the engine's source stand-in
 * (the structures' own precedent — StructurePalette's water row); the
 * gen-time tick ({@code world.tickBlockNow}) drops — the flow spread is
 * the runtime fluid system's own work; the lake's sky-light consult
 * reads the sky column (the chunk is not lit at generation).</p>
 */
final class TerrainFeatures {

    private TerrainFeatures() {
    }

    /** Whether the cell sees the sky (the lake's {@code getLight(SKY) > 0}). */
    private static boolean isSkyLit(FeatureWorld world, int x, int y, int z) {
        for (int above = y + 1; above < 256; above++) {
            if (world.isOpaqueAt(x, above, z)) {
                return false;
            }
        }
        return true;
    }

    /** The liquid-kind consult (the lake's own two arms). */
    private static boolean isWater(BlockType liquid) {
        return liquid.identifier().value().equals("water");
    }

    /** The liquid-kind consult (the lava ring's own arm). */
    private static boolean isLava(BlockType liquid) {
        return liquid.identifier().value().equals("lava");
    }

    // ---------------------------------------------------------------- the lake

    /**
     * The lake (LakeFeature 1:1, lines 13-146): the -8 base shift, the
     * air drop-down with the y 5 floor and the -4 sink, the 4-7 ellipsoid
     * rolls, the wall material gates (a liquid at the dome refuses whole,
     * a non-solid non-liquid wall cell at the bowl refuses), the fill (the
     * bowl's liquid, the dome's air), the dirt-to-grass (or mycelium) cap
     * under the open sky, the lava ring, and the water's frozen top (the
     * temperature law — {@code canFreeze} with the always-dark block light
     * of an unlit chunk).
     */
    static final class LakeFeature implements FloraFeatures.Feature {
        private final BlockType liquid;

        LakeFeature(BlockType liquid) {
            this.liquid = liquid;
        }

        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            x -= 8;
            z -= 8;
            while (y > 5 && world.isAirAt(x, y, z)) {
                y--;
            }
            if (y <= 4) {
                return false;
            }
            y -= 4;
            boolean[] shape = new boolean[2048];
            int ellipses = random.nextInt(4) + 4;
            for (int i = 0; i < ellipses; i++) {
                double d0 = random.nextDouble() * 6.0 + 3.0;
                double d1 = random.nextDouble() * 4.0 + 2.0;
                double d2 = random.nextDouble() * 6.0 + 3.0;
                double d3 = random.nextDouble() * (16.0 - d0 - 2.0) + 1.0 + d0 / 2.0;
                double d4 = random.nextDouble() * (8.0 - d1 - 4.0) + 2.0 + d1 / 2.0;
                double d5 = random.nextDouble() * (16.0 - d2 - 2.0) + 1.0 + d2 / 2.0;
                for (int l = 1; l < 15; l++) {
                    for (int i1 = 1; i1 < 15; i1++) {
                        for (int j1 = 1; j1 < 7; j1++) {
                            double d6 = (l - d3) / (d0 / 2.0);
                            double d7 = (j1 - d4) / (d1 / 2.0);
                            double d8 = (i1 - d5) / (d2 / 2.0);
                            if (d6 * d6 + d7 * d7 + d8 * d8 < 1.0) {
                                shape[(l * 16 + i1) * 8 + j1] = true;
                            }
                        }
                    }
                }
            }
            // lines 63-85: the wall gates.
            for (int k1 = 0; k1 < 16; k1++) {
                for (int l2 = 0; l2 < 16; l2++) {
                    for (int k = 0; k < 8; k++) {
                        boolean wall = !shape[(k1 * 16 + l2) * 8 + k]
                                && (k1 < 15 && shape[((k1 + 1) * 16 + l2) * 8 + k]
                                || k1 > 0 && shape[((k1 - 1) * 16 + l2) * 8 + k]
                                || l2 < 15 && shape[(k1 * 16 + l2 + 1) * 8 + k]
                                || l2 > 0 && shape[(k1 * 16 + (l2 - 1)) * 8 + k]
                                || k < 7 && shape[(k1 * 16 + l2) * 8 + k + 1]
                                || k > 0 && shape[(k1 * 16 + l2) * 8 + (k - 1)]);
                        if (wall) {
                            BlockType at = world.blockAt(x + k1, y + k, z + l2);
                            boolean liquidAt = world.isLiquidAt(x + k1, y + k, z + l2);
                            if (k >= 4 && liquidAt) {
                                return false;
                            }
                            if (k < 4 && !world.isSolidAt(x + k1, y + k, z + l2) && !at.equals(liquid)) {
                                return false;
                            }
                        }
                    }
                }
            }
            // lines 87-98: the fill (the bowl's liquid, the dome's air).
            for (int l1 = 0; l1 < 16; l1++) {
                for (int i3 = 0; i3 < 16; i3++) {
                    for (int i4 = 0; i4 < 8; i4++) {
                        if (shape[(l1 * 16 + i3) * 8 + i4]) {
                            world.setBlockAt(x + l1, y + i4, z + i3,
                                    i4 >= 4 ? BuiltinBlocks.AIR : liquid);
                        }
                    }
                }
            }
            // lines 100-118: the dirt-to-grass cap (the mycelium consult).
            for (int i2 = 0; i2 < 16; i2++) {
                for (int j3 = 0; j3 < 16; j3++) {
                    for (int j4 = 4; j4 < 8; j4++) {
                        if (shape[(i2 * 16 + j3) * 8 + j4]) {
                            int capX = x + i2;
                            int capY = y + j4 - 1;
                            int capZ = z + j3;
                            if (world.blockAt(capX, capY, capZ).equals(BuiltinBlocks.DIRT)
                                    && isSkyLit(world, x + i2, y + j4, z + j3)) {
                                world.setBlockAt(capX, capY, capZ,
                                        world.biomeAt(capX, capZ).surfaceLegacyId() == 110
                                                ? BuiltinBlocks.MYCELIUM
                                                : BuiltinBlocks.GRASS_BLOCK);
                            }
                        }
                    }
                }
            }
            // lines 120-136: the lava ring.
            if (isLava(liquid)) {
                for (int j2 = 0; j2 < 16; j2++) {
                    for (int k3 = 0; k3 < 16; k3++) {
                        for (int k4 = 0; k4 < 8; k4++) {
                            boolean wall = !shape[(j2 * 16 + k3) * 8 + k4]
                                    && (j2 < 15 && shape[((j2 + 1) * 16 + k3) * 8 + k4]
                                    || j2 > 0 && shape[((j2 - 1) * 16 + k3) * 8 + k4]
                                    || k3 < 15 && shape[(j2 * 16 + k3 + 1) * 8 + k4]
                                    || k3 > 0 && shape[(j2 * 16 + (k3 - 1)) * 8 + k4]
                                    || k4 < 7 && shape[(j2 * 16 + k3) * 8 + k4 + 1]
                                    || k4 > 0 && shape[(j2 * 16 + k3) * 8 + (k4 - 1)]);
                            if (wall && (k4 < 4 || random.nextInt(2) != 0)
                                    && world.isSolidAt(x + j2, y + k4, z + k3)) {
                                world.setBlockAt(x + j2, y + k4, z + k3, BuiltinBlocks.STONE);
                            }
                        }
                    }
                }
            }
            // lines 138-145: the water's frozen top (the temperature law).
            if (isWater(liquid)) {
                for (int k2 = 0; k2 < 16; k2++) {
                    for (int l3 = 0; l3 < 16; l3++) {
                        int topX = x + k2;
                        int topZ = z + l3;
                        if (world.biomeAt(topX, topZ).temperatureAt(topX, y + 4, topZ) <= 0.15F
                                && world.blockAt(topX, y + 4, topZ).equals(liquid)) {
                            world.setBlockAt(topX, y + 4, topZ, BuiltinBlocks.ICE);
                        }
                    }
                }
            }
            return true;
        }
    }

    // -------------------------------------------------------------- the dungeon

    /**
     * The dungeon's loot table (DungeonFeature lines 24-41, 1:1 — the
     * reference's own fifteen rows; the enchanted book rides addAll's own
     * tail in the roll walk).
     */
    private static final List<LootRow> DUNGEON_LOOT = List.of(
            new LootRow("minecraft:saddle", 1, 1, 10),
            new LootRow("minecraft:iron_ingot", 1, 4, 10),
            new LootRow("minecraft:bread", 1, 1, 10),
            new LootRow("minecraft:wheat", 1, 4, 10),
            new LootRow("minecraft:gunpowder", 1, 4, 10),
            new LootRow("minecraft:string", 1, 4, 10),
            new LootRow("minecraft:bucket", 1, 1, 10),
            new LootRow("minecraft:golden_apple", 1, 1, 1),
            new LootRow("minecraft:redstone", 1, 4, 10),
            new LootRow("minecraft:record_13", 1, 1, 4),
            new LootRow("minecraft:record_cat", 1, 1, 4),
            new LootRow("minecraft:name_tag", 1, 1, 10),
            new LootRow("minecraft:golden_horse_armor", 1, 1, 2),
            new LootRow("minecraft:iron_horse_armor", 1, 1, 5),
            new LootRow("minecraft:diamond_horse_armor", 1, 1, 1));

    /** The dungeon's spawner table (line 21): the four rolls. */
    private static final MobType[] DUNGEON_TYPES = {
            MobType.SKELETON, MobType.ZOMBIE, MobType.ZOMBIE, MobType.SPIDER};

    /** One loot row (the reference's LootEntry shape). */
    private record LootRow(String itemId, int min, int max, int weight) {
    }

    /**
     * The dungeon (DungeonFeature 1:1, lines 43-140): the 2-3-cell
     * half-widths (the 5x5-7x7 floor), the solid floor/ceiling gate with
     * the wall-opening count band 1-5, the mossy-at-3-in-4 floor shell
     * with the cobblestone band, the inner clearing, the two chest
     * placements (three attempts each, the exactly-one-solid-neighbor
     * law, the eight-roll loot walk with the enchanted book tail), and
     * the typed spawner at the center.
     */
    static final class DungeonFeature implements FloraFeatures.Feature {
        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            int j = random.nextInt(2) + 2;
            int k = -j - 1;
            int l = j + 1;
            int k1 = random.nextInt(2) + 2;
            int l1 = -k1 - 1;
            int i2 = k1 + 1;
            int openings = 0;
            for (int k2 = k; k2 <= l; k2++) {
                for (int l2 = -1; l2 <= 4; l2++) {
                    for (int i3 = l1; i3 <= i2; i3++) {
                        boolean solid = world.isSolidAt(x + k2, y + l2, z + i3);
                        if (l2 == -1 && !solid) {
                            return false;
                        }
                        if (l2 == 4 && !solid) {
                            return false;
                        }
                        if ((k2 == k || k2 == l || i3 == l1 || i3 == i2) && l2 == 0
                                && world.isAirAt(x + k2, y, z + i3)
                                && world.isAirAt(x + k2, y + 1, z + i3)) {
                            openings++;
                        }
                    }
                }
            }
            if (openings >= 1 && openings <= 5) {
                for (int k3 = k; k3 <= l; k3++) {
                    for (int i4 = 3; i4 >= -1; i4--) {
                        for (int k4 = l1; k4 <= i2; k4++) {
                            if (k3 != k && i4 != -1 && k4 != l1 && k3 != l && i4 != 4 && k4 != i2) {
                                if (!world.blockAt(x + k3, y + i4, z + k4)
                                        .equals(BuiltinBlocks.CHEST)) {
                                    world.setBlockAt(x + k3, y + i4, z + k4, BuiltinBlocks.AIR);
                                }
                            } else if (y + i4 >= 0
                                    && !world.isSolidAt(x + k3, y + i4 - 1, z + k4)) {
                                world.setBlockAt(x + k3, y + i4, z + k4, BuiltinBlocks.AIR);
                            } else if (world.isSolidAt(x + k3, y + i4, z + k4)
                                    && !world.blockAt(x + k3, y + i4, z + k4)
                                            .equals(BuiltinBlocks.CHEST)) {
                                if (i4 == -1 && random.nextInt(4) != 0) {
                                    world.setBlockAt(x + k3, y + i4, z + k4,
                                            BuiltinBlocks.MOSSY_COBBLESTONE);
                                } else {
                                    world.setBlockAt(x + k3, y + i4, z + k4,
                                            BuiltinBlocks.COBBLESTONE);
                                }
                            }
                        }
                    }
                }
                for (int l3 = 0; l3 < 2; l3++) {
                    // The chest's three attempts (lines 88-116).
                    for (int j4 = 0; j4 < 3; j4++) {
                        int cx = x + random.nextInt(j * 2 + 1) - j;
                        int cz = z + random.nextInt(k1 * 2 + 1) - k1;
                        if (world.isAirAt(cx, y, cz)) {
                            int solidNeighbors = 0;
                            if (world.isSolidAt(cx - 1, y, cz)) {
                                solidNeighbors++;
                            }
                            if (world.isSolidAt(cx + 1, y, cz)) {
                                solidNeighbors++;
                            }
                            if (world.isSolidAt(cx, y, cz - 1)) {
                                solidNeighbors++;
                            }
                            if (world.isSolidAt(cx, y, cz + 1)) {
                                solidNeighbors++;
                            }
                            if (solidNeighbors == 1) {
                                rollDungeonChest(world, random, cx, y, cz);
                                break;
                            }
                        }
                    }
                }
                world.setBlockAt(x, y, z, BuiltinBlocks.MOB_SPAWNER);
                world.placeMobSpawner(x, y, z, DUNGEON_TYPES[random.nextInt(DUNGEON_TYPES.length)]);
                return true;
            }
            return false;
        }

        /**
         * The chest + its loot walk (LootEntry.addLoot over the table with
         * the enchanted book's addAll tail, the mineshaft's own landed
         * shape): eight weighted picks, the count band, the random slot of
         * the 27, the over-stack split.
         */
        private static void rollDungeonChest(FeatureWorld world, Random random,
                                             int x, int y, int z) {
            world.setBlockAt(x, y, z, BuiltinBlocks.CHEST);
            // The book row rolls FIRST (the reference's argument order):
            // the level-30 offer walk over the plain book (the stronghold
            // mineshaft's own landed shape, written locally — the structure
            // package's helper is its own lane's).
            var offers = net.zaminmc.torch.server.enchantment.EnchantmentHelper.buildOffers(
                    random, net.zaminmc.torch.item.ItemStack.of(
                            net.zaminmc.torch.server.item.BuiltinItems.BOOK), 30);
            java.util.LinkedHashMap<Integer, Integer> enchantments = new java.util.LinkedHashMap<>();
            if (offers != null) {
                for (var offer : offers) {
                    enchantments.merge(offer.enchantment().id, offer.level(), Math::max);
                }
            }
            int bookWeight = 1;
            Map<Integer, StructureWorld.GenLootSlot> slots = new LinkedHashMap<>();
            int totalWeight = 0;
            for (LootRow row : DUNGEON_LOOT) {
                totalWeight += row.weight();
            }
            for (int i = 0; i < 8; i++) {
                int pick = random.nextInt(totalWeight + bookWeight);
                LootRow chosen = null;
                for (LootRow row : DUNGEON_LOOT) {
                    pick -= row.weight();
                    if (pick < 0) {
                        chosen = row;
                        break;
                    }
                }
                if (chosen == null) {
                    int slot = random.nextInt(27);
                    slots.put(slot, new StructureWorld.GenLootSlot(slot,
                            "minecraft:enchanted_book", 1,
                            enchantments.isEmpty() ? null : enchantments));
                    continue;
                }
                int count = chosen.min()
                        + random.nextInt(chosen.max() - chosen.min() + 1);
                int maxStack = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                                Identifier.parse(chosen.itemId()))
                        .map(net.zaminmc.torch.item.ItemType::maxStackSize).orElse(64);
                if (maxStack >= count) {
                    int slot = random.nextInt(27);
                    slots.put(slot, new StructureWorld.GenLootSlot(slot, chosen.itemId(), count));
                } else {
                    for (int k = 0; k < count; k++) {
                        int slot = random.nextInt(27);
                        slots.put(slot, new StructureWorld.GenLootSlot(slot, chosen.itemId(), 1));
                    }
                }
            }
            world.placeGenChest(x, y, z, List.copyOf(slots.values()));
        }
    }

    // ---------------------------------------------------------- the desert well

    /**
     * The desert well (DesertWellFeature 1:1, lines 19-89): the drop-down,
     * the sand gate with the two-cell floor check beneath the rim, the
     * 5x5 slab base pair, the water cross, the rim with its four sand
     * slab corners, the 3x3 cap, and the four posts (the sand-slab
     * variant — the engine's sandstone slab over 44/1; the flowing water
     * rides the source stand-in).
     */
    static final class DesertWellFeature implements FloraFeatures.Feature {
        private static final BlockType SLAB = BuiltinBlocks.SANDSTONE;
        private static final BlockType BASE = BuiltinBlocks.SANDSTONE_SLAB;
        private final BlockType water;

        DesertWellFeature(BlockType water) {
            this.water = water;
        }

        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            while (world.isAirAt(x, y, z) && y > 2) {
                y--;
            }
            if (!world.blockAt(x, y, z).equals(BuiltinBlocks.SAND)) {
                return false;
            }
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    if (world.isAirAt(x + i, y - 1, z + j)
                            && world.isAirAt(x + i, y - 2, z + j)) {
                        return false;
                    }
                }
            }
            for (int l = -1; l <= 0; l++) {
                for (int l1 = -2; l1 <= 2; l1++) {
                    for (int k = -2; k <= 2; k++) {
                        world.setBlockAt(x + l1, y + l, z + k, SLAB);
                    }
                }
            }
            world.setBlockAt(x, y, z, water);
            for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                world.setBlockAt(x + dir[0], y, z + dir[1], water);
            }
            for (int i1 = -2; i1 <= 2; i1++) {
                for (int i2 = -2; i2 <= 2; i2++) {
                    if (i1 == -2 || i1 == 2 || i2 == -2 || i2 == 2) {
                        world.setBlockAt(x + i1, y + 1, z + i2, SLAB);
                    }
                }
            }
            world.setBlockAt(x + 2, y + 1, z, BASE);
            world.setBlockAt(x - 2, y + 1, z, BASE);
            world.setBlockAt(x, y + 1, z + 2, BASE);
            world.setBlockAt(x, y + 1, z - 2, BASE);
            for (int j1 = -1; j1 <= 1; j1++) {
                for (int j2 = -1; j2 <= 1; j2++) {
                    if (j1 == 0 && j2 == 0) {
                        world.setBlockAt(x + j1, y + 4, z + j2, SLAB);
                    } else {
                        world.setBlockAt(x + j1, y + 4, z + j2, BASE);
                    }
                }
            }
            for (int k1 = 1; k1 <= 3; k1++) {
                world.setBlockAt(x - 1, y + k1, z - 1, SLAB);
                world.setBlockAt(x - 1, y + k1, z + 1, SLAB);
                world.setBlockAt(x + 1, y + k1, z - 1, SLAB);
                world.setBlockAt(x + 1, y + k1, z + 1, SLAB);
            }
            return true;
        }
    }

    // ------------------------------------------------------------ the ice spike

    /**
     * The ice spike (IceSpikeFeature 1:1, lines 13-91): the snow gate, the
     * rand(4) lift, the 7-10 height with the i/4+rand(2) width, the rare
     * 1-in-60 +10..40 pillar jump, the taper circle law with the 0.25
     * inset and the 0.75 edge roll, the down-mirror over the lower half,
     * the packed-ice band (air/dirt/snow/ice), and the base descent with
     * the 50-cell walk and the corner re-rolls.
     */
    static final class IceSpikeFeature implements FloraFeatures.Feature {
        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            while (world.isAirAt(x, y, z) && y > 2) {
                y--;
            }
            if (!world.blockAt(x, y, z).equals(BuiltinBlocks.SNOW)) {
                return false;
            }
            y += random.nextInt(4);
            int i = random.nextInt(4) + 7;
            int j = i / 4 + random.nextInt(2);
            if (j > 1 && random.nextInt(60) == 0) {
                y += 10 + random.nextInt(30);
            }
            for (int k = 0; k < i; k++) {
                float f = (1.0F - (float) k / i) * j;
                int l = (int) Math.ceil(f);
                for (int i1 = -l; i1 <= l; i1++) {
                    float f1 = Math.abs(i1) - 0.25F;
                    for (int j1 = -l; j1 <= l; j1++) {
                        float f2 = Math.abs(j1) - 0.25F;
                        if ((i1 == 0 && j1 == 0 || f1 * f1 + f2 * f2 <= f * f)
                                && (i1 != -l && i1 != l && j1 != -l && j1 != l
                                        || !(random.nextFloat() > 0.75F))) {
                            placeIceColumn(world, x + i1, y + k, z + j1);
                            if (k != 0 && l > 1) {
                                placeIceColumn(world, x + i1, y - k, z + j1);
                            }
                        }
                    }
                }
            }
            int k1 = Math.max(0, Math.min(j - 1, 1));
            for (int l1 = -k1; l1 <= k1; l1++) {
                for (int i2 = -k1; i2 <= k1; i2++) {
                    int bx = x + l1;
                    int bz = z + i2;
                    int j2 = 50;
                    if (Math.abs(l1) == 1 && Math.abs(i2) == 1) {
                        j2 = random.nextInt(5);
                    }
                    int by = y - 1;
                    while (by > 50) {
                        BlockType at = world.blockAt(bx, by, bz);
                        String value = at.identifier().value();
                        if (!at.equals(BuiltinBlocks.AIR)
                                && !value.equals("dirt") && !value.equals("snow")
                                && !value.equals("ice")
                                && !value.equals("packed_ice")) {
                            break;
                        }
                        world.setBlockAt(bx, by, bz, BuiltinBlocks.PACKED_ICE);
                        by--;
                        if (--j2 <= 0) {
                            by -= random.nextInt(5) + 1;
                            j2 = random.nextInt(5);
                        }
                    }
                }
            }
            return true;
        }

        /** The packed-ice band consult (air/dirt/snow/ice). */
        private static void placeIceColumn(FeatureWorld world, int x, int y, int z) {
            BlockType at = world.blockAt(x, y, z);
            String value = at.identifier().value();
            if (at.equals(BuiltinBlocks.AIR) || value.equals("dirt")
                    || value.equals("snow") || value.equals("ice")) {
                world.setBlockAt(x, y, z, BuiltinBlocks.PACKED_ICE);
            }
        }
    }

    // ---------------------------------------------------------- the liquid fall

    /**
     * The liquid fall (LiquidFallFeature 1:1, lines 13-64): the stone
     * above and below, the air-or-stone cell, the three-stone-one-air
     * side law, and the source-block placement (the flowing form's
     * gen-time tick is the runtime fluid system's own work — the named
     * adaptation, the structures' own water precedent).
     */
    static final class LiquidFallFeature implements FloraFeatures.Feature {
        private final BlockType liquid;

        LiquidFallFeature(BlockType liquid) {
            this.liquid = liquid;
        }

        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            if (!world.blockAt(x, y + 1, z).equals(BuiltinBlocks.STONE)) {
                return false;
            }
            if (!world.blockAt(x, y - 1, z).equals(BuiltinBlocks.STONE)) {
                return false;
            }
            BlockType at = world.blockAt(x, y, z);
            if (!world.isAirAt(x, y, z) && !at.equals(BuiltinBlocks.STONE)) {
                return false;
            }
            int stone = 0;
            if (world.blockAt(x - 1, y, z).equals(BuiltinBlocks.STONE)) {
                stone++;
            }
            if (world.blockAt(x + 1, y, z).equals(BuiltinBlocks.STONE)) {
                stone++;
            }
            if (world.blockAt(x, y, z - 1).equals(BuiltinBlocks.STONE)) {
                stone++;
            }
            if (world.blockAt(x, y, z + 1).equals(BuiltinBlocks.STONE)) {
                stone++;
            }
            int air = 0;
            if (world.isAirAt(x - 1, y, z)) {
                air++;
            }
            if (world.isAirAt(x + 1, y, z)) {
                air++;
            }
            if (world.isAirAt(x, y, z - 1)) {
                air++;
            }
            if (world.isAirAt(x, y, z + 1)) {
                air++;
            }
            if (stone == 3 && air == 1) {
                world.setBlockAt(x, y, z, liquid);
            }
            return true;
        }
    }

    // -------------------------------------------------------- the liquid pocket

    /**
     * The liquid pocket (LiquidPocketFeature 1:1, lines 13-70): the
     * netherrack above, the air-or-netherrack cell, the side/floor
     * netherrack count against the air count, the exposed law — the
     * nether generator's own stage (ported for the family's completeness,
     * unwired in the overworld walk).
     */
    static final class LiquidPocketFeature implements FloraFeatures.Feature {
        private final BlockType liquid;
        private final boolean canBeExposedToAir;

        LiquidPocketFeature(BlockType liquid, boolean canBeExposedToAir) {
            this.liquid = liquid;
            this.canBeExposedToAir = canBeExposedToAir;
        }

        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            if (!world.blockAt(x, y + 1, z).identifier().value().equals("netherrack")) {
                return false;
            }
            BlockType at = world.blockAt(x, y, z);
            if (!world.isAirAt(x, y, z)
                    && !at.identifier().value().equals("netherrack")) {
                return false;
            }
            int netherrack = 0;
            if (world.blockAt(x - 1, y, z).identifier().value().equals("netherrack")) {
                netherrack++;
            }
            if (world.blockAt(x + 1, y, z).identifier().value().equals("netherrack")) {
                netherrack++;
            }
            if (world.blockAt(x, y, z - 1).identifier().value().equals("netherrack")) {
                netherrack++;
            }
            if (world.blockAt(x, y, z + 1).identifier().value().equals("netherrack")) {
                netherrack++;
            }
            if (world.blockAt(x, y - 1, z).identifier().value().equals("netherrack")) {
                netherrack++;
            }
            int air = 0;
            if (world.isAirAt(x - 1, y, z)) {
                air++;
            }
            if (world.isAirAt(x + 1, y, z)) {
                air++;
            }
            if (world.isAirAt(x, y, z - 1)) {
                air++;
            }
            if (world.isAirAt(x, y, z + 1)) {
                air++;
            }
            if (world.isAirAt(x, y - 1, z)) {
                air++;
            }
            if (!canBeExposedToAir && netherrack == 4 && air == 1 || netherrack == 5) {
                world.setBlockAt(x, y, z, liquid);
            }
            return true;
        }
    }

    // ---------------------------------------------------------- the bonus chest

    /**
     * The bonus chest's loot table (ServerWorld lines 98-109, 1:1).
     */
    private static final List<LootRow> BONUS_CHEST_LOOT = List.of(
            new LootRow("minecraft:stick", 1, 3, 10),
            new LootRow("minecraft:oak_planks", 1, 3, 10),
            new LootRow("minecraft:oak_log", 1, 3, 10),
            new LootRow("minecraft:stone_axe", 1, 1, 3),
            new LootRow("minecraft:wooden_axe", 1, 1, 5),
            new LootRow("minecraft:stone_pickaxe", 1, 1, 3),
            new LootRow("minecraft:wooden_pickaxe", 1, 1, 5),
            new LootRow("minecraft:apple", 2, 3, 5),
            new LootRow("minecraft:bread", 2, 3, 3),
            new LootRow("minecraft:spruce_log", 1, 3, 10));

    /**
     * The bonus chest (BonusChestFeature 1:1, lines 17-71): the
     * air/leaf drop-down, the four attempts with the solid-top gate, the
     * chest with its ten-roll loot walk, and the four torches — the
     * world-type gate's own feature (the engine carries no bonus-chest
     * world option today; ported for the family's completeness).
     */
    static final class BonusChestFeature implements FloraFeatures.Feature {
        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            while (world.isAirAt(x, y, z) && y > 1) {
                y--;
            }
            if (y < 1) {
                return false;
            }
            y++;
            for (int i = 0; i < 4; i++) {
                int bx = x + random.nextInt(4) - random.nextInt(4);
                int by = y + random.nextInt(3) - random.nextInt(3);
                int bz = z + random.nextInt(4) - random.nextInt(4);
                if (world.isAirAt(bx, by, bz) && world.isSolidAt(bx, by - 1, bz)) {
                    world.setBlockAt(bx, by, bz, BuiltinBlocks.CHEST);
                    Map<Integer, StructureWorld.GenLootSlot> slots = new LinkedHashMap<>();
                    int totalWeight = 0;
                    for (LootRow row : BONUS_CHEST_LOOT) {
                        totalWeight += row.weight();
                    }
                    for (int roll = 0; roll < 10; roll++) {
                        int pick = random.nextInt(totalWeight);
                        LootRow chosen = BONUS_CHEST_LOOT.get(BONUS_CHEST_LOOT.size() - 1);
                        for (LootRow row : BONUS_CHEST_LOOT) {
                            pick -= row.weight();
                            if (pick < 0) {
                                chosen = row;
                                break;
                            }
                        }
                        int count = chosen.min()
                                + random.nextInt(chosen.max() - chosen.min() + 1);
                        int slot = random.nextInt(27);
                        slots.put(slot, new StructureWorld.GenLootSlot(slot,
                                chosen.itemId(), count));
                    }
                    world.placeGenChest(bx, by, bz, List.copyOf(slots.values()));
                    placeTorch(world, bx + 1, by, bz);
                    placeTorch(world, bx - 1, by, bz);
                    placeTorch(world, bx, by, bz - 1);
                    placeTorch(world, bx, by, bz + 1);
                    return true;
                }
            }
            return false;
        }

        /** The torch beside the chest (hasSolidTop + air). */
        private static void placeTorch(FeatureWorld world, int x, int y, int z) {
            if (world.isAirAt(x, y, z) && world.isSolidAt(x, y - 1, z)) {
                world.setBlockAt(x, y, z, BuiltinBlocks.TORCH);
            }
        }
    }
}
