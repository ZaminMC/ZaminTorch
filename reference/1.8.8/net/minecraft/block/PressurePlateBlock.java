package net.minecraft.block;

import java.util.List;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public class PressurePlateBlock extends AbstractPressurePlateBlock {
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    private final PressurePlateBlock.ActivationRule rule;

    protected PressurePlateBlock(Material material, PressurePlateBlock.ActivationRule rule) {
        super(material);
        this.setDefaultState(this.stateDefinition.any().set(POWERED, false));
        this.rule = rule;
    }

    @Override
    protected int getOutputSignal(BlockState state) {
        return state.get(POWERED) ? 15 : 0;
    }

    @Override
    protected BlockState setOutputSignal(BlockState state, int signal) {
        return state.set(POWERED, signal > 0);
    }

    @Override
    protected int calculateOutputSignal(World world, BlockPos pos) {
        Box box = this.getActivationBounds(pos);
        List<? extends Entity> list;
        switch (this.rule) {
            case EVERYTHING:
                list = world.getEntities(null, box);
                break;
            case MOBS:
                list = world.getEntitiesOfType(LivingEntity.class, box);
                break;
            default:
                return 0;
        }

        if (!list.isEmpty()) {
            for (Entity entity : list) {
                if (!entity.canAvoidTraps()) {
                    return 15;
                }
            }
        }

        return 0;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(POWERED, metadata == 1);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(POWERED) ? 1 : 0;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, POWERED);
    }

    public enum ActivationRule {
        EVERYTHING,
        MOBS;
    }
}
