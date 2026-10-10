package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The redstone family's block types (Slice 9a — the flattened state model the
 * engine's block identity uses, reference/1.8.8 block/RedstoneWireBlock +
 * RedstoneTorchBlock + RepeaterBlock/DiodeBlock).
 *
 * <p><b>The wire</b> stores only POWER (0..15, the metadata nibble, legacy id
 * 55) — the visual connections are the client's own resolveVirtualProperties
 * computation, never server state. Sixteen identifiers, {@code
 * minecraft:redstone_wire} (power 0) .. {@code minecraft:redstone_wire_15}.
 *
 * <p><b>The torch</b> exists as a lit pair ({@code minecraft:redstone_torch}
 * standing + four wall facings, legacy 76, metadata 1..5) and an unlit pair
 * ({@code minecraft:unlit_redstone_torch}..., legacy 75) — the toggle swaps
 * the family pair in place, exactly the reference's setBlockState swap. The
 * facing is the torch's FACING (UP for standing, the four horizontals for
 * walls): the torch reads its input at FACING.opposite (its attachment side)
 * and emits strongly only upward.
 *
 * <p><b>The repeater</b> exists as an unpowered pair ({@code
 * minecraft:repeater_<facing>_<delay>}, legacy 93) and a powered pair ({@code
 * minecraft:powered_repeater_<facing>_<delay>}, legacy 94) — the FACING (one
 * of the four horizontals) points toward the repeater's INPUT (the reference's
 * convention: the input is read at pos.offset(FACING), the output exits at
 * FACING.opposite, and the placed block points FACING at the player, so the
 * signal flows away from the look direction, reference DiodeBlock lines
 * 117-127 + 154-157). The DELAY (1..4 ticks) is the player-cycled property
 * (use/right-click steps it, reference RepeaterBlock.use lines 38-45). The
 * LOCKED property is virtual (computed from the side inputs) and never stored.
 *
 * <p>The horizontal facing codes match the reference's {@code
 * Direction.byIdHorizontal}: 0=south(+Z), 1=west(-X), 2=north(-Z), 3=east(+X).
 */
public final class RedstoneBlocks {

    /** The horizontal facing codes (Direction.byIdHorizontal order). */
    public static final int FACING_SOUTH = 0; // +Z
    public static final int FACING_WEST = 1;  // -X
    public static final int FACING_NORTH = 2; // -Z
    public static final int FACING_EAST = 3;  // +X

    /** The facing's unit offsets in the byIdHorizontal order. */
    private static final int[][] FACING_OFFSETS = {
            {0, 1},   // south +Z
            {-1, 0},  // west -X
            {0, -1},  // north -Z
            {1, 0},   // east +X
    };

    private static final String[] FACING_NAMES = {"south", "west", "north", "east"};

    /**
     * The torch pair's storage layout: index 0 = standing (metadata 1), then
     * the four wall facings (2=east, 3=west, 4=south, 5=north — the
     * reference's metadata order).
     */
    private static final String[] TORCH_WALL = {"east", "west", "south", "north"};
    /** The wall facings' reference metadata (FACING ids): 2=north 3=south 4=west 5=east. */
    private static final int[] TORCH_WALL_META = {5, 4, 3, 2};

    public static final int MAX_POWER = 15;

    private RedstoneBlocks() {
    }

    // ------------------------------------------------------------------
    // The wire
    // ------------------------------------------------------------------

    /** The wire at a power level (0..15). */
    public static EngineBlockType wireOfPower(int power) {
        return WIRE[power & 0xF];
    }

    /** The power stored in a wire block type (0 for non-wires). */
    public static int wirePower(net.zaminmc.torch.block.BlockType type) {
        return type instanceof EngineBlockType engineType ? engineType.legacyMetadata() : 0;
    }

    /** @return whether the type is any power level of the wire. */
    public static boolean isWire(net.zaminmc.torch.block.BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().startsWith("redstone_wire");
    }

    // ------------------------------------------------------------------
    // The torch family (lit + unlit)
    // ------------------------------------------------------------------

    /**
     * The torch facing as the reference's FACING ordinal: 0=DOWN,1=UP,2=NORTH,
     * 3=SOUTH,4=WEST,5=EAST — the standing torch is UP(1), the walls read
     * from the identifier suffix.
     */
    public static int torchFacing(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        if (value.equals("redstone_torch") || value.equals("unlit_redstone_torch")) {
            return 1; // UP
        }
        String suffix = value.substring(value.lastIndexOf('_') + 1);
        return switch (suffix) {
            case "north" -> 2;
            case "south" -> 3;
            case "west" -> 4;
            case "east" -> 5;
            default -> 1;
        };
    }

    /** @return whether the torch type is the lit pair (legacy 76). */
    public static boolean torchLit(net.zaminmc.torch.block.BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().startsWith("redstone_torch");
    }

    /** @return whether the type is either torch pair (lit or unlit). */
    public static boolean isTorch(net.zaminmc.torch.block.BlockType type) {
        if (type == null || !type.identifier().namespace().equals("minecraft")) {
            return false;
        }
        String value = type.identifier().value();
        return value.startsWith("redstone_torch") || value.startsWith("unlit_redstone_torch");
    }

    /** The lit/unlit pair member carrying the same facing as the given type. */
    public static EngineBlockType torchOfType(net.zaminmc.torch.block.BlockType type, boolean lit) {
        String value = type.identifier().value();
        String prefix = value.startsWith("unlit_") ? "unlit_redstone_torch" : "redstone_torch";
        boolean standing = value.equals("redstone_torch") || value.equals("unlit_redstone_torch");
        if (standing) {
            return lit ? TORCH_LIT[0] : TORCH_UNLIT[0];
        }
        String suffix = value.substring(value.lastIndexOf('_') + 1); // east/west/south/north
        return lit
                ? lookup(TORCH_LIT, prefix, suffix)
                : lookup(TORCH_UNLIT, prefix, suffix);
    }

    private static EngineBlockType lookup(EngineBlockType[] pair, String prefix, String suffix) {
        for (int i = 0; i < TORCH_WALL.length; i++) {
            if (TORCH_WALL[i].equals(suffix)) {
                return pair[1 + i];
            }
        }
        return pair[0];
    }

    /** The lit torch type standing on the floor (FACING up). */
    public static EngineBlockType torchStandingLit() {
        return TORCH_LIT[0];
    }

    /** The torch pair member for a reference FACING id (1=UP standing, 2..5 the walls). */
    public static EngineBlockType torchOfFacing(int facing, boolean lit) {
        if (facing <= 1) {
            return lit ? TORCH_LIT[0] : TORCH_UNLIT[0];
        }
        EngineBlockType[] pair = lit ? TORCH_LIT : TORCH_UNLIT;
        for (int i = 0; i < TORCH_WALL.length; i++) {
            if (TORCH_WALL_META[i] == facing) {
                return pair[1 + i];
            }
        }
        return pair[0];
    }

    // ------------------------------------------------------------------
    // The repeater family
    // ------------------------------------------------------------------

    /** @return whether the type is either repeater pair. */
    public static boolean isRepeater(net.zaminmc.torch.block.BlockType type) {
        if (type == null || !type.identifier().namespace().equals("minecraft")) {
            return false;
        }
        String value = type.identifier().value();
        return value.startsWith("repeater_") || value.startsWith("powered_repeater_");
    }

    /** @return whether the repeater type is the powered pair (legacy 94). */
    public static boolean repeaterPowered(net.zaminmc.torch.block.BlockType type) {
        return type.identifier().value().startsWith("powered_repeater_");
    }

    /** The repeater's FACING code (0=south..3=east, toward its INPUT). */
    public static int repeaterFacing(net.zaminmc.torch.block.BlockType type) {
        // repeater_<facing>_<delay> / powered_repeater_<facing>_<delay>:
        // strip the prefix, then the facing is the first segment.
        String value = type.identifier().value();
        String rest = value.startsWith("powered_")
                ? value.substring("powered_repeater_".length())
                : value.substring("repeater_".length());
        String facing = rest.substring(0, rest.indexOf('_'));
        for (int i = 0; i < FACING_NAMES.length; i++) {
            if (FACING_NAMES[i].equals(facing)) {
                return i;
            }
        }
        return 0;
    }

    /** The repeater's player-set delay (1..4, the reference's DELAY property). */
    public static int repeaterDelay(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        return Integer.parseInt(value.substring(value.lastIndexOf('_') + 1));
    }

    /** The repeater type for a state (facing 0-3, delay 1-4, powered pair). */
    public static EngineBlockType repeaterOf(int facing, int delay, boolean powered) {
        return REPEATERS[powered ? 1 : 0][facing & 3][(delay - 1) & 3];
    }

    /** The same repeater state with the powered pair swapped. */
    public static EngineBlockType repeaterOfState(net.zaminmc.torch.block.BlockType type, boolean powered) {
        return repeaterOf(repeaterFacing(type), repeaterDelay(type), powered);
    }

    /** The same repeater state with the delay swapped (the use cycle). */
    public static EngineBlockType repeaterWithDelay(net.zaminmc.torch.block.BlockType type, int delay) {
        return repeaterOf(repeaterFacing(type), delay, repeaterPowered(type));
    }

    // ------------------------------------------------------------------
    // The shared geometry helpers
    // ------------------------------------------------------------------

    /** The horizontal unit offset of a facing code (index 0-3). */
    public static int[] facingOffset(int facing) {
        return FACING_OFFSETS[facing & 3];
    }

    /** The opposite horizontal facing code. */
    public static int oppositeFacing(int facing) {
        return (facing + 2) & 3;
    }

    /** The clockwise-rotated horizontal facing code (south->west->north->east). */
    public static int rotateClockwise(int facing) {
        return (facing + 1) & 3;
    }

    /** The counter-clockwise-rotated horizontal facing code. */
    public static int rotateCounterClockwise(int facing) {
        return (facing + 3) & 3;
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    private static final EngineBlockType[] WIRE = buildWire();
    private static final EngineBlockType[] TORCH_LIT = buildTorch("redstone_torch");
    private static final EngineBlockType[] TORCH_UNLIT = buildTorch("unlit_redstone_torch");
    private static final EngineBlockType[][][] REPEATERS = buildRepeaters();

    // ------------------------------------------------------------------
    // The lever (legacy 69): FACING (8 attachment facings) x POWERED
    // ------------------------------------------------------------------

    /**
     * The lever's eight facing keys with their reference ids and attachment
     * directions (LeverBlock.Facing lines 284-300: down_x 0, east 1, west 2,
     * south 3, north 4, up_z 5, up_x 6, down_z 7 — the X/Z split rides the
     * player's look axis on the up/down mounts).
     */
    private static final String[] LEVER_KEYS = {"down_x", "east", "west", "south", "north", "up_z", "up_x", "down_z"};
    private static final int[] LEVER_ATTACHMENT = {0, 5, 4, 3, 2, 1, 1, 0}; // reference FACING ids: DOWN=0 EAST=5 WEST=4 SOUTH=3 NORTH=2 UP=1

    private static final EngineBlockType[] LEVER = buildLever();
    private static final EngineBlockType[] LEVER_POWERED = buildLeverPowered();

    private static EngineBlockType[] buildLever() {
        EngineBlockType[] lever = new EngineBlockType[8];
        for (int facing = 0; facing < 8; facing++) {
            lever[facing] = new EngineBlockType(
                    Identifier.parse("minecraft:lever_" + LEVER_KEYS[facing]),
                    "Lever", facing);
        }
        return lever;
    }

    private static EngineBlockType[] buildLeverPowered() {
        EngineBlockType[] lever = new EngineBlockType[8];
        for (int facing = 0; facing < 8; facing++) {
            lever[facing] = new EngineBlockType(
                    Identifier.parse("minecraft:lever_" + LEVER_KEYS[facing] + "_powered"),
                    "Lever", facing | 8);
        }
        return lever;
    }

    /** @return whether the type is any lever state. */
    public static boolean isLever(net.zaminmc.torch.block.BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && (type.identifier().value().startsWith("lever_"));
    }

    /** @return whether the lever state is the powered pair. */
    public static boolean leverPowered(net.zaminmc.torch.block.BlockType type) {
        return type.identifier().value().startsWith("lever_") && type.identifier().value().endsWith("_powered");
    }

    /** The lever's facing id (0..7, the reference's Facing.getId). */
    public static int leverFacing(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value().substring("lever_".length());
        String key = value.endsWith("_powered") ? value.substring(0, value.length() - "_powered".length()) : value;
        for (int i = 0; i < LEVER_KEYS.length; i++) {
            if (LEVER_KEYS[i].equals(key)) {
                return i;
            }
        }
        return 0;
    }

    /** The lever's attachment direction (the reference FACING id it mounts on). */
    public static int leverAttachment(net.zaminmc.torch.block.BlockType type) {
        return LEVER_ATTACHMENT[leverFacing(type)];
    }

    /** The lever state for a facing id + powered pair. */
    public static EngineBlockType leverOf(int facing, boolean powered) {
        return powered ? LEVER_POWERED[facing & 7] : LEVER[facing & 7];
    }

    // ------------------------------------------------------------------
    // The buttons (stone 77, wood 143): FACING (6) x POWERED
    // ------------------------------------------------------------------

    /** The six reference direction names in FACING-id order (down up north south west east). */
    private static final String[] DIRECTION_KEYS = {"down", "up", "north", "south", "west", "east"};

    private static final EngineBlockType[][] BUTTON = buildButtons();

    private static EngineBlockType[][] buildButtons() {
        EngineBlockType[][] buttons = new EngineBlockType[2][];
        for (int wooden = 0; wooden <= 1; wooden++) {
            EngineBlockType[] family = new EngineBlockType[6 * 2];
            int index = 0;
            for (int facing = 0; facing < 6; facing++) {
                family[index++] = new EngineBlockType(
                        Identifier.parse((wooden == 1 ? "minecraft:wooden_button_" : "minecraft:stone_button_")
                                + DIRECTION_KEYS[facing]),
                        wooden == 1 ? "Wooden Button" : "Stone Button", facing);
                family[index++] = new EngineBlockType(
                        Identifier.parse((wooden == 1 ? "minecraft:wooden_button_" : "minecraft:stone_button_")
                                + DIRECTION_KEYS[facing] + "_powered"),
                        wooden == 1 ? "Wooden Button" : "Stone Button", facing | 8);
            }
            buttons[wooden] = family;
        }
        return buttons;
    }

    /** @return whether the type is any button state (stone or wood). */
    public static boolean isButton(net.zaminmc.torch.block.BlockType type) {
        if (type == null || !type.identifier().namespace().equals("minecraft")) {
            return false;
        }
        String value = type.identifier().value();
        return value.startsWith("stone_button_") || value.startsWith("wooden_button_");
    }

    /** @return whether the button state is the powered pair. */
    public static boolean buttonPowered(net.zaminmc.torch.block.BlockType type) {
        return type.identifier().value().endsWith("_powered");
    }

    /** The button's facing (the reference FACING id — the side it mounts on). */
    public static int buttonFacing(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        String rest = value.startsWith("wooden_") ? value.substring("wooden_button_".length())
                : value.substring("stone_button_".length());
        String key = rest.endsWith("_powered") ? rest.substring(0, rest.length() - "_powered".length()) : rest;
        for (int i = 0; i < DIRECTION_KEYS.length; i++) {
            if (DIRECTION_KEYS[i].equals(key)) {
                return i;
            }
        }
        return 0;
    }

    /** @return whether the button is the wooden pair (the 30-tick release). */
    public static boolean buttonWooden(net.zaminmc.torch.block.BlockType type) {
        return type.identifier().value().startsWith("wooden_button_");
    }

    /** The button state for a facing + powered pair. */
    public static EngineBlockType buttonOf(int facing, boolean powered, boolean wooden) {
        return BUTTON[wooden ? 1 : 0][facing * 2 + (powered ? 1 : 0)];
    }

    // ------------------------------------------------------------------
    // The pressure plates (stone 70, wood 72): POWERED only
    // ------------------------------------------------------------------

    private static final EngineBlockType[] PLATE = {
            new EngineBlockType(Identifier.parse("minecraft:stone_pressure_plate"), "Stone Pressure Plate", 0),
            new EngineBlockType(Identifier.parse("minecraft:stone_pressure_plate_powered"), "Stone Pressure Plate", 1),
            new EngineBlockType(Identifier.parse("minecraft:wooden_pressure_plate"), "Wooden Pressure Plate", 0),
            new EngineBlockType(Identifier.parse("minecraft:wooden_pressure_plate_powered"), "Wooden Pressure Plate", 1),
    };

    /** @return whether the type is any pressure plate state. */
    public static boolean isPlate(net.zaminmc.torch.block.BlockType type) {
        if (type == null || !type.identifier().namespace().equals("minecraft")) {
            return false;
        }
        String value = type.identifier().value();
        return value.startsWith("stone_pressure_plate") || value.startsWith("wooden_pressure_plate");
    }

    /** @return whether the plate state is the powered pair. */
    public static boolean platePowered(net.zaminmc.torch.block.BlockType type) {
        return type.identifier().value().endsWith("_powered");
    }

    /** @return whether the plate is the wooden pair (the EVERYTHING rule). */
    public static boolean plateWooden(net.zaminmc.torch.block.BlockType type) {
        return type.identifier().value().startsWith("wooden_pressure_plate");
    }

    /** The plate state for a powered pair. */
    public static EngineBlockType plateOf(boolean powered, boolean wooden) {
        return PLATE[(wooden ? 2 : 0) + (powered ? 1 : 0)];
    }

    private static EngineBlockType[] buildWire() {
        EngineBlockType[] wire = new EngineBlockType[16];
        for (int power = 0; power <= MAX_POWER; power++) {
            Identifier id = power == 0
                    ? Identifier.parse("minecraft:redstone_wire")
                    : Identifier.parse("minecraft:redstone_wire_" + power);
            wire[power] = new EngineBlockType(id, "Redstone Wire", power);
        }
        return wire;
    }

    private static EngineBlockType[] buildTorch(String prefix) {
        EngineBlockType[] torch = new EngineBlockType[5];
        torch[0] = new EngineBlockType(Identifier.parse("minecraft:" + prefix),
                "Redstone Torch", 1);
        for (int i = 0; i < TORCH_WALL.length; i++) {
            torch[1 + i] = new EngineBlockType(
                    Identifier.parse("minecraft:" + prefix + "_" + TORCH_WALL[i]),
                    "Redstone Torch", i + 2);
        }
        return torch;
    }

    private static EngineBlockType[][][] buildRepeaters() {
        EngineBlockType[][][] all = new EngineBlockType[2][4][4];
        for (int facing = 0; facing < 4; facing++) {
            for (int delay = 1; delay <= 4; delay++) {
                all[0][facing][delay - 1] = new EngineBlockType(
                        Identifier.parse("minecraft:repeater_" + FACING_NAMES[facing] + "_" + delay),
                        "Repeater", facing | ((delay - 1) << 2));
                all[1][facing][delay - 1] = new EngineBlockType(
                        Identifier.parse("minecraft:powered_repeater_" + FACING_NAMES[facing] + "_" + delay),
                        "Repeater", facing | ((delay - 1) << 2));
            }
        }
        return all;
    }

    /** Registers every redstone type into the block registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType type : WIRE) {
            builder.register(type);
        }
        for (EngineBlockType type : TORCH_LIT) {
            builder.register(type);
        }
        for (EngineBlockType type : TORCH_UNLIT) {
            builder.register(type);
        }
        List<EngineBlockType> repeaters = new ArrayList<>();
        for (EngineBlockType[][] facing : REPEATERS) {
            for (EngineBlockType[] delays : facing) {
                repeaters.addAll(java.util.Arrays.asList(delays));
            }
        }
        for (EngineBlockType type : repeaters) {
            builder.register(type);
        }
        for (EngineBlockType type : LEVER) {
            builder.register(type);
        }
        for (EngineBlockType type : LEVER_POWERED) {
            builder.register(type);
        }
        for (EngineBlockType[] family : BUTTON) {
            for (EngineBlockType type : family) {
                builder.register(type);
            }
        }
        for (EngineBlockType type : PLATE) {
            builder.register(type);
        }
        return builder;
    }
}
