package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.piston.PistonBlocks;
import net.zaminmc.torch.util.Identifier;

/**
 * The reference's {@code Block.isSolid()} (Block.java lines 274-276) — the
 * SIGNAL re-radiation consult, distinct from both the movement model
 * ({@link WorldSolidity} — what blocks a body) and the material model
 * ({@link MaterialSolidity} — the Material class alone):
 *
 * <pre>{@code
 * isSolid() = material.isSolidBlocking() && isCube() && !isSignalSource()
 * }</pre>
 *
 * <p>{@code World.getSignal} (lines 2358-2362) routes on this law alone: a
 * solid block re-radiates the strong power entering it as weak; every other
 * block answers its own emission. The same consult gates the comparator's
 * through-solid arm (ComparatorBlock.getInputSignal line 114), the wire's
 * climb and descent bands (RedstoneWireBlock.doUpdatePower lines 129 + 133),
 * the wire's arrival and departure wakes (onAdded lines 191-194 + onRemoved
 * lines 216-219) and the comparator wake's through-solid walk
 * (World.updateNeighborComparators line 2708).</p>
 *
 * <p>The three conjuncts over the engine's own tables:
 * <ul>
 * <li>{@code material.isSolidBlocking()} (Material.java lines 100-102 =
 * {@code !translucent && blocksMovement()}) — the opaque-material consult:
 * the leaves, the glass family, the glowstone, the sea lantern, the panes,
 * the TNT, the ice, the snow layer and the cactus are TRANSLUCENT; the
 * air/fire/liquid/plant/decoration/portal/cobweb families never block
 * movement.</li>
 * <li>{@code isCube()} — the render cube: the full-box collision shape
 * ({@link CollisionShapes#isFullCube}) MINUS the reference's own full-box
 * isCube-false families — the glass pair (GlassBlock lines 25-27 +
 * StainedGlassBlock lines 60-62), the beacon (BeaconBlock lines 54-56) and
 * the piston bases (PistonBaseBlock lines 223-225 — the base carries the
 * full box, the head rides its own cell) — PLUS the double slab (SlabBlock's
 * own {@code isDouble()}). Every partial shape — the chests (ChestBlock
 * lines 46-48 + EnderChestBlock lines 38-40), the single slabs, the stairs,
 * the fences, the walls, the panes, the farmland, the hoppers, the doors,
 * the trapdoors and the rest of the isCube-false census — reads false
 * through the collision table alone.</li>
 * <li>{@code !isSignalSource()} — the true-override census: the redstone
 * block (RedstoneBlock lines 16-18 — the ONLY member that survives the
 * first two conjuncts), the trapped chest (ChestBlock lines 377-380 — dead
 * at isCube), the daylight detector, the buttons, the plates, the levers,
 * the wire, the diodes, the redstone torches, the tripwire hook and the
 * detector rail (each dead at isCube or the material conjunct before this
 * arm).</li>
 * </ul></p>
 */
public final class BlockSolidity {

    private BlockSolidity() {
    }

    /**
     * @return the reference's {@code Block.isSolid()} for the registered
     *         kind — whether the block re-radiates the strong power
     *         entering it as weak (the signal consult, never the movement
     *         consult).
     */
    public static boolean isSolid(BlockType type) {
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
        // isCube(): the render cube.
        if (!isCube(type, id)) {
            return false;
        }
        // !isSignalSource(): the redstone block alone reaches this arm.
        return !id.equals(BuiltinBlocks.REDSTONE_BLOCK.identifier());
    }

    /**
     * The reference's {@code isCube()} consult (the render cube): the
     * double slab's full cube, then the full-box collision shape minus the
     * reference's own full-box isCube-false families.
     */
    private static boolean isCube(BlockType type, Identifier id) {
        String value = id.value();
        // SlabBlock.isCube() = isDouble(): the double form is the cube
        // (the engine's flat ids keep the joined pair's own names).
        if (value.startsWith("double_") && (value.endsWith("_slab")
                || value.endsWith("_slab2") || value.endsWith("_slab_seamless")
                || value.endsWith("_slab2_seamless"))) {
            return true;
        }
        // The partial-shape census answers through the collision table.
        if (!CollisionShapes.isFullCube(type)) {
            return false;
        }
        // The full-box isCube-false families: the glass pair, the beacon
        // and the piston bases.
        return !isGlassFamily(value)
                && !value.equals("beacon")
                && !PistonBlocks.isPiston(type);
    }

    /** The glass family (the isCube-false full-box pair). */
    private static boolean isGlassFamily(String value) {
        return value.equals("glass") || value.startsWith("stained_glass");
    }

    /**
     * The reference's {@code isCube()} consult (the render cube), public for
     * the mount laws ({@link SupportConsult} — F-SP1): the torch's
     * {@code isSolidBlockingCube}, {@code hasSolidTop}'s first arm and the
     * trapdoor's {@code canBePlacedOn} all carry the cube conjunct.
     */
    public static boolean isCube(BlockType type) {
        return type != null && isCube(type, type.identifier());
    }
}
