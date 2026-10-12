package net.zaminmc.torch.event;

/**
 * The public classification of damage sources (the §27.7 damage family):
 * the cause the engine's armor and enchantment steps already branch on,
 * surfaced through the damage events. The names are the engine's own
 * {@code DamageKind} constants (the reference's {@code DamageSource}
 * predicates) so a plugin's cause matching stays 1:1 with the survival
 * math.
 *
 * <p>The one addition beyond the engine's protection-step classification
 * is {@link #MAGIC}: the potion family's periodic and instant damage
 * (the reference's {@code DamageSource.magic}, which pierces armor and
 * carries no knockback).</p>
 */
public enum DamageCause {
    /** The {@code player}/{@code mob} entity sources (melee). */
    MELEE,
    /** The {@code arrow} source (the {@code setProjectile} family). */
    PROJECTILE,
    /** The {@code explosion} source ({@code setExplosive}). */
    EXPLOSION,
    /** {@code inFire}: standing inside the flame block. */
    IN_FIRE,
    /** {@code onFire}: the burn residual (bypasses armor, still fire). */
    ON_FIRE,
    /** {@code lava}. */
    LAVA,
    /** {@code fall}: the landing damage (bypasses armor, still protected). */
    FALL,
    /** {@code drown} (bypasses armor, still protected). */
    DROWN,
    /** {@code cactus}: an ordinary physical source. */
    CACTUS,
    /** {@code starve}: the reference's {@code setUnblockable} source. */
    STARVE,
    /** {@code outOfWorld}: the void (every piece contributes zero). */
    OUT_OF_WORLD,
    /** The potion family's periodic/instant arm ({@code DamageSource.magic}). */
    MAGIC;

    /** The reference {@code DamageSource.isFire} family. */
    public boolean isFire() {
        return this == IN_FIRE || this == ON_FIRE || this == LAVA;
    }

    /** The reference {@code DamageSource.isExplosive}. */
    public boolean isExplosive() {
        return this == EXPLOSION;
    }

    /** The reference {@code DamageSource.isProjectile}. */
    public boolean isProjectile() {
        return this == PROJECTILE;
    }
}
