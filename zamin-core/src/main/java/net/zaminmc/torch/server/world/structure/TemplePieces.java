package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.dispenser.DispenserBlocks;
import net.zaminmc.torch.server.piston.PistonBlocks;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.server.redstone.TripwireBlocks;

import java.util.List;
import java.util.Random;

/**
 * The temple piece family (Slice 12f — the reference port of
 * gen/structure/TemplePieces.java, 1:1 over the Slice 12a foundation):
 * the scattered-structure base (the random facing with the width/depth
 * swap for the east/west pair, the surface-average re-base), the desert
 * pyramid (the stepped shell, the four corner towers, the blueprint
 * floor, the TNT chamber, the four direction chests) and the jungle
 * temple (the mossy cobblestone picker, the two tripwire dispenser
 * traps, the lever-piston puzzle, the main + hidden chests).
 *
 * <p><b>Ledgered</b> (the reference's arms the engine lacks the systems
 * for): the witch hut — the arm never fires (the engine carries no
 * swampland biome; the piece needs the spruce planks/stairs, the cauldron
 * and the flower pot — it ledgers with the swamp biome slice); the witch
 * spawn (Witch 1, 1-1) and the pyramid's blueprint-floor TNT chain ride
 * the mob/explosion slices already in place.</p>
 */
public final class TemplePieces {

    private TemplePieces() {
    }

    // ------------------------------------------------------------ the loot

    /**
     * The temple treasure table (DesertPyramid.LOOT_ENTRIES +
     * JungleTemple.TREASURE_LOOT_ENTRIES — the same 10 rows, weight 66).
     */
    static final List<StrongholdPieces.LootEntry> TREASURE_LOOT = List.of(
            new StrongholdPieces.LootEntry("minecraft:diamond", 1, 3, 3),
            new StrongholdPieces.LootEntry("minecraft:iron_ingot", 1, 5, 10),
            new StrongholdPieces.LootEntry("minecraft:gold_ingot", 2, 7, 15),
            new StrongholdPieces.LootEntry("minecraft:emerald", 1, 3, 2),
            new StrongholdPieces.LootEntry("minecraft:bone", 4, 6, 20),
            new StrongholdPieces.LootEntry("minecraft:rotten_flesh", 3, 7, 16),
            new StrongholdPieces.LootEntry("minecraft:saddle", 1, 1, 3),
            new StrongholdPieces.LootEntry("minecraft:iron_horse_armor", 1, 1, 1),
            new StrongholdPieces.LootEntry("minecraft:golden_horse_armor", 1, 1, 1),
            new StrongholdPieces.LootEntry("minecraft:diamond_horse_armor", 1, 1, 1));

    /** The jungle trap table (TRAP_LOOT_ENTRIES — the arrow row). */
    static final List<StrongholdPieces.LootEntry> TRAP_LOOT = List.of(
            new StrongholdPieces.LootEntry("minecraft:arrow", 2, 7, 30));

    // -------------------------------------------------------- the base

    /**
     * The scattered-structure base (ScatteredStructurePiece lines
     * 724-785): the random facing with the width/depth swap for the
     * east/west pair, and the surface-average re-base (updateHPos — the
     * deterministic heightAt average, the same envelope as the village's
     * updateHeight).
     */
    abstract static class ScatteredStructurePiece extends StructurePiece {
        protected int width;
        protected int height;
        protected int depth;
        protected int hPos = -1;

        protected ScatteredStructurePiece(Random random, int x, int y, int z,
                                          int width, int height, int depth) {
            super(0);
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.facing = StructureFacing.pickHorizontal(random);
            switch (this.facing) {
                case NORTH:
                case SOUTH:
                    this.bounds = new StructureBox(x, y, z, x + width - 1, y + height - 1, z + depth - 1);
                    break;
                default:
                    this.bounds = new StructureBox(x, y, z, x + depth - 1, y + height - 1, z + width - 1);
            }
        }

        /**
         * The surface-average re-base (updateHPos lines 762-785, 1:1): the
         * average of the pure terrain height over the piece's box clipped
         * to the chunk — the deterministic equivalent of the reference's
         * getSurfaceHeight scan (the village's own envelope).
         */
        boolean updateHPos(StructureWorld world, StructureBox clip, int offset) {
            if (this.hPos >= 0) {
                return true;
            }
            int total = 0;
            int count = 0;
            for (int z = this.bounds.minZ; z <= this.bounds.maxZ; z++) {
                for (int x = this.bounds.minX; x <= this.bounds.maxX; x++) {
                    if (clip.contains(x, 64, z)) {
                        total += Math.max(world.surfaceHeight(x, z), 0);
                        count++;
                    }
                }
            }
            if (count == 0) {
                return false;
            }
            this.hPos = total / count;
            this.bounds.move(0, this.hPos - this.bounds.minY + offset, 0);
            return true;
        }
    }

    // ------------------------------------------------------ the pyramid

    /**
     * The desert pyramid (DesertPyramid lines 28-402): the 21x15x21
     * stepped pyramid — the four corner towers, the blueprint floor
     * (the orange clay with the yellow center — the reference's
     * {@code ~DyeColor.ORANGE & 15 = 1} / {@code ~DyeColor.BLUE & 15 = 4}),
     * the hidden TNT chamber and the four direction chests.
     */
    static final class DesertPyramid extends ScatteredStructurePiece {
        private final boolean[] hasChest = new boolean[4];

        DesertPyramid(Random random, int x, int z) {
            super(random, x, 64, z, 21, 15, 21);
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, -4, 0, this.width - 1, 0, this.depth - 1,
                    palette.sandstone(), palette.sandstone(), false);
            for (int i = 1; i <= 9; i++) {
                this.fillWithOutline(world, clip, i, i, i, this.width - 1 - i, i, this.depth - 1 - i,
                        palette.sandstone(), palette.sandstone(), false);
                this.fillWithOutline(world, clip, i + 1, i, i + 1, this.width - 2 - i, i, this.depth - 2 - i,
                        palette.air(), palette.air(), false);
            }
            for (int x = 0; x < this.width; x++) {
                for (int z = 0; z < this.depth; z++) {
                    this.fillColumnDown(world, palette.sandstone(), x, -5, z, clip);
                }
            }
            int south = this.postProcessMetadata(MetadataFamily.STAIRS, 3);
            int north = this.postProcessMetadata(MetadataFamily.STAIRS, 2);
            int east = this.postProcessMetadata(MetadataFamily.STAIRS, 0);
            int west = this.postProcessMetadata(MetadataFamily.STAIRS, 1);
            this.fillWithOutline(world, clip, 0, 0, 0, 4, 9, 4,
                    palette.sandstone(), palette.air(), false);
            this.fillWithOutline(world, clip, 1, 10, 1, 3, 10, 3,
                    palette.sandstone(), palette.sandstone(), false);
            this.setBlockState(world, palette.sandstoneStairs(south), 2, 10, 0, clip);
            this.setBlockState(world, palette.sandstoneStairs(north), 2, 10, 4, clip);
            this.setBlockState(world, palette.sandstoneStairs(east), 0, 10, 2, clip);
            this.setBlockState(world, palette.sandstoneStairs(west), 4, 10, 2, clip);
            this.fillWithOutline(world, clip, this.width - 5, 0, 0, this.width - 1, 9, 4,
                    palette.sandstone(), palette.air(), false);
            this.fillWithOutline(world, clip, this.width - 4, 10, 1, this.width - 2, 10, 3,
                    palette.sandstone(), palette.sandstone(), false);
            this.setBlockState(world, palette.sandstoneStairs(south), this.width - 3, 10, 0, clip);
            this.setBlockState(world, palette.sandstoneStairs(north), this.width - 3, 10, 4, clip);
            this.setBlockState(world, palette.sandstoneStairs(east), this.width - 5, 10, 2, clip);
            this.setBlockState(world, palette.sandstoneStairs(west), this.width - 1, 10, 2, clip);
            this.fillWithOutline(world, clip, 8, 0, 0, 12, 4, 4,
                    palette.sandstone(), palette.air(), false);
            this.fillWithOutline(world, clip, 9, 1, 0, 11, 3, 4, palette.air(), palette.air(), false);
            this.setBlockState(world, palette.smoothSandstone(), 9, 1, 1, clip);
            this.setBlockState(world, palette.smoothSandstone(), 9, 2, 1, clip);
            this.setBlockState(world, palette.smoothSandstone(), 9, 3, 1, clip);
            this.setBlockState(world, palette.smoothSandstone(), 10, 3, 1, clip);
            this.setBlockState(world, palette.smoothSandstone(), 11, 3, 1, clip);
            this.setBlockState(world, palette.smoothSandstone(), 11, 2, 1, clip);
            this.setBlockState(world, palette.smoothSandstone(), 11, 1, 1, clip);
            this.fillWithOutline(world, clip, 4, 1, 1, 8, 3, 3,
                    palette.sandstone(), palette.air(), false);
            this.fillWithOutline(world, clip, 4, 1, 2, 8, 2, 2, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 12, 1, 1, 16, 3, 3,
                    palette.sandstone(), palette.air(), false);
            this.fillWithOutline(world, clip, 12, 1, 2, 16, 2, 2, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 5, 4, 5, this.width - 6, 4, this.depth - 6,
                    palette.sandstone(), palette.sandstone(), false);
            this.fillWithOutline(world, clip, 9, 4, 9, 11, 4, 11, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 8, 1, 8, 8, 3, 8,
                    palette.smoothSandstone(), palette.smoothSandstone(), false);
            this.fillWithOutline(world, clip, 12, 1, 8, 12, 3, 8,
                    palette.smoothSandstone(), palette.smoothSandstone(), false);
            this.fillWithOutline(world, clip, 8, 1, 12, 8, 3, 12,
                    palette.smoothSandstone(), palette.smoothSandstone(), false);
            this.fillWithOutline(world, clip, 12, 1, 12, 12, 3, 12,
                    palette.smoothSandstone(), palette.smoothSandstone(), false);
            this.fillWithOutline(world, clip, 1, 1, 5, 4, 4, 11,
                    palette.sandstone(), palette.sandstone(), false);
            this.fillWithOutline(world, clip, this.width - 5, 1, 5, this.width - 2, 4, 11,
                    palette.sandstone(), palette.sandstone(), false);
            this.fillWithOutline(world, clip, 6, 7, 9, 6, 7, 11,
                    palette.sandstone(), palette.sandstone(), false);
            this.fillWithOutline(world, clip, this.width - 7, 7, 9, this.width - 7, 7, 11,
                    palette.sandstone(), palette.sandstone(), false);
            this.fillWithOutline(world, clip, 5, 5, 9, 5, 7, 11,
                    palette.smoothSandstone(), palette.smoothSandstone(), false);
            this.fillWithOutline(world, clip, this.width - 6, 5, 9, this.width - 6, 7, 11,
                    palette.smoothSandstone(), palette.smoothSandstone(), false);
            this.setBlockState(world, palette.air(), 5, 5, 10, clip);
            this.setBlockState(world, palette.air(), 5, 6, 10, clip);
            this.setBlockState(world, palette.air(), 6, 6, 10, clip);
            this.setBlockState(world, palette.air(), this.width - 6, 5, 10, clip);
            this.setBlockState(world, palette.air(), this.width - 6, 6, 10, clip);
            this.setBlockState(world, palette.air(), this.width - 7, 6, 10, clip);
            this.fillWithOutline(world, clip, 2, 4, 4, 2, 6, 4, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, this.width - 3, 4, 4, this.width - 3, 6, 4,
                    palette.air(), palette.air(), false);
            this.setBlockState(world, palette.sandstoneStairs(south), 2, 4, 5, clip);
            this.setBlockState(world, palette.sandstoneStairs(south), 2, 3, 4, clip);
            this.setBlockState(world, palette.sandstoneStairs(south), this.width - 3, 4, 5, clip);
            this.setBlockState(world, palette.sandstoneStairs(south), this.width - 3, 3, 4, clip);
            this.fillWithOutline(world, clip, 1, 1, 3, 2, 2, 3,
                    palette.sandstone(), palette.sandstone(), false);
            this.fillWithOutline(world, clip, this.width - 3, 1, 3, this.width - 2, 2, 3,
                    palette.sandstone(), palette.sandstone(), false);
            this.setBlockState(world, palette.sandstoneStairs(0), 1, 1, 2, clip);
            this.setBlockState(world, palette.sandstoneStairs(0), this.width - 2, 1, 2, clip);
            this.setBlockState(world, BuiltinBlocks.SANDSTONE_SLAB, 1, 2, 2, clip);
            this.setBlockState(world, BuiltinBlocks.SANDSTONE_SLAB, this.width - 2, 2, 2, clip);
            this.setBlockState(world, palette.sandstoneStairs(west), 2, 1, 2, clip);
            this.setBlockState(world, palette.sandstoneStairs(east), this.width - 3, 1, 2, clip);
            this.fillWithOutline(world, clip, 4, 3, 5, 4, 3, 18,
                    palette.sandstone(), palette.sandstone(), false);
            this.fillWithOutline(world, clip, this.width - 5, 3, 5, this.width - 5, 3, 17,
                    palette.sandstone(), palette.sandstone(), false);
            this.fillWithOutline(world, clip, 3, 1, 5, 4, 2, 16, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, this.width - 6, 1, 5, this.width - 5, 2, 16,
                    palette.air(), palette.air(), false);
            for (int k1 = 5; k1 <= 17; k1 += 2) {
                this.setBlockState(world, palette.smoothSandstone(), 4, 1, k1, clip);
                this.setBlockState(world, palette.chiseledSandstone(), 4, 2, k1, clip);
                this.setBlockState(world, palette.smoothSandstone(), this.width - 5, 1, k1, clip);
                this.setBlockState(world, palette.chiseledSandstone(), this.width - 5, 2, k1, clip);
            }
            // The blueprint floor (lines 246-258): the orange clay ring +
            // the yellow center (the reference's ~ORANGE & 15 = 1,
            // ~BLUE & 15 = 4).
            this.setBlockState(world, palette.stainedClayOrange(), 10, 0, 7, clip);
            this.setBlockState(world, palette.stainedClayOrange(), 10, 0, 8, clip);
            this.setBlockState(world, palette.stainedClayOrange(), 9, 0, 9, clip);
            this.setBlockState(world, palette.stainedClayOrange(), 11, 0, 9, clip);
            this.setBlockState(world, palette.stainedClayOrange(), 8, 0, 10, clip);
            this.setBlockState(world, palette.stainedClayOrange(), 12, 0, 10, clip);
            this.setBlockState(world, palette.stainedClayOrange(), 7, 0, 10, clip);
            this.setBlockState(world, palette.stainedClayOrange(), 13, 0, 10, clip);
            this.setBlockState(world, palette.stainedClayOrange(), 9, 0, 11, clip);
            this.setBlockState(world, palette.stainedClayOrange(), 11, 0, 11, clip);
            this.setBlockState(world, palette.stainedClayOrange(), 10, 0, 12, clip);
            this.setBlockState(world, palette.stainedClayOrange(), 10, 0, 13, clip);
            this.setBlockState(world, palette.stainedClayYellow(), 10, 0, 10, clip);
            for (int x = 0; x <= this.width - 1; x += this.width - 1) {
                this.setBlockState(world, palette.smoothSandstone(), x, 2, 1, clip);
                this.setBlockState(world, palette.stainedClayOrange(), x, 2, 2, clip);
                this.setBlockState(world, palette.smoothSandstone(), x, 2, 3, clip);
                this.setBlockState(world, palette.smoothSandstone(), x, 3, 1, clip);
                this.setBlockState(world, palette.stainedClayOrange(), x, 3, 2, clip);
                this.setBlockState(world, palette.smoothSandstone(), x, 3, 3, clip);
                this.setBlockState(world, palette.stainedClayOrange(), x, 4, 1, clip);
                this.setBlockState(world, palette.chiseledSandstone(), x, 4, 2, clip);
                this.setBlockState(world, palette.stainedClayOrange(), x, 4, 3, clip);
                this.setBlockState(world, palette.smoothSandstone(), x, 5, 1, clip);
                this.setBlockState(world, palette.stainedClayOrange(), x, 5, 2, clip);
                this.setBlockState(world, palette.smoothSandstone(), x, 5, 3, clip);
                this.setBlockState(world, palette.stainedClayOrange(), x, 6, 1, clip);
                this.setBlockState(world, palette.chiseledSandstone(), x, 6, 2, clip);
                this.setBlockState(world, palette.stainedClayOrange(), x, 6, 3, clip);
                this.setBlockState(world, palette.stainedClayOrange(), x, 7, 1, clip);
                this.setBlockState(world, palette.stainedClayOrange(), x, 7, 2, clip);
                this.setBlockState(world, palette.stainedClayOrange(), x, 7, 3, clip);
                this.setBlockState(world, palette.smoothSandstone(), x, 8, 1, clip);
                this.setBlockState(world, palette.smoothSandstone(), x, 8, 2, clip);
                this.setBlockState(world, palette.smoothSandstone(), x, 8, 3, clip);
            }
            for (int k3 = 2; k3 <= this.width - 3; k3 += this.width - 3 - 2) {
                this.setBlockState(world, palette.smoothSandstone(), k3 - 1, 2, 0, clip);
                this.setBlockState(world, palette.stainedClayOrange(), k3, 2, 0, clip);
                this.setBlockState(world, palette.smoothSandstone(), k3 + 1, 2, 0, clip);
                this.setBlockState(world, palette.smoothSandstone(), k3 - 1, 3, 0, clip);
                this.setBlockState(world, palette.stainedClayOrange(), k3, 3, 0, clip);
                this.setBlockState(world, palette.smoothSandstone(), k3 + 1, 3, 0, clip);
                this.setBlockState(world, palette.stainedClayOrange(), k3 - 1, 4, 0, clip);
                this.setBlockState(world, palette.chiseledSandstone(), k3, 4, 0, clip);
                this.setBlockState(world, palette.stainedClayOrange(), k3 + 1, 4, 0, clip);
                this.setBlockState(world, palette.smoothSandstone(), k3 - 1, 5, 0, clip);
                this.setBlockState(world, palette.stainedClayOrange(), k3, 5, 0, clip);
                this.setBlockState(world, palette.smoothSandstone(), k3 + 1, 5, 0, clip);
                this.setBlockState(world, palette.stainedClayOrange(), k3 - 1, 6, 0, clip);
                this.setBlockState(world, palette.chiseledSandstone(), k3, 6, 0, clip);
                this.setBlockState(world, palette.stainedClayOrange(), k3 + 1, 6, 0, clip);
                this.setBlockState(world, palette.stainedClayOrange(), k3 - 1, 7, 0, clip);
                this.setBlockState(world, palette.stainedClayOrange(), k3, 7, 0, clip);
                this.setBlockState(world, palette.stainedClayOrange(), k3 + 1, 7, 0, clip);
                this.setBlockState(world, palette.smoothSandstone(), k3 - 1, 8, 0, clip);
                this.setBlockState(world, palette.smoothSandstone(), k3, 8, 0, clip);
                this.setBlockState(world, palette.smoothSandstone(), k3 + 1, 8, 0, clip);
            }
            this.fillWithOutline(world, clip, 8, 4, 0, 12, 6, 0,
                    palette.smoothSandstone(), palette.smoothSandstone(), false);
            this.setBlockState(world, palette.air(), 8, 6, 0, clip);
            this.setBlockState(world, palette.air(), 12, 6, 0, clip);
            this.setBlockState(world, palette.stainedClayOrange(), 9, 5, 0, clip);
            this.setBlockState(world, palette.chiseledSandstone(), 10, 5, 0, clip);
            this.setBlockState(world, palette.stainedClayOrange(), 11, 5, 0, clip);
            // The hidden chamber (lines 288-335): the shaft, the TNT floor,
            // the pressure plate and the pillar room.
            this.fillWithOutline(world, clip, 8, -14, 8, 12, -11, 12,
                    palette.smoothSandstone(), palette.smoothSandstone(), false);
            this.fillWithOutline(world, clip, 8, -10, 8, 12, -10, 12,
                    palette.chiseledSandstone(), palette.chiseledSandstone(), false);
            this.fillWithOutline(world, clip, 8, -9, 8, 12, -9, 12,
                    palette.smoothSandstone(), palette.smoothSandstone(), false);
            this.fillWithOutline(world, clip, 8, -8, 8, 12, -1, 12,
                    palette.sandstone(), palette.sandstone(), false);
            this.fillWithOutline(world, clip, 9, -11, 9, 11, -1, 11, palette.air(), palette.air(), false);
            this.setBlockState(world, net.zaminmc.torch.server.redstone.RedstoneBlocks.plateOf(false, false), 10, -11, 10, clip);
            this.fillWithOutline(world, clip, 9, -13, 9, 11, -13, 11,
                    palette.tnt(), palette.air(), false);
            this.setBlockState(world, palette.air(), 8, -11, 10, clip);
            this.setBlockState(world, palette.air(), 8, -10, 10, clip);
            this.setBlockState(world, palette.chiseledSandstone(), 7, -10, 10, clip);
            this.setBlockState(world, palette.smoothSandstone(), 7, -11, 10, clip);
            this.setBlockState(world, palette.air(), 12, -11, 10, clip);
            this.setBlockState(world, palette.air(), 12, -10, 10, clip);
            this.setBlockState(world, palette.chiseledSandstone(), 13, -10, 10, clip);
            this.setBlockState(world, palette.smoothSandstone(), 13, -11, 10, clip);
            this.setBlockState(world, palette.air(), 10, -11, 8, clip);
            this.setBlockState(world, palette.air(), 10, -10, 8, clip);
            this.setBlockState(world, palette.chiseledSandstone(), 10, -10, 7, clip);
            this.setBlockState(world, palette.smoothSandstone(), 10, -11, 7, clip);
            this.setBlockState(world, palette.air(), 10, -11, 12, clip);
            this.setBlockState(world, palette.air(), 10, -10, 12, clip);
            this.setBlockState(world, palette.chiseledSandstone(), 10, -10, 13, clip);
            this.setBlockState(world, palette.smoothSandstone(), 10, -11, 13, clip);
            // The four direction chests (lines 329-343): the horizontal
            // offset pair (N (0,-2), S (0,+2), W (-2,0), E (+2,0)).
            int[][] offsets = {{0, -2}, {0, 2}, {-2, 0}, {2, 0}};
            for (int i = 0; i < 4; i++) {
                if (!this.hasChest[i]) {
                    this.hasChest[i] = StrongholdPieces.placeChestWithLoot(this, world, random, clip,
                            10 + offsets[i][0], -11, 10 + offsets[i][1], TREASURE_LOOT,
                            StrongholdPieces.rollBookLoot(random, 1, 1, 1), 2 + random.nextInt(5));
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------ the jungle temple

    /**
     * The jungle temple (JungleTemple lines 404-743): the 12x10x15 ruin —
     * the mossy cobblestone picker (40% plain, 60% mossy), the two
     * tripwire dispenser traps, the lever-piston puzzle room and the pair
     * of chests.
     */
    static final class JungleTemple extends ScatteredStructurePiece {
        private boolean hasMainChest;
        private boolean hasHiddenChest;
        private boolean hasPrimaryTrap;
        private boolean hasSecondaryTrap;

        JungleTemple(Random random, int x, int z) {
            super(random, x, 64, z, 12, 10, 15);
        }

        /** The cobblestone picker (CobblestonePicker lines 745-757, 1:1). */
        private StructurePiece.BlockPicker cobblestonePicker(StructureWorld world) {
            StructurePalette palette = world.palette();
            return (random, x, y, z, shell) -> random.nextFloat() < 0.4F
                    ? palette.cobblestone() : palette.mossyCobblestone();
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (!this.updateHPos(world, clip, 0)) {
                return false;
            }
            StructurePalette palette = world.palette();
            StructurePiece.BlockPicker picker = cobblestonePicker(world);
            int south = this.postProcessMetadata(MetadataFamily.STAIRS, 3);
            int north = this.postProcessMetadata(MetadataFamily.STAIRS, 2);
            int east = this.postProcessMetadata(MetadataFamily.STAIRS, 0);
            int west = this.postProcessMetadata(MetadataFamily.STAIRS, 1);
            this.fillPicked(world, clip, 0, -4, 0, this.width - 1, 0, this.depth - 1, false, random, picker);
            this.fillPicked(world, clip, 2, 1, 2, 9, 2, 2, false, random, picker);
            this.fillPicked(world, clip, 2, 1, 12, 9, 2, 12, false, random, picker);
            this.fillPicked(world, clip, 2, 1, 3, 2, 2, 11, false, random, picker);
            this.fillPicked(world, clip, 9, 1, 3, 9, 2, 11, false, random, picker);
            this.fillPicked(world, clip, 1, 3, 1, 10, 6, 1, false, random, picker);
            this.fillPicked(world, clip, 1, 3, 13, 10, 6, 13, false, random, picker);
            this.fillPicked(world, clip, 1, 3, 2, 1, 6, 12, false, random, picker);
            this.fillPicked(world, clip, 10, 3, 2, 10, 6, 12, false, random, picker);
            this.fillPicked(world, clip, 2, 3, 2, 9, 3, 12, false, random, picker);
            this.fillPicked(world, clip, 2, 6, 2, 9, 6, 12, false, random, picker);
            this.fillPicked(world, clip, 3, 7, 3, 8, 7, 11, false, random, picker);
            this.fillPicked(world, clip, 4, 8, 4, 7, 8, 10, false, random, picker);
            this.fillAir(world, clip, 3, 1, 3, 8, 2, 11);
            this.fillAir(world, clip, 4, 3, 6, 7, 3, 9);
            this.fillAir(world, clip, 2, 4, 2, 9, 5, 12);
            this.fillAir(world, clip, 4, 6, 5, 7, 6, 9);
            this.fillAir(world, clip, 5, 7, 6, 6, 7, 8);
            this.fillAir(world, clip, 5, 1, 2, 6, 2, 2);
            this.fillAir(world, clip, 5, 2, 12, 6, 2, 12);
            this.fillAir(world, clip, 5, 5, 1, 6, 5, 1);
            this.fillAir(world, clip, 5, 5, 13, 6, 5, 13);
            this.setBlockState(world, palette.air(), 1, 5, 5, clip);
            this.setBlockState(world, palette.air(), 10, 5, 5, clip);
            this.setBlockState(world, palette.air(), 1, 5, 9, clip);
            this.setBlockState(world, palette.air(), 10, 5, 9, clip);
            for (int i1 = 0; i1 <= 14; i1 += 14) {
                this.fillPicked(world, clip, 2, 4, i1, 2, 5, i1, false, random, picker);
                this.fillPicked(world, clip, 4, 4, i1, 4, 5, i1, false, random, picker);
                this.fillPicked(world, clip, 7, 4, i1, 7, 5, i1, false, random, picker);
                this.fillPicked(world, clip, 9, 4, i1, 9, 5, i1, false, random, picker);
            }
            this.fillPicked(world, clip, 5, 6, 0, 6, 6, 0, false, random, picker);
            for (int k1 = 0; k1 <= 11; k1 += 11) {
                for (int j1 = 2; j1 <= 12; j1 += 2) {
                    this.fillPicked(world, clip, k1, 4, j1, k1, 5, j1, false, random, picker);
                }
                this.fillPicked(world, clip, k1, 6, 5, k1, 6, 5, false, random, picker);
                this.fillPicked(world, clip, k1, 6, 9, k1, 6, 9, false, random, picker);
            }
            this.fillPicked(world, clip, 2, 7, 2, 2, 9, 2, false, random, picker);
            this.fillPicked(world, clip, 9, 7, 2, 9, 9, 2, false, random, picker);
            this.fillPicked(world, clip, 2, 7, 12, 2, 9, 12, false, random, picker);
            this.fillPicked(world, clip, 9, 7, 12, 9, 9, 12, false, random, picker);
            this.fillPicked(world, clip, 4, 9, 4, 4, 9, 4, false, random, picker);
            this.fillPicked(world, clip, 7, 9, 4, 7, 9, 4, false, random, picker);
            this.fillPicked(world, clip, 4, 9, 10, 4, 9, 10, false, random, picker);
            this.fillPicked(world, clip, 7, 9, 10, 7, 9, 10, false, random, picker);
            this.fillPicked(world, clip, 5, 9, 7, 6, 9, 7, false, random, picker);
            this.setBlockState(world, palette.stoneStairs(south), 5, 9, 6, clip);
            this.setBlockState(world, palette.stoneStairs(south), 6, 9, 6, clip);
            this.setBlockState(world, palette.stoneStairs(north), 5, 9, 8, clip);
            this.setBlockState(world, palette.stoneStairs(north), 6, 9, 8, clip);
            this.setBlockState(world, palette.stoneStairs(south), 4, 0, 0, clip);
            this.setBlockState(world, palette.stoneStairs(south), 5, 0, 0, clip);
            this.setBlockState(world, palette.stoneStairs(south), 6, 0, 0, clip);
            this.setBlockState(world, palette.stoneStairs(south), 7, 0, 0, clip);
            this.setBlockState(world, palette.stoneStairs(south), 4, 1, 8, clip);
            this.setBlockState(world, palette.stoneStairs(south), 4, 2, 9, clip);
            this.setBlockState(world, palette.stoneStairs(south), 4, 3, 10, clip);
            this.setBlockState(world, palette.stoneStairs(south), 7, 1, 8, clip);
            this.setBlockState(world, palette.stoneStairs(south), 7, 2, 9, clip);
            this.setBlockState(world, palette.stoneStairs(south), 7, 3, 10, clip);
            this.fillPicked(world, clip, 4, 1, 9, 4, 1, 9, false, random, picker);
            this.fillPicked(world, clip, 7, 1, 9, 7, 1, 9, false, random, picker);
            this.fillPicked(world, clip, 4, 1, 10, 7, 2, 10, false, random, picker);
            this.fillPicked(world, clip, 5, 4, 5, 6, 4, 5, false, random, picker);
            this.setBlockState(world, palette.stoneStairs(east), 4, 4, 5, clip);
            this.setBlockState(world, palette.stoneStairs(west), 7, 4, 5, clip);
            for (int l1 = 0; l1 < 4; l1++) {
                this.setBlockState(world, palette.stoneStairs(north), 5, 0 - l1, 6 + l1, clip);
                this.setBlockState(world, palette.stoneStairs(north), 6, 0 - l1, 6 + l1, clip);
                this.fillAir(world, clip, 5, 0 - l1, 7 + l1, 6, 0 - l1, 9 + l1);
            }
            this.fillAir(world, clip, 1, -3, 12, 10, -1, 13);
            this.fillAir(world, clip, 1, -3, 1, 3, -1, 13);
            this.fillAir(world, clip, 1, -3, 1, 9, -1, 5);
            for (int i2 = 1; i2 <= 13; i2 += 2) {
                this.fillPicked(world, clip, 1, -3, i2, 1, -2, i2, false, random, picker);
            }
            for (int j2 = 2; j2 <= 12; j2 += 2) {
                this.fillPicked(world, clip, 1, -1, j2, 3, -1, j2, false, random, picker);
            }
            this.fillPicked(world, clip, 2, -2, 1, 5, -2, 1, false, random, picker);
            this.fillPicked(world, clip, 7, -2, 1, 9, -2, 1, false, random, picker);
            this.fillPicked(world, clip, 6, -3, 1, 6, -3, 1, false, random, picker);
            this.fillPicked(world, clip, 6, -1, 1, 6, -1, 1, false, random, picker);
            // The primary tripwire trap (lines 566-584): the EAST/WEST hook
            // pair over the horizontal id band, the attached tripwire, the
            // redstone wire to the mossy cobble, the dispenser.
            int eastHook = this.postProcessMetadata(MetadataFamily.HORIZONTAL, 3);
            int westHook = this.postProcessMetadata(MetadataFamily.HORIZONTAL, 1);
            this.setBlockState(world, TripwireBlocks.hookOf(horizontalToDirection(eastHook),
                    true, false), 1, -3, 8, clip);
            this.setBlockState(world, TripwireBlocks.hookOf(horizontalToDirection(westHook),
                    true, false), 4, -3, 8, clip);
            this.setBlockState(world, TripwireBlocks.wireOf(false, false, true, false), 2, -3, 8, clip);
            this.setBlockState(world, TripwireBlocks.wireOf(false, false, true, false), 3, -3, 8, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 5, -3, 7, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 5, -3, 6, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 5, -3, 5, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 5, -3, 4, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 5, -3, 3, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 5, -3, 2, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 5, -3, 1, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 4, -3, 1, clip);
            this.setBlockState(world, palette.mossyCobblestone(), 3, -3, 1, clip);
            if (!this.hasPrimaryTrap) {
                this.hasPrimaryTrap = placeDispenserWithLoot(this, world, random, clip, 3, -2, 1,
                        this.postProcessMetadata(MetadataFamily.PISTON, 2), TRAP_LOOT, 2);
            }
            this.setBlockState(world, palette.vine(), 3, -2, 2, clip);
            // The secondary tripwire trap (lines 588-608): the NORTH/SOUTH
            // hook pair, the wire column down to the second dispenser.
            int northHook = this.postProcessMetadata(MetadataFamily.HORIZONTAL, 2);
            int southHook = this.postProcessMetadata(MetadataFamily.HORIZONTAL, 0);
            this.setBlockState(world, TripwireBlocks.hookOf(horizontalToDirection(northHook),
                    true, false), 7, -3, 1, clip);
            this.setBlockState(world, TripwireBlocks.hookOf(horizontalToDirection(southHook),
                    true, false), 7, -3, 5, clip);
            this.setBlockState(world, TripwireBlocks.wireOf(false, false, true, false), 7, -3, 2, clip);
            this.setBlockState(world, TripwireBlocks.wireOf(false, false, true, false), 7, -3, 3, clip);
            this.setBlockState(world, TripwireBlocks.wireOf(false, false, true, false), 7, -3, 4, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 8, -3, 6, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 9, -3, 6, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 9, -3, 5, clip);
            this.setBlockState(world, palette.mossyCobblestone(), 9, -3, 4, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 9, -2, 4, clip);
            if (!this.hasSecondaryTrap) {
                this.hasSecondaryTrap = placeDispenserWithLoot(this, world, random, clip, 9, -2, 3,
                        this.postProcessMetadata(MetadataFamily.PISTON, 4), TRAP_LOOT, 2);
            }
            this.setBlockState(world, palette.vine(), 8, -1, 3, clip);
            this.setBlockState(world, palette.vine(), 8, -2, 3, clip);
            if (!this.hasMainChest) {
                this.hasMainChest = StrongholdPieces.placeChestWithLoot(this, world, random, clip,
                        8, -3, 3, TREASURE_LOOT, StrongholdPieces.rollBookLoot(random, 1, 1, 1),
                        2 + random.nextInt(5));
            }
            this.setBlockState(world, palette.mossyCobblestone(), 9, -3, 2, clip);
            this.setBlockState(world, palette.mossyCobblestone(), 8, -3, 1, clip);
            this.setBlockState(world, palette.mossyCobblestone(), 4, -3, 5, clip);
            this.setBlockState(world, palette.mossyCobblestone(), 5, -2, 5, clip);
            this.setBlockState(world, palette.mossyCobblestone(), 5, -1, 5, clip);
            this.setBlockState(world, palette.mossyCobblestone(), 6, -3, 5, clip);
            this.setBlockState(world, palette.mossyCobblestone(), 7, -2, 5, clip);
            this.setBlockState(world, palette.mossyCobblestone(), 7, -1, 5, clip);
            this.setBlockState(world, palette.mossyCobblestone(), 8, -3, 5, clip);
            this.fillPicked(world, clip, 9, -1, 1, 9, -1, 5, false, random, picker);
            this.fillAir(world, clip, 8, -3, 8, 10, -1, 10);
            this.setBlockState(world, palette.chiseledStoneBricks(), 8, -2, 11, clip);
            this.setBlockState(world, palette.chiseledStoneBricks(), 9, -2, 11, clip);
            this.setBlockState(world, palette.chiseledStoneBricks(), 10, -2, 11, clip);
            // The lever trio (lines 624-639): the NORTH flip then
            // getMetadataUnpowered (N=4 S=3 W=2 E=1).
            int leverFacing = this.postProcessMetadata(MetadataFamily.PISTON, 2);
            int leverMetadata = switch (leverFacing) {
                case 3 -> 3; // south
                case 4 -> 2; // west
                case 5 -> 1; // east
                default -> 4; // north
            };
            this.setBlockState(world, RedstoneBlocks.leverOf(leverMetadata, false), 8, -2, 12, clip);
            this.setBlockState(world, RedstoneBlocks.leverOf(leverMetadata, false), 9, -2, 12, clip);
            this.setBlockState(world, RedstoneBlocks.leverOf(leverMetadata, false), 10, -2, 12, clip);
            this.fillPicked(world, clip, 8, -3, 8, 8, -3, 10, false, random, picker);
            this.fillPicked(world, clip, 10, -3, 8, 10, -3, 10, false, random, picker);
            this.setBlockState(world, palette.mossyCobblestone(), 10, -2, 9, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 8, -2, 9, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 8, -2, 10, clip);
            this.setBlockState(world, RedstoneBlocks.wireOfPower(0), 10, -1, 9, clip);
            // The piston puzzle (lines 645-659): the UP sticky piston
            // (unflipped — the reference's own raw Direction id), the WEST
            // pair flipped, the repeater.
            this.setBlockState(world, PistonBlocks.pistonOf(1, false, true), 9, -2, 8, clip);
            this.setBlockState(world, PistonBlocks.pistonOf(
                    this.postProcessMetadata(MetadataFamily.PISTON, 4), false, true), 10, -2, 8, clip);
            this.setBlockState(world, PistonBlocks.pistonOf(
                    this.postProcessMetadata(MetadataFamily.PISTON, 4), false, true), 10, -1, 8, clip);
            this.setBlockState(world, RedstoneBlocks.repeaterOf(
                    this.postProcessMetadata(MetadataFamily.HORIZONTAL, 2), 1, false),
                    10, -2, 10, clip);
            if (!this.hasHiddenChest) {
                this.hasHiddenChest = StrongholdPieces.placeChestWithLoot(this, world, random, clip,
                        9, -3, 10, TREASURE_LOOT, StrongholdPieces.rollBookLoot(random, 1, 1, 1),
                        2 + random.nextInt(5));
            }
            return true;
        }

        /**
         * The dispenser + loot (placeDispenserWithLoot lines 586-602 +
         * LootEntry.addLoot): the clip gate, the facing flip, the rolled
         * contents riding the chunk's pending record — the same model as
         * the chest (the engine's dispenser inventory is the nine-slot
         * window the dispenser system drains).
         */
        private static boolean placeDispenserWithLoot(StructurePiece piece, StructureWorld world,
                                                      Random random, StructureBox clip, int x, int y,
                                                      int z, int facing,
                                                      List<StrongholdPieces.LootEntry> entries,
                                                      int amount) {
            int wx = piece.transformX(x, z);
            int wy = piece.transformY(y);
            int wz = piece.transformZ(x, z);
            BlockType current = clip.contains(wx, wy, wz) ? world.blockAt(wx, wy, wz) : null;
            if (current != null && net.zaminmc.torch.server.dispenser.DispenserBlocks.isFamily(current)) {
                return false;
            }
            piece.setBlockState(world, DispenserBlocks.dispenserOf(facing, false), x, y, z, clip);
            java.util.Map<Integer, StructureWorld.GenLootSlot> slots = new java.util.LinkedHashMap<>();
            for (int i = 0; i < amount; i++) {
                StrongholdPieces.LootEntry entry = entries.getFirst();
                int count = entry.minCount()
                        + random.nextInt(entry.maxCount() - entry.minCount() + 1);
                if (count > 0) {
                    int slot = random.nextInt(9);
                    slots.put(slot, new StructureWorld.GenLootSlot(slot, entry.itemId(), count));
                }
            }
            world.placeGenDispenser(wx, wy, wz, facing, List.copyOf(slots.values()));
            return true;
        }

        /** The horizontal id (S=0 W=1 N=2 E=3) → the reference Direction id. */
        private static int horizontalToDirection(int horizontalId) {
            return switch (horizontalId & 3) {
                case 0 -> 3; // south
                case 1 -> 4; // west
                case 2 -> 2; // north
                default -> 5; // east
            };
        }
    }
}
