package net.minecraft.client.render.block.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.block.entity.HumanoidSkullModel;
import net.minecraft.client.render.model.block.entity.SkullModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.skin.DefaultSkinUtils;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.Direction;

public class SkullRenderer extends BlockEntityRenderer<SkullBlockEntity> {
    private static final Identifier SKELETON_LOCATION = new Identifier("textures/entity/skeleton/skeleton.png");
    private static final Identifier WITHER_SKELETON_LOCATION = new Identifier("textures/entity/skeleton/wither_skeleton.png");
    private static final Identifier ZOMBIE_LOCATION = new Identifier("textures/entity/zombie/zombie.png");
    private static final Identifier CREEPER_LOCATION = new Identifier("textures/entity/creeper/creeper.png");
    public static SkullRenderer INSTANCE;
    private final SkullModel model = new SkullModel(0, 0, 64, 32);
    private final SkullModel humanoidModel = new HumanoidSkullModel();

    public void render(SkullBlockEntity skullBlockEntity, double d, double e, double f, float g, int i) {
        Direction direction = Direction.byId(skullBlockEntity.getBlockMetadata() & 7);
        this.render(
            (float)d, (float)e, (float)f, direction, skullBlockEntity.getRotation() * 360 / 16.0F, skullBlockEntity.getType(), skullBlockEntity.getProfile(), i
        );
    }

    @Override
    public void init(BlockEntityRenderDispatcher dispatcher) {
        super.init(dispatcher);
        INSTANCE = this;
    }

    public void render(float x, float y, float z, Direction facing, float rotation, int skullType, GameProfile profile, int blockMiningProgress) {
        Model model = this.model;
        if (blockMiningProgress >= 0) {
            this.bindTexture(MINING_PROGRESS_LOCATIONS[blockMiningProgress]);
            GlStateManager.matrixMode(5890);
            GlStateManager.pushMatrix();
            GlStateManager.scalef(4.0F, 2.0F, 1.0F);
            GlStateManager.translatef(0.0625F, 0.0625F, 0.0625F);
            GlStateManager.matrixMode(5888);
        } else {
            switch (skullType) {
                case 0:
                default:
                    this.bindTexture(SKELETON_LOCATION);
                    break;
                case 1:
                    this.bindTexture(WITHER_SKELETON_LOCATION);
                    break;
                case 2:
                    this.bindTexture(ZOMBIE_LOCATION);
                    model = this.humanoidModel;
                    break;
                case 3:
                    model = this.humanoidModel;
                    Identifier identifier = DefaultSkinUtils.getDefaultSkin();
                    if (profile != null) {
                        Minecraft minecraft = Minecraft.getInstance();
                        Map<Type, MinecraftProfileTexture> map = minecraft.getSkinManager().getTextures(profile);
                        if (map.containsKey(Type.SKIN)) {
                            identifier = minecraft.getSkinManager().register(map.get(Type.SKIN), Type.SKIN);
                        } else {
                            UUID uuid = PlayerEntity.getUuid(profile);
                            identifier = DefaultSkinUtils.getDefaultSkin(uuid);
                        }
                    }

                    this.bindTexture(identifier);
                    break;
                case 4:
                    this.bindTexture(CREEPER_LOCATION);
            }
        }

        GlStateManager.pushMatrix();
        GlStateManager.disableCull();
        if (facing != Direction.UP) {
            switch (facing) {
                case NORTH:
                    GlStateManager.translatef(x + 0.5F, y + 0.25F, z + 0.74F);
                    break;
                case SOUTH:
                    GlStateManager.translatef(x + 0.5F, y + 0.25F, z + 0.26F);
                    rotation = 180.0F;
                    break;
                case WEST:
                    GlStateManager.translatef(x + 0.74F, y + 0.25F, z + 0.5F);
                    rotation = 270.0F;
                    break;
                case EAST:
                default:
                    GlStateManager.translatef(x + 0.26F, y + 0.25F, z + 0.5F);
                    rotation = 90.0F;
            }
        } else {
            GlStateManager.translatef(x + 0.5F, y, z + 0.5F);
        }

        float f = 0.0625F;
        GlStateManager.enableRescaleNormal();
        GlStateManager.scalef(-1.0F, -1.0F, 1.0F);
        GlStateManager.enableAlphaTest();
        model.render(null, 0.0F, 0.0F, 0.0F, rotation, 0.0F, f);
        GlStateManager.popMatrix();
        if (blockMiningProgress >= 0) {
            GlStateManager.matrixMode(5890);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5888);
        }
    }
}
