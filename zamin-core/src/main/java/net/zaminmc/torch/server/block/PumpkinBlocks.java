package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The pumpkin pair's horizontal FACING states (the metadata slice,
 * reference/1.8.8 block/PumpkinBlock.java): legacy 86 (the plain block)
 * and legacy 91 (the lit lantern), four states each. The nibble is the
 * reference's metadata {@code FACING.getIdHorizontal()}
 * (PumpkinBlock lines 138-148: getStateFromMetadata reads
 * {@code Direction.byIdHorizontal(metadata)} and getMetadataFromState
 * writes the horizontal id back) — a DIFFERENT domain than the chest
 * family's Direction-id band: {@code 0=SOUTH, 1=WEST, 2=NORTH, 3=EAST}
 * (Direction.java lines 14-17's own idHorizontal assignments).
 *
 * <p>The bare forms keep their plain ids as the SOUTH state (metadata 0,
 * the dye family's white-wool precedent — every pumpkin in an existing
 * world stays valid): {@code minecraft:pumpkin} and
 * {@code minecraft:lit_pumpkin} ARE the south-facing states; the family
 * adds the west/north/east states beside them.</p>
 *
 * <p>The placement law (PumpkinBlock.getPlacementState line 129):
 * {@code FACING = entity.getHorizontalFacing().getOpposite()} — the
 * pumpkin's face looks back at the placer. The worldgen patch
 * (PumpkinPatchFeature line 16) rolls {@code Direction.Plane.HORIZONTAL
 * .pick(random)}; the dispense arm (Bootstrap line 403) and the stem's
 * fruit (StemBlock line 77) place the DEFAULT state — FACING NORTH,
 * horizontal id 2, not the bare south form.</p>
 */
public final class PumpkinBlocks {

    /** The horizontal-id order (Direction.getIdHorizontal's domain). */
    static final String[] HORIZONTAL_KEYS = {"south", "west", "north", "east"};

    /** The bare south states (metadata 0 — BuiltinBlocks' own constants). */
    public static final EngineBlockType PUMPKIN = BuiltinBlocks.PUMPKIN;
    public static final EngineBlockType LIT_PUMPKIN = BuiltinBlocks.LIT_PUMPKIN;

    /** The rotated plain states: west 1, north 2, east 3. */
    private static final EngineBlockType[] PLAIN_ROTATED = {
            new EngineBlockType(Identifier.parse("minecraft:pumpkin_west"), "Pumpkin", 1),
            new EngineBlockType(Identifier.parse("minecraft:pumpkin_north"), "Pumpkin", 2),
            new EngineBlockType(Identifier.parse("minecraft:pumpkin_east"), "Pumpkin", 3)};

    /** The rotated lit states: west 1, north 2, east 3. */
    private static final EngineBlockType[] LIT_ROTATED = {
            new EngineBlockType(Identifier.parse("minecraft:lit_pumpkin_west"), "Jack o'Lantern", 1),
            new EngineBlockType(Identifier.parse("minecraft:lit_pumpkin_north"), "Jack o'Lantern", 2),
            new EngineBlockType(Identifier.parse("minecraft:lit_pumpkin_east"), "Jack o'Lantern", 3)};

    private PumpkinBlocks() {
    }

    /** @return whether the type is any plain-pumpkin facing state. */
    public static boolean isPlainFamily(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && (type.identifier().value().equals("pumpkin")
                        || type.identifier().value().startsWith("pumpkin_")
                                && !type.identifier().value().startsWith("pumpkin_stem"));
    }

    /** @return whether the type is any lit-pumpkin facing state. */
    public static boolean isLitFamily(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && (type.identifier().value().equals("lit_pumpkin")
                        || type.identifier().value().startsWith("lit_pumpkin_"));
    }

    /** @return whether the type is either family's state — the golem head. */
    public static boolean isGolemHead(BlockType type) {
        return isPlainFamily(type) || isLitFamily(type);
    }

    /** The state's FACING as the reference horizontal id (0..3). */
    public static int horizontalFacingOf(BlockType type) {
        String value = type.identifier().value();
        String stem = value.startsWith("lit_pumpkin") ? "lit_pumpkin" : "pumpkin";
        if (value.length() <= stem.length()) {
            return 0; // the bare forms ARE the south states (metadata 0)
        }
        String facing = value.substring(stem.length() + 1); // past the '_'
        for (int i = 0; i < HORIZONTAL_KEYS.length; i++) {
            if (HORIZONTAL_KEYS[i].equals(facing)) {
                return i;
            }
        }
        return 0;
    }

    /** The pumpkin state for a kind and a horizontal id (0 = the bare south form). */
    public static EngineBlockType pumpkinOf(boolean lit, int horizontalId) {
        if (lit) {
            switch (horizontalId & 3) {
                case 1: return LIT_ROTATED[0];
                case 2: return LIT_ROTATED[1];
                case 3: return LIT_ROTATED[2];
                default: return LIT_PUMPKIN;
            }
        }
        switch (horizontalId & 3) {
            case 1: return PLAIN_ROTATED[0];
            case 2: return PLAIN_ROTATED[1];
            case 3: return PLAIN_ROTATED[2];
            default: return PUMPKIN;
        }
    }

    /**
     * The look's horizontal band over the yaw — the reference's own
     * formula (Entity.getHorizontalFacing over Direction.byRotation:
     * {@code byIdHorizontal(floor(yaw / 90.0 + 0.5) & 3)}): the +45-degree
     * shift centers the cardinals (yaw 0 the middle of the south band,
     * not its edge) and the {@code & 3} masks the negative yaws the
     * client sends. The engine's sign placement (the same formula) is the
     * in-house precedent; the chest/furnace placement sites' older
     * {@code (int) (yaw / 90.0) % 4} truncation diverges at every
     * half-quadrant (yaw 45 is a WEST look in the reference, a SOUTH one
     * under the truncation) — their own family audits own that band.
     *
     * @return the look's horizontal id: 0 south, 1 west, 2 north, 3 east.
     */
    public static int lookBandOf(float yaw) {
        return (int) ((((yaw % 360.0f) + 360.0f + 45.0f) % 360.0f) / 90.0f) & 3;
    }

    /** The horizontal id's opposite (Direction.getOpposite over the band). */
    public static int oppositeHorizontal(int horizontalId) {
        return horizontalId ^ 2; // 0<->2 (south/north), 1<->3 (west/east)
    }

    /** Registers the rotated states into the block registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType type : PLAIN_ROTATED) {
            builder.register(type);
        }
        for (EngineBlockType type : LIT_ROTATED) {
            builder.register(type);
        }
        return builder;
    }
}
