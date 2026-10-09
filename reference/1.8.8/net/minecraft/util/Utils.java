package net.minecraft.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import org.apache.logging.log4j.Logger;

public class Utils {
    public static Utils.OS getOS() {
        String s = System.getProperty("os.name").toLowerCase();
        if (s.contains("win")) {
            return Utils.OS.WINDOWS;
        } else if (s.contains("mac")) {
            return Utils.OS.MACOS;
        } else if (s.contains("solaris")) {
            return Utils.OS.SOLARIS;
        } else if (s.contains("sunos")) {
            return Utils.OS.SOLARIS;
        } else if (s.contains("linux")) {
            return Utils.OS.LINUX;
        } else {
            return s.contains("unix") ? Utils.OS.LINUX : Utils.OS.UNKNOWN;
        }
    }

    public static <V> V run(FutureTask<V> task, Logger logger) {
        try {
            task.run();
            return task.get();
        } catch (ExecutionException executionexception) {
            logger.fatal("Error executing task", executionexception);
        } catch (InterruptedException interruptedexception) {
            logger.fatal("Error executing task", interruptedexception);
        }

        return null;
    }

    public enum OS {
        LINUX,
        SOLARIS,
        WINDOWS,
        MACOS,
        UNKNOWN;
    }
}
