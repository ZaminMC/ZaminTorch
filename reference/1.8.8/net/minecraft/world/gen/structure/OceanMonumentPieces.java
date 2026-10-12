package net.minecraft.world.gen.structure;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.PrismarineBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.mob.monster.GuardianEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class OceanMonumentPieces {
    public static void register() {
        StructureRegistry.registerPiece(OceanMonumentPieces.OceanMonument.class, "OMB");
        StructureRegistry.registerPiece(OceanMonumentPieces.CoreRoom.class, "OMCR");
        StructureRegistry.registerPiece(OceanMonumentPieces.DoubleWidthXRoom.class, "OMDXR");
        StructureRegistry.registerPiece(OceanMonumentPieces.DoubleHeightWidthXRoom.class, "OMDXYR");
        StructureRegistry.registerPiece(OceanMonumentPieces.DoubleHeightRoom.class, "OMDYR");
        StructureRegistry.registerPiece(OceanMonumentPieces.DoubleHeightWidthZRoom.class, "OMDYZR");
        StructureRegistry.registerPiece(OceanMonumentPieces.DoubleWidthZRoom.class, "OMDZR");
        StructureRegistry.registerPiece(OceanMonumentPieces.MonumentEntry.class, "OMEntry");
        StructureRegistry.registerPiece(OceanMonumentPieces.Penthouse.class, "OMPenthouse");
        StructureRegistry.registerPiece(OceanMonumentPieces.SimpleRoom.class, "OMSimple");
        StructureRegistry.registerPiece(OceanMonumentPieces.SimpleTopRoom.class, "OMSimpleT");
    }

    public static class CoreRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public CoreRoom() {
        }

        public CoreRoom(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 2, 2, 2);
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.replaceFiller(world, bounds, 1, 8, 0, 14, 8, 14, BASE);
            int i = 7;
            BlockState blockstate = BASE_LIGHT;
            this.fillWithOutline(world, bounds, 0, i, 0, 0, i, 15, blockstate, blockstate, false);
            this.fillWithOutline(world, bounds, 15, i, 0, 15, i, 15, blockstate, blockstate, false);
            this.fillWithOutline(world, bounds, 1, i, 0, 15, i, 0, blockstate, blockstate, false);
            this.fillWithOutline(world, bounds, 1, i, 15, 14, i, 15, blockstate, blockstate, false);

            for (int k = 1; k <= 6; k++) {
                blockstate = BASE_LIGHT;
                if (k == 2 || k == 6) {
                    blockstate = BASE;
                }

                for (int j = 0; j <= 15; j += 15) {
                    this.fillWithOutline(world, bounds, j, k, 0, j, k, 1, blockstate, blockstate, false);
                    this.fillWithOutline(world, bounds, j, k, 6, j, k, 9, blockstate, blockstate, false);
                    this.fillWithOutline(world, bounds, j, k, 14, j, k, 15, blockstate, blockstate, false);
                }

                this.fillWithOutline(world, bounds, 1, k, 0, 1, k, 0, blockstate, blockstate, false);
                this.fillWithOutline(world, bounds, 6, k, 0, 9, k, 0, blockstate, blockstate, false);
                this.fillWithOutline(world, bounds, 14, k, 0, 14, k, 0, blockstate, blockstate, false);
                this.fillWithOutline(world, bounds, 1, k, 15, 14, k, 15, blockstate, blockstate, false);
            }

            this.fillWithOutline(world, bounds, 6, 3, 6, 9, 6, 9, BASE_DARK, BASE_DARK, false);
            this.fillWithOutline(world, bounds, 7, 4, 7, 8, 5, 8, Blocks.GOLD_BLOCK.defaultState(), Blocks.GOLD_BLOCK.defaultState(), false);

            for (int l = 3; l <= 6; l += 3) {
                for (int i1 = 6; i1 <= 9; i1 += 3) {
                    this.setBlockState(world, LIGHT_SOURCE, i1, l, 6, bounds);
                    this.setBlockState(world, LIGHT_SOURCE, i1, l, 9, bounds);
                }
            }

            this.fillWithOutline(world, bounds, 5, 1, 6, 5, 2, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 1, 9, 5, 2, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 10, 1, 6, 10, 2, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 10, 1, 9, 10, 2, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 6, 1, 5, 6, 2, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 9, 1, 5, 9, 2, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 6, 1, 10, 6, 2, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 9, 1, 10, 9, 2, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 2, 5, 5, 6, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 2, 10, 5, 6, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 10, 2, 5, 10, 6, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 10, 2, 10, 10, 6, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 7, 1, 5, 7, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 10, 7, 1, 10, 7, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 7, 9, 5, 7, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 10, 7, 9, 10, 7, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 7, 5, 6, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 7, 10, 6, 7, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 9, 7, 5, 14, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 9, 7, 10, 14, 7, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 2, 1, 2, 2, 1, 3, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 3, 1, 2, 3, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 13, 1, 2, 13, 1, 3, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 12, 1, 2, 12, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 2, 1, 12, 2, 1, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 3, 1, 13, 3, 1, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 13, 1, 12, 13, 1, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 12, 1, 13, 12, 1, 13, BASE_LIGHT, BASE_LIGHT, false);
            return true;
        }
    }

    public static class DoubleHeightRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public DoubleHeightRoom() {
        }

        public DoubleHeightRoom(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 1, 2, 1);
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.pieceDefinition.index / 25 > 0) {
                this.generateFloor(world, bounds, 0, 0, this.pieceDefinition.hasEntrance[Direction.DOWN.getId()]);
            }

            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition = this.pieceDefinition.connections[Direction.UP.getId()];
            if (oceanmonumentpieces$piecedefinition.connections[Direction.UP.getId()] == null) {
                this.replaceFiller(world, bounds, 1, 8, 1, 6, 8, 6, BASE);
            }

            this.fillWithOutline(world, bounds, 0, 4, 0, 0, 4, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 7, 4, 0, 7, 4, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 4, 0, 6, 4, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 4, 7, 6, 4, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 2, 4, 1, 2, 4, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 4, 2, 1, 4, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 4, 1, 5, 4, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 6, 4, 2, 6, 4, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 2, 4, 5, 2, 4, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 4, 5, 1, 4, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 4, 5, 5, 4, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 6, 4, 5, 6, 4, 5, BASE_LIGHT, BASE_LIGHT, false);
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition1 = this.pieceDefinition;

            for (int i = 1; i <= 5; i += 4) {
                int j = 0;
                if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.SOUTH.getId()]) {
                    this.fillWithOutline(world, bounds, 2, i, j, 2, i + 2, j, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 5, i, j, 5, i + 2, j, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 3, i + 2, j, 4, i + 2, j, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.fillWithOutline(world, bounds, 0, i, j, 7, i + 2, j, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 0, i + 1, j, 7, i + 1, j, BASE, BASE, false);
                }

                int b0 = 7;
                if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.NORTH.getId()]) {
                    this.fillWithOutline(world, bounds, 2, i, b0, 2, i + 2, b0, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 5, i, b0, 5, i + 2, b0, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 3, i + 2, b0, 4, i + 2, b0, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.fillWithOutline(world, bounds, 0, i, b0, 7, i + 2, b0, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 0, i + 1, b0, 7, i + 1, b0, BASE, BASE, false);
                }

                int k = 0;
                if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.WEST.getId()]) {
                    this.fillWithOutline(world, bounds, k, i, 2, k, i + 2, 2, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, k, i, 5, k, i + 2, 5, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, k, i + 2, 3, k, i + 2, 4, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.fillWithOutline(world, bounds, k, i, 0, k, i + 2, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, k, i + 1, 0, k, i + 1, 7, BASE, BASE, false);
                }

                int b1 = 7;
                if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.EAST.getId()]) {
                    this.fillWithOutline(world, bounds, b1, i, 2, b1, i + 2, 2, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, b1, i, 5, b1, i + 2, 5, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, b1, i + 2, 3, b1, i + 2, 4, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.fillWithOutline(world, bounds, b1, i, 0, b1, i + 2, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, b1, i + 1, 0, b1, i + 1, 7, BASE, BASE, false);
                }

                oceanmonumentpieces$piecedefinition1 = oceanmonumentpieces$piecedefinition;
            }

            return true;
        }
    }

    static class DoubleHeightRoomFitter implements OceanMonumentPieces.RoomFitter {
        private DoubleHeightRoomFitter() {
        }

        @Override
        public boolean fits(OceanMonumentPieces.PieceDefinition pieceDefinition) {
            return pieceDefinition.hasEntrance[Direction.UP.getId()] && !pieceDefinition.connections[Direction.UP.getId()].claimed;
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition = pieceDefinition;
            oceanmonumentpieces$piecedefinition.claimed = true;
            oceanmonumentpieces$piecedefinition.connections[Direction.UP.getId()].claimed = true;
            return new OceanMonumentPieces.DoubleHeightRoom(facing, oceanmonumentpieces$piecedefinition, random);
        }
    }

    public static class DoubleHeightWidthXRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public DoubleHeightWidthXRoom() {
        }

        public DoubleHeightWidthXRoom(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 2, 2, 1);
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition = this.pieceDefinition.connections[Direction.EAST.getId()];
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition1 = this.pieceDefinition;
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition2 = oceanmonumentpieces$piecedefinition1.connections[Direction.UP.getId()];
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition3 = oceanmonumentpieces$piecedefinition.connections[Direction.UP.getId()];
            if (this.pieceDefinition.index / 25 > 0) {
                this.generateFloor(world, bounds, 8, 0, oceanmonumentpieces$piecedefinition.hasEntrance[Direction.DOWN.getId()]);
                this.generateFloor(world, bounds, 0, 0, oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.DOWN.getId()]);
            }

            if (oceanmonumentpieces$piecedefinition2.connections[Direction.UP.getId()] == null) {
                this.replaceFiller(world, bounds, 1, 8, 1, 7, 8, 6, BASE);
            }

            if (oceanmonumentpieces$piecedefinition3.connections[Direction.UP.getId()] == null) {
                this.replaceFiller(world, bounds, 8, 8, 1, 14, 8, 6, BASE);
            }

            for (int i = 1; i <= 7; i++) {
                BlockState blockstate = BASE_LIGHT;
                if (i == 2 || i == 6) {
                    blockstate = BASE;
                }

                this.fillWithOutline(world, bounds, 0, i, 0, 0, i, 7, blockstate, blockstate, false);
                this.fillWithOutline(world, bounds, 15, i, 0, 15, i, 7, blockstate, blockstate, false);
                this.fillWithOutline(world, bounds, 1, i, 0, 15, i, 0, blockstate, blockstate, false);
                this.fillWithOutline(world, bounds, 1, i, 7, 14, i, 7, blockstate, blockstate, false);
            }

            this.fillWithOutline(world, bounds, 2, 1, 3, 2, 7, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 3, 1, 2, 4, 7, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 3, 1, 5, 4, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 13, 1, 3, 13, 7, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 11, 1, 2, 12, 7, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 11, 1, 5, 12, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 1, 3, 5, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 10, 1, 3, 10, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 7, 2, 10, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 5, 2, 5, 7, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 10, 5, 2, 10, 7, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 5, 5, 5, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 10, 5, 5, 10, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.setBlockState(world, BASE_LIGHT, 6, 6, 2, bounds);
            this.setBlockState(world, BASE_LIGHT, 9, 6, 2, bounds);
            this.setBlockState(world, BASE_LIGHT, 6, 6, 5, bounds);
            this.setBlockState(world, BASE_LIGHT, 9, 6, 5, bounds);
            this.fillWithOutline(world, bounds, 5, 4, 3, 6, 4, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 9, 4, 3, 10, 4, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.setBlockState(world, LIGHT_SOURCE, 5, 4, 2, bounds);
            this.setBlockState(world, LIGHT_SOURCE, 5, 4, 5, bounds);
            this.setBlockState(world, LIGHT_SOURCE, 10, 4, 2, bounds);
            this.setBlockState(world, LIGHT_SOURCE, 10, 4, 5, bounds);
            if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.SOUTH.getId()]) {
                this.fillWater(world, bounds, 3, 1, 0, 4, 2, 0, false);
            }

            if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.NORTH.getId()]) {
                this.fillWater(world, bounds, 3, 1, 7, 4, 2, 7, false);
            }

            if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.WEST.getId()]) {
                this.fillWater(world, bounds, 0, 1, 3, 0, 2, 4, false);
            }

            if (oceanmonumentpieces$piecedefinition.hasEntrance[Direction.SOUTH.getId()]) {
                this.fillWater(world, bounds, 11, 1, 0, 12, 2, 0, false);
            }

            if (oceanmonumentpieces$piecedefinition.hasEntrance[Direction.NORTH.getId()]) {
                this.fillWater(world, bounds, 11, 1, 7, 12, 2, 7, false);
            }

            if (oceanmonumentpieces$piecedefinition.hasEntrance[Direction.EAST.getId()]) {
                this.fillWater(world, bounds, 15, 1, 3, 15, 2, 4, false);
            }

            if (oceanmonumentpieces$piecedefinition2.hasEntrance[Direction.SOUTH.getId()]) {
                this.fillWater(world, bounds, 3, 5, 0, 4, 6, 0, false);
            }

            if (oceanmonumentpieces$piecedefinition2.hasEntrance[Direction.NORTH.getId()]) {
                this.fillWater(world, bounds, 3, 5, 7, 4, 6, 7, false);
            }

            if (oceanmonumentpieces$piecedefinition2.hasEntrance[Direction.WEST.getId()]) {
                this.fillWater(world, bounds, 0, 5, 3, 0, 6, 4, false);
            }

            if (oceanmonumentpieces$piecedefinition3.hasEntrance[Direction.SOUTH.getId()]) {
                this.fillWater(world, bounds, 11, 5, 0, 12, 6, 0, false);
            }

            if (oceanmonumentpieces$piecedefinition3.hasEntrance[Direction.NORTH.getId()]) {
                this.fillWater(world, bounds, 11, 5, 7, 12, 6, 7, false);
            }

            if (oceanmonumentpieces$piecedefinition3.hasEntrance[Direction.EAST.getId()]) {
                this.fillWater(world, bounds, 15, 5, 3, 15, 6, 4, false);
            }

            return true;
        }
    }

    static class DoubleHeightWidthXRoomFitter implements OceanMonumentPieces.RoomFitter {
        private DoubleHeightWidthXRoomFitter() {
        }

        @Override
        public boolean fits(OceanMonumentPieces.PieceDefinition pieceDefinition) {
            if (pieceDefinition.hasEntrance[Direction.EAST.getId()]
                && !pieceDefinition.connections[Direction.EAST.getId()].claimed
                && pieceDefinition.hasEntrance[Direction.UP.getId()]
                && !pieceDefinition.connections[Direction.UP.getId()].claimed) {
                OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition = pieceDefinition.connections[Direction.EAST.getId()];
                return oceanmonumentpieces$piecedefinition.hasEntrance[Direction.UP.getId()]
                    && !oceanmonumentpieces$piecedefinition.connections[Direction.UP.getId()].claimed;
            } else {
                return false;
            }
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            pieceDefinition.claimed = true;
            pieceDefinition.connections[Direction.EAST.getId()].claimed = true;
            pieceDefinition.connections[Direction.UP.getId()].claimed = true;
            pieceDefinition.connections[Direction.EAST.getId()].connections[Direction.UP.getId()].claimed = true;
            return new OceanMonumentPieces.DoubleHeightWidthXRoom(facing, pieceDefinition, random);
        }
    }

    public static class DoubleHeightWidthZRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public DoubleHeightWidthZRoom() {
        }

        public DoubleHeightWidthZRoom(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 1, 2, 2);
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition = this.pieceDefinition.connections[Direction.NORTH.getId()];
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition1 = this.pieceDefinition;
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition2 = oceanmonumentpieces$piecedefinition.connections[Direction.UP.getId()];
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition3 = oceanmonumentpieces$piecedefinition1.connections[Direction.UP.getId()];
            if (this.pieceDefinition.index / 25 > 0) {
                this.generateFloor(world, bounds, 0, 8, oceanmonumentpieces$piecedefinition.hasEntrance[Direction.DOWN.getId()]);
                this.generateFloor(world, bounds, 0, 0, oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.DOWN.getId()]);
            }

            if (oceanmonumentpieces$piecedefinition3.connections[Direction.UP.getId()] == null) {
                this.replaceFiller(world, bounds, 1, 8, 1, 6, 8, 7, BASE);
            }

            if (oceanmonumentpieces$piecedefinition2.connections[Direction.UP.getId()] == null) {
                this.replaceFiller(world, bounds, 1, 8, 8, 6, 8, 14, BASE);
            }

            for (int i = 1; i <= 7; i++) {
                BlockState blockstate = BASE_LIGHT;
                if (i == 2 || i == 6) {
                    blockstate = BASE;
                }

                this.fillWithOutline(world, bounds, 0, i, 0, 0, i, 15, blockstate, blockstate, false);
                this.fillWithOutline(world, bounds, 7, i, 0, 7, i, 15, blockstate, blockstate, false);
                this.fillWithOutline(world, bounds, 1, i, 0, 6, i, 0, blockstate, blockstate, false);
                this.fillWithOutline(world, bounds, 1, i, 15, 6, i, 15, blockstate, blockstate, false);
            }

            for (int j = 1; j <= 7; j++) {
                BlockState blockstate1 = BASE_DARK;
                if (j == 2 || j == 6) {
                    blockstate1 = LIGHT_SOURCE;
                }

                this.fillWithOutline(world, bounds, 3, j, 7, 4, j, 8, blockstate1, blockstate1, false);
            }

            if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.SOUTH.getId()]) {
                this.fillWater(world, bounds, 3, 1, 0, 4, 2, 0, false);
            }

            if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.EAST.getId()]) {
                this.fillWater(world, bounds, 7, 1, 3, 7, 2, 4, false);
            }

            if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.WEST.getId()]) {
                this.fillWater(world, bounds, 0, 1, 3, 0, 2, 4, false);
            }

            if (oceanmonumentpieces$piecedefinition.hasEntrance[Direction.NORTH.getId()]) {
                this.fillWater(world, bounds, 3, 1, 15, 4, 2, 15, false);
            }

            if (oceanmonumentpieces$piecedefinition.hasEntrance[Direction.WEST.getId()]) {
                this.fillWater(world, bounds, 0, 1, 11, 0, 2, 12, false);
            }

            if (oceanmonumentpieces$piecedefinition.hasEntrance[Direction.EAST.getId()]) {
                this.fillWater(world, bounds, 7, 1, 11, 7, 2, 12, false);
            }

            if (oceanmonumentpieces$piecedefinition3.hasEntrance[Direction.SOUTH.getId()]) {
                this.fillWater(world, bounds, 3, 5, 0, 4, 6, 0, false);
            }

            if (oceanmonumentpieces$piecedefinition3.hasEntrance[Direction.EAST.getId()]) {
                this.fillWater(world, bounds, 7, 5, 3, 7, 6, 4, false);
                this.fillWithOutline(world, bounds, 5, 4, 2, 6, 4, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 6, 1, 2, 6, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 6, 1, 5, 6, 3, 5, BASE_LIGHT, BASE_LIGHT, false);
            }

            if (oceanmonumentpieces$piecedefinition3.hasEntrance[Direction.WEST.getId()]) {
                this.fillWater(world, bounds, 0, 5, 3, 0, 6, 4, false);
                this.fillWithOutline(world, bounds, 1, 4, 2, 2, 4, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 1, 1, 2, 1, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 1, 1, 5, 1, 3, 5, BASE_LIGHT, BASE_LIGHT, false);
            }

            if (oceanmonumentpieces$piecedefinition2.hasEntrance[Direction.NORTH.getId()]) {
                this.fillWater(world, bounds, 3, 5, 15, 4, 6, 15, false);
            }

            if (oceanmonumentpieces$piecedefinition2.hasEntrance[Direction.WEST.getId()]) {
                this.fillWater(world, bounds, 0, 5, 11, 0, 6, 12, false);
                this.fillWithOutline(world, bounds, 1, 4, 10, 2, 4, 13, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 1, 1, 10, 1, 3, 10, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 1, 1, 13, 1, 3, 13, BASE_LIGHT, BASE_LIGHT, false);
            }

            if (oceanmonumentpieces$piecedefinition2.hasEntrance[Direction.EAST.getId()]) {
                this.fillWater(world, bounds, 7, 5, 11, 7, 6, 12, false);
                this.fillWithOutline(world, bounds, 5, 4, 10, 6, 4, 13, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 6, 1, 10, 6, 3, 10, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 6, 1, 13, 6, 3, 13, BASE_LIGHT, BASE_LIGHT, false);
            }

            return true;
        }
    }

    static class DoubleHeightWidthZRoomFitter implements OceanMonumentPieces.RoomFitter {
        private DoubleHeightWidthZRoomFitter() {
        }

        @Override
        public boolean fits(OceanMonumentPieces.PieceDefinition pieceDefinition) {
            if (pieceDefinition.hasEntrance[Direction.NORTH.getId()]
                && !pieceDefinition.connections[Direction.NORTH.getId()].claimed
                && pieceDefinition.hasEntrance[Direction.UP.getId()]
                && !pieceDefinition.connections[Direction.UP.getId()].claimed) {
                OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition = pieceDefinition.connections[Direction.NORTH.getId()];
                return oceanmonumentpieces$piecedefinition.hasEntrance[Direction.UP.getId()]
                    && !oceanmonumentpieces$piecedefinition.connections[Direction.UP.getId()].claimed;
            } else {
                return false;
            }
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            pieceDefinition.claimed = true;
            pieceDefinition.connections[Direction.NORTH.getId()].claimed = true;
            pieceDefinition.connections[Direction.UP.getId()].claimed = true;
            pieceDefinition.connections[Direction.NORTH.getId()].connections[Direction.UP.getId()].claimed = true;
            return new OceanMonumentPieces.DoubleHeightWidthZRoom(facing, pieceDefinition, random);
        }
    }

    public static class DoubleWidthXRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public DoubleWidthXRoom() {
        }

        public DoubleWidthXRoom(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 2, 1, 1);
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition = this.pieceDefinition.connections[Direction.EAST.getId()];
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition1 = this.pieceDefinition;
            if (this.pieceDefinition.index / 25 > 0) {
                this.generateFloor(world, bounds, 8, 0, oceanmonumentpieces$piecedefinition.hasEntrance[Direction.DOWN.getId()]);
                this.generateFloor(world, bounds, 0, 0, oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.DOWN.getId()]);
            }

            if (oceanmonumentpieces$piecedefinition1.connections[Direction.UP.getId()] == null) {
                this.replaceFiller(world, bounds, 1, 4, 1, 7, 4, 6, BASE);
            }

            if (oceanmonumentpieces$piecedefinition.connections[Direction.UP.getId()] == null) {
                this.replaceFiller(world, bounds, 8, 4, 1, 14, 4, 6, BASE);
            }

            this.fillWithOutline(world, bounds, 0, 3, 0, 0, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 15, 3, 0, 15, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 3, 0, 15, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 3, 7, 14, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 0, 2, 7, BASE, BASE, false);
            this.fillWithOutline(world, bounds, 15, 2, 0, 15, 2, 7, BASE, BASE, false);
            this.fillWithOutline(world, bounds, 1, 2, 0, 15, 2, 0, BASE, BASE, false);
            this.fillWithOutline(world, bounds, 1, 2, 7, 14, 2, 7, BASE, BASE, false);
            this.fillWithOutline(world, bounds, 0, 1, 0, 0, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 15, 1, 0, 15, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 1, 0, 15, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 1, 7, 14, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 1, 0, 10, 1, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 6, 2, 0, 9, 2, 3, BASE, BASE, false);
            this.fillWithOutline(world, bounds, 5, 3, 0, 10, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.setBlockState(world, LIGHT_SOURCE, 6, 2, 3, bounds);
            this.setBlockState(world, LIGHT_SOURCE, 9, 2, 3, bounds);
            if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.SOUTH.getId()]) {
                this.fillWater(world, bounds, 3, 1, 0, 4, 2, 0, false);
            }

            if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.NORTH.getId()]) {
                this.fillWater(world, bounds, 3, 1, 7, 4, 2, 7, false);
            }

            if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.WEST.getId()]) {
                this.fillWater(world, bounds, 0, 1, 3, 0, 2, 4, false);
            }

            if (oceanmonumentpieces$piecedefinition.hasEntrance[Direction.SOUTH.getId()]) {
                this.fillWater(world, bounds, 11, 1, 0, 12, 2, 0, false);
            }

            if (oceanmonumentpieces$piecedefinition.hasEntrance[Direction.NORTH.getId()]) {
                this.fillWater(world, bounds, 11, 1, 7, 12, 2, 7, false);
            }

            if (oceanmonumentpieces$piecedefinition.hasEntrance[Direction.EAST.getId()]) {
                this.fillWater(world, bounds, 15, 1, 3, 15, 2, 4, false);
            }

            return true;
        }
    }

    static class DoubleWidthXRoomFitter implements OceanMonumentPieces.RoomFitter {
        private DoubleWidthXRoomFitter() {
        }

        @Override
        public boolean fits(OceanMonumentPieces.PieceDefinition pieceDefinition) {
            return pieceDefinition.hasEntrance[Direction.EAST.getId()] && !pieceDefinition.connections[Direction.EAST.getId()].claimed;
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition = pieceDefinition;
            oceanmonumentpieces$piecedefinition.claimed = true;
            oceanmonumentpieces$piecedefinition.connections[Direction.EAST.getId()].claimed = true;
            return new OceanMonumentPieces.DoubleWidthXRoom(facing, oceanmonumentpieces$piecedefinition, random);
        }
    }

    public static class DoubleWidthZRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public DoubleWidthZRoom() {
        }

        public DoubleWidthZRoom(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 1, 1, 2);
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition = this.pieceDefinition.connections[Direction.NORTH.getId()];
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition1 = this.pieceDefinition;
            if (this.pieceDefinition.index / 25 > 0) {
                this.generateFloor(world, bounds, 0, 8, oceanmonumentpieces$piecedefinition.hasEntrance[Direction.DOWN.getId()]);
                this.generateFloor(world, bounds, 0, 0, oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.DOWN.getId()]);
            }

            if (oceanmonumentpieces$piecedefinition1.connections[Direction.UP.getId()] == null) {
                this.replaceFiller(world, bounds, 1, 4, 1, 6, 4, 7, BASE);
            }

            if (oceanmonumentpieces$piecedefinition.connections[Direction.UP.getId()] == null) {
                this.replaceFiller(world, bounds, 1, 4, 8, 6, 4, 14, BASE);
            }

            this.fillWithOutline(world, bounds, 0, 3, 0, 0, 3, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 7, 3, 0, 7, 3, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 3, 0, 7, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 3, 15, 6, 3, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 0, 2, 15, BASE, BASE, false);
            this.fillWithOutline(world, bounds, 7, 2, 0, 7, 2, 15, BASE, BASE, false);
            this.fillWithOutline(world, bounds, 1, 2, 0, 7, 2, 0, BASE, BASE, false);
            this.fillWithOutline(world, bounds, 1, 2, 15, 6, 2, 15, BASE, BASE, false);
            this.fillWithOutline(world, bounds, 0, 1, 0, 0, 1, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 7, 1, 0, 7, 1, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 1, 0, 7, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 1, 15, 6, 1, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 1, 1, 1, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 6, 1, 1, 6, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 3, 1, 1, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 6, 3, 1, 6, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 1, 13, 1, 1, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 6, 1, 13, 6, 1, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 3, 13, 1, 3, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 6, 3, 13, 6, 3, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 2, 1, 6, 2, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 1, 6, 5, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 2, 1, 9, 2, 3, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 1, 9, 5, 3, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 3, 2, 6, 4, 2, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 3, 2, 9, 4, 2, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 2, 2, 7, 2, 2, 8, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 2, 7, 5, 2, 8, BASE_LIGHT, BASE_LIGHT, false);
            this.setBlockState(world, LIGHT_SOURCE, 2, 2, 5, bounds);
            this.setBlockState(world, LIGHT_SOURCE, 5, 2, 5, bounds);
            this.setBlockState(world, LIGHT_SOURCE, 2, 2, 10, bounds);
            this.setBlockState(world, LIGHT_SOURCE, 5, 2, 10, bounds);
            this.setBlockState(world, BASE_LIGHT, 2, 3, 5, bounds);
            this.setBlockState(world, BASE_LIGHT, 5, 3, 5, bounds);
            this.setBlockState(world, BASE_LIGHT, 2, 3, 10, bounds);
            this.setBlockState(world, BASE_LIGHT, 5, 3, 10, bounds);
            if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.SOUTH.getId()]) {
                this.fillWater(world, bounds, 3, 1, 0, 4, 2, 0, false);
            }

            if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.EAST.getId()]) {
                this.fillWater(world, bounds, 7, 1, 3, 7, 2, 4, false);
            }

            if (oceanmonumentpieces$piecedefinition1.hasEntrance[Direction.WEST.getId()]) {
                this.fillWater(world, bounds, 0, 1, 3, 0, 2, 4, false);
            }

            if (oceanmonumentpieces$piecedefinition.hasEntrance[Direction.NORTH.getId()]) {
                this.fillWater(world, bounds, 3, 1, 15, 4, 2, 15, false);
            }

            if (oceanmonumentpieces$piecedefinition.hasEntrance[Direction.WEST.getId()]) {
                this.fillWater(world, bounds, 0, 1, 11, 0, 2, 12, false);
            }

            if (oceanmonumentpieces$piecedefinition.hasEntrance[Direction.EAST.getId()]) {
                this.fillWater(world, bounds, 7, 1, 11, 7, 2, 12, false);
            }

            return true;
        }
    }

    static class DoubleWidthZRoomFitter implements OceanMonumentPieces.RoomFitter {
        private DoubleWidthZRoomFitter() {
        }

        @Override
        public boolean fits(OceanMonumentPieces.PieceDefinition pieceDefinition) {
            return pieceDefinition.hasEntrance[Direction.NORTH.getId()] && !pieceDefinition.connections[Direction.NORTH.getId()].claimed;
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition = pieceDefinition;
            if (!pieceDefinition.hasEntrance[Direction.NORTH.getId()] || pieceDefinition.connections[Direction.NORTH.getId()].claimed) {
                oceanmonumentpieces$piecedefinition = pieceDefinition.connections[Direction.SOUTH.getId()];
            }

            oceanmonumentpieces$piecedefinition.claimed = true;
            oceanmonumentpieces$piecedefinition.connections[Direction.NORTH.getId()].claimed = true;
            return new OceanMonumentPieces.DoubleWidthZRoom(facing, oceanmonumentpieces$piecedefinition, random);
        }
    }

    public static class MonumentEntry extends OceanMonumentPieces.OceanMonumentPiece {
        public MonumentEntry() {
        }

        public MonumentEntry(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition) {
            super(1, facing, pieceDefinition, 1, 1, 1);
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(world, bounds, 0, 3, 0, 2, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 3, 0, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 1, 2, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 6, 2, 0, 7, 2, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 0, 1, 0, 0, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 7, 1, 0, 7, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 0, 1, 7, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 1, 0, 2, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 5, 1, 0, 6, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            if (this.pieceDefinition.hasEntrance[Direction.NORTH.getId()]) {
                this.fillWater(world, bounds, 3, 1, 7, 4, 2, 7, false);
            }

            if (this.pieceDefinition.hasEntrance[Direction.WEST.getId()]) {
                this.fillWater(world, bounds, 0, 1, 3, 1, 2, 4, false);
            }

            if (this.pieceDefinition.hasEntrance[Direction.EAST.getId()]) {
                this.fillWater(world, bounds, 6, 1, 3, 7, 2, 4, false);
            }

            return true;
        }
    }

    public static class OceanMonument extends OceanMonumentPieces.OceanMonumentPiece {
        private OceanMonumentPieces.PieceDefinition source;
        private OceanMonumentPieces.PieceDefinition core;
        private List<OceanMonumentPieces.OceanMonumentPiece> children = Lists.newArrayList();

        public OceanMonument() {
        }

        public OceanMonument(Random random, int x, int z, Direction facing) {
            super(0);
            this.facing = facing;
            switch (this.facing) {
                case NORTH:
                case SOUTH:
                    this.bounds = new StructureBox(x, 39, z, x + 58 - 1, 61, z + 58 - 1);
                    break;
                default:
                    this.bounds = new StructureBox(x, 39, z, x + 58 - 1, 61, z + 58 - 1);
            }

            List<OceanMonumentPieces.PieceDefinition> list = this.createPieceLayout(random);
            this.source.claimed = true;
            this.children.add(new OceanMonumentPieces.MonumentEntry(this.facing, this.source));
            this.children.add(new OceanMonumentPieces.CoreRoom(this.facing, this.core, random));
            List<OceanMonumentPieces.RoomFitter> list1 = Lists.newArrayList();
            list1.add(new OceanMonumentPieces.DoubleHeightWidthXRoomFitter());
            list1.add(new OceanMonumentPieces.DoubleHeightWidthZRoomFitter());
            list1.add(new OceanMonumentPieces.DoubleWidthZRoomFitter());
            list1.add(new OceanMonumentPieces.DoubleWidthXRoomFitter());
            list1.add(new OceanMonumentPieces.DoubleHeightRoomFitter());
            list1.add(new OceanMonumentPieces.SimpleTopRoomFitter());
            list1.add(new OceanMonumentPieces.SimpleRoomFitter());

            for (OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition : list) {
                if (!oceanmonumentpieces$piecedefinition.claimed && !oceanmonumentpieces$piecedefinition.isSpecial()) {
                    for (OceanMonumentPieces.RoomFitter oceanmonumentpieces$roomfitter : list1) {
                        if (oceanmonumentpieces$roomfitter.fits(oceanmonumentpieces$piecedefinition)) {
                            this.children.add(oceanmonumentpieces$roomfitter.create(this.facing, oceanmonumentpieces$piecedefinition, random));
                            break;
                        }
                    }
                }
            }

            int j = this.bounds.minY;
            int k = this.transformX(9, 22);
            int l = this.transformZ(9, 22);

            for (OceanMonumentPieces.OceanMonumentPiece oceanmonumentpieces$oceanmonumentpiece : this.children) {
                oceanmonumentpieces$oceanmonumentpiece.getBounds().move(k, j, l);
            }

            StructureBox structurebox1 = StructureBox.of(
                this.transformX(1, 1), this.transformY(1), this.transformZ(1, 1), this.transformX(23, 21), this.transformY(8), this.transformZ(23, 21)
            );
            StructureBox structurebox2 = StructureBox.of(
                this.transformX(34, 1), this.transformY(1), this.transformZ(34, 1), this.transformX(56, 21), this.transformY(8), this.transformZ(56, 21)
            );
            StructureBox structurebox = StructureBox.of(
                this.transformX(22, 22), this.transformY(13), this.transformZ(22, 22), this.transformX(35, 35), this.transformY(17), this.transformZ(35, 35)
            );
            int i = random.nextInt();
            this.children.add(new OceanMonumentPieces.WingRoom(this.facing, structurebox1, i++));
            this.children.add(new OceanMonumentPieces.WingRoom(this.facing, structurebox2, i++));
            this.children.add(new OceanMonumentPieces.Penthouse(this.facing, structurebox));
        }

        private List<OceanMonumentPieces.PieceDefinition> createPieceLayout(Random random) {
            OceanMonumentPieces.PieceDefinition[] aoceanmonumentpieces$piecedefinition = new OceanMonumentPieces.PieceDefinition[75];

            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 4; j++) {
                    int k = 0;
                    int l = getPieceIndex(i, k, j);
                    aoceanmonumentpieces$piecedefinition[l] = new OceanMonumentPieces.PieceDefinition(l);
                }
            }

            for (int i2 = 0; i2 < 5; i2++) {
                for (int l2 = 0; l2 < 4; l2++) {
                    int k3 = 1;
                    int j4 = getPieceIndex(i2, k3, l2);
                    aoceanmonumentpieces$piecedefinition[j4] = new OceanMonumentPieces.PieceDefinition(j4);
                }
            }

            for (int j2 = 1; j2 < 4; j2++) {
                for (int i3 = 0; i3 < 2; i3++) {
                    int l3 = 2;
                    int k4 = getPieceIndex(j2, l3, i3);
                    aoceanmonumentpieces$piecedefinition[k4] = new OceanMonumentPieces.PieceDefinition(k4);
                }
            }

            this.source = aoceanmonumentpieces$piecedefinition[SOURCE_INDEX];

            for (int k2 = 0; k2 < 5; k2++) {
                for (int j3 = 0; j3 < 5; j3++) {
                    for (int i4 = 0; i4 < 3; i4++) {
                        int l4 = getPieceIndex(k2, i4, j3);
                        if (aoceanmonumentpieces$piecedefinition[l4] != null) {
                            for (Direction direction : Direction.values()) {
                                int i1 = k2 + direction.getOffsetX();
                                int j1 = i4 + direction.getOffsetY();
                                int k1 = j3 + direction.getOffsetZ();
                                if (i1 >= 0 && i1 < 5 && k1 >= 0 && k1 < 5 && j1 >= 0 && j1 < 3) {
                                    int l1 = getPieceIndex(i1, j1, k1);
                                    if (aoceanmonumentpieces$piecedefinition[l1] != null) {
                                        if (k1 != j3) {
                                            aoceanmonumentpieces$piecedefinition[l4]
                                                .updateConnection(direction.getOpposite(), aoceanmonumentpieces$piecedefinition[l1]);
                                        } else {
                                            aoceanmonumentpieces$piecedefinition[l4].updateConnection(direction, aoceanmonumentpieces$piecedefinition[l1]);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition;
            aoceanmonumentpieces$piecedefinition[TOP_CONNECT_INDEX]
                .updateConnection(Direction.UP, oceanmonumentpieces$piecedefinition = new OceanMonumentPieces.PieceDefinition(1003));
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition1;
            aoceanmonumentpieces$piecedefinition[LEFT_CONNECT_INDEX]
                .updateConnection(Direction.SOUTH, oceanmonumentpieces$piecedefinition1 = new OceanMonumentPieces.PieceDefinition(1001));
            OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition2;
            aoceanmonumentpieces$piecedefinition[RIGHT_CONNECT_INDEX]
                .updateConnection(Direction.SOUTH, oceanmonumentpieces$piecedefinition2 = new OceanMonumentPieces.PieceDefinition(1002));
            oceanmonumentpieces$piecedefinition.claimed = true;
            oceanmonumentpieces$piecedefinition1.claimed = true;
            oceanmonumentpieces$piecedefinition2.claimed = true;
            this.source.source = true;
            this.core = aoceanmonumentpieces$piecedefinition[getPieceIndex(random.nextInt(4), 0, 2)];
            this.core.claimed = true;
            this.core.connections[Direction.EAST.getId()].claimed = true;
            this.core.connections[Direction.NORTH.getId()].claimed = true;
            this.core.connections[Direction.EAST.getId()].connections[Direction.NORTH.getId()].claimed = true;
            this.core.connections[Direction.UP.getId()].claimed = true;
            this.core.connections[Direction.EAST.getId()].connections[Direction.UP.getId()].claimed = true;
            this.core.connections[Direction.NORTH.getId()].connections[Direction.UP.getId()].claimed = true;
            this.core.connections[Direction.EAST.getId()].connections[Direction.NORTH.getId()].connections[Direction.UP.getId()].claimed = true;
            List<OceanMonumentPieces.PieceDefinition> list = Lists.newArrayList();

            for (OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition4 : aoceanmonumentpieces$piecedefinition) {
                if (oceanmonumentpieces$piecedefinition4 != null) {
                    oceanmonumentpieces$piecedefinition4.updateEntrances();
                    list.add(oceanmonumentpieces$piecedefinition4);
                }
            }

            oceanmonumentpieces$piecedefinition.updateEntrances();
            Collections.shuffle(list, random);
            int i5 = 1;

            for (OceanMonumentPieces.PieceDefinition oceanmonumentpieces$piecedefinition3 : list) {
                int j5 = 0;
                int k5 = 0;

                while (j5 < 2 && k5 < 5) {
                    k5++;
                    int l5 = random.nextInt(6);
                    if (oceanmonumentpieces$piecedefinition3.hasEntrance[l5]) {
                        int i6 = Direction.byId(l5).getOpposite().getId();
                        oceanmonumentpieces$piecedefinition3.hasEntrance[l5] = false;
                        oceanmonumentpieces$piecedefinition3.connections[l5].hasEntrance[i6] = false;
                        if (oceanmonumentpieces$piecedefinition3.findSource(i5++) && oceanmonumentpieces$piecedefinition3.connections[l5].findSource(i5++)) {
                            j5++;
                        } else {
                            oceanmonumentpieces$piecedefinition3.hasEntrance[l5] = true;
                            oceanmonumentpieces$piecedefinition3.connections[l5].hasEntrance[i6] = true;
                        }
                    }
                }
            }

            list.add(oceanmonumentpieces$piecedefinition);
            list.add(oceanmonumentpieces$piecedefinition1);
            list.add(oceanmonumentpieces$piecedefinition2);
            return list;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            int i = Math.max(world.getSeaLevel(), 64) - this.bounds.minY;
            this.fillWater(world, bounds, 0, 0, 0, 58, i, 58, false);
            this.generateWing(false, 0, world, random, bounds);
            this.generateWing(true, 33, world, random, bounds);
            this.generateEntranceArch(world, random, bounds);
            this.generateEntranceWall(world, random, bounds);
            this.generateRoof(world, random, bounds);
            this.generateLowerWall(world, random, bounds);
            this.generateMiddleWall(world, random, bounds);
            this.generateUpperWall(world, random, bounds);

            for (int j = 0; j < 7; j++) {
                int k = 0;

                while (k < 7) {
                    if (k == 0 && j == 3) {
                        k = 6;
                    }

                    int l = j * 9;
                    int i1 = k * 9;

                    for (int j1 = 0; j1 < 4; j1++) {
                        for (int k1 = 0; k1 < 4; k1++) {
                            this.setBlockState(world, BASE_LIGHT, l + j1, 0, i1 + k1, bounds);
                            this.fillColumnDown(world, BASE_LIGHT, l + j1, -1, i1 + k1, bounds);
                        }
                    }

                    if (j != 0 && j != 6) {
                        k += 6;
                    } else {
                        k++;
                    }
                }
            }

            for (int l1 = 0; l1 < 5; l1++) {
                this.fillWater(world, bounds, -1 - l1, 0 + l1 * 2, -1 - l1, -1 - l1, 23, 58 + l1, false);
                this.fillWater(world, bounds, 58 + l1, 0 + l1 * 2, -1 - l1, 58 + l1, 23, 58 + l1, false);
                this.fillWater(world, bounds, 0 - l1, 0 + l1 * 2, -1 - l1, 57 + l1, 23, -1 - l1, false);
                this.fillWater(world, bounds, 0 - l1, 0 + l1 * 2, 58 + l1, 57 + l1, 23, 58 + l1, false);
            }

            for (OceanMonumentPieces.OceanMonumentPiece oceanmonumentpieces$oceanmonumentpiece : this.children) {
                if (oceanmonumentpieces$oceanmonumentpiece.getBounds().intersects(bounds)) {
                    oceanmonumentpieces$oceanmonumentpiece.postProcess(world, random, bounds);
                }
            }

            return true;
        }

        private void generateWing(boolean right, int x, World world, Random random, StructureBox bounds) {
            int i = 24;
            if (this.intersects(bounds, x, 0, x + 23, 20)) {
                this.fillWithOutline(world, bounds, x + 0, 0, 0, x + 24, 0, 20, BASE, BASE, false);
                this.fillWater(world, bounds, x + 0, 1, 0, x + 24, 10, 20, false);

                for (int j = 0; j < 4; j++) {
                    this.fillWithOutline(world, bounds, x + j, j + 1, j, x + j, j + 1, 20, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, x + j + 7, j + 5, j + 7, x + j + 7, j + 5, 20, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, x + 17 - j, j + 5, j + 7, x + 17 - j, j + 5, 20, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, x + 24 - j, j + 1, j, x + 24 - j, j + 1, 20, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, x + j + 1, j + 1, j, x + 23 - j, j + 1, j, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, x + j + 8, j + 5, j + 7, x + 16 - j, j + 5, j + 7, BASE_LIGHT, BASE_LIGHT, false);
                }

                this.fillWithOutline(world, bounds, x + 4, 4, 4, x + 6, 4, 20, BASE, BASE, false);
                this.fillWithOutline(world, bounds, x + 7, 4, 4, x + 17, 4, 6, BASE, BASE, false);
                this.fillWithOutline(world, bounds, x + 18, 4, 4, x + 20, 4, 20, BASE, BASE, false);
                this.fillWithOutline(world, bounds, x + 11, 8, 11, x + 13, 8, 20, BASE, BASE, false);
                this.setBlockState(world, BASE_DECO, x + 12, 9, 12, bounds);
                this.setBlockState(world, BASE_DECO, x + 12, 9, 15, bounds);
                this.setBlockState(world, BASE_DECO, x + 12, 9, 18, bounds);
                int j1 = right ? x + 19 : x + 5;
                int k = right ? x + 5 : x + 19;

                for (int l = 20; l >= 5; l -= 3) {
                    this.setBlockState(world, BASE_DECO, j1, 5, l, bounds);
                }

                for (int k1 = 19; k1 >= 7; k1 -= 3) {
                    this.setBlockState(world, BASE_DECO, k, 5, k1, bounds);
                }

                for (int l1 = 0; l1 < 4; l1++) {
                    int i1 = right ? x + (24 - (17 - l1 * 3)) : x + 17 - l1 * 3;
                    this.setBlockState(world, BASE_DECO, i1, 5, 5, bounds);
                }

                this.setBlockState(world, BASE_DECO, k, 5, 5, bounds);
                this.fillWithOutline(world, bounds, x + 11, 1, 12, x + 13, 7, 12, BASE, BASE, false);
                this.fillWithOutline(world, bounds, x + 12, 1, 11, x + 12, 7, 13, BASE, BASE, false);
            }
        }

        private void generateEntranceArch(World world, Random random, StructureBox bounds) {
            if (this.intersects(bounds, 22, 5, 35, 17)) {
                this.fillWater(world, bounds, 25, 0, 0, 32, 8, 20, false);

                for (int i = 0; i < 4; i++) {
                    this.fillWithOutline(world, bounds, 24, 2, 5 + i * 4, 24, 4, 5 + i * 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 22, 4, 5 + i * 4, 23, 4, 5 + i * 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.setBlockState(world, BASE_LIGHT, 25, 5, 5 + i * 4, bounds);
                    this.setBlockState(world, BASE_LIGHT, 26, 6, 5 + i * 4, bounds);
                    this.setBlockState(world, LIGHT_SOURCE, 26, 5, 5 + i * 4, bounds);
                    this.fillWithOutline(world, bounds, 33, 2, 5 + i * 4, 33, 4, 5 + i * 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 34, 4, 5 + i * 4, 35, 4, 5 + i * 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.setBlockState(world, BASE_LIGHT, 32, 5, 5 + i * 4, bounds);
                    this.setBlockState(world, BASE_LIGHT, 31, 6, 5 + i * 4, bounds);
                    this.setBlockState(world, LIGHT_SOURCE, 31, 5, 5 + i * 4, bounds);
                    this.fillWithOutline(world, bounds, 27, 6, 5 + i * 4, 30, 6, 5 + i * 4, BASE, BASE, false);
                }
            }
        }

        private void generateEntranceWall(World world, Random random, StructureBox bounds) {
            if (this.intersects(bounds, 15, 20, 42, 21)) {
                this.fillWithOutline(world, bounds, 15, 0, 21, 42, 0, 21, BASE, BASE, false);
                this.fillWater(world, bounds, 26, 1, 21, 31, 3, 21, false);
                this.fillWithOutline(world, bounds, 21, 12, 21, 36, 12, 21, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 17, 11, 21, 40, 11, 21, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 16, 10, 21, 41, 10, 21, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 15, 7, 21, 42, 9, 21, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 16, 6, 21, 41, 6, 21, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 17, 5, 21, 40, 5, 21, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 21, 4, 21, 36, 4, 21, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 22, 3, 21, 26, 3, 21, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 31, 3, 21, 35, 3, 21, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 23, 2, 21, 25, 2, 21, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 32, 2, 21, 34, 2, 21, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 28, 4, 20, 29, 4, 21, BASE_LIGHT, BASE_LIGHT, false);
                this.setBlockState(world, BASE_LIGHT, 27, 3, 21, bounds);
                this.setBlockState(world, BASE_LIGHT, 30, 3, 21, bounds);
                this.setBlockState(world, BASE_LIGHT, 26, 2, 21, bounds);
                this.setBlockState(world, BASE_LIGHT, 31, 2, 21, bounds);
                this.setBlockState(world, BASE_LIGHT, 25, 1, 21, bounds);
                this.setBlockState(world, BASE_LIGHT, 32, 1, 21, bounds);

                for (int i = 0; i < 7; i++) {
                    this.setBlockState(world, BASE_DARK, 28 - i, 6 + i, 21, bounds);
                    this.setBlockState(world, BASE_DARK, 29 + i, 6 + i, 21, bounds);
                }

                for (int j = 0; j < 4; j++) {
                    this.setBlockState(world, BASE_DARK, 28 - j, 9 + j, 21, bounds);
                    this.setBlockState(world, BASE_DARK, 29 + j, 9 + j, 21, bounds);
                }

                this.setBlockState(world, BASE_DARK, 28, 12, 21, bounds);
                this.setBlockState(world, BASE_DARK, 29, 12, 21, bounds);

                for (int k = 0; k < 3; k++) {
                    this.setBlockState(world, BASE_DARK, 22 - k * 2, 8, 21, bounds);
                    this.setBlockState(world, BASE_DARK, 22 - k * 2, 9, 21, bounds);
                    this.setBlockState(world, BASE_DARK, 35 + k * 2, 8, 21, bounds);
                    this.setBlockState(world, BASE_DARK, 35 + k * 2, 9, 21, bounds);
                }

                this.fillWater(world, bounds, 15, 13, 21, 42, 15, 21, false);
                this.fillWater(world, bounds, 15, 1, 21, 15, 6, 21, false);
                this.fillWater(world, bounds, 16, 1, 21, 16, 5, 21, false);
                this.fillWater(world, bounds, 17, 1, 21, 20, 4, 21, false);
                this.fillWater(world, bounds, 21, 1, 21, 21, 3, 21, false);
                this.fillWater(world, bounds, 22, 1, 21, 22, 2, 21, false);
                this.fillWater(world, bounds, 23, 1, 21, 24, 1, 21, false);
                this.fillWater(world, bounds, 42, 1, 21, 42, 6, 21, false);
                this.fillWater(world, bounds, 41, 1, 21, 41, 5, 21, false);
                this.fillWater(world, bounds, 37, 1, 21, 40, 4, 21, false);
                this.fillWater(world, bounds, 36, 1, 21, 36, 3, 21, false);
                this.fillWater(world, bounds, 33, 1, 21, 34, 1, 21, false);
                this.fillWater(world, bounds, 35, 1, 21, 35, 2, 21, false);
            }
        }

        private void generateRoof(World world, Random random, StructureBox bounds) {
            if (this.intersects(bounds, 21, 21, 36, 36)) {
                this.fillWithOutline(world, bounds, 21, 0, 22, 36, 0, 36, BASE, BASE, false);
                this.fillWater(world, bounds, 21, 1, 22, 36, 23, 36, false);

                for (int i = 0; i < 4; i++) {
                    this.fillWithOutline(world, bounds, 21 + i, 13 + i, 21 + i, 36 - i, 13 + i, 21 + i, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 21 + i, 13 + i, 36 - i, 36 - i, 13 + i, 36 - i, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 21 + i, 13 + i, 22 + i, 21 + i, 13 + i, 35 - i, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 36 - i, 13 + i, 22 + i, 36 - i, 13 + i, 35 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                this.fillWithOutline(world, bounds, 25, 16, 25, 32, 16, 32, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 25, 17, 25, 25, 19, 25, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 32, 17, 25, 32, 19, 25, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 25, 17, 32, 25, 19, 32, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 32, 17, 32, 32, 19, 32, BASE_LIGHT, BASE_LIGHT, false);
                this.setBlockState(world, BASE_LIGHT, 26, 20, 26, bounds);
                this.setBlockState(world, BASE_LIGHT, 27, 21, 27, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 27, 20, 27, bounds);
                this.setBlockState(world, BASE_LIGHT, 26, 20, 31, bounds);
                this.setBlockState(world, BASE_LIGHT, 27, 21, 30, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 27, 20, 30, bounds);
                this.setBlockState(world, BASE_LIGHT, 31, 20, 31, bounds);
                this.setBlockState(world, BASE_LIGHT, 30, 21, 30, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 30, 20, 30, bounds);
                this.setBlockState(world, BASE_LIGHT, 31, 20, 26, bounds);
                this.setBlockState(world, BASE_LIGHT, 30, 21, 27, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 30, 20, 27, bounds);
                this.fillWithOutline(world, bounds, 28, 21, 27, 29, 21, 27, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 27, 21, 28, 27, 21, 29, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 28, 21, 30, 29, 21, 30, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 30, 21, 28, 30, 21, 29, BASE, BASE, false);
            }
        }

        private void generateLowerWall(World world, Random random, StructureBox bounds) {
            if (this.intersects(bounds, 0, 21, 6, 58)) {
                this.fillWithOutline(world, bounds, 0, 0, 21, 6, 0, 57, BASE, BASE, false);
                this.fillWater(world, bounds, 0, 1, 21, 6, 7, 57, false);
                this.fillWithOutline(world, bounds, 4, 4, 21, 6, 4, 53, BASE, BASE, false);

                for (int i = 0; i < 4; i++) {
                    this.fillWithOutline(world, bounds, i, i + 1, 21, i, i + 1, 57 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (int j = 23; j < 53; j += 3) {
                    this.setBlockState(world, BASE_DECO, 5, 5, j, bounds);
                }

                this.setBlockState(world, BASE_DECO, 5, 5, 52, bounds);

                for (int k = 0; k < 4; k++) {
                    this.fillWithOutline(world, bounds, k, k + 1, 21, k, k + 1, 57 - k, BASE_LIGHT, BASE_LIGHT, false);
                }

                this.fillWithOutline(world, bounds, 4, 1, 52, 6, 3, 52, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 5, 1, 51, 5, 3, 53, BASE, BASE, false);
            }

            if (this.intersects(bounds, 51, 21, 58, 58)) {
                this.fillWithOutline(world, bounds, 51, 0, 21, 57, 0, 57, BASE, BASE, false);
                this.fillWater(world, bounds, 51, 1, 21, 57, 7, 57, false);
                this.fillWithOutline(world, bounds, 51, 4, 21, 53, 4, 53, BASE, BASE, false);

                for (int l = 0; l < 4; l++) {
                    this.fillWithOutline(world, bounds, 57 - l, l + 1, 21, 57 - l, l + 1, 57 - l, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (int i1 = 23; i1 < 53; i1 += 3) {
                    this.setBlockState(world, BASE_DECO, 52, 5, i1, bounds);
                }

                this.setBlockState(world, BASE_DECO, 52, 5, 52, bounds);
                this.fillWithOutline(world, bounds, 51, 1, 52, 53, 3, 52, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 52, 1, 51, 52, 3, 53, BASE, BASE, false);
            }

            if (this.intersects(bounds, 0, 51, 57, 57)) {
                this.fillWithOutline(world, bounds, 7, 0, 51, 50, 0, 57, BASE, BASE, false);
                this.fillWater(world, bounds, 7, 1, 51, 50, 10, 57, false);

                for (int j1 = 0; j1 < 4; j1++) {
                    this.fillWithOutline(world, bounds, j1 + 1, j1 + 1, 57 - j1, 56 - j1, j1 + 1, 57 - j1, BASE_LIGHT, BASE_LIGHT, false);
                }
            }
        }

        private void generateMiddleWall(World world, Random random, StructureBox bounds) {
            if (this.intersects(bounds, 7, 21, 13, 50)) {
                this.fillWithOutline(world, bounds, 7, 0, 21, 13, 0, 50, BASE, BASE, false);
                this.fillWater(world, bounds, 7, 1, 21, 13, 10, 50, false);
                this.fillWithOutline(world, bounds, 11, 8, 21, 13, 8, 53, BASE, BASE, false);

                for (int i = 0; i < 4; i++) {
                    this.fillWithOutline(world, bounds, i + 7, i + 5, 21, i + 7, i + 5, 54, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (int j = 21; j <= 45; j += 3) {
                    this.setBlockState(world, BASE_DECO, 12, 9, j, bounds);
                }
            }

            if (this.intersects(bounds, 44, 21, 50, 54)) {
                this.fillWithOutline(world, bounds, 44, 0, 21, 50, 0, 50, BASE, BASE, false);
                this.fillWater(world, bounds, 44, 1, 21, 50, 10, 50, false);
                this.fillWithOutline(world, bounds, 44, 8, 21, 46, 8, 53, BASE, BASE, false);

                for (int k = 0; k < 4; k++) {
                    this.fillWithOutline(world, bounds, 50 - k, k + 5, 21, 50 - k, k + 5, 54, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (int l = 21; l <= 45; l += 3) {
                    this.setBlockState(world, BASE_DECO, 45, 9, l, bounds);
                }
            }

            if (this.intersects(bounds, 8, 44, 49, 54)) {
                this.fillWithOutline(world, bounds, 14, 0, 44, 43, 0, 50, BASE, BASE, false);
                this.fillWater(world, bounds, 14, 1, 44, 43, 10, 50, false);

                for (int i1 = 12; i1 <= 45; i1 += 3) {
                    this.setBlockState(world, BASE_DECO, i1, 9, 45, bounds);
                    this.setBlockState(world, BASE_DECO, i1, 9, 52, bounds);
                    if (i1 == 12 || i1 == 18 || i1 == 24 || i1 == 33 || i1 == 39 || i1 == 45) {
                        this.setBlockState(world, BASE_DECO, i1, 9, 47, bounds);
                        this.setBlockState(world, BASE_DECO, i1, 9, 50, bounds);
                        this.setBlockState(world, BASE_DECO, i1, 10, 45, bounds);
                        this.setBlockState(world, BASE_DECO, i1, 10, 46, bounds);
                        this.setBlockState(world, BASE_DECO, i1, 10, 51, bounds);
                        this.setBlockState(world, BASE_DECO, i1, 10, 52, bounds);
                        this.setBlockState(world, BASE_DECO, i1, 11, 47, bounds);
                        this.setBlockState(world, BASE_DECO, i1, 11, 50, bounds);
                        this.setBlockState(world, BASE_DECO, i1, 12, 48, bounds);
                        this.setBlockState(world, BASE_DECO, i1, 12, 49, bounds);
                    }
                }

                for (int j1 = 0; j1 < 3; j1++) {
                    this.fillWithOutline(world, bounds, 8 + j1, 5 + j1, 54, 49 - j1, 5 + j1, 54, BASE, BASE, false);
                }

                this.fillWithOutline(world, bounds, 11, 8, 54, 46, 8, 54, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 14, 8, 44, 43, 8, 53, BASE, BASE, false);
            }
        }

        private void generateUpperWall(World world, Random random, StructureBox bounds) {
            if (this.intersects(bounds, 14, 21, 20, 43)) {
                this.fillWithOutline(world, bounds, 14, 0, 21, 20, 0, 43, BASE, BASE, false);
                this.fillWater(world, bounds, 14, 1, 22, 20, 14, 43, false);
                this.fillWithOutline(world, bounds, 18, 12, 22, 20, 12, 39, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 18, 12, 21, 20, 12, 21, BASE_LIGHT, BASE_LIGHT, false);

                for (int i = 0; i < 4; i++) {
                    this.fillWithOutline(world, bounds, i + 14, i + 9, 21, i + 14, i + 9, 43 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (int j = 23; j <= 39; j += 3) {
                    this.setBlockState(world, BASE_DECO, 19, 13, j, bounds);
                }
            }

            if (this.intersects(bounds, 37, 21, 43, 43)) {
                this.fillWithOutline(world, bounds, 37, 0, 21, 43, 0, 43, BASE, BASE, false);
                this.fillWater(world, bounds, 37, 1, 22, 43, 14, 43, false);
                this.fillWithOutline(world, bounds, 37, 12, 22, 39, 12, 39, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 37, 12, 21, 39, 12, 21, BASE_LIGHT, BASE_LIGHT, false);

                for (int k = 0; k < 4; k++) {
                    this.fillWithOutline(world, bounds, 43 - k, k + 9, 21, 43 - k, k + 9, 43 - k, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (int l = 23; l <= 39; l += 3) {
                    this.setBlockState(world, BASE_DECO, 38, 13, l, bounds);
                }
            }

            if (this.intersects(bounds, 15, 37, 42, 43)) {
                this.fillWithOutline(world, bounds, 21, 0, 37, 36, 0, 43, BASE, BASE, false);
                this.fillWater(world, bounds, 21, 1, 37, 36, 14, 43, false);
                this.fillWithOutline(world, bounds, 21, 12, 37, 36, 12, 39, BASE, BASE, false);

                for (int i1 = 0; i1 < 4; i1++) {
                    this.fillWithOutline(world, bounds, 15 + i1, i1 + 9, 43 - i1, 42 - i1, i1 + 9, 43 - i1, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (int j1 = 21; j1 <= 36; j1 += 3) {
                    this.setBlockState(world, BASE_DECO, j1, 13, 38, bounds);
                }
            }
        }
    }

    public abstract static class OceanMonumentPiece extends StructurePiece {
        protected static final BlockState BASE = Blocks.PRISMARINE.getStateFromMetadata(PrismarineBlock.ROUGH_VARIANT);
        protected static final BlockState BASE_LIGHT = Blocks.PRISMARINE.getStateFromMetadata(PrismarineBlock.BRICKS_VARIANT);
        protected static final BlockState BASE_DARK = Blocks.PRISMARINE.getStateFromMetadata(PrismarineBlock.DARK_VARIANT);
        protected static final BlockState BASE_DECO = BASE_LIGHT;
        protected static final BlockState LIGHT_SOURCE = Blocks.SEA_LANTERN.defaultState();
        protected static final BlockState FILLER = Blocks.WATER.defaultState();
        protected static final int SOURCE_INDEX = getPieceIndex(2, 0, 0);
        protected static final int TOP_CONNECT_INDEX = getPieceIndex(2, 2, 0);
        protected static final int LEFT_CONNECT_INDEX = getPieceIndex(0, 1, 0);
        protected static final int RIGHT_CONNECT_INDEX = getPieceIndex(4, 1, 0);
        protected OceanMonumentPieces.PieceDefinition pieceDefinition;

        protected static final int getPieceIndex(int rx, int ry, int rz) {
            return ry * 25 + rz * 5 + rx;
        }

        public OceanMonumentPiece() {
            super(0);
        }

        public OceanMonumentPiece(int i) {
            super(i);
        }

        public OceanMonumentPiece(Direction facing, StructureBox bounds) {
            super(1);
            this.facing = facing;
            this.bounds = bounds;
        }

        protected OceanMonumentPiece(int generationDepth, Direction facing, OceanMonumentPieces.PieceDefinition roomDefinition, int rx, int ry, int rz) {
            super(generationDepth);
            this.facing = facing;
            this.pieceDefinition = roomDefinition;
            int i = roomDefinition.index;
            int j = i % 5;
            int k = i / 5 % 5;
            int l = i / 25;
            if (facing != Direction.NORTH && facing != Direction.SOUTH) {
                this.bounds = new StructureBox(0, 0, 0, rz * 8 - 1, ry * 4 - 1, rx * 8 - 1);
            } else {
                this.bounds = new StructureBox(0, 0, 0, rx * 8 - 1, ry * 4 - 1, rz * 8 - 1);
            }

            switch (facing) {
                case NORTH:
                    this.bounds.move(j * 8, l * 4, -(k + rz) * 8 + 1);
                    break;
                case SOUTH:
                    this.bounds.move(j * 8, l * 4, k * 8);
                    break;
                case WEST:
                    this.bounds.move(-(k + rz) * 8 + 1, l * 4, j * 8);
                    break;
                default:
                    this.bounds.move(k * 8, l * 4, j * 8);
            }
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
        }

        protected void fillWater(World world, StructureBox bounds, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean skipAir) {
            for (int i = minY; i <= maxY; i++) {
                for (int j = minX; j <= maxX; j++) {
                    for (int k = minZ; k <= maxZ; k++) {
                        if (!skipAir || this.getBlockState(world, j, i, k, bounds).getBlock().getMaterial() != Material.AIR) {
                            if (this.transformY(i) >= world.getSeaLevel()) {
                                this.setBlockState(world, Blocks.AIR.defaultState(), j, i, k, bounds);
                            } else {
                                this.setBlockState(world, FILLER, j, i, k, bounds);
                            }
                        }
                    }
                }
            }
        }

        protected void generateFloor(World world, StructureBox bounds, int x, int z, boolean lights) {
            if (lights) {
                this.fillWithOutline(world, bounds, x + 0, 0, z + 0, x + 2, 0, z + 8 - 1, BASE, BASE, false);
                this.fillWithOutline(world, bounds, x + 5, 0, z + 0, x + 8 - 1, 0, z + 8 - 1, BASE, BASE, false);
                this.fillWithOutline(world, bounds, x + 3, 0, z + 0, x + 4, 0, z + 2, BASE, BASE, false);
                this.fillWithOutline(world, bounds, x + 3, 0, z + 5, x + 4, 0, z + 8 - 1, BASE, BASE, false);
                this.fillWithOutline(world, bounds, x + 3, 0, z + 2, x + 4, 0, z + 2, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, x + 3, 0, z + 5, x + 4, 0, z + 5, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, x + 2, 0, z + 3, x + 2, 0, z + 4, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, x + 5, 0, z + 3, x + 5, 0, z + 4, BASE_LIGHT, BASE_LIGHT, false);
            } else {
                this.fillWithOutline(world, bounds, x + 0, 0, z + 0, x + 8 - 1, 0, z + 8 - 1, BASE, BASE, false);
            }
        }

        protected void replaceFiller(World world, StructureBox bounds, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState state) {
            for (int i = minY; i <= maxY; i++) {
                for (int j = minX; j <= maxX; j++) {
                    for (int k = minZ; k <= maxZ; k++) {
                        if (this.getBlockState(world, j, i, k, bounds) == FILLER) {
                            this.setBlockState(world, state, j, i, k, bounds);
                        }
                    }
                }
            }
        }

        protected boolean intersects(StructureBox bounds, int minX, int minZ, int maxX, int maxZ) {
            int i = this.transformX(minX, minZ);
            int j = this.transformZ(minX, minZ);
            int k = this.transformX(maxX, maxZ);
            int l = this.transformZ(maxX, maxZ);
            return bounds.intersects(Math.min(i, k), Math.min(j, l), Math.max(i, k), Math.max(j, l));
        }

        protected boolean spawnElderGuardian(World world, StructureBox bounds, int x, int y, int z) {
            int i = this.transformX(x, z);
            int j = this.transformY(y);
            int k = this.transformZ(x, z);
            if (bounds.contains(new BlockPos(i, j, k))) {
                GuardianEntity guardianentity = new GuardianEntity(world);
                guardianentity.setElder(true);
                guardianentity.heal(guardianentity.getMaxHealth());
                guardianentity.setPositionAndAngles(i + 0.5, j, k + 0.5, 0.0F, 0.0F);
                guardianentity.initialize(world.getLocalDifficulty(new BlockPos(guardianentity)), null);
                world.addEntity(guardianentity);
                return true;
            } else {
                return false;
            }
        }
    }

    public static class Penthouse extends OceanMonumentPieces.OceanMonumentPiece {
        public Penthouse() {
        }

        public Penthouse(Direction direction, StructureBox structureBox) {
            super(direction, structureBox);
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(world, bounds, 2, -1, 2, 11, -1, 11, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 0, -1, 0, 1, -1, 11, BASE, BASE, false);
            this.fillWithOutline(world, bounds, 12, -1, 0, 13, -1, 11, BASE, BASE, false);
            this.fillWithOutline(world, bounds, 2, -1, 0, 11, -1, 1, BASE, BASE, false);
            this.fillWithOutline(world, bounds, 2, -1, 12, 11, -1, 13, BASE, BASE, false);
            this.fillWithOutline(world, bounds, 0, 0, 0, 0, 0, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 13, 0, 0, 13, 0, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 0, 0, 12, 0, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 0, 13, 12, 0, 13, BASE_LIGHT, BASE_LIGHT, false);

            for (int i = 2; i <= 11; i += 3) {
                this.setBlockState(world, LIGHT_SOURCE, 0, 0, i, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 13, 0, i, bounds);
                this.setBlockState(world, LIGHT_SOURCE, i, 0, 0, bounds);
            }

            this.fillWithOutline(world, bounds, 2, 0, 3, 4, 0, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 9, 0, 3, 11, 0, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 4, 0, 9, 9, 0, 11, BASE_LIGHT, BASE_LIGHT, false);
            this.setBlockState(world, BASE_LIGHT, 5, 0, 8, bounds);
            this.setBlockState(world, BASE_LIGHT, 8, 0, 8, bounds);
            this.setBlockState(world, BASE_LIGHT, 10, 0, 10, bounds);
            this.setBlockState(world, BASE_LIGHT, 3, 0, 10, bounds);
            this.fillWithOutline(world, bounds, 3, 0, 3, 3, 0, 7, BASE_DARK, BASE_DARK, false);
            this.fillWithOutline(world, bounds, 10, 0, 3, 10, 0, 7, BASE_DARK, BASE_DARK, false);
            this.fillWithOutline(world, bounds, 6, 0, 10, 7, 0, 10, BASE_DARK, BASE_DARK, false);
            int l = 3;

            for (int j = 0; j < 2; j++) {
                for (int k = 2; k <= 8; k += 3) {
                    this.fillWithOutline(world, bounds, l, 0, k, l, 2, k, BASE_LIGHT, BASE_LIGHT, false);
                }

                l = 10;
            }

            this.fillWithOutline(world, bounds, 5, 0, 10, 5, 2, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 8, 0, 10, 8, 2, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 6, -1, 7, 7, -1, 8, BASE_DARK, BASE_DARK, false);
            this.fillWater(world, bounds, 6, -1, 3, 7, -1, 4, false);
            this.spawnElderGuardian(world, bounds, 6, 1, 6);
            return true;
        }
    }

    static class PieceDefinition {
        int index;
        OceanMonumentPieces.PieceDefinition[] connections = new OceanMonumentPieces.PieceDefinition[6];
        boolean[] hasEntrance = new boolean[6];
        boolean claimed;
        boolean source;
        int scanIndex;

        public PieceDefinition(int index) {
            this.index = index;
        }

        public void updateConnection(Direction dir, OceanMonumentPieces.PieceDefinition connection) {
            this.connections[dir.getId()] = connection;
            connection.connections[dir.getOpposite().getId()] = this;
        }

        public void updateEntrances() {
            for (int i = 0; i < 6; i++) {
                this.hasEntrance[i] = this.connections[i] != null;
            }
        }

        public boolean findSource(int index) {
            if (this.source) {
                return true;
            }

            this.scanIndex = index;

            for (int i = 0; i < 6; i++) {
                if (this.connections[i] != null && this.hasEntrance[i] && this.connections[i].scanIndex != index && this.connections[i].findSource(index)) {
                    return true;
                }
            }

            return false;
        }

        public boolean isSpecial() {
            return this.index >= 75;
        }

        public int countEntrances() {
            int i = 0;

            for (int j = 0; j < 6; j++) {
                if (this.hasEntrance[j]) {
                    i++;
                }
            }

            return i;
        }
    }

    interface RoomFitter {
        boolean fits(OceanMonumentPieces.PieceDefinition pieceDefinition);

        OceanMonumentPieces.OceanMonumentPiece create(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random);
    }

    public static class SimpleRoom extends OceanMonumentPieces.OceanMonumentPiece {
        private int design;

        public SimpleRoom() {
        }

        public SimpleRoom(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 1, 1, 1);
            this.design = random.nextInt(3);
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.pieceDefinition.index / 25 > 0) {
                this.generateFloor(world, bounds, 0, 0, this.pieceDefinition.hasEntrance[Direction.DOWN.getId()]);
            }

            if (this.pieceDefinition.connections[Direction.UP.getId()] == null) {
                this.replaceFiller(world, bounds, 1, 4, 1, 6, 4, 6, BASE);
            }

            boolean flag = this.design != 0
                && random.nextBoolean()
                && !this.pieceDefinition.hasEntrance[Direction.DOWN.getId()]
                && !this.pieceDefinition.hasEntrance[Direction.UP.getId()]
                && this.pieceDefinition.countEntrances() > 1;
            if (this.design == 0) {
                this.fillWithOutline(world, bounds, 0, 1, 0, 2, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 0, 3, 0, 2, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 0, 2, 0, 0, 2, 2, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 1, 2, 0, 2, 2, 0, BASE, BASE, false);
                this.setBlockState(world, LIGHT_SOURCE, 1, 2, 1, bounds);
                this.fillWithOutline(world, bounds, 5, 1, 0, 7, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 5, 3, 0, 7, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 7, 2, 0, 7, 2, 2, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 5, 2, 0, 6, 2, 0, BASE, BASE, false);
                this.setBlockState(world, LIGHT_SOURCE, 6, 2, 1, bounds);
                this.fillWithOutline(world, bounds, 0, 1, 5, 2, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 0, 3, 5, 2, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 0, 2, 5, 0, 2, 7, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 1, 2, 7, 2, 2, 7, BASE, BASE, false);
                this.setBlockState(world, LIGHT_SOURCE, 1, 2, 6, bounds);
                this.fillWithOutline(world, bounds, 5, 1, 5, 7, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 5, 3, 5, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 7, 2, 5, 7, 2, 7, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 5, 2, 7, 6, 2, 7, BASE, BASE, false);
                this.setBlockState(world, LIGHT_SOURCE, 6, 2, 6, bounds);
                if (this.pieceDefinition.hasEntrance[Direction.SOUTH.getId()]) {
                    this.fillWithOutline(world, bounds, 3, 3, 0, 4, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.fillWithOutline(world, bounds, 3, 3, 0, 4, 3, 1, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 3, 2, 0, 4, 2, 0, BASE, BASE, false);
                    this.fillWithOutline(world, bounds, 3, 1, 0, 4, 1, 1, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (this.pieceDefinition.hasEntrance[Direction.NORTH.getId()]) {
                    this.fillWithOutline(world, bounds, 3, 3, 7, 4, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.fillWithOutline(world, bounds, 3, 3, 6, 4, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 3, 2, 7, 4, 2, 7, BASE, BASE, false);
                    this.fillWithOutline(world, bounds, 3, 1, 6, 4, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (this.pieceDefinition.hasEntrance[Direction.WEST.getId()]) {
                    this.fillWithOutline(world, bounds, 0, 3, 3, 0, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.fillWithOutline(world, bounds, 0, 3, 3, 1, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 0, 2, 3, 0, 2, 4, BASE, BASE, false);
                    this.fillWithOutline(world, bounds, 0, 1, 3, 1, 1, 4, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (this.pieceDefinition.hasEntrance[Direction.EAST.getId()]) {
                    this.fillWithOutline(world, bounds, 7, 3, 3, 7, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.fillWithOutline(world, bounds, 6, 3, 3, 7, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 7, 2, 3, 7, 2, 4, BASE, BASE, false);
                    this.fillWithOutline(world, bounds, 6, 1, 3, 7, 1, 4, BASE_LIGHT, BASE_LIGHT, false);
                }
            } else if (this.design == 1) {
                this.fillWithOutline(world, bounds, 2, 1, 2, 2, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 2, 1, 5, 2, 3, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 5, 1, 5, 5, 3, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 5, 1, 2, 5, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.setBlockState(world, LIGHT_SOURCE, 2, 2, 2, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 2, 2, 5, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 5, 2, 5, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 5, 2, 2, bounds);
                this.fillWithOutline(world, bounds, 0, 1, 0, 1, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 0, 1, 1, 0, 3, 1, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 0, 1, 7, 1, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 0, 1, 6, 0, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 6, 1, 7, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 7, 1, 6, 7, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 6, 1, 0, 7, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 7, 1, 1, 7, 3, 1, BASE_LIGHT, BASE_LIGHT, false);
                this.setBlockState(world, BASE, 1, 2, 0, bounds);
                this.setBlockState(world, BASE, 0, 2, 1, bounds);
                this.setBlockState(world, BASE, 1, 2, 7, bounds);
                this.setBlockState(world, BASE, 0, 2, 6, bounds);
                this.setBlockState(world, BASE, 6, 2, 7, bounds);
                this.setBlockState(world, BASE, 7, 2, 6, bounds);
                this.setBlockState(world, BASE, 6, 2, 0, bounds);
                this.setBlockState(world, BASE, 7, 2, 1, bounds);
                if (!this.pieceDefinition.hasEntrance[Direction.SOUTH.getId()]) {
                    this.fillWithOutline(world, bounds, 1, 3, 0, 6, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 1, 2, 0, 6, 2, 0, BASE, BASE, false);
                    this.fillWithOutline(world, bounds, 1, 1, 0, 6, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (!this.pieceDefinition.hasEntrance[Direction.NORTH.getId()]) {
                    this.fillWithOutline(world, bounds, 1, 3, 7, 6, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 1, 2, 7, 6, 2, 7, BASE, BASE, false);
                    this.fillWithOutline(world, bounds, 1, 1, 7, 6, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (!this.pieceDefinition.hasEntrance[Direction.WEST.getId()]) {
                    this.fillWithOutline(world, bounds, 0, 3, 1, 0, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 0, 2, 1, 0, 2, 6, BASE, BASE, false);
                    this.fillWithOutline(world, bounds, 0, 1, 1, 0, 1, 6, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (!this.pieceDefinition.hasEntrance[Direction.EAST.getId()]) {
                    this.fillWithOutline(world, bounds, 7, 3, 1, 7, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, 7, 2, 1, 7, 2, 6, BASE, BASE, false);
                    this.fillWithOutline(world, bounds, 7, 1, 1, 7, 1, 6, BASE_LIGHT, BASE_LIGHT, false);
                }
            } else if (this.design == 2) {
                this.fillWithOutline(world, bounds, 0, 1, 0, 0, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 7, 1, 0, 7, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 1, 1, 0, 6, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 1, 1, 7, 6, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 0, 2, 0, 0, 2, 7, BASE_DARK, BASE_DARK, false);
                this.fillWithOutline(world, bounds, 7, 2, 0, 7, 2, 7, BASE_DARK, BASE_DARK, false);
                this.fillWithOutline(world, bounds, 1, 2, 0, 6, 2, 0, BASE_DARK, BASE_DARK, false);
                this.fillWithOutline(world, bounds, 1, 2, 7, 6, 2, 7, BASE_DARK, BASE_DARK, false);
                this.fillWithOutline(world, bounds, 0, 3, 0, 0, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 7, 3, 0, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 1, 3, 0, 6, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 1, 3, 7, 6, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 0, 1, 3, 0, 2, 4, BASE_DARK, BASE_DARK, false);
                this.fillWithOutline(world, bounds, 7, 1, 3, 7, 2, 4, BASE_DARK, BASE_DARK, false);
                this.fillWithOutline(world, bounds, 3, 1, 0, 4, 2, 0, BASE_DARK, BASE_DARK, false);
                this.fillWithOutline(world, bounds, 3, 1, 7, 4, 2, 7, BASE_DARK, BASE_DARK, false);
                if (this.pieceDefinition.hasEntrance[Direction.SOUTH.getId()]) {
                    this.fillWater(world, bounds, 3, 1, 0, 4, 2, 0, false);
                }

                if (this.pieceDefinition.hasEntrance[Direction.NORTH.getId()]) {
                    this.fillWater(world, bounds, 3, 1, 7, 4, 2, 7, false);
                }

                if (this.pieceDefinition.hasEntrance[Direction.WEST.getId()]) {
                    this.fillWater(world, bounds, 0, 1, 3, 0, 2, 4, false);
                }

                if (this.pieceDefinition.hasEntrance[Direction.EAST.getId()]) {
                    this.fillWater(world, bounds, 7, 1, 3, 7, 2, 4, false);
                }
            }

            if (flag) {
                this.fillWithOutline(world, bounds, 3, 1, 3, 4, 1, 4, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 3, 2, 3, 4, 2, 4, BASE, BASE, false);
                this.fillWithOutline(world, bounds, 3, 3, 3, 4, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
            }

            return true;
        }
    }

    static class SimpleRoomFitter implements OceanMonumentPieces.RoomFitter {
        private SimpleRoomFitter() {
        }

        @Override
        public boolean fits(OceanMonumentPieces.PieceDefinition pieceDefinition) {
            return true;
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            pieceDefinition.claimed = true;
            return new OceanMonumentPieces.SimpleRoom(facing, pieceDefinition, random);
        }
    }

    public static class SimpleTopRoom extends OceanMonumentPieces.OceanMonumentPiece {
        public SimpleTopRoom() {
        }

        public SimpleTopRoom(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 1, 1, 1);
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.pieceDefinition.index / 25 > 0) {
                this.generateFloor(world, bounds, 0, 0, this.pieceDefinition.hasEntrance[Direction.DOWN.getId()]);
            }

            if (this.pieceDefinition.connections[Direction.UP.getId()] == null) {
                this.replaceFiller(world, bounds, 1, 4, 1, 6, 4, 6, BASE);
            }

            for (int i = 1; i <= 6; i++) {
                for (int j = 1; j <= 6; j++) {
                    if (random.nextInt(3) != 0) {
                        int k = 2 + (random.nextInt(4) == 0 ? 0 : 1);
                        this.fillWithOutline(
                            world, bounds, i, k, j, i, 3, j, Blocks.SPONGE.getStateFromMetadata(1), Blocks.SPONGE.getStateFromMetadata(1), false
                        );
                    }
                }
            }

            this.fillWithOutline(world, bounds, 0, 1, 0, 0, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 7, 1, 0, 7, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 1, 0, 6, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 1, 7, 6, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 0, 2, 0, 0, 2, 7, BASE_DARK, BASE_DARK, false);
            this.fillWithOutline(world, bounds, 7, 2, 0, 7, 2, 7, BASE_DARK, BASE_DARK, false);
            this.fillWithOutline(world, bounds, 1, 2, 0, 6, 2, 0, BASE_DARK, BASE_DARK, false);
            this.fillWithOutline(world, bounds, 1, 2, 7, 6, 2, 7, BASE_DARK, BASE_DARK, false);
            this.fillWithOutline(world, bounds, 0, 3, 0, 0, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 7, 3, 0, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 3, 0, 6, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 1, 3, 7, 6, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.fillWithOutline(world, bounds, 0, 1, 3, 0, 2, 4, BASE_DARK, BASE_DARK, false);
            this.fillWithOutline(world, bounds, 7, 1, 3, 7, 2, 4, BASE_DARK, BASE_DARK, false);
            this.fillWithOutline(world, bounds, 3, 1, 0, 4, 2, 0, BASE_DARK, BASE_DARK, false);
            this.fillWithOutline(world, bounds, 3, 1, 7, 4, 2, 7, BASE_DARK, BASE_DARK, false);
            if (this.pieceDefinition.hasEntrance[Direction.SOUTH.getId()]) {
                this.fillWater(world, bounds, 3, 1, 0, 4, 2, 0, false);
            }

            return true;
        }
    }

    static class SimpleTopRoomFitter implements OceanMonumentPieces.RoomFitter {
        private SimpleTopRoomFitter() {
        }

        @Override
        public boolean fits(OceanMonumentPieces.PieceDefinition pieceDefinition) {
            return !pieceDefinition.hasEntrance[Direction.WEST.getId()]
                && !pieceDefinition.hasEntrance[Direction.EAST.getId()]
                && !pieceDefinition.hasEntrance[Direction.NORTH.getId()]
                && !pieceDefinition.hasEntrance[Direction.SOUTH.getId()]
                && !pieceDefinition.hasEntrance[Direction.UP.getId()];
        }

        @Override
        public OceanMonumentPieces.OceanMonumentPiece create(Direction facing, OceanMonumentPieces.PieceDefinition pieceDefinition, Random random) {
            pieceDefinition.claimed = true;
            return new OceanMonumentPieces.SimpleTopRoom(facing, pieceDefinition, random);
        }
    }

    public static class WingRoom extends OceanMonumentPieces.OceanMonumentPiece {
        private int design;

        public WingRoom() {
        }

        public WingRoom(Direction facing, StructureBox bounds, int design) {
            super(facing, bounds);
            this.design = design & 1;
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (this.design == 0) {
                for (int i = 0; i < 4; i++) {
                    this.fillWithOutline(world, bounds, 10 - i, 3 - i, 20 - i, 12 + i, 3 - i, 20, BASE_LIGHT, BASE_LIGHT, false);
                }

                this.fillWithOutline(world, bounds, 7, 0, 6, 15, 0, 16, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 6, 0, 6, 6, 3, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 16, 0, 6, 16, 3, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 7, 1, 7, 7, 1, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 15, 1, 7, 15, 1, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 7, 1, 6, 9, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 13, 1, 6, 15, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 8, 1, 7, 9, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 13, 1, 7, 14, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 9, 0, 5, 13, 0, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 10, 0, 7, 12, 0, 7, BASE_DARK, BASE_DARK, false);
                this.fillWithOutline(world, bounds, 8, 0, 10, 8, 0, 12, BASE_DARK, BASE_DARK, false);
                this.fillWithOutline(world, bounds, 14, 0, 10, 14, 0, 12, BASE_DARK, BASE_DARK, false);

                for (int i1 = 18; i1 >= 7; i1 -= 3) {
                    this.setBlockState(world, LIGHT_SOURCE, 6, 3, i1, bounds);
                    this.setBlockState(world, LIGHT_SOURCE, 16, 3, i1, bounds);
                }

                this.setBlockState(world, LIGHT_SOURCE, 10, 0, 10, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 12, 0, 10, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 10, 0, 12, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 12, 0, 12, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 8, 3, 6, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 14, 3, 6, bounds);
                this.setBlockState(world, BASE_LIGHT, 4, 2, 4, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 4, 1, 4, bounds);
                this.setBlockState(world, BASE_LIGHT, 4, 0, 4, bounds);
                this.setBlockState(world, BASE_LIGHT, 18, 2, 4, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 18, 1, 4, bounds);
                this.setBlockState(world, BASE_LIGHT, 18, 0, 4, bounds);
                this.setBlockState(world, BASE_LIGHT, 4, 2, 18, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 4, 1, 18, bounds);
                this.setBlockState(world, BASE_LIGHT, 4, 0, 18, bounds);
                this.setBlockState(world, BASE_LIGHT, 18, 2, 18, bounds);
                this.setBlockState(world, LIGHT_SOURCE, 18, 1, 18, bounds);
                this.setBlockState(world, BASE_LIGHT, 18, 0, 18, bounds);
                this.setBlockState(world, BASE_LIGHT, 9, 7, 20, bounds);
                this.setBlockState(world, BASE_LIGHT, 13, 7, 20, bounds);
                this.fillWithOutline(world, bounds, 6, 0, 21, 7, 4, 21, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 15, 0, 21, 16, 4, 21, BASE_LIGHT, BASE_LIGHT, false);
                this.spawnElderGuardian(world, bounds, 11, 2, 16);
            } else if (this.design == 1) {
                this.fillWithOutline(world, bounds, 9, 3, 18, 13, 3, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 9, 0, 18, 9, 2, 18, BASE_LIGHT, BASE_LIGHT, false);
                this.fillWithOutline(world, bounds, 13, 0, 18, 13, 2, 18, BASE_LIGHT, BASE_LIGHT, false);
                int j1 = 9;
                int j = 20;
                int k = 5;

                for (int l = 0; l < 2; l++) {
                    this.setBlockState(world, BASE_LIGHT, j1, k + 1, j, bounds);
                    this.setBlockState(world, LIGHT_SOURCE, j1, k, j, bounds);
                    this.setBlockState(world, BASE_LIGHT, j1, k - 1, j, bounds);
                    j1 = 13;
                }

                this.fillWithOutline(world, bounds, 7, 3, 7, 15, 3, 14, BASE_LIGHT, BASE_LIGHT, false);
                int b0 = 10;

                for (int k1 = 0; k1 < 2; k1++) {
                    this.fillWithOutline(world, bounds, b0, 0, 10, b0, 6, 10, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, b0, 0, 12, b0, 6, 12, BASE_LIGHT, BASE_LIGHT, false);
                    this.setBlockState(world, LIGHT_SOURCE, b0, 0, 10, bounds);
                    this.setBlockState(world, LIGHT_SOURCE, b0, 0, 12, bounds);
                    this.setBlockState(world, LIGHT_SOURCE, b0, 4, 10, bounds);
                    this.setBlockState(world, LIGHT_SOURCE, b0, 4, 12, bounds);
                    b0 = 12;
                }

                b0 = 8;

                for (int l1 = 0; l1 < 2; l1++) {
                    this.fillWithOutline(world, bounds, b0, 0, 7, b0, 2, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.fillWithOutline(world, bounds, b0, 0, 14, b0, 2, 14, BASE_LIGHT, BASE_LIGHT, false);
                    b0 = 14;
                }

                this.fillWithOutline(world, bounds, 8, 3, 8, 8, 3, 13, BASE_DARK, BASE_DARK, false);
                this.fillWithOutline(world, bounds, 14, 3, 8, 14, 3, 13, BASE_DARK, BASE_DARK, false);
                this.spawnElderGuardian(world, bounds, 11, 5, 13);
            }

            return true;
        }
    }
}
