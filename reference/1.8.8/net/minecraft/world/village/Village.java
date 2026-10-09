package net.minecraft.world.village;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.material.Material;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.GameProfileCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class Village {
    private World world;
    private final List<VillageDoor> doors = Lists.newArrayList();
    /**
     * The sum of all door positions - used to calculate the village center.
     */
    private BlockPos combinedDoorPositions = BlockPos.ORIGIN;
    private BlockPos center = BlockPos.ORIGIN;
    private int radius;
    private int lastUpdateTick;
    private int ticks;
    private int populationSize;
    private int lastMatingTicks;
    private TreeMap<String, Integer> playerReputations = new TreeMap<>();
    private List<Village.Attacker> attackers = Lists.newArrayList();
    private int golems;

    public Village() {
    }

    public Village(World world) {
        this.world = world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public void tick(int ticks) {
        this.ticks = ticks;
        this.removeInvalidDoors();
        this.cleanUpAttackers();
        if (ticks % 20 == 0) {
            this.updatePopulationSize();
        }

        if (ticks % 30 == 0) {
            this.updateIronGolemCount();
        }

        int i = this.populationSize / 10;
        if (this.golems < i && this.doors.size() > 20 && this.world.random.nextInt(7000) == 0) {
            Vec3d vec3d = this.getSpawnPosForIronGolem(this.center, 2, 4, 2);
            if (vec3d != null) {
                IronGolemEntity irongolementity = new IronGolemEntity(this.world);
                irongolementity.setPosition(vec3d.x, vec3d.y, vec3d.z);
                this.world.addEntity(irongolementity);
                this.golems++;
            }
        }
    }

    private Vec3d getSpawnPosForIronGolem(BlockPos center, int rangeX, int rangeY, int rangeZ) {
        for (int i = 0; i < 10; i++) {
            BlockPos blockpos = center.add(this.world.random.nextInt(16) - 8, this.world.random.nextInt(6) - 3, this.world.random.nextInt(16) - 8);
            if (this.contains(blockpos) && this.canIronGolemSpawn(new BlockPos(rangeX, rangeY, rangeZ), blockpos)) {
                return new Vec3d(blockpos.getX(), blockpos.getY(), blockpos.getZ());
            }
        }

        return null;
    }

    private boolean canIronGolemSpawn(BlockPos range, BlockPos pos) {
        if (!World.hasSolidTop(this.world, pos.down())) {
            return false;
        }

        int i = pos.getX() - range.getX() / 2;
        int j = pos.getZ() - range.getZ() / 2;

        for (int k = i; k < i + range.getX(); k++) {
            for (int l = pos.getY(); l < pos.getY() + range.getY(); l++) {
                for (int i1 = j; i1 < j + range.getZ(); i1++) {
                    if (this.world.getBlockState(new BlockPos(k, l, i1)).getBlock().isSolid()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private void updateIronGolemCount() {
        List<IronGolemEntity> list = this.world
            .getEntitiesOfType(
                IronGolemEntity.class,
                new Box(
                    this.center.getX() - this.radius,
                    this.center.getY() - 4,
                    this.center.getZ() - this.radius,
                    this.center.getX() + this.radius,
                    this.center.getY() + 4,
                    this.center.getZ() + this.radius
                )
            );
        this.golems = list.size();
    }

    private void updatePopulationSize() {
        List<VillagerEntity> list = this.world
            .getEntitiesOfType(
                VillagerEntity.class,
                new Box(
                    this.center.getX() - this.radius,
                    this.center.getY() - 4,
                    this.center.getZ() - this.radius,
                    this.center.getX() + this.radius,
                    this.center.getY() + 4,
                    this.center.getZ() + this.radius
                )
            );
        this.populationSize = list.size();
        if (this.populationSize == 0) {
            this.playerReputations.clear();
        }
    }

    public BlockPos getCenter() {
        return this.center;
    }

    public int getRadius() {
        return this.radius;
    }

    public int getDoorCount() {
        return this.doors.size();
    }

    public int getTicksSinceUpdate() {
        return this.ticks - this.lastUpdateTick;
    }

    public int getPopulationSize() {
        return this.populationSize;
    }

    public boolean contains(BlockPos pos) {
        return this.center.squaredDistanceTo(pos) < this.radius * this.radius;
    }

    public List<VillageDoor> getDoors() {
        return this.doors;
    }

    public VillageDoor getNearestDoor(BlockPos pos) {
        VillageDoor villagedoor = null;
        int i = Integer.MAX_VALUE;

        for (VillageDoor villagedoor1 : this.doors) {
            int j = villagedoor1.squaredDistanceTo(pos);
            if (j < i) {
                villagedoor = villagedoor1;
                i = j;
            }
        }

        return villagedoor;
    }

    public VillageDoor getNearestTickingDoor(BlockPos pos) {
        VillageDoor villagedoor = null;
        int i = Integer.MAX_VALUE;

        for (VillageDoor villagedoor1 : this.doors) {
            int j = villagedoor1.squaredDistanceTo(pos);
            if (j > 256) {
                j *= 1000;
            } else {
                j = villagedoor1.getRestrictedTicks();
            }

            if (j < i) {
                villagedoor = villagedoor1;
                i = j;
            }
        }

        return villagedoor;
    }

    public VillageDoor getDoor(BlockPos pos) {
        if (this.center.squaredDistanceTo(pos) > this.radius * this.radius) {
            return null;
        }

        for (VillageDoor villagedoor : this.doors) {
            if (villagedoor.getPos().getX() == pos.getX()
                && villagedoor.getPos().getZ() == pos.getZ()
                && Math.abs(villagedoor.getPos().getY() - pos.getY()) <= 1) {
                return villagedoor;
            }
        }

        return null;
    }

    public void addDoor(VillageDoor door) {
        this.doors.add(door);
        this.combinedDoorPositions = this.combinedDoorPositions.add(door.getPos());
        this.updateCenterAndRadius();
        this.lastUpdateTick = door.getLastUpdateTick();
    }

    public boolean isEmpty() {
        return this.doors.isEmpty();
    }

    public void addOrUpdateAttacker(LivingEntity attacker) {
        for (Village.Attacker village$attacker : this.attackers) {
            if (village$attacker.attacker == attacker) {
                village$attacker.lastUpdateTick = this.ticks;
                return;
            }
        }

        this.attackers.add(new Village.Attacker(attacker, this.ticks));
    }

    public LivingEntity getNearestAttacker(LivingEntity entity) {
        double d0 = Double.MAX_VALUE;
        Village.Attacker village$attacker = null;

        for (int i = 0; i < this.attackers.size(); i++) {
            Village.Attacker village$attacker1 = this.attackers.get(i);
            double d1 = village$attacker1.attacker.squaredDistanceTo(entity);
            if (!(d1 > d0)) {
                village$attacker = village$attacker1;
                d0 = d1;
            }
        }

        return village$attacker != null ? village$attacker.attacker : null;
    }

    public PlayerEntity getNearestPlayer(LivingEntity entity) {
        double d0 = Double.MAX_VALUE;
        PlayerEntity playerentity = null;

        for (String s : this.playerReputations.keySet()) {
            if (this.hasBadReputation(s)) {
                PlayerEntity playerentity1 = this.world.getPlayer(s);
                if (playerentity1 != null) {
                    double d1 = playerentity1.squaredDistanceTo(entity);
                    if (!(d1 > d0)) {
                        playerentity = playerentity1;
                        d0 = d1;
                    }
                }
            }
        }

        return playerentity;
    }

    private void cleanUpAttackers() {
        Iterator<Village.Attacker> iterator = this.attackers.iterator();

        while (iterator.hasNext()) {
            Village.Attacker village$attacker = iterator.next();
            if (!village$attacker.attacker.isAlive() || Math.abs(this.ticks - village$attacker.lastUpdateTick) > 300) {
                iterator.remove();
            }
        }
    }

    private void removeInvalidDoors() {
        boolean flag = false;
        boolean flag1 = this.world.random.nextInt(50) == 0;
        Iterator<VillageDoor> iterator = this.doors.iterator();

        while (iterator.hasNext()) {
            VillageDoor villagedoor = iterator.next();
            if (flag1) {
                villagedoor.resetRestrictedTicks();
            }

            if (!this.isWoodenDoor(villagedoor.getPos()) || Math.abs(this.ticks - villagedoor.getLastUpdateTick()) > 1200) {
                this.combinedDoorPositions = this.combinedDoorPositions.subtract(villagedoor.getPos());
                flag = true;
                villagedoor.setOutSideVillage(true);
                iterator.remove();
            }
        }

        if (flag) {
            this.updateCenterAndRadius();
        }
    }

    private boolean isWoodenDoor(BlockPos pos) {
        Block block = this.world.getBlockState(pos).getBlock();
        return block instanceof DoorBlock && block.getMaterial() == Material.WOOD;
    }

    private void updateCenterAndRadius() {
        int i = this.doors.size();
        if (i == 0) {
            this.center = new BlockPos(0, 0, 0);
            this.radius = 0;
        } else {
            this.center = new BlockPos(this.combinedDoorPositions.getX() / i, this.combinedDoorPositions.getY() / i, this.combinedDoorPositions.getZ() / i);
            int j = 0;

            for (VillageDoor villagedoor : this.doors) {
                j = Math.max(villagedoor.squaredDistanceTo(this.center), j);
            }

            this.radius = Math.max(32, (int)Math.sqrt(j) + 1);
        }
    }

    public int getReputation(String playerName) {
        Integer integer = this.playerReputations.get(playerName);
        return integer != null ? integer : 0;
    }

    public int updateReputation(String playerName, int reputation) {
        int i = this.getReputation(playerName);
        int j = MathHelper.clamp(i + reputation, -30, 10);
        this.playerReputations.put(playerName, j);
        return j;
    }

    public boolean hasBadReputation(String playerName) {
        return this.getReputation(playerName) <= -15;
    }

    public void readNbt(NbtCompound nbt) {
        this.populationSize = nbt.getInt("PopSize");
        this.radius = nbt.getInt("Radius");
        this.golems = nbt.getInt("Golems");
        this.lastUpdateTick = nbt.getInt("Stable");
        this.ticks = nbt.getInt("Tick");
        this.lastMatingTicks = nbt.getInt("MTick");
        this.center = new BlockPos(nbt.getInt("CX"), nbt.getInt("CY"), nbt.getInt("CZ"));
        this.combinedDoorPositions = new BlockPos(nbt.getInt("ACX"), nbt.getInt("ACY"), nbt.getInt("ACZ"));
        NbtList nbtlist = nbt.getList("Doors", 10);

        for (int i = 0; i < nbtlist.size(); i++) {
            NbtCompound nbtcompound = nbtlist.getCompound(i);
            VillageDoor villagedoor = new VillageDoor(
                new BlockPos(nbtcompound.getInt("X"), nbtcompound.getInt("Y"), nbtcompound.getInt("Z")),
                nbtcompound.getInt("IDX"),
                nbtcompound.getInt("IDZ"),
                nbtcompound.getInt("TS")
            );
            this.doors.add(villagedoor);
        }

        NbtList nbtlist1 = nbt.getList("Players", 10);

        for (int j = 0; j < nbtlist1.size(); j++) {
            NbtCompound nbtcompound1 = nbtlist1.getCompound(j);
            if (nbtcompound1.contains("UUID")) {
                GameProfileCache gameprofilecache = MinecraftServer.getInstance().getGameProfileCache();
                GameProfile gameprofile = gameprofilecache.get(UUID.fromString(nbtcompound1.getString("UUID")));
                if (gameprofile != null) {
                    this.playerReputations.put(gameprofile.getName(), nbtcompound1.getInt("S"));
                }
            } else {
                this.playerReputations.put(nbtcompound1.getString("Name"), nbtcompound1.getInt("S"));
            }
        }
    }

    public void writeNbt(NbtCompound nbt) {
        nbt.putInt("PopSize", this.populationSize);
        nbt.putInt("Radius", this.radius);
        nbt.putInt("Golems", this.golems);
        nbt.putInt("Stable", this.lastUpdateTick);
        nbt.putInt("Tick", this.ticks);
        nbt.putInt("MTick", this.lastMatingTicks);
        nbt.putInt("CX", this.center.getX());
        nbt.putInt("CY", this.center.getY());
        nbt.putInt("CZ", this.center.getZ());
        nbt.putInt("ACX", this.combinedDoorPositions.getX());
        nbt.putInt("ACY", this.combinedDoorPositions.getY());
        nbt.putInt("ACZ", this.combinedDoorPositions.getZ());
        NbtList nbtlist = new NbtList();

        for (VillageDoor villagedoor : this.doors) {
            NbtCompound nbtcompound = new NbtCompound();
            nbtcompound.putInt("X", villagedoor.getPos().getX());
            nbtcompound.putInt("Y", villagedoor.getPos().getY());
            nbtcompound.putInt("Z", villagedoor.getPos().getZ());
            nbtcompound.putInt("IDX", villagedoor.getIndoorsOffsetX());
            nbtcompound.putInt("IDZ", villagedoor.getIndoorsOffsetZ());
            nbtcompound.putInt("TS", villagedoor.getLastUpdateTick());
            nbtlist.addElement(nbtcompound);
        }

        nbt.put("Doors", nbtlist);
        NbtList nbtlist1 = new NbtList();

        for (String s : this.playerReputations.keySet()) {
            NbtCompound nbtcompound1 = new NbtCompound();
            GameProfileCache gameprofilecache = MinecraftServer.getInstance().getGameProfileCache();
            GameProfile gameprofile = gameprofilecache.get(s);
            if (gameprofile != null) {
                nbtcompound1.putString("UUID", gameprofile.getId().toString());
                nbtcompound1.putInt("S", this.playerReputations.get(s));
                nbtlist1.addElement(nbtcompound1);
            }
        }

        nbt.put("Players", nbtlist1);
    }

    public void stopMating() {
        this.lastMatingTicks = this.ticks;
    }

    public boolean canMate() {
        return this.lastMatingTicks == 0 || this.ticks - this.lastMatingTicks >= 3600;
    }

    public void updateAllReputations(int reputation) {
        for (String s : this.playerReputations.keySet()) {
            this.updateReputation(s, reputation);
        }
    }

    class Attacker {
        public LivingEntity attacker;
        public int lastUpdateTick;

        Attacker(LivingEntity attacker, int ticks) {
            this.attacker = attacker;
            this.lastUpdateTick = ticks;
        }
    }
}
