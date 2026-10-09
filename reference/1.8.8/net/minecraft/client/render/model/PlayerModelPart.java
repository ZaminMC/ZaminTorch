package net.minecraft.client.render.model;

import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public enum PlayerModelPart {
    CAPE(0, "cape"),
    JACKET(1, "jacket"),
    LEFT_SLEEVE(2, "left_sleeve"),
    RIGHT_SLEEVE(3, "right_sleeve"),
    LEFT_PANTS_LEG(4, "left_pants_leg"),
    RIGHT_PANTS_LEG(5, "right_pants_leg"),
    HAT(6, "hat");

    private final int index;
    private final int flag;
    private final String key;
    private final Text name;

    PlayerModelPart(int index, String key) {
        this.index = index;
        this.flag = 1 << index;
        this.key = key;
        this.name = new TranslatableText("options.modelPart." + key);
    }

    public int getFlag() {
        return this.flag;
    }

    public int getIndex() {
        return this.index;
    }

    public String getKey() {
        return this.key;
    }

    public Text getName() {
        return this.name;
    }
}
