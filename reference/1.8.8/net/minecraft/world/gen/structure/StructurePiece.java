package net.minecraft.world.gen.structure;

import java.util.List;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.item.DoorItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public abstract class StructurePiece {
    protected StructureBox bounds;
    protected Direction facing;
    protected int generationDepth;

    public StructurePiece() {
    }

    protected StructurePiece(int generationDepth) {
        this.generationDepth = generationDepth;
    }

    public NbtCompound toNbt() {
        NbtCompound nbtcompound = new NbtCompound();
        nbtcompound.putString("id", StructureRegistry.getId(this));
        nbtcompound.put("BB", this.bounds.toNbt());
        nbtcompound.putInt("O", this.facing == null ? -1 : this.facing.getIdHorizontal());
        nbtcompound.putInt("GD", this.generationDepth);
        this.writeNbt(nbtcompound);
        return nbtcompound;
    }

    protected abstract void writeNbt(NbtCompound nbt);

    public void readNbt(World world, NbtCompound nbt) {
        if (nbt.contains("BB")) {
            this.bounds = new StructureBox(nbt.getIntArray("BB"));
        }

        int i = nbt.getInt("O");
        this.facing = i == -1 ? null : Direction.byIdHorizontal(i);
        this.generationDepth = nbt.getInt("GD");
        this.readNbt(nbt);
    }

    protected abstract void readNbt(NbtCompound nbt);

    public void addChildren(StructurePiece start, List<StructurePiece> pieces, Random random) {
    }

    public abstract boolean postProcess(World world, Random random, StructureBox bounds);

    public StructureBox getBounds() {
        return this.bounds;
    }

    /**
     * Returns the distance between this piece and the structure start, traced along the structure piece tree.
     */
    public int getGenerationDepth() {
        return this.generationDepth;
    }

    public static StructurePiece getIntersectingPiece(List<StructurePiece> pieces, StructureBox bounds) {
        for (StructurePiece structurepiece : pieces) {
            if (structurepiece.getBounds() != null && structurepiece.getBounds().intersects(bounds)) {
                return structurepiece;
            }
        }

        return null;
    }

    public BlockPos getCenterPos() {
        return new BlockPos(this.bounds.getCenter());
    }

    protected boolean bordersOnLiquids(World world, StructureBox bounds) {
        int i = Math.max(this.bounds.minX - 1, bounds.minX);
        int j = Math.max(this.bounds.minY - 1, bounds.minY);
        int k = Math.max(this.bounds.minZ - 1, bounds.minZ);
        int l = Math.min(this.bounds.maxX + 1, bounds.maxX);
        int i1 = Math.min(this.bounds.maxY + 1, bounds.maxY);
        int j1 = Math.min(this.bounds.maxZ + 1, bounds.maxZ);
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int k1 = i; k1 <= l; k1++) {
            for (int l1 = k; l1 <= j1; l1++) {
                if (world.getBlockState(blockpos$mutable.set(k1, j, l1)).getBlock().getMaterial().isLiquid()) {
                    return true;
                }

                if (world.getBlockState(blockpos$mutable.set(k1, i1, l1)).getBlock().getMaterial().isLiquid()) {
                    return true;
                }
            }
        }

        for (int i2 = i; i2 <= l; i2++) {
            for (int k2 = j; k2 <= i1; k2++) {
                if (world.getBlockState(blockpos$mutable.set(i2, k2, k)).getBlock().getMaterial().isLiquid()) {
                    return true;
                }

                if (world.getBlockState(blockpos$mutable.set(i2, k2, j1)).getBlock().getMaterial().isLiquid()) {
                    return true;
                }
            }
        }

        for (int j2 = k; j2 <= j1; j2++) {
            for (int l2 = j; l2 <= i1; l2++) {
                if (world.getBlockState(blockpos$mutable.set(i, l2, j2)).getBlock().getMaterial().isLiquid()) {
                    return true;
                }

                if (world.getBlockState(blockpos$mutable.set(l, l2, j2)).getBlock().getMaterial().isLiquid()) {
                    return true;
                }
            }
        }

        return false;
    }

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

    protected int transformY(int y) {
        return this.facing == null ? y : y + this.bounds.minY;
    }

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

    protected int postProcessBlockMetadata(Block block, int facing) {
        if (block == Blocks.RAIL) {
            if (this.facing == Direction.WEST || this.facing == Direction.EAST) {
                if (facing == 1) {
                    return 0;
                }

                return 1;
            }
        } else if (block instanceof DoorBlock) {
            if (this.facing == Direction.SOUTH) {
                if (facing == 0) {
                    return 2;
                }

                if (facing == 2) {
                    return 0;
                }
            } else {
                if (this.facing == Direction.WEST) {
                    return facing + 1 & 3;
                }

                if (this.facing == Direction.EAST) {
                    return facing + 3 & 3;
                }
            }
        } else if (block != Blocks.STONE_STAIRS
            && block != Blocks.OAK_STAIRS
            && block != Blocks.NETHER_BRICK_STAIRS
            && block != Blocks.STONE_BRICK_STAIRS
            && block != Blocks.SANDSTONE_STAIRS) {
            if (block == Blocks.LADDER) {
                if (this.facing == Direction.SOUTH) {
                    if (facing == Direction.NORTH.getId()) {
                        return Direction.SOUTH.getId();
                    }

                    if (facing == Direction.SOUTH.getId()) {
                        return Direction.NORTH.getId();
                    }
                } else if (this.facing == Direction.WEST) {
                    if (facing == Direction.NORTH.getId()) {
                        return Direction.WEST.getId();
                    }

                    if (facing == Direction.SOUTH.getId()) {
                        return Direction.EAST.getId();
                    }

                    if (facing == Direction.WEST.getId()) {
                        return Direction.NORTH.getId();
                    }

                    if (facing == Direction.EAST.getId()) {
                        return Direction.SOUTH.getId();
                    }
                } else if (this.facing == Direction.EAST) {
                    if (facing == Direction.NORTH.getId()) {
                        return Direction.EAST.getId();
                    }

                    if (facing == Direction.SOUTH.getId()) {
                        return Direction.WEST.getId();
                    }

                    if (facing == Direction.WEST.getId()) {
                        return Direction.NORTH.getId();
                    }

                    if (facing == Direction.EAST.getId()) {
                        return Direction.SOUTH.getId();
                    }
                }
            } else if (block == Blocks.STONE_BUTTON) {
                if (this.facing == Direction.SOUTH) {
                    if (facing == 3) {
                        return 4;
                    }

                    if (facing == 4) {
                        return 3;
                    }
                } else if (this.facing == Direction.WEST) {
                    if (facing == 3) {
                        return 1;
                    }

                    if (facing == 4) {
                        return 2;
                    }

                    if (facing == 2) {
                        return 3;
                    }

                    if (facing == 1) {
                        return 4;
                    }
                } else if (this.facing == Direction.EAST) {
                    if (facing == 3) {
                        return 2;
                    }

                    if (facing == 4) {
                        return 1;
                    }

                    if (facing == 2) {
                        return 3;
                    }

                    if (facing == 1) {
                        return 4;
                    }
                }
            } else if (block == Blocks.TRIPWIRE_HOOK || block instanceof HorizontalFacingBlock) {
                Direction direction = Direction.byIdHorizontal(facing);
                if (this.facing == Direction.SOUTH) {
                    if (direction == Direction.SOUTH || direction == Direction.NORTH) {
                        return direction.getOpposite().getIdHorizontal();
                    }
                } else if (this.facing == Direction.WEST) {
                    if (direction == Direction.NORTH) {
                        return Direction.WEST.getIdHorizontal();
                    }

                    if (direction == Direction.SOUTH) {
                        return Direction.EAST.getIdHorizontal();
                    }

                    if (direction == Direction.WEST) {
                        return Direction.NORTH.getIdHorizontal();
                    }

                    if (direction == Direction.EAST) {
                        return Direction.SOUTH.getIdHorizontal();
                    }
                } else if (this.facing == Direction.EAST) {
                    if (direction == Direction.NORTH) {
                        return Direction.EAST.getIdHorizontal();
                    }

                    if (direction == Direction.SOUTH) {
                        return Direction.WEST.getIdHorizontal();
                    }

                    if (direction == Direction.WEST) {
                        return Direction.NORTH.getIdHorizontal();
                    }

                    if (direction == Direction.EAST) {
                        return Direction.SOUTH.getIdHorizontal();
                    }
                }
            } else if (block == Blocks.PISTON || block == Blocks.STICKY_PISTON || block == Blocks.LEVER || block == Blocks.DISPENSER) {
                if (this.facing == Direction.SOUTH) {
                    if (facing == Direction.NORTH.getId() || facing == Direction.SOUTH.getId()) {
                        return Direction.byId(facing).getOpposite().getId();
                    }
                } else if (this.facing == Direction.WEST) {
                    if (facing == Direction.NORTH.getId()) {
                        return Direction.WEST.getId();
                    }

                    if (facing == Direction.SOUTH.getId()) {
                        return Direction.EAST.getId();
                    }

                    if (facing == Direction.WEST.getId()) {
                        return Direction.NORTH.getId();
                    }

                    if (facing == Direction.EAST.getId()) {
                        return Direction.SOUTH.getId();
                    }
                } else if (this.facing == Direction.EAST) {
                    if (facing == Direction.NORTH.getId()) {
                        return Direction.EAST.getId();
                    }

                    if (facing == Direction.SOUTH.getId()) {
                        return Direction.WEST.getId();
                    }

                    if (facing == Direction.WEST.getId()) {
                        return Direction.NORTH.getId();
                    }

                    if (facing == Direction.EAST.getId()) {
                        return Direction.SOUTH.getId();
                    }
                }
            }
        } else if (this.facing == Direction.SOUTH) {
            if (facing == 2) {
                return 3;
            }

            if (facing == 3) {
                return 2;
            }
        } else if (this.facing == Direction.WEST) {
            if (facing == 0) {
                return 2;
            }

            if (facing == 1) {
                return 3;
            }

            if (facing == 2) {
                return 0;
            }

            if (facing == 3) {
                return 1;
            }
        } else if (this.facing == Direction.EAST) {
            if (facing == 0) {
                return 2;
            }

            if (facing == 1) {
                return 3;
            }

            if (facing == 2) {
                return 1;
            }

            if (facing == 3) {
                return 0;
            }
        }

        return facing;
    }

    protected void setBlockState(World world, BlockState state, int x, int y, int z, StructureBox bounds) {
        BlockPos blockpos = new BlockPos(this.transformX(x, z), this.transformY(y), this.transformZ(x, z));
        if (bounds.contains(blockpos)) {
            world.setBlockState(blockpos, state, 2);
        }
    }

    protected BlockState getBlockState(World world, int x, int y, int z, StructureBox bounds) {
        int i = this.transformX(x, z);
        int j = this.transformY(y);
        int k = this.transformZ(x, z);
        BlockPos blockpos = new BlockPos(i, j, k);
        return !bounds.contains(blockpos) ? Blocks.AIR.defaultState() : world.getBlockState(blockpos);
    }

    protected void fillAir(World world, StructureBox bounds, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int i = minY; i <= maxY; i++) {
            for (int j = minX; j <= maxX; j++) {
                for (int k = minZ; k <= maxZ; k++) {
                    this.setBlockState(world, Blocks.AIR.defaultState(), j, i, k, bounds);
                }
            }
        }
    }

    protected void fillWithOutline(
        World world, StructureBox bounds, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState edge, BlockState filler, boolean avoidAir
    ) {
        for (int i = minY; i <= maxY; i++) {
            for (int j = minX; j <= maxX; j++) {
                for (int k = minZ; k <= maxZ; k++) {
                    if (!avoidAir || this.getBlockState(world, j, i, k, bounds).getBlock().getMaterial() != Material.AIR) {
                        if (i != minY && i != maxY && j != minX && j != maxX && k != minZ && k != maxZ) {
                            this.setBlockState(world, filler, j, i, k, bounds);
                        } else {
                            this.setBlockState(world, edge, j, i, k, bounds);
                        }
                    }
                }
            }
        }
    }

    protected void fill(
        World world,
        StructureBox bounds,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        boolean avoidAir,
        Random random,
        StructurePiece.BlockPicker blocks
    ) {
        for (int i = minY; i <= maxY; i++) {
            for (int j = minX; j <= maxX; j++) {
                for (int k = minZ; k <= maxZ; k++) {
                    if (!avoidAir || this.getBlockState(world, j, i, k, bounds).getBlock().getMaterial() != Material.AIR) {
                        blocks.pick(random, j, i, k, i == minY || i == maxY || j == minX || j == maxX || k == minZ || k == maxZ);
                        this.setBlockState(world, blocks.getBlockState(), j, i, k, bounds);
                    }
                }
            }
        }
    }

    protected void fillRandomlyWithOutline(
        World world,
        StructureBox bounds,
        Random random,
        float threshold,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        BlockState edge,
        BlockState filler,
        boolean avoidAir
    ) {
        for (int i = minY; i <= maxY; i++) {
            for (int j = minX; j <= maxX; j++) {
                for (int k = minZ; k <= maxZ; k++) {
                    if (!(random.nextFloat() > threshold) && (!avoidAir || this.getBlockState(world, j, i, k, bounds).getBlock().getMaterial() != Material.AIR)
                        )
                     {
                        if (i != minY && i != maxY && j != minX && j != maxX && k != minZ && k != maxZ) {
                            this.setBlockState(world, filler, j, i, k, bounds);
                        } else {
                            this.setBlockState(world, edge, j, i, k, bounds);
                        }
                    }
                }
            }
        }
    }

    protected void setBlockStateWithThreshold(World world, StructureBox bounds, Random random, float threshold, int x, int y, int z, BlockState state) {
        if (random.nextFloat() < threshold) {
            this.setBlockState(world, state, x, y, z, bounds);
        }
    }

    protected void placeUpperHemisphere(
        World world, StructureBox bounds, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState state, boolean avoidAir
    ) {
        float f = maxX - minX + 1;
        float f1 = maxY - minY + 1;
        float f2 = maxZ - minZ + 1;
        float f3 = minX + f / 2.0F;
        float f4 = minZ + f2 / 2.0F;

        for (int i = minY; i <= maxY; i++) {
            float f5 = (i - minY) / f1;

            for (int j = minX; j <= maxX; j++) {
                float f6 = (j - f3) / (f * 0.5F);

                for (int k = minZ; k <= maxZ; k++) {
                    float f7 = (k - f4) / (f2 * 0.5F);
                    if (!avoidAir || this.getBlockState(world, j, i, k, bounds).getBlock().getMaterial() != Material.AIR) {
                        float f8 = f6 * f6 + f5 * f5 + f7 * f7;
                        if (f8 <= 1.05F) {
                            this.setBlockState(world, state, j, i, k, bounds);
                        }
                    }
                }
            }
        }
    }

    protected void fillAirColumnUp(World world, int x, int y, int z, StructureBox bounds) {
        BlockPos blockpos = new BlockPos(this.transformX(x, z), this.transformY(y), this.transformZ(x, z));
        if (bounds.contains(blockpos)) {
            while (!world.isAir(blockpos) && blockpos.getY() < 255) {
                world.setBlockState(blockpos, Blocks.AIR.defaultState(), 2);
                blockpos = blockpos.up();
            }
        }
    }

    protected void fillColumnDown(World world, BlockState state, int x, int y, int z, StructureBox bounds) {
        int i = this.transformX(x, z);
        int j = this.transformY(y);
        int k = this.transformZ(x, z);
        if (bounds.contains(new BlockPos(i, j, k))) {
            while ((world.isAir(new BlockPos(i, j, k)) || world.getBlockState(new BlockPos(i, j, k)).getBlock().getMaterial().isLiquid()) && j > 1) {
                world.setBlockState(new BlockPos(i, j, k), state, 2);
                j--;
            }
        }
    }

    protected boolean placeChestWithLoot(World world, StructureBox bounds, Random random, int x, int y, int z, List<LootEntry> entries, int amount) {
        BlockPos blockpos = new BlockPos(this.transformX(x, z), this.transformY(y), this.transformZ(x, z));
        if (bounds.contains(blockpos) && world.getBlockState(blockpos).getBlock() != Blocks.CHEST) {
            BlockState blockstate = Blocks.CHEST.defaultState();
            world.setBlockState(blockpos, Blocks.CHEST.updateFacing(world, blockpos, blockstate), 2);
            BlockEntity blockentity = world.getBlockEntity(blockpos);
            if (blockentity instanceof ChestBlockEntity) {
                LootEntry.addLoot(random, entries, (ChestBlockEntity)blockentity, amount);
            }

            return true;
        } else {
            return false;
        }
    }

    protected boolean placeDispenserWithLoot(
        World world, StructureBox bounds, Random random, int x, int y, int z, int facing, List<LootEntry> entries, int amount
    ) {
        BlockPos blockpos = new BlockPos(this.transformX(x, z), this.transformY(y), this.transformZ(x, z));
        if (bounds.contains(blockpos) && world.getBlockState(blockpos).getBlock() != Blocks.DISPENSER) {
            world.setBlockState(blockpos, Blocks.DISPENSER.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.DISPENSER, facing)), 2);
            BlockEntity blockentity = world.getBlockEntity(blockpos);
            if (blockentity instanceof DispenserBlockEntity) {
                LootEntry.addLoot(random, entries, (DispenserBlockEntity)blockentity, amount);
            }

            return true;
        } else {
            return false;
        }
    }

    protected void placeWoodenDoor(World world, StructureBox bounds, Random random, int x, int y, int z, Direction facing) {
        BlockPos blockpos = new BlockPos(this.transformX(x, z), this.transformY(y), this.transformZ(x, z));
        if (bounds.contains(blockpos)) {
            DoorItem.place(world, blockpos, facing.counterClockwiseY(), Blocks.WOODEN_DOOR);
        }
    }

    public void move(int dx, int dy, int dz) {
        this.bounds.move(dx, dy, dz);
    }

    public abstract static class BlockPicker {
        protected BlockState state = Blocks.AIR.defaultState();

        protected BlockPicker() {
        }

        public abstract void pick(Random randomm, int x, int y, int z, boolean isNonAir);

        public BlockState getBlockState() {
            return this.state;
        }
    }
}
