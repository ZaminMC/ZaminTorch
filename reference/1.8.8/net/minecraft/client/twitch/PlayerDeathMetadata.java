package net.minecraft.client.twitch;

import net.minecraft.entity.living.LivingEntity;

public class PlayerDeathMetadata extends StreamMetadata {
    public PlayerDeathMetadata(LivingEntity player, LivingEntity killer) {
        super("player_death");
        if (player != null) {
            this.put("player", player.getName());
        }

        if (killer != null) {
            this.put("killer", killer.getName());
        }
    }
}
