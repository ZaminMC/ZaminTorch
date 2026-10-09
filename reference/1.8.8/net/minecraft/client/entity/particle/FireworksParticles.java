package net.minecraft.client.entity.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.item.DyeItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class FireworksParticles {
    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            FireworksParticles.Spark fireworksparticles$spark = new FireworksParticles.Spark(
                world, x, y, z, velocityX, velocityY, velocityZ, Minecraft.getInstance().particleManager
            );
            fireworksparticles$spark.setAlpha(0.99F);
            return fireworksparticles$spark;
        }
    }

    public static class Overlay extends Particle {
        protected Overlay(World world, double d, double e, double f) {
            super(world, d, e, f);
            this.lifetime = 4;
        }

        @Override
        public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
            float f = 0.25F;
            float f1 = 0.5F;
            float f2 = 0.125F;
            float f3 = 0.375F;
            float f4 = 7.1F * MathHelper.sin((this.age + tickDelta - 1.0F) * 0.25F * (float) Math.PI);
            this.alpha = 0.6F - (this.age + tickDelta - 1.0F) * 0.25F * 0.5F;
            float f5 = (float)(this.lastX + (this.x - this.lastX) * tickDelta - lerpCameraX);
            float f6 = (float)(this.lastY + (this.y - this.lastY) * tickDelta - lerpCameraY);
            float f7 = (float)(this.lastZ + (this.z - this.lastZ) * tickDelta - lerpCameraZ);
            int i = this.getLightLevel(tickDelta);
            int j = i >> 16 & 65535;
            int k = i & 65535;
            bufferBuilder.vertex(f5 - dx * f4 - forwards * f4, f6 - dy * f4, f7 - dz * f4 - sideways * f4)
                .texture(0.5, 0.375)
                .color(this.red, this.green, this.blue, this.alpha)
                .texture(j, k)
                .nextVertex();
            bufferBuilder.vertex(f5 - dx * f4 + forwards * f4, f6 + dy * f4, f7 - dz * f4 + sideways * f4)
                .texture(0.5, 0.125)
                .color(this.red, this.green, this.blue, this.alpha)
                .texture(j, k)
                .nextVertex();
            bufferBuilder.vertex(f5 + dx * f4 + forwards * f4, f6 + dy * f4, f7 + dz * f4 + sideways * f4)
                .texture(0.25, 0.125)
                .color(this.red, this.green, this.blue, this.alpha)
                .texture(j, k)
                .nextVertex();
            bufferBuilder.vertex(f5 + dx * f4 - forwards * f4, f6 - dy * f4, f7 + dz * f4 - sideways * f4)
                .texture(0.25, 0.375)
                .color(this.red, this.green, this.blue, this.alpha)
                .texture(j, k)
                .nextVertex();
        }
    }

    public static class Spark extends Particle {
        private int texture = 160;
        private boolean trail;
        private boolean flicker;
        private final ParticleManager manager;
        private float fadeRed;
        private float fadeGreen;
        private float fadeBlue;
        private boolean fade;

        public Spark(World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, ParticleManager manager) {
            super(world, x, y, z);
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.velocityZ = velocityZ;
            this.manager = manager;
            this.size *= 0.75F;
            this.lifetime = 48 + this.random.nextInt(12);
            this.noClip = false;
        }

        public void setTrail(boolean trail) {
            this.trail = trail;
        }

        public void setFlicker(boolean flicker) {
            this.flicker = flicker;
        }

        public void setColor(int color) {
            float f = ((color & 0xFF0000) >> 16) / 255.0F;
            float f1 = ((color & 0xFF00) >> 8) / 255.0F;
            float f2 = ((color & 0xFF) >> 0) / 255.0F;
            float f3 = 1.0F;
            this.setColor(f * f3, f1 * f3, f2 * f3);
        }

        public void setFade(int color) {
            this.fadeRed = ((color & 0xFF0000) >> 16) / 255.0F;
            this.fadeGreen = ((color & 0xFF00) >> 8) / 255.0F;
            this.fadeBlue = ((color & 0xFF) >> 0) / 255.0F;
            this.fade = true;
        }

        @Override
        public Box getCollisionShape() {
            return null;
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
            if (!this.flicker || this.age < this.lifetime / 3 || (this.age + this.lifetime) / 3 % 2 == 0) {
                super.render(bufferBuilder, camera, tickDelta, dx, dy, dz, forwards, sideways);
            }
        }

        @Override
        public void tick() {
            this.lastX = this.x;
            this.lastY = this.y;
            this.lastZ = this.z;
            if (this.age++ >= this.lifetime) {
                this.remove();
            }

            if (this.age > this.lifetime / 2) {
                this.setAlpha(1.0F - ((float)this.age - this.lifetime / 2) / this.lifetime);
                if (this.fade) {
                    this.red = this.red + (this.fadeRed - this.red) * 0.2F;
                    this.green = this.green + (this.fadeGreen - this.green) * 0.2F;
                    this.blue = this.blue + (this.fadeBlue - this.blue) * 0.2F;
                }
            }

            this.setTextureCoordinates(this.texture + (7 - this.age * 8 / this.lifetime));
            this.velocityY -= 0.004;
            this.move(this.velocityX, this.velocityY, this.velocityZ);
            this.velocityX *= 0.91F;
            this.velocityY *= 0.91F;
            this.velocityZ *= 0.91F;
            if (this.onGround) {
                this.velocityX *= 0.7F;
                this.velocityZ *= 0.7F;
            }

            if (this.trail && this.age < this.lifetime / 2 && (this.age + this.lifetime) % 2 == 0) {
                FireworksParticles.Spark fireworksparticles$spark = new FireworksParticles.Spark(
                    this.world, this.x, this.y, this.z, 0.0, 0.0, 0.0, this.manager
                );
                fireworksparticles$spark.setAlpha(0.99F);
                fireworksparticles$spark.setColor(this.red, this.green, this.blue);
                fireworksparticles$spark.age = fireworksparticles$spark.lifetime / 2;
                if (this.fade) {
                    fireworksparticles$spark.fade = true;
                    fireworksparticles$spark.fadeRed = this.fadeRed;
                    fireworksparticles$spark.fadeGreen = this.fadeGreen;
                    fireworksparticles$spark.fadeBlue = this.fadeBlue;
                }

                fireworksparticles$spark.flicker = this.flicker;
                this.manager.add(fireworksparticles$spark);
            }
        }

        @Override
        public int getLightLevel(float tickDelta) {
            return 15728880;
        }

        @Override
        public float getBrightness(float tickDelta) {
            return 1.0F;
        }
    }

    public static class Starter extends Particle {
        private int life;
        private final ParticleManager manager;
        private NbtList explosions;
        boolean twinkleDelay;

        public Starter(
            World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, ParticleManager manager, NbtCompound explosions
        ) {
            super(world, x, y, z, 0.0, 0.0, 0.0);
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.velocityZ = velocityZ;
            this.manager = manager;
            this.lifetime = 8;
            if (explosions != null) {
                this.explosions = explosions.getList("Explosions", 10);
                if (this.explosions.size() == 0) {
                    this.explosions = null;
                } else {
                    this.lifetime = this.explosions.size() * 2 - 1;

                    for (int i = 0; i < this.explosions.size(); i++) {
                        NbtCompound nbtcompound = this.explosions.getCompound(i);
                        if (nbtcompound.getBoolean("Flicker")) {
                            this.twinkleDelay = true;
                            this.lifetime += 15;
                            break;
                        }
                    }
                }
            }
        }

        @Override
        public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        }

        @Override
        public void tick() {
            if (this.life == 0 && this.explosions != null) {
                boolean flag = this.isFarAway();
                boolean flag1 = false;
                if (this.explosions.size() >= 3) {
                    flag1 = true;
                } else {
                    for (int i = 0; i < this.explosions.size(); i++) {
                        NbtCompound nbtcompound = this.explosions.getCompound(i);
                        if (nbtcompound.getByte("Type") == 1) {
                            flag1 = true;
                            break;
                        }
                    }
                }

                String s1 = "fireworks." + (flag1 ? "largeBlast" : "blast") + (flag ? "_far" : "");
                this.world.playSound(this.x, this.y, this.z, s1, 20.0F, 0.95F + this.random.nextFloat() * 0.1F, true);
            }

            if (this.life % 2 == 0 && this.explosions != null && this.life / 2 < this.explosions.size()) {
                int k = this.life / 2;
                NbtCompound nbtcompound1 = this.explosions.getCompound(k);
                int l = nbtcompound1.getByte("Type");
                boolean flag4 = nbtcompound1.getBoolean("Trail");
                boolean flag2 = nbtcompound1.getBoolean("Flicker");
                int[] aint = nbtcompound1.getIntArray("Colors");
                int[] aint1 = nbtcompound1.getIntArray("FadeColors");
                if (aint.length == 0) {
                    aint = new int[]{DyeItem.COLORS[0]};
                }

                if (l == 1) {
                    this.createBall(0.5, 4, aint, aint1, flag4, flag2);
                } else if (l == 2) {
                    this.createShape(
                        0.5,
                        new double[][]{
                            {0.0, 1.0},
                            {0.3455, 0.309},
                            {0.9511, 0.309},
                            {0.3795918367346939, -0.12653061224489795},
                            {0.6122448979591837, -0.8040816326530612},
                            {0.0, -0.35918367346938773}
                        },
                        aint,
                        aint1,
                        flag4,
                        flag2,
                        false
                    );
                } else if (l == 3) {
                    this.createShape(
                        0.5,
                        new double[][]{
                            {0.0, 0.2},
                            {0.2, 0.2},
                            {0.2, 0.6},
                            {0.6, 0.6},
                            {0.6, 0.2},
                            {0.2, 0.2},
                            {0.2, 0.0},
                            {0.4, 0.0},
                            {0.4, -0.6},
                            {0.2, -0.6},
                            {0.2, -0.4},
                            {0.0, -0.4}
                        },
                        aint,
                        aint1,
                        flag4,
                        flag2,
                        true
                    );
                } else if (l == 4) {
                    this.createBurst(aint, aint1, flag4, flag2);
                } else {
                    this.createBall(0.25, 2, aint, aint1, flag4, flag2);
                }

                int j = aint[0];
                float f = ((j & 0xFF0000) >> 16) / 255.0F;
                float f1 = ((j & 0xFF00) >> 8) / 255.0F;
                float f2 = ((j & 0xFF) >> 0) / 255.0F;
                FireworksParticles.Overlay fireworksparticles$overlay = new FireworksParticles.Overlay(this.world, this.x, this.y, this.z);
                fireworksparticles$overlay.setColor(f, f1, f2);
                this.manager.add(fireworksparticles$overlay);
            }

            this.life++;
            if (this.life > this.lifetime) {
                if (this.twinkleDelay) {
                    boolean flag3 = this.isFarAway();
                    String s = "fireworks." + (flag3 ? "twinkle_far" : "twinkle");
                    this.world.playSound(this.x, this.y, this.z, s, 20.0F, 0.9F + this.random.nextFloat() * 0.15F, true);
                }

                this.remove();
            }
        }

        private boolean isFarAway() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft == null || minecraft.getCamera() == null || !(minecraft.getCamera().squaredDistanceTo(this.x, this.y, this.z) < 256.0);
        }

        private void create(
            double x, double y, double z, double velocityX, double velocityY, double velocityZ, int[] colors, int[] fadeColors, boolean trail, boolean flicker
        ) {
            FireworksParticles.Spark fireworksparticles$spark = new FireworksParticles.Spark(this.world, x, y, z, velocityX, velocityY, velocityZ, this.manager);
            fireworksparticles$spark.setAlpha(0.99F);
            fireworksparticles$spark.setTrail(trail);
            fireworksparticles$spark.setFlicker(flicker);
            int i = this.random.nextInt(colors.length);
            fireworksparticles$spark.setColor(colors[i]);
            if (fadeColors != null && fadeColors.length > 0) {
                fireworksparticles$spark.setFade(fadeColors[this.random.nextInt(fadeColors.length)]);
            }

            this.manager.add(fireworksparticles$spark);
        }

        private void createBall(double size, int amount, int[] colors, int[] fadeColors, boolean trail, boolean flicker) {
            double d0 = this.x;
            double d1 = this.y;
            double d2 = this.z;

            for (int i = -amount; i <= amount; i++) {
                for (int j = -amount; j <= amount; j++) {
                    for (int k = -amount; k <= amount; k++) {
                        double d3 = j + (this.random.nextDouble() - this.random.nextDouble()) * 0.5;
                        double d4 = i + (this.random.nextDouble() - this.random.nextDouble()) * 0.5;
                        double d5 = k + (this.random.nextDouble() - this.random.nextDouble()) * 0.5;
                        double d6 = MathHelper.sqrt(d3 * d3 + d4 * d4 + d5 * d5) / size + this.random.nextGaussian() * 0.05;
                        this.create(d0, d1, d2, d3 / d6, d4 / d6, d5 / d6, colors, fadeColors, trail, flicker);
                        if (i != -amount && i != amount && j != -amount && j != amount) {
                            k += amount * 2 - 1;
                        }
                    }
                }
            }
        }

        private void createShape(double size, double[][] pattern, int[] colors, int[] fadeColors, boolean trail, boolean flicker, boolean keepShape) {
            double d0 = pattern[0][0];
            double d1 = pattern[0][1];
            this.create(this.x, this.y, this.z, d0 * size, d1 * size, 0.0, colors, fadeColors, trail, flicker);
            float f = this.random.nextFloat() * (float) Math.PI;
            double d2 = keepShape ? 0.034 : 0.34;

            for (int i = 0; i < 3; i++) {
                double d3 = f + i * (float) Math.PI * d2;
                double d4 = d0;
                double d5 = d1;

                for (int j = 1; j < pattern.length; j++) {
                    double d6 = pattern[j][0];
                    double d7 = pattern[j][1];

                    for (double d8 = 0.25; d8 <= 1.0; d8 += 0.25) {
                        double d9 = (d4 + (d6 - d4) * d8) * size;
                        double d10 = (d5 + (d7 - d5) * d8) * size;
                        double d11 = d9 * Math.sin(d3);
                        d9 *= Math.cos(d3);

                        for (double d12 = -1.0; d12 <= 1.0; d12 += 2.0) {
                            this.create(this.x, this.y, this.z, d9 * d12, d10, d11 * d12, colors, fadeColors, trail, flicker);
                        }
                    }

                    d4 = d6;
                    d5 = d7;
                }
            }
        }

        private void createBurst(int[] colors, int[] fadeColors, boolean trail, boolean flicker) {
            double d0 = this.random.nextGaussian() * 0.05;
            double d1 = this.random.nextGaussian() * 0.05;

            for (int i = 0; i < 70; i++) {
                double d2 = this.velocityX * 0.5 + this.random.nextGaussian() * 0.15 + d0;
                double d3 = this.velocityZ * 0.5 + this.random.nextGaussian() * 0.15 + d1;
                double d4 = this.velocityY * 0.5 + this.random.nextDouble() * 0.5;
                this.create(this.x, this.y, this.z, d2, d4, d3, colors, fadeColors, trail, flicker);
            }
        }

        @Override
        public int getAtlasType() {
            return 0;
        }
    }
}
