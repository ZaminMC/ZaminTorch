package net.minecraft.client.gui.screen.inventory.menu;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Random;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.model.block.entity.EnchantingTableBookModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.menu.EnchantingTableMenu;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Nameable;
import net.minecraft.world.World;
import org.lwjgl.util.glu.Project;

public class EnchantingTableScreen extends InventoryMenuScreen {
    private static final Identifier MENU_LOCATION = new Identifier("textures/gui/container/enchanting_table.png");
    private static final Identifier BOOK_LOCATION = new Identifier("textures/entity/enchanting_table_book.png");
    private static final EnchantingTableBookModel BOOK_MODEL = new EnchantingTableBookModel();
    private final PlayerInventory playerInventory;
    private Random random = new Random();
    private EnchantingTableMenu menu;
    public int ticksOpen;
    public float nextPageAngle;
    public float pageAngle;
    public float approximatePageAngle;
    public float pageRotationSpeed;
    public float nextTurningSpeed;
    public float turningSpeed;
    ItemStack itemToEnchant;
    private final Nameable inventory;

    public EnchantingTableScreen(PlayerInventory playerInventory, World world, Nameable inventory) {
        super(new EnchantingTableMenu(playerInventory, world));
        this.playerInventory = playerInventory;
        this.menu = (EnchantingTableMenu)this.menu;
        this.inventory = inventory;
    }

    @Override
    protected void renderLabels(int mouseX, int mouseY) {
        this.textRenderer.draw(this.inventory.getDisplayName().getString(), 12, 5, 4210752);
        this.textRenderer.draw(this.playerInventory.getDisplayName().getString(), 8, this.backgroundHeight - 96 + 2, 4210752);
    }

    @Override
    public void tick() {
        super.tick();
        this.tickEnchantingScreen();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;

        for (int k = 0; k < 3; k++) {
            int l = mouseX - (i + 60);
            int i1 = mouseY - (j + 14 + 19 * k);
            if (l >= 0 && i1 >= 0 && l < 108 && i1 < 19 && this.menu.onButtonClick(this.minecraft.player, k)) {
                this.minecraft.interactionManager.clickMenuButton(this.menu.networkId, k);
            }
        }
    }

    @Override
    protected void renderMenuBackground(float tickDelta, int mouseX, int mouseY) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(MENU_LOCATION);
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        this.drawTexture(i, j, 0, 0, this.backgroundWidth, this.backgroundHeight);
        GlStateManager.pushMatrix();
        GlStateManager.matrixMode(5889);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Window window = new Window(this.minecraft);
        GlStateManager.viewport(
            (window.getWidth() - 320) / 2 * window.getScale(),
            (window.getHeight() - 240) / 2 * window.getScale(),
            320 * window.getScale(),
            240 * window.getScale()
        );
        GlStateManager.translatef(-0.34F, 0.23F, 0.0F);
        Project.gluPerspective(90.0F, 1.3333334F, 9.0F, 80.0F);
        float f = 1.0F;
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        Lighting.turnOn();
        GlStateManager.translatef(0.0F, 3.3F, -16.0F);
        GlStateManager.scalef(f, f, f);
        float f1 = 5.0F;
        GlStateManager.scalef(f1, f1, f1);
        GlStateManager.rotatef(180.0F, 0.0F, 0.0F, 1.0F);
        this.minecraft.getTextureManager().bind(BOOK_LOCATION);
        GlStateManager.rotatef(20.0F, 1.0F, 0.0F, 0.0F);
        float f2 = this.turningSpeed + (this.nextTurningSpeed - this.turningSpeed) * tickDelta;
        GlStateManager.translatef((1.0F - f2) * 0.2F, (1.0F - f2) * 0.1F, (1.0F - f2) * 0.25F);
        GlStateManager.rotatef(-(1.0F - f2) * 90.0F - 90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(180.0F, 1.0F, 0.0F, 0.0F);
        float f3 = this.pageAngle + (this.nextPageAngle - this.pageAngle) * tickDelta + 0.25F;
        float f4 = this.pageAngle + (this.nextPageAngle - this.pageAngle) * tickDelta + 0.75F;
        f3 = (f3 - MathHelper.fastFloor(f3)) * 1.6F - 0.3F;
        f4 = (f4 - MathHelper.fastFloor(f4)) * 1.6F - 0.3F;
        if (f3 < 0.0F) {
            f3 = 0.0F;
        }

        if (f4 < 0.0F) {
            f4 = 0.0F;
        }

        if (f3 > 1.0F) {
            f3 = 1.0F;
        }

        if (f4 > 1.0F) {
            f4 = 1.0F;
        }

        GlStateManager.enableRescaleNormal();
        BOOK_MODEL.render(null, 0.0F, f3, f4, f2, 0.0F, 0.0625F);
        GlStateManager.disableRescaleNormal();
        Lighting.turnOff();
        GlStateManager.matrixMode(5889);
        GlStateManager.viewport(0, 0, this.minecraft.width, this.minecraft.height);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(5888);
        GlStateManager.popMatrix();
        Lighting.turnOff();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        EnchantingPhrases.getInstance().setSeed(this.menu.seed);
        int k = this.menu.getLapisCount();

        for (int l = 0; l < 3; l++) {
            int i1 = i + 60;
            int j1 = i1 + 20;
            int k1 = 86;
            String s = EnchantingPhrases.getInstance().getRandomPhrase();
            this.drawOffset = 0.0F;
            this.minecraft.getTextureManager().bind(MENU_LOCATION);
            int l1 = this.menu.enchantingCosts[l];
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            if (l1 == 0) {
                this.drawTexture(i1, j + 14 + 19 * l, 0, 185, 108, 19);
            } else {
                String s1 = "" + l1;
                TextRenderer textrenderer = this.minecraft.enchantingPhraseRenderer;
                int i2 = 6839882;
                if ((k < l + 1 || this.minecraft.player.xpLevel < l1) && !this.minecraft.player.abilities.creativeMode) {
                    this.drawTexture(i1, j + 14 + 19 * l, 0, 185, 108, 19);
                    this.drawTexture(i1 + 1, j + 15 + 19 * l, 16 * l, 239, 16, 16);
                    textrenderer.splitAndDraw(s, j1, j + 16 + 19 * l, k1, (i2 & 16711422) >> 1);
                    i2 = 4226832;
                } else {
                    int j2 = mouseX - (i + 60);
                    int k2 = mouseY - (j + 14 + 19 * l);
                    if (j2 >= 0 && k2 >= 0 && j2 < 108 && k2 < 19) {
                        this.drawTexture(i1, j + 14 + 19 * l, 0, 204, 108, 19);
                        i2 = 16777088;
                    } else {
                        this.drawTexture(i1, j + 14 + 19 * l, 0, 166, 108, 19);
                    }

                    this.drawTexture(i1 + 1, j + 15 + 19 * l, 16 * l, 223, 16, 16);
                    textrenderer.splitAndDraw(s, j1, j + 16 + 19 * l, k1, i2);
                    i2 = 8453920;
                }

                textrenderer = this.minecraft.textRenderer;
                textrenderer.drawWithShadow(s1, j1 + 86 - textrenderer.getWidth(s1), j + 16 + 19 * l + 7, i2);
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        super.render(mouseX, mouseY, tickDelta);
        boolean flag = this.minecraft.player.abilities.creativeMode;
        int i = this.menu.getLapisCount();

        for (int j = 0; j < 3; j++) {
            int k = this.menu.enchantingCosts[j];
            int l = this.menu.enchantmentClues[j];
            int i1 = j + 1;
            if (this.isMouseInRegion(60, 14 + 19 * j, 108, 17, mouseX, mouseY) && k > 0 && l >= 0) {
                List<String> list = Lists.newArrayList();
                if (l >= 0 && Enchantment.byId(l & 0xFF) != null) {
                    String s = Enchantment.byId(l & 0xFF).getName((l & 0xFF00) >> 8);
                    list.add(Formatting.WHITE.toString() + Formatting.ITALIC.toString() + I18n.translate("container.enchant.clue", s));
                }

                if (!flag) {
                    if (l >= 0) {
                        list.add("");
                    }

                    if (this.minecraft.player.xpLevel < k) {
                        list.add(Formatting.RED.toString() + "Level Requirement: " + this.menu.enchantingCosts[j]);
                    } else {
                        String s1 = "";
                        if (i1 == 1) {
                            s1 = I18n.translate("container.enchant.lapis.one");
                        } else {
                            s1 = I18n.translate("container.enchant.lapis.many", i1);
                        }

                        if (i >= i1) {
                            list.add(Formatting.GRAY.toString() + "" + s1);
                        } else {
                            list.add(Formatting.RED.toString() + "" + s1);
                        }

                        if (i1 == 1) {
                            s1 = I18n.translate("container.enchant.level.one");
                        } else {
                            s1 = I18n.translate("container.enchant.level.many", i1);
                        }

                        list.add(Formatting.GRAY.toString() + "" + s1);
                    }
                }

                this.renderTooltip(list, mouseX, mouseY);
                break;
            }
        }
    }

    public void tickEnchantingScreen() {
        ItemStack itemstack = this.menu.getSlot(0).getItem();
        if (!ItemStack.matches(itemstack, this.itemToEnchant)) {
            this.itemToEnchant = itemstack;

            do {
                this.approximatePageAngle = this.approximatePageAngle + (this.random.nextInt(4) - this.random.nextInt(4));
            } while (this.nextPageAngle <= this.approximatePageAngle + 1.0F && this.nextPageAngle >= this.approximatePageAngle - 1.0F);
        }

        this.ticksOpen++;
        this.pageAngle = this.nextPageAngle;
        this.turningSpeed = this.nextTurningSpeed;
        boolean flag = false;

        for (int i = 0; i < 3; i++) {
            if (this.menu.enchantingCosts[i] != 0) {
                flag = true;
            }
        }

        if (flag) {
            this.nextTurningSpeed += 0.2F;
        } else {
            this.nextTurningSpeed -= 0.2F;
        }

        this.nextTurningSpeed = MathHelper.clamp(this.nextTurningSpeed, 0.0F, 1.0F);
        float f1 = (this.approximatePageAngle - this.nextPageAngle) * 0.4F;
        float f = 0.2F;
        f1 = MathHelper.clamp(f1, -f, f);
        this.pageRotationSpeed = this.pageRotationSpeed + (f1 - this.pageRotationSpeed) * 0.9F;
        this.nextPageAngle = this.nextPageAngle + this.pageRotationSpeed;
    }
}
