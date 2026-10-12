package net.minecraft.realms;

import java.net.InetAddress;
import java.net.UnknownHostException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.handler.ClientLoginNetworkHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.NetworkProtocol;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.HelloC2SPacket;
import net.minecraft.text.TranslatableText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RealmsConnect {
    private static final Logger LOGGER = LogManager.getLogger();
    private final RealmsScreen onlineScreen;
    private volatile boolean aborted = false;
    private Connection connection;

    public RealmsConnect(RealmsScreen parent) {
        this.onlineScreen = parent;
    }

    public void connect(String address, int port) {
        Realms.setConnectedToRealms(true);
        (new Thread("Realms-connect-task") {
                @Override
                public void run() {
                    InetAddress inetaddress = null;

                    try {
                        inetaddress = InetAddress.getByName(address);
                        if (RealmsConnect.this.aborted) {
                            return;
                        }

                        RealmsConnect.this.connection = Connection.connect(inetaddress, port, Minecraft.getInstance().options.shouldUseNativeTransport());
                        if (RealmsConnect.this.aborted) {
                            return;
                        }

                        RealmsConnect.this.connection
                            .setListener(
                                new ClientLoginNetworkHandler(
                                    RealmsConnect.this.connection, Minecraft.getInstance(), RealmsConnect.this.onlineScreen.getProxy()
                                )
                            );
                        if (RealmsConnect.this.aborted) {
                            return;
                        }

                        RealmsConnect.this.connection.send(new HandshakeC2SPacket(47, address, port, NetworkProtocol.LOGIN));
                        if (RealmsConnect.this.aborted) {
                            return;
                        }

                        RealmsConnect.this.connection.send(new HelloC2SPacket(Minecraft.getInstance().getSession().getProfile()));
                    } catch (UnknownHostException unknownhostexception) {
                        Realms.clearResourcePack();
                        if (RealmsConnect.this.aborted) {
                            return;
                        }

                        RealmsConnect.LOGGER.error("Couldn't connect to world", unknownhostexception);
                        Minecraft.getInstance().getResourcePacks().removeServerPack();
                        Realms.setScreen(
                            new DisconnectedRealmsScreen(
                                RealmsConnect.this.onlineScreen,
                                "connect.failed",
                                new TranslatableText("disconnect.genericReason", "Unknown host '" + address + "'")
                            )
                        );
                    } catch (Exception exception) {
                        Realms.clearResourcePack();
                        if (RealmsConnect.this.aborted) {
                            return;
                        }

                        RealmsConnect.LOGGER.error("Couldn't connect to world", exception);
                        String s = exception.toString();
                        if (inetaddress != null) {
                            String s1 = inetaddress.toString() + ":" + port;
                            s = s.replaceAll(s1, "");
                        }

                        Realms.setScreen(
                            new DisconnectedRealmsScreen(RealmsConnect.this.onlineScreen, "connect.failed", new TranslatableText("disconnect.genericReason", s))
                        );
                    }
                }
            })
            .start();
    }

    public void abort() {
        this.aborted = true;
    }

    public void tick() {
        if (this.connection != null) {
            if (this.connection.isConnected()) {
                this.connection.tick();
            } else {
                this.connection.handleDisconnection();
            }
        }
    }
}
