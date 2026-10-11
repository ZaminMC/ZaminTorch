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

    /** The single-cell chance write (setBlockStateWithThreshold lines 514-518). */
    protected void setBlockStateWithThreshold(StructureWorld world, StructureBox clip,
                                               Random random, float threshold,
                                               int x, int y, int z, BlockType type) {
        if (random.nextFloat() < threshold) {
            this.setBlockState(world, type, x, y, z, clip);
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
}
