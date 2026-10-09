package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.FlowerPotBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.locale.I18n;
import net.minecraft.stat.Stats;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class FlowerPotBlock extends BlockWithBlockEntity {
    public static final IntegerProperty LEGACY_DATA = IntegerProperty.of("legacy_data", 0, 15);
    public static final EnumProperty<FlowerPotBlock.Contents> CONTENTS = EnumProperty.of("contents", FlowerPotBlock.Contents.class);

    public FlowerPotBlock() {
        super(Material.DECORATION);
        this.setDefaultState(this.stateDefinition.any().set(CONTENTS, FlowerPotBlock.Contents.EMPTY).set(LEGACY_DATA, 0));
        this.resetShape();
    }

    @Override
    public String getName() {
        return I18n.translate("item.flowerPot.name");
    }

    @Override
    public void resetShape() {
        float f = 0.375F;
        float f1 = f / 2.0F;
        this.setShape(0.5F - f1, 0.0F, 0.5F - f1, 0.5F + f1, f, 0.5F + f1);
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
    public boolean isCube() {
        return false;
    }

    @Override
    public int getColor(WorldView world, BlockPos pos, int tint) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof FlowerPotBlockEntity) {
            Item item = ((FlowerPotBlockEntity)blockentity).getPlant();
            if (item instanceof BlockItem) {
                return Block.byItem(item).getColor(world, pos, tint);
            }
        }

        return 16777215;
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        ItemStack itemstack = player.inventory.getSelectedItem();
        if (itemstack != null && itemstack.getItem() instanceof BlockItem) {
            FlowerPotBlockEntity flowerpotblockentity = this.getFlowerPotBlockEntity(world, pos);
            if (flowerpotblockentity == null) {
                return false;
            }

            if (flowerpotblockentity.getPlant() != null) {
                return false;
            }

            Block block = Block.byItem(itemstack.getItem());
            if (!this.isPottablePlant(block, itemstack.getMetadata())) {
                return false;
            }

            flowerpotblockentity.setPlant(itemstack.getItem(), itemstack.getMetadata());
            flowerpotblockentity.markDirty();
            world.notifyBlockChanged(pos);
            player.incrementStat(Stats.FLOWERS_POTTED);
            if (!player.abilities.creativeMode && --itemstack.size <= 0) {
                player.inventory.setItem(player.inventory.selectedSlot, null);
            }

            return true;
        } else {
            return false;
        }
    }

    private boolean isPottablePlant(Block block, int metadata) {
        return block == Blocks.YELLOW_FLOWER
            || block == Blocks.RED_FLOWER
            || block == Blocks.CACTUS
            || block == Blocks.BROWN_MUSHROOM
            || block == Blocks.RED_MUSHROOM
            || block == Blocks.SAPLING
            || block == Blocks.DEADBUSH
            || block == Blocks.TALLGRASS && metadata == TallPlantBlock.Type.FERN.getId();
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        FlowerPotBlockEntity flowerpotblockentity = this.getFlowerPotBlockEntity(world, pos);
        return flowerpotblockentity != null && flowerpotblockentity.getPlant() != null ? flowerpotblockentity.getPlant() : Items.FLOWER_POT;
    }

    @Override
    public int getPickItemMetadata(World world, BlockPos pos) {
        FlowerPotBlockEntity flowerpotblockentity = this.getFlowerPotBlockEntity(world, pos);
        return flowerpotblockentity != null && flowerpotblockentity.getPlant() != null ? flowerpotblockentity.getMetadata() : 0;
    }

    @Override
    public boolean hasPickItemMetadata() {
        return true;
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return super.canBePlaced(world, pos) && World.hasSolidTop(world, pos.down());
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!World.hasSolidTop(world, pos.down())) {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);
        }
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        FlowerPotBlockEntity flowerpotblockentity = this.getFlowerPotBlockEntity(world, pos);
        if (flowerpotblockentity != null && flowerpotblockentity.getPlant() != null) {
            dropItem(world, pos, new ItemStack(flowerpotblockentity.getPlant(), 1, flowerpotblockentity.getMetadata()));
        }

        super.onRemoved(world, pos, state);
    }

    @Override
    public void beforeMinedByPlayer(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        super.beforeMinedByPlayer(world, pos, state, player);
        if (player.abilities.creativeMode) {
            FlowerPotBlockEntity flowerpotblockentity = this.getFlowerPotBlockEntity(world, pos);
            if (flowerpotblockentity != null) {
                flowerpotblockentity.setPlant(null, 0);
            }
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.FLOWER_POT;
    }

    private FlowerPotBlockEntity getFlowerPotBlockEntity(World world, BlockPos x) {
        BlockEntity blockentity = world.getBlockEntity(x);
        return blockentity instanceof FlowerPotBlockEntity ? (FlowerPotBlockEntity)blockentity : null;
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        Block block = null;
        int i = 0;
        switch (metadata) {
            case 1:
                block = Blocks.RED_FLOWER;
                i = FlowerBlock.Type.POPPY.getId();
                break;
            case 2:
                block = Blocks.YELLOW_FLOWER;
                break;
            case 3:
                block = Blocks.SAPLING;
                i = PlanksBlock.Variant.OAK.getId();
                break;
            case 4:
                block = Blocks.SAPLING;
                i = PlanksBlock.Variant.SPRUCE.getId();
                break;
            case 5:
                block = Blocks.SAPLING;
                i = PlanksBlock.Variant.BIRCH.getId();
                break;
            case 6:
                block = Blocks.SAPLING;
                i = PlanksBlock.Variant.JUNGLE.getId();
                break;
            case 7:
                block = Blocks.RED_MUSHROOM;
                break;
            case 8:
                block = Blocks.BROWN_MUSHROOM;
                break;
            case 9:
                block = Blocks.CACTUS;
                break;
            case 10:
                block = Blocks.DEADBUSH;
                break;
            case 11:
                block = Blocks.TALLGRASS;
                i = TallPlantBlock.Type.FERN.getId();
                break;
            case 12:
                block = Blocks.SAPLING;
                i = PlanksBlock.Variant.ACACIA.getId();
                break;
            case 13:
                block = Blocks.SAPLING;
                i = PlanksBlock.Variant.DARK_OAK.getId();
        }

        return new FlowerPotBlockEntity(Item.byBlock(block), i);
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, CONTENTS, LEGACY_DATA);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(LEGACY_DATA);
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        FlowerPotBlock.Contents flowerpotblock$contents = FlowerPotBlock.Contents.EMPTY;
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof FlowerPotBlockEntity) {
            FlowerPotBlockEntity flowerpotblockentity = (FlowerPotBlockEntity)blockentity;
            Item item = flowerpotblockentity.getPlant();
            if (item instanceof BlockItem) {
                int i = flowerpotblockentity.getMetadata();
                Block block = Block.byItem(item);
                if (block == Blocks.SAPLING) {
                    switch (PlanksBlock.Variant.byId(i)) {
                        case OAK:
                            flowerpotblock$contents = FlowerPotBlock.Contents.OAK_SAPLING;
                            break;
                        case SPRUCE:
                            flowerpotblock$contents = FlowerPotBlock.Contents.SPRUCE_SAPLING;
                            break;
                        case BIRCH:
                            flowerpotblock$contents = FlowerPotBlock.Contents.BIRCH_SAPLING;
                            break;
                        case JUNGLE:
                            flowerpotblock$contents = FlowerPotBlock.Contents.JUNGLE_SAPLING;
                            break;
                        case ACACIA:
                            flowerpotblock$contents = FlowerPotBlock.Contents.ACACIA_SAPLING;
                            break;
                        case DARK_OAK:
                            flowerpotblock$contents = FlowerPotBlock.Contents.DARK_OAK_SAPLING;
                            break;
                        default:
                            flowerpotblock$contents = FlowerPotBlock.Contents.EMPTY;
                    }
                } else if (block == Blocks.TALLGRASS) {
                    switch (i) {
                        case 0:
                            flowerpotblock$contents = FlowerPotBlock.Contents.DEAD_BUSH;
                            break;
                        case 2:
                            flowerpotblock$contents = FlowerPotBlock.Contents.FERN;
                            break;
                        default:
                            flowerpotblock$contents = FlowerPotBlock.Contents.EMPTY;
                    }
                } else if (block == Blocks.YELLOW_FLOWER) {
                    flowerpotblock$contents = FlowerPotBlock.Contents.DANDELION;
                } else if (block == Blocks.RED_FLOWER) {
                    switch (FlowerBlock.Type.byId(FlowerBlock.Group.RED, i)) {
                        case POPPY:
                            flowerpotblock$contents = FlowerPotBlock.Contents.POPPY;
                            break;
                        case BLUE_ORCHID:
                            flowerpotblock$contents = FlowerPotBlock.Contents.BLUE_ORCHID;
                            break;
                        case ALLIUM:
                            flowerpotblock$contents = FlowerPotBlock.Contents.ALLIUM;
                            break;
                        case HOUSTONIA:
                            flowerpotblock$contents = FlowerPotBlock.Contents.HOUSTONIA;
                            break;
                        case RED_TULIP:
                            flowerpotblock$contents = FlowerPotBlock.Contents.RED_TULIP;
                            break;
                        case ORANGE_TULIP:
                            flowerpotblock$contents = FlowerPotBlock.Contents.ORANGE_TULIP;
                            break;
                        case WHITE_TULIP:
                            flowerpotblock$contents = FlowerPotBlock.Contents.WHITE_TULIP;
                            break;
                        case PINK_TULIP:
                            flowerpotblock$contents = FlowerPotBlock.Contents.PINK_TULIP;
                            break;
                        case OXEY_DAISY:
                            flowerpotblock$contents = FlowerPotBlock.Contents.OXEYE_DAISY;
                            break;
                        default:
                            flowerpotblock$contents = FlowerPotBlock.Contents.EMPTY;
                    }
                } else if (block == Blocks.RED_MUSHROOM) {
                    flowerpotblock$contents = FlowerPotBlock.Contents.MUSHROOM_RED;
                } else if (block == Blocks.BROWN_MUSHROOM) {
                    flowerpotblock$contents = FlowerPotBlock.Contents.MUSHROOM_BROWN;
                } else if (block == Blocks.DEADBUSH) {
                    flowerpotblock$contents = FlowerPotBlock.Contents.DEAD_BUSH;
                } else if (block == Blocks.CACTUS) {
                    flowerpotblock$contents = FlowerPotBlock.Contents.CACTUS;
                }
            }
        }

        return state.set(CONTENTS, flowerpotblock$contents);
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    public enum Contents implements StringSerializable {
        EMPTY("empty"),
        POPPY("rose"),
        BLUE_ORCHID("blue_orchid"),
        ALLIUM("allium"),
        HOUSTONIA("houstonia"),
        RED_TULIP("red_tulip"),
        ORANGE_TULIP("orange_tulip"),
        WHITE_TULIP("white_tulip"),
        PINK_TULIP("pink_tulip"),
        OXEYE_DAISY("oxeye_daisy"),
        DANDELION("dandelion"),
        OAK_SAPLING("oak_sapling"),
        SPRUCE_SAPLING("spruce_sapling"),
        BIRCH_SAPLING("birch_sapling"),
        JUNGLE_SAPLING("jungle_sapling"),
        ACACIA_SAPLING("acacia_sapling"),
        DARK_OAK_SAPLING("dark_oak_sapling"),
        MUSHROOM_RED("mushroom_red"),
        MUSHROOM_BROWN("mushroom_brown"),
        DEAD_BUSH("dead_bush"),
        FERN("fern"),
        CACTUS("cactus");

        private final String name;

        Contents(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }

        @Override
        public String serializeToString() {
            return this.name;
        }
    }
}
