package net.minecraft.block;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.util.math.Direction;

public abstract class AxisBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = EnumProperty.of("axis", Direction.Axis.class);

    protected AxisBlock(Material material) {
        super(material, material.getColor());
    }

    protected AxisBlock(Material material, MapColor mapColor) {
        super(material, mapColor);
    }
}
