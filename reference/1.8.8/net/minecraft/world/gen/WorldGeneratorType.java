package net.minecraft.world.gen;

public class WorldGeneratorType {
    public static final WorldGeneratorType[] BY_ID = new WorldGeneratorType[16];
    public static final WorldGeneratorType DEFAULT = new WorldGeneratorType(0, "default", 1).setVersioned();
    public static final WorldGeneratorType FLAT = new WorldGeneratorType(1, "flat");
    public static final WorldGeneratorType LARGE_BIOMES = new WorldGeneratorType(2, "largeBiomes");
    public static final WorldGeneratorType AMPLIFIED = new WorldGeneratorType(3, "amplified").setHasInfo();
    public static final WorldGeneratorType CUSTOMIZED = new WorldGeneratorType(4, "customized");
    public static final WorldGeneratorType DEBUG_ALL_BLOCK_STATES = new WorldGeneratorType(5, "debug_all_block_states");
    /**
     * The default WorldType from minecraft 1.1
     */
    public static final WorldGeneratorType DEFAULT_1_1 = new WorldGeneratorType(8, "default_1_1", 0).setVisible(false);
    private final int id;
    private final String key;
    private final int version;
    private boolean visible;
    private boolean versioned;
    private boolean info;

    private WorldGeneratorType(int id, String key) {
        this(id, key, 0);
    }

    private WorldGeneratorType(int id, String key, int version) {
        this.key = key;
        this.version = version;
        this.visible = true;
        this.id = id;
        BY_ID[id] = this;
    }

    public String getKey() {
        return this.key;
    }

    public String getTranslationKey() {
        return "generator." + this.key;
    }

    public String getInfoTranslationKey() {
        return this.getTranslationKey() + ".info";
    }

    public int getVersion() {
        return this.version;
    }

    public WorldGeneratorType getTypeForVersion(int version) {
        return this == DEFAULT && version == 0 ? DEFAULT_1_1 : this;
    }

    private WorldGeneratorType setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public boolean isVisible() {
        return this.visible;
    }

    private WorldGeneratorType setVersioned() {
        this.versioned = true;
        return this;
    }

    public boolean isVersioned() {
        return this.versioned;
    }

    public static WorldGeneratorType byKey(String key) {
        for (int i = 0; i < BY_ID.length; i++) {
            if (BY_ID[i] != null && BY_ID[i].key.equalsIgnoreCase(key)) {
                return BY_ID[i];
            }
        }

        return null;
    }

    public int getId() {
        return this.id;
    }

    public boolean hasInfo() {
        return this.info;
    }

    private WorldGeneratorType setHasInfo() {
        this.info = true;
        return this;
    }
}
