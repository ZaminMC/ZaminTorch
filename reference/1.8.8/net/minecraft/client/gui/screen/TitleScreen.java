package net.minecraft.client.gui.screen;

import com.google.common.collect.Lists;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.options.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.options.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.LanguageButton;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.realms.RealmsBridge;
import net.minecraft.resource.Identifier;
import net.minecraft.server.world.DemoServerWorld;
import net.minecraft.text.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldData;
import net.minecraft.world.storage.WorldStorageSource;
import org.apache.commons.io.Charsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.util.glu.Project;

public class TitleScreen extends Screen implements ConfirmationListener {
    private static final AtomicInteger mcoAvailabilityCheckerCount = new AtomicInteger(0);
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Random RANDOM = new Random();
    private float ticks;
    private String splashText;
    private ButtonWidget buttonResetDemo;
    private int time;
    private DynamicTexture defualtBackgroundImage;
    private boolean realmsEnabled = true;
    private final Object threadedLock = new Object();
    private String outdatedGpuWarning;
    private String warningInfo;
    private String warningInfoLink;
    private static final Identifier SPLASHES_LOCATION = new Identifier("texts/splashes.txt");
    private static final Identifier TITLE_LOCATION = new Identifier("textures/gui/title/minecraft.png");
    private static final Identifier[] PANORAMA_LOCATIONS = new Identifier[]{
        new Identifier("textures/gui/title/background/panorama_0.png"),
        new Identifier("textures/gui/title/background/panorama_1.png"),
        new Identifier("textures/gui/title/background/panorama_2.png"),
        new Identifier("textures/gui/title/background/panorama_3.png"),
        new Identifier("textures/gui/title/background/panorama_4.png"),
        new Identifier("textures/gui/title/background/panorama_5.png")
    };
    public static final String MORE_INFO = "Please click " + Formatting.UNDERLINE + "here" + Formatting.RESET + " for more information.";
    private int warningTextWidth;
    private int outdatedGpuTextWidth;
    private int x1;
    private int y1;
    private int x2;
    private int y2;
    private Identifier backgroundLocation;
    private ButtonWidget realmsButton;

    public TitleScreen() {
        this.warningInfo = MORE_INFO;
        this.splashText = "missingno";
        BufferedReader bufferedreader = null;

        try {
            List<String> list = Lists.newArrayList();
            bufferedreader = new BufferedReader(
                new InputStreamReader(Minecraft.getInstance().getResourceManager().getResource(SPLASHES_LOCATION).asStream(), Charsets.UTF_8)
            );

            String s;
            while ((s = bufferedreader.readLine()) != null) {
                s = s.trim();
                if (!s.isEmpty()) {
                    list.add(s);
                }
            }

            if (!list.isEmpty()) {
                do {
                    this.splashText = list.get(RANDOM.nextInt(list.size()));
                } while (this.splashText.hashCode() == 125780783);
            }
        } catch (IOException ioexception1) {
        } finally {
            if (bufferedreader != null) {
                try {
                    bufferedreader.close();
                } catch (IOException ioexception) {
                }
            }
        }

        this.ticks = RANDOM.nextFloat();
        this.outdatedGpuWarning = "";
        if (!GLContext.getCapabilities().OpenGL20 && !GLX.isNextGen()) {
            this.outdatedGpuWarning = I18n.translate("title.oldgl1");
            this.warningInfo = I18n.translate("title.oldgl2");
            this.warningInfoLink = "https://help.mojang.com/customer/portal/articles/325948?ref=game";
        }
    }

    @Override
    public void tick() {
        this.time++;
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }

    @Override
    protected void keyPressed(char chr, int key) {
    }

    @Override
    public void init() {
        this.defualtBackgroundImage = new DynamicTexture(256, 256);
        this.backgroundLocation = this.minecraft.getTextureManager().register("background", this.defualtBackgroundImage);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        if (calendar.get(2) + 1 == 12 && calendar.get(5) == 24) {
            this.splashText = "Merry X-mas!";
        } else if (calendar.get(2) + 1 == 1 && calendar.get(5) == 1) {
            this.splashText = "Happy new year!";
        } else if (calendar.get(2) + 1 == 10 && calendar.get(5) == 31) {
            this.splashText = "OOoooOOOoooo! Spooky!";
        }

        int i = 24;
        int j = this.height / 4 + 48;
        if (this.minecraft.isDemo()) {
            this.initWidgetsDemo(j, 24);
        } else {
            this.initWidgetsNormal(j, 24);
        }

        this.buttons.add(new ButtonWidget(0, this.width / 2 - 100, j + 72 + 12, 98, 20, I18n.translate("menu.options")));
        this.buttons.add(new ButtonWidget(4, this.width / 2 + 2, j + 72 + 12, 98, 20, I18n.translate("menu.quit")));
        this.buttons.add(new LanguageButton(5, this.width / 2 - 124, j + 72 + 12));
        synchronized (this.threadedLock) {
            this.outdatedGpuTextWidth = this.textRenderer.getWidth(this.outdatedGpuWarning);
            this.warningTextWidth = this.textRenderer.getWidth(this.warningInfo);
            int k = Math.max(this.outdatedGpuTextWidth, this.warningTextWidth);
            this.x1 = (this.width - k) / 2;
            this.y1 = this.buttons.get(0).y - 24;
            this.x2 = this.x1 + k;
            this.y2 = this.y1 + 24;
        }

        this.minecraft.setConnectedToRealms(false);
    }

    private void initWidgetsNormal(int height, int offset) {
        this.buttons.add(new ButtonWidget(1, this.width / 2 - 100, height, I18n.translate("menu.singleplayer")));
        this.buttons.add(new ButtonWidget(2, this.width / 2 - 100, height + offset * 1, I18n.translate("menu.multiplayer")));
        this.buttons.add(this.realmsButton = new ButtonWidget(14, this.width / 2 - 100, height + offset * 2, I18n.translate("menu.online")));
    }

    private void initWidgetsDemo(int y, int spacingY) {
        this.buttons.add(new ButtonWidget(11, this.width / 2 - 100, y, I18n.translate("menu.playdemo")));
        this.buttons.add(this.buttonResetDemo = new ButtonWidget(12, this.width / 2 - 100, y + spacingY * 1, I18n.translate("menu.resetdemo")));
        WorldStorageSource worldstoragesource = this.minecraft.getWorldStorageSource();
        WorldData worlddata = worldstoragesource.getData("Demo_World");
        if (worlddata == null) {
            this.buttonResetDemo.active = false;
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == 0) {
            this.minecraft.openScreen(new OptionsScreen(this, this.minecraft.options));
        }

        if (button.id == 5) {
            this.minecraft.openScreen(new LanguageOptionsScreen(this, this.minecraft.options, this.minecraft.getLanguageManager()));
        }

        if (button.id == 1) {
            this.minecraft.openScreen(new SelectWorldScreen(this));
        }

        if (button.id == 2) {
            this.minecraft.openScreen(new MultiplayerScreen(this));
        }

        if (button.id == 14 && this.realmsButton.visible) {
            this.switchToRealms();
        }

        if (button.id == 4) {
            this.minecraft.stop();
        }

        if (button.id == 11) {
            this.minecraft.startGame("Demo_World", "Demo_World", DemoServerWorld.SETTINGS);
        }

        if (button.id == 12) {
            WorldStorageSource worldstoragesource = this.minecraft.getWorldStorageSource();
            WorldData worlddata = worldstoragesource.getData("Demo_World");
            if (worlddata != null) {
                ConfirmScreen confirmscreen = SelectWorldScreen.getDeleteWarningPrompt(this, worlddata.getName(), 12);
                this.minecraft.openScreen(confirmscreen);
            }
        }
    }

    private void switchToRealms() {
        RealmsBridge realmsbridge = new RealmsBridge();
        realmsbridge.switchToRealms(this);
    }

    @Override
    public void confirmResult(boolean result, int id) {
        if (result && id == 12) {
            WorldStorageSource worldstoragesource = this.minecraft.getWorldStorageSource();
            worldstoragesource.flush();
            worldstoragesource.delete("Demo_World");
            this.minecraft.openScreen(this);
        } else if (id == 13) {
            if (result) {
                try {
                    Class<?> oclass = Class.forName("java.awt.Desktop");
                    Object object = oclass.getMethod("getDesktop").invoke(null);
                    oclass.getMethod("browse", URI.class).invoke(object, new URI(this.warningInfoLink));
                } catch (Throwable throwable) {
                    LOGGER.error("Couldn't open link", throwable);
                }
            }

            this.minecraft.openScreen(this);
        }
    }

    private void drawBackgroundBase(int mouseX, int mouseY, float tickdelta) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        GlStateManager.matrixMode(5889);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Project.gluPerspective(120.0F, 1.0F, 0.05F, 10.0F);
        GlStateManager.matrixMode(5888);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.rotatef(180.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotatef(90.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.disableAlphaTest();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        int i = 8;

        for (int j = 0; j < i * i; j++) {
            GlStateManager.pushMatrix();
            float f = ((float)(j % i) / i - 0.5F) / 64.0F;
            float f1 = ((float)(j / i) / i - 0.5F) / 64.0F;
            float f2 = 0.0F;
            GlStateManager.translatef(f, f1, f2);
            GlStateManager.rotatef(MathHelper.sin((this.time + tickdelta) / 400.0F) * 25.0F + 20.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotatef(-(this.time + tickdelta) * 0.1F, 0.0F, 1.0F, 0.0F);

            for (int k = 0; k < 6; k++) {
                GlStateManager.pushMatrix();
                if (k == 1) {
                    GlStateManager.rotatef(90.0F, 0.0F, 1.0F, 0.0F);
                }

                if (k == 2) {
                    GlStateManager.rotatef(180.0F, 0.0F, 1.0F, 0.0F);
                }

                if (k == 3) {
                    GlStateManager.rotatef(-90.0F, 0.0F, 1.0F, 0.0F);
                }

                if (k == 4) {
                    GlStateManager.rotatef(90.0F, 1.0F, 0.0F, 0.0F);
                }

                if (k == 5) {
                    GlStateManager.rotatef(-90.0F, 1.0F, 0.0F, 0.0F);
                }

                this.minecraft.getTextureManager().bind(PANORAMA_LOCATIONS[k]);
                bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
                int l = 255 / (j + 1);
                float f3 = 0.0F;
                bufferbuilder.vertex(-1.0, -1.0, 1.0).texture(0.0, 0.0).color(255, 255, 255, l).nextVertex();
                bufferbuilder.vertex(1.0, -1.0, 1.0).texture(1.0, 0.0).color(255, 255, 255, l).nextVertex();
                bufferbuilder.vertex(1.0, 1.0, 1.0).texture(1.0, 1.0).color(255, 255, 255, l).nextVertex();
                bufferbuilder.vertex(-1.0, 1.0, 1.0).texture(0.0, 1.0).color(255, 255, 255, l).nextVertex();
                tesselator.end();
                GlStateManager.popMatrix();
            }

            GlStateManager.popMatrix();
            GlStateManager.colorMask(true, true, true, false);
        }

        bufferbuilder.offset(0.0, 0.0, 0.0);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.matrixMode(5889);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(5888);
        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableDepthTest();
    }

    private void drawBackgroundImage(float tickDelta) {
        this.minecraft.getTextureManager().bind(this.backgroundLocation);
        GL11.glTexParameteri(3553, 10241, 9729);
        GL11.glTexParameteri(3553, 10240, 9729);
        GL11.glCopyTexSubImage2D(3553, 0, 0, 0, 0, 0, 256, 256);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.colorMask(true, true, true, false);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
        GlStateManager.disableAlphaTest();
        int i = 3;

        for (int j = 0; j < i; j++) {
            float f = 1.0F / (j + 1);
            int k = this.width;
            int l = this.height;
            float f1 = (j - i / 2) / 256.0F;
            bufferbuilder.vertex(k, l, this.drawOffset).texture(0.0F + f1, 1.0).color(1.0F, 1.0F, 1.0F, f).nextVertex();
            bufferbuilder.vertex(k, 0.0, this.drawOffset).texture(1.0F + f1, 1.0).color(1.0F, 1.0F, 1.0F, f).nextVertex();
            bufferbuilder.vertex(0.0, 0.0, this.drawOffset).texture(1.0F + f1, 0.0).color(1.0F, 1.0F, 1.0F, f).nextVertex();
            bufferbuilder.vertex(0.0, l, this.drawOffset).texture(0.0F + f1, 0.0).color(1.0F, 1.0F, 1.0F, f).nextVertex();
        }

        tesselator.end();
        GlStateManager.enableAlphaTest();
        GlStateManager.colorMask(true, true, true, true);
    }

    private void drawBackground(int mouseX, int mouseY, float tickDelta) {
        this.minecraft.getRenderTarget().unbindWrite();
        GlStateManager.viewport(0, 0, 256, 256);
        this.drawBackgroundBase(mouseX, mouseY, tickDelta);
        this.drawBackgroundImage(tickDelta);
        this.drawBackgroundImage(tickDelta);
        this.drawBackgroundImage(tickDelta);
        this.drawBackgroundImage(tickDelta);
        this.drawBackgroundImage(tickDelta);
        this.drawBackgroundImage(tickDelta);
        this.drawBackgroundImage(tickDelta);
        this.minecraft.getRenderTarget().bindWrite(true);
        GlStateManager.viewport(0, 0, this.minecraft.width, this.minecraft.height);
        float f = this.width > this.height ? 120.0F / this.width : 120.0F / this.height;
        float f1 = this.height * f / 256.0F;
        float f2 = this.width * f / 256.0F;
        int i = this.width;
        int j = this.height;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferbuilder.vertex(0.0, j, this.drawOffset).texture(0.5F - f1, 0.5F + f2).color(1.0F, 1.0F, 1.0F, 1.0F).nextVertex();
        bufferbuilder.vertex(i, j, this.drawOffset).texture(0.5F - f1, 0.5F - f2).color(1.0F, 1.0F, 1.0F, 1.0F).nextVertex();
        bufferbuilder.vertex(i, 0.0, this.drawOffset).texture(0.5F + f1, 0.5F - f2).color(1.0F, 1.0F, 1.0F, 1.0F).nextVertex();
        bufferbuilder.vertex(0.0, 0.0, this.drawOffset).texture(0.5F + f1, 0.5F + f2).color(1.0F, 1.0F, 1.0F, 1.0F).nextVertex();
        tesselator.end();
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        GlStateManager.disableAlphaTest();
        this.drawBackground(mouseX, mouseY, tickDelta);
        GlStateManager.enableAlphaTest();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        int i = 274;
        int j = this.width / 2 - i / 2;
        int k = 30;
        this.fillGradient(0, 0, this.width, this.height, -2130706433, 16777215);
        this.fillGradient(0, 0, this.width, this.height, 0, Integer.MIN_VALUE);
        this.minecraft.getTextureManager().bind(TITLE_LOCATION);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (this.ticks < 1.0E-4) {
            this.drawTexture(j + 0, k + 0, 0, 0, 99, 44);
            this.drawTexture(j + 99, k + 0, 129, 0, 27, 44);
            this.drawTexture(j + 99 + 26, k + 0, 126, 0, 3, 44);
            this.drawTexture(j + 99 + 26 + 3, k + 0, 99, 0, 26, 44);
            this.drawTexture(j + 155, k + 0, 0, 45, 155, 44);
        } else {
            this.drawTexture(j + 0, k + 0, 0, 0, 155, 44);
            this.drawTexture(j + 155, k + 0, 0, 45, 155, 44);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translatef(this.width / 2 + 90, 70.0F, 0.0F);
        GlStateManager.rotatef(-20.0F, 0.0F, 0.0F, 1.0F);
        float f = 1.8F - MathHelper.abs(MathHelper.sin((float)(Minecraft.getTime() % 1000L) / 1000.0F * (float) Math.PI * 2.0F) * 0.1F);
        f = f * 100.0F / (this.textRenderer.getWidth(this.splashText) + 32);
        GlStateManager.scalef(f, f, f);
        this.drawCenteredString(this.textRenderer, this.splashText, 0, -8, -256);
        GlStateManager.popMatrix();
        String s = "Minecraft 1.8.8";
        if (this.minecraft.isDemo()) {
            s = s + " Demo";
        }

        this.drawString(this.textRenderer, s, 2, this.height - 10, -1);
        String s1 = "Copyright Mojang AB. Do not distribute!";
        this.drawString(this.textRenderer, s1, this.width - this.textRenderer.getWidth(s1) - 2, this.height - 10, -1);
        if (this.outdatedGpuWarning != null && this.outdatedGpuWarning.length() > 0) {
            fill(this.x1 - 2, this.y1 - 2, this.x2 + 2, this.y2 - 1, 1428160512);
            this.drawString(this.textRenderer, this.outdatedGpuWarning, this.x1, this.y1, -1);
            this.drawString(this.textRenderer, this.warningInfo, (this.width - this.warningTextWidth) / 2, this.buttons.get(0).y - 12, -1);
        }

        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        synchronized (this.threadedLock) {
            if (this.outdatedGpuWarning.length() > 0 && mouseX >= this.x1 && mouseX <= this.x2 && mouseY >= this.y1 && mouseY <= this.y2) {
                ConfirmChatLinkScreen confirmchatlinkscreen = new ConfirmChatLinkScreen(this, this.warningInfoLink, 13, true);
                confirmchatlinkscreen.noWarning();
                this.minecraft.openScreen(confirmchatlinkscreen);
            }
        }
    }
}
