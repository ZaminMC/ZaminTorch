package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The reference's MOUNT-SURVIVAL consults (F-SP1) — the laws the
 * torch/lever/button/trapdoor families read when a block change asks
 * whether they still hang, each DIFFERENT from the collision-solid
 * question {@link WorldSolidity#isSolid} the engine's four consults had
 * been answering:
 *
 * <ul>
 * <li><b>The torch WALL arm</b> — {@code World.isSolidBlockingCube} (World
 *     lines 1754-1768): {@code material.isSolidBlocking() && isCube()}.
 *     The translucent materials (LEAVES, GLASS, TNT, ICE, SNOW_LAYER,
 *     CACTUS — Material's whole {@code setTranslucent()} census) never
 *     answer, whatever their shape; the non-cubes never answer either.</li>
 * <li><b>The torch FLOOR arm</b> — {@code TorchBlock.canSitOnTop} (lines
 *     49-56): {@code World.hasSolidTop} (the five arms) OR the explicit
 *     list — FENCES, GLASS, COBBLESTONE_WALL, STAINED_GLASS. A torch
 *     stands on glass in vanilla, never on ice, TNT, leaves, cactus or
 *     the snow layer.</li>
 * <li><b>The lever/button split</b> — {@code ButtonBlock.canSurvive}
 *     (lines 70-73; {@code LeverBlock.canSurvive} line 60 delegates):
 *     {@code attachment == DOWN ? World.hasSolidTop(host) :
 *     block.isSolid()} — the floor mount rides hasSolidTop's arms, every
 *     wall/ceiling mount rides {@link BlockSolidity#isSolid} (Block.java
 *     lines 274-276, the three conjuncts).</li>
 * <li><b>The trapdoor mount</b> — {@code TrapdoorBlock.canBePlacedOn}
 *     (lines 188-190): {@code isSolidBlockingCube || GLOWSTONE ||
 *     instanceof SlabBlock || instanceof StairsBlock} — the reference's
 *     own explicit arms beside the cube law (the suffix arms live at the
 *     consult site, {@code RedstoneSystem.trapdoorMountHolds}).</li>
 * </ul>
 *
 * <p>The plain torch's own support walk (the reference's one
 * {@code TorchBlock} class carries the law for BOTH families — the
 * redstone torch extends it) reads the same {@link #canSitOnTop} floor
 * law: the engine's single standing state consults the cell below
 * through this class, never the bare "is it air" question.</p>
 */
public final class SupportConsult {

    private SupportConsult() {
    }

    /**
     * The reference's {@code World.isSolidBlockingCube} (World.java lines
     * 1754-1768) — the torch's WALL consult and the trapdoor mount's first
     * arm: {@code material.isSolidBlocking() && isCube()}.
     *
     * @return whether the block is an opaque-material full render cube —
     *         the translucent materials and every partial shape answer
     *         FALSE, whatever their collision solidity.
     */
    public static boolean isSolidBlockingCube(BlockType type) {
        if (type == null) {
            return false;
        }
        Identifier id = type.identifier();
        if (!id.namespace().equals("minecraft")) {
            return false; // an unregistered kind defaults to the law's false
        }
        // material.isSolidBlocking() = !translucent && blocksMovement().
        if (MaterialSolidity.isTranslucentMaterial(id)
                || !MaterialSolidity.blocksMovement(id)) {
            return false;
        }
        return BlockSolidity.isCube(type);
    }

    /**
     * The reference's {@code World.hasSolidTop} (World.java lines
     * 1740-1752) — the five arms: the solid-blocking cube, the TOP-half
     * stairs, the TOP-half slabs, the hopper and the seven-layer snow.
     *
     * <p>The engine's snow-layer vocabulary is the single one-layer state
     * (the seven-layer band is VT-003's named tail), so the fifth arm
     * answers FALSE for every registered snow state — exactly the
     * reference's answer for every layer count below seven.</p>
     */
    public static boolean hasSolidTop(BlockType type) {
        if (type == null) {
            return false;
        }
        Identifier id = type.identifier();
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        if (isSolidBlockingCube(type)) {
            return true; // the first arm
        }
        String value = id.value();
        // StairsBlock with HALF == TOP.
        if (WoodShapeBlocks.isStairsTopState(value)) {
            return true;
        }
        // SlabBlock with HALF == TOP (the _slab2 family's top form joins
        // the census — the reference's instanceof SlabBlock reads the
        // family whole).
        if (value.endsWith("_slab_top") || value.endsWith("_slab2_top")) {
            return true;
        }
        // HopperBlock — the fourth arm. The engine registers NO bare
        // minecraft:hopper block: the flattened state model holds the
        // hopper_<facing>[_disabled] band alone (HopperBlocks), so the
        // arm reads the band whole — the reference's instanceof
        // HopperBlock covers every state the family carries (the F-SP2
        // rider correcting the dead bare-form read: a wire, a diode, a
        // plate or a rail on a hopper floor stays, hasSolidTop's fourth
        // arm; the pre-rider arm answered every registered hopper
        // false).
        return value.startsWith("hopper_");
    }

    /**
     * The reference's {@code TorchBlock.canSitOnTop} (lines 49-56) — the
     * torch FLOOR consult: {@code hasSolidTop || instanceof FenceBlock ||
     * == GLASS || == COBBLESTONE_WALL || == STAINED_GLASS}.
     */
    public static boolean canSitOnTop(BlockType type) {
        if (hasSolidTop(type)) {
            return true;
        }
        if (type == null) {
            return false;
        }
        Identifier id = type.identifier();
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        // The explicit list: every FenceBlock family (the nether brick's
        // own included), the glass, the cobblestone wall family and the
        // stained glass family.
        return WorldSolidity.isFence(id)
                || value.equals("glass")
                || WallBlocks.isWall(id)
                || value.equals("stained_glass")
                || value.startsWith("stained_glass_");
    }

    /**
     * The reference's {@code ButtonBlock.canSurvive} (lines 70-73; the
     * lever's own consult delegates to it) — the lever/button split:
     * the FLOOR mount (the attachment direction DOWN) rides
     * {@link #hasSolidTop}; every wall/ceiling mount rides
     * {@link BlockSolidity#isSolid} (Block.java lines 274-276).
     *
     * @param attachmentDown whether the family mounts on the block BELOW
     *                       it (the reference's {@code facing == DOWN}
     *                       arm)
     */
    public static boolean buttonOrLeverSurvives(boolean attachmentDown, BlockType host) {
        return attachmentDown ? hasSolidTop(host) : BlockSolidity.isSolid(host);
    }
}
