package net.minecraft.entity.living.attribute;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.util.LowercaseMap;

public abstract class AbstractEntityAttributeContainer {
    protected final Map<EntityAttribute, EntityAttributeInstance> byType = Maps.newHashMap();
    protected final Map<String, EntityAttributeInstance> byName = new LowercaseMap<>();
    protected final Multimap<EntityAttribute, EntityAttribute> byParent = HashMultimap.create();

    public EntityAttributeInstance get(EntityAttribute attribute) {
        return this.byType.get(attribute);
    }

    public EntityAttributeInstance get(String name) {
        return this.byName.get(name);
    }

    public EntityAttributeInstance register(EntityAttribute attribute) {
        if (this.byName.containsKey(attribute.getName())) {
            throw new IllegalArgumentException("Attribute is already registered!");
        }

        EntityAttributeInstance entityattributeinstance = this.newInstance(attribute);
        this.byName.put(attribute.getName(), entityattributeinstance);
        this.byType.put(attribute, entityattributeinstance);

        for (EntityAttribute entityattribute = attribute.getParent(); entityattribute != null; entityattribute = entityattribute.getParent()) {
            this.byParent.put(entityattribute, attribute);
        }

        return entityattributeinstance;
    }

    protected abstract EntityAttributeInstance newInstance(EntityAttribute attribute);

    public Collection<EntityAttributeInstance> getAll() {
        return this.byName.values();
    }

    public void track(EntityAttributeInstance instance) {
    }

    public void removeModifiers(Multimap<String, AttributeModifier> modifiers) {
        for (Entry<String, AttributeModifier> entry : modifiers.entries()) {
            EntityAttributeInstance entityattributeinstance = this.get(entry.getKey());
            if (entityattributeinstance != null) {
                entityattributeinstance.removeModifier(entry.getValue());
            }
        }
    }

    public void addModifiers(Multimap<String, AttributeModifier> modifiers) {
        for (Entry<String, AttributeModifier> entry : modifiers.entries()) {
            EntityAttributeInstance entityattributeinstance = this.get(entry.getKey());
            if (entityattributeinstance != null) {
                entityattributeinstance.removeModifier(entry.getValue());
                entityattributeinstance.addModifier(entry.getValue());
            }
        }
    }
}
