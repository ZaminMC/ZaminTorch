package net.minecraft.realms;

import com.mojang.util.UUIDTypeAdapter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.resource.skin.DefaultSkinUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;

public class RealmsScreen {
    public static final int SKIN_HEAD_U = 8;
    public static final int SKIN_HEAD_V = 8;
    public static final int SKIN_HEAD_WIDTH = 8;
    public static final int SKIN_HEAD_HEIGHT = 8;
    public static final int SKIN_HAT_U = 40;
    public static final int SKIN_HAT_V = 8;
    public static final int SKIN_HAT_WIDTH = 8;
    public static final int SKIN_HAT_HEIGHT = 8;
    public static final int SKIN_TEX_WIDTH = 64;
    public static final int SKIN_TEX_HEIGHT = 64;
    protected Minecraft minecraft;
    public int width;
    public int height;
    private RealmsScreenProxy proxy = new RealmsScreenProxy(this);

    public RealmsScreenProxy getProxy() {
        return this.proxy;
    }

    public void init() {
    }

    public void init(Minecraft minecraft, int width, int height) {
    }

    public void drawCenteredString(String text, int x, int y, int color) {
        this.proxy.drawCenteredString(text, x, y, color);
    }

    public void drawString(String string, int i, int j, int k) {
        this.proxy.drawString(string, i, j, k);
    }

    public void blit(int x, int y, int u, int v, int width, int height) {
        this.proxy.drawTexture(x, y, u, v, width, height);
    }

    public static void blit(int x, int y, float u, float v, int textureWidth, int textureHeight, int width, int height, float scaleU, float scaleV) {
        GuiElement.drawTexture(x, y, u, v, textureWidth, textureHeight, width, height, scaleU, scaleV);
    }

    public static void blit(int x, int y, float u, float v, int width, int height, float scaleU, float scaleV) {
        GuiElement.drawTexture(x, y, u, v, width, height, scaleU, scaleV);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int color1, int color2) {
        this.proxy.fillGradient(x1, y1, x2, y2, color1, color2);
    }

    public void renderBackground() {
        this.proxy.renderBackground();
    }

    public boolean isPauseScreen() {
        return this.proxy.shouldPauseGame();
    }

    public void renderBackground(int offset) {
        this.proxy.renderBackground(offset);
    }

    public void render(int mouseX, int mouseY, float tickDelta) {
        for (int i = 0; i < this.proxy.getButtons().size(); i++) {
            this.proxy.getButtons().get(i).render(mouseX, mouseY);
        }
    }

    public void renderTooltip(ItemStack itemStack, int mouseX, int mouseY) {
        this.proxy.renderTooltip(itemStack, mouseX, mouseY);
    }

    public void renderTooltip(String text, int mouseX, int mouseY) {
        this.proxy.renderTooltip(text, mouseX, mouseY);
    }

    public void renderTooltip(List<String> text, int mouseX, int mouseY) {
        this.proxy.renderTooltip(text, mouseX, mouseY);
    }

    public static void bindFace(String uuid, String username) {
        Identifier identifier = ClientPlayerEntity.getSkinTextureLocation(username);
        if (identifier == null) {
            identifier = DefaultSkinUtils.getDefaultSkin(UUIDTypeAdapter.fromString(uuid));
        }

        ClientPlayerEntity.loadSkinTexture(identifier, username);
        Minecraft.getInstance().getTextureManager().bind(identifier);
    }

    public static void bind(String id) {
        Identifier identifier = new Identifier(id);
        Minecraft.getInstance().getTextureManager().bind(identifier);
    }

    public void tick() {
    }

    public int width() {
        return this.proxy.width;
    }

    public int height() {
        return this.proxy.height;
    }

    public int fontLineHeight() {
        return this.proxy.getFontHeight();
    }

    public int fontWidth(String s) {
        return this.proxy.getStringWidth(s);
    }

    public void fontDrawShadow(String s, int x, int y, int color) {
        this.proxy.drawWithShadow(s, x, y, color);
    }

    public List<String> fontSplit(String s, int width) {
        return this.proxy.wrapLines(s, width);
    }

    public void buttonClicked(RealmsButton button) {
    }

    public static RealmsButton newButton(int id, int x, int y, String message) {
        return new RealmsButton(id, x, y, message);
    }

    public static RealmsButton newButton(int id, int x, int y, int width, int height, String message) {
        return new RealmsButton(id, x, y, width, height, message);
    }

    public void buttonsClear() {
        this.proxy.clear();
    }

    public void buttonsAdd(RealmsButton button) {
        this.proxy.addButton(button);
    }

    public List<RealmsButton> buttons() {
        return this.proxy.getButtons();
    }

    public void buttonsRemove(RealmsButton button) {
        this.proxy.removeButton(button);
    }

    public RealmsEditBox newEditBox(int id, int x, int y, int width, int height) {
        return new RealmsEditBox(id, x, y, width, height);
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
    }

    public void mouseEvent() {
    }

    public void keyboardEvent() {
    }

    public void mouseReleased(int mouseX, int mouseY, int button) {
    }

    public void mouseDragged(int mouseX, int mouseY, int button, long duration) {
    }

    public void keyPressed(char chr, int code) {
    }

    public void confirmResult(boolean result, int id) {
    }

    public static String getLocalizedString(String key) {
        return I18n.translate(key);
    }

    public static String getLocalizedString(String key, Object... args) {
        return I18n.translate(key, args);
    }

    public RealmsAnvilLevelStorageSource getLevelStorageSource() {
        return new RealmsAnvilLevelStorageSource(Minecraft.getInstance().getWorldStorageSource());
    }

    public void removed() {
    }
}
