package net.minecraft.entity.living.mob.passive.animal;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.crafting.CraftingManager;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.AnimalBreedGoal;
import net.minecraft.entity.ai.goal.EatGrassGoal;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.FollowParentGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.passive.PassiveEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;

public class SheepEntity extends AnimalEntity {
    private final CraftingInventory inventory = new CraftingInventory(new InventoryMenu() {
        @Override
        public boolean isValid(PlayerEntity player) {
            return false;
        }
    }, 2, 1);
    private static final Map<DyeColor, float[]> DYE_COLOR_TO_RGB = Maps.newEnumMap(DyeColor.class);
    private int eatGrassTimer;
    private EatGrassGoal eatGrassGoal = new EatGrassGoal(this);

    public static float[] getColorRgb(DyeColor color) {
        return DYE_COLOR_TO_RGB.get(color);
    }

    public SheepEntity(World world) {
        super(world);
        this.setSize(0.9F, 1.3F);
        ((GroundPathNavigation)this.getNavigation()).setCanSwim(true);
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(1, new EscapeDangerGoal(this, 1.25));
        this.goalSelector.addGoal(2, new AnimalBreedGoal(this, 1.0));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.1, Items.WHEAT, false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
        this.goalSelector.addGoal(5, this.eatGrassGoal);
        this.goalSelector.addGoal(6, new WanderAroundGoal(this, 1.0));
        this.goalSelector.addGoal(7, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.addGoal(8, new LookAroundGoal(this));
        this.inventory.setItem(0, new ItemStack(Items.DYE, 1, 0));
        this.inventory.setItem(1, new ItemStack(Items.DYE, 1, 0));
    }

    @Override
    protected void mobAiTick() {
        this.eatGrassTimer = this.eatGrassGoal.getTimer();
        super.mobAiTick();
    }

    @Override
    public void mobTick() {
        if (this.world.isClient) {
            this.eatGrassTimer = Math.max(0, this.eatGrassTimer - 1);
        }

        super.mobTick();
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(8.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.23F);
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, new Byte((byte)0));
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        if (!this.isSheared()) {
            this.dropItem(new ItemStack(Item.byBlock(Blocks.WOOL), 1, this.getColor().getId()), 0.0F);
        }

        int i = this.random.nextInt(2) + 1 + this.random.nextInt(1 + lootingMultiplier);

        for (int j = 0; j < i; j++) {
            if (this.isOnFire()) {
                this.dropItem(Items.COOKED_MUTTON, 1);
            } else {
                this.dropItem(Items.MUTTON, 1);
            }
        }
    }

    @Override
    protected Item getDropItem() {
        return Item.byBlock(Blocks.WOOL);
    }

    @Override
    public void doEvent(byte event) {
        if (event == 10) {
            this.eatGrassTimer = 40;
        } else {
            super.doEvent(event);
        }
    }

    public float getNeckAngle(float delta) {
        if (this.eatGrassTimer <= 0) {
            return 0.0F;
        } else if (this.eatGrassTimer >= 4 && this.eatGrassTimer <= 36) {
            return 1.0F;
        } else {
            return this.eatGrassTimer < 4 ? (this.eatGrassTimer - delta) / 4.0F : -(this.eatGrassTimer - 40 - delta) / 4.0F;
        }
    }

    public float getHeadAngle(float delta) {
        if (this.eatGrassTimer > 4 && this.eatGrassTimer <= 36) {
            float f = (this.eatGrassTimer - 4 - delta) / 32.0F;
            return (float) (Math.PI / 5) + 0.21991149F * MathHelper.sin(f * 28.7F);
        } else {
            return this.eatGrassTimer > 0 ? (float) (Math.PI / 5) : this.pitch / (180.0F / (float)Math.PI);
        }
    }

    @Override
    public boolean interactMob(PlayerEntity player) {
        ItemStack itemstack = player.inventory.getSelectedItem();
        if (itemstack != null && itemstack.getItem() == Items.SHEARS && !this.isSheared() && !this.isBaby()) {
            if (!this.world.isClient) {
                this.setSheared(true);
                int i = 1 + this.random.nextInt(3);

                for (int j = 0; j < i; j++) {
                    ItemEntity itementity = this.dropItem(new ItemStack(Item.byBlock(Blocks.WOOL), 1, this.getColor().getId()), 1.0F);
                    itementity.velocityY = itementity.velocityY + this.random.nextFloat() * 0.05F;
                    itementity.velocityX = itementity.velocityX + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F;
                    itementity.velocityZ = itementity.velocityZ + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F;
                }
            }

            itemstack.takeDamageAndBreak(1, player);
            this.playSound("mob.sheep.shear", 1.0F, 1.0F);
        }

        return super.interactMob(player);
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putBoolean("Sheared", this.isSheared());
        nbt.putByte("Color", (byte)this.getColor().getId());
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.setSheared(nbt.getBoolean("Sheared"));
        this.setColor(DyeColor.byId(nbt.getByte("Color")));
    }

    @Override
    protected String getAmbientSound() {
        return "mob.sheep.say";
    }

    @Override
    protected String getHurtSound() {
        return "mob.sheep.say";
    }

    @Override
    protected String getDeathSound() {
        return "mob.sheep.say";
    }

    @Override
    protected void playStepSound(BlockPos pos, Block block) {
        this.playSound("mob.sheep.step", 0.15F, 1.0F);
    }

    public DyeColor getColor() {
        return DyeColor.byId(this.syncedData.getByte(16) & 15);
    }

    public void setColor(DyeColor id) {
        byte b0 = this.syncedData.getByte(16);
        this.syncedData.update(16, (byte)(b0 & 240 | id.getId() & 15));
    }

    public boolean isSheared() {
        return (this.syncedData.getByte(16) & 16) != 0;
    }

    public void setSheared(boolean sheared) {
        byte b0 = this.syncedData.getByte(16);
        if (sheared) {
            this.syncedData.update(16, (byte)(b0 | 16));
        } else {
            this.syncedData.update(16, (byte)(b0 & -17));
        }
    }

    public static DyeColor pickColor(Random random) {
        int i = random.nextInt(100);
        if (i < 5) {
            return DyeColor.BLACK;
        } else if (i < 10) {
            return DyeColor.GRAY;
        } else if (i < 15) {
            return DyeColor.SILVER;
        } else if (i < 18) {
            return DyeColor.BROWN;
        } else {
            return random.nextInt(500) == 0 ? DyeColor.PINK : DyeColor.WHITE;
        }
    }

    public SheepEntity makeChild(PassiveEntity passiveEntity) {
        SheepEntity sheepentity = (SheepEntity)passiveEntity;
        SheepEntity sheepentity1 = new SheepEntity(this.world);
        sheepentity1.setColor(this.getColorForOffspring(this, sheepentity));
        return sheepentity1;
    }

    @Override
    public void onEatingGrass() {
        this.setSheared(false);
        if (this.isBaby()) {
            this.growUp(60);
        }
    }

    @Override
    public EntityData initialize(LocalDifficulty localDifficulty, EntityData data) {
        data = super.initialize(localDifficulty, data);
        this.setColor(pickColor(this.world.random));
        return data;
    }

    private DyeColor getColorForOffspring(AnimalEntity parent1, AnimalEntity parent2) {
        int i = ((SheepEntity)parent1).getColor().getMetadata();
        int j = ((SheepEntity)parent2).getColor().getMetadata();
        this.inventory.getItem(0).setDamage(i);
        this.inventory.getItem(1).setDamage(j);
        ItemStack itemstack = CraftingManager.getInstance().getResult(this.inventory, ((SheepEntity)parent1).world);
        int k;
        if (itemstack != null && itemstack.getItem() == Items.DYE) {
            k = itemstack.getMetadata();
        } else {
            k = this.world.random.nextBoolean() ? i : j;
        }

        return DyeColor.byMetadata(k);
    }

    @Override
    public float getEyeHeight() {
        return 0.95F * this.height;
    }

    static {
        DYE_COLOR_TO_RGB.put(DyeColor.WHITE, new float[]{1.0F, 1.0F, 1.0F});
        DYE_COLOR_TO_RGB.put(DyeColor.ORANGE, new float[]{0.85F, 0.5F, 0.2F});
        DYE_COLOR_TO_RGB.put(DyeColor.MAGENTA, new float[]{0.7F, 0.3F, 0.85F});
        DYE_COLOR_TO_RGB.put(DyeColor.LIGHT_BLUE, new float[]{0.4F, 0.6F, 0.85F});
        DYE_COLOR_TO_RGB.put(DyeColor.YELLOW, new float[]{0.9F, 0.9F, 0.2F});
        DYE_COLOR_TO_RGB.put(DyeColor.LIME, new float[]{0.5F, 0.8F, 0.1F});
        DYE_COLOR_TO_RGB.put(DyeColor.PINK, new float[]{0.95F, 0.5F, 0.65F});
        DYE_COLOR_TO_RGB.put(DyeColor.GRAY, new float[]{0.3F, 0.3F, 0.3F});
        DYE_COLOR_TO_RGB.put(DyeColor.SILVER, new float[]{0.6F, 0.6F, 0.6F});
        DYE_COLOR_TO_RGB.put(DyeColor.CYAN, new float[]{0.3F, 0.5F, 0.6F});
        DYE_COLOR_TO_RGB.put(DyeColor.PURPLE, new float[]{0.5F, 0.25F, 0.7F});
        DYE_COLOR_TO_RGB.put(DyeColor.BLUE, new float[]{0.2F, 0.3F, 0.7F});
        DYE_COLOR_TO_RGB.put(DyeColor.BROWN, new float[]{0.4F, 0.3F, 0.2F});
        DYE_COLOR_TO_RGB.put(DyeColor.GREEN, new float[]{0.4F, 0.5F, 0.2F});
        DYE_COLOR_TO_RGB.put(DyeColor.RED, new float[]{0.6F, 0.2F, 0.2F});
        DYE_COLOR_TO_RGB.put(DyeColor.BLACK, new float[]{0.1F, 0.1F, 0.1F});
    }
}
