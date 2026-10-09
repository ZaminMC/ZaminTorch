package net.minecraft.server.rcon;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import net.minecraft.server.dedicated.DedicatedServerAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RconClient extends RconBase {
    private static final Logger LOGGER = LogManager.getLogger();
    private boolean authenticated;
    private Socket socket;
    private byte[] packetBuffer = new byte[1460];
    private String password;

    RconClient(DedicatedServerAccess server, Socket socket) {
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

    @Override
    public void run() {
        try {
            try {
                while (this.running) {
                    BufferedInputStream bufferedinputstream = new BufferedInputStream(this.socket.getInputStream());
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
                                    this.execute(l, "Error executing: " + s1 + " (" + exception.getMessage() + ")");
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
                            this.execute(l, String.format("Unknown request %s", Integer.toHexString(i1)));
                    }
                }

                return;
            } catch (SocketTimeoutException sockettimeoutexception) {
            } catch (IOException ioexception) {
            } catch (Exception exception1) {
                LOGGER.error("Exception whilst parsing RCON input", exception1);
            }
        } finally {
            this.close();
        }
    }

    private void execute(int stream1, int stream2, String text) throws IOException {
        ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream(1248);
        DataOutputStream dataoutputstream = new DataOutputStream(bytearrayoutputstream);
        byte[] abyte = text.getBytes("UTF-8");
        dataoutputstream.writeInt(Integer.reverseBytes(abyte.length + 10));
        dataoutputstream.writeInt(Integer.reverseBytes(stream1));
        dataoutputstream.writeInt(Integer.reverseBytes(stream2));
        dataoutputstream.write(abyte);
        dataoutputstream.write(0);
        dataoutputstream.write(0);
        this.socket.getOutputStream().write(bytearrayoutputstream.toByteArray());
    }

    private void executeUnknown() throws IOException {
        this.execute(-1, 2, "");
    }

    private void execute(int id, String name) throws IOException {
        int i = name.length();

        do {
            int j = 4096 <= i ? 4096 : i;
            this.execute(id, 0, name.substring(0, j));
            name = name.substring(j);
            i = name.length();
        } while (0 != i);
    }

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
}
