package net.minecraft.server;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.text.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UserConverter {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final File IP_BANS_FILE = new File("banned-ips.txt");
    public static final File PLAYER_BANS_FILE = new File("banned-players.txt");
    public static final File OPS_FILE = new File("ops.txt");
    public static final File WHITELIST_FILE = new File("white-list.txt");

    private static void findProfiles(MinecraftServer server, Collection<String> names, ProfileLookupCallback callback) {
        String[] astring = Iterators.toArray(Iterators.filter(names.iterator(), new Predicate<String>() {
            public boolean apply(String string) {
                return !StringUtils.isStringEmpty(string);
            }
        }), String.class);
        if (server.isOnlineMode()) {
            server.getGameProfileRepository().findProfilesByNames(astring, Agent.MINECRAFT, callback);
        } else {
            for (String s : astring) {
                UUID uuid = PlayerEntity.getUuid(new GameProfile(null, s));
                GameProfile gameprofile = new GameProfile(uuid, s);
                callback.onProfileLookupSucceeded(gameprofile);
            }
        }
    }

    public static String convertMobOwner(String playerName) {
        if (!StringUtils.isStringEmpty(playerName) && playerName.length() <= 16) {
            final MinecraftServer minecraftserver = MinecraftServer.getInstance();
            GameProfile gameprofile = minecraftserver.getGameProfileCache().get(playerName);
            if (gameprofile != null && gameprofile.getId() != null) {
                return gameprofile.getId().toString();
            } else if (!minecraftserver.isSingleplayer() && minecraftserver.isOnlineMode()) {
                final List<GameProfile> list = Lists.newArrayList();
                ProfileLookupCallback profilelookupcallback = new ProfileLookupCallback() {
                    @Override
                    public void onProfileLookupSucceeded(GameProfile profile) {
                        minecraftserver.getGameProfileCache().add(profile);
                        list.add(profile);
                    }

                    @Override
                    public void onProfileLookupFailed(GameProfile profile, Exception e) {
                        UserConverter.LOGGER.warn("Could not lookup user whitelist entry for " + profile.getName(), e);
                    }
                };
                findProfiles(minecraftserver, Lists.newArrayList(playerName), profilelookupcallback);
                return list.size() > 0 && list.get(0).getId() != null ? list.get(0).getId().toString() : "";
            } else {
                return PlayerEntity.getUuid(new GameProfile(null, playerName)).toString();
            }
        } else {
            return playerName;
        }
    }
}
