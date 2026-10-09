package net.minecraft.client.twitch;

import net.minecraft.stat.achievement.AchievementStat;

public class AchievementMetadata extends StreamMetadata {
    public AchievementMetadata(AchievementStat stat) {
        super("achievement");
        this.put("achievement_id", stat.key);
        this.put("achievement_name", stat.getDecoratedName().getString());
        this.put("achievement_description", stat.getDescription());
        this.setDescription("Achievement '" + stat.getDecoratedName().getString() + "' obtained!");
    }
}
