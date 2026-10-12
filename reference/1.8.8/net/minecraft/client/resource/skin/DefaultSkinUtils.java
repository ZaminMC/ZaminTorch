package net.minecraft.client.resource.skin;

import java.util.UUID;
import net.minecraft.resource.Identifier;

public class DefaultSkinUtils {
    private static final Identifier STEVE_SKIN = new Identifier("textures/entity/steve.png");
    private static final Identifier ALEX_SKIN = new Identifier("textures/entity/alex.png");

    public static Identifier getDefaultSkin() {
        return STEVE_SKIN;
    }

    public static Identifier getDefaultSkin(UUID uuid) {
        return isAlexDefault(uuid) ? ALEX_SKIN : STEVE_SKIN;
    }

    public static String getDefaultModelType(UUID uuid) {
        return isAlexDefault(uuid) ? "slim" : "default";
    }

    private static boolean isAlexDefault(UUID uuid) {
        return (uuid.hashCode() & 1) == 1;
    }
}
