package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.Growth;

/**
 * The public growth-family view (§27.3's family — the VIII2/VII7/VIII8
 * exposure): the engine's own stage/age/survival consults dressed as
 * the {@link Growth} read surface. The type consults are pure (the
 * engine's own static tables); the position consults read the live
 * world through the same best-effort light reads the api's {@code
 * blockLightAt}/{@code skyLightAt} document (§27.6 — an off-lane
 * answer reflects some instant between ticks). The mutation arms (the
 * random-tick rolls, the bonemeal dispatcher's consume/grow laws) stay
 * engine-owned — this surface answers the OUTCOMES a plugin would
 * predict, never performs them.
 */
public final class GrowthViews implements Growth {

    private final net.zaminmc.torch.server.world.EngineWorld world;

    public GrowthViews(net.zaminmc.torch.server.world.EngineWorld world) {
        this.world = world;
    }

    @Override
    public int cropStage(BlockType type) {
        return WorldSolidity.cropStageOf(type.identifier());
    }

    @Override
    public int saplingVariant(BlockType type) {
        return WoodVariantBlocks.variantOfSapling(type.identifier());
    }

    @Override
    public boolean saplingReady(BlockType type) {
        return SaplingBlocks.isReady(type.identifier());
    }

    @Override
    public boolean saplingLightGatePasses(BlockPosition position) {
        // The random tick's own read (SaplingBlocks.randomTick line
        // 127): the brighter of block and sky light at the cell ABOVE.
        BlockPosition above = position.offset(0, 1, 0);
        return Math.max(world.blockLightAt(above), world.skyLightAt(above))
                >= SaplingBlocks.GROW_LIGHT;
    }

    @Override
    public int cocoaAge(BlockType type) {
        if (!CocoaBlocks.isCocoa(type)) {
            return -1;
        }
        return CocoaBlocks.ageOf(type);
    }

    @Override
    public boolean mushroomPlant(BlockType type) {
        return MushroomBlocks.isMushroomPlant(type);
    }

    @Override
    public boolean mushroomSurvivesAt(BlockPosition position) {
        return MushroomBlocks.canPlantSurviveAt(world, position);
    }
}
