package net.minecraft.client.gui.screen;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.LabelWidget;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entities;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.SnbtParser;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.stat.achievement.AchievementStat;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Formatting;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import tv.twitch.chat.ChatUserInfo;

public abstract class Screen extends GuiElement implements ConfirmationListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Set<String> HYPERTEXT_TRANSFER_PROTOCOLS = Sets.newHashSet("http", "https");
    private static final Splitter LINE_SPLITTER = Splitter.on('\n');
    protected Minecraft minecraft;
    protected ItemRenderer itemRenderer;
    public int width;
    public int height;
    protected List<ButtonWidget> buttons = Lists.newArrayList();
    protected List<LabelWidget> labels = Lists.newArrayList();
    public boolean passEvents;
    protected TextRenderer textRenderer;
    private ButtonWidget lastClickedButton;
    private int lastButton;
    private long lastUpdateTime;
    private int multiTouch;
    private URI link;

    public void render(int mouseX, int mouseY, float tickDelta) {
        for (int i = 0; i < this.buttons.size(); i++) {
            this.buttons.get(i).render(this.minecraft, mouseX, mouseY);
        }

        for (int j = 0; j < this.labels.size(); j++) {
            this.labels.get(j).render(this.minecraft, mouseX, mouseY);
        }
    }

    protected void keyPressed(char chr, int key) {
        if (key == 1) {
            this.minecraft.openScreen(null);
            if (this.minecraft.screen == null) {
                this.minecraft.lockMouse();
            }
        }
    }

    public static String getClipboard() {
        try {
            Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return (String)transferable.getTransferData(DataFlavor.stringFlavor);
            }
        } catch (Exception exception) {
        }

        return "";
    }

    public static void setClipboard(String text) {
        if (!StringUtils.isEmpty(text)) {
            try {
                StringSelection stringselection = new StringSelection(text);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringselection, null);
            } catch (Exception exception) {
            }
        }
    }

    protected void renderTooltip(ItemStack item, int mouseX, int mouseY) {
        List<String> list = item.getTooltip(this.minecraft.player, this.minecraft.options.advancedItemTooltips);

        for (int i = 0; i < list.size(); i++) {
            if (i == 0) {
                list.set(i, item.getRarity().formatting + list.get(i));
            } else {
                list.set(i, Formatting.GRAY + list.get(i));
            }
        }

        this.renderTooltip(list, mouseX, mouseY);
    }

    protected void renderTooltip(String text, int mouseX, int mouseY) {
        this.renderTooltip(Arrays.asList(text), mouseX, mouseY);
    }

    protected void renderTooltip(List<String> text, int mouseX, int mouseY) {
        if (!text.isEmpty()) {
            GlStateManager.disableRescaleNormal();
            Lighting.turnOff();
            GlStateManager.disableLighting();
            GlStateManager.disableDepthTest();
            int i = 0;

            for (String s : text) {
                int j = this.textRenderer.getWidth(s);
                if (j > i) {
                    i = j;
                }
            }

            int l1 = mouseX + 12;
            int i2 = mouseY - 12;
            int j2 = i;
            int k = 8;
            if (text.size() > 1) {
                k += 2 + (text.size() - 1) * 10;
            }

            if (l1 + i > this.width) {
                l1 -= 28 + i;
            }

            if (i2 + k + 6 > this.height) {
                i2 = this.height - k - 6;
            }

            this.drawOffset = 300.0F;
            this.itemRenderer.zOffset = 300.0F;
            int l = -267386864;
            this.fillGradient(l1 - 3, i2 - 4, l1 + j2 + 3, i2 - 3, l, l);
            this.fillGradient(l1 - 3, i2 + k + 3, l1 + j2 + 3, i2 + k + 4, l, l);
            this.fillGradient(l1 - 3, i2 - 3, l1 + j2 + 3, i2 + k + 3, l, l);
            this.fillGradient(l1 - 4, i2 - 3, l1 - 3, i2 + k + 3, l, l);
            this.fillGradient(l1 + j2 + 3, i2 - 3, l1 + j2 + 4, i2 + k + 3, l, l);
            int i1 = 1347420415;
            int j1 = (i1 & 16711422) >> 1 | i1 & 0xFF000000;
            this.fillGradient(l1 - 3, i2 - 3 + 1, l1 - 3 + 1, i2 + k + 3 - 1, i1, j1);
            this.fillGradient(l1 + j2 + 2, i2 - 3 + 1, l1 + j2 + 3, i2 + k + 3 - 1, i1, j1);
            this.fillGradient(l1 - 3, i2 - 3, l1 + j2 + 3, i2 - 3 + 1, i1, i1);
            this.fillGradient(l1 - 3, i2 + k + 2, l1 + j2 + 3, i2 + k + 3, j1, j1);

            for (int k1 = 0; k1 < text.size(); k1++) {
                String s1 = text.get(k1);
                this.textRenderer.drawWithShadow(s1, l1, i2, -1);
                if (k1 == 0) {
                    i2 += 2;
                }

                i2 += 10;
            }

            this.drawOffset = 0.0F;
            this.itemRenderer.zOffset = 0.0F;
            GlStateManager.enableLighting();
            GlStateManager.enableDepthTest();
            Lighting.turnOn();
            GlStateManager.enableRescaleNormal();
        }
    }

    protected void renderTextHoverEffect(Text text, int x, int y) {
        if (text != null && text.getStyle().getHoverEvent() != null) {
            HoverEvent hoverevent = text.getStyle().getHoverEvent();
            if (hoverevent.getAction() == HoverEvent.Action.SHOW_ITEM) {
                ItemStack itemstack = null;

                try {
                    NbtElement nbtelement = SnbtParser.parse(hoverevent.getValue().getString());
                    if (nbtelement instanceof NbtCompound) {
                        itemstack = ItemStack.fromNbt((NbtCompound)nbtelement);
                    }
                } catch (NbtException nbtexception1) {
                }

                if (itemstack != null) {
                    this.renderTooltip(itemstack, x, y);
                } else {
                    this.renderTooltip(Formatting.RED + "Invalid Item!", x, y);
                }
            } else if (hoverevent.getAction() == HoverEvent.Action.SHOW_ENTITY) {
                if (this.minecraft.options.advancedItemTooltips) {
                    try {
                        NbtElement nbtelement1 = SnbtParser.parse(hoverevent.getValue().getString());
                        if (nbtelement1 instanceof NbtCompound) {
                            List<String> list1 = Lists.newArrayList();
                            NbtCompound nbtcompound = (NbtCompound)nbtelement1;
                            list1.add(nbtcompound.getString("name"));
                            if (nbtcompound.contains("type", 8)) {
                                String s = nbtcompound.getString("type");
                                list1.add("Type: " + s + " (" + Entities.getId(s) + ")");
                            }

                            list1.add(nbtcompound.getString("id"));
                            this.renderTooltip(list1, x, y);
                        } else {
                            this.renderTooltip(Formatting.RED + "Invalid Entity!", x, y);
                        }
                    } catch (NbtException nbtexception) {
                        this.renderTooltip(Formatting.RED + "Invalid Entity!", x, y);
                    }
                }
            } else if (hoverevent.getAction() == HoverEvent.Action.SHOW_TEXT) {
                this.renderTooltip(LINE_SPLITTER.splitToList(hoverevent.getValue().getFormattedString()), x, y);
            } else if (hoverevent.getAction() == HoverEvent.Action.SHOW_ACHIEVEMENT) {
                Stat stat = Stats.byKey(hoverevent.getValue().getString());
                if (stat != null) {
                    Text textx = stat.getDecoratedName();
                    Text text1 = new TranslatableText("stats.tooltip.type." + (stat.isAchievement() ? "achievement" : "statistic"));
                    text1.getStyle().setItalic(true);
                    String s1 = stat instanceof AchievementStat ? ((AchievementStat)stat).getDescription() : null;
                    List<String> list = Lists.newArrayList(textx.getFormattedString(), text1.getFormattedString());
                    if (s1 != null) {
                        list.addAll(this.textRenderer.split(s1, 150));
                    }

                    this.renderTooltip(list, x, y);
                } else {
                    this.renderTooltip(Formatting.RED + "Invalid statistic/achievement!", x, y);
                }
            }

            GlStateManager.disableLighting();
        }
    }

    protected void insertText(String text, boolean replace) {
    }

    protected boolean handleClickEvent(Text text) {
        if (text == null) {
            return false;
        }

        ClickEvent clickevent = text.getStyle().getClickEvent();
        if (isShiftDown()) {
            if (text.getStyle().getInsertion() != null) {
                this.insertText(text.getStyle().getInsertion(), false);
            }
        } else if (clickevent != null) {
            if (clickevent.getAction() == ClickEvent.Action.OPEN_URL) {
                if (!this.minecraft.options.chatLinks) {
                    return false;
                }

                try {
                    URI uri = new URI(clickevent.getValue());
                    String s = uri.getScheme();
                    if (s == null) {
                        throw new URISyntaxException(clickevent.getValue(), "Missing protocol");
                    }

                    if (!HYPERTEXT_TRANSFER_PROTOCOLS.contains(s.toLowerCase())) {
                        throw new URISyntaxException(clickevent.getValue(), "Unsupported protocol: " + s.toLowerCase());
                    }

                    if (this.minecraft.options.chatLinksPrompt) {
                        this.link = uri;
                        this.minecraft.openScreen(new ConfirmChatLinkScreen(this, clickevent.getValue(), 31102009, false));
                    } else {
                        this.openLink(uri);
                    }
                } catch (URISyntaxException urisyntaxexception) {
                    LOGGER.error("Can't open url for " + clickevent, urisyntaxexception);
                }
            } else if (clickevent.getAction() == ClickEvent.Action.OPEN_FILE) {
                URI uri1 = new File(clickevent.getValue()).toURI();
                this.openLink(uri1);
            } else if (clickevent.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                this.insertText(clickevent.getValue(), true);
            } else if (clickevent.getAction() == ClickEvent.Action.RUN_COMMAND) {
                this.sendChatMessage(clickevent.getValue(), false);
            } else if (clickevent.getAction() == ClickEvent.Action.TWITCH_USER_INFO) {
                ChatUserInfo chatuserinfo = this.minecraft.getTwitchStream().m_8560465(clickevent.getValue());
                if (chatuserinfo != null) {
                    this.minecraft.openScreen(new TwitchUserInfoScreen(this.minecraft.getTwitchStream(), chatuserinfo));
                } else {
                    LOGGER.error("Tried to handle twitch user but couldn't find them!");
                }
            } else {
                LOGGER.error("Don't know how to handle " + clickevent);
            }

            return true;
        }

        return false;
    }

    public void sendChatMessage(String message) {
        this.sendChatMessage(message, true);
    }

    public void sendChatMessage(String message, boolean addToChat) {
        if (addToChat) {
            this.minecraft.gui.getChat().addRecentMessage(message);
        }

        this.minecraft.player.sendChat(message);
    }

    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < this.buttons.size(); i++) {
                ButtonWidget buttonwidget = this.buttons.get(i);
                if (buttonwidget.mouseClicked(this.minecraft, mouseX, mouseY)) {
                    this.lastClickedButton = buttonwidget;
                    buttonwidget.playClickSound(this.minecraft.getSoundManager());
                    this.buttonClicked(buttonwidget);
                }
            }
        }
    }

    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if (this.lastClickedButton != null && button == 0) {
            this.lastClickedButton.mouseReleased(mouseX, mouseY);
            this.lastClickedButton = null;
        }
    }

    protected void mouseDragged(int mouseX, int mouseY, int button, long duration) {
    }

    protected void buttonClicked(ButtonWidget button) {
    }

    public void init(Minecraft minecraft, int width, int height) {
        this.minecraft = minecraft;
        this.itemRenderer = minecraft.getItemRenderer();
        this.textRenderer = minecraft.textRenderer;
        this.width = width;
        this.height = height;
        this.buttons.clear();
        this.init();
    }

    public void init() {
    }

    public void handleInputs() {
        if (Mouse.isCreated()) {
            while (Mouse.next()) {
                this.handleMouse();
            }
        }

        if (Keyboard.isCreated()) {
            while (Keyboard.next()) {
                this.handleKeyboard();
            }
        }
    }

    public void handleMouse() {
        int i = Mouse.getEventX() * this.width / this.minecraft.width;
        int j = this.height - Mouse.getEventY() * this.height / this.minecraft.height - 1;
        int k = Mouse.getEventButton();
        if (Mouse.getEventButtonState()) {
            if (this.minecraft.options.touchscreen && this.multiTouch++ > 0) {
                return;
            }

            this.lastButton = k;
            this.lastUpdateTime = Minecraft.getTime();
            this.mouseClicked(i, j, this.lastButton);
        } else if (k != -1) {
            if (this.minecraft.options.touchscreen && --this.multiTouch > 0) {
                return;
            }

            this.lastButton = -1;
            this.mouseReleased(i, j, k);
        } else if (this.lastButton != -1 && this.lastUpdateTime > 0L) {
            long l = Minecraft.getTime() - this.lastUpdateTime;
            this.mouseDragged(i, j, this.lastButton, l);
        }
    }

    public void handleKeyboard() {
        if (Keyboard.getEventKeyState()) {
            this.keyPressed(Keyboard.getEventCharacter(), Keyboard.getEventKey());
        }

        this.minecraft.handleGuiKeyBindings();
    }

    public void tick() {
    }

    public void removed() {
    }

    public void renderBackground() {
        this.renderBackground(0);
    }

    public void renderBackground(int offset) {
        if (this.minecraft.world != null) {
            this.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
        } else {
            this.drawBackgroundTexture(offset);
        }
    }

    public void drawBackgroundTexture(int offset) {
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        this.minecraft.getTextureManager().bind(BACKGROUND_LOCATION);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        float f = 32.0F;
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferbuilder.vertex(0.0, this.height, 0.0).texture(0.0, this.height / 32.0F + offset).color(64, 64, 64, 255).nextVertex();
        bufferbuilder.vertex(this.width, this.height, 0.0).texture(this.width / 32.0F, this.height / 32.0F + offset).color(64, 64, 64, 255).nextVertex();
        bufferbuilder.vertex(this.width, 0.0, 0.0).texture(this.width / 32.0F, offset).color(64, 64, 64, 255).nextVertex();
        bufferbuilder.vertex(0.0, 0.0, 0.0).texture(0.0, offset).color(64, 64, 64, 255).nextVertex();
        tesselator.end();
    }

    public boolean shouldPauseGame() {
        return true;
    }

    @Override
    public void confirmResult(boolean result, int id) {
        if (id == 31102009) {
            if (result) {
                this.openLink(this.link);
            }

            this.link = null;
            this.minecraft.openScreen(this);
        }
    }

    private void openLink(URI link) {
        try {
            Class<?> oclass = Class.forName("java.awt.Desktop");
            Object object = oclass.getMethod("getDesktop").invoke(null);
            oclass.getMethod("browse", URI.class).invoke(object, link);
        } catch (Throwable throwable) {
            LOGGER.error("Couldn't open link", throwable);
        }
    }

    public static boolean isControlDown() {
        return Minecraft.IS_MAC ? Keyboard.isKeyDown(219) || Keyboard.isKeyDown(220) : Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157);
    }

    public static boolean isShiftDown() {
        return Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54);
    }

    public static boolean isAltDown() {
        return Keyboard.isKeyDown(56) || Keyboard.isKeyDown(184);
    }

    public static boolean isCut(int key) {
        return key == 45 && isControlDown() && !isShiftDown() && !isAltDown();
    }

    public static boolean isPaste(int key) {
        return key == 47 && isControlDown() && !isShiftDown() && !isAltDown();
    }

    public static boolean isCopy(int key) {
        return key == 46 && isControlDown() && !isShiftDown() && !isAltDown();
    }

    public static boolean isSelectAll(int key) {
        return key == 30 && isControlDown() && !isShiftDown() && !isAltDown();
    }

    public void resize(Minecraft minecraft, int width, int height) {
        this.init(minecraft, width, height);
    }
}
