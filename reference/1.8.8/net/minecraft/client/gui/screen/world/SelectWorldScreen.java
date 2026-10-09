package net.minecraft.client.gui.screen.world;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ConfirmationListener;
import net.minecraft.client.gui.screen.FatalErrorScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ListWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Formatting;
import net.minecraft.world.WorldData;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.WorldSaveInfo;
import net.minecraft.world.storage.WorldStorage;
import net.minecraft.world.storage.WorldStorageSource;
import net.minecraft.world.storage.exception.WorldStorageException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SelectWorldScreen extends Screen implements ConfirmationListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private final DateFormat dateFormat = new SimpleDateFormat();
    protected Screen parent;
    protected String title = "Select world";
    private boolean selected;
    private int selectedWorldId;
    private List<WorldSaveInfo> saves;
    private SelectWorldScreen.WorldListWidget worldList;
    private String worldText;
    /**
     * The text to display when a world needs to be converted (e.g. when it needs to be converted from a lower version)
     */
    private String conversionText;
    private String[] gameModeTexts = new String[4];
    /**
     * Is true if the player is in a child screen (e.g. a confirmation screen).
     * Is set to false again when the selectWorldScreen gets displayed again.
     */
    private boolean isInChildScreen;
    private ButtonWidget deleteWorldButton;
    private ButtonWidget playSelectedWorldButton;
    private ButtonWidget renameWorldButton;
    private ButtonWidget recreateWorldButton;

    public SelectWorldScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        this.title = I18n.translate("selectWorld.title");

        try {
            this.getSaves();
        } catch (WorldStorageException worldstorageexception) {
            LOGGER.error("Couldn't load level list", worldstorageexception);
            this.minecraft.openScreen(new FatalErrorScreen("Unable to load worlds", worldstorageexception.getMessage()));
            return;
        }

        this.worldText = I18n.translate("selectWorld.world");
        this.conversionText = I18n.translate("selectWorld.conversion");
        this.gameModeTexts[WorldSettings.GameMode.SURVIVAL.getId()] = I18n.translate("gameMode.survival");
        this.gameModeTexts[WorldSettings.GameMode.CREATIVE.getId()] = I18n.translate("gameMode.creative");
        this.gameModeTexts[WorldSettings.GameMode.ADVENTURE.getId()] = I18n.translate("gameMode.adventure");
        this.gameModeTexts[WorldSettings.GameMode.SPECTATOR.getId()] = I18n.translate("gameMode.spectator");
        this.worldList = new SelectWorldScreen.WorldListWidget(this.minecraft);
        this.worldList.setScrollButtonIds(4, 5);
        this.addButtons();
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        this.worldList.handleMouse();
    }

    private void getSaves() throws WorldStorageException {
        WorldStorageSource worldstoragesource = this.minecraft.getWorldStorageSource();
        this.saves = worldstoragesource.getAll();
        Collections.sort(this.saves);
        this.selectedWorldId = -1;
    }

    /**
     * The world save name is the name of the save folder. When a world is created this
     * is always equal to the world name, but can be different if the folder is either manually
     * editted or if you create multiple worlds with the same name.
     */
    protected String getSaveFileName(int index) {
        return this.saves.get(index).getSaveName();
    }

    protected String getWorldName(int index) {
        String s = this.saves.get(index).getName();
        if (StringUtils.isEmpty(s)) {
            s = I18n.translate("selectWorld.world") + " " + (index + 1);
        }

        return s;
    }

    public void addButtons() {
        this.buttons
            .add(this.playSelectedWorldButton = new ButtonWidget(1, this.width / 2 - 154, this.height - 52, 150, 20, I18n.translate("selectWorld.select")));
        this.buttons.add(new ButtonWidget(3, this.width / 2 + 4, this.height - 52, 150, 20, I18n.translate("selectWorld.create")));
        this.buttons.add(this.renameWorldButton = new ButtonWidget(6, this.width / 2 - 154, this.height - 28, 72, 20, I18n.translate("selectWorld.rename")));
        this.buttons.add(this.deleteWorldButton = new ButtonWidget(2, this.width / 2 - 76, this.height - 28, 72, 20, I18n.translate("selectWorld.delete")));
        this.buttons.add(this.recreateWorldButton = new ButtonWidget(7, this.width / 2 + 4, this.height - 28, 72, 20, I18n.translate("selectWorld.recreate")));
        this.buttons.add(new ButtonWidget(0, this.width / 2 + 82, this.height - 28, 72, 20, I18n.translate("gui.cancel")));
        this.playSelectedWorldButton.active = false;
        this.deleteWorldButton.active = false;
        this.renameWorldButton.active = false;
        this.recreateWorldButton.active = false;
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 2) {
                String s = this.getWorldName(this.selectedWorldId);
                if (s != null) {
                    this.isInChildScreen = true;
                    ConfirmScreen confirmscreen = getDeleteWarningPrompt(this, s, this.selectedWorldId);
                    this.minecraft.openScreen(confirmscreen);
                }
            } else if (button.id == 1) {
                this.selectWorld(this.selectedWorldId);
            } else if (button.id == 3) {
                this.minecraft.openScreen(new CreateWorldScreen(this));
            } else if (button.id == 6) {
                this.minecraft.openScreen(new EditWorldScreen(this, this.getSaveFileName(this.selectedWorldId)));
            } else if (button.id == 0) {
                this.minecraft.openScreen(this.parent);
            } else if (button.id == 7) {
                CreateWorldScreen createworldscreen = new CreateWorldScreen(this);
                WorldStorage worldstorage = this.minecraft.getWorldStorageSource().get(this.getSaveFileName(this.selectedWorldId), false);
                WorldData worlddata = worldstorage.loadData();
                worldstorage.forceSave();
                createworldscreen.copyWorld(worlddata);
                this.minecraft.openScreen(createworldscreen);
            } else {
                this.worldList.buttonClicked(button);
            }
        }
    }

    public void selectWorld(int id) {
        this.minecraft.openScreen(null);
        if (!this.selected) {
            this.selected = true;
            String s = this.getSaveFileName(id);
            if (s == null) {
                s = "World" + id;
            }

            String s1 = this.getWorldName(id);
            if (s1 == null) {
                s1 = "World" + id;
            }

            if (this.minecraft.getWorldStorageSource().exists(s)) {
                this.minecraft.startGame(s, s1, null);
            }
        }
    }

    @Override
    public void confirmResult(boolean result, int id) {
        if (this.isInChildScreen) {
            this.isInChildScreen = false;
            if (result) {
                WorldStorageSource worldstoragesource = this.minecraft.getWorldStorageSource();
                worldstoragesource.flush();
                worldstoragesource.delete(this.getSaveFileName(id));

                try {
                    this.getSaves();
                } catch (WorldStorageException worldstorageexception) {
                    LOGGER.error("Couldn't load level list", worldstorageexception);
                }
            }

            this.minecraft.openScreen(this);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.worldList.render(mouseX, mouseY, tickDelta);
        this.drawCenteredString(this.textRenderer, this.title, this.width / 2, 20, 16777215);
        super.render(mouseX, mouseY, tickDelta);
    }

    public static ConfirmScreen getDeleteWarningPrompt(ConfirmationListener screen, String worldName, int id) {
        String s = I18n.translate("selectWorld.deleteQuestion");
        String s1 = "'" + worldName + "' " + I18n.translate("selectWorld.deleteWarning");
        String s2 = I18n.translate("selectWorld.deleteButton");
        String s3 = I18n.translate("gui.cancel");
        return new ConfirmScreen(screen, s, s1, s2, s3, id);
    }

    class WorldListWidget extends ListWidget {
        public WorldListWidget(Minecraft minecraft) {
            super(minecraft, SelectWorldScreen.this.width, SelectWorldScreen.this.height, 32, SelectWorldScreen.this.height - 64, 36);
        }

        @Override
        protected int size() {
            return SelectWorldScreen.this.saves.size();
        }

        @Override
        protected void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            SelectWorldScreen.this.selectedWorldId = index;
            boolean flag = SelectWorldScreen.this.selectedWorldId >= 0 && SelectWorldScreen.this.selectedWorldId < this.size();
            SelectWorldScreen.this.playSelectedWorldButton.active = flag;
            SelectWorldScreen.this.deleteWorldButton.active = flag;
            SelectWorldScreen.this.renameWorldButton.active = flag;
            SelectWorldScreen.this.recreateWorldButton.active = flag;
            if (doubleClick && flag) {
                SelectWorldScreen.this.selectWorld(index);
            }
        }

        @Override
        protected boolean isEntrySelected(int index) {
            return index == SelectWorldScreen.this.selectedWorldId;
        }

        @Override
        protected int getHeight() {
            return SelectWorldScreen.this.saves.size() * 36;
        }

        @Override
        protected void renderBackground() {
            SelectWorldScreen.this.renderBackground();
        }

        @Override
        protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
            WorldSaveInfo worldsaveinfo = SelectWorldScreen.this.saves.get(index);
            String s = worldsaveinfo.getName();
            if (StringUtils.isEmpty(s)) {
                s = SelectWorldScreen.this.worldText + " " + (index + 1);
            }

            String s1 = worldsaveinfo.getSaveName();
            s1 = s1 + " (" + SelectWorldScreen.this.dateFormat.format(new Date(worldsaveinfo.getLastPlayed()));
            s1 = s1 + ")";
            String s2 = "";
            if (worldsaveinfo.isSameVersion()) {
                s2 = SelectWorldScreen.this.conversionText + " " + s2;
            } else {
                s2 = SelectWorldScreen.this.gameModeTexts[worldsaveinfo.getGameMode().getId()];
                if (worldsaveinfo.isHardcore()) {
                    s2 = Formatting.DARK_RED + I18n.translate("gameMode.hardcore") + Formatting.RESET;
                }

                if (worldsaveinfo.areCheatsEnabled()) {
                    s2 = s2 + ", " + I18n.translate("selectWorld.cheats");
                }
            }

            SelectWorldScreen.this.drawString(SelectWorldScreen.this.textRenderer, s, x + 2, y + 1, 16777215);
            SelectWorldScreen.this.drawString(SelectWorldScreen.this.textRenderer, s1, x + 2, y + 12, 8421504);
            SelectWorldScreen.this.drawString(SelectWorldScreen.this.textRenderer, s2, x + 2, y + 12 + 10, 8421504);
        }
    }
}
