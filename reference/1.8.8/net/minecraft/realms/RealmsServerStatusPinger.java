package net.minecraft.realms;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.network.handler.ClientQueryPacketHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.NetworkProtocol;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.query.PingC2SPacket;
import net.minecraft.network.packet.c2s.query.ServerStatusC2SPacket;
import net.minecraft.network.packet.s2c.query.PingS2CPacket;
import net.minecraft.network.packet.s2c.query.ServerStatusS2CPacket;
import net.minecraft.server.ServerStatus;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RealmsServerStatusPinger {
    private static final Logger LOGGER = LogManager.getLogger();
    private final List<Connection> connections = Collections.synchronizedList(Lists.newArrayList());

    public void pingServer(String ip, RealmsServerPing serverPing) throws UnknownHostException {
        if (ip != null && !ip.startsWith("0.0.0.0") && !ip.isEmpty()) {
            RealmsServerAddress realmsserveraddress = RealmsServerAddress.parseString(ip);
            final Connection connection = Connection.connect(InetAddress.getByName(realmsserveraddress.getHost()), realmsserveraddress.getPort(), false);
            this.connections.add(connection);
            connection.setListener(
                new ClientQueryPacketHandler() {
                    private boolean f_8915524 = false;

                    @Override
                    public void handleServerStatus(ServerStatusS2CPacket packet) {
                        ServerStatus serverstatus = packet.getServerStatus();
                        if (serverstatus.getPlayers() != null) {
                            serverPing.nrOfPlayers = String.valueOf(serverstatus.getPlayers().getOnline());
                            if (ArrayUtils.isNotEmpty(serverstatus.getPlayers().get())) {
                                StringBuilder stringbuilder = new StringBuilder();

                                for (GameProfile gameprofile : serverstatus.getPlayers().get()) {
                                    if (stringbuilder.length() > 0) {
                                        stringbuilder.append("\n");
                                    }

                                    stringbuilder.append(gameprofile.getName());
                                }

                                if (serverstatus.getPlayers().get().length < serverstatus.getPlayers().getOnline()) {
                                    if (stringbuilder.length() > 0) {
                                        stringbuilder.append("\n");
                                    }

                                    stringbuilder.append("... and ")
                                        .append(serverstatus.getPlayers().getOnline() - serverstatus.getPlayers().get().length)
                                        .append(" more ...");
                                }

                                serverPing.playerList = stringbuilder.toString();
                            }
                        } else {
                            serverPing.playerList = "";
                        }

                        connection.send(new PingC2SPacket(Realms.currentTimeMillis()));
                        this.f_8915524 = true;
                    }

                    @Override
                    public void handlePing(PingS2CPacket packet) {
                        connection.disconnect(new LiteralText("Finished"));
                    }

                    @Override
                    public void onDisconnect(Text reason) {
                        if (!this.f_8915524) {
                            RealmsServerStatusPinger.LOGGER.error("Can't ping " + ip + ": " + reason.getString());
                        }
                    }
                }
            );

            try {
                connection.send(
                    new HandshakeC2SPacket(
                        RealmsSharedConstants.NETWORK_PROTOCOL_VERSION, realmsserveraddress.getHost(), realmsserveraddress.getPort(), NetworkProtocol.STATUS
                    )
                );
                connection.send(new ServerStatusC2SPacket());
            } catch (Throwable throwable) {
                LOGGER.error(throwable);
            }
        }
    }

    public void tick() {
        synchronized (this.connections) {
            Iterator<Connection> iterator = this.connections.iterator();

            while (iterator.hasNext()) {
                Connection connection = iterator.next();
                if (connection.isConnected()) {
                    connection.tick();
                } else {
                    iterator.remove();
                    connection.handleDisconnection();
                }
            }
        }
    }

    public void removeAll() {
        synchronized (this.connections) {
            Iterator<Connection> iterator = this.connections.iterator();

            while (iterator.hasNext()) {
                Connection connection = iterator.next();
                if (connection.isConnected()) {
                    iterator.remove();
                    connection.disconnect(new LiteralText("Cancelled"));
                }
            }
        }
    }
}
