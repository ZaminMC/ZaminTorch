package net.minecraft.server;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;

final class UserConverter$4 implements ProfileLookupCallback {
    UserConverter$4(MinecraftServer minecraftServer, Whitelist whitelist) {
        this.f_4946064 = minecraftServer;
        this.f_0441390 = whitelist;
    }

    @Override
    public void onProfileLookupSucceeded(GameProfile profile) {
        this.f_4946064.getGameProfileCache().add(profile);
        this.f_0441390.add(new WhitelistEntry(profile));
    }

    @Override
    public void onProfileLookupFailed(GameProfile profile, Exception e) {
        UserConverter.m_6886248().warn("Could not lookup user whitelist entry for " + profile.getName(), e);
        if (!(e instanceof ProfileNotFoundException)) {
            throw new UserConverter$ConversionException("Could not request user " + profile.getName() + " from backend systems", e);
        }
    }
}
