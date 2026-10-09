package net.minecraft.realms;

import java.lang.reflect.Constructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RealmsBridge extends RealmsScreen {
    private static final Logger LOGGER = LogManager.getLogger();
    private Screen previousScreen;

    public void switchToRealms(Screen parent) {
        this.previousScreen = parent;

        try {
            Class<?> oclass = Class.forName("com.mojang.realmsclient.RealmsMainScreen");
            Constructor<?> constructor = oclass.getDeclaredConstructor(RealmsScreen.class);
            constructor.setAccessible(true);
            Object object = constructor.newInstance(this);
            Minecraft.getInstance().openScreen(((RealmsScreen)object).getProxy());
        } catch (Exception exception) {
            LOGGER.error("Realms module missing", exception);
        }
    }

    @Override
    public void init() {
        Minecraft.getInstance().openScreen(this.previousScreen);
    }
}
