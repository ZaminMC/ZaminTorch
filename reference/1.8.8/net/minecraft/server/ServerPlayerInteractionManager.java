package net.minecraft.server;

import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.LockableMenuProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerInfoS2CPacket;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;

public class ServerPlayerInteractionManager {
    public World world;
    public ServerPlayerEntity player;
    private WorldSettings.GameMode gameMode = WorldSettings.GameMode.NOT_SET;
    private boolean isMiningBlock;
    private int miningStartTime;
    private BlockPos target = BlockPos.ORIGIN;
    private int ticks;
    private boolean wasMiningBlock;
    private BlockPos prevTarget = BlockPos.ORIGIN;
    private int prevMiningStartTime;
    private int prevMiningProgress = -1;

    public ServerPlayerInteractionManager(World world) {
        this.world = world;
    }

    public void setGameMode(WorldSettings.GameMode gameMode) {
        this.gameMode = gameMode;
        gameMode.apply(this.player.abilities);
        this.player.syncAbilities();
        this.player.server.getPlayerManager().sendPacket(new PlayerInfoS2CPacket(PlayerInfoS2CPacket.Action.UPDATE_GAME_MODE, this.player));
    }

    public WorldSettings.GameMode getGameMode() {
        return this.gameMode;
    }

    public boolean isSurvival() {
        return this.gameMode.isSurvival();
    }

    public boolean isCreative() {
        return this.gameMode.isCreative();
    }

    public void setGameModeIfNotSet(WorldSettings.GameMode gameMode) {
        if (this.gameMode == WorldSettings.GameMode.NOT_SET) {
            this.gameMode = gameMode;
        }

        this.setGameMode(this.gameMode);
    }

    public void tick() {
        this.ticks++;
        if (this.wasMiningBlock) {
            int i = this.ticks - this.prevMiningStartTime;
            Block block = this.world.getBlockState(this.prevTarget).getBlock();
            if (block.getMaterial() == Material.AIR) {
                this.wasMiningBlock = false;
            } else {
                float f = block.getMiningSpeed(this.player, this.player.world, this.prevTarget) * (i + 1);
                int j = (int)(f * 10.0F);
                if (j != this.prevMiningProgress) {
                    this.world.updateBlockMiningProgress(this.player.getNetworkId(), this.prevTarget, j);
                    this.prevMiningProgress = j;
                }

                if (f >= 1.0F) {
                    this.wasMiningBlock = false;
                    this.tryMineBlock(this.prevTarget);
                }
            }
        } else if (this.isMiningBlock) {
            Block block1 = this.world.getBlockState(this.target).getBlock();
            if (block1.getMaterial() == Material.AIR) {
                this.world.updateBlockMiningProgress(this.player.getNetworkId(), this.target, -1);
                this.prevMiningProgress = -1;
                this.isMiningBlock = false;
            } else {
                int k = this.ticks - this.miningStartTime;
                float f1 = block1.getMiningSpeed(this.player, this.player.world, this.prevTarget) * (k + 1);
                int l = (int)(f1 * 10.0F);
                if (l != this.prevMiningProgress) {
                    this.world.updateBlockMiningProgress(this.player.getNetworkId(), this.target, l);
                    this.prevMiningProgress = l;
                }
            }
        }
    }

    public void startMiningBlock(BlockPos pos, Direction face) {
        if (this.isCreative()) {
            if (!this.world.extinguishFire(null, pos, face)) {
                this.tryMineBlock(pos);
            }
        } else {
            Block block = this.world.getBlockState(pos).getBlock();
            if (this.gameMode.restrictsWorldModification()) {
                if (this.gameMode == WorldSettings.GameMode.SPECTATOR) {
                    return;
                }

                if (!this.player.canModifyWorld()) {
                    ItemStack itemstack = this.player.getItemInHand();
                    if (itemstack == null) {
                        return;
                    }

                    if (!itemstack.hasMineBlockOverride(block)) {
                        return;
                    }
                }
            }

            this.world.extinguishFire(null, pos, face);
            this.miningStartTime = this.ticks;
            float f = 1.0F;
            if (block.getMaterial() != Material.AIR) {
                block.startMining(this.world, pos, this.player);
                f = block.getMiningSpeed(this.player, this.player.world, pos);
            }

            if (block.getMaterial() != Material.AIR && f >= 1.0F) {
                this.tryMineBlock(pos);
            } else {
                this.isMiningBlock = true;
                this.target = pos;
                int i = (int)(f * 10.0F);
                this.world.updateBlockMiningProgress(this.player.getNetworkId(), pos, i);
                this.prevMiningProgress = i;
            }
        }
    }

    public void finishMiningBlock(BlockPos pos) {
        if (pos.equals(this.target)) {
            int i = this.ticks - this.miningStartTime;
            Block block = this.world.getBlockState(pos).getBlock();
            if (block.getMaterial() != Material.AIR) {
                float f = block.getMiningSpeed(this.player, this.player.world, pos) * (i + 1);
                if (f >= 0.7F) {
                    this.isMiningBlock = false;
                    this.world.updateBlockMiningProgress(this.player.getNetworkId(), pos, -1);
                    this.tryMineBlock(pos);
                } else if (!this.wasMiningBlock) {
                    this.isMiningBlock = false;
                    this.wasMiningBlock = true;
                    this.prevTarget = pos;
                    this.prevMiningStartTime = this.miningStartTime;
                }
            }
        }
    }

    public void stopMiningBlock() {
        this.isMiningBlock = false;
        this.world.updateBlockMiningProgress(this.player.getNetworkId(), this.target, -1);
    }

    private boolean mineBlock(BlockPos pos) {
        BlockState blockstate = this.world.getBlockState(pos);
        blockstate.getBlock().beforeMinedByPlayer(this.world, pos, blockstate, this.player);
        boolean flag = this.world.removeBlock(pos);
        if (flag) {
            blockstate.getBlock().onBroken(this.world, pos, blockstate);
        }

        return flag;
    }

    public boolean tryMineBlock(BlockPos pos) {
        if (this.gameMode.isCreative() && this.player.getDisplayItemInHand() != null && this.player.getDisplayItemInHand().getItem() instanceof SwordItem) {
            return false;
        }

        BlockState blockstate = this.world.getBlockState(pos);
        BlockEntity blockentity = this.world.getBlockEntity(pos);
        if (this.gameMode.restrictsWorldModification()) {
            if (this.gameMode == WorldSettings.GameMode.SPECTATOR) {
                return false;
            }

            if (!this.player.canModifyWorld()) {
                ItemStack itemstack = this.player.getItemInHand();
                if (itemstack == null) {
                    return false;
                }

                if (!itemstack.hasMineBlockOverride(blockstate.getBlock())) {
                    return false;
                }
            }
        }

        this.world.doEvent(this.player, 2001, pos, Block.serialize(blockstate));
        boolean flag1 = this.mineBlock(pos);
        if (this.isCreative()) {
            this.player.networkHandler.sendPacket(new BlockUpdateS2CPacket(this.world, pos));
        } else {
            ItemStack itemstack1 = this.player.getItemInHand();
            boolean flag = this.player.canMineBlock(blockstate.getBlock());
            if (itemstack1 != null) {
                itemstack1.mineBlock(this.world, blockstate.getBlock(), pos, this.player);
                if (itemstack1.size == 0) {
                    this.player.clearItemInHand();
                }
            }

            if (flag1 && flag) {
                blockstate.getBlock().afterMinedByPlayer(this.world, this.player, pos, blockstate, blockentity);
            }
        }

        return flag1;
    }

    public boolean useItem(PlayerEntity player, World world, ItemStack itemInHand) {
        if (this.gameMode == WorldSettings.GameMode.SPECTATOR) {
            return false;
        }

        int i = itemInHand.size;
        int j = itemInHand.getMetadata();
        ItemStack itemstack = itemInHand.startUsing(world, player);
        if (itemstack != itemInHand || itemstack != null && (itemstack.size != i || itemstack.getUseDuration() > 0 || itemstack.getMetadata() != j)) {
            player.inventory.items[player.inventory.selectedSlot] = itemstack;
            if (this.isCreative()) {
                itemstack.size = i;
                if (itemstack.isDamageable()) {
                    itemstack.setDamage(j);
                }
            }

            if (itemstack.size == 0) {
                player.inventory.items[player.inventory.selectedSlot] = null;
            }

            if (!player.hasItemInUse()) {
                ((ServerPlayerEntity)player).setMenu(player.playerMenu);
            }

            return true;
        } else {
            return false;
        }
    }

    public boolean useBlock(PlayerEntity player, World world, ItemStack itemInHand, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (this.gameMode == WorldSettings.GameMode.SPECTATOR) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof LockableMenuProvider) {
                Block block = world.getBlockState(pos).getBlock();
                LockableMenuProvider lockablemenuprovider = (LockableMenuProvider)blockentity;
                if (lockablemenuprovider instanceof ChestBlockEntity && block instanceof ChestBlock) {
                    lockablemenuprovider = ((ChestBlock)block).getInventory(world, pos);
                }

                if (lockablemenuprovider != null) {
                    player.openChestMenu(lockablemenuprovider);
                    return true;
                }
            } else if (blockentity instanceof Inventory) {
                player.openChestMenu((Inventory)blockentity);
                return true;
            }

            return false;
        } else {
            if (!player.isSneaking() || player.getDisplayItemInHand() == null) {
                BlockState blockstate = world.getBlockState(pos);
                if (blockstate.getBlock().use(world, pos, blockstate, player, face, faceX, faceY, faceZ)) {
                    return true;
                }
            }

            if (itemInHand == null) {
                return false;
            } else if (this.isCreative()) {
                int j = itemInHand.getMetadata();
                int i = itemInHand.size;
                boolean flag = itemInHand.useOn(player, world, pos, face, faceX, faceY, faceZ);
                itemInHand.setDamage(j);
                itemInHand.size = i;
                return flag;
            } else {
                return itemInHand.useOn(player, world, pos, face, faceX, faceY, faceZ);
            }
        }
    }

    public void setWorld(ServerWorld world) {
        this.world = world;
    }
}
