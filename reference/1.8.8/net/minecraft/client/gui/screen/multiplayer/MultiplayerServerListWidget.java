package net.minecraft.client.gui.screen.multiplayer;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.widget.LanServerEntry;
import net.minecraft.client.gui.widget.ServerListEntryWidget;
import net.minecraft.client.network.LanServerQueryManager;
import net.minecraft.client.options.ServerList;

public class MultiplayerServerListWidget extends EntryListWidget {
    private final MultiplayerScreen parent;
    /**
     * External servers (i.e. servers you access with an ip)
     */
    private final List<ServerListEntryWidget> servers = Lists.newArrayList();
    /**
     * LAN servers or local servers (i.e. servers on your local network)
     */
    private final List<LanServerEntry> lanServers = Lists.newArrayList();
    /**
     * The widget you see that is scanning for LAN servers when there are no servers in the server list.
     */
    private final EntryListWidget.Entry scanningWidget = new LanScanWidget();
    /**
     * The index of the server that is currently selected.
     */
    private int currentServerIndex = -1;

    public MultiplayerServerListWidget(MultiplayerScreen parent, Minecraft minecraft, int x, int y, int yStart, int yEnd, int entryHeight) {
        super(minecraft, x, y, yStart, yEnd, entryHeight);
        this.parent = parent;
    }

    @Override
    public EntryListWidget.Entry getEntry(int index) {
        if (index < this.servers.size()) {
            return this.servers.get(index);
        }

        index -= this.servers.size();
        return index == 0 ? this.scanningWidget : this.lanServers.get(--index);
    }

    @Override
    protected int size() {
        return this.servers.size() + 1 + this.lanServers.size();
    }

    public void setCurrentServerIndex(int index) {
        this.currentServerIndex = index;
    }

    @Override
    protected boolean isEntrySelected(int index) {
        return index == this.currentServerIndex;
    }

    public int getCurrentServerIndex() {
        return this.currentServerIndex;
    }

    public void setServers(ServerList servers) {
        this.servers.clear();

        for (int i = 0; i < servers.size(); i++) {
            this.servers.add(new ServerListEntryWidget(this.parent, servers.get(i)));
        }
    }

    public void setLanServers(List<LanServerQueryManager.LanServer> lanServers) {
        this.lanServers.clear();

        for (LanServerQueryManager.LanServer lanserverquerymanager$lanserver : lanServers) {
            this.lanServers.add(new LanServerEntry(this.parent, lanserverquerymanager$lanserver));
        }
    }

    @Override
    protected int getScrollbarPosition() {
        return super.getScrollbarPosition() + 30;
    }

    @Override
    public int getRowWidth() {
        return super.getRowWidth() + 85;
    }
}
