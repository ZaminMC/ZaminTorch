package net.minecraft.client.entity.living.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.render.texture.HttpTexture;
import net.minecraft.client.render.texture.SkinImageProcessor;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.resource.skin.DefaultSkinUtils;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.resource.Identifier;
import net.minecraft.text.StringUtils;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;

public abstract class ClientPlayerEntity extends PlayerEntity {
    private PlayerInfo player;

    public ClientPlayerEntity(World world, GameProfile gameProfile) {
        super(world, gameProfile);
    }

    @Override
    public boolean isSpectator() {
        PlayerInfo playerinfo = Minecraft.getInstance().getNetworkHandler().getOnlinePlayer(this.getGameProfile().getId());
        return playerinfo != null && playerinfo.getGameMode() == WorldSettings.GameMode.SPECTATOR;
    }

    public boolean hasInfo() {
        return this.getInfo() != null;
    }

    protected PlayerInfo getInfo() {
        if (this.player == null) {
            this.player = Minecraft.getInstance().getNetworkHandler().getOnlinePlayer(this.getUuid());
        }

        return this.player;
    }

    public boolean hasSkinTexture() {
        PlayerInfo playerinfo = this.getInfo();
        return playerinfo != null && playerinfo.hasSkinTexture();
    }

    public Identifier getSkinTextureLocation() {
        PlayerInfo playerinfo = this.getInfo();
        return playerinfo == null ? DefaultSkinUtils.getDefaultSkin(this.getUuid()) : playerinfo.getSkinTexture();
    }

    public Identifier getCapeTextureLocation() {
        PlayerInfo playerinfo = this.getInfo();
        return playerinfo == null ? null : playerinfo.getCapeTexture();
    }

    public static HttpTexture loadSkinTexture(Identifier location, String playerName) {
        TextureManager texturemanager = Minecraft.getInstance().getTextureManager();
        Texture texture = texturemanager.get(location);
        if (texture == null) {
            texture = new HttpTexture(
                null,
                String.format("http://skins.minecraft.net/MinecraftSkins/%s.png", StringUtils.stripFormatting(playerName)),
                DefaultSkinUtils.getDefaultSkin(getUuidForOffline(playerName)),
                new SkinImageProcessor()
            );
            texturemanager.register(location, texture);
        }

        return (HttpTexture)texture;
    }

    public static Identifier getSkinTextureLocation(String playerName) {
        return new Identifier("skins/" + StringUtils.stripFormatting(playerName));
    }

    public String getModelType() {
        PlayerInfo playerinfo = this.getInfo();
        return playerinfo == null ? DefaultSkinUtils.getDefaultModelType(this.getUuid()) : playerinfo.getModelType();
    }

    public float getFovModifier() {
        float f = 1.0F;
        if (this.abilities.flying) {
            f *= 1.1F;
        }

        EntityAttributeInstance entityattributeinstance = this.getAttribute(EntityAttributes.MOVEMENT_SPEED);
        f = (float)(f * ((entityattributeinstance.get() / this.abilities.getWalkSpeed() + 1.0) / 2.0));
        if (this.abilities.getWalkSpeed() == 0.0F || Float.isNaN(f) || Float.isInfinite(f)) {
            f = 1.0F;
        }

        if (this.hasItemInUse() && this.getItemInUse().getItem() == Items.BOW) {
            int i = this.getRemainingItemUseDuration();
            float f1 = i / 20.0F;
            if (f1 > 1.0F) {
                f1 = 1.0F;
            } else {
                f1 *= f1;
            }

            f *= 1.0F - f1 * 0.15F;
        }

        return f;
    }
}
