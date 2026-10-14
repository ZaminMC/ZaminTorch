package net.zaminmc.torch.server.experience;

import net.zaminmc.torch.util.Identifier;

import java.util.Map;
import java.util.Random;

/**
 * The XP award sources of the vanilla world, as data: mining rolls per ore
 * (the historical {@code dropXp} tables) and kill rewards per mob family
 * (the historical {@code getExperienceValue}: 5 for hostiles, 1-3 for the
 * wanderers). Iron and gold ore pay nothing at the pickaxe — their reward
 * rides the furnace take in vanilla, which the furnace slice tracks for a
 * later pass.
 */
public final class ExperienceAwards {

    private ExperienceAwards() {
    }

    /** Mining rolls: identifier prefix → roll bounds (min inclusive, max exclusive). */
    private static final Map<String, int[]> MINING_ROLLS = Map.ofEntries(
            Map.entry("minecraft:coal_ore", new int[]{0, 3}),
            Map.entry("minecraft:diamond_ore", new int[]{3, 8}),
            Map.entry("minecraft:redstone_ore", new int[]{1, 6}),
            // The vault's ores (Slice 32 — OreBlock.dropItems lines 58-77 +
            // RedstoneOreBlock line 84's own 1-5): lapis 2-5, emerald 3-7,
            // quartz 2-5.
            Map.entry("minecraft:lapis_ore", new int[]{2, 6}),
            Map.entry("minecraft:emerald_ore", new int[]{3, 8}),
            Map.entry("minecraft:quartz_ore", new int[]{2, 6}));

    /** Hostile kill reward (the historical 5 XP band, ±0 variance this slice). */
    public static final int HOSTILE_KILL_XP = 5;

    /**
     * @return the XP a survival break of the block yields (0 for anything
     *         that is not an XP ore; the roll is the historical per-break roll).
     */
    public static int forBlockBreak(Identifier blockIdentifier, Random random) {
        int[] roll = MINING_ROLLS.get(blockIdentifier.toString());
        if (roll == null) {
            return 0;
        }
        return roll[0] + random.nextInt(roll[1] - roll[0]);
    }

    /**
     * @return the XP a killed mob's body releases: 5 for the hostile band,
     *         1-3 for the wanderers (the historical passive roll).
     */
    public static int forMobKill(boolean hostile, Random random) {
        return hostile ? HOSTILE_KILL_XP : 1 + random.nextInt(3);
    }
}
