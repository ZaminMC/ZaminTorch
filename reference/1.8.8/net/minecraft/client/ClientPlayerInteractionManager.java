package net.minecraft.client;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.CreativeMenuSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.InventoryMenuClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.MenuClickButtonC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectSlotC2SPacket;
import net.minecraft.resource.Identifier;
import net.minecraft.stat.PlayerStats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;

public class ClientPlayerInteractionManager {
    private final Minecraft minecraft;
    private final ClientPlayNetworkHandler networkHandler;
    private BlockPos target = new BlockPos(-1, -1, -1);
    private ItemStack miningTool;
    private float miningProgress;
    private float miningSoundTimer;
    private int miningCooldown;
    private boolean isMiningBlock;
    private WorldSettings.GameMode gameMode = WorldSettings.GameMode.SURVIVAL;
    private int selectedHotbarSlot;

    public ClientPlayerInteractionManager(Minecraft minecraft, ClientPlayNetworkHandler networkHandler) {
        this.minecraft = minecraft;
        this.networkHandler = networkHandler;
    }

    public static void instaMineBlock(Minecraft minecraft, ClientPlayerInteractionManager interactionManager, BlockPos pos, Direction face) {
        if (!minecraft.world.extinguishFire(minecraft.player, pos, face)) {
            interactionManager.finishMiningBlock(pos, face);
        }
    }

    public void adjustPlayer(PlayerEntity player) {
        this.gameMode.apply(player.abilities);
    }

    public boolean hidesGui() {
        return this.gameMode == WorldSettings.GameMode.SPECTATOR;
    }

    public void setGameMode(WorldSettings.GameMode gameMode) {
        this.gameMode = gameMode;
        this.gameMode.apply(this.minecraft.player.abilities);
    }

    public void initPlayer(PlayerEntity player) {
        player.yaw = -180.0F;
    }

    public boolean hasStatusBars() {
        return this.gameMode.isSurvival();
    }

    public boolean finishMiningBlock(BlockPos pos, Direction face) {
        if (this.gameMode.restrictsWorldModification()) {
            if (this.gameMode == WorldSettings.GameMode.SPECTATOR) {
                return false;
            }

            if (!this.minecraft.player.canModifyWorld()) {
                Block block = this.minecraft.world.getBlockState(pos).getBlock();
                ItemStack itemstack = this.minecraft.player.getItemInHand();
                if (itemstack == null) {
                    return false;
                }

                if (!itemstack.hasMineBlockOverride(block)) {
                    return false;
                }
            }
        }

        if (this.gameMode.isCreative()
            && this.minecraft.player.getDisplayItemInHand() != null
            && this.minecraft.player.getDisplayItemInHand().getItem() instanceof SwordItem) {
            return false;
        }

        World world = this.minecraft.world;
        BlockState blockstate = world.getBlockState(pos);
        Block block1 = blockstate.getBlock();
        if (block1.getMaterial() == Material.AIR) {
            return false;
        }

        world.doEvent(2001, pos, Block.serialize(blockstate));
        boolean flag = world.removeBlock(pos);
        if (flag) {
            block1.onBroken(world, pos, blockstate);
        }

        this.target = new BlockPos(this.target.getX(), -1, this.target.getZ());
        if (!this.gameMode.isCreative()) {
            ItemStack itemstack1 = this.minecraft.player.getItemInHand();
            if (itemstack1 != null) {
                itemstack1.mineBlock(world, block1, pos, this.minecraft.player);
                if (itemstack1.size == 0) {
                    this.minecraft.player.clearItemInHand();
                }
            }
        }

        return flag;
    }

    public boolean startMiningBlock(BlockPos pos, Direction face) {
        if (this.gameMode.restrictsWorldModification()) {
            if (this.gameMode == WorldSettings.GameMode.SPECTATOR) {
                return false;
            }

            if (!this.minecraft.player.canModifyWorld()) {
                Block block = this.minecraft.world.getBlockState(pos).getBlock();
                ItemStack itemstack = this.minecraft.player.getItemInHand();
                if (itemstack == null) {
                    return false;
                }

                if (!itemstack.hasMineBlockOverride(block)) {
                    return false;
                }
            }
        }

        if (!this.minecraft.world.getWorldBorder().contains(pos)) {
            return false;
        }

        if (this.gameMode.isCreative()) {
            this.networkHandler.sendPacket(new PlayerHandActionC2SPacket(PlayerHandActionC2SPacket.Action.START_DESTROY_BLOCK, pos, face));
            instaMineBlock(this.minecraft, this, pos, face);
            this.miningCooldown = 5;
        } else if (!this.isMiningBlock || !this.isMiningBlock(pos)) {
            if (this.isMiningBlock) {
                this.networkHandler.sendPacket(new PlayerHandActionC2SPacket(PlayerHandActionC2SPacket.Action.ABORT_DESTROY_BLOCK, this.target, face));
            }

            this.networkHandler.sendPacket(new PlayerHandActionC2SPacket(PlayerHandActionC2SPacket.Action.START_DESTROY_BLOCK, pos, face));
            Block block1 = this.minecraft.world.getBlockState(pos).getBlock();
            boolean flag = block1.getMaterial() != Material.AIR;
            if (flag && this.miningProgress == 0.0F) {
                block1.startMining(this.minecraft.world, pos, this.minecraft.player);
            }

            if (flag && block1.getMiningSpeed(this.minecraft.player, this.minecraft.player.world, pos) >= 1.0F) {
                this.finishMiningBlock(pos, face);
            } else {
                this.isMiningBlock = true;
                this.target = pos;
                this.miningTool = this.minecraft.player.getDisplayItemInHand();
                this.miningProgress = 0.0F;
                this.miningSoundTimer = 0.0F;
                this.minecraft.world.updateBlockMiningProgress(this.minecraft.player.getNetworkId(), this.target, (int)(this.miningProgress * 10.0F) - 1);
            }
        }

        return true;
    }

    public void stopMiningBlock() {
        if (this.isMiningBlock) {
            this.networkHandler.sendPacket(new PlayerHandActionC2SPacket(PlayerHandActionC2SPacket.Action.ABORT_DESTROY_BLOCK, this.target, Direction.DOWN));
            this.isMiningBlock = false;
            this.miningProgress = 0.0F;
            this.minecraft.world.updateBlockMiningProgress(this.minecraft.player.getNetworkId(), this.target, -1);
        }
    }

    public boolean tickBlockMining(BlockPos pos, Direction face) {
        this.updateSelectedHotbarSlot();
        if (this.miningCooldown > 0) {
            this.miningCooldown--;
            return true;
        }

        if (this.gameMode.isCreative() && this.minecraft.world.getWorldBorder().contains(pos)) {
            this.miningCooldown = 5;
            this.networkHandler.sendPacket(new PlayerHandActionC2SPacket(PlayerHandActionC2SPacket.Action.START_DESTROY_BLOCK, pos, face));
            instaMineBlock(this.minecraft, this, pos, face);
            return true;
        }

        if (this.isMiningBlock(pos)) {
            Block block = this.minecraft.world.getBlockState(pos).getBlock();
            if (block.getMaterial() == Material.AIR) {
                this.isMiningBlock = false;
                return false;
            }

            this.miningProgress = this.miningProgress + block.getMiningSpeed(this.minecraft.player, this.minecraft.player.world, pos);
            if (this.miningSoundTimer % 4.0F == 0.0F) {
                this.minecraft
                    .getSoundManager()
                    .play(
                        new SimpleSoundInstance(
                            new Identifier(block.sounds.getStepping()),
                            (block.sounds.getVolume() + 1.0F) / 8.0F,
                            block.sounds.getPitch() * 0.5F,
                            pos.getX() + 0.5F,
                            pos.getY() + 0.5F,
                            pos.getZ() + 0.5F
                        )
                    );
            }

            this.miningSoundTimer++;
            if (this.miningProgress >= 1.0F) {
                this.isMiningBlock = false;
                this.networkHandler.sendPacket(new PlayerHandActionC2SPacket(PlayerHandActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, face));
                this.finishMiningBlock(pos, face);
                this.miningProgress = 0.0F;
                this.miningSoundTimer = 0.0F;
                this.miningCooldown = 5;
            }

            this.minecraft.world.updateBlockMiningProgress(this.minecraft.player.getNetworkId(), this.target, (int)(this.miningProgress * 10.0F) - 1);
            return true;
        } else {
            return this.startMiningBlock(pos, face);
        }
    }

    public float getReach() {
        return this.gameMode.isCreative() ? 5.0F : 4.5F;
    }

    public void tick() {
        this.updateSelectedHotbarSlot();
        if (this.networkHandler.getConnection().isConnected()) {
            this.networkHandler.getConnection().tick();
        } else {
            this.networkHandler.getConnection().handleDisconnection();
        }
    }

    private boolean isMiningBlock(BlockPos pos) {
        ItemStack itemstack = this.minecraft.player.getDisplayItemInHand();
        boolean flag = this.miningTool == null && itemstack == null;
        if (this.miningTool != null && itemstack != null) {
            flag = itemstack.getItem() == this.miningTool.getItem()
                && ItemStack.matchesNbt(itemstack, this.miningTool)
                && (itemstack.isDamageable() || itemstack.getMetadata() == this.miningTool.getMetadata());
        }

        return pos.equals(this.target) && flag;
    }

    private void updateSelectedHotbarSlot() {
        int i = this.minecraft.player.inventory.selectedSlot;
        if (i != this.selectedHotbarSlot) {
            this.selectedHotbarSlot = i;
            this.networkHandler.sendPacket(new SelectSlotC2SPacket(this.selectedHotbarSlot));
        }
    }

    public boolean useBlock(LocalClientPlayerEntity player, ClientWorld world, ItemStack itemInHand, BlockPos pos, Direction face, Vec3d facePos) {
        this.updateSelectedHotbarSlot();
        float f = (float)(facePos.x - pos.getX());
        float f1 = (float)(facePos.y - pos.getY());
        float f2 = (float)(facePos.z - pos.getZ());
        boolean flag = false;
        if (!this.minecraft.world.getWorldBorder().contains(pos)) {
            return false;
        }

        if (this.gameMode != WorldSettings.GameMode.SPECTATOR) {
            BlockState blockstate = world.getBlockState(pos);
            if ((!player.isSneaking() || player.getDisplayItemInHand() == null) && blockstate.getBlock().use(world, pos, blockstate, player, face, f, f1, f2)) {
                flag = true;
            }

            if (!flag && itemInHand != null && itemInHand.getItem() instanceof BlockItem) {
                BlockItem blockitem = (BlockItem)itemInHand.getItem();
                if (!blockitem.onPlace(world, pos, face, player, itemInHand)) {
                    return false;
                }
            }
        }

        this.networkHandler.sendPacket(new PlayerUseC2SPacket(pos, face.getId(), player.inventory.getSelectedItem(), f, f1, f2));
        if (flag || this.gameMode == WorldSettings.GameMode.SPECTATOR) {
            return true;
        } else if (itemInHand == null) {
            return false;
        } else if (this.gameMode.isCreative()) {
            int i = itemInHand.getMetadata();
            int j = itemInHand.size;
            boolean flag1 = itemInHand.useOn(player, world, pos, face, f, f1, f2);
            itemInHand.setDamage(i);
            itemInHand.size = j;
            return flag1;
        } else {
            return itemInHand.useOn(player, world, pos, face, f, f1, f2);
        }
    }

    public boolean useItem(PlayerEntity player, World world, ItemStack itemInHand) {
        if (this.gameMode == WorldSettings.GameMode.SPECTATOR) {
            return false;
        }

        this.updateSelectedHotbarSlot();
        this.networkHandler.sendPacket(new PlayerUseC2SPacket(player.inventory.getSelectedItem()));
        int i = itemInHand.size;
        ItemStack itemstack = itemInHand.startUsing(world, player);
        if (itemstack != itemInHand || itemstack != null && itemstack.size != i) {
            player.inventory.items[player.inventory.selectedSlot] = itemstack;
            if (itemstack.size == 0) {
                player.inventory.items[player.inventory.selectedSlot] = null;
            }

            return true;
        } else {
            return false;
        }
    }

    public LocalClientPlayerEntity createPlayer(World world, PlayerStats stats) {
        return new LocalClientPlayerEntity(this.minecraft, world, this.networkHandler, stats);
    }

    public void attackEntity(PlayerEntity player, Entity target) {
        this.updateSelectedHotbarSlot();
        this.networkHandler.sendPacket(new PlayerInteractEntityC2SPacket(target, PlayerInteractEntityC2SPacket.Action.ATTACK));
        if (this.gameMode != WorldSettings.GameMode.SPECTATOR) {
            player.attack(target);
        }
    }

    public boolean interactEntity(PlayerEntity player, Entity target) {
        this.updateSelectedHotbarSlot();
        this.networkHandler.sendPacket(new PlayerInteractEntityC2SPacket(target, PlayerInteractEntityC2SPacket.Action.INTERACT));
        return this.gameMode != WorldSettings.GameMode.SPECTATOR && player.interact(target);
    }

    public boolean interactEntityAt(PlayerEntity player, Entity target, HitResult hit) {
        this.updateSelectedHotbarSlot();
        Vec3d vec3d = new Vec3d(hit.facePos.x - target.x, hit.facePos.y - target.y, hit.facePos.z - target.z);
        this.networkHandler.sendPacket(new PlayerInteractEntityC2SPacket(target, vec3d));
        return this.gameMode != WorldSettings.GameMode.SPECTATOR && target.interactAt(player, vec3d);
    }

    public ItemStack clickSlot(int syncId, int slotId, int clickData, int actionType, PlayerEntity player) {
        short short1 = player.menu.nextInteractionId(player.inventory);
        ItemStack itemstack = player.menu.onClickSlot(slotId, clickData, actionType, player);
        this.networkHandler.sendPacket(new InventoryMenuClickSlotC2SPacket(syncId, slotId, clickData, actionType, itemstack, short1));
        return itemstack;
    }

    public void clickMenuButton(int menuId, int buttonId) {
        this.networkHandler.sendPacket(new MenuClickButtonC2SPacket(menuId, buttonId));
    }

    public void addItemToCreativeMenu(ItemStack item, int slot) {
        if (this.gameMode.isCreative()) {
            this.networkHandler.sendPacket(new CreativeMenuSlotC2SPacket(slot, item));
        }
    }

    public void dropItemFromCreativeMenu(ItemStack item) {
        if (this.gameMode.isCreative() && item != null) {
            this.networkHandler.sendPacket(new CreativeMenuSlotC2SPacket(-1, item));
        }
    }

    public void stopUsingHand(PlayerEntity player) {
        this.updateSelectedHotbarSlot();
        this.networkHandler.sendPacket(new PlayerHandActionC2SPacket(PlayerHandActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
        player.stopUsingItem();
    }

    public boolean hasXpBar() {
        return this.gameMode.isSurvival();
    }

    public boolean hasAttackCooldown() {
        return !this.gameMode.isCreative();
    }

    public boolean hasCreativeInventory() {
        return this.gameMode.isCreative();
    }

    public boolean hasExtendedReach() {
        return this.gameMode.isCreative();
    }

    public boolean hasRidingInventory() {
        return this.minecraft.player.isRiding() && this.minecraft.player.vehicle instanceof HorseBaseEntity;
    }

    public boolean isSpectator() {
        return this.gameMode == WorldSettings.GameMode.SPECTATOR;
    }

    public WorldSettings.GameMode getGameMode() {
        return this.gameMode;
    }

    public boolean isMiningBlock() {
        return this.isMiningBlock;
    }
}
