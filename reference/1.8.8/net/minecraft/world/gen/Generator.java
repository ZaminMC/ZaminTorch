package net.minecraft.world.gen;

import java.util.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.chunk.ChunkSource;

public class Generator {
    protected int range = 8;
    protected Random random = new Random();
    protected World world;

    public void place(ChunkSource source, World world, int chunkX, int chunkZ, BlockStateStorage blocks) {
        int i = this.range;
        this.world = world;
        this.random.setSeed(world.getSeed());
        long j = this.random.nextLong();
        long k = this.random.nextLong();

        for (int l = chunkX - i; l <= chunkX + i; l++) {
            for (int i1 = chunkZ - i; i1 <= chunkZ + i; i1++) {
                long j1 = l * j;
                long k1 = i1 * k;
                this.random.setSeed(j1 ^ k1 ^ world.getSeed());
                this.place(world, l, i1, chunkX, chunkZ, blocks);
            }
        }
    }

    protected void place(World world, int startChunkX, int startChunkZ, int chunkX, int chunkZ, BlockStateStorage blocks) {
    }
}
