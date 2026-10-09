package net.minecraft.entity.living.attribute;

import java.util.Collection;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EntityAttributes {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final EntityAttribute MAX_HEALTH = new RangedEntityAttribute(null, "generic.maxHealth", 20.0, 0.0, 1024.0)
        .setDisplayName("Max Health")
        .setTrackable(true);
    public static final EntityAttribute FOLLOW_RANGE = new RangedEntityAttribute(null, "generic.followRange", 32.0, 0.0, 2048.0).setDisplayName("Follow Range");
    public static final EntityAttribute KNOCKBACK_RESISTANCE = new RangedEntityAttribute(null, "generic.knockbackResistance", 0.0, 0.0, 1.0)
        .setDisplayName("Knockback Resistance");
    public static final EntityAttribute MOVEMENT_SPEED = new RangedEntityAttribute(null, "generic.movementSpeed", 0.7F, 0.0, 1024.0)
        .setDisplayName("Movement Speed")
        .setTrackable(true);
    public static final EntityAttribute ATTACK_DAMAGE = new RangedEntityAttribute(null, "generic.attackDamage", 2.0, 0.0, 2048.0);

    public static NbtList toNbt(AbstractEntityAttributeContainer container) {
        NbtList nbtlist = new NbtList();

        for (EntityAttributeInstance entityattributeinstance : container.getAll()) {
            nbtlist.addElement(toNbt(entityattributeinstance));
        }

        return nbtlist;
    }

    private static NbtCompound toNbt(EntityAttributeInstance instance) {
        NbtCompound nbtcompound = new NbtCompound();
        EntityAttribute entityattribute = instance.getAttribute();
        nbtcompound.putString("Name", entityattribute.getName());
        nbtcompound.putDouble("Base", instance.getBase());
        Collection<AttributeModifier> collection = instance.getModifiers();
        if (collection != null && !collection.isEmpty()) {
            NbtList nbtlist = new NbtList();

            for (AttributeModifier attributemodifier : collection) {
                if (attributemodifier.isSerialized()) {
                    nbtlist.addElement(toNbt(attributemodifier));
                }
            }

            nbtcompound.put("Modifiers", nbtlist);
        }

        return nbtcompound;
    }

    private static NbtCompound toNbt(AttributeModifier modifier) {
        NbtCompound nbtcompound = new NbtCompound();
        nbtcompound.putString("Name", modifier.getName());
        nbtcompound.putDouble("Amount", modifier.get());
        nbtcompound.putInt("Operation", modifier.getOperation());
        nbtcompound.putLong("UUIDMost", modifier.getId().getMostSignificantBits());
        nbtcompound.putLong("UUIDLeast", modifier.getId().getLeastSignificantBits());
        return nbtcompound;
    }

    public static void readNbt(AbstractEntityAttributeContainer container, NbtList nbt) {
        for (int i = 0; i < nbt.size(); i++) {
            NbtCompound nbtcompound = nbt.getCompound(i);
            EntityAttributeInstance entityattributeinstance = container.get(nbtcompound.getString("Name"));
            if (entityattributeinstance != null) {
                readNbt(entityattributeinstance, nbtcompound);
            } else {
                LOGGER.warn("Ignoring unknown attribute '" + nbtcompound.getString("Name") + "'");
            }
        }
    }

    private static void readNbt(EntityAttributeInstance instance, NbtCompound nbt) {
        instance.setBase(nbt.getDouble("Base"));
        if (nbt.contains("Modifiers", 9)) {
            NbtList nbtlist = nbt.getList("Modifiers", 10);

            for (int i = 0; i < nbtlist.size(); i++) {
                AttributeModifier attributemodifier = fromNbt(nbtlist.getCompound(i));
                if (attributemodifier != null) {
                    AttributeModifier attributemodifier1 = instance.getModifier(attributemodifier.getId());
                    if (attributemodifier1 != null) {
                        instance.removeModifier(attributemodifier1);
                    }

                    instance.addModifier(attributemodifier);
                }
            }
        }
    }

    public static AttributeModifier fromNbt(NbtCompound nbt) {
        UUID uuid = new UUID(nbt.getLong("UUIDMost"), nbt.getLong("UUIDLeast"));

        try {
            return new AttributeModifier(uuid, nbt.getString("Name"), nbt.getDouble("Amount"), nbt.getInt("Operation"));
        } catch (Exception exception) {
            LOGGER.warn("Unable to create attribute: " + exception.getMessage());
            return null;
        }
    }
}
