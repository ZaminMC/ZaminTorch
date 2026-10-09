package net.minecraft.client.entity.living.player;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.CommandBlockScreen;
import net.minecraft.client.gui.screen.inventory.BookEditScreen;
import net.minecraft.client.gui.screen.inventory.menu.AnvilScreen;
import net.minecraft.client.gui.screen.inventory.menu.BeaconScreen;
import net.minecraft.client.gui.screen.inventory.menu.BrewingStandScreen;
import net.minecraft.client.gui.screen.inventory.menu.ChestScreen;
import net.minecraft.client.gui.screen.inventory.menu.CraftingTableScreen;
import net.minecraft.client.gui.screen.inventory.menu.DispenserScreen;
import net.minecraft.client.gui.screen.inventory.menu.EnchantingTableScreen;
import net.minecraft.client.gui.screen.inventory.menu.FurnaceScreen;
import net.minecraft.client.gui.screen.inventory.menu.HopperScreen;
import net.minecraft.client.gui.screen.inventory.menu.HorseScreen;
import net.minecraft.client.gui.screen.inventory.menu.SignEditScreen;
import net.minecraft.client.gui.screen.inventory.menu.VillagerScreen;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.sound.instance.MinecartWithPlayerSoundInstance;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.MenuProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseInventoryMenuC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMovementActionC2SPacket;
import net.minecraft.resource.Identifier;
import net.minecraft.server.command.source.CommandExecutor;
import net.minecraft.stat.PlayerStats;
import net.minecraft.stat.Stat;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.village.trade.Trader;

public class LocalClientPlayerEntity extends ClientPlayerEntity {
    public final ClientPlayNetworkHandler networkHandler;
    private final PlayerStats stats;
    /**
     * The last position sent to the server.
     * A new PlayerMoveC2SPacket.Position is sent when the current player position differs from the last position sent to the server by 0.03 blocks or more, or every 20 ticks.
     * @see #sendMovementToServer()
     */
    private double sentX;
    private double sentY;
    private double sentZ;
    /**
     * The last rotation sent to the server.
     * A new PlayerMoveC2SPacket.Angles is sent when the current player rotation differs from the last rotation sent to the server.
     * @see #sendMovementToServer
     */
    private float sentYaw;
    private float sentPitch;
    /**
     * The last sneaking status sent to the server.
     * @see #sendMovementToServer
     */
    private boolean sentSneaking;
    /**
     * The last sprinting status sent to the server.
     * This can easily desync with the serverside sprinting status.
     * @see #sendMovementToServer
     */
    private boolean sentSprinting;
    private int ticksSinceSentPosition;
    private boolean healthInitialized;
    private String serverBrand;
    public Input input;
    protected Minecraft minecraft;
    protected int doubleTapSprintTime;
    public int sprintTimer;
    public float easedYaw;
    public float easedPitch;
    public float lastEasedYaw;
    public float lastEasedPitch;
    private int horseJumpTimer;
    private float horseJumpSize;
    public float portalTime;
    public float lastPortalTime;

    public LocalClientPlayerEntity(Minecraft minecraft, World world, ClientPlayNetworkHandler networkHandler, PlayerStats statHandler) {
        super(world, networkHandler.getProfile());
        this.networkHandler = networkHandler;
        this.stats = statHandler;
        this.minecraft = minecraft;
        this.dimension = 0;
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void heal(float amount) {
    }

    @Override
    public void startRiding(Entity entity) {
        super.startRiding(entity);
        if (entity instanceof MinecartEntity) {
            this.minecraft.getSoundManager().play(new MinecartWithPlayerSoundInstance(this, (MinecartEntity)entity));
        }
    }

    @Override
    public void tick() {
        if (this.world.isChunkLoaded(new BlockPos(this.x, 0.0, this.z))) {
            super.tick();
            if (this.isRiding()) {
                this.networkHandler.sendPacket(new PlayerMoveC2SPacket.Angles(this.yaw, this.pitch, this.onGround));
                this.networkHandler.sendPacket(new PlayerInputC2SPacket(this.sidewaysSpeed, this.forwardSpeed, this.input.jumping, this.input.sneaking));
            } else {
                this.sendMovementToServer();
            }
        }
    }

    /**
     * Sends updated position, rotation, sneaking, and sprinting status to the server.
     */
    public void sendMovementToServer() {
        boolean flag = this.isSprinting();
        if (flag != this.sentSprinting) {
            if (flag) {
                this.networkHandler.sendPacket(new PlayerMovementActionC2SPacket(this, PlayerMovementActionC2SPacket.Action.START_SPRINTING));
            } else {
                this.networkHandler.sendPacket(new PlayerMovementActionC2SPacket(this, PlayerMovementActionC2SPacket.Action.STOP_SPRINTING));
            }

            this.sentSprinting = flag;
        }

        boolean flag1 = this.isSneaking();
        if (flag1 != this.sentSneaking) {
            if (flag1) {
                this.networkHandler.sendPacket(new PlayerMovementActionC2SPacket(this, PlayerMovementActionC2SPacket.Action.START_SNEAKING));
            } else {
                this.networkHandler.sendPacket(new PlayerMovementActionC2SPacket(this, PlayerMovementActionC2SPacket.Action.STOP_SNEAKING));
            }

            this.sentSneaking = flag1;
        }

        if (this.isCamera()) {
            double d0 = this.x - this.sentX;
            double d1 = this.getShape().minY - this.sentY;
            double d2 = this.z - this.sentZ;
            double d3 = this.yaw - this.sentYaw;
            double d4 = this.pitch - this.sentPitch;
            boolean flag2 = d0 * d0 + d1 * d1 + d2 * d2 > 9.0E-4 || this.ticksSinceSentPosition >= 20;
            boolean flag3 = d3 != 0.0 || d4 != 0.0;
            if (this.vehicle == null) {
                if (flag2 && flag3) {
                    this.networkHandler
                        .sendPacket(new PlayerMoveC2SPacket.PositionAndAngles(this.x, this.getShape().minY, this.z, this.yaw, this.pitch, this.onGround));
                } else if (flag2) {
                    this.networkHandler.sendPacket(new PlayerMoveC2SPacket.Position(this.x, this.getShape().minY, this.z, this.onGround));
                } else if (flag3) {
                    this.networkHandler.sendPacket(new PlayerMoveC2SPacket.Angles(this.yaw, this.pitch, this.onGround));
                } else {
                    this.networkHandler.sendPacket(new PlayerMoveC2SPacket(this.onGround));
                }
            } else {
                this.networkHandler
                    .sendPacket(new PlayerMoveC2SPacket.PositionAndAngles(this.velocityX, -999.0, this.velocityZ, this.yaw, this.pitch, this.onGround));
                flag2 = false;
            }

            this.ticksSinceSentPosition++;
            if (flag2) {
                this.sentX = this.x;
                this.sentY = this.getShape().minY;
                this.sentZ = this.z;
                this.ticksSinceSentPosition = 0;
            }

            if (flag3) {
                this.sentYaw = this.yaw;
                this.sentPitch = this.pitch;
            }
        }
    }

    @Override
    public ItemEntity dropItem(boolean whole) {
        PlayerHandActionC2SPacket.Action playerhandactionc2spacket$action = whole
            ? PlayerHandActionC2SPacket.Action.DROP_ALL_ITEMS
            : PlayerHandActionC2SPacket.Action.DROP_ITEM;
        this.networkHandler.sendPacket(new PlayerHandActionC2SPacket(playerhandactionc2spacket$action, BlockPos.ORIGIN, Direction.DOWN));
        return null;
    }

    @Override
    protected void spawnItem(ItemEntity item) {
    }

    public void sendChat(String message) {
        this.networkHandler.sendPacket(new ChatMessageC2SPacket(message));
    }

    @Override
    public void swingArm() {
        super.swingArm();
        this.networkHandler.sendPacket(new ArmSwingC2SPacket());
    }

    @Override
    public void respawn() {
        this.networkHandler.sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Status.PERFORM_RESPAWN));
    }

    @Override
    protected void applyDamage(DamageSource source, float damage) {
        if (!this.isInvulnerable(source)) {
            this.setHealth(this.getHealth() - damage);
        }
    }

    @Override
    public void closeMenu() {
        this.networkHandler.sendPacket(new CloseInventoryMenuC2SPacket(this.menu.networkId));
        this.doCloseMenu();
    }

    public void doCloseMenu() {
        this.inventory.setCursorItem(null);
        super.closeMenu();
        this.minecraft.openScreen(null);
    }

    public void damageTo(float health) {
        if (this.healthInitialized) {
            float f = this.getHealth() - health;
            if (f <= 0.0F) {
                this.setHealth(health);
                if (f < 0.0F) {
                    this.invulnerableTimer = this.invulnerableTicks / 2;
                }
            } else {
                this.lastDamageTaken = f;
                this.setHealth(this.getHealth());
                this.invulnerableTimer = this.invulnerableTicks;
                this.applyDamage(DamageSource.GENERIC, f);
                this.damagedTimer = this.damagedTime = 10;
            }
        } else {
            this.setHealth(health);
            this.healthInitialized = true;
        }
    }

    @Override
    public void incrementStat(Stat stat, int amount) {
        if (stat != null) {
            if (stat.local) {
                super.incrementStat(stat, amount);
            }
        }
    }

    @Override
    public void syncAbilities() {
        this.networkHandler.sendPacket(new PlayerAbilitiesC2SPacket(this.abilities));
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    protected void startRidingJump() {
        this.networkHandler
            .sendPacket(new PlayerMovementActionC2SPacket(this, PlayerMovementActionC2SPacket.Action.RIDING_JUMP, (int)(this.getRidingJumpProgress() * 100.0F)));
    }

    public void openRidingInventory() {
        this.networkHandler.sendPacket(new PlayerMovementActionC2SPacket(this, PlayerMovementActionC2SPacket.Action.OPEN_HORSE_INVENTORY));
    }

    public void setServerBrand(String serverBrand) {
        this.serverBrand = serverBrand;
    }

    public String getServerBrand() {
        return this.serverBrand;
    }

    public PlayerStats getStats() {
        return this.stats;
    }

    @Override
    public void addMessage(Text message) {
        this.minecraft.gui.getChat().addMessage(message);
    }

    @Override
    protected boolean pushAwayFrom(double x, double y, double z) {
        if (this.noClip) {
            return false;
        }

        BlockPos blockpos = new BlockPos(x, y, z);
        double d0 = x - blockpos.getX();
        double d1 = z - blockpos.getZ();
        if (!this.canSurvive(blockpos)) {
            int i = -1;
            double d2 = 9999.0;
            if (this.canSurvive(blockpos.west()) && d0 < d2) {
                d2 = d0;
                i = 0;
            }

            if (this.canSurvive(blockpos.east()) && 1.0 - d0 < d2) {
                d2 = 1.0 - d0;
                i = 1;
            }

            if (this.canSurvive(blockpos.north()) && d1 < d2) {
                d2 = d1;
                i = 4;
            }

            if (this.canSurvive(blockpos.south()) && 1.0 - d1 < d2) {
                d2 = 1.0 - d1;
                i = 5;
            }

            float f = 0.1F;
            if (i == 0) {
                this.velocityX = -f;
            }

            if (i == 1) {
                this.velocityX = f;
            }

            if (i == 4) {
                this.velocityZ = -f;
            }

            if (i == 5) {
                this.velocityZ = f;
            }
        }

        return false;
    }

    private boolean canSurvive(BlockPos pos) {
        return !this.world.getBlockState(pos).getBlock().isSolid() && !this.world.getBlockState(pos.up()).getBlock().isSolid();
    }

    @Override
    public void setSprinting(boolean sprinting) {
        super.setSprinting(sprinting);
        this.sprintTimer = sprinting ? 600 : 0;
    }

    public void setXp(float xpProgress, int xp, int xpLevel) {
        this.xpProgress = xpProgress;
        this.xp = xp;
        this.xpLevel = xpLevel;
    }

    @Override
    public void sendMessage(Text message) {
        this.minecraft.gui.getChat().addMessage(message);
    }

    @Override
    public boolean canUseCommand(int permissionLevel, String command) {
        return permissionLevel <= 0;
    }

    @Override
    public BlockPos getCommandSourceBlockPos() {
        return new BlockPos(this.x + 0.5, this.y + 0.5, this.z + 0.5);
    }

    @Override
    public void playSound(String id, float volume, float pitch) {
        this.world.playSound(this.x, this.y, this.z, id, volume, pitch, false);
    }

    @Override
    public boolean isLocallyControlled() {
        return true;
    }

    public boolean isRidingRideableMob() {
        return this.vehicle != null && this.vehicle instanceof HorseBaseEntity && ((HorseBaseEntity)this.vehicle).isSaddled();
    }

    public float getRidingJumpProgress() {
        return this.horseJumpSize;
    }

    @Override
    public void openSignEditor(SignBlockEntity sign) {
        this.minecraft.openScreen(new SignEditScreen(sign));
    }

    @Override
    public void openCommandBlockMenu(CommandExecutor commandBlock) {
        this.minecraft.openScreen(new CommandBlockScreen(commandBlock));
    }

    @Override
    public void openEditBookScreen(ItemStack book) {
        Item item = book.getItem();
        if (item == Items.WRITABLE_BOOK) {
            this.minecraft.openScreen(new BookEditScreen(this, book, true));
        }
    }

    @Override
    public void openChestMenu(Inventory inventory) {
        String s = inventory instanceof MenuProvider ? ((MenuProvider)inventory).getMenuType() : "minecraft:container";
        if ("minecraft:chest".equals(s)) {
            this.minecraft.openScreen(new ChestScreen(this.inventory, inventory));
        } else if ("minecraft:hopper".equals(s)) {
            this.minecraft.openScreen(new HopperScreen(this.inventory, inventory));
        } else if ("minecraft:furnace".equals(s)) {
            this.minecraft.openScreen(new FurnaceScreen(this.inventory, inventory));
        } else if ("minecraft:brewing_stand".equals(s)) {
            this.minecraft.openScreen(new BrewingStandScreen(this.inventory, inventory));
        } else if ("minecraft:beacon".equals(s)) {
            this.minecraft.openScreen(new BeaconScreen(this.inventory, inventory));
        } else if (!"minecraft:dispenser".equals(s) && !"minecraft:dropper".equals(s)) {
            this.minecraft.openScreen(new ChestScreen(this.inventory, inventory));
        } else {
            this.minecraft.openScreen(new DispenserScreen(this.inventory, inventory));
        }
    }

    @Override
    public void openHorseMenu(HorseBaseEntity horse, Inventory inventory) {
        this.minecraft.openScreen(new HorseScreen(this.inventory, inventory, horse));
    }

    @Override
    public void openMenu(MenuProvider menuProvider) {
        String s = menuProvider.getMenuType();
        if ("minecraft:crafting_table".equals(s)) {
            this.minecraft.openScreen(new CraftingTableScreen(this.inventory, this.world));
        } else if ("minecraft:enchanting_table".equals(s)) {
            this.minecraft.openScreen(new EnchantingTableScreen(this.inventory, this.world, menuProvider));
        } else if ("minecraft:anvil".equals(s)) {
            this.minecraft.openScreen(new AnvilScreen(this.inventory, this.world));
        }
    }

    @Override
    public void openTraderMenu(Trader trader) {
        this.minecraft.openScreen(new VillagerScreen(this.inventory, trader, this.world));
    }

    @Override
    public void addCritParticles(Entity entity) {
        this.minecraft.particleManager.addEmitter(entity, ParticleType.CRIT);
    }

    @Override
    public void addEnchantedCritParticles(Entity entity) {
        this.minecraft.particleManager.addEmitter(entity, ParticleType.CRIT_MAGIC);
    }

    @Override
    public boolean isSneaking() {
        boolean flag = this.input != null && this.input.sneaking;
        return flag && !this.sleeping;
    }

    @Override
    public void serverTickAi() {
        super.serverTickAi();
        if (this.isCamera()) {
            this.sidewaysSpeed = this.input.movementSideways;
            this.forwardSpeed = this.input.movementForward;
            this.jumping = this.input.jumping;
            this.lastEasedYaw = this.easedYaw;
            this.lastEasedPitch = this.easedPitch;
            this.easedPitch = (float)(this.easedPitch + (this.pitch - this.easedPitch) * 0.5);
            this.easedYaw = (float)(this.easedYaw + (this.yaw - this.easedYaw) * 0.5);
        }
    }

    protected boolean isCamera() {
        return this.minecraft.getCamera() == this;
    }

    @Override
    public void mobTick() {
        if (this.sprintTimer > 0) {
            this.sprintTimer--;
            if (this.sprintTimer == 0) {
                this.setSprinting(false);
            }
        }

        if (this.doubleTapSprintTime > 0) {
            this.doubleTapSprintTime--;
        }

        this.lastPortalTime = this.portalTime;
        if (this.inPortal) {
            if (this.minecraft.screen != null && !this.minecraft.screen.shouldPauseGame()) {
                this.minecraft.openScreen(null);
            }

            if (this.portalTime == 0.0F) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.of(new Identifier("portal.trigger"), this.random.nextFloat() * 0.4F + 0.8F));
            }

            this.portalTime += 0.0125F;
            if (this.portalTime >= 1.0F) {
                this.portalTime = 1.0F;
            }

            this.inPortal = false;
        } else if (this.hasStatusEffect(StatusEffect.NAUSEA) && this.getEffectInstance(StatusEffect.NAUSEA).getDuration() > 60) {
            this.portalTime += 0.006666667F;
            if (this.portalTime > 1.0F) {
                this.portalTime = 1.0F;
            }
        } else {
            if (this.portalTime > 0.0F) {
                this.portalTime -= 0.05F;
            }

            if (this.portalTime < 0.0F) {
                this.portalTime = 0.0F;
            }
        }

        if (this.portalCooldown > 0) {
            this.portalCooldown--;
        }

        boolean flag = this.input.jumping;
        boolean flag1 = this.input.sneaking;
        float f = 0.8F;
        boolean flag2 = this.input.movementForward >= f;
        this.input.tick();
        if (this.hasItemInUse() && !this.isRiding()) {
            this.input.movementSideways *= 0.2F;
            this.input.movementForward *= 0.2F;
            this.doubleTapSprintTime = 0;
        }

        this.pushAwayFrom(this.x - this.width * 0.35, this.getShape().minY + 0.5, this.z + this.width * 0.35);
        this.pushAwayFrom(this.x - this.width * 0.35, this.getShape().minY + 0.5, this.z - this.width * 0.35);
        this.pushAwayFrom(this.x + this.width * 0.35, this.getShape().minY + 0.5, this.z - this.width * 0.35);
        this.pushAwayFrom(this.x + this.width * 0.35, this.getShape().minY + 0.5, this.z + this.width * 0.35);
        boolean flag3 = this.getHungerManager().getFoodLevel() > 6.0F || this.abilities.canFly;
        if (this.onGround
            && !flag1
            && !flag2
            && this.input.movementForward >= f
            && !this.isSprinting()
            && flag3
            && !this.hasItemInUse()
            && !this.hasStatusEffect(StatusEffect.BLINDNESS)) {
            if (this.doubleTapSprintTime <= 0 && !this.minecraft.options.sprintKey.isPressed()) {
                this.doubleTapSprintTime = 7;
            } else {
                this.setSprinting(true);
            }
        }

        if (!this.isSprinting()
            && this.input.movementForward >= f
            && flag3
            && !this.hasItemInUse()
            && !this.hasStatusEffect(StatusEffect.BLINDNESS)
            && this.minecraft.options.sprintKey.isPressed()) {
            this.setSprinting(true);
        }

        if (this.isSprinting() && (this.input.movementForward < f || this.collidingHorizontally || !flag3)) {
            this.setSprinting(false);
        }

        if (this.abilities.canFly) {
            if (this.minecraft.interactionManager.isSpectator()) {
                if (!this.abilities.flying) {
                    this.abilities.flying = true;
                    this.syncAbilities();
                }
            } else if (!flag && this.input.jumping) {
                if (this.pressedJumpTwiceTimer == 0) {
                    this.pressedJumpTwiceTimer = 7;
                } else {
                    this.abilities.flying = !this.abilities.flying;
                    this.syncAbilities();
                    this.pressedJumpTwiceTimer = 0;
                }
            }
        }

        if (this.abilities.flying && this.isCamera()) {
            if (this.input.sneaking) {
                this.velocityY = this.velocityY - this.abilities.getFlySpeed() * 3.0F;
            }

            if (this.input.jumping) {
                this.velocityY = this.velocityY + this.abilities.getFlySpeed() * 3.0F;
            }
        }

        if (this.isRidingRideableMob()) {
            if (this.horseJumpTimer < 0) {
                this.horseJumpTimer++;
                if (this.horseJumpTimer == 0) {
                    this.horseJumpSize = 0.0F;
                }
            }

            if (flag && !this.input.jumping) {
                this.horseJumpTimer = -10;
                this.startRidingJump();
            } else if (!flag && this.input.jumping) {
                this.horseJumpTimer = 0;
                this.horseJumpSize = 0.0F;
            } else if (flag) {
                this.horseJumpTimer++;
                if (this.horseJumpTimer < 10) {
                    this.horseJumpSize = this.horseJumpTimer * 0.1F;
                } else {
                    this.horseJumpSize = 0.8F + 2.0F / (this.horseJumpTimer - 9) * 0.1F;
                }
            }
        } else {
            this.horseJumpSize = 0.0F;
        }

        super.mobTick();
        if (this.onGround && this.abilities.flying && !this.minecraft.interactionManager.isSpectator()) {
            this.abilities.flying = false;
            this.syncAbilities();
        }
    }
}
