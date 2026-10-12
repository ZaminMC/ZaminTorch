package net.minecraft.client.gui.screen.inventory.menu;

import io.netty.buffer.Unpooled;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.BeaconMenu;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BeaconScreen extends InventoryMenuScreen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Identifier MENU_LOCATION = new Identifier("textures/gui/container/beacon.png");
    private Inventory inventory;
    private BeaconScreen.DoneButtonWidget doneButton;
    private boolean acceptsPayment;

    public BeaconScreen(PlayerInventory playerInventory, Inventory inventory) {
        super(new BeaconMenu(playerInventory, inventory));
        this.inventory = inventory;
        this.backgroundWidth = 230;
        this.backgroundHeight = 219;
    }

    @Override
    public void init() {
        super.init();
        this.buttons.add(this.doneButton = new BeaconScreen.DoneButtonWidget(-1, this.x + 164, this.y + 107));
        this.buttons.add(new BeaconScreen.CancelButtonWidget(-2, this.x + 190, this.y + 107));
        this.acceptsPayment = true;
        this.doneButton.active = false;
    }

    @Override
    public void tick() {
        super.tick();
        int i = this.inventory.getData(0);
        int j = this.inventory.getData(1);
        int k = this.inventory.getData(2);
        if (this.acceptsPayment && i >= 0) {
            this.acceptsPayment = false;

            for (int l = 0; l <= 2; l++) {
                int i1 = BeaconBlockEntity.EFFECTS[l].length;
                int j1 = i1 * 22 + (i1 - 1) * 2;

                for (int k1 = 0; k1 < i1; k1++) {
                    int l1 = BeaconBlockEntity.EFFECTS[l][k1].id;
                    BeaconScreen.EffectButtonWidget beaconscreen$effectbuttonwidget = new BeaconScreen.EffectButtonWidget(
                        l << 8 | l1, this.x + 76 + k1 * 24 - j1 / 2, this.y + 22 + l * 25, l1, l
                    );
                    this.buttons.add(beaconscreen$effectbuttonwidget);
                    if (l >= i) {
                        beaconscreen$effectbuttonwidget.active = false;
                    } else if (l1 == j) {
                        beaconscreen$effectbuttonwidget.setDisabled(true);
                    }
                }
            }

            int i2 = 3;
            int j2 = BeaconBlockEntity.EFFECTS[i2].length + 1;
            int k2 = j2 * 22 + (j2 - 1) * 2;

            for (int l2 = 0; l2 < j2 - 1; l2++) {
                int i3 = BeaconBlockEntity.EFFECTS[i2][l2].id;
                BeaconScreen.EffectButtonWidget beaconscreen$effectbuttonwidget2 = new BeaconScreen.EffectButtonWidget(
                    i2 << 8 | i3, this.x + 167 + l2 * 24 - k2 / 2, this.y + 47, i3, i2
                );
                this.buttons.add(beaconscreen$effectbuttonwidget2);
                if (i2 >= i) {
                    beaconscreen$effectbuttonwidget2.active = false;
                } else if (i3 == k) {
                    beaconscreen$effectbuttonwidget2.setDisabled(true);
                }
            }

            if (j > 0) {
                BeaconScreen.EffectButtonWidget beaconscreen$effectbuttonwidget1 = new BeaconScreen.EffectButtonWidget(
                    i2 << 8 | j, this.x + 167 + (j2 - 1) * 24 - k2 / 2, this.y + 47, j, i2
                );
                this.buttons.add(beaconscreen$effectbuttonwidget1);
                if (i2 >= i) {
                    beaconscreen$effectbuttonwidget1.active = false;
                } else if (j == k) {
                    beaconscreen$effectbuttonwidget1.setDisabled(true);
                }
            }
        }

        this.doneButton.active = this.inventory.getItem(0) != null && j > 0;
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == -2) {
            this.minecraft.openScreen(null);
        } else if (button.id == -1) {
            String s = "MC|Beacon";
            PacketByteBuf packetbytebuf = new PacketByteBuf(Unpooled.buffer());
            packetbytebuf.writeInt(this.inventory.getData(1));
            packetbytebuf.writeInt(this.inventory.getData(2));
            this.minecraft.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(s, packetbytebuf));
            this.minecraft.openScreen(null);
        } else if (button instanceof BeaconScreen.EffectButtonWidget) {
            if (((BeaconScreen.EffectButtonWidget)button).isDisabled()) {
                return;
            }

            int j = button.id;
            int k = j & 0xFF;
            int i = j >> 8;
            if (i < 3) {
                this.inventory.setData(1, k);
            } else {
                this.inventory.setData(2, k);
            }

            this.buttons.clear();
            this.init();
            this.tick();
        }
    }

    @Override
    protected void renderLabels(int mouseX, int mouseY) {
        Lighting.turnOff();
        this.drawCenteredString(this.textRenderer, I18n.translate("tile.beacon.primary"), 62, 10, 14737632);
        this.drawCenteredString(this.textRenderer, I18n.translate("tile.beacon.secondary"), 169, 10, 14737632);

        for (ButtonWidget buttonwidget : this.buttons) {
            if (buttonwidget.isHovered()) {
                buttonwidget.renderTooltip(mouseX - this.x, mouseY - this.y);
                break;
            }
        }

        Lighting.turnOnGui();
    }

    @Override
    protected void renderMenuBackground(float tickDelta, int mouseX, int mouseY) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(MENU_LOCATION);
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        this.drawTexture(i, j, 0, 0, this.backgroundWidth, this.backgroundHeight);
        this.itemRenderer.zOffset = 100.0F;
        this.itemRenderer.renderGuiItem(new ItemStack(Items.EMERALD), i + 42, j + 109);
        this.itemRenderer.renderGuiItem(new ItemStack(Items.DIAMOND), i + 42 + 22, j + 109);
        this.itemRenderer.renderGuiItem(new ItemStack(Items.GOLD_INGOT), i + 42 + 44, j + 109);
        this.itemRenderer.renderGuiItem(new ItemStack(Items.IRON_INGOT), i + 42 + 66, j + 109);
        this.itemRenderer.zOffset = 0.0F;
    }

    static class BeaconButtonWidget extends ButtonWidget {
        /**
         * The texture of the button inside the beacon GUI
         */
        private final Identifier textureLocation;
        private final int u;
        private final int v;
        /**
         * If clicking the button is disabled (button becomes unclickable and grayed out).
         */
        private boolean disabled;

        protected BeaconButtonWidget(int x, int y, int id, Identifier textureLocation, int u, int v) {
            super(x, y, id, 22, 22, "");
            this.textureLocation = textureLocation;
            this.u = u;
            this.v = v;
        }

        @Override
        public void render(Minecraft minecraft, int mouseX, int mouseY) {
            if (this.visible) {
                minecraft.getTextureManager().bind(BeaconScreen.MENU_LOCATION);
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
                int i = 219;
                int j = 0;
                if (!this.active) {
                    j += this.width * 2;
                } else if (this.disabled) {
                    j += this.width * 1;
                } else if (this.hovered) {
                    j += this.width * 3;
                }

                this.drawTexture(this.x, this.y, j, i, this.width, this.height);
                if (!BeaconScreen.MENU_LOCATION.equals(this.textureLocation)) {
                    minecraft.getTextureManager().bind(this.textureLocation);
                }

                this.drawTexture(this.x + 2, this.y + 2, this.u, this.v, 18, 18);
            }
        }

        public boolean isDisabled() {
            return this.disabled;
        }

        public void setDisabled(boolean disabled) {
            this.disabled = disabled;
        }
    }

    class CancelButtonWidget extends BeaconScreen.BeaconButtonWidget {
        public CancelButtonWidget(int x, int y, int id) {
            super(x, y, id, BeaconScreen.MENU_LOCATION, 112, 220);
        }

        @Override
        public void renderTooltip(int mouseX, int mouseY) {
            BeaconScreen.this.renderTooltip(I18n.translate("gui.cancel"), mouseX, mouseY);
        }
    }

    class DoneButtonWidget extends BeaconScreen.BeaconButtonWidget {
        public DoneButtonWidget(int x, int y, int id) {
            super(x, y, id, BeaconScreen.MENU_LOCATION, 90, 220);
        }

        @Override
        public void renderTooltip(int mouseX, int mouseY) {
            BeaconScreen.this.renderTooltip(I18n.translate("gui.done"), mouseX, mouseY);
        }
    }

    class EffectButtonWidget extends BeaconScreen.BeaconButtonWidget {
        private final int effectId;
        private final int effectStrength;

        public EffectButtonWidget(int x, int y, int id, int effectId, int effectStrength) {
            super(
                x,
                y,
                id,
                InventoryMenuScreen.MENU_LOCATION,
                0 + StatusEffect.BY_ID[effectId].getIconIndex() % 8 * 18,
                198 + StatusEffect.BY_ID[effectId].getIconIndex() / 8 * 18
            );
            this.effectId = effectId;
            this.effectStrength = effectStrength;
        }

        @Override
        public void renderTooltip(int mouseX, int mouseY) {
            String s = I18n.translate(StatusEffect.BY_ID[this.effectId].getTranslationKey());
            if (this.effectStrength >= 3 && this.effectId != StatusEffect.REGENERATION.id) {
                s = s + " II";
            }

            BeaconScreen.this.renderTooltip(s, mouseX, mouseY);
        }
    }
}
