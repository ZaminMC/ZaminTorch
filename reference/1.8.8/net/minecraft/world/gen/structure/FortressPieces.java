package net.minecraft.world.gen.structure;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class FortressPieces {
    private static final FortressPieces.FortressPieceWeight[] BRIDGE_PIECE_WEIGHTS = new FortressPieces.FortressPieceWeight[]{
        new FortressPieces.FortressPieceWeight(FortressPieces.StraightBridge.class, 30, 0, true),
        new FortressPieces.FortressPieceWeight(FortressPieces.BridgeCrossing.class, 10, 4),
        new FortressPieces.FortressPieceWeight(FortressPieces.LargeCrossing.class, 10, 4),
        new FortressPieces.FortressPieceWeight(FortressPieces.BridgeStairs.class, 10, 3),
        new FortressPieces.FortressPieceWeight(FortressPieces.BlazeSpawner.class, 5, 2),
        new FortressPieces.FortressPieceWeight(FortressPieces.CastleEntrance.class, 5, 1)
    };
    private static final FortressPieces.FortressPieceWeight[] CASTLE_PIECE_WEIGHTS = new FortressPieces.FortressPieceWeight[]{
        new FortressPieces.FortressPieceWeight(FortressPieces.SmallCorridor.class, 25, 0, true),
        new FortressPieces.FortressPieceWeight(FortressPieces.SmallCorridorCrossing.class, 15, 5),
        new FortressPieces.FortressPieceWeight(FortressPieces.SmallCorridorRightTurn.class, 5, 10),
        new FortressPieces.FortressPieceWeight(FortressPieces.SmallCorridorLeftTurn.class, 5, 10),
        new FortressPieces.FortressPieceWeight(FortressPieces.StairsCorridor.class, 10, 3, true),
        new FortressPieces.FortressPieceWeight(FortressPieces.CorridorBalcony.class, 7, 2),
        new FortressPieces.FortressPieceWeight(FortressPieces.NetherwartFarm.class, 5, 2)
    };

    public static void register() {
        StructureRegistry.registerPiece(FortressPieces.BridgeCrossing.class, "NeBCr");
        StructureRegistry.registerPiece(FortressPieces.BridgeEnd.class, "NeBEF");
        StructureRegistry.registerPiece(FortressPieces.StraightBridge.class, "NeBS");
        StructureRegistry.registerPiece(FortressPieces.StairsCorridor.class, "NeCCS");
        StructureRegistry.registerPiece(FortressPieces.CorridorBalcony.class, "NeCTB");
        StructureRegistry.registerPiece(FortressPieces.CastleEntrance.class, "NeCE");
        StructureRegistry.registerPiece(FortressPieces.SmallCorridorCrossing.class, "NeSCSC");
        StructureRegistry.registerPiece(FortressPieces.SmallCorridorLeftTurn.class, "NeSCLT");
        StructureRegistry.registerPiece(FortressPieces.SmallCorridor.class, "NeSC");
        StructureRegistry.registerPiece(FortressPieces.SmallCorridorRightTurn.class, "NeSCRT");
        StructureRegistry.registerPiece(FortressPieces.NetherwartFarm.class, "NeCSR");
        StructureRegistry.registerPiece(FortressPieces.BlazeSpawner.class, "NeMT");
        StructureRegistry.registerPiece(FortressPieces.LargeCrossing.class, "NeRC");
        StructureRegistry.registerPiece(FortressPieces.BridgeStairs.class, "NeSR");
        StructureRegistry.registerPiece(FortressPieces.Start.class, "NeStart");
    }

    private static FortressPieces.FortressPiece createPiece(
        FortressPieces.FortressPieceWeight weight, List<StructurePiece> entries, Random random, int x, int y, int z, Direction facing, int generationDepth
    ) {
        Class<? extends FortressPieces.FortressPiece> oclass = weight.type;
        FortressPieces.FortressPiece fortresspieces$fortresspiece = null;
        if (oclass == FortressPieces.StraightBridge.class) {
            fortresspieces$fortresspiece = FortressPieces.StraightBridge.of(entries, random, x, y, z, facing, generationDepth);
        } else if (oclass == FortressPieces.BridgeCrossing.class) {
            fortresspieces$fortresspiece = FortressPieces.BridgeCrossing.of(entries, random, x, y, z, facing, generationDepth);
        } else if (oclass == FortressPieces.LargeCrossing.class) {
            fortresspieces$fortresspiece = FortressPieces.LargeCrossing.of(entries, random, x, y, z, facing, generationDepth);
        } else if (oclass == FortressPieces.BridgeStairs.class) {
            fortresspieces$fortresspiece = FortressPieces.BridgeStairs.of(entries, random, x, y, z, generationDepth, facing);
        } else if (oclass == FortressPieces.BlazeSpawner.class) {
            fortresspieces$fortresspiece = FortressPieces.BlazeSpawner.of(entries, random, x, y, z, generationDepth, facing);
        } else if (oclass == FortressPieces.CastleEntrance.class) {
            fortresspieces$fortresspiece = FortressPieces.CastleEntrance.of(entries, random, x, y, z, facing, generationDepth);
        } else if (oclass == FortressPieces.SmallCorridor.class) {
            fortresspieces$fortresspiece = FortressPieces.SmallCorridor.of(entries, random, x, y, z, facing, generationDepth);
        } else if (oclass == FortressPieces.SmallCorridorRightTurn.class) {
            fortresspieces$fortresspiece = FortressPieces.SmallCorridorRightTurn.of(entries, random, x, y, z, facing, generationDepth);
        } else if (oclass == FortressPieces.SmallCorridorLeftTurn.class) {
            fortresspieces$fortresspiece = FortressPieces.SmallCorridorLeftTurn.of(entries, random, x, y, z, facing, generationDepth);
        } else if (oclass == FortressPieces.StairsCorridor.class) {
            fortresspieces$fortresspiece = FortressPieces.StairsCorridor.of(entries, random, x, y, z, facing, generationDepth);
        } else if (oclass == FortressPieces.CorridorBalcony.class) {
            fortresspieces$fortresspiece = FortressPieces.CorridorBalcony.of(entries, random, x, y, z, facing, generationDepth);
        } else if (oclass == FortressPieces.SmallCorridorCrossing.class) {
            fortresspieces$fortresspiece = FortressPieces.SmallCorridorCrossing.of(entries, random, x, y, z, facing, generationDepth);
        } else if (oclass == FortressPieces.NetherwartFarm.class) {
            fortresspieces$fortresspiece = FortressPieces.NetherwartFarm.of(entries, random, x, y, z, facing, generationDepth);
        }

        return fortresspieces$fortresspiece;
    }

    public static class BlazeSpawner extends FortressPieces.FortressPiece {
        private boolean shouldGenerateMobSpawner;

        public BlazeSpawner() {
        }

        public BlazeSpawner(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.shouldGenerateMobSpawner = nbt.getBoolean("Mob");
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putBoolean("Mob", this.shouldGenerateMobSpawner);
        }

        public static FortressPieces.BlazeSpawner of(List<StructurePiece> pieces, Random random, int x, int y, int z, int generationDepth, Direction facing) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -2, 0, 0, 7, 8, 9, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new FortressPieces.BlazeSpawner(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(world, bounds, 0, 2, 0, 6, 7, 7, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 0, 5, 1, 7, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 2, 1, 5, 2, 7, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 3, 2, 5, 3, 7, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 4, 3, 5, 4, 7, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 2, 0, 1, 4, 2, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 2, 0, 5, 4, 2, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 5, 2, 1, 5, 3, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 5, 2, 5, 5, 3, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 5, 3, 0, 5, 8, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 5, 3, 6, 5, 8, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 5, 8, 5, 5, 8, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), 1, 6, 3, bounds);
            this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), 5, 6, 3, bounds);
            this.fillWithOutline(world, bounds, 0, 6, 3, 0, 6, 8, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 6, 3, 6, 6, 8, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 6, 8, 5, 7, 8, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 8, 8, 4, 8, 8, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            if (!this.shouldGenerateMobSpawner) {
                BlockPos blockpos = new BlockPos(this.transformX(3, 5), this.transformY(5), this.transformZ(3, 5));
                if (bounds.contains(blockpos)) {
                    this.shouldGenerateMobSpawner = true;
                    world.setBlockState(blockpos, Blocks.MOB_SPAWNER.defaultState(), 2);
                    BlockEntity blockentity = world.getBlockEntity(blockpos);
                    if (blockentity instanceof MobSpawnerBlockEntity) {
                        ((MobSpawnerBlockEntity)blockentity).getSpawner().setType("Blaze");
                    }
                }
            }

            for (int i = 0; i <= 6; i++) {
                for (int j = 0; j <= 6; j++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), i, -1, j, bounds);
                }
            }

            return true;
        }
    }

    public static class BridgeCrossing extends FortressPieces.FortressPiece {
        public BridgeCrossing() {
        }

        public BridgeCrossing(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        protected BridgeCrossing(Random random, int chunkX, int chunkZ) {
            super(0);
            this.facing = Direction.Plane.HORIZONTAL.pick(random);
            switch (this.facing) {
                case NORTH:
                case SOUTH:
                    this.bounds = new StructureBox(chunkX, 64, chunkZ, chunkX + 19 - 1, 73, chunkZ + 19 - 1);
                    break;
                default:
                    this.bounds = new StructureBox(chunkX, 64, chunkZ, chunkX + 19 - 1, 73, chunkZ + 19 - 1);
            }
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressPieces.Start)start, pieces, random, 8, 3, false);
            this.generatePieceLeft((FortressPieces.Start)start, pieces, random, 3, 8, false);
            this.generatePieceRight((FortressPieces.Start)start, pieces, random, 3, 8, false);
        }

        public static FortressPieces.BridgeCrossing of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -8, -3, 0, 19, 10, 19, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new FortressPieces.BridgeCrossing(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(world, bounds, 7, 3, 0, 11, 4, 18, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 3, 7, 18, 4, 11, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 5, 0, 10, 7, 18, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 5, 8, 18, 7, 10, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 7, 5, 0, 7, 5, 7, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 7, 5, 11, 7, 5, 18, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 11, 5, 0, 11, 5, 7, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 11, 5, 11, 11, 5, 18, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 5, 7, 7, 5, 7, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 11, 5, 7, 18, 5, 7, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 5, 11, 7, 5, 11, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 11, 5, 11, 18, 5, 11, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 7, 2, 0, 11, 2, 5, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 7, 2, 13, 11, 2, 18, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 7, 0, 0, 11, 1, 3, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 7, 0, 15, 11, 1, 18, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);

            for (int i = 7; i <= 11; i++) {
                for (int j = 0; j <= 2; j++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), i, -1, j, bounds);
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), i, -1, 18 - j, bounds);
                }
            }

            this.fillWithOutline(world, bounds, 0, 2, 7, 5, 2, 11, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 13, 2, 7, 18, 2, 11, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 0, 7, 3, 1, 11, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 15, 0, 7, 18, 1, 11, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);

            for (int k = 0; k <= 2; k++) {
                for (int l = 7; l <= 11; l++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), k, -1, l, bounds);
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), 18 - k, -1, l, bounds);
                }
            }

            return true;
        }
    }

    public static class BridgeEnd extends FortressPieces.FortressPiece {
        private int seed;

        public BridgeEnd() {
        }

        public BridgeEnd(int generationDepth, Random seed, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.seed = seed.nextInt();
        }

        public static FortressPieces.BridgeEnd of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -1, -3, 0, 5, 10, 8, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new FortressPieces.BridgeEnd(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.seed = nbt.getInt("Seed");
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putInt("Seed", this.seed);
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            Random randomx = new Random(this.seed);

            for (int i = 0; i <= 4; i++) {
                for (int j = 3; j <= 4; j++) {
                    int k = randomx.nextInt(8);
                    this.fillWithOutline(world, bounds, i, j, 0, i, j, k, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
                }
            }

            int l = randomx.nextInt(8);
            this.fillWithOutline(world, bounds, 0, 5, 0, 0, 5, l, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            l = randomx.nextInt(8);
            this.fillWithOutline(world, bounds, 4, 5, 0, 4, 5, l, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);

            for (int i1 = 0; i1 <= 4; i1++) {
                int k1 = randomx.nextInt(5);
                this.fillWithOutline(world, bounds, i1, 2, 0, i1, 2, k1, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            }

            for (int j1 = 0; j1 <= 4; j1++) {
                for (int l1 = 0; l1 <= 1; l1++) {
                    int i2 = randomx.nextInt(3);
                    this.fillWithOutline(world, bounds, j1, l1, 0, j1, l1, i2, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
                }
            }

            return true;
        }
    }

    public static class BridgeStairs extends FortressPieces.FortressPiece {
        public BridgeStairs() {
        }

        public BridgeStairs(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceRight((FortressPieces.Start)start, pieces, random, 6, 2, false);
        }

        public static FortressPieces.BridgeStairs of(List<StructurePiece> pieces, Random random, int x, int y, int z, int generationDepth, Direction facing) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -2, 0, 0, 7, 11, 7, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new FortressPieces.BridgeStairs(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(world, bounds, 0, 0, 0, 6, 1, 6, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 6, 10, 6, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 1, 8, 0, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 2, 0, 6, 8, 0, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 1, 0, 8, 6, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 2, 1, 6, 8, 6, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 2, 6, 5, 8, 6, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 3, 2, 0, 5, 4, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 3, 2, 6, 5, 2, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 3, 4, 6, 5, 4, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.setBlockState(world, Blocks.NETHER_BRICKS.defaultState(), 5, 2, 5, bounds);
            this.fillWithOutline(world, bounds, 4, 2, 5, 4, 3, 5, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 3, 2, 5, 3, 4, 5, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 2, 5, 2, 5, 5, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 2, 5, 1, 6, 5, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 7, 1, 5, 7, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 8, 2, 6, 8, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 6, 0, 4, 8, 0, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 5, 0, 4, 5, 0, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);

            for (int i = 0; i <= 6; i++) {
                for (int j = 0; j <= 6; j++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), i, -1, j, bounds);
                }
            }

            return true;
        }
    }

    public static class CastleEntrance extends FortressPieces.FortressPiece {
        public CastleEntrance() {
        }

        public CastleEntrance(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressPieces.Start)start, pieces, random, 5, 3, true);
        }

        public static FortressPieces.CastleEntrance of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -5, -3, 0, 13, 14, 13, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new FortressPieces.CastleEntrance(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(world, bounds, 0, 3, 0, 12, 4, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 5, 0, 12, 13, 12, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 5, 0, 1, 12, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 11, 5, 0, 12, 12, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 5, 11, 4, 12, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 5, 11, 10, 12, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 9, 11, 7, 12, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 5, 0, 4, 12, 1, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 5, 0, 10, 12, 1, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 9, 0, 7, 12, 1, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 11, 2, 10, 12, 10, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 8, 0, 7, 8, 0, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);

            for (int i = 1; i <= 11; i += 2) {
                this.fillWithOutline(
                    world, bounds, i, 10, 0, i, 11, 0, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false
                );
                this.fillWithOutline(
                    world, bounds, i, 10, 12, i, 11, 12, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false
                );
                this.fillWithOutline(
                    world, bounds, 0, 10, i, 0, 11, i, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false
                );
                this.fillWithOutline(
                    world, bounds, 12, 10, i, 12, 11, i, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false
                );
                this.setBlockState(world, Blocks.NETHER_BRICKS.defaultState(), i, 13, 0, bounds);
                this.setBlockState(world, Blocks.NETHER_BRICKS.defaultState(), i, 13, 12, bounds);
                this.setBlockState(world, Blocks.NETHER_BRICKS.defaultState(), 0, 13, i, bounds);
                this.setBlockState(world, Blocks.NETHER_BRICKS.defaultState(), 12, 13, i, bounds);
                this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), i + 1, 13, 0, bounds);
                this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), i + 1, 13, 12, bounds);
                this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), 0, 13, i + 1, bounds);
                this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), 12, 13, i + 1, bounds);
            }

            this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), 0, 13, 0, bounds);
            this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), 0, 13, 12, bounds);
            this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), 0, 13, 0, bounds);
            this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), 12, 13, 0, bounds);

            for (int k = 3; k <= 9; k += 2) {
                this.fillWithOutline(world, bounds, 1, 7, k, 1, 8, k, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
                this.fillWithOutline(
                    world, bounds, 11, 7, k, 11, 8, k, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false
                );
            }

            this.fillWithOutline(world, bounds, 4, 2, 0, 8, 2, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 4, 12, 2, 8, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 0, 0, 8, 1, 3, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 0, 9, 8, 1, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 0, 4, 3, 1, 8, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 9, 0, 4, 12, 1, 8, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);

            for (int l = 4; l <= 8; l++) {
                for (int j = 0; j <= 2; j++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), l, -1, j, bounds);
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), l, -1, 12 - j, bounds);
                }
            }

            for (int i1 = 0; i1 <= 2; i1++) {
                for (int j1 = 4; j1 <= 8; j1++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), i1, -1, j1, bounds);
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), 12 - i1, -1, j1, bounds);
                }
            }

            this.fillWithOutline(world, bounds, 5, 5, 5, 7, 5, 7, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 1, 6, 6, 4, 6, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.setBlockState(world, Blocks.NETHER_BRICKS.defaultState(), 6, 0, 6, bounds);
            this.setBlockState(world, Blocks.FLOWING_LAVA.defaultState(), 6, 5, 6, bounds);
            BlockPos blockpos = new BlockPos(this.transformX(6, 6), this.transformY(5), this.transformZ(6, 6));
            if (bounds.contains(blockpos)) {
                world.tickBlockNow(Blocks.FLOWING_LAVA, blockpos, random);
            }

            return true;
        }
    }

    public static class CorridorBalcony extends FortressPieces.FortressPiece {
        public CorridorBalcony() {
        }

        public CorridorBalcony(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            int i = 1;
            if (this.facing == Direction.WEST || this.facing == Direction.NORTH) {
                i = 5;
            }

            this.generatePieceLeft((FortressPieces.Start)start, pieces, random, 0, i, random.nextInt(8) > 0);
            this.generatePieceRight((FortressPieces.Start)start, pieces, random, 0, i, random.nextInt(8) > 0);
        }

        public static FortressPieces.CorridorBalcony of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -3, 0, 0, 9, 7, 9, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new FortressPieces.CorridorBalcony(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(world, bounds, 0, 0, 0, 8, 1, 8, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 8, 5, 8, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 6, 0, 8, 6, 5, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 2, 5, 0, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 2, 0, 8, 5, 0, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 3, 0, 1, 4, 0, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 7, 3, 0, 7, 4, 0, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 4, 8, 2, 8, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 1, 4, 2, 2, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 1, 4, 7, 2, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 3, 8, 8, 3, 8, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 3, 6, 0, 3, 7, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 3, 6, 8, 3, 7, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 3, 4, 0, 5, 5, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 3, 4, 8, 5, 5, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 3, 5, 2, 5, 5, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 3, 5, 7, 5, 5, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 4, 5, 1, 5, 5, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 7, 4, 5, 7, 5, 5, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);

            for (int i = 0; i <= 5; i++) {
                for (int j = 0; j <= 8; j++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), j, -1, i, bounds);
                }
            }

            return true;
        }
    }

    abstract static class FortressPiece extends StructurePiece {
        protected static final List<LootEntry> LOOT_ENTRIES = Lists.newArrayList(
            new LootEntry(Items.DIAMOND, 0, 1, 3, 5),
            new LootEntry(Items.IRON_INGOT, 0, 1, 5, 5),
            new LootEntry(Items.GOLD_INGOT, 0, 1, 3, 15),
            new LootEntry(Items.GOLDEN_SWORD, 0, 1, 1, 5),
            new LootEntry(Items.GOLDEN_CHESTPLATE, 0, 1, 1, 5),
            new LootEntry(Items.FLINT_AND_STEEL, 0, 1, 1, 5),
            new LootEntry(Items.NETHER_WART, 0, 3, 7, 5),
            new LootEntry(Items.SADDLE, 0, 1, 1, 10),
            new LootEntry(Items.GOLDEN_HORSE_ARMOR, 0, 1, 1, 8),
            new LootEntry(Items.IRON_HORSE_ARMOR, 0, 1, 1, 5),
            new LootEntry(Items.DIAMOND_HORSE_ARMOR, 0, 1, 1, 3),
            new LootEntry(Item.byBlock(Blocks.OBSIDIAN), 0, 2, 4, 2)
        );

        public FortressPiece() {
        }

        protected FortressPiece(int i) {
            super(i);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
        }

        /**
         * Will return the total weight of the given piece weights if more pieces can still be generated, or -1.
         */
        private int getTotalWeight(List<FortressPieces.FortressPieceWeight> pieceWeights) {
            boolean flag = false;
            int i = 0;

            for (FortressPieces.FortressPieceWeight fortresspieces$fortresspieceweight : pieceWeights) {
                if (fortresspieces$fortresspieceweight.maxAmount > 0
                    && fortresspieces$fortresspieceweight.amountGenerated < fortresspieces$fortresspieceweight.maxAmount) {
                    flag = true;
                }

                i += fortresspieces$fortresspieceweight.weight;
            }

            return flag ? i : -1;
        }

        private FortressPieces.FortressPiece generateNextPiece(
            FortressPieces.Start startPiece,
            List<FortressPieces.FortressPieceWeight> weights,
            List<StructurePiece> pieces,
            Random random,
            int x,
            int y,
            int z,
            Direction facing,
            int generationDepth
        ) {
            int i = this.getTotalWeight(weights);
            boolean flag = i > 0 && generationDepth <= 30;
            int j = 0;

            while (j < 5 && flag) {
                j++;
                int k = random.nextInt(i);

                for (FortressPieces.FortressPieceWeight fortresspieces$fortresspieceweight : weights) {
                    k -= fortresspieces$fortresspieceweight.weight;
                    if (k < 0) {
                        if (!fortresspieces$fortresspieceweight.isValid(generationDepth)
                            || fortresspieces$fortresspieceweight == startPiece.previous && !fortresspieces$fortresspieceweight.isConnectorPiece) {
                            break;
                        }

                        FortressPieces.FortressPiece fortresspieces$fortresspiece = FortressPieces.createPiece(
                            fortresspieces$fortresspieceweight, pieces, random, x, y, z, facing, generationDepth
                        );
                        if (fortresspieces$fortresspiece != null) {
                            fortresspieces$fortresspieceweight.amountGenerated++;
                            startPiece.previous = fortresspieces$fortresspieceweight;
                            if (!fortresspieces$fortresspieceweight.isValid()) {
                                weights.remove(fortresspieces$fortresspieceweight);
                            }

                            return fortresspieces$fortresspiece;
                        }
                    }
                }
            }

            return FortressPieces.BridgeEnd.of(pieces, random, x, y, z, facing, generationDepth);
        }

        private StructurePiece generateNextPiece(
            FortressPieces.Start startPiece,
            List<StructurePiece> pieces,
            Random random,
            int x,
            int y,
            int z,
            Direction facing,
            int generationDepth,
            boolean pickCastlePiece
        ) {
            if (Math.abs(x - startPiece.getBounds().minX) <= 112 && Math.abs(z - startPiece.getBounds().minZ) <= 112) {
                List<FortressPieces.FortressPieceWeight> list = startPiece.bridgePieces;
                if (pickCastlePiece) {
                    list = startPiece.castlePieces;
                }

                StructurePiece structurepiece = this.generateNextPiece(startPiece, list, pieces, random, x, y, z, facing, generationDepth + 1);
                if (structurepiece != null) {
                    pieces.add(structurepiece);
                    startPiece.children.add(structurepiece);
                }

                return structurepiece;
            } else {
                return FortressPieces.BridgeEnd.of(pieces, random, x, y, z, facing, generationDepth);
            }
        }

        protected StructurePiece generatePieceForward(
            FortressPieces.Start startPiece, List<StructurePiece> pieces, Random random, int offset, int yOffset, boolean pickCastlePiece
        ) {
            if (this.facing != null) {
                switch (this.facing) {
                    case NORTH:
                        return this.generateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX + offset,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ - 1,
                            this.facing,
                            this.getGenerationDepth(),
                            pickCastlePiece
                        );
                    case SOUTH:
                        return this.generateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX + offset,
                            this.bounds.minY + yOffset,
                            this.bounds.maxZ + 1,
                            this.facing,
                            this.getGenerationDepth(),
                            pickCastlePiece
                        );
                    case WEST:
                        return this.generateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX - 1,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ + offset,
                            this.facing,
                            this.getGenerationDepth(),
                            pickCastlePiece
                        );
                    case EAST:
                        return this.generateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.maxX + 1,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ + offset,
                            this.facing,
                            this.getGenerationDepth(),
                            pickCastlePiece
                        );
                }
            }

            return null;
        }

        protected StructurePiece generatePieceLeft(
            FortressPieces.Start startPiece, List<StructurePiece> pieces, Random random, int yOffset, int offset, boolean pickCastlePiece
        ) {
            if (this.facing != null) {
                switch (this.facing) {
                    case NORTH:
                        return this.generateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX - 1,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ + offset,
                            Direction.WEST,
                            this.getGenerationDepth(),
                            pickCastlePiece
                        );
                    case SOUTH:
                        return this.generateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX - 1,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ + offset,
                            Direction.WEST,
                            this.getGenerationDepth(),
                            pickCastlePiece
                        );
                    case WEST:
                        return this.generateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX + offset,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ - 1,
                            Direction.NORTH,
                            this.getGenerationDepth(),
                            pickCastlePiece
                        );
                    case EAST:
                        return this.generateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX + offset,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ - 1,
                            Direction.NORTH,
                            this.getGenerationDepth(),
                            pickCastlePiece
                        );
                }
            }

            return null;
        }

        protected StructurePiece generatePieceRight(
            FortressPieces.Start startPiece, List<StructurePiece> pieces, Random random, int yOffset, int offset, boolean pickCastlePiece
        ) {
            if (this.facing != null) {
                switch (this.facing) {
                    case NORTH:
                        return this.generateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.maxX + 1,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ + offset,
                            Direction.EAST,
                            this.getGenerationDepth(),
                            pickCastlePiece
                        );
                    case SOUTH:
                        return this.generateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.maxX + 1,
                            this.bounds.minY + yOffset,
                            this.bounds.minZ + offset,
                            Direction.EAST,
                            this.getGenerationDepth(),
                            pickCastlePiece
                        );
                    case WEST:
                        return this.generateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX + offset,
                            this.bounds.minY + yOffset,
                            this.bounds.maxZ + 1,
                            Direction.SOUTH,
                            this.getGenerationDepth(),
                            pickCastlePiece
                        );
                    case EAST:
                        return this.generateNextPiece(
                            startPiece,
                            pieces,
                            random,
                            this.bounds.minX + offset,
                            this.bounds.minY + yOffset,
                            this.bounds.maxZ + 1,
                            Direction.SOUTH,
                            this.getGenerationDepth(),
                            pickCastlePiece
                        );
                }
            }

            return null;
        }

        protected static boolean isValidBounds(StructureBox bounds) {
            return bounds != null && bounds.minY > 10;
        }
    }

    static class FortressPieceWeight {
        public Class<? extends FortressPieces.FortressPiece> type;
        public final int weight;
        public int amountGenerated;
        public int maxAmount;
        public boolean isConnectorPiece;

        public FortressPieceWeight(Class<? extends FortressPieces.FortressPiece> type, int weight, int maxAmount, boolean isConnectorPiece) {
            this.type = type;
            this.weight = weight;
            this.maxAmount = maxAmount;
            this.isConnectorPiece = isConnectorPiece;
        }

        public FortressPieceWeight(Class<? extends FortressPieces.FortressPiece> type, int weight, int maxAmount) {
            this(type, weight, maxAmount, false);
        }

        public boolean isValid(int generationDepth) {
            return this.maxAmount == 0 || this.amountGenerated < this.maxAmount;
        }

        public boolean isValid() {
            return this.maxAmount == 0 || this.amountGenerated < this.maxAmount;
        }
    }

    public static class LargeCrossing extends FortressPieces.FortressPiece {
        public LargeCrossing() {
        }

        public LargeCrossing(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressPieces.Start)start, pieces, random, 2, 0, false);
            this.generatePieceLeft((FortressPieces.Start)start, pieces, random, 0, 2, false);
            this.generatePieceRight((FortressPieces.Start)start, pieces, random, 0, 2, false);
        }

        public static FortressPieces.LargeCrossing of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -2, 0, 0, 7, 9, 7, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new FortressPieces.LargeCrossing(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(world, bounds, 0, 0, 0, 6, 1, 6, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 6, 7, 6, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 1, 6, 0, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 6, 1, 6, 6, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 2, 0, 6, 6, 0, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 2, 6, 6, 6, 6, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 0, 6, 1, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 5, 0, 6, 6, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 2, 0, 6, 6, 1, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 2, 5, 6, 6, 6, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 6, 0, 4, 6, 0, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 5, 0, 4, 5, 0, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 6, 6, 4, 6, 6, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 5, 6, 4, 5, 6, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 6, 2, 0, 6, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 5, 2, 0, 5, 4, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 6, 2, 6, 6, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 6, 5, 2, 6, 5, 4, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);

            for (int i = 0; i <= 6; i++) {
                for (int j = 0; j <= 6; j++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), i, -1, j, bounds);
                }
            }

            return true;
        }
    }

    public static class NetherwartFarm extends FortressPieces.FortressPiece {
        public NetherwartFarm() {
        }

        public NetherwartFarm(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressPieces.Start)start, pieces, random, 5, 3, true);
            this.generatePieceForward((FortressPieces.Start)start, pieces, random, 5, 11, true);
        }

        public static FortressPieces.NetherwartFarm of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -5, -3, 0, 13, 14, 13, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new FortressPieces.NetherwartFarm(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(world, bounds, 0, 3, 0, 12, 4, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 5, 0, 12, 13, 12, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 5, 0, 1, 12, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 11, 5, 0, 12, 12, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 5, 11, 4, 12, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 5, 11, 10, 12, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 9, 11, 7, 12, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 5, 0, 4, 12, 1, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 5, 0, 10, 12, 1, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 9, 0, 7, 12, 1, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 11, 2, 10, 12, 10, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);

            for (int i = 1; i <= 11; i += 2) {
                this.fillWithOutline(
                    world, bounds, i, 10, 0, i, 11, 0, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false
                );
                this.fillWithOutline(
                    world, bounds, i, 10, 12, i, 11, 12, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false
                );
                this.fillWithOutline(
                    world, bounds, 0, 10, i, 0, 11, i, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false
                );
                this.fillWithOutline(
                    world, bounds, 12, 10, i, 12, 11, i, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false
                );
                this.setBlockState(world, Blocks.NETHER_BRICKS.defaultState(), i, 13, 0, bounds);
                this.setBlockState(world, Blocks.NETHER_BRICKS.defaultState(), i, 13, 12, bounds);
                this.setBlockState(world, Blocks.NETHER_BRICKS.defaultState(), 0, 13, i, bounds);
                this.setBlockState(world, Blocks.NETHER_BRICKS.defaultState(), 12, 13, i, bounds);
                this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), i + 1, 13, 0, bounds);
                this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), i + 1, 13, 12, bounds);
                this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), 0, 13, i + 1, bounds);
                this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), 12, 13, i + 1, bounds);
            }

            this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), 0, 13, 0, bounds);
            this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), 0, 13, 12, bounds);
            this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), 0, 13, 0, bounds);
            this.setBlockState(world, Blocks.NETHER_BRICK_FENCE.defaultState(), 12, 13, 0, bounds);

            for (int j1 = 3; j1 <= 9; j1 += 2) {
                this.fillWithOutline(
                    world, bounds, 1, 7, j1, 1, 8, j1, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false
                );
                this.fillWithOutline(
                    world, bounds, 11, 7, j1, 11, 8, j1, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false
                );
            }

            int k1 = this.postProcessBlockMetadata(Blocks.NETHER_BRICK_STAIRS, 3);

            for (int j = 0; j <= 6; j++) {
                int k = j + 4;

                for (int l = 5; l <= 7; l++) {
                    this.setBlockState(world, Blocks.NETHER_BRICK_STAIRS.getStateFromMetadata(k1), l, 5 + j, k, bounds);
                }

                if (k >= 5 && k <= 8) {
                    this.fillWithOutline(world, bounds, 5, 5, k, 7, j + 4, k, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
                } else if (k >= 9 && k <= 10) {
                    this.fillWithOutline(world, bounds, 5, 8, k, 7, j + 4, k, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
                }

                if (j >= 1) {
                    this.fillWithOutline(world, bounds, 5, 6 + j, k, 7, 9 + j, k, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
                }
            }

            for (int l1 = 5; l1 <= 7; l1++) {
                this.setBlockState(world, Blocks.NETHER_BRICK_STAIRS.getStateFromMetadata(k1), l1, 12, 11, bounds);
            }

            this.fillWithOutline(world, bounds, 5, 6, 7, 5, 7, 7, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 7, 6, 7, 7, 7, 7, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 13, 12, 7, 13, 12, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 5, 2, 3, 5, 3, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 5, 9, 3, 5, 10, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 2, 5, 4, 2, 5, 8, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 9, 5, 2, 10, 5, 3, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 9, 5, 9, 10, 5, 10, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 10, 5, 4, 10, 5, 8, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            int i2 = this.postProcessBlockMetadata(Blocks.NETHER_BRICK_STAIRS, 0);
            int j2 = this.postProcessBlockMetadata(Blocks.NETHER_BRICK_STAIRS, 1);
            this.setBlockState(world, Blocks.NETHER_BRICK_STAIRS.getStateFromMetadata(j2), 4, 5, 2, bounds);
            this.setBlockState(world, Blocks.NETHER_BRICK_STAIRS.getStateFromMetadata(j2), 4, 5, 3, bounds);
            this.setBlockState(world, Blocks.NETHER_BRICK_STAIRS.getStateFromMetadata(j2), 4, 5, 9, bounds);
            this.setBlockState(world, Blocks.NETHER_BRICK_STAIRS.getStateFromMetadata(j2), 4, 5, 10, bounds);
            this.setBlockState(world, Blocks.NETHER_BRICK_STAIRS.getStateFromMetadata(i2), 8, 5, 2, bounds);
            this.setBlockState(world, Blocks.NETHER_BRICK_STAIRS.getStateFromMetadata(i2), 8, 5, 3, bounds);
            this.setBlockState(world, Blocks.NETHER_BRICK_STAIRS.getStateFromMetadata(i2), 8, 5, 9, bounds);
            this.setBlockState(world, Blocks.NETHER_BRICK_STAIRS.getStateFromMetadata(i2), 8, 5, 10, bounds);
            this.fillWithOutline(world, bounds, 3, 4, 4, 4, 4, 8, Blocks.SOUL_SAND.defaultState(), Blocks.SOUL_SAND.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 4, 4, 9, 4, 8, Blocks.SOUL_SAND.defaultState(), Blocks.SOUL_SAND.defaultState(), false);
            this.fillWithOutline(world, bounds, 3, 5, 4, 4, 5, 8, Blocks.NETHER_WART.defaultState(), Blocks.NETHER_WART.defaultState(), false);
            this.fillWithOutline(world, bounds, 8, 5, 4, 9, 5, 8, Blocks.NETHER_WART.defaultState(), Blocks.NETHER_WART.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 2, 0, 8, 2, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 4, 12, 2, 8, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 0, 0, 8, 1, 3, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 0, 9, 8, 1, 12, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 0, 4, 3, 1, 8, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 9, 0, 4, 12, 1, 8, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);

            for (int k2 = 4; k2 <= 8; k2++) {
                for (int i1 = 0; i1 <= 2; i1++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), k2, -1, i1, bounds);
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), k2, -1, 12 - i1, bounds);
                }
            }

            for (int l2 = 0; l2 <= 2; l2++) {
                for (int i3 = 4; i3 <= 8; i3++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), l2, -1, i3, bounds);
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), 12 - l2, -1, i3, bounds);
                }
            }

            return true;
        }
    }

    public static class SmallCorridor extends FortressPieces.FortressPiece {
        public SmallCorridor() {
        }

        public SmallCorridor(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressPieces.Start)start, pieces, random, 1, 0, true);
        }

        public static FortressPieces.SmallCorridor of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -1, 0, 0, 5, 7, 5, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new FortressPieces.SmallCorridor(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(world, bounds, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 0, 5, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 2, 0, 4, 5, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 3, 1, 0, 4, 1, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 3, 3, 0, 4, 3, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 3, 1, 4, 4, 1, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 3, 3, 4, 4, 3, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);

            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 4; j++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), i, -1, j, bounds);
                }
            }

            return true;
        }
    }

    public static class SmallCorridorCrossing extends FortressPieces.FortressPiece {
        public SmallCorridorCrossing() {
        }

        public SmallCorridorCrossing(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressPieces.Start)start, pieces, random, 1, 0, true);
            this.generatePieceLeft((FortressPieces.Start)start, pieces, random, 0, 1, true);
            this.generatePieceRight((FortressPieces.Start)start, pieces, random, 0, 1, true);
        }

        public static FortressPieces.SmallCorridorCrossing of(
            List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -1, 0, 0, 5, 7, 5, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new FortressPieces.SmallCorridorCrossing(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(world, bounds, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 0, 5, 0, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 2, 0, 4, 5, 0, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 4, 0, 5, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 2, 4, 4, 5, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);

            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 4; j++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), i, -1, j, bounds);
                }
            }

            return true;
        }
    }

    public static class SmallCorridorLeftTurn extends FortressPieces.FortressPiece {
        private boolean hasChest;

        public SmallCorridorLeftTurn() {
        }

        public SmallCorridorLeftTurn(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.hasChest = random.nextInt(3) == 0;
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.hasChest = nbt.getBoolean("Chest");
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putBoolean("Chest", this.hasChest);
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceLeft((FortressPieces.Start)start, pieces, random, 0, 1, true);
        }

        public static FortressPieces.SmallCorridorLeftTurn of(
            List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -1, 0, 0, 5, 7, 5, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new FortressPieces.SmallCorridorLeftTurn(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(world, bounds, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 2, 0, 4, 5, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 3, 1, 4, 4, 1, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 3, 3, 4, 4, 3, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 0, 5, 0, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 4, 3, 5, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 3, 4, 1, 4, 4, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 3, 3, 4, 3, 4, 4, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            if (this.hasChest && bounds.contains(new BlockPos(this.transformX(3, 3), this.transformY(2), this.transformZ(3, 3)))) {
                this.hasChest = false;
                this.placeChestWithLoot(world, bounds, random, 3, 2, 3, LOOT_ENTRIES, 2 + random.nextInt(4));
            }

            this.fillWithOutline(world, bounds, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);

            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 4; j++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), i, -1, j, bounds);
                }
            }

            return true;
        }
    }

    public static class SmallCorridorRightTurn extends FortressPieces.FortressPiece {
        private boolean hasLootChest;

        public SmallCorridorRightTurn() {
        }

        public SmallCorridorRightTurn(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.hasLootChest = random.nextInt(3) == 0;
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.hasLootChest = nbt.getBoolean("Chest");
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putBoolean("Chest", this.hasLootChest);
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceRight((FortressPieces.Start)start, pieces, random, 0, 1, true);
        }

        public static FortressPieces.SmallCorridorRightTurn of(
            List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth
        ) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -1, 0, 0, 5, 7, 5, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new FortressPieces.SmallCorridorRightTurn(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(world, bounds, 0, 0, 0, 4, 1, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 4, 5, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 0, 5, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 3, 1, 0, 4, 1, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 3, 3, 0, 4, 3, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 2, 0, 4, 5, 0, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 2, 4, 4, 5, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 3, 4, 1, 4, 4, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 3, 3, 4, 3, 4, 4, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            if (this.hasLootChest && bounds.contains(new BlockPos(this.transformX(1, 3), this.transformY(2), this.transformZ(1, 3)))) {
                this.hasLootChest = false;
                this.placeChestWithLoot(world, bounds, random, 1, 2, 3, LOOT_ENTRIES, 2 + random.nextInt(4));
            }

            this.fillWithOutline(world, bounds, 0, 6, 0, 4, 6, 4, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);

            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 4; j++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), i, -1, j, bounds);
                }
            }

            return true;
        }
    }

    public static class StairsCorridor extends FortressPieces.FortressPiece {
        public StairsCorridor() {
        }

        public StairsCorridor(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressPieces.Start)start, pieces, random, 1, 0, true);
        }

        public static FortressPieces.StairsCorridor of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -1, -7, 0, 5, 14, 10, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new FortressPieces.StairsCorridor(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            int i = this.postProcessBlockMetadata(Blocks.NETHER_BRICK_STAIRS, 2);

            for (int j = 0; j <= 9; j++) {
                int k = Math.max(1, 7 - j);
                int l = Math.min(Math.max(k + 5, 14 - j), 13);
                int i1 = j;
                this.fillWithOutline(world, bounds, 0, 0, i1, 4, k, i1, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
                this.fillWithOutline(world, bounds, 1, k + 1, i1, 3, l - 1, i1, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
                if (j <= 6) {
                    this.setBlockState(world, Blocks.NETHER_BRICK_STAIRS.getStateFromMetadata(i), 1, k + 1, i1, bounds);
                    this.setBlockState(world, Blocks.NETHER_BRICK_STAIRS.getStateFromMetadata(i), 2, k + 1, i1, bounds);
                    this.setBlockState(world, Blocks.NETHER_BRICK_STAIRS.getStateFromMetadata(i), 3, k + 1, i1, bounds);
                }

                this.fillWithOutline(world, bounds, 0, l, i1, 4, l, i1, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
                this.fillWithOutline(world, bounds, 0, k + 1, i1, 0, l - 1, i1, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
                this.fillWithOutline(world, bounds, 4, k + 1, i1, 4, l - 1, i1, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
                if ((j & 1) == 0) {
                    this.fillWithOutline(
                        world, bounds, 0, k + 2, i1, 0, k + 3, i1, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false
                    );
                    this.fillWithOutline(
                        world, bounds, 4, k + 2, i1, 4, k + 3, i1, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false
                    );
                }

                for (int j1 = 0; j1 <= 4; j1++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), j1, -1, i1, bounds);
                }
            }

            return true;
        }
    }

    public static class Start extends FortressPieces.BridgeCrossing {
        public FortressPieces.FortressPieceWeight previous;
        public List<FortressPieces.FortressPieceWeight> bridgePieces;
        public List<FortressPieces.FortressPieceWeight> castlePieces;
        public List<StructurePiece> children = Lists.newArrayList();

        public Start() {
        }

        public Start(Random random, int i, int j) {
            super(random, i, j);
            this.bridgePieces = Lists.newArrayList();

            for (FortressPieces.FortressPieceWeight fortresspieces$fortresspieceweight : FortressPieces.BRIDGE_PIECE_WEIGHTS) {
                fortresspieces$fortresspieceweight.amountGenerated = 0;
                this.bridgePieces.add(fortresspieces$fortresspieceweight);
            }

            this.castlePieces = Lists.newArrayList();

            for (FortressPieces.FortressPieceWeight fortresspieces$fortresspieceweight1 : FortressPieces.CASTLE_PIECE_WEIGHTS) {
                fortresspieces$fortresspieceweight1.amountGenerated = 0;
                this.castlePieces.add(fortresspieces$fortresspieceweight1);
            }
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
        }
    }

    public static class StraightBridge extends FortressPieces.FortressPiece {
        public StraightBridge() {
        }

        public StraightBridge(int generationDepth, Random random, StructureBox bounds, Direction facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressPieces.Start)start, pieces, random, 1, 3, false);
        }

        public static FortressPieces.StraightBridge of(List<StructurePiece> pieces, Random random, int x, int y, int z, Direction facing, int generationDepth) {
            StructureBox structurebox = StructureBox.orient(x, y, z, -1, -3, 0, 5, 10, 19, facing);
            return isValidBounds(structurebox) && StructurePiece.getIntersectingPiece(pieces, structurebox) == null
                ? new FortressPieces.StraightBridge(generationDepth, random, structurebox, facing)
                : null;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(world, bounds, 0, 3, 0, 4, 4, 18, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 5, 0, 3, 7, 18, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 5, 0, 0, 5, 18, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 5, 0, 4, 5, 18, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 4, 2, 5, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 2, 13, 4, 2, 18, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 0, 0, 4, 1, 3, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 0, 15, 4, 1, 18, Blocks.NETHER_BRICKS.defaultState(), Blocks.NETHER_BRICKS.defaultState(), false);

            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 2; j++) {
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), i, -1, j, bounds);
                    this.fillColumnDown(world, Blocks.NETHER_BRICKS.defaultState(), i, -1, 18 - j, bounds);
                }
            }

            this.fillWithOutline(world, bounds, 0, 1, 1, 0, 4, 1, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 3, 4, 0, 4, 4, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 3, 14, 0, 4, 14, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 0, 1, 17, 0, 4, 17, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 1, 1, 4, 4, 1, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 3, 4, 4, 4, 4, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 3, 14, 4, 4, 14, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 1, 17, 4, 4, 17, Blocks.NETHER_BRICK_FENCE.defaultState(), Blocks.NETHER_BRICK_FENCE.defaultState(), false);
            return true;
        }
    }
}
