package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

import java.util.Objects;

/**
 * The one solidity rule of the engine, shared by every physics query — mob
 * bodies, item stacks, falling blocks and projectiles all ask the same
 * question and get the same answer.
 *
 * <p>Non-solid: air, torches (the thin wall model) and fluids — the
 * historical Material replaces-able set: entities fall through water and
 * lava, items sink through the pool, arrows do not stick in a stream. Solid:
 * everything else (the flat-world block set is fully opaque; the fence/slab
 * nuance arrives with the collision-shape slice).</p>
 */
public final class WorldSolidity {

    private WorldSolidity() {
    }

    /** @return whether the block type blocks entity movement. Null = air. */
    public static boolean isSolid(BlockType type) {
        if (type == null) {
            return false;
        }
        Identifier id = type.identifier();
        return !id.equals(BuiltinBlocks.AIR.identifier())
                && !id.equals(BuiltinBlocks.TORCH.identifier())
                && !isFlora(id)
                && !FluidBlocks.isFluid(id);
    }

    /**
     * The walk-through flora (the historical replaceable Material set): tall
     * grass, the dead bush and the two flowers never block a body.
     */
    private static boolean isFlora(Identifier id) {
        return id.equals(BuiltinBlocks.TALL_GRASS.identifier())
                || id.equals(BuiltinBlocks.DEAD_BUSH.identifier())
                || id.equals(BuiltinBlocks.DANDELION.identifier())
                || id.equals(BuiltinBlocks.POPPY.identifier())
                || isWheatCrop(id);
    }

    /** The wheat crop stages (block 59, ages 0..7): walk-through plants. */
    public static boolean isWheatCrop(Identifier id) {
        return id.namespace().equals("minecraft")
                && id.value().startsWith("wheat_stage");
    }
}
