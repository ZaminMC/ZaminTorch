package net.zaminmc.torch.server.rcon;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The remote-admin thread base, 1:1 the reference
 * (reference/1.8.8/net/minecraft/server/rcon/RconBase.java, 137 lines): the
 * counted named thread per listener (the reference's own thread-per-listener
 * model — never the tick lane; §6's law), the running flag, the socket
 * registries, and the close helpers.
 *
 * <p>Two engine-side differences, both named:</p>
 * <ul>
 *   <li>{@code running} is volatile — the engine's shutdown thread closes a
 *       listener from another thread, and the reference's own loops
 *       (`while (this.running)`) must observe the stop. The reference could
 *       rely on process exit; the engine's idempotent shutdown cannot.</li>
 *   <li>{@link #stop()} exists at all — the reference has no stop verb (a
 *       stopped vanilla server simply dies); the engine's launcher lifecycle
 *       demands the listeners close on shutdown, restart-safe and
 *       idempotent.</li>
 * </ul>
 *
 * <p>The reference's own unused field {@code f_8753444} (the decompile's
 * artifact) is omitted; Guava's Lists/Maps are the JDK collections here.</p>
 */
public abstract class RconBase implements Runnable {

    private static final AtomicInteger count = new AtomicInteger(0);

    /** Volatile for the cross-thread stop (the named adaptation above). */
    protected volatile boolean running;
    protected final RconServerAccess server;
    protected final String description;
    /** Assigned by {@link #start()} (the reference line 16, non-final). */
    protected Thread thread;
    protected final List<DatagramSocket> sockets = new ArrayList<>();
    protected final List<ServerSocket> closeableSockets = new ArrayList<>();

    protected RconBase(RconServerAccess server, String description) {
        this.server = server;
        this.description = description;
        if (this.server.isDebuggingEnabled()) {
            this.warn("Debugging is enabled, performance maybe reduced!");
        }
    }

    /** The reference lines 29-33: the counted named thread, synchronized. */
    public synchronized void start() {
        Thread worker = new Thread(this, this.description + " #" + count.incrementAndGet());
        worker.start();
        this.thread = worker;
        this.running = true;
    }

    /**
     * The engine's lifecycle verb (the named adaptation): flips the loop
     * flag and interrupts the thread so a blocked read or a blocked command
     * wait unblocks. Blocking socket reads are not interruptible in Java —
     * the concrete stops close their sockets beside this.
     */
    public void stop() {
        this.running = false;
        Thread worker = this.thread;
        if (worker != null) {
            worker.interrupt();
        }
    }

    /** The reference line 36. */
    public boolean isRunning() {
        return this.running;
    }

    /** The reference lines 55-57: the live player census the query replies read. */
    protected int getCurrentPlayerCount() {
        return this.server.getPlayerCount();
    }

    /** The reference lines 59-62. */
    protected void registerSocket(DatagramSocket datagramSocket) {
        this.log("registerSocket: " + datagramSocket);
        this.sockets.add(datagramSocket);
    }

    /** The reference lines 64-81. */
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

    /** The reference lines 83-85. */
    protected boolean closeSocket(ServerSocket socket) {
        return this.closeSocket(socket, true);
    }

    /** The reference lines 87-109. */
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

    /** The reference lines 111-113. */
    protected void forceClose() {
        this.forceClose(false);
    }

    /**
     * The reference lines 115-136. The engine's stop verb made this walk
     * cross-thread (the run loop's own finally calls it from the listener
     * thread while a shutdown-thread stop can be mid-walk), so each loop
     * iterates a SNAPSHOT of its list — both callers then close idempotently
     * (closeSocket consults isClosed) and the clears are safe to run twice.
     */
    protected void forceClose(boolean warn) {
        int i = 0;

        for (DatagramSocket datagramsocket : new ArrayList<>(this.sockets)) {
            if (this.closeSocket(datagramsocket, false)) {
                i++;
            }
        }

        this.sockets.clear();

        for (ServerSocket serversocket : new ArrayList<>(this.closeableSockets)) {
            if (this.closeSocket(serversocket, false)) {
                i++;
            }
        }

        this.closeableSockets.clear();
        if (warn && 0 < i) {
            this.warn("Force closed " + i + " sockets");
        }
    }

    /** The reference lines 39-41. */
    protected void log(String message) {
        this.server.log(message);
    }

    /** The reference lines 43-45. */
    protected void info(String message) {
        this.server.info(message);
    }

    /** The reference lines 47-49. */
    protected void warn(String message) {
        this.server.warn(message);
    }

    /** The reference lines 51-53 ({@code logError}, kept verbatim). */
    protected void logError(String essage) {
        this.server.error(essage);
    }
}
