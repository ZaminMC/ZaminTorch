package net.minecraft.client.gui.screen.world;

import com.google.common.base.Predicate;
import com.google.common.primitives.Floats;
import java.util.Random;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.OverworldGeneratorOptions;

public class CustomizeWorldScreen extends Screen implements GeneratorOptionSlider.ValueFormatter, OverworldGeneratorOptionsWidget.Controller {
    private CreateWorldScreen parentScreen;
    protected String title = "Customize World Settings";
    protected String pageTitle = "Page 1 of 3";
    protected String pageDescription = "Basic Settings";
    protected String[] pageDescriptions = new String[4];
    private OverworldGeneratorOptionsWidget overworldGeneratorOptions;
    private ButtonWidget doneButton;
    private ButtonWidget randomizeButton;
    private ButtonWidget defaultsButton;
    private ButtonWidget prevPageButton;
    private ButtonWidget nextPageButton;
    private ButtonWidget confirmYesButton;
    private ButtonWidget confirmNoButton;
    private ButtonWidget presetsButton;
    private boolean defaultsButtonActive = false;
    private int queuedButtonId = 0;
    private boolean skipNextClick = false;
    private Predicate<String> floatValidator = new Predicate<String>() {
        public boolean apply(String string) {
            Float f = Floats.tryParse(string);
            return string.length() == 0 || f != null && Floats.isFinite(f) && f >= 0.0F;
        }
    };
    private OverworldGeneratorOptions.Builder defaultOverworldGeneratorOptions = new OverworldGeneratorOptions.Builder();
    private OverworldGeneratorOptions.Builder selectedOverworldGeneratorOptions;
    private Random random = new Random();

    public CustomizeWorldScreen(Screen parent, String overworldGeneratorOptions) {
        this.parentScreen = (CreateWorldScreen)parent;
        this.selectOverworldGeneratorOptions(overworldGeneratorOptions);
    }

    @Override
    public void init() {
        int i = 0;
        int j = 0;
        if (this.overworldGeneratorOptions != null) {
            i = this.overworldGeneratorOptions.getPage();
            j = this.overworldGeneratorOptions.getScrollAmount();
        }

        this.title = I18n.translate("options.customizeTitle");
        this.buttons.clear();
        this.buttons.add(this.prevPageButton = new ButtonWidget(302, 20, 5, 80, 20, I18n.translate("createWorld.customize.custom.prev")));
        this.buttons.add(this.nextPageButton = new ButtonWidget(303, this.width - 100, 5, 80, 20, I18n.translate("createWorld.customize.custom.next")));
        this.buttons
            .add(
                this.defaultsButton = new ButtonWidget(
                    304, this.width / 2 - 187, this.height - 27, 90, 20, I18n.translate("createWorld.customize.custom.defaults")
                )
            );
        this.buttons
            .add(
                this.randomizeButton = new ButtonWidget(
                    301, this.width / 2 - 92, this.height - 27, 90, 20, I18n.translate("createWorld.customize.custom.randomize")
                )
            );
        this.buttons
            .add(
                this.presetsButton = new ButtonWidget(305, this.width / 2 + 3, this.height - 27, 90, 20, I18n.translate("createWorld.customize.custom.presets"))
            );
        this.buttons.add(this.doneButton = new ButtonWidget(300, this.width / 2 + 98, this.height - 27, 90, 20, I18n.translate("gui.done")));
        this.defaultsButton.active = this.defaultsButtonActive;
        this.confirmYesButton = new ButtonWidget(306, this.width / 2 - 55, 160, 50, 20, I18n.translate("gui.yes"));
        this.confirmYesButton.visible = false;
        this.buttons.add(this.confirmYesButton);
        this.confirmNoButton = new ButtonWidget(307, this.width / 2 + 5, 160, 50, 20, I18n.translate("gui.no"));
        this.confirmNoButton.visible = false;
        this.buttons.add(this.confirmNoButton);
        if (this.queuedButtonId != 0) {
            this.confirmYesButton.visible = true;
            this.confirmNoButton.visible = true;
        }

        this.updateOverworldGeneratorOptions();
        if (i != 0) {
            this.overworldGeneratorOptions.selectPage(i);
            this.overworldGeneratorOptions.scroll(j);
            this.updatePageIndicators();
        }
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        this.overworldGeneratorOptions.handleMouse();
    }

    private void updateOverworldGeneratorOptions() {
        OverworldGeneratorOptionsWidget.GeneratorOption[] aoverworldgeneratoroptionswidget$generatoroption = new OverworldGeneratorOptionsWidget.GeneratorOption[]{
            new OverworldGeneratorOptionsWidget.NumberOption(
                160, I18n.translate("createWorld.customize.custom.seaLevel"), true, this, 1.0F, 255.0F, this.selectedOverworldGeneratorOptions.seaLevel
            ),
            new OverworldGeneratorOptionsWidget.ToggleOption(
                148, I18n.translate("createWorld.customize.custom.useCaves"), true, this.selectedOverworldGeneratorOptions.useCaves
            ),
            new OverworldGeneratorOptionsWidget.ToggleOption(
                150, I18n.translate("createWorld.customize.custom.useStrongholds"), true, this.selectedOverworldGeneratorOptions.useStrongholds
            ),
            new OverworldGeneratorOptionsWidget.ToggleOption(
                151, I18n.translate("createWorld.customize.custom.useVillages"), true, this.selectedOverworldGeneratorOptions.useVillages
            ),
            new OverworldGeneratorOptionsWidget.ToggleOption(
                152, I18n.translate("createWorld.customize.custom.useMineShafts"), true, this.selectedOverworldGeneratorOptions.useMineshafts
            ),
            new OverworldGeneratorOptionsWidget.ToggleOption(
                153, I18n.translate("createWorld.customize.custom.useTemples"), true, this.selectedOverworldGeneratorOptions.useTemples
            ),
            new OverworldGeneratorOptionsWidget.ToggleOption(
                210, I18n.translate("createWorld.customize.custom.useMonuments"), true, this.selectedOverworldGeneratorOptions.useMonuments
            ),
            new OverworldGeneratorOptionsWidget.ToggleOption(
                154, I18n.translate("createWorld.customize.custom.useRavines"), true, this.selectedOverworldGeneratorOptions.useRavines
            ),
            new OverworldGeneratorOptionsWidget.ToggleOption(
                149, I18n.translate("createWorld.customize.custom.useDungeons"), true, this.selectedOverworldGeneratorOptions.useDungeons
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                157,
                I18n.translate("createWorld.customize.custom.dungeonChance"),
                true,
                this,
                1.0F,
                100.0F,
                this.selectedOverworldGeneratorOptions.dungeonChance
            ),
            new OverworldGeneratorOptionsWidget.ToggleOption(
                155, I18n.translate("createWorld.customize.custom.useWaterLakes"), true, this.selectedOverworldGeneratorOptions.useWaterLakes
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                158,
                I18n.translate("createWorld.customize.custom.waterLakeChance"),
                true,
                this,
                1.0F,
                100.0F,
                this.selectedOverworldGeneratorOptions.waterLakeChance
            ),
            new OverworldGeneratorOptionsWidget.ToggleOption(
                156, I18n.translate("createWorld.customize.custom.useLavaLakes"), true, this.selectedOverworldGeneratorOptions.useLavaLakes
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                159,
                I18n.translate("createWorld.customize.custom.lavaLakeChance"),
                true,
                this,
                10.0F,
                100.0F,
                this.selectedOverworldGeneratorOptions.lavaLakeChance
            ),
            new OverworldGeneratorOptionsWidget.ToggleOption(
                161, I18n.translate("createWorld.customize.custom.useLavaOceans"), true, this.selectedOverworldGeneratorOptions.useLavaOceans
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                162, I18n.translate("createWorld.customize.custom.fixedBiome"), true, this, -1.0F, 37.0F, this.selectedOverworldGeneratorOptions.fixedBiome
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                163, I18n.translate("createWorld.customize.custom.biomeSize"), true, this, 1.0F, 8.0F, this.selectedOverworldGeneratorOptions.biomeSize
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                164, I18n.translate("createWorld.customize.custom.riverSize"), true, this, 1.0F, 5.0F, this.selectedOverworldGeneratorOptions.riverSize
            )
        };
        OverworldGeneratorOptionsWidget.GeneratorOption[] aoverworldgeneratoroptionswidget$generatoroption1 = new OverworldGeneratorOptionsWidget.GeneratorOption[]{
            new OverworldGeneratorOptionsWidget.Label(416, I18n.translate("tile.dirt.name"), false),
            null,
            new OverworldGeneratorOptionsWidget.NumberOption(
                165, I18n.translate("createWorld.customize.custom.size"), false, this, 1.0F, 50.0F, this.selectedOverworldGeneratorOptions.dirtSize
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                166, I18n.translate("createWorld.customize.custom.count"), false, this, 0.0F, 40.0F, this.selectedOverworldGeneratorOptions.dirtCount
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                167, I18n.translate("createWorld.customize.custom.minHeight"), false, this, 0.0F, 255.0F, this.selectedOverworldGeneratorOptions.dirtMinHeight
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                168, I18n.translate("createWorld.customize.custom.maxHeight"), false, this, 0.0F, 255.0F, this.selectedOverworldGeneratorOptions.dirtMaxHeight
            ),
            new OverworldGeneratorOptionsWidget.Label(417, I18n.translate("tile.gravel.name"), false),
            null,
            new OverworldGeneratorOptionsWidget.NumberOption(
                169, I18n.translate("createWorld.customize.custom.size"), false, this, 1.0F, 50.0F, this.selectedOverworldGeneratorOptions.gravelSize
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                170, I18n.translate("createWorld.customize.custom.count"), false, this, 0.0F, 40.0F, this.selectedOverworldGeneratorOptions.gravelCount
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                171,
                I18n.translate("createWorld.customize.custom.minHeight"),
                false,
                this,
                0.0F,
                255.0F,
                this.selectedOverworldGeneratorOptions.gravelMinHeight
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                172,
                I18n.translate("createWorld.customize.custom.maxHeight"),
                false,
                this,
                0.0F,
                255.0F,
                this.selectedOverworldGeneratorOptions.gravelMaxHeight
            ),
            new OverworldGeneratorOptionsWidget.Label(418, I18n.translate("tile.stone.granite.name"), false),
            null,
            new OverworldGeneratorOptionsWidget.NumberOption(
                173, I18n.translate("createWorld.customize.custom.size"), false, this, 1.0F, 50.0F, this.selectedOverworldGeneratorOptions.graniteSize
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                174, I18n.translate("createWorld.customize.custom.count"), false, this, 0.0F, 40.0F, this.selectedOverworldGeneratorOptions.graniteCount
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                175,
                I18n.translate("createWorld.customize.custom.minHeight"),
                false,
                this,
                0.0F,
                255.0F,
                this.selectedOverworldGeneratorOptions.graniteMinHeight
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                176,
                I18n.translate("createWorld.customize.custom.maxHeight"),
                false,
                this,
                0.0F,
                255.0F,
                this.selectedOverworldGeneratorOptions.graniteMaxHeight
            ),
            new OverworldGeneratorOptionsWidget.Label(419, I18n.translate("tile.stone.diorite.name"), false),
            null,
            new OverworldGeneratorOptionsWidget.NumberOption(
                177, I18n.translate("createWorld.customize.custom.size"), false, this, 1.0F, 50.0F, this.selectedOverworldGeneratorOptions.dioriteSize
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                178, I18n.translate("createWorld.customize.custom.count"), false, this, 0.0F, 40.0F, this.selectedOverworldGeneratorOptions.dioriteCount
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                179,
                I18n.translate("createWorld.customize.custom.minHeight"),
                false,
                this,
                0.0F,
                255.0F,
                this.selectedOverworldGeneratorOptions.dioriteMinHeight
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                180,
                I18n.translate("createWorld.customize.custom.maxHeight"),
                false,
                this,
                0.0F,
                255.0F,
                this.selectedOverworldGeneratorOptions.dioriteMaxHeight
            ),
            new OverworldGeneratorOptionsWidget.Label(420, I18n.translate("tile.stone.andesite.name"), false),
            null,
            new OverworldGeneratorOptionsWidget.NumberOption(
                181, I18n.translate("createWorld.customize.custom.size"), false, this, 1.0F, 50.0F, this.selectedOverworldGeneratorOptions.andesiteSize
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                182, I18n.translate("createWorld.customize.custom.count"), false, this, 0.0F, 40.0F, this.selectedOverworldGeneratorOptions.andesiteCount
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                183,
                I18n.translate("createWorld.customize.custom.minHeight"),
                false,
                this,
                0.0F,
                255.0F,
                this.selectedOverworldGeneratorOptions.andesiteMinHeight
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                184,
                I18n.translate("createWorld.customize.custom.maxHeight"),
                false,
                this,
                0.0F,
                255.0F,
                this.selectedOverworldGeneratorOptions.andesiteMaxHeight
            ),
            new OverworldGeneratorOptionsWidget.Label(421, I18n.translate("tile.oreCoal.name"), false),
            null,
            new OverworldGeneratorOptionsWidget.NumberOption(
                185, I18n.translate("createWorld.customize.custom.size"), false, this, 1.0F, 50.0F, this.selectedOverworldGeneratorOptions.coalSize
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                186, I18n.translate("createWorld.customize.custom.count"), false, this, 0.0F, 40.0F, this.selectedOverworldGeneratorOptions.coalCount
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                187, I18n.translate("createWorld.customize.custom.minHeight"), false, this, 0.0F, 255.0F, this.selectedOverworldGeneratorOptions.coalMinHeight
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                189, I18n.translate("createWorld.customize.custom.maxHeight"), false, this, 0.0F, 255.0F, this.selectedOverworldGeneratorOptions.coalMaxHeight
            ),
            new OverworldGeneratorOptionsWidget.Label(422, I18n.translate("tile.oreIron.name"), false),
            null,
            new OverworldGeneratorOptionsWidget.NumberOption(
                190, I18n.translate("createWorld.customize.custom.size"), false, this, 1.0F, 50.0F, this.selectedOverworldGeneratorOptions.ironSize
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                191, I18n.translate("createWorld.customize.custom.count"), false, this, 0.0F, 40.0F, this.selectedOverworldGeneratorOptions.ironCount
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                192, I18n.translate("createWorld.customize.custom.minHeight"), false, this, 0.0F, 255.0F, this.selectedOverworldGeneratorOptions.ironMinHeight
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                193, I18n.translate("createWorld.customize.custom.maxHeight"), false, this, 0.0F, 255.0F, this.selectedOverworldGeneratorOptions.ironMaxHeight
            ),
            new OverworldGeneratorOptionsWidget.Label(423, I18n.translate("tile.oreGold.name"), false),
            null,
            new OverworldGeneratorOptionsWidget.NumberOption(
                194, I18n.translate("createWorld.customize.custom.size"), false, this, 1.0F, 50.0F, this.selectedOverworldGeneratorOptions.goldSize
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                195, I18n.translate("createWorld.customize.custom.count"), false, this, 0.0F, 40.0F, this.selectedOverworldGeneratorOptions.goldCount
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                196, I18n.translate("createWorld.customize.custom.minHeight"), false, this, 0.0F, 255.0F, this.selectedOverworldGeneratorOptions.goldMinHeight
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                197, I18n.translate("createWorld.customize.custom.maxHeight"), false, this, 0.0F, 255.0F, this.selectedOverworldGeneratorOptions.goldMaxHeight
            ),
            new OverworldGeneratorOptionsWidget.Label(424, I18n.translate("tile.oreRedstone.name"), false),
            null,
            new OverworldGeneratorOptionsWidget.NumberOption(
                198, I18n.translate("createWorld.customize.custom.size"), false, this, 1.0F, 50.0F, this.selectedOverworldGeneratorOptions.redstoneSize
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                199, I18n.translate("createWorld.customize.custom.count"), false, this, 0.0F, 40.0F, this.selectedOverworldGeneratorOptions.redstoneCount
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                200,
                I18n.translate("createWorld.customize.custom.minHeight"),
                false,
                this,
                0.0F,
                255.0F,
                this.selectedOverworldGeneratorOptions.redstoneMinHeight
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                201,
                I18n.translate("createWorld.customize.custom.maxHeight"),
                false,
                this,
                0.0F,
                255.0F,
                this.selectedOverworldGeneratorOptions.redstoneMaxHeight
            ),
            new OverworldGeneratorOptionsWidget.Label(425, I18n.translate("tile.oreDiamond.name"), false),
            null,
            new OverworldGeneratorOptionsWidget.NumberOption(
                202, I18n.translate("createWorld.customize.custom.size"), false, this, 1.0F, 50.0F, this.selectedOverworldGeneratorOptions.diamondSize
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                203, I18n.translate("createWorld.customize.custom.count"), false, this, 0.0F, 40.0F, this.selectedOverworldGeneratorOptions.diamondCount
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                204,
                I18n.translate("createWorld.customize.custom.minHeight"),
                false,
                this,
                0.0F,
                255.0F,
                this.selectedOverworldGeneratorOptions.diamondMinHeight
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                205,
                I18n.translate("createWorld.customize.custom.maxHeight"),
                false,
                this,
                0.0F,
                255.0F,
                this.selectedOverworldGeneratorOptions.diamondMaxHeight
            ),
            new OverworldGeneratorOptionsWidget.Label(426, I18n.translate("tile.oreLapis.name"), false),
            null,
            new OverworldGeneratorOptionsWidget.NumberOption(
                206, I18n.translate("createWorld.customize.custom.size"), false, this, 1.0F, 50.0F, this.selectedOverworldGeneratorOptions.lapisSize
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                207, I18n.translate("createWorld.customize.custom.count"), false, this, 0.0F, 40.0F, this.selectedOverworldGeneratorOptions.lapisCount
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                208, I18n.translate("createWorld.customize.custom.center"), false, this, 0.0F, 255.0F, this.selectedOverworldGeneratorOptions.lapisMinHeight
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                209, I18n.translate("createWorld.customize.custom.spread"), false, this, 0.0F, 255.0F, this.selectedOverworldGeneratorOptions.lapisMaxHeight
            )
        };
        OverworldGeneratorOptionsWidget.GeneratorOption[] aoverworldgeneratoroptionswidget$generatoroption2 = new OverworldGeneratorOptionsWidget.GeneratorOption[]{
            new OverworldGeneratorOptionsWidget.NumberOption(
                100,
                I18n.translate("createWorld.customize.custom.mainNoiseScaleX"),
                false,
                this,
                1.0F,
                5000.0F,
                this.selectedOverworldGeneratorOptions.mainNoiseScaleX
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                101,
                I18n.translate("createWorld.customize.custom.mainNoiseScaleY"),
                false,
                this,
                1.0F,
                5000.0F,
                this.selectedOverworldGeneratorOptions.mainNoiseScaleY
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                102,
                I18n.translate("createWorld.customize.custom.mainNoiseScaleZ"),
                false,
                this,
                1.0F,
                5000.0F,
                this.selectedOverworldGeneratorOptions.mainNoiseScaleZ
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                103,
                I18n.translate("createWorld.customize.custom.depthNoiseScaleX"),
                false,
                this,
                1.0F,
                2000.0F,
                this.selectedOverworldGeneratorOptions.depthNoisescaleX
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                104,
                I18n.translate("createWorld.customize.custom.depthNoiseScaleZ"),
                false,
                this,
                1.0F,
                2000.0F,
                this.selectedOverworldGeneratorOptions.depthNoiseScaleZ
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                105,
                I18n.translate("createWorld.customize.custom.depthNoiseScaleExponent"),
                false,
                this,
                0.01F,
                20.0F,
                this.selectedOverworldGeneratorOptions.depthNoiseScaleExponent
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                106, I18n.translate("createWorld.customize.custom.baseSize"), false, this, 1.0F, 25.0F, this.selectedOverworldGeneratorOptions.baseSize
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                107,
                I18n.translate("createWorld.customize.custom.coordinateScale"),
                false,
                this,
                1.0F,
                6000.0F,
                this.selectedOverworldGeneratorOptions.coordinateScale
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                108, I18n.translate("createWorld.customize.custom.heightScale"), false, this, 1.0F, 6000.0F, this.selectedOverworldGeneratorOptions.heightScale
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                109, I18n.translate("createWorld.customize.custom.stretchY"), false, this, 0.01F, 50.0F, this.selectedOverworldGeneratorOptions.stretchY
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                110,
                I18n.translate("createWorld.customize.custom.upperLimitScale"),
                false,
                this,
                1.0F,
                5000.0F,
                this.selectedOverworldGeneratorOptions.upperLimitScale
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                111,
                I18n.translate("createWorld.customize.custom.lowerLimitScale"),
                false,
                this,
                1.0F,
                5000.0F,
                this.selectedOverworldGeneratorOptions.lowerLimitScale
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                112,
                I18n.translate("createWorld.customize.custom.biomeDepthWeight"),
                false,
                this,
                1.0F,
                20.0F,
                this.selectedOverworldGeneratorOptions.biomeDepthWeight
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                113,
                I18n.translate("createWorld.customize.custom.biomeDepthOffset"),
                false,
                this,
                0.0F,
                20.0F,
                this.selectedOverworldGeneratorOptions.biomeDepthOffset
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                114,
                I18n.translate("createWorld.customize.custom.biomeScaleWeight"),
                false,
                this,
                1.0F,
                20.0F,
                this.selectedOverworldGeneratorOptions.biomeScaleWeight
            ),
            new OverworldGeneratorOptionsWidget.NumberOption(
                115,
                I18n.translate("createWorld.customize.custom.biomeScaleOffset"),
                false,
                this,
                0.0F,
                20.0F,
                this.selectedOverworldGeneratorOptions.biomeScaleOffset
            )
        };
        OverworldGeneratorOptionsWidget.GeneratorOption[] aoverworldgeneratoroptionswidget$generatoroption3 = new OverworldGeneratorOptionsWidget.GeneratorOption[]{
            new OverworldGeneratorOptionsWidget.Label(400, I18n.translate("createWorld.customize.custom.mainNoiseScaleX") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                132, String.format("%5.3f", this.selectedOverworldGeneratorOptions.mainNoiseScaleX), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(401, I18n.translate("createWorld.customize.custom.mainNoiseScaleY") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                133, String.format("%5.3f", this.selectedOverworldGeneratorOptions.mainNoiseScaleY), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(402, I18n.translate("createWorld.customize.custom.mainNoiseScaleZ") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                134, String.format("%5.3f", this.selectedOverworldGeneratorOptions.mainNoiseScaleZ), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(403, I18n.translate("createWorld.customize.custom.depthNoiseScaleX") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                135, String.format("%5.3f", this.selectedOverworldGeneratorOptions.depthNoisescaleX), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(404, I18n.translate("createWorld.customize.custom.depthNoiseScaleZ") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                136, String.format("%5.3f", this.selectedOverworldGeneratorOptions.depthNoiseScaleZ), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(405, I18n.translate("createWorld.customize.custom.depthNoiseScaleExponent") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                137, String.format("%2.3f", this.selectedOverworldGeneratorOptions.depthNoiseScaleExponent), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(406, I18n.translate("createWorld.customize.custom.baseSize") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                138, String.format("%2.3f", this.selectedOverworldGeneratorOptions.baseSize), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(407, I18n.translate("createWorld.customize.custom.coordinateScale") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                139, String.format("%5.3f", this.selectedOverworldGeneratorOptions.coordinateScale), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(408, I18n.translate("createWorld.customize.custom.heightScale") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                140, String.format("%5.3f", this.selectedOverworldGeneratorOptions.heightScale), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(409, I18n.translate("createWorld.customize.custom.stretchY") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                141, String.format("%2.3f", this.selectedOverworldGeneratorOptions.stretchY), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(410, I18n.translate("createWorld.customize.custom.upperLimitScale") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                142, String.format("%5.3f", this.selectedOverworldGeneratorOptions.upperLimitScale), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(411, I18n.translate("createWorld.customize.custom.lowerLimitScale") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                143, String.format("%5.3f", this.selectedOverworldGeneratorOptions.lowerLimitScale), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(412, I18n.translate("createWorld.customize.custom.biomeDepthWeight") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                144, String.format("%2.3f", this.selectedOverworldGeneratorOptions.biomeDepthWeight), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(413, I18n.translate("createWorld.customize.custom.biomeDepthOffset") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                145, String.format("%2.3f", this.selectedOverworldGeneratorOptions.biomeDepthOffset), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(414, I18n.translate("createWorld.customize.custom.biomeScaleWeight") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                146, String.format("%2.3f", this.selectedOverworldGeneratorOptions.biomeScaleWeight), false, this.floatValidator
            ),
            new OverworldGeneratorOptionsWidget.Label(415, I18n.translate("createWorld.customize.custom.biomeScaleOffset") + ":", false),
            new OverworldGeneratorOptionsWidget.TextOption(
                147, String.format("%2.3f", this.selectedOverworldGeneratorOptions.biomeScaleOffset), false, this.floatValidator
            )
        };
        this.overworldGeneratorOptions = new OverworldGeneratorOptionsWidget(
            this.minecraft,
            this.width,
            this.height,
            32,
            this.height - 32,
            25,
            this,
            aoverworldgeneratoroptionswidget$generatoroption,
            aoverworldgeneratoroptionswidget$generatoroption1,
            aoverworldgeneratoroptionswidget$generatoroption2,
            aoverworldgeneratoroptionswidget$generatoroption3
        );

        for (int i = 0; i < 4; i++) {
            this.pageDescriptions[i] = I18n.translate("createWorld.customize.custom.page" + i);
        }

        this.updatePageIndicators();
    }

    public String getOverworldGeneratorOptions() {
        return this.selectedOverworldGeneratorOptions.toString().replace("\n", "");
    }

    public void selectOverworldGeneratorOptions(String generatorOptions) {
        if (generatorOptions != null && generatorOptions.length() != 0) {
            this.selectedOverworldGeneratorOptions = OverworldGeneratorOptions.Builder.fromJson(generatorOptions);
        } else {
            this.selectedOverworldGeneratorOptions = new OverworldGeneratorOptions.Builder();
        }
    }

    @Override
    public void setValue(int id, String value) {
        float f = 0.0F;

        try {
            f = Float.parseFloat(value);
        } catch (NumberFormatException numberformatexception) {
        }

        float f1 = 0.0F;
        switch (id) {
            case 132:
                f1 = this.selectedOverworldGeneratorOptions.mainNoiseScaleX = MathHelper.clamp(f, 1.0F, 5000.0F);
                break;
            case 133:
                f1 = this.selectedOverworldGeneratorOptions.mainNoiseScaleY = MathHelper.clamp(f, 1.0F, 5000.0F);
                break;
            case 134:
                f1 = this.selectedOverworldGeneratorOptions.mainNoiseScaleZ = MathHelper.clamp(f, 1.0F, 5000.0F);
                break;
            case 135:
                f1 = this.selectedOverworldGeneratorOptions.depthNoisescaleX = MathHelper.clamp(f, 1.0F, 2000.0F);
                break;
            case 136:
                f1 = this.selectedOverworldGeneratorOptions.depthNoiseScaleZ = MathHelper.clamp(f, 1.0F, 2000.0F);
                break;
            case 137:
                f1 = this.selectedOverworldGeneratorOptions.depthNoiseScaleExponent = MathHelper.clamp(f, 0.01F, 20.0F);
                break;
            case 138:
                f1 = this.selectedOverworldGeneratorOptions.baseSize = MathHelper.clamp(f, 1.0F, 25.0F);
                break;
            case 139:
                f1 = this.selectedOverworldGeneratorOptions.coordinateScale = MathHelper.clamp(f, 1.0F, 6000.0F);
                break;
            case 140:
                f1 = this.selectedOverworldGeneratorOptions.heightScale = MathHelper.clamp(f, 1.0F, 6000.0F);
                break;
            case 141:
                f1 = this.selectedOverworldGeneratorOptions.stretchY = MathHelper.clamp(f, 0.01F, 50.0F);
                break;
            case 142:
                f1 = this.selectedOverworldGeneratorOptions.upperLimitScale = MathHelper.clamp(f, 1.0F, 5000.0F);
                break;
            case 143:
                f1 = this.selectedOverworldGeneratorOptions.lowerLimitScale = MathHelper.clamp(f, 1.0F, 5000.0F);
                break;
            case 144:
                f1 = this.selectedOverworldGeneratorOptions.biomeDepthWeight = MathHelper.clamp(f, 1.0F, 20.0F);
                break;
            case 145:
                f1 = this.selectedOverworldGeneratorOptions.biomeDepthOffset = MathHelper.clamp(f, 0.0F, 20.0F);
                break;
            case 146:
                f1 = this.selectedOverworldGeneratorOptions.biomeScaleWeight = MathHelper.clamp(f, 1.0F, 20.0F);
                break;
            case 147:
                f1 = this.selectedOverworldGeneratorOptions.biomeScaleOffset = MathHelper.clamp(f, 0.0F, 20.0F);
        }

        if (f1 != f && f != 0.0F) {
            ((TextFieldWidget)this.overworldGeneratorOptions.getElement(id)).setText(this.formatValue(id, f1));
        }

        ((GeneratorOptionSlider)this.overworldGeneratorOptions.getElement(id - 132 + 100)).setValue(f1, false);
        if (!this.selectedOverworldGeneratorOptions.equals(this.defaultOverworldGeneratorOptions)) {
            this.updateDefaultsButton(true);
        }
    }

    private void updateDefaultsButton(boolean active) {
        this.defaultsButtonActive = active;
        this.defaultsButton.active = active;
    }

    @Override
    public String formatValue(int id, String message, float value) {
        return message + ": " + this.formatValue(id, value);
    }

    private String formatValue(int id, float value) {
        switch (id) {
            case 100:
            case 101:
            case 102:
            case 103:
            case 104:
            case 107:
            case 108:
            case 110:
            case 111:
            case 132:
            case 133:
            case 134:
            case 135:
            case 136:
            case 139:
            case 140:
            case 142:
            case 143:
                return String.format("%5.3f", value);
            case 105:
            case 106:
            case 109:
            case 112:
            case 113:
            case 114:
            case 115:
            case 137:
            case 138:
            case 141:
            case 144:
            case 145:
            case 146:
            case 147:
                return String.format("%2.3f", value);
            case 116:
            case 117:
            case 118:
            case 119:
            case 120:
            case 121:
            case 122:
            case 123:
            case 124:
            case 125:
            case 126:
            case 127:
            case 128:
            case 129:
            case 130:
            case 131:
            case 148:
            case 149:
            case 150:
            case 151:
            case 152:
            case 153:
            case 154:
            case 155:
            case 156:
            case 157:
            case 158:
            case 159:
            case 160:
            case 161:
            default:
                return String.format("%d", (int)value);
            case 162:
                if (value < 0.0F) {
                    return I18n.translate("gui.all");
                } else if ((int)value >= Biome.HELL.id) {
                    Biome biome1 = Biome.getAll()[(int)value + 2];
                    return biome1 != null ? biome1.name : "?";
                } else {
                    Biome biome = Biome.getAll()[(int)value];
                    return biome != null ? biome.name : "?";
                }
        }
    }

    @Override
    public void setValue(int id, boolean value) {
        switch (id) {
            case 148:
                this.selectedOverworldGeneratorOptions.useCaves = value;
                break;
            case 149:
                this.selectedOverworldGeneratorOptions.useDungeons = value;
                break;
            case 150:
                this.selectedOverworldGeneratorOptions.useStrongholds = value;
                break;
            case 151:
                this.selectedOverworldGeneratorOptions.useVillages = value;
                break;
            case 152:
                this.selectedOverworldGeneratorOptions.useMineshafts = value;
                break;
            case 153:
                this.selectedOverworldGeneratorOptions.useTemples = value;
                break;
            case 154:
                this.selectedOverworldGeneratorOptions.useRavines = value;
                break;
            case 155:
                this.selectedOverworldGeneratorOptions.useWaterLakes = value;
                break;
            case 156:
                this.selectedOverworldGeneratorOptions.useLavaLakes = value;
                break;
            case 161:
                this.selectedOverworldGeneratorOptions.useLavaOceans = value;
                break;
            case 210:
                this.selectedOverworldGeneratorOptions.useMonuments = value;
        }

        if (!this.selectedOverworldGeneratorOptions.equals(this.defaultOverworldGeneratorOptions)) {
            this.updateDefaultsButton(true);
        }
    }

    @Override
    public void setValue(int id, float value) {
        switch (id) {
            case 100:
                this.selectedOverworldGeneratorOptions.mainNoiseScaleX = value;
                break;
            case 101:
                this.selectedOverworldGeneratorOptions.mainNoiseScaleY = value;
                break;
            case 102:
                this.selectedOverworldGeneratorOptions.mainNoiseScaleZ = value;
                break;
            case 103:
                this.selectedOverworldGeneratorOptions.depthNoisescaleX = value;
                break;
            case 104:
                this.selectedOverworldGeneratorOptions.depthNoiseScaleZ = value;
                break;
            case 105:
                this.selectedOverworldGeneratorOptions.depthNoiseScaleExponent = value;
                break;
            case 106:
                this.selectedOverworldGeneratorOptions.baseSize = value;
                break;
            case 107:
                this.selectedOverworldGeneratorOptions.coordinateScale = value;
                break;
            case 108:
                this.selectedOverworldGeneratorOptions.heightScale = value;
                break;
            case 109:
                this.selectedOverworldGeneratorOptions.stretchY = value;
                break;
            case 110:
                this.selectedOverworldGeneratorOptions.upperLimitScale = value;
                break;
            case 111:
                this.selectedOverworldGeneratorOptions.lowerLimitScale = value;
                break;
            case 112:
                this.selectedOverworldGeneratorOptions.biomeDepthWeight = value;
                break;
            case 113:
                this.selectedOverworldGeneratorOptions.biomeDepthOffset = value;
                break;
            case 114:
                this.selectedOverworldGeneratorOptions.biomeScaleWeight = value;
                break;
            case 115:
                this.selectedOverworldGeneratorOptions.biomeScaleOffset = value;
            case 116:
            case 117:
            case 118:
            case 119:
            case 120:
            case 121:
            case 122:
            case 123:
            case 124:
            case 125:
            case 126:
            case 127:
            case 128:
            case 129:
            case 130:
            case 131:
            case 132:
            case 133:
            case 134:
            case 135:
            case 136:
            case 137:
            case 138:
            case 139:
            case 140:
            case 141:
            case 142:
            case 143:
            case 144:
            case 145:
            case 146:
            case 147:
            case 148:
            case 149:
            case 150:
            case 151:
            case 152:
            case 153:
            case 154:
            case 155:
            case 156:
            case 161:
            case 188:
            default:
                break;
            case 157:
                this.selectedOverworldGeneratorOptions.dungeonChance = (int)value;
                break;
            case 158:
                this.selectedOverworldGeneratorOptions.waterLakeChance = (int)value;
                break;
            case 159:
                this.selectedOverworldGeneratorOptions.lavaLakeChance = (int)value;
                break;
            case 160:
                this.selectedOverworldGeneratorOptions.seaLevel = (int)value;
                break;
            case 162:
                this.selectedOverworldGeneratorOptions.fixedBiome = (int)value;
                break;
            case 163:
                this.selectedOverworldGeneratorOptions.biomeSize = (int)value;
                break;
            case 164:
                this.selectedOverworldGeneratorOptions.riverSize = (int)value;
                break;
            case 165:
                this.selectedOverworldGeneratorOptions.dirtSize = (int)value;
                break;
            case 166:
                this.selectedOverworldGeneratorOptions.dirtCount = (int)value;
                break;
            case 167:
                this.selectedOverworldGeneratorOptions.dirtMinHeight = (int)value;
                break;
            case 168:
                this.selectedOverworldGeneratorOptions.dirtMaxHeight = (int)value;
                break;
            case 169:
                this.selectedOverworldGeneratorOptions.gravelSize = (int)value;
                break;
            case 170:
                this.selectedOverworldGeneratorOptions.gravelCount = (int)value;
                break;
            case 171:
                this.selectedOverworldGeneratorOptions.gravelMinHeight = (int)value;
                break;
            case 172:
                this.selectedOverworldGeneratorOptions.gravelMaxHeight = (int)value;
                break;
            case 173:
                this.selectedOverworldGeneratorOptions.graniteSize = (int)value;
                break;
            case 174:
                this.selectedOverworldGeneratorOptions.graniteCount = (int)value;
                break;
            case 175:
                this.selectedOverworldGeneratorOptions.graniteMinHeight = (int)value;
                break;
            case 176:
                this.selectedOverworldGeneratorOptions.graniteMaxHeight = (int)value;
                break;
            case 177:
                this.selectedOverworldGeneratorOptions.dioriteSize = (int)value;
                break;
            case 178:
                this.selectedOverworldGeneratorOptions.dioriteCount = (int)value;
                break;
            case 179:
                this.selectedOverworldGeneratorOptions.dioriteMinHeight = (int)value;
                break;
            case 180:
                this.selectedOverworldGeneratorOptions.dioriteMaxHeight = (int)value;
                break;
            case 181:
                this.selectedOverworldGeneratorOptions.andesiteSize = (int)value;
                break;
            case 182:
                this.selectedOverworldGeneratorOptions.andesiteCount = (int)value;
                break;
            case 183:
                this.selectedOverworldGeneratorOptions.andesiteMinHeight = (int)value;
                break;
            case 184:
                this.selectedOverworldGeneratorOptions.andesiteMaxHeight = (int)value;
                break;
            case 185:
                this.selectedOverworldGeneratorOptions.coalSize = (int)value;
                break;
            case 186:
                this.selectedOverworldGeneratorOptions.coalCount = (int)value;
                break;
            case 187:
                this.selectedOverworldGeneratorOptions.coalMinHeight = (int)value;
                break;
            case 189:
                this.selectedOverworldGeneratorOptions.coalMaxHeight = (int)value;
                break;
            case 190:
                this.selectedOverworldGeneratorOptions.ironSize = (int)value;
                break;
            case 191:
                this.selectedOverworldGeneratorOptions.ironCount = (int)value;
                break;
            case 192:
                this.selectedOverworldGeneratorOptions.ironMinHeight = (int)value;
                break;
            case 193:
                this.selectedOverworldGeneratorOptions.ironMaxHeight = (int)value;
                break;
            case 194:
                this.selectedOverworldGeneratorOptions.goldSize = (int)value;
                break;
            case 195:
                this.selectedOverworldGeneratorOptions.goldCount = (int)value;
                break;
            case 196:
                this.selectedOverworldGeneratorOptions.goldMinHeight = (int)value;
                break;
            case 197:
                this.selectedOverworldGeneratorOptions.goldMaxHeight = (int)value;
                break;
            case 198:
                this.selectedOverworldGeneratorOptions.redstoneSize = (int)value;
                break;
            case 199:
                this.selectedOverworldGeneratorOptions.redstoneCount = (int)value;
                break;
            case 200:
                this.selectedOverworldGeneratorOptions.redstoneMinHeight = (int)value;
                break;
            case 201:
                this.selectedOverworldGeneratorOptions.redstoneMaxHeight = (int)value;
                break;
            case 202:
                this.selectedOverworldGeneratorOptions.diamondSize = (int)value;
                break;
            case 203:
                this.selectedOverworldGeneratorOptions.diamondCount = (int)value;
                break;
            case 204:
                this.selectedOverworldGeneratorOptions.diamondMinHeight = (int)value;
                break;
            case 205:
                this.selectedOverworldGeneratorOptions.diamondMaxHeight = (int)value;
                break;
            case 206:
                this.selectedOverworldGeneratorOptions.lapisSize = (int)value;
                break;
            case 207:
                this.selectedOverworldGeneratorOptions.lapisCount = (int)value;
                break;
            case 208:
                this.selectedOverworldGeneratorOptions.lapisMinHeight = (int)value;
                break;
            case 209:
                this.selectedOverworldGeneratorOptions.lapisMaxHeight = (int)value;
        }

        if (id >= 100 && id < 116) {
            GuiElement guielement = this.overworldGeneratorOptions.getElement(id - 100 + 132);
            if (guielement != null) {
                ((TextFieldWidget)guielement).setText(this.formatValue(id, value));
            }
        }

        if (!this.selectedOverworldGeneratorOptions.equals(this.defaultOverworldGeneratorOptions)) {
            this.updateDefaultsButton(true);
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            switch (button.id) {
                case 300:
                    this.parentScreen.generatorOptions = this.selectedOverworldGeneratorOptions.toString();
                    this.minecraft.openScreen(this.parentScreen);
                    break;
                case 301:
                    for (int i = 0; i < this.overworldGeneratorOptions.size(); i++) {
                        OverworldGeneratorOptionsWidget.GeneratorOptionsEntry overworldgeneratoroptionswidget$generatoroptionsentry = this.overworldGeneratorOptions
                            .getEntry(i);
                        GuiElement guielement = overworldgeneratoroptionswidget$generatoroptionsentry.getLeft();
                        if (guielement instanceof ButtonWidget) {
                            ButtonWidget buttonwidget = (ButtonWidget)guielement;
                            if (buttonwidget instanceof GeneratorOptionSlider) {
                                float f = ((GeneratorOptionSlider)buttonwidget).getAmount() * (0.75F + this.random.nextFloat() * 0.5F)
                                    + (this.random.nextFloat() * 0.1F - 0.05F);
                                ((GeneratorOptionSlider)buttonwidget).setAmount(MathHelper.clamp(f, 0.0F, 1.0F));
                            } else if (buttonwidget instanceof GeneratorOptionToggle) {
                                ((GeneratorOptionToggle)buttonwidget).setValue(this.random.nextBoolean());
                            }
                        }

                        GuiElement guielement1 = overworldgeneratoroptionswidget$generatoroptionsentry.getRight();
                        if (guielement1 instanceof ButtonWidget) {
                            ButtonWidget buttonwidget1 = (ButtonWidget)guielement1;
                            if (buttonwidget1 instanceof GeneratorOptionSlider) {
                                float f1 = ((GeneratorOptionSlider)buttonwidget1).getAmount() * (0.75F + this.random.nextFloat() * 0.5F)
                                    + (this.random.nextFloat() * 0.1F - 0.05F);
                                ((GeneratorOptionSlider)buttonwidget1).setAmount(MathHelper.clamp(f1, 0.0F, 1.0F));
                            } else if (buttonwidget1 instanceof GeneratorOptionToggle) {
                                ((GeneratorOptionToggle)buttonwidget1).setValue(this.random.nextBoolean());
                            }
                        }
                    }
                    break;
                case 302:
                    this.overworldGeneratorOptions.prevPage();
                    this.updatePageIndicators();
                    break;
                case 303:
                    this.overworldGeneratorOptions.nextPage();
                    this.updatePageIndicators();
                    break;
                case 304:
                    if (this.defaultsButtonActive) {
                        this.queueButtonClick(304);
                    }
                    break;
                case 305:
                    this.minecraft.openScreen(new CustomizeWorldPresetsScreen(this));
                    break;
                case 306:
                    this.toggleDefaults();
                    break;
                case 307:
                    this.queuedButtonId = 0;
                    this.toggleDefaults();
            }
        }
    }

    private void resetOverworldGeneratorOptions() {
        this.selectedOverworldGeneratorOptions.reset();
        this.updateOverworldGeneratorOptions();
        this.updateDefaultsButton(false);
    }

    private void queueButtonClick(int id) {
        this.queuedButtonId = id;
        this.updateButtons(true);
    }

    private void toggleDefaults() {
        switch (this.queuedButtonId) {
            case 300:
                this.buttonClicked((GeneratorOptionToggle)this.overworldGeneratorOptions.getElement(300));
                break;
            case 304:
                this.resetOverworldGeneratorOptions();
        }

        this.queuedButtonId = 0;
        this.skipNextClick = true;
        this.updateButtons(false);
    }

    private void updateButtons(boolean defaults) {
        this.confirmYesButton.visible = defaults;
        this.confirmNoButton.visible = defaults;
        this.randomizeButton.active = !defaults;
        this.doneButton.active = !defaults;
        this.prevPageButton.active = !defaults;
        this.nextPageButton.active = !defaults;
        this.defaultsButton.active = this.defaultsButtonActive && !defaults;
        this.presetsButton.active = !defaults;
        this.overworldGeneratorOptions.setActive(!defaults);
    }

    private void updatePageIndicators() {
        this.prevPageButton.active = this.overworldGeneratorOptions.getPage() != 0;
        this.nextPageButton.active = this.overworldGeneratorOptions.getPage() != this.overworldGeneratorOptions.getOptionCount() - 1;
        this.pageTitle = I18n.translate("book.pageIndicator", this.overworldGeneratorOptions.getPage() + 1, this.overworldGeneratorOptions.getOptionCount());
        this.pageDescription = this.pageDescriptions[this.overworldGeneratorOptions.getPage()];
        this.randomizeButton.active = this.overworldGeneratorOptions.getPage() != this.overworldGeneratorOptions.getOptionCount() - 1;
    }

    @Override
    protected void keyPressed(char chr, int key) {
        super.keyPressed(chr, key);
        if (this.queuedButtonId == 0) {
            switch (key) {
                case 200:
                    this.modifyValue(1.0F);
                    break;
                case 208:
                    this.modifyValue(-1.0F);
                    break;
                default:
                    this.overworldGeneratorOptions.keyPressed(chr, key);
            }
        }
    }

    private void modifyValue(float modifier) {
        GuiElement guielement = this.overworldGeneratorOptions.getSelected();
        if (guielement instanceof TextFieldWidget) {
            float f = modifier;
            if (Screen.isShiftDown()) {
                f *= 0.1F;
                if (Screen.isControlDown()) {
                    f *= 0.1F;
                }
            } else if (Screen.isControlDown()) {
                f *= 10.0F;
                if (Screen.isAltDown()) {
                    f *= 10.0F;
                }
            }

            TextFieldWidget textfieldwidget = (TextFieldWidget)guielement;
            Float f1 = Floats.tryParse(textfieldwidget.getText());
            if (f1 != null) {
                f1 = f1 + f;
                int i = textfieldwidget.getId();
                String s = this.formatValue(textfieldwidget.getId(), f1);
                textfieldwidget.setText(s);
                this.setValue(i, s);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (this.queuedButtonId == 0 && !this.skipNextClick) {
            this.overworldGeneratorOptions.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button);
        if (this.skipNextClick) {
            this.skipNextClick = false;
        } else if (this.queuedButtonId == 0) {
            this.overworldGeneratorOptions.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.overworldGeneratorOptions.render(mouseX, mouseY, tickDelta);
        this.drawCenteredString(this.textRenderer, this.title, this.width / 2, 2, 16777215);
        this.drawCenteredString(this.textRenderer, this.pageTitle, this.width / 2, 12, 16777215);
        this.drawCenteredString(this.textRenderer, this.pageDescription, this.width / 2, 22, 16777215);
        super.render(mouseX, mouseY, tickDelta);
        if (this.queuedButtonId != 0) {
            fill(0, 0, this.width, this.height, Integer.MIN_VALUE);
            this.drawHorizontalLine(this.width / 2 - 91, this.width / 2 + 90, 99, -2039584);
            this.drawHorizontalLine(this.width / 2 - 91, this.width / 2 + 90, 185, -6250336);
            this.drawVerticalLine(this.width / 2 - 91, 99, 185, -2039584);
            this.drawVerticalLine(this.width / 2 + 90, 99, 185, -6250336);
            float f = 85.0F;
            float f1 = 180.0F;
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            this.minecraft.getTextureManager().bind(BACKGROUND_LOCATION);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            float f2 = 32.0F;
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
            bufferbuilder.vertex(this.width / 2 - 90, 185.0, 0.0).texture(0.0, 2.65625).color(64, 64, 64, 64).nextVertex();
            bufferbuilder.vertex(this.width / 2 + 90, 185.0, 0.0).texture(5.625, 2.65625).color(64, 64, 64, 64).nextVertex();
            bufferbuilder.vertex(this.width / 2 + 90, 100.0, 0.0).texture(5.625, 0.0).color(64, 64, 64, 64).nextVertex();
            bufferbuilder.vertex(this.width / 2 - 90, 100.0, 0.0).texture(0.0, 0.0).color(64, 64, 64, 64).nextVertex();
            tesselator.end();
            this.drawCenteredString(this.textRenderer, I18n.translate("createWorld.customize.custom.confirmTitle"), this.width / 2, 105, 16777215);
            this.drawCenteredString(this.textRenderer, I18n.translate("createWorld.customize.custom.confirm1"), this.width / 2, 125, 16777215);
            this.drawCenteredString(this.textRenderer, I18n.translate("createWorld.customize.custom.confirm2"), this.width / 2, 135, 16777215);
            this.confirmYesButton.render(this.minecraft, mouseX, mouseY);
            this.confirmNoButton.render(this.minecraft, mouseX, mouseY);
        }
    }
}
