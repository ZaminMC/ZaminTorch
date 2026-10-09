package net.minecraft.entity.living.mob.passive.animal;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.SpawnableEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.mob.passive.PassiveEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public abstract class AnimalEntity extends PassiveEntity implements SpawnableEntity {
    protected Block spawnableBlock = Blocks.GRASS;
    private int inLoveTimer;
    private PlayerEntity loveCausingPlayer;

    public AnimalEntity(World world) {
        super(world);
    }

    @Override
    protected void mobAiTick() {
        if (this.getBreedingAge() != 0) {
            this.inLoveTimer = 0;
        }

        super.mobAiTick();
    }

    @Override
    public void mobTick() {
        super.mobTick();
        if (this.getBreedingAge() != 0) {
            this.inLoveTimer = 0;
        }

        if (this.inLoveTimer > 0) {
            this.inLoveTimer--;
            if (this.inLoveTimer % 10 == 0) {
                double d0 = this.random.nextGaussian() * 0.02;
                double d1 = this.random.nextGaussian() * 0.02;
                double d2 = this.random.nextGaussian() * 0.02;
                this.world
                    .addParticle(
                        ParticleType.HEART,
                        this.x + this.random.nextFloat() * this.width * 2.0F - this.width,
                        this.y + 0.5 + this.random.nextFloat() * this.height,
                        this.z + this.random.nextFloat() * this.width * 2.0F - this.width,
                        d0,
                        d1,
                        d2
                    );
            }
        }
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        this.inLoveTimer = 0;
        return super.takeDamage(source, amount);
    }

    @Override
    public float getPathfindingFavor(BlockPos pos) {
        return this.world.getBlockState(pos.down()).getBlock() == Blocks.GRASS ? 10.0F : this.world.getBrightness(pos) - 0.5F;
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putInt("InLove", this.inLoveTimer);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.inLoveTimer = nbt.getInt("InLove");
    }

    @Override
    public boolean canSpawn() {
        int i = MathHelper.floor(this.x);
        int j = MathHelper.floor(this.getShape().minY);
        int k = MathHelper.floor(this.z);
        BlockPos blockpos = new BlockPos(i, j, k);
        return this.world.getBlockState(blockpos.down()).getBlock() == this.spawnableBlock && this.world.getActualLight(blockpos) > 8 && super.canSpawn();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 120;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    protected int getXpDrop(PlayerEntity playerEntity) {
        return 1 + this.world.random.nextInt(3);
    }

    public boolean isBreedingItem(ItemStack item) {
        return item != null && item.getItem() == Items.WHEAT;
    }

    @Override
    public boolean interactMob(PlayerEntity player) {
        ItemStack itemstack = player.inventory.getSelectedItem();
        if (itemstack != null) {
            if (this.isBreedingItem(itemstack) && this.getBreedingAge() == 0 && this.inLoveTimer <= 0) {
                this.eat(player, itemstack);
                this.lovePlayer(player);
                return true;
            }

            if (this.isBaby() && this.isBreedingItem(itemstack)) {
                this.eat(player, itemstack);
                this.growUp((int)(-this.getBreedingAge() / 20 * 0.1F), true);
                return true;
            }
        }

        return super.interactMob(player);
    }

    protected void eat(PlayerEntity player, ItemStack item) {
        if (!player.abilities.creativeMode) {
            item.size--;
            if (item.size <= 0) {
                player.inventory.setItem(player.inventory.selectedSlot, null);
            }
        }
    }

    public void lovePlayer(PlayerEntity player) {
        this.inLoveTimer = 600;
        this.loveCausingPlayer = player;
        this.world.doEntityEvent(this, (byte)18);
    }

    public PlayerEntity getLoveCausingPlayer() {
        return this.loveCausingPlayer;
    }

    public boolean isInLove() {
        return this.inLoveTimer > 0;
    }

    public void resetLoveTicks() {
        this.inLoveTimer = 0;
    }

    public boolean canBreedWith(AnimalEntity other) {
        return other != this && other.getClass() == this.getClass() && this.isInLove() && other.isInLove();
    }

    @Override
    public void doEvent(byte event) {
        if (event == 18) {
            for (int i = 0; i < 7; i++) {
                double d0 = this.random.nextGaussian() * 0.02;
                double d1 = this.random.nextGaussian() * 0.02;
                double d2 = this.random.nextGaussian() * 0.02;
                this.world
                    .addParticle(
                        ParticleType.HEART,
                        this.x + this.random.nextFloat() * this.width * 2.0F - this.width,
                        this.y + 0.5 + this.random.nextFloat() * this.height,
                        this.z + this.random.nextFloat() * this.width * 2.0F - this.width,
                        d0,
                        d1,
                        d2
                    );
            }
        } else {
            super.doEvent(event);
        }
    }
}
