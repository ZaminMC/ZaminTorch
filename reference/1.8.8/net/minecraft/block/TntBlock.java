package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.PrimedTntEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

public class TntBlock extends Block {
    public static final BooleanProperty EXPLODE = BooleanProperty.of("explode");

    public TntBlock() {
        super(Material.TNT);
        this.setDefaultState(this.stateDefinition.any().set(EXPLODE, false));
        this.setCreativeModeTab(CreativeModeTab.REDSTONE);
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        super.onAdded(world, pos, state);
        if (world.hasNeighborSignal(pos)) {
            this.onBroken(world, pos, state.set(EXPLODE, true));
            world.removeBlock(pos);
        }
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (world.hasNeighborSignal(pos)) {
            this.onBroken(world, pos, state.set(EXPLODE, true));
            world.removeBlock(pos);
        }
    }

    @Override
    public void onExploded(World world, BlockPos pos, Explosion explosion) {
        if (!world.isClient) {
            PrimedTntEntity primedtntentity = new PrimedTntEntity(world, pos.getX() + 0.5F, pos.getY(), pos.getZ() + 0.5F, explosion.getSource());
            primedtntentity.fuseTimer = world.random.nextInt(primedtntentity.fuseTimer / 4) + primedtntentity.fuseTimer / 8;
            world.addEntity(primedtntentity);
        }
    }

    @Override
    public void onBroken(World world, BlockPos pos, BlockState state) {
        this.ignite(world, pos, state, null);
    }

    public void ignite(World world, BlockPos pos, BlockState state, LivingEntity igniter) {
        if (!world.isClient) {
            if (state.get(EXPLODE)) {
                PrimedTntEntity primedtntentity = new PrimedTntEntity(world, pos.getX() + 0.5F, pos.getY(), pos.getZ() + 0.5F, igniter);
                world.addEntity(primedtntentity);
                world.playSound(primedtntentity, "game.tnt.primed", 1.0F, 1.0F);
            }
        }
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (player.getItemInHand() != null) {
            Item item = player.getItemInHand().getItem();
            if (item == Items.FLINT_AND_STEEL || item == Items.FIRE_CHARGE) {
                this.ignite(world, pos, state.set(EXPLODE, true), player);
                world.removeBlock(pos);
                if (item == Items.FLINT_AND_STEEL) {
                    player.getItemInHand().takeDamageAndBreak(1, player);
                } else if (!player.abilities.creativeMode) {
                    player.getItemInHand().size--;
                }

                return true;
            }
        }

        return super.use(world, pos, state, player, face, faceX, faceY, faceZ);
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClient && entity instanceof ArrowEntity) {
            ArrowEntity arrowentity = (ArrowEntity)entity;
            if (arrowentity.isOnFire()) {
                this.ignite(
                    world,
                    pos,
                    world.getBlockState(pos).set(EXPLODE, true),
                    arrowentity.shooter instanceof LivingEntity ? (LivingEntity)arrowentity.shooter : null
                );
                world.removeBlock(pos);
            }
        }
    }

    @Override
    public boolean shouldDropItemsOnExplosion(Explosion explosion) {
        return false;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(EXPLODE, (metadata & 1) > 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(EXPLODE) ? 1 : 0;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, EXPLODE);
    }
}
