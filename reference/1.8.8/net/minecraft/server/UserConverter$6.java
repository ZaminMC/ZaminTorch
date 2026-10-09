package net.minecraft.server;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.yggdrasil.ProfileNotFoundException;
import java.io.File;
import java.util.UUID;
import net.minecraft.server.dedicated.DedicatedServer;

final class UserConverter$6 implements ProfileLookupCallback {
    UserConverter$6(DedicatedServer dedicatedServer, File file, File file2, File file3, String[] strings) {
        this.f_3030994 = dedicatedServer;
        this.f_9418380 = file;
        this.f_0020287 = file2;
        this.f_1894337 = file3;
        this.f_2647049 = strings;
    }

    @Override
    public void onProfileLookupSucceeded(GameProfile profile) {
        this.f_3030994.getGameProfileCache().add(profile);
        UUID uuid = profile.getId();
        if (uuid == null) {
            throw new UserConverter$ConversionException("Missing UUID for user profile " + profile.getName());
        }

        this.convertPlayerFile(this.f_9418380, this.getPlayerFileName(profile), uuid.toString());
    }

    @Override
    public void onProfileLookupFailed(GameProfile profile, Exception e) {
        UserConverter.m_6886248().warn("Could not lookup user uuid for " + profile.getName(), e);
        if (e instanceof ProfileNotFoundException) {
            String s = this.getPlayerFileName(profile);
            this.convertPlayerFile(this.f_0020287, s, s);
        } else {
            throw new UserConverter$ConversionException("Could not request user " + profile.getName() + " from backend systems", e);
        }
    }

    private void convertPlayerFile(File dir, String fileName, String uuid) {
        File file1 = new File(this.f_1894337, fileName + ".dat");
        File file2 = new File(dir, uuid + ".dat");
        UserConverter.m_1707778(dir);
        if (!file1.renameTo(file2)) {
            throw new UserConverter$ConversionException("Could not convert file for " + fileName);
        }
    }

    private String getPlayerFileName(GameProfile profile) {
        String s = null;

        for (int i = 0; i < this.f_2647049.length; i++) {
            if (this.f_2647049[i] != null && this.f_2647049[i].equalsIgnoreCase(profile.getName())) {
                s = this.f_2647049[i];
                break;
            }
        }

        if (s == null) {
            throw new UserConverter$ConversionException("Could not find the filename for " + profile.getName() + " anymore");
        } else {
            return s;
        }
    }
}
