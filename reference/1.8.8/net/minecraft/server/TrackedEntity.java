package net.minecraft.server;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.entity.EnderCrystalEntity;
import net.minecraft.entity.EnderEyeEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.FireworksEntity;
import net.minecraft.entity.FishingBobberEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.PrimedTntEntity;
import net.minecraft.entity.SpawnableEntity;
import net.minecraft.entity.data.SyncedData;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.LeadKnotEntity;
import net.minecraft.entity.decoration.PaintingEntity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributeContainer;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.EggEntity;
import net.minecraft.entity.projectile.EnderPearlEntity;
import net.minecraft.entity.projectile.ExperienceBottleEntity;
import net.minecraft.entity.projectile.PotionEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.SnowballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.AddEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.AddExperienceOrbS2CPacket;
import net.minecraft.network.packet.s2c.play.AddMobS2CPacket;
import net.minecraft.network.packet.s2c.play.AddPaintingS2CPacket;
import net.minecraft.network.packet.s2c.play.AddPlayerS2CPacket;
import net.minecraft.network.packet.s2c.play.AttachEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDataS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityHeadAnglesS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTeleportS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSleepS2CPacket;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.map.SavedMapData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TrackedEntity {
    private static final Logger LOGGER = LogManager.getLogger();
    public Entity entity;
    public int trackingRange;
    public int updateInterval;
    public int lastX;
    public int lastY;
    public int lastZ;
    public int lastYaw;
    public int lastPitch;
    public int lastHeadYaw;
    public double velocityX;
    public double velocityY;
    public double velocityZ;
    public int ticks;
    private double x;
    private double y;
    private double z;
    private boolean initialized;
    private boolean syncVelocity;
    private int ticksWithoutVehicle;
    private Entity vehicle;
    private boolean riding;
    private boolean onGround;
    public boolean moved;
    public Set<ServerPlayerEntity> players = Sets.newHashSet();

    public TrackedEntity(Entity entity, int trackingRange, int updateInterval, boolean syncVelocity) {
        this.entity = entity;
        this.trackingRange = trackingRange;
        this.updateInterval = updateInterval;
        this.syncVelocity = syncVelocity;
        this.lastX = MathHelper.floor(entity.x * 32.0);
        this.lastY = MathHelper.floor(entity.y * 32.0);
        this.lastZ = MathHelper.floor(entity.z * 32.0);
        this.lastYaw = MathHelper.floor(entity.yaw * 256.0F / 360.0F);
        this.lastPitch = MathHelper.floor(entity.pitch * 256.0F / 360.0F);
        this.lastHeadYaw = MathHelper.floor(entity.getHeadYaw() * 256.0F / 360.0F);
        this.onGround = entity.onGround;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof TrackedEntity && ((TrackedEntity)object).entity.getNetworkId() == this.entity.getNetworkId();
    }

    @Override
    public int hashCode() {
        return this.entity.getNetworkId();
    }

    public void tick(List<PlayerEntity> players) {
        this.moved = false;
        if (!this.initialized || this.entity.squaredDistanceTo(this.x, this.y, this.z) > 16.0) {
            this.x = this.entity.x;
            this.y = this.entity.y;
            this.z = this.entity.z;
            this.initialized = true;
            this.moved = true;
            this.updatePlayers(players);
        }

        if (this.vehicle != this.entity.vehicle || this.entity.vehicle != null && this.ticks % 60 == 0) {
            this.vehicle = this.entity.vehicle;
            this.sendPacket(new AttachEntityS2CPacket(0, this.entity, this.entity.vehicle));
        }

        if (this.entity instanceof ItemFrameEntity && this.ticks % 10 == 0) {
            ItemFrameEntity itemframeentity = (ItemFrameEntity)this.entity;
            ItemStack itemstack = itemframeentity.getDisplayItem();
            if (itemstack != null && itemstack.getItem() instanceof FilledMapItem) {
                SavedMapData savedmapdata = Items.FILLED_MAP.getSavedMapData(itemstack, this.entity.world);

                for (PlayerEntity playerentity : players) {
                    ServerPlayerEntity serverplayerentity = (ServerPlayerEntity)playerentity;
                    savedmapdata.tickHolder(serverplayerentity, itemstack);
                    Packet packet = Items.FILLED_MAP.getUpdatePacket(itemstack, this.entity.world, serverplayerentity);
                    if (packet != null) {
                        serverplayerentity.networkHandler.sendPacket(packet);
                    }
                }
            }

            this.sendDataAndAttributes();
        }

        if (this.ticks % this.updateInterval == 0 || this.entity.velocityDirty || this.entity.getSyncedData().isDirty()) {
            if (this.entity.vehicle == null) {
                this.ticksWithoutVehicle++;
                int k = MathHelper.floor(this.entity.x * 32.0);
                int j1 = MathHelper.floor(this.entity.y * 32.0);
                int k1 = MathHelper.floor(this.entity.z * 32.0);
                int l1 = MathHelper.floor(this.entity.yaw * 256.0F / 360.0F);
                int i2 = MathHelper.floor(this.entity.pitch * 256.0F / 360.0F);
                int j2 = k - this.lastX;
                int k2 = j1 - this.lastY;
                int i = k1 - this.lastZ;
                Packet packet1 = null;
                boolean flag = Math.abs(j2) >= 4 || Math.abs(k2) >= 4 || Math.abs(i) >= 4 || this.ticks % 60 == 0;
                boolean flag1 = Math.abs(l1 - this.lastYaw) >= 4 || Math.abs(i2 - this.lastPitch) >= 4;
                if (this.ticks > 0 || this.entity instanceof ArrowEntity) {
                    if (j2 >= -128
                        && j2 < 128
                        && k2 >= -128
                        && k2 < 128
                        && i >= -128
                        && i < 128
                        && this.ticksWithoutVehicle <= 400
                        && !this.riding
                        && this.onGround == this.entity.onGround) {
                        if ((!flag || !flag1) && !(this.entity instanceof ArrowEntity)) {
                            if (flag) {
                                packet1 = new EntityMoveS2CPacket.Position(this.entity.getNetworkId(), (byte)j2, (byte)k2, (byte)i, this.entity.onGround);
                            } else if (flag1) {
                                packet1 = new EntityMoveS2CPacket.Angles(this.entity.getNetworkId(), (byte)l1, (byte)i2, this.entity.onGround);
                            }
                        } else {
                            packet1 = new EntityMoveS2CPacket.PositionAndAngles(
                                this.entity.getNetworkId(), (byte)j2, (byte)k2, (byte)i, (byte)l1, (byte)i2, this.entity.onGround
                            );
                        }
                    } else {
                        this.onGround = this.entity.onGround;
                        this.ticksWithoutVehicle = 0;
                        packet1 = new EntityTeleportS2CPacket(this.entity.getNetworkId(), k, j1, k1, (byte)l1, (byte)i2, this.entity.onGround);
                    }
                }

                if (this.syncVelocity) {
                    double d0 = this.entity.velocityX - this.velocityX;
                    double d1 = this.entity.velocityY - this.velocityY;
                    double d2 = this.entity.velocityZ - this.velocityZ;
                    double d3 = 0.02;
                    double d4 = d0 * d0 + d1 * d1 + d2 * d2;
                    if (d4 > d3 * d3 || d4 > 0.0 && this.entity.velocityX == 0.0 && this.entity.velocityY == 0.0 && this.entity.velocityZ == 0.0) {
                        this.velocityX = this.entity.velocityX;
                        this.velocityY = this.entity.velocityY;
                        this.velocityZ = this.entity.velocityZ;
                        this.sendPacket(new EntityVelocityS2CPacket(this.entity.getNetworkId(), this.velocityX, this.velocityY, this.velocityZ));
                    }
                }

                if (packet1 != null) {
                    this.sendPacket(packet1);
                }

                this.sendDataAndAttributes();
                if (flag) {
                    this.lastX = k;
                    this.lastY = j1;
                    this.lastZ = k1;
                }

                if (flag1) {
                    this.lastYaw = l1;
                    this.lastPitch = i2;
                }

                this.riding = false;
            } else {
                int j = MathHelper.floor(this.entity.yaw * 256.0F / 360.0F);
                int i1 = MathHelper.floor(this.entity.pitch * 256.0F / 360.0F);
                boolean flag2 = Math.abs(j - this.lastYaw) >= 4 || Math.abs(i1 - this.lastPitch) >= 4;
                if (flag2) {
                    this.sendPacket(new EntityMoveS2CPacket.Angles(this.entity.getNetworkId(), (byte)j, (byte)i1, this.entity.onGround));
                    this.lastYaw = j;
                    this.lastPitch = i1;
                }

                this.lastX = MathHelper.floor(this.entity.x * 32.0);
                this.lastY = MathHelper.floor(this.entity.y * 32.0);
                this.lastZ = MathHelper.floor(this.entity.z * 32.0);
                this.sendDataAndAttributes();
                this.riding = true;
            }

            int l = MathHelper.floor(this.entity.getHeadYaw() * 256.0F / 360.0F);
            if (Math.abs(l - this.lastHeadYaw) >= 4) {
                this.sendPacket(new EntityHeadAnglesS2CPacket(this.entity, (byte)l));
                this.lastHeadYaw = l;
            }

            this.entity.velocityDirty = false;
        }

        this.ticks++;
        if (this.entity.damaged) {
            this.sendPacketToAll(new EntityVelocityS2CPacket(this.entity));
            this.entity.damaged = false;
        }
    }

    private void sendDataAndAttributes() {
        SyncedData synceddata = this.entity.getSyncedData();
        if (synceddata.isDirty()) {
            this.sendPacketToAll(new EntityDataS2CPacket(this.entity.getNetworkId(), synceddata, false));
        }

        if (this.entity instanceof LivingEntity) {
            EntityAttributeContainer entityattributecontainer = (EntityAttributeContainer)((LivingEntity)this.entity).getAttributes();
            Set<EntityAttributeInstance> set = entityattributecontainer.getTracked();
            if (!set.isEmpty()) {
                this.sendPacketToAll(new EntityAttributesS2CPacket(this.entity.getNetworkId(), set));
            }

            set.clear();
        }
    }

    public void sendPacket(Packet packet) {
        for (ServerPlayerEntity serverplayerentity : this.players) {
            serverplayerentity.networkHandler.sendPacket(packet);
        }
    }

    public void sendPacketToAll(Packet packet) {
        this.sendPacket(packet);
        if (this.entity instanceof ServerPlayerEntity) {
            ((ServerPlayerEntity)this.entity).networkHandler.sendPacket(packet);
        }
    }

    /**
     * Called when this entity is removed from the world.
     */
    public void onRemoved() {
        for (ServerPlayerEntity serverplayerentity : this.players) {
            serverplayerentity.sendRemoveEntity(this.entity);
        }
    }

    public void removePlayer(ServerPlayerEntity player) {
        if (this.players.contains(player)) {
            player.sendRemoveEntity(this.entity);
            this.players.remove(player);
        }
    }

    public void updatePlayer(ServerPlayerEntity player) {
        if (player != this.entity) {
            if (this.broadcastTo(player)) {
                if (!this.players.contains(player) && (this.isInViewOfPlayer(player) || this.entity.teleporting)) {
                    this.players.add(player);
                    Packet packet = this.createAddEntityPacket();
                    player.networkHandler.sendPacket(packet);
                    if (!this.entity.getSyncedData().isEmpty()) {
                        player.networkHandler.sendPacket(new EntityDataS2CPacket(this.entity.getNetworkId(), this.entity.getSyncedData(), true));
                    }

                    NbtCompound nbtcompound = this.entity.getSyncedNbt();
                    if (nbtcompound != null) {
                        player.networkHandler.sendPacket(new EntitySyncS2CPacket(this.entity.getNetworkId(), nbtcompound));
                    }

                    if (this.entity instanceof LivingEntity) {
                        EntityAttributeContainer entityattributecontainer = (EntityAttributeContainer)((LivingEntity)this.entity).getAttributes();
                        Collection<EntityAttributeInstance> collection = entityattributecontainer.getTrackable();
                        if (!collection.isEmpty()) {
                            player.networkHandler.sendPacket(new EntityAttributesS2CPacket(this.entity.getNetworkId(), collection));
                        }
                    }

                    this.velocityX = this.entity.velocityX;
                    this.velocityY = this.entity.velocityY;
                    this.velocityZ = this.entity.velocityZ;
                    if (this.syncVelocity && !(packet instanceof AddMobS2CPacket)) {
                        player.networkHandler
                            .sendPacket(
                                new EntityVelocityS2CPacket(this.entity.getNetworkId(), this.entity.velocityX, this.entity.velocityY, this.entity.velocityZ)
                            );
                    }

                    if (this.entity.vehicle != null) {
                        player.networkHandler.sendPacket(new AttachEntityS2CPacket(0, this.entity, this.entity.vehicle));
                    }

                    if (this.entity instanceof MobEntity && ((MobEntity)this.entity).getLeashHolder() != null) {
                        player.networkHandler.sendPacket(new AttachEntityS2CPacket(1, this.entity, ((MobEntity)this.entity).getLeashHolder()));
                    }

                    if (this.entity instanceof LivingEntity) {
                        for (int i = 0; i < 5; i++) {
                            ItemStack itemstack = ((LivingEntity)this.entity).getEquipment(i);
                            if (itemstack != null) {
                                player.networkHandler.sendPacket(new EntityEquipmentS2CPacket(this.entity.getNetworkId(), i, itemstack));
                            }
                        }
                    }

                    if (this.entity instanceof PlayerEntity) {
                        PlayerEntity playerentity = (PlayerEntity)this.entity;
                        if (playerentity.isSleeping()) {
                            player.networkHandler.sendPacket(new PlayerSleepS2CPacket(playerentity, new BlockPos(this.entity)));
                        }
                    }

                    if (this.entity instanceof LivingEntity) {
                        LivingEntity livingentity = (LivingEntity)this.entity;

                        for (StatusEffectInstance statuseffectinstance : livingentity.getStatusEffects()) {
                            player.networkHandler.sendPacket(new EntityStatusEffectS2CPacket(this.entity.getNetworkId(), statuseffectinstance));
                        }
                    }
                }
            } else if (this.players.contains(player)) {
                this.players.remove(player);
                player.sendRemoveEntity(this.entity);
            }
        }
    }

    public boolean broadcastTo(ServerPlayerEntity player) {
        double d0 = player.x - this.lastX / 32;
        double d1 = player.z - this.lastZ / 32;
        return d0 >= -this.trackingRange
            && d0 <= this.trackingRange
            && d1 >= -this.trackingRange
            && d1 <= this.trackingRange
            && this.entity.broadcastTo(player);
    }

    private boolean isInViewOfPlayer(ServerPlayerEntity player) {
        return player.getServerWorld().getChunkMap().isChunkWithinView(player, this.entity.chunkX, this.entity.chunkZ);
    }

    public void updatePlayers(List<PlayerEntity> players) {
        for (int i = 0; i < players.size(); i++) {
            this.updatePlayer((ServerPlayerEntity)players.get(i));
        }
    }

    private Packet createAddEntityPacket() {
        if (this.entity.removed) {
            LOGGER.warn("Fetching addPacket for removed entity");
        }

        if (this.entity instanceof ItemEntity) {
            return new AddEntityS2CPacket(this.entity, 2, 1);
        }

        if (this.entity instanceof ServerPlayerEntity) {
            return new AddPlayerS2CPacket((PlayerEntity)this.entity);
        }

        if (this.entity instanceof MinecartEntity) {
            MinecartEntity minecartentity = (MinecartEntity)this.entity;
            return new AddEntityS2CPacket(this.entity, 10, minecartentity.getMinecartType().getIndex());
        }

        if (this.entity instanceof BoatEntity) {
            return new AddEntityS2CPacket(this.entity, 1);
        }

        if (this.entity instanceof SpawnableEntity) {
            this.lastHeadYaw = MathHelper.floor(this.entity.getHeadYaw() * 256.0F / 360.0F);
            return new AddMobS2CPacket((LivingEntity)this.entity);
        }

        if (this.entity instanceof FishingBobberEntity) {
            Entity entity1 = ((FishingBobberEntity)this.entity).thrower;
            return new AddEntityS2CPacket(this.entity, 90, entity1 != null ? entity1.getNetworkId() : this.entity.getNetworkId());
        }

        if (this.entity instanceof ArrowEntity) {
            Entity entity = ((ArrowEntity)this.entity).shooter;
            return new AddEntityS2CPacket(this.entity, 60, entity != null ? entity.getNetworkId() : this.entity.getNetworkId());
        }

        if (this.entity instanceof SnowballEntity) {
            return new AddEntityS2CPacket(this.entity, 61);
        }

        if (this.entity instanceof PotionEntity) {
            return new AddEntityS2CPacket(this.entity, 73, ((PotionEntity)this.entity).getStatusEffect());
        }

        if (this.entity instanceof ExperienceBottleEntity) {
            return new AddEntityS2CPacket(this.entity, 75);
        }

        if (this.entity instanceof EnderPearlEntity) {
            return new AddEntityS2CPacket(this.entity, 65);
        }

        if (this.entity instanceof EnderEyeEntity) {
            return new AddEntityS2CPacket(this.entity, 72);
        }

        if (this.entity instanceof FireworksEntity) {
            return new AddEntityS2CPacket(this.entity, 76);
        }

        if (this.entity instanceof ProjectileEntity) {
            ProjectileEntity projectileentity = (ProjectileEntity)this.entity;
            AddEntityS2CPacket addentitys2cpacket2 = null;
            int i = 63;
            if (this.entity instanceof SmallFireballEntity) {
                i = 64;
            } else if (this.entity instanceof WitherSkullEntity) {
                i = 66;
            }

            if (projectileentity.shooter != null) {
                addentitys2cpacket2 = new AddEntityS2CPacket(this.entity, i, ((ProjectileEntity)this.entity).shooter.getNetworkId());
            } else {
                addentitys2cpacket2 = new AddEntityS2CPacket(this.entity, i, 0);
            }

            addentitys2cpacket2.setVelocityX((int)(projectileentity.accelerationX * 8000.0));
            addentitys2cpacket2.setVelocityY((int)(projectileentity.accelerationY * 8000.0));
            addentitys2cpacket2.setVelocityZ((int)(projectileentity.accelerationZ * 8000.0));
            return addentitys2cpacket2;
        } else if (this.entity instanceof EggEntity) {
            return new AddEntityS2CPacket(this.entity, 62);
        } else if (this.entity instanceof PrimedTntEntity) {
            return new AddEntityS2CPacket(this.entity, 50);
        } else if (this.entity instanceof EnderCrystalEntity) {
            return new AddEntityS2CPacket(this.entity, 51);
        } else if (this.entity instanceof FallingBlockEntity) {
            FallingBlockEntity fallingblockentity = (FallingBlockEntity)this.entity;
            return new AddEntityS2CPacket(this.entity, 70, Block.serialize(fallingblockentity.getBlock()));
        } else if (this.entity instanceof ArmorStandEntity) {
            return new AddEntityS2CPacket(this.entity, 78);
        } else if (this.entity instanceof PaintingEntity) {
            return new AddPaintingS2CPacket((PaintingEntity)this.entity);
        } else if (this.entity instanceof ItemFrameEntity) {
            ItemFrameEntity itemframeentity = (ItemFrameEntity)this.entity;
            AddEntityS2CPacket addentitys2cpacket1 = new AddEntityS2CPacket(this.entity, 71, itemframeentity.dir.getIdHorizontal());
            BlockPos blockpos1 = itemframeentity.getBlockPos();
            addentitys2cpacket1.setX(MathHelper.floor(blockpos1.getX() * 32));
            addentitys2cpacket1.setY(MathHelper.floor(blockpos1.getY() * 32));
            addentitys2cpacket1.setZ(MathHelper.floor(blockpos1.getZ() * 32));
            return addentitys2cpacket1;
        } else if (this.entity instanceof LeadKnotEntity) {
            LeadKnotEntity leadknotentity = (LeadKnotEntity)this.entity;
            AddEntityS2CPacket addentitys2cpacket = new AddEntityS2CPacket(this.entity, 77);
            BlockPos blockpos = leadknotentity.getBlockPos();
            addentitys2cpacket.setX(MathHelper.floor(blockpos.getX() * 32));
            addentitys2cpacket.setY(MathHelper.floor(blockpos.getY() * 32));
            addentitys2cpacket.setZ(MathHelper.floor(blockpos.getZ() * 32));
            return addentitys2cpacket;
        } else if (this.entity instanceof ExperienceOrbEntity) {
            return new AddExperienceOrbS2CPacket((ExperienceOrbEntity)this.entity);
        } else {
            throw new IllegalArgumentException("Don't know how to add " + this.entity.getClass() + "!");
        }
    }

    public void onPlayerRespawn(ServerPlayerEntity player) {
        if (this.players.contains(player)) {
            this.players.remove(player);
            player.sendRemoveEntity(this.entity);
        }
    }
}
