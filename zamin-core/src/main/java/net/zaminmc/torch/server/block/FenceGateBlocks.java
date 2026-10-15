package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The six fence-gate families (Slice 37 — FenceGateBlock.java over
 * PlanksBlock.Variant's domain): the oak gate 107 and the five 1.7 woods'
 * gates (spruce 183, birch 184, jungle 185, dark_oak 186, acacia 187 —
 * Block.java's own rows, every one {@code setStrength(2.0F)
 * setBlastResistance(5.0F)} WOOD_SOUNDS).
 *
 * <p>The state vocabulary is the reference's own
 * {@code getStateFromMetadata} band: the horizontal facing
 * ({@code Direction.byIdHorizontal}, south 0 / west 1 / north 2 / east 3)
 * with the OPEN bit 4 and the POWERED bit 8 — sixteen states per family.
 * The IN_WALL virtual property (FenceGateBlock.resolveVirtualProperties —
 * the 1.0-height flush form between cobblestone walls) is a computed
 * render-time read in the reference and stays client-side; the
 * collision-shape table carries the closed gate's own 1.5-tall 3/8 panel
 * ({@code CollisionShapes}, 1:1) and no box at all when open.</p>
 */
public final class FenceGateBlocks {

    private FenceGateBlocks() {
    }

    /** The gate families: name -> wire id (Block.java's own rows). */
    public static final Map<String, Integer> GATES = buildGates();

    /** The horizontal facing band over metadata 0-3 (the reference's own order). */
    static final List<String> FACINGS = List.of("south", "west", "north", "east");

    private static Map<String, Integer> buildGates() {
        java.util.LinkedHashMap<String, Integer> gates = new java.util.LinkedHashMap<>();
        gates.put("oak", 107);
        gates.put("spruce", 183);
        gates.put("birch", 184);
        gates.put("jungle", 185);
        gates.put("dark_oak", 186);
        gates.put("acacia", 187);
        return Map.copyOf(gates);
    }

    /** The display name of a family's gate. */
    private static String displayOf(String family) {
        String[] parts = family.split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return name.append("Fence Gate").toString();
    }

    /**
     * One state's identifier: the bare closed south form keeps the plain
     * name (the family's item shares it — Item.java's own
     * {@code register(Blocks.FENCE_GATE)} rows), everything else the
     * compound suffix band ({@code _west}, {@code _open_north},
     * {@code _powered_open_east} ...).
     */
    private static Identifier stateId(String family, int facing, boolean open, boolean powered) {
        StringBuilder value = new StringBuilder(family).append("_fence_gate");
        if (powered) {
            value.append("_powered");
        }
        if (open) {
            value.append("_open");
        }
        if (facing != 0) {
            value.append('_').append(FACINGS.get(facing));
        }
        return Identifier.parse("minecraft:" + value);
    }

    /** One state of a family (the metadata band's exact member). */
    public static EngineBlockType state(String family, int facing, boolean open, boolean powered) {
        return new EngineBlockType(stateId(family, facing, open, powered),
                displayOf(family), metaOf(facing, open, powered));
    }

    /** The reference's getMetadataFromState: facing | open 4 | powered 8. */
    public static int metaOf(int facing, boolean open, boolean powered) {
        return (facing & 3) | (open ? 4 : 0) | (powered ? 8 : 0);
    }

    /** Every registration this family owns (the 96 states). */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (String family : GATES.keySet()) {
            for (int facing = 0; facing < 4; facing++) {
                for (boolean open : new boolean[] {false, true}) {
                    for (boolean powered : new boolean[] {false, true}) {
                        blocks.add(state(family, facing, open, powered));
                    }
                }
            }
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads (the family helpers the engine's arms consult)
    // ------------------------------------------------------------------

    /** @return whether the type is any gate family's state. */
    public static boolean isGate(net.zaminmc.torch.block.BlockType type) {
        return familyOf(type) != null;
    }

    /** @return the gate family of a type (item or state), or null. */
    public static String familyOf(net.zaminmc.torch.block.BlockType type) {
        if (!type.identifier().namespace().equals("minecraft")) {
            return null;
        }
        String value = type.identifier().value();
        int gate = value.indexOf("_fence_gate");
        if (gate < 0) {
            return null;
        }
        String family = value.substring(0, gate);
        return GATES.containsKey(family) ? family : null;
    }

    /** The state's facing band (0=S, 1=W, 2=N, 3=E; the bare form's south 0). */
    public static int facingOf(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        int gate = value.indexOf("_fence_gate");
        String tail = value.substring(gate + "_fence_gate".length());
        int facing = 0;
        for (int i = 0; i < FACINGS.size(); i++) {
            if (tail.endsWith("_" + FACINGS.get(i))) {
                facing = i;
                break;
            }
        }
        return facing;
    }

    /** The state's OPEN bit (use()'s flip arm — the walk-through band). */
    public static boolean isOpen(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        int gate = value.indexOf("_fence_gate");
        return value.substring(gate + "_fence_gate".length()).contains("_open");
    }

    /** The state's POWERED bit (the signal arm's memory). */
    public static boolean isPowered(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        int gate = value.indexOf("_fence_gate");
        return value.substring(gate + "_fence_gate".length()).contains("_powered");
    }

    /**
     * use()'s open arm (FenceGateBlock.use lines 94-110): the flip keeps
     * facing and POWERED; opening from the far side ALSO flips the facing
     * to the player's look (the {@code state.get(FACING) ==
     * direction.getOpposite()} rewrite the caller performs first).
     */
    public static net.zaminmc.torch.block.BlockType with(
            net.zaminmc.torch.block.BlockType type, int facing, boolean open) {
        String family = familyOf(type);
        if (family == null) {
            return type;
        }
        return state(family, facing, open, isPowered(type));
    }

    /** The signal arm's write: facing and open kept, POWERED rewritten. */
    public static net.zaminmc.torch.block.BlockType withPowered(
            net.zaminmc.torch.block.BlockType type, boolean powered) {
        String family = familyOf(type);
        if (family == null) {
            return type;
        }
        return state(family, facingOf(type), isOpen(type), powered);
    }

    /** The signal arm's write: POWERED and open together (the edge arm). */
    public static net.zaminmc.torch.block.BlockType withPoweredOpen(
            net.zaminmc.torch.block.BlockType type, boolean powered, boolean open) {
        String family = familyOf(type);
        if (family == null) {
            return type;
        }
        return state(family, facingOf(type), open, powered);
    }

    /**
     * @return whether the item identifier string (the full form
     * "minecraft:oak_fence_gate" or the bare "oak_fence_gate") is a gate
     * family's item.
     */
    public static boolean isGateItem(String identifier) {
        if (identifier == null) {
            return false;
        }
        String value = identifier.startsWith("minecraft:")
                ? identifier.substring("minecraft:".length()) : identifier;
        if (!value.endsWith("_fence_gate")) {
            return false;
        }
        return GATES.containsKey(value.substring(0, value.length() - "_fence_gate".length()));
    }

    /** The family's item identifier (the bare name — the family's meta-0 state's twin). */
    public static Identifier gateItem(String family) {
        return Identifier.parse("minecraft:" + family + "_fence_gate");
    }

    /** The placement state (getPlacementState): the look's own facing, closed, unpowered. */
    public static net.zaminmc.torch.block.BlockType placementState(String family, int lookBand) {
        return state(family, lookBand & 3, false, false);
    }
}
