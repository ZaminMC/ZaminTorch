package net.minecraft.server;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
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
import net.minecraft.entity.decoration.DecorationEntity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.mob.ambient.BatEntity;
import net.minecraft.entity.living.mob.monster.boss.EnderDragonEntity;
import net.minecraft.entity.living.mob.monster.boss.WitherEntity;
import net.minecraft.entity.living.mob.water.SquidEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.EggEntity;
import net.minecraft.entity.projectile.EnderPearlEntity;
import net.minecraft.entity.projectile.ExperienceBottleEntity;
import net.minecraft.entity.projectile.PotionEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.SnowballEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Int2ObjectHashMap;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EntityMap {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ServerWorld world;
    private Set<TrackedEntity> entities = Sets.newHashSet();
    private Int2ObjectHashMap<TrackedEntity> entitiesById = new Int2ObjectHashMap<>();
    private int viewDistance;

    public EntityMap(ServerWorld world) {
        this.world = world;
        this.viewDistance = world.getServer().getPlayerManager().getViewDistance();
    }

    /**
     * Called when the given entity is added to the world.
     */
    public void onEntityAdded(Entity entity) {
        if (entity instanceof ServerPlayerEntity) {
            this.startTracking(entity, 512, 2);
            ServerPlayerEntity serverplayerentity = (ServerPlayerEntity)entity;

            for (TrackedEntity trackedentity : this.entities) {
                if (trackedentity.entity != serverplayerentity) {
                    trackedentity.updatePlayer(serverplayerentity);
                }
            }
        } else if (entity instanceof FishingBobberEntity) {
            this.startTracking(entity, 64, 5, true);
        } else if (entity instanceof ArrowEntity) {
            this.startTracking(entity, 64, 20, false);
        } else if (entity instanceof SmallFireballEntity) {
            this.startTracking(entity, 64, 10, false);
        } else if (entity instanceof ProjectileEntity) {
            this.startTracking(entity, 64, 10, false);
        } else if (entity instanceof SnowballEntity) {
            this.startTracking(entity, 64, 10, true);
        } else if (entity instanceof EnderPearlEntity) {
            this.startTracking(entity, 64, 10, true);
        } else if (entity instanceof EnderEyeEntity) {
            this.startTracking(entity, 64, 4, true);
        } else if (entity instanceof EggEntity) {
            this.startTracking(entity, 64, 10, true);
        } else if (entity instanceof PotionEntity) {
            this.startTracking(entity, 64, 10, true);
        } else if (entity instanceof ExperienceBottleEntity) {
            this.startTracking(entity, 64, 10, true);
        } else if (entity instanceof FireworksEntity) {
            this.startTracking(entity, 64, 10, true);
        } else if (entity instanceof ItemEntity) {
            this.startTracking(entity, 64, 20, true);
        } else if (entity instanceof MinecartEntity) {
            this.startTracking(entity, 80, 3, true);
        } else if (entity instanceof BoatEntity) {
            this.startTracking(entity, 80, 3, true);
        } else if (entity instanceof SquidEntity) {
            this.startTracking(entity, 64, 3, true);
        } else if (entity instanceof WitherEntity) {
            this.startTracking(entity, 80, 3, false);
        } else if (entity instanceof BatEntity) {
            this.startTracking(entity, 80, 3, false);
        } else if (entity instanceof EnderDragonEntity) {
            this.startTracking(entity, 160, 3, true);
        } else if (entity instanceof SpawnableEntity) {
            this.startTracking(entity, 80, 3, true);
        } else if (entity instanceof PrimedTntEntity) {
            this.startTracking(entity, 160, 10, true);
        } else if (entity instanceof FallingBlockEntity) {
            this.startTracking(entity, 160, 20, true);
        } else if (entity instanceof DecorationEntity) {
            this.startTracking(entity, 160, Integer.MAX_VALUE, false);
        } else if (entity instanceof ArmorStandEntity) {
            this.startTracking(entity, 160, 3, true);
        } else if (entity instanceof ExperienceOrbEntity) {
            this.startTracking(entity, 160, 20, true);
        } else if (entity instanceof EnderCrystalEntity) {
            this.startTracking(entity, 256, Integer.MAX_VALUE, false);
        }
    }

    public void startTracking(Entity entity, int trackingRange, int updateInterval) {
        this.startTracking(entity, trackingRange, updateInterval, false);
    }

    public void startTracking(Entity entity, int trackingRange, int updateInterval, boolean syncVelocity) {
        if (trackingRange > this.viewDistance) {
            trackingRange = this.viewDistance;
        }

        try {
            if (this.entitiesById.containsKey(entity.getNetworkId())) {
                throw new IllegalStateException("Entity is already tracked!");
            }

            TrackedEntity trackedentity = new TrackedEntity(entity, trackingRange, updateInterval, syncVelocity);
            this.entities.add(trackedentity);
            this.entitiesById.put(entity.getNetworkId(), trackedentity);
            trackedentity.updatePlayers(this.world.players);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Adding entity to track");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Entity To Track");
            crashreportcategory.add("Tracking range", trackingRange + " blocks");
            crashreportcategory.add("Update interval", new Callable<String>() {
                public String call() throws Exception {
                    String s = "Once per " + updateInterval + " ticks";
                    if (updateInterval == Integer.MAX_VALUE) {
                        s = "Maximum (" + s + ")";
                    }

                    return s;
                }
            });
            entity.populateCrashReport(crashreportcategory);
            CrashReportCategory crashreportcategory1 = crashreport.addCategory("Entity That Is Already Tracked");
            this.entitiesById.get(entity.getNetworkId()).entity.populateCrashReport(crashreportcategory1);

            try {
                throw new CrashException(crashreport);
            } catch (CrashException crashexception) {
                LOGGER.error("\"Silently\" catching entity tracking error.", crashexception);
            }
        }
    }

    /**
     * Called when the given entity is removed from the world.
     */
    public void onEntityRemoved(Entity entity) {
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverplayerentity = (ServerPlayerEntity)entity;

            for (TrackedEntity trackedentity : this.entities) {
                trackedentity.removePlayer(serverplayerentity);
            }
        }

        TrackedEntity trackedentity1 = this.entitiesById.remove(entity.getNetworkId());
        if (trackedentity1 != null) {
            this.entities.remove(trackedentity1);
            trackedentity1.onRemoved();
        }
    }

    public void tick() {
        List<ServerPlayerEntity> list = Lists.newArrayList();

        for (TrackedEntity trackedentity : this.entities) {
            trackedentity.tick(this.world.players);
            if (trackedentity.moved && trackedentity.entity instanceof ServerPlayerEntity) {
                list.add((ServerPlayerEntity)trackedentity.entity);
            }
        }

        for (int i = 0; i < list.size(); i++) {
            ServerPlayerEntity serverplayerentity = list.get(i);

            for (TrackedEntity trackedentity1 : this.entities) {
                if (trackedentity1.entity != serverplayerentity) {
                    trackedentity1.updatePlayer(serverplayerentity);
                }
            }
        }
    }

    public void updateVisibility(ServerPlayerEntity player) {
        for (TrackedEntity trackedentity : this.entities) {
            if (trackedentity.entity == player) {
                trackedentity.updatePlayers(this.world.players);
            } else {
                trackedentity.updatePlayer(player);
            }
        }
    }

    public void sendPacket(Entity entity, Packet packet) {
        TrackedEntity trackedentity = this.entitiesById.get(entity.getNetworkId());
        if (trackedentity != null) {
            trackedentity.sendPacket(packet);
        }
    }

    public void sendPacketToAll(Entity entity, Packet packet) {
        TrackedEntity trackedentity = this.entitiesById.get(entity.getNetworkId());
        if (trackedentity != null) {
            trackedentity.sendPacketToAll(packet);
        }
    }

    public void onPlayerRespawn(ServerPlayerEntity player) {
        for (TrackedEntity trackedentity : this.entities) {
            trackedentity.onPlayerRespawn(player);
        }
    }

    public void addPlayer(ServerPlayerEntity player, WorldChunk chunk) {
        for (TrackedEntity trackedentity : this.entities) {
            if (trackedentity.entity != player && trackedentity.entity.chunkX == chunk.chunkX && trackedentity.entity.chunkZ == chunk.chunkZ) {
                trackedentity.updatePlayer(player);
            }
        }
    }
}
