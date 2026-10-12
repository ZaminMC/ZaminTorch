package net.minecraft.world.chunk;

import com.google.common.base.Predicate;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

public class EmptyChunk extends WorldChunk {
    public EmptyChunk(World world, int chunkX, int chunkZ) {
        super(world, chunkX, chunkZ);
    }

    @Override
    public boolean isAt(int chunkX, int chunkZ) {
        return chunkX == this.chunkX && chunkZ == this.chunkZ;
    }

    @Override
    public int getHeight(int localX, int localZ) {
        return 0;
    }

    @Override
    public void populateHeightMapOnly() {
    }

    @Override
    public void populateHeightMap() {
    }

    @Override
    public Block getBlock(BlockPos pos) {
        return Blocks.AIR;
    }

    @Override
    public int getOpacity(BlockPos pos) {
        return 255;
    }

    @Override
    public int getBlockMetadata(BlockPos pos) {
        return 0;
    }

    @Override
    public int getLight(LightType type, BlockPos pos) {
        return type.defaultValue;
    }

    @Override
    public void setLight(LightType type, BlockPos pos, int light) {
    }

    @Override
    public int getLight(BlockPos pos, int ambientDarkness) {
        return 0;
    }

    @Override
    public void addEntity(Entity entity) {
    }

    @Override
    public void removeEntity(Entity entity) {
    }

    @Override
    public void removeEntity(Entity entity, int chunkY) {
    }

    @Override
    public boolean hasSkyAccess(BlockPos pos) {
        return false;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos, WorldChunk.BlockEntityCreationType creationType) {
        return null;
    }

    @Override
    public void addBlockEntity(BlockEntity blockEntity) {
    }

    @Override
    public void setBlockEntity(BlockPos pos, BlockEntity blockEntity) {
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
    }

    @Override
    public void load() {
    }

    @Override
    public void unload() {
    }

    @Override
    public void markDirty() {
    }

    @Override
    public void getEntities(Entity exclude, Box bounds, List<Entity> entities, Predicate<? super Entity> filter) {
    }

    @Override
    public <T extends Entity> void getEntitiesOfType(Class<? extends T> type, Box bounds, List<T> entities, Predicate<? super T> filter) {
    }

    @Override
    public boolean shouldSave(boolean saveEntities) {
        return false;
    }

    @Override
    public Random getRandomForSlime(long seed) {
        return new Random(
            this.getWorld().getSeed()
                    + this.chunkX * this.chunkX * 4987142
                    + this.chunkX * 5947611
                    + this.chunkZ * this.chunkZ * 4392871L
                    + this.chunkZ * 389711
                ^ seed
        );
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean isEmpty(int minY, int maxY) {
        return true;
    }
}
