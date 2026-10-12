package net.minecraft.server.rcon;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.dedicated.DedicatedServerAccess;

public abstract class RconBase implements Runnable {
    private static final AtomicInteger count = new AtomicInteger(0);
    protected boolean running;
    protected DedicatedServerAccess server;
    protected final String description;
    protected Thread thread;
    protected int f_8753444 = 5;
    protected List<DatagramSocket> sockets = Lists.newArrayList();
    protected List<ServerSocket> closeableSockets = Lists.newArrayList();

    protected RconBase(DedicatedServerAccess server, String description) {
        this.server = server;
        this.description = description;
        if (this.server.isDebuggingEnabled()) {
            this.warn("Debugging is enabled, performance maybe reduced!");
        }
    }

    public synchronized void start() {
        this.thread = new Thread(this, this.description + " #" + count.incrementAndGet());
        this.thread.start();
        this.running = true;
    }

    public boolean isRunning() {
        return this.running;
    }

    protected void log(String message) {
        this.server.log(message);
    }

    protected void info(String message) {
        this.server.info(message);
    }

    protected void warn(String message) {
        this.server.warn(message);
    }

    protected void logError(String essage) {
        this.server.error(essage);
    }

    protected int getCurrentPlayerCount() {
        return this.server.getPlayerCount();
    }

    protected void registerSocket(DatagramSocket datagramSocket) {
        this.log("registerSocket: " + datagramSocket);
        this.sockets.add(datagramSocket);
    }

    protected boolean closeSocket(DatagramSocket socket, boolean remove) {
        this.log("closeSocket: " + socket);
        if (null == socket) {
            return false;
        }

        boolean flag = false;
        if (!socket.isClosed()) {
            socket.close();
            flag = true;
        }

        if (remove) {
            this.sockets.remove(socket);
        }

        return flag;
    }

    protected boolean closeSocket(ServerSocket socket) {
        return this.closeSocket(socket, true);
    }

    protected boolean closeSocket(ServerSocket socket, boolean remove) {
        this.log("closeSocket: " + socket);
        if (null == socket) {
            return false;
        }

        boolean flag = false;

        try {
            if (!socket.isClosed()) {
                socket.close();
                flag = true;
            }
        } catch (IOException ioexception) {
            this.warn("IO: " + ioexception.getMessage());
        }

        if (remove) {
            this.closeableSockets.remove(socket);
        }

        return flag;
    }

    protected void forceClose() {
        this.forceClose(false);
    }

    protected void forceClose(boolean warn) {
        int i = 0;

        for (DatagramSocket datagramsocket : this.sockets) {
            if (this.closeSocket(datagramsocket, false)) {
                i++;
            }
        }

        this.sockets.clear();

        for (ServerSocket serversocket : this.closeableSockets) {
            if (this.closeSocket(serversocket, false)) {
                i++;
            }
        }

        this.closeableSockets.clear();
        if (warn && 0 < i) {
            this.warn("Force closed " + i + " sockets");
        }
    }
}
