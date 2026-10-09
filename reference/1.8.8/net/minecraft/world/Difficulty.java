package net.minecraft.world;

public enum Difficulty {
    PEACEFUL(0, "options.difficulty.peaceful"),
    EASY(1, "options.difficulty.easy"),
    NORMAL(2, "options.difficulty.normal"),
    HARD(3, "options.difficulty.hard");

    private static final Difficulty[] BY_ID = new Difficulty[values().length];
    private final int id;
    private final String key;

    Difficulty(int id, String key) {
        this.id = id;
        this.key = key;
    }

    public int getId() {
        return this.id;
    }

    public static Difficulty byId(int id) {
        return BY_ID[id % BY_ID.length];
    }

    public String getKey() {
        return this.key;
    }

    static {
        for (Difficulty difficulty : values()) {
            BY_ID[difficulty.id] = difficulty;
        }
    }
}
