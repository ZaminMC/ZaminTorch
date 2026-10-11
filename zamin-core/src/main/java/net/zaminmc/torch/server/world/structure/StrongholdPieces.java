package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The stronghold piece family (Slice 12c — the reference port of
 * gen/structure/StrongholdPieces.java, 1:1 over the Slice 12a foundation):
 * the eleven piece families behind the weight table, the entrance family
 * (the four door shapes over the stone-brick frames), the stone-brick
 * picker (the reference's own 0.2/0.5/0.55 band), and the three loot
 * tables with the enchanted-book arm.
 *
 * <p><b>The architectural decision</b> (the same envelope as the village's
 * piece-weight walk): the reference's static weight state
 * ({@code currentWeights}, {@code forcedPiece}, {@code totalWeight},
 * {@code Start.previous}) is per-<em>start</em> generation state in the
 * engine — held by {@link StrongholdStart} — so the parallel chunk
 * pipeline's per-chunk recomputation never shares mutable statics. The
 * reference resets the weights once per stronghold; the engine resets
 * them once per start recomputation, which is the same walk from the
 * same seed and therefore the identical piece set.</p>
 *
 * <p><b>Ledgered</b> (the reference's arms the engine lacks the systems
 * for): the silverfish spawner's block entity (the block lands; the
 * spawning logic rides the mob-spawner slice), and the chest's
 * {@code updateFacing} re-orientation (the engine's chest carries no
 * facing — the village's own stand-in).</p>
 */
public final class StrongholdPieces {

    private StrongholdPieces() {
    }

    // ------------------------------------------------------------ the loot family

    /**
     * One loot row (LootEntry lines 12-29): the item with its count band
     * and its weight (the reference's metadata column is all zeros across
     * the three tables — the plain record keeps the four live fields). The
     * enchanted-book row rides the caller's own roll instead (the
     * reference's EnchantedBookItem.createLoot — a rolled enchantment, not
     * a fixed row).
     */
    record LootEntry(String itemId, int minCount, int maxCount, int weight) {
    }

    /**
     * The chest corridor's table (ChestCorridor lines 202-221 — 18 rows,
     * the sum of the weights 100).
     */
    private static final List<LootEntry> CHEST_CORRIDOR_LOOT = List.of(
            new LootEntry("minecraft:ender_pearl", 1, 1, 10),
            new LootEntry("minecraft:diamond", 1, 3, 3),
            new LootEntry("minecraft:iron_ingot", 1, 5, 10),
            new LootEntry("minecraft:gold_ingot", 1, 3, 5),
            new LootEntry("minecraft:redstone", 4, 9, 5),
            new LootEntry("minecraft:bread", 1, 3, 15),
            new LootEntry("minecraft:apple", 1, 3, 15),
            new LootEntry("minecraft:iron_pickaxe", 1, 1, 5),
            new LootEntry("minecraft:iron_sword", 1, 1, 5),
            new LootEntry("minecraft:iron_chestplate", 1, 1, 5),
            new LootEntry("minecraft:iron_helmet", 1, 1, 5),
            new LootEntry("minecraft:iron_leggings", 1, 1, 5),
            new LootEntry("minecraft:iron_boots", 1, 1, 5),
            new LootEntry("minecraft:golden_apple", 1, 1, 1),
            new LootEntry("minecraft:saddle", 1, 1, 1),
            new LootEntry("minecraft:iron_horse_armor", 1, 1, 1),
            new LootEntry("minecraft:golden_horse_armor", 1, 1, 1),
            new LootEntry("minecraft:diamond_horse_armor", 1, 1, 1));

    /** The library's table (Library lines 570-575 — 4 rows, weight 42). */
    private static final List<LootEntry> LIBRARY_LOOT = List.of(
            new LootEntry("minecraft:book", 1, 3, 20),
            new LootEntry("minecraft:paper", 2, 7, 20),
            new LootEntry("minecraft:empty_map", 1, 1, 1),
            new LootEntry("minecraft:compass", 1, 1, 1));

    /** The room crossing's table (RoomCrossing lines 880-888 — 7 rows, weight 61). */
    private static final List<LootEntry> ROOM_CROSSING_LOOT = List.of(
            new LootEntry("minecraft:iron_ingot", 1, 5, 10),
            new LootEntry("minecraft:gold_ingot", 1, 3, 5),
            new LootEntry("minecraft:redstone", 4, 9, 5),
            new LootEntry("minecraft:coal", 3, 8, 10),
            new LootEntry("minecraft:bread", 1, 3, 15),
            new LootEntry("minecraft:apple", 1, 3, 15),
            new LootEntry("minecraft:iron_pickaxe", 1, 1, 1));

    // ------------------------------------------------------------ the weights

    /**
     * The piece kinds (the reference's own weight table order, lines 20-35):
     * weight + the max-amount cap, the two depth-gated families carried by
     * {@link #isValidDepth}.
     */
    enum PieceKind {
        STRAIGHT_CORRIDOR(40, 0),
        PRISON(5, 5),
        LEFT_TURN(20, 0),
        RIGHT_TURN(20, 0),
        ROOM_CROSSING(10, 6),
        STRAIGHT_STAIRS(5, 5),
        SPIRAL_STAIRCASE(5, 5),
        FIVE_WAY_CROSSING(5, 4),
        CHEST_CORRIDOR(5, 4),
        LIBRARY(10, 2),
        END_PORTAL_ROOM(20, 1);

        final int weight;
        final int maxAmount;

        PieceKind(int weight, int maxAmount) {
            this.weight = weight;
            this.maxAmount = maxAmount;
        }

        /**
         * The weight-table validity gate (the Library's and the
         * EndPortalRoom's own overrides — the depth-gated families).
         */
        boolean isValidDepth(int generationDepth) {
            return switch (this) {
                case LIBRARY -> generationDepth > 4;
                case END_PORTAL_ROOM -> generationDepth > 5;
                default -> true;
            };
        }
    }

    /**
     * One per-start weight row (StrongholdPieceWeight lines 1498-1520):
     * the generated-count roll.
     */
    static final class PieceWeight {
        final PieceKind kind;
        int amountGenerated;

        PieceWeight(PieceKind kind) {
            this.kind = kind;
        }

        /** The reference's both isValid arms (the max-amount gate). */
        boolean isValid() {
            return this.kind.maxAmount == 0 || this.amountGenerated < this.kind.maxAmount;
        }
    }

    /**
     * The per-start walk state (the reference's statics, made per-start —
     * the engine's parallel-safe contract): the weight rows in the
     * reference's own table order, the forced piece, the total, and the
     * previous row (the no-immediate-repeat rule).
     */
    static final class WalkState {
        final List<PieceWeight> weights = new ArrayList<>();
        PieceKind forcedPiece;
        PieceWeight previous;
        int totalWeight;

        WalkState() {
            for (PieceKind kind : PieceKind.values()) {
                this.weights.add(new PieceWeight(kind));
            }
        }

        /**
         * updateTotalWeight lines 100-115, 1:1: the total sums EVERY row
         * (the exhausted ones too — the reference's own quirk: a
         * nextInt(totalWeight) roll may land on an exhausted row and
         * burn one of the five tries) while the return answers whether
         * any row can still generate.
         */
        boolean updateTotalWeight() {
            boolean anyLeft = false;
            this.totalWeight = 0;
            for (PieceWeight weight : this.weights) {
                if (weight.kind.maxAmount > 0 && weight.amountGenerated < weight.kind.maxAmount) {
                    anyLeft = true;
                }
                this.totalWeight += weight.kind.weight;
            }
            return anyLeft;
        }
    }

    // ----------------------------------------------------------- the start piece

    /**
     * The start piece (Start lines 1142-1165 extending the source
     * SpiralStaircase): the five-block staircase that anchors the whole
     * stronghold — the random facing, the forced FiveWayCrossing after
     * the stairs down, the children queue the outer walk drains in random
     * order, the no-repeat weight row, and the end-portal-room pointer
     * the placement's retry gate reads.
     */
    static final class StrongholdStart extends SpiralStaircase {

        /** The not-yet-expanded children (the outer walk's queue). */
        final List<StructurePiece> children = new ArrayList<>();
        /** The per-start walk state (the reference's statics). */
        final WalkState walk = new WalkState();
        /** The EndPortalRoom's addChildren writes this (the retry gate reads it). */
        EndPortalRoom endPortalRoom;

        StrongholdStart(int generationDepth, Random random, int x, int z) {
            super(true, generationDepth, random, x, z);
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            // The source's addChildren (SpiralStaircase lines 1078-1084):
            // the first piece after the stairs down is always the
            // FiveWayCrossing (the forced piece).
            ((StrongholdStart) start).walk.forcedPiece = PieceKind.FIVE_WAY_CROSSING;
            this.generatePieceForward((StrongholdStart) start, pieces, random, 1, 1);
        }
    }

    // -------------------------------------------------------- the piece base

    /** The four entrance shapes (the reference's EntranceType enum). */
    enum EntranceType {
        OPENING,
        WOOD_DOOR,
        GATES,
        IRON_DOOR
    }

    /**
     * The stronghold piece base (StrongholdPiece lines 1257-1496): the
     * entrance shape, the four generation arms (forward/left/right with
     * the reference's own parameter order quirk — the left/right pair
     * takes {@code (yOffset, offset)}), and the minY&gt;10 bounds gate.
     */
    abstract static class StrongholdPiece extends StructurePiece {

        EntranceType entranceType = EntranceType.OPENING;

        protected StrongholdPiece(int generationDepth) {
            super(generationDepth);
        }

        /**
         * pickEntranceType lines 1395-1409: nextInt(5) — 0/1/default
         * OPENING, 2 WOOD_DOOR, 3 GATES, 4 IRON_DOOR.
         */
        static EntranceType pickEntranceType(Random random) {
            return switch (random.nextInt(5)) {
                case 2 -> EntranceType.WOOD_DOOR;
                case 3 -> EntranceType.GATES;
                case 4 -> EntranceType.IRON_DOOR;
                default -> EntranceType.OPENING;
            };
        }

        /**
         * The entrance family (generateEntrance lines 1302-1371, 1:1): the
         * three-by-three doorway at the piece-local cell — the plain air
         * opening, the wooden door in the stone-brick frame, the iron
         * bars gate, and the iron door with its pair of stone buttons.
         */
        void generateEntrance(StructureWorld world, Random random, StructureBox clip,
                              EntranceType type, int x, int y, int z) {
            StructurePalette palette = world.palette();
            switch (type) {
                case WOOD_DOOR -> {
                    this.setBlockState(world, palette.stoneBricks(), x, y, z, clip);
                    this.setBlockState(world, palette.stoneBricks(), x, y + 1, z, clip);
                    this.setBlockState(world, palette.stoneBricks(), x, y + 2, z, clip);
                    this.setBlockState(world, palette.stoneBricks(), x + 1, y + 2, z, clip);
                    this.setBlockState(world, palette.stoneBricks(), x + 2, y + 2, z, clip);
                    this.setBlockState(world, palette.stoneBricks(), x + 2, y + 1, z, clip);
                    this.setBlockState(world, palette.stoneBricks(), x + 2, y, z, clip);
                    // The reference's defaultState pair: the lower facing
                    // NORTH, the plain metadata-8 upper.
                    this.setBlockState(world, palette.doorLower(2), x + 1, y, z, clip);
                    this.setBlockState(world, palette.doorUpper(), x + 1, y + 1, z, clip);
                }
                case GATES -> {
                    this.setBlockState(world, palette.air(), x + 1, y, z, clip);
                    this.setBlockState(world, palette.air(), x + 1, y + 1, z, clip);
                    this.setBlockState(world, palette.ironBars(), x, y, z, clip);
                    this.setBlockState(world, palette.ironBars(), x, y + 1, z, clip);
                    this.setBlockState(world, palette.ironBars(), x, y + 2, z, clip);
                    this.setBlockState(world, palette.ironBars(), x + 1, y + 2, z, clip);
                    this.setBlockState(world, palette.ironBars(), x + 2, y + 2, z, clip);
                    this.setBlockState(world, palette.ironBars(), x + 2, y + 1, z, clip);
                    this.setBlockState(world, palette.ironBars(), x + 2, y, z, clip);
                }
                case IRON_DOOR -> {
                    this.setBlockState(world, palette.stoneBricks(), x, y, z, clip);
                    this.setBlockState(world, palette.stoneBricks(), x, y + 1, z, clip);
                    this.setBlockState(world, palette.stoneBricks(), x, y + 2, z, clip);
                    this.setBlockState(world, palette.stoneBricks(), x + 1, y + 2, z, clip);
                    this.setBlockState(world, palette.stoneBricks(), x + 2, y + 2, z, clip);
                    this.setBlockState(world, palette.stoneBricks(), x + 2, y + 1, z, clip);
                    this.setBlockState(world, palette.stoneBricks(), x + 2, y, z, clip);
                    this.setBlockState(world, palette.ironDoorLower(2), x + 1, y, z, clip);
                    this.setBlockState(world, palette.ironDoorUpper(), x + 1, y + 1, z, clip);
                    // The button pair: the metadata flip on the reference's
                    // own attachment band (4 north, 3 south), the wall sides.
                    this.setBlockState(world, stoneButton(this.postProcessMetadata(
                            MetadataFamily.STONE_BUTTON, 4)), x + 2, y + 1, z + 1, clip);
                    this.setBlockState(world, stoneButton(this.postProcessMetadata(
                            MetadataFamily.STONE_BUTTON, 3)), x + 2, y + 1, z - 1, clip);
                }
                default -> this.fillWithOutline(world, clip, x, y, z, x + 3 - 1, y + 3 - 1, z,
                        palette.air(), palette.air(), false);
            }
        }

        /**
         * The reference's ButtonBlock attachment band (0 down, 1 east,
         * 2 west, 3 south, 4 north, 5 up) over the engine's button family
         * (the Direction-ordinal band down/up/north/south/west/east).
         */
        static BlockType stoneButton(int referenceMetadata) {
            int facing = switch (referenceMetadata & 7) {
                case 1 -> 5; // east
                case 2 -> 4; // west
                case 3 -> 3; // south
                case 4 -> 2; // north
                case 5 -> 1; // up
                default -> 0; // down
            };
            return net.zaminmc.torch.server.redstone.RedstoneBlocks.buttonOf(facing, false, false);
        }

        // ------------------------------------------------ the generation arms

        /**
         * generatePieceForward lines 1411-1452: the next piece beyond the
         * far face, at (offset, yOffset).
         */
        StructurePiece generatePieceForward(StrongholdStart startPiece, List<StructurePiece> pieces,
                                            Random random, int offset, int yOffset) {
            if (this.facing == null) {
                return null;
            }
            return switch (this.facing) {
                case NORTH -> StrongholdPieces.generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.minZ - 1,
                        this.facing, this.getGenerationDepth());
                case SOUTH -> StrongholdPieces.generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.maxZ + 1,
                        this.facing, this.getGenerationDepth());
                case WEST -> StrongholdPieces.generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX - 1, this.bounds.minY + yOffset, this.bounds.minZ + offset,
                        this.facing, this.getGenerationDepth());
                case EAST -> StrongholdPieces.generateNextPiece(startPiece, pieces, random,
                        this.bounds.maxX + 1, this.bounds.minY + yOffset, this.bounds.minZ + offset,
                        this.facing, this.getGenerationDepth());
            };
        }

        /**
         * generatePieceLeft lines 1454-1488: the reference's own parameter
         * order — {@code (yOffset, offset)} — the west/north arm.
         */
        StructurePiece generatePieceLeft(StrongholdStart startPiece, List<StructurePiece> pieces,
                                         Random random, int yOffset, int offset) {
            if (this.facing == null) {
                return null;
            }
            return switch (this.facing) {
                case NORTH -> StrongholdPieces.generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX - 1, this.bounds.minY + yOffset, this.bounds.minZ + offset,
                        StructureFacing.WEST, this.getGenerationDepth());
                case SOUTH -> StrongholdPieces.generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX - 1, this.bounds.minY + yOffset, this.bounds.minZ + offset,
                        StructureFacing.WEST, this.getGenerationDepth());
                case WEST -> StrongholdPieces.generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.minZ - 1,
                        StructureFacing.NORTH, this.getGenerationDepth());
                case EAST -> StrongholdPieces.generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.minZ - 1,
                        StructureFacing.NORTH, this.getGenerationDepth());
            };
        }

        /**
         * generatePieceRight lines 1490-1524: the east/south arm (the same
         * {@code (yOffset, offset)} order).
         */
        StructurePiece generatePieceRight(StrongholdStart startPiece, List<StructurePiece> pieces,
                                          Random random, int yOffset, int offset) {
            if (this.facing == null) {
                return null;
            }
            return switch (this.facing) {
                case NORTH -> StrongholdPieces.generateNextPiece(startPiece, pieces, random,
                        this.bounds.maxX + 1, this.bounds.minY + yOffset, this.bounds.minZ + offset,
                        StructureFacing.EAST, this.getGenerationDepth());
                case SOUTH -> StrongholdPieces.generateNextPiece(startPiece, pieces, random,
                        this.bounds.maxX + 1, this.bounds.minY + yOffset, this.bounds.minZ + offset,
                        StructureFacing.EAST, this.getGenerationDepth());
                case WEST -> StrongholdPieces.generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.maxZ + 1,
                        StructureFacing.SOUTH, this.getGenerationDepth());
                case EAST -> StrongholdPieces.generateNextPiece(startPiece, pieces, random,
                        this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.maxZ + 1,
                        StructureFacing.SOUTH, this.getGenerationDepth());
            };
        }

        /** isValidBounds line 1526: the box must clear y 10. */
        static boolean isValidBounds(StructureBox bounds) {
            return bounds != null && bounds.minY > 10;
        }
    }

    // ------------------------------------------------------ the walk driver

    /**
     * The next-piece roll (generateNextPiece lines 136-174, 1:1): the
     * forced piece first, then the five-try weighted walk with the
     * depth-gate and the no-repeat checks, the PlainCorridor fallback.
     */
    private static StructurePiece rollNextPiece(StrongholdStart startPiece,
                                                  List<StructurePiece> pieces, Random random,
                                                  int x, int y, int z, StructureFacing facing,
                                                  int generationDepth) {
        WalkState walk = startPiece.walk;
        if (!walk.updateTotalWeight()) {
            return null;
        }
        if (walk.forcedPiece != null) {
            StructurePiece forced = createPiece(walk.forcedPiece, pieces, random, x, y, z,
                    facing, generationDepth);
            walk.forcedPiece = null;
            if (forced != null) {
                return forced;
            }
        }
        int tries = 0;
        while (tries < 5) {
            tries++;
            int pick = random.nextInt(walk.totalWeight);
            for (PieceWeight weight : walk.weights) {
                pick -= weight.kind.weight;
                if (pick < 0) {
                    if (!weight.kind.isValidDepth(generationDepth) || weight == walk.previous) {
                        break;
                    }
                    StructurePiece piece = createPiece(weight.kind, pieces, random, x, y, z,
                            facing, generationDepth);
                    if (piece != null) {
                        weight.amountGenerated++;
                        walk.previous = weight;
                        if (!weight.isValid()) {
                            walk.weights.remove(weight);
                        }
                        return piece;
                    }
                }
            }
        }
        StructureBox fallback = PlainCorridor.findSize(pieces, x, y, z, facing);
        return fallback != null && fallback.minY > 1
                ? new PlainCorridor(generationDepth, fallback, facing)
                : null;
    }

    /**
     * tryGenerateNextPiece lines 176-195: the depth-50 cap and the
     * ±112-block leash from the start's own corner, then the piece joins
     * BOTH lists (the outer walk drains its addChildren later).
     */
    static StructurePiece generateNextPiece(StrongholdStart startPiece,
                                            List<StructurePiece> pieces, Random random,
                                            int x, int y, int z, StructureFacing facing,
                                            int generationDepth) {
        if (generationDepth > 50) {
            return null;
        }
        if (Math.abs(x - startPiece.getBounds().minX) <= 112
                && Math.abs(z - startPiece.getBounds().minZ) <= 112) {
            StructurePiece piece = rollNextPiece(startPiece, pieces, random, x, y, z,
                    facing, generationDepth + 1);
            if (piece != null) {
                pieces.add(piece);
                startPiece.children.add(piece);
            }
            return piece;
        }
        return null;
    }

    /** createPiece lines 71-109: the kind dispatch to the of() factories. */
    private static StructurePiece createPiece(PieceKind kind, List<StructurePiece> pieces,
                                              Random random, int x, int y, int z,
                                              StructureFacing facing, int generationDepth) {
        return switch (kind) {
            case STRAIGHT_CORRIDOR -> StraightCorridor.of(pieces, random, x, y, z, facing, generationDepth);
            case PRISON -> Prison.of(pieces, random, x, y, z, facing, generationDepth);
            case LEFT_TURN -> LeftTurn.of(pieces, random, x, y, z, facing, generationDepth);
            case RIGHT_TURN -> RightTurn.of(pieces, random, x, y, z, facing, generationDepth);
            case ROOM_CROSSING -> RoomCrossing.of(pieces, random, x, y, z, facing, generationDepth);
            case STRAIGHT_STAIRS -> StraightStairs.of(pieces, random, x, y, z, facing, generationDepth);
            case SPIRAL_STAIRCASE -> SpiralStaircase.of(pieces, random, x, y, z, facing, generationDepth);
            case FIVE_WAY_CROSSING -> FiveWayCrossing.of(pieces, random, x, y, z, facing, generationDepth);
            case CHEST_CORRIDOR -> ChestCorridor.of(pieces, random, x, y, z, facing, generationDepth);
            case LIBRARY -> Library.of(pieces, random, x, y, z, facing, generationDepth);
            case END_PORTAL_ROOM -> EndPortalRoom.of(pieces, random, x, y, z, facing, generationDepth);
        };
    }

    // --------------------------------------------------------- the stone picker

    /**
     * The stone-brick picker (StoneBrickPicker lines 1170-1187, 1:1): the
     * reference's own band — {@code < 0.2} cracked, {@code < 0.5} mossy,
     * {@code < 0.55} the silverfish monster egg, else the plain brick.
     */
    private static StructurePiece.BlockPicker stoneBrickPicker(StructureWorld world) {
        StructurePalette palette = world.palette();
        return (random, x, y, z, shell) -> {
            float roll = random.nextFloat();
            if (roll < 0.2F) {
                return palette.crackedStoneBricks();
            }
            if (roll < 0.5F) {
                return palette.mossyStoneBricks();
            }
            if (roll < 0.55F) {
                return palette.infestedStoneBricks();
            }
            return palette.stoneBricks();
        };
    }

    // ------------------------------------------------------------- the pieces

    /**
     * The straight corridor (StraightCorridor lines 1125-1247): the
     * 5x5x7 hallway with the torch threshold rolls and the two side
     * openings.
     */
    static final class StraightCorridor extends StrongholdPiece {
        private final boolean left;
        private final boolean right;

        StraightCorridor(int generationDepth, Random random, StructureBox bounds,
                         StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = pickEntranceType(random);
            this.bounds = bounds;
            this.left = random.nextInt(2) == 0;
            this.right = random.nextInt(2) == 0;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((StrongholdStart) start, pieces, random, 1, 1);
            if (this.left) {
                this.generatePieceLeft((StrongholdStart) start, pieces, random, 1, 2);
            }
            if (this.right) {
                this.generatePieceRight((StrongholdStart) start, pieces, random, 1, 2);
            }
        }

        static StraightCorridor of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                                   StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -1, -1, 0, 5, 5, 7, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new StraightCorridor(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            StructurePalette palette = world.palette();
            this.fillPicked(world, clip, 0, 0, 0, 4, 4, 6, true, random, stoneBrickPicker(world));
            this.generateEntrance(world, random, clip, this.entranceType, 1, 1, 0);
            this.generateEntrance(world, random, clip, EntranceType.OPENING, 1, 1, 6);
            this.setBlockStateWithThreshold(world, clip, random, 0.1F, 1, 2, 1, palette.torch());
            this.setBlockStateWithThreshold(world, clip, random, 0.1F, 3, 2, 1, palette.torch());
            this.setBlockStateWithThreshold(world, clip, random, 0.1F, 1, 2, 5, palette.torch());
            this.setBlockStateWithThreshold(world, clip, random, 0.1F, 3, 2, 5, palette.torch());
            if (this.left) {
                this.fillWithOutline(world, clip, 0, 1, 2, 0, 3, 4, palette.air(), palette.air(), false);
            }
            if (this.right) {
                this.fillWithOutline(world, clip, 4, 1, 2, 4, 3, 4, palette.air(), palette.air(), false);
            }
            return true;
        }
    }

    /**
     * The prison (Prison lines 789-866): the 9x5x11 block with the iron
     * bar cells and the pair of iron doors.
     */
    static final class Prison extends StrongholdPiece {

        Prison(int generationDepth, Random random, StructureBox bounds, StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = pickEntranceType(random);
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((StrongholdStart) start, pieces, random, 1, 1);
        }

        static Prison of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                         StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -1, -1, 0, 9, 5, 11, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new Prison(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            StructurePalette palette = world.palette();
            BlockPicker picker = stoneBrickPicker(world);
            this.fillPicked(world, clip, 0, 0, 0, 8, 4, 10, true, random, picker);
            this.generateEntrance(world, random, clip, this.entranceType, 1, 1, 0);
            this.fillWithOutline(world, clip, 1, 1, 10, 3, 3, 10, palette.air(), palette.air(), false);
            this.fillPicked(world, clip, 4, 1, 1, 4, 3, 1, false, random, picker);
            this.fillPicked(world, clip, 4, 1, 3, 4, 3, 3, false, random, picker);
            this.fillPicked(world, clip, 4, 1, 7, 4, 3, 7, false, random, picker);
            this.fillPicked(world, clip, 4, 1, 9, 4, 3, 9, false, random, picker);
            this.fillWithOutline(world, clip, 4, 1, 4, 4, 3, 6, palette.ironBars(), palette.ironBars(), false);
            this.fillWithOutline(world, clip, 5, 1, 5, 7, 3, 5, palette.ironBars(), palette.ironBars(), false);
            this.setBlockState(world, palette.ironBars(), 4, 3, 2, clip);
            this.setBlockState(world, palette.ironBars(), 4, 3, 8, clip);
            // The iron door pair: the facing flip on the reference's own
            // metadata band (3 + the upper's +8).
            int doorMetadata = this.postProcessMetadata(MetadataFamily.DOOR, 3);
            this.setBlockState(world, palette.ironDoorLower(doorMetadata), 4, 1, 2, clip);
            this.setBlockState(world, palette.ironDoorUpper(), 4, 2, 2, clip);
            this.setBlockState(world, palette.ironDoorLower(doorMetadata), 4, 1, 8, clip);
            this.setBlockState(world, palette.ironDoorUpper(), 4, 2, 8, clip);
            return true;
        }
    }

    /**
     * The left turn (LeftTurn lines 540-575): the 5x5x5 elbow — the exit
     * wall swaps with the facing.
     */
    static class LeftTurn extends StrongholdPiece {

        LeftTurn(int generationDepth, Random random, StructureBox bounds, StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = pickEntranceType(random);
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            if (this.facing != StructureFacing.NORTH && this.facing != StructureFacing.EAST) {
                this.generatePieceRight((StrongholdStart) start, pieces, random, 1, 1);
            } else {
                this.generatePieceLeft((StrongholdStart) start, pieces, random, 1, 1);
            }
        }

        static LeftTurn of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                           StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -1, -1, 0, 5, 5, 5, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new LeftTurn(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            StructurePalette palette = world.palette();
            this.fillPicked(world, clip, 0, 0, 0, 4, 4, 4, true, random, stoneBrickPicker(world));
            this.generateEntrance(world, random, clip, this.entranceType, 1, 1, 0);
            if (this.facing != StructureFacing.NORTH && this.facing != StructureFacing.EAST) {
                this.fillWithOutline(world, clip, 4, 1, 1, 4, 3, 3, palette.air(), palette.air(), false);
            } else {
                this.fillWithOutline(world, clip, 0, 1, 1, 0, 3, 3, palette.air(), palette.air(), false);
            }
            return true;
        }
    }

    /**
     * The right turn (RightTurn lines 868-899): the mirrored elbow — the
     * same box, the swapped exit arm.
     */
    static final class RightTurn extends LeftTurn {

        RightTurn(int generationDepth, Random random, StructureBox bounds, StructureFacing facing) {
            super(generationDepth, random, bounds, facing);
        }

        static RightTurn of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                            StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -1, -1, 0, 5, 5, 5, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new RightTurn(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            if (this.facing != StructureFacing.NORTH && this.facing != StructureFacing.EAST) {
                this.generatePieceLeft((StrongholdStart) start, pieces, random, 1, 1);
            } else {
                this.generatePieceRight((StrongholdStart) start, pieces, random, 1, 1);
            }
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            StructurePalette palette = world.palette();
            this.fillPicked(world, clip, 0, 0, 0, 4, 4, 4, true, random, stoneBrickPicker(world));
            this.generateEntrance(world, random, clip, this.entranceType, 1, 1, 0);
            if (this.facing != StructureFacing.NORTH && this.facing != StructureFacing.EAST) {
                this.fillWithOutline(world, clip, 0, 1, 1, 0, 3, 3, palette.air(), palette.air(), false);
            } else {
                this.fillWithOutline(world, clip, 4, 1, 1, 4, 3, 3, palette.air(), palette.air(), false);
            }
            return true;
        }
    }

    /**
     * The room crossing (RoomCrossing lines 845-1007): the 11x7x11 hub
     * with the three interior types (the fountain pillar, the lava
     * brazier, the storage loft).
     */
    static final class RoomCrossing extends StrongholdPiece {
        private final int type;

        RoomCrossing(int generationDepth, Random random, StructureBox bounds,
                     StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = pickEntranceType(random);
            this.bounds = bounds;
            this.type = random.nextInt(5);
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((StrongholdStart) start, pieces, random, 4, 1);
            this.generatePieceLeft((StrongholdStart) start, pieces, random, 1, 4);
            this.generatePieceRight((StrongholdStart) start, pieces, random, 1, 4);
        }

        static RoomCrossing of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                               StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -4, -1, 0, 11, 7, 11, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new RoomCrossing(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            StructurePalette palette = world.palette();
            BlockPicker picker = stoneBrickPicker(world);
            this.fillPicked(world, clip, 0, 0, 0, 10, 6, 10, true, random, picker);
            this.generateEntrance(world, random, clip, this.entranceType, 4, 1, 0);
            this.fillWithOutline(world, clip, 4, 1, 10, 6, 3, 10, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 1, 4, 0, 3, 6, palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 10, 1, 4, 10, 3, 6, palette.air(), palette.air(), false);
            switch (this.type) {
                case 0 -> {
                    this.setBlockState(world, palette.stoneBricks(), 5, 1, 5, clip);
                    this.setBlockState(world, palette.stoneBricks(), 5, 2, 5, clip);
                    this.setBlockState(world, palette.stoneBricks(), 5, 3, 5, clip);
                    this.setBlockState(world, palette.torch(), 4, 3, 5, clip);
                    this.setBlockState(world, palette.torch(), 6, 3, 5, clip);
                    this.setBlockState(world, palette.torch(), 5, 3, 4, clip);
                    this.setBlockState(world, palette.torch(), 5, 3, 6, clip);
                    this.setBlockState(world, palette.stoneSlab(), 4, 1, 4, clip);
                    this.setBlockState(world, palette.stoneSlab(), 4, 1, 5, clip);
                    this.setBlockState(world, palette.stoneSlab(), 4, 1, 6, clip);
                    this.setBlockState(world, palette.stoneSlab(), 6, 1, 4, clip);
                    this.setBlockState(world, palette.stoneSlab(), 6, 1, 5, clip);
                    this.setBlockState(world, palette.stoneSlab(), 6, 1, 6, clip);
                    this.setBlockState(world, palette.stoneSlab(), 5, 1, 4, clip);
                    this.setBlockState(world, palette.stoneSlab(), 5, 1, 6, clip);
                }
                case 1 -> {
                    for (int i1 = 0; i1 < 5; i1++) {
                        this.setBlockState(world, palette.stoneBricks(), 3, 1, 3 + i1, clip);
                        this.setBlockState(world, palette.stoneBricks(), 7, 1, 3 + i1, clip);
                        this.setBlockState(world, palette.stoneBricks(), 3 + i1, 1, 3, clip);
                        this.setBlockState(world, palette.stoneBricks(), 3 + i1, 1, 7, clip);
                    }
                    this.setBlockState(world, palette.stoneBricks(), 5, 1, 5, clip);
                    this.setBlockState(world, palette.stoneBricks(), 5, 2, 5, clip);
                    this.setBlockState(world, palette.stoneBricks(), 5, 3, 5, clip);
                    this.setBlockState(world, palette.water(), 5, 4, 5, clip);
                }
                case 2 -> {
                    for (int i = 1; i <= 9; i++) {
                        this.setBlockState(world, palette.cobblestone(), 1, 3, i, clip);
                        this.setBlockState(world, palette.cobblestone(), 9, 3, i, clip);
                    }
                    for (int j = 1; j <= 9; j++) {
                        this.setBlockState(world, palette.cobblestone(), j, 3, 1, clip);
                        this.setBlockState(world, palette.cobblestone(), j, 3, 9, clip);
                    }
                    this.setBlockState(world, palette.cobblestone(), 5, 1, 4, clip);
                    this.setBlockState(world, palette.cobblestone(), 5, 1, 6, clip);
                    this.setBlockState(world, palette.cobblestone(), 5, 3, 4, clip);
                    this.setBlockState(world, palette.cobblestone(), 5, 3, 6, clip);
                    this.setBlockState(world, palette.cobblestone(), 4, 1, 5, clip);
                    this.setBlockState(world, palette.cobblestone(), 6, 1, 5, clip);
                    this.setBlockState(world, palette.cobblestone(), 4, 3, 5, clip);
                    this.setBlockState(world, palette.cobblestone(), 6, 3, 5, clip);
                    for (int k = 1; k <= 3; k++) {
                        this.setBlockState(world, palette.cobblestone(), 4, k, 4, clip);
                        this.setBlockState(world, palette.cobblestone(), 6, k, 4, clip);
                        this.setBlockState(world, palette.cobblestone(), 4, k, 6, clip);
                        this.setBlockState(world, palette.cobblestone(), 6, k, 6, clip);
                    }
                    this.setBlockState(world, palette.torch(), 5, 3, 5, clip);
                    for (int l = 2; l <= 8; l++) {
                        this.setBlockState(world, palette.planks(), 2, 3, l, clip);
                        this.setBlockState(world, palette.planks(), 3, 3, l, clip);
                        if (l <= 3 || l >= 7) {
                            this.setBlockState(world, palette.planks(), 4, 3, l, clip);
                            this.setBlockState(world, palette.planks(), 5, 3, l, clip);
                            this.setBlockState(world, palette.planks(), 6, 3, l, clip);
                        }
                        this.setBlockState(world, palette.planks(), 7, 3, l, clip);
                        this.setBlockState(world, palette.planks(), 8, 3, l, clip);
                    }
                    // The loft ladder: the reference passes Direction.WEST's
                    // OWN id (4 — the ladder's metadata band, not the
                    // horizontal band), the flip per the facing.
                    int ladderMetadata = this.postProcessMetadata(MetadataFamily.LADDER, 4);
                    for (int step = 1; step <= 3; step++) {
                        this.setBlockState(world, palette.ladder(ladderMetadata), 9, step, 3, clip);
                    }
                    // The loft chest: the same table with its own book roll.
                    StrongholdPieces.placeChestWithLoot(this, world, random, clip, 3, 4, 8,
                            ROOM_CROSSING_LOOT, StrongholdPieces.rollBookLoot(random, 1, 1, 1),
                            1 + random.nextInt(4));
                }
                default -> {
                    // The reference's 3/4 arms: the empty room (the type
                    // dice's 0-in-5 miss — no interior).
                }
            }
            return true;
        }
    }

    /**
     * The straight stairs (StraightStairs lines 1249-1313): the 5x11x8
     * six-step descent with the stone-stair flights.
     */
    static final class StraightStairs extends StrongholdPiece {

        StraightStairs(int generationDepth, Random random, StructureBox bounds,
                       StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = pickEntranceType(random);
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((StrongholdStart) start, pieces, random, 1, 1);
        }

        static StraightStairs of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                                  StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -1, -7, 0, 5, 11, 8, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new StraightStairs(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            StructurePalette palette = world.palette();
            this.fillPicked(world, clip, 0, 0, 0, 4, 10, 7, true, random, stoneBrickPicker(world));
            this.generateEntrance(world, random, clip, this.entranceType, 1, 7, 0);
            this.generateEntrance(world, random, clip, EntranceType.OPENING, 1, 1, 7);
            int stairsMetadata = this.postProcessMetadata(MetadataFamily.STAIRS, 2);
            for (int j = 0; j < 6; j++) {
                this.setBlockState(world, palette.stoneStairs(stairsMetadata), 1, 6 - j, 1 + j, clip);
                this.setBlockState(world, palette.stoneStairs(stairsMetadata), 2, 6 - j, 1 + j, clip);
                this.setBlockState(world, palette.stoneStairs(stairsMetadata), 3, 6 - j, 1 + j, clip);
                if (j < 5) {
                    this.setBlockState(world, palette.stoneBricks(), 1, 5 - j, 1 + j, clip);
                    this.setBlockState(world, palette.stoneBricks(), 2, 5 - j, 1 + j, clip);
                    this.setBlockState(world, palette.stoneBricks(), 3, 5 - j, 1 + j, clip);
                }
            }
            return true;
        }
    }

    /**
     * The spiral staircase (SpiralStaircase lines 1009-1140): the
     * 5x11x5 shaft with the winding stone-brick steps. The source variant
     * (the reference's Start shape) carries the random facing and the
     * y-64 anchor box before the re-base.
     */
    static class SpiralStaircase extends StrongholdPiece {
        private final boolean isSource;

        SpiralStaircase(int generationDepth, Random random, StructureBox bounds,
                        StructureFacing facing) {
            this(false, generationDepth, bounds, facing, random);
        }

        /** The source shape (the reference's five-arg constructor, lines 1065-1076). */
        protected SpiralStaircase(boolean source, int generationDepth, Random random,
                                  int x, int z) {
            super(generationDepth);
            this.isSource = source;
            this.facing = StructureFacing.pickHorizontal(random);
            this.entranceType = EntranceType.OPENING;
            this.bounds = new StructureBox(x, 64, z, x + 5 - 1, 74, z + 5 - 1);
        }

        private SpiralStaircase(boolean notSource, int generationDepth, StructureBox bounds,
                                StructureFacing facing, Random random) {
            super(generationDepth);
            this.isSource = notSource;
            this.facing = facing;
            this.entranceType = pickEntranceType(random);
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            if (this.isSource) {
                ((StrongholdStart) start).walk.forcedPiece = PieceKind.FIVE_WAY_CROSSING;
            }
            this.generatePieceForward((StrongholdStart) start, pieces, random, 1, 1);
        }

        static SpiralStaircase of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                                  StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -1, -7, 0, 5, 11, 5, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new SpiralStaircase(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            StructurePalette palette = world.palette();
            this.fillPicked(world, clip, 0, 0, 0, 4, 10, 4, true, random, stoneBrickPicker(world));
            this.generateEntrance(world, random, clip, this.entranceType, 1, 7, 0);
            this.generateEntrance(world, random, clip, EntranceType.OPENING, 1, 1, 4);
            this.setBlockState(world, palette.stoneBricks(), 2, 6, 1, clip);
            this.setBlockState(world, palette.stoneBricks(), 1, 5, 1, clip);
            this.setBlockState(world, palette.stoneSlab(), 1, 6, 1, clip);
            this.setBlockState(world, palette.stoneBricks(), 1, 5, 2, clip);
            this.setBlockState(world, palette.stoneBricks(), 1, 4, 3, clip);
            this.setBlockState(world, palette.stoneSlab(), 1, 5, 3, clip);
            this.setBlockState(world, palette.stoneBricks(), 2, 4, 3, clip);
            this.setBlockState(world, palette.stoneBricks(), 3, 3, 3, clip);
            this.setBlockState(world, palette.stoneSlab(), 3, 4, 3, clip);
            this.setBlockState(world, palette.stoneBricks(), 3, 3, 2, clip);
            this.setBlockState(world, palette.stoneBricks(), 3, 2, 1, clip);
            this.setBlockState(world, palette.stoneSlab(), 3, 3, 1, clip);
            this.setBlockState(world, palette.stoneBricks(), 2, 2, 1, clip);
            this.setBlockState(world, palette.stoneBricks(), 1, 1, 1, clip);
            this.setBlockState(world, palette.stoneSlab(), 1, 2, 1, clip);
            this.setBlockState(world, palette.stoneBricks(), 1, 1, 2, clip);
            this.setBlockState(world, palette.stoneSlab(), 1, 1, 3, clip);
            return true;
        }
    }

    /**
     * The five-way crossing (FiveWayCrossing lines 396-538): the
     * 10x9x11 hub with the two floors and the four side arms.
     */
    static final class FiveWayCrossing extends StrongholdPiece {
        private final boolean leftLow;
        private final boolean leftHigh;
        private final boolean rightLow;
        private final boolean rightHigh;

        FiveWayCrossing(int generationDepth, Random random, StructureBox bounds,
                        StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = pickEntranceType(random);
            this.bounds = bounds;
            this.leftLow = random.nextBoolean();
            this.leftHigh = random.nextBoolean();
            this.rightLow = random.nextBoolean();
            this.rightHigh = random.nextInt(3) > 0;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            int low = 3;
            int high = 5;
            if (this.facing == StructureFacing.WEST || this.facing == StructureFacing.NORTH) {
                low = 8 - low;
                high = 8 - high;
            }
            this.generatePieceForward((StrongholdStart) start, pieces, random, 5, 1);
            if (this.leftLow) {
                this.generatePieceLeft((StrongholdStart) start, pieces, random, low, 1);
            }
            if (this.leftHigh) {
                this.generatePieceLeft((StrongholdStart) start, pieces, random, high, 7);
            }
            if (this.rightLow) {
                this.generatePieceRight((StrongholdStart) start, pieces, random, low, 1);
            }
            if (this.rightHigh) {
                this.generatePieceRight((StrongholdStart) start, pieces, random, high, 7);
            }
        }

        static FiveWayCrossing of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                                  StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -4, -3, 0, 10, 9, 11, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new FiveWayCrossing(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            StructurePalette palette = world.palette();
            BlockPicker picker = stoneBrickPicker(world);
            this.fillPicked(world, clip, 0, 0, 0, 9, 8, 10, true, random, picker);
            this.generateEntrance(world, random, clip, this.entranceType, 4, 3, 0);
            if (this.leftLow) {
                this.fillWithOutline(world, clip, 0, 3, 1, 0, 5, 3, palette.air(), palette.air(), false);
            }
            if (this.rightLow) {
                this.fillWithOutline(world, clip, 9, 3, 1, 9, 5, 3, palette.air(), palette.air(), false);
            }
            if (this.leftHigh) {
                this.fillWithOutline(world, clip, 0, 5, 7, 0, 7, 9, palette.air(), palette.air(), false);
            }
            if (this.rightHigh) {
                this.fillWithOutline(world, clip, 9, 5, 7, 9, 7, 9, palette.air(), palette.air(), false);
            }
            this.fillWithOutline(world, clip, 5, 1, 10, 7, 3, 10, palette.air(), palette.air(), false);
            this.fillPicked(world, clip, 1, 2, 1, 8, 2, 6, false, random, picker);
            this.fillPicked(world, clip, 4, 1, 5, 4, 4, 9, false, random, picker);
            this.fillPicked(world, clip, 8, 1, 5, 8, 4, 9, false, random, picker);
            this.fillPicked(world, clip, 1, 4, 7, 3, 4, 9, false, random, picker);
            this.fillPicked(world, clip, 1, 3, 5, 3, 3, 6, false, random, picker);
            this.fillWithOutline(world, clip, 1, 3, 4, 3, 3, 4, palette.stoneSlab(), palette.stoneSlab(), false);
            this.fillWithOutline(world, clip, 1, 4, 6, 3, 4, 6, palette.stoneSlab(), palette.stoneSlab(), false);
            this.fillPicked(world, clip, 5, 1, 7, 7, 1, 8, false, random, picker);
            this.fillWithOutline(world, clip, 5, 1, 9, 7, 1, 9, palette.stoneSlab(), palette.stoneSlab(), false);
            this.fillWithOutline(world, clip, 5, 2, 7, 7, 2, 7, palette.stoneSlab(), palette.stoneSlab(), false);
            this.fillWithOutline(world, clip, 4, 5, 7, 4, 5, 9, palette.stoneSlab(), palette.stoneSlab(), false);
            this.fillWithOutline(world, clip, 8, 5, 7, 8, 5, 9, palette.stoneSlab(), palette.stoneSlab(), false);
            this.fillWithOutline(world, clip, 5, 5, 7, 7, 5, 9, palette.doubleStoneSlab(), palette.doubleStoneSlab(), false);
            this.setBlockState(world, palette.torch(), 6, 5, 6, clip);
            return true;
        }
    }

    /**
     * The chest corridor (ChestCorridor lines 198-284): the 5x5x7 hallway
     * with the smooth-slab pedestal and the loot chest.
     */
    static final class ChestCorridor extends StrongholdPiece {
        private boolean hasChest;

        ChestCorridor(int generationDepth, Random random, StructureBox bounds,
                      StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = pickEntranceType(random);
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            this.generatePieceForward((StrongholdStart) start, pieces, random, 1, 1);
        }

        static ChestCorridor of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                                StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -1, -1, 0, 5, 5, 7, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new ChestCorridor(generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            StructurePalette palette = world.palette();
            this.fillPicked(world, clip, 0, 0, 0, 4, 4, 6, true, random, stoneBrickPicker(world));
            this.generateEntrance(world, random, clip, this.entranceType, 1, 1, 0);
            this.generateEntrance(world, random, clip, EntranceType.OPENING, 1, 1, 6);
            this.fillWithOutline(world, clip, 3, 1, 2, 3, 1, 4, palette.stoneBricks(), palette.stoneBricks(), false);
            this.setBlockState(world, palette.stoneBrickSlab(), 3, 1, 1, clip);
            this.setBlockState(world, palette.stoneBrickSlab(), 3, 1, 5, clip);
            this.setBlockState(world, palette.stoneBrickSlab(), 3, 2, 2, clip);
            this.setBlockState(world, palette.stoneBrickSlab(), 3, 2, 4, clip);
            for (int i = 2; i <= 4; i++) {
                this.setBlockState(world, palette.stoneBrickSlab(), 2, 1, i, clip);
            }
            if (!this.hasChest
                    && clip.contains(this.transformX(3, 3), this.transformY(2), this.transformZ(3, 3))) {
                this.hasChest = true;
                // The reference's own roll order: the enchanted book's
                // createLoot first (the addAll argument), then the amount.
                StrongholdPieces.placeChestWithLoot(this, world, random, clip, 3, 2, 3,
                        CHEST_CORRIDOR_LOOT, StrongholdPieces.rollBookLoot(random, 1, 1, 1),
                        2 + random.nextInt(2));
            }
            return true;
        }
    }

    /**
     * The library (Library lines 577-787): the 14x11x15 (or the 6-tall)
     * reading hall — the shelf walls, the upper gallery, the cobwebs,
     * and the pair of loot chests.
     */
    static final class Library extends StrongholdPiece {
        private final boolean tall;

        Library(int generationDepth, Random random, StructureBox bounds, StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.entranceType = pickEntranceType(random);
            this.bounds = bounds;
            this.tall = bounds.getSpanY() > 6;
        }

        static Library of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                          StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -4, -1, 0, 14, 11, 15, facing);
            if (!isValidBounds(box) || StructurePiece.getIntersectingPiece(pieces, box) != null) {
                box = StructureBox.orient(x, y, z, -4, -1, 0, 14, 6, 15, facing);
                if (!isValidBounds(box) || StructurePiece.getIntersectingPiece(pieces, box) != null) {
                    return null;
                }
            }
            return new Library(generationDepth, random, box, facing);
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            StructurePalette palette = world.palette();
            int ceiling = this.tall ? 11 : 6;
            this.fillPicked(world, clip, 0, 0, 0, 13, ceiling - 1, 14, true, random,
                    stoneBrickPicker(world));
            this.generateEntrance(world, random, clip, this.entranceType, 4, 1, 0);
            this.fillRandomlyWithOutline(world, clip, random, 0.07F, 2, 1, 1, 11, 4, 13,
                    palette.cobweb(), palette.cobweb(), false);
            for (int l = 1; l <= 13; l++) {
                if ((l - 1) % 4 == 0) {
                    this.fillWithOutline(world, clip, 1, 1, l, 1, 4, l, palette.planks(), palette.planks(), false);
                    this.fillWithOutline(world, clip, 12, 1, l, 12, 4, l, palette.planks(), palette.planks(), false);
                    this.setBlockState(world, palette.torch(), 2, 3, l, clip);
                    this.setBlockState(world, palette.torch(), 11, 3, l, clip);
                    if (this.tall) {
                        this.fillWithOutline(world, clip, 1, 6, l, 1, 9, l, palette.planks(), palette.planks(), false);
                        this.fillWithOutline(world, clip, 12, 6, l, 12, 9, l, palette.planks(), palette.planks(), false);
                    }
                } else {
                    this.fillWithOutline(world, clip, 1, 1, l, 1, 4, l, palette.bookshelf(), palette.bookshelf(), false);
                    this.fillWithOutline(world, clip, 12, 1, l, 12, 4, l, palette.bookshelf(), palette.bookshelf(), false);
                    if (this.tall) {
                        this.fillWithOutline(world, clip, 1, 6, l, 1, 9, l, palette.bookshelf(), palette.bookshelf(), false);
                        this.fillWithOutline(world, clip, 12, 6, l, 12, 9, l, palette.bookshelf(), palette.bookshelf(), false);
                    }
                }
            }
            for (int k1 = 3; k1 < 12; k1 += 2) {
                this.fillWithOutline(world, clip, 3, 1, k1, 4, 3, k1, palette.bookshelf(), palette.bookshelf(), false);
                this.fillWithOutline(world, clip, 6, 1, k1, 7, 3, k1, palette.bookshelf(), palette.bookshelf(), false);
                this.fillWithOutline(world, clip, 9, 1, k1, 10, 3, k1, palette.bookshelf(), palette.bookshelf(), false);
            }
            if (this.tall) {
                this.fillWithOutline(world, clip, 1, 5, 1, 3, 5, 13, palette.planks(), palette.planks(), false);
                this.fillWithOutline(world, clip, 10, 5, 1, 12, 5, 13, palette.planks(), palette.planks(), false);
                this.fillWithOutline(world, clip, 4, 5, 1, 9, 5, 2, palette.planks(), palette.planks(), false);
                this.fillWithOutline(world, clip, 4, 5, 12, 9, 5, 13, palette.planks(), palette.planks(), false);
                this.setBlockState(world, palette.planks(), 9, 5, 11, clip);
                this.setBlockState(world, palette.planks(), 8, 5, 11, clip);
                this.setBlockState(world, palette.planks(), 9, 5, 10, clip);
                this.fillWithOutline(world, clip, 3, 6, 2, 3, 6, 12, palette.fence(), palette.fence(), false);
                this.fillWithOutline(world, clip, 10, 6, 2, 10, 6, 10, palette.fence(), palette.fence(), false);
                this.fillWithOutline(world, clip, 4, 6, 2, 9, 6, 2, palette.fence(), palette.fence(), false);
                this.fillWithOutline(world, clip, 4, 6, 12, 8, 6, 12, palette.fence(), palette.fence(), false);
                this.setBlockState(world, palette.fence(), 9, 6, 11, clip);
                this.setBlockState(world, palette.fence(), 8, 6, 11, clip);
                this.setBlockState(world, palette.fence(), 9, 6, 10, clip);
                // The gallery ladder: the EAST facing id, the metadata flip.
                int ladderMetadata = this.postProcessMetadata(MetadataFamily.LADDER, 3);
                for (int step = 1; step <= 7; step++) {
                    this.setBlockState(world, palette.ladder(ladderMetadata), 10, step, 13, clip);
                }
                // The gallery's fence-and-torch pedestal (lines 731-747).
                int center = 7;
                this.setBlockState(world, palette.fence(), center - 1, 9, center, clip);
                this.setBlockState(world, palette.fence(), center, 9, center, clip);
                this.setBlockState(world, palette.fence(), center - 1, 8, center, clip);
                this.setBlockState(world, palette.fence(), center, 8, center, clip);
                this.setBlockState(world, palette.fence(), center - 1, 7, center, clip);
                this.setBlockState(world, palette.fence(), center, 7, center, clip);
                this.setBlockState(world, palette.fence(), center - 2, 7, center, clip);
                this.setBlockState(world, palette.fence(), center + 1, 7, center, clip);
                this.setBlockState(world, palette.fence(), center - 1, 7, center - 1, clip);
                this.setBlockState(world, palette.fence(), center - 1, 7, center + 1, clip);
                this.setBlockState(world, palette.fence(), center, 7, center - 1, clip);
                this.setBlockState(world, palette.fence(), center, 7, center + 1, clip);
                this.setBlockState(world, palette.torch(), center - 2, 8, center, clip);
                this.setBlockState(world, palette.torch(), center + 1, 8, center, clip);
                this.setBlockState(world, palette.torch(), center - 1, 8, center - 1, clip);
                this.setBlockState(world, palette.torch(), center - 1, 8, center + 1, clip);
                this.setBlockState(world, palette.torch(), center, 8, center - 1, clip);
                this.setBlockState(world, palette.torch(), center, 8, center + 1, clip);
            }
            StrongholdPieces.placeChestWithLoot(this, world, random, clip, 3, 3, 5, LIBRARY_LOOT,
                    StrongholdPieces.rollBookLoot(random, 1, 5, 2), 1 + random.nextInt(4));
            if (this.tall) {
                this.setBlockState(world, palette.air(), 12, 9, 1, clip);
                StrongholdPieces.placeChestWithLoot(this, world, random, clip, 12, 8, 1, LIBRARY_LOOT,
                        StrongholdPieces.rollBookLoot(random, 1, 5, 2), 1 + random.nextInt(4));
            }
            return true;
        }
    }

    /**
     * The plain corridor (PlainCorridor lines 664-787): the stone-brick
     * tunnel that fills the leftover gap between two pieces — the
     * findSize probe shrinks it until it escapes the neighbor's box.
     */
    static final class PlainCorridor extends StrongholdPiece {
        private final int steps;

        PlainCorridor(int generationDepth, StructureBox bounds, StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.steps = facing != StructureFacing.NORTH && facing != StructureFacing.SOUTH
                    ? bounds.getSpanX() : bounds.getSpanZ();
        }

        /**
         * findSize lines 706-731: the 5x5x4 probe must touch an existing
         * piece at the same floor level, then the shrink walk peels the
         * length until the escape.
         */
        static StructureBox findSize(List<StructurePiece> pieces, int x, int y, int z,
                                     StructureFacing facing) {
            StructureBox box = StructureBox.orient(x, y, z, -1, -1, 0, 5, 5, 4, facing);
            StructurePiece piece = StructurePiece.getIntersectingPiece(pieces, box);
            if (piece == null) {
                return null;
            }
            if (piece.getBounds().minY == box.minY) {
                for (int length = 3; length >= 1; length--) {
                    box = StructureBox.orient(x, y, z, -1, -1, 0, 5, 5, length - 1, facing);
                    if (!piece.getBounds().intersects(box)) {
                        return StructureBox.orient(x, y, z, -1, -1, 0, 5, 5, length, facing);
                    }
                }
            }
            return null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            StructurePalette palette = world.palette();
            for (int i = 0; i < this.steps; i++) {
                this.setBlockState(world, palette.stoneBricks(), 0, 0, i, clip);
                this.setBlockState(world, palette.stoneBricks(), 1, 0, i, clip);
                this.setBlockState(world, palette.stoneBricks(), 2, 0, i, clip);
                this.setBlockState(world, palette.stoneBricks(), 3, 0, i, clip);
                this.setBlockState(world, palette.stoneBricks(), 4, 0, i, clip);
                for (int j = 1; j <= 3; j++) {
                    this.setBlockState(world, palette.stoneBricks(), 0, j, i, clip);
                    this.setBlockState(world, palette.air(), 1, j, i, clip);
                    this.setBlockState(world, palette.air(), 2, j, i, clip);
                    this.setBlockState(world, palette.air(), 3, j, i, clip);
                    this.setBlockState(world, palette.stoneBricks(), 4, j, i, clip);
                }
                this.setBlockState(world, palette.stoneBricks(), 0, 4, i, clip);
                this.setBlockState(world, palette.stoneBricks(), 1, 4, i, clip);
                this.setBlockState(world, palette.stoneBricks(), 2, 4, i, clip);
                this.setBlockState(world, palette.stoneBricks(), 3, 4, i, clip);
                this.setBlockState(world, palette.stoneBricks(), 4, 4, i, clip);
            }
            return true;
        }
    }

    /**
     * The end portal room (EndPortalRoom lines 286-394): the 11x8x16
     * finale — the lava moat, the iron bar walls, the stair platform,
     * the twelve portal frames with their eye rolls, and the silverfish
     * spawner.
     */
    static final class EndPortalRoom extends StrongholdPiece {
        private boolean hasSpawner;

        EndPortalRoom(int generationDepth, StructureBox bounds, StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            if (start instanceof StrongholdStart strongholdStart) {
                strongholdStart.endPortalRoom = this;
            }
        }

        static EndPortalRoom of(List<StructurePiece> pieces, Random random, int x, int y, int z,
                                StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, -4, -1, 0, 11, 8, 16, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new EndPortalRoom(generationDepth, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            BlockPicker picker = stoneBrickPicker(world);
            // The reference's only avoidAir=false shell: the whole box,
            // ceiling included, rolls the picker for every cell.
            this.fillPicked(world, clip, 0, 0, 0, 10, 7, 15, false, random, picker);
            this.generateEntrance(world, random, clip, EntranceType.GATES, 4, 1, 0);
            int ceiling = 6;
            this.fillPicked(world, clip, 1, ceiling, 1, 1, ceiling, 14, false, random, picker);
            this.fillPicked(world, clip, 9, ceiling, 1, 9, ceiling, 14, false, random, picker);
            this.fillPicked(world, clip, 2, ceiling, 1, 8, ceiling, 2, false, random, picker);
            this.fillPicked(world, clip, 2, ceiling, 14, 8, ceiling, 14, false, random, picker);
            this.fillPicked(world, clip, 1, 1, 1, 2, 1, 4, false, random, picker);
            this.fillPicked(world, clip, 8, 1, 1, 9, 1, 4, false, random, picker);
            this.fillWithOutline(world, clip, 1, 1, 1, 1, 1, 3, palette.lava(), palette.lava(), false);
            this.fillWithOutline(world, clip, 9, 1, 1, 9, 1, 3, palette.lava(), palette.lava(), false);
            this.fillPicked(world, clip, 3, 1, 8, 7, 1, 12, false, random, picker);
            this.fillWithOutline(world, clip, 4, 1, 9, 6, 1, 11, palette.lava(), palette.lava(), false);
            for (int j = 3; j < 14; j += 2) {
                this.fillWithOutline(world, clip, 0, 3, j, 0, 4, j, palette.ironBars(), palette.ironBars(), false);
                this.fillWithOutline(world, clip, 10, 3, j, 10, 4, j, palette.ironBars(), palette.ironBars(), false);
            }
            for (int k1 = 2; k1 < 9; k1 += 2) {
                this.fillWithOutline(world, clip, k1, 3, 15, k1, 4, 15, palette.ironBars(), palette.ironBars(), false);
            }
            int stairsMetadata = this.postProcessMetadata(MetadataFamily.STAIRS, 3);
            this.fillPicked(world, clip, 4, 1, 5, 6, 1, 7, false, random, picker);
            this.fillPicked(world, clip, 4, 2, 6, 6, 2, 7, false, random, picker);
            this.fillPicked(world, clip, 4, 3, 7, 6, 3, 7, false, random, picker);
            for (int k = 4; k <= 6; k++) {
                this.setBlockState(world, palette.stoneStairs(stairsMetadata), k, 1, 4, clip);
                this.setBlockState(world, palette.stoneStairs(stairsMetadata), k, 2, 5, clip);
                this.setBlockState(world, palette.stoneStairs(stairsMetadata), k, 3, 6, clip);
            }
            // The frame facings (lines 349-370): the base band N/S/E/W
            // with the SOUTH/WEST/EAST remaps.
            int north = StructureFacing.NORTH.horizontalId();
            int south = StructureFacing.SOUTH.horizontalId();
            int east = StructureFacing.EAST.horizontalId();
            int west = StructureFacing.WEST.horizontalId();
            if (this.facing != null) {
                switch (this.facing) {
                    case SOUTH -> {
                        north = StructureFacing.SOUTH.horizontalId();
                        south = StructureFacing.NORTH.horizontalId();
                    }
                    case WEST -> {
                        north = StructureFacing.WEST.horizontalId();
                        south = StructureFacing.EAST.horizontalId();
                        east = StructureFacing.SOUTH.horizontalId();
                        west = StructureFacing.NORTH.horizontalId();
                    }
                    case EAST -> {
                        north = StructureFacing.EAST.horizontalId();
                        south = StructureFacing.WEST.horizontalId();
                        east = StructureFacing.SOUTH.horizontalId();
                        west = StructureFacing.NORTH.horizontalId();
                    }
                    default -> {
                    }
                }
            }
            // The twelve frames with their eye rolls (nextFloat() > 0.9 —
            // the reference's own order: the near row, the far row, the
            // west column, the east column).
            this.setBlockState(world, frame(palette, north, random), 4, 3, 8, clip);
            this.setBlockState(world, frame(palette, north, random), 5, 3, 8, clip);
            this.setBlockState(world, frame(palette, north, random), 6, 3, 8, clip);
            this.setBlockState(world, frame(palette, south, random), 4, 3, 12, clip);
            this.setBlockState(world, frame(palette, south, random), 5, 3, 12, clip);
            this.setBlockState(world, frame(palette, south, random), 6, 3, 12, clip);
            this.setBlockState(world, frame(palette, east, random), 3, 3, 9, clip);
            this.setBlockState(world, frame(palette, east, random), 3, 3, 10, clip);
            this.setBlockState(world, frame(palette, east, random), 3, 3, 11, clip);
            this.setBlockState(world, frame(palette, west, random), 7, 3, 9, clip);
            this.setBlockState(world, frame(palette, west, random), 7, 3, 10, clip);
            this.setBlockState(world, frame(palette, west, random), 7, 3, 11, clip);
            // The silverfish spawner (lines 381-393): the block lands; the
            // spawner block entity ledgers with the mob-spawner slice.
            if (!this.hasSpawner) {
                int spawnerY = this.transformY(3);
                int spawnerX = this.transformX(5, 6);
                int spawnerZ = this.transformZ(5, 6);
                if (clip.contains(spawnerX, spawnerY, spawnerZ)) {
                    this.hasSpawner = true;
                    this.setBlockState(world, palette.mobSpawner(), 5, 3, 6, clip);
                }
            }
            return true;
        }

        /** One frame with its own eye roll (the reference's per-frame set(EYE, nextFloat() > 0.9F)). */
        private static BlockType frame(StructurePalette palette, int facingId, Random random) {
            boolean eye = random.nextFloat() > 0.9F;
            return palette.endPortalFrame(eye ? facingId | 4 : facingId);
        }
    }

    // ------------------------------------------------------------ the loot walk

    /** The enchanted-book loot row (EnchantedBookItem.createLoot's appended entry). */
    private record BookRow(int minCount, int maxCount, int weight, Map<Integer, Integer> enchantments) {
    }

    /**
     * The enchanted-book loot arm (EnchantedBookItem.createLoot lines
     * 91-100 + EnchantmentHelper.addRandomEnchantment lines 249-269): a
     * level-30 random enchantment over the book's take-everything pool,
     * the book converting to the enchanted form.
     */
    static BookRow rollBookLoot(Random random, int minCount, int maxCount, int weight) {
        var offers = net.zaminmc.torch.server.enchantment.EnchantmentHelper.buildOffers(
                random, net.zaminmc.torch.item.ItemStack.of(BuiltinItems.BOOK), 30);
        java.util.LinkedHashMap<Integer, Integer> enchantments = new java.util.LinkedHashMap<>();
        if (offers != null) {
            for (var offer : offers) {
                enchantments.merge(offer.enchantment().id, offer.level(), Math::max);
            }
        }
        return new BookRow(minCount, maxCount, weight, enchantments.isEmpty() ? null : enchantments);
    }

    /**
     * The chest + loot (placeChestWithLoot lines 570-584 + LootEntry.addLoot
     * lines 34-60, 1:1): the clip gate (the chest only in its owning
     * chunk), the not-already-a-chest guard, then the roll walk — the
     * weighted pick over the table plus the book row, the count band, the
     * stack-split arm, and the slot draw at 27. A zero count writes
     * nothing (the reference's own size-0 stack is the empty slot).
     */
    static boolean placeChestWithLoot(StructurePiece piece, StructureWorld world, Random random,
                                   StructureBox clip, int x, int y, int z,
                                   List<LootEntry> entries, BookRow bookRow, int amount) {
        int wx = piece.transformX(x, z);
        int wy = piece.transformY(y);
        int wz = piece.transformZ(x, z);
        if (!clip.contains(wx, wy, wz) || world.blockAt(wx, wy, wz) == BuiltinBlocks.CHEST) {
            return false;
        }
        piece.setBlockState(world, BuiltinBlocks.CHEST, x, y, z, clip);
        Map<Integer, StructureWorld.GenLootSlot> slots = new java.util.LinkedHashMap<>();
        for (int i = 0; i < amount; i++) {
            // WeightedPicker.pick lines 18-29 (the descending-weight walk)
            // over the table with the book row appended at the end
            // (LootEntry.addAll's own order).
            int total = bookRow != null ? bookRow.weight() : 0;
            for (LootEntry entry : entries) {
                total += entry.weight();
            }
            int pick = random.nextInt(total);
            LootEntry chosen = null;
            for (LootEntry entry : entries) {
                pick -= entry.weight();
                if (pick < 0) {
                    chosen = entry;
                    break;
                }
            }
            // The book row rides the table's end: the leftover pick lands
            // on it after its own weight subtraction.
            boolean isBook = false;
            if (chosen == null && bookRow != null) {
                pick -= bookRow.weight();
                isBook = pick < 0;
            }
            if (isBook) {
                chosen = new LootEntry("minecraft:enchanted_book",
                        bookRow.minCount(), bookRow.maxCount(), bookRow.weight());
            }
            int count = chosen.minCount()
                    + random.nextInt(chosen.maxCount() - chosen.minCount() + 1);
            if (isBook) {
                // The reference's own shape: the enchanted book stacks to
                // one, so a rolled count above one splits into that many
                // single-book stacks at their own random slots.
                int books = Math.max(1, count);
                for (int k = 0; k < books; k++) {
                    int slot = random.nextInt(27);
                    slots.put(slot, new StructureWorld.GenLootSlot(slot, chosen.itemId(), 1,
                            bookRow.enchantments()));
                }
                continue;
            }
            int maxStack = maxStackOf(chosen.itemId());
            if (maxStack >= count) {
                int slot = random.nextInt(27);
                if (count > 0) {
                    slots.put(slot, new StructureWorld.GenLootSlot(slot, chosen.itemId(), count));
                }
            } else {
                for (int k = 0; k < count; k++) {
                    int slot = random.nextInt(27);
                    slots.put(slot, new StructureWorld.GenLootSlot(slot, chosen.itemId(), 1));
                }
            }
        }
        world.placeGenChest(wx, wy, wz, List.copyOf(slots.values()));
        return true;
    }

    /** The stack ceiling of one item id (the reference's getMaxSize). */
    private static int maxStackOf(String itemId) {
        return BuiltinItems.lookup(Identifier.parse(itemId))
                .map(net.zaminmc.torch.item.ItemType::maxStackSize).orElse(64);
    }
}
