package net.minecraft.entity.decoration;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class PaintingEntity extends DecorationEntity {
    public PaintingEntity.Motive motive;

    public PaintingEntity(World world) {
        super(world);
    }

    public PaintingEntity(World world, BlockPos pos, Direction facing) {
        super(world, pos);
        List<PaintingEntity.Motive> list = Lists.newArrayList();

        for (PaintingEntity.Motive paintingentity$motive : PaintingEntity.Motive.values()) {
            this.motive = paintingentity$motive;
            this.setDirection(facing);
            if (this.canSurvive()) {
                list.add(paintingentity$motive);
            }
        }

        if (!list.isEmpty()) {
            this.motive = list.get(this.random.nextInt(list.size()));
        }

        this.setDirection(facing);
    }

    public PaintingEntity(World world, BlockPos x, Direction y, String z) {
        this(world, x, y);

        for (PaintingEntity.Motive paintingentity$motive : PaintingEntity.Motive.values()) {
            if (paintingentity$motive.name.equals(z)) {
                this.motive = paintingentity$motive;
                break;
            }
        }

        this.setDirection(y);
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        nbt.putString("Motive", this.motive.name);
        super.writeCustomNbt(nbt);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        String s = nbt.getString("Motive");

        for (PaintingEntity.Motive paintingentity$motive : PaintingEntity.Motive.values()) {
            if (paintingentity$motive.name.equals(s)) {
                this.motive = paintingentity$motive;
            }
        }

        if (this.motive == null) {
            this.motive = PaintingEntity.Motive.KEBAB;
        }

        super.readCustomNbt(nbt);
    }

    @Override
    public int getWidth() {
        return this.motive.width;
    }

    @Override
    public int getHeight() {
        return this.motive.height;
    }

    @Override
    public void onAttack(Entity entity) {
        if (this.world.getGameRules().getBoolean("doEntityDrops")) {
            if (entity instanceof PlayerEntity) {
                PlayerEntity playerentity = (PlayerEntity)entity;
                if (playerentity.abilities.creativeMode) {
                    return;
                }
            }

            this.dropItem(new ItemStack(Items.PAINTING), 0.0F);
        }
    }

    @Override
    public void setPositionAndAngles(double x, double y, double z, float yaw, float pitch) {
        BlockPos blockpos = this.pos.add(x - this.x, y - this.y, z - this.z);
        this.setPosition(blockpos.getX(), blockpos.getY(), blockpos.getZ());
    }

    @Override
    public void lerpPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        BlockPos blockpos = this.pos.add(x - this.x, y - this.y, z - this.z);
        this.setPosition(blockpos.getX(), blockpos.getY(), blockpos.getZ());
    }

    public enum Motive {
        KEBAB("Kebab", 16, 16, 0, 0),
        AZTEC("Aztec", 16, 16, 16, 0),
        ALBAN("Alban", 16, 16, 32, 0),
        AZTEC2("Aztec2", 16, 16, 48, 0),
        BOMB("Bomb", 16, 16, 64, 0),
        PLANT("Plant", 16, 16, 80, 0),
        WASTELAND("Wasteland", 16, 16, 96, 0),
        POOL("Pool", 32, 16, 0, 32),
        COURBET("Courbet", 32, 16, 32, 32),
        SEA("Sea", 32, 16, 64, 32),
        SUNSET("Sunset", 32, 16, 96, 32),
        CREEBET("Creebet", 32, 16, 128, 32),
        WANDERER("Wanderer", 16, 32, 0, 64),
        GRAHAM("Graham", 16, 32, 16, 64),
        MATCH("Match", 32, 32, 0, 128),
        BUST("Bust", 32, 32, 32, 128),
        STAGE("Stage", 32, 32, 64, 128),
        VOID("Void", 32, 32, 96, 128),
        SKULL_AND_ROSES("SkullAndRoses", 32, 32, 128, 128),
        WTIHER("Wither", 32, 32, 160, 128),
        FIGHTERS("Fighters", 64, 32, 0, 96),
        POINTER("Pointer", 64, 64, 0, 192),
        PIGSCENE("Pigscene", 64, 64, 64, 192),
        BURNING_SKULL("BurningSkull", 64, 64, 128, 192),
        SKELETON("Skeleton", 64, 48, 192, 64),
        DONKEY_KONG("DonkeyKong", 64, 48, 192, 112);

        public static final int SKULL_AND_ROSES_LENGTH = "SkullAndRoses".length();
        public final String name;
        public final int width;
        public final int height;
        public final int u;
        public final int v;

        Motive(String name, int width, int height, int u, int v) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.u = u;
            this.v = v;
        }
    }
}
