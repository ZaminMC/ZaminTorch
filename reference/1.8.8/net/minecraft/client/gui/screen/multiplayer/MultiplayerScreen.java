package net.minecraft.client.gui.screen.multiplayer;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ConfirmationListener;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.DirectConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.AddServerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.LanServerEntry;
import net.minecraft.client.gui.widget.ServerListEntryWidget;
import net.minecraft.client.network.LanServerQueryManager;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.options.ServerList;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.resource.language.I18n;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

public class MultiplayerScreen extends Screen implements ConfirmationListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private final MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();
    private Screen parent;
    private MultiplayerServerListWidget serverList;
    private ServerList servers;
    private ButtonWidget editButton;
    private ButtonWidget joinButton;
    private ButtonWidget deleteButton;
    private boolean deleteServerConfirmationDialogOpen;
    private boolean addServerDialogOpen;
    private boolean editServerDialogOpen;
    private boolean serverOpen;
    private String tooltip;
    private ServerListEntry newServer;
    private LanServerQueryManager.LanServerList lanServerList;
    private LanServerQueryManager.LanServerDetector lanServerDetector;
    private boolean initialized;

    public MultiplayerScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        this.buttons.clear();
        if (!this.initialized) {
            this.initialized = true;
            this.servers = new ServerList(this.minecraft);
            this.servers.load();
            this.lanServerList = new LanServerQueryManager.LanServerList();

            try {
                this.lanServerDetector = new LanServerQueryManager.LanServerDetector(this.lanServerList);
                this.lanServerDetector.start();
            } catch (Exception exception) {
                LOGGER.warn("Unable to start LAN server detection: " + exception.getMessage());
            }

            this.serverList = new MultiplayerServerListWidget(this, this.minecraft, this.width, this.height, 32, this.height - 64, 36);
            this.serverList.setServers(this.servers);
        } else {
            this.serverList.setBounds(this.width, this.height, 32, this.height - 64);
        }

        this.addButtons();
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        this.serverList.handleMouse();
    }

    /**
     * Button Ids:
     *     - 0: Cancel Button
     *     - 1: Select Server Button
     *     - 2: Delete Server Button
     *     - 3: Add Server button
     *     - 4: Select Server Directly
     *     - 5:
     *     - 6:
     *     - 7: Edit Server Button
     *     - 8: Refresh Server Button
     */
    public void addButtons() {
        this.buttons.add(this.editButton = new ButtonWidget(7, this.width / 2 - 154, this.height - 28, 70, 20, I18n.translate("selectServer.edit")));
        this.buttons.add(this.deleteButton = new ButtonWidget(2, this.width / 2 - 74, this.height - 28, 70, 20, I18n.translate("selectServer.delete")));
        this.buttons.add(this.joinButton = new ButtonWidget(1, this.width / 2 - 154, this.height - 52, 100, 20, I18n.translate("selectServer.select")));
        this.buttons.add(new ButtonWidget(4, this.width / 2 - 50, this.height - 52, 100, 20, I18n.translate("selectServer.direct")));
        this.buttons.add(new ButtonWidget(3, this.width / 2 + 4 + 50, this.height - 52, 100, 20, I18n.translate("selectServer.add")));
        this.buttons.add(new ButtonWidget(8, this.width / 2 + 4, this.height - 28, 70, 20, I18n.translate("selectServer.refresh")));
        this.buttons.add(new ButtonWidget(0, this.width / 2 + 4 + 76, this.height - 28, 75, 20, I18n.translate("gui.cancel")));
        this.moveToServer(this.serverList.getCurrentServerIndex());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.lanServerList.needsUpdate()) {
            List<LanServerQueryManager.LanServer> list = this.lanServerList.getServers();
            this.lanServerList.markClean();
            this.serverList.setLanServers(list);
        }

        this.pinger.tick();
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
        if (this.lanServerDetector != null) {
            this.lanServerDetector.interrupt();
            this.lanServerDetector = null;
        }

        this.pinger.cancel();
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            EntryListWidget.Entry entrylistwidget$entry = this.serverList.getCurrentServerIndex() < 0
                ? null
                : this.serverList.getEntry(this.serverList.getCurrentServerIndex());
            if (button.id == 2 && entrylistwidget$entry instanceof ServerListEntryWidget) {
                String s4 = ((ServerListEntryWidget)entrylistwidget$entry).fetchServer().name;
                if (s4 != null) {
                    this.deleteServerConfirmationDialogOpen = true;
                    String s = I18n.translate("selectServer.deleteQuestion");
                    String s1 = "'" + s4 + "' " + I18n.translate("selectServer.deleteWarning");
                    String s2 = I18n.translate("selectServer.deleteButton");
                    String s3 = I18n.translate("gui.cancel");
                    ConfirmScreen confirmscreen = new ConfirmScreen(this, s, s1, s2, s3, this.serverList.getCurrentServerIndex());
                    this.minecraft.openScreen(confirmscreen);
                }
            } else if (button.id == 1) {
                this.connect();
            } else if (button.id == 4) {
                this.serverOpen = true;
                this.minecraft
                    .openScreen(new DirectConnectScreen(this, this.newServer = new ServerListEntry(I18n.translate("selectServer.defaultName"), "", false)));
            } else if (button.id == 3) {
                this.addServerDialogOpen = true;
                this.minecraft
                    .openScreen(new AddServerScreen(this, this.newServer = new ServerListEntry(I18n.translate("selectServer.defaultName"), "", false)));
            } else if (button.id == 7 && entrylistwidget$entry instanceof ServerListEntryWidget) {
                this.editServerDialogOpen = true;
                ServerListEntry serverlistentry = ((ServerListEntryWidget)entrylistwidget$entry).fetchServer();
                this.newServer = new ServerListEntry(serverlistentry.name, serverlistentry.ip, false);
                this.newServer.set(serverlistentry);
                this.minecraft.openScreen(new AddServerScreen(this, this.newServer));
            } else if (button.id == 0) {
                this.minecraft.openScreen(this.parent);
            } else if (button.id == 8) {
                this.refresh();
            }
        }
    }

    private void refresh() {
        this.minecraft.openScreen(new MultiplayerScreen(this.parent));
    }

    @Override
    public void confirmResult(boolean result, int id) {
        EntryListWidget.Entry entrylistwidget$entry = this.serverList.getCurrentServerIndex() < 0
            ? null
            : this.serverList.getEntry(this.serverList.getCurrentServerIndex());
        if (this.deleteServerConfirmationDialogOpen) {
            this.deleteServerConfirmationDialogOpen = false;
            if (result && entrylistwidget$entry instanceof ServerListEntryWidget) {
                this.servers.remove(this.serverList.getCurrentServerIndex());
                this.servers.save();
                this.serverList.setCurrentServerIndex(-1);
                this.serverList.setServers(this.servers);
            }

            this.minecraft.openScreen(this);
        } else if (this.serverOpen) {
            this.serverOpen = false;
            if (result) {
                this.connect(this.newServer);
            } else {
                this.minecraft.openScreen(this);
            }
        } else if (this.addServerDialogOpen) {
            this.addServerDialogOpen = false;
            if (result) {
                this.servers.add(this.newServer);
                this.servers.save();
                this.serverList.setCurrentServerIndex(-1);
                this.serverList.setServers(this.servers);
            }

            this.minecraft.openScreen(this);
        } else if (this.editServerDialogOpen) {
            this.editServerDialogOpen = false;
            if (result && entrylistwidget$entry instanceof ServerListEntryWidget) {
                ServerListEntry serverlistentry = ((ServerListEntryWidget)entrylistwidget$entry).fetchServer();
                serverlistentry.name = this.newServer.name;
                serverlistentry.ip = this.newServer.ip;
                serverlistentry.set(this.newServer);
                this.servers.save();
                this.serverList.setServers(this.servers);
            }

            this.minecraft.openScreen(this);
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
        int i = this.serverList.getCurrentServerIndex();
        EntryListWidget.Entry entrylistwidget$entry = i < 0 ? null : this.serverList.getEntry(i);
        if (key == 63) {
            this.refresh();
        } else {
            if (i >= 0) {
                if (key == 200) {
                    if (isShiftDown()) {
                        if (i > 0 && entrylistwidget$entry instanceof ServerListEntryWidget) {
                            this.servers.swap(i, i - 1);
                            this.moveToServer(this.serverList.getCurrentServerIndex() - 1);
                            this.serverList.scroll(-this.serverList.getEntryHeight());
                            this.serverList.setServers(this.servers);
                        }
                    } else if (i > 0) {
                        this.moveToServer(this.serverList.getCurrentServerIndex() - 1);
                        this.serverList.scroll(-this.serverList.getEntryHeight());
                        if (this.serverList.getEntry(this.serverList.getCurrentServerIndex()) instanceof LanScanWidget) {
                            if (this.serverList.getCurrentServerIndex() > 0) {
                                this.moveToServer(this.serverList.size() - 1);
                                this.serverList.scroll(-this.serverList.getEntryHeight());
                            } else {
                                this.moveToServer(-1);
                            }
                        }
                    } else {
                        this.moveToServer(-1);
                    }
                } else if (key == 208) {
                    if (isShiftDown()) {
                        if (i < this.servers.size() - 1) {
                            this.servers.swap(i, i + 1);
                            this.moveToServer(i + 1);
                            this.serverList.scroll(this.serverList.getEntryHeight());
                            this.serverList.setServers(this.servers);
                        }
                    } else if (i < this.serverList.size()) {
                        this.moveToServer(this.serverList.getCurrentServerIndex() + 1);
                        this.serverList.scroll(this.serverList.getEntryHeight());
                        if (this.serverList.getEntry(this.serverList.getCurrentServerIndex()) instanceof LanScanWidget) {
                            if (this.serverList.getCurrentServerIndex() < this.serverList.size() - 1) {
                                this.moveToServer(this.serverList.size() + 1);
                                this.serverList.scroll(this.serverList.getEntryHeight());
                            } else {
                                this.moveToServer(-1);
                            }
                        }
                    } else {
                        this.moveToServer(-1);
                    }
                } else if (key != 28 && key != 156) {
                    super.keyPressed(chr, key);
                } else {
                    this.buttonClicked(this.buttons.get(2));
                }
            } else {
                super.keyPressed(chr, key);
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.tooltip = null;
        this.renderBackground();
        this.serverList.render(mouseX, mouseY, tickDelta);
        this.drawCenteredString(this.textRenderer, I18n.translate("multiplayer.title"), this.width / 2, 20, 16777215);
        super.render(mouseX, mouseY, tickDelta);
        if (this.tooltip != null) {
            this.renderTooltip(Lists.newArrayList(Splitter.on("\n").split(this.tooltip)), mouseX, mouseY);
        }
    }

    public void connect() {
        EntryListWidget.Entry entrylistwidget$entry = this.serverList.getCurrentServerIndex() < 0
            ? null
            : this.serverList.getEntry(this.serverList.getCurrentServerIndex());
        if (entrylistwidget$entry instanceof ServerListEntryWidget) {
            this.connect(((ServerListEntryWidget)entrylistwidget$entry).fetchServer());
        } else if (entrylistwidget$entry instanceof LanServerEntry) {
            LanServerQueryManager.LanServer lanserverquerymanager$lanserver = ((LanServerEntry)entrylistwidget$entry).getLanServerInfo();
            this.connect(new ServerListEntry(lanserverquerymanager$lanserver.getMotd(), lanserverquerymanager$lanserver.getPort(), true));
        }
    }

    private void connect(ServerListEntry entry) {
        this.minecraft.openScreen(new ConnectScreen(this, this.minecraft, entry));
    }

    public void moveToServer(int index) {
        this.serverList.setCurrentServerIndex(index);
        EntryListWidget.Entry entrylistwidget$entry = index < 0 ? null : this.serverList.getEntry(index);
        this.joinButton.active = false;
        this.editButton.active = false;
        this.deleteButton.active = false;
        if (entrylistwidget$entry != null && !(entrylistwidget$entry instanceof LanScanWidget)) {
            this.joinButton.active = true;
            if (entrylistwidget$entry instanceof ServerListEntryWidget) {
                this.editButton.active = true;
                this.deleteButton.active = true;
            }
        }
    }

    public MultiplayerServerListPinger getServerListPinger() {
        return this.pinger;
    }

    public void setTooltip(String text) {
        this.tooltip = text;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        this.serverList.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button);
        this.serverList.mouseReleased(mouseX, mouseY, button);
    }

    public ServerList getServerList() {
        return this.servers;
    }

    public boolean canMoveUp(ServerListEntryWidget entry, int index) {
        return index > 0;
    }

    public boolean canMoveDown(ServerListEntryWidget entry, int index) {
        return index < this.servers.size() - 1;
    }

    public void moveUp(ServerListEntryWidget entry, int index, boolean moveToTop) {
        int i = moveToTop ? 0 : index - 1;
        this.servers.swap(index, i);
        if (this.serverList.getCurrentServerIndex() == index) {
            this.moveToServer(i);
        }

        this.serverList.setServers(this.servers);
    }

    public void moveDown(ServerListEntryWidget entry, int index, boolean moveToBottom) {
        int i = moveToBottom ? this.servers.size() - 1 : index + 1;
        this.servers.swap(index, i);
        if (this.serverList.getCurrentServerIndex() == index) {
            this.moveToServer(i);
        }

        this.serverList.setServers(this.servers);
    }
}
