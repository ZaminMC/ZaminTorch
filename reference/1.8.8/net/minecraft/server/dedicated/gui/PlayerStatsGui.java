package net.minecraft.server.dedicated.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import javax.swing.JComponent;
import javax.swing.Timer;
import net.minecraft.server.MinecraftServer;

public class PlayerStatsGui extends JComponent {
    private static final DecimalFormat AVG_TICK_FORMAT = new DecimalFormat("########0.000");
    private int[] memoryUsePercentage = new int[256];
    private int memoryUsage;
    private String[] lines = new String[11];
    private final MinecraftServer server;

    public PlayerStatsGui(MinecraftServer server) {
        this.server = server;
        this.setPreferredSize(new Dimension(456, 246));
        this.setMinimumSize(new Dimension(456, 246));
        this.setMaximumSize(new Dimension(456, 246));
        new Timer(500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PlayerStatsGui.this.update();
            }
        }).start();
        this.setBackground(Color.BLACK);
    }

    private void update() {
        long i = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.gc();
        this.lines[0] = "Memory use: " + i / 1024L / 1024L + " mb (" + Runtime.getRuntime().freeMemory() * 100L / Runtime.getRuntime().maxMemory() + "% free)";
        this.lines[1] = "Avg tick: " + AVG_TICK_FORMAT.format(this.average(this.server.averageTickTimes) * 1.0E-6) + " ms";
        this.repaint();
    }

    private double average(long[] longs) {
        long i = 0L;

        for (int j = 0; j < longs.length; j++) {
            i += longs[j];
        }

        return (double)i / longs.length;
    }

    @Override
    public void paint(Graphics graphics) {
        graphics.setColor(new Color(16777215));
        graphics.fillRect(0, 0, 456, 246);

        for (int i = 0; i < 256; i++) {
            int j = this.memoryUsePercentage[i + this.memoryUsage & 0xFF];
            graphics.setColor(new Color(j + 28 << 16));
            graphics.fillRect(i, 100 - j, 1, j);
        }

        graphics.setColor(Color.BLACK);

        for (int k = 0; k < this.lines.length; k++) {
            String s = this.lines[k];
            if (s != null) {
                graphics.drawString(s, 32, 116 + k * 16);
            }
        }
    }
}
