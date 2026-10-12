package net.minecraft.client.gui.screen;

import com.google.common.collect.Lists;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.sound.MusicManager;
import net.minecraft.client.sound.system.SoundManager;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Formatting;
import org.apache.commons.io.Charsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CreditsScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Identifier TITLE_LOCATION = new Identifier("textures/gui/title/minecraft.png");
    private static final Identifier VIGNETTE_LOCATION = new Identifier("textures/misc/vignette.png");
    private int ticksOpen;
    private List<String> creditTextLines;
    private int creditsHeight;
    private float speed = 0.5F;

    @Override
    public void tick() {
        MusicManager musicmanager = this.minecraft.getMusicManager();
        SoundManager soundmanager = this.minecraft.getSoundManager();
        if (this.ticksOpen == 0) {
            musicmanager.stopPlaying();
            musicmanager.startPlaying(MusicManager.Music.CREDITS);
            soundmanager.resume();
        }

        soundmanager.tick();
        this.ticksOpen++;
        float f = (this.creditsHeight + this.height + this.height + 24) / this.speed;
        if (this.ticksOpen > f) {
            this.respawn();
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == 1) {
            this.respawn();
        }
    }

    private void respawn() {
        this.minecraft.player.networkHandler.sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Status.PERFORM_RESPAWN));
        this.minecraft.openScreen(null);
    }

    @Override
    public boolean shouldPauseGame() {
        return true;
    }

    @Override
    public void init() {
        if (this.creditTextLines == null) {
            this.creditTextLines = Lists.newArrayList();

            try {
                String s = "";
                String s1 = "" + Formatting.WHITE + Formatting.OBFUSCATED + Formatting.GREEN + Formatting.AQUA;
                int i = 274;
                InputStream inputstream = this.minecraft.getResourceManager().getResource(new Identifier("texts/end.txt")).asStream();
                BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(inputstream, Charsets.UTF_8));
                Random random = new Random(8124371L);

                while ((s = bufferedreader.readLine()) != null) {
                    s = s.replaceAll("PLAYERNAME", this.minecraft.getSession().getUsername());

                    while (s.contains(s1)) {
                        int j = s.indexOf(s1);
                        String s2 = s.substring(0, j);
                        String s3 = s.substring(j + s1.length());
                        s = s2 + Formatting.WHITE + Formatting.OBFUSCATED + "XXXXXXXX".substring(0, random.nextInt(4) + 3) + s3;
                    }

                    this.creditTextLines.addAll(this.minecraft.textRenderer.split(s, i));
                    this.creditTextLines.add("");
                }

                inputstream.close();

                for (int k = 0; k < 8; k++) {
                    this.creditTextLines.add("");
                }

                inputstream = this.minecraft.getResourceManager().getResource(new Identifier("texts/credits.txt")).asStream();
                bufferedreader = new BufferedReader(new InputStreamReader(inputstream, Charsets.UTF_8));

                while ((s = bufferedreader.readLine()) != null) {
                    s = s.replaceAll("PLAYERNAME", this.minecraft.getSession().getUsername());
                    s = s.replaceAll("\t", "    ");
                    this.creditTextLines.addAll(this.minecraft.textRenderer.split(s, i));
                    this.creditTextLines.add("");
                }

                inputstream.close();
                this.creditsHeight = this.creditTextLines.size() * 12;
            } catch (Exception exception) {
                LOGGER.error("Couldn't load credits", exception);
            }
        }
    }

    private void renderBackground(int mouseX, int mouseY, float tickDelta) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        this.minecraft.getTextureManager().bind(GuiElement.BACKGROUND_LOCATION);
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
        int i = this.width;
        float f = 0.0F - (this.ticksOpen + tickDelta) * 0.5F * this.speed;
        float f1 = this.height - (this.ticksOpen + tickDelta) * 0.5F * this.speed;
        float f2 = 0.015625F;
        float f3 = (this.ticksOpen + tickDelta - 0.0F) * 0.02F;
        float f4 = (this.creditsHeight + this.height + this.height + 24) / this.speed;
        float f5 = (f4 - 20.0F - (this.ticksOpen + tickDelta)) * 0.005F;
        if (f5 < f3) {
            f3 = f5;
        }

        if (f3 > 1.0F) {
            f3 = 1.0F;
        }

        f3 *= f3;
        f3 = f3 * 96.0F / 255.0F;
        bufferbuilder.vertex(0.0, this.height, this.drawOffset).texture(0.0, f * f2).color(f3, f3, f3, 1.0F).nextVertex();
        bufferbuilder.vertex(i, this.height, this.drawOffset).texture(i * f2, f * f2).color(f3, f3, f3, 1.0F).nextVertex();
        bufferbuilder.vertex(i, 0.0, this.drawOffset).texture(i * f2, f1 * f2).color(f3, f3, f3, 1.0F).nextVertex();
        bufferbuilder.vertex(0.0, 0.0, this.drawOffset).texture(0.0, f1 * f2).color(f3, f3, f3, 1.0F).nextVertex();
        tesselator.end();
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground(mouseX, mouseY, tickDelta);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        int i = 274;
        int j = this.width / 2 - i / 2;
        int k = this.height + 50;
        float f = -(this.ticksOpen + tickDelta) * this.speed;
        GlStateManager.pushMatrix();
        GlStateManager.translatef(0.0F, f, 0.0F);
        this.minecraft.getTextureManager().bind(TITLE_LOCATION);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.drawTexture(j, k, 0, 0, 155, 44);
        this.drawTexture(j + 155, k, 0, 45, 155, 44);
        int l = k + 200;

        for (int i1 = 0; i1 < this.creditTextLines.size(); i1++) {
            if (i1 == this.creditTextLines.size() - 1) {
                float f1 = l + f - (this.height / 2 - 6);
                if (f1 < 0.0F) {
                    GlStateManager.translatef(0.0F, -f1, 0.0F);
                }
            }

            if (l + f + 12.0F + 8.0F > 0.0F && l + f < this.height) {
                String s = this.creditTextLines.get(i1);
                if (s.startsWith("[C]")) {
                    this.textRenderer.drawWithShadow(s.substring(3), j + (i - this.textRenderer.getWidth(s.substring(3))) / 2, l, 16777215);
                } else {
                    this.textRenderer.random.setSeed(i1 * 4238972211L + this.ticksOpen / 4);
                    this.textRenderer.drawWithShadow(s, j, l, 16777215);
                }
            }

            l += 12;
        }

        GlStateManager.popMatrix();
        this.minecraft.getTextureManager().bind(VIGNETTE_LOCATION);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(0, 769);
        int j1 = this.width;
        int k1 = this.height;
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferbuilder.vertex(0.0, k1, this.drawOffset).texture(0.0, 1.0).color(1.0F, 1.0F, 1.0F, 1.0F).nextVertex();
        bufferbuilder.vertex(j1, k1, this.drawOffset).texture(1.0, 1.0).color(1.0F, 1.0F, 1.0F, 1.0F).nextVertex();
        bufferbuilder.vertex(j1, 0.0, this.drawOffset).texture(1.0, 0.0).color(1.0F, 1.0F, 1.0F, 1.0F).nextVertex();
        bufferbuilder.vertex(0.0, 0.0, this.drawOffset).texture(0.0, 0.0).color(1.0F, 1.0F, 1.0F, 1.0F).nextVertex();
        tesselator.end();
        GlStateManager.disableBlend();
        super.render(mouseX, mouseY, tickDelta);
    }
}
