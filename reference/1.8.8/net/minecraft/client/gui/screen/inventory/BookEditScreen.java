package net.minecraft.client.gui.screen.inventory;

import com.google.common.collect.Lists;
import com.google.gson.JsonParseException;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.TextRenderUtils;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.WrittenBookItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.resource.Identifier;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

public class BookEditScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Identifier BACKGROUND_LOCATION = new Identifier("textures/gui/book.png");
    private final PlayerEntity reader;
    private final ItemStack book;
    private final boolean unsigned;
    private boolean dirty;
    private boolean signing;
    private int tickCounter;
    private int widthOffset = 192;
    private int horizontalMargin = 192;
    private int verticalMargin = 1;
    private int currentPage;
    private NbtList pagesNbt;
    private String title = "";
    private List<Text> lines;
    private int loadedPage = -1;
    private BookEditScreen.BookButton nextPageButton;
    private BookEditScreen.BookButton previousPageButton;
    private ButtonWidget doneButton;
    private ButtonWidget signButton;
    private ButtonWidget finalizeButton;
    private ButtonWidget cancelButton;

    public BookEditScreen(PlayerEntity reader, ItemStack book, boolean unsigned) {
        this.reader = reader;
        this.book = book;
        this.unsigned = unsigned;
        if (book.hasNbt()) {
            NbtCompound nbtcompound = book.getNbt();
            this.pagesNbt = nbtcompound.getList("pages", 8);
            if (this.pagesNbt != null) {
                this.pagesNbt = (NbtList)this.pagesNbt.copy();
                this.verticalMargin = this.pagesNbt.size();
                if (this.verticalMargin < 1) {
                    this.verticalMargin = 1;
                }
            }
        }

        if (this.pagesNbt == null && unsigned) {
            this.pagesNbt = new NbtList();
            this.pagesNbt.addElement(new NbtString(""));
            this.verticalMargin = 1;
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.tickCounter++;
    }

    @Override
    public void init() {
        this.buttons.clear();
        Keyboard.enableRepeatEvents(true);
        if (this.unsigned) {
            this.buttons.add(this.signButton = new ButtonWidget(3, this.width / 2 - 100, 4 + this.horizontalMargin, 98, 20, I18n.translate("book.signButton")));
            this.buttons.add(this.doneButton = new ButtonWidget(0, this.width / 2 + 2, 4 + this.horizontalMargin, 98, 20, I18n.translate("gui.done")));
            this.buttons
                .add(this.finalizeButton = new ButtonWidget(5, this.width / 2 - 100, 4 + this.horizontalMargin, 98, 20, I18n.translate("book.finalizeButton")));
            this.buttons.add(this.cancelButton = new ButtonWidget(4, this.width / 2 + 2, 4 + this.horizontalMargin, 98, 20, I18n.translate("gui.cancel")));
        } else {
            this.buttons.add(this.doneButton = new ButtonWidget(0, this.width / 2 - 100, 4 + this.horizontalMargin, 200, 20, I18n.translate("gui.done")));
        }

        int i = (this.width - this.widthOffset) / 2;
        int j = 2;
        this.buttons.add(this.nextPageButton = new BookEditScreen.BookButton(1, i + 120, j + 154, true));
        this.buttons.add(this.previousPageButton = new BookEditScreen.BookButton(2, i + 38, j + 154, false));
        this.updateButtons();
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
    }

    private void updateButtons() {
        this.nextPageButton.visible = !this.signing && (this.currentPage < this.verticalMargin - 1 || this.unsigned);
        this.previousPageButton.visible = !this.signing && this.currentPage > 0;
        this.doneButton.visible = !this.unsigned || !this.signing;
        if (this.unsigned) {
            this.signButton.visible = !this.signing;
            this.cancelButton.visible = this.signing;
            this.finalizeButton.visible = this.signing;
            this.finalizeButton.active = this.title.trim().length() > 0;
        }
    }

    private void finalizeBook(boolean signBook) {
        if (this.unsigned && this.dirty) {
            if (this.pagesNbt != null) {
                while (this.pagesNbt.size() > 1) {
                    String s = this.pagesNbt.getString(this.pagesNbt.size() - 1);
                    if (s.length() != 0) {
                        break;
                    }

                    this.pagesNbt.removeElement(this.pagesNbt.size() - 1);
                }

                if (this.book.hasNbt()) {
                    NbtCompound nbtcompound = this.book.getNbt();
                    nbtcompound.put("pages", this.pagesNbt);
                } else {
                    this.book.addToNbt("pages", this.pagesNbt);
                }

                String s2 = "MC|BEdit";
                if (signBook) {
                    s2 = "MC|BSign";
                    this.book.addToNbt("author", new NbtString(this.reader.getName()));
                    this.book.addToNbt("title", new NbtString(this.title.trim()));

                    for (int i = 0; i < this.pagesNbt.size(); i++) {
                        String s1 = this.pagesNbt.getString(i);
                        Text text = new LiteralText(s1);
                        s1 = Text.Serializer.toJson(text);
                        this.pagesNbt.setElement(i, new NbtString(s1));
                    }

                    this.book.setItem(Items.WRITTEN_BOOK);
                }

                PacketByteBuf packetbytebuf = new PacketByteBuf(Unpooled.buffer());
                packetbytebuf.writeItem(this.book);
                this.minecraft.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(s2, packetbytebuf));
            }
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 0) {
                this.minecraft.openScreen(null);
                this.finalizeBook(false);
            } else if (button.id == 3 && this.unsigned) {
                this.signing = true;
            } else if (button.id == 1) {
                if (this.currentPage < this.verticalMargin - 1) {
                    this.currentPage++;
                } else if (this.unsigned) {
                    this.appendNewPage();
                    if (this.currentPage < this.verticalMargin - 1) {
                        this.currentPage++;
                    }
                }
            } else if (button.id == 2) {
                if (this.currentPage > 0) {
                    this.currentPage--;
                }
            } else if (button.id == 5 && this.signing) {
                this.finalizeBook(true);
                this.minecraft.openScreen(null);
            } else if (button.id == 4 && this.signing) {
                this.signing = false;
            }

            this.updateButtons();
        }
    }

    private void appendNewPage() {
        if (this.pagesNbt != null && this.pagesNbt.size() < 50) {
            this.pagesNbt.addElement(new NbtString(""));
            this.verticalMargin++;
            this.dirty = true;
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
        super.keyPressed(chr, key);
        if (this.unsigned) {
            if (this.signing) {
                this.handleTitleKeyPresses(chr, key);
            } else {
                this.handleBookKeyPresses(chr, key);
            }
        }
    }

    private void handleBookKeyPresses(char chr, int key) {
        if (Screen.isPaste(key)) {
            this.handleClipboardPaste(Screen.getClipboard());
        } else {
            switch (key) {
                case 14:
                    String s = this.getCurrentPageContent();
                    if (s.length() > 0) {
                        this.setPageContent(s.substring(0, s.length() - 1));
                    }

                    return;
                case 28:
                case 156:
                    this.handleClipboardPaste("\n");
                    return;
                default:
                    if (SharedConstants.isValidChatChar(chr)) {
                        this.handleClipboardPaste(Character.toString(chr));
                    }
            }
        }
    }

    private void handleTitleKeyPresses(char chr, int key) {
        switch (key) {
            case 14:
                if (!this.title.isEmpty()) {
                    this.title = this.title.substring(0, this.title.length() - 1);
                    this.updateButtons();
                }

                return;
            case 28:
            case 156:
                if (!this.title.isEmpty()) {
                    this.finalizeBook(true);
                    this.minecraft.openScreen(null);
                }

                return;
            default:
                if (this.title.length() < 16 && SharedConstants.isValidChatChar(chr)) {
                    this.title = this.title + Character.toString(chr);
                    this.updateButtons();
                    this.dirty = true;
                }
        }
    }

    private String getCurrentPageContent() {
        return this.pagesNbt != null && this.currentPage >= 0 && this.currentPage < this.pagesNbt.size() ? this.pagesNbt.getString(this.currentPage) : "";
    }

    private void setPageContent(String pageContent) {
        if (this.pagesNbt != null && this.currentPage >= 0 && this.currentPage < this.pagesNbt.size()) {
            this.pagesNbt.setElement(this.currentPage, new NbtString(pageContent));
            this.dirty = true;
        }
    }

    private void handleClipboardPaste(String content) {
        String s = this.getCurrentPageContent();
        String s1 = s + content;
        int i = this.textRenderer.splitAndGetHeight(s1 + "" + Formatting.BLACK + "_", 118);
        if (i <= 128 && s1.length() < 256) {
            this.setPageContent(s1);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(BACKGROUND_LOCATION);
        int i = (this.width - this.widthOffset) / 2;
        int j = 2;
        this.drawTexture(i, j, 0, 0, this.widthOffset, this.horizontalMargin);
        if (this.signing) {
            String s = this.title;
            if (this.unsigned) {
                if (this.tickCounter / 6 % 2 == 0) {
                    s = s + "" + Formatting.BLACK + "_";
                } else {
                    s = s + "" + Formatting.GRAY + "_";
                }
            }

            String s1 = I18n.translate("book.editTitle");
            int k = this.textRenderer.getWidth(s1);
            this.textRenderer.draw(s1, i + 36 + (116 - k) / 2, j + 16 + 16, 0);
            int l = this.textRenderer.getWidth(s);
            this.textRenderer.draw(s, i + 36 + (116 - l) / 2, j + 48, 0);
            String s2 = I18n.translate("book.byAuthor", this.reader.getName());
            int i1 = this.textRenderer.getWidth(s2);
            this.textRenderer.draw(Formatting.DARK_GRAY + s2, i + 36 + (116 - i1) / 2, j + 48 + 10, 0);
            String s3 = I18n.translate("book.finalizeWarning");
            this.textRenderer.splitAndDraw(s3, i + 36, j + 80, 116, 0);
        } else {
            String s4 = I18n.translate("book.pageIndicator", this.currentPage + 1, this.verticalMargin);
            String s5 = "";
            if (this.pagesNbt != null && this.currentPage >= 0 && this.currentPage < this.pagesNbt.size()) {
                s5 = this.pagesNbt.getString(this.currentPage);
            }

            if (this.unsigned) {
                if (this.textRenderer.isBidirectional()) {
                    s5 = s5 + "_";
                } else if (this.tickCounter / 6 % 2 == 0) {
                    s5 = s5 + "" + Formatting.BLACK + "_";
                } else {
                    s5 = s5 + "" + Formatting.GRAY + "_";
                }
            } else if (this.loadedPage != this.currentPage) {
                if (WrittenBookItem.isValid(this.book.getNbt())) {
                    try {
                        Text text = Text.Serializer.fromJson(s5);
                        this.lines = text != null ? TextRenderUtils.wrapText(text, 116, this.textRenderer, true, true) : null;
                    } catch (JsonParseException jsonparseexception) {
                        this.lines = null;
                    }
                } else {
                    LiteralText literaltext = new LiteralText(Formatting.DARK_RED.toString() + "* Invalid book tag *");
                    this.lines = Lists.newArrayList(literaltext);
                }

                this.loadedPage = this.currentPage;
            }

            int j1 = this.textRenderer.getWidth(s4);
            this.textRenderer.draw(s4, i - j1 + this.widthOffset - 44, j + 16, 0);
            if (this.lines == null) {
                this.textRenderer.splitAndDraw(s5, i + 36, j + 16 + 16, 116, 0);
            } else {
                int k1 = Math.min(128 / this.textRenderer.fontHeight, this.lines.size());

                for (int l1 = 0; l1 < k1; l1++) {
                    Text text2 = this.lines.get(l1);
                    this.textRenderer.draw(text2.getString(), i + 36, j + 16 + 16 + l1 * this.textRenderer.fontHeight, 0);
                }

                Text text1 = this.getHoveredText(mouseX, mouseY);
                if (text1 != null) {
                    this.renderTextHoverEffect(text1, mouseX, mouseY);
                }
            }
        }

        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            Text text = this.getHoveredText(mouseX, mouseY);
            if (this.handleClickEvent(text)) {
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean handleClickEvent(Text text) {
        ClickEvent clickevent = text == null ? null : text.getStyle().getClickEvent();
        if (clickevent == null) {
            return false;
        }

        if (clickevent.getAction() == ClickEvent.Action.CHANGE_PAGE) {
            String s = clickevent.getValue();

            try {
                int i = Integer.parseInt(s) - 1;
                if (i >= 0 && i < this.verticalMargin && i != this.currentPage) {
                    this.currentPage = i;
                    this.updateButtons();
                    return true;
                }
            } catch (Throwable throwable) {
            }

            return false;
        } else {
            boolean flag = super.handleClickEvent(text);
            if (flag && clickevent.getAction() == ClickEvent.Action.RUN_COMMAND) {
                this.minecraft.openScreen(null);
            }

            return flag;
        }
    }

    public Text getHoveredText(int mouseX, int mouseY) {
        if (this.lines == null) {
            return null;
        }

        int i = mouseX - (this.width - this.widthOffset) / 2 - 36;
        int j = mouseY - 2 - 16 - 16;
        if (i >= 0 && j >= 0) {
            int k = Math.min(128 / this.textRenderer.fontHeight, this.lines.size());
            if (i <= 116 && j < this.minecraft.textRenderer.fontHeight * k + k) {
                int l = j / this.minecraft.textRenderer.fontHeight;
                if (l >= 0 && l < this.lines.size()) {
                    Text text = this.lines.get(l);
                    int i1 = 0;

                    for (Text text1 : text) {
                        if (text1 instanceof LiteralText) {
                            i1 += this.minecraft.textRenderer.getWidth(((LiteralText)text1).getRawString());
                            if (i1 > i) {
                                return text1;
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

    static class BookButton extends ButtonWidget {
        private final boolean clickable;

        public BookButton(int id, int x, int y, boolean clickable) {
            super(id, x, y, 23, 13, "");
            this.clickable = clickable;
        }

        @Override
        public void render(Minecraft minecraft, int mouseX, int mouseY) {
            if (this.visible) {
                boolean flag = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                minecraft.getTextureManager().bind(BookEditScreen.BACKGROUND_LOCATION);
                int i = 0;
                int j = 192;
                if (flag) {
                    i += 23;
                }

                if (!this.clickable) {
                    j += 13;
                }

                this.drawTexture(this.x, this.y, i, j, 23, 13);
            }
        }
    }
}
