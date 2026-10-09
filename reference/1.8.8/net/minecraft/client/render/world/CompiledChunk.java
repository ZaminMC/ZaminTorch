package net.minecraft.client.render.world;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.util.math.Direction;

public class CompiledChunk {
    public static final CompiledChunk UNCOMPILED = new CompiledChunk() {
        @Override
        protected void setBlock(BlockLayer layer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLayer(BlockLayer layer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isVisible(Direction from, Direction to) {
            return false;
        }
    };
    private final boolean[] blocks = new boolean[BlockLayer.values().length];
    private final boolean[] layers = new boolean[BlockLayer.values().length];
    private boolean empty = true;
    private final List<BlockEntity> renderableBlockEntities = Lists.newArrayList();
    private OcclusionData occlusion = new OcclusionData();
    private BufferBuilder.State transparencyState;

    public boolean isEmpty() {
        return this.empty;
    }

    protected void setBlock(BlockLayer layer) {
        this.empty = false;
        this.blocks[layer.ordinal()] = true;
    }

    public boolean hasBlock(BlockLayer layer) {
        return !this.blocks[layer.ordinal()];
    }

    public void setLayer(BlockLayer layer) {
        this.layers[layer.ordinal()] = true;
    }

    public boolean hasLayer(BlockLayer layer) {
        return this.layers[layer.ordinal()];
    }

    public List<BlockEntity> getRenderableBlockEntities() {
        return this.renderableBlockEntities;
    }

    public void addRenderableBlockEntity(BlockEntity blockEntity) {
        this.renderableBlockEntities.add(blockEntity);
    }

    public boolean isVisible(Direction from, Direction to) {
        return this.occlusion.isVisible(from, to);
    }

    public void setOcclusionData(OcclusionData occlusion) {
        this.occlusion = occlusion;
    }

    public BufferBuilder.State getTransparencyState() {
        return this.transparencyState;
    }

    public void setTransparencyState(BufferBuilder.State state) {
        this.transparencyState = state;
    }
}
