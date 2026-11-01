package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The mount-survival laws (F-SP1 — the support-consult family): the four
 * consults vanilla answers per family, each different, over the engine's
 * own tables (MaterialSolidity's material census, BlockSolidity's render
 * cube, the state vocabulary's half bands).
 *
 * <pre>{@code
 * World.isSolidBlockingCube   (World.java lines 1753-1768)
 *   = material.isSolidBlocking() && isCube()
 * World.hasSolidTop           (World.java lines 1740-1752)
 *   = isSolidBlockingCube || stairs HALF==TOP || slab HALF==TOP
 *     || HopperBlock || SnowLayerBlock LAYERS==7
 * TorchBlock.canSitOnTop      (TorchBlock.java lines 49-55)
 *   = hasSolidTop || FenceBlock || GLASS || COBBLESTONE_WALL || STAINED_GLASS
 * TorchBlock.canAttach        (TorchBlock.java lines 66-69)
 *   = horizontal && isSolidBlockingCube || UP && canSitOnTop
 * ButtonBlock.canSurvive      (ButtonBlock.java lines 70-73; LeverBlock
 *   line 60 delegates)
 *   = DOWN ? hasSolidTop : Block.isSolid()
 * TrapdoorBlock.canBePlacedOn (TrapdoorBlock.java lines 188-190)
 *   = isSolidBlocking && isCube || GLOWSTONE || SlabBlock || StairsBlock
 * }</pre>
 *
 * <p>Every law is distinct from the movement consult
 * ({@link WorldSolidity} — what blocks a body): the translucent materials
 * (LEAVES, GLASS, TNT, ICE, SNOW_LAYER, CACTUS — Material.java lines
 * 13/22/24/26/28/30) never answer a wall consult whatever their shape,
 * and the non-cube solids (panes, iron bars, walls, fences) never answer
 * the cube consults. The signal consult {@link BlockSolidity#isSolid}
 * carries the lever/button law's non-floor arm exactly.</p>
 *
 * <p>Known limitation (the engine's own single-state snow model — VT-003's
 * state-surface tail): {@code hasSolidTop}'s snow arm reads LAYERS == 7,
 * a band the engine's one-state snow layer does not model; the row answers
 * false.</p>
 */
public final class SupportConsult {

    private SupportConsult() {
    }

    /**
     * The reference's {@code World.isSolidBlockingCube} (lines 1753-1768):
     * {@code material.isSolidBlocking() && isCube()} — the torch's WALL
     * consult and the trapdoor's first arm. The translucent materials never
     * answer; the non-cube solids never answer.
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
        return !MaterialSolidity.isTranslucentMaterial(id)
                && MaterialSolidity.blocksMovement(id)
                && BlockSolidity.isCube(type);
    }

    /**
     * The reference's {@code World.hasSolidTop} (lines 1740-1752) — the
     * five arms: the full opaque cubes, the stairs' top half, the slab's
     * top half, the hopper, the seven-layer snow. The lever/button FLOOR
     * consult and the torch floor's first arm ride this law.
     */
    public static boolean hasSolidTop(BlockType type) {
        if (type == null) {
            return false;
        }
        Identifier id = type.identifier();
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        // Arm 1: material.isSolidBlocking() && isCube() — the full opaque
        // cubes (the double slabs included: SlabBlock.isCube() = isDouble()).
        if (isSolidBlockingCube(type)) {
            return true;
        }
        String value = id.value();
        // Arm 2: StairsBlock with HALF == TOP. The engine's directional
        // fold: {family}_stairs_{facing} is the bottom half,
        // {family}_stairs_{facing}_top the upside-down one; the bare
        // aliases' default metadata row is the bottom (the VT-020 census).
        if (isStairs(value)) {
            return value.endsWith("_top");
        }
        // Arm 3: SlabBlock with HALF == TOP ({family}_slab_top).
        if (isSlab(value)) {
            return value.endsWith("_top");
        }
        // Arm 4: HopperBlock's own solid top (the reference's instanceof —
        // the hopper's enabled flag lives on the block entity in vanilla,
        // so the whole family answers; the engine's disabled PAIR is its
        // own state model of the same block, the family check carrying it).
        if (value.equals("hopper")
                || net.zaminmc.torch.server.hopper.HopperBlocks.isHopper(type)) {
            return true;
        }
        // Arm 5: SnowLayerBlock with LAYERS == 7 — the engine's one-state
        // snow layer carries no layers band (VT-003's state-surface tail);
        // the row answers false (the documented limitation).
        return false;
    }

    /**
     * The torch's FLOOR consult (TorchBlock.canSitOnTop, lines 49-55):
     * {@code hasSolidTop} plus the explicit list — the fences
     * (instanceof FenceBlock), the glass block, the cobblestone wall and
     * the stained glass block, each the EXACT block (the mossy wall and
     * the panes are NOT in the reference's list).
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
        if (WorldSolidity.isFence(id)) {
            return true; // instanceof FenceBlock (the six woods + nether brick)
        }
        String value = id.value();
        return value.equals("glass") // == Blocks.GLASS
                || value.equals("cobblestone_wall") // == Blocks.COBBLESTONE_WALL
                || value.equals("stained_glass"); // == Blocks.STAINED_GLASS
    }

    /**
     * The lever/button mount consult (ButtonBlock.canSurvive lines 70-73;
     * LeverBlock line 60 delegates): the FLOOR mount (the mount below —
     * the reference's {@code facing == DOWN} arm) rides
     * {@code World.hasSolidTop}; every other side rides
     * {@code Block.isSolid()} — the signal consult {@link BlockSolidity}
     * already ports 1:1.
     */
    public static boolean leverButtonMountHolds(BlockType mount, boolean floorMount) {
        return floorMount ? hasSolidTop(mount) : BlockSolidity.isSolid(mount);
    }

    /**
     * The trapdoor's mount consult (TrapdoorBlock.canBePlacedOn lines
     * 188-190): {@code material.isSolidBlocking() && isCube() || GLOWSTONE
     * || SlabBlock || StairsBlock}. The slab and stairs instance arms
     * cover BOTH halves and the doubles (the reference's own
     * instanceof); everything else rides the cube law — the fences, the
     * gates, the doors, the trapdoors, the rails, the chests, the panes,
     * the walls and the translucent materials all refuse through the cube
     * conjunct alone (the reference's own rejection).
     */
    public static boolean trapdoorCanBePlacedOn(BlockType type) {
        if (type == null) {
            return false;
        }
        Identifier id = type.identifier();
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        if (value.equals("glowstone")) {
            return true; // == Blocks.GLOWSTONE
        }
        if (isSlab(value) || isStairs(value)) {
            return true; // instanceof SlabBlock / StairsBlock (both halves)
        }
        return isSolidBlockingCube(type);
    }

    // ------------------------------------------------------------------
    // The state vocabulary's family bands (the engine's own shapes — the
    // same suffix laws isTrapdoorMount carried, the VT-020 census's rows)
    // ------------------------------------------------------------------

    /**
     * The slab family over the engine's state vocabulary: the six woods'
     * and the four stones' single halves ({family}_slab / {family}_slab_top)
     * plus the red sandstone pair (stone_slab2 / stone_slab2_top). The
     * double forms are full cubes and answer the cube law directly.
     */
    static boolean isSlab(String value) {
        return value.endsWith("_slab") || value.endsWith("_slab_top")
                || value.equals("stone_slab2") || value.equals("stone_slab2_top");
    }

    /**
     * The stairs family over the engine's state vocabulary: the bare
     * family aliases (isStairsItem — every registered stairs name ends
     * with {@code _stairs}) and the directional fold's facing states
     * (isStairsState, the bottom and the upside-down halves).
     */
    static boolean isStairs(String value) {
        return WoodShapeBlocks.isStairsItem(value) || WoodShapeBlocks.isStairsState(value);
    }
}
