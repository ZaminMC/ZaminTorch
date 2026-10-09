package net.minecraft.client.resource.model;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.property.Property;
import net.minecraft.client.resource.ModelIdentifier;

public abstract class AbstractBlockModelProvider implements BlockModelProvider {
    protected Map<BlockState, ModelIdentifier> models = Maps.newLinkedHashMap();

    public String propertiesAsString(Map<Property, Comparable> properties) {
        StringBuilder stringbuilder = new StringBuilder();

        for (Entry<Property, Comparable> entry : properties.entrySet()) {
            if (stringbuilder.length() != 0) {
                stringbuilder.append(",");
            }

            Property property = entry.getKey();
            Comparable comparable = entry.getValue();
            stringbuilder.append(property.getName());
            stringbuilder.append("=");
            stringbuilder.append(property.getName(comparable));
        }

        if (stringbuilder.length() == 0) {
            stringbuilder.append("normal");
        }

        return stringbuilder.toString();
    }

    @Override
    public Map<BlockState, ModelIdentifier> provide(Block block) {
        for (BlockState blockstate : block.stateDefinition().all()) {
            this.models.put(blockstate, this.provide(blockstate));
        }

        return this.models;
    }

    protected abstract ModelIdentifier provide(BlockState state);
}
