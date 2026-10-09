package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

public class BlocksUpdateS2CPacket implements Packet<ClientPlayPacketHandler> {
    private ChunkPos chunkPos;
    private BlocksUpdateS2CPacket.BlockUpdate[] updates;

    public BlocksUpdateS2CPacket() {
    }

    public BlocksUpdateS2CPacket(int blockChangeCount, short[] positions, WorldChunk chunk) {
        this.chunkPos = new ChunkPos(chunk.chunkX, chunk.chunkZ);
        this.updates = new BlocksUpdateS2CPacket.BlockUpdate[blockChangeCount];

        for (int i = 0; i < this.updates.length; i++) {
            this.updates[i] = new BlocksUpdateS2CPacket.BlockUpdate(positions[i], chunk);
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.chunkPos = new ChunkPos(buffer.readInt(), buffer.readInt());
        this.updates = new BlocksUpdateS2CPacket.BlockUpdate[buffer.readVarInt()];

        for (int i = 0; i < this.updates.length; i++) {
            this.updates[i] = new BlocksUpdateS2CPacket.BlockUpdate(buffer.readShort(), Block.STATE_REGISTRY.get(buffer.readVarInt()));
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeInt(this.chunkPos.x);
        buffer.writeInt(this.chunkPos.z);
        buffer.writeVarInt(this.updates.length);

        for (BlocksUpdateS2CPacket.BlockUpdate blocksupdates2cpacket$blockupdate : this.updates) {
            buffer.writeShort(blocksupdates2cpacket$blockupdate.getPosition());
            buffer.writeVarInt(Block.STATE_REGISTRY.getId(blocksupdates2cpacket$blockupdate.getBlockState()));
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleBlocksUpdate(this);
    }

    public BlocksUpdateS2CPacket.BlockUpdate[] getUpdates() {
        return this.updates;
    }

    public class BlockUpdate {
        private final short position;
        private final BlockState state;

        public BlockUpdate(short position, BlockState state) {
            this.position = position;
            this.state = state;
        }

        public BlockUpdate(short position, WorldChunk chunk) {
            this.position = position;
            this.state = chunk.getBlockState(this.getBlockPos());
        }

        public BlockPos getBlockPos() {
            return new BlockPos(BlocksUpdateS2CPacket.this.chunkPos.getBlockPos(this.position >> 12 & 15, this.position & 255, this.position >> 8 & 15));
        }

        public short getPosition() {
            return this.position;
        }

        public BlockState getBlockState() {
            return this.state;
        }
    }
}
