package net.minecraft.server.dedicated.gui;

import java.util.Vector;
import javax.swing.JList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Tickable;

public class PlayerListGui extends JList implements Tickable {
    private MinecraftServer server;
    private int tick;

    public PlayerListGui(MinecraftServer server) {
        this.server = server;
        server.addTickable(this);
    }

    @Override
    public void tick() {
        if (this.tick++ % 20 == 0) {
            Vector<String> vector = new Vector<>();

            for (int i = 0; i < this.server.getPlayerManager().getAll().size(); i++) {
                vector.add(this.server.getPlayerManager().getAll().get(i).getName());
            }

            this.setListData(vector);
        }
    }
}
