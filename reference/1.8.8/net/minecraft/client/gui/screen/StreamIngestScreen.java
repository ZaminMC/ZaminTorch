package net.minecraft.client.gui.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ListWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.twitch.IngestTester;
import net.minecraft.text.Formatting;
import tv.twitch.broadcast.IngestServer;

public class StreamIngestScreen extends Screen {
    private final Screen parent;
    private String title;
    private StreamIngestScreen.IngestList ingest;

    public StreamIngestScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        this.title = I18n.translate("options.stream.ingest.title");
        this.ingest = new StreamIngestScreen.IngestList(this.minecraft);
        if (!this.minecraft.getTwitchStream().isIngestTesting()) {
            this.minecraft.getTwitchStream().startIngestTesting();
        }

        this.buttons.add(new ButtonWidget(1, this.width / 2 - 155, this.height - 24 - 6, 150, 20, I18n.translate("gui.done")));
        this.buttons.add(new ButtonWidget(2, this.width / 2 + 5, this.height - 24 - 6, 150, 20, I18n.translate("options.stream.ingest.reset")));
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        this.ingest.handleMouse();
    }

    @Override
    public void removed() {
        if (this.minecraft.getTwitchStream().isIngestTesting()) {
            this.minecraft.getTwitchStream().getIngestTester().m_3952819();
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 1) {
                this.minecraft.openScreen(this.parent);
            } else {
                this.minecraft.options.streamPreferredServer = "";
                this.minecraft.options.save();
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.ingest.render(mouseX, mouseY, tickDelta);
        this.drawCenteredString(this.textRenderer, this.title, this.width / 2, 20, 16777215);
        super.render(mouseX, mouseY, tickDelta);
    }

    class IngestList extends ListWidget {
        public IngestList(Minecraft minecraft) {
            super(
                minecraft,
                StreamIngestScreen.this.width,
                StreamIngestScreen.this.height,
                32,
                StreamIngestScreen.this.height - 35,
                (int)(minecraft.textRenderer.fontHeight * 3.5)
            );
            this.setRenderSelectionHighlight(false);
        }

        @Override
        protected int size() {
            return this.minecraft.getTwitchStream().getServers().length;
        }

        @Override
        protected void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            this.minecraft.options.streamPreferredServer = this.minecraft.getTwitchStream().getServers()[index].serverUrl;
            this.minecraft.options.save();
        }

        @Override
        protected boolean isEntrySelected(int index) {
            return this.minecraft.getTwitchStream().getServers()[index].serverUrl.equals(this.minecraft.options.streamPreferredServer);
        }

        @Override
        protected void renderBackground() {
        }

        @Override
        protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
            IngestServer ingestserver = this.minecraft.getTwitchStream().getServers()[index];
            String s = ingestserver.serverUrl.replaceAll("\\{stream_key\\}", "");
            String s1 = (int)ingestserver.bitrateKbps + " kbps";
            String s2 = null;
            IngestTester ingesttester = this.minecraft.getTwitchStream().getIngestTester();
            if (ingesttester != null) {
                if (ingestserver == ingesttester.m_4296079()) {
                    s = Formatting.GREEN + s;
                    s1 = (int)(ingesttester.m_7651052() * 100.0F) + "%";
                } else if (index < ingesttester.m_5896257()) {
                    if (ingestserver.bitrateKbps == 0.0F) {
                        s1 = Formatting.RED + "Down!";
                    }
                } else {
                    s1 = Formatting.OBFUSCATED + "1234" + Formatting.RESET + " kbps";
                }
            } else if (ingestserver.bitrateKbps == 0.0F) {
                s1 = Formatting.RED + "Down!";
            }

            x -= 15;
            if (this.isEntrySelected(index)) {
                s2 = Formatting.BLUE + "(Preferred)";
            } else if (ingestserver.defaultServer) {
                s2 = Formatting.GREEN + "(Default)";
            }

            StreamIngestScreen.this.drawString(StreamIngestScreen.this.textRenderer, ingestserver.serverName, x + 2, y + 5, 16777215);
            StreamIngestScreen.this.drawString(
                StreamIngestScreen.this.textRenderer, s, x + 2, y + StreamIngestScreen.this.textRenderer.fontHeight + 5 + 3, 3158064
            );
            StreamIngestScreen.this.drawString(
                StreamIngestScreen.this.textRenderer, s1, this.getScrollbarPosition() - 5 - StreamIngestScreen.this.textRenderer.getWidth(s1), y + 5, 8421504
            );
            if (s2 != null) {
                StreamIngestScreen.this.drawString(
                    StreamIngestScreen.this.textRenderer,
                    s2,
                    this.getScrollbarPosition() - 5 - StreamIngestScreen.this.textRenderer.getWidth(s2),
                    y + 5 + 3 + StreamIngestScreen.this.textRenderer.fontHeight,
                    8421504
                );
            }
        }

        @Override
        protected int getScrollbarPosition() {
            return super.getScrollbarPosition() + 15;
        }
    }
}
