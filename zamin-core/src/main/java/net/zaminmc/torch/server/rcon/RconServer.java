package net.zaminmc.torch.server.rcon;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.Iterator;
import java.util.Map;

/**
 * The RCON TCP listener, 1:1 the reference
 * (reference/1.8.8/net/minecraft/server/rcon/RconServer.java, 105 lines):
 * the port bind, the accept loop with its 500 ms accept-timeout cleanup
 * cadence, and the reference's own refusal laws in {@link #start()} — an
 * empty password or an out-of-range port warns and disables the listener,
 * never the server (RconServer.start lines 90-103).
 *
 * <p>The engine-side differences, both named:</p>
 * <ul>
 *   <li>The client map is a concurrent map — the reference's plain HashMap
 *       was touched only by the listener thread, but the engine's stop verb
 *       walks it from the shutdown thread (the lifecycle addition).</li>
 *   <li>{@link #stop()} closes the listener AND every live client socket
 *       and interrupts the client threads — a stopped engine server must
 *       not keep blocked remote-admin threads alive (the launcher's
 *       idempotent shutdown law; the reference had no stop verb). To make
 *       that walk airtight the map seat is taken BEFORE the client thread
 *       starts (the reference's own order runs one instruction the other
 *       way — a live-but-unmapped client would leak a parked thread past
 *       a racing stop).</li>
 * </ul>
 */
public class RconServer extends RconBase {

    private int port;
    private final int serverPort;
    private String serverIp;
    private ServerSocket listener;
    private final String password;
    private final Map<SocketAddress, RconClient> clients =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The reference lines 23-46: the property reads and the default-port
     * law. The engine's access answers the already-resolved rows (the
     * EngineConfig compact constructor applied RconServer lines 29-31's
     * {@code 0 -> serverPort + 10} law at load time — the immutable-record
     * adaptation; the reference's write-back arm here is therefore
     * structurally dormant, kept 1:1).
     */
    public RconServer(RconServerAccess server) {
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

    /** The reference lines 48-50 (a fresh map per construction; concurrent for the engine's stop walk). */
    private void clearClients() {
        this.clients.clear();
    }

    /**
     * The reference lines 52-61: the stopped-client sweep, running after
     * every accept and on every accept timeout. The reference's own quirk
     * kept 1:1: a finished client's {@code running} flag is never flipped,
     * so only a same-address reconnect replaces its map seat — the map
     * grows one entry per distinct remote address, exactly the reference's
     * shape.
     */
    private void removeStoppedClients() {
        Iterator<Map.Entry<SocketAddress, RconClient>> iterator = this.clients.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<SocketAddress, RconClient> entry = iterator.next();
            if (!entry.getValue().isRunning()) {
                iterator.remove();
            }
        }
    }

    /** The reference lines 64-87: the accept loop. */
    @Override
    public void run() {
        this.info("RCON running on " + this.serverIp + ":" + this.port);

        try {
            while (this.running) {
                try {
                    Socket socket = this.listener.accept();
                    socket.setSoTimeout(500);
                    RconClient rconclient = new RconClient(this.server, socket);
                    // The engine's stop-verb reorder (the reference starts
                    // the thread BEFORE the map seat, lines 72-74 — a stop
                    // racing the accept could then miss a live client whose
                    // thread is already parked in the infinite read): the
                    // seat goes in first so the stop walk always closes
                    // every accepted connection.
                    this.clients.put(socket.getRemoteSocketAddress(), rconclient);
                    rconclient.start();
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

    /**
     * The reference lines 90-104: the refusal laws. An empty password
     * (lines 91-92) or an invalid port (lines 93-94) warns and never
     * starts; a bind failure warns and degrades (lines 100-102) — the
     * server keeps running in every refusal arm.
     *
     * <p>The one engine-side difference, named: the socket sets
     * SO_REUSEADDR before the bind (the reference binds directly, line 97;
     * the engine's own net convention — the protocol adapter's transport
     * does the same). Without it a restarted server could NOT rebind its
     * rcon.port while a closed accepted-connection still holds the port in
     * TIME_WAIT, and the degrade law above would silently disable the
     * listener — the exact restart hazard the engine's shutdown law
     * forbids.</p>
     */
    @Override
    public void start() {
        if (0 == this.password.length()) {
            this.warn("No rcon password set in '" + this.server.getPropertiesFilePath()
                    + "', rcon disabled!");
        } else if (0 >= this.port || 65535 < this.port) {
            this.warn("Invalid rcon port " + this.port + " found in '"
                    + this.server.getPropertiesFilePath() + "', rcon disabled!");
        } else if (!this.running) {
            try {
                ServerSocket listener = new ServerSocket();
                listener.setReuseAddress(true);
                listener.bind(new java.net.InetSocketAddress(
                        InetAddress.getByName(this.serverIp), this.port), 0);
                this.listener = listener;
                this.listener.setSoTimeout(500);
                super.start();
            } catch (IOException ioexception) {
                this.warn("Unable to initialise rcon on " + this.serverIp + ":"
                        + this.port + " : " + ioexception.getMessage());
            }
        }
    }

    /**
     * The engine's lifecycle addition: flip the loop flag, then close every
     * live client (its socket unblocks the read, its interrupt unblocks a
     * command wait) and the listener itself (unblocking accept). Idempotent
     * — every close consults its own socket state first.
     */
    @Override
    public void stop() {
        super.stop();
        for (RconClient client : this.clients.values()) {
            client.stop();
        }
        this.clients.clear();
        this.closeSocket(this.listener);
    }
}
