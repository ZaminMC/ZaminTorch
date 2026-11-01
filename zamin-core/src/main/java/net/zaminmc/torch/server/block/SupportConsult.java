package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The reference's ATTACHMENT-SUPPORT consults (the support-consult family,
 * F-SP1): the laws vanilla answers when a mount must hold a torch, a
 * lever, a button or a trapdoor — each consult DIFFERENT, none of them the
 * collision question {@link WorldSolidity#isSolid} answers.
 *
 * <p>The four laws (reference/1.8.8):</p>
 * <ul>
 *   <li>{@code World.isSolidBlockingCube} (World.java lines 1753-1768) =
 *       {@code material.isSolidBlocking() && isCube()} — the torch's WALL
 *       consult ({@code TorchBlock.canAttach} line 68, the horizontal arm;
 *       the same law {@code tryBreak} lines 106-113 re-reads on the
 *       survival walk). The translucent materials never answer; the
 *       non-cubes never answer.</li>
 *   <li>{@code World.hasSolidTop} (World.java lines 1740-1752) — the five
 *       arms: the solid-blocking cubes (the doubles among them), the TOP
 *       halves of the stairs and slabs, the hoppers, the 7-layer snow.
 *       The torch's FLOOR consult extends it
 *       ({@code TorchBlock.canSitOnTop} lines 49-55) with the explicit
 *       {@code FenceBlock || GLASS || COBBLESTONE_WALL || STAINED_GLASS}
 *       list; the lever/button FLOOR mount (the DOWN arm of
 *       {@code ButtonBlock.canSurvive} lines 70-73, to which
 *       {@code LeverBlock.canSurvive} line 60 delegates) reads it alone.</li>
 *   <li>{@code Block.isSolid()} (Block.java lines 274-276) =
 *       {@code material.isSolidBlocking() && isCube() && !isSignalSource()}
 *       — the lever/button consult for every side EXCEPT the floor (the
 *       walls AND the ceiling: {@code ButtonBlock.canSurvive}'s not-DOWN
 *       arm). The engine's landed form is {@link BlockSolidity#isSolid}.</li>
 *   <li>{@code TrapdoorBlock.canBePlacedOn} (lines 188-190) =
 *       {@code material.isSolidBlocking() && isCube() || == GLOWSTONE ||
 *       instanceof SlabBlock || instanceof StairsBlock} — ANY slab half,
 *       ANY stair state (no HALF condition), and no hopper arm.</li>
 * </ul>
 *
 * <p>One law per consult SITE, the same one-consult-per-site shape the
 * F-EC1 and VT-020 corrections carried. The survival walks
 * ({@code RedstoneSystem.torchSupported} / {@code leverSupported} /
 * {@code buttonSupported} / {@code trapdoorMountHolds}) and the placement
 * gates ({@code placeTorchOnTick} / {@code placeLeverOnTick} /
 * {@code placeButtonOnTick} / {@code placeTrapdoorOnTick}) read the law
 * their vanilla counterpart reads — never the collision question.</p>
 */
public final class SupportConsult {

    private SupportConsult() {
    }

    /**
     * The reference's {@code World.isSolidBlockingCube} (World.java lines
     * 1753-1768): {@code material.isSolidBlocking() && isCube()} — the
     * torch's WALL consult. The translucent materials (the leaves, the
     * glass family with the glowstone and the panes, the TNT, the ice, the
     * snow layer, the cactus — {@code Material.isSolidBlocking} =
     * {@code !translucent && blocksMovement()}, Material.java lines
     * 100-102) never answer; the non-cubes (the partial shapes — the
     * single slabs, the stairs, the fences, the walls, the panes, the
     * chests, the hoppers, the doors, the trapdoors) never answer. The
     * DOUBLE slabs answer ({@code SlabBlock.isCube() = isDouble()}).
     */
    public static boolean isSolidBlockingCube(BlockType type) {
        if (type == null) {
            return false;
        }
        Identifier id = type.identifier();
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        // material.isSolidBlocking() = !translucent && blocksMovement().
        if (MaterialSolidity.isTranslucentMaterial(id)
                || !MaterialSolidity.blocksMovement(id)) {
            return false;
        }
        // isCube() — the render cube (BlockSolidity's own law).
        return BlockSolidity.isCube(type, id);
    }

    /**
     * The reference's {@code World.hasSolidTop} (World.java lines
     * 1740-1752) — the five arms, in the reference's own order:
     * <ol>
     *   <li>{@code material.isSolidBlocking() && isCube()} — the
     *       solid-blocking cubes, the DOUBLE slabs among them;</li>
     *   <li>{@code StairsBlock} with {@code HALF == TOP} (the bottom
     *       halves refuse);</li>
     *   <li>{@code SlabBlock} with {@code HALF == TOP} (the doubles
     *       answered arm 1; the bottoms refuse);</li>
     *   <li>{@code HopperBlock} — every facing, the engine's whole
     *       {@code hopper_*} band;</li>
     *   <li>{@code SnowLayerBlock} with {@code LAYERS == 7} — unreachable
     *       in the engine (the snow layer is the single 1-layer state,
     *       which the isCube gate refuses through arm 1's translucent
     *       material).</li>
     * </ol>
     */
    public static boolean hasSolidTop(BlockType type) {
        if (type == null) {
            return false;
        }
        Identifier id = type.identifier();
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        // Arm 1: the solid-blocking cube (the doubles included).
        if (isSolidBlockingCube(type)) {
            return true;
        }
        String value = id.value();
        // Arm 2: StairsBlock with HALF == TOP.
        if (WoodShapeBlocks.isStairsState(value)) {
            return WoodShapeBlocks.isStairsTopState(value);
        }
        // Arm 3: SlabBlock with HALF == TOP (the doubles answered arm 1 —
        // the stone_slab2 pair carries its own rows, the engine's flat ids).
        if (isSingleSlab(value)) {
            return value.endsWith("_slab_top") || value.equals("stone_slab2_top");
        }
        // Arm 4: HopperBlock — every facing/enabled state.
        if (value.startsWith("hopper_")) {
            return true;
        }
        // Arm 5: the 7-layer snow — unreachable (the single 1-layer state
        // fails arm 1's translucent material exactly like the reference's
        // LAYERS < 7 rows fail the isCube gate).
        return false;
    }

    /**
     * The reference's {@code TorchBlock.canSitOnTop} (lines 49-55): the
     * torch's FLOOR consult — {@code World.hasSolidTop} extended with the
     * explicit {@code instanceof FenceBlock || == Blocks.GLASS ||
     * == Blocks.COBBLESTONE_WALL || == Blocks.STAINED_GLASS} list. The
     * fences (every wood AND the nether brick — {@code FenceBlock}
     * instances, Block.java lines 1235-1236), the glass, the cobblestone
     * wall (BOTH variants — the mossy form is the same reference block's
     * VARIANT), and the stained glass (the block family, never the panes)
     * hold the standing torch; nothing else beyond hasSolidTop's arms.
     */
    public static boolean torchCanSitOnTop(BlockType type) {
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
        return WorldSolidity.isFence(id)
                || value.equals("glass")
                || value.equals("cobblestone_wall") || value.equals("mossy_cobblestone_wall")
                || (value.startsWith("stained_glass")
                        && !value.startsWith("stained_glass_pane"));
    }

    /**
     * The reference's lever/button mount consult
     * ({@code ButtonBlock.canSurvive} lines 70-73, to which
     * {@code LeverBlock.canSurvive} line 60 delegates):
     * {@code facing == DOWN ? World.hasSolidTop(blockpos) :
     * block.isSolid()} — where {@code facing} is the CONSULT direction
     * (toward the mount). The floor mount (FACING=UP, consult DOWN) reads
     * {@link #hasSolidTop}; every other side — the walls AND the ceiling
     * (FACING=DOWN, consult UP onto the block above) — reads
     * {@link BlockSolidity#isSolid} (the engine's landed
     * {@code Block.isSolid()}).
     *
     * @param mount      the mount block the consult reads
     * @param consultDown whether the consult direction is DOWN (the floor
     *                    mount — the lever/button points UP)
     */
    public static boolean leverButtonMountHolds(BlockType mount, boolean consultDown) {
        return consultDown ? hasSolidTop(mount) : BlockSolidity.isSolid(mount);
    }

    /**
     * The reference's {@code TrapdoorBlock.canBePlacedOn} (lines 188-190):
     * {@code material.isSolidBlocking() && isCube() || == Blocks.GLOWSTONE
     * || instanceof SlabBlock || instanceof StairsBlock} — ANY slab half
     * and ANY stair state (no HALF condition — the trapdoor's mount law is
     * looser than hasSolidTop's), the glowstone's explicit row (the ONE
     * consult that names it), and no hopper arm ({@code hasSolidTop}'s
     * hopper row is the floor consults' alone).
     */
    public static boolean trapdoorCanBePlacedOn(BlockType mount) {
        if (mount == null) {
            return false;
        }
        Identifier id = mount.identifier();
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        if (isSolidBlockingCube(mount)) {
            return true;
        }
        String value = id.value();
        return value.equals("glowstone")
                || isSlabBlock(value)
                || isStairsBlock(value);
    }

    /**
     * @return whether the value is a SINGLE-slab state ({@code
     * SlabBlock} with the flat ids' half suffixes — the doubles excluded,
     * they answer the solid-blocking-cube arm).
     */
    private static boolean isSingleSlab(String value) {
        if (value.startsWith("double_")) {
            return false;
        }
        return value.endsWith("_slab") || value.endsWith("_slab_top")
                || value.equals("stone_slab2") || value.equals("stone_slab2_top");
    }

    /**
     * @return whether the value belongs to a slab family ({@code
     * SlabBlock} — the single halves AND the doubles, the instance arm's
     * whole family).
     */
    private static boolean isSlabBlock(String value) {
        if (value.startsWith("double_")) {
            return value.endsWith("_slab") || value.endsWith("_slab2")
                    || value.endsWith("_slab_seamless") || value.endsWith("_slab2_seamless");
        }
        return value.endsWith("_slab") || value.endsWith("_slab_top")
                || value.equals("stone_slab2") || value.equals("stone_slab2_top");
    }

    /**
     * @return whether the value belongs to a stairs family ({@code
     * StairsBlock} — every facing state AND the bare family name).
     */
    private static boolean isStairsBlock(String value) {
        return WoodShapeBlocks.isStairsState(value) || WoodShapeBlocks.isStairsItem(value)
                || value.startsWith("oak_stairs") || value.startsWith("cobblestone_stairs")
                || value.startsWith("sandstone_stairs") || value.startsWith("nether_brick_stairs")
                || value.startsWith("red_sandstone_stairs");
    }
}
