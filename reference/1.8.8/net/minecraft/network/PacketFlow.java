package net.minecraft.network;

public enum PacketFlow {
    /**
     * Packets sent from the client to the server.
     */
    SERVERBOUND,
    /**
     * Packets sent from the server to the client.
     */
    CLIENTBOUND;
}
