package net.minecraft.stat;

import net.minecraft.text.Text;

public class DistanceStat extends Stat {
    public DistanceStat(String string, Text text, StatFormatter statFormatter) {
        super(string, text, statFormatter);
    }

    public DistanceStat(String string, Text text) {
        super(string, text);
    }

    @Override
    public Stat register() {
        super.register();
        Stats.GENERAL.add(this);
        return this;
    }
}
