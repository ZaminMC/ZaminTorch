package net.minecraft.client.resource.model;

import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.resource.ModelIdentifier;

public interface BlockModelProvider {
    Map<BlockState, ModelIdentifier> provide(Block block);
}
