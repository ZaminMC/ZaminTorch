package net.minecraft.entity.living.attribute;

import io.netty.util.internal.ThreadLocalRandom;
import java.util.UUID;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.Validate;

public class AttributeModifier {
    private final double value;
    private final int operation;
    private final String name;
    private final UUID id;
    private boolean serialized = true;

    public AttributeModifier(String name, double value, int operation) {
        this(MathHelper.nextUuid(ThreadLocalRandom.current()), name, value, operation);
    }

    public AttributeModifier(UUID id, String name, double value, int operation) {
        this.id = id;
        this.name = name;
        this.value = value;
        this.operation = operation;
        Validate.notEmpty(name, "Modifier name cannot be empty");
        Validate.inclusiveBetween(0L, 2L, operation, "Invalid operation");
    }

    public UUID getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public int getOperation() {
        return this.operation;
    }

    public double get() {
        return this.value;
    }

    public boolean isSerialized() {
        return this.serialized;
    }

    public AttributeModifier setSerialized(boolean serialized) {
        this.serialized = serialized;
        return this;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object != null && this.getClass() == object.getClass()) {
            AttributeModifier attributemodifier = (AttributeModifier)object;
            return this.id != null ? this.id.equals(attributemodifier.id) : attributemodifier.id == null;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.id != null ? this.id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "AttributeModifier{amount="
            + this.value
            + ", operation="
            + this.operation
            + ", name='"
            + this.name
            + '\''
            + ", id="
            + this.id
            + ", serialize="
            + this.serialized
            + '}';
    }
}
