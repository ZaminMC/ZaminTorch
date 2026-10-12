package net.zaminmc.torch.server.experience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The historical level curve's arithmetic: the three-band costs, the
 * level/progress derivations from a total, and the death-drop rule.
 */
class ExperienceMathTest {

    @Test
    void lowBandCostsGrowByTwoPerLevel() {
        assertEquals(7, ExperienceMath.xpToNextLevel(0));
        assertEquals(9, ExperienceMath.xpToNextLevel(1));
        assertEquals(37, ExperienceMath.xpToNextLevel(15), "level 15 is the last cheap rung");
    }

    @Test
    void midBandCostsGrowByFivePerLevel() {
        assertEquals(42, ExperienceMath.xpToNextLevel(16));
        assertEquals(112, ExperienceMath.xpToNextLevel(30), "level 30 is the last mid rung");
    }

    @Test
    void highBandCostsGrowByNinePerLevel() {
        assertEquals(121, ExperienceMath.xpToNextLevel(31));
        assertEquals(202, ExperienceMath.xpToNextLevel(40));
    }

    @Test
    void levelDerivesFromTheTotal() {
        // 16 points = level 0's 7 + level 1's 9 exactly: level 2.
        assertEquals(2, ExperienceMath.levelForTotalXp(16));
        // One point short of level 2.
        assertEquals(1, ExperienceMath.levelForTotalXp(15));
        assertEquals(0, ExperienceMath.levelForTotalXp(0));
        assertEquals(0, ExperienceMath.levelForTotalXp(-5), "negatives clamp to level 0");
        // The walk agrees with the cumulative table at level 30.
        assertEquals(30, ExperienceMath.levelForTotalXp(ExperienceMath.totalXpForLevel(30)));
    }

    @Test
    void progressFillsTheBarWithinALevel() {
        // Fresh level-1 body: 0 of 9 points into the rung.
        assertEquals(0.0f, ExperienceMath.progressForTotalXp(7), 0.0001f);
        // Half-way into the level-1 rung (9 points): 4 of 9.
        assertEquals(4.0f / 9.0f, ExperienceMath.progressForTotalXp(11), 0.01f);
        // A body at an exact level boundary reads an empty bar.
        assertEquals(0.0f, ExperienceMath.progressForTotalXp(16), 0.0001f);
    }

    @Test
    void deathDropsSevenPerLevelCappedAtOneHundred() {
        assertEquals(0, ExperienceMath.xpDroppedOnDeath(0));
        assertEquals(35, ExperienceMath.xpDroppedOnDeath(5));
        assertEquals(100, ExperienceMath.xpDroppedOnDeath(20), "level 20 caps the scatter");
        assertEquals(100, ExperienceMath.xpDroppedOnDeath(50));
    }

    @Test
    void roundTripLevelAndTotalStayConsistent() {
        for (int level : new int[] {0, 1, 15, 16, 30, 31, 45}) {
            long total = ExperienceMath.totalXpForLevel(level);
            assertEquals(level, ExperienceMath.levelForTotalXp(total),
                    "level " + level + " round-trips through its total");
            assertTrue(ExperienceMath.progressForTotalXp(total) <= 0.0001f,
                    "a body standing exactly on a level reads an empty bar");
        }
    }
}
