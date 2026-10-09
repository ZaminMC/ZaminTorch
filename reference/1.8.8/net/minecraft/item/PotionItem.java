package net.minecraft.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.attribute.EntityAttribute;
import net.minecraft.entity.living.effect.PotionHelper;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.PotionEntity;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.stat.Stats;
import net.minecraft.text.Formatting;
import net.minecraft.world.World;

public class PotionItem extends Item {
    private Map<Integer, List<StatusEffectInstance>> potionEffectsByMetadataCache = Maps.newHashMap();
    private static final Map<List<StatusEffectInstance>, Integer> ITEM_STACKS = Maps.newLinkedHashMap();

    public PotionItem() {
        this.setMaxStackSize(1);
        this.setHasCustomData(true);
        this.setMaxDamage(0);
        this.setCreativeModeTab(CreativeModeTab.BREWING);
    }

    public List<StatusEffectInstance> getPotionEffects(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt().contains("CustomPotionEffects", 9)) {
            List<StatusEffectInstance> list1 = Lists.newArrayList();
            NbtList nbtlist = stack.getNbt().getList("CustomPotionEffects", 10);

            for (int i = 0; i < nbtlist.size(); i++) {
                NbtCompound nbtcompound = nbtlist.getCompound(i);
                StatusEffectInstance statuseffectinstance = StatusEffectInstance.fromNbt(nbtcompound);
                if (statuseffectinstance != null) {
                    list1.add(statuseffectinstance);
                }
            }

            return list1;
        } else {
            List<StatusEffectInstance> list = this.potionEffectsByMetadataCache.get(stack.getMetadata());
            if (list == null) {
                list = PotionHelper.getStatusEffects(stack.getMetadata(), false);
                this.potionEffectsByMetadataCache.put(stack.getMetadata(), list);
            }

            return list;
        }
    }

    public List<StatusEffectInstance> getPotionEffects(int metadata) {
        List<StatusEffectInstance> list = this.potionEffectsByMetadataCache.get(metadata);
        if (list == null) {
            list = PotionHelper.getStatusEffects(metadata, false);
            this.potionEffectsByMetadataCache.put(metadata, list);
        }

        return list;
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, PlayerEntity player) {
        if (!player.abilities.creativeMode) {
            stack.size--;
        }

        if (!world.isClient) {
            List<StatusEffectInstance> list = this.getPotionEffects(stack);
            if (list != null) {
                for (StatusEffectInstance statuseffectinstance : list) {
                    player.addStatusEffect(new StatusEffectInstance(statuseffectinstance));
                }
            }
        }

        player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
        if (!player.abilities.creativeMode) {
            if (stack.size <= 0) {
                return new ItemStack(Items.GLASS_BOTTLE);
            }

            player.inventory.addItem(new ItemStack(Items.GLASS_BOTTLE));
        }

        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 32;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        if (isSplashPotion(stack.getMetadata())) {
            if (!player.abilities.creativeMode) {
                stack.size--;
            }

            world.playSound((Entity)player, "random.bow", 0.5F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
            if (!world.isClient) {
                world.addEntity(new PotionEntity(world, player, stack));
            }

            player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
            return stack;
        } else {
            player.setItemInUse(stack, this.getUseDuration(stack));
            return stack;
        }
    }

    public static boolean isSplashPotion(int metadata) {
        return (metadata & 16384) != 0;
    }

    public int getPotionColor(int metadata) {
        return PotionHelper.getColor(metadata, false);
    }

    @Override
    public int getDisplayColor(ItemStack stack, int stage) {
        return stage > 0 ? 16777215 : this.getPotionColor(stack.getMetadata());
    }

    public boolean hasInstantPotionEffect(int metadata) {
        List<StatusEffectInstance> list = this.getPotionEffects(metadata);
        if (list != null && !list.isEmpty()) {
            for (StatusEffectInstance statuseffectinstance : list) {
                if (StatusEffect.BY_ID[statuseffectinstance.getId()].isInstant()) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    @Override
    public String getName(ItemStack stack) {
        if (stack.getMetadata() == 0) {
            return I18n.translate("item.emptyPotion.name").trim();
        }

        String s = "";
        if (isSplashPotion(stack.getMetadata())) {
            s = I18n.translate("potion.prefix.grenade").trim() + " ";
        }

        List<StatusEffectInstance> list = Items.POTION.getPotionEffects(stack);
        if (list != null && !list.isEmpty()) {
            String s2 = list.get(0).getName();
            s2 = s2 + ".postfix";
            return s + I18n.translate(s2).trim();
        } else {
            String s1 = PotionHelper.getPrefix(stack.getMetadata());
            return I18n.translate(s1).trim() + " " + super.getName(stack);
        }
    }

    @Override
    public void addHoverText(ItemStack stack, PlayerEntity player, List<String> tooltip, boolean advanced) {
        if (stack.getMetadata() != 0) {
            List<StatusEffectInstance> list = Items.POTION.getPotionEffects(stack);
            Multimap<String, AttributeModifier> multimap = HashMultimap.create();
            if (list != null && !list.isEmpty()) {
                for (StatusEffectInstance statuseffectinstance : list) {
                    String s1 = I18n.translate(statuseffectinstance.getName()).trim();
                    StatusEffect statuseffect = StatusEffect.BY_ID[statuseffectinstance.getId()];
                    Map<EntityAttribute, AttributeModifier> map = statuseffect.getModifiers();
                    if (map != null && map.size() > 0) {
                        for (Entry<EntityAttribute, AttributeModifier> entry : map.entrySet()) {
                            AttributeModifier attributemodifier = entry.getValue();
                            AttributeModifier attributemodifier1 = new AttributeModifier(
                                attributemodifier.getName(),
                                statuseffect.getModifier(statuseffectinstance.getAmplifier(), attributemodifier),
                                attributemodifier.getOperation()
                            );
                            multimap.put(entry.getKey().getName(), attributemodifier1);
                        }
                    }

                    if (statuseffectinstance.getAmplifier() > 0) {
                        s1 = s1 + " " + I18n.translate("potion.potency." + statuseffectinstance.getAmplifier()).trim();
                    }

                    if (statuseffectinstance.getDuration() > 20) {
                        s1 = s1 + " (" + StatusEffect.getDurationString(statuseffectinstance) + ")";
                    }

                    if (statuseffect.isHarmful()) {
                        tooltip.add(Formatting.RED + s1);
                    } else {
                        tooltip.add(Formatting.GRAY + s1);
                    }
                }
            } else {
                String s = I18n.translate("potion.empty").trim();
                tooltip.add(Formatting.GRAY + s);
            }

            if (!multimap.isEmpty()) {
                tooltip.add("");
                tooltip.add(Formatting.DARK_PURPLE + I18n.translate("potion.effects.whenDrank"));

                for (Entry<String, AttributeModifier> entry1 : multimap.entries()) {
                    AttributeModifier attributemodifier2 = entry1.getValue();
                    double d0 = attributemodifier2.get();
                    double d1;
                    if (attributemodifier2.getOperation() != 1 && attributemodifier2.getOperation() != 2) {
                        d1 = attributemodifier2.get();
                    } else {
                        d1 = attributemodifier2.get() * 100.0;
                    }

                    if (d0 > 0.0) {
                        tooltip.add(
                            Formatting.BLUE
                                + I18n.translate(
                                    "attribute.modifier.plus." + attributemodifier2.getOperation(),
                                    ItemStack.MODIFIER_FORMAT.format(d1),
                                    I18n.translate("attribute.name." + entry1.getKey())
                                )
                        );
                    } else if (d0 < 0.0) {
                        d1 *= -1.0;
                        tooltip.add(
                            Formatting.RED
                                + I18n.translate(
                                    "attribute.modifier.take." + attributemodifier2.getOperation(),
                                    ItemStack.MODIFIER_FORMAT.format(d1),
                                    I18n.translate("attribute.name." + entry1.getKey())
                                )
                        );
                    }
                }
            }
        }
    }

    @Override
    public boolean hasEnchantmentGlint(ItemStack stack) {
        List<StatusEffectInstance> list = this.getPotionEffects(stack);
        return list != null && !list.isEmpty();
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        super.addToCreativeMenu(item, tab, inventory);
        if (ITEM_STACKS.isEmpty()) {
            for (int i = 0; i <= 15; i++) {
                for (int j = 0; j <= 1; j++) {
                    int k = i;
                    if (j == 0) {
                        k |= 8192;
                    } else {
                        k |= 16384;
                    }

                    for (int l = 0; l <= 2; l++) {
                        int i1 = k;
                        if (l != 0) {
                            if (l == 1) {
                                i1 |= 32;
                            } else if (l == 2) {
                                i1 |= 64;
                            }
                        }

                        List<StatusEffectInstance> list = PotionHelper.getStatusEffects(i1, false);
                        if (list != null && !list.isEmpty()) {
                            ITEM_STACKS.put(list, i1);
                        }
                    }
                }
            }
        }

        for (int j1 : ITEM_STACKS.values()) {
            inventory.add(new ItemStack(item, 1, j1));
        }
    }
}
