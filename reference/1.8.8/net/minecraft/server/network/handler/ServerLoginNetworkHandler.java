package net.minecraft.server.network.handler;

import com.google.common.base.Charsets;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import net.minecraft.network.Connection;
import net.minecraft.network.encryption.EncryptionUtils;
import net.minecraft.network.packet.c2s.login.HelloC2SPacket;
import net.minecraft.network.packet.c2s.login.KeyC2SPacket;
import net.minecraft.network.packet.s2c.login.CompressionThresholdS2CPacket;
import net.minecraft.network.packet.s2c.login.HelloS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginFailS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Tickable;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerLoginNetworkHandler implements ServerLoginPacketHandler, Tickable {
    private static final AtomicInteger authenticatorThreadId = new AtomicInteger(0);
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Random RANDOM = new Random();
    private final byte[] nonce = new byte[4];
    private final MinecraftServer server;
    public final Connection connection;
    private ServerLoginNetworkHandler.Stage stage = ServerLoginNetworkHandler.Stage.HELLO;
    private int ticks;
    private GameProfile profile;
    private String key = "";
    private SecretKey secretKey;
    private ServerPlayerEntity player;

    public ServerLoginNetworkHandler(MinecraftServer server, Connection connection) {
        this.server = server;
        this.connection = connection;
        RANDOM.nextBytes(this.nonce);
    }

    @Override
    public void tick() {
        if (this.stage == ServerLoginNetworkHandler.Stage.READY_TO_ACCEPT) {
            this.acceptLogin();
        } else if (this.stage == ServerLoginNetworkHandler.Stage.DELAY_ACCEPT) {
            ServerPlayerEntity serverplayerentity = this.server.getPlayerManager().get(this.profile.getId());
            if (serverplayerentity == null) {
                this.stage = ServerLoginNetworkHandler.Stage.READY_TO_ACCEPT;
                this.server.getPlayerManager().onLogin(this.connection, this.player);
                this.player = null;
            }
        }

        if (this.ticks++ == 600) {
            this.disconnect("Took too long to log in");
        }
    }

    public void disconnect(String reason) {
        try {
            LOGGER.info("Disconnecting " + this.getConnectionInfo() + ": " + reason);
            LiteralText literaltext = new LiteralText(reason);
            this.connection.send(new LoginFailS2CPacket(literaltext));
            this.connection.disconnect(literaltext);
        } catch (Exception exception) {
            LOGGER.error("Error whilst disconnecting player", exception);
        }
    }

    public void acceptLogin() {
        if (!this.profile.isComplete()) {
            this.profile = this.createFakeProfile(this.profile);
        }

        String s = this.server.getPlayerManager().canLogin(this.connection.getAddress(), this.profile);
        if (s != null) {
            this.disconnect(s);
        } else {
            this.stage = ServerLoginNetworkHandler.Stage.ACCEPTED;
            if (this.server.getNetworkCompressionThreshold() >= 0 && !this.connection.isLocal()) {
                this.connection
                    .send(
                        new CompressionThresholdS2CPacket(this.server.getNetworkCompressionThreshold()),
                        new ChannelFutureListener() {
                            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                                ServerLoginNetworkHandler.this.connection
                                    .setCompressionThreshold(ServerLoginNetworkHandler.this.server.getNetworkCompressionThreshold());
                            }
                        }
                    );
            }

            this.connection.send(new LoginSuccessS2CPacket(this.profile));
            ServerPlayerEntity serverplayerentity = this.server.getPlayerManager().get(this.profile.getId());
            if (serverplayerentity != null) {
                this.stage = ServerLoginNetworkHandler.Stage.DELAY_ACCEPT;
                this.player = this.server.getPlayerManager().createForLogin(this.profile);
            } else {
                this.server.getPlayerManager().onLogin(this.connection, this.server.getPlayerManager().createForLogin(this.profile));
            }
        }
    }

    @Override
    public void onDisconnect(Text reason) {
        LOGGER.info(this.getConnectionInfo() + " lost connection: " + reason.getString());
    }

    public String getConnectionInfo() {
        return this.profile != null
            ? this.profile.toString() + " (" + this.connection.getAddress().toString() + ")"
            : String.valueOf(this.connection.getAddress());
    }

    @Override
    public void handleHello(HelloC2SPacket packet) {
        Validate.validState(this.stage == ServerLoginNetworkHandler.Stage.HELLO, "Unexpected hello packet");
        this.profile = packet.getProfile();
        if (this.server.isOnlineMode() && !this.connection.isLocal()) {
            this.stage = ServerLoginNetworkHandler.Stage.KEY;
            this.connection.send(new HelloS2CPacket(this.key, this.server.getKeyPair().getPublic(), this.nonce));
        } else {
            this.stage = ServerLoginNetworkHandler.Stage.READY_TO_ACCEPT;
        }
    }

    @Override
    public void handleKey(KeyC2SPacket packet) {
        Validate.validState(this.stage == ServerLoginNetworkHandler.Stage.KEY, "Unexpected key packet");
        PrivateKey privatekey = this.server.getKeyPair().getPrivate();
        if (!Arrays.equals(this.nonce, packet.getNonce(privatekey))) {
            throw new IllegalStateException("Invalid nonce!");
        }

        this.secretKey = packet.getSecretKey(privatekey);
        this.stage = ServerLoginNetworkHandler.Stage.AUTHENTICATING;
        this.connection.setupEncryption(this.secretKey);
        (new Thread("User Authenticator #" + authenticatorThreadId.incrementAndGet()) {
                @Override
                public void run() {
                    GameProfile gameprofile = ServerLoginNetworkHandler.this.profile;

                    try {
                        String s = new BigInteger(
                                EncryptionUtils.digest(
                                    ServerLoginNetworkHandler.this.key,
                                    ServerLoginNetworkHandler.this.server.getKeyPair().getPublic(),
                                    ServerLoginNetworkHandler.this.secretKey
                                )
                            )
                            .toString(16);
                        ServerLoginNetworkHandler.this.profile = ServerLoginNetworkHandler.this.server
                            .getSessionService()
                            .hasJoinedServer(new GameProfile(null, gameprofile.getName()), s);
                        if (ServerLoginNetworkHandler.this.profile != null) {
                            ServerLoginNetworkHandler.LOGGER
                                .info(
                                    "UUID of player "
                                        + ServerLoginNetworkHandler.this.profile.getName()
                                        + " is "
                                        + ServerLoginNetworkHandler.this.profile.getId()
                                );
                            ServerLoginNetworkHandler.this.stage = ServerLoginNetworkHandler.Stage.READY_TO_ACCEPT;
                        } else if (ServerLoginNetworkHandler.this.server.isSingleplayer()) {
                            ServerLoginNetworkHandler.LOGGER.warn("Failed to verify username but will let them in anyway!");
                            ServerLoginNetworkHandler.this.profile = ServerLoginNetworkHandler.this.createFakeProfile(gameprofile);
                            ServerLoginNetworkHandler.this.stage = ServerLoginNetworkHandler.Stage.READY_TO_ACCEPT;
                        } else {
                            ServerLoginNetworkHandler.this.disconnect("Failed to verify username!");
                            ServerLoginNetworkHandler.LOGGER
                                .error("Username '" + ServerLoginNetworkHandler.this.profile.getName() + "' tried to join with an invalid session");
                        }
                    } catch (AuthenticationUnavailableException authenticationunavailableexception) {
                        if (ServerLoginNetworkHandler.this.server.isSingleplayer()) {
                            ServerLoginNetworkHandler.LOGGER.warn("Authentication servers are down but will let them in anyway!");
                            ServerLoginNetworkHandler.this.profile = ServerLoginNetworkHandler.this.createFakeProfile(gameprofile);
                            ServerLoginNetworkHandler.this.stage = ServerLoginNetworkHandler.Stage.READY_TO_ACCEPT;
                        } else {
                            ServerLoginNetworkHandler.this.disconnect("Authentication servers are down. Please try again later, sorry!");
                            ServerLoginNetworkHandler.LOGGER.error("Couldn't verify username because servers are unavailable");
                        }
                    }
                }
            })
            .start();
    }

    protected GameProfile createFakeProfile(GameProfile profile) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + profile.getName()).getBytes(Charsets.UTF_8));
        return new GameProfile(uuid, profile.getName());
    }

    enum Stage {
        HELLO,
        KEY,
        AUTHENTICATING,
        READY_TO_ACCEPT,
        DELAY_ACCEPT,
        ACCEPTED;
    }
}
