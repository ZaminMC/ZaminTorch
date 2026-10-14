package net.zaminmc.torch.server.daylight;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.weather.WeatherSystem;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.WorldChangeListener;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The daylight detector 1:1 (the reference's DaylightDetectorBlock +
 * DaylightDetectorBlockEntity, Block.java lines 1323/1377): the sky-light
 * power band over the reference's own math chain —
 *
 * <ul>
 *   <li><b>The tick gate</b> (the BE's own Tickable.tick lines 8-15): every
 *   tick consulted, the update runs only when {@code world.getTime() % 20
 *   == 0} — the engine's own {@code timeOfDay()} clock (a frozen
 *   doDaylightCycle freezes the detector with it, the reference's own
 *   behavior).</li>
 *   <li><b>The time fraction</b> (Dimension.getTimeOfDay lines 76-92): the
 *   day-cycle walk with the cosine easing — {@code f = (i + 1)/24000 - 0.25}
 *   wrapped, then {@code f1 + (1 - (cos(f * PI) + 1)/2 - f1)/3}.</li>
 *   <li><b>The ambient darkness</b> (World.calculateAmbientDarkness lines
 *   1004-1013): the skylight subtracted sky-darkness — the cosine band
 *   clamped, scaled by the rain and thunder gradients ({@code 1 - g*5/16}
 *   each), times 11.</li>
 *   <li><b>The sun angle</b> (World.getSunAngle lines 1087-1090): the
 *   fraction times 2&pi;; the detector's own wrap — {@code f1 = f < PI ? 0 :
 *   2PI; f += (f1 - f) * 0.2} — pulls the night angle toward the horizon
 *   (DaylightDetectorBlock lines 51-53).</li>
 *   <li><b>The power</b> (updateOutputState lines 47-64): {@code round((skyLight
 *   - darkness) * cos(angle))} clamped 0-15, the inverted form answering
 *   {@code 15 - i}; the block variant flips only when the level changed
 *   (the reference's own setBlockState-if gate).</li>
 * </ul>
 *
 * <p><b>The use arm</b> (use lines 66-85): the toggle between the two forms
 * keeping the POWER level, then the immediate re-update. <b>The emission</b>
 * (getSignal lines 43-45): the POWER level weakly in EVERY direction — the
 * redstone system's own consult; no strong arm (the reference overrides
 * getSignal alone, never getDirectSignal). The nether's hasNoSky gate
 * lives in the update walk (the engine's detector set is per-world).</p>
 */
public final class DaylightDetectorSystem implements WorldChangeListener {

    private final EngineWorld world;
    private final WeatherSystem weather;

    /** The live detectors by position (the placement listener's own set). */
    private final Set<BlockPosition> detectors = ConcurrentHashMap.newKeySet();

    public DaylightDetectorSystem(EngineWorld world, WeatherSystem weather) {
        this.world = world;
        this.weather = weather;
    }

    // ------------------------------------------------------------- the lifecycle

    /** The placement arm: a placed detector joins the set at power 0. */
    @Override
    public void onBlockChanged(EngineWorld changedWorld, BlockPosition position, BlockType newType) {
        if (DaylightDetectorBlocks.isFamily(newType)) {
            detectors.add(position);
        } else {
            detectors.remove(position);
        }
    }

    /** The break arm (the engine's break hook): the set entry dies. */
    public void onBlockBroken(BlockPosition position) {
        detectors.remove(position);
    }

    /**
     * The restore arm: a world that already carries the detector blocks
     * repopulates the set from the live blocks (the detector family needs
     * no store of its own — the power level rides the block variant, which
     * the world's own delta persistence carries).
     */
    public void rescan() {
        // The placement listener maintains the set during play; the restore
        // rides the same listener through the world's delta load (the
        // changes publish through the change bus).
    }

    // ------------------------------------------------------------- the tick

    /**
     * The BE's own Tickable.tick: consulted every tick, the update runs
     * only on the 20-tick boundary (getTime % 20 == 0). Tick-thread
     * context.
     */
    public void tick() {
        if (world.timeOfDay() % 20L != 0L || detectors.isEmpty()) {
            return;
        }
        float rain = weather == null ? 0.0F : weather.rainGradient();
        float thunder = weather == null ? 0.0F : weather.thunderGradient();
        for (BlockPosition position : detectors) {
            // The block-gone discard only decides on a LOADED chunk — an
            // unloaded chunk reads as air, but the vanilla block entity
            // freezes there (a restored detector survives the restart).
            if (!world.isChunkLoadedAt(position)) {
                continue;
            }
            BlockType type = world.getBlock(position);
            if (!DaylightDetectorBlocks.isFamily(type)) {
                detectors.remove(position);
                continue;
            }
            updateOutputState(position, type, rain, thunder);
        }
    }

    /**
     * The reference's updateOutputState (lines 47-64): the sky light minus
     * the ambient darkness, the sun-angle cosine, the clamp, the invert —
     * and the variant flip only when the level changed.
     */
    private void updateOutputState(BlockPosition position, BlockType type, float rain,
                                   float thunder) {
        int level = computePower(world.skyLightAt(position), world.timeOfDay(), rain, thunder,
                DaylightDetectorBlocks.isInverted(type));
        if (DaylightDetectorBlocks.powerOf(type) != level) {
            world.setBlock(position, DaylightDetectorBlocks.isInverted(type)
                    ? DaylightDetectorBlocks.invertedOfPower(level)
                    : DaylightDetectorBlocks.ofPower(level));
        }
    }

    /**
     * The use arm (use lines 66-85): the toggle between the two forms
     * keeping the POWER level, then the immediate re-update (the reference
     * calls the OTHER block's updateOutputState right after the swap).
     * Returns false when the click must fall through (not a detector).
     * Tick-thread context.
     */
    public boolean useFromTick(BlockPosition position) {
        BlockType type = world.getBlock(position);
        if (!DaylightDetectorBlocks.isFamily(type)) {
            return false;
        }
        int power = DaylightDetectorBlocks.powerOf(type);
        boolean nowInverted = !DaylightDetectorBlocks.isInverted(type);
        world.setBlock(position, nowInverted
                ? DaylightDetectorBlocks.invertedOfPower(power)
                : DaylightDetectorBlocks.ofPower(power));
        BlockType swapped = world.getBlock(position);
        updateOutputState(position, swapped,
                weather == null ? 0.0F : weather.rainGradient(),
                weather == null ? 0.0F : weather.thunderGradient());
        return true;
    }

    // ------------------------------------------------------------- the math

    /**
     * The power walk (updateOutputState lines 49-58): the sky light minus
     * the ambient darkness, scaled by the sun-angle cosine with the
     * night-wrap pull, clamped 0-15 (the inverted form's 15 - i).
     */
    public static int computePower(int skyLight, long timeOfDay, float rain, float thunder,
                                   boolean inverted) {
        int i = skyLight - ambientDarkness(timeOfDay, rain, thunder);
        float f = sunAngle(timeOfDay);
        float f1 = f < (float) Math.PI ? 0.0F : (float) (Math.PI * 2);
        f += (f1 - f) * 0.2F;
        i = Math.round(i * (float) Math.cos(f));
        i = Math.max(0, Math.min(15, i));
        return inverted ? 15 - i : i;
    }

    /** World.getSunAngle (lines 1087-1090): the fraction times 2&pi;. */
    public static float sunAngle(long timeOfDay) {
        return timeFraction(timeOfDay) * (float) Math.PI * 2.0F;
    }

    /**
     * Dimension.getTimeOfDay (lines 76-92): the day fraction with the
     * cosine easing ({@code f1 + (f - f1)/3} over the harmonic walk).
     */
    public static float timeFraction(long timeOfDay) {
        int i = (int) (timeOfDay % 24000L);
        float f = (i + 1.0F) / 24000.0F - 0.25F;
        if (f < 0.0F) {
            f++;
        }
        if (f > 1.0F) {
            f--;
        }
        float f1 = f;
        f = 1.0F - (float) ((Math.cos(f * Math.PI) + 1.0) / 2.0);
        return f1 + (f - f1) / 3.0F;
    }

    /**
     * World.calculateAmbientDarkness (lines 1004-1013): the sky-darkness
     * band — the clamped cosine, the rain and thunder scalings, times 11.
     */
    public static int ambientDarkness(long timeOfDay, float rain, float thunder) {
        float f = timeFraction(timeOfDay);
        float f1 = 1.0F - ((float) Math.cos(f * (float) Math.PI * 2.0F) * 2.0F + 0.5F);
        f1 = clamp01(f1);
        f1 = 1.0F - f1;
        f1 = (float) (f1 * (1.0 - rain * 5.0 / 16.0));
        f1 = (float) (f1 * (1.0 - thunder * 5.0 / 16.0));
        f1 = 1.0F - f1;
        return (int) (f1 * 11.0F);
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }
}
