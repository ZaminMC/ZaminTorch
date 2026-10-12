package net.minecraft.item;

import com.google.common.base.Predicates;
import java.util.List;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.DispenseBehavior;
import net.minecraft.block.dispenser.DispenseItemBehavior;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.IBlockSource;
import net.minecraft.world.World;

public class ArmorItem extends Item {
    private static final int[] BASE_DURABILITY = new int[]{11, 16, 15, 13};
    public static final String[] EMPTY_SLOTS = new String[]{
        "minecraft:items/empty_armor_slot_helmet",
        "minecraft:items/empty_armor_slot_chestplate",
        "minecraft:items/empty_armor_slot_leggings",
        "minecraft:items/empty_armor_slot_boots"
    };
    private static final DispenseBehavior DISPENSE_BEHAVIOR = new DispenseItemBehavior() {
        @Override
        protected ItemStack dispenseItem(IBlockSource source, ItemStack item) {
            BlockPos blockpos = source.getPos().offset(DispenserBlock.getDirection(source.getBlockMetadata()));
            int i = blockpos.getX();
            int j = blockpos.getY();
            int k = blockpos.getZ();
            Box box = new Box(i, j, k, i + 1, j + 1, k + 1);
            List<LivingEntity> list = source.getWorld()
                .getEntitiesOfType(LivingEntity.class, box, Predicates.and(EntityFilter.NOT_SPECTATOR, new EntityFilter.CanPickUpItemsFilter(item)));
            if (list.size() > 0) {
                LivingEntity livingentity = list.get(0);
                int l = livingentity instanceof PlayerEntity ? 1 : 0;
                int i1 = MobEntity.getEquipmentSlot(item);
                ItemStack itemstack = item.copy();
                itemstack.size = 1;
                livingentity.setEquipment(i1 - l, itemstack);
                if (livingentity instanceof MobEntity) {
                    ((MobEntity)livingentity).setInventoryDropChances(i1, 2.0F);
                }

                item.size--;
                return item;
            } else {
                return super.dispenseItem(source, item);
            }
        }
    };
    public final int slot;
    public final int protection;
    public final int material;
    private final ArmorItem.Tier tier;

    public ArmorItem(ArmorItem.Tier material, int materialId, int slot) {
        this.tier = material;
        this.slot = slot;
        this.material = materialId;
        this.protection = material.getProtection(slot);
        this.setMaxDamage(material.getDurability(slot));
        this.maxStackSize = 1;
        this.setCreativeModeTab(CreativeModeTab.COMBAT);
        DispenserBlock.BEHAVIORS.put(this, DISPENSE_BEHAVIOR);
    }

    @Override
    public int getDisplayColor(ItemStack stack, int stage) {
        if (stage > 0) {
            return 16777215;
        }

        int i = this.getColor(stack);
        if (i < 0) {
            i = 16777215;
        }

        return i;
    }

    @Override
    public int getEnchantability() {
        return this.tier.getEnchantability();
    }

    public ArmorItem.Tier getTier() {
        return this.tier;
    }

    public boolean hasColor(ItemStack stack) {
        return this.tier == ArmorItem.Tier.CLOTH
            && stack.hasNbt()
            && stack.getNbt().contains("display", 10)
            && stack.getNbt().getCompound("display").contains("color", 3);
    }

    public int getColor(ItemStack stack) {
        if (this.tier != ArmorItem.Tier.CLOTH) {
            return -1;
        }

        NbtCompound nbtcompound = stack.getNbt();
        if (nbtcompound != null) {
            NbtCompound nbtcompound1 = nbtcompound.getCompound("display");
            if (nbtcompound1 != null && nbtcompound1.contains("color", 3)) {
                return nbtcompound1.getInt("color");
            }
        }

        return 10511680;
    }

    public void removeColor(ItemStack stack) {
        if (this.tier == ArmorItem.Tier.CLOTH) {
            NbtCompound nbtcompound = stack.getNbt();
            if (nbtcompound != null) {
                NbtCompound nbtcompound1 = nbtcompound.getCompound("display");
                if (nbtcompound1.contains("color")) {
                    nbtcompound1.remove("color");
                }
            }
        }
    }

    public void setColor(ItemStack stack, int color) {
        if (this.tier != ArmorItem.Tier.CLOTH) {
            throw new UnsupportedOperationException("Can't dye non-leather!");
        }

        NbtCompound nbtcompound = stack.getNbt();
        if (nbtcompound == null) {
            nbtcompound = new NbtCompound();
            stack.setNbt(nbtcompound);
        }

        NbtCompound nbtcompound1 = nbtcompound.getCompound("display");
        if (!nbtcompound.contains("display", 10)) {
            nbtcompound.put("display", nbtcompound1);
        }

        nbtcompound1.putInt("color", color);
    }

    @Override
    public boolean isRepairable(ItemStack stack, ItemStack ingredient) {
        return this.tier.getRepairIngredient() == ingredient.getItem() || super.isRepairable(stack, ingredient);
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        int i = MobEntity.getEquipmentSlot(stack) - 1;
        ItemStack itemstack = player.getArmor(i);
        if (itemstack == null) {
            player.setEquipment(i, stack.copy());
            stack.size = 0;
        }

        return stack;
    }

    public enum Tier {
        CLOTH("leather", 5, new int[]{1, 3, 2, 1}, 15),
        CHAIN("chainmail", 15, new int[]{2, 5, 4, 1}, 12),
        IRON("iron", 15, new int[]{2, 6, 5, 2}, 9),
        GOLD("gold", 7, new int[]{2, 5, 3, 1}, 25),
        DIAMOND("diamond", 33, new int[]{3, 8, 6, 3}, 10);

        private final String key;
        private final int durability;
        private final int[] protection;
        private final int enchantability;

        Tier(String id, int durability, int[] protection, int enchantability) {
            this.key = id;
            this.durability = durability;
            this.protection = protection;
            this.enchantability = enchantability;
        }

        public int getDurability(int slot) {
            return ArmorItem.BASE_DURABILITY[slot] * this.durability;
        }

        public int getProtection(int slot) {
            return this.protection[slot];
        }

        public int getEnchantability() {
            return this.enchantability;
        }

        public Item getRepairIngredient() {
            if (this == CLOTH) {
                return Items.LEATHER;
            } else if (this == CHAIN) {
                return Items.IRON_INGOT;
            } else if (this == GOLD) {
                return Items.GOLD_INGOT;
            } else if (this == IRON) {
                return Items.IRON_INGOT;
            } else {
                return this == DIAMOND ? Items.DIAMOND : null;
            }
        }

        public String getKey() {
            return this.key;
        }
    }
}
