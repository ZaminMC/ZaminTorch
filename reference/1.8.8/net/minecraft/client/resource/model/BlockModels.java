package net.minecraft.client.resource.model;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.resource.ModelIdentifier;

public class BlockModels {
    private Map<Block, BlockModelProvider> providers = Maps.newIdentityHashMap();
    private Set<Block> custom = Sets.newIdentityHashSet();

    public void register(Block block, BlockModelProvider provider) {
        this.providers.put(block, provider);
    }

    public void register(Block... blocks) {
        Collections.addAll(this.custom, blocks);
    }

    public Map<BlockState, ModelIdentifier> provide() {
        Map<BlockState, ModelIdentifier> map = Maps.newIdentityHashMap();

        for (Block block : Block.REGISTRY) {
            if (!this.custom.contains(block)) {
                map.putAll(Objects.firstNonNull(this.providers.get(block), new SimpleBlockModelProvider()).provide(block));
            }
        }

        return map;
    }
}
