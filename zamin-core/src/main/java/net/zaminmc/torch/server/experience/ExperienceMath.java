package net.zaminmc.torch.server.experience;

/**
 * The historical 1.8 level curve (Glowstone's player-XP path, ported as the
 * engine's own arithmetic): the XP needed to advance from one level to the
 * next grows in three bands, and a body's bar fills by {@code totalXp}
 * against the next level's cost.
 *
 * <p>The engine stores a single integer of total XP per player; level and
 * progress are pure derivations (the same relationship vanilla's fields
 * keep, with totalXp as the authoritative state so persistence is one
 * number).</p>
 */
public final class ExperienceMath {

    private ExperienceMath() {
    }

    /**
     * @return the XP required to advance FROM {@code level} to level+1
     *         (the historical {@code xpBarCap}: 7 + 2·level under 16,
     *         37 + 5·(level-15) under 31, 112 + 9·(level-30) beyond).
     */
    public static int xpToNextLevel(int level) {
        if (level < 0) {
            return 0;
        }
        if (level < 16) {
            return 2 * level + 7;
        }
        if (level < 31) {
            return 5 * level - 38;
        }
        return 9 * level - 158;
    }

    /**
     * @return the level a body with {@code totalXp} accumulated points sits
     *         at (the loop walks the same curve; a few dozen iterations even
     *         at end-game levels, so no closed form is needed).
     */
    public static int levelForTotalXp(long totalXp) {
        int level = 0;
        long remaining = Math.max(0, totalXp);
        while (remaining >= xpToNextLevel(level) && level < Integer.MAX_VALUE - 1) {
            remaining -= xpToNextLevel(level);
            level++;
        }
        return level;
    }

    /**
     * @return the fraction (0..1) the XP bar has filled toward the next
     *         level, the {@code Set Experience} packet's progress float.
     */
    public static float progressForTotalXp(long totalXp) {
        int level = levelForTotalXp(totalXp);
        long intoLevel = Math.max(0, totalXp - totalXpForLevel(level));
        int cost = xpToNextLevel(level);
        if (cost <= 0) {
            return 0.0f;
        }
        return Math.min(1.0f, (float) intoLevel / cost);
    }

    /** @return the total XP a body needs to reach the start of {@code level}. */
    public static long totalXpForLevel(int level) {
        long total = 0;
        for (int i = 0; i < level; i++) {
            total += xpToNextLevel(i);
        }
        return total;
    }

    /**
     * The historical death-drop rule (vanilla {@code dropFewItems} on the
     * player): a dead body scatters 7 points per level, capped at 100.
     *
     * @return the XP the death scatters into orbs (0 at level 0).
     */
    public static int xpDroppedOnDeath(int level) {
        return Math.min(7 * level, 100);
    }
}
