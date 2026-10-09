package net.minecraft.client.render.block;

import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.manager.ResourceReloadListener;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.BasicBakedModel;
import net.minecraft.client.resource.model.WeightedBakedModel;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldView;
import net.minecraft.world.gen.WorldGeneratorType;

public class BlockRenderDispatcher implements ResourceReloadListener {
    private BlockModelShaper modelShaper;
    private final GameOptions options;
    private final BlockModelRenderer modelRenderer = new BlockModelRenderer();
    private final AnimatedBlockEntityRenderer blockEntityRenderer = new AnimatedBlockEntityRenderer();
    private final LiquidRenderer liquidRenderer = new LiquidRenderer();

    public BlockRenderDispatcher(BlockModelShaper modelShaper, GameOptions options) {
        this.modelShaper = modelShaper;
        this.options = options;
    }

    public BlockModelShaper getModelShaper() {
        return this.modelShaper;
    }

    public void renderMiningProgress(BlockState state, BlockPos pos, TextureAtlasSprite sprite, WorldView world) {
        Block block = state.getBlock();
        int i = block.getRenderType();
        if (i == 3) {
            state = block.resolveVirtualProperties(state, world, pos);
            BakedModel bakedmodel = this.modelShaper.getModel(state);
            BakedModel bakedmodel1 = new BasicBakedModel.Builder(bakedmodel, sprite).build();
            this.modelRenderer.render(world, bakedmodel1, state, pos, Tesselator.getInstance().getBuffer());
        }
    }

    public boolean render(BlockState state, BlockPos pos, WorldView world, BufferBuilder bufferBuilder) {
        try {
            int i = state.getBlock().getRenderType();
            if (i == -1) {
                return false;
            }

            switch (i) {
                case 1:
                    return this.liquidRenderer.render(world, state, pos, bufferBuilder);
                case 2:
                    return false;
                case 3:
                    BakedModel bakedmodel = this.getModel(state, world, pos);
                    return this.modelRenderer.render(world, bakedmodel, state, pos, bufferBuilder);
                default:
                    return false;
            }
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Tesselating block in world");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Block being tesselated");
            CrashReportCategory.addBlockDetails(crashreportcategory, pos, state.getBlock(), state.getBlock().getMetadataFromState(state));
            throw new CrashException(crashreport);
        }
    }

    public BlockModelRenderer getModelRenderer() {
        return this.modelRenderer;
    }

    private BakedModel getModel(BlockState state, BlockPos pos) {
        BakedModel bakedmodel = this.modelShaper.getModel(state);
        if (pos != null && this.options.allowBlockAlternatives && bakedmodel instanceof WeightedBakedModel) {
            bakedmodel = ((WeightedBakedModel)bakedmodel).pick(MathHelper.hashCode(pos));
        }

        return bakedmodel;
    }

    public BakedModel getModel(BlockState state, WorldView world, BlockPos pos) {
        Block block = state.getBlock();
        if (world.getGeneratorType() != WorldGeneratorType.DEBUG_ALL_BLOCK_STATES) {
            try {
                state = block.resolveVirtualProperties(state, world, pos);
            } catch (Exception exception) {
            }
        }

        BakedModel bakedmodel = this.modelShaper.getModel(state);
        if (pos != null && this.options.allowBlockAlternatives && bakedmodel instanceof WeightedBakedModel) {
            bakedmodel = ((WeightedBakedModel)bakedmodel).pick(MathHelper.hashCode(pos));
        }

        return bakedmodel;
    }

    public void renderAsItem(BlockState state, float brightness) {
        int i = state.getBlock().getRenderType();
        if (i != -1) {
            switch (i) {
                case 1:
                default:
                    break;
                case 2:
                    this.blockEntityRenderer.render(state.getBlock(), brightness);
                    break;
                case 3:
                    BakedModel bakedmodel = this.getModel(state, null);
                    this.modelRenderer.render(bakedmodel, state, brightness, true);
            }
        }
    }

    public boolean isItem3d(Block block, int metadata) {
        if (block == null) {
            return false;
        }

        int i = block.getRenderType();
        return i != 3 && i == 2;
    }

    @Override
    public void reload(ResourceManager resourceManager) {
        this.liquidRenderer.reload();
    }
}
