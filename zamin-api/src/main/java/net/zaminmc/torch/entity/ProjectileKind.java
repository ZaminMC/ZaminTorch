package net.zaminmc.torch.entity;

import net.zaminmc.torch.util.Identifier;

/**
 * The public projectile kinds (architecture §27.3's projectile family).
 *
 * <p>One constant per engine projectile body; the wire's historical object
 * ids live behind them. The {@link #identifier()} form is the canonical
 * kind identifier ({@code minecraft:arrow}).</p>
 */
public enum ProjectileKind {

    /** The fired arrow (the historical object type 60). */
    ARROW("minecraft:arrow"),

    /** The thrown snowball (61). */
    SNOWBALL("minecraft:snowball"),

    /** The thrown egg (62). */
    EGG("minecraft:egg"),

    /** The blaze/dispenser fire charge (63). */
    SMALL_FIREBALL("minecraft:small_fireball"),

    /** The ghast's large fireball (64). */
    LARGE_FIREBALL("minecraft:fireball"),

    /** The thrown splash potion (68). */
    SPLASH_POTION("minecraft:potion"),

    /** The thrown ender pearl (65). */
    ENDER_PEARL("minecraft:ender_pearl"),

    /** The wither boss's skull (66): the charged blue variant rides the same kind. */
    WITHER_SKULL("minecraft:wither_skull");

    private final Identifier identifier;

    ProjectileKind(String identifier) {
        this.identifier = Identifier.parse(identifier);
    }

    /** The kind's canonical identifier. */
    public Identifier identifier() {
        return identifier;
    }
}
