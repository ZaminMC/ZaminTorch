package net.minecraft.client.gui.widget;

import com.google.common.base.Charsets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import java.awt.image.BufferedImage;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Formatting;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerListEntryWidget implements EntryListWidget.Entry {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ThreadPoolExecutor EXECUTOR = new ScheduledThreadPoolExecutor(
        5, new ThreadFactoryBuilder().setNameFormat("Server Pinger #%d").setDaemon(true).build()
    );
    private static final Identifier UNKNOWN_SERVER_LOCATION = new Identifier("textures/misc/unknown_server.png");
    private static final Identifier SERVER_SELECTION_LOCATION = new Identifier("textures/gui/server_selection.png");
    private final MultiplayerScreen screen;
    private final Minecraft minecraft;
    private final ServerListEntry entry;
    private final Identifier iconIdentifier;
    private String icon;
    private DynamicTexture iconTexture;
    private long lastMouseClickedTime;

    protected ServerListEntryWidget(MultiplayerScreen screen, ServerListEntry entry) {
        this.screen = screen;
        this.entry = entry;
        this.minecraft = Minecraft.getInstance();
        this.iconIdentifier = new Identifier("servers/" + entry.ip + "/icon");
        this.iconTexture = (DynamicTexture)this.minecraft.getTextureManager().get(this.iconIdentifier);
    }

    @Override
    public void render(int index, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
        if (!this.entry.loaded) {
            this.entry.loaded = true;
            this.entry.ping = -2L;
            this.entry.motd = "";
            this.entry.status = "";
            EXECUTOR.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        ServerListEntryWidget.this.screen.getServerListPinger().add(ServerListEntryWidget.this.entry);
                    } catch (UnknownHostException unknownhostexception) {
                        ServerListEntryWidget.this.entry.ping = -1L;
                        ServerListEntryWidget.this.entry.motd = Formatting.DARK_RED + "Can't resolve hostname";
                    } catch (Exception exception) {
                        ServerListEntryWidget.this.entry.ping = -1L;
                        ServerListEntryWidget.this.entry.motd = Formatting.DARK_RED + "Can't connect to server.";
                    }
                }
            });
        }

        boolean flag = this.entry.protocol > 47;
        boolean flag1 = this.entry.protocol < 47;
        boolean flag2 = flag || flag1;
        this.minecraft.textRenderer.draw(this.entry.name, x + 32 + 3, y + 1, 16777215);
        List<String> list = this.minecraft.textRenderer.split(this.entry.motd, width - 32 - 2);

        for (int i = 0; i < Math.min(list.size(), 2); i++) {
            this.minecraft.textRenderer.draw(list.get(i), x + 32 + 3, y + 12 + this.minecraft.textRenderer.fontHeight * i, 8421504);
        }

        String s2 = flag2 ? Formatting.DARK_RED + this.entry.version : this.entry.status;
        int j = this.minecraft.textRenderer.getWidth(s2);
        this.minecraft.textRenderer.draw(s2, x + width - j - 15 - 2, y + 1, 8421504);
        int k = 0;
        String s = null;
        int l;
        String s1;
        if (flag2) {
            l = 5;
            s1 = flag ? "Client out of date!" : "Server out of date!";
            s = this.entry.onlinePlayers;
        } else if (this.entry.loaded && this.entry.ping != -2L) {
            if (this.entry.ping < 0L) {
                l = 5;
            } else if (this.entry.ping < 150L) {
                l = 0;
            } else if (this.entry.ping < 300L) {
                l = 1;
            } else if (this.entry.ping < 600L) {
                l = 2;
            } else if (this.entry.ping < 1000L) {
                l = 3;
            } else {
                l = 4;
            }

            if (this.entry.ping < 0L) {
                s1 = "(no connection)";
            } else {
                s1 = this.entry.ping + "ms";
                s = this.entry.onlinePlayers;
            }
        } else {
            k = 1;
            l = (int)(Minecraft.getTime() / 100L + index * 2 & 7L);
            if (l > 4) {
                l = 8 - l;
            }

            s1 = "Pinging...";
        }

        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(GuiElement.ICONS_LOCATION);
        GuiElement.drawTexture(x + width - 15, y, k * 10, 176 + l * 8, 10, 8, 256.0F, 256.0F);
        if (this.entry.getIcon() != null && !this.entry.getIcon().equals(this.icon)) {
            this.icon = this.entry.getIcon();
            this.loadServerIcon();
            this.screen.getServerList().save();
        }

        if (this.iconTexture != null) {
            this.drawIcon(x, y, this.iconIdentifier);
        } else {
            this.drawIcon(x, y, UNKNOWN_SERVER_LOCATION);
        }

        int i1 = mouseX - x;
        int j1 = mouseY - y;
        if (i1 >= width - 15 && i1 <= width - 5 && j1 >= 0 && j1 <= 8) {
            this.screen.setTooltip(s1);
        } else if (i1 >= width - j - 15 - 2 && i1 <= width - 15 - 2 && j1 >= 0 && j1 <= 8) {
            this.screen.setTooltip(s);
        }

        if (this.minecraft.options.touchscreen || hovered) {
            this.minecraft.getTextureManager().bind(SERVER_SELECTION_LOCATION);
            GuiElement.fill(x, y, x + 32, y + 32, -1601138544);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            int k1 = mouseX - x;
            int l1 = mouseY - y;
            if (this.m_1489497()) {
                if (k1 < 32 && k1 > 16) {
                    GuiElement.drawTexture(x, y, 0.0F, 32.0F, 32, 32, 256.0F, 256.0F);
                } else {
                    GuiElement.drawTexture(x, y, 0.0F, 0.0F, 32, 32, 256.0F, 256.0F);
                }
            }

            if (this.screen.canMoveUp(this, index)) {
                if (k1 < 16 && l1 < 16) {
                    GuiElement.drawTexture(x, y, 96.0F, 32.0F, 32, 32, 256.0F, 256.0F);
                } else {
                    GuiElement.drawTexture(x, y, 96.0F, 0.0F, 32, 32, 256.0F, 256.0F);
                }
            }

            if (this.screen.canMoveDown(this, index)) {
                if (k1 < 16 && l1 > 16) {
                    GuiElement.drawTexture(x, y, 64.0F, 32.0F, 32, 32, 256.0F, 256.0F);
                } else {
                    GuiElement.drawTexture(x, y, 64.0F, 0.0F, 32, 32, 256.0F, 256.0F);
                }
            }
        }
    }

    protected void drawIcon(int x, int y, Identifier id) {
        this.minecraft.getTextureManager().bind(id);
        GlStateManager.enableBlend();
        GuiElement.drawTexture(x, y, 0.0F, 0.0F, 32, 32, 32.0F, 32.0F);
        GlStateManager.disableBlend();
    }

    private boolean m_1489497() {
        return true;
    }

    private void loadServerIcon() {
        if (this.entry.getIcon() == null) {
            this.minecraft.getTextureManager().close(this.iconIdentifier);
            this.iconTexture = null;
        } else {
            ByteBuf bytebuf = Unpooled.copiedBuffer(this.entry.getIcon(), Charsets.UTF_8);
            ByteBuf bytebuf1 = Base64.decode(bytebuf);

            BufferedImage bufferedimage;
            label62: {
                try {
                    bufferedimage = TextureUtil.readImage(new ByteBufInputStream(bytebuf1));
                    Validate.validState(bufferedimage.getWidth() == 64, "Must be 64 pixels wide");
                    Validate.validState(bufferedimage.getHeight() == 64, "Must be 64 pixels high");
                    break label62;
                } catch (Throwable throwable) {
                    LOGGER.error("Invalid icon for server " + this.entry.name + " (" + this.entry.ip + ")", throwable);
                    this.entry.setIcon(null);
                } finally {
                    bytebuf.release();
                    bytebuf1.release();
                }

                return;
            }

            if (this.iconTexture == null) {
                this.iconTexture = new DynamicTexture(bufferedimage.getWidth(), bufferedimage.getHeight());
                this.minecraft.getTextureManager().register(this.iconIdentifier, this.iconTexture);
            }

            bufferedimage.getRGB(0, 0, bufferedimage.getWidth(), bufferedimage.getHeight(), this.iconTexture.getPixels(), 0, bufferedimage.getWidth());
            this.iconTexture.upload();
        }
    }

    @Override
    public boolean mouseClicked(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
        if (entryMouseX <= 32) {
            if (entryMouseX < 32 && entryMouseX > 16 && this.m_1489497()) {
                this.screen.moveToServer(index);
                this.screen.connect();
                return true;
            }

            if (entryMouseX < 16 && entryMouseY < 16 && this.screen.canMoveUp(this, index)) {
                this.screen.moveUp(this, index, Screen.isShiftDown());
                return true;
            }

            if (entryMouseX < 16 && entryMouseY > 16 && this.screen.canMoveDown(this, index)) {
                this.screen.moveDown(this, index, Screen.isShiftDown());
                return true;
            }
        }

        this.screen.moveToServer(index);
        if (Minecraft.getTime() - this.lastMouseClickedTime < 250L) {
            this.screen.connect();
        }

        this.lastMouseClickedTime = Minecraft.getTime();
        return false;
    }

    @Override
    public void renderOutOfBounds(int index, int x, int y) {
    }

    @Override
    public void mouseReleased(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
    }

    public ServerListEntry fetchServer() {
        return this.entry;
    }
}
