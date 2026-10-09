package net.minecraft.item;

import com.mojang.authlib.GameProfile;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class SkullItem extends Item {
    private static final String[] SKULL_TYPES = new String[]{"skeleton", "wither", "zombie", "char", "creeper"};

    public SkullItem() {
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
        this.setMaxDamage(0);
        this.setHasCustomData(true);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (face == Direction.DOWN) {
            return false;
        }

        BlockState blockstate = world.getBlockState(pos);
        Block block = blockstate.getBlock();
        boolean flag = block.canBeReplaced(world, pos);
        if (!flag) {
            if (!world.getBlockState(pos).getBlock().getMaterial().isSolid()) {
                return false;
            }

            pos = pos.offset(face);
        }

        if (!player.canUseItemOn(pos, face, stack)) {
            return false;
        }

        if (!Blocks.SKULL.canBePlaced(world, pos)) {
            return false;
        }

        if (!world.isClient) {
            world.setBlockState(pos, Blocks.SKULL.defaultState().set(SkullBlock.FACING, face), 3);
            int i = 0;
            if (face == Direction.UP) {
                i = MathHelper.floor(player.yaw * 16.0F / 360.0F + 0.5) & 15;
            }

            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof SkullBlockEntity) {
                SkullBlockEntity skullblockentity = (SkullBlockEntity)blockentity;
                if (stack.getMetadata() == 3) {
                    GameProfile gameprofile = null;
                    if (stack.hasNbt()) {
                        NbtCompound nbtcompound = stack.getNbt();
                        if (nbtcompound.contains("SkullOwner", 10)) {
                            gameprofile = NbtUtils.readProfile(nbtcompound.getCompound("SkullOwner"));
                        } else if (nbtcompound.contains("SkullOwner", 8) && nbtcompound.getString("SkullOwner").length() > 0) {
                            gameprofile = new GameProfile(null, nbtcompound.getString("SkullOwner"));
                        }
                    }

                    skullblockentity.setProfile(gameprofile);
                } else {
                    skullblockentity.setSkullType(stack.getMetadata());
                }

                skullblockentity.setRotation(i);
                Blocks.SKULL.trySpawn(world, pos, skullblockentity);
            }

            stack.size--;
        }

        return true;
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (int i = 0; i < SKULL_TYPES.length; i++) {
            inventory.add(new ItemStack(item, 1, i));
        }
    }

    @Override
    public int getBlockMetadata(int metadata) {
        return metadata;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        int i = stack.getMetadata();
        if (i < 0 || i >= SKULL_TYPES.length) {
            i = 0;
        }

        return super.getTranslationKey() + "." + SKULL_TYPES[i];
    }

    @Override
    public String getName(ItemStack stack) {
        if (stack.getMetadata() == 3 && stack.hasNbt()) {
            if (stack.getNbt().contains("SkullOwner", 8)) {
                return I18n.translate("item.skull.player.name", stack.getNbt().getString("SkullOwner"));
            }

            if (stack.getNbt().contains("SkullOwner", 10)) {
                NbtCompound nbtcompound = stack.getNbt().getCompound("SkullOwner");
                if (nbtcompound.contains("Name", 8)) {
                    return I18n.translate("item.skull.player.name", nbtcompound.getString("Name"));
                }
            }
        }

        return super.getName(stack);
    }

    @Override
    public boolean validateNbt(NbtCompound nbt) {
        super.validateNbt(nbt);
        if (nbt.contains("SkullOwner", 8) && nbt.getString("SkullOwner").length() > 0) {
            GameProfile gameprofile = new GameProfile(null, nbt.getString("SkullOwner"));
            gameprofile = SkullBlockEntity.updateProfile(gameprofile);
            nbt.put("SkullOwner", NbtUtils.writeProfile(new NbtCompound(), gameprofile));
            return true;
        } else {
            return false;
        }
    }
}
