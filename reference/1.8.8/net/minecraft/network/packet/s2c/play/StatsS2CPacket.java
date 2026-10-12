package net.minecraft.network.packet.s2c.play;

import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;

public class StatsS2CPacket implements Packet<ClientPlayPacketHandler> {
    private Map<Stat, Integer> stats;

    public StatsS2CPacket() {
    }

    public StatsS2CPacket(Map<Stat, Integer> stats) {
        this.stats = stats;
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleStats(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        int i = buffer.readVarInt();
        this.stats = Maps.newHashMap();

        for (int j = 0; j < i; j++) {
            Stat stat = Stats.byKey(buffer.readString(32767));
            int k = buffer.readVarInt();
            if (stat != null) {
                this.stats.put(stat, k);
            }
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.stats.size());

        for (Entry<Stat, Integer> entry : this.stats.entrySet()) {
            buffer.writeString(entry.getKey().key);
            buffer.writeVarInt(entry.getValue());
        }
    }

    public Map<Stat, Integer> getStats() {
        return this.stats;
    }
}
