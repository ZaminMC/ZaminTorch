package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.inventory.InventoryUtils;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.locale.I18n;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class BrewingStandBlock extends BlockWithBlockEntity {
    public static final BooleanProperty[] HAS_BOTTLE = new BooleanProperty[]{
        BooleanProperty.of("has_bottle_0"), BooleanProperty.of("has_bottle_1"), BooleanProperty.of("has_bottle_2")
    };

    public BrewingStandBlock() {
        super(Material.IRON);
        this.setDefaultState(this.stateDefinition.any().set(HAS_BOTTLE[0], false).set(HAS_BOTTLE[1], false).set(HAS_BOTTLE[2], false));
    }

    @Override
    public String getName() {
        return I18n.translate("item.brewingStand.name");
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public int getRenderType() {
        return 3;
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new BrewingStandBlockEntity();
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public void addCollisions(World world, BlockPos pos, BlockState state, Box shape, List<Box> collisions, Entity entity) {
        this.setShape(0.4375F, 0.0F, 0.4375F, 0.5625F, 0.875F, 0.5625F);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        this.resetShape();
        super.addCollisions(world, pos, state, shape, collisions, entity);
    }

    @Override
    public void resetShape() {
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.125F, 1.0F);
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof BrewingStandBlockEntity) {
            player.openChestMenu((BrewingStandBlockEntity)blockentity);
            player.incrementStat(Stats.BREWING_STAND_INTERACTIONS);
        }

        return true;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
        if (item.hasCustomHoverName()) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof BrewingStandBlockEntity) {
                ((BrewingStandBlockEntity)blockentity).setCustomName(item.getHoverName());
            }
        }
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        double d0 = pos.getX() + 0.4F + random.nextFloat() * 0.2F;
        double d1 = pos.getY() + 0.7F + random.nextFloat() * 0.3F;
        double d2 = pos.getZ() + 0.4F + random.nextFloat() * 0.2F;
        world.addParticle(ParticleType.SMOKE_NORMAL, d0, d1, d2, 0.0, 0.0, 0.0);
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof BrewingStandBlockEntity) {
            InventoryUtils.dropItems(world, pos, (BrewingStandBlockEntity)blockentity);
        }

        super.onRemoved(world, pos, state);
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.BREWING_STAND;
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Items.BREWING_STAND;
    }

    @Override
    public boolean isAnalogSignalSource() {
        return true;
    }

    @Override
    public int getAnalogSignal(World world, BlockPos pos) {
        return InventoryMenu.getAnalogSignal(world.getBlockEntity(pos));
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        BlockState blockstate = this.defaultState();

        for (int i = 0; i < 3; i++) {
            blockstate = blockstate.set(HAS_BOTTLE[i], (metadata & 1 << i) > 0);
        }

        return blockstate;
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;

        for (int j = 0; j < 3; j++) {
            if (state.get(HAS_BOTTLE[j])) {
                i |= 1 << j;
            }
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, HAS_BOTTLE[0], HAS_BOTTLE[1], HAS_BOTTLE[2]);
    }
}
