package net.minecraft.client.gui.screen.inventory.menu;

import java.util.Collection;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.inventory.menu.InventoryMenu;

public abstract class PlayerInventoryScreen extends InventoryMenuScreen {
    private boolean hasStatusEffect;

    public PlayerInventoryScreen(InventoryMenu inventoryMenu) {
        super(inventoryMenu);
    }

    @Override
    public void init() {
        super.init();
        this.checkStatusEffects();
    }

    protected void checkStatusEffects() {
        if (!this.minecraft.player.getStatusEffects().isEmpty()) {
            this.x = 160 + (this.width - this.backgroundWidth - 200) / 2;
            this.hasStatusEffect = true;
        } else {
            this.x = (this.width - this.backgroundWidth) / 2;
            this.hasStatusEffect = false;
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        super.render(mouseX, mouseY, tickDelta);
        if (this.hasStatusEffect) {
            this.drawStatusEffects();
        }
    }

    private void drawStatusEffects() {
        int i = this.x - 124;
        int j = this.y;
        int k = 166;
        Collection<StatusEffectInstance> collection = this.minecraft.player.getStatusEffects();
        if (!collection.isEmpty()) {
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableLighting();
            int l = 33;
            if (collection.size() > 5) {
                l = 132 / (collection.size() - 1);
            }

            for (StatusEffectInstance statuseffectinstance : this.minecraft.player.getStatusEffects()) {
                StatusEffect statuseffect = StatusEffect.BY_ID[statuseffectinstance.getId()];
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                this.minecraft.getTextureManager().bind(MENU_LOCATION);
                this.drawTexture(i, j, 0, 166, 140, 32);
                if (statuseffect.hasIcon()) {
                    int i1 = statuseffect.getIconIndex();
                    this.drawTexture(i + 6, j + 7, 0 + i1 % 8 * 18, 198 + i1 / 8 * 18, 18, 18);
                }

                String s1 = I18n.translate(statuseffect.getTranslationKey());
                if (statuseffectinstance.getAmplifier() == 1) {
                    s1 = s1 + " " + I18n.translate("enchantment.level.2");
                } else if (statuseffectinstance.getAmplifier() == 2) {
                    s1 = s1 + " " + I18n.translate("enchantment.level.3");
                } else if (statuseffectinstance.getAmplifier() == 3) {
                    s1 = s1 + " " + I18n.translate("enchantment.level.4");
                }

                this.textRenderer.drawWithShadow(s1, i + 10 + 18, j + 6, 16777215);
                String s = StatusEffect.getDurationString(statuseffectinstance);
                this.textRenderer.drawWithShadow(s, i + 10 + 18, j + 6 + 10, 8355711);
                j += l;
            }
        }
    }
}
