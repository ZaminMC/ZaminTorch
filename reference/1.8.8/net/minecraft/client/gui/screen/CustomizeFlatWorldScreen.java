package net.minecraft.client.gui.screen;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ListWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.gen.chunk.FlatWorldGeneratorSettings;
import net.minecraft.world.gen.chunk.FlatWorldLayer;

public class CustomizeFlatWorldScreen extends Screen {
    private final CreateWorldScreen parent;
    private FlatWorldGeneratorSettings generator = FlatWorldGeneratorSettings.ofDefault();
    private String title;
    private String blockText;
    private String heightText;
    private CustomizeFlatWorldScreen.LayerListWidget layers;
    private ButtonWidget addLayer;
    private ButtonWidget editLayer;
    private ButtonWidget removeLayer;

    public CustomizeFlatWorldScreen(CreateWorldScreen parent, String generatorOptions) {
        this.parent = parent;
        this.setGeneratorOptions(generatorOptions);
    }

    public String getGeneratorOptions() {
        return this.generator.toString();
    }

    public void setGeneratorOptions(String options) {
        this.generator = FlatWorldGeneratorSettings.of(options);
    }

    @Override
    public void init() {
        this.buttons.clear();
        this.title = I18n.translate("createWorld.customize.flat.title");
        this.blockText = I18n.translate("createWorld.customize.flat.tile");
        this.heightText = I18n.translate("createWorld.customize.flat.height");
        this.layers = new CustomizeFlatWorldScreen.LayerListWidget();
        this.buttons
            .add(
                this.addLayer = new ButtonWidget(
                    2, this.width / 2 - 154, this.height - 52, 100, 20, I18n.translate("createWorld.customize.flat.addLayer") + " (NYI)"
                )
            );
        this.buttons
            .add(
                this.editLayer = new ButtonWidget(
                    3, this.width / 2 - 50, this.height - 52, 100, 20, I18n.translate("createWorld.customize.flat.editLayer") + " (NYI)"
                )
            );
        this.buttons
            .add(
                this.removeLayer = new ButtonWidget(
                    4, this.width / 2 - 155, this.height - 52, 150, 20, I18n.translate("createWorld.customize.flat.removeLayer")
                )
            );
        this.buttons.add(new ButtonWidget(0, this.width / 2 - 155, this.height - 28, 150, 20, I18n.translate("gui.done")));
        this.buttons.add(new ButtonWidget(5, this.width / 2 + 5, this.height - 52, 150, 20, I18n.translate("createWorld.customize.presets")));
        this.buttons.add(new ButtonWidget(1, this.width / 2 + 5, this.height - 28, 150, 20, I18n.translate("gui.cancel")));
        this.addLayer.visible = this.editLayer.visible = false;
        this.generator.processLayers();
        this.setActive();
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        this.layers.handleMouse();
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        int i = this.generator.getLayers().size() - this.layers.focusedEntry - 1;
        if (button.id == 1) {
            this.minecraft.openScreen(this.parent);
        } else if (button.id == 0) {
            this.parent.generatorOptions = this.getGeneratorOptions();
            this.minecraft.openScreen(this.parent);
        } else if (button.id == 5) {
            this.minecraft.openScreen(new PresetsScreen(this));
        } else if (button.id == 4 && this.isActive()) {
            this.generator.getLayers().remove(i);
            this.layers.focusedEntry = Math.min(this.layers.focusedEntry, this.generator.getLayers().size() - 1);
        }

        this.generator.processLayers();
        this.setActive();
    }

    public void setActive() {
        boolean flag = this.isActive();
        this.removeLayer.active = flag;
        this.editLayer.active = flag;
        this.editLayer.active = false;
        this.addLayer.active = false;
    }

    private boolean isActive() {
        return this.layers.focusedEntry > -1 && this.layers.focusedEntry < this.generator.getLayers().size();
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.layers.render(mouseX, mouseY, tickDelta);
        this.drawCenteredString(this.textRenderer, this.title, this.width / 2, 8, 16777215);
        int i = this.width / 2 - 92 - 16;
        this.drawString(this.textRenderer, this.blockText, i, 32, 16777215);
        this.drawString(this.textRenderer, this.heightText, i + 2 + 213 - this.textRenderer.getWidth(this.heightText), 32, 16777215);
        super.render(mouseX, mouseY, tickDelta);
    }

    class LayerListWidget extends ListWidget {
        public int focusedEntry = -1;

        public LayerListWidget() {
            super(
                CustomizeFlatWorldScreen.this.minecraft,
                CustomizeFlatWorldScreen.this.width,
                CustomizeFlatWorldScreen.this.height,
                43,
                CustomizeFlatWorldScreen.this.height - 60,
                24
            );
        }

        private void renderIcon(int x, int y, ItemStack item) {
            this.renderIcon(x + 1, y + 1);
            GlStateManager.enableRescaleNormal();
            if (item != null && item.getItem() != null) {
                Lighting.turnOnGui();
                CustomizeFlatWorldScreen.this.itemRenderer.renderGuiItemModel(item, x + 2, y + 2);
                Lighting.turnOff();
            }

            GlStateManager.disableRescaleNormal();
        }

        private void renderIcon(int x, int y) {
            this.renderIcon(x, y, 0, 0);
        }

        private void renderIcon(int x, int y, int u, int v) {
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.minecraft.getTextureManager().bind(GuiElement.STATS_ICONS_LOCATION);
            float f = 0.0078125F;
            float f1 = 0.0078125F;
            int i = 18;
            int j = 18;
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
            bufferbuilder.vertex(x + 0, y + 18, CustomizeFlatWorldScreen.this.drawOffset).texture((u + 0) * 0.0078125F, (v + 18) * 0.0078125F).nextVertex();
            bufferbuilder.vertex(x + 18, y + 18, CustomizeFlatWorldScreen.this.drawOffset).texture((u + 18) * 0.0078125F, (v + 18) * 0.0078125F).nextVertex();
            bufferbuilder.vertex(x + 18, y + 0, CustomizeFlatWorldScreen.this.drawOffset).texture((u + 18) * 0.0078125F, (v + 0) * 0.0078125F).nextVertex();
            bufferbuilder.vertex(x + 0, y + 0, CustomizeFlatWorldScreen.this.drawOffset).texture((u + 0) * 0.0078125F, (v + 0) * 0.0078125F).nextVertex();
            tesselator.end();
        }

        @Override
        protected int size() {
            return CustomizeFlatWorldScreen.this.generator.getLayers().size();
        }

        @Override
        protected void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            this.focusedEntry = index;
            CustomizeFlatWorldScreen.this.setActive();
        }

        @Override
        protected boolean isEntrySelected(int index) {
            return index == this.focusedEntry;
        }

        @Override
        protected void renderBackground() {
        }

        @Override
        protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
            FlatWorldLayer flatworldlayer = CustomizeFlatWorldScreen.this.generator
                .getLayers()
                .get(CustomizeFlatWorldScreen.this.generator.getLayers().size() - index - 1);
            BlockState blockstate = flatworldlayer.getBlockState();
            Block block = blockstate.getBlock();
            Item item = Item.byBlock(block);
            ItemStack itemstack = block != Blocks.AIR && item != null ? new ItemStack(item, 1, block.getMetadataFromState(blockstate)) : null;
            String s = itemstack == null ? "Air" : item.getName(itemstack);
            if (item == null) {
                if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) {
                    item = Items.WATER_BUCKET;
                } else if (block == Blocks.LAVA || block == Blocks.FLOWING_LAVA) {
                    item = Items.LAVA_BUCKET;
                }

                if (item != null) {
                    itemstack = new ItemStack(item, 1, block.getMetadataFromState(blockstate));
                    s = block.getName();
                }
            }

            this.renderIcon(x, y, itemstack);
            CustomizeFlatWorldScreen.this.textRenderer.draw(s, x + 18 + 5, y + 3, 16777215);
            String s1;
            if (index == 0) {
                s1 = I18n.translate("createWorld.customize.flat.layer.top", flatworldlayer.getSize());
            } else if (index == CustomizeFlatWorldScreen.this.generator.getLayers().size() - 1) {
                s1 = I18n.translate("createWorld.customize.flat.layer.bottom", flatworldlayer.getSize());
            } else {
                s1 = I18n.translate("createWorld.customize.flat.layer", flatworldlayer.getSize());
            }

            CustomizeFlatWorldScreen.this.textRenderer.draw(s1, x + 2 + 213 - CustomizeFlatWorldScreen.this.textRenderer.getWidth(s1), y + 3, 16777215);
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width - 70;
        }
    }
}
