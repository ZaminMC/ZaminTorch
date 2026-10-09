package net.minecraft.block;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class WeightedPressurePlateBlock extends AbstractPressurePlateBlock {
    public static final IntegerProperty POWER = IntegerProperty.of("power", 0, 15);
    private final int weight;

    protected WeightedPressurePlateBlock(Material material, int weight) {
        this(material, weight, material.getColor());
    }

    protected WeightedPressurePlateBlock(Material material, int weight, MapColor color) {
        super(material, color);
        this.setDefaultState(this.stateDefinition.any().set(POWER, 0));
        this.weight = weight;
    }

    @Override
    protected int calculateOutputSignal(World world, BlockPos pos) {
        int i = Math.min(world.getEntitiesOfType(Entity.class, this.getActivationBounds(pos)).size(), this.weight);
        if (i > 0) {
            float f = (float)Math.min(this.weight, i) / this.weight;
            return MathHelper.ceil(f * 15.0F);
        } else {
            return 0;
        }
    }

    @Override
    protected int getOutputSignal(BlockState state) {
        return state.get(POWER);
    }

    @Override
    protected BlockState setOutputSignal(BlockState state, int signal) {
        return state.set(POWER, signal);
    }

    @Override
    public int getTickRate(World world) {
        return 10;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(POWER, metadata);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(POWER);
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, POWER);
    }
}
