package net.minecraft.server.integrated;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LanServerPinger extends Thread {
    private static final AtomicInteger threadId = new AtomicInteger(0);
    private static final Logger LOGGER = LogManager.getLogger();
    private final String motd;
    private final DatagramSocket socket;
    private boolean isRunning = true;
    private final String port;

    public LanServerPinger(String motd, String port) throws IOException {
        super("LanServerPinger #" + threadId.incrementAndGet());
        this.motd = motd;
        this.port = port;
        this.setDaemon(true);
        this.socket = new DatagramSocket();
    }

    @Override
    public void run() {
        String s = getMetadata(this.motd, this.port);
        byte[] abyte = s.getBytes();

        while (!this.isInterrupted() && this.isRunning) {
            try {
                InetAddress inetaddress = InetAddress.getByName("224.0.2.60");
                DatagramPacket datagrampacket = new DatagramPacket(abyte, abyte.length, inetaddress, 4445);
                this.socket.send(datagrampacket);
            } catch (IOException ioexception) {
                LOGGER.warn("LanServerPinger: " + ioexception.getMessage());
                break;
            }

            try {
                sleep(1500L);
            } catch (InterruptedException interruptedexception) {
            }
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();
        this.isRunning = false;
    }

    public static String getMetadata(String motd, String port) {
        return "[MOTD]" + motd + "[/MOTD][AD]" + port + "[/AD]";
    }

    public static String parseMotd(String metadata) {
        int i = metadata.indexOf("[MOTD]");
        if (i < 0) {
            return "missing no";
        }

        int j = metadata.indexOf("[/MOTD]", i + "[MOTD]".length());
        return j < i ? "missing no" : metadata.substring(i + "[MOTD]".length(), j);
    }

    public static String parsePort(String metadata) {
        int i = metadata.indexOf("[/MOTD]");
        if (i < 0) {
            return null;
        }

        int j = metadata.indexOf("[/MOTD]", i + "[/MOTD]".length());
        if (j >= 0) {
            return null;
        }

        int k = metadata.indexOf("[AD]", i + "[/MOTD]".length());
        if (k < 0) {
            return null;
        }

        int l = metadata.indexOf("[/AD]", k + "[AD]".length());
        return l < k ? null : metadata.substring(k + "[AD]".length(), l);
    }
}
