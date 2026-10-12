package net.minecraft.block.entity;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.mob.passive.animal.SheepEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.BeaconMenu;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class BeaconBlockEntity extends InventoryBlockEntity implements Tickable, Inventory {
    public static final StatusEffect[][] EFFECTS = new StatusEffect[][]{
        {StatusEffect.SPEED, StatusEffect.HASTE}, {StatusEffect.RESISTANCE, StatusEffect.JUMP_BOOST}, {StatusEffect.STRENGTH}, {StatusEffect.REGENERATION}
    };
    private final List<BeaconBlockEntity.BeamSection> beamSections = Lists.newArrayList();
    private long lastBeamRenderTime;
    private float beamAngle;
    private boolean active;
    private int levels = -1;
    private int primaryEffect;
    private int secondaryEffect;
    private ItemStack inventory;
    private String customName;

    @Override
    public void tick() {
        if (this.world.getTime() % 80L == 0L) {
            this.update();
        }
    }

    public void update() {
        this.updateLevels();
        this.spreadEffects();
    }

    private void spreadEffects() {
        if (this.active && this.levels > 0 && !this.world.isClient && this.primaryEffect > 0) {
            double d0 = this.levels * 10 + 10;
            int i = 0;
            if (this.levels >= 4 && this.primaryEffect == this.secondaryEffect) {
                i = 1;
            }

            int j = this.pos.getX();
            int k = this.pos.getY();
            int l = this.pos.getZ();
            Box box = new Box(j, k, l, j + 1, k + 1, l + 1).grown(d0, d0, d0).expanded(0.0, this.world.getHeight(), 0.0);
            List<PlayerEntity> list = this.world.getEntitiesOfType(PlayerEntity.class, box);

            for (PlayerEntity playerentity : list) {
                playerentity.addStatusEffect(new StatusEffectInstance(this.primaryEffect, 180, i, true, true));
            }

            if (this.levels >= 4 && this.primaryEffect != this.secondaryEffect && this.secondaryEffect > 0) {
                for (PlayerEntity playerentity1 : list) {
                    playerentity1.addStatusEffect(new StatusEffectInstance(this.secondaryEffect, 180, 0, true, true));
                }
            }
        }
    }

    private void updateLevels() {
        int i = this.levels;
        int j = this.pos.getX();
        int k = this.pos.getY();
        int l = this.pos.getZ();
        this.levels = 0;
        this.beamSections.clear();
        this.active = true;
        BeaconBlockEntity.BeamSection beaconblockentity$beamsection = new BeaconBlockEntity.BeamSection(SheepEntity.getColorRgb(DyeColor.WHITE));
        this.beamSections.add(beaconblockentity$beamsection);
        boolean flag = true;
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int i1 = k + 1; i1 < 256; i1++) {
            BlockState blockstate = this.world.getBlockState(blockpos$mutable.set(j, i1, l));
            float[] afloat;
            if (blockstate.getBlock() == Blocks.STAINED_GLASS) {
                afloat = SheepEntity.getColorRgb(blockstate.get(StainedGlassBlock.COLOR));
            } else {
                if (blockstate.getBlock() != Blocks.STAINED_GLASS_PANE) {
                    if (blockstate.getBlock().getOpacity() >= 15 && blockstate.getBlock() != Blocks.BEDROCK) {
                        this.active = false;
                        this.beamSections.clear();
                        break;
                    }

                    beaconblockentity$beamsection.incrementHeight();
                    continue;
                }

                afloat = SheepEntity.getColorRgb(blockstate.get(StainedGlassPaneBlock.COLOR));
            }

            if (!flag) {
                afloat = new float[]{
                    (beaconblockentity$beamsection.getColor()[0] + afloat[0]) / 2.0F,
                    (beaconblockentity$beamsection.getColor()[1] + afloat[1]) / 2.0F,
                    (beaconblockentity$beamsection.getColor()[2] + afloat[2]) / 2.0F
                };
            }

            if (Arrays.equals(afloat, beaconblockentity$beamsection.getColor())) {
                beaconblockentity$beamsection.incrementHeight();
            } else {
                beaconblockentity$beamsection = new BeaconBlockEntity.BeamSection(afloat);
                this.beamSections.add(beaconblockentity$beamsection);
            }

            flag = false;
        }

        if (this.active) {
            for (int l1 = 1; l1 <= 4; this.levels = l1++) {
                int i2 = k - l1;
                if (i2 < 0) {
                    break;
                }

                boolean flag1 = true;

                for (int j1 = j - l1; j1 <= j + l1 && flag1; j1++) {
                    for (int k1 = l - l1; k1 <= l + l1; k1++) {
                        Block block = this.world.getBlockState(new BlockPos(j1, i2, k1)).getBlock();
                        if (block != Blocks.EMERALD_BLOCK && block != Blocks.GOLD_BLOCK && block != Blocks.DIAMOND_BLOCK && block != Blocks.IRON_BLOCK) {
                            flag1 = false;
                            break;
                        }
                    }
                }

                if (!flag1) {
                    break;
                }
            }

            if (this.levels == 0) {
                this.active = false;
            }
        }

        if (!this.world.isClient && this.levels == 4 && i < this.levels) {
            for (PlayerEntity playerentity : this.world.getEntitiesOfType(PlayerEntity.class, new Box(j, k, l, j, k - 4, l).grown(10.0, 5.0, 10.0))) {
                playerentity.incrementStat(Achievements.ACTIVATE_MAX_BEACON);
            }
        }
    }

    public List<BeaconBlockEntity.BeamSection> getBeamSections() {
        return this.beamSections;
    }

    public float getBeamAngle() {
        if (!this.active) {
            return 0.0F;
        }

        int i = (int)(this.world.getTime() - this.lastBeamRenderTime);
        this.lastBeamRenderTime = this.world.getTime();
        if (i > 1) {
            this.beamAngle -= i / 40.0F;
            if (this.beamAngle < 0.0F) {
                this.beamAngle = 0.0F;
            }
        }

        this.beamAngle += 0.025F;
        if (this.beamAngle > 1.0F) {
            this.beamAngle = 1.0F;
        }

        return this.beamAngle;
    }

    @Override
    public Packet createUpdatePacket() {
        NbtCompound nbtcompound = new NbtCompound();
        this.writeNbt(nbtcompound);
        return new BlockEntityUpdateS2CPacket(this.pos, 3, nbtcompound);
    }

    @Override
    public double getSquaredViewDistance() {
        return 65536.0;
    }

    private int getBeaconEffect(int id) {
        if (id >= 0 && id < StatusEffect.BY_ID.length && StatusEffect.BY_ID[id] != null) {
            StatusEffect statuseffect = StatusEffect.BY_ID[id];
            return statuseffect != StatusEffect.SPEED
                    && statuseffect != StatusEffect.HASTE
                    && statuseffect != StatusEffect.RESISTANCE
                    && statuseffect != StatusEffect.JUMP_BOOST
                    && statuseffect != StatusEffect.STRENGTH
                    && statuseffect != StatusEffect.REGENERATION
                ? 0
                : id;
        } else {
            return 0;
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.primaryEffect = this.getBeaconEffect(nbt.getInt("Primary"));
        this.secondaryEffect = this.getBeaconEffect(nbt.getInt("Secondary"));
        this.levels = nbt.getInt("Levels");
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("Primary", this.primaryEffect);
        nbt.putInt("Secondary", this.secondaryEffect);
        nbt.putInt("Levels", this.levels);
    }

    @Override
    public int getSize() {
        return 1;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? this.inventory : null;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || this.inventory == null) {
            return null;
        } else if (amount >= this.inventory.size) {
            ItemStack itemstack = this.inventory;
            this.inventory = null;
            return itemstack;
        } else {
            this.inventory.size -= amount;
            return new ItemStack(this.inventory.getItem(), amount, this.inventory.getMetadata());
        }
    }

    @Override
    public ItemStack removeItemQuietly(int slot) {
        if (slot == 0 && this.inventory != null) {
            ItemStack itemstack = this.inventory;
            this.inventory = null;
            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        if (slot == 0) {
            this.inventory = item;
        }
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.customName : "container.beacon";
    }

    @Override
    public boolean hasCustomName() {
        return this.customName != null && this.customName.length() > 0;
    }

    public void setCustomName(String name) {
        this.customName = name;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean isValid(PlayerEntity player) {
        return this.world.getBlockEntity(this.pos) == this
            && !(player.squaredDistanceTo(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) > 64.0);
    }

    @Override
    public void onOpen(PlayerEntity player) {
    }

    @Override
    public void onClose(PlayerEntity player) {
    }

    @Override
    public boolean isItemAllowed(int slot, ItemStack item) {
        return item.getItem() == Items.EMERALD || item.getItem() == Items.DIAMOND || item.getItem() == Items.GOLD_INGOT || item.getItem() == Items.IRON_INGOT;
    }

    @Override
    public String getMenuType() {
        return "minecraft:beacon";
    }

    @Override
    public InventoryMenu createMenu(PlayerInventory playerInventory, PlayerEntity player) {
        return new BeaconMenu(playerInventory, this);
    }

    @Override
    public int getData(int id) {
        switch (id) {
            case 0:
                return this.levels;
            case 1:
                return this.primaryEffect;
            case 2:
                return this.secondaryEffect;
            default:
                return 0;
        }
    }

    @Override
    public void setData(int id, int value) {
        switch (id) {
            case 0:
                this.levels = value;
                break;
            case 1:
                this.primaryEffect = this.getBeaconEffect(value);
                break;
            case 2:
                this.secondaryEffect = this.getBeaconEffect(value);
        }
    }

    @Override
    public int getDataSize() {
        return 3;
    }

    @Override
    public void clear() {
        this.inventory = null;
    }

    @Override
    public boolean doEvent(int type, int data) {
        if (type == 1) {
            this.update();
            return true;
        } else {
            return super.doEvent(type, data);
        }
    }

    public static class BeamSection {
        private final float[] color;
        private int height;

        public BeamSection(float[] color) {
            this.color = color;
            this.height = 1;
        }

        protected void incrementHeight() {
            this.height++;
        }

        public float[] getColor() {
            return this.color;
        }

        public int getHeight() {
            return this.height;
        }
    }
}
