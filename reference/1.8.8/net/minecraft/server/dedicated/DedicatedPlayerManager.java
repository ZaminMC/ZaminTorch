package net.minecraft.server.dedicated;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import net.minecraft.server.PlayerManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DedicatedPlayerManager extends PlayerManager {
    private static final Logger LOGGER = LogManager.getLogger();

    public DedicatedPlayerManager(DedicatedServer server) {
        super(server);
        this.updateViewDistance(server.getIntProperty("view-distance", 10));
        this.maxPlayerCount = server.getIntProperty("max-players", 20);
        this.setEnforceWhitelist(server.getBooleanProperty("white-list", false));
        if (!server.isSingleplayer()) {
            this.getBans().setEnabled(true);
            this.getIpBans().setEnabled(true);
        }

        this.loadPlayerBans();
        this.savePlayerBans();
        this.loadIpBans();
        this.saveIpBans();
        this.loadOps();
        this.loadWhitelist();
        this.saveOps();
        if (!this.getWhitelist().getFile().exists()) {
            this.saveWhitelist();
        }
    }

    @Override
    public void setEnforceWhitelist(boolean enforce) {
        super.setEnforceWhitelist(enforce);
        this.getServer().setProperty("white-list", enforce);
        this.getServer().saveProperties();
    }

    @Override
    public void addOp(GameProfile profile) {
        super.addOp(profile);
        this.saveOps();
    }

    @Override
    public void removeOp(GameProfile profile) {
        super.removeOp(profile);
        this.saveOps();
    }

    @Override
    public void removeFromWhitelist(GameProfile profile) {
        super.removeFromWhitelist(profile);
        this.saveWhitelist();
    }

    @Override
    public void addToWhitelist(GameProfile profile) {
        super.addToWhitelist(profile);
        this.saveWhitelist();
    }

    @Override
    public void reloadWhitelist() {
        this.loadWhitelist();
    }

    private void saveIpBans() {
        try {
            this.getIpBans().save();
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to save ip banlist: ", ioexception);
        }
    }

    private void savePlayerBans() {
        try {
            this.getBans().save();
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to save user banlist: ", ioexception);
        }
    }

    private void loadIpBans() {
        try {
            this.getIpBans().load();
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to load ip banlist: ", ioexception);
        }
    }

    private void loadPlayerBans() {
        try {
            this.getBans().load();
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to load user banlist: ", ioexception);
        }
    }

    private void loadOps() {
        try {
            this.getOps().load();
        } catch (Exception exception) {
            LOGGER.warn("Failed to load operators list: ", exception);
        }
    }

    private void saveOps() {
        try {
            this.getOps().save();
        } catch (Exception exception) {
            LOGGER.warn("Failed to save operators list: ", exception);
        }
    }

    private void loadWhitelist() {
        try {
            this.getWhitelist().load();
        } catch (Exception exception) {
            LOGGER.warn("Failed to load white-list: ", exception);
        }
    }

    private void saveWhitelist() {
        try {
            this.getWhitelist().save();
        } catch (Exception exception) {
            LOGGER.warn("Failed to save white-list: ", exception);
        }
    }

    @Override
    public boolean isWhitelisted(GameProfile profile) {
        return !this.isWhitelistEnforced() || this.isOp(profile) || this.getWhitelist().isWhitelisted(profile);
    }

    public DedicatedServer getServer() {
        return (DedicatedServer)super.getServer();
    }

    @Override
    public boolean canBypassPlayerLimit(GameProfile profile) {
        return this.getOps().bypassesPlayerLimit(profile);
    }
}
