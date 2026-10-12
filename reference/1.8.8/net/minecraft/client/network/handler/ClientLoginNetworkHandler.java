package net.minecraft.client.network.handler;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.math.BigInteger;
import java.security.PublicKey;
import javax.crypto.SecretKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.NetworkProtocol;
import net.minecraft.network.encryption.EncryptionUtils;
import net.minecraft.network.packet.c2s.login.KeyC2SPacket;
import net.minecraft.network.packet.s2c.login.CompressionThresholdS2CPacket;
import net.minecraft.network.packet.s2c.login.HelloS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginFailS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClientLoginNetworkHandler implements ClientLoginPacketHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Minecraft minecraft;
    private final Screen screen;
    private final Connection connection;
    private GameProfile profile;

    public ClientLoginNetworkHandler(Connection connection, Minecraft minecraft, Screen screen) {
        this.connection = connection;
        this.minecraft = minecraft;
        this.screen = screen;
    }

    @Override
    public void handleHello(HelloS2CPacket packet) {
        final SecretKey secretkey = EncryptionUtils.generateSecretKey();
        String s = packet.getServerKey();
        PublicKey publickey = packet.getPublicKey();
        String s1 = new BigInteger(EncryptionUtils.digest(s, publickey, secretkey)).toString(16);
        if (this.minecraft.getCurrentServerEntry() != null && this.minecraft.getCurrentServerEntry().isLocal()) {
            try {
                this.getSessionService().joinServer(this.minecraft.getSession().getProfile(), this.minecraft.getSession().getAccessToken(), s1);
            } catch (AuthenticationException authenticationexception1) {
                LOGGER.warn("Couldn't connect to auth servers but will continue to join LAN");
            }
        } else {
            try {
                this.getSessionService().joinServer(this.minecraft.getSession().getProfile(), this.minecraft.getSession().getAccessToken(), s1);
            } catch (AuthenticationUnavailableException authenticationunavailableexception) {
                this.connection
                    .disconnect(new TranslatableText("disconnect.loginFailedInfo", new TranslatableText("disconnect.loginFailedInfo.serversUnavailable")));
                return;
            } catch (InvalidCredentialsException invalidcredentialsexception) {
                this.connection
                    .disconnect(new TranslatableText("disconnect.loginFailedInfo", new TranslatableText("disconnect.loginFailedInfo.invalidSession")));
                return;
            } catch (AuthenticationException authenticationexception) {
                this.connection.disconnect(new TranslatableText("disconnect.loginFailedInfo", authenticationexception.getMessage()));
                return;
            }
        }

        this.connection.send(new KeyC2SPacket(secretkey, publickey, packet.getNonce()), new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                ClientLoginNetworkHandler.this.connection.setupEncryption(secretkey);
            }
        });
    }

    private MinecraftSessionService getSessionService() {
        return this.minecraft.getSessionService();
    }

    @Override
    public void handleLoginSuccess(LoginSuccessS2CPacket packet) {
        this.profile = packet.getProfile();
        this.connection.setProtocol(NetworkProtocol.PLAY);
        this.connection.setListener(new ClientPlayNetworkHandler(this.minecraft, this.screen, this.connection, this.profile));
    }

    @Override
    public void onDisconnect(Text reason) {
        this.minecraft.openScreen(new DisconnectedScreen(this.screen, "connect.failed", reason));
    }

    @Override
    public void handleLoginFail(LoginFailS2CPacket packet) {
        this.connection.disconnect(packet.getReason());
    }

    @Override
    public void handleCompressionThreshold(CompressionThresholdS2CPacket packet) {
        if (!this.connection.isLocal()) {
            this.connection.setCompressionThreshold(packet.getCompressionThreshold());
        }
    }
}
