package net.minecraft.entity.living;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Rotation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class ArmorStandEntity extends LivingEntity {
    private static final Rotation DEFAULT_HEAD_ROTATION = new Rotation(0.0F, 0.0F, 0.0F);
    private static final Rotation DEFAULT_BODY_ROTATION = new Rotation(0.0F, 0.0F, 0.0F);
    private static final Rotation DEFAULT_LEFT_ARM_ROTATION = new Rotation(-10.0F, 0.0F, -10.0F);
    private static final Rotation DEFAULT_RIGHT_ARM_ROTATION = new Rotation(-15.0F, 0.0F, 10.0F);
    private static final Rotation DEFAULT_LEFT_LEG_ROTATION = new Rotation(-1.0F, 0.0F, -1.0F);
    private static final Rotation DEFAULT_RIGHT_LEG_ROTATION = new Rotation(1.0F, 0.0F, 1.0F);
    private final ItemStack[] equipment = new ItemStack[5];
    private boolean invisible;
    private long lastDamageTime;
    private int getDisabledSlots;
    private boolean marker;
    private Rotation headRotation = DEFAULT_HEAD_ROTATION;
    private Rotation bodyRotation = DEFAULT_BODY_ROTATION;
    private Rotation leftArmRotation = DEFAULT_LEFT_ARM_ROTATION;
    private Rotation rightArmRotation = DEFAULT_RIGHT_ARM_ROTATION;
    private Rotation leftLegRotation = DEFAULT_LEFT_LEG_ROTATION;
    private Rotation rightLegRotation = DEFAULT_RIGHT_LEG_ROTATION;

    public ArmorStandEntity(World world) {
        super(world);
        this.setSilent(true);
        this.noClip = this.isNoGravity();
        this.setSize(0.5F, 1.975F);
    }

    public ArmorStandEntity(World world, double x, double y, double z) {
        this(world);
        this.setPosition(x, y, z);
    }

    @Override
    public boolean isLocallyControlled() {
        return super.isLocallyControlled() && !this.isNoGravity();
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(10, (byte)0);
        this.syncedData.register(11, DEFAULT_HEAD_ROTATION);
        this.syncedData.register(12, DEFAULT_BODY_ROTATION);
        this.syncedData.register(13, DEFAULT_LEFT_ARM_ROTATION);
        this.syncedData.register(14, DEFAULT_RIGHT_ARM_ROTATION);
        this.syncedData.register(15, DEFAULT_LEFT_LEG_ROTATION);
        this.syncedData.register(16, DEFAULT_RIGHT_LEG_ROTATION);
    }

    @Override
    public ItemStack getDisplayItemInHand() {
        return this.equipment[0];
    }

    @Override
    public ItemStack getEquipment(int slot) {
        return this.equipment[slot];
    }

    @Override
    public ItemStack getArmor(int slot) {
        return this.equipment[slot + 1];
    }

    @Override
    public void setEquipment(int slot, ItemStack item) {
        this.equipment[slot] = item;
    }

    @Override
    public ItemStack[] getEquipment() {
        return this.equipment;
    }

    @Override
    public boolean replaceItem(int slot, ItemStack item) {
        int i;
        if (slot == 99) {
            i = 0;
        } else {
            i = slot - 100 + 1;
            if (i < 0 || i >= this.equipment.length) {
                return false;
            }
        }

        if (item != null && MobEntity.getEquipmentSlot(item) != i && (i != 4 || !(item.getItem() instanceof BlockItem))) {
            return false;
        }

        this.setEquipment(i, item);
        return true;
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        NbtList nbtlist = new NbtList();

        for (int i = 0; i < this.equipment.length; i++) {
            NbtCompound nbtcompound = new NbtCompound();
            if (this.equipment[i] != null) {
                this.equipment[i].writeNbt(nbtcompound);
            }

            nbtlist.addElement(nbtcompound);
        }

        nbt.put("Equipment", nbtlist);
        if (this.isCustomNameVisible() && (this.getCustomName() == null || this.getCustomName().length() == 0)) {
            nbt.putBoolean("CustomNameVisible", this.isCustomNameVisible());
        }

        nbt.putBoolean("Invisible", this.isInvisible());
        nbt.putBoolean("Small", this.isSmall());
        nbt.putBoolean("ShowArms", this.isShowArms());
        nbt.putInt("DisabledSlots", this.getDisabledSlots);
        nbt.putBoolean("NoGravity", this.isNoGravity());
        nbt.putBoolean("NoBasePlate", this.isBasePlateVisible());
        if (this.isMarker()) {
            nbt.putBoolean("Marker", this.isMarker());
        }

        nbt.put("Pose", this.getPose());
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        if (nbt.contains("Equipment", 9)) {
            NbtList nbtlist = nbt.getList("Equipment", 10);

            for (int i = 0; i < this.equipment.length; i++) {
                this.equipment[i] = ItemStack.fromNbt(nbtlist.getCompound(i));
            }
        }

        this.setInvisible(nbt.getBoolean("Invisible"));
        this.setSmall(nbt.getBoolean("Small"));
        this.setShowArms(nbt.getBoolean("ShowArms"));
        this.getDisabledSlots = nbt.getInt("DisabledSlots");
        this.setNoGravity(nbt.getBoolean("NoGravity"));
        this.setArmsVisible(nbt.getBoolean("NoBasePlate"));
        this.setMarker(nbt.getBoolean("Marker"));
        this.marker = !this.isMarker();
        this.noClip = this.isNoGravity();
        NbtCompound nbtcompound = nbt.getCompound("Pose");
        this.setPose(nbtcompound);
    }

    private void setPose(NbtCompound nbt) {
        NbtList nbtlist = nbt.getList("Head", 5);
        if (nbtlist.size() > 0) {
            this.setHeadRotation(new Rotation(nbtlist));
        } else {
            this.setHeadRotation(DEFAULT_HEAD_ROTATION);
        }

        NbtList nbtlist1 = nbt.getList("Body", 5);
        if (nbtlist1.size() > 0) {
            this.setBodyRotation(new Rotation(nbtlist1));
        } else {
            this.setBodyRotation(DEFAULT_BODY_ROTATION);
        }

        NbtList nbtlist2 = nbt.getList("LeftArm", 5);
        if (nbtlist2.size() > 0) {
            this.setLeftArmRotation(new Rotation(nbtlist2));
        } else {
            this.setLeftArmRotation(DEFAULT_LEFT_ARM_ROTATION);
        }

        NbtList nbtlist3 = nbt.getList("RightArm", 5);
        if (nbtlist3.size() > 0) {
            this.setRightArmRotation(new Rotation(nbtlist3));
        } else {
            this.setRightArmRotation(DEFAULT_RIGHT_ARM_ROTATION);
        }

        NbtList nbtlist4 = nbt.getList("LeftLeg", 5);
        if (nbtlist4.size() > 0) {
            this.setLeftLegRotation(new Rotation(nbtlist4));
        } else {
            this.setLeftLegRotation(DEFAULT_LEFT_LEG_ROTATION);
        }

        NbtList nbtlist5 = nbt.getList("RightLeg", 5);
        if (nbtlist5.size() > 0) {
            this.setRightLegRotation(new Rotation(nbtlist5));
        } else {
            this.setRightLegRotation(DEFAULT_RIGHT_LEG_ROTATION);
        }
    }

    private NbtCompound getPose() {
        NbtCompound nbtcompound = new NbtCompound();
        if (!DEFAULT_HEAD_ROTATION.equals(this.headRotation)) {
            nbtcompound.put("Head", this.headRotation.toNbt());
        }

        if (!DEFAULT_BODY_ROTATION.equals(this.bodyRotation)) {
            nbtcompound.put("Body", this.bodyRotation.toNbt());
        }

        if (!DEFAULT_LEFT_ARM_ROTATION.equals(this.leftArmRotation)) {
            nbtcompound.put("LeftArm", this.leftArmRotation.toNbt());
        }

        if (!DEFAULT_RIGHT_ARM_ROTATION.equals(this.rightArmRotation)) {
            nbtcompound.put("RightArm", this.rightArmRotation.toNbt());
        }

        if (!DEFAULT_LEFT_LEG_ROTATION.equals(this.leftLegRotation)) {
            nbtcompound.put("LeftLeg", this.leftLegRotation.toNbt());
        }

        if (!DEFAULT_RIGHT_LEG_ROTATION.equals(this.rightLegRotation)) {
            nbtcompound.put("RightLeg", this.rightLegRotation.toNbt());
        }

        return nbtcompound;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    protected void pushAwayCollidingEntities() {
        List<Entity> list = this.world.getEntities(this, this.getShape());
        if (list != null && !list.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                Entity entity = list.get(i);
                if (entity instanceof MinecartEntity
                    && ((MinecartEntity)entity).getMinecartType() == MinecartEntity.Type.RIDEABLE
                    && this.squaredDistanceTo(entity) <= 0.2) {
                    entity.push(this);
                }
            }
        }
    }

    @Override
    public boolean interactAt(PlayerEntity player, Vec3d offset) {
        if (this.isMarker()) {
            return false;
        }

        if (!this.world.isClient && !player.isSpectator()) {
            int i = 0;
            ItemStack itemstack = player.getItemInHand();
            boolean flag = itemstack != null;
            if (flag && itemstack.getItem() instanceof ArmorItem) {
                ArmorItem armoritem = (ArmorItem)itemstack.getItem();
                if (armoritem.slot == 3) {
                    i = 1;
                } else if (armoritem.slot == 2) {
                    i = 2;
                } else if (armoritem.slot == 1) {
                    i = 3;
                } else if (armoritem.slot == 0) {
                    i = 4;
                }
            }

            if (flag && (itemstack.getItem() == Items.SKULL || itemstack.getItem() == Item.byBlock(Blocks.PUMPKIN))) {
                i = 4;
            }

            double d4 = 0.1;
            double d0 = 0.9;
            double d1 = 0.4;
            double d2 = 1.6;
            int j = 0;
            boolean flag1 = this.isSmall();
            double d3 = flag1 ? offset.y * 2.0 : offset.y;
            if (d3 >= 0.1 && d3 < 0.1 + (flag1 ? 0.8 : 0.45) && this.equipment[1] != null) {
                j = 1;
            } else if (d3 >= 0.9 + (flag1 ? 0.3 : 0.0) && d3 < 0.9 + (flag1 ? 1.0 : 0.7) && this.equipment[3] != null) {
                j = 3;
            } else if (d3 >= 0.4 && d3 < 0.4 + (flag1 ? 1.0 : 0.8) && this.equipment[2] != null) {
                j = 2;
            } else if (d3 >= 1.6 && this.equipment[4] != null) {
                j = 4;
            }

            boolean flag2 = this.equipment[j] != null;
            if ((this.getDisabledSlots & 1 << j) != 0 || (this.getDisabledSlots & 1 << i) != 0) {
                j = i;
                if ((this.getDisabledSlots & 1 << j) != 0) {
                    if ((this.getDisabledSlots & 1) != 0) {
                        return true;
                    }

                    j = 0;
                }
            }

            if (flag && i == 0 && !this.isShowArms()) {
                return true;
            }

            if (flag) {
                this.swapItem(player, i);
            } else if (flag2) {
                this.swapItem(player, j);
            }

            return true;
        } else {
            return true;
        }
    }

    private void swapItem(PlayerEntity player, int slot) {
        ItemStack itemstack = this.equipment[slot];
        if (itemstack == null || (this.getDisabledSlots & 1 << slot + 8) == 0) {
            if (itemstack != null || (this.getDisabledSlots & 1 << slot + 16) == 0) {
                int i = player.inventory.selectedSlot;
                ItemStack itemstack1 = player.inventory.getItem(i);
                if (player.abilities.creativeMode && (itemstack == null || itemstack.getItem() == Item.byBlock(Blocks.AIR)) && itemstack1 != null) {
                    ItemStack itemstack3 = itemstack1.copy();
                    itemstack3.size = 1;
                    this.setEquipment(slot, itemstack3);
                } else if (itemstack1 == null || itemstack1.size <= 1) {
                    this.setEquipment(slot, itemstack1);
                    player.inventory.setItem(i, itemstack);
                } else if (itemstack == null) {
                    ItemStack itemstack2 = itemstack1.copy();
                    itemstack2.size = 1;
                    this.setEquipment(slot, itemstack2);
                    itemstack1.size--;
                }
            }
        }
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.world.isClient) {
            return false;
        }

        if (DamageSource.OUT_OF_WORLD.equals(source)) {
            this.remove();
            return false;
        }

        if (this.isInvulnerable(source) || this.invisible || this.isMarker()) {
            return false;
        }

        if (source.isExplosive()) {
            this.onBroken();
            this.remove();
            return false;
        }

        if (DamageSource.FIRE.equals(source)) {
            if (!this.isOnFire()) {
                this.setOnFireFor(5);
            } else {
                this.takeFireDamage(0.15F);
            }

            return false;
        } else {
            if (DamageSource.ON_FIRE.equals(source) && this.getHealth() > 0.5F) {
                this.takeFireDamage(4.0F);
                return false;
            }

            boolean flag = "arrow".equals(source.getName());
            boolean flag1 = "player".equals(source.getName());
            if (!flag1 && !flag) {
                return false;
            }

            if (source.getSource() instanceof ArrowEntity) {
                source.getSource().remove();
            }

            if (source.getAttacker() instanceof PlayerEntity && !((PlayerEntity)source.getAttacker()).abilities.canModifyWorld) {
                return false;
            }

            if (source.isCreativePlayer()) {
                this.addBreakingParticles();
                this.remove();
                return false;
            }

            long i = this.world.getTime();
            if (i - this.lastDamageTime > 5L && !flag) {
                this.lastDamageTime = i;
            } else {
                this.onBrokenByPlayer();
                this.addBreakingParticles();
                this.remove();
            }

            return false;
        }
    }

    @Override
    public boolean shouldRender(double squaredDistanceToCamera) {
        double d0 = this.getShape().getAverageSideLength() * 4.0;
        if (Double.isNaN(d0) || d0 == 0.0) {
            d0 = 4.0;
        }

        d0 *= 64.0;
        return squaredDistanceToCamera < d0 * d0;
    }

    private void addBreakingParticles() {
        if (this.world instanceof ServerWorld) {
            ((ServerWorld)this.world)
                .addParticle(
                    ParticleType.BLOCK_DUST,
                    this.x,
                    this.y + this.height / 1.5,
                    this.z,
                    10,
                    this.width / 4.0F,
                    this.height / 4.0F,
                    this.width / 4.0F,
                    0.05,
                    Block.serialize(Blocks.PLANKS.defaultState())
                );
        }
    }

    private void takeFireDamage(float amount) {
        float f = this.getHealth();
        f -= amount;
        if (f <= 0.5F) {
            this.onBroken();
            this.remove();
        } else {
            this.setHealth(f);
        }
    }

    private void onBrokenByPlayer() {
        Block.dropItem(this.world, new BlockPos(this), new ItemStack(Items.ARMOR_STAND));
        this.onBroken();
    }

    private void onBroken() {
        for (int i = 0; i < this.equipment.length; i++) {
            if (this.equipment[i] != null && this.equipment[i].size > 0) {
                if (this.equipment[i] != null) {
                    Block.dropItem(this.world, new BlockPos(this).up(), this.equipment[i]);
                }

                this.equipment[i] = null;
            }
        }
    }

    @Override
    protected float bodyMovement(float yaw, float movement) {
        this.lastBodyYaw = this.lastYaw;
        this.bodyYaw = this.yaw;
        return 0.0F;
    }

    @Override
    public float getEyeHeight() {
        return this.isBaby() ? this.height * 0.5F : this.height * 0.9F;
    }

    @Override
    public void moveRelative(float sideways, float forwards) {
        if (!this.isNoGravity()) {
            super.moveRelative(sideways, forwards);
        }
    }

    @Override
    public void tick() {
        super.tick();
        Rotation rotation = this.syncedData.getRotation(11);
        if (!this.headRotation.equals(rotation)) {
            this.setHeadRotation(rotation);
        }

        Rotation rotation1 = this.syncedData.getRotation(12);
        if (!this.bodyRotation.equals(rotation1)) {
            this.setBodyRotation(rotation1);
        }

        Rotation rotation2 = this.syncedData.getRotation(13);
        if (!this.leftArmRotation.equals(rotation2)) {
            this.setLeftArmRotation(rotation2);
        }

        Rotation rotation3 = this.syncedData.getRotation(14);
        if (!this.rightArmRotation.equals(rotation3)) {
            this.setRightArmRotation(rotation3);
        }

        Rotation rotation4 = this.syncedData.getRotation(15);
        if (!this.leftLegRotation.equals(rotation4)) {
            this.setLeftLegRotation(rotation4);
        }

        Rotation rotation5 = this.syncedData.getRotation(16);
        if (!this.rightLegRotation.equals(rotation5)) {
            this.setRightLegRotation(rotation5);
        }

        boolean flag = this.isMarker();
        if (!this.marker && flag) {
            this.updateSize(false);
        } else {
            if (!this.marker || flag) {
                return;
            }

            this.updateSize(true);
        }

        this.marker = flag;
    }

    private void updateSize(boolean marker) {
        double d0 = this.x;
        double d1 = this.y;
        double d2 = this.z;
        if (marker) {
            this.setSize(0.5F, 1.975F);
        } else {
            this.setSize(0.0F, 0.0F);
        }

        this.setPosition(d0, d1, d2);
    }

    @Override
    protected void updateVisibility() {
        this.setInvisible(this.invisible);
    }

    @Override
    public void setInvisible(boolean invisible) {
        this.invisible = invisible;
        super.setInvisible(invisible);
    }

    @Override
    public boolean isBaby() {
        return this.isSmall();
    }

    @Override
    public void discard() {
        this.remove();
    }

    @Override
    public boolean isImmuneToExplosions() {
        return this.isInvisible();
    }

    private void setSmall(boolean small) {
        byte b0 = this.syncedData.getByte(10);
        if (small) {
            b0 = (byte)(b0 | 1);
        } else {
            b0 = (byte)(b0 & -2);
        }

        this.syncedData.update(10, b0);
    }

    public boolean isSmall() {
        return (this.syncedData.getByte(10) & 1) != 0;
    }

    private void setNoGravity(boolean noGravity) {
        byte b0 = this.syncedData.getByte(10);
        if (noGravity) {
            b0 = (byte)(b0 | 2);
        } else {
            b0 = (byte)(b0 & -3);
        }

        this.syncedData.update(10, b0);
    }

    public boolean isNoGravity() {
        return (this.syncedData.getByte(10) & 2) != 0;
    }

    private void setShowArms(boolean showArms) {
        byte b0 = this.syncedData.getByte(10);
        if (showArms) {
            b0 = (byte)(b0 | 4);
        } else {
            b0 = (byte)(b0 & -5);
        }

        this.syncedData.update(10, b0);
    }

    public boolean isShowArms() {
        return (this.syncedData.getByte(10) & 4) != 0;
    }

    private void setArmsVisible(boolean visible) {
        byte b0 = this.syncedData.getByte(10);
        if (visible) {
            b0 = (byte)(b0 | 8);
        } else {
            b0 = (byte)(b0 & -9);
        }

        this.syncedData.update(10, b0);
    }

    public boolean isBasePlateVisible() {
        return (this.syncedData.getByte(10) & 8) != 0;
    }

    private void setMarker(boolean marker) {
        byte b0 = this.syncedData.getByte(10);
        if (marker) {
            b0 = (byte)(b0 | 16);
        } else {
            b0 = (byte)(b0 & -17);
        }

        this.syncedData.update(10, b0);
    }

    public boolean isMarker() {
        return (this.syncedData.getByte(10) & 16) != 0;
    }

    public void setHeadRotation(Rotation rotation) {
        this.headRotation = rotation;
        this.syncedData.update(11, rotation);
    }

    public void setBodyRotation(Rotation rotation) {
        this.bodyRotation = rotation;
        this.syncedData.update(12, rotation);
    }

    public void setLeftArmRotation(Rotation rotation) {
        this.leftArmRotation = rotation;
        this.syncedData.update(13, rotation);
    }

    public void setRightArmRotation(Rotation rotation) {
        this.rightArmRotation = rotation;
        this.syncedData.update(14, rotation);
    }

    public void setLeftLegRotation(Rotation rotation) {
        this.leftLegRotation = rotation;
        this.syncedData.update(15, rotation);
    }

    public void setRightLegRotation(Rotation rotation) {
        this.rightLegRotation = rotation;
        this.syncedData.update(16, rotation);
    }

    public Rotation getHeadRotation() {
        return this.headRotation;
    }

    public Rotation getBodyRotation() {
        return this.bodyRotation;
    }

    public Rotation getLeftArmRotation() {
        return this.leftArmRotation;
    }

    public Rotation getRightArmRotation() {
        return this.rightArmRotation;
    }

    public Rotation getLeftLegRotation() {
        return this.leftLegRotation;
    }

    public Rotation getRightLegRotation() {
        return this.rightLegRotation;
    }

    @Override
    public boolean hasCollision() {
        return super.hasCollision() && !this.isMarker();
    }
}
