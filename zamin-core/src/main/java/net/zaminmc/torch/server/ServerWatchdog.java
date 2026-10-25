package net.zaminmc.torch.server;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * The tick watchdog (ML-013): the reference DedicatedServer's own
 * ServerWatchdog, carried onto the engine's tick lane. The shape is the
 * reference's, law for law (net.minecraft.server.dedicated.ServerWatchdog,
 * 1.8.8 reference lines 17-90):
 *
 * <ul>
 *   <li>The loop watches the tick lane's heartbeat — the in-flight tick's
 *       start stamp, the same window the reference reads through
 *       {@code getNextTickTime()} — and sleeps until the current tick's age
 *       would reach the threshold (the reference's own
 *       {@code Thread.sleep(i + maxTickTime - j)}, line 70).</li>
 *   <li>A tick wedged past {@code max-tick-time} (the {@code server.properties}
 *       row, DedicatedServer line 522's one-minute default; 0 disables the
 *       whole thread at the boot gate, lines 216-221) logs the two fatal
 *       lines, dumps every thread, writes a crash report under
 *       {@code crash-reports/crash-yyyy-MM-dd_HH.mm.ss-server.txt}, and
 *       force-exits the JVM — a wedged tick lane owns every world write, so
 *       a graceful save walk would itself hang on the wedged lane; the
 *       reference's own verdict, carried whole.</li>
 *   <li>The crash report carries the reference's structure (CrashReport.build
 *       lines 160-182): the header, the witty comment, the time, the
 *       description, the tick thread's stack as the error, the 87-dash
 *       separator, and the thread-dump section.</li>
 * </ul>
 *
 * <p>The force-exit is a seam: production wires {@link #forceExitJvm()} (the
 * reference's exit(), lines 76-89 — System.exit(1) with a 10-second
 * {@code halt(1)} timer so a stuck shutdown hook can never wedge the
 * process); tests inject a capturing hook so the stall walk runs in-process.</p>
 */
public final class ServerWatchdog implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(ServerWatchdog.class.getName());

    /** The reference's own crash-header separator length (build line 175). */
    private static final int SEPARATOR_DASHES = 87;

    /**
     * The witty comment list, 1:1 the reference's CrashReport lines 253-288 —
     * the crash report's own flavor, carried whole.
     */
    private static final String[] WITTY_COMMENTS = {
            "Who set us up the TNT?",
            "Everything's going to plan. No, really, that was supposed to happen.",
            "Uh... Did I do that?",
            "Oops.",
            "Why did you do that?",
            "I feel sad now :(",
            "My bad.",
            "I'm sorry, Dave.",
            "I let you down. Sorry :(",
            "On the bright side, I bought you a teddy bear!",
            "Daisy, daisy...",
            "Oh - I know what I did wrong!",
            "Hey, that tickles! Hehehe!",
            "I blame Dinnerbone.",
            "You should try our sister game, Minceraft!",
            "Don't be sad. I'll do better next time, I promise!",
            "Don't be sad, have a hug! <3",
            "I just don't know what went wrong :(",
            "Shall we play a game?",
            "Quite honestly, I wouldn't worry myself about that.",
            "I bet Cylons wouldn't have this problem.",
            "Sorry :(",
            "Surprise! Haha. Well, this is awkward.",
            "Would you like a cupcake?",
            "Hi. I'm Minecraft, and I'm a crashaholic.",
            "Ooh. Shiny.",
            "This doesn't make any sense!",
            "Why is it breaking :(",
            "Don't do that.",
            "Ouch. That hurt :(",
            "You're mean.",
            "This is a token for 1 free hug. Redeem at your nearest Mojangsta: [~~HUG~~]",
            "There are four lights!",
            "But it works on my machine."
    };

    private final EngineTicker ticker;
    private final long maxTickTimeMillis;
    private final Path crashReportsDir;
    private final Runnable forceExit;

    /**
     * @param ticker           the tick lane to watch (the heartbeat + the
     *                         owner thread + the interval face)
     * @param maxTickTimeMillis the stall threshold; the boot gate never
     *                         starts the thread at 0, so this must be
     *                         positive
     * @param crashReportsDir  where the stall's crash report lands (the
     *                         reference's {@code <runDir>/crash-reports})
     * @param forceExit        the force-exit seam — production wires
     *                         {@link #forceExitJvm()}, tests capture
     */
    ServerWatchdog(EngineTicker ticker, long maxTickTimeMillis,
                   Path crashReportsDir, Runnable forceExit) {
        this.ticker = java.util.Objects.requireNonNull(ticker, "ticker");
        if (maxTickTimeMillis <= 0) {
            throw new IllegalArgumentException(
                    "max-tick-time must be positive (the boot gate owns the disabled case): "
                            + maxTickTimeMillis);
        }
        this.maxTickTimeMillis = maxTickTimeMillis;
        this.crashReportsDir = java.util.Objects.requireNonNull(crashReportsDir, "crashReportsDir");
        this.forceExit = java.util.Objects.requireNonNull(forceExit, "forceExit");
    }

    /**
     * The reference's own loop (run lines 28-73): watch, sleep until the
     * current tick would trip, re-watch; a stalled lane fires once and the
     * walk force-exits (production) or returns (the test seam).
     */
    @Override
    public void run() {
        while (ticker.isLoopRunning()) {
            if (tripIfStalled()) {
                return;
            }
            // The reference's sleep (line 70): until the current tick's age
            // would reach the threshold. The idle lane (no tick started yet)
            // sleeps the whole threshold — the pre-loop window can never
            // trip. A tick that started between the read and the sleep only
            // wakes the loop early; the next pass re-measures.
            long start = ticker.currentTickStartNanos();
            long idleAge = start == 0 ? 0L : (System.nanoTime() - start) / 1_000_000L;
            long sleepMillis = Math.max(1L, maxTickTimeMillis - idleAge);
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                // The reference's own shape (line 71): the loop re-reads the
                // run gate and exits when the lane is down.
            }
        }
    }

    /**
     * One watchdog pass: measures the in-flight tick's age against the
     * threshold and, when it has tripped, runs the whole stall walk (the
     * two fatal lines, the thread dump, the crash report, the force exit).
     * Package-private so the stall walk is testable in-process without the
     * loop's cadence.
     *
     * @return whether the stall walk ran
     */
    boolean tripIfStalled() {
        long start = ticker.currentTickStartNanos();
        if (start == 0) {
            return false; // the idle gate: no tick has ever started
        }
        long ageMillis = (System.nanoTime() - start) / 1_000_000L;
        if (ageMillis > maxTickTimeMillis) {
            onStall(ageMillis);
            return true;
        }
        return false;
    }

    /**
     * The stall walk, the reference's fatal arm (lines 33-66): the two fatal
     * lines, every thread dumped, the tick thread's stack as the crash
     * error, the crash report written, then the force exit.
     */
    private void onStall(long ageMillis) {
        LOGGER.severe("A single server tick took "
                + String.format("%.2f", ageMillis / 1000.0)
                + " seconds (should be max "
                + String.format("%.2f", ticker.tickIntervalMillis()) + ")");
        LOGGER.severe("Considering it to be crashed, server will forcibly shutdown.");

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] infos = threadBean.dumpAllThreads(true, true);
        StringBuilder dump = new StringBuilder();
        StackTraceElement[] tickStack = null;
        Thread tickThread = ticker.ownerThread();
        for (ThreadInfo info : infos) {
            if (tickThread != null && info.getThreadId() == tickThread.threadId()) {
                tickStack = info.getStackTrace();
            }
            dump.append(info).append('\n');
        }

        String report = buildReport(ageMillis, tickThread, tickStack, dump.toString());
        Path file = crashReportsDir.resolve("crash-"
                + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date())
                + "-server.txt");
        try {
            Files.createDirectories(crashReportsDir);
            Files.writeString(file, report, StandardCharsets.UTF_8);
            LOGGER.severe("This crash report has been saved to: "
                    + file.toAbsolutePath());
        } catch (Exception e) {
            LOGGER.severe("We were unable to save this crash report to disk.");
        }

        forceExit.run();
    }

    /**
     * The report body, the reference CrashReport.build structure (lines
     * 160-182) over Torch's own header: the witty comment, the time, the
     * description, the tick thread's stack as the error, the 87-dash
     * separator, then the sections (the reference's categories).
     */
    private String buildReport(long ageMillis, Thread tickThread,
                               StackTraceElement[] tickStack, String threadDump) {
        StringBuilder out = new StringBuilder();
        out.append("---- ZaminTorch Crash Report ----\n");
        out.append("// ").append(wittyComment()).append("\n\n");
        out.append("Time: ").append(new SimpleDateFormat().format(new Date())).append('\n');
        out.append("Description: Watching Server\n\n");
        out.append("The tick lane has been wedged for ").append(ageMillis)
                .append("ms (max-tick-time ").append(maxTickTimeMillis)
                .append("ms). The tick thread's stack at the stall:\n\n");
        if (tickThread != null) {
            out.append("Thread: ").append(tickThread.getName()).append('\n');
        }
        if (tickStack != null && tickStack.length > 0) {
            for (StackTraceElement frame : tickStack) {
                out.append("    at ").append(frame).append('\n');
            }
        } else {
            out.append("    (no tick thread stack captured)\n");
        }
        out.append('\n');
        out.append("A detailed walkthrough of the error, its code path and all known details is as follows:\n");
        out.append("-".repeat(SEPARATOR_DASHES)).append("\n\n");
        out.append("-- Thread Dump --\n");
        out.append("Details:\n");
        out.append(threadDump).append('\n');
        out.append("-- Torch --\n");
        out.append("Details:\n");
        out.append("Max tick time: ").append(maxTickTimeMillis).append("ms\n");
        out.append("Tick interval: ")
                .append(String.format("%.2f", ticker.tickIntervalMillis())).append("ms\n");
        out.append("Completed ticks: ").append(ticker.completedTicks()).append('\n');
        out.append("Ticks per second (1m window): ")
                .append(String.format("%.2f", ticker.averageTps(60))).append('\n');
        return out.toString();
    }

    /**
     * The witty comment pick, the reference's own law (CrashReport lines
     * 290-292): a comment is chosen from the list by the wall-clock
     * nanosecond — the reference's {@code System.nanoTime() % length} run.
     */
    private static String wittyComment() {
        return WITTY_COMMENTS[(int) (System.nanoTime() % WITTY_COMMENTS.length)];
    }

    /**
     * The production force exit, the reference's exit() 1:1 (lines 76-89):
     * System.exit(1) under a 10-second halt(1) timer — a stuck shutdown
     * hook can never wedge the process — and halt(1) on any Throwable.
     */
    static void forceExitJvm() {
        try {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Runtime.getRuntime().halt(1);
                }
            }, 10000L);
            System.exit(1);
        } catch (Throwable throwable) {
            Runtime.getRuntime().halt(1);
        }
    }
}
