package net.minecraft.client.gui.screen;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.TreeMap;
import java.util.Map.Entry;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ListWidget;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.resource.language.I18n;

public class SnooperScreen extends Screen {
    private final Screen parent;
    private final GameOptions options;
    private final List<String> snooperKeys = Lists.newArrayList();
    private final List<String> snooperValues = Lists.newArrayList();
    private String title;
    private String[] description;
    private SnooperScreen.SnooperListWidget snooperList;
    private ButtonWidget snooperToggleButton;

    public SnooperScreen(Screen parent, GameOptions options) {
        this.parent = parent;
        this.options = options;
    }

    @Override
    public void init() {
        this.title = I18n.translate("options.snooper.title");
        String s = I18n.translate("options.snooper.desc");
        List<String> list = Lists.newArrayList();

        for (String s1 : this.textRenderer.split(s, this.width - 30)) {
            list.add(s1);
        }

        this.description = list.toArray(new String[list.size()]);
        this.snooperKeys.clear();
        this.snooperValues.clear();
        this.buttons
            .add(
                this.snooperToggleButton = new ButtonWidget(
                    1, this.width / 2 - 152, this.height - 30, 150, 20, this.options.getAsString(GameOptions.Option.SNOOPER_ENABLED)
                )
            );
        this.buttons.add(new ButtonWidget(2, this.width / 2 + 2, this.height - 30, 150, 20, I18n.translate("gui.done")));
        boolean flag = this.minecraft.getServer() != null && this.minecraft.getServer().getSnooper() != null;

        for (Entry<String, String> entry : new TreeMap<>(this.minecraft.getSnooper().getAll()).entrySet()) {
            this.snooperKeys.add((flag ? "C " : "") + entry.getKey());
            this.snooperValues.add(this.textRenderer.trim(entry.getValue(), this.width - 220));
        }

        if (flag) {
            for (Entry<String, String> entry1 : new TreeMap<>(this.minecraft.getServer().getSnooper().getAll()).entrySet()) {
                this.snooperKeys.add("S " + entry1.getKey());
                this.snooperValues.add(this.textRenderer.trim(entry1.getValue(), this.width - 220));
            }
        }

        this.snooperList = new SnooperScreen.SnooperListWidget();
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        this.snooperList.handleMouse();
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 2) {
                this.options.save();
                this.options.save();
                this.minecraft.openScreen(this.parent);
            }

            if (button.id == 1) {
                this.options.set(GameOptions.Option.SNOOPER_ENABLED, 1);
                this.snooperToggleButton.message = this.options.getAsString(GameOptions.Option.SNOOPER_ENABLED);
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.snooperList.render(mouseX, mouseY, tickDelta);
        this.drawCenteredString(this.textRenderer, this.title, this.width / 2, 8, 16777215);
        int i = 22;

        for (String s : this.description) {
            this.drawCenteredString(this.textRenderer, s, this.width / 2, i, 8421504);
            i += this.textRenderer.fontHeight;
        }

        super.render(mouseX, mouseY, tickDelta);
    }

    class SnooperListWidget extends ListWidget {
        public SnooperListWidget() {
            super(
                SnooperScreen.this.minecraft,
                SnooperScreen.this.width,
                SnooperScreen.this.height,
                80,
                SnooperScreen.this.height - 40,
                SnooperScreen.this.textRenderer.fontHeight + 1
            );
        }

        @Override
        protected int size() {
            return SnooperScreen.this.snooperKeys.size();
        }

        @Override
        protected void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
        }

        @Override
        protected boolean isEntrySelected(int index) {
            return false;
        }

        @Override
        protected void renderBackground() {
        }

        @Override
        protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
            SnooperScreen.this.textRenderer.draw(SnooperScreen.this.snooperKeys.get(index), 10, y, 16777215);
            SnooperScreen.this.textRenderer.draw(SnooperScreen.this.snooperValues.get(index), 230, y, 16777215);
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width - 10;
        }
    }
}
