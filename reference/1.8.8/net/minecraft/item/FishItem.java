package net.minecraft.item;

import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.living.effect.PotionHelper;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.World;

public class FishItem extends FoodItem {
    private final boolean cooked;

    public FishItem(boolean cooked) {
        super(0, 0.0F, false);
        this.cooked = cooked;
    }

    @Override
    public int getHungerPoints(ItemStack stack) {
        FishItem.Type fishitem$type = FishItem.Type.byItem(stack);
        return this.cooked && fishitem$type.canBeCooked() ? fishitem$type.getHungerPointsForCooked() : fishitem$type.getHungerPointsForRaw();
    }

    @Override
    public float getSaturation(ItemStack stack) {
        FishItem.Type fishitem$type = FishItem.Type.byItem(stack);
        return this.cooked && fishitem$type.canBeCooked() ? fishitem$type.getSaturationForCooked() : fishitem$type.getSaturationForRaw();
    }

    @Override
    public String asPotionIngredient(ItemStack stack) {
        return FishItem.Type.byItem(stack) == FishItem.Type.PUFFERFISH ? PotionHelper.PUFFERFISH : null;
    }

    @Override
    protected void addEatEffects(ItemStack stack, World world, PlayerEntity player) {
        FishItem.Type fishitem$type = FishItem.Type.byItem(stack);
        if (fishitem$type == FishItem.Type.PUFFERFISH) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffect.POISON.id, 1200, 3));
            player.addStatusEffect(new StatusEffectInstance(StatusEffect.HUNGER.id, 300, 2));
            player.addStatusEffect(new StatusEffectInstance(StatusEffect.NAUSEA.id, 300, 1));
        }

        super.addEatEffects(stack, world, player);
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (FishItem.Type fishitem$type : FishItem.Type.values()) {
            if (!this.cooked || fishitem$type.canBeCooked()) {
                inventory.add(new ItemStack(this, 1, fishitem$type.getId()));
            }
        }
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        FishItem.Type fishitem$type = FishItem.Type.byItem(stack);
        return this.getTranslationKey() + "." + fishitem$type.getKey() + "." + (this.cooked && fishitem$type.canBeCooked() ? "cooked" : "raw");
    }

    public enum Type {
        COD(0, "cod", 2, 0.1F, 5, 0.6F),
        SALMON(1, "salmon", 2, 0.1F, 6, 0.8F),
        CLOWNFISH(2, "clownfish", 1, 0.1F),
        PUFFERFISH(3, "pufferfish", 1, 0.1F);

        private static final Map<Integer, FishItem.Type> BY_ID = Maps.newHashMap();
        private final int id;
        private final String key;
        private final int hungerPointsForRaw;
        private final float saturationForRaw;
        private final int hungerPointsForCooked;
        private final float saturationForCooked;
        private boolean canBeCooked = false;

        Type(int id, String key, int hungerPointsForRaw, float saturationForRaw, int hungerPointsForCooked, float saturationForCooked) {
            this.id = id;
            this.key = key;
            this.hungerPointsForRaw = hungerPointsForRaw;
            this.saturationForRaw = saturationForRaw;
            this.hungerPointsForCooked = hungerPointsForCooked;
            this.saturationForCooked = saturationForCooked;
            this.canBeCooked = true;
        }

        Type(int id, String key, int hungerPoints, float saturation) {
            this.id = id;
            this.key = key;
            this.hungerPointsForRaw = hungerPoints;
            this.saturationForRaw = saturation;
            this.hungerPointsForCooked = 0;
            this.saturationForCooked = 0.0F;
            this.canBeCooked = false;
        }

        public int getId() {
            return this.id;
        }

        public String getKey() {
            return this.key;
        }

        public int getHungerPointsForRaw() {
            return this.hungerPointsForRaw;
        }

        public float getSaturationForRaw() {
            return this.saturationForRaw;
        }

        public int getHungerPointsForCooked() {
            return this.hungerPointsForCooked;
        }

        public float getSaturationForCooked() {
            return this.saturationForCooked;
        }

        public boolean canBeCooked() {
            return this.canBeCooked;
        }

        public static FishItem.Type byId(int id) {
            FishItem.Type fishitem$type = BY_ID.get(id);
            return fishitem$type == null ? COD : fishitem$type;
        }

        public static FishItem.Type byItem(ItemStack stack) {
            return stack.getItem() instanceof FishItem ? byId(stack.getMetadata()) : COD;
        }

        static {
            for (FishItem.Type fishitem$type : values()) {
                BY_ID.put(fishitem$type.getId(), fishitem$type);
            }
        }
    }
}
