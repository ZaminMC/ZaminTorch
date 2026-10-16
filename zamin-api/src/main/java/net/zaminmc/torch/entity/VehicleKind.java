package net.zaminmc.torch.entity;

import net.zaminmc.torch.util.Identifier;

/**
 * The public vehicle kinds (architecture §27.3's vehicle/mount family).
 */
public enum VehicleKind {

    /** The wooden boat. */
    BOAT("minecraft:boat"),

    /** The rideable minecart. */
    MINECART("minecraft:minecart"),

    /** The chest minecart (the 27-slot rolling container). */
    CHEST_MINECART("minecraft:chest_minecart"),

    /** The furnace minecart (the coal-fed pusher). */
    FURNACE_MINECART("minecraft:furnace_minecart"),

    /** The TNT minecart (the 80-tick fuse on rails). */
    TNT_MINECART("minecraft:tnt_minecart"),

    /** The hopper minecart (the 5-slot rolling pickup). */
    HOPPER_MINECART("minecraft:hopper_minecart"),

    /** The spawner minecart (the rolling mob spawner). */
    SPAWNER_MINECART("minecraft:spawner_minecart"),

    /** The command-block minecart (the rolling executor). */
    COMMAND_BLOCK_MINECART("minecraft:command_block_minecart");

    private final Identifier identifier;

    VehicleKind(String identifier) {
        this.identifier = Identifier.parse(identifier);
    }

    /** The kind's canonical identifier. */
    public Identifier identifier() {
        return identifier;
    }
}
