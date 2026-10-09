package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BannerItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class CauldronBlock extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.of("level", 0, 3);

    public CauldronBlock() {
        super(Material.IRON, MapColor.STONE);
        this.setDefaultState(this.stateDefinition.any().set(LEVEL, 0));
    }

    @Override
    public void addCollisions(World world, BlockPos pos, BlockState state, Box shape, List<Box> collisions, Entity entity) {
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.3125F, 1.0F);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        float f = 0.125F;
        this.setShape(0.0F, 0.0F, 0.0F, f, 1.0F, 1.0F);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, f);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        this.setShape(1.0F - f, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        this.setShape(0.0F, 0.0F, 1.0F - f, 1.0F, 1.0F, 1.0F);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        this.resetShape();
    }

    @Override
    public void resetShape() {
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, BlockState state, Entity entity) {
        int i = state.get(LEVEL);
        float f = pos.getY() + (6.0F + 3 * i) / 16.0F;
        if (!world.isClient && entity.isOnFire() && i > 0 && entity.getShape().minY <= f) {
            entity.extinguish();
            this.setLevel(world, pos, state, i - 1);
        }
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        ItemStack itemstack = player.inventory.getSelectedItem();
        if (itemstack == null) {
            return true;
        }

        int i = state.get(LEVEL);
        Item item = itemstack.getItem();
        if (item == Items.WATER_BUCKET) {
            if (i < 3) {
                if (!player.abilities.creativeMode) {
                    player.inventory.setItem(player.inventory.selectedSlot, new ItemStack(Items.BUCKET));
                }

                player.incrementStat(Stats.CAULDRONS_FILLED);
                this.setLevel(world, pos, state, 3);
            }

            return true;
        } else if (item == Items.GLASS_BOTTLE) {
            if (i > 0) {
                if (!player.abilities.creativeMode) {
                    ItemStack itemstack2 = new ItemStack(Items.POTION, 1, 0);
                    if (!player.inventory.addItem(itemstack2)) {
                        world.addEntity(new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, itemstack2));
                    } else if (player instanceof ServerPlayerEntity) {
                        ((ServerPlayerEntity)player).setMenu(player.playerMenu);
                    }

                    player.incrementStat(Stats.CAULDRONS_USED);
                    itemstack.size--;
                    if (itemstack.size <= 0) {
                        player.inventory.setItem(player.inventory.selectedSlot, null);
                    }
                }

                this.setLevel(world, pos, state, i - 1);
            }

            return true;
        } else {
            if (i > 0 && item instanceof ArmorItem) {
                ArmorItem armoritem = (ArmorItem)item;
                if (armoritem.getTier() == ArmorItem.Tier.CLOTH && armoritem.hasColor(itemstack)) {
                    armoritem.removeColor(itemstack);
                    this.setLevel(world, pos, state, i - 1);
                    player.incrementStat(Stats.ARMOR_CLEANED);
                    return true;
                }
            }

            if (i > 0 && item instanceof BannerItem && BannerBlockEntity.getPatternCount(itemstack) > 0) {
                ItemStack itemstack1 = itemstack.copy();
                itemstack1.size = 1;
                BannerBlockEntity.removeLastPattern(itemstack1);
                if (itemstack.size <= 1 && !player.abilities.creativeMode) {
                    player.inventory.setItem(player.inventory.selectedSlot, itemstack1);
                } else {
                    if (!player.inventory.addItem(itemstack1)) {
                        world.addEntity(new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, itemstack1));
                    } else if (player instanceof ServerPlayerEntity) {
                        ((ServerPlayerEntity)player).setMenu(player.playerMenu);
                    }

                    player.incrementStat(Stats.BANNER_CLEANED);
                    if (!player.abilities.creativeMode) {
                        itemstack.size--;
                    }
                }

                if (!player.abilities.creativeMode) {
                    this.setLevel(world, pos, state, i - 1);
                }

                return true;
            } else {
                return false;
            }
        }
    }

    public void setLevel(World world, BlockPos pos, BlockState state, int level) {
        world.setBlockState(pos, state.set(LEVEL, MathHelper.clamp(level, 0, 3)), 2);
        world.updateNeighborComparators(pos, this);
    }

    @Override
    public void randomPrecipitationTick(World world, BlockPos pos) {
        if (world.random.nextInt(20) == 1) {
            BlockState blockstate = world.getBlockState(pos);
            if (blockstate.get(LEVEL) < 3) {
                world.setBlockState(pos, blockstate.next(LEVEL), 2);
            }
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.CAULDRON;
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Items.CAULDRON;
    }

    @Override
    public boolean isAnalogSignalSource() {
        return true;
    }

    @Override
    public int getAnalogSignal(World world, BlockPos pos) {
        return world.getBlockState(pos).get(LEVEL);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(LEVEL, metadata);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(LEVEL);
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, LEVEL);
    }
}
