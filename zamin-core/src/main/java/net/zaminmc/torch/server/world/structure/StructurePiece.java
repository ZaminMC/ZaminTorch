package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockType;

import java.util.List;
import java.util.Random;

/**
 * The structure piece base (Slice 12a — the reference port of
 * gen/structure/StructurePiece.java, 1:1 over the engine's
 * {@link StructureWorld}): the bounds + the facing + the piece-local
 * coordinate transform (transformX/Y/Z, the reference's exact
 * quadrant math) and the fill family (fillWithOutline,
 * fillRandomlyWithOutline, setBlockStateWithThreshold,
 * placeUpperHemisphere, bordersOnLiquids).
 *
 * <p>Piece-local coordinates: x/z run 0..span-1 along the facing,
 * y runs 0..spanY-1 up from {@code bounds.minY}; the transform maps them
 * onto the world grid so one geometry serves all four facings.</p>
 */
public abstract class StructurePiece {

    protected StructureBox bounds;
    protected StructureFacing facing;
    private int generationDepth;

    protected StructurePiece(int generationDepth) {
        this.generationDepth = generationDepth;
    }

    public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
    }

    /** Writes this piece's cells inside the clip. @return whether the piece stays. */
    public abstract boolean postProcess(StructureWorld world, Random random, StructureBox clip);

    public StructureBox getBounds() {
        return this.bounds;
    }

    /** The distance from the structure start along the piece tree. */
    public int getGenerationDepth() {
        return this.generationDepth;
    }

    /** The reference's getIntersectingPiece: the first piece the box touches. */
    public static StructurePiece getIntersectingPiece(List<StructurePiece> pieces, StructureBox box) {
        for (StructurePiece piece : pieces) {
            if (piece.getBounds() != null && piece.getBounds().intersects(box)) {
                return piece;
            }
        }
        return null;
    }

    /**
     * The liquid-border probe (bordersOnLiquids lines 87-133): the shell of
     * the piece's box clipped to the current chunk — a liquid on any of the
     * six faces refuses the piece (it stays only when the whole clipped
     * shell is dry).
     */
    protected boolean bordersOnLiquids(StructureWorld world, StructureBox clip) {
        int minX = Math.max(this.bounds.minX - 1, clip.minX);
        int minY = Math.max(this.bounds.minY - 1, clip.minY);
        int minZ = Math.max(this.bounds.minZ - 1, clip.minZ);
        int maxX = Math.min(this.bounds.maxX + 1, clip.maxX);
        int maxY = Math.min(this.bounds.maxY + 1, clip.maxY);
        int maxZ = Math.min(this.bounds.maxZ + 1, clip.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (world.isLiquid(world.blockAt(x, minY, z))) {
                    return true;
                }
                if (world.isLiquid(world.blockAt(x, maxY, z))) {
                    return true;
                }
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (world.isLiquid(world.blockAt(x, y, minZ))) {
                    return true;
                }
                if (world.isLiquid(world.blockAt(x, y, maxZ))) {
                    return true;
                }
            }
        }
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                if (world.isLiquid(world.blockAt(minX, y, z))) {
                    return true;
                }
                if (world.isLiquid(world.blockAt(maxX, y, z))) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------- transforms

    /** The piece-local x → world x (transformX lines 135-151). */
    protected int transformX(int x, int z) {
        if (this.facing == null) {
            return x;
        }
        switch (this.facing) {
            case NORTH:
            case SOUTH:
                return this.bounds.minX + x;
            case WEST:
                return this.bounds.maxX - z;
            case EAST:
                return this.bounds.minX + z;
            default:
                return x;
        }
    }

    /** The piece-local y → world y (transformY lines 153-155). */
    protected int transformY(int y) {
        return this.facing == null ? y : y + this.bounds.minY;
    }

    /** The piece-local z → world z (transformZ lines 157-173). */
    protected int transformZ(int x, int z) {
        if (this.facing == null) {
            return z;
        }
        switch (this.facing) {
            case NORTH:
                return this.bounds.maxZ - z;
            case SOUTH:
                return this.bounds.minZ + z;
            case WEST:
            case EAST:
                return this.bounds.minZ + x;
            default:
                return z;
        }
    }

    /**
     * The rail metadata flip (postProcessBlockMetadata's RAIL arm, lines
     * 176-183): a west/east piece swaps the rail's orientation bit.
     */
    protected int railMetadata(int metadata) {
        if (this.facing == StructureFacing.WEST || this.facing == StructureFacing.EAST) {
            return metadata == 1 ? 0 : 1;
        }
        return metadata;
    }

    /**
     * The metadata families postProcessBlockMetadata flips (the reference's
     * own block-set dispatch, lines 175-393): each family carries its own
     * metadata band and its own per-facing remap.
     */
    protected enum MetadataFamily {
        /** Blocks.RAIL — the orientation bit. */
        RAIL,
        /** Any DoorBlock — the horizontal facing band. */
        DOOR,
        /** The stairs family (oak/stone/nether brick/stone brick/sandstone) — the ascending band. */
        STAIRS,
        /** Blocks.LADDER — the Direction id band (N=2 S=3 W=4 E=5). */
        LADDER,
        /** Blocks.STONE_BUTTON — the attachment band (3/4 the wall sides). */
        STONE_BUTTON,
        /** The tripwire hook and the HorizontalFacing family — the horizontal id band. */
        HORIZONTAL,
        /** The piston/lever/dispenser family — the Direction id band. */
        PISTON
    }

    /**
     * The facing-aware metadata remap (postProcessBlockMetadata, 1:1): the
     * reference flips a piece-placed block's metadata by the piece's own
     * rotation so the block reads correctly in the world. NORTH pieces
     * pass every band unchanged (the reference's own default arm).
     */
    protected int postProcessMetadata(MetadataFamily family, int metadata) {
        if (this.facing == null) {
            return metadata;
        }
        return switch (family) {
            case RAIL -> this.facing == StructureFacing.WEST || this.facing == StructureFacing.EAST
                    ? (metadata == 1 ? 0 : 1)
                    : metadata;
            case DOOR -> switch (this.facing) {
                case SOUTH -> metadata == 0 ? 2 : (metadata == 2 ? 0 : metadata);
                case WEST -> (metadata + 1) & 3;
                case EAST -> (metadata + 3) & 3;
                default -> metadata;
            };
            case STAIRS -> switch (this.facing) {
                case SOUTH -> metadata == 2 ? 3 : (metadata == 3 ? 2 : metadata);
                case WEST -> metadata == 0 ? 2 : (metadata == 1 ? 3 : (metadata == 2 ? 0 : 1));
                case EAST -> metadata == 0 ? 2 : (metadata == 1 ? 3 : (metadata == 2 ? 1 : 0));
                default -> metadata;
            };
            case LADDER -> switch (this.facing) {
                case SOUTH -> metadata == 2 ? 3 : (metadata == 3 ? 2 : metadata);
                case WEST -> metadata == 2 ? 4 : (metadata == 3 ? 5 : (metadata == 4 ? 2 : 3));
                case EAST -> metadata == 2 ? 5 : (metadata == 3 ? 4 : (metadata == 4 ? 2 : 3));
                default -> metadata;
            };
            case STONE_BUTTON -> switch (this.facing) {
                case SOUTH -> metadata == 3 ? 4 : (metadata == 4 ? 3 : metadata);
                case WEST -> metadata == 3 ? 1 : (metadata == 4 ? 2 : (metadata == 2 ? 3 : 4));
                case EAST -> metadata == 3 ? 2 : (metadata == 4 ? 1 : (metadata == 2 ? 3 : 4));
                default -> metadata;
            };
            case HORIZONTAL -> switch (this.facing) {
                // The band's own ids: S=0 W=1 N=2 E=3.
                case SOUTH -> metadata == 0 ? 2 : (metadata == 2 ? 0 : metadata);
                case WEST -> metadata == 2 ? 1 : (metadata == 0 ? 3 : (metadata == 1 ? 2 : 0));
                case EAST -> metadata == 2 ? 3 : (metadata == 0 ? 1 : (metadata == 1 ? 2 : 0));
                default -> metadata;
            };
            case PISTON -> switch (this.facing) {
                // The Direction id band: N=2 S=3 W=4 E=5.
                case SOUTH -> metadata == 2 ? 3 : (metadata == 3 ? 2 : metadata);
                case WEST -> metadata == 2 ? 4 : (metadata == 3 ? 5 : (metadata == 4 ? 2 : 3));
                case EAST -> metadata == 2 ? 5 : (metadata == 3 ? 4 : (metadata == 4 ? 2 : 3));
                default -> metadata;
            };
        };
    }

    // ---------------------------------------------------------------- writes

    /** One cell write, clipped (setBlockState lines 414-419). */
    protected void setBlockState(StructureWorld world, BlockType type, int x, int y, int z,
                                StructureBox clip) {
        int wx = this.transformX(x, z);
        int wy = this.transformY(y);
        int wz = this.transformZ(x, z);
        if (clip.contains(wx, wy, wz)) {
            world.setBlock(wx, wy, wz, type);
        }
    }

    /** One cell read, clipped (getBlockState lines 421-427 — air outside). */
    protected BlockType getBlockState(StructureWorld world, int x, int y, int z, StructureBox clip) {
        int wx = this.transformX(x, z);
        int wy = this.transformY(y);
        int wz = this.transformZ(x, z);
        return !clip.contains(wx, wy, wz)
                ? null : world.blockAt(wx, wy, wz);
    }

    /** The outlined box fill (fillWithOutline lines 439-455): edge vs inner. */
    protected void fillWithOutline(StructureWorld world, StructureBox clip,
                                   int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                   BlockType edge, BlockType filler, boolean avoidAir) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockType current = this.getBlockState(world, x, y, z, clip);
                    if (!avoidAir || current == null || !world.isAir(current)) {
                        if (y != minY && y != maxY && x != minX && x != maxX && z != minZ && z != maxZ) {
                            this.setBlockState(world, filler, x, y, z, clip);
                        } else {
                            this.setBlockState(world, edge, x, y, z, clip);
                        }
                    }
                }
            }
        }
    }

    /**
     * The threshold fill (fillRandomlyWithOutline lines 482-512): the
     * reference's own {@code !(random.nextFloat() > threshold)} gate —
     * the inclusive side of the roll.
     */
    protected void fillRandomlyWithOutline(StructureWorld world, StructureBox clip,
                                           Random random, float threshold,
                                           int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                           BlockType edge, BlockType filler, boolean avoidAir) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!(random.nextFloat() > threshold)) {
                        BlockType current = this.getBlockState(world, x, y, z, clip);
                        if (!avoidAir || current == null || !world.isAir(current)) {
                            if (y != minY && y != maxY && x != minX && x != maxX
                                    && z != minZ && z != maxZ) {
                                this.setBlockState(world, filler, x, y, z, clip);
                            } else {
                                this.setBlockState(world, edge, x, y, z, clip);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The plain air fill (fillAir lines 429-437, 1:1 — the jungle
     * temple's carving pass).
     */
    protected void fillAir(StructureWorld world, StructureBox clip,
                           int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    this.setBlockState(world, world.palette().air(), x, y, z, clip);
                }
            }
        }
    }

    /** The single-cell chance write (setBlockStateWithThreshold lines 514-518). */
    protected void setBlockStateWithThreshold(StructureWorld world, StructureBox clip,
                                               Random random, float threshold,
                                               int x, int y, int z, BlockType type) {
        if (random.nextFloat() < threshold) {
            this.setBlockState(world, type, x, y, z, clip);
        }
    }

    /**
     * The per-cell block picker (the reference's BlockPicker, lines 614-625):
     * one {@code pick} call per written cell, the chosen state landing in the
     * {@link #fillPicked} loop. The reference's own pickers roll the random
     * inside the call (the StoneBrickPicker's own band walk).
     */
    protected interface BlockPicker {
        /** Chooses the block for one cell (the reference's pick + getBlockState pair). */
        BlockType pick(Random random, int x, int y, int z, boolean shell);
    }

    /**
     * The picker fill (fill lines 457-480, 1:1): the outlined walk with a
     * per-cell picker instead of a fixed pair — the reference's own gate
     * ({@code !avoidAir || the cell is not air}, the out-of-clip read
     * answering air exactly like the reference's box guard) rolls the
     * picker once per passing cell before the clipped write.
     */
    protected void fillPicked(StructureWorld world, StructureBox clip,
                              int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                              boolean avoidAir, Random random, BlockPicker picker) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockType current = this.getBlockState(world, x, y, z, clip);
                    if (!avoidAir || (current != null && !world.isAir(current))) {
                        BlockType picked = picker.pick(random, x, y, z,
                                y == minY || y == maxY || x == minX || x == maxX
                                        || z == minZ || z == maxZ);
                        if (picked != null) {
                            this.setBlockState(world, picked, x, y, z, clip);
                        }
                    }
                }
            }
        }
    }

    /**
     * The upper hemisphere carve (placeUpperHemisphere lines 520-546): the
     * ellipsoid test {@code f8 <= 1.05} over the normalized offsets.
     */
    protected void placeUpperHemisphere(StructureWorld world, StructureBox clip,
                                        int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                        BlockType type, boolean avoidAir) {
        float sizeX = maxX - minX + 1;
        float sizeY = maxY - minY + 1;
        float sizeZ = maxZ - minZ + 1;
        float centerX = minX + sizeX / 2.0F;
        float centerZ = minZ + sizeZ / 2.0F;

        for (int y = minY; y <= maxY; y++) {
            float level = (y - minY) / sizeY;
            for (int x = minX; x <= maxX; x++) {
                float offsetX = (x - centerX) / (sizeX * 0.5F);
                for (int z = minZ; z <= maxZ; z++) {
                    float offsetZ = (z - centerZ) / (sizeZ * 0.5F);
                    BlockType current = this.getBlockState(world, x, y, z, clip);
                    if (!avoidAir || current == null || !world.isAir(current)) {
                        float distance = offsetX * offsetX + level * level + offsetZ * offsetZ;
                        if (distance <= 1.05F) {
                            this.setBlockState(world, type, x, y, z, clip);
                        }
                    }
                }
            }
        }
    }

    /** The whole-piece move (the start's vertical re-base). */
    public void move(int dx, int dy, int dz) {
        this.bounds.move(dx, dy, dz);
    }

    // ------------------------------------------------- the column helpers (Slice 12b)

    /**
     * The clearing column (fillAirColumnUp lines 548-556): clears every
     * non-air block from the cell upward — the structures' terrain
     * flattener that carves the buildings' air out of a hillside.
     */
    protected void fillAirColumnUp(StructureWorld world, int x, int y, int z, StructureBox clip) {
        int wx = this.transformX(x, z);
        int wy = this.transformY(y);
        int wz = this.transformZ(x, z);
        if (clip.contains(wx, wy, wz)) {
            while (wy < 255) {
                BlockType current = world.blockAt(wx, wy, wz);
                if (current == null || world.isAir(current)) {
                    break;
                }
                world.setBlock(wx, wy, wz, world.palette().air());
                wy++;
            }
        }
    }

    /**
     * The founding column (fillColumnDown lines 558-568): fills the cell
     * and every air-or-liquid cell below it — the buildings' foundation
     * that roots a hillside house's floor in the slope.
     */
    protected void fillColumnDown(StructureWorld world, BlockType type, int x, int y, int z,
                                  StructureBox clip) {
        int wx = this.transformX(x, z);
        int wy = this.transformY(y);
        int wz = this.transformZ(x, z);
        if (clip.contains(wx, wy, wz)) {
            while (wy > 1) {
                BlockType current = world.blockAt(wx, wy, wz);
                if (current != null && !world.isAir(current) && !world.isLiquid(current)) {
                    break;
                }
                world.setBlock(wx, wy, wz, type);
                wy--;
            }
        }
    }

    /**
     * The structure door (placeWoodenDoor + DoorItem.place, lines 603-607):
     * the reference flips the metadata, reads the horizontal facing, then
     * turns it once counter-clockwise before the placement — the lower
     * half carries that facing, the upper half sits above it. The engine's
     * door model carries no hinge field — the reference's hinge is
     * client-side-derived from neighbor solidity, so the plain upper half
     * is the observable stand-in (the ledger's envelope note).
     */
    protected void placeWoodenDoor(StructureWorld world, int x, int y, int z, StructureBox clip) {
        int metadata = this.postProcessMetadata(MetadataFamily.DOOR, 1);
        // byIdHorizontal then counterClockwiseY (S 0->E 3, W 1->N 2, N 2->W 1, E 3->S 0).
        int horizontal = switch (metadata & 3) {
            case 0 -> 3;
            case 1 -> 2;
            case 2 -> 1;
            default -> 0;
        };
        BlockType lower = world.palette().doorLower(horizontal);
        this.setBlockState(world, lower, x, y, z, clip);
        this.setBlockState(world, world.palette().doorUpper(), x, y + 1, z, clip);
    }
}
