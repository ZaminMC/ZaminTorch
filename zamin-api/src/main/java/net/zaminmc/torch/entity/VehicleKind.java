package net.zaminmc.torch.entity;

import net.zaminmc.torch.util.Identifier;

/**
 * The public vehicle kinds (architecture §27.3's vehicle/mount family).
 */
public enum VehicleKind {

    /** The wooden boat. */
    BOAT("minecraft:boat"),

    /** The rideable minecart. */
    MINECART("minecraft:minecart");

    private final Identifier identifier;

    VehicleKind(String identifier) {
        this.identifier = Identifier.parse(identifier);
    }

    /** The kind's canonical identifier. */
    public Identifier identifier() {
        return identifier;
    }
}
