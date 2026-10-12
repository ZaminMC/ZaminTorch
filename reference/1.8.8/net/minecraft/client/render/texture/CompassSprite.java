package net.minecraft.client.render.texture;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class CompassSprite extends TextureAtlasSprite {
    public double angle;
    public double angleDelta;
    public static String name;

    public CompassSprite(String string) {
        super(string);
        name = string;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.world != null && minecraft.player != null) {
            this.tick(minecraft.world, minecraft.player.x, minecraft.player.z, minecraft.player.yaw, false, false);
        } else {
            this.tick(null, 0.0, 0.0, 0.0, true, false);
        }
    }

    public void tick(World world, double x, double y, double z, boolean findSpawnPoint, boolean skipAngleInterpolation) {
        if (!this.frames.isEmpty()) {
            double d0 = 0.0;
            if (world != null && !findSpawnPoint) {
                BlockPos blockpos = world.getSpawnPoint();
                double d1 = blockpos.getX() - x;
                double d2 = blockpos.getZ() - y;
                z %= 360.0;
                d0 = -((z - 90.0) * Math.PI / 180.0 - Math.atan2(d2, d1));
                if (!world.dimension.isNatural()) {
                    d0 = Math.random() * (float) Math.PI * 2.0;
                }
            }

            if (skipAngleInterpolation) {
                this.angle = d0;
            } else {
                double d3 = d0 - this.angle;

                while (d3 < -Math.PI) {
                    d3 += Math.PI * 2;
                }

                while (d3 >= Math.PI) {
                    d3 -= Math.PI * 2;
                }

                d3 = MathHelper.clamp(d3, -1.0, 1.0);
                this.angleDelta += d3 * 0.1;
                this.angleDelta *= 0.8;
                this.angle = this.angle + this.angleDelta;
            }

            int i = (int)((this.angle / (Math.PI * 2) + 1.0) * this.frames.size()) % this.frames.size();

            while (i < 0) {
                i = (i + this.frames.size()) % this.frames.size();
            }

            if (i != this.activeFrame) {
                this.activeFrame = i;
                TextureUtil.upload(this.frames.get(this.activeFrame), this.width, this.height, this.x, this.y, false, false);
            }
        }
    }
}
