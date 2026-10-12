package net.minecraft.server.dedicated;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerWatchdog implements Runnable {
    private static final Logger LOGGER = LogManager.getLogger();
    private final DedicatedServer server;
    private final long maxTickTime;

    public ServerWatchdog(DedicatedServer server) {
        this.server = server;
        this.maxTickTime = server.getMaxTickTime();
    }

    @Override
    public void run() {
        while (this.server.isRunning()) {
            long i = this.server.getNextTickTime();
            long j = MinecraftServer.getTimeMillis();
            long k = j - i;
            if (k > this.maxTickTime) {
                LOGGER.fatal(
                    "A single server tick took " + String.format("%.2f", (float)k / 1000.0F) + " seconds (should be max " + String.format("%.2f", 0.05F) + ")"
                );
                LOGGER.fatal("Considering it to be crashed, server will forcibly shutdown.");
                ThreadMXBean threadmxbean = ManagementFactory.getThreadMXBean();
                ThreadInfo[] athreadinfo = threadmxbean.dumpAllThreads(true, true);
                StringBuilder stringbuilder = new StringBuilder();
                Error error = new Error();

                for (ThreadInfo threadinfo : athreadinfo) {
                    if (threadinfo.getThreadId() == this.server.getThread().getId()) {
                        error.setStackTrace(threadinfo.getStackTrace());
                    }

                    stringbuilder.append(threadinfo);
                    stringbuilder.append("\n");
                }

                CrashReport crashreport = new CrashReport("Watching Server", error);
                this.server.populateCrashReport(crashreport);
                CrashReportCategory crashreportcategory = crashreport.addCategory("Thread Dump");
                crashreportcategory.add("Threads", stringbuilder);
                File file1 = new File(
                    new File(this.server.getRunDir(), "crash-reports"),
                    "crash-" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + "-server.txt"
                );
                if (crashreport.writeToFile(file1)) {
                    LOGGER.error("This crash report has been saved to: " + file1.getAbsolutePath());
                } else {
                    LOGGER.error("We were unable to save this crash report to disk.");
                }

                this.exit();
            }

            try {
                Thread.sleep(i + this.maxTickTime - j);
            } catch (InterruptedException interruptedexception) {
            }
        }
    }

    private void exit() {
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
