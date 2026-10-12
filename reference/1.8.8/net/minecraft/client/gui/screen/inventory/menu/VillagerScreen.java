package net.minecraft.client.gui.screen.inventory.menu;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.menu.TraderMenu;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.minecraft.world.village.trade.TradeOffer;
import net.minecraft.world.village.trade.TradeOffers;
import net.minecraft.world.village.trade.Trader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VillagerScreen extends InventoryMenuScreen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Identifier MENU_LOCATION = new Identifier("textures/gui/container/villager.png");
    private Trader trader;
    private VillagerScreen.PaginationButton buttonForward;
    private VillagerScreen.PaginationButton buttonBack;
    private int currentPage;
    private Text name;

    public VillagerScreen(PlayerInventory playerInventory, Trader trader, World world) {
        super(new TraderMenu(playerInventory, trader, world));
        this.trader = trader;
        this.name = trader.getDisplayName();
    }

    @Override
    public void init() {
        super.init();
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        this.buttons.add(this.buttonForward = new VillagerScreen.PaginationButton(1, i + 120 + 27, j + 24 - 1, true));
        this.buttons.add(this.buttonBack = new VillagerScreen.PaginationButton(2, i + 36 - 19, j + 24 - 1, false));
        this.buttonForward.active = false;
        this.buttonBack.active = false;
    }

    @Override
    protected void renderLabels(int mouseX, int mouseY) {
        String s = this.name.getString();
        this.textRenderer.draw(s, this.backgroundWidth / 2 - this.textRenderer.getWidth(s) / 2, 6, 4210752);
        this.textRenderer.draw(I18n.translate("container.inventory"), 8, this.backgroundHeight - 96 + 2, 4210752);
    }

    @Override
    public void tick() {
        super.tick();
        TradeOffers tradeoffers = this.trader.getOffers(this.minecraft.player);
        if (tradeoffers != null) {
            this.buttonForward.active = this.currentPage < tradeoffers.size() - 1;
            this.buttonBack.active = this.currentPage > 0;
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        boolean flag = false;
        if (button == this.buttonForward) {
            this.currentPage++;
            TradeOffers tradeoffers = this.trader.getOffers(this.minecraft.player);
            if (tradeoffers != null && this.currentPage >= tradeoffers.size()) {
                this.currentPage = tradeoffers.size() - 1;
            }

            flag = true;
        } else if (button == this.buttonBack) {
            this.currentPage--;
            if (this.currentPage < 0) {
                this.currentPage = 0;
            }

            flag = true;
        }

        if (flag) {
            ((TraderMenu)this.menu).setRecipeIndex(this.currentPage);
            PacketByteBuf packetbytebuf = new PacketByteBuf(Unpooled.buffer());
            packetbytebuf.writeInt(this.currentPage);
            this.minecraft.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket("MC|TrSel", packetbytebuf));
        }
    }

    @Override
    protected void renderMenuBackground(float tickDelta, int mouseX, int mouseY) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(MENU_LOCATION);
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        this.drawTexture(i, j, 0, 0, this.backgroundWidth, this.backgroundHeight);
        TradeOffers tradeoffers = this.trader.getOffers(this.minecraft.player);
        if (tradeoffers != null && !tradeoffers.isEmpty()) {
            int k = this.currentPage;
            if (k < 0 || k >= tradeoffers.size()) {
                return;
            }

            TradeOffer tradeoffer = tradeoffers.get(k);
            if (tradeoffer.isDisabled()) {
                this.minecraft.getTextureManager().bind(MENU_LOCATION);
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.disableLighting();
                this.drawTexture(this.x + 83, this.y + 21, 212, 0, 28, 21);
                this.drawTexture(this.x + 83, this.y + 51, 212, 0, 28, 21);
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        super.render(mouseX, mouseY, tickDelta);
        TradeOffers tradeoffers = this.trader.getOffers(this.minecraft.player);
        if (tradeoffers != null && !tradeoffers.isEmpty()) {
            int i = (this.width - this.backgroundWidth) / 2;
            int j = (this.height - this.backgroundHeight) / 2;
            int k = this.currentPage;
            TradeOffer tradeoffer = tradeoffers.get(k);
            ItemStack itemstack = tradeoffer.getPrimaryPayment();
            ItemStack itemstack1 = tradeoffer.getSecondaryPayment();
            ItemStack itemstack2 = tradeoffer.getResult();
            GlStateManager.pushMatrix();
            Lighting.turnOnGui();
            GlStateManager.disableLighting();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableColorMaterial();
            GlStateManager.enableLighting();
            this.itemRenderer.zOffset = 100.0F;
            this.itemRenderer.renderGuiItem(itemstack, i + 36, j + 24);
            this.itemRenderer.renderGuiItemDecoration(this.textRenderer, itemstack, i + 36, j + 24);
            if (itemstack1 != null) {
                this.itemRenderer.renderGuiItem(itemstack1, i + 62, j + 24);
                this.itemRenderer.renderGuiItemDecoration(this.textRenderer, itemstack1, i + 62, j + 24);
            }

            this.itemRenderer.renderGuiItem(itemstack2, i + 120, j + 24);
            this.itemRenderer.renderGuiItemDecoration(this.textRenderer, itemstack2, i + 120, j + 24);
            this.itemRenderer.zOffset = 0.0F;
            GlStateManager.disableLighting();
            if (this.isMouseInRegion(36, 24, 16, 16, mouseX, mouseY) && itemstack != null) {
                this.renderTooltip(itemstack, mouseX, mouseY);
            } else if (itemstack1 != null && this.isMouseInRegion(62, 24, 16, 16, mouseX, mouseY) && itemstack1 != null) {
                this.renderTooltip(itemstack1, mouseX, mouseY);
            } else if (itemstack2 != null && this.isMouseInRegion(120, 24, 16, 16, mouseX, mouseY) && itemstack2 != null) {
                this.renderTooltip(itemstack2, mouseX, mouseY);
            } else if (tradeoffer.isDisabled()
                && (this.isMouseInRegion(83, 21, 28, 21, mouseX, mouseY) || this.isMouseInRegion(83, 51, 28, 21, mouseX, mouseY))) {
                this.renderTooltip(I18n.translate("merchant.deprecated"), mouseX, mouseY);
            }

            GlStateManager.popMatrix();
            GlStateManager.enableLighting();
            GlStateManager.enableDepthTest();
            Lighting.turnOn();
        }
    }

    public Trader getTrader() {
        return this.trader;
    }

    static class PaginationButton extends ButtonWidget {
        private final boolean hasHeight;

        public PaginationButton(int x, int y, int id, boolean hasHeight) {
            super(x, y, id, 12, 19, "");
            this.hasHeight = hasHeight;
        }

        @Override
        public void render(Minecraft minecraft, int mouseX, int mouseY) {
            if (this.visible) {
                minecraft.getTextureManager().bind(VillagerScreen.MENU_LOCATION);
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                boolean flag = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
                int i = 0;
                int j = 176;
                if (!this.active) {
                    j += this.width * 2;
                } else if (flag) {
                    j += this.width;
                }

                if (!this.hasHeight) {
                    i += this.height;
                }

                this.drawTexture(this.x, this.y, j, i, this.width, this.height);
            }
        }
    }
}
