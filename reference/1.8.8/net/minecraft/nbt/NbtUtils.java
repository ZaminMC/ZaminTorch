package net.minecraft.nbt;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.UUID;
import net.minecraft.text.StringUtils;

public final class NbtUtils {
    public static GameProfile readProfile(NbtCompound nbt) {
        String s = null;
        String s1 = null;
        if (nbt.contains("Name", 8)) {
            s = nbt.getString("Name");
        }

        if (nbt.contains("Id", 8)) {
            s1 = nbt.getString("Id");
        }

        if (StringUtils.isStringEmpty(s) && StringUtils.isStringEmpty(s1)) {
            return null;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(s1);
        } catch (Throwable throwable) {
            uuid = null;
        }

        GameProfile gameprofile = new GameProfile(uuid, s);
        if (nbt.contains("Properties", 10)) {
            NbtCompound nbtcompound = nbt.getCompound("Properties");

            for (String s2 : nbtcompound.getKeys()) {
                NbtList nbtlist = nbtcompound.getList(s2, 10);

                for (int i = 0; i < nbtlist.size(); i++) {
                    NbtCompound nbtcompound1 = nbtlist.getCompound(i);
                    String s3 = nbtcompound1.getString("Value");
                    if (nbtcompound1.contains("Signature", 8)) {
                        gameprofile.getProperties().put(s2, new Property(s2, s3, nbtcompound1.getString("Signature")));
                    } else {
                        gameprofile.getProperties().put(s2, new Property(s2, s3));
                    }
                }
            }
        }

        return gameprofile;
    }

    public static NbtCompound writeProfile(NbtCompound nbt, GameProfile profile) {
        if (!StringUtils.isStringEmpty(profile.getName())) {
            nbt.putString("Name", profile.getName());
        }

        if (profile.getId() != null) {
            nbt.putString("Id", profile.getId().toString());
        }

        if (!profile.getProperties().isEmpty()) {
            NbtCompound nbtcompound = new NbtCompound();

            for (String s : profile.getProperties().keySet()) {
                NbtList nbtlist = new NbtList();

                for (Property property : profile.getProperties().get(s)) {
                    NbtCompound nbtcompound1 = new NbtCompound();
                    nbtcompound1.putString("Value", property.getValue());
                    if (property.hasSignature()) {
                        nbtcompound1.putString("Signature", property.getSignature());
                    }

                    nbtlist.addElement(nbtcompound1);
                }

                nbtcompound.put(s, nbtlist);
            }

            nbt.put("Properties", nbtcompound);
        }

        return nbt;
    }

    public static boolean matches(NbtElement element1, NbtElement element2, boolean recurseLists) {
        if (element1 == element2) {
            return true;
        }

        if (element1 == null) {
            return true;
        }

        if (element2 == null) {
            return false;
        }

        if (!element1.getClass().equals(element2.getClass())) {
            return false;
        }

        if (element1 instanceof NbtCompound) {
            NbtCompound nbtcompound = (NbtCompound)element1;
            NbtCompound nbtcompound1 = (NbtCompound)element2;

            for (String s : nbtcompound.getKeys()) {
                NbtElement nbtelement1 = nbtcompound.get(s);
                if (!matches(nbtelement1, nbtcompound1.get(s), recurseLists)) {
                    return false;
                }
            }

            return true;
        } else if (element1 instanceof NbtList && recurseLists) {
            NbtList nbtlist = (NbtList)element1;
            NbtList nbtlist1 = (NbtList)element2;
            if (nbtlist.size() == 0) {
                return nbtlist1.size() == 0;
            }

            for (int i = 0; i < nbtlist.size(); i++) {
                NbtElement nbtelement = nbtlist.getElement(i);
                boolean flag = false;

                for (int j = 0; j < nbtlist1.size(); j++) {
                    if (matches(nbtelement, nbtlist1.getElement(j), recurseLists)) {
                        flag = true;
                        break;
                    }
                }

                if (!flag) {
                    return false;
                }
            }

            return true;
        } else {
            return element1.equals(element2);
        }
    }
}
