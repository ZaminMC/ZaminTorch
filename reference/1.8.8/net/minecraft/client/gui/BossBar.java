package net.minecraft.client.gui;

import net.minecraft.entity.living.mob.monster.boss.Boss;

public final class BossBar {
    public static float health;
    public static int timer;
    public static String name;
    public static boolean modifiesSkyColor;

    public static void update(Boss boss, boolean modifiesSkyColor) {
        health = boss.getHealth() / boss.getMaxHealth();
        timer = 100;
        name = boss.getDisplayName().getFormattedString();
        BossBar.modifiesSkyColor = modifiesSkyColor;
    }
}
