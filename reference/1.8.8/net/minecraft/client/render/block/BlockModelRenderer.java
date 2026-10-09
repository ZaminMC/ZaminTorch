package net.minecraft.client.render.block;

import java.util.BitSet;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.BakedQuad;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.WorldView;

public class BlockModelRenderer {
    public boolean render(WorldView world, BakedModel model, BlockState state, BlockPos pos, BufferBuilder bufferBuilder) {
        Block block = state.getBlock();
        block.updateShape(world, pos);
        return this.render(world, model, state, pos, bufferBuilder, true);
    }

    public boolean render(WorldView world, BakedModel model, BlockState state, BlockPos pos, BufferBuilder bufferBuilder, boolean checkShouldRenderFace) {
        boolean flag = Minecraft.isAmbientOcclusionEnabled() && state.getBlock().getLight() == 0 && model.useAmbientOcclusion();

        try {
            Block block = state.getBlock();
            return flag
                ? this.tesselateWithAmbientOcclusion(world, model, block, pos, bufferBuilder, checkShouldRenderFace)
                : this.tesselateWithoutAmbientOcclusion(world, model, block, pos, bufferBuilder, checkShouldRenderFace);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Tesselating block model");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Block model being tesselated");
            CrashReportCategory.addBlockDetails(crashreportcategory, pos, state);
            crashreportcategory.add("Using AO", flag);
            throw new CrashException(crashreport);
        }
    }

    public boolean tesselateWithAmbientOcclusion(
        WorldView world, BakedModel model, Block block, BlockPos pos, BufferBuilder bufferBuilder, boolean checkShouldRenderFace
    ) {
        boolean flag = false;
        float[] afloat = new float[Direction.values().length * 2];
        BitSet bitset = new BitSet(3);
        BlockModelRenderer.AmbientOcclusionFace blockmodelrenderer$ambientocclusionface = new BlockModelRenderer.AmbientOcclusionFace();

        for (Direction direction : Direction.values()) {
            List<BakedQuad> list = model.getQuads(direction);
            if (!list.isEmpty()) {
                BlockPos blockpos = pos.offset(direction);
                if (!checkShouldRenderFace || block.shouldRenderFace(world, blockpos, direction)) {
                    this.tesselateFaceWithAmbientOcclusion(world, block, pos, bufferBuilder, list, afloat, bitset, blockmodelrenderer$ambientocclusionface);
                    flag = true;
                }
            }
        }

        List<BakedQuad> list1 = model.getQuads();
        if (list1.size() > 0) {
            this.tesselateFaceWithAmbientOcclusion(world, block, pos, bufferBuilder, list1, afloat, bitset, blockmodelrenderer$ambientocclusionface);
            flag = true;
        }

        return flag;
    }

    public boolean tesselateWithoutAmbientOcclusion(
        WorldView world, BakedModel model, Block block, BlockPos pos, BufferBuilder bufferBuilder, boolean checkShouldRenderFace
    ) {
        boolean flag = false;
        BitSet bitset = new BitSet(3);

        for (Direction direction : Direction.values()) {
            List<BakedQuad> list = model.getQuads(direction);
            if (!list.isEmpty()) {
                BlockPos blockpos = pos.offset(direction);
                if (!checkShouldRenderFace || block.shouldRenderFace(world, blockpos, direction)) {
                    int i = block.getLightColor(world, blockpos);
                    this.tesselateFaceWithoutAmbientOcclusion(world, block, pos, direction, i, false, bufferBuilder, list, bitset);
                    flag = true;
                }
            }
        }

        List<BakedQuad> list1 = model.getQuads();
        if (list1.size() > 0) {
            this.tesselateFaceWithoutAmbientOcclusion(world, block, pos, null, -1, true, bufferBuilder, list1, bitset);
            flag = true;
        }

        return flag;
    }

    private void tesselateFaceWithAmbientOcclusion(
        WorldView world,
        Block block,
        BlockPos pos,
        BufferBuilder bufferBuilder,
        List<BakedQuad> quads,
        float[] faceShape,
        BitSet shapeState,
        BlockModelRenderer.AmbientOcclusionFace face
    ) {
        double d0 = pos.getX();
        double d1 = pos.getY();
        double d2 = pos.getZ();
        Block.OffsetType block$offsettype = block.getOffsetType();
        if (block$offsettype != Block.OffsetType.NONE) {
            long i = MathHelper.hashCode(pos);
            d0 += ((float)(i >> 16 & 15L) / 15.0F - 0.5) * 0.5;
            d2 += ((float)(i >> 24 & 15L) / 15.0F - 0.5) * 0.5;
            if (block$offsettype == Block.OffsetType.XYZ) {
                d1 += ((float)(i >> 20 & 15L) / 15.0F - 1.0) * 0.2;
            }
        }

        for (BakedQuad bakedquad : quads) {
            this.computeFaceShape(block, bakedquad.getVertices(), bakedquad.getFace(), faceShape, shapeState);
            face.compute(world, block, pos, bakedquad.getFace(), faceShape, shapeState);
            bufferBuilder.vertices(bakedquad.getVertices());
            bufferBuilder.lightColor(face.lightMap[0], face.lightMap[1], face.lightMap[2], face.lightMap[3]);
            if (bakedquad.hasTint()) {
                int j = block.getColor(world, pos, bakedquad.getTintIndex());
                if (GameRenderer.anaglyphEnabled) {
                    j = TextureUtil.getAnaglyphColor(j);
                }

                float f = (j >> 16 & 0xFF) / 255.0F;
                float f1 = (j >> 8 & 0xFF) / 255.0F;
                float f2 = (j & 0xFF) / 255.0F;
                bufferBuilder.multiplyColor(face.brightness[0] * f, face.brightness[0] * f1, face.brightness[0] * f2, 4);
                bufferBuilder.multiplyColor(face.brightness[1] * f, face.brightness[1] * f1, face.brightness[1] * f2, 3);
                bufferBuilder.multiplyColor(face.brightness[2] * f, face.brightness[2] * f1, face.brightness[2] * f2, 2);
                bufferBuilder.multiplyColor(face.brightness[3] * f, face.brightness[3] * f1, face.brightness[3] * f2, 1);
            } else {
                bufferBuilder.multiplyColor(face.brightness[0], face.brightness[0], face.brightness[0], 4);
                bufferBuilder.multiplyColor(face.brightness[1], face.brightness[1], face.brightness[1], 3);
                bufferBuilder.multiplyColor(face.brightness[2], face.brightness[2], face.brightness[2], 2);
                bufferBuilder.multiplyColor(face.brightness[3], face.brightness[3], face.brightness[3], 1);
            }

            bufferBuilder.postPosition(d0, d1, d2);
        }
    }

    private void computeFaceShape(Block block, int[] vertices, Direction face, float[] faceShape, BitSet shapeState) {
        float f = 32.0F;
        float f1 = 32.0F;
        float f2 = 32.0F;
        float f3 = -32.0F;
        float f4 = -32.0F;
        float f5 = -32.0F;

        for (int i = 0; i < 4; i++) {
            float f6 = Float.intBitsToFloat(vertices[i * 7]);
            float f7 = Float.intBitsToFloat(vertices[i * 7 + 1]);
            float f8 = Float.intBitsToFloat(vertices[i * 7 + 2]);
            f = Math.min(f, f6);
            f1 = Math.min(f1, f7);
            f2 = Math.min(f2, f8);
            f3 = Math.max(f3, f6);
            f4 = Math.max(f4, f7);
            f5 = Math.max(f5, f8);
        }

        if (faceShape != null) {
            faceShape[Direction.WEST.getId()] = f;
            faceShape[Direction.EAST.getId()] = f3;
            faceShape[Direction.DOWN.getId()] = f1;
            faceShape[Direction.UP.getId()] = f4;
            faceShape[Direction.NORTH.getId()] = f2;
            faceShape[Direction.SOUTH.getId()] = f5;
            faceShape[Direction.WEST.getId() + Direction.values().length] = 1.0F - f;
            faceShape[Direction.EAST.getId() + Direction.values().length] = 1.0F - f3;
            faceShape[Direction.DOWN.getId() + Direction.values().length] = 1.0F - f1;
            faceShape[Direction.UP.getId() + Direction.values().length] = 1.0F - f4;
            faceShape[Direction.NORTH.getId() + Direction.values().length] = 1.0F - f2;
            faceShape[Direction.SOUTH.getId() + Direction.values().length] = 1.0F - f5;
        }

        float f9 = 1.0E-4F;
        float f10 = 0.9999F;
        switch (face) {
            case DOWN:
                shapeState.set(1, f >= 1.0E-4F || f2 >= 1.0E-4F || f3 <= 0.9999F || f5 <= 0.9999F);
                shapeState.set(0, (f1 < 1.0E-4F || block.isCube()) && f1 == f4);
                break;
            case UP:
                shapeState.set(1, f >= 1.0E-4F || f2 >= 1.0E-4F || f3 <= 0.9999F || f5 <= 0.9999F);
                shapeState.set(0, (f4 > 0.9999F || block.isCube()) && f1 == f4);
                break;
            case NORTH:
                shapeState.set(1, f >= 1.0E-4F || f1 >= 1.0E-4F || f3 <= 0.9999F || f4 <= 0.9999F);
                shapeState.set(0, (f2 < 1.0E-4F || block.isCube()) && f2 == f5);
                break;
            case SOUTH:
                shapeState.set(1, f >= 1.0E-4F || f1 >= 1.0E-4F || f3 <= 0.9999F || f4 <= 0.9999F);
                shapeState.set(0, (f5 > 0.9999F || block.isCube()) && f2 == f5);
                break;
            case WEST:
                shapeState.set(1, f1 >= 1.0E-4F || f2 >= 1.0E-4F || f4 <= 0.9999F || f5 <= 0.9999F);
                shapeState.set(0, (f < 1.0E-4F || block.isCube()) && f == f3);
                break;
            case EAST:
                shapeState.set(1, f1 >= 1.0E-4F || f2 >= 1.0E-4F || f4 <= 0.9999F || f5 <= 0.9999F);
                shapeState.set(0, (f3 > 0.9999F || block.isCube()) && f == f3);
        }
    }

    private void tesselateFaceWithoutAmbientOcclusion(
        WorldView world,
        Block block,
        BlockPos pos,
        Direction face,
        int lightColor,
        boolean computeShape,
        BufferBuilder bufferBuilder,
        List<BakedQuad> quads,
        BitSet shapeState
    ) {
        double d0 = pos.getX();
        double d1 = pos.getY();
        double d2 = pos.getZ();
        Block.OffsetType block$offsettype = block.getOffsetType();
        if (block$offsettype != Block.OffsetType.NONE) {
            int i = pos.getX();
            int j = pos.getZ();
            long k = i * 3129871 ^ j * 116129781L;
            k = k * k * 42317861L + k * 11L;
            d0 += ((float)(k >> 16 & 15L) / 15.0F - 0.5) * 0.5;
            d2 += ((float)(k >> 24 & 15L) / 15.0F - 0.5) * 0.5;
            if (block$offsettype == Block.OffsetType.XYZ) {
                d1 += ((float)(k >> 20 & 15L) / 15.0F - 1.0) * 0.2;
            }
        }

        for (BakedQuad bakedquad : quads) {
            if (computeShape) {
                this.computeFaceShape(block, bakedquad.getVertices(), bakedquad.getFace(), null, shapeState);
                lightColor = shapeState.get(0) ? block.getLightColor(world, pos.offset(bakedquad.getFace())) : block.getLightColor(world, pos);
            }

            bufferBuilder.vertices(bakedquad.getVertices());
            bufferBuilder.lightColor(lightColor, lightColor, lightColor, lightColor);
            if (bakedquad.hasTint()) {
                int l = block.getColor(world, pos, bakedquad.getTintIndex());
                if (GameRenderer.anaglyphEnabled) {
                    l = TextureUtil.getAnaglyphColor(l);
                }

                float f = (l >> 16 & 0xFF) / 255.0F;
                float f1 = (l >> 8 & 0xFF) / 255.0F;
                float f2 = (l & 0xFF) / 255.0F;
                bufferBuilder.multiplyColor(f, f1, f2, 4);
                bufferBuilder.multiplyColor(f, f1, f2, 3);
                bufferBuilder.multiplyColor(f, f1, f2, 2);
                bufferBuilder.multiplyColor(f, f1, f2, 1);
            }

            bufferBuilder.postPosition(d0, d1, d2);
        }
    }

    public void render(BakedModel model, float brightness, float r, float g, float b) {
        for (Direction direction : Direction.values()) {
            this.renderQuads(brightness, r, g, b, model.getQuads(direction));
        }

        this.renderQuads(brightness, r, g, b, model.getQuads());
    }

    public void render(BakedModel model, BlockState state, float brightness, boolean skipColor) {
        Block block = state.getBlock();
        block.resetShape();
        GlStateManager.rotatef(90.0F, 0.0F, 1.0F, 0.0F);
        int i = block.getColor(block.getStateForRendering(state));
        if (GameRenderer.anaglyphEnabled) {
            i = TextureUtil.getAnaglyphColor(i);
        }

        float f = (i >> 16 & 0xFF) / 255.0F;
        float f1 = (i >> 8 & 0xFF) / 255.0F;
        float f2 = (i & 0xFF) / 255.0F;
        if (!skipColor) {
            GlStateManager.color4f(brightness, brightness, brightness, 1.0F);
        }

        this.render(model, brightness, f, f1, f2);
    }

    private void renderQuads(float brightness, float r, float g, float b, List<BakedQuad> quads) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();

        for (BakedQuad bakedquad : quads) {
            bufferbuilder.begin(7, DefaultVertexFormat.BLOCK_NORMALS);
            bufferbuilder.vertices(bakedquad.getVertices());
            if (bakedquad.hasTint()) {
                bufferbuilder.setQuadColor(r * brightness, g * brightness, b * brightness);
            } else {
                bufferbuilder.setQuadColor(brightness, brightness, brightness);
            }

            Vec3i vec3i = bakedquad.getFace().getNormal();
            bufferbuilder.postNormal(vec3i.getX(), vec3i.getY(), vec3i.getZ());
            tesselator.end();
        }
    }

    public enum AdjacencyData {
        DOWN(
            new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH},
            0.5F,
            false,
            new BlockModelRenderer.SizeData[0],
            new BlockModelRenderer.SizeData[0],
            new BlockModelRenderer.SizeData[0],
            new BlockModelRenderer.SizeData[0]
        ),
        UP(
            new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH},
            1.0F,
            false,
            new BlockModelRenderer.SizeData[0],
            new BlockModelRenderer.SizeData[0],
            new BlockModelRenderer.SizeData[0],
            new BlockModelRenderer.SizeData[0]
        ),
        NORTH(
            new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST},
            0.8F,
            true,
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.FLIP_WEST,
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.WEST,
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.WEST,
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.FLIP_WEST
            },
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.FLIP_EAST,
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.EAST,
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.EAST,
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.FLIP_EAST
            },
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.FLIP_EAST,
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.EAST,
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.EAST,
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.FLIP_EAST
            },
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.FLIP_WEST,
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.WEST,
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.WEST,
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.FLIP_WEST
            }
        ),
        SOUTH(
            new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP},
            0.8F,
            true,
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.FLIP_WEST,
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.FLIP_WEST,
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.WEST,
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.WEST
            },
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.FLIP_WEST,
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.FLIP_WEST,
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.WEST,
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.WEST
            },
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.FLIP_EAST,
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.FLIP_EAST,
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.EAST,
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.EAST
            },
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.FLIP_EAST,
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.FLIP_EAST,
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.EAST,
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.EAST
            }
        ),
        WEST(
            new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH},
            0.6F,
            true,
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.SOUTH,
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.FLIP_SOUTH,
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.FLIP_SOUTH,
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.SOUTH
            },
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.NORTH,
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.FLIP_NORTH,
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.FLIP_NORTH,
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.NORTH
            },
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.NORTH,
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.FLIP_NORTH,
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.FLIP_NORTH,
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.NORTH
            },
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.SOUTH,
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.FLIP_SOUTH,
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.FLIP_SOUTH,
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.SOUTH
            }
        ),
        EAST(
            new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH},
            0.6F,
            true,
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.SOUTH,
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.FLIP_SOUTH,
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.FLIP_SOUTH,
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.SOUTH
            },
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.NORTH,
                BlockModelRenderer.SizeData.FLIP_DOWN,
                BlockModelRenderer.SizeData.FLIP_NORTH,
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.FLIP_NORTH,
                BlockModelRenderer.SizeData.DOWN,
                BlockModelRenderer.SizeData.NORTH
            },
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.NORTH,
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.FLIP_NORTH,
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.FLIP_NORTH,
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.NORTH
            },
            new BlockModelRenderer.SizeData[]{
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.SOUTH,
                BlockModelRenderer.SizeData.FLIP_UP,
                BlockModelRenderer.SizeData.FLIP_SOUTH,
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.FLIP_SOUTH,
                BlockModelRenderer.SizeData.UP,
                BlockModelRenderer.SizeData.SOUTH
            }
        );

        protected final Direction[] corners;
        protected final float weight;
        protected final boolean nonCubicWeight;
        protected final BlockModelRenderer.SizeData[] vertex1Weights;
        protected final BlockModelRenderer.SizeData[] vertex2Weights;
        protected final BlockModelRenderer.SizeData[] vertex3Weights;
        protected final BlockModelRenderer.SizeData[] vertex4Weights;
        private static final BlockModelRenderer.AdjacencyData[] ALL = new BlockModelRenderer.AdjacencyData[6];

        AdjacencyData(
            Direction[] corners,
            float weight,
            boolean nonCubicWeight,
            BlockModelRenderer.SizeData[] vertex1Weights,
            BlockModelRenderer.SizeData[] vertex2Weights,
            BlockModelRenderer.SizeData[] vertex3Weights,
            BlockModelRenderer.SizeData[] vertex4Weights
        ) {
            this.corners = corners;
            this.weight = weight;
            this.nonCubicWeight = nonCubicWeight;
            this.vertex1Weights = vertex1Weights;
            this.vertex2Weights = vertex2Weights;
            this.vertex3Weights = vertex3Weights;
            this.vertex4Weights = vertex4Weights;
        }

        public static BlockModelRenderer.AdjacencyData byDirection(Direction dir) {
            return ALL[dir.getId()];
        }

        static {
            ALL[Direction.DOWN.getId()] = DOWN;
            ALL[Direction.UP.getId()] = UP;
            ALL[Direction.NORTH.getId()] = NORTH;
            ALL[Direction.SOUTH.getId()] = SOUTH;
            ALL[Direction.WEST.getId()] = WEST;
            ALL[Direction.EAST.getId()] = EAST;
        }
    }

    class AmbientOcclusionFace {
        private final float[] brightness = new float[4];
        private final int[] lightMap = new int[4];

        public AmbientOcclusionFace() {
        }

        public void compute(WorldView world, Block block, BlockPos pos, Direction face, float[] faceShape, BitSet shapeState) {
            BlockPos blockpos = shapeState.get(0) ? pos.offset(face) : pos;
            BlockModelRenderer.AdjacencyData blockmodelrenderer$adjacencydata = BlockModelRenderer.AdjacencyData.byDirection(face);
            BlockPos blockpos1 = blockpos.offset(blockmodelrenderer$adjacencydata.corners[0]);
            BlockPos blockpos2 = blockpos.offset(blockmodelrenderer$adjacencydata.corners[1]);
            BlockPos blockpos3 = blockpos.offset(blockmodelrenderer$adjacencydata.corners[2]);
            BlockPos blockpos4 = blockpos.offset(blockmodelrenderer$adjacencydata.corners[3]);
            int i = block.getLightColor(world, blockpos1);
            int j = block.getLightColor(world, blockpos2);
            int k = block.getLightColor(world, blockpos3);
            int l = block.getLightColor(world, blockpos4);
            float f = world.getBlockState(blockpos1).getBlock().getAmbientOcclusionLight();
            float f1 = world.getBlockState(blockpos2).getBlock().getAmbientOcclusionLight();
            float f2 = world.getBlockState(blockpos3).getBlock().getAmbientOcclusionLight();
            float f3 = world.getBlockState(blockpos4).getBlock().getAmbientOcclusionLight();
            boolean flag = world.getBlockState(blockpos1.offset(face)).getBlock().isTranslucent();
            boolean flag1 = world.getBlockState(blockpos2.offset(face)).getBlock().isTranslucent();
            boolean flag2 = world.getBlockState(blockpos3.offset(face)).getBlock().isTranslucent();
            boolean flag3 = world.getBlockState(blockpos4.offset(face)).getBlock().isTranslucent();
            float f4;
            int i1;
            if (!flag2 && !flag) {
                f4 = f;
                i1 = i;
            } else {
                BlockPos blockpos5 = blockpos1.offset(blockmodelrenderer$adjacencydata.corners[2]);
                f4 = world.getBlockState(blockpos5).getBlock().getAmbientOcclusionLight();
                i1 = block.getLightColor(world, blockpos5);
            }

            float f5;
            int j1;
            if (!flag3 && !flag) {
                f5 = f;
                j1 = i;
            } else {
                BlockPos blockpos6 = blockpos1.offset(blockmodelrenderer$adjacencydata.corners[3]);
                f5 = world.getBlockState(blockpos6).getBlock().getAmbientOcclusionLight();
                j1 = block.getLightColor(world, blockpos6);
            }

            float f6;
            int k1;
            if (!flag2 && !flag1) {
                f6 = f1;
                k1 = j;
            } else {
                BlockPos blockpos7 = blockpos2.offset(blockmodelrenderer$adjacencydata.corners[2]);
                f6 = world.getBlockState(blockpos7).getBlock().getAmbientOcclusionLight();
                k1 = block.getLightColor(world, blockpos7);
            }

            float f7;
            int l1;
            if (!flag3 && !flag1) {
                f7 = f1;
                l1 = j;
            } else {
                BlockPos blockpos8 = blockpos2.offset(blockmodelrenderer$adjacencydata.corners[3]);
                f7 = world.getBlockState(blockpos8).getBlock().getAmbientOcclusionLight();
                l1 = block.getLightColor(world, blockpos8);
            }

            int i3 = block.getLightColor(world, pos);
            if (shapeState.get(0) || !world.getBlockState(pos.offset(face)).getBlock().isSolidRender()) {
                i3 = block.getLightColor(world, pos.offset(face));
            }

            float f8 = shapeState.get(0)
                ? world.getBlockState(blockpos).getBlock().getAmbientOcclusionLight()
                : world.getBlockState(pos).getBlock().getAmbientOcclusionLight();
            BlockModelRenderer.AmbientVertexRemap blockmodelrenderer$ambientvertexremap = BlockModelRenderer.AmbientVertexRemap.byDirection(face);
            if (shapeState.get(1) && blockmodelrenderer$adjacencydata.nonCubicWeight) {
                float f29 = (f3 + f + f5 + f8) * 0.25F;
                float f30 = (f2 + f + f4 + f8) * 0.25F;
                float f31 = (f2 + f1 + f6 + f8) * 0.25F;
                float f32 = (f3 + f1 + f7 + f8) * 0.25F;
                float f13 = faceShape[blockmodelrenderer$adjacencydata.vertex1Weights[0].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex1Weights[1].shape];
                float f14 = faceShape[blockmodelrenderer$adjacencydata.vertex1Weights[2].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex1Weights[3].shape];
                float f15 = faceShape[blockmodelrenderer$adjacencydata.vertex1Weights[4].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex1Weights[5].shape];
                float f16 = faceShape[blockmodelrenderer$adjacencydata.vertex1Weights[6].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex1Weights[7].shape];
                float f17 = faceShape[blockmodelrenderer$adjacencydata.vertex2Weights[0].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex2Weights[1].shape];
                float f18 = faceShape[blockmodelrenderer$adjacencydata.vertex2Weights[2].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex2Weights[3].shape];
                float f19 = faceShape[blockmodelrenderer$adjacencydata.vertex2Weights[4].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex2Weights[5].shape];
                float f20 = faceShape[blockmodelrenderer$adjacencydata.vertex2Weights[6].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex2Weights[7].shape];
                float f21 = faceShape[blockmodelrenderer$adjacencydata.vertex3Weights[0].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex3Weights[1].shape];
                float f22 = faceShape[blockmodelrenderer$adjacencydata.vertex3Weights[2].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex3Weights[3].shape];
                float f23 = faceShape[blockmodelrenderer$adjacencydata.vertex3Weights[4].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex3Weights[5].shape];
                float f24 = faceShape[blockmodelrenderer$adjacencydata.vertex3Weights[6].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex3Weights[7].shape];
                float f25 = faceShape[blockmodelrenderer$adjacencydata.vertex4Weights[0].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex4Weights[1].shape];
                float f26 = faceShape[blockmodelrenderer$adjacencydata.vertex4Weights[2].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex4Weights[3].shape];
                float f27 = faceShape[blockmodelrenderer$adjacencydata.vertex4Weights[4].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex4Weights[5].shape];
                float f28 = faceShape[blockmodelrenderer$adjacencydata.vertex4Weights[6].shape]
                    * faceShape[blockmodelrenderer$adjacencydata.vertex4Weights[7].shape];
                this.brightness[blockmodelrenderer$ambientvertexremap.vertex1] = f29 * f13 + f30 * f14 + f31 * f15 + f32 * f16;
                this.brightness[blockmodelrenderer$ambientvertexremap.vertex2] = f29 * f17 + f30 * f18 + f31 * f19 + f32 * f20;
                this.brightness[blockmodelrenderer$ambientvertexremap.vertex3] = f29 * f21 + f30 * f22 + f31 * f23 + f32 * f24;
                this.brightness[blockmodelrenderer$ambientvertexremap.vertex4] = f29 * f25 + f30 * f26 + f31 * f27 + f32 * f28;
                int i2 = this.blend(l, i, j1, i3);
                int j2 = this.blend(k, i, i1, i3);
                int k2 = this.blend(k, j, k1, i3);
                int l2 = this.blend(l, j, l1, i3);
                this.lightMap[blockmodelrenderer$ambientvertexremap.vertex1] = this.blend(i2, j2, k2, l2, f13, f14, f15, f16);
                this.lightMap[blockmodelrenderer$ambientvertexremap.vertex2] = this.blend(i2, j2, k2, l2, f17, f18, f19, f20);
                this.lightMap[blockmodelrenderer$ambientvertexremap.vertex3] = this.blend(i2, j2, k2, l2, f21, f22, f23, f24);
                this.lightMap[blockmodelrenderer$ambientvertexremap.vertex4] = this.blend(i2, j2, k2, l2, f25, f26, f27, f28);
            } else {
                float f9 = (f3 + f + f5 + f8) * 0.25F;
                float f10 = (f2 + f + f4 + f8) * 0.25F;
                float f11 = (f2 + f1 + f6 + f8) * 0.25F;
                float f12 = (f3 + f1 + f7 + f8) * 0.25F;
                this.lightMap[blockmodelrenderer$ambientvertexremap.vertex1] = this.blend(l, i, j1, i3);
                this.lightMap[blockmodelrenderer$ambientvertexremap.vertex2] = this.blend(k, i, i1, i3);
                this.lightMap[blockmodelrenderer$ambientvertexremap.vertex3] = this.blend(k, j, k1, i3);
                this.lightMap[blockmodelrenderer$ambientvertexremap.vertex4] = this.blend(l, j, l1, i3);
                this.brightness[blockmodelrenderer$ambientvertexremap.vertex1] = f9;
                this.brightness[blockmodelrenderer$ambientvertexremap.vertex2] = f10;
                this.brightness[blockmodelrenderer$ambientvertexremap.vertex3] = f11;
                this.brightness[blockmodelrenderer$ambientvertexremap.vertex4] = f12;
            }
        }

        private int blend(int i, int j, int k, int l) {
            if (i == 0) {
                i = l;
            }

            if (j == 0) {
                j = l;
            }

            if (k == 0) {
                k = l;
            }

            return i + j + k + l >> 2 & 16711935;
        }

        private int blend(int i, int j, int k, int l, float f, float g, float h, float m) {
            int ix = (int)((i >> 16 & 0xFF) * f + (j >> 16 & 0xFF) * g + (k >> 16 & 0xFF) * h + (l >> 16 & 0xFF) * m) & 0xFF;
            int jx = (int)((i & 0xFF) * f + (j & 0xFF) * g + (k & 0xFF) * h + (l & 0xFF) * m) & 0xFF;
            return ix << 16 | jx;
        }
    }

    enum AmbientVertexRemap {
        DOWN(0, 1, 2, 3),
        UP(2, 3, 0, 1),
        NORTH(3, 0, 1, 2),
        SOUTH(0, 1, 2, 3),
        WEST(3, 0, 1, 2),
        EAST(1, 2, 3, 0);

        private final int vertex1;
        private final int vertex2;
        private final int vertex3;
        private final int vertex4;
        private static final BlockModelRenderer.AmbientVertexRemap[] ALL = new BlockModelRenderer.AmbientVertexRemap[6];

        AmbientVertexRemap(int vertex1, int vertex2, int vertex3, int vertex4) {
            this.vertex1 = vertex1;
            this.vertex2 = vertex2;
            this.vertex3 = vertex3;
            this.vertex4 = vertex4;
        }

        public static BlockModelRenderer.AmbientVertexRemap byDirection(Direction dir) {
            return ALL[dir.getId()];
        }

        static {
            ALL[Direction.DOWN.getId()] = DOWN;
            ALL[Direction.UP.getId()] = UP;
            ALL[Direction.NORTH.getId()] = NORTH;
            ALL[Direction.SOUTH.getId()] = SOUTH;
            ALL[Direction.WEST.getId()] = WEST;
            ALL[Direction.EAST.getId()] = EAST;
        }
    }

    public enum SizeData {
        DOWN(Direction.DOWN, false),
        UP(Direction.UP, false),
        NORTH(Direction.NORTH, false),
        SOUTH(Direction.SOUTH, false),
        WEST(Direction.WEST, false),
        EAST(Direction.EAST, false),
        FLIP_DOWN(Direction.DOWN, true),
        FLIP_UP(Direction.UP, true),
        FLIP_NORTH(Direction.NORTH, true),
        FLIP_SOUTH(Direction.SOUTH, true),
        FLIP_WEST(Direction.WEST, true),
        FLIP_EAST(Direction.EAST, true);

        protected final int shape;

        SizeData(Direction dir, boolean flip) {
            this.shape = dir.getId() + (flip ? Direction.values().length : 0);
        }
    }
}
