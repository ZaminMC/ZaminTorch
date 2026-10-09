package net.minecraft.client.gui.screen;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.packet.c2s.play.CommandSuggestionsC2SPacket;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.HitResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class ChatScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private String lastChatMessage = "";
    private int messageHistorySize = -1;
    private boolean reset;
    private boolean completed;
    private int currentMessageId;
    private List<String> messageHistory = Lists.newArrayList();
    protected TextFieldWidget chatField;
    private String initialChatText = "";

    public ChatScreen() {
    }

    public ChatScreen(String initialChatText) {
        this.initialChatText = initialChatText;
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        this.messageHistorySize = this.minecraft.gui.getChat().getRecentMessages().size();
        this.chatField = new TextFieldWidget(0, this.textRenderer, 4, this.height - 12, this.width - 4, 12);
        this.chatField.setMaxLength(100);
        this.chatField.setHasBorder(false);
        this.chatField.setFocused(true);
        this.chatField.setText(this.initialChatText);
        this.chatField.setFocusUnlocked(false);
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
        this.minecraft.gui.getChat().resetScroll();
    }

    @Override
    public void tick() {
        this.chatField.tick();
    }

    @Override
    protected void keyPressed(char chr, int key) {
        this.completed = false;
        if (key == 15) {
            this.reset();
        } else {
            this.reset = false;
        }

        if (key == 1) {
            this.minecraft.openScreen(null);
        } else if (key == 28 || key == 156) {
            String s = this.chatField.getText().trim();
            if (s.length() > 0) {
                this.sendChatMessage(s);
            }

            this.minecraft.openScreen(null);
        } else if (key == 200) {
            this.goThroughHistory(-1);
        } else if (key == 208) {
            this.goThroughHistory(1);
        } else if (key == 201) {
            this.minecraft.gui.getChat().scroll(this.minecraft.gui.getChat().getVisibleLineCount() - 1);
        } else if (key == 209) {
            this.minecraft.gui.getChat().scroll(-this.minecraft.gui.getChat().getVisibleLineCount() + 1);
        } else {
            this.chatField.keyPressed(chr, key);
        }
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        int i = Mouse.getEventDWheel();
        if (i != 0) {
            if (i > 1) {
                i = 1;
            }

            if (i < -1) {
                i = -1;
            }

            if (!isShiftDown()) {
                i *= 7;
            }

            this.minecraft.gui.getChat().scroll(i);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            Text text = this.minecraft.gui.getChat().getMessageAt(Mouse.getX(), Mouse.getY());
            if (this.handleClickEvent(text)) {
                return;
            }
        }

        this.chatField.mouseClicked(mouseX, mouseY, button);
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void insertText(String text, boolean replace) {
        if (replace) {
            this.chatField.setText(text);
        } else {
            this.chatField.write(text);
        }
    }

    public void reset() {
        if (this.reset) {
            this.chatField.eraseCharacters(this.chatField.getWordSkipPosition(-1, this.chatField.getCursor(), false) - this.chatField.getCursor());
            if (this.currentMessageId >= this.messageHistory.size()) {
                this.currentMessageId = 0;
            }
        } else {
            int i = this.chatField.getWordSkipPosition(-1, this.chatField.getCursor(), false);
            this.messageHistory.clear();
            this.currentMessageId = 0;
            String s = this.chatField.getText().substring(i).toLowerCase();
            String s1 = this.chatField.getText().substring(0, this.chatField.getCursor());
            this.goThroughHistory(s1, s);
            if (this.messageHistory.isEmpty()) {
                return;
            }

            this.reset = true;
            this.chatField.eraseCharacters(i - this.chatField.getCursor());
        }

        if (this.messageHistory.size() > 1) {
            StringBuilder stringbuilder = new StringBuilder();

            for (String s2 : this.messageHistory) {
                if (stringbuilder.length() > 0) {
                    stringbuilder.append(", ");
                }

                stringbuilder.append(s2);
            }

            this.minecraft.gui.getChat().addMessage(new LiteralText(stringbuilder.toString()), 1);
        }

        this.chatField.write(this.messageHistory.get(this.currentMessageId++));
    }

    private void goThroughHistory(String text, String cursor) {
        if (text.length() >= 1) {
            BlockPos blockpos = null;
            if (this.minecraft.crosshairTarget != null && this.minecraft.crosshairTarget.type == HitResult.Type.BLOCK) {
                blockpos = this.minecraft.crosshairTarget.getPos();
            }

            this.minecraft.player.networkHandler.sendPacket(new CommandSuggestionsC2SPacket(text, blockpos));
            this.completed = true;
        }
    }

    public void goThroughHistory(int numberOfNewMessages) {
        int i = this.messageHistorySize + numberOfNewMessages;
        int j = this.minecraft.gui.getChat().getRecentMessages().size();
        i = MathHelper.clamp(i, 0, j);
        if (i != this.messageHistorySize) {
            if (i == j) {
                this.messageHistorySize = j;
                this.chatField.setText(this.lastChatMessage);
            } else {
                if (this.messageHistorySize == j) {
                    this.lastChatMessage = this.chatField.getText();
                }

                this.chatField.setText(this.minecraft.gui.getChat().getRecentMessages().get(i));
                this.messageHistorySize = i;
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        fill(2, this.height - 14, this.width - 2, this.height - 2, Integer.MIN_VALUE);
        this.chatField.render();
        Text text = this.minecraft.gui.getChat().getMessageAt(Mouse.getX(), Mouse.getY());
        if (text != null && text.getStyle().getHoverEvent() != null) {
            this.renderTextHoverEffect(text, mouseX, mouseY);
        }

        super.render(mouseX, mouseY, tickDelta);
    }

    public void setMessageHistory(String[] messages) {
        if (this.completed) {
            this.reset = false;
            this.messageHistory.clear();

            for (String s : messages) {
                if (s.length() > 0) {
                    this.messageHistory.add(s);
                }
            }

            String s1 = this.chatField.getText().substring(this.chatField.getWordSkipPosition(-1, this.chatField.getCursor(), false));
            String s2 = StringUtils.getCommonPrefix(messages);
            if (s2.length() > 0 && !s1.equalsIgnoreCase(s2)) {
                this.chatField.eraseCharacters(this.chatField.getWordSkipPosition(-1, this.chatField.getCursor(), false) - this.chatField.getCursor());
                this.chatField.write(s2);
            } else if (this.messageHistory.size() > 0) {
                this.reset = true;
                this.reset();
            }
        }
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}
