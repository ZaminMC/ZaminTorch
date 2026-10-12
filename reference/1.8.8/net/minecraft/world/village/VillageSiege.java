package net.minecraft.world.village;

import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.NaturalSpawner;
import net.minecraft.world.World;

public class VillageSiege {
    private World world;
    private boolean ready;
    private int state = -1;
    private int zombieCount;
    private int cooldown;
    private Village village;
    private int siegeX;
    private int siegeY;
    private int siegeZ;

    public VillageSiege(World world) {
        this.world = world;
    }

    public void tick() {
        if (this.world.isSunny()) {
            this.state = 0;
        } else if (this.state != 2) {
            if (this.state == 0) {
                float f = this.world.getTimeOfDay(0.0F);
                if (f < 0.5 || f > 0.501) {
                    return;
                }

                this.state = this.world.random.nextInt(10) == 0 ? 1 : 2;
                this.ready = false;
                if (this.state == 2) {
                    return;
                }
            }

            if (this.state != -1) {
                if (!this.ready) {
                    if (!this.initSiege()) {
                        return;
                    }

                    this.ready = true;
                }

                if (this.cooldown > 0) {
                    this.cooldown--;
                } else {
                    this.cooldown = 2;
                    if (this.zombieCount > 0) {
                        this.spawnZombies();
                        this.zombieCount--;
                    } else {
                        this.state = 2;
                    }
                }
            }
        }
    }

    private boolean initSiege() {
        for (PlayerEntity playerentity : this.world.players) {
            if (!playerentity.isSpectator()) {
                this.village = this.world.getVillages().getNearestVillage(new BlockPos(playerentity), 1);
                if (this.village != null
                    && this.village.getDoorCount() >= 10
                    && this.village.getTicksSinceUpdate() >= 20
                    && this.village.getPopulationSize() >= 20) {
                    BlockPos blockpos = this.village.getCenter();
                    float f = this.village.getRadius();
                    boolean flag = false;

                    for (int i = 0; i < 10; i++) {
                        float f1 = this.world.random.nextFloat() * (float) Math.PI * 2.0F;
                        this.siegeX = blockpos.getX() + (int)(MathHelper.cos(f1) * f * 0.9);
                        this.siegeY = blockpos.getY();
                        this.siegeZ = blockpos.getZ() + (int)(MathHelper.sin(f1) * f * 0.9);
                        flag = false;

                        for (Village village : this.world.getVillages().getVillages()) {
                            if (village != this.village && village.contains(new BlockPos(this.siegeX, this.siegeY, this.siegeZ))) {
                                flag = true;
                                break;
                            }
                        }

                        if (!flag) {
                            break;
                        }
                    }

                    if (flag) {
                        return false;
                    }

                    Vec3d vec3d = this.findSpawnPos(new BlockPos(this.siegeX, this.siegeY, this.siegeZ));
                    if (vec3d != null) {
                        this.cooldown = 0;
                        this.zombieCount = 20;
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean spawnZombies() {
        Vec3d vec3d = this.findSpawnPos(new BlockPos(this.siegeX, this.siegeY, this.siegeZ));
        if (vec3d == null) {
            return false;
        }

        ZombieEntity zombieentity;
        try {
            zombieentity = new ZombieEntity(this.world);
            zombieentity.initialize(this.world.getLocalDifficulty(new BlockPos(zombieentity)), null);
            zombieentity.setType(false);
        } catch (Exception exception) {
            exception.printStackTrace();
            return false;
        }

        zombieentity.setPositionAndAngles(vec3d.x, vec3d.y, vec3d.z, this.world.random.nextFloat() * 360.0F, 0.0F);
        this.world.addEntity(zombieentity);
        BlockPos blockpos = this.village.getCenter();
        zombieentity.setVillagePosAndRadius(blockpos, this.village.getRadius());
        return true;
    }

    private Vec3d findSpawnPos(BlockPos siegePos) {
        for (int i = 0; i < 10; i++) {
            BlockPos blockpos = siegePos.add(this.world.random.nextInt(16) - 8, this.world.random.nextInt(6) - 3, this.world.random.nextInt(16) - 8);
            if (this.village.contains(blockpos) && NaturalSpawner.isValidSpawnPos(MobEntity.SpawnEnvironment.ON_GROUND, this.world, blockpos)) {
                return new Vec3d(blockpos.getX(), blockpos.getY(), blockpos.getZ());
            }
        }

        return null;
    }
}
