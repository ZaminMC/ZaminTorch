package net.minecraft.client.gui.screen;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.menu.StatsListener;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ListWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.entity.Entities;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.resource.Identifier;
import net.minecraft.stat.ItemStat;
import net.minecraft.stat.PlayerStats;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import org.lwjgl.input.Mouse;

public class StatsScreen extends Screen implements StatsListener {
    protected Screen parent;
    protected String title = "Select world";
    private StatsScreen.GeneralStatsListWidget generalStats;
    private StatsScreen.ItemStatsListWidget itemStats;
    private StatsScreen.StatsListWidget blockStats;
    private StatsScreen.EntityStatsListWidget mobStats;
    private PlayerStats stats;
    private ListWidget selectedStatsList;
    private boolean downloadingStats = true;

    public StatsScreen(Screen parent, PlayerStats stats) {
        this.parent = parent;
        this.stats = stats;
    }

    @Override
    public void init() {
        this.title = I18n.translate("gui.stats");
        this.downloadingStats = true;
        this.minecraft.getNetworkHandler().sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Status.REQUEST_STATS));
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        if (this.selectedStatsList != null) {
            this.selectedStatsList.handleMouse();
        }
    }

    public void createLists() {
        this.generalStats = new StatsScreen.GeneralStatsListWidget(this.minecraft);
        this.generalStats.setScrollButtonIds(1, 1);
        this.itemStats = new StatsScreen.ItemStatsListWidget(this.minecraft);
        this.itemStats.setScrollButtonIds(1, 1);
        this.blockStats = new StatsScreen.StatsListWidget(this.minecraft);
        this.blockStats.setScrollButtonIds(1, 1);
        this.mobStats = new StatsScreen.EntityStatsListWidget(this.minecraft);
        this.mobStats.setScrollButtonIds(1, 1);
    }

    public void createButtons() {
        this.buttons.add(new ButtonWidget(0, this.width / 2 + 4, this.height - 28, 150, 20, I18n.translate("gui.done")));
        this.buttons.add(new ButtonWidget(1, this.width / 2 - 160, this.height - 52, 80, 20, I18n.translate("stat.generalButton")));
        ButtonWidget buttonwidget;
        this.buttons.add(buttonwidget = new ButtonWidget(2, this.width / 2 - 80, this.height - 52, 80, 20, I18n.translate("stat.blocksButton")));
        ButtonWidget buttonwidget1;
        this.buttons.add(buttonwidget1 = new ButtonWidget(3, this.width / 2, this.height - 52, 80, 20, I18n.translate("stat.itemsButton")));
        ButtonWidget buttonwidget2;
        this.buttons.add(buttonwidget2 = new ButtonWidget(4, this.width / 2 + 80, this.height - 52, 80, 20, I18n.translate("stat.mobsButton")));
        if (this.blockStats.size() == 0) {
            buttonwidget.active = false;
        }

        if (this.itemStats.size() == 0) {
            buttonwidget1.active = false;
        }

        if (this.mobStats.size() == 0) {
            buttonwidget2.active = false;
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 0) {
                this.minecraft.openScreen(this.parent);
            } else if (button.id == 1) {
                this.selectedStatsList = this.generalStats;
            } else if (button.id == 3) {
                this.selectedStatsList = this.itemStats;
            } else if (button.id == 2) {
                this.selectedStatsList = this.blockStats;
            } else if (button.id == 4) {
                this.selectedStatsList = this.mobStats;
            } else {
                this.selectedStatsList.buttonClicked(button);
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        if (this.downloadingStats) {
            this.renderBackground();
            this.drawCenteredString(this.textRenderer, I18n.translate("multiplayer.downloadingStats"), this.width / 2, this.height / 2, 16777215);
            this.drawCenteredString(
                this.textRenderer,
                PROGRESS_BAR_STAGES[(int)(Minecraft.getTime() / 150L % PROGRESS_BAR_STAGES.length)],
                this.width / 2,
                this.height / 2 + this.textRenderer.fontHeight * 2,
                16777215
            );
        } else {
            this.selectedStatsList.render(mouseX, mouseY, tickDelta);
            this.drawCenteredString(this.textRenderer, this.title, this.width / 2, 20, 16777215);
            super.render(mouseX, mouseY, tickDelta);
        }
    }

    @Override
    public void onStatsReady() {
        if (this.downloadingStats) {
            this.createLists();
            this.createButtons();
            this.selectedStatsList = this.generalStats;
            this.downloadingStats = false;
        }
    }

    @Override
    public boolean shouldPauseGame() {
        return !this.downloadingStats;
    }

    private void renderStatIcon(int x, int y, Item item) {
        this.renderSlot(x + 1, y + 1);
        GlStateManager.enableRescaleNormal();
        Lighting.turnOnGui();
        this.itemRenderer.renderGuiItemModel(new ItemStack(item, 1, 0), x + 2, y + 2);
        Lighting.turnOff();
        GlStateManager.disableRescaleNormal();
    }

    private void renderSlot(int x, int y) {
        this.renderSlot(x, y, 0, 0);
    }

    private void renderSlot(int x, int y, int u, int v) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(STATS_ICONS_LOCATION);
        float f = 0.0078125F;
        float f1 = 0.0078125F;
        int i = 18;
        int j = 18;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(x + 0, y + 18, this.drawOffset).texture((u + 0) * 0.0078125F, (v + 18) * 0.0078125F).nextVertex();
        bufferbuilder.vertex(x + 18, y + 18, this.drawOffset).texture((u + 18) * 0.0078125F, (v + 18) * 0.0078125F).nextVertex();
        bufferbuilder.vertex(x + 18, y + 0, this.drawOffset).texture((u + 18) * 0.0078125F, (v + 0) * 0.0078125F).nextVertex();
        bufferbuilder.vertex(x + 0, y + 0, this.drawOffset).texture((u + 0) * 0.0078125F, (v + 0) * 0.0078125F).nextVertex();
        tesselator.end();
    }

    abstract class AbstractStatsListWidget extends ListWidget {
        protected int clickedIconId = -1;
        protected List<ItemStat> entries;
        protected Comparator<ItemStat> statComparator;
        protected int selectedTab = -1;
        protected int statSortOrder;

        protected AbstractStatsListWidget(Minecraft minecraft) {
            super(minecraft, StatsScreen.this.width, StatsScreen.this.height, 32, StatsScreen.this.height - 64, 20);
            this.setRenderSelectionHighlight(false);
            this.setHeader(true, 20);
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
            StatsScreen.this.renderBackground();
        }

        @Override
        protected void renderHeader(int x, int y, Tesselator tesselator) {
            if (!Mouse.isButtonDown(0)) {
                this.clickedIconId = -1;
            }

            if (this.clickedIconId == 0) {
                StatsScreen.this.renderSlot(x + 115 - 18, y + 1, 0, 0);
            } else {
                StatsScreen.this.renderSlot(x + 115 - 18, y + 1, 0, 18);
            }

            if (this.clickedIconId == 1) {
                StatsScreen.this.renderSlot(x + 165 - 18, y + 1, 0, 0);
            } else {
                StatsScreen.this.renderSlot(x + 165 - 18, y + 1, 0, 18);
            }

            if (this.clickedIconId == 2) {
                StatsScreen.this.renderSlot(x + 215 - 18, y + 1, 0, 0);
            } else {
                StatsScreen.this.renderSlot(x + 215 - 18, y + 1, 0, 18);
            }

            if (this.selectedTab != -1) {
                int i = 79;
                int j = 18;
                if (this.selectedTab == 1) {
                    i = 129;
                } else if (this.selectedTab == 2) {
                    i = 179;
                }

                if (this.statSortOrder == 1) {
                    j = 36;
                }

                StatsScreen.this.renderSlot(x + i, y + 1, j, 0);
            }
        }

        @Override
        protected void headerClicked(int x, int y) {
            this.clickedIconId = -1;
            if (x >= 79 && x < 115) {
                this.clickedIconId = 0;
            } else if (x >= 129 && x < 165) {
                this.clickedIconId = 1;
            } else if (x >= 179 && x < 215) {
                this.clickedIconId = 2;
            }

            if (this.clickedIconId >= 0) {
                this.click(this.clickedIconId);
                this.minecraft.getSoundManager().play(SimpleSoundInstance.of(new Identifier("gui.button.press"), 1.0F));
            }
        }

        @Override
        protected final int size() {
            return this.entries.size();
        }

        protected final ItemStat getEntry(int index) {
            return this.entries.get(index);
        }

        protected abstract String getColumnHeader(int column);

        protected void renderStat(Stat stat, int x, int y, boolean isRowEven) {
            if (stat != null) {
                String s = stat.format(StatsScreen.this.stats.get(stat));
                StatsScreen.this.drawString(
                    StatsScreen.this.textRenderer, s, x - StatsScreen.this.textRenderer.getWidth(s), y + 5, isRowEven ? 16777215 : 9474192
                );
            } else {
                String s1 = "-";
                StatsScreen.this.drawString(
                    StatsScreen.this.textRenderer, s1, x - StatsScreen.this.textRenderer.getWidth(s1), y + 5, isRowEven ? 16777215 : 9474192
                );
            }
        }

        @Override
        protected void renderDecorations(int mouseX, int mouseY) {
            if (mouseY >= this.minY && mouseY <= this.maxY) {
                int i = this.getEntryAt(mouseX, mouseY);
                int j = this.width / 2 - 92 - 16;
                if (i >= 0) {
                    if (mouseX < j + 40 || mouseX > j + 40 + 20) {
                        return;
                    }

                    ItemStat itemstat = this.getEntry(i);
                    this.renderStat(itemstat, mouseX, mouseY);
                } else {
                    String s = "";
                    if (mouseX >= j + 115 - 18 && mouseX <= j + 115) {
                        s = this.getColumnHeader(0);
                    } else if (mouseX >= j + 165 - 18 && mouseX <= j + 165) {
                        s = this.getColumnHeader(1);
                    } else {
                        if (mouseX < j + 215 - 18 || mouseX > j + 215) {
                            return;
                        }

                        s = this.getColumnHeader(2);
                    }

                    s = ("" + I18n.translate(s)).trim();
                    if (s.length() > 0) {
                        int k = mouseX + 12;
                        int l = mouseY - 12;
                        int i1 = StatsScreen.this.textRenderer.getWidth(s);
                        StatsScreen.this.fillGradient(k - 3, l - 3, k + i1 + 3, l + 8 + 3, -1073741824, -1073741824);
                        StatsScreen.this.textRenderer.drawWithShadow(s, k, l, -1);
                    }
                }
            }
        }

        protected void renderStat(ItemStat stat, int x, int z) {
            if (stat != null) {
                Item item = stat.getItem();
                ItemStack itemstack = new ItemStack(item);
                String s = itemstack.getTranslationKey();
                String s1 = ("" + I18n.translate(s + ".name")).trim();
                if (s1.length() > 0) {
                    int i = x + 12;
                    int j = z - 12;
                    int k = StatsScreen.this.textRenderer.getWidth(s1);
                    StatsScreen.this.fillGradient(i - 3, j - 3, i + k + 3, j + 8 + 3, -1073741824, -1073741824);
                    StatsScreen.this.textRenderer.drawWithShadow(s1, i, j, -1);
                }
            }
        }

        protected void click(int buttonDownTime) {
            if (buttonDownTime != this.selectedTab) {
                this.selectedTab = buttonDownTime;
                this.statSortOrder = -1;
            } else if (this.statSortOrder == -1) {
                this.statSortOrder = 1;
            } else {
                this.selectedTab = -1;
                this.statSortOrder = 0;
            }

            Collections.sort(this.entries, this.statComparator);
        }
    }

    class EntityStatsListWidget extends ListWidget {
        private final List<Entities.SpawnEggData> entries = Lists.newArrayList();

        public EntityStatsListWidget(Minecraft minecraft) {
            super(minecraft, StatsScreen.this.width, StatsScreen.this.height, 32, StatsScreen.this.height - 64, StatsScreen.this.textRenderer.fontHeight * 4);
            this.setRenderSelectionHighlight(false);

            for (Entities.SpawnEggData entities$spawneggdata : Entities.SPAWN_EGG_DATA.values()) {
                if (StatsScreen.this.stats.get(entities$spawneggdata.killEntityStat) > 0
                    || StatsScreen.this.stats.get(entities$spawneggdata.entityKilledByStat) > 0) {
                    this.entries.add(entities$spawneggdata);
                }
            }
        }

        @Override
        protected int size() {
            return this.entries.size();
        }

        @Override
        protected void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
        }

        @Override
        protected boolean isEntrySelected(int index) {
            return false;
        }

        @Override
        protected int getHeight() {
            return this.size() * StatsScreen.this.textRenderer.fontHeight * 4;
        }

        @Override
        protected void renderBackground() {
            StatsScreen.this.renderBackground();
        }

        @Override
        protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
            Entities.SpawnEggData entities$spawneggdata = this.entries.get(index);
            String s = I18n.translate("entity." + Entities.getKey(entities$spawneggdata.id) + ".name");
            int i = StatsScreen.this.stats.get(entities$spawneggdata.killEntityStat);
            int j = StatsScreen.this.stats.get(entities$spawneggdata.entityKilledByStat);
            String s1 = I18n.translate("stat.entityKills", i, s);
            String s2 = I18n.translate("stat.entityKilledBy", s, j);
            if (i == 0) {
                s1 = I18n.translate("stat.entityKills.none", s);
            }

            if (j == 0) {
                s2 = I18n.translate("stat.entityKilledBy.none", s);
            }

            StatsScreen.this.drawString(StatsScreen.this.textRenderer, s, x + 2 - 10, y + 1, 16777215);
            StatsScreen.this.drawString(StatsScreen.this.textRenderer, s1, x + 2, y + 1 + StatsScreen.this.textRenderer.fontHeight, i == 0 ? 6316128 : 9474192);
            StatsScreen.this.drawString(
                StatsScreen.this.textRenderer, s2, x + 2, y + 1 + StatsScreen.this.textRenderer.fontHeight * 2, j == 0 ? 6316128 : 9474192
            );
        }
    }

    class GeneralStatsListWidget extends ListWidget {
        public GeneralStatsListWidget(Minecraft minecraft) {
            super(minecraft, StatsScreen.this.width, StatsScreen.this.height, 32, StatsScreen.this.height - 64, 10);
            this.setRenderSelectionHighlight(false);
        }

        @Override
        protected int size() {
            return Stats.GENERAL.size();
        }

        @Override
        protected void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
        }

        @Override
        protected boolean isEntrySelected(int index) {
            return false;
        }

        @Override
        protected int getHeight() {
            return this.size() * 10;
        }

        @Override
        protected void renderBackground() {
            StatsScreen.this.renderBackground();
        }

        @Override
        protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
            Stat stat = Stats.GENERAL.get(index);
            StatsScreen.this.drawString(StatsScreen.this.textRenderer, stat.getDecoratedName().getString(), x + 2, y + 1, index % 2 == 0 ? 16777215 : 9474192);
            String s = stat.format(StatsScreen.this.stats.get(stat));
            StatsScreen.this.drawString(
                StatsScreen.this.textRenderer, s, x + 2 + 213 - StatsScreen.this.textRenderer.getWidth(s), y + 1, index % 2 == 0 ? 16777215 : 9474192
            );
        }
    }

    class ItemStatsListWidget extends StatsScreen.AbstractStatsListWidget {
        public ItemStatsListWidget(Minecraft minecraft) {
            super(minecraft);
            this.entries = Lists.newArrayList();

            for (ItemStat itemstat : Stats.USED) {
                boolean flag = false;
                int i = Item.getId(itemstat.getItem());
                if (StatsScreen.this.stats.get(itemstat) > 0) {
                    flag = true;
                } else if (Stats.ITEMS_BROKEN[i] != null && StatsScreen.this.stats.get(Stats.ITEMS_BROKEN[i]) > 0) {
                    flag = true;
                } else if (Stats.ITEMS_CRAFTED[i] != null && StatsScreen.this.stats.get(Stats.ITEMS_CRAFTED[i]) > 0) {
                    flag = true;
                }

                if (flag) {
                    this.entries.add(itemstat);
                }
            }

            this.statComparator = new Comparator<ItemStat>() {
                public int compare(ItemStat itemStat, ItemStat itemStat2) {
                    int j = Item.getId(itemStat.getItem());
                    int k = Item.getId(itemStat2.getItem());
                    Stat stat = null;
                    Stat stat1 = null;
                    if (ItemStatsListWidget.this.selectedTab == 0) {
                        stat = Stats.ITEMS_BROKEN[j];
                        stat1 = Stats.ITEMS_BROKEN[k];
                    } else if (ItemStatsListWidget.this.selectedTab == 1) {
                        stat = Stats.ITEMS_CRAFTED[j];
                        stat1 = Stats.ITEMS_CRAFTED[k];
                    } else if (ItemStatsListWidget.this.selectedTab == 2) {
                        stat = Stats.ITEMS_USED[j];
                        stat1 = Stats.ITEMS_USED[k];
                    }

                    if (stat != null || stat1 != null) {
                        if (stat == null) {
                            return 1;
                        }

                        if (stat1 == null) {
                            return -1;
                        }

                        int l = StatsScreen.this.stats.get(stat);
                        int i1 = StatsScreen.this.stats.get(stat1);
                        if (l != i1) {
                            return (l - i1) * ItemStatsListWidget.this.statSortOrder;
                        }
                    }

                    return j - k;
                }
            };
        }

        @Override
        protected void renderHeader(int x, int y, Tesselator tesselator) {
            super.renderHeader(x, y, tesselator);
            if (this.clickedIconId == 0) {
                StatsScreen.this.renderSlot(x + 115 - 18 + 1, y + 1 + 1, 72, 18);
            } else {
                StatsScreen.this.renderSlot(x + 115 - 18, y + 1, 72, 18);
            }

            if (this.clickedIconId == 1) {
                StatsScreen.this.renderSlot(x + 165 - 18 + 1, y + 1 + 1, 18, 18);
            } else {
                StatsScreen.this.renderSlot(x + 165 - 18, y + 1, 18, 18);
            }

            if (this.clickedIconId == 2) {
                StatsScreen.this.renderSlot(x + 215 - 18 + 1, y + 1 + 1, 36, 18);
            } else {
                StatsScreen.this.renderSlot(x + 215 - 18, y + 1, 36, 18);
            }
        }

        @Override
        protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
            ItemStat itemstat = this.getEntry(index);
            Item item = itemstat.getItem();
            StatsScreen.this.renderStatIcon(x + 40, y, item);
            int i = Item.getId(item);
            this.renderStat(Stats.ITEMS_BROKEN[i], x + 115, y, index % 2 == 0);
            this.renderStat(Stats.ITEMS_CRAFTED[i], x + 165, y, index % 2 == 0);
            this.renderStat(itemstat, x + 215, y, index % 2 == 0);
        }

        @Override
        protected String getColumnHeader(int column) {
            if (column == 1) {
                return "stat.crafted";
            } else {
                return column == 2 ? "stat.used" : "stat.depleted";
            }
        }
    }

    class StatsListWidget extends StatsScreen.AbstractStatsListWidget {
        public StatsListWidget(Minecraft minecraft) {
            super(minecraft);
            this.entries = Lists.newArrayList();

            for (ItemStat itemstat : Stats.MINED) {
                boolean flag = false;
                int i = Item.getId(itemstat.getItem());
                if (StatsScreen.this.stats.get(itemstat) > 0) {
                    flag = true;
                } else if (Stats.ITEMS_USED[i] != null && StatsScreen.this.stats.get(Stats.ITEMS_USED[i]) > 0) {
                    flag = true;
                } else if (Stats.ITEMS_CRAFTED[i] != null && StatsScreen.this.stats.get(Stats.ITEMS_CRAFTED[i]) > 0) {
                    flag = true;
                }

                if (flag) {
                    this.entries.add(itemstat);
                }
            }

            this.statComparator = new Comparator<ItemStat>() {
                public int compare(ItemStat itemStat, ItemStat itemStat2) {
                    int j = Item.getId(itemStat.getItem());
                    int k = Item.getId(itemStat2.getItem());
                    Stat stat = null;
                    Stat stat1 = null;
                    if (StatsListWidget.this.selectedTab == 2) {
                        stat = Stats.BLOCKS_MINED[j];
                        stat1 = Stats.BLOCKS_MINED[k];
                    } else if (StatsListWidget.this.selectedTab == 0) {
                        stat = Stats.ITEMS_CRAFTED[j];
                        stat1 = Stats.ITEMS_CRAFTED[k];
                    } else if (StatsListWidget.this.selectedTab == 1) {
                        stat = Stats.ITEMS_USED[j];
                        stat1 = Stats.ITEMS_USED[k];
                    }

                    if (stat != null || stat1 != null) {
                        if (stat == null) {
                            return 1;
                        }

                        if (stat1 == null) {
                            return -1;
                        }

                        int l = StatsScreen.this.stats.get(stat);
                        int i1 = StatsScreen.this.stats.get(stat1);
                        if (l != i1) {
                            return (l - i1) * StatsListWidget.this.statSortOrder;
                        }
                    }

                    return j - k;
                }
            };
        }

        @Override
        protected void renderHeader(int x, int y, Tesselator tesselator) {
            super.renderHeader(x, y, tesselator);
            if (this.clickedIconId == 0) {
                StatsScreen.this.renderSlot(x + 115 - 18 + 1, y + 1 + 1, 18, 18);
            } else {
                StatsScreen.this.renderSlot(x + 115 - 18, y + 1, 18, 18);
            }

            if (this.clickedIconId == 1) {
                StatsScreen.this.renderSlot(x + 165 - 18 + 1, y + 1 + 1, 36, 18);
            } else {
                StatsScreen.this.renderSlot(x + 165 - 18, y + 1, 36, 18);
            }

            if (this.clickedIconId == 2) {
                StatsScreen.this.renderSlot(x + 215 - 18 + 1, y + 1 + 1, 54, 18);
            } else {
                StatsScreen.this.renderSlot(x + 215 - 18, y + 1, 54, 18);
            }
        }

        @Override
        protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
            ItemStat itemstat = this.getEntry(index);
            Item item = itemstat.getItem();
            StatsScreen.this.renderStatIcon(x + 40, y, item);
            int i = Item.getId(item);
            this.renderStat(Stats.ITEMS_CRAFTED[i], x + 115, y, index % 2 == 0);
            this.renderStat(Stats.ITEMS_USED[i], x + 165, y, index % 2 == 0);
            this.renderStat(itemstat, x + 215, y, index % 2 == 0);
        }

        @Override
        protected String getColumnHeader(int column) {
            if (column == 0) {
                return "stat.crafted";
            } else {
                return column == 1 ? "stat.used" : "stat.mined";
            }
        }
    }
}
