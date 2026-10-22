package net.zaminmc.torch.entity.ai;

import net.zaminmc.torch.block.BlockPosition;

/**
 * The zombie's door-break read (VIII9 — the reference's
 * {@code BreakDoorGoal} over {@code AbstractDoorInteractGoal}, the
 * built-in BREAK_DOOR band's arming + progress): whether the goal is
 * armed on the body (the {@code CanBreakDoors} row), and — while the
 * walk owns a door — the door target and the progress toward the break.
 * Reached through {@link net.zaminmc.torch.entity.LivingEntity#doorBreak()}
 * (beside {@link net.zaminmc.torch.entity.LivingEntity#goals()}, the
 * custom-goal registration surface this read completes).
 *
 * <p><b>The reads (the reference's own rows):</b> the arming flag is the
 * {@code CanBreakDoors} row the spawn dice roll
 * ({@code nextFloat() < f * 0.1F}) and the leader bonus force — the
 * reference's {@code setCanBreakDoors} adds and removes the goal from
 * the selector, the engine's kind-gated {@code canUse} reads the same
 * law. The live walk is the reference's goal-running state: the door
 * target is {@code AbstractDoorInteractGoal}'s {@code doorPos} (the
 * UPPER half's cell, captured at the clip site — the reference's
 * canStart node walk inside the squared-2.25 band, the fallback cell
 * above the body), and the progress is {@code BreakDoorGoal}'s
 * {@code breakProgress} — the 240-tick hardness clock (12 seconds at
 * the door; the wire cracks at {@code progress / 240 * 10}, the 0-9
 * stage band, with the completion alone on HARD removing the cell).</p>
 *
 * <p><b>Threading (§27.6 — the goal walk is tick-lane):</b> this view is
 * a live read over the authoritative body, never a copy (§27.5): every
 * answer resolves to the state the walk holds at the moment of the
 * call. Consistent on the body's simulation context — the lane the mind
 * walks, the documented owner path {@code World.execute(runnable)}; the
 * walk mutates only between ticks there. An off-lane read is
 * best-effort: each answer is one whole word (the flag, the counter,
 * the cell — never half-written) reflecting some instant between ticks,
 * the same best-effort band the live entity reads carry.</p>
 *
 * <p><b>When the reads are meaningful:</b> {@code doorPosition()} and
 * {@code progress()} answer the walk's own fields — before the first
 * capture they hold the untouched start (the reference's
 * {@code doorPos = BlockPos.ORIGIN} and a zero clock), and after the
 * walk ends they reset the same tick the completion or the stop lands.
 * Consult {@link #breaking()} first: the pair carries its meaning only
 * while the walk owns the door.</p>
 */
public interface DoorBreak {

    /**
     * Whether the door-break goal is armed on this body — the
     * {@code CanBreakDoors} row (the spawn-dice roll + the leader's
     * forced arm + the NBT row's own verb). An armed zombie claims a
     * closed wooden door it clips mid-walk; the flag never arms on a
     * non-zombie body.
     *
     * @return the goal-arming flag (persists across walks — the
     *         completion leaves it armed, the reference's own shape)
     */
    boolean canBreakDoors();

    /**
     * Whether the walk owns a door cell — the goal's live run (the
     * reference's started state between {@code canStart} and the
     * {@code shouldContinue} failure that stops it). True from the
     * capture at the door until the completion (progress 240 on HARD)
     * or the stop (the door opened, the body left the squared-4 band,
     * the clock past 240).
     */
    boolean breaking();

    /**
     * The door target the walk owns — the UPPER half's cell
     * ({@code AbstractDoorInteractGoal}'s {@code doorPos}, the
     * reference's own {@code node.y + 1} capture). Meaningful while
     * {@link #breaking()} answers true; the reference's field likewise
     * holds the last capture outside a walk.
     */
    BlockPosition doorPosition();

    /**
     * The progress toward the break — the 240-tick hardness clock
     * ({@code BreakDoorGoal}'s {@code breakProgress}, one per tick of
     * the walk). The band: 0 before the walk and after its end, 240 the
     * completion tick; the wire's crack stage is
     * {@code progress / 240 * 10} (0-9 through the walk, 10 the
     * completion's last word).
     */
    int progress();
}
