package net.minecraft.client.gui.widget;

import net.minecraft.client.options.GameOptions;

public class OptionButtonWidget extends ButtonWidget {
    private final GameOptions.Option option;

    public OptionButtonWidget(int i, int j, int k, String string) {
        this(i, j, k, null, string);
    }

    public OptionButtonWidget(int i, int j, int k, int l, int m, String string) {
        super(i, j, k, l, m, string);
        this.option = null;
    }

    public OptionButtonWidget(int id, int x, int y, GameOptions.Option option, String message) {
        super(id, x, y, 150, 20, message);
        this.option = option;
    }

    public GameOptions.Option getOption() {
        return this.option;
    }
}
