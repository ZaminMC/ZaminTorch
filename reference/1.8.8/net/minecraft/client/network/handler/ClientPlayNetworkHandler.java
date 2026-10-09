package net.minecraft.client.network.handler;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.block.entity.FlowerPotBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.entity.particle.EntityPickupParticle;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ConfirmationListener;
import net.minecraft.client.gui.screen.CreditsScreen;
import net.minecraft.client.gui.screen.DemoScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.inventory.BookEditScreen;
import net.minecraft.client.gui.screen.inventory.menu.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.inventory.menu.VillagerScreen;
import net.minecraft.client.gui.screen.menu.StatsListener;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.ServerList;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.instance.GuardianAttackSoundInstance;
import net.minecraft.client.twitch.AchievementMetadata;
import net.minecraft.client.twitch.PlayerCombatMetadata;
import net.minecraft.client.twitch.PlayerDeathMetadata;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.world.villager.trade.ClientTrader;
import net.minecraft.entity.EnderCrystalEntity;
import net.minecraft.entity.EnderEyeEntity;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.FireworksEntity;
import net.minecraft.entity.FishingBobberEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.PrimedTntEntity;
import net.minecraft.entity.data.SyncedData;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.LeadKnotEntity;
import net.minecraft.entity.decoration.PaintingEntity;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.AbstractEntityAttributeContainer;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.RangedEntityAttribute;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.monster.GuardianEntity;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.EggEntity;
import net.minecraft.entity.projectile.EnderPearlEntity;
import net.minecraft.entity.projectile.ExperienceBottleEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.PotionEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.SnowballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.inventory.AnimalInventory;
import net.minecraft.inventory.MenuInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.inventory.menu.EmptyMenuProvider;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.PacketUtils;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.play.InventoryMenuConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.ResourcePackC2SPacket;
import net.minecraft.network.packet.s2c.play.AddEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.AddExperienceOrbS2CPacket;
import net.minecraft.network.packet.s2c.play.AddGlobalEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.AddMobS2CPacket;
import net.minecraft.network.packet.s2c.play.AddPaintingS2CPacket;
import net.minecraft.network.packet.s2c.play.AddPlayerS2CPacket;
import net.minecraft.network.packet.s2c.play.AttachEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockMiningProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlocksUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.CameraS2CPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.CloseInventoryMenuS2CPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.s2c.play.CompressionThresholdS2CPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.DifficultyS2CPacket;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDataS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityHeadAnglesS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPickupS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityRemoveStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTeleportS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.GameEventS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuConfirmS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuContentS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuDataS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuSlotContentS2CPacket;
import net.minecraft.network.packet.s2c.play.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.LoginS2CPacket;
import net.minecraft.network.packet.s2c.play.MapDataS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenInventoryMenuS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenSignEditorS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerCombatS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerHealthS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerInfoS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSleepS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerXpS2CPacket;
import net.minecraft.network.packet.s2c.play.RemoveEntitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.ResourcePackS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreS2CPacket;
import net.minecraft.network.packet.s2c.play.SelectSlotS2CPacket;
import net.minecraft.network.packet.s2c.play.SignUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SoundEventS2CPacket;
import net.minecraft.network.packet.s2c.play.SpawnPointS2CPacket;
import net.minecraft.network.packet.s2c.play.StatsS2CPacket;
import net.minecraft.network.packet.s2c.play.TabListS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.network.packet.s2c.play.TitlesS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldBorderS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldChunkS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldChunksS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeS2CPacket;
import net.minecraft.realms.DisconnectedRealmsScreen;
import net.minecraft.realms.RealmsScreenProxy;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.stat.Stat;
import net.minecraft.stat.achievement.AchievementStat;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringUtils;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.OverworldDimension;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.map.SavedMapData;
import net.minecraft.world.village.trade.TradeOffers;
import net.minecraft.world.village.trade.Trader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClientPlayNetworkHandler implements ClientPlayPacketHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Connection connection;
    private final GameProfile profile;
    private final Screen callbackScreen;
    private Minecraft minecraft;
    private ClientWorld world;
    private boolean started;
    private final Map<UUID, PlayerInfo> onlinePlayers = Maps.newHashMap();
    public int maxPlayerCount = 20;
    private boolean hasAchievements = false;
    private final Random random = new Random();

    public ClientPlayNetworkHandler(Minecraft minecraft, Screen callbackScreen, Connection connection, GameProfile profile) {
        this.minecraft = minecraft;
        this.callbackScreen = callbackScreen;
        this.connection = connection;
        this.profile = profile;
    }

    public void closeWorld() {
        this.world = null;
    }

    @Override
    public void handleLogin(LoginS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        this.minecraft.interactionManager = new ClientPlayerInteractionManager(this.minecraft, this);
        this.world = new ClientWorld(
            this,
            new WorldSettings(0L, packet.getGameMode(), false, packet.getHardcore(), packet.getGeneratorType()),
            packet.getDimension(),
            packet.getDifficulty(),
            this.minecraft.profiler
        );
        this.minecraft.options.difficulty = packet.getDifficulty();
        this.minecraft.setWorld(this.world);
        this.minecraft.player.dimension = packet.getDimension();
        this.minecraft.openScreen(new DownloadingTerrainScreen(this));
        this.minecraft.player.setNetworkId(packet.getEntityId());
        this.maxPlayerCount = packet.getMaxPlayerCount();
        this.minecraft.player.setReducedDebugInfo(packet.getReducedDebugInfo());
        this.minecraft.interactionManager.setGameMode(packet.getGameMode());
        this.minecraft.options.syncClientSettings();
        this.connection.send(new CustomPayloadC2SPacket("MC|Brand", new PacketByteBuf(Unpooled.buffer()).writeString(ClientBrandRetriever.getClientModName())));
    }

    @Override
    public void handleAddEntity(AddEntityS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        double d0 = packet.getX() / 32.0;
        double d1 = packet.getY() / 32.0;
        double d2 = packet.getZ() / 32.0;
        Entity entity = null;
        if (packet.getType() == 10) {
            entity = MinecartEntity.create(this.world, d0, d1, d2, MinecartEntity.Type.byIndex(packet.getData()));
        } else if (packet.getType() == 90) {
            Entity entity1 = this.world.getEntity(packet.getData());
            if (entity1 instanceof PlayerEntity) {
                entity = new FishingBobberEntity(this.world, d0, d1, d2, (PlayerEntity)entity1);
            }

            packet.setData(0);
        } else if (packet.getType() == 60) {
            entity = new ArrowEntity(this.world, d0, d1, d2);
        } else if (packet.getType() == 61) {
            entity = new SnowballEntity(this.world, d0, d1, d2);
        } else if (packet.getType() == 71) {
            entity = new ItemFrameEntity(
                this.world, new BlockPos(MathHelper.floor(d0), MathHelper.floor(d1), MathHelper.floor(d2)), Direction.byIdHorizontal(packet.getData())
            );
            packet.setData(0);
        } else if (packet.getType() == 77) {
            entity = new LeadKnotEntity(this.world, new BlockPos(MathHelper.floor(d0), MathHelper.floor(d1), MathHelper.floor(d2)));
            packet.setData(0);
        } else if (packet.getType() == 65) {
            entity = new EnderPearlEntity(this.world, d0, d1, d2);
        } else if (packet.getType() == 72) {
            entity = new EnderEyeEntity(this.world, d0, d1, d2);
        } else if (packet.getType() == 76) {
            entity = new FireworksEntity(this.world, d0, d1, d2, null);
        } else if (packet.getType() == 63) {
            entity = new FireballEntity(this.world, d0, d1, d2, packet.getVelocityX() / 8000.0, packet.getVelocityY() / 8000.0, packet.getVelocityZ() / 8000.0);
            packet.setData(0);
        } else if (packet.getType() == 64) {
            entity = new SmallFireballEntity(
                this.world, d0, d1, d2, packet.getVelocityX() / 8000.0, packet.getVelocityY() / 8000.0, packet.getVelocityZ() / 8000.0
            );
            packet.setData(0);
        } else if (packet.getType() == 66) {
            entity = new WitherSkullEntity(
                this.world, d0, d1, d2, packet.getVelocityX() / 8000.0, packet.getVelocityY() / 8000.0, packet.getVelocityZ() / 8000.0
            );
            packet.setData(0);
        } else if (packet.getType() == 62) {
            entity = new EggEntity(this.world, d0, d1, d2);
        } else if (packet.getType() == 73) {
            entity = new PotionEntity(this.world, d0, d1, d2, packet.getData());
            packet.setData(0);
        } else if (packet.getType() == 75) {
            entity = new ExperienceBottleEntity(this.world, d0, d1, d2);
            packet.setData(0);
        } else if (packet.getType() == 1) {
            entity = new BoatEntity(this.world, d0, d1, d2);
        } else if (packet.getType() == 50) {
            entity = new PrimedTntEntity(this.world, d0, d1, d2, null);
        } else if (packet.getType() == 78) {
            entity = new ArmorStandEntity(this.world, d0, d1, d2);
        } else if (packet.getType() == 51) {
            entity = new EnderCrystalEntity(this.world, d0, d1, d2);
        } else if (packet.getType() == 2) {
            entity = new ItemEntity(this.world, d0, d1, d2);
        } else if (packet.getType() == 70) {
            entity = new FallingBlockEntity(this.world, d0, d1, d2, Block.deserialize(packet.getData() & 65535));
            packet.setData(0);
        }

        if (entity != null) {
            entity.lastKnownX = packet.getX();
            entity.lastKnownY = packet.getY();
            entity.lastKnownZ = packet.getZ();
            entity.pitch = packet.getPitch() * 360 / 256.0F;
            entity.yaw = packet.getYaw() * 360 / 256.0F;
            Entity[] aentity = entity.getParts();
            if (aentity != null) {
                int i = packet.getId() - entity.getNetworkId();

                for (int j = 0; j < aentity.length; j++) {
                    aentity[j].setNetworkId(aentity[j].getNetworkId() + i);
                }
            }

            entity.setNetworkId(packet.getId());
            this.world.forceEntity(packet.getId(), entity);
            if (packet.getData() > 0) {
                if (packet.getType() == 60) {
                    Entity entity2 = this.world.getEntity(packet.getData());
                    if (entity2 instanceof LivingEntity && entity instanceof ArrowEntity) {
                        ((ArrowEntity)entity).shooter = entity2;
                    }
                }

                entity.lerpVelocity(packet.getVelocityX() / 8000.0, packet.getVelocityY() / 8000.0, packet.getVelocityZ() / 8000.0);
            }
        }
    }

    @Override
    public void handleAddExperienceOrb(AddExperienceOrbS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = new ExperienceOrbEntity(this.world, packet.getX() / 32.0, packet.getY() / 32.0, packet.getZ() / 32.0, packet.getXp());
        entity.lastKnownX = packet.getX();
        entity.lastKnownY = packet.getY();
        entity.lastKnownZ = packet.getZ();
        entity.yaw = 0.0F;
        entity.pitch = 0.0F;
        entity.setNetworkId(packet.getId());
        this.world.forceEntity(packet.getId(), entity);
    }

    @Override
    public void handleAddGlobalEntity(AddGlobalEntityS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        double d0 = packet.getX() / 32.0;
        double d1 = packet.getY() / 32.0;
        double d2 = packet.getZ() / 32.0;
        Entity entity = null;
        if (packet.getType() == 1) {
            entity = new LightningBoltEntity(this.world, d0, d1, d2);
        }

        if (entity != null) {
            entity.lastKnownX = packet.getX();
            entity.lastKnownY = packet.getY();
            entity.lastKnownZ = packet.getZ();
            entity.yaw = 0.0F;
            entity.pitch = 0.0F;
            entity.setNetworkId(packet.getId());
            this.world.addGlobalEntity(entity);
        }
    }

    @Override
    public void handleAddPainting(AddPaintingS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        PaintingEntity paintingentity = new PaintingEntity(this.world, packet.getPos(), packet.getFacing(), packet.getMotive());
        this.world.forceEntity(packet.getId(), paintingentity);
    }

    @Override
    public void handleEntityVelocity(EntityVelocityS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = this.world.getEntity(packet.getId());
        if (entity != null) {
            entity.lerpVelocity(packet.getVelocityX() / 8000.0, packet.getVelocityY() / 8000.0, packet.getVelocityZ() / 8000.0);
        }
    }

    @Override
    public void handleEntityData(EntityDataS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = this.world.getEntity(packet.getId());
        if (entity != null && packet.getDataEntries() != null) {
            entity.getSyncedData().update(packet.getDataEntries());
        }
    }

    @Override
    public void handleAddPlayer(AddPlayerS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        double d0 = packet.getX() / 32.0;
        double d1 = packet.getY() / 32.0;
        double d2 = packet.getZ() / 32.0;
        float f = packet.getYaw() * 360 / 256.0F;
        float f1 = packet.getPitch() * 360 / 256.0F;
        RemoteClientPlayerEntity remoteclientplayerentity = new RemoteClientPlayerEntity(
            this.minecraft.world, this.getOnlinePlayer(packet.getUuid()).getProfile()
        );
        remoteclientplayerentity.lastX = remoteclientplayerentity.prevX = remoteclientplayerentity.lastKnownX = packet.getX();
        remoteclientplayerentity.lastY = remoteclientplayerentity.prevY = remoteclientplayerentity.lastKnownY = packet.getY();
        remoteclientplayerentity.lastZ = remoteclientplayerentity.prevZ = remoteclientplayerentity.lastKnownZ = packet.getZ();
        int i = packet.getMainHandItem();
        if (i == 0) {
            remoteclientplayerentity.inventory.items[remoteclientplayerentity.inventory.selectedSlot] = null;
        } else {
            remoteclientplayerentity.inventory.items[remoteclientplayerentity.inventory.selectedSlot] = new ItemStack(Item.byId(i), 1, 0);
        }

        remoteclientplayerentity.updatePositionAndAngles(d0, d1, d2, f, f1);
        this.world.forceEntity(packet.getId(), remoteclientplayerentity);
        List<SyncedData.Entry> list = packet.getDataEntries();
        if (list != null) {
            remoteclientplayerentity.getSyncedData().update(list);
        }
    }

    @Override
    public void handleEntityTeleport(EntityTeleportS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = this.world.getEntity(packet.getId());
        if (entity != null) {
            entity.lastKnownX = packet.getX();
            entity.lastKnownY = packet.getY();
            entity.lastKnownZ = packet.getZ();
            double d0 = entity.lastKnownX / 32.0;
            double d1 = entity.lastKnownY / 32.0;
            double d2 = entity.lastKnownZ / 32.0;
            float f = packet.getYaw() * 360 / 256.0F;
            float f1 = packet.getPitch() * 360 / 256.0F;
            if (!(Math.abs(entity.x - d0) >= 0.03125) && !(Math.abs(entity.y - d1) >= 0.015625) && !(Math.abs(entity.z - d2) >= 0.03125)) {
                entity.lerpPositionAndAngles(entity.x, entity.y, entity.z, f, f1, 3, true);
            } else {
                entity.lerpPositionAndAngles(d0, d1, d2, f, f1, 3, true);
            }

            entity.onGround = packet.getOnGround();
        }
    }

    @Override
    public void handleSelectSlot(SelectSlotS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        if (packet.getSlot() >= 0 && packet.getSlot() < PlayerInventory.getHotbarSize()) {
            this.minecraft.player.inventory.selectedSlot = packet.getSlot();
        }
    }

    @Override
    public void handleEntityMove(EntityMoveS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = packet.getEntity(this.world);
        if (entity != null) {
            entity.lastKnownX = entity.lastKnownX + packet.getDx();
            entity.lastKnownY = entity.lastKnownY + packet.getDy();
            entity.lastKnownZ = entity.lastKnownZ + packet.getDz();
            double d0 = entity.lastKnownX / 32.0;
            double d1 = entity.lastKnownY / 32.0;
            double d2 = entity.lastKnownZ / 32.0;
            float f = packet.hasAngles() ? packet.getYaw() * 360 / 256.0F : entity.yaw;
            float f1 = packet.hasAngles() ? packet.getPitch() * 360 / 256.0F : entity.pitch;
            entity.lerpPositionAndAngles(d0, d1, d2, f, f1, 3, false);
            entity.onGround = packet.getOnGround();
        }
    }

    @Override
    public void handleEntityHeadAngles(EntityHeadAnglesS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = packet.getEntity(this.world);
        if (entity != null) {
            float f = packet.getHeadYaw() * 360 / 256.0F;
            entity.setHeadYaw(f);
        }
    }

    @Override
    public void handleRemoveEntities(RemoveEntitiesS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);

        for (int i = 0; i < packet.getIds().length; i++) {
            this.world.removeEntity(packet.getIds()[i]);
        }
    }

    @Override
    public void handlePlayerMove(PlayerMoveS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        PlayerEntity playerentity = this.minecraft.player;
        double d0 = packet.getX();
        double d1 = packet.getY();
        double d2 = packet.getZ();
        float f = packet.getYaw();
        float f1 = packet.getPitch();
        if (packet.getRelativeArgs().contains(PlayerMoveS2CPacket.Argument.X)) {
            d0 += playerentity.x;
        } else {
            playerentity.velocityX = 0.0;
        }

        if (packet.getRelativeArgs().contains(PlayerMoveS2CPacket.Argument.Y)) {
            d1 += playerentity.y;
        } else {
            playerentity.velocityY = 0.0;
        }

        if (packet.getRelativeArgs().contains(PlayerMoveS2CPacket.Argument.Z)) {
            d2 += playerentity.z;
        } else {
            playerentity.velocityZ = 0.0;
        }

        if (packet.getRelativeArgs().contains(PlayerMoveS2CPacket.Argument.PITCH)) {
            f1 += playerentity.pitch;
        }

        if (packet.getRelativeArgs().contains(PlayerMoveS2CPacket.Argument.YAW)) {
            f += playerentity.yaw;
        }

        playerentity.updatePositionAndAngles(d0, d1, d2, f, f1);
        this.connection
            .send(
                new PlayerMoveC2SPacket.PositionAndAngles(
                    playerentity.x, playerentity.getShape().minY, playerentity.z, playerentity.yaw, playerentity.pitch, false
                )
            );
        if (!this.started) {
            this.minecraft.player.lastX = this.minecraft.player.x;
            this.minecraft.player.lastY = this.minecraft.player.y;
            this.minecraft.player.lastZ = this.minecraft.player.z;
            this.started = true;
            this.minecraft.openScreen(null);
        }
    }

    @Override
    public void handleBlocksUpdate(BlocksUpdateS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);

        for (BlocksUpdateS2CPacket.BlockUpdate blocksupdates2cpacket$blockupdate : packet.getUpdates()) {
            this.world.setBlockStateFromPacket(blocksupdates2cpacket$blockupdate.getBlockPos(), blocksupdates2cpacket$blockupdate.getBlockState());
        }
    }

    @Override
    public void handleWorldChunk(WorldChunkS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        if (packet.isFull()) {
            if (packet.getSections() == 0) {
                this.world.updateChunk(packet.getChunkX(), packet.getChunkZ(), false);
                return;
            }

            this.world.updateChunk(packet.getChunkX(), packet.getChunkZ(), true);
        }

        this.world.clearBlockResets(packet.getChunkX() << 4, 0, packet.getChunkZ() << 4, (packet.getChunkX() << 4) + 15, 256, (packet.getChunkZ() << 4) + 15);
        WorldChunk worldchunk = this.world.getChunkAt(packet.getChunkX(), packet.getChunkZ());
        worldchunk.update(packet.getChunkData(), packet.getSections(), packet.isFull());
        this.world
            .notifyRegionChanged(packet.getChunkX() << 4, 0, packet.getChunkZ() << 4, (packet.getChunkX() << 4) + 15, 256, (packet.getChunkZ() << 4) + 15);
        if (!packet.isFull() || !(this.world.dimension instanceof OverworldDimension)) {
            worldchunk.resetBorderLightChecks();
        }
    }

    @Override
    public void handleBlockUpdate(BlockUpdateS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        this.world.setBlockStateFromPacket(packet.getPos(), packet.getBlockState());
    }

    @Override
    public void handleDisconnect(DisconnectS2CPacket packet) {
        this.connection.disconnect(packet.getReason());
    }

    @Override
    public void onDisconnect(Text reason) {
        this.minecraft.setWorld(null);
        if (this.callbackScreen != null) {
            if (this.callbackScreen instanceof RealmsScreenProxy) {
                this.minecraft
                    .openScreen(new DisconnectedRealmsScreen(((RealmsScreenProxy)this.callbackScreen).getDelegate(), "disconnect.lost", reason).getProxy());
            } else {
                this.minecraft.openScreen(new DisconnectedScreen(this.callbackScreen, "disconnect.lost", reason));
            }
        } else {
            this.minecraft.openScreen(new DisconnectedScreen(new MultiplayerScreen(new TitleScreen()), "disconnect.lost", reason));
        }
    }

    public void sendPacket(Packet packet) {
        this.connection.send(packet);
    }

    @Override
    public void handleEntityPickup(EntityPickupS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = this.world.getEntity(packet.getId());
        LivingEntity livingentity = (LivingEntity)this.world.getEntity(packet.getCollectorId());
        if (livingentity == null) {
            livingentity = this.minecraft.player;
        }

        if (entity != null) {
            if (entity instanceof ExperienceOrbEntity) {
                this.world.playSound((Entity)entity, "random.orb", 0.2F, ((this.random.nextFloat() - this.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
            } else {
                this.world.playSound((Entity)entity, "random.pop", 0.2F, ((this.random.nextFloat() - this.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
            }

            this.minecraft.particleManager.add(new EntityPickupParticle(this.world, entity, livingentity, 0.5F));
            this.world.removeEntity(packet.getId());
        }
    }

    @Override
    public void handleChatMessage(ChatMessageS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        if (packet.getType() == 2) {
            this.minecraft.gui.setOverlayMessage(packet.getMessage(), false);
        } else {
            this.minecraft.gui.getChat().addMessage(packet.getMessage());
        }
    }

    @Override
    public void handleEntityAnimation(EntityAnimationS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = this.world.getEntity(packet.getId());
        if (entity != null) {
            if (packet.getAction() == 0) {
                LivingEntity livingentity = (LivingEntity)entity;
                livingentity.swingArm();
            } else if (packet.getAction() == 1) {
                entity.animateDamage();
            } else if (packet.getAction() == 2) {
                PlayerEntity playerentity = (PlayerEntity)entity;
                playerentity.wakeUp(false, false, false);
            } else if (packet.getAction() == 4) {
                this.minecraft.particleManager.addEmitter(entity, ParticleType.CRIT);
            } else if (packet.getAction() == 5) {
                this.minecraft.particleManager.addEmitter(entity, ParticleType.CRIT_MAGIC);
            }
        }
    }

    @Override
    public void handlePlayerSleep(PlayerSleepS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        packet.getPlayer(this.world).trySleep(packet.getPos());
    }

    @Override
    public void handleAddMob(AddMobS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        double d0 = packet.getX() / 32.0;
        double d1 = packet.getY() / 32.0;
        double d2 = packet.getZ() / 32.0;
        float f = packet.getYaw() * 360 / 256.0F;
        float f1 = packet.getPitch() * 360 / 256.0F;
        LivingEntity livingentity = (LivingEntity)Entities.create(packet.getType(), this.minecraft.world);
        livingentity.lastKnownX = packet.getX();
        livingentity.lastKnownY = packet.getY();
        livingentity.lastKnownZ = packet.getZ();
        livingentity.bodyYaw = livingentity.headYaw = packet.getHeadYaw() * 360 / 256.0F;
        Entity[] aentity = livingentity.getParts();
        if (aentity != null) {
            int i = packet.getId() - livingentity.getNetworkId();

            for (int j = 0; j < aentity.length; j++) {
                aentity[j].setNetworkId(aentity[j].getNetworkId() + i);
            }
        }

        livingentity.setNetworkId(packet.getId());
        livingentity.updatePositionAndAngles(d0, d1, d2, f, f1);
        livingentity.velocityX = packet.getVelocityX() / 8000.0F;
        livingentity.velocityY = packet.getVelocityY() / 8000.0F;
        livingentity.velocityZ = packet.getVelocityZ() / 8000.0F;
        this.world.forceEntity(packet.getId(), livingentity);
        List<SyncedData.Entry> list = packet.getDataEntries();
        if (list != null) {
            livingentity.getSyncedData().update(list);
        }
    }

    @Override
    public void handleWorldTime(WorldTimeS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        this.minecraft.world.setTime(packet.getTime());
        this.minecraft.world.setTimeOfDay(packet.getTimeOfDay());
    }

    @Override
    public void handleSpawnPoint(SpawnPointS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        this.minecraft.player.setSpawnPoint(packet.getPos(), true);
        this.minecraft.world.getData().setSpawnPoint(packet.getPos());
    }

    @Override
    public void handleAttachEntity(AttachEntityS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = this.world.getEntity(packet.getId());
        Entity entity1 = this.world.getEntity(packet.getAttachedId());
        if (packet.getType() == 0) {
            boolean flag = false;
            if (packet.getId() == this.minecraft.player.getNetworkId()) {
                entity = this.minecraft.player;
                if (entity1 instanceof BoatEntity) {
                    ((BoatEntity)entity1).setEmpty(false);
                }

                flag = entity.vehicle == null && entity1 != null;
            } else if (entity1 instanceof BoatEntity) {
                ((BoatEntity)entity1).setEmpty(true);
            }

            if (entity == null) {
                return;
            }

            entity.startRiding(entity1);
            if (flag) {
                GameOptions gameoptions = this.minecraft.options;
                this.minecraft.gui.setOverlayMessage(I18n.translate("mount.onboard", GameOptions.getKeyName(gameoptions.sneakKey.getKeyCode())), false);
            }
        } else if (packet.getType() == 1 && entity instanceof MobEntity) {
            if (entity1 != null) {
                ((MobEntity)entity).attachLeash(entity1, false);
            } else {
                ((MobEntity)entity).detachLeash(false, false);
            }
        }
    }

    @Override
    public void handleEntityEvent(EntityEventS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = packet.getEntity(this.world);
        if (entity != null) {
            if (packet.getEvent() == 21) {
                this.minecraft.getSoundManager().play(new GuardianAttackSoundInstance((GuardianEntity)entity));
            } else {
                entity.doEvent(packet.getEvent());
            }
        }
    }

    @Override
    public void handlePlayerHealth(PlayerHealthS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        this.minecraft.player.damageTo(packet.getHealth());
        this.minecraft.player.getHungerManager().setFoodLevel(packet.getHunger());
        this.minecraft.player.getHungerManager().setSaturationLevel(packet.getSaturation());
    }

    @Override
    public void handlePlayerXp(PlayerXpS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        this.minecraft.player.setXp(packet.getLevelProgress(), packet.getXp(), packet.getLevel());
    }

    @Override
    public void handlePlayerRespawn(PlayerRespawnS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        if (packet.getDimension() != this.minecraft.player.dimension) {
            this.started = false;
            Scoreboard scoreboard = this.world.getScoreboard();
            this.world = new ClientWorld(
                this,
                new WorldSettings(0L, packet.getGameMode(), false, this.minecraft.world.getData().isHardcore(), packet.getGeneratorType()),
                packet.getDimension(),
                packet.getDifficulty(),
                this.minecraft.profiler
            );
            this.world.setScoreboard(scoreboard);
            this.minecraft.setWorld(this.world);
            this.minecraft.player.dimension = packet.getDimension();
            this.minecraft.openScreen(new DownloadingTerrainScreen(this));
        }

        this.minecraft.respawnPlayer(packet.getDimension());
        this.minecraft.interactionManager.setGameMode(packet.getGameMode());
    }

    @Override
    public void handleExplosion(ExplosionS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Explosion explosion = new Explosion(
            this.minecraft.world, null, packet.getX(), packet.getY(), packet.getZ(), packet.getPower(), packet.getDamagedBlocks()
        );
        explosion.damageBlocks(true);
        this.minecraft.player.velocityX = this.minecraft.player.velocityX + packet.getPlayerVelocityX();
        this.minecraft.player.velocityY = this.minecraft.player.velocityY + packet.getPlayerVelocityY();
        this.minecraft.player.velocityZ = this.minecraft.player.velocityZ + packet.getPlayerVelocityZ();
    }

    @Override
    public void handleOpenInventoryMenu(OpenInventoryMenuS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        LocalClientPlayerEntity localclientplayerentity = this.minecraft.player;
        if ("minecraft:container".equals(packet.getMenuType())) {
            localclientplayerentity.openChestMenu(new SimpleInventory(packet.getDisplayName(), packet.getSize()));
            localclientplayerentity.menu.networkId = packet.getMenuId();
        } else if ("minecraft:villager".equals(packet.getMenuType())) {
            localclientplayerentity.openTraderMenu(new ClientTrader(localclientplayerentity, packet.getDisplayName()));
            localclientplayerentity.menu.networkId = packet.getMenuId();
        } else if ("EntityHorse".equals(packet.getMenuType())) {
            Entity entity = this.world.getEntity(packet.getOwnerId());
            if (entity instanceof HorseBaseEntity) {
                localclientplayerentity.openHorseMenu((HorseBaseEntity)entity, new AnimalInventory(packet.getDisplayName(), packet.getSize()));
                localclientplayerentity.menu.networkId = packet.getMenuId();
            }
        } else if (!packet.hasSize()) {
            localclientplayerentity.openMenu(new EmptyMenuProvider(packet.getMenuType(), packet.getDisplayName()));
            localclientplayerentity.menu.networkId = packet.getMenuId();
        } else {
            MenuInventory menuinventory = new MenuInventory(packet.getMenuType(), packet.getDisplayName(), packet.getSize());
            localclientplayerentity.openChestMenu(menuinventory);
            localclientplayerentity.menu.networkId = packet.getMenuId();
        }
    }

    @Override
    public void handleInventoryMenuSlotContent(InventoryMenuSlotContentS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        PlayerEntity playerentity = this.minecraft.player;
        if (packet.getMenuId() == -1) {
            playerentity.inventory.setCursorItem(packet.getItem());
        } else {
            boolean flag = false;
            if (this.minecraft.screen instanceof CreativeInventoryScreen) {
                CreativeInventoryScreen creativeinventoryscreen = (CreativeInventoryScreen)this.minecraft.screen;
                flag = creativeinventoryscreen.getSelectedTab() != CreativeModeTab.INVENTORY.getId();
            }

            if (packet.getMenuId() == 0 && packet.getSlotId() >= 36 && packet.getSlotId() < 45) {
                ItemStack itemstack = playerentity.playerMenu.getSlot(packet.getSlotId()).getItem();
                if (packet.getItem() != null && (itemstack == null || itemstack.size < packet.getItem().size)) {
                    packet.getItem().popAnimationTime = 5;
                }

                playerentity.playerMenu.setItem(packet.getSlotId(), packet.getItem());
            } else if (packet.getMenuId() == playerentity.menu.networkId && (packet.getMenuId() != 0 || !flag)) {
                playerentity.menu.setItem(packet.getSlotId(), packet.getItem());
            }
        }
    }

    @Override
    public void handleInventoryMenuConfirm(InventoryMenuConfirmS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        InventoryMenu inventorymenu = null;
        PlayerEntity playerentity = this.minecraft.player;
        if (packet.getMenuId() == 0) {
            inventorymenu = playerentity.playerMenu;
        } else if (packet.getMenuId() == playerentity.menu.networkId) {
            inventorymenu = playerentity.menu;
        }

        if (inventorymenu != null && !packet.getAccepted()) {
            this.sendPacket(new InventoryMenuConfirmC2SPacket(packet.getMenuId(), packet.getActionId(), true));
        }
    }

    @Override
    public void handleInventoryMenuContent(InventoryMenuContentS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        PlayerEntity playerentity = this.minecraft.player;
        if (packet.getMenuId() == 0) {
            playerentity.playerMenu.setItems(packet.getItems());
        } else if (packet.getMenuId() == playerentity.menu.networkId) {
            playerentity.menu.setItems(packet.getItems());
        }
    }

    @Override
    public void handleOpenSignEditor(OpenSignEditorS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        BlockEntity blockentity = this.world.getBlockEntity(packet.getPos());
        if (!(blockentity instanceof SignBlockEntity)) {
            blockentity = new SignBlockEntity();
            blockentity.setWorld(this.world);
            blockentity.setPos(packet.getPos());
        }

        this.minecraft.player.openSignEditor((SignBlockEntity)blockentity);
    }

    @Override
    public void handleSignBlockEntityUpdate(SignUpdateS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        boolean flag = false;
        if (this.minecraft.world.isChunkLoaded(packet.getPos())) {
            BlockEntity blockentity = this.minecraft.world.getBlockEntity(packet.getPos());
            if (blockentity instanceof SignBlockEntity) {
                SignBlockEntity signblockentity = (SignBlockEntity)blockentity;
                if (signblockentity.isEditable()) {
                    System.arraycopy(packet.getLines(), 0, signblockentity.lines, 0, 4);
                    signblockentity.markDirty();
                }

                flag = true;
            }
        }

        if (!flag && this.minecraft.player != null) {
            this.minecraft
                .player
                .sendMessage(
                    new LiteralText("Unable to locate sign at " + packet.getPos().getX() + ", " + packet.getPos().getY() + ", " + packet.getPos().getZ())
                );
        }
    }

    @Override
    public void handleBlockEntityUpdate(BlockEntityUpdateS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        if (this.minecraft.world.isChunkLoaded(packet.getPos())) {
            BlockEntity blockentity = this.minecraft.world.getBlockEntity(packet.getPos());
            int i = packet.getType();
            if (i == 1 && blockentity instanceof MobSpawnerBlockEntity
                || i == 2 && blockentity instanceof CommandBlockBlockEntity
                || i == 3 && blockentity instanceof BeaconBlockEntity
                || i == 4 && blockentity instanceof SkullBlockEntity
                || i == 5 && blockentity instanceof FlowerPotBlockEntity
                || i == 6 && blockentity instanceof BannerBlockEntity) {
                blockentity.readNbt(packet.getNbt());
            }
        }
    }

    @Override
    public void handleInventoryMenuData(InventoryMenuDataS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        PlayerEntity playerentity = this.minecraft.player;
        if (playerentity.menu != null && playerentity.menu.networkId == packet.getMenuId()) {
            playerentity.menu.setData(packet.getDataId(), packet.getValue());
        }
    }

    @Override
    public void handleEntityEquipment(EntityEquipmentS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = this.world.getEntity(packet.getId());
        if (entity != null) {
            entity.setEquipment(packet.getEquipmentSlot(), packet.getItem());
        }
    }

    @Override
    public void handleCloseInventoryMenu(CloseInventoryMenuS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        this.minecraft.player.doCloseMenu();
    }

    @Override
    public void handleBlockEvent(BlockEventS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        this.minecraft.world.addBlockEvent(packet.getPos(), packet.getBlock(), packet.getType(), packet.getData());
    }

    @Override
    public void handleBlockMiningProgress(BlockMiningProgressS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        this.minecraft.world.updateBlockMiningProgress(packet.getPlayerId(), packet.getPos(), packet.getProgress());
    }

    @Override
    public void handleWorldChunks(WorldChunksS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);

        for (int i = 0; i < packet.getChunkCount(); i++) {
            int j = packet.getChunkX(i);
            int k = packet.getChunkZ(i);
            this.world.updateChunk(j, k, true);
            this.world.clearBlockResets(j << 4, 0, k << 4, (j << 4) + 15, 256, (k << 4) + 15);
            WorldChunk worldchunk = this.world.getChunkAt(j, k);
            worldchunk.update(packet.getData(i), packet.getSections(i), true);
            this.world.notifyRegionChanged(j << 4, 0, k << 4, (j << 4) + 15, 256, (k << 4) + 15);
            if (!(this.world.dimension instanceof OverworldDimension)) {
                worldchunk.resetBorderLightChecks();
            }
        }
    }

    @Override
    public void handleGameEvent(GameEventS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        PlayerEntity playerentity = this.minecraft.player;
        int i = packet.getEvent();
        float f = packet.getData();
        int j = MathHelper.floor(f + 0.5F);
        if (i >= 0 && i < GameEventS2CPacket.EVENT_MESSAGES.length && GameEventS2CPacket.EVENT_MESSAGES[i] != null) {
            playerentity.addMessage(new TranslatableText(GameEventS2CPacket.EVENT_MESSAGES[i]));
        }

        if (i == 1) {
            this.world.getData().setRaining(true);
            this.world.setRain(0.0F);
        } else if (i == 2) {
            this.world.getData().setRaining(false);
            this.world.setRain(1.0F);
        } else if (i == 3) {
            this.minecraft.interactionManager.setGameMode(WorldSettings.GameMode.byId(j));
        } else if (i == 4) {
            this.minecraft.openScreen(new CreditsScreen());
        } else if (i == 5) {
            GameOptions gameoptions = this.minecraft.options;
            if (f == 0.0F) {
                this.minecraft.openScreen(new DemoScreen());
            } else if (f == 101.0F) {
                this.minecraft
                    .gui
                    .getChat()
                    .addMessage(
                        new TranslatableText(
                            "demo.help.movement",
                            GameOptions.getKeyName(gameoptions.forwardKey.getKeyCode()),
                            GameOptions.getKeyName(gameoptions.leftKey.getKeyCode()),
                            GameOptions.getKeyName(gameoptions.backKey.getKeyCode()),
                            GameOptions.getKeyName(gameoptions.rightKey.getKeyCode())
                        )
                    );
            } else if (f == 102.0F) {
                this.minecraft.gui.getChat().addMessage(new TranslatableText("demo.help.jump", GameOptions.getKeyName(gameoptions.jumpKey.getKeyCode())));
            } else if (f == 103.0F) {
                this.minecraft
                    .gui
                    .getChat()
                    .addMessage(new TranslatableText("demo.help.inventory", GameOptions.getKeyName(gameoptions.inventoryKey.getKeyCode())));
            }
        } else if (i == 6) {
            this.world.playSound(playerentity.x, playerentity.y + playerentity.getEyeHeight(), playerentity.z, "random.successful_hit", 0.18F, 0.45F, false);
        } else if (i == 7) {
            this.world.setRain(f);
        } else if (i == 8) {
            this.world.setThunder(f);
        } else if (i == 10) {
            this.world.addParticle(ParticleType.MOB_APPEARANCE, playerentity.x, playerentity.y, playerentity.z, 0.0, 0.0, 0.0);
            this.world.playSound(playerentity.x, playerentity.y, playerentity.z, "mob.guardian.curse", 1.0F, 1.0F, false);
        }
    }

    @Override
    public void handleMapData(MapDataS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        SavedMapData savedmapdata = FilledMapItem.getMapData(packet.getId(), this.minecraft.world);
        packet.apply(savedmapdata);
        this.minecraft.gameRenderer.getMapRenderer().updateTexture(savedmapdata);
    }

    @Override
    public void handleWorldEvent(WorldEventS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        if (packet.isGlobal()) {
            this.minecraft.world.doGlobalEvent(packet.getEvent(), packet.getPos(), packet.getData());
        } else {
            this.minecraft.world.doEvent(packet.getEvent(), packet.getPos(), packet.getData());
        }
    }

    @Override
    public void handleStats(StatsS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        boolean flag = false;

        for (Entry<Stat, Integer> entry : packet.getStats().entrySet()) {
            Stat stat = entry.getKey();
            int i = entry.getValue();
            if (stat.isAchievement() && i > 0) {
                if (this.hasAchievements && this.minecraft.player.getStats().get(stat) == 0) {
                    AchievementStat achievementstat = (AchievementStat)stat;
                    this.minecraft.toast.set(achievementstat);
                    this.minecraft.getTwitchStream().sendActionMetadata(new AchievementMetadata(achievementstat), 0L);
                    if (stat == Achievements.OPEN_INVENTORY) {
                        this.minecraft.options.showInventoryAchievementHint = false;
                        this.minecraft.options.save();
                    }
                }

                flag = true;
            }

            this.minecraft.player.getStats().set(this.minecraft.player, stat, i);
        }

        if (!this.hasAchievements && !flag && this.minecraft.options.showInventoryAchievementHint) {
            this.minecraft.toast.setTutorial(Achievements.OPEN_INVENTORY);
        }

        this.hasAchievements = true;
        if (this.minecraft.screen instanceof StatsListener) {
            ((StatsListener)this.minecraft.screen).onStatsReady();
        }
    }

    @Override
    public void handleEntityStatusEffect(EntityStatusEffectS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = this.world.getEntity(packet.getId());
        if (entity instanceof LivingEntity) {
            StatusEffectInstance statuseffectinstance = new StatusEffectInstance(
                packet.getEffect(), packet.getDuration(), packet.getAmplifier(), false, packet.getParticles()
            );
            statuseffectinstance.setPermanent(packet.isPermanent());
            ((LivingEntity)entity).addStatusEffect(statuseffectinstance);
        }
    }

    @Override
    public void handlePlayerCombat(PlayerCombatS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = this.world.getEntity(packet.killerId);
        LivingEntity livingentity = entity instanceof LivingEntity ? (LivingEntity)entity : null;
        if (packet.event == PlayerCombatS2CPacket.Event.END_COMBAT) {
            long i = 1000 * packet.duration / 20;
            PlayerCombatMetadata playercombatmetadata = new PlayerCombatMetadata(this.minecraft.player, livingentity);
            this.minecraft.getTwitchStream().sendSpanMetadata(playercombatmetadata, 0L - i, 0L);
        } else if (packet.event == PlayerCombatS2CPacket.Event.ENTITY_DIED) {
            Entity entity1 = this.world.getEntity(packet.playerId);
            if (entity1 instanceof PlayerEntity) {
                PlayerDeathMetadata playerdeathmetadata = new PlayerDeathMetadata((PlayerEntity)entity1, livingentity);
                playerdeathmetadata.setDescription(packet.message);
                this.minecraft.getTwitchStream().sendActionMetadata(playerdeathmetadata, 0L);
            }
        }
    }

    @Override
    public void handleDifficulty(DifficultyS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        this.minecraft.world.getData().setDifficulty(packet.getDifficulty());
        this.minecraft.world.getData().setDifficultyLocked(packet.getLocked());
    }

    @Override
    public void handleCamera(CameraS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = packet.getCamera(this.world);
        if (entity != null) {
            this.minecraft.setCamera(entity);
        }
    }

    @Override
    public void handleWorldBorder(WorldBorderS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        packet.apply(this.world.getWorldBorder());
    }

    @Override
    public void handleTitles(TitlesS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        TitlesS2CPacket.Type titless2cpacket$type = packet.getType();
        String s = null;
        String s1 = null;
        String s2 = packet.getText() != null ? packet.getText().getFormattedString() : "";
        switch (titless2cpacket$type) {
            case TITLE:
                s = s2;
                break;
            case SUBTITLE:
                s1 = s2;
                break;
            case RESET:
                this.minecraft.gui.setTitles("", "", -1, -1, -1);
                this.minecraft.gui.resetTitleTimes();
                return;
        }

        this.minecraft.gui.setTitles(s, s1, packet.getFadeIn(), packet.getDuration(), packet.getFadeOut());
    }

    @Override
    public void handleCompressionThreshold(CompressionThresholdS2CPacket packet) {
        if (!this.connection.isLocal()) {
            this.connection.setCompressionThreshold(packet.getCompressionThreshold());
        }
    }

    @Override
    public void handleTabList(TabListS2CPacket packet) {
        this.minecraft.gui.getPlayerTabOverlay().setHeader(packet.getHeader().getFormattedString().length() == 0 ? null : packet.getHeader());
        this.minecraft.gui.getPlayerTabOverlay().setFooter(packet.getFooter().getFormattedString().length() == 0 ? null : packet.getFooter());
    }

    @Override
    public void handleEntityRemoveStatusEffect(EntityRemoveStatusEffectS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = this.world.getEntity(packet.getId());
        if (entity instanceof LivingEntity) {
            ((LivingEntity)entity).removeEffect(packet.getEffect());
        }
    }

    @Override
    public void handlePlayerInfo(PlayerInfoS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);

        for (PlayerInfoS2CPacket.Entry playerinfos2cpacket$entry : packet.getEntries()) {
            if (packet.getAction() == PlayerInfoS2CPacket.Action.REMOVE_PLAYER) {
                this.onlinePlayers.remove(playerinfos2cpacket$entry.getProfile().getId());
            } else {
                PlayerInfo playerinfo = this.onlinePlayers.get(playerinfos2cpacket$entry.getProfile().getId());
                if (packet.getAction() == PlayerInfoS2CPacket.Action.ADD_PLAYER) {
                    playerinfo = new PlayerInfo(playerinfos2cpacket$entry);
                    this.onlinePlayers.put(playerinfo.getProfile().getId(), playerinfo);
                }

                if (playerinfo != null) {
                    switch (packet.getAction()) {
                        case ADD_PLAYER:
                            playerinfo.setGameMode(playerinfos2cpacket$entry.getGameMode());
                            playerinfo.setPing(playerinfos2cpacket$entry.getPing());
                            break;
                        case UPDATE_GAME_MODE:
                            playerinfo.setGameMode(playerinfos2cpacket$entry.getGameMode());
                            break;
                        case UPDATE_PING:
                            playerinfo.setPing(playerinfos2cpacket$entry.getPing());
                            break;
                        case UPDATE_DISPLAY_NAME:
                            playerinfo.setDisplayName(playerinfos2cpacket$entry.getDisplayName());
                    }
                }
            }
        }
    }

    @Override
    public void handleKeepAlive(KeepAliveS2CPacket packet) {
        this.sendPacket(new KeepAliveC2SPacket(packet.getTimeMillis()));
    }

    @Override
    public void handlePlayerAbilities(PlayerAbilitiesS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        PlayerEntity playerentity = this.minecraft.player;
        playerentity.abilities.flying = packet.isFlying();
        playerentity.abilities.creativeMode = packet.isCreativeMode();
        playerentity.abilities.invulnerable = packet.isInvulnerable();
        playerentity.abilities.canFly = packet.allowsFlying();
        playerentity.abilities.setFlySpeed(packet.getFlySpeed());
        playerentity.abilities.setWalkSpeed(packet.getWalkSpeed());
    }

    @Override
    public void handleCommandSuggestions(CommandSuggestionsS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        String[] astring = packet.getSuggestions();
        if (this.minecraft.screen instanceof ChatScreen) {
            ChatScreen chatscreen = (ChatScreen)this.minecraft.screen;
            chatscreen.setMessageHistory(astring);
        }
    }

    @Override
    public void handleSoundEvent(SoundEventS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        this.minecraft.world.playSound(packet.getX(), packet.getY(), packet.getZ(), packet.getSound(), packet.getVolume(), packet.getPitch(), false);
    }

    @Override
    public void handleResourcePack(ResourcePackS2CPacket packet) {
        final String s = packet.getUrl();
        final String s1 = packet.getHash();
        if (s.startsWith("level://")) {
            String s2 = s.substring("level://".length());
            File file1 = new File(this.minecraft.gameDir, "saves");
            File file2 = new File(file1, s2);
            if (file2.isFile()) {
                this.connection.send(new ResourcePackC2SPacket(s1, ResourcePackC2SPacket.Response.ACCEPTED));
                Futures.addCallback(this.minecraft.getResourcePacks().applyServerPack(file2), new FutureCallback<Object>() {
                    @Override
                    public void onSuccess(Object result) {
                        ClientPlayNetworkHandler.this.connection.send(new ResourcePackC2SPacket(s1, ResourcePackC2SPacket.Response.SUCCESSFULLY_LOADED));
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        ClientPlayNetworkHandler.this.connection.send(new ResourcePackC2SPacket(s1, ResourcePackC2SPacket.Response.FAILED_DOWNLOAD));
                    }
                });
            } else {
                this.connection.send(new ResourcePackC2SPacket(s1, ResourcePackC2SPacket.Response.FAILED_DOWNLOAD));
            }
        } else {
            if (this.minecraft.getCurrentServerEntry() != null
                && this.minecraft.getCurrentServerEntry().getResourcePackStatus() == ServerListEntry.ResourcePackStatus.ENABLED) {
                this.connection.send(new ResourcePackC2SPacket(s1, ResourcePackC2SPacket.Response.ACCEPTED));
                Futures.addCallback(this.minecraft.getResourcePacks().downloadServerPack(s, s1), new FutureCallback<Object>() {
                    @Override
                    public void onSuccess(Object object) {
                        ClientPlayNetworkHandler.this.connection.send(new ResourcePackC2SPacket(s1, ResourcePackC2SPacket.Response.SUCCESSFULLY_LOADED));
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        ClientPlayNetworkHandler.this.connection.send(new ResourcePackC2SPacket(s1, ResourcePackC2SPacket.Response.FAILED_DOWNLOAD));
                    }
                });
            } else if (this.minecraft.getCurrentServerEntry() != null
                && this.minecraft.getCurrentServerEntry().getResourcePackStatus() != ServerListEntry.ResourcePackStatus.PROMPT) {
                this.connection.send(new ResourcePackC2SPacket(s1, ResourcePackC2SPacket.Response.DECLINED));
            } else {
                this.minecraft
                    .execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                ClientPlayNetworkHandler.this.minecraft
                                    .openScreen(
                                        new ConfirmScreen(
                                            new ConfirmationListener() {
                                                @Override
                                                public void confirmResult(boolean result, int id) {
                                                    ClientPlayNetworkHandler.this.minecraft = Minecraft.getInstance();
                                                    if (result) {
                                                        if (ClientPlayNetworkHandler.this.minecraft.getCurrentServerEntry() != null) {
                                                            ClientPlayNetworkHandler.this.minecraft
                                                                .getCurrentServerEntry()
                                                                .setResourcePackStatus(ServerListEntry.ResourcePackStatus.ENABLED);
                                                        }

                                                        ClientPlayNetworkHandler.this.connection
                                                            .send(new ResourcePackC2SPacket(s1, ResourcePackC2SPacket.Response.ACCEPTED));
                                                        Futures.addCallback(
                                                            ClientPlayNetworkHandler.this.minecraft.getResourcePacks().downloadServerPack(s, s1),
                                                            new FutureCallback<Object>() {
                                                                @Override
                                                                public void onSuccess(Object object) {
                                                                    ClientPlayNetworkHandler.this.connection
                                                                        .send(new ResourcePackC2SPacket(s1, ResourcePackC2SPacket.Response.SUCCESSFULLY_LOADED));
                                                                }

                                                                @Override
                                                                public void onFailure(Throwable throwable) {
                                                                    ClientPlayNetworkHandler.this.connection
                                                                        .send(new ResourcePackC2SPacket(s1, ResourcePackC2SPacket.Response.FAILED_DOWNLOAD));
                                                                }
                                                            }
                                                        );
                                                    } else {
                                                        if (ClientPlayNetworkHandler.this.minecraft.getCurrentServerEntry() != null) {
                                                            ClientPlayNetworkHandler.this.minecraft
                                                                .getCurrentServerEntry()
                                                                .setResourcePackStatus(ServerListEntry.ResourcePackStatus.DISABLED);
                                                        }

                                                        ClientPlayNetworkHandler.this.connection
                                                            .send(new ResourcePackC2SPacket(s1, ResourcePackC2SPacket.Response.DECLINED));
                                                    }

                                                    ServerList.update(ClientPlayNetworkHandler.this.minecraft.getCurrentServerEntry());
                                                    ClientPlayNetworkHandler.this.minecraft.openScreen(null);
                                                }
                                            },
                                            I18n.translate("multiplayer.texturePrompt.line1"),
                                            I18n.translate("multiplayer.texturePrompt.line2"),
                                            0
                                        )
                                    );
                            }
                        }
                    );
            }
        }
    }

    @Override
    public void handleEntitySync(EntitySyncS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = packet.getEntity(this.world);
        if (entity != null) {
            entity.syncNbt(packet.getNbt());
        }
    }

    @Override
    public void handleCustomPayload(CustomPayloadS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        if ("MC|TrList".equals(packet.getChannel())) {
            PacketByteBuf packetbytebuf = packet.getData();

            try {
                int i = packetbytebuf.readInt();
                Screen screen = this.minecraft.screen;
                if (screen != null && screen instanceof VillagerScreen && i == this.minecraft.player.menu.networkId) {
                    Trader trader = ((VillagerScreen)screen).getTrader();
                    TradeOffers tradeoffers = TradeOffers.deserialize(packetbytebuf);
                    trader.setOffers(tradeoffers);
                }
            } catch (IOException ioexception) {
                LOGGER.error("Couldn't load trade info", ioexception);
            } finally {
                packetbytebuf.release();
            }
        } else if ("MC|Brand".equals(packet.getChannel())) {
            this.minecraft.player.setServerBrand(packet.getData().readString(32767));
        } else if ("MC|BOpen".equals(packet.getChannel())) {
            ItemStack itemstack = this.minecraft.player.getItemInHand();
            if (itemstack != null && itemstack.getItem() == Items.WRITTEN_BOOK) {
                this.minecraft.openScreen(new BookEditScreen(this.minecraft.player, itemstack, false));
            }
        }
    }

    @Override
    public void handleScoreboardObjective(ScoreboardObjectiveS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Scoreboard scoreboard = this.world.getScoreboard();
        if (packet.getAction() == 0) {
            ScoreboardObjective scoreboardobjective = scoreboard.createObjective(packet.getName(), ScoreboardCriterion.DUMMY);
            scoreboardobjective.setDisplayName(packet.getDisplayName());
            scoreboardobjective.setRenderType(packet.getRenderType());
        } else {
            ScoreboardObjective scoreboardobjective1 = scoreboard.getObjective(packet.getName());
            if (packet.getAction() == 1) {
                scoreboard.removeObjective(scoreboardobjective1);
            } else if (packet.getAction() == 2) {
                scoreboardobjective1.setDisplayName(packet.getDisplayName());
                scoreboardobjective1.setRenderType(packet.getRenderType());
            }
        }
    }

    @Override
    public void handleScoreboardScore(ScoreboardScoreS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Scoreboard scoreboard = this.world.getScoreboard();
        ScoreboardObjective scoreboardobjective = scoreboard.getObjective(packet.getObjective());
        if (packet.getUpdateMode() == ScoreboardScoreS2CPacket.UpdateMode.CHANGE) {
            ScoreboardScore scoreboardscore = scoreboard.getScore(packet.getOwner(), scoreboardobjective);
            scoreboardscore.set(packet.getScore());
        } else if (packet.getUpdateMode() == ScoreboardScoreS2CPacket.UpdateMode.REMOVE) {
            if (StringUtils.isStringEmpty(packet.getObjective())) {
                scoreboard.removeScore(packet.getOwner(), null);
            } else if (scoreboardobjective != null) {
                scoreboard.removeScore(packet.getOwner(), scoreboardobjective);
            }
        }
    }

    @Override
    public void handleScoreboardDisplay(ScoreboardDisplayS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Scoreboard scoreboard = this.world.getScoreboard();
        if (packet.getObjective().length() == 0) {
            scoreboard.setDisplayObjective(packet.getSlot(), null);
        } else {
            ScoreboardObjective scoreboardobjective = scoreboard.getObjective(packet.getObjective());
            scoreboard.setDisplayObjective(packet.getSlot(), scoreboardobjective);
        }
    }

    @Override
    public void handleTeam(TeamS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Scoreboard scoreboard = this.world.getScoreboard();
        Team team;
        if (packet.getAction() == 0) {
            team = scoreboard.addTeam(packet.getName());
        } else {
            team = scoreboard.getTeam(packet.getName());
        }

        if (packet.getAction() == 0 || packet.getAction() == 2) {
            team.setDisplayName(packet.getDisplayName());
            team.setPrefix(packet.getPrefix());
            team.setSuffix(packet.getSuffix());
            team.setColor(Formatting.byId(packet.getColor()));
            team.unpackFriendlyFlags(packet.getFlags());
            AbstractTeam.Visibility abstractteam$visibility = AbstractTeam.Visibility.byKey(packet.getNameTagVisibility());
            if (abstractteam$visibility != null) {
                team.setNameTagVisibility(abstractteam$visibility);
            }
        }

        if (packet.getAction() == 0 || packet.getAction() == 3) {
            for (String s : packet.getMembers()) {
                scoreboard.addMemberToTeam(s, packet.getName());
            }
        }

        if (packet.getAction() == 4) {
            for (String s1 : packet.getMembers()) {
                scoreboard.removeMemberFromTeam(s1, team);
            }
        }

        if (packet.getAction() == 1) {
            scoreboard.removeTeam(team);
        }
    }

    @Override
    public void handleParticle(ParticleS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        if (packet.getCount() == 0) {
            double d0 = packet.getVelocityScale() * packet.getVelocityX();
            double d2 = packet.getVelocityScale() * packet.getVelocityY();
            double d4 = packet.getVelocityScale() * packet.getVelocityZ();

            try {
                this.world
                    .addParticle(packet.getType(), packet.getIgnoreDistance(), packet.getX(), packet.getY(), packet.getZ(), d0, d2, d4, packet.getParameters());
            } catch (Throwable throwable1) {
                LOGGER.warn("Could not spawn particle effect " + packet.getType());
            }
        } else {
            for (int i = 0; i < packet.getCount(); i++) {
                double d1 = this.random.nextGaussian() * packet.getVelocityX();
                double d3 = this.random.nextGaussian() * packet.getVelocityY();
                double d5 = this.random.nextGaussian() * packet.getVelocityZ();
                double d6 = this.random.nextGaussian() * packet.getVelocityScale();
                double d7 = this.random.nextGaussian() * packet.getVelocityScale();
                double d8 = this.random.nextGaussian() * packet.getVelocityScale();

                try {
                    this.world
                        .addParticle(
                            packet.getType(),
                            packet.getIgnoreDistance(),
                            packet.getX() + d1,
                            packet.getY() + d3,
                            packet.getZ() + d5,
                            d6,
                            d7,
                            d8,
                            packet.getParameters()
                        );
                } catch (Throwable throwable) {
                    LOGGER.warn("Could not spawn particle effect " + packet.getType());
                    return;
                }
            }
        }
    }

    @Override
    public void handleEntityAttributes(EntityAttributesS2CPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.minecraft);
        Entity entity = this.world.getEntity(packet.getEntityId());
        if (entity != null) {
            if (!(entity instanceof LivingEntity)) {
                throw new IllegalStateException("Server tried to update attributes of a non-living entity (actually: " + entity + ")");
            }

            AbstractEntityAttributeContainer abstractentityattributecontainer = ((LivingEntity)entity).getAttributes();

            for (EntityAttributesS2CPacket.Entry entityattributess2cpacket$entry : packet.getEntries()) {
                EntityAttributeInstance entityattributeinstance = abstractentityattributecontainer.get(entityattributess2cpacket$entry.getId());
                if (entityattributeinstance == null) {
                    entityattributeinstance = abstractentityattributecontainer.register(
                        new RangedEntityAttribute(null, entityattributess2cpacket$entry.getId(), 0.0, Double.MIN_NORMAL, Double.MAX_VALUE)
                    );
                }

                entityattributeinstance.setBase(entityattributess2cpacket$entry.getBaseValue());
                entityattributeinstance.clearModifiers();

                for (AttributeModifier attributemodifier : entityattributess2cpacket$entry.getModifiers()) {
                    entityattributeinstance.addModifier(attributemodifier);
                }
            }
        }
    }

    public Connection getConnection() {
        return this.connection;
    }

    public Collection<PlayerInfo> getOnlinePlayers() {
        return this.onlinePlayers.values();
    }

    public PlayerInfo getOnlinePlayer(UUID uuid) {
        return this.onlinePlayers.get(uuid);
    }

    public PlayerInfo getOnlinePlayer(String name) {
        for (PlayerInfo playerinfo : this.onlinePlayers.values()) {
            if (playerinfo.getProfile().getName().equals(name)) {
                return playerinfo;
            }
        }

        return null;
    }

    public GameProfile getProfile() {
        return this.profile;
    }
}
