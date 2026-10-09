package net.minecraft.client.gui.screen.inventory.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.StatsScreen;
import net.minecraft.client.gui.screen.menu.AchievementsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;

public class SurvivalInventoryScreen extends PlayerInventoryScreen {
    private float mouseX;
    private float mouseY;

    public SurvivalInventoryScreen(PlayerEntity player) {
        super(player.playerMenu);
        this.passEvents = true;
    }

    @Override
    public void tick() {
        if (this.minecraft.interactionManager.hasCreativeInventory()) {
            this.minecraft.openScreen(new CreativeInventoryScreen(this.minecraft.player));
        }

        this.checkStatusEffects();
    }

    @Override
    public void init() {
        this.buttons.clear();
        if (this.minecraft.interactionManager.hasCreativeInventory()) {
            this.minecraft.openScreen(new CreativeInventoryScreen(this.minecraft.player));
        } else {
            super.init();
        }
    }

    @Override
    protected void renderLabels(int mouseX, int mouseY) {
        this.textRenderer.draw(I18n.translate("container.crafting"), 86, 16, 4210752);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        super.render(mouseX, mouseY, tickDelta);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    @Override
    protected void renderMenuBackground(float tickDelta, int mouseX, int mouseY) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(MENU_LOCATION);
        int i = this.x;
        int j = this.y;
        this.drawTexture(i, j, 0, 0, this.backgroundWidth, this.backgroundHeight);
        renderEntity(i + 51, j + 75, 30, i + 51 - this.mouseX, j + 75 - 50 - this.mouseY, this.minecraft.player);
    }

    public static void renderEntity(int x, int y, int size, float mouseX, float mouseY, LivingEntity entity) {
        GlStateManager.enableColorMaterial();
        GlStateManager.pushMatrix();
        GlStateManager.translatef(x, y, 50.0F);
        GlStateManager.scalef(-size, size, size);
        GlStateManager.rotatef(180.0F, 0.0F, 0.0F, 1.0F);
        float f = entity.bodyYaw;
        float f1 = entity.yaw;
        float f2 = entity.pitch;
        float f3 = entity.lastHeadYaw;
        float f4 = entity.headYaw;
        GlStateManager.rotatef(135.0F, 0.0F, 1.0F, 0.0F);
        Lighting.turnOn();
        GlStateManager.rotatef(-135.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(-((float)Math.atan(mouseY / 40.0F)) * 20.0F, 1.0F, 0.0F, 0.0F);
        entity.bodyYaw = (float)Math.atan(mouseX / 40.0F) * 20.0F;
        entity.yaw = (float)Math.atan(mouseX / 40.0F) * 40.0F;
        entity.pitch = -((float)Math.atan(mouseY / 40.0F)) * 20.0F;
        entity.headYaw = entity.yaw;
        entity.lastHeadYaw = entity.yaw;
        GlStateManager.translatef(0.0F, 0.0F, 0.0F);
        EntityRenderDispatcher entityrenderdispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        entityrenderdispatcher.setCameraYaw(180.0F);
        entityrenderdispatcher.setRenderShadow(false);
        entityrenderdispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F);
        entityrenderdispatcher.setRenderShadow(true);
        entity.bodyYaw = f;
        entity.yaw = f1;
        entity.pitch = f2;
        entity.lastHeadYaw = f3;
        entity.headYaw = f4;
        GlStateManager.popMatrix();
        Lighting.turnOff();
        GlStateManager.disableRescaleNormal();
        GlStateManager.activeTexture(GLX.GL_TEXTURE1);
        GlStateManager.disableTexture();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == 0) {
            this.minecraft.openScreen(new AchievementsScreen(this, this.minecraft.player.getStats()));
        }

        if (button.id == 1) {
            this.minecraft.openScreen(new StatsScreen(this, this.minecraft.player.getStats()));
        }
    }
}
