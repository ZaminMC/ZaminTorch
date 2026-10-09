package net.minecraft.client.render.model.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.monster.GuardianEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class GuardianModel extends Model {
    private ModelPart head;
    private ModelPart eye;
    private ModelPart[] spikes;
    private ModelPart[] tail;

    public GuardianModel() {
        this.textureWidth = 64;
        this.textureHeight = 64;
        this.spikes = new ModelPart[12];
        this.head = new ModelPart(this);
        this.head.setTextureCoords(0, 0).addBox(-6.0F, 10.0F, -8.0F, 12, 12, 16);
        this.head.setTextureCoords(0, 28).addBox(-8.0F, 10.0F, -6.0F, 2, 12, 12);
        this.head.setTextureCoords(0, 28).addBox(6.0F, 10.0F, -6.0F, 2, 12, 12, true);
        this.head.setTextureCoords(16, 40).addBox(-6.0F, 8.0F, -6.0F, 12, 2, 12);
        this.head.setTextureCoords(16, 40).addBox(-6.0F, 22.0F, -6.0F, 12, 2, 12);

        for (int i = 0; i < this.spikes.length; i++) {
            this.spikes[i] = new ModelPart(this, 0, 0);
            this.spikes[i].addBox(-1.0F, -4.5F, -1.0F, 2, 9, 2);
            this.head.addChild(this.spikes[i]);
        }

        this.eye = new ModelPart(this, 8, 0);
        this.eye.addBox(-1.0F, 15.0F, 0.0F, 2, 2, 1);
        this.head.addChild(this.eye);
        this.tail = new ModelPart[3];
        this.tail[0] = new ModelPart(this, 40, 0);
        this.tail[0].addBox(-2.0F, 14.0F, 7.0F, 4, 4, 8);
        this.tail[1] = new ModelPart(this, 0, 54);
        this.tail[1].addBox(0.0F, 14.0F, 0.0F, 3, 3, 7);
        this.tail[2] = new ModelPart(this);
        this.tail[2].setTextureCoords(41, 32).addBox(0.0F, 14.0F, 0.0F, 2, 2, 6);
        this.tail[2].setTextureCoords(25, 19).addBox(1.0F, 10.5F, 3.0F, 1, 9, 9);
        this.head.addChild(this.tail[0]);
        this.tail[0].addChild(this.tail[1]);
        this.tail[1].addChild(this.tail[2]);
    }

    public int getVersion() {
        return 54;
    }

    @Override
    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        this.setupAnimation(walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale, entity);
        this.head.render(scale);
    }

    @Override
    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
        GuardianEntity guardianentity = (GuardianEntity)entity;
        float f = bob - guardianentity.ticks;
        this.head.rotationY = yaw / (180.0F / (float)Math.PI);
        this.head.rotationX = pitch / (180.0F / (float)Math.PI);
        float[] afloat = new float[]{1.75F, 0.25F, 0.0F, 0.0F, 0.5F, 0.5F, 0.5F, 0.5F, 1.25F, 0.75F, 0.0F, 0.0F};
        float[] afloat1 = new float[]{0.0F, 0.0F, 0.0F, 0.0F, 0.25F, 1.75F, 1.25F, 0.75F, 0.0F, 0.0F, 0.0F, 0.0F};
        float[] afloat2 = new float[]{0.0F, 0.0F, 0.25F, 1.75F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.75F, 1.25F};
        float[] afloat3 = new float[]{0.0F, 0.0F, 8.0F, -8.0F, -8.0F, 8.0F, 8.0F, -8.0F, 0.0F, 0.0F, 8.0F, -8.0F};
        float[] afloat4 = new float[]{-8.0F, -8.0F, -8.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 8.0F, 8.0F};
        float[] afloat5 = new float[]{8.0F, -8.0F, 0.0F, 0.0F, -8.0F, -8.0F, 8.0F, 8.0F, 8.0F, -8.0F, 0.0F, 0.0F};
        float f1 = (1.0F - guardianentity.getSpikesExtension(f)) * 0.55F;

        for (int i = 0; i < 12; i++) {
            this.spikes[i].rotationX = (float) Math.PI * afloat[i];
            this.spikes[i].rotationY = (float) Math.PI * afloat1[i];
            this.spikes[i].rotationZ = (float) Math.PI * afloat2[i];
            this.spikes[i].x = afloat3[i] * (1.0F + MathHelper.cos(bob * 1.5F + i) * 0.01F - f1);
            this.spikes[i].y = 16.0F + afloat4[i] * (1.0F + MathHelper.cos(bob * 1.5F + i) * 0.01F - f1);
            this.spikes[i].z = afloat5[i] * (1.0F + MathHelper.cos(bob * 1.5F + i) * 0.01F - f1);
        }

        this.eye.z = -8.25F;
        Entity entityx = Minecraft.getInstance().getCamera();
        if (guardianentity.hasBeamTarget()) {
            entityx = guardianentity.getLaserTarget();
        }

        if (entityx != null) {
            Vec3d vec3d = entityx.getEyePosition(0.0F);
            Vec3d vec3d1 = entity.getEyePosition(0.0F);
            double d0 = vec3d.y - vec3d1.y;
            if (d0 > 0.0) {
                this.eye.y = 0.0F;
            } else {
                this.eye.y = 1.0F;
            }

            Vec3d vec3d2 = entity.getRotationVec(0.0F);
            vec3d2 = new Vec3d(vec3d2.x, 0.0, vec3d2.z);
            Vec3d vec3d3 = new Vec3d(vec3d1.x - vec3d.x, 0.0, vec3d1.z - vec3d.z).normalize().rotateY((float) (Math.PI / 2));
            double d1 = vec3d2.dot(vec3d3);
            this.eye.x = MathHelper.sqrt((float)Math.abs(d1)) * 2.0F * (float)Math.signum(d1);
        }

        this.eye.visible = true;
        float f2 = guardianentity.getTailAngle(f);
        this.tail[0].rotationY = MathHelper.sin(f2) * (float) Math.PI * 0.05F;
        this.tail[1].rotationY = MathHelper.sin(f2) * (float) Math.PI * 0.1F;
        this.tail[1].x = -1.5F;
        this.tail[1].y = 0.5F;
        this.tail[1].z = 14.0F;
        this.tail[2].rotationY = MathHelper.sin(f2) * (float) Math.PI * 0.15F;
        this.tail[2].x = 0.5F;
        this.tail[2].y = 0.5F;
        this.tail[2].z = 6.0F;
    }
}
