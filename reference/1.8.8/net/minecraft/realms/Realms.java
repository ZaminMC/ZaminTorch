package net.minecraft.realms;

import com.google.common.util.concurrent.ListenableFuture;
import com.mojang.authlib.GameProfile;
import com.mojang.util.UUIDTypeAdapter;
import java.net.Proxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Session;
import net.minecraft.world.WorldSettings;

public class Realms {
    public static boolean isTouchScreen() {
        return Minecraft.getInstance().options.touchscreen;
    }

    public static Proxy getProxy() {
        return Minecraft.getInstance().getNetworkProxy();
    }

    public static String sessionId() {
        Session session = Minecraft.getInstance().getSession();
        return session == null ? null : session.getSessionId();
    }

    public static String userName() {
        Session session = Minecraft.getInstance().getSession();
        return session == null ? null : session.getUsername();
    }

    public static long currentTimeMillis() {
        return Minecraft.getTime();
    }

    public static String getSessionId() {
        return Minecraft.getInstance().getSession().getSessionId();
    }

    public static String getUUID() {
        return Minecraft.getInstance().getSession().getUuid();
    }

    public static String getName() {
        return Minecraft.getInstance().getSession().getUsername();
    }

    public static String uuidToName(String uuid) {
        return Minecraft.getInstance().getSessionService().fillProfileProperties(new GameProfile(UUIDTypeAdapter.fromString(uuid), null), false).getName();
    }

    public static void setScreen(RealmsScreen screen) {
        Minecraft.getInstance().openScreen(screen.getProxy());
    }

    public static String getGameDirectoryPath() {
        return Minecraft.getInstance().gameDir.getAbsolutePath();
    }

    public static int survivalId() {
        return WorldSettings.GameMode.SURVIVAL.getId();
    }

    public static int creativeId() {
        return WorldSettings.GameMode.CREATIVE.getId();
    }

    public static int adventureId() {
        return WorldSettings.GameMode.ADVENTURE.getId();
    }

    public static int spectatorId() {
        return WorldSettings.GameMode.SPECTATOR.getId();
    }

    public static void setConnectedToRealms(boolean connected) {
        Minecraft.getInstance().setConnectedToRealms(connected);
    }

    public static ListenableFuture<Object> downloadResourcePack(String url, String hash) {
        return Minecraft.getInstance().getResourcePacks().downloadServerPack(url, hash);
    }

    public static void clearResourcePack() {
        Minecraft.getInstance().getResourcePacks().removeServerPack();
    }
}
