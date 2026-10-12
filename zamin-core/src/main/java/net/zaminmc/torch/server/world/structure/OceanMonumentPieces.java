package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The ocean monument piece family (Slice 12e — the reference port of
 * gen/structure/OceanMonumentPieces.java, 1:1 over the Slice 12a
 * foundation): the room-graph layout (the 75-cell lattice, the connection
 * walk, the claim marks, the entrance-removal findSource pass), the seven
 * room fitters, the room families, and the master monument piece (the
 * wings, the entrance arch, the wall tiers, the roof, the base grid).
 *
 * <p><b>The six-direction band</b> (the reference's {@code Direction}
 * ids): DOWN 0, UP 1, NORTH 2, SOUTH 3, WEST 4, EAST 5 — the
 * {@link PieceDefinition} connection arrays are indexed by it.</p>
 *
 * <p><b>Ledgered</b> (the reference's arms the engine lacks the systems
 * for): the elder guardian spawns (the Penthouse's and the two wings' —
 * the guardian entity rides the guardian-mob slice) and the monument's
 * own guardian spawn table (Guardian 1, 2-4 — the same slice).</p>
 */
public final class OceanMonumentPieces {

    private OceanMonumentPieces() {
    }

    // ------------------------------------------------- the direction id band

    static final int DOWN = 0;
    static final int UP = 1;
    static final int NORTH = 2;
    static final int SOUTH = 3;
    static final int WEST = 4;
    static final int EAST = 5;

    /** The reference's Direction offsets, indexed by the id band. */
    private static final int[] OFFSET_X = {0, 0, 0, 0, -1, 1};
    private static final int[] OFFSET_Y = {-1, 1, 0, 0, 0, 0};
    private static final int[] OFFSET_Z = {0, 0, -1, 1, 0, 0};

    /** The reference's Direction.getOpposite ids, indexed by the id band. */
    private static final int[] OPPOSITE = {UP, DOWN, SOUTH, NORTH, EAST, WEST};

    /** The cell index (getPieceIndex lines 1252-1254): {@code ry * 25 + rz * 5 + rx}. */
    static int pieceIndex(int rx, int ry, int rz) {
        return ry * 25 + rz * 5 + rx;
    }

    /** The source cell (SOURCE_INDEX): the (2, 0, 0) room. */
    static final int SOURCE_INDEX = pieceIndex(2, 0, 0);
    /** The top connect cell (TOP_CONNECT_INDEX): the (2, 2, 0) room. */
    static final int TOP_CONNECT_INDEX = pieceIndex(2, 2, 0);
    /** The left connect cell (LEFT_CONNECT_INDEX): the (0, 1, 0) room. */
    static final int LEFT_CONNECT_INDEX = pieceIndex(0, 1, 0);
    /** The right connect cell (RIGHT_CONNECT_INDEX): the (4, 1, 0) room. */
    static final int RIGHT_CONNECT_INDEX = pieceIndex(4, 1, 0);

    // ------------------------------------------------------- the room graph

    /**
     * One room cell of the layout graph (PieceDefinition lines 1367-1414):
     * the six connections, the entrance flags, the claim marks, the source
     * flag and the findSource scan index.
     */
    static final class PieceDefinition {
        final int index;
        final PieceDefinition[] connections = new PieceDefinition[6];
        final boolean[] hasEntrance = new boolean[6];
        boolean claimed;
        boolean source;
        int scanIndex;

        PieceDefinition(int index) {
            this.index = index;
        }

        /** updateConnection lines 1380-1383: the two-way link. */
        void updateConnection(int dir, PieceDefinition connection) {
            this.connections[dir] = connection;
            connection.connections[OPPOSITE[dir]] = this;
        }

        /** updateEntrances lines 1385-1390. */
        void updateEntrances() {
            for (int i = 0; i < 6; i++) {
                this.hasEntrance[i] = this.connections[i] != null;
            }
        }

        /** findSource lines 1392-1406: the recursive reachability walk. */
        boolean findSource(int index) {
            if (this.source) {
                return true;
            }
            this.scanIndex = index;
            for (int i = 0; i < 6; i++) {
                if (this.connections[i] != null && this.hasEntrance[i]
                        && this.connections[i].scanIndex != index
                        && this.connections[i].findSource(index)) {
                    return true;
                }
            }
            return false;
        }

        /** isSpecial line 1408: the appended wing/penthouse cells. */
        boolean isSpecial() {
            return this.index >= 75;
        }

        /** countEntrances lines 1410-1418. */
        int countEntrances() {
            int count = 0;
            for (int i = 0; i < 6; i++) {
                if (this.hasEntrance[i]) {
                    count++;
                }
            }
            return count;
        }
    }

    /** The room fitter (RoomFitter lines 1419-1423). */
    private interface RoomFitter {
        boolean fits(PieceDefinition pieceDefinition);

        OceanMonumentPiece create(StructureFacing facing, PieceDefinition pieceDefinition, Random random);
    }

    // ------------------------------------------------------------ the base

    /**
     * The monument piece base (OceanMonumentPiece lines 1240-1365): the
     * room-placement constructor with the reference's exact move math, the
     * water fill (air above the sea level, water below), the floor pair,
     * the filler replace, and the local-frame intersect probe.
     */
    abstract static class OceanMonumentPiece extends StructurePiece {
        PieceDefinition pieceDefinition;

        protected OceanMonumentPiece(int generationDepth) {
            super(generationDepth);
        }

        protected OceanMonumentPiece(StructureFacing facing, StructureBox bounds) {
            super(1);
            this.facing = facing;
            this.bounds = bounds;
        }

        /**
         * The room placement (lines 1276-1301): the cell-derived local box
         * (the rx/rz span swap for the east/west facings) with the cell
         * move — the reference's exact arithmetic.
         */
        protected OceanMonumentPiece(int generationDepth, StructureFacing facing,
                                     PieceDefinition roomDefinition, int rx, int ry, int rz) {
            super(generationDepth);
            this.facing = facing;
            this.pieceDefinition = roomDefinition;
            int index = roomDefinition.index;
            int cellX = index % 5;
            int cellZ = index / 5 % 5;
            int cellY = index / 25;
            if (facing != StructureFacing.NORTH && facing != StructureFacing.SOUTH) {
                this.bounds = new StructureBox(0, 0, 0, rz * 8 - 1, ry * 4 - 1, rx * 8 - 1);
            } else {
                this.bounds = new StructureBox(0, 0, 0, rx * 8 - 1, ry * 4 - 1, rz * 8 - 1);
            }
            switch (facing) {
                case NORTH -> this.bounds.move(cellX * 8, cellY * 4, -(cellZ + rz) * 8 + 1);
                case SOUTH -> this.bounds.move(cellX * 8, cellY * 4, cellZ * 8);
                case WEST -> this.bounds.move(-(cellZ + rz) * 8 + 1, cellY * 4, cellX * 8);
                default -> this.bounds.move(cellZ * 8, cellY * 4, cellX * 8);
            }
        }

        /**
         * The water fill (fillWater lines 1324-1341): water below the sea
         * level, air at or above it.
         */
        void fillWater(StructureWorld world, StructureBox clip,
                       int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean skipAir) {
            StructurePalette palette = world.palette();
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (!skipAir || !world.isAir(this.getBlockState(world, x, y, z, clip))) {
                            if (this.transformY(y) >= world.seaLevel()) {
                                this.setBlockState(world, palette.air(), x, y, z, clip);
                            } else {
                                this.setBlockState(world, palette.water(), x, y, z, clip);
                            }
                        }
                    }
                }
            }
        }

        /**
         * The floor pair (generateFloor lines 1343-1358): the plain floor,
         * or the lit pattern with the four light cells.
         */
        void generateFloor(StructureWorld world, StructureBox clip, int x, int z, boolean lights) {
            StructurePalette palette = world.palette();
            if (lights) {
                this.fillWithOutline(world, clip, x + 0, 0, z + 0, x + 2, 0, z + 8 - 1,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, x + 5, 0, z + 0, x + 8 - 1, 0, z + 8 - 1,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, x + 3, 0, z + 0, x + 4, 0, z + 2,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, x + 3, 0, z + 5, x + 4, 0, z + 8 - 1,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, x + 3, 0, z + 2, x + 4, 0, z + 2,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, x + 3, 0, z + 5, x + 4, 0, z + 5,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, x + 2, 0, z + 3, x + 2, 0, z + 4,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, x + 5, 0, z + 3, x + 5, 0, z + 4,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
            } else {
                this.fillWithOutline(world, clip, x + 0, 0, z + 0, x + 8 - 1, 0, z + 8 - 1,
                        palette.prismarineRough(), palette.prismarineRough(), false);
            }
        }

        /**
         * The filler replace (replaceFiller lines 1360-1373): the water
         * cells become the given state.
         */
        void replaceFiller(StructureWorld world, StructureBox clip,
                           int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockType state) {
            StructurePalette palette = world.palette();
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (this.getBlockState(world, x, y, z, clip) == palette.water()) {
                            this.setBlockState(world, state, x, y, z, clip);
                        }
                    }
                }
            }
        }

        /** The local-frame intersect probe (intersects lines 1375-1385). */
        boolean intersects(StructureBox clip, int minX, int minZ, int maxX, int maxZ) {
            int ax = this.transformX(minX, minZ);
            int az = this.transformZ(minX, minZ);
            int bx = this.transformX(maxX, maxZ);
            int bz = this.transformZ(maxX, maxZ);
            return clip.intersects(Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz));
        }

        /**
         * The elder guardian spawn (spawnElderGuardian lines 1387-1401): the
         * guardian entity rides the guardian-mob slice — the engine's
         * stand-in is the documented no-op (the ledger's envelope note).
         */
        @SuppressWarnings("unused")
        void spawnElderGuardian(StructureWorld world, StructureBox clip, int x, int y, int z) {
            // No entity system for the guardian yet — the position check
            // stays for the record (the reference's bounds.contains gate).
        }
    }

    // ------------------------------------------------------ the room families
    // (in the reference's own declaration order)

    /** The core room (CoreRoom lines 28-127): the eight gold-block treasure room. */
    static final class CoreRoom extends OceanMonumentPiece {

        CoreRoom(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 2, 2, 2);
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.replaceFiller(world, clip, 1, 8, 0, 14, 8, 14, palette.prismarineRough());
            int rim = 7;
            this.fillWithOutline(world, clip, 0, rim, 0, 0, rim, 15,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 15, rim, 0, 15, rim, 15,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, rim, 0, 15, rim, 0,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, rim, 15, 14, rim, 15,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            for (int k = 1; k <= 6; k++) {
                BlockType state = palette.prismarineBricks();
                if (k == 2 || k == 6) {
                    state = palette.prismarineRough();
                }
                for (int j = 0; j <= 15; j += 15) {
                    this.fillWithOutline(world, clip, j, k, 0, j, k, 1, state, state, false);
                    this.fillWithOutline(world, clip, j, k, 6, j, k, 9, state, state, false);
                    this.fillWithOutline(world, clip, j, k, 14, j, k, 15, state, state, false);
                }
                this.fillWithOutline(world, clip, 1, k, 0, 1, k, 0, state, state, false);
                this.fillWithOutline(world, clip, 6, k, 0, 9, k, 0, state, state, false);
                this.fillWithOutline(world, clip, 14, k, 0, 14, k, 0, state, state, false);
                this.fillWithOutline(world, clip, 1, k, 15, 14, k, 15, state, state, false);
            }
            this.fillWithOutline(world, clip, 6, 3, 6, 9, 6, 9,
                    palette.prismarineDark(), palette.prismarineDark(), false);
            this.fillWithOutline(world, clip, 7, 4, 7, 8, 5, 8,
                    palette.goldBlock(), palette.goldBlock(), false);
            for (int l = 3; l <= 6; l += 3) {
                for (int i1 = 6; i1 <= 9; i1 += 3) {
                    this.setBlockState(world, palette.seaLantern(), i1, l, 6, clip);
                    this.setBlockState(world, palette.seaLantern(), i1, l, 9, clip);
                }
            }
            this.fillWithOutline(world, clip, 5, 1, 6, 5, 2, 6,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 1, 9, 5, 2, 9,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 10, 1, 6, 10, 2, 6,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 10, 1, 9, 10, 2, 9,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 6, 1, 5, 6, 2, 5,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 9, 1, 5, 9, 2, 5,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 6, 1, 10, 6, 2, 10,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 9, 1, 10, 9, 2, 10,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 2, 5, 5, 6, 5,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 2, 10, 5, 6, 10,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 10, 2, 5, 10, 6, 5,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 10, 2, 10, 10, 6, 10,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 7, 1, 5, 7, 6,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 10, 7, 1, 10, 7, 6,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 7, 9, 5, 7, 14,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 10, 7, 9, 10, 7, 14,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 7, 5, 6, 7, 5,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 7, 10, 6, 7, 10,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 9, 7, 5, 14, 7, 5,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 9, 7, 10, 14, 7, 10,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 2, 1, 2, 2, 1, 3,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 3, 1, 2, 3, 1, 2,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 13, 1, 2, 13, 1, 3,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 12, 1, 2, 12, 1, 2,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 2, 1, 12, 2, 1, 13,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 3, 1, 13, 3, 1, 13,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 13, 1, 12, 13, 1, 13,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 12, 1, 13, 12, 1, 13,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            return true;
        }
    }

    /** The double-height room (DoubleHeightRoom lines 129-224): the two-storey cell pair. */
    static final class DoubleHeightRoom extends OceanMonumentPiece {

        DoubleHeightRoom(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 1, 2, 1);
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.pieceDefinition.index / 25 > 0) {
                this.generateFloor(world, clip, 0, 0, this.pieceDefinition.hasEntrance[DOWN]);
            }
            PieceDefinition above = this.pieceDefinition.connections[UP];
            if (above.connections[UP] == null) {
                this.replaceFiller(world, clip, 1, 8, 1, 6, 8, 6, palette.prismarineRough());
            }
            this.fillWithOutline(world, clip, 0, 4, 0, 0, 4, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 7, 4, 0, 7, 4, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 4, 0, 6, 4, 0,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 4, 7, 6, 4, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 2, 4, 1, 2, 4, 2,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 4, 2, 1, 4, 2,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 4, 1, 5, 4, 2,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 6, 4, 2, 6, 4, 2,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 2, 4, 5, 2, 4, 6,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 4, 5, 1, 4, 5,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 4, 5, 5, 4, 6,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 6, 4, 5, 6, 4, 5,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            PieceDefinition scan = this.pieceDefinition;
            for (int i = 1; i <= 5; i += 4) {
                int south = 0;
                if (scan.hasEntrance[SOUTH]) {
                    this.fillWithOutline(world, clip, 2, i, south, 2, i + 2, south,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 5, i, south, 5, i + 2, south,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 3, i + 2, south, 4, i + 2, south,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                } else {
                    this.fillWithOutline(world, clip, 0, i, south, 7, i + 2, south,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 0, i + 1, south, 7, i + 1, south,
                            palette.prismarineRough(), palette.prismarineRough(), false);
                }
                int north = 7;
                if (scan.hasEntrance[NORTH]) {
                    this.fillWithOutline(world, clip, 2, i, north, 2, i + 2, north,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 5, i, north, 5, i + 2, north,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 3, i + 2, north, 4, i + 2, north,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                } else {
                    this.fillWithOutline(world, clip, 0, i, north, 7, i + 2, north,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 0, i + 1, north, 7, i + 1, north,
                            palette.prismarineRough(), palette.prismarineRough(), false);
                }
                int west = 0;
                if (scan.hasEntrance[WEST]) {
                    this.fillWithOutline(world, clip, west, i, 2, west, i + 2, 2,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, west, i, 5, west, i + 2, 5,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, west, i + 2, 3, west, i + 2, 4,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                } else {
                    this.fillWithOutline(world, clip, west, i, 0, west, i + 2, 7,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, west, i + 1, 0, west, i + 1, 7,
                            palette.prismarineRough(), palette.prismarineRough(), false);
                }
                int east = 7;
                if (scan.hasEntrance[EAST]) {
                    this.fillWithOutline(world, clip, east, i, 2, east, i + 2, 2,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, east, i, 5, east, i + 2, 5,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, east, i + 2, 3, east, i + 2, 4,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                } else {
                    this.fillWithOutline(world, clip, east, i, 0, east, i + 2, 7,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, east, i + 1, 0, east, i + 1, 7,
                            palette.prismarineRough(), palette.prismarineRough(), false);
                }
                scan = this.pieceDefinition;
            }
            return true;
        }
    }

    /** The double-height fitter (DoubleHeightRoomFitter lines 226-240). */
    private static final class DoubleHeightRoomFitter implements RoomFitter {
        @Override
        public boolean fits(PieceDefinition pieceDefinition) {
            return pieceDefinition.hasEntrance[UP] && !pieceDefinition.connections[UP].claimed;
        }

        @Override
        public OceanMonumentPiece create(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            pieceDefinition.claimed = true;
            pieceDefinition.connections[UP].claimed = true;
            return new DoubleHeightRoom(facing, pieceDefinition, random);
        }
    }

    /** The double-height double-width X room (DoubleHeightWidthXRoom lines 242-370). */
    static final class DoubleHeightWidthXRoom extends OceanMonumentPiece {

        DoubleHeightWidthXRoom(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 2, 2, 1);
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            PieceDefinition east = this.pieceDefinition.connections[EAST];
            PieceDefinition here = this.pieceDefinition;
            PieceDefinition aboveHere = here.connections[UP];
            PieceDefinition aboveEast = east.connections[UP];
            if (this.pieceDefinition.index / 25 > 0) {
                this.generateFloor(world, clip, 8, 0, east.hasEntrance[DOWN]);
                this.generateFloor(world, clip, 0, 0, here.hasEntrance[DOWN]);
            }
            if (aboveHere.connections[UP] == null) {
                this.replaceFiller(world, clip, 1, 8, 1, 7, 8, 6, palette.prismarineRough());
            }
            if (aboveEast.connections[UP] == null) {
                this.replaceFiller(world, clip, 8, 8, 1, 14, 8, 6, palette.prismarineRough());
            }
            for (int i = 1; i <= 7; i++) {
                BlockType state = palette.prismarineBricks();
                if (i == 2 || i == 6) {
                    state = palette.prismarineRough();
                }
                this.fillWithOutline(world, clip, 0, i, 0, 0, i, 7, state, state, false);
                this.fillWithOutline(world, clip, 15, i, 0, 15, i, 7, state, state, false);
                this.fillWithOutline(world, clip, 1, i, 0, 15, i, 0, state, state, false);
                this.fillWithOutline(world, clip, 1, i, 7, 14, i, 7, state, state, false);
            }
            this.fillWithOutline(world, clip, 2, 1, 3, 2, 7, 4,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 3, 1, 2, 4, 7, 2,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 3, 1, 5, 4, 7, 5,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 13, 1, 3, 13, 7, 4,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 11, 1, 2, 12, 7, 2,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 11, 1, 5, 12, 7, 5,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 1, 3, 5, 3, 4,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 10, 1, 3, 10, 3, 4,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 7, 2, 10, 7, 5,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 5, 2, 5, 7, 2,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 10, 5, 2, 10, 7, 2,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 5, 5, 5, 7, 5,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 10, 5, 5, 10, 7, 5,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.setBlockState(world, palette.prismarineBricks(), 6, 6, 2, clip);
            this.setBlockState(world, palette.prismarineBricks(), 9, 6, 2, clip);
            this.setBlockState(world, palette.prismarineBricks(), 6, 6, 5, clip);
            this.setBlockState(world, palette.prismarineBricks(), 9, 6, 5, clip);
            this.fillWithOutline(world, clip, 5, 4, 3, 6, 4, 4,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 9, 4, 3, 10, 4, 4,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.setBlockState(world, palette.seaLantern(), 5, 4, 2, clip);
            this.setBlockState(world, palette.seaLantern(), 5, 4, 5, clip);
            this.setBlockState(world, palette.seaLantern(), 10, 4, 2, clip);
            this.setBlockState(world, palette.seaLantern(), 10, 4, 5, clip);
            if (here.hasEntrance[SOUTH]) {
                this.fillWater(world, clip, 3, 1, 0, 4, 2, 0, false);
            }
            if (here.hasEntrance[NORTH]) {
                this.fillWater(world, clip, 3, 1, 7, 4, 2, 7, false);
            }
            if (here.hasEntrance[WEST]) {
                this.fillWater(world, clip, 0, 1, 3, 0, 2, 4, false);
            }
            if (east.hasEntrance[SOUTH]) {
                this.fillWater(world, clip, 11, 1, 0, 12, 2, 0, false);
            }
            if (east.hasEntrance[NORTH]) {
                this.fillWater(world, clip, 11, 1, 7, 12, 2, 7, false);
            }
            if (east.hasEntrance[EAST]) {
                this.fillWater(world, clip, 15, 1, 3, 15, 2, 4, false);
            }
            if (aboveHere.hasEntrance[SOUTH]) {
                this.fillWater(world, clip, 3, 5, 0, 4, 6, 0, false);
            }
            if (aboveHere.hasEntrance[NORTH]) {
                this.fillWater(world, clip, 3, 5, 7, 4, 6, 7, false);
            }
            if (aboveHere.hasEntrance[WEST]) {
                this.fillWater(world, clip, 0, 5, 3, 0, 6, 4, false);
            }
            if (aboveEast.hasEntrance[SOUTH]) {
                this.fillWater(world, clip, 11, 5, 0, 12, 6, 0, false);
            }
            if (aboveEast.hasEntrance[NORTH]) {
                this.fillWater(world, clip, 11, 5, 7, 12, 6, 7, false);
            }
            if (aboveEast.hasEntrance[EAST]) {
                this.fillWater(world, clip, 15, 5, 3, 15, 6, 4, false);
            }
            return true;
        }
    }

    /** The double-height double-width X fitter (lines 372-398). */
    private static final class DoubleHeightWidthXRoomFitter implements RoomFitter {
        @Override
        public boolean fits(PieceDefinition pieceDefinition) {
            if (pieceDefinition.hasEntrance[EAST]
                    && !pieceDefinition.connections[EAST].claimed
                    && pieceDefinition.hasEntrance[UP]
                    && !pieceDefinition.connections[UP].claimed) {
                PieceDefinition east = pieceDefinition.connections[EAST];
                return east.hasEntrance[UP] && !east.connections[UP].claimed;
            }
            return false;
        }

        @Override
        public OceanMonumentPiece create(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            pieceDefinition.claimed = true;
            pieceDefinition.connections[EAST].claimed = true;
            pieceDefinition.connections[UP].claimed = true;
            pieceDefinition.connections[EAST].connections[UP].claimed = true;
            return new DoubleHeightWidthXRoom(facing, pieceDefinition, random);
        }
    }

    /** The monument entry (MonumentEntry lines 700-740): the source room's doorway. */
    static final class MonumentEntry extends OceanMonumentPiece {

        MonumentEntry(StructureFacing facing, PieceDefinition pieceDefinition) {
            super(1, facing, pieceDefinition, 1, 1, 1);
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 3, 0, 2, 3, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 3, 0, 7, 3, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 1, 2, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 6, 2, 0, 7, 2, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 0, 1, 0, 0, 1, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 7, 1, 0, 7, 1, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 0, 1, 7, 7, 3, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 1, 0, 2, 3, 0,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 1, 0, 6, 3, 0,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            if (this.pieceDefinition.hasEntrance[NORTH]) {
                this.fillWater(world, clip, 3, 1, 7, 4, 2, 7, false);
            }
            if (this.pieceDefinition.hasEntrance[WEST]) {
                this.fillWater(world, clip, 0, 1, 3, 1, 2, 4, false);
            }
            if (this.pieceDefinition.hasEntrance[EAST]) {
                this.fillWater(world, clip, 6, 1, 3, 7, 2, 4, false);
            }
            return true;
        }
    }

    /** The penthouse (Penthouse lines 1160-1473): the top room with the elder guardian. */
    static final class Penthouse extends OceanMonumentPiece {

        Penthouse(StructureFacing direction, StructureBox box) {
            super(direction, box);
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 2, -1, 2, 11, -1, 11,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 0, -1, 0, 1, -1, 11,
                    palette.prismarineRough(), palette.prismarineRough(), false);
            this.fillWithOutline(world, clip, 12, -1, 0, 13, -1, 11,
                    palette.prismarineRough(), palette.prismarineRough(), false);
            this.fillWithOutline(world, clip, 2, -1, 0, 11, -1, 1,
                    palette.prismarineRough(), palette.prismarineRough(), false);
            this.fillWithOutline(world, clip, 2, -1, 12, 11, -1, 13,
                    palette.prismarineRough(), palette.prismarineRough(), false);
            this.fillWithOutline(world, clip, 0, 0, 0, 0, 0, 13,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 13, 0, 0, 13, 0, 13,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 0, 0, 12, 0, 0,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 0, 13, 12, 0, 13,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            for (int i = 2; i <= 11; i += 3) {
                this.setBlockState(world, palette.seaLantern(), 0, 0, i, clip);
                this.setBlockState(world, palette.seaLantern(), 13, 0, i, clip);
                this.setBlockState(world, palette.seaLantern(), i, 0, 0, clip);
            }
            this.fillWithOutline(world, clip, 2, 0, 3, 4, 0, 9,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 9, 0, 3, 11, 0, 9,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 4, 0, 9, 9, 0, 11,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.setBlockState(world, palette.prismarineBricks(), 5, 0, 8, clip);
            this.setBlockState(world, palette.prismarineBricks(), 8, 0, 8, clip);
            this.setBlockState(world, palette.prismarineBricks(), 10, 0, 10, clip);
            this.setBlockState(world, palette.prismarineBricks(), 3, 0, 10, clip);
            this.fillWithOutline(world, clip, 3, 0, 3, 3, 0, 7,
                    palette.prismarineDark(), palette.prismarineDark(), false);
            this.fillWithOutline(world, clip, 10, 0, 3, 10, 0, 7,
                    palette.prismarineDark(), palette.prismarineDark(), false);
            this.fillWithOutline(world, clip, 6, 0, 10, 7, 0, 10,
                    palette.prismarineDark(), palette.prismarineDark(), false);
            int column = 3;
            for (int j = 0; j < 2; j++) {
                for (int k = 2; k <= 8; k += 3) {
                    this.fillWithOutline(world, clip, column, 0, k, column, 2, k,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                column = 10;
            }
            this.fillWithOutline(world, clip, 5, 0, 10, 5, 2, 10,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 8, 0, 10, 8, 2, 10,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 6, -1, 7, 7, -1, 8,
                    palette.prismarineDark(), palette.prismarineDark(), false);
            this.fillWater(world, clip, 6, -1, 3, 7, -1, 4, false);
            // The elder guardian (the reference's spawnElderGuardian at
            // (6, 1, 6)): the entity rides the guardian-mob slice.
            this.spawnElderGuardian(world, clip, 6, 1, 6);
            return true;
        }
    }

    /** The simple room (SimpleRoom lines 1425-1702): the three-design cell. */
    static final class SimpleRoom extends OceanMonumentPiece {
        private final int design;

        SimpleRoom(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 1, 1, 1);
            this.design = random.nextInt(3);
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.pieceDefinition.index / 25 > 0) {
                this.generateFloor(world, clip, 0, 0, this.pieceDefinition.hasEntrance[DOWN]);
            }
            if (this.pieceDefinition.connections[UP] == null) {
                this.replaceFiller(world, clip, 1, 4, 1, 6, 4, 6, palette.prismarineRough());
            }
            boolean pillar = this.design != 0
                    && random.nextBoolean()
                    && !this.pieceDefinition.hasEntrance[DOWN]
                    && !this.pieceDefinition.hasEntrance[UP]
                    && this.pieceDefinition.countEntrances() > 1;
            if (this.design == 0) {
                this.fillWithOutline(world, clip, 0, 1, 0, 2, 1, 2,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 0, 3, 0, 2, 3, 2,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 0, 2, 0, 0, 2, 2,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 1, 2, 0, 2, 2, 0,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.setBlockState(world, palette.seaLantern(), 1, 2, 1, clip);
                this.fillWithOutline(world, clip, 5, 1, 0, 7, 1, 2,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 5, 3, 0, 7, 3, 2,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 7, 2, 0, 7, 2, 2,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 5, 2, 0, 6, 2, 0,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.setBlockState(world, palette.seaLantern(), 6, 2, 1, clip);
                this.fillWithOutline(world, clip, 0, 1, 5, 2, 1, 7,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 0, 3, 5, 2, 3, 7,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 0, 2, 5, 0, 2, 7,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 1, 2, 7, 2, 2, 7,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.setBlockState(world, palette.seaLantern(), 1, 2, 6, clip);
                this.fillWithOutline(world, clip, 5, 1, 5, 7, 1, 7,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 5, 3, 5, 7, 3, 7,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 7, 2, 5, 7, 2, 7,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 5, 2, 7, 6, 2, 7,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.setBlockState(world, palette.seaLantern(), 6, 2, 6, clip);
                if (this.pieceDefinition.hasEntrance[SOUTH]) {
                    this.fillWithOutline(world, clip, 3, 3, 0, 4, 3, 0,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                } else {
                    this.fillWithOutline(world, clip, 3, 3, 0, 4, 3, 1,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 3, 2, 0, 4, 2, 0,
                            palette.prismarineRough(), palette.prismarineRough(), false);
                    this.fillWithOutline(world, clip, 3, 1, 0, 4, 1, 1,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                if (this.pieceDefinition.hasEntrance[NORTH]) {
                    this.fillWithOutline(world, clip, 3, 3, 7, 4, 3, 7,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                } else {
                    this.fillWithOutline(world, clip, 3, 3, 6, 4, 3, 7,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 3, 2, 7, 4, 2, 7,
                            palette.prismarineRough(), palette.prismarineRough(), false);
                    this.fillWithOutline(world, clip, 3, 1, 6, 4, 1, 7,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                if (this.pieceDefinition.hasEntrance[WEST]) {
                    this.fillWithOutline(world, clip, 0, 3, 3, 0, 3, 4,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                } else {
                    this.fillWithOutline(world, clip, 0, 3, 3, 1, 3, 4,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 0, 2, 3, 0, 2, 4,
                            palette.prismarineRough(), palette.prismarineRough(), false);
                    this.fillWithOutline(world, clip, 0, 1, 3, 1, 1, 4,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                if (this.pieceDefinition.hasEntrance[EAST]) {
                    this.fillWithOutline(world, clip, 7, 3, 3, 7, 3, 4,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                } else {
                    this.fillWithOutline(world, clip, 6, 3, 3, 7, 3, 4,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 7, 2, 3, 7, 2, 4,
                            palette.prismarineRough(), palette.prismarineRough(), false);
                    this.fillWithOutline(world, clip, 6, 1, 3, 7, 1, 4,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
            } else if (this.design == 1) {
                this.fillWithOutline(world, clip, 2, 1, 2, 2, 3, 2,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 2, 1, 5, 2, 3, 5,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 5, 1, 5, 5, 3, 5,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 5, 1, 2, 5, 3, 2,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.setBlockState(world, palette.seaLantern(), 2, 2, 2, clip);
                this.setBlockState(world, palette.seaLantern(), 2, 2, 5, clip);
                this.setBlockState(world, palette.seaLantern(), 5, 2, 5, clip);
                this.setBlockState(world, palette.seaLantern(), 5, 2, 2, clip);
                this.fillWithOutline(world, clip, 0, 1, 0, 1, 3, 0,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 0, 1, 1, 0, 3, 1,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 0, 1, 7, 1, 3, 7,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 0, 1, 6, 0, 3, 6,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 6, 1, 7, 7, 3, 7,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 7, 1, 6, 7, 3, 6,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 6, 1, 0, 7, 3, 0,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 7, 1, 1, 7, 3, 1,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.setBlockState(world, palette.prismarineRough(), 1, 2, 0, clip);
                this.setBlockState(world, palette.prismarineRough(), 0, 2, 1, clip);
                this.setBlockState(world, palette.prismarineRough(), 1, 2, 7, clip);
                this.setBlockState(world, palette.prismarineRough(), 0, 2, 6, clip);
                this.setBlockState(world, palette.prismarineRough(), 6, 2, 7, clip);
                this.setBlockState(world, palette.prismarineRough(), 7, 2, 6, clip);
                this.setBlockState(world, palette.prismarineRough(), 6, 2, 0, clip);
                this.setBlockState(world, palette.prismarineRough(), 7, 2, 1, clip);
                if (!this.pieceDefinition.hasEntrance[SOUTH]) {
                    this.fillWithOutline(world, clip, 1, 3, 0, 6, 3, 0,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 1, 2, 0, 6, 2, 0,
                            palette.prismarineRough(), palette.prismarineRough(), false);
                    this.fillWithOutline(world, clip, 1, 1, 0, 6, 1, 0,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                if (!this.pieceDefinition.hasEntrance[NORTH]) {
                    this.fillWithOutline(world, clip, 1, 3, 7, 6, 3, 7,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 1, 2, 7, 6, 2, 7,
                            palette.prismarineRough(), palette.prismarineRough(), false);
                    this.fillWithOutline(world, clip, 1, 1, 7, 6, 1, 7,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                if (!this.pieceDefinition.hasEntrance[WEST]) {
                    this.fillWithOutline(world, clip, 0, 3, 1, 0, 3, 6,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 0, 2, 1, 0, 2, 6,
                            palette.prismarineRough(), palette.prismarineRough(), false);
                    this.fillWithOutline(world, clip, 0, 1, 1, 0, 1, 6,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                if (!this.pieceDefinition.hasEntrance[EAST]) {
                    this.fillWithOutline(world, clip, 7, 3, 1, 7, 3, 6,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 7, 2, 1, 7, 2, 6,
                            palette.prismarineRough(), palette.prismarineRough(), false);
                    this.fillWithOutline(world, clip, 7, 1, 1, 7, 1, 6,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
            } else if (this.design == 2) {
                this.fillWithOutline(world, clip, 0, 1, 0, 0, 1, 7,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 7, 1, 0, 7, 1, 7,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 1, 1, 0, 6, 1, 0,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 1, 1, 7, 6, 1, 7,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 0, 2, 0, 0, 2, 7,
                        palette.prismarineDark(), palette.prismarineDark(), false);
                this.fillWithOutline(world, clip, 7, 2, 0, 7, 2, 7,
                        palette.prismarineDark(), palette.prismarineDark(), false);
                this.fillWithOutline(world, clip, 1, 2, 0, 6, 2, 0,
                        palette.prismarineDark(), palette.prismarineDark(), false);
                this.fillWithOutline(world, clip, 1, 2, 7, 6, 2, 7,
                        palette.prismarineDark(), palette.prismarineDark(), false);
                this.fillWithOutline(world, clip, 0, 3, 0, 0, 3, 7,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 7, 3, 0, 7, 3, 7,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 1, 3, 0, 6, 3, 0,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 1, 3, 7, 6, 3, 7,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 0, 1, 3, 0, 2, 4,
                        palette.prismarineDark(), palette.prismarineDark(), false);
                this.fillWithOutline(world, clip, 7, 1, 3, 7, 2, 4,
                        palette.prismarineDark(), palette.prismarineDark(), false);
                this.fillWithOutline(world, clip, 3, 1, 0, 4, 2, 0,
                        palette.prismarineDark(), palette.prismarineDark(), false);
                this.fillWithOutline(world, clip, 3, 1, 7, 4, 2, 7,
                        palette.prismarineDark(), palette.prismarineDark(), false);
                if (this.pieceDefinition.hasEntrance[SOUTH]) {
                    this.fillWater(world, clip, 3, 1, 0, 4, 2, 0, false);
                }
                if (this.pieceDefinition.hasEntrance[NORTH]) {
                    this.fillWater(world, clip, 3, 1, 7, 4, 2, 7, false);
                }
                if (this.pieceDefinition.hasEntrance[WEST]) {
                    this.fillWater(world, clip, 0, 1, 3, 0, 2, 4, false);
                }
                if (this.pieceDefinition.hasEntrance[EAST]) {
                    this.fillWater(world, clip, 7, 1, 3, 7, 2, 4, false);
                }
            }
            if (pillar) {
                this.fillWithOutline(world, clip, 3, 1, 3, 4, 1, 4,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 3, 2, 3, 4, 2, 4,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 3, 3, 3, 4, 3, 4,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
            }
            return true;
        }
    }

    /** The simple room fitter (SimpleRoomFitter lines 1704-1718). */
    private static final class SimpleRoomFitter implements RoomFitter {
        @Override
        public boolean fits(PieceDefinition pieceDefinition) {
            return true;
        }

        @Override
        public OceanMonumentPiece create(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            pieceDefinition.claimed = true;
            return new SimpleRoom(facing, pieceDefinition, random);
        }
    }

    /** The simple top room (SimpleTopRoom lines 1720-1801): the wet-sponge room. */
    static final class SimpleTopRoom extends OceanMonumentPiece {

        SimpleTopRoom(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 1, 1, 1);
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.pieceDefinition.index / 25 > 0) {
                this.generateFloor(world, clip, 0, 0, this.pieceDefinition.hasEntrance[DOWN]);
            }
            if (this.pieceDefinition.connections[UP] == null) {
                this.replaceFiller(world, clip, 1, 4, 1, 6, 4, 6, palette.prismarineRough());
            }
            for (int i = 1; i <= 6; i++) {
                for (int j = 1; j <= 6; j++) {
                    if (random.nextInt(3) != 0) {
                        int k = 2 + (random.nextInt(4) == 0 ? 0 : 1);
                        this.fillWithOutline(world, clip, i, k, j, i, 3, j,
                                palette.wetSponge(), palette.wetSponge(), false);
                    }
                }
            }
            this.fillWithOutline(world, clip, 0, 1, 0, 0, 1, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 7, 1, 0, 7, 1, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 1, 0, 6, 1, 0,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 1, 7, 6, 1, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 0, 2, 7,
                    palette.prismarineDark(), palette.prismarineDark(), false);
            this.fillWithOutline(world, clip, 7, 2, 0, 7, 2, 7,
                    palette.prismarineDark(), palette.prismarineDark(), false);
            this.fillWithOutline(world, clip, 1, 2, 0, 6, 2, 0,
                    palette.prismarineDark(), palette.prismarineDark(), false);
            this.fillWithOutline(world, clip, 1, 2, 7, 6, 2, 7,
                    palette.prismarineDark(), palette.prismarineDark(), false);
            this.fillWithOutline(world, clip, 0, 3, 0, 0, 3, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 7, 3, 0, 7, 3, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 3, 0, 6, 3, 0,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 3, 7, 6, 3, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 0, 1, 3, 0, 2, 4,
                    palette.prismarineDark(), palette.prismarineDark(), false);
            this.fillWithOutline(world, clip, 7, 1, 3, 7, 2, 4,
                    palette.prismarineDark(), palette.prismarineDark(), false);
            this.fillWithOutline(world, clip, 3, 1, 0, 4, 2, 0,
                    palette.prismarineDark(), palette.prismarineDark(), false);
            this.fillWithOutline(world, clip, 3, 1, 7, 4, 2, 7,
                    palette.prismarineDark(), palette.prismarineDark(), false);
            if (this.pieceDefinition.hasEntrance[SOUTH]) {
                this.fillWater(world, clip, 3, 1, 0, 4, 2, 0, false);
            }
            return true;
        }
    }

    /** The simple top room fitter (SimpleTopRoomFitter lines 1803-1819). */
    private static final class SimpleTopRoomFitter implements RoomFitter {
        @Override
        public boolean fits(PieceDefinition pieceDefinition) {
            return !pieceDefinition.hasEntrance[WEST]
                    && !pieceDefinition.hasEntrance[EAST]
                    && !pieceDefinition.hasEntrance[NORTH]
                    && !pieceDefinition.hasEntrance[SOUTH]
                    && !pieceDefinition.hasEntrance[UP];
        }

        @Override
        public OceanMonumentPiece create(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            pieceDefinition.claimed = true;
            return new SimpleTopRoom(facing, pieceDefinition, random);
        }
    }

    /** The double-width Z room (DoubleWidthZRoom lines 604-692) with its fitter. */
    static final class DoubleWidthZRoom extends OceanMonumentPiece {

        DoubleWidthZRoom(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 1, 1, 2);
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            PieceDefinition north = this.pieceDefinition.connections[NORTH];
            PieceDefinition here = this.pieceDefinition;
            if (this.pieceDefinition.index / 25 > 0) {
                this.generateFloor(world, clip, 0, 8, north.hasEntrance[DOWN]);
                this.generateFloor(world, clip, 0, 0, here.hasEntrance[DOWN]);
            }
            if (here.connections[UP] == null) {
                this.replaceFiller(world, clip, 1, 4, 1, 6, 4, 7, palette.prismarineRough());
            }
            if (north.connections[UP] == null) {
                this.replaceFiller(world, clip, 1, 4, 8, 6, 4, 14, palette.prismarineRough());
            }
            this.fillWithOutline(world, clip, 0, 3, 0, 0, 3, 15,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 7, 3, 0, 7, 3, 15,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 3, 0, 7, 3, 0,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 3, 15, 6, 3, 15,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 0, 2, 15,
                    palette.prismarineRough(), palette.prismarineRough(), false);
            this.fillWithOutline(world, clip, 7, 2, 0, 7, 2, 15,
                    palette.prismarineRough(), palette.prismarineRough(), false);
            this.fillWithOutline(world, clip, 1, 2, 0, 7, 2, 0,
                    palette.prismarineRough(), palette.prismarineRough(), false);
            this.fillWithOutline(world, clip, 1, 2, 15, 6, 2, 15,
                    palette.prismarineRough(), palette.prismarineRough(), false);
            this.fillWithOutline(world, clip, 0, 1, 0, 0, 1, 15,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 7, 1, 0, 7, 1, 15,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 1, 0, 7, 1, 0,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 1, 15, 6, 1, 15,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 1, 1, 1, 1, 2,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 6, 1, 1, 6, 1, 2,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 3, 1, 1, 3, 2,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 6, 3, 1, 6, 3, 2,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 1, 13, 1, 1, 14,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 6, 1, 13, 6, 1, 14,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 3, 13, 1, 3, 14,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 6, 3, 13, 6, 3, 14,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 2, 1, 6, 2, 3, 6,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 1, 6, 5, 3, 6,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 2, 1, 9, 2, 3, 9,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 1, 9, 5, 3, 9,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 3, 2, 6, 4, 2, 6,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 3, 2, 9, 4, 2, 9,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 2, 2, 7, 2, 2, 8,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 2, 7, 5, 2, 8,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.setBlockState(world, palette.seaLantern(), 2, 2, 5, clip);
            this.setBlockState(world, palette.seaLantern(), 5, 2, 5, clip);
            this.setBlockState(world, palette.seaLantern(), 2, 2, 10, clip);
            this.setBlockState(world, palette.seaLantern(), 5, 2, 10, clip);
            this.setBlockState(world, palette.prismarineBricks(), 2, 3, 5, clip);
            this.setBlockState(world, palette.prismarineBricks(), 5, 3, 5, clip);
            this.setBlockState(world, palette.prismarineBricks(), 2, 3, 10, clip);
            this.setBlockState(world, palette.prismarineBricks(), 5, 3, 10, clip);
            if (here.hasEntrance[SOUTH]) {
                this.fillWater(world, clip, 3, 1, 0, 4, 2, 0, false);
            }
            if (here.hasEntrance[EAST]) {
                this.fillWater(world, clip, 7, 1, 3, 7, 2, 4, false);
            }
            if (here.hasEntrance[WEST]) {
                this.fillWater(world, clip, 0, 1, 3, 0, 2, 4, false);
            }
            if (north.hasEntrance[NORTH]) {
                this.fillWater(world, clip, 3, 1, 15, 4, 2, 15, false);
            }
            if (north.hasEntrance[WEST]) {
                this.fillWater(world, clip, 0, 1, 11, 0, 2, 12, false);
            }
            if (north.hasEntrance[EAST]) {
                this.fillWater(world, clip, 7, 1, 11, 7, 2, 12, false);
            }
            return true;
        }
    }

    /** The double-width Z fitter (lines 694-712 — the SOUTH fallback). */
    private static final class DoubleWidthZRoomFitter implements RoomFitter {
        @Override
        public boolean fits(PieceDefinition pieceDefinition) {
            return pieceDefinition.hasEntrance[NORTH] && !pieceDefinition.connections[NORTH].claimed;
        }

        @Override
        public OceanMonumentPiece create(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            PieceDefinition target = pieceDefinition;
            if (!pieceDefinition.hasEntrance[NORTH] || pieceDefinition.connections[NORTH].claimed) {
                target = pieceDefinition.connections[SOUTH];
            }
            target.claimed = true;
            target.connections[NORTH].claimed = true;
            return new DoubleWidthZRoom(facing, target, random);
        }
    }

    /** The double-width X room (DoubleWidthXRoom lines 470-559). */
    static final class DoubleWidthXRoom extends OceanMonumentPiece {

        DoubleWidthXRoom(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 2, 1, 1);
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            PieceDefinition east = this.pieceDefinition.connections[EAST];
            PieceDefinition here = this.pieceDefinition;
            if (this.pieceDefinition.index / 25 > 0) {
                this.generateFloor(world, clip, 8, 0, east.hasEntrance[DOWN]);
                this.generateFloor(world, clip, 0, 0, here.hasEntrance[DOWN]);
            }
            if (here.connections[UP] == null) {
                this.replaceFiller(world, clip, 1, 4, 1, 7, 4, 6, palette.prismarineRough());
            }
            if (east.connections[UP] == null) {
                this.replaceFiller(world, clip, 8, 4, 1, 14, 4, 6, palette.prismarineRough());
            }
            this.fillWithOutline(world, clip, 0, 3, 0, 0, 3, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 15, 3, 0, 15, 3, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 3, 0, 15, 3, 0,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 3, 7, 14, 3, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 0, 2, 7,
                    palette.prismarineRough(), palette.prismarineRough(), false);
            this.fillWithOutline(world, clip, 15, 2, 0, 15, 2, 7,
                    palette.prismarineRough(), palette.prismarineRough(), false);
            this.fillWithOutline(world, clip, 1, 2, 0, 15, 2, 0,
                    palette.prismarineRough(), palette.prismarineRough(), false);
            this.fillWithOutline(world, clip, 1, 2, 7, 14, 2, 7,
                    palette.prismarineRough(), palette.prismarineRough(), false);
            this.fillWithOutline(world, clip, 0, 1, 0, 0, 1, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 15, 1, 0, 15, 1, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 1, 0, 15, 1, 0,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 1, 1, 7, 14, 1, 7,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 5, 1, 0, 10, 1, 4,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.fillWithOutline(world, clip, 6, 2, 0, 9, 2, 3,
                    palette.prismarineRough(), palette.prismarineRough(), false);
            this.fillWithOutline(world, clip, 5, 3, 0, 10, 3, 4,
                    palette.prismarineBricks(), palette.prismarineBricks(), false);
            this.setBlockState(world, palette.seaLantern(), 6, 2, 3, clip);
            this.setBlockState(world, palette.seaLantern(), 9, 2, 3, clip);
            if (here.hasEntrance[SOUTH]) {
                this.fillWater(world, clip, 3, 1, 0, 4, 2, 0, false);
            }
            if (here.hasEntrance[NORTH]) {
                this.fillWater(world, clip, 3, 1, 7, 4, 2, 7, false);
            }
            if (here.hasEntrance[WEST]) {
                this.fillWater(world, clip, 0, 1, 3, 0, 2, 4, false);
            }
            if (east.hasEntrance[SOUTH]) {
                this.fillWater(world, clip, 11, 1, 0, 12, 2, 0, false);
            }
            if (east.hasEntrance[NORTH]) {
                this.fillWater(world, clip, 11, 1, 7, 12, 2, 7, false);
            }
            if (east.hasEntrance[EAST]) {
                this.fillWater(world, clip, 15, 1, 3, 15, 2, 4, false);
            }
            return true;
        }
    }

    /** The double-width X fitter (lines 561-577). */
    private static final class DoubleWidthXRoomFitter implements RoomFitter {
        @Override
        public boolean fits(PieceDefinition pieceDefinition) {
            return pieceDefinition.hasEntrance[EAST] && !pieceDefinition.connections[EAST].claimed;
        }

        @Override
        public OceanMonumentPiece create(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            pieceDefinition.claimed = true;
            pieceDefinition.connections[EAST].claimed = true;
            return new DoubleWidthXRoom(facing, pieceDefinition, random);
        }
    }

    /** The double-height double-width Z room (DoubleHeightWidthZRoom lines 400-468). */
    static final class DoubleHeightWidthZRoom extends OceanMonumentPiece {

        DoubleHeightWidthZRoom(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            super(1, facing, pieceDefinition, 1, 2, 2);
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            PieceDefinition north = this.pieceDefinition.connections[NORTH];
            PieceDefinition here = this.pieceDefinition;
            PieceDefinition aboveNorth = north.connections[UP];
            PieceDefinition aboveHere = here.connections[UP];
            if (this.pieceDefinition.index / 25 > 0) {
                this.generateFloor(world, clip, 0, 8, north.hasEntrance[DOWN]);
                this.generateFloor(world, clip, 0, 0, here.hasEntrance[DOWN]);
            }
            if (aboveHere.connections[UP] == null) {
                this.replaceFiller(world, clip, 1, 8, 1, 6, 8, 7, palette.prismarineRough());
            }
            if (aboveNorth.connections[UP] == null) {
                this.replaceFiller(world, clip, 1, 8, 8, 6, 8, 14, palette.prismarineRough());
            }
            for (int i = 1; i <= 7; i++) {
                BlockType state = palette.prismarineBricks();
                if (i == 2 || i == 6) {
                    state = palette.prismarineRough();
                }
                this.fillWithOutline(world, clip, 0, i, 0, 0, i, 15, state, state, false);
                this.fillWithOutline(world, clip, 7, i, 0, 7, i, 15, state, state, false);
                this.fillWithOutline(world, clip, 1, i, 0, 6, i, 0, state, state, false);
                this.fillWithOutline(world, clip, 1, i, 15, 6, i, 15, state, state, false);
            }
            for (int j = 1; j <= 7; j++) {
                BlockType state = palette.prismarineDark();
                if (j == 2 || j == 6) {
                    state = palette.seaLantern();
                }
                this.fillWithOutline(world, clip, 3, j, 7, 4, j, 8, state, state, false);
            }
            if (here.hasEntrance[SOUTH]) {
                this.fillWater(world, clip, 3, 1, 0, 4, 2, 0, false);
            }
            if (here.hasEntrance[EAST]) {
                this.fillWater(world, clip, 7, 1, 3, 7, 2, 4, false);
            }
            if (here.hasEntrance[WEST]) {
                this.fillWater(world, clip, 0, 1, 3, 0, 2, 4, false);
            }
            if (north.hasEntrance[NORTH]) {
                this.fillWater(world, clip, 3, 1, 15, 4, 2, 15, false);
            }
            if (north.hasEntrance[WEST]) {
                this.fillWater(world, clip, 0, 1, 11, 0, 2, 12, false);
            }
            if (north.hasEntrance[EAST]) {
                this.fillWater(world, clip, 7, 1, 11, 7, 2, 12, false);
            }
            if (aboveHere.hasEntrance[SOUTH]) {
                this.fillWater(world, clip, 3, 5, 0, 4, 6, 0, false);
            }
            if (aboveHere.hasEntrance[EAST]) {
                this.fillWater(world, clip, 7, 5, 3, 7, 6, 4, false);
                this.fillWithOutline(world, clip, 5, 4, 2, 6, 4, 5,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 6, 1, 2, 6, 3, 2,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 6, 1, 5, 6, 3, 5,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
            }
            if (aboveHere.hasEntrance[WEST]) {
                this.fillWater(world, clip, 0, 5, 3, 0, 6, 4, false);
                this.fillWithOutline(world, clip, 1, 4, 2, 2, 4, 5,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 1, 1, 2, 1, 3, 2,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 1, 1, 5, 1, 3, 5,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
            }
            if (aboveNorth.hasEntrance[NORTH]) {
                this.fillWater(world, clip, 3, 5, 15, 4, 6, 15, false);
            }
            if (aboveNorth.hasEntrance[WEST]) {
                this.fillWater(world, clip, 0, 5, 11, 0, 6, 12, false);
                this.fillWithOutline(world, clip, 1, 4, 10, 2, 4, 13,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 1, 1, 10, 1, 3, 10,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 1, 1, 13, 1, 3, 13,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
            }
            if (aboveNorth.hasEntrance[EAST]) {
                this.fillWater(world, clip, 7, 5, 11, 7, 6, 12, false);
                this.fillWithOutline(world, clip, 5, 4, 10, 6, 4, 13,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 6, 1, 10, 6, 3, 10,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 6, 1, 13, 6, 3, 13,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
            }
            return true;
        }
    }

    /** The double-height double-width Z fitter (lines 470-496). */
    private static final class DoubleHeightWidthZRoomFitter implements RoomFitter {
        @Override
        public boolean fits(PieceDefinition pieceDefinition) {
            if (pieceDefinition.hasEntrance[NORTH]
                    && !pieceDefinition.connections[NORTH].claimed
                    && pieceDefinition.hasEntrance[UP]
                    && !pieceDefinition.connections[UP].claimed) {
                PieceDefinition north = pieceDefinition.connections[NORTH];
                return north.hasEntrance[UP] && !north.connections[UP].claimed;
            }
            return false;
        }

        @Override
        public OceanMonumentPiece create(StructureFacing facing, PieceDefinition pieceDefinition, Random random) {
            pieceDefinition.claimed = true;
            pieceDefinition.connections[NORTH].claimed = true;
            pieceDefinition.connections[UP].claimed = true;
            pieceDefinition.connections[NORTH].connections[UP].claimed = true;
            return new DoubleHeightWidthZRoom(facing, pieceDefinition, random);
        }
    }

    /** The wing room (WingRoom lines 1803-1898 region): the two elder-guardian attics. */
    static final class WingRoom extends OceanMonumentPiece {
        private final int design;

        WingRoom(StructureFacing facing, StructureBox bounds, int design) {
            super(facing, bounds);
            this.design = design & 1;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.design == 0) {
                for (int i = 0; i < 4; i++) {
                    this.fillWithOutline(world, clip, 10 - i, 3 - i, 20 - i, 12 + i, 3 - i, 20,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                this.fillWithOutline(world, clip, 7, 0, 6, 15, 0, 16,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 6, 0, 6, 6, 3, 20,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 16, 0, 6, 16, 3, 20,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 7, 1, 7, 7, 1, 20,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 15, 1, 7, 15, 1, 20,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 7, 1, 6, 9, 3, 6,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 13, 1, 6, 15, 3, 6,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 8, 1, 7, 9, 1, 7,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 13, 1, 7, 14, 1, 7,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 9, 0, 5, 13, 0, 5,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 10, 0, 7, 12, 0, 7,
                        palette.prismarineDark(), palette.prismarineDark(), false);
                this.fillWithOutline(world, clip, 8, 0, 10, 8, 0, 12,
                        palette.prismarineDark(), palette.prismarineDark(), false);
                this.fillWithOutline(world, clip, 14, 0, 10, 14, 0, 12,
                        palette.prismarineDark(), palette.prismarineDark(), false);
                for (int i1 = 18; i1 >= 7; i1 -= 3) {
                    this.setBlockState(world, palette.seaLantern(), 6, 3, i1, clip);
                    this.setBlockState(world, palette.seaLantern(), 16, 3, i1, clip);
                }
                this.setBlockState(world, palette.seaLantern(), 10, 0, 10, clip);
                this.setBlockState(world, palette.seaLantern(), 12, 0, 10, clip);
                this.setBlockState(world, palette.seaLantern(), 10, 0, 12, clip);
                this.setBlockState(world, palette.seaLantern(), 12, 0, 12, clip);
                this.setBlockState(world, palette.seaLantern(), 8, 3, 6, clip);
                this.setBlockState(world, palette.seaLantern(), 14, 3, 6, clip);
                this.setBlockState(world, palette.prismarineBricks(), 4, 2, 4, clip);
                this.setBlockState(world, palette.seaLantern(), 4, 1, 4, clip);
                this.setBlockState(world, palette.prismarineBricks(), 4, 0, 4, clip);
                this.setBlockState(world, palette.prismarineBricks(), 18, 2, 4, clip);
                this.setBlockState(world, palette.seaLantern(), 18, 1, 4, clip);
                this.setBlockState(world, palette.prismarineBricks(), 18, 0, 4, clip);
                this.setBlockState(world, palette.prismarineBricks(), 4, 2, 18, clip);
                this.setBlockState(world, palette.seaLantern(), 4, 1, 18, clip);
                this.setBlockState(world, palette.prismarineBricks(), 4, 0, 18, clip);
                this.setBlockState(world, palette.prismarineBricks(), 18, 2, 18, clip);
                this.setBlockState(world, palette.seaLantern(), 18, 1, 18, clip);
                this.setBlockState(world, palette.prismarineBricks(), 18, 0, 18, clip);
                this.setBlockState(world, palette.prismarineBricks(), 9, 7, 20, clip);
                this.setBlockState(world, palette.prismarineBricks(), 13, 7, 20, clip);
                this.fillWithOutline(world, clip, 6, 0, 21, 7, 4, 21,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 15, 0, 21, 16, 4, 21,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.spawnElderGuardian(world, clip, 11, 2, 16);
            } else if (this.design == 1) {
                this.fillWithOutline(world, clip, 9, 3, 18, 13, 3, 20,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 9, 0, 18, 9, 2, 18,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 13, 0, 18, 13, 2, 18,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                int column = 9;
                int z = 20;
                int y = 5;
                for (int l = 0; l < 2; l++) {
                    this.setBlockState(world, palette.prismarineBricks(), column, y + 1, z, clip);
                    this.setBlockState(world, palette.seaLantern(), column, y, z, clip);
                    this.setBlockState(world, palette.prismarineBricks(), column, y - 1, z, clip);
                    column = 13;
                }
                this.fillWithOutline(world, clip, 7, 3, 7, 15, 3, 14,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                int x = 10;
                for (int k1 = 0; k1 < 2; k1++) {
                    this.fillWithOutline(world, clip, x, 0, 10, x, 6, 10,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, x, 0, 12, x, 6, 12,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.setBlockState(world, palette.seaLantern(), x, 0, 10, clip);
                    this.setBlockState(world, palette.seaLantern(), x, 0, 12, clip);
                    this.setBlockState(world, palette.seaLantern(), x, 4, 10, clip);
                    this.setBlockState(world, palette.seaLantern(), x, 4, 12, clip);
                    x = 12;
                }
                int edge = 8;
                for (int l1 = 0; l1 < 2; l1++) {
                    this.fillWithOutline(world, clip, edge, 0, 7, edge, 2, 7,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, edge, 0, 14, edge, 2, 14,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    edge = 14;
                }
                this.fillWithOutline(world, clip, 8, 3, 8, 8, 3, 13,
                        palette.prismarineDark(), palette.prismarineDark(), false);
                this.fillWithOutline(world, clip, 14, 3, 8, 14, 3, 13,
                        palette.prismarineDark(), palette.prismarineDark(), false);
                this.spawnElderGuardian(world, clip, 11, 5, 13);
            }
            return true;
        }
    }

    /**
     * The master monument (OceanMonument lines 742-1240): the 58x23x58
     * whole-monument piece — the room-graph layout, the children assembly,
     * and the whole-monument postProcess (the water fill, the wings, the
     * entrance arch, the wall tiers, the roof, the base grid and the
     * corner fillets).
     */
    static final class OceanMonument extends OceanMonumentPiece {
        private PieceDefinition source;
        private PieceDefinition core;
        private final List<OceanMonumentPiece> children = new ArrayList<>();

        OceanMonument(Random random, int x, int z, StructureFacing facing) {
            super(0);
            this.facing = facing;
            // The reference's own anchor box (y 39..61 — the 23-tall body).
            this.bounds = new StructureBox(x, 39, z, x + 58 - 1, 61, z + 58 - 1);
            List<PieceDefinition> layout = this.createPieceLayout(random);
            this.source.claimed = true;
            this.children.add(new MonumentEntry(this.facing, this.source));
            this.children.add(new CoreRoom(this.facing, this.core, random));
            List<RoomFitter> fitters = new ArrayList<>();
            fitters.add(new DoubleHeightWidthXRoomFitter());
            fitters.add(new DoubleHeightWidthZRoomFitter());
            fitters.add(new DoubleWidthZRoomFitter());
            fitters.add(new DoubleWidthXRoomFitter());
            fitters.add(new DoubleHeightRoomFitter());
            fitters.add(new SimpleTopRoomFitter());
            fitters.add(new SimpleRoomFitter());
            for (PieceDefinition definition : layout) {
                if (!definition.claimed && !definition.isSpecial()) {
                    for (RoomFitter fitter : fitters) {
                        if (fitter.fits(definition)) {
                            this.children.add(fitter.create(this.facing, definition, random));
                            break;
                        }
                    }
                }
            }
            int baseY = this.bounds.minY;
            int offsetX = this.transformX(9, 22);
            int offsetZ = this.transformZ(9, 22);
            for (OceanMonumentPiece piece : this.children) {
                piece.getBounds().move(offsetX, baseY, offsetZ);
            }
            StructureBox leftWing = StructureBox.of(
                    this.transformX(1, 1), this.transformY(1), this.transformZ(1, 1),
                    this.transformX(23, 21), this.transformY(8), this.transformZ(23, 21));
            StructureBox rightWing = StructureBox.of(
                    this.transformX(34, 1), this.transformY(1), this.transformZ(34, 1),
                    this.transformX(56, 21), this.transformY(8), this.transformZ(56, 21));
            StructureBox penthouse = StructureBox.of(
                    this.transformX(22, 22), this.transformY(13), this.transformZ(22, 22),
                    this.transformX(35, 35), this.transformY(17), this.transformZ(35, 35));
            int design = random.nextInt();
            this.children.add(new WingRoom(this.facing, leftWing, design++));
            this.children.add(new WingRoom(this.facing, rightWing, design++));
            this.children.add(new Penthouse(this.facing, penthouse));
        }

        /**
         * The room-graph layout (createPieceLayout lines 824-1002): the
         * 75-cell lattice (the y0 floor ring, the y1 full ring, the y2
         * core ring), the connection walk, the three appended specials,
         * the core pick with its claim marks, the entrance update, the
         * shuffle, and the findSource entrance-removal pass.
         */
        private List<PieceDefinition> createPieceLayout(Random random) {
            PieceDefinition[] definitions = new PieceDefinition[75];
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 4; j++) {
                    definitions[pieceIndex(i, 0, j)] = new PieceDefinition(pieceIndex(i, 0, j));
                }
            }
            for (int i2 = 0; i2 < 5; i2++) {
                for (int l2 = 0; l2 < 4; l2++) {
                    definitions[pieceIndex(i2, 1, l2)] = new PieceDefinition(pieceIndex(i2, 1, l2));
                }
            }
            for (int j2 = 1; j2 < 4; j2++) {
                for (int i3 = 0; i3 < 2; i3++) {
                    definitions[pieceIndex(j2, 2, i3)] = new PieceDefinition(pieceIndex(j2, 2, i3));
                }
            }
            this.source = definitions[SOURCE_INDEX];
            for (int cellX = 0; cellX < 5; cellX++) {
                for (int cellZ = 0; cellZ < 5; cellZ++) {
                    for (int cellY = 0; cellY < 3; cellY++) {
                        int index = pieceIndex(cellX, cellY, cellZ);
                        if (definitions[index] != null) {
                            for (int dir = 0; dir < 6; dir++) {
                                int nx = cellX + OFFSET_X[dir];
                                int ny = cellY + OFFSET_Y[dir];
                                int nz = cellZ + OFFSET_Z[dir];
                                if (nx >= 0 && nx < 5 && nz >= 0 && nz < 5 && ny >= 0 && ny < 3) {
                                    int neighbor = pieceIndex(nx, ny, nz);
                                    if (definitions[neighbor] != null) {
                                        if (nz != cellZ) {
                                            definitions[index].updateConnection(OPPOSITE[dir], definitions[neighbor]);
                                        } else {
                                            definitions[index].updateConnection(dir, definitions[neighbor]);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            PieceDefinition topConnect = new PieceDefinition(1003);
            definitions[TOP_CONNECT_INDEX].updateConnection(UP, topConnect);
            PieceDefinition leftConnect = new PieceDefinition(1001);
            definitions[LEFT_CONNECT_INDEX].updateConnection(SOUTH, leftConnect);
            PieceDefinition rightConnect = new PieceDefinition(1002);
            definitions[RIGHT_CONNECT_INDEX].updateConnection(SOUTH, rightConnect);
            topConnect.claimed = true;
            leftConnect.claimed = true;
            rightConnect.claimed = true;
            this.source.source = true;
            this.core = definitions[pieceIndex(random.nextInt(4), 0, 2)];
            this.core.claimed = true;
            this.core.connections[EAST].claimed = true;
            this.core.connections[NORTH].claimed = true;
            this.core.connections[EAST].connections[NORTH].claimed = true;
            this.core.connections[UP].claimed = true;
            this.core.connections[EAST].connections[UP].claimed = true;
            this.core.connections[NORTH].connections[UP].claimed = true;
            this.core.connections[EAST].connections[NORTH].connections[UP].claimed = true;
            List<PieceDefinition> list = new ArrayList<>();
            for (PieceDefinition definition : definitions) {
                if (definition != null) {
                    definition.updateEntrances();
                    list.add(definition);
                }
            }
            topConnect.updateEntrances();
            Collections.shuffle(list, random);
            int scanIndex = 1;
            for (PieceDefinition definition : list) {
                int removed = 0;
                int tries = 0;
                while (removed < 2 && tries < 5) {
                    tries++;
                    int dir = random.nextInt(6);
                    if (definition.hasEntrance[dir]) {
                        int opposite = OPPOSITE[dir];
                        definition.hasEntrance[dir] = false;
                        definition.connections[dir].hasEntrance[opposite] = false;
                        if (definition.findSource(scanIndex++)
                                && definition.connections[dir].findSource(scanIndex++)) {
                            removed++;
                        } else {
                            definition.hasEntrance[dir] = true;
                            definition.connections[dir].hasEntrance[opposite] = true;
                        }
                    }
                }
            }
            list.add(topConnect);
            list.add(leftConnect);
            list.add(rightConnect);
            return list;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            int waterTop = Math.max(world.seaLevel(), 64) - this.bounds.minY;
            this.fillWater(world, clip, 0, 0, 0, 58, waterTop, 58, false);
            this.generateWing(false, 0, world, random, clip);
            this.generateWing(true, 33, world, random, clip);
            this.generateEntranceArch(world, random, clip);
            this.generateEntranceWall(world, random, clip);
            this.generateRoof(world, random, clip);
            this.generateLowerWall(world, random, clip);
            this.generateMiddleWall(world, random, clip);
            this.generateUpperWall(world, random, clip);
            // The base grid (lines 1097-1121): the 7x7 pillars.
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
                            this.setBlockState(world, palette.prismarineBricks(), l + j1, 0, i1 + k1, clip);
                            this.fillColumnDown(world, palette.prismarineBricks(), l + j1, -1, i1 + k1, clip);
                        }
                    }
                    if (j != 0 && j != 6) {
                        k += 6;
                    } else {
                        k++;
                    }
                }
            }
            // The corner fillets (lines 1123-1129): the five water rings.
            for (int l1 = 0; l1 < 5; l1++) {
                this.fillWater(world, clip, -1 - l1, 0 + l1 * 2, -1 - l1, -1 - l1, 23, 58 + l1, false);
                this.fillWater(world, clip, 58 + l1, 0 + l1 * 2, -1 - l1, 58 + l1, 23, 58 + l1, false);
                this.fillWater(world, clip, 0 - l1, 0 + l1 * 2, -1 - l1, 57 + l1, 23, -1 - l1, false);
                this.fillWater(world, clip, 0 - l1, 0 + l1 * 2, 58 + l1, 57 + l1, 23, 58 + l1, false);
            }
            for (OceanMonumentPiece piece : this.children) {
                if (piece.getBounds().intersects(clip)) {
                    piece.postProcess(world, random, clip);
                }
            }
            return true;
        }

        /** The wing pair (generateWing lines 1131-1178). */
        private void generateWing(boolean right, int x, StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.intersects(clip, x, 0, x + 23, 20)) {
                this.fillWithOutline(world, clip, x + 0, 0, 0, x + 24, 0, 20,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWater(world, clip, x + 0, 1, 0, x + 24, 10, 20, false);
                for (int j = 0; j < 4; j++) {
                    this.fillWithOutline(world, clip, x + j, j + 1, j, x + j, j + 1, 20,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, x + j + 7, j + 5, j + 7, x + j + 7, j + 5, 20,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, x + 17 - j, j + 5, j + 7, x + 17 - j, j + 5, 20,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, x + 24 - j, j + 1, j, x + 24 - j, j + 1, 20,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, x + j + 1, j + 1, j, x + 23 - j, j + 1, j,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, x + j + 8, j + 5, j + 7, x + 16 - j, j + 5, j + 7,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                this.fillWithOutline(world, clip, x + 4, 4, 4, x + 6, 4, 20,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, x + 7, 4, 4, x + 17, 4, 6,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, x + 18, 4, 4, x + 20, 4, 20,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, x + 11, 8, 11, x + 13, 8, 20,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.setBlockState(world, palette.prismarineBricks(), x + 12, 9, 12, clip);
                this.setBlockState(world, palette.prismarineBricks(), x + 12, 9, 15, clip);
                this.setBlockState(world, palette.prismarineBricks(), x + 12, 9, 18, clip);
                int j1 = right ? x + 19 : x + 5;
                int k = right ? x + 5 : x + 19;
                for (int l = 20; l >= 5; l -= 3) {
                    this.setBlockState(world, palette.prismarineBricks(), j1, 5, l, clip);
                }
                for (int k1 = 19; k1 >= 7; k1 -= 3) {
                    this.setBlockState(world, palette.prismarineBricks(), k, 5, k1, clip);
                }
                for (int l1 = 0; l1 < 4; l1++) {
                    int i1 = right ? x + (24 - (17 - l1 * 3)) : x + 17 - l1 * 3;
                    this.setBlockState(world, palette.prismarineBricks(), i1, 5, 5, clip);
                }
                this.setBlockState(world, palette.prismarineBricks(), k, 5, 5, clip);
                this.fillWithOutline(world, clip, x + 11, 1, 12, x + 13, 7, 12,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, x + 12, 1, 11, x + 12, 7, 13,
                        palette.prismarineRough(), palette.prismarineRough(), false);
            }
        }

        /** The entrance arch (generateEntranceArch lines 1180-1203). */
        private void generateEntranceArch(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.intersects(clip, 22, 5, 35, 17)) {
                this.fillWater(world, clip, 25, 0, 0, 32, 8, 20, false);
                for (int i = 0; i < 4; i++) {
                    this.fillWithOutline(world, clip, 24, 2, 5 + i * 4, 24, 4, 5 + i * 4,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 22, 4, 5 + i * 4, 23, 4, 5 + i * 4,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.setBlockState(world, palette.prismarineBricks(), 25, 5, 5 + i * 4, clip);
                    this.setBlockState(world, palette.prismarineBricks(), 26, 6, 5 + i * 4, clip);
                    this.setBlockState(world, palette.seaLantern(), 26, 5, 5 + i * 4, clip);
                    this.fillWithOutline(world, clip, 33, 2, 5 + i * 4, 33, 4, 5 + i * 4,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 34, 4, 5 + i * 4, 35, 4, 5 + i * 4,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.setBlockState(world, palette.prismarineBricks(), 32, 5, 5 + i * 4, clip);
                    this.setBlockState(world, palette.prismarineBricks(), 31, 6, 5 + i * 4, clip);
                    this.setBlockState(world, palette.seaLantern(), 31, 5, 5 + i * 4, clip);
                    this.fillWithOutline(world, clip, 27, 6, 5 + i * 4, 30, 6, 5 + i * 4,
                            palette.prismarineRough(), palette.prismarineRough(), false);
                }
            }
        }

        /** The entrance wall (generateEntranceWall lines 1205-1265). */
        private void generateEntranceWall(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.intersects(clip, 15, 20, 42, 21)) {
                this.fillWithOutline(world, clip, 15, 0, 21, 42, 0, 21,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWater(world, clip, 26, 1, 21, 31, 3, 21, false);
                this.fillWithOutline(world, clip, 21, 12, 21, 36, 12, 21,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 17, 11, 21, 40, 11, 21,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 16, 10, 21, 41, 10, 21,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 15, 7, 21, 42, 9, 21,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 16, 6, 21, 41, 6, 21,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 17, 5, 21, 40, 5, 21,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 21, 4, 21, 36, 4, 21,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 22, 3, 21, 26, 3, 21,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 31, 3, 21, 35, 3, 21,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 23, 2, 21, 25, 2, 21,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 32, 2, 21, 34, 2, 21,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 28, 4, 20, 29, 4, 21,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.setBlockState(world, palette.prismarineBricks(), 27, 3, 21, clip);
                this.setBlockState(world, palette.prismarineBricks(), 30, 3, 21, clip);
                this.setBlockState(world, palette.prismarineBricks(), 26, 2, 21, clip);
                this.setBlockState(world, palette.prismarineBricks(), 31, 2, 21, clip);
                this.setBlockState(world, palette.prismarineBricks(), 25, 1, 21, clip);
                this.setBlockState(world, palette.prismarineBricks(), 32, 1, 21, clip);
                for (int i = 0; i < 7; i++) {
                    this.setBlockState(world, palette.prismarineDark(), 28 - i, 6 + i, 21, clip);
                    this.setBlockState(world, palette.prismarineDark(), 29 + i, 6 + i, 21, clip);
                }
                for (int j = 0; j < 4; j++) {
                    this.setBlockState(world, palette.prismarineDark(), 28 - j, 9 + j, 21, clip);
                    this.setBlockState(world, palette.prismarineDark(), 29 + j, 9 + j, 21, clip);
                }
                this.setBlockState(world, palette.prismarineDark(), 28, 12, 21, clip);
                this.setBlockState(world, palette.prismarineDark(), 29, 12, 21, clip);
                for (int k = 0; k < 3; k++) {
                    this.setBlockState(world, palette.prismarineDark(), 22 - k * 2, 8, 21, clip);
                    this.setBlockState(world, palette.prismarineDark(), 22 - k * 2, 9, 21, clip);
                    this.setBlockState(world, palette.prismarineDark(), 35 + k * 2, 8, 21, clip);
                    this.setBlockState(world, palette.prismarineDark(), 35 + k * 2, 9, 21, clip);
                }
                this.fillWater(world, clip, 15, 13, 21, 42, 15, 21, false);
                this.fillWater(world, clip, 15, 1, 21, 15, 6, 21, false);
                this.fillWater(world, clip, 16, 1, 21, 16, 5, 21, false);
                this.fillWater(world, clip, 17, 1, 21, 20, 4, 21, false);
                this.fillWater(world, clip, 21, 1, 21, 21, 3, 21, false);
                this.fillWater(world, clip, 22, 1, 21, 22, 2, 21, false);
                this.fillWater(world, clip, 23, 1, 21, 24, 1, 21, false);
                this.fillWater(world, clip, 42, 1, 21, 42, 6, 21, false);
                this.fillWater(world, clip, 41, 1, 21, 41, 5, 21, false);
                this.fillWater(world, clip, 37, 1, 21, 40, 4, 21, false);
                this.fillWater(world, clip, 36, 1, 21, 36, 3, 21, false);
                this.fillWater(world, clip, 33, 1, 21, 34, 1, 21, false);
                this.fillWater(world, clip, 35, 1, 21, 35, 2, 21, false);
            }
        }

        /** The roof (generateRoof lines 1267-1317). */
        private void generateRoof(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.intersects(clip, 21, 21, 36, 36)) {
                this.fillWithOutline(world, clip, 21, 0, 22, 36, 0, 36,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWater(world, clip, 21, 1, 22, 36, 23, 36, false);
                for (int i = 0; i < 4; i++) {
                    this.fillWithOutline(world, clip, 21 + i, 13 + i, 21 + i, 36 - i, 13 + i, 21 + i,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 21 + i, 13 + i, 36 - i, 36 - i, 13 + i, 36 - i,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 21 + i, 13 + i, 22 + i, 21 + i, 13 + i, 35 - i,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                    this.fillWithOutline(world, clip, 36 - i, 13 + i, 22 + i, 36 - i, 13 + i, 35 - i,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                this.fillWithOutline(world, clip, 25, 16, 25, 32, 16, 32,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 25, 17, 25, 25, 19, 25,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 32, 17, 25, 32, 19, 25,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 25, 17, 32, 25, 19, 32,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 32, 17, 32, 32, 19, 32,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.setBlockState(world, palette.prismarineBricks(), 26, 20, 26, clip);
                this.setBlockState(world, palette.prismarineBricks(), 27, 21, 27, clip);
                this.setBlockState(world, palette.seaLantern(), 27, 20, 27, clip);
                this.setBlockState(world, palette.prismarineBricks(), 26, 20, 31, clip);
                this.setBlockState(world, palette.prismarineBricks(), 27, 21, 30, clip);
                this.setBlockState(world, palette.seaLantern(), 27, 20, 30, clip);
                this.setBlockState(world, palette.prismarineBricks(), 31, 20, 31, clip);
                this.setBlockState(world, palette.prismarineBricks(), 30, 21, 30, clip);
                this.setBlockState(world, palette.seaLantern(), 30, 20, 30, clip);
                this.setBlockState(world, palette.prismarineBricks(), 31, 20, 26, clip);
                this.setBlockState(world, palette.prismarineBricks(), 30, 21, 27, clip);
                this.setBlockState(world, palette.seaLantern(), 30, 20, 27, clip);
                this.fillWithOutline(world, clip, 28, 21, 27, 29, 21, 27,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 27, 21, 28, 27, 21, 29,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 28, 21, 30, 29, 21, 30,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 30, 21, 28, 30, 21, 29,
                        palette.prismarineRough(), palette.prismarineRough(), false);
            }
        }

        /** The lower wall (generateLowerWall lines 1319-1369). */
        private void generateLowerWall(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.intersects(clip, 0, 21, 6, 58)) {
                this.fillWithOutline(world, clip, 0, 0, 21, 6, 0, 57,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWater(world, clip, 0, 1, 21, 6, 7, 57, false);
                this.fillWithOutline(world, clip, 4, 4, 21, 6, 4, 53,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                for (int i = 0; i < 4; i++) {
                    this.fillWithOutline(world, clip, i, i + 1, 21, i, i + 1, 57 - i,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                for (int j = 23; j < 53; j += 3) {
                    this.setBlockState(world, palette.prismarineBricks(), 5, 5, j, clip);
                }
                this.setBlockState(world, palette.prismarineBricks(), 5, 5, 52, clip);
                for (int k = 0; k < 4; k++) {
                    this.fillWithOutline(world, clip, k, k + 1, 21, k, k + 1, 57 - k,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                this.fillWithOutline(world, clip, 4, 1, 52, 6, 3, 52,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 5, 1, 51, 5, 3, 53,
                        palette.prismarineRough(), palette.prismarineRough(), false);
            }
            if (this.intersects(clip, 51, 21, 58, 58)) {
                this.fillWithOutline(world, clip, 51, 0, 21, 57, 0, 57,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWater(world, clip, 51, 1, 21, 57, 7, 57, false);
                this.fillWithOutline(world, clip, 51, 4, 21, 53, 4, 53,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                for (int l = 0; l < 4; l++) {
                    this.fillWithOutline(world, clip, 57 - l, l + 1, 21, 57 - l, l + 1, 57 - l,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                for (int i1 = 23; i1 < 53; i1 += 3) {
                    this.setBlockState(world, palette.prismarineBricks(), 52, 5, i1, clip);
                }
                this.setBlockState(world, palette.prismarineBricks(), 52, 5, 52, clip);
                this.fillWithOutline(world, clip, 51, 1, 52, 53, 3, 52,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 52, 1, 51, 52, 3, 53,
                        palette.prismarineRough(), palette.prismarineRough(), false);
            }
            if (this.intersects(clip, 0, 51, 57, 57)) {
                this.fillWithOutline(world, clip, 7, 0, 51, 50, 0, 57,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWater(world, clip, 7, 1, 51, 50, 10, 57, false);
                for (int j1 = 0; j1 < 4; j1++) {
                    this.fillWithOutline(world, clip, j1 + 1, j1 + 1, 57 - j1, 56 - j1, j1 + 1, 57 - j1,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
            }
        }

        /** The middle wall (generateMiddleWall lines 1371-1432). */
        private void generateMiddleWall(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.intersects(clip, 7, 21, 13, 50)) {
                this.fillWithOutline(world, clip, 7, 0, 21, 13, 0, 50,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWater(world, clip, 7, 1, 21, 13, 10, 50, false);
                this.fillWithOutline(world, clip, 11, 8, 21, 13, 8, 53,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                for (int i = 0; i < 4; i++) {
                    this.fillWithOutline(world, clip, i + 7, i + 5, 21, i + 7, i + 5, 54,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                for (int j = 21; j <= 45; j += 3) {
                    this.setBlockState(world, palette.prismarineBricks(), 12, 9, j, clip);
                }
            }
            if (this.intersects(clip, 44, 21, 50, 54)) {
                this.fillWithOutline(world, clip, 44, 0, 21, 50, 0, 50,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWater(world, clip, 44, 1, 21, 50, 10, 50, false);
                this.fillWithOutline(world, clip, 44, 8, 21, 46, 8, 53,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                for (int k = 0; k < 4; k++) {
                    this.fillWithOutline(world, clip, 50 - k, k + 5, 21, 50 - k, k + 5, 54,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                for (int l = 21; l <= 45; l += 3) {
                    this.setBlockState(world, palette.prismarineBricks(), 45, 9, l, clip);
                }
            }
            if (this.intersects(clip, 8, 44, 49, 54)) {
                this.fillWithOutline(world, clip, 14, 0, 44, 43, 0, 50,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWater(world, clip, 14, 1, 44, 43, 10, 50, false);
                for (int i1 = 12; i1 <= 45; i1 += 3) {
                    this.setBlockState(world, palette.prismarineBricks(), i1, 9, 45, clip);
                    this.setBlockState(world, palette.prismarineBricks(), i1, 9, 52, clip);
                    if (i1 == 12 || i1 == 18 || i1 == 24 || i1 == 33 || i1 == 39 || i1 == 45) {
                        this.setBlockState(world, palette.prismarineBricks(), i1, 9, 47, clip);
                        this.setBlockState(world, palette.prismarineBricks(), i1, 9, 50, clip);
                        this.setBlockState(world, palette.prismarineBricks(), i1, 10, 45, clip);
                        this.setBlockState(world, palette.prismarineBricks(), i1, 10, 46, clip);
                        this.setBlockState(world, palette.prismarineBricks(), i1, 10, 51, clip);
                        this.setBlockState(world, palette.prismarineBricks(), i1, 10, 52, clip);
                        this.setBlockState(world, palette.prismarineBricks(), i1, 11, 47, clip);
                        this.setBlockState(world, palette.prismarineBricks(), i1, 11, 50, clip);
                        this.setBlockState(world, palette.prismarineBricks(), i1, 12, 48, clip);
                        this.setBlockState(world, palette.prismarineBricks(), i1, 12, 49, clip);
                    }
                }
                for (int j1 = 0; j1 < 3; j1++) {
                    this.fillWithOutline(world, clip, 8 + j1, 5 + j1, 54, 49 - j1, 5 + j1, 54,
                            palette.prismarineRough(), palette.prismarineRough(), false);
                }
                this.fillWithOutline(world, clip, 11, 8, 54, 46, 8, 54,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                this.fillWithOutline(world, clip, 14, 8, 44, 43, 8, 53,
                        palette.prismarineRough(), palette.prismarineRough(), false);
            }
        }

        /** The upper wall (generateUpperWall lines 1434-1488). */
        private void generateUpperWall(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.intersects(clip, 14, 21, 20, 43)) {
                this.fillWithOutline(world, clip, 14, 0, 21, 20, 0, 43,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWater(world, clip, 14, 1, 22, 20, 14, 43, false);
                this.fillWithOutline(world, clip, 18, 12, 22, 20, 12, 39,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 18, 12, 21, 20, 12, 21,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                for (int i = 0; i < 4; i++) {
                    this.fillWithOutline(world, clip, i + 14, i + 9, 21, i + 14, i + 9, 43 - i,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                for (int j = 23; j <= 39; j += 3) {
                    this.setBlockState(world, palette.prismarineBricks(), 19, 13, j, clip);
                }
            }
            if (this.intersects(clip, 37, 21, 43, 43)) {
                this.fillWithOutline(world, clip, 37, 0, 21, 43, 0, 43,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWater(world, clip, 37, 1, 22, 43, 14, 43, false);
                this.fillWithOutline(world, clip, 37, 12, 22, 39, 12, 39,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWithOutline(world, clip, 37, 12, 21, 39, 12, 21,
                        palette.prismarineBricks(), palette.prismarineBricks(), false);
                for (int k = 0; k < 4; k++) {
                    this.fillWithOutline(world, clip, 43 - k, k + 9, 21, 43 - k, k + 9, 43 - k,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                for (int l = 23; l <= 39; l += 3) {
                    this.setBlockState(world, palette.prismarineBricks(), 38, 13, l, clip);
                }
            }
            if (this.intersects(clip, 15, 37, 42, 43)) {
                this.fillWithOutline(world, clip, 21, 0, 37, 36, 0, 43,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                this.fillWater(world, clip, 21, 1, 37, 36, 14, 43, false);
                this.fillWithOutline(world, clip, 21, 12, 37, 36, 12, 39,
                        palette.prismarineRough(), palette.prismarineRough(), false);
                for (int i1 = 0; i1 < 4; i1++) {
                    this.fillWithOutline(world, clip, 15 + i1, i1 + 9, 43 - i1, 42 - i1, i1 + 9, 43 - i1,
                            palette.prismarineBricks(), palette.prismarineBricks(), false);
                }
                for (int j1 = 21; j1 <= 36; j1 += 3) {
                    this.setBlockState(world, palette.prismarineBricks(), j1, 13, 38, clip);
                }
            }
        }
    }
}
