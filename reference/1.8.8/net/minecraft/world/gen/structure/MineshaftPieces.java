package net.minecraft.world.gen.structure;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class MineshaftPieces {
    private static final List<LootEntry> LOOT_ENTRIES = Lists.newArrayList(
        new LootEntry(Items.IRON_INGOT, 0, 1, 5, 10),
        new LootEntry(Items.GOLD_INGOT, 0, 1, 3, 5),
        new LootEntry(Items.REDSTONE, 0, 4, 9, 5),
        new LootEntry(Items.DYE, DyeColor.BLUE.getMetadata(), 4, 9, 5),
        new LootEntry(Items.DIAMOND, 0, 1, 2, 3),
        new LootEntry(Items.COAL, 0, 3, 8, 10),
        new LootEntry(Items.BREAD, 0, 1, 3, 15),
        new LootEntry(Items.IRON_PICKAXE, 0, 1, 1, 1),
        new LootEntry(Item.byBlock(Blocks.RAIL), 0, 4, 8, 1),
        new LootEntry(Items.MELON_SEEDS, 0, 2, 4, 10),
        new LootEntry(Items.PUMPKIN_SEEDS, 0, 2, 4, 10),
        new LootEntry(Items.SADDLE, 0, 1, 1, 3),
        new LootEntry(Items.IRON_HORSE_ARMOR, 0, 1, 1, 1)
    );

    public static void register() {
        StructureRegistry.registerPiece(MineshaftPieces.Corridor.class, "MSCorridor");
        StructureRegistry.registerPiece(MineshaftPieces.Crossing.class, "MSCrossing");
        StructureRegistry.registerPiece(MineshaftPieces.Room.class, "MSRoom");
        StructureRegistry.registerPiece(MineshaftPieces.Stairs.class, "MSStairs");
    }

    private static StructurePiece createPiece(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
        int i = random.nextInt(100);
        if (i >= 80) {
            StructureBox structurebox = MineshaftPieces.Crossing.findSize(pieces, random, x, y, z, facing);
            if (structurebox != null) {
                return new MineshaftPieces.Crossing(generationDepth, random, structurebox, facing);
            }
        } else if (i >= 70) {
            StructureBox structurebox1 = MineshaftPieces.Stairs.findSize(pieces, random, x, y, z, facing);
            if (structurebox1 != null) {
                return new MineshaftPieces.Stairs(generationDepth, random, structurebox1, facing);
            }
        } else {
            StructureBox structurebox2 = MineshaftPieces.Corridor.findSize(pieces, random, x, y, z, facing);
            if (structurebox2 != null) {
                return new MineshaftPieces.Corridor(generationDepth, random, structurebox2, facing);
            }
        }

        return null;
    }

    private static StructurePiece generateNextPiece(
        StructurePiece startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
    ) {
        if (generationDepth > 8) {
            return null;
        }

        if (Math.abs(x - startPiece.getBounds().minX) <= 80 && Math.abs(z - startPiece.getBounds().minZ) <= 80) {
            StructurePiece structurepiece = createPiece(pieces, random, x, y, z, facing, generationDepth + 1);
            if (structurepiece != null) {
                pieces.add(structurepiece);
                structurepiece.addChildren(startPiece, pieces, random);
            }

            return structurepiece;
        } else {
            return null;
        }
    }

    public static class Corridor extends StructurePiece {
        private boolean hasRails;
        private boolean hasCobwebs;
        private boolean hasSpiderSpawner;
        private int sections;

        public Corridor() {
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            nbt.putBoolean("hr", this.hasRails);
            nbt.putBoolean("sc", this.hasCobwebs);
            nbt.putBoolean("hps", this.hasSpiderSpawner);
            nbt.putInt("Num", this.sections);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            this.hasRails = nbt.getBoolean("hr");
            this.hasCobwebs = nbt.getBoolean("sc");
            this.hasSpiderSpawner = nbt.getBoolean("hps");
            this.sections = nbt.getInt("Num");
        }

        public Corridor(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.hasRails = random.nextInt(3) == 0;
            this.hasCobwebs = !this.hasRails && random.nextInt(23) == 0;
            if (this.facing != Direction.NORTH && this.facing != Direction.SOUTH) {
                this.sections = bounds.getSpanX() / 5;
            } else {
                this.sections = bounds.getSpanZ() / 5;
            }
        }

        public static StructureBox findSize(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing) {
            StructureBox structurebox = new StructureBox(x, y, z, x, y + 2, z);

            int i;
            for (i = random.nextInt(3) + 2; i > 0; i--) {
                int j = i * 5;
                switch (facing) {
                    case NORTH:
                        structurebox.maxX = x + 2;
                        structurebox.minZ = z - (j - 1);
                        break;
                    case SOUTH:
                        structurebox.maxX = x + 2;
                        structurebox.maxZ = z + (j - 1);
                        break;
                    case WEST:
                        structurebox.minX = x - (j - 1);
                        structurebox.maxZ = z + 2;
                        break;
                    case EAST:
                        structurebox.maxX = x + (j - 1);
                        structurebox.maxZ = z + 2;
                }

                if (StructurePiece.getIntersectingPiece(pieces, structurebox) == null) {
                    break;
                }
            }

            return i > 0 ? structurebox : null;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            int i = this.getGenerationDepth();
            int j = random.nextInt(4);
            if (this.facing != null) {
                switch (this.facing) {
                    case NORTH:
                        if (j <= 1) {
                            MineshaftPieces.generateNextPiece(
                                start, pieces, random, this.bounds.minX, this.bounds.minY - 1 + random.nextInt(3), this.bounds.minZ - 1, this.facing, i
                            );
                        } else if (j == 2) {
                            MineshaftPieces.generateNextPiece(
                                start, pieces, random, this.bounds.minX - 1, this.bounds.minY - 1 + random.nextInt(3), this.bounds.minZ, Direction.WEST, i
                            );
                        } else {
                            MineshaftPieces.generateNextPiece(
                                start, pieces, random, this.bounds.maxX + 1, this.bounds.minY - 1 + random.nextInt(3), this.bounds.minZ, Direction.EAST, i
                            );
                        }
                        break;
                    case SOUTH:
                        if (j <= 1) {
                            MineshaftPieces.generateNextPiece(
                                start, pieces, random, this.bounds.minX, this.bounds.minY - 1 + random.nextInt(3), this.bounds.maxZ + 1, this.facing, i
                            );
                        } else if (j == 2) {
                            MineshaftPieces.generateNextPiece(
                                start, pieces, random, this.bounds.minX - 1, this.bounds.minY - 1 + random.nextInt(3), this.bounds.maxZ - 3, Direction.WEST, i
                            );
                        } else {
                            MineshaftPieces.generateNextPiece(
                                start, pieces, random, this.bounds.maxX + 1, this.bounds.minY - 1 + random.nextInt(3), this.bounds.maxZ - 3, Direction.EAST, i
                            );
                        }
                        break;
                    case WEST:
                        if (j <= 1) {
                            MineshaftPieces.generateNextPiece(
                                start, pieces, random, this.bounds.minX - 1, this.bounds.minY - 1 + random.nextInt(3), this.bounds.minZ, this.facing, i
                            );
                        } else if (j == 2) {
                            MineshaftPieces.generateNextPiece(
                                start, pieces, random, this.bounds.minX, this.bounds.minY - 1 + random.nextInt(3), this.bounds.minZ - 1, Direction.NORTH, i
                            );
                        } else {
                            MineshaftPieces.generateNextPiece(
                                start, pieces, random, this.bounds.minX, this.bounds.minY - 1 + random.nextInt(3), this.bounds.maxZ + 1, Direction.SOUTH, i
                            );
                        }
                        break;
                    case EAST:
                        if (j <= 1) {
                            MineshaftPieces.generateNextPiece(
                                start, pieces, random, this.bounds.maxX + 1, this.bounds.minY - 1 + random.nextInt(3), this.bounds.minZ, this.facing, i
                            );
                        } else if (j == 2) {
                            MineshaftPieces.generateNextPiece(
                                start, pieces, random, this.bounds.maxX - 3, this.bounds.minY - 1 + random.nextInt(3), this.bounds.minZ - 1, Direction.NORTH, i
                            );
                        } else {
                            MineshaftPieces.generateNextPiece(
                                start, pieces, random, this.bounds.maxX - 3, this.bounds.minY - 1 + random.nextInt(3), this.bounds.maxZ + 1, Direction.SOUTH, i
                            );
                        }
                }
            }

            if (i < 8) {
                if (this.facing != Direction.NORTH && this.facing != Direction.SOUTH) {
                    for (int i1 = this.bounds.minX + 3; i1 + 3 <= this.bounds.maxX; i1 += 5) {
                        int j1 = random.nextInt(5);
                        if (j1 == 0) {
                            MineshaftPieces.generateNextPiece(start, pieces, random, i1, this.bounds.minY, this.bounds.minZ - 1, Direction.NORTH, i + 1);
                        } else if (j1 == 1) {
                            MineshaftPieces.generateNextPiece(start, pieces, random, i1, this.bounds.minY, this.bounds.maxZ + 1, Direction.SOUTH, i + 1);
                        }
                    }
                } else {
                    for (int k = this.bounds.minZ + 3; k + 3 <= this.bounds.maxZ; k += 5) {
                        int l = random.nextInt(5);
                        if (l == 0) {
                            MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.minX - 1, this.bounds.minY, k, Direction.WEST, i + 1);
                        } else if (l == 1) {
                            MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.maxX + 1, this.bounds.minY, k, Direction.EAST, i + 1);
                        }
                    }
                }
            }
        }

        @Override
        protected boolean placeChestWithLoot(World world, StructureBox bounds, Random random, int x, int y, int z, List<LootEntry> entries, int amount) {
            BlockPos blockpos = new BlockPos(this.transformX(x, z), this.transformY(y), this.transformZ(x, z));
            if (bounds.contains(blockpos) && world.getBlockState(blockpos).getBlock().getMaterial() == Material.AIR) {
                int i = random.nextBoolean() ? 1 : 0;
                world.setBlockState(blockpos, Blocks.RAIL.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.RAIL, i)), 2);
                ChestMinecartEntity chestminecartentity = new ChestMinecartEntity(world, blockpos.getX() + 0.5F, blockpos.getY() + 0.5F, blockpos.getZ() + 0.5F);
                LootEntry.addLoot(random, entries, chestminecartentity, amount);
                world.addEntity(chestminecartentity);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            int i = 0;
            int j = 2;
            int k = 0;
            int l = 2;
            int i1 = this.sections * 5 - 1;
            this.fillWithOutline(world, bounds, 0, 0, 0, 2, 1, i1, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillRandomlyWithOutline(world, bounds, random, 0.8F, 0, 2, 0, 2, 2, i1, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            if (this.hasCobwebs) {
                this.fillRandomlyWithOutline(world, bounds, random, 0.6F, 0, 0, 0, 2, 1, i1, Blocks.WEB.defaultState(), Blocks.AIR.defaultState(), false);
            }

            for (int j1 = 0; j1 < this.sections; j1++) {
                int k1 = 2 + j1 * 5;
                this.fillWithOutline(world, bounds, 0, 0, k1, 0, 1, k1, Blocks.FENCE.defaultState(), Blocks.AIR.defaultState(), false);
                this.fillWithOutline(world, bounds, 2, 0, k1, 2, 1, k1, Blocks.FENCE.defaultState(), Blocks.AIR.defaultState(), false);
                if (random.nextInt(4) == 0) {
                    this.fillWithOutline(world, bounds, 0, 2, k1, 0, 2, k1, Blocks.PLANKS.defaultState(), Blocks.AIR.defaultState(), false);
                    this.fillWithOutline(world, bounds, 2, 2, k1, 2, 2, k1, Blocks.PLANKS.defaultState(), Blocks.AIR.defaultState(), false);
                } else {
                    this.fillWithOutline(world, bounds, 0, 2, k1, 2, 2, k1, Blocks.PLANKS.defaultState(), Blocks.AIR.defaultState(), false);
                }

                this.setBlockStateWithThreshold(world, bounds, random, 0.1F, 0, 2, k1 - 1, Blocks.WEB.defaultState());
                this.setBlockStateWithThreshold(world, bounds, random, 0.1F, 2, 2, k1 - 1, Blocks.WEB.defaultState());
                this.setBlockStateWithThreshold(world, bounds, random, 0.1F, 0, 2, k1 + 1, Blocks.WEB.defaultState());
                this.setBlockStateWithThreshold(world, bounds, random, 0.1F, 2, 2, k1 + 1, Blocks.WEB.defaultState());
                this.setBlockStateWithThreshold(world, bounds, random, 0.05F, 0, 2, k1 - 2, Blocks.WEB.defaultState());
                this.setBlockStateWithThreshold(world, bounds, random, 0.05F, 2, 2, k1 - 2, Blocks.WEB.defaultState());
                this.setBlockStateWithThreshold(world, bounds, random, 0.05F, 0, 2, k1 + 2, Blocks.WEB.defaultState());
                this.setBlockStateWithThreshold(world, bounds, random, 0.05F, 2, 2, k1 + 2, Blocks.WEB.defaultState());
                this.setBlockStateWithThreshold(world, bounds, random, 0.05F, 1, 2, k1 - 1, Blocks.TORCH.getStateFromMetadata(Direction.UP.getId()));
                this.setBlockStateWithThreshold(world, bounds, random, 0.05F, 1, 2, k1 + 1, Blocks.TORCH.getStateFromMetadata(Direction.UP.getId()));
                if (random.nextInt(100) == 0) {
                    this.placeChestWithLoot(
                        world,
                        bounds,
                        random,
                        2,
                        0,
                        k1 - 1,
                        LootEntry.addAll(MineshaftPieces.LOOT_ENTRIES, Items.ENCHANTED_BOOK.createLoot(random)),
                        3 + random.nextInt(4)
                    );
                }

                if (random.nextInt(100) == 0) {
                    this.placeChestWithLoot(
                        world,
                        bounds,
                        random,
                        0,
                        0,
                        k1 + 1,
                        LootEntry.addAll(MineshaftPieces.LOOT_ENTRIES, Items.ENCHANTED_BOOK.createLoot(random)),
                        3 + random.nextInt(4)
                    );
                }

                if (this.hasCobwebs && !this.hasSpiderSpawner) {
                    int l1 = this.transformY(0);
                    int i2 = k1 - 1 + random.nextInt(3);
                    int j2 = this.transformX(1, i2);
                    i2 = this.transformZ(1, i2);
                    BlockPos blockpos = new BlockPos(j2, l1, i2);
                    if (bounds.contains(blockpos)) {
                        this.hasSpiderSpawner = true;
                        world.setBlockState(blockpos, Blocks.MOB_SPAWNER.defaultState(), 2);
                        BlockEntity blockentity = world.getBlockEntity(blockpos);
                        if (blockentity instanceof MobSpawnerBlockEntity) {
                            ((MobSpawnerBlockEntity)blockentity).getSpawner().setType("CaveSpider");
                        }
                    }
                }
            }

            for (int k2 = 0; k2 <= 2; k2++) {
                for (int i3 = 0; i3 <= i1; i3++) {
                    int j3 = -1;
                    BlockState blockstate1 = this.getBlockState(world, k2, j3, i3, bounds);
                    if (blockstate1.getBlock().getMaterial() == Material.AIR) {
                        int k3 = -1;
                        this.setBlockState(world, Blocks.PLANKS.defaultState(), k2, k3, i3, bounds);
                    }
                }
            }

            if (this.hasRails) {
                for (int l2 = 0; l2 <= i1; l2++) {
                    BlockState blockstate = this.getBlockState(world, 1, -1, l2, bounds);
                    if (blockstate.getBlock().getMaterial() != Material.AIR && blockstate.getBlock().isOpaque()) {
                        this.setBlockStateWithThreshold(
                            world, bounds, random, 0.7F, 1, 0, l2, Blocks.RAIL.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.RAIL, 0))
                        );
                    }
                }
            }

            return true;
        }
    }

    public static class Crossing extends StructurePiece {
        private Direction direction;
        private boolean hasTwoFloors;

        public Crossing() {
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            nbt.putBoolean("tf", this.hasTwoFloors);
            nbt.putInt("D", this.direction.getIdHorizontal());
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            this.hasTwoFloors = nbt.getBoolean("tf");
            this.direction = Direction.byIdHorizontal(nbt.getInt("D"));
        }

        public Crossing(int generationDepth, Random random, StructureBox bounds, Direction direction) {
            super(generationDepth);
            this.direction = direction;
            this.bounds = bounds;
            this.hasTwoFloors = bounds.getSpanY() > 3;
        }

        public static StructureBox findSize(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing) {
            StructureBox structurebox = new StructureBox(x, y, z, x, y + 2, z);
            if (random.nextInt(4) == 0) {
                structurebox.maxY += 4;
            }

            switch (facing) {
                case NORTH:
                    structurebox.minX = x - 1;
                    structurebox.maxX = x + 3;
                    structurebox.minZ = z - 4;
                    break;
                case SOUTH:
                    structurebox.minX = x - 1;
                    structurebox.maxX = x + 3;
                    structurebox.maxZ = z + 4;
                    break;
                case WEST:
                    structurebox.minX = x - 4;
                    structurebox.minZ = z - 1;
                    structurebox.maxZ = z + 3;
                    break;
                case EAST:
                    structurebox.maxX = x + 4;
                    structurebox.minZ = z - 1;
                    structurebox.maxZ = z + 3;
            }

            return StructurePiece.getIntersectingPiece(pieces, structurebox) != null ? null : structurebox;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            int i = this.getGenerationDepth();
            switch (this.direction) {
                case NORTH:
                    MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.minX + 1, this.bounds.minY, this.bounds.minZ - 1, Direction.NORTH, i);
                    MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.minX - 1, this.bounds.minY, this.bounds.minZ + 1, Direction.WEST, i);
                    MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.maxX + 1, this.bounds.minY, this.bounds.minZ + 1, Direction.EAST, i);
                    break;
                case SOUTH:
                    MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.minX + 1, this.bounds.minY, this.bounds.maxZ + 1, Direction.SOUTH, i);
                    MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.minX - 1, this.bounds.minY, this.bounds.minZ + 1, Direction.WEST, i);
                    MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.maxX + 1, this.bounds.minY, this.bounds.minZ + 1, Direction.EAST, i);
                    break;
                case WEST:
                    MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.minX + 1, this.bounds.minY, this.bounds.minZ - 1, Direction.NORTH, i);
                    MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.minX + 1, this.bounds.minY, this.bounds.maxZ + 1, Direction.SOUTH, i);
                    MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.minX - 1, this.bounds.minY, this.bounds.minZ + 1, Direction.WEST, i);
                    break;
                case EAST:
                    MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.minX + 1, this.bounds.minY, this.bounds.minZ - 1, Direction.NORTH, i);
                    MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.minX + 1, this.bounds.minY, this.bounds.maxZ + 1, Direction.SOUTH, i);
                    MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.maxX + 1, this.bounds.minY, this.bounds.minZ + 1, Direction.EAST, i);
            }

            if (this.hasTwoFloors) {
                if (random.nextBoolean()) {
                    MineshaftPieces.generateNextPiece(
                        start, pieces, random, this.bounds.minX + 1, this.bounds.minY + 3 + 1, this.bounds.minZ - 1, Direction.NORTH, i
                    );
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateNextPiece(
                        start, pieces, random, this.bounds.minX - 1, this.bounds.minY + 3 + 1, this.bounds.minZ + 1, Direction.WEST, i
                    );
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateNextPiece(
                        start, pieces, random, this.bounds.maxX + 1, this.bounds.minY + 3 + 1, this.bounds.minZ + 1, Direction.EAST, i
                    );
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateNextPiece(
                        start, pieces, random, this.bounds.minX + 1, this.bounds.minY + 3 + 1, this.bounds.maxZ + 1, Direction.SOUTH, i
                    );
                }
            }
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            if (this.hasTwoFloors) {
                this.fillWithOutline(
                    world,
                    bounds,
                    this.bounds.minX + 1,
                    this.bounds.minY,
                    this.bounds.minZ,
                    this.bounds.maxX - 1,
                    this.bounds.minY + 3 - 1,
                    this.bounds.maxZ,
                    Blocks.AIR.defaultState(),
                    Blocks.AIR.defaultState(),
                    false
                );
                this.fillWithOutline(
                    world,
                    bounds,
                    this.bounds.minX,
                    this.bounds.minY,
                    this.bounds.minZ + 1,
                    this.bounds.maxX,
                    this.bounds.minY + 3 - 1,
                    this.bounds.maxZ - 1,
                    Blocks.AIR.defaultState(),
                    Blocks.AIR.defaultState(),
                    false
                );
                this.fillWithOutline(
                    world,
                    bounds,
                    this.bounds.minX + 1,
                    this.bounds.maxY - 2,
                    this.bounds.minZ,
                    this.bounds.maxX - 1,
                    this.bounds.maxY,
                    this.bounds.maxZ,
                    Blocks.AIR.defaultState(),
                    Blocks.AIR.defaultState(),
                    false
                );
                this.fillWithOutline(
                    world,
                    bounds,
                    this.bounds.minX,
                    this.bounds.maxY - 2,
                    this.bounds.minZ + 1,
                    this.bounds.maxX,
                    this.bounds.maxY,
                    this.bounds.maxZ - 1,
                    Blocks.AIR.defaultState(),
                    Blocks.AIR.defaultState(),
                    false
                );
                this.fillWithOutline(
                    world,
                    bounds,
                    this.bounds.minX + 1,
                    this.bounds.minY + 3,
                    this.bounds.minZ + 1,
                    this.bounds.maxX - 1,
                    this.bounds.minY + 3,
                    this.bounds.maxZ - 1,
                    Blocks.AIR.defaultState(),
                    Blocks.AIR.defaultState(),
                    false
                );
            } else {
                this.fillWithOutline(
                    world,
                    bounds,
                    this.bounds.minX + 1,
                    this.bounds.minY,
                    this.bounds.minZ,
                    this.bounds.maxX - 1,
                    this.bounds.maxY,
                    this.bounds.maxZ,
                    Blocks.AIR.defaultState(),
                    Blocks.AIR.defaultState(),
                    false
                );
                this.fillWithOutline(
                    world,
                    bounds,
                    this.bounds.minX,
                    this.bounds.minY,
                    this.bounds.minZ + 1,
                    this.bounds.maxX,
                    this.bounds.maxY,
                    this.bounds.maxZ - 1,
                    Blocks.AIR.defaultState(),
                    Blocks.AIR.defaultState(),
                    false
                );
            }

            this.fillWithOutline(
                world,
                bounds,
                this.bounds.minX + 1,
                this.bounds.minY,
                this.bounds.minZ + 1,
                this.bounds.minX + 1,
                this.bounds.maxY,
                this.bounds.minZ + 1,
                Blocks.PLANKS.defaultState(),
                Blocks.AIR.defaultState(),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                this.bounds.minX + 1,
                this.bounds.minY,
                this.bounds.maxZ - 1,
                this.bounds.minX + 1,
                this.bounds.maxY,
                this.bounds.maxZ - 1,
                Blocks.PLANKS.defaultState(),
                Blocks.AIR.defaultState(),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                this.bounds.maxX - 1,
                this.bounds.minY,
                this.bounds.minZ + 1,
                this.bounds.maxX - 1,
                this.bounds.maxY,
                this.bounds.minZ + 1,
                Blocks.PLANKS.defaultState(),
                Blocks.AIR.defaultState(),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                this.bounds.maxX - 1,
                this.bounds.minY,
                this.bounds.maxZ - 1,
                this.bounds.maxX - 1,
                this.bounds.maxY,
                this.bounds.maxZ - 1,
                Blocks.PLANKS.defaultState(),
                Blocks.AIR.defaultState(),
                false
            );

            for (int i = this.bounds.minX; i <= this.bounds.maxX; i++) {
                for (int j = this.bounds.minZ; j <= this.bounds.maxZ; j++) {
                    if (this.getBlockState(world, i, this.bounds.minY - 1, j, bounds).getBlock().getMaterial() == Material.AIR) {
                        this.setBlockState(world, Blocks.PLANKS.defaultState(), i, this.bounds.minY - 1, j, bounds);
                    }
                }
            }

            return true;
        }
    }

    public static class Room extends StructurePiece {
        private List<StructureBox> entrances = Lists.newLinkedList();

        public Room() {
        }

        public Room(int generationDepth, Random random, int x, int z) {
            super(generationDepth);
            this.bounds = new StructureBox(x, 50, z, x + 7 + random.nextInt(6), 54 + random.nextInt(6), z + 7 + random.nextInt(6));
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            int i = this.getGenerationDepth();
            int k = this.bounds.getSpanY() - 3 - 1;
            if (k <= 0) {
                k = 1;
            }

            int j = 0;

            while (j < this.bounds.getSpanX()) {
                j += random.nextInt(this.bounds.getSpanX());
                if (j + 3 > this.bounds.getSpanX()) {
                    break;
                }

                StructurePiece structurepiece = MineshaftPieces.generateNextPiece(
                    start, pieces, random, this.bounds.minX + j, this.bounds.minY + random.nextInt(k) + 1, this.bounds.minZ - 1, Direction.NORTH, i
                );
                if (structurepiece != null) {
                    StructureBox structurebox = structurepiece.getBounds();
                    this.entrances
                        .add(
                            new StructureBox(structurebox.minX, structurebox.minY, this.bounds.minZ, structurebox.maxX, structurebox.maxY, this.bounds.minZ + 1)
                        );
                }

                j += 4;
            }

            j = 0;

            while (j < this.bounds.getSpanX()) {
                j += random.nextInt(this.bounds.getSpanX());
                if (j + 3 > this.bounds.getSpanX()) {
                    break;
                }

                StructurePiece structurepiece1 = MineshaftPieces.generateNextPiece(
                    start, pieces, random, this.bounds.minX + j, this.bounds.minY + random.nextInt(k) + 1, this.bounds.maxZ + 1, Direction.SOUTH, i
                );
                if (structurepiece1 != null) {
                    StructureBox structurebox1 = structurepiece1.getBounds();
                    this.entrances
                        .add(
                            new StructureBox(
                                structurebox1.minX, structurebox1.minY, this.bounds.maxZ - 1, structurebox1.maxX, structurebox1.maxY, this.bounds.maxZ
                            )
                        );
                }

                j += 4;
            }

            j = 0;

            while (j < this.bounds.getSpanZ()) {
                j += random.nextInt(this.bounds.getSpanZ());
                if (j + 3 > this.bounds.getSpanZ()) {
                    break;
                }

                StructurePiece structurepiece2 = MineshaftPieces.generateNextPiece(
                    start, pieces, random, this.bounds.minX - 1, this.bounds.minY + random.nextInt(k) + 1, this.bounds.minZ + j, Direction.WEST, i
                );
                if (structurepiece2 != null) {
                    StructureBox structurebox2 = structurepiece2.getBounds();
                    this.entrances
                        .add(
                            new StructureBox(
                                this.bounds.minX, structurebox2.minY, structurebox2.minZ, this.bounds.minX + 1, structurebox2.maxY, structurebox2.maxZ
                            )
                        );
                }

                j += 4;
            }

            j = 0;

            while (j < this.bounds.getSpanZ()) {
                j += random.nextInt(this.bounds.getSpanZ());
                if (j + 3 > this.bounds.getSpanZ()) {
                    break;
                }

                StructurePiece structurepiece3 = MineshaftPieces.generateNextPiece(
                    start, pieces, random, this.bounds.maxX + 1, this.bounds.minY + random.nextInt(k) + 1, this.bounds.minZ + j, Direction.EAST, i
                );
                if (structurepiece3 != null) {
                    StructureBox structurebox3 = structurepiece3.getBounds();
                    this.entrances
                        .add(
                            new StructureBox(
                                this.bounds.maxX - 1, structurebox3.minY, structurebox3.minZ, this.bounds.maxX, structurebox3.maxY, structurebox3.maxZ
                            )
                        );
                }

                j += 4;
            }
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            this.fillWithOutline(
                world,
                bounds,
                this.bounds.minX,
                this.bounds.minY,
                this.bounds.minZ,
                this.bounds.maxX,
                this.bounds.minY,
                this.bounds.maxZ,
                Blocks.DIRT.defaultState(),
                Blocks.AIR.defaultState(),
                true
            );
            this.fillWithOutline(
                world,
                bounds,
                this.bounds.minX,
                this.bounds.minY + 1,
                this.bounds.minZ,
                this.bounds.maxX,
                Math.min(this.bounds.minY + 3, this.bounds.maxY),
                this.bounds.maxZ,
                Blocks.AIR.defaultState(),
                Blocks.AIR.defaultState(),
                false
            );

            for (StructureBox structurebox : this.entrances) {
                this.fillWithOutline(
                    world,
                    bounds,
                    structurebox.minX,
                    structurebox.maxY - 2,
                    structurebox.minZ,
                    structurebox.maxX,
                    structurebox.maxY,
                    structurebox.maxZ,
                    Blocks.AIR.defaultState(),
                    Blocks.AIR.defaultState(),
                    false
                );
            }

            this.placeUpperHemisphere(
                world,
                bounds,
                this.bounds.minX,
                this.bounds.minY + 4,
                this.bounds.minZ,
                this.bounds.maxX,
                this.bounds.maxY,
                this.bounds.maxZ,
                Blocks.AIR.defaultState(),
                false
            );
            return true;
        }

        @Override
        public void move(int dx, int dy, int dz) {
            super.move(dx, dy, dz);

            for (StructureBox structurebox : this.entrances) {
                structurebox.move(dx, dy, dz);
            }
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            NbtList nbtlist = new NbtList();

            for (StructureBox structurebox : this.entrances) {
                nbtlist.addElement(structurebox.toNbt());
            }

            nbt.put("Entrances", nbtlist);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            NbtList nbtlist = nbt.getList("Entrances", 11);

            for (int i = 0; i < nbtlist.size(); i++) {
                this.entrances.add(new StructureBox(nbtlist.getIntArray(i)));
            }
        }
    }

    public static class Stairs extends StructurePiece {
        public Stairs() {
        }

        public Stairs(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
        }

        public static StructureBox findSize(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing) {
            StructureBox structurebox = new StructureBox(x, y - 5, z, x, y + 2, z);
            switch (facing) {
                case NORTH:
                    structurebox.maxX = x + 2;
                    structurebox.minZ = z - 8;
                    break;
                case SOUTH:
                    structurebox.maxX = x + 2;
                    structurebox.maxZ = z + 8;
                    break;
                case WEST:
                    structurebox.minX = x - 8;
                    structurebox.maxZ = z + 2;
                    break;
                case EAST:
                    structurebox.maxX = x + 8;
                    structurebox.maxZ = z + 2;
            }

            return StructurePiece.getIntersectingPiece(pieces, structurebox) != null ? null : structurebox;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            int i = this.getGenerationDepth();
            if (this.facing != null) {
                switch (this.facing) {
                    case NORTH:
                        MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.minX, this.bounds.minY, this.bounds.minZ - 1, Direction.NORTH, i);
                        break;
                    case SOUTH:
                        MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.minX, this.bounds.minY, this.bounds.maxZ + 1, Direction.SOUTH, i);
                        break;
                    case WEST:
                        MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.minX - 1, this.bounds.minY, this.bounds.minZ, Direction.WEST, i);
                        break;
                    case EAST:
                        MineshaftPieces.generateNextPiece(start, pieces, random, this.bounds.maxX + 1, this.bounds.minY, this.bounds.minZ, Direction.EAST, i);
                }
            }
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            this.fillWithOutline(world, bounds, 0, 5, 0, 2, 7, 1, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 0, 7, 2, 2, 8, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);

            for (int i = 0; i < 5; i++) {
                this.fillWithOutline(
                    world, bounds, 0, 5 - i - (i < 4 ? 1 : 0), 2 + i, 2, 7 - i, 2 + i, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false
                );
            }

            return true;
        }
    }
}
