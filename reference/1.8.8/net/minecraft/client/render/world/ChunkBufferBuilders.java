package net.minecraft.client.render.world;

import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.vertex.BufferBuilder;

public class ChunkBufferBuilders {
    private final BufferBuilder[] builders = new BufferBuilder[BlockLayer.values().length];

    public ChunkBufferBuilders() {
        this.builders[BlockLayer.SOLID.ordinal()] = new BufferBuilder(2097152);
        this.builders[BlockLayer.CUTOUT.ordinal()] = new BufferBuilder(131072);
        this.builders[BlockLayer.CUTOUT_MIPPED.ordinal()] = new BufferBuilder(131072);
        this.builders[BlockLayer.TRANSLUCENT.ordinal()] = new BufferBuilder(262144);
    }

    public BufferBuilder get(BlockLayer layer) {
        return this.builders[layer.ordinal()];
    }

    public BufferBuilder get(int layer) {
        return this.builders[layer];
    }
}
