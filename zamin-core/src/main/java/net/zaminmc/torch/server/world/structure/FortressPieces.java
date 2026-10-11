package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The fortress piece family (Slice 12d — the reference port of
 * gen/structure/FortressPieces.java, 1:1 over the Slice 12a foundation):
 * the two weight tables (the bridge and the castle walks) behind the
 * connector-repeat rule, the BridgeEnd fallback, the fourteen piece
 * families, and the loot table.
 *
 * <p><b>The architectural decision</b> (the stronghold's own envelope): the
 * reference's per-stronghold weight state is per-<em>start</em> instance
 * state in the engine — held by {@link FortressStart} — so the parallel
 * chunk pipeline's per-chunk recomputation never shares mutable
 * statics.</p>
 *
 * <p><b>Ledgered</b> (the reference's arms the engine lacks the systems
 * for): the blaze spawner's block entity (the block lands; the spawning
 * logic + the fortress's own monster spawn table — Blaze 10/2/3, the
 * zombie pigman 5/4/4, the skeleton 10/4/4, the magma cube 3/4/4 — ride
 * the mob-spawner/natural-spawn slices), and the castle entrance's
 * populate-time lava tick (the engine's lava is the source-block
 * stand-in, exactly like the blacksmith's pools — no update arms run at
 * populate time).</p>
 */
public final class FortressPieces {

    private FortressPieces() {
    }

    // ------------------------------------------------------------ the loot

    /**
     * The fortress loot table (FortressPiece.LOOT_ENTRIES lines 557-570 —
     * 12 rows, weight 78; the reference's metadata column all zeros).
     */
    static final List<StrongholdPieces.LootEntry> LOOT_ENTRIES = List.of(
            new StrongholdPieces.LootEntry("minecraft:diamond", 1, 3, 5),
            new StrongholdPieces.LootEntry("minecraft:iron_ingot", 1, 5, 5),
            new StrongholdPieces.LootEntry("minecraft:gold_ingot", 1, 3, 15),
            new StrongholdPieces.LootEntry("minecraft:golden_sword", 1, 1, 5),
            new StrongholdPieces.LootEntry("minecraft:golden_chestplate", 1, 1, 5),
            new StrongholdPieces.LootEntry("minecraft:flint_and_steel", 1, 1, 5),
            new StrongholdPieces.LootEntry("minecraft:nether_wart", 3, 7, 5),
            new StrongholdPieces.LootEntry("minecraft:saddle", 1, 1, 10),
            new StrongholdPieces.LootEntry("minecraft:golden_horse_armor", 1, 1, 8),
            new StrongholdPieces.LootEntry("minecraft:iron_horse_armor", 1, 1, 5),
            new StrongholdPieces.LootEntry("minecraft:diamond_horse_armor", 1, 1, 3),
            new StrongholdPieces.LootEntry("minecraft:obsidian", 2, 4, 2));

    // ------------------------------------------------------------ the weights

    /**
     * The piece kinds — the two tables (BRIDGE_PIECE_WEIGHTS lines 20-27 +
     * CASTLE_PIECE_WEIGHTS lines 28-36, the reference's own order): weight,
     * the max-amount cap, and the connector flag (the bridge's StraightBridge
     * + the castle's SmallCorridor/StairsCorridor may repeat back-to-back).
     */
    enum PieceKind {
        STRAIGHT_BRIDGE(30, 0, true),
        BRIDGE_CROSSING(10, 4, false),
        LARGE_CROSSING(10, 4, false),
        BRIDGE_STAIRS(10, 3, false),
        BLAZE_SPAWNER(5, 2, false),
        CASTLE_ENTRANCE(5, 1, false),
        SMALL_CORRIDOR(25, 0, true),
        SMALL_CORRIDOR_CROSSING(15, 5, false),
        SMALL_CORRIDOR_RIGHT_TURN(5, 10, false),
        SMALL_CORRIDOR_LEFT_TURN(5, 10, false),
        STAIRS_CORRIDOR(10, 3, true),
        CORRIDOR_BALCONY(7, 2, false),
        NETHERWART_FARM(5, 2, false);

        final int weight;
        final int maxAmount;
        final boolean connector;

        PieceKind(int weight, int maxAmount, boolean connector) {
            this.weight = weight;
            this.maxAmount = maxAmount;
            this.connector = connector;
        }
    }

    /** One per-start weight row (FortressPieceWeight lines 785-805). */
    static final class PieceWeight {
        final PieceKind kind;
        int amountGenerated;

        PieceWeight(PieceKind kind) {
            this.kind = kind;
        }

        boolean isValid() {
            return this.kind.maxAmount == 0 || this.amountGenerated < this.kind.maxAmount;
        }
    }

    /** The per-start weight state (the bridge + the castle tables). */
    static final class WalkState {
        final List<PieceWeight> bridgePieces = new ArrayList<>();
        final List<PieceWeight> castlePieces = new ArrayList<>();
        PieceWeight previous;

        WalkState() {
            for (PieceKind kind : new PieceKind[]{PieceKind.STRAIGHT_BRIDGE, PieceKind.BRIDGE_CROSSING,
                    PieceKind.LARGE_CROSSING, PieceKind.BRIDGE_STAIRS, PieceKind.BLAZE_SPAWNER,
                    PieceKind.CASTLE_ENTRANCE}) {
                this.bridgePieces.add(new PieceWeight(kind));
            }
            for (PieceKind kind : new PieceKind[]{PieceKind.SMALL_CORRIDOR, PieceKind.SMALL_CORRIDOR_CROSSING,
                    PieceKind.SMALL_CORRIDOR_RIGHT_TURN, PieceKind.SMALL_CORRIDOR_LEFT_TURN,
                    PieceKind.STAIRS_CORRIDOR, PieceKind.CORRIDOR_BALCONY, PieceKind.NETHERWART_FARM}) {
                this.castlePieces.add(new PieceWeight(kind));
            }
        }
    }

    // -------------------------------------------------------- the piece base

    /**
     * The fortress piece base (FortressPiece lines 552-783): the loot
     * table, the weight walk with the connector-repeat rule and the
     * BridgeEnd fallback, and the four generation arms (forward/left/right
     * with the castle-table flag — the left/right pair keeps the
     * reference's {@code (yOffset, offset)} order).
     */
    abstract static class FortressPiece extends StructurePiece {

        protected FortressPiece(int generationDepth) {
            super(generationDepth);
        }

        /**
         * getTotalWeight lines 559-576: the total of every row, or -1 when
         * no row can still generate (the walk's stop signal — the sentinel,
         * not the stronghold's boolean).
         */
        private static int getTotalWeight(List<PieceWeight> pieceWeights) {
            boolean anyLeft = false;
            int total = 0;
            for (PieceWeight weight : pieceWeights) {
                if (weight.kind.maxAmount > 0 && weight.amountGenerated < weight.kind.maxAmount) {
                    anyLeft = true;
                }
                total += weight.kind.weight;
            }
            return anyLeft ? total : -1;
        }

        /**
         * generateNextPiece lines 578-620: the five-try weighted walk over
         * one table — the connector-repeat rule (a non-connector row may
         * not follow itself), the depth-30 cap, the BridgeEnd fallback.
         */
        private static StructurePiece generateNextPiece(FortressStart startPiece,
                                                        List<PieceWeight> weights,
                                                        List<StructurePiece> pieces, Random random,
                                                        int x, int y, int z, StructureFacing facing,
                                                        int generationDepth) {
            int total = getTotalWeight(weights);
            boolean running = total > 0 && generationDepth <= 30;
            int tries = 0;
            while (tries < 5 && running) {
                tries++;
                int pick = random.nextInt(total);
                for (PieceWeight weight : weights) {
                    pick -= weight.kind.weight;
                    if (pick < 0) {
                        if (!weight.isValid()
                                || weight == startPiece.walk.previous && !weight.kind.connector) {
                            break;
                        }
                        FortressPiece piece = createPiece(weight.kind, pieces, random, x, y, z,
                                facing, generationDepth);
                        if (piece != null) {
                            weight.amountGenerated++;
                            startPiece.walk.previous = weight;
                            if (!weight.isValid()) {
                                weights.remove(weight);
                            }
                            return piece;
                        }
                    }
                }
            }
            return BridgeEnd.of(pieces, random, x, y, z, facing, generationDepth);
        }

        /**
         * The leash walk (generateNextPiece lines 622-650): the ±112-block
         * leash from the start's corner, the table choice (the bridge or
         * the castle), then the piece joins BOTH lists — the out-of-leash
         * BridgeEnd returns WITHOUT joining (the reference's own shape).
         */
        static StructurePiece generateNextPiece(FortressStart startPiece,
                                                List<StructurePiece> pieces, Random random,
                                                int x, int y, int z, StructureFacing facing,
                                                int generationDepth, boolean pickCastlePiece) {
            if (Math.abs(x - startPiece.getBounds().minX) <= 112
                    && Math.abs(z - startPiece.getBounds().minZ) <= 112) {
                List<PieceWeight> weights = pickCastlePiece
                        ? startPiece.walk.castlePieces : startPiece.walk.bridgePieces;
                StructurePiece piece = generateNextPiece(startPiece, weights, pieces, random,
                        x, y, z, facing, generationDepth + 1);
                if (piece != null) {
                    pieces.add(piece);
                    startPiece.children.add(piece);
                }
                return piece;
            }
            return BridgeEnd.of(pieces, random, x, y, z, facing, generationDepth);
        }

        /** createPiece lines 58-112: the kind dispatch to the of() factories. */
        private static FortressPiece createPiece(PieceKind kind, List<StructurePiece> pieces,
                                                 Random random, int x, int y, int z,
                                                 StructureFacing facing, int generationDepth) {
            return switch (kind) {
                case STRAIGHT_BRIDGE -> StraightBridge.of(pieces, random, x, y, z, facing, generationDepth);
                case BRIDGE_CROSSING -> BridgeCrossing.of(pieces, random, x, y, z, facing, generationDepth);
                case LARGE_CROSSING -> LargeCrossing.of(pieces, random, x, y, z, facing, generationDepth);
                case BRIDGE_STAIRS -> BridgeStairs.of(pieces, random, x, y, z, generationDepth, facing);
                case BLAZE_SPAWNER -> BlazeSpawner.of(pieces, random, x, y, z, generationDepth, facing);
                case CASTLE_ENTRANCE -> CastleEntrance.of(pieces, random, x, y, z, facing, generationDepth);
                case SMALL_CORRIDOR -> SmallCorridor.of(pieces, random, x, y, z, facing, generationDepth);
                case SMALL_CORRIDOR_CROSSING -> SmallCorridorCrossing.of(pieces, random, x, y, z, facing, generationDepth);
                case SMALL_CORRIDOR_RIGHT_TURN -> SmallCorridorRightTurn.of(pieces, random, x, y, z, facing, generationDepth);
                case SMALL_CORRIDOR_LEFT_TURN -> SmallCorridorLeftTurn.of(pieces, random, x, y, z, facing, generationDepth);
                case STAIRS_CORRIDOR -> StairsCorridor.of(pieces, random, x, y, z, facing, generationDepth);
                case CORRIDOR_BALCONY -> CorridorBalcony.of(pieces, random, x, y, z, facing, generationDepth);
                case NETHERWART_FARM -> NetherwartFarm.of(pieces, random, x, y, z, facing, generationDepth);
            };
        }

        // ------------------------------------------------ the generation arms

        /** generatePieceForward lines 652-700: (offset, yOffset, castle). */
        StructurePiece generatePieceForward(FortressStart startPiece, List<StructurePiece> pieces,
                                            Random random, int offset, int yOffset,
                                            boolean pickCastlePiece) {
            if (this.facing == null) {
                return null;
            }
            return switch (this.facing) {
                case NORTH -> generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.minZ - 1,
                        this.facing, this.getGenerationDepth(), pickCastlePiece);
                case SOUTH -> generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.maxZ + 1,
                        this.facing, this.getGenerationDepth(), pickCastlePiece);
                case WEST -> generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX - 1, this.bounds.minY + yOffset, this.bounds.minZ + offset,
                        this.facing, this.getGenerationDepth(), pickCastlePiece);
                case EAST -> generateNextPiece(startPiece, pieces, random,
                        this.bounds.maxX + 1, this.bounds.minY + yOffset, this.bounds.minZ + offset,
                        this.facing, this.getGenerationDepth(), pickCastlePiece);
            };
        }

        /** generatePieceLeft lines 702-745: (yOffset, offset, castle). */
        StructurePiece generatePieceLeft(FortressStart startPiece, List<StructurePiece> pieces,
                                         Random random, int yOffset, int offset,
                                         boolean pickCastlePiece) {
            if (this.facing == null) {
                return null;
            }
            return switch (this.facing) {
                case NORTH -> generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX - 1, this.bounds.minY + yOffset, this.bounds.minZ + offset,
                        StructureFacing.WEST, this.getGenerationDepth(), pickCastlePiece);
                case SOUTH -> generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX - 1, this.bounds.minY + yOffset, this.bounds.minZ + offset,
                        StructureFacing.WEST, this.getGenerationDepth(), pickCastlePiece);
                case WEST -> generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.minZ - 1,
                        StructureFacing.NORTH, this.getGenerationDepth(), pickCastlePiece);
                case EAST -> generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.minZ - 1,
                        StructureFacing.NORTH, this.getGenerationDepth(), pickCastlePiece);
            };
        }

        /** generatePieceRight lines 747-781: (yOffset, offset, castle). */
        StructurePiece generatePieceRight(FortressStart startPiece, List<StructurePiece> pieces,
                                          Random random, int yOffset, int offset,
                                          boolean pickCastlePiece) {
            if (this.facing == null) {
                return null;
            }
            return switch (this.facing) {
                case NORTH -> generateNextPiece(startPiece, pieces, random,
                        this.bounds.maxX + 1, this.bounds.minY + yOffset, this.bounds.minZ + offset,
                        StructureFacing.EAST, this.getGenerationDepth(), pickCastlePiece);
                case SOUTH -> generateNextPiece(startPiece, pieces, random,
                        this.bounds.maxX + 1, this.bounds.minY + yOffset, this.bounds.minZ + offset,
                        StructureFacing.EAST, this.getGenerationDepth(), pickCastlePiece);
                case WEST -> generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.maxZ + 1,
                        StructureFacing.SOUTH, this.getGenerationDepth(), pickCastlePiece);
                case EAST -> generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.maxZ + 1,
                        StructureFacing.SOUTH, this.getGenerationDepth(), pickCastlePiece);
            };
        }

        /** isValidBounds line 783: the box must clear y 10. */
        static boolean isValidBounds(StructureBox bounds) {
            return bounds != null && bounds.minY > 10;
        }
    }

    // ------------------------------------------------------------- the pieces

    /**
     * The straight bridge (StraightBridge lines 1313-1392): the 5x10x19
     * causeway with the rail fences.
     */
    static final class StraightBridge extends FortressPiece {

        StraightBridge(int generationDepth, Random random, StructureBox bounds,
                       StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressStart) start, pieces, random, 1, 3, false);
        }

        static StraightBridge of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                                 StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -1, -3, 0, 5, 10, 19, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new StraightBridge(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 3, 0, 4, 4, 18,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 5, 0, 3, 7, 18, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 5, 0, 0, 5, 18,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 4, 5, 0, 4, 5, 18,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 4, 2, 5,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 13, 4, 2, 18,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 0, 0, 4, 1, 3,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 0, 15, 4, 1, 18,
                    palette.netherBricks(), palette.netherBricks(), false);
            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 2; j++) {
                    this.fillColumnDown(world, palette.netherBricks(), i, -1, j, clip);
                    this.fillColumnDown(world, palette.netherBricks(), i, -1, 18 - j, clip);
                }
            }
            this.fillWithOutline(world, clip, 0, 1, 1, 0, 4, 1,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 0, 3, 4, 0, 4, 4,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 0, 3, 14, 0, 4, 14,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 0, 1, 17, 0, 4, 17,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 4, 1, 1, 4, 4, 1,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 4, 3, 4, 4, 4, 4,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 4, 3, 14, 4, 4, 14,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 4, 1, 17, 4, 4, 17,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            return true;
        }
    }

    /**
     * The bridge crossing (BridgeCrossing lines 205-246): the 19x10x19
     * four-way bridge hub — the fortress start's own shape.
     */
    static class BridgeCrossing extends FortressPiece {

        BridgeCrossing(int generationDepth, Random random, StructureBox bounds,
                      StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        /** The start shape (the protected three-arg constructor, lines 214-225). */
        protected BridgeCrossing(Random random, int chunkX, int chunkZ) {
            super(0);
            this.facing = StructureFacing.pickHorizontal(random);
            this.bounds = new StructureBox(chunkX, 64, chunkZ,
                    chunkX + 19 - 1, 73, chunkZ + 19 - 1);
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressStart) start, pieces, random, 8, 3, false);
            this.generatePieceLeft((FortressStart) start, pieces, random, 3, 8, false);
            this.generatePieceRight((FortressStart) start, pieces, random, 3, 8, false);
        }

        static BridgeCrossing of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                                 StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -8, -3, 0, 19, 10, 19, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new BridgeCrossing(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 7, 3, 0, 11, 4, 18,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 3, 7, 18, 4, 11,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 8, 5, 0, 10, 7, 18, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 5, 8, 18, 7, 10, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 7, 5, 0, 7, 5, 7,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 7, 5, 11, 7, 5, 18,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 11, 5, 0, 11, 5, 7,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 11, 5, 11, 11, 5, 18,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 5, 7, 7, 5, 7,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 11, 5, 7, 18, 5, 7,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 5, 11, 7, 5, 11,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 11, 5, 11, 18, 5, 11,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 7, 2, 0, 11, 2, 5,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 7, 2, 13, 11, 2, 18,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 7, 0, 0, 11, 1, 3,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 7, 0, 15, 11, 1, 18,
                    palette.netherBricks(), palette.netherBricks(), false);
            for (int i = 7; i <= 11; i++) {
                for (int j = 0; j <= 2; j++) {
                    this.fillColumnDown(world, palette.netherBricks(), i, -1, j, clip);
                    this.fillColumnDown(world, palette.netherBricks(), i, -1, 18 - j, clip);
                }
            }
            this.fillWithOutline(world, clip, 0, 2, 7, 5, 2, 11,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 13, 2, 7, 18, 2, 11,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 0, 7, 3, 1, 11,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 15, 0, 7, 18, 1, 11,
                    palette.netherBricks(), palette.netherBricks(), false);
            for (int k = 0; k <= 2; k++) {
                for (int l = 7; l <= 11; l++) {
                    this.fillColumnDown(world, palette.netherBricks(), k, -1, l, clip);
                    this.fillColumnDown(world, palette.netherBricks(), 18 - k, -1, l, clip);
                }
            }
            return true;
        }
    }

    /**
     * The fortress start (Start lines 1382-1414 extending the
     * BridgeCrossing shape): the 19x19 crossing anchor + the two weight
     * tables + the children queue.
     */
    static final class FortressStart extends BridgeCrossing {
        final List<StructurePiece> children = new ArrayList<>();
        final WalkState walk = new WalkState();

        FortressStart(Random random, int chunkX, int chunkZ) {
            super(random, chunkX, chunkZ);
        }
    }

    /**
     * The bridge end (BridgeEnd lines 248-312): the ragged terminator wall
     * — its own random seed drawn at construction, the wall lengths rolled
     * from it at postProcess time.
     */
    static final class BridgeEnd extends FortressPiece {
        private final int seed;

        BridgeEnd(int generationDepth, Random random, StructureBox bounds, StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.seed = random.nextInt();
        }

        static BridgeEnd of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                            StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -1, -3, 0, 5, 10, 8, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new BridgeEnd(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            Random wallRandom = new Random(this.seed);
            for (int i = 0; i <= 4; i++) {
                for (int j = 3; j <= 4; j++) {
                    int length = wallRandom.nextInt(8);
                    this.fillWithOutline(world, clip, i, j, 0, i, j, length,
                            palette.netherBricks(), palette.netherBricks(), false);
                }
            }
            int length = wallRandom.nextInt(8);
            this.fillWithOutline(world, clip, 0, 5, 0, 0, 5, length,
                    palette.netherBricks(), palette.netherBricks(), false);
            length = wallRandom.nextInt(8);
            this.fillWithOutline(world, clip, 4, 5, 0, 4, 5, length,
                    palette.netherBricks(), palette.netherBricks(), false);
            for (int i1 = 0; i1 <= 4; i1++) {
                int k1 = wallRandom.nextInt(5);
                this.fillWithOutline(world, clip, i1, 2, 0, i1, 2, k1,
                        palette.netherBricks(), palette.netherBricks(), false);
            }
            for (int j1 = 0; j1 <= 4; j1++) {
                for (int l1 = 0; l1 <= 1; l1++) {
                    int i2 = wallRandom.nextInt(3);
                    this.fillWithOutline(world, clip, j1, l1, 0, j1, l1, i2,
                            palette.netherBricks(), palette.netherBricks(), false);
                }
            }
            return true;
        }
    }

    /**
     * The bridge stairs (BridgeStairs lines 314-386): the 7x11x7 stair
     * tower off the bridge's side.
     */
    static final class BridgeStairs extends FortressPiece {

        BridgeStairs(int generationDepth, Random random, StructureBox bounds,
                     StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceRight((FortressStart) start, pieces, random, 6, 2, false);
        }

        static BridgeStairs of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                               int generationDepth, StructureFacing facing) {
            StructureBox box = StructureBox.orient(x, y, z, -2, 0, 0, 7, 11, 7, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new BridgeStairs(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 0, 0, 6, 1, 6,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 6, 10, 6, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 1, 8, 0,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 5, 2, 0, 6, 8, 0,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 1, 0, 8, 6,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 6, 2, 1, 6, 8, 6,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 2, 6, 5, 8, 6,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 3, 2, 0, 5, 4,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 6, 3, 2, 6, 5, 2,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 6, 3, 4, 6, 5, 4,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.setBlockState(world, palette.netherBricks(), 5, 2, 5, clip);
            this.fillWithOutline(world, clip, 4, 2, 5, 4, 3, 5,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 3, 2, 5, 3, 4, 5,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 2, 2, 5, 2, 5, 5,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 2, 5, 1, 6, 5,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 7, 1, 5, 7, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 6, 8, 2, 6, 8, 4, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 2, 6, 0, 4, 8, 0,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 2, 5, 0, 4, 5, 0,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            for (int i = 0; i <= 6; i++) {
                for (int j = 0; j <= 6; j++) {
                    this.fillColumnDown(world, palette.netherBricks(), i, -1, j, clip);
                }
            }
            return true;
        }
    }

    /**
     * The blaze spawner (BlazeSpawner lines 118-199): the 7x8x9 stair
     * platform with the blaze spawner on top.
     */
    static final class BlazeSpawner extends FortressPiece {
        private boolean shouldGenerateMobSpawner;

        BlazeSpawner(int generationDepth, Random random, StructureBox bounds,
                     StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        static BlazeSpawner of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                               int generationDepth, StructureFacing facing) {
            StructureBox box = StructureBox.orient(x, y, z, -2, 0, 0, 7, 8, 9, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new BlazeSpawner(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 2, 0, 6, 7, 7, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 1, 0, 0, 5, 1, 7,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 2, 1, 5, 2, 7,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 3, 2, 5, 3, 7,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 4, 3, 5, 4, 7,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 2, 0, 1, 4, 2,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 5, 2, 0, 5, 4, 2,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 5, 2, 1, 5, 3,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 5, 5, 2, 5, 5, 3,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 5, 3, 0, 5, 8,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 6, 5, 3, 6, 5, 8,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 5, 8, 5, 5, 8,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.setBlockState(world, palette.netherBrickFence(), 1, 6, 3, clip);
            this.setBlockState(world, palette.netherBrickFence(), 5, 6, 3, clip);
            this.fillWithOutline(world, clip, 0, 6, 3, 0, 6, 8,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 6, 6, 3, 6, 6, 8,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 1, 6, 8, 5, 7, 8,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 2, 8, 8, 4, 8, 8,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            // The blaze spawner (lines 183-192): the block lands; the block
            // entity + the fortress's own monster spawn table ledger with
            // the mob-spawner slice (the reference's setType("Blaze")).
            if (!this.shouldGenerateMobSpawner) {
                int spawnerX = this.transformX(3, 5);
                int spawnerY = this.transformY(5);
                int spawnerZ = this.transformZ(3, 5);
                if (clip.contains(spawnerX, spawnerY, spawnerZ)) {
                    this.shouldGenerateMobSpawner = true;
                    this.setBlockState(world, palette.mobSpawner(), 3, 5, 5, clip);
                }
            }
            for (int i = 0; i <= 6; i++) {
                for (int j = 0; j <= 6; j++) {
                    this.fillColumnDown(world, palette.netherBricks(), i, -1, j, clip);
                }
            }
            return true;
        }
    }

    /**
     * The castle entrance (CastleEntrance lines 388-509): the 13x14x13
     * gatehouse with the lava well.
     */
    static final class CastleEntrance extends FortressPiece {

        CastleEntrance(int generationDepth, Random random, StructureBox bounds,
                      StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressStart) start, pieces, random, 5, 3, true);
        }

        static CastleEntrance of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                                StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -5, -3, 0, 13, 14, 13, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new CastleEntrance(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 3, 0, 12, 4, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 5, 0, 12, 13, 12, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 5, 0, 1, 12, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 11, 5, 0, 12, 12, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 2, 5, 11, 4, 12, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 8, 5, 11, 10, 12, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 5, 9, 11, 7, 12, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 2, 5, 0, 4, 12, 1,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 8, 5, 0, 10, 12, 1,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 5, 9, 0, 7, 12, 1,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 2, 11, 2, 10, 12, 10,
                    palette.netherBricks(), palette.netherBricks(), false);
            for (int i = 1; i <= 11; i += 2) {
                this.fillWithOutline(world, clip, i, 10, 0, i, 11, 0,
                        palette.netherBrickFence(), palette.netherBrickFence(), false);
                this.fillWithOutline(world, clip, i, 10, 12, i, 11, 12,
                        palette.netherBrickFence(), palette.netherBrickFence(), false);
                this.fillWithOutline(world, clip, 0, 10, i, 0, 11, i,
                        palette.netherBrickFence(), palette.netherBrickFence(), false);
                this.fillWithOutline(world, clip, 12, 10, i, 12, 11, i,
                        palette.netherBrickFence(), palette.netherBrickFence(), false);
                this.setBlockState(world, palette.netherBricks(), i, 13, 0, clip);
                this.setBlockState(world, palette.netherBricks(), i, 13, 12, clip);
                this.setBlockState(world, palette.netherBricks(), 0, 13, i, clip);
                this.setBlockState(world, palette.netherBricks(), 12, 13, i, clip);
                this.setBlockState(world, palette.netherBrickFence(), i + 1, 13, 0, clip);
                this.setBlockState(world, palette.netherBrickFence(), i + 1, 13, 12, clip);
                this.setBlockState(world, palette.netherBrickFence(), 0, 13, i + 1, clip);
                this.setBlockState(world, palette.netherBrickFence(), 12, 13, i + 1, clip);
            }
            this.setBlockState(world, palette.netherBrickFence(), 0, 13, 0, clip);
            this.setBlockState(world, palette.netherBrickFence(), 0, 13, 12, clip);
            this.setBlockState(world, palette.netherBrickFence(), 0, 13, 0, clip);
            this.setBlockState(world, palette.netherBrickFence(), 12, 13, 0, clip);
            for (int k = 3; k <= 9; k += 2) {
                this.fillWithOutline(world, clip, 1, 7, k, 1, 8, k,
                        palette.netherBrickFence(), palette.netherBrickFence(), false);
                this.fillWithOutline(world, clip, 11, 7, k, 11, 8, k,
                        palette.netherBrickFence(), palette.netherBrickFence(), false);
            }
            this.fillWithOutline(world, clip, 4, 2, 0, 8, 2, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 4, 12, 2, 8,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 4, 0, 0, 8, 1, 3,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 4, 0, 9, 8, 1, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 0, 4, 3, 1, 8,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 9, 0, 4, 12, 1, 8,
                    palette.netherBricks(), palette.netherBricks(), false);
            for (int l = 4; l <= 8; l++) {
                for (int j = 0; j <= 2; j++) {
                    this.fillColumnDown(world, palette.netherBricks(), l, -1, j, clip);
                    this.fillColumnDown(world, palette.netherBricks(), l, -1, 12 - j, clip);
                }
            }
            for (int i1 = 0; i1 <= 2; i1++) {
                for (int j1 = 4; j1 <= 8; j1++) {
                    this.fillColumnDown(world, palette.netherBricks(), i1, -1, j1, clip);
                    this.fillColumnDown(world, palette.netherBricks(), 12 - i1, -1, j1, clip);
                }
            }
            this.fillWithOutline(world, clip, 5, 5, 5, 7, 5, 7,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 6, 1, 6, 6, 4, 6, palette.air(), palette.air(), false);
            this.setBlockState(world, palette.netherBricks(), 6, 0, 6, clip);
            // The lava well (lines 505-509): the source-block stand-in —
            // the reference's populate-time tickBlockNow ledgers with the
            // fluid-update slice (the same envelope as the blacksmith's
            // pools: no update arms run at populate time).
            this.setBlockState(world, palette.lava(), 6, 5, 6, clip);
            return true;
        }
    }

    /**
     * The corridor balcony (CorridorBalcony lines 511-552 + 386-... — the
     * castle's lookout): the 9x7x9 room with the two side corridors.
     */
    static final class CorridorBalcony extends FortressPiece {

        CorridorBalcony(int generationDepth, Random random, StructureBox bounds,
                       StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            int offset = 1;
            if (this.facing == StructureFacing.WEST || this.facing == StructureFacing.NORTH) {
                offset = 5;
            }
            this.generatePieceLeft((FortressStart) start, pieces, random, 0, offset,
                    random.nextInt(8) > 0);
            this.generatePieceRight((FortressStart) start, pieces, random, 0, offset,
                    random.nextInt(8) > 0);
        }

        static CorridorBalcony of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                                 StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -3, 0, 0, 9, 7, 9, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new CorridorBalcony(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 0, 0, 8, 1, 8,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 8, 5, 8, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 6, 0, 8, 6, 5,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 2, 5, 0,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 6, 2, 0, 8, 5, 0,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 3, 0, 1, 4, 0,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 7, 3, 0, 7, 4, 0,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 0, 2, 4, 8, 2, 8,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 1, 4, 2, 2, 4, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 6, 1, 4, 7, 2, 4, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 3, 8, 8, 3, 8,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 0, 3, 6, 0, 3, 7,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 8, 3, 6, 8, 3, 7,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 0, 3, 4, 0, 5, 5,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 8, 3, 4, 8, 5, 5,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 3, 5, 2, 5, 5,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 6, 3, 5, 7, 5, 5,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 4, 5, 1, 5, 5,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 7, 4, 5, 7, 5, 5,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            for (int i = 0; i <= 5; i++) {
                for (int j = 0; j <= 8; j++) {
                    this.fillColumnDown(world, palette.netherBricks(), j, -1, i, clip);
                }
            }
            return true;
        }
    }

    /**
     * The large crossing (LargeCrossing lines 807-911): the 7x9x7 tower
     * hub with the roofed lookout.
     */
    static final class LargeCrossing extends FortressPiece {

        LargeCrossing(int generationDepth, Random random, StructureBox bounds,
                     StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressStart) start, pieces, random, 2, 0, false);
            this.generatePieceLeft((FortressStart) start, pieces, random, 0, 2, false);
            this.generatePieceRight((FortressStart) start, pieces, random, 0, 2, false);
        }

        static LargeCrossing of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                                StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -2, 0, 0, 7, 9, 7, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new LargeCrossing(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 0, 0, 6, 1, 6,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 6, 7, 6, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 1, 6, 0,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 6, 1, 6, 6,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 5, 2, 0, 6, 6, 0,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 5, 2, 6, 6, 6, 6,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 0, 6, 1,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 5, 0, 6, 6,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 6, 2, 0, 6, 6, 1,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 6, 2, 5, 6, 6, 6,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 2, 6, 0, 4, 6, 0,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 2, 5, 0, 4, 5, 0,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 2, 6, 6, 4, 6, 6,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 2, 5, 6, 4, 5, 6,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 0, 6, 2, 0, 6, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 5, 2, 0, 5, 4,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 6, 6, 2, 6, 6, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 6, 5, 2, 6, 5, 4,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            for (int i = 0; i <= 6; i++) {
                for (int j = 0; j <= 6; j++) {
                    this.fillColumnDown(world, palette.netherBricks(), i, -1, j, clip);
                }
            }
            return true;
        }
    }

    /**
     * The small corridor (SmallCorridor lines 913-947): the 5x7x5 castle
     * hallway.
     */
    static final class SmallCorridor extends FortressPiece {

        SmallCorridor(int generationDepth, Random random, StructureBox bounds,
                      StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressStart) start, pieces, random, 1, 0, true);
        }

        static SmallCorridor of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                                StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -1, 0, 0, 5, 7, 5, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new SmallCorridor(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 0, 0, 4, 1, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 4, 5, 4, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 0, 5, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 4, 2, 0, 4, 5, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 3, 1, 0, 4, 1,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 0, 3, 3, 0, 4, 3,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 4, 3, 1, 4, 4, 1,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 4, 3, 3, 4, 4, 3,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 0, 6, 0, 4, 6, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 4; j++) {
                    this.fillColumnDown(world, palette.netherBricks(), i, -1, j, clip);
                }
            }
            return true;
        }
    }

    /**
     * The small corridor crossing (SmallCorridorCrossing lines 949-983):
     * the four-way 5x7x5 hub.
     */
    static final class SmallCorridorCrossing extends FortressPiece {

        SmallCorridorCrossing(int generationDepth, Random random, StructureBox bounds,
                             StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressStart) start, pieces, random, 1, 0, true);
            this.generatePieceLeft((FortressStart) start, pieces, random, 0, 1, true);
            this.generatePieceRight((FortressStart) start, pieces, random, 0, 1, true);
        }

        static SmallCorridorCrossing of(List<StructurePiece> pieces, Random random, int x, int y,
                                        int z, StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -1, 0, 0, 5, 7, 5, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new SmallCorridorCrossing(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 0, 0, 4, 1, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 4, 5, 4, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 0, 5, 0,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 4, 2, 0, 4, 5, 0,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 4, 0, 5, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 4, 2, 4, 4, 5, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 6, 0, 4, 6, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 4; j++) {
                    this.fillColumnDown(world, palette.netherBricks(), i, -1, j, clip);
                }
            }
            return true;
        }
    }

    /**
     * The small corridor left turn (SmallCorridorLeftTurn lines
     * 985-1056): the 5x7x5 elbow with the loot chest roll.
     */
    static final class SmallCorridorLeftTurn extends FortressPiece {
        private boolean hasChest;

        SmallCorridorLeftTurn(int generationDepth, Random random, StructureBox bounds,
                             StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.hasChest = random.nextInt(3) == 0;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceLeft((FortressStart) start, pieces, random, 0, 1, true);
        }

        static SmallCorridorLeftTurn of(List<StructurePiece> pieces, Random random, int x, int y,
                                        int z, StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -1, 0, 0, 5, 7, 5, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new SmallCorridorLeftTurn(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 0, 0, 4, 1, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 4, 5, 4, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 4, 2, 0, 4, 5, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 4, 3, 1, 4, 4, 1,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 4, 3, 3, 4, 4, 3,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 0, 5, 0,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 4, 3, 5, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 3, 4, 1, 4, 4,
                    palette.netherBrickFence(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 3, 3, 4, 3, 4, 4,
                    palette.netherBrickFence(), palette.netherBricks(), false);
            if (this.hasChest && clip.contains(this.transformX(3, 3), this.transformY(2),
                    this.transformZ(3, 3))) {
                this.hasChest = false;
                StrongholdPieces.placeChestWithLoot(this, world, random, clip, 3, 2, 3,
                        LOOT_ENTRIES, null, 2 + random.nextInt(4));
            }
            this.fillWithOutline(world, clip, 0, 6, 0, 4, 6, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 4; j++) {
                    this.fillColumnDown(world, palette.netherBricks(), i, -1, j, clip);
                }
            }
            return true;
        }
    }

    /**
     * The small corridor right turn (SmallCorridorRightTurn lines
     * 1058-1134): the mirrored elbow with its own chest roll.
     */
    static final class SmallCorridorRightTurn extends FortressPiece {
        private boolean hasLootChest;

        SmallCorridorRightTurn(int generationDepth, Random random, StructureBox bounds,
                              StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.hasLootChest = random.nextInt(3) == 0;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceRight((FortressStart) start, pieces, random, 0, 1, true);
        }

        static SmallCorridorRightTurn of(List<StructurePiece> pieces, Random random, int x, int y,
                                         int z, StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -1, 0, 0, 5, 7, 5, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new SmallCorridorRightTurn(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 0, 0, 4, 1, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 4, 5, 4, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 0, 5, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 3, 1, 0, 4, 1,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 0, 3, 3, 0, 4, 3,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 4, 2, 0, 4, 5, 0,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 2, 4, 4, 5, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 1, 3, 4, 1, 4, 4,
                    palette.netherBrickFence(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 3, 3, 4, 3, 4, 4,
                    palette.netherBrickFence(), palette.netherBricks(), false);
            if (this.hasLootChest && clip.contains(this.transformX(1, 3), this.transformY(2),
                    this.transformZ(1, 3))) {
                this.hasLootChest = false;
                StrongholdPieces.placeChestWithLoot(this, world, random, clip, 1, 2, 3,
                        LOOT_ENTRIES, null, 2 + random.nextInt(4));
            }
            this.fillWithOutline(world, clip, 0, 6, 0, 4, 6, 4,
                    palette.netherBricks(), palette.netherBricks(), false);
            for (int i = 0; i <= 4; i++) {
                for (int j = 0; j <= 4; j++) {
                    this.fillColumnDown(world, palette.netherBricks(), i, -1, j, clip);
                }
            }
            return true;
        }
    }

    /**
     * The stairs corridor (StairsCorridor lines 1136-1234): the 5x14x10
     * castle staircase.
     */
    static final class StairsCorridor extends FortressPiece {

        StairsCorridor(int generationDepth, Random random, StructureBox bounds,
                       StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressStart) start, pieces, random, 1, 0, true);
        }

        static StairsCorridor of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                                 StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -1, -7, 0, 5, 14, 10, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new StairsCorridor(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            int stairsMetadata = this.postProcessMetadata(MetadataFamily.STAIRS, 2);
            for (int j = 0; j <= 9; j++) {
                int floor = Math.max(1, 7 - j);
                int ceiling = Math.min(Math.max(floor + 5, 14 - j), 13);
                this.fillWithOutline(world, clip, 0, 0, j, 4, floor, j,
                        palette.netherBricks(), palette.netherBricks(), false);
                this.fillWithOutline(world, clip, 1, floor + 1, j, 3, ceiling - 1, j,
                        palette.air(), palette.air(), false);
                if (j <= 6) {
                    this.setBlockState(world, palette.netherBrickStairs(stairsMetadata), 1, floor + 1, j, clip);
                    this.setBlockState(world, palette.netherBrickStairs(stairsMetadata), 2, floor + 1, j, clip);
                    this.setBlockState(world, palette.netherBrickStairs(stairsMetadata), 3, floor + 1, j, clip);
                }
                this.fillWithOutline(world, clip, 0, ceiling, j, 4, ceiling, j,
                        palette.netherBricks(), palette.netherBricks(), false);
                this.fillWithOutline(world, clip, 0, floor + 1, j, 0, ceiling - 1, j,
                        palette.netherBricks(), palette.netherBricks(), false);
                this.fillWithOutline(world, clip, 4, floor + 1, j, 4, ceiling - 1, j,
                        palette.netherBricks(), palette.netherBricks(), false);
                if ((j & 1) == 0) {
                    this.fillWithOutline(world, clip, 0, floor + 2, j, 0, floor + 3, j,
                            palette.netherBrickFence(), palette.netherBrickFence(), false);
                    this.fillWithOutline(world, clip, 4, floor + 2, j, 4, floor + 3, j,
                            palette.netherBrickFence(), palette.netherBrickFence(), false);
                }
                for (int j1 = 0; j1 <= 4; j1++) {
                    this.fillColumnDown(world, palette.netherBricks(), j1, -1, j, clip);
                }
            }
            return true;
        }
    }

    /**
     * The nether wart farm (NetherwartFarm lines 913-1112 — the castle's
     * inner courtyard): the stair cascade, the soul sand beds with the
     * wart rows, and the two-level gallery.
     */
    static final class NetherwartFarm extends FortressPiece {

        NetherwartFarm(int generationDepth, Random random, StructureBox bounds,
                       StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((FortressStart) start, pieces, random, 5, 3, true);
            this.generatePieceForward((FortressStart) start, pieces, random, 5, 11, true);
        }

        static NetherwartFarm of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                                StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -5, -3, 0, 13, 14, 13, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new NetherwartFarm(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 3, 0, 12, 4, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 5, 0, 12, 13, 12, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 5, 0, 1, 12, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 11, 5, 0, 12, 12, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 2, 5, 11, 4, 12, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 8, 5, 11, 10, 12, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 5, 9, 11, 7, 12, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 2, 5, 0, 4, 12, 1,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 8, 5, 0, 10, 12, 1,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 5, 9, 0, 7, 12, 1,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 2, 11, 2, 10, 12, 10,
                    palette.netherBricks(), palette.netherBricks(), false);
            for (int i = 1; i <= 11; i += 2) {
                this.fillWithOutline(world, clip, i, 10, 0, i, 11, 0,
                        palette.netherBrickFence(), palette.netherBrickFence(), false);
                this.fillWithOutline(world, clip, i, 10, 12, i, 11, 12,
                        palette.netherBrickFence(), palette.netherBrickFence(), false);
                this.fillWithOutline(world, clip, 0, 10, i, 0, 11, i,
                        palette.netherBrickFence(), palette.netherBrickFence(), false);
                this.fillWithOutline(world, clip, 12, 10, i, 12, 11, i,
                        palette.netherBrickFence(), palette.netherBrickFence(), false);
                this.setBlockState(world, palette.netherBricks(), i, 13, 0, clip);
                this.setBlockState(world, palette.netherBricks(), i, 13, 12, clip);
                this.setBlockState(world, palette.netherBricks(), 0, 13, i, clip);
                this.setBlockState(world, palette.netherBricks(), 12, 13, i, clip);
                this.setBlockState(world, palette.netherBrickFence(), i + 1, 13, 0, clip);
                this.setBlockState(world, palette.netherBrickFence(), i + 1, 13, 12, clip);
                this.setBlockState(world, palette.netherBrickFence(), 0, 13, i + 1, clip);
                this.setBlockState(world, palette.netherBrickFence(), 12, 13, i + 1, clip);
            }
            this.setBlockState(world, palette.netherBrickFence(), 0, 13, 0, clip);
            this.setBlockState(world, palette.netherBrickFence(), 0, 13, 12, clip);
            this.setBlockState(world, palette.netherBrickFence(), 0, 13, 0, clip);
            this.setBlockState(world, palette.netherBrickFence(), 12, 13, 0, clip);
            for (int j1 = 3; j1 <= 9; j1 += 2) {
                this.fillWithOutline(world, clip, 1, 7, j1, 1, 8, j1,
                        palette.netherBrickFence(), palette.netherBrickFence(), false);
                this.fillWithOutline(world, clip, 11, 7, j1, 11, 8, j1,
                        palette.netherBrickFence(), palette.netherBrickFence(), false);
            }
            // The stair cascade (lines 1020-1040).
            int cascadeMetadata = this.postProcessMetadata(MetadataFamily.STAIRS, 3);
            for (int j = 0; j <= 6; j++) {
                int k = j + 4;
                for (int l = 5; l <= 7; l++) {
                    this.setBlockState(world, palette.netherBrickStairs(cascadeMetadata),
                            l, 5 + j, k, clip);
                }
                if (k >= 5 && k <= 8) {
                    this.fillWithOutline(world, clip, 5, 5, k, 7, j + 4, k,
                            palette.netherBricks(), palette.netherBricks(), false);
                } else if (k >= 9 && k <= 10) {
                    this.fillWithOutline(world, clip, 5, 8, k, 7, j + 4, k,
                            palette.netherBricks(), palette.netherBricks(), false);
                }
                if (j >= 1) {
                    this.fillWithOutline(world, clip, 5, 6 + j, k, 7, 9 + j, k,
                            palette.air(), palette.air(), false);
                }
            }
            for (int l1 = 5; l1 <= 7; l1++) {
                this.setBlockState(world, palette.netherBrickStairs(cascadeMetadata), l1, 12, 11, clip);
            }
            this.fillWithOutline(world, clip, 5, 6, 7, 5, 7, 7,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 7, 6, 7, 7, 7, 7,
                    palette.netherBrickFence(), palette.netherBrickFence(), false);
            this.fillWithOutline(world, clip, 5, 13, 12, 7, 13, 12, palette.air(), palette.air(), false);
            // The planting beds (lines 1046-1059).
            this.fillWithOutline(world, clip, 2, 5, 2, 3, 5, 3,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 2, 5, 9, 3, 5, 10,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 2, 5, 4, 2, 5, 8,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 9, 5, 2, 10, 5, 3,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 9, 5, 9, 10, 5, 10,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 10, 5, 4, 10, 5, 8,
                    palette.netherBricks(), palette.netherBricks(), false);
            int eastMetadata = this.postProcessMetadata(MetadataFamily.STAIRS, 0);
            int westMetadata = this.postProcessMetadata(MetadataFamily.STAIRS, 1);
            this.setBlockState(world, palette.netherBrickStairs(westMetadata), 4, 5, 2, clip);
            this.setBlockState(world, palette.netherBrickStairs(westMetadata), 4, 5, 3, clip);
            this.setBlockState(world, palette.netherBrickStairs(westMetadata), 4, 5, 9, clip);
            this.setBlockState(world, palette.netherBrickStairs(westMetadata), 4, 5, 10, clip);
            this.setBlockState(world, palette.netherBrickStairs(eastMetadata), 8, 5, 2, clip);
            this.setBlockState(world, palette.netherBrickStairs(eastMetadata), 8, 5, 3, clip);
            this.setBlockState(world, palette.netherBrickStairs(eastMetadata), 8, 5, 9, clip);
            this.setBlockState(world, palette.netherBrickStairs(eastMetadata), 8, 5, 10, clip);
            // The soul sand beds with the wart (the reference plants age 0).
            this.fillWithOutline(world, clip, 3, 4, 4, 4, 4, 8,
                    palette.soulSand(), palette.soulSand(), false);
            this.fillWithOutline(world, clip, 8, 4, 4, 9, 4, 8,
                    palette.soulSand(), palette.soulSand(), false);
            this.fillWithOutline(world, clip, 3, 5, 4, 4, 5, 8,
                    palette.netherWart(0), palette.netherWart(0), false);
            this.fillWithOutline(world, clip, 8, 5, 4, 9, 5, 8,
                    palette.netherWart(0), palette.netherWart(0), false);
            this.fillWithOutline(world, clip, 4, 2, 0, 8, 2, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 2, 4, 12, 2, 8,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 4, 0, 0, 8, 1, 3,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 4, 0, 9, 8, 1, 12,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 0, 0, 4, 3, 1, 8,
                    palette.netherBricks(), palette.netherBricks(), false);
            this.fillWithOutline(world, clip, 9, 0, 4, 12, 1, 8,
                    palette.netherBricks(), palette.netherBricks(), false);
            for (int k2 = 4; k2 <= 8; k2++) {
                for (int i1 = 0; i1 <= 2; i1++) {
                    this.fillColumnDown(world, palette.netherBricks(), k2, -1, i1, clip);
                    this.fillColumnDown(world, palette.netherBricks(), k2, -1, 12 - i1, clip);
                }
            }
            for (int l2 = 0; l2 <= 2; l2++) {
                for (int i3 = 4; i3 <= 8; i3++) {
                    this.fillColumnDown(world, palette.netherBricks(), l2, -1, i3, clip);
                    this.fillColumnDown(world, palette.netherBricks(), 12 - l2, -1, i3, clip);
                }
            }
            return true;
        }
    }
}
