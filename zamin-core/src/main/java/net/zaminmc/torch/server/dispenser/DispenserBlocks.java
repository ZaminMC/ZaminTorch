package net.zaminmc.torch.server.dispenser;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The dispenser and dropper families' block types (Slice 11a — the flattened
 * state model, reference/1.8.8 block/DispenserBlock.java +
 * block/DropperBlock.java): legacy 23 the dispenser, 158 the dropper (the
 * id repaired in Slice 32 — the original 125 was the double wooden slab's), six
 * FACING values and a TRIGGERED pair per facing. The nibble is the
 * reference's metadata {@code facing | (triggered ? 8 : 0)}
 * (DispenserBlock lines 202-215: getDirection reads {@code id & 7},
 * TRIGGERED reads {@code (metadata & 8) > 0}).
 *
 * <p>The FACING is the DISPENSE side — the item leaves toward it. The
 * placement points it the piston way (PistonBaseBlock.getFacingForPlacement:
 * the eye-height rule when the player stands close, else the look opposite),
 * and a neighbor signal raises the TRIGGERED pair for the 4-tick reaction
 * (getTickRate line 44).</p>
 */
public final class DispenserBlocks {

    /** The reference direction names in FACING-id order. */
    static final String[] DIRECTION_KEYS = {"down", "up", "north", "south", "west", "east"};

    private static final EngineBlockType[][] DISPENSERS = buildStates("dispenser");
    private static final EngineBlockType[][] DROPPERS = buildStates("dropper");

    private DispenserBlocks() {
    }

    /** @return whether the type is any dispenser state. */
    public static boolean isDispenser(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().startsWith("dispenser_");
    }

    /** @return whether the type is any dropper state. */
    public static boolean isDropper(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().startsWith("dropper_");
    }

    /** @return whether the type belongs to either family. */
    public static boolean isFamily(BlockType type) {
        return isDispenser(type) || isDropper(type);
    }

    /** The dispense-side FACING as the reference Direction id. */
    public static int facing(BlockType type) {
        String value = type.identifier().value();
        boolean triggered = value.endsWith("_triggered");
        String rest = triggered
                ? value.substring(0, value.length() - "_triggered".length()) : value;
        String facing = rest.substring(rest.lastIndexOf('_') + 1);
        for (int i = 0; i < DIRECTION_KEYS.length; i++) {
            if (DIRECTION_KEYS[i].equals(facing)) {
                return i;
            }
        }
        return 0;
    }

    /** @return whether the TRIGGERED pair is raised (the activation flag). */
    public static boolean triggered(BlockType type) {
        return type.identifier().value().endsWith("_triggered");
    }

    /** The dispenser state for a facing + triggered pair. */
    public static EngineBlockType dispenserOf(int facing, boolean triggered) {
        return of(DISPENSERS, facing, triggered);
    }

    /** The dropper state for a facing + triggered pair. */
    public static EngineBlockType dropperOf(int facing, boolean triggered) {
        return of(DROPPERS, facing, triggered);
    }

    /** The same family state with the triggered pair swapped. */
    public static EngineBlockType ofTriggerState(BlockType type, boolean triggered) {
        boolean dropper = isDropper(type);
        return dropper
                ? dropperOf(facing(type), triggered)
                : dispenserOf(facing(type), triggered);
    }

    private static EngineBlockType of(EngineBlockType[][] states, int facing, boolean triggered) {
        int clamped = facing & 7;
        if (clamped >= states.length) {
            clamped = 0; // the FACING property covers all six; the guard holds
        }
        return states[clamped][triggered ? 1 : 0];
    }

    private static EngineBlockType[][] buildStates(String name) {
        EngineBlockType[][] all = new EngineBlockType[6][2];
        for (int facing = 0; facing < 6; facing++) {
            for (int triggered = 0; triggered <= 1; triggered++) {
                all[facing][triggered] = new EngineBlockType(
                        Identifier.parse("minecraft:" + name + "_" + DIRECTION_KEYS[facing]
                                + (triggered == 1 ? "_triggered" : "")),
                        name.equals("dispenser") ? "Dispenser" : "Dropper",
                        facing | (triggered == 1 ? 8 : 0));
            }
        }
        return all;
    }

    /** Registers every dispenser and dropper type into the registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType[] facing : DISPENSERS) {
            for (EngineBlockType type : facing) {
                builder.register(type);
            }
        }
        for (EngineBlockType[] facing : DROPPERS) {
            for (EngineBlockType type : facing) {
                builder.register(type);
            }
        }
        return builder;
    }
}
