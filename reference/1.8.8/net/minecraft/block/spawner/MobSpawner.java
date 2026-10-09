package net.minecraft.block.spawner;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.StringUtils;
import net.minecraft.util.WeightedPicker;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public abstract class MobSpawner {
    private int delay = 20;
    private String type = "Pig";
    private final List<MobSpawner.Entry> spawnPotentials = Lists.newArrayList();
    private MobSpawner.Entry nextEntry;
    private double rotation;
    private double lastRotation;
    private int minSpawnDelay = 200;
    private int maxSpawnDelay = 800;
    private int spawnCount = 4;
    private Entity displayEntity;
    private int maxNearbyEntities = 6;
    private int requiredPlayerRange = 16;
    private int spawnRange = 4;

    private String getType() {
        if (this.getNextEntry() == null) {
            if (this.type != null && this.type.equals("Minecart")) {
                this.type = "MinecartRideable";
            }

            return this.type;
        } else {
            return this.getNextEntry().type;
        }
    }

    public void setType(String type) {
        this.type = type;
    }

    private boolean isNearPlayer() {
        BlockPos blockpos = this.getPos();
        return this.getWorld().isPlayerWithinRange(blockpos.getX() + 0.5, blockpos.getY() + 0.5, blockpos.getZ() + 0.5, this.requiredPlayerRange);
    }

    public void tick() {
        if (this.isNearPlayer()) {
            BlockPos blockpos = this.getPos();
            if (this.getWorld().isClient) {
                double d0 = blockpos.getX() + this.getWorld().random.nextFloat();
                double d1 = blockpos.getY() + this.getWorld().random.nextFloat();
                double d2 = blockpos.getZ() + this.getWorld().random.nextFloat();
                this.getWorld().addParticle(ParticleType.SMOKE_NORMAL, d0, d1, d2, 0.0, 0.0, 0.0);
                this.getWorld().addParticle(ParticleType.FLAME, d0, d1, d2, 0.0, 0.0, 0.0);
                if (this.delay > 0) {
                    this.delay--;
                }

                this.lastRotation = this.rotation;
                this.rotation = (this.rotation + 1000.0F / (this.delay + 200.0F)) % 360.0;
            } else {
                if (this.delay == -1) {
                    this.delay();
                }

                if (this.delay > 0) {
                    this.delay--;
                    return;
                }

                boolean flag = false;

                for (int i = 0; i < this.spawnCount; i++) {
                    Entity entity = Entities.createSilently(this.getType(), this.getWorld());
                    if (entity == null) {
                        return;
                    }

                    int j = this.getWorld()
                        .getEntitiesOfType(
                            entity.getClass(),
                            new Box(blockpos.getX(), blockpos.getY(), blockpos.getZ(), blockpos.getX() + 1, blockpos.getY() + 1, blockpos.getZ() + 1)
                                .grown(this.spawnRange, this.spawnRange, this.spawnRange)
                        )
                        .size();
                    if (j >= this.maxNearbyEntities) {
                        this.delay();
                        return;
                    }

                    double d5 = blockpos.getX() + (this.getWorld().random.nextDouble() - this.getWorld().random.nextDouble()) * this.spawnRange + 0.5;
                    double d3 = blockpos.getY() + this.getWorld().random.nextInt(3) - 1;
                    double d4 = blockpos.getZ() + (this.getWorld().random.nextDouble() - this.getWorld().random.nextDouble()) * this.spawnRange + 0.5;
                    MobEntity mobentity = entity instanceof MobEntity ? (MobEntity)entity : null;
                    entity.setPositionAndAngles(d5, d3, d4, this.getWorld().random.nextFloat() * 360.0F, 0.0F);
                    if (mobentity == null || mobentity.canSpawn() && mobentity.isUnobstructed()) {
                        this.prepareEntityForSpawning(entity, true);
                        this.getWorld().doEvent(2004, blockpos, 0);
                        if (mobentity != null) {
                            mobentity.animateSpawn();
                        }

                        flag = true;
                    }
                }

                if (flag) {
                    this.delay();
                }
            }
        }
    }

    private Entity prepareEntityForSpawning(Entity entity, boolean doSpawn) {
        if (this.getNextEntry() != null) {
            NbtCompound nbtcompound = new NbtCompound();
            entity.writeNbtIfNotMount(nbtcompound);

            for (String s : this.getNextEntry().properties.getKeys()) {
                NbtElement nbtelement = this.getNextEntry().properties.get(s);
                nbtcompound.put(s, nbtelement.copy());
            }

            entity.readNbt(nbtcompound);
            if (entity.world != null && doSpawn) {
                entity.world.addEntity(entity);
            }

            Entity entityx = entity;

            while (nbtcompound.contains("Riding", 10)) {
                NbtCompound nbtcompound2 = nbtcompound.getCompound("Riding");
                Entity entity1 = Entities.createSilently(nbtcompound2.getString("id"), entity.world);
                if (entity1 != null) {
                    NbtCompound nbtcompound1 = new NbtCompound();
                    entity1.writeNbtIfNotMount(nbtcompound1);

                    for (String s1 : nbtcompound2.getKeys()) {
                        NbtElement nbtelement1 = nbtcompound2.get(s1);
                        nbtcompound1.put(s1, nbtelement1.copy());
                    }

                    entity1.readNbt(nbtcompound1);
                    entity1.setPositionAndAngles(entityx.x, entityx.y, entityx.z, entityx.yaw, entityx.pitch);
                    if (entity.world != null && doSpawn) {
                        entity.world.addEntity(entity1);
                    }

                    entityx.startRiding(entity1);
                }

                entityx = entity1;
                nbtcompound = nbtcompound2;
            }
        } else if (entity instanceof LivingEntity && entity.world != null && doSpawn) {
            if (entity instanceof MobEntity) {
                ((MobEntity)entity).initialize(entity.world.getLocalDifficulty(new BlockPos(entity)), null);
            }

            entity.world.addEntity(entity);
        }

        return entity;
    }

    private void delay() {
        if (this.maxSpawnDelay <= this.minSpawnDelay) {
            this.delay = this.minSpawnDelay;
        } else {
            this.delay = this.minSpawnDelay + this.getWorld().random.nextInt(this.maxSpawnDelay - this.minSpawnDelay);
        }

        if (this.spawnPotentials.size() > 0) {
            this.setNextEntry(WeightedPicker.pick(this.getWorld().random, this.spawnPotentials));
        }

        this.broacastEvent(1);
    }

    public void readNbt(NbtCompound nbt) {
        this.type = nbt.getString("EntityId");
        this.delay = nbt.getShort("Delay");
        this.spawnPotentials.clear();
        if (nbt.contains("SpawnPotentials", 9)) {
            NbtList nbtlist = nbt.getList("SpawnPotentials", 10);

            for (int i = 0; i < nbtlist.size(); i++) {
                this.spawnPotentials.add(new MobSpawner.Entry(nbtlist.getCompound(i)));
            }
        }

        if (nbt.contains("SpawnData", 10)) {
            this.setNextEntry(new MobSpawner.Entry(nbt.getCompound("SpawnData"), this.type));
        } else {
            this.setNextEntry(null);
        }

        if (nbt.contains("MinSpawnDelay", 99)) {
            this.minSpawnDelay = nbt.getShort("MinSpawnDelay");
            this.maxSpawnDelay = nbt.getShort("MaxSpawnDelay");
            this.spawnCount = nbt.getShort("SpawnCount");
        }

        if (nbt.contains("MaxNearbyEntities", 99)) {
            this.maxNearbyEntities = nbt.getShort("MaxNearbyEntities");
            this.requiredPlayerRange = nbt.getShort("RequiredPlayerRange");
        }

        if (nbt.contains("SpawnRange", 99)) {
            this.spawnRange = nbt.getShort("SpawnRange");
        }

        if (this.getWorld() != null) {
            this.displayEntity = null;
        }
    }

    public void writeNbt(NbtCompound nbt) {
        String s = this.getType();
        if (!StringUtils.isStringEmpty(s)) {
            nbt.putString("EntityId", s);
            nbt.putShort("Delay", (short)this.delay);
            nbt.putShort("MinSpawnDelay", (short)this.minSpawnDelay);
            nbt.putShort("MaxSpawnDelay", (short)this.maxSpawnDelay);
            nbt.putShort("SpawnCount", (short)this.spawnCount);
            nbt.putShort("MaxNearbyEntities", (short)this.maxNearbyEntities);
            nbt.putShort("RequiredPlayerRange", (short)this.requiredPlayerRange);
            nbt.putShort("SpawnRange", (short)this.spawnRange);
            if (this.getNextEntry() != null) {
                nbt.put("SpawnData", this.getNextEntry().properties.copy());
            }

            if (this.getNextEntry() != null || this.spawnPotentials.size() > 0) {
                NbtList nbtlist = new NbtList();
                if (this.spawnPotentials.size() > 0) {
                    for (MobSpawner.Entry mobspawner$entry : this.spawnPotentials) {
                        nbtlist.addElement(mobspawner$entry.toNbt());
                    }
                } else {
                    nbtlist.addElement(this.getNextEntry().toNbt());
                }

                nbt.put("SpawnPotentials", nbtlist);
            }
        }
    }

    public Entity getDisplayEntity(World world) {
        if (this.displayEntity == null) {
            Entity entity = Entities.createSilently(this.getType(), world);
            if (entity != null) {
                entity = this.prepareEntityForSpawning(entity, false);
                this.displayEntity = entity;
            }
        }

        return this.displayEntity;
    }

    public boolean doEvent(int event) {
        if (event == 1 && this.getWorld().isClient) {
            this.delay = this.minSpawnDelay;
            return true;
        } else {
            return false;
        }
    }

    private MobSpawner.Entry getNextEntry() {
        return this.nextEntry;
    }

    public void setNextEntry(MobSpawner.Entry entry) {
        this.nextEntry = entry;
    }

    public abstract void broacastEvent(int event);

    public abstract World getWorld();

    public abstract BlockPos getPos();

    public double getRotation() {
        return this.rotation;
    }

    public double getLastRotation() {
        return this.lastRotation;
    }

    public class Entry extends WeightedPicker.Entry {
        private final NbtCompound properties;
        private final String type;

        public Entry(NbtCompound properties) {
            this(properties.getCompound("Properties"), properties.getString("Type"), properties.getInt("Weight"));
        }

        public Entry(NbtCompound nbt, String type) {
            this(nbt, type, 1);
        }

        private Entry(NbtCompound properties, String type, int weight) {
            super(weight);
            if (type.equals("Minecart")) {
                if (properties != null) {
                    type = MinecartEntity.Type.byIndex(properties.getInt("Type")).getName();
                } else {
                    type = "MinecartRideable";
                }
            }

            this.properties = properties;
            this.type = type;
        }

        public NbtCompound toNbt() {
            NbtCompound nbtcompound = new NbtCompound();
            nbtcompound.put("Properties", this.properties);
            nbtcompound.putString("Type", this.type);
            nbtcompound.putInt("Weight", this.weight);
            return nbtcompound;
        }
    }
}
