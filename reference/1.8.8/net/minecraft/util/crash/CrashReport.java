package net.minecraft.util.crash;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import net.minecraft.world.biome.IntCache;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CrashReport {
    private static final Logger LOGGER = LogManager.getLogger();
    private final String description;
    private final Throwable exception;
    private final CrashReportCategory systemDetails = new CrashReportCategory(this, "System Details");
    private final List<CrashReportCategory> details = Lists.newArrayList();
    private File file;
    private boolean hasStackTrace = true;
    private StackTraceElement[] stackTrace = new StackTraceElement[0];

    public CrashReport(String description, Throwable exception) {
        this.description = description;
        this.exception = exception;
        this.fillSystemDetails();
    }

    private void fillSystemDetails() {
        this.systemDetails.add("Minecraft Version", new Callable<String>() {
            public String call() {
                return "1.8.8";
            }
        });
        this.systemDetails.add("Operating System", new Callable<String>() {
            public String call() {
                return System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ") version " + System.getProperty("os.version");
            }
        });
        this.systemDetails.add("Java Version", new Callable<String>() {
            public String call() {
                return System.getProperty("java.version") + ", " + System.getProperty("java.vendor");
            }
        });
        this.systemDetails.add("Java VM Version", new Callable<String>() {
            public String call() {
                return System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.info") + "), " + System.getProperty("java.vm.vendor");
            }
        });
        this.systemDetails.add("Memory", new Callable<String>() {
            public String call() {
                Runtime runtime = Runtime.getRuntime();
                long i = runtime.maxMemory();
                long j = runtime.totalMemory();
                long k = runtime.freeMemory();
                long l = i / 1024L / 1024L;
                long i1 = j / 1024L / 1024L;
                long j1 = k / 1024L / 1024L;
                return k + " bytes (" + j1 + " MB) / " + j + " bytes (" + i1 + " MB) up to " + i + " bytes (" + l + " MB)";
            }
        });
        this.systemDetails.add("JVM Flags", new Callable<String>() {
            public String call() {
                RuntimeMXBean runtimemxbean = ManagementFactory.getRuntimeMXBean();
                List<String> list = runtimemxbean.getInputArguments();
                int i = 0;
                StringBuilder stringbuilder = new StringBuilder();

                for (String s : list) {
                    if (s.startsWith("-X")) {
                        if (i++ > 0) {
                            stringbuilder.append(" ");
                        }

                        stringbuilder.append(s);
                    }
                }

                return String.format("%d total; %s", i, stringbuilder.toString());
            }
        });
        this.systemDetails.add("IntCache", new Callable<String>() {
            public String call() throws Exception {
                return IntCache.getDebugInfo();
            }
        });
    }

    public String getDescription() {
        return this.description;
    }

    public Throwable getException() {
        return this.exception;
    }

    public void addDetails(StringBuilder sb) {
        if ((this.stackTrace == null || this.stackTrace.length <= 0) && this.details.size() > 0) {
            this.stackTrace = ArrayUtils.subarray(this.details.get(0).getStackTrace(), 0, 1);
        }

        if (this.stackTrace != null && this.stackTrace.length > 0) {
            sb.append("-- Head --\n");
            sb.append("Stacktrace:\n");

            for (StackTraceElement stacktraceelement : this.stackTrace) {
                sb.append("\t").append("at ").append(stacktraceelement.toString());
                sb.append("\n");
            }

            sb.append("\n");
        }

        for (CrashReportCategory crashreportcategory : this.details) {
            crashreportcategory.addDetails(sb);
            sb.append("\n\n");
        }

        this.systemDetails.addDetails(sb);
    }

    public String getExceptionMessage() {
        StringWriter stringwriter = null;
        PrintWriter printwriter = null;
        Throwable throwable = this.exception;
        if (throwable.getMessage() == null) {
            if (throwable instanceof NullPointerException) {
                throwable = new NullPointerException(this.description);
            } else if (throwable instanceof StackOverflowError) {
                throwable = new StackOverflowError(this.description);
            } else if (throwable instanceof OutOfMemoryError) {
                throwable = new OutOfMemoryError(this.description);
            }

            throwable.setStackTrace(this.exception.getStackTrace());
        }

        String s = throwable.toString();

        try {
            stringwriter = new StringWriter();
            printwriter = new PrintWriter(stringwriter);
            throwable.printStackTrace(printwriter);
            s = stringwriter.toString();
        } finally {
            IOUtils.closeQuietly(stringwriter);
            IOUtils.closeQuietly(printwriter);
        }

        return s;
    }

    public String build() {
        StringBuilder stringbuilder = new StringBuilder();
        stringbuilder.append("---- Minecraft Crash Report ----\n");
        stringbuilder.append("// ");
        stringbuilder.append(getWittyComment());
        stringbuilder.append("\n\n");
        stringbuilder.append("Time: ");
        stringbuilder.append(new SimpleDateFormat().format(new Date()));
        stringbuilder.append("\n");
        stringbuilder.append("Description: ");
        stringbuilder.append(this.description);
        stringbuilder.append("\n\n");
        stringbuilder.append(this.getExceptionMessage());
        stringbuilder.append("\n\nA detailed walkthrough of the error, its code path and all known details is as follows:\n");

        for (int i = 0; i < 87; i++) {
            stringbuilder.append("-");
        }

        stringbuilder.append("\n\n");
        this.addDetails(stringbuilder);
        return stringbuilder.toString();
    }

    public File getFile() {
        return this.file;
    }

    public boolean writeToFile(File file) {
        if (this.file != null) {
            return false;
        }

        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try {
            FileWriter filewriter = new FileWriter(file);
            filewriter.write(this.build());
            filewriter.close();
            this.file = file;
            return true;
        } catch (Throwable throwable) {
            LOGGER.error("Could not save crash report to " + file, throwable);
            return false;
        }
    }

    public CrashReportCategory getSystemDetails() {
        return this.systemDetails;
    }

    public CrashReportCategory addCategory(String title) {
        return this.addCategory(title, 1);
    }

    public CrashReportCategory addCategory(String title, int ignoredStackTraceCallCount) {
        CrashReportCategory crashreportcategory = new CrashReportCategory(this, title);
        if (this.hasStackTrace) {
            int i = crashreportcategory.getStackTrace(ignoredStackTraceCallCount);
            StackTraceElement[] astacktraceelement = this.exception.getStackTrace();
            StackTraceElement stacktraceelement = null;
            StackTraceElement stacktraceelement1 = null;
            int j = astacktraceelement.length - i;
            if (j < 0) {
                System.out.println("Negative index in crash report handler (" + astacktraceelement.length + "/" + i + ")");
            }

            if (astacktraceelement != null && 0 <= j && j < astacktraceelement.length) {
                stacktraceelement = astacktraceelement[j];
                if (astacktraceelement.length + 1 - i < astacktraceelement.length) {
                    stacktraceelement1 = astacktraceelement[astacktraceelement.length + 1 - i];
                }
            }

            this.hasStackTrace = crashreportcategory.validateStackTrace(stacktraceelement, stacktraceelement1);
            if (i > 0 && !this.details.isEmpty()) {
                CrashReportCategory crashreportcategory1 = this.details.get(this.details.size() - 1);
                crashreportcategory1.trimStackTrace(i);
            } else if (astacktraceelement != null && astacktraceelement.length >= i && 0 <= j && j < astacktraceelement.length) {
                this.stackTrace = new StackTraceElement[j];
                System.arraycopy(astacktraceelement, 0, this.stackTrace, 0, this.stackTrace.length);
            } else {
                this.hasStackTrace = false;
            }
        }

        this.details.add(crashreportcategory);
        return crashreportcategory;
    }

    private static String getWittyComment() {
        String[] astring = new String[]{
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

        try {
            return astring[(int)(System.nanoTime() % astring.length)];
        } catch (Throwable throwable) {
            return "Witty comment unavailable :(";
        }
    }

    public static CrashReport of(Throwable exception, String title) {
        CrashReport crashreport;
        if (exception instanceof CrashException) {
            crashreport = ((CrashException)exception).getReport();
        } else {
            crashreport = new CrashReport(title, exception);
        }

        return crashreport;
    }
}
