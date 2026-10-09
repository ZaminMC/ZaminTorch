package net.minecraft.item;

import net.minecraft.text.Formatting;

public enum Rarity {
    COMMON(Formatting.WHITE, "Common"),
    UNCOMMON(Formatting.YELLOW, "Uncommon"),
    RARE(Formatting.AQUA, "Rare"),
    EPIC(Formatting.LIGHT_PURPLE, "Epic");

    public final Formatting formatting;
    public final String name;

    Rarity(Formatting formatting, String name) {
        this.formatting = formatting;
        this.name = name;
    }
}
