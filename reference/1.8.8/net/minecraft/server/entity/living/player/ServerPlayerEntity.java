package net.minecraft.server.entity.living.player;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.EntityDamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.ChestMenu;
import net.minecraft.inventory.menu.HorseMenu;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.menu.InventoryMenuListener;
import net.minecraft.inventory.menu.LockableMenuProvider;
import net.minecraft.inventory.menu.MenuProvider;
import net.minecraft.inventory.menu.TraderMenu;
import net.minecraft.inventory.slot.CraftingResultSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.NetworkSyncedItem;
import net.minecraft.item.UseAction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientSettingsC2SPacket;
import net.minecraft.network.packet.s2c.play.AttachEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.CameraS2CPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.CloseInventoryMenuS2CPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityRemoveStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.GameEventS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuContentS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuDataS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuSlotContentS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenInventoryMenuS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenSignEditorS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerCombatS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerHealthS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSleepS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerXpS2CPacket;
import net.minecraft.network.packet.s2c.play.RemoveEntitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.ResourcePackS2CPacket;
import net.minecraft.network.packet.s2c.play.SoundEventS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldChunkS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldChunksS2CPacket;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.OpEntry;
import net.minecraft.server.ServerPlayerInteractionManager;
import net.minecraft.server.network.handler.ServerPlayNetworkHandler;
import net.minecraft.server.stat.ServerPlayerStats;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.AchievementProgress;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.village.trade.TradeOffers;
import net.minecraft.world.village.trade.Trader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerPlayerEntity extends PlayerEntity implements InventoryMenuListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private String language = "en_US";
    public ServerPlayNetworkHandler networkHandler;
    public final MinecraftServer server;
    public final ServerPlayerInteractionManager interactionManager;
    public double trackedX;
    public double trackedZ;
    /**
     * Coordinates of chunks that have come within view of the player, but have not yet
     * been sent to the client.
     */
    public final List<ChunkPos> pendingChunks = Lists.newLinkedList();
    private final List<Integer> removedEntities = Lists.newLinkedList();
    private final ServerPlayerStats stats;
    private float completeHealth = Float.MIN_VALUE;
    private float lastHealth = -1.0E8F;
    private int lastHungerLevel = -99999999;
    private boolean wasHungry = true;
    private int lastSentXp = -99999999;
    private int spawnProtectionTicks = 60;
    private PlayerEntity.ChatVisibility chatVisibility;
    private boolean chatColors = true;
    private long lastActionTime = System.currentTimeMillis();
    private Entity camera = null;
    private int menuId;
    public boolean useItemCooldown;
    public int ping;
    public boolean leavingTheEnd;

    public ServerPlayerEntity(MinecraftServer server, ServerWorld world, GameProfile profile, ServerPlayerInteractionManager interactionManager) {
        super(world, profile);
        interactionManager.player = this;
        this.interactionManager = interactionManager;
        BlockPos blockpos = world.getSpawnPoint();
        if (!world.dimension.hasNoSky() && world.getData().getDefaultGamemode() != WorldSettings.GameMode.ADVENTURE) {
            int i = Math.max(5, server.getSpawnProtectionRadius() - 6);
            int j = MathHelper.floor(world.getWorldBorder().getDistanceFrom(blockpos.getX(), blockpos.getZ()));
            if (j < i) {
                i = j;
            }

            if (j <= 1) {
                i = 1;
            }

            blockpos = world.getSurfaceHeight(blockpos.add(this.random.nextInt(i * 2) - i, 0, this.random.nextInt(i * 2) - i));
        }

        this.server = server;
        this.stats = server.getPlayerManager().getStats(this);
        this.stepHeight = 0.0F;
        this.refreshPositionAndAngles(blockpos, 0.0F, 0.0F);

        while (!world.getCollisions(this, this.getShape()).isEmpty() && this.y < 255.0) {
            this.setPosition(this.x, this.y + 1.0, this.z);
        }
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        if (nbt.contains("playerGameType", 99)) {
            if (MinecraftServer.getInstance().shouldForceGameMode()) {
                this.interactionManager.setGameMode(MinecraftServer.getInstance().getDefaultGameMode());
            } else {
                this.interactionManager.setGameMode(WorldSettings.GameMode.byId(nbt.getInt("playerGameType")));
            }
        }
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putInt("playerGameType", this.interactionManager.getGameMode().getId());
    }

    @Override
    public void addXp(int levels) {
        super.addXp(levels);
        this.lastSentXp = -1;
    }

    @Override
    public void applyEnchantmentCosts(int cost) {
        super.applyEnchantmentCosts(cost);
        this.lastSentXp = -1;
    }

    public void initMenu() {
        this.menu.addListener(this);
    }

    @Override
    public void enterCombat() {
        super.enterCombat();
        this.networkHandler.sendPacket(new PlayerCombatS2CPacket(this.getDamageTracker(), PlayerCombatS2CPacket.Event.ENTER_COMBAT));
    }

    @Override
    public void endCombat() {
        super.endCombat();
        this.networkHandler.sendPacket(new PlayerCombatS2CPacket(this.getDamageTracker(), PlayerCombatS2CPacket.Event.END_COMBAT));
    }

    @Override
    public void tick() {
        this.interactionManager.tick();
        this.spawnProtectionTicks--;
        if (this.invulnerableTimer > 0) {
            this.invulnerableTimer--;
        }

        this.menu.updateListeners();
        if (!this.world.isClient && !this.menu.isValid(this)) {
            this.closeMenu();
            this.menu = this.playerMenu;
        }

        while (!this.removedEntities.isEmpty()) {
            int i = Math.min(this.removedEntities.size(), Integer.MAX_VALUE);
            int[] aint = new int[i];
            Iterator<Integer> iterator = this.removedEntities.iterator();
            int j = 0;

            while (iterator.hasNext() && j < i) {
                aint[j++] = iterator.next();
                iterator.remove();
            }

            this.networkHandler.sendPacket(new RemoveEntitiesS2CPacket(aint));
        }

        if (!this.pendingChunks.isEmpty()) {
            List<WorldChunk> list = Lists.newArrayList();
            Iterator<ChunkPos> iterator1 = this.pendingChunks.iterator();
            List<BlockEntity> list1 = Lists.newArrayList();

            while (iterator1.hasNext() && list.size() < 10) {
                ChunkPos chunkpos = iterator1.next();
                if (chunkpos != null) {
                    if (this.world.isChunkLoaded(new BlockPos(chunkpos.x << 4, 0, chunkpos.z << 4))) {
                        WorldChunk worldchunk = this.world.getChunkAt(chunkpos.x, chunkpos.z);
                        if (worldchunk.isPopulated()) {
                            list.add(worldchunk);
                            list1.addAll(
                                ((ServerWorld)this.world)
                                    .getBlockEntities(chunkpos.x * 16, 0, chunkpos.z * 16, chunkpos.x * 16 + 16, 256, chunkpos.z * 16 + 16)
                            );
                            iterator1.remove();
                        }
                    }
                } else {
                    iterator1.remove();
                }
            }

            if (!list.isEmpty()) {
                if (list.size() == 1) {
                    this.networkHandler.sendPacket(new WorldChunkS2CPacket(list.get(0), true, 65535));
                } else {
                    this.networkHandler.sendPacket(new WorldChunksS2CPacket(list));
                }

                for (BlockEntity blockentity : list1) {
                    this.updateBlockEntity(blockentity);
                }

                for (WorldChunk worldchunk1 : list) {
                    this.getServerWorld().getEntityMap().addPlayer(this, worldchunk1);
                }
            }
        }

        Entity entity = this.getCamera();
        if (entity != this) {
            if (!entity.isAlive()) {
                this.setCamera(this);
            } else {
                this.updatePositionAndAngles(entity.x, entity.y, entity.z, entity.yaw, entity.pitch);
                this.server.getPlayerManager().move(this);
                if (this.isSneaking()) {
                    this.setCamera(this);
                }
            }
        }
    }

    public void tickPlayer() {
        try {
            super.tick();

            for (int i = 0; i < this.inventory.getSize(); i++) {
                ItemStack itemstack = this.inventory.getItem(i);
                if (itemstack != null && itemstack.getItem().isNetworkSynced()) {
                    Packet packet = ((NetworkSyncedItem)itemstack.getItem()).getUpdatePacket(itemstack, this.world, this);
                    if (packet != null) {
                        this.networkHandler.sendPacket(packet);
                    }
                }
            }

            if (this.getHealth() != this.lastHealth
                || this.lastHungerLevel != this.hungerManager.getFoodLevel()
                || this.hungerManager.getSaturationLevel() == 0.0F != this.wasHungry) {
                this.networkHandler
                    .sendPacket(new PlayerHealthS2CPacket(this.getHealth(), this.hungerManager.getFoodLevel(), this.hungerManager.getSaturationLevel()));
                this.lastHealth = this.getHealth();
                this.lastHungerLevel = this.hungerManager.getFoodLevel();
                this.wasHungry = this.hungerManager.getSaturationLevel() == 0.0F;
            }

            if (this.getHealth() + this.getAbsorption() != this.completeHealth) {
                this.completeHealth = this.getHealth() + this.getAbsorption();

                for (ScoreboardObjective scoreboardobjective : this.getScoreboard().getObjectives(ScoreboardCriterion.HEALTH)) {
                    this.getScoreboard().getScore(this.getName(), scoreboardobjective).setToTotalOf(Arrays.asList(this));
                }
            }

            if (this.xp != this.lastSentXp) {
                this.lastSentXp = this.xp;
                this.networkHandler.sendPacket(new PlayerXpS2CPacket(this.xpProgress, this.xp, this.xpLevel));
            }

            if (this.ticks % 20 * 5 == 0 && !this.getStats().hasAchievement(Achievements.ENTER_ALL_BIOMES)) {
                this.updateExploredBiomes();
            }
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Ticking player");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Player being ticked");
            this.populateCrashReport(crashreportcategory);
            throw new CrashException(crashreport);
        }
    }

    protected void updateExploredBiomes() {
        Biome biome = this.world.getBiome(new BlockPos(MathHelper.floor(this.x), 0, MathHelper.floor(this.z)));
        String s = biome.name;
        AchievementProgress achievementprogress = this.getStats().getProgress(Achievements.ENTER_ALL_BIOMES);
        if (achievementprogress == null) {
            achievementprogress = this.getStats().setProgress(Achievements.ENTER_ALL_BIOMES, new AchievementProgress());
        }

        achievementprogress.add(s);
        if (this.getStats().hasParentAchievement(Achievements.ENTER_ALL_BIOMES) && achievementprogress.size() >= Biome.EXPLORABLE.size()) {
            Set<Biome> set = Sets.newHashSet(Biome.EXPLORABLE);

            for (String s1 : achievementprogress) {
                Iterator<Biome> iterator = set.iterator();

                while (iterator.hasNext()) {
                    Biome biome1 = iterator.next();
                    if (biome1.name.equals(s1)) {
                        iterator.remove();
                    }
                }

                if (set.isEmpty()) {
                    break;
                }
            }

            if (set.isEmpty()) {
                this.incrementStat(Achievements.ENTER_ALL_BIOMES);
            }
        }
    }

    @Override
    public void die(DamageSource source) {
        if (this.world.getGameRules().getBoolean("showDeathMessages")) {
            AbstractTeam abstractteam = this.getScoreboardTeam();
            if (abstractteam == null || abstractteam.getDeathMessageVisibility() == AbstractTeam.Visibility.ALWAYS) {
                this.server.getPlayerManager().sendSystemMessage(this.getDamageTracker().getDeathMessage());
            } else if (abstractteam.getDeathMessageVisibility() == AbstractTeam.Visibility.HIDE_FOR_OTHER_TEAMS) {
                this.server.getPlayerManager().sendMessageToTeamMembers(this, this.getDamageTracker().getDeathMessage());
            } else if (abstractteam.getDeathMessageVisibility() == AbstractTeam.Visibility.HIDE_FOR_OWN_TEAM) {
                this.server.getPlayerManager().sendMessageToNonTeamMembers(this, this.getDamageTracker().getDeathMessage());
            }
        }

        if (!this.world.getGameRules().getBoolean("keepInventory")) {
            this.inventory.dropAll();
        }

        for (ScoreboardObjective scoreboardobjective : this.world.getScoreboard().getObjectives(ScoreboardCriterion.DEATH_COUNT)) {
            ScoreboardScore scoreboardscore = this.getScoreboard().getScore(this.getName(), scoreboardobjective);
            scoreboardscore.increment();
        }

        LivingEntity livingentity = this.getLastAttacker();
        if (livingentity != null) {
            Entities.SpawnEggData entities$spawneggdata = Entities.SPAWN_EGG_DATA.get(Entities.getId(livingentity));
            if (entities$spawneggdata != null) {
                this.incrementStat(entities$spawneggdata.entityKilledByStat);
            }

            livingentity.takeKillScore(this, this.score);
        }

        this.incrementStat(Stats.DEATHS);
        this.clearStat(Stats.TIME_SINCE_DEATH);
        this.getDamageTracker().resetStatus();
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        boolean flag = this.server.isDedicated() && this.canPvp() && "fall".equals(source.name);
        if (!flag && this.spawnProtectionTicks > 0 && source != DamageSource.OUT_OF_WORLD) {
            return false;
        }

        if (source instanceof EntityDamageSource) {
            Entity entity = source.getAttacker();
            if (entity instanceof PlayerEntity && !this.canAttack((PlayerEntity)entity)) {
                return false;
            }

            if (entity instanceof ArrowEntity) {
                ArrowEntity arrowentity = (ArrowEntity)entity;
                if (arrowentity.shooter instanceof PlayerEntity && !this.canAttack((PlayerEntity)arrowentity.shooter)) {
                    return false;
                }
            }
        }

        return super.takeDamage(source, amount);
    }

    @Override
    public boolean canAttack(PlayerEntity player) {
        return this.canPvp() && super.canAttack(player);
    }

    private boolean canPvp() {
        return this.server.isPvpEnabled();
    }

    @Override
    public void changeDimension(int dimension) {
        if (this.dimension == 1 && dimension == 1) {
            this.incrementStat(Achievements.LEAVE_THE_END);
            this.world.removeEntity(this);
            this.leavingTheEnd = true;
            this.networkHandler.sendPacket(new GameEventS2CPacket(4, 0.0F));
        } else {
            if (this.dimension == 0 && dimension == 1) {
                this.incrementStat(Achievements.ENTER_THE_END);
                BlockPos blockpos = this.server.getWorld(dimension).getForcedSpawnPoint();
                if (blockpos != null) {
                    this.networkHandler.teleport(blockpos.getX(), blockpos.getY(), blockpos.getZ(), 0.0F, 0.0F);
                }

                dimension = 1;
            } else {
                this.incrementStat(Achievements.ENTER_THE_NETHER);
            }

            this.server.getPlayerManager().changeDimension(this, dimension);
            this.lastSentXp = -1;
            this.lastHealth = -1.0F;
            this.lastHungerLevel = -1;
        }
    }

    @Override
    public boolean broadcastTo(ServerPlayerEntity player) {
        return player.isSpectator() ? this.getCamera() == this : !this.isSpectator() && super.broadcastTo(player);
    }

    private void updateBlockEntity(BlockEntity blockEntity) {
        if (blockEntity != null) {
            Packet packet = blockEntity.createUpdatePacket();
            if (packet != null) {
                this.networkHandler.sendPacket(packet);
            }
        }
    }

    @Override
    public void sendPickup(Entity entity, int count) {
        super.sendPickup(entity, count);
        this.menu.updateListeners();
    }

    @Override
    public PlayerEntity.SleepAllowedStatus trySleep(BlockPos pos) {
        PlayerEntity.SleepAllowedStatus playerentity$sleepallowedstatus = super.trySleep(pos);
        if (playerentity$sleepallowedstatus == PlayerEntity.SleepAllowedStatus.OK) {
            Packet packet = new PlayerSleepS2CPacket(this, pos);
            this.getServerWorld().getEntityMap().sendPacket(this, packet);
            this.networkHandler.teleport(this.x, this.y, this.z, this.yaw, this.pitch);
            this.networkHandler.sendPacket(packet);
        }

        return playerentity$sleepallowedstatus;
    }

    @Override
    public void wakeUp(boolean resetSleepTimer, boolean updateAllPlayersSleeping, boolean setSpawnPoint) {
        if (this.isSleeping()) {
            this.getServerWorld().getEntityMap().sendPacketToAll(this, new EntityAnimationS2CPacket(this, 2));
        }

        super.wakeUp(resetSleepTimer, updateAllPlayersSleeping, setSpawnPoint);
        if (this.networkHandler != null) {
            this.networkHandler.teleport(this.x, this.y, this.z, this.yaw, this.pitch);
        }
    }

    @Override
    public void startRiding(Entity entity) {
        Entity entityx = this.vehicle;
        super.startRiding(entity);
        if (entity != entityx) {
            this.networkHandler.sendPacket(new AttachEntityS2CPacket(0, this, this.vehicle));
            this.networkHandler.teleport(this.x, this.y, this.z, this.yaw, this.pitch);
        }
    }

    @Override
    protected void checkFallDamage(double dy, boolean landed, Block block, BlockPos pos) {
    }

    public void handleFall(double distance, boolean onGround) {
        int i = MathHelper.floor(this.x);
        int j = MathHelper.floor(this.y - 0.2F);
        int k = MathHelper.floor(this.z);
        BlockPos blockpos = new BlockPos(i, j, k);
        Block block = this.world.getBlockState(blockpos).getBlock();
        if (block.getMaterial() == Material.AIR) {
            Block block1 = this.world.getBlockState(blockpos.down()).getBlock();
            if (block1 instanceof FenceBlock || block1 instanceof WallBlock || block1 instanceof FenceGateBlock) {
                blockpos = blockpos.down();
                block = this.world.getBlockState(blockpos).getBlock();
            }
        }

        super.checkFallDamage(distance, onGround, block, blockpos);
    }

    @Override
    public void openSignEditor(SignBlockEntity sign) {
        sign.setPlayer(this);
        this.networkHandler.sendPacket(new OpenSignEditorS2CPacket(sign.getPos()));
    }

    private void incrementSyncId() {
        this.menuId = this.menuId % 100 + 1;
    }

    @Override
    public void openMenu(MenuProvider menuProvider) {
        this.incrementSyncId();
        this.networkHandler.sendPacket(new OpenInventoryMenuS2CPacket(this.menuId, menuProvider.getMenuType(), menuProvider.getDisplayName()));
        this.menu = menuProvider.createMenu(this.inventory, this);
        this.menu.networkId = this.menuId;
        this.menu.addListener(this);
    }

    @Override
    public void openChestMenu(Inventory inventory) {
        if (this.menu != this.playerMenu) {
            this.closeMenu();
        }

        if (inventory instanceof LockableMenuProvider) {
            LockableMenuProvider lockablemenuprovider = (LockableMenuProvider)inventory;
            if (lockablemenuprovider.isLocked() && !this.canUnlockInventory(lockablemenuprovider.getLock()) && !this.isSpectator()) {
                this.networkHandler.sendPacket(new ChatMessageS2CPacket(new TranslatableText("container.isLocked", inventory.getDisplayName()), (byte)2));
                this.networkHandler.sendPacket(new SoundEventS2CPacket("random.door_close", this.x, this.y, this.z, 1.0F, 1.0F));
                return;
            }
        }

        this.incrementSyncId();
        if (inventory instanceof MenuProvider) {
            this.networkHandler
                .sendPacket(
                    new OpenInventoryMenuS2CPacket(this.menuId, ((MenuProvider)inventory).getMenuType(), inventory.getDisplayName(), inventory.getSize())
                );
            this.menu = ((MenuProvider)inventory).createMenu(this.inventory, this);
        } else {
            this.networkHandler.sendPacket(new OpenInventoryMenuS2CPacket(this.menuId, "minecraft:container", inventory.getDisplayName(), inventory.getSize()));
            this.menu = new ChestMenu(this.inventory, inventory, this);
        }

        this.menu.networkId = this.menuId;
        this.menu.addListener(this);
    }

    @Override
    public void openTraderMenu(Trader trader) {
        this.incrementSyncId();
        this.menu = new TraderMenu(this.inventory, trader, this.world);
        this.menu.networkId = this.menuId;
        this.menu.addListener(this);
        Inventory inventory = ((TraderMenu)this.menu).getTraderInventory();
        Text text = trader.getDisplayName();
        this.networkHandler.sendPacket(new OpenInventoryMenuS2CPacket(this.menuId, "minecraft:villager", text, inventory.getSize()));
        TradeOffers tradeoffers = trader.getOffers(this);
        if (tradeoffers != null) {
            PacketByteBuf packetbytebuf = new PacketByteBuf(Unpooled.buffer());
            packetbytebuf.writeInt(this.menuId);
            tradeoffers.serialize(packetbytebuf);
            this.networkHandler.sendPacket(new CustomPayloadS2CPacket("MC|TrList", packetbytebuf));
        }
    }

    @Override
    public void openHorseMenu(HorseBaseEntity horse, Inventory inventory) {
        if (this.menu != this.playerMenu) {
            this.closeMenu();
        }

        this.incrementSyncId();
        this.networkHandler
            .sendPacket(new OpenInventoryMenuS2CPacket(this.menuId, "EntityHorse", inventory.getDisplayName(), inventory.getSize(), horse.getNetworkId()));
        this.menu = new HorseMenu(this.inventory, inventory, horse, this);
        this.menu.networkId = this.menuId;
        this.menu.addListener(this);
    }

    @Override
    public void openEditBookScreen(ItemStack book) {
        Item item = book.getItem();
        if (item == Items.WRITTEN_BOOK) {
            this.networkHandler.sendPacket(new CustomPayloadS2CPacket("MC|BOpen", new PacketByteBuf(Unpooled.buffer())));
        }
    }

    @Override
    public void onSlotChanged(InventoryMenu menu, int slot, ItemStack item) {
        if (!(menu.getSlot(slot) instanceof CraftingResultSlot)) {
            if (!this.useItemCooldown) {
                this.networkHandler.sendPacket(new InventoryMenuSlotContentS2CPacket(menu.networkId, slot, item));
            }
        }
    }

    public void setMenu(InventoryMenu menu) {
        this.onMenuChanged(menu, menu.getItems());
    }

    @Override
    public void onMenuChanged(InventoryMenu menu, List<ItemStack> items) {
        this.networkHandler.sendPacket(new InventoryMenuContentS2CPacket(menu.networkId, items));
        this.networkHandler.sendPacket(new InventoryMenuSlotContentS2CPacket(-1, -1, this.inventory.getCursorItem()));
    }

    @Override
    public void onDataChanged(InventoryMenu menu, int id, int value) {
        this.networkHandler.sendPacket(new InventoryMenuDataS2CPacket(menu.networkId, id, value));
    }

    @Override
    public void updateData(InventoryMenu menu, Inventory inventory) {
        for (int i = 0; i < inventory.getDataSize(); i++) {
            this.networkHandler.sendPacket(new InventoryMenuDataS2CPacket(menu.networkId, i, inventory.getData(i)));
        }
    }

    @Override
    public void closeMenu() {
        this.networkHandler.sendPacket(new CloseInventoryMenuS2CPacket(this.menu.networkId));
        this.doCloseMenu();
    }

    public void use() {
        if (!this.useItemCooldown) {
            this.networkHandler.sendPacket(new InventoryMenuSlotContentS2CPacket(-1, -1, this.inventory.getCursorItem()));
        }
    }

    public void doCloseMenu() {
        this.menu.close(this);
        this.menu = this.playerMenu;
    }

    public void setPlayerInput(float sidewaysSpeed, float forwardSpeed, boolean jumping, boolean sneaking) {
        if (this.vehicle != null) {
            if (sidewaysSpeed >= -1.0F && sidewaysSpeed <= 1.0F) {
                this.sidewaysSpeed = sidewaysSpeed;
            }

            if (forwardSpeed >= -1.0F && forwardSpeed <= 1.0F) {
                this.forwardSpeed = forwardSpeed;
            }

            this.jumping = jumping;
            this.setSneaking(sneaking);
        }
    }

    @Override
    public void incrementStat(Stat stat, int amount) {
        if (stat != null) {
            this.stats.increment(this, stat, amount);

            for (ScoreboardObjective scoreboardobjective : this.getScoreboard().getObjectives(stat.getCriterion())) {
                this.getScoreboard().getScore(this.getName(), scoreboardobjective).increase(amount);
            }

            if (this.stats.isDirty()) {
                this.stats.sendStats(this);
            }
        }
    }

    @Override
    public void clearStat(Stat stat) {
        if (stat != null) {
            this.stats.set(this, stat, 0);

            for (ScoreboardObjective scoreboardobjective : this.getScoreboard().getObjectives(stat.getCriterion())) {
                this.getScoreboard().getScore(this.getName(), scoreboardobjective).set(0);
            }

            if (this.stats.isDirty()) {
                this.stats.sendStats(this);
            }
        }
    }

    public void onDisconnect() {
        if (this.rider != null) {
            this.rider.startRiding(this);
        }

        if (this.sleeping) {
            this.wakeUp(true, false, false);
        }
    }

    public void markHealthDirty() {
        this.lastHealth = -1.0E8F;
    }

    @Override
    public void addMessage(Text message) {
        this.networkHandler.sendPacket(new ChatMessageS2CPacket(message));
    }

    @Override
    protected void finishUsingItem() {
        this.networkHandler.sendPacket(new EntityEventS2CPacket(this, (byte)9));
        super.finishUsingItem();
    }

    @Override
    public void setItemInUse(ItemStack item, int maxUseTime) {
        super.setItemInUse(item, maxUseTime);
        if (item != null && item.getItem() != null && item.getItem().getUseAction(item) == UseAction.EAT) {
            this.getServerWorld().getEntityMap().sendPacketToAll(this, new EntityAnimationS2CPacket(this, 3));
        }
    }

    @Override
    public void copyFrom(PlayerEntity player, boolean comesFromTheEnd) {
        super.copyFrom(player, comesFromTheEnd);
        this.lastSentXp = -1;
        this.lastHealth = -1.0F;
        this.lastHungerLevel = -1;
        this.removedEntities.addAll(((ServerPlayerEntity)player).removedEntities);
    }

    @Override
    protected void onStatusEffectApplied(StatusEffectInstance instance) {
        super.onStatusEffectApplied(instance);
        this.networkHandler.sendPacket(new EntityStatusEffectS2CPacket(this.getNetworkId(), instance));
    }

    @Override
    protected void onStatusEffectUpgraded(StatusEffectInstance instance, boolean timerRanOut) {
        super.onStatusEffectUpgraded(instance, timerRanOut);
        this.networkHandler.sendPacket(new EntityStatusEffectS2CPacket(this.getNetworkId(), instance));
    }

    @Override
    protected void onStatusEffectRemoved(StatusEffectInstance effect) {
        super.onStatusEffectRemoved(effect);
        this.networkHandler.sendPacket(new EntityRemoveStatusEffectS2CPacket(this.getNetworkId(), effect));
    }

    @Override
    public void teleport(double x, double y, double z) {
        this.networkHandler.teleport(x, y, z, this.yaw, this.pitch);
    }

    @Override
    public void addCritParticles(Entity entity) {
        this.getServerWorld().getEntityMap().sendPacketToAll(this, new EntityAnimationS2CPacket(entity, 4));
    }

    @Override
    public void addEnchantedCritParticles(Entity entity) {
        this.getServerWorld().getEntityMap().sendPacketToAll(this, new EntityAnimationS2CPacket(entity, 5));
    }

    @Override
    public void syncAbilities() {
        if (this.networkHandler != null) {
            this.networkHandler.sendPacket(new PlayerAbilitiesS2CPacket(this.abilities));
            this.updateVisibility();
        }
    }

    public ServerWorld getServerWorld() {
        return (ServerWorld)this.world;
    }

    @Override
    public void setGameMode(WorldSettings.GameMode gameMode) {
        this.interactionManager.setGameMode(gameMode);
        this.networkHandler.sendPacket(new GameEventS2CPacket(3, gameMode.getId()));
        if (gameMode == WorldSettings.GameMode.SPECTATOR) {
            this.startRiding(null);
        } else {
            this.setCamera(this);
        }

        this.syncAbilities();
        this.markEffects();
    }

    @Override
    public boolean isSpectator() {
        return this.interactionManager.getGameMode() == WorldSettings.GameMode.SPECTATOR;
    }

    @Override
    public void sendMessage(Text message) {
        this.networkHandler.sendPacket(new ChatMessageS2CPacket(message));
    }

    @Override
    public boolean canUseCommand(int permissionLevel, String command) {
        if ("seed".equals(command) && !this.server.isDedicated()) {
            return true;
        }

        if (!"tell".equals(command) && !"help".equals(command) && !"me".equals(command) && !"trigger".equals(command)) {
            if (this.server.getPlayerManager().isOp(this.getGameProfile())) {
                OpEntry opentry = this.server.getPlayerManager().getOps().get(this.getGameProfile());
                return opentry != null ? opentry.getPermissionLevel() >= permissionLevel : this.server.getOpPermissionLevel() >= permissionLevel;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    public String getIp() {
        String s = this.networkHandler.connection.getAddress().toString();
        s = s.substring(s.indexOf("/") + 1);
        return s.substring(0, s.indexOf(":"));
    }

    public void updateSettings(ClientSettingsC2SPacket packet) {
        this.language = packet.getLanguage();
        this.chatVisibility = packet.getChatVisibility();
        this.chatColors = packet.getChatColors();
        this.getSyncedData().update(10, (byte)packet.getModelParts());
    }

    public PlayerEntity.ChatVisibility getChatVisibility() {
        return this.chatVisibility;
    }

    public void sendResourcePack(String url, String hash) {
        this.networkHandler.sendPacket(new ResourcePackS2CPacket(url, hash));
    }

    @Override
    public BlockPos getCommandSourceBlockPos() {
        return new BlockPos(this.x, this.y + 0.5, this.z);
    }

    public void updateLastActionTime() {
        this.lastActionTime = MinecraftServer.getTimeMillis();
    }

    public ServerPlayerStats getStats() {
        return this.stats;
    }

    public void sendRemoveEntity(Entity entity) {
        if (entity instanceof PlayerEntity) {
            this.networkHandler.sendPacket(new RemoveEntitiesS2CPacket(entity.getNetworkId()));
        } else {
            this.removedEntities.add(entity.getNetworkId());
        }
    }

    @Override
    protected void updateVisibility() {
        if (this.isSpectator()) {
            this.clearEffectParticles();
            this.setInvisible(true);
        } else {
            super.updateVisibility();
        }

        this.getServerWorld().getEntityMap().updateVisibility(this);
    }

    public Entity getCamera() {
        return this.camera == null ? this : this.camera;
    }

    public void setCamera(Entity camera) {
        Entity entity = this.getCamera();
        this.camera = camera == null ? this : camera;
        if (entity != this.camera) {
            this.networkHandler.sendPacket(new CameraS2CPacket(this.camera));
            this.teleport(this.camera.x, this.camera.y, this.camera.z);
        }
    }

    @Override
    public void attack(Entity target) {
        if (this.interactionManager.getGameMode() == WorldSettings.GameMode.SPECTATOR) {
            this.setCamera(target);
        } else {
            super.attack(target);
        }
    }

    public long getLastActionTime() {
        return this.lastActionTime;
    }

    public Text getPlayerListName() {
        return null;
    }
}
