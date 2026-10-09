package net.minecraft.client.render.world;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.nio.FloatBuffer;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class RenderChunk {
    private World world;
    private final WorldRenderer renderer;
    public static int updateCounter;
    private BlockPos origin;
    public CompiledChunk compiled = CompiledChunk.UNCOMPILED;
    private final ReentrantLock taskLock = new ReentrantLock();
    private final ReentrantLock compileLock = new ReentrantLock();
    private ChunkCompileTask pendingTask = null;
    private final Set<BlockEntity> globalBlockEntities = Sets.newHashSet();
    private final int index;
    private final FloatBuffer matrix = MemoryTracker.createFloatBuffer(16);
    private final VertexBuffer[] buffers = new VertexBuffer[BlockLayer.values().length];
    public Box bounds;
    private int lastFrame = -1;
    private boolean dirty = true;
    private EnumMap<Direction, BlockPos> relativeOrigins = Maps.newEnumMap(Direction.class);

    public RenderChunk(World world, WorldRenderer renderer, BlockPos pos, int index) {
        this.world = world;
        this.renderer = renderer;
        this.index = index;
        if (!pos.equals(this.getOrigin())) {
            this.setOrigin(pos);
        }

        if (GLX.useVbo()) {
            for (int i = 0; i < BlockLayer.values().length; i++) {
                this.buffers[i] = new VertexBuffer(DefaultVertexFormat.BLOCK);
            }
        }
    }

    public boolean setFrame(int frame) {
        if (this.lastFrame == frame) {
            return false;
        }

        this.lastFrame = frame;
        return true;
    }

    public VertexBuffer getBuffer(int layer) {
        return this.buffers[layer];
    }

    public void setOrigin(BlockPos origin) {
        this.reset();
        this.origin = origin;
        this.bounds = new Box(origin, origin.add(16, 16, 16));

        for (Direction direction : Direction.values()) {
            this.relativeOrigins.put(direction, origin.offset(direction, 16));
        }

        this.glTransform();
    }

    public void rebuildTranslucent(float cameraX, float cameraY, float cameraZ, ChunkCompileTask task) {
        CompiledChunk compiledchunk = task.getCompiledChunk();
        if (compiledchunk.getTransparencyState() != null && !compiledchunk.hasBlock(BlockLayer.TRANSLUCENT)) {
            this.beginLayer(task.getBuffers().get(BlockLayer.TRANSLUCENT), this.origin);
            task.getBuffers().get(BlockLayer.TRANSLUCENT).setState(compiledchunk.getTransparencyState());
            this.endLayer(BlockLayer.TRANSLUCENT, cameraX, cameraY, cameraZ, task.getBuffers().get(BlockLayer.TRANSLUCENT), compiledchunk);
        }
    }

    public void compile(float cameraX, float cameraY, float cameraZ, ChunkCompileTask task) {
        CompiledChunk compiledchunk = new CompiledChunk();
        int i = 1;
        BlockPos blockpos = this.origin;
        BlockPos blockpos1 = blockpos.add(15, 15, 15);
        task.getLock().lock();

        WorldView worldview;
        try {
            if (task.getStatus() != ChunkCompileTask.Status.COMPILING) {
                return;
            }

            worldview = new RenderChunkRegion(this.world, blockpos.add(-1, -1, -1), blockpos1.add(1, 1, 1), 1);
            task.setCompiledChunk(compiledchunk);
        } finally {
            task.getLock().unlock();
        }

        ChunkOcclusionGraph chunkOcclusionGraph = new ChunkOcclusionGraph();
        HashSet set = Sets.newHashSet();
        if (!worldview.isEmpty()) {
            updateCounter++;
            boolean[] aboolean = new boolean[BlockLayer.values().length];
            BlockRenderDispatcher blockrenderdispatcher = Minecraft.getInstance().getBlockRenderDispatcher();

            for (BlockPos.Mutable blockpos$mutable : BlockPos.iterateRegionMutable(blockpos, blockpos1)) {
                BlockState blockstate = worldview.getBlockState(blockpos$mutable);
                Block block = blockstate.getBlock();
                if (block.isSolidRender()) {
                    chunkOcclusionGraph.close(blockpos$mutable);
                }

                if (block.hasBlockEntity()) {
                    BlockEntity blockentity = worldview.getBlockEntity(new BlockPos(blockpos$mutable));
                    BlockEntityRenderer<BlockEntity> blockentityrenderer = BlockEntityRenderDispatcher.INSTANCE.getRenderer(blockentity);
                    if (blockentity != null && blockentityrenderer != null) {
                        compiledchunk.addRenderableBlockEntity(blockentity);
                        if (blockentityrenderer.shouldRenderOffScreen()) {
                            set.add(blockentity);
                        }
                    }
                }

                BlockLayer blocklayer1 = block.getRenderLayer();
                int j = blocklayer1.ordinal();
                if (block.getRenderType() != -1) {
                    BufferBuilder bufferbuilder = task.getBuffers().get(j);
                    if (!compiledchunk.hasLayer(blocklayer1)) {
                        compiledchunk.setLayer(blocklayer1);
                        this.beginLayer(bufferbuilder, blockpos);
                    }

                    aboolean[j] |= blockrenderdispatcher.render(blockstate, blockpos$mutable, worldview, bufferbuilder);
                }
            }

            for (BlockLayer blocklayer : BlockLayer.values()) {
                if (aboolean[blocklayer.ordinal()]) {
                    compiledchunk.setBlock(blocklayer);
                }

                if (compiledchunk.hasLayer(blocklayer)) {
                    this.endLayer(blocklayer, cameraX, cameraY, cameraZ, task.getBuffers().get(blocklayer), compiledchunk);
                }
            }
        }

        compiledchunk.setOcclusionData(chunkOcclusionGraph.resolve());
        this.taskLock.lock();

        try {
            Set<BlockEntity> setx = Sets.newHashSet(set);
            Set<BlockEntity> set1 = Sets.newHashSet(this.globalBlockEntities);
            setx.removeAll(this.globalBlockEntities);
            set1.removeAll(set);
            this.globalBlockEntities.clear();
            this.globalBlockEntities.addAll(set);
            this.renderer.updateGlobalBlockEntities(set1, setx);
        } finally {
            this.taskLock.unlock();
        }
    }

    protected void cancelCompile() {
        this.taskLock.lock();

        try {
            if (this.pendingTask != null && this.pendingTask.getStatus() != ChunkCompileTask.Status.DONE) {
                this.pendingTask.cancel();
                this.pendingTask = null;
            }
        } finally {
            this.taskLock.unlock();
        }
    }

    public ReentrantLock getTaskLock() {
        return this.taskLock;
    }

    public ChunkCompileTask startCompile() {
        this.taskLock.lock();

        try {
            this.cancelCompile();
            this.pendingTask = new ChunkCompileTask(this, ChunkCompileTask.Type.REBUILD_CHUNK);
            return this.pendingTask;
        } finally {
            this.taskLock.unlock();
        }
    }

    public ChunkCompileTask startResortTransparency() {
        this.taskLock.lock();

        try {
            if (this.pendingTask != null && this.pendingTask.getStatus() == ChunkCompileTask.Status.PENDING) {
                return null;
            }

            if (this.pendingTask != null && this.pendingTask.getStatus() != ChunkCompileTask.Status.DONE) {
                this.pendingTask.cancel();
                this.pendingTask = null;
            }

            this.pendingTask = new ChunkCompileTask(this, ChunkCompileTask.Type.RESORT_TRANSPARENCY);
            this.pendingTask.setCompiledChunk(this.compiled);
            return this.pendingTask;
        } finally {
            this.taskLock.unlock();
        }
    }

    private void beginLayer(BufferBuilder bufferBuilder, BlockPos offset) {
        bufferBuilder.begin(7, DefaultVertexFormat.BLOCK);
        bufferBuilder.offset(-offset.getX(), -offset.getY(), -offset.getZ());
    }

    private void endLayer(BlockLayer layer, float cameraX, float cameraY, float cameraZ, BufferBuilder bufferBuilder, CompiledChunk compiled) {
        if (layer == BlockLayer.TRANSLUCENT && !compiled.hasBlock(layer)) {
            bufferBuilder.sortQuads(cameraX, cameraY, cameraZ);
            compiled.setTransparencyState(bufferBuilder.getState());
        }

        bufferBuilder.end();
    }

    private void glTransform() {
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        float f = 1.000001F;
        GlStateManager.translatef(-8.0F, -8.0F, -8.0F);
        GlStateManager.scalef(f, f, f);
        GlStateManager.translatef(8.0F, 8.0F, 8.0F);
        GlStateManager.getFloat(2982, this.matrix);
        GlStateManager.popMatrix();
    }

    public void glMultMatrix() {
        GlStateManager.multMatrix(this.matrix);
    }

    public CompiledChunk getCompiledChunk() {
        return this.compiled;
    }

    public void setCompiledChunk(CompiledChunk compiled) {
        this.compileLock.lock();

        try {
            this.compiled = compiled;
        } finally {
            this.compileLock.unlock();
        }
    }

    public void reset() {
        this.cancelCompile();
        this.compiled = CompiledChunk.UNCOMPILED;
    }

    public void delete() {
        this.reset();
        this.world = null;

        for (int i = 0; i < BlockLayer.values().length; i++) {
            if (this.buffers[i] != null) {
                this.buffers[i].delete();
            }
        }
    }

    public BlockPos getOrigin() {
        return this.origin;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public BlockPos getRelativeOrigin(Direction dir) {
        return this.relativeOrigins.get(dir);
    }
}
