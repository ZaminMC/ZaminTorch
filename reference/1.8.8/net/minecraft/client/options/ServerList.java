package net.minecraft.client.options;

import com.google.common.collect.Lists;
import java.io.File;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerList {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Minecraft minecraft;
    private final List<ServerListEntry> entries = Lists.newArrayList();

    public ServerList(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.load();
    }

    public void load() {
        try {
            this.entries.clear();
            NbtCompound nbtcompound = NbtIo.read(new File(this.minecraft.gameDir, "servers.dat"));
            if (nbtcompound == null) {
                return;
            }

            NbtList nbtlist = nbtcompound.getList("servers", 10);

            for (int i = 0; i < nbtlist.size(); i++) {
                this.entries.add(ServerListEntry.fromNbt(nbtlist.getCompound(i)));
            }
        } catch (Exception exception) {
            LOGGER.error("Couldn't load server list", exception);
        }
    }

    public void save() {
        try {
            NbtList nbtlist = new NbtList();

            for (ServerListEntry serverlistentry : this.entries) {
                nbtlist.addElement(serverlistentry.toNbt());
            }

            NbtCompound nbtcompound = new NbtCompound();
            nbtcompound.put("servers", nbtlist);
            NbtIo.writeSafely(nbtcompound, new File(this.minecraft.gameDir, "servers.dat"));
        } catch (Exception exception) {
            LOGGER.error("Couldn't save server list", exception);
        }
    }

    public ServerListEntry get(int index) {
        return this.entries.get(index);
    }

    public void remove(int index) {
        this.entries.remove(index);
    }

    public void add(ServerListEntry entry) {
        this.entries.add(entry);
    }

    public int size() {
        return this.entries.size();
    }

    public void swap(int index1, int index2) {
        ServerListEntry serverlistentry = this.get(index1);
        this.entries.set(index1, this.get(index2));
        this.entries.set(index2, serverlistentry);
        this.save();
    }

    public void set(int index, ServerListEntry entry) {
        this.entries.set(index, entry);
    }

    /**
     * Update the given server entry in the {@code servers.dat} file.
     */
    public static void update(ServerListEntry entry) {
        ServerList serverlist = new ServerList(Minecraft.getInstance());
        serverlist.load();

        for (int i = 0; i < serverlist.size(); i++) {
            ServerListEntry serverlistentry = serverlist.get(i);
            if (serverlistentry.name.equals(entry.name) && serverlistentry.ip.equals(entry.ip)) {
                serverlist.set(i, entry);
                break;
            }
        }

        serverlist.save();
    }
}
