package net.minecraft.entity.living.attribute;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.LowercaseMap;

public class EntityAttributeContainer extends AbstractEntityAttributeContainer {
    private final Set<EntityAttributeInstance> tracked = Sets.newHashSet();
    protected final Map<String, EntityAttributeInstance> byName = new LowercaseMap<>();

    public ModifiableEntityAttributeInstance get(EntityAttribute entityAttribute) {
        return (ModifiableEntityAttributeInstance)super.get(entityAttribute);
    }

    public ModifiableEntityAttributeInstance get(String string) {
        EntityAttributeInstance entityattributeinstance = super.get(string);
        if (entityattributeinstance == null) {
            entityattributeinstance = this.byName.get(string);
        }

        return (ModifiableEntityAttributeInstance)entityattributeinstance;
    }

    @Override
    public EntityAttributeInstance register(EntityAttribute attribute) {
        EntityAttributeInstance entityattributeinstance = super.register(attribute);
        if (attribute instanceof RangedEntityAttribute && ((RangedEntityAttribute)attribute).getDisplayName() != null) {
            this.byName.put(((RangedEntityAttribute)attribute).getDisplayName(), entityattributeinstance);
        }

        return entityattributeinstance;
    }

    @Override
    protected EntityAttributeInstance newInstance(EntityAttribute attribute) {
        return new ModifiableEntityAttributeInstance(this, attribute);
    }

    @Override
    public void track(EntityAttributeInstance instance) {
        if (instance.getAttribute().isTrackable()) {
            this.tracked.add(instance);
        }

        for (EntityAttribute entityattribute : this.byParent.get(instance.getAttribute())) {
            ModifiableEntityAttributeInstance modifiableentityattributeinstance = this.get(entityattribute);
            if (modifiableentityattributeinstance != null) {
                modifiableentityattributeinstance.markDirty();
            }
        }
    }

    public Set<EntityAttributeInstance> getTracked() {
        return this.tracked;
    }

    public Collection<EntityAttributeInstance> getTrackable() {
        Set<EntityAttributeInstance> set = Sets.newHashSet();

        for (EntityAttributeInstance entityattributeinstance : this.getAll()) {
            if (entityattributeinstance.getAttribute().isTrackable()) {
                set.add(entityattributeinstance);
            }
        }

        return set;
    }
}
