package net.minecraft.item;

import java.util.List;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIntArray;

public class FireworksChargeItem extends Item {
    @Override
    public int getDisplayColor(ItemStack stack, int stage) {
        if (stage != 1) {
            return super.getDisplayColor(stack, stage);
        }

        NbtElement nbtelement = getExplosionNbt(stack, "Colors");
        if (!(nbtelement instanceof NbtIntArray)) {
            return 9079434;
        }

        NbtIntArray nbtintarray = (NbtIntArray)nbtelement;
        int[] aint = nbtintarray.getIntArray();
        if (aint.length == 1) {
            return aint[0];
        }

        int i = 0;
        int j = 0;
        int k = 0;

        for (int l : aint) {
            i += (l & 0xFF0000) >> 16;
            j += (l & 0xFF00) >> 8;
            k += (l & 0xFF) >> 0;
        }

        i /= aint.length;
        j /= aint.length;
        k /= aint.length;
        return i << 16 | j << 8 | k;
    }

    public static NbtElement getExplosionNbt(ItemStack stack, String name) {
        if (stack.hasNbt()) {
            NbtCompound nbtcompound = stack.getNbt().getCompound("Explosion");
            if (nbtcompound != null) {
                return nbtcompound.get(name);
            }
        }

        return null;
    }

    @Override
    public void addHoverText(ItemStack stack, PlayerEntity player, List<String> tooltip, boolean advanced) {
        if (stack.hasNbt()) {
            NbtCompound nbtcompound = stack.getNbt().getCompound("Explosion");
            if (nbtcompound != null) {
                addExplosionInfo(nbtcompound, tooltip);
            }
        }
    }

    public static void addExplosionInfo(NbtCompound nbt, List<String> list) {
        byte b0 = nbt.getByte("Type");
        if (b0 >= 0 && b0 <= 4) {
            list.add(I18n.translate("item.fireworksCharge.type." + b0).trim());
        } else {
            list.add(I18n.translate("item.fireworksCharge.type").trim());
        }

        int[] aint = nbt.getIntArray("Colors");
        if (aint.length > 0) {
            boolean flag = true;
            String s = "";

            for (int i : aint) {
                if (!flag) {
                    s = s + ", ";
                }

                flag = false;
                boolean flag1 = false;

                for (int j = 0; j < DyeItem.COLORS.length; j++) {
                    if (i == DyeItem.COLORS[j]) {
                        flag1 = true;
                        s = s + I18n.translate("item.fireworksCharge." + DyeColor.byMetadata(j).getName());
                        break;
                    }
                }

                if (!flag1) {
                    s = s + I18n.translate("item.fireworksCharge.customColor");
                }
            }

            list.add(s);
        }

        int[] aint1 = nbt.getIntArray("FadeColors");
        if (aint1.length > 0) {
            boolean flag2 = true;
            String s1 = I18n.translate("item.fireworksCharge.fadeTo") + " ";

            for (int l : aint1) {
                if (!flag2) {
                    s1 = s1 + ", ";
                }

                flag2 = false;
                boolean flag5 = false;

                for (int k = 0; k < 16; k++) {
                    if (l == DyeItem.COLORS[k]) {
                        flag5 = true;
                        s1 = s1 + I18n.translate("item.fireworksCharge." + DyeColor.byMetadata(k).getName());
                        break;
                    }
                }

                if (!flag5) {
                    s1 = s1 + I18n.translate("item.fireworksCharge.customColor");
                }
            }

            list.add(s1);
        }

        boolean flag3 = nbt.getBoolean("Trail");
        if (flag3) {
            list.add(I18n.translate("item.fireworksCharge.trail"));
        }

        boolean flag4 = nbt.getBoolean("Flicker");
        if (flag4) {
            list.add(I18n.translate("item.fireworksCharge.flicker"));
        }
    }
}
