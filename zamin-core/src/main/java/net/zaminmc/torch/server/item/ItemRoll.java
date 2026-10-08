package net.zaminmc.torch.server.item;

/**
 * One line of a mob's loot table: item type, minimum and maximum count.
 * The roll is inclusive on both ends; min &gt; max collapses to min.
 * (Data shape mirrors the community loot convention; the values live in the
 * entity package's {@code MobType} table.)
 */
public record ItemRoll(EngineItemType type, int min, int max) {

    public ItemRoll {
        if (min < 0) {
            throw new IllegalArgumentException("Loot min cannot be negative: " + min);
        }
        if (max < min) {
            max = min;
        }
    }

    /** Rolls one outcome with the given random source. */
    public int roll(java.util.Random random) {
        if (max == min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }
}
