package net.minecraft.network;

import net.minecraft.network.handler.PacketHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.BlockableEventLoop;

public class PacketUtils {
    public static <T extends PacketHandler> void ensureOnSameThread(Packet<T> packet, T listener, BlockableEventLoop runner) throws DifferentThreadException {
        if (!runner.isOnSameThread()) {
            runner.execute(new Runnable() {
                @Override
                public void run() {
                    packet.handle(listener);
                }
            });
            throw DifferentThreadException.INSTANCE;
        }
    }
}
