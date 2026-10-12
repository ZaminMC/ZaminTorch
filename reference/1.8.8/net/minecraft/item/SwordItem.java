package net.minecraft.item;

import com.google.common.collect.Multimap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SwordItem extends Item {
    private float attackDamage;
    private final Item.Tier tier;

    public SwordItem(Item.Tier tier) {
        this.tier = tier;
        this.maxStackSize = 1;
        this.setMaxDamage(tier.getDurability());
        this.setCreativeModeTab(CreativeModeTab.COMBAT);
        this.attackDamage = 4.0F + tier.getAttackDamage();
    }

    public float getAttackDamage() {
        return this.tier.getAttackDamage();
    }

    @Override
    public float getMiningSpeed(ItemStack stack, Block block) {
        if (block == Blocks.WEB) {
            return 15.0F;
        }

        Material material = block.getMaterial();
        return material != Material.PLANT
                && material != Material.REPLACEABLE_PLANT
                && material != Material.CORAL
                && material != Material.LEAVES
                && material != Material.PUMPKIN
            ? 1.0F
            : 1.5F;
    }

    @Override
    public boolean attack(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.takeDamageAndBreak(1, attacker);
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, World world, Block block, BlockPos pos, LivingEntity entity) {
        if (block.getMiningTime(world, pos) != 0.0) {
            stack.takeDamageAndBreak(2, entity);
        }

        return true;
    }

    @Override
    public boolean isHandheld() {
        return true;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        player.setItemInUse(stack, this.getUseDuration(stack));
        return stack;
    }

    @Override
    public boolean canMineBlock(Block block) {
        return block == Blocks.WEB;
    }

    @Override
    public int getEnchantability() {
        return this.tier.getEnchantability();
    }

    public String getTierName() {
        return this.tier.toString();
    }

    @Override
    public boolean isRepairable(ItemStack stack, ItemStack ingredient) {
        return this.tier.getRepairIngredient() == ingredient.getItem() || super.isRepairable(stack, ingredient);
    }

    @Override
    public Multimap<String, AttributeModifier> getDefaultAttributeModifiers() {
        Multimap<String, AttributeModifier> multimap = super.getDefaultAttributeModifiers();
        multimap.put(EntityAttributes.ATTACK_DAMAGE.getName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER_UUID, "Weapon modifier", this.attackDamage, 0));
        return multimap;
    }
}
