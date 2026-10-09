package net.minecraft.client.gui.screen.world;

import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screen.CustomizeFlatWorldScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.world.WorldData;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import net.minecraft.world.storage.WorldStorageSource;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

public class CreateWorldScreen extends Screen {
    private Screen parent;
    private TextFieldWidget displayNameField;
    private TextFieldWidget seedField;
    private String worldSaveName;
    private String gameModeName = "survival";
    private String savedGameModeName;
    private boolean structures = true;
    private boolean allowCommands;
    private boolean cheatsEnabled;
    private boolean bonusChest;
    private boolean hardcore;
    private boolean creatingLevel;
    private boolean moreOptionsOpen;
    private ButtonWidget gameMode;
    private ButtonWidget moreWorldOptions;
    private ButtonWidget generateStructures;
    private ButtonWidget bonusChestButton;
    private ButtonWidget mapTypeButton;
    private ButtonWidget allowCommandsButton;
    private ButtonWidget customizeButton;
    private String firstGameModeDescriptionLine;
    private String secondGameModeDescriptionLine;
    private String seed;
    private String worldName;
    private int generatorType;
    public String generatorOptions = "";
    private static final String[] ILLEGAL_FOLDER_NAMES = new String[]{
        "CON",
        "COM",
        "PRN",
        "AUX",
        "CLOCK$",
        "NUL",
        "COM1",
        "COM2",
        "COM3",
        "COM4",
        "COM5",
        "COM6",
        "COM7",
        "COM8",
        "COM9",
        "LPT1",
        "LPT2",
        "LPT3",
        "LPT4",
        "LPT5",
        "LPT6",
        "LPT7",
        "LPT8",
        "LPT9"
    };

    public CreateWorldScreen(Screen parent) {
        this.parent = parent;
        this.seed = "";
        this.worldName = I18n.translate("selectWorld.newWorld");
    }

    @Override
    public void tick() {
        this.displayNameField.tick();
        this.seedField.tick();
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        this.buttons.clear();
        this.buttons.add(new ButtonWidget(0, this.width / 2 - 155, this.height - 28, 150, 20, I18n.translate("selectWorld.create")));
        this.buttons.add(new ButtonWidget(1, this.width / 2 + 5, this.height - 28, 150, 20, I18n.translate("gui.cancel")));
        this.buttons.add(this.gameMode = new ButtonWidget(2, this.width / 2 - 75, 115, 150, 20, I18n.translate("selectWorld.gameMode")));
        this.buttons.add(this.moreWorldOptions = new ButtonWidget(3, this.width / 2 - 75, 187, 150, 20, I18n.translate("selectWorld.moreWorldOptions")));
        this.buttons.add(this.generateStructures = new ButtonWidget(4, this.width / 2 - 155, 100, 150, 20, I18n.translate("selectWorld.mapFeatures")));
        this.generateStructures.visible = false;
        this.buttons.add(this.bonusChestButton = new ButtonWidget(7, this.width / 2 + 5, 151, 150, 20, I18n.translate("selectWorld.bonusItems")));
        this.bonusChestButton.visible = false;
        this.buttons.add(this.mapTypeButton = new ButtonWidget(5, this.width / 2 + 5, 100, 150, 20, I18n.translate("selectWorld.mapType")));
        this.mapTypeButton.visible = false;
        this.buttons.add(this.allowCommandsButton = new ButtonWidget(6, this.width / 2 - 155, 151, 150, 20, I18n.translate("selectWorld.allowCommands")));
        this.allowCommandsButton.visible = false;
        this.buttons.add(this.customizeButton = new ButtonWidget(8, this.width / 2 + 5, 120, 150, 20, I18n.translate("selectWorld.customizeType")));
        this.customizeButton.visible = false;
        this.displayNameField = new TextFieldWidget(9, this.textRenderer, this.width / 2 - 100, 60, 200, 20);
        this.displayNameField.setFocused(true);
        this.displayNameField.setText(this.worldName);
        this.seedField = new TextFieldWidget(10, this.textRenderer, this.width / 2 - 100, 60, 200, 20);
        this.seedField.setText(this.seed);
        this.setScreen(this.moreOptionsOpen);
        this.getSaveDirectoryName();
        this.updateSettingsLabels();
    }

    private void getSaveDirectoryName() {
        this.worldSaveName = this.displayNameField.getText().trim();

        for (char c0 : SharedConstants.INVALID_FILE_CHARS) {
            this.worldSaveName = this.worldSaveName.replace(c0, '_');
        }

        if (StringUtils.isEmpty(this.worldSaveName)) {
            this.worldSaveName = "World";
        }

        this.worldSaveName = getWorldSaveName(this.minecraft.getWorldStorageSource(), this.worldSaveName);
    }

    private void updateSettingsLabels() {
        this.gameMode.message = I18n.translate("selectWorld.gameMode") + ": " + I18n.translate("selectWorld.gameMode." + this.gameModeName);
        this.firstGameModeDescriptionLine = I18n.translate("selectWorld.gameMode." + this.gameModeName + ".line1");
        this.secondGameModeDescriptionLine = I18n.translate("selectWorld.gameMode." + this.gameModeName + ".line2");
        this.generateStructures.message = I18n.translate("selectWorld.mapFeatures") + " ";
        if (this.structures) {
            this.generateStructures.message = this.generateStructures.message + I18n.translate("options.on");
        } else {
            this.generateStructures.message = this.generateStructures.message + I18n.translate("options.off");
        }

        this.bonusChestButton.message = I18n.translate("selectWorld.bonusItems") + " ";
        if (this.bonusChest && !this.hardcore) {
            this.bonusChestButton.message = this.bonusChestButton.message + I18n.translate("options.on");
        } else {
            this.bonusChestButton.message = this.bonusChestButton.message + I18n.translate("options.off");
        }

        this.mapTypeButton.message = I18n.translate("selectWorld.mapType")
            + " "
            + I18n.translate(WorldGeneratorType.BY_ID[this.generatorType].getTranslationKey());
        this.allowCommandsButton.message = I18n.translate("selectWorld.allowCommands") + " ";
        if (this.allowCommands && !this.hardcore) {
            this.allowCommandsButton.message = this.allowCommandsButton.message + I18n.translate("options.on");
        } else {
            this.allowCommandsButton.message = this.allowCommandsButton.message + I18n.translate("options.off");
        }
    }

    public static String getWorldSaveName(WorldStorageSource storageSource, String worldName) {
        worldName = worldName.replaceAll("[\\./\"]", "_");

        for (String s : ILLEGAL_FOLDER_NAMES) {
            if (worldName.equalsIgnoreCase(s)) {
                worldName = "_" + worldName + "_";
            }
        }

        while (storageSource.getData(worldName) != null) {
            worldName = worldName + "-";
        }

        return worldName;
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 1) {
                this.minecraft.openScreen(this.parent);
            } else if (button.id == 0) {
                this.minecraft.openScreen(null);
                if (this.creatingLevel) {
                    return;
                }

                this.creatingLevel = true;
                long i = new Random().nextLong();
                String s = this.seedField.getText();
                if (!StringUtils.isEmpty(s)) {
                    try {
                        long j = Long.parseLong(s);
                        if (j != 0L) {
                            i = j;
                        }
                    } catch (NumberFormatException numberformatexception) {
                        i = s.hashCode();
                    }
                }

                WorldSettings.GameMode worldsettings$gamemode = WorldSettings.GameMode.byKey(this.gameModeName);
                WorldSettings worldsettings = new WorldSettings(
                    i, worldsettings$gamemode, this.structures, this.hardcore, WorldGeneratorType.BY_ID[this.generatorType]
                );
                worldsettings.setGeneratorOptions(this.generatorOptions);
                if (this.bonusChest && !this.hardcore) {
                    worldsettings.enableBonusChest();
                }

                if (this.allowCommands && !this.hardcore) {
                    worldsettings.enableCommands();
                }

                this.minecraft.startGame(this.worldSaveName, this.displayNameField.getText().trim(), worldsettings);
            } else if (button.id == 3) {
                this.toggleMoreOptions();
            } else if (button.id == 2) {
                if (this.gameModeName.equals("survival")) {
                    if (!this.cheatsEnabled) {
                        this.allowCommands = false;
                    }

                    this.hardcore = false;
                    this.gameModeName = "hardcore";
                    this.hardcore = true;
                    this.allowCommandsButton.active = false;
                    this.bonusChestButton.active = false;
                    this.updateSettingsLabels();
                } else if (this.gameModeName.equals("hardcore")) {
                    if (!this.cheatsEnabled) {
                        this.allowCommands = true;
                    }

                    this.hardcore = false;
                    this.gameModeName = "creative";
                    this.updateSettingsLabels();
                    this.hardcore = false;
                    this.allowCommandsButton.active = true;
                    this.bonusChestButton.active = true;
                } else {
                    if (!this.cheatsEnabled) {
                        this.allowCommands = false;
                    }

                    this.gameModeName = "survival";
                    this.updateSettingsLabels();
                    this.allowCommandsButton.active = true;
                    this.bonusChestButton.active = true;
                    this.hardcore = false;
                }

                this.updateSettingsLabels();
            } else if (button.id == 4) {
                this.structures = !this.structures;
                this.updateSettingsLabels();
            } else if (button.id == 7) {
                this.bonusChest = !this.bonusChest;
                this.updateSettingsLabels();
            } else if (button.id == 5) {
                this.generatorType++;
                if (this.generatorType >= WorldGeneratorType.BY_ID.length) {
                    this.generatorType = 0;
                }

                while (!this.canShowCurrentWorldGenerator()) {
                    this.generatorType++;
                    if (this.generatorType >= WorldGeneratorType.BY_ID.length) {
                        this.generatorType = 0;
                    }
                }

                this.generatorOptions = "";
                this.updateSettingsLabels();
                this.setScreen(this.moreOptionsOpen);
            } else if (button.id == 6) {
                this.cheatsEnabled = true;
                this.allowCommands = !this.allowCommands;
                this.updateSettingsLabels();
            } else if (button.id == 8) {
                if (WorldGeneratorType.BY_ID[this.generatorType] == WorldGeneratorType.FLAT) {
                    this.minecraft.openScreen(new CustomizeFlatWorldScreen(this, this.generatorOptions));
                } else {
                    this.minecraft.openScreen(new CustomizeWorldScreen(this, this.generatorOptions));
                }
            }
        }
    }

    private boolean canShowCurrentWorldGenerator() {
        WorldGeneratorType worldgeneratortype = WorldGeneratorType.BY_ID[this.generatorType];
        return worldgeneratortype != null
            && worldgeneratortype.isVisible()
            && (worldgeneratortype != WorldGeneratorType.DEBUG_ALL_BLOCK_STATES || isShiftDown());
    }

    private void toggleMoreOptions() {
        this.setScreen(!this.moreOptionsOpen);
    }

    private void setScreen(boolean moreOptionsOpen) {
        this.moreOptionsOpen = moreOptionsOpen;
        if (WorldGeneratorType.BY_ID[this.generatorType] == WorldGeneratorType.DEBUG_ALL_BLOCK_STATES) {
            this.gameMode.visible = !this.moreOptionsOpen;
            this.gameMode.active = false;
            if (this.savedGameModeName == null) {
                this.savedGameModeName = this.gameModeName;
            }

            this.gameModeName = "spectator";
            this.generateStructures.visible = false;
            this.bonusChestButton.visible = false;
            this.mapTypeButton.visible = this.moreOptionsOpen;
            this.allowCommandsButton.visible = false;
            this.customizeButton.visible = false;
        } else {
            this.gameMode.visible = !this.moreOptionsOpen;
            this.gameMode.active = true;
            if (this.savedGameModeName != null) {
                this.gameModeName = this.savedGameModeName;
                this.savedGameModeName = null;
            }

            this.generateStructures.visible = this.moreOptionsOpen && WorldGeneratorType.BY_ID[this.generatorType] != WorldGeneratorType.CUSTOMIZED;
            this.bonusChestButton.visible = this.moreOptionsOpen;
            this.mapTypeButton.visible = this.moreOptionsOpen;
            this.allowCommandsButton.visible = this.moreOptionsOpen;
            this.customizeButton.visible = this.moreOptionsOpen
                && (
                    WorldGeneratorType.BY_ID[this.generatorType] == WorldGeneratorType.FLAT
                        || WorldGeneratorType.BY_ID[this.generatorType] == WorldGeneratorType.CUSTOMIZED
                );
        }

        this.updateSettingsLabels();
        if (this.moreOptionsOpen) {
            this.moreWorldOptions.message = I18n.translate("gui.done");
        } else {
            this.moreWorldOptions.message = I18n.translate("selectWorld.moreWorldOptions");
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (this.displayNameField.isFocused() && !this.moreOptionsOpen) {
            this.displayNameField.keyPressed(chr, key);
            this.worldName = this.displayNameField.getText();
        } else if (this.seedField.isFocused() && this.moreOptionsOpen) {
            this.seedField.keyPressed(chr, key);
            this.seed = this.seedField.getText();
        }

        if (key == 28 || key == 156) {
            this.buttonClicked(this.buttons.get(0));
        }

        this.buttons.get(0).active = this.displayNameField.getText().length() > 0;
        this.getSaveDirectoryName();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (this.moreOptionsOpen) {
            this.seedField.mouseClicked(mouseX, mouseY, button);
        } else {
            this.displayNameField.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, I18n.translate("selectWorld.create"), this.width / 2, 20, -1);
        if (this.moreOptionsOpen) {
            this.drawString(this.textRenderer, I18n.translate("selectWorld.enterSeed"), this.width / 2 - 100, 47, -6250336);
            this.drawString(this.textRenderer, I18n.translate("selectWorld.seedInfo"), this.width / 2 - 100, 85, -6250336);
            if (this.generateStructures.visible) {
                this.drawString(this.textRenderer, I18n.translate("selectWorld.mapFeatures.info"), this.width / 2 - 150, 122, -6250336);
            }

            if (this.allowCommandsButton.visible) {
                this.drawString(this.textRenderer, I18n.translate("selectWorld.allowCommands.info"), this.width / 2 - 150, 172, -6250336);
            }

            this.seedField.render();
            if (WorldGeneratorType.BY_ID[this.generatorType].hasInfo()) {
                this.textRenderer
                    .splitAndDraw(
                        I18n.translate(WorldGeneratorType.BY_ID[this.generatorType].getInfoTranslationKey()),
                        this.mapTypeButton.x + 2,
                        this.mapTypeButton.y + 22,
                        this.mapTypeButton.getWidth(),
                        10526880
                    );
            }
        } else {
            this.drawString(this.textRenderer, I18n.translate("selectWorld.enterName"), this.width / 2 - 100, 47, -6250336);
            this.drawString(this.textRenderer, I18n.translate("selectWorld.resultFolder") + " " + this.worldSaveName, this.width / 2 - 100, 85, -6250336);
            this.displayNameField.render();
            this.drawString(this.textRenderer, this.firstGameModeDescriptionLine, this.width / 2 - 100, 137, -6250336);
            this.drawString(this.textRenderer, this.secondGameModeDescriptionLine, this.width / 2 - 100, 149, -6250336);
        }

        super.render(mouseX, mouseY, tickDelta);
    }

    public void copyWorld(WorldData data) {
        this.worldName = I18n.translate("selectWorld.newWorld.copyOf", data.getName());
        this.seed = data.getSeed() + "";
        this.generatorType = data.getGeneratorType().getId();
        this.generatorOptions = data.getGeneratorOptions();
        this.structures = data.allowStructures();
        this.allowCommands = data.allowCommands();
        if (data.isHardcore()) {
            this.gameModeName = "hardcore";
        } else if (data.getDefaultGamemode().isSurvival()) {
            this.gameModeName = "survival";
        } else if (data.getDefaultGamemode().isCreative()) {
            this.gameModeName = "creative";
        }
    }
}
