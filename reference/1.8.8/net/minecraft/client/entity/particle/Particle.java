package net.minecraft.client.entity.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class Particle extends Entity {
    protected int spriteRow;
    protected int spriteColumn;
    protected float offsetU;
    protected float offsetV;
    protected int age;
    protected int lifetime;
    protected float size;
    protected float gravity;
    protected float red;
    protected float green;
    protected float blue;
    protected float alpha = 1.0F;
    protected TextureAtlasSprite sprite;
    public static double lerpCameraX;
    public static double lerpCameraY;
    public static double lerpCameraZ;

    protected Particle(World world, double x, double y, double z) {
        super(world);
        this.setSize(0.2F, 0.2F);
        this.setPosition(x, y, z);
        this.prevX = this.lastX = x;
        this.prevY = this.lastY = y;
        this.prevZ = this.lastZ = z;
        this.red = this.green = this.blue = 1.0F;
        this.offsetU = this.random.nextFloat() * 3.0F;
        this.offsetV = this.random.nextFloat() * 3.0F;
        this.size = (this.random.nextFloat() * 0.5F + 0.5F) * 2.0F;
        this.lifetime = (int)(4.0F / (this.random.nextFloat() * 0.9F + 0.1F));
        this.age = 0;
    }

    public Particle(World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        this(world, x, y, z);
        this.velocityX = velocityX + (Math.random() * 2.0 - 1.0) * 0.4F;
        this.velocityY = velocityY + (Math.random() * 2.0 - 1.0) * 0.4F;
        this.velocityZ = velocityZ + (Math.random() * 2.0 - 1.0) * 0.4F;
        float f = (float)(Math.random() + Math.random() + 1.0) * 0.15F;
        float f1 = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityY * this.velocityY + this.velocityZ * this.velocityZ);
        this.velocityX = this.velocityX / f1 * f * 0.4F;
        this.velocityY = this.velocityY / f1 * f * 0.4F + 0.1F;
        this.velocityZ = this.velocityZ / f1 * f * 0.4F;
    }

    public Particle multiplyVelocity(float value) {
        this.velocityX *= value;
        this.velocityY = (this.velocityY - 0.1F) * value + 0.1F;
        this.velocityZ *= value;
        return this;
    }

    public Particle multiplySize(float value) {
        this.setSize(0.2F * value, 0.2F * value);
        this.size *= value;
        return this;
    }

    public void setColor(float red, float green, float blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public void setAlpha(float alpha) {
        if (this.alpha == 1.0F && alpha < 1.0F) {
            Minecraft.getInstance().particleManager.setTranslucent(this);
        } else if (this.alpha < 1.0F && alpha == 1.0F) {
            Minecraft.getInstance().particleManager.setOpaque(this);
        }

        this.alpha = alpha;
    }

    public float getRed() {
        return this.red;
    }

    public float getGreen() {
        return this.green;
    }

    public float getBlue() {
        return this.blue;
    }

    public float getAlpha() {
        return this.alpha;
    }

    @Override
    protected boolean makesSteps() {
        return false;
    }

    @Override
    protected void registerSyncedData() {
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }

        this.velocityY = this.velocityY - 0.04 * this.gravity;
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.velocityX *= 0.98F;
        this.velocityY *= 0.98F;
        this.velocityZ *= 0.98F;
        if (this.onGround) {
            this.velocityX *= 0.7F;
            this.velocityZ *= 0.7F;
        }
    }

    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        float f = this.spriteRow / 16.0F;
        float f1 = f + 0.0624375F;
        float f2 = this.spriteColumn / 16.0F;
        float f3 = f2 + 0.0624375F;
        float f4 = 0.1F * this.size;
        if (this.sprite != null) {
            f = this.sprite.getUMin();
            f1 = this.sprite.getUMax();
            f2 = this.sprite.getVMin();
            f3 = this.sprite.getVMax();
        }

        float f5 = (float)(this.lastX + (this.x - this.lastX) * tickDelta - lerpCameraX);
        float f6 = (float)(this.lastY + (this.y - this.lastY) * tickDelta - lerpCameraY);
        float f7 = (float)(this.lastZ + (this.z - this.lastZ) * tickDelta - lerpCameraZ);
        int i = this.getLightLevel(tickDelta);
        int j = i >> 16 & 65535;
        int k = i & 65535;
        bufferBuilder.vertex(f5 - dx * f4 - forwards * f4, f6 - dy * f4, f7 - dz * f4 - sideways * f4)
            .texture(f1, f3)
            .color(this.red, this.green, this.blue, this.alpha)
            .texture(j, k)
            .nextVertex();
        bufferBuilder.vertex(f5 - dx * f4 + forwards * f4, f6 + dy * f4, f7 - dz * f4 + sideways * f4)
            .texture(f1, f2)
            .color(this.red, this.green, this.blue, this.alpha)
            .texture(j, k)
            .nextVertex();
        bufferBuilder.vertex(f5 + dx * f4 + forwards * f4, f6 + dy * f4, f7 + dz * f4 + sideways * f4)
            .texture(f, f2)
            .color(this.red, this.green, this.blue, this.alpha)
            .texture(j, k)
            .nextVertex();
        bufferBuilder.vertex(f5 + dx * f4 - forwards * f4, f6 - dy * f4, f7 + dz * f4 - sideways * f4)
            .texture(f, f3)
            .color(this.red, this.green, this.blue, this.alpha)
            .texture(j, k)
            .nextVertex();
    }

    public int getAtlasType() {
        return 0;
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
    }

    public void setTexture(TextureAtlasSprite sprite) {
        int i = this.getAtlasType();
        if (i == 1) {
            this.sprite = sprite;
        } else {
            throw new RuntimeException("Invalid call to Particle.setTex, use coordinate methods");
        }
    }

    public void setTextureCoordinates(int sprite) {
        if (this.getAtlasType() != 0) {
            throw new RuntimeException("Invalid call to Particle.setMiscTex");
        }

        this.spriteRow = sprite % 16;
        this.spriteColumn = sprite / 16;
    }

    public void incrementSpriteRow() {
        this.spriteRow++;
    }

    @Override
    public boolean canBePunched() {
        return false;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName()
            + ", Pos ("
            + this.x
            + ","
            + this.y
            + ","
            + this.z
            + "), RGBA ("
            + this.red
            + ","
            + this.green
            + ","
            + this.blue
            + ","
            + this.alpha
            + "), Age "
            + this.age;
    }
}
