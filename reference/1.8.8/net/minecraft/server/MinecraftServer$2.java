package net.minecraft.server;

import net.minecraft.server.dedicated.DedicatedServer;

final class MinecraftServer$2 extends Thread {
    MinecraftServer$2(String string, DedicatedServer dedicatedServer) {
        super(string);
        this.f_5439840 = dedicatedServer;
    }

    @Override
    public void run() {
        this.f_5439840.shutdown();
    }
}
