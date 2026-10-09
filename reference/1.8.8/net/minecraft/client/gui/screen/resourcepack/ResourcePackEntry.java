package net.minecraft.client.gui.screen.resourcepack;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ConfirmationListener;
import net.minecraft.client.gui.screen.ResourcePacksScreen;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public abstract class ResourcePackEntry implements EntryListWidget.Entry {
    private static final Identifier RESOURCE_PACKS_LOCATION = new Identifier("textures/gui/resource_packs.png");
    private static final Text INCOMPATIBLE_GENERIC = new TranslatableText("resourcePack.incompatible");
    private static final Text INCOMPATIBLE_TOO_OLD = new TranslatableText("resourcePack.incompatible.old");
    private static final Text INCOMPATIBLE_TOO_NEW = new TranslatableText("resourcePack.incompatible.new");
    protected final Minecraft minecraft;
    protected final ResourcePacksScreen parent;

    public ResourcePackEntry(ResourcePacksScreen parent) {
        this.parent = parent;
        this.minecraft = Minecraft.getInstance();
    }

    @Override
    public void render(int index, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
        int i = this.getFormat();
        if (i != 1) {
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GuiElement.fill(x - 1, y - 1, x + width - 9, y + height + 1, -8978432);
        }

        this.bindIcon();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GuiElement.drawTexture(x, y, 0.0F, 0.0F, 32, 32, 32.0F, 32.0F);
        String s = this.getName();
        String s1 = this.getDescription();
        if ((this.minecraft.options.touchscreen || hovered) && this.canMove()) {
            this.minecraft.getTextureManager().bind(RESOURCE_PACKS_LOCATION);
            GuiElement.fill(x, y, x + 32, y + 32, -1601138544);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            int j = mouseX - x;
            int k = mouseY - y;
            if (i < 1) {
                s = INCOMPATIBLE_GENERIC.getFormattedString();
                s1 = INCOMPATIBLE_TOO_OLD.getFormattedString();
            } else if (i > 1) {
                s = INCOMPATIBLE_GENERIC.getFormattedString();
                s1 = INCOMPATIBLE_TOO_NEW.getFormattedString();
            }

            if (this.canMoveRight()) {
                if (j < 32) {
                    GuiElement.drawTexture(x, y, 0.0F, 32.0F, 32, 32, 256.0F, 256.0F);
                } else {
                    GuiElement.drawTexture(x, y, 0.0F, 0.0F, 32, 32, 256.0F, 256.0F);
                }
            } else {
                if (this.canMoveLeft()) {
                    if (j < 16) {
                        GuiElement.drawTexture(x, y, 32.0F, 32.0F, 32, 32, 256.0F, 256.0F);
                    } else {
                        GuiElement.drawTexture(x, y, 32.0F, 0.0F, 32, 32, 256.0F, 256.0F);
                    }
                }

                if (this.canMoveUp()) {
                    if (j < 32 && j > 16 && k < 16) {
                        GuiElement.drawTexture(x, y, 96.0F, 32.0F, 32, 32, 256.0F, 256.0F);
                    } else {
                        GuiElement.drawTexture(x, y, 96.0F, 0.0F, 32, 32, 256.0F, 256.0F);
                    }
                }

                if (this.canMoveDown()) {
                    if (j < 32 && j > 16 && k > 16) {
                        GuiElement.drawTexture(x, y, 64.0F, 32.0F, 32, 32, 256.0F, 256.0F);
                    } else {
                        GuiElement.drawTexture(x, y, 64.0F, 0.0F, 32, 32, 256.0F, 256.0F);
                    }
                }
            }
        }

        int i1 = this.minecraft.textRenderer.getWidth(s);
        if (i1 > 157) {
            s = this.minecraft.textRenderer.trim(s, 157 - this.minecraft.textRenderer.getWidth("...")) + "...";
        }

        this.minecraft.textRenderer.drawWithShadow(s, x + 32 + 2, y + 1, 16777215);
        List<String> list = this.minecraft.textRenderer.split(s1, 157);

        for (int l = 0; l < 2 && l < list.size(); l++) {
            this.minecraft.textRenderer.drawWithShadow(list.get(l), x + 32 + 2, y + 12 + 10 * l, 8421504);
        }
    }

    protected abstract int getFormat();

    protected abstract String getDescription();

    protected abstract String getName();

    protected abstract void bindIcon();

    protected boolean canMove() {
        return true;
    }

    protected boolean canMoveRight() {
        return !this.parent.isApplied(this);
    }

    protected boolean canMoveLeft() {
        return this.parent.isApplied(this);
    }

    protected boolean canMoveUp() {
        List<ResourcePackEntry> list = this.parent.getSiblingPacks(this);
        int i = list.indexOf(this);
        return i > 0 && list.get(i - 1).canMove();
    }

    protected boolean canMoveDown() {
        List<ResourcePackEntry> list = this.parent.getSiblingPacks(this);
        int i = list.indexOf(this);
        return i >= 0 && i < list.size() - 1 && list.get(i + 1).canMove();
    }

    @Override
    public boolean mouseClicked(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
        if (this.canMove() && entryMouseX <= 32) {
            if (this.canMoveRight()) {
                this.parent.setChanged();
                int j = this.getFormat();
                if (j != 1) {
                    String s1 = I18n.translate("resourcePack.incompatible.confirm.title");
                    String s = I18n.translate("resourcePack.incompatible.confirm." + (j > 1 ? "new" : "old"));
                    this.minecraft.openScreen(new ConfirmScreen(new ConfirmationListener() {
                        @Override
                        public void confirmResult(boolean result, int id) {
                            List<ResourcePackEntry> list2 = ResourcePackEntry.this.parent.getSiblingPacks(ResourcePackEntry.this);
                            ResourcePackEntry.this.minecraft.openScreen(ResourcePackEntry.this.parent);
                            if (result) {
                                list2.remove(ResourcePackEntry.this);
                                ResourcePackEntry.this.parent.getAppliedPacks().add(0, ResourcePackEntry.this);
                            }
                        }
                    }, s1, s, 0));
                } else {
                    this.parent.getSiblingPacks(this).remove(this);
                    this.parent.getAppliedPacks().add(0, this);
                }

                return true;
            }

            if (entryMouseX < 16 && this.canMoveLeft()) {
                this.parent.getSiblingPacks(this).remove(this);
                this.parent.getAvailablePacks().add(0, this);
                this.parent.setChanged();
                return true;
            }

            if (entryMouseX > 16 && entryMouseY < 16 && this.canMoveUp()) {
                List<ResourcePackEntry> list1 = this.parent.getSiblingPacks(this);
                int k = list1.indexOf(this);
                list1.remove(this);
                list1.add(k - 1, this);
                this.parent.setChanged();
                return true;
            }

            if (entryMouseX > 16 && entryMouseY > 16 && this.canMoveDown()) {
                List<ResourcePackEntry> list = this.parent.getSiblingPacks(this);
                int i = list.indexOf(this);
                list.remove(this);
                list.add(i + 1, this);
                this.parent.setChanged();
                return true;
            }
        }

        return false;
    }

    @Override
    public void renderOutOfBounds(int index, int x, int y) {
    }

    @Override
    public void mouseReleased(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
    }
}
