package net.minecraft.client.gui.screen.resourcepack;

import com.google.gson.JsonParseException;
import java.io.IOException;
import net.minecraft.client.gui.screen.ResourcePacksScreen;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.client.resource.metadata.ResourcePackMetadata;
import net.minecraft.client.resource.pack.ResourcePack;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Formatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultResourcePackEntry extends ResourcePackEntry {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ResourcePack pack = this.minecraft.getResourcePacks().defaultPack;
    private final Identifier iconLocation;

    public DefaultResourcePackEntry(ResourcePacksScreen resourcePacksScreen) {
        super(resourcePacksScreen);

        DynamicTexture dynamictexture;
        try {
            dynamictexture = new DynamicTexture(this.pack.getIcon());
        } catch (IOException ioexception) {
            dynamictexture = TextureUtil.MISSING_TEXTURE;
        }

        this.iconLocation = this.minecraft.getTextureManager().register("texturepackicon", dynamictexture);
    }

    @Override
    protected int getFormat() {
        return 1;
    }

    @Override
    protected String getDescription() {
        try {
            ResourcePackMetadata resourcepackmetadata = this.pack.getMetadataSection(this.minecraft.getResourcePacks().metadataSerializers, "pack");
            if (resourcepackmetadata != null) {
                return resourcepackmetadata.getDescription().getFormattedString();
            }
        } catch (JsonParseException jsonparseexception) {
            LOGGER.error("Couldn't load metadata info", jsonparseexception);
        } catch (IOException ioexception) {
            LOGGER.error("Couldn't load metadata info", ioexception);
        }

        return Formatting.RED + "Missing " + "pack.mcmeta" + " :(";
    }

    @Override
    protected boolean canMoveRight() {
        return false;
    }

    @Override
    protected boolean canMoveLeft() {
        return false;
    }

    @Override
    protected boolean canMoveUp() {
        return false;
    }

    @Override
    protected boolean canMoveDown() {
        return false;
    }

    @Override
    protected String getName() {
        return "Default";
    }

    @Override
    protected void bindIcon() {
        this.minecraft.getTextureManager().bind(this.iconLocation);
    }

    @Override
    protected boolean canMove() {
        return false;
    }
}
