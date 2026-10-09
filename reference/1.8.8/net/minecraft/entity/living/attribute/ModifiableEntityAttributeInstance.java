package net.minecraft.entity.living.attribute;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ModifiableEntityAttributeInstance implements EntityAttributeInstance {
    private final AbstractEntityAttributeContainer container;
    private final EntityAttribute attribute;
    private final Map<Integer, Set<AttributeModifier>> modifiersByOperation = Maps.newHashMap();
    private final Map<String, Set<AttributeModifier>> modifiersByName = Maps.newHashMap();
    private final Map<UUID, AttributeModifier> modifiersById = Maps.newHashMap();
    private double base;
    private boolean dirty = true;
    private double value;

    public ModifiableEntityAttributeInstance(AbstractEntityAttributeContainer container, EntityAttribute attribute) {
        this.container = container;
        this.attribute = attribute;
        this.base = attribute.getDefault();

        for (int i = 0; i < 3; i++) {
            this.modifiersByOperation.put(i, Sets.newHashSet());
        }
    }

    @Override
    public EntityAttribute getAttribute() {
        return this.attribute;
    }

    @Override
    public double getBase() {
        return this.base;
    }

    @Override
    public void setBase(double base) {
        if (base != this.getBase()) {
            this.base = base;
            this.markDirty();
        }
    }

    @Override
    public Collection<AttributeModifier> getModifiers(int operation) {
        return this.modifiersByOperation.get(operation);
    }

    @Override
    public Collection<AttributeModifier> getModifiers() {
        Set<AttributeModifier> set = Sets.newHashSet();

        for (int i = 0; i < 3; i++) {
            set.addAll(this.getModifiers(i));
        }

        return set;
    }

    @Override
    public AttributeModifier getModifier(UUID id) {
        return this.modifiersById.get(id);
    }

    @Override
    public boolean hasModifier(AttributeModifier modifier) {
        return this.modifiersById.get(modifier.getId()) != null;
    }

    @Override
    public void addModifier(AttributeModifier modifier) {
        if (this.getModifier(modifier.getId()) != null) {
            throw new IllegalArgumentException("Modifier is already applied on this attribute!");
        }

        Set<AttributeModifier> set = this.modifiersByName.get(modifier.getName());
        if (set == null) {
            set = Sets.newHashSet();
            this.modifiersByName.put(modifier.getName(), set);
        }

        this.modifiersByOperation.get(modifier.getOperation()).add(modifier);
        set.add(modifier);
        this.modifiersById.put(modifier.getId(), modifier);
        this.markDirty();
    }

    protected void markDirty() {
        this.dirty = true;
        this.container.track(this);
    }

    @Override
    public void removeModifier(AttributeModifier modifier) {
        for (int i = 0; i < 3; i++) {
            Set<AttributeModifier> set = this.modifiersByOperation.get(i);
            set.remove(modifier);
        }

        Set<AttributeModifier> set1 = this.modifiersByName.get(modifier.getName());
        if (set1 != null) {
            set1.remove(modifier);
            if (set1.isEmpty()) {
                this.modifiersByName.remove(modifier.getName());
            }
        }

        this.modifiersById.remove(modifier.getId());
        this.markDirty();
    }

    @Override
    public void clearModifiers() {
        Collection<AttributeModifier> collection = this.getModifiers();
        if (collection != null) {
            for (AttributeModifier attributemodifier : Lists.newArrayList(collection)) {
                this.removeModifier(attributemodifier);
            }
        }
    }

    @Override
    public double get() {
        if (this.dirty) {
            this.value = this.computeValue();
            this.dirty = false;
        }

        return this.value;
    }

    private double computeValue() {
        double d0 = this.getBase();

        for (AttributeModifier attributemodifier : this.getAppliedModifiers(0)) {
            d0 += attributemodifier.get();
        }

        double d1 = d0;

        for (AttributeModifier attributemodifier1 : this.getAppliedModifiers(1)) {
            d1 += d0 * attributemodifier1.get();
        }

        for (AttributeModifier attributemodifier2 : this.getAppliedModifiers(2)) {
            d1 *= 1.0 + attributemodifier2.get();
        }

        return this.attribute.clamp(d1);
    }

    private Collection<AttributeModifier> getAppliedModifiers(int operation) {
        Set<AttributeModifier> set = Sets.newHashSet(this.getModifiers(operation));

        for (EntityAttribute entityattribute = this.attribute.getParent(); entityattribute != null; entityattribute = entityattribute.getParent()) {
            EntityAttributeInstance entityattributeinstance = this.container.get(entityattribute);
            if (entityattributeinstance != null) {
                set.addAll(entityattributeinstance.getModifiers(operation));
            }
        }

        return set;
    }
}
