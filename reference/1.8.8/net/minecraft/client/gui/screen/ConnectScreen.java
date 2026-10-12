package net.minecraft.client.gui.screen;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.handler.ClientLoginNetworkHandler;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.network.Connection;
import net.minecraft.network.NetworkProtocol;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.HelloC2SPacket;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConnectScreen extends Screen {
    private static final AtomicInteger connectorThreadsCount = new AtomicInteger(0);
    private static final Logger LOGGER = LogManager.getLogger();
    private Connection connection;
    private boolean aborted;
    private final Screen parent;

    public ConnectScreen(Screen parent, Minecraft minecraft, ServerListEntry server) {
        this.minecraft = minecraft;
        this.parent = parent;
        ServerAddress serveraddress = ServerAddress.parse(server.ip);
        minecraft.setWorld(null);
        minecraft.setCurrentServerEntry(server);
        this.connect(serveraddress.getAddress(), serveraddress.getPort());
    }

    public ConnectScreen(Screen parent, Minecraft minecraft, String address, int port) {
        this.minecraft = minecraft;
        this.parent = parent;
        minecraft.setWorld(null);
        this.connect(address, port);
    }

    private void connect(String address, int port) {
        LOGGER.info("Connecting to " + address + ", " + port);
        (new Thread("Server Connector #" + connectorThreadsCount.incrementAndGet()) {
                @Override
                public void run() {
                    InetAddress inetaddress = null;

                    try {
                        if (ConnectScreen.this.aborted) {
                            return;
                        }

                        inetaddress = InetAddress.getByName(address);
                        ConnectScreen.this.connection = Connection.connect(inetaddress, port, ConnectScreen.this.minecraft.options.shouldUseNativeTransport());
                        ConnectScreen.this.connection
                            .setListener(new ClientLoginNetworkHandler(ConnectScreen.this.connection, ConnectScreen.this.minecraft, ConnectScreen.this.parent));
                        ConnectScreen.this.connection.send(new HandshakeC2SPacket(47, address, port, NetworkProtocol.LOGIN));
                        ConnectScreen.this.connection.send(new HelloC2SPacket(ConnectScreen.this.minecraft.getSession().getProfile()));
                    } catch (UnknownHostException unknownhostexception) {
                        if (ConnectScreen.this.aborted) {
                            return;
                        }

                        ConnectScreen.LOGGER.error("Couldn't connect to server", unknownhostexception);
                        ConnectScreen.this.minecraft
                            .openScreen(
                                new DisconnectedScreen(
                                    ConnectScreen.this.parent, "connect.failed", new TranslatableText("disconnect.genericReason", "Unknown host")
                                )
                            );
                    } catch (Exception exception) {
                        if (ConnectScreen.this.aborted) {
                            return;
                        }

                        ConnectScreen.LOGGER.error("Couldn't connect to server", exception);
                        String s = exception.toString();
                        if (inetaddress != null) {
                            String s1 = inetaddress.toString() + ":" + port;
                            s = s.replaceAll(s1, "");
                        }

                        ConnectScreen.this.minecraft
                            .openScreen(
                                new DisconnectedScreen(ConnectScreen.this.parent, "connect.failed", new TranslatableText("disconnect.genericReason", s))
                            );
                    }
                }
            })
            .start();
    }

    @Override
    public void tick() {
        if (this.connection != null) {
            if (this.connection.isConnected()) {
                this.connection.tick();
            } else {
                this.connection.handleDisconnection();
            }
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
    }

    @Override
    public void init() {
        this.buttons.clear();
        this.buttons.add(new ButtonWidget(0, this.width / 2 - 100, this.height / 4 + 120 + 12, I18n.translate("gui.cancel")));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == 0) {
            this.aborted = true;
            if (this.connection != null) {
                this.connection.disconnect(new LiteralText("Aborted"));
            }

            this.minecraft.openScreen(this.parent);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        if (this.connection == null) {
            this.drawCenteredString(this.textRenderer, I18n.translate("connect.connecting"), this.width / 2, this.height / 2 - 50, 16777215);
        } else {
            this.drawCenteredString(this.textRenderer, I18n.translate("connect.authorizing"), this.width / 2, this.height / 2 - 50, 16777215);
        }

        super.render(mouseX, mouseY, tickDelta);
    }
}
