package net.minecraft.item;

import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.spawner.MobSpawner;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.locale.I18n;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class SpawnEggItem extends Item {
    public SpawnEggItem() {
        this.setHasCustomData(true);
        this.setCreativeModeTab(CreativeModeTab.MISC);
    }

    @Override
    public String getName(ItemStack stack) {
        String s = ("" + I18n.translate(this.getTranslationKey() + ".name")).trim();
        String s1 = Entities.getKey(stack.getMetadata());
        if (s1 != null) {
            s = s + " " + I18n.translate("entity." + s1 + ".name");
        }

        return s;
    }

    @Override
    public int getDisplayColor(ItemStack stack, int stage) {
        Entities.SpawnEggData entities$spawneggdata = Entities.SPAWN_EGG_DATA.get(stack.getMetadata());
        if (entities$spawneggdata != null) {
            return stage == 0 ? entities$spawneggdata.baseColor : entities$spawneggdata.spotsColor;
        } else {
            return 16777215;
        }
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        if (!player.canUseItemOn(pos.offset(face), face, stack)) {
            return false;
        }

        BlockState blockstate = world.getBlockState(pos);
        if (blockstate.getBlock() == Blocks.MOB_SPAWNER) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof MobSpawnerBlockEntity) {
                MobSpawner mobspawner = ((MobSpawnerBlockEntity)blockentity).getSpawner();
                mobspawner.setType(Entities.getKey(stack.getMetadata()));
                blockentity.markDirty();
                world.notifyBlockChanged(pos);
                if (!player.abilities.creativeMode) {
                    stack.size--;
                }

                return true;
            }
        }

        pos = pos.offset(face);
        double d0 = 0.0;
        if (face == Direction.UP && blockstate instanceof FenceBlock) {
            d0 = 0.5;
        }

        Entity entity = spawnEntity(world, stack.getMetadata(), pos.getX() + 0.5, pos.getY() + d0, pos.getZ() + 0.5);
        if (entity != null) {
            if (entity instanceof LivingEntity && stack.hasCustomHoverName()) {
                entity.setCustomName(stack.getHoverName());
            }

            if (!player.abilities.creativeMode) {
                stack.size--;
            }
        }

        return true;
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        if (world.isClient) {
            return stack;
        }

        HitResult hitresult = this.getUseTarget(world, player, true);
        if (hitresult == null) {
            return stack;
        }

        if (hitresult.type == HitResult.Type.BLOCK) {
            BlockPos blockpos = hitresult.getPos();
            if (!world.canModify(player, blockpos)) {
                return stack;
            }

            if (!player.canUseItemOn(blockpos, hitresult.face, stack)) {
                return stack;
            }

            if (world.getBlockState(blockpos).getBlock() instanceof LiquidBlock) {
                Entity entity = spawnEntity(world, stack.getMetadata(), blockpos.getX() + 0.5, blockpos.getY() + 0.5, blockpos.getZ() + 0.5);
                if (entity != null) {
                    if (entity instanceof LivingEntity && stack.hasCustomHoverName()) {
                        ((MobEntity)entity).setCustomName(stack.getHoverName());
                    }

                    if (!player.abilities.creativeMode) {
                        stack.size--;
                    }

                    player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
                }
            }
        }

        return stack;
    }

    public static Entity spawnEntity(World world, int id, double x, double y, double z) {
        if (!Entities.SPAWN_EGG_DATA.containsKey(id)) {
            return null;
        }

        Entity entity = null;

        for (int i = 0; i < 1; i++) {
            entity = Entities.create(id, world);
            if (entity instanceof LivingEntity) {
                MobEntity mobentity = (MobEntity)entity;
                entity.setPositionAndAngles(x, y, z, MathHelper.wrapDegrees(world.random.nextFloat() * 360.0F), 0.0F);
                mobentity.headYaw = mobentity.yaw;
                mobentity.bodyYaw = mobentity.yaw;
                mobentity.initialize(world.getLocalDifficulty(new BlockPos(mobentity)), null);
                world.addEntity(entity);
                mobentity.playAmbientSound();
            }
        }

        return entity;
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (Entities.SpawnEggData entities$spawneggdata : Entities.SPAWN_EGG_DATA.values()) {
            inventory.add(new ItemStack(item, 1, entities$spawneggdata.id));
        }
    }
}
