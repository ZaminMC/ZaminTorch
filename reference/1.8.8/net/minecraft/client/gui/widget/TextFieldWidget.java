package net.minecraft.client.gui.widget;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.OverworldGeneratorOptionsWidget;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.util.math.MathHelper;

public class TextFieldWidget extends GuiElement {
    private final int id;
    private final TextRenderer textRenderer;
    public int x;
    public int y;
    private final int width;
    private final int height;
    private String text = "";
    private int maxLength = 32;
    private int focusedTicks;
    private boolean hasBorder = true;
    private boolean focusUnlocked = true;
    private boolean focused;
    private boolean editable = true;
    private int firstCharacterIndex;
    private int selectionStart;
    private int selectionEnd;
    private int editableColor = 14737632;
    private int uneditableColor = 7368816;
    private boolean visible = true;
    private OverworldGeneratorOptionsWidget.Controller controller;
    private Predicate<String> filter = Predicates.alwaysTrue();

    public TextFieldWidget(int id, TextRenderer textRenderer, int x, int y, int width, int height) {
        this.id = id;
        this.textRenderer = textRenderer;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setController(OverworldGeneratorOptionsWidget.Controller controller) {
        this.controller = controller;
    }

    public void tick() {
        this.focusedTicks++;
    }

    public void setText(String text) {
        if (this.filter.apply(text)) {
            if (text.length() > this.maxLength) {
                this.text = text.substring(0, this.maxLength);
            } else {
                this.text = text;
            }

            this.setCursorToEnd();
        }
    }

    public String getText() {
        return this.text;
    }

    public String getSelectedText() {
        int i = this.selectionStart < this.selectionEnd ? this.selectionStart : this.selectionEnd;
        int j = this.selectionStart < this.selectionEnd ? this.selectionEnd : this.selectionStart;
        return this.text.substring(i, j);
    }

    public void setFilter(Predicate<String> validator) {
        this.filter = validator;
    }

    public void write(String text) {
        String s = "";
        String s1 = SharedConstants.stripInvalidChars(text);
        int i = this.selectionStart < this.selectionEnd ? this.selectionStart : this.selectionEnd;
        int j = this.selectionStart < this.selectionEnd ? this.selectionEnd : this.selectionStart;
        int k = this.maxLength - this.text.length() - (i - j);
        int l = 0;
        if (this.text.length() > 0) {
            s = s + this.text.substring(0, i);
        }

        if (k < s1.length()) {
            s = s + s1.substring(0, k);
            l = k;
        } else {
            s = s + s1;
            l = s1.length();
        }

        if (this.text.length() > 0 && j < this.text.length()) {
            s = s + this.text.substring(j);
        }

        if (this.filter.apply(s)) {
            this.text = s;
            this.moveCursor(i - this.selectionEnd + l);
            if (this.controller != null) {
                this.controller.setValue(this.id, this.text);
            }
        }
    }

    public void eraseWords(int wordOffset) {
        if (this.text.length() != 0) {
            if (this.selectionEnd != this.selectionStart) {
                this.write("");
            } else {
                this.eraseCharacters(this.getWordSkipPosition(wordOffset) - this.selectionStart);
            }
        }
    }

    public void eraseCharacters(int characterOffset) {
        if (this.text.length() != 0) {
            if (this.selectionEnd != this.selectionStart) {
                this.write("");
            } else {
                boolean flag = characterOffset < 0;
                int i = flag ? this.selectionStart + characterOffset : this.selectionStart;
                int j = flag ? this.selectionStart : this.selectionStart + characterOffset;
                String s = "";
                if (i >= 0) {
                    s = this.text.substring(0, i);
                }

                if (j < this.text.length()) {
                    s = s + this.text.substring(j);
                }

                if (this.filter.apply(s)) {
                    this.text = s;
                    if (flag) {
                        this.moveCursor(characterOffset);
                    }

                    if (this.controller != null) {
                        this.controller.setValue(this.id, this.text);
                    }
                }
            }
        }
    }

    public int getId() {
        return this.id;
    }

    public int getWordSkipPosition(int wordOffset) {
        return this.getWordSkipPosition(wordOffset, this.getCursor());
    }

    public int getWordSkipPosition(int wordOffset, int cursorPosition) {
        return this.getWordSkipPosition(wordOffset, cursorPosition, true);
    }

    public int getWordSkipPosition(int wordOffset, int cursorPosition, boolean skipOverSpaces) {
        int i = cursorPosition;
        boolean flag = wordOffset < 0;
        int j = Math.abs(wordOffset);

        for (int k = 0; k < j; k++) {
            if (!flag) {
                int l = this.text.length();
                i = this.text.indexOf(32, i);
                if (i == -1) {
                    i = l;
                } else {
                    while (skipOverSpaces && i < l && this.text.charAt(i) == ' ') {
                        i++;
                    }
                }
            } else {
                while (skipOverSpaces && i > 0 && this.text.charAt(i - 1) == ' ') {
                    i--;
                }

                while (i > 0 && this.text.charAt(i - 1) != ' ') {
                    i--;
                }
            }
        }

        return i;
    }

    public void moveCursor(int offset) {
        this.setCursor(this.selectionEnd + offset);
    }

    public void setCursor(int cursor) {
        this.selectionStart = cursor;
        int i = this.text.length();
        this.selectionStart = MathHelper.clamp(this.selectionStart, 0, i);
        this.setSelectionEnd(this.selectionStart);
    }

    public void setCursorToStart() {
        this.setCursor(0);
    }

    public void setCursorToEnd() {
        this.setCursor(this.text.length());
    }

    public boolean keyPressed(char chr, int code) {
        if (!this.focused) {
            return false;
        }

        if (Screen.isSelectAll(code)) {
            this.setCursorToEnd();
            this.setSelectionEnd(0);
            return true;
        }

        if (Screen.isCopy(code)) {
            Screen.setClipboard(this.getSelectedText());
            return true;
        }

        if (Screen.isPaste(code)) {
            if (this.editable) {
                this.write(Screen.getClipboard());
            }

            return true;
        } else if (Screen.isCut(code)) {
            Screen.setClipboard(this.getSelectedText());
            if (this.editable) {
                this.write("");
            }

            return true;
        } else {
            switch (code) {
                case 14:
                    if (Screen.isControlDown()) {
                        if (this.editable) {
                            this.eraseWords(-1);
                        }
                    } else if (this.editable) {
                        this.eraseCharacters(-1);
                    }

                    return true;
                case 199:
                    if (Screen.isShiftDown()) {
                        this.setSelectionEnd(0);
                    } else {
                        this.setCursorToStart();
                    }

                    return true;
                case 203:
                    if (Screen.isShiftDown()) {
                        if (Screen.isControlDown()) {
                            this.setSelectionEnd(this.getWordSkipPosition(-1, this.getSelectionEnd()));
                        } else {
                            this.setSelectionEnd(this.getSelectionEnd() - 1);
                        }
                    } else if (Screen.isControlDown()) {
                        this.setCursor(this.getWordSkipPosition(-1));
                    } else {
                        this.moveCursor(-1);
                    }

                    return true;
                case 205:
                    if (Screen.isShiftDown()) {
                        if (Screen.isControlDown()) {
                            this.setSelectionEnd(this.getWordSkipPosition(1, this.getSelectionEnd()));
                        } else {
                            this.setSelectionEnd(this.getSelectionEnd() + 1);
                        }
                    } else if (Screen.isControlDown()) {
                        this.setCursor(this.getWordSkipPosition(1));
                    } else {
                        this.moveCursor(1);
                    }

                    return true;
                case 207:
                    if (Screen.isShiftDown()) {
                        this.setSelectionEnd(this.text.length());
                    } else {
                        this.setCursorToEnd();
                    }

                    return true;
                case 211:
                    if (Screen.isControlDown()) {
                        if (this.editable) {
                            this.eraseWords(1);
                        }
                    } else if (this.editable) {
                        this.eraseCharacters(1);
                    }

                    return true;
                default:
                    if (SharedConstants.isValidChatChar(chr)) {
                        if (this.editable) {
                            this.write(Character.toString(chr));
                        }

                        return true;
                    } else {
                        return false;
                    }
            }
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        boolean flag = mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height;
        if (this.focusUnlocked) {
            this.setFocused(flag);
        }

        if (this.focused && flag && button == 0) {
            int i = mouseX - this.x;
            if (this.hasBorder) {
                i -= 4;
            }

            String s = this.textRenderer.trim(this.text.substring(this.firstCharacterIndex), this.getInnerWidth());
            this.setCursor(this.textRenderer.trim(s, i).length() + this.firstCharacterIndex);
        }
    }

    public void render() {
        if (this.isVisible()) {
            if (this.hasBorder()) {
                fill(this.x - 1, this.y - 1, this.x + this.width + 1, this.y + this.height + 1, -6250336);
                fill(this.x, this.y, this.x + this.width, this.y + this.height, -16777216);
            }

            int i = this.editable ? this.editableColor : this.uneditableColor;
            int j = this.selectionStart - this.firstCharacterIndex;
            int k = this.selectionEnd - this.firstCharacterIndex;
            String s = this.textRenderer.trim(this.text.substring(this.firstCharacterIndex), this.getInnerWidth());
            boolean flag = j >= 0 && j <= s.length();
            boolean flag1 = this.focused && this.focusedTicks / 6 % 2 == 0 && flag;
            int l = this.hasBorder ? this.x + 4 : this.x;
            int i1 = this.hasBorder ? this.y + (this.height - 8) / 2 : this.y;
            int j1 = l;
            if (k > s.length()) {
                k = s.length();
            }

            if (s.length() > 0) {
                String s1 = flag ? s.substring(0, j) : s;
                j1 = this.textRenderer.drawWithShadow(s1, j1, i1, i);
            }

            boolean flag2 = this.selectionStart < this.text.length() || this.text.length() >= this.getMaxLength();
            int k1 = j1;
            if (!flag) {
                k1 = j > 0 ? l + this.width : l;
            } else if (flag2) {
                k1--;
                j1--;
            }

            if (s.length() > 0 && flag && j < s.length()) {
                j1 = this.textRenderer.drawWithShadow(s.substring(j), j1, i1, i);
            }

            if (flag1) {
                if (flag2) {
                    GuiElement.fill(k1, i1 - 1, k1 + 1, i1 + 1 + this.textRenderer.fontHeight, -3092272);
                } else {
                    this.textRenderer.drawWithShadow("_", k1, i1, i);
                }
            }

            if (k != j) {
                int l1 = l + this.textRenderer.getWidth(s.substring(0, k));
                this.renderSelection(k1, i1 - 1, l1 - 1, i1 + 1 + this.textRenderer.fontHeight);
            }
        }
    }

    private void renderSelection(int x1, int y1, int x2, int y2) {
        if (x1 < x2) {
            int i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            int j = y1;
            y1 = y2;
            y2 = j;
        }

        if (x2 > this.x + this.width) {
            x2 = this.x + this.width;
        }

        if (x1 > this.x + this.width) {
            x1 = this.x + this.width;
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        GlStateManager.color4f(0.0F, 0.0F, 255.0F, 255.0F);
        GlStateManager.disableTexture();
        GlStateManager.enableColorLogicOp();
        GlStateManager.logicOp(5387);
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION);
        bufferbuilder.vertex(x1, y2, 0.0).nextVertex();
        bufferbuilder.vertex(x2, y2, 0.0).nextVertex();
        bufferbuilder.vertex(x2, y1, 0.0).nextVertex();
        bufferbuilder.vertex(x1, y1, 0.0).nextVertex();
        tesselator.end();
        GlStateManager.disableColorLogicOp();
        GlStateManager.enableTexture();
    }

    public void setMaxLength(int maximumLength) {
        this.maxLength = maximumLength;
        if (this.text.length() > maximumLength) {
            this.text = this.text.substring(0, maximumLength);
        }
    }

    public int getMaxLength() {
        return this.maxLength;
    }

    public int getCursor() {
        return this.selectionStart;
    }

    public boolean hasBorder() {
        return this.hasBorder;
    }

    public void setHasBorder(boolean hasBorder) {
        this.hasBorder = hasBorder;
    }

    public void setEditableColor(int color) {
        this.editableColor = color;
    }

    public void setUneditableColor(int color) {
        this.uneditableColor = color;
    }

    public void setFocused(boolean focused) {
        if (focused && !this.focused) {
            this.focusedTicks = 0;
        }

        this.focused = focused;
    }

    public boolean isFocused() {
        return this.focused;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public int getSelectionEnd() {
        return this.selectionEnd;
    }

    public int getInnerWidth() {
        return this.hasBorder() ? this.width - 8 : this.width;
    }

    public void setSelectionEnd(int index) {
        int i = this.text.length();
        if (index > i) {
            index = i;
        }

        if (index < 0) {
            index = 0;
        }

        this.selectionEnd = index;
        if (this.textRenderer != null) {
            if (this.firstCharacterIndex > i) {
                this.firstCharacterIndex = i;
            }

            int j = this.getInnerWidth();
            String s = this.textRenderer.trim(this.text.substring(this.firstCharacterIndex), j);
            int k = s.length() + this.firstCharacterIndex;
            if (index == this.firstCharacterIndex) {
                this.firstCharacterIndex = this.firstCharacterIndex - this.textRenderer.trim(this.text, j, true).length();
            }

            if (index > k) {
                this.firstCharacterIndex += index - k;
            } else if (index <= this.firstCharacterIndex) {
                this.firstCharacterIndex = this.firstCharacterIndex - (this.firstCharacterIndex - index);
            }

            this.firstCharacterIndex = MathHelper.clamp(this.firstCharacterIndex, 0, i);
        }
    }

    public void setFocusUnlocked(boolean focusUnlocked) {
        this.focusUnlocked = focusUnlocked;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
