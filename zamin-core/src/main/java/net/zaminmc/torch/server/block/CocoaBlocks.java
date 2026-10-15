package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The cocoa family (Slice 43 — CocoaBlock 1:1): the pod 127
 * ({@code setStrength(0.2F).setBlastResistance(5.0F)} WOOD_SOUNDS over
 * Material.PLANT) — the three AGE stages over the four horizontal
 * facings, mounted on the jungle log the FACING names.
 *
 * <p>The state vocabulary is the reference's own
 * {@code getStateFromMetadata} band: the horizontal facing (S=0 W=1 N=2
 * E=3) with the AGE over bits 2-3 — twelve states. The bare form carries
 * the south/age-0 placement shape; the pods have NO items (the creative
 * pick yields the brown dye, the planting rides the dye's own use arm).</p>
 */
public final class CocoaBlocks {

    private CocoaBlocks() {
    }

    /** The wire id (Block.java's own row). */
    public static final int WIRE_ID = 127;

    /** The horizontal facing band over metadata 0-3. */
    static final List<String> FACINGS = List.of("south", "west", "north", "east");

    /** The jungle log the pods mount on (isSupported's own consult). */
    public static final Identifier JUNGLE_LOG = Identifier.parse("minecraft:jungle_log");

    /** The brown dye's damage in the dye family's domain (DyeColor.BROWN's 12). */
    public static final int BROWN_DYE_DAMAGE = 12;

    /** One state's identifier: the bare south/age-0 form keeps the plain name. */
    private static Identifier stateId(int facing, int age) {
        StringBuilder value = new StringBuilder("cocoa");
        if (age > 0) {
            value.append("_age").append(age);
        }
        if (facing != 0) {
            value.append('_').append(FACINGS.get(facing));
        }
        return Identifier.parse("minecraft:" + value);
    }

    /** One state (the metadata band's exact member). */
    public static EngineBlockType state(int facing, int age) {
        return new EngineBlockType(stateId(facing, age), "Cocoa", metaOf(facing, age));
    }

    /** The reference's getMetadataFromState: facing | age &lt;&lt; 2. */
    public static int metaOf(int facing, int age) {
        return (facing & 3) | (age << 2);
    }

    /** Every registration this family owns (the 12 states). */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (int facing = 0; facing < 4; facing++) {
            for (int age = 0; age <= 2; age++) {
                blocks.add(state(facing, age));
            }
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads
    // ------------------------------------------------------------------

    /** @return whether the type is a cocoa state. */
    public static boolean isCocoa(net.zaminmc.torch.block.BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && (type.identifier().value().equals("cocoa")
                || type.identifier().value().startsWith("cocoa_"));
    }

    /** The state's facing band (0=S, 1=W, 2=N, 3=E). */
    public static int facingOf(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        for (int i = 0; i < FACINGS.size(); i++) {
            if (value.endsWith("_" + FACINGS.get(i))) {
                return i;
            }
        }
        return 0;
    }

    /** The state's AGE band (0-2). */
    public static int ageOf(net.zaminmc.torch.block.BlockType type) {
        String value = type.identifier().value();
        if (value.contains("_age1")) {
            return 1;
        }
        return value.contains("_age2") ? 2 : 0;
    }

    /** The placement state (getPlacementState: FACING = the clicked face's
     *  OPPOSITE — the pod faces back at its log — AGE 0). */
    public static EngineBlockType placementState(int clickedFace) {
        // The engine's face band (2=N, 3=S, 4=W, 5=E) turned opposite over
        // the horizontal band's own order (S=0, W=1, N=2, E=3).
        int facing = switch (clickedFace) {
            case 2 -> 0; // clicked the log's north face: the pod faces south, back at it
            case 3 -> 2; // clicked the south face: the pod faces north
            case 4 -> 3; // clicked the west face: the pod faces east
            default -> 1; // clicked the east face: the pod faces west
        };
        return state(facing, 0);
    }

    /** The growth write (tick's AGE advance, the bone-meal's +1). */
    public static net.zaminmc.torch.block.BlockType withAge(
            net.zaminmc.torch.block.BlockType type, int age) {
        return state(facingOf(type), Math.max(0, Math.min(2, age)));
    }

    /**
     * The support read (isSupported lines 53-58): the FACING cell must be
     * the jungle log.
     */
    public static boolean isSupported(net.zaminmc.torch.server.world.EngineWorld world,
            net.zaminmc.torch.block.BlockPosition pos, int facing) {
        int[] step = switch (facing) {
            case 0 -> new int[] {0, 1};    // facing south: the log south of the pod
            case 1 -> new int[] {-1, 0};   // facing west: the log west
            case 2 -> new int[] {0, -1};   // facing north: the log north
            default -> new int[] {1, 0};   // facing east: the log east
        };
        return world.getBlock(pos.offset(step[0], 0, step[1])).identifier().equals(JUNGLE_LOG);
    }
}
