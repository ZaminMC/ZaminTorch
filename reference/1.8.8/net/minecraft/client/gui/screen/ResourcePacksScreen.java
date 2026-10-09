package net.minecraft.client.gui.screen;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.screen.resourcepack.AppliedResourcePackListWidget;
import net.minecraft.client.gui.screen.resourcepack.AvailableResourcePackListWidget;
import net.minecraft.client.gui.screen.resourcepack.CustomResourcePackEntry;
import net.minecraft.client.gui.screen.resourcepack.DefaultResourcePackEntry;
import net.minecraft.client.gui.screen.resourcepack.ResourcePackEntry;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.resource.pack.ResourcePacks;
import net.minecraft.util.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.Sys;

public class ResourcePacksScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Screen parent;
    private List<ResourcePackEntry> availablePacks;
    private List<ResourcePackEntry> appliedPacks;
    private AvailableResourcePackListWidget availablePacksWidget;
    private AppliedResourcePackListWidget appliedPacksWidget;
    private boolean changed = false;

    public ResourcePacksScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        this.buttons.add(new OptionButtonWidget(2, this.width / 2 - 154, this.height - 48, I18n.translate("resourcePack.openFolder")));
        this.buttons.add(new OptionButtonWidget(1, this.width / 2 + 4, this.height - 48, I18n.translate("gui.done")));
        if (!this.changed) {
            this.availablePacks = Lists.newArrayList();
            this.appliedPacks = Lists.newArrayList();
            ResourcePacks resourcepacks = this.minecraft.getResourcePacks();
            resourcepacks.load();
            List<ResourcePacks.Entry> list = Lists.newArrayList(resourcepacks.getAvailable());
            list.removeAll(resourcepacks.getApplied());

            for (ResourcePacks.Entry resourcepacks$entry : list) {
                this.availablePacks.add(new CustomResourcePackEntry(this, resourcepacks$entry));
            }

            for (ResourcePacks.Entry resourcepacks$entry1 : Lists.reverse(resourcepacks.getApplied())) {
                this.appliedPacks.add(new CustomResourcePackEntry(this, resourcepacks$entry1));
            }

            this.appliedPacks.add(new DefaultResourcePackEntry(this));
        }

        this.availablePacksWidget = new AvailableResourcePackListWidget(this.minecraft, 200, this.height, this.availablePacks);
        this.availablePacksWidget.setX(this.width / 2 - 4 - 200);
        this.availablePacksWidget.setScrollButtonIds(7, 8);
        this.appliedPacksWidget = new AppliedResourcePackListWidget(this.minecraft, 200, this.height, this.appliedPacks);
        this.appliedPacksWidget.setX(this.width / 2 + 4);
        this.appliedPacksWidget.setScrollButtonIds(7, 8);
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        this.appliedPacksWidget.handleMouse();
        this.availablePacksWidget.handleMouse();
    }

    public boolean isApplied(ResourcePackEntry pack) {
        return this.appliedPacks.contains(pack);
    }

    public List<ResourcePackEntry> getSiblingPacks(ResourcePackEntry pack) {
        return this.isApplied(pack) ? this.appliedPacks : this.availablePacks;
    }

    public List<ResourcePackEntry> getAvailablePacks() {
        return this.availablePacks;
    }

    public List<ResourcePackEntry> getAppliedPacks() {
        return this.appliedPacks;
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 2) {
                File file1 = this.minecraft.getResourcePacks().getDirectory();
                String s = file1.getAbsolutePath();
                if (Utils.getOS() == Utils.OS.MACOS) {
                    try {
                        LOGGER.info(s);
                        Runtime.getRuntime().exec(new String[]{"/usr/bin/open", s});
                        return;
                    } catch (IOException ioexception1) {
                        LOGGER.error("Couldn't open file", ioexception1);
                    }
                } else if (Utils.getOS() == Utils.OS.WINDOWS) {
                    String s1 = String.format("cmd.exe /C start \"Open file\" \"%s\"", s);

                    try {
                        Runtime.getRuntime().exec(s1);
                        return;
                    } catch (IOException ioexception) {
                        LOGGER.error("Couldn't open file", ioexception);
                    }
                }

                boolean flag = false;

                try {
                    Class<?> oclass = Class.forName("java.awt.Desktop");
                    Object object = oclass.getMethod("getDesktop").invoke(null);
                    oclass.getMethod("browse", URI.class).invoke(object, file1.toURI());
                } catch (Throwable throwable) {
                    LOGGER.error("Couldn't open link", throwable);
                    flag = true;
                }

                if (flag) {
                    LOGGER.info("Opening via system class!");
                    Sys.openURL("file://" + s);
                }
            } else if (button.id == 1) {
                if (this.changed) {
                    List<ResourcePacks.Entry> list = Lists.newArrayList();

                    for (ResourcePackEntry resourcepackentry : this.appliedPacks) {
                        if (resourcepackentry instanceof CustomResourcePackEntry) {
                            list.add(((CustomResourcePackEntry)resourcepackentry).getPack());
                        }
                    }

                    Collections.reverse(list);
                    this.minecraft.getResourcePacks().apply(list);
                    this.minecraft.options.resourcePacks.clear();
                    this.minecraft.options.incompatibleResourcePacks.clear();

                    for (ResourcePacks.Entry resourcepacks$entry : list) {
                        this.minecraft.options.resourcePacks.add(resourcepacks$entry.getName());
                        if (resourcepacks$entry.getFormat() != 1) {
                            this.minecraft.options.incompatibleResourcePacks.add(resourcepacks$entry.getName());
                        }
                    }

                    this.minecraft.options.save();
                    this.minecraft.reloadResources();
                }

                this.minecraft.openScreen(this.parent);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        this.availablePacksWidget.mouseClicked(mouseX, mouseY, button);
        this.appliedPacksWidget.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.drawBackgroundTexture(0);
        this.availablePacksWidget.render(mouseX, mouseY, tickDelta);
        this.appliedPacksWidget.render(mouseX, mouseY, tickDelta);
        this.drawCenteredString(this.textRenderer, I18n.translate("resourcePack.title"), this.width / 2, 16, 16777215);
        this.drawCenteredString(this.textRenderer, I18n.translate("resourcePack.folderInfo"), this.width / 2 - 77, this.height - 26, 8421504);
        super.render(mouseX, mouseY, tickDelta);
    }

    public void setChanged() {
        this.changed = true;
    }
}
