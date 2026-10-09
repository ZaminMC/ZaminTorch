package net.minecraft.client.network;

import com.google.common.base.Objects;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.skin.DefaultSkinUtils;
import net.minecraft.client.resource.skin.SkinManager;
import net.minecraft.network.packet.s2c.play.PlayerInfoS2CPacket;
import net.minecraft.resource.Identifier;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.Text;
import net.minecraft.world.WorldSettings;

public class PlayerInfo {
    private final GameProfile profile;
    private WorldSettings.GameMode gameMode;
    private int ping;
    private boolean waitingForTextures = false;
    private Identifier skinTexture;
    private Identifier capeTexture;
    private String modelType;
    private Text displayName;
    private int lastHealth = 0;
    private int health = 0;
    private long lastHealthUpdateTime = 0L;
    private long lastHealthAnimationTime = 0L;
    private long renderVisibility = 0L;

    public PlayerInfo(GameProfile profile) {
        this.profile = profile;
    }

    public PlayerInfo(PlayerInfoS2CPacket.Entry entry) {
        this.profile = entry.getProfile();
        this.gameMode = entry.getGameMode();
        this.ping = entry.getPing();
        this.displayName = entry.getDisplayName();
    }

    public GameProfile getProfile() {
        return this.profile;
    }

    public WorldSettings.GameMode getGameMode() {
        return this.gameMode;
    }

    public int getPing() {
        return this.ping;
    }

    protected void setGameMode(WorldSettings.GameMode gameMode) {
        this.gameMode = gameMode;
    }

    protected void setPing(int ping) {
        this.ping = ping;
    }

    public boolean hasSkinTexture() {
        return this.skinTexture != null;
    }

    public String getModelType() {
        return this.modelType == null ? DefaultSkinUtils.getDefaultModelType(this.profile.getId()) : this.modelType;
    }

    public Identifier getSkinTexture() {
        if (this.skinTexture == null) {
            this.registerTextures();
        }

        return Objects.firstNonNull(this.skinTexture, DefaultSkinUtils.getDefaultSkin(this.profile.getId()));
    }

    public Identifier getCapeTexture() {
        if (this.capeTexture == null) {
            this.registerTextures();
        }

        return this.capeTexture;
    }

    public Team getTeam() {
        return Minecraft.getInstance().world.getScoreboard().getTeamOfMember(this.getProfile().getName());
    }

    protected void registerTextures() {
        synchronized (this) {
            if (!this.waitingForTextures) {
                this.waitingForTextures = true;
                Minecraft.getInstance().getSkinManager().register(this.profile, new SkinManager.SkinTextureCallback() {
                    @Override
                    public void textureAvailable(Type type, Identifier location, MinecraftProfileTexture texture) {
                        switch (type) {
                            case SKIN:
                                PlayerInfo.this.skinTexture = location;
                                PlayerInfo.this.modelType = texture.getMetadata("model");
                                if (PlayerInfo.this.modelType == null) {
                                    PlayerInfo.this.modelType = "default";
                                }
                                break;
                            case CAPE:
                                PlayerInfo.this.capeTexture = location;
                        }
                    }
                }, true);
            }
        }
    }

    public void setDisplayName(Text displayName) {
        this.displayName = displayName;
    }

    public Text getDisplayName() {
        return this.displayName;
    }

    public int getLastHealth() {
        return this.lastHealth;
    }

    public void setLastHealth(int health) {
        this.lastHealth = health;
    }

    public int getHealth() {
        return this.health;
    }

    public void setHealth(int health) {
        this.health = health;
    }

    public long getLastHealthUpdateTime() {
        return this.lastHealthUpdateTime;
    }

    public void setLastHealthUpdateTime(long time) {
        this.lastHealthUpdateTime = time;
    }

    public long getLastHealthAnimationTime() {
        return this.lastHealthAnimationTime;
    }

    public void setLastHealthAnimationTime(long time) {
        this.lastHealthAnimationTime = time;
    }

    public long getRenderVisibility() {
        return this.renderVisibility;
    }

    public void setRenderVisibility(long visibility) {
        this.renderVisibility = visibility;
    }
}
