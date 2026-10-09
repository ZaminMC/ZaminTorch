package net.minecraft.entity.living.mob.passive.animal;

import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.living.mob.passive.PassiveEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;

public class MooshroomEntity extends CowEntity {
    public MooshroomEntity(World world) {
        super(world);
        this.setSize(0.9F, 1.3F);
        this.spawnableBlock = Blocks.MYCELIUM;
    }

    @Override
    public boolean interactMob(PlayerEntity player) {
        ItemStack itemstack = player.inventory.getSelectedItem();
        if (itemstack != null && itemstack.getItem() == Items.BOWL && this.getBreedingAge() >= 0) {
            if (itemstack.size == 1) {
                player.inventory.setItem(player.inventory.selectedSlot, new ItemStack(Items.MUSHROOM_STEW));
                return true;
            }

            if (player.inventory.addItem(new ItemStack(Items.MUSHROOM_STEW)) && !player.abilities.creativeMode) {
                player.inventory.removeItem(player.inventory.selectedSlot, 1);
                return true;
            }
        }

        if (itemstack != null && itemstack.getItem() == Items.SHEARS && this.getBreedingAge() >= 0) {
            this.remove();
            this.world.addParticle(ParticleType.EXPLOSION_LARGE, this.x, this.y + this.height / 2.0F, this.z, 0.0, 0.0, 0.0);
            if (!this.world.isClient) {
                CowEntity cowentity = new CowEntity(this.world);
                cowentity.setPositionAndAngles(this.x, this.y, this.z, this.yaw, this.pitch);
                cowentity.setHealth(this.getHealth());
                cowentity.bodyYaw = this.bodyYaw;
                if (this.hasCustomName()) {
                    cowentity.setCustomName(this.getCustomName());
                }

                this.world.addEntity(cowentity);

                for (int i = 0; i < 5; i++) {
                    this.world.addEntity(new ItemEntity(this.world, this.x, this.y + this.height, this.z, new ItemStack(Blocks.RED_MUSHROOM)));
                }

                itemstack.takeDamageAndBreak(1, player);
                this.playSound("mob.sheep.shear", 1.0F, 1.0F);
            }

            return true;
        } else {
            return super.interactMob(player);
        }
    }

    public MooshroomEntity makeChild(PassiveEntity passiveEntity) {
        return new MooshroomEntity(this.world);
    }
}
