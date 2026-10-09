package net.minecraft.entity.living.mob.monster;

import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class GiantEntity extends MonsterEntity {
    public GiantEntity(World world) {
        super(world);
        this.setSize(this.width * 6.0F, this.height * 6.0F);
    }

    @Override
    public float getEyeHeight() {
        return 10.440001F;
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(100.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.5);
        this.getAttribute(EntityAttributes.ATTACK_DAMAGE).setBase(50.0);
    }

    @Override
    public float getPathfindingFavor(BlockPos pos) {
        return this.world.getBrightness(pos) - 0.5F;
    }
}
