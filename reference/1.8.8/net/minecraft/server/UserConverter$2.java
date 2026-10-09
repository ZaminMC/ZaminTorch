package net.minecraft.server;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

final class UserConverter$2 implements ProfileLookupCallback {
    UserConverter$2(MinecraftServer minecraftServer, Map map, PlayerBans playerBans) {
        this.f_8454284 = minecraftServer;
        this.f_9749436 = map;
        this.f_8661777 = playerBans;
    }

    @Override
    public void onProfileLookupSucceeded(GameProfile profile) {
        this.f_8454284.getGameProfileCache().add(profile);
        String[] astring = (String[])this.f_9749436.get(profile.getName().toLowerCase(Locale.ROOT));
        if (astring == null) {
            UserConverter.m_6886248().warn("Could not convert user banlist entry for " + profile.getName());
            throw new UserConverter$ConversionException("Profile not in the conversionlist");
        }

        Date date = astring.length > 1 ? UserConverter.m_3126737(astring[1], null) : null;
        String s = astring.length > 2 ? astring[2] : null;
        Date date1 = astring.length > 3 ? UserConverter.m_3126737(astring[3], null) : null;
        String s1 = astring.length > 4 ? astring[4] : null;
        this.f_8661777.add(new PlayerBanEntry(profile, date, s, date1, s1));
    }

    @Override
    public void onProfileLookupFailed(GameProfile profile, Exception e) {
        UserConverter.m_6886248().warn("Could not lookup user banlist entry for " + profile.getName(), e);
        if (!(e instanceof ProfileNotFoundException)) {
            throw new UserConverter$ConversionException("Could not request user " + profile.getName() + " from backend systems", e);
        }
    }
}
