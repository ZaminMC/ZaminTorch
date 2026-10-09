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
                && !id.equals(BuiltinBlocks.FIRE.identifier())
                && !isSign(id)
                && !isLadder(id)
                && !isOpenDoorHalf(id)
                && !isFlora(id)
                && !FluidBlocks.isFluid(id);
    }

    /**
     * The walk-through flora (the historical replaceable Material set): tall
     * grass, the dead bush, the two flowers, the wheat crop and the sugar
     * cane never block a body.
     */
    private static boolean isFlora(Identifier id) {
        return id.equals(BuiltinBlocks.TALL_GRASS.identifier())
                || id.equals(BuiltinBlocks.DEAD_BUSH.identifier())
                || id.equals(BuiltinBlocks.DANDELION.identifier())
                || id.equals(BuiltinBlocks.POPPY.identifier())
                || id.equals(BuiltinBlocks.SUGAR_CANE.identifier())
                || isWheatCrop(id);
    }

    /** The ladders (block 65): climbable, never block a body. */
    public static boolean isLadder(Identifier id) {
        return id.namespace().equals("minecraft")
                && (id.value().equals("ladder") || id.value().startsWith("ladder_"));
    }

    /** The open door halves: the swing opens the passage. */
    public static boolean isOpenDoorHalf(Identifier id) {
        return id.namespace().equals("minecraft")
                && (id.value().startsWith("oak_door_open")
                    || id.value().equals("oak_door_upper_open"));
    }

    /** The standing signs (block 63): the thin-post model, walk-through. */
    public static boolean isSign(Identifier id) {
        return id.namespace().equals("minecraft")
                && (id.value().equals("sign") || id.value().startsWith("sign_"));
    }

    /** The wheat crop stages (block 59, ages 0..7): walk-through plants. */
    public static boolean isWheatCrop(Identifier id) {
        return id.namespace().equals("minecraft")
                && id.value().startsWith("wheat_stage");
    }

    /** The fire block (51): walk-through, damages bodies standing in it. */
    public static boolean isFire(Identifier id) {
        return id.equals(BuiltinBlocks.FIRE.identifier());
    }

    /** The sugar cane (83): walk-through reed, grows to three on wet soil. */
    public static boolean isSugarCane(Identifier id) {
        return id.equals(BuiltinBlocks.SUGAR_CANE.identifier());
    }

    /** The cactus (81): solid, breaks beside solids, damages bodies touching it. */
    public static boolean isCactus(Identifier id) {
        return id.equals(BuiltinBlocks.CACTUS.identifier());
    }

    /** Either furnace half (61 unlit / 62 lit) — the container's two faces. */
    public static boolean isFurnaceBlock(Identifier id) {
        return id.equals(BuiltinBlocks.FURNACE.identifier())
                || id.equals(BuiltinBlocks.FURNACE_LIT.identifier());
    }
}
