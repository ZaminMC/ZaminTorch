package net.zaminmc.torch.server.interaction;

/**
 * The vanilla 1.8.8 mining-timing math, ported from the in-repo decompiled
 * source ({@code reference/1.8.8}): {@code PlayerEntity.getMiningSpeed(Block)}
 * (the tool-and-environment speed) and {@code Block.getMiningSpeed(player,
 * world, pos)} (the per-tick progress delta). The values and the operation
 * order are the vanilla ones, not approximations:
 *
 * <ul>
 *   <li>the tool's tier speed only applies when its class matches the block's
 *       material (a pickaxe does nothing to dirt);</li>
 *   <li>the Efficiency enchantment adds {@code level^2 + 1} — but only when the
 *       base speed is already above the bare fist's 1.0 (vanilla gates the
 *       bonus on {@code f > 1.0F}, so an enchanted bare fist stays slow);</li>
 *   <li>being submerged in water without Aqua Affinity divides the speed by 5;</li>
 *   <li>not standing on the ground divides the speed by 5;</li>
 *   <li>the per-tick progress is {@code speed / hardness / (30 | 100)} — 30
 *       when the tool can harvest the block's drops, 100 otherwise, and 0
 *       (unbreakable) for negative hardness.</li>
 * </ul>
 *
 * <p>Haste and mining fatigue enter the same function once the engine grows a
 * status-effect system (the vanilla multipliers are haste
 * {@code 1 + 0.2*(amplifier+1)} and fatigue {@code 0.3/0.09/0.0027/8.1e-4} by
 * amplifier); the hook is the {@code speed} parameter chain here.</p>
 */
public final class MiningRules {

    /** The vanilla bare-fist speed ({@code PlayerInventory.getMiningSpeed}). */
    public static final double BARE_FIST_SPEED = 1.0;
    /** The vanilla harvestable divisor ({@code Block.getMiningSpeed}). */
    public static final double HARVEST_DIVISOR = 30.0;
    /** The vanilla non-harvestable divisor (the same method, the wrong tool). */
    public static final double SLOW_DIVISOR = 100.0;
    /** The vanilla water/airborne penalty factor ({@code f /= 5.0F}, twice). */
    public static final double PENALTY_DIVISOR = 5.0;

    private MiningRules() {
    }

    /**
     * The vanilla {@code PlayerEntity.getMiningSpeed(Block)}: the tool tier
     * speed against the block's material, then the Efficiency bonus, then the
     * environment divisors.
     *
     * @param baseToolSpeed   the tool tier speed when the tool class matches
     *                        the block's material, 1.0 otherwise ({@code
     *                        BlockBehaviorTable.speedMultiplier})
     * @param efficiencyLevel the held item's Efficiency enchantment level (0
     *                        when the engine runs without enchantments)
     * @param submerged       the eye is inside water
     * @param aquaAffinity    the player carries the Aqua Affinity enchantment
     * @param onGround        the player stands on ground
     */
    public static double miningSpeed(double baseToolSpeed, int efficiencyLevel,
                                     boolean submerged, boolean aquaAffinity, boolean onGround) {
        double speed = baseToolSpeed;
        if (speed > BARE_FIST_SPEED && efficiencyLevel > 0) {
            speed += efficiencyLevel * efficiencyLevel + 1;
        }
        if (submerged && !aquaAffinity) {
            speed /= PENALTY_DIVISOR;
        }
        if (!onGround) {
            speed /= PENALTY_DIVISOR;
        }
        return speed;
    }

    /**
     * The vanilla {@code Block.getMiningSpeed(player, world, pos)}: the
     * progress fraction this tick adds to the dig. Negative hardness is the
     * vanilla unbreakable encoding and yields no progress at all.
     */
    public static double perTickProgress(double hardness, double speed, boolean canHarvest) {
        if (hardness < 0.0) {
            return 0.0;
        }
        if (hardness == 0.0) {
            return 1.0; // instant: the dig completes the tick it starts
        }
        return speed / hardness / (canHarvest ? HARVEST_DIVISOR : SLOW_DIVISOR);
    }

    /**
     * The aggregate break duration in ticks: the first tick the accumulated
     * damage reaches 1 completes the dig, so the count rounds up. Unbreakable
     * blocks return {@link Integer#MAX_VALUE} (the dig never completes).
     */
    public static int breakTicks(double hardness, double speed, boolean canHarvest) {
        double perTick = perTickProgress(hardness, speed, canHarvest);
        if (perTick <= 0.0) {
            return Integer.MAX_VALUE;
        }
        if (perTick >= 1.0) {
            return 1; // instant or faster
        }
        return (int) Math.ceil(1.0 / perTick - 1e-9);
    }
}
