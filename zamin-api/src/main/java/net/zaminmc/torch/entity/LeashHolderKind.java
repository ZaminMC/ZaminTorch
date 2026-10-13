package net.zaminmc.torch.entity;

/**
 * The leash holder's shape (§27.3's leash family): a leashed mob is held
 * either by a player's hand or by a knot tied at a fence post — the two
 * holder arms the reference's {@code MobEntity} carries.
 */
public enum LeashHolderKind {
    /** The mob carries no live lead. */
    NONE,
    /** A player's hand holds the lead (the right-click attach). */
    PLAYER,
    /** A leash knot at a fence post holds the lead (the use-on-fence tie). */
    KNOT
}
