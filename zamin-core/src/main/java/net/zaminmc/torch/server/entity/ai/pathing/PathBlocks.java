package net.zaminmc.torch.server.entity.ai.pathing;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;

/**
 * The engine block → path-material mapping: the caller's translation of the
 * vanilla {@code Block.getMaterial()} / {@code Block.canWalkThrough} /
 * fence-rail-trapdoor-type checks into the {@link CellMaterial} categories
 * the WalkNodeEvaluator branches on.
 *
 * <p>Ported from the reference classification surface (reference/1.8.8
 * WalkNodeEvaluator.getBlockingType + Block.canWalkThrough):
 * {@code canWalkThrough} defaults to {@code !material.blocksMovement()} —
 * so fire (an AirMaterial) and the flora walk through, water walks through
 * (LiquidBlock: {@code material != LAVA}) with the swimmable flag, lava
 * blocks (-2), fences are the 1.5-tall barrier (-3), rails path around
 * unless the mob already stands on them (-3), and every other collidable
 * blocks outright (0).</p>
 */
public final class PathBlocks {

    private PathBlocks() {
    }

    /** @return the material category the path scanner sees in this cell. */
    public static CellMaterial materialOf(BlockType type) {
        String id = type.identifier().toString();
        if (id.equals("minecraft:air")) {
            return CellMaterial.AIR;
        }
        if (FluidBlocks.isWater(type.identifier())) {
            return CellMaterial.WATER;
        }
        if (FluidBlocks.isLava(type.identifier())) {
            return CellMaterial.LAVA;
        }
        if (id.equals("minecraft:oak_fence")) {
            return CellMaterial.FENCE;
        }
        if (id.equals("minecraft:rail")) {
            return CellMaterial.RAIL;
        }
        // The vanilla canWalkThrough set: non-colliding, non-fluid, non-fire
        // — the engine's flora (tall grass, dead bush, flowers) plus the
        // torch and the sugar cane column.
        if (!WorldSolidity.isSolid(type)) {
            return CellMaterial.WALK_THROUGH;
        }
        return CellMaterial.SOLID;
    }
}
