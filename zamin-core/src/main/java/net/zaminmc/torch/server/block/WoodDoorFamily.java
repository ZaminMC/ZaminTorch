package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.List;
import java.util.Locale;

/**
 * The complete 1.8.8 door family over {@code DoorBlock.java} +
 * {@code DoorItem.java}: the six wood doors (oak 64, spruce 193, birch 194,
 * jungle 195, acacia 196, dark_oak 197) and the iron door (71).
 *
 * <p>The reference's metadata layout (DoorBlock.getMetadataFromState /
 * getStateFromMetadata, lines 300-333): the lower half carries the facing
 * bits 0-1 plus the open bit 4; the upper half carries the half bit 8, the
 * right-hinge bit 1, and the powered bit 2. The client renders facing and
 * open from the LOWER cell (resolveVirtualProperties pulls them up from
 * below) and the hinge from the upper's bit 0 — the engine's flattened
 * per-state model mirrors exactly that split: every observable state is its
 * own block type with the reference's own metadata nibble.</p>
 *
 * <p>The engine's historical state-name band (the {@code _north},
 * {@code _east}, {@code _south} suffixes) follows the shipped oak shape;
 * the metadata each name carries is the byte the vanilla client decodes
 * (DoorBlock.getStateFromMetadata: {@code meta & 3} is the horizontal id of
 * the facing turned clockwise — S/W/N/E = 0/1/2/3), which the placement
 * math in {@code EngineServer.placeDoorOnTick} writes 1:1 from the
 * player's yaw band.</p>
 *
 * <p>The powered bit lives on the upper exactly as the reference encodes
 * it (bit 2, metadata 10/11/14/15) and stays INDEPENDENT of the open bit
 * (DoorBlock.neighborChanged's edge test reads POWERED, use() writes OPEN
 * only): a manually-opened door carries an unpowered open upper (metadata
 * 12/13 — the engine-internal open flag rides bit 4, a bit the 1.8.8
 * client ignores on upper halves), a signal-held door an open POWERED
 * upper (14/15), and a signal-open door manually closed keeps its powered
 * bit until the signal departs. Only (closed, unpowered) and those two
 * open forms persist — the reference's neighborChanged writes (powered,
 * open) together on a signal edge, so the closed-powered pair never
 * commits and the family does not register it.</p>
 */
public final class WoodDoorFamily {

    /** The seven families in registration order (the item ids match). */
    public static final List<String> FAMILIES =
            List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "iron");

    /** The lower closed states per family, indexed by the metadata band 0..3. */
    private static final EngineBlockType[][] LOWER_CLOSED = new EngineBlockType[7][];
    /** The lower open states per family, indexed by the metadata band 0..3. */
    private static final EngineBlockType[][] LOWER_OPEN = new EngineBlockType[7][];
    /**
     * The upper states per family — the full hinge × powered × open grid
     * (DoorBlock keeps the two bits independent: use() writes OPEN only,
     * the signal arm writes both, so every combination is reachable — the
     * manual close of a signal-held door persists as closed-POWERED until
     * the signal departs). Index: powered<<2 | open<<1 | rightHinge.
     * Wire metas: 8 | right 1 | powered 2 | open 4 (the engine's own open
     * flag rides bit 4, which the 1.8.8 client ignores on upper halves).
     */
    private static final EngineBlockType[][] UPPER = new EngineBlockType[7][];

    static {
        for (int f = 0; f < FAMILIES.size(); f++) {
            String wood = FAMILIES.get(f);
            boolean iron = wood.equals("iron");
            // The lower name band: oak-style woods name metadata 0 bare
            // ("oak_door"); the iron door's shipped shape names it "west".
            String meta0 = iron ? wood + "_door_west" : wood + "_door";
            String meta1 = wood + "_door_north";
            String meta2 = wood + "_door_east";
            String meta3 = wood + "_door_south";
            String open0 = wood + "_door_open" + (iron ? "_west" : "");
            LOWER_CLOSED[f] = new EngineBlockType[] {
                    state(meta0, 0), state(meta1, 1), state(meta2, 2), state(meta3, 3)};
            LOWER_OPEN[f] = new EngineBlockType[] {
                    state(open0, 4),
                    state(wood + "_door_open_north", 5),
                    state(wood + "_door_open_east", 6),
                    state(wood + "_door_open_south", 7)};
            // The upper grid: hinge × powered × open (all eight — the
            // reference's two bits stay independent across use()/signal).
            UPPER[f] = new EngineBlockType[] {
                    state(wood + "_door_upper", 8),                       // closed, unpowered, left
                    state(wood + "_door_upper_right", 9),                  // closed, unpowered, right
                    state(wood + "_door_upper_powered", 10),               // closed, powered, left
                    state(wood + "_door_upper_right_powered", 11),         // closed, powered, right
                    state(wood + "_door_upper_open", 12),                   // open, unpowered, left
                    state(wood + "_door_upper_right_open", 13),             // open, unpowered, right
                    state(wood + "_door_upper_powered_open", 14),           // open, powered, left
                    state(wood + "_door_upper_right_powered_open", 15)};    // open, powered, right
        }
        // The shipped oak and iron constants stay authoritative for their
        // identifiers — the family tables resolve to the very instances the
        // registry freezes, so lookups and registrations agree.
        LOWER_CLOSED[0] = new EngineBlockType[] {
                BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_W, BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_N,
                BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_E, BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_S};
        LOWER_OPEN[0] = new EngineBlockType[] {
                BuiltinBlocks.OAK_DOOR_LOWER_OPEN_W, BuiltinBlocks.OAK_DOOR_LOWER_OPEN_N,
                BuiltinBlocks.OAK_DOOR_LOWER_OPEN_E, BuiltinBlocks.OAK_DOOR_LOWER_OPEN_S};
        UPPER[0] = new EngineBlockType[] {
                BuiltinBlocks.OAK_DOOR_UPPER, upperFresh(0, 1), upperFresh(0, 2), upperFresh(0, 3),
                BuiltinBlocks.OAK_DOOR_UPPER_OPEN, upperFresh(0, 5), upperFresh(0, 6), upperFresh(0, 7)};
        UPPER[6] = new EngineBlockType[] {
                BuiltinBlocks.IRON_DOOR_UPPER, upperFresh(6, 1), upperFresh(6, 2), upperFresh(6, 3),
                upperFresh(6, 4), upperFresh(6, 5), upperFresh(6, 6), upperFresh(6, 7)};
        LOWER_CLOSED[6] = new EngineBlockType[] {
                BuiltinBlocks.IRON_DOOR_LOWER_W, BuiltinBlocks.IRON_DOOR_LOWER_N,
                BuiltinBlocks.IRON_DOOR_LOWER_E, BuiltinBlocks.IRON_DOOR_LOWER_S};
    }

    /** The loop-built upper of a family/index (the static-init reader). */
    private static EngineBlockType upperFresh(int familyIndex, int index) {
        return UPPER[familyIndex][index];
    }

    private WoodDoorFamily() {
    }

    /** A door state's display name. */
    private static String display(String wood) {
        String[] parts = wood.split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return name.append("Door").toString().trim();
    }

    private static EngineBlockType state(String value, int legacyMetadata) {
        return new EngineBlockType(Identifier.parse("minecraft:" + value),
                display(value.substring(0, value.indexOf("_door"))), legacyMetadata);
    }

    /** Registers every door state the family owns (the new ids only — the
     *  shipped oak/iron constants ride BuiltinBlocks' own registration). */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (int f = 0; f < FAMILIES.size(); f++) {
            String wood = FAMILIES.get(f);
            if (wood.equals("oak")) {
                // Oak's eight lowers and its closed/plain-open uppers are
                // shipped; the hinge pair, the powered pair, and the
                // right/power combinations are new.
                for (int u : new int[] {1, 2, 3, 5, 6, 7}) {
                    builder = builder.register(UPPER[0][u]);
                }
            } else if (wood.equals("iron")) {
                // The iron door's four closed lowers and its plain upper
                // are shipped; the open band and the seven upper variants
                // are new.
                for (int meta = 0; meta < 4; meta++) {
                    builder = builder.register(LOWER_OPEN[6][meta]);
                }
                for (int u = 1; u < 8; u++) {
                    builder = builder.register(UPPER[6][u]);
                }
            } else {
                for (int meta = 0; meta < 4; meta++) {
                    builder = builder.register(LOWER_CLOSED[f][meta])
                            .register(LOWER_OPEN[f][meta]);
                }
                for (int u = 0; u < 8; u++) {
                    builder = builder.register(UPPER[f][u]);
                }
            }
        }
        return builder;
    }

    /** Whether the type belongs to any door family (either half). */
    public static boolean isDoor(net.zaminmc.torch.block.BlockType type) {
        return type != null && familyIndex(type.identifier()) >= 0;
    }

    /** Whether the identifier belongs to any door family (either half). */
    public static boolean isDoor(Identifier identifier) {
        return familyIndex(identifier) >= 0;
    }

    /** The family index (0..6) of a door identifier, or -1. */
    private static int familyIndex(Identifier identifier) {
        if (!identifier.namespace().equals("minecraft")) {
            return -1;
        }
        String value = identifier.value();
        for (int f = 0; f < FAMILIES.size(); f++) {
            String wood = FAMILIES.get(f);
            if (value.equals(wood + "_door") || value.startsWith(wood + "_door_")) {
                return f;
            }
        }
        return -1;
    }

    /** The family name ("oak".."dark_oak", "iron") of a door type. */
    public static String familyOf(net.zaminmc.torch.block.BlockType type) {
        int index = familyIndex(type.identifier());
        return index < 0 ? null : FAMILIES.get(index);
    }

    /** The legacy metadata nibble of a door state's identifier (resolved
     *  against the family's own tables — the shipped constants and the
     *  family's states agree byte for byte). */
    public static int metaOf(Identifier identifier) {
        int f = familyIndex(identifier);
        if (f < 0) {
            return -1;
        }
        for (int meta = 0; meta < 4; meta++) {
            if (LOWER_CLOSED[f][meta].identifier().equals(identifier)) {
                return LOWER_CLOSED[f][meta].legacyMetadata();
            }
            if (LOWER_OPEN[f][meta].identifier().equals(identifier)) {
                return LOWER_OPEN[f][meta].legacyMetadata();
            }
        }
        for (EngineBlockType upper : UPPER[f]) {
            if (upper.identifier().equals(identifier)) {
                return upper.legacyMetadata();
            }
        }
        return -1;
    }

    /** Whether the door type is an upper half (the metadata's bit 8). */
    public static boolean isUpper(net.zaminmc.torch.block.BlockType type) {
        int meta = metaOf(type.identifier());
        return meta >= 8;
    }

    /** Whether the door type is an OPEN half (the lower's open bit; the
     *  uppers of swinging doors carry the same flag in engine-internal
     *  bit 4, a bit the 1.8.8 client ignores on upper halves). */
    public static boolean isOpen(net.zaminmc.torch.block.BlockType type) {
        int meta = metaOf(type.identifier());
        return meta >= 0 && (meta & 4) != 0;
    }

    /** Whether the upper half hangs on the right hinge (bit 0 of its meta). */
    public static boolean isRightHinge(net.zaminmc.torch.block.BlockType type) {
        int meta = metaOf(type.identifier());
        return meta >= 8 && (meta & 1) != 0;
    }

    /** Whether the upper half carries the POWERED bit (metadata bit 2 —
     *  the edge-test memory DoorBlock.neighborChanged reads; the use arm
     *  never writes it, the signal arm does). */
    public static boolean isPowered(net.zaminmc.torch.block.BlockType upperType) {
        int meta = metaOf(upperType.identifier());
        return meta >= 8 && (meta & 2) != 0;
    }

    /** Whether the door family is iron (the use arm's refusal). */
    public static boolean isIron(String family) {
        return "iron".equals(family);
    }

    /** The lower state for a family by the facing band and open flag. */
    public static EngineBlockType lower(String family, int metaBand, boolean open) {
        EngineBlockType[] band = open ? LOWER_OPEN[FAMILIES.indexOf(family)]
                : LOWER_CLOSED[FAMILIES.indexOf(family)];
        return band[metaBand & 3];
    }

    /** The matching upper state for a family by hinge and the two
     *  independent bits the reference carries (OPEN and POWERED — use()
     *  writes OPEN only, the signal arm writes both, so every combination
     *  of the grid is reachable). Index over the array's own order
     *  (the closed pair, the powered pair, the open pair, the both pair):
     *  open<<2 | powered<<1 | rightHinge. */
    public static EngineBlockType upper(String family, boolean rightHinge,
                                        boolean open, boolean powered) {
        int index = (open ? 4 : 0) | (powered ? 2 : 0) | (rightHinge ? 1 : 0);
        return UPPER[FAMILIES.indexOf(family)][index];
    }

    /** The open-mirror of a lower state (the toggle's write). */
    public static EngineBlockType flipLower(net.zaminmc.torch.block.BlockType lower) {
        int family = familyIndex(lower.identifier());
        int metaBand = metaOf(lower.identifier()) & 3;
        boolean open = isOpen(lower);
        return open ? LOWER_CLOSED[family][metaBand] : LOWER_OPEN[family][metaBand];
    }

    /** The door item identifier of a family ("oak_door".."iron_door"). */
    public static Identifier doorItem(String family) {
        return Identifier.parse("minecraft:" + family + "_door");
    }

    /** Whether the full item identifier string ("minecraft:oak_door") is
     *  a door item of any family. */
    public static boolean isDoorItem(String fullIdentifier) {
        if (!fullIdentifier.startsWith("minecraft:")) {
            return false;
        }
        String value = fullIdentifier.substring("minecraft:".length());
        for (String wood : FAMILIES) {
            if (value.equals(wood + "_door")) {
                return true;
            }
        }
        return false;
    }

    /** The family's display name ("Dark Oak Door"). */
    public static String displayName(String family) {
        return display(family);
    }
}
