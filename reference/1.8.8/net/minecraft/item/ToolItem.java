package net.minecraft.item;

import com.google.common.collect.Multimap;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ToolItem extends Item {
    private Set<Block> effectiveBlocks;
    protected float miningSpeed = 4.0F;
    private float attackDamage;
    protected Item.Tier tier;

    protected ToolItem(float attackDamage, Item.Tier tier, Set<Block> effectiveBlocks) {
        this.tier = tier;
        this.effectiveBlocks = effectiveBlocks;
        this.maxStackSize = 1;
        this.setMaxDamage(tier.getDurability());
        this.miningSpeed = tier.getMiningSpeed();
        this.attackDamage = attackDamage + tier.getAttackDamage();
        this.setCreativeModeTab(CreativeModeTab.TOOLS);
    }

    @Override
    public float getMiningSpeed(ItemStack stack, Block block) {
        return this.effectiveBlocks.contains(block) ? this.miningSpeed : 1.0F;
    }

    @Override
    public boolean attack(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.takeDamageAndBreak(2, attacker);
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, World world, Block block, BlockPos pos, LivingEntity entity) {
        if (block.getMiningTime(world, pos) != 0.0) {
            stack.takeDamageAndBreak(1, entity);
        }

        return true;
    }

    @Override
    public boolean isHandheld() {
        return true;
    }

    public Item.Tier getTier() {
        return this.tier;
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
        multimap.put(EntityAttributes.ATTACK_DAMAGE.getName(), new AttributeModifier(ATTACK_DAMAGE_MODIFIER_UUID, "Tool modifier", this.attackDamage, 0));
        return multimap;
    }
}
