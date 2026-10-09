package net.minecraft.world.gen.structure;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SandstoneBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;

public class VillagePieces {
    public static void register() {
        StructureRegistry.registerPiece(VillagePieces.LibrarianHouse.class, "ViBH");
        StructureRegistry.registerPiece(VillagePieces.DoubleFarm.class, "ViDF");
        StructureRegistry.registerPiece(VillagePieces.Farm.class, "ViF");
        StructureRegistry.registerPiece(VillagePieces.LampPost.class, "ViL");
        StructureRegistry.registerPiece(VillagePieces.ButcherHouse.class, "ViPH");
        StructureRegistry.registerPiece(VillagePieces.SmallHouse.class, "ViSH");
        StructureRegistry.registerPiece(VillagePieces.FarmerHouse.class, "ViSmH");
        StructureRegistry.registerPiece(VillagePieces.PriestHouse.class, "ViST");
        StructureRegistry.registerPiece(VillagePieces.BlacksmithHouse.class, "ViS");
        StructureRegistry.registerPiece(VillagePieces.Start.class, "ViStart");
        StructureRegistry.registerPiece(VillagePieces.Road.class, "ViSR");
        StructureRegistry.registerPiece(VillagePieces.LargeHouse.class, "ViTRH");
        StructureRegistry.registerPiece(VillagePieces.Well.class, "ViW");
    }

    public static List<VillagePieces.VillagePieceWeight> getPieceWeights(Random random, int size) {
        List<VillagePieces.VillagePieceWeight> list = Lists.newArrayList();
        list.add(new VillagePieces.VillagePieceWeight(VillagePieces.SmallHouse.class, 4, MathHelper.nextInt(random, 2 + size, 4 + size * 2)));
        list.add(new VillagePieces.VillagePieceWeight(VillagePieces.PriestHouse.class, 20, MathHelper.nextInt(random, 0 + size, 1 + size)));
        list.add(new VillagePieces.VillagePieceWeight(VillagePieces.LibrarianHouse.class, 20, MathHelper.nextInt(random, 0 + size, 2 + size)));
        list.add(new VillagePieces.VillagePieceWeight(VillagePieces.FarmerHouse.class, 3, MathHelper.nextInt(random, 2 + size, 5 + size * 3)));
        list.add(new VillagePieces.VillagePieceWeight(VillagePieces.ButcherHouse.class, 15, MathHelper.nextInt(random, 0 + size, 2 + size)));
        list.add(new VillagePieces.VillagePieceWeight(VillagePieces.DoubleFarm.class, 3, MathHelper.nextInt(random, 1 + size, 4 + size)));
        list.add(new VillagePieces.VillagePieceWeight(VillagePieces.Farm.class, 3, MathHelper.nextInt(random, 2 + size, 4 + size * 2)));
        list.add(new VillagePieces.VillagePieceWeight(VillagePieces.BlacksmithHouse.class, 15, MathHelper.nextInt(random, 0, 1 + size)));
        list.add(new VillagePieces.VillagePieceWeight(VillagePieces.LargeHouse.class, 8, MathHelper.nextInt(random, 0 + size, 3 + size * 2)));
        Iterator<VillagePieces.VillagePieceWeight> iterator = list.iterator();

        while (iterator.hasNext()) {
            if (iterator.next().maxAmount == 0) {
                iterator.remove();
            }
        }

        return list;
    }

    /**
     * Will return the total weight of the given piece weights if more pieces can still be generated, or -1.
     */
    private static int updateTotalWeight(List<VillagePieces.VillagePieceWeight> weights) {
        boolean flag = false;
        int i = 0;

        for (VillagePieces.VillagePieceWeight villagepieces$villagepieceweight : weights) {
            if (villagepieces$villagepieceweight.maxAmount > 0 && villagepieces$villagepieceweight.amountGenerated < villagepieces$villagepieceweight.maxAmount
                )
             {
                flag = true;
            }

            i += villagepieces$villagepieceweight.weight;
        }

        return flag ? i : -1;
    }

    private static VillagePieces.VillagePiece createBuildingPiece(
        VillagePieces.Start startPiece,
        VillagePieces.VillagePieceWeight weight,
        List<StructurePiece> pieces,
        Random random,
        int x,
        int y,
        int z,
        Direction facing,
        int generationDepth
    ) {
        Class<? extends VillagePieces.VillagePiece> oclass = weight.type;
        VillagePieces.VillagePiece villagepieces$villagepiece = null;
        if (oclass == VillagePieces.SmallHouse.class) {
            villagepieces$villagepiece = VillagePieces.SmallHouse.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
        } else if (oclass == VillagePieces.PriestHouse.class) {
            villagepieces$villagepiece = VillagePieces.PriestHouse.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
        } else if (oclass == VillagePieces.LibrarianHouse.class) {
            villagepieces$villagepiece = VillagePieces.LibrarianHouse.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
        } else if (oclass == VillagePieces.FarmerHouse.class) {
            villagepieces$villagepiece = VillagePieces.FarmerHouse.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
        } else if (oclass == VillagePieces.ButcherHouse.class) {
            villagepieces$villagepiece = VillagePieces.ButcherHouse.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
        } else if (oclass == VillagePieces.DoubleFarm.class) {
            villagepieces$villagepiece = VillagePieces.DoubleFarm.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
        } else if (oclass == VillagePieces.Farm.class) {
            villagepieces$villagepiece = VillagePieces.Farm.create(startPiece, pieces, random, x, y, z, facing, generationDepth);
        } else if (oclass == VillagePieces.BlacksmithHouse.class) {
            villagepieces$villagepiece = VillagePieces.BlacksmithHouse.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
        } else if (oclass == VillagePieces.LargeHouse.class) {
            villagepieces$villagepiece = VillagePieces.LargeHouse.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
        }

        return villagepieces$villagepiece;
    }

    private static VillagePieces.VillagePiece generateNextBuildingPiece(
        VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
    ) {
        int i = updateTotalWeight(startPiece.weights);
        if (i <= 0) {
            return null;
        }

        int j = 0;

        while (j < 5) {
            j++;
            int k = random.nextInt(i);

            for (VillagePieces.VillagePieceWeight villagepieces$villagepieceweight : startPiece.weights) {
                k -= villagepieces$villagepieceweight.weight;
                if (k < 0) {
                    if (!villagepieces$villagepieceweight.isValid(generationDepth)
                        || villagepieces$villagepieceweight == startPiece.previous && startPiece.weights.size() > 1) {
                        break;
                    }

                    VillagePieces.VillagePiece villagepieces$villagepiece = createBuildingPiece(
                        startPiece, villagepieces$villagepieceweight, pieces, random, x, y, z, facing, generationDepth
                    );
                    if (villagepieces$villagepiece != null) {
                        villagepieces$villagepieceweight.amountGenerated++;
                        startPiece.previous = villagepieces$villagepieceweight;
                        if (!villagepieces$villagepieceweight.isValid()) {
                            startPiece.weights.remove(villagepieces$villagepieceweight);
                        }

                        return villagepieces$villagepiece;
                    }
                }
            }
        }

        StructureBox structurebox = VillagePieces.LampPost.findSize(startPiece, pieces, random, x, y, z, facing);
        return structurebox != null ? new VillagePieces.LampPost(startPiece, generationDepth, random, structurebox, facing) : null;
    }

    private static StructurePiece generateBuildingPiece(
        VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
    ) {
        if (generationDepth > 50) {
            return null;
        }

        if (Math.abs(x - startPiece.getBounds().minX) <= 112 && Math.abs(z - startPiece.getBounds().minZ) <= 112) {
            StructurePiece structurepiece = generateNextBuildingPiece(startPiece, pieces, random, x, y, z, facing, generationDepth + 1);
            if (structurepiece != null) {
                int i = (structurepiece.bounds.minX + structurepiece.bounds.maxX) / 2;
                int j = (structurepiece.bounds.minZ + structurepiece.bounds.maxZ) / 2;
                int k = structurepiece.bounds.maxX - structurepiece.bounds.minX;
                int l = structurepiece.bounds.maxZ - structurepiece.bounds.minZ;
                int i1 = k > l ? k : l;
                if (startPiece.getBiomeSource().isBiomeWithin(i, j, i1 / 2 + 4, VillageStructure.VALID_BIOMES)) {
                    pieces.add(structurepiece);
                    startPiece.buildingPieces.add(structurepiece);
                    return structurepiece;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private static StructurePiece generateRoadPiece(
        VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
    ) {
        if (generationDepth > 3 + startPiece.size) {
            return null;
        }

        if (Math.abs(x - startPiece.getBounds().minX) <= 112 && Math.abs(z - startPiece.getBounds().minZ) <= 112) {
            StructureBox structurebox = VillagePieces.Road.findSize(startPiece, pieces, random, x, y, z, facing);
            if (structurebox != null && structurebox.minY > 10) {
                StructurePiece structurepiece = new VillagePieces.Road(startPiece, generationDepth, random, structurebox, facing);
                int i = (structurepiece.bounds.minX + structurepiece.bounds.maxX) / 2;
                int j = (structurepiece.bounds.minZ + structurepiece.bounds.maxZ) / 2;
                int k = structurepiece.bounds.maxX - structurepiece.bounds.minX;
                int l = structurepiece.bounds.maxZ - structurepiece.bounds.minZ;
                int i1 = k > l ? k : l;
                if (startPiece.getBiomeSource().isBiomeWithin(i, j, i1 / 2 + 4, VillageStructure.VALID_BIOMES)) {
                    pieces.add(structurepiece);
                    startPiece.roadPieces.add(structurepiece);
                    return structurepiece;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public static class BlacksmithHouse extends VillagePieces.VillagePiece {
        private static final List<LootEntry> LOOT_ENTRIES = Lists.newArrayList(
            new LootEntry(Items.DIAMOND, 0, 1, 3, 3),
            new LootEntry(Items.IRON_INGOT, 0, 1, 5, 10),
            new LootEntry(Items.GOLD_INGOT, 0, 1, 3, 5),
            new LootEntry(Items.BREAD, 0, 1, 3, 15),
            new LootEntry(Items.APPLE, 0, 1, 3, 15),
            new LootEntry(Items.IRON_PICKAXE, 0, 1, 1, 5),
            new LootEntry(Items.IRON_SWORD, 0, 1, 1, 5),
            new LootEntry(Items.IRON_CHESTPLATE, 0, 1, 1, 5),
            new LootEntry(Items.IRON_HELMET, 0, 1, 1, 5),
            new LootEntry(Items.IRON_LEGGINGS, 0, 1, 1, 5),
            new LootEntry(Items.IRON_BOOTS, 0, 1, 1, 5),
            new LootEntry(Item.byBlock(Blocks.OBSIDIAN), 0, 3, 7, 5),
            new LootEntry(Item.byBlock(Blocks.SAPLING), 0, 3, 7, 5),
            new LootEntry(Items.SADDLE, 0, 1, 1, 3),
            new LootEntry(Items.IRON_HORSE_ARMOR, 0, 1, 1, 1),
            new LootEntry(Items.GOLDEN_HORSE_ARMOR, 0, 1, 1, 1),
            new LootEntry(Items.DIAMOND_HORSE_ARMOR, 0, 1, 1, 1)
        );
        private boolean hasChest;

        public BlacksmithHouse() {
        }

        public BlacksmithHouse(VillagePieces.Start startPiece, int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        public static VillagePieces.BlacksmithHouse of(
            VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, 0, 0, 0, 10, 6, 7, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new VillagePieces.BlacksmithHouse(startPiece, generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putBoolean("Chest", this.hasChest);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.hasChest = nbt.getBoolean("Chest");
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world, bounds);
                if (this.hpos < 0) {
                    return true;
                }

                this.bounds.move(0, this.hpos - this.bounds.maxY + 6 - 1, 0);
            }

            this.fillWithOutline(world, bounds, 0, 1, 0, 9, 4, 6, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 0, 0, 9, 0, 6, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 4, 0, 9, 4, 6, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 5, 0, 9, 5, 6, Blocks.STONE_SLAB.defaultState(), Blocks.STONE_SLAB.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 5, 1, 8, 5, 5, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 1, 0, 2, 3, 0, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 1, 0, 0, 4, 0, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 3, 1, 0, 3, 4, 0, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 1, 6, 0, 4, 6, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 3, 3, 1, bounds);
            this.fillWithOutline(world, bounds, 3, 1, 2, 3, 3, 2, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 1, 3, 5, 3, 3, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 1, 1, 0, 3, 5, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 1, 6, 5, 3, 6, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 1, 0, 5, 3, 0, Blocks.FENCE.defaultState(), Blocks.FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 9, 1, 0, 9, 3, 0, Blocks.FENCE.defaultState(), Blocks.FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 1, 4, 9, 4, 6, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.setBlockState(world, Blocks.FLOWING_LAVA.defaultState(), 7, 1, 5, bounds);
            this.setBlockState(world, Blocks.FLOWING_LAVA.defaultState(), 8, 1, 5, bounds);
            this.setBlockState(world, Blocks.IRON_BARS.defaultState(), 9, 2, 5, bounds);
            this.setBlockState(world, Blocks.IRON_BARS.defaultState(), 9, 2, 4, bounds);
            this.fillWithOutline(world, bounds, 7, 2, 4, 8, 2, 5, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 6, 1, 3, bounds);
            this.setBlockState(world, Blocks.FURNACE.defaultState(), 6, 2, 3, bounds);
            this.setBlockState(world, Blocks.FURNACE.defaultState(), 6, 3, 3, bounds);
            this.setBlockState(world, Blocks.DOUBLE_STONE_SLAB.defaultState(), 8, 1, 1, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 2, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 2, 4, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 2, 2, 6, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 4, 2, 6, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 2, 1, 4, bounds);
            this.setBlockState(world, Blocks.WOODEN_PRESSURE_PLATE.defaultState(), 2, 2, 4, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 1, 1, 5, bounds);
            this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 3)), 2, 1, 5, bounds);
            this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 1)), 1, 1, 4, bounds);
            if (!this.hasChest && bounds.contains(new BlockPos(this.transformX(5, 5), this.transformY(1), this.transformZ(5, 5)))) {
                this.hasChest = true;
                this.placeChestWithLoot(world, bounds, random, 5, 1, 5, LOOT_ENTRIES, 3 + random.nextInt(6));
            }

            for (int i = 6; i <= 8; i++) {
                if (this.getBlockState(world, i, 0, -1, bounds).getBlock().getMaterial() == Material.AIR
                    && this.getBlockState(world, i, -1, -1, bounds).getBlock().getMaterial() != Material.AIR) {
                    this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 3)), i, 0, -1, bounds);
                }
            }

            for (int k = 0; k < 7; k++) {
                for (int j = 0; j < 10; j++) {
                    this.fillAirColumnUp(world, j, 6, k, bounds);
                    this.fillColumnDown(world, Blocks.COBBLESTONE.defaultState(), j, -1, k, bounds);
                }
            }

            this.spawnVillagers(world, bounds, 7, 1, 1, 1);
            return true;
        }

        @Override
        protected int getVillagerProfession(int index, int profession) {
            return 3;
        }
    }

    public static class ButcherHouse extends VillagePieces.VillagePiece {
        public ButcherHouse() {
        }

        public ButcherHouse(VillagePieces.Start startPiece, int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        public static VillagePieces.ButcherHouse of(
            VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, 0, 0, 0, 9, 7, 11, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new VillagePieces.ButcherHouse(startPiece, generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world, bounds);
                if (this.hpos < 0) {
                    return true;
                }

                this.bounds.move(0, this.hpos - this.bounds.maxY + 7 - 1, 0);
            }

            this.fillWithOutline(world, bounds, 1, 1, 1, 7, 4, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 1, 6, 8, 4, 10, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 0, 6, 8, 0, 10, Blocks.DIRT.defaultState(), Blocks.DIRT.defaultState(), false);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 6, 0, 6, bounds);
            this.fillWithOutline(world, bounds, 2, 1, 6, 2, 1, 10, Blocks.FENCE.defaultState(), Blocks.FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 1, 6, 8, 1, 10, Blocks.FENCE.defaultState(), Blocks.FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 3, 1, 10, 7, 1, 10, Blocks.FENCE.defaultState(), Blocks.FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 1, 7, 0, 4, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 0, 0, 0, 3, 5, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 0, 0, 8, 3, 5, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 0, 7, 1, 0, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 5, 7, 1, 5, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 2, 0, 7, 3, 0, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 2, 5, 7, 3, 5, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 4, 1, 8, 4, 1, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 4, 4, 8, 4, 4, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 5, 2, 8, 5, 3, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 0, 4, 2, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 0, 4, 3, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 8, 4, 2, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 8, 4, 3, bounds);
            int i = this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 3);
            int j = this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 2);

            for (int k = -1; k <= 2; k++) {
                for (int l = 0; l <= 8; l++) {
                    this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(i), l, 4 + k, k, bounds);
                    this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(j), l, 4 + k, 5 - k, bounds);
                }
            }

            this.setBlockState(world, Blocks.LOG.defaultState(), 0, 2, 1, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 0, 2, 4, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 8, 2, 1, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 8, 2, 4, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 2, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 2, 3, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 8, 2, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 8, 2, 3, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 2, 2, 5, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 3, 2, 5, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 5, 2, 0, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 6, 2, 5, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 2, 1, 3, bounds);
            this.setBlockState(world, Blocks.WOODEN_PRESSURE_PLATE.defaultState(), 2, 2, 3, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 1, 1, 4, bounds);
            this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 3)), 2, 1, 4, bounds);
            this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 1)), 1, 1, 3, bounds);
            this.fillWithOutline(world, bounds, 5, 0, 1, 7, 0, 3, Blocks.DOUBLE_STONE_SLAB.defaultState(), Blocks.DOUBLE_STONE_SLAB.defaultState(), false);
            this.setBlockState(world, Blocks.DOUBLE_STONE_SLAB.defaultState(), 6, 1, 1, bounds);
            this.setBlockState(world, Blocks.DOUBLE_STONE_SLAB.defaultState(), 6, 1, 2, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 2, 1, 0, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 2, 2, 0, bounds);
            this.setBlockState(world, Blocks.TORCH.defaultState().set(TorchBlock.FACING, this.facing), 2, 3, 1, bounds);
            this.placeWoodenDoor(world, bounds, random, 2, 1, 0, Direction.byIdHorizontal(this.postProcessBlockMetadata(Blocks.WOODEN_DOOR, 1)));
            if (this.getBlockState(world, 2, 0, -1, bounds).getBlock().getMaterial() == Material.AIR
                && this.getBlockState(world, 2, -1, -1, bounds).getBlock().getMaterial() != Material.AIR) {
                this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 3)), 2, 0, -1, bounds);
            }

            this.setBlockState(world, Blocks.AIR.defaultState(), 6, 1, 5, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 6, 2, 5, bounds);
            this.setBlockState(world, Blocks.TORCH.defaultState().set(TorchBlock.FACING, this.facing.getOpposite()), 6, 3, 4, bounds);
            this.placeWoodenDoor(world, bounds, random, 6, 1, 5, Direction.byIdHorizontal(this.postProcessBlockMetadata(Blocks.WOODEN_DOOR, 1)));

            for (int i1 = 0; i1 < 5; i1++) {
                for (int j1 = 0; j1 < 9; j1++) {
                    this.fillAirColumnUp(world, j1, 7, i1, bounds);
                    this.fillColumnDown(world, Blocks.COBBLESTONE.defaultState(), j1, -1, i1, bounds);
                }
            }

            this.spawnVillagers(world, bounds, 4, 1, 2, 2);
            return true;
        }

        @Override
        protected int getVillagerProfession(int index, int profession) {
            return index == 0 ? 4 : super.getVillagerProfession(index, profession);
        }
    }

    public static class DoubleFarm extends VillagePieces.VillagePiece {
        private Block crop1;
        private Block crop2;
        private Block crop3;
        private Block crop4;

        public DoubleFarm() {
        }

        public DoubleFarm(VillagePieces.Start startPiece, int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.crop1 = this.pickCrop(random);
            this.crop2 = this.pickCrop(random);
            this.crop3 = this.pickCrop(random);
            this.crop4 = this.pickCrop(random);
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putInt("CA", Block.REGISTRY.getId(this.crop1));
            nbt.putInt("CB", Block.REGISTRY.getId(this.crop2));
            nbt.putInt("CC", Block.REGISTRY.getId(this.crop3));
            nbt.putInt("CD", Block.REGISTRY.getId(this.crop4));
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.crop1 = Block.byId(nbt.getInt("CA"));
            this.crop2 = Block.byId(nbt.getInt("CB"));
            this.crop3 = Block.byId(nbt.getInt("CC"));
            this.crop4 = Block.byId(nbt.getInt("CD"));
        }

        private Block pickCrop(Random random) {
            switch (random.nextInt(5)) {
                case 0:
                    return Blocks.CARROTS;
                case 1:
                    return Blocks.POTATOES;
                default:
                    return Blocks.WHEAT;
            }
        }

        public static VillagePieces.DoubleFarm of(
            VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, 0, 0, 0, 13, 4, 9, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new VillagePieces.DoubleFarm(startPiece, generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world, bounds);
                if (this.hpos < 0) {
                    return true;
                }

                this.bounds.move(0, this.hpos - this.bounds.maxY + 4 - 1, 0);
            }

            this.fillWithOutline(world, bounds, 0, 1, 0, 12, 4, 8, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 1, 2, 0, 7, Blocks.FARMLAND.defaultState(), Blocks.FARMLAND.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 0, 1, 5, 0, 7, Blocks.FARMLAND.defaultState(), Blocks.FARMLAND.defaultState(), false);
            this.fillWithOutline(world, bounds, 7, 0, 1, 8, 0, 7, Blocks.FARMLAND.defaultState(), Blocks.FARMLAND.defaultState(), false);
            this.fillWithOutline(world, bounds, 10, 0, 1, 11, 0, 7, Blocks.FARMLAND.defaultState(), Blocks.FARMLAND.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 0, 0, 0, 0, 8, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 0, 0, 6, 0, 8, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 12, 0, 0, 12, 0, 8, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 0, 11, 0, 0, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 8, 11, 0, 8, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 3, 0, 1, 3, 0, 7, Blocks.WATER.defaultState(), Blocks.WATER.defaultState(), false);
            this.fillWithOutline(world, bounds, 9, 0, 1, 9, 0, 7, Blocks.WATER.defaultState(), Blocks.WATER.defaultState(), false);

            for (int i = 1; i <= 7; i++) {
                this.setBlockState(world, this.crop1.getStateFromMetadata(MathHelper.nextInt(random, 2, 7)), 1, 1, i, bounds);
                this.setBlockState(world, this.crop1.getStateFromMetadata(MathHelper.nextInt(random, 2, 7)), 2, 1, i, bounds);
                this.setBlockState(world, this.crop2.getStateFromMetadata(MathHelper.nextInt(random, 2, 7)), 4, 1, i, bounds);
                this.setBlockState(world, this.crop2.getStateFromMetadata(MathHelper.nextInt(random, 2, 7)), 5, 1, i, bounds);
                this.setBlockState(world, this.crop3.getStateFromMetadata(MathHelper.nextInt(random, 2, 7)), 7, 1, i, bounds);
                this.setBlockState(world, this.crop3.getStateFromMetadata(MathHelper.nextInt(random, 2, 7)), 8, 1, i, bounds);
                this.setBlockState(world, this.crop4.getStateFromMetadata(MathHelper.nextInt(random, 2, 7)), 10, 1, i, bounds);
                this.setBlockState(world, this.crop4.getStateFromMetadata(MathHelper.nextInt(random, 2, 7)), 11, 1, i, bounds);
            }

            for (int k = 0; k < 9; k++) {
                for (int j = 0; j < 13; j++) {
                    this.fillAirColumnUp(world, j, 4, k, bounds);
                    this.fillColumnDown(world, Blocks.DIRT.defaultState(), j, -1, k, bounds);
                }
            }

            return true;
        }
    }

    public static class Farm extends VillagePieces.VillagePiece {
        private Block crop1;
        private Block crop2;

        public Farm() {
        }

        public Farm(VillagePieces.Start startPiece, int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.crop1 = this.pickCrop(random);
            this.crop2 = this.pickCrop(random);
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putInt("CA", Block.REGISTRY.getId(this.crop1));
            nbt.putInt("CB", Block.REGISTRY.getId(this.crop2));
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.crop1 = Block.byId(nbt.getInt("CA"));
            this.crop2 = Block.byId(nbt.getInt("CB"));
        }

        private Block pickCrop(Random random) {
            switch (random.nextInt(5)) {
                case 0:
                    return Blocks.CARROTS;
                case 1:
                    return Blocks.POTATOES;
                default:
                    return Blocks.WHEAT;
            }
        }

        public static VillagePieces.Farm create(
            VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, 0, 0, 0, 7, 4, 9, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new VillagePieces.Farm(startPiece, generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world, bounds);
                if (this.hpos < 0) {
                    return true;
                }

                this.bounds.move(0, this.hpos - this.bounds.maxY + 4 - 1, 0);
            }

            this.fillWithOutline(world, bounds, 0, 1, 0, 6, 4, 8, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 1, 2, 0, 7, Blocks.FARMLAND.defaultState(), Blocks.FARMLAND.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 0, 1, 5, 0, 7, Blocks.FARMLAND.defaultState(), Blocks.FARMLAND.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 0, 0, 0, 0, 8, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 0, 0, 6, 0, 8, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 0, 5, 0, 0, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 8, 5, 0, 8, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 3, 0, 1, 3, 0, 7, Blocks.WATER.defaultState(), Blocks.WATER.defaultState(), false);

            for (int i = 1; i <= 7; i++) {
                this.setBlockState(world, this.crop1.getStateFromMetadata(MathHelper.nextInt(random, 2, 7)), 1, 1, i, bounds);
                this.setBlockState(world, this.crop1.getStateFromMetadata(MathHelper.nextInt(random, 2, 7)), 2, 1, i, bounds);
                this.setBlockState(world, this.crop2.getStateFromMetadata(MathHelper.nextInt(random, 2, 7)), 4, 1, i, bounds);
                this.setBlockState(world, this.crop2.getStateFromMetadata(MathHelper.nextInt(random, 2, 7)), 5, 1, i, bounds);
            }

            for (int k = 0; k < 9; k++) {
                for (int j = 0; j < 7; j++) {
                    this.fillAirColumnUp(world, j, 4, k, bounds);
                    this.fillColumnDown(world, Blocks.DIRT.defaultState(), j, -1, k, bounds);
                }
            }

            return true;
        }
    }

    public static class FarmerHouse extends VillagePieces.VillagePiece {
        private boolean lowCeiling;
        private int tablePosition;

        public FarmerHouse() {
        }

        public FarmerHouse(VillagePieces.Start startPiece, int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.lowCeiling = random.nextBoolean();
            this.tablePosition = random.nextInt(3);
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putInt("T", this.tablePosition);
            nbt.putBoolean("C", this.lowCeiling);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.tablePosition = nbt.getInt("T");
            this.lowCeiling = nbt.getBoolean("C");
        }

        public static VillagePieces.FarmerHouse of(
            VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, 0, 0, 0, 4, 6, 5, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new VillagePieces.FarmerHouse(startPiece, generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world, bounds);
                if (this.hpos < 0) {
                    return true;
                }

                this.bounds.move(0, this.hpos - this.bounds.maxY + 6 - 1, 0);
            }

            this.fillWithOutline(world, bounds, 1, 1, 1, 3, 5, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 0, 0, 3, 0, 4, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 1, 2, 0, 3, Blocks.DIRT.defaultState(), Blocks.DIRT.defaultState(), false);
            if (this.lowCeiling) {
                this.fillWithOutline(world, bounds, 1, 4, 1, 2, 4, 3, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            } else {
                this.fillWithOutline(world, bounds, 1, 5, 1, 2, 5, 3, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            }

            this.setBlockState(world, Blocks.LOG.defaultState(), 1, 4, 0, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 2, 4, 0, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 1, 4, 4, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 2, 4, 4, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 0, 4, 1, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 0, 4, 2, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 0, 4, 3, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 3, 4, 1, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 3, 4, 2, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 3, 4, 3, bounds);
            this.fillWithOutline(world, bounds, 0, 1, 0, 0, 3, 0, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 3, 1, 0, 3, 3, 0, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 1, 4, 0, 3, 4, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 3, 1, 4, 3, 3, 4, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 1, 1, 0, 3, 3, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 3, 1, 1, 3, 3, 3, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 1, 0, 2, 3, 0, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 1, 4, 2, 3, 4, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 2, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 3, 2, 2, bounds);
            if (this.tablePosition > 0) {
                this.setBlockState(world, Blocks.FENCE.defaultState(), this.tablePosition, 1, 3, bounds);
                this.setBlockState(world, Blocks.WOODEN_PRESSURE_PLATE.defaultState(), this.tablePosition, 2, 3, bounds);
            }

            this.setBlockState(world, Blocks.AIR.defaultState(), 1, 1, 0, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 1, 2, 0, bounds);
            this.placeWoodenDoor(world, bounds, random, 1, 1, 0, Direction.byIdHorizontal(this.postProcessBlockMetadata(Blocks.WOODEN_DOOR, 1)));
            if (this.getBlockState(world, 1, 0, -1, bounds).getBlock().getMaterial() == Material.AIR
                && this.getBlockState(world, 1, -1, -1, bounds).getBlock().getMaterial() != Material.AIR) {
                this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 3)), 1, 0, -1, bounds);
            }

            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 4; j++) {
                    this.fillAirColumnUp(world, j, 6, i, bounds);
                    this.fillColumnDown(world, Blocks.COBBLESTONE.defaultState(), j, -1, i, bounds);
                }
            }

            this.spawnVillagers(world, bounds, 1, 1, 2, 1);
            return true;
        }
    }

    public static class LampPost extends VillagePieces.VillagePiece {
        public LampPost() {
        }

        public LampPost(VillagePieces.Start startPiece, int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        public static StructureBox findSize(VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing) {
            StructureBox structurebox = StructureBox.orient(x, y, z, 0, 0, 0, 3, 4, 2, facing);
            return StructurePiece.getIntersectingPiece(pieces, structurebox) != null ? null : structurebox;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world, bounds);
                if (this.hpos < 0) {
                    return true;
                }

                this.bounds.move(0, this.hpos - this.bounds.maxY + 4 - 1, 0);
            }

            this.fillWithOutline(world, bounds, 0, 0, 0, 2, 3, 1, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 1, 0, 0, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 1, 1, 0, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 1, 2, 0, bounds);
            this.setBlockState(world, Blocks.WOOL.getStateFromMetadata(DyeColor.WHITE.getMetadata()), 1, 3, 0, bounds);
            boolean flag = this.facing == Direction.EAST || this.facing == Direction.NORTH;
            this.setBlockState(world, Blocks.TORCH.defaultState().set(TorchBlock.FACING, this.facing.clockwiseY()), flag ? 2 : 0, 3, 0, bounds);
            this.setBlockState(world, Blocks.TORCH.defaultState().set(TorchBlock.FACING, this.facing), 1, 3, 1, bounds);
            this.setBlockState(world, Blocks.TORCH.defaultState().set(TorchBlock.FACING, this.facing.counterClockwiseY()), flag ? 0 : 2, 3, 0, bounds);
            this.setBlockState(world, Blocks.TORCH.defaultState().set(TorchBlock.FACING, this.facing.getOpposite()), 1, 3, -1, bounds);
            return true;
        }
    }

    public static class LargeHouse extends VillagePieces.VillagePiece {
        public LargeHouse() {
        }

        public LargeHouse(VillagePieces.Start startPiece, int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        public static VillagePieces.LargeHouse of(
            VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, 0, 0, 0, 9, 7, 12, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new VillagePieces.LargeHouse(startPiece, generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world, bounds);
                if (this.hpos < 0) {
                    return true;
                }

                this.bounds.move(0, this.hpos - this.bounds.maxY + 7 - 1, 0);
            }

            this.fillWithOutline(world, bounds, 1, 1, 1, 7, 4, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 1, 6, 8, 4, 10, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 0, 5, 8, 0, 10, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 1, 7, 0, 4, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 0, 0, 0, 3, 5, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 0, 0, 8, 3, 10, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 0, 7, 2, 0, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 5, 2, 1, 5, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 0, 6, 2, 3, 10, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 3, 0, 10, 7, 3, 10, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 2, 0, 7, 3, 0, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 2, 5, 2, 3, 5, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 4, 1, 8, 4, 1, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 4, 4, 3, 4, 4, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 5, 2, 8, 5, 3, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 0, 4, 2, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 0, 4, 3, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 8, 4, 2, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 8, 4, 3, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 8, 4, 4, bounds);
            int i = this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 3);
            int j = this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 2);

            for (int k = -1; k <= 2; k++) {
                for (int l = 0; l <= 8; l++) {
                    this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(i), l, 4 + k, k, bounds);
                    if ((k > -1 || l <= 1) && (k > 0 || l <= 3) && (k > 1 || l <= 4 || l >= 6)) {
                        this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(j), l, 4 + k, 5 - k, bounds);
                    }
                }
            }

            this.fillWithOutline(world, bounds, 3, 4, 5, 3, 4, 10, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 7, 4, 2, 7, 4, 10, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 5, 4, 4, 5, 10, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 5, 4, 6, 5, 10, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 6, 3, 5, 6, 10, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            int k1 = this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 0);

            for (int l1 = 4; l1 >= 1; l1--) {
                this.setBlockState(world, Blocks.PLANKS.defaultState(), l1, 2 + l1, 7 - l1, bounds);

                for (int i1 = 8 - l1; i1 <= 10; i1++) {
                    this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(k1), l1, 2 + l1, i1, bounds);
                }
            }

            int i2 = this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 1);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 6, 6, 3, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 7, 5, 4, bounds);
            this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(i2), 6, 6, 4, bounds);

            for (int j2 = 6; j2 <= 8; j2++) {
                for (int j1 = 5; j1 <= 10; j1++) {
                    this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(i2), j2, 12 - j2, j1, bounds);
                }
            }

            this.setBlockState(world, Blocks.LOG.defaultState(), 0, 2, 1, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 0, 2, 4, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 2, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 2, 3, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 4, 2, 0, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 5, 2, 0, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 6, 2, 0, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 8, 2, 1, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 8, 2, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 8, 2, 3, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 8, 2, 4, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 8, 2, 5, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 8, 2, 6, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 8, 2, 7, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 8, 2, 8, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 8, 2, 9, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 2, 2, 6, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 2, 2, 7, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 2, 2, 8, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 2, 2, 9, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 4, 4, 10, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 5, 4, 10, bounds);
            this.setBlockState(world, Blocks.LOG.defaultState(), 6, 4, 10, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 5, 5, 10, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 2, 1, 0, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 2, 2, 0, bounds);
            this.setBlockState(world, Blocks.TORCH.defaultState().set(TorchBlock.FACING, this.facing), 2, 3, 1, bounds);
            this.placeWoodenDoor(world, bounds, random, 2, 1, 0, Direction.byIdHorizontal(this.postProcessBlockMetadata(Blocks.WOODEN_DOOR, 1)));
            this.fillWithOutline(world, bounds, 1, 0, -1, 3, 2, -1, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            if (this.getBlockState(world, 2, 0, -1, bounds).getBlock().getMaterial() == Material.AIR
                && this.getBlockState(world, 2, -1, -1, bounds).getBlock().getMaterial() != Material.AIR) {
                this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 3)), 2, 0, -1, bounds);
            }

            for (int k2 = 0; k2 < 5; k2++) {
                for (int i3 = 0; i3 < 9; i3++) {
                    this.fillAirColumnUp(world, i3, 7, k2, bounds);
                    this.fillColumnDown(world, Blocks.COBBLESTONE.defaultState(), i3, -1, k2, bounds);
                }
            }

            for (int l2 = 5; l2 < 11; l2++) {
                for (int j3 = 2; j3 < 9; j3++) {
                    this.fillAirColumnUp(world, j3, 7, l2, bounds);
                    this.fillColumnDown(world, Blocks.COBBLESTONE.defaultState(), j3, -1, l2, bounds);
                }
            }

            this.spawnVillagers(world, bounds, 4, 1, 2, 2);
            return true;
        }
    }

    public static class LibrarianHouse extends VillagePieces.VillagePiece {
        public LibrarianHouse() {
        }

        public LibrarianHouse(VillagePieces.Start startPiece, int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        public static VillagePieces.LibrarianHouse of(
            VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, 0, 0, 0, 9, 9, 6, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new VillagePieces.LibrarianHouse(startPiece, generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world, bounds);
                if (this.hpos < 0) {
                    return true;
                }

                this.bounds.move(0, this.hpos - this.bounds.maxY + 9 - 1, 0);
            }

            this.fillWithOutline(world, bounds, 1, 1, 1, 7, 5, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 0, 0, 8, 0, 5, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 5, 0, 8, 5, 5, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 6, 1, 8, 6, 4, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 7, 2, 8, 7, 3, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            int i = this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 3);
            int j = this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 2);

            for (int k = -1; k <= 2; k++) {
                for (int l = 0; l <= 8; l++) {
                    this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(i), l, 6 + k, k, bounds);
                    this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(j), l, 6 + k, 5 - k, bounds);
                }
            }

            this.fillWithOutline(world, bounds, 0, 1, 0, 0, 1, 5, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 1, 5, 8, 1, 5, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 1, 0, 8, 1, 4, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 1, 0, 7, 1, 0, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 0, 4, 0, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 5, 0, 4, 5, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 2, 5, 8, 4, 5, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 2, 0, 8, 4, 0, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 1, 0, 4, 4, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 2, 5, 7, 4, 5, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 2, 1, 8, 4, 4, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 2, 0, 7, 4, 0, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 4, 2, 0, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 5, 2, 0, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 6, 2, 0, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 4, 3, 0, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 5, 3, 0, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 6, 3, 0, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 2, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 2, 3, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 3, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 3, 3, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 8, 2, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 8, 2, 3, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 8, 3, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 8, 3, 3, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 2, 2, 5, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 3, 2, 5, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 5, 2, 5, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 6, 2, 5, bounds);
            this.fillWithOutline(world, bounds, 1, 4, 1, 7, 4, 1, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 4, 4, 7, 4, 4, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 3, 4, 7, 3, 4, Blocks.BOOKSHELF.defaultState(), Blocks.BOOKSHELF.defaultState(), false);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 7, 1, 4, bounds);
            this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 0)), 7, 1, 3, bounds);
            int j1 = this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 3);
            this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(j1), 6, 1, 4, bounds);
            this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(j1), 5, 1, 4, bounds);
            this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(j1), 4, 1, 4, bounds);
            this.setBlockState(world, Blocks.OAK_STAIRS.getStateFromMetadata(j1), 3, 1, 4, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 6, 1, 3, bounds);
            this.setBlockState(world, Blocks.WOODEN_PRESSURE_PLATE.defaultState(), 6, 2, 3, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 4, 1, 3, bounds);
            this.setBlockState(world, Blocks.WOODEN_PRESSURE_PLATE.defaultState(), 4, 2, 3, bounds);
            this.setBlockState(world, Blocks.CRAFTING_TABLE.defaultState(), 7, 1, 1, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 1, 1, 0, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 1, 2, 0, bounds);
            this.placeWoodenDoor(world, bounds, random, 1, 1, 0, Direction.byIdHorizontal(this.postProcessBlockMetadata(Blocks.WOODEN_DOOR, 1)));
            if (this.getBlockState(world, 1, 0, -1, bounds).getBlock().getMaterial() == Material.AIR
                && this.getBlockState(world, 1, -1, -1, bounds).getBlock().getMaterial() != Material.AIR) {
                this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 3)), 1, 0, -1, bounds);
            }

            for (int k1 = 0; k1 < 6; k1++) {
                for (int i1 = 0; i1 < 9; i1++) {
                    this.fillAirColumnUp(world, i1, 9, k1, bounds);
                    this.fillColumnDown(world, Blocks.COBBLESTONE.defaultState(), i1, -1, k1, bounds);
                }
            }

            this.spawnVillagers(world, bounds, 2, 1, 2, 1);
            return true;
        }

        @Override
        protected int getVillagerProfession(int index, int profession) {
            return 1;
        }
    }

    public static class PriestHouse extends VillagePieces.VillagePiece {
        public PriestHouse() {
        }

        public PriestHouse(VillagePieces.Start startPiece, int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        public static VillagePieces.PriestHouse of(
            VillagePieces.Start statPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, 0, 0, 0, 5, 12, 9, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new VillagePieces.PriestHouse(statPiece, generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world, bounds);
                if (this.hpos < 0) {
                    return true;
                }

                this.bounds.move(0, this.hpos - this.bounds.maxY + 12 - 1, 0);
            }

            this.fillWithOutline(world, bounds, 1, 1, 1, 3, 3, 7, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 5, 1, 3, 9, 3, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 0, 3, 0, 8, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 1, 0, 3, 10, 0, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 1, 1, 0, 10, 3, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 1, 1, 4, 10, 3, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 0, 4, 0, 4, 7, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 0, 4, 4, 4, 7, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 1, 8, 3, 4, 8, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 5, 4, 3, 10, 4, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 5, 5, 3, 5, 7, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 9, 0, 4, 9, 4, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 4, 0, 4, 4, 4, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 0, 11, 2, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 4, 11, 2, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 2, 11, 0, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 2, 11, 4, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 1, 1, 6, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 1, 1, 7, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 2, 1, 7, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 3, 1, 6, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 3, 1, 7, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 3)), 1, 1, 5, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 3)), 2, 1, 6, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 3)), 3, 1, 5, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 1)), 1, 2, 7, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 0)), 3, 2, 7, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 2, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 3, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 4, 2, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 4, 3, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 6, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 7, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 4, 6, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 4, 7, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 2, 6, 0, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 2, 7, 0, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 2, 6, 4, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 2, 7, 4, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 3, 6, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 4, 3, 6, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 2, 3, 8, bounds);
            this.setBlockState(world, Blocks.TORCH.defaultState().set(TorchBlock.FACING, this.facing.getOpposite()), 2, 4, 7, bounds);
            this.setBlockState(world, Blocks.TORCH.defaultState().set(TorchBlock.FACING, this.facing.clockwiseY()), 1, 4, 6, bounds);
            this.setBlockState(world, Blocks.TORCH.defaultState().set(TorchBlock.FACING, this.facing.counterClockwiseY()), 3, 4, 6, bounds);
            this.setBlockState(world, Blocks.TORCH.defaultState().set(TorchBlock.FACING, this.facing), 2, 4, 5, bounds);
            int i = this.postProcessBlockMetadata(Blocks.LADDER, 4);

            for (int j = 1; j <= 9; j++) {
                this.setBlockState(world, Blocks.LADDER.getStateFromMetadata(i), 3, j, 3, bounds);
            }

            this.setBlockState(world, Blocks.AIR.defaultState(), 2, 1, 0, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 2, 2, 0, bounds);
            this.placeWoodenDoor(world, bounds, random, 2, 1, 0, Direction.byIdHorizontal(this.postProcessBlockMetadata(Blocks.WOODEN_DOOR, 1)));
            if (this.getBlockState(world, 2, 0, -1, bounds).getBlock().getMaterial() == Material.AIR
                && this.getBlockState(world, 2, -1, -1, bounds).getBlock().getMaterial() != Material.AIR) {
                this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 3)), 2, 0, -1, bounds);
            }

            for (int l = 0; l < 9; l++) {
                for (int k = 0; k < 5; k++) {
                    this.fillAirColumnUp(world, k, 12, l, bounds);
                    this.fillColumnDown(world, Blocks.COBBLESTONE.defaultState(), k, -1, l, bounds);
                }
            }

            this.spawnVillagers(world, bounds, 2, 1, 2, 1);
            return true;
        }

        @Override
        protected int getVillagerProfession(int index, int profession) {
            return 2;
        }
    }

    public static class Road extends VillagePieces.RoadPiece {
        private int length;

        public Road() {
        }

        public Road(VillagePieces.Start startPiece, int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.length = Math.max(bounds.getSpanX(), bounds.getSpanZ());
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putInt("Length", this.length);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.length = nbt.getInt("Length");
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            boolean flag = false;

            for (int i = random.nextInt(5); i < this.length - 8; i += 2 + random.nextInt(5)) {
                StructurePiece structurepiece = this.generatePieceLeft((VillagePieces.Start)start, pieces, random, 0, i);
                if (structurepiece != null) {
                    i += Math.max(structurepiece.bounds.getSpanX(), structurepiece.bounds.getSpanZ());
                    flag = true;
                }
            }

            for (int j = random.nextInt(5); j < this.length - 8; j += 2 + random.nextInt(5)) {
                StructurePiece structurepiece1 = this.generatePieceRight((VillagePieces.Start)start, pieces, random, 0, j);
                if (structurepiece1 != null) {
                    j += Math.max(structurepiece1.bounds.getSpanX(), structurepiece1.bounds.getSpanZ());
                    flag = true;
                }
            }

            if (flag && random.nextInt(3) > 0 && this.facing != null) {
                switch (this.facing) {
                    case NORTH:
                        VillagePieces.generateRoadPiece(
                            (VillagePieces.Start)start,
                            pieces,
                            random,
                            this.bounds.minX - 1,
                            this.bounds.minY,
                            this.bounds.minZ,
                            Direction.WEST,
                            this.getGenerationDepth()
                        );
                        break;
                    case SOUTH:
                        VillagePieces.generateRoadPiece(
                            (VillagePieces.Start)start,
                            pieces,
                            random,
                            this.bounds.minX - 1,
                            this.bounds.minY,
                            this.bounds.maxZ - 2,
                            Direction.WEST,
                            this.getGenerationDepth()
                        );
                        break;
                    case WEST:
                        VillagePieces.generateRoadPiece(
                            (VillagePieces.Start)start,
                            pieces,
                            random,
                            this.bounds.minX,
                            this.bounds.minY,
                            this.bounds.minZ - 1,
                            Direction.NORTH,
                            this.getGenerationDepth()
                        );
                        break;
                    case EAST:
                        VillagePieces.generateRoadPiece(
                            (VillagePieces.Start)start,
                            pieces,
                            random,
                            this.bounds.maxX - 2,
                            this.bounds.minY,
                            this.bounds.minZ - 1,
                            Direction.NORTH,
                            this.getGenerationDepth()
                        );
                }
            }

            if (flag && random.nextInt(3) > 0 && this.facing != null) {
                switch (this.facing) {
                    case NORTH:
                        VillagePieces.generateRoadPiece(
                            (VillagePieces.Start)start,
                            pieces,
                            random,
                            this.bounds.maxX + 1,
                            this.bounds.minY,
                            this.bounds.minZ,
                            Direction.EAST,
                            this.getGenerationDepth()
                        );
                        break;
                    case SOUTH:
                        VillagePieces.generateRoadPiece(
                            (VillagePieces.Start)start,
                            pieces,
                            random,
                            this.bounds.maxX + 1,
                            this.bounds.minY,
                            this.bounds.maxZ - 2,
                            Direction.EAST,
                            this.getGenerationDepth()
                        );
                        break;
                    case WEST:
                        VillagePieces.generateRoadPiece(
                            (VillagePieces.Start)start,
                            pieces,
                            random,
                            this.bounds.minX,
                            this.bounds.minY,
                            this.bounds.maxZ + 1,
                            Direction.SOUTH,
                            this.getGenerationDepth()
                        );
                        break;
                    case EAST:
                        VillagePieces.generateRoadPiece(
                            (VillagePieces.Start)start,
                            pieces,
                            random,
                            this.bounds.maxX - 2,
                            this.bounds.minY,
                            this.bounds.maxZ + 1,
                            Direction.SOUTH,
                            this.getGenerationDepth()
                        );
                }
            }
        }

        public static StructureBox findSize(VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing) {
            for (int i = 7 * MathHelper.nextInt(random, 3, 5); i >= 7; i -= 7) {
                StructureBox structurebox = StructureBox.orient(x, y, z, 0, 0, 0, 3, 3, i, facing);
                if (StructurePiece.getIntersectingPiece(pieces, structurebox) == null) {
                    return structurebox;
                }
            }

            return null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            BlockState blockstate = this.getBlockForVariant(Blocks.GRAVEL.defaultState());
            BlockState blockstate1 = this.getBlockForVariant(Blocks.COBBLESTONE.defaultState());

            for (int i = this.bounds.minX; i <= this.bounds.maxX; i++) {
                for (int j = this.bounds.minZ; j <= this.bounds.maxZ; j++) {
                    BlockPos blockpos = new BlockPos(i, 64, j);
                    if (bounds.contains(blockpos)) {
                        blockpos = world.getSurfaceHeight(blockpos).down();
                        world.setBlockState(blockpos, blockstate, 2);
                        world.setBlockState(blockpos.down(), blockstate1, 2);
                    }
                }
            }

            return true;
        }
    }

    public abstract static class RoadPiece extends VillagePieces.VillagePiece {
        public RoadPiece() {
        }

        protected RoadPiece(VillagePieces.Start start, int i) {
            super(start, i);
        }
    }

    public static class SmallHouse extends VillagePieces.VillagePiece {
        private boolean terrace;

        public SmallHouse() {
        }

        public SmallHouse(VillagePieces.Start statPiece, int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(statPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.terrace = random.nextBoolean();
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putBoolean("Terrace", this.terrace);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.terrace = nbt.getBoolean("Terrace");
        }

        public static VillagePieces.SmallHouse of(
            VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, 0, 0, 0, 5, 6, 5, facing);
            return StructurePiece.getIntersectingPiece(pieces, structurebox) != null
                ? null
                : new VillagePieces.SmallHouse(startPiece, generationDepth, random, structurebox, facing);
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world, bounds);
                if (this.hpos < 0) {
                    return true;
                }

                this.bounds.move(0, this.hpos - this.bounds.maxY + 6 - 1, 0);
            }

            this.fillWithOutline(world, bounds, 0, 0, 0, 4, 0, 4, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 4, 0, 4, 4, 4, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 4, 1, 3, 4, 3, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 0, 1, 0, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 0, 2, 0, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 0, 3, 0, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 4, 1, 0, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 4, 2, 0, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 4, 3, 0, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 0, 1, 4, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 0, 2, 4, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 0, 3, 4, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 4, 1, 4, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 4, 2, 4, bounds);
            this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 4, 3, 4, bounds);
            this.fillWithOutline(world, bounds, 0, 1, 1, 0, 3, 3, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 1, 1, 4, 3, 3, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 1, 4, 3, 3, 4, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 0, 2, 2, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 2, 2, 4, bounds);
            this.setBlockState(world, Blocks.GLASS_PANE.defaultState(), 4, 2, 2, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 1, 1, 0, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 1, 2, 0, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 1, 3, 0, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 2, 3, 0, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 3, 3, 0, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 3, 2, 0, bounds);
            this.setBlockState(world, Blocks.PLANKS.defaultState(), 3, 1, 0, bounds);
            if (this.getBlockState(world, 2, 0, -1, bounds).getBlock().getMaterial() == Material.AIR
                && this.getBlockState(world, 2, -1, -1, bounds).getBlock().getMaterial() != Material.AIR) {
                this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 3)), 2, 0, -1, bounds);
            }

            this.fillWithOutline(world, bounds, 1, 1, 1, 3, 3, 3, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            if (this.terrace) {
                this.setBlockState(world, Blocks.FENCE.defaultState(), 0, 5, 0, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 1, 5, 0, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 2, 5, 0, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 3, 5, 0, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 4, 5, 0, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 0, 5, 4, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 1, 5, 4, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 2, 5, 4, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 3, 5, 4, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 4, 5, 4, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 4, 5, 1, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 4, 5, 2, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 4, 5, 3, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 0, 5, 1, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 0, 5, 2, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 0, 5, 3, bounds);
            }

            if (this.terrace) {
                int i = this.postProcessBlockMetadata(Blocks.LADDER, 3);
                this.setBlockState(world, Blocks.LADDER.getStateFromMetadata(i), 3, 1, 3, bounds);
                this.setBlockState(world, Blocks.LADDER.getStateFromMetadata(i), 3, 2, 3, bounds);
                this.setBlockState(world, Blocks.LADDER.getStateFromMetadata(i), 3, 3, 3, bounds);
                this.setBlockState(world, Blocks.LADDER.getStateFromMetadata(i), 3, 4, 3, bounds);
            }

            this.setBlockState(world, Blocks.TORCH.defaultState().set(TorchBlock.FACING, this.facing), 2, 3, 1, bounds);

            for (int k = 0; k < 5; k++) {
                for (int j = 0; j < 5; j++) {
                    this.fillAirColumnUp(world, j, 6, k, bounds);
                    this.fillColumnDown(world, Blocks.COBBLESTONE.defaultState(), j, -1, k, bounds);
                }
            }

            this.spawnVillagers(world, bounds, 1, 1, 2, 1);
            return true;
        }
    }

    public static class Start extends VillagePieces.Well {
        public BiomeSource biomeSource;
        public boolean isDesertVillage;
        public int size;
        public VillagePieces.VillagePieceWeight previous;
        public List<VillagePieces.VillagePieceWeight> weights;
        public List<StructurePiece> buildingPieces = Lists.newArrayList();
        public List<StructurePiece> roadPieces = Lists.newArrayList();

        public Start() {
        }

        public Start(BiomeSource biomeSource, int i, Random random, int x, int z, List<VillagePieces.VillagePieceWeight> weights, int size) {
            super(null, 0, random, x, z);
            this.biomeSource = biomeSource;
            this.weights = weights;
            this.size = size;
            Biome biome = biomeSource.getBiome(new BlockPos(x, 0, z), Biome.DEFAULT);
            this.isDesertVillage = biome == Biome.DESERT || biome == Biome.DESERT_HILLS;
            this.setDesert(this.isDesertVillage);
        }

        public BiomeSource getBiomeSource() {
            return this.biomeSource;
        }
    }

    abstract static class VillagePiece extends StructurePiece {
        protected int hpos = -1;
        private int villageCount;
        private boolean desert;

        public VillagePiece() {
        }

        protected VillagePiece(VillagePieces.Start startPiece, int generationDepth) {
            super(generationDepth);
            if (startPiece != null) {
                this.desert = startPiece.isDesertVillage;
            }
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            nbt.putInt("HPos", this.hpos);
            nbt.putInt("VCount", this.villageCount);
            nbt.putBoolean("Desert", this.desert);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            this.hpos = nbt.getInt("HPos");
            this.villageCount = nbt.getInt("VCount");
            this.desert = nbt.getBoolean("Desert");
        }

        protected StructurePiece generatePieceLeft(VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int yOffset, int offset) {
            if (this.facing != null) {
                switch (this.facing) {
                    case NORTH:
                        return VillagePieces.generateBuildingPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX - 1,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ + offset,
                            Direction.WEST,
                            this.getGenerationDepth()
                        );
                    case SOUTH:
                        return VillagePieces.generateBuildingPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX - 1,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ + offset,
                            Direction.WEST,
                            this.getGenerationDepth()
                        );
                    case WEST:
                        return VillagePieces.generateBuildingPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX + offset,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ - 1,
                            Direction.NORTH,
                            this.getGenerationDepth()
                        );
                    case EAST:
                        return VillagePieces.generateBuildingPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX + offset,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ - 1,
                            Direction.NORTH,
                            this.getGenerationDepth()
                        );
                }
            }

            return null;
        }

        protected StructurePiece generatePieceRight(VillagePieces.Start startPiece, List<StructurePiece> pieces, Random random, int yOffset, int offset) {
            if (this.facing != null) {
                switch (this.facing) {
                    case NORTH:
                        return VillagePieces.generateBuildingPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.maxX + 1,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ + offset,
                            Direction.EAST,
                            this.getGenerationDepth()
                        );
                    case SOUTH:
                        return VillagePieces.generateBuildingPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.maxX + 1,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ + offset,
                            Direction.EAST,
                            this.getGenerationDepth()
                        );
                    case WEST:
                        return VillagePieces.generateBuildingPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX + offset,
                            this.bounds.minY + yOffset,
                            this.bounds.maxZ + 1,
                            Direction.SOUTH,
                            this.getGenerationDepth()
                        );
                    case EAST:
                        return VillagePieces.generateBuildingPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX + offset,
                            this.bounds.minY + yOffset,
                            this.bounds.maxZ + 1,
                            Direction.SOUTH,
                            this.getGenerationDepth()
                        );
                }
            }

            return null;
        }

        protected int updateHeight(World world, StructureBox bounds) {
            int i = 0;
            int j = 0;
            BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

            for (int k = this.bounds.minZ; k <= this.bounds.maxZ; k++) {
                for (int l = this.bounds.minX; l <= this.bounds.maxX; l++) {
                    blockpos$mutable.set(l, 64, k);
                    if (bounds.contains(blockpos$mutable)) {
                        i += Math.max(world.getSurfaceHeight(blockpos$mutable).getY(), world.dimension.getMinSpawnY());
                        j++;
                    }
                }
            }

            return j == 0 ? -1 : i / j;
        }

        protected static boolean isValidBounds(StructureBox bounds) {
            return bounds != null && bounds.minY > 10;
        }

        protected void spawnVillagers(World world, StructureBox bounds, int x, int y, int z, int max) {
            if (this.villageCount < max) {
                for (int i = this.villageCount; i < max; i++) {
                    int j = this.transformX(x + i, z);
                    int k = this.transformY(y);
                    int l = this.transformZ(x + i, z);
                    if (!bounds.contains(new BlockPos(j, k, l))) {
                        break;
                    }

                    this.villageCount++;
                    VillagerEntity villagerentity = new VillagerEntity(world);
                    villagerentity.setPositionAndAngles(j + 0.5, k, l + 0.5, 0.0F, 0.0F);
                    villagerentity.initialize(world.getLocalDifficulty(new BlockPos(villagerentity)), null);
                    villagerentity.setProfession(this.getVillagerProfession(i, villagerentity.getProfession()));
                    world.addEntity(villagerentity);
                }
            }
        }

        protected int getVillagerProfession(int index, int profession) {
            return profession;
        }

        protected BlockState getBlockForVariant(BlockState block) {
            if (this.desert) {
                if (block.getBlock() == Blocks.LOG || block.getBlock() == Blocks.LOG2) {
                    return Blocks.SANDSTONE.defaultState();
                }

                if (block.getBlock() == Blocks.COBBLESTONE) {
                    return Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.DEFAULT.getId());
                }

                if (block.getBlock() == Blocks.PLANKS) {
                    return Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId());
                }

                if (block.getBlock() == Blocks.OAK_STAIRS) {
                    return Blocks.SANDSTONE_STAIRS.defaultState().set(StairsBlock.FACING, block.get(StairsBlock.FACING));
                }

                if (block.getBlock() == Blocks.STONE_STAIRS) {
                    return Blocks.SANDSTONE_STAIRS.defaultState().set(StairsBlock.FACING, block.get(StairsBlock.FACING));
                }

                if (block.getBlock() == Blocks.GRAVEL) {
                    return Blocks.SANDSTONE.defaultState();
                }
            }

            return block;
        }

        @Override
        protected void setBlockState(World world, BlockState state, int x, int y, int z, StructureBox bounds) {
            BlockState blockstate = this.getBlockForVariant(state);
            super.setBlockState(world, blockstate, x, y, z, bounds);
        }

        @Override
        protected void fillWithOutline(
            World world, StructureBox bounds, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState edge, BlockState filler, boolean avoidAir
        ) {
            BlockState blockstate = this.getBlockForVariant(edge);
            BlockState blockstate1 = this.getBlockForVariant(filler);
            super.fillWithOutline(world, bounds, minX, minY, minZ, maxX, maxY, maxZ, blockstate, blockstate1, avoidAir);
        }

        @Override
        protected void fillColumnDown(World world, BlockState state, int x, int y, int z, StructureBox bounds) {
            BlockState blockstate = this.getBlockForVariant(state);
            super.fillColumnDown(world, blockstate, x, y, z, bounds);
        }

        protected void setDesert(boolean desert) {
            this.desert = desert;
        }
    }

    public static class VillagePieceWeight {
        public Class<? extends VillagePieces.VillagePiece> type;
        public final int weight;
        public int amountGenerated;
        public int maxAmount;

        public VillagePieceWeight(Class<? extends VillagePieces.VillagePiece> type, int weight, int maxAmount) {
            this.type = type;
            this.weight = weight;
            this.maxAmount = maxAmount;
        }

        public boolean isValid(int generationDepth) {
            return this.maxAmount == 0 || this.amountGenerated < this.maxAmount;
        }

        public boolean isValid() {
            return this.maxAmount == 0 || this.amountGenerated < this.maxAmount;
        }
    }

    public static class Well extends VillagePieces.VillagePiece {
        public Well() {
        }

        public Well(VillagePieces.Start startPiece, int generationDepth, Random random, int x, int z) {
            super(startPiece, generationDepth);
            this.facing = Direction.Plane.HORIZONTAL.pick(random);
            switch (this.facing) {
                case NORTH:
                case SOUTH:
                    this.bounds = new StructureBox(x, 64, z, x + 6 - 1, 78, z + 6 - 1);
                    break;
                default:
                    this.bounds = new StructureBox(x, 64, z, x + 6 - 1, 78, z + 6 - 1);
            }
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            VillagePieces.generateRoadPiece(
                (VillagePieces.Start)start,
                pieces,
                random,
                this.bounds.minX - 1,
                this.bounds.maxY - 4,
                this.bounds.minZ + 1,
                Direction.WEST,
                this.getGenerationDepth()
            );
            VillagePieces.generateRoadPiece(
                (VillagePieces.Start)start,
                pieces,
                random,
                this.bounds.maxX + 1,
                this.bounds.maxY - 4,
                this.bounds.minZ + 1,
                Direction.EAST,
                this.getGenerationDepth()
            );
            VillagePieces.generateRoadPiece(
                (VillagePieces.Start)start,
                pieces,
                random,
                this.bounds.minX + 1,
                this.bounds.maxY - 4,
                this.bounds.minZ - 1,
                Direction.NORTH,
                this.getGenerationDepth()
            );
            VillagePieces.generateRoadPiece(
                (VillagePieces.Start)start,
                pieces,
                random,
                this.bounds.minX + 1,
                this.bounds.maxY - 4,
                this.bounds.maxZ + 1,
                Direction.SOUTH,
                this.getGenerationDepth()
            );
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world, bounds);
                if (this.hpos < 0) {
                    return true;
                }

                this.bounds.move(0, this.hpos - this.bounds.maxY + 3, 0);
            }

            this.fillWithOutline(world, bounds, 1, 0, 1, 4, 12, 4, Blocks.COBBLESTONE.defaultState(), Blocks.FLOWING_WATER.defaultState(), false);
            this.setBlockState(world, Blocks.AIR.defaultState(), 2, 12, 2, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 3, 12, 2, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 2, 12, 3, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 3, 12, 3, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 1, 13, 1, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 1, 14, 1, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 4, 13, 1, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 4, 14, 1, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 1, 13, 4, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 1, 14, 4, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 4, 13, 4, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 4, 14, 4, bounds);
            this.fillWithOutline(world, bounds, 1, 15, 1, 4, 15, 4, Blocks.COBBLESTONE.defaultState(), Blocks.COBBLESTONE.defaultState(), false);

            for (int i = 0; i <= 5; i++) {
                for (int j = 0; j <= 5; j++) {
                    if (j == 0 || j == 5 || i == 0 || i == 5) {
                        this.setBlockState(world, Blocks.GRAVEL.defaultState(), j, 11, i, bounds);
                        this.fillAirColumnUp(world, j, 12, i, bounds);
                    }
                }
            }

            return true;
        }
    }
}
