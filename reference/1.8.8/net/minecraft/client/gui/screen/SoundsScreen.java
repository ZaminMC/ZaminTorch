package net.minecraft.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.SoundCategory;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.client.sound.system.SoundManager;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class SoundsScreen extends Screen {
    private final Screen parent;
    private final GameOptions options;
    protected String name = "Options";
    private String off;

    public SoundsScreen(Screen parent, GameOptions options) {
        this.parent = parent;
        this.options = options;
    }

    @Override
    public void init() {
        int i = 0;
        this.name = I18n.translate("options.sounds.title");
        this.off = I18n.translate("options.off");
        this.buttons
            .add(
                new SoundsScreen.SoundSliderWidget(
                    SoundCategory.MASTER.getId(), this.width / 2 - 155 + i % 2 * 160, this.height / 6 - 12 + 24 * (i >> 1), SoundCategory.MASTER, true
                )
            );
        i += 2;

        for (SoundCategory soundcategory : SoundCategory.values()) {
            if (soundcategory != SoundCategory.MASTER) {
                this.buttons
                    .add(
                        new SoundsScreen.SoundSliderWidget(
                            soundcategory.getId(), this.width / 2 - 155 + i % 2 * 160, this.height / 6 - 12 + 24 * (i >> 1), soundcategory, false
                        )
                    );
                i++;
            }
        }

        this.buttons.add(new ButtonWidget(200, this.width / 2 - 100, this.height / 6 + 168, I18n.translate("gui.done")));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 200) {
                this.minecraft.options.save();
                this.minecraft.openScreen(this.parent);
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, this.name, this.width / 2, 15, 16777215);
        super.render(mouseX, mouseY, tickDelta);
    }

    protected String getVolume(SoundCategory soundCatecory) {
        float f = this.options.getSoundCategoryVolume(soundCatecory);
        return f == 0.0F ? this.off : (int)(f * 100.0F) + "%";
    }

    class SoundSliderWidget extends ButtonWidget {
        private final SoundCategory soundCatergory;
        private final String widgetName;
        public float sliderButtonPos = 1.0F;
        public boolean dragging;

        public SoundSliderWidget(int id, int x, int y, SoundCategory category, boolean doubleSlider) {
            super(id, x, y, doubleSlider ? 310 : 150, 20, "");
            this.soundCatergory = category;
            this.widgetName = I18n.translate("soundCategory." + category.getName());
            this.message = this.widgetName + ": " + SoundsScreen.this.getVolume(category);
            this.sliderButtonPos = SoundsScreen.this.options.getSoundCategoryVolume(category);
        }

        @Override
        protected int getYImage(boolean hovered) {
            return 0;
        }

        @Override
        protected void renderBackground(Minecraft minecraft, int mouseX, int mouseY) {
            if (this.visible) {
                if (this.dragging) {
                    this.sliderButtonPos = (float)(mouseX - (this.x + 4)) / (this.width - 8);
                    this.sliderButtonPos = MathHelper.clamp(this.sliderButtonPos, 0.0F, 1.0F);
                    minecraft.options.setSoundCategoryVolume(this.soundCatergory, this.sliderButtonPos);
                    minecraft.options.save();
                    this.message = this.widgetName + ": " + SoundsScreen.this.getVolume(this.soundCatergory);
                }

                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                this.drawTexture(this.x + (int)(this.sliderButtonPos * (this.width - 8)), this.y, 0, 66, 4, 20);
                this.drawTexture(this.x + (int)(this.sliderButtonPos * (this.width - 8)) + 4, this.y, 196, 66, 4, 20);
            }
        }

        @Override
        public boolean mouseClicked(Minecraft minecraft, int mouseX, int mouseY) {
            if (super.mouseClicked(minecraft, mouseX, mouseY)) {
                this.sliderButtonPos = (float)(mouseX - (this.x + 4)) / (this.width - 8);
                this.sliderButtonPos = MathHelper.clamp(this.sliderButtonPos, 0.0F, 1.0F);
                minecraft.options.setSoundCategoryVolume(this.soundCatergory, this.sliderButtonPos);
                minecraft.options.save();
                this.message = this.widgetName + ": " + SoundsScreen.this.getVolume(this.soundCatergory);
                this.dragging = true;
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void playClickSound(SoundManager soundManager) {
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            if (this.dragging) {
                if (this.soundCatergory == SoundCategory.MASTER) {
                    float f = 1.0F;
                } else {
                    SoundsScreen.this.options.getSoundCategoryVolume(this.soundCatergory);
                }

                SoundsScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.of(new Identifier("gui.button.press"), 1.0F));
            }

            this.dragging = false;
        }
    }
}
