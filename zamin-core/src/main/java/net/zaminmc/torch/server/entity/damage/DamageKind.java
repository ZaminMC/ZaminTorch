package net.zaminmc.torch.server.entity.damage;

/**
 * The damage-source classification the enchantment-protection step reads
 * (the historical {@code DamageSource} predicates the reference's
 * {@code getDamageAfterEffectsAndEnchantments} walks). The flags below are
 * the ones {@code ProtectionEnchantment.getExtraProtection} branches on;
 * the exhaustion/armor-bypass economics live with the damage entries
 * (they already ride the {@code exhaustionCharge} parameter).
 *
 * <p>Reference map ({@code reference/1.8.8/entity/damage/DamageSource.java}):
 * {@code inFire} and {@code onFire} and {@code lava} carry {@code setFire};
 * {@code fall} is the {@code == DamageSource.FALL} identity the feather-
 * falling branch reads; {@code explosion} carries {@code setExplosive};
 * {@code arrow}/{@code thrown}/{@code fireball} carry {@code setProjectile};
 * {@code outOfWorld} carries {@code setOutOfWorld}; {@code starve} is the
 * one {@code setUnblockable} source the engine emits (it skips the whole
 * effects-and-enchantments half).</p>
 */
public enum DamageKind {
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
    /** {@code drown} (bypasses armor, still protected — only "all" applies). */
    DROWN,
    /** {@code cactus}: an ordinary physical source. */
    CACTUS,
    /** {@code starve}: the reference's {@code setUnblockable} source. */
    STARVE,
    /** {@code outOfWorld}: the void (every piece contributes zero). */
    OUT_OF_WORLD;

    /** The reference {@code DamageSource.isFire} family. */
    public boolean isFire() {
        return this == IN_FIRE || this == ON_FIRE || this == LAVA;
    }

    /** The reference {@code source == DamageSource.FALL} identity. */
    public boolean isFall() {
        return this == FALL;
    }

    /** The reference {@code DamageSource.isExplosive}. */
    public boolean isExplosive() {
        return this == EXPLOSION;
    }

    /** The reference {@code DamageSource.isProjectile}. */
    public boolean isProjectile() {
        return this == PROJECTILE;
    }

    /** The reference {@code DamageSource.isOutOfWorld}. */
    public boolean isOutOfWorld() {
        return this == OUT_OF_WORLD;
    }

    /**
     * The reference {@code isUnblockable} arm (the effects-and-enchantments
     * half returns the damage untouched). {@code starve} is the only
     * unblockable source the engine currently emits.
     */
    public boolean isUnblockable() {
        return this == STARVE;
    }
}
