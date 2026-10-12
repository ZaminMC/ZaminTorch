package net.minecraft.client.resource.model;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.property.Property;
import net.minecraft.client.resource.ModelIdentifier;

public class VariantBlockModelProvider extends AbstractBlockModelProvider {
    private final Property<?> property;
    private final String variant;
    private final List<Property<?>> unusedProperties;

    private VariantBlockModelProvider(Property<?> property, String variant, List<Property<?>> unusedProperties) {
        this.property = property;
        this.variant = variant;
        this.unusedProperties = unusedProperties;
    }

    @Override
    protected ModelIdentifier provide(BlockState state) {
        Map<Property, Comparable> map = Maps.newLinkedHashMap(state.values());
        String s;
        if (this.property == null) {
            s = Block.REGISTRY.getKey(state.getBlock()).toString();
        } else {
            s = ((Property<Comparable>)this.property).getName(map.remove(this.property));
        }

        if (this.variant != null) {
            s = s + this.variant;
        }

        for (Property<?> property : this.unusedProperties) {
            map.remove(property);
        }

        return new ModelIdentifier(s, this.propertiesAsString(map));
    }

    public static class Builder {
        private Property<?> property;
        private String variant;
        private final List<Property<?>> unusedProperties = Lists.newArrayList();

        public VariantBlockModelProvider.Builder setProperty(Property<?> property) {
            this.property = property;
            return this;
        }

        public VariantBlockModelProvider.Builder setVariant(String variant) {
            this.variant = variant;
            return this;
        }

        public VariantBlockModelProvider.Builder setUnusedProperties(Property<?>... properties) {
            Collections.addAll(this.unusedProperties, properties);
            return this;
        }

        public VariantBlockModelProvider build() {
            return new VariantBlockModelProvider(this.property, this.variant, this.unusedProperties);
        }
    }
}
