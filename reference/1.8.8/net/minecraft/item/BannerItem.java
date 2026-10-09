package net.minecraft.item;

import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.block.StandingSignBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class BannerItem extends BlockItem {
    public BannerItem() {
        super(Blocks.STANDING_BANNER);
        this.maxStackSize = 16;
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
        this.setHasCustomData(true);
        this.setMaxDamage(0);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (face == Direction.DOWN) {
            return false;
        }

        if (!world.getBlockState(pos).getBlock().getMaterial().isSolid()) {
            return false;
        }

        pos = pos.offset(face);
        if (!player.canUseItemOn(pos, face, stack)) {
            return false;
        }

        if (!Blocks.STANDING_BANNER.canBePlaced(world, pos)) {
            return false;
        }

        if (world.isClient) {
            return true;
        }

        if (face == Direction.UP) {
            int i = MathHelper.floor((player.yaw + 180.0F) * 16.0F / 360.0F + 0.5) & 15;
            world.setBlockState(pos, Blocks.STANDING_BANNER.defaultState().set(StandingSignBlock.ROTATION, i), 3);
        } else {
            world.setBlockState(pos, Blocks.WALL_BANNER.defaultState().set(WallSignBlock.FACING, face), 3);
        }

        stack.size--;
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof BannerBlockEntity) {
            ((BannerBlockEntity)blockentity).set(stack);
        }

        return true;
    }

    @Override
    public String getName(ItemStack stack) {
        String s = "item.banner.";
        DyeColor dyecolor = this.getBaseColor(stack);
        s = s + dyecolor.getName() + ".name";
        return I18n.translate(s);
    }

    @Override
    public void addHoverText(ItemStack stack, PlayerEntity player, List<String> tooltip, boolean advanced) {
        NbtCompound nbtcompound = stack.getNbt("BlockEntityTag", false);
        if (nbtcompound != null && nbtcompound.contains("Patterns")) {
            NbtList nbtlist = nbtcompound.getList("Patterns", 10);

            for (int i = 0; i < nbtlist.size() && i < 6; i++) {
                NbtCompound nbtcompound1 = nbtlist.getCompound(i);
                DyeColor dyecolor = DyeColor.byMetadata(nbtcompound1.getInt("Color"));
                BannerBlockEntity.Pattern bannerblockentity$pattern = BannerBlockEntity.Pattern.byHash(nbtcompound1.getString("Pattern"));
                if (bannerblockentity$pattern != null) {
                    tooltip.add(I18n.translate("item.banner." + bannerblockentity$pattern.getKey() + "." + dyecolor.getName()));
                }
            }
        }
    }

    @Override
    public int getDisplayColor(ItemStack stack, int stage) {
        if (stage == 0) {
            return 16777215;
        }

        DyeColor dyecolor = this.getBaseColor(stack);
        return dyecolor.getMapColor().color;
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (DyeColor dyecolor : DyeColor.values()) {
            NbtCompound nbtcompound = new NbtCompound();
            BannerBlockEntity.writeNbt(nbtcompound, dyecolor.getMetadata(), null);
            NbtCompound nbtcompound1 = new NbtCompound();
            nbtcompound1.put("BlockEntityTag", nbtcompound);
            ItemStack itemstack = new ItemStack(item, 1, dyecolor.getMetadata());
            itemstack.setNbt(nbtcompound1);
            inventory.add(itemstack);
        }
    }

    @Override
    public CreativeModeTab getCreativeModeTab() {
        return CreativeModeTab.DECORATIONS;
    }

    private DyeColor getBaseColor(ItemStack stack) {
        NbtCompound nbtcompound = stack.getNbt("BlockEntityTag", false);
        DyeColor dyecolor = null;
        if (nbtcompound != null && nbtcompound.contains("Base")) {
            dyecolor = DyeColor.byMetadata(nbtcompound.getInt("Base"));
        } else {
            dyecolor = DyeColor.byMetadata(stack.getMetadata());
        }

        return dyecolor;
    }
}
