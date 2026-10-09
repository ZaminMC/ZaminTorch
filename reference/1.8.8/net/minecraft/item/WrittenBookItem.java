package net.minecraft.item;

import java.util.List;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.s2c.play.InventoryMenuSlotContentS2CPacket;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringUtils;
import net.minecraft.text.Text;
import net.minecraft.text.TextUtils;
import net.minecraft.world.World;

public class WrittenBookItem extends Item {
    public WrittenBookItem() {
        this.setMaxStackSize(1);
    }

    public static boolean isValid(NbtCompound tag) {
        if (!BookAndQuillItem.isValid(tag)) {
            return false;
        }

        if (!tag.contains("title", 8)) {
            return false;
        }

        String s = tag.getString("title");
        return s != null && s.length() <= 32 && tag.contains("author", 8);
    }

    public static int getGeneration(ItemStack stack) {
        return stack.getNbt().getInt("generation");
    }

    @Override
    public String getName(ItemStack stack) {
        if (stack.hasNbt()) {
            NbtCompound nbtcompound = stack.getNbt();
            String s = nbtcompound.getString("title");
            if (!StringUtils.isStringEmpty(s)) {
                return s;
            }
        }

        return super.getName(stack);
    }

    @Override
    public void addHoverText(ItemStack stack, PlayerEntity player, List<String> tooltip, boolean advanced) {
        if (stack.hasNbt()) {
            NbtCompound nbtcompound = stack.getNbt();
            String s = nbtcompound.getString("author");
            if (!StringUtils.isStringEmpty(s)) {
                tooltip.add(Formatting.GRAY + I18n.translate("book.byAuthor", s));
            }

            tooltip.add(Formatting.GRAY + I18n.translate("book.generation." + nbtcompound.getInt("generation")));
        }
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        if (!world.isClient) {
            this.resolvePages(stack, player);
        }

        player.openEditBookScreen(stack);
        player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
        return stack;
    }

    private void resolvePages(ItemStack item, PlayerEntity player) {
        if (item != null && item.getNbt() != null) {
            NbtCompound nbtcompound = item.getNbt();
            if (!nbtcompound.getBoolean("resolved")) {
                nbtcompound.putBoolean("resolved", true);
                if (isValid(nbtcompound)) {
                    NbtList nbtlist = nbtcompound.getList("pages", 8);

                    for (int i = 0; i < nbtlist.size(); i++) {
                        String s = nbtlist.getString(i);

                        Text text;
                        try {
                            text = Text.Serializer.fromJson(s);
                            text = TextUtils.updateForEntity(player, text, player);
                        } catch (Exception exception) {
                            text = new LiteralText(s);
                        }

                        nbtlist.setElement(i, new NbtString(Text.Serializer.toJson(text)));
                    }

                    nbtcompound.put("pages", nbtlist);
                    if (player instanceof ServerPlayerEntity && player.getItemInHand() == item) {
                        InventorySlot inventoryslot = player.menu.getSlot(player.inventory, player.inventory.selectedSlot);
                        ((ServerPlayerEntity)player).networkHandler.sendPacket(new InventoryMenuSlotContentS2CPacket(0, inventoryslot.index, item));
                    }
                }
            }
        }
    }

    @Override
    public boolean hasEnchantmentGlint(ItemStack stack) {
        return true;
    }
}
