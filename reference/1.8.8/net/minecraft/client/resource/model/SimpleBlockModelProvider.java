package net.minecraft.client.resource.model;

import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.resource.ModelIdentifier;

public class SimpleBlockModelProvider extends AbstractBlockModelProvider {
    @Override
    protected ModelIdentifier provide(BlockState state) {
        return new ModelIdentifier(Block.REGISTRY.getKey(state.getBlock()), this.propertiesAsString(state.values()));
    }
}
