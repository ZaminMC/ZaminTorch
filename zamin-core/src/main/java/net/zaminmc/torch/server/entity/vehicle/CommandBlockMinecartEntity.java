package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;

import java.util.Random;

/**
 * The command-block minecart (Slice 66 — the reference port of
 * vehicle/CommandBlockMinecartEntity.java over
 * server/command/source/CommandExecutor.java): the rolling executor the
 * powered activator rail fires.
 *
 * <p><b>The run (onActivatorRail lines 99-104):</b> a powered activator
 * rail executes the stored command, at most once every 4 ticks
 * ({@code ticks - lastExecuted >= 4}). The executor's own gates
 * (CommandExecutor.run lines 79-100): the command-blocks-enabled server
 * gate (the engine's config has no such flag yet — the run is ungated,
 * ledgered with the config slice), the op level 2
 * ({@code canUseCommand: permissionLevel <= 2}), and the success count
 * from the handler's own return.</p>
 *
 * <p><b>The output (CommandExecutor.sendMessage lines 126-132):</b> every
 * feedback line prepends the {@code [HH:mm:ss]} timestamp and lands in
 * the last output while {@code trackOutput} holds (the reference's own
 * date format, the engine's wall clock).</p>
 *
 * <p><b>The synced data (registerSyncedData slots 23-24):</b> the command
 * string at 23 and the last output's JSON at 24 — the client's edit GUI
 * reads them (the open itself is client-side for creative players; the
 * edits ride the Update Entity NBT 0x49 packet back).</p>
 */
public class CommandBlockMinecartEntity extends MinecartEntity {

    /** The re-run floor (onActivatorRail line 100: 4 ticks). */
    public static final int REEXECUTE_COOLDOWN = 4;

    /**
     * The engine's run surface (the CommandService dispatch with the
     * executor's own sender shape — op level 2, the name "@", the
     * feedback landing in the last output). Wired at spawn by the engine.
     */
    public interface CommandHost {
        /**
         * One execution (CommandExecutor.run): @return the success count
         * (the handler's own return; the feedback lines land in the
         * cart's last output through the host's sendMessage walk).
         */
        int run(CommandBlockMinecartEntity cart, String command);
    }

    /** The executor's own state (CommandExecutor's fields, 1:1). */
    private String command = "";
    private int successCount;
    private boolean trackOutput = true;
    private String lastOutput;
    private final CommandHost host;
    private int ticks;
    private int lastExecuted;

    public CommandBlockMinecartEntity(int entityId, Position position, Random random,
                                      CommandHost host) {
        super(entityId, Kind.COMMAND_BLOCK_MINECART, position, random);
        this.host = java.util.Objects.requireNonNull(host, "host");
    }

    /** The wire's minecart type index: 6 = command block. */
    @Override
    public int minecartTypeIndex() {
        return 6;
    }

    /** The stored command (getCommand) — no leading slash. */
    public String command() {
        return command;
    }

    /** The command's write arm (setCommand line 107: the count resets). */
    public void setCommand(String command) {
        this.command = command == null ? "" : command;
        this.successCount = 0;
    }

    /** The last run's success count (getSuccessCount). */
    public int successCount() {
        return successCount;
    }

    /** The success count's write arm (readNbt's SuccessCount row). */
    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    /** Whether the output tracks (trackOutput). */
    public boolean trackOutput() {
        return trackOutput;
    }

    /** The track flag's write arm (readNbt's TrackOutput row). */
    public void setTrackOutput(boolean trackOutput) {
        this.trackOutput = trackOutput;
    }

    /** The last output line, or null (getLastOutput). */
    public String lastOutput() {
        return lastOutput;
    }

    /** The last output's write arm (setLastOutput). */
    public void setLastOutput(String lastOutput) {
        this.lastOutput = lastOutput;
    }

    /**
     * The feedback line (sendMessage lines 126-132): the timestamped
     * output while tracking.
     */
    public void recordOutput(String message) {
        if (trackOutput) {
            this.lastOutput = "[" + new java.text.SimpleDateFormat("HH:mm:ss")
                    .format(new java.util.Date()) + "] " + message;
        }
    }

    /**
     * The kind's tick: the cart body moves first, the age clock advances
     * (the reference's {@code ticks} — the re-run floor's own counter).
     */
    @Override
    protected void tickKind(net.zaminmc.torch.server.entity.vehicle.VehicleEntity.WorldQuery world) {
        super.tickKind(world);
        ticks++;
    }

    /**
     * The activator rail's arm (onActivatorRail lines 99-104): a powered
     * rail fires the command, at most once every 4 ticks.
     */
    public void onActivatorRail(boolean powered) {
        if (powered && !dead() && ticks - lastExecuted >= REEXECUTE_COOLDOWN) {
            this.successCount = host.run(this, command);
            this.lastExecuted = ticks;
        }
    }

    /** @return the executor's own age clock (diagnostics). */
    public int ticks() {
        return ticks;
    }
}
