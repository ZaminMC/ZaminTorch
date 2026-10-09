package net.minecraft.client.render.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.SkullRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.math.Direction;

public class BlockEntityItemRenderer {
    public static BlockEntityItemRenderer INSTANCE = new BlockEntityItemRenderer();
    private ChestBlockEntity chest = new ChestBlockEntity(0);
    private ChestBlockEntity trappedChest = new ChestBlockEntity(1);
    private EnderChestBlockEntity enderChest = new EnderChestBlockEntity();
    private BannerBlockEntity banner = new BannerBlockEntity();
    private SkullBlockEntity skull = new SkullBlockEntity();

    public void render(ItemStack item) {
        if (item.getItem() == Items.BANNER) {
            this.banner.set(item);
            BlockEntityRenderDispatcher.INSTANCE.render(this.banner, 0.0, 0.0, 0.0, 0.0F);
        } else if (item.getItem() == Items.SKULL) {
            GameProfile gameprofile = null;
            if (item.hasNbt()) {
                NbtCompound nbtcompound = item.getNbt();
                if (nbtcompound.contains("SkullOwner", 10)) {
                    gameprofile = NbtUtils.readProfile(nbtcompound.getCompound("SkullOwner"));
                } else if (nbtcompound.contains("SkullOwner", 8) && nbtcompound.getString("SkullOwner").length() > 0) {
                    GameProfile gameprofile1 = new GameProfile(null, nbtcompound.getString("SkullOwner"));
                    gameprofile = SkullBlockEntity.updateProfile(gameprofile1);
                    nbtcompound.remove("SkullOwner");
                    nbtcompound.put("SkullOwner", NbtUtils.writeProfile(new NbtCompound(), gameprofile));
                }
            }

            if (SkullRenderer.INSTANCE != null) {
                GlStateManager.pushMatrix();
                GlStateManager.translatef(-0.5F, 0.0F, -0.5F);
                GlStateManager.scalef(2.0F, 2.0F, 2.0F);
                GlStateManager.disableCull();
                SkullRenderer.INSTANCE.render(0.0F, 0.0F, 0.0F, Direction.UP, 0.0F, item.getMetadata(), gameprofile, -1);
                GlStateManager.enableCull();
                GlStateManager.popMatrix();
            }
        } else {
            Block block = Block.byItem(item.getItem());
            if (block == Blocks.ENDER_CHEST) {
                BlockEntityRenderDispatcher.INSTANCE.render(this.enderChest, 0.0, 0.0, 0.0, 0.0F);
            } else if (block == Blocks.TRAPPED_CHEST) {
                BlockEntityRenderDispatcher.INSTANCE.render(this.trappedChest, 0.0, 0.0, 0.0, 0.0F);
            } else {
                BlockEntityRenderDispatcher.INSTANCE.render(this.chest, 0.0, 0.0, 0.0, 0.0F);
            }
        }
    }
}
