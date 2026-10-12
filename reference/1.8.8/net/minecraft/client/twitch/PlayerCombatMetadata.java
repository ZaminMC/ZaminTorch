package net.minecraft.client.twitch;

import net.minecraft.entity.living.LivingEntity;

public class PlayerCombatMetadata extends StreamMetadata {
    public PlayerCombatMetadata(LivingEntity player, LivingEntity opponent) {
        super("player_combat");
        this.put("player", player.getName());
        if (opponent != null) {
            this.put("primary_opponent", opponent.getName());
        }

        if (opponent != null) {
            this.setDescription("Combat between " + player.getName() + " and " + opponent.getName());
        } else {
            this.setDescription("Combat between " + player.getName() + " and others");
        }
    }
}
