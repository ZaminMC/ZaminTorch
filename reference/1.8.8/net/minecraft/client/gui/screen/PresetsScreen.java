package net.minecraft.client.gui.screen;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.FlatWorldGeneratorSettings;
import net.minecraft.world.gen.chunk.FlatWorldLayer;
import org.lwjgl.input.Keyboard;

public class PresetsScreen extends Screen {
    private static final List<PresetsScreen.FlatWorldPreset> PRESETS = Lists.newArrayList();
    private final CustomizeFlatWorldScreen parent;
    private String titleText;
    private String shareText;
    private String listText;
    private PresetsScreen.PresetsListWidget listWidget;
    private ButtonWidget selectButton;
    private TextFieldWidget customPresetField;

    public PresetsScreen(CustomizeFlatWorldScreen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        this.buttons.clear();
        Keyboard.enableRepeatEvents(true);
        this.titleText = I18n.translate("createWorld.customize.presets.title");
        this.shareText = I18n.translate("createWorld.customize.presets.share");
        this.listText = I18n.translate("createWorld.customize.presets.list");
        this.customPresetField = new TextFieldWidget(2, this.textRenderer, 50, 40, this.width - 100, 20);
        this.listWidget = new PresetsScreen.PresetsListWidget();
        this.customPresetField.setMaxLength(1230);
        this.customPresetField.setText(this.parent.getGeneratorOptions());
        this.buttons
            .add(
                this.selectButton = new ButtonWidget(0, this.width / 2 - 155, this.height - 28, 150, 20, I18n.translate("createWorld.customize.presets.select"))
            );
        this.buttons.add(new ButtonWidget(1, this.width / 2 + 5, this.height - 28, 150, 20, I18n.translate("gui.cancel")));
        this.activateSelectButtonIfPresetSelected();
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        this.listWidget.handleMouse();
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        this.customPresetField.mouseClicked(mouseX, mouseY, button);
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (!this.customPresetField.keyPressed(chr, key)) {
            super.keyPressed(chr, key);
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == 0 && this.isPresetSelected()) {
            this.parent.setGeneratorOptions(this.customPresetField.getText());
            this.minecraft.openScreen(this.parent);
        } else if (button.id == 1) {
            this.minecraft.openScreen(this.parent);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.listWidget.render(mouseX, mouseY, tickDelta);
        this.drawCenteredString(this.textRenderer, this.titleText, this.width / 2, 8, 16777215);
        this.drawString(this.textRenderer, this.shareText, 50, 30, 10526880);
        this.drawString(this.textRenderer, this.listText, 50, 70, 10526880);
        this.customPresetField.render();
        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    public void tick() {
        this.customPresetField.tick();
        super.tick();
    }

    public void activateSelectButtonIfPresetSelected() {
        boolean flag = this.isPresetSelected();
        this.selectButton.active = flag;
    }

    /**
     * Returns true if any preset is selected
     */
    private boolean isPresetSelected() {
        return this.listWidget.selectedEntryIndex > -1 && this.listWidget.selectedEntryIndex < PRESETS.size() || this.customPresetField.getText().length() > 1;
    }

    private static void addRedstonePreset(String name, Item icon, Biome iconDamage, FlatWorldLayer... layers) {
        addFlatWorldPreset(name, icon, 0, iconDamage, null, layers);
    }

    private static void addFlatWorldPreset(String presetName, Item item, Biome biome, List<String> features, FlatWorldLayer... layers) {
        addFlatWorldPreset(presetName, item, 0, biome, features, layers);
    }

    private static void addFlatWorldPreset(String name, Item item, int metadata, Biome biome, List<String> features, FlatWorldLayer... layers) {
        FlatWorldGeneratorSettings flatworldgeneratorsettings = new FlatWorldGeneratorSettings();

        for (int i = layers.length - 1; i >= 0; i--) {
            flatworldgeneratorsettings.getLayers().add(layers[i]);
        }

        flatworldgeneratorsettings.setBiome(biome.id);
        flatworldgeneratorsettings.processLayers();
        if (features != null) {
            for (String s : features) {
                flatworldgeneratorsettings.getFeatures().put(s, Maps.newHashMap());
            }
        }

        PRESETS.add(new PresetsScreen.FlatWorldPreset(item, metadata, name, flatworldgeneratorsettings.toString()));
    }

    static {
        addFlatWorldPreset(
            "Classic Flat",
            Item.byBlock(Blocks.GRASS),
            Biome.PLAINS,
            Arrays.asList("village"),
            new FlatWorldLayer(1, Blocks.GRASS),
            new FlatWorldLayer(2, Blocks.DIRT),
            new FlatWorldLayer(1, Blocks.BEDROCK)
        );
        addFlatWorldPreset(
            "Tunnelers' Dream",
            Item.byBlock(Blocks.STONE),
            Biome.EXTREME_HILLS,
            Arrays.asList("biome_1", "dungeon", "decoration", "stronghold", "mineshaft"),
            new FlatWorldLayer(1, Blocks.GRASS),
            new FlatWorldLayer(5, Blocks.DIRT),
            new FlatWorldLayer(230, Blocks.STONE),
            new FlatWorldLayer(1, Blocks.BEDROCK)
        );
        addFlatWorldPreset(
            "Water World",
            Items.WATER_BUCKET,
            Biome.DEEP_OCEAN,
            Arrays.asList("biome_1", "oceanmonument"),
            new FlatWorldLayer(90, Blocks.WATER),
            new FlatWorldLayer(5, Blocks.SAND),
            new FlatWorldLayer(5, Blocks.DIRT),
            new FlatWorldLayer(5, Blocks.STONE),
            new FlatWorldLayer(1, Blocks.BEDROCK)
        );
        addFlatWorldPreset(
            "Overworld",
            Item.byBlock(Blocks.TALLGRASS),
            TallPlantBlock.Type.GRASS.getId(),
            Biome.PLAINS,
            Arrays.asList("village", "biome_1", "decoration", "stronghold", "mineshaft", "dungeon", "lake", "lava_lake"),
            new FlatWorldLayer(1, Blocks.GRASS),
            new FlatWorldLayer(3, Blocks.DIRT),
            new FlatWorldLayer(59, Blocks.STONE),
            new FlatWorldLayer(1, Blocks.BEDROCK)
        );
        addFlatWorldPreset(
            "Snowy Kingdom",
            Item.byBlock(Blocks.SNOW_LAYER),
            Biome.ICE_PLAINS,
            Arrays.asList("village", "biome_1"),
            new FlatWorldLayer(1, Blocks.SNOW_LAYER),
            new FlatWorldLayer(1, Blocks.GRASS),
            new FlatWorldLayer(3, Blocks.DIRT),
            new FlatWorldLayer(59, Blocks.STONE),
            new FlatWorldLayer(1, Blocks.BEDROCK)
        );
        addFlatWorldPreset(
            "Bottomless Pit",
            Items.FEATHER,
            Biome.PLAINS,
            Arrays.asList("village", "biome_1"),
            new FlatWorldLayer(1, Blocks.GRASS),
            new FlatWorldLayer(3, Blocks.DIRT),
            new FlatWorldLayer(2, Blocks.COBBLESTONE)
        );
        addFlatWorldPreset(
            "Desert",
            Item.byBlock(Blocks.SAND),
            Biome.DESERT,
            Arrays.asList("village", "biome_1", "decoration", "stronghold", "mineshaft", "dungeon"),
            new FlatWorldLayer(8, Blocks.SAND),
            new FlatWorldLayer(52, Blocks.SANDSTONE),
            new FlatWorldLayer(3, Blocks.STONE),
            new FlatWorldLayer(1, Blocks.BEDROCK)
        );
        addRedstonePreset(
            "Redstone Ready",
            Items.REDSTONE,
            Biome.DESERT,
            new FlatWorldLayer(52, Blocks.SANDSTONE),
            new FlatWorldLayer(3, Blocks.STONE),
            new FlatWorldLayer(1, Blocks.BEDROCK)
        );
    }

    static class FlatWorldPreset {
        public Item presetItem;
        public int presetItemMetadata;
        public String presetName;
        public String presetLayers;

        public FlatWorldPreset(Item presetItem, int presetItemMetadata, String presetName, String presetLayers) {
            this.presetItem = presetItem;
            this.presetItemMetadata = presetItemMetadata;
            this.presetName = presetName;
            this.presetLayers = presetLayers;
        }
    }

    class PresetsListWidget extends ListWidget {
        public int selectedEntryIndex = -1;

        public PresetsListWidget() {
            super(PresetsScreen.this.minecraft, PresetsScreen.this.width, PresetsScreen.this.height, 80, PresetsScreen.this.height - 37, 24);
        }

        private void renderEntry(int x, int y, Item item, int metadata) {
            this.render(x + 1, y + 1);
            GlStateManager.enableRescaleNormal();
            Lighting.turnOnGui();
            PresetsScreen.this.itemRenderer.renderGuiItemModel(new ItemStack(item, 1, metadata), x + 2, y + 2);
            Lighting.turnOff();
            GlStateManager.disableRescaleNormal();
        }

        private void render(int x, int y) {
            this.render(x, y, 0, 0);
        }

        private void render(int x, int y, int u, int v) {
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.minecraft.getTextureManager().bind(GuiElement.STATS_ICONS_LOCATION);
            float f = 0.0078125F;
            float f1 = 0.0078125F;
            int i = 18;
            int j = 18;
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
            bufferbuilder.vertex(x + 0, y + 18, PresetsScreen.this.drawOffset).texture((u + 0) * 0.0078125F, (v + 18) * 0.0078125F).nextVertex();
            bufferbuilder.vertex(x + 18, y + 18, PresetsScreen.this.drawOffset).texture((u + 18) * 0.0078125F, (v + 18) * 0.0078125F).nextVertex();
            bufferbuilder.vertex(x + 18, y + 0, PresetsScreen.this.drawOffset).texture((u + 18) * 0.0078125F, (v + 0) * 0.0078125F).nextVertex();
            bufferbuilder.vertex(x + 0, y + 0, PresetsScreen.this.drawOffset).texture((u + 0) * 0.0078125F, (v + 0) * 0.0078125F).nextVertex();
            tesselator.end();
        }

        @Override
        protected int size() {
            return PresetsScreen.PRESETS.size();
        }

        @Override
        protected void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            this.selectedEntryIndex = index;
            PresetsScreen.this.activateSelectButtonIfPresetSelected();
            PresetsScreen.this.customPresetField.setText(PresetsScreen.PRESETS.get(PresetsScreen.this.listWidget.selectedEntryIndex).presetLayers);
        }

        @Override
        protected boolean isEntrySelected(int index) {
            return index == this.selectedEntryIndex;
        }

        @Override
        protected void renderBackground() {
        }

        @Override
        protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
            PresetsScreen.FlatWorldPreset presetsscreen$flatworldpreset = PresetsScreen.PRESETS.get(index);
            this.renderEntry(x, y, presetsscreen$flatworldpreset.presetItem, presetsscreen$flatworldpreset.presetItemMetadata);
            PresetsScreen.this.textRenderer.draw(presetsscreen$flatworldpreset.presetName, x + 18 + 5, y + 6, 16777215);
        }
    }
}
