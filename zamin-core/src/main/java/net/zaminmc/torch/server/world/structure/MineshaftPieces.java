package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockType;

import java.util.List;
import java.util.Random;

/**
 * The mineshaft piece family (Slice 12a — the reference port of
 * gen/structure/MineshaftPieces.java, 1:1): the Room start, the
 * Corridor (rails, cobwebs, fences, the plank floor patch), the
 * Crossing and the Stairs.
 *
 * <p><b>Ledgered</b> (the reference's arms the engine lacks the systems
 * for): the chest minecart with its loot rolls LANDED with Slice 21a (the
 * corridor's two nextInt(100) arms — the rail cell + the cart record
 * materializing into the vehicle manager at chunk publish); the
 * cave-spider mob spawner's own logic rides the mob-spawner slice (the
 * corridor keeps the cobweb arm the reference pairs with it).</p>
 */
public final class MineshaftPieces {

    private MineshaftPieces() {
    }

    /**
     * The chest-minecart loot table (LOOT_ENTRIES lines 22-36, 1:1): the
     * thirteen ordinary rows with the reference's own min/max count bands
     * and weights (the enchanted book rides addAll's own tail below).
     */
    private static final List<LootRow> LOOT_ENTRIES = List.of(
            new LootRow("minecraft:iron_ingot", 1, 5, 10),
            new LootRow("minecraft:gold_ingot", 1, 3, 5),
            new LootRow("minecraft:redstone", 4, 9, 5),
            // The lapis band (the reference's DYE + BLUE metadata 4 — the
            // engine's lapis item is the dye 351 whose damage 4 is blue).
            new LootRow("minecraft:lapis", 4, 9, 5),
            new LootRow("minecraft:diamond", 1, 2, 3),
            new LootRow("minecraft:coal", 3, 8, 10),
            new LootRow("minecraft:bread", 1, 3, 15),
            new LootRow("minecraft:iron_pickaxe", 1, 1, 1),
            new LootRow("minecraft:rail", 4, 8, 1),
            new LootRow("minecraft:melon_seeds", 2, 4, 10),
            new LootRow("minecraft:pumpkin_seeds", 2, 4, 10),
            new LootRow("minecraft:saddle", 1, 1, 3),
            new LootRow("minecraft:iron_horse_armor", 1, 1, 1));

    /** The total weight (WeightedPicker's own pick denominator). */
    private static final int LOOT_TOTAL_WEIGHT = LOOT_ENTRIES.stream()
            .mapToInt(LootRow::weight).sum();

    /** One loot row (the reference's LootEntry: min/max count + weight + the metadata column). */
    private record LootRow(String itemId, int minChance, int maxChance, int weight, int damage) {

        /** The plain shape (metadata 0). */
        LootRow(String itemId, int minChance, int maxChance, int weight) {
            this(itemId, minChance, maxChance, weight, 0);
        }
    }

    /** WeightedPicker.pick: the descending-weight walk over the table. */
    private static LootRow pickLoot(Random random) {
        int pick = random.nextInt(LOOT_TOTAL_WEIGHT);
        for (LootRow entry : LOOT_ENTRIES) {
            pick -= entry.weight();
            if (pick < 0) {
                return entry;
            }
        }
        return LOOT_ENTRIES.get(LOOT_ENTRIES.size() - 1);
    }

    /** The next-piece roll (createPiece lines 45-65): corridor / stairs / crossing. */
    private static StructurePiece createPiece(List<StructurePiece> pieces, Random random,
                                              int x, int y, int z, StructureFacing facing,
                                              int generationDepth) {
        int roll = random.nextInt(100);
        if (roll >= 80) {
            StructureBox box = Crossing.findSize(pieces, random, x, y, z, facing);
            if (box != null) {
                return new Crossing(generationDepth, random, box, facing);
            }
        } else if (roll >= 70) {
            StructureBox box = Stairs.findSize(pieces, random, x, y, z, facing);
            if (box != null) {
                return new Stairs(generationDepth, random, box, facing);
            }
        } else {
            StructureBox box = Corridor.findSize(pieces, random, x, y, z, facing);
            if (box != null) {
                return new Corridor(generationDepth, random, box, facing);
            }
        }
        return null;
    }

    /**
     * The recursive step (generateNextPiece lines 67-85): the depth cap of
     * 8, the ±80 horizontal leash from the start piece, then the child
     * walk.
     */
    static StructurePiece generateNextPiece(StructurePiece startPiece, List<StructurePiece> pieces,
                                            Random random, int x, int y, int z,
                                            StructureFacing facing, int generationDepth) {
        if (generationDepth > 8) {
            return null;
        }
        if (Math.abs(x - startPiece.getBounds().minX) <= 80
                && Math.abs(z - startPiece.getBounds().minZ) <= 80) {
            StructurePiece piece = createPiece(pieces, random, x, y, z, facing, generationDepth + 1);
            if (piece != null) {
                pieces.add(piece);
                piece.addChildren(startPiece, pieces, random);
            }
            return piece;
        }
        return null;
    }

    // ---------------------------------------------------------------- corridor

    /**
     * The corridor (Corridor lines 87-368): 3 wide, 3 tall, 5-cell
     * sections; the 1-in-3 rails, the 1-in-23 cobwebs, the fence frame
     * with the plank caps, the floor patch under the air, the torch
     * rolls.
     */
    static final class Corridor extends StructurePiece {
        private final boolean hasRails;
        private final boolean hasCobwebs;
        private boolean hasSpiderSpawner;
        private final int sections;

        Corridor(int generationDepth, Random random, StructureBox box, StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = box;
            this.hasRails = random.nextInt(3) == 0;
            this.hasCobwebs = !this.hasRails && random.nextInt(23) == 0;
            if (this.facing != StructureFacing.NORTH && this.facing != StructureFacing.SOUTH) {
                this.sections = box.getSpanX() / 5;
            } else {
                this.sections = box.getSpanZ() / 5;
            }
        }

        static StructureBox findSize(List<StructurePiece> pieces, Random random,
                                    int x, int y, int z, StructureFacing facing) {
            StructureBox box = new StructureBox(x, y, z, x, y + 2, z);
            int sections;
            for (sections = random.nextInt(3) + 2; sections > 0; sections--) {
                int length = sections * 5;
                switch (facing) {
                    case NORTH -> {
                        box.maxX = x + 2;
                        box.minZ = z - (length - 1);
                    }
                    case SOUTH -> {
                        box.maxX = x + 2;
                        box.maxZ = z + (length - 1);
                    }
                    case WEST -> {
                        box.minX = x - (length - 1);
                        box.maxZ = z + 2;
                    }
                    case EAST -> {
                        box.maxX = x + (length - 1);
                        box.maxZ = z + 2;
                    }
                }
                if (StructurePiece.getIntersectingPiece(pieces, box) == null) {
                    break;
                }
            }
            return sections > 0 ? box : null;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            int depth = this.getGenerationDepth();
            int roll = random.nextInt(4);
            if (this.facing != null) {
                switch (this.facing) {
                    case NORTH -> {
                        if (roll <= 1) {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    this.bounds.minX, this.bounds.minY - 1 + random.nextInt(3),
                                    this.bounds.minZ - 1, this.facing, depth);
                        } else if (roll == 2) {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    this.bounds.minX - 1, this.bounds.minY - 1 + random.nextInt(3),
                                    this.bounds.minZ, StructureFacing.WEST, depth);
                        } else {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    this.bounds.maxX + 1, this.bounds.minY - 1 + random.nextInt(3),
                                    this.bounds.minZ, StructureFacing.EAST, depth);
                        }
                    }
                    case SOUTH -> {
                        if (roll <= 1) {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    this.bounds.minX, this.bounds.minY - 1 + random.nextInt(3),
                                    this.bounds.maxZ + 1, this.facing, depth);
                        } else if (roll == 2) {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    this.bounds.minX - 1, this.bounds.minY - 1 + random.nextInt(3),
                                    this.bounds.maxZ - 3, StructureFacing.WEST, depth);
                        } else {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    this.bounds.maxX + 1, this.bounds.minY - 1 + random.nextInt(3),
                                    this.bounds.maxZ - 3, StructureFacing.EAST, depth);
                        }
                    }
                    case WEST -> {
                        if (roll <= 1) {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    this.bounds.minX - 1, this.bounds.minY - 1 + random.nextInt(3),
                                    this.bounds.minZ, this.facing, depth);
                        } else if (roll == 2) {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    this.bounds.minX, this.bounds.minY - 1 + random.nextInt(3),
                                    this.bounds.minZ - 1, StructureFacing.NORTH, depth);
                        } else {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    this.bounds.minX, this.bounds.minY - 1 + random.nextInt(3),
                                    this.bounds.maxZ + 1, StructureFacing.SOUTH, depth);
                        }
                    }
                    case EAST -> {
                        if (roll <= 1) {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    this.bounds.maxX + 1, this.bounds.minY - 1 + random.nextInt(3),
                                    this.bounds.minZ, this.facing, depth);
                        } else if (roll == 2) {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    this.bounds.maxX - 3, this.bounds.minY - 1 + random.nextInt(3),
                                    this.bounds.minZ - 1, StructureFacing.NORTH, depth);
                        } else {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    this.bounds.maxX - 3, this.bounds.minY - 1 + random.nextInt(3),
                                    this.bounds.maxZ + 1, StructureFacing.SOUTH, depth);
                        }
                    }
                }
            }
            if (depth < 8) {
                if (this.facing != StructureFacing.NORTH && this.facing != StructureFacing.SOUTH) {
                    for (int x = this.bounds.minX + 3; x + 3 <= this.bounds.maxX; x += 5) {
                        int sideRoll = random.nextInt(5);
                        if (sideRoll == 0) {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    x, this.bounds.minY, this.bounds.minZ - 1,
                                    StructureFacing.NORTH, depth + 1);
                        } else if (sideRoll == 1) {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    x, this.bounds.minY, this.bounds.maxZ + 1,
                                    StructureFacing.SOUTH, depth + 1);
                        }
                    }
                } else {
                    for (int z = this.bounds.minZ + 3; z + 3 <= this.bounds.maxZ; z += 5) {
                        int sideRoll = random.nextInt(5);
                        if (sideRoll == 0) {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    this.bounds.minX - 1, this.bounds.minY, z,
                                    StructureFacing.WEST, depth + 1);
                        } else if (sideRoll == 1) {
                            MineshaftPieces.generateNextPiece(start, pieces, random,
                                    this.bounds.maxX + 1, this.bounds.minY, z,
                                    StructureFacing.EAST, depth + 1);
                        }
                    }
                }
            }
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            int span = this.sections * 5 - 1;
            // The walkway (postProcess lines 269-278): the full air carve,
            // then the 0.8 shuffle of the top row.
            this.fillWithOutline(world, clip, 0, 0, 0, 2, 1, span,
                    palette.air(), palette.air(), false);
            this.fillRandomlyWithOutline(world, clip, random, 0.8F, 0, 2, 0, 2, 2, span,
                    palette.air(), palette.air(), false);
            if (this.hasCobwebs) {
                this.fillRandomlyWithOutline(world, clip, random, 0.6F, 0, 0, 0, 2, 1, span,
                        palette.cobweb(), palette.air(), false);
            }
            // The section frames (lines 280-298): the fence posts, the
            // plank caps, the cobweb and torch rolls.
            for (int section = 0; section < this.sections; section++) {
                int at = 2 + section * 5;
                this.fillWithOutline(world, clip, 0, 0, at, 0, 1, at,
                        palette.fence(), palette.air(), false);
                this.fillWithOutline(world, clip, 2, 0, at, 2, 1, at,
                        palette.fence(), palette.air(), false);
                if (random.nextInt(4) == 0) {
                    this.fillWithOutline(world, clip, 0, 2, at, 0, 2, at,
                            palette.planks(), palette.air(), false);
                    this.fillWithOutline(world, clip, 2, 2, at, 2, 2, at,
                            palette.planks(), palette.air(), false);
                } else {
                    this.fillWithOutline(world, clip, 0, 2, at, 2, 2, at,
                            palette.planks(), palette.air(), false);
                }
                this.setBlockStateWithThreshold(world, clip, random, 0.1F, 0, 2, at - 1, palette.cobweb());
                this.setBlockStateWithThreshold(world, clip, random, 0.1F, 2, 2, at - 1, palette.cobweb());
                this.setBlockStateWithThreshold(world, clip, random, 0.1F, 0, 2, at + 1, palette.cobweb());
                this.setBlockStateWithThreshold(world, clip, random, 0.1F, 2, 2, at + 1, palette.cobweb());
                this.setBlockStateWithThreshold(world, clip, random, 0.05F, 0, 2, at - 2, palette.cobweb());
                this.setBlockStateWithThreshold(world, clip, random, 0.05F, 2, 2, at - 2, palette.cobweb());
                this.setBlockStateWithThreshold(world, clip, random, 0.05F, 0, 2, at + 2, palette.cobweb());
                this.setBlockStateWithThreshold(world, clip, random, 0.05F, 2, 2, at + 2, palette.cobweb());
                this.setBlockStateWithThreshold(world, clip, random, 0.05F, 1, 2, at - 1, palette.torch());
                this.setBlockStateWithThreshold(world, clip, random, 0.05F, 1, 2, at + 1, palette.torch());
                // The chest-minecart arms (lines 301-325, Slice 21a): the
                // two 1-in-100 rolls per section — the rail cell + the cart
                // record (the loot walk mirrors LootEntry.addLoot with the
                // enchanted book riding addAll's own tail).
                if (random.nextInt(100) == 0) {
                    this.placeChestMinecartWithLoot(world, clip, random, 2, 0, at - 1);
                }
                if (random.nextInt(100) == 0) {
                    this.placeChestMinecartWithLoot(world, clip, random, 0, 0, at + 1);
                }

                // The cobweb-and-spawner pair (lines 327-341, 1:1): the
                // spawner at the section's random cell — the block + the
                // typed record (the reference's setType("CaveSpider")).
                if (this.hasCobwebs && !this.hasSpiderSpawner) {
                    int spawnerY = this.transformY(0);
                    int at2 = at - 1 + random.nextInt(3);
                    int spawnerX = this.transformX(1, at2);
                    int spawnerZ = this.transformZ(1, at2);
                    if (clip.contains(spawnerX, spawnerY, spawnerZ)) {
                        this.hasSpiderSpawner = true;
                        this.setBlockState(world, palette.mobSpawner(), 1, 0, at2, clip);
                        world.placeMobSpawner(spawnerX, spawnerY, spawnerZ,
                                net.zaminmc.torch.server.entity.MobType.CAVE_SPIDER);
                    }
                }
            }
            // The floor patch (lines 344-353): a plank under every open
            // bottom cell.
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= span; z++) {
                    BlockType below = this.getBlockState(world, x, -1, z, clip);
                    if (below == null || world.isAir(below)) {
                        this.setBlockState(world, palette.planks(), x, -1, z, clip);
                    }
                }
            }
            // The rails (lines 355-364): 0.7 per cell over a solid floor.
            if (this.hasRails) {
                for (int z = 0; z <= span; z++) {
                    BlockType floor = this.getBlockState(world, 1, -1, z, clip);
                    if (floor != null && !world.isAir(floor) && world.isOpaque(floor)) {
                        this.setBlockStateWithThreshold(world, clip, random, 0.7F, 1, 0, z,
                                palette.rail(this.railMetadata(0)));
                    }
                }
            }
            return true;
        }

        /**
         * The loot-carrying chest minecart (the Corridor's own
         * placeChestWithLoot override, lines 246-259 + LootEntry.addLoot
         * lines 34-48): the air-gated rail cell (the boolean orientation
         * walk), then the cart record with the rolled contents — the
         * reference's world.addEntity rides the chunk's pending queue
         * here (the cart materializes into the vehicle manager at chunk
         * publish, Slice 21a).
         */
        private void placeChestMinecartWithLoot(StructureWorld world, StructureBox clip,
                                                Random random, int x, int y, int z) {
            StructurePalette palette = world.palette();
            int wx = this.transformX(x, z);
            int wy = this.transformY(y);
            int wz = this.transformZ(x, z);
            if (!clip.contains(wx, wy, wz) || !world.isAir(world.blockAt(wx, wy, wz))) {
                return; // the reference's bounds.contains + Material.AIR gate
            }
            // The rail under the cart (the nextBoolean orientation).
            this.setBlockState(world, palette.rail(this.railMetadata(random.nextBoolean() ? 1 : 0)),
                    x, y, z, clip);
            // The book row rolls FIRST (the reference's argument order:
            // addAll's createLoot consumes the stream before the amount's
            // nextInt(4)).
            var bookRow = StrongholdPieces.rollBookLoot(random, 1, 1, 1);
            int amount = 3 + random.nextInt(4);
            java.util.Map<Integer, StructureWorld.GenLootSlot> slots = new java.util.LinkedHashMap<>();
            for (int i = 0; i < amount; i++) {
                // WeightedPicker.pick over the table with the book row
                // appended at the end (LootEntry.addAll's own order).
                int total = LOOT_TOTAL_WEIGHT + bookRow.weight();
                int pick = random.nextInt(total);
                LootRow chosen = null;
                for (LootRow entry : LOOT_ENTRIES) {
                    pick -= entry.weight();
                    if (pick < 0) {
                        chosen = entry;
                        break;
                    }
                }
                boolean isBook = false;
                if (chosen == null) {
                    pick -= bookRow.weight();
                    isBook = pick < 0;
                }
                if (isBook) {
                    // The enchanted book (the reference's own shape: the
                    // book stacks to one, so the rolled count splits into
                    // single-book stacks at their own random slots).
                    int slot = random.nextInt(27);
                    slots.put(slot, new StructureWorld.GenLootSlot(slot,
                            "minecraft:enchanted_book", 1, bookRow.enchantments()));
                    continue;
                }
                int count = chosen.minChance()
                        + random.nextInt(chosen.maxChance() - chosen.minChance() + 1);
                int maxStack = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                                net.zaminmc.torch.util.Identifier.parse(chosen.itemId()))
                        .map(net.zaminmc.torch.item.ItemType::maxStackSize).orElse(64);
                if (maxStack >= count) {
                    int slot = random.nextInt(27);
                    slots.put(slot, new StructureWorld.GenLootSlot(slot, chosen.itemId(), count,
                            chosen.damage()));
                } else {
                    for (int k = 0; k < count; k++) {
                        int slot = random.nextInt(27);
                        slots.put(slot, new StructureWorld.GenLootSlot(slot, chosen.itemId(), 1,
                                chosen.damage()));
                    }
                }
            }
            world.placeGenChestMinecart(wx, wy, wz, List.copyOf(slots.values()));
        }
    }

    // ---------------------------------------------------------------- crossing

    /**
     * The crossing (Crossing lines 370-643): the 5x5x3 junction (the
     * rare second floor), the plank pillars, the floor patch.
     */
    static final class Crossing extends StructurePiece {
        private final StructureFacing direction;
        private final boolean hasTwoFloors;

        Crossing(int generationDepth, Random random, StructureBox box, StructureFacing direction) {
            super(generationDepth);
            this.direction = direction;
            this.bounds = box;
            this.hasTwoFloors = box.getSpanY() > 3;
        }

        static StructureBox findSize(List<StructurePiece> pieces, Random random,
                                     int x, int y, int z, StructureFacing facing) {
            StructureBox box = new StructureBox(x, y, z, x, y + 2, z);
            if (random.nextInt(4) == 0) {
                box.maxY += 4;
            }
            switch (facing) {
                case NORTH -> {
                    box.minX = x - 1;
                    box.maxX = x + 3;
                    box.minZ = z - 4;
                }
                case SOUTH -> {
                    box.minX = x - 1;
                    box.maxX = x + 3;
                    box.maxZ = z + 4;
                }
                case WEST -> {
                    box.minX = x - 4;
                    box.minZ = z - 1;
                    box.maxZ = z + 3;
                }
                case EAST -> {
                    box.maxX = x + 4;
                    box.minZ = z - 1;
                    box.maxZ = z + 3;
                }
            }
            return StructurePiece.getIntersectingPiece(pieces, box) != null ? null : box;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            int depth = this.getGenerationDepth();
            switch (this.direction) {
                case NORTH -> {
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX + 1, this.bounds.minY, this.bounds.minZ - 1,
                            StructureFacing.NORTH, depth);
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX - 1, this.bounds.minY, this.bounds.minZ + 1,
                            StructureFacing.WEST, depth);
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.maxX + 1, this.bounds.minY, this.bounds.minZ + 1,
                            StructureFacing.EAST, depth);
                }
                case SOUTH -> {
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX + 1, this.bounds.minY, this.bounds.maxZ + 1,
                            StructureFacing.SOUTH, depth);
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX - 1, this.bounds.minY, this.bounds.minZ + 1,
                            StructureFacing.WEST, depth);
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.maxX + 1, this.bounds.minY, this.bounds.minZ + 1,
                            StructureFacing.EAST, depth);
                }
                case WEST -> {
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX + 1, this.bounds.minY, this.bounds.minZ - 1,
                            StructureFacing.NORTH, depth);
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX + 1, this.bounds.minY, this.bounds.maxZ + 1,
                            StructureFacing.SOUTH, depth);
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX - 1, this.bounds.minY, this.bounds.minZ + 1,
                            StructureFacing.WEST, depth);
                }
                case EAST -> {
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX + 1, this.bounds.minY, this.bounds.minZ - 1,
                            StructureFacing.NORTH, depth);
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX + 1, this.bounds.minY, this.bounds.maxZ + 1,
                            StructureFacing.SOUTH, depth);
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.maxX + 1, this.bounds.minY, this.bounds.minZ + 1,
                            StructureFacing.EAST, depth);
                }
            }
            if (this.hasTwoFloors) {
                if (random.nextBoolean()) {
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX + 1, this.bounds.minY + 3 + 1, this.bounds.minZ - 1,
                            StructureFacing.NORTH, depth);
                }
                if (random.nextBoolean()) {
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX - 1, this.bounds.minY + 3 + 1, this.bounds.minZ + 1,
                            StructureFacing.WEST, depth);
                }
                if (random.nextBoolean()) {
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.maxX + 1, this.bounds.minY + 3 + 1, this.bounds.minZ + 1,
                            StructureFacing.EAST, depth);
                }
                if (random.nextBoolean()) {
                    MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX + 1, this.bounds.minY + 3 + 1, this.bounds.maxZ + 1,
                            StructureFacing.SOUTH, depth);
                }
            }
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            // The carve (lines 485-578): the two-floor pairs, the
            // cross-shaped openings.
            if (this.hasTwoFloors) {
                this.fillWithOutline(world, clip, this.bounds.minX + 1, this.bounds.minY,
                        this.bounds.minZ, this.bounds.maxX - 1, this.bounds.minY + 3 - 1,
                        this.bounds.maxZ, palette.air(), palette.air(), false);
                this.fillWithOutline(world, clip, this.bounds.minX, this.bounds.minY,
                        this.bounds.minZ + 1, this.bounds.maxX, this.bounds.minY + 3 - 1,
                        this.bounds.maxZ - 1, palette.air(), palette.air(), false);
                this.fillWithOutline(world, clip, this.bounds.minX + 1, this.bounds.maxY - 2,
                        this.bounds.minZ, this.bounds.maxX - 1, this.bounds.maxY,
                        this.bounds.maxZ, palette.air(), palette.air(), false);
                this.fillWithOutline(world, clip, this.bounds.minX, this.bounds.maxY - 2,
                        this.bounds.minZ + 1, this.bounds.maxX, this.bounds.maxY,
                        this.bounds.maxZ - 1, palette.air(), palette.air(), false);
                this.fillWithOutline(world, clip, this.bounds.minX + 1, this.bounds.minY + 3,
                        this.bounds.minZ + 1, this.bounds.maxX - 1, this.bounds.minY + 3,
                        this.bounds.maxZ - 1, palette.air(), palette.air(), false);
            } else {
                this.fillWithOutline(world, clip, this.bounds.minX + 1, this.bounds.minY,
                        this.bounds.minZ, this.bounds.maxX - 1, this.bounds.maxY,
                        this.bounds.maxZ, palette.air(), palette.air(), false);
                this.fillWithOutline(world, clip, this.bounds.minX, this.bounds.minY,
                        this.bounds.minZ + 1, this.bounds.maxX, this.bounds.maxY,
                        this.bounds.maxZ - 1, palette.air(), palette.air(), false);
            }
            // The plank pillars (lines 580-631): the four corner columns.
            this.fillWithOutline(world, clip, this.bounds.minX + 1, this.bounds.minY,
                    this.bounds.minZ + 1, this.bounds.minX + 1, this.bounds.maxY,
                    this.bounds.minZ + 1, palette.planks(), palette.air(), false);
            this.fillWithOutline(world, clip, this.bounds.minX + 1, this.bounds.minY,
                    this.bounds.maxZ - 1, this.bounds.minX + 1, this.bounds.maxY,
                    this.bounds.maxZ - 1, palette.planks(), palette.air(), false);
            this.fillWithOutline(world, clip, this.bounds.maxX - 1, this.bounds.minY,
                    this.bounds.minZ + 1, this.bounds.maxX - 1, this.bounds.maxY,
                    this.bounds.minZ + 1, palette.planks(), palette.air(), false);
            this.fillWithOutline(world, clip, this.bounds.maxX - 1, this.bounds.minY,
                    this.bounds.maxZ - 1, this.bounds.maxX - 1, this.bounds.maxY,
                    this.bounds.maxZ - 1, palette.planks(), palette.air(), false);
            // The floor patch (lines 633-639).
            for (int x = this.bounds.minX; x <= this.bounds.maxX; x++) {
                for (int z = this.bounds.minZ; z <= this.bounds.maxZ; z++) {
                    BlockType below = this.getBlockState(world, x, this.bounds.minY - 1, z, clip);
                    if (below == null || world.isAir(below)) {
                        this.setBlockState(world, palette.planks(), x, this.bounds.minY - 1, z, clip);
                    }
                }
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- room

    /**
     * The starting room (Room lines 645-851): the 8-13 wide dirt-floored
     * hall whose four walls grow the corridor tree; the entrance cutouts
     * ride with the pieces.
     */
    static final class Room extends StructurePiece {
        private final java.util.List<StructureBox> entrances = new java.util.LinkedList<>();

        Room(int generationDepth, Random random, int x, int z) {
            super(generationDepth);
            this.bounds = new StructureBox(x, 50, z,
                    x + 7 + random.nextInt(6), 54 + random.nextInt(6),
                    z + 7 + random.nextInt(6));
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            int depth = this.getGenerationDepth();
            int levels = this.bounds.getSpanY() - 3 - 1;
            if (levels <= 0) {
                levels = 1;
            }
            int at = 0;
            while (at < this.bounds.getSpanX()) {
                at += random.nextInt(this.bounds.getSpanX());
                if (at + 3 > this.bounds.getSpanX()) {
                    break;
                }
                StructurePiece piece = MineshaftPieces.generateNextPiece(start, pieces, random,
                        this.bounds.minX + at, this.bounds.minY + random.nextInt(levels) + 1,
                        this.bounds.minZ - 1, StructureFacing.NORTH, depth);
                if (piece != null) {
                    StructureBox child = piece.getBounds();
                    this.entrances.add(new StructureBox(child.minX, child.minY, this.bounds.minZ,
                            child.maxX, child.maxY, this.bounds.minZ + 1));
                }
                at += 4;
            }
            at = 0;
            while (at < this.bounds.getSpanX()) {
                at += random.nextInt(this.bounds.getSpanX());
                if (at + 3 > this.bounds.getSpanX()) {
                    break;
                }
                StructurePiece piece = MineshaftPieces.generateNextPiece(start, pieces, random,
                        this.bounds.minX + at, this.bounds.minY + random.nextInt(levels) + 1,
                        this.bounds.maxZ + 1, StructureFacing.SOUTH, depth);
                if (piece != null) {
                    StructureBox child = piece.getBounds();
                    this.entrances.add(new StructureBox(child.minX, child.minY, this.bounds.maxZ - 1,
                            child.maxX, child.maxY, this.bounds.maxZ));
                }
                at += 4;
            }
            at = 0;
            while (at < this.bounds.getSpanZ()) {
                at += random.nextInt(this.bounds.getSpanZ());
                if (at + 3 > this.bounds.getSpanZ()) {
                    break;
                }
                StructurePiece piece = MineshaftPieces.generateNextPiece(start, pieces, random,
                        this.bounds.minX - 1, this.bounds.minY + random.nextInt(levels) + 1,
                        this.bounds.minZ + at, StructureFacing.WEST, depth);
                if (piece != null) {
                    StructureBox child = piece.getBounds();
                    this.entrances.add(new StructureBox(this.bounds.minX, child.minY, child.minZ,
                            this.bounds.minX + 1, child.maxY, child.maxZ));
                }
                at += 4;
            }
            at = 0;
            while (at < this.bounds.getSpanZ()) {
                at += random.nextInt(this.bounds.getSpanZ());
                if (at + 3 > this.bounds.getSpanZ()) {
                    break;
                }
                StructurePiece piece = MineshaftPieces.generateNextPiece(start, pieces, random,
                        this.bounds.maxX + 1, this.bounds.minY + random.nextInt(levels) + 1,
                        this.bounds.minZ + at, StructureFacing.EAST, depth);
                if (piece != null) {
                    StructureBox child = piece.getBounds();
                    this.entrances.add(new StructureBox(this.bounds.maxX - 1, child.minY, child.minZ,
                            this.bounds.maxX, child.maxY, child.maxZ));
                }
                at += 4;
            }
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            // The dirt floor over solid ground, then the air hall (lines
            // 765-790).
            this.fillWithOutline(world, clip, this.bounds.minX, this.bounds.minY, this.bounds.minZ,
                    this.bounds.maxX, this.bounds.minY, this.bounds.maxZ,
                    palette.dirt(), palette.air(), true);
            this.fillWithOutline(world, clip, this.bounds.minX, this.bounds.minY + 1,
                    this.bounds.minZ, this.bounds.maxX,
                    Math.min(this.bounds.minY + 3, this.bounds.maxY), this.bounds.maxZ,
                    palette.air(), palette.air(), false);
            // The entrance cutouts (lines 792-806).
            for (StructureBox entrance : this.entrances) {
                this.fillWithOutline(world, clip, entrance.minX, entrance.maxY - 2, entrance.minZ,
                        entrance.maxX, entrance.maxY, entrance.maxZ,
                        palette.air(), palette.air(), false);
            }
            // The dome carve (lines 808-819).
            this.placeUpperHemisphere(world, clip, this.bounds.minX, this.bounds.minY + 4,
                    this.bounds.minZ, this.bounds.maxX, this.bounds.maxY, this.bounds.maxZ,
                    palette.air(), false);
            return true;
        }

        @Override
        public void move(int dx, int dy, int dz) {
            super.move(dx, dy, dz);
            for (StructureBox entrance : this.entrances) {
                entrance.move(dx, dy, dz);
            }
        }
    }

    // ---------------------------------------------------------------- stairs

    /** The stairs (Stairs lines 853-931): the diagonal shaft carve. */
    static final class Stairs extends StructurePiece {

        Stairs(int generationDepth, Random random, StructureBox box, StructureFacing facing) {
            super(generationDepth);
            this.facing = facing;
            this.bounds = box;
        }

        static StructureBox findSize(List<StructurePiece> pieces, Random random,
                                     int x, int y, int z, StructureFacing facing) {
            StructureBox box = new StructureBox(x, y - 5, z, x, y + 2, z);
            switch (facing) {
                case NORTH -> {
                    box.maxX = x + 2;
                    box.minZ = z - 8;
                }
                case SOUTH -> {
                    box.maxX = x + 2;
                    box.maxZ = z + 8;
                }
                case WEST -> {
                    box.minX = x - 8;
                    box.maxZ = z + 2;
                }
                case EAST -> {
                    box.maxX = x + 8;
                    box.maxZ = z + 2;
                }
            }
            return StructurePiece.getIntersectingPiece(pieces, box) != null ? null : box;
        }

        @Override
        public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
            int depth = this.getGenerationDepth();
            if (this.facing != null) {
                switch (this.facing) {
                    case NORTH -> MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX, this.bounds.minY, this.bounds.minZ - 1,
                            StructureFacing.NORTH, depth);
                    case SOUTH -> MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX, this.bounds.minY, this.bounds.maxZ + 1,
                            StructureFacing.SOUTH, depth);
                    case WEST -> MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.minX - 1, this.bounds.minY, this.bounds.minZ,
                            StructureFacing.WEST, depth);
                    case EAST -> MineshaftPieces.generateNextPiece(start, pieces, random,
                            this.bounds.maxX + 1, this.bounds.minY, this.bounds.minZ,
                            StructureFacing.EAST, depth);
                }
            }
        }

        @Override
        public boolean postProcess(StructureWorld world, Random random, StructureBox clip) {
            StructurePalette palette = world.palette();
            if (this.bordersOnLiquids(world, clip)) {
                return false;
            }
            // The shaft (lines 920-927): the top step, the landing, then
            // the five descending steps.
            this.fillWithOutline(world, clip, 0, 5, 0, 2, 7, 1,
                    palette.air(), palette.air(), false);
            this.fillWithOutline(world, clip, 0, 0, 7, 2, 2, 8,
                    palette.air(), palette.air(), false);
            for (int step = 0; step < 5; step++) {
                this.fillWithOutline(world, clip, 0, 5 - step - (step < 4 ? 1 : 0), 2 + step,
                        2, 7 - step, 2 + step, palette.air(), palette.air(), false);
            }
            return true;
        }
    }
}
