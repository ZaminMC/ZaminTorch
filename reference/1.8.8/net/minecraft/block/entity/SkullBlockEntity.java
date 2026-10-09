package net.minecraft.block.entity;

import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.StringUtils;

public class SkullBlockEntity extends BlockEntity {
    private int skullType;
    private int rotation;
    private GameProfile profile = null;

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putByte("SkullType", (byte)(this.skullType & 0xFF));
        nbt.putByte("Rot", (byte)(this.rotation & 0xFF));
        if (this.profile != null) {
            NbtCompound nbtcompound = new NbtCompound();
            NbtUtils.writeProfile(nbtcompound, this.profile);
            nbt.put("Owner", nbtcompound);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.skullType = nbt.getByte("SkullType");
        this.rotation = nbt.getByte("Rot");
        if (this.skullType == 3) {
            if (nbt.contains("Owner", 10)) {
                this.profile = NbtUtils.readProfile(nbt.getCompound("Owner"));
            } else if (nbt.contains("ExtraType", 8)) {
                String s = nbt.getString("ExtraType");
                if (!StringUtils.isStringEmpty(s)) {
                    this.profile = new GameProfile(null, s);
                    this.updateProfile();
                }
            }
        }
    }

    public GameProfile getProfile() {
        return this.profile;
    }

    @Override
    public Packet createUpdatePacket() {
        NbtCompound nbtcompound = new NbtCompound();
        this.writeNbt(nbtcompound);
        return new BlockEntityUpdateS2CPacket(this.pos, 4, nbtcompound);
    }

    public void setSkullType(int type) {
        this.skullType = type;
        this.profile = null;
    }

    public void setProfile(GameProfile profile) {
        this.skullType = 3;
        this.profile = profile;
        this.updateProfile();
    }

    private void updateProfile() {
        this.profile = updateProfile(this.profile);
        this.markDirty();
    }

    public static GameProfile updateProfile(GameProfile profile) {
        if (profile != null && !StringUtils.isStringEmpty(profile.getName())) {
            if (profile.isComplete() && profile.getProperties().containsKey("textures")) {
                return profile;
            }

            if (MinecraftServer.getInstance() == null) {
                return profile;
            }

            GameProfile gameprofile = MinecraftServer.getInstance().getGameProfileCache().get(profile.getName());
            if (gameprofile == null) {
                return profile;
            }

            Property property = Iterables.getFirst(gameprofile.getProperties().get("textures"), null);
            if (property == null) {
                gameprofile = MinecraftServer.getInstance().getSessionService().fillProfileProperties(gameprofile, true);
            }

            return gameprofile;
        } else {
            return profile;
        }
    }

    public int getType() {
        return this.skullType;
    }

    public int getRotation() {
        return this.rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }
}
