package net.minecraft.realms;

import net.minecraft.client.network.ServerAddress;

public class RealmsServerAddress {
    private final String host;
    private final int port;

    protected RealmsServerAddress(String string, int i) {
        this.host = string;
        this.port = i;
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    public static RealmsServerAddress parseString(String address) {
        ServerAddress serveraddress = ServerAddress.parse(address);
        return new RealmsServerAddress(serveraddress.getAddress(), serveraddress.getPort());
    }
}
