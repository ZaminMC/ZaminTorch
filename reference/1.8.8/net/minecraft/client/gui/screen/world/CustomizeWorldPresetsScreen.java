package net.minecraft.client.gui.screen.world;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.resource.Identifier;
import net.minecraft.world.gen.chunk.OverworldGeneratorOptions;
import org.lwjgl.input.Keyboard;

public class CustomizeWorldPresetsScreen extends Screen {
    private static final List<CustomizeWorldPresetsScreen.CustomWorldPreset> PRESETS = Lists.newArrayList();
    private CustomizeWorldPresetsScreen.PresetsWidget presets;
    private ButtonWidget usePresetButton;
    private TextFieldWidget presetField;
    private CustomizeWorldScreen parent;
    protected String title = "Customize World Presets";
    private String sharePresetText;
    private String presetListText;

    public CustomizeWorldPresetsScreen(CustomizeWorldScreen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        this.buttons.clear();
        Keyboard.enableRepeatEvents(true);
        this.title = I18n.translate("createWorld.customize.custom.presets.title");
        this.sharePresetText = I18n.translate("createWorld.customize.presets.share");
        this.presetListText = I18n.translate("createWorld.customize.presets.list");
        this.presetField = new TextFieldWidget(2, this.textRenderer, 50, 40, this.width - 100, 20);
        this.presets = new CustomizeWorldPresetsScreen.PresetsWidget();
        this.presetField.setMaxLength(2000);
        this.presetField.setText(this.parent.getOverworldGeneratorOptions());
        this.buttons
            .add(
                this.usePresetButton = new ButtonWidget(
                    0, this.width / 2 - 102, this.height - 27, 100, 20, I18n.translate("createWorld.customize.presets.select")
                )
            );
        this.buttons.add(new ButtonWidget(1, this.width / 2 + 3, this.height - 27, 100, 20, I18n.translate("gui.cancel")));
        this.updateButtons();
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        this.presets.handleMouse();
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        this.presetField.mouseClicked(mouseX, mouseY, button);
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (!this.presetField.keyPressed(chr, key)) {
            super.keyPressed(chr, key);
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        switch (button.id) {
            case 0:
                this.parent.selectOverworldGeneratorOptions(this.presetField.getText());
                this.minecraft.openScreen(this.parent);
                break;
            case 1:
                this.minecraft.openScreen(this.parent);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.presets.render(mouseX, mouseY, tickDelta);
        this.drawCenteredString(this.textRenderer, this.title, this.width / 2, 8, 16777215);
        this.drawString(this.textRenderer, this.sharePresetText, 50, 30, 10526880);
        this.drawString(this.textRenderer, this.presetListText, 50, 70, 10526880);
        this.presetField.render();
        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    public void tick() {
        this.presetField.tick();
        super.tick();
    }

    public void updateButtons() {
        this.usePresetButton.active = this.hasSelectedPreset();
    }

    private boolean hasSelectedPreset() {
        return this.presets.selected > -1 && this.presets.selected < PRESETS.size() || this.presetField.getText().length() > 1;
    }

    static {
        OverworldGeneratorOptions.Builder overworldgeneratoroptions$builder = OverworldGeneratorOptions.Builder.fromJson(
            "{ \"coordinateScale\":684.412, \"heightScale\":684.412, \"upperLimitScale\":512.0, \"lowerLimitScale\":512.0, \"depthNoiseScaleX\":200.0, \"depthNoiseScaleZ\":200.0, \"depthNoiseScaleExponent\":0.5, \"mainNoiseScaleX\":5000.0, \"mainNoiseScaleY\":1000.0, \"mainNoiseScaleZ\":5000.0, \"baseSize\":8.5, \"stretchY\":8.0, \"biomeDepthWeight\":2.0, \"biomeDepthOffset\":0.5, \"biomeScaleWeight\":2.0, \"biomeScaleOffset\":0.375, \"useCaves\":true, \"useDungeons\":true, \"dungeonChance\":8, \"useStrongholds\":true, \"useVillages\":true, \"useMineShafts\":true, \"useTemples\":true, \"useRavines\":true, \"useWaterLakes\":true, \"waterLakeChance\":4, \"useLavaLakes\":true, \"lavaLakeChance\":80, \"useLavaOceans\":false, \"seaLevel\":255 }"
        );
        Identifier identifier = new Identifier("textures/gui/presets/water.png");
        PRESETS.add(
            new CustomizeWorldPresetsScreen.CustomWorldPreset(
                I18n.translate("createWorld.customize.custom.preset.waterWorld"), identifier, overworldgeneratoroptions$builder
            )
        );
        overworldgeneratoroptions$builder = OverworldGeneratorOptions.Builder.fromJson(
            "{\"coordinateScale\":3000.0, \"heightScale\":6000.0, \"upperLimitScale\":250.0, \"lowerLimitScale\":512.0, \"depthNoiseScaleX\":200.0, \"depthNoiseScaleZ\":200.0, \"depthNoiseScaleExponent\":0.5, \"mainNoiseScaleX\":80.0, \"mainNoiseScaleY\":160.0, \"mainNoiseScaleZ\":80.0, \"baseSize\":8.5, \"stretchY\":10.0, \"biomeDepthWeight\":1.0, \"biomeDepthOffset\":0.0, \"biomeScaleWeight\":1.0, \"biomeScaleOffset\":0.0, \"useCaves\":true, \"useDungeons\":true, \"dungeonChance\":8, \"useStrongholds\":true, \"useVillages\":true, \"useMineShafts\":true, \"useTemples\":true, \"useRavines\":true, \"useWaterLakes\":true, \"waterLakeChance\":4, \"useLavaLakes\":true, \"lavaLakeChance\":80, \"useLavaOceans\":false, \"seaLevel\":63 }"
        );
        identifier = new Identifier("textures/gui/presets/isles.png");
        PRESETS.add(
            new CustomizeWorldPresetsScreen.CustomWorldPreset(
                I18n.translate("createWorld.customize.custom.preset.isleLand"), identifier, overworldgeneratoroptions$builder
            )
        );
        overworldgeneratoroptions$builder = OverworldGeneratorOptions.Builder.fromJson(
            "{\"coordinateScale\":684.412, \"heightScale\":684.412, \"upperLimitScale\":512.0, \"lowerLimitScale\":512.0, \"depthNoiseScaleX\":200.0, \"depthNoiseScaleZ\":200.0, \"depthNoiseScaleExponent\":0.5, \"mainNoiseScaleX\":5000.0, \"mainNoiseScaleY\":1000.0, \"mainNoiseScaleZ\":5000.0, \"baseSize\":8.5, \"stretchY\":5.0, \"biomeDepthWeight\":2.0, \"biomeDepthOffset\":1.0, \"biomeScaleWeight\":4.0, \"biomeScaleOffset\":1.0, \"useCaves\":true, \"useDungeons\":true, \"dungeonChance\":8, \"useStrongholds\":true, \"useVillages\":true, \"useMineShafts\":true, \"useTemples\":true, \"useRavines\":true, \"useWaterLakes\":true, \"waterLakeChance\":4, \"useLavaLakes\":true, \"lavaLakeChance\":80, \"useLavaOceans\":false, \"seaLevel\":63 }"
        );
        identifier = new Identifier("textures/gui/presets/delight.png");
        PRESETS.add(
            new CustomizeWorldPresetsScreen.CustomWorldPreset(
                I18n.translate("createWorld.customize.custom.preset.caveDelight"), identifier, overworldgeneratoroptions$builder
            )
        );
        overworldgeneratoroptions$builder = OverworldGeneratorOptions.Builder.fromJson(
            "{\"coordinateScale\":738.41864, \"heightScale\":157.69133, \"upperLimitScale\":801.4267, \"lowerLimitScale\":1254.1643, \"depthNoiseScaleX\":374.93652, \"depthNoiseScaleZ\":288.65228, \"depthNoiseScaleExponent\":1.2092624, \"mainNoiseScaleX\":1355.9908, \"mainNoiseScaleY\":745.5343, \"mainNoiseScaleZ\":1183.464, \"baseSize\":1.8758626, \"stretchY\":1.7137525, \"biomeDepthWeight\":1.7553768, \"biomeDepthOffset\":3.4701107, \"biomeScaleWeight\":1.0, \"biomeScaleOffset\":2.535211, \"useCaves\":true, \"useDungeons\":true, \"dungeonChance\":8, \"useStrongholds\":true, \"useVillages\":true, \"useMineShafts\":true, \"useTemples\":true, \"useRavines\":true, \"useWaterLakes\":true, \"waterLakeChance\":4, \"useLavaLakes\":true, \"lavaLakeChance\":80, \"useLavaOceans\":false, \"seaLevel\":63 }"
        );
        identifier = new Identifier("textures/gui/presets/madness.png");
        PRESETS.add(
            new CustomizeWorldPresetsScreen.CustomWorldPreset(
                I18n.translate("createWorld.customize.custom.preset.mountains"), identifier, overworldgeneratoroptions$builder
            )
        );
        overworldgeneratoroptions$builder = OverworldGeneratorOptions.Builder.fromJson(
            "{\"coordinateScale\":684.412, \"heightScale\":684.412, \"upperLimitScale\":512.0, \"lowerLimitScale\":512.0, \"depthNoiseScaleX\":200.0, \"depthNoiseScaleZ\":200.0, \"depthNoiseScaleExponent\":0.5, \"mainNoiseScaleX\":1000.0, \"mainNoiseScaleY\":3000.0, \"mainNoiseScaleZ\":1000.0, \"baseSize\":8.5, \"stretchY\":10.0, \"biomeDepthWeight\":1.0, \"biomeDepthOffset\":0.0, \"biomeScaleWeight\":1.0, \"biomeScaleOffset\":0.0, \"useCaves\":true, \"useDungeons\":true, \"dungeonChance\":8, \"useStrongholds\":true, \"useVillages\":true, \"useMineShafts\":true, \"useTemples\":true, \"useRavines\":true, \"useWaterLakes\":true, \"waterLakeChance\":4, \"useLavaLakes\":true, \"lavaLakeChance\":80, \"useLavaOceans\":false, \"seaLevel\":20 }"
        );
        identifier = new Identifier("textures/gui/presets/drought.png");
        PRESETS.add(
            new CustomizeWorldPresetsScreen.CustomWorldPreset(
                I18n.translate("createWorld.customize.custom.preset.drought"), identifier, overworldgeneratoroptions$builder
            )
        );
        overworldgeneratoroptions$builder = OverworldGeneratorOptions.Builder.fromJson(
            "{\"coordinateScale\":684.412, \"heightScale\":684.412, \"upperLimitScale\":2.0, \"lowerLimitScale\":64.0, \"depthNoiseScaleX\":200.0, \"depthNoiseScaleZ\":200.0, \"depthNoiseScaleExponent\":0.5, \"mainNoiseScaleX\":80.0, \"mainNoiseScaleY\":160.0, \"mainNoiseScaleZ\":80.0, \"baseSize\":8.5, \"stretchY\":12.0, \"biomeDepthWeight\":1.0, \"biomeDepthOffset\":0.0, \"biomeScaleWeight\":1.0, \"biomeScaleOffset\":0.0, \"useCaves\":true, \"useDungeons\":true, \"dungeonChance\":8, \"useStrongholds\":true, \"useVillages\":true, \"useMineShafts\":true, \"useTemples\":true, \"useRavines\":true, \"useWaterLakes\":true, \"waterLakeChance\":4, \"useLavaLakes\":true, \"lavaLakeChance\":80, \"useLavaOceans\":false, \"seaLevel\":6 }"
        );
        identifier = new Identifier("textures/gui/presets/chaos.png");
        PRESETS.add(
            new CustomizeWorldPresetsScreen.CustomWorldPreset(
                I18n.translate("createWorld.customize.custom.preset.caveChaos"), identifier, overworldgeneratoroptions$builder
            )
        );
        overworldgeneratoroptions$builder = OverworldGeneratorOptions.Builder.fromJson(
            "{\"coordinateScale\":684.412, \"heightScale\":684.412, \"upperLimitScale\":512.0, \"lowerLimitScale\":512.0, \"depthNoiseScaleX\":200.0, \"depthNoiseScaleZ\":200.0, \"depthNoiseScaleExponent\":0.5, \"mainNoiseScaleX\":80.0, \"mainNoiseScaleY\":160.0, \"mainNoiseScaleZ\":80.0, \"baseSize\":8.5, \"stretchY\":12.0, \"biomeDepthWeight\":1.0, \"biomeDepthOffset\":0.0, \"biomeScaleWeight\":1.0, \"biomeScaleOffset\":0.0, \"useCaves\":true, \"useDungeons\":true, \"dungeonChance\":8, \"useStrongholds\":true, \"useVillages\":true, \"useMineShafts\":true, \"useTemples\":true, \"useRavines\":true, \"useWaterLakes\":true, \"waterLakeChance\":4, \"useLavaLakes\":true, \"lavaLakeChance\":80, \"useLavaOceans\":true, \"seaLevel\":40 }"
        );
        identifier = new Identifier("textures/gui/presets/luck.png");
        PRESETS.add(
            new CustomizeWorldPresetsScreen.CustomWorldPreset(
                I18n.translate("createWorld.customize.custom.preset.goodLuck"), identifier, overworldgeneratoroptions$builder
            )
        );
    }

    static class CustomWorldPreset {
        public String name;
        public Identifier iconLocation;
        public OverworldGeneratorOptions.Builder generatorOptions;

        public CustomWorldPreset(String name, Identifier iconLocation, OverworldGeneratorOptions.Builder generatorOptions) {
            this.name = name;
            this.iconLocation = iconLocation;
            this.generatorOptions = generatorOptions;
        }
    }

    class PresetsWidget extends ListWidget {
        public int selected = -1;

        public PresetsWidget() {
            super(
                CustomizeWorldPresetsScreen.this.minecraft,
                CustomizeWorldPresetsScreen.this.width,
                CustomizeWorldPresetsScreen.this.height,
                80,
                CustomizeWorldPresetsScreen.this.height - 32,
                38
            );
        }

        @Override
        protected int size() {
            return CustomizeWorldPresetsScreen.PRESETS.size();
        }

        @Override
        protected void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            this.selected = index;
            CustomizeWorldPresetsScreen.this.updateButtons();
            CustomizeWorldPresetsScreen.this.presetField
                .setText(CustomizeWorldPresetsScreen.PRESETS.get(CustomizeWorldPresetsScreen.this.presets.selected).generatorOptions.toString());
        }

        @Override
        protected boolean isEntrySelected(int index) {
            return index == this.selected;
        }

        @Override
        protected void renderBackground() {
        }

        private void renderEntry(int x, int y, Identifier iconLocation) {
            int i = x + 5;
            int j = y;
            CustomizeWorldPresetsScreen.this.drawHorizontalLine(i - 1, i + 32, j - 1, -2039584);
            CustomizeWorldPresetsScreen.this.drawHorizontalLine(i - 1, i + 32, j + 32, -6250336);
            CustomizeWorldPresetsScreen.this.drawVerticalLine(i - 1, j - 1, j + 32, -2039584);
            CustomizeWorldPresetsScreen.this.drawVerticalLine(i + 32, j - 1, j + 32, -6250336);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.minecraft.getTextureManager().bind(iconLocation);
            int k = 32;
            int l = 32;
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
            bufferbuilder.vertex(i + 0, j + 32, 0.0).texture(0.0, 1.0).nextVertex();
            bufferbuilder.vertex(i + 32, j + 32, 0.0).texture(1.0, 1.0).nextVertex();
            bufferbuilder.vertex(i + 32, j + 0, 0.0).texture(1.0, 0.0).nextVertex();
            bufferbuilder.vertex(i + 0, j + 0, 0.0).texture(0.0, 0.0).nextVertex();
            tesselator.end();
        }

        @Override
        protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
            CustomizeWorldPresetsScreen.CustomWorldPreset customizeworldpresetsscreen$customworldpreset = CustomizeWorldPresetsScreen.PRESETS.get(index);
            this.renderEntry(x, y, customizeworldpresetsscreen$customworldpreset.iconLocation);
            CustomizeWorldPresetsScreen.this.textRenderer.draw(customizeworldpresetsscreen$customworldpreset.name, x + 32 + 10, y + 14, 16777215);
        }
    }
}
