package net.minecraft.world.gen.structure;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.block.InfestedBlock;
import net.minecraft.block.StoneSlabBlock;
import net.minecraft.block.StonebrickBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class StrongholdPieces {
    private static final StrongholdPieces.StrongholdPieceWeight[] PIECE_WEIGHTS = new StrongholdPieces.StrongholdPieceWeight[]{
        new StrongholdPieces.StrongholdPieceWeight(StrongholdPieces.StraightCorridor.class, 40, 0),
        new StrongholdPieces.StrongholdPieceWeight(StrongholdPieces.Prison.class, 5, 5),
        new StrongholdPieces.StrongholdPieceWeight(StrongholdPieces.LeftTurn.class, 20, 0),
        new StrongholdPieces.StrongholdPieceWeight(StrongholdPieces.RightTurn.class, 20, 0),
        new StrongholdPieces.StrongholdPieceWeight(StrongholdPieces.RoomCrossing.class, 10, 6),
        new StrongholdPieces.StrongholdPieceWeight(StrongholdPieces.StraightStairs.class, 5, 5),
        new StrongholdPieces.StrongholdPieceWeight(StrongholdPieces.SpiralStaircase.class, 5, 5),
        new StrongholdPieces.StrongholdPieceWeight(StrongholdPieces.FiveWayCrossing.class, 5, 4),
        new StrongholdPieces.StrongholdPieceWeight(StrongholdPieces.ChestCorridor.class, 5, 4),
        new StrongholdPieces.StrongholdPieceWeight(StrongholdPieces.Library.class, 10, 2) {
            @Override
            public boolean isValid(int generationDepth) {
                return super.isValid(generationDepth) && generationDepth > 4;
            }
        },
        new StrongholdPieces.StrongholdPieceWeight(StrongholdPieces.EndPortalRoom.class, 20, 1) {
            @Override
            public boolean isValid(int generationDepth) {
                return super.isValid(generationDepth) && generationDepth > 5;
            }
        }
    };
    private static List<StrongholdPieces.StrongholdPieceWeight> currentWeights;
    private static Class<? extends StrongholdPieces.StrongholdPiece> forcedPiece;
    static int totalWeight;
    private static final StrongholdPieces.StoneBrickPicker STONE_BRICK_PICKER = new StrongholdPieces.StoneBrickPicker();

    public static void register() {
        StructureRegistry.registerPiece(StrongholdPieces.ChestCorridor.class, "SHCC");
        StructureRegistry.registerPiece(StrongholdPieces.PlainCorridor.class, "SHFC");
        StructureRegistry.registerPiece(StrongholdPieces.FiveWayCrossing.class, "SH5C");
        StructureRegistry.registerPiece(StrongholdPieces.LeftTurn.class, "SHLT");
        StructureRegistry.registerPiece(StrongholdPieces.Library.class, "SHLi");
        StructureRegistry.registerPiece(StrongholdPieces.EndPortalRoom.class, "SHPR");
        StructureRegistry.registerPiece(StrongholdPieces.Prison.class, "SHPH");
        StructureRegistry.registerPiece(StrongholdPieces.RightTurn.class, "SHRT");
        StructureRegistry.registerPiece(StrongholdPieces.RoomCrossing.class, "SHRC");
        StructureRegistry.registerPiece(StrongholdPieces.SpiralStaircase.class, "SHSD");
        StructureRegistry.registerPiece(StrongholdPieces.Start.class, "SHStart");
        StructureRegistry.registerPiece(StrongholdPieces.StraightCorridor.class, "SHS");
        StructureRegistry.registerPiece(StrongholdPieces.StraightStairs.class, "SHSSD");
    }

    public static void resetWeights() {
        currentWeights = Lists.newArrayList();

        for (StrongholdPieces.StrongholdPieceWeight strongholdpieces$strongholdpieceweight : PIECE_WEIGHTS) {
            strongholdpieces$strongholdpieceweight.amountGenerated = 0;
            currentWeights.add(strongholdpieces$strongholdpieceweight);
        }

        forcedPiece = null;
    }

    /**
     * Returns whether any more pieces can be generated.
     */
    private static boolean updateTotalWeight() {
        boolean flag = false;
        totalWeight = 0;

        for (StrongholdPieces.StrongholdPieceWeight strongholdpieces$strongholdpieceweight : currentWeights) {
            if (strongholdpieces$strongholdpieceweight.maxAmount > 0
                && strongholdpieces$strongholdpieceweight.amountGenerated < strongholdpieces$strongholdpieceweight.maxAmount) {
                flag = true;
            }

            totalWeight = totalWeight + strongholdpieces$strongholdpieceweight.weight;
        }

        return flag;
    }

    private static StrongholdPieces.StrongholdPiece createPiece(
        Class<? extends StrongholdPieces.StrongholdPiece> type,
        List<StructurePiece> pieces,
        Random random,
        int x,
        int y,
        int z,
        Direction facing,
        int generationDepth
    ) {
        StrongholdPieces.StrongholdPiece strongholdpieces$strongholdpiece = null;
        if (type == StrongholdPieces.StraightCorridor.class) {
            strongholdpieces$strongholdpiece = StrongholdPieces.StraightCorridor.of(pieces, random, x, y, z, facing, generationDepth);
        } else if (type == StrongholdPieces.Prison.class) {
            strongholdpieces$strongholdpiece = StrongholdPieces.Prison.of(pieces, random, x, y, z, facing, generationDepth);
        } else if (type == StrongholdPieces.LeftTurn.class) {
            strongholdpieces$strongholdpiece = StrongholdPieces.LeftTurn.of(pieces, random, x, y, z, facing, generationDepth);
        } else if (type == StrongholdPieces.RightTurn.class) {
            strongholdpieces$strongholdpiece = StrongholdPieces.RightTurn.of(pieces, random, x, y, z, facing, generationDepth);
        } else if (type == StrongholdPieces.RoomCrossing.class) {
            strongholdpieces$strongholdpiece = StrongholdPieces.RoomCrossing.of(pieces, random, x, y, z, facing, generationDepth);
        } else if (type == StrongholdPieces.StraightStairs.class) {
            strongholdpieces$strongholdpiece = StrongholdPieces.StraightStairs.of(pieces, random, x, y, z, facing, generationDepth);
        } else if (type == StrongholdPieces.SpiralStaircase.class) {
            strongholdpieces$strongholdpiece = StrongholdPieces.SpiralStaircase.of(pieces, random, x, y, z, facing, generationDepth);
        } else if (type == StrongholdPieces.FiveWayCrossing.class) {
            strongholdpieces$strongholdpiece = StrongholdPieces.FiveWayCrossing.of(pieces, random, x, y, z, facing, generationDepth);
        } else if (type == StrongholdPieces.ChestCorridor.class) {
            strongholdpieces$strongholdpiece = StrongholdPieces.ChestCorridor.of(pieces, random, x, y, z, facing, generationDepth);
        } else if (type == StrongholdPieces.Library.class) {
            strongholdpieces$strongholdpiece = StrongholdPieces.Library.of(pieces, random, x, y, z, facing, generationDepth);
        } else if (type == StrongholdPieces.EndPortalRoom.class) {
            strongholdpieces$strongholdpiece = StrongholdPieces.EndPortalRoom.of(pieces, random, x, y, z, facing, generationDepth);
        }

        return strongholdpieces$strongholdpiece;
    }

    private static StrongholdPieces.StrongholdPiece generateNextPiece(
        StrongholdPieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
    ) {
        if (!updateTotalWeight()) {
            return null;
        }

        if (forcedPiece != null) {
            StrongholdPieces.StrongholdPiece strongholdpieces$strongholdpiece = createPiece(forcedPiece, pieces, random, x, y, z, facing, generationDepth);
            forcedPiece = null;
            if (strongholdpieces$strongholdpiece != null) {
                return strongholdpieces$strongholdpiece;
            }
        }

        int j = 0;

        while (j < 5) {
            j++;
            int i = random.nextInt(totalWeight);

            for (StrongholdPieces.StrongholdPieceWeight strongholdpieces$strongholdpieceweight : currentWeights) {
                i -= strongholdpieces$strongholdpieceweight.weight;
                if (i < 0) {
                    if (!strongholdpieces$strongholdpieceweight.isValid(generationDepth) || strongholdpieces$strongholdpieceweight == startPiece.previous) {
                        break;
                    }

                    StrongholdPieces.StrongholdPiece strongholdpieces$strongholdpiece1 = createPiece(
                        strongholdpieces$strongholdpieceweight.type, pieces, random, x, y, z, facing, generationDepth
                    );
                    if (strongholdpieces$strongholdpiece1 != null) {
                        strongholdpieces$strongholdpieceweight.amountGenerated++;
                        startPiece.previous = strongholdpieces$strongholdpieceweight;
                        if (!strongholdpieces$strongholdpieceweight.isValid()) {
                            currentWeights.remove(strongholdpieces$strongholdpieceweight);
                        }

                        return strongholdpieces$strongholdpiece1;
                    }
                }
            }
        }

        StructureBox structurebox = StrongholdPieces.PlainCorridor.findSize(pieces, random, x, y, z, facing);
        return structurebox != null && structurebox.minY > 1 ? new StrongholdPieces.PlainCorridor(generationDepth, random, structurebox, facing) : null;
    }

    private static StructurePiece tryGenerateNextPiece(
        StrongholdPieces.Start startPiece, List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
    ) {
        if (generationDepth > 50) {
            return null;
        }

        if (Math.abs(x - startPiece.getBounds().minX) <= 112 && Math.abs(z - startPiece.getBounds().minZ) <= 112) {
            StructurePiece structurepiece = generateNextPiece(startPiece, pieces, random, x, y, z, facing, generationDepth + 1);
            if (structurepiece != null) {
                pieces.add(structurepiece);
                startPiece.children.add(structurepiece);
            }

            return structurepiece;
        } else {
            return null;
        }
    }

    public static class ChestCorridor extends StrongholdPieces.StrongholdPiece {
        private static final List<LootEntry> LOOT_ENTRIES = Lists.newArrayList(
            new LootEntry(Items.ENDER_PEARL, 0, 1, 1, 10),
            new LootEntry(Items.DIAMOND, 0, 1, 3, 3),
            new LootEntry(Items.IRON_INGOT, 0, 1, 5, 10),
            new LootEntry(Items.GOLD_INGOT, 0, 1, 3, 5),
            new LootEntry(Items.REDSTONE, 0, 4, 9, 5),
            new LootEntry(Items.BREAD, 0, 1, 3, 15),
            new LootEntry(Items.APPLE, 0, 1, 3, 15),
            new LootEntry(Items.IRON_PICKAXE, 0, 1, 1, 5),
            new LootEntry(Items.IRON_SWORD, 0, 1, 1, 5),
            new LootEntry(Items.IRON_CHESTPLATE, 0, 1, 1, 5),
            new LootEntry(Items.IRON_HELMET, 0, 1, 1, 5),
            new LootEntry(Items.IRON_LEGGINGS, 0, 1, 1, 5),
            new LootEntry(Items.IRON_BOOTS, 0, 1, 1, 5),
            new LootEntry(Items.GOLDEN_APPLE, 0, 1, 1, 1),
            new LootEntry(Items.SADDLE, 0, 1, 1, 1),
            new LootEntry(Items.IRON_HORSE_ARMOR, 0, 1, 1, 1),
            new LootEntry(Items.GOLDEN_HORSE_ARMOR, 0, 1, 1, 1),
            new LootEntry(Items.DIAMOND_HORSE_ARMOR, 0, 1, 1, 1)
        );
        private boolean hasChest;

        public ChestCorridor() {
        }

        public ChestCorridor(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = this.pickEntranceType(random);
            this.bounds = bounds;
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
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((StrongholdPieces.Start)start, pieces, random, 1, 1);
        }

        public static StrongholdPieces.ChestCorridor of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -1, -1, 0, 5, 5, 7, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new StrongholdPieces.ChestCorridor(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            this.fill(world, bounds, 0, 0, 0, 4, 4, 6, true, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.generateEntrance(world, random, bounds, this.entranceType, 1, 1, 0);
            this.generateEntrance(world, random, bounds, StrongholdPieces.StrongholdPiece.EntranceType.OPENING, 1, 1, 6);
            this.fillWithOutline(world, bounds, 3, 1, 2, 3, 1, 4, Blocks.STONE_BRICKS.defaultState(), Blocks.STONE_BRICKS.defaultState(), false);
            this.setBlockState(world, Blocks.STONE_SLAB.getStateFromMetadata(StoneSlabBlock.Variant.SMOOTHBRICK.getId()), 3, 1, 1, bounds);
            this.setBlockState(world, Blocks.STONE_SLAB.getStateFromMetadata(StoneSlabBlock.Variant.SMOOTHBRICK.getId()), 3, 1, 5, bounds);
            this.setBlockState(world, Blocks.STONE_SLAB.getStateFromMetadata(StoneSlabBlock.Variant.SMOOTHBRICK.getId()), 3, 2, 2, bounds);
            this.setBlockState(world, Blocks.STONE_SLAB.getStateFromMetadata(StoneSlabBlock.Variant.SMOOTHBRICK.getId()), 3, 2, 4, bounds);

            for (int i = 2; i <= 4; i++) {
                this.setBlockState(world, Blocks.STONE_SLAB.getStateFromMetadata(StoneSlabBlock.Variant.SMOOTHBRICK.getId()), 2, 1, i, bounds);
            }

            if (!this.hasChest && bounds.contains(new BlockPos(this.transformX(3, 3), this.transformY(2), this.transformZ(3, 3)))) {
                this.hasChest = true;
                this.placeChestWithLoot(
                    world, bounds, random, 3, 2, 3, LootEntry.addAll(LOOT_ENTRIES, Items.ENCHANTED_BOOK.createLoot(random)), 2 + random.nextInt(2)
                );
            }

            return true;
        }
    }

    public static class EndPortalRoom extends StrongholdPieces.StrongholdPiece {
        private boolean hasSpawner;

        public EndPortalRoom() {
        }

        public EndPortalRoom(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putBoolean("Mob", this.hasSpawner);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.hasSpawner = nbt.getBoolean("Mob");
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            if (start != null) {
                ((StrongholdPieces.Start)start).endPortalRoom = this;
            }
        }

        public static StrongholdPieces.EndPortalRoom of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -4, -1, 0, 11, 8, 16, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new StrongholdPieces.EndPortalRoom(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fill(world, bounds, 0, 0, 0, 10, 7, 15, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.generateEntrance(world, random, bounds, StrongholdPieces.StrongholdPiece.EntranceType.GATES, 4, 1, 0);
            int i = 6;
            this.fill(world, bounds, 1, i, 1, 1, i, 14, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fill(world, bounds, 9, i, 1, 9, i, 14, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fill(world, bounds, 2, i, 1, 8, i, 2, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fill(world, bounds, 2, i, 14, 8, i, 14, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fill(world, bounds, 1, 1, 1, 2, 1, 4, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fill(world, bounds, 8, 1, 1, 9, 1, 4, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fillWithOutline(world, bounds, 1, 1, 1, 1, 1, 3, Blocks.FLOWING_LAVA.defaultState(), Blocks.FLOWING_LAVA.defaultState(), false);
            this.fillWithOutline(world, bounds, 9, 1, 1, 9, 1, 3, Blocks.FLOWING_LAVA.defaultState(), Blocks.FLOWING_LAVA.defaultState(), false);
            this.fill(world, bounds, 3, 1, 8, 7, 1, 12, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fillWithOutline(world, bounds, 4, 1, 9, 6, 1, 11, Blocks.FLOWING_LAVA.defaultState(), Blocks.FLOWING_LAVA.defaultState(), false);

            for (int j = 3; j < 14; j += 2) {
                this.fillWithOutline(world, bounds, 0, 3, j, 0, 4, j, Blocks.IRON_BARS.defaultState(), Blocks.IRON_BARS.defaultState(), false);
                this.fillWithOutline(world, bounds, 10, 3, j, 10, 4, j, Blocks.IRON_BARS.defaultState(), Blocks.IRON_BARS.defaultState(), false);
            }

            for (int k1 = 2; k1 < 9; k1 += 2) {
                this.fillWithOutline(world, bounds, k1, 3, 15, k1, 4, 15, Blocks.IRON_BARS.defaultState(), Blocks.IRON_BARS.defaultState(), false);
            }

            int l1 = this.postProcessBlockMetadata(Blocks.STONE_BRICK_STAIRS, 3);
            this.fill(world, bounds, 4, 1, 5, 6, 1, 7, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fill(world, bounds, 4, 2, 6, 6, 2, 7, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fill(world, bounds, 4, 3, 7, 6, 3, 7, false, random, StrongholdPieces.STONE_BRICK_PICKER);

            for (int k = 4; k <= 6; k++) {
                this.setBlockState(world, Blocks.STONE_BRICK_STAIRS.getStateFromMetadata(l1), k, 1, 4, bounds);
                this.setBlockState(world, Blocks.STONE_BRICK_STAIRS.getStateFromMetadata(l1), k, 2, 5, bounds);
                this.setBlockState(world, Blocks.STONE_BRICK_STAIRS.getStateFromMetadata(l1), k, 3, 6, bounds);
            }

            int i2 = Direction.NORTH.getIdHorizontal();
            int l = Direction.SOUTH.getIdHorizontal();
            int i1 = Direction.EAST.getIdHorizontal();
            int j1 = Direction.WEST.getIdHorizontal();
            if (this.facing != null) {
                switch (this.facing) {
                    case SOUTH:
                        i2 = Direction.SOUTH.getIdHorizontal();
                        l = Direction.NORTH.getIdHorizontal();
                        break;
                    case WEST:
                        i2 = Direction.WEST.getIdHorizontal();
                        l = Direction.EAST.getIdHorizontal();
                        i1 = Direction.SOUTH.getIdHorizontal();
                        j1 = Direction.NORTH.getIdHorizontal();
                        break;
                    case EAST:
                        i2 = Direction.EAST.getIdHorizontal();
                        l = Direction.WEST.getIdHorizontal();
                        i1 = Direction.SOUTH.getIdHorizontal();
                        j1 = Direction.NORTH.getIdHorizontal();
                }
            }

            this.setBlockState(world, Blocks.END_PORTAL_FRAME.getStateFromMetadata(i2).set(EndPortalFrameBlock.EYE, random.nextFloat() > 0.9F), 4, 3, 8, bounds);
            this.setBlockState(world, Blocks.END_PORTAL_FRAME.getStateFromMetadata(i2).set(EndPortalFrameBlock.EYE, random.nextFloat() > 0.9F), 5, 3, 8, bounds);
            this.setBlockState(world, Blocks.END_PORTAL_FRAME.getStateFromMetadata(i2).set(EndPortalFrameBlock.EYE, random.nextFloat() > 0.9F), 6, 3, 8, bounds);
            this.setBlockState(world, Blocks.END_PORTAL_FRAME.getStateFromMetadata(l).set(EndPortalFrameBlock.EYE, random.nextFloat() > 0.9F), 4, 3, 12, bounds);
            this.setBlockState(world, Blocks.END_PORTAL_FRAME.getStateFromMetadata(l).set(EndPortalFrameBlock.EYE, random.nextFloat() > 0.9F), 5, 3, 12, bounds);
            this.setBlockState(world, Blocks.END_PORTAL_FRAME.getStateFromMetadata(l).set(EndPortalFrameBlock.EYE, random.nextFloat() > 0.9F), 6, 3, 12, bounds);
            this.setBlockState(world, Blocks.END_PORTAL_FRAME.getStateFromMetadata(i1).set(EndPortalFrameBlock.EYE, random.nextFloat() > 0.9F), 3, 3, 9, bounds);
            this.setBlockState(
                world, Blocks.END_PORTAL_FRAME.getStateFromMetadata(i1).set(EndPortalFrameBlock.EYE, random.nextFloat() > 0.9F), 3, 3, 10, bounds
            );
            this.setBlockState(
                world, Blocks.END_PORTAL_FRAME.getStateFromMetadata(i1).set(EndPortalFrameBlock.EYE, random.nextFloat() > 0.9F), 3, 3, 11, bounds
            );
            this.setBlockState(world, Blocks.END_PORTAL_FRAME.getStateFromMetadata(j1).set(EndPortalFrameBlock.EYE, random.nextFloat() > 0.9F), 7, 3, 9, bounds);
            this.setBlockState(
                world, Blocks.END_PORTAL_FRAME.getStateFromMetadata(j1).set(EndPortalFrameBlock.EYE, random.nextFloat() > 0.9F), 7, 3, 10, bounds
            );
            this.setBlockState(
                world, Blocks.END_PORTAL_FRAME.getStateFromMetadata(j1).set(EndPortalFrameBlock.EYE, random.nextFloat() > 0.9F), 7, 3, 11, bounds
            );
            if (!this.hasSpawner) {
                i = this.transformY(3);
                BlockPos blockpos = new BlockPos(this.transformX(5, 6), i, this.transformZ(5, 6));
                if (bounds.contains(blockpos)) {
                    this.hasSpawner = true;
                    world.setBlockState(blockpos, Blocks.MOB_SPAWNER.defaultState(), 2);
                    BlockEntity blockentity = world.getBlockEntity(blockpos);
                    if (blockentity instanceof MobSpawnerBlockEntity) {
                        ((MobSpawnerBlockEntity)blockentity).getSpawner().setType("Silverfish");
                    }
                }
            }

            return true;
        }
    }

    public static class FiveWayCrossing extends StrongholdPieces.StrongholdPiece {
        private boolean leftLow;
        private boolean leftHigh;
        private boolean rightLow;
        private boolean rightHigh;

        public FiveWayCrossing() {
        }

        public FiveWayCrossing(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = this.pickEntranceType(random);
            this.bounds = bounds;
            this.leftLow = random.nextBoolean();
            this.leftHigh = random.nextBoolean();
            this.rightLow = random.nextBoolean();
            this.rightHigh = random.nextInt(3) > 0;
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putBoolean("leftLow", this.leftLow);
            nbt.putBoolean("leftHigh", this.leftHigh);
            nbt.putBoolean("rightLow", this.rightLow);
            nbt.putBoolean("rightHigh", this.rightHigh);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.leftLow = nbt.getBoolean("leftLow");
            this.leftHigh = nbt.getBoolean("leftHigh");
            this.rightLow = nbt.getBoolean("rightLow");
            this.rightHigh = nbt.getBoolean("rightHigh");
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            int i = 3;
            int j = 5;
            if (this.facing == Direction.WEST || this.facing == Direction.NORTH) {
                i = 8 - i;
                j = 8 - j;
            }

            this.generatePieceForward((StrongholdPieces.Start)start, pieces, random, 5, 1);
            if (this.leftLow) {
                this.generatePieceLeft((StrongholdPieces.Start)start, pieces, random, i, 1);
            }

            if (this.leftHigh) {
                this.generatePieceLeft((StrongholdPieces.Start)start, pieces, random, j, 7);
            }

            if (this.rightLow) {
                this.generatePieceRight((StrongholdPieces.Start)start, pieces, random, i, 1);
            }

            if (this.rightHigh) {
                this.generatePieceRight((StrongholdPieces.Start)start, pieces, random, j, 7);
            }
        }

        public static StrongholdPieces.FiveWayCrossing of(
            List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -4, -3, 0, 10, 9, 11, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new StrongholdPieces.FiveWayCrossing(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            this.fill(world, bounds, 0, 0, 0, 9, 8, 10, true, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.generateEntrance(world, random, bounds, this.entranceType, 4, 3, 0);
            if (this.leftLow) {
                this.fillWithOutline(world, bounds, 0, 3, 1, 0, 5, 3, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            }

            if (this.rightLow) {
                this.fillWithOutline(world, bounds, 9, 3, 1, 9, 5, 3, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            }

            if (this.leftHigh) {
                this.fillWithOutline(world, bounds, 0, 5, 7, 0, 7, 9, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            }

            if (this.rightHigh) {
                this.fillWithOutline(world, bounds, 9, 5, 7, 9, 7, 9, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            }

            this.fillWithOutline(world, bounds, 5, 1, 10, 7, 3, 10, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fill(world, bounds, 1, 2, 1, 8, 2, 6, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fill(world, bounds, 4, 1, 5, 4, 4, 9, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fill(world, bounds, 8, 1, 5, 8, 4, 9, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fill(world, bounds, 1, 4, 7, 3, 4, 9, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fill(world, bounds, 1, 3, 5, 3, 3, 6, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fillWithOutline(world, bounds, 1, 3, 4, 3, 3, 4, Blocks.STONE_SLAB.defaultState(), Blocks.STONE_SLAB.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 4, 6, 3, 4, 6, Blocks.STONE_SLAB.defaultState(), Blocks.STONE_SLAB.defaultState(), false);
            this.fill(world, bounds, 5, 1, 7, 7, 1, 8, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fillWithOutline(world, bounds, 5, 1, 9, 7, 1, 9, Blocks.STONE_SLAB.defaultState(), Blocks.STONE_SLAB.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 2, 7, 7, 2, 7, Blocks.STONE_SLAB.defaultState(), Blocks.STONE_SLAB.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 5, 7, 4, 5, 9, Blocks.STONE_SLAB.defaultState(), Blocks.STONE_SLAB.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 5, 7, 8, 5, 9, Blocks.STONE_SLAB.defaultState(), Blocks.STONE_SLAB.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 5, 7, 7, 5, 9, Blocks.DOUBLE_STONE_SLAB.defaultState(), Blocks.DOUBLE_STONE_SLAB.defaultState(), false);
            this.setBlockState(world, Blocks.TORCH.defaultState(), 6, 5, 6, bounds);
            return true;
        }
    }

    public static class LeftTurn extends StrongholdPieces.StrongholdPiece {
        public LeftTurn() {
        }

        public LeftTurn(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = this.pickEntranceType(random);
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            if (this.facing != Direction.NORTH && this.facing != Direction.EAST) {
                this.generatePieceRight((StrongholdPieces.Start)start, pieces, random, 1, 1);
            } else {
                this.generatePieceLeft((StrongholdPieces.Start)start, pieces, random, 1, 1);
            }
        }

        public static StrongholdPieces.LeftTurn of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -1, -1, 0, 5, 5, 5, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new StrongholdPieces.LeftTurn(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            this.fill(world, bounds, 0, 0, 0, 4, 4, 4, true, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.generateEntrance(world, random, bounds, this.entranceType, 1, 1, 0);
            if (this.facing != Direction.NORTH && this.facing != Direction.EAST) {
                this.fillWithOutline(world, bounds, 4, 1, 1, 4, 3, 3, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            } else {
                this.fillWithOutline(world, bounds, 0, 1, 1, 0, 3, 3, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            }

            return true;
        }
    }

    public static class Library extends StrongholdPieces.StrongholdPiece {
        private static final List<LootEntry> LOOT_ENTRIES = Lists.newArrayList(
            new LootEntry(Items.BOOK, 0, 1, 3, 20),
            new LootEntry(Items.PAPER, 0, 2, 7, 20),
            new LootEntry(Items.EMPTY_MAP, 0, 1, 1, 1),
            new LootEntry(Items.COMPASS, 0, 1, 1, 1)
        );
        private boolean tall;

        public Library() {
        }

        public Library(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = this.pickEntranceType(random);
            this.bounds = bounds;
            this.tall = bounds.getSpanY() > 6;
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putBoolean("Tall", this.tall);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.tall = nbt.getBoolean("Tall");
        }

        public static StrongholdPieces.Library of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -4, -1, 0, 14, 11, 15, facing);
            if (!isValidBounds(structurebox) || StructurePiece.getIntersectingPiece(pieces, structurebox) != null) {
                structurebox = StructureBox.orient(x, y, z, -4, -1, 0, 14, 6, 15, facing);
                if (!isValidBounds(structurebox) || StructurePiece.getIntersectingPiece(pieces, structurebox) != null) {
                    return null;
                }
            }

            return new StrongholdPieces.Library(generationDepth, random, structurebox, facing);
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            int i = 11;
            if (!this.tall) {
                i = 6;
            }

            this.fill(world, bounds, 0, 0, 0, 13, i - 1, 14, true, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.generateEntrance(world, random, bounds, this.entranceType, 4, 1, 0);
            this.fillRandomlyWithOutline(world, bounds, random, 0.07F, 2, 1, 1, 11, 4, 13, Blocks.WEB.defaultState(), Blocks.WEB.defaultState(), false);
            int j = 1;
            int k = 12;

            for (int l = 1; l <= 13; l++) {
                if ((l - 1) % 4 == 0) {
                    this.fillWithOutline(world, bounds, 1, 1, l, 1, 4, l, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
                    this.fillWithOutline(world, bounds, 12, 1, l, 12, 4, l, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
                    this.setBlockState(world, Blocks.TORCH.defaultState(), 2, 3, l, bounds);
                    this.setBlockState(world, Blocks.TORCH.defaultState(), 11, 3, l, bounds);
                    if (this.tall) {
                        this.fillWithOutline(world, bounds, 1, 6, l, 1, 9, l, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
                        this.fillWithOutline(world, bounds, 12, 6, l, 12, 9, l, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
                    }
                } else {
                    this.fillWithOutline(world, bounds, 1, 1, l, 1, 4, l, Blocks.BOOKSHELF.defaultState(), Blocks.BOOKSHELF.defaultState(), false);
                    this.fillWithOutline(world, bounds, 12, 1, l, 12, 4, l, Blocks.BOOKSHELF.defaultState(), Blocks.BOOKSHELF.defaultState(), false);
                    if (this.tall) {
                        this.fillWithOutline(world, bounds, 1, 6, l, 1, 9, l, Blocks.BOOKSHELF.defaultState(), Blocks.BOOKSHELF.defaultState(), false);
                        this.fillWithOutline(world, bounds, 12, 6, l, 12, 9, l, Blocks.BOOKSHELF.defaultState(), Blocks.BOOKSHELF.defaultState(), false);
                    }
                }
            }

            for (int k1 = 3; k1 < 12; k1 += 2) {
                this.fillWithOutline(world, bounds, 3, 1, k1, 4, 3, k1, Blocks.BOOKSHELF.defaultState(), Blocks.BOOKSHELF.defaultState(), false);
                this.fillWithOutline(world, bounds, 6, 1, k1, 7, 3, k1, Blocks.BOOKSHELF.defaultState(), Blocks.BOOKSHELF.defaultState(), false);
                this.fillWithOutline(world, bounds, 9, 1, k1, 10, 3, k1, Blocks.BOOKSHELF.defaultState(), Blocks.BOOKSHELF.defaultState(), false);
            }

            if (this.tall) {
                this.fillWithOutline(world, bounds, 1, 5, 1, 3, 5, 13, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
                this.fillWithOutline(world, bounds, 10, 5, 1, 12, 5, 13, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
                this.fillWithOutline(world, bounds, 4, 5, 1, 9, 5, 2, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
                this.fillWithOutline(world, bounds, 4, 5, 12, 9, 5, 13, Blocks.PLANKS.defaultState(), Blocks.PLANKS.defaultState(), false);
                this.setBlockState(world, Blocks.PLANKS.defaultState(), 9, 5, 11, bounds);
                this.setBlockState(world, Blocks.PLANKS.defaultState(), 8, 5, 11, bounds);
                this.setBlockState(world, Blocks.PLANKS.defaultState(), 9, 5, 10, bounds);
                this.fillWithOutline(world, bounds, 3, 6, 2, 3, 6, 12, Blocks.FENCE.defaultState(), Blocks.FENCE.defaultState(), false);
                this.fillWithOutline(world, bounds, 10, 6, 2, 10, 6, 10, Blocks.FENCE.defaultState(), Blocks.FENCE.defaultState(), false);
                this.fillWithOutline(world, bounds, 4, 6, 2, 9, 6, 2, Blocks.FENCE.defaultState(), Blocks.FENCE.defaultState(), false);
                this.fillWithOutline(world, bounds, 4, 6, 12, 8, 6, 12, Blocks.FENCE.defaultState(), Blocks.FENCE.defaultState(), false);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 9, 6, 11, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 8, 6, 11, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), 9, 6, 10, bounds);
                int l1 = this.postProcessBlockMetadata(Blocks.LADDER, 3);
                this.setBlockState(world, Blocks.LADDER.getStateFromMetadata(l1), 10, 1, 13, bounds);
                this.setBlockState(world, Blocks.LADDER.getStateFromMetadata(l1), 10, 2, 13, bounds);
                this.setBlockState(world, Blocks.LADDER.getStateFromMetadata(l1), 10, 3, 13, bounds);
                this.setBlockState(world, Blocks.LADDER.getStateFromMetadata(l1), 10, 4, 13, bounds);
                this.setBlockState(world, Blocks.LADDER.getStateFromMetadata(l1), 10, 5, 13, bounds);
                this.setBlockState(world, Blocks.LADDER.getStateFromMetadata(l1), 10, 6, 13, bounds);
                this.setBlockState(world, Blocks.LADDER.getStateFromMetadata(l1), 10, 7, 13, bounds);
                int i1 = 7;
                int j1 = 7;
                this.setBlockState(world, Blocks.FENCE.defaultState(), i1 - 1, 9, j1, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), i1, 9, j1, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), i1 - 1, 8, j1, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), i1, 8, j1, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), i1 - 1, 7, j1, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), i1, 7, j1, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), i1 - 2, 7, j1, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), i1 + 1, 7, j1, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), i1 - 1, 7, j1 - 1, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), i1 - 1, 7, j1 + 1, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), i1, 7, j1 - 1, bounds);
                this.setBlockState(world, Blocks.FENCE.defaultState(), i1, 7, j1 + 1, bounds);
                this.setBlockState(world, Blocks.TORCH.defaultState(), i1 - 2, 8, j1, bounds);
                this.setBlockState(world, Blocks.TORCH.defaultState(), i1 + 1, 8, j1, bounds);
                this.setBlockState(world, Blocks.TORCH.defaultState(), i1 - 1, 8, j1 - 1, bounds);
                this.setBlockState(world, Blocks.TORCH.defaultState(), i1 - 1, 8, j1 + 1, bounds);
                this.setBlockState(world, Blocks.TORCH.defaultState(), i1, 8, j1 - 1, bounds);
                this.setBlockState(world, Blocks.TORCH.defaultState(), i1, 8, j1 + 1, bounds);
            }

            this.placeChestWithLoot(
                world, bounds, random, 3, 3, 5, LootEntry.addAll(LOOT_ENTRIES, Items.ENCHANTED_BOOK.createLoot(random, 1, 5, 2)), 1 + random.nextInt(4)
            );
            if (this.tall) {
                this.setBlockState(world, Blocks.AIR.defaultState(), 12, 9, 1, bounds);
                this.placeChestWithLoot(
                    world, bounds, random, 12, 8, 1, LootEntry.addAll(LOOT_ENTRIES, Items.ENCHANTED_BOOK.createLoot(random, 1, 5, 2)), 1 + random.nextInt(4)
                );
            }

            return true;
        }
    }

    public static class PlainCorridor extends StrongholdPieces.StrongholdPiece {
        private int steps;

        public PlainCorridor() {
        }

        public PlainCorridor(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.steps = facing != Direction.NORTH && facing != Direction.SOUTH ? bounds.getSpanX() : bounds.getSpanZ();
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putInt("Steps", this.steps);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.steps = nbt.getInt("Steps");
        }

        public static StructureBox findSize(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing) {
            int i = 3;
            StructureBox structurebox = StructureBox.orient(x, y, z, -1, -1, 0, 5, 5, 4, facing);
            StructurePiece structurepiece = StructurePiece.getIntersectingPiece(pieces, structurebox);
            if (structurepiece == null) {
                return null;
            }

            if (structurepiece.getBounds().minY == structurebox.minY) {
                for (int j = 3; j >= 1; j--) {
                    structurebox = StructureBox.orient(x, y, z, -1, -1, 0, 5, 5, j - 1, facing);
                    if (!structurepiece.getBounds().intersects(structurebox)) {
                        return StructureBox.orient(x, y, z, -1, -1, 0, 5, 5, j, facing);
                    }
                }
            }

            return null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            for (int i = 0; i < this.steps; i++) {
                this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 0, 0, i, bounds);
                this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 1, 0, i, bounds);
                this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 2, 0, i, bounds);
                this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 3, 0, i, bounds);
                this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 4, 0, i, bounds);

                for (int j = 1; j <= 3; j++) {
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 0, j, i, bounds);
                    this.setBlockState(world, Blocks.AIR.defaultState(), 1, j, i, bounds);
                    this.setBlockState(world, Blocks.AIR.defaultState(), 2, j, i, bounds);
                    this.setBlockState(world, Blocks.AIR.defaultState(), 3, j, i, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 4, j, i, bounds);
                }

                this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 0, 4, i, bounds);
                this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 1, 4, i, bounds);
                this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 2, 4, i, bounds);
                this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 3, 4, i, bounds);
                this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 4, 4, i, bounds);
            }

            return true;
        }
    }

    public static class Prison extends StrongholdPieces.StrongholdPiece {
        public Prison() {
        }

        public Prison(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = this.pickEntranceType(random);
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((StrongholdPieces.Start)start, pieces, random, 1, 1);
        }

        public static StrongholdPieces.Prison of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -1, -1, 0, 9, 5, 11, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new StrongholdPieces.Prison(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            this.fill(world, bounds, 0, 0, 0, 8, 4, 10, true, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.generateEntrance(world, random, bounds, this.entranceType, 1, 1, 0);
            this.fillWithOutline(world, bounds, 1, 1, 10, 3, 3, 10, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fill(world, bounds, 4, 1, 1, 4, 3, 1, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fill(world, bounds, 4, 1, 3, 4, 3, 3, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fill(world, bounds, 4, 1, 7, 4, 3, 7, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fill(world, bounds, 4, 1, 9, 4, 3, 9, false, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.fillWithOutline(world, bounds, 4, 1, 4, 4, 3, 6, Blocks.IRON_BARS.defaultState(), Blocks.IRON_BARS.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 1, 5, 7, 3, 5, Blocks.IRON_BARS.defaultState(), Blocks.IRON_BARS.defaultState(), false);
            this.setBlockState(world, Blocks.IRON_BARS.defaultState(), 4, 3, 2, bounds);
            this.setBlockState(world, Blocks.IRON_BARS.defaultState(), 4, 3, 8, bounds);
            this.setBlockState(world, Blocks.IRON_DOOR.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.IRON_DOOR, 3)), 4, 1, 2, bounds);
            this.setBlockState(world, Blocks.IRON_DOOR.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.IRON_DOOR, 3) + 8), 4, 2, 2, bounds);
            this.setBlockState(world, Blocks.IRON_DOOR.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.IRON_DOOR, 3)), 4, 1, 8, bounds);
            this.setBlockState(world, Blocks.IRON_DOOR.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.IRON_DOOR, 3) + 8), 4, 2, 8, bounds);
            return true;
        }
    }

    public static class RightTurn extends StrongholdPieces.LeftTurn {
        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            if (this.facing != Direction.NORTH && this.facing != Direction.EAST) {
                this.generatePieceLeft((StrongholdPieces.Start)start, pieces, random, 1, 1);
            } else {
                this.generatePieceRight((StrongholdPieces.Start)start, pieces, random, 1, 1);
            }
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            this.fill(world, bounds, 0, 0, 0, 4, 4, 4, true, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.generateEntrance(world, random, bounds, this.entranceType, 1, 1, 0);
            if (this.facing != Direction.NORTH && this.facing != Direction.EAST) {
                this.fillWithOutline(world, bounds, 0, 1, 1, 0, 3, 3, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            } else {
                this.fillWithOutline(world, bounds, 4, 1, 1, 4, 3, 3, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            }

            return true;
        }
    }

    public static class RoomCrossing extends StrongholdPieces.StrongholdPiece {
        private static final List<LootEntry> LOOT_ENTRIES = Lists.newArrayList(
            new LootEntry(Items.IRON_INGOT, 0, 1, 5, 10),
            new LootEntry(Items.GOLD_INGOT, 0, 1, 3, 5),
            new LootEntry(Items.REDSTONE, 0, 4, 9, 5),
            new LootEntry(Items.COAL, 0, 3, 8, 10),
            new LootEntry(Items.BREAD, 0, 1, 3, 15),
            new LootEntry(Items.APPLE, 0, 1, 3, 15),
            new LootEntry(Items.IRON_PICKAXE, 0, 1, 1, 1)
        );
        protected int type;

        public RoomCrossing() {
        }

        public RoomCrossing(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = this.pickEntranceType(random);
            this.bounds = bounds;
            this.type = random.nextInt(5);
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putInt("Type", this.type);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.type = nbt.getInt("Type");
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((StrongholdPieces.Start)start, pieces, random, 4, 1);
            this.generatePieceLeft((StrongholdPieces.Start)start, pieces, random, 1, 4);
            this.generatePieceRight((StrongholdPieces.Start)start, pieces, random, 1, 4);
        }

        public static StrongholdPieces.RoomCrossing of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -4, -1, 0, 11, 7, 11, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new StrongholdPieces.RoomCrossing(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            this.fill(world, bounds, 0, 0, 0, 10, 6, 10, true, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.generateEntrance(world, random, bounds, this.entranceType, 4, 1, 0);
            this.fillWithOutline(world, bounds, 4, 1, 10, 6, 3, 10, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 1, 4, 0, 3, 6, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 10, 1, 4, 10, 3, 6, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            switch (this.type) {
                case 0:
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 5, 1, 5, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 5, 2, 5, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 5, 3, 5, bounds);
                    this.setBlockState(world, Blocks.TORCH.defaultState(), 4, 3, 5, bounds);
                    this.setBlockState(world, Blocks.TORCH.defaultState(), 6, 3, 5, bounds);
                    this.setBlockState(world, Blocks.TORCH.defaultState(), 5, 3, 4, bounds);
                    this.setBlockState(world, Blocks.TORCH.defaultState(), 5, 3, 6, bounds);
                    this.setBlockState(world, Blocks.STONE_SLAB.defaultState(), 4, 1, 4, bounds);
                    this.setBlockState(world, Blocks.STONE_SLAB.defaultState(), 4, 1, 5, bounds);
                    this.setBlockState(world, Blocks.STONE_SLAB.defaultState(), 4, 1, 6, bounds);
                    this.setBlockState(world, Blocks.STONE_SLAB.defaultState(), 6, 1, 4, bounds);
                    this.setBlockState(world, Blocks.STONE_SLAB.defaultState(), 6, 1, 5, bounds);
                    this.setBlockState(world, Blocks.STONE_SLAB.defaultState(), 6, 1, 6, bounds);
                    this.setBlockState(world, Blocks.STONE_SLAB.defaultState(), 5, 1, 4, bounds);
                    this.setBlockState(world, Blocks.STONE_SLAB.defaultState(), 5, 1, 6, bounds);
                    break;
                case 1:
                    for (int i1 = 0; i1 < 5; i1++) {
                        this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 3, 1, 3 + i1, bounds);
                        this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 7, 1, 3 + i1, bounds);
                        this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 3 + i1, 1, 3, bounds);
                        this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 3 + i1, 1, 7, bounds);
                    }

                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 5, 1, 5, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 5, 2, 5, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 5, 3, 5, bounds);
                    this.setBlockState(world, Blocks.FLOWING_WATER.defaultState(), 5, 4, 5, bounds);
                    break;
                case 2:
                    for (int i = 1; i <= 9; i++) {
                        this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 1, 3, i, bounds);
                        this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 9, 3, i, bounds);
                    }

                    for (int j = 1; j <= 9; j++) {
                        this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), j, 3, 1, bounds);
                        this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), j, 3, 9, bounds);
                    }

                    this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 5, 1, 4, bounds);
                    this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 5, 1, 6, bounds);
                    this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 5, 3, 4, bounds);
                    this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 5, 3, 6, bounds);
                    this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 4, 1, 5, bounds);
                    this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 6, 1, 5, bounds);
                    this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 4, 3, 5, bounds);
                    this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 6, 3, 5, bounds);

                    for (int k = 1; k <= 3; k++) {
                        this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 4, k, 4, bounds);
                        this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 6, k, 4, bounds);
                        this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 4, k, 6, bounds);
                        this.setBlockState(world, Blocks.COBBLESTONE.defaultState(), 6, k, 6, bounds);
                    }

                    this.setBlockState(world, Blocks.TORCH.defaultState(), 5, 3, 5, bounds);

                    for (int l = 2; l <= 8; l++) {
                        this.setBlockState(world, Blocks.PLANKS.defaultState(), 2, 3, l, bounds);
                        this.setBlockState(world, Blocks.PLANKS.defaultState(), 3, 3, l, bounds);
                        if (l <= 3 || l >= 7) {
                            this.setBlockState(world, Blocks.PLANKS.defaultState(), 4, 3, l, bounds);
                            this.setBlockState(world, Blocks.PLANKS.defaultState(), 5, 3, l, bounds);
                            this.setBlockState(world, Blocks.PLANKS.defaultState(), 6, 3, l, bounds);
                        }

                        this.setBlockState(world, Blocks.PLANKS.defaultState(), 7, 3, l, bounds);
                        this.setBlockState(world, Blocks.PLANKS.defaultState(), 8, 3, l, bounds);
                    }

                    this.setBlockState(
                        world, Blocks.LADDER.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.LADDER, Direction.WEST.getId())), 9, 1, 3, bounds
                    );
                    this.setBlockState(
                        world, Blocks.LADDER.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.LADDER, Direction.WEST.getId())), 9, 2, 3, bounds
                    );
                    this.setBlockState(
                        world, Blocks.LADDER.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.LADDER, Direction.WEST.getId())), 9, 3, 3, bounds
                    );
                    this.placeChestWithLoot(
                        world, bounds, random, 3, 4, 8, LootEntry.addAll(LOOT_ENTRIES, Items.ENCHANTED_BOOK.createLoot(random)), 1 + random.nextInt(4)
                    );
            }

            return true;
        }
    }

    public static class SpiralStaircase extends StrongholdPieces.StrongholdPiece {
        private boolean isSource;

        public SpiralStaircase() {
        }

        public SpiralStaircase(int generationDepth, Random random, int x, int z) {
            super(generationDepth);
            this.isSource = true;
            this.facing = Direction.Plane.HORIZONTAL.pick(random);
            this.entranceType = StrongholdPieces.StrongholdPiece.EntranceType.OPENING;
            switch (this.facing) {
                case NORTH:
                case SOUTH:
                    this.bounds = new StructureBox(x, 64, z, x + 5 - 1, 74, z + 5 - 1);
                    break;
                default:
                    this.bounds = new StructureBox(x, 64, z, x + 5 - 1, 74, z + 5 - 1);
            }
        }

        public SpiralStaircase(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.isSource = false;
            this.facing = facing;
            this.entranceType = this.pickEntranceType(random);
            this.bounds = bounds;
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putBoolean("Source", this.isSource);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.isSource = nbt.getBoolean("Source");
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            if (this.isSource) {
                StrongholdPieces.forcedPiece = StrongholdPieces.FiveWayCrossing.class;
            }

            this.generatePieceForward((StrongholdPieces.Start)start, pieces, random, 1, 1);
        }

        public static StrongholdPieces.SpiralStaircase of(
            List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -1, -7, 0, 5, 11, 5, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new StrongholdPieces.SpiralStaircase(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            this.fill(world, bounds, 0, 0, 0, 4, 10, 4, true, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.generateEntrance(world, random, bounds, this.entranceType, 1, 7, 0);
            this.generateEntrance(world, random, bounds, StrongholdPieces.StrongholdPiece.EntranceType.OPENING, 1, 1, 4);
            this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 2, 6, 1, bounds);
            this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 1, 5, 1, bounds);
            this.setBlockState(world, Blocks.STONE_SLAB.getStateFromMetadata(StoneSlabBlock.Variant.STONE.getId()), 1, 6, 1, bounds);
            this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 1, 5, 2, bounds);
            this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 1, 4, 3, bounds);
            this.setBlockState(world, Blocks.STONE_SLAB.getStateFromMetadata(StoneSlabBlock.Variant.STONE.getId()), 1, 5, 3, bounds);
            this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 2, 4, 3, bounds);
            this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 3, 3, 3, bounds);
            this.setBlockState(world, Blocks.STONE_SLAB.getStateFromMetadata(StoneSlabBlock.Variant.STONE.getId()), 3, 4, 3, bounds);
            this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 3, 3, 2, bounds);
            this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 3, 2, 1, bounds);
            this.setBlockState(world, Blocks.STONE_SLAB.getStateFromMetadata(StoneSlabBlock.Variant.STONE.getId()), 3, 3, 1, bounds);
            this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 2, 2, 1, bounds);
            this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 1, 1, 1, bounds);
            this.setBlockState(world, Blocks.STONE_SLAB.getStateFromMetadata(StoneSlabBlock.Variant.STONE.getId()), 1, 2, 1, bounds);
            this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 1, 1, 2, bounds);
            this.setBlockState(world, Blocks.STONE_SLAB.getStateFromMetadata(StoneSlabBlock.Variant.STONE.getId()), 1, 1, 3, bounds);
            return true;
        }
    }

    public static class Start extends StrongholdPieces.SpiralStaircase {
        public StrongholdPieces.StrongholdPieceWeight previous;
        public StrongholdPieces.EndPortalRoom endPortalRoom;
        public List<StructurePiece> children = Lists.newArrayList();

        public Start() {
        }

        public Start(int i, Random random, int j, int k) {
            super(0, random, j, k);
        }

        @Override
        public BlockPos getCenterPos() {
            return this.endPortalRoom != null ? this.endPortalRoom.getCenterPos() : super.getCenterPos();
        }
    }

    static class StoneBrickPicker extends StructurePiece.BlockPicker {
        private StoneBrickPicker() {
        }

        @Override
        public void pick(Random randomm, int x, int y, int z, boolean isNonAir) {
            if (isNonAir) {
                float f = randomm.nextFloat();
                if (f < 0.2F) {
                    this.state = Blocks.STONE_BRICKS.getStateFromMetadata(StonebrickBlock.CRACKED_ID);
                } else if (f < 0.5F) {
                    this.state = Blocks.STONE_BRICKS.getStateFromMetadata(StonebrickBlock.MOSSY_ID);
                } else if (f < 0.55F) {
                    this.state = Blocks.MONSTER_EGG.getStateFromMetadata(InfestedBlock.Variant.STONEBRICK.getId());
                } else {
                    this.state = Blocks.STONE_BRICKS.defaultState();
                }
            } else {
                this.state = Blocks.AIR.defaultState();
            }
        }
    }

    public static class StraightCorridor extends StrongholdPieces.StrongholdPiece {
        private boolean left;
        private boolean right;

        public StraightCorridor() {
        }

        public StraightCorridor(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = this.pickEntranceType(random);
            this.bounds = bounds;
            this.left = random.nextInt(2) == 0;
            this.right = random.nextInt(2) == 0;
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putBoolean("Left", this.left);
            nbt.putBoolean("Right", this.right);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.left = nbt.getBoolean("Left");
            this.right = nbt.getBoolean("Right");
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((StrongholdPieces.Start)start, pieces, random, 1, 1);
            if (this.left) {
                this.generatePieceLeft((StrongholdPieces.Start)start, pieces, random, 1, 2);
            }

            if (this.right) {
                this.generatePieceRight((StrongholdPieces.Start)start, pieces, random, 1, 2);
            }
        }

        public static StrongholdPieces.StraightCorridor of(
            List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -1, -1, 0, 5, 5, 7, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new StrongholdPieces.StraightCorridor(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            this.fill(world, bounds, 0, 0, 0, 4, 4, 6, true, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.generateEntrance(world, random, bounds, this.entranceType, 1, 1, 0);
            this.generateEntrance(world, random, bounds, StrongholdPieces.StrongholdPiece.EntranceType.OPENING, 1, 1, 6);
            this.setBlockStateWithThreshold(world, bounds, random, 0.1F, 1, 2, 1, Blocks.TORCH.defaultState());
            this.setBlockStateWithThreshold(world, bounds, random, 0.1F, 3, 2, 1, Blocks.TORCH.defaultState());
            this.setBlockStateWithThreshold(world, bounds, random, 0.1F, 1, 2, 5, Blocks.TORCH.defaultState());
            this.setBlockStateWithThreshold(world, bounds, random, 0.1F, 3, 2, 5, Blocks.TORCH.defaultState());
            if (this.left) {
                this.fillWithOutline(world, bounds, 0, 1, 2, 0, 3, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            }

            if (this.right) {
                this.fillWithOutline(world, bounds, 4, 1, 2, 4, 3, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            }

            return true;
        }
    }

    public static class StraightStairs extends StrongholdPieces.StrongholdPiece {
        public StraightStairs() {
        }

        public StraightStairs(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = this.pickEntranceType(random);
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((StrongholdPieces.Start)start, pieces, random, 1, 1);
        }

        public static StrongholdPieces.StraightStairs of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -1, -7, 0, 5, 11, 8, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new StrongholdPieces.StraightStairs(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.bordersOnLiquids(world, bounds)) {
                return false;
            }

            this.fill(world, bounds, 0, 0, 0, 4, 10, 7, true, random, StrongholdPieces.STONE_BRICK_PICKER);
            this.generateEntrance(world, random, bounds, this.entranceType, 1, 7, 0);
            this.generateEntrance(world, random, bounds, StrongholdPieces.StrongholdPiece.EntranceType.OPENING, 1, 1, 7);
            int i = this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 2);

            for (int j = 0; j < 6; j++) {
                this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 1, 6 - j, 1 + j, bounds);
                this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 2, 6 - j, 1 + j, bounds);
                this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 3, 6 - j, 1 + j, bounds);
                if (j < 5) {
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 1, 5 - j, 1 + j, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 2, 5 - j, 1 + j, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), 3, 5 - j, 1 + j, bounds);
                }
            }

            return true;
        }
    }

    abstract static class StrongholdPiece extends StructurePiece {
        protected StrongholdPieces.StrongholdPiece.EntranceType entranceType = StrongholdPieces.StrongholdPiece.EntranceType.OPENING;

        public StrongholdPiece() {
        }

        protected StrongholdPiece(int i) {
            super(i);
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            nbt.putString("EntryDoor", this.entranceType.name());
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            this.entranceType = StrongholdPieces.StrongholdPiece.EntranceType.valueOf(nbt.getString("EntryDoor"));
        }

        protected void generateEntrance(
            World world, Random random, StructureBox bounds, StrongholdPieces.StrongholdPiece.EntranceType type, int x, int y, int z
        ) {
            switch (type) {
                case OPENING:
                default:
                    this.fillWithOutline(world, bounds, x, y, z, x + 3 - 1, y + 3 - 1, z, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
                    break;
                case WOOD_DOOR:
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), x, y, z, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), x, y + 1, z, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), x, y + 2, z, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), x + 1, y + 2, z, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), x + 2, y + 2, z, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), x + 2, y + 1, z, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), x + 2, y, z, bounds);
                    this.setBlockState(world, Blocks.WOODEN_DOOR.defaultState(), x + 1, y, z, bounds);
                    this.setBlockState(world, Blocks.WOODEN_DOOR.getStateFromMetadata(8), x + 1, y + 1, z, bounds);
                    break;
                case GATES:
                    this.setBlockState(world, Blocks.AIR.defaultState(), x + 1, y, z, bounds);
                    this.setBlockState(world, Blocks.AIR.defaultState(), x + 1, y + 1, z, bounds);
                    this.setBlockState(world, Blocks.IRON_BARS.defaultState(), x, y, z, bounds);
                    this.setBlockState(world, Blocks.IRON_BARS.defaultState(), x, y + 1, z, bounds);
                    this.setBlockState(world, Blocks.IRON_BARS.defaultState(), x, y + 2, z, bounds);
                    this.setBlockState(world, Blocks.IRON_BARS.defaultState(), x + 1, y + 2, z, bounds);
                    this.setBlockState(world, Blocks.IRON_BARS.defaultState(), x + 2, y + 2, z, bounds);
                    this.setBlockState(world, Blocks.IRON_BARS.defaultState(), x + 2, y + 1, z, bounds);
                    this.setBlockState(world, Blocks.IRON_BARS.defaultState(), x + 2, y, z, bounds);
                    break;
                case IRON_DOOR:
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), x, y, z, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), x, y + 1, z, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), x, y + 2, z, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), x + 1, y + 2, z, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), x + 2, y + 2, z, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), x + 2, y + 1, z, bounds);
                    this.setBlockState(world, Blocks.STONE_BRICKS.defaultState(), x + 2, y, z, bounds);
                    this.setBlockState(world, Blocks.IRON_DOOR.defaultState(), x + 1, y, z, bounds);
                    this.setBlockState(world, Blocks.IRON_DOOR.getStateFromMetadata(8), x + 1, y + 1, z, bounds);
                    this.setBlockState(
                        world, Blocks.STONE_BUTTON.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STONE_BUTTON, 4)), x + 2, y + 1, z + 1, bounds
                    );
                    this.setBlockState(
                        world, Blocks.STONE_BUTTON.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STONE_BUTTON, 3)), x + 2, y + 1, z - 1, bounds
                    );
            }
        }

        protected StrongholdPieces.StrongholdPiece.EntranceType pickEntranceType(Random random) {
            int i = random.nextInt(5);
            switch (i) {
                case 0:
                case 1:
                default:
                    return StrongholdPieces.StrongholdPiece.EntranceType.OPENING;
                case 2:
                    return StrongholdPieces.StrongholdPiece.EntranceType.WOOD_DOOR;
                case 3:
                    return StrongholdPieces.StrongholdPiece.EntranceType.GATES;
                case 4:
                    return StrongholdPieces.StrongholdPiece.EntranceType.IRON_DOOR;
            }
        }

        protected StructurePiece generatePieceForward(StrongholdPieces.Start startPiece, List<StructurePiece> pieces, Random random, int offset, int yOffset) {
            if (this.facing != null) {
                switch (this.facing) {
                    case NORTH:
                        return StrongholdPieces.tryGenerateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX + offset,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ - 1,
                            this.facing,
                            this.getGenerationDepth()
                        );
                    case SOUTH:
                        return StrongholdPieces.tryGenerateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX + offset,
                            this.bounds.minY + yOffset,
                            this.bounds.maxZ + 1,
                            this.facing,
                            this.getGenerationDepth()
                        );
                    case WEST:
                        return StrongholdPieces.tryGenerateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX - 1,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ + offset,
                            this.facing,
                            this.getGenerationDepth()
                        );
                    case EAST:
                        return StrongholdPieces.tryGenerateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.maxX + 1,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ + offset,
                            this.facing,
                            this.getGenerationDepth()
                        );
                }
            }

            return null;
        }

        protected StructurePiece generatePieceLeft(StrongholdPieces.Start startPiece, List<StructurePiece> pieces, Random random, int yOffset, int offset) {
            if (this.facing != null) {
                switch (this.facing) {
                    case NORTH:
                        return StrongholdPieces.tryGenerateNextPiece(
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
                        return StrongholdPieces.tryGenerateNextPiece(
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
                        return StrongholdPieces.tryGenerateNextPiece(
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
                        return StrongholdPieces.tryGenerateNextPiece(
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

        protected StructurePiece generatePieceRight(StrongholdPieces.Start startPiece, List<StructurePiece> pieces, Random random, int yOffset, int offset) {
            if (this.facing != null) {
                switch (this.facing) {
                    case NORTH:
                        return StrongholdPieces.tryGenerateNextPiece(
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
                        return StrongholdPieces.tryGenerateNextPiece(
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
                        return StrongholdPieces.tryGenerateNextPiece(
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
                        return StrongholdPieces.tryGenerateNextPiece(
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

        protected static boolean isValidBounds(StructureBox bounds) {
            return bounds != null && bounds.minY > 10;
        }

        public enum EntranceType {
            OPENING,
            WOOD_DOOR,
            GATES,
            IRON_DOOR;
        }
    }

    static class StrongholdPieceWeight {
        public Class<? extends StrongholdPieces.StrongholdPiece> type;
        public final int weight;
        public int amountGenerated;
        public int maxAmount;

        public StrongholdPieceWeight(Class<? extends StrongholdPieces.StrongholdPiece> type, int weight, int maxAmount) {
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
}
