package net.minecraft.client.gui.screen.resourcepack;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.language.I18n;

public class AvailableResourcePackListWidget extends ResourcePackListWidget {
    public AvailableResourcePackListWidget(Minecraft minecraft, int i, int j, List<ResourcePackEntry> list) {
        super(minecraft, i, j, list);
    }

    @Override
    protected String getTitle() {
        return I18n.translate("resourcePack.available.title");
    }
}
