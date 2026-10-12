package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The tripwire family's block types (Slice 10c — the flattened state
 * model, reference/1.8.8 block/TripwireBlock.java +
 * TripwireHookBlock.java).
 *
 * <p><b>The wire</b> (legacy 132): the four metadata bits POWERED | SUSPENDED
 * | ATTACHED | DISARMED (TripwireBlock lines 226-249). The N/E/S/W
 * connection flags are VIRTUAL (resolveVirtualProperties — the client
 * computes its own connections for rendering); the engine's flattened
 * model keeps the four stored bits only, named
 * {@code tripwire[_powered][_suspended][_attached][_disarmed]}. SUSPENDED
 * means "no solid top below" (the wire hangs in the air — a fresh
 * placement sets it from the floor test).</p>
 *
 * <p><b>The hook</b> (legacy 131): a horizontal FACING with an ATTACHED pair
 * and a POWERED pair — the nibble
 * {@code facingH | (attached ? 4 : 0) | (powered ? 8 : 0)}
 * (TripwireHookBlock lines 271-283). The FACING points AT the wire chain
 * (away from the mounting wall).</p>
 */
public final class TripwireBlocks {

    /** The horizontal direction names in facingH order (south west north east). */
    private static final String[] HORIZONTALS = {"south", "west", "north", "east"};

    private static final EngineBlockType[] WIRE = buildWire();
    private static final EngineBlockType[][][] HOOK = buildHook();

    private TripwireBlocks() {
    }

    // ------------------------------------------------------------------
    // The wire (legacy 132)
    // ------------------------------------------------------------------

    /** @return whether the type is any tripwire state (the bare base included). */
    public static boolean isTripwire(BlockType type) {
        if (type == null || !type.identifier().namespace().equals("minecraft")) {
            return false;
        }
        String value = type.identifier().value();
        return value.equals("tripwire")
                || (value.startsWith("tripwire_") && !value.startsWith("tripwire_hook"));
    }

    /** The wire's POWERED bit. */
    public static boolean wirePowered(BlockType type) {
        return wireBits(type, 1);
    }

    /** The wire's SUSPENDED bit (no solid top below). */
    public static boolean wireSuspended(BlockType type) {
        return wireBits(type, 2);
    }

    /** The wire's ATTACHED bit. */
    public static boolean wireAttached(BlockType type) {
        return wireBits(type, 4);
    }

    /** The wire's DISARMED bit (the shears arm). */
    public static boolean wireDisarmed(BlockType type) {
        return wireBits(type, 8);
    }

    private static boolean wireBits(BlockType type, int mask) {
        return (metadataOf(valueOf(type)) & mask) != 0;
    }

    /** The wire state for a bit combination. */
    public static EngineBlockType wireOf(boolean powered, boolean suspended,
                                          boolean attached, boolean disarmed) {
        return WIRE[(powered ? 1 : 0) | (suspended ? 2 : 0) | (attached ? 4 : 0)
                | (disarmed ? 8 : 0)];
    }

    /** The same wire state with one bit swapped. */
    public static EngineBlockType wireOfState(BlockType type, boolean powered,
                                               boolean suspended, boolean attached,
                                               boolean disarmed) {
        return wireOf(powered, suspended, attached, disarmed);
    }

    // ------------------------------------------------------------------
    // The hook (legacy 131)
    // ------------------------------------------------------------------

    /** @return whether the type is any tripwire hook state. */
    public static boolean isHook(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().startsWith("tripwire_hook");
    }

    /** The hook's FACING as the reference Direction id (toward the wire chain). */
    public static int hookFacing(BlockType type) {
        String rest = valueOf(type).substring("tripwire_hook_".length());
        int cut = rest.indexOf('_');
        String facing = cut < 0 ? rest : rest.substring(0, cut);
        return switch (facing) {
            case "south" -> 3;
            case "west" -> 4;
            case "north" -> 2;
            default -> 5; // east
        };
    }

    /** The hook's FACING as the reference's horizontal metadata id (0=S 1=W 2=N 3=E). */
    public static int hookFacingH(BlockType type) {
        return switch (hookFacing(type)) {
            case 3 -> 0; // south
            case 4 -> 1; // west
            case 2 -> 2; // north
            default -> 3; // east
        };
    }

    /** The hook's ATTACHED bit. */
    public static boolean hookAttached(BlockType type) {
        return valueOf(type).contains("_attached");
    }

    /** The hook's POWERED bit. */
    public static boolean hookPowered(BlockType type) {
        return valueOf(type).endsWith("_powered");
    }

    /** The hook state for a facing (reference Direction id) + attached + powered. */
    public static EngineBlockType hookOf(int facing, boolean attached, boolean powered) {
        int h = switch (facing) {
            case 2 -> 2; // north
            case 3 -> 0; // south
            case 4 -> 1; // west
            default -> 3; // east
        };
        return HOOK[h][attached ? 1 : 0][powered ? 1 : 0];
    }

    /** The same hook state with the attached/powered pairs swapped. */
    public static EngineBlockType hookOfState(BlockType type, boolean attached, boolean powered) {
        return hookOf(hookFacing(type), attached, powered);
    }

    // ------------------------------------------------------------------
    // Naming + registration
    // ------------------------------------------------------------------

    private static String valueOf(BlockType type) {
        return type.identifier().value();
    }

    /** The four stored bits from the identifier's suffixes. */
    private static int metadataOf(String value) {
        int bits = 0;
        if (value.contains("_powered")) {
            bits |= 1;
        }
        if (value.contains("_suspended")) {
            bits |= 2;
        }
        if (value.contains("_attached")) {
            bits |= 4;
        }
        if (value.endsWith("_disarmed")) {
            bits |= 8;
        }
        return bits;
    }

    private static EngineBlockType[] buildWire() {
        EngineBlockType[] all = new EngineBlockType[16];
        for (int bits = 0; bits < 16; bits++) {
            boolean powered = (bits & 1) != 0;
            boolean suspended = (bits & 2) != 0;
            boolean attached = (bits & 4) != 0;
            boolean disarmed = (bits & 8) != 0;
            StringBuilder name = new StringBuilder("tripwire");
            if (powered) {
                name.append("_powered");
            }
            if (suspended) {
                name.append("_suspended");
            }
            if (attached) {
                name.append("_attached");
            }
            if (disarmed) {
                name.append("_disarmed");
            }
            all[bits] = new EngineBlockType(Identifier.parse("minecraft:" + name),
                    "Tripwire", bits);
        }
        return all;
    }

    private static EngineBlockType[][][] buildHook() {
        EngineBlockType[][][] all = new EngineBlockType[4][2][2];
        for (int h = 0; h < 4; h++) {
            for (int attached = 0; attached <= 1; attached++) {
                for (int powered = 0; powered <= 1; powered++) {
                    all[h][attached][powered] = new EngineBlockType(
                            Identifier.parse("minecraft:tripwire_hook_" + HORIZONTALS[h]
                                    + (attached == 1 ? "_attached" : "")
                                    + (powered == 1 ? "_powered" : "")),
                            "Tripwire Hook", h | (attached << 2) | (powered << 3));
                }
            }
        }
        return all;
    }

    /** Registers every tripwire type into the block registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType type : WIRE) {
            builder.register(type);
        }
        for (EngineBlockType[][] facing : HOOK) {
            for (EngineBlockType[] attached : facing) {
                for (EngineBlockType type : attached) {
                    builder.register(type);
                }
            }
        }
        return builder;
    }
}
