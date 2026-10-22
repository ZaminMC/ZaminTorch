package net.zaminmc.torch.server.rcon;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The RCON per-connection loop, 1:1 the reference
 * (reference/1.8.8/net/minecraft/server/rcon/RconClient.java, 138 lines): the
 * 1460-byte single-read framing (the reference's own one-segment assumption,
 * line 40), the length check (line 47), the auth gate with the id law
 * (success answers the request id, failure answers -1 — lines 74-82), the
 * command dispatch through the server's console route (line 62), and the
 * 4096-CHARACTER response split with its do-while trailing packet (lines
 * 116-125 — an empty output still answers ONE empty packet; that is the
 * broadcast quirk vanilla clients rely on).
 *
 * <p>The engine-side difference, named: the log4j LOGGER of reference line
 * 14 is the engine's JUL logger (§30's logging-backend decision).</p>
 */
public class RconClient extends RconBase {

    private static final Logger LOGGER = Logger.getLogger(RconClient.class.getName());

    private boolean authenticated;
    private Socket socket;
    private final byte[] packetBuffer = new byte[1460];
    private final String password;

    /** The reference lines 20-32: the socket, the password read, the connect log. */
    RconClient(RconServerAccess server, Socket socket) {
        super(server, "RCON Client");
        this.socket = socket;

        try {
            this.socket.setSoTimeout(0);
        } catch (Exception exception) {
            this.running = false;
        }

        this.password = server.getStringProperty("rcon.password", "");
        this.info("Rcon connection from: " + socket.getInetAddress());
    }

    /**
     * The reference lines 35-97: the read loop. A short read ({@code 10 > i},
     * line 41 — EOF included) or a length mismatch (line 47) closes; type 2
     * executes only when authenticated (line 58), type 3 is the auth gate
     * (lines 71-82), anything else answers the unknown-request line (lines
     * 83-84).
     */
    @Override
    public void run() {
        try {
            try {
                while (this.running) {
                    BufferedInputStream bufferedinputstream = new BufferedInputStream(
                            this.socket.getInputStream());
                    int i = bufferedinputstream.read(this.packetBuffer, 0, 1460);
                    if (10 > i) {
                        return;
                    }

                    int j = 0;
                    int k = BufferHelper.getIntLE(this.packetBuffer, 0, i);
                    if (k != i - 4) {
                        return;
                    }

                    j += 4;
                    int l = BufferHelper.getIntLE(this.packetBuffer, j, i);
                    j += 4;
                    int i1 = BufferHelper.getIntLE(this.packetBuffer, j);
                    j += 4;
                    switch (i1) {
                        case 2:
                            if (this.authenticated) {
                                String s1 = BufferHelper.getString(this.packetBuffer, j, i);

                                try {
                                    this.execute(l, this.server.runRconCommand(s1));
                                } catch (Exception exception) {
                                    this.execute(l, "Error executing: " + s1
                                            + " (" + exception.getMessage() + ")");
                                }
                                break;
                            }

                            this.executeUnknown();
                            break;
                        case 3:
                            String s = BufferHelper.getString(this.packetBuffer, j, i);
                            j += s.length();
                            if (0 != s.length() && s.equals(this.password)) {
                                this.authenticated = true;
                                this.execute(l, 2, "");
                                break;
                            }

                            this.authenticated = false;
                            this.executeUnknown();
                            break;
                        default:
                            this.execute(l, String.format("Unknown request %s",
                                    Integer.toHexString(i1)));
                    }
                }

                return;
            } catch (SocketTimeoutException sockettimeoutexception) {
            } catch (IOException ioexception) {
            } catch (Exception exception1) {
                LOGGER.log(Level.SEVERE, "Exception whilst parsing RCON input", exception1);
            }
        } finally {
            this.close();
        }
    }

    /**
     * The reference lines 99-110: the one packet frame — little-endian
     * length (body + 10: the two nulls and the id/type words, not counting
     * the length word itself), little-endian id, little-endian type, the
     * UTF-8 body, then the two trailing NULs.
     */
    private void execute(int stream1, int stream2, String text) throws IOException {
        ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream(1248);
        DataOutputStream dataoutputstream = new DataOutputStream(bytearrayoutputstream);
        byte[] abyte = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        dataoutputstream.writeInt(Integer.reverseBytes(abyte.length + 10));
        dataoutputstream.writeInt(Integer.reverseBytes(stream1));
        dataoutputstream.writeInt(Integer.reverseBytes(stream2));
        dataoutputstream.write(abyte);
        dataoutputstream.write(0);
        dataoutputstream.write(0);
        this.socket.getOutputStream().write(bytearrayoutputstream.toByteArray());
    }

    /** The reference lines 112-114: the auth-failure frame (id -1, type 2, empty). */
    private void executeUnknown() throws IOException {
        this.execute(-1, 2, "");
    }

    /**
     * The reference lines 116-125: the response split — 4096-CHARACTER
     * chunks (the reference splits on String length, not bytes — the
     * vanilla law, ported as-is), and the do-while's own trailing quirk:
     * the loop always answers at least one packet, so an empty output
     * yields exactly one empty packet and a 4096-char output exactly one
     * full packet.
     */
    private void execute(int id, String name) throws IOException {
        int i = name.length();

        do {
            int j = 4096 <= i ? 4096 : i;
            this.execute(id, 0, name.substring(0, j));
            name = name.substring(j);
            i = name.length();
        } while (0 != i);
    }

    /** The reference lines 127-137: the idempotent socket close. */
    private void close() {
        if (null != this.socket) {
            try {
                this.socket.close();
            } catch (IOException ioexception) {
                this.warn("IO: " + ioexception.getMessage());
            }

            this.socket = null;
        }
    }

    /**
     * The engine's lifecycle addition (the reference has no stop verb):
     * beside the base's flag flip + interrupt, the socket close is what
     * unblocks a thread parked in a read — a stopped server must not leave
     * the remote threads alive.
     */
    @Override
    public void stop() {
        super.stop();
        this.close();
    }
}
