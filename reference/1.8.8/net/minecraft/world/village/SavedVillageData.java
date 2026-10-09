package net.minecraft.world.village;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.material.Material;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.saveddata.SavedData;

public class SavedVillageData extends SavedData {
    private World world;
    private final List<BlockPos> pendingUpdates = Lists.newArrayList();
    private final List<VillageDoor> pendingDoors = Lists.newArrayList();
    private final List<Village> villages = Lists.newArrayList();
    private int ticks;

    public SavedVillageData(String string) {
        super(string);
    }

    public SavedVillageData(World world) {
        super(getId(world.dimension));
        this.world = world;
        this.markDirty();
    }

    public void setWorld(World world) {
        this.world = world;

        for (Village village : this.villages) {
            village.setWorld(world);
        }
    }

    public void markForUpdate(BlockPos pos) {
        if (this.pendingUpdates.size() <= 64) {
            if (!this.isMarkedForUpdate(pos)) {
                this.pendingUpdates.add(pos);
            }
        }
    }

    public void tick() {
        this.ticks++;

        for (Village village : this.villages) {
            village.tick(this.ticks);
        }

        this.removeEmptyVillages();
        this.runPendingUpdate();
        this.addPendingDoors();
        if (this.ticks % 400 == 0) {
            this.markDirty();
        }
    }

    private void removeEmptyVillages() {
        Iterator<Village> iterator = this.villages.iterator();

        while (iterator.hasNext()) {
            Village village = iterator.next();
            if (village.isEmpty()) {
                iterator.remove();
                this.markDirty();
            }
        }
    }

    public List<Village> getVillages() {
        return this.villages;
    }

    public Village getNearestVillage(BlockPos pos, int range) {
        Village village = null;
        double d0 = Float.MAX_VALUE;

        for (Village village1 : this.villages) {
            double d1 = village1.getCenter().squaredDistanceTo(pos);
            if (!(d1 >= d0)) {
                float f = range + village1.getRadius();
                if (!(d1 > f * f)) {
                    village = village1;
                    d0 = d1;
                }
            }
        }

        return village;
    }

    private void runPendingUpdate() {
        if (!this.pendingUpdates.isEmpty()) {
            this.addOrUpdateDoorsAround(this.pendingUpdates.remove(0));
        }
    }

    private void addPendingDoors() {
        for (int i = 0; i < this.pendingDoors.size(); i++) {
            VillageDoor villagedoor = this.pendingDoors.get(i);
            Village village = this.getNearestVillage(villagedoor.getPos(), 32);
            if (village == null) {
                village = new Village(this.world);
                this.villages.add(village);
                this.markDirty();
            }

            village.addDoor(villagedoor);
        }

        this.pendingDoors.clear();
    }

    private void addOrUpdateDoorsAround(BlockPos pos) {
        int i = 16;
        int j = 4;
        int k = 16;

        for (int l = -i; l < i; l++) {
            for (int i1 = -j; i1 < j; i1++) {
                for (int j1 = -k; j1 < k; j1++) {
                    BlockPos blockpos = pos.add(l, i1, j1);
                    if (this.isWoodenDoor(blockpos)) {
                        VillageDoor villagedoor = this.getDoor(blockpos);
                        if (villagedoor == null) {
                            this.addDoor(blockpos);
                        } else {
                            villagedoor.setLastUpdateTick(this.ticks);
                        }
                    }
                }
            }
        }
    }

    private VillageDoor getDoor(BlockPos pos) {
        for (VillageDoor villagedoor : this.pendingDoors) {
            if (villagedoor.getPos().getX() == pos.getX()
                && villagedoor.getPos().getZ() == pos.getZ()
                && Math.abs(villagedoor.getPos().getY() - pos.getY()) <= 1) {
                return villagedoor;
            }
        }

        for (Village village : this.villages) {
            VillageDoor villagedoor1 = village.getDoor(pos);
            if (villagedoor1 != null) {
                return villagedoor1;
            }
        }

        return null;
    }

    private void addDoor(BlockPos pos) {
        Direction direction = DoorBlock.getFacing(this.world, pos);
        Direction direction1 = direction.getOpposite();
        int i = this.getDistanceToSkyAccess(pos, direction, 5);
        int j = this.getDistanceToSkyAccess(pos, direction1, i + 1);
        if (i != j) {
            this.pendingDoors.add(new VillageDoor(pos, i < j ? direction : direction1, this.ticks));
        }
    }

    private int getDistanceToSkyAccess(BlockPos pos, Direction dir, int min) {
        int i = 0;

        for (int j = 1; j <= 5; j++) {
            if (this.world.hasSkyAccess(pos.offset(dir, j))) {
                if (++i >= min) {
                    return i;
                }
            }
        }

        return i;
    }

    private boolean isMarkedForUpdate(BlockPos pos) {
        for (BlockPos blockpos : this.pendingUpdates) {
            if (blockpos.equals(pos)) {
                return true;
            }
        }

        return false;
    }

    private boolean isWoodenDoor(BlockPos pos) {
        Block block = this.world.getBlockState(pos).getBlock();
        return block instanceof DoorBlock && block.getMaterial() == Material.WOOD;
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        this.ticks = nbt.getInt("Tick");
        NbtList nbtlist = nbt.getList("Villages", 10);

        for (int i = 0; i < nbtlist.size(); i++) {
            NbtCompound nbtcompound = nbtlist.getCompound(i);
            Village village = new Village();
            village.readNbt(nbtcompound);
            this.villages.add(village);
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        nbt.putInt("Tick", this.ticks);
        NbtList nbtlist = new NbtList();

        for (Village village : this.villages) {
            NbtCompound nbtcompound = new NbtCompound();
            village.writeNbt(nbtcompound);
            nbtlist.addElement(nbtcompound);
        }

        nbt.put("Villages", nbtlist);
    }

    public static String getId(Dimension dimension) {
        return "villages" + dimension.getDataSuffix();
    }
}
