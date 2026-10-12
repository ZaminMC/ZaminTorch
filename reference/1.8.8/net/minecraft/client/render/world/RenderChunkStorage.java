package net.minecraft.client.render.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class RenderChunkStorage {
    protected final WorldRenderer renderer;
    protected final World world;
    protected int sizeY;
    protected int sizeX;
    protected int sizeZ;
    public RenderChunk[] chunks;

    public RenderChunkStorage(World world, int viewDistance, WorldRenderer renderer, RenderChunkFactory factory) {
        this.renderer = renderer;
        this.world = world;
        this.setViewDistance(viewDistance);
        this.setFactory(factory);
    }

    protected void setFactory(RenderChunkFactory factory) {
        int i = this.sizeX * this.sizeY * this.sizeZ;
        this.chunks = new RenderChunk[i];
        int j = 0;

        for (int k = 0; k < this.sizeX; k++) {
            for (int l = 0; l < this.sizeY; l++) {
                for (int i1 = 0; i1 < this.sizeZ; i1++) {
                    int j1 = (i1 * this.sizeY + l) * this.sizeX + k;
                    BlockPos blockpos = new BlockPos(k * 16, l * 16, i1 * 16);
                    this.chunks[j1] = factory.createChunk(this.world, this.renderer, blockpos, j++);
                }
            }
        }
    }

    public void releaseBuffers() {
        for (RenderChunk renderchunk : this.chunks) {
            renderchunk.delete();
        }
    }

    protected void setViewDistance(int viewDistance) {
        int i = viewDistance * 2 + 1;
        this.sizeX = i;
        this.sizeY = 16;
        this.sizeZ = i;
    }

    public void updateCameraPos(double x, double z) {
        int i = MathHelper.floor(x) - 8;
        int j = MathHelper.floor(z) - 8;
        int k = this.sizeX * 16;

        for (int l = 0; l < this.sizeX; l++) {
            int i1 = this.coordinate(i, k, l);

            for (int j1 = 0; j1 < this.sizeZ; j1++) {
                int k1 = this.coordinate(j, k, j1);

                for (int l1 = 0; l1 < this.sizeY; l1++) {
                    int i2 = l1 * 16;
                    RenderChunk renderchunk = this.chunks[(j1 * this.sizeY + l1) * this.sizeX + l];
                    BlockPos blockpos = new BlockPos(i1, i2, k1);
                    if (!blockpos.equals(renderchunk.getOrigin())) {
                        renderchunk.setOrigin(blockpos);
                    }
                }
            }
        }
    }

    private int coordinate(int origin, int chunk, int size) {
        int i = size * 16;
        int j = i - origin + chunk / 2;
        if (j < 0) {
            j -= chunk - 1;
        }

        return i - j / chunk * chunk;
    }

    public void markDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int i = MathHelper.floorDiv(minX, 16);
        int j = MathHelper.floorDiv(minY, 16);
        int k = MathHelper.floorDiv(minZ, 16);
        int l = MathHelper.floorDiv(maxX, 16);
        int i1 = MathHelper.floorDiv(maxY, 16);
        int j1 = MathHelper.floorDiv(maxZ, 16);

        for (int k1 = i; k1 <= l; k1++) {
            int l1 = k1 % this.sizeX;
            if (l1 < 0) {
                l1 += this.sizeX;
            }

            for (int i2 = j; i2 <= i1; i2++) {
                int j2 = i2 % this.sizeY;
                if (j2 < 0) {
                    j2 += this.sizeY;
                }

                for (int k2 = k; k2 <= j1; k2++) {
                    int l2 = k2 % this.sizeZ;
                    if (l2 < 0) {
                        l2 += this.sizeZ;
                    }

                    int i3 = (l2 * this.sizeY + j2) * this.sizeX + l1;
                    RenderChunk renderchunk = this.chunks[i3];
                    renderchunk.setDirty(true);
                }
            }
        }
    }

    protected RenderChunk getChunk(BlockPos pos) {
        int i = MathHelper.floorDiv(pos.getX(), 16);
        int j = MathHelper.floorDiv(pos.getY(), 16);
        int k = MathHelper.floorDiv(pos.getZ(), 16);
        if (j >= 0 && j < this.sizeY) {
            i %= this.sizeX;
            if (i < 0) {
                i += this.sizeX;
            }

            k %= this.sizeZ;
            if (k < 0) {
                k += this.sizeZ;
            }

            int l = (k * this.sizeY + j) * this.sizeX + i;
            return this.chunks[l];
        } else {
            return null;
        }
    }
}
