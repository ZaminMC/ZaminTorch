package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The trapdoor pair (Slice 39 — TrapdoorBlock 1:1 over the reference's
 * registrations): the wooden trapdoor 96 (Material.WOOD,
 * {@code setStrength(3.0F)} WOOD_SOUNDS) and the iron trapdoor 167
 * (Material.IRON, {@code setStrength(5.0F)} METAL_SOUNDS — the pickaxe
 * class's own gate).
 *
 * <p>The state vocabulary is the reference's own
 * {@code getStateFromMetadata} band: the horizontal facing over metadata
 * 0-3 (getFacing: north 0, south 1, west 2, east 3) with the OPEN bit 4
 * and the TOP-half bit 8 — sixteen states per family. Both halves carry
 * their own collision panel in the collision-shape table (the closed 3/16
 * floor/ceiling panel, the open vertical one against the wall —
 * {@code CollisionShapes.trapdoorBox}, 1:1).</p>
 */
public final class TrapdoorBlocks {

    private TrapdoorBlocks() {
    }

    /** The pair: name -> wire id (Block.java's own rows). */
    public static final Map<String, Integer> TRAPDOORS = Map.of(
            "trapdoor", 96,
            "iron_trapdoor", 167);

    /** The horizontal facing band over metadata 0-3 (the reference's getFacing). */
    static final List<String> FACINGS = List.of("north", "south", "west", "east");

    private static String displayOf(String family) {
        return family.equals("iron_trapdoor") ? "Iron Trapdoor" : "Trapdoor";
    }

    /** One state's identifier: the bare closed bottom north form keeps the
     *  plain name (the family item shares it — Item.java's own rows 434/503). */
    private static Identifier stateId(String family, int facing, boolean open, boolean top) {
        StringBuilder value = new StringBuilder(family);
        if (open) {
            value.append("_open");
        }
        if (top) {
            value.append("_top");
        }
        if (facing != 0) {
            value.append('_').append(FACINGS.get(facing));
        }
        return Identifier.parse("minecraft:" + value);
    }

    /** One state of a family (the metadata band's exact member). */
    public static EngineBlockType state(String family, int facing, boolean open, boolean top) {
        return new EngineBlockType(stateId(family, facing, open, top),
                displayOf(family), metaOf(facing, open, top));
    }

    /** The reference's getMetadataFromState: facing | open 4 | top 8. */
    public static int metaOf(int facing, boolean open, boolean top) {
        return (facing & 3) | (open ? 4 : 0) | (top ? 8 : 0);
    }

    /** Every registration this family owns (the 32 states). */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (String family : TRAPDOORS.keySet()) {
            for (int facing = 0; facing < 4; facing++) {
                for (boolean open : new boolean[] {false, true}) {
                    for (boolean top : new boolean[] {false, true}) {
                        blocks.add(state(family, facing, open, top));
                    }
                }
            }
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads
    // ------------------------------------------------------------------

    /** @return whether the type is a trapdoor family's state. */
    public static boolean isTrapdoor(net.zaminmc.torch.block.BlockType type) {
        return familyOf(type) != null;
    }

    /** @return the trapdoor family of a type (item or state), or null. */
    public static String familyOf(net.zaminmc.torch.block.BlockType type) {
        if (type == null || !type.identifier().namespace().equals("minecraft")) {
            return null;
        }
        String value = type.identifier().value();
        for (String family : TRAPDOORS.keySet()) {
            if (value.equals(family) || value.startsWith(family + "_")) {
                return family;
            }
        }
        return null;
    }

    /** @return whether the family is the iron pair (the material refusal). */
    public static boolean isIron(String family) {
        return "iron_trapdoor".equals(family);
    }

    /** The state's facing band (0=N, 1=S, 2=W, 3=E; the bare form's north 0). */
    public static int facingOf(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        String family = familyOf(type);
        if (family == null) {
            return 0;
        }
        String tail = value.substring(family.length());
        for (int i = 0; i < FACINGS.size(); i++) {
            if (tail.endsWith("_" + FACINGS.get(i))) {
                return i;
            }
        }
        return 0;
    }

    /** The state's OPEN bit. */
    public static boolean isOpen(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        String family = familyOf(type);
        return family != null
                && value.substring(family.length()).contains("_open");
    }

    /** The state's TOP-half bit. */
    public static boolean isTop(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        String family = familyOf(type);
        return family != null
                && value.substring(family.length()).contains("_top");
    }

    /** use()'s flip (state.next(OPEN)): facing, half and family kept. */
    public static net.zaminmc.torch.block.BlockType flipOpen(
            net.zaminmc.torch.block.BlockType type) {
        String family = familyOf(type);
        if (family == null) {
            return type;
        }
        return state(family, facingOf(type), !isOpen(type), isTop(type));
    }

    /** The signal arm's write: OPEN := flag, the rest kept. */
    public static net.zaminmc.torch.block.BlockType withOpen(
            net.zaminmc.torch.block.BlockType type, boolean open) {
        String family = familyOf(type);
        if (family == null) {
            return type;
        }
        return state(family, facingOf(type), open, isTop(type));
    }

    /** @return whether the identifier string is a trapdoor family's item. */
    public static boolean isTrapdoorItem(String identifier) {
        if (identifier == null) {
            return false;
        }
        String value = identifier.startsWith("minecraft:")
                ? identifier.substring("minecraft:".length()) : identifier;
        return TRAPDOORS.containsKey(value);
    }

    /** The family's item identifier. */
    public static Identifier trapdoorItem(String family) {
        return Identifier.parse("minecraft:" + family);
    }
}
