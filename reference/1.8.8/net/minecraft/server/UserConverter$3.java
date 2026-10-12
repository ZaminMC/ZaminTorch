package net.minecraft.server;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;

final class UserConverter$3 implements ProfileLookupCallback {
    UserConverter$3(MinecraftServer minecraftServer, Ops ops) {
        this.f_3957924 = minecraftServer;
        this.f_3561987 = ops;
    }

    @Override
    public void onProfileLookupSucceeded(GameProfile profile) {
        this.f_3957924.getGameProfileCache().add(profile);
        this.f_3561987.add(new OpEntry(profile, this.f_3957924.getOpPermissionLevel(), false));
    }

    @Override
    public void onProfileLookupFailed(GameProfile profile, Exception e) {
        UserConverter.m_6886248().warn("Could not lookup oplist entry for " + profile.getName(), e);
        if (!(e instanceof ProfileNotFoundException)) {
            throw new UserConverter$ConversionException("Could not request user " + profile.getName() + " from backend systems", e);
        }
    }
}
