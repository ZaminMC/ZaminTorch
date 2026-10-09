package net.minecraft.item;

import net.minecraft.block.material.MapColor;
import net.minecraft.text.Formatting;
import net.minecraft.util.StringSerializable;

public enum DyeColor implements StringSerializable {
    WHITE(0, 15, "white", "white", MapColor.WHITE, Formatting.WHITE),
    ORANGE(1, 14, "orange", "orange", MapColor.ORANGE, Formatting.GOLD),
    MAGENTA(2, 13, "magenta", "magenta", MapColor.MAGENTA, Formatting.AQUA),
    LIGHT_BLUE(3, 12, "light_blue", "lightBlue", MapColor.LIGHT_BLUE, Formatting.BLUE),
    YELLOW(4, 11, "yellow", "yellow", MapColor.YELLOW, Formatting.YELLOW),
    LIME(5, 10, "lime", "lime", MapColor.LIME, Formatting.GREEN),
    PINK(6, 9, "pink", "pink", MapColor.PINK, Formatting.LIGHT_PURPLE),
    GRAY(7, 8, "gray", "gray", MapColor.GRAY, Formatting.DARK_GRAY),
    SILVER(8, 7, "silver", "silver", MapColor.LIGHT_GRAY, Formatting.GRAY),
    CYAN(9, 6, "cyan", "cyan", MapColor.CYAN, Formatting.DARK_AQUA),
    PURPLE(10, 5, "purple", "purple", MapColor.PURPLE, Formatting.DARK_PURPLE),
    BLUE(11, 4, "blue", "blue", MapColor.BLUE, Formatting.DARK_BLUE),
    BROWN(12, 3, "brown", "brown", MapColor.BROWN, Formatting.GOLD),
    GREEN(13, 2, "green", "green", MapColor.GREEN, Formatting.DARK_GREEN),
    RED(14, 1, "red", "red", MapColor.RED, Formatting.DARK_RED),
    BLACK(15, 0, "black", "black", MapColor.BLACK, Formatting.BLACK);

    private static final DyeColor[] BY_ID = new DyeColor[values().length];
    private static final DyeColor[] BY_METADATA = new DyeColor[values().length];
    private final int id;
    private final int metadata;
    private final String key;
    private final String name;
    private final MapColor mapColor;
    private final Formatting formatting;

    DyeColor(int id, int metadata, String key, String name, MapColor mapColor, Formatting formatting) {
        this.id = id;
        this.metadata = metadata;
        this.key = key;
        this.name = name;
        this.mapColor = mapColor;
        this.formatting = formatting;
    }

    public int getId() {
        return this.id;
    }

    public int getMetadata() {
        return this.metadata;
    }

    public String getName() {
        return this.name;
    }

    public MapColor getMapColor() {
        return this.mapColor;
    }

    public static DyeColor byMetadata(int metadata) {
        if (metadata < 0 || metadata >= BY_METADATA.length) {
            metadata = 0;
        }

        return BY_METADATA[metadata];
    }

    public static DyeColor byId(int id) {
        if (id < 0 || id >= BY_ID.length) {
            id = 0;
        }

        return BY_ID[id];
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public String serializeToString() {
        return this.key;
    }

    static {
        for (DyeColor dyecolor : values()) {
            BY_ID[dyecolor.getId()] = dyecolor;
            BY_METADATA[dyecolor.getMetadata()] = dyecolor;
        }
    }
}
