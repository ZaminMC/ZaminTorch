package net.zamin.engine.block;

import net.zamin.api.BlockType;

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
        return !type.identifier().equals(BuiltinBlocks.TORCH.identifier())
                && !FluidBlocks.isFluid(type.identifier());
    }
}
