package net.minecraft.client.render.model;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;

public abstract class Model {
    public float attackAnimationProgress;
    public boolean riding;
    public boolean isBaby = true;
    public List<ModelPart> parts = Lists.newArrayList();
    private Map<String, TexturePos> texturePositions = Maps.newHashMap();
    public int textureWidth = 64;
    public int textureHeight = 32;

    public void render(Entity entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
    }

    public void setupAnimation(float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale, Entity entity) {
    }

    public void prepare(LivingEntity mob, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta) {
    }

    public ModelPart pickPart(Random random) {
        return this.parts.get(random.nextInt(this.parts.size()));
    }

    protected void setTexturePos(String id, int u, int v) {
        this.texturePositions.put(id, new TexturePos(u, v));
    }

    public TexturePos getTexturePos(String id) {
        return this.texturePositions.get(id);
    }

    public static void copyRotation(ModelPart from, ModelPart to) {
        to.rotationX = from.rotationX;
        to.rotationY = from.rotationY;
        to.rotationZ = from.rotationZ;
        to.x = from.x;
        to.y = from.y;
        to.z = from.z;
    }

    public void copyPropertiesFrom(Model model) {
        this.attackAnimationProgress = model.attackAnimationProgress;
        this.riding = model.riding;
        this.isBaby = model.isBaby;
    }
}
