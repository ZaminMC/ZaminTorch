package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The village piece families (Slice 12b — the reference port of
 * gen/structure/VillagePieces.java, 1:1 over the engine's foundation):
 * the piece-weight table, the road/building queue walk, and the thirteen
 * families — the Well (the Start's own head), the roads with their
 * side-building dispatch, the SmallHouse/FarmerHouse/ButcherHouse/
 * LibrarianHouse/PriestHouse/BlacksmithHouse/LargeHouse shells, the two
 * farm plots, and the LampPost.
 *
 * <p><b>The engine's envelope notes</b> (the deterministic-contract
 * differences from the reference, all recorded in the ledger): the
 * wall-mounted torch placements land as the engine's standing torch (no
 * per-facing torch types yet); the door's upper half carries no hinge
 * (the reference derives the hinge client-side from neighbor solidity);
 * the desert variant swaps by block identity (the engine's flat model)
 * rather than by state; the villagers' base profession rolls from the
 * populate random (the reference's entity-private random).</p>
 */
public final class VillagePieces {

    private VillagePieces() {
    }

    // ------------------------------------------------------------- the weights

    /** The building families (the reference's Class dispatch, enum-shaped). */
    enum Kind {
        SMALL_HOUSE,
        PRIEST_HOUSE,
        LIBRARIAN_HOUSE,
        FARMER_HOUSE,
        BUTCHER_HOUSE,
        DOUBLE_FARM,
        FARM,
        BLACKSMITH_HOUSE,
        LARGE_HOUSE
    }

    /** The reference's VillagePieceWeight: one family's weight + budget. */
    static final class VillagePieceWeight {
        final Kind kind;
        final int weight;
        int amountGenerated;
        final int maxAmount;

        VillagePieceWeight(Kind kind, int weight, int maxAmount) {
            this.kind = kind;
            this.weight = weight;
            this.maxAmount = maxAmount;
        }

        boolean isValid(int generationDepth) {
            return this.maxAmount == 0 || this.amountGenerated < this.maxAmount;
        }
    }

    /**
     * The piece-weight table (getPieceWeights lines 43-66): the nine
     * families with the weight + the MathHelper.nextInt count rolls —
     * the reference's exact min/max pairs, the zero-budget rows removed.
     */
    static List<VillagePieceWeight> getPieceWeights(Random random, int size) {
        List<VillagePieceWeight> list = new ArrayList<>();
        list.add(new VillagePieceWeight(Kind.SMALL_HOUSE, 4, nextInt(random, 2 + size, 4 + size * 2)));
        list.add(new VillagePieceWeight(Kind.PRIEST_HOUSE, 20, nextInt(random, 0 + size, 1 + size)));
        list.add(new VillagePieceWeight(Kind.LIBRARIAN_HOUSE, 20, nextInt(random, 0 + size, 2 + size)));
        list.add(new VillagePieceWeight(Kind.FARMER_HOUSE, 3, nextInt(random, 2 + size, 5 + size * 3)));
        list.add(new VillagePieceWeight(Kind.BUTCHER_HOUSE, 15, nextInt(random, 0 + size, 2 + size)));
        list.add(new VillagePieceWeight(Kind.DOUBLE_FARM, 3, nextInt(random, 1 + size, 4 + size)));
        list.add(new VillagePieceWeight(Kind.FARM, 3, nextInt(random, 2 + size, 4 + size * 2)));
        list.add(new VillagePieceWeight(Kind.BLACKSMITH_HOUSE, 15, nextInt(random, 0, 1 + size)));
        list.add(new VillagePieceWeight(Kind.LARGE_HOUSE, 8, nextInt(random, 0 + size, 3 + size * 2)));
        Iterator<VillagePieceWeight> iterator = list.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().maxAmount == 0) {
                iterator.remove();
            }
        }
        return list;
    }

    /** MathHelper.nextInt lines 125-127: {@code min >= max ? min : nextInt(max-min+1)+min}. */
    private static int nextInt(Random random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    /**
     * The biome gate (BiomeSource.isBiomeWithin lines 146-161 over the
     * engine's pure biome function): every 4-block cell of the box — the
     * reference's own quarter-resolution anchors {@code (x-range)>>2 ..
     * (x+range)>>2} — must be a village biome (plains or desert; the
     * engine carries no savanna, the ledger's envelope note). The cells
     * sample the column at the cell's center (the +2), the deterministic
     * equivalent of the reference's one-biome-per-grid-cell model.
     */
    static boolean biomeWithin(StructureWorld world, int x, int z, int range) {
        int minCellX = (x - range) >> 2;
        int minCellZ = (z - range) >> 2;
        int maxCellX = (x + range) >> 2;
        int maxCellZ = (z + range) >> 2;
        for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                net.zaminmc.torch.server.world.Biome biome =
                        world.biomeAt(cellX * 4 + 2, cellZ * 4 + 2);
                if (biome != net.zaminmc.torch.server.world.Biome.PLAINS
                        && biome != net.zaminmc.torch.server.world.Biome.DESERT) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The total weight while any family still has budget, else -1 (updateTotalWeight lines 69-88). */
    private static int updateTotalWeight(List<VillagePieceWeight> weights) {
        boolean anyBudget = false;
        int total = 0;
        for (VillagePieceWeight weight : weights) {
            if (weight.maxAmount > 0 && weight.amountGenerated < weight.maxAmount) {
                anyBudget = true;
            }
            total += weight.weight;
        }
        return anyBudget ? total : -1;
    }

    // -------------------------------------------------- the placement dispatch

    /** createBuildingPiece lines 91-127: the family's own factory. */
    private static VillagePiece createBuildingPiece(VillageStart startPiece, VillagePieceWeight weight,
                                                    List<StructurePiece> pieces, Random random,
                                                    int x, int y, int z, StructureFacing facing,
                                                    int generationDepth) {
        return switch (weight.kind) {
            case SMALL_HOUSE -> SmallHouse.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
            case PRIEST_HOUSE -> PriestHouse.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
            case LIBRARIAN_HOUSE -> LibrarianHouse.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
            case FARMER_HOUSE -> FarmerHouse.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
            case BUTCHER_HOUSE -> ButcherHouse.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
            case DOUBLE_FARM -> DoubleFarm.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
            case FARM -> Farm.create(startPiece, pieces, random, x, y, z, facing, generationDepth);
            case BLACKSMITH_HOUSE -> BlacksmithHouse.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
            case LARGE_HOUSE -> LargeHouse.of(startPiece, pieces, random, x, y, z, facing, generationDepth);
        };
    }

    /** generateNextBuildingPiece lines 129-171: the 5-try weighted walk + the lamp post fallback. */
    private static VillagePiece generateNextBuildingPiece(VillageStart startPiece, List<StructurePiece> pieces,
                                                          Random random, int x, int y, int z,
                                                          StructureFacing facing, int generationDepth) {
        int totalWeight = updateTotalWeight(startPiece.weights);
        if (totalWeight <= 0) {
            return null;
        }
        int tries = 0;
        while (tries < 5) {
            tries++;
            int pick = random.nextInt(totalWeight);
            for (VillagePieceWeight weight : startPiece.weights) {
                pick -= weight.weight;
                if (pick < 0) {
                    if (!weight.isValid(generationDepth)
                            || weight == startPiece.previous && startPiece.weights.size() > 1) {
                        break;
                    }
                    VillagePiece piece = createBuildingPiece(
                            startPiece, weight, pieces, random, x, y, z, facing, generationDepth);
                    if (piece != null) {
                        weight.amountGenerated++;
                        startPiece.previous = weight;
                        if (!weight.isValid(0)) {
                            startPiece.weights.remove(weight);
                        }
                        return piece;
                    }
                }
            }
        }
        StructureBox lampBox = LampPost.findSize(pieces, random, x, y, z, facing);
        return lampBox != null ? new LampPost(startPiece, generationDepth, random, lampBox, facing) : null;
    }

    /** generateBuildingPiece lines 173-198: the depth gate + the leash + the biome gate. */
    static StructurePiece generateBuildingPiece(VillageStart startPiece, List<StructurePiece> pieces,
                                                Random random, int x, int y, int z,
                                                StructureFacing facing, int generationDepth) {
        if (generationDepth > 50) {
            return null;
        }
        if (Math.abs(x - startPiece.getBounds().minX) <= 112 && Math.abs(z - startPiece.getBounds().minZ) <= 112) {
            StructurePiece piece = generateNextBuildingPiece(
                    startPiece, pieces, random, x, y, z, facing, generationDepth + 1);
            if (piece != null) {
                StructureBox box = piece.getBounds();
                int centerX = (box.minX + box.maxX) / 2;
                int centerZ = (box.minZ + box.maxZ) / 2;
                int spanX = box.maxX - box.minX;
                int spanZ = box.maxZ - box.minZ;
                int radius = spanX > spanZ ? spanX : spanZ;
                if (biomeWithin(startPiece.world, centerX, centerZ, radius / 2 + 4)) {
                    pieces.add(piece);
                    startPiece.buildingPieces.add(piece);
                    return piece;
                }
            }
            return null;
        }
        return null;
    }

    /** generateRoadPiece lines 200-230: the depth gate + the y gate + the biome gate. */
    static StructurePiece generateRoadPiece(VillageStart startPiece, List<StructurePiece> pieces,
                                            Random random, int x, int y, int z,
                                            StructureFacing facing, int generationDepth) {
        if (generationDepth > 3 + startPiece.size) {
            return null;
        }
        if (Math.abs(x - startPiece.getBounds().minX) <= 112 && Math.abs(z - startPiece.getBounds().minZ) <= 112) {
            StructureBox box = Road.findSize(pieces, random, x, y, z, facing);
            if (box != null && box.minY > 10) {
                StructurePiece piece = new Road(startPiece, generationDepth, random, box, facing);
                StructureBox pieceBox = piece.getBounds();
                int centerX = (pieceBox.minX + pieceBox.maxX) / 2;
                int centerZ = (pieceBox.minZ + pieceBox.maxZ) / 2;
                int spanX = pieceBox.maxX - pieceBox.minX;
                int spanZ = pieceBox.maxZ - pieceBox.minZ;
                int radius = spanX > spanZ ? spanX : spanZ;
                if (biomeWithin(startPiece.world, centerX, centerZ, radius / 2 + 4)) {
                    pieces.add(piece);
                    startPiece.roadPieces.add(piece);
                    return piece;
                }
            }
            return null;
        }
        return null;
    }

    // ----------------------------------------------------------- the base piece

    /**
     * The village piece base (VillagePiece lines 1486-1743): the surface
     * re-base state, the side-dispatch pair, the updateHeight probe, the
     * villager spawner, and the desert block swap over the engine's
     * block-identity model.
     */
    abstract static class VillagePiece extends StructurePiece {
        protected int hpos = -1;
        protected int villageCount;
        protected boolean desert;

        protected VillagePiece(VillageStart startPiece, int generationDepth) {
            super(generationDepth);
            if (startPiece != null) {
                this.desert = startPiece.isDesertVillage;
            }
        }

        /** generatePieceLeft lines 1563-1615: the left-side placement dispatch. */
        protected StructurePiece generatePieceLeft(VillageStart startPiece, List<StructurePiece> pieces,
                                                   Random random, int yOffset, int offset) {
            if (this.facing == null) {
                return null;
            }
            switch (this.facing) {
                case NORTH:
                case SOUTH:
                    return generateBuildingPiece(startPiece, pieces, random,
                            this.bounds.minX - 1, this.bounds.minY + yOffset, this.bounds.minZ + offset,
                            StructureFacing.WEST, this.getGenerationDepth());
                case WEST:
                    return generateBuildingPiece(startPiece, pieces, random,
                            this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.minZ - 1,
                            StructureFacing.NORTH, this.getGenerationDepth());
                case EAST:
                    return generateBuildingPiece(startPiece, pieces, random,
                            this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.minZ - 1,
                            StructureFacing.NORTH, this.getGenerationDepth());
                default:
                    return null;
            }
        }

        /** generatePieceRight lines 1617-1669: the right-side placement dispatch. */
        protected StructurePiece generatePieceRight(VillageStart startPiece, List<StructurePiece> pieces,
                                                    Random random, int yOffset, int offset) {
            if (this.facing == null) {
                return null;
            }
            switch (this.facing) {
                case NORTH:
                case SOUTH:
                    return generateBuildingPiece(startPiece, pieces, random,
                            this.bounds.maxX + 1, this.bounds.minY + yOffset, this.bounds.minZ + offset,
                            StructureFacing.EAST, this.getGenerationDepth());
                case WEST:
                    return generateBuildingPiece(startPiece, pieces, random,
                            this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.maxZ + 1,
                            StructureFacing.SOUTH, this.getGenerationDepth());
                case EAST:
                    return generateBuildingPiece(startPiece, pieces, random,
                            this.bounds.minX + offset, this.bounds.minY + yOffset, this.bounds.maxZ + 1,
                            StructureFacing.SOUTH, this.getGenerationDepth());
                default:
                    return null;
            }
        }

        /**
         * The surface probe (updateHeight lines 1671-1692): the average of
         * the terrain heights over the piece's box, clamped to the
         * dimension's minSpawnY (seaLevel + 1). The engine probes the
         * generator's pure height over the WHOLE box — the deterministic
         * equivalent of the reference's clipped average (a piece spanning
         * chunks re-bases identically in every chunk that touches it; the
         * reference's own answer is first-chunk load-order).
         */
        protected int updateHeight(StructureWorld world) {
            int total = 0;
            int count = 0;
            for (int z = this.bounds.minZ; z <= this.bounds.maxZ; z++) {
                for (int x = this.bounds.minX; x <= this.bounds.maxX; x++) {
                    total += Math.max(world.surfaceHeight(x, z), world.seaLevel() + 1);
                    count++;
                }
            }
            return count == 0 ? -1 : total / count;
        }

        /** The placement gate (isValidBounds line 1694): a box below y=10 never places. */
        protected static boolean isValidBounds(StructureBox bounds) {
            return bounds != null && bounds.minY > 10;
        }

        /**
         * The villager spawner (spawnVillagers lines 1696-1717): the piece's
         * spawn row spreads along x, gated by the chunk clip — the engine's
         * per-chunk recompute resets the count, so the chunk whose clip
         * holds the first cell spawns the run (the reference continues the
         * sequence across its cached piece; the envelope note).
         */
        protected void spawnVillagers(StructureWorld world, StructureBox clip, int x, int y, int z, int max) {
            if (this.villageCount >= max) {
                return;
            }
            for (int i = this.villageCount; i < max; i++) {
                int wx = this.transformX(x + i, z);
                int wy = this.transformY(y);
                int wz = this.transformZ(x + i, z);
                if (!clip.contains(wx, wy, wz)) {
                    break;
                }
                this.villageCount++;
                world.spawnVillager(wx, wy, wz, this.getVillagerProfession(i));
            }
        }

        /**
         * The building's profession override (getVillagerProfession lines
         * 1719-1721): {@code -1} keeps the entity's own random career (the
         * reference passes the entity's roll through); the houses override
         * with their own fixed id (the butcher's index split included).
         */
        protected int getVillagerProfession(int index) {
            return -1;
        }

        /**
         * The desert swap (getBlockForVariant lines 1723-1738): the
         * reference's sandstone palette over the engine's block identity —
         * the log and the gravel become sandstone, the cobblestone the
         * default sandstone skin, the planks the smooth skin, and the two
         * stair families the sandstone stairs.
         */
        protected BlockType getBlockForVariant(BlockType type, StructureWorld world) {
            if (!this.desert || type == null) {
                return type;
            }
            StructurePalette palette = world.palette();
            String value = type.identifier().value();
            if (value.equals("oak_log") || value.equals("gravel")) {
                return palette.sandstone();
            }
            if (value.equals("cobblestone")) {
                return palette.sandstone();
            }
            if (value.equals("oak_planks")) {
                return palette.smoothSandstone();
            }
            if (value.startsWith("oak_stairs_") || value.startsWith("cobblestone_stairs_")) {
                return palette.sandstoneStairs(0);
            }
            return type;
        }

        @Override
        protected void setBlockState(StructureWorld world, BlockType type, int x, int y, int z,
                                     StructureBox clip) {
            super.setBlockState(world, this.getBlockForVariant(type, world), x, y, z, clip);
        }

        @Override
        protected void fillWithOutline(StructureWorld world, StructureBox clip,
                                       int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                       BlockType edge, BlockType filler, boolean avoidAir) {
            super.fillWithOutline(world, clip, minX, minY, minZ, maxX, maxY, maxZ,
                    this.getBlockForVariant(edge, world), this.getBlockForVariant(filler, world), avoidAir);
        }

        @Override
        protected void fillColumnDown(StructureWorld world, BlockType type, int x, int y, int z,
                                      StructureBox clip) {
            super.fillColumnDown(world, this.getBlockForVariant(type, world), x, y, z, clip);
        }

        protected void setDesert(boolean desert) {
            this.desert = desert;
        }
    }

    // ------------------------------------------------------------- the well

    /**
     * The well (Well lines 1752-1848): the village's anchor — the 4x4
     * cobblestone shaft with the water column, the four fence posts and
     * the cobble roof, the gravel ring at the surface.
     */
    static class Well extends VillagePiece {

        Well(VillageStart startPiece, int generationDepth, Random random, int x, int z) {
            super(startPiece, generationDepth);
            this.facing = StructureFacing.pickHorizontal(random);
            this.bounds = new StructureBox(x, 64, z, x + 6 - 1, 78, z + 6 - 1);
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            VillageStart villageStart = (VillageStart) start;
            generateRoadPiece(villageStart, pieces, random,
                    this.bounds.minX - 1, this.bounds.maxY - 4, this.bounds.minZ + 1,
                    StructureFacing.WEST, this.getGenerationDepth());
            generateRoadPiece(villageStart, pieces, random,
                    this.bounds.maxX + 1, this.bounds.maxY - 4, this.bounds.minZ + 1,
                    StructureFacing.EAST, this.getGenerationDepth());
            generateRoadPiece(villageStart, pieces, random,
                    this.bounds.minX + 1, this.bounds.maxY - 4, this.bounds.minZ - 1,
                    StructureFacing.NORTH, this.getGenerationDepth());
            generateRoadPiece(villageStart, pieces, random,
                    this.bounds.minX + 1, this.bounds.maxY - 4, this.bounds.maxZ + 1,
                    StructureFacing.SOUTH, this.getGenerationDepth());
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world);
                if (this.hpos < 0) {
                    return true;
                }
                this.bounds.move(0, this.hpos - this.bounds.maxY + 3, 0);
            }
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 1, 0, 1, 4, 12, 4,
                    palette.cobblestone(), palette.water(), false);
            this.setBlockState(world, palette.air(), 2, 12, 2, clip);
            this.setBlockState(world, palette.air(), 3, 12, 2, clip);
            this.setBlockState(world, palette.air(), 2, 12, 3, clip);
            this.setBlockState(world, palette.air(), 3, 12, 3, clip);
            this.setBlockState(world, palette.fence(), 1, 13, 1, clip);
            this.setBlockState(world, palette.fence(), 1, 14, 1, clip);
            this.setBlockState(world, palette.fence(), 4, 13, 1, clip);
            this.setBlockState(world, palette.fence(), 4, 14, 1, clip);
            this.setBlockState(world, palette.fence(), 1, 13, 4, clip);
            this.setBlockState(world, palette.fence(), 1, 14, 4, clip);
            this.setBlockState(world, palette.fence(), 4, 13, 4, clip);
            this.setBlockState(world, palette.fence(), 4, 14, 4, clip);
            this.fillWithOutline(world, clip, 1, 15, 1, 4, 15, 4,
                    palette.cobblestone(), palette.cobblestone(), false);
            for (int z = 0; z <= 5; z++) {
                for (int x = 0; x <= 5; x++) {
                    if (z == 0 || z == 5 || x == 0 || x == 5) {
                        this.setBlockState(world, palette.gravel(), x, 11, z, clip);
                        this.fillAirColumnUp(world, x, 12, z, clip);
                    }
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------- the start

    /**
     * The village start (Start lines 1480-1506): the well at the region's
     * +2 corner carrying the weight table, the road/building queues, and
     * the desert flag — the reference's own piece-walk state.
     */
    static final class VillageStart extends Well {
        final StructureWorld world;
        final boolean isDesertVillage;
        final int size;
        VillagePieceWeight previous;
        List<VillagePieceWeight> weights;
        final List<StructurePiece> buildingPieces = new ArrayList<>();
        final List<StructurePiece> roadPieces = new ArrayList<>();

        VillageStart(StructureWorld world, int generationDepth, Random random,
                     int x, int z, List<VillagePieceWeight> weights, int size) {
            super(null, generationDepth, random, x, z);
            this.world = world;
            this.weights = weights;
            this.size = size;
            this.isDesertVillage = world.biomeAt(x, z) == net.zaminmc.torch.server.world.Biome.DESERT;
            this.setDesert(this.isDesertVillage);
        }
    }

    // ------------------------------------------------------------- the road

    /**
     * The road (Road lines 1161-1328): the 3-wide gravel path probing the
     * surface per column — the reference's own walk over the box's cells
     * clipped to the current chunk, the gravel replacing the surface
     * block and the cobblestone under it.
     */
    static class Road extends RoadPiece {
        private final int length;

        Road(VillageStart startPiece, int generationDepth, Random random, StructureBox bounds,
             StructureFacing facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.length = Math.max(bounds.getSpanX(), bounds.getSpanZ());
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            VillageStart villageStart = (VillageStart) start;
            boolean placed = false;
            for (int i = random.nextInt(5); i < this.length - 8; i += 2 + random.nextInt(5)) {
                StructurePiece piece = this.generatePieceLeft(villageStart, pieces, random, 0, i);
                if (piece != null) {
                    i += Math.max(piece.getBounds().getSpanX(), piece.getBounds().getSpanZ());
                    placed = true;
                }
            }
            for (int j = random.nextInt(5); j < this.length - 8; j += 2 + random.nextInt(5)) {
                StructurePiece piece = this.generatePieceRight(villageStart, pieces, random, 0, j);
                if (piece != null) {
                    j += Math.max(piece.getBounds().getSpanX(), piece.getBounds().getSpanZ());
                    placed = true;
                }
            }
            if (placed && random.nextInt(3) > 0 && this.facing != null) {
                switch (this.facing) {
                    case NORTH -> generateRoadPiece(villageStart, pieces, random,
                            this.bounds.minX - 1, this.bounds.minY, this.bounds.minZ,
                            StructureFacing.WEST, this.getGenerationDepth());
                    case SOUTH -> generateRoadPiece(villageStart, pieces, random,
                            this.bounds.minX - 1, this.bounds.minY, this.bounds.maxZ - 2,
                            StructureFacing.WEST, this.getGenerationDepth());
                    case WEST -> generateRoadPiece(villageStart, pieces, random,
                            this.bounds.minX, this.bounds.minY, this.bounds.minZ - 1,
                            StructureFacing.NORTH, this.getGenerationDepth());
                    case EAST -> generateRoadPiece(villageStart, pieces, random,
                            this.bounds.maxX - 2, this.bounds.minY, this.bounds.minZ - 1,
                            StructureFacing.NORTH, this.getGenerationDepth());
                    default -> {
                    }
                }
            }
            if (placed && random.nextInt(3) > 0 && this.facing != null) {
                switch (this.facing) {
                    case NORTH -> generateRoadPiece(villageStart, pieces, random,
                            this.bounds.maxX + 1, this.bounds.minY, this.bounds.minZ,
                            StructureFacing.EAST, this.getGenerationDepth());
                    case SOUTH -> generateRoadPiece(villageStart, pieces, random,
                            this.bounds.maxX + 1, this.bounds.minY, this.bounds.maxZ - 2,
                            StructureFacing.EAST, this.getGenerationDepth());
                    case WEST -> generateRoadPiece(villageStart, pieces, random,
                            this.bounds.minX, this.bounds.minY, this.bounds.maxZ + 1,
                            StructureFacing.SOUTH, this.getGenerationDepth());
                    case EAST -> generateRoadPiece(villageStart, pieces, random,
                            this.bounds.maxX - 2, this.bounds.minY, this.bounds.maxZ + 1,
                            StructureFacing.SOUTH, this.getGenerationDepth());
                    default -> {
                    }
                }
            }
        }

        /** findSize lines 1330-1343: the 7-step length ladder, first free box wins. */
        static StructureBox findSize(List<StructurePiece> pieces, Random random,
                                     int x, int y, int z, StructureFacing facing) {
            for (int i = 7 * nextInt(random, 3, 5); i >= 7; i -= 7) {
                StructureBox box = StructureBox.orient(x, y, z, 0, 0, 0, 3, 3, i, facing);
                if (StructurePiece.getIntersectingPiece(pieces, box) == null) {
                    return box;
                }
            }
            return null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            BlockType surface = this.getBlockForVariant(palette.gravel(), world);
            BlockType under = this.getBlockForVariant(palette.cobblestone(), world);
            for (int x = this.bounds.minX; x <= this.bounds.maxX; x++) {
                for (int z = this.bounds.minZ; z <= this.bounds.maxZ; z++) {
                    if (clip.contains(x, 64, z)) {
                        int surfaceY = world.surfaceHeight(x, z) - 1;
                        world.setBlock(x, surfaceY, z, surface);
                        world.setBlock(x, surfaceY - 1, z, under);
                    }
                }
            }
            return true;
        }
    }

    /** The road marker base (RoadPiece lines 1330-1337). */
    abstract static class RoadPiece extends VillagePiece {

        protected RoadPiece(VillageStart start, int generationDepth) {
            super(start, generationDepth);
        }
    }

    // ------------------------------------------------------- the small house

    /**
     * The small house (SmallHouse lines 1341-1478): the 5x5 hut — the
     * cobblestone floor, the log roof with the plank inset, the terrace
     * roll (the fence rail + the ladder up) and the front stair.
     */
    static final class SmallHouse extends VillagePiece {
        private final boolean terrace;

        SmallHouse(VillageStart startPiece, int generationDepth, Random random,
                   StructureBox bounds, StructureFacing facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.terrace = random.nextBoolean();
        }

        static SmallHouse of(VillageStart startPiece, List<StructurePiece> pieces, Random random,
                             int x, int y, int z, StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, 0, 0, 0, 5, 6, 5, facing);
            return StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new SmallHouse(startPiece, generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world);
                if (this.hpos < 0) {
                    return true;
                }
                this.bounds.move(0, this.hpos - this.bounds.maxY + 6 - 1, 0);
            }
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 0, 0, 4, 0, 4,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 0, 4, 0, 4, 4, 4,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 1, 4, 1, 3, 4, 3,
                    palette.planks(), palette.planks(), false);
            for (int[] corner : new int[][]{{0, 0}, {4, 0}, {0, 4}, {4, 4}}) {
                for (int y = 1; y <= 3; y++) {
                    this.setBlockState(world, palette.cobblestone(), corner[0], y, corner[1], clip);
                }
            }
            this.fillWithOutline(world, clip, 0, 1, 1, 0, 3, 3,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 4, 1, 1, 4, 3, 3,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 1, 1, 4, 3, 3, 4,
                    palette.planks(), palette.planks(), false);
            this.setBlockState(world, palette.glassPane(), 0, 2, 2, clip);
            this.setBlockState(world, palette.glassPane(), 2, 2, 4, clip);
            this.setBlockState(world, palette.glassPane(), 4, 2, 2, clip);
            for (int y = 1; y <= 3; y++) {
                this.setBlockState(world, palette.planks(), 1, y, 0, clip);
                this.setBlockState(world, palette.planks(), 3, y, 0, clip);
            }
            this.setBlockState(world, palette.planks(), 2, 3, 0, clip);
            BlockType frontBelow = this.getBlockState(world, 2, 0, -1, clip);
            BlockType frontBelowTwo = this.getBlockState(world, 2, -1, -1, clip);
            if ((frontBelow == null || world.isAir(frontBelow))
                    && frontBelowTwo != null && !world.isAir(frontBelowTwo)) {
                this.setBlockState(world, palette.stoneStairs(
                        this.postProcessMetadata(MetadataFamily.STAIRS, 3)), 2, 0, -1, clip);
            }
            this.fillWithOutline(world, clip, 1, 1, 1, 3, 3, 3,
                    palette.air(), palette.air(), false);
            if (this.terrace) {
                for (int x = 0; x <= 4; x++) {
                    this.setBlockState(world, palette.fence(), x, 5, 0, clip);
                    this.setBlockState(world, palette.fence(), x, 5, 4, clip);
                }
                for (int z = 1; z <= 3; z++) {
                    this.setBlockState(world, palette.fence(), 0, 5, z, clip);
                    this.setBlockState(world, palette.fence(), 4, 5, z, clip);
                }
            }
            if (this.terrace) {
                int ladderMetadata = this.postProcessMetadata(MetadataFamily.LADDER, 3);
                for (int y = 1; y <= 4; y++) {
                    this.setBlockState(world, palette.ladder(ladderMetadata), 3, y, 3, clip);
                }
            }
            this.setBlockState(world, palette.torch(), 2, 3, 1, clip);
            for (int z = 0; z < 5; z++) {
                for (int x = 0; x < 5; x++) {
                    this.fillAirColumnUp(world, x, 6, z, clip);
                    this.fillColumnDown(world, palette.cobblestone(), x, -1, z, clip);
                }
            }
            this.spawnVillagers(world, clip, 1, 1, 2, 1);
            return true;
        }
    }

    // ------------------------------------------------------- the farmer house

    /**
     * The farmer house (FarmerHouse lines 631-739): the 4x5 cottage — the
     * cobblestone floor with the dirt inset, the log roof, the low-ceiling
     * roll and the table position roll.
     */
    static final class FarmerHouse extends VillagePiece {
        private final boolean lowCeiling;
        private final int tablePosition;

        FarmerHouse(VillageStart startPiece, int generationDepth, Random random,
                    StructureBox bounds, StructureFacing facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.lowCeiling = random.nextBoolean();
            this.tablePosition = random.nextInt(3);
        }

        static FarmerHouse of(VillageStart startPiece, List<StructurePiece> pieces, Random random,
                              int x, int y, int z, StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, 0, 0, 0, 4, 6, 5, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new FarmerHouse(startPiece, generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world);
                if (this.hpos < 0) {
                    return true;
                }
                this.bounds.move(0, this.hpos - this.bounds.maxY + 6 - 1, 0);
            }
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 1, 1, 1, 3, 5, 4,
                    palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 0, 0, 3, 0, 4,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 1, 0, 1, 2, 0, 3,
                    palette.dirt(), palette.dirt(), false);
            if (this.lowCeiling) {
                this.fillWithOutline(world, clip, 1, 4, 1, 2, 4, 3,
                        palette.log(), palette.log(), false);
            } else {
                this.fillWithOutline(world, clip, 1, 5, 1, 2, 5, 3,
                        palette.log(), palette.log(), false);
            }
            this.setBlockState(world, palette.log(), 1, 4, 0, clip);
            this.setBlockState(world, palette.log(), 2, 4, 0, clip);
            this.setBlockState(world, palette.log(), 1, 4, 4, clip);
            this.setBlockState(world, palette.log(), 2, 4, 4, clip);
            this.setBlockState(world, palette.log(), 0, 4, 1, clip);
            this.setBlockState(world, palette.log(), 0, 4, 2, clip);
            this.setBlockState(world, palette.log(), 0, 4, 3, clip);
            this.setBlockState(world, palette.log(), 3, 4, 1, clip);
            this.setBlockState(world, palette.log(), 3, 4, 2, clip);
            this.setBlockState(world, palette.log(), 3, 4, 3, clip);
            this.fillWithOutline(world, clip, 0, 1, 0, 0, 3, 0,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 3, 1, 0, 3, 3, 0,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 0, 1, 4, 0, 3, 4,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 3, 1, 4, 3, 3, 4,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 0, 1, 1, 0, 3, 3,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 3, 1, 1, 3, 3, 3,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 1, 1, 0, 2, 3, 0,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 1, 1, 4, 2, 3, 4,
                    palette.planks(), palette.planks(), false);
            this.setBlockState(world, palette.glassPane(), 0, 2, 2, clip);
            this.setBlockState(world, palette.glassPane(), 3, 2, 2, clip);
            if (this.tablePosition > 0) {
                this.setBlockState(world, palette.fence(), this.tablePosition, 1, 3, clip);
                this.setBlockState(world, palette.woodenPressurePlate(), this.tablePosition, 2, 3, clip);
            }
            this.setBlockState(world, palette.air(), 1, 1, 0, clip);
            this.setBlockState(world, palette.air(), 1, 2, 0, clip);
            this.placeWoodenDoor(world, 1, 1, 0, clip);
            BlockType frontBelow = this.getBlockState(world, 1, 0, -1, clip);
            BlockType frontBelowTwo = this.getBlockState(world, 1, -1, -1, clip);
            if ((frontBelow == null || world.isAir(frontBelow))
                    && frontBelowTwo != null && !world.isAir(frontBelowTwo)) {
                this.setBlockState(world, palette.stoneStairs(
                        this.postProcessMetadata(MetadataFamily.STAIRS, 3)), 1, 0, -1, clip);
            }
            for (int z = 0; z < 5; z++) {
                for (int x = 0; x < 4; x++) {
                    this.fillAirColumnUp(world, x, 6, z, clip);
                    this.fillColumnDown(world, palette.cobblestone(), x, -1, z, clip);
                }
            }
            this.spawnVillagers(world, clip, 1, 1, 2, 1);
            return true;
        }
    }

    // ------------------------------------------------------- the lamp post

    /**
     * The lamp post (LampPost lines 838-878): the three-fence post with
     * the white wool lamp and the four torches.
     */
    static final class LampPost extends VillagePiece {

        LampPost(VillageStart startPiece, int generationDepth, Random random,
                 StructureBox bounds, StructureFacing facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        static StructureBox findSize(List<StructurePiece> pieces, Random random,
                                     int x, int y, int z, StructureFacing facing) {
            StructureBox box = StructureBox.orient(x, y, z, 0, 0, 0, 3, 4, 2, facing);
            return StructurePiece.getIntersectingPiece(pieces, box) != null ? null : box;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world);
                if (this.hpos < 0) {
                    return true;
                }
                this.bounds.move(0, this.hpos - this.bounds.maxY + 4 - 1, 0);
            }
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 0, 0, 2, 3, 1,
                    palette.air(), palette.air(), false);
            this.setBlockState(world, palette.fence(), 1, 0, 0, clip);
            this.setBlockState(world, palette.fence(), 1, 1, 0, clip);
            this.setBlockState(world, palette.fence(), 1, 2, 0, clip);
            this.setBlockState(world, palette.woolWhite(), 1, 3, 0, clip);
            // The reference's four wall torches (the clockwise, the front,
            // the counter-clockwise and the back facings) — the engine's
            // standing-torch stand-in at the same four cells.
            boolean side = this.facing == StructureFacing.EAST || this.facing == StructureFacing.NORTH;
            this.setBlockState(world, palette.torch(), side ? 2 : 0, 3, 0, clip);
            this.setBlockState(world, palette.torch(), 1, 3, 1, clip);
            this.setBlockState(world, palette.torch(), side ? 0 : 2, 3, 0, clip);
            this.setBlockState(world, palette.torch(), 1, 3, -1, clip);
            return true;
        }
    }

    // ------------------------------------------------------- the butcher house

    /**
     * The butcher house (ButcherHouse lines 337-464): the 9x11 two-room
     * house — the front room with the gable roof, the fenced yard behind,
     * the two doors and the internal stairs.
     */
    static final class ButcherHouse extends VillagePiece {

        ButcherHouse(VillageStart startPiece, int generationDepth, Random random,
                     StructureBox bounds, StructureFacing facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        static ButcherHouse of(VillageStart startPiece, List<StructurePiece> pieces, Random random,
                               int x, int y, int z, StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, 0, 0, 0, 9, 7, 11, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new ButcherHouse(startPiece, generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world);
                if (this.hpos < 0) {
                    return true;
                }
                this.bounds.move(0, this.hpos - this.bounds.maxY + 7 - 1, 0);
            }
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 1, 1, 1, 7, 4, 4,
                    palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 2, 1, 6, 8, 4, 10,
                    palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 2, 0, 6, 8, 0, 10,
                    palette.dirt(), palette.dirt(), false);
            this.setBlockState(world, palette.cobblestone(), 6, 0, 6, clip);
            this.fillWithOutline(world, clip, 2, 1, 6, 2, 1, 10,
                    palette.fence(), palette.fence(), false);
            this.fillWithOutline(world, clip, 8, 1, 6, 8, 1, 10,
                    palette.fence(), palette.fence(), false);
            this.fillWithOutline(world, clip, 3, 1, 10, 7, 1, 10,
                    palette.fence(), palette.fence(), false);
            this.fillWithOutline(world, clip, 1, 0, 1, 7, 0, 4,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 0, 0, 0, 0, 3, 5,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 8, 0, 0, 8, 3, 5,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 1, 0, 0, 7, 1, 0,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 1, 0, 5, 7, 1, 5,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 1, 2, 0, 7, 3, 0,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 1, 2, 5, 7, 3, 5,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 0, 4, 1, 8, 4, 1,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 0, 4, 4, 8, 4, 4,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 0, 5, 2, 8, 5, 3,
                    palette.planks(), palette.planks(), false);
            this.setBlockState(world, palette.planks(), 0, 4, 2, clip);
            this.setBlockState(world, palette.planks(), 0, 4, 3, clip);
            this.setBlockState(world, palette.planks(), 8, 4, 2, clip);
            this.setBlockState(world, palette.planks(), 8, 4, 3, clip);
            int roofEast = this.postProcessMetadata(MetadataFamily.STAIRS, 3);
            int roofWest = this.postProcessMetadata(MetadataFamily.STAIRS, 2);
            for (int k = -1; k <= 2; k++) {
                for (int l = 0; l <= 8; l++) {
                    this.setBlockState(world, palette.oakStairs(roofEast), l, 4 + k, k, clip);
                    this.setBlockState(world, palette.oakStairs(roofWest), l, 4 + k, 5 - k, clip);
                }
            }
            this.setBlockState(world, palette.log(), 0, 2, 1, clip);
            this.setBlockState(world, palette.log(), 0, 2, 4, clip);
            this.setBlockState(world, palette.log(), 8, 2, 1, clip);
            this.setBlockState(world, palette.log(), 8, 2, 4, clip);
            this.setBlockState(world, palette.glassPane(), 0, 2, 2, clip);
            this.setBlockState(world, palette.glassPane(), 0, 2, 3, clip);
            this.setBlockState(world, palette.glassPane(), 8, 2, 2, clip);
            this.setBlockState(world, palette.glassPane(), 8, 2, 3, clip);
            this.setBlockState(world, palette.glassPane(), 2, 2, 5, clip);
            this.setBlockState(world, palette.glassPane(), 3, 2, 5, clip);
            this.setBlockState(world, palette.glassPane(), 5, 2, 0, clip);
            this.setBlockState(world, palette.glassPane(), 6, 2, 5, clip);
            this.setBlockState(world, palette.fence(), 2, 1, 3, clip);
            this.setBlockState(world, palette.woodenPressurePlate(), 2, 2, 3, clip);
            this.setBlockState(world, palette.planks(), 1, 1, 4, clip);
            this.setBlockState(world, palette.oakStairs(
                    this.postProcessMetadata(MetadataFamily.STAIRS, 3)), 2, 1, 4, clip);
            this.setBlockState(world, palette.oakStairs(
                    this.postProcessMetadata(MetadataFamily.STAIRS, 1)), 1, 1, 3, clip);
            this.fillWithOutline(world, clip, 5, 0, 1, 7, 0, 3,
                    palette.doubleStoneSlab(), palette.doubleStoneSlab(), false);
            this.setBlockState(world, palette.doubleStoneSlab(), 6, 1, 1, clip);
            this.setBlockState(world, palette.doubleStoneSlab(), 6, 1, 2, clip);
            this.setBlockState(world, palette.air(), 2, 1, 0, clip);
            this.setBlockState(world, palette.air(), 2, 2, 0, clip);
            this.setBlockState(world, palette.torch(), 2, 3, 1, clip);
            this.placeWoodenDoor(world, 2, 1, 0, clip);
            BlockType frontBelow = this.getBlockState(world, 2, 0, -1, clip);
            BlockType frontBelowTwo = this.getBlockState(world, 2, -1, -1, clip);
            if ((frontBelow == null || world.isAir(frontBelow))
                    && frontBelowTwo != null && !world.isAir(frontBelowTwo)) {
                this.setBlockState(world, palette.stoneStairs(
                        this.postProcessMetadata(MetadataFamily.STAIRS, 3)), 2, 0, -1, clip);
            }
            this.setBlockState(world, palette.air(), 6, 1, 5, clip);
            this.setBlockState(world, palette.air(), 6, 2, 5, clip);
            this.setBlockState(world, palette.torch(), 6, 3, 4, clip);
            this.placeWoodenDoor(world, 6, 1, 5, clip);
            for (int z = 0; z < 5; z++) {
                for (int x = 0; x < 9; x++) {
                    this.fillAirColumnUp(world, x, 7, z, clip);
                    this.fillColumnDown(world, palette.cobblestone(), x, -1, z, clip);
                }
            }
            this.spawnVillagers(world, clip, 4, 1, 2, 2);
            return true;
        }

        @Override
        protected int getVillagerProfession(int index) {
            return index == 0 ? 4 : super.getVillagerProfession(index);
        }
    }

    // ------------------------------------------------------- the librarian house

    /**
     * The librarian house (LibrarianHouse lines 905-1033): the 9x6 house
     * with the rounded stone roof, the bookshelf wall and the crafting
     * corner.
     */
    static final class LibrarianHouse extends VillagePiece {

        LibrarianHouse(VillageStart startPiece, int generationDepth, Random random,
                       StructureBox bounds, StructureFacing facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        static LibrarianHouse of(VillageStart startPiece, List<StructurePiece> pieces, Random random,
                                 int x, int y, int z, StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, 0, 0, 0, 9, 9, 6, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new LibrarianHouse(startPiece, generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world);
                if (this.hpos < 0) {
                    return true;
                }
                this.bounds.move(0, this.hpos - this.bounds.maxY + 9 - 1, 0);
            }
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 1, 1, 1, 7, 5, 4,
                    palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 0, 0, 8, 0, 5,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 0, 5, 0, 8, 5, 5,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 0, 6, 1, 8, 6, 4,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 0, 7, 2, 8, 7, 3,
                    palette.cobblestone(), palette.cobblestone(), false);
            int roofEast = this.postProcessMetadata(MetadataFamily.STAIRS, 3);
            int roofWest = this.postProcessMetadata(MetadataFamily.STAIRS, 2);
            for (int k = -1; k <= 2; k++) {
                for (int l = 0; l <= 8; l++) {
                    this.setBlockState(world, palette.oakStairs(roofEast), l, 6 + k, k, clip);
                    this.setBlockState(world, palette.oakStairs(roofWest), l, 6 + k, 5 - k, clip);
                }
            }
            this.fillWithOutline(world, clip, 0, 1, 0, 0, 1, 5,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 1, 1, 5, 8, 1, 5,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 8, 1, 0, 8, 1, 4,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 2, 1, 0, 7, 1, 0,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 0, 2, 0, 0, 4, 0,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 0, 2, 5, 0, 4, 5,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 8, 2, 5, 8, 4, 5,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 8, 2, 0, 8, 4, 0,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 0, 2, 1, 0, 4, 4,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 1, 2, 5, 7, 4, 5,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 8, 2, 1, 8, 4, 4,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 1, 2, 0, 7, 4, 0,
                    palette.planks(), palette.planks(), false);
            this.setBlockState(world, palette.glassPane(), 4, 2, 0, clip);
            this.setBlockState(world, palette.glassPane(), 5, 2, 0, clip);
            this.setBlockState(world, palette.glassPane(), 6, 2, 0, clip);
            this.setBlockState(world, palette.glassPane(), 4, 3, 0, clip);
            this.setBlockState(world, palette.glassPane(), 5, 3, 0, clip);
            this.setBlockState(world, palette.glassPane(), 6, 3, 0, clip);
            this.setBlockState(world, palette.glassPane(), 0, 2, 2, clip);
            this.setBlockState(world, palette.glassPane(), 0, 2, 3, clip);
            this.setBlockState(world, palette.glassPane(), 0, 3, 2, clip);
            this.setBlockState(world, palette.glassPane(), 0, 3, 3, clip);
            this.setBlockState(world, palette.glassPane(), 8, 2, 2, clip);
            this.setBlockState(world, palette.glassPane(), 8, 2, 3, clip);
            this.setBlockState(world, palette.glassPane(), 8, 3, 2, clip);
            this.setBlockState(world, palette.glassPane(), 8, 3, 3, clip);
            this.setBlockState(world, palette.glassPane(), 2, 2, 5, clip);
            this.setBlockState(world, palette.glassPane(), 3, 2, 5, clip);
            this.setBlockState(world, palette.glassPane(), 5, 2, 5, clip);
            this.setBlockState(world, palette.glassPane(), 6, 2, 5, clip);
            this.fillWithOutline(world, clip, 1, 4, 1, 7, 4, 1,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 1, 4, 4, 7, 4, 4,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 1, 3, 4, 7, 3, 4,
                    palette.bookshelf(), palette.bookshelf(), false);
            this.setBlockState(world, palette.planks(), 7, 1, 4, clip);
            this.setBlockState(world, palette.oakStairs(
                    this.postProcessMetadata(MetadataFamily.STAIRS, 0)), 7, 1, 3, clip);
            int benchEast = this.postProcessMetadata(MetadataFamily.STAIRS, 3);
            this.setBlockState(world, palette.oakStairs(benchEast), 6, 1, 4, clip);
            this.setBlockState(world, palette.oakStairs(benchEast), 5, 1, 4, clip);
            this.setBlockState(world, palette.oakStairs(benchEast), 4, 1, 4, clip);
            this.setBlockState(world, palette.oakStairs(benchEast), 3, 1, 4, clip);
            this.setBlockState(world, palette.fence(), 6, 1, 3, clip);
            this.setBlockState(world, palette.woodenPressurePlate(), 6, 2, 3, clip);
            this.setBlockState(world, palette.fence(), 4, 1, 3, clip);
            this.setBlockState(world, palette.woodenPressurePlate(), 4, 2, 3, clip);
            this.setBlockState(world, palette.craftingTable(), 7, 1, 1, clip);
            this.setBlockState(world, palette.air(), 1, 1, 0, clip);
            this.setBlockState(world, palette.air(), 1, 2, 0, clip);
            this.placeWoodenDoor(world, 1, 1, 0, clip);
            BlockType frontBelow = this.getBlockState(world, 1, 0, -1, clip);
            BlockType frontBelowTwo = this.getBlockState(world, 1, -1, -1, clip);
            if ((frontBelow == null || world.isAir(frontBelow))
                    && frontBelowTwo != null && !world.isAir(frontBelowTwo)) {
                this.setBlockState(world, palette.stoneStairs(
                        this.postProcessMetadata(MetadataFamily.STAIRS, 3)), 1, 0, -1, clip);
            }
            for (int z = 0; z < 6; z++) {
                for (int x = 0; x < 9; x++) {
                    this.fillAirColumnUp(world, x, 9, z, clip);
                    this.fillColumnDown(world, palette.cobblestone(), x, -1, z, clip);
                }
            }
            this.spawnVillagers(world, clip, 2, 1, 2, 1);
            return true;
        }

        @Override
        protected int getVillagerProfession(int index) {
            return 1;
        }
    }

    // ------------------------------------------------------- the priest house

    /**
     * The priest house (PriestHouse lines 1035-1167): the 5x9 church —
     * the tower with the crenellated roof, the glass windows, the
     * internal ladder to the upper floor and the reading bench.
     */
    static final class PriestHouse extends VillagePiece {

        PriestHouse(VillageStart startPiece, int generationDepth, Random random,
                    StructureBox bounds, StructureFacing facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        static PriestHouse of(VillageStart startPiece, List<StructurePiece> pieces, Random random,
                              int x, int y, int z, StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, 0, 0, 0, 5, 12, 9, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new PriestHouse(startPiece, generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world);
                if (this.hpos < 0) {
                    return true;
                }
                this.bounds.move(0, this.hpos - this.bounds.maxY + 12 - 1, 0);
            }
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 1, 1, 1, 3, 3, 7,
                    palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 1, 5, 1, 3, 9, 3,
                    palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 1, 0, 0, 3, 0, 8,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 1, 1, 0, 3, 10, 0,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 0, 1, 1, 0, 10, 3,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 4, 1, 1, 4, 10, 3,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 0, 0, 4, 0, 4, 7,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 4, 0, 4, 4, 4, 7,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 1, 1, 8, 3, 4, 8,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 1, 5, 4, 3, 10, 4,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 1, 5, 5, 3, 5, 7,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 0, 9, 0, 4, 9, 4,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 0, 4, 0, 4, 4, 4,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.setBlockState(world, palette.cobblestone(), 0, 11, 2, clip);
            this.setBlockState(world, palette.cobblestone(), 4, 11, 2, clip);
            this.setBlockState(world, palette.cobblestone(), 2, 11, 0, clip);
            this.setBlockState(world, palette.cobblestone(), 2, 11, 4, clip);
            this.setBlockState(world, palette.cobblestone(), 1, 1, 6, clip);
            this.setBlockState(world, palette.cobblestone(), 1, 1, 7, clip);
            this.setBlockState(world, palette.cobblestone(), 2, 1, 7, clip);
            this.setBlockState(world, palette.cobblestone(), 3, 1, 6, clip);
            this.setBlockState(world, palette.cobblestone(), 3, 1, 7, clip);
            this.setBlockState(world, palette.stoneStairs(
                    this.postProcessMetadata(MetadataFamily.STAIRS, 3)), 1, 1, 5, clip);
            this.setBlockState(world, palette.stoneStairs(
                    this.postProcessMetadata(MetadataFamily.STAIRS, 3)), 2, 1, 6, clip);
            this.setBlockState(world, palette.stoneStairs(
                    this.postProcessMetadata(MetadataFamily.STAIRS, 3)), 3, 1, 5, clip);
            this.setBlockState(world, palette.stoneStairs(
                    this.postProcessMetadata(MetadataFamily.STAIRS, 1)), 1, 2, 7, clip);
            this.setBlockState(world, palette.stoneStairs(
                    this.postProcessMetadata(MetadataFamily.STAIRS, 0)), 3, 2, 7, clip);
            this.setBlockState(world, palette.glassPane(), 0, 2, 2, clip);
            this.setBlockState(world, palette.glassPane(), 0, 3, 2, clip);
            this.setBlockState(world, palette.glassPane(), 4, 2, 2, clip);
            this.setBlockState(world, palette.glassPane(), 4, 3, 2, clip);
            this.setBlockState(world, palette.glassPane(), 0, 6, 2, clip);
            this.setBlockState(world, palette.glassPane(), 0, 7, 2, clip);
            this.setBlockState(world, palette.glassPane(), 4, 6, 2, clip);
            this.setBlockState(world, palette.glassPane(), 4, 7, 2, clip);
            this.setBlockState(world, palette.glassPane(), 2, 6, 0, clip);
            this.setBlockState(world, palette.glassPane(), 2, 7, 0, clip);
            this.setBlockState(world, palette.glassPane(), 2, 6, 4, clip);
            this.setBlockState(world, palette.glassPane(), 2, 7, 4, clip);
            this.setBlockState(world, palette.glassPane(), 0, 3, 6, clip);
            this.setBlockState(world, palette.glassPane(), 4, 3, 6, clip);
            this.setBlockState(world, palette.glassPane(), 2, 3, 8, clip);
            this.setBlockState(world, palette.torch(), 2, 4, 7, clip);
            this.setBlockState(world, palette.torch(), 1, 4, 6, clip);
            this.setBlockState(world, palette.torch(), 3, 4, 6, clip);
            this.setBlockState(world, palette.torch(), 2, 4, 5, clip);
            int ladderMetadata = this.postProcessMetadata(MetadataFamily.LADDER, 4);
            for (int y = 1; y <= 9; y++) {
                this.setBlockState(world, palette.ladder(ladderMetadata), 3, y, 3, clip);
            }
            this.setBlockState(world, palette.air(), 2, 1, 0, clip);
            this.setBlockState(world, palette.air(), 2, 2, 0, clip);
            this.placeWoodenDoor(world, 2, 1, 0, clip);
            BlockType frontBelow = this.getBlockState(world, 2, 0, -1, clip);
            BlockType frontBelowTwo = this.getBlockState(world, 2, -1, -1, clip);
            if ((frontBelow == null || world.isAir(frontBelow))
                    && frontBelowTwo != null && !world.isAir(frontBelowTwo)) {
                this.setBlockState(world, palette.stoneStairs(
                        this.postProcessMetadata(MetadataFamily.STAIRS, 3)), 2, 0, -1, clip);
            }
            for (int z = 0; z < 9; z++) {
                for (int x = 0; x < 5; x++) {
                    this.fillAirColumnUp(world, x, 12, z, clip);
                    this.fillColumnDown(world, palette.cobblestone(), x, -1, z, clip);
                }
            }
            this.spawnVillagers(world, clip, 2, 1, 2, 1);
            return true;
        }

        @Override
        protected int getVillagerProfession(int index) {
            return 2;
        }
    }

    // ------------------------------------------------------- the farms

    /** The crop pick (pickCrop lines 487-496): carrots 1-in-5, potatoes 1-in-5, else wheat. */
    private static int pickCrop(Random random) {
        switch (random.nextInt(5)) {
            case 0:
                return 0; // carrots
            case 1:
                return 1; // potatoes
            default:
                return 2; // wheat
        }
    }

    /** The farm plots' crop kind: 0 carrots, 1 potatoes, 2 wheat. */
    private static BlockType cropOf(StructurePalette palette, int kind, int stage) {
        return switch (kind) {
            case 0 -> palette.carrots(stage);
            case 1 -> palette.potatoes(stage);
            default -> palette.wheat(stage);
        };
    }

    /**
     * The double farm (DoubleFarm lines 466-558): the 13x9 plot pair —
     * four farmland rows over two water channels, the log frame, the
     * four crop rolls at random stages.
     */
    static final class DoubleFarm extends VillagePiece {
        private final int crop1;
        private final int crop2;
        private final int crop3;
        private final int crop4;

        DoubleFarm(VillageStart startPiece, int generationDepth, Random random,
                   StructureBox bounds, StructureFacing facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.crop1 = pickCrop(random);
            this.crop2 = pickCrop(random);
            this.crop3 = pickCrop(random);
            this.crop4 = pickCrop(random);
        }

        static DoubleFarm of(VillageStart startPiece, List<StructurePiece> pieces, Random random,
                             int x, int y, int z, StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, 0, 0, 0, 13, 4, 9, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new DoubleFarm(startPiece, generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world);
                if (this.hpos < 0) {
                    return true;
                }
                this.bounds.move(0, this.hpos - this.bounds.maxY + 4 - 1, 0);
            }
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 1, 0, 12, 4, 8,
                    palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 1, 0, 1, 2, 0, 7,
                    palette.farmland(), palette.farmland(), false);
            this.fillWithOutline(world, clip, 4, 0, 1, 5, 0, 7,
                    palette.farmland(), palette.farmland(), false);
            this.fillWithOutline(world, clip, 7, 0, 1, 8, 0, 7,
                    palette.farmland(), palette.farmland(), false);
            this.fillWithOutline(world, clip, 10, 0, 1, 11, 0, 7,
                    palette.farmland(), palette.farmland(), false);
            this.fillWithOutline(world, clip, 0, 0, 0, 0, 0, 8,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 6, 0, 0, 6, 0, 8,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 12, 0, 0, 12, 0, 8,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 1, 0, 0, 11, 0, 0,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 1, 0, 8, 11, 0, 8,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 3, 0, 1, 3, 0, 7,
                    palette.water(), palette.water(), false);
            this.fillWithOutline(world, clip, 9, 0, 1, 9, 0, 7,
                    palette.water(), palette.water(), false);
            for (int i = 1; i <= 7; i++) {
                this.setBlockState(world, cropOf(palette, this.crop1, nextInt(random, 2, 7)), 1, 1, i, clip);
                this.setBlockState(world, cropOf(palette, this.crop1, nextInt(random, 2, 7)), 2, 1, i, clip);
                this.setBlockState(world, cropOf(palette, this.crop2, nextInt(random, 2, 7)), 4, 1, i, clip);
                this.setBlockState(world, cropOf(palette, this.crop2, nextInt(random, 2, 7)), 5, 1, i, clip);
                this.setBlockState(world, cropOf(palette, this.crop3, nextInt(random, 2, 7)), 7, 1, i, clip);
                this.setBlockState(world, cropOf(palette, this.crop3, nextInt(random, 2, 7)), 8, 1, i, clip);
                this.setBlockState(world, cropOf(palette, this.crop4, nextInt(random, 2, 7)), 10, 1, i, clip);
                this.setBlockState(world, cropOf(palette, this.crop4, nextInt(random, 2, 7)), 11, 1, i, clip);
            }
            for (int z = 0; z < 9; z++) {
                for (int x = 0; x < 13; x++) {
                    this.fillAirColumnUp(world, x, 4, z, clip);
                    this.fillColumnDown(world, palette.dirt(), x, -1, z, clip);
                }
            }
            return true;
        }
    }

    /**
     * The single farm (Farm lines 560-629): the 7x9 plot — two farmland
     * rows over one water channel, the log frame, the two crop rolls.
     */
    static final class Farm extends VillagePiece {
        private final int crop1;
        private final int crop2;

        Farm(VillageStart startPiece, int generationDepth, Random random,
             StructureBox bounds, StructureFacing facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
            this.crop1 = pickCrop(random);
            this.crop2 = pickCrop(random);
        }

        static Farm create(VillageStart startPiece, List<StructurePiece> pieces, Random random,
                            int x, int y, int z, StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, 0, 0, 0, 7, 4, 9, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new Farm(startPiece, generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world);
                if (this.hpos < 0) {
                    return true;
                }
                this.bounds.move(0, this.hpos - this.bounds.maxY + 4 - 1, 0);
            }
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 1, 0, 6, 4, 8,
                    palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 1, 0, 1, 2, 0, 7,
                    palette.farmland(), palette.farmland(), false);
            this.fillWithOutline(world, clip, 4, 0, 1, 5, 0, 7,
                    palette.farmland(), palette.farmland(), false);
            this.fillWithOutline(world, clip, 0, 0, 0, 0, 0, 8,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 6, 0, 0, 6, 0, 8,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 1, 0, 0, 5, 0, 0,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 1, 0, 8, 5, 0, 8,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 3, 0, 1, 3, 0, 7,
                    palette.water(), palette.water(), false);
            for (int i = 1; i <= 7; i++) {
                this.setBlockState(world, cropOf(palette, this.crop1, nextInt(random, 2, 7)), 1, 1, i, clip);
                this.setBlockState(world, cropOf(palette, this.crop1, nextInt(random, 2, 7)), 2, 1, i, clip);
                this.setBlockState(world, cropOf(palette, this.crop2, nextInt(random, 2, 7)), 4, 1, i, clip);
                this.setBlockState(world, cropOf(palette, this.crop2, nextInt(random, 2, 7)), 5, 1, i, clip);
            }
            for (int z = 0; z < 9; z++) {
                for (int x = 0; x < 7; x++) {
                    this.fillAirUp(world, clip, x, z);
                }
            }
            return true;
        }

        private void fillAirUp(StructureWorld world, StructureBox clip, int x, int z) {
            StructurePalette palette = world.palette();
            this.fillAirColumnUp(world, x, 4, z, clip);
            this.fillColumnDown(world, palette.dirt(), x, -1, z, clip);
        }
    }

    // ------------------------------------------------------- the blacksmith

    /**
     * One loot row (the reference's LootEntry): the item, the count roll
     * band and the pick weight — the reference's own shape, with the max
     * stack size folded from the item registry at roll time.
     */
    private record VillageLootEntry(String itemId, int minChance, int maxChance, int weight) {
    }

    /**
     * The blacksmith house (BlacksmithHouse lines 216-335): the 10x7
     * forge — the front room with the lava pools behind iron bars, the
     * double-furnace wall, the two chests with the loot table and the
     * smooth-stone table.
     */
    static final class BlacksmithHouse extends VillagePiece {

        /** The chest's loot table (LOOT_ENTRIES lines 218-234, 1:1). */
        private static final List<VillageLootEntry> LOOT_ENTRIES = List.of(
                new VillageLootEntry("minecraft:diamond", 1, 3, 3),
                new VillageLootEntry("minecraft:iron_ingot", 1, 5, 10),
                new VillageLootEntry("minecraft:gold_ingot", 1, 3, 5),
                new VillageLootEntry("minecraft:bread", 1, 3, 15),
                new VillageLootEntry("minecraft:apple", 1, 3, 15),
                new VillageLootEntry("minecraft:iron_pickaxe", 1, 1, 5),
                new VillageLootEntry("minecraft:iron_sword", 1, 1, 5),
                new VillageLootEntry("minecraft:iron_chestplate", 1, 1, 5),
                new VillageLootEntry("minecraft:iron_helmet", 1, 1, 5),
                new VillageLootEntry("minecraft:iron_leggings", 1, 1, 5),
                new VillageLootEntry("minecraft:iron_boots", 1, 1, 5),
                new VillageLootEntry("minecraft:obsidian", 3, 7, 5),
                new VillageLootEntry("minecraft:oak_sapling", 3, 7, 5),
                new VillageLootEntry("minecraft:saddle", 1, 1, 3),
                new VillageLootEntry("minecraft:iron_horse_armor", 1, 1, 1),
                new VillageLootEntry("minecraft:golden_horse_armor", 1, 1, 1),
                new VillageLootEntry("minecraft:diamond_horse_armor", 1, 1, 1));

        /** The total weight (WeightedPicker's own pick denominator). */
        private static final int LOOT_TOTAL_WEIGHT = LOOT_ENTRIES.stream()
                .mapToInt(VillageLootEntry::weight).sum();

        private boolean hasChest;

        BlacksmithHouse(VillageStart startPiece, int generationDepth, Random random,
                        StructureBox bounds, StructureFacing facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        static BlacksmithHouse of(VillageStart startPiece, List<StructurePiece> pieces, Random random,
                                  int x, int y, int z, StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, 0, 0, 0, 10, 6, 7, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new BlacksmithHouse(startPiece, generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world);
                if (this.hpos < 0) {
                    return true;
                }
                this.bounds.move(0, this.hpos - this.bounds.maxY + 6 - 1, 0);
            }
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 0, 1, 0, 9, 4, 6,
                    palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 0, 0, 9, 0, 6,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 0, 4, 0, 9, 4, 6,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 0, 5, 0, 9, 5, 6,
                    palette.stoneSlab(), palette.stoneSlab(), false);
            this.fillWithOutline(world, clip, 1, 5, 1, 8, 5, 5,
                    palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 1, 1, 0, 2, 3, 0,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 0, 1, 0, 0, 4, 0,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 3, 1, 0, 3, 4, 0,
                    palette.log(), palette.log(), false);
            this.fillWithOutline(world, clip, 0, 1, 6, 0, 4, 6,
                    palette.log(), palette.log(), false);
            this.setBlockState(world, palette.planks(), 3, 3, 1, clip);
            this.fillWithOutline(world, clip, 3, 1, 2, 3, 3, 2,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 4, 1, 3, 5, 3, 3,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 0, 1, 1, 0, 3, 5,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 1, 1, 6, 5, 3, 6,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 5, 1, 0, 5, 3, 0,
                    palette.fence(), palette.fence(), false);
            this.fillWithOutline(world, clip, 9, 1, 0, 9, 3, 0,
                    palette.fence(), palette.fence(), false);
            this.fillWithOutline(world, clip, 6, 1, 4, 9, 4, 6,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.setBlockState(world, palette.lava(), 7, 1, 5, clip);
            this.setBlockState(world, palette.lava(), 8, 1, 5, clip);
            this.setBlockState(world, palette.ironBars(), 9, 2, 5, clip);
            this.setBlockState(world, palette.ironBars(), 9, 2, 4, clip);
            this.fillWithOutline(world, clip, 7, 2, 4, 8, 2, 5,
                    palette.air(), palette.air(), false);
            this.setBlockState(world, palette.cobblestone(), 6, 1, 3, clip);
            this.setBlockState(world, palette.furnace(), 6, 2, 3, clip);
            this.setBlockState(world, palette.furnace(), 6, 3, 3, clip);
            this.setBlockState(world, palette.doubleStoneSlab(), 8, 1, 1, clip);
            this.setBlockState(world, palette.glassPane(), 0, 2, 2, clip);
            this.setBlockState(world, palette.glassPane(), 0, 2, 4, clip);
            this.setBlockState(world, palette.glassPane(), 2, 2, 6, clip);
            this.setBlockState(world, palette.glassPane(), 4, 2, 6, clip);
            this.setBlockState(world, palette.fence(), 2, 1, 4, clip);
            this.setBlockState(world, palette.woodenPressurePlate(), 2, 2, 4, clip);
            this.setBlockState(world, palette.planks(), 1, 1, 5, clip);
            this.setBlockState(world, palette.oakStairs(
                    this.postProcessMetadata(MetadataFamily.STAIRS, 3)), 2, 1, 5, clip);
            this.setBlockState(world, palette.oakStairs(
                    this.postProcessMetadata(MetadataFamily.STAIRS, 1)), 1, 1, 4, clip);
            if (!this.hasChest && clip.contains(this.transformX(5, 5), this.transformY(1), this.transformZ(5, 5))) {
                this.hasChest = true;
                this.placeChestWithLoot(world, random, clip, 5, 1, 5, 3 + random.nextInt(6));
            }
            for (int i = 6; i <= 8; i++) {
                BlockType frontBelow = this.getBlockState(world, i, 0, -1, clip);
                BlockType frontBelowTwo = this.getBlockState(world, i, -1, -1, clip);
                if ((frontBelow == null || world.isAir(frontBelow))
                        && frontBelowTwo != null && !world.isAir(frontBelowTwo)) {
                    this.setBlockState(world, palette.stoneStairs(
                            this.postProcessMetadata(MetadataFamily.STAIRS, 3)), i, 0, -1, clip);
                }
            }
            for (int z = 0; z < 7; z++) {
                for (int x = 0; x < 10; x++) {
                    this.fillAirColumnUp(world, x, 6, z, clip);
                    this.fillColumnDown(world, palette.cobblestone(), x, -1, z, clip);
                }
            }
            this.spawnVillagers(world, clip, 7, 1, 1, 1);
            return true;
        }

        /**
         * The chest + loot (placeChestWithLoot + LootEntry.addLoot lines
         * 570-588 + 34-60): the chest block lands through the ordinary
         * write path, the rolled contents ride the chunk's pending record.
         */
        private void placeChestWithLoot(StructureWorld world, Random random, StructureBox clip,
                                        int x, int y, int z, int amount) {
            StructurePalette palette = world.palette();
            this.setBlockState(world, net.zaminmc.torch.server.block.BuiltinBlocks.CHEST, x, y, z, clip);
            java.util.Map<Integer, StructureWorld.GenLootSlot> slots = new java.util.LinkedHashMap<>();
            for (int i = 0; i < amount; i++) {
                VillageLootEntry entry = pickLoot(random);
                int count = entry.minChance() + random.nextInt(entry.maxChance() - entry.minChance() + 1);
                net.zaminmc.torch.util.Identifier id =
                        net.zaminmc.torch.util.Identifier.parse(entry.itemId());
                int maxStack = net.zaminmc.torch.server.item.BuiltinItems.lookup(id)
                        .map(net.zaminmc.torch.item.ItemType::maxStackSize).orElse(64);
                if (maxStack >= count) {
                    int slot = random.nextInt(27);
                    slots.put(slot, new StructureWorld.GenLootSlot(slot, entry.itemId(), count));
                } else {
                    for (int k = 0; k < count; k++) {
                        int slot = random.nextInt(27);
                        slots.put(slot, new StructureWorld.GenLootSlot(slot, entry.itemId(), 1));
                    }
                }
            }
            world.placeGenChest(this.transformX(x, z), this.transformY(y), this.transformZ(x, z),
                    List.copyOf(slots.values()));
        }

        /** WeightedPicker.pick lines 18-29: the descending-weight walk. */
        private static VillageLootEntry pickLoot(Random random) {
            int pick = random.nextInt(LOOT_TOTAL_WEIGHT);
            for (VillageLootEntry entry : LOOT_ENTRIES) {
                pick -= entry.weight();
                if (pick < 0) {
                    return entry;
                }
            }
            return LOOT_ENTRIES.get(LOOT_ENTRIES.size() - 1);
        }

        @Override
        protected int getVillagerProfession(int index) {
            return 3;
        }
    }

    // ------------------------------------------------------- the large house

    /**
     * The large house (LargeHouse lines 880-1033): the 9x12 L-house — the
     * front room with the gable roof, the hall behind with the internal
     * staircase to the gallery.
     */
    static final class LargeHouse extends VillagePiece {

        LargeHouse(VillageStart startPiece, int generationDepth, Random random,
                   StructureBox bounds, StructureFacing facing) {
            super(startPiece, generationDepth);
            this.facing = facing;
            this.bounds = bounds;
        }

        static LargeHouse of(VillageStart startPiece, List<StructurePiece> pieces, Random random,
                            int x, int y, int z, StructureFacing facing, int generationDepth) {
            StructureBox box = StructureBox.orient(x, y, z, 0, 0, 0, 9, 7, 12, facing);
            return isValidBounds(box) && StructurePiece.getIntersectingPiece(pieces, box) == null
                    ? new LargeHouse(startPiece, generationDepth, random, box, facing)
                    : null;
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            if (this.hpos < 0) {
                this.hpos = this.updateHeight(world);
                if (this.hpos < 0) {
                    return true;
                }
                this.bounds.move(0, this.hpos - this.bounds.maxY + 7 - 1, 0);
            }
            StructurePalette palette = world.palette();
            this.fillWithOutline(world, clip, 1, 1, 1, 7, 4, 4,
                    palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 2, 1, 6, 8, 4, 10,
                    palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 2, 0, 5, 8, 0, 10,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 1, 0, 1, 7, 0, 4,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 0, 0, 0, 0, 3, 5,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 8, 0, 0, 8, 3, 10,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 1, 0, 0, 7, 2, 0,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 1, 0, 5, 2, 1, 5,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 2, 0, 6, 2, 3, 10,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 3, 0, 10, 7, 3, 10,
                    palette.cobblestone(), palette.cobblestone(), false);
            this.fillWithOutline(world, clip, 1, 2, 0, 7, 3, 0,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 1, 2, 5, 2, 3, 5,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 0, 4, 1, 8, 4, 1,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 0, 4, 4, 3, 4, 4,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 0, 5, 2, 8, 5, 3,
                    palette.planks(), palette.planks(), false);
            this.setBlockState(world, palette.planks(), 0, 4, 2, clip);
            this.setBlockState(world, palette.planks(), 0, 4, 3, clip);
            this.setBlockState(world, palette.planks(), 8, 4, 2, clip);
            this.setBlockState(world, palette.planks(), 8, 4, 3, clip);
            this.setBlockState(world, palette.planks(), 8, 4, 4, clip);
            int roofEast = this.postProcessMetadata(MetadataFamily.STAIRS, 3);
            int roofWest = this.postProcessMetadata(MetadataFamily.STAIRS, 2);
            for (int k = -1; k <= 2; k++) {
                for (int l = 0; l <= 8; l++) {
                    this.setBlockState(world, palette.oakStairs(roofEast), l, 4 + k, k, clip);
                    if ((k > -1 || l <= 1) && (k > 0 || l <= 3) && (k > 1 || l <= 4 || l >= 6)) {
                        this.setBlockState(world, palette.oakStairs(roofWest), l, 4 + k, 5 - k, clip);
                    }
                }
            }
            this.fillWithOutline(world, clip, 3, 4, 5, 3, 4, 10,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 7, 4, 2, 7, 4, 10,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 4, 5, 4, 4, 5, 10,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 6, 5, 4, 6, 5, 10,
                    palette.planks(), palette.planks(), false);
            this.fillWithOutline(world, clip, 5, 6, 3, 5, 6, 10,
                    palette.planks(), palette.planks(), false);
            int stairNorth = this.postProcessMetadata(MetadataFamily.STAIRS, 0);
            for (int l1 = 4; l1 >= 1; l1--) {
                this.setBlockState(world, palette.planks(), l1, 2 + l1, 7 - l1, clip);
                for (int i1 = 8 - l1; i1 <= 10; i1++) {
                    this.setBlockState(world, palette.oakStairs(stairNorth), l1, 2 + l1, i1, clip);
                }
            }
            int stairEast = this.postProcessMetadata(MetadataFamily.STAIRS, 1);
            this.setBlockState(world, palette.planks(), 6, 6, 3, clip);
            this.setBlockState(world, palette.planks(), 7, 5, 4, clip);
            this.setBlockState(world, palette.oakStairs(stairEast), 6, 6, 4, clip);
            for (int j2 = 6; j2 <= 8; j2++) {
                for (int j1 = 5; j1 <= 10; j1++) {
                    this.setBlockState(world, palette.oakStairs(stairEast), j2, 12 - j2, j1, clip);
                }
            }
            this.setBlockState(world, palette.log(), 0, 2, 1, clip);
            this.setBlockState(world, palette.log(), 0, 2, 4, clip);
            this.setBlockState(world, palette.glassPane(), 0, 2, 2, clip);
            this.setBlockState(world, palette.glassPane(), 0, 2, 3, clip);
            this.setBlockState(world, palette.log(), 4, 2, 0, clip);
            this.setBlockState(world, palette.glassPane(), 5, 2, 0, clip);
            this.setBlockState(world, palette.log(), 6, 2, 0, clip);
            this.setBlockState(world, palette.log(), 8, 2, 1, clip);
            this.setBlockState(world, palette.glassPane(), 8, 2, 2, clip);
            this.setBlockState(world, palette.glassPane(), 8, 2, 3, clip);
            this.setBlockState(world, palette.log(), 8, 2, 4, clip);
            this.setBlockState(world, palette.planks(), 8, 2, 5, clip);
            this.setBlockState(world, palette.log(), 8, 2, 6, clip);
            this.setBlockState(world, palette.glassPane(), 8, 2, 7, clip);
            this.setBlockState(world, palette.glassPane(), 8, 2, 8, clip);
            this.setBlockState(world, palette.log(), 8, 2, 9, clip);
            this.setBlockState(world, palette.log(), 2, 2, 6, clip);
            this.setBlockState(world, palette.glassPane(), 2, 2, 7, clip);
            this.setBlockState(world, palette.glassPane(), 2, 2, 8, clip);
            this.setBlockState(world, palette.log(), 2, 2, 9, clip);
            this.setBlockState(world, palette.log(), 4, 4, 10, clip);
            this.setBlockState(world, palette.glassPane(), 5, 4, 10, clip);
            this.setBlockState(world, palette.log(), 6, 4, 10, clip);
            this.setBlockState(world, palette.planks(), 5, 5, 10, clip);
            this.setBlockState(world, palette.air(), 2, 1, 0, clip);
            this.setBlockState(world, palette.air(), 2, 2, 0, clip);
            this.setBlockState(world, palette.torch(), 2, 3, 1, clip);
            this.placeWoodenDoor(world, 2, 1, 0, clip);
            this.fillWithOutline(world, clip, 1, 0, -1, 3, 2, -1,
                    palette.air(), palette.air(), false);
            BlockType frontBelow = this.getBlockState(world, 2, 0, -1, clip);
            BlockType frontBelowTwo = this.getBlockState(world, 2, -1, -1, clip);
            if ((frontBelow == null || world.isAir(frontBelow))
                    && frontBelowTwo != null && !world.isAir(frontBelowTwo)) {
                this.setBlockState(world, palette.stoneStairs(
                        this.postProcessMetadata(MetadataFamily.STAIRS, 3)), 2, 0, -1, clip);
            }
            for (int z = 0; z < 5; z++) {
                for (int x = 0; x < 9; x++) {
                    this.fillAirColumnUp(world, x, 7, z, clip);
                    this.fillColumnDown(world, palette.cobblestone(), x, -1, z, clip);
                }
            }
            for (int z = 5; z < 11; z++) {
                for (int x = 2; x < 9; x++) {
                    this.fillAirColumnUp(world, x, 7, z, clip);
                    this.fillColumnDown(world, palette.cobblestone(), x, -1, z, clip);
                }
            }
            this.spawnVillagers(world, clip, 4, 1, 2, 2);
            return true;
        }
    }
}
