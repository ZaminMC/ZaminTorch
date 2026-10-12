package net.minecraft.client.gui.chat;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatMessage;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.TextRenderUtils;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChatGui extends GuiElement {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Minecraft minecraft;
    /**
     * Recent messages that show up on screen even if the chat is not in focus.
     */
    private final List<String> recentMessages = Lists.newArrayList();
    private final List<ChatMessage> messages = Lists.newArrayList();
    /**
     * A list of chat messages, trimmed to fit on the screen. Note: this means long messages
     * will have multiple entries; one for each line.
     */
    private final List<ChatMessage> trimmedMessages = Lists.newArrayList();
    private int scroll;
    private boolean hasNewMessagesSinceScroll;

    public ChatGui(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void render(int ticks) {
        if (this.minecraft.options.chatVisibility != PlayerEntity.ChatVisibility.HIDDEN) {
            int i = this.getVisibleLineCount();
            boolean flag = false;
            int j = 0;
            int k = this.trimmedMessages.size();
            float f = this.minecraft.options.chatOpacity * 0.9F + 0.1F;
            if (k > 0) {
                if (this.isChatFocused()) {
                    flag = true;
                }

                float f1 = this.getChatScale();
                int l = MathHelper.ceil(this.getWidth() / f1);
                GlStateManager.pushMatrix();
                GlStateManager.translatef(2.0F, 20.0F, 0.0F);
                GlStateManager.scalef(f1, f1, 1.0F);

                for (int i1 = 0; i1 + this.scroll < this.trimmedMessages.size() && i1 < i; i1++) {
                    ChatMessage chatmessage = this.trimmedMessages.get(i1 + this.scroll);
                    if (chatmessage != null) {
                        int j1 = ticks - chatmessage.getTimeOfCreation();
                        if (j1 < 200 || flag) {
                            double d0 = j1 / 200.0;
                            d0 = 1.0 - d0;
                            d0 *= 10.0;
                            d0 = MathHelper.clamp(d0, 0.0, 1.0);
                            d0 *= d0;
                            int l1 = (int)(255.0 * d0);
                            if (flag) {
                                l1 = 255;
                            }

                            l1 = (int)(l1 * f);
                            j++;
                            if (l1 > 3) {
                                int i2 = 0;
                                int j2 = -i1 * 9;
                                fill(i2, j2 - 9, i2 + l + 4, j2, l1 / 2 << 24);
                                String s = chatmessage.getText().getFormattedString();
                                GlStateManager.enableBlend();
                                this.minecraft.textRenderer.drawWithShadow(s, i2, j2 - 8, 16777215 + (l1 << 24));
                                GlStateManager.disableAlphaTest();
                                GlStateManager.disableBlend();
                            }
                        }
                    }
                }

                if (flag) {
                    int k2 = this.minecraft.textRenderer.fontHeight;
                    GlStateManager.translatef(-3.0F, 0.0F, 0.0F);
                    int l2 = k * k2 + k;
                    int i3 = j * k2 + j;
                    int j3 = this.scroll * i3 / k;
                    int k1 = i3 * i3 / l2;
                    if (l2 != i3) {
                        int k3 = j3 > 0 ? 170 : 96;
                        int l3 = this.hasNewMessagesSinceScroll ? 13382451 : 3355562;
                        fill(0, -j3, 2, -j3 - k1, l3 + (k3 << 24));
                        fill(2, -j3, 1, -j3 - k1, 13421772 + (k3 << 24));
                    }
                }

                GlStateManager.popMatrix();
            }
        }
    }

    public void clear() {
        this.trimmedMessages.clear();
        this.messages.clear();
        this.recentMessages.clear();
    }

    public void addMessage(Text message) {
        this.addMessage(message, 0);
    }

    public void addMessage(Text message, int id) {
        this.addMessage(message, id, this.minecraft.gui.getTicks(), false);
        LOGGER.info("[CHAT] " + message.getString());
    }

    private void addMessage(Text message, int id, int time, boolean deleted) {
        if (id != 0) {
            this.removeMessage(id);
        }

        int i = MathHelper.floor(this.getWidth() / this.getChatScale());
        List<Text> list = TextRenderUtils.wrapText(message, i, this.minecraft.textRenderer, false, false);
        boolean flag = this.isChatFocused();

        for (Text text : list) {
            if (flag && this.scroll > 0) {
                this.hasNewMessagesSinceScroll = true;
                this.scroll(1);
            }

            this.trimmedMessages.add(0, new ChatMessage(time, text, id));
        }

        while (this.trimmedMessages.size() > 100) {
            this.trimmedMessages.remove(this.trimmedMessages.size() - 1);
        }

        if (!deleted) {
            this.messages.add(0, new ChatMessage(time, message, id));

            while (this.messages.size() > 100) {
                this.messages.remove(this.messages.size() - 1);
            }
        }
    }

    public void reset() {
        this.trimmedMessages.clear();
        this.resetScroll();

        for (int i = this.messages.size() - 1; i >= 0; i--) {
            ChatMessage chatmessage = this.messages.get(i);
            this.addMessage(chatmessage.getText(), chatmessage.getId(), chatmessage.getTimeOfCreation(), true);
        }
    }

    public List<String> getRecentMessages() {
        return this.recentMessages;
    }

    public void addRecentMessage(String message) {
        if (this.recentMessages.isEmpty() || !this.recentMessages.get(this.recentMessages.size() - 1).equals(message)) {
            this.recentMessages.add(message);
        }
    }

    public void resetScroll() {
        this.scroll = 0;
        this.hasNewMessagesSinceScroll = false;
    }

    public void scroll(int lines) {
        this.scroll += lines;
        int i = this.trimmedMessages.size();
        if (this.scroll > i - this.getVisibleLineCount()) {
            this.scroll = i - this.getVisibleLineCount();
        }

        if (this.scroll <= 0) {
            this.scroll = 0;
            this.hasNewMessagesSinceScroll = false;
        }
    }

    public Text getMessageAt(int x, int y) {
        if (!this.isChatFocused()) {
            return null;
        }

        Window window = new Window(this.minecraft);
        int i = window.getScale();
        float f = this.getChatScale();
        int j = x / i - 3;
        int k = y / i - 27;
        j = MathHelper.floor(j / f);
        k = MathHelper.floor(k / f);
        if (j >= 0 && k >= 0) {
            int l = Math.min(this.getVisibleLineCount(), this.trimmedMessages.size());
            if (j <= MathHelper.floor(this.getWidth() / this.getChatScale()) && k < this.minecraft.textRenderer.fontHeight * l + l) {
                int i1 = k / this.minecraft.textRenderer.fontHeight + this.scroll;
                if (i1 >= 0 && i1 < this.trimmedMessages.size()) {
                    ChatMessage chatmessage = this.trimmedMessages.get(i1);
                    int j1 = 0;

                    for (Text text : chatmessage.getText()) {
                        if (text instanceof LiteralText) {
                            j1 += this.minecraft.textRenderer.getWidth(TextRenderUtils.prepareText(((LiteralText)text).getRawString(), false));
                            if (j1 > j) {
                                return text;
                            }
                        }
                    }
                }

                return null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public boolean isChatFocused() {
        return this.minecraft.screen instanceof ChatScreen;
    }

    public void removeMessage(int id) {
        Iterator<ChatMessage> iterator = this.trimmedMessages.iterator();

        while (iterator.hasNext()) {
            ChatMessage chatmessage = iterator.next();
            if (chatmessage.getId() == id) {
                iterator.remove();
            }
        }

        iterator = this.messages.iterator();

        while (iterator.hasNext()) {
            ChatMessage chatmessage1 = iterator.next();
            if (chatmessage1.getId() == id) {
                iterator.remove();
                break;
            }
        }
    }

    public int getWidth() {
        return getWidth(this.minecraft.options.chatWidth);
    }

    public int getHeight() {
        return getHeight(this.isChatFocused() ? this.minecraft.options.focusedChatHeight : this.minecraft.options.unfocusedChatHeight);
    }

    public float getChatScale() {
        return this.minecraft.options.chatScale;
    }

    public static int getWidth(float chatWidth) {
        int i = 320;
        int j = 40;
        return MathHelper.floor(chatWidth * (i - j) + j);
    }

    public static int getHeight(float chatHeight) {
        int i = 180;
        int j = 20;
        return MathHelper.floor(chatHeight * (i - j) + j);
    }

    public int getVisibleLineCount() {
        return this.getHeight() / 9;
    }
}
