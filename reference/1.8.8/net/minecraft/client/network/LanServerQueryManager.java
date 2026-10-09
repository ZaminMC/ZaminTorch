package net.minecraft.client.network;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.LanServerPinger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LanServerQueryManager {
    private static final AtomicInteger threadId = new AtomicInteger(0);
    private static final Logger LOGGER = LogManager.getLogger();

    public static class LanServer {
        private String motd;
        private String port;
        private long lastTickTime;

        public LanServer(String motd, String port) {
            this.motd = motd;
            this.port = port;
            this.lastTickTime = Minecraft.getTime();
        }

        public String getMotd() {
            return this.motd;
        }

        public String getPort() {
            return this.port;
        }

        public void tick() {
            this.lastTickTime = Minecraft.getTime();
        }
    }

    public static class LanServerDetector extends Thread {
        private final LanServerQueryManager.LanServerList serverList;
        private final InetAddress address;
        private final MulticastSocket socket;

        public LanServerDetector(LanServerQueryManager.LanServerList serverList) throws IOException {
            super("LanServerDetector #" + LanServerQueryManager.threadId.incrementAndGet());
            this.serverList = serverList;
            this.setDaemon(true);
            this.socket = new MulticastSocket(4445);
            this.address = InetAddress.getByName("224.0.2.60");
            this.socket.setSoTimeout(5000);
            this.socket.joinGroup(this.address);
        }

        @Override
        public void run() {
            byte[] abyte = new byte[1024];

            while (!this.isInterrupted()) {
                DatagramPacket datagrampacket = new DatagramPacket(abyte, abyte.length);

                try {
                    this.socket.receive(datagrampacket);
                } catch (SocketTimeoutException sockettimeoutexception) {
                    continue;
                } catch (IOException ioexception1) {
                    LanServerQueryManager.LOGGER.error("Couldn't ping server", ioexception1);
                    break;
                }

                String s = new String(datagrampacket.getData(), datagrampacket.getOffset(), datagrampacket.getLength());
                LanServerQueryManager.LOGGER.debug(datagrampacket.getAddress() + ": " + s);
                this.serverList.addServer(s, datagrampacket.getAddress());
            }

            try {
                this.socket.leaveGroup(this.address);
            } catch (IOException ioexception) {
            }

            this.socket.close();
        }
    }

    public static class LanServerList {
        private List<LanServerQueryManager.LanServer> servers = Lists.newArrayList();
        boolean dirty;

        public synchronized boolean needsUpdate() {
            return this.dirty;
        }

        public synchronized void markClean() {
            this.dirty = false;
        }

        public synchronized List<LanServerQueryManager.LanServer> getServers() {
            return Collections.unmodifiableList(this.servers);
        }

        public synchronized void addServer(String metadata, InetAddress address) {
            String s = LanServerPinger.parseMotd(metadata);
            String s1 = LanServerPinger.parsePort(metadata);
            if (s1 != null) {
                s1 = address.getHostAddress() + ":" + s1;
                boolean flag = false;

                for (LanServerQueryManager.LanServer lanserverquerymanager$lanserver : this.servers) {
                    if (lanserverquerymanager$lanserver.getPort().equals(s1)) {
                        lanserverquerymanager$lanserver.tick();
                        flag = true;
                        break;
                    }
                }

                if (!flag) {
                    this.servers.add(new LanServerQueryManager.LanServer(s, s1));
                    this.dirty = true;
                }
            }
        }
    }
}
