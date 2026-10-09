package net.minecraft.server.rcon;

import com.google.common.collect.Maps;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.server.dedicated.DedicatedServerAccess;

public class RconServer extends RconBase {
    private int port;
    private int serverPort;
    private String serverIp;
    private ServerSocket listener;
    private String password;
    private Map<SocketAddress, RconClient> clients;

    public RconServer(DedicatedServerAccess server) {
        super(server, "RCON Listener");
        this.port = server.getIntProperty("rcon.port", 0);
        this.password = server.getStringProperty("rcon.password", "");
        this.serverIp = server.getIp();
        this.serverPort = server.getPort();
        if (0 == this.port) {
            this.port = this.serverPort + 10;
            this.info("Setting default rcon port to " + this.port);
            server.setProperty("rcon.port", this.port);
            if (0 == this.password.length()) {
                server.setProperty("rcon.password", "");
            }

            server.saveProperties();
        }

        if (0 == this.serverIp.length()) {
            this.serverIp = "0.0.0.0";
        }

        this.clearClients();
        this.listener = null;
    }

    private void clearClients() {
        this.clients = Maps.newHashMap();
    }

    private void removeStoppedClients() {
        Iterator<Entry<SocketAddress, RconClient>> iterator = this.clients.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<SocketAddress, RconClient> entry = iterator.next();
            if (!entry.getValue().isRunning()) {
                iterator.remove();
            }
        }
    }

    @Override
    public void run() {
        this.info("RCON running on " + this.serverIp + ":" + this.port);

        try {
            while (this.running) {
                try {
                    Socket socket = this.listener.accept();
                    socket.setSoTimeout(500);
                    RconClient rconclient = new RconClient(this.server, socket);
                    rconclient.start();
                    this.clients.put(socket.getRemoteSocketAddress(), rconclient);
                    this.removeStoppedClients();
                } catch (SocketTimeoutException sockettimeoutexception) {
                    this.removeStoppedClients();
                } catch (IOException ioexception) {
                    if (this.running) {
                        this.info("IO: " + ioexception.getMessage());
                    }
                }
            }
        } finally {
            this.closeSocket(this.listener);
        }
    }

    @Override
    public void start() {
        if (0 == this.password.length()) {
            this.warn("No rcon password set in '" + this.server.getPropertiesFilePath() + "', rcon disabled!");
        } else if (0 >= this.port || 65535 < this.port) {
            this.warn("Invalid rcon port " + this.port + " found in '" + this.server.getPropertiesFilePath() + "', rcon disabled!");
        } else if (!this.running) {
            try {
                this.listener = new ServerSocket(this.port, 0, InetAddress.getByName(this.serverIp));
                this.listener.setSoTimeout(500);
                super.start();
            } catch (IOException ioexception) {
                this.warn("Unable to initialise rcon on " + this.serverIp + ":" + this.port + " : " + ioexception.getMessage());
            }
        }
    }
}
